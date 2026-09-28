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
// 一覧を読み直した後のフォーカスの行き先（frontend-components.md の 3.3、functional-spec.md の W7〜W9）。
// 求めた行き先と、今の行・招待を使えるか・行の処理中から、実際に当てる所を決める純粋な関数。
// 行があり「送り直す」を押せない → その行のメールアドレスのセルの受け口、行が無い → 一覧の見出し、一覧が空 → 空の表示の文。

/** 求めるフォーカスの行き先 */
export type FocusTarget =
  | { kind: 'resend'; invitationId: number }
  | { kind: 'email'; invitationId: number }
  | { kind: 'heading' }
  | { kind: 'empty' }

/** 行き先を決めるための今の一覧の様子 */
export interface FocusContext {
  /** 今のページの行の invitationId */
  rowIds: readonly number[]
  /** 全件数（0 なら空の表示） */
  total: number
  /** 招待を使えるか（使えないと「送り直す」を押せない） */
  invitationEnabled: boolean
  /** 行の処理中の invitationId */
  resendingIds: ReadonlySet<number>
}

function headingOrEmpty(context: FocusContext): FocusTarget {
  return context.total <= 0 ? { kind: 'empty' } : { kind: 'heading' }
}

/**
 * 実際にフォーカスを当てる所を決める。
 *
 * @param target 求めた行き先
 * @param context 今の一覧の様子
 */
export function resolveFocusTarget(target: FocusTarget, context: FocusContext): FocusTarget {
  switch (target.kind) {
    case 'resend': {
      if (!context.rowIds.includes(target.invitationId)) {
        return headingOrEmpty(context)
      }
      const pressable = context.invitationEnabled && !context.resendingIds.has(target.invitationId)
      return pressable ? target : { kind: 'email', invitationId: target.invitationId }
    }
    case 'email':
      return context.rowIds.includes(target.invitationId) ? target : headingOrEmpty(context)
    case 'heading':
    case 'empty':
      return headingOrEmpty(context)
  }
}
