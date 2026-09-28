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
// ログインの画面の言語の切り替え（U4 の W7・D12、CR1.5・CR6.6、NFR7.4）。
// 「日本語」「English」の2つのボタンの組（aria-pressed）で、選択肢はそれぞれの言語の名前で示し lang 属性を付ける（訳さない）。
// 選ぶと画面の言語（文言・<html lang>・要求の言語）を切り替え、ブラウザの保存の値の言語だけを書き換える。
// フォーカスは選んだボタンのまま（ボタンを描き直さないため）。
import { Button } from 'make-you-chic-ui'
import { useDisplaySettings } from '../../app/display-settings/DisplaySettingsProvider'
import { saveBrowserLanguage } from '../../app/display-settings/displaySettingsStore'
import {
  DISPLAY_LANGUAGES,
  LANGUAGE_NAMES,
  type DisplayLanguage,
} from '../../app/display-settings/displaySettingsTypes'
import { useMessages } from '../../app/i18n/I18nProvider'
import './LoginLanguageSwitch.css'

/** ログインの画面の右上に置く言語の切り替え */
export function LoginLanguageSwitch() {
  const t = useMessages()
  const { language, setLanguage } = useDisplaySettings()

  const choose = (next: DisplayLanguage) => {
    setLanguage(next)
    saveBrowserLanguage(next)
  }

  return (
    <div
      role="group"
      aria-label={t('auth.language.label')}
      className="auth-language-switch"
      data-testid="login-language-switch"
    >
      {DISPLAY_LANGUAGES.map((code) => (
        <Button
          key={code}
          variant={code === language ? 'primary' : 'secondary'}
          size="sm"
          lang={code}
          aria-pressed={code === language}
          onClick={() => choose(code)}
          data-testid={`login-language-switch-${code}`}
        >
          {LANGUAGE_NAMES[code]}
        </Button>
      ))}
    </div>
  )
}
