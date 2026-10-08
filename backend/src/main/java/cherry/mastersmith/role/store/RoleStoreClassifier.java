/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cherry.mastersmith.role.store;

import cherry.mastersmith.common.persistence.RowLockFailures;
import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;

/**
 * 排他と書き込みの例外の区分（{@code reliability-design.md} 2.3、NFR3.3・NFR1.8、計画の D-6・D-7。group の
 * {@code StoreFailureClassifier} と同じ順）。
 *
 * <ol>
 *   <li>{@link RowLockFailures#isLockFailure(Throwable)} が真なら待ちの上限切れ（行の排他・一意の鍵の待ち）
 *   <li>原因の連なりに SQLState 23505（一意・主キー）か 23503・23506（外部キー。H2 は親の削除を 23503、親の無い追加を 23506 にする）
 *       があれば違反とし、制約の名前で読み替え先を決める（{@code uk_roles_name_key} → 名前の重なり、割り当ての主キー → 重ねての割り当て、
 *       作業ロールの保存の主キー → 待ちと同じ扱い、ロール・利用者・グループを指す外部キー → それぞれの相手）
 *   <li>どれにも当たらなければ想定外
 * </ol>
 *
 * <p>制約の名前は、Hibernate の {@link ConstraintViolationException#getConstraintName()} と、連なりの {@link SQLException} の文から
 * 探す。文には行の値（名前・ID）が入るため、このクラスの中で既知の名前の一覧と照らすだけで、文そのものをログ・例外・戻り値に載せない
 * （BR12.3）。H2 の主キーの索引の名前は {@code PRIMARY_KEY_xx} のため、主キーは表の名前で見分ける。
 */
public final class RoleStoreClassifier {

    /** 例外の区分。 */
    public enum Kind {
        /** 待ちの上限切れ（行き詰まりを含む）。 */
        LOCK_TIMEOUT,
        /** 名前の鍵の一意の制約の違反。 */
        NAME_TAKEN,
        /** 割り当ての主キーの違反（B5）。 */
        ALREADY_ASSIGNED,
        /** 作業ロールの保存の主キーの違反（同じ利用者の初めての切り替えの重なり。待ちの上限切れと同じ扱い。B5）。 */
        SELECTION_KEY,
        /** ロールを指す外部キーの違反。 */
        REFERENCED_ROLE,
        /** 利用者を指す外部キーの違反（B5）。 */
        REFERENCED_USER,
        /** グループを指す外部キーの違反（B5）。 */
        REFERENCED_GROUP,
        /** 想定外。 */
        UNEXPECTED
    }

    /** 一意・主キーの違反の SQLState。 */
    static final String UNIQUE_VIOLATION = "23505";

    /** 外部キーの違反の SQLState（子が残る親の削除 23503、親の無い子の追加 23506）。 */
    static final Set<String> FOREIGN_KEY_VIOLATIONS = Set.of("23503", "23506");

    /** 名前の鍵の一意の制約の名前（V11）。 */
    static final String NAME_KEY_CONSTRAINT = "UK_ROLES_NAME_KEY";

    /** 設定からロールへの外部キーの名前（V11）。 */
    static final String ROLE_FOREIGN_KEY = "FK_PERMISSION_SETTINGS_ROLE";

    /** 設定の主キーの名前（V11。H2 は違反の文に表の名前を入れるため、表の名前でも見分ける）。 */
    static final String SETTINGS_PRIMARY_KEY = "PK_PERMISSION_SETTINGS";

    /** 設定の表の名前。 */
    static final String SETTINGS_TABLE = "PERMISSION_SETTINGS";

    /** 利用者への割り当ての主キーの名前（V12。H2 は違反の文に表の名前を入れるため、表の名前でも見分ける）。 */
    static final String USER_ASSIGNMENT_PRIMARY_KEY = "PK_USER_ROLE_ASSIGNMENTS";

