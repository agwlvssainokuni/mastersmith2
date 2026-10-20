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
// S1 業務データの一覧（Intent 261016-business-data-views の U3 の L7、D3・D7〜D14・F1・F2・F3・F6・F8、
// frontend-components.md の 4節、interaction-spec.md の BusinessDataListPage、AC1.1.1〜AC1.3.15、
// U3-NFR1.7・1.9・2.10・4.2・4.20・5.9・5.10、計画 4.8 の P-7・P-9）。
// - 道の引数（schema・table・item）を読み、定義（useTableDefinition）と一覧（useRowSearch）で描く。見てよいかと許す操作は
//   サーバーが判定し、画面は定義の operations に従って出すだけ。
// - 枠に tables-page、見出し（h1。テーブルの表示名）に tables-heading を引き継ぐ（P-7）。
// - 全体の件数と表示の範囲を aria-live="polite" で伝える（D12）。読み直しの間は前の表を残し aria-busy を立てる（D14）。
// - 行を開くときは、主キーを記憶に置いて意味の無い印を作り、履歴の状態に { rowToken } だけを置いて詳細へ移る（D2）。
//   移る前に押した要素（行の番号と、リンクか操作か）を一覧の状態に置き、戻ったらそこへフォーカスを戻す。
// - 開けないとき（403・404・503 など）は S6 の表示に置き換え、前の行を残さない（F8）。
// - 定義が変わったと分かったら定義を読み直し、「表示の設定が変わりました」を読み上げにも伝える（F6）。
import { Alert } from 'make-you-chic-ui'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate } from 'react-router'
import { useDisplayLanguage } from '../../app/i18n/I18nProvider'
import { toTablePath } from '../../app/navigation/tablePlaceholderPath'
import { PageSizeSelect } from '../../shared/page-size/PageSizeSelect'
import { ScrollRegion } from '../../shared/scroll-region/ScrollRegion'
import { putRowKey, saveListState } from './businessDataStore'
import {
  PAGE_SIZES,
  type ListState,
  type PageSize,
  type SearchInputs,
  type SortKey,
} from './businessDataTypes'
import { BusinessDataProblemView } from './BusinessDataProblemView'
import { BusinessDataTable } from './BusinessDataTable'
import { displayRange, isPageOverflow, previousPageFor } from './listPaging'
import { readProblemMessageKey } from './problemMessage'
import { RestrictionNotice } from './RestrictionNotice'
import { SearchPanel } from './SearchPanel'
import { countActiveConditions } from './searchConditions'
import { useTableRefFromSearch, useWorkRoleKey } from './tableHookSupport'
import { localizedName, useValueTexts } from './tablesText'
import { useRowSearch } from './useRowSearch'
import { useTableDefinition } from './useTableDefinition'
import './tablesCommon.css'
import './BusinessDataListPage.css'

