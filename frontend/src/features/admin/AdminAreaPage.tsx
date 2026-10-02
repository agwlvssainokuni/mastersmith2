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
// 権限が無い（403・ACCESS_DENIED）ときは失敗を骨組みの useAdminForbidden に渡し、ShellLayout が
// コンテンツの領域を S6 に置き換えるため、ここでは何も描かない（U4 の FC 6.2）。そのほかの失敗は
// 一般的なエラーの文言を領域の中に出す。結果はメモリに残さず、表示のたびにやり直す。
import { Alert } from 'make-you-chic-ui'
import { useEffect, useState } from 'react'
import { useAdminForbidden } from '../../app/admin-forbidden/AdminForbiddenProvider'
import { useMessages } from '../../app/i18n/I18nProvider'
import { requestAdminCheck } from './adminApi'
import { statusFromError, type AdminAreaStatus } from './adminAreaStatus'
import { AdminPlaceholder } from './AdminPlaceholder'

/** 管理者向け領域の画面 */
export function AdminAreaPage() {
  const t = useMessages()
  const [status, setStatus] = useState<AdminAreaStatus>('Checking')
  const reportForbidden = useAdminForbidden()

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
          setStatus(statusFromError(error, reportForbidden))
        }
      })
    return () => {
      active = false
    }
    // 確認は表示のたびに1回だけ行う。reportForbidden は画面の URL が変わらない限り同じ関数のため、
    // 依存に入れても確認を繰り返さない（FC 3.3）。
  }, [reportForbidden])

  if (status === 'Checking') {
    return (
      <div data-testid="admin-area-page" aria-busy="true">
        <p role="status" data-testid="admin-area-checking">
          {t('admin.area.checking')}
        </p>
      </div>
    )
  }
  if (status === 'Forbidden') {
    // ShellLayout が S6 に置き換えている（この部品はすぐに外れる）。
    return null
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
