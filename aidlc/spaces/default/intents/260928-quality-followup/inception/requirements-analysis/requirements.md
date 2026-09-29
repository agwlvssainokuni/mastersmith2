# 要件定義（Intent 260928-quality-followup）

scope bugfix・深さ Minimal・Test Strategy Minimal。答えは `requirements-analysis-questions.md`（Q1〜Q5・F1・F2、まとめの確認は Looks correct）。コードの事実と所見 K-1〜K-10 は、コード知識ベースの `aidlc/spaces/default/codekb/mastersmith2/business-overview.md`（所見の一覧）・`architecture.md`（K-4・K-6 の流れ）・`code-structure.md`（置き場）による。

## Sources

- Initial description: 「コントラストの Not Met（make-you-chic-ui の固定先を 7865c28・310e1ec に更新）、CI の2つの時間切れ、http.server.requests と mastersmith.mail.send の p95 のバケット（p95 の警報3件を働かせる）、Dependabot の開いた知らせの取り込みを直す。あわせて alarms.md・log-queries.md・runbooks.md の RB-17 の誤りを README と手順書で正す。」 [desc]
- Workflow-selected scope: bugfix [scope]
- 質問の答え: Q1〜Q5・F1・F2 [Q1]〜[Q5]・[F1]・[F2]
- コード知識ベース: `aidlc/spaces/default/codekb/mastersmith2/`（所見 K-1〜K-10）
- 前の Intent の振り返り: `aidlc/spaces/default/intents/260925-user-management/operation/feedback-optimization/feedback-loop.md` の2節（次の Intent の第1候補・第2候補）

## 1. 目的（Intent analysis）

前の Intent（`260925-user-management`）で受け入れたまま残した失敗・未達と、承認済みの運用の記録の誤りを片付け、次の3つを「働いている」状態に戻す。

1. **画面のアクセシビリティ**: ブランドカラーとテーマのすべての組で、文字のコントラストが WCAG 2.1 AA を満たす（NFR7.1 の Not Met を解消する）。
2. **統合前の関門と CI**: CI の `./gradlew verify` が時間切れで落ちない。
3. **監視と運用**: p95 の警報3件が値を持って働き、運用の手順の記述がコードと合う。依存の更新の知らせが溜まったままにならない。

新しい機能は足さない。利用者から見た振る舞いの変化は、make-you-chic-ui の更新による文字の色の変化（K-1）と、アプリ独自の CSS の文字の色（K-3）だけである。

## 2. 機能要件

### FR1 make-you-chic-ui の固定先の更新（K-1・K-2）[desc]

- **FR1.1** サブモジュール `vendor/make-you-chic-ui` の固定先を `735ef04` から `310e1ec`（間に `7865c28` を含む）へ上げる。更新は承認を得た専用のコミットで行い、コミットのメッセージか記録に前後のハッシュを残す（`project.md` の Mandated）。
- **FR1.2** E2E の既知の違反の一覧（`frontend/e2e/support/axe.ts` の `KNOWN_VIOLATIONS` ほか）を、更新の後の実際の検査の結果に合わせる。更新で解消した違反は一覧から除く。
- **FR1.3** README の「既知の制約（ブランドカラーのコントラスト）」の節と、E2E の一覧を説明する記述を、更新の後の実際に合わせる。
- 受け入れの基準:
  - Given 固定先を `310e1ec` に上げたとき、When `./gradlew e2eTest` を流すと、Then 050〜090 のすべてが通る。
  - Given 更新の前に違反としていた組（green・orange の primary の Button、Avatar の頭文字2文字、dark の FormField の誤りの文字、Alert の danger、Badge の success）について、When 実際のブラウザの axe で検査すると、Then コントラストの違反が出ない。出る組が残るときは、一覧に残し、理由を記録する。

### FR2 アプリ独自の CSS の文字の色（K-3）[Q5]

