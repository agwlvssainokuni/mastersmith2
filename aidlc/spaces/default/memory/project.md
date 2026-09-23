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
- Construction の設計の段で、単位に新しく決める論点が無いときは、質問を作らず、設計の要点を要約として依頼者に確認する（Looks correct / Request changes）。 (learned 2026-09-22) <!-- cid:260922-auth-audit-base:infrastructure-design:43273034433bb9ddc5727514f8d6640170eddfcfff090cc9e50101aaeb3a7182 -->
- ローカルの1コマンドの検査は Gradle の1つのタスク（./gradlew verify）を入口にし、フロントエンドの検査（npm）と外部の道具（Gitleaks・OSV-Scanner）も Gradle から呼ぶ。CI も同じタスクを呼ぶ。 (learned 2026-09-22) <!-- cid:260922-auth-audit-base:infrastructure-design:62d8de5982127d518cdf7b11cd3ae4dbf5764a9bb03b3861724f28dd11b92945 -->
- Spring Security を使う単位では、セキュリティの決まり（SecurityFilterChain）を、それを前提とするテストより先に入れる。決まりが無いと既定の設定が全要求にログインを求め、関係のないテストが落ちるため。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:code-generation:bfd72fde6eec3e1831dced6070a1f62fffaa4c44bf9f498ba97aa50af15c6737 -->
- 要件の網羅を確かめるときは、要件定義の FR・NFR から、機能設計の BR と NFR 要件の枝番を経て、Code Generation の traceability.json へ至る2段の連鎖でたどる（traceability.json は要件の ID を直接持たず、単位ごとの ID で持つため）。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:build-and-test:1340b2bcd75ac79b135d0768a43f458838dd75a8266977a7f183ed9c9fba3820 -->
- 確認のための要約と、実装または承認済みの設計が食い違ったときは、実装と承認済みの設計を正として記録し、要約との差を成果物に明記する（要約に合わせて実装を変えない）。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:ci-pipeline:14248c7b19e31f384aae87bfe2bc4ccc4a1b979bdd77d36d0ee3452b0e6da01d -->
- 確定済みの設計と違う決定を依頼者がしたときは、設計の文書は書き換えず、差をその段の成果物に明記し、README などの手順を決定に合わせて直す。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:deployment-pipeline:b511560ca6ee8edac9468a296692cea2d0ce2afd343dedd46d09e1691cc7ea6f -->
## Walking Skeleton

<!-- Project-specific specialisation. Example: -->
<!-- The walking skeleton must exercise the legacy service adapter as well -->
<!-- as the new service boundary. -->

## Testing Posture

<!-- Project-specific specialisation. -->

- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:build-and-test:0cb06418ec91709472835a5f5f3278309672385a651295cb7d541197c7521196 -->
- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:build-and-test:0bf5838d31cde7d0227a8b8a7218db87d70238e8b3c13aa06ca6b3864dd85c28 -->
## Change Control

<!-- Project-specific. Mode: strict or relaxed. Strict here holds for every intent and cannot be changed from chat. -->

- コミットはこまめに、ファイル変更のまとまりごと（回答確定時・成果物作成時・内容確認/承認時）に行う。コミットのタイミングは提案し、実行前に必ず人間の承認を得る。コミットメッセージは日本語で記述する。 (learned 2026-09-22) <!-- cid:260922-auth-audit-base:intent-capture:822f19c942c585b350586f2ad3ecf1660487d52354264177a5c424103985dc8a -->
- 確定済みの成果物に記録の食い違いが見つかったら、隠さず判定の根拠とともに明記し、直すかどうかを依頼者に確かめる。直したときは、元の状態と直した理由を記録に残す。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:ci-pipeline:fa9b684652e9547fdc00219e301a27d661ffac1f52f9749d17d7bdcac8f883ec -->
## Deployment

<!-- Project-specific specialisation. -->

