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
// SVG の中身の検査（scripts/check-svg-safety.mjs）のテストが共有する道具。
// scripts/ は型を持たない .mjs のため、動的な import() と手書きの型で読む（src/perfEvidenceRedactor.test.ts と同じ形）。
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { pathToFileURL } from 'node:url'

export type SvgCheckResult = { ok: true } | { ok: false; code: string; position: number }

export interface SvgSafetyModule {
  MAX_SVG_LENGTH: number
  MAX_DEPTH: number
  ALLOWED_ELEMENTS: Set<string>
  ATTRIBUTE_GRAMMARS: Map<string, (value: string) => boolean>
  checkSvgContent: (content: unknown, options?: { standalone?: boolean }) => SvgCheckResult
  collectSvgFiles: (root?: string) => string[]
}

const SCRIPTS = resolve(import.meta.dirname, '../scripts')
const TEMPLATE = resolve(import.meta.dirname, '../../config/license-header.txt')

/** 検査の道具を読む */
export async function loadSvgTool(): Promise<SvgSafetyModule> {
  const url = pathToFileURL(resolve(SCRIPTS, 'check-svg-safety.mjs')).href
  return (await import(/* @vite-ignore */ url)) as unknown as SvgSafetyModule
}

/** ライセンスヘッダーのひな形から作る、HTML のコメント形のヘッダー（config/license-header.txt と同じ） */
export function svgLicenseHeader(): string {
  const lines = readFileSync(TEMPLATE, 'utf8')
    .replace(/\r\n/g, '\n')
    .replace(/\n+$/, '')
    .split('\n')
  return ['<!--', ...lines.map((l) => (l === '' ? '' : `  ${l}`)), '-->'].join('\n')
}

/** 根の svg 要素の開始タグ（standalone のファイルの形） */
export const ROOT_OPEN =
  '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" width="64" height="64">'

/** 実際の public/ の SVG と同じ形の、許可リストの要素・属性だけの良い例（ヘッダー付き） */
export function goodSvg(inner = '<path d="M4 4H20V20H4Z" fill="currentColor"/>'): string {
  return `${svgLicenseHeader()}\n${ROOT_OPEN}${inner}</svg>\n`
}

/** ヘッダー無しで、根の xmlns も持たない形（画面の部品を描いた結果の形） */
export function goodFragment(inner = '<path d="M4 4H20V20H4Z" fill="currentColor"/>'): string {
  return `<svg viewBox="0 0 64 64" width="64" height="64" aria-hidden="true" focusable="false">${inner}</svg>`
}
