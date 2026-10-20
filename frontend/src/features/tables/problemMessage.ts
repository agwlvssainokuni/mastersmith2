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
// 読み取りの失敗の文と、定義の読み直しの判定（Intent 261016-business-data-views の U3 の L5、D4・D6、機能設計の 6.1、
// 計画 4.6、U3-NFR6.3・8.7）。
// - 文は code と reason だけで選ぶ。DB の文・制約の名前・入力した値・主キーの値は出さない。
// - 知らない code は、状態コードの区分（4xx・5xx）ごとの決まった文に写す。通信の失敗は「読み込めませんでした」。
// - needsDefinitionReload: definitionChanged が true か、fieldErrors の field が今の定義の項目に無ければ true（F6 を行う）。
//   field が空の文字の誤りは画面に結べない誤りで、「今の定義に無い」の判定から外す（U2 の G6・U1 の NOT_ALLOWED）。
import type { BusinessProblem, TableDefinition } from './businessDataTypes'

/** 読み取りの失敗の種類（S6 の表示と一覧の上の文） */
export type ReadProblemKind =
  | 'forbidden'
  | 'table-not-found'
  | 'row-not-found'
  | 'dsl-not-applied'
  | 'unavailable'
  | 'mismatch'
  | 'not-unique'
  | 'payload-too-large'
  | 'bad-request'
  | 'unknown-client'
  | 'unknown-server'
  | 'network'

/** code ごとの読み取りの失敗の種類 */
const READ_CODE_KINDS: Readonly<Record<string, ReadProblemKind>> = {
  ACCESS_DENIED: 'forbidden',
  BUSINESS_TABLE_NOT_FOUND: 'table-not-found',
  BUSINESS_ROW_NOT_FOUND: 'row-not-found',
  BUSINESS_DSL_NOT_APPLIED: 'dsl-not-applied',
  BUSINESS_TARGET_DB_UNAVAILABLE: 'unavailable',
  BUSINESS_TARGET_DB_TIMEOUT: 'unavailable',
  BUSINESS_TARGET_DB_MISMATCH: 'mismatch',
  BUSINESS_ROW_OPERATION_NOT_ALLOWED: 'not-unique',
  BUSINESS_ROW_NOT_UNIQUE: 'not-unique',
  PAYLOAD_TOO_LARGE: 'payload-too-large',
  VALIDATION_FAILED: 'bad-request',
  MALFORMED_REQUEST: 'bad-request',
}

/**
 * 読み取りの失敗の種類を決める。
 *
 * @param problem 業務データの誤り（通信の失敗は undefined）
 */
export function readProblemKind(problem: BusinessProblem | undefined): ReadProblemKind {
  if (problem === undefined) {
    return 'network'
  }
  if (problem.code !== undefined && Object.hasOwn(READ_CODE_KINDS, problem.code)) {
    return READ_CODE_KINDS[problem.code]
  }
  if (problem.status === 403) {
    return 'forbidden'
  }
  if (problem.status === 413) {
    return 'payload-too-large'
  }
  return problem.status >= 500 ? 'unknown-server' : 'unknown-client'
}

/** 読み取りの失敗の種類ごとの文言の鍵 */
const READ_KIND_KEYS: Readonly<Record<ReadProblemKind, string>> = {
  forbidden: 'tables.forbidden',
  'table-not-found': 'tables.problem.tableNotFound',
  'row-not-found': 'tables.problem.rowNotFound',
  'dsl-not-applied': 'tables.problem.dslNotApplied',
  unavailable: 'tables.problem.unavailable',
  mismatch: 'tables.problem.mismatch',
  'not-unique': 'tables.problem.notUnique',
  'payload-too-large': 'tables.problem.payloadTooLarge',
  'bad-request': 'tables.problem.badRequest',
  'unknown-client': 'tables.problem.unknownClient',
  'unknown-server': 'tables.problem.unknownServer',
  network: 'tables.loadError',
}

/** 読み取りの失敗の文言の鍵 */
export function readProblemMessageKey(kind: ReadProblemKind): string {
  return READ_KIND_KEYS[kind]
}

/** 文を用意している fieldErrors の reason（backend の BusinessDataFieldErrorReason） */
export const FIELD_ERROR_REASONS = [
  'NOT_ALLOWED',
  'INVALID_VALUE',
  'RANGE_REVERSED',
  'NOT_A_CHOICE',
  'KEY_MISMATCH',
  'REQUIRED',
  'TOO_SHORT',
  'TOO_LONG',
  'OUT_OF_RANGE',
  'TOO_MANY_DIGITS',
  'PATTERN_MISMATCH',
  'PATTERN_TIMEOUT',
  'DUPLICATE',
  'REFERENCE_NOT_FOUND',
  'NOT_NULL',
  'NOT_EDITABLE_NOT_NULL',
  'NO_CHANGE',
] as const

/** fieldErrors の reason の文言の鍵（知らない reason は INVALID_VALUE の文） */
export function fieldErrorMessageKey(reason: string): string {
  const known = (FIELD_ERROR_REASONS as readonly string[]).includes(reason)
  return `tables.error.${known ? reason : 'INVALID_VALUE'}`
}

