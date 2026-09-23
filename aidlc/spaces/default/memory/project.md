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
- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:performance-validation:ab2f1782048387afc43d5ecfe6517fd2f601355f934e19d3f106f25a99a14fbb -->
- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:performance-validation:981941a9bbe608353df1979cdc3e9d9c4a10b9fa6cf276c92273fa3011ab4eae -->
- 同時の重なりを確実に作るため、本番のコードを変えずに、監査の書き込みの時間を測る LongSupplier（AuditEventListener で2本目を借りる直前に呼ばれる）をテストで差し替えて待ち合わせる方式にした。既存の LoginConcurrencyIT は 8 スレッドでプールの 10 に届かず、前回の失敗のログインで尽きなかった理由の1つと見られる。 (learned 2026-09-23) <!-- cid:260923-audit-pool-exhaustion:code-generation:9994efaff2db1d6b088324a65881cf41511f0df894e41b24fa6d0ff09f7f10c0 -->
- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) <!-- cid:260923-colima-spec-up:build-and-test:0b433994defeb0970a91e3befaf62e4fef958b5e241106072014bb819309de6a -->
- 修正の前の設定（例: 上限 1g）も修正の後の環境（例: CPU 4 の VM）で流し（pre1g）、要件の前提（VM を上げても F3 が起きる）を実測で裏付ける。 (learned 2026-09-23) <!-- cid:260923-colima-spec-up:build-and-test:90f54d05e33c070ff54875bddded6c447c1c2d8e4d42aeb9de025f608fee1a3a -->
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
- 配備先が開発者の PC 上のコンテナのときは、環境の段（environment-provisioning など）を Docker の実行環境・イメージ・ボリューム・.env・compose.yaml の設定と読み替え、設計の値を実際にコンテナを起動して1つずつ確かめる。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:environment-provisioning:ac6d4ef03ae6ae98e21f41ed4b710aff15e4e1f0e74529d462247e8e8c7a43a6 -->
- 配備先が決まるまでの手元の監視は、grafana/otel-lgtm を compose の profile で見たいときだけ起動し、ダッシュボードと警報の決まりはファイルでリポジトリに置く（画面からは変えない）。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:observability-setup:542822cb3fe5db425d4384bbb06c49af318db761a9d0ab6afe21eb6c39a2fdc2 -->
- CI Pipeline・Infrastructure Design の段が無いため、前の Intent の配備の手順（cd-config・deployment-strategy・rollback-runbook）を正として、今回の差（設定だけの変更、負荷の確かめを配備の前に置く、設定の戻し）だけを書いた。 (learned 2026-09-23) <!-- cid:260923-audit-pool-exhaustion:deployment-pipeline:9945db55da8fa593cd367dda708feaaa4f82e1880b1162da5325289f9eff9fc5 -->
- 戻し方は、設定の値だけの変更であることを生かし、まず .env で上限を 10 に戻す（作り直し不要、ただし F2 が戻る）を第一の手とし、直らなければ直前の版 7040876 へ戻す二段にした。 (learned 2026-09-23) <!-- cid:260923-audit-pool-exhaustion:deployment-pipeline:b6451b7830168d2997b6cf63aeb3c07c3393e836c537a3c788f4f503389f356c -->
- 配備の前の「未コミットの変更が無い」確認は、アプリのソースを対象とし、監査ログとこの段の記録のディレクトリ（ワークフローの記録）は外して判断した。 (learned 2026-09-23) <!-- cid:260923-audit-pool-exhaustion:deployment-execution:3d67f0638e47408d3b6b0958e7ff3bc42f3084cbe4562be73c13e52a103f5a8e -->
- colima の VM が 2GiB のため、使い捨ての環境（1g）と配備したアプリ（1g）を同時に動かせず、負荷の確かめのあいだは前の版のアプリを止めた。確かめが通らなければ docker compose start で前の版をそのまま起動し直せるよう、up ではなく stop にした。 (learned 2026-09-23) <!-- cid:260923-audit-pool-exhaustion:deployment-execution:6523e4ea564581544d814a40c7e6bf64cf16bf00df416f91da0a332805720b87 -->
- JVM の設定の口に JAVA_TOOL_OPTIONS ではなく独自の MASTERSMITH_JAVA_OPTIONS を ENTRYPOINT の既定の引数の後ろに置く形を選んだ。標準の変数はコマンド行の 75% に負け、起動時の Picked up の1行で JSON のログを崩すため。 (learned 2026-09-23) <!-- cid:260923-colima-spec-up:code-generation:5bee00b6c6fc0ff963eca9371e14bc5dc3c8e19d69c2ccf04583df03488e0340 -->
- 戻しで .env の元の値が要るときは、.env を開かずに、変更の前に .env をリポジトリの外（ホームの下）へ中身を表示せずに複写し、戻すときはその複写を戻す。 (learned 2026-09-23) <!-- cid:260923-colima-spec-up:deployment-pipeline:1d22caeb1205783cdbc97baa8745e5db11703baa067f63ffd6ad472f8ccc968e -->
- Build and Test で、配備するものと同じソースのイメージを配備と同じ上限で負荷の試験済みのときは、依頼者の判断（Q1: A）で配備の前の k6 を省き、配備の後の healthy とスモークテストで確かめる。 (learned 2026-09-23) <!-- cid:260923-colima-spec-up:deployment-pipeline:7d388e8869c0f1041f0b6003ba34d70530499d4da273d77b23819e54cf862e1c -->
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
- 依頼者の判断で、2本使いの解消（接続を返してから記録する・監査専用のプール）ではなく、プールの上限の引き上げを選んだ。コードと既存の監査の決まりは変わらない。その代わりに、同時の数が上限（30）に達すると再び起きうるという危険が残り、要件（FR5）と README に既知の制約として記録する。 (learned 2026-09-23) <!-- cid:260923-audit-pool-exhaustion:requirements-analysis:212bb4696c70283d6edbf364e5565121bf51cabe7f1c875fdf9d296fe94f247a -->
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
- NEVER 対象DB の接続情報（接続先・ユーザー名・パスワード・資格情報を含む JDBC の URL）を、生成した DSL・プレビューの応答・画面・ログ・監査ログ・エラー応答に含めず、画面・API・DSL から受け取らない（設定だけから受け取る） (affirmed 2026-09-23)
- NEVER JDBC の例外や、YAML・JSON Schema の部品の例外のメッセージを、そのままエラー応答に含めない (affirmed 2026-09-23)
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
- ALWAYS 対象DB の結合テストは、版を固定したイメージのコンテナで起動した実際の MySQL・MariaDB・PostgreSQL で行い、H2 などの別の DB やモックで代用しない (affirmed 2026-09-23)
- ALWAYS 利用者が投入する DSL（YAML）は信頼できない入力として扱い、大きさ・入れ子の深さ・別名の数の上限を明示し、タグと任意の型の生成を拒否し、重複キーをエラーにする (affirmed 2026-09-23)
## Corrections

