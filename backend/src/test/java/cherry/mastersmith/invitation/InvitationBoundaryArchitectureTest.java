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
package cherry.mastersmith.invitation;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
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
 * U3（招待と登録の完了）の境界の構造の検査（{@code logical-components.md} 2節、team.md の Code Style）。既存の
 * {@code DslBoundaryArchitectureTest} と同じ置き方で、U3 のパッケージの下に置く。既存の全体の決まり（ArchitectureTest）と機能ごとの
 * 境界テストは変えない。
 */
class InvitationBoundaryArchitectureTest {

    private static final String INVITATION = "cherry.mastersmith.invitation..";

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("cherry.mastersmith");

    /** JPA のエンティティ。 */
    private static final DescribedPredicate<JavaClass> ENTITY = DescribedPredicate.describe(
            "JPA entities", javaClass -> javaClass.isAnnotatedWith("jakarta.persistence.Entity"));

    @Test
    @DisplayName("invitation depends neither on auth nor on audit")
    void noAuthOrAudit() {
        noClasses()
                .that()
                .resideInAPackage(INVITATION)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("cherry.mastersmith.auth..", "cherry.mastersmith.audit..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("invitation uses user only through user.service and the value types of user.domain")
    void userOnlyThroughServiceAndValues() {
        noClasses()
                .that()
                .resideInAPackage(INVITATION)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("cherry.mastersmith.user.repository..", "cherry.mastersmith.user.web..")
                .check(CLASSES);
        noClasses()
                .that()
                .resideInAPackage(INVITATION)
                .should()
                .dependOnClassesThat(ENTITY.and(JavaClass.Predicates.resideInAPackage("cherry.mastersmith.user..")))
                .check(CLASSES);
        assertThat(CLASSES.stream()
                        .filter(javaClass -> javaClass.getPackageName().startsWith("cherry.mastersmith.invitation"))
                        .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                        .anyMatch(dependency ->
                                dependency.getTargetClass().getPackageName().equals("cherry.mastersmith.user.service")))
                .as("規則が依存を見分けている（user.service は実際に使っている）")
                .isTrue();
    }

    @Test
    @DisplayName("invitation uses mail only through mail.service and mail.domain")
    void mailOnlyThroughServiceAndDomain() {
        noClasses()
                .that()
                .resideInAPackage(INVITATION)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "cherry.mastersmith.mail.config..",
                        "cherry.mastersmith.mail.template..",
                        "cherry.mastersmith.mail.transport..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("invitation does not depend on dsl, dslmanage, targetdb, access or appearance")
    void unrelatedFeatures() {
        noClasses()
                .that()
                .resideInAPackage(INVITATION)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "cherry.mastersmith.dsl..",
                        "cherry.mastersmith.dslmanage..",
                        "cherry.mastersmith.targetdb..",
                        "cherry.mastersmith.access..",
                        "cherry.mastersmith.appearance..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("outside invitation only audit depends on it, and only on the events of invitation.domain")
    void onlyAuditDependsOnEvents() {
        noClasses()
                .that()
                .resideOutsideOfPackages(INVITATION, "cherry.mastersmith.audit..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(INVITATION)
                .check(CLASSES);
        classes()
                .that()
                .resideInAPackage("cherry.mastersmith.audit..")
                .should()
                .onlyDependOnClassesThat(DescribedPredicate.describe(
                        "classes outside invitation, or the events of invitation.domain and their reason",
                        target -> !target.getPackageName().startsWith("cherry.mastersmith.invitation")
                                || (target.getPackageName().equals("cherry.mastersmith.invitation.domain")
                                        && (target.getSimpleName().endsWith("Event")
                                                || target.getSimpleName().equals("LinkRejection")))))
                .check(CLASSES);
    }

    @Test
    @DisplayName("transaction boundaries of invitation are only in invitation.service")
    void transactionsOnlyInService() {
        for (String annotation : new String[] {
            "org.springframework.transaction.annotation.Transactional", "jakarta.transaction.Transactional"
        }) {
            noClasses()
                    .that()
                    .resideInAPackage(INVITATION)
                    .should()
                    .beAnnotatedWith(annotation)
                    .check(CLASSES);
            methods()
                    .that()
                    .areDeclaredInClassesThat()
                    .resideInAPackage(INVITATION)
                    .should()
                    .notBeAnnotatedWith(annotation)
                    .check(CLASSES);
        }
        noClasses()
                .that()
                .resideInAPackage(INVITATION)
                .and()
                .resideOutsideOfPackage("cherry.mastersmith.invitation.service..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.transaction.support.TransactionTemplate")
                .check(CLASSES);
    }
}
