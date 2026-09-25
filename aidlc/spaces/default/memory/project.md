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
- パッケージごとのカバレッジの下限と SpotBugs の SQL_ の関門を U1 の計画に入れた。team.md の決まりだが今のビルドに無く、この Intent で最初に作る単位のため。U1 の設計の文書には無い作業。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:code-generation:f66b8e2ef92e2bcac74ebaab2f46d6365d0b1ba0e9a1d87063ca2bb06622cba0 -->
- U4 で既存の AuditSecretLeakIT の列の一覧に V6 の4列を足し、テストの JVM のヒープを 1g にした。前者は承認済みの V6 と必ず食い違うため、後者は構造の検査がクラスを持ち続け U3 の 10MB 超えのテストでヒープが尽きたため。どちらも計画に無い変更で、依頼者に確かめる。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:code-generation:2fc093e5a02d5ebb999fb83481a41710222b3f0a07fcf066eccd001329f4bac7 -->
- 応答しない対象DB の TIMEOUT の確かめに、テストの中で開いた ServerSocket（受け付けて何も返さない）を使う。外の端末に頼らず確実に再現できる代わりに、本物の DB の遅延ではない。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:code-generation:ed7ab1daca4fa077e39042a6faee655336c1911356dc58405498a185143f8e92 -->
- パッケージごとのカバレッジの下限は新しいパッケージだけに当てた。実測で audit.service（行 77.2%）・common.health（行 79.2%）・auth.repository（分岐 50.0%）が単独で下回ったため（team.md の決まりどおり）。既存の 22 パッケージを一覧で外し、新しいパッケージは自動で対象になる。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:code-generation:3e576ee8cb983e190e8b6b8fb776e790d8fe306cfdb35014e2d1c89322ed9dd7 -->
- 負荷の試験で接続プールが尽きたかを確かめるときは、使い捨てのアプリにだけ MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics を渡して /actuator/metrics の hikaricp の値を読み、数秒ごとの使用中の数ではなく、待ちの時間切れの累計と借りるまでの待ちの最大で判断する（配備したアプリの公開の範囲は変えない）。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:performance-validation:6183459b552b1e347df4f86017641db5c56a4349202caff390c55c193c899525 -->
- k6 などの長い試験は caffeinate -i を付けて流し、PC の自動のスリープで要求が止まって結果が崩れるのを防ぐ（バッテリー駆動のまま 414 秒スリープし、要求が 6分52秒止まった）。内部DB に SQL で直接入れた試験用の利用者はロックの状態の行が無いため、同時のログインを流す前に1人ずつログインさせて行を作る。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:performance-validation:f0c52a1398842c59feab5bedd7239515fc40d48a12f80d7e96034db103864add -->
- 軽い API の性能は、投入を重ねて内部DB のファイルが膨らんだ状態（悪い側の条件）のまま測る。メモリの最大（memory.peak）を比べる試験の前は、アプリのコンテナを作り直して前の最大の値を消す。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:performance-validation:b9c43187ce6f38e701503c4be8cbbe11c162c17cd1cb8b6739f6f7ddffc4ca74 -->
- Performance Validation の段が無いため、2件目の直しの負荷の試験での確かめ（Q5: A）の持ち主を Build and Test とした（project.md の学びどおり）。 (learned 2026-09-24) <!-- cid:260924-followup-fixes:requirements-analysis:edd0566de8d745298681f86ffd92eaa003402622695282d848392f1b7c9214ac -->
- colima の PC で ./gradlew verify を流すときは、README の DOCKER_HOST と TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE をシェルに渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗した。付けて流し直した結果を基準とした。 (learned 2026-09-25) <!-- cid:260924-followup-fixes:code-generation:0a4cb9ad3d6a5ebd80fe91acc5f4cc91f09a822150d200f9575eb6f2cedb4828 -->
- unit-test-instructions.md の k6 inspect のコマンドは --include-system-env-vars が無いと場面の名前が undefined になり確かめにならなかった。承認済みの文書は変えず、実際には付けて流し、code-summary.md に差を記録した。 (learned 2026-09-25) <!-- cid:260924-followup-fixes:code-generation:068f5e9bd3cb7323b66538b382654b3f47845c2d93a731055ad7f42ea01b6fc8 -->
- 1回目の verify で AccessTokenApiIT の 6 件が接続の失敗で落ちた。変更の経路に触れず、同じ時刻に Gradle の作業プロセスとの接続も時間切れだったため PC の負荷による一時的な失敗と見立て、段の中の直しの1回目としてコードを変えずに verify を流し直して通した。原因は確かめていない。 (learned 2026-09-25) <!-- cid:260924-followup-fixes:build-and-test:89def63d81d3ea5433f37ddeb523b0e1717eb00818d68775428f4ef43df53854 -->
- 依頼者の判断で、試験のイメージを mastersmith:local ではなく mastersmith:followup-fixes で作り、perf/README の手順（local を作り直す）から外れた。配備したアプリは k6 のあいだ止め、約 17 分後に同じコンテナで起動し直した。 (learned 2026-09-25) <!-- cid:260924-followup-fixes:build-and-test:aee16284a3cef2c44d558369fb16a72cd2f17e98dda591a3b51e238a0b25909a -->
- 試験の後にロックの状態の行の数を数える問い合わせの列の名前を誤り、使い捨ての環境を消した後で取り直せなかった。合格の条件（ログインの checks と 500 の件数）で判定した。消す前に確かめの結果を見てから片付けるべきだった。 (learned 2026-09-25) <!-- cid:260924-followup-fixes:build-and-test:147c0e0eaef76c0e3a8476984863843c4ea89bb16c1e2f76e33ee0abf3050bbc -->
- Q4（確かめられたときだけ直す）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違いうるため、追加の質問 F3 で、再現できなければ不安定と確かめられていない扱いとして統合してよいことを確かめた。 (learned 2026-09-25) <!-- cid:260925-storage-memory-fixes:requirements-analysis:a23fdf740efe5d8068f7ef6f16866a3588ae6a67c46166049f9e197f23e934f3 -->
- 要件 FR2.2（目標は計画の承認の場で決める）を満たすため、依頼者の決定（Q1: A）で、計画を書く前に dslMixed でメモリの内訳を測ることにした。測る間は配備したアプリを止める。 (learned 2026-09-25) <!-- cid:260925-storage-memory-fixes:code-generation:760ae81ebee8c49c5d838bf07a3633f6804a32f4ec204a9d02c089969b592a8c -->
- 生成で、テストの既定で MBean の登録を無効にする置き場を、計画の候補の TestDatabase ではなくテストの EnvironmentPostProcessor にした。TestDatabase を使わずに Spring を起動するテストのクラスが 15 あり、漏れるため。再現の結合テストは 12 回では伸びが小さく判定がはっきりしないため、24 回・履歴の上限 3 にした。 (learned 2026-09-25) <!-- cid:260925-storage-memory-fixes:code-generation:a0332a6724b6141e08621eb05434ac0373297bd2e50938a432a9fd83c4d3ffca -->
- AccessTokenApiIT は、同じ例外の文言を仕組み（colima の IPv4 の転送と Java の IPv6 の待ち受けの番号の重なり）ごと再現できたことを原因の確認とみなし、依頼者の決定（D7）でテストの JVM に preferIPv4Stack を付けて直した。実際の1回目がこれで起きたかは確かめられず、今回の繰り返しでは重なりは起きなかった。 (learned 2026-09-25) <!-- cid:260925-storage-memory-fixes:code-generation:6bdd5f35b23343f9b0a99488e0933b8c9fdc8da1eb2b961a69c6005ec1c34f8b -->
- Test Strategy は Minimal だが、要件 FR5 と計画の「Build and Test に引き継ぐこと」のため、結合・性能・セキュリティの手順書も作り、この段で dslMixed・--storage --compact（40 回）・詰め直しの最中のログイン・refresh・dslCycle を流した。 (learned 2026-09-25) <!-- cid:260925-storage-memory-fixes:build-and-test:4e7373ea4a2eb5760ac5e59036d9abc6e57399692d9ed00090a62bc8e13b283f -->
- 計画の dslCycle を最初の台本に入れ忘れ、配備したアプリをもう一度（約 4 分）止めて流した。負荷の試験の台本を書く前に、計画の「Build and Test に引き継ぐこと」の項目を一つずつ台本の手順と突き合わせる。 (learned 2026-09-25) <!-- cid:260925-storage-memory-fixes:build-and-test:2e944db2a96ad1aaaef48adaaba593f5fdf096c086a8dc8d68cad5e392ecb12c -->
## Change Control

