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
package cherry.mastersmith.common.persistence;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code common.persistence}（共通の「あれば更新・無ければ挿入」の手順と排他の失敗の判定）の境界の構造の検査（Intent
 * 261114-internal-db-postgresql の U2、NFR9.2、team.md の Code Style「新しく作る機能は境界テストを置く」）。
 *
 * <p>手順の部品は機能のパッケージ（{@code role}・{@code dslmanage}・{@code user} など）に依存せず、呼ぶ側の機能が {@code common}
 * に依存する向きだけを保つ。部品は {@code TraceAspect} の対象の層（{@code web}・{@code service}・{@code domain}・{@code repository}）
 * の外に置き、Spring の Bean にしない（持ち主がコンストラクターで作って持つ）。既存の {@code ArchitectureTest} とほかの機能の境界
 * テストは書き換えず、そのまま通る。
 */
class CommonPersistenceBoundaryArchitectureTest {

    private static final String PERSISTENCE = "cherry.mastersmith.common.persistence..";

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("cherry.mastersmith");

    @Test
    @DisplayName("common.persistence depends on no feature package of the application")
    void dependsOnNoFeaturePackage() {
        noClasses()
                .that()
                .resideInAPackage(PERSISTENCE)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "cherry.mastersmith.access..",
                        "cherry.mastersmith.appearance..",
                        "cherry.mastersmith.audit..",
                        "cherry.mastersmith.auth..",
                        "cherry.mastersmith.businessdata..",
                        "cherry.mastersmith.config..",
                        "cherry.mastersmith.dsl..",
                        "cherry.mastersmith.dslmanage..",
                        "cherry.mastersmith.group..",
                        "cherry.mastersmith.internaldb..",
                        "cherry.mastersmith.invitation..",
                        "cherry.mastersmith.mail..",
                        "cherry.mastersmith.navigation..",
                        "cherry.mastersmith.role..",
                        "cherry.mastersmith.targetdb..",
                        "cherry.mastersmith.user..",
                        "cherry.mastersmith.useradmin..")
                .check(CLASSES);
        assertThat(CLASSES.stream()
                        .filter(javaClass -> javaClass.getPackageName().equals("cherry.mastersmith.common.persistence"))
                        .map(JavaClass::getSimpleName))
                .as("規則が対象のクラスを見分けている")
                .contains("KeyedUpsertExecutor", "KeyedUpsert", "RowLockFailures");
    }

    @Test
    @DisplayName("common.persistence stays outside the traced layers web, service, domain and repository")
    void staysOutsideTheTracedLayers() {
        noClasses()
                .that()
                .resideInAPackage(PERSISTENCE)
                .should()
                .resideInAnyPackage("..web..", "..service..", "..domain..", "..repository..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("the upsert executor is not a Spring bean and its owners create it themselves")
    void executorIsNotABean() {
        classes()
                .that()
                .haveSimpleName("KeyedUpsertExecutor")
                .should()
                .notBeAnnotatedWith(org.springframework.stereotype.Component.class)
                .andShould()
                .notBeAnnotatedWith(org.springframework.stereotype.Service.class)
                .andShould()
                .notBeAnnotatedWith(org.springframework.stereotype.Repository.class)
                .andShould()
                .notBeAnnotatedWith(org.springframework.context.annotation.Configuration.class)
                .check(CLASSES);
    }

    @Test
    @DisplayName(
            "the custom fragment of the user repository stays in the repository layer and never reaches web or service")
    void userRepositoryFragmentStaysInTheRepositoryLayer() {
        assertThat(CLASSES.stream()
                        .filter(javaClass -> javaClass.getPackageName().equals("cherry.mastersmith.user.repository"))
                        .map(JavaClass::getSimpleName))
                .as("規則が対象のクラスを見分けている")
                .contains("UserRepositoryImpl", "UserRepositoryCustom");
        noClasses()
                .that()
                .haveSimpleNameStartingWith("UserRepositoryImpl")
                .or()
                .haveSimpleName("UserRepositoryCustom")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("cherry.mastersmith..web..", "cherry.mastersmith..service..")
                .check(CLASSES);
    }
}
