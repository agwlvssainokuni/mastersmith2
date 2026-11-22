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
package cherry.mastersmith.internaldb;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresIT;
import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresRuntimeCheck;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * 内部DB の起動の入口の部品（{@code internaldb.platform}）の境界の構造の検査（NFR9.2・T10）。依存してよい先を JDK・Spring・Flyway・
 * Hikari・SLF4J・{@code internaldb} の中だけに固定し、業務の機能と {@code targetdb} へ依存せず、依存もされない（Spring が自動で
 * 組み立てる分を除く）。既存の {@code ArchitectureTest} と他の機能の境界テストは変えない。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InternalDbBoundaryArchitectureTest {

    private static final String PLATFORM = "cherry.mastersmith.internaldb.platform..";

    // 静的な項目にしない（クラスの読み込みの結果をテストの JVM に持ち続けず、このクラスのテストが終われば手放す）。
    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("cherry.mastersmith");

    @Test
    @DisplayName("internaldb.platform depends only on the JDK, Spring, Flyway, Hikari, SLF4J and itself")
    void platformDependsOnlyOnAllowedLibraries() {
        classes()
                .that()
                .resideInAPackage(PLATFORM)
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "java..",
                        "javax..",
                        "org.springframework..",
                        "org.flywaydb..",
                        "com.zaxxer.hikari..",
                        "org.slf4j..",
                        "cherry.mastersmith.internaldb..")
                .check(classes);
    }

    @Test
    @DisplayName("internaldb.platform depends on no business feature and not on targetdb")
    void platformDoesNotDependOnFeatures() {
        noClasses()
                .that()
                .resideInAPackage(PLATFORM)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "cherry.mastersmith.auth..",
                        "cherry.mastersmith.user..",
                        "cherry.mastersmith.useradmin..",
                        "cherry.mastersmith.audit..",
                        "cherry.mastersmith.access..",
                        "cherry.mastersmith.appearance..",
                        "cherry.mastersmith.businessdata..",
                        "cherry.mastersmith.dsl..",
                        "cherry.mastersmith.dslmanage..",
                        "cherry.mastersmith.group..",
                        "cherry.mastersmith.invitation..",
                        "cherry.mastersmith.mail..",
                        "cherry.mastersmith.navigation..",
                        "cherry.mastersmith.role..",
                        "cherry.mastersmith.targetdb..",
                        "cherry.mastersmith.common..",
                        "cherry.mastersmith.config..")
                .check(classes);
    }

    @Test
    @DisplayName("the PostgreSQL IT annotation registers OutputCaptureExtension before the runtime check")
    void postgresItAnnotationRegistersOutputCaptureFirst() {
        JavaClasses testClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages("cherry.mastersmith.internaldb.testsupport");
        JavaClass annotation = testClasses.get(InternalDbPostgresIT.class);

        List<String> order = new ArrayList<>();
        annotation
                .tryGetAnnotationOfType("org.junit.jupiter.api.extension.ExtendWith")
                .ifPresent(extend -> {
                    Object value = extend.get("value").orElseThrow();
                    for (Object each : (Object[]) value) {
                        order.add(((JavaClass) each).getName());
                    }
                });

        assertThat(order)
                .containsExactly(
                        OutputCaptureExtension.class.getName(), InternalDbPostgresRuntimeCheck.class.getName());
    }

    @Test
    @DisplayName("no test class registers the PostgreSQL runtime check directly (only through the annotation)")
    void runtimeCheckIsRegisteredOnlyThroughTheAnnotation() {
        JavaClasses testClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages("cherry.mastersmith");

        List<String> direct = new ArrayList<>();
        for (JavaClass javaClass : testClasses) {
            if (javaClass.getName().equals(InternalDbPostgresIT.class.getName())) {
                continue;
            }
            javaClass
                    .tryGetAnnotationOfType("org.junit.jupiter.api.extension.ExtendWith")
                    .ifPresent(extend -> {
                        for (Object each : (Object[]) extend.get("value").orElseThrow()) {
                            if (((JavaClass) each).getName().equals(InternalDbPostgresRuntimeCheck.class.getName())) {
                                direct.add(javaClass.getName());
                            }
                        }
                    });
        }

        assertThat(direct).isEmpty();
    }

    @Test
    @DisplayName("no class outside internaldb depends on internaldb (Spring assembles it automatically)")
    void nothingDependsOnInternalDb() {
        noClasses()
                .that()
                .resideOutsideOfPackage("cherry.mastersmith.internaldb..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("cherry.mastersmith.internaldb..")
                .allowEmptyShould(true)
                .check(classes);
    }
}
