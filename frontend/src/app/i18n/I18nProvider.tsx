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
// 表示言語を決め、文言の鍵から文言を引く手段を提供する（WF6、BR6.1、BR6.2）。
import { useEffect, useMemo, type ReactNode } from 'react'
import { I18nextProvider, useTranslation } from 'react-i18next'
import type { FeatureMessages } from '../registry/types'
import { createI18n } from './i18n'
import { resolveLanguage, type DisplayLanguage } from './resolveLanguage'

export interface I18nProviderProps {
  /** 各機能の登録の文言 */
  featureMessages?: readonly FeatureMessages[]
  children: ReactNode
}

/** ブラウザの希望言語を優先順に返す。 */
function browserLanguages(): readonly string[] {
  if (typeof navigator === 'undefined') {
    return []
  }
  if (navigator.languages && navigator.languages.length > 0) {
    return navigator.languages
  }
  return navigator.language ? [navigator.language] : []
}

/** 表示言語と文言を提供する。`<html lang>` も表示言語に合わせる。 */
export function I18nProvider({ featureMessages = [], children }: I18nProviderProps) {
  const language = resolveLanguage(browserLanguages())
  const i18n = useMemo(() => createI18n(language, featureMessages), [language, featureMessages])

  useEffect(() => {
    document.documentElement.lang = language
  }, [language])

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
