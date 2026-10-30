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
 * 読み取りの目的（NFR 設計の差: {@code readSchema(ReadPurpose)}）。目的ごとに問い合わせ1回の待ちの上限が決まる。読み取りの
 * 全体の上限は、生成・照合のどちらにも無い（照合の全体の上限は作らない。NFR 設計の承認の場の決定 B）。
 *
 * <p>業務データの読み取り（Intent 261016-business-data-views の U1、BR8.2・NFR4.4）の一覧と詳細の目的を足した。スキーマの読み取り
 * （{@code TargetSchemaReader}）は生成・照合だけを使い、業務データの読み取りの口（{@code TargetReadAccess}）は一覧・詳細・参照先を使う。
 *
 * <p>他テーブル参照の参照先の読み取り（Intent 261029-reference-lookup-cleanup の U1、BR8.1・契約 C5）の目的を足した。定義の取得で
 * 参照先を読むときだけ、全体の上限（{@code TargetReadAccess#referenceReadTotal()}）が別にある。
 */
public enum ReadPurpose {
    /** 既定の DSL の生成（U3）。問い合わせ1回の上限は既定 20 秒。 */
    GENERATE,
    /** 適用中の DSL との照合（U4）。問い合わせ1回の上限は既定 5 秒。 */
    COMPARE,
    /** 業務データの一覧（件数とページのそれぞれ）。問い合わせ1回の上限は既定 5 秒。 */
    LIST,
    /** 業務データの詳細。問い合わせ1回の上限は既定 5 秒。 */
    DETAIL,
    /** 他テーブル参照の参照先（選択肢・検索・ラベル・保存前の確かめ）。問い合わせ1回の上限は既定 5 秒。 */
    REFERENCE
}
