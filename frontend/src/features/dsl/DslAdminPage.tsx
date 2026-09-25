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
// DSL の管理画面（frontend-components.md の 1.1・2節、functional-spec.md の 4〜7節、mockups.md、interaction-spec.md）。
// 今の状態を上部に常に示し、プレビュー・投入・履歴のタブ、確かめる表示、画面の中の失敗の知らせ、読み上げの領域を持つ。
// 状態と操作は useDslAdmin が持ち、この部品は子の部品に値と操作を渡す。管理者でない（403）ときは、既存の管理者向け
// 領域と同じく「ページが見つかりません」を示す。判定はサーバー側（401・403）で行う（BR1.2・BR8.2）。
import { Alert } from 'make-you-chic-ui'
import { NotFoundPage } from '../../app/pages/NotFoundPage'
import { dslApi, type DslApi } from './api/dslApi'
import { saveFile } from './api/saveFile'
import type { DslFile } from './api/types'
import { DslConfirmDialog } from './DslConfirmDialog'
import { DslErrorList } from './DslErrorList'
import { DslHistoryTable } from './DslHistoryTable'
import { DslPreviewPanel } from './DslPreviewPanel'
import { DslStatusPanel } from './DslStatusPanel'
import { DslSubmitForm } from './DslSubmitForm'
import { DslTabs } from './DslTabs'
import { useDslAdmin } from './useDslAdmin'
import { useDslText } from './useDslText'
import './DslCommon.css'
import './DslAdminPage.css'

export interface DslAdminPageProps {
  /** API の関数の集まり（テストで差し替える） */
  api?: DslApi
  /** ダウンロードしたファイルの保存（テストで差し替える） */
  save?: (file: DslFile) => void
  /** 日時の時差（省略すると端末の時差。テストで固定する） */
  timeZone?: string
}

/** DSL の管理画面 */
export function DslAdminPage({ api = dslApi, save = saveFile, timeZone }: DslAdminPageProps) {
  const t = useDslText()
  const state = useDslAdmin(api, save, t)
  const busy = state.busy !== null

  if (state.forbidden) {
    return (
      <div data-testid="dsl-admin-page">
        <NotFoundPage />
      </div>
    )
  }

  const panels = {
    preview: (
      <DslPreviewPanel
        preview={state.preview}
        loadState={state.previewLoad}
        emptyReason={state.emptyReason}
        replacedByOther={state.replacedByOther}
        busy={busy}
        applying={state.busy === 'apply'}
        discarding={state.busy === 'discard'}
        headingRef={state.previewHeadingRef}
        onDownload={state.downloadPreview}
        onDiscard={state.discard}
        onApply={state.apply}
        onRetry={state.retryPreview}
        onReadSchema={state.generate}
        onGoToSubmit={() => state.selectTab('submit')}
        onShowLatest={state.showLatest}
      />
    ),
    submit: (
      <DslSubmitForm
        input={state.submitInput}
        onChange={state.setSubmitInput}
        busy={busy}
        submitting={state.busy === 'submit'}
        onSubmit={state.submit}
        errors={
          state.submitErrors !== undefined ? (
            <DslErrorList report={state.submitErrors} data-testid="dsl-submit-errors" />
          ) : null
        }
      />
    ),
    history: (
      <section
        className="dsl-history"
        aria-labelledby="dsl-history-heading"
        data-testid="dsl-history-panel"
      >
        <h2 id="dsl-history-heading" className="dsl-section-heading">
          {t('dsl.history.heading')}
        </h2>
        {state.restoreErrors !== undefined && (
          <DslErrorList report={state.restoreErrors} data-testid="dsl-restore-errors" />
        )}
        <DslHistoryTable
          entries={state.history}
          loadState={state.historyLoad}
          busy={busy}
          restoringId={state.restoringId}
          onRestore={state.restore}
          onDownloadApplied={state.downloadApplied}
          onRetry={state.retryHistory}
          timeZone={timeZone}
        />
      </section>
    ),
  }

  return (
    <div className="dsl-admin" data-testid="dsl-admin-page" aria-busy={busy}>
      <h1 className="dsl-admin-heading">{t('dsl.page.heading')}</h1>
      <DslStatusPanel
        status={state.status}
        loadState={state.statusLoad}
        busy={busy}
        generating={state.busy === 'generate'}
        onReadSchema={state.generate}
        onRetry={state.retryStatus}
        timeZone={timeZone}
      />
      {state.alertKey !== null && (
        <Alert variant="danger" onDismiss={state.dismissAlert} dismissLabel={t('dsl.action.close')}>
          <span data-testid="dsl-alert">{t(state.alertKey)}</span>
        </Alert>
      )}
      <DslTabs selected={state.selectedTab} onSelect={state.selectTab} panels={panels} />
      <DslConfirmDialog
        confirm={state.confirm}
        onConfirm={state.confirmDialog}
        onCancel={state.cancelDialog}
        timeZone={timeZone}
      />
      <div aria-live="polite" className="dsl-visually-hidden" data-testid="dsl-live">
        {state.liveMessage}
      </div>
    </div>
  )
}
