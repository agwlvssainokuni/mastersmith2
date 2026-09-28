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
// プリファレンスのフォーム（functional-spec.md の W3〜W8・W12・W13、D9〜D11・D13・D14、frontend-components.md の 4節）。
// - 項目は上から、氏名（FormField と TextInput）・言語・テーマと文字の大きさの案内・テーマ・文字の大きさ（make-you-chic-ui の
//   RadioGroup に legend を渡して fieldset・legend で描く）・「元に戻す」・「保存する」。
// - RadioGroup は aria-describedby を外から受けないため、言語の案内と選択の誤りは legend の中に置き、テーマと文字の大きさの
//   案内は2つのまとまりの前に1回だけ結び付けない文字として置く（10節の (b)、U6 と同じ置き方）。
// - 送信中は「保存する」を loading（aria-disabled・aria-busy、フォーカスを保つ）、氏名を readOnly、「元に戻す」を disabled にする。
//   RadioGroup は disabled にせず、送信中の選択はフックが受け付けない（D10）。RadioGroup とボタンは言語が変わっても
//   作り直さない（key を変えない）。
// - 誤りと知らせは文言の鍵で受け、描画のたびに今の言語で引く（W13 の2）。サーバーの detail を受け取る口は作らない（D13）。
import { Alert, Button, FormField, RadioGroup, TextInput } from 'make-you-chic-ui'
import { useEffect, useRef, type FormEvent, type ReactNode } from 'react'
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
import type { Preferences, PreferencesField } from './preferencesApi'
import './PreferencesForm.css'

/** テーマの選択肢の並び（OS に合わせる → ライト → ダーク。画面イメージ S4 のとおり） */
const THEME_ORDER: readonly ThemeChoice[] = ['system', 'light', 'dark']

/** 言語の選択肢の並び */
const LANGUAGE_ORDER: readonly DisplayLanguage[] = ['ja', 'en']

/** フォーカスの要求（同じ先を続けて求められるよう、回 seq で区別する） */
export interface FocusRequest<T extends string> {
  target: T
  seq: number
}

/** プリファレンスのフォームのフォーカスの行き先（項目か「保存する」） */
export type PreferencesFocusTarget = PreferencesField | 'save'

/** 選択のまとまりの中の選ばれている（無ければ最初の）ラジオへフォーカスを移す。 */
function focusChoice(container: HTMLElement | null): void {
  const radio =
    container?.querySelector<HTMLInputElement>('input[type="radio"]:checked') ??
    container?.querySelector<HTMLInputElement>('input[type="radio"]')
  radio?.focus()
}

export interface PreferencesFormProps {
  /** フォームの名前に使う見出し h1 の id */
  headingId: string
  form: Preferences
  /** 項目の誤りの文言の鍵 */
  fieldErrors: Partial<Record<PreferencesField, string>>
  /** 画面の知らせの文言の鍵 */
  alert: string | undefined
  dirty: boolean
  saving: boolean
  /** 描画の後にフォーカスを移す先（項目の誤り D9・「元に戻す」の後 W5・読み直しの後 W2 の4） */
  focusRequest: FocusRequest<PreferencesFocusTarget> | undefined
  setDisplayName: (value: string) => void
  setLanguage: (value: DisplayLanguage) => void
  setTheme: (value: ThemeChoice) => void
  setFontSize: (value: FontSize) => void
  reset: () => void
  save: () => void
  dismissAlert: () => void
}

/** 選択のまとまりの legend（名前・案内・誤りを縦に並べる） */
function Legend({ name, hint, error }: { name: string; hint?: string; error?: string }) {
  return (
    <span className="preferences-legend">
      <span>{name}</span>
      {hint !== undefined && <span className="preferences-legend-hint">{hint}</span>}
      {error !== undefined && <span className="preferences-choice-error">{error}</span>}
    </span>
  )
}

