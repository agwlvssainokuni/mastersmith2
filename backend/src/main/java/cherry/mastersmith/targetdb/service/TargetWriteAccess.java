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
package cherry.mastersmith.targetdb.service;

import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.domain.TargetWriteOutcome;
import cherry.mastersmith.targetdb.domain.TargetWriteSession;
import cherry.mastersmith.targetdb.domain.WriteStep;
import java.util.Optional;
import java.util.function.Function;

/**
 * 業務データの書き込みの接続を貸す口（Intent 261016-business-data-views の U2、BR9.3・契約 C6、logical-components.md の L9）。
 *
 * <p>名前つきの書き込みの接続（{@code targetWriteDataSource}）から、名前つきの管理者（{@code targetWriteTransactionManager}）の
 * トランザクション（READ COMMITTED）の中で1本を1回だけ借りて処理に貸し、処理の答え（{@link WriteStep}）に従って確定・巻き戻し・
 * セーブポイントへ戻して確かめる のどれかをしてから返す。接続を戻り値で渡さない・保存しない。設定が無ければ処理を呼ばない。
 * 例外を外へ出さず、例外のメッセージと原因を結果に持たない。
 */
public interface TargetWriteAccess {

    /**
     * 書き込みの接続を1本借りて処理に貸す。
     *
     * @param work 貸し出しの間だけ接続を使い、答えを返す処理（接続を保存しない・戻り値にしない・例外を投げない）
     * @param <T> 処理の結果の型
     * @return 答えに従って終えた・設定が無い・借りられない・口が受け持つ段で失敗した のどれか
     */
    <T> TargetWriteOutcome<T> withWrite(Function<TargetWriteSession, WriteStep<T>> work);

    /**
     * 接続せずに、書き込みの設定から DB の種類を返す（書き込みの設定があるかの判定にも使う。BR1.7）。
     *
     * @return DB の種類（書き込みの設定が無ければ空）
     */
    Optional<DatabaseProduct> product();
}
