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
// S5 削除の確かめ（Intent 261016-business-data-views の U3 の L7、D26・D23・D4、F5、interaction-spec.md の DeleteConfirmModal、
// AC2.3.1〜AC2.3.5）。make-you-chic-ui の Modal（role="alertdialog"、背景のクリックで閉じない、はじめのフォーカスは「やめる」）。
// - 見出しは「<テーブルの表示名> <行のラベル> を削除しますか」。Escape・[×] は「やめる」と同じ（送信中は閉じない）。
// - 失敗は Modal の中に文を出す（外部キーで拒まれた・行が無い・つながらない など）。
// - 削除されたか分からないとき（D4: A）: 文と、一覧を読み直して確かめる入口を出す。もう一度「削除する」を押すと、
//   「削除済みかもしれません」の確かめ（はじめのフォーカスは「やめる」）に替えてから送る。
// - 行の値・主キーを data-testid・id に入れない。
import { Alert, Button, Modal } from 'make-you-chic-ui'
import { useRef } from 'react'
import { useTranslation } from 'react-i18next'
import './tablesCommon.css'

/** 確かめの段（confirm: 初めの確かめ、maybe: 分からなかった後の確かめ） */
export type DeletePhase = 'confirm' | 'maybe'

export interface DeleteConfirmModalProps {
  /** 見出し（行のラベルを含む） */
  title: string
  phase: DeletePhase
  /** 送信中 */
  busy: boolean
  /** 送信から 5 秒を過ぎた */
  slow: boolean
  /** Modal の中に出す失敗の文（無ければ undefined） */
  message?: string
  /** 削除されたか分からない */
  unknown: boolean
  /** 確かめる入口（一覧を読み直す）。分からないときだけ出す */
  onCheck?: () => void
  onCancel: () => void
  onConfirm: () => void
}

/** 削除の確かめ */
export function DeleteConfirmModal({
  title,
  phase,
  busy,
  slow,
  message,
  unknown,
  onCheck,
  onCancel,
  onConfirm,
}: DeleteConfirmModalProps) {
  const { t } = useTranslation()
  const cancelRef = useRef<HTMLButtonElement>(null)

  function handleClose(): void {
    if (!busy) {
      onCancel()
    }
  }

  return (
    <Modal
      open
      role="alertdialog"
      title={phase === 'maybe' ? t('tables.write.maybeSavedTitle') : title}
      onClose={handleClose}
      initialFocusRef={cancelRef}
      closeLabel={t('tables.delete.cancel')}
      closeOnBackdropClick={false}
    >
      <div className="tables-dialog" data-testid="tables-delete-dialog">
        <p className="tables-text" data-testid="tables-delete-text">
          {phase === 'maybe' ? t('tables.write.maybeDeletedBody') : t('tables.delete.body')}
        </p>
        {message !== undefined && (
          <div data-testid="tables-delete-alert">
            <Alert variant="danger">{message}</Alert>
          </div>
        )}
        {unknown && phase === 'confirm' && (
          <div data-testid="tables-delete-unknown">
            <Alert variant="warning">{t('tables.write.unknownDelete')}</Alert>
            {onCheck !== undefined && (
              <button type="button" className="tables-text-button" onClick={onCheck}>
                {t('tables.write.reloadList')}
              </button>
            )}
          </div>
        )}
        {busy && (
          <p role="status" className="tables-text" data-testid="tables-delete-status">
            {slow ? t('tables.form.slow') : t('tables.delete.deleting')}
          </p>
        )}
        <div className="tables-dialog-actions">
          <Button
            ref={cancelRef}
            variant="secondary"
            disabled={busy}
            onClick={handleClose}
            data-testid="tables-delete-cancel"
          >
            {t('tables.delete.cancel')}
          </Button>
          <Button
            variant="danger"
            loading={busy}
            onClick={() => {
              if (!busy) {
                onConfirm()
              }
            }}
            data-testid="tables-delete-confirm"
          >
            {phase === 'maybe' ? t('tables.write.sendAgain') : t('tables.delete.confirm')}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
