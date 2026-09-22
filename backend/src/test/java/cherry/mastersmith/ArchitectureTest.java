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
package cherry.mastersmith;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 層の境界の構造の検査（team.md の Code Style「層の境界」）。
 *
 * <p>U1 の段階ではまだ無い層（repository・エンティティ）があるため、対象が空の規則でも失敗させない。
 */
class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("cherry.mastersmith");

    private static final String[] TRANSACTIONAL = {
        "org.springframework.transaction.annotation.Transactional", "jakarta.transaction.Transactional"
    };

    private static final String[] INJECTION = {
        "org.springframework.beans.factory.annotation.Autowired",
        "jakarta.inject.Inject",
        "jakarta.annotation.Resource",
        "org.springframework.beans.factory.annotation.Value"
    };

    private static boolean isEntity(JavaType type) {
        JavaClass raw = type.toErasure();
        if (raw.isAnnotatedWith("jakarta.persistence.Entity")) {
            return true;
        }
        if (type instanceof JavaParameterizedType parameterized) {
            return parameterized.getActualTypeArguments().stream().anyMatch(ArchitectureTest::isEntity);
        }
        return false;
    }

    private static final ArchCondition<JavaMethod> NOT_RETURN_ENTITY = new ArchCondition<>("not return an @Entity") {
        @Override
        public void check(JavaMethod method, ConditionEvents events) {
            if (isEntity(method.getReturnType())) {
                events.add(SimpleConditionEvent.violated(method, method.getFullName() + " returns an @Entity"));
            }
        }
    };

    private static final DescribedPredicate<JavaClass> CONTROLLER = DescribedPredicate.describe(
            "controllers",
            javaClass -> javaClass.isAnnotatedWith("org.springframework.stereotype.Controller")
                    || javaClass.isAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                    || javaClass.isAnnotatedWith("org.springframework.web.bind.annotation.RestControllerAdvice"));

    private static void check(ArchRule rule) {
        rule.allowEmptyShould(true).check(CLASSES);
    }

    @Test
    @DisplayName("web layer does not use the repository layer directly")
    void webDoesNotUseRepository() {
        check(noClasses()
                .that()
                .resideInAPackage("..web..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..repository.."));
    }

    @Test
    @DisplayName("transaction boundaries are placed only in the service layer")
    void transactionsOnlyInService() {
        for (String annotation : TRANSACTIONAL) {
            check(noClasses()
                    .that()
                    .resideOutsideOfPackage("..service..")
                    .should()
                    .beAnnotatedWith(annotation));
            check(noMethods()
                    .that()
                    .areDeclaredInClassesThat()
                    .resideOutsideOfPackage("..service..")
                    .should()
                    .beAnnotatedWith(annotation));
        }
    }

    @Test
    @DisplayName("controllers never return JPA entities as API responses")
    void controllersDoNotReturnEntities() {
        check(methods()
                .that()
                .areDeclaredInClassesThat(CONTROLLER)
                .and()
                .arePublic()
                .should(NOT_RETURN_ENTITY));
    }

    @Test
    @DisplayName("dependencies are injected through constructors only")
    void constructorInjectionOnly() {
        for (String annotation : INJECTION) {
            check(fields().should().notBeAnnotatedWith(annotation));
            check(methods().should().notBeAnnotatedWith(annotation));
        }
    }

    @Test
    @DisplayName("no class depends on Lombok")
    void noLombok() {
        check(noClasses().should().dependOnClassesThat().resideInAPackage("lombok.."));
    }
}
