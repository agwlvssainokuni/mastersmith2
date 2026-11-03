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
// - 見出し・説明・作業ロールの 1 行・業務のカード・管理のカード・今の案内の順（Intent 261102-timezone-ui-polish の U5、
//   D14〜D21）。新しい API は呼ばず、すでにシェルが読む状態だけを使う。カードが 0 件の見出しは出さない。
import { Alert, Button } from 'make-you-chic-ui'
import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { useDisplayLanguage, useMessages } from '../i18n/I18nProvider'
import { useLoginState } from '../login-state/LoginStateGate'
import { useBusinessNavigation } from '../navigation/BusinessNavigationProvider'
import { useFeatureRegistry } from '../registry/FeatureRegistryContext'
import { useWorkRole, type WorkRoleStatus } from '../work-role/WorkRoleProvider'
import { MY_PERMISSIONS_PATH, useHasMyPermissions } from '../work-role/WorkRoleSwitcher'
import { HomeCardList } from './HomeCardList'
import { buildHomeCards } from './homeCards'
import './HomeCards.css'
import './Page.css'

/** 作業ロールの読み込みが済んでいる状態（この間だけ作業ロールの 1 行を出す。D15） */
const WORK_ROLE_LOADED: readonly WorkRoleStatus[] = ['ready', 'reloading', 'switching']

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
  const { t: format } = useTranslation()
  const language = useDisplayLanguage()
  const business = useBusinessNavigation()
  const workRole = useWorkRole()
  const loginState = useLoginState()
  const registrations = useFeatureRegistry()
  const hasMyPermissions = useHasMyPermissions()
  const noticeKey = homeNoticeKey(business, workRole.current !== null)
  const cards = useMemo(
    () =>
      buildHomeCards({
        business,
        loginState,
        registrations,
        language,
        t: (key) => format(key),
      }),
    [business, loginState, registrations, language, format],
  )
  const showWorkRole = WORK_ROLE_LOADED.includes(workRole.status)

  return (
    <section className="page" data-testid="home-page" aria-labelledby="home-page-heading">
      <h1 id="home-page-heading" className="page-heading">
        {t('home.heading')}
      </h1>
      <p className="page-description">{t('home.description')}</p>
      {showWorkRole ? (
        <p className="home-work-role" data-testid="home-work-role">
          {workRole.current !== null
            ? format('home.workRole', { name: workRole.current.name })
            : t('home.workRoleNone')}
        </p>
      ) : null}
      {cards.business.length > 0 ? (
        <section
          className="home-section"
          data-testid="home-business-section"
          aria-labelledby="home-business-heading"
        >
          <h2 id="home-business-heading" className="home-section-heading">
            {t('home.section.business')}
          </h2>
          <HomeCardList
            headingId="home-business-heading"
            cards={cards.business}
            testIdPrefix="home-business"
          />
        </section>
      ) : null}
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
      {cards.admin.length > 0 ? (
        <section
          className="home-section"
          data-testid="home-admin-section"
          aria-labelledby="home-admin-heading"
        >
          <h2 id="home-admin-heading" className="home-section-heading">
            {t('home.section.admin')}
          </h2>
          <HomeCardList
            headingId="home-admin-heading"
            cards={cards.admin}
            testIdPrefix="home-admin"
          />
        </section>
      ) : null}
    </section>
  )
}
