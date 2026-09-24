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
import { renderWithProviders } from '../../../app/testing/renderWithProviders'
import type { FeatureRegistration } from '../../../app/registry/types'
import { dslMessages } from '../messages'

/** 文言だけを持つ登録 */
export const dslMessagesRegistration: FeatureRegistration = {
  featureId: 'dsl',
  messages: dslMessages,
}

/** 部品を描画する（languages はブラウザの希望言語）。 */
export function renderDsl(
  ui: ReactElement,
  languages: readonly string[] = ['ja-JP'],
): RenderResult {
  return renderWithProviders(
    <ToastProvider>
      <ModalStackProvider>{ui}</ModalStackProvider>
    </ToastProvider>,
    { route: '/admin/dsl', registrations: [dslMessagesRegistration], languages },
  )
}
