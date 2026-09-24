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
package cherry.mastersmith.dslmanage.generate;

import cherry.mastersmith.targetdb.domain.UnavailableReason;
import java.util.Arrays;
import java.util.Objects;

/**
 * 既定の DSL の生成の結果（契約 C5 の {@code DefaultDslResult}）。生成できた・対象DB の設定が無い・対象DB に接続できない の
 * どれか。
 *
 * <p>どの場合も、接続先・ユーザー名・パスワード・内部の例外のメッセージを持たない（型にその項目が無い。{@code entities.md}）。
 */
public sealed interface DefaultDslResult {

    /**
     * 生成できた（U2 の検証を通った本文）。
     *
     * @param yamlBytes UTF-8 の YAML の本文
     * @param dslHash 本文の識別（U2 の {@code DslReader.hash} と同じ SHA-256 の16進数）
     */
    record Generated(byte[] yamlBytes, String dslHash) implements DefaultDslResult {

        /** 本文と識別が必須であることを確かめ、本文を写して持つ。 */
        public Generated {
            yamlBytes = Objects.requireNonNull(yamlBytes, "yamlBytes は必須です").clone();
            Objects.requireNonNull(dslHash, "dslHash は必須です");
        }

        /**
         * 本文の写しを返す（持っている配列を外から変えられないようにする）。
         *
         * @return UTF-8 の YAML の本文
         */
        @Override
        public byte[] yamlBytes() {
            return yamlBytes.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Generated that
                    && Arrays.equals(yamlBytes, that.yamlBytes)
                    && dslHash.equals(that.dslHash);
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(yamlBytes) + dslHash.hashCode();
        }

        /** 本文を出さない文字列（ログに本文が出ないようにする。NFR4.9）。 */
        @Override
        public String toString() {
            return "Generated[bytes=" + yamlBytes.length + ", dslHash=" + dslHash + "]";
        }
    }

    /** 対象DB の設定が無い（BR1.1、U1 の {@code Unconfigured}）。 */
    record TargetUnconfigured() implements DefaultDslResult {}

    /**
     * 対象DB に接続できない・応答しない（BR1.1、U1 の {@code Unavailable} の理由をそのまま）。
     *
     * @param reason 理由
     */
    record TargetUnavailable(UnavailableReason reason) implements DefaultDslResult {

        /** 理由が必須であることを確かめる。 */
        public TargetUnavailable {
            Objects.requireNonNull(reason, "reason は必須です");
        }
    }
}
