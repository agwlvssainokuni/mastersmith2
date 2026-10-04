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
package cherry.mastersmith.user.testsupport;

import cherry.mastersmith.user.domain.InitialAdminRescuedEvent;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 初期管理者の救済の知らせを受けて例外を投げる、テストだけの受け手（Intent 261004-safety-carryover の FR1.2a）。
 *
 * <p>起動の元に足して使う（{@code new SpringApplicationBuilder(MastersmithApplication.class,
 * FailingInitialAdminRescueConfig.class)}）。順を最後にして auth の受け手（順 {@code 0}）の後に呼ばれるようにし、投げる前に同じ
 * トランザクションの中で失敗回数とリフレッシュトークンが書き換わっていることを読み取り、{@link Probe} に残す（巻き戻しが auth の
 * 書き換えも覆うことを確かめるため）。
 */
@TestConfiguration(proxyBeanMethods = false)
public class FailingInitialAdminRescueConfig {

    /** 投げる例外のメッセージ（ログに出ないことを確かめるため、決まった文にする）。 */
    public static final String FAILURE_MESSAGE = "テスト用の救済の途中の失敗";

    /** 投げる前に、同じトランザクションの中で読んだ値。 */
    public static class Probe {

        private volatile Integer failuresSeen;

        private volatile Integer activeTokensSeen;

        private final AtomicInteger calls = new AtomicInteger();

        /**
         * 失敗回数を返す。
         *
         * @return 投げる前に読んだ失敗回数（呼ばれていなければ null）
         */
        public Integer failuresSeen() {
            return failuresSeen;
        }

        /**
         * まだ無効でないリフレッシュトークンの数を返す。
         *
         * @return 投げる前に読んだ数（呼ばれていなければ null）
         */
        public Integer activeTokensSeen() {
            return activeTokensSeen;
        }

        /**
         * 呼ばれた回数を返す。
         *
         * @return 回数
         */
        public int calls() {
            return calls.get();
        }
    }

    /** 救済の知らせを受けて、読み取ってから例外を投げる受け手。 */
    public static class FailingRescueListener {

        private final JdbcTemplate jdbc;

        private final Probe probe;

        /**
         * 作る。
         *
         * @param dataSource 内部DB
         * @param probe 読んだ値の置き場
         */
        public FailingRescueListener(DataSource dataSource, Probe probe) {
            this.jdbc = new JdbcTemplate(dataSource);
            this.probe = probe;
        }

        /**
         * 同じトランザクションの中で読み取り、例外を投げる。
         *
         * @param event 救済の知らせ
         */
        @Order(Ordered.LOWEST_PRECEDENCE)
        @EventListener
        public void onInitialAdminRescued(InitialAdminRescuedEvent event) {
            probe.calls.incrementAndGet();
            probe.failuresSeen = jdbc.queryForObject(
                    "SELECT consecutive_failures FROM login_attempt_states WHERE subject_id = ?",
                    Integer.class,
                    event.userId());
            probe.activeTokensSeen = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
                    Integer.class,
                    event.userId());
            throw new IllegalStateException(FAILURE_MESSAGE);
        }
    }

    /**
     * 読んだ値の置き場を作る。
     *
     * @return 置き場
     */
    @Bean
    public Probe failingInitialAdminRescueProbe() {
        return new Probe();
    }

    /**
     * 失敗させる受け手を作る。
     *
     * @param dataSource 内部DB
     * @param probe 読んだ値の置き場
     * @return 受け手
     */
    @Bean
    public FailingRescueListener failingInitialAdminRescueListener(DataSource dataSource, Probe probe) {
        return new FailingRescueListener(dataSource, probe);
    }
}