<!-- Project-specific. Mode: strict or relaxed. Strict here holds for every intent and cannot be changed from chat. -->

- コミットはこまめに、ファイル変更のまとまりごと（回答確定時・成果物作成時・内容確認/承認時）に行う。コミットのタイミングは提案し、実行前に必ず人間の承認を得る。コミットメッセージは日本語で記述する。 (learned 2026-09-22) <!-- cid:260922-auth-audit-base:intent-capture:822f19c942c585b350586f2ad3ecf1660487d52354264177a5c424103985dc8a -->
- 確定済みの成果物に記録の食い違いが見つかったら、隠さず判定の根拠とともに明記し、直すかどうかを依頼者に確かめる。直したときは、元の状態と直した理由を記録に残す。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:ci-pipeline:fa9b684652e9547fdc00219e301a27d661ffac1f52f9749d17d7bdcac8f883ec -->
- 計画の Step ごとのコミットの提案は、生成の担当ではなく、生成の後に依頼者の承認を得てまとめて C1〜C6 に分けて行う形にした。担当は依頼者に直接尋ねられないため。統合は計画どおり短命のブランチ fix/260924-followup-fixes から fast-forward（team.md の squash とは違う。サブモジュールの専用のコミットを残すため、計画の承認で受け入れられた）。 (learned 2026-09-25) <!-- cid:260924-followup-fixes:code-generation:87c26f259ce9c6a4de73518651b6f0e868ab12453a73de73f50f5fe768dd262c -->
- develop へ取り込む前に、この段の質問と確認の記録を依頼者の承認なしでコミットした（581b006）。依頼者に伝えて了承を得た。コミットは段の記録であっても、実行の前に必ず提案して承認を得る。 (learned 2026-09-25) <!-- cid:260925-storage-memory-fixes:deployment-execution:bfba46b7cf01ba6b124b43122d6b501702ed308cc28e7c0340aea762d39c0b8f -->
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
- compose の app のサービスに対象DB の環境変数を足さず、.env.example に足すだけにした。app はすでに .env を env_file で読むため。基盤の設計（cicd-pipeline.md 3節）の書き方とは違う。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:code-generation:9cee5b93f37740922dc363b3d0600e4250adfef1c03ada3ea807df5783ab181e -->
- 配備先が開発者の PC 上のコンテナの間の Feedback & Optimization では、費用（Cost Explorer・Trusted Advisor）を VM・コンテナの資源の上限と実測に、設定のずれ（AWS Config）を docker inspect・docker compose config・.env の項目の有無（値は見ない）と、記録した設計の値との1つずつの比べに読み替える。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:feedback-optimization:2092e4de75ef6ca51d922bf2ba8a0bd22190e5194288dfbedaee190202c22398 -->
- 手元の監視を常に動かしていない間は、SLO の判定を Unverified とし、配備の直後・監視の確かめ・負荷の試験・振り返りの時点の値を基準の値として並べ、配備先が決まったときの測り方を書く。目標を緩めて満たしたことにはしない。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:feedback-optimization:fa7ca0460f9a1e1941002f1251ad0d8bece205d0aeef0076a63aaceb8a3b14b6 -->
- Q2: A（AI が .env から2行を移す）と Q4: B（戻すときにイメージと .env を戻す）の組み合わせでは、戻すとアプリが再び見本の対象DB の管理者のパスワードを持つため、追加の質問 F1 で確かめ、戻すのはイメージだけにした。 (learned 2026-09-25) <!-- cid:260924-followup-fixes:deployment-pipeline:d3116be97e057542dbcb08021c0ad2975e9595d143eabdeabd4fe61cd0dcb1d1 -->
- 配備の前の k6 と内部DB のバックアップは質問にせず、決まっていることとして書いた（k6 は project.md の決まりで Build and Test の結果を正とする。バックアップはスキーマの変更が無いため不要）。 (learned 2026-09-25) <!-- cid:260924-followup-fixes:deployment-pipeline:0675c59d7ff8841998ab55a88372b2dff3d387b0d28e132210f8ab3d49e4d5db -->
- 戻し方の前提（Q2: A）は、前の版のイメージを新しい .env と一時のボリュームで起動して健全になることで確かめた。配備した内部DB のデータでの起動は確かめていない（スキーマの変更が無いため）。 (learned 2026-09-25) <!-- cid:260924-followup-fixes:deployment-execution:96ee1f52a4e60ff3af1808774205c8491cd15a07a159f5eb7833321ea912b251 -->
- 前の版のイメージは HikariCP の JMX を有効にしていないため、戻すと詰め直しの道具が使えなくなり最大ヒープも 75% に戻る。設定がイメージの中にあるためイメージだけで戻せる代わりに、戻したときの運用の差を戻しの手順に書いた。 (learned 2026-09-25) <!-- cid:260925-storage-memory-fixes:deployment-pipeline:7b476360b6c318ef99d4a013d925563cd9a28cf632612b083d7cfb448e8cf622 -->
- 戻し先のイメージの確かめで、イメージに HEALTHCHECK が無く、起動の直後の running を健全と誤って判定しかけた。compose の健全性の確かめと同じ bash の /dev/tcp で /actuator/health の 200 を待つ形でやり直した。 (learned 2026-09-25) <!-- cid:260925-storage-memory-fixes:deployment-execution:b351b2a240157b9e52139413dc379757850591942093ec3c00802300883910da -->
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
- NEVER 配備先が決まるまで、実在の宛先や外部の SMTP へメールを送らない（手元でメールを見るときは、メールを受けて画面で見せるだけの受け手を compose の profile で起動する） (affirmed 2026-09-25)
- NEVER 招待のトークン（招待の URL に含まれる値を含む）を、ログ・監査ログ・トレースの属性・エラー応答に含めない (affirmed 2026-09-25)
- NEVER メールアドレスをアプリのログとエラー応答に含めず、メールの部品の例外のメッセージと SMTP の応答をそのままエラー応答に含めない (affirmed 2026-09-25)
- NEVER メールの部品のデバッグ出力（`mail.debug`）を有効にしない (affirmed 2026-09-25)
- NEVER メールの本文に差し込む利用者の値を、HTML としてエスケープしない差し込み（Mustache の `{{{ }}}`・`{{& }}`）で入れない（エスケープされる差し込みだけで入れる） (affirmed 2026-09-25)
- NEVER 招待の URL を要求の Host ヘッダーから組み立てない（設定したベース URL だけから組み立てる） (affirmed 2026-09-25)
- NEVER `vendor/java-mustache-processor` の中身をこのリポジトリから直接変更しない（変更は java-mustache-processor のリポジトリ側で行う） (affirmed 2026-09-25)
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
- ALWAYS SMTP の接続先と資格情報は環境変数（`.env`）だけから受け取り、接続先の設定が無ければメールを送らない (affirmed 2026-09-25)
- ALWAYS 招待のトークンは内部DB にハッシュ値だけを保存し、1回だけ有効で有効期限を持たせる (affirmed 2026-09-25)
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
- ダウンロードを Should にした答え（Q5: B）が、要件でプレビューに YAML の本文を表示しないこととの組み合わせで、既定の DSL を取り出して直す流れを壊すため、追加の質問 F1 で確かめ、プレビュー中の DSL のダウンロードだけを Must にした。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:user-stories:db488139c12d7530e9f2221d196e751108c558b39fc0a19bc93381fbf9e2b520 -->
- mob の3人の意見のうち、判断が分かれる4点（US1.1 を分けて16件にするか、受け付けなかった投入の監査、同じ内容の再適用、適用と破棄の前の確認）だけを依頼者に尋ね、残りの指摘（受け入れ基準の境界・判定の仕方・依存の循環の解消・監査の基準を各操作へ移す・履歴の書き込みを適用へ移す など）はそのまま取り込んだ。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:user-stories:d7668056490dada75b2398c58d25697f64c704b1227516749fc690706c57d85d -->
- 合否の判定の仕方（状態コードと code、「変わらない」「含まれない」の確かめ方、3種類の DB、性能の測り方、画面の共通の決まり）を、各受け入れ基準に繰り返さず「前提と読み方」に一度だけ書いた。基準は短くなるが、読む人は前提を合わせて読む必要がある。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:user-stories:2ecbf8b36511bbf87b212b36ff55fbbbc0697adac0d472676e33cf5d9e24a5f3 -->
- classic の範囲で Ideation のラフな画面イメージと利用者の流れが無いため、画面イメージはストーリーと要件から直接作り、無い成果物の中身は作らなかった。質問はストーリーで決まっていない画面の組み立て（構成・入り口・入力・違いの見せ方・誤りの件数・幅・WCAG・処理中）に絞った。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:refined-mockups:67cde05df4a34d082040aff941e4aca0373c38ad1093d48933d1ab0fa4efc8c3 -->
- make-you-chic-ui に無い部品（ファイルの選択・違いの表の行の開閉・メニューの木）は、サブモジュールを変更できないため frontend の側で作る方針とし、後続の Intent でも要るなら make-you-chic-ui 側への追加を依頼者と相談する、と記録した。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:refined-mockups:c7b9f95a495c60d5232798273b56a48c5aa8414628581053a30d2f80fbb5ab86 -->
- 確かめる表示（Modal）は背景のクリックで閉じない・はじめのフォーカスを「やめる」に置くことにした。取り消しにくい操作（適用・破棄・置き換え）の誤操作を防ぐ代わりに、操作の手数は1つ増える。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:refined-mockups:b6fcdc8249bbb8139df3e604ec60dc795d0ee3d1d7112fc6171b1531a1b19d4e -->
- 実現可能性の評価（Feasibility）の段が無いため、ADR-010 に実現できるかの判断と、確かめる条件と持ち主の段（NFR 要件・最初の Bolt・Build and Test）をまとめた。ADR-006（既定の候補にしない指定）と ADR-008（位置の対応表）には、確かめられなかったときの切り替え先を書いた。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:domain-design:695e108547432093d2ecb3f2300bae5a5bc29bca78b150991ddf716a82f1b887 -->
- 既存の部品は、前の Intent の Domain Design の部品名（UserAccount・AccessControl・AuditLog・AppFrame・ApiClient）で書いた。今回の Reverse Engineering で作り直したコード知識ベースの部品名（user・access・audit・frontend-* など）とは対応表を置いていない。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:domain-design:47fd6c22dccd46d4a30875cc09d88d48460b44530d74ee527b51a03377a93fbb -->
- 後続の Intent が dsl に依存し dslmanage に依存しないよう、ActiveDslModel は保持と提供口だけを持ち、起動時の読み込みと差し替えを DslLifecycle に任せた。依存の循環は無くなるが、モデルの中身が起動の順序に依存する。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:domain-design:83b75262e9c8e17b65e503f39901dd822d899e662a80ba5fd50fe80abdcb1aed -->
- 対象DB（U1）・DSL の定義（U2）・既定の DSL の生成（U3）は、単独では動かずアプリの中で使う部品なので種別を library とした。API を持つ DSL の管理（U4）だけを service、画面（U5）を ui とした。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:units-generation:912efd0c7fa7ccc04075eaf60a7f268cfd0fe3fb67c1ad63bf02dac71c327ad8 -->
- 依存の上では U1 と U2 を並行して作れるが、依頼者の判断（Q4: B）で依存の順に1つずつ作る前提とし、依存の図には並行できることだけを記録した（順序は Delivery Planning）。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:units-generation:74ba684373b7e83194f639022f770e0f6ff0066b7a3648e8e8532fa53b92879e -->
- 外部に公開する API が無いため、契約は依存の6本に、画面との HTTP（C6）・監査への出来事（C7）・後続の Intent への提供口（C8）を加えた8本とした。後続の Intent への提供口は、外部の利用者として扱った。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:contract-design:d3fe9cb4f375fee36dc4f2c17455dc28bc6010f3b0a66f8bc8ee8bfc66542f9c -->
- 既存のエラー応答の仕組み（ProblemType は code と状態コードを1つに固定、GlobalExceptionHandler は BusinessException から応答を作る）を十分に確かめずに契約を書き、同じ code を 404 と 409 の2つで使う形と、結果の型から応答への変換の経路が決まっていない形になった。レビューで指摘された。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:contract-design:5294ab39131c497db4165560e3bb2a2cc510dd5355e8697778d35f29cf7033b9 -->
- アプリの中の部品の間では、想定内の失敗を結果の型で返す形（Q5: A）を選んだ。場合分けの漏れを型で防げるが、既存の例外から応答を作る仕組みとの橋渡し（U4 の中の変換）が要る。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:contract-design:1fa11b818fc53f08ae1f7acb678e0495efabc7930516075cc494170c89484c96 -->
- 依存がほぼ一本道で順序の選択肢が U1 と U2 の前後だけのため、WSJF の点数付けは使わず、危険の大きいものを先にする考え方だけで順序を決めた。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:delivery-planning:62def213df31046e4d9b36bfa76938689eded13f4c065654bc1250bdb3ca35cf -->
- 依頼者が気がかりとした2点（ADR-008・ADR-006）を最初に確かめたいが、設計の段ごとに全単位を通す進め方ではコード生成が最後になるため、NFR 要件の段で本番とは別の試しのコードで小さく確かめることを、まとめの確認に加えて承認を得た。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:delivery-planning:e6089693d5b37d86d5490168c932b669cf32e41157b8b42f08d969e830a9bf67 -->
- U4 を Must の B4 と Should の B5 に分け、Bolt を6つにした。1つの Bolt が小さくなり Should を切り離せるが、同じ単位を2回に分けて統合することになる。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:delivery-planning:3ae4bde50d6c8df2bbda81659fb4f1e1fbd9d91c464eb9fe8bd8163c1e237398 -->
- U2・U3・U4 のレビューで、契約（C6・C7・C8）や前の単位の決まりとの食い違い（カラムの違いの区分の値、受け付けなかった理由の名前、DSL の識別の欠け、別のスキーマの外部キーの扱い）が指摘された。単位の設計を書く前に、その単位が触れる契約の列挙値・項目と、前の単位の決まりを突き合わせる。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:functional-design:c9f8c45b5f5649ee8b3b8f70474bde805333a67f7a45d2541b48645118e50b0d -->
- U1 は要件・ADR-006・契約 C1 で論点がほぼ決まっていたため、質問を作らず設計の要点の確認（Looks correct）で進めた。接続先の設定は Spring Boot の設定の値としてエンティティにせず、決まり（BR1.x）として書いた。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:functional-design:f9890676d80ce0e4d031b89206f348c7190d956ce26c3d81bc560e71e73a9942 -->
- U5 の要約では部品の構成を functional-spec.md に含めるとしたが、ui の単位には frontend-components.md が求められる成果物だったため、レビューの後に部品の構成・画面の状態・API の受け渡しを frontend-components.md に移した（決まりの中身は変えていない）。要約の前に、段の定義で単位の種別ごとの成果物を確かめる。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:functional-design:47a2a51fc757b60ecd39a0ae733004d5cbddf4d62cf5d0b67d6613b48de253eb -->
- AC6.2.1〜AC6.2.3 の一括の確かめ（11 本の API のサーバー側の結合テスト）を、単位の分割表のとおり U5 に置いた。U5 の境界（画面と ApiClient）とずれるため、どの Bolt・どのパッケージで書くかはコード生成の計画で決める（レビューの R-01）。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:functional-design:0411baeb721c2ca227457ec40dc9535018c41da30b3351ec9038a17584841f58 -->
- 部品の候補は Maven Central で最新の版と依存を確かめてから試した。networknt の最新の版 3.0.7 はアプリ全体の Jackson を Spring Boot の版から引き上げるため、Spring Boot と同じ 3.1 系で動く 3.0.6 を選んだ。新しい依存は、推移依存で既存の部品の版を引き上げないかを依存の木で確かめる。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:nfr-requirements:2b9eaee2db8c2c15c8eb0fb8d39ebfd1a3ce4694b1fba5f199d3e053922d2a28 -->
- U3 の決定（DSL の上限を 10MB）に合わせて、レビューの済んだ U2 の NFR 要件の文書を書き換えてしまい、レビューが今の中身を確かめない状態になった。依頼者の Request Changes で直し、U1 から順にレビューし直した。別の単位の決定で、レビューの済んだ文書を直す必要が出たときは、先に依頼者に何を変えるかを尋ね、Request Changes を経てから直す。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:nfr-requirements:37a2f15427ab44b01ae637d198e5504666ce37d4cbb61182a6c7d3591c157885 -->
- 想定の規模の既定の DSL の大きさを試算したところ、要件の上限 5MB を超えることが分かった（約 5.3MB）。依頼者は書式を変えずに上限を 10MB に上げることを選び、承認済みの文書は書き換えずに差の一覧を U3 に記録した。数値の上限を決める段では、上限と、機能が作る最大の出力の大きさを突き合わせる。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:nfr-requirements:8891494975dc6b29448592cba1ee2e02f491b05f3754c55c08f7550e6491096e -->
- U4 のレビューで、本文の大きさを確かめる既存の仕組みはログインの確認より前で動くため、そこでは監査に要る「操作した人」が分からないと指摘された。既存の仕組みに手を足す設計では、その仕組みが要求の流れのどこで動くか（ログインの前か後か）をコードで確かめてから書く。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:nfr-design:2c3aba2e9571266c28a29ff6812347c001c6829fd2911f3b44d4ae72d651f41f -->
- U5 の NFR 設計で、大きさの判定の順序（文字列にする前に判定）だけが求められていたのに、送り方（バイト列のまま送る）まで変えてしまい、承認済みの機能設計（テキストとして読んで送る）と食い違った。NFR 設計で承認済みの設計と違う作りにするときは、差の一覧に載せ、依頼者に確かめてから書く。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:nfr-design:57189423d73219a9a42678de200df1e2b60fdfaab2af61e8b30b01c09ff1253b -->
- 照合の全体の上限（8 秒）を強制する作りが要るとレビューで指摘されたが、依頼者は上限を作らず、応答しない対象DB では 10 秒の目標を超えること（最悪 23〜28 秒）を許すと決めた。文書は直さず、承認の場で決定を記録する（決定 B）。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:nfr-design:4b924de437208836a07a1428c51fa4a4c5a7c6337d0f63c235c73802f70298cc -->
- U2 のレビューで、今の SecurityConfig は /api/** の外をすでにログインなしで通すため、JSON Schema の静的なファイルを許す設定は要らないと指摘された。公開の範囲を設計するときは、既存のセキュリティの決まり（許す道と既定の扱い）をコードで確かめてから書く。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:infrastructure-design:dd39ed8c1cd0e12519df7876a5f74dae9dbc0b49f2ba8c672174c5d8219964cf -->
- U4 のレビュー役に決定 C（既存のテスト3件は直さない）の根拠（テスト用の決まり PublicApiTestRules）を伝えず、1回目は NOT-READY になった。前の段の承認で決めたことをレビューの前提に書くときは、根拠のファイルの場所も伝える。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:infrastructure-design:67d7e015616534455228bc981a2a6e47b3fbd0dfe0835ac5e27264ffc7dc3a62 -->
- レビュー役の書いた表のセルに `|` や「 / 」で区切った列名が入り、記録が2回受け付けられなかった（書き直しの依頼でファイルが消え、やり直しが要った）。レビューの依頼には、セルの中に `|` を書かず並べるときは「・」でつなぐことを最初から書く。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:infrastructure-design:a3e52f9b05ccadacb24ee82529cc7b58e5441bbce3e9fd61b0edb7220f09a38a -->
- コード生成の計画を書く前に、その単位の各設計の段の承認の場の決定（監査ログの Approve・Request Changes の文言）を洗い出し、設計の文書と違う決定を計画に反映する。設計の文書だけを読んで、承認の場で変えた決定（例: 照合の全体の上限を作らない）を見落とさないため。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:code-generation:465f9b738669b46225c6cc19277d02dfb522066a7ac50973d766b259c4b966bf -->
- U1 で3種類の DB をすべて読めるようにする計画にした。Bolt の計画は「最初の1種類（3種類は B3）」としていたが、後で承認された NFR 設計（読み手を U1 に2つ置く）と基盤の設計（NFR12.1、3種類を verify で毎回）に合わせた。型の分類と 30 秒の確かめは U3 に残す。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:code-generation:2fb51bd3ad29b3d5920301065702783f5717bf7cd9eb8808018696181a52f11d -->
- 設定の型のポートと種類を文字列で受けることにした。数・列挙で受けると不正な値で Spring の結び付けが起動を止め、BR1.3（不正でも起動を続ける）を守れないため。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:code-generation:b5b8bce3a910d213a87588676a9f966b1b39c937919ea1b067d2c610d899aaf7 -->
- U2 の JSON Schema の複写を bootWar ではなく processResources の出力に対して行う計画にした。結合テスト（クラスパスで起動）でも公開の道を確かめるため。WAR の中の置き場は基盤の設計と同じになる。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:code-generation:d9168ff73279598b132aec91b71e472985c958ebe2e81716dcce1d23578b9922 -->
- U5 の設計の「C6 の 11 本の API」を、契約 C6 と U4 の実装どおり 10 本として扱った。設計の数え違いと判断し、10 本すべてを一括の確かめと dslApi で扱って差を記録した。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:code-generation:a9f7abdf1bd5349a81d95761ca941b7c51347c945dd8509c30ed506d03507d78 -->
- 3つの JDBC ドライバー自身のログを OFF にした。MariaDB のドライバーが認証の失敗をユーザー名つきで WARN に出し、NFR4.4 に反したため。失敗は読み取りの口が原因の種類と SQLState だけで出す。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:code-generation:08e69aceb200ec04ad7c35a34d27cc22578d122dc59aad4498cf68a855e25919 -->
- U2 で深さ・別名・タグを SnakeYAML の LoaderOptions ではなく Parser を包む部品で数えた。2.6 の上限は位置を持たない例外で止まり、TagInspector は !custom・!!str を通すため。LoaderOptions にも同じ上限を置いて二重に守る。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:code-generation:3f589e574ae19a349117bc6369172f8d40b3bb6b2ceb779fb2c8c594a2b82756 -->
- AC2.2.3（同じテーブル・カラムの二重の定義）は意味の誤りではなく DUPLICATE_KEY になる。BR1.5 のとおり。受け入れ基準の文言とは違うため、承認の場で伝える。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:code-generation:a56dd5b6f41cfdb2b20c1999eb6490911de17a4c6d05b3fa5a7c17b7f698f8c4 -->
- U3 で longtext の長さ（4294967295）は DSL の dbType.length を null にし maxLength も作らない。U2 の JSON Schema と DbType の整数の範囲を超え、生成した DSL が検証を通らなくなるため。BR2.1・BR4.1 との差。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:code-generation:76b79ed4071dac0f062262743398e331b984d95d96dec454e01ccf6be31a5328 -->
- U3 で写しに無いテーブルを参照する外部キーは DSL に写さない。写すと U2 の意味の検証（BR3.3）を通らず生成が失敗するため。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:code-generation:eaa15b9f184e86896f3519d3f8aa6a5ef1c9229987fa004d0a39e83118c8f4b7 -->
- U4 のトランザクションの境界を DslLifecycle ではなく DslRecordStore（service）に置いた。生成と照合で対象DB を読むあいだ内部DB の接続を持ち続けないため。確定の後だけ差し替えと出来事を行う。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:code-generation:c015b5280cb14aef88af84f6b73daea82bdab93b963f69195585b5a19cb93272 -->
- U5 の違いの表・誤りの一覧・履歴を make-you-chic-ui の Table ではなく見た目を合わせた素の table にした。Table に行の開閉が無く、ページ送りの文言が日本語に固定で英語の表示を満たせないため。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:code-generation:221f87635b3f17a5e03922d5b0704346d4f364b685b841f2d65b9f24916a7720 -->
- PostgreSQL の主キー・外部キーを pg_constraint から読むことにした。information_schema.table_constraints は SELECT だけの権限のアカウントに制約を返さず、読み取り専用のアカウントで成功させる基準（AC1.1.10）を満たせないため。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:code-generation:08247e65231d8babdc7536a6166a39aebed1ad7ba069490e5977934cd284306f -->
- U2 の Validation の値を数（number）と文字（text）の2つに分けた。YAML の値の型をそのまま保ち、後続の Intent が型を判定し直さずに済む代わりに、entities.md の value 1つの形とは違う。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:code-generation:c77eb97fea82cb186ddddd31242869584b4dcda31c6e84952f9a5e952ab6b9d5 -->
- U4 の適用はプレビューの行を INSERT ... SELECT の1文で履歴へ写す。10MB の本文を読み直さずに済む代わりに、H2 の SQL に依存する。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:code-generation:80c597a572fc58d46352c537543a1f8e52e45582b47f16bc7ca6d9855b6c4689 -->
- U2 の計画の前に、各設計の段の承認の場の決定（監査ログの Approve の文言）を洗い出した。U1 で決定 B を見落とした反省から。U2 に関わるのは決定 D（JSON Schema を許す設定は足さない）と、10MB への引き上げ。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:code-generation:e5adf83437ab5ced96690000ea82c3620261c3a736d28f3a2a1f6cec6fce6c56 -->
- 設定のずれを確かめるときは、compose の既定値と各 PC の .env の値を分けて見る（メモリの上限は compose の既定が 1g で、2g はこの PC の .env の値だった）。承認済みの記録と食い違ったときは、記録を書き換えず、ずれの記録に根拠とともに明記して依頼者に諮る。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:feedback-optimization:f7aed2210bbb992c5bb115494a313c935d143971bc23f6d98823b1d3881904e4 -->
- 依頼者は Full rescan を選んだが、深さ Minimal のため開発担当が深く読んだのは7件に関わる約30ファイルとビルドの設定だけだった。記録上の範囲は kind: partial とし、./ を analyzed.paths に入れない（前回と同じ扱い）。 (learned 2026-09-24) <!-- cid:260924-followup-fixes:reverse-engineering:af16b38d0511d7ef16486d2d008c467505aec9676b09a5032695d58313d7af5f -->
- 比べた結果は NARROWER。前回の深い範囲（common-*・access・frontend-app-core など12部品）は今回流し読みで、記録上の範囲から外れた。代わりに今回の7件に関わる30ファイル（config・auth・audit・container-runtime・perf-and-monitoring・build-and-verify・frontend-feature-dsl）を深く確かめた。 (learned 2026-09-24) <!-- cid:260924-followup-fixes:reverse-engineering:fde1a512f7d277c2d14c56ac3bb1bcb075512477ec44439955c0ac2678c78997 -->
- 2件目・3件目・5件目は依頼の文と前の Intent の振り返りで直す向きが決まっていたため質問にせず、問いの冒頭に「決まっていること」として書いた。質問は判断が分かれる5点（Loki の個人に関する値・Hibernate の案内・環境変数の分け方・閉じるボタンの名前・負荷の試験の手順）に絞った。 (learned 2026-09-24) <!-- cid:260924-followup-fixes:requirements-analysis:55a20087759902237d785de5dc5f9356bba4a6045d7919dd73f74b72ae905ce1 -->
- zsh ではコマンドを変数に入れても単語に分かれず、使い捨てのアプリを止める手順が動かずに H2 のファイルのロックで利用者の投入が失敗した。関数に直してやり直した。 (learned 2026-09-25) <!-- cid:260924-followup-fixes:build-and-test:eac943d99a37dcc57ed0bfdad708c5da461d96092b454798be563c5d8497682b -->
- 依頼者は Full rescan を選んだが、深さ Minimal のため開発担当が深く読んだのは4件に関わる約30ファイルだけだった。記録上の範囲は kind: partial とし、./ を analyzed.paths に入れない（前回・前々回と同じ扱い）。 (learned 2026-09-25) <!-- cid:260925-storage-memory-fixes:reverse-engineering:31e0dd4a4e60115c41309f55819446453d89ecad55c51679a6cfc32386ee8517 -->
- アーキテクトへの依頼で、記録するコミットにスナップショットの source の識別（git:cb55a97…、コミットではない値）を渡してしまい、timestamp の表にそのまま書かれた。公開の前に develop の HEAD（4970f3b）に直した。記録するコミットは git rev-parse HEAD で取り、スナップショットの値と混ぜない。 (learned 2026-09-25) <!-- cid:260925-storage-memory-fixes:reverse-engineering:8e107e1dd133acfaee2024524120055096e196e62d89ce2556edc58d15fdfdb5 -->
- 開発担当は原因の見立て（H2 が空いた場所を再利用しない理由、メモリの内訳、AccessTokenApiIT の原因）をすべて未検証の仮説として書いた。確かめた事実と仮説を分けることで、要件定義で測り方と切り分けを決める材料にする。 (learned 2026-09-25) <!-- cid:260925-storage-memory-fixes:reverse-engineering:3c819fd32cf1845bc669af3a7e068808eb07b60a739402dcba562cf3f0c8cce1 -->
- Q1 の答え（A）に添えられた依頼者の補足（H2 は接続が生きている間は詰め直さない）を、直し方の前提として要件に書き、コード生成で実際に確かめることにした。補足は質問の選択肢に無い技術の方向づけで、表の形を変えない（Q2: B）答えとも合う。 (learned 2026-09-25) <!-- cid:260925-storage-memory-fixes:requirements-analysis:ad999a6b391342cc3a29ff64b2d3ee34bfc948e9ee8812982413eb148cca2056 -->
- Q1（動いている間も頭打ち）と F1（管理者が操作したときだけ詰め直す）が食い違うため、追加の質問 F4 で目標を「操作の直後に戻る」に確かめ直した。あわせて入口（F5: API だけ）と監査（F6: 残す）も追加で確かめ、質問は 4 問＋追加 6 問になった。 (learned 2026-09-25) <!-- cid:260925-storage-memory-fixes:requirements-analysis:8ff983dd32b178546d4bdd8dbf4f158a8c9a21be9ef837819c16a2279072aa5f -->
- メモリの 93% はアプリが持ち続けるデータではなく最大ヒープの割合 75% によると測定から読み、依頼者の決定（D1: T1・D2: M1）で Dockerfile の MaxRAMPercentage を 50 にするだけにした。目標の判定は Build and Test の dslMixed で行う。 (learned 2026-09-25) <!-- cid:260925-storage-memory-fixes:code-generation:8ac52a3298c25a902eb524fe9cfbdaf659995c604c3f71ecab7627a7ebb213f0 -->
- 依頼者の決定（Q2: A、Q3）で、詰め直しの入口を承認済みの要件の管理者向け HTTP API（FR1.3）から JMX だけに変え、HikariCP の標準の機能で行うためアプリのログと監査ログ（FR1.6・FR1.7）は出さないことにした。要件の文書は書き換えず、差を計画とこの段の成果物に明記する（project.md の決まり）。 (learned 2026-09-25) <!-- cid:260925-storage-memory-fixes:code-generation:de414ea347467e3f61a6c94312bc88ffc5feab5d1a23a8bc5df9d93dd6325653 -->
- 開発担当への依頼で Testing Contract の本文を差し込み忘れ（{CONTRACT} のまま）、すぐ後に追って本文のファイルの場所を伝えた。計画に貼られた本文が render の出力と一致するかを承認の前に確かめる。 (learned 2026-09-25) <!-- cid:260925-storage-memory-fixes:code-generation:bce3478ec9e3aa2f2de3f4b4749b7508b831b3be52560504e018428772be948f -->
- traceability.json の Deferred（FR2.4・FR5.x）はこの段で確かめて Met だったが、コード生成の成果物は確定の後なので書き換えず、cross-unit-traceability.md に「この段で確かめた」と記録し、判定は不合格（条件つき）として承認の場で扱いを確かめる。 (learned 2026-09-25) <!-- cid:260925-storage-memory-fixes:build-and-test:472fb66d456296d5a7fe677840031cb6c59d5d85c3bf6b067bfe4b5603efc9c1 -->
- 統合（fast-forward）・配備の前の k6 とバックアップを省くこと・イメージだけの戻しは、前の段と前の Intent の決まりで決まっているとして質問にせず、配備の後に詰め直しの道具を流すかだけを質問にした（Q1: A）。 (learned 2026-09-25) <!-- cid:260925-storage-memory-fixes:deployment-pipeline:5ea2c2ef96330799fa4cc999edda63873970382b0b2c851a09c9cbf8b1855014 -->
- 既存の知識ベースが STALE のため再利用の選択肢は出さず、依頼者は Full rescan を選んだ。スナップショットは ./ 全体（source git:f6133c3e…）。深く読んだ範囲だけを analyzed に記録する方針（前回までと同じ）で、今回の Intent G・H に関わるユーザー・認証・監査・管理画面・フロントエンドの骨組みを重点に読む。 (learned 2026-09-25) <!-- cid:260925-user-management:reverse-engineering:5a8548e943c8633cefd0aa9789bcd2e0f3f6c2714789e548319e6cef4671cfb8 -->
- 依頼者は Full rescan を選んだが、深さ Standard で開発担当が深く読んだのは今回の Intent に関わる 89 パス・19 部品だけだった。記録上の範囲は kind: partial とし ./ を analyzed.paths に入れない（前回までと同じ扱い）。比較の結果は NARROWER で、前回の深い範囲（dsl・dslmanage・backend-test-support・perf-and-monitoring）は流し読みに下がった。 (learned 2026-09-25) <!-- cid:260925-user-management:reverse-engineering:46ef8970da9eb2c3776a1fe97a515394a7e2608d1be5494dd649e6cdfc630a85 -->
- 今回の Intent に関わる所見 11 件に K-1〜K-11 の番号を付け、本文は持ち主の文書に1回だけ書き、business-overview.md には一覧だけを置いた。重複は無くなるが、読む人は所見の文書をたどる必要がある。 (learned 2026-09-25) <!-- cid:260925-user-management:reverse-engineering:d331d883f6bb64d9a73bed339866cecf6a0a78dc849bc15ce7f95b8fbe32519c -->
- 再実行のため、リードの候補 P1〜P11 と支援役3名の追加（受け手の条件・E2E の実行の時点・SpotBugs の関門・Dependabot の受け方・記録の更新）のうち、チームの進め方に当たるものだけを 11 問にまとめた。利用者の状態・招待の期限・パスワード変更後のトークン・送信とトランザクションなどの機能の中身は要件・設計の段に回した。セキュリティ担当の確認で GitHub の既定のブランチは develop と分かり、Dependabot は向き先ではなく受け方だけを問うた。 (learned 2026-09-25) <!-- cid:260925-user-management:practices-discovery:7b6b8591e2830a41d4a2e80b2eca885a38e1e1c179f53495d736ccc48a18c9b3 -->
- Q1（サブモジュール＋composite build）の答えが team.md・project.md の「lockfile で版を固定」「脆弱性検査の対象に含める」「取得元は Maven Central だけ」と食い違いうるため、追加の質問 F1（置き場と直接の変更の禁止）・F2（推移依存も lockfile と検査の対象にすることを条件にし最初の Bolt で確かめる）で確かめた。 (learned 2026-09-25) <!-- cid:260925-user-management:practices-discovery:5fd00432ffa9a4073386ee332ce2406b1377675c6f5d8039512052f2e1b17f13 -->
- まとめの確認の前に出す決定の要約（review-brief summary）は道具が動かず（main が無いという誤り）出せなかったため、答えのまとめだけを示して確認した。 (learned 2026-09-25) <!-- cid:260925-user-management:practices-discovery:4a6236945b51767b44319f3d565a6049b8c3792098b33801f02216f31bf39aed -->
- 「今回は招待から登録の完了までの E2E を1本足す」は今回の Intent だけの決定のため team.md に入れず evidence.md の要件・設計に回す論点に置いた。team.md は「Intent ごとに代表の流れを1本まで足す」の決まりだけにした。 (learned 2026-09-25) <!-- cid:260925-user-management:practices-discovery:8be32579f67998def12787c9a4a35168d12e9c9e3f4fcba4fb5efa9f7ff0de9b -->
- 依頼文の「ロードマップ」は、依頼者に確かめて参考資料の要件のドラフト（5.8・5.9・7・8・9 章）を読んだ。依頼文・ロードマップ・project.md の固い制約で決まっている点は質問にせず冒頭に書き、質問は利用者の状態の持ち方・招待の期限と操作・送信の失敗・入力項目・登録の完了の流れ・既定の値・言語の範囲・パスワード変更後・監査・設定の不足の 12 問にした。 (learned 2026-09-25) <!-- cid:260925-user-management:requirements-analysis:8a50270e4626c98cf3eb887338b6e214757343b2762cabe6640404d4dd2e1402 -->
- Q5 の「ユーザレコードの氏名にはメアドを入れておき」は、Q1 で招待を別の表に持つことにしたため、登録の完了の画面の氏名の初期値として読んだ（前提 A2）。 (learned 2026-09-25) <!-- cid:260925-user-management:requirements-analysis:82b75c6ee0fd890a348d1b9a20ad0f358c1f483e6a61bebfd7aab2ede99dde5b -->
- Q11 で管理者の招待の操作を選ばず、登録の完了とパスワード変更だけを選んだため、DSL の管理者の操作を残している今の監査と食い違いうるとして追加の質問 F1 で確かめ、招待・送り直し・取り消しを残すことになった。 (learned 2026-09-25) <!-- cid:260925-user-management:requirements-analysis:bedcdf4e8ff467ba6ad762b2f4a5b5cb554ebab127a51861e6e857ec5cdf6eac -->
- 応答時間（招待 5 秒・ほか 1 秒）は質問で尋ねず [assumption] として要件に置き、NFR 要件の段で確かめる形にした。レビューで出典の RQ4 が誤解を招くと指摘された（R-01）。 (learned 2026-09-25) <!-- cid:260925-user-management:requirements-analysis:53c588d73b9315f9e2166c22d1d80645412baff5f08c86b815a7b18301e8b88d -->
- 要件の確認で受け入れた指摘（R-02 期限切れの招待がある場合の再招待と古い招待の扱い、R-03 判定条件の Given/When/Then）は、この段の受け入れ基準で具体にする方針を計画に書いた。質問はペルソナ・分け方・横断の要件・Should・細かさの5問にした。 (learned 2026-09-25) <!-- cid:260925-user-management:user-stories:4a60d2bfdc43bf5ba3193df85ee120cb4be9076593bc38a6a3d4e0d713e80040 -->
- mob の3人が挙げた判断の候補（約 25）のうち、専門の知識で決まるもの（期限の時刻ちょうどは無効、今のパスワードの誤りは 401 にしない、完了の時点の同じメールアドレスはほかの拒否と同じ応答（NFR3）、リンクを開いただけの失敗は監査に残さない（FR9.1 は登録の失敗））はリードが決め、利用者の体験や要件の追加に当たる9点（M1〜M9）だけを依頼者に尋ねた。 (learned 2026-09-25) <!-- cid:260925-user-management:user-stories:89df20143e494a6213ec76fa90ad73fa466662d07bcb13eb5e6194177c0c5980 -->
- mob の3人の意見は合わせて約 75KB あり、統合（stories.md・personas.md・traceability.json）を決定事項つきでプロダクトマネージャーの担当に任せた。リードは判断の振り分けと依頼者への確認を受け持った。 (learned 2026-09-25) <!-- cid:260925-user-management:user-stories:895ff6b7b015ab61d4230d0cbb6f0d10afd6187dc52f989feb9e0984e0aba711 -->
- 実施の判断の文書を質問の確認の前にシェルで書いたため記録された書き込みが無く、レビューの依頼が拒まれた。確認の後に編集し直して依頼を通した。成果物は確認の後に書く（または書き直す）。 (learned 2026-09-25) <!-- cid:260925-user-management:user-stories:e711ee38a761ee9b5e3fe4cd9b7923a1bdc18dfa8352f65e00a294b64409d2ad -->
- 依頼者の判断の後に確認をやり直した（M1〜M9 を足したまとめ）。mob の判断は答えの中身を変えるため、ストーリーに反映する前にもう一度 Looks correct を得た。 (learned 2026-09-25) <!-- cid:260925-user-management:user-stories:da0ac873162523cb729ddc7d577d19186883288dc21fd6d2a21f94f491eb1d2a -->
- classic の範囲で Ideation のラフな画面イメージと利用者の流れが無いため、画面イメージはストーリーと要件から直接作る。WCAG 2.1 AA は前の Intent と同じとして質問にせず、ストーリーの段から回された画面の論点（異論の食い違い・言語の切り替えの置き場・パスワードの上限の伝え方）と画面の組み立てを7問にした。 (learned 2026-09-25) <!-- cid:260925-user-management:refined-mockups:666df297dd276a34b74bc2c000784823350b7346bf7161d37836846a1e990f96 -->
- S2 のログインの画面へのメールアドレスの持ち越しと、S4 の未保存の確かめを出さないことは、要件・ストーリーに無い細部として [assumption] にした。 (learned 2026-09-25) <!-- cid:260925-user-management:refined-mockups:7e4b88e7fde27dd6525bb739a8d2fa360b1d309c5dc849157eb73b3af64ce62e -->
- 画面イメージを書く前に、統合で増えたストーリーの受け入れ基準（84 件）を1件ずつ突き合わせず、AC1.1.8（成功の後もフォームが開いたまま）・AC1.1.4（案内から一覧の行へ移れる）・AC3.2.16（autocomplete="username"）と食い違い・抜けが出て、レビューで指摘された。画面の段では、書く前に対象の受け入れ基準を画面ごとに一覧にして照らし合わせる。 (learned 2026-09-25) <!-- cid:260925-user-management:refined-mockups:be3f36883fd2d611cc857a71a82fa4bdc52885de02c684604b6548f45d237ee8 -->
- Q1 B（入力は Modal）と AC1.1.8（成功の後もフォームに残る）が食い違う。Modal のまま成功で閉じるか、成功の後も Modal を開いたまま次の招待に備えるかは、承認の場で依頼者が決める。 (learned 2026-09-25) <!-- cid:260925-user-management:refined-mockups:52e333cbee982310bc23f4ed07119ac43d662edf28924c4510587e71fc3fbe35 -->
- Feasibility の段が無いため、java-mustache-processor の取り込み・JVM の中の SMTP の受け手・H2 での招待中の一意を ADR-010 にまとめ、確かめる段と切り替え先を書いた。 (learned 2026-09-25) <!-- cid:260925-user-management:domain-design:ffa97308b9ccd815f133698ccec7e6af08d308357d74cd4311e7f0c0a1297948 -->
- 招待メールは要求の中で、招待の確定の後にトランザクションの外で送り結果を別の短いトランザクションで記録する形（ADR-009）にした。送信の待ちで接続を持たない代わりに、応答時間に SMTP の時間が乗り、送信の途中で止まると結果が残らない。 (learned 2026-09-25) <!-- cid:260925-user-management:domain-design:9c5a29497f6e73c5a5056731f096ff5450de0686365a16d64cf9a8967f65482e -->
