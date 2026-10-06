<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->


- 2026-10-06T14:31:11Z — 基盤の変更が無い library の単位のため、cicd-pipeline.md は新しい設計ではなく、既存の CI と verify の段（0〜9）に足すテストを対応づけた記録として書いた（project.md の学び）
<!-- aidlc-wave-memory:cross-cutting:52544ab4ba8d13f06d349172287bdc1279fffd745b27b42e0bb852c4a1bbdde5 -->

- 2026-10-06T14:31:11Z — traceability.json は NFR 設計の 23 の ID をすべて並べ、基盤（段・タスク・置き場）に当たるものを OK、コードの作りだけで決まるものを理由と確かめの段つきの N/A にした（前の Intent の library の単位と同じ形）
<!-- aidlc-wave-memory:cross-cutting:82ba1da3b968da5d6f9caed0edcce089ccb1cd3e1804d5d03935158e00bbe6e3 -->

- 2026-10-06T14:31:11Z — JSON Schema の v2 への替えで名指しを直す箇所を、コードの検索で6つ（build.gradle.kts の2か所・DslFormat・DslSchemaPublicationIT・画面の2ファイル・README）に絞って一覧にした
<!-- aidlc-wave-memory:dsl-v2:3d78c09e1775aebf06b80668a2f4fad298713d8a3d8ebd38b1ab71c943f36384 -->

- 2026-10-06T14:31:11Z — traceability.json は NFR 設計の 25 の ID をすべて並べ、検査・ビルド・道具・測る段に当たるものを OK、コードの作りだけで決まるものを N/A にした
<!-- aidlc-wave-memory:dsl-v2:27f2b71b772e82d8a0ca7d4a73182e500912661be60c3b943845fce4551ce3e9 -->

- 2026-10-06T14:43:10Z — 配備・内部DB・接続プール・設定は変えず、足すのは移行1つ（見込み V10__u3_group.sql）とダッシュボードの区画だけと読んだ。CI と verify は既にある仕組みの記録として書いた（project.md の学び）
<!-- aidlc-wave-memory:group:b02c3244ca071f3f6b09611a9cd2b37c9c53520c58fc9a709153b3f0f96b04ce -->

- 2026-10-06T14:43:10Z — traceability.json は NFR 設計の 32 の ID をすべて並べ、基盤（設定・移行・道具・監視・段）に当たるものを OK、コードの作りだけで決まるものを理由と確かめの段つきの N/A にした
<!-- aidlc-wave-memory:group:5e46a93fd1cc3b0d39b8ed17bc8783668f126d59979bdbef971dd77d4471471b -->

- 2026-10-06T15:00:45Z — 配備・接続プール・compose・依存は変えず、増えるのは移行2つ（見込み V11・V12）と application.yaml の2行（B6 の StatementCreatorUtils: OFF、B1 で足す共通の max-swallow-size）だけと読んだ
<!-- aidlc-wave-memory:role:40eeb9364f7be7ba7dd2b14002c51f4cf47dbc48329006c2829a62194ef8ff9e -->

- 2026-10-06T15:00:45Z — traceability.json は NFR 設計の 45 の ID をすべて並べ、基盤（設定・移行・道具・監視・段）に当たる 29 件を OK、コードの作りと画面の単位の持ち物を理由つきの N/A にした
<!-- aidlc-wave-memory:role:a9ea22f810559f1960d7533de8ce34ea42147214499d48218ceefad9459e0966 -->

- 2026-10-06T15:15:02Z — 読み取りだけの単位のため、基盤の変更は同梱するアイコンの一覧のファイル1つだけと読み、CI と verify は既にある仕組みの記録として書いた
<!-- aidlc-wave-memory:navigation:448bf74f32025aea0fc37766e9aab839344b7fb3963bac3d1afecca1dc5970b2 -->

- 2026-10-06T15:15:02Z — traceability.json は NFR 設計の 33 の ID をすべて並べ、基盤（同梱・段・道具・監視）に当たる 17 件を OK、コードの作りと他の単位の持ち物を理由つきの N/A にした
<!-- aidlc-wave-memory:navigation:34b7b606853e39be45c975cea8d8909a3a7b7a3e73831813068a500f0bd950e7 -->

- 2026-10-06T15:15:02Z — 画面だけの単位のため、基盤は WAR に同梱する画面の成果物・入口の data router・遅延読み込み・ApiDownload の headers・E2E の差し替えの口だけと読んだ
<!-- aidlc-wave-memory:role-admin-ui:aaf753e089e7de9f4d012667edbdb7e754bc8550a2a648ca62d1253e8db87233 -->

