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
import cherry.mastersmith.targetdb.domain.ReadPurpose;
import cherry.mastersmith.targetdb.domain.TargetReadOutcome;
import cherry.mastersmith.targetdb.domain.TargetReadSession;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

/**
 * 業務データの読み取りの接続を貸す口（Intent 261016-business-data-views の U1、BR8.1・契約 C6、logical-components.md の L9）。
 *
 * <p>既存の読み取りの接続（既定の候補にしない {@code targetDataSource}）から1本借り、目的の上限 + 5 秒の通信の待ちを付けて処理に
 * 貸し、処理が戻ったら（失敗しても）元の通信の待ちに戻して閉じ、処理の結果だけを返す。接続を戻り値で渡さない・保存しない。設定が
 * 無ければ処理を呼ばない。借りる失敗は理由で分けない（NFR 設計の Q3: A）。例外のメッセージと原因は結果に持たない。
 */
public interface TargetReadAccess {

    /**
     * 接続を1本借りて処理に貸す。
     *
     * @param purpose 目的（{@link ReadPurpose#LIST}・{@link ReadPurpose#DETAIL}・{@link ReadPurpose#REFERENCE}。スキーマの読み取りの
     *     目的は受け付けない）
     * @param work 貸し出しの間だけ接続を使う処理（接続を保存しない・戻り値にしない）
     * @param <T> 処理の結果の型
     * @return 処理を呼んで終えた・設定が無い・接続を借りられない のどれか
     * @throws IllegalArgumentException スキーマの読み取りの目的を渡したとき
     * @throws cherry.mastersmith.targetdb.domain.TargetReadUnexpectedException 処理が非検査の例外を投げたとき（メッセージと原因を
     *     持たない）
     */
    <T> TargetReadOutcome<T> withRead(ReadPurpose purpose, Function<TargetReadSession, T> work);

    /**
     * 接続せずに、設定から DB の種類を返す（定義の API の型の扱い。BR7.9・F3: A）。
     *
     * @return DB の種類（設定が無ければ空）
     */
    Optional<DatabaseProduct> product();

    /**
     * 定義の取得で参照先を読む全体の待ちの上限を返す（Intent 261029-reference-lookup-cleanup の U1、NFR3.5・reliability-design.md
     * 5.1）。{@code businessdata} は {@code targetdb.config} に依存できないため、この口で受け渡す。
     *
     * <p>{@link #withRead} の処理（work）の中でだけ呼ぶ。そこでは設定が「使える」ことが決まっている。設定が「使える」でないとき
     * （使わない・不正）に呼ぶのは呼び出し側の誤りで、{@link IllegalStateException} を投げる（NFR 設計 U1 のレビュー R-05）。
     *
     * @return 全体の待ちの上限（1 秒以上）
     * @throws IllegalStateException 設定が「使える」でないとき
     */
    Duration referenceReadTotal();
}
