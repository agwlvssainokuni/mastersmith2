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
// 作成・更新のフォームの入力の値と、送る本文の写し（Intent 261016-business-data-views の U3 の L5、D19・D20、SM1〜SM3・SM7、
// AC2.2.2・AC2.2.8〜AC2.2.11）。純粋な関数で、値は文字のまま扱い数に直さない（BIGINT・DECIMAL の桁を失わない）。
// - 入力の値は文字で持つ。真偽値の入力は 'true'・'false'、NULL の初めの値は空の文字（外れた状態で描く）。
//   日時の欄の値は日時の決まり（下）のとおり。
// - 変更の有無は、初めに入力へ入れた値と今の値を比べて決める（DB の生の値とは比べない。D20）。
// - 作成: 入力にするカラムのうち、空でない値だけを送る（空のままのカラムは送らず、対象DB の既定値に任せる）。
//   必須の真偽値の入力は 'false' で始め、送る（チェックを外したままで送れるようにする。generation-notes の B6 の差）。
// - 更新: 変えたカラムだけを送る。空にしたカラムは、文字の型（STRING）なら ''、ほかの型なら null。
// - masked（password）: 空で始め、値を入れたときだけ送る（作成・更新とも）。
// - 入力不可の参照の項目（Intent 261029-reference-lookup-cleanup の U3、D15・X-5）は、任意の引数 lockedColumns で渡し、
//   入力できないカラムと同じに扱う（本文に入れず、変更の有無にも数えない）。渡さなければ今と同じ結果。
// - 日時（Intent 261102-timezone-ui-polish の U4、計画 Step 9、D13〜D17・D21・D22・D35）:
//   - 時点を表す型（DATETIME_TZ）の初めの値は、フォームを開いたときに固定した表示のタイムゾーンの壁時計
//     （formatInstantForInput。端数は桁のまま、読めない値は元の文字）。送る値は resolveDateTimeInput の ok の時点だけ。
//   - 時差を持たない日時（DATETIME）の初めの値は区切りの T を空白にした文字（端数を落とさない）。送る値は空白を T にした文字。
//   - 送るのは今と同じく「作成は空でない欄・更新は初めの文字から変えた欄」だけ（変えていない時点は送らないため、元の時点が保たれる）。
//   - 判定が ok にならない欄（読めない・存在しない時刻・2択が未選択・表示のタイムゾーンが無い）は本文に入れない。保存の前に
//     validateForm（dateTime の引数）が誤りにする。表示のタイムゾーン・2択の選択は任意の最後の引数 time で渡す
//     （渡さなければ表示のタイムゾーンが無い扱い。時点を表す型の欄を変換しない）。
//   - 保存の押下では、resolveFormDateTimes の結果を diffForm の最後の引数に渡して使い回し、欄ごとの変換を1回にする
//     （NFR 設計の performance-design.md 2.2）。
import {
  formatInstantForInput,
  formatLocalDateTimeForInput,
} from '../../shared/format/dateTimeInput'
import type { OverlapChoice } from '../../shared/format/timeZone'
import type { CellValue, ColumnDefinition, RowValues } from './businessDataTypes'
import {
  dateTimeKindOf,
  resolveDateTimeInput,
  type DateTimeKind,
  type DateTimeResolution,
} from './resolveDateTimeInput'

/** フォームの種類 */
export type FormMode = 'create' | 'update'

/** フォームの入力の値（カラムの名前ごと） */
export type FormInputs = Readonly<Record<string, string>>

/** 入力不可の参照の項目が無いとき（既定の引数） */
export const NO_LOCKED: ReadonlySet<string> = new Set()

/** 欄ごとの夏時間の2択の選択（未選択は null か項目なし。D21） */
export type OverlapChoices = Readonly<Record<string, OverlapChoice | null>>

/** フォームの日時の欄の受け渡し（D12・D21） */
export interface FormTime {
  /** フォームを開いたときに固定した表示のタイムゾーンの名前（読み込みの失敗は null） */
  readonly timeZone: string | null
  /** 欄ごとの2択の選択 */
  readonly overlapChoices: OverlapChoices
}

/** 表示のタイムゾーンが無いとき（既定の引数。時点を表す型の欄を変換しない） */
export const NO_FORM_TIME: FormTime = { timeZone: null, overlapChoices: {} }

/**
 * 日時の文字の欄（DateTimeField）にするカラムの種類（Intent 261102-timezone-ui-polish の U4、D13・D14・D33）。時点を表す型・
 * 時差を持たない日時で、部品が文字の入力（text・textarea・datetime か指定なし）のときだけ。参照の項目と、選択肢の部品
 * （select・radio・lookup）は今のまま変換しない（選択肢の値・参照の値は返された文字のまま送る）。
 *
 * @param column カラムの定義
 */
