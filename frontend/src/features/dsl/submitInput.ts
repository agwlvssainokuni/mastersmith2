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
// 投入の入力（BR2.2・BR2.3、NFR1.20・NFR3.11）。入力のしかたと、送る前の大きさの判定、送る本文の読み出し。
// 大きさの判定は案内だけで、サーバー側の上限（413 DSL_TOO_LARGE）の代わりにしない。
// 上限は 10 × 1024 × 1024 バイト（U5 の NFR 要件 tech-stack-decisions.md 2節。承認済みの機能設計の 5MB との差）。
import type { SubmitSource } from './api/types'

/** 投入の大きさの上限（バイト） */
export const MAX_SUBMIT_BYTES = 10 * 1024 * 1024

/** JSON Schema（同じオリジンの固定のパス。ログインなしで取れる静的なファイル） */
export const DSL_SCHEMA_PATH = '/dsl/dsl-schema-v1.json'

/** 入力のしかた */
export type SubmitMode = 'file' | 'paste'

/** 投入の入力（切り替えても、もう一方の入力は画面の中で保つ） */
export interface SubmitInput {
  mode: SubmitMode
  file: File | null
  pasteText: string
}

/** 空の入力 */
export const EMPTY_SUBMIT_INPUT: SubmitInput = { mode: 'file', file: null, pasteText: '' }

/** 選んでいる方の入力の状態 */
export type SubmitReadiness = 'empty' | 'tooLarge' | 'ready'

/** 送る本文と出どころ */
export interface SubmitPayload {
  text: string
  source: SubmitSource
}

/**
 * 文字列を UTF-8 にしたときのバイト数を、バイト列を作らずに数える。
 * 対になっていないサロゲートは、TextEncoder と同じく置き換え文字（3 バイト）として数える。
 */
export function utf8ByteLength(text: string): number {
  let bytes = 0
  for (let i = 0; i < text.length; i += 1) {
    const code = text.charCodeAt(i)
    if (code < 0x80) {
      bytes += 1
    } else if (code < 0x800) {
      bytes += 2
    } else if (code >= 0xd800 && code <= 0xdbff && i + 1 < text.length) {
      const next = text.charCodeAt(i + 1)
      if (next >= 0xdc00 && next <= 0xdfff) {
        bytes += 4
        i += 1
      } else {
        bytes += 3
      }
    } else {
      bytes += 3
    }
  }
  return bytes
}

/** 選んでいる方の入力の状態を決める（ファイルは読まずに File.size で判定する）。 */
export function submitReadiness(input: SubmitInput): SubmitReadiness {
  if (input.mode === 'file') {
    if (input.file === null) {
      return 'empty'
    }
    return input.file.size > MAX_SUBMIT_BYTES ? 'tooLarge' : 'ready'
  }
  if (input.pasteText === '') {
    return 'empty'
  }
  return utf8ByteLength(input.pasteText) > MAX_SUBMIT_BYTES ? 'tooLarge' : 'ready'
}

/**
 * 送る本文を読み出す。選んでいる方だけを使い、上限の内のファイルはテキスト（UTF-8）として読む（BR2.2）。
 * 入力が無い・上限を超えるときは読まずに null を返す。
 */
export async function readSubmitPayload(input: SubmitInput): Promise<SubmitPayload | null> {
  if (submitReadiness(input) !== 'ready') {
    return null
  }
  if (input.mode === 'file' && input.file !== null) {
    return { text: await input.file.text(), source: 'UPLOAD' }
  }
  return { text: input.pasteText, source: 'PASTE' }
}
