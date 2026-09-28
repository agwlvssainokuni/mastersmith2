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

import cherry.mastersmith.common.error.domain.LocalizedText;
import cherry.mastersmith.common.error.domain.ProblemType;
import java.util.List;

/**
 * Invitation の問題の種類（BR9.3、NFR8.1、契約のエラーの code の一覧）。1つの code に1つの状態コードを固定する。
 *
 * <p>入力の誤りは既存の {@code VALIDATION_FAILED}（400）、未認証は既存の {@code AUTHENTICATION_REQUIRED}（401）、管理者でないは既存の
 * {@code ACCESS_DENIED}（403）を使うため、ここには置かない。説明文にメールアドレス・トークン・内部の例外の文言を入れない。
 */
public final class InvitationProblemTypes {

    /** 招待しようとしたメールアドレスの利用者がすでにいる（409。BR2.1）。 */
    public static final ProblemType INVITATION_EMAIL_REGISTERED = new ProblemType(
            "INVITATION_EMAIL_REGISTERED",
            409,
            new LocalizedText("登録済みのメールアドレスです", "Email address already registered"),
            new LocalizedText(
                    "このメールアドレスの利用者はすでに登録されているため、招待できませんでした。",
                    "The invitation could not be created because a user with this email address is already registered."),
            new LocalizedText("メールアドレスを確かめてください。", "Check the email address."));

    /** 同じメールアドレスの期限内の招待中がある（409。追加の項目 invitationId・page。BR2.2）。 */
    public static final ProblemType INVITATION_ALREADY_PENDING = new ProblemType(
            "INVITATION_ALREADY_PENDING",
            409,
            new LocalizedText("招待中のメールアドレスです", "Invitation already pending"),
            new LocalizedText(
                    "このメールアドレスへの有効な招待がすでにあるため、新しく招待できませんでした。",
                    "A new invitation could not be created because a valid invitation to this email address already exists."),
            new LocalizedText(
                    "招待の一覧で、その招待を送り直すか取り消してください。", "Resend or cancel that invitation from the invitation list."));

    /** 招待を使える設定でない（503。追加の項目 unavailableReasons。BR1.5）。 */
    public static final ProblemType INVITATION_NOT_CONFIGURED = new ProblemType(
            "INVITATION_NOT_CONFIGURED",
            503,
            new LocalizedText("招待を使えない設定です", "Invitations are not configured"),
            new LocalizedText(
                    "招待のリンクのベースURL かメールの送信の設定が無いため、招待できませんでした。",
                    "The invitation could not be sent because the base URL for invitation links or the mail settings are missing."),
            new LocalizedText("管理者に設定を頼んでください。", "Ask the administrator to configure the settings."));

    /** 送り直し・取り消しの対象が無い・招待中でない（404。BR6.3）。 */
    public static final ProblemType INVITATION_NOT_FOUND = new ProblemType(
            "INVITATION_NOT_FOUND",
            404,
            new LocalizedText("招待が見つかりません", "Invitation not found"),
            new LocalizedText(
                    "指定した招待は無いか、すでに完了・取り消しされています。",
                    "The invitation does not exist or has already been completed or cancelled."),
            new LocalizedText("招待の一覧を読み直してください。", "Reload the invitation list."));

    /** 招待のリンクが使えない（404。理由によらず同じ。BR7.5）。 */
    public static final ProblemType REGISTRATION_LINK_INVALID = new ProblemType(
            "REGISTRATION_LINK_INVALID",
            404,
            new LocalizedText("このリンクは使えません", "This link cannot be used"),
            new LocalizedText(
                    "招待のリンクが無効か、有効期限が切れているか、すでに使われています。",
                    "The invitation link is invalid, has expired, or has already been used."),
            new LocalizedText(
                    "招待した管理者に、招待を送り直してもらってください。", "Ask the administrator who invited you to resend the invitation."));

    private InvitationProblemTypes() {}

    /**
     * Invitation の問題の種類をすべて返す。
     *
     * @return 問題の種類の一覧
     */
    public static List<ProblemType> all() {
        return List.of(
                INVITATION_EMAIL_REGISTERED,
                INVITATION_ALREADY_PENDING,
                INVITATION_NOT_CONFIGURED,
                INVITATION_NOT_FOUND,
                REGISTRATION_LINK_INVALID);
    }
}
