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
// 表示のタイムゾーンの提供元を固定の値で差し込むテストの補助（テストからだけ使う。Intent 261102-timezone-ui-polish の U2、D-P8・RD5）。
// 管理の画面・プリファレンスの画面を描く画面のテストは、この補助で固定のタイムゾーンか状態を渡す。
import { useMemo, type ReactNode } from 'react'
import {
  DisplayTimeZoneActionsContext,
  DisplayTimeZoneContext,
  type DisplayTimeZoneActions,
  type DisplayTimeZoneState,
} from '../DisplayTimeZoneProvider'

export interface DisplayTimeZoneTestProviderProps {
  /** ready の名前（既定は Asia/Tokyo）。state を渡したときは使わない */
  timeZone?: string
  /** 状態をそのまま渡す（loading・error の確かめ用） */
  state?: DisplayTimeZoneState
  /** 操作の差し替え（既定は何もしない） */
  actions?: Partial<DisplayTimeZoneActions>
  children: ReactNode
}

const NOOP: DisplayTimeZoneActions = { applySaved: () => {}, reload: () => {} }

/** 固定の表示のタイムゾーンを子に渡す。 */
export function DisplayTimeZoneTestProvider({
  timeZone = 'Asia/Tokyo',
  state,
  actions,
  children,
}: DisplayTimeZoneTestProviderProps) {
  const value = useMemo<DisplayTimeZoneState>(
    () => state ?? { status: 'ready', timeZone, source: 'saved' },
    [state, timeZone],
  )
  const merged = useMemo<DisplayTimeZoneActions>(() => ({ ...NOOP, ...actions }), [actions])
  return (
    <DisplayTimeZoneContext.Provider value={value}>
      <DisplayTimeZoneActionsContext.Provider value={merged}>
        {children}
      </DisplayTimeZoneActionsContext.Provider>
    </DisplayTimeZoneContext.Provider>
  )
}
