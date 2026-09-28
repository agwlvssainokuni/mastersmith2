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
// パスワードの変更の画面（S5、frontend-components.md の 1節・4節、functional-spec.md の 4.2）。見出し h1 とフォーム。
// 開いても API を呼ばない（NFR6.4）。状態と API は usePasswordChangeForm にまとめる。
import { useId } from 'react'
import { useMessages } from '../../app/i18n/I18nProvider'
import { PasswordChangeForm } from './PasswordChangeForm'
import { usePasswordChangeForm } from './usePasswordChangeForm'
import './PreferencesPage.css'

/** パスワードの変更の画面 */
export function PasswordChangePage() {
  const t = useMessages()
  const controller = usePasswordChangeForm()
  const headingId = useId()
  return (
    <section className="preferences-page" data-testid="preferences-password-page">
      <h1 id={headingId} className="preferences-heading" data-testid="preferences-password-heading">
        {t('preferences.password.title')}
      </h1>
      <PasswordChangeForm
        headingId={headingId}
        form={controller.form}
        fieldErrors={controller.fieldErrors}
        alert={controller.alert}
        sending={controller.phase === 'sending'}
        focusRequest={controller.focusRequest}
        setField={controller.setField}
        submit={controller.submit}
        dismissAlert={controller.dismissAlert}
      />
    </section>
  )
}