export function dateTimeInputKind(column: ColumnDefinition): DateTimeKind | null {
  const kind = dateTimeKindOf(column.valueType)
  if (kind === null || (column.reference !== undefined && column.reference !== null)) {
    return null
  }
  const part = column.formPart ?? null
  return part === null || part === 'text' || part === 'textarea' || part === 'datetime'
    ? kind
    : null
}

/** 真偽値の入力（checkbox・switch）か */
export function isBooleanInput(column: ColumnDefinition): boolean {
  return column.formPart === 'checkbox' || column.formPart === 'switch'
}

/** そのフォームで入力にするカラムか（作成は editableOnCreate、更新は editableOnUpdate で、省略した値でない） */
export function isEditable(
  column: ColumnDefinition,
  mode: FormMode,
  truncatedColumns: readonly string[] = [],
): boolean {
  if (mode === 'create') {
    return column.editableOnCreate
  }
  return column.editableOnUpdate && !truncatedColumns.includes(column.column)
}

/**
 * DB の値を入力の初めの値にする（D16）。
 *
 * @param column カラムの定義
 * @param value 詳細の値（無い・null は NULL）
 * @param timeZone フォームの表示のタイムゾーン（時点を表す型に使う。無ければ元の文字のまま）
 */
export function toInputValue(
  column: ColumnDefinition,
  value: CellValue | undefined,
  timeZone: string | null = null,
): string {
  if (column.masked || value === null || value === undefined) {
    return ''
  }
  switch (dateTimeInputKind(column)) {
    case 'instant':
      return timeZone === null ? value : formatInstantForInput(value, timeZone)
    case 'local':
      return formatLocalDateTimeForInput(value)
    default:
      return value
  }
}

/**
 * 日時の欄の入力を判定する（日時の欄でなければ null）。用途は作成・更新（form）。
 *
 * @param column カラムの定義
 * @param input 入力の文字（空でないもの）
 * @param time 表示のタイムゾーンと2択の選択
 */
export function resolveFormDateTime(
  column: ColumnDefinition,
  input: string,
  time: FormTime,
): DateTimeResolution | null {
  const kind = dateTimeInputKind(column)
  if (kind === null) {
    return null
  }
  const choice = Object.hasOwn(time.overlapChoices, column.column)
    ? time.overlapChoices[column.column]
    : null
  return resolveDateTimeInput({
    text: input,
    kind,
    timeZone: time.timeZone,
    purpose: 'form',
    overlapChoice: choice ?? null,
  })
}

/**
 * フォームの初めの入力の値を作る（入力にするカラムだけ）。
 *
 * @param columns 詳細のカラム
 * @param mode 作成か更新か
 * @param values 更新のときの今の値（作成では渡さない）
 * @param truncatedColumns 先頭だけを受けたカラム（更新では読み取りにする）
 * @param timeZone フォームを開いたときに固定した表示のタイムゾーン（時点を表す型の初めの値に使う。D12・D16）
 */
export function initialInputs(
  columns: readonly ColumnDefinition[],
  mode: FormMode,
  values: Readonly<Record<string, CellValue>> = {},
  truncatedColumns: readonly string[] = [],
  timeZone: string | null = null,
): FormInputs {
  const entries: [string, string][] = []
  for (const column of columns) {
    if (!isEditable(column, mode, truncatedColumns)) {
      continue
    }
    if (mode === 'create') {
      entries.push([
        column.column,
        isBooleanInput(column) && column.required === true ? 'false' : '',
      ])
    } else {
      const value = Object.hasOwn(values, column.column) ? values[column.column] : undefined
      entries.push([column.column, toInputValue(column, value, timeZone)])
    }
  }
  return Object.fromEntries(entries)
}

/** カラムの名前ごとの日時の欄の判定の結果（resolveFormDateTimes の結果） */
export type FormDateTimeResolutions = Readonly<Record<string, DateTimeResolution>>

/**
 * 空でない入力の値を送る形にする。日時の欄は判定の ok の値だけ（ok でなければ undefined。本文に入れない）。
 * 判定の結果（resolutions）にその欄があれば使い回し、変換し直さない（NFR 設計の performance-design.md 2.2）。
 */
function toWireValue(
  column: ColumnDefinition,
  input: string,
  time: FormTime,
  resolutions: FormDateTimeResolutions | undefined,
): string | undefined {
  if (dateTimeInputKind(column) === null) {
    return input
  }
  const resolved =
    resolutions !== undefined && Object.hasOwn(resolutions, column.column)
      ? resolutions[column.column]
      : resolveFormDateTime(column, input, time)
  if (resolved === null) {
    return input
  }
  return resolved.kind === 'ok' ? resolved.wire : undefined
}

