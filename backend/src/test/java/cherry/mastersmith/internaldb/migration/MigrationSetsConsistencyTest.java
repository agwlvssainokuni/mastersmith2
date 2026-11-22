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
package cherry.mastersmith.internaldb.migration;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.internaldb.testsupport.MigrationObjectNames;
import cherry.mastersmith.internaldb.testsupport.MigrationObjectNames.MigrationFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * H2 用（{@code db/migration}）と PostgreSQL 用（{@code db/migration-postgresql}）の移行の突き合わせ（BR2.8 の (c)〜(f)、FR2.1・FR3.2・
 * FR3.6）。2 つの置き場がずれないことを、版・名前・禁止の書き方で確かめる。
 */
class MigrationSetsConsistencyTest {

    private static final String H2 = "db/migration";
    private static final String POSTGRES = "db/migration-postgresql";

    private final SortedMap<Integer, MigrationFile> h2 = MigrationObjectNames.load(H2);
    private final SortedMap<Integer, MigrationFile> postgres = MigrationObjectNames.load(POSTGRES);

    @Test
    @DisplayName("both locations hold the same versions and descriptions, and the versions run 1..N without gaps")
    void versionsAndDescriptionsAreTheSame() {
        assertThat(versionAndDescription(postgres)).isEqualTo(versionAndDescription(h2));
        assertThat(postgres).hasSize(14);
        assertThat(contiguousFromOne(postgres.keySet())).isTrue();
        assertThat(contiguousFromOne(h2.keySet())).isTrue();
    }

    @Test
    @DisplayName("the PostgreSQL file names are exactly the H2 file names")
    void fileNamesAreTheSame() {
        List<String> h2Names = h2.values().stream().map(MigrationFile::fileName).toList();
        List<String> postgresNames =
                postgres.values().stream().map(MigrationFile::fileName).toList();

        assertThat(postgresNames).isEqualTo(h2Names);
    }

    @Test
    @DisplayName("for each version the PostgreSQL file declares the same tables, named constraints and indexes as H2")
    void objectNamesAreTheSamePerVersion() {
        for (var entry : h2.entrySet()) {
            Set<String> expected =
                    MigrationObjectNames.objectNames(entry.getValue().sql());
            Set<String> actual = MigrationObjectNames.objectNames(
                    postgres.get(entry.getKey()).sql());

            assertThat(actual).as("object names of V%d", entry.getKey()).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName(
            "the seven foreign keys of V10 to V12 are present with the same constraint names and without ON DELETE")
    void foreignKeysKeepTheirNames() {
        List<String> foreignKeys = List.of(
                "fk_group_members_group",
                "fk_permission_settings_role",
                "fk_user_role_assignments_role",
                "fk_user_role_assignments_user",
                "fk_group_role_assignments_role",
                "fk_group_role_assignments_group",
                "fk_work_role_selections_user");
        String h2Sql = String.join(
                "\n",
                h2.subMap(10, 13).values().stream().map(MigrationFile::sql).toList());
        String postgresSql = MigrationObjectNames.stripComments(String.join(
                "\n",
                postgres.subMap(10, 13).values().stream()
                        .map(MigrationFile::sql)
                        .toList()));

        for (String name : foreignKeys) {
            assertThat(h2Sql).contains(name);
            assertThat(postgresSql).contains(name);
        }
        assertThat(postgresSql.toUpperCase()).doesNotContain("ON DELETE");
    }

    @ParameterizedTest(name = "PostgreSQL migrations do not use {0} outside comments")
    @ValueSource(strings = {"ON DELETE", "BINARY(", "BINARY LARGE OBJECT"})
    @DisplayName("PostgreSQL migrations avoid the H2-only spellings (comments are stripped before searching)")
    void postgresMigrationsAvoidH2Spellings(String forbidden) {
        for (MigrationFile file : postgres.values()) {
            String body = MigrationObjectNames.stripComments(file.sql()).toUpperCase();

            assertThat(body).as("%s in %s", forbidden, file.fileName()).doesNotContain(forbidden);
        }
    }

    @Test
    @DisplayName("the binary columns are BYTEA and the generated column is STORED in the PostgreSQL files")
    void binaryColumnsAndGeneratedColumn() {
        assertThat(postgres.get(3).sql()).contains("token_hash BYTEA NOT NULL");
        assertThat(postgres.get(8).sql()).contains("token_hash BYTEA NOT NULL");
        assertThat(postgres.get(5).sql()).contains("yaml_bytes BYTEA NOT NULL");
        assertThat(MigrationObjectNames.stripComments(postgres.get(8).sql())).contains("STORED");
        assertThat(postgres.get(8).sql()).contains("uk_invitations_pending_email UNIQUE (pending_email)");
    }

    @Test
    @DisplayName("the comparison fails when a name is added on one side")
    void detectsAnAddedName() {
        String base = "CREATE TABLE a (id BIGINT, CONSTRAINT uk_a UNIQUE (id));";
        String added = base + "\nCREATE INDEX ix_a ON a (id);";

        assertThat(MigrationObjectNames.objectNames(added)).isNotEqualTo(MigrationObjectNames.objectNames(base));
        assertThat(MigrationObjectNames.objectNames(base)).containsExactly("a", "uk_a");
    }

    @Test
    @DisplayName("the comparison fails when a version is missing")
    void detectsAMissingVersion() {
        SortedMap<Integer, MigrationFile> withGap = new TreeMap<>(postgres);
        withGap.remove(7);

        assertThat(contiguousFromOne(withGap.keySet())).isFalse();
        assertThat(versionAndDescription(withGap)).isNotEqualTo(versionAndDescription(h2));
    }

    @Test
    @DisplayName("the comparison fails when a file is changed so that a table disappears")
    void detectsAChangedFile() {
        String original = postgres.get(9).sql();
        String changed = original.replace("ALTER TABLE users", "ALTER TABLE accounts");

        assertThat(changed).isNotEqualTo(original);
        // 9 は表を作らないので名前は変わらない。表を作る V4 の表の名前を変えた例で、差を検出できることを確かめる。
        String v4 = postgres.get(4).sql();
        String renamed = v4.replace("CREATE TABLE audit_events", "CREATE TABLE audit_events_x");

        assertThat(MigrationObjectNames.objectNames(renamed)).isNotEqualTo(MigrationObjectNames.objectNames(v4));
    }

    @Test
    @DisplayName("comments are stripped before names are extracted")
    void commentsAreIgnored() {
        String sql = "-- CREATE TABLE hidden (x INT);\nCREATE TABLE shown (x INT); -- CONSTRAINT nope\n";

        assertThat(MigrationObjectNames.objectNames(sql)).containsExactly("shown");
    }

    private static List<String> versionAndDescription(SortedMap<Integer, MigrationFile> files) {
        List<String> out = new ArrayList<>();
        files.forEach((version, file) -> out.add(version + ":" + file.description()));
        return out;
    }

    private static boolean contiguousFromOne(Set<Integer> versions) {
        int expected = 1;
        for (int version : versions) {
            if (version != expected++) {
                return false;
            }
        }
        return true;
    }
}
