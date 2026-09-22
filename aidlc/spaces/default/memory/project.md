# Project-Level Rules

> Project-specific specialisation and corrections. Loaded after `org.md` and
> `team.md` as strict-additive guidance; contradictions with broader policy
> are rejected. Populated by practices-discovery and the self-learning loop.
>
> Use sparingly: most teams don't need a project layer. Reach for it
> only when this specific project needs stable, durable guidance beyond the
> team practice (for example, package-specific release checks or an additional
> regression suite for a legacy component).

## Way of Working

<!-- Project-specific specialisation. Example: -->
<!-- This monorepo requires package-scoped branch names and a package owner -->
<!-- review in addition to the team's normal merge policy. -->

- ワークフロー計画で実現可能性の評価（Feasibility）をドメイン設計に吸収した場合は、ドメイン設計の中で実現可能性の判断（例: 組み込み H2 による単一インスタンス前提）も扱い、質問と ADR に記録する。 (learned 2026-09-22) <!-- cid:260922-auth-audit-base:domain-design:ee07c801eb0d3d87e282d50543ccbef44bacce0c9ef7a250387f1efe4dee204f -->
## Walking Skeleton

<!-- Project-specific specialisation. Example: -->
<!-- The walking skeleton must exercise the legacy service adapter as well -->
<!-- as the new service boundary. -->

## Testing Posture

<!-- Project-specific specialisation. -->

## Change Control

<!-- Project-specific. Mode: strict or relaxed. Strict here holds for every intent and cannot be changed from chat. -->

- コミットはこまめに、ファイル変更のまとまりごと（回答確定時・成果物作成時・内容確認/承認時）に行う。コミットのタイミングは提案し、実行前に必ず人間の承認を得る。コミットメッセージは日本語で記述する。 (learned 2026-09-22) <!-- cid:260922-auth-audit-base:intent-capture:822f19c942c585b350586f2ad3ecf1660487d52354264177a5c424103985dc8a -->
## Deployment

<!-- Project-specific specialisation. -->

## Code Style

<!-- Project-specific specialisation. -->

- ドキュメントや説明文中でファイル・ディレクトリのパスに言及する際は、絶対パスではなくプロジェクトルートからの相対パスで記述する（例: `aidlc/spaces/default/memory/project.md`）。 (learned 2026-09-22) <!-- cid:260922-auth-audit-base:intent-capture:64b945310a4c7f4ef0d40407fd6e745789656be88a6ef14e9a0b837f7f39aba8 -->
## Tech Stack

<!-- Technology choices locked for this project. -->

- フロントエンドはデザインシステム make-you-chic-ui（React + TypeScript）を使用する。Gitサブモジュールとして `vendor/make-you-chic-ui` に取り込み、組み込み手順は同リポジトリの `docs/integration-guide.md` に従う (learned 2026-09-22) <!-- cid:260922-auth-audit-base:approval-handoff:7a1d387f49e374e248043714bbcc0610684f5aed20a6ce859ddbffce95ae8cbc -->
## Decided

<!-- Decisions made in earlier stages that should not be re-asked. -->
<!-- Format: DECIDED: [decision] (Stage [slug], [date]) -->

- DECIDED: 監査記録の共通の仕組みはauth-audit-foundation Intentでは作らず、業務データのCRUDを扱う後続Intentで共通化を検討する (Stage scope-definition) (learned 2026-09-22) <!-- cid:260922-auth-audit-base:scope-definition:1052d63284043bef45745f943b9ecbd4368ac39b0f51a2ffda8c8cfd4a919d7a -->
- DECIDED: ログアウトは画面側でのトークン破棄とリフレッシュトークンのサーバー側無効化とし、アクセストークンの失効の仕組みは持たない。そのためアクセストークンの有効期限は短く設定する (Stage scope-definition) (learned 2026-09-22) <!-- cid:260922-auth-audit-base:scope-definition:6f6fc76572cdae83ee921248b14cd67754cd97f0866df35af930691d1f178539 -->
- DECIDED: ログイン後の画面は make-you-chic-ui の AppShell（サイドバー＋トップバー＋コンテンツの3領域）の中に置き、ログイン画面は AppShell の外に独立したレイアウトとして置く (Stage approval-handoff) (learned 2026-09-22) <!-- cid:260922-auth-audit-base:approval-handoff:e0f5bda9a9313505b73c5d90ef95850833f5c172df5799b28890ef44ab51cad2 -->
## Scope Overrides

