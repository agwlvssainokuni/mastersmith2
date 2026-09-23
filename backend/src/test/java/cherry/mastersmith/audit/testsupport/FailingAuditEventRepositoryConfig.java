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
package cherry.mastersmith.audit.testsupport;

import cherry.mastersmith.audit.domain.AuditEvent;
import cherry.mastersmith.audit.repository.AuditEventRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * 監査イベントの追記を必ず失敗させるテスト用の保存の部品（計画の C9）。本物の DB を壊さずに、書き込みの失敗を起こす。
 *
 * <p>失敗の起こし方は3つ。追記の失敗（DB の例外）、接続を借りる失敗（トランザクションを始められない例外）、そして失敗させない
 * 状態（下ごしらえのときに使う）。
 */
@TestConfiguration(proxyBeanMethods = false)
public class FailingAuditEventRepositoryConfig {

    /** 失敗のさせ方。 */
    public enum Mode {
        /** 失敗させない（本物の保存の部品にそのまま渡す）。 */
        NONE,
        /** 追記の失敗（DB の例外）。 */
        APPEND_FAILURE,
        /** 接続を借りる失敗（トランザクションを始められない）。 */
        CONNECTION_FAILURE
    }

    /** 失敗する保存の部品。失敗させない状態では、本物の保存の部品にそのまま渡す。 */
    public static class FailingAuditEventRepository implements AuditEventRepository {

        private final AuditEventRepository delegate;

        private volatile Mode mode = Mode.APPEND_FAILURE;

        private final AtomicInteger saveCalls = new AtomicInteger();

        /**
         * 作る。
         *
         * @param delegate 本物の保存の部品
         */
        public FailingAuditEventRepository(AuditEventRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public <S extends AuditEvent> S save(S auditEvent) {
            saveCalls.incrementAndGet();
            return switch (mode) {
                case APPEND_FAILURE ->
                    throw new DataIntegrityViolationException(
                            "テスト用の追記の失敗（INSERT INTO audit_events）",
                            new DataAccessResourceFailureException("テスト用の元の例外"));
                case CONNECTION_FAILURE ->
                    throw new CannotCreateTransactionException(
                            "テスト用の接続を借りる失敗", new IllegalStateException("プールから接続を借りられませんでした"));
                case NONE -> delegate.save(auditEvent);
            };
        }

        @Override
        public Optional<AuditEvent> findById(Long auditEventId) {
            return delegate.findById(auditEventId);
        }

        @Override
        public List<AuditEvent> findAllByOrderByOccurredAtAsc() {
            return delegate.findAllByOrderByOccurredAtAsc();
        }

        /**
         * 失敗のさせ方を決める。
         *
         * @param mode 失敗のさせ方
         */
        public void mode(Mode mode) {
            this.mode = mode;
        }

        /**
         * これまでの追記の呼び出しの回数を返して 0 に戻す（再試行が無いことの確かめに使う）。
         *
         * @return 追記の呼び出しの回数
         */
        public int takeSaveCalls() {
            return saveCalls.getAndSet(0);
        }
    }

    /**
     * 失敗する保存の部品を、本物の代わりに置く。
     *
     * @param delegate 本物の保存の部品（Spring Data が作る Bean）
     * @return 失敗する保存の部品
     */
    @Bean
    @Primary
    public FailingAuditEventRepository failingAuditEventRepository(
            @Qualifier("auditEventRepository") AuditEventRepository delegate) {
        return new FailingAuditEventRepository(delegate);
    }
}
