# 配備・運用・負荷の試験・監視の細かい学び

> `team.md`・`project.md` から文を変えずに移した決まり（2026-11-07）。読む時: Build and Test の負荷の試験と、Deployment Pipeline 以降の段（配備・環境・監視・障害対応・性能の確かめ・振り返り）。
> 元の見出しを各行の前の注 `<!-- 元: <ファイル> の <見出し> -->` で示す。
> 文中の「上の…」「下の…」「Code Style の…」「Testing Posture の…」は、元のファイル（`aidlc/spaces/default/memory/` の `team.md`・`project.md`）に残した行を指す。特に「上の『共通の形』」は、`team.md` の Testing Posture の「権限ごとの API の認可のテストの共通の形」の行を指す。

<!-- 元: team.md の ## Testing Posture -->
- 負荷の試験（k6 の `--summary-export` など）の結果に入る認証の値（JWT・リフレッシュトークン・Authorization ヘッダー）を伏せる仕組みを入れるときは、仕組みそのものに伏せ字のテスト（JWT の形が残らない、すでに伏せた入力に流しても結果が変わらない（冪等）、入れ子の JSON・配列・長い文字列の中でも伏せる、JWT 以外の値（数値・場面名）を壊さない）を付け、`./gradlew verify` と CI に入れる。記録（`evidence/`）に置いたものには `gitleaks dir` を流して確かめる。仕組みの置き場は設計の段で決める。

<!-- 元: project.md の ## Testing Posture -->
- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:build-and-test:0bf5838d31cde7d0227a8b8a7218db87d70238e8b3c13aa06ca6b3864dd85c28 -->

<!-- 元: project.md の ## Testing Posture -->
- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:performance-validation:ab2f1782048387afc43d5ecfe6517fd2f601355f934e19d3f106f25a99a14fbb -->

<!-- 元: project.md の ## Testing Posture -->
- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:performance-validation:981941a9bbe608353df1979cdc3e9d9c4a10b9fa6cf276c92273fa3011ab4eae -->

<!-- 元: project.md の ## Testing Posture -->
- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) <!-- cid:260923-colima-spec-up:build-and-test:0b433994defeb0970a91e3befaf62e4fef958b5e241106072014bb819309de6a -->

<!-- 元: project.md の ## Testing Posture -->
- 負荷の試験で接続プールが尽きたかを確かめるときは、使い捨てのアプリにだけ MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics を渡して /actuator/metrics の hikaricp の値を読み、数秒ごとの使用中の数ではなく、待ちの時間切れの累計と借りるまでの待ちの最大で判断する（配備したアプリの公開の範囲は変えない）。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:performance-validation:6183459b552b1e347df4f86017641db5c56a4349202caff390c55c193c899525 -->

<!-- 元: project.md の ## Testing Posture -->
- 軽い API の性能は、投入を重ねて内部DB のファイルが膨らんだ状態（悪い側の条件）のまま測る。メモリの最大（memory.peak）を比べる試験の前は、アプリのコンテナを作り直して前の最大の値を消す。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:performance-validation:b9c43187ce6f38e701503c4be8cbbe11c162c17cd1cb8b6739f6f7ddffc4ca74 -->

<!-- 元: project.md の ## Testing Posture -->
- 応答しないメールの受け手は、Mailpit を docker pause で一時停止して作る（接続は受け付け、何も返さない）。コンテナを止めると接続の失敗になり、時間切れの確かめにならない（user-management の Performance Validation）。 (learned 2026-09-28) <!-- cid:260925-user-management:performance-validation:0ab7ba7e09b5c371df3611ba3a4fbb257134eef98307d4ea68256e5399dae71d -->

<!-- 元: project.md の ## Testing Posture -->
- 接続プールの見積もりを確かめる負荷の試験には、上限に届く形（プールの上限を下げた場面など）を含める。上限に届かない負荷では、見積もりが誤っていても合格する（user-admin の U3 の NFR 要件のレビュー R-02）。 (learned 2026-10-01) <!-- cid:260930-user-admin:nfr-requirements:081ae35e2df4baab72ba31d62c38f71f2901d47862327a22c2a35cf3f79e93ff -->

<!-- 元: project.md の ## Testing Posture -->
- 接続の待ちの最大のように数 ms の目標は1回の値が揺れるため、合否の回を複数回流すことを負荷の試験の計画に入れる（role-menu の U4-NFR2.7b で1回目 23.55 ms、作り直した流し直しで 8.55 ms となり、原因を確かめないまま Not Met で持ち越した）。 (learned 2026-10-15) <!-- cid:261004-role-menu:performance-validation:3f81b1ca69ea7e980dd28498e53f06fd9f29c3af4fec2690253431a0ba6eba68 -->

<!-- 元: project.md の ## Testing Posture -->
- 大きな入力（10 MiB の DSL の投入など）のヒープの見積もりは、設計の段で試しの測定で裏付ける（role-menu の Performance Validation で GC の後でも 573〜746 MB あり、見積もりの 256〜384 MB を超えた。dslMixed の memory.peak は上限 2g の 87%）。 (learned 2026-10-15) <!-- cid:261004-role-menu:performance-validation:373bdf0b74236a3d3a4d9e3e32463b4280b09ff3b58e3aad3927f40d56d0ec6f -->

