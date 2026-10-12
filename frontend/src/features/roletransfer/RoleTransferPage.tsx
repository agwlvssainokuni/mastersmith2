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
// S7 権限の受け渡し（Intent 261004-role-menu の U6、functional-spec.md の 3.5・4.5、D22〜D26・D28〜D31、
// frontend-components.md の 5節・8節、AC3.1.1〜AC3.1.14）。見出し（h1）、画面の上の知らせ（Alert）、書き出す（間は押せない、
// 上限超えの案内）、読み込む（ファイルの選択・確かめる）、確かめの結果の表または誤りの一覧、適用する（確かめ）を並べる。
// 状態と操作は useRoleTransfer が持つ。確かめた本文と指紋は画面の状態にだけ持つ（NFR 設計 Q1 A）。
import { Alert, Button } from 'make-you-chic-ui'
import { useRef } from 'react'
import { useAdminForbidden } from '../../app/admin-forbidden/AdminForbiddenProvider'
import { roleTransferApi, type RoleTransferApi } from './api/roleTransferApi'
import { ApplyConfirmDialog } from './ApplyConfirmDialog'
import { TransferCheckTable } from './TransferCheckTable'
import { TransferErrorList } from './TransferErrorList'
import { TransferFilePicker } from './TransferFilePicker'
import { MAX_TRANSFER_MIB } from './transferLimits'
import { useRoleTransfer } from './useRoleTransfer'
import { useRoleTransferText } from './useRoleTransferText'
import './RoleTransferPage.css'

export interface RoleTransferPageProps {
  /** API の関数の集まり（テストで差し替える） */
  api?: RoleTransferApi
}

/** S7 権限の受け渡し */
export function RoleTransferPage({ api = roleTransferApi }: RoleTransferPageProps) {
  const t = useRoleTransferText()
  const reportForbidden = useAdminForbidden()
  const state = useRoleTransfer({ api, reportForbidden })
  const applyRef = useRef<HTMLButtonElement>(null)
  const { phase, check, notice } = state
  const busy = state.submitting
  const canCheck =
    state.fileInfo !== null &&
    !busy &&
    (phase === 'fileSelected' ||
      phase === 'stale' ||
      phase === 'rejected' ||
      phase === 'invalid' ||
      phase === 'checked' ||
      phase === 'noChange')
  const showResult =
    check !== null && (phase === 'checked' || phase === 'noChange' || phase === 'applying')
  const cleared =
    check?.roles
      .filter((role) => role.action === 'REPLACE')
      .reduce((sum, role) => sum + role.cleared, 0) ?? 0

  return (
    <div className="roletransfer-page" data-testid="roletransfer-page">
      <h1 className="roletransfer-heading">{t('roletransfer.title')}</h1>
      <div className="roletransfer-notice" data-testid="roletransfer-notice">
        {notice !== null && (
          <Alert
            variant={notice.variant}
            onDismiss={state.dismissNotice}
            dismissLabel={t('roletransfer.action.dismiss')}
          >
            {t(notice.key, notice.values)}
          </Alert>
        )}
      </div>
      <section className="roletransfer-section" aria-labelledby="roletransfer-export-heading">
        <h2 id="roletransfer-export-heading" className="roletransfer-subheading">
          {t('roletransfer.export.heading')}
        </h2>
        <p className="roletransfer-text">{t('roletransfer.export.description')}</p>
        <div>
          <Button
            variant="secondary"
            loading={state.exporting}
            onClick={() => void state.exportFile()}
            data-testid="roletransfer-export"
          >
            {t(state.exporting ? 'roletransfer.action.processing' : 'roletransfer.export.button')}
          </Button>
        </div>
        {state.exceeds && (
          <p role="status" className="roletransfer-text" data-testid="roletransfer-export-exceeds">
            {t('roletransfer.export.exceeds', { limit: MAX_TRANSFER_MIB })}
          </p>
        )}
      </section>
      <section className="roletransfer-section" aria-labelledby="roletransfer-import-heading">
        <h2 id="roletransfer-import-heading" className="roletransfer-subheading">
          {t('roletransfer.import.heading')}
        </h2>
        <p className="roletransfer-text">{t('roletransfer.import.description')}</p>
        <TransferFilePicker
          key={state.pickerKey}
          fileInfo={state.fileInfo}
          tooLarge={phase === 'fileTooLarge'}
          disabled={busy}
          onSelect={state.selectFile}
        />
        <div className="roletransfer-actions roletransfer-actions-start">
          <Button
            variant="secondary"
            loading={phase === 'checking'}
            disabled={!canCheck}
            onClick={state.runCheck}
            data-testid="roletransfer-check"
          >
            {t(phase === 'checking' ? 'roletransfer.check.checking' : 'roletransfer.check.button')}
          </Button>
        </div>
        {phase === 'checking' && state.slow && (
          <p role="status" className="roletransfer-text" data-testid="roletransfer-check-slow">
            {t('roletransfer.slow.check')}
          </p>
        )}
        {phase === 'invalid' && state.errors !== null && (
          <TransferErrorList errors={state.errors} />
        )}
        {showResult && check !== null && (
          <section className="roletransfer-section" aria-labelledby="roletransfer-result-heading">
            <h3 id="roletransfer-result-heading" className="roletransfer-subheading">
              {t('roletransfer.result.heading')}
            </h3>
            {phase === 'noChange' ? (
              <p className="roletransfer-text" data-testid="roletransfer-no-change">
                {t('roletransfer.noChangeResult')}
              </p>
            ) : (
              <TransferCheckTable check={check} />
            )}
            <div>
              <Button
                ref={applyRef}
                variant="primary"
                disabled={phase !== 'checked'}
                onClick={state.openApply}
                data-testid="roletransfer-apply"
              >
                {t('roletransfer.apply.button')}
              </Button>
            </div>
          </section>
        )}
      </section>
      {state.confirmOpen && check !== null && (
        <ApplyConfirmDialog
          roleCount={check.roles.length}
          cleared={cleared}
          submitting={busy}
          slow={state.slow}
          onConfirm={state.apply}
          onCancel={state.cancelApply}
          finalFocusRef={applyRef}
        />
      )}
    </div>
  )
}
