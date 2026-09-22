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
package cherry.mastersmith.auth.testsupport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Hibernate が発行する SQL を、スレッドごとに数えるテストの補助（BR2.7 の回数の確かめ）。
 *
 * <p>{@code spring.jpa.properties.hibernate.session_factory.statement_inspector} にこのクラスの名前を設定し、
 * {@link #start()} から {@link #stop()} の間に発行された SQL を記録する。記録は、SQL の種類（最初の語）と対象の表の名前に
 * まとめた「種類」で比べる。
 */
public class SqlStatementCounter implements StatementInspector {

    private static final long serialVersionUID = 1L;

    private static final Pattern TABLE = Pattern.compile(
            "(?:\\bfrom|\\bupdate|\\binto|\\bmerge into)\\s+([a-z_][a-z0-9_]*)", Pattern.CASE_INSENSITIVE);

    private static final List<Recorded> RECORDED = new CopyOnWriteArrayList<>();

    private static volatile boolean recording;

    /** 記録した SQL 1件（発行したスレッドと SQL）。 */
    public record Recorded(String thread, String sql) {}

    @Override
    public String inspect(String sql) {
        if (recording) {
            RECORDED.add(new Recorded(Thread.currentThread().getName(), sql));
        }
        return sql;
    }

    /** これまでの記録を消して、記録を始める。 */
    public static void start() {
        RECORDED.clear();
        recording = true;
    }

    /**
     * 記録を止め、スレッドごとの SQL の種類の一覧を返す。
     *
     * @return スレッドの名前ごとの SQL の種類（発行の順）
     */
    public static Map<String, List<String>> stop() {
        recording = false;
        Map<String, List<String>> byThread = new LinkedHashMap<>();
        for (Recorded recorded : RECORDED) {
            byThread.computeIfAbsent(recorded.thread(), key -> new ArrayList<>())
                    .add(kind(recorded.sql()));
        }
        return byThread;
    }

    /**
     * SQL を「種類」（最初の語と対象の表、排他の指定の有無）にまとめる。
     *
     * @param sql SQL
     * @return 種類（例: {@code select users}、{@code select login_attempt_states for update}）
     */
    public static String kind(String sql) {
        String normalized = sql.strip().toLowerCase(Locale.ROOT);
        String verb = normalized.split("\\s+", 2)[0];
        Matcher matcher = TABLE.matcher(normalized);
        String table = matcher.find() ? matcher.group(1) : "?";
        String lock = normalized.contains(" for update") ? " for update" : "";
        return verb + " " + table + lock;
    }
}
