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

import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 検証を通った DSL を解釈した、変更できない値の木（契約 C4・C8 の {@code DslModel}、BR5.2）。後続の Intent I・J・K が使う。
 *
 * <p>接続先・権限を持たない。取り出した側が持ち続けても、適用中のモデルの差し替えの影響を受けない（BR5.3）。
 *
 * @param dslHash 本文のバイト列の識別（SHA-256 を 16 進数の小文字 64 文字にしたもの。BR5.1）
 * @param formatVersion 書式の版
 * @param menus 最上位のメニュー（0件以上。DSL の順）
 * @param tables テーブル（キーは物理名。DSL の順を保つ）
 */
public record DslModel(String dslHash, int formatVersion, List<DslMenuItem> menus, Map<String, DslTable> tables) {

    /** 識別の形と版を確かめ、一覧と対応表を変更できないもの（対応表は DSL の順を保つ）にする。 */
    public DslModel {
        requireHash(dslHash);
        if (formatVersion != DslFormat.CURRENT_VERSION) {
            throw new IllegalArgumentException("formatVersion が対応する版ではありません");
        }
        menus = ModelValues.copyList(menus, "menus");
        tables = ModelValues.copyNamedMap(tables, DslTable::name, "tables");
    }

    private static void requireHash(String dslHash) {
        if (dslHash == null
                || dslHash.length() != 64
                || !dslHash.chars().allMatch(c -> HexFormat.isHexDigit(c) && !Character.isUpperCase(c))) {
            throw new IllegalArgumentException("dslHash は 16 進数の小文字 64 文字です");
        }
    }
}