- 配備先が決まるまでは、基盤の設計を開発者の PC 上のコンテナの範囲に限り、クラウドの基盤（IaC・検証環境・警報の通知の先）は作らない。配備先が決まったときに置き換える前提で書く。 (learned 2026-09-22) <!-- cid:260922-auth-audit-base:infrastructure-design:737809f49e081ec40c56d8179ff3b4129fe694f9d826d020d088e48d06f980a2 -->
- CI の仕組みが既に実装されている段（ci-pipeline など）では、その段の文書を新しい設計ではなく、既にあるものの記録として書く（きっかけ・段の並び・関門の基準・成果物・固定している版）。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:ci-pipeline:389857be7b2081cfcb663fe5dd9db837e751334bfd6626a3f7df4b5df637d023 -->
- CI が必要な検査を実行しているかの確認は、Build and Test が記録した検査の一覧（コマンド）と、CI の段との対応づけで判断する。意図して CI の外に置く検査（E2E など）は、その旨と代わりの実行の場を明記する。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:ci-pipeline:5db3d4deb009c3eebf80febf187717a0b22554d957e7ac8e86ec6cf6c71727a4 -->
- 配備先が決まっていない間の配備の段（deployment-pipeline など）は、既にある Dockerfile・compose.yaml・README の手順を正として記録と整理を行い、決まっていない点だけを質問する。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:deployment-pipeline:b1a86e0f41aa6e3459a2de7b1a258188647faeed5d2b769ede422dbd9702e886 -->
## Code Style

<!-- Project-specific specialisation. -->

- ドキュメントや説明文中でファイル・ディレクトリのパスに言及する際は、絶対パスではなくプロジェクトルートからの相対パスで記述する（例: `aidlc/spaces/default/memory/project.md`）。 (learned 2026-09-22) <!-- cid:260922-auth-audit-base:intent-capture:64b945310a4c7f4ef0d40407fd6e745789656be88a6ef14e9a0b837f7f39aba8 -->
- 機能設計のエンティティには、アプリが独自に持つデータだけを書く。フレームワーク（Spring Boot・Spring Security・Micrometer Tracing・Actuator・SLF4J など）が提供する仕組みや設定値（ログの1件、トレースの情報、接続設定、アクセス制御の設定など）はエンティティにせず、求める振る舞いを決まり（rules）として書く。 (learned 2026-09-22) <!-- cid:260922-auth-audit-base:functional-design:5b233759b75786707968b69af9d5714d020655d16743c718d001c1fbf9b747db -->
## Tech Stack

<!-- Technology choices locked for this project. -->

- フロントエンドはデザインシステム make-you-chic-ui（React + TypeScript）を使用する。Gitサブモジュールとして `vendor/make-you-chic-ui` に取り込み、組み込み手順は同リポジトリの `docs/integration-guide.md` に従う (learned 2026-09-22) <!-- cid:260922-auth-audit-base:approval-handoff:7a1d387f49e374e248043714bbcc0610684f5aed20a6ce859ddbffce95ae8cbc -->
- opentelemetry-logback-appender は 2.28.1-alpha に固定する（Spring Boot 4.1.1 が持つ OpenTelemetry 1.62 と、より新しい版が食い違い、外部エクスポートの有効時に失敗するため）。Spring Boot を上げるときは、この部品の版も合わせて見直す。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:code-generation:a65f53c1e0eafca70f00ce4b9dc6098dc082c5fb53eb56087ea054ce665295c9 -->
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
- 全単位に効く基盤の設定（タイムゾーンなど）を、それを必要とする単位の段で決めたときは、その単位の設計書に優先と反映先（例: U1 の compose）を明記する。保存する時刻はタイムゾーンに依存しない時点（UTC）として扱う。 (learned 2026-09-22) <!-- cid:260922-auth-audit-base:infrastructure-design:b0c7cc7ddec194d695f0f5a709c737043d5361be34e03785ce7fdb89a011abb1 -->
- 質問への回答の組み合わせで決まらない点が残ったとき（例: 手元の WAR とイメージのタグ local では、戻すときの WAR の入手と動いている版の見分け方が決まらない）は、要約の確認の前に追加の質問で埋める。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:deployment-pipeline:fd6fe58e02174dadf871e79604246bb54ee7adbac088df9e51f1c0dcda41ef93 -->
