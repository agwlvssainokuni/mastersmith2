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
// プレビュー（interaction-spec.md の DslPreviewPanel、BR5.6〜BR5.9、AC3.1.1〜AC3.1.7・AC3.2.1〜AC3.2.4・AC4.2.1・AC4.2.2）。
// 検証を通ったこと・照合の警告・要約・違い・メニューの木を示し、ダウンロード・破棄・適用の操作を持つ。
// プレビューが無いときは案内と、読み込み・投入のタブへの操作だけを示す（押せない操作を並べない）。
import { Alert, Button, Icon } from 'make-you-chic-ui'
import { useEffect, useRef, useState, type RefObject } from 'react'
import type { Preview, PreviewSummary } from './api/types'
import { DslDiffTable } from './DslDiffTable'
import { DslMenuTree } from './DslMenuTree'
import { DslWarningList, NOT_COMPARED_KINDS } from './DslWarningList'
import type { LoadState } from './loadState'
import { useDslText } from './useDslText'
import './DslCommon.css'
import './DslPreviewPanel.css'

/** プレビューが空になった理由（別の管理者の破棄は文言を変える、mockups.md の 3.6） */
export type EmptyReason = 'none' | 'discardedByOther'

export interface DslPreviewPanelProps {
  /** プレビュー（無ければ null） */
  preview: Preview | null
  loadState: LoadState
  emptyReason: EmptyReason
  /** 適用が別の管理者の置き換えで拒否された（409） */
  replacedByOther: boolean
  /** 何かの操作を処理中（破棄・適用・読み込みのボタンを使えなくする） */
  busy: boolean
  applying: boolean
  discarding: boolean
  /** 見出しへのフォーカスの移し先（空の状態では案内の見出し） */
  headingRef: RefObject<HTMLHeadingElement | null>
  onDownload: () => void
  onDiscard: () => void
  onApply: (previewId: string) => void
  onRetry: () => void
  onReadSchema: () => void
  onGoToSubmit: () => void
  onShowLatest: () => void
}

/** プレビュー */
export function DslPreviewPanel(props: DslPreviewPanelProps) {
  const t = useDslText()
  const { preview, loadState, headingRef } = props

  if (loadState === 'loading') {
    return (
      <p role="status" className="dsl-muted" data-testid="dsl-preview-loading">
        {t('dsl.preview.loading')}
      </p>
    )
  }
  if (loadState === 'failed') {
    return (
      <Alert variant="danger" action={{ label: t('dsl.action.retry'), onClick: props.onRetry }}>
        <span data-testid="dsl-preview-error">{t('dsl.preview.loadFailed')}</span>
      </Alert>
    )
  }
  if (preview === null) {
    return (
      <section
        className="dsl-preview dsl-preview-empty"
        aria-labelledby="dsl-preview-heading"
        data-testid="dsl-preview-empty"
      >
        <h2 id="dsl-preview-heading" ref={headingRef} tabIndex={-1} className="dsl-section-heading">
          {t(
            props.emptyReason === 'discardedByOther'
              ? 'dsl.preview.discardedByOther'
              : 'dsl.preview.emptyHeading',
          )}
        </h2>
        <p className="dsl-muted">{t('dsl.preview.emptyDescription')}</p>
        <div className="dsl-actions dsl-preview-empty-actions">
          <Button
            variant="primary"
            disabled={props.busy}
            onClick={props.onReadSchema}
            data-testid="dsl-preview-empty-read-schema"
          >
            {t('dsl.action.readSchema')}
          </Button>
          <Button
            variant="secondary"
            onClick={props.onGoToSubmit}
            data-testid="dsl-preview-empty-go-submit"
          >
            {t('dsl.action.goToSubmit')}
          </Button>
        </div>
      </section>
    )
  }
  return <PreviewContent {...props} preview={preview} />
}

