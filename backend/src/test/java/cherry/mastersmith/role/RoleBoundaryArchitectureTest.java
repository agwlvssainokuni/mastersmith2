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

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code role}（ロールと権限）の境界の構造の検査（Intent 261004-role-menu の B3 で足した最小の形。計画の 11節 Q1: A・D-19）。
 *
 * <p>B3 の {@code role} は group の問う口の仮の実装だけを持つ。アプリの中で依存してよいのは {@code group.service}（口だけ）と
 * {@code common} だけ。B4 以降は role の Bolt が規則を足す（user に依存するなら、{@code UserBoundaryArchitectureTest} を緩める承認を
 * 同じように得る）。
 */
class RoleBoundaryArchitectureTest {

    private static final String ROLE = "cherry.mastersmith.role..";

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("cherry.mastersmith");

    private static boolean dependsOnPackage(String fromPrefix, String targetPrefix) {
        return CLASSES.stream()
                .filter(javaClass -> javaClass.getPackageName().startsWith(fromPrefix))
                .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                .anyMatch(dependency ->
                        dependency.getTargetClass().getPackageName().startsWith(targetPrefix));
    }

    @Test
    @DisplayName("role depends only on group.service and common among the application packages")
    void dependsOnlyOnGroupServiceAndCommon() {
        noClasses()
                .that()
                .resideInAPackage(ROLE)
                .should()
                .dependOnClassesThat(resideInAPackage("cherry.mastersmith..")
                        .and(resideOutsideOfPackages(
                                ROLE, "cherry.mastersmith.group.service..", "cherry.mastersmith.common..")))
                .check(CLASSES);
        assertThat(dependsOnPackage("cherry.mastersmith.role", "cherry.mastersmith.group.service"))
                .as("規則が依存を見分けている（role は実際に group.service の口を使っている）")
                .isTrue();
    }
}
