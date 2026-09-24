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
package cherry.mastersmith.dslmanage;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * U4 の境界の構造の検査（team.md の Code Style「層の境界」、NFR 設計の決定 A、ADR-001）。U3 の
 * {@code DslManageGenerateBoundaryArchitectureTest} と同じ置き方で、{@code dslmanage} の上に置く。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DslManageBoundaryArchitectureTest {

    // 静的な項目にしない（クラスの読み込みの結果をテストの JVM に持ち続けず、このクラスのテストが終われば手放す）。
    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("cherry.mastersmith");

    private static final String[] TRANSACTIONAL = {
        "org.springframework.transaction.annotation.Transactional", "jakarta.transaction.Transactional"
    };

    @Test
    @DisplayName("the DSL web layer never uses the DSL repositories")
    void webDoesNotUseRepository() {
        noClasses()
                .that()
                .resideInAPackage("cherry.mastersmith.dslmanage.web..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("cherry.mastersmith.dslmanage.repository..")
                .check(classes);
    }

    @Test
    @DisplayName("inside dslmanage, transaction boundaries live in dslmanage.service only")
    void transactionsOnlyInService() {
        for (String annotation : TRANSACTIONAL) {
            noMethods()
                    .that()
                    .areDeclaredInClassesThat()
                    .resideInAPackage("cherry.mastersmith.dslmanage..")
                    .and()
                    .areDeclaredInClassesThat()
                    .resideOutsideOfPackage("cherry.mastersmith.dslmanage.service..")
                    .should()
                    .beAnnotatedWith(annotation)
                    .check(classes);
            noClasses()
                    .that()
                    .resideInAPackage("cherry.mastersmith.dslmanage..")
                    .and()
                    .resideOutsideOfPackage("cherry.mastersmith.dslmanage.service..")
                    .should()
                    .beAnnotatedWith(annotation)
                    .check(classes);
        }
    }

    @Test
    @DisplayName("dslmanage uses neither the settings and queries of targetdb nor the parser and validators of dsl")
    void onlyPublicPartsOfU1AndU2() {
        noClasses()
                .that()
                .resideInAPackage("cherry.mastersmith.dslmanage..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "cherry.mastersmith.targetdb.config..",
                        "cherry.mastersmith.targetdb.repository..",
                        "cherry.mastersmith.dsl.parse..",
                        "cherry.mastersmith.dsl.validate..")
                .check(classes);
    }

    @Test
    @DisplayName("the common request size limit knows nothing about the DSL management")
    void commonWebDoesNotKnowDslManage() {
        noClasses()
                .that()
                .resideInAnyPackage("cherry.mastersmith.common..", "cherry.mastersmith.config..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("cherry.mastersmith.dslmanage..", "cherry.mastersmith.dsl..")
                .check(classes);
    }
}
