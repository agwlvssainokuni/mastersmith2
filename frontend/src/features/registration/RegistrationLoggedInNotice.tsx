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
// ログインしたまま開いたときの案内（functional-spec.md の W3、Q1 A、D4）。情報の Alert と2つのボタンを置く。
// ログアウト中は「ログアウトして続ける」を Button の loading（aria-disabled・aria-busy、押しても onLogout を呼ばない、
// フォーカスはボタンに残る）にし、文言を「ログアウトしています」に変える（CR6.3）。
import { Alert, Button } from 'make-you-chic-ui'
import { useMessages } from '../../app/i18n/I18nProvider'
import './RegistrationPage.css'

export interface RegistrationLoggedInNoticeProps {
  /** ログアウトの途中か */
  loggingOut: boolean
  /** 「ログアウトして続ける」を押したとき */
  onLogout: () => void
  /** 「ホームへ戻る」を押したとき */
  onHome: () => void
}

/** ログイン中の案内 */
export function RegistrationLoggedInNotice({
  loggingOut,
  onLogout,
  onHome,
}: RegistrationLoggedInNoticeProps) {
  const t = useMessages()
  return (
    <div className="registration-section" data-testid="registration-logged-in-notice">
      <Alert variant="info">{t('registration.loggedIn.message')}</Alert>
      <div className="registration-actions">
        <Button variant="secondary" onClick={onHome} data-testid="registration-home-button">
          {t('registration.loggedIn.home')}
        </Button>
        <Button
          variant="primary"
          loading={loggingOut}
          onClick={onLogout}
          data-testid="registration-logout-button"
        >
          {loggingOut ? t('registration.loggedIn.loggingOut') : t('registration.loggedIn.logout')}
        </Button>
      </div>
    </div>
  )
}
