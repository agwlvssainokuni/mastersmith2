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
package cherry.mastersmith.user;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code user}（利用者）の境界の構造の検査（Intent 261004-role-menu の U1、要件 C4、機能設計 9節 5、team.md の Code Style
 * 「境界テストが無ければ足す」）。
 *
 * <p>B2 で {@code user.web} の口に API の分類の印を付けたため、今の依存をそのまま書いて足した。{@code user} がアプリの中で依存して
 * よいのは {@code common} だけで、{@code user} に依存してよいのは {@code audit}・{@code auth}・{@code dslmanage}・
 * {@code group}・{@code invitation}・{@code role}・{@code useradmin} だけ。既存の全体の決まり（ArchitectureTest）とほかの機能の境界テストは変えない。
 *
 * <p>{@code group} は Intent 261004-role-menu の B3（U3）で足した（グループのメンバーの利用者の有無と、まとめて読む口
 * {@code findSummariesByIds}、伏せる型の値。設計の依存の向き group → user）。既存の境界テストを緩める変更として、コード生成の計画に
 * 明記して依頼者の承認を得た（計画の 11節 Q2: A、D-20）。ほかの規則は緩めていない。
 *
 * <p>{@code role} は Intent 261004-role-menu の B4（U4）で足した（出来事と要求の文脈の {@code RequestOrigin}、B5 で利用者の有無と伏せる
 * 型の要約。設計の依存の向き role → user、NFR6.5）。group と同じく既存の境界テストを緩める変更として、U4 のコード生成の計画に明記して
 * 依頼者の承認を得た（計画の 13節 Q1: A、D-35）。ほかの規則は緩めていない。
 */
class UserBoundaryArchitectureTest {

    private static final String USER = "cherry.mastersmith.user..";

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
    @DisplayName("user depends only on common among the application packages")
    void dependsOnlyOnCommon() {
        noClasses()
                .that()
                .resideInAPackage(USER)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "cherry.mastersmith.access..",
                        "cherry.mastersmith.appearance..",
                        "cherry.mastersmith.audit..",
                        "cherry.mastersmith.auth..",
                        "cherry.mastersmith.config..",
                        "cherry.mastersmith.dsl..",
                        "cherry.mastersmith.dslmanage..",
                        "cherry.mastersmith.invitation..",
                        "cherry.mastersmith.mail..",
                        "cherry.mastersmith.targetdb..",
                        "cherry.mastersmith.useradmin..")
                .check(CLASSES);
        assertThat(dependsOnPackage("cherry.mastersmith.user", "cherry.mastersmith.common"))
                .as("規則が依存を見分けている（common は実際に使っている）")
                .isTrue();
    }

    @Test
    @DisplayName("outside user only audit, auth, dslmanage, group, invitation, role and useradmin may depend on it")
    void onlyKnownFeaturesDependOnUser() {
        noClasses()
                .that()
                .resideOutsideOfPackages(
                        USER,
                        "cherry.mastersmith.audit..",
                        "cherry.mastersmith.auth..",
                        "cherry.mastersmith.dslmanage..",
                        "cherry.mastersmith.group..",
                        "cherry.mastersmith.invitation..",
                        "cherry.mastersmith.role..",
                        "cherry.mastersmith.useradmin..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(USER)
                .check(CLASSES);
        assertThat(dependsOnPackage("cherry.mastersmith.invitation", "cherry.mastersmith.user"))
                .as("規則が依存を見分けている（invitation は実際に user を使っている）")
                .isTrue();
    }

    @Test
    @DisplayName("common never depends on user")
    void commonDoesNotDependOnUser() {
        noClasses()
                .that()
                .resideInAPackage("cherry.mastersmith.common..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(USER)
                .check(CLASSES);
    }
}
