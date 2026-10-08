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
package cherry.mastersmith.role.testsupport;

import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.domain.RoleName;
import cherry.mastersmith.role.domain.RoleNameValidation;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * role の結合テストの前提を作る手伝い（計画の 7.3）。ロールと設定の行を本番のコードの書き込みを通さずに作る（テストのためだけの API・
 * 操作を本番のコードに足さないため。group の {@code GroupFixtures} と同じ形）。名前の鍵は本番と同じく {@link RoleName} から作る。
 */
public final class RoleFixtures {

    /** 作成と更新の決まった日時。 */
    public static final Instant CREATED_AT = Instant.parse("2026-10-02T00:00:00Z");

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private final JdbcTemplate jdbc;

    /**
     * 作る。
     *
     * @param jdbc 内部DB への問い合わせ
     */
    public RoleFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 重ならないロールの名前を作る。
     *
     * @param prefix 名前の頭
     * @return 名前
     */
    public static String uniqueName(String prefix) {
        return prefix + "-" + SEQUENCE.incrementAndGet() + "-"
                + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 名前を検証済みの値にする。
     *
     * @param raw 名前（規則に合う値）
     * @return 名前
     */
    public static RoleName name(String raw) {
        return ((RoleNameValidation.Valid) RoleName.parse(raw)).name();
    }

    /**
     * ロールを作る（作成と更新の日時は決まった値）。
     *
     * @param raw 名前（{@link RoleName} の規則に合う値）
     * @return ロールの ID
     */
    public long role(String raw) {
        RoleName roleName = name(raw);
        KeyHolder keys = new GeneratedKeyHolder();
        OffsetDateTime at = OffsetDateTime.ofInstant(CREATED_AT, ZoneOffset.UTC);
        jdbc.update(
                connection -> {
                    var statement = connection.prepareStatement(
                            "INSERT INTO roles (name, name_key, created_at, updated_at) VALUES (?, ?, ?, ?)",
                            new String[] {"role_id"});
                    statement.setString(1, roleName.value());
                    statement.setString(2, roleName.key());
                    statement.setObject(3, at);
                    statement.setObject(4, at);
                    return statement;
                },
                keys);
        return keys.getKeyAs(Long.class);
    }

    /**
     * 設定の行を足す。
     *
     * @param roleId ロールの ID
     * @param target 対象
     * @param values 値の組（少なくとも1つが設定されていること）
     */
    public void setting(long roleId, PermissionTarget target, PermissionValues values) {
        MainPermission main = values.main();
        jdbc.update(
                "INSERT INTO permission_settings (role_id, schema_name, table_name, column_name, main_permission,"
                        + " create_permission, delete_permission, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                roleId,
                target.schemaName(),
                target.storedTableName(),
                target.storedColumnName(),
                main == null ? null : main.name(),
                values.create(),
                values.delete(),
                OffsetDateTime.ofInstant(CREATED_AT, ZoneOffset.UTC));
    }

    /**
     * 主権限だけの設定の行を足す。
     *
     * @param roleId ロールの ID
     * @param target 対象
     * @param main 主権限
     */
    public void setting(long roleId, PermissionTarget target, MainPermission main) {
        setting(roleId, target, new PermissionValues(main, null, null));
    }

    /**
     * ロールの行の数を返す。
     *
     * @param roleId ロールの ID
     * @return 行があれば 1、無ければ 0
     */
    public int roleRows(long roleId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM roles WHERE role_id = ?", Integer.class, roleId);
        return count == null ? 0 : count;
    }

    /**
     * ロールの設定の行の数を返す。
     *
     * @param roleId ロールの ID
     * @return 行の数
     */
    public int settingRows(long roleId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM permission_settings WHERE role_id = ?", Integer.class, roleId);
        return count == null ? 0 : count;
    }

    /**
     * ロールの名前と更新の日時を読む。
     *
     * @param roleId ロールの ID
     * @return 列の名前（大文字）と値
     */
    public Map<String, Object> roleRow(long roleId) {
        return jdbc.queryForMap("SELECT name, name_key, updated_at FROM roles WHERE role_id = ?", roleId);
    }
}
