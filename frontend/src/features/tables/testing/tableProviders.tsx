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
// 業務データの画面のテストの骨組みの Provider（テストからだけ使う。unit-test-instructions.md 6節）。
// 作業ロールと業務のメニューは偽の API で包み、呼ばれた回数を数える。作業ロールは答えを差し替えて roleId の変化を起こせる。
import type { ReactNode } from 'react'
import { BusinessNavigationProvider } from '../../../app/navigation/BusinessNavigationProvider'
import {
  fakeNavigationLoad,
  fakeWorkRoleApi,
  SALES,
  type Deferred,
} from '../../../app/testing/frameTestSupport'
import { WorkRoleProvider } from '../../../app/work-role/WorkRoleProvider'
import type { WorkRoleState } from '../../../app/work-role/workRoleApi'

/** 作業ロールが1つ（営業）の答え */
export const ONE_ROLE: WorkRoleState = { roles: [SALES], current: SALES }

/** 骨組みの偽の Provider を作る */
export function tableFrame(
  roles: (WorkRoleState | Deferred<WorkRoleState> | Error)[] = [ONE_ROLE],
) {
  const role = fakeWorkRoleApi(roles)
  const nav = fakeNavigationLoad([{ items: [], emptyReason: 'NOT_CONFIGURED' }])
  function Frame({ children }: { children: ReactNode }) {
    return (
      <WorkRoleProvider api={role.api}>
        <BusinessNavigationProvider load={nav.load}>{children}</BusinessNavigationProvider>
      </WorkRoleProvider>
    )
  }
  return { Frame, roleCalls: role.calls, navCalls: nav.calls }
}
