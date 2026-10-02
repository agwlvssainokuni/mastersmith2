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
// 権限が無いときの表示 S6（U4 の D11・D12、FC 3.4、契約 C4 の AdminForbiddenView）。
// - 見出しは今の URL に登録されたサイドバーの項目の名前（無ければ「管理」、forbiddenHeadingKey）。
// - Alert（info。make-you-chic-ui では role="status"）の中に「この画面を使う権限がありません」と
//   ホームへ読み込み直しなしで移るリンク「ホームへ戻る」を置く。原因は書かない（FS の G8）。
// - 部品が作られたときだけ見出しへフォーカスを移す。ForbiddenByApi から ForbiddenByRoute へ移るときは
//   部品が作り直されないため、移し直しは起きない（R-05）。
// - Props も失敗の値も受け取らず、応答の値を画面に出さない（NFR3.1）。HTML の直接の埋め込みは使わない（NFR9.5）。
import { Alert } from 'make-you-chic-ui'
import { useEffect, useId, useRef } from 'react'
import { Link, useLocation } from 'react-router'
import { useMessages } from '../i18n/I18nProvider'
import { useFeatureRegistry } from '../registry/FeatureRegistryContext'
import { HOME_PATH } from '../registry/validateRegistrations'
import { forbiddenHeadingKey } from './forbiddenHeading'
import './AdminForbiddenView.css'

/** 権限が無いときの表示（S6） */
export function AdminForbiddenView() {
  const t = useMessages()
  const { pathname } = useLocation()
  const registrations = useFeatureRegistry()
  const headingId = useId()
  const headingRef = useRef<HTMLHeadingElement>(null)

  useEffect(() => {
    headingRef.current?.focus()
  }, [])

  return (
    <section
      className="admin-forbidden"
      aria-labelledby={headingId}
      data-testid="admin-forbidden-view"
    >
      <h1
        id={headingId}
        ref={headingRef}
        className="admin-forbidden-heading"
        tabIndex={-1}
        data-testid="admin-forbidden-heading"
      >
        {t(forbiddenHeadingKey(pathname, registrations))}
      </h1>
      <Alert variant="info">
        <p className="admin-forbidden-message">{t('adminForbidden.message')}</p>
        <Link
          to={HOME_PATH}
          className="admin-forbidden-home-link"
          data-testid="admin-forbidden-home-link"
        >
          {t('adminForbidden.homeLink')}
        </Link>
      </Alert>
    </section>
  )
}
