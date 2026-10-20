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
// Intent 261016-business-data-views の U3（D25）で features/roleadmin/ から移した。src/shared/ は機能を読まないため、文言は
// props で受け、data-testid の頭も呼ぶ側が決める（roleadmin は今の roleadmin-leave-* のまま）。
import { Button, Modal } from 'make-you-chic-ui'
import { useRef, useState } from 'react'
import './LeaveConfirmDialog.css'

/** 確かめの文言（呼ぶ側の言語の文） */
export interface LeaveConfirmLabels {
  /** 見出し */
  title: string
  /** 本文 */
  body: string
  /** ［留まる］ */
  stay: string
  /** ［移る］ */
  leave: string
  /** [×] の名前 */
  close: string
}

export interface LeaveConfirmDialogProps {
  /** ［留まる］（Escape・[×] を含む） */
  onStay: () => void
  /** ［移る］ */
  onLeave: () => void
  /** 文言 */
  labels: LeaveConfirmLabels
  /** data-testid の頭（`<頭>-dialog`・`<頭>-stay`・`<頭>-go`） */
  testIdPrefix: string
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
export function LeaveConfirmDialog({
  onStay,
  onLeave,
  labels,
  testIdPrefix,
}: LeaveConfirmDialogProps) {
  const stayRef = useRef<HTMLButtonElement>(null)
  // 開いた時点で戻し先を決める（描画のたびに探し直さない）。
  const [returnTab] = useState(selectedTabOfFocused)
  const returnRef = useRef<HTMLElement | null>(returnTab)

  return (
    <Modal
      open
      role="alertdialog"
      title={labels.title}
      onClose={onStay}
      initialFocusRef={stayRef}
      finalFocusRef={returnTab !== null ? returnRef : undefined}
      closeLabel={labels.close}
      closeOnBackdropClick={false}
    >
      <div className="shared-unsaved-guard-dialog" data-testid={`${testIdPrefix}-dialog`}>
        <p className="shared-unsaved-guard-text">{labels.body}</p>
        <div className="shared-unsaved-guard-actions">
          <Button
            ref={stayRef}
            variant="secondary"
            onClick={onStay}
            data-testid={`${testIdPrefix}-stay`}
          >
            {labels.stay}
          </Button>
          <Button variant="danger" onClick={onLeave} data-testid={`${testIdPrefix}-go`}>
            {labels.leave}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
