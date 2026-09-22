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
package cherry.mastersmith.auth.service;

import cherry.mastersmith.auth.repository.RefreshTokenRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 使い終わったリフレッシュトークンの削除の定期実行（NFR1.7、scalability-design 2章）。
 *
 * <p>期限から保存の日数（既定 7 日）を過ぎた行を、件数の上限（1,000 行）ごとに別のトランザクションで消す。消した件数を INFO で
 * 出し、失敗は ERROR（スタックトレース付き）で出して次の回に任せる（例外を外へ出さない）。
 */
@Component
public class RefreshTokenCleanupJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private final RefreshTokenRepository repository;

    private final AuthProperties properties;

    private final Clock clock;

    private final TransactionTemplate transaction;

    /**
     * 削除の処理を作る。
     *
     * @param repository リフレッシュトークンの DB アクセス
     * @param properties 認証の設定
     * @param clock 時計
     * @param transactionManager トランザクションの管理
     */
    public RefreshTokenCleanupJob(
            RefreshTokenRepository repository,
            AuthProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * 設定の時刻に削除を行う。
     *
     * @return 消した行の数（失敗したら -1）
     */
    @Scheduled(cron = "${mastersmith.auth.refresh-token-cleanup.cron}")
    public int run() {
        try {
            int deleted = cleanup();
            LOGGER.atInfo().addKeyValue("deleted", deleted).log("使い終わったリフレッシュトークンを削除しました");
            return deleted;
        } catch (RuntimeException e) {
            LOGGER.atError().setCause(e).log("使い終わったリフレッシュトークンの削除に失敗しました。次の回に再び行います");
            return -1;
        }
    }

    private int cleanup() {
        Instant cutoff = clock.instant().minus(properties.refreshTokenCleanup().retention());
        int total = 0;
        int deleted;
        do {
            Integer batch = transaction.execute(
                    status -> repository.deleteExpiredBefore(cutoff, RefreshTokenRepository.DELETE_BATCH_SIZE));
            deleted = batch == null ? 0 : batch;
            total += deleted;
        } while (deleted == RefreshTokenRepository.DELETE_BATCH_SIZE);
        return total;
    }
}
