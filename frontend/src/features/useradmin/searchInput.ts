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
// 検索の文字の画面の側の確かめ（functional-spec.md の D15・W3、frontend-components.md の 7節、AC1.1.4・AC1.1.11）。
// 前後の White_Space（半角・全角を含む）を trimDisplayName で除いてから countCodePoints で数え、上限を超えれば送らない。
// 除いた後が空なら検索なし（q を付けない）。送る値は除いた後の値。判定はサーバーが正で、この確かめはその代わりにしない。
import { countCodePoints, trimDisplayName } from '../../shared/validation/codePoints'

/**
 * 検索の文字の上限（前後の空白を除いた後のコードポイントの数）。サーバー（U3 の Q3 A、
 * `cherry.mastersmith.useradmin.web.SearchTextConverter` の上限）と同じ 254 にする（変えるときは両方を同じ変更で直す）。
 * この機能だけが使うため、shared/validation/limits.ts には置かない。
 */
export const USER_SEARCH_MAX_CODE_POINTS = 254

/** 検索の文字の確かめの結果 */
export type SearchInputCheck =
  /** 送ってよい（searchText が空なら検索なし） */
  | { ok: true; searchText: string }
  /** 上限を超えた（送らない） */
  | { ok: false; reason: 'tooLong' }

/** 検索の入力欄の値を確かめ、送る値（前後の空白を除いた値）か上限の誤りを返す。 */
export function checkSearchInput(value: string): SearchInputCheck {
  const trimmed = trimDisplayName(value)
  if (countCodePoints(trimmed) > USER_SEARCH_MAX_CODE_POINTS) {
    return { ok: false, reason: 'tooLong' }
  }
  return { ok: true, searchText: trimmed }
}
