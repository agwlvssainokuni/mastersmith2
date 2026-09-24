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
// DSL の管理の API（契約 C6 の 10 本）を呼ぶ関数（frontend-components.md の 1.2、BR2.1〜BR2.5・BR2.8）。
// すべて既存の ApiClient を通し、アクセストークンは ApiClient の中だけで扱う（NFR3.10）。
// 失敗は ApiClient の ApiError（kind・status・code、本文が Problem Details なら problem）として投げる。
import { apiDownload, apiRequest } from '../../../shared/api-client/apiClient'
import { networkError, type ApiError } from '../../../shared/api-client/apiError'
import type {
  DslErrorItem,
  DslErrorReport,
  DslFile,
  DslStatus,
  HistoryEntry,
  Preview,
  SubmitSource,
} from './types'

/** DSL の管理の API の根 */
export const DSL_API_ROOT = '/api/admin/dsl'

/** 各 API のパス */
export const DSL_API_PATHS = {
  status: `${DSL_API_ROOT}/status`,
  preview: `${DSL_API_ROOT}/preview`,
  generate: `${DSL_API_ROOT}/preview/generate`,
  previewDownload: `${DSL_API_ROOT}/preview/download`,
  apply: `${DSL_API_ROOT}/apply`,
  appliedDownload: `${DSL_API_ROOT}/applied/download`,
  history: `${DSL_API_ROOT}/history`,
} as const

/** 履歴の版をプレビューに戻す API のパス */
export function restorePath(revisionId: string): string {
  return `${DSL_API_PATHS.history}/${encodeURIComponent(revisionId)}/restore`
}

/** 投入の本文の形 */
export const YAML_CONTENT_TYPE = 'application/yaml'

/** ファイル名が読めないときのダウンロードのファイル名（BR2.4） */
export const DEFAULT_DOWNLOAD_FILE_NAME = 'dsl.yaml'

/** 本文を JSON として読む。読めなければ通信の失敗として扱う（画面は一般の文言を出す）。 */
async function readJson<T>(response: Response): Promise<T> {
  try {
    return (await response.json()) as T
  } catch {
    const error: ApiError = networkError()
    throw error
  }
}

async function getJson<T>(path: string): Promise<T> {
  return readJson<T>(await apiRequest(path, { method: 'GET' }))
}

async function postJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  return readJson<T>(await apiRequest(path, { ...init, method: 'POST' }))
}

/** 今の状態を読む（GET /status）。 */
export function getStatus(): Promise<DslStatus> {
  return getJson<DslStatus>(DSL_API_PATHS.status)
}

/** プレビューの中身を読む（GET /preview）。無ければ 404 DSL_PREVIEW_NOT_FOUND。 */
export function getPreview(): Promise<Preview> {
  return getJson<Preview>(DSL_API_PATHS.preview)
}

/** スキーマを読み込み、既定の DSL をプレビューに置く（POST /preview/generate）。 */
export function generatePreview(): Promise<Preview> {
  return postJson<Preview>(DSL_API_PATHS.generate)
}

/**
 * DSL を投入する（POST /preview?source=...）。本文は `application/yaml` の文字列で送る（BR2.2）。
 * 文字列の本文は UTF-8 で送られ、長さ（Content-Length）が付く。
 */
export function submitPreview(text: string, source: SubmitSource): Promise<Preview> {
  return postJson<Preview>(`${DSL_API_PATHS.preview}?source=${source}`, {
    headers: { 'Content-Type': YAML_CONTENT_TYPE },
    body: text,
  })
}

/** プレビューを破棄する（DELETE /preview）。無ければ 404 DSL_PREVIEW_NOT_FOUND。 */
export async function discardPreview(): Promise<void> {
  await apiRequest(DSL_API_PATHS.preview, { method: 'DELETE' })
}

/** 表示しているプレビューの識別を付けて適用する（POST /apply、BR2.5）。 */
export function applyPreview(previewId: string): Promise<DslStatus> {
  return postJson<DslStatus>(DSL_API_PATHS.apply, {
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ previewId }),
  })
}

