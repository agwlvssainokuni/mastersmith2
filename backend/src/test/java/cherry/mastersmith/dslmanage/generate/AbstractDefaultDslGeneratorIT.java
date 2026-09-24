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
package cherry.mastersmith.dslmanage.generate;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dsl.domain.DslColumn;
import cherry.mastersmith.dsl.domain.DslMenuItem;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.FormPart;
import cherry.mastersmith.dsl.domain.OptionSourceKind;
import cherry.mastersmith.dsl.domain.SearchOperator;
import cherry.mastersmith.dsl.domain.Validation;
import cherry.mastersmith.dsl.domain.ValidationType;
import cherry.mastersmith.dsl.service.DslReader;
import cherry.mastersmith.dsl.validate.PatternChecker;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.domain.ReadPurpose;
import cherry.mastersmith.targetdb.domain.TargetSchemaResult;
import cherry.mastersmith.targetdb.repository.SchemaQueries;
import cherry.mastersmith.targetdb.service.TargetSchemaReader;
import cherry.mastersmith.targetdb.testsupport.ContainerRuntimeCheck;
import cherry.mastersmith.targetdb.testsupport.TargetDbTestDatabase;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * 既定の DSL の生成の結合テストの共通の確かめ（US1.2 の AC1.2.1・AC1.2.2、US1.1 の AC1.1.1〜AC1.1.5・AC1.1.9 の生成の側、
 * BR2.2・BR3.2・BR5.1・BR5.3、NFR4.9）。3種類の DB の子のクラスで、同じ内容を確かめる。
 *
 * <p>テストのクラスごとに本物の DB をコンテナで1つ起動し（U1 の {@code targetdb/testsupport} の仕組み）、名前の重ならない
 * スキーマに見本の表・ビュー・外部キー・コメント・記号を含む名前を作る。読み取りの権限だけのアカウントで U1 の情報スキーマの
 * 読み手を使って写しを読み、生成 → U2 の本物の読み込みの口で検証する。クラスの終わりにスキーマとアカウントを消す。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ContainerRuntimeCheck.class)
abstract class AbstractDefaultDslGeneratorIT {

    /** 問い合わせ1回の待ちの上限（秒。既定の DSL の生成の既定値と同じ）。 */
    private static final int QUERY_TIMEOUT_SECONDS = 20;

    /** 記号を含む名前の表。 */
    static final String SYMBOL_TABLE = "sym 'q' ; --x";

    /** 記号を含む名前のカラム。 */
    static final String SYMBOL_COLUMN = "a: b # c";

    /** YAML の記号と引用符を含むコメント。 */
    static final String SYMBOL_COMMENT = "所属: #&*!'\"- [x]";

    private final TargetDbTestDatabase database = TargetDbTestDatabase.of(product());

    private final String schema = TargetDbTestDatabase.uniqueName("Gen_");

    private final String emptySchema = TargetDbTestDatabase.uniqueName("gen_empty_");

    private final String reader = TargetDbTestDatabase.uniqueName("gen_reader_");

    private final String readerPassword = TestDatabase.randomSecret();

    private final String emptyReader = TargetDbTestDatabase.uniqueName("gen_empty_reader_");

    private final String emptyReaderPassword = TestDatabase.randomSecret();

    private final PatternChecker patterns = GenerateTestSupport.newPatternChecker();

    private final DslReader dslReader = GenerateTestSupport.newReader(patterns);

    /**
     * 確かめる DB の種類を返す。
     *
     * @return 種類
     */
    abstract DatabaseProduct product();

    /**
     * 見本のスキーマを作る SQL を返す（{@code %s} は引用符つきのスキーマの名前に置き換える）。
     *
     * @param q 識別子を引用符で囲む関数
     * @return SQL
     */
    abstract List<String> fixtureSql(UnaryOperator<String> q);

    /**
     * 種類ごとの型の分類の確かめ（真偽値の型など）。
     *
     * @param dept 見本の {@code Dept_Mst}
     */
    abstract void assertProductSpecificColumns(DslTable dept);

