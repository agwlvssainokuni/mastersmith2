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
// 未保存の変更の確かめ（Intent 261004-role-menu の U6 の D15、frontend-components.md の 8節）。make-you-chic-ui の
// Modal（role="alertdialog"、背景のクリックで閉じない、はじめのフォーカスは「留まる」）。Escape・[×] は「留まる」と
// 同じ。閉じた後のフォーカスは、開いたときにフォーカスのあった要素がタブなら選んでいるタブへ戻す（矢印のキーで
// 別のタブへ移ろうとして止めたとき、選ばれていないタブにフォーカスが残らないため）。それ以外は Modal の既定の戻し。
import { Button, Modal } from 'make-you-chic-ui'
import { useRef, useState } from 'react'
import { useRoleAdminText } from './useRoleAdminText'
import './RoleListPage.css'

export interface LeaveConfirmDialogProps {
  /** ［留まる］（Escape・[×] を含む） */
  onStay: () => void
  /** ［移る］ */
  onLeave: () => void
}

/** 開いたときにフォーカスのあった要素がタブなら、同じタブの並びの選んでいるタブ */
function selectedTabOfFocused(): HTMLElement | null {
  const focused = document.activeElement
  if (!(focused instanceof HTMLElement) || focused.getAttribute('role') !== 'tab') {
    return null
  }
  const list = focused.closest('[role="tablist"]')
  return list?.querySelector<HTMLElement>('[role="tab"][aria-selected="true"]') ?? null
}

/** 未保存の変更の確かめ */
export function LeaveConfirmDialog({ onStay, onLeave }: LeaveConfirmDialogProps) {
  const t = useRoleAdminText()
  const stayRef = useRef<HTMLButtonElement>(null)
  // 開いた時点で戻し先を決める（描画のたびに探し直さない）。
  const [returnTab] = useState(selectedTabOfFocused)
  const returnRef = useRef<HTMLElement | null>(returnTab)

  return (
    <Modal
      open
      role="alertdialog"
      title={t('roleadmin.leave.title')}
      onClose={onStay}
      initialFocusRef={stayRef}
      finalFocusRef={returnTab !== null ? returnRef : undefined}
      closeLabel={t('roleadmin.action.close')}
      closeOnBackdropClick={false}
    >
      <div className="roleadmin-dialog" data-testid="roleadmin-leave-dialog">
        <p className="roleadmin-dialog-text">{t('roleadmin.unsaved.leave')}</p>
        <div className="roleadmin-dialog-actions">
          <Button
            ref={stayRef}
            variant="secondary"
            onClick={onStay}
            data-testid="roleadmin-leave-stay"
          >
            {t('roleadmin.leave.stay')}
          </Button>
          <Button variant="danger" onClick={onLeave} data-testid="roleadmin-leave-go">
            {t('roleadmin.leave.go')}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
