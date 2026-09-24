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
// プレビュー・投入・履歴のタブ（interaction-spec.md の DslTabs）。make-you-chic-ui の Tabs を使い、
// 左右の矢印でタブを移り、Tab でパネルへ入る。選ばれているタブは画面（DslAdminPage）が持つ。
import { Tabs } from 'make-you-chic-ui'
import type { ReactNode } from 'react'
import { useDslText } from './useDslText'

/** タブ */
export type DslTab = 'preview' | 'submit' | 'history'

/** タブの並び */
export const DSL_TABS: readonly DslTab[] = ['preview', 'submit', 'history']

export interface DslTabsProps {
  selected: DslTab
  onSelect: (tab: DslTab) => void
  /** タブごとの中身（選ばれているタブの中身だけを描く） */
  panels: Readonly<Record<DslTab, ReactNode>>
}

/** プレビュー・投入・履歴のタブ */
export function DslTabs({ selected, onSelect, panels }: DslTabsProps) {
  const t = useDslText()
  return (
    <div data-testid="dsl-tabs">
      <Tabs
        aria-label={t('dsl.page.tabsLabel')}
        activeIndex={DSL_TABS.indexOf(selected)}
        onChange={(index) => onSelect(DSL_TABS[index] ?? 'preview')}
        items={DSL_TABS.map((tab) => ({ label: t(`dsl.tab.${tab}`), content: panels[tab] }))}
      />
    </div>
  )
}
