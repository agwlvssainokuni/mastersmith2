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
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code role}（ロールと権限）の境界の構造の検査（Intent 261004-role-menu の U4、NFR6.5、計画の 5節・D-35、{@code team.md} の Code Style
 * 「新しく作る機能は境界テストを置く」）。B3 の最小の1規則を、承認済みの NFR6.5 の形に広げた（B4）。
 *
 * <ul>
 *   <li>{@code role} がアプリの中で依存してよいのは {@code group.service}・{@code group.domain}・{@code user.service}・
 *       {@code user.domain}・{@code dsl.service}・{@code dsl.domain}・{@code common} だけ（{@code dsl.parse}・{@code dsl.validate}・
 *       {@code group.store}・{@code group.repository}・{@code user.repository} には依存しない）
 *   <li>{@code role} に依存してよいのは {@code audit}（{@code role.domain} だけ）と {@code navigation}（{@code role.service} だけ）
 *   <li>{@code role.store} を使うのは {@code role.service} だけで、{@code role.store} を持つ（注入される）のは
 *       {@code RoleStoreTransactions} だけ
 *   <li>{@code role.service} は {@code EntityManager}・{@code JdbcTemplate} を使わない
 *   <li>{@code role.repository} は書き込みの方法・{@code @Modifying} を持たず、{@code CrudRepository} を継がない
 *   <li>トランザクションの境界は {@code role.service} の {@code TransactionTemplate} だけ（{@code @Transactional} を付けない）
 * </ul>
 *
 * <p>規則が空振りしないよう、各規則に「規則が依存を見分けている」ことの確かめを添える（既存の境界テストと同じ形）。既存の全体の決まり
 * （ArchitectureTest）とほかの機能の境界テストは変えない（{@code UserBoundaryArchitectureTest} の1か所だけ、計画の 13節 Q1: A）。
 */
class RoleBoundaryArchitectureTest {

    private static final String ROLE = "cherry.mastersmith.role..";

    private static final String STORE = "cherry.mastersmith.role.store..";

    private static final String SERVICE = "cherry.mastersmith.role.service..";

