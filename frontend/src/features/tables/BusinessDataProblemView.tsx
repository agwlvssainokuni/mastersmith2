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
// S6 開けない・つながらないときの表示（Intent 261016-business-data-views の U3 の L7、D6・F8、機能設計の 6.1、
// frontend-components.md の 4節、U3-NFR5.9・6.3）。
// - 見出し（h1）に文を出し、次の行き先を出す。行・入力の部品・前に出ていた行は出さない（F8 の 1）。
// - 403: 今の作業ロールと、作業ロールが2つ以上なら［作業ロールを切り替える］（トップバーのトリガーへフォーカスを移す）、
//   いつも［ホームへ］とメニューから選び直す案内（今の置き場の画面と同じ形。P-7）。
// - テーブルが無い: メニューから選び直す案内と［ホームへ］。DSL が無い: ［ホームへ］。行が無い・食い違い・1件に決められない:
//   ［一覧へ戻る］（一覧に居るときは［ホームへ］）。つながらない・知らない失敗・通信の失敗: ［もう一度］。
// - 文は code だけで選び、DB の文・テーブル名・値を出さない。状態は aria-live="polite" で伝え、フォーカスは動かさない。
import { Button } from 'make-you-chic-ui'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { useWorkRole } from '../../app/work-role/WorkRoleProvider'
import { readProblemMessageKey, type ReadProblemKind } from './problemMessage'
import './tablesCommon.css'
import './BusinessDataProblemView.css'

export interface BusinessDataProblemViewProps {
  kind: ReadProblemKind
  /** もう一度読む（やり直せる失敗のときだけ出す） */
  onRetry?: () => void
  /** 一覧の道（詳細から一覧へ戻る行き先。一覧の画面では渡さない） */
  listPath?: string
  /** 見出しの文の鍵を替える（書き込みの 403 の tables.problem.writeForbidden など。B6 の D3） */
  messageKey?: string
}

const RETRYABLE: ReadonlySet<ReadProblemKind> = new Set([
  'unavailable',
  'unknown-server',
  'unknown-client',
  'network',
  'payload-too-large',
  'bad-request',
])

const BACK_TO_LIST: ReadonlySet<ReadProblemKind> = new Set([
  'row-not-found',
  'mismatch',
  'not-unique',
])

/** 開けない・つながらないときの表示 */
export function BusinessDataProblemView({
  kind,
  onRetry,
  listPath,
  messageKey,
}: BusinessDataProblemViewProps) {
  const { t } = useTranslation()
  const workRole = useWorkRole()
  const message = t(messageKey ?? readProblemMessageKey(kind))
  const showList = BACK_TO_LIST.has(kind) && listPath !== undefined
  return (
    <div className="tables-problem" data-testid="tables-problem">
      <h1 className="tables-heading" data-testid="tables-heading">
        {message}
      </h1>
      {kind === 'forbidden' && (
        <p className="tables-text">
          {t('tables.currentWorkRole', { name: workRole.current?.name ?? t('tables.noWorkRole') })}
        </p>
      )}
      {(kind === 'forbidden' || kind === 'table-not-found') && (
        <p className="tables-text">{t('tables.chooseFromMenu')}</p>
      )}
      <div className="tables-actions">
        {kind === 'forbidden' && workRole.roles.length >= 2 && (
          <Button variant="secondary" onClick={() => workRole.focusWorkRoleSwitcher()}>
            {t('tables.switchWorkRole')}
          </Button>
        )}
        {RETRYABLE.has(kind) && onRetry !== undefined && (
          <Button variant="secondary" onClick={onRetry}>
            {t('tables.retry')}
          </Button>
        )}
        {showList ? (
          <Link className="tables-link" to={listPath}>
            {t('tables.backToList')}
          </Link>
        ) : (
          <Link className="tables-link" to="/">
            {t('tables.goHome')}
          </Link>
        )}
      </div>
    </div>
  )
}
