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
// プリファレンスの読み込みの失敗の表示（functional-spec.md の W2、D11、frontend-components.md の 4節）。
// 失敗の Alert（role="alert"）と「もう一度読み込む」。サーバーの detail は受け取らず、この機能の文言だけを出す（D13）。
import { Alert, Button } from 'make-you-chic-ui'
import { useEffect, useRef } from 'react'
import { useMessages } from '../../app/i18n/I18nProvider'
import { LOAD_FAILED_KEY } from './errorMessages'
import './PreferencesForm.css'
import './PreferencesPage.css'

export interface PreferencesLoadFailureProps {
  /** 「もう一度読み込む」を押したとき */
  onRetry: () => void
  /** 読み直しの失敗の回（変わったら「もう一度読み込む」へフォーカスを移す。W2 の4） */
  retryFocusSeq?: number
}

/** 読み込みの失敗の表示 */
export function PreferencesLoadFailure({ onRetry, retryFocusSeq }: PreferencesLoadFailureProps) {
  const t = useMessages()
  const retryRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (retryFocusSeq !== undefined) {
      retryRef.current?.focus()
    }
  }, [retryFocusSeq])

  return (
    <div className="preferences-section" data-testid="preferences-load-failed">
      <Alert variant="danger">{t(LOAD_FAILED_KEY)}</Alert>
      <div className="preferences-actions">
        <Button
          ref={retryRef}
          variant="secondary"
          onClick={onRetry}
          data-testid="preferences-reload-button"
        >
          {t('preferences.load.retry')}
        </Button>
      </div>
    </div>
  )
}
