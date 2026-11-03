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
// 暗黙のタイムゾーン（ブラウザの既定）に頼る書き方を止める no-restricted-syntax の選択子（Intent 261102-timezone-ui-polish の U2、
// LC9、BR2.13・H4・SD6）。eslint.config.js と src/eslintTimeZoneRule.test.ts が同じものを読む（src/ の外に置き、計測の分母に入れない）。
// - S1: ほかのファイルが `x.resolvedOptions().timeZone` を読む（ブラウザのタイムゾーンを読むのは browserTimeZone.ts だけ）
// - S2: `Intl.DateTimeFormat` を timeZone の指定なしで作る・呼ぶ（`new Intl.DateTimeFormat('ja')`・`Intl.DateTimeFormat('ja', {})`）
// - S3: `toLocaleDateString`・`toLocaleTimeString` を timeZone の指定なしで呼ぶ
// - S4: `toLocaleString` を timeZone の指定なしで、数の整形の指定（桁・通貨・単位など）もなく呼ぶ（日時の呼び方とみなす）
// 選択子は項目の有無しか見ない（timeZone に入れる値の中身は見ない）。取りこぼしは、整形の関数の timeZone の引数を省略不可の string にする
// 型の決まりと、機械の既定のタイムゾーンを変えても同じ結果になる単体テストで補う。
//
// `Property` の探し方は、呼び出しの中の ObjectExpression の直下の timeZone を探す（`:has(ObjectExpression > Property[...])`。
// 呼ばれる側の式に入れ子の指定があっても当たる点は、見逃しを増やす側の誤りとして受け入れる）。

/** 呼び出しの中の指定オブジェクトに timeZone がある */
const HAS_TIME_ZONE = ":has(ObjectExpression > Property[key.name='timeZone'])"

/** 数の整形の指定の名前（これがあれば toLocaleString は数の整形とみなす） */
const NUMBER_OPTION =
  '(minimumFractionDigits|maximumFractionDigits|minimumIntegerDigits|minimumSignificantDigits|maximumSignificantDigits|style|currency|currencyDisplay|currencySign|unit|unitDisplay|notation|compactDisplay|useGrouping|signDisplay|roundingMode|roundingPriority|roundingIncrement|trailingZeroDisplay)'

/** 止める選択子（S1〜S4） */
export const TIME_ZONE_SELECTORS = {
  S1: {
    selector:
      "MemberExpression[property.name='timeZone'][object.type='CallExpression'][object.callee.property.name='resolvedOptions']",
    message:
      'ブラウザのタイムゾーンを読むのは src/app/display-settings/browserTimeZone.ts だけです。表示のタイムゾーンは useDisplayTimeZone から使ってください。',
  },
  S2: {
    selector: `:matches(NewExpression, CallExpression)[callee.type='MemberExpression'][callee.object.name='Intl'][callee.property.name='DateTimeFormat']:not(${HAS_TIME_ZONE})`,
    message:
      'Intl.DateTimeFormat は timeZone を明示に渡してください（ブラウザの既定のタイムゾーンに頼らない）。時刻の整形は src/shared/format/timeZone を使ってください。',
  },
  S3: {
    selector: `CallExpression[callee.type='MemberExpression'][callee.property.name=/^toLocale(Date|Time)String$/]:not(${HAS_TIME_ZONE})`,
    message:
      'toLocaleDateString・toLocaleTimeString は timeZone を明示に渡してください（ブラウザの既定のタイムゾーンに頼らない）。',
  },
  S4: {
    selector: `CallExpression[callee.type='MemberExpression'][callee.property.name='toLocaleString']:not(${HAS_TIME_ZONE}):not(:has(ObjectExpression > Property[key.name=/^${NUMBER_OPTION}$/]))`,
    message:
      'toLocaleString は日時の整形では timeZone を明示に渡してください（数の整形は桁・通貨などの指定を付けてください）。',
  },
}

/** S1〜S4 を no-restricted-syntax に渡す形の並び */
export const ALL_TIME_ZONE_SYNTAX = Object.values(TIME_ZONE_SELECTORS)

/** ブラウザのタイムゾーンを読む唯一のファイル（S1・S2 を除く）に当てる並び */
export const BROWSER_TIME_ZONE_SYNTAX = [TIME_ZONE_SELECTORS.S3, TIME_ZONE_SELECTORS.S4]
