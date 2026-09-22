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
package cherry.mastersmith.auth;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-001・ADR-004 の境界の構造の検査。
 *
 * <p>依頼者の決定（Code Generation の問題3・案 A）により、{@code auth} から {@code user.domain} への依存は「エンティティ
 * （{@code User}）」に限って禁じる。秘密の値の型 {@code user.domain.Password} は {@code user.service} の公開の操作の引数として
 * {@code auth} から使う。
 */
class AuthBoundaryArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("cherry.mastersmith");

    private static final DescribedPredicate<JavaClass> USER_ENTITY = DescribedPredicate.describe(
            "entities of user.domain",
            javaClass -> javaClass.getPackageName().startsWith("cherry.mastersmith.user.domain")
                    && javaClass.isAnnotatedWith("jakarta.persistence.Entity"));

    @Test
    @DisplayName("auth does not depend on user.domain entities or user.repository")
    void authUsesOnlyUserServiceOperations() {
        noClasses()
                .that()
                .resideInAPackage("cherry.mastersmith.auth..")
                .should()
                .dependOnClassesThat(USER_ENTITY)
                .orShould()
                .dependOnClassesThat()
                .resideInAPackage("cherry.mastersmith.user.repository..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("user does not depend on auth")
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
    @DisplayName("auth and user do not depend on audit")
    void noDependencyOnAudit() {
        noClasses()
                .that()
                .resideInAnyPackage("cherry.mastersmith.auth..", "cherry.mastersmith.user..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("cherry.mastersmith.audit..")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    @DisplayName("only classes inside user read the password hash of User")
    void passwordHashStaysInUser() {
        noClasses()
                .that()
                .resideOutsideOfPackage("cherry.mastersmith.user..")
                .should()
                .callMethod("cherry.mastersmith.user.domain.User", "getPasswordHash")
                .check(CLASSES);
    }
}
