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
// 作成・更新の画面の参照の項目の部品の選び方と、選択肢の文（Intent 261029-reference-lookup-cleanup の U3、D5・D6・D12・D13、
// frontend-components.md の 4節）。純粋な関数で、画面で権限を判定せず定義の応答だけで決める。
// - 用途で入力できないカラム（editableOnCreate・editableOnUpdate が偽、更新で省略した値）を reference.status より先に見る
//   （U1 の R-05 の引き継ぎ。U1 はそのカラムに参照先の権限によらず NO_PERMISSION を返す）。
// - ラベル・値は文字のまま返す（HTML にしない。D34）。
import type { ColumnDefinition, ReferenceOption, ReferenceStatus } from '../businessDataTypes'
import { isEditable, type FormMode } from '../formDiff'

/** 入力不可の参照の項目の理由 */
export type ReadonlyReason = 'permission' | 'too-many'

/** カラムの表示の種類（D5 の順） */
export type FieldKind =
  /** 作成で入力できないカラム（出さない） */
  | { kind: 'hidden' }
  /** 更新で入力できないカラム（既存の名前と値の表示） */
  | { kind: 'locked' }
  /** 参照でないカラム（既存の入力の部品） */
  | { kind: 'plain' }
  /** 参照の入力不可の項目（ReadonlyReferenceField） */
  | { kind: 'reference-readonly'; reason: ReadonlyReason }
  /** REFERENCE の選択肢（ReferenceSelectField） */
  | { kind: 'reference-select' }
  /** LOOKUP のピッカー（LookupField） */
  | { kind: 'lookup' }

/**
 * 入力不可の理由（TOO_MANY は候補が多い、ほかは権限不足。知らない値は写しが NO_PERMISSION に倒している。D3・D12）。
 *
 * @param status 参照の状態
 */
export function readonlyReason(status: ReferenceStatus): ReadonlyReason {
  return status === 'TOO_MANY' ? 'too-many' : 'permission'
}

/**
 * カラムの表示の種類を決める（D5。上から最初に当たるもの）。
 *
 * @param column カラムの定義
 * @param mode 作成か更新か
 * @param truncated 更新で先頭だけを受けたカラム
 */
export function chooseFieldKind(
  column: ColumnDefinition,
  mode: FormMode,
  truncated: readonly string[] = [],
): FieldKind {
  if (!isEditable(column, mode, truncated)) {
    return mode === 'create' ? { kind: 'hidden' } : { kind: 'locked' }
  }
  const reference = column.reference ?? null
  if (reference === null) {
    return { kind: 'plain' }
  }
  if (reference.status !== 'AVAILABLE') {
    return { kind: 'reference-readonly', reason: readonlyReason(reference.status) }
  }
  return reference.kind === 'REFERENCE' ? { kind: 'reference-select' } : { kind: 'lookup' }
}

/** 参照の入力不可の項目か（送る本文と画面の検証から外す。D15） */
export function isReadonlyReference(
  column: ColumnDefinition,
  mode: FormMode,
  truncated: readonly string[] = [],
): boolean {
  return chooseFieldKind(column, mode, truncated).kind === 'reference-readonly'
}

/** 必須のカラムか（NULL を許さない、または required。U2 の引き継ぎ） */
export function isRequiredColumn(column: ColumnDefinition): boolean {
  return column.nullable === false || column.required === true
}

/**
 * 作成の画面で値を入れられない必須の参照の項目か（D13 の固定の誤りを出す。更新では出さない。D14）。
 *
 * @param column カラムの定義
 * @param mode 作成か更新か
 * @param truncated 更新で先頭だけを受けたカラム
 */
export function isBlockedRequired(
  column: ColumnDefinition,
  mode: FormMode,
  truncated: readonly string[] = [],
): boolean {
  return (
    mode === 'create' && isReadonlyReference(column, mode, truncated) && isRequiredColumn(column)
  )
}

/**
 * 入力不可の参照の項目の名前の集合（diffForm・hasFormChanges・validateForm に渡す。X-5）。
 *
 * @param columns 詳細のカラム
 * @param mode 作成か更新か
 * @param truncated 更新で先頭だけを受けたカラム
 */
export function lockedReferenceColumns(
  columns: readonly ColumnDefinition[],
  mode: FormMode,
  truncated: readonly string[] = [],
): ReadonlySet<string> {
  return new Set(
    columns
      .filter((column) => isReadonlyReference(column, mode, truncated))
      .map((column) => column.column),
  )
}

/**
 * 選択肢・欄の文（「ラベル (値)」、ラベルが null なら値だけ。D6・D16）。
 *
 * @param option 値とラベル
 */
export function optionText(option: ReferenceOption): string {
  return option.label === null ? option.value : `${option.label} (${option.value})`
}
