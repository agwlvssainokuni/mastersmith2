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
// 検索の部品のテスト（functional-spec.md の W3・D13・D15、frontend-components.md の 8節、AC1.1.10・AC1.1.11、NFR7.2）。
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { renderUserAdmin } from './testing/renderUserAdmin'
import { UserSearchBox, type UserSearchBoxProps } from './UserSearchBox'

function Harness(props: Partial<UserSearchBoxProps>) {
  const [value, setValue] = useState(props.value ?? '')
  return (
    <UserSearchBox
      value={value}
      error={props.error}
      busy={props.busy ?? false}
      onChange={(next) => {
        setValue(next)
        props.onChange?.(next)
      }}
      onSearch={props.onSearch ?? (() => {})}
      onClear={props.onClear ?? (() => {})}
    />
  )
}

function input(): HTMLElement {
  return screen.getByRole('searchbox', { name: '検索' })
}

describe('UserSearchBox', () => {
  it('searches with the button and with Enter', async () => {
    const user = userEvent.setup()
    const onSearch = vi.fn()
    renderUserAdmin(<Harness onSearch={onSearch} />)
    await user.type(input(), '山田')
    await user.click(screen.getByRole('button', { name: '検索' }))
    await user.type(input(), '{Enter}')
    expect(onSearch).toHaveBeenCalledTimes(2)
    expect(input()).toHaveValue('山田')
  })

  it('links the description and shows no error by default', () => {
    renderUserAdmin(<Harness />)
    expect(screen.getByRole('search')).toBeInTheDocument()
    expect(input()).toHaveAccessibleDescription(
      'メールアドレスまたは氏名の一部。前後の空白を除いて 254 文字まで',
    )
    expect(input()).not.toHaveAttribute('aria-invalid')
    expect(screen.queryByTestId('useradmin-search-error')).toBeNull()
  })

  it('shows the limit error under the input with aria-invalid and keeps the description', () => {
    renderUserAdmin(<Harness value="x" error="検索の文字は 254 文字までにしてください。" />)
    expect(input()).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByTestId('useradmin-search-error')).toHaveTextContent(
      '検索の文字は 254 文字までにしてください。',
    )
    expect(input()).toHaveAccessibleDescription(
      '検索の文字は 254 文字までにしてください。 メールアドレスまたは氏名の一部。前後の空白を除いて 254 文字まで',
    )
    expect(input()).toHaveValue('x')
  })

  it('clears the search with the clear button', async () => {
    const user = userEvent.setup()
    const onClear = vi.fn()
    const onSearch = vi.fn()
    renderUserAdmin(<Harness value="山田" onClear={onClear} onSearch={onSearch} />)
    await user.click(screen.getByRole('button', { name: '検索を消す' }))
    expect(onClear).toHaveBeenCalledTimes(1)
    expect(onSearch).not.toHaveBeenCalled()
  })

  it('ignores the buttons and Enter while busy and keeps the focus', async () => {
    const user = userEvent.setup()
    const onSearch = vi.fn()
    const onClear = vi.fn()
    renderUserAdmin(<Harness value="山田" busy onSearch={onSearch} onClear={onClear} />)
    const search = screen.getByRole('button', { name: '検索' })
    const clear = screen.getByRole('button', { name: '検索を消す' })
    expect(search).toHaveAttribute('aria-disabled', 'true')
    expect(clear).toHaveAttribute('aria-disabled', 'true')
    expect(search).not.toBeDisabled()
    await user.click(search)
    expect(search).toHaveFocus()
    await user.click(clear)
    expect(clear).toHaveFocus()
    await user.click(input())
    await user.keyboard('{Enter}')
    expect(onSearch).not.toHaveBeenCalled()
    expect(onClear).not.toHaveBeenCalled()
    expect(input()).toHaveFocus()
  })

  it('shows English text on an English screen', () => {
    renderUserAdmin(<Harness />, { languages: ['en-US'] })
    expect(screen.getByRole('searchbox', { name: 'Search' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Clear search' })).toBeInTheDocument()
  })

  it('has no accessibility violations by default and with an error', async () => {
    const { container, unmount } = renderUserAdmin(<Harness />)
    expect(await axe(container)).toHaveNoViolations()
    unmount()
    const errorView = renderUserAdmin(
      <Harness value="x" error="検索の文字は 254 文字までにしてください。" />,
    )
    expect(await axe(errorView.container)).toHaveNoViolations()
  })
})
