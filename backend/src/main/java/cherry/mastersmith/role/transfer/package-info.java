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
/**
 * 権限の YAML の受け渡しの純粋な部品（{@code logical-components.md} の L7、FS の 2.13〜2.15、BR9）。木の検証と誤りの一覧
 * （{@link cherry.mastersmith.role.transfer.RoleTransferReader}）、書き出しの文字列（{@link cherry.mastersmith.role.transfer.RoleTransferWriter}）、
 * 変わる点の一覧（{@link cherry.mastersmith.role.transfer.TransferPlanner}）、指紋（{@link cherry.mastersmith.role.transfer.TransferFingerprint}）。
 *
 * <p>DB に触れず（{@code role.repository}・{@code role.store}・{@code EntityManager}・{@code JdbcTemplate} を使わない）、DSL の読み込みの
 * 部品（{@code dsl.parse}）にも依存しない。使うのは Jackson の木と {@code role.domain}、{@code dsl.service} の位置を引く口
 * （{@code YamlPositions}）と読み込みの拒否の区分だけ（{@code RoleBoundaryArchitectureTest}）。
 *
 * <p>メソッドの呼び出しの追跡（{@code TraceAspect}）の対象の層の外の用途名の下位パッケージ。{@code role.service} との受け渡しは、
 * {@code toString} で中身（ロールの名前・対象の名前・値）を伏せ、大きさと件数だけを出す型（{@link cherry.mastersmith.role.transfer.RoleTransferPayload}・
 * {@link cherry.mastersmith.role.transfer.RoleTransferTree}・{@link cherry.mastersmith.role.transfer.RoleTransferDocument}・
 * {@link cherry.mastersmith.role.transfer.TransferPlan}・{@link cherry.mastersmith.role.transfer.TransferErrors}）で行う（NFR1.9、
 * {@code security-design.md} 4.3）。
 */
package cherry.mastersmith.role.transfer;
