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
// Modal が閉じ終わった後に処理を始める口（Intent 261003-user-admin-followup の G1）。
// make-you-chic-ui の ModalStackProvider は、Modal を閉じた後の描画の効果で背景（body の直下の要素）の inert を外し、
// 続けて開く前の要素（または finalFocusRef）へフォーカスを戻す。閉じると同時に一覧の読み直しを始めると、読み直しの後に
// 当てたフォーカス（見出し・行）を、後から来るこの戻しが上書きしうる。そこで、背景の inert がすべて外れたことを
// MutationObserver で待ってから処理を始める。MutationObserver の知らせは、inert を外してフォーカスを戻す効果が
// 終わった後（同じ効果の中の同期の処理の後）に届くため、処理はフォーカスの戻しより後になる。
// 背景に inert が無いとき（Modal が開いていない・ModalStackProvider が無い）は、すぐに処理を始める。

/** 背景（body の直下の要素）に inert が残っているか */
function backgroundInert(): boolean {
  return document.querySelector('body > [inert]') !== null
}

/**
 * Modal が閉じ終わった後（背景の inert が外れ、フォーカスの戻しが済んだ後）に callback を1回呼ぶ。
 * 閉じる操作（Modal を描かなくする状態の更新）と同じ時点で呼ぶ。
 *
 * @param callback 閉じ終わった後に始める処理
 * @returns 待ちをやめる関数（画面を離れるときに呼ぶ。呼んだ後は callback を呼ばない）
 */
export function runAfterModalClosed(callback: () => void): () => void {
  if (!backgroundInert()) {
    callback()
    return () => {}
  }
  const observer = new MutationObserver(() => {
    if (!backgroundInert()) {
      observer.disconnect()
      callback()
    }
  })
  observer.observe(document.body, {
    subtree: true,
    childList: true,
    attributes: true,
    attributeFilter: ['inert'],
  })
  return () => observer.disconnect()
}
