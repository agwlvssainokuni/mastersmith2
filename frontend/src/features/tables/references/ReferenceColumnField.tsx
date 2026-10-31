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
// 作成・更新の画面のカラムごとの部品の選び分け（Intent 261029-reference-lookup-cleanup の U3、D5、X-4）。
// chooseFieldKind の結果で、参照の3つの部品・既存の入力の部品・既存の読み取りの表示のどれを描くかを選ぶ薄い部品。
// 既存の入力の部品と読み取りの表示は、作成・更新の画面が描く関数で受ける（振る舞いを変えない）。
// テストの印は位置の番号（c<index>）だけで作る（D34）。
import type { ReactNode } from 'react'
import type { ColumnDefinition, ReferenceSource } from '../businessDataTypes'
import type { FormMode } from '../formDiff'
import { LookupField } from './LookupField'
import { ReadonlyReferenceField } from './ReadonlyReferenceField'
import { ReferenceSelectField } from './ReferenceSelectField'
import { chooseFieldKind, isBlockedRequired, isRequiredColumn } from './referenceField'
import type { SearchReference } from './useLookupSearch'

export interface ReferenceColumnFieldProps {
  column: ColumnDefinition
  index: number
  /** 項目名（日英の表示名） */
  name: string
  mode: FormMode
  /** 更新で先頭だけを受けたカラム */
  truncated: readonly string[]
  /** 参照元のスキーマ・テーブル（カラムの名前を足して参照先の API に送る） */
  table: { schemaName: string; tableName: string }
  value: string
  /** 開いたときの値 */
  initialValue: string
  error: string | undefined
  /** 保存中 */
  disabled: boolean
  /** LOOKUP の欄のラベル（選んだ行か、読めたラベル） */
  lookupLabel: string | null
  lookupLabelLoading: boolean
  onChange: (value: string, label?: string | null) => void
  onBlur: () => void
  /** 既存の入力の部品（参照でないカラム） */
  renderPlain: () => ReactNode
  /** 既存の読み取りの表示（更新で入力できないカラム） */
  renderLocked: () => ReactNode
  /** 検索の関数（テストで差し替える） */
  search?: SearchReference
}

/** カラムごとの部品の選び分け */
export function ReferenceColumnField({
  column,
  index,
  name,
  mode,
  truncated,
  table,
  value,
  initialValue,
  error,
  disabled,
  lookupLabel,
  lookupLabelLoading,
  onChange,
  onBlur,
  renderPlain,
  renderLocked,
  search,
}: ReferenceColumnFieldProps) {
  const kind = chooseFieldKind(column, mode, truncated)
  if (kind.kind === 'hidden') {
    return null
  }
  if (kind.kind === 'locked') {
    return renderLocked()
  }
  let content: ReactNode
  switch (kind.kind) {
    case 'plain':
      content = renderPlain()
      break
    case 'reference-readonly':
      content = (
        <ReadonlyReferenceField
          index={index}
          name={name}
          value={initialValue}
          reason={kind.reason}
          blockedRequired={isBlockedRequired(column, mode, truncated)}
          error={error}
        />
      )
      break
    case 'reference-select':
      content = (
        <ReferenceSelectField
          column={column}
          index={index}
          name={name}
          mode={mode}
          value={value}
          initialValue={initialValue}
          error={error}
          disabled={disabled}
          onChange={(next) => onChange(next)}
          onBlur={onBlur}
        />
      )
      break
    case 'lookup': {
      const picker = column.reference?.picker ?? null
      content =
        picker === null ? null : (
          <LookupField
            index={index}
            name={name}
            picker={picker}
            source={
              {
                schemaName: table.schemaName,
                tableName: table.tableName,
                column: column.column,
              } satisfies ReferenceSource
            }
            value={value}
            label={lookupLabel}
            labelLoading={lookupLabelLoading}
            clearable={!isRequiredColumn(column)}
            error={error}
            disabled={disabled}
            onChange={(next, label) => onChange(next, label)}
            {...(search === undefined ? {} : { search })}
          />
        )
      break
    }
  }
  return (
    <div className="tables-form-field" data-testid={`tables-form-field-c${index}`}>
      {content}
    </div>
  )
}
