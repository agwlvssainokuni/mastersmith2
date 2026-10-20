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
// NULL の表示（Intent 261016-business-data-views の U3 の L8、D15、AC1.5.5、U3-NFR5.6）。
// - 淡い文字の「（値なし）」を見せ、読み上げは「値なし」にする。色だけで表さない（括弧つきの文字で見分けられる）。
// - 淡い文字も 4.5:1 の色（--color-text-muted）を使う。
// - src/shared/ は app/・features/ を読まない。文言は呼ぶ側が画面の言語の文で渡す。
import './NullValue.css'

export interface NullValueProps {
  /** 見せる文字（例「（値なし）」） */
  text: string
  /** 読み上げの文字（例「値なし」） */
  spokenText: string
}

/** NULL の表示 */
export function NullValue({ text, spokenText }: NullValueProps) {
  return (
    <span className="shared-null-value" data-testid="null-value">
      <span aria-hidden="true">{text}</span>
      <span className="shared-null-value-spoken">{spokenText}</span>
    </span>
  )
}
