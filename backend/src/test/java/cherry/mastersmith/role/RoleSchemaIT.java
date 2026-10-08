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
package cherry.mastersmith.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionSetting;
import cherry.mastersmith.role.domain.PermissionSettingId;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.domain.Role;
import cherry.mastersmith.role.domain.RoleName;
import cherry.mastersmith.role.domain.RoleNameValidation;
import jakarta.persistence.EntityManager;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ロールの表と権限の設定の表（V11）の結合テスト（組み込みの H2。計画の 7.3、NFR3.6、6節 A4）。
 *
 * <p>制約の名前（store の区分が読み替え先を決めるのに使う）、検査の制約、列の長さ、エンティティの読み書き（JPQL のエンティティ名
 * {@code Role}・{@code PermissionSetting} が予約語に当たらないこと）、{@code users.admin_flag} が残ることを確かめる。
 */
@SpringBootTest
class RoleSchemaIT {

    private static final Instant NOW = Instant.parse("2026-10-08T05:00:00Z");

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    EntityManager entityManager;

    private static RoleName name(String raw) {
        return ((RoleNameValidation.Valid) RoleName.parse(raw)).name();
    }

    private long insertRole(String raw) {
        Role role = tx.execute(status -> {
            Role created = new Role(name(raw), NOW);
            entityManager.persist(created);
            return created;
        });
        return role.getRoleId();
    }

    private int insertSetting(long roleId, String schema, String table, String column, String main, Boolean create) {
        return jdbc.update(
                "INSERT INTO permission_settings (role_id, schema_name, table_name, column_name, main_permission,"
                        + " create_permission, delete_permission, updated_at) VALUES (?, ?, ?, ?, ?, ?, NULL, ?)",
                roleId,
                schema,
                table,
                column,
                main,
                create,
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("V11 is applied at startup and users.admin_flag is kept")
    void migrationApplied() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT \"version\", \"success\" FROM \"flyway_schema_history\" WHERE \"version\" = '11'");
        List<String> userColumns = jdbc.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'USERS'", String.class);

        assertThat(rows).containsExactly(Map.of("version", "11", "success", true));
        assertThat(userColumns).contains("ADMIN_FLAG");
    }

    @Test
    @DisplayName("V11 creates the named constraints and the column lengths of 128 and 256")
    void constraintsAndLengths() {
        List<String> constraints = jdbc.queryForList(
                "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS"
                        + " WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME IN ('ROLES', 'PERMISSION_SETTINGS')"
                        + " ORDER BY CONSTRAINT_NAME",
                String.class);
        List<Map<String, Object>> lengths = jdbc.queryForList(
                "SELECT TABLE_NAME, COLUMN_NAME, CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS"
                        + " WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME IN ('ROLES', 'PERMISSION_SETTINGS')"
                        + " AND CHARACTER_MAXIMUM_LENGTH IS NOT NULL"
                        + " ORDER BY TABLE_NAME, COLUMN_NAME");

        assertThat(constraints)
                .contains(
                        "CK_PERMISSION_SETTINGS_ANY",
                        "CK_PERMISSION_SETTINGS_COLUMN_AUX",
                        "CK_PERMISSION_SETTINGS_LEVEL",
                        "CK_PERMISSION_SETTINGS_MAIN",
                        "FK_PERMISSION_SETTINGS_ROLE",
                        "PK_PERMISSION_SETTINGS",
                        "UK_ROLES_NAME_KEY");
        assertThat(lengths)
                .containsExactly(
                        Map.of(
                                "TABLE_NAME",
                                "PERMISSION_SETTINGS",
                                "COLUMN_NAME",
                                "COLUMN_NAME",
                                "CHARACTER_MAXIMUM_LENGTH",
                                256L),
                        Map.of(
                                "TABLE_NAME",
                                "PERMISSION_SETTINGS",
                                "COLUMN_NAME",
                                "MAIN_PERMISSION",
                                "CHARACTER_MAXIMUM_LENGTH",
                                8L),
                        Map.of(
                                "TABLE_NAME",
                                "PERMISSION_SETTINGS",
                                "COLUMN_NAME",
                                "SCHEMA_NAME",
                                "CHARACTER_MAXIMUM_LENGTH",
                                256L),
                        Map.of(
                                "TABLE_NAME",
                                "PERMISSION_SETTINGS",
                                "COLUMN_NAME",
                                "TABLE_NAME",
                                "CHARACTER_MAXIMUM_LENGTH",
                                256L),
                        Map.of("TABLE_NAME", "ROLES", "COLUMN_NAME", "NAME", "CHARACTER_MAXIMUM_LENGTH", 128L),
                        Map.of("TABLE_NAME", "ROLES", "COLUMN_NAME", "NAME_KEY", "CHARACTER_MAXIMUM_LENGTH", 256L));
    }

