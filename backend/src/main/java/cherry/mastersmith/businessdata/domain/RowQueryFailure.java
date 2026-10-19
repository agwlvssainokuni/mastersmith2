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

import java.util.Objects;

/**
 * 行の SQL の失敗（logical-components.md の L7）。理由・段階・SQLState・業者のエラーの番号・例外のクラスの名前だけを持ち、例外の
 * メッセージと原因を持たない（BR10.2。PostgreSQL の例外の文には入力の値が入るため）。
 *
 * @param reason 理由
 * @param stage 段階
 * @param sqlState SQLState（無ければ null）
 * @param vendorCode 業者のエラーの番号（無ければ null）
 * @param exceptionClass 例外のクラスの名前
 */
public record RowQueryFailure(
        RowQueryFailureReason reason, RowQueryStage stage, String sqlState, Integer vendorCode, String exceptionClass) {

    /** 必須の値を確かめる。 */
    public RowQueryFailure {
        Objects.requireNonNull(reason, "reason は必須です");
        Objects.requireNonNull(stage, "stage は必須です");
        Objects.requireNonNull(exceptionClass, "exceptionClass は必須です");
    }
}