    /**
     * MySQL・MariaDB の見本のスキーマを作る SQL（2つの DB で同じ）。
     *
     * @param q 識別子を引用符で囲む関数
     * @return SQL
     */
    static List<String> mysqlFamilySql(UnaryOperator<String> q) {
        String dept = "%s." + q.apply("Dept_Mst");
        return List.of(
                "CREATE TABLE " + dept + " (code VARCHAR(10) NOT NULL COMMENT '部署コード', name VARCHAR(300) NOT NULL,"
                        + " note TEXT NULL, amount DECIMAL(10,2) NOT NULL DEFAULT 0, born DATE NULL,"
                        + " updated_at DATETIME NULL, hm TIME NULL, flag TINYINT(1) NOT NULL DEFAULT 0, bit1 BIT(1) NULL,"
                        + " tiny TINYINT NULL, PRIMARY KEY (code)) COMMENT '部署'",
                "CREATE TABLE %s.emp (id INT NOT NULL PRIMARY KEY, dept_code VARCHAR(10) NULL COMMENT '"
                        + SYMBOL_COMMENT.replace("'", "''") + "', CONSTRAINT fk_emp_dept FOREIGN KEY (dept_code)"
                        + " REFERENCES " + dept + " (code))",
                "CREATE TABLE %s." + q.apply(SYMBOL_TABLE) + " (" + q.apply(SYMBOL_COLUMN)
                        + " INT NOT NULL PRIMARY KEY)",
                "CREATE TABLE %s.a_first (id INT NOT NULL PRIMARY KEY, created TIMESTAMP NULL)",
                "CREATE VIEW %s.v_emp AS SELECT id, dept_code FROM %s.emp");
    }

    /**
     * MySQL・MariaDB の型の分類の確かめ（{@code tinyint(1)}・{@code bit(1)} は真偽値、{@code tinyint} は数値）。
     *
     * @param dept 見本の {@code Dept_Mst}
     */
    static void assertMysqlFamilyColumns(DslTable dept) {
        assertThat(dept.columns().get("flag").dbType().name())
                .as("型の名前は DATA_TYPE のまま")
                .isEqualTo("tinyint");
        assertThat(dept.columns().get("bit1").formPart()).isEqualTo(FormPart.CHECKBOX);
        assertThat(dept.columns().get("tiny").formPart()).isEqualTo(FormPart.NUMBER);
        assertThat(dept.columns().get("updated_at").dbType().name()).isEqualTo("datetime");
    }

    @BeforeAll
    void startDatabase() {
        database.start();
        database.createSchema(schema);
        database.createSchema(emptySchema);
        String quoted = database.q(schema);
        database.execute(fixtureSql(database::q).stream()
                .map(sql -> sql.replace("%s", quoted))
                .toArray(String[]::new));
        database.createReadOnlyAccount(reader, readerPassword, schema);
        database.createReadOnlyAccount(emptyReader, emptyReaderPassword, emptySchema);
    }

    @AfterAll
    void dropAndStop() {
        try {
            patterns.close();
            database.dropAccount(reader);
            database.dropAccount(emptyReader);
            database.dropSchema(schema);
            database.dropSchema(emptySchema);
        } finally {
            database.stop();
        }
    }

    /** 読み取りの権限だけのアカウントで、U1 の情報スキーマの読み手を使って読む読み取りの口。 */
    private TargetSchemaReader schemaReader(String target, String user, String password) {
        return purpose -> {
            assertThat(purpose).isEqualTo(ReadPurpose.GENERATE);
            try (Connection connection = database.connect(target, user, password)) {
                return TargetSchemaResult.success(
                        SchemaQueries.of(product()).read(connection, target, QUERY_TIMEOUT_SECONDS));
            } catch (SQLException e) {
                throw new IllegalStateException("テストの対象DB を読めません", e);
            }
        };
    }

    private DefaultDslResult.Generated generate(String target, String user, String password) {
        DefaultDslResult result = new TargetSchemaDslGenerator(
                        schemaReader(target, user, password), dslReader, new DslTreeBuilder(), new DslYamlWriter())
                .generate();
        assertThat(result).isInstanceOf(DefaultDslResult.Generated.class);
        return (DefaultDslResult.Generated) result;
    }