<!-- Custom scope rules for this project. -->

## Forbidden

<!-- Populated by practices-discovery affirmation gate. -->
<!-- Format: NEVER [behavior] (affirmed [date]) -->
<!-- Example: NEVER throw exceptions across service layer boundaries (affirmed 2026-05-17) -->

- NEVER 初期管理者のパスワードやトークンの署名鍵などの秘密情報を、ソースコードや設定ファイルに直接書かない (affirmed 2026-09-22)
- NEVER パスワード（平文・ハッシュ値とも）・アクセストークン・リフレッシュトークン・署名鍵を、ログ・監査ログ・トレースの属性・外部へのエクスポート・エラー応答に含めない (affirmed 2026-09-22)
- NEVER `.env` や鍵ファイルなど秘密情報を含む設定ファイルをコミットしない（見本は値を空にした `.env.example` として置く） (affirmed 2026-09-22)
- NEVER `vendor/make-you-chic-ui` の中身をこのリポジトリから直接変更しない（変更は make-you-chic-ui のリポジトリ側で行う） (affirmed 2026-09-22)
## Mandated

<!-- Populated by practices-discovery affirmation gate. -->
<!-- Format: ALWAYS [behavior] (affirmed [date]) -->
<!-- Example: ALWAYS use Result<T,E> for fallible operations in service layer (affirmed 2026-05-17) -->

- ユーザー提供の参考資料は `reference/` に置き、Git管理対象外（.gitignore）とする。成果物やコードコメントに `reference/` 配下のファイルパスを書かない。内容は読み込んで理解し、必要な結論・要点を直接ドキュメントへ書き込む。 (learned 2026-09-22) <!-- cid:260922-auth-audit-base:intent-capture:602ae1e8f7161f7425d243033ebadc4ff4bfc9635bf7648a8fcd72e58e52d24d -->
- 生成するソースファイルの先頭には、言語のコメント構文に合わせたApache License 2.0の標準ヘッダーを必ず挿入する。年は `2026`、著作権者名は `agwlvssainokuni`。 (learned 2026-09-22) <!-- cid:260922-auth-audit-base:intent-capture:7963368b1341ed7275bfc2aa238b7b4892e9bf1121d0d3ce97166a3603529af0 -->
- ALWAYS 統合前に、フォーマット・リンタ・ビルド・全テスト・秘密情報の検出・依存関係の脆弱性検査（重大度 High 以上）が通っていることを確認する (affirmed 2026-09-22)
- ALWAYS 認証・認可・監査ログに関わる変更には、失敗の場合（拒否・ロック・無効なトークンなど）のテストを含める (affirmed 2026-09-22)
- ALWAYS 不具合を修正するときは、その不具合を再現するテストを同じコミットに含める (affirmed 2026-09-22)
- ALWAYS サブモジュールの固定先の更新は、承認を得た専用のコミットで行い、更新前後のコミットハッシュを記録する (affirmed 2026-09-22)
- ALWAYS 依存関係は lockfile で版を固定し、CI では lockfile どおりに入れる（`npm ci` など） (affirmed 2026-09-22)
- ALWAYS 秘密情報の検出を、コミット前と CI の両方で実行する (affirmed 2026-09-22)
## Corrections

<!-- Project-specific corrections from human feedback. -->
<!-- Format: NEVER/ALWAYS [behavior] (learned [date]) -->
