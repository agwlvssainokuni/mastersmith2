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
 * グループごとのメンバーの数の投影（一覧の1ページの分を {@code GROUP BY} の1回で読む。BR7.3・NFR2.6）。メンバーが 0 のグループは行が
 * 無い（呼び出し元が 0 で埋める）。
 *
 * @param groupId グループの ID
 * @param members メンバーの数
 */
public record GroupMemberCount(long groupId, long members) {}
