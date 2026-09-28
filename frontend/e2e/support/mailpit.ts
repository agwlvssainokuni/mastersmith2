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
// 手元の受け手（Mailpit）から招待メールのリンクを取り出す手伝い（Intent 260925-user-management の B5 の共用の手伝い。
// U6 の基盤の設計 cicd-pipeline.md 4.3、Q1 C）。E2E-1（090）が使い、U7 も使える。
// - Mailpit の API は GET（検索と1通の読み取り）だけを使い、書き込み・消す API は使わない（メールは開発者が片付ける）。
// - Node の組み込みの fetch で呼び、Playwright の request を使わない（Mailpit の応答の本文をトレースと報告に記録させない）。
// - 失敗の知らせ・console・注記・添付・標準出力に、宛先・URL・トークン・本文を載せない（件数と種類だけ）。
import { expect } from '@playwright/test'

/**
 * Mailpit の API の場所（compose.yaml の公開の番号 8025）。Gradle の e2eTest の前提の確かめ（build.gradle.kts の
 * mailpitInfoUrl）と同じ場所で、変えるときは両方をそろえる（U6 の計画の9節の決定 3）。
 */
export const MAILPIT_API_URL = 'http://127.0.0.1:8025'

/** 招待メールを待つ上限（ミリ秒）と間隔 */
const WAIT_TIMEOUT_MS = 10_000
const WAIT_INTERVAL_MS = 500

/** 登録の完了のリンクの形（ベース URL の後ろに /register#token=） */
const REGISTRATION_LINK = /https?:\/\/[^\s"'<>]+\/register#token=[^\s"'<>]+/g

interface MailpitAddress {
  Address?: unknown
}

interface MailpitSummary {
  ID?: unknown
  To?: unknown
}

/** Mailpit の API を GET で読む。読めなければ undefined（理由・URL は出さない）。 */
async function getJson(path: string): Promise<unknown> {
  try {
    const response = await fetch(`${MAILPIT_API_URL}${path}`, { method: 'GET' })
    if (!response.ok) {
      return undefined
    }
    return (await response.json()) as unknown
  } catch {
    return undefined
  }
}

/** 宛先がそのアドレスと完全に一致するメッセージの ID を探す（検索は to: の問い合わせ）。 */
async function searchExact(address: string): Promise<string[]> {
  const query = new URLSearchParams({ query: `to:"${address}"` })
  const body = (await getJson(`/api/v1/search?${query.toString()}`)) as
    { messages?: MailpitSummary[] } | undefined
  const messages = Array.isArray(body?.messages) ? body.messages : []
  return messages
    .filter(
      (message) =>
        Array.isArray(message.To) &&
        (message.To as MailpitAddress[]).some((to) => to.Address === address),
    )
    .map((message) => message.ID)
    .filter((id): id is string => typeof id === 'string')
}

/** HTML の文字参照を戻す（テンプレートのエンジンは & < > " を置き換える）。 */
function decodeEntities(value: string): string {
  return value
    .replace(/&#x([0-9a-f]+);/gi, (_, hex: string) => String.fromCodePoint(parseInt(hex, 16)))
    .replace(/&#(\d+);/g, (_, dec: string) => String.fromCodePoint(Number(dec)))
    .replace(/&quot;/g, '"')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&')
}

/** 文字の中の登録の完了のリンクを重なりなく集める。 */
function linksIn(text: string): string[] {
  return [...new Set(text.match(REGISTRATION_LINK) ?? [])]
}

/**
 * そのアドレス宛ての招待メールを Mailpit で探し、登録の完了のリンク（/register#token=…）を取り出す。
 * 宛先が完全に一致するメールがちょうど1通で、HTML の href と本文の文字のリンクが同じ1つであることを確かめる。
 * 見つからない・合わないときは、件数と種類だけの失敗にする。
 */
export async function findInvitationLink(address: string): Promise<string> {
  let ids: string[] = []
  await expect
    .poll(
      async () => {
        ids = await searchExact(address)
        return ids.length
      },
      {
        message: '招待メールが見つかりません（Mailpit の起動と U1 の SMTP の設定を確かめる）',
        timeout: WAIT_TIMEOUT_MS,
        intervals: [WAIT_INTERVAL_MS],
      },
    )
    .toBeGreaterThan(0)
  expect(ids.length, '宛先が一致する招待メールの件数').toBe(1)

  const message = (await getJson(`/api/v1/message/${encodeURIComponent(ids[0] ?? '')}`)) as
    { HTML?: unknown; Text?: unknown } | undefined
  const html = typeof message?.HTML === 'string' ? message.HTML : ''
  const text = typeof message?.Text === 'string' ? message.Text : ''
  const hrefs = linksIn(
    [...html.matchAll(/href="([^"]*)"/g)].map((match) => decodeEntities(match[1] ?? '')).join(' '),
  )
  const htmlText = linksIn(decodeEntities(html.replace(/<[^>]*>/g, ' ')))
  const plainText = linksIn(text)
  expect(hrefs.length, 'HTML の href のリンクの件数').toBe(1)
  expect(htmlText.length, '本文の文字のリンクの件数').toBe(1)
  expect(hrefs[0] === htmlText[0], 'href と本文の文字のリンクが同じ').toBe(true)
  // 文字の本文（text/plain）があれば、そのリンクも同じであること。
  expect(
    plainText.every((link) => link === hrefs[0]),
    '文字の本文のリンクが HTML と同じ',
  ).toBe(true)
  return hrefs[0] ?? ''
}