<!-- 元: project.md の ## Testing Posture -->
- k6 の扱い: `k6 inspect` には `--include-system-env-vars` を付ける（無いと場面の名前が undefined になる）。colima の VM の時計が負荷の間にずれて `http_req_duration` が崩れるため、判定は1回の繰り返しに要求1つの場面の `iteration_duration` で行い（トークンは setup() で取る）、`http_req_duration` は並べて記録する。操作が2つ以上の場面は op のタグが `iteration_duration` に付くかを台本で確かめる。内部DB に SQL で直接入れた試験用の利用者はロックの状態の行が無いため、同時のログインを流す前に1人ずつログインさせて行を作る。 (merged 2026-11-04)

<!-- 元: project.md の ## Testing Posture -->
- 使い捨ての環境を消す前に、確かめの結果（問い合わせの結果・ログの WARN の件数と内訳など）を見て、取り直しが要らないことを確かめてから片付ける。消す前に数える項目は、台本に最初から書いておく。 (merged 2026-11-04)

<!-- 元: project.md の ## Testing Posture -->
- 負荷の試験の台本を書く前に、計画の「Build and Test に引き継ぐこと」の項目を一つずつ台本の手順と突き合わせる。 (merged 2026-11-04)

<!-- 元: project.md の ## Deployment -->
- 配備先が開発者の PC 上のコンテナのときは、環境の段（environment-provisioning など）を Docker の実行環境・イメージ・ボリューム・.env・compose.yaml の設定と読み替え、設計の値を実際にコンテナを起動して1つずつ確かめる。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:environment-provisioning:ac6d4ef03ae6ae98e21f41ed4b710aff15e4e1f0e74529d462247e8e8c7a43a6 -->

<!-- 元: project.md の ## Deployment -->
- 配備先が決まるまでの手元の監視は、grafana/otel-lgtm を compose の profile で見たいときだけ起動し、ダッシュボードと警報の決まりはファイルでリポジトリに置く（画面からは変えない）。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:observability-setup:542822cb3fe5db425d4384bbb06c49af318db761a9d0ab6afe21eb6c39a2fdc2 -->

<!-- 元: project.md の ## Deployment -->
- JVM の設定の口に JAVA_TOOL_OPTIONS ではなく独自の MASTERSMITH_JAVA_OPTIONS を ENTRYPOINT の既定の引数の後ろに置く形を選んだ。標準の変数はコマンド行の 75% に負け、起動時の Picked up の1行で JSON のログを崩すため。 (learned 2026-09-23) <!-- cid:260923-colima-spec-up:code-generation:5bee00b6c6fc0ff963eca9371e14bc5dc3c8e19d69c2ccf04583df03488e0340 -->

<!-- 元: project.md の ## Deployment -->
- 戻しで .env の元の値が要るときは、.env を開かずに、変更の前に .env をリポジトリの外（ホームの下）へ中身を表示せずに複写し、戻すときはその複写を戻す。 (learned 2026-09-23) <!-- cid:260923-colima-spec-up:deployment-pipeline:1d22caeb1205783cdbc97baa8745e5db11703baa067f63ffd6ad472f8ccc968e -->

<!-- 元: project.md の ## Deployment -->
- 配備先が開発者の PC 上のコンテナの間の Feedback & Optimization では、費用（Cost Explorer・Trusted Advisor）を VM・コンテナの資源の上限と実測に、設定のずれ（AWS Config）を docker inspect・docker compose config・.env の項目の有無（値は見ない）と、記録した設計の値との1つずつの比べに読み替える。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:feedback-optimization:2092e4de75ef6ca51d922bf2ba8a0bd22190e5194288dfbedaee190202c22398 -->

<!-- 元: project.md の ## Deployment -->
- 手元の監視を常に動かしていない間は、SLO の判定を Unverified とし、配備の直後・監視の確かめ・負荷の試験・振り返りの時点の値を基準の値として並べ、配備先が決まったときの測り方を書く。目標を緩めて満たしたことにはしない。 (learned 2026-09-24) <!-- cid:260923-dsl-schema-loader:feedback-optimization:fa7ca0460f9a1e1941002f1251ad0d8bece205d0aeef0076a63aaceb8a3b14b6 -->

<!-- 元: project.md の ## Deployment -->
- 監視の確かめで使い捨てのアプリを lgtm に送らせるときは、lgtm を網につないだ後にアプリを起動する（先に起動するとログの送信の失敗の ERROR が出る。role-menu と前の Intent で同じ失敗）。 (learned 2026-10-14) <!-- cid:261004-role-menu:observability-setup:a8d53fd0789113ff432dccf8d823b05492162f4b14a1dc843f02f95381ae4cc3 -->

