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
package cherry.mastersmith.role.store;

/**
 * 権限の YAML の適用で書いた結果の数（{@code reliability-design.md} 2.5）。
 *
 * @param created 作ったロールの数
 * @param replaced 置き換えたロールの数
 * @param settingRows 書いた設定の行の数
 * @param batches 設定の行の書き込みのバッチの数（1 バッチは {@value RoleStore#TRANSFER_BATCH_SIZE} 件まで）
 */
public record TransferWritten(int created, int replaced, int settingRows, int batches) {}
