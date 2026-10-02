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
// 利用者の管理の画面（S1、frontend-components.md の 1節・4節、functional-spec.md の 4.1・W1〜W12、mockups.md の S1）。
// 見出し（h1）、検索、業務の失敗の知らせ（Alert warning・[×]）、読み込みの失敗（Alert danger と「もう一度読み込む」）、
// 一覧の見出し（h2、tabIndex=-1、フォーカスの行き先）、状態の読み上げ（role="status"）、表、確かめと入力の表示を並べる。
// 状態と操作は useUserAdmin が持ち、この部品は子の部品に値と操作を渡すだけ。U4 の口（useAdminForbidden・
// useApplyOwnProfile）と画面の言語（useDisplaySettings）を受けて渡す。403 の表示（S6）は骨組みの AppFrame が受け持つため、
// この画面は forbidden の状態を持たない（機能設計 10節の (c)）。ページ送りと検索で URL を変えない（D2）。
// フォーカスは、読み込み中でない描画の確定の後に、求められた行き先（focusTarget.ts で決める）へ当てる。
import { Alert, Button } from 'make-you-chic-ui'
import { useEffect, useId, useRef } from 'react'
import { useAdminForbidden } from '../../app/admin-forbidden/AdminForbiddenProvider'
import { useDisplaySettings } from '../../app/display-settings/DisplaySettingsProvider'
import { useApplyOwnProfile } from '../../app/display-settings/useApplyOwnProfile'
import { userAdminApi, type UserAdminApi } from './api/userAdminApi'
import { ConfirmActionDialog } from './ConfirmActionDialog'
import { EditProfileDialog } from './EditProfileDialog'
import { focusAfterReload } from './focusTarget'
import { UserRowActions } from './UserRowActions'
import { UserSearchBox } from './UserSearchBox'
import { UserTable } from './UserTable'
import { useUserAdmin } from './useUserAdmin'
import { useUserAdminText } from './useUserAdminText'
import './UserAdminPage.css'

export interface UserAdminPageProps {
  /** API の関数の集まり（テストで差し替える） */
  api?: UserAdminApi
  /** 日時の時間帯（省略すると端末の時間帯。テストで固定する） */
  timeZone?: string
  /** 今の時刻を返す関数（解除の予定の時刻が今日かの判定。テストで固定する） */
  now?: () => Date
}

function currentTime(): Date {
  return new Date()
}

/** 利用者の管理の画面 */
export function UserAdminPage({
  api = userAdminApi,
  timeZone,
  now = currentTime,
}: UserAdminPageProps) {
  const t = useUserAdminText()
  const { language } = useDisplaySettings()
  const reportForbidden = useAdminForbidden()
  const applyOwnProfile = useApplyOwnProfile()
  const state = useUserAdmin({ api, t, reportForbidden, applyOwnProfile })
  const headingId = useId()
  const headingRef = useRef<HTMLHeadingElement>(null)
  const retryRef = useRef<HTMLButtonElement>(null)
  const actionRefs = useRef(new Map<number, HTMLSpanElement>())
  const { view, list, focusTarget, consumeFocus } = state

  useEffect(() => {
    if (focusTarget === null || view === 'loading' || view === 'reloading') {
      return
    }
    const place = focusAfterReload(focusTarget, list?.items.map((row) => row.userId) ?? [])
    if (place?.kind === 'row') {
      actionRefs.current.get(place.userId)?.querySelector('button')?.focus()
    } else if (place?.kind === 'retry') {
      ;(retryRef.current ?? headingRef.current)?.focus()
    } else {
      headingRef.current?.focus()
    }
    consumeFocus()
  }, [focusTarget, view, list, consumeFocus])

  function registerActions(userId: number) {
    return (element: HTMLSpanElement | null) => {
      if (element) {
        actionRefs.current.set(userId, element)
      } else {
        actionRefs.current.delete(userId)
      }
    }
  }

  const listBusy = state.loading
  const showTable =
    list !== null && (view === 'populated' || view === 'empty-page' || view === 'reloading')
  const statusText =
    view === 'loading'
      ? t('useradmin.list.loading')
      : view === 'reloading'
        ? t('useradmin.list.reloading')
        : state.liveMessage
  const statusVisible = view === 'loading' || view === 'reloading'

  return (
    <div className="useradmin-page" data-testid="useradmin-page">
      <h1 className="useradmin-heading">{t('useradmin.title')}</h1>
      <UserSearchBox
        value={state.searchInput}
        error={state.searchError !== null ? t(state.searchError) : undefined}
        busy={listBusy}
        onChange={state.changeSearchInput}
        onSearch={state.submitSearch}
        onClear={state.clearSearch}
      />
      {state.failure !== null && (
        <div data-testid="useradmin-failure-alert">
          <Alert
            variant="warning"
            onDismiss={state.dismissFailure}
            dismissLabel={t('useradmin.action.dismiss')}
          >
            {t(
              state.failure.key,
              state.failure.name !== undefined ? { name: state.failure.name } : undefined,
            )}
          </Alert>
        </div>
      )}
      {view === 'load-error' && (
        <div className="useradmin-load-error" data-testid="useradmin-load-error">
          <Alert variant="danger">{t('useradmin.list.loadFailed')}</Alert>
          <div>
            <Button
              ref={retryRef}
              variant="secondary"
              onClick={state.retry}
              data-testid="useradmin-retry"
            >
              {t('useradmin.action.retry')}
            </Button>
          </div>
        </div>
      )}
      <section className="useradmin-list" aria-labelledby={headingId}>
        <h2
          id={headingId}
          ref={headingRef}
          tabIndex={-1}
          className="useradmin-list-heading"
          data-testid="useradmin-list-heading"
        >
          {t('useradmin.list.heading')}
        </h2>
        <p
          role="status"
          className={statusVisible ? 'useradmin-status' : 'useradmin-visually-hidden'}
          data-testid="useradmin-status"
        >
          {statusText}
        </p>
        {view === 'loading' && (
          <div className="useradmin-skeleton" aria-hidden="true" data-testid="useradmin-skeleton">
            <span className="useradmin-skeleton-row" />
            <span className="useradmin-skeleton-row" />
            <span className="useradmin-skeleton-row" />
          </div>
        )}
        {view === 'empty-search' && (
          <p className="useradmin-empty" data-testid="useradmin-empty-search">
            {t('useradmin.search.empty', { text: state.searchText })}
          </p>
        )}
        {view === 'empty-page' && (
          <p className="useradmin-empty" data-testid="useradmin-empty-page">
            {t('useradmin.list.emptyPage')}
          </p>
        )}
        {showTable && (
          <UserTable
            rows={list.items}
            total={list.total}
            page={state.page}
            onPageChange={state.goToPage}
            busy={listBusy}
            label={t('useradmin.list.heading')}
            language={language}
            timeZone={timeZone}
            now={now()}
            renderActions={(row) => (
              <UserRowActions
                user={row}
                busy={listBusy || state.busyUserId === row.userId}
                onSelect={(action) => state.selectAction(row, action)}
                containerRef={registerActions(row.userId)}
              />
            )}
          />
        )}
      </section>
      <ConfirmActionDialog
        state={state.confirm}
        slow={state.slow}
        onConfirm={() => void state.confirmAction()}
        onCancel={state.closeConfirm}
      />
      <EditProfileDialog
        state={state.edit}
        slow={state.slow}
        onChangeName={state.changeEditName}
        onChangeLanguage={state.changeEditLanguage}
        onSave={() => void state.saveEdit()}
        onCancel={state.closeEdit}
      />
    </div>
  )
}
