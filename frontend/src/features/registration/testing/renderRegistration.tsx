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
// 登録の完了の画面のテストの補助（テストからだけ使う。画面からは読み込まない）。
// 骨組みの renderWithProviders に登録の完了の機能の文言（と、渡されたほかの機能の登録）を渡し、
// 最初の画面の URL の既定を /register#token=<テストのトークン> にして描く。U4 の口とログイン状態は本物で動かす。
// 画面の言語はブラウザの希望言語（languages）で切り替える（既定は ja）。
import type { RenderResult } from '@testing-library/react'
import type { ReactElement } from 'react'
import { useLocation, useNavigationType } from 'react-router'
import type { FeatureRegistration, LoginStateProvider } from '../../../app/registry/types'
import { renderWithProviders } from '../../../app/testing/renderWithProviders'
import { registrationMessages } from '../messages'
import { TEST_TOKEN } from './fixtures'

/** 文言だけを持つ登録 */
export const registrationMessagesRegistration: FeatureRegistration = {
  featureId: 'registration',
  messages: registrationMessages,
}

/** 描画の設定 */
export interface RenderRegistrationOptions {
  /** 最初の画面の URL（既定は /register#token=<テストのトークン>） */
  route?: string
  /** ブラウザの希望言語（既定は ja） */
  languages?: readonly string[]
  /** ログイン状態の提供元（既定は無し＝未ログイン） */
  provider?: LoginStateProvider
  /** 登録の完了の文言のほかに渡す登録（例: ログインの画面の文言） */
  registrations?: readonly FeatureRegistration[]
}

/** 今の場所のフラグメント・問い合わせと、最後の移動の種類を示す（URL の確かめに使う）。 */
export function RegistrationLocationProbe() {
  const { hash, search } = useLocation()
  const navigationType = useNavigationType()
  return (
    <output data-testid="registration-location" hidden>
      <span data-testid="registration-location-hash">{hash}</span>
      <span data-testid="registration-location-search">{search}</span>
      <span data-testid="registration-location-type">{navigationType}</span>
    </output>
  )
}

/** 部品を描画する。 */
export function renderRegistration(
  ui: ReactElement,
  options: RenderRegistrationOptions = {},
): RenderResult {
  const {
    route = `/register#token=${TEST_TOKEN}`,
    languages = ['ja-JP'],
    provider,
    registrations = [],
  } = options
  return renderWithProviders(
    <>
      {ui}
      <RegistrationLocationProbe />
    </>,
    {
      route,
      languages,
      provider,
      registrations: [registrationMessagesRegistration, ...registrations],
    },
  )
}
