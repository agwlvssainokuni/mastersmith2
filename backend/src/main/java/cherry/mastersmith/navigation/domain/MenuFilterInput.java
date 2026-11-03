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
package cherry.mastersmith.navigation.domain;

import cherry.mastersmith.role.domain.MainPermission;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

/**
 * 絞る関数 {@link MenuFilter} の入力の操作（{@code entities.md} の ENT-009）。
 *
 * @param tableMain テーブルの組（スキーマの名前, テーブルの名前）から、テーブルの階層の実効の主権限を返す操作（本番は写しの
 *     {@code main(schema, table, null)}。DSL に無い組は NONE。null を返さない）
 * @param iconOf DSL の {@code icon}（null を含む）から、許した名前を返す操作（BR4.2）
 * @param tableKindOf テーブルの組（スキーマの名前, テーブルの名前）から、テーブルの種類を返す操作（種類が分からないときは null を返してよい。
 *     {@link MenuFilter} は移れる項目を作るときだけ呼ぶ）
 */
public record MenuFilterInput(
        BiFunction<String, String, MainPermission> tableMain,
        UnaryOperator<String> iconOf,
        BiFunction<String, String, NavTableKind> tableKindOf) {

    /**
     * 種類の操作を持たない作り方（種類は常に null）。
     *
     * @param tableMain 主権限の操作
     * @param iconOf アイコンの操作
     */
    public MenuFilterInput(BiFunction<String, String, MainPermission> tableMain, UnaryOperator<String> iconOf) {
        this(tableMain, iconOf, (schema, table) -> null);
    }

    /** 3つとも必須であることを確かめる。 */
    public MenuFilterInput {
        Objects.requireNonNull(tableMain, "tableMain は必須です");
        Objects.requireNonNull(iconOf, "iconOf は必須です");
        Objects.requireNonNull(tableKindOf, "tableKindOf は必須です");
    }
}
