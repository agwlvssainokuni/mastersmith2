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

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * U3 の境界の構造の検査（ADR-001、NFR4.7・NFR4.9、logical-components.md、unit-of-work.md の U3）。
 * {@code TargetDbBoundaryArchitectureTest}・{@code DslBoundaryArchitectureTest} と同じ置き方で、U3 のパッケージの上に置く。
 */
class DslManageGenerateBoundaryArchitectureTest {

    private static final String GENERATE = "cherry.mastersmith.dslmanage.generate..";

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("cherry.mastersmith");

    @Test
    @DisplayName("generate uses only the service and domain of targetdb and dsl among the application packages")
    void onlyServiceAndDomainOfU1AndU2() {
        DescribedPredicate<JavaClass> otherApplicationClasses = resideInAPackage("cherry.mastersmith..")
                .and(not(resideInAnyPackage(
                        GENERATE,
                        "cherry.mastersmith.targetdb.service",
                        "cherry.mastersmith.targetdb.domain",
                        "cherry.mastersmith.dsl.service",
                        "cherry.mastersmith.dsl.domain")));
        noClasses()
                .that()
                .resideInAPackage(GENERATE)
                .should()
                .dependOnClassesThat(otherApplicationClasses)
                .check(CLASSES);
        noClasses()
                .that()
                .resideInAPackage(GENERATE)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "cherry.mastersmith.targetdb.config..",
                        "cherry.mastersmith.targetdb.repository..",
                        "cherry.mastersmith.dsl.parse..",
                        "cherry.mastersmith.dsl.validate..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("generate has no web or repository layer and touches neither JPA nor JDBC")
    void noWebRepositoryOrDatabase() {
        classes()
                .that()
                .resideInAPackage(GENERATE)
                .should()
                .resideInAPackage("cherry.mastersmith.dslmanage.generate")
                .as("generate は API と保存を持たない（web 層・repository 層を持たない。保存は U4）")
                .check(CLASSES);
        noClasses()
                .that()
                .resideInAPackage(GENERATE)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "cherry.mastersmith..web..",
                        "cherry.mastersmith..repository..",
                        "java.sql..",
                        "javax.sql..",
                        "jakarta.persistence..",
                        "org.springframework.jdbc..",
                        "org.springframework.data..",
                        "org.springframework.transaction..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("generate uses neither the SnakeYAML constructors nor the YAML parts of Jackson")
    void noYamlTypeConstruction() {
        noClasses()
                .that()
                .resideInAPackage(GENERATE)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.yaml.snakeyaml.constructor..",
                        "tools.jackson.dataformat.yaml..",
                        "com.fasterxml.jackson.dataformat.yaml..",
                        "org.snakeyaml.engine..")
                .check(CLASSES);
        noClasses()
                .that()
                .resideInAPackage(GENERATE)
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("org.yaml.snakeyaml.Yaml")
                .because("書き出しは Representer・Serializer・Emitter で行い、読み込みの口を持つ Yaml を使わない")
                .check(CLASSES);
    }
}
