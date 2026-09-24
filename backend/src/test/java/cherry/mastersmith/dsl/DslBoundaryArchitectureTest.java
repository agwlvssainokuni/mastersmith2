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
package cherry.mastersmith.dsl;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * U2 の境界の構造の検査（ADR-001・ADR-009、NFR3.5、unit-of-work.md の U2）。{@code TargetDbBoundaryArchitectureTest} と同じ
 * 置き方で、U2 のパッケージの下に置く。
 */
class DslBoundaryArchitectureTest {

    private static final String DSL = "cherry.mastersmith.dsl..";

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("cherry.mastersmith");

    private static final DescribedPredicate<JavaCall<?>> YAML_LOAD = DescribedPredicate.describe(
            "a Yaml.load method of SnakeYAML",
            call -> call.getTargetOwner().getFullName().equals("org.yaml.snakeyaml.Yaml")
                    && call.getName().startsWith("load"));

    @Test
    @DisplayName("dsl knows nothing about the target database, the DSL management, web layers or repositories")
    void dslStaysIndependent() {
        noClasses()
                .that()
                .resideInAPackage(DSL)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "cherry.mastersmith.targetdb..",
                        "cherry.mastersmith.dslmanage..",
                        "cherry.mastersmith..web..",
                        "cherry.mastersmith..repository..")
                .check(CLASSES);
        classes()
                .that()
                .resideInAPackage(DSL)
                .should()
                .resideInAnyPackage(
                        "cherry.mastersmith.dsl",
                        "cherry.mastersmith.dsl.domain",
                        "cherry.mastersmith.dsl.parse",
                        "cherry.mastersmith.dsl.validate",
                        "cherry.mastersmith.dsl.service")
                .as("dsl は API を持たないため web 層・repository 層を持たない")
                .check(CLASSES);
    }

    @Test
    @DisplayName("parse and validate are used only inside dsl")
    void parseAndValidateStayInside() {
        noClasses()
                .that()
                .resideOutsideOfPackage(DSL)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("cherry.mastersmith.dsl.parse..", "cherry.mastersmith.dsl.validate..")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    @DisplayName("dsl never uses the YAML readers of Jackson or snakeyaml-engine")
    void noOtherYamlReaders() {
        noClasses()
                .that()
                .resideInAPackage(DSL)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "tools.jackson.dataformat.yaml..",
                        "com.fasterxml.jackson.dataformat.yaml..",
                        "org.snakeyaml.engine..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("dsl never uses the object construction of SnakeYAML, only the node tree")
    void noObjectConstruction() {
        // 規則が SnakeYAML の使い方を見分けていることの確かめ（節の木を作る部品は使っている）。
        assertThat(CLASSES.stream()
                        .filter(javaClass -> javaClass.getPackageName().startsWith("cherry.mastersmith.dsl"))
                        .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                        .anyMatch(dependency -> dependency
                                .getTargetClass()
                                .getFullName()
                                .equals("org.yaml.snakeyaml.composer.Composer")))
                .isTrue();
        noClasses()
                .that()
                .resideInAPackage(DSL)
                .should()
                .dependOnClassesThat()
                .resideInAPackage("org.yaml.snakeyaml.constructor..")
                .check(CLASSES);
        noClasses()
                .that()
                .resideInAPackage(DSL)
                .should()
                .callMethodWhere(YAML_LOAD)
                .check(CLASSES);
    }
}
