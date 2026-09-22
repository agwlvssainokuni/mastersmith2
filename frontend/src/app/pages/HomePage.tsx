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
// ホーム（ログイン後の最初の画面）（BR7.9）。本Intentでは見出しと短い説明だけを持つ。
import { useMessages } from '../i18n/I18nProvider'
import './Page.css'

/** ホームの画面 */
export function HomePage() {
  const t = useMessages()
  return (
    <section className="page" data-testid="home-page" aria-labelledby="home-page-heading">
      <h1 id="home-page-heading" className="page-heading">
        {t('home.heading')}
      </h1>
      <p className="page-description">{t('home.description')}</p>
    </section>
  )
}
