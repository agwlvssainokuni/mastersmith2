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
package cherry.mastersmith.invitation.domain;

import cherry.mastersmith.user.domain.EmailAddress;
import cherry.mastersmith.user.domain.FieldError;
import cherry.mastersmith.user.domain.FieldErrorReason;
import cherry.mastersmith.user.domain.Language;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 招待の要求の入力の検証（BR1.1・BR1.2、NFR9.12、{@code security-design.md} 6節）。DB を使わない純粋な関数。
 *
 * <ul>
 *   <li>{@code email}: 値が無い（null・空）は {@link FieldErrorReason#REQUIRED}、正規化の前に CR か LF を1つでも含めば
 *       {@link FieldErrorReason#INVALID_CHARACTER}（前後の改行が正規化で除かれて通るのを防ぐ）、正規化の後が空は
 *       {@link FieldErrorReason#REQUIRED}、254 文字を超えれば {@link FieldErrorReason#TOO_LONG}、形式に合わなければ
 *       {@link FieldErrorReason#INVALID_VALUE}
 *   <li>{@code language}: {@code ja}・{@code en} の完全な一致（{@link FieldErrorReason#REQUIRED}・{@link FieldErrorReason#INVALID_VALUE}）
 * </ul>
 *
 * <p>誤りは email・language の順にすべて集め、1つの項目には最初に当たった理由だけを載せる。入れた値は載せない。
 */
public final class InvitationRequestValidation {

    /** メールアドレスの項目の名前（契約 C5）。 */
    public static final String EMAIL = "email";

    /** 言語の項目の名前（契約 C5）。 */
    public static final String LANGUAGE = "language";

    private InvitationRequestValidation() {}

    /**
     * 2つを検証する。
     *
     * @param email メールアドレス（正規化の前の値。null でもよい）
     * @param language 言語（null でもよい）
     * @return 項目ごとの誤り（誤りが無ければ空）
     */
    public static List<FieldError> validate(String email, String language) {
        List<FieldError> errors = new ArrayList<>();
        checkEmail(email).ifPresent(reason -> errors.add(new FieldError(EMAIL, reason)));
        Language.check(language).ifPresent(reason -> errors.add(new FieldError(LANGUAGE, reason)));
        return List.copyOf(errors);
    }

    /**
     * メールアドレスが決まりに合うかを判定する。
     *
     * @param raw 入力された値（正規化の前。null でもよい）
     * @return 合わなければ理由（合えば空）
     */
    public static Optional<FieldErrorReason> checkEmail(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Optional.of(FieldErrorReason.REQUIRED);
        }
        if (raw.indexOf('\r') >= 0 || raw.indexOf('\n') >= 0) {
            return Optional.of(FieldErrorReason.INVALID_CHARACTER);
        }
        String normalized = EmailAddress.normalize(raw);
        if (normalized.isEmpty()) {
            return Optional.of(FieldErrorReason.REQUIRED);
        }
        if (normalized.length() > EmailAddress.MAX_LENGTH) {
            return Optional.of(FieldErrorReason.TOO_LONG);
        }
        if (!EmailAddress.isValid(normalized)) {
            return Optional.of(FieldErrorReason.INVALID_VALUE);
        }
        return Optional.empty();
    }

    /**
     * 検証を通ったメールアドレスを正規化した値にする。
     *
     * @param raw 入力された値
     * @return 正規化した値
     * @throws IllegalArgumentException 決まりに合わないとき（先に {@link #validate} で確かめる）
     */
    public static InvitationEmail toEmail(String raw) {
        if (checkEmail(raw).isPresent()) {
            throw new IllegalArgumentException("招待先のメールアドレスが決まりに合いません");
        }
        return new InvitationEmail(EmailAddress.normalize(raw));
    }

    /**
     * 検証を通った言語を値にする。
     *
     * @param raw 入力された値
     * @return 言語
     * @throws IllegalArgumentException 決まりに合わないとき（先に {@link #validate} で確かめる）
     */
    public static Language toLanguage(String raw) {
        return Language.fromValue(raw).orElseThrow(() -> new IllegalArgumentException("招待の言語が決まりに合いません"));
    }
}
