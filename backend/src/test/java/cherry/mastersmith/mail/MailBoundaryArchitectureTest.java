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
package cherry.mastersmith.mail;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * U1（メールの描画と送信）の境界の構造の検査（logical-components.md の 3節、NFR5.1、team.md の Code Style）。既存の
 * {@code DslBoundaryArchitectureTest} と同じ置き方で、U1 のパッケージの下に置く。既存の全体の決まり（ArchitectureTest）と機能ごとの
 * 境界テストは変えない。
 */
class MailBoundaryArchitectureTest {

    private static final String MAIL = "cherry.mastersmith.mail..";

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("cherry.mastersmith");

    @Test
    @DisplayName("mail depends on no other feature and on no web or repository layer (common is allowed)")
    void mailStaysIndependent() {
        noClasses()
                .that()
                .resideInAPackage(MAIL)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "cherry.mastersmith.user..",
                        "cherry.mastersmith.auth..",
                        "cherry.mastersmith.audit..",
                        "cherry.mastersmith.access..",
                        "cherry.mastersmith.dsl..",
                        "cherry.mastersmith.dslmanage..",
                        "cherry.mastersmith.targetdb..",
                        "cherry.mastersmith.invitation..",
                        "cherry.mastersmith.appearance..",
                        "cherry.mastersmith.config..",
                        "cherry.mastersmith..web..",
                        "cherry.mastersmith..repository..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("mail has only the service, domain, config, template and transport sub-packages")
    void onlyKnownSubPackages() {
        classes()
                .that()
                .resideInAPackage(MAIL)
                .should()
                .resideInAnyPackage(
                        "cherry.mastersmith.mail",
                        "cherry.mastersmith.mail.service",
                        "cherry.mastersmith.mail.domain",
                        "cherry.mastersmith.mail.config",
                        "cherry.mastersmith.mail.template",
                        "cherry.mastersmith.mail.transport")
                .as("mail は API と内部DB を持たないため web 層・repository 層を持たない")
                .check(CLASSES);
    }

    @Test
    @DisplayName("from outside mail only the service and domain packages are used")
    void onlyServiceAndDomainAreUsedFromOutside() {
        noClasses()
                .that()
                .resideOutsideOfPackage(MAIL)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "cherry.mastersmith.mail.config..",
                        "cherry.mastersmith.mail.template..",
                        "cherry.mastersmith.mail.transport..")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    @DisplayName("mail never uses transactions")
    void noTransactions() {
        for (String annotation : new String[] {
            "org.springframework.transaction.annotation.Transactional", "jakarta.transaction.Transactional"
        }) {
            noClasses()
                    .that()
                    .resideInAPackage(MAIL)
                    .should()
                    .beAnnotatedWith(annotation)
                    .check(CLASSES);
            methods()
                    .that()
                    .areDeclaredInClassesThat()
                    .resideInAPackage(MAIL)
                    .should()
                    .notBeAnnotatedWith(annotation)
                    .check(CLASSES);
        }
        noClasses()
                .that()
                .resideInAPackage(MAIL)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework.transaction..", "jakarta.transaction..", "jakarta.persistence..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("the Mustache engine is used only in template, and the mail components only in transport and config")
    void externalComponentsStayInPlace() {
        // 規則が依存を見分けていることの確かめ（それぞれの部品は実際に使っている）。
        assertThat(CLASSES.stream()
                        .filter(javaClass -> javaClass.getPackageName().equals("cherry.mastersmith.mail.template"))
                        .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                        .anyMatch(dependency ->
                                dependency.getTargetClass().getPackageName().equals("cherry.mustache")))
                .isTrue();
        noClasses()
                .that()
                .resideInAPackage(MAIL)
                .and()
                .resideOutsideOfPackage("cherry.mastersmith.mail.template..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("cherry.mustache..")
                .check(CLASSES);
        noClasses()
                .that()
                .resideInAPackage(MAIL)
                .and()
                .resideOutsideOfPackages("cherry.mastersmith.mail.transport..", "cherry.mastersmith.mail.config..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("jakarta.mail..", "org.springframework.mail..")
                .check(CLASSES);
    }
}
