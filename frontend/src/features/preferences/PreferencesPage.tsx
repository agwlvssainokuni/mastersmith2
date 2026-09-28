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
// プリファレンスの画面（S4、frontend-components.md の 1節・4節、functional-spec.md の 4.1）。見出し h1 と、状態に応じて
// 読み込み中・読み込みの失敗・フォームを出し分ける。状態と API と U4 の口は usePreferencesForm にまとめる。
import { useId } from 'react'
import { useMessages } from '../../app/i18n/I18nProvider'
import { PreferencesForm } from './PreferencesForm'
import { PreferencesLoadFailure } from './PreferencesLoadFailure'
import { usePreferencesForm } from './usePreferencesForm'
import './PreferencesPage.css'

/** プリファレンスの画面 */
export function PreferencesPage() {
  const t = useMessages()
  const controller = usePreferencesForm()
  const headingId = useId()
  const { phase, form } = controller

  let body
  if (phase === 'loadFailed') {
    body = (
      <PreferencesLoadFailure
        onRetry={controller.reload}
        retryFocusSeq={controller.retryFocusSeq}
      />
    )
  } else if (phase === 'loading' || form === undefined) {
    body = (
      <div aria-busy="true" data-testid="preferences-loading">
        <p role="status" className="preferences-loading">
          {t('preferences.loading')}
        </p>
      </div>
    )
  } else {
    body = (
      <PreferencesForm
        headingId={headingId}
        form={form}
        fieldErrors={controller.fieldErrors}
        alert={controller.alert}
        dirty={controller.dirty}
        saving={phase === 'saving'}
        focusRequest={controller.focusRequest}
        setDisplayName={controller.setDisplayName}
        setLanguage={controller.setLanguage}
        setTheme={controller.setTheme}
        setFontSize={controller.setFontSize}
        reset={controller.reset}
        save={controller.save}
        dismissAlert={controller.dismissAlert}
      />
    )
  }

  return (
    <section className="preferences-page" data-testid="preferences-page">
      <h1 id={headingId} className="preferences-heading" data-testid="preferences-heading">
        {t('preferences.page.title')}
      </h1>
      {body}
    </section>
  )
}
