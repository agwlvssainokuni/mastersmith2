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
// S8 自分の権限（Should。Intent 261004-role-menu の U7 の L10、functional-spec.md の D24〜D26・W8.1〜W8.3・4.4、
// frontend-components.md の 7節）。
// - 見出し「自分の権限」と「今の作業ロール: 〇〇」、左は共有の木 SharedTreeView（スキーマ → テーブル）、右は値の表。
// - 作業ロールが無いときは案内（D25）、対象が無いときは「対象がありません。」、失敗は「読み込めませんでした。」と［もう一度］。
// - 768px 未満は縦に積み、木で選んだら表の見出しへフォーカスを移す（10a 節）。
import { Button } from 'make-you-chic-ui'
import { useEffect, useMemo, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { SharedTreeView } from '../../shared/tree/SharedTreeView'
import type { SharedTreeLabels } from '../../shared/tree/types'
import { MyPermissionsDetail } from './MyPermissionsDetail'
import { useMyPermissions, type MyPermissionsApi } from './useMyPermissions'
import './MyPermissionsPage.css'

/** 縦に積む幅か（768px 未満） */
function isNarrow(): boolean {
  return typeof window.matchMedia === 'function' && window.matchMedia('(width < 768px)').matches
}

export interface MyPermissionsPageProps {
  /** API の関数（テストで差し替える） */
  api?: MyPermissionsApi
}

/** 自分の権限の画面 */
export function MyPermissionsPage({ api }: MyPermissionsPageProps = {}) {
  const { t } = useTranslation()
  const state = useMyPermissions(api)
  const detailHeading = useRef<HTMLHeadingElement>(null)
  const focusDetail = useRef(false)

  const labels = useMemo<SharedTreeLabels>(
    () => ({
      expand: (label) => t('mypermissions.tree.expand', { label }),
      collapse: (label) => t('mypermissions.tree.collapse', { label }),
      loading: t('mypermissions.loading'),
      loadFailed: t('mypermissions.loadError'),
      childArea: (label) => t('mypermissions.tree.childArea', { label }),
      retry: t('mypermissions.retry'),
      empty: t('mypermissions.tree.empty'),
    }),
    [t],
  )

  useEffect(() => {
    if (focusDetail.current && state.selectedId !== null) {
      focusDetail.current = false
      detailHeading.current?.focus()
    }
  }, [state.selectedId])

  let body: React.JSX.Element
  if (state.view === 'loading') {
    body = <p className="mypermissions-text">{t('mypermissions.loading')}</p>
  } else if (state.view === 'load-error') {
    body = (
      <div>
        <p className="mypermissions-text">{t('mypermissions.loadError')}</p>
        <Button variant="secondary" onClick={state.retry}>
          {t('mypermissions.retry')}
        </Button>
      </div>
    )
  } else if (state.view === 'no-role') {
    body = <p className="mypermissions-text">{t('mypermissions.noRole')}</p>
  } else if (state.view === 'empty') {
    body = <p className="mypermissions-text">{t('mypermissions.empty')}</p>
  } else {
    body = (
      <div className="mypermissions-layout" data-testid="mypermissions-layout">
        <SharedTreeView
          nodes={state.nodes}
          loadChildren={state.loadChildren}
          selectedId={state.selectedId}
          onSelect={(id) => {
            focusDetail.current = isNarrow()
            state.select(id)
          }}
          expandedIds={state.expandedIds}
          onToggle={state.toggle}
          labels={labels}
          ariaLabel={t('mypermissions.tree.label')}
        />
        <MyPermissionsDetail
          node={state.selectedNode}
          columns={state.columns}
          headingRef={detailHeading}
        />
      </div>
    )
  }

  return (
    <section
      className="mypermissions-page"
      data-testid="mypermissions-page"
      aria-labelledby="mypermissions-heading"
    >
      <h1 id="mypermissions-heading" className="mypermissions-heading">
        {t('mypermissions.heading')}
      </h1>
      {state.workRole !== null && state.view !== 'loading' ? (
        <p className="mypermissions-text">
          {t('mypermissions.currentWorkRole', { name: state.workRole.name })}
        </p>
      ) : null}
      {body}
    </section>
  )
}
