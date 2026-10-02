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
// 検索（functional-spec.md の W3・D13・D15、frontend-components.md の 4節、AC1.1.10・AC1.1.11）。
// role="search" の form で Enter と [検索] を受ける。見える label「検索」と説明の文を入力欄に aria-describedby で結び、
// 誤りがあれば入力欄の下に出して aria-invalid を付ける（説明の文は誤りのときも残す）。
// busy（一覧の読み直しの間）は [検索]・[検索を消す] に aria-disabled="true" を付け、disabled 属性は付けない（フォーカスを
// 外さない）。押下と Enter の送信は busy の間は何もしない（フックの側でも捨てる。D13 の (2)・(3)）。
// 入力欄は HTML の maxLength で止めない（前後の空白を除いて数えるため、機能設計 10節の (a)）。
import { Button, FormField, TextInput } from 'make-you-chic-ui'
import { useId, type FormEvent, type MouseEvent } from 'react'
import { useUserAdminText } from './useUserAdminText'
import './UserSearchBox.css'

export interface UserSearchBoxProps {
  /** 入力欄の値（入れたまま） */
  value: string
  /** 入力欄の下に出す誤りの文言（無ければ undefined） */
  error?: string
  /** 一覧の読み直しの間 */
  busy: boolean
  onChange: (value: string) => void
  onSearch: () => void
  onClear: () => void
}

/** 検索 */
export function UserSearchBox({
  value,
  error,
  busy,
  onChange,
  onSearch,
  onClear,
}: UserSearchBoxProps) {
  const t = useUserAdminText()
  const descriptionId = useId()
  const errorId = useId()

  function handleSubmit(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault()
    if (!busy) {
      onSearch()
    }
  }

  function handleClear(event: MouseEvent<HTMLButtonElement>): void {
    event.preventDefault()
    if (!busy) {
      onClear()
    }
  }

  return (
    <form
      role="search"
      className="useradmin-search"
      noValidate
      onSubmit={handleSubmit}
      data-testid="useradmin-search"
    >
      <FormField label={t('useradmin.search.label')} className="useradmin-search-field">
        <TextInput
          type="search"
          autoComplete="off"
          value={value}
          onChange={onChange}
          aria-describedby={error !== undefined ? `${errorId} ${descriptionId}` : descriptionId}
          aria-invalid={error !== undefined ? true : undefined}
          data-testid="useradmin-search-input"
        />
        <span id={descriptionId} className="useradmin-search-description">
          {t('useradmin.search.description')}
        </span>
        {error !== undefined && (
          <span
            id={errorId}
            role="alert"
            className="useradmin-search-error"
            data-testid="useradmin-search-error"
          >
            {error}
          </span>
        )}
      </FormField>
      <div className="useradmin-search-actions">
        <Button
          type="submit"
          variant="primary"
          aria-disabled={busy || undefined}
          data-testid="useradmin-search-submit"
        >
          {t('useradmin.search.submit')}
        </Button>
        <Button
          variant="secondary"
          aria-disabled={busy || undefined}
          onClick={handleClear}
          data-testid="useradmin-search-clear"
        >
          {t('useradmin.search.clear')}
        </Button>
      </div>
    </form>
  )
}
