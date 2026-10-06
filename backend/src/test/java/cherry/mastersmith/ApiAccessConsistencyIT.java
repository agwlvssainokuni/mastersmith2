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

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.security.ApiAccess;
import cherry.mastersmith.common.security.ApiAccessLevel;
import cherry.mastersmith.common.testsupport.ApiAccessRules;
import cherry.mastersmith.common.testsupport.ApiAccessSubjects;
import cherry.mastersmith.common.testsupport.ApiAccessSubjects.Subject;
import cherry.mastersmith.common.testsupport.PublicApiInventory;
import cherry.mastersmith.common.testsupport.PublicApiInventory.Diff;
import cherry.mastersmith.common.testsupport.PublicApiInventory.PublicApiEntry;
import cherry.mastersmith.common.testsupport.TestDatabase;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.web.access.WebInvocationPrivilegeEvaluator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * API の分類の印 {@code ApiAccess} の実行時の検査（security-design 4.3・4.4、要件 NFR1.3・NFR1.5・NFR1.6・NFR1.8、機能設計
 * BR1.4〜BR1.6、US1.1 AC1.1.15）。
 *
 * <p>本番の設定で起動し（テスト用の決まりとテストだけの口の設定は入れない）、アプリが持つ口の一覧の口ごとに、宣言された方法（無ければ
 * GET・POST・PUT・PATCH・DELETE）と道の型（パス変数は見本の値 {@code 1} に置き換える）の組を、3つの主体で本番の Spring Security の
 * 判定（{@link WebInvocationPrivilegeEvaluator}）にかけ、印ごとの期待と比べる。要求は送らない。
 *
 * <p>起動の形は {@code ApiDefaultAccessIT} と同じ（計画 D-2。クラスごとに一時の内部DB を設定するため、起動の文脈は使い回されない）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiAccessConsistencyIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiAccessConsistencyIT.class);

    /** 今ある口の数（機能設計 3節）。 */
    private static final int MIN_ENDPOINTS = 34;

    /** 方法の宣言が無い口で確かめる方法。 */
    private static final List<String> DEFAULT_METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE");

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("cherry.mastersmith");

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    WebInvocationPrivilegeEvaluator evaluator;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    private static String keyOf(HandlerMethod handler) {
        Method method = handler.getMethod();
        return ApiAccessRules.key(
                method.getDeclaringClass().getName(),
                method.getName(),
                Arrays.stream(method.getParameterTypes()).map(Class::getName).toList());
    }

    private static Optional<ApiAccessLevel> levelOf(HandlerMethod handler) {
        ApiAccess onMethod = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), ApiAccess.class);
        if (onMethod != null) {
            return Optional.of(onMethod.value());
        }
        return Optional.ofNullable(AnnotatedElementUtils.findMergedAnnotation(
                        handler.getMethod().getDeclaringClass(), ApiAccess.class))
                .map(ApiAccess::value);
    }

    private static List<String> methodsOf(RequestMappingInfo info) {
        Set<RequestMethod> declared = info.getMethodsCondition().getMethods();
        if (declared.isEmpty()) {
            return DEFAULT_METHODS;
        }
        return declared.stream().map(RequestMethod::name).sorted().toList();
    }

    private static String uriOf(String pattern) {
        return pattern.replaceAll("\\{[^}]*}", "1");
    }

    private Map<RequestMappingInfo, HandlerMethod> handlers() {
        return handlerMapping.getHandlerMethods();
    }

    @Test
    @DisplayName("the privilege evaluator is available as a bean so that no MockMvc fallback is needed")
    void evaluatorIsAvailable() {
        assertThat(evaluator).isNotNull();
        assertThat(handlers()).isNotEmpty();
    }

    @Test
    @DisplayName("the runtime endpoints come only from production classes")
    void onlyProductionClasses() {
        Set<String> productionClassNames =
                PRODUCTION_CLASSES.stream().map(JavaClass::getName).collect(Collectors.toSet());

        List<String> outsiders = handlers().values().stream()
                .map(handler -> handler.getMethod().getDeclaringClass().getName())
                .filter(name -> !productionClassNames.contains(name))
                .distinct()
                .sorted()
                .toList();

        assertThat(outsiders).as("本番に無いクラス（テストの出力のクラスなど）の口").isEmpty();
    }

    @Test
    @DisplayName("the runtime endpoints match the endpoints of the static check by class and method, at least 34")
    void sameSetAsStaticCheck() {
        Set<String> runtime = handlers().values().stream()
                .map(ApiAccessConsistencyIT::keyOf)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> statics = ApiAccessRules.endpointKeys(PRODUCTION_CLASSES);

        assertThat(runtime).hasSizeGreaterThanOrEqualTo(MIN_ENDPOINTS);
        assertThat(runtime).isEqualTo(statics);
    }

    @Test
    @DisplayName("every endpoint, declared method and subject is judged as its mark expects")
    void judgementsMatchMarks() {
        List<String> violations = new ArrayList<>();
        int checks = 0;
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlers().entrySet()) {
            HandlerMethod handler = entry.getValue();
            String name = handler.getMethod().getDeclaringClass().getName() + "."
                    + handler.getMethod().getName();
            Optional<ApiAccessLevel> level = levelOf(handler);
            if (level.isEmpty()) {
                violations.add(name + " has no ApiAccess mark");
                continue;
            }
            for (String pattern : entry.getKey().getPatternValues()) {
                for (String method : methodsOf(entry.getKey())) {
                    for (Subject subject : Subject.values()) {
                        boolean allowed = evaluator.isAllowed("", uriOf(pattern), method, subject.authentication());
                        boolean expected = ApiAccessSubjects.expected(level.get(), subject);
                        checks++;
                        if (allowed != expected) {
                            violations.add(name + " " + method + " " + pattern + " " + subject + " mark="
                                    + level.get() + " expected=" + (expected ? "allowed" : "denied") + " actual="
                                    + (allowed ? "allowed" : "denied"));
                        }
                    }
                }
            }
        }
        LOGGER.atInfo()
                .addKeyValue("endpoints", handlers().size())
                .addKeyValue("checks", checks)
                .addKeyValue("violations", violations.size())
                .log("ApiAccess の実行時の検査の件数");

        assertThat(violations).as("印と本番の安全の決まりの判定の食い違い").isEmpty();
    }

    @Test
    @DisplayName("the PUBLIC endpoints match the inventory row for row")
    void publicEndpointsMatchInventory() {
        Set<PublicApiEntry> actual = new HashSet<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlers().entrySet()) {
            if (levelOf(entry.getValue()).orElse(null) == ApiAccessLevel.PUBLIC) {
                actual.add(PublicApiInventory.entryOf(entry.getKey()));
            }
        }
        int pairs = actual.stream()
                .mapToInt(entry -> entry.methods().size() * entry.patterns().size())
                .sum();
        LOGGER.atInfo()
                .addKeyValue("publicEndpoints", actual.size())
                .addKeyValue("publicPairs", pairs)
                .log("PUBLIC の口の件数");

        Diff diff = PublicApiInventory.diff(PublicApiInventory.EXPECTED, actual);

        assertThat(diff.added()).as("一覧に無い PUBLIC の口（一覧の行と理由を同じ変更で足す）").isEmpty();
        assertThat(diff.removed()).as("一覧にあるのに PUBLIC でない口（一覧の行を同じ変更で外す）").isEmpty();
    }
}
