# チームの割り当て — timezone-ui-polish

Bolt は、1つ以上の作る単位（Unit of Work）を、設計から統合まで通して動くものにする1回の作り込みのこと。mob は、複数の担当が1つの作業を一緒に進める形のこと（この Intent では使わない）。この文書は、Bolt を誰が受け持つかを書く。

出典: Bolt の計画 `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/delivery-planning/bolt-plan.md`、チームの進め方 `aidlc/spaces/default/memory/team.md`（Way of Working）。

## 体制

- チームの編成（Team Formation）の段は、この範囲（classic）では行わない。作業は、依頼者1名と AI（aidlc-developer-agent）で行う。
- AI がすべての Bolt を作る。依頼者は、各段の承認、コミットの承認（段の成果物の初稿と承認の後を除く）、`origin` への `git push`、上流（make-you-chic-ui）への依頼を出すこと、承認の場での判断を受け持つ（`team.md`・`project.md`）。
- 一度に1つの Bolt だけを進める（作る前提: Q2 の前の段の答え A）。複数の担当が並行する形（Program Board: 複数のチームの計画を1枚で見る表）は使わない。

## Bolt と担当

| Bolt | 単位 | 担当（作る） | 依頼者の受け持ち |
|---|---|---|---|
| B1 | U3 u3-target-db-time | aidlc-developer-agent | 設計の段・コード生成の承認、K-95・K-98 の確かめの結果の確認、統合の前の検査の確認、`git push` |
| B2 | U1 u1-time-zone-conversion | aidlc-developer-agent | 同上 |
| B3 | U2 u2-display-time-zone | aidlc-developer-agent | 同上（利用者の設定の移行の確認を含む） |
| B4 | U4 u4-business-data-time | aidlc-developer-agent | 同上（N6 の代表の E2E の確認を含む） |
| B5 | U6 u6-app-icon-assets | aidlc-developer-agent | 同上（図柄の確認） |
| B6 | U5 u5-menu-icons-home | aidlc-developer-agent | 同上＋上流の取り込みの状態の確認と、取り込めないときの扱いの決定 |

## 確かめの担当

- 各 Bolt の設計・コードの確認（レビュー）は、段の定義の確認担当（`aidlc-architecture-reviewer-agent`・`aidlc-product-lead-agent`・コードの確認担当）が、別の担当として行う。作る担当とは分ける。
- テスト（性質ベース・結合・画面・E2E・axe）は、作る担当が Bolt の中で書き、`./gradlew verify` と E2E で確かめる。
