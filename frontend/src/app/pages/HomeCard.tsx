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
// ホームの入口のカード 1 枚（Intent 261102-timezone-ui-polish の U5、functional-spec.md の D17・D21・D29）。
// - react-router の Link の中に make-you-chic-ui の Card を置く。押せる範囲は Link（名前が読み上げられ、Tab で届き、Enter で押せる）。
// - アイコンは飾りで読み上げから外す（aria-hidden）。名前は文字として描く（HTML として描かない）。
import { Card, Icon } from 'make-you-chic-ui'
import { Link } from 'react-router'
import type { HomeCardModel } from './homeCards'
import './HomeCards.css'

export interface HomeCardProps {
  card: HomeCardModel
  testId: string
}

/** ホームのカード */
export function HomeCard({ card, testId }: HomeCardProps) {
  return (
    <Link className="home-card-link" to={card.path} data-testid={testId}>
      <Card className="home-card">
        <span className="home-card-icon" aria-hidden="true">
          <Icon name={card.icon} />
        </span>
        <span className="home-card-label">{card.label}</span>
      </Card>
    </Link>
  )
}
