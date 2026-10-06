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
package cherry.mastersmith.access;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code access}（アクセスの決まり・管理者の判定）の境界の構造の検査（Intent 261004-role-menu の U1、要件 C4、security-design 4.6、
 * team.md の Code Style「境界テストが無ければ足す」）。
 *
 * <p>B2 で {@code access.web} の口に API の分類の印を付けたため、今の依存をそのまま書いて足した。{@code access} が依存してよいのは
 * {@code auth.domain}・{@code auth.web}・{@code common}・{@code config} で、{@code access} に依存してよいのは {@code audit}・
 * {@code useradmin} だけ。既存の全体の決まり（ArchitectureTest）とほかの機能の境界テストは変えない。
 */
class AccessBoundaryArchitectureTest {

    private static final String ACCESS = "cherry.mastersmith.access..";

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
    @DisplayName("access depends only on auth.domain, auth.web, common and config among the application packages")
    void allowedDependencies() {
        noClasses()
                .that()
                .resideInAPackage(ACCESS)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "cherry.mastersmith.auth.service..",
                        "cherry.mastersmith.auth.repository..",
                        "cherry.mastersmith.appearance..",
                        "cherry.mastersmith.audit..",
                        "cherry.mastersmith.dsl..",
                        "cherry.mastersmith.dslmanage..",
                        "cherry.mastersmith.invitation..",
                        "cherry.mastersmith.mail..",
                        "cherry.mastersmith.targetdb..",
                        "cherry.mastersmith.user..",
                        "cherry.mastersmith.useradmin..")
                .check(CLASSES);
        assertThat(dependsOnPackage("cherry.mastersmith.access", "cherry.mastersmith.auth.domain"))
                .as("規則が依存を見分けている（auth.domain は実際に使っている）")
                .isTrue();
    }

    @Test
    @DisplayName("outside access only audit and useradmin may depend on it")
    void onlyAuditAndUserAdminDependOnAccess() {
        noClasses()
                .that()
                .resideOutsideOfPackages(ACCESS, "cherry.mastersmith.audit..", "cherry.mastersmith.useradmin..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(ACCESS)
                .check(CLASSES);
        assertThat(dependsOnPackage("cherry.mastersmith.audit", "cherry.mastersmith.access"))
                .as("規則が依存を見分けている（audit は実際に access を使っている）")
                .isTrue();
    }

    @Test
    @DisplayName("access never depends on the user feature even after the ApiAccess marks")
    void noUserDependency() {
        noClasses()
                .that()
                .resideInAPackage(ACCESS)
                .should()
                .dependOnClassesThat()
                .resideInAPackage("cherry.mastersmith.user..")
                .check(CLASSES);
    }
}
