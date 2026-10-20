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
// 書式の決まった文の見本（テストからだけ使う）。画面の日本語の文と同じ値。
import type { ValueTexts } from '../valueFormat'

/** 日本語の決まった文 */
export const JA_TEXTS: ValueTexts = {
  null: '（値なし）',
  binary: '（バイナリ）',
  empty: '（空）',
  yes: 'はい',
  no: 'いいえ',
  on: 'オン',
  off: 'オフ',
}