<!-- Project-specific corrections from human feedback. -->
<!-- Format: NEVER/ALWAYS [behavior] (learned [date]) -->
- 全単位に効く基盤の設定（タイムゾーンなど）を、それを必要とする単位の段で決めたときは、その単位の設計書に優先と反映先（例: U1 の compose）を明記する。保存する時刻はタイムゾーンに依存しない時点（UTC）として扱う。 (learned 2026-09-22) <!-- cid:260922-auth-audit-base:infrastructure-design:b0c7cc7ddec194d695f0f5a709c737043d5361be34e03785ce7fdb89a011abb1 -->
- 質問への回答の組み合わせで決まらない点が残ったとき（例: 手元の WAR とイメージのタグ local では、戻すときの WAR の入手と動いている版の見分け方が決まらない）は、要約の確認の前に追加の質問で埋める。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:deployment-pipeline:fd6fe58e02174dadf871e79604246bb54ee7adbac088df9e51f1c0dcda41ef93 -->
- 環境に関わる段では、質問を作る前にその PC の実行環境（例: colima の CPU・メモリ、既存のイメージ・ボリューム・.env の有無）を読み取りだけで調べ、設計の値を満たせない点を質問にする。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:environment-provisioning:e1e4bbd98bd4bc930b9341961e8cb33bada2efe1658186645fc558cbd0525e9e -->
- 手順書で予定した確認（例: 戻しの練習）を依頼者の判断でやめたときは、未確認のまま残る前提と、次に確かめる機会をその段の成果物に記録する。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:deployment-execution:b34ddfa7ebe1fb62622536b635e15d834ec86cf9b982bd0eccce25802001053e -->
- パスワードなど秘密情報が要る操作（例: 初期管理者でのログイン）は依頼者が行い、AI は監査イベントとログで裏付ける。個人に関する値は表示せず、値の有無だけを確かめる。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:deployment-execution:f8f1f6c69411467ee65e09363c7644e62aa7d351e1373c303c05319b3da7fbb4 -->
- 警報やダッシュボードの式は、書く前に実際に起動して指標・ラベル・ログの項目の名前を確かめ、書いた後にすべての式を実行して正しいことを確かめる。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:observability-setup:29742d589973c4f5111245570e090fe1480ffcf5fdd38bc811adbe404efec989 -->
- 確認のために送る要求が監査ログに残る（追記だけで消せない）ときは、送る前に依頼者に伝える。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:observability-setup:372ae4e79606cc709aee169b5d5dcc5f8702f2b408f108142e0b99820f40bc80 -->
- 要求1件で接続を2本使う経路（確定の後の監査の書き込みなど）があるときは、同時の数がコネクションプールの上限に達する場合を、設計の見積もりだけでなく必ず負荷の試験で確かめる。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:performance-validation:40555675680aff89713df315f029a4ce1e4f0ddda325781eaf44dd4680d05af4 -->
- 最初のコード知識ベースのため全体を対象に調べたが、深く読んだのは F2 に関わる範囲（ログイン・ログアウト・監査・接続の設定・関係するテスト）だけなので、記録上の範囲は partial とした。画面側や共通部品は流し読みの扱い。 (learned 2026-09-23) <!-- cid:260923-audit-pool-exhaustion:reverse-engineering:b0cbc892459ac4ff65e23667cf74963257af00f488b36486f444afba9daba7d7 -->
- 前の Intent では、監査を別スレッドに移すとトレースIDと「確定の後に記録」の決まりが変わるため、接続を2本使う形を受け入れていた。直し方を決めるときは、この決まりと既存の結合テスト（AuditAuthenticationEventsIT・AuditTraceIdIT・AuditRollbackIT・AuditWriteFailureIT）を守れるかで比べる。 (learned 2026-09-23) <!-- cid:260923-audit-pool-exhaustion:reverse-engineering:15498ffe61fe6ff56f34e8d23e9d51408e48990f74de543a12304ade5b25f156 -->
- 待ち合わせの上限を計画の例 30 秒から 20 秒にした（上限 10・N=20 の試しで HTTP の要求の時間切れ 30 秒が先に来るため）。DataSourcePoolIT に「既定で 30 本を同時に借りられる」テストを1件足した。 (learned 2026-09-23) <!-- cid:260923-audit-pool-exhaustion:code-generation:fd57c64b6eb6841155789942a7e285a8bf7d706736c3c562d34150b2adf8d3da -->
- 要件 FR6.2 は「配備した後」に k6 で確かめるとしているが、依頼者の決定（Q2: A）で配備の前に行う。要件は書き換えず、cd-config.md 4節に差を明記した。 (learned 2026-09-23) <!-- cid:260923-audit-pool-exhaustion:deployment-pipeline:41777a07c42db043bcf8c7e0129e93c36b63ac4ace7b61ee9a825fc546cf8135 -->
- 今回の範囲に絞ったスキャンの対象を、コンテナ・JVM・負荷試験・README・接続プールの設定・LoginService とした。F3（メモリ）と F4（CPU とログイン）に直接関わる設定と手順だけを深く読み、ほかは流し読みの扱いにした。 (learned 2026-09-23) <!-- cid:260923-colima-spec-up:reverse-engineering:5e13085dcbd2dc454021b33a19064567921546b0997a4f88aee69c8c9bd0e578 -->
- 全体の再スキャンではなく範囲を絞ったスキャンを依頼者が選んだ。前回の深い範囲（監査・接続）は再確認できないため流し読みに下げ、記録上の範囲は狭くなった（NARROWER）。 (learned 2026-09-23) <!-- cid:260923-colima-spec-up:reverse-engineering:5e7311e6bf785d2be64bd56c48e5a95eb8cb390053153f9278bd0428ec2ed393 -->
- VM の拡張だけでは F3 が直らない（mem_limit 1g が固定）ことをコードの調査で示し、依頼の文言（VM の性能を上げて直す）より広い変更（上限と JVM の設定の口）を質問で選んでもらった。依頼の文言に合わせて VM だけにする案も選択肢に残した。 (learned 2026-09-23) <!-- cid:260923-colima-spec-up:requirements-analysis:1789e8a46d20c3bb00bfbefb0e82996ccffe53e875260ca780fb6e6df5a42be8 -->
- 単位の分割が無い bugfix のため、VM の作り直し・F3 の内訳の測定・k6 の試験は Build and Test、.env の変更は Deployment Execution に回し、この段は設定・確かめのスクリプト・文書に限った。今の VM（2GiB）で配備したアプリを止めずに確かめられる範囲にするため。 (learned 2026-09-23) <!-- cid:260923-colima-spec-up:code-generation:766ebe01c2f2af7b9cc82dbb3507af461cd1806771c8ad9591050677b5170679 -->
- レビューの依頼の後にレビュー役が git diff を実行し、作業フォルダが変わったと判定されて結果を記録できなかった。確認の回数も尽きたため、依頼者の Request Changes（traceability.json の R-01・R-02 の修正）を経て、git・ビルドを触らない指示で再レビューした。レビュー役には、git・gradlew・docker build を使わず Read だけで確かめるよう最初から指示する。 (learned 2026-09-23) <!-- cid:260923-colima-spec-up:code-generation:4658f175314924d35f961e4e63d652159b81dd720ba383241c3c06c29f73e0cf -->
- colima の VM のコンテナとファイルを受け渡す一時の場所は、mktemp -d（macOS の一時ディレクトリ。VM から見えない）ではなくホームの下（権限 700）に置く。監査イベントの確認の複写が空振りし、アプリを2回止めることになったため。 (learned 2026-09-23) <!-- cid:260923-colima-spec-up:deployment-execution:3e57cc185ad8a539725ead969745a4338d6edbb64f04ae94bbfb037cca0d541f -->
- 依頼者は全体の読み直し（Full rescan）を選んだが、記録上の範囲は実際に深く読んだものだけ（kind: partial、77パス・17部品）とした。コンテナ・負荷試験・監視の設定と README は今回の Intent に関わりが薄く流し読みにしたため、前回（colima-spec-up）より範囲が狭い（NARROWER）と判定された。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:reverse-engineering:66e0c533baba6b3eff4b377b85c707c50707b85055c58fad07e6ff83de0f921c -->
- アーキテクトが開発者のスキャンにあったパッケージ間の依存を import の検索で確かめ直し、3点（audit は common.observability に依存しない、access は config に依存する、auth は common.observability に依存する）を訂正して記録した。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:reverse-engineering:95881d5d834a59c49e064f7deacbd71812e1eaa719b3a04a6fa52634b2a96e4c -->
- 部品の名前を英数字の ID（auth・frontend-app-core など）にし、Scope of Analysis の components と component-inventory.md の見出しを文字どおり一致させた。照合は安定するが、前回の日本語・クラス名の部品名とは一致しなくなり、比較で部品が「失われた」と表示される。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:reverse-engineering:3a2dcce51a6e79ec9aff8f865d7208b35839ce466e60ba15c7c61da4b719846d -->
- 再実行のため、支援役3名が挙げた約30の論点のうち、チームの進め方に当たるものだけを9問にし、「適用」の中身・接続設定の足し方・パッケージの切り方・E2E の2本目・検証エラーの形は要件・設計の段に回した。Q4 の「すべてのパッケージ」はバックエンド（JaCoCo のパッケージ単位）だけに当てると解釈した。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:practices-discovery:8fab6616a98835ce774a51cadf44f3041a595ddc845762f476c0138c4b88d8ff -->
- Q3（コンテナが無ければ飛ばす）と Q4（すべてのパッケージに下限）の答えが team.md の「全検査を通してから統合」「除外を増やさない」と食い違いうるため、追加の質問 F1・F2 で確かめた。まとめの確認の場で依頼者が F2 を B（既存が下回れば新しいパッケージだけ）に変えた。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:practices-discovery:d0cee141fa53bfbe206d69de6a9ec345f3da8322a543253fd9fb2c769eee5fc1 -->
- 統合で、面談で聞いていない初稿の行（対象DB の設定の型の伏せ字、401／403／200 のテスト、Flyway V5、Playwright）は基準から外し、evidence.md に記録した。基準は依頼者が確かめたことだけにし、今回の Intent だけに関わる事項は要件・設計で扱う。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:practices-discovery:de2e0b3c606110dc97885886e4265a0ecbcf3b7213d178b7f3b6625633dc6eaa -->
- Q5（プレビューにエラーの一覧を見せる）と Q6（検証を通らない DSL はプレビューに置かない）が食い違うため、追加の質問 F1 で「エラーは投入の結果として見せる」と確かめた。あわせて履歴の件数（F2）と、プレビューの置き換えと同時の操作（F3）も追加で確かめた。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:requirements-analysis:3d8c8d9478dec2ab23934427b859ac71b5969e80dd81da19cadfbb7c9a4c2f10 -->
- 質問は Standard の目安（5〜8）より多い12問＋追加3問にした。Intent A・D・E を1つにまとめており、資料で決まっていない範囲の境目（適用の意味・投入方法・保存・履歴・照合・監査・接続できないとき）が多いため。DSL の具体的な構造や型の対応の規則は設計の段に回した。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:requirements-analysis:ee393b7357aac385e8aa66fbf5e5d7fd21c34d41a086552fcadc6c84ccf1c504 -->
