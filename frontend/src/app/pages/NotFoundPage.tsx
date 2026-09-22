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
// 「ページが見つかりません」とホームへのリンク（BR7.5、BR7.8）。
import { Link } from 'react-router'
import { useMessages } from '../i18n/I18nProvider'
import { HOME_PATH } from '../registry/validateRegistrations'
import './Page.css'

/** 見つからない画面 */
export function NotFoundPage() {
  const t = useMessages()
  return (
    <section className="page" data-testid="not-found-page" aria-labelledby="not-found-page-heading">
      <h1 id="not-found-page-heading" className="page-heading">
        {t('notFound.heading')}
      </h1>
      <p className="page-description">{t('notFound.description')}</p>
      <Link to={HOME_PATH} className="page-link" data-testid="not-found-home-link">
        {t('notFound.homeLink')}
      </Link>
    </section>
  )
}
