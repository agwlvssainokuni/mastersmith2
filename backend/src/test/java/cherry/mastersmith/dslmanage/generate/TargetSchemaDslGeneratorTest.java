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

import static cherry.mastersmith.dslmanage.generate.GenerateTestSupport.INT;
import static cherry.mastersmith.dslmanage.generate.GenerateTestSupport.VARCHAR_50;
import static cherry.mastersmith.dslmanage.generate.GenerateTestSupport.notNull;
import static cherry.mastersmith.dslmanage.generate.GenerateTestSupport.nullable;
import static cherry.mastersmith.dslmanage.generate.GenerateTestSupport.schema;
import static cherry.mastersmith.dslmanage.generate.GenerateTestSupport.table;
import static cherry.mastersmith.dslmanage.generate.GenerateTestSupport.valid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.dsl.domain.DslFormat;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.domain.DslReadResult;
import cherry.mastersmith.dsl.domain.FormPart;
import cherry.mastersmith.dsl.service.DslReader;
import cherry.mastersmith.dsl.validate.PatternChecker;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.domain.ReadPurpose;
import cherry.mastersmith.targetdb.domain.TargetColumn;
import cherry.mastersmith.targetdb.domain.TargetDbType;
import cherry.mastersmith.targetdb.domain.TargetSchema;
import cherry.mastersmith.targetdb.domain.TargetSchemaResult;
import cherry.mastersmith.targetdb.domain.TargetTable;
import cherry.mastersmith.targetdb.domain.UnavailableReason;
import cherry.mastersmith.targetdb.service.TargetSchemaReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;

/**
 * 生成の口の単体テスト（BR1.1・BR5.1〜BR5.3、NFR2.5・NFR1.7・NFR4.9、AC1.1.5 の U3 の側）。U1 の読み取りの口だけを差し替え、
 * U2 の読み込みの口は本物を使う。
 */
class TargetSchemaDslGeneratorTest {

    private static final PatternChecker PATTERNS = GenerateTestSupport.newPatternChecker();

    private static final DslReader READER = GenerateTestSupport.newReader(PATTERNS);

    /** 写しの外にある値の代わり（生成した DSL・結果・ログに出ないことを確かめる）。 */
    private static final String SCHEMA_NAME = "schema_name_value_x9";

    @AfterAll
    static void close() {
        PATTERNS.close();
    }

    /** U2 の読み込みの口を包み、呼ばれた回数を数える。 */
    private static final class CountingReader implements DslReader {

        private final AtomicInteger reads = new AtomicInteger();

        @Override
        public DslReadResult read(byte[] yamlBytes) {
            reads.incrementAndGet();
            return READER.read(yamlBytes);
        }

        @Override
        public String hash(byte[] yamlBytes) {
            return READER.hash(yamlBytes);
        }
    }

    private final CountingReader dslReader = new CountingReader();

    private final AtomicReference<ReadPurpose> purpose = new AtomicReference<>();

    private TargetSchemaDslGenerator generator(TargetSchemaResult result) {
        return generator(result, new DslYamlWriter());
    }

    private TargetSchemaDslGenerator generator(TargetSchemaResult result, DslYamlWriter writer) {
        TargetSchemaReader schemaReader = readPurpose -> {
            purpose.set(readPurpose);
            return result;
        };
        return new TargetSchemaDslGenerator(schemaReader, dslReader, new DslTreeBuilder(), writer);
    }

    private static TargetSchema withName(TargetTable... tables) {
        return new TargetSchema(DatabaseProduct.POSTGRESQL, SCHEMA_NAME, List.of(tables));
    }

    @Test
    @DisplayName("an unconfigured target database gives TargetUnconfigured without generating anything")
    void unconfigured() {
        DefaultDslResult result = generator(TargetSchemaResult.unconfigured()).generate();

        assertThat(result).isEqualTo(new DefaultDslResult.TargetUnconfigured());
        assertThat(purpose.get()).isEqualTo(ReadPurpose.GENERATE);
        assertThat(dslReader.reads.get()).isZero();
    }

