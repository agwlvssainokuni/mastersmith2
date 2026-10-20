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
package cherry.mastersmith.businessdata.testsupport;

import java.util.regex.Pattern;

/**
 * 応答の本文とログの、実行ごとに変わる欄（トレースID・スパンID・Problem Details の type の URL の番号）を伏せる（テストからだけ使う）。
 *
 * <p>「本文・ログに行の値が含まれない」を短い数字の目印（{@code 838}・8 桁の主キーの値など）で確かめると、乱数の16進のトレースID
 * や、テストのたびに変わるアプリの番号（{@code http://localhost:<番号>/api/problems/…}）に偶然に当たって落ちることがある（Intent
 * 261016-business-data-views の U3 の B6 で、{@code AbstractBusinessDataReadIT} の {@code 838} がトレースID {@code …c83841…} に
 * 当たって落ちた）。確かめる前にこの関数を通し、乱数の欄だけを決まった文字に置き換える。ほかの欄（detail・title・fieldErrors・
 * ログの message など）はそのまま残すため、行の値の漏れは今までどおり見つかる。
 */
public final class RandomFieldMask {

    /** トレースID・スパンID（JSON の文字の欄。応答の traceId、ログの MDC の traceId・spanId、監査のログの auditTraceId など名前が traceId・spanId で終わる欄） */
    private static final Pattern ID_FIELDS =
            Pattern.compile("\"([A-Za-z_]*(?:[tT]race_?[iI]d|[sS]pan_?[iI]d))\"\\s*:\\s*\"[^\"]*\"");

    /**
     * TRACE のログの、物の文字の形のトレースID・スパンID（{@code ProblemDetail} の properties や監査の記録の {@code traceId=<16進>}）。
     */
    private static final Pattern ID_PROPERTIES =
            Pattern.compile("\\b([A-Za-z_]*(?:[tT]race_?[iI]d|[sS]pan_?[iI]d))=[0-9a-fA-F]+");

    /**
     * Problem Details の type の URL のアプリの番号（{@code http://localhost:<番号>/api/problems/}）。対象DB の接続先（ホストと番号）の
     * 漏れの確かめを弱めないよう、{@code /api/problems/} が続く URL の番号だけを伏せる。
     */
    private static final Pattern PROBLEM_TYPE_PORT = Pattern.compile("(https?://[^/\"\\s]+):[0-9]+(/api/problems/)");

    private RandomFieldMask() {}

    /**
     * 乱数の欄を伏せた文字を返す。
     *
     * @param text 応答の本文かログ
     * @return トレースID・スパンIDの値を {@code <id>}、Problem Details の type の URL の番号を {@code <port>} に置き換えた文字
     */
    public static String mask(String text) {
        String masked = ID_FIELDS.matcher(text).replaceAll("\"$1\":\"<id>\"");
        masked = ID_PROPERTIES.matcher(masked).replaceAll("$1=<id>");
        return PROBLEM_TYPE_PORT.matcher(masked).replaceAll("$1:<port>$2");
    }
}
