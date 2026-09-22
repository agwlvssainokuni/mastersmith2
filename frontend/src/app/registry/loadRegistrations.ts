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
// 登録用ファイルの読み込みの結果（import.meta.glob の結果の形）を、登録の一覧に変える（BR7.1）。
// glob の呼び出しは registrationModules.ts に分け、ここは試験できる純粋な関数にする。
import { RegistrationError, type FeatureRegistration } from './types'

/** 登録用ファイルが名前付きでエクスポートする値の名前。 */
export const REGISTRATION_EXPORT_NAME = 'registration'

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

/**
 * 読み込んだ登録用ファイルの一覧から、登録の一覧を作る。読み込んだ順を保つ。
 * 決まった名前のエクスポートが無いファイルがあれば、そのファイルを示して失敗させる。
 */
export function loadRegistrations(
  modules: Readonly<Record<string, unknown>>,
): FeatureRegistration[] {
  const registrations: FeatureRegistration[] = []
  const problems: string[] = []
  for (const [file, module] of Object.entries(modules)) {
    const value = isObject(module) ? module[REGISTRATION_EXPORT_NAME] : undefined
    if (isObject(value) && typeof value.featureId === 'string') {
      registrations.push(value as unknown as FeatureRegistration)
    } else {
      problems.push(`${file}: 名前付きのエクスポート "${REGISTRATION_EXPORT_NAME}" がありません`)
    }
  }
  if (problems.length > 0) {
    throw new RegistrationError(problems)
  }
  return registrations
}
