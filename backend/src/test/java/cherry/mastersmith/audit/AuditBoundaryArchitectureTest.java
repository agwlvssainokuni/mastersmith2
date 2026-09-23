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
package cherry.mastersmith.audit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * U4 の境界の構造の検査（BR4.1、NFR3.2、NFR3.5）。U2 の {@code AuthBoundaryArchitectureTest} と同じ置き方で、U4 の
 * パッケージの下に置く（U1 の {@code ArchitectureTest} は変えない）。
 */
class AuditBoundaryArchitectureTest {

    private static final String AUDIT = "cherry.mastersmith.audit..";

    private static final String AUDIT_REPOSITORY = "cherry.mastersmith.audit.repository..";

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("cherry.mastersmith");

    @Test
    @DisplayName("the audit repository declares no update or delete operation")
    void repositoryHasNoUpdateOrDelete() {
        noMethods()
                .that()
                .areDeclaredInClassesThat()
                .resideInAPackage(AUDIT_REPOSITORY)
                .should()
                .haveNameMatching("(delete|remove|update|save.*And.*|flush).*")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    @DisplayName("the audit repository declares no modifying query")
    void repositoryHasNoModifyingQuery() {
        noMethods()
                .that()
                .areDeclaredInClassesThat()
                .resideInAPackage(AUDIT_REPOSITORY)
                .should()
                .beAnnotatedWith("org.springframework.data.jpa.repository.Modifying")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    @DisplayName("the audit repository does not inherit the delete operations of the standard repositories")
    void repositoryDoesNotExtendCrudRepository() {
        noClasses()
                .that()
                .resideInAPackage(AUDIT_REPOSITORY)
                .should()
                .beAssignableTo("org.springframework.data.repository.CrudRepository")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    @DisplayName("only audit uses the audit repository")
    void onlyAuditUsesTheAuditRepository() {
        noClasses()
                .that()
                .resideOutsideOfPackage(AUDIT)
                .should()
                .dependOnClassesThat()
                .resideInAPackage(AUDIT_REPOSITORY)
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    @DisplayName("transaction boundaries of audit live in audit.service only")
    void transactionsOnlyInAuditService() {
        for (String annotation : new String[] {
            "org.springframework.transaction.annotation.Transactional", "jakarta.transaction.Transactional"
        }) {
            noMethods()
                    .that()
                    .areDeclaredInClassesThat()
                    .resideInAPackage(AUDIT)
                    .and()
                    .areDeclaredInClassesThat()
                    .resideOutsideOfPackage("cherry.mastersmith.audit.service..")
                    .should()
                    .beAnnotatedWith(annotation)
                    .allowEmptyShould(true)
                    .check(CLASSES);
            noClasses()
                    .that()
                    .resideInAPackage(AUDIT)
                    .and()
                    .resideOutsideOfPackage("cherry.mastersmith.audit.service..")
                    .should()
                    .beAnnotatedWith(annotation)
                    .allowEmptyShould(true)
                    .check(CLASSES);
        }
    }

    @Test
    @DisplayName("audit has no web layer because it exposes no API")
    void auditHasNoWebLayer() {
        classes()
                .that()
                .resideInAPackage(AUDIT)
                .should()
                .resideInAnyPackage(
                        "cherry.mastersmith.audit",
                        "cherry.mastersmith.audit.domain",
                        "cherry.mastersmith.audit.repository",
                        "cherry.mastersmith.audit.service")
                .check(CLASSES);
        assertThat(CLASSES.stream()
                        .filter(javaClass -> javaClass.getPackageName().startsWith("cherry.mastersmith.audit"))
                        .anyMatch(javaClass ->
                                javaClass.isAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                                        || javaClass.isAnnotatedWith("org.springframework.stereotype.Controller")))
                .as("U4 は API を持たない（NFR3.5）")
                .isFalse();
    }
}
