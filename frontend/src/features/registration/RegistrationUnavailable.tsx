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
// 使えないリンクの表示（functional-spec.md の W11、Q4 B、AC3.2.2・AC3.2.11、NFR3、CR6.5）。理由によらず同じ文と導線で、
// トークン・メールアドレス・有効期限の長さを含めない。「ログインの画面へ」は移動の操作のため、ボタンの見た目にしないリンク。
// 外部の URL を置かない（NFR1.4）。
import { Alert } from 'make-you-chic-ui'
import { Link } from 'react-router'
import { useMessages } from '../../app/i18n/I18nProvider'
import './RegistrationPage.css'

/** 使えないリンクの表示 */
export function RegistrationUnavailable() {
  const t = useMessages()
  return (
    <div className="registration-section" data-testid="registration-unavailable">
      <Alert variant="warning">{t('registration.unavailable')}</Alert>
      <Link to="/login" className="registration-link" data-testid="registration-to-login-link">
        {t('registration.toLogin')}
      </Link>
    </div>
  )
}