- **FR2.1** `frontend/src/features/preferences/PreferencesForm.css` の `.preferences-choice-error` を、make-you-chic-ui で足された誤りの文字用の色（`--color-danger-text`）に切り替える。
- **FR2.2** `frontend/src/features/dsl/DslSubmitForm.css` の `.dsl-link` を、塗りの色（`--color-primary`）ではなく文字用の色に切り替える。どの色を使うかは、コントラストを満たすものを Code Generation で選ぶ。
- **FR2.3** どの画面からも使われていない `frontend/src/app/pages/Page.css` の `.page-link` を消す。
- 受け入れの基準:
  - Given ブランドカラーとテーマのすべての組で、When 誤りを出した状態のプリファレンスの画面と、DSL の画面を検査すると（vitest-axe か E2E）、Then コントラストの違反が出ない。
  - Given `.page-link` を消したとき、When `frontend/src` を検索すると、Then 使っている箇所が無く、画面のテストと Stylelint が通る。

### FR3 CI の時間切れの2件（K-4・K-5）[Q1]・[F1]

- **FR3.1** `H2CompactionByPoolSuspensionIT` の、接続が 0 本になるまでの待ち（今は `ZERO_CONNECTIONS_WAIT` の 10 秒）を 30 秒に延ばす。
- **FR3.2** `InvitationAdminPage.test.tsx` の「keeps addresses and names out of storage, the URL and the console in every flow」の1件だけ、Vitest の上限を 15 秒に延ばす。ほかのテストと、設定全体の既定（5 秒）は変えない。
- **FR3.3** 上の2つのテストに、失敗したときの状態をログに出す診断を足す。
  - K-4：待ちが時間切れになったとき、プールの接続の数（全体・使用中・空き）と、接続を待っているスレッドの数を出す。
  - K-5：テストがかかった時間を出す。
- **FR3.4** 原因（接続の補充、定期の処理、CI の速さのどれか）は確かめずに、上限を延ばすことを直しとして統合する。この判断が `team.md` の決まりとどう違うかを、この Intent の記録（Code Generation と Build and Test の成果物）に明記する。対象の決まりは「不安定なテストは原因を直すまで統合しない」「CI が失敗したら次に進む前に原因を直す」の2つ。
- 受け入れの基準:
  - Given 上限を延ばしたとき、When `./gradlew verify` を手元と CI で流すと、Then 2つのテストが通る。
  - Given 待ちがわざと時間切れになるようにしたとき（例：上限を一時的に極端に短くする）、When テストを流すと、Then 決めた項目がログに出る。この確かめは Build and Test で1回だけ行い、コードには残さない。

### FR4 p95 のヒストグラムのバケット（K-6）[Q2]

- **FR4.1** `http.server.requests` に、決めた境界だけのバケット（`slo`）を出す。境界は 100・250・500・1000・2000・5000 ms。
- **FR4.2** `mastersmith.mail.send` に、決めた境界だけのバケットを出す。境界は 100・250・500・1000・2000・5000・10000 ms。
- **FR4.3** 警報 `ms-login-p95`・`ms-refresh-p95`・`ms-check-p95` とダッシュボードの p95 のパネルが、値を持つことを確かめる。Prometheus での指標の名前が今の式（`http_server_requests_milliseconds_bucket`）と違うときは、式の側を実際の名前に直す。
- **FR4.4** メールの送信の p95 のパネルの式を、トレースから作る値（`traces_spanmetrics_latency_bucket`）から `mastersmith.mail.send` のバケットに戻すかは、Code Generation で実際の名前を確かめてから決める。戻すときは、README の「既知の欠け」の記述も直す。
- 受け入れの基準:
  - Given 外部へのエクスポートを有効にして手元の監視を起動し、When 対象の API へ要求を、送信の周期（1 分）を複数またいでくり返し送ると、Then p95 の式が NaN ではない値を返す（`project.md` の Corrections の確かめ方）。
  - Given ログインの応答が 1000 ms を超える状態が 5 分続いたとき、When 警報を評価すると、Then `ms-login-p95` が鳴る。ほかの2件も同じ。この確かめは、負荷をかけた使い捨ての環境で行うか、式に当てる値の確かめで代える（Build and Test で決める）。
  - Given バケットを足したとき、When `/actuator/metrics` の出し方と、公開する窓口（health だけ）を確かめると、Then 公開の範囲は変わっていない。

### FR5 Dependabot の知らせの取り込み（K-7）[Q3]・[F2]

