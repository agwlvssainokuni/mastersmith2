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
// S4 の右の表（Intent 261004-role-menu の U6 の D11・D13・D14、functional-spec.md の W4.2〜W4.6、frontend-components.md
// の 2.2・8節）。選んだ節の設定（スキーマなら主権限・CREATE・DELETE、テーブルはそれに業務のメニューの文字）と、
// テーブルのときはカラムの表（主権限だけ）を出す。Select の読み上げの名前に対象を入れ、実効の値は文字で区別する
// （明示・継承の元・既定。補助権限は「可」「不可」と明示のときだけ「（明示）」）。写しで変えた行は「（未保存）」を添え、
// 実効の値は保存まで変えない。今の DSL に無い節・行（orphan）は値の入力を出さず、案内と［設定を消す］だけを出す。
// 名前は React の文字として描く（HTML として解釈しない）。表は外枠の中で横に動き、画面全体を横にはみ出さない。
import { Alert, Badge, Button, Select, type SelectOption } from 'make-you-chic-ui'
import type { RefObject } from 'react'
import { MAIN_VALUES, type Explicit, type PermissionNode } from './api/types'
import { draftExplicit, targetKey, type PermissionDraft } from './permissionDraft'
import { effectiveLabel, formatEffectiveLabel, type InheritedNames } from './permissionLabels'
import type { ColumnsState, ExplicitField } from './useRolePermissions'
import { useRoleAdminText, type RoleAdminText } from './useRoleAdminText'
import './PermissionPanel.css'

export interface PermissionTableProps {
  /** 選んでいる節（スキーマかテーブル） */
  node: PermissionNode
  /** カラムの読み込み（テーブルのときだけ使う） */
  columns: ColumnsState
  /** 未保存の写し */
  draft: PermissionDraft
  /** 保存・消すの間（入力を押せなくする） */
  busy: boolean
  /** 継承の元の表示名 */
  names: InheritedNames
  /** 値を変えた */
  onChange: (node: PermissionNode, field: ExplicitField, value: Explicit[ExplicitField]) => void
  /** 今の DSL に無い設定を消す */
  onClearOrphan: (node: PermissionNode) => void
  /** カラムの読み込みをやり直す */
  onRetryColumns: () => void
  /** 表の見出し（狭い画面で木から選んだときのフォーカスの行き先） */
  headingRef?: RefObject<HTMLHeadingElement | null>
}

const UNSET = ''

function mainOptions(t: RoleAdminText): SelectOption[] {
  return [
    { value: UNSET, label: t('roleadmin.option.unset') },
    ...MAIN_VALUES.map((value) => ({ value, label: value })),
  ]
}

function auxOptions(t: RoleAdminText): SelectOption[] {
  return [
    { value: UNSET, label: t('roleadmin.option.unset') },
    { value: 'true', label: t('roleadmin.option.allow') },
    { value: 'false', label: t('roleadmin.option.deny') },
  ]
}

function mainValueOf(value: string): Explicit['main'] {
  return (MAIN_VALUES as readonly string[]).includes(value) ? (value as Explicit['main']) : null
}

function auxValueOf(value: string): boolean | null {
  return value === 'true' ? true : value === 'false' ? false : null
}

function auxText(value: boolean | null): string {
  return value === null ? UNSET : String(value)
}

/** 対象の名前（表示名と、違うときは物理名） */
function physicalName(node: PermissionNode): string {
  return node.columnName ?? node.tableName ?? node.schemaName
}

