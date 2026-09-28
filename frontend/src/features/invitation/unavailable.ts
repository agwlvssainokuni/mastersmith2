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
// 招待を使えないときの警告の文の形（functional-spec.md の W3、AC1.1.6・AC2.2.9）。純粋な関数。
// 理由が1つならその理由の文、2つなら見出しと2つの理由、知っている理由が1つも無い（知らない値だけ・空）なら見出しと
// 一般の文。知らない理由の値は出さない。設定の値そのものは扱わない。
import { UNAVAILABLE_REASONS, type UnavailableReason } from './api/types'

/** 警告の文の形 */
export type UnavailableNotice =
  | { kind: 'single'; messageKey: string }
  | { kind: 'multiple'; titleKey: string; reasonKeys: string[] }
  | { kind: 'unknown'; titleKey: string; messageKey: string }

const TITLE_KEY = 'invitation.unavailable.title'

const KNOWN: ReadonlySet<string> = new Set(UNAVAILABLE_REASONS)

/** 知っている理由だけを、重なりを除いて並びのまま返す。 */
export function knownReasons(reasons: readonly unknown[]): UnavailableReason[] {
  const result: UnavailableReason[] = []
  for (const reason of reasons) {
    if (typeof reason === 'string' && KNOWN.has(reason)) {
      const known = reason as UnavailableReason
      if (!result.includes(known)) {
        result.push(known)
      }
    }
  }
  return result
}

/**
 * 理由の一覧から警告の文の形を決める。
 *
 * @param reasons 応答の unavailableReasons（知らない値を含みうる）
 */
export function unavailableNotice(reasons: readonly unknown[]): UnavailableNotice {
  const known = knownReasons(reasons)
  if (known.length === 0) {
    return { kind: 'unknown', titleKey: TITLE_KEY, messageKey: 'invitation.unavailable.unknown' }
  }
  if (known.length === 1) {
    return { kind: 'single', messageKey: `invitation.unavailable.${known[0]}` }
  }
  return {
    kind: 'multiple',
    titleKey: TITLE_KEY,
    reasonKeys: known.map((reason) => `invitation.unavailableReason.${reason}`),
  }
}
