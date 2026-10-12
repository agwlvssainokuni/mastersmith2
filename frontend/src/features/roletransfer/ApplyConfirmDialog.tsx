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
// 適用の確かめ（Intent 261004-role-menu の U6 の D25・D8、functional-spec.md の W7.4、AC3.1.3・AC3.1.10）。make-you-chic-ui
// の Modal（role="alertdialog"、背景のクリックで閉じない、はじめのフォーカスは「やめる」、閉じたら「適用する」へ戻す）。
// 「やめる」では何も送らない。送信中は閉じず、5 秒を過ぎたら待ちの案内を出す。
import { Button, Modal } from 'make-you-chic-ui'
import { useRef, type RefObject } from 'react'
import { useRoleTransferText } from './useRoleTransferText'
import './RoleTransferPage.css'

export interface ApplyConfirmDialogProps {
  /** 置き換え・作成するロールの数 */
  roleCount: number
  /** 置き換えるロールで設定なしに戻る件数 */
  cleared: number
  /** 送信中 */
  submitting: boolean
  /** 送信から 5 秒を過ぎた */
  slow: boolean
  onConfirm: () => void
  onCancel: () => void
  /** 閉じた後にフォーカスを戻す先 */
  finalFocusRef?: RefObject<HTMLElement | null>
}

/** 適用の確かめ */
export function ApplyConfirmDialog({
  roleCount,
  cleared,
  submitting,
  slow,
  onConfirm,
  onCancel,
  finalFocusRef,
}: ApplyConfirmDialogProps) {
  const t = useRoleTransferText()
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
      title={t('roletransfer.apply.title')}
      onClose={handleClose}
      initialFocusRef={cancelRef}
      finalFocusRef={finalFocusRef}
      closeLabel={t('roletransfer.action.close')}
      closeOnBackdropClick={false}
    >
      <div className="roletransfer-dialog" data-testid="roletransfer-apply-dialog">
        <p className="roletransfer-text">
          {t('roletransfer.apply.confirm', { count: roleCount, cleared })}
        </p>
        {submitting && slow && (
          <p role="status" className="roletransfer-text" data-testid="roletransfer-apply-slow">
            {t('roletransfer.slow.apply')}
          </p>
        )}
        <div className="roletransfer-actions">
          <Button
            ref={cancelRef}
            variant="secondary"
            disabled={submitting}
            onClick={handleClose}
            data-testid="roletransfer-apply-cancel"
          >
            {t('roletransfer.action.cancel')}
          </Button>
          <Button
            variant="primary"
            loading={submitting}
            onClick={() => {
              if (!submitting) {
                onConfirm()
              }
            }}
            data-testid="roletransfer-apply-submit"
          >
            {t(submitting ? 'roletransfer.action.processing' : 'roletransfer.apply.submit')}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