/** プリファレンスのフォーム */
export function PreferencesForm({
  headingId,
  form,
  fieldErrors,
  alert,
  dirty,
  saving,
  focusRequest,
  setDisplayName,
  setLanguage,
  setTheme,
  setFontSize,
  reset,
  save,
  dismissAlert,
}: PreferencesFormProps) {
  const t = useMessages()
  const displayNameRef = useRef<HTMLInputElement>(null)
  const languageRef = useRef<HTMLDivElement>(null)
  const themeRef = useRef<HTMLDivElement>(null)
  const fontSizeRef = useRef<HTMLDivElement>(null)
  const saveRef = useRef<HTMLButtonElement>(null)

  // 描画の確定の後に、求められた要素へフォーカスを移す。送信の後には求められない（D10）。
  useEffect(() => {
    switch (focusRequest?.target) {
      case 'displayName':
        displayNameRef.current?.focus()
        break
      case 'language':
        focusChoice(languageRef.current)
        break
      case 'theme':
        focusChoice(themeRef.current)
        break
      case 'fontSize':
        focusChoice(fontSizeRef.current)
        break
      case 'save':
        saveRef.current?.focus()
        break
      case undefined:
        break
    }
  }, [focusRequest])

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    save()
  }

  const errorOf = (field: PreferencesField): string | undefined => {
    const key = fieldErrors[field]
    return key === undefined ? undefined : t(key)
  }

  const legend = (nameKey: string, field: PreferencesField, hintKey?: string): ReactNode => (
    <Legend
      name={t(nameKey)}
      hint={hintKey === undefined ? undefined : t(hintKey)}
      error={errorOf(field)}
    />
  )

  return (
    <form
      className="preferences-form"
      aria-labelledby={headingId}
      noValidate
      onSubmit={handleSubmit}
      data-testid="preferences-form"
    >
      {alert !== undefined && (
        <div data-testid="preferences-alert">
          <Alert
            variant="danger"
            onDismiss={dismissAlert}
            dismissLabel={t('preferences.alert.dismiss')}
          >
            {t(alert)}
          </Alert>
        </div>
      )}
      <FormField label={t('preferences.displayName')} required error={errorOf('displayName')}>
        <TextInput
          ref={displayNameRef}
          autoComplete="name"
          value={form.displayName}
          onChange={setDisplayName}
          readOnly={saving}
          data-testid="preferences-display-name-input"
        />
      </FormField>
      <div ref={languageRef} data-testid="preferences-language">
        <RadioGroup
          name="preferences-language"
          className="preferences-radio-group"
          legend={legend('preferences.language', 'language', 'preferences.language.hint')}
          options={LANGUAGE_ORDER.map((language) => ({
            value: language,
            label: LANGUAGE_NAMES[language],
            lang: language,
          }))}
          value={form.language}
          onChange={(value) => {
            if (isDisplayLanguage(value)) {
              setLanguage(value)
            }
          }}
        />
      </div>
      <p className="preferences-hint" data-testid="preferences-appearance-hint">
        {t('preferences.appearance.hint')}
      </p>
      <div ref={themeRef} data-testid="preferences-theme">
        <RadioGroup
          name="preferences-theme"
          className="preferences-radio-group"
          legend={legend('preferences.theme', 'theme')}
          options={THEME_ORDER.map((theme) => ({
            value: theme,
            label: t(`display.theme.${theme}`),
          }))}
          value={form.theme}
          onChange={(value) => {
            if (isThemeChoice(value)) {
              setTheme(value)
            }
          }}
        />
      </div>
      <div ref={fontSizeRef} data-testid="preferences-font-size">
        <RadioGroup
          name="preferences-font-size"
          className="preferences-radio-group"
          legend={legend('preferences.fontSize', 'fontSize')}
          options={FONT_SIZES.map((fontSize) => ({
            value: fontSize,
            label: t(`display.fontSize.${fontSize}`),
          }))}
          value={form.fontSize}
          onChange={(value) => {
            if (isFontSize(value)) {
              setFontSize(value)
            }
          }}
        />
      </div>
      <div className="preferences-actions">
        <Button
          variant="secondary"
          disabled={!dirty || saving}
          onClick={reset}
          data-testid="preferences-reset-button"
        >
          {t('preferences.reset')}
        </Button>
        <Button ref={saveRef} type="submit" loading={saving} data-testid="preferences-save-button">
          {saving ? t('preferences.saving') : t('preferences.save')}
        </Button>
      </div>
    </form>
  )
}