- **FR5.1** 手元の Dependabot のブランチ（15 本）の更新を、`team.md` の受け方（手元で版と lockfile をまとめて更新し、`./gradlew verify` を通してから `develop` に統合する）で試す。`verify` を通ったものだけを取り込む。
- **FR5.2** 既存の決まりとぶつかる更新は、`verify` を通った場合に限り、決まりを変えて取り込む。変えた決まりと理由は、この Intent の記録に書き、学びの手順で `project.md` に反映するかを依頼者に確かめる。
  - `networknt-json-schema-validator` 3.0.7：Jackson を引き上げる件
  - `TargetDbImages` の digest
- **FR5.3** `mysql` 26.7.0 と `mariadb` 13.0.2 は、`compose.yaml` と `TargetDbImages` の digest の定数を一緒に上げ、対象DB の結合テストが通ったときだけ取り込む。
- **FR5.4** `eclipse-temurin` 26 と `opentelemetry-logback-appender` 2.31.1-alpha は見送る。見送る理由をこの Intent の記録に書く。
  - Temurin 26：ビルドと CI の JDK 25 と版が分かれ、`verify` ではイメージを確かめられない。
  - logback-appender：2.28.1-alpha に固定した理由（外部へのエクスポートを有効にしたときの失敗）を、`verify` では確かめられない。
- **FR5.5** 取り込んだもの・見送ったもの・通らずに見送ったものを、ブランチ（更新）ごとに1行で一覧にして記録する。プルリクエストを GitHub で閉じる操作は依頼者が行う。
- 受け入れの基準:
  - Given 取り込んだ更新のすべてを入れた状態で、When `./gradlew verify`（対象DB の結合テストを含む）と `./gradlew e2eTest` を流すと、Then すべて通る。
  - Given 一覧を見ると、Then 15 本のそれぞれに、取り込み・見送り・通らずに見送りのどれかと理由が書かれている。

### FR6 運用の記録の誤りを README で正す（K-8・K-9）[Q4]

- **FR6.1** README に「警報と対応の手順」の節を足す。前の Intent の運用の記録（`alarms.md`・`log-queries.md`・`runbooks.md`）のうち、次の点を正しい形で書く。
  - 登録の完了（`POST /api/registration/complete`）で拒否されたときは、形の誤ったトークンでも監査に `REGISTRATION_FAILED` が残る。
  - 招待メールの送信の失敗（招待は 201、送り直しは 200 で `sendResult: FAILED`）は、既存の警報（5xx の割合、ERROR のログ）に当たらない。気づく方法（画面の表示・WARN のログ・ダッシュボード）を書く。
- **FR6.2** 前の Intent の承認済みの記録は書き換えない。この Intent の記録に、前の記録のどの記述をどう正したかの差を明記する（`project.md` の Way of Working と Change Control）。
- **FR6.3** README に書く前に、書く内容（状態コード・ログのレベル・監査の有無・警報に当たるか）を、ソースか実際の要求で確かめる（`project.md` の Corrections）。
- 受け入れの基準:
  - Given README の新しい節を読むと、Then 上の2点がコードと合う形で書かれており、確かめた方法が記録にある。
  - Given リンクの確かめ（`POST /api/registration/verify`）の拒否についても触れるときは、Then 監査に残るかをソースで確かめた結果が書かれている（まだ確かめていない。コード知識ベースの K-8）。

## 3. 非機能要件

- **NFR1 アクセシビリティ** 文字のコントラストは WCAG 2.1 AA（通常の文字 4.5:1、大きな文字 3:1）を満たす。対象はブランドカラーとテーマのすべての組。実際のブラウザの axe で、誤りを出した状態と、現実に近いデータ（2語の氏名など）を使って確かめる（`project.md` の Testing Posture）。（FR1・FR2）
- **NFR2 関門の安定** CI の `./gradlew verify` が、時間切れ以外の理由でも落ちずに通る。今回の変更の後の CI の実行1回以上で確かめる。直さない原因（確かめていない原因）は記録に残す。（FR3）
- **NFR3 観測の系列の数** 足すバケットは、決めた境界（`http.server.requests` は6個、`mastersmith.mail.send` は7個）と `+Inf` だけとする。既定のバケット（数十個）は出さない。（FR4）
- **NFR4 品質の関門を緩めない** カバレッジの下限（行 80%・分岐 70%、全体の合計とパッケージごと）、SpotBugs・OSV-Scanner・Gitleaks の関門を緩めない。依存の更新で下限を下回ったり、新しい脆弱性（High 以上）が出たりしたら、その更新は見送る。
  - 変更が `packagesJudgedByTotal` の一覧のパッケージに及ぶときは、`team.md` のとおりテストを足してパッケージの下限を満たし、一覧から外す。（FR4・FR5）
