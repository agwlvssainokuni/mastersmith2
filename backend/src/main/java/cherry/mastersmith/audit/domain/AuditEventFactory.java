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
package cherry.mastersmith.audit.domain;

import cherry.mastersmith.access.domain.AccessDeniedReason;
import cherry.mastersmith.access.domain.AccessEventType;
import cherry.mastersmith.access.domain.AccessResult;
import cherry.mastersmith.access.domain.AdminAccessDeniedEvent;
import cherry.mastersmith.auth.domain.AuthenticationEvent;
import cherry.mastersmith.auth.domain.AuthenticationEventType;
import cherry.mastersmith.auth.domain.LoginFailureReason;

/**
 * 受け取った出来事から監査イベントを作る（BR1.1、BR1.2、BR1.5、BR2.1）。DB にも時計にも触れない純粋な関数である。
 *
 * <p>種類から結果を決め（BR1.2）、日時は出来事の日時をそのまま使う（BR1.5）。攻撃者が決められる値（メールアドレス・
 * User-Agent・要求のパス）は {@link AuditText} で切り詰める。要求のパスはアクセスの拒否のときだけ記録し、認証の出来事では
 * 空にする（{@code nfr-design/security-design.md} 1章・6章）。
 *
 * <p>U2 の出来事の利用者IDは、監査ログの記録項目に含まれないため記録しない（{@code functional-design/entities.md}）。
 * U3 の出来事のメールアドレスは、文字列化が伏せ字になるため必ず {@link AdminAccessDeniedEvent#enteredEmail()} で取る。
 *
 * <p>種類と理由の写し取りは網羅の {@code switch} で書く。U2・U3 が値を増やしたときに、コンパイルで気づけるようにするため。
 */
public final class AuditEventFactory {

    private AuditEventFactory() {}

    /**
     * U2 の認証の出来事から監査イベントを作る。
     *
     * @param event 認証の出来事
     * @return 監査イベント
     */
    public static AuditEvent from(AuthenticationEvent event) {
        AuditEventType eventType = eventTypeOf(event.eventType());
        return new AuditEvent(
                event.occurredAt(),
                eventType,
                resultOf(eventType),
                AuditText.enteredEmail(event.enteredEmail()),
                failureReasonOf(event.failureReason()),
                event.sourceIp(),
                AuditText.userAgent(event.userAgent()),
                null,
                event.traceId());
    }

    /**
     * U3 のアクセス拒否の出来事から監査イベントを作る。
     *
     * @param event アクセス拒否の出来事
     * @return 監査イベント
     */
    public static AuditEvent from(AdminAccessDeniedEvent event) {
        return new AuditEvent(
                event.occurredAt(),
                eventTypeOf(event.eventType()),
                resultOf(event.result()),
                AuditText.enteredEmail(event.enteredEmail()),
                failureReasonOf(event.failureReason()),
                event.sourceIp(),
                AuditText.userAgent(event.userAgent()),
                AuditText.requestPath(event.requestPath()),
                event.traceId());
    }

    private static AuditEventType eventTypeOf(AuthenticationEventType eventType) {
        return switch (eventType) {
            case LOGIN_SUCCEEDED -> AuditEventType.LOGIN_SUCCEEDED;
            case LOGIN_FAILED -> AuditEventType.LOGIN_FAILED;
            case LOGGED_OUT -> AuditEventType.LOGGED_OUT;
        };
    }

    private static AuditEventType eventTypeOf(AccessEventType eventType) {
        return switch (eventType) {
            case ACCESS_DENIED -> AuditEventType.ACCESS_DENIED;
        };
    }

    /**
     * 監査イベントの種類から結果を決める（BR1.2）。
     *
     * @param eventType 種類
     * @return 結果
     */
    public static AuditResult resultOf(AuditEventType eventType) {
        return switch (eventType) {
            case LOGIN_SUCCEEDED, LOGGED_OUT -> AuditResult.SUCCESS;
            case LOGIN_FAILED, ACCESS_DENIED -> AuditResult.FAILURE;
        };
    }

    private static AuditResult resultOf(AccessResult result) {
        return switch (result) {
            case FAILURE -> AuditResult.FAILURE;
        };
    }

    private static AuditFailureReason failureReasonOf(LoginFailureReason reason) {
        if (reason == null) {
            return null;
        }
        return switch (reason) {
            case USER_NOT_FOUND -> AuditFailureReason.USER_NOT_FOUND;
            case PASSWORD_MISMATCH -> AuditFailureReason.PASSWORD_MISMATCH;
            case ACCOUNT_LOCKED -> AuditFailureReason.ACCOUNT_LOCKED;
        };
    }

    private static AuditFailureReason failureReasonOf(AccessDeniedReason reason) {
        if (reason == null) {
            return null;
        }
        return switch (reason) {
            case NOT_ADMIN -> AuditFailureReason.NOT_ADMIN;
            case TOKEN_MISSING -> AuditFailureReason.TOKEN_MISSING;
            case TOKEN_MALFORMED -> AuditFailureReason.TOKEN_MALFORMED;
            case TOKEN_INVALID -> AuditFailureReason.TOKEN_INVALID;
            case USER_NOT_FOUND -> AuditFailureReason.USER_NOT_FOUND;
        };
    }
}
