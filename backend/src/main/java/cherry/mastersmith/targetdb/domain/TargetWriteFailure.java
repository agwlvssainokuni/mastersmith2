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
 * 業務データの書き込みの口が受け持つ段の失敗（Intent 261016-business-data-views の U2、security-design.md の 2節）。段・SQLState・
 * 業者のエラーの番号・例外のクラスの名前と、時間切れ・通信の断絶だったかだけを持ち、例外のメッセージと原因を持たない（例外の文には
 * 入力の値が入りうるため）。
 *
 * @param stage 失敗した段
 * @param sqlState SQLState（無ければ null）
 * @param vendorCode 業者のエラーの番号（無ければ null）
 * @param exceptionClass 例外のクラスの名前
 * @param interrupted 時間切れ・通信の断絶（{@code SQLTimeoutException}・{@code SocketTimeoutException}・57014・70100・類 08）なら
 *     true
 */
public record TargetWriteFailure(
        TargetWriteStage stage, String sqlState, Integer vendorCode, String exceptionClass, boolean interrupted) {

    /** 必須の値を確かめる。 */
    public TargetWriteFailure {
        Objects.requireNonNull(stage, "stage は必須です");
        Objects.requireNonNull(exceptionClass, "exceptionClass は必須です");
    }
}
