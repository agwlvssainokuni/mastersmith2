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
// 管理者向け領域の中身（BR5.3）。本Intentでは見出しと、今後の管理機能がここに加わる旨の説明だけを置く。
import { useMessages } from '../../app/i18n/I18nProvider'
import './AdminPlaceholder.css'

/** 管理者向け領域のプレースホルダ */
export function AdminPlaceholder() {
  const t = useMessages()
  return (
    <section
      className="admin-placeholder"
      data-testid="admin-placeholder"
      aria-labelledby="admin-placeholder-heading"
    >
      <h1 id="admin-placeholder-heading" className="admin-placeholder-heading">
        {t('admin.area.heading')}
      </h1>
      <p className="admin-placeholder-description">{t('admin.area.description')}</p>
    </section>
  )
}
