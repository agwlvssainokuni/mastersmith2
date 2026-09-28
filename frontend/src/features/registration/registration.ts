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
// 登録の完了の画面を画面の骨組みに登録する（functional-spec.md の W1、frontend-components.md の 2節、NFR6.3）。
// 骨組み（src/app/）のファイルは書き換えない。招待のリンクから開く、ログインなしのアプリシェルの外の画面で、
// サイドバー・ユーザーメニューの項目は持たない。画面は遅延読み込み（auth・DSL と同じ lazy）で、入口の JavaScript に入れない。
import { lazy } from 'react'
import type { FeatureRegistration } from '../../app/registry/types'
import { registrationMessages } from './messages'

const RegistrationPage = lazy(() =>
  import('./RegistrationPage').then((module) => ({ default: module.RegistrationPage })),
)

/** 登録の完了の画面の URL（招待のリンクは `<ベース URL>/register#token=<トークン>`） */
export const REGISTRATION_PATH = '/register'

/** 登録の完了の画面の機能の登録 */
export const registration: FeatureRegistration = {
  featureId: 'registration',
  routes: [
    {
      path: REGISTRATION_PATH,
      screen: RegistrationPage,
      layout: 'STANDALONE',
      access: 'PUBLIC',
    },
  ],
  messages: registrationMessages,
}
