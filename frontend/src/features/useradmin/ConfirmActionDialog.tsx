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
// 確かめの表示（S3、5種類。functional-spec.md の 4.2・W5・D7・D13・D14・7.3、frontend-components.md の 4節、
// AC2.1.8・AC3.1.7・AC4.1.9）。make-you-chic-ui の Modal（role="alertdialog"、背景のクリックで閉じない、閉じるボタンの
// 名前は画面の言語、はじめのフォーカスは「やめる」）。aria-labelledby・aria-describedby は Modal が付ける（説明は本文の
// 全体）ため付け直さず、本文の先頭に対象と効き目の文を置く（機能設計 10節の (k)）。
// 送信中は Escape・[×]・「やめる」で閉じず、両方のボタンを押せない（実行は Button の loading で「処理中」、フォーカスを保つ）。
// 送信から 5 秒を過ぎたら「時間がかかっています」を出す。「印を外す」「止める」は危険の見た目。
import { Button, Modal } from 'make-you-chic-ui'
import { useRef, type RefObject } from 'react'
import type { ConfirmState } from './useUserAdmin'
import { useUserAdminText } from './useUserAdminText'
import './EditProfileDialog.css'

export interface ConfirmActionDialogProps {
  /** 確かめの表示の状態（閉じているときは null） */
  state: ConfirmState | null
  /** 送信から 5 秒を過ぎた */
  slow: boolean
  onConfirm: () => void
  onCancel: () => void
  /**
   * 閉じた後にフォーカスを戻す先（行の「操作」のボタンなど）。Dropdown の項目から開くと、開く前にフォーカスのあった
   * 項目はメニューごと消えるため、Modal の既定の戻し先が無くなり body に落ちる。開いた元を指す ref を渡す（FR1.2）。
   */
  finalFocusRef?: RefObject<HTMLElement | null>
}

/** 危険の見た目にする操作 */
const DANGER_ACTIONS: ReadonlySet<string> = new Set(['revokeAdmin', 'suspend'])

/** 確かめの表示 */
export function ConfirmActionDialog({
  state,
  slow,
  onConfirm,
  onCancel,
  finalFocusRef,
}: ConfirmActionDialogProps) {
  const t = useUserAdminText()
  const cancelRef = useRef<HTMLButtonElement>(null)

  if (state === null) {
    return null
  }
  const { action, user, submitting } = state
  const values = { name: user.displayName, email: user.email }

  function handleClose(): void {
    if (!submitting) {
      onCancel()
    }
  }

  return (
    <Modal
      open
      role="alertdialog"
      title={t(`useradmin.confirm.${action}.title`)}
      onClose={handleClose}
      initialFocusRef={cancelRef}
      finalFocusRef={finalFocusRef}
      closeLabel={t('useradmin.action.close')}
      closeOnBackdropClick={false}
    >
      <div className="useradmin-dialog" data-testid="useradmin-confirm-dialog">
        <div className="useradmin-dialog-lead">
          <p className="useradmin-dialog-text" data-testid="useradmin-confirm-target">
            {t('useradmin.dialog.target', values)}
          </p>
          <p className="useradmin-dialog-text" data-testid="useradmin-confirm-effect">
            {t(`useradmin.confirm.${action}.body`, values)}
          </p>
        </div>
        {submitting && slow && (
          <p role="status" className="useradmin-dialog-slow" data-testid="useradmin-confirm-slow">
            {t('useradmin.dialog.slow')}
          </p>
        )}
        <div className="useradmin-dialog-actions">
          <Button
            ref={cancelRef}
            variant="secondary"
            disabled={submitting}
            onClick={handleClose}
            data-testid="useradmin-confirm-cancel"
          >
            {t('useradmin.action.cancel')}
          </Button>
          <Button
            variant={DANGER_ACTIONS.has(action) ? 'danger' : 'primary'}
            loading={submitting}
            onClick={onConfirm}
            data-testid="useradmin-confirm-submit"
          >
            {t(submitting ? 'useradmin.action.processing' : `useradmin.confirm.${action}.submit`)}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