    @ParameterizedTest
    @EnumSource(UnavailableReason.class)
    @DisplayName("an unavailable target database gives TargetUnavailable with the same reason")
    void unavailable(UnavailableReason reason) {
        DefaultDslResult result =
                generator(TargetSchemaResult.unavailable(reason)).generate();

        assertThat(result).isEqualTo(new DefaultDslResult.TargetUnavailable(reason));
        assertThat(dslReader.reads.get()).isZero();
    }

    @Test
    @DisplayName("a schema is generated with the readSchema purpose GENERATE and the U2 hash of the same bytes")
    void generated() {
        TargetSchema schema = withName(table("emp", "社員", List.of("id"), List.of(), notNull("id", INT)));

        DefaultDslResult result = generator(TargetSchemaResult.success(schema)).generate();

        assertThat(purpose.get()).isEqualTo(ReadPurpose.GENERATE);
        assertThat(result).isInstanceOf(DefaultDslResult.Generated.class);
        DefaultDslResult.Generated generated = (DefaultDslResult.Generated) result;
        assertThat(generated.yamlBytes()).isEqualTo(GenerateTestSupport.generate(schema));
        assertThat(generated.dslHash()).isEqualTo(READER.hash(generated.yamlBytes()));
        assertThat(valid(READER, generated.yamlBytes()).dslHash()).isEqualTo(generated.dslHash());
        assertThat(dslReader.reads.get()).isEqualTo(1);
        assertThat(new String(generated.yamlBytes(), StandardCharsets.UTF_8)).doesNotContain(SCHEMA_NAME);
    }

    @Test
    @DisplayName("a schema without tables gives an empty DSL that passes the validation")
    void emptySchema() {
        DefaultDslResult result =
                generator(TargetSchemaResult.success(schema())).generate();

        DslModel model = valid(READER, ((DefaultDslResult.Generated) result).yamlBytes());
        assertThat(model.menus()).isEmpty();
        assertThat(model.tables()).isEmpty();
    }

    @Test
    @DisplayName("generation goes on with columns of unknown types")
    void unknownTypes() {
        TargetSchema schema = schema(table(
                "geo",
                null,
                List.of("id"),
                List.of(),
                notNull("id", INT),
                nullable("shape", new TargetDbType("geometry", null, null, null, "geometry")),
                nullable("doc", new TargetDbType("jsonb", null, null, null))));

        DefaultDslResult result = generator(TargetSchemaResult.success(schema)).generate();

        DslModel model = valid(READER, ((DefaultDslResult.Generated) result).yamlBytes());
        assertThat(model.tables().get("geo").columns().get("shape").formPart()).isEqualTo(FormPart.TEXT);
        assertThat(model.tables().get("geo").columns().get("doc").search().enabled())
                .isFalse();
    }

