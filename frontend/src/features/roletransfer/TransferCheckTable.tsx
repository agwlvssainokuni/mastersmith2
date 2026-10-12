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
// 確かめの結果の表（Intent 261004-role-menu の U6 の D24、functional-spec.md の W7.3、frontend-components.md の 5節・8節、
// AC3.1.2・AC3.1.3・AC3.1.6・AC3.1.9・AC3.1.14）。素の table（make-you-chic-ui の Table の見た目）で、列は ロール・
// 変わり方・足す・変わる・消える・今の DSL に無い。行ごとの開閉のボタンの名前は「営業の変わる点を開く／閉じる」
// （aria-expanded・aria-controls）。開いた行は 対象・前・後・理由 の表を 100 件ずつのページ送りで出し、今の DSL に無い対象は
// 「⚠ 今の DSL に無い」を文字で添える。開いた行の変わる点の表は中に押せる要素が無いため、role="region"・tabIndex=0・
// 読み上げの名前を付ける（計画 13節 Q7: A）。表の下に、消える件数と、ファイルに無いため残るロールを文字で出す。
// 名前は React の文字として描く。
import { Button } from 'make-you-chic-ui'
import { Fragment, useId, useState } from 'react'
import type { TransferCheck, TransferChange, TransferRolePlan, TransferValues } from './api/types'
import { changePageRange } from './changePaging'
import { useRoleTransferText, type RoleTransferText } from './useRoleTransferText'
import './RoleTransferPage.css'

export interface TransferCheckTableProps {
  /** 確かめの結果 */
  check: TransferCheck
}

/** 対象の名前（スキーマ・テーブル・カラムを点でつなぐ） */
export function targetText(change: TransferChange): string {
  const { schemaName, tableName, columnName } = change.target
  return [schemaName, tableName, columnName].filter((name) => name !== null).join('.')
}

/** 前・後の値の文字 */
export function valuesText(values: TransferValues, t: RoleTransferText): string {
  const aux = (value: boolean | null) =>
    value === null
      ? t('roletransfer.value.unset')
      : t(value ? 'roletransfer.value.allow' : 'roletransfer.value.deny')
  return t('roletransfer.value.format', {
    main: values.main ?? t('roletransfer.value.unset'),
    create: aux(values.create),
    delete: aux(values.delete),
  })
}

