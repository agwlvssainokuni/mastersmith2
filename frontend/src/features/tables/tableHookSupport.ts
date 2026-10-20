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
// 業務データの画面のフックが共有する小さな口（Intent 261016-business-data-views の U3 の L7、F8・P-9）。
// - 作業ロールの読み込みが済んだか（済むまで最初の要求を送らない。P-9）と、今の作業ロールの ID。
// - 失敗を読み取りの失敗の種類に写す（通信の失敗は network）。
// - 403 の後に業務のメニューと作業ロールを読み直す（F8 の 2。業務データの API は送り直さない。U3-NFR8.7）。
// - 道の引数（schema・table・item）を読み、名前が変わらない限り同じ物を返す（フックの依存を安定させる）。
import { useCallback, useMemo } from 'react'
import { useBusinessNavigation } from '../../app/navigation/BusinessNavigationProvider'
import {
  readTablePlaceholderParams,
  type TableRef,
} from '../../app/navigation/tablePlaceholderPath'
import { useWorkRole } from '../../app/work-role/WorkRoleProvider'
import { parseBusinessProblem } from './businessDataParse'
import type { BusinessProblem } from './businessDataTypes'
import { readProblemKind, type ReadProblemKind } from './problemMessage'

/** 読み取りの失敗 */
export interface ReadFailure {
  kind: ReadProblemKind
  /** サーバーの応答の誤り（通信の失敗は undefined） */
  problem: BusinessProblem | undefined
}

/** API の失敗を読み取りの失敗に写す */
export function toReadFailure(error: unknown): ReadFailure {
  const problem = parseBusinessProblem(error)
  return { kind: readProblemKind(problem), problem }
}

/** 作業ロールの読み込みが済んだかと、今の作業ロールの ID */
export interface WorkRoleKey {
  /** 初めての読み込みが済んだか（読み込みの失敗・未ログインを含めて、待たなくてよいか） */
  settled: boolean
  /** 今の作業ロールの ID（無ければ null） */
  roleId: number | null
}

/** 作業ロールの読み込みの状態と ID を返す */
export function useWorkRoleKey(): WorkRoleKey {
  const workRole = useWorkRole()
  return { settled: workRole.status !== 'loading', roleId: workRole.current?.roleId ?? null }
}

/** 403 の後に業務のメニューと作業ロールを読み直す関数を返す（F8 の 2） */
export function useForbiddenFollowUp(): () => void {
  const business = useBusinessNavigation()
  const workRole = useWorkRole()
  const { reload: reloadBusiness } = business
  const { reload: reloadWorkRole } = workRole
  return useCallback(() => {
    reloadBusiness()
    reloadWorkRole()
  }, [reloadBusiness, reloadWorkRole])
}

/** 道の引数のスキーマ名とテーブル名を、名前が変わらない限り同じ物にする */
export function useTableRefFromSearch(search: string): {
  ref: TableRef | null
  itemId: string | null
} {
  const { ref, itemId } = readTablePlaceholderParams(search)
  const schemaName = ref?.schemaName
  const tableName = ref?.tableName
  const stable = useMemo(
    () => (schemaName === undefined || tableName === undefined ? null : { schemaName, tableName }),
    [schemaName, tableName],
  )
  return { ref: stable, itemId }
}
