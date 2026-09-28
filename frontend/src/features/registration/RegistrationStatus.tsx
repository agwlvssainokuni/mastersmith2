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
// リンクの確かめ中と、読み込めないときの表示（functional-spec.md の W4、D5、NFR6.2）。
// 確かめ中は role="status" の文字、読み込めないときは失敗の Alert（role="alert"）と「もう一度読み込む」。
// サーバーの detail は受け取らず、この単位の文言だけを出す（D10）。
import { Alert, Button } from 'make-you-chic-ui'
import { useMessages } from '../../app/i18n/I18nProvider'
import './RegistrationPage.css'

export interface RegistrationStatusProps {
  /** 確かめ中か、読み込めないか */
  kind: 'verifying' | 'loadFailed'
  /** 「もう一度読み込む」を押したとき */
  onReload: () => void
}

/** 確かめ中・読み込めないの表示 */
export function RegistrationStatus({ kind, onReload }: RegistrationStatusProps) {
  const t = useMessages()
  if (kind === 'verifying') {
    return (
      <p role="status" className="registration-verifying" data-testid="registration-verifying">
        {t('registration.verifying')}
      </p>
    )
  }
  return (
    <div className="registration-section" data-testid="registration-load-failed">
      <Alert variant="danger">{t('registration.loadFailed')}</Alert>
      <div className="registration-actions">
        <Button variant="secondary" onClick={onReload} data-testid="registration-reload-button">
          {t('registration.reload')}
        </Button>
      </div>
    </div>
  )
}
