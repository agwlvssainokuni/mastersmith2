# Initiative Brief — auth-audit-foundation

Ideationフェーズの成果物をまとめた1ページの概要。詳細は各成果物を参照。

- `aidlc/spaces/default/intents/260922-auth-audit-base/ideation/intent-capture/intent-statement.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/ideation/intent-capture/stakeholder-map.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/ideation/scope-definition/scope-document.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/ideation/scope-definition/intent-backlog.md`

## Intent と課題

MasterSmith（マスタ管理アプリ）の他の全機能は、認証・認可・監査ログの基盤の上に成り立つが、この基盤はまだ存在しない。本Intentは「認証・認可基盤（Intent B）」と「監査ログ・構造化ログ基盤（Intent C）」を統合し、この基盤を最初に確立する。対象ユーザーは MasterSmith を導入・運用する組織の内部管理者・利用者である（intent-statement.md）。

成功基準は、ログイン・トークン発行／検証・管理者権限ゲートが動作し、対象イベントが漏れなく監査ログに記録され、後続Intentがこの基盤の上に問題なく構築できることである（intent-statement.md）。

## 市場検証

市場調査は行っていない。ワークフロー計画（`auth-audit-foundation`）の承認時に、「内部管理者向けの基盤機能であり、調査対象となる外部市場がない」という理由で、市場調査のステップを実行しないことが決まっている（decision-log.md D-02）。

## 実現可能性とリスク

- 実現可能性: 独立した評価は行わず、ドメイン設計の中で判断する（approval-handoff-questions.md Q1）
- 認識済みのリスク（approval-handoff-questions.md Q2）:
  - テスト・CIが一切ない新規リポジトリのため、品質を確かめる仕組みを一から作る必要がある
  - 認証・認可はセキュリティ上重要で、監査ログは後から追えることが求められる
  - 後続の全Intentがこの基盤の上に乗るため、ここでの判断の影響範囲が大きい
- 対策は要件定義・設計・Practices Discoveryで具体化する（同 Q2）

## スコープ境界

| 区分 | 内容 |
|---|---|
| スコープ内 | アプリ骨格、最小限の画面（ログイン画面・ログイン後の画面の枠）、初期管理者の自動作成、ログイン、トークン更新、ログアウト、アカウントロック、管理画面アクセス制御、監査ログ記録、構造化ログ・分散トレース・OTELエクスポート（scope-document.md S1〜S10） |
| スコープ外 | ロール・権限設定（F）、ユーザー登録・招待（G）、ユーザープリファレンス（H）、対象DB接続（D/E）、トークン失効・強制ログアウト、後続Intent向けの共通の監査記録の仕組み（scope-document.md X1〜X6） |
| 進め方 | 依存関係順（アプリ骨格・ログ基盤 → 認証 → 監査ログ）。期限なし（scope-document.md） |

## 画面イメージ

`concept-visuals.html`（本ディレクトリ）に、デザインシステム make-you-chic-ui のアプリシェルの構造（サイドバー＋トップバー＋コンテンツの3領域、ログイン画面はアプリシェルの外に独立して置く）に沿った4画面の画面イメージを置いた（approval-handoff-questions.md Q4・Q6）。

| 画面 | 内容 |
|---|---|
| 画面1 ログイン | 中央のカードに、ユーザーID・パスワード・ログインボタン |
| 画面2 ログイン失敗時 | アカウントロックを知らせるメッセージを表示 |
| 画面3 ログイン後（一般ユーザー） | サイドバーは「ホーム」のみ。コンテンツ領域は後続Intentのためのプレースホルダ |
| 画面4 ログイン後（管理者） | サイドバーに「管理」が加わり、管理者のみ入れる領域のプレースホルダを表示。右上のユーザーメニューからログアウト |

## 体制

依頼者1名とAIで進める（approval-handoff-questions.md Q3）。

## Go / No-go 推奨

**Go** — Inceptionフェーズ（要件定義・設計）に進む（approval-handoff-questions.md Q5）。

## Assumptions & Open Questions

None.