/** 開いた行の変わる点（100 件ずつ） */
function ChangeRows({ plan, id }: { plan: TransferRolePlan; id: string }) {
  const t = useRoleTransferText()
  const [page, setPage] = useState(1)
  const range = changePageRange(page, plan.changes.length)
  const shown = plan.changes.slice(range.start, range.end)
  if (plan.changes.length === 0) {
    return (
      <p id={id} className="roletransfer-text">
        {t('roletransfer.changes.empty')}
      </p>
    )
  }
  return (
    <div id={id} className="roletransfer-changes" data-testid={`roletransfer-changes-${plan.name}`}>
      <div
        className="mycui-table-wrapper roletransfer-focusable"
        role="region"
        // oxlint-disable-next-line jsx-a11y/no-noninteractive-tabindex -- 中に押せる要素の無い横に動く表の領域をキーボードで読めるようにするため（axe の scrollable-region-focusable、計画 13節 Q7: A）。role="region" と名前つきの領域で、除外はこの1行だけ。
        tabIndex={0}
        aria-label={t('roletransfer.changes.region', { name: plan.name })}
      >
        <table className="mycui-table">
          <thead>
            <tr>
              <th scope="col">{t('roletransfer.changes.target')}</th>
              <th scope="col">{t('roletransfer.changes.before')}</th>
              <th scope="col">{t('roletransfer.changes.after')}</th>
              <th scope="col">{t('roletransfer.changes.reason')}</th>
            </tr>
          </thead>
          <tbody>
            {shown.map((change, index) => (
              <tr key={range.start + index} data-testid="roletransfer-change-row">
                <td className="roletransfer-wrap">{targetText(change)}</td>
                <td>{valuesText(change.before, t)}</td>
                <td>{valuesText(change.after, t)}</td>
                <td>
                  <span className="roletransfer-reason">
                    <span>{t(`roletransfer.reasonKind.${change.reason}`)}</span>
                    {!change.inCurrentDsl && <span>{t('roletransfer.orphan')}</span>}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {range.pageCount > 1 && (
        <div className="roletransfer-pager">
          <Button
            variant="secondary"
            size="sm"
            disabled={range.page <= 1}
            onClick={() => setPage(range.page - 1)}
          >
            {t('roletransfer.pager.prev')}
          </Button>
          <span className="roletransfer-text" aria-live="polite">
            {t('roletransfer.changes.range', {
              from: range.start + 1,
              to: range.end,
              total: plan.changes.length,
            })}
          </span>
          <Button
            variant="secondary"
            size="sm"
            disabled={range.page >= range.pageCount}
            onClick={() => setPage(range.page + 1)}
            data-testid="roletransfer-changes-next"
          >
            {t('roletransfer.pager.next')}
          </Button>
        </div>
      )}
    </div>
  )
}

/** 確かめの結果の表 */
export function TransferCheckTable({ check }: TransferCheckTableProps) {
  const t = useRoleTransferText()
  const idPrefix = useId()
  const [open, setOpen] = useState<ReadonlySet<number>>(new Set())
  const cleared = check.roles
    .filter((role) => role.action === 'REPLACE')
    .reduce((sum, role) => sum + role.cleared, 0)

  function toggle(index: number): void {
    setOpen((current) => {
      const next = new Set(current)
      if (next.has(index)) {
        next.delete(index)
      } else {
        next.add(index)
      }
      return next
    })
  }

  return (
    <div className="roletransfer-result" data-testid="roletransfer-check-table">
      <div
        className="mycui-table-wrapper"
        role="region"
        aria-label={t('roletransfer.result.region')}
      >
        <table
          className="mycui-table roletransfer-result-table"
          aria-label={t('roletransfer.result.table')}
        >
          <thead>
            <tr>
              <th scope="col">{t('roletransfer.column.role')}</th>
              <th scope="col">{t('roletransfer.column.action')}</th>
              <th scope="col">{t('roletransfer.column.added')}</th>
              <th scope="col">{t('roletransfer.column.changed')}</th>
              <th scope="col">{t('roletransfer.column.cleared')}</th>
              <th scope="col">{t('roletransfer.column.notInDsl')}</th>
            </tr>
          </thead>
          <tbody>
            {check.roles.map((plan, index) => {
              const expanded = open.has(index)
              const detailId = `${idPrefix}-changes-${index}`
              return (
                <Fragment key={`${plan.name}-${index}`}>
                  <tr data-testid={`roletransfer-plan-${index}`}>
                    <th scope="row">
                      <span className="roletransfer-role">
                        <Button
                          variant="secondary"
                          size="sm"
                          aria-expanded={expanded}
                          aria-controls={detailId}
                          aria-label={t(
                            expanded ? 'roletransfer.toggle.close' : 'roletransfer.toggle.open',
                            {
                              name: plan.name,
                            },
                          )}
                          onClick={() => toggle(index)}
                        >
                          <span aria-hidden="true">{expanded ? '▾' : '▸'}</span>
                        </Button>
                        <span className="roletransfer-wrap">{plan.name}</span>
                      </span>
                    </th>
                    <td>{t(`roletransfer.action.${plan.action}`)}</td>
                    <td>{plan.added}</td>
                    <td>{plan.changed}</td>
                    <td>{plan.cleared}</td>
                    <td>
                      {plan.notInDsl > 0
                        ? `${t('roletransfer.orphan')} ${plan.notInDsl}`
                        : plan.notInDsl}
                    </td>
                  </tr>
                  {expanded && (
                    <tr>
                      <td colSpan={6}>
                        <ChangeRows plan={plan} id={detailId} />
                      </td>
                    </tr>
                  )}
                </Fragment>
              )
            })}
          </tbody>
        </table>
      </div>
      {cleared > 0 && (
        <p className="roletransfer-text" data-testid="roletransfer-summary-cleared">
          {t('roletransfer.summary.cleared', { count: cleared })}
        </p>
      )}
      {check.untouchedRoles.length > 0 && (
        <p
          className="roletransfer-text roletransfer-wrap"
          data-testid="roletransfer-summary-untouched"
        >
          {t('roletransfer.summary.untouched', {
            names: check.untouchedRoles.join(t('roletransfer.summary.separator')),
          })}
        </p>
      )}
    </div>
  )
}
