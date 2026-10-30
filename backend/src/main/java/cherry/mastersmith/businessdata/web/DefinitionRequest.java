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
package cherry.mastersmith.businessdata.web;

import cherry.mastersmith.businessdata.domain.DefinitionPurpose;
import java.util.Arrays;
import java.util.Optional;

/**
 * 定義の API の本文（{@code POST /api/tables/definition}）。
 *
 * @param schema スキーマ名
 * @param table テーブル名
 * @param definitionId 画面が持っている定義の識別（任意）
 * @param purpose 用途（任意。{@code VIEW}・{@code CREATE}・{@code UPDATE}。無ければ {@code VIEW}。Intent
 *     261029-reference-lookup-cleanup の U1、BR1.1・BR12.1）
 */
public record DefinitionRequest(String schema, String table, DefinitionIdBody definitionId, String purpose) {

    /**
     * 用途を指定しない本文を作る（今の画面と同じ形）。
     *
     * @param schema スキーマ名
     * @param table テーブル名
     * @param definitionId 画面が持っている定義の識別（任意）
     */
    public DefinitionRequest(String schema, String table, DefinitionIdBody definitionId) {
        this(schema, table, definitionId, null);
    }

    /**
     * 用途を読む（文字どおり一致するときだけ。大文字と小文字を区別する）。
     *
     * @return 用途（無ければ {@code VIEW}。列挙に無い値は空）
     */
    Optional<DefinitionPurpose> toPurpose() {
        if (purpose == null) {
            return Optional.of(DefinitionPurpose.VIEW);
        }
        return Arrays.stream(DefinitionPurpose.values())
                .filter(p -> p.name().equals(purpose))
                .findFirst();
    }
}
