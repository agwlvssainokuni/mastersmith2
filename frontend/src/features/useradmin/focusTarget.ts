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
// フォーカスの行き先の型と、一覧を読み直した後の行き先を決める純粋な関数（functional-spec.md の W12、
// frontend-components.md の 2.1）。操作・保存の後は同じ userId の行の「操作」、その利用者が今のページに見えなくなったら
// 一覧の見出し。「もう一度読み込む」の後にまた失敗したら「もう一度読み込む」へ。

/** 求めるフォーカスの行き先 */
export type FocusTarget =
  /** その行の「操作」 */
  | { kind: 'row'; userId: number }
  /** 一覧の見出し（h2） */
  | { kind: 'heading' }
  /** 読み込みの失敗の「もう一度読み込む」 */
  | { kind: 'retry' }

/**
 * 読み直しの後に実際に当てる行き先を決める。
 *
 * @param target 求めた行き先（無ければ null）
 * @param rowIds 読み直した後の今のページの行の userId（空の一覧なら空）
 * @returns 当てる行き先（求めた行き先が無ければ null）
 */
export function focusAfterReload(
  target: FocusTarget | null,
  rowIds: readonly number[],
): FocusTarget | null {
  if (target === null) {
    return null
  }
  if (target.kind === 'row') {
    return rowIds.includes(target.userId) ? target : { kind: 'heading' }
  }
  return target
}
