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
package cherry.mastersmith.common.observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.regex.Pattern;
import net.logstash.logback.composite.JsonWritingUtils;
import net.logstash.logback.composite.loggingevent.MessageJsonProvider;
import tools.jackson.core.JsonGenerator;

/**
 * 標準出力の JSON のメッセージの項目を、改行を含まない1行の文字列にして出す部品（260924-followup-fixes の FR4.1、FR4.3）。
 *
 * <p>JSON の出力はもともと改行を {@code \n} と書いて1行に出すが、メッセージの値の中に改行が残るため、読むときに1件が複数行に
 * 見える（Hibernate の起動の案内など）。改行の並び（CRLF・CR・LF）1つを {@link #LINE_BREAK_MARK} 1つに置き換える。行の頭の
 * タブなどはそのまま残す。項目の名前と並びは元の部品と同じで、スタックトレースの項目（別の部品）は変えない。
 */
public class SingleLineMessageJsonProvider extends MessageJsonProvider {

    /** 改行の代わりに置く記号（前後に空白を置いた U+23CE）。 */
    static final String LINE_BREAK_MARK = " ⏎ ";

    private static final Pattern LINE_BREAK = Pattern.compile("\r\n|\r|\n");

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) {
        JsonWritingUtils.writeStringField(generator, getFieldName(), toSingleLine(event.getFormattedMessage()));
    }

    /**
     * 改行の並びを記号に置き換える。
     *
     * @param message 元のメッセージ（null 可）
     * @return 改行を含まないメッセージ（元が null なら null）
     */
    static String toSingleLine(String message) {
        if (message == null || (message.indexOf('\n') < 0 && message.indexOf('\r') < 0)) {
            return message;
        }
        return LINE_BREAK.matcher(message).replaceAll(LINE_BREAK_MARK);
    }
}
