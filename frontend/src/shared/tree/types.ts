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
// 共有の木 SharedTreeView の型（契約 C2、機能設計 ENT-006〜ENT-009、frontend-components.md 2.2）。
// 木は app/ と features/ を読まない（BR2.2）。文言は持たず、呼ぶ側が labels で渡す（BR4.9）。

/** 節の横に添える短い印（ENT-007）。意味は text で示し、tone は見た目の補いだけ（BR4.11）。 */
export interface SharedTreeBadge {
  /** 1つの節の中で重ならない */
  id: string
  /** 文字として描く（信頼できない入力として扱う） */
  text: string
  /** 見た目の種類（既定は neutral） */
  tone?: 'neutral' | 'warning'
}

/** 木の節（ENT-006）。子は開いたときに loadChildren で読む。 */
export interface SharedTreeNode {
  /** 1つの木の中で読み込んだすべての節を通じて重ならない */
  id: string
  /** 表示名。文字として描く（信頼できない入力として扱う、BR4.1） */
  label: string
  /** 開閉のボタンを出すか */
  hasChildren: boolean
  /** 印（既定は無し） */
  badges?: readonly SharedTreeBadge[]
}

/** 木の文言（ENT-008）。呼ぶ側が表示言語に合わせて渡す。 */
export interface SharedTreeLabels {
  /** 閉じている節の開閉のボタンの名前（例「販売DB を開く」） */
  expand: (label: string) => string
  /** 開いている節の開閉のボタンの名前（例「販売DB を閉じる」） */
  collapse: (label: string) => string
  /** 子の読み込み中 */
  loading: string
  /** 子の読み込みの失敗（拒否の理由は出さない） */
  loadFailed: string
  /** 再試行のボタン */
  retry: string
  /** 子が0件 */
  empty: string
}

/** 節ごとの子の読み込みの状態の種類（ENT-009、functional-spec.md 5.1）。 */
export type SharedTreeChildStatus = 'notLoaded' | 'loading' | 'loaded' | 'failed'

/** 節ごとの子の読み込みの状態（ENT-009）。木の中だけで持ち、外へ出さない。 */
export interface SharedTreeChildState {
  nodeId: string
  status: SharedTreeChildStatus
  /** loaded のときだけ */
  children?: readonly SharedTreeNode[]
}

/** 共有の木の props（契約 C2 と、互換の変更として足した labels・ariaLabel）。 */
export interface SharedTreeViewProps {
  /** 最上位の節。新しい配列に替えると、持っていた子を捨てて開いている節を読み直す（BR4.5） */
  nodes: readonly SharedTreeNode[]
  /** 節を開いたときに子を読む */
  loadChildren: (id: string) => Promise<readonly SharedTreeNode[]>
  /** 選んでいる節 */
  selectedId: string | null
  /** 選ぶボタンが押された */
  onSelect: (id: string) => void
  /** 開いている節（呼ぶ側が持つ、BR4.4） */
  expandedIds: ReadonlySet<string>
  /** 開閉のボタンが押された（expanded は次の状態） */
  onToggle: (id: string, expanded: boolean) => void
  /** 文言 */
  labels: SharedTreeLabels
  /** 最上位の一覧の読み上げの名前 */
  ariaLabel: string
}
