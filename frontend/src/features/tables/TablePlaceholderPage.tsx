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
// S2 テーブルの置き場（Intent 261004-role-menu の U7 の L9、functional-spec.md の D20〜D23・W7.1〜W7.3・4.3、
// frontend-components.md の 6節、NFR1.1・NFR1.6・NFR1.7）。
// - 道の引数（schema・table）を読み、置き場の問い合わせの答えで描く。見てよいかはサーバーが判定する。
// - available: 見出し（h1）にテーブルの表示名（D13）と準備中の文。forbidden（403・400・引数なし）: どれも同じ表示で、
//   表示名とテーブルの名前（道の引数を含む）を出さない。load-error: 「読み込めませんでした。」と［もう一度］。
// - 権限なしの表示に、ロールが2つ以上なら［作業ロールを切り替える］（トップバーのトリガーへフォーカスを移す）、いつも
//   ［ホームへ］。状態の変化は aria-live="polite" で伝え、フォーカスは動かさない（D23）。
import { Button } from 'make-you-chic-ui'
import { Link, useLocation } from 'react-router'
import { useTranslation } from 'react-i18next'
import { resolveNavLabel } from '../../app/navigation/buildNavSections'
import { readTablePlaceholderParams } from '../../app/navigation/tablePlaceholderPath'
import { useDisplayLanguage } from '../../app/i18n/I18nProvider'
import { useWorkRole } from '../../app/work-role/WorkRoleProvider'
import { useTableAccess } from './useTableAccess'
import './TablePlaceholderPage.css'

/** テーブルの置き場の画面 */
export function TablePlaceholderPage() {
  const { t } = useTranslation()
  const language = useDisplayLanguage()
  const { search } = useLocation()
  const { ref } = readTablePlaceholderParams(search)
  const state = useTableAccess(ref)
  const workRole = useWorkRole()

  let body: React.JSX.Element
  let announcement = ''
  if (state.view === 'available' && state.access !== null) {
    const { access } = state
    const name = resolveNavLabel(
      access.displayName,
      language,
      { schemaName: access.schemaName, tableName: access.tableName },
      access.tableName,
    )
    announcement = t('tables.preparing')
    body = (
      <>
        <h1 className="tables-heading" data-testid="tables-heading">
          {name}
        </h1>
        <p className="tables-text">{t('tables.preparing')}</p>
      </>
    )
  } else if (state.view === 'forbidden') {
    announcement = t('tables.forbidden')
    const roleName = workRole.current?.name ?? t('tables.noWorkRole')
    body = (
      <>
        <h1 className="tables-heading" data-testid="tables-heading">
          {t('tables.forbidden')}
        </h1>
        <p className="tables-text">{t('tables.currentWorkRole', { name: roleName })}</p>
        <div className="tables-actions">
          {workRole.roles.length >= 2 ? (
            <Button variant="secondary" onClick={() => workRole.focusWorkRoleSwitcher()}>
              {t('tables.switchWorkRole')}
            </Button>
          ) : null}
          <Link className="tables-link" to="/">
            {t('tables.goHome')}
          </Link>
        </div>
      </>
    )
  } else if (state.view === 'load-error') {
    announcement = t('tables.loadError')
    body = (
      <div className="tables-actions">
        <p className="tables-text">{t('tables.loadError')}</p>
        <Button variant="secondary" onClick={state.retry}>
          {t('tables.retry')}
        </Button>
      </div>
    )
  } else {
    body = <p className="tables-text">{t('tables.loading')}</p>
  }

  return (
    <section className="tables-page" data-testid="tables-page" aria-busy={state.pending}>
      {body}
      <p className="tables-visually-hidden" aria-live="polite" data-testid="tables-live">
        {state.pending ? '' : announcement}
      </p>
    </section>
  )
}
