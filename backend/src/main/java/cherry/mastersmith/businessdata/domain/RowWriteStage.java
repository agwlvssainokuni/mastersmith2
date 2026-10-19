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
package cherry.mastersmith.businessdata.domain;

/**
 * 書き込みの SQL のどの段で失敗したか（Intent 261016-business-data-views の U2、reliability-design.md の 2節。U1 の R-01 を先に避ける形）。
 * 分類の部品（{@code RowSqlFailureClassifier}）に段を渡し、段ごとの表で引く。
 */
public enum RowWriteStage {
    /** 接続を借りる（MySQL・MariaDB の初期化の文を含む）。 */
    BORROW,
    /** 一意の事前の問い合わせ（書き込みの文を送る前）。セーブポイントを置く段もここに数える。 */
    PRECHECK,
    /** 書き込みの文（INSERT・UPDATE・DELETE）。 */
    WRITE,
    /** 確定。 */
    COMMIT,
    /** 違反の後のセーブポイントへの戻し。 */
    ROLLBACK_TO_SAVEPOINT,
    /** 違反の後の確かめの問い合わせ。 */
    INSPECT,
    /** 全体の巻き戻し。 */
    ROLLBACK,
    /** 結果の読み（生成された鍵）。 */
    READ_RESULT
}
