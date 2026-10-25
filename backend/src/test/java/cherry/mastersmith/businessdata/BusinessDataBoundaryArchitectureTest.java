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
package cherry.mastersmith.businessdata;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.common.testsupport.StatementCallFixture;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 業務データ（businessdata）の境界の構造の検査（BR3.5・BR11.2、ADR-001・ADR-002、team.md の Code Style、計画の 4.4）。既存の
 * {@code ArchitectureTest}・{@code TargetDbBoundaryArchitectureTest}・{@code RoleBoundaryArchitectureTest} は書き換えず、足す。
 */
class BusinessDataBoundaryArchitectureTest {

    private static final String BUSINESSDATA = "cherry.mastersmith.businessdata..";

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("cherry.mastersmith");

    /** 文を作る JDBC の口の名前（ストアドの呼び出しの {@code prepareCall} を含む）。 */
    private static final Set<String> STATEMENT_FACTORIES = Set.of("prepareStatement", "createStatement", "prepareCall");

    /** 文を実行する JDBC の口の名前（{@code Statement} と {@code PreparedStatement}・{@code CallableStatement} のもの）。 */
    private static final Set<String> STATEMENT_EXECUTIONS = Set.of(
            "execute", "executeQuery", "executeUpdate", "executeBatch", "executeLargeUpdate", "executeLargeBatch");

    private static final DescribedPredicate<JavaCall<?>> STATEMENT_CALL = DescribedPredicate.describe(
            "a statement factory of java.sql.Connection or an execution of java.sql.Statement",
            call -> (STATEMENT_FACTORIES.contains(call.getName())
                            && call.getTargetOwner().isAssignableTo(Connection.class))
                    || (STATEMENT_EXECUTIONS.contains(call.getName())
                            && call.getTargetOwner().isAssignableTo(Statement.class)));

    /**
     * アプリのどこで JDBC の文を作る・実行するかを、クラスの名前で許す一覧（R-06。{@code businessdata} の外を含む）。業務データの SQL は
     * {@code RowSqlExecutor} だけ。ほかは、対象DB のスキーマの読み取りの決まった SQL（{@code targetdb.repository}）と、内部DB の
     * 死活の確かめ（{@code common.health}）。この一覧に足すときは、理由を添えて計画で承認を得る（足すだけで通る規則ではなく、足すこと
     * 自体を見える変更にするための一覧）。
     */
    private static final Set<String> ALLOWED_STATEMENT_OWNERS = Set.of(
            "cherry.mastersmith.businessdata.repository.RowSqlExecutor",
            "cherry.mastersmith.targetdb.repository.MysqlSchemaQueries",
            "cherry.mastersmith.targetdb.repository.PostgresSchemaQueries",
            "cherry.mastersmith.targetdb.repository.SchemaRows",
            "cherry.mastersmith.common.health.TimeBoundedDbHealthIndicator");

    /** 一覧の外のクラスが JDBC の文を作る・実行することを禁じる規則。 */
    private static ArchRule noStatementsOutsideTheAllowedOwners() {
        return noClasses()
                .that(DescribedPredicate.describe(
                        "is not one of the allowed statement owners",
                        (JavaClass javaClass) -> !ALLOWED_STATEMENT_OWNERS.contains(javaClass.getName())))
                .should()
                .callMethodWhere(STATEMENT_CALL);
    }

