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
// 横のスクロールの枠（Intent 261016-business-data-views の U3 の L8、D27、AC1.3.15、U3-NFR5.6・5.10）。
// - 広い表を包み、画面全体ではなく枠の中だけを横にスクロールさせる。
// - role="region" と aria-label で名前を持ち、tabIndex=0 でキーボードのフォーカスを受けて矢印で動かせる（WCAG 2.1.1・2.4.6）。
// - make-you-chic-ui の Table の枠にこの口が無いため、取り込み（依頼（5））までの手当て。取り込まれたら B7 で置き換える。
// - src/shared/ は app/・features/ を読まない。名前は呼ぶ側が画面の言語の文で渡す。
import type { ReactNode } from 'react'
import './ScrollRegion.css'

export interface ScrollRegionProps {
  /** 枠の名前（例「商品の一覧の表」） */
  label: string
  children: ReactNode
  /** テストの印（行の値を入れない） */
  testId?: string
}

/** 横のスクロールの枠 */
export function ScrollRegion({ label, children, testId }: ScrollRegionProps) {
  return (
    <div
      className="shared-scroll-region"
      role="region"
      aria-label={label}
      // oxlint-disable-next-line jsx-a11y/no-noninteractive-tabindex -- 中に押せる要素が無くても横に動く表の枠をキーボードで動かせるようにするため（axe の scrollable-region-focusable、D27。roletransfer の TransferCheckTable と同じ形）。role="region" と名前つきの枠で、除外はこの1行だけ。
      tabIndex={0}
      data-testid={testId}
    >
      {children}
    </div>
  )
}
