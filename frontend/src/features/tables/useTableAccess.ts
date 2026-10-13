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
// S2 の置き場の問い合わせの状態（Intent 261004-role-menu の U7 の L9、functional-spec.md の D21・D22・4.3、
// frontend-components.md の 6節、NFR2.9）。
// - 引数（schema・table）・作業ロール（current の roleId と切り替えの回数）が変わるたびに問い合わせる。引数が無い・空なら
//   要求を送らずに forbidden。
// - 世代で古い答えを捨てる。問い合わせの間は前の表示を残す（loading の印だけを立てる）。
// - 200 は available、403・400 は forbidden（表示名を持たない）、ほかは load-error。403 を受けたら業務のメニューと作業ロール
//   を読み直す（D4 の (c)・D22）。401 は ApiClient とログインの状態に任せる（load-error として扱う）。
import { useCallback, useEffect, useEffectEvent, useRef, useState } from 'react'
import { useBusinessNavigation } from '../../app/navigation/BusinessNavigationProvider'
import type { TableRef } from '../../app/navigation/tablePlaceholderPath'
import { useWorkRole } from '../../app/work-role/WorkRoleProvider'
import { getTableAccess, type TableAccess } from './tableAccessApi'

/** 置き場の画面の状態（4.3） */
export type TableAccessView = 'loading' | 'available' | 'forbidden' | 'load-error'

/** 置き場の問い合わせの状態 */
export interface TableAccessState {
  view: TableAccessView
  /** 見てよいときだけ持つ */
  access: TableAccess | null
  /** 問い合わせの最中か（前の表示を残す） */
  pending: boolean
  /** もう一度問い合わせる */
  retry: () => void
}

interface LoadState {
  view: TableAccessView
  access: TableAccess | null
  /** この答えを得た問い合わせの鍵（今の鍵と違えば問い合わせの最中） */
  key: string | null
}

function statusOf(error: unknown): number | undefined {
  const value = error as { kind?: unknown; status?: unknown } | null | undefined
  return typeof value === 'object' &&
    value !== null &&
    value.kind === 'response' &&
    typeof value.status === 'number'
    ? value.status
    : undefined
}

/**
 * 置き場の問い合わせの状態を返す。
 *
 * @param ref 道の引数のスキーマ名とテーブル名（無ければ null）
 * @param load 問い合わせの関数（テストで差し替える）
 */
export function useTableAccess(
  ref: TableRef | null,
  load: (ref: TableRef) => Promise<TableAccess> = getTableAccess,
): TableAccessState {
  const business = useBusinessNavigation()
  const workRole = useWorkRole()
  const [state, setState] = useState<LoadState>({ view: 'loading', access: null, key: null })
  const [attempt, setAttempt] = useState(0)
  const generation = useRef(0)

  const query = useEffectEvent((target: TableRef, key: string): void => {
    generation.current += 1
    const mine = generation.current
    load(target).then(
      (access) => {
        if (generation.current === mine) {
          setState({ view: 'available', access, key })
        }
      },
      (error: unknown) => {
        if (generation.current !== mine) {
          return
        }
        const status = statusOf(error)
        if (status === 403 || status === 400) {
          setState({ view: 'forbidden', access: null, key })
          if (status === 403) {
            business.reload()
            workRole.reload()
          }
        } else {
          setState({ view: 'load-error', access: null, key })
        }
      },
    )
  })

  const schemaName = ref?.schemaName
  const tableName = ref?.tableName
  const roleId = workRole.current?.roleId
  const { revision } = workRole
  // 作業ロールの初めての読み込みの間は問い合わせを待つ（読み終えた時点の作業ロールで1回だけ問い合わせる）。
  const roleSettled = workRole.status !== 'loading'
  const key = JSON.stringify([schemaName, tableName, roleSettled, roleId, revision, attempt])

  useEffect(() => {
    if (!roleSettled) {
      return
    }
    if (schemaName === undefined || tableName === undefined) {
      // 引数が無いときは要求を送らない。届いていない答えは捨てる。
      generation.current += 1
      return
    }
    query({ schemaName, tableName }, key)
  }, [schemaName, tableName, roleSettled, key])

  const retry = useCallback(() => setAttempt((value) => value + 1), [])

  if (ref === null) {
    return { view: 'forbidden', access: null, pending: false, retry }
  }
  return { view: state.view, access: state.access, pending: state.key !== key, retry }
}
