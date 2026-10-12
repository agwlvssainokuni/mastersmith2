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
// ROLE_TRANSFER_INVALID の誤りの一覧（Intent 261004-role-menu の U6 の D26、functional-spec.md の W7.3、
// frontend-components.md の 5節・8節、AC3.1.5・AC3.1.12・AC3.1.13）。DSL の誤りの一覧と同じ見た目の部品を roletransfer の
// 中に持つ。先頭に件数の警告（role="alert"、出たときにフォーカス）、表は 行・列・場所（path は点の形のまま）・種類（reason を
// 画面の言語の文言に写す、知らない値は汎用）。無い値は「—」。サーバーの文言・部品の例外の文は出さない。
// 横に動く表の領域は中に押せる要素が無いため、role="region"・tabIndex=0・読み上げの名前を付ける（計画 13節 Q7: A、
// dsl-v2 の D-20 と同じ形）。
import { Icon } from 'make-you-chic-ui'
import { useEffect, useRef } from 'react'
import type { TransferErrorItem } from './api/types'
import { reasonMessageKey } from './reasonMessage'
import { useRoleTransferText } from './useRoleTransferText'
import './RoleTransferPage.css'

export interface TransferErrorListProps {
  /** 誤り（最大 100 件。サーバーが返したまま） */
  errors: readonly TransferErrorItem[]
}

function orDash(value: number | string | null): string {
  return value === null ? '—' : String(value)
}

/** 誤りの一覧 */
export function TransferErrorList({ errors }: TransferErrorListProps) {
  const t = useRoleTransferText()
  const alertRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    alertRef.current?.focus()
  }, [errors])

  return (
    <div className="roletransfer-errors" data-testid="roletransfer-errors">
      <div
        ref={alertRef}
        role="alert"
        tabIndex={-1}
        className="mycui-alert variant-danger roletransfer-focusable"
        data-testid="roletransfer-errors-alert"
      >
        <Icon name="danger" size={18} />
        <div className="mycui-alert-body">
          <p className="roletransfer-text">
            {t('roletransfer.invalid.summary', { count: errors.length })}
          </p>
        </div>
      </div>
      {errors.length > 0 && (
        <div
          className="mycui-table-wrapper roletransfer-focusable"
          role="region"
          // oxlint-disable-next-line jsx-a11y/no-noninteractive-tabindex -- 中に押せる要素の無い横に動く表の領域をキーボードで読めるようにするため（axe の scrollable-region-focusable、計画 13節 Q7: A）。role="region" と名前つきの領域で、除外はこの1行だけ。
          tabIndex={0}
          aria-label={t('roletransfer.errors.region')}
          data-testid="roletransfer-errors-region"
        >
          <table className="mycui-table" aria-label={t('roletransfer.errors.table')}>
            <thead>
              <tr>
                <th scope="col">{t('roletransfer.errors.line')}</th>
                <th scope="col">{t('roletransfer.errors.column')}</th>
                <th scope="col">{t('roletransfer.errors.path')}</th>
                <th scope="col">{t('roletransfer.errors.reason')}</th>
              </tr>
            </thead>
            <tbody>
              {errors.map((item, index) => (
                <tr key={index} data-testid="roletransfer-errors-row">
                  <td>{orDash(item.line)}</td>
                  <td>{orDash(item.column)}</td>
                  <td className="roletransfer-wrap">{orDash(item.path)}</td>
                  <td>{t(reasonMessageKey(item.reason))}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