    @Test
    @DisplayName(
            "businessdata depends only on targetdb service and domain, role service and domain, dsl, common and libraries")
    void allowedDependencies() {
        classes()
                .that()
                .resideInAPackage(BUSINESSDATA)
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(
                        BUSINESSDATA,
                        "cherry.mastersmith.targetdb.service..",
                        "cherry.mastersmith.targetdb.domain..",
                        "cherry.mastersmith.role.service..",
                        "cherry.mastersmith.role.domain..",
                        "cherry.mastersmith.dsl.domain..",
                        "cherry.mastersmith.dsl.service..",
                        "cherry.mastersmith.common..",
                        "java..",
                        "javax..",
                        "jakarta..",
                        "org.springframework..",
                        "org.slf4j..",
                        "com.fasterxml.jackson.annotation..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("businessdata.repository does not depend on targetdb.service")
    void repositoryDoesNotUseTheLendingPort() {
        noClasses()
                .that()
                .resideInAPackage("cherry.mastersmith.businessdata.repository..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("cherry.mastersmith.targetdb.service..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("only RowSqlExecutor creates statements from a JDBC connection in businessdata")
    void onlyRowSqlExecutorCreatesStatements() {
        List<String> callers = CLASSES.stream()
                .filter(javaClass -> javaClass.getPackageName().startsWith("cherry.mastersmith.businessdata"))
                .flatMap(javaClass -> javaClass.getMethodCallsFromSelf().stream())
                .filter(STATEMENT_CALL)
                .map(call -> call.getOriginOwner().getSimpleName() + "#"
                        + call.getOrigin().getName())
                .distinct()
                .toList();
        assertThat(callers)
                .as("規則が呼び出しを見分けている（RowSqlExecutor の読み取りの search・detail と、U2 の書き込みの5つのメソッドが実際に文を" + "作っている。U2 の計画の A2）")
                .containsExactlyInAnyOrder(
                        "RowSqlExecutor#search",
                        "RowSqlExecutor#detail",
                        "RowSqlExecutor#precheckUnique",
                        "RowSqlExecutor#insert",
                        "RowSqlExecutor#update",
                        "RowSqlExecutor#delete",
                        "RowSqlExecutor#inspectViolation");
        noClasses()
                .that()
                .resideInAPackage(BUSINESSDATA)
                .and()
                .doNotHaveFullyQualifiedName("cherry.mastersmith.businessdata.repository.RowSqlExecutor")
                .should()
                .callMethodWhere(STATEMENT_CALL)
                .check(CLASSES);
    }

    @Test
    @DisplayName("no class outside the allowed owners creates or runs a JDBC statement, in businessdata or elsewhere")
    void statementsOnlyInTheAllowedOwners() {
        noStatementsOutsideTheAllowedOwners().check(CLASSES);
        assertThat(ALLOWED_STATEMENT_OWNERS)
                .as("一覧のクラスが実際に存在する（名前の誤りで規則が空振りしない）")
                .allSatisfy(name -> assertThat(CLASSES.contain(name)).as(name).isTrue());
    }

    @Test
    @DisplayName("the rule finds prepareCall and Statement.execute outside the allowed owners and fails")
    void ruleFindsCallsOutsideTheAllowedOwners() {
        JavaClasses fixture = new ClassFileImporter().importClasses(StatementCallFixture.class);

        assertThatThrownBy(() -> noStatementsOutsideTheAllowedOwners().check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("StatementCallFixture")
                .hasMessageContaining("prepareCall")
                .hasMessageContaining("execute");
    }

    @Test
    @DisplayName("the read side of businessdata takes no part in transactions and uses no JdbcTemplate")
    void noTransactionsOnTheReadSide() {
        noClasses()
                .that()
                .resideInAnyPackage(
                        "cherry.mastersmith.businessdata.web..",
                        "cherry.mastersmith.businessdata.domain..",
                        "cherry.mastersmith.businessdata.repository..")
                .or()
                .haveFullyQualifiedName("cherry.mastersmith.businessdata.service.BusinessDataReadService")
                .or()
                .haveFullyQualifiedName("cherry.mastersmith.businessdata.service.TableAccessResolver")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.transaction.annotation.Transactional")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.transaction.support.TransactionTemplate")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate")
                .check(CLASSES);
    }

    @Test
    @DisplayName(
            "the write side of businessdata opens no transaction of the internal database and uses no JdbcTemplate")
    void noTransactionsOnTheWriteSide() {
        // U2 の計画の 3節の足す決まり（BR9.4）。対象DB の書き込みのトランザクションは targetdb.service の書き込みの口だけが持つ。
        noClasses()
                .that()
                .resideInAnyPackage(
                        "cherry.mastersmith.businessdata.web..",
                        "cherry.mastersmith.businessdata.domain..",
                        "cherry.mastersmith.businessdata.repository..")
                .or()
                .haveFullyQualifiedName("cherry.mastersmith.businessdata.service.BusinessDataWriteService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.transaction.annotation.Transactional")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.transaction.support.TransactionTemplate")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate")
                .check(CLASSES);
    }

    @Test
    @DisplayName("only audit depends on businessdata, and only on its domain for the change event and the audit reason")
    void nobodyDependsOnBusinessDataButAuditOnTheDomain() {
        // 規則が依存を見分けていることの確かめ（audit → businessdata.domain が実際にある。U2 の計画の A1・D6: A）。
        assertThat(CLASSES.stream()
                        .filter(javaClass -> javaClass.getPackageName().startsWith("cherry.mastersmith.audit"))
                        .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                        .anyMatch(dependency -> dependency
                                .getTargetClass()
                                .getPackageName()
                                .equals("cherry.mastersmith.businessdata.domain")))
                .isTrue();
        noClasses()
                .that()
                .resideOutsideOfPackages(BUSINESSDATA, "cherry.mastersmith.audit..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(BUSINESSDATA)
                .check(CLASSES);
        // audit が使ってよいのは、出来事（BusinessDataChangedEvent）と監査の理由の値（BusinessDataAuditReason）を置く domain だけ
        // （契約 C5 の向き。role・group などの出来事と同じ形）。
        noClasses()
                .that()
                .resideInAPackage("cherry.mastersmith.audit..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "cherry.mastersmith.businessdata.service..",
                        "cherry.mastersmith.businessdata.repository..",
                        "cherry.mastersmith.businessdata.web..",
                        "cherry.mastersmith.businessdata")
                .check(CLASSES);
    }
}
