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
// 行の操作のメニューに出す項目と押せなさを決める純粋な関数（functional-spec.md の W4・D5・D6、frontend-components.md の
// 4.1）。行の admin・suspended・resettable・self の4つの値だけから決め、ほかの値（locked など）や画面の外の状態を見ない。
// 並びは W4 の表の上からの順。画面で押せない形にするのは表示だけで、サーバーの拒否（U3）の代わりにしない（NFR1.1）。

/** 行の操作の種類の値 */
export const ROW_ACTION_KINDS = [
  'grantAdmin',
  'revokeAdmin',
  'suspend',
  'resume',
  'resetFailures',
  'editProfile',
] as const

/** 行の操作の種類 */
export type RowActionKind = (typeof ROW_ACTION_KINDS)[number]

/** 確かめの表示（S3）を出す操作（editProfile を除く5つ） */
export type ConfirmActionKind = Exclude<RowActionKind, 'editProfile'>

/** 押せない理由の値 */
export const DISABLED_REASONS = ['selfRevoke', 'selfSuspend'] as const

/** 押せない理由（自分の行の「印を外す」「止める」） */
export type DisabledReason = (typeof DISABLED_REASONS)[number]

/** メニューの1項目 */
export interface RowAction {
  kind: RowActionKind
  /** 押せない形で出すときの理由（押せるときは無い） */
  disabledReason?: DisabledReason
}

/** 項目を決めるのに使う行の値（この4つだけ） */
export interface RowActionInput {
  admin: boolean
  suspended: boolean
  resettable: boolean
  self: boolean
}

/**
 * 行の4つの値から、メニューの項目を W4 の表の順で返す。
 * 停止中の行には印の項目と「利用を止める」を出さず「停止を解く」を出す。ロックの解除は resettable だけで決める（M2 B）。
 * 自分の行の「印を外す」「止める」は消さずに押せない形で出す（D6）。
 */
export function rowActions({ admin, suspended, resettable, self }: RowActionInput): RowAction[] {
  const actions: RowAction[] = []
  if (!suspended) {
    if (admin) {
      actions.push(
        self ? { kind: 'revokeAdmin', disabledReason: 'selfRevoke' } : { kind: 'revokeAdmin' },
      )
    } else {
      actions.push({ kind: 'grantAdmin' })
    }
    actions.push(self ? { kind: 'suspend', disabledReason: 'selfSuspend' } : { kind: 'suspend' })
  } else {
    actions.push({ kind: 'resume' })
  }
  if (resettable) {
    actions.push({ kind: 'resetFailures' })
  }
  actions.push({ kind: 'editProfile' })
  return actions
}