/** S4 の右の表 */
export function PermissionTable({
  node,
  columns,
  draft,
  busy,
  names,
  onChange,
  onClearOrphan,
  onRetryColumns,
  headingRef,
}: PermissionTableProps) {
  const t = useRoleAdminText()
  const isTable = node.tableName !== null
  const columnNames: InheritedNames = isTable ? { ...names, table: node.displayName } : names

  function nameCell(target: PermissionNode) {
    const changed = draft.has(targetKey(target))
    const physical = physicalName(target)
    return (
      <span className="roleadmin-target">
        <span className="roleadmin-wrap">{target.displayName}</span>
        {physical !== target.displayName && (
          <span className="roleadmin-wrap roleadmin-physical">{physical}</span>
        )}
        {changed && <span className="roleadmin-unsaved">{t('roleadmin.unsavedMark')}</span>}
        {!target.inCurrentDsl && <Badge variant="danger">{t('roleadmin.orphan')}</Badge>}
      </span>
    )
  }

  function effectiveCell(target: PermissionNode, withAux: boolean, rowNames: InheritedNames) {
    const main = formatEffectiveLabel(effectiveLabel(target, 'main', rowNames), t)
    return (
      <span
        className="roleadmin-effective"
        data-testid={`roleadmin-effective-${targetKey(target)}`}
      >
        <span>{t('roleadmin.effective.mainLabel', { text: main })}</span>
        {withAux && (
          <>
            <span>
              {t('roleadmin.effective.createLabel', {
                text: formatEffectiveLabel(effectiveLabel(target, 'create', rowNames), t),
              })}
            </span>
            <span>
              {t('roleadmin.effective.deleteLabel', {
                text: formatEffectiveLabel(effectiveLabel(target, 'delete', rowNames), t),
              })}
            </span>
          </>
        )}
      </span>
    )
  }

  function orphanCell(target: PermissionNode) {
    return (
      <span className="roleadmin-orphan">
        <span>{t('roleadmin.orphan.notice')}</span>
        <Button
          variant="secondary"
          size="sm"
          disabled={busy}
          onClick={() => onClearOrphan(target)}
          data-testid={`roleadmin-clear-${targetKey(target)}`}
        >
          {t('roleadmin.orphan.clear')}
        </Button>
      </span>
    )
  }

  function selectCell(target: PermissionNode, field: ExplicitField) {
    const value = draftExplicit(draft, target, target.explicit)
    const label = t(`roleadmin.select.${field}`, { target: target.displayName })
    if (field === 'main') {
      return (
        <Select
          aria-label={label}
          options={mainOptions(t)}
          value={value.main ?? UNSET}
          disabled={busy}
          onChange={(next) => onChange(target, 'main', mainValueOf(next))}
        />
      )
    }
    return (
      <Select
        aria-label={label}
        options={auxOptions(t)}
        value={auxText(value[field])}
        disabled={busy}
        onChange={(next) => onChange(target, field, auxValueOf(next))}
      />
    )
  }

  return (
    <div className="roleadmin-perm-table" data-testid="roleadmin-perm-table">
      <h2 ref={headingRef} tabIndex={-1} className="roleadmin-perm-heading">
        {t(isTable ? 'roleadmin.perm.tableSettings' : 'roleadmin.perm.schemaSettings', {
          name: node.displayName,
        })}
      </h2>
      <div className="roleadmin-scroll">
        <table className="roleadmin-grid" data-testid="roleadmin-node-table">
          <thead>
            <tr>
              <th scope="col">{t('roleadmin.perm.header.target')}</th>
              <th scope="col">{t('roleadmin.perm.header.main')}</th>
              <th scope="col">{t('roleadmin.perm.header.create')}</th>
              <th scope="col">{t('roleadmin.perm.header.delete')}</th>
              <th scope="col">{t('roleadmin.perm.header.effective')}</th>
            </tr>
          </thead>
          <tbody>
            <tr data-testid="roleadmin-node-row">
              <th scope="row">
                {nameCell(node)}
                {isTable && (
                  <span className="roleadmin-menu-state">
                    {t(node.inMenu ? 'roleadmin.menu.in' : 'roleadmin.menu.out')}
                  </span>
                )}
              </th>
              {node.inCurrentDsl ? (
                <>
                  <td>{selectCell(node, 'main')}</td>
                  <td>{selectCell(node, 'create')}</td>
                  <td>{selectCell(node, 'delete')}</td>
                </>
              ) : (
                <td colSpan={3}>{orphanCell(node)}</td>
              )}
              <td>{effectiveCell(node, true, names)}</td>
            </tr>
          </tbody>
        </table>
      </div>
      {isTable && (
        <section className="roleadmin-columns" aria-label={t('roleadmin.perm.columns')}>
          <h3 className="roleadmin-perm-subheading">{t('roleadmin.perm.columns')}</h3>
          {columns.phase === 'loading' && (
            <p role="status" className="roleadmin-status">
              {t('roleadmin.perm.columnsLoading')}
            </p>
          )}
          {columns.phase === 'failed' && (
            <div className="roleadmin-load-error" data-testid="roleadmin-columns-error">
              <Alert variant="danger">{t('roleadmin.perm.columnsLoadFailed')}</Alert>
              <div>
                <Button variant="secondary" onClick={onRetryColumns}>
                  {t('roleadmin.action.retry')}
                </Button>
              </div>
            </div>
          )}
          {columns.phase === 'loaded' && columns.items.length === 0 && (
            <p className="roleadmin-status">{t('roleadmin.perm.columnsEmpty')}</p>
          )}
          {columns.items.length > 0 && columns.phase !== 'failed' && (
            <div className="roleadmin-scroll">
              <table className="roleadmin-grid" data-testid="roleadmin-column-table">
                <thead>
                  <tr>
                    <th scope="col">{t('roleadmin.perm.header.target')}</th>
                    <th scope="col">{t('roleadmin.perm.header.main')}</th>
                    <th scope="col">{t('roleadmin.perm.header.effective')}</th>
                  </tr>
                </thead>
                <tbody>
                  {columns.items.map((column) => (
                    <tr
                      key={targetKey(column)}
                      data-testid={`roleadmin-column-${column.columnName}`}
                    >
                      <th scope="row">{nameCell(column)}</th>
                      <td>
                        {column.inCurrentDsl ? selectCell(column, 'main') : orphanCell(column)}
                      </td>
                      <td>{effectiveCell(column, false, columnNames)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}
    </div>
  )
}
