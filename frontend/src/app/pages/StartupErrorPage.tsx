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
// 画面の起動の失敗の表示（BR7.2）。登録の問題を示して、画面の起動を止める。
import { useMessages } from '../i18n/I18nProvider'
import './Page.css'

export interface StartupErrorPageProps {
  /** 問題の一覧（どの登録が問題か） */
  problems: readonly string[]
}

/** 起動の失敗の画面 */
export function StartupErrorPage({ problems }: StartupErrorPageProps) {
  const t = useMessages()
  return (
    <section
      className="page"
      role="alert"
      data-testid="startup-error-page"
      aria-labelledby="startup-error-page-heading"
    >
      <h1 id="startup-error-page-heading" className="page-heading">
        {t('startupError.heading')}
      </h1>
      <p className="page-description">{t('startupError.description')}</p>
      <ul className="page-list" data-testid="startup-error-problems">
        {problems.map((problem) => (
          <li key={problem}>{problem}</li>
        ))}
      </ul>
    </section>
  )
}
