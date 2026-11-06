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
// タイムゾーンの名前の門（LC1、SD4・SD5）。名前の検査と、Intl.DateTimeFormat の取り出し・キャッシュを受け持つ。
// ブラウザの既定のタイムゾーン・ロケールは読まない（固定のロケールと、明示の名前だけを使う）。

/** 名前の長さの上限（SD1.2。IANA の最長の名前の約 2 倍） */
export const MAX_TIME_ZONE_NAME_LENGTH = 64

/** 整形器のキャッシュの上限（SD5.1）。超えたら全体を捨てる */
export const MAX_FORMATTER_CACHE_SIZE = 128

/** 整形のロケール（グレゴリオ暦・ラテン数字。画面のロケールに影響されない。SD4.2） */
const FORMAT_LOCALE = 'en-US-u-ca-gregory-nu-latn'

/** 使ってよい文字（IANA の名前は英数字・_・-・+・/ だけ） */
const NAME_CHARS = /^[A-Za-z0-9_+\-/]+$/

/** 固定のオフセット表記（+0900 など）。Intl が受け付けても、解釈できない名前として弾く（BR1.10） */
const FIXED_OFFSET = /^[+-][0-9]{2}(:?[0-9]{2})?$/

/** 名前をキーにした整形器のキャッシュ（モジュールの中だけ。外へ公開しない） */
const formatterCache = new Map<string, Intl.DateTimeFormat>()

/**
 * 名前が検査（型・長さ・文字・固定のオフセットの除外）を通るか。Intl には問い合わせず、整形器のキャッシュにも触れない。
 * 選択肢の組み立ての時差（offsetLabel.ts）も、同じ検査を使う（Intent 261104-tz-options-perf）。
 */
export function passesNameCheck(name: unknown): name is string {
  if (typeof name !== 'string') {
    return false
  }
  if (name.length < 1 || name.length > MAX_TIME_ZONE_NAME_LENGTH) {
    return false
  }
  return NAME_CHARS.test(name) && !FIXED_OFFSET.test(name)
}

/**
 * 名前の整形器を、キャッシュに入れずに作る（呼ぶたびに新しい整形器を 1 つ作る）。検査を通らない・Intl が知らない名前は null
 * （例外は投げない）。getFormatter と同じロケール・項目で作る。選択肢の組み立てが共有のキャッシュを汚さないために使う
 * （Intent 261104-tz-options-perf の FR3.1）。
 */
export function createUncachedFormatter(name: unknown): Intl.DateTimeFormat | null {
  if (!passesNameCheck(name)) {
    return null
  }
  try {
    return new Intl.DateTimeFormat(FORMAT_LOCALE, {
      timeZone: name,
      hourCycle: 'h23',
      era: 'short',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    })
  } catch {
    // Intl が知らない名前（RangeError）。解釈できない名前として null を返す（SD1.5）
    return null
  }
}

/**
 * 名前に対応する整形器を返す。解釈できない名前は null（例外は投げない）。同じ名前では整形器を使い回す。
 * 結果はキャッシュの有無で変わらない。
 */
export function getFormatter(name: unknown): Intl.DateTimeFormat | null {
  if (!passesNameCheck(name)) {
    return null
  }
  const cached = formatterCache.get(name)
  if (cached !== undefined) {
    return cached
  }
  const formatter = createUncachedFormatter(name)
  if (formatter === null) {
    return null
  }
  if (formatterCache.size >= MAX_FORMATTER_CACHE_SIZE) {
    formatterCache.clear()
  }
  formatterCache.set(name, formatter)
  return formatter
}

/** 名前が、今のブラウザの Intl が知る IANA の名前として解釈できるか（BR1.10） */
export function canInterpretTimeZone(timeZone: unknown): boolean {
  return getFormatter(timeZone) !== null
}

/** 整形器のキャッシュを空にする（テスト用。公開の入口 index.ts からは出さない） */
export function clearFormatterCache(): void {
  formatterCache.clear()
}

/** 整形器のキャッシュの件数（テスト用。公開の入口 index.ts からは出さない） */
export function formatterCacheSize(): number {
  return formatterCache.size
}
