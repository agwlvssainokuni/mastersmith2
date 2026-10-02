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
package cherry.mastersmith.useradmin;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * U3（利用者の管理、Intent 260930-user-admin）の境界の構造の検査（BR7.6、NFR11.1、team.md の Code Style）。既存の機能ごとの境界テストと
 * 同じ置き方で、U3 のパッケージの下に置く。既存の全体の決まり（ArchitectureTest）とほかの機能の境界テストは変えない。
 */
class UserAdminBoundaryArchitectureTest {

    private static final String USERADMIN = "cherry.mastersmith.useradmin..";

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("cherry.mastersmith");

    /** JPA のエンティティ。 */
    private static final DescribedPredicate<JavaClass> ENTITY = DescribedPredicate.describe(
            "JPA entities", javaClass -> javaClass.isAnnotatedWith("jakarta.persistence.Entity"));

    private static boolean dependsOnPackage(String fromPrefix, String targetPackage) {
        return CLASSES.stream()
                .filter(javaClass -> javaClass.getPackageName().startsWith(fromPrefix))
                .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                .anyMatch(dependency ->
                        dependency.getTargetClass().getPackageName().equals(targetPackage));
    }

    @Test
    @DisplayName("useradmin uses user and auth only through their service and domain, and no unrelated feature")
    void allowedFeatures() {
        noClasses()
                .that()
                .resideInAPackage(USERADMIN)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "cherry.mastersmith.user.repository..",
                        "cherry.mastersmith.user.web..",
                        "cherry.mastersmith.auth.repository..",
                        "cherry.mastersmith.auth.web..",
                        "cherry.mastersmith.invitation..",
                        "cherry.mastersmith.dsl..",
                        "cherry.mastersmith.dslmanage..",
                        "cherry.mastersmith.targetdb..",
                        "cherry.mastersmith.mail..",
                        "cherry.mastersmith.appearance..",
                        "cherry.mastersmith.audit..")
                .check(CLASSES);
        assertThat(dependsOnPackage("cherry.mastersmith.useradmin", "cherry.mastersmith.user.service"))
                .as("規則が依存を見分けている（user.service は実際に使っている）")
                .isTrue();
        assertThat(dependsOnPackage("cherry.mastersmith.useradmin", "cherry.mastersmith.auth.service"))
                .as("規則が依存を見分けている（auth.service は実際に使っている）")
                .isTrue();
    }

    @Test
    @DisplayName("useradmin depends on no JPA entity (it holds only projected values)")
    void noEntities() {
        noClasses()
                .that()
                .resideInAPackage(USERADMIN)
                .should()
                .dependOnClassesThat(ENTITY)
                .check(CLASSES);
    }

    @Test
    @DisplayName("only useradmin.web may use access, and then only access.domain.AccessProblemTypes")
    void accessOnlyForTheProblemType() {
        noClasses()
                .that()
                .resideInAPackage(USERADMIN)
                .and()
                .resideOutsideOfPackage("cherry.mastersmith.useradmin.web..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("cherry.mastersmith.access..")
                .check(CLASSES);
        noClasses()
                .that()
                .resideInAPackage("cherry.mastersmith.useradmin.web..")
                .should()
                .dependOnClassesThat(DescribedPredicate.describe(
                        "classes of access other than access.domain.AccessProblemTypes",
                        target -> target.getPackageName().startsWith("cherry.mastersmith.access")
                                && !target.getName().equals("cherry.mastersmith.access.domain.AccessProblemTypes")))
                .check(CLASSES);
    }

    @Test
    @DisplayName("user still does not know auth after the ports added for useradmin")
    void userDoesNotDependOnAuth() {
        noClasses()
                .that()
                .resideInAPackage("cherry.mastersmith.user..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("cherry.mastersmith.auth..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("outside useradmin only audit may depend on it")
    void onlyAuditDependsOnUserAdmin() {
        noClasses()
                .that()
                .resideOutsideOfPackages(USERADMIN, "cherry.mastersmith.audit..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(USERADMIN)
                .check(CLASSES);
    }

    @Test
    @DisplayName("transaction boundaries of useradmin are only TransactionTemplate in useradmin.service")
    void transactionsOnlyInService() {
        for (String annotation : new String[] {
            "org.springframework.transaction.annotation.Transactional", "jakarta.transaction.Transactional"
        }) {
            noClasses()
                    .that()
                    .resideInAPackage(USERADMIN)
                    .should()
                    .beAnnotatedWith(annotation)
                    .check(CLASSES);
            methods()
                    .that()
                    .areDeclaredInClassesThat()
                    .resideInAPackage(USERADMIN)
                    .should()
                    .notBeAnnotatedWith(annotation)
                    .check(CLASSES);
        }
        noClasses()
                .that()
                .resideInAPackage(USERADMIN)
                .and()
                .resideOutsideOfPackage("cherry.mastersmith.useradmin.service..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.transaction.support.TransactionTemplate")
                .check(CLASSES);
    }
}