- 2026-10-06T15:15:02Z — traceability.json は NFR 設計の 38 の ID をすべて並べ、基盤（配信・段・E2E・記録）に当たる 27 件を OK、画面のコードの作りを理由つきの N/A にした
<!-- aidlc-wave-memory:role-admin-ui:cf72d4ca1b544d46e010ddb3d473de65d070d31a69b47012903ceef5c911bb42 -->

- 2026-10-06T16:18:07Z — 画面だけの単位のため、基盤は WAR に同梱する画面の成果物・遅延読み込み・make-you-chic-ui の固定先の更新・E2E だけと読み、CI と verify は既にある仕組みの記録として書いた
<!-- aidlc-wave-memory:app-frame-ui:223c619cf740e3cfbb94cab20c945aa8ffe1989cee0b8f06fac85c1df4f7aa6c -->

- 2026-10-06T16:18:07Z — traceability.json は NFR 設計の 29 の ID をすべて並べ、基盤（固定先・段・E2E・記録・保存）に当たる 23 件を OK、画面のコードの作りを理由つきの N/A にした
<!-- aidlc-wave-memory:app-frame-ui:7a7fcc9ee8a2d70f5ccd2fbaaf9300ccc9c04bb6fba7aa62d3603cc830f8726a -->
## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->


- 2026-10-06T14:31:11Z — ESLint の決まりのテストの置き場を、logical-components.md 2節の『コード生成で決める』から、Vitest の今の対象の frontend/src/ の下の *.test.ts（node の実行環境）に絞った。差は cicd-pipeline.md 12節に記録した
<!-- aidlc-wave-memory:cross-cutting:2974966811c5203a69e7b192741084fbdd2232a3c21f8852e01621d6b1d346bf -->

- 2026-10-06T14:31:11Z — Q1: A で、本番と同じヒープでの同時の投入の確かめを、B1 が perf/dsl-timing.sh に足す口で行う形にした。承認済みの設計は持ち主（Build and Test）だけを決めていたため、差を cicd-pipeline.md 12節に書いた
<!-- aidlc-wave-memory:dsl-v2:210a591daee46114c66a91efe2bf310aa22109d2d3de6ca474cc772140df0f01 -->

- 2026-10-06T14:43:10Z — 承認済みの observability-design.md 4節の「既存の p95 の警報がグループの API にも効く」は、mastersmith.yaml の p95 の警報3つが uri を1つの道に固定しているため事実と食い違った。文書は書き換えず、Q1: A（区画を足し警報は足さない）で monitoring-design.md 7節に差を記録した
<!-- aidlc-wave-memory:group:55cc87145b5ebc9204de25c42b1a6143dbf15753144dfba448213e1098586b7b -->

- 2026-10-06T14:43:10Z — performance-design.md 2.1 はデータの用意を setup() と書くが、利用者を作る API が無いため、利用者だけは perf/README の SQL の手順を広げる形にした（cicd-pipeline.md 10節）
<!-- aidlc-wave-memory:group:59efc69c99d51cec59802b2b075e84286a8a5c9becef17bc573b3f604a5f16c3 -->

- 2026-10-06T15:00:45Z — 承認済みの jqwik のテスト4つの名前（*Properties）は Gradle の test・integrationTest のどちらにも当たらず動かないため、*PropertyTest に直した（group の読み直しの R-01 の先取り）。差は cicd-pipeline.md 11節に記録した
<!-- aidlc-wave-memory:role:d8474d71aa30df782c282f9819c9df84620e8222911427a2e21de107a697aa69 -->

- 2026-10-06T15:00:45Z — Q1: A で、Tomcat の max-swallow-size を DSL と共通の設定として B1 で足し、B6 で実際の Tomcat で 10 MiB の2つ目に 409 が届くことを確かめる形にした。B1 で入らなかったときは B6 で足して差を記録する
<!-- aidlc-wave-memory:role:5862957618aa938272d478b6d124ae80f473f156101db2336541fc37f1a52e94 -->

- 2026-10-06T15:15:02Z — Q1: A で、承認済みの scalability-design.md 1節（SQL で直接入れる・目安の DSL は既定の生成）を、role の準備の台本に乗せる形（API と import、DSL は2つとも生成の部品で対象DB なし）に替えた。差は cicd-pipeline.md 10節
<!-- aidlc-wave-memory:navigation:29b107c355aac3e3b13c90dd92173d77412365113a698b7e92f9eb419761a237 -->

- 2026-10-06T15:15:02Z — 承認済みの observability-design.md 4節の式の名前 http_server_requests_seconds_bucket は、手元の既存の式の _milliseconds_ と食い違うため、monitoring-design.md 7節に差を書き Observability Setup で確かめる形にした
<!-- aidlc-wave-memory:navigation:0c8765597f89f4bf74c6f3ef199a562ea3ece3fcb369ca5d8fc91dd0c206f192 -->

