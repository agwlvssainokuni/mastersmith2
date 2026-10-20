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
// E2E の報告に残してはならない値のファイル（値のファイル）と、値の形の表（Intent 260930-user-admin の U5、
// security-design.md 3.4・3.5）。110 が利用者 U を作った直後に、U のメールアドレス・パスワード・氏名と実行ごとの印 runTag を
// recordSecretValues で test-results/ の下の1つのファイルに書く。報告の部品（playwright-secret-check-reporter.ts）が、
// 実行の終わりにこのファイルの値と下の値の形で報告を探し、探し終えたらファイルを消す。
// - 中身は種類と値の組の JSON の配列。workers が 1 のため、書くときは読み足して書き直す。作るときと書いた後に権限を 600 にする。
// - Playwright は実行の始めに test-results/ を消すため、前の実行の値は残らない。
// - 値はこのファイルとテストの変数だけに持ち、注記・添付・標準出力・失敗の知らせに出さない。
// - 報告の部品と使い捨ての確かめの台本が Node の型の取り除き（--experimental-strip-types）でも読めるよう、Node の外の
//   部品を読み込まない（registeredUser.ts も読み込まない。氏名の値は下の SECRET_FIXED_VALUES に同じ値を置く）。
import { chmodSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'

/** 値のファイルの置き場（frontend/ からの相対パス） */
export const SECRET_VALUES_FILE = 'test-results/e2e-secret-values.json'

/** 報告の部品が runTag を単独の値として探す最小の長さ（短いときは宛先の形の中でだけ探し、警告する） */
export const RUN_TAG_MIN_SEARCH_LENGTH = 24

/**
 * 値の種類（型と実行時の一覧を1つの定数から作る。Intent 261016-business-data-views の U3 の NFR 設計のレビュー R-07）。
 * businessValue は業務データの見本の値（185 の見本の目印・180 が実行ごとに作る値）。
 */
export const SECRET_KINDS = ['email', 'password', 'displayName', 'runTag', 'businessValue'] as const

/** 値の種類 */
export type SecretKind = (typeof SECRET_KINDS)[number]

/**
 * 業務データの見本の目印（Intent 261016-business-data-views の U3 の L10、security-design.md の 8節）。185 などの見本の
 * 行の値（主キー・セルの値）は、すべてこの目印を含む形で作る（businessDataFixtures.ts）。報告にこの目印が出れば、見本の
 * 行の値が報告に漏れたことになる。`<script>` のような一般的な文字列は探す値にしない。
 */
export const BUSINESS_VALUE_MARK = 'E2E-BIZ-MARK'

/**
 * U4 の測りの見本（userAdminFixtures.ts の measurementListPage）が作る値（Intent 261016-business-data-views の U3 の計画 D5: A）。
 * 番号 01〜25 の「測定 NN」と sokutei.NN@example.com。見本の作り方を変えるときは、ここも合わせて直す（このファイルは Node の外の
 * 部品を読み込まないため、同じ式をここに置く）。
 */
export function measurementSampleValues(): SecretEntry[] {
  return Array.from({ length: 25 }, (_, index) => String(index + 1).padStart(2, '0')).flatMap(
    (number): SecretEntry[] => [
      { kind: 'displayName', value: `測定 ${number}` },
      { kind: 'email', value: `sokutei.${number}@example.com` },
    ],
  )
}

/** 値のファイルの1件 */
export interface SecretEntry {
  kind: SecretKind
  value: string
}

/** 値の形（値のファイルが書かれなかった実行や、前の実行の残りに備えて、形でも探す） */
export interface SecretValueShape {
  kind: SecretKind
  /** ASCII の正規表現（バイト列を latin1 の文字列として当てる） */
  pattern: RegExp
}

/**
 * 110 が作る値の形（registeredUser.ts の createRegisteredUser・newRunPassword の形）。
 * 宛先は u7-perf- で始まり @example.com で終わる（@ が %40 の URL の形を含む）。パスワードは e2e-u7-pw- と 24 文字の16進。
 * 手伝いの形を変えるときは、この表も合わせて直す（security-design.md 11節）。
 */
export const SECRET_VALUE_SHAPES: readonly SecretValueShape[] = [
  { kind: 'email', pattern: /u7-perf-[0-9A-Za-z._-]+(?:@|%40)example\.com/i },
  { kind: 'password', pattern: /e2e-u7-pw-[0-9a-f]{24}/i },
]

/**
 * 形ではなく決まった値で探すもの。氏名は registeredUser.ts の REGISTERED_DISPLAY_NAME と同じ値（そのまま・URL の形・
 * JSON の \u の形で探す）。
 * Intent 261016-business-data-views の U4（U4-NFR4.7、security-design.md の 1節）: 行の「操作」とトップバーのトリガーの
 * 読み上げの名前に入る見本の値も探す。userAdminFixtures.ts の検査の一覧の行の氏名とメールアドレス（LONG_EMAIL と同じ値を
 * 含む）と、130 の 2語の氏名（TWO_WORD_NAME）。手伝いの値を変えるときは、この一覧も合わせて直す。
 */
export const SECRET_FIXED_VALUES: readonly SecretEntry[] = [
  { kind: 'displayName', value: '計測 花子' },
  { kind: 'displayName', value: '管理 一郎' },
  { kind: 'email', value: 'kanri.ichiro@example.com' },
  { kind: 'displayName', value: '佐藤 花子' },
  { kind: 'email', value: 'sato.hanako@example.com' },
  { kind: 'displayName', value: '鈴木 次郎' },
  { kind: 'email', value: 'suzuki.jiro@example.com' },
  { kind: 'displayName', value: '高橋 三郎' },
  { kind: 'email', value: 'takahashi.saburo@example.com' },
  { kind: 'displayName', value: '田中 四郎' },
  {
    kind: 'email',
    value: `${'very.long.mail.address.for.wrapping.check'.repeat(3)}@example.com`,
  },
  { kind: 'displayName', value: '<b>記号</b> & "引用" \'名\'' },
  { kind: 'email', value: 'kigou@example.com' },
  { kind: 'displayName', value: 'Probe Sample' },
  // Intent 261016-business-data-views の U3（計画 Step 13）: 185 の見本の目印と、U4 の測りの見本の値（D5: A）。
  { kind: 'businessValue', value: BUSINESS_VALUE_MARK },
  ...measurementSampleValues(),
]

/** frontend/ の場所 */
const FRONTEND_ROOT = path.resolve(import.meta.dirname, '..', '..')

/** 値のファイルの絶対パス */
export function secretValuesFilePath(): string {
  return path.join(FRONTEND_ROOT, SECRET_VALUES_FILE)
}

/** 値のファイルを読む（無ければ空。形が違えば誤り）。 */
export function readSecretValues(file: string): SecretEntry[] {
  if (!existsSync(file)) {
    return []
  }
  const parsed: unknown = JSON.parse(readFileSync(file, 'utf8'))
  if (!Array.isArray(parsed)) {
    throw new Error('値のファイルの形が違います（配列ではありません）。')
  }
  return parsed.map((item: unknown) => {
    const entry = item as Partial<SecretEntry> | null
    if (
      entry === null ||
      typeof entry !== 'object' ||
      typeof entry.value !== 'string' ||
      !(SECRET_KINDS as readonly string[]).includes(String(entry.kind))
    ) {
      throw new Error('値のファイルの形が違います（種類か値が読めません）。')
    }
    return { kind: entry.kind as SecretKind, value: entry.value }
  })
}

/** 値のファイルに値を読み足して書き直す（作るときと書いた後に権限 600）。空の値は書かない。 */
export function recordSecretValues(
  entries: readonly SecretEntry[],
  file: string = secretValuesFilePath(),
): void {
  const current = readSecretValues(file)
  const next = [...current, ...entries.filter((entry) => entry.value !== '')]
  mkdirSync(path.dirname(file), { recursive: true })
  writeFileSync(file, JSON.stringify(next), { encoding: 'utf8', mode: 0o600 })
  chmodSync(file, 0o600)
}
