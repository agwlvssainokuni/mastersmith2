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
package cherry.mastersmith.targetdb.testsupport;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * 対象DB のテストの JUnit 5 の拡張の登録の順の構造の検査（NFR12.3、Build and Test からの戻し Loop-back 1）。
 *
 * <p>JUnit 5 は、前処理（{@code beforeAll}）を登録の順に、後処理（{@code afterAll}）を逆の順に呼ぶ。
 * {@link ContainerRuntimeCheck} を {@link OutputCaptureExtension} より先に登録すると、コンテナの実行環境に届かずにテストを
 * 飛ばしたとき、ログの取り込みが始まらないまま後処理の {@code OutputCapture.pop} が空の待ち行列で失敗し、飛ばしたはずのクラスが
 * {@code initializationError} になる。そのため、両方を登録するテストのクラスは {@link OutputCaptureExtension} を先に登録する。
 *
 * <p>検査の対象は {@code cherry.mastersmith} のテストのクラス全体（ほかの単位のテストも含む）。違反があれば、違反したクラスと
 * 登録の順を一覧で示して失敗する。
 */
class ExtensionOrderArchitectureTest {

    private static final String EXTEND_WITH = "org.junit.jupiter.api.extension.ExtendWith";

    private static final String EXTENSIONS = "org.junit.jupiter.api.extension.Extensions";

    /** 両方の拡張を登録するクラスは、ログの取り込みを先に登録しなければならない、という規則。 */
    private static final ArchCondition<JavaClass> REGISTER_OUTPUT_CAPTURE_FIRST =
            new ArchCondition<>("register OutputCaptureExtension before ContainerRuntimeCheck") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    List<String> order = registeredExtensions(javaClass);
                    int check = order.indexOf(ContainerRuntimeCheck.class.getName());
                    int capture = order.indexOf(OutputCaptureExtension.class.getName());
                    if (check < 0 || capture < 0) {
                        return;
                    }
                    boolean satisfied = capture < check;
                    String message = javaClass.getName() + " registers " + order;
                    events.add(new SimpleConditionEvent(javaClass, satisfied, message));
                }
            };

    /** 検査の対象のクラスから、この検査自身の見本のクラス（わざと誤った順を含む）を除く。 */
    private static final ArchRule RULE = classes()
            .that()
            .doNotHaveFullyQualifiedName(WrongOrderFixture.class.getName())
            .and()
            .doNotHaveFullyQualifiedName(RightOrderFixture.class.getName())
            .should(REGISTER_OUTPUT_CAPTURE_FIRST);

    /**
     * クラスに直接付いた {@code @ExtendWith}（繰り返しの {@code @Extensions} を含む）の拡張を、登録の順に返す。
     *
     * @param javaClass クラス
     * @return 拡張のクラスの名前（登録の順）
     */
    static List<String> registeredExtensions(JavaClass javaClass) {
        List<String> names = new ArrayList<>();
        javaClass.tryGetAnnotationOfType(EXTEND_WITH).ifPresent(annotation -> addValues(annotation, names));
        javaClass.tryGetAnnotationOfType(EXTENSIONS).ifPresent(container -> {
            Optional<Object> value = container.get("value");
            if (value.isPresent() && value.get() instanceof Object[] annotations) {
                for (Object each : annotations) {
                    if (each instanceof JavaAnnotation<?> annotation) {
                        addValues(annotation, names);
                    }
                }
            }
        });
        return names;
    }

    private static void addValues(JavaAnnotation<?> annotation, List<String> names) {
        Optional<Object> value = annotation.get("value");
        if (value.isPresent() && value.get() instanceof Object[] classes) {
            for (Object each : classes) {
                if (each instanceof JavaClass extension) {
                    names.add(extension.getName());
                }
            }
        }
    }

    private static JavaClasses testClasses() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages("cherry.mastersmith");
    }

    @Test
    @DisplayName("every test class registering both extensions registers OutputCaptureExtension first")
    void outputCaptureIsRegisteredFirst() {
        JavaClasses classes = testClasses();
        // 規則が空振りしていないことの確かめ（両方を登録するクラスが見つかる）。
        assertThat(classes.stream()
                        .filter(javaClass ->
                                !javaClass.getName().startsWith(ExtensionOrderArchitectureTest.class.getName()))
                        .map(ExtensionOrderArchitectureTest::registeredExtensions)
                        .anyMatch(order -> order.contains(ContainerRuntimeCheck.class.getName())
                                && order.contains(OutputCaptureExtension.class.getName())))
                .as("両方の拡張を登録するテストのクラスが見つかる")
                .isTrue();

        RULE.check(classes);
    }

    @Test
    @DisplayName("the rule reports a class that registers ContainerRuntimeCheck first and accepts the right order")
    void ruleDetectsWrongOrder() {
        JavaClasses fixtures = new ClassFileImporter().importClasses(WrongOrderFixture.class, RightOrderFixture.class);

        EvaluationResult result =
                classes().should(REGISTER_OUTPUT_CAPTURE_FIRST).evaluate(fixtures);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .singleElement()
                .asString()
                .contains(WrongOrderFixture.class.getName())
                .doesNotContain(RightOrderFixture.class.getName());
    }

    @Test
    @DisplayName("extensions are read in the order they are registered")
    void readsRegistrationOrder() {
        JavaClasses fixtures = new ClassFileImporter().importClasses(WrongOrderFixture.class, RightOrderFixture.class);

        assertThat(registeredExtensions(fixtures.get(WrongOrderFixture.class)))
                .containsExactly(ContainerRuntimeCheck.class.getName(), OutputCaptureExtension.class.getName());
        assertThat(registeredExtensions(fixtures.get(RightOrderFixture.class)))
                .containsExactly(OutputCaptureExtension.class.getName(), ContainerRuntimeCheck.class.getName());
    }

    /** 誤った順の見本（テストとしては実行されない）。 */
    @ExtendWith({ContainerRuntimeCheck.class, OutputCaptureExtension.class})
    static final class WrongOrderFixture {}

    /** 正しい順の見本（テストとしては実行されない）。 */
    @ExtendWith({OutputCaptureExtension.class, ContainerRuntimeCheck.class})
    static final class RightOrderFixture {}
}