    /** 利用者への割り当ての表の名前。 */
    static final String USER_ASSIGNMENT_TABLE = "USER_ROLE_ASSIGNMENTS";

    /** グループへの割り当ての主キーの名前（V12）。 */
    static final String GROUP_ASSIGNMENT_PRIMARY_KEY = "PK_GROUP_ROLE_ASSIGNMENTS";

    /** グループへの割り当ての表の名前。 */
    static final String GROUP_ASSIGNMENT_TABLE = "GROUP_ROLE_ASSIGNMENTS";

    /** 作業ロールの保存の主キーの名前（V12）。 */
    static final String SELECTION_PRIMARY_KEY = "PK_WORK_ROLE_SELECTIONS";

    /** 作業ロールの保存の表の名前。 */
    static final String SELECTION_TABLE = "WORK_ROLE_SELECTIONS";

    /** ロールを指す外部キーの名前（V11・V12）。 */
    static final List<String> ROLE_FOREIGN_KEYS =
            List.of(ROLE_FOREIGN_KEY, "FK_USER_ROLE_ASSIGNMENTS_ROLE", "FK_GROUP_ROLE_ASSIGNMENTS_ROLE");

    /** 利用者を指す外部キーの名前（V12）。 */
    static final List<String> USER_FOREIGN_KEYS =
            List.of("FK_USER_ROLE_ASSIGNMENTS_USER", "FK_WORK_ROLE_SELECTIONS_USER");

    /** グループを指す外部キーの名前（V12）。 */
    static final String GROUP_FOREIGN_KEY = "FK_GROUP_ROLE_ASSIGNMENTS_GROUP";

    /** 想定外の例外の文に入れてよい、role の既知の制約の名前（長い名前を先に照らす）。 */
    static final List<String> KNOWN_CONSTRAINTS = List.of(
            "CK_PERMISSION_SETTINGS_COLUMN_AUX",
            "CK_PERMISSION_SETTINGS_LEVEL",
            "CK_PERMISSION_SETTINGS_MAIN",
            "CK_PERMISSION_SETTINGS_ANY",
            "FK_GROUP_ROLE_ASSIGNMENTS_GROUP",
            "FK_GROUP_ROLE_ASSIGNMENTS_ROLE",
            "FK_USER_ROLE_ASSIGNMENTS_ROLE",
            "FK_USER_ROLE_ASSIGNMENTS_USER",
            "FK_WORK_ROLE_SELECTIONS_USER",
            GROUP_ASSIGNMENT_PRIMARY_KEY,
            USER_ASSIGNMENT_PRIMARY_KEY,
            SELECTION_PRIMARY_KEY,
            ROLE_FOREIGN_KEY,
            SETTINGS_PRIMARY_KEY,
            NAME_KEY_CONSTRAINT);

    private RoleStoreClassifier() {}

    /** 連なりから読み取った手がかり（SQLState と、照らすための名前の文）。 */
    private record Evidence(boolean unique, boolean foreignKey, String sqlState, String text) {}

