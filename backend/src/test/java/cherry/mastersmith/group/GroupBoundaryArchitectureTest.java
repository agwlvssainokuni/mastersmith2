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
package cherry.mastersmith.group;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code group}（グループの管理）の境界の構造の検査（Intent 261004-role-menu の U3、{@code logical-components.md} 2節の6項目、NFR6.6、
 * {@code team.md} の Code Style「新しく作る機能は境界テストを置く」）。
 *
 * <ul>
 *   <li>{@code group} は {@code role}・{@code audit}・{@code useradmin} に依存しない（アプリの中で依存してよいのは {@code user} と
 *       {@code common}）
 *   <li>{@code group} に依存してよいのは {@code role}（口だけ）と {@code audit}（出来事の型だけ）
 *   <li>{@code group.store} を使うのは {@code group.service} だけ
 *   <li>{@code group.service} は {@code EntityManager} を使わない
 *   <li>{@code group.repository} は書き込みの方法・{@code @Modifying} を持たず、{@code CrudRepository} を継がない
 *   <li>トランザクションの境界は {@code group.service} の {@code TransactionTemplate} だけ（{@code @Transactional} を付けない）
 * </ul>
 *
 * <p>規則が空振りしないよう、各規則に「規則が依存を見分けている」ことの確かめを添える（既存の境界テストと同じ形）。既存の全体の決まり
 * （ArchitectureTest）とほかの機能の境界テストは変えない。
 */
class GroupBoundaryArchitectureTest {

    private static final String GROUP = "cherry.mastersmith.group..";

    private static final String STORE = "cherry.mastersmith.group.store..";

    private static final String SERVICE = "cherry.mastersmith.group.service..";

    private static final String REPOSITORY = "cherry.mastersmith.group.repository..";

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
    @DisplayName("group depends on neither role, audit nor useradmin")
    void groupDoesNotDependOnItsUsers() {
        noClasses()
                .that()
                .resideInAPackage(GROUP)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "cherry.mastersmith.role..", "cherry.mastersmith.audit..", "cherry.mastersmith.useradmin..")
                .check(CLASSES);
        assertThat(dependsOnPackage("cherry.mastersmith.group", "cherry.mastersmith.user."))
                .as("規則が依存を見分けている（user は実際に使っている）")
                .isTrue();
    }

    @Test
    @DisplayName("outside group only role and audit may depend on it")
    void onlyRoleAndAuditDependOnGroup() {
        noClasses()
                .that()
                .resideOutsideOfPackages(GROUP, "cherry.mastersmith.role..", "cherry.mastersmith.audit..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(GROUP)
                .check(CLASSES);
        assertThat(dependsOnPackage("cherry.mastersmith.audit", "cherry.mastersmith.group"))
                .as("規則が依存を見分けている（audit は実際に group の出来事を使っている）")
                .isTrue();
        assertThat(dependsOnPackage("cherry.mastersmith.role", "cherry.mastersmith.group.service"))
                .isTrue();
    }

    @Test
    @DisplayName("only group.service uses group.store")
    void onlyServiceUsesStore() {
        noClasses()
                .that()
                .resideOutsideOfPackages(STORE, SERVICE)
                .should()
                .dependOnClassesThat()
                .resideInAPackage(STORE)
                .check(CLASSES);
        assertThat(dependsOnPackage("cherry.mastersmith.group.service", "cherry.mastersmith.group.store"))
                .as("規則が依存を見分けている（service は実際に store を使っている）")
                .isTrue();
    }

    @Test
    @DisplayName("group.service does not use the EntityManager")
    void serviceDoesNotUseEntityManager() {
        noClasses()
                .that()
                .resideInAPackage(SERVICE)
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("jakarta.persistence.EntityManager")
                .check(CLASSES);
        assertThat(dependsOnClass("cherry.mastersmith.group.store", "jakarta.persistence.EntityManager"))
                .as("規則が依存を見分けている（store は実際に EntityManager を使っている）")
                .isTrue();
    }

    @Test
    @DisplayName("group.repository declares no write method or modifying query and does not extend CrudRepository")
    void repositoryIsReadOnly() {
        noMethods()
                .that()
                .areDeclaredInClassesThat()
                .resideInAPackage(REPOSITORY)
                .should()
                .beAnnotatedWith("org.springframework.data.jpa.repository.Modifying")
                .orShould()
                .haveNameMatching("(save|delete|remove|update|persist|merge).*")
                .allowEmptyShould(true)
                .check(CLASSES);
        noClasses()
                .that()
                .resideInAPackage(REPOSITORY)
                .should()
                .beAssignableTo("org.springframework.data.repository.CrudRepository")
                .orShould()
                .beAssignableTo("org.springframework.data.repository.ListCrudRepository")
                .check(CLASSES);
        assertThat(CLASSES.stream()
                        .filter(javaClass ->
                                javaClass.getPackageName().startsWith("cherry.mastersmith.user.repository"))
                        .flatMap(javaClass -> javaClass.getMethods().stream())
                        .anyMatch(
                                method -> method.isAnnotatedWith("org.springframework.data.jpa.repository.Modifying")))
                .as("規則が依存を見分けている（user.repository には @Modifying がある）")
                .isTrue();
        assertThat(CLASSES.get("cherry.mastersmith.user.repository.UserRepository")
                        .isAssignableTo("org.springframework.data.repository.CrudRepository"))
                .isTrue();
    }

    @Test
    @DisplayName("transaction boundaries of group are only the TransactionTemplate of group.service")
    void transactionsOnlyInService() {
        for (String annotation : new String[] {
            "org.springframework.transaction.annotation.Transactional", "jakarta.transaction.Transactional"
        }) {
            noClasses()
                    .that()
                    .resideInAPackage(GROUP)
                    .should()
                    .beAnnotatedWith(annotation)
                    .check(CLASSES);
            noMethods()
                    .that()
                    .areDeclaredInClassesThat()
                    .resideInAPackage(GROUP)
                    .should()
                    .beAnnotatedWith(annotation)
                    .check(CLASSES);
        }
        noClasses()
                .that()
                .resideInAPackage(GROUP)
                .and()
                .resideOutsideOfPackage(SERVICE)
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.transaction.support.TransactionTemplate")
                .check(CLASSES);
        assertThat(dependsOnClass(
                        "cherry.mastersmith.group.service",
                        "org.springframework.transaction.support.TransactionTemplate"))
                .as("規則が依存を見分けている（service は実際に TransactionTemplate を使っている）")
                .isTrue();
    }
}