/** 今の定義が知っている項目の名前（検索の項目・カラム・並べ替え・主キー） */
function knownFields(definition: TableDefinition): Set<string> {
  return new Set([
    ...definition.searchFields.map((field) => field.column),
    ...definition.listColumns.map((column) => column.column),
    ...definition.detailColumns.map((column) => column.column),
    ...definition.sortableColumns,
    ...definition.primaryKeyColumns,
  ])
}

/**
 * 定義を読み直す（F6）べき誤りか。
 *
 * @param problem 業務データの誤り
 * @param definition 今の定義
 */
export function needsDefinitionReload(
  problem: BusinessProblem | undefined,
  definition: TableDefinition,
): boolean {
  if (problem === undefined) {
    return false
  }
  if (problem.definitionChanged || problem.code === 'BUSINESS_DEFINITION_CHANGED') {
    return true
  }
  const known = knownFields(definition)
  return problem.fieldErrors.some((error) => error.field !== '' && !known.has(error.field))
}

/** 書き込みの失敗（保存されていない）の種類（計画 4.6 の表、機能設計の 6.1） */
export type WriteProblemKind =
  | 'forbidden'
  | 'table-not-found'
  | 'row-not-found'
  | 'dsl-not-applied'
  | 'unavailable'
  | 'mismatch'
  | 'not-unique'
  | 'write-not-configured'
  | 'definition-changed'
  | 'duplicate'
  | 'reference-not-found'
  | 'save-rejected'
  | 'validation'
  | 'payload-too-large'

/** code ごとの書き込みの失敗の種類（writeOutcome の NOT_SAVED_CODES と同じ code） */
const WRITE_CODE_KINDS: Readonly<Record<string, WriteProblemKind>> = {
  ACCESS_DENIED: 'forbidden',
  BUSINESS_TABLE_NOT_FOUND: 'table-not-found',
  BUSINESS_ROW_NOT_FOUND: 'row-not-found',
  BUSINESS_DSL_NOT_APPLIED: 'dsl-not-applied',
  BUSINESS_TARGET_DB_UNAVAILABLE: 'unavailable',
  BUSINESS_TARGET_DB_MISMATCH: 'mismatch',
  BUSINESS_ROW_OPERATION_NOT_ALLOWED: 'not-unique',
  BUSINESS_ROW_NOT_UNIQUE: 'not-unique',
  BUSINESS_WRITE_NOT_CONFIGURED: 'write-not-configured',
  BUSINESS_DEFINITION_CHANGED: 'definition-changed',
  BUSINESS_DUPLICATE_VALUE: 'duplicate',
  BUSINESS_REFERENCE_NOT_FOUND: 'reference-not-found',
  BUSINESS_SAVE_REJECTED: 'save-rejected',
  VALIDATION_FAILED: 'validation',
  PAYLOAD_TOO_LARGE: 'payload-too-large',
}

/**
 * 書き込みの失敗（writeOutcome で「保存されていない」に分けたもの）の種類を決める。
 *
 * @param problem 業務データの誤り（code は NOT_SAVED_CODES のどれか）
 */
export function writeProblemKind(problem: BusinessProblem): WriteProblemKind {
  if (problem.code !== undefined && Object.hasOwn(WRITE_CODE_KINDS, problem.code)) {
    return WRITE_CODE_KINDS[problem.code]
  }
  // NOT_SAVED_CODES の外は writeOutcome が「分からない」に倒すため、ここには来ない。来たら最も弱い文にする。
  return 'save-rejected'
}

/** 書き込みの操作（文の出し分けに使う） */
export type WriteMessageOperation = 'save' | 'delete'

/**
 * 書き込みの失敗の文言の鍵（フォームの上・削除の Modal の中に出す文）。
 * validation・definition-changed は項目ごとの文と F6 で扱い、フォームの上の文は補いに使う。
 *
 * @param kind 書き込みの失敗の種類
 * @param operation 保存（作成・更新）か削除か
 */
export function writeProblemMessageKey(
  kind: WriteProblemKind,
  operation: WriteMessageOperation,
): string {
  switch (kind) {
    case 'forbidden':
      return operation === 'delete'
        ? 'tables.problem.writeForbiddenDelete'
        : 'tables.problem.writeForbidden'
    case 'unavailable':
      return operation === 'delete' ? 'tables.write.notDeletedRetry' : 'tables.write.notSavedRetry'
    case 'save-rejected':
      return operation === 'delete' ? 'tables.write.deleteReferenced' : 'tables.write.saveRejected'
    case 'duplicate':
      return 'tables.error.DUPLICATE'
    case 'reference-not-found':
      return 'tables.error.REFERENCE_NOT_FOUND'
    case 'write-not-configured':
      return 'tables.writeNotConfigured'
    case 'definition-changed':
      return 'tables.definitionChanged'
    case 'validation':
      return 'tables.form.checkInputs'
    case 'payload-too-large':
      return 'tables.problem.payloadTooLarge'
    case 'table-not-found':
      return 'tables.problem.tableNotFound'
    case 'row-not-found':
      return 'tables.problem.rowNotFound'
    case 'dsl-not-applied':
      return 'tables.problem.dslNotApplied'
    case 'mismatch':
      return 'tables.problem.mismatch'
    case 'not-unique':
      return 'tables.problem.notUnique'
  }
}
