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
// 照合の警告の一覧（mockups.md の 2・3.4、BR5.3・BR5.8・BR7.3、AC3.2.1〜AC3.2.4）。
// 種類ごとの見出しと、場所・内容を示す。照合できなかった警告を先頭に置く。色に加えて記号と文言で示す。
// 内容（message）はサーバーが表示言語で返した文言をそのまま文字として示す。
import { Icon } from 'make-you-chic-ui'
import type { DslWarning, WarningKind } from './api/types'
import { useDslText } from './useDslText'
import './DslCommon.css'
import './DslWarningList.css'

export interface DslWarningListProps {
  warnings: DslWarning[]
}

/** 見出しの順（照合できなかったことを先頭に） */
export const WARNING_KIND_ORDER: readonly WarningKind[] = [
  'TARGET_UNCONFIGURED',
  'TARGET_UNAVAILABLE',
  'TABLE_MISSING',
  'COLUMN_MISSING',
  'TYPE_MISMATCH',
]

/** 照合できなかったことを表す種類 */
export const NOT_COMPARED_KINDS: ReadonlySet<WarningKind> = new Set([
  'TARGET_UNCONFIGURED',
  'TARGET_UNAVAILABLE',
])

/** 照合の警告の一覧 */
export function DslWarningList({ warnings }: DslWarningListProps) {
  const t = useDslText()
  if (warnings.length === 0) {
    return (
      <p className="dsl-muted" data-testid="dsl-warning-none">
        {t('dsl.warning.none')}
      </p>
    )
  }
  const groups = WARNING_KIND_ORDER.map((kind) => ({
    kind,
    items: warnings.filter((warning) => warning.kind === kind),
  })).filter((group) => group.items.length > 0)

  return (
    <div className="dsl-warnings" data-testid="dsl-warning-list">
      {groups.map((group) => (
        <section
          key={group.kind}
          aria-labelledby={`dsl-warning-kind-${group.kind}`}
          data-testid={`dsl-warning-group-${group.kind}`}
        >
          <h4 id={`dsl-warning-kind-${group.kind}`} className="dsl-warning-heading">
            <Icon name="warning" size={16} />
            <span>{t(`dsl.warningKind.${group.kind}`)}</span>
          </h4>
          <ul className="dsl-warning-items">
            {group.items.map((warning, index) => (
              <li key={`${warning.path ?? ''}-${index}`}>
                <span aria-hidden="true">⚠ </span>
                {warning.path !== null && <span className="dsl-warning-path">{warning.path}</span>}
                {warning.path !== null && ' … '}
                <span>{warning.message}</span>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  )
}