function inputOf(inputs: FormInputs, column: string): string {
  return Object.hasOwn(inputs, column) ? inputs[column] : ''
}

/**
 * 送る本文の値を作る（D20）。変えたカラムが無ければ空の組を返す（保存を押せない）。
 *
 * @param columns 詳細のカラム
 * @param mode 作成か更新か
 * @param initial 初めの入力の値
 * @param current 今の入力の値
 * @param truncatedColumns 先頭だけを受けたカラム
 * @param lockedColumns 入力不可の参照の項目（X-5）
 * @param time 表示のタイムゾーンと2択の選択（日時の欄の送る値に使う）
 * @param resolutions 同じ入力で先に求めた日時の欄の判定の結果（resolveFormDateTimes の結果）。渡すと、そこにある欄は
 *   変換し直さずに ok の送る値を使い回す（保存の押下で欄ごとの変換を1回にする。performance-design.md 2.2）。
 *   渡さなければ今と同じく、ここで変換する。
 */
export function diffForm(
  columns: readonly ColumnDefinition[],
  mode: FormMode,
  initial: FormInputs,
  current: FormInputs,
  truncatedColumns: readonly string[] = [],
  lockedColumns: ReadonlySet<string> = NO_LOCKED,
  time: FormTime = NO_FORM_TIME,
  resolutions?: FormDateTimeResolutions,
): RowValues {
  const entries: [string, CellValue][] = []
  const pushWire = (column: ColumnDefinition, now: string): void => {
    const wire = toWireValue(column, now, time, resolutions)
    if (wire !== undefined) {
      entries.push([column.column, wire])
    }
  }
  for (const column of columns) {
    if (!isEditable(column, mode, truncatedColumns) || lockedColumns.has(column.column)) {
      continue
    }
    const now = inputOf(current, column.column)
    if (column.masked || mode === 'create') {
      if (now !== '') {
        pushWire(column, now)
      }
      continue
    }
    if (now === inputOf(initial, column.column)) {
      continue
    }
    if (now === '') {
      entries.push([column.column, column.valueType === 'STRING' ? '' : null])
    } else {
      pushWire(column, now)
    }
  }
  return Object.fromEntries(entries)
}

/**
 * 送る対象の日時の欄（作成は空でない欄、更新は初めの文字から変えた空でない欄）の判定の結果を返す（D18〜D22）。
 * 更新で初めの文字のままの欄は判定しない（送らない。2択も出さない。D22）。
 *
 * @param columns 詳細のカラム
 * @param mode 作成か更新か
 * @param initial 初めの入力の値
 * @param current 今の入力の値
 * @param truncatedColumns 先頭だけを受けたカラム
 * @param lockedColumns 入力不可の参照の項目
 * @param time 表示のタイムゾーンと2択の選択
 * @returns カラムの名前ごとの判定の結果
 */
export function resolveFormDateTimes(
  columns: readonly ColumnDefinition[],
  mode: FormMode,
  initial: FormInputs,
  current: FormInputs,
  truncatedColumns: readonly string[] = [],
  lockedColumns: ReadonlySet<string> = NO_LOCKED,
  time: FormTime = NO_FORM_TIME,
): Readonly<Record<string, DateTimeResolution>> {
  const entries: [string, DateTimeResolution][] = []
  for (const column of columns) {
    if (!isEditable(column, mode, truncatedColumns) || lockedColumns.has(column.column)) {
      continue
    }
    const now = inputOf(current, column.column)
    if (now === '' || (mode === 'update' && now === inputOf(initial, column.column))) {
      continue
    }
    const resolved = resolveFormDateTime(column, now, time)
    if (resolved !== null) {
      entries.push([column.column, resolved])
    }
  }
  return Object.fromEntries(entries)
}

/** 変更があるか（未保存の確かめに使う。作成でも初めの値と比べる。入力不可の参照の項目は数えない） */
export function hasFormChanges(
  columns: readonly ColumnDefinition[],
  mode: FormMode,
  initial: FormInputs,
  current: FormInputs,
  truncatedColumns: readonly string[] = [],
  lockedColumns: ReadonlySet<string> = NO_LOCKED,
): boolean {
  return columns.some(
    (column) =>
      isEditable(column, mode, truncatedColumns) &&
      !lockedColumns.has(column.column) &&
      inputOf(initial, column.column) !== inputOf(current, column.column),
  )
}
