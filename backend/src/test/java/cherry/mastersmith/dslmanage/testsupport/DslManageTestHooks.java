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

import cherry.mastersmith.dslmanage.generate.DefaultDslGenerator;
import cherry.mastersmith.dslmanage.generate.DefaultDslResult;
import cherry.mastersmith.dslmanage.repository.DslAppliedRevisionRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * DSL の管理の結合テストの差し込み口（本番のコードは変えない）。
 *
 * <ul>
 *   <li>履歴への写しの直後で、同時の適用を指定の件数がそろうまで待ち合わせる（AC4.2.3。重なりを確実に作る）
 *   <li>履歴への写しの直後で失敗させる（AC4.2.4。写した行が巻き戻ることを確かめる）
 *   <li>既定の DSL の生成の途中で止め、重い処理の許可を持ったままにする（NFR1.13。{@code DSL_BUSY} を確実に作る）
 * </ul>
 *
 * <p>実時刻の {@code sleep} は使わない。待ちには上限を置き、そろわなくてもテストが止まらないようにする。
 */
@TestConfiguration(proxyBeanMethods = false)
public class DslManageTestHooks {

    /**
     * 差し込み口つきの履歴の表の DB アクセスを、本物の代わりに置く。
     *
     * @param entityManager JPA のエンティティの管理
     * @return 差し込み口つきの DB アクセス
     */
    @Bean
    @Primary
    public ControlledRevisionRepository controlledRevisionRepository(EntityManager entityManager) {
        return new ControlledRevisionRepository(entityManager);
    }

    /**
     * 止められる既定の DSL の生成を、本物の代わりに置く（対象DB を設定しないテストで使う）。
     *
     * @return 止められる生成
     */
    @Bean
    @Primary
    public BlockingGenerator blockingGenerator() {
        return new BlockingGenerator();
    }

    /** 差し込み口つきの履歴の表の DB アクセス。 */
    public static class ControlledRevisionRepository extends DslAppliedRevisionRepository {

        private volatile CountDownLatch barrier;

        private volatile Duration maxWait = Duration.ZERO;

        private volatile boolean failAfterCopy;

        /**
         * 作る。
         *
         * @param entityManager JPA のエンティティの管理
         */
        public ControlledRevisionRepository(EntityManager entityManager) {
            super(entityManager);
        }

        /**
         * 写しの直後の待ち合わせを始める。
         *
         * @param parties そろえる件数
         * @param wait 待ちの上限
         */
        public void arm(int parties, Duration wait) {
            maxWait = wait;
            barrier = new CountDownLatch(parties);
        }

        /**
         * 前回の待ち合わせで、決めた件数がそろったかを返す。
         *
         * @return そろったとき true
         */
        public boolean allArrived() {
            CountDownLatch current = barrier;
            return current != null && current.getCount() == 0;
        }

        /**
         * 写しの直後に失敗させるかを決める。
         *
         * @param fail 失敗させるなら true
         */
        public void failAfterCopy(boolean fail) {
            failAfterCopy = fail;
        }

        /** 差し込み口をすべて外す。 */
        public void reset() {
            barrier = null;
            failAfterCopy = false;
        }

        @Override
        public int copyFromPreview(UUID revisionId, UUID previewId, long appliedByUserId, Instant appliedAt) {
            int copied = super.copyFromPreview(revisionId, previewId, appliedByUserId, appliedAt);
            CountDownLatch current = barrier;
            if (current != null) {
                current.countDown();
                try {
                    current.await(maxWait.toMillis(), TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (failAfterCopy) {
                throw new DataAccessResourceFailureException("テスト用の履歴の書き込みの失敗");
            }
            return copied;
        }
    }

    /** 止められる既定の DSL の生成（止めていなければ、小さな DSL を返す）。 */
    public static class BlockingGenerator implements DefaultDslGenerator {

        private volatile CountDownLatch entered = new CountDownLatch(0);

        private volatile CountDownLatch release = new CountDownLatch(0);

        private final byte[] yaml =
                DslYaml.dsl().table("generated", DslYaml.column("c")).bytes();

        private final String hash;

        /** 作る。 */
        public BlockingGenerator() {
            this.hash = DslYamlHashes.sha256(yaml);
        }

        /** 次の生成を止める（{@link #awaitEntered} で入ったことを待ち、{@link #release} で進める）。 */
        public void block() {
            entered = new CountDownLatch(1);
            release = new CountDownLatch(1);
        }

        /**
         * 止めた生成に入るのを待つ。
         *
         * @return 上限（20 秒）までに入れば true
         * @throws InterruptedException 割り込まれたとき
         */
        public boolean awaitEntered() throws InterruptedException {
            return entered.await(20, TimeUnit.SECONDS);
        }

        /** 止めた生成を進める。 */
        public void release() {
            release.countDown();
        }

        @Override
        public DefaultDslResult generate() {
            entered.countDown();
            try {
                release.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new DefaultDslResult.Generated(yaml, hash);
        }
    }
}
