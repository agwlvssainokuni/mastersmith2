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
 * 業務データの読み取りの口（{@code TargetReadAccess#withRead}）の結果（Intent 261016-business-data-views の U1、BR8.1、
 * logical-components.md の L8）。処理を呼んで終えた・設定が無い・接続を借りられない のどれか。
 *
 * <p>どの場合も、接続先・ユーザー名・パスワード・例外のメッセージと原因を持たない（型にその項目が無い）。
 *
 * @param <T> 処理の結果の型
 */
public sealed interface TargetReadOutcome<T> {

    /**
     * 処理を呼んで終えた。
     *
     * @param value 処理の結果
     * @param <T> 処理の結果の型
     */
    record Done<T>(T value) implements TargetReadOutcome<T> {

        /**
         * 値の型の単純名だけを出す（値そのものを出さない。{@code TraceAspect} が service の層の戻り値を TRACE に出すため。NFR 設計の
         * レビュー R-02）。
         */
        @Override
        public String toString() {
            return "Done[valueType="
                    + (value == null ? "null" : value.getClass().getSimpleName()) + "]";
        }
    }

    /**
     * 対象DB の読み取りの接続の設定が無い（使わない・不正を含む）。処理は呼んでいない。
     *
     * @param <T> 処理の結果の型
     */
    record NotConfigured<T>() implements TargetReadOutcome<T> {}

    /**
     * 接続を借りられない（接続できない・借りる待ちの時間切れ・通信の待ちを付けられない）。処理は呼んでいない。借りる失敗は理由で
     * 分けない（NFR 設計の Q3: A）。
     *
     * @param sqlState SQLState（無ければ null）
     * @param vendorCode 業者のエラーの番号（無ければ null）
     * @param exceptionClass 例外のクラスの名前
     * @param <T> 処理の結果の型
     */
    record Unavailable<T>(String sqlState, Integer vendorCode, String exceptionClass) implements TargetReadOutcome<T> {}
}
