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

import cherry.mastersmith.targetdb.domain.ReadPurpose;
import cherry.mastersmith.targetdb.domain.TargetSchemaResult;

/**
 * 対象DB のスキーマの読み取りの口（契約 C1・C3。U3 の生成と U4 の照合が使う）。
 *
 * <p>接続先とスキーマ名は {@code mastersmith.target-db.*} の設定だけから取る。読み取り専用の接続で、スキーマ・データを変更
 * しない。想定内の失敗（設定が無い・接続できない・応答しない）は結果の型で返し、例外にしない。結果にもログにも、接続先・
 * ユーザー名・パスワード・内部の例外のメッセージを含めない。
 *
 * <p>契約の記述（引数なし）との差: NFR 設計の決定で、目的（{@link ReadPurpose}）を受け取る。目的ごとに問い合わせ1回の待ちの
 * 上限が決まる。読み取りの全体の上限は持たない（照合の全体の上限は作らない。NFR 設計の承認の場の決定 B）。
 */
public interface TargetSchemaReader {

    /**
     * 設定したスキーマを読む。
     *
     * @param purpose 読み取りの目的（U3 は {@link ReadPurpose#GENERATE}、U4 の照合は {@link ReadPurpose#COMPARE}）
     * @return 成功・設定が無い・接続できない のどれか
     */
    TargetSchemaResult readSchema(ReadPurpose purpose);
}
