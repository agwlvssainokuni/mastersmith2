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
package cherry.mastersmith.common.testsupport;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import cherry.mastersmith.access.domain.AdminPaths;
import cherry.mastersmith.common.security.ApiAccess;
import cherry.mastersmith.common.security.ApiAccessLevel;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaEnumConstant;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.properties.HasAnnotations;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API の分類の印 {@link ApiAccess} の規則を作る1か所（security-design 4.2・4.2.1、機能設計 BR1.1〜BR1.3・BR1.8）。
 *
 * <p>本番のクラスに当てる静的な検査（{@code ApiAccessArchitectureTest}）と、違反の見本に当てる検査の検査
 * （{@code ApiAccessRulesTest}）が、同じこの規則を使う。規則が緩んだら見本の確かめで気づけるようにするためである。
 *
 * <p>口は、{@code @RestController}・{@code @Controller} のクラスに宣言された、RequestMapping 系の注釈（{@code @GetMapping} などを
 * 含む）を持つ方法とする。規則は口をすべて対象に取り、条件の中で印ごとに場合分けする（NFR 設計の読み直し R-07）。どの規則も対象が
 * 空なら落とす（{@code allowEmptyShould(false)}。全体の設定 {@code archRule.failOnEmptyShould = false} は変えない）。
 *
 * <p>道は、クラスと方法の RequestMapping の道をつないで作り、設定値の置き換えの形 {@code ${名前:既定値}} は既定値で読む。そのため、
 * 設定で道を変えた環境とのずれは、ここ（静的な検査）では見えず、実際の道を読む実行時の検査だけが見る。
 *
 * <p>違反の文には、口のクラス名・方法名・道だけを出す（NFR1.10）。
 */
public final class ApiAccessRules {

    private static final String ROOT_PACKAGE = "cherry.mastersmith";