    @Test
    @DisplayName(
            "the check constraints reject an empty row, a column without a table, column auxiliaries and other values")
    void checkConstraints() {
        long roleId = insertRole("検査の制約");

        assertThatThrownBy(() -> insertSetting(roleId, "S", "", "", null, null))
                .as("3つの値がすべて設定なし")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("CK_PERMISSION_SETTINGS_ANY");
        assertThatThrownBy(() -> insertSetting(roleId, "S", "", "C", "READ", null))
                .as("テーブルの無いカラム")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("CK_PERMISSION_SETTINGS_LEVEL");
        assertThatThrownBy(() -> insertSetting(roleId, "S", "T", "C", "READ", true))
                .as("カラムの補助権限")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("CK_PERMISSION_SETTINGS_COLUMN_AUX");
        assertThatThrownBy(() -> insertSetting(roleId, "S", "T", "", "ADMIN", null))
                .as("許していない主権限の値")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("CK_PERMISSION_SETTINGS_MAIN");
        assertThat(insertSetting(roleId, "S", "", "", null, false)).isEqualTo(1);
        assertThat(insertSetting(roleId, "S", "T", "C", "NONE", null)).isEqualTo(1);
    }

    @Test
    @DisplayName("the name key is unique, and a role with settings cannot be deleted before its settings")
    void uniqueAndForeignKey() {
        long roleId = insertRole("Sales-一意");
        insertSetting(roleId, "S", "T", "", "FULL", null);

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO roles (name, name_key, created_at, updated_at) VALUES (?, ?, ?, ?)",
                        "SALES-一意",
                        name("SALES-一意").key(),
                        OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC),
                        OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("UK_ROLES_NAME_KEY");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM roles WHERE role_id = ?", roleId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("FK_PERMISSION_SETTINGS_ROLE");
        assertThatThrownBy(() -> insertSetting(Long.MAX_VALUE, "S", "", "", "READ", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("FK_PERMISSION_SETTINGS_ROLE");
    }

    @Test
    @DisplayName("the entities Role and PermissionSetting are written and read through JPQL and the native table names")
    void entitiesRoundTrip() {
        long roleId = insertRole("エンティティ");
        PermissionTarget column = PermissionTarget.column("SALES", "ORDER_LINE", "UNIT_PRICE");
        PermissionTarget table = PermissionTarget.table("SALES", "ORDER_LINE");
        tx.executeWithoutResult(status -> {
            entityManager.persist(
                    new PermissionSetting(roleId, table, new PermissionValues(MainPermission.READ, true, null), NOW));
            entityManager.persist(
                    new PermissionSetting(roleId, column, new PermissionValues(MainPermission.NONE, null, null), NOW));
        });

        List<String> names = tx.execute(status -> entityManager
                .createQuery("select r.name from Role r where r.roleId = :roleId", String.class)
                .setParameter("roleId", roleId)
                .getResultList());
        PermissionSetting found = tx.execute(
                status -> entityManager.find(PermissionSetting.class, new PermissionSettingId(roleId, column)));
        Long count = tx.execute(status -> entityManager
                .createQuery("select count(p) from PermissionSetting p where p.roleId = :roleId", Long.class)
                .setParameter("roleId", roleId)
                .getSingleResult());

        assertThat(names).containsExactly("エンティティ");
        assertThat(found.target()).isEqualTo(column);
        assertThat(found.values()).isEqualTo(new PermissionValues(MainPermission.NONE, null, null));
        assertThat(found.toString()).doesNotContain("UNIT_PRICE").doesNotContain("ORDER_LINE");
        assertThat(count).isEqualTo(2L);
        assertThat(jdbc.queryForMap(
                        "SELECT table_name, column_name, main_permission, create_permission FROM permission_settings"
                                + " WHERE role_id = ? AND column_name = ''",
                        roleId))
                .containsEntry("TABLE_NAME", "ORDER_LINE")
                .containsEntry("COLUMN_NAME", "")
                .containsEntry("MAIN_PERMISSION", "READ")
                .containsEntry("CREATE_PERMISSION", true);
        assertThat(jdbc.queryForObject("SELECT name_key FROM roles WHERE role_id = ?", String.class, roleId))
                .isEqualTo("エンティティ");
    }
}
