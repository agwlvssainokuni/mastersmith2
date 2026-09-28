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
// 登録の完了のフォーム（functional-spec.md の W5〜W10、D8〜D10、frontend-components.md の 5節・6節）。
// - 項目は上から、メールアドレス（読み取り専用。disabled にしない）・氏名・パスワード・パスワード（確かめ）と、
//   h2「表示の設定」の下の3つの RadioGroup（legend を渡して fieldset・legend で描く）。
// - 誤りは FormField の error で項目の下に文字で出し、aria-invalid・aria-describedby で結び付ける（CR6.1）。
// - 送信中は送信の Button を loading（aria-disabled・aria-busy、フォーカスを保つ）にし、文字の欄を readOnly にする。
//   RadioGroup は disabled にせず、送信中の選択はフックが無視する（W8 の1）。
// - 描画の確定の後に focusTarget の要素へフォーカスを移し、移したことを上へ知らせる。
// - サーバーの detail を受け取る口は作らない（D10）。値と文言を console に出さない。
import { Alert, Button, FormField, RadioGroup, TextInput } from 'make-you-chic-ui'
import { useEffect, useRef, type FormEvent } from 'react'
import {
  FONT_SIZES,
  LANGUAGE_NAMES,
  type DisplayLanguage,
  type FontSize,
  type ThemeChoice,
} from '../../app/display-settings/displaySettingsTypes'
import {
  isDisplayLanguage,
  isFontSize,
  isThemeChoice,
} from '../../app/display-settings/resolveDisplaySettings'
import { useMessages } from '../../app/i18n/I18nProvider'
import type {
  RegistrationFailure,
  RegistrationFocusTarget,
  RegistrationProblems,
  RegistrationValues,
} from './formProblems'
import './RegistrationForm.css'

/** テーマの選択肢の並び（OS に合わせる → ライト → ダーク。画面イメージ S2 のとおり） */
const THEME_ORDER: readonly ThemeChoice[] = ['system', 'light', 'dark']

/** 言語の選択肢の並び */
const LANGUAGE_ORDER: readonly DisplayLanguage[] = ['ja', 'en']

export interface RegistrationFormProps {
  /** フォームの名前に使う見出し h1 の id */
  headingId: string
  /** 招待のメールアドレス（読み取り専用の欄の値） */
  email: string
  values: RegistrationValues
  problems: RegistrationProblems
  failure: RegistrationFailure | undefined
  submitting: boolean
  focusTarget: RegistrationFocusTarget | undefined
  onFocusHandled: () => void
  onDisplayNameChange: (value: string) => void
  onPasswordChange: (value: string) => void
  onPasswordConfirmationChange: (value: string) => void
  onSelectLanguage: (language: DisplayLanguage) => void
  onSelectTheme: (theme: ThemeChoice) => void
  onSelectFontSize: (fontSize: FontSize) => void
  onSubmit: () => void
}

