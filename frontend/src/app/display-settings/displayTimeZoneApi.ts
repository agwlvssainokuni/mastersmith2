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
// 表示のタイムゾーンの読み取り（Intent 261102-timezone-ui-polish の U2、BR2.12・RD4）。GET /api/me/preferences から displayTimeZone
// だけを取り出す。プリファレンスの画面の API とは型を共有しない（機能どうしを import しない向きを守るため）。
// 形が違えば DisplayTimeZoneShapeError（ApiError とは別の失敗の種類。値は持たない）。値をコンソール・ブラウザの保存に出さない。

import { apiRequest } from '../../shared/api-client/apiClient'

/** 名前の長さの上限（サーバーの列の長さと同じ） */
export const MAX_DISPLAY_TIME_ZONE_LENGTH = 64

/** 応答の形が決めたものでない（値は持たない） */
export class DisplayTimeZoneShapeError extends Error {
  constructor() {
    super('表示のタイムゾーンの応答の形が違います')
    this.name = 'DisplayTimeZoneShapeError'
  }
}

/** 表示のタイムゾーンを読む関数（テストで差し替える） */
export type DisplayTimeZoneFetcher = () => Promise<string | null>

/** 自分の表示のタイムゾーンを読む。文字列（64 文字以下）か null のときだけ受ける。 */
export async function fetchDisplayTimeZone(): Promise<string | null> {
  const response = await apiRequest('/api/me/preferences', { method: 'GET' })
  let body: unknown
  try {
    body = await response.json()
  } catch {
    throw new DisplayTimeZoneShapeError()
  }
  if (typeof body !== 'object' || body === null || Array.isArray(body)) {
    throw new DisplayTimeZoneShapeError()
  }
  const value = (body as Record<string, unknown>).displayTimeZone
  if (value === null) {
    return null
  }
  if (typeof value === 'string' && value.length <= MAX_DISPLAY_TIME_ZONE_LENGTH) {
    return value
  }
  throw new DisplayTimeZoneShapeError()
}
