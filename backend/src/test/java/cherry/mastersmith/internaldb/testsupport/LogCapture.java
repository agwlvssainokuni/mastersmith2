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
package cherry.mastersmith.internaldb.testsupport;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.LoggerFactory;

/**
 * アプリの全ロガー（ルート）の出力を集める、テストの補助（Logback のアペンダー）。起動の失敗のテストで、接続先・利用者名・
 * パスワードがどのロガーの出力（メッセージ・キーと値・例外の連なり）にも出ないことを確かめるために使う。閉じるとアペンダーを外す。
 */
public final class LogCapture implements AutoCloseable {

    private final Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Level originalLevel;

    private LogCapture() {
        originalLevel = root.getLevel();
        appender.start();
        root.addAppender(appender);
    }

    /**
     * ルートのロガーへアペンダーを付けて集め始める。
     *
     * @return 集めている間だけ有効な補助
     */
    public static LogCapture start() {
        return new LogCapture();
    }

    /**
     * 集めた出来事。
     *
     * @return 出来事の一覧（コピー）
     */
    public List<ILoggingEvent> events() {
        return List.copyOf(appender.list);
    }

    /**
     * 集めた ERROR の出来事のうち、メッセージが指定の文のもの。
     *
     * @param message メッセージ
     * @return 出来事の一覧
     */
    public List<ILoggingEvent> errorsWithMessage(String message) {
        return events().stream()
                .filter(e -> e.getLevel() == Level.ERROR && message.equals(e.getFormattedMessage()))
                .toList();
    }

    /**
     * 集めた出来事を、メッセージ・キーと値・例外の連なり（型と文）まで含めて 1 つの文字列にしたもの。値が出ていないことの検索に使う。
     *
     * @return すべての出力
     */
    public String allText() {
        return events().stream().map(LogCapture::text).collect(Collectors.joining("\n"));
    }

    /**
     * 1 件の出来事をメッセージ・キーと値・例外の連なりまで文字列にする。
     *
     * @param event 出来事
     * @return 文字列
     */
    public static String text(ILoggingEvent event) {
        StringBuilder out = new StringBuilder(event.getLoggerName()).append(' ').append(event.getFormattedMessage());
        if (event.getKeyValuePairs() != null) {
            event.getKeyValuePairs()
                    .forEach(kv -> out.append(' ').append(kv.key).append('=').append(kv.value));
        }
        if (event.getArgumentArray() != null) {
            for (Object arg : event.getArgumentArray()) {
                out.append(' ').append(arg);
            }
        }
        for (IThrowableProxy proxy = event.getThrowableProxy(); proxy != null; proxy = proxy.getCause()) {
            out.append(' ').append(proxy.getClassName()).append(": ").append(proxy.getMessage());
        }
        return out.toString();
    }

    @Override
    public void close() {
        root.detachAppender(appender);
        appender.stop();
        root.setLevel(originalLevel);
    }
}
