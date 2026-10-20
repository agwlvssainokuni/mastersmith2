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
// S2 行の詳細（Intent 261016-business-data-views の U3 の L7、D2・D9・D15・D16・D18・F3・F6・F8、
// frontend-components.md の 4節、interaction-spec.md の BusinessDataDetailPage、AC1.4.1〜AC1.4.7・AC1.5.1〜AC1.5.8、
// U3-NFR2.11・4.2・4.20・5.9）。
// - 主キーは履歴の状態の印（rowToken）で記憶から引く。印が記憶に無ければ（再読み込みの後・作業ロールを切り替えた後）、
//   同じテーブルの一覧へ置き換えて移る（D2、計画 D2: A）。
// - 定義の詳細のカラムを説明の一覧（dl）で出す。値は書式の後の文字で、詳細では折り返して全体を出す
//   （white-space: pre-wrap; overflow-wrap: anywhere）。切った値には「長いため先頭だけを表示しています」を添える。
// - 見出し（h1）は「<テーブルの表示名>: <行のラベル>」で、開いたら見出しへフォーカスを移す（D18）。
// - document.title に行のラベルを入れない（ブラウザの履歴のデータに残るため。NFR4.2）。
// - 更新・削除の入口（B6）: 許す操作に UPDATE があれば「更新」（同じ印で更新の画面へ）、DELETE があれば「削除」（S5 の確かめ）。
//   削除できたら一覧へ置き換えて戻り（一覧の状態は保つ）、Toast で知らせる。書き込みの 403 は S6 の 403 の表示（D3）。
// - 開けないときは S6 の表示にする。
import { Button, useToast } from 'make-you-chic-ui'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useNavigate } from 'react-router'
import { useDisplayLanguage } from '../../app/i18n/I18nProvider'
import { toTablePath } from '../../app/navigation/tablePlaceholderPath'
import { NullValue } from '../../shared/null-value/NullValue'
import { BusinessDataProblemView } from './BusinessDataProblemView'
import { DeleteConfirmModal } from './DeleteConfirmModal'
import { readProblemMessageKey } from './problemMessage'
import { rowLabelParts, rowLabelText } from './rowLabel'
import { useForbiddenFollowUp, useTableRefFromSearch } from './tableHookSupport'
import { localizedName, useValueTexts } from './tablesText'
import { useRowDeleteFlow } from './useRowDeleteFlow'
import { useRowDetail } from './useRowDetail'
import { useTableDefinition } from './useTableDefinition'
import { formatCell } from './valueFormat'
import './tablesCommon.css'
import './BusinessDataDetailPage.css'

/** 履歴の状態から印を読む（形が違えば undefined） */
function readRowToken(state: unknown): unknown {
  return typeof state === 'object' && state !== null && 'rowToken' in state
    ? (state as { rowToken: unknown }).rowToken
    : undefined
}

