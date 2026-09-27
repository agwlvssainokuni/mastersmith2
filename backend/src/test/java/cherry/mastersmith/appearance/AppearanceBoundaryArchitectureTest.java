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
package cherry.mastersmith.appearance;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * U8（インスタンスの見た目の設定）の境界の構造の検査（NFR5.1、reliability-design.md の 3.1節）。
 *
 * <p>{@code appearance} は内部DB に触れず、{@code common} の外のほかの機能と依存し合わない。既存の {@code ArchitectureTest} と
 * ほかの機能の境界テストは変えない。
 */
class AppearanceBoundaryArchitectureTest {

    private static final String APPEARANCE = "cherry.mastersmith.appearance..";

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("cherry.mastersmith");

    @Test
    @DisplayName("appearance does not depend on any database access")
    void noDatabaseAccess() {
        noClasses()
                .that()
                .resideInAPackage(APPEARANCE)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..repository..",
                        "javax.sql..",
                        "jakarta.persistence..",
                        "org.springframework.jdbc..",
                        "org.springframework.data..",
                        "org.springframework.transaction..",
                        "com.zaxxer.hikari..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("appearance depends on no other feature than common, config included")
    void noOtherFeature() {
        noClasses()
                .that()
                .resideInAPackage(APPEARANCE)
                .should()
                .dependOnClassesThat(resideInAPackage("cherry.mastersmith..")
                        .and(resideOutsideOfPackages(APPEARANCE, "cherry.mastersmith.common.."))
                        .as("classes of cherry.mastersmith outside appearance and common"))
                .check(CLASSES);
    }

    @Test
    @DisplayName("no class outside appearance depends on appearance")
    void nobodyDependsOnAppearance() {
        noClasses()
                .that()
                .resideInAPackage("cherry.mastersmith..")
                .and()
                .resideOutsideOfPackage(APPEARANCE)
                .should()
                .dependOnClassesThat()
                .resideInAPackage(APPEARANCE)
                .check(CLASSES);
    }

    @Test
    @DisplayName("appearance.web does not use appearance.config directly")
    void webDoesNotUseConfig() {
        noClasses()
                .that()
                .resideInAPackage("cherry.mastersmith.appearance.web..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("cherry.mastersmith.appearance.config..")
                .check(CLASSES);
    }
}
