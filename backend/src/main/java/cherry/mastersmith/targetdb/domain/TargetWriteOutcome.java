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

import java.util.Objects;

/**
 * 業務データの書き込みの口（{@code TargetWriteAccess#withWrite}）の結果（Intent 261016-business-data-views の U2、BR9.3・BR10.1、
 * logical-components.md の L8）。処理の答えに従って終えた・設定が無い・接続を借りられない・口が受け持つ段で失敗した のどれか。
 *
 * <p>どの場合も、接続先・ユーザー名・パスワード・例外のメッセージと原因を持たない（型にその項目が無い）。
 *
 * @param <T> 処理の結果の型
 */
public sealed interface TargetWriteOutcome<T> {

    /**
     * 処理の答えに従って終えた（確定した・巻き戻した・確かめた）。
     *
     * @param value 処理の結果
     * @param <T> 処理の結果の型
     */
    record Done<T>(T value) implements TargetWriteOutcome<T> {

        /** 値の型の単純名だけを出す（値そのものを出さない。{@code TraceAspect} が service の層の戻り値を TRACE に出すため）。 */
        @Override
        public String toString() {
            return "Done[valueType="
                    + (value == null ? "null" : value.getClass().getSimpleName()) + "]";
        }
    }

    /**
     * 対象DB の書き込みの接続の設定が無い。処理は呼んでいない。
     *
     * @param <T> 処理の結果の型
     */
    record NotConfigured<T>() implements TargetWriteOutcome<T> {}

    /**
     * 接続を借りられない（書き込みの文は送っていない）。処理は呼んでいない。
     *
     * @param sqlState SQLState（無ければ null）
     * @param vendorCode 業者のエラーの番号（無ければ null）
     * @param exceptionClass 例外のクラスの名前
     * @param <T> 処理の結果の型
     */
    record Unavailable<T>(String sqlState, Integer vendorCode, String exceptionClass)
            implements TargetWriteOutcome<T> {}

    /**
     * 口が受け持つ段（確定・処理の想定外の例外）で失敗した。
     *
     * @param failure 失敗
     * @param <T> 処理の結果の型
     */
    record Failed<T>(TargetWriteFailure failure) implements TargetWriteOutcome<T> {

        /** 失敗が必須であることを確かめる。 */
        public Failed {
            Objects.requireNonNull(failure, "failure は必須です");
        }
    }
}
