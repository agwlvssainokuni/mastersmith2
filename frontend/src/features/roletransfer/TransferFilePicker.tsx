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
// ファイルの選択と大きさの案内（Intent 261004-role-menu の U6 の D23、functional-spec.md の W7.2、frontend-components.md
// の 5節、AC3.1.7）。ネイティブの input type="file"（accept=".yaml,.yml"）を見える label で包む。選んだファイルの名前と
// 大きさを文字で出し、上限を超えたときは「ファイルが大きすぎます（上限 10 MiB）」を出す（上限の値は定数から）。
// 大きさの判定は呼ぶ側（useRoleTransfer）が行い、この部品はファイルを渡すだけ。名前は React の文字として描く。
import { useId } from 'react'
import type { FileInfo } from './useRoleTransfer'
import { MAX_TRANSFER_MIB } from './transferLimits'
import { useRoleTransferText } from './useRoleTransferText'
import './RoleTransferPage.css'

export interface TransferFilePickerProps {
  /** 選んだファイル（無ければ null） */
  fileInfo: FileInfo | null
  /** 上限を超えた */
  tooLarge: boolean
  /** 選べない（確かめ・適用の間） */
  disabled: boolean
  /** ファイルを選んだ（取り消しは null） */
  onSelect: (file: File | null) => void
}

/** ファイルの選択 */
export function TransferFilePicker({
  fileInfo,
  tooLarge,
  disabled,
  onSelect,
}: TransferFilePickerProps) {
  const t = useRoleTransferText()
  const inputId = useId()
  const statusId = useId()
  return (
    <div className="roletransfer-picker" data-testid="roletransfer-picker">
      <label htmlFor={inputId} className="roletransfer-picker-label">
        {t('roletransfer.file.choose')}
      </label>
      <input
        id={inputId}
        type="file"
        accept=".yaml,.yml"
        className="roletransfer-picker-input"
        disabled={disabled}
        aria-describedby={statusId}
        onChange={(event) => onSelect(event.currentTarget.files?.item(0) ?? null)}
        data-testid="roletransfer-file-input"
      />
      <p id={statusId} className="roletransfer-text" data-testid="roletransfer-file-status">
        {fileInfo === null
          ? t('roletransfer.file.none')
          : t('roletransfer.file.selected', { name: fileInfo.name, size: fileInfo.size })}
      </p>
      {tooLarge && (
        <p
          role="alert"
          className="roletransfer-error-text"
          data-testid="roletransfer-file-too-large"
        >
          {t('roletransfer.file.tooLarge', { limit: MAX_TRANSFER_MIB })}
        </p>
      )}
    </div>
  )
}
