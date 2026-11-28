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

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresIT;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * 内部DB が PostgreSQL のテスト（Intent 261114-internal-db-postgresql の U3）の構造の検査（計画の Step 4。BR9.1・BR9.2・NFR3.1・NFR9.x）。
 * コンテナを使わず、普段の {@code verify} の単体テストの中で流れる。対象は、{@link InternalDbPostgresIT} の付いたクラスと、U3 の
 * PostgreSQL 用の部品（{@code internaldb/testsupport}）。既存の境界テストは緩めない。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PgTestBoundaryArchitectureTest {

    private static final String TESTSUPPORT_PACKAGE = "cherry.mastersmith.internaldb.testsupport";

    /** U3 が足した PostgreSQL 用の部品（外側のクラスの名前）。 */
    private static final Set<String> U3_PARTS = Set.of(
            TESTSUPPORT_PACKAGE + ".PgApp",
            TESTSUPPORT_PACKAGE + ".PgSharedApp",
            TESTSUPPORT_PACKAGE + ".ConflictProbe",
            TESTSUPPORT_PACKAGE + ".WaitWindow",
            TESTSUPPORT_PACKAGE + ".PortableFixture",
            TESTSUPPORT_PACKAGE + ".PgClockConfig",
            TESTSUPPORT_PACKAGE + ".PgUserSearchSamples");

    /** H2 向けの既存のテストの部品（PostgreSQL のテストは依存しない）。 */
    private static final Set<String> H2_ONLY_HELPERS = Set.of(
            "cherry.mastersmith.auth.testsupport.H2SessionWaits",
            "cherry.mastersmith.common.testsupport.H2BlockedSessions",
            "cherry.mastersmith.role.testsupport.RoleFixtures",
            "cherry.mastersmith.role.testsupport.FailingSettingTrigger",
            "cherry.mastersmith.useradmin.testsupport.UserAdminFixtures",
            "cherry.mastersmith.useradmin.testsupport.TestUserAdminBarrier",
            "cherry.mastersmith.businessdata.testsupport.BusinessDataWriteTables",
            "cherry.mastersmith.auth.testsupport.TestLoginAttemptBarrier",
            "cherry.mastersmith.invitation.testsupport.TestInvitationBarrier");

    /** U3 の新しいクラスが使ってよい、既存のテストの部品（固定の一覧。ここに無いものを使うときは、計画とこの一覧に足してから使う）。 */
    private static final Set<String> ALLOWED_TESTSUPPORT = Set.of(
            "cherry.mastersmith.common.testsupport.HttpTestClient",
            "cherry.mastersmith.common.testsupport.JsonLogRecords",
            "cherry.mastersmith.common.testsupport.LogEvents",
            "cherry.mastersmith.common.testsupport.TestDatabase",
            "cherry.mastersmith.auth.testsupport.MutableClock",
            "cherry.mastersmith.mail.testsupport.SmtpTestServer",
            "cherry.mastersmith.invitation.testsupport.ReceivedMails",
            "cherry.mastersmith.common.testsupport.StatementCounter");

    private static final String TEST_DATABASE = "cherry.mastersmith.common.testsupport.TestDatabase";

    private static final Set<String> TEST_DATABASE_FORBIDDEN_METHODS = Set.of("url", "register");

    private static final Path SOURCE_ROOT = locateSourceRoot();

    // 静的な項目にしない（クラスの読み込みの結果をテストの JVM に持ち続けず、このクラスのテストが終われば手放す）。
    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
            .withImportOption(location -> location.contains("/internaldb/") || location.contains("/postgres/"))
            .importPackages("cherry.mastersmith");

    private static Path locateSourceRoot() {
        for (String candidate : List.of("src/test/java", "backend/src/test/java")) {
            Path path = Path.of(candidate);
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        throw new IllegalStateException("the test source directory was not found");
    }

    private static String outer(JavaClass javaClass) {
        JavaClass current = javaClass;
        while (current.getEnclosingClass().isPresent()) {
            current = current.getEnclosingClass().get();
        }
        return current.getName();
    }

    private static boolean isPgIt(JavaClass javaClass) {
        return isPgItName(javaClass.getName());
    }

    private static boolean isPgItName(String name) {
        String simple = name.substring(name.lastIndexOf('.') + 1);
        return simple.startsWith("Pg") && simple.endsWith("IT");
    }

    /** U3 の新しいクラス（Pg で始まる IT と、U3 の部品）。 */
    private List<JavaClass> u3Classes() {
        return classes.stream()
                .filter(c -> U3_PARTS.contains(outer(c)) || isPgItName(outer(c)))
                .toList();
    }

    /** {@link InternalDbPostgresIT} の付いたクラス（U1 のものを含む）と、U3 の部品。 */
    private List<JavaClass> subjects() {
        List<JavaClass> subjects = new ArrayList<>(u3Classes());
        classes.stream()
                .filter(c -> c.isAnnotatedWith(InternalDbPostgresIT.class.getName()))
                .filter(c -> !subjects.contains(c))
                .forEach(subjects::add);
        return subjects;
    }

    @Test
    @DisplayName("the PostgreSQL IT classes are found, and every one named Pg...IT carries the annotation")
    void everyPgItCarriesTheAnnotation() {
        List<String> missing = classes.stream()
                .filter(c -> !c.isNestedClass() && isPgIt(c))
                .filter(c -> !c.isAnnotatedWith(InternalDbPostgresIT.class.getName()))
                .map(JavaClass::getName)
                .toList();

        assertThat(subjects()).isNotEmpty();
        assertThat(missing).as("Pg...IT classes without @InternalDbPostgresIT").isEmpty();
    }

    @Test
    @DisplayName("every class with the PostgreSQL IT annotation is named ...IT so the Gradle filter cannot miss it")
    void annotatedClassesAreNamedIt() {
        List<String> notIt = classes.stream()
                .filter(c -> !c.isNestedClass())
                .filter(c -> c.isAnnotatedWith(InternalDbPostgresIT.class.getName()))
                .filter(c -> !c.getSimpleName().endsWith("IT"))
                .filter(c -> !c.isAnnotation())
                .map(JavaClass::getName)
                .toList();

        assertThat(notIt).as("annotated classes not named ...IT").isEmpty();
        assertThat(classes.stream()
                        .filter(c -> !c.isNestedClass())
                        .filter(c -> c.isAnnotatedWith(InternalDbPostgresIT.class.getName()))
                        .filter(c -> !c.isAnnotation())
                        .count())
                .as("the number of @InternalDbPostgresIT classes")
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("the PostgreSQL tests and their parts depend on no H2 type and no H2-only test helper")
    void noH2Dependency() {
        List<String> violations = new ArrayList<>();
        for (JavaClass subject : subjects()) {
            for (Dependency dependency : subject.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                String targetOuter = outer(target);
                if (target.getPackageName().startsWith("org.h2") || H2_ONLY_HELPERS.contains(targetOuter)) {
                    violations.add(subject.getName() + " -> " + target.getName());
                }
            }
        }

        assertThat(violations).as("forbidden dependencies").isEmpty();
    }

    @Test
    @DisplayName("the PostgreSQL tests never call TestDatabase.url or TestDatabase.register (randomSecret is allowed)")
    void testDatabaseUrlAndRegisterAreNotCalled() {
        List<String> violations = new ArrayList<>();
        for (JavaClass subject : subjects()) {
            for (JavaMethodCall call : subject.getMethodCallsFromSelf()) {
                if (TEST_DATABASE.equals(call.getTargetOwner().getName())
                        && TEST_DATABASE_FORBIDDEN_METHODS.contains(
                                call.getTarget().getName())) {
                    violations.add(
                            subject.getName() + " calls " + call.getTarget().getName());
                }
            }
        }

        assertThat(violations).as("forbidden TestDatabase calls").isEmpty();
    }

    @Test
    @DisplayName("the new U3 classes use only the fixed list of existing test helpers")
    void newClassesUseOnlyAllowedTestHelpers() {
        List<String> violations = new ArrayList<>();
        for (JavaClass subject : u3Classes()) {
            for (Dependency dependency : subject.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                String packageName = target.getPackageName();
                boolean testHelper =
                        packageName.startsWith("cherry.mastersmith.") && packageName.contains(".testsupport");
                if (testHelper
                        && !packageName.equals(TESTSUPPORT_PACKAGE)
                        && !ALLOWED_TESTSUPPORT.contains(outer(target))) {
                    violations.add(subject.getName() + " -> " + target.getName());
                }
            }
        }

        assertThat(violations).as("test helpers outside the allowed list").isEmpty();
    }

    @Test
    @DisplayName("the sources of the new U3 classes contain no image reference and no H2-only SQL")
    void sourcesHaveNoImageReferenceNorH2Sql() throws IOException {
        List<String> violations = new ArrayList<>();
        Set<String> seen =
                u3Classes().stream().map(PgTestBoundaryArchitectureTest::outer).collect(Collectors.toSet());
        for (String name : seen) {
            Path file = SOURCE_ROOT.resolve(name.replace('.', '/') + ".java");
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (text.contains("postgres:")) {
                violations.add(file.getFileName() + " has a container image reference");
            }
            String upper = text.toUpperCase(java.util.Locale.ROOT);
            if (upper.contains("INFORMATION_SCHEMA.SESSIONS") || upper.contains("DATEADD(")) {
                violations.add(file.getFileName() + " uses H2-only SQL");
            }
        }

        assertThat(seen).as("the new U3 classes").isNotEmpty();
        assertThat(violations).as("source violations").isEmpty();
    }
}