    private static final String COMMON_PACKAGE = "cherry.mastersmith.common";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{[^}:]*(?::([^}]*))?}");

    /** 口（RequestMapping 系の注釈を持つ、コントローラーのクラスの方法）。 */
    public static final DescribedPredicate<JavaMethod> ENDPOINT =
            DescribedPredicate.describe("request handler methods of controllers", ApiAccessRules::isEndpoint);

    private ApiAccessRules() {}

    /**
     * 口に効く印が1つだけ（方法かクラスのどちらか一方だけに印がある）ことの規則（NFR1.1、BR1.1）。
     *
     * @return 規則
     */
    public static ArchRule markedExactlyOnce() {
        return methods()
                .that(ENDPOINT)
                .should(new ArchCondition<>("carry exactly one ApiAccess mark on either the method or the class") {
                    @Override
                    public void check(JavaMethod method, ConditionEvents events) {
                        boolean onMethod = method.isAnnotatedWith(ApiAccess.class);
                        boolean onClass = method.getOwner().isAnnotatedWith(ApiAccess.class);
                        if (!onMethod && !onClass) {
                            events.add(
                                    SimpleConditionEvent.violated(method, describe(method) + " has no ApiAccess mark"));
                        } else if (onMethod && onClass) {
                            events.add(SimpleConditionEvent.violated(
                                    method,
                                    describe(method) + " has ApiAccess marks on both the method and the class"));
                        }
                    }
                })
                .allowEmptyShould(false);
    }

    /**
     * ADMIN の印と管理者の道（{@link AdminPaths#isAdminOnly}）が両方向で一致することの規則（NFR1.2、BR1.2）。
     *
     * <p>印が無い・二重の口は {@link #markedExactlyOnce()} が受け持つため、ここでは場合分けで通す。
     *
     * @return 規則
     */
    public static ArchRule adminMatchesAdminPaths() {
        return methods()
                .that(ENDPOINT)
                .should(new ArchCondition<>("be ADMIN exactly when the path is an admin-only path") {
                    @Override
                    public void check(JavaMethod method, ConditionEvents events) {
                        Optional<ApiAccessLevel> level = effectiveLevel(method);
                        if (level.isEmpty()) {
                            return;
                        }
                        for (String path : paths(method)) {
                            boolean adminPath = AdminPaths.isAdminOnly(path);
                            boolean adminMark = level.get() == ApiAccessLevel.ADMIN;
                            if (adminMark && !adminPath) {
                                events.add(SimpleConditionEvent.violated(
                                        method, describe(method) + " is ADMIN but outside the admin paths"));
                            } else if (!adminMark && adminPath) {
                                events.add(SimpleConditionEvent.violated(
                                        method, describe(method) + " is on an admin path but is not ADMIN"));
                            }
                        }
                    }
                })
                .allowEmptyShould(false);
    }

    /**
     * AUTHENTICATED の口の道が {@code /api/} の下で、管理者の道の外にあることの規則（NFR1.2、BR1.3）。
     *
     * @return 規則
     */
    public static ArchRule authenticatedPathsInsideApi() {
        return methods()
                .that(ENDPOINT)
                .should(new ArchCondition<>("be under /api/ and outside the admin paths when AUTHENTICATED") {
                    @Override
                    public void check(JavaMethod method, ConditionEvents events) {
                        if (effectiveLevel(method).orElse(null) != ApiAccessLevel.AUTHENTICATED) {
                            return;
                        }
                        for (String path : paths(method)) {
                            if (!path.startsWith("/api/") || AdminPaths.isAdminOnly(path)) {
                                events.add(SimpleConditionEvent.violated(
                                        method,
                                        describe(method)
                                                + " is AUTHENTICATED but not under /api/ outside the admin paths"));
                            }
                        }
                    }
                })
                .allowEmptyShould(false);
    }

    /**
     * {@code common.security} が機能のパッケージに依存しないことの規則（BR1.8）。本番のクラスだけに当てる。
     *
     * <p>{@code cherry.mastersmith} の中で依存してよいのは {@code cherry.mastersmith.common} の下だけとする。
     *
     * @return 規則
     */
    public static ArchRule commonSecurityIndependentOfFeatures() {
        return noClasses()
                .that()
                .resideInAPackage(COMMON_PACKAGE + ".security..")
                .should()
                .dependOnClassesThat(DescribedPredicate.describe(
                        "classes of the feature packages",
                        target -> target.getPackageName().startsWith(ROOT_PACKAGE)
                                && !target.getPackageName().startsWith(COMMON_PACKAGE)))
                .allowEmptyShould(false);
    }

    /**
     * 口の一覧を返す（クラス名と方法の順）。
     *
     * @param classes 読み込んだクラス
     * @return 口
     */
    public static List<JavaMethod> endpoints(JavaClasses classes) {
        List<JavaMethod> endpoints = new ArrayList<>();
        for (JavaClass javaClass : classes) {
            for (JavaMethod method : javaClass.getMethods()) {
                if (isEndpoint(method)) {
                    endpoints.add(method);
                }
            }
        }
        endpoints.sort((left, right) -> key(left).compareTo(key(right)));
        return endpoints;
    }

    /**
     * 口の集合を、クラス名・方法名・引数の型の組の文字の集合で返す（実行時の検査の集合と比べるため）。
     *
     * @param classes 読み込んだクラス
     * @return 口の鍵の集合
     */
    public static Set<String> endpointKeys(JavaClasses classes) {
        return endpoints(classes).stream().map(ApiAccessRules::key).collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * 口の鍵（クラス名#方法名(引数の型,...)）を作る。実行時の検査の口の鍵と同じ形。
     *
     * @param className クラスの完全な名前
     * @param methodName 方法の名前
     * @param parameterTypeNames 引数の型の完全な名前
     * @return 口の鍵
     */
    public static String key(String className, String methodName, List<String> parameterTypeNames) {
        return className + "#" + methodName + "(" + String.join(",", parameterTypeNames) + ")";
    }

    /**
     * 口に効く印の値を返す（方法かクラスの一方だけに印があるときだけ）。
     *
     * @param method 口
     * @return 印の値。印が無い・二重なら空
     */
    public static Optional<ApiAccessLevel> effectiveLevel(JavaMethod method) {
        Optional<ApiAccessLevel> onMethod = levelOf(method);
        Optional<ApiAccessLevel> onClass = levelOf(method.getOwner());
        if (onMethod.isPresent() == onClass.isPresent()) {
            return Optional.empty();
        }
        return onMethod.isPresent() ? onMethod : onClass;
    }

    /**
     * 口の道を返す（クラスと方法の道をつなぎ、{@code ${名前:既定値}} は既定値で読む）。
     *
     * @param method 口
     * @return 道の一覧
     */
    public static List<String> paths(JavaMethod method) {
        List<String> classPaths =
                mappingPaths(method.getOwner().tryGetAnnotationOfType(RequestMapping.class.getName()));
        List<String> methodPaths = mappingPaths(requestMappingOf(method));
        List<String> paths = new ArrayList<>();
        for (String classPath : classPaths) {
            for (String methodPath : methodPaths) {
                paths.add(join(resolveDefaults(classPath), resolveDefaults(methodPath)));
            }
        }
        return paths;
    }

    private static boolean isEndpoint(JavaMethod method) {
        JavaClass owner = method.getOwner();
        boolean controller = owner.isAnnotatedWith(RestController.class) || owner.isAnnotatedWith(Controller.class);
        return controller && requestMappingOf(method).isPresent();
    }

    private static Optional<JavaAnnotation<JavaMethod>> requestMappingOf(JavaMethod method) {
        return method.getAnnotations().stream()
                .filter(annotation -> annotation.getRawType().isEquivalentTo(RequestMapping.class)
                        || annotation.getRawType().isAnnotatedWith(RequestMapping.class))
                .findFirst();
    }

    private static Optional<ApiAccessLevel> levelOf(HasAnnotations<?> element) {
        return element.tryGetAnnotationOfType(ApiAccess.class.getName())
                .flatMap(annotation -> annotation.get("value"))
                .map(value -> ApiAccessLevel.valueOf(((JavaEnumConstant) value).name()));
    }

    private static List<String> mappingPaths(Optional<? extends JavaAnnotation<?>> annotation) {
        if (annotation.isEmpty()) {
            return List.of("");
        }
        List<String> paths = stringsOf(annotation.get(), "path");
        if (paths.isEmpty()) {
            paths = stringsOf(annotation.get(), "value");
        }
        return paths.isEmpty() ? List.of("") : paths;
    }

    private static List<String> stringsOf(JavaAnnotation<?> annotation, String property) {
        return annotation
                .get(property)
                .filter(String[].class::isInstance)
                .map(value -> List.of((String[]) value))
                .orElse(List.of());
    }

    private static String resolveDefaults(String path) {
        Matcher matcher = PLACEHOLDER.matcher(path);
        StringBuilder resolved = new StringBuilder();
        while (matcher.find()) {
            String defaultValue = matcher.group(1) == null ? matcher.group() : matcher.group(1);
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(defaultValue));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    private static String join(String classPath, String methodPath) {
        String joined;
        if (classPath.isEmpty()) {
            joined = methodPath;
        } else if (methodPath.isEmpty()) {
            joined = classPath;
        } else {
            joined = stripTrailingSlash(classPath) + (methodPath.startsWith("/") ? methodPath : "/" + methodPath);
        }
        return joined.startsWith("/") ? joined : "/" + joined;
    }

    private static String stripTrailingSlash(String path) {
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private static String key(JavaMethod method) {
        return key(
                method.getOwner().getName(),
                method.getName(),
                method.getRawParameterTypes().stream().map(JavaClass::getName).toList());
    }

    private static String describe(JavaMethod method) {
        return method.getOwner().getName() + "." + method.getName() + " " + paths(method);
    }
}
