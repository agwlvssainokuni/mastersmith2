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

- ワークフロー計画で実現可能性の評価（Feasibility）をドメイン設計に吸収した場合は、ドメイン設計の中で実現可能性の判断（例: 組み込み H2 による単一インスタンス前提）も扱い、質問と ADR に記録する。 (learned 2026-09-22) <!-- cid:260922-auth-audit-base:domain-design:ee07c801eb0d3d87e282d50543ccbef44bacce0c9ef7a250387f1efe4dee204f -->
- Construction の設計の段で、単位に新しく決める論点が無いときは、質問を作らず、設計の要点を要約として依頼者に確認する（Looks correct / Request changes）。 (learned 2026-09-22) <!-- cid:260922-auth-audit-base:infrastructure-design:43273034433bb9ddc5727514f8d6640170eddfcfff090cc9e50101aaeb3a7182 -->
- ローカルの1コマンドの検査は Gradle の1つのタスク（./gradlew verify）を入口にし、フロントエンドの検査（npm）と外部の道具（Gitleaks・OSV-Scanner）も Gradle から呼ぶ。CI も同じタスクを呼ぶ。 (learned 2026-09-22) <!-- cid:260922-auth-audit-base:infrastructure-design:62d8de5982127d518cdf7b11cd3ae4dbf5764a9bb03b3861724f28dd11b92945 -->
- Spring Security を使う単位では、セキュリティの決まり（SecurityFilterChain）を、それを前提とするテストより先に入れる。決まりが無いと既定の設定が全要求にログインを求め、関係のないテストが落ちるため。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:code-generation:bfd72fde6eec3e1831dced6070a1f62fffaa4c44bf9f498ba97aa50af15c6737 -->
- 要件の網羅を確かめるときは、要件定義の FR・NFR から、機能設計の BR と NFR 要件の枝番を経て、Code Generation の traceability.json へ至る2段の連鎖でたどる（traceability.json は要件の ID を直接持たず、単位ごとの ID で持つため）。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:build-and-test:1340b2bcd75ac79b135d0768a43f458838dd75a8266977a7f183ed9c9fba3820 -->
- 確認のための要約と、実装または承認済みの設計が食い違ったときは、実装と承認済みの設計を正として記録し、要約との差を成果物に明記する（要約に合わせて実装を変えない）。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:ci-pipeline:14248c7b19e31f384aae87bfe2bc4ccc4a1b979bdd77d36d0ee3452b0e6da01d -->
- 確定済みの設計と違う決定を依頼者がしたときは、設計の文書は書き換えず、差をその段の成果物に明記し、README などの手順を決定に合わせて直す。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:deployment-pipeline:b511560ca6ee8edac9468a296692cea2d0ce2afd343dedd46d09e1691cc7ea6f -->
- 大きい単位を複数の Bolt に分けるときは、設計の段は1つの単位として通し、コード生成の計画で Bolt ごとに分ける（user-admin の U3 を前半 B3・後半 B4 に分けた）。 (learned 2026-10-01) <!-- cid:260930-user-admin:delivery-planning:eeb51d6f303964784162df4356b33c9ff6143b628c976443564ba5121ae3d810 -->
- 担当を同時に多く動かすと利用の上限に達しうる（user-admin の NFR 設計で5人が同時に止まった）。止まったときは単位の作業を unit pause で一時停止にし、次にすることを記録してから、上限が戻った後に作り直す。 (learned 2026-10-01) <!-- cid:260930-user-admin:nfr-design:e6f09109975092bcc3a8e3da4a0571366205a163cebf2105400f08f123dc1875 -->
- 単位の依存の上では先に作れても、合格の条件が後の単位の画面を含むもの（例: axe-core の引き上げは E2E の全件の通過と違反 0 件）は、Delivery Planning で前半と後半の Bolt に分け、後半を後の単位の後に置く（business-data-views で U4 を B3・B7 に分け、Units Generation のレビューの R-01 を解いた）。 (learned 2026-10-18) <!-- cid:261016-business-data-views:delivery-planning:9bdbedc867d8d465a785327558d6bae8473198dc41964c98d89080086e4fe92c -->
- まとめの確認で「この段で行う」とした確かめ（k6 inspect など）は、記録の担当に任せたときも、承認の場の前に実際に流したかを確かめ、流していなければ指揮役が流して記録を直す。 (merged 2026-11-04)
- 質問のファイルの中で、質問ではない節（捨ての試しの結果など）の見出しを「## Q7: …」のような質問の番号の形にしない。質問と重なる見出しとして扱われ、まとめの確認が受け付けられない。 (merged 2026-11-04)

## Walking Skeleton

## Testing Posture

- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:build-and-test:0cb06418ec91709472835a5f5f3278309672385a651295cb7d541197c7521196 -->
- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:build-and-test:0bf5838d31cde7d0227a8b8a7218db87d70238e8b3c13aa06ca6b3864dd85c28 -->
- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:performance-validation:ab2f1782048387afc43d5ecfe6517fd2f601355f934e19d3f106f25a99a14fbb -->
- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:performance-validation:981941a9bbe608353df1979cdc3e9d9c4a10b9fa6cf276c92273fa3011ab4eae -->
- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) <!-- cid:260923-colima-spec-up:build-and-test:0b433994defeb0970a91e3befaf62e4fef958b5e241106072014bb819309de6a -->
- 負荷の試験で接続プールが尽きたかを確かめるときは、使い捨てのアプリにだけ MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics を渡して /actuator/metrics の hikaricp の値を読み、数秒ごとの使用中の数ではなく、待ちの時間切れの累計と借りるまでの待ちの最大で判断する（配備したアプリの公開の範囲は変えない）。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:performance-validation:6183459b552b1e347df4f86017641db5c56a4349202caff390c55c193c899525 -->
- 軽い API の性能は、投入を重ねて内部DB のファイルが膨らんだ状態（悪い側の条件）のまま測る。メモリの最大（memory.peak）を比べる試験の前は、アプリのコンテナを作り直して前の最大の値を消す。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:performance-validation:b9c43187ce6f38e701503c4be8cbbe11c162c17cd1cb8b6739f6f7ddffc4ca74 -->
- 実際のブラウザのアクセシビリティの検査（axe）は、誤りを出した状態と、現実に近いデータ（2語の氏名など）で、ブランドカラーとテーマのすべての組について行う。user-management の U7 の 080 で初めて、make-you-chic-ui の Avatar（頭文字が2文字のとき）と dark の FormField の誤りの文字のコントラスト不足が出た（050〜070 は誤りの状態を見ず、氏名が1語だったため出なかった）。 (learned 2026-09-28) <!-- cid:260925-user-management:code-generation:bf749cf9922de8af8d667e42afabe703fc4bc49c92098bc0d879bd50e2f3dbc1 -->
- 応答しないメールの受け手は、Mailpit を docker pause で一時停止して作る（接続は受け付け、何も返さない）。コンテナを止めると接続の失敗になり、時間切れの確かめにならない（user-management の Performance Validation）。 (learned 2026-09-28) <!-- cid:260925-user-management:performance-validation:0ab7ba7e09b5c371df3611ba3a4fbb257134eef98307d4ea68256e5399dae71d -->
- Delivery Planning でのカバレッジの実測は、./gradlew verify 全体ではなく :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport で行い、jacocoTestReport.xml から手を入れる見込みのパッケージの値を読む（user-admin で約 5 分）。 (learned 2026-10-01) <!-- cid:260930-user-admin:delivery-planning:e35925faac38449777eadec784bc2546fb19d593b9a634f5af273fef22f9b364 -->
- 接続プールの見積もりを確かめる負荷の試験には、上限に届く形（プールの上限を下げた場面など）を含める。上限に届かない負荷では、見積もりが誤っていても合格する（user-admin の U3 の NFR 要件のレビュー R-02）。 (learned 2026-10-01) <!-- cid:260930-user-admin:nfr-requirements:081ae35e2df4baab72ba31d62c38f71f2901d47862327a22c2a35cf3f79e93ff -->
- 画面のはみ出しの確かめ（E2E・axe）では、画面全体の横のスクロールだけでなく、開いたメニュー・ポップアップなど画面に固定で置く部品が画面の中に収まることも確かめる。表の右端に置く make-you-chic-ui の Dropdown は placement に bottom-end を指定する（user-admin の配備の後のスモークテストで、行の「操作」のメニューが右へはみ出していたのを E2E 120 が拾えなかった）。 (learned 2026-10-03) <!-- cid:260930-user-admin:deployment-execution:b5e3f019a0c5b8d77ffeefffb93397257a20b3d09a272885ed5d89cf991b043c -->
- 木の画面の単体テスト・E2E・スモークテストでは、本物と同じ形の応答（子の有無の印を含む）を使い、すべての段の開閉を押して確かめる（role-menu の配備の後のスモークテストで、「自分の権限を見る」の木でテーブルの開閉を押すと自分自身を子に持って固まる不具合が見つかった。単体の見本のテーブルは子が無い形で本物と違い、E2E は選ぶだけで開閉を押していなかった）。 (learned 2026-10-14) <!-- cid:261004-role-menu:deployment-execution:617c2725a739e866d25a157a07f607a7d8eccb7ce66d3be91fa609e2f0aba46e -->
- 画面の不具合を使い捨ての環境で再現するときは、依頼者がした操作（どのボタン・どの開閉を押したか、画面の文言とその場所）を先に聞き、同じ操作を試す。role-menu の S8 で、選ぶだけで開閉を押さずに再現せず「環境の差」と誤って見立て、依頼者の再試行で条件が分かった。 (learned 2026-10-14) <!-- cid:261004-role-menu:deployment-execution:f96cc73619d8f749689d96ebc66682424b85c179f12439e0c3f9117fe01e992d -->
- 接続の待ちの最大のように数 ms の目標は1回の値が揺れるため、合否の回を複数回流すことを負荷の試験の計画に入れる（role-menu の U4-NFR2.7b で1回目 23.55 ms、作り直した流し直しで 8.55 ms となり、原因を確かめないまま Not Met で持ち越した）。 (learned 2026-10-15) <!-- cid:261004-role-menu:performance-validation:3f81b1ca69ea7e980dd28498e53f06fd9f29c3af4fec2690253431a0ba6eba68 -->
- 大きな入力（10 MiB の DSL の投入など）のヒープの見積もりは、設計の段で試しの測定で裏付ける（role-menu の Performance Validation で GC の後でも 573〜746 MB あり、見積もりの 256〜384 MB を超えた。dslMixed の memory.peak は上限 2g の 87%）。 (learned 2026-10-15) <!-- cid:261004-role-menu:performance-validation:373bdf0b74236a3d3a4d9e3e32463b4280b09ff3b58e3aad3927f40d56d0ec6f -->
- 長いテスト（verify・E2E・k6）は、試験の台本全体を `caffeinate -dims` で包む（場面ごとに起こし直すと切れ目で守りが外れる）。流す前に `pmset -g assertions` で眠らない状態か、流した後に `pmset -g log` で Sleep・DarkWake が無かったかを確かめ、入っていたら結果を使わずに流し直す（DarkWake の Thermal Emergency のスリープは `caffeinate -i` では止められない）。 (merged 2026-11-04)
- colima の PC で `./gradlew verify` を流すときは、README の `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡す。渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗する。 (merged 2026-11-04)
- k6 の扱い: `k6 inspect` には `--include-system-env-vars` を付ける（無いと場面の名前が undefined になる）。colima の VM の時計が負荷の間にずれて `http_req_duration` が崩れるため、判定は1回の繰り返しに要求1つの場面の `iteration_duration` で行い（トークンは setup() で取る）、`http_req_duration` は並べて記録する。操作が2つ以上の場面は op のタグが `iteration_duration` に付くかを台本で確かめる。内部DB に SQL で直接入れた試験用の利用者はロックの状態の行が無いため、同時のログインを流す前に1人ずつログインさせて行を作る。 (merged 2026-11-04)
- 使い捨ての環境を消す前に、確かめの結果（問い合わせの結果・ログの WARN の件数と内訳など）を見て、取り直しが要らないことを確かめてから片付ける。消す前に数える項目は、台本に最初から書いておく。 (merged 2026-11-04)
- Playwright の `webServer.env` に置いた値は json の報告に残る。E2E の仮の資格情報はプロセスの環境変数で渡し、報告の json の文字列の検索で、パスワード・トークン・メールアドレスが入っていないことを確かめる。 (merged 2026-11-04)
- 負荷の試験の台本を書く前に、計画の「Build and Test に引き継ぐこと」の項目を一つずつ台本の手順と突き合わせる。 (merged 2026-11-04)

## Change Control

- コミットはこまめに、ファイル変更のまとまりごと（回答確定時・成果物作成時・内容確認/承認時）に行う。コミットのタイミングは提案し、実行前に必ず人間の承認を得る。コミットメッセージは日本語で記述する。 (learned 2026-09-22) <!-- cid:260922-auth-audit-base:intent-capture:822f19c942c585b350586f2ad3ecf1660487d52354264177a5c424103985dc8a -->
- 確定済みの成果物に記録の食い違いが見つかったら、隠さず判定の根拠とともに明記し、直すかどうかを依頼者に確かめる。直したときは、元の状態と直した理由を記録に残す。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:ci-pipeline:fa9b684652e9547fdc00219e301a27d661ffac1f52f9749d17d7bdcac8f883ec -->
- コード生成の段の承認の場は、最後のコードのコミットの後、記録だけのコミット（aidlc/ の下だけ）を積む前に開く。user-admin で R2・R3・R4 と承認の場の記録のコミットが先頭に続き、aidlc/ の外のコードの作業が見えないとして承認の場が断られた（REQUIRED_SOURCE_WORK_MISSING）。 (learned 2026-10-03) <!-- cid:260930-user-admin:code-generation:82a48423a89edaac5d5346295acd928c13c5ebc6b48abe2741f6c6f687196578 -->
- squash の統合の前に、監査ログを含む aidlc/ の未コミットの変更をコミットしておく。user-admin の U3 の小さな直しの統合で git restore --source=HEAD -- aidlc/ を使い、未コミットの監査ログの追記（承認の場の断りと問いと答えの記録）を消して戻せなかった。 (learned 2026-10-03) <!-- cid:260930-user-admin:code-generation:6134b387e5869328fae2d8bafc2131cf7bf2251f06efa3069d5c9bc4c620406e -->
- コード生成のコミットは計画の手順ごとではなく、import の向きを確かめて場所・機能ごとに区切り、最初のコミットの時点で型の検査（コンパイル）が通ることを確かめる（role-menu の B1・B3・B8・B9 で計画の手順ごとの区切りがビルドの通らない区切りになり、場所ごとに変えた）。コミットの提案は確かめたい点の問いとは別に、積む前に必ず尋ねる（B5 で確かめたい点の答えの後に承認なしで C1 を積んだ）。 (learned 2026-10-13) <!-- cid:261004-role-menu:build-and-test:1a3edd202ea8b42358822afbf8772db989401c8b6f89d39b7877d0ac0500d441 -->
- 段の成果物の初稿ができた時と、段を承認した時は、コミットの可否を尋ねずに、その段の成果物と記録をコミットする（依頼者の指示）。それ以外の時のコミットは、これまでどおり提案して承認を得る。 (learned 2026-10-29) <!-- cid:261029-reference-lookup-cleanup:functional-design:03f6ea181d8da001a0e79d9eb79d6ade0d3faf856cbd5b89724b71612116b30f -->
- コード生成の段を始める前に、E2E の報告（`frontend/playwright-report`・`frontend/test-results`）など git の対象外の生成物を消しておく。段の途中で消すと単位の一覧に載せられず戻せもしないため、承認の場が止まる。止まったときは、依頼者の明示の承認を得て、承認の場を開く操作1回だけに `AIDLC_SKIP_SOURCE_FRESHNESS=1` を付け、理由を承認の場の記録に残す。 (merged 2026-11-04)

## Deployment

- 配備先が決まるまでは、基盤の設計を開発者の PC 上のコンテナの範囲に限り、クラウドの基盤（IaC・検証環境・警報の通知の先）は作らない。配備先が決まったときに置き換える前提で書く。 (learned 2026-09-22) <!-- cid:260922-auth-audit-base:infrastructure-design:737809f49e081ec40c56d8179ff3b4129fe694f9d826d020d088e48d06f980a2 -->
- CI の仕組みが既に実装されている段（ci-pipeline など）では、その段の文書を新しい設計ではなく、既にあるものの記録として書く（きっかけ・段の並び・関門の基準・成果物・固定している版）。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:ci-pipeline:389857be7b2081cfcb663fe5dd9db837e751334bfd6626a3f7df4b5df637d023 -->
- CI が必要な検査を実行しているかの確認は、Build and Test が記録した検査の一覧（コマンド）と、CI の段との対応づけで判断する。意図して CI の外に置く検査（E2E など）は、その旨と代わりの実行の場を明記する。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:ci-pipeline:5db3d4deb009c3eebf80febf187717a0b22554d957e7ac8e86ec6cf6c71727a4 -->
- 配備先が決まっていない間の配備の段（deployment-pipeline など）は、既にある Dockerfile・compose.yaml・README の手順を正として記録と整理を行い、決まっていない点だけを質問する。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:deployment-pipeline:b1a86e0f41aa6e3459a2de7b1a258188647faeed5d2b769ede422dbd9702e886 -->
- 配備先が開発者の PC 上のコンテナのときは、環境の段（environment-provisioning など）を Docker の実行環境・イメージ・ボリューム・.env・compose.yaml の設定と読み替え、設計の値を実際にコンテナを起動して1つずつ確かめる。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:environment-provisioning:ac6d4ef03ae6ae98e21f41ed4b710aff15e4e1f0e74529d462247e8e8c7a43a6 -->
- 配備先が決まるまでの手元の監視は、grafana/otel-lgtm を compose の profile で見たいときだけ起動し、ダッシュボードと警報の決まりはファイルでリポジトリに置く（画面からは変えない）。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:observability-setup:542822cb3fe5db425d4384bbb06c49af318db761a9d0ab6afe21eb6c39a2fdc2 -->
- 配備の前の「未コミットの変更が無い」確認は、アプリのソースを対象とし、監査ログとこの段の記録のディレクトリ（ワークフローの記録）は外して判断した。 (learned 2026-09-23) <!-- cid:260923-audit-pool-exhaustion:deployment-execution:3d67f0638e47408d3b6b0958e7ff3bc42f3084cbe4562be73c13e52a103f5a8e -->
- JVM の設定の口に JAVA_TOOL_OPTIONS ではなく独自の MASTERSMITH_JAVA_OPTIONS を ENTRYPOINT の既定の引数の後ろに置く形を選んだ。標準の変数はコマンド行の 75% に負け、起動時の Picked up の1行で JSON のログを崩すため。 (learned 2026-09-23) <!-- cid:260923-colima-spec-up:code-generation:5bee00b6c6fc0ff963eca9371e14bc5dc3c8e19d69c2ccf04583df03488e0340 -->
- 戻しで .env の元の値が要るときは、.env を開かずに、変更の前に .env をリポジトリの外（ホームの下）へ中身を表示せずに複写し、戻すときはその複写を戻す。 (learned 2026-09-23) <!-- cid:260923-colima-spec-up:deployment-pipeline:1d22caeb1205783cdbc97baa8745e5db11703baa067f63ffd6ad472f8ccc968e -->
- Build and Test で、配備するものと同じソースのイメージを配備と同じ上限で負荷の試験済みのときは、依頼者の判断（Q1: A）で配備の前の k6 を省き、配備の後の healthy とスモークテストで確かめる。 (learned 2026-09-23) <!-- cid:260923-colima-spec-up:deployment-pipeline:7d388e8869c0f1041f0b6003ba34d70530499d4da273d77b23819e54cf862e1c -->
- 配備先が開発者の PC 上のコンテナの間の Feedback & Optimization では、費用（Cost Explorer・Trusted Advisor）を VM・コンテナの資源の上限と実測に、設定のずれ（AWS Config）を docker inspect・docker compose config・.env の項目の有無（値は見ない）と、記録した設計の値との1つずつの比べに読み替える。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:feedback-optimization:2092e4de75ef6ca51d922bf2ba8a0bd22190e5194288dfbedaee190202c22398 -->
- 手元の監視を常に動かしていない間は、SLO の判定を Unverified とし、配備の直後・監視の確かめ・負荷の試験・振り返りの時点の値を基準の値として並べ、配備先が決まったときの測り方を書く。目標を緩めて満たしたことにはしない。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:feedback-optimization:fa7ca0460f9a1e1941002f1251ad0d8bece205d0aeef0076a63aaceb8a3b14b6 -->
- Dependabot の gradle の実行の failure は、composite build のサブモジュールの中の依存を更新できないことが原因だった。サブモジュールを Gradle の composite build で組むときは、dependabot.yml の gradle の設定に exclude-paths（vendor/**）を置く。 (learned 2026-09-28) <!-- cid:260925-user-management:ci-pipeline:a81bd5114a167f19a8fe787b4e3f9db0ff11a7b9388be4a58e649c196cc68179 -->
- 監視の確かめで使い捨てのアプリを lgtm に送らせるときは、lgtm を網につないだ後にアプリを起動する（先に起動するとログの送信の失敗の ERROR が出る。role-menu と前の Intent で同じ失敗）。 (learned 2026-10-14) <!-- cid:261004-role-menu:observability-setup:a8d53fd0789113ff432dccf8d823b05492162f4b14a1dc843f02f95381ae4cc3 -->
- JVM が止まったとき（ExitOnOutOfMemoryError・restart no）に拾える警報は ms-app-absent だけで、止めてから Pending まで約 5 分 20 秒、Alerting まで約 10 分 20 秒かかる（role-menu の Observability Setup で初めて鳴ることを実測した）。 (learned 2026-10-14) <!-- cid:261004-role-menu:observability-setup:1a99f2925284ff87c8d2a97c4d253696ad0efae836692e39ddbc374f5dea9697 -->
- ExitOnOutOfMemoryError で JVM が止まる直前の1行（Terminating due to java.lang.OutOfMemoryError）は JSON ではなく Loki に届かない。docker logs と終了コード 3（Exited (3)、OOMKilled false）で見分け、docker compose start で約 10 秒で戻る（role-menu の Incident Response で使い捨ての環境のヒープを 192 MB に下げて実測した）。 (learned 2026-10-14) <!-- cid:261004-role-menu:incident-response:e9b409e49db86bac4a108d2e842106160121cdf9b39d41a62ff90a0f87a52aa6 -->
- CI の時間は run の始めから終わりではなく、検査の job の始めから終わりで測った。a031ba1 の run は前の run の終わりまで約 31 分待ち、run の全体では 59 分だったが、検査の job は 28分37秒で 42 分の線の内だった。 (learned 2026-10-20) <!-- cid:261016-business-data-views:build-and-test:61c8c95d0bef093d17ae4b3d25a4d54b365e106c1b90b5937bacd770b29e3bc3 -->
- CI の gitleaksScan は全ブランチを取得して履歴全体を見る（fetch-depth: 0）。force-push で履歴の秘密を直したあとも、旧コミットを持つ Dependabot のブランチが公開側に残る間は CI が同じ件数で落ちる。PR を閉じてブランチを消し、Dependabot の付け替えを待ってから、原因が分かった再実行として CI を流し直す（perf-targets-review の 805db12 は 1 回目 failure・2 回目 success）。 (learned 2026-10-29) <!-- cid:261027-perf-targets-review:deployment-execution:df9cdb7081fa49e449be5a70dc0e555c530c5126436b2e83cdd8925860fad06c -->
- docker compose up -d --build app は、新しくビルドされたイメージの ID が変わっても、構成が同じだとコンテナを作り直さず旧イメージのまま起動し直すことがある。入れ替えの後に docker inspect でコンテナのイメージ ID と mastersmith:local の ID が一致するかを確かめ、食い違うときは --force-recreate --no-deps を付けて作り直す（perf-targets-review の Deployment Execution）。 (learned 2026-10-29) <!-- cid:261027-perf-targets-review:deployment-execution:f7fb35bc881e9f5dfe7aab6cb8ee90c129b00a0d71a9eaa37ecbd93e686948e3 -->
- CI Pipeline・Infrastructure Design の段が無い Intent の配備の段は、前の Intent の配備の手順（cd-config・deployment-strategy・rollback-runbook）を正として、今回の差だけを書く。スキーマと .env の変更が無いときは、戻しはイメージだけとし、バックアップと戻しの練習は行わない。 (merged 2026-11-04)
- スモークテストの手順を書く前に、(1) 確かめる項目が画面で変えられるものか設定で変えるものかをソースと README で確かめ、(2) 「切り替える」「選ぶ」などの操作が成り立つ前提（候補が2つ以上ある、自動で選ばれない）を手順と監査の期待の件数に入れ、(3) 監査に残るか（AuditEventType）をソースで確かめる。依頼者が画面を触ったときは、監査に残る操作（管理の操作・ログインの失敗・保存を押した回数など）をその場で聞いて期待の件数を合わせ、「済んだ」の答えの後も配備の後の複写で行の数と監査を数えてから判定する。 (merged 2026-11-04)
- CI の結果を記録するとき（Build and Test、main への取り込みの前）は、最後の結論だけでなく試行ごとの結果（`gh run view --json attempt,jobs`）を確かめ、再実行で通ったものは1回目の失敗とそのテストも書く。前の取り込みの後のすべてのコミットが対象で、記録だけのコミットの CI も含める（ソースを変えなくても、新しく公表された脆弱性で落ちる）。 (merged 2026-11-04)
- 内部DB の確かめ（監査の行の種類ごとの件数・利用者の状態など）は、アプリを1回止めて内部DB を複写し（配備の前後のバックアップを兼ねる）、リポジトリの外の小さな Java の道具（H2 の JDBC、`ACCESS_MODE_DATA=r`）で値を表示せずに数える。列の名前を誤ると H2 の誤りの表示に値が出うるため、問い合わせの前に列の名前を確かめる。 (merged 2026-11-04)
- 内部DB の複写と .env の複写はリポジトリの外（`~/.mastersmith-backup/` など、置き場は権限 700、ファイルは 600）に置き、中身を開かない。手順で「終わりに消す」とした複写は、その段の終わりに消したかを確かめる。 (merged 2026-11-04)
- 監視の式の値を確かめる要求は、負荷の試験の場面ではなく、待ちを入れた少ない要求（1 VU で繰り返しごとに 1〜2 秒待つなど）で、指標の送信の周期（1 分）を複数またいでくり返し送る。新しく現れた系列の最初の 1 件は Prometheus の increase に数えられない。待ちの無い場面を送ると lgtm の中の受け手と Tempo がメモリの上限で止まり、内部DB の監査の行も膨らむ。 (merged 2026-11-04)
- 戻し先のイメージを確かめるときは、イメージに HEALTHCHECK が無いため、起動の直後の running を健全と判定せず、compose の健全性の確かめと同じ bash の `/dev/tcp` で `/actuator/health` の 200 を待つ。 (merged 2026-11-04)
- Dockerfile は手元の WAR を写すだけで、イメージを作り直しても WAR は作り直さない。配備の手順を書く前にこのことを確かめ、配備の前に WAR の SHA-256 と中身（直しの値など）を確かめる。 (merged 2026-11-04)
- colima の VM のコンテナとファイルを受け渡す一時の場所は、`mktemp -d`（macOS の一時ディレクトリ。VM から見えない）ではなくホームの下（権限 700）に置く。 (merged 2026-11-04)

## Code Style

- ドキュメントや説明文中でファイル・ディレクトリのパスに言及する際は、絶対パスではなくプロジェクトルートからの相対パスで記述する（例: `aidlc/spaces/default/memory/project.md`）。 (learned 2026-09-22) <!-- cid:260922-auth-audit-base:intent-capture:64b945310a4c7f4ef0d40407fd6e745789656be88a6ef14e9a0b837f7f39aba8 -->
- 機能設計のエンティティには、アプリが独自に持つデータだけを書く。フレームワーク（Spring Boot・Spring Security・Micrometer Tracing・Actuator・SLF4J など）が提供する仕組みや設定値（ログの1件、トレースの情報、接続設定、アクセス制御の設定など）はエンティティにせず、求める振る舞いを決まり（rules）として書く。 (learned 2026-09-22) <!-- cid:260922-auth-audit-base:functional-design:5b233759b75786707968b69af9d5714d020655d16743c718d001c1fbf9b747db -->
- `TraceAspect` は web・service・domain・repository の層の引数と戻り値を TRACE のログに文字列で出す。個人に関する値（検索の文字・メールアドレス・氏名）と秘密（トークン・招待の URL・宛先）は、controller が受け取る時点の型から repository の口まで、すべての経路で文字列にすると伏せ字になる型のまま渡し（String で受けて中で包む形はログに出る）、どこで文字列にするかを設計に書く。または `TraceAspect` の対象の層の外の用途名の下位パッケージに置く。 (merged 2026-11-04)
- 排他の上限切れなどの例外の連なり（H2 の MVStoreException の文など）には、行の全部の列の値が入りうる。行の排他の読み取り（FOR UPDATE・@Lock）だけでなく書き込みの問い合わせ（@Modifying）も含めて経路を洗い出し、例外は `TraceAspect` の対象の層の外へ出さず、受けた所でクラスの名前だけをログに出す。 (merged 2026-11-04)

## Tech Stack

- フロントエンドはデザインシステム make-you-chic-ui（React + TypeScript）を使用する。Gitサブモジュールとして `vendor/make-you-chic-ui` に取り込み、組み込み手順は同リポジトリの `docs/integration-guide.md` に従う (learned 2026-09-22) <!-- cid:260922-auth-audit-base:approval-handoff:7a1d387f49e374e248043714bbcc0610684f5aed20a6ce859ddbffce95ae8cbc -->
- opentelemetry-logback-appender は 2.28.1-alpha に固定する（Spring Boot 4.1.1 が持つ OpenTelemetry 1.62 と、より新しい版が食い違い、外部エクスポートの有効時に失敗するため）。Spring Boot を上げるときは、この部品の版も合わせて見直す。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:code-generation:a65f53c1e0eafca70f00ce4b9dc6098dc082c5fb53eb56087ea054ce665295c9 -->
- H2 2.4.240 の複数スレッドの詰め直しの競合（JVM の assert で検出）を避けるため、-Dh2.compactThreads=1 を Dockerfile・テストの JVM・E2E の起動でそろえる。H2 を上げるときは、この設定の要否を見直す（user-management の U3）。 (learned 2026-09-28) <!-- cid:260925-user-management:code-generation:6e45dd4524a1ab9eab84a737f001fc25ad31a28d2c9b96a50c12b5ce4442c49b -->
- Jackson の High（GHSA-q4xh-88c3-wmh7）は変更の前の develop でも OSV-Scanner を止めていた。networknt 3.0.7 を取り込むと 3.2 系に上がるため、3.0.6 に戻して Spring Boot の 3.1 系のまま 3.1.6 に上げる形（G4: A）にした。Spring Boot の BOM の版のプロパティで上書きする口が無く、Jackson の BOM を platform で読む形になった。 (learned 2026-09-29) <!-- cid:260928-quality-followup:code-generation:048ed0cead6ebbc806d232e24e1ef834a59dbff4d954455d02d8d510bda79173 -->
- 対象DB のコンテナの版は長く支援される版に限らず、verify（対象DB の結合テスト）を通った最新の版を使う（quality-followup で mysql 26.7・mariadb 13.0 に上げた）。compose.yaml・docker/perf/compose.yaml・TargetDbImages の digest を一緒に上げる。 (learned 2026-09-29) <!-- cid:260928-quality-followup:code-generation:c720e9319af164201f7a46bcde302cd8f962359ad94dcfaf9752c3de5d76a917 -->
- Jackson の上書きは 3.1.7 とする（user-admin の B1 の前に、High の脆弱性 4 件を解くため 3.1.6 から上げた。Spring Boot の管理の系列 3.1 のまま、Jackson の BOM を platform で読む形は同じ）。Spring Boot を上げるときは、この上書きの要否を見直す。 (learned 2026-10-03) <!-- cid:260930-user-admin:code-generation:7230d7ba8d3a9a1dcd450f2fa37c323a71091a36a61422a5c44eaa74245fe919 -->
- 一意の鍵・主キーの待ちの上限を承認済みの「3 秒」ではなく H2 の既定の約 2 秒のままにした。捨ての試しで書き込みの待ちにはヒントが効かず約 2,000 ms で切れ、SET LOCK_TIMEOUT はプールの接続に残ってほかの機能に響くため。差は reliability-design.md 1.3 と logical-components.md 4節に記録した。 (learned 2026-10-06) <!-- cid:261004-role-menu:nfr-design:d71ee16a91a075569bb61cd2f8c0ef27095b1bcca3b4ffb6f44ceb31c47ae532 -->
- 深さと別名の上限を LoaderOptions と LimitingParser の両方に同じ値で渡す形にした。試しの T1' で LoaderOptions の方が小さいと部品の例外（位置なし）が先に出て SYNTAX に写るため。値を2か所に渡す手間の代わりに、区分と位置を必ず保てる。 (learned 2026-10-06) <!-- cid:261004-role-menu:nfr-design:0abb2baa97c3474516c45beb3aba6c0e0d584e971be80c3cf81371297320ac26 -->
## Decided

- DECIDED: 監査記録の共通の仕組みはauth-audit-foundation Intentでは作らず、業務データのCRUDを扱う後続Intentで共通化を検討する (Stage scope-definition) (learned 2026-09-22) <!-- cid:260922-auth-audit-base:scope-definition:1052d63284043bef45745f943b9ecbd4368ac39b0f51a2ffda8c8cfd4a919d7a -->
- DECIDED: ログアウトは画面側でのトークン破棄とリフレッシュトークンのサーバー側無効化とし、アクセストークンの失効の仕組みは持たない。そのためアクセストークンの有効期限は短く設定する (Stage scope-definition) (learned 2026-09-22) <!-- cid:260922-auth-audit-base:scope-definition:6f6fc76572cdae83ee921248b14cd67754cd97f0866df35af930691d1f178539 -->
- DECIDED: ログイン後の画面は make-you-chic-ui の AppShell（サイドバー＋トップバー＋コンテンツの3領域）の中に置き、ログイン画面は AppShell の外に独立したレイアウトとして置く (Stage approval-handoff) (learned 2026-09-22) <!-- cid:260922-auth-audit-base:approval-handoff:e0f5bda9a9313505b73c5d90ef95850833f5c172df5799b28890ef44ab51cad2 -->
- 依頼者の判断で、2本使いの解消（接続を返してから記録する・監査専用のプール）ではなく、プールの上限の引き上げを選んだ。コードと既存の監査の決まりは変わらない。その代わりに、同時の数が上限（30）に達すると再び起きうるという危険が残り、要件（FR5）と README に既知の制約として記録する。 (learned 2026-09-23) <!-- cid:260923-audit-pool-exhaustion:requirements-analysis:212bb4696c70283d6edbf364e5565121bf51cabe7f1c875fdf9d296fe94f247a -->
## Scope Overrides

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
- NEVER 最後の有効な管理者を無くす操作（管理者の印を外す・利用を止める）を受け付けない (affirmed 2026-09-30)
- NEVER API の応答（成功の応答を含む）に、パスワードのハッシュ値・招待のトークンのハッシュ値・ロックの判定の内部の値を含めない (affirmed 2026-09-30)
- NEVER 業務データの SQL に、利用者の入力（スキーマ名・テーブル名・カラム名・並べ替えの列・検索の値）を文字列のまま組み込まない（識別子は適用中の DSL にある名前と照らしてから引用し、値はバインド変数で渡す） (affirmed 2026-10-18)
- NEVER 業務データの行の値（主キーの値を含む）を、アプリのログ・トレースの属性・エラー応答に含めない (affirmed 2026-10-18)
- NEVER アクセシビリティの検査の規則を外して（`disableRules`・規則の絞り込み・要素の `exclude` など）axe の違反を通さない (affirmed 2026-10-18)
- NEVER 認証の値（JWT・リフレッシュトークン・Authorization ヘッダー）を、伏せずに負荷の試験（perf）の記録へ置かない (affirmed 2026-10-29)
- NEVER 自作の SVG（アプリのアイコン・favicon）に、スクリプト・イベントの属性（onload など）・外部の参照（外部の URL の href・読み込み）を入れない (affirmed 2026-11-02)

## Mandated

- ユーザー提供の参考資料は `reference/` に置き、Git管理対象外（.gitignore）とする。成果物やコードコメントに `reference/` 配下のファイルパスを書かない。内容は読み込んで理解し、必要な結論・要点を直接ドキュメントへ書き込む。 (learned 2026-09-22) <!-- cid:260922-auth-audit-base:intent-capture:602ae1e8f7161f7425d243033ebadc4ff4bfc9635bf7648a8fcd72e58e52d24d -->
- 生成するソースファイルの先頭には、言語のコメント構文に合わせたApache License 2.0の標準ヘッダーを必ず挿入する。年は `2026`、著作権者名は `agwlvssainokuni`。 (learned 2026-09-22) <!-- cid:260922-auth-audit-base:intent-capture:7963368b1341ed7275bfc2aa238b7b4892e9bf1121d0d3ce97166a3603529af0 -->

<!-- Populated by practices-discovery affirmation gate. -->
<!-- Format: ALWAYS [behavior] (affirmed [date]) -->
<!-- Example: ALWAYS use Result<T,E> for fallible operations in service layer (affirmed 2026-05-17) -->

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
- ALWAYS 利用者の状態（停止・管理者の印）の判定は、ログインの照合・トークンの更新・アクセストークンの認証のすべてでサーバー側で行い、画面で操作を隠すことを代わりにしない (affirmed 2026-09-30)
- ALWAYS 利用者の権限・状態を変える管理の操作（管理者の印の付け外し・利用停止と再開・ロックの解除）は、操作した人・対象の利用者・結果を監査に残す（拒否した操作を含めるかは要件で決める） (affirmed 2026-09-30)
- ALWAYS 役割・権限による判定は、API ごとにサーバー側で行い、メニュー・画面の出し分けを代わりにしない (affirmed 2026-10-04)
- ALWAYS 主キーの無いテーブルとビューの行の更新・削除は、画面で操作を隠すことに頼らず、サーバー側で拒否する (affirmed 2026-10-18)
- ALWAYS カラムの主権限が NONE（読めない）のカラムは、業務データの API の応答に含めない（画面で隠すことを代わりにしない） (affirmed 2026-10-18)
- ALWAYS 対象DB へ書き込む場合は、書き込みの接続と資格情報を読み取りと分けて環境変数（`.env`）だけから受け取り、書き込みのアカウントには DSL の対象のスキーマの行の操作（`SELECT`・`INSERT`・`UPDATE`・`DELETE`）だけを与え、DDL の権限を与えない (affirmed 2026-10-18)

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
- 前の Intent では、監査を別スレッドに移すとトレースIDと「確定の後に記録」の決まりが変わるため、接続を2本使う形を受け入れていた。直し方を決めるときは、この決まりと既存の結合テスト（AuditAuthenticationEventsIT・AuditTraceIdIT・AuditRollbackIT・AuditWriteFailureIT）を守れるかで比べる。 (learned 2026-09-23) <!-- cid:260923-audit-pool-exhaustion:reverse-engineering:15498ffe61fe6ff56f34e8d23e9d51408e48990f74de543a12304ade5b25f156 -->
- U3 の決定（DSL の上限を 10MB）に合わせて、レビューの済んだ U2 の NFR 要件の文書を書き換えてしまい、レビューが今の中身を確かめない状態になった。依頼者の Request Changes で直し、U1 から順にレビューし直した。別の単位の決定で、レビューの済んだ文書を直す必要が出たときは、先に依頼者に何を変えるかを尋ね、Request Changes を経てから直す。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:nfr-requirements:37a2f15427ab44b01ae637d198e5504666ce37d4cbb61182a6c7d3591c157885 -->
- 想定の規模の既定の DSL の大きさを試算したところ、要件の上限 5MB を超えることが分かった（約 5.3MB）。依頼者は書式を変えずに上限を 10MB に上げることを選び、承認済みの文書は書き換えずに差の一覧を U3 に記録した。数値の上限を決める段では、上限と、機能が作る最大の出力の大きさを突き合わせる。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:nfr-requirements:8891494975dc6b29448592cba1ee2e02f491b05f3754c55c08f7550e6491096e -->
- U5 の NFR 設計で、大きさの判定の順序（文字列にする前に判定）だけが求められていたのに、送り方（バイト列のまま送る）まで変えてしまい、承認済みの機能設計（テキストとして読んで送る）と食い違った。NFR 設計で承認済みの設計と違う作りにするときは、差の一覧に載せ、依頼者に確かめてから書く。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:nfr-design:57189423d73219a9a42678de200df1e2b60fdfaab2af61e8b30b01c09ff1253b -->
- 部品の候補は Maven Central で最新の版と依存を確かめてから試した。networknt の最新の版 3.0.7 はアプリ全体の Jackson を Spring Boot の版から引き上げるため、Spring Boot と同じ 3.1 系で動く 3.0.6 を選んだ。新しい依存は、推移依存で既存の部品の版を引き上げないかを依存の木で確かめる。 (learned 2026-09-23) <!-- cid:260923-dsl-schema-loader:nfr-requirements:2b9eaee2db8c2c15c8eb0fb8d39ebfd1a3ce4694b1fba5f199d3e053922d2a28 -->
- 設定のずれを確かめるときは、compose の既定値と各 PC の .env の値を分けて見る（メモリの上限は compose の既定が 1g で、2g はこの PC の .env の値だった）。承認済みの記録と食い違ったときは、記録を書き換えず、ずれの記録に根拠とともに明記して依頼者に諮る。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:feedback-optimization:f7aed2210bbb992c5bb115494a313c935d143971bc23f6d98823b1d3881904e4 -->
- 直し方が決まっているがまだ直していない指摘に後の単位が頼るときは、書く担当に直した後の形を伝えて設計させ、差をその単位の上流との差に記録する（承認の場での直しが後の単位に及ばないようにするため）。 (learned 2026-09-27) <!-- cid:260925-user-management:functional-design:4cdbe8610f0455d21fa032542bb83dc7ef04c4f09a4f319eb976c2a7e670d38e -->
- 「Gradle の依存の取得元は Maven Central だけ」は依存の解決に当てる決まりで、ビルドのプラグインは Gradle Plugin Portal から取ってよい（java-mustache-processor のプラグインも同じ。user-management の U1 の NFR 要件で依頼者が決めた読み方）。 (learned 2026-09-27) <!-- cid:260925-user-management:nfr-requirements:ed99b4844459fe7dcf025d402a9b76c73f562f463c811b38fb76265c1cdff6d7 -->
- Playwright で行うアクセシビリティの検査や画面の時間の測りは、操作の流れではないため、team.md の「E2E は Intent ごとに代表の流れを1本まで」の本数に数えない（user-management の U4 の NFR 要件で依頼者が受け入れた読み方）。 (learned 2026-09-27) <!-- cid:260925-user-management:nfr-requirements:f356f81be21385b86e602668c00923b24918051ddaa4893207883bf896ba1e51 -->
- 安全の決まりの差し込み口（SecurityRuleContributor）の order は、単位の番号ではなく機能の名前で 100 台ずつ割り当てる（auth 100 台・access 200 台・invitation 300 台・appearance 400 台）。x00・x50 はテストの決まりが使う。 (learned 2026-09-27) <!-- cid:260925-user-management:nfr-design:6cd599535143c30042a6f07aa23710d31c306d2e753ef1f60d0e86819726aa58 -->
- E2E の検査で API の答えを差し替えるときは、見本を1つにして画面の側の型を付け、本物の応答と項目の名前・型が一致することを流れの E2E で毎回確かめる。 (learned 2026-09-27) <!-- cid:260925-user-management:nfr-design:fd8fb66e2438a145b302825a2ae3649641ea6e23dacf4b19325f4589d3c1a77b -->
- 95 パーセンタイルの式を書く前に、その指標に +Inf 以外のバケット（le）があるかを確かめる。式が NaN を返すときは、要求が無いためと決めつけず、要求を送ってから式を流し直す（user-management の Observability Setup で、http.server.requests と mastersmith.mail.send にバケットが無く、ログインなどの p95 の警報3件が前から鳴らない状態だったと分かった。前の Intent は NaN を要求が無いためと誤って記録していた）。 (learned 2026-09-28) <!-- cid:260925-user-management:observability-setup:4962fa019abf98a8a9434901947eb969b172ef85029c5b9eb698a9a8746259fe -->
- Forbidden「メールアドレスをアプリのログとエラー応答に含めない」は、メールアドレスそのものを含めないことと読み、先頭の1文字＋***＋@＋ドメインの伏せ字（EmailAddress.mask）は反しないとみなす（260929-log-deps-cleanup の F1: A）。監査の記録の失敗の ERROR のキー enteredEmail は、手で記録を補うための既知の例外としてメールアドレスそのものを載せる（260925-user-management の U4 の決定、260929-log-deps-cleanup の R-01 を受けた依頼者の決定）。 (learned 2026-09-29) <!-- cid:260929-log-deps-cleanup:build-and-test:b77f61b77306b44fae66d6f208f1f54d7c927a0b388848d662efeefa682f5847 -->
- 承認の場で依頼者が決めたこと（指摘の扱いの選択など）は、承認の操作の理由だけでなく、その段の成果物の「承認の場の決定」の節にも書く。user-admin の NFR 設計で、U3 R-01 のまとめた手当ての決定が承認の理由にしか書かれず、承認済みの文書と監査の記録に残らなかった（Infrastructure Design の U3 の Q2 で、上流との差として記録した）。 (learned 2026-10-01) <!-- cid:260930-user-admin:infrastructure-design:71c7249af67f2e226c4b69659cae40fc7fcdd4fd214b30014c8956e892a067f6 -->
- 契約・設計・まとめの確認の要約・説明用のコードの断片を書く前に、触れる既存のものをコードで確かめる: エンティティと列挙（AuditEventType・AuditResult など）、エラーの仕組み（ProblemType は code と状態コードを1つに固定、GlobalExceptionHandler）、セキュリティの決まり（SecurityConfig の許す道と既定）、既存の仕組みが要求の流れのどこで動くか（ログインの前か後か）、使い回す設定をほかに使っている箇所、ほかの単位の型と名前、承認済みの機能設計と契約の名前。make-you-chic-ui の部品の口は、報告ではなく部品のソース（上流は読み取りだけの `git show`）で確かめる。 (merged 2026-11-04)
- 警報・手順書・ログの問い合わせ・スモークテストの記録に「既存の警報で拾う」「監査に残る／残らない」と書く前に、その失敗の状態コード・ログのレベル・監査の種類（AuditEventType）をソースか実際の要求で確かめる。 (merged 2026-11-04)
- 起動のログなどを確かめるときは、個人に関する値を出さず、ロガーと項目の名前と件数だけを出す形で見る（初期管理者の INFO にメールアドレスが出るなど、既存のログに値が入っていることがある）。 (merged 2026-11-04)
- レビュー役への依頼には、最初から次を書く: 記録の見出しは `## Review` の1つだけで、その後に H2 を置かず小見出しは H3、Status は単一の語（New）、表のセルの中に `|` を書かず並べるときは「・」でつなぐ、git・gradlew・docker build を使わず Read だけで確かめる。前の段の承認で決めたことをレビューの前提に書くときは、根拠のファイルの場所も伝える。 (merged 2026-11-04)
- 答えの記録（`aidlc engine log answer`）は依頼者の返事1回につき1回しか受け付けられない。複数の問い（複数の単位の問いを含む）をまとめて尋ねたときは、その返事の全問の答えを1回で記録し、単位ごとに記録が要るものは返事を分けて尋ねる。まとめの確認の答えは `--checkpoint summary-confirmation` と `--questions-file` を付けて記録する。追加の質問のファイルを分けたときは、まとめの確認がどちらのファイルに求められるかを確かめる。 (merged 2026-11-04)
- 決定の要約を出す道具（review-brief summary）が動かないとき（main(argv) を持たないという誤りなど）は、答えのまとめだけを示して確認する。 (merged 2026-11-04)
- 成果物は質問の確認の後に書く（確認の前にシェルで書くと記録された書き込みが無く、レビューの依頼が拒まれる）。担当に成果物を書かせた後は、レビューの依頼の前に指揮役の Edit で各成果物を触り直す（担当の書き込みが記録されないことがある）。 (merged 2026-11-04)
- 担当への依頼は、長い生成・統合を1回にせず手順やファイルの単位に分け、1ファイルずつ書いて保存させる。次の回の担当には前の回の決定と記録の場所を伝える。依頼に差し込む本文（Testing Contract など）は、承認の前に差し込み漏れ（`{CONTRACT}` のまま）が無いかを確かめ、大きな出力は保存したファイルを最初に読ませる形にする。長い再現の試みは、頼む前に時間の上限を決める。 (merged 2026-11-04)
- Reverse Engineering で依頼者が Full rescan を選んでも、記録上の範囲（Scope of Analysis）は実際に深く読んだものだけとし（`kind: partial`、`./` を `analyzed.paths` に入れない）、前回より狭いとき（NARROWER）は流し読みに下げた範囲を書く。部品の名前は英数字の ID にし、`components` と component-inventory.md の見出しを文字どおり一致させる。記録するコミットは `git rev-parse HEAD` で取り、スナップショットの source の値と混ぜない。確かめた事実と未検証の仮説は分けて書く。 (merged 2026-11-04)
- 質問を作るときは、依頼文・前の段・team.md・project.md で決まっている点を質問にせず冒頭に「決まっていること」として書き、判断が分かれる点だけを尋ねる（Practices Discovery ではチームの進め方に当たるものだけを尋ね、機能の中身は要件・設計の段に回す）。答えが team.md・project.md の決まりや別の答えと食い違いうるときは、まとめの確認の前に追加の質問（F）で確かめる。まとめ方・順の選択肢は、出す前に依存の図の上で順に並べられるかを確かめる。 (merged 2026-11-04)
- mob や支援役の意見を取り込むときは、専門の知識で決まるものはリードが決め、利用者の体験や要件との差に当たる点（M1〜）だけを依頼者に尋ねる。M の答えで答えの中身が変わったときは、反映する前にまとめの確認をやり直す。 (merged 2026-11-04)
- Construction の設計の段の単位ごとのレビューは1回きりで、指摘はその場で直さない（直すと記録したレビューが今の中身を覆わなくなる）。指摘は承認の場へ持ち越すことを依頼者に伝え、全単位を通してから承認の場で Request Changes を1回にまとめて直す。advisory のレビューの段で Request Changes を受けたら、直す前に段のやり直し（redo jump）を行ってから直し、まとめの確認を確かめ直してレビューに回す。 (merged 2026-11-04)
- 同じ段で複数の単位を順に進めるときは、先の単位の答えとレビューの指摘を後の単位の質問の案と依頼に先に反映し、E2E のファイルの番号・確かめる組・k6 の場面の名前・NFR の枝番の割り振りを、段の始め（依頼者に尋ねる前）に単位の間でそろえる。複数の単位にまたがる直しを並べて頼むときは、継ぎ目の形（項目の持ち主・入れ子か平らか・列挙の値）を依頼に書いてそろえる。 (merged 2026-11-04)
- NFR 要件の枝番は単位の中で .1 から振り、上流の枝番と同じ番号は同じ意味でだけ使い、足す要件は上流の最後の枝番の次から振る。当たる上流の NFR の ID が無い要件は、応答・同時性・指標を NFR6、画面の側のセキュリティ・依存・テスト・起動を NFR9 の枝番に寄せ、寄せたことと対応表を成果物の冒頭に書く。 (merged 2026-11-04)
- traceability.json の書き方: 機能設計では unit-of-work-story-map.md でその単位が出てくるストーリーの AC をすべて並べ、受け持たない AC は主の単位へ Deferred にし、一部だけ受け持つ AC を OK にするときは「部分」と target に書く。rules.md を作らない ui の単位の target は functional-spec.md の D の番号と流れの番号を指す。NFR 要件では上流の枝番ごとにも行を立て、OK の target にはその単位で定義した ID だけを書く。測っていない目標は OK にせず Deferred（持ち主の段）にする。 (merged 2026-11-04)
- コード生成の計画を書く前に、(1) その単位の各設計の段の承認の場の決定（監査ログの Approve・Request Changes の文言）を洗い出して計画に反映し、(2) 変える型・表・移行の番号・API・画面の骨組み・ログのキーの名前を使っている既存のテストと E2E を検索で洗い出して影響の範囲に入れ、(3) 同じイメージ・同じ値を持つファイルを洗い出す。段を始める前に前の段の計画の未実施の手順（チェックの付いていない Step）を洗い出し、承認の場の前に全単位の code-summary.md の「依頼者に確かめたいこと」の節を並べる。 (merged 2026-11-04)
- DB や部品の振る舞いに頼る設計の前提（排他の後の数え・行を取る順・待ちの上限・メモリの量など）は、文書を書く前に、リポジトリの外の捨ての試しのコードで確かめる。前提が崩れた点は追加の質問（F）で依頼者に確かめてから文書に反映し、結果の要点（版・設定・数値・設計への意味）を成果物に写す。 (merged 2026-11-04)
- 記録の有無（「記録が抜けている」など）は、確かめる前に依頼者に言わない。 (merged 2026-11-04)
- zsh ではコマンドを変数に入れても単語に分かれない。手順の中で繰り返すコマンドは関数にする。 (merged 2026-11-04)
## Interpretations

## Deviations

## Tradeoffs
