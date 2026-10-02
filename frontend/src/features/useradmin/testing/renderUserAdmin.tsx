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
// 利用者の管理の画面のテストの補助（テストからだけ使う。画面からは読み込まない）。
// 文言を登録した骨組みの Provider と、make-you-chic-ui の Toast・Modal の Provider の中に部品を描画する（招待の
// testing/renderInvitation.tsx と同じ形）。ログイン状態の提供元（管理者・テーマと文字の大きさ・言語）を渡せ、
// 骨組みの ShellLayout の中に描く選択（S6 と上の帯の確かめ）を持つ（計画 8節の D-6・D-7）。
import type { RenderResult } from '@testing-library/react'
import { ModalStackProvider, ToastProvider } from 'make-you-chic-ui'
import type { ReactElement } from 'react'
import type { PartialDisplaySettings } from '../../../app/display-settings/displaySettingsTypes'
import { ShellLayout } from '../../../app/layout/ShellLayout'
import type { FeatureRegistration, LoginStateProvider } from '../../../app/registry/types'
import { fakeProvider, renderWithProviders } from '../../../app/testing/renderWithProviders'
import { userAdminMessages } from '../messages'
import { USER_ADMIN_PATH } from '../registration'

/** 文言だけを持つ登録 */
export const userAdminMessagesRegistration: FeatureRegistration = {
  featureId: 'useradmin',
  messages: userAdminMessages,
}

/** 描き方の選択 */
export interface RenderUserAdminOptions {
  /** ブラウザの希望言語（既定は ja） */
  languages?: readonly string[]
  /** 部品を骨組みの ShellLayout の中に描く（S6 と上の帯の確かめ） */
  withShell?: boolean
  /** ログイン状態の提供元（既定はログインしていない状態。preferences を渡すと管理者の提供元を作る） */
  provider?: LoginStateProvider
  /** 管理者の提供元を作るときの利用者の表示の設定（テーマと文字の大きさ・言語） */
  preferences?: PartialDisplaySettings
  /** 登録（S6 の見出しを確かめるときは本物の登録を渡す。既定は文言だけの登録） */
  registrations?: readonly FeatureRegistration[]
}

/** 管理者のログイン状態の提供元（氏名は自分の行の見本と同じ） */
export function adminProvider(preferences?: PartialDisplaySettings): LoginStateProvider {
  return fakeProvider({ loggedIn: true, admin: true, displayName: '管理 一郎', preferences })
}

/** 部品を描画する。 */
export function renderUserAdmin(
  ui: ReactElement,
  options: RenderUserAdminOptions = {},
): RenderResult {
  const {
    languages = ['ja-JP'],
    withShell = false,
    preferences,
    registrations = [userAdminMessagesRegistration],
  } = options
  const provider =
    options.provider ?? (preferences !== undefined ? adminProvider(preferences) : undefined)
  const content = withShell ? <ShellLayout>{ui}</ShellLayout> : ui
  return renderWithProviders(
    <ToastProvider>
      <ModalStackProvider>{content}</ModalStackProvider>
    </ToastProvider>,
    {
      route: USER_ADMIN_PATH,
      registrations,
      languages,
      provider,
    },
  )
}
