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
// 今の状態（interaction-spec.md の DslStatusPanel、BR3.1・BR5.10・BR6.3、AC3.1.6・AC3.1.7）。
// 適用中とプレビューの識別・出どころ・人・日時を示し、スキーマの読み込みのボタンを持つ。どのタブでも画面の上部に示す。
import { Alert, Button, Card } from 'make-you-chic-ui'
import { useDisplayLanguage } from '../../app/i18n/I18nProvider'
import type { DslStatus, DslUser } from './api/types'
import { DslHash } from './DslHash'
import { formatDateTime } from './format'
import type { LoadState } from './loadState'
import { useDslText, type DslText } from './useDslText'
import './DslCommon.css'
import './DslStatusPanel.css'

export interface DslStatusPanelProps {
  /** 今の状態（読めていなければ null） */
  status: DslStatus | null
  loadState: LoadState
  /** 何かの操作を処理中（読み込みのボタンを使えなくする） */
  busy: boolean
  /** スキーマの読み込みを処理中（ボタンを処理中の表示にする） */
  generating: boolean
  /** スキーマの読み込み（置き換えの確認は画面が出す） */
  onReadSchema: () => void
  /** もう一度読み込む */
  onRetry: () => void
  /** 日時の時差（省略すると端末の時差） */
  timeZone?: string
}

function userName(user: DslUser, t: DslText): string {
  return user.email ?? t('dsl.user.unknown')
}

/** 今の状態 */
export function DslStatusPanel({
  status,
  loadState,
  busy,
  generating,
  onReadSchema,
  onRetry,
  timeZone,
}: DslStatusPanelProps) {
  const t = useDslText()
  const language = useDisplayLanguage()
  const date = (iso: string) => formatDateTime(iso, language, timeZone)

  return (
    <Card className="dsl-status" data-testid="dsl-status-panel">
      <section aria-labelledby="dsl-status-heading" className="dsl-status-section">
        <h2 id="dsl-status-heading" className="dsl-section-heading">
          {t('dsl.status.heading')}
        </h2>
        {loadState === 'loading' && (
          <p role="status" className="dsl-muted" data-testid="dsl-status-loading">
            {t('dsl.status.loading')}
          </p>
        )}
        {loadState === 'failed' && (
          <Alert variant="danger" action={{ label: t('dsl.action.retry'), onClick: onRetry }}>
            <span data-testid="dsl-status-error">{t('dsl.status.loadFailed')}</span>
          </Alert>
        )}
        {loadState === 'loaded' && status && (
          <dl className="dsl-status-list">
            <div className="dsl-status-row" data-testid="dsl-status-applied">
              <dt>{t('dsl.status.applied')}</dt>
              <dd>
                {status.applied ? (
                  <span className="dsl-status-values">
                    <span>
                      {t('dsl.status.hash')} <DslHash hash={status.applied.dslHash} />
                    </span>
                    <span>
                      {t('dsl.status.appliedBy')} {userName(status.applied.by, t)}
                    </span>
                    <span>{date(status.applied.at)}</span>
                  </span>
                ) : (
                  t('dsl.status.noApplied')
                )}
              </dd>
            </div>
            <div className="dsl-status-row" data-testid="dsl-status-preview">
              <dt>{t('dsl.status.preview')}</dt>
              <dd>
                {status.preview ? (
                  <span className="dsl-status-values">
                    <span>
                      {t('dsl.status.hash')} <DslHash hash={status.preview.dslHash} />
                    </span>
                    <span>
                      {t('dsl.status.source')} {t(`dsl.source.${status.preview.source}`)}
                    </span>
                    <span>
                      {t('dsl.status.placedBy')} {userName(status.preview.by, t)}
                    </span>
                    <span>{date(status.preview.at)}</span>
                  </span>
                ) : (
                  t('dsl.status.noPreview')
                )}
              </dd>
            </div>
          </dl>
        )}
        <div className="dsl-actions">
          <Button
            variant="primary"
            loading={generating}
            disabled={busy}
            onClick={onReadSchema}
            data-testid="dsl-read-schema-button"
          >
            {t('dsl.action.readSchema')}
          </Button>
        </div>
      </section>
    </Card>
  )
}