    private static final String REPOSITORY = "cherry.mastersmith.role.repository..";

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
    @DisplayName("role depends only on the service and domain of group, user and dsl, and on common")
    void dependsOnlyOnAllowedPackages() {
        noClasses()
                .that()
                .resideInAPackage(ROLE)
                .should()
                .dependOnClassesThat(resideInAPackage("cherry.mastersmith..")
                        .and(resideOutsideOfPackages(
                                ROLE,
                                "cherry.mastersmith.group.service..",
                                "cherry.mastersmith.group.domain..",
                                "cherry.mastersmith.user.service..",
                                "cherry.mastersmith.user.domain..",
                                "cherry.mastersmith.dsl.service..",
                                "cherry.mastersmith.dsl.domain..",
                                "cherry.mastersmith.common..")))
                .check(CLASSES);
        noClasses()
                .that()
                .resideInAPackage(ROLE)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "cherry.mastersmith.dsl.parse..",
                        "cherry.mastersmith.dsl.validate..",
                        "cherry.mastersmith.group.store..",
                        "cherry.mastersmith.group.repository..",
                        "cherry.mastersmith.user.repository..")
                .check(CLASSES);
        assertThat(dependsOnPackage("cherry.mastersmith.role", "cherry.mastersmith.group.service"))
                .as("規則が依存を見分けている（role は実際に group.service の口を使っている）")
                .isTrue();
        assertThat(dependsOnPackage("cherry.mastersmith.role", "cherry.mastersmith.user.domain"))
                .isTrue();
        assertThat(dependsOnPackage("cherry.mastersmith.role", "cherry.mastersmith.dsl.service"))
                .isTrue();
    }

    @Test
    @DisplayName("outside role only audit (role.domain) and navigation (role.service) may depend on it")
    void onlyAuditAndNavigationDependOnRole() {
        noClasses()
                .that()
                .resideOutsideOfPackages(ROLE, "cherry.mastersmith.audit..", "cherry.mastersmith.navigation..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(ROLE)
                .check(CLASSES);
        noClasses()
                .that()
                .resideInAPackage("cherry.mastersmith.audit..")
                .should()
                .dependOnClassesThat(
                        resideInAPackage(ROLE).and(resideOutsideOfPackages("cherry.mastersmith.role.domain..")))
                .check(CLASSES);
        noClasses()
                .that()
                .resideInAPackage("cherry.mastersmith.navigation..")
                .should()
                .dependOnClassesThat(resideInAPackage(ROLE).and(resideOutsideOfPackages(SERVICE)))
                .allowEmptyShould(true)
                .check(CLASSES);
        assertThat(dependsOnPackage("cherry.mastersmith.audit", "cherry.mastersmith.role.domain"))
                .as("規則が依存を見分けている（audit は実際に role の出来事を使っている）")
                .isTrue();
    }

    @Test
    @DisplayName("only role.service uses role.store, and only RoleStoreTransactions holds the store")
    void onlyServiceUsesStore() {
        noClasses()
                .that()
                .resideOutsideOfPackages(STORE, SERVICE)
                .should()
                .dependOnClassesThat()
                .resideInAPackage(STORE)
                .check(CLASSES);
        noFields()
                .that()
                .areDeclaredInClassesThat()
                .resideInAPackage(ROLE)
                .and()
                .areDeclaredInClassesThat()
                .doNotHaveFullyQualifiedName("cherry.mastersmith.role.service.RoleStoreTransactions")
                .should()
                .haveRawType("cherry.mastersmith.role.store.RoleStore")
                .check(CLASSES);
        assertThat(dependsOnPackage("cherry.mastersmith.role.service", "cherry.mastersmith.role.store"))
                .as("規則が依存を見分けている（service は実際に store を使っている）")
                .isTrue();
        JavaClass transactions = CLASSES.get("cherry.mastersmith.role.service.RoleStoreTransactions");
        assertThat(transactions.getFields())
                .as("規則が依存を見分けている（RoleStoreTransactions は実際に store を持つ）")
                .anyMatch(field -> field.getRawType().getName().equals("cherry.mastersmith.role.store.RoleStore"));
    }

    @Test
    @DisplayName("role.service uses neither the EntityManager nor the JdbcTemplate")
    void serviceDoesNotUseEntityManagerOrJdbc() {
        noClasses()
                .that()
                .resideInAPackage(SERVICE)
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("jakarta.persistence.EntityManager")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate")
                .check(CLASSES);
        assertThat(dependsOnClass("cherry.mastersmith.role.store", "jakarta.persistence.EntityManager"))
                .as("規則が依存を見分けている（store は実際に EntityManager を使っている）")
                .isTrue();
    }

    @Test
    @DisplayName("role.repository declares no write method or modifying query and does not extend CrudRepository")
    void repositoryIsReadOnly() {
        // 射影の record の項目（updatedAt・delete など）は書き込みの方法ではないため、Spring Data の口（interface）だけを見る。
        noMethods()
                .that()
                .areDeclaredInClassesThat()
                .resideInAPackage(REPOSITORY)
                .and()
                .areDeclaredInClassesThat()
                .areInterfaces()
                .should()
                .beAnnotatedWith("org.springframework.data.jpa.repository.Modifying")
                .orShould()
                .haveNameMatching("(save|delete|remove|update|persist|merge).*")
                .check(CLASSES);
        noClasses()
                .that()
                .resideInAPackage(REPOSITORY)
                .should()
                .beAssignableTo("org.springframework.data.repository.CrudRepository")
                .orShould()
                .beAssignableTo("org.springframework.data.repository.ListCrudRepository")
                .check(CLASSES);
        assertThat(CLASSES.get("cherry.mastersmith.user.repository.UserRepository")
                        .isAssignableTo("org.springframework.data.repository.CrudRepository"))
                .as("規則が依存を見分けている（user.repository は CrudRepository を継ぐ）")
                .isTrue();
        assertThat(CLASSES.stream()
                        .filter(javaClass -> javaClass.getPackageName().equals("cherry.mastersmith.role.repository"))
                        .count())
                .as("role.repository に読み取りの部品がある")
                .isPositive();
    }

    @Test
    @DisplayName("transaction boundaries of role are only the TransactionTemplate of role.service")
    void transactionsOnlyInService() {
        for (String annotation : new String[] {
            "org.springframework.transaction.annotation.Transactional", "jakarta.transaction.Transactional"
        }) {
            noClasses()
                    .that()
                    .resideInAPackage(ROLE)
                    .should()
                    .beAnnotatedWith(annotation)
                    .check(CLASSES);
            noMethods()
                    .that()
                    .areDeclaredInClassesThat()
                    .resideInAPackage(ROLE)
                    .should()
                    .beAnnotatedWith(annotation)
                    .check(CLASSES);
        }
        noClasses()
                .that()
                .resideInAPackage(ROLE)
                .and()
                .resideOutsideOfPackage(SERVICE)
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.transaction.support.TransactionTemplate")
                .check(CLASSES);
        assertThat(dependsOnClass(
                        "cherry.mastersmith.role.service",
                        "org.springframework.transaction.support.TransactionTemplate"))
                .as("規則が依存を見分けている（service は実際に TransactionTemplate を使っている）")
                .isTrue();
    }
}
