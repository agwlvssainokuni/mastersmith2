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
// 権限が無いときの表示（S6）の見出しの文言の鍵を決める純粋な関数（U4 の D11、FC 3.5）。
// - 全機能のサイドバーの項目から、path が今の URL に合う項目（matchPath の end: true）を探し、その文言の鍵を返す。
// - 項目は表示の条件（visibleWhen）で絞らない。印が外れて管理のメニューが消えた後も同じ見出しにするため。
// - 合う項目が無ければ共通の見出しの鍵（adminForbidden.heading）を返す。
import { matchPath } from 'react-router'
import type { FeatureRegistration } from '../registry/types'

/** 合う項目が無い URL の共通の見出しの鍵 */
export const ADMIN_FORBIDDEN_HEADING_KEY = 'adminForbidden.heading'

/** 今の URL に登録されたサイドバーの項目の文言の鍵。無ければ共通の見出しの鍵（D11）。 */
export function forbiddenHeadingKey(
  pathname: string,
  registrations: readonly FeatureRegistration[],
): string {
  for (const registration of registrations) {
    for (const item of registration.sidebarItems ?? []) {
      if (matchPath({ path: item.path, end: true }, pathname)) {
        return item.labelKey
      }
    }
  }
  return ADMIN_FORBIDDEN_HEADING_KEY
}
