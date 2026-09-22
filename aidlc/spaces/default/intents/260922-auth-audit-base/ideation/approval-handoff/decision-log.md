# Decision Log — auth-audit-foundation（Ideationフェーズ）

Ideationフェーズで行った決定の記録。「出典」は決定が記録されている場所を示す。

| ID | 決定 | 決定者 | 出典 |
|---|---|---|---|
| D-01 | 最初のIntentとして、認証・認可基盤（Intent B）と監査ログ・構造化ログ基盤（Intent C）を統合して取り組む。他のIntentの統廃合は、次のIntentを決めるときに再検討する | 依頼者 | ワークフロー開始時の依頼（`project-description.json`） |
| D-02 | ワークフロー計画として、カスタムスコープ `auth-audit-foundation`（33ステージ中22ステージを実行）を採用する。市場調査・実現可能性評価・チーム編成・ラフモックアップ・ユーザーストーリー・詳細モックアップ・契約設計・デリバリー計画・インシデント対応・フィードバック最適化は実行しない（リバースエンジニアリングは新規プロジェクトのため対象外） | 依頼者 | ワークフロー計画の承認（`.claude/scopes/aidlc-auth-audit-foundation.md`） |
| D-03 | 変更管理（Change Control）を `strict` から `relaxed` に変更する | 依頼者 | 監査ログ `CHANGE_CONTROL_SET` |
| D-04 | 本Intentの課題・対象ユーザー・成功基準・トリガーを確定し、ステークホルダーと意思決定者は依頼者1名とする | 依頼者 | intent-capture-questions.md Q1〜Q8 |
| D-05 | 成果物は他の資料を前提とせず自己完結的に書くため、ロードマップ全体（Intent A〜K）の内容を intent-statement.md に記載する | 依頼者 | intent-capture-questions.md Q9・Q10 |
| D-06 | MVPはバックエンドAPI＋最小限のログイン画面＋ログイン後の画面の枠とし、本Intentが最初のアプリ骨格を兼ねる | 依頼者 | scope-definition-questions.md Q1 |
| D-07 | 最初の管理者ユーザーは、初回起動時に設定（設定ファイル／環境変数）で指定したものを自動作成する | 依頼者 | scope-definition-questions.md Q2 |
| D-08 | 認証機能はログイン・ログアウト・トークン更新・アカウントロックを含め、トークン失効・強制ログアウトは含めない | 依頼者 | scope-definition-questions.md Q3 |
| D-09 | ログアウトは画面側でのトークン破棄とリフレッシュトークンのサーバー側無効化とし、アクセストークンの有効期限を短く設定する | 依頼者 | scope-definition-questions.md Q10 |
| D-10 | 監査ログの記録対象はログイン成功・失敗、ログアウト、権限不足による管理画面へのアクセス拒否とし、後続Intent向けの共通化は後続Intentで行う | 依頼者 | scope-definition-questions.md Q4・Q9 |
| D-11 | 構造化ログ・分散トレース・OTELエクスポート機能まで含める（エクスポートは設定で有効化、既定は無効） | 依頼者 | scope-definition-questions.md Q5 |
| D-12 | ロール・権限設定（F）、ユーザー登録・招待（G）、ユーザープリファレンス（H）、対象DB接続（D/E）は範囲外とする | 依頼者 | scope-definition-questions.md Q6 |
| D-13 | 作業は依存関係順（アプリ骨格・ログ基盤 → 認証 → 監査ログ）で進め、期限は設けない | 依頼者 | scope-definition-questions.md Q7・Q8 |
| D-14 | 実現可能性は独立したステップで評価せず、ドメイン設計の中で判断する | 依頼者 | approval-handoff-questions.md Q1 |
| D-15 | 認識済みの3つのリスクを受け入れたうえで進め、対策は要件定義・設計・Practices Discoveryで具体化する | 依頼者 | approval-handoff-questions.md Q2 |
| D-16 | 体制は依頼者1名とAIとする | 依頼者 | approval-handoff-questions.md Q3 |
| D-17 | make-you-chic-ui のアプリシェルの構造に沿った画面イメージ（concept-visuals.html、4画面）を作成し、その内容で確定する | 依頼者 | approval-handoff-questions.md Q4・Q6 |
| D-18 | デザインシステム make-you-chic-ui を、Gitサブモジュールとして `vendor/make-you-chic-ui` に取り込む | 依頼者 | リポジトリのコミット（`.gitmodules`） |
| D-19 | Inceptionフェーズに進む（Go） | 依頼者 | approval-handoff-questions.md Q5 |

## Assumptions & Open Questions

None.
