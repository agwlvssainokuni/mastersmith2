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
// 行の「操作」とメニュー（S2、functional-spec.md の 4.4・W4・D5・D6・D13、frontend-components.md の 2.5・4節、
// AC2.1.9・AC3.1.8、NFR1.1・NFR7.1）。make-you-chic-ui の Dropdown の trigger に Button（secondary・小）を渡す。
// - 読み上げの名前は「〔氏名〕（〔メールアドレス〕）の操作」、busy なら末尾に「（処理中）」。
// - busy の間は Button を loading にする。Dropdown は trigger の onClick を「開く」の関数で上書きするが、Button は loading の
//   とき受け取った onClick を呼ばないため、メニューは開かない。disabled 属性を付けないため、フォーカスは残る（4.4）。
// - 項目は rowActions の結果から作る。押せない項目は disabled と理由の文（description）にし、Dropdown が aria-disabled と
//   aria-describedby で結ぶ（名前は項目名だけ）。押せない項目を押しても onClick は呼ばれず、メニューは開いたまま（固定先 3d9521a で確かめた形、
//   generation-notes.md の回2 の FC 2.5 の表）。守りとして、busy の間に項目の選択が届いても onSelect を呼ばない。
// - 画面で押せない形にするのは表示だけで、サーバーの拒否（U3）の代わりにしない。
// - メニューは placement="bottom-end" で trigger の右端にそろえる（表の右端の列で画面の右へはみ出さないため。FR2.1）。
import { Button, Dropdown, type MenuItem } from 'make-you-chic-ui'
import type { AdminUser } from './api/types'
import { rowActions, type RowActionKind } from './rowActions'
import { useUserAdminText } from './useUserAdminText'

export interface UserRowActionsProps {
  /** 対象の行 */
  user: AdminUser
  /** 一覧の読み直しの間、またはこの行への要求の送信中 */
  busy: boolean
  /** 押せる項目を選んだ */
  onSelect: (action: RowActionKind) => void
  /** 「操作」を包む要素（フォーカスを戻すときに中のボタンを探す） */
  containerRef?: (element: HTMLSpanElement | null) => void
}

/** 行の「操作」 */
export function UserRowActions({ user, busy, onSelect, containerRef }: UserRowActionsProps) {
  const t = useUserAdminText()
  const values = { name: user.displayName, email: user.email }
  const name = t(busy ? 'useradmin.actions.busyName' : 'useradmin.actions.name', values)

  const items: MenuItem[] = rowActions(user).map((action) =>
    action.disabledReason !== undefined
      ? {
          label: t(`useradmin.menu.${action.kind}`),
          disabled: true,
          description: t(`useradmin.disabled.${action.disabledReason}`),
        }
      : {
          label: t(`useradmin.menu.${action.kind}`),
          onClick: () => {
            if (!busy) {
              onSelect(action.kind)
            }
          },
        },
  )

  return (
    <span
      ref={containerRef}
      className="useradmin-row-actions"
      data-testid={`useradmin-row-actions-${user.userId}`}
    >
      <Dropdown
        trigger={
          <Button variant="secondary" size="sm" loading={busy} aria-label={name}>
            {t('useradmin.actions.button')}
          </Button>
        }
        items={items}
        // 「操作」は表の右端の列に置くため、メニューの右端を trigger の右端にそろえ、画面の右へはみ出さないようにする（FR2.1）。
        placement="bottom-end"
      />
    </span>
  )
}