    @Test
    @DisplayName("a real schema is read, generated and validated with the categories, keys, labels and order")
    void generatesFromTheRealSchema() {
        DefaultDslResult.Generated generated = generate(schema, reader, readerPassword);
        DslModel model = GenerateTestSupport.valid(dslReader, generated.yamlBytes());

        assertThat(generated.dslHash()).isEqualTo(dslReader.hash(generated.yamlBytes()));
        assertThat(model.menus())
                .extracting(DslMenuItem::table)
                .containsExactly("a_first", "Dept_Mst", "emp", SYMBOL_TABLE, "v_emp");
        assertThat(model.tables().keySet()).containsExactly("a_first", "Dept_Mst", "emp", SYMBOL_TABLE, "v_emp");

        DslTable dept = model.tables().get("Dept_Mst");
        assertThat(dept.label().ja()).isEqualTo("部署");
        assertThat(dept.label().en()).isEqualTo("Dept_Mst");
        assertThat(dept.primaryKey()).containsExactly("code");
        DslColumn code = dept.columns().get("code");
        assertThat(code.label().ja()).isEqualTo("部署コード");
        assertThat(code.dbType().name()).isEqualTo("varchar");
        assertThat(code.dbType().length()).isEqualTo(10);
        assertThat(code.formPart()).isEqualTo(FormPart.TEXT);
        assertThat(code.search().operator()).isEqualTo(SearchOperator.EQUALS);
        assertThat(code.validations())
                .extracting(Validation::type)
                .containsExactly(ValidationType.REQUIRED, ValidationType.MAX_LENGTH);
        DslColumn name = dept.columns().get("name");
        assertThat(name.formPart()).as("長さ 300 は長い文字列").isEqualTo(FormPart.TEXTAREA);
        assertThat(name.validations())
                .extracting(Validation::type)
                .containsExactly(ValidationType.REQUIRED, ValidationType.MAX_LENGTH);
        assertThat(dept.columns().get("note").formPart()).isEqualTo(FormPart.TEXTAREA);
        DslColumn amount = dept.columns().get("amount");
        assertThat(amount.formPart()).isEqualTo(FormPart.NUMBER);
        assertThat(amount.validations()).as("既定値のある NOT NULL").isEmpty();
        assertThat(dept.columns().get("born").formPart()).isEqualTo(FormPart.DATE);
        assertThat(dept.columns().get("updated_at").formPart()).isEqualTo(FormPart.DATETIME);
        assertThat(dept.columns().get("hm").formPart()).isEqualTo(FormPart.TEXT);
        assertThat(dept.columns().get("hm").search().operator()).isEqualTo(SearchOperator.EQUALS);
        assertThat(dept.columns().get("flag").formPart()).isEqualTo(FormPart.CHECKBOX);
        assertProductSpecificColumns(dept);

        DslColumn deptCode = model.tables().get("emp").columns().get("dept_code");
        assertThat(deptCode.label().ja()).isEqualTo(SYMBOL_COMMENT);
        assertThat(deptCode.formPart()).isEqualTo(FormPart.SELECT);
        assertThat(deptCode.options().source()).isEqualTo(OptionSourceKind.REFERENCE);
        assertThat(deptCode.options().table()).isEqualTo("Dept_Mst");
        assertThat(deptCode.options().valueColumn()).isEqualTo("code");
        assertThat(model.tables().get("emp").foreignKeys())
                .singleElement()
                .satisfies(fk -> assertThat(fk.referencedTable()).isEqualTo("Dept_Mst"));
        assertThat(model.tables().get(SYMBOL_TABLE).columns()).containsOnlyKeys(SYMBOL_COLUMN);
        DslTable view = model.tables().get("v_emp");
        assertThat(view.view()).isTrue();
        assertThat(view.primaryKey()).isEmpty();
        assertThat(view.foreignKeys()).isEmpty();
        assertThat(model.tables().get("a_first").columns().get("created").formPart())
                .isEqualTo(FormPart.DATETIME);
    }

    @Test
    @DisplayName("generating twice gives the same bytes and no connection values or schema name are written")
    void deterministicAndWithoutConnectionValues() {
        byte[] first = generate(schema, reader, readerPassword).yamlBytes();
        byte[] second = generate(schema, reader, readerPassword).yamlBytes();

        assertThat(second).isEqualTo(first);
        assertThat(new String(first, StandardCharsets.UTF_8))
                .doesNotContain(schema)
                .doesNotContain(reader)
                .doesNotContain(readerPassword)
                .doesNotContain(String.valueOf(database.port()))
                .doesNotContainIgnoringCase("jdbc:");
    }

    @Test
    @DisplayName("a schema without tables gives an empty DSL that passes the validation")
    void emptySchema() {
        DslModel model = GenerateTestSupport.valid(
                dslReader,
                generate(emptySchema, emptyReader, emptyReaderPassword).yamlBytes());

        assertThat(model.menus()).isEmpty();
        assertThat(model.tables()).isEmpty();
    }
}
