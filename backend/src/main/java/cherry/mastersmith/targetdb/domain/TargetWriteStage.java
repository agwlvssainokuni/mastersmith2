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

/**
 * 業務データの書き込みの口（{@code TargetWriteAccess}）のどの段で失敗したか（Intent 261016-business-data-views の U2、
 * reliability-design.md の 2節・logical-components.md の L8）。文の段（事前の問い合わせ・書き込みの文・確かめ・結果の読み）は文を作る
 * {@code businessdata.repository} が持ち、ここには口が受け持つ段だけを置く。
 */
public enum TargetWriteStage {
    /** 接続を借りる（トランザクションを始める）段。書き込みの文は送っていない。 */
    BORROW,
    /** 口に渡した処理が、想定外の非検査の例外を投げた。 */
    WORK,
    /** 確定の段（確定したか分からない）。 */
    COMMIT,
    /** 違反の後に、書き込みの文の直前のセーブポイントへ戻す段。 */
    ROLLBACK_TO_SAVEPOINT,
    /** 違反の後の確かめの処理が、想定外の非検査の例外を投げた。 */
    INSPECT,
    /** 全体を巻き戻す段。 */
    ROLLBACK
}