/** 登録の完了のフォーム */
export function RegistrationForm({
  headingId,
  email,
  values,
  problems,
  failure,
  submitting,
  focusTarget,
  onFocusHandled,
  onDisplayNameChange,
  onPasswordChange,
  onPasswordConfirmationChange,
  onSelectLanguage,
  onSelectTheme,
  onSelectFontSize,
  onSubmit,
}: RegistrationFormProps) {
  const t = useMessages()
  const displayNameRef = useRef<HTMLInputElement>(null)
  const passwordRef = useRef<HTMLInputElement>(null)
  const passwordConfirmationRef = useRef<HTMLInputElement>(null)
  const failureRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (focusTarget === undefined) {
      return
    }
    const targets: Record<RegistrationFocusTarget, HTMLElement | null> = {
      displayName: displayNameRef.current,
      password: passwordRef.current,
      passwordConfirmation: passwordConfirmationRef.current,
      failure: failureRef.current,
    }
    targets[focusTarget]?.focus()
    onFocusHandled()
  }, [focusTarget, onFocusHandled])

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    onSubmit()
  }

  const errorOf = (field: keyof RegistrationProblems): string | undefined => {
    const key = problems[field]
    return key === undefined ? undefined : t(key)
  }

  return (
    <form
      className="registration-form"
      aria-labelledby={headingId}
      noValidate
      onSubmit={handleSubmit}
      data-testid="registration-form"
    >
      {failure !== undefined && (
        <div
          ref={failureRef}
          tabIndex={-1}
          className="registration-failure"
          data-testid="registration-failure-alert"
        >
          <Alert variant="danger">{t(`registration.${failure}`)}</Alert>
        </div>
      )}
      <FormField label={t('registration.email.label')}>
        <TextInput
          type="email"
          autoComplete="username"
          readOnly
          value={email}
          data-testid="registration-email-input"
        />
      </FormField>
      <FormField
        label={t('registration.displayName.label')}
        required
        helperText={t('registration.displayName.hint')}
        error={errorOf('displayName')}
      >
        <TextInput
          ref={displayNameRef}
          value={values.displayName}
          onChange={onDisplayNameChange}
          readOnly={submitting}
          data-testid="registration-display-name-input"
        />
      </FormField>
      <FormField
        label={t('registration.password.label')}
        required
        helperText={t('registration.password.hint')}
        error={errorOf('password')}
      >
        <TextInput
          ref={passwordRef}
          type="password"
          autoComplete="new-password"
          value={values.password}
          onChange={onPasswordChange}
          readOnly={submitting}
          data-testid="registration-password-input"
        />
      </FormField>
      <FormField
        label={t('registration.passwordConfirmation.label')}
        required
        error={errorOf('passwordConfirmation')}
      >
        <TextInput
          ref={passwordConfirmationRef}
          type="password"
          autoComplete="new-password"
          value={values.passwordConfirmation}
          onChange={onPasswordConfirmationChange}
          readOnly={submitting}
          data-testid="registration-password-confirmation-input"
        />
      </FormField>
      <h2 className="registration-display-heading">{t('registration.display.heading')}</h2>
      <div data-testid="registration-language">
        <RadioGroup
          name="language"
          className="registration-radio-group"
          legend={
            <span className="registration-legend">
              <span>{t('registration.language.legend')}</span>
              <span className="registration-legend-hint">{t('registration.language.hint')}</span>
            </span>
          }
          options={LANGUAGE_ORDER.map((language) => ({
            value: language,
            label: LANGUAGE_NAMES[language],
            lang: language,
          }))}
          value={values.language}
          onChange={(value) => {
            if (isDisplayLanguage(value)) {
              onSelectLanguage(value)
            }
          }}
        />
      </div>
      <div data-testid="registration-theme">
        <RadioGroup
          name="theme"
          className="registration-radio-group"
          legend={t('registration.theme.legend')}
          options={THEME_ORDER.map((theme) => ({
            value: theme,
            label: t(`display.theme.${theme}`),
          }))}
          value={values.theme}
          onChange={(value) => {
            if (isThemeChoice(value)) {
              onSelectTheme(value)
            }
          }}
        />
      </div>
      <div data-testid="registration-font-size">
        <RadioGroup
          name="fontSize"
          className="registration-radio-group"
          legend={t('registration.fontSize.legend')}
          options={FONT_SIZES.map((fontSize) => ({
            value: fontSize,
            label: t(`display.fontSize.${fontSize}`),
          }))}
          value={values.fontSize}
          onChange={(value) => {
            if (isFontSize(value)) {
              onSelectFontSize(value)
            }
          }}
        />
        <p className="registration-hint">{t('registration.appearance.hint')}</p>
      </div>
      <div className="registration-form-actions">
        <Button type="submit" loading={submitting} data-testid="registration-submit-button">
          {submitting ? t('registration.submitting') : t('registration.submit')}
        </Button>
      </div>
    </form>
  )
}
