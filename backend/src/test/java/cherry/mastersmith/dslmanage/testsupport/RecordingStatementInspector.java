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
package cherry.mastersmith.dslmanage.testsupport;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Hibernate が発行する SQL の本文を記録するテストの補助（本文の列を読まないことの確かめに使う）。
 *
 * <p>{@code spring.jpa.properties.hibernate.session_factory.statement_inspector} にこのクラスの名前を設定したテストだけで働く。
 * {@link #start()} から {@link #stop()} までの SQL を記録する。
 */
public class RecordingStatementInspector implements StatementInspector {

    private static final long serialVersionUID = 1L;

    private static final List<String> RECORDED = new CopyOnWriteArrayList<>();

    private static volatile boolean recording;

    @Override
    public String inspect(String sql) {
        if (recording) {
            RECORDED.add(sql);
        }
        return sql;
    }

    /** これまでの記録を消して、記録を始める。 */
    public static void start() {
        RECORDED.clear();
        recording = true;
    }

    /**
     * 記録をやめ、記録した SQL を返す。
     *
     * @return 記録した SQL（発行の順）
     */
    public static List<String> stop() {
        recording = false;
        return List.copyOf(RECORDED);
    }
}
