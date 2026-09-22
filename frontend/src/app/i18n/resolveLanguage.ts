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
// 表示言語の決め方（BR6.1）。

/** 表示言語 */
export type DisplayLanguage = 'ja' | 'en'

/** 日本語でも英語でもないときの表示言語 */
export const DEFAULT_LANGUAGE: DisplayLanguage = 'ja'

/**
 * ブラウザの希望言語を優先順に見て、最初に ja または en に当たったものを返す。
 * 地域付き（en-US など）は先頭の言語部分で判定する。どれにも当たらなければ日本語。
 */
export function resolveLanguage(languages: readonly string[]): DisplayLanguage {
  for (const language of languages) {
    const primary = language.split('-')[0]?.trim().toLowerCase()
    if (primary === 'ja' || primary === 'en') {
      return primary
    }
  }
  return DEFAULT_LANGUAGE
}