function PreviewContent(props: DslPreviewPanelProps & { preview: Preview }) {
  const t = useDslText()
  const { preview, headingRef, replacedByOther } = props
  const rejectionRef = useRef<HTMLDivElement>(null)
  const notCompared = preview.warnings.some((w) => NOT_COMPARED_KINDS.has(w.kind))
  const mismatchCount = preview.warnings.filter((w) => !NOT_COMPARED_KINDS.has(w.kind)).length

  useEffect(() => {
    if (replacedByOther) {
      rejectionRef.current?.focus()
    }
  }, [replacedByOther])

  return (
    <section
      className="dsl-preview"
      aria-labelledby="dsl-preview-heading"
      data-testid="dsl-preview-panel"
    >
      <h2 id="dsl-preview-heading" ref={headingRef} tabIndex={-1} className="dsl-section-heading">
        {t('dsl.preview.heading')}
      </h2>
      {replacedByOther && (
        <div ref={rejectionRef} tabIndex={-1} className="dsl-focus-target">
          <Alert
            variant="danger"
            action={{ label: t('dsl.action.showLatest'), onClick: props.onShowLatest }}
          >
            <span data-testid="dsl-preview-replaced">{t('dsl.preview.replacedByOther')}</span>
          </Alert>
        </div>
      )}
      <Alert variant="success">
        <span data-testid="dsl-preview-valid">{t('dsl.preview.valid')}</span>
      </Alert>
      {notCompared && (
        <Alert variant="warning">
          <span data-testid="dsl-preview-not-compared">{t('dsl.preview.notCompared')}</span>
        </Alert>
      )}
      {mismatchCount > 0 && (
        <Alert variant="warning">
          <span data-testid="dsl-preview-mismatch">
            {t('dsl.preview.mismatchCount', { count: mismatchCount })}
          </span>
        </Alert>
      )}

      <section aria-labelledby="dsl-preview-summary" className="dsl-preview-section">
        <h3 id="dsl-preview-summary" className="dsl-subheading">
          {t('dsl.preview.summaryHeading')}
        </h3>
        <Summary summary={preview.summary} />
      </section>

      <section aria-labelledby="dsl-preview-diff" className="dsl-preview-section">
        <h3 id="dsl-preview-diff" className="dsl-subheading">
          {t('dsl.preview.diffHeading')}
        </h3>
        <DslDiffTable key={preview.previewId} diff={preview.diff} />
      </section>

      <section aria-labelledby="dsl-preview-warnings" className="dsl-preview-section">
        <h3 id="dsl-preview-warnings" className="dsl-subheading">
          {t('dsl.preview.warningHeading')}
        </h3>
        <DslWarningList warnings={preview.warnings} />
      </section>

      <section aria-labelledby="dsl-preview-menu" className="dsl-preview-section">
        <h3 id="dsl-preview-menu" className="dsl-subheading">
          {t('dsl.preview.menuHeading')}
        </h3>
        <DslMenuTree key={preview.previewId} nodes={preview.summary.menuTree} />
      </section>

      <div className="dsl-actions">
        <Button variant="secondary" onClick={props.onDownload} data-testid="dsl-preview-download">
          <Icon name="download" size={16} />
          {t('dsl.action.download')}
        </Button>
        <Button
          variant="danger"
          loading={props.discarding}
          disabled={props.busy}
          onClick={props.onDiscard}
          data-testid="dsl-preview-discard"
        >
          {t('dsl.action.discard')}
        </Button>
        <Button
          variant="primary"
          loading={props.applying}
          disabled={props.busy}
          onClick={() => props.onApply(preview.previewId)}
          data-testid="dsl-preview-apply"
        >
          {t('dsl.action.apply')}
        </Button>
      </div>
    </section>
  )
}

/** 要約と、表示名の未設定の場所（先頭 100 件と総数、BR5.6） */
function Summary({ summary }: { summary: PreviewSummary }) {
  const t = useDslText()
  const [open, setOpen] = useState(false)
  const shown = summary.missingDisplayNames
  const total = summary.missingDisplayNameTotal ?? shown.length

  return (
    <div className="dsl-preview-summary" data-testid="dsl-preview-summary">
      <p className="dsl-summary-line">
        <span data-testid="dsl-preview-table-count">
          {t('dsl.preview.tableCount', {
            tables: summary.tableCount,
            views: summary.viewCount,
          })}
        </span>
        <span data-testid="dsl-preview-column-count">
          {t('dsl.preview.columnCount', { count: summary.columnCount })}
        </span>
      </p>
      <p className="dsl-summary-line">
        <span data-testid="dsl-preview-missing-count">
          {t('dsl.preview.missingCount', { count: total })}
        </span>
        {shown.length > 0 && (
          <Button
            variant="ghost"
            size="sm"
            aria-expanded={open}
            aria-controls="dsl-preview-missing-list"
            onClick={() => setOpen((value) => !value)}
            data-testid="dsl-preview-missing-toggle"
          >
            {t(open ? 'dsl.preview.missingHide' : 'dsl.preview.missingShow')}
          </Button>
        )}
      </p>
      {open && shown.length > 0 && (
        <div id="dsl-preview-missing-list" data-testid="dsl-preview-missing-list">
          {total > shown.length && (
            <p className="dsl-muted" data-testid="dsl-preview-missing-truncated">
              {t('dsl.preview.missingTruncated', { shown: shown.length, total })}
            </p>
          )}
          <div className="mycui-table-wrapper">
            <table className="mycui-table" aria-label={t('dsl.preview.missingTableLabel')}>
              <thead>
                <tr>
                  <th scope="col">{t('dsl.preview.missingPath')}</th>
                  <th scope="col">{t('dsl.preview.missingLanguage')}</th>
                </tr>
              </thead>
              <tbody>
                {shown.map((item, index) => (
                  <tr key={`${item.path}-${item.language}-${index}`}>
                    <td className="dsl-break">{item.path}</td>
                    <td>{t(`dsl.language.${item.language}`)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  )
}
