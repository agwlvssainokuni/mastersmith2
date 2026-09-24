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

import cherry.mastersmith.dsl.domain.DslError;
import cherry.mastersmith.dsl.domain.DslErrorKind;
import cherry.mastersmith.dsl.domain.DslFormat;
import cherry.mastersmith.dsl.domain.DslReadResult;
import cherry.mastersmith.dsl.service.DslReader;
import cherry.mastersmith.targetdb.domain.ReadPurpose;
import cherry.mastersmith.targetdb.domain.TargetSchema;
import cherry.mastersmith.targetdb.domain.TargetSchemaResult;
import cherry.mastersmith.targetdb.service.TargetSchemaReader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * {@link DefaultDslGenerator} の実装（functional-spec.md の 1節、BR1.1・BR5.1〜BR5.3、NFR2.5・NFR1.7・NFR4.9、NFR 設計
 * security-design.md の 1節）。
 *
 * <p>U1 の読み取り → 組み立て → 書き出し → 大きさの確かめ → U2 の検証 の順に呼ぶ。内訳の時間（ミリ秒）を DEBUG のログに出し、
 * Build and Test で内訳ごとに測れるようにする。ログには本文・写しの値・接続先を出さない（件数と時間と誤りの種類だけ）。
 */
@Service
public class TargetSchemaDslGenerator implements DefaultDslGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(TargetSchemaDslGenerator.class);

    private final TargetSchemaReader schemaReader;

    private final DslReader dslReader;

    private final DslTreeBuilder treeBuilder;

    private final DslYamlWriter yamlWriter;

    /**
     * 作る。
     *
     * @param schemaReader 対象DB のスキーマの読み取りの口（U1）
     * @param dslReader DSL の読み込みの口（U2）
     * @param treeBuilder DSL の値の木の組み立て
     * @param yamlWriter YAML の書き出し
     */
    public TargetSchemaDslGenerator(
            TargetSchemaReader schemaReader,
            DslReader dslReader,
            DslTreeBuilder treeBuilder,
            DslYamlWriter yamlWriter) {
        this.schemaReader = schemaReader;
        this.dslReader = dslReader;
        this.treeBuilder = treeBuilder;
        this.yamlWriter = yamlWriter;
    }

    @Override
    public DefaultDslResult generate() {
        long started = System.nanoTime();
        TargetSchema schema;
        switch (schemaReader.readSchema(ReadPurpose.GENERATE)) {
            case TargetSchemaResult.Unconfigured ignored -> {
                return new DefaultDslResult.TargetUnconfigured();
            }
            case TargetSchemaResult.Unavailable unavailable -> {
                return new DefaultDslResult.TargetUnavailable(unavailable.reason());
            }
            case TargetSchemaResult.Success success -> schema = success.schema();
        }
        long read = System.nanoTime();
        Map<String, Object> tree = treeBuilder.build(schema);
        long built = System.nanoTime();
        byte[] yaml = yamlWriter.write(tree);
        long written = System.nanoTime();
        if (yaml.length > DslFormat.MAX_BYTES) {
            // U2 に大きな本文を渡す前に打ち切る（NFR2.5。正の確かめは U2 の大きさの上限）。ログは応答に変える境界で1回だけ出す。
            throw new IllegalStateException("生成した既定の DSL が大きさの上限を超えました");
        }
        DslReadResult result = dslReader.read(yaml);
        long validated = System.nanoTime();
        LOGGER.atDebug()
                .addKeyValue("tables", schema.tables().size())
                .addKeyValue("bytes", yaml.length)
                .addKeyValue("readMillis", millis(started, read))
                .addKeyValue("buildMillis", millis(read, built))
                .addKeyValue("writeMillis", millis(built, written))
                .addKeyValue("validateMillis", millis(written, validated))
                .log("既定の DSL の生成の内訳");
        return switch (result) {
            case DslReadResult.Valid valid -> new DefaultDslResult.Generated(yaml, valid.dslHash());
            case DslReadResult.Invalid invalid -> throw invalidGenerated(invalid.errors());
        };
    }

    /** 生成した DSL が U2 の検証を通らない（作りの誤り。BR5.2）。誤りの種類と件数だけをログに出す。 */
    private static IllegalStateException invalidGenerated(List<DslError> errors) {
        List<String> kinds = errors.stream()
                .map(DslError::kind)
                .distinct()
                .map(DslErrorKind::name)
                .sorted()
                .toList();
        LOGGER.atWarn()
                .addKeyValue("errorKinds", kinds)
                .addKeyValue("errors", errors.size())
                .log("生成した既定の DSL が U2 の検証を通りませんでした");
        return new IllegalStateException("生成した既定の DSL が U2 の検証を通りませんでした");
    }

    private static long millis(long from, long to) {
        return TimeUnit.NANOSECONDS.toMillis(to - from);
    }
}
