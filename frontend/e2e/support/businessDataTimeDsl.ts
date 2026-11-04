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
// 業務データの日時の代表の流れ（200）の専用の DSL（Intent 261102-timezone-ui-polish の U4、計画 Step 14、security-design.md 2.8、
// NFR 設計の Q2: Looks correct の 6）。
// - 共有の businessDataDsl.ts（180・190・195）は変えない。この DSL は実行のスキーマの表 e2e_event だけとそのメニューを持つ。
//   カラムは businessDataSchema.sql と同じ。
// - 版の印は E2ET_（表・メニューの表示名は「E2ET …」で始め、ロールの名前は E2ET_ROLE_<印>）。180 の「E2EB …」・190 と 195 の
//   「E2ER …」と重ならない。
// - 200 は流れの中でこの DSL を投入・適用し、後始末はしない（適用した DSL が残る）。200 より後のファイルは適用中の DSL に頼らない。

/** 日時の表の名前（businessDataSchema.sql と同じ） */
export const EVENT_TABLE = 'e2e_event'

/** 日時の表のカラムの名前（businessDataSchema.sql と同じ） */
export const EVENT_COLUMNS = {
  id: 'id',
  name: 'name',
  occurredAt: 'occurred_at',
} as const

/** 日時の表のカラムの表示名（日本語。画面の欄の名前に使う） */
export const EVENT_COLUMN_LABELS = {
  id: '出来事の番号',
  name: '名前',
  occurredAt: '発生の時刻',
} as const

/** 表とメニューの表示名（版の印 E2ET を入れる） */
export interface BusinessDataTimeLabels {
  table: string
  menu: string
}

/**
 * 表とメニューの表示名を作る（「E2ET …」で始める）。
 *
 * @param tag ファイルごとの印
 */
export function businessDataTimeLabels(tag: string): BusinessDataTimeLabels {
  return { table: `E2ET 出来事 ${tag}`, menu: `E2ET 出来事のメニュー ${tag}` }
}

/**
 * 版 2 の DSL（実行のスキーマの日時の表とメニュー）。
 *
 * @param schema 実行のスキーマ（./gradlew e2eTest が渡す E2E_BIZ_SCHEMA）
 * @param labels 表とメニューの表示名
 */
export function businessDataTimeDsl(schema: string, labels: BusinessDataTimeLabels): string {
  const quoted = (value: string) => JSON.stringify(value)
  return `version: 2
menus:
  - label: { ja: ${quoted(labels.menu)}, en: ${quoted(labels.menu)} }
    table: { schema: ${schema}, name: ${EVENT_TABLE} }
schemas:
  ${schema}:
    label: { ja: ${quoted(schema)}, en: ${quoted(schema)} }
    tables:
      ${EVENT_TABLE}:
        label: { ja: ${quoted(labels.table)}, en: ${quoted(labels.table)} }
        view: false
        primaryKey: [${EVENT_COLUMNS.id}]
        foreignKeys: []
        columns:
          ${EVENT_COLUMNS.id}:
            label: { ja: ${EVENT_COLUMN_LABELS.id}, en: Event code }
            dbType: { name: varchar, length: 20, precision: null, scale: null, nullable: false }
            formPart: text
            search: { enabled: false, collapsed: false }
            list: { visible: true, order: 1, sortable: true }
            detail: { visible: true }
            validations:
              - { type: required, origin: DB }
              - { type: maxLength, value: 20, origin: DB }
          ${EVENT_COLUMNS.name}:
            label: { ja: ${EVENT_COLUMN_LABELS.name}, en: Name }
            dbType: { name: varchar, length: 50, precision: null, scale: null, nullable: false }
            formPart: text
            search: { enabled: false, collapsed: false }
            list: { visible: true, order: 2, sortable: true }
            detail: { visible: true }
            validations:
              - { type: required, origin: DB }
              - { type: maxLength, value: 50, origin: DB }
          ${EVENT_COLUMNS.occurredAt}:
            label: { ja: ${EVENT_COLUMN_LABELS.occurredAt}, en: Occurred at }
            dbType: { name: timestamptz, length: null, precision: 6, scale: null, nullable: true }
            formPart: datetime
            search: { enabled: false, collapsed: false }
            list: { visible: true, order: 3, sortable: true }
            detail: { visible: true }
            validations: []
`
}