<!-- 元: project.md の ## Deployment -->
- JVM が止まったとき（ExitOnOutOfMemoryError・restart no）に拾える警報は ms-app-absent だけで、止めてから Pending まで約 5 分 20 秒、Alerting まで約 10 分 20 秒かかる（role-menu の Observability Setup で初めて鳴ることを実測した）。 (learned 2026-10-14) <!-- cid:261004-role-menu:observability-setup:1a99f2925284ff87c8d2a97c4d253696ad0efae836692e39ddbc374f5dea9697 -->

<!-- 元: project.md の ## Deployment -->
- ExitOnOutOfMemoryError で JVM が止まる直前の1行（Terminating due to java.lang.OutOfMemoryError）は JSON ではなく Loki に届かない。docker logs と終了コード 3（Exited (3)、OOMKilled false）で見分け、docker compose start で約 10 秒で戻る（role-menu の Incident Response で使い捨ての環境のヒープを 192 MB に下げて実測した）。 (learned 2026-10-14) <!-- cid:261004-role-menu:incident-response:e9b409e49db86bac4a108d2e842106160121cdf9b39d41a62ff90a0f87a52aa6 -->

<!-- 元: project.md の ## Deployment -->
- CI の gitleaksScan は全ブランチを取得して履歴全体を見る（fetch-depth: 0）。force-push で履歴の秘密を直したあとも、旧コミットを持つ Dependabot のブランチが公開側に残る間は CI が同じ件数で落ちる。PR を閉じてブランチを消し、Dependabot の付け替えを待ってから、原因が分かった再実行として CI を流し直す（perf-targets-review の 805db12 は 1 回目 failure・2 回目 success）。 (learned 2026-10-29) <!-- cid:261027-perf-targets-review:deployment-execution:df9cdb7081fa49e449be5a70dc0e555c530c5126436b2e83cdd8925860fad06c -->

<!-- 元: project.md の ## Deployment -->
- docker compose up -d --build app は、新しくビルドされたイメージの ID が変わっても、構成が同じだとコンテナを作り直さず旧イメージのまま起動し直すことがある。入れ替えの後に docker inspect でコンテナのイメージ ID と mastersmith:local の ID が一致するかを確かめ、食い違うときは --force-recreate --no-deps を付けて作り直す（perf-targets-review の Deployment Execution）。 (learned 2026-10-29) <!-- cid:261027-perf-targets-review:deployment-execution:f7fb35bc881e9f5dfe7aab6cb8ee90c129b00a0d71a9eaa37ecbd93e686948e3 -->

<!-- 元: project.md の ## Deployment -->
- 内部DB の確かめ（監査の行の種類ごとの件数・利用者の状態など）は、アプリを1回止めて内部DB を複写し（配備の前後のバックアップを兼ねる）、リポジトリの外の小さな Java の道具（H2 の JDBC、`ACCESS_MODE_DATA=r`）で値を表示せずに数える。列の名前を誤ると H2 の誤りの表示に値が出うるため、問い合わせの前に列の名前を確かめる。 (merged 2026-11-04)

<!-- 元: project.md の ## Deployment -->
- 内部DB の複写と .env の複写はリポジトリの外（`~/.mastersmith-backup/` など、置き場は権限 700、ファイルは 600）に置き、中身を開かない。手順で「終わりに消す」とした複写は、その段の終わりに消したかを確かめる。 (merged 2026-11-04)

<!-- 元: project.md の ## Deployment -->
- 監視の式の値を確かめる要求は、負荷の試験の場面ではなく、待ちを入れた少ない要求（1 VU で繰り返しごとに 1〜2 秒待つなど）で、指標の送信の周期（1 分）を複数またいでくり返し送る。新しく現れた系列の最初の 1 件は Prometheus の increase に数えられない。待ちの無い場面を送ると lgtm の中の受け手と Tempo がメモリの上限で止まり、内部DB の監査の行も膨らむ。 (merged 2026-11-04)

<!-- 元: project.md の ## Deployment -->
- 戻し先のイメージを確かめるときは、イメージに HEALTHCHECK が無いため、起動の直後の running を健全と判定せず、compose の健全性の確かめと同じ bash の `/dev/tcp` で `/actuator/health` の 200 を待つ。 (merged 2026-11-04)

<!-- 元: project.md の ## Deployment -->
- colima の VM のコンテナとファイルを受け渡す一時の場所は、`mktemp -d`（macOS の一時ディレクトリ。VM から見えない）ではなくホームの下（権限 700）に置く。 (merged 2026-11-04)

<!-- 元: project.md の ## Corrections -->
- 95 パーセンタイルの式を書く前に、その指標に +Inf 以外のバケット（le）があるかを確かめる。式が NaN を返すときは、要求が無いためと決めつけず、要求を送ってから式を流し直す（user-management の Observability Setup で、http.server.requests と mastersmith.mail.send にバケットが無く、ログインなどの p95 の警報3件が前から鳴らない状態だったと分かった。前の Intent は NaN を要求が無いためと誤って記録していた）。 (learned 2026-09-28) <!-- cid:260925-user-management:observability-setup:4962fa019abf98a8a9434901947eb969b172ef85029c5b9eb698a9a8746259fe -->
