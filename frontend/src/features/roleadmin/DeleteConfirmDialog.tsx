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
// ロールの削除の確かめ（Intent 261004-role-menu の U6 の D8・D4・D3、functional-spec.md の W3.5）。make-you-chic-ui の
// Modal（role="alertdialog"、背景のクリックで閉じない、はじめのフォーカスは「やめる」、閉じたら開いた所へ戻す）。
// 「やめる」では何も送らない（AC1.1.7）。送信中は閉じず、BUSY とほかの失敗は Modal の中に出して開いたままにする。
// groupadmin にも同じ形を持つ（機能どうしの import の禁止）。
import { Alert, Button, Modal } from 'make-you-chic-ui'
import { useRef, type RefObject } from 'react'
import { useRoleAdminText } from './useRoleAdminText'
import './RoleListPage.css'

export interface DeleteConfirmDialogProps {
  /** 削除するロールの名前 */
  name: string
  /** 送信中 */
  submitting: boolean
  /** 送信から 5 秒を過ぎた */
  slow: boolean
  /** BUSY・ほかの失敗（Modal の中に出す文言の鍵） */
  dialogMessage?: string
  onConfirm: () => void
  onCancel: () => void
  /** 閉じた後にフォーカスを戻す先 */
  finalFocusRef?: RefObject<HTMLElement | null>
}

/** ロールの削除の確かめ */
export function DeleteConfirmDialog({
  name,
  submitting,
  slow,
  dialogMessage,
  onConfirm,
  onCancel,
  finalFocusRef,
}: DeleteConfirmDialogProps) {
  const t = useRoleAdminText()
  const cancelRef = useRef<HTMLButtonElement>(null)

  function handleClose(): void {
    if (!submitting) {
      onCancel()
    }
  }

  return (
    <Modal
      open
      role="alertdialog"
      title={t('roleadmin.delete.title')}
      onClose={handleClose}
      initialFocusRef={cancelRef}
      finalFocusRef={finalFocusRef}
      closeLabel={t('roleadmin.action.close')}
      closeOnBackdropClick={false}
    >
      <div className="roleadmin-dialog" data-testid="roleadmin-delete-dialog">
        <p className="roleadmin-dialog-text" data-testid="roleadmin-delete-text">
          {t('roleadmin.delete.confirm', { name })}
        </p>
        {dialogMessage !== undefined && (
          <div data-testid="roleadmin-delete-alert">
            <Alert variant="danger">{t(dialogMessage)}</Alert>
          </div>
        )}
        {submitting && slow && (
          <p role="status" className="roleadmin-dialog-slow" data-testid="roleadmin-delete-slow">
            {t('roleadmin.slow')}
          </p>
        )}
        <div className="roleadmin-dialog-actions">
          <Button
            ref={cancelRef}
            variant="secondary"
            disabled={submitting}
            onClick={handleClose}
            data-testid="roleadmin-delete-cancel"
          >
            {t('roleadmin.action.cancel')}
          </Button>
          <Button
            variant="danger"
            loading={submitting}
            onClick={() => {
              if (!submitting) {
                onConfirm()
              }
            }}
            data-testid="roleadmin-delete-submit"
          >
            {t(submitting ? 'roleadmin.action.processing' : 'roleadmin.delete.submit')}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
