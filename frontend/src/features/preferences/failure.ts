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
// API の失敗の振り分け（frontend-components.md の 5節、functional-spec.md の 6節）。ApiClient の ApiError のうち、
// サーバーが応答を返したものの code を読む。形の誤り（PreferencesShapeError）と通信の失敗は undefined。
import type { ApiResponseError } from '../../shared/api-client/apiError'

/** サーバーが返したエラー応答か。 */
export function isApiResponseError(error: unknown): error is ApiResponseError {
  return (
    typeof error === 'object' &&
    error !== null &&
    (error as { kind?: unknown }).kind === 'response' &&
    typeof (error as { status?: unknown }).status === 'number'
  )
}

/** 400 の応答の code（400 でなければ undefined）。 */
export function badRequestCode(error: unknown): string | undefined {
  return isApiResponseError(error) && error.status === 400 ? error.code : undefined
}
