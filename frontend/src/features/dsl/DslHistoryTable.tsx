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
// 適用の履歴（interaction-spec.md の DslHistoryTable、AC5.1.1・AC5.1.2・AC5.2.1）。新しい順（サーバーの順のまま）に、
// 適用した日時・人・識別・状態・操作を示す。今適用中の版は「適用中」の文字（Badge）で示し、その行は適用中の DSL の
// ダウンロード、ほかの行は「プレビューに戻す」を持つ。戻しのボタンの名前には、どの版かを含める。
import { Alert, Badge, Button } from 'make-you-chic-ui'
import { useDisplayLanguage } from '../../app/i18n/I18nProvider'
import type { HistoryEntry } from './api/types'
import { DslHash } from './DslHash'
import { formatDateTime } from './format'
import type { LoadState } from './loadState'
import { useDslText } from './useDslText'
import './DslCommon.css'

export interface DslHistoryTableProps {
  entries: HistoryEntry[]
  loadState: LoadState
  /** 何かの操作を処理中（戻しのボタンを使えなくする） */
  busy: boolean
  /** 戻しを処理中の版（そのボタンを処理中の表示にする） */
  restoringId: string | null
  onRestore: (entry: HistoryEntry) => void
  onDownloadApplied: () => void
  onRetry: () => void
  /** 日時の時差（省略すると端末の時差） */
  timeZone?: string
}

/** 適用の履歴 */
export function DslHistoryTable({
  entries,
  loadState,
  busy,
  restoringId,
  onRestore,
  onDownloadApplied,
  onRetry,
  timeZone,
}: DslHistoryTableProps) {
  const t = useDslText()
  const language = useDisplayLanguage()

  if (loadState === 'loading') {
    return (
      <p role="status" className="dsl-muted" data-testid="dsl-history-loading">
        {t('dsl.history.loading')}
      </p>
    )
  }
  if (loadState === 'failed') {
    return (
      <Alert variant="danger" action={{ label: t('dsl.action.retry'), onClick: onRetry }}>
        <span data-testid="dsl-history-error">{t('dsl.history.loadFailed')}</span>
      </Alert>
    )
  }
  if (entries.length === 0) {
    return (
      <p className="dsl-muted" data-testid="dsl-history-empty">
        {t('dsl.history.empty')}
      </p>
    )
  }
  return (
    <div className="mycui-table-wrapper" data-testid="dsl-history-table">
      <table className="mycui-table">
        <caption className="dsl-visually-hidden">{t('dsl.history.caption')}</caption>
        <thead>
          <tr>
            <th scope="col">{t('dsl.history.at')}</th>
            <th scope="col">{t('dsl.history.by')}</th>
            <th scope="col">{t('dsl.history.hash')}</th>
            <th scope="col">{t('dsl.history.state')}</th>
            <th scope="col">{t('dsl.history.actions')}</th>
          </tr>
        </thead>
        <tbody>
          {entries.map((entry) => {
            const at = formatDateTime(entry.at, language, timeZone)
            return (
              <tr key={entry.revisionId} data-testid={`dsl-history-row-${entry.revisionId}`}>
                <td>{at}</td>
                <td>{entry.by.email ?? t('dsl.user.unknown')}</td>
                <td>
                  <DslHash hash={entry.dslHash} />
                </td>
                <td>
                  {entry.current && <Badge variant="success">{t('dsl.history.current')}</Badge>}
                </td>
                <td>
                  {entry.current ? (
                    <Button
                      variant="secondary"
                      size="sm"
                      aria-label={t('dsl.history.downloadLabel')}
                      onClick={onDownloadApplied}
                      data-testid="dsl-history-download"
                    >
                      {t('dsl.action.download')}
                    </Button>
                  ) : (
                    <Button
                      variant="secondary"
                      size="sm"
                      aria-label={t('dsl.history.restoreLabel', { at })}
                      loading={restoringId === entry.revisionId}
                      disabled={busy}
                      onClick={() => onRestore(entry)}
                      data-testid={`dsl-history-restore-${entry.revisionId}`}
                    >
                      {t('dsl.action.restore')}
                    </Button>
                  )}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
