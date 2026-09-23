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
// 管理者向け領域（BR5.2、NFR9.1）。表示するたびに確認用 API を呼び、確認が終わるまで中身を表示しない。
// 403 は「ページが見つかりません」（U1 の NotFoundPage）、そのほかの失敗は一般的なエラーの文言を領域の中に出す。
// 結果はメモリに残さず、表示のたびにやり直す。
import { Alert } from 'make-you-chic-ui'
import { useEffect, useState } from 'react'
import { useMessages } from '../../app/i18n/I18nProvider'
import { NotFoundPage } from '../../app/pages/NotFoundPage'
import { requestAdminCheck } from './adminApi'
import { statusFromError, type AdminAreaStatus } from './adminAreaStatus'
import { AdminPlaceholder } from './AdminPlaceholder'

/** 管理者向け領域の画面 */
export function AdminAreaPage() {
  const t = useMessages()
  const [status, setStatus] = useState<AdminAreaStatus>('Checking')

  useEffect(() => {
    // 表示のたびに（この部品が作られるたびに）確認をやり直す。初めの状態は Checking のため、ここでは設定し直さない。
    let active = true
    requestAdminCheck()
      .then(() => {
        if (active) {
          setStatus('Shown')
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setStatus(statusFromError(error))
        }
      })
    return () => {
      active = false
    }
  }, [])

  if (status === 'Checking') {
    return (
      <div data-testid="admin-area-page" aria-busy="true">
        <p role="status" data-testid="admin-area-checking">
          {t('admin.area.checking')}
        </p>
      </div>
    )
  }
  if (status === 'NotFound') {
    return (
      <div data-testid="admin-area-page" aria-busy="false">
        <NotFoundPage />
      </div>
    )
  }
  if (status === 'Error') {
    return (
      <div data-testid="admin-area-page" aria-busy="false">
        <Alert variant="danger">
          <span data-testid="admin-area-error">{t('admin.area.error')}</span>
        </Alert>
      </div>
    )
  }
  return (
    <div data-testid="admin-area-page" aria-busy="false">
      <AdminPlaceholder />
    </div>
  )
}
