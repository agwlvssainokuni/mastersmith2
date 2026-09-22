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
// i18next の初期化。骨組みの文言と、各機能の登録の文言（計画の C2 の決定）をまとめて持つ。
import i18next, { type i18n } from 'i18next'
import type { FeatureMessages } from '../registry/types'
import { en } from './messages/en'
import { ja } from './messages/ja'
import type { DisplayLanguage } from './resolveLanguage'

/** 骨組み（U1）の文言の鍵の集まり */
export const baseMessageKeys: ReadonlySet<string> = new Set(Object.keys(ja))

/**
 * 表示言語と機能の文言を指定して、i18next の実体を作る（アプリの実体ごとに1つ。大域の状態を持たない）。
 * 文言の鍵は `.` を含む平らな鍵として扱う。
 */
export function createI18n(
  language: DisplayLanguage,
  featureMessages: readonly FeatureMessages[] = [],
): i18n {
  const instance = i18next.createInstance()
  void instance.init({
    lng: language,
    fallbackLng: false,
    initAsync: false,
    keySeparator: false,
    nsSeparator: false,
    interpolation: { escapeValue: false },
    resources: {
      ja: { translation: Object.assign({}, ja, ...featureMessages.map((m) => m.ja)) },
      en: { translation: Object.assign({}, en, ...featureMessages.map((m) => m.en)) },
    },
  })
  return instance
}
