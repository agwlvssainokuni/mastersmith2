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
package cherry.mastersmith.targetdb;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.sql.Statement;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * U1 の境界の構造の検査（NFR4.1・NFR4.6、BR1.1・BR1.6、unit-of-work.md の U1）。{@code AuditBoundaryArchitectureTest} と
 * 同じ置き方で、U1 のパッケージの下に置く。
 */
class TargetDbBoundaryArchitectureTest {

    private static final String TARGETDB = "cherry.mastersmith.targetdb..";

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("cherry.mastersmith");

    /** 書き込み・DDL を発行する JDBC と Spring の JDBC の操作の名前。 */
    private static final Set<String> UPDATE_METHODS = Set.of(
            "execute",
            "executeUpdate",
            "executeLargeUpdate",
            "addBatch",
            "executeBatch",
            "executeLargeBatch",
            "update",
            "batchUpdate");

    private static final DescribedPredicate<JavaCall<?>> UPDATE_CALL = DescribedPredicate.describe(
            "an update or DDL operation of java.sql or Spring JDBC",
            call -> UPDATE_METHODS.contains(call.getName())
                    && (call.getTargetOwner().isAssignableTo(Statement.class)
                            || call.getTargetOwner().getPackageName().startsWith("org.springframework.jdbc")));

    @Test
    @DisplayName("the settings and queries of the target database are used only inside targetdb, not by web layers")
    void configAndRepositoryStayInside() {
        noClasses()
                .that()
                .resideOutsideOfPackage(TARGETDB)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("cherry.mastersmith.targetdb.config..", "cherry.mastersmith.targetdb.repository..")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    @DisplayName("targetdb knows nothing about the DSL, its management or any web layer")
    void targetdbDoesNotKnowTheDsl() {
        noClasses()
                .that()
                .resideInAPackage(TARGETDB)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "cherry.mastersmith.dsl..", "cherry.mastersmith.dslmanage..", "cherry.mastersmith..web..")
                .check(CLASSES);
        classes()
                .that()
                .resideInAPackage(TARGETDB)
                .should()
                .resideInAnyPackage(
                        "cherry.mastersmith.targetdb",
                        "cherry.mastersmith.targetdb.config",
                        "cherry.mastersmith.targetdb.domain",
                        "cherry.mastersmith.targetdb.repository",
                        "cherry.mastersmith.targetdb.service")
                .as("targetdb は API を持たないため web 層を持たない")
                .check(CLASSES);
    }

    @Test
    @DisplayName("targetdb never issues writes or DDL through JDBC or Spring JDBC")
    void noUpdateOperations() {
        // 規則が JDBC の呼び出しを見分けていることの確かめ（読み取りの呼び出しは見つかる）。
        assertThat(CLASSES.stream()
                        .filter(javaClass -> javaClass.getPackageName().startsWith("cherry.mastersmith.targetdb"))
                        .flatMap(javaClass -> javaClass.getMethodCallsFromSelf().stream())
                        .anyMatch(call -> call.getName().equals("executeQuery")
                                && call.getTargetOwner().isAssignableTo(Statement.class)))
                .isTrue();
        noClasses()
                .that()
                .resideInAPackage(TARGETDB)
                .should()
                .callMethodWhere(UPDATE_CALL)
                .check(CLASSES);
        noClasses()
                .that()
                .resideInAPackage(TARGETDB)
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate")
                .check(CLASSES);
    }

    @Test
    @DisplayName("targetdb takes no part in transactions of the internal database")
    void noTransactions() {
        noClasses()
                .that()
                .resideInAPackage(TARGETDB)
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.transaction.annotation.Transactional")
                .check(CLASSES);
    }
}
