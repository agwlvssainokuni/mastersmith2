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
// 日時の入力の判定（Intent 261102-timezone-ui-polish の U4、計画 Step 8、D13・D14・D17・D19〜D22・D26・D27）。
// 入力の文字・型・タイムゾーン・用途・2択の選択から、送る値か誤りの種類を決める純粋な関数。
// - 時点を表す型（instant）: U1 の parseLocalDateTime だけで書式・範囲・夏時間を判定し、ok の時点（UTC の +00:00 の文字）
//   だけを送る値にする（ADR-002）。夏時間で2回ある時刻は、範囲の始まりは先・終わりは後に自動で決め（Q4: A）、作成・更新と
//   一致の検索は2択の選択で決める（未選択は overlap-pending）。表示のタイムゾーンが無い（読み込みの失敗）ときは unavailable。
// - 時差を持たない日時（local）: 変換せず、U1 と同じ書式と範囲の確かめだけを当て、区切りの空白を T にして送る（D14）。
//   タイムゾーンの有無・値によらない。
// - 誤りは種類だけで返し、入力の値を誤りに入れない（NFR5.1.4）。例外は投げない。
import { formatInstant, parseLocalDateTime, type OverlapChoice } from '../../shared/format/timeZone'
import { isLocalDateTimeText, toLocalDateTimeWire } from '../../shared/format/dateTimeInput'
import type { ValueType } from './businessDataTypes'

/** 日時の欄の種類（instant は時点を表す型、local は時差を持たない日時） */
export type DateTimeKind = 'instant' | 'local'

/** 日時の欄の用途（作成・更新、範囲の始まり・終わり、一致の検索） */
export type DateTimePurpose = 'form' | 'range-from' | 'range-to' | 'equals'

/** 夏時間で2回ある時刻の2つの時差（±HH:mm。2択の文に使う） */
export interface OverlapOffsets {
  readonly earlier: string
  readonly later: string
}

/** 判定の結果 */
export type DateTimeResolution =
  /** 送る値が1つに決まった（overlap は2回ある時刻を選択・自動で決めたときの時差。それ以外は null） */
  | { readonly kind: 'ok'; readonly wire: string; readonly overlap: OverlapOffsets | null }
  /** 書式に合わない・暦に無い日付・範囲の外（D19） */
  | { readonly kind: 'invalid' }
  /** 夏時間で存在しない時刻（D20・D26） */
  | { readonly kind: 'gap' }
  /** 夏時間で2回ある時刻で、2択が未選択（D21・D27。作成・更新と一致の検索だけ） */
  | { readonly kind: 'overlap-pending'; readonly overlap: OverlapOffsets }
  /** 表示のタイムゾーンを読み込めない（D11） */
  | { readonly kind: 'unavailable' }

/** 判定の入力 */
export interface DateTimeInput {
  /** 入力の文字 */
  readonly text: string
  readonly kind: DateTimeKind
  /** 表示のタイムゾーンの名前（instant のとき。読み込みの失敗は null。local では使わない） */
  readonly timeZone: string | null
  readonly purpose: DateTimePurpose
  /** 2回ある時刻の2択の選択（未選択は null。範囲の用途では使わない） */
  readonly overlapChoice: OverlapChoice | null
}

/** 日時の欄の画面だけの誤りの理由（D19〜D21・D26・D11。サーバーには無い。フォームと検索で共通） */
export type DateTimeErrorReason =
  'DATE_TIME_FORMAT' | 'DATE_TIME_GAP' | 'DATE_TIME_OVERLAP' | 'TIME_ZONE_UNAVAILABLE'

/**
 * 判定の結果を誤りの理由にする（ok は undefined）。
 *
 * @param resolution resolveDateTimeInput の結果
 */
export function dateTimeErrorReason(
  resolution: DateTimeResolution,
): DateTimeErrorReason | undefined {
  switch (resolution.kind) {
    case 'ok':
      return undefined
    case 'invalid':
      return 'DATE_TIME_FORMAT'
    case 'gap':
      return 'DATE_TIME_GAP'
    case 'overlap-pending':
      return 'DATE_TIME_OVERLAP'
    case 'unavailable':
      return 'TIME_ZONE_UNAVAILABLE'
  }
}

/**
 * 受け渡しの型から日時の欄の種類を決める（DATETIME_TZ は instant、DATETIME は local、ほかは日時の欄ではない）。
 *
 * @param valueType カラム・検索の項目の受け渡しの型
 */
export function dateTimeKindOf(valueType: ValueType): DateTimeKind | null {
  switch (valueType) {
    case 'DATETIME_TZ':
      return 'instant'
    case 'DATETIME':
      return 'local'
    default:
      return null
  }
}

/** formatInstant の ok の文字の末尾の時差 */
const OFFSET_AT_END = / ([+-]\d{2}:\d{2}(?::\d{2})?)$/

/**
 * 時点を表示のタイムゾーンで書式にしたときの時差（±HH:mm。読めなければ空の文字）。入力の案内の今の時差（D15・D24）にも使う。
 *
 * @param instant 時点の文字（今の時刻の ISO 8601 など）
 * @param timeZone 表示のタイムゾーンの名前
 */
export function offsetOf(instant: string, timeZone: string): string {
  const result = formatInstant(instant, timeZone)
  return result.kind === 'ok' ? (OFFSET_AT_END.exec(result.text)?.[1] ?? '') : ''
}

/** 範囲の用途で、2回ある時刻をどちらに決めるか（始まりは先、終わりは後。Q4: A） */
function automaticChoice(purpose: DateTimePurpose): OverlapChoice | null {
  switch (purpose) {
    case 'range-from':
      return 'earlier'
    case 'range-to':
      return 'later'
    default:
      return null
  }
}

/**
 * 日時の入力を判定する。
 *
 * @param input 文字・種類・タイムゾーン・用途・2択の選択
 */
export function resolveDateTimeInput(input: DateTimeInput): DateTimeResolution {
  if (input.kind === 'local') {
    return isLocalDateTimeText(input.text)
      ? { kind: 'ok', wire: toLocalDateTimeWire(input.text), overlap: null }
      : { kind: 'invalid' }
  }
  const timeZone = input.timeZone
  if (timeZone === null) {
    return { kind: 'unavailable' }
  }
  const parsed = parseLocalDateTime(input.text, timeZone)
  switch (parsed.kind) {
    case 'ok':
      return { kind: 'ok', wire: parsed.instant, overlap: null }
    case 'gap':
      return { kind: 'gap' }
    case 'invalid':
      return { kind: 'invalid' }
    case 'invalid-time-zone':
      return { kind: 'unavailable' }
    case 'overlap': {
      const overlap: OverlapOffsets = {
        earlier: offsetOf(parsed.earlier, timeZone),
        later: offsetOf(parsed.later, timeZone),
      }
      const choice = automaticChoice(input.purpose) ?? input.overlapChoice
      if (choice === null) {
        return { kind: 'overlap-pending', overlap }
      }
      return { kind: 'ok', wire: choice === 'later' ? parsed.later : parsed.earlier, overlap }
    }
  }
}
