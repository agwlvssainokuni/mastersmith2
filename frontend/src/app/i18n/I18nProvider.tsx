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
// 文言の鍵から文言を引く手段を提供する（WF6、BR6.2）。表示言語は外（表示の設定の土台）から受け取る（U4 の D14）。
// ブラウザの言語設定の読み取りと <html lang> の切り替えは、表示の設定の土台（DisplaySettingsProvider）が受け持つ。
import { useMemo, type ReactNode } from 'react'
import { I18nextProvider, useTranslation } from 'react-i18next'
import type { FeatureMessages } from '../registry/types'
import { createI18n } from './i18n'
import type { DisplayLanguage } from './resolveLanguage'

export interface I18nProviderProps {
  /** 表示言語（表示の設定の土台が決めた画面の言語） */
  language: DisplayLanguage
  /** 各機能の登録の文言 */
  featureMessages?: readonly FeatureMessages[]
  children: ReactNode
}

/** 受けた表示言語の文言を提供する。言語が変わったら、同じ描画で文言が変わる。 */
export function I18nProvider({ language, featureMessages = [], children }: I18nProviderProps) {
  const i18n = useMemo(() => createI18n(language, featureMessages), [language, featureMessages])
  return <I18nextProvider i18n={i18n}>{children}</I18nextProvider>
}

/** 文言の鍵から文言を引く関数を返す。 */
export function useMessages(): (key: string) => string {
  const { t } = useTranslation()
  return (key: string) => t(key)
}

/** 現在の表示言語を返す。 */
export function useDisplayLanguage(): DisplayLanguage {
  const { i18n } = useTranslation()
  return i18n.language === 'en' ? 'en' : 'ja'
}
