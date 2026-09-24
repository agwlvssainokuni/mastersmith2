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
package cherry.mastersmith.dslmanage.domain;

import java.util.Objects;

/**
 * ダウンロードする DSL（BR6.2）。本文は保存したバイト列のまま。
 *
 * @param fileName ファイル名（種類と識別の先頭12文字が分かる名前）
 * @param content 本文と参照
 */
public record DslDownload(String fileName, DslContent<?> content) {

    /** 識別の先頭の文字数。 */
    public static final int HASH_PREFIX_LENGTH = 12;

    /** 必須の値を確かめる。 */
    public DslDownload {
        Objects.requireNonNull(fileName, "fileName は必須です");
        Objects.requireNonNull(content, "content は必須です");
    }

    /**
     * プレビュー中の DSL のダウンロードを作る（ファイル名は {@code dsl-preview-<識別の先頭12文字>.yaml}）。
     *
     * @param content プレビューの本文と参照
     * @return ダウンロード
     */
    public static DslDownload preview(DslContent<DslPreviewRef> content) {
        return new DslDownload("dsl-preview-" + prefix(content.ref().dslHash()) + ".yaml", content);
    }

    /**
     * 識別の先頭12文字を返す（ログ・ファイル名に使う）。
     *
     * @param dslHash 識別（無ければ null）
     * @return 先頭12文字（無ければ null）
     */
    public static String prefix(String dslHash) {
        if (dslHash == null) {
            return null;
        }
        return dslHash.length() <= HASH_PREFIX_LENGTH ? dslHash : dslHash.substring(0, HASH_PREFIX_LENGTH);
    }
}
