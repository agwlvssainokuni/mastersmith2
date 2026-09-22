# Intent Statement — auth-audit-foundation

## Problem Statement

MasterSmithの他の全機能は、認証・認可・監査ログの基盤の上に成り立つが、この基盤はまだ存在しない。本Intentは、その基盤（認証・認可基盤＋監査ログ・構造化ログ基盤）を最初に確立する [Q1]。本Intentが土台となる後続Intentの全体像は「Related Roadmap Intents (Self-Contained)」節を参照。

## Target Customer

MasterSmithを導入・運用する組織の内部管理者・利用者であり、外部エンドユーザー向けではない [Q3]。

## Success Metrics

- ログイン・JWT発行/検証・管理者権限ゲートが動作すること [Q2]
- 対象イベントが漏れなく監査ログに記録されること [Q2]
- 後続Intent（下記「Related Roadmap Intents」参照）がこの基盤の上に問題なく構築できること [Q2]

## Initiative Trigger

新規プロジェクト（MasterSmith）の立ち上げそのものであり、他の全機能の前提となる基盤が必要なため [Q4]。

## Initial Scope Signal

- ワークフロー選択スコープ: `auth-audit-foundation`（カスタム合成スコープ、22/33ステージ実行）[scope]
- ユーザー確認済みプロダクト境界: 上記スコープはユーザーが意図するプロダクト境界と一致している [Q8]

## Related Roadmap Intents (Self-Contained)

本IntentはMasterSmithプロジェクト全体のロードマップの一部である。本ドキュメントは他の資料への参照を前提とせず、関連Intent（A〜K）の内容をここに自己完結的に記載する [Q9]。

| Intent | 内容 |
|---|---|
| A | DSLスキーマ定義 — マスタ管理画面の項目設定（メニュー・検索条件・一覧表示・バリデーション・フォーム部品・表示名等）をYAML＋JSON Schemaとして定義する |
| B | 認証・認可基盤 — ログイン・JWT発行/検証、管理者フラグによる管理画面アクセス制御（**本Intentに含む**） |
| C | 監査ログ・構造化ログ基盤 — 内部DBへの監査ログ蓄積、SLF4J/logback構造化ログ、分散トレース、Actuator経由OTELエクスポート（**本Intentに含む**） |
| D | スキーマ読み込み・デフォルトDSL生成機能 — 管理画面からの手動実行、初回のデフォルトDSL生成、既存DSLへの全上書きリセット |
| E | DSLローダー／インタープリタ＋プレビュー・適用フロー — DSLを読み込み内部モデルへ変換するバックエンド基盤機能、プレビュー→適用の2段階反映フロー |
| F | ロールベース権限管理 — ユーザー/グループ/ロールの管理、主権限・補助権限の設定画面、作業ロール切替UI、内部DB永続化、YAML export/import |
| G | ユーザー登録・招待フロー — 管理者によるユーザー登録、言語指定付き招待メール送信、招待ユーザーによるプリファレンス設定での登録完了 |
| H | ユーザープリファレンス — 言語（ja/en）・テーマ（light/dark/system）・文字サイズの個人設定画面、内部DB永続化 |
| I | メニュー・ナビゲーション（N階層） |
| J | 一覧画面（検索＋一覧表示） — DSL・権限駆動でのUI生成 |
| K | 詳細・編集画面（バリデーション＋フォーム部品＋参照ピッカー子画面） — DSL・権限駆動でのUI生成 |

## Assumptions & Open Questions

None.
