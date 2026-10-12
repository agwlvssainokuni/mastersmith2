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
//
// 利用者の直接の割り当てを外すときの確かめの判定（Intent 261004-role-menu の U6 の D19、AC2.2.9、NFR6.2）。純粋な関数。
// 利用者のロール（出どころつき）から、外すとそのロールが無くなるか（出どころが直接だけか）と、残りのロールの最初
// （ロールの ID の最も小さいもの。role の作業ロールの読み替えと同じ）を求める。管理の API は他人の作業ロールを返さないため、
// 画面は「作業ロールがこのロールなら」の条件つきで示す（機能設計 9節 (c)）。
import type { UserRole } from './api/types'

/** 外したときの影響 */
export interface RemovalImpact {
  /** 外すとこのロールが無くなるか（出どころに直接以外があれば偽） */
  losesRole: boolean
  /** 無くなるときの残りのロールの最初（ID の最小）。残りが無い・無くならないときは null */
  nextWorkRole: { roleId: number; name: string } | null
}

/** 利用者のロールから、このロールの直接の割り当てを外したときの影響を求める。 */
export function removalImpact(roles: readonly UserRole[], roleId: number): RemovalImpact {
  const target = roles.find((role) => role.roleId === roleId)
  const losesRole =
    target !== undefined && target.sources.every((source) => source.kind === 'DIRECT')
  if (!losesRole) {
    return { losesRole: false, nextWorkRole: null }
  }
  const remaining = roles
    .filter((role) => role.roleId !== roleId)
    .reduce<UserRole | undefined>(
      (min, role) => (min === undefined || role.roleId < min.roleId ? role : min),
      undefined,
    )
  return {
    losesRole: true,
    nextWorkRole:
      remaining === undefined ? null : { roleId: remaining.roleId, name: remaining.name },
  }
}
