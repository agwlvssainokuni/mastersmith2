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
package cherry.mastersmith.invitation.service;

import cherry.mastersmith.invitation.repository.InvitationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.function.IntSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 保存期間を過ぎた招待の定期の削除（BR11.1・BR11.2、NFR9.10、{@code reliability-design.md} 4節）。既存の
 * {@code RefreshTokenCleanupJob} と同じ形。
 *
 * <p>境目を「時計の今 − 保存の日数」とし、終わった招待（終わった日時が境目以前）と期限切れの招待中（有効期限が境目以前）を、件数の上限
 * （1,000 行）ごとに別のトランザクションで上限に届かなくなるまで消す。消した件数を INFO で出し、失敗は ERROR（スタックトレース付き）で
 * 出して次の回に任せる（例外を外へ出さない）。ログにメールアドレス・ハッシュを出さない。監査の記録は消さない。
 */
@Component
public class InvitationCleanupJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(InvitationCleanupJob.class);

    private final InvitationRepository repository;

    private final InvitationSettings settings;

    private final Clock clock;

    private final TransactionTemplate transaction;

    /**
     * 作る。
     *
     * @param repository 招待の DB アクセス
     * @param settings 招待の設定（保存の日数）
     * @param clock 時計
     * @param transactionManager トランザクションの管理
     */
    public InvitationCleanupJob(
            InvitationRepository repository,
            InvitationSettings settings,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.settings = settings;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * 設定の時刻に削除を行う。
     *
     * @return 消した行の数（失敗したら -1）
     */
    @Scheduled(cron = "${mastersmith.invitation.cleanup.cron:0 45 3 * * *}")
    public int run() {
        try {
            Instant cutoff = clock.instant().minus(settings.retention());
            int ended =
                    deleteInBatches(() -> repository.deleteEndedBefore(cutoff, InvitationRepository.DELETE_BATCH_SIZE));
            int expired = deleteInBatches(
                    () -> repository.deleteExpiredPendingBefore(cutoff, InvitationRepository.DELETE_BATCH_SIZE));
            int deleted = ended + expired;
            LOGGER.atInfo()
                    .addKeyValue("deleted", deleted)
                    .addKeyValue("ended", ended)
                    .addKeyValue("expiredPending", expired)
                    .log("保存期間を過ぎた招待を削除しました");
            return deleted;
        } catch (RuntimeException e) {
            LOGGER.atError().setCause(e).log("保存期間を過ぎた招待の削除に失敗しました。次の回に再び行います");
            return -1;
        }
    }

    private int deleteInBatches(IntSupplier batch) {
        int total = 0;
        int deleted;
        do {
            Integer count = transaction.execute(status -> batch.getAsInt());
            deleted = count == null ? 0 : count;
            total += deleted;
        } while (deleted == InvitationRepository.DELETE_BATCH_SIZE);
        return total;
    }
}
