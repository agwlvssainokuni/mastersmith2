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
// テストの値（契約 C6 の形）を組み立てる（テストからだけ使う）。
import type {
  DslErrorItem,
  DslErrorReport,
  DslStatus,
  HistoryEntry,
  Preview,
  PreviewRef,
  TableDiff,
} from '../api/types'

/** 64 文字の識別を作る。 */
export function hashOf(prefix: string): string {
  return (prefix + '0'.repeat(64)).slice(0, 64)
}

/** プレビューの参照 */
export function previewRef(overrides: Partial<PreviewRef> = {}): PreviewRef {
  return {
    previewId: 'preview-1',
    dslHash: hashOf('8b02d4'),
    source: 'UPLOAD',
    by: { userId: '2', email: 'hanako@example.com' },
    at: '2026-09-24T02:02:00Z',
    ...overrides,
  }
}

/** 今の状態 */
export function statusOf(overrides: Partial<DslStatus> = {}): DslStatus {
  return {
    applied: {
      revisionId: 'revision-1',
      dslHash: hashOf('3f9a1c'),
      source: 'GENERATED',
      by: { userId: '1', email: 'taro@example.com' },
      at: '2026-09-24T01:15:00Z',
    },
    preview: previewRef(),
    ...overrides,
  }
}

/** 違いのあるテーブル3つと変わらないテーブル1つ */
export function sampleTables(): TableDiff[] {
  return [
    {
      name: 'dept_mst',
      change: 'CHANGED',
      columns: [
        { name: 'dept_kana', change: 'ADDED', changedItems: [] },
        { name: 'fax_no', change: 'REMOVED', changedItems: [] },
        { name: 'dept_name', change: 'CHANGED', changedItems: ['label.ja', 'dbType.length'] },
      ],
    },
    {
      name: 'item_mst',
      change: 'ADDED',
      columns: [{ name: 'item_code', change: 'ADDED', changedItems: [] }],
    },
    {
      name: 'old_code',
      change: 'REMOVED',
      columns: [{ name: 'code', change: 'REMOVED', changedItems: [] }],
    },
    { name: 'same_mst', change: 'UNCHANGED', columns: [] },
  ]
}

/** プレビューの中身 */
export function previewOf(overrides: Partial<Preview> = {}): Preview {
  return {
    ...previewRef(),
    summary: {
      tableCount: 42,
      viewCount: 3,
      columnCount: 1318,
      menuTree: [
        {
          label: { ja: '基本マスタ', en: 'Basic masters' },
          table: null,
          children: [
            { label: { ja: '部署', en: 'Departments' }, table: 'dept_mst', children: [] },
            { label: { ja: '品目', en: 'Items' }, table: 'item_mst', children: [] },
          ],
        },
      ],
      missingDisplayNames: [{ path: 'tables.dept_mst.columns.fax_no.label', language: 'en' }],
      missingDisplayNameTotal: 1,
    },
    diff: { appliedExists: true, tables: sampleTables() },
    warnings: [
      { kind: 'COLUMN_MISSING', path: 'dept_mst.remarks', message: '対象DB にありません' },
      {
        kind: 'TYPE_MISMATCH',
        path: 'item_mst.price',
        message: '型が違います（DSL: 数値 / DB: 文字列）',
      },
    ],
    ...overrides,
  }
}

/** 誤りを n 件作る。 */
export function errorItems(n: number): DslErrorItem[] {
  return Array.from({ length: n }, (_, index) => ({
    kind: 'SEMANTIC' as const,
    line: index + 1,
    column: 3,
    path: `tables.t${index}`,
    message: `誤り ${index + 1}`,
  }))
}

/** 誤りの一覧 */
export function errorReport(total: number, shown = Math.min(total, 100)): DslErrorReport {
  return { total, errors: errorItems(shown) }
}

/** 履歴 */
export function historyOf(): HistoryEntry[] {
  return [
    {
      revisionId: 'revision-3',
      dslHash: hashOf('3f9a1c'),
      source: 'GENERATED',
      by: { userId: '1', email: 'taro@example.com' },
      at: '2026-09-24T01:15:00Z',
      current: true,
    },
    {
      revisionId: 'revision-2',
      dslHash: hashOf('77e0b2'),
      source: 'UPLOAD',
      by: { userId: '2', email: null },
      at: '2026-09-20T07:40:00Z',
      current: false,
    },
  ]
}
