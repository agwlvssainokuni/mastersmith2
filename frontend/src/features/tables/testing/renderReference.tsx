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
// 参照の部品のテストの描き方（Intent 261029-reference-lookup-cleanup の U3。テストからだけ使う）。業務データの文言と
// Modal の重なりの Provider で包み、部品を main の中に描く。make-you-chic-ui の部品は差し替えない（本物を描く）。
import { ModalStackProvider } from 'make-you-chic-ui'
import type { ReactElement } from 'react'
import { renderWithProviders } from '../../../app/testing/renderWithProviders'
import { registration } from '../registration'

/** 参照の部品を描く */
export function renderReference(ui: ReactElement, options: { languages?: string[] } = {}) {
  return renderWithProviders(
    <ModalStackProvider>
      <main>{ui}</main>
    </ModalStackProvider>,
    { registrations: [registration], languages: options.languages ?? ['ja-JP'] },
  )
}
