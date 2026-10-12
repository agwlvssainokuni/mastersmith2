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
// グループの管理の画面のテストの補助（テストからだけ使う。画面からは読み込まない。計画 unit-test-instructions.md の 6節）。
// 文言を登録した骨組みの Provider と make-you-chic-ui の Modal の Provider の中に部品を描く（既存の renderWithProviders、
// MemoryRouter）。グループの画面は useBlocker を使わないため data router の描き方は持たない。
import type { RenderResult } from '@testing-library/react'
import { ModalStackProvider, ToastProvider } from 'make-you-chic-ui'
import type { ReactElement } from 'react'
import { ShellLayout } from '../../../app/layout/ShellLayout'
import type { FeatureRegistration, LoginStateProvider } from '../../../app/registry/types'
import { fakeProvider, renderWithProviders } from '../../../app/testing/renderWithProviders'
import { groupAdminMessages } from '../messages'
import { GROUP_LIST_PATH } from '../paths'

/** 文言だけを持つ登録 */
export const groupAdminMessagesRegistration: FeatureRegistration = {
  featureId: 'groupadmin',
  messages: groupAdminMessages,
}

/** 管理者のログイン状態の提供元 */
export function adminProvider(): LoginStateProvider {
  return fakeProvider({ loggedIn: true, admin: true, displayName: '管理 一郎' })
}

/** 描き方の選択 */
export interface RenderGroupAdminOptions {
  /** 最初の画面の URL */
  route?: string
  /** ブラウザの希望言語（既定は ja） */
  languages?: readonly string[]
  /** 登録（既定は文言だけの登録） */
  registrations?: readonly FeatureRegistration[]
  /** ログイン状態の提供元（既定は管理者） */
  provider?: LoginStateProvider
  /** 部品を骨組みの ShellLayout の中に描く（403 の表示の確かめ） */
  withShell?: boolean
}

/** 部品を描く（MemoryRouter）。 */
export function renderGroupAdmin(
  ui: ReactElement,
  options: RenderGroupAdminOptions = {},
): RenderResult {
  const {
    route = GROUP_LIST_PATH,
    languages = ['ja-JP'],
    registrations = [groupAdminMessagesRegistration],
    provider = adminProvider(),
    withShell = false,
  } = options
  const content = withShell ? <ShellLayout>{ui}</ShellLayout> : ui
  return renderWithProviders(
    <ToastProvider>
      <ModalStackProvider>{content}</ModalStackProvider>
    </ToastProvider>,
    { route, registrations, languages, provider },
  )
}
