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
package cherry.mastersmith.navigation;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code navigation}（業務のメニュー）の境界の構造の検査（Intent 261004-role-menu の U5、BR7.1・BR7.4、NFR1.6・NFR6.5、
 * {@code logical-components.md} の 2節、{@code team.md} の Code Style「新しく作る機能は境界テストを置く」）。
 *
 * <ul>
 *   <li>{@code navigation} がアプリの中で依存してよいのは {@code dsl.service}・{@code dsl.domain}・{@code role.service}・
 *       {@code role.domain}・{@code auth.domain}・{@code common} だけ
 *   <li>{@code access} に依存しない（{@code ACCESS_DENIED} は共通の問題の種類の一覧から code で引く。計画の 13節 Q2: A）。
 *       {@code dsl.parse}・{@code dsl.validate}・{@code role.repository}・{@code role.store}・{@code role.transfer} に依存しない
 *   <li>どの機能も {@code navigation} に依存しない
 *   <li>{@link cherry.mastersmith.navigation.service.NavigationBarrier} の本番の実装は {@code NoOpNavigationBarrier} だけ
 *   <li>{@code AuthenticatedUser} と user の型は {@code navigation.web} だけが読み、service・domain に現れない（主体は web で
 *       利用者 ID にして渡す）
 *   <li>repository の層を持たず、DB の部品（{@code EntityManager}・{@code JdbcTemplate}）に依存しない（読み取りだけ。BR6.2）
 * </ul>
 *
 * <p>規則が空振りしないよう、各規則に「規則が依存を見分けている」ことの確かめを添える（既存の境界テストと同じ形）。
 */
class NavigationBoundaryArchitectureTest {

    private static final String NAVIGATION = "cherry.mastersmith.navigation..";

    private static final String BARRIER = "cherry.mastersmith.navigation.service.NavigationBarrier";

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

    private static boolean dependsOnClass(String fromPrefix, String targetClassName) {
        return CLASSES.stream()
                .filter(javaClass -> javaClass.getPackageName().startsWith(fromPrefix))
                .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                .anyMatch(dependency -> dependency.getTargetClass().getName().equals(targetClassName));
    }

    @Test
    @DisplayName("navigation depends only on the service and domain of dsl and role, on auth.domain and on common")
    void dependsOnlyOnAllowedPackages() {
        noClasses()
                .that()
                .resideInAPackage(NAVIGATION)
                .should()
                .dependOnClassesThat(resideInAPackage("cherry.mastersmith..")
                        .and(resideOutsideOfPackages(
                                NAVIGATION,
                                "cherry.mastersmith.dsl.service..",
                                "cherry.mastersmith.dsl.domain..",
                                "cherry.mastersmith.role.service..",
                                "cherry.mastersmith.role.domain..",
                                "cherry.mastersmith.auth.domain..",
                                "cherry.mastersmith.common..")))
                .check(CLASSES);
        noClasses()
                .that()
                .resideInAPackage(NAVIGATION)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "cherry.mastersmith.access..",
                        "cherry.mastersmith.dsl.parse..",
                        "cherry.mastersmith.dsl.validate..",
                        "cherry.mastersmith.role.repository..",
                        "cherry.mastersmith.role.store..",
                        "cherry.mastersmith.role.transfer..",
                        "cherry.mastersmith.role.web..")
                .check(CLASSES);
        assertThat(dependsOnPackage("cherry.mastersmith.navigation", "cherry.mastersmith.role.service"))
                .as("規則が依存を見分けている（navigation は実際に role の解決の口を使っている）")
                .isTrue();
        assertThat(dependsOnPackage("cherry.mastersmith.navigation", "cherry.mastersmith.dsl.service"))
                .isTrue();
        assertThat(CLASSES.contain("cherry.mastersmith.access.domain.AccessProblemTypes"))
                .as("規則が依存を見分けている（access には ACCESS_DENIED の定義がある）")
                .isTrue();
    }

    @Test
    @DisplayName("no other feature depends on navigation")
    void noFeatureDependsOnNavigation() {
        noClasses()
                .that()
                .resideOutsideOfPackage(NAVIGATION)
                .should()
                .dependOnClassesThat()
                .resideInAPackage(NAVIGATION)
                .check(CLASSES);
        assertThat(CLASSES.stream()
                        .filter(javaClass -> javaClass.getPackageName().startsWith("cherry.mastersmith.navigation")))
                .as("navigation に部品がある")
                .isNotEmpty();
    }

    @Test
    @DisplayName("the only production implementation of NavigationBarrier is NoOpNavigationBarrier")
    void onlyNoOpBarrier() {
        List<String> implementations = CLASSES.stream()
                .filter(javaClass -> !javaClass.isInterface())
                .filter(javaClass -> javaClass.isAssignableTo(BARRIER))
                .map(JavaClass::getName)
                .toList();

        assertThat(implementations).containsExactly("cherry.mastersmith.navigation.service.NoOpNavigationBarrier");
    }

    @Test
    @DisplayName("AuthenticatedUser and the user types are read only in navigation.web")
    void subjectIsReadOnlyInWeb() {
        noClasses()
                .that()
                .resideInAnyPackage("cherry.mastersmith.navigation.service..", "cherry.mastersmith.navigation.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("cherry.mastersmith.auth..", "cherry.mastersmith.user..")
                .check(CLASSES);
        assertThat(dependsOnClass(
                        "cherry.mastersmith.navigation.web", "cherry.mastersmith.auth.domain.AuthenticatedUser"))
                .as("規則が依存を見分けている（web は実際に主体の型を読んでいる）")
                .isTrue();
        assertThat(CLASSES.get("cherry.mastersmith.navigation.service.NavigationService")
                        .getMethods())
                .filteredOn(method -> method.getModifiers().contains(JavaModifier.PUBLIC))
                .allSatisfy(method -> assertThat(method.getRawParameterTypes())
                        .noneMatch(type -> type.getPackageName().startsWith("cherry.mastersmith.auth")
                                || type.getPackageName().startsWith("cherry.mastersmith.user")));
    }

    @Test
    @DisplayName("navigation has no repository layer and uses no database part directly")
    void noRepositoryLayer() {
        assertThat(CLASSES.stream()
                        .filter(javaClass -> javaClass.getPackageName().startsWith("cherry.mastersmith.navigation"))
                        .map(JavaClass::getPackageName)
                        .distinct())
                .as("navigation は domain・service・web の3つの層だけを持つ")
                .containsExactlyInAnyOrder(
                        "cherry.mastersmith.navigation.domain",
                        "cherry.mastersmith.navigation.service",
                        "cherry.mastersmith.navigation.web");
        noClasses()
                .that()
                .resideInAPackage(NAVIGATION)
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("jakarta.persistence.EntityManager")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate")
                .orShould()
                .beAnnotatedWith("org.springframework.stereotype.Repository")
                .orShould()
                .beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
                .check(CLASSES);
        classes()
                .that()
                .resideInAPackage("cherry.mastersmith.navigation.web..")
                .and()
                .haveSimpleNameEndingWith("Controller")
                .should()
                .beAnnotatedWith("cherry.mastersmith.common.security.ApiAccess")
                .check(CLASSES);
    }
}
