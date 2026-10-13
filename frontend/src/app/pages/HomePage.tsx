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
// ホーム（ログイン後の最初の画面）（BR7.9）。見出しと短い説明に加え、業務のメニューの状態で案内を出す
// （Intent 261004-role-menu の U7 の D19・D26、W4.1、計画 12節 D-6）。
// - 読み込み中・idle は出さない。NOT_CONFIGURED・NOTHING_VISIBLE（作業ロールの有無で文を分ける）・読み込みの失敗の
//   案内は make-you-chic-ui の Alert（info、role="status"）。失敗のときは［もう一度］で業務のメニューと作業ロールを読み直す。
// - 自分の権限の画面（/me/permissions）が登録されているときだけ、案内に［自分の権限を見る］を添える。
import { Alert, Button } from 'make-you-chic-ui'
import { Link } from 'react-router'
import { useMessages } from '../i18n/I18nProvider'
import { useBusinessNavigation } from '../navigation/BusinessNavigationProvider'
import { useWorkRole } from '../work-role/WorkRoleProvider'
import { MY_PERMISSIONS_PATH, useHasMyPermissions } from '../work-role/WorkRoleSwitcher'
import './Page.css'

/** 業務のメニューの状態から案内の文言の鍵を決める（出さないときは null）。 */
export function homeNoticeKey(
  business: ReturnType<typeof useBusinessNavigation>,
  hasWorkRole: boolean,
): string | null {
  if (business.status === 'load-error') {
    return 'home.loadError'
  }
  if (business.status !== 'loaded' || business.response === null) {
    return null
  }
  if (business.response.items.length > 0) {
    return null
  }
  if (business.response.emptyReason === 'NOT_CONFIGURED') {
    return 'home.notConfigured'
  }
  return hasWorkRole ? 'home.nothingVisible' : 'home.noRole'
}

/** ホームの画面 */
export function HomePage() {
  const t = useMessages()
  const business = useBusinessNavigation()
  const workRole = useWorkRole()
  const hasMyPermissions = useHasMyPermissions()
  const noticeKey = homeNoticeKey(business, workRole.current !== null)

  return (
    <section className="page" data-testid="home-page" aria-labelledby="home-page-heading">
      <h1 id="home-page-heading" className="page-heading">
        {t('home.heading')}
      </h1>
      <p className="page-description">{t('home.description')}</p>
      {noticeKey !== null ? (
        <Alert variant="info">
          <p className="page-notice" data-testid="home-notice">
            {t(noticeKey)}
          </p>
          {noticeKey === 'home.loadError' ? (
            <Button
              variant="secondary"
              onClick={() => {
                business.reload()
                workRole.reload()
              }}
            >
              {t('home.retry')}
            </Button>
          ) : null}
          {hasMyPermissions ? (
            <Link className="page-link" to={MY_PERMISSIONS_PATH}>
              {t('home.myPermissions')}
            </Link>
          ) : null}
        </Alert>
      ) : null}
    </section>
  )
}
