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
package cherry.mastersmith.role.domain;

import static cherry.mastersmith.role.domain.InheritedFrom.DEFAULT;
import static cherry.mastersmith.role.domain.InheritedFrom.EXPLICIT;
import static cherry.mastersmith.role.domain.InheritedFrom.SCHEMA;
import static cherry.mastersmith.role.domain.InheritedFrom.TABLE;
import static cherry.mastersmith.role.domain.MainPermission.FULL;
import static cherry.mastersmith.role.domain.MainPermission.NONE;
import static cherry.mastersmith.role.domain.MainPermission.READ;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.role.domain.PermissionInheritance.Resolution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 継承の解決（BR5.1・BR5.2・BR4.11、AC1.2.2〜AC1.2.4）の単体テスト。 */
class PermissionInheritanceTest {

    private static PermissionValues values(MainPermission main, Boolean create, Boolean delete) {
        return new PermissionValues(main, create, delete);
    }

    @Test
    @DisplayName("nothing set anywhere is NONE and not allowed, inherited from the default")
    void defaults() {
        Resolution resolution = PermissionInheritance.resolve(PermissionLevel.COLUMN, null, null, null);

        assertThat(resolution).isEqualTo(new Resolution(EffectivePermission.NONE, DEFAULT, DEFAULT, DEFAULT));
    }

    @Test
    @DisplayName("AC1.2.2: schema FULL, table READ, column NONE give FULL, READ, READ for another column and NONE")
    void exampleOfTheStory() {
        PermissionValues schema = values(FULL, null, null);
        PermissionValues table = values(READ, null, null);
        PermissionValues column = values(NONE, null, null);

        assertThat(PermissionInheritance.resolve(PermissionLevel.SCHEMA, schema, null, null)
                        .effective()
                        .main())
                .isEqualTo(FULL);
        assertThat(PermissionInheritance.resolve(PermissionLevel.TABLE, schema, table, null)
                        .effective()
                        .main())
                .isEqualTo(READ);
        Resolution other = PermissionInheritance.resolve(PermissionLevel.COLUMN, schema, table, null);
        assertThat(other.effective().main()).isEqualTo(READ);
        assertThat(other.mainFrom()).isEqualTo(TABLE);
        Resolution explicit = PermissionInheritance.resolve(PermissionLevel.COLUMN, schema, table, column);
        assertThat(explicit.effective().main()).isEqualTo(NONE);
        assertThat(explicit.mainFrom()).isEqualTo(EXPLICIT);
    }

    @Test
    @DisplayName("a table without its own value inherits the schema value and names the schema as the source")
    void tableInheritsSchema() {
        Resolution resolution = PermissionInheritance.resolve(
                PermissionLevel.TABLE, values(READ, true, false), PermissionValues.NOT_SET, null);

        assertThat(resolution)
                .isEqualTo(new Resolution(new EffectivePermission(READ, true, false), SCHEMA, SCHEMA, SCHEMA));
    }

    @Test
    @DisplayName("CREATE and DELETE are resolved separately and do not affect each other (AC1.2.4)")
    void auxiliariesAreIndependent() {
        Resolution resolution = PermissionInheritance.resolve(
                PermissionLevel.TABLE, values(null, null, true), values(null, true, null), null);

        assertThat(resolution.effective()).isEqualTo(new EffectivePermission(NONE, true, true));
        assertThat(resolution.createFrom()).isEqualTo(EXPLICIT);
        assertThat(resolution.deleteFrom()).isEqualTo(SCHEMA);
        assertThat(resolution.mainFrom()).isEqualTo(DEFAULT);
    }

    @Test
    @DisplayName("a column takes the auxiliary permissions of its table, never its own")
    void columnAuxiliaryIsTheTable() {
        Resolution resolution = PermissionInheritance.resolve(
                PermissionLevel.COLUMN, values(null, false, true), values(null, true, null), values(READ, null, null));

        assertThat(resolution.effective()).isEqualTo(new EffectivePermission(READ, true, true));
        assertThat(resolution.createFrom()).isEqualTo(TABLE);
        assertThat(resolution.deleteFrom()).isEqualTo(SCHEMA);
    }

    @Test
    @DisplayName("values below the level of the target are not used, and the own level is EXPLICIT")
    void lowerLevelsAreIgnored() {
        Resolution schema = PermissionInheritance.resolve(
                PermissionLevel.SCHEMA, values(READ, null, null), values(FULL, true, true), values(NONE, null, null));
        Resolution table = PermissionInheritance.resolve(
                PermissionLevel.TABLE, PermissionValues.NOT_SET, values(FULL, null, null), values(NONE, null, null));

        assertThat(schema)
                .isEqualTo(new Resolution(new EffectivePermission(READ, false, false), EXPLICIT, DEFAULT, DEFAULT));
        assertThat(table.effective().main()).isEqualTo(FULL);
        assertThat(table.mainFrom()).isEqualTo(EXPLICIT);
    }

    @Test
    @DisplayName("the formula helpers follow column ?? table ?? schema ?? NONE and table ?? schema ?? false")
    void formulas() {
        assertThat(PermissionInheritance.main(null, null, null)).isEqualTo(NONE);
        assertThat(PermissionInheritance.main(FULL, null, null)).isEqualTo(FULL);
        assertThat(PermissionInheritance.main(FULL, READ, null)).isEqualTo(READ);
        assertThat(PermissionInheritance.main(FULL, READ, NONE)).isEqualTo(NONE);
        assertThat(PermissionInheritance.auxiliary(null, null)).isFalse();
        assertThat(PermissionInheritance.auxiliary(true, null)).isTrue();
        assertThat(PermissionInheritance.auxiliary(true, false)).isFalse();
    }
}
