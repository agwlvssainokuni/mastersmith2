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
package cherry.mastersmith.group.store;

import cherry.mastersmith.common.persistence.RowLockFailures;
import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;

/**
 * 排他と書き込みの例外の区分（{@code reliability-design.md} 1.2 の T7・2.2、NFR3.3・NFR1.8）。
 *
 * <p>区分の順:
 *
 * <ol>
 *   <li>{@link RowLockFailures#isLockFailure(Throwable)} が真なら待ちの上限切れ（行の排他・一意の鍵・主キーの待ち）
 *   <li>原因の連なりに SQLState 23505（一意・主キー）か 23503・23506（外部キー。H2 は親の削除を 23503、親の無い追加を 23506 にする）
 *       があれば違反とし、制約の名前で読み替え先を決める（{@code uk_groups_name_key} → 名前の重なり、{@code group_members} の主キー →
 *       すでにメンバー、{@code fk_group_members_group} と U4 role の {@code fk_group_role_assignments_group} → グループを指す外部キー）
 *   <li>どれにも当たらなければ想定外
 * </ol>
 *
 * <p>制約の名前は、Hibernate の {@link ConstraintViolationException#getConstraintName()} と、連なりの {@link SQLException} の文から
 * 探す。文には行の値（名前・ID）が入るため、このクラスの中で照らすだけで、ログ・例外・戻り値に載せない（BR9.3）。
 */
public final class StoreFailureClassifier {

    /** 例外の区分。 */
    public enum Kind {
        /** 待ちの上限切れ（行き詰まりを含む）。 */
        LOCK_TIMEOUT,
        /** 名前の鍵の一意の制約の違反。 */
        NAME_TAKEN,
        /** メンバーの主キーの違反。 */
        ALREADY_MEMBER,
        /** グループを指す外部キーの違反（メンバーとロールの割り当て）。 */
        REFERENCED,
        /** 想定外。 */
        UNEXPECTED
    }

    /** 一意・主キーの違反の SQLState。 */
    static final String UNIQUE_VIOLATION = "23505";

    /** 外部キーの違反の SQLState（子が残る親の削除 23503、親の無い子の追加 23506）。 */
    static final Set<String> FOREIGN_KEY_VIOLATIONS = Set.of("23503", "23506");

    /** 名前の鍵の一意の制約の名前（V10）。 */
    static final String NAME_KEY_CONSTRAINT = "UK_GROUPS_NAME_KEY";

    /** メンバーの表の名前（主キーの違反の文に表の名前が入る。主キーの索引の名前は H2 が付けるため表の名前で見分ける）。 */
    static final String MEMBERS_TABLE = "GROUP_MEMBERS";

    /** メンバーの主キーの制約の名前（V10）。 */
    static final String MEMBERS_PRIMARY_KEY = "PK_GROUP_MEMBERS";

    /** メンバーからグループへの外部キーの名前（V10）。 */
    static final String GROUP_FOREIGN_KEY = "FK_GROUP_MEMBERS_GROUP";

    /**
     * U4 role のグループへの割り当てからグループへの外部キーの名前（V12）。ロールの割り当てが残るグループの削除は問う口とグループの行の
     * 排他で断るため API からは届かないが、届いたときも {@code GROUP_IN_USE} に読み替える最後の守り（Intent 261004-role-menu の U4 の
     * 計画 13節 Q4: A・D-18）。
     */
    static final String ROLE_ASSIGNMENT_FOREIGN_KEY = "FK_GROUP_ROLE_ASSIGNMENTS_GROUP";

    private StoreFailureClassifier() {}

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
        boolean unique = false;
        boolean foreignKey = false;
        StringBuilder names = new StringBuilder();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = failure; current != null && seen.add(current); current = current.getCause()) {
            if (current instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                names.append(' ').append(violation.getConstraintName());
            }
            if (current instanceof SQLException sql) {
                unique |= UNIQUE_VIOLATION.equals(sql.getSQLState());
                foreignKey |= FOREIGN_KEY_VIOLATIONS.contains(sql.getSQLState());
                if (sql.getMessage() != null) {
                    names.append(' ').append(sql.getMessage());
                }
            }
        }
        String text = names.toString().toUpperCase(Locale.ROOT);
        if (unique && text.contains(NAME_KEY_CONSTRAINT)) {
            return Kind.NAME_TAKEN;
        }
        if (unique && (text.contains(MEMBERS_PRIMARY_KEY) || text.contains(MEMBERS_TABLE))) {
            return Kind.ALREADY_MEMBER;
        }
        if (foreignKey && (text.contains(GROUP_FOREIGN_KEY) || text.contains(ROLE_ASSIGNMENT_FOREIGN_KEY))) {
            return Kind.REFERENCED;
        }
        return Kind.UNEXPECTED;
    }

    /**
     * 例外を結果の型に変える。上限切れは WARN（排他の種類と例外のクラスの名前だけ）を1回出してから {@link StoreOutcome.Busy} を返し、
     * 想定外はクラスの名前だけを持つ例外に包み直して投げる。
     *
     * @param <T> 結果の値の型
     * @param failure 例外
     * @param lockKind 上限切れのときの排他の種類
     * @param logger WARN を出す先
     * @return 結果
     * @throws GroupStoreUnexpectedException 想定外のとき
     */
    public static <T> StoreOutcome<T> toOutcome(Throwable failure, String lockKind, Logger logger) {
        return switch (classify(failure)) {
            case LOCK_TIMEOUT -> {
                RowLockFailures.warn(logger, lockKind, failure);
                yield new StoreOutcome.Busy<>(lockKind);
            }
            case NAME_TAKEN -> new StoreOutcome.NameTaken<>();
            case ALREADY_MEMBER -> new StoreOutcome.AlreadyMember<>();
            case REFERENCED -> new StoreOutcome.Referenced<>();
            case UNEXPECTED -> throw new GroupStoreUnexpectedException(failure);
        };
    }
}
