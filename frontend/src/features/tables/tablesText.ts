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
// 業務データの画面の文言を引く小さな口（Intent 261016-business-data-views の U3 の L7、D13・D15、U3-NFR6.3）。
// - 書式の決まった文（ValueTexts）を画面の言語の文言から作る。
// - 表示名（日英の組）を画面の言語で選び、空なら他方の言語、それも空なら決まった名前（テーブル名・カラム名）にする
//   （骨組みの resolveNavLabel と同じ決まり。D13）。
import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import type { DisplayLanguage } from '../../app/i18n/resolveLanguage'
import type { LocalizedText } from './businessDataTypes'
import type { ValueTexts } from './valueFormat'

/** 書式の決まった文を返す */
export function useValueTexts(): ValueTexts {
  const { t } = useTranslation()
  return useMemo(
    () => ({
      null: t('tables.value.null'),
      binary: t('tables.value.binary'),
      empty: t('tables.value.empty'),
      yes: t('tables.value.yes'),
      no: t('tables.value.no'),
      on: t('tables.value.on'),
      off: t('tables.value.off'),
    }),
    [t],
  )
}

/**
 * 表示名を画面の言語で選ぶ。
 *
 * @param text 日英の表示名
 * @param language 画面の言語
 * @param fallback どちらも空のときの名前
 */
export function localizedName(
  text: LocalizedText,
  language: DisplayLanguage,
  fallback: string,
): string {
  const primary = language === 'en' ? text.en : text.ja
  const secondary = language === 'en' ? text.ja : text.en
  if (primary.trim() !== '') {
    return primary
  }
  if (secondary.trim() !== '') {
    return secondary
  }
  return fallback
}
