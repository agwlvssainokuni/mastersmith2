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

import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 検証を通った DSL を解釈した、変更できない値の木（契約 C4・C8 の {@code DslModel}、BR5.2）。後続の Intent I・J・K が使う。
 *
 * <p>接続先・権限を持たない。取り出した側が持ち続けても、適用中のモデルの差し替えの影響を受けない（BR5.3）。
 *
 * <p>書式の版 2（U2 dsl-v2、契約 C3）では、テーブルをスキーマの下にだけ置き、版 1 の平らな {@code tables} は持たない。テーブルは
 * スキーマ名とテーブル名の組で引く（{@link #findTable(TableRef)}、BR8.2）。スキーマは検証でちょうど1つに絞る（BR1.3）。
 *
 * @param dslHash 本文のバイト列の識別（SHA-256 を 16 進数の小文字 64 文字にしたもの。BR5.1）
 * @param formatVersion 書式の版
 * @param schemas スキーマ（DSL の順。名前は重ならない）
 * @param menus 最上位のメニュー（0件以上。DSL の順）
 */
public record DslModel(String dslHash, int formatVersion, List<DslSchema> schemas, List<DslMenuItem> menus) {

    /** 識別の形と版を確かめ、一覧を変更できないものにし、スキーマ名が重ならないことを確かめる。 */
    public DslModel {
        requireHash(dslHash);
        if (formatVersion != DslFormat.CURRENT_VERSION) {
            throw new IllegalArgumentException("formatVersion が対応する版ではありません");
        }
        schemas = ModelValues.copyList(schemas, "schemas");
        menus = ModelValues.copyList(menus, "menus");
        Set<String> names = new HashSet<>();
        for (DslSchema schema : schemas) {
            if (!names.add(schema.name())) {
                throw new IllegalArgumentException("schemas の名前が重なっています");
            }
        }
    }

    /**
     * スキーマ名とテーブル名の組でテーブルを引く（名前は大文字・小文字を区別する。BR8.2）。
     *
     * @param ref テーブルの組
     * @return テーブル（無ければ空）
     */
    public Optional<DslTable> findTable(TableRef ref) {
        Objects.requireNonNull(ref, "ref は必須です");
        return schemas.stream()
                .filter(schema -> schema.name().equals(ref.schema()))
                .findFirst()
                .map(schema -> schema.tables().get(ref.name()));
    }

    /**
     * ただ1つのスキーマを返す（検証を通ったモデルはスキーマをちょうど1つ持つ。BR1.3）。
     *
     * @return スキーマ
     * @throws IllegalStateException スキーマがちょうど1つでないとき（プログラムの誤り）
     */
    public DslSchema schema() {
        if (schemas.size() != 1) {
            throw new IllegalStateException("スキーマがちょうど1つではありません");
        }
        return schemas.getFirst();
    }

    private static void requireHash(String dslHash) {
        if (dslHash == null
                || dslHash.length() != 64
                || !dslHash.chars().allMatch(c -> HexFormat.isHexDigit(c) && !Character.isUpperCase(c))) {
            throw new IllegalArgumentException("dslHash は 16 進数の小文字 64 文字です");
        }
    }
}
