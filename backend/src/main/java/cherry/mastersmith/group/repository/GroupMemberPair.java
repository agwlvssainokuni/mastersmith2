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
package cherry.mastersmith.group.repository;

/**
 * グループとメンバーの組の投影（{@code memberUserIds} の1回の読み取り。BR6.5）。グループの表から左の外部結合で読むため、メンバーが 0 の
 * グループは利用者 ID が null の行が1つになり、存在しないグループは行が無い。
 *
 * @param groupId グループの ID
 * @param userId 利用者 ID（メンバーが 0 のグループでは null）
 */
public record GroupMemberPair(long groupId, Long userId) {}
