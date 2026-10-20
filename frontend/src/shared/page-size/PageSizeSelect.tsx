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
// 1ページの件数の選択（Intent 261016-business-data-views の U3 の L8、D11・D27、AC1.3.2）。
// - make-you-chic-ui の Select（ネイティブの select）を FormField で名前に結ぶ。表（Table）の外に置く。
// - make-you-chic-ui の Table に件数の選択が無いため、取り込み（依頼（5））までの手当て。取り込まれたら B7 で置き換える。
// - src/shared/ は app/・features/ を読まない。名前と選択肢の文は呼ぶ側が渡す。
import { FormField, Select } from 'make-you-chic-ui'
import './PageSizeSelect.css'

export interface PageSizeSelectProps<T extends number> {
  /** 今の件数 */
  value: T
  /** 選べる件数（並べる順） */
  options: readonly T[]
  /** 件数を選んだ */
  onChange: (value: T) => void
  /** 選択の名前（例「1ページの件数」） */
  label: string
  /** 押せなくする */
  disabled?: boolean
}

/** 1ページの件数の選択 */
export function PageSizeSelect<T extends number>({
  value,
  options,
  onChange,
  label,
  disabled,
}: PageSizeSelectProps<T>) {
  return (
    <FormField label={label} className="shared-page-size">
      <Select
        options={options.map((option) => ({ label: String(option), value: String(option) }))}
        value={String(value)}
        disabled={disabled}
        data-testid="page-size-select"
        onChange={(next) => {
          const chosen = options.find((option) => String(option) === next)
          if (chosen !== undefined && chosen !== value) {
            onChange(chosen)
          }
        }}
      />
    </FormField>
  )
}