- 2026-10-06T15:15:02Z — 承認済みの設計と違う作りは無い。画面の側の監視を置かないことは、設計に書かれていない事項を既存の画面と同じ扱いとして monitoring-design.md 1節に明記した
<!-- aidlc-wave-memory:role-admin-ui:62e5c6ae5f64517ed9820c38b6c5a51788d3490d758f0873f528784b8771de02 -->

- 2026-10-06T16:18:07Z — Q1: A で、承認済みの NFR2.10（前後の記録だけ）に、前の値を B9 の変更の前に取る手順と manifest の入口の静的な import に3つの画面の塊が無いことの判定を足した。role-admin-ui の R-01 も同じ形にそろえる。差は cicd-pipeline.md 9節・infrastructure-specification.md 6節
<!-- aidlc-wave-memory:app-frame-ui:fdee72c7b9d2597b519cdf590bbcec692b5bfc2da306a5b1da5dda54761c4bc8 -->

- 2026-10-06T16:18:07Z — E2E の本数の読み方（F は 150 の1本、I は 170 の1本、150 への追加は同じ1本の中）を承認済みの NFR6.3 の読み方として明記した（role-admin-ui の R-03 の手当て）
<!-- aidlc-wave-memory:app-frame-ui:c91368e2822da5b524114b775dadf788e16e5ee18f3eb83872e5ad23bab49376 -->
## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->


- 2026-10-06T14:31:11Z — ESLint の決まりのテストを src/ の下に置く形を選んだ。Vitest の設定を広げずに verify と CI で毎回流れる代わりに、設定のテストが画面のソースの木に混ざる
<!-- aidlc-wave-memory:cross-cutting:a5cd2541cbb8069204151db752e7aa918e0c6b5fe823034a025a16fd813087be -->

- 2026-10-06T14:31:11Z — 同時の投入は重なり方が要求の届く順で変わるため、成功1つと 503 DSL_BUSY 1つの組で判定し、両方が通った回は数えずにやり直す形にした。確実に重ねる形（テストの部品での待ち合わせ）は DslConcurrencyIT に任せ、本番のコンテナではアプリが止まらないことだけを見る
<!-- aidlc-wave-memory:dsl-v2:dc9f3d2fc625527d77082de024539dc5f003632955bed58057be5636d5923456 -->

- 2026-10-06T14:43:10Z — 警報を足さずダッシュボードの区画だけにした。鳴らす先の無い警報と要求の少ない管理の API の p95 の揺れを避ける代わりに、SLO の破れは起動して見るか k6 で測るまで分からない（SLO は Unverified）
<!-- aidlc-wave-memory:group:d5225cc9996872079eaabd678abb298f315228fa08be74c45477c4b6fab27253 -->

- 2026-10-06T14:43:10Z — 区画の p95 はアプリの指標 http_server_requests のバケットで作る。招待の区画（トレースの traces_spanmetrics_latency）と元の指標が違うため、パネルの説明に書く
<!-- aidlc-wave-memory:group:4765e49b45027c416c3eb5aff88000de06d7652f937f6b67d723274c605cc07c -->

- 2026-10-06T15:00:45Z — Q2: A で悪い側のデータを DSL の管理の API と role の import の API で入れる形にした。アプリの検証を通ったデータだけが入る代わりに、準備が B6 の import の口に頼り、照合の対象DB の要否は B4 の計画まで決まらない
<!-- aidlc-wave-memory:role:778663af53cfe61deec8170239fff7aea7c7019228ae909a90d5837fbecf67f6 -->

- 2026-10-06T15:00:45Z — max-swallow-size を広げると、断る要求でもサーバーが最大 10 MiB を読み捨てる。ヒープは使わないが、帯域と時間はかかる
<!-- aidlc-wave-memory:role:bf3976802f01d4eb3404c9bfd4f32f6ff94bd0e1fc27c9beafc23bd7e6d34a23 -->

- 2026-10-06T15:15:02Z — k6 の場面を role と同じ環境で続けて流すため、流す順（準備 → 読み取りの場面 → navMenuBaseline → roleTransferLarge）を決めた。順を守れないときは準備のやり直しが要る
<!-- aidlc-wave-memory:navigation:8fa7ef188a2400187f7d45b6444638ecb31f2471f168173f9e35a7f7061b2809 -->

- 2026-10-06T15:15:02Z — 画面の側の監視を置かない代わりに、画面の失敗は API の側の指標とログ、画面の時間は E2E の測りでしか見えない。配備先が決まったときに見直す
<!-- aidlc-wave-memory:role-admin-ui:db922914094b9f92e9cc5681aa8815e7f47a9f9df89c401408a02e885e604dce -->