    @Test
    @DisplayName("a DSL over 10MB fails as unexpected without a partial DSL and without calling U2")
    void overTheSizeLimit() {
        List<TargetTable> tables = new ArrayList<>();
        for (int t = 0; t < 220; t++) {
            List<TargetColumn> columns = new ArrayList<>();
            for (int c = 0; c < 100; c++) {
                columns.add(notNull("column_%03d".formatted(c), VARCHAR_50));
            }
            tables.add(new TargetTable("table_%03d".formatted(t), false, null, columns, List.of(), List.of()));
        }
        TargetSchema schema = withName(tables.toArray(TargetTable[]::new));
        assertThat(GenerateTestSupport.generate(schema).length)
                .as("写しは上限を超える大きさ")
                .isGreaterThan(DslFormat.MAX_BYTES);

        assertThatThrownBy(() -> generator(TargetSchemaResult.success(schema)).generate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("table_")
                .hasMessageNotContaining(SCHEMA_NAME);
        assertThat(dslReader.reads.get()).as("U2 に大きな本文を渡さない").isZero();
    }

    @Test
    @DisplayName("a body that fails the U2 validation is an unexpected failure logging only kinds and counts")
    void invalidBody() {
        String secretValue = "secret_table_value_7q";
        DslYamlWriter broken = new DslYamlWriter() {
            @Override
            public byte[] write(Map<String, Object> tree) {
                return ("version: 1\nmenus:\n  - label: {ja: a, en: a}\n    table: " + secretValue + "\ntables: {}\n")
                        .getBytes(StandardCharsets.UTF_8);
            }
        };
        TargetSchema schema = withName(table("emp", null, List.of(), List.of(), notNull("id", INT)));

        try (LogEvents events = LogEvents.capture(TargetSchemaDslGenerator.class)) {
            assertThatThrownBy(() -> generator(TargetSchemaResult.success(schema), broken)
                            .generate())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageNotContaining(secretValue);

            ILoggingEvent warn = events.list().stream()
                    .filter(event -> event.getLevel() == Level.WARN)
                    .findFirst()
                    .orElseThrow();
            assertThat(warn.getKeyValuePairs()).extracting(pair -> pair.key).containsExactly("errorKinds", "errors");
            assertThat(warn.getKeyValuePairs().get(0).value).isEqualTo(List.of("SEMANTIC"));
            assertThat(warn.getKeyValuePairs().get(1).value).isEqualTo(1);
            assertThat(everything(events.list()))
                    .doesNotContain(secretValue)
                    .doesNotContain(SCHEMA_NAME)
                    .doesNotContain("emp");
        }
    }

    @Test
    @DisplayName("the breakdown of times is logged at DEBUG without the body, names or connection values")
    void breakdownLog() {
        Logger logger = (Logger) LoggerFactory.getLogger(TargetSchemaDslGenerator.class);
        Level original = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        try (LogEvents events = LogEvents.capture(TargetSchemaDslGenerator.class)) {
            TargetSchema schema = withName(table("employee_tbl", "社員の表", List.of(), List.of(), notNull("id", INT)));

            generator(TargetSchemaResult.success(schema)).generate();

            ILoggingEvent debug = events.list().stream()
                    .filter(event -> event.getLevel() == Level.DEBUG)
                    .findFirst()
                    .orElseThrow();
            assertThat(debug.getKeyValuePairs())
                    .extracting(pair -> pair.key)
                    .containsExactly("tables", "bytes", "readMillis", "buildMillis", "writeMillis", "validateMillis");
            assertThat(everything(events.list()))
                    .doesNotContain("employee_tbl")
                    .doesNotContain("社員の表")
                    .doesNotContain(SCHEMA_NAME);
        } finally {
            logger.setLevel(original);
        }
    }

    @Test
    @DisplayName("the generated result keeps its own copy of the bytes and never prints the body")
    void generatedValue() {
        byte[] body = "version: 1\n".getBytes(StandardCharsets.UTF_8);
        DefaultDslResult.Generated generated = new DefaultDslResult.Generated(body, "h");
        body[0] = 'X';
        generated.yamlBytes()[0] = 'Y';

        assertThat(generated.yamlBytes()[0]).isEqualTo((byte) 'v');
        assertThat(generated)
                .isEqualTo(new DefaultDslResult.Generated("version: 1\n".getBytes(StandardCharsets.UTF_8), "h"));
        assertThat(generated).isNotEqualTo(new DefaultDslResult.Generated(new byte[0], "h"));
        assertThat(generated).isNotEqualTo(new DefaultDslResult.Generated(generated.yamlBytes(), "other"));
        assertThat(generated).isNotEqualTo("h");
        assertThat(generated.hashCode())
                .isEqualTo(new DefaultDslResult.Generated(generated.yamlBytes(), "h").hashCode());
        assertThat(generated.toString()).doesNotContain("version").contains("bytes=11");
        assertThatThrownBy(() -> new DefaultDslResult.Generated(null, "h")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DefaultDslResult.Generated(new byte[0], null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DefaultDslResult.TargetUnavailable(null)).isInstanceOf(NullPointerException.class);
    }

    /** ログの出来事の文・キーと値をすべてつないだ文字列。 */
    private static String everything(List<ILoggingEvent> events) {
        StringBuilder all = new StringBuilder();
        for (ILoggingEvent event : events) {
            all.append(event.getFormattedMessage()).append('\n');
            if (event.getKeyValuePairs() != null) {
                event.getKeyValuePairs()
                        .forEach(pair -> all.append(pair.key)
                                .append('=')
                                .append(pair.value)
                                .append('\n'));
            }
        }
        return all.toString();
    }
}
