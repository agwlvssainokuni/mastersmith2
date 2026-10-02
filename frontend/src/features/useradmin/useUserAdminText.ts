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
// 文言の鍵から、値を埋めた文言を引く（functional-spec.md の 7節・W8 の 3、frontend-components.md の 3.4、NFR8.3）。
// 招待の useInvitationText の形に、3つ目の引数の言語を足したもの。言語を渡したときは i18next の lng の指定で引く
// （自分の言語を直した直後の Toast を、送った言語で出すため。機能設計の承認の場の R-02）。
// 埋める値（氏名・メールアドレスなど）は文言として解釈されず、表示は React の文字として描く（HTML として解釈しない）。
import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import type { UserLanguage } from './api/types'

/** 文言の鍵と埋める値（と言語）から文言を返す関数 */
export type UserAdminText = (
  key: string,
  values?: Readonly<Record<string, string | number>>,
  language?: UserLanguage,
) => string

/** 利用者の管理の画面の文言を引く関数を返す。 */
export function useUserAdminText(): UserAdminText {
  const { t } = useTranslation()
  return useCallback<UserAdminText>(
    (key, values, language) => {
      if (language !== undefined) {
        return t(key, { ...values, lng: language })
      }
      return values ? t(key, values) : t(key)
    },
    [t],
  )
}
