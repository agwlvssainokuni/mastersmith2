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
// 文言の鍵から、値を埋めた文言を引く（BR6.1）。埋める値（テーブル名・件数など）は文言として解釈されない
// （i18next の既定で、埋めた値の中の入れ子の指定は展開しない）。表示は React の文字として描く（NFR3.9）。
import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'

/** 文言の鍵と埋める値から文言を返す関数 */
export type DslText = (key: string, values?: Readonly<Record<string, string | number>>) => string

/** DSL の管理画面の文言を引く関数を返す。 */
export function useDslText(): DslText {
  const { t } = useTranslation()
  return useCallback<DslText>((key, values) => (values ? t(key, values) : t(key)), [t])
}
