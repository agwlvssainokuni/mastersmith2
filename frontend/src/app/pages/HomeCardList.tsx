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
// ホームのカードの並び（Intent 261102-timezone-ui-polish の U5、functional-spec.md の D21）。
// 番号なしのリスト（ul・li）で、見出し（h2）と aria-labelledby で結ぶ。
import { HomeCard } from './HomeCard'
import type { HomeCardModel } from './homeCards'
import './HomeCards.css'

export interface HomeCardListProps {
  /** 結ぶ見出しの id */
  headingId: string
  cards: readonly HomeCardModel[]
  /** data-testid の前置き（`<前置き>-card-<n>` と `<前置き>-list`） */
  testIdPrefix: string
}

/** カードの並び */
export function HomeCardList({ headingId, cards, testIdPrefix }: HomeCardListProps) {
  return (
    <ul className="home-card-list" aria-labelledby={headingId} data-testid={`${testIdPrefix}-list`}>
      {cards.map((card, index) => (
        <li key={card.id} className="home-card-item">
          <HomeCard card={card} testId={`${testIdPrefix}-card-${index}`} />
        </li>
      ))}
    </ul>
  )
}