- **NFR5 秘密情報と公開の範囲** 診断のログ（FR3.3）に、秘密情報と個人に関する値を出さない。出すのは接続の数・スレッドの数・時間だけ。指標の公開の範囲（Web に公開するのは health だけ）を変えない。（FR3・FR4）
- **NFR6 テスト** Test Strategy は Minimal。要件ごとに確かめるテストか確かめの手順を1つ以上置く。既存のテストはすべて通ったままにする。E2E は `verify` と CI の外なので、統合の前に `./gradlew e2eTest` を手元で流す（`team.md` の Testing Posture）。

## 4. 制約

- `vendor/make-you-chic-ui` と `vendor/java-mustache-processor` の中身は、このリポジトリから変えない（`project.md` の Forbidden）。
- サブモジュールの固定先の更新を含む変更は、短命のブランチから `develop` へ fast-forward で統合してよい。それ以外は squash で統合する（`team.md` の Way of Working）。
- 統合の前に `./gradlew verify` を通す。コンテナの実行環境（colima）を動かし、対象DB のテストも通してから統合する。README の `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡す（`project.md` の Testing Posture）。
- `git push` と、GitHub のプルリクエストを閉じる操作は依頼者が行う（`team.md` の Way of Working）。
- 配備先は開発者の PC 上のコンテナのまま。外部の SMTP には送らない（`team.md` の Deployment、`project.md` の Forbidden）。

## 5. 前提（Assumptions）

- **A1** CI の2件の時間切れは、上限が CI の runner の速さに対して短すぎたことによる、とみなす。原因は確かめない（F1: B）。確かめられるのは、上限を延ばした後に通ることと、次に落ちたときの診断だけである。持ち主：Build and Test。[F1]
- **A2** `InvitationAdminPage.test.tsx` の該当の1件の上限は 15 秒（既定の3倍）とする（まとめの確認で了承）。[Q1]
- **A3** p95 の値は、決めた境界の間を補った近似になる（Q2: A の性質）。警報のしきい値の 1000 ms は境界に含まれるため、しきい値の判定は正確である。[Q2]
- **A4** OTLP の登録先が、決めた境界のバケットを Prometheus に送り、名前が今の式と合うかは確かめていない（コード知識ベースの K-6 の見立て）。持ち主：Code Generation（起動して確かめる）。[assumption]
- **A5** make-you-chic-ui の更新で `frontend/package-lock.json` は書き換わらない見込み（`file:` の依存で `link: true` のため。実行して確かめていない）。持ち主：Code Generation。[assumption]

## 6. 範囲の外（Out of scope）

- CI の2件の時間切れの原因の特定（F1: B で確かめないと決めた）。
- Temurin 26 と logback-appender 2.31.1-alpha の取り込み（F2: C）。
- 前の Intent の承認済みの記録（`alarms.md`・`log-queries.md`・`runbooks.md`）の書き換え。
- リポジトリに運用の文書の置き場（`docs/operations/` など）を新しく作ること（Q4: A）。
- 前の Intent の第2候補のうち、bcrypt の余裕の見直しと、監視のデータの片付け。
- Dependabot alerts を有効にすること（前の Intent の決定で無効のまま）。

## 7. 未決の点（Open questions）

- **O1** 見送る更新を `dependabot.yml` の `ignore` に入れて、同じ知らせを止めるか。Q3 の答え（C）は、この点を決めていない。持ち主：Code Generation の計画の承認。
- **O2** メールの送信の p95 のパネルを、`mastersmith.mail.send` のバケットに戻すか（FR4.4）。持ち主：Code Generation。
- **O3** 警報が鳴ることの確かめを、負荷をかけた使い捨ての環境で行うか、式に当てる値の確かめで代えるか（FR4 の受け入れの基準）。持ち主：Build and Test。
- **O4** `team.md` の Testing Posture の「`packagesJudgedByTotal` は 22 パッケージ」の記述を、今の 12 個に直すか（K-10）。持ち主：この段の学びの手順。

## Assumptions & Open Questions

前提は5節（A1〜A5）、未決の点は7節（O1〜O4）のとおり。
