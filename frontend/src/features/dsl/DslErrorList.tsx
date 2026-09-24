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
// 誤りの一覧（interaction-spec.md の DslErrorList、BR5.1〜BR5.4、NFR1.21、AC2.2.9）。
// 件数を先頭に出し、先頭 100 件だけを行・列・場所・種類・内容の表で示し、残りの件数を示す。
// 危険な形・版の非対応で1件だけのときは、理由の1件だけを示す。行・列・場所が無ければ「—」。
// 表示された時点で件数の警告（role="alert"、tabindex="-1"）へフォーカスを移す。
import { Icon } from 'make-you-chic-ui'
import { useEffect, useRef } from 'react'
import type { DslErrorItem, DslErrorKind, DslErrorReport } from './api/types'
import { useDslText } from './useDslText'
import './DslCommon.css'
import './DslErrorList.css'

/** 描く誤りの上限（NFR1.21） */
export const MAX_SHOWN_ERRORS = 100

/** 理由の1件だけを示す種類（危険な形・版の非対応） */
export const SINGLE_REASON_KINDS: ReadonlySet<DslErrorKind> = new Set([
  'SIZE_LIMIT',
  'DEPTH_LIMIT',
  'ALIAS_LIMIT',
  'FORBIDDEN_TAG',
  'DUPLICATE_KEY',
  'UNSUPPORTED_VERSION',
])

export interface DslErrorListProps {
  /** 誤りの一覧（本文の形が合わないときは null。件数の分からない誤りとして示す） */
  report: DslErrorReport | null
  'data-testid'?: string
}

function orDash(value: number | string | null): string {
  return value === null ? '—' : String(value)
}

/** 誤りの一覧 */
export function DslErrorList({
  report,
  'data-testid': testId = 'dsl-error-list',
}: DslErrorListProps) {
  const t = useDslText()
  const alertRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    alertRef.current?.focus()
  }, [report])

  const shown: DslErrorItem[] = report ? report.errors.slice(0, MAX_SHOWN_ERRORS) : []
  const single =
    report !== null &&
    report.total === 1 &&
    shown.length === 1 &&
    SINGLE_REASON_KINDS.has(shown[0].kind)
      ? shown[0]
      : null
  const rest = report ? report.total - shown.length : 0

  return (
    <div className="dsl-error-list" data-testid={testId}>
      <div
        ref={alertRef}
        role="alert"
        tabIndex={-1}
        className="mycui-alert variant-danger dsl-error-alert"
        data-testid={`${testId}-alert`}
      >
        <Icon name="danger" size={18} />
        <div className="mycui-alert-body">
          {report === null && <p className="dsl-error-text">{t('dsl.errors.unknown')}</p>}
          {single !== null && (
            <>
              <p className="dsl-error-text">{t('dsl.errors.single')}</p>
              <p className="dsl-error-text" data-testid={`${testId}-single`}>
                <span className="dsl-error-kind">{t(`dsl.errorKind.${single.kind}`)}</span>
                {'：'}
                <span>{single.message}</span>
              </p>
            </>
          )}
          {report !== null && single === null && (
            <>
              <p className="dsl-error-text" data-testid={`${testId}-count`}>
                {t('dsl.errors.count', { count: report.total })}
              </p>
              {rest > 0 && (
                <p className="dsl-error-text" data-testid={`${testId}-rest`}>
                  {t('dsl.errors.truncated', { shown: shown.length, rest })}
                </p>
              )}
            </>
          )}
        </div>
      </div>
      {report !== null && single === null && shown.length > 0 && (
        <div className="mycui-table-wrapper">
          <table className="mycui-table" aria-label={t('dsl.errors.tableLabel')}>
            <thead>
              <tr>
                <th scope="col">{t('dsl.errors.line')}</th>
                <th scope="col">{t('dsl.errors.column')}</th>
                <th scope="col">{t('dsl.errors.path')}</th>
                <th scope="col">{t('dsl.errors.kind')}</th>
                <th scope="col">{t('dsl.errors.message')}</th>
              </tr>
            </thead>
            <tbody>
              {shown.map((item, index) => (
                <tr key={index} data-testid={`${testId}-row`}>
                  <td>{orDash(item.line)}</td>
                  <td>{orDash(item.column)}</td>
                  <td className="dsl-break">{orDash(item.path)}</td>
                  <td>{t(`dsl.errorKind.${item.kind}`)}</td>
                  <td>{item.message}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
