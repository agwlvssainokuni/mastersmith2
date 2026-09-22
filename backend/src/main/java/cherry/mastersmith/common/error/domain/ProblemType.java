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
package cherry.mastersmith.common.error.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 問題の種類。エラー応答の code と type の元であり、type の URL を開いたときに返す説明の内容（BR5.2、BR5.9、BR5.14）。
 *
 * <p>各機能は自分の場所に {@link ProblemTypeCatalog} の Bean として定義を置き、U1 が起動時にすべて集める（BR5.16）。
 *
 * @param code 画面が分岐に使う安定した値。大文字とアンダースコアだけ（{@code ^[A-Z][A-Z0-9_]*$}）。一度決めたら変えない
 * @param status 状態コード（400〜599）
 * @param title 名前（日英）
 * @param description どんなときに起きるか（日英）。個々の要求の内容は含めない
 * @param resolution 利用者がすべきこと（日英）。無ければ null
 */
public record ProblemType(
        String code, int status, LocalizedText title, LocalizedText description, LocalizedText resolution) {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    /** 値の決まりを確かめる。 */
    public ProblemType {
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("code の形が正しくありません: " + code);
        }
        if (status < 400 || status > 599) {
            throw new IllegalArgumentException("状態コードは 400〜599 にしてください: " + status);
        }
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
    }

    /**
     * 説明ページの URL に使う slug（code を小文字にし、アンダースコアをハイフンにしたもの）を返す。
     *
     * @return slug（例: {@code VALIDATION_FAILED} → {@code validation-failed}）
     */
    public String slug() {
        return toSlug(code);
    }

    /**
     * code から slug を導く。
     *
     * @param code 問題の種類の code
     * @return slug
     */
    public static String toSlug(String code) {
        return code.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /**
     * slug から code に戻す（{@link #toSlug(String)} の逆）。
     *
     * @param slug slug
     * @return code
     */
    public static String toCode(String slug) {
        return slug.toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
