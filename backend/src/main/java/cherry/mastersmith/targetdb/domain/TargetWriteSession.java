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
import java.sql.SQLException;
import java.util.Objects;

/**
 * 業務データの書き込みの口（{@code TargetWriteAccess}）が貸している間だけ使える、対象DB の書き込みの接続と DB の種類と、セーブポイントの
 * 操作の口の組（Intent 261016-business-data-views の U2、BR9.3、logical-components.md の L8）。
 *
 * <p>貸し出しの処理が戻ったら使えない。保存しない・戻り値にしない・口の外へ持ち出さない。接続を閉じ、確定・巻き戻しをするのは貸した口
 * だけで、文を作るのは {@code businessdata.repository} の {@code RowSqlExecutor} だけ。
 *
 * <p>文字列にするときは DB の種類と文の上限の秒だけを出し、接続の文字列（接続先・ユーザー名を含みうる）を出さない
 * （{@code TraceAspect} が repository の層の引数を TRACE に文字列で出すため）。
 *
 * @param product DB の種類
 * @param connection 貸している書き込みの接続
 * @param queryTimeoutSeconds 書き込みの文1回の待ちの上限（秒。1 以上）
 * @param savepoints セーブポイントの操作の口
 */
public record TargetWriteSession(
        DatabaseProduct product, Connection connection, int queryTimeoutSeconds, SavepointMarker savepoints) {

    /** 必須の値と、上限の秒が 1 以上であることを確かめる。 */
    public TargetWriteSession {
        Objects.requireNonNull(product, "product は必須です");
        Objects.requireNonNull(connection, "connection は必須です");
        Objects.requireNonNull(savepoints, "savepoints は必須です");
        if (queryTimeoutSeconds < 1) {
            throw new IllegalArgumentException("queryTimeoutSeconds は 1 以上です");
        }
    }

    /**
     * 書き込みの文の直前のセーブポイントを置く（違反の後の確かめのため。reliability-design.md の 3節）。
     *
     * @throws SQLException セーブポイントを置けなかったとき
     */
    public void markSavepoint() throws SQLException {
        savepoints.mark();
    }

    /** DB の種類と文の上限の秒だけを出す（接続を出さない）。 */
    @Override
    public String toString() {
        return "TargetWriteSession[product=" + product + ", queryTimeoutSeconds=" + queryTimeoutSeconds + "]";
    }
}
