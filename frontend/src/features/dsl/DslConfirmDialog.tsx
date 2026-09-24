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
// 確かめる表示（interaction-spec.md の DslConfirmDialog、BR7.1、AC1.1.6・AC2.1.3・AC3.3.1・AC4.1.2・AC5.1.4）。
// 置き換え・適用・破棄の確認。make-you-chic-ui の Modal を使い、はじめのフォーカスは「やめる」に置く。
// Escape と「やめる」で閉じて何も変えない。閉じるとフォーカスは開く前のボタンへ戻る（Modal のフォーカスの決まり）。
// 取り消しにくい操作のため、背景のクリックでは閉じない（Modal は背景の押下でも閉じるため、その押下だけを無視する）。
import { Button, Modal } from 'make-you-chic-ui'
import { useEffect, useRef } from 'react'
import { useDisplayLanguage } from '../../app/i18n/I18nProvider'
import type { PreviewRef } from './api/types'
import type { ChangeCounts } from './diffCounts'
import { formatDateTime } from './format'
import { useDslText } from './useDslText'
import './DslConfirmDialog.css'

/** 置き換えを起こす操作 */
export type ReplaceOperation = 'generate' | 'submit' | 'restore'

/** 確かめる内容 */
export type DslConfirm =
  | { kind: 'replace'; operation: ReplaceOperation; preview: PreviewRef }
  | { kind: 'apply'; tables: ChangeCounts; columns: ChangeCounts; warningCount: number }
  | { kind: 'discard'; preview: PreviewRef }

export interface DslConfirmDialogProps {
  /** 表示中の確認（閉じているときは null） */
  confirm: DslConfirm | null
  onConfirm: () => void
  onCancel: () => void
  /** 日時の時差（省略すると端末の時差） */
  timeZone?: string
}

const OVERLAY_CLASS = 'mycui-modal-overlay'

const REPLACE_BODY: Record<ReplaceOperation, string> = {
  generate: 'dsl.confirm.replaceGenerate',
  submit: 'dsl.confirm.replaceSubmit',
  restore: 'dsl.confirm.replaceRestore',
}

/** 確かめる表示 */
export function DslConfirmDialog({
  confirm,
  onConfirm,
  onCancel,
  timeZone,
}: DslConfirmDialogProps) {
  const t = useDslText()
  const language = useDisplayLanguage()
  const cancelRef = useRef<HTMLButtonElement>(null)
  const pressedOnBackdrop = useRef(false)
  const open = confirm !== null

  useEffect(() => {
    if (!open) {
      return
    }
    // Modal より先に押下を受け、背景そのものの押下かどうかを覚える（背景では閉じないため）。
    function remember(event: MouseEvent): void {
      const target = event.target
      pressedOnBackdrop.current =
        target instanceof Element && target.classList.contains(OVERLAY_CLASS)
    }
    window.addEventListener('mousedown', remember, true)
    return () => window.removeEventListener('mousedown', remember, true)
  }, [open])

  function handleClose(): void {
    if (pressedOnBackdrop.current) {
      pressedOnBackdrop.current = false
      return
    }
    onCancel()
  }

  if (confirm === null) {
    return null
  }
  const title =
    confirm.kind === 'replace'
      ? t('dsl.confirm.replaceTitle')
      : confirm.kind === 'apply'
        ? t('dsl.confirm.applyTitle')
        : t('dsl.confirm.discardTitle')
  const confirmLabel =
    confirm.kind === 'replace'
      ? t('dsl.action.replace')
      : confirm.kind === 'apply'
        ? t('dsl.action.apply')
        : t('dsl.action.discard')

  return (
    <Modal open title={title} onClose={handleClose} initialFocusRef={cancelRef}>
      <div className="dsl-confirm" data-testid={`dsl-confirm-${confirm.kind}`}>
        {confirm.kind === 'replace' && (
          <>
            <p className="dsl-confirm-text">{t(REPLACE_BODY[confirm.operation])}</p>
            <PreviewFacts
              label={t('dsl.confirm.currentPreview')}
              preview={confirm.preview}
              at={formatDateTime(confirm.preview.at, language, timeZone)}
            />
          </>
        )}
        {confirm.kind === 'apply' && (
          <>
            <p className="dsl-confirm-text">{t('dsl.confirm.applyBody')}</p>
            <dl className="dsl-confirm-facts">
              <div>
                <dt>{t('dsl.confirm.diffLabel')}</dt>
                <dd data-testid="dsl-confirm-diff">
                  <span>{t('dsl.confirm.tableCounts', { ...confirm.tables })}</span>
                  <span>{t('dsl.confirm.columnCounts', { ...confirm.columns })}</span>
                </dd>
              </div>
              <div>
                <dt>{t('dsl.confirm.warningLabel')}</dt>
                <dd data-testid="dsl-confirm-warnings">
                  {confirm.warningCount > 0
                    ? t('dsl.confirm.warningCount', { count: confirm.warningCount })
                    : t('dsl.confirm.noWarning')}
                </dd>
              </div>
            </dl>
          </>
        )}
        {confirm.kind === 'discard' && (
          <>
            <p className="dsl-confirm-text">{t('dsl.confirm.discardBody')}</p>
            <PreviewFacts
              label={t('dsl.confirm.currentPreview')}
              preview={confirm.preview}
              at={formatDateTime(confirm.preview.at, language, timeZone)}
            />
          </>
        )}
        <div className="dsl-confirm-actions">
          <Button
            ref={cancelRef}
            variant="secondary"
            onClick={onCancel}
            data-testid="dsl-confirm-cancel"
          >
            {t('dsl.action.cancel')}
          </Button>
          <Button
            variant={confirm.kind === 'discard' ? 'danger' : 'primary'}
            onClick={onConfirm}
            data-testid="dsl-confirm-ok"
          >
            {confirmLabel}
          </Button>
        </div>
      </div>
    </Modal>
  )
}

interface PreviewFactsProps {
  label: string
  preview: PreviewRef
  at: string
}

/** 今のプレビューの出どころ・置いた人・日時 */
function PreviewFacts({ label, preview, at }: PreviewFactsProps) {
  const t = useDslText()
  return (
    <dl className="dsl-confirm-facts" data-testid="dsl-confirm-preview">
      <div>
        <dt>{label}</dt>
        <dd>
          <span>
            {t('dsl.status.source')} {t(`dsl.source.${preview.source}`)}
          </span>
          <span>
            {t('dsl.status.placedBy')} {preview.by.email ?? t('dsl.user.unknown')}
          </span>
          <span>{at}</span>
        </dd>
      </div>
    </dl>
  )
}