- 2026-10-06T16:18:07Z — 古いタブの遅延読み込みの失敗を拾う仕組みは足さず、配備の後のスモークテストの最初に読み込み直す手順で補う（受け入れた制約）
<!-- aidlc-wave-memory:app-frame-ui:166ce74d42004a6ae77f1bc563045db5d2fbb46d33385298cbbdc4f059c3427a -->

- 2026-10-06T16:18:07Z — 統合の形（固定先の専用のコミットを残す fast-forward か squash か）は B9 の計画まで決めない
<!-- aidlc-wave-memory:app-frame-ui:d49bba4625dbb12f371afa286a94ba0aa6454a7d54b99bef634cc03e61525954 -->
## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->

- 2026-10-06T14:31:11Z — verify の時間の増え方（ApiAccessConsistencyIT が既存の起動の文脈を使い回せるか）は Build and Test の実測を待つ
<!-- aidlc-wave-memory:cross-cutting:f690774c64639537209c5b15a13e80aed58dfa9d52aa8eb4c555787cb190e53e -->

- 2026-10-06T14:31:11Z — 同時の投入の口のオプションの名前とやり直しの回数は Code Generation で決める
<!-- aidlc-wave-memory:dsl-v2:e005d96f320582bc090d203e60d938adda561279c3c5cb1b1ed595cd0e1a7735 -->

- 2026-10-06T14:31:11Z — colima の VM 6GiB で使い捨ての環境と配備したアプリを同時に動かすか止めるかは、Build and Test の手順で決める
<!-- aidlc-wave-memory:dsl-v2:cfe677f3ed55d41a4ea86692ab34f0ade3f508c22ac05e69a243b4432fbe7ea2 -->

- 2026-10-06T14:43:10Z — ダッシュボードの区画の式の名前（uri・le・service_name）は Observability Setup で起動して確かめる
<!-- aidlc-wave-memory:group:cfca095d027e92bd1b74d1b7269e9020a9ceaf5c1dcf9d4d26f9c1fdc6f499e6 -->

- 2026-10-06T14:43:10Z — 配備の前の内部DB の複写と前の版での戻しの練習は、U4 の移行と合わせて deployment-pipeline で1回にまとめる
<!-- aidlc-wave-memory:group:3d855cd59337a7e4f4cf15dccaee2f2d9bb4fab13382c6988800f44be6d447b9 -->

- 2026-10-06T15:00:45Z — 照合の対象DB が DSL の準備に要るか（B4 の計画）
<!-- aidlc-wave-memory:role:80e6acca3127359ca884543cc4844101ebedefd4f2333dad8799d3b07d03b917 -->

- 2026-10-06T15:00:45Z — dsl-v2 の承認の場で max-swallow-size の扱いが違う形に決まったときは、role の Q1 A とそろえ直す
<!-- aidlc-wave-memory:role:ecc8bbbf72440f460b8bc06a822607bc180ee42eb1a411eb55172d17f945ce48 -->

- 2026-10-06T15:00:45Z — 手元の監視での hikaricp.connections.acquire の系列の名前と単位は Observability Setup で確かめる
<!-- aidlc-wave-memory:role:97f48f45772ee57c269ae72c324744d2e49bb926cce5d9edb90c185a1decb355 -->

- 2026-10-06T15:15:02Z — 区画の式の実際の名前（_milliseconds_ の付き方と uri の値）は Observability Setup で確かめる
<!-- aidlc-wave-memory:navigation:e93e5c18af9d6765bf66400f8743d4ff979fc86564e3efbf0474422d7ce08c45 -->

- 2026-10-06T15:15:02Z — E2E の番号（140・150）は app-frame-ui（160・170）と合わせてコード生成の計画で確定する
<!-- aidlc-wave-memory:role-admin-ui:c77553d1843dcbb8582b03c23dcb4c95d9d4cad403a83e81d1dfbb8e52bc8fa8 -->

- 2026-10-06T15:15:02Z — data router への差し替えが既存の画面と E2E を変えないかは B8 の最初に確かめる
<!-- aidlc-wave-memory:role-admin-ui:ca1ce023923d621533d31baefb1e412a5617502319020cd75e059bbc97f9350f -->

- 2026-10-06T16:18:07Z — B8 の data router が入ったか、B2 の useLogout の形、E2E の番号、160 の1組あたりの時間、自分の権限の応答の型は B9 の計画の最初に確かめる
<!-- aidlc-wave-memory:app-frame-ui:18059ceb7e099368e179b988367e39306294d21d118be30526fef9edfcc9533f -->

- 2026-10-06T16:18:07Z — role-admin-ui の承認の場で入口の量の判定が違う形に決まったときは、U7 もそろえ直す
<!-- aidlc-wave-memory:app-frame-ui:bb916402b9fafedd9f92596b1fccb4336b6e2e72f4f8f17c06bc742aadda89e5 -->