/** S2 行の詳細 */
export function BusinessDataDetailPage() {
  const { t } = useTranslation()
  const language = useDisplayLanguage()
  const texts = useValueTexts()
  const location = useLocation()
  const navigate = useNavigate()
  const { ref, itemId } = useTableRefFromSearch(location.search)
  const token = readRowToken(location.state)
  const definitionState = useTableDefinition(ref)
  const { definition, reload: reloadDefinition } = definitionState
  const onDefinitionStale = useCallback(() => reloadDefinition(), [reloadDefinition])
  const detail = useRowDetail(ref, token, definition, onDefinitionStale)
  const headingRef = useRef<HTMLHeadingElement | null>(null)
  const listPath = ref === null ? '/' : toTablePath('list', ref, itemId)
  const toast = useToast()
  const followUp = useForbiddenFollowUp()
  const [writeForbidden, setWriteForbidden] = useState(false)
  const deletion = useRowDeleteFlow({
    ref: ref ?? { schemaName: '', tableName: '' },
    definition,
    onDeleted: () => {
      void navigate(listPath, { replace: true })
      toast.show({ message: t('tables.toast.deleted'), variant: 'success' })
    },
    onForbidden: () => {
      setWriteForbidden(true)
      followUp()
    },
    onDefinitionStale,
  })

  const missing = detail.status === 'missing'
  useEffect(() => {
    if (missing) {
      void navigate(listPath, { replace: true })
    }
  }, [missing, listPath, navigate])

  const row = detail.status === 'ready' ? detail.row : null
  useEffect(() => {
    if (row !== null) {
      headingRef.current?.focus()
    }
  }, [row])

  let body: React.JSX.Element
  let announcement = ''
  let busy = false
  if (writeForbidden) {
    announcement = t('tables.problem.writeForbiddenDelete')
    body = (
      <BusinessDataProblemView
        kind="forbidden"
        messageKey="tables.problem.writeForbiddenDelete"
        listPath={listPath}
      />
    )
  } else if (definitionState.status === 'problem' && definitionState.failure !== null) {
    announcement = t(readProblemMessageKey(definitionState.failure.kind))
    body = (
      <BusinessDataProblemView
        kind={definitionState.failure.kind}
        onRetry={definitionState.retry}
        listPath={listPath}
      />
    )
  } else if (detail.status === 'problem' && detail.failure !== null) {
    announcement = t(readProblemMessageKey(detail.failure.kind))
    body = (
      <BusinessDataProblemView
        kind={detail.failure.kind}
        onRetry={detail.retry}
        listPath={listPath}
      />
    )
  } else if (definition === null || row === null) {
    busy = true
    body = <p className="tables-text">{t('tables.loading')}</p>
  } else {
    const tableName = localizedName(definition.displayName, language, definition.table)
    const label = rowLabelText(rowLabelParts(definition, row.values, texts), texts)
    body = (
      <>
        <Link className="tables-link" to={listPath} data-testid="tables-back-to-list">
          {t('tables.backToList')}
        </Link>
        <h1
          ref={headingRef}
          className="tables-heading"
          data-testid="tables-detail-heading"
          tabIndex={-1}
        >
          {t('tables.detail.heading', { table: tableName, label })}
        </h1>
        {(definition.operations.includes('UPDATE') || definition.operations.includes('DELETE')) && (
          <div className="tables-actions" data-testid="tables-detail-actions">
            {definition.operations.includes('UPDATE') && ref !== null && (
              <Link
                className="tables-link"
                to={toTablePath('edit', ref, itemId)}
                state={{ rowToken: token }}
                data-testid="tables-detail-update"
              >
                {t('tables.actions.update')}
              </Link>
            )}
            {definition.operations.includes('DELETE') && (
              <Button
                variant="danger"
                size="sm"
                onClick={() => deletion.open({ key: row.key, label })}
                data-testid="tables-detail-delete"
              >
                {t('tables.actions.delete')}
              </Button>
            )}
          </div>
        )}
        <dl className="tables-detail" data-testid="tables-detail">
          {definition.detailColumns.map((column, index) => {
            const value = Object.hasOwn(row.values, column.column)
              ? row.values[column.column]
              : undefined
            const formatted = formatCell(column, value, texts)
            const truncated = row.truncatedColumns.includes(column.column)
            return (
              <div
                key={column.column}
                className="tables-detail-item"
                data-testid={`tables-detail-c${index}`}
              >
                <dt>{localizedName(column.displayName, language, column.column)}</dt>
                <dd>
                  {formatted.kind === 'null' ? (
                    <NullValue text={texts.null} spokenText={t('tables.value.nullSpoken')} />
                  ) : formatted.kind === 'masked' ? (
                    <>
                      <span aria-hidden="true">{formatted.text}</span>
                      <span className="tables-visually-hidden">
                        {t('tables.value.maskedSpoken')}
                      </span>
                    </>
                  ) : (
                    <span className="tables-detail-value">{formatted.text}</span>
                  )}
                  {truncated && (
                    <span className="tables-detail-truncated">{t('tables.value.truncated')}</span>
                  )}
                </dd>
              </div>
            )
          })}
        </dl>
      </>
    )
  }

  return (
    <section className="tables-page" data-testid="tables-page" aria-busy={busy}>
      {body}
      <p className="tables-visually-hidden" aria-live="polite" data-testid="tables-live">
        {busy ? '' : announcement}
      </p>
      {deletion.target !== null && definition !== null && (
        <DeleteConfirmModal
          title={t('tables.delete.title', {
            table: localizedName(definition.displayName, language, definition.table),
            label: deletion.target.label,
          })}
          phase={deletion.phase}
          busy={deletion.busy}
          slow={deletion.slow}
          message={deletion.message}
          unknown={deletion.unknown}
          onCheck={() => void navigate(listPath)}
          onCancel={deletion.cancel}
          onConfirm={deletion.confirm}
        />
      )}
    </section>
  )
}
