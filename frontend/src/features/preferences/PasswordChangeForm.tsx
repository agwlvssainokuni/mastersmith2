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
// パスワードの変更のフォーム（functional-spec.md の W9・W10・W12、D9〜D11、CR6.2・CR6.9、security-design.md の 2節）。
// - 3つの項目はどれも type="password"。autocomplete は今が current-password、新しい2つが new-password。
// - 送信中は「変更する」を loading（aria-disabled・aria-busy、フォーカスを保つ）、3つの項目を readOnly にする（D10）。
// - フォームに method・action を置かず、送信の出来事で既定の動きを止めてフックの submit を呼ぶ（値を URL に載せる道を作らない）。
// - 誤りと知らせは文言の鍵で受け、描画のたびに今の言語で引く。値は console・ブラウザの保存に出さない。
import { Alert, Button, FormField, TextInput } from 'make-you-chic-ui'
import { useEffect, useRef, type FormEvent } from 'react'
import { useMessages } from '../../app/i18n/I18nProvider'
import type { PasswordChangeInput, PasswordField } from './preferencesApi'
import type { FocusRequest } from './PreferencesForm'
import './PreferencesForm.css'

export interface PasswordChangeFormProps {
  /** フォームの名前に使う見出し h1 の id */
  headingId: string
  form: PasswordChangeInput
  /** 項目の誤りの文言の鍵 */
  fieldErrors: Partial<Record<PasswordField, string>>
  /** 画面の知らせの文言の鍵 */
  alert: string | undefined
  sending: boolean
  /** 描画の後にフォーカスを移す項目（項目の誤りのときだけ。D9） */
  focusRequest: FocusRequest<PasswordField> | undefined
  setField: (name: PasswordField, value: string) => void
  submit: () => void
  dismissAlert: () => void
}

/** 項目ごとの文言の鍵・autocomplete・data-testid */
const FIELDS: readonly {
  name: PasswordField
  labelKey: string
  autoComplete: 'current-password' | 'new-password'
  testId: string
  hintKey?: string
}[] = [
  {
    name: 'currentPassword',
    labelKey: 'preferences.password.current',
    autoComplete: 'current-password',
    testId: 'preferences-password-current-input',
  },
  {
    name: 'newPassword',
    labelKey: 'preferences.password.new',
    autoComplete: 'new-password',
    testId: 'preferences-password-new-input',
    hintKey: 'preferences.password.hint',
  },
  {
    name: 'newPasswordConfirmation',
    labelKey: 'preferences.password.confirm',
    autoComplete: 'new-password',
    testId: 'preferences-password-confirm-input',
  },
]

/** パスワードの変更のフォーム */
export function PasswordChangeForm({
  headingId,
  form,
  fieldErrors,
  alert,
  sending,
  focusRequest,
  setField,
  submit,
  dismissAlert,
}: PasswordChangeFormProps) {
  const t = useMessages()
  const currentRef = useRef<HTMLInputElement>(null)
  const newRef = useRef<HTMLInputElement>(null)
  const confirmRef = useRef<HTMLInputElement>(null)
  const inputRefs = {
    currentPassword: currentRef,
    newPassword: newRef,
    newPasswordConfirmation: confirmRef,
  }

  useEffect(() => {
    const target = focusRequest?.target
    if (target === 'currentPassword') {
      currentRef.current?.focus()
    } else if (target === 'newPassword') {
      newRef.current?.focus()
    } else if (target === 'newPasswordConfirmation') {
      confirmRef.current?.focus()
    }
  }, [focusRequest])

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    submit()
  }

  return (
    <form
      className="preferences-form"
      aria-labelledby={headingId}
      noValidate
      onSubmit={handleSubmit}
      data-testid="preferences-password-form"
    >
      {alert !== undefined && (
        <div data-testid="preferences-password-alert">
          <Alert
            variant="danger"
            onDismiss={dismissAlert}
            dismissLabel={t('preferences.alert.dismiss')}
          >
            {t(alert)}
          </Alert>
        </div>
      )}
      {FIELDS.map((field) => {
        const errorKey = fieldErrors[field.name]
        return (
          <FormField
            key={field.name}
            label={t(field.labelKey)}
            required
            helperText={field.hintKey === undefined ? undefined : t(field.hintKey)}
            error={errorKey === undefined ? undefined : t(errorKey)}
          >
            <TextInput
              ref={inputRefs[field.name]}
              type="password"
              autoComplete={field.autoComplete}
              value={form[field.name]}
              onChange={(value) => setField(field.name, value)}
              readOnly={sending}
              data-testid={field.testId}
            />
          </FormField>
        )
      })}
      <div className="preferences-actions">
        <Button type="submit" loading={sending} data-testid="preferences-password-submit-button">
          {sending ? t('preferences.password.submitting') : t('preferences.password.submit')}
        </Button>
      </div>
    </form>
  )
}
