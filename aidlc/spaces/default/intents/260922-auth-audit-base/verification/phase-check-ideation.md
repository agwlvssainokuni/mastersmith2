# Phase Check — Ideation → Inception

対象Intent: `260922-auth-audit-base`（スコープ `auth-audit-foundation`）

## 1. Intent → Scope の整合性

intent-statement.md が示す本Intentの範囲（認証・認可基盤＋監査ログ・構造化ログ基盤）と、scope-document.md のスコープ内項目との対応。

| Intent の要素（intent-statement.md） | 対応するスコープ項目（scope-document.md） | 判定 |
|---|---|---|
| ログイン・JWT発行／検証（Intent B） | S4 ログイン、S5 トークン更新、S6 ログアウト、S7 アカウントロック | OK |
| 管理者フラグによる管理画面アクセス制御（Intent B） | S8 管理画面アクセス制御 | OK |
| 内部DBへの監査ログ蓄積（Intent C） | S9 監査ログ記録 | OK |
| 構造化ログ・分散トレース・OTELエクスポート（Intent C） | S10 構造化ログ・分散トレース・外部エクスポート | OK |
| 後続Intentがこの基盤の上に構築できること（成功基準） | S1 アプリ骨格、S2 最小限の画面、S3 初期管理者の自動作成 | OK |

逆方向（スコープ項目 → Intent）: S1〜S10 はいずれも上表のIntentの要素に対応しており、Intentの範囲外の項目はない。

## 2. Scope → Intent Backlog の整合性

| スコープ項目 | 対応する proto-Unit（intent-backlog.md） | 判定 |
|---|---|---|
| S1 アプリ骨格 | PB-1 | OK |
| S2 最小限の画面 | PB-1（画面の枠）、PB-4（ログイン画面） | OK |
| S3 初期管理者の自動作成 | PB-3 | OK |
| S4 ログイン | PB-4 | OK |
| S5 トークン更新 | PB-5 | OK |
| S6 ログアウト | PB-5 | OK |
| S7 アカウントロック | PB-6 | OK |
| S8 管理画面アクセス制御 | PB-7 | OK |
| S9 監査ログ記録 | PB-8 | OK |
| S10 構造化ログ・分散トレース・外部エクスポート | PB-2 | OK |

スコープ外 X1〜X6 は、intent-backlog.md の Won't W-1〜W-6 と1対1で対応している。

逆方向（proto-Unit → スコープ項目）: PB-1〜PB-8 はいずれもスコープ内項目に対応しており、スコープ外の proto-Unit はない。

## 3. スコープ項目の実現可能性の裏付け

| 判定 | 内容 |
|---|---|
| Deferred | 実現可能性の評価ステップ（Feasibility）はこのワークフローでは実行しないため、S1〜S10 に対する実現可能性の評価文書はない。依頼者の決定により、実現可能性はドメイン設計の中で判断する（approval-handoff-questions.md Q1、decision-log.md D-14） |

## 4. フェーズ間の矛盾

矛盾は検出されなかった。

## 5. 結果

- Intent → Scope: 100%（5/5）対応
- Scope → Intent Backlog: 100%（10/10）対応
- 実現可能性の裏付け: ドメイン設計へ持ち越し（依頼者の決定による）

- [ ] 人による確認（Approval & Handoff の承認ゲートで確認する）
