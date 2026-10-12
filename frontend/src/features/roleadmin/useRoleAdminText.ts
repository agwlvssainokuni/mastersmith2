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
// ロールの管理の画面の文言を引く（Intent 261004-role-menu の U6、frontend-components.md の 2.1。既存の useUserAdminText と
// 同じ形）。差し込む値（名前・氏名）は文言として解釈されず、表示は React の文字として描く（HTML として解釈しない）。
import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'

/** 文言の鍵と差し込む値から文言を返す関数 */
export type RoleAdminText = (
  key: string,
  values?: Readonly<Record<string, string | number>>,
) => string

/** ロールの管理の画面の文言を引く関数を返す。 */
export function useRoleAdminText(): RoleAdminText {
  const { t } = useTranslation()
  return useCallback<RoleAdminText>((key, values) => (values ? t(key, values) : t(key)), [t])
}