/** 適用の履歴を読む（GET /history、新しい順）。 */
export function getHistory(): Promise<HistoryEntry[]> {
  return getJson<HistoryEntry[]>(DSL_API_PATHS.history)
}

/** 履歴の版をプレビューに戻す（POST /history/{revisionId}/restore）。 */
export function restoreRevision(revisionId: string): Promise<Preview> {
  return postJson<Preview>(restorePath(revisionId))
}

/**
 * `Content-Disposition` からファイル名を取る（`filename*` を優先し、次に `filename`）。
 * 読めなければ {@link DEFAULT_DOWNLOAD_FILE_NAME}。画面でファイル名を組み立てない（BR2.4）。
 */
export function fileNameFromContentDisposition(header: string | null): string {
  if (!header) {
    return DEFAULT_DOWNLOAD_FILE_NAME
  }
  const extended = /filename\*\s*=\s*(?:UTF-8|utf-8)''([^;]+)/.exec(header)
  if (extended) {
    try {
      const decoded = decodeURIComponent(extended[1].trim())
      if (decoded) {
        return decoded
      }
    } catch {
      // 符号化が壊れているときは、次の filename を見る。
    }
  }
  const plain = /filename\s*=\s*(?:"([^"]*)"|([^;\s]+))/.exec(header)
  const name = (plain?.[1] ?? plain?.[2] ?? '').trim()
  return name || DEFAULT_DOWNLOAD_FILE_NAME
}

async function download(path: string): Promise<DslFile> {
  const { blob, contentDisposition } = await apiDownload(path, { method: 'GET' })
  return { blob, fileName: fileNameFromContentDisposition(contentDisposition) }
}

/** プレビュー中の DSL を受け取る（GET /preview/download）。 */
export function downloadPreview(): Promise<DslFile> {
  return download(DSL_API_PATHS.previewDownload)
}

/** 適用中の DSL を受け取る（GET /applied/download）。 */
export function downloadApplied(): Promise<DslFile> {
  return download(DSL_API_PATHS.appliedDownload)
}

function isErrorItem(value: unknown): value is DslErrorItem {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const item = value as Record<string, unknown>
  const optionalNumber = (v: unknown) => v === undefined || v === null || typeof v === 'number'
  const optionalString = (v: unknown) => v === undefined || v === null || typeof v === 'string'
  return (
    typeof item.kind === 'string' &&
    typeof item.message === 'string' &&
    optionalNumber(item.line) &&
    optionalNumber(item.column) &&
    optionalString(item.path)
  )
}

/**
 * 失敗から誤りの一覧を取り出す（BR2.8）。422 の本文に `total`（1以上の整数）と `errors`（誤りの配列）が
 * あるときだけ返し、形が合わなければ undefined（画面は件数の分からない誤りとして一般の文言を出す）。
 */
export function readErrorReport(error: unknown): DslErrorReport | undefined {
  const apiError = error as ApiError | undefined
  if (apiError?.kind !== 'response' || apiError.problem === undefined) {
    return undefined
  }
  const { total, errors } = apiError.problem
  if (typeof total !== 'number' || !Number.isInteger(total) || total < 1) {
    return undefined
  }
  if (!Array.isArray(errors) || !errors.every(isErrorItem)) {
    return undefined
  }
  const items = errors.map((item) => ({
    kind: item.kind,
    line: item.line ?? null,
    column: item.column ?? null,
    path: item.path ?? null,
    message: item.message,
  }))
  return { total, errors: items }
}

/** 画面が使う API の関数の集まり（テストで差し替える） */
export const dslApi = {
  getStatus,
  getPreview,
  generatePreview,
  submitPreview,
  discardPreview,
  applyPreview,
  getHistory,
  restoreRevision,
  downloadPreview,
  downloadApplied,
}

/** 画面が使う API の関数の集まりの型 */
export type DslApi = typeof dslApi
