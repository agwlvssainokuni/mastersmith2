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
 * スキーマの読み取りの結果（契約 C1・C3 の {@code TargetSchemaResult}）。成功・設定が無い・接続できない のどれか。
 *
 * <p>どの場合も、接続先・ユーザー名・パスワード・内部の例外のメッセージを持たない（型にその項目が無い）。
 */
public sealed interface TargetSchemaResult {

    /**
     * 成功。
     *
     * @param schema スキーマの写し
     */
    record Success(TargetSchema schema) implements TargetSchemaResult {

        /** 写しが必須であることを確かめる。 */
        public Success {
            Objects.requireNonNull(schema, "schema は必須です");
        }
    }

    /** 対象DB の設定が無い（必須の項目の欠け・種類が対応外を含む）。 */
    record Unconfigured() implements TargetSchemaResult {}

    /**
     * 対象DB に接続できない・応答しない。
     *
     * @param reason 理由
     */
    record Unavailable(UnavailableReason reason) implements TargetSchemaResult {

        /** 理由が必須であることを確かめる。 */
        public Unavailable {
            Objects.requireNonNull(reason, "reason は必須です");
        }
    }

    /**
     * 成功の結果を作る。
     *
     * @param schema スキーマの写し
     * @return 成功
     */
    static TargetSchemaResult success(TargetSchema schema) {
        return new Success(schema);
    }

    /**
     * 設定が無い結果を作る。
     *
     * @return 設定が無い
     */
    static TargetSchemaResult unconfigured() {
        return new Unconfigured();
    }

    /**
     * 接続できない結果を作る。
     *
     * @param reason 理由
     * @return 接続できない
     */
    static TargetSchemaResult unavailable(UnavailableReason reason) {
        return new Unavailable(reason);
    }
}
