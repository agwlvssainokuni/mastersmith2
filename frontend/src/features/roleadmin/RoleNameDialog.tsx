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
// ロールの作成・名前の変更の Modal（Intent 261004-role-menu の U6 の D7・D4・D3、functional-spec.md の 4.2・W3.3・W3.4、
// frontend-components.md の 2.2）。名前を1つ入れる。画面でも validateAdminName（前後の空白を除いて 1〜64 コードポイント、
// 制御文字なし）で確かめ、誤りを入力欄の下に出して aria-describedby で結ぶ（FormField が結ぶ）。誤りが無いときは残りの
// 文字数を入力欄の下に出す。判定の正はサーバーで、サーバーの拒否（重なり・同じ名前・入力の誤り）も入力欄の下に出す。
// BUSY とほかの失敗は Modal の中の Alert に出し、入力は残す。背景のクリックで閉じず、送信中は閉じない。
// groupadmin にも同じ形を持つ（機能どうしの import の禁止）。
import { Alert, Button, FormField, Modal, TextInput } from 'make-you-chic-ui'
import { useRef, useState, type FormEvent, type RefObject } from 'react'
import {
  remainingAdminNameLength,
  validateAdminName,
} from '../../shared/validation/validateAdminName'
import { useRoleAdminText } from './useRoleAdminText'
import './RoleListPage.css'

export interface RoleNameDialogProps {
  /** 作成か名前の変更か */
  mode: 'create' | 'rename'
  /** 名前の変更のときの今の名前 */
  initialName?: string
  /** 送信中 */
  submitting: boolean
  /** 送信から 5 秒を過ぎた */
  slow: boolean
  /** サーバーの拒否（入力欄の下に出す文言の鍵） */
  serverProblem?: string
  /** BUSY・ほかの失敗（Modal の中に出す文言の鍵） */
  dialogMessage?: string
  /** 画面の検査を通った名前を送る */
  onSubmit: (name: string) => void
  /** 入力が変わった（サーバーの拒否の文言を消す） */
  onChange?: () => void
  onCancel: () => void
  /** 閉じた後にフォーカスを戻す先 */
  finalFocusRef?: RefObject<HTMLElement | null>
}

/** ロールの作成・名前の変更の Modal */
export function RoleNameDialog({
  mode,
  initialName = '',
  submitting,
  slow,
  serverProblem,
  dialogMessage,
  onSubmit,
  onChange,
  onCancel,
  finalFocusRef,
}: RoleNameDialogProps) {
  const t = useRoleAdminText()
  const inputRef = useRef<HTMLInputElement>(null)
  const [name, setName] = useState(initialName)
  const [touched, setTouched] = useState(false)
  const problem = validateAdminName(name)
  const remaining = remainingAdminNameLength(name)

  // 画面の検査の誤りは、入力を始めた後か送ろうとした後に出す（開いた直後の空の欄に誤りを出さない）。
  const localError = touched && problem !== undefined ? t(`roleadmin.name.${problem}`) : undefined
  const error = localError ?? (serverProblem !== undefined ? t(serverProblem) : undefined)
  const helper =
    remaining >= 0
      ? t('roleadmin.name.remaining', { count: remaining })
      : t('roleadmin.name.over', { count: -remaining })

  function handleClose(): void {
    if (!submitting) {
      onCancel()
    }
  }

  function handleChange(value: string): void {
    setName(value)
    setTouched(true)
    onChange?.()
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault()
    setTouched(true)
    if (submitting || problem !== undefined) {
      inputRef.current?.focus()
      return
    }
    onSubmit(name)
  }

  return (
    <Modal
      open
      title={t(`roleadmin.dialog.${mode}.title`)}
      onClose={handleClose}
      initialFocusRef={inputRef}
      finalFocusRef={finalFocusRef}
      closeLabel={t('roleadmin.action.close')}
      closeOnBackdropClick={false}
    >
      <form
        className="roleadmin-dialog"
        noValidate
        onSubmit={handleSubmit}
        data-testid="roleadmin-name-dialog"
      >
        {dialogMessage !== undefined && (
          <div data-testid="roleadmin-name-alert">
            <Alert variant="danger">{t(dialogMessage)}</Alert>
          </div>
        )}
        <FormField label={t('roleadmin.name.label')} error={error} helperText={helper}>
          <TextInput
            ref={inputRef}
            autoComplete="off"
            aria-required="true"
            value={name}
            onChange={handleChange}
            readOnly={submitting}
            data-testid="roleadmin-name-input"
          />
        </FormField>
        {submitting && slow && (
          <p role="status" className="roleadmin-dialog-slow" data-testid="roleadmin-name-slow">
            {t('roleadmin.slow')}
          </p>
        )}
        <div className="roleadmin-dialog-actions">
          <Button
            variant="secondary"
            disabled={submitting}
            onClick={handleClose}
            data-testid="roleadmin-name-cancel"
          >
            {t('roleadmin.action.cancel')}
          </Button>
          <Button
            type="submit"
            variant="primary"
            loading={submitting}
            data-testid="roleadmin-name-submit"
          >
            {t(submitting ? 'roleadmin.action.processing' : `roleadmin.dialog.${mode}.submit`)}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
