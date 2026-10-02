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
// DSL の管理画面のテストの補助（テストからだけ使う。画面からは読み込まない）。
// 文言を登録した骨組みの Provider と、make-you-chic-ui の Toast・Modal の Provider の中に部品を描画する。
import type { RenderResult } from '@testing-library/react'
import { ModalStackProvider, ToastProvider } from 'make-you-chic-ui'
import type { ReactElement } from 'react'
import { ShellLayout } from '../../../app/layout/ShellLayout'
import { renderWithProviders } from '../../../app/testing/renderWithProviders'
import type { FeatureRegistration, LoginStateProvider } from '../../../app/registry/types'
import { dslMessages } from '../messages'

/** 文言だけを持つ登録 */
export const dslMessagesRegistration: FeatureRegistration = {
  featureId: 'dsl',
  messages: dslMessages,
}

/**
 * 描き方の選択（U4 の R-03）。既定は今までどおり ShellLayout なし・文言だけの登録・ログイン状態の提供元なし。
 * 画面が「権限が無い」（S6）に置き換わることを確かめるテストだけが `withShell` を使う。
 */
export interface RenderDslOptions {
  /** 部品を骨組みの ShellLayout の中に描く（S6 はコンテンツの領域に出るため） */
  withShell?: boolean
  /** ログイン状態の提供元（ShellLayout のサイドバー・上の帯に使う） */
  provider?: LoginStateProvider
  /** 登録（S6 の見出しを確かめるときは本物の登録を渡す。既定は文言だけの登録） */
  registrations?: readonly FeatureRegistration[]
}

/** 部品を描画する（languages はブラウザの希望言語、options は描き方の選択）。 */
export function renderDsl(
  ui: ReactElement,
  languages: readonly string[] = ['ja-JP'],
  options: RenderDslOptions = {},
): RenderResult {
  const { withShell = false, provider, registrations = [dslMessagesRegistration] } = options
  const content = withShell ? <ShellLayout>{ui}</ShellLayout> : ui
  return renderWithProviders(
    <ToastProvider>
      <ModalStackProvider>{content}</ModalStackProvider>
    </ToastProvider>,
    { route: '/admin/dsl', registrations, languages, provider },
  )
}
