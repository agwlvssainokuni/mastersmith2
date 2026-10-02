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
package cherry.mastersmith.useradmin.service;

import cherry.mastersmith.useradmin.domain.AdminOperation;

/**
 * 管理者の行と対象の行を排他した後、有効な管理者を数えて判定する直前に呼ぶ待ち合わせの口（Intent 260930-user-admin の U3、BR3.6、
 * {@code reliability-design.md} 6節）。印を付ける・外す・止めるの3つの操作から呼ぶ。
 *
 * <p>本番は何もしない既定の部品（{@link NoOpUserAdminBarrier}）を使い、本番のコードの流れと順は変えない。同時の重なりの結合テストが
 * 差し替えて、重なりを確実に作る。引数は操作の区分と利用者 ID だけで、追跡の TRACE に個人に関する値は出ない。
 */
public interface UserAdminBarrier {

    /**
     * 有効な管理者を数えて判定する直前に呼ばれる（行を排他したトランザクションの中）。
     *
     * @param operation 操作の区分
     * @param targetUserId 対象の利用者 ID
     */
    void beforeCount(AdminOperation operation, long targetUserId);
}
