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
package cherry.mastersmith.targetdb.domain;

import java.sql.Connection;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 業務データの読み取りの口（{@code TargetReadAccess}）が貸している間だけ使える、対象DB の接続と DB の種類の組（Intent
 * 261016-business-data-views の U1、BR8.1・BR10.1、entities.md の TargetReadSession）。
 *
 * <p>貸し出しの処理が戻ったら使えない。保存しない・戻り値にしない・口の外へ持ち出さない。接続を閉じるのは貸した口だけで、文を
 * 作るのは {@code businessdata.repository} の {@code RowSqlExecutor} だけ。
 *
 * <p>文字列にするときは DB の種類と問い合わせの上限の秒だけを出し、接続の文字列（接続先・ユーザー名を含みうる）を出さない
 * （{@code TraceAspect} が repository の層の引数を TRACE に文字列で出すため）。
 *
 * @param product DB の種類
 * @param connection 貸している接続
 * @param queryTimeoutSeconds 目的ごとの問い合わせ1回の待ちの上限（秒。1 以上）
 * @param brokenMark この接続は壊れたとみなす印（時間切れ・断絶の後に立てる。貸した口が返すときに見て、立っていれば接続を
 *     プールから捨てる。Intent 261025-deps-perf-carryover の FR3.2）
 */
public record TargetReadSession(
        DatabaseProduct product, Connection connection, int queryTimeoutSeconds, AtomicBoolean brokenMark) {

    /**
     * 印が立っていない状態で作る。
     *
     * @param product DB の種類
     * @param connection 貸している接続
     * @param queryTimeoutSeconds 問い合わせ1回の待ちの上限（秒。1 以上）
     */
    public TargetReadSession(DatabaseProduct product, Connection connection, int queryTimeoutSeconds) {
        this(product, connection, queryTimeoutSeconds, new AtomicBoolean());
    }

    /** 必須の値と、上限の秒が 1 以上であることを確かめる。 */
    public TargetReadSession {
        Objects.requireNonNull(product, "product は必須です");
        Objects.requireNonNull(connection, "connection は必須です");
        Objects.requireNonNull(brokenMark, "brokenMark は必須です");
        if (queryTimeoutSeconds < 1) {
            throw new IllegalArgumentException("queryTimeoutSeconds は 1 以上です");
        }
    }

    /** 時間切れ・断絶の後に、この接続を壊れたとみなす印を立てる（貸した口が返すときにプールから捨てる）。 */
    public void markBroken() {
        brokenMark.set(true);
    }

    /**
     * 壊れたとみなす印が立っているかを返す。
     *
     * @return 立っていれば true
     */
    public boolean isBroken() {
        return brokenMark.get();
    }

    /** DB の種類と問い合わせの上限の秒だけを出す（接続を出さない）。 */
    @Override
    public String toString() {
        return "TargetReadSession[product=" + product + ", queryTimeoutSeconds=" + queryTimeoutSeconds + "]";
    }
}
