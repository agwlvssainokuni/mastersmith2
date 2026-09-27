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
package cherry.mastersmith.mail.domain;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 送信の依頼の確かめ（BR3.1〜BR3.4、functional-spec.md の 4節の手順 2）。Bean ではない純粋な関数。
 *
 * <p>次の順に確かめ、最初に当たったもので止める: 言語 → templateId（一覧に有るか）→ 宛先・差し込む値の改行 → 宛先の形 →
 * 差し込みの名前の完全一致と値（null・空・空白だけ）。一覧の引き当ては呼び出し側（{@code mail.service}）が行い、ここには
 * 「一覧に有るか」と「期待する差し込みの名前の集合」を引数で渡す（{@code mail.domain} を {@code mail.template} に依存させない）。
 */
public final class MailRequestValidation {

    /** 受け付ける言語（BR3.4）。 */
    public static final Set<String> SUPPORTED_LANGUAGES = Set.of("ja", "en");

    private MailRequestValidation() {}

    /**
     * 依頼を確かめる。
     *
     * @param request 送信の依頼
     * @param templateKnown templateId がテンプレートの一覧に有るか
     * @param expectedVariableNames 一覧の差し込みの名前の集合（一覧に無いときは使わない）
     * @return 確かめを通れば空、通らなければ失敗の種類（INVALID_INPUT または TEMPLATE_ERROR）
     */
    public static Optional<MailFailureKind> validate(
            MailRequest request, boolean templateKnown, Set<String> expectedVariableNames) {
        if (request.language() == null || !SUPPORTED_LANGUAGES.contains(request.language())) {
            return Optional.of(MailFailureKind.INVALID_INPUT);
        }
        if (!templateKnown) {
            return Optional.of(MailFailureKind.TEMPLATE_ERROR);
        }
        if (containsLineBreak(request.to())
                || request.variables().values().stream().anyMatch(MailRequestValidation::containsLineBreak)) {
            return Optional.of(MailFailureKind.INVALID_INPUT);
        }
        if (!MailAddressRule.isValid(request.to())) {
            return Optional.of(MailFailureKind.INVALID_INPUT);
        }
        if (!variablesMatch(request.variables(), expectedVariableNames)) {
            return Optional.of(MailFailureKind.INVALID_INPUT);
        }
        return Optional.empty();
    }

    /**
     * CR か LF を含むかを返す（BR3.2）。null は含まないとみなす（null は別の確かめで拒否する）。
     *
     * @param value 値
     * @return CR か LF を含めば true
     */
    static boolean containsLineBreak(String value) {
        return value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0);
    }

    /**
     * 差し込みの名前の集合が一覧と一致し、どの値も null・空・空白だけでないかを返す（BR3.3）。空白の判定は
     * {@link String#strip()} と同じ（{@link Character#isWhitespace(int)}。半角の空白・タブ・全角の空白を含む）。
     */
    private static boolean variablesMatch(Map<String, String> variables, Set<String> expectedNames) {
        if (!variables.keySet().equals(expectedNames)) {
            return false;
        }
        return variables.values().stream().allMatch(value -> value != null && !value.isBlank());
    }
}