    private static Evidence evidenceOf(Throwable failure) {
        boolean unique = false;
        boolean foreignKey = false;
        String sqlState = null;
        StringBuilder names = new StringBuilder();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = failure; current != null && seen.add(current); current = current.getCause()) {
            if (current instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                names.append(' ').append(violation.getConstraintName());
            }
            if (current instanceof SQLException sql) {
                if (sqlState == null && sql.getSQLState() != null) {
                    sqlState = sql.getSQLState();
                }
                unique |= UNIQUE_VIOLATION.equals(sql.getSQLState());
                foreignKey |= FOREIGN_KEY_VIOLATIONS.contains(sql.getSQLState());
                if (sql.getMessage() != null) {
                    names.append(' ').append(sql.getMessage());
                }
            }
        }
        return new Evidence(unique, foreignKey, sqlState, names.toString().toUpperCase(Locale.ROOT));
    }

    /**
     * 例外を区分する。
     *
     * @param failure 例外
     * @return 区分
     */
    public static Kind classify(Throwable failure) {
        if (RowLockFailures.isLockFailure(failure)) {
            return Kind.LOCK_TIMEOUT;
        }
        Evidence evidence = evidenceOf(failure);
        String text = evidence.text();
        if (evidence.unique()) {
            if (text.contains(NAME_KEY_CONSTRAINT)) {
                return Kind.NAME_TAKEN;
            }
            if (text.contains(SELECTION_PRIMARY_KEY) || text.contains(SELECTION_TABLE)) {
                return Kind.SELECTION_KEY;
            }
            if (text.contains(USER_ASSIGNMENT_PRIMARY_KEY)
                    || text.contains(USER_ASSIGNMENT_TABLE)
                    || text.contains(GROUP_ASSIGNMENT_PRIMARY_KEY)
                    || text.contains(GROUP_ASSIGNMENT_TABLE)) {
                return Kind.ALREADY_ASSIGNED;
            }
        }
        if (evidence.foreignKey()) {
            if (USER_FOREIGN_KEYS.stream().anyMatch(text::contains)) {
                return Kind.REFERENCED_USER;
            }
            if (text.contains(GROUP_FOREIGN_KEY)) {
                return Kind.REFERENCED_GROUP;
            }
            if (ROLE_FOREIGN_KEYS.stream().anyMatch(text::contains)) {
                return Kind.REFERENCED_ROLE;
            }
        }
        return Kind.UNEXPECTED;
    }

    /**
     * 例外を結果の型に変える。上限切れと作業ロールの保存の主キーの違反は WARN（排他の種類と例外のクラスの名前だけ）を1回出してから
     * {@link RoleStoreOutcome.Busy} を返し、想定外は SQLState と既知の制約の名前だけを持つ例外に包み直して投げる。
     *
     * @param <T> 結果の値の型
     * @param failure 例外
     * @param lockKind 上限切れのときの排他の種類
     * @param logger WARN を出す先
     * @return 結果
     * @throws RoleStoreUnexpectedException 想定外のとき
     */
    public static <T> RoleStoreOutcome<T> toOutcome(Throwable failure, String lockKind, Logger logger) {
        return switch (classify(failure)) {
            case LOCK_TIMEOUT -> {
                RowLockFailures.warn(logger, lockKind, failure);
                yield new RoleStoreOutcome.Busy<>(lockKind);
            }
            case SELECTION_KEY -> {
                RowLockFailures.warn(logger, lockKind, failure);
                yield new RoleStoreOutcome.Busy<>(lockKind);
            }
            case NAME_TAKEN -> new RoleStoreOutcome.NameTaken<>();
            case ALREADY_ASSIGNED -> new RoleStoreOutcome.AlreadyAssigned<>();
            case REFERENCED_ROLE -> new RoleStoreOutcome.Referenced<>(Referent.ROLE);
            case REFERENCED_USER -> new RoleStoreOutcome.Referenced<>(Referent.USER);
            case REFERENCED_GROUP -> new RoleStoreOutcome.Referenced<>(Referent.GROUP);
            case UNEXPECTED -> throw unexpected(failure);
        };
    }

    /**
     * 想定外の例外を包み直す（SQLState と既知の制約の名前だけを持つ。計画の 13節 Q5: A）。
     *
     * @param failure 元の例外
     * @return 包み直した例外
     */
    static RoleStoreUnexpectedException unexpected(Throwable failure) {
        Evidence evidence = evidenceOf(failure);
        String constraint = KNOWN_CONSTRAINTS.stream()
                .filter(name -> evidence.text().contains(name))
                .findFirst()
                .orElse(evidence.unique() && evidence.text().contains(SETTINGS_TABLE) ? SETTINGS_PRIMARY_KEY : null);
        return new RoleStoreUnexpectedException(failure, evidence.sqlState(), constraint);
    }
}
