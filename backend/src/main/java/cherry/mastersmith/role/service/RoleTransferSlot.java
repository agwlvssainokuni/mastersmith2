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
package cherry.mastersmith.role.service;

import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 権限の YAML の確かめと適用を、アプリの中で同時に1つずつしか通さない入口（{@code logical-components.md} の L9、
 * {@code reliability-design.md} 2.6、NFR 設計の Q5: A）。
 *
 * <p>本文を読む前に {@link #tryAcquire()} で待たずに取り、取れなければ呼び出し元が 409 {@code ROLE_BUSY} で断る（監査に残さない）。取った
 * 後は呼び出し元が {@code finally} で必ず {@link #release()} する。取れなかったときの WARN は、例外を持たないため
 * {@code RowLockFailures.warn} を使わず、キーを {@code lockKind}（{@value #LOCK_KIND}）だけにした決まった文で1回出す（計画の D-20、NFR
 * 設計の読み直しの R-12）。単一のインスタンスの前提（組み込みの H2）のため、入口はアプリの中の1つで足りる。
 */
@Component
public class RoleTransferSlot {

    /** 入口を取れなかったときの WARN の {@code lockKind}。 */
    public static final String LOCK_KIND = "ROLE_TRANSFER_SLOT";

    /** 入口を取れなかったときの WARN の決まった文。 */
    static final String WARN_MESSAGE = "権限の受け渡しの入口を取れませんでした";

    private static final Logger LOGGER = LoggerFactory.getLogger(RoleTransferSlot.class);

    private final Semaphore permit = new Semaphore(1);

    /**
     * 入口を待たずに取る。取れなければ WARN を1回出す。
     *
     * @return 取れたら真
     */
    public boolean tryAcquire() {
        if (permit.tryAcquire()) {
            return true;
        }
        LOGGER.atWarn().addKeyValue("lockKind", LOCK_KIND).log(WARN_MESSAGE);
        return false;
    }

    /** 取った入口を放す（{@link #tryAcquire()} が真を返したときだけ呼ぶ）。 */
    public void release() {
        permit.release();
    }
}
