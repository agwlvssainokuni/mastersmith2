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

/**
 * 攻撃者が決められる値（メールアドレス・User-Agent・要求のパス）の切り詰め（BR2.1、NFR3.4）。
 *
 * <p>長さは文字（Unicode のコードポイント）単位で数え、上限を超える分を捨てる。サロゲートペア（絵文字など）を分断しないため、
 * 切る位置は必ず文字の境界にそろえる。切り詰めのほかの加工（前後の空白の除去、制御文字の置き換えなど）はしない（BR2.2）。
 */
public final class AuditText {

    /** 入力されたメールアドレスの長さの上限（コードポイントで数える）。 */
    public static final int MAX_ENTERED_EMAIL_LENGTH = 254;

    /** User-Agent の長さの上限（コードポイントで数える）。 */
    public static final int MAX_USER_AGENT_LENGTH = 512;

    /** 要求のパスの長さの上限（コードポイントで数える）。 */
    public static final int MAX_REQUEST_PATH_LENGTH = 512;

    private AuditText() {}

    /**
     * 値を上限のコードポイント数まで切り詰める。
     *
     * @param value 値（null でもよい）
     * @param maxCodePoints 上限のコードポイント数（1 以上）
     * @return 切り詰めた値（null は null のまま）
     */
    public static String truncate(String value, int maxCodePoints) {
        if (maxCodePoints < 1) {
            throw new IllegalArgumentException("maxCodePoints は 1 以上である必要があります: " + maxCodePoints);
        }
        if (value == null) {
            return null;
        }
        int length = value.length();
        // よくある短い値は、コードポイントを数えずにそのまま返す（1 文字は最大 2 の char で表される）。
        if (length <= maxCodePoints) {
            return value;
        }
        if (value.codePointCount(0, length) <= maxCodePoints) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
    }

    /**
     * 入力されたメールアドレスを上限まで切り詰める。
     *
     * @param enteredEmail 入力されたメールアドレス（null でもよい）
     * @return 切り詰めた値
     */
    public static String enteredEmail(String enteredEmail) {
        return truncate(enteredEmail, MAX_ENTERED_EMAIL_LENGTH);
    }

    /**
     * User-Agent を上限まで切り詰める。
     *
     * @param userAgent User-Agent（null でもよい）
     * @return 切り詰めた値
     */
    public static String userAgent(String userAgent) {
        return truncate(userAgent, MAX_USER_AGENT_LENGTH);
    }

    /**
     * 要求のパスを上限まで切り詰める。
     *
     * @param requestPath 要求のパス（null でもよい）
     * @return 切り詰めた値
     */
    public static String requestPath(String requestPath) {
        return truncate(requestPath, MAX_REQUEST_PATH_LENGTH);
    }
}