/** S1 業務データの一覧 */
export function BusinessDataListPage() {
  const { t } = useTranslation()
  const language = useDisplayLanguage()
  const texts = useValueTexts()
  const { search } = useLocation()
  const navigate = useNavigate()
  const { ref, itemId } = useTableRefFromSearch(search)
  const { roleId } = useWorkRoleKey()
  const definitionState = useTableDefinition(ref)
  const { definition, reload: reloadDefinition } = definitionState
  const [definitionChanged, setDefinitionChanged] = useState(false)
  const onDefinitionStale = useCallback(() => {
    setDefinitionChanged(true)
    reloadDefinition()
  }, [reloadDefinition])
  const rows = useRowSearch(ref, definition, onDefinitionStale)
  const { listState, setListState } = rows
  const sectionRef = useRef<HTMLElement | null>(null)

  const tableName =
    definition === null ? '' : localizedName(definition.displayName, language, definition.table)
  const detailHref = ref === null ? '/' : toTablePath('row', ref, itemId)

  const change = useCallback(
    (next: Partial<ListState>) => {
      if (listState !== null) {
        setListState({ ...listState, ...next, returnFocus: undefined })
      }
    },
    [listState, setListState],
  )

  const onOpenRow = useCallback(
    (index: number, via: 'link' | 'actions') => {
      const item = rows.page?.items[index]
      if (ref === null || item === undefined || item.key === null || listState === null) {
        return
      }
      const rowToken = putRowKey(item.key, roleId)
      saveListState(ref, roleId, { ...listState, returnFocus: { index, via } })
      void navigate(toTablePath('row', ref, itemId), { state: { rowToken } })
    },
    [rows.page, ref, roleId, listState, itemId, navigate],
  )

  // 詳細から戻ったら、移る前に押した行のリンクか「操作」のトリガーへフォーカスを戻す（1回だけ）。
  const returnFocus = listState?.returnFocus
  const pageReady = rows.status === 'ready' && rows.page !== null
  useEffect(() => {
    if (!pageReady || returnFocus === undefined || ref === null || listState === null) {
      return
    }
    const root = sectionRef.current
    const target =
      returnFocus.via === 'link'
        ? root?.querySelector<HTMLElement>(`[data-testid="tables-row-link-r${returnFocus.index}"]`)
        : root?.querySelector<HTMLElement>(
            `[data-testid="tables-row-actions-r${returnFocus.index}"] button`,
          )
    target?.focus()
    saveListState(ref, roleId, { ...listState, returnFocus: undefined })
  }, [pageReady, returnFocus, ref, roleId, listState])

  let body: React.JSX.Element
  let announcement = ''
  let busy = false
  if (definitionState.status === 'problem' && definitionState.failure !== null) {
    announcement = t(readProblemMessageKey(definitionState.failure.kind))
    body = (
      <BusinessDataProblemView
        kind={definitionState.failure.kind}
        onRetry={definitionState.retry}
      />
    )
  } else if (rows.status === 'problem' && rows.failure !== null) {
    announcement = t(readProblemMessageKey(rows.failure.kind))
    body = <BusinessDataProblemView kind={rows.failure.kind} onRetry={rows.retry} />
  } else if (definition === null || listState === null) {
    busy = true
    body = (
      <h1 className="tables-heading" data-testid="tables-heading">
        {t('tables.loading')}
      </h1>
    )
  } else {
    busy = rows.busy
    const page = rows.page
    const range = page === null ? null : displayRange(page.page, listState.size, page.items.length)
    const countText =
      page === null
        ? ''
        : range === null
          ? t('tables.list.countEmpty', { total: page.total })
          : t('tables.list.count', { total: page.total, from: range.from, to: range.to })
    announcement = [definitionChanged ? t('tables.definitionChanged') : '', countText]
      .filter((text) => text !== '')
      .join(' ')
    const active = countActiveConditions(definition.searchFields, listState.search)
    let content: React.JSX.Element
    if (page === null) {
      content = <p className="tables-text">{t('tables.loading')}</p>
    } else if (page.items.length === 0) {
      if (isPageOverflow(page.items.length, page.total)) {
        content = (
          <div className="tables-empty" data-testid="tables-page-overflow">
            <p className="tables-text">{t('tables.list.pageOverflow')}</p>
            <button
              type="button"
              className="tables-text-button"
              onClick={() =>
                change({ page: previousPageFor(listState.page, page.total, listState.size) })
              }
            >
              {t('tables.list.prevPage')}
            </button>
          </div>
        )
      } else if (active > 0) {
        content = (
          <div className="tables-empty" data-testid="tables-empty-filtered">
            <p className="tables-text">{t('tables.list.empty.filtered')}</p>
            <button
              type="button"
              className="tables-text-button"
              onClick={() => change({ search: {}, page: 1 })}
            >
              {t('tables.search.clear')}
            </button>
          </div>
        )
      } else {
        content = (
          <div className="tables-empty" data-testid="tables-empty-table">
            <p className="tables-text">{t('tables.list.empty.table')}</p>
          </div>
        )
      }
    } else {
      content = (
        <ScrollRegion
          label={t('tables.list.regionLabel', { name: tableName })}
          testId="tables-scroll"
        >
          <BusinessDataTable
            definition={definition}
            page={page}
            sort={listState.sort}
            size={listState.size}
            busy={rows.busy}
            language={language}
            tableName={tableName}
            texts={texts}
            detailHref={detailHref}
            onSortChange={(sort: SortKey | null) => change({ sort, page: 1 })}
            onPageChange={(next: number) => change({ page: next })}
            onOpenRow={onOpenRow}
          />
        </ScrollRegion>
      )
    }
    body = (
      <>
        <h1 className="tables-heading" data-testid="tables-heading">
          {tableName}
        </h1>
        <RestrictionNotice
          reason={definition.restrictionReason}
          writeConfigured={definition.writeConfigured}
        />
        {rows.notice !== null && (
          <Alert variant="warning">{t(readProblemMessageKey(rows.notice.kind))}</Alert>
        )}
        {definition.searchFields.length > 0 && (
          <SearchPanel
            key={JSON.stringify(listState.search)}
            fields={definition.searchFields}
            columns={[...definition.listColumns, ...definition.detailColumns]}
            values={listState.search}
            serverErrors={rows.fieldErrors}
            expanded={listState.searchExpanded}
            language={language}
            texts={texts}
            onSearch={(values: SearchInputs) => change({ search: values, page: 1 })}
            onClear={() => change({ search: {}, page: 1 })}
            onToggle={() => change({ searchExpanded: !listState.searchExpanded })}
          />
        )}
        <div className="tables-list-bar">
          <p className="tables-count" data-testid="tables-count">
            {countText}
          </p>
          <PageSizeSelect<PageSize>
            value={listState.size}
            options={PAGE_SIZES}
            label={t('tables.list.pageSize')}
            disabled={rows.busy}
            onChange={(size) => change({ size, page: 1 })}
          />
        </div>
        {content}
      </>
    )
  }

  return (
    <section ref={sectionRef} className="tables-page" data-testid="tables-page" aria-busy={busy}>
      {body}
      <p className="tables-visually-hidden" aria-live="polite" data-testid="tables-live">
        {busy ? '' : announcement}
      </p>
    </section>
  )
}
