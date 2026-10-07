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
package cherry.mastersmith.dsl.domain;

import java.util.Map;
import java.util.Objects;

/**
 * スキーマ1つ（書式の版 2、契約 C3 の {@code DslSchema}、U2 dsl-v2 の entities.md の DslSchemaV2）。権限の対象の最上位の階層。
 *
 * <p>契約 C3 の {@code displayName: string} との差: 表示名はテーブル・カラムと同じ ja・en の {@link DisplayName} にした
 * （functional-spec.md の 9節）。
 *
 * @param name スキーマ名（1文字以上）
 * @param label 表示名
 * @param tables テーブル（キーは物理名で、このスキーマの中で一意。DSL の順を保つ。0 件を許す）
 */
public record DslSchema(String name, DisplayName label, Map<String, DslTable> tables) {

    /** 必須の値を確かめ、対応表を変更できないもの（DSL の順を保つ）にする。 */
    public DslSchema {
        ModelValues.requireName(name, "schema.name");
        Objects.requireNonNull(label, "schema.label は必須です");
        tables = ModelValues.copyNamedMap(tables, DslTable::name, "schema.tables");
    }
}
