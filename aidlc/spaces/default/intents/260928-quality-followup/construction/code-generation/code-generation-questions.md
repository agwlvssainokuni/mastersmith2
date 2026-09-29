# Code Generation の質問（Intent 260928-quality-followup）

計画 `code-generation-plan.md` の6節「計画の承認で確かめること」（Q1〜Q9）を、計画の承認の前に確かめます。番号は計画の6節と同じです。

## Q1.（未決の点 O1）見送る更新を `dependabot.yml` の `ignore` に入れるか

Temurin 26 と logback-appender 2.31.1-alpha は見送ると決めています（F2: C）。見送る更新について、同じ知らせが来ないように `ignore` に入れますか？

A. 入れる。対象は eclipse-temurin の大きな版と logback-appender のすべて。外す時期をコメントに書く。「verify を通らずに見送った」更新は入れない。
B. 入れない。プルリクエストを閉じるだけにする（新しい版が出るたびに知らせが来る）。
X. Other (please specify)

[Answer]: A

## Q2.（未決の点 O2）メールの送信の p95 のパネルを `mastersmith.mail.send` のバケットに戻すか

A. 戻す。手元の監視で、名前と値を確かめられたときだけ戻す。README の「既知の欠け」も直す。
B. 戻さない。トレースから作る値のままにし、README には「アプリの指標にもバケットがある」ことだけを書く。
C. A に加えて、招待と登録の p95 のパネル（今は同じくトレースの値）も `http.server.requests` のバケットに替える。要件（FR4.4）の外なので、差として記録する。
X. Other (please specify)

[Answer]: A

## Q3. `.page-link` は使われていた（FR2.3 の前提と違う）

要件の FR2.3 は「使われていない `.page-link` を消す」としていました。ところが実際には、`frontend/src/app/pages/NotFoundPage.tsx` の 31 行（見つからない画面の「ホームへ」のリンク）が使っています。確定済みの要件は書き換えず、差は記録します。どう扱いますか？

A. 残し、文字用の色（`.dsl-link` と同じ）に直す。新しい E2E の 100 で、見つからない画面も確かめる。
B. `.page-link` とその `className` を消し、ブラウザの既定のリンクの色にする。
C. 要件の文言どおり、CSS だけを消す（`className` が指す先の無い状態になる）。
X. Other (please specify)

[Answer]: A

## Q7. 専用のコミット（C1）と、「不具合を直すときは再現するテストを同じコミットに含める」決まりの関係

`project.md` の Mandated には、次の2つの決まりがあります。この Intent では両方を同時には満たせません。

- サブモジュールの固定先の更新は、専用のコミットで行う
- 不具合を直すときは、再現するテストを同じコミットに含める

A. C1 は固定先の更新だけにし、確かめる E2E の一覧は直後の C2 に置く。C1〜C10 をまとめて fast-forward で統合する。差は `code-summary.md` に書く。
B. C1 に E2E の一覧と README も含める（専用のコミットではなくなる）。
X. Other (please specify)

[Answer]: A

## Q4〜Q6・Q8・Q9. 推奨どおりでよいか

- **Q4**：FR2 のコントラストは、新しい E2E の 100（流れではない検査）で、20 組 × 3 つの状態を確かめる。単体テスト（jsdom）では色を計算しないため、vitest-axe では判定できない。
- **Q5**：接続が 0 本になるまでの待ち（定数）を、4か所すべてで 30 秒にする。診断は、ログに加えて失敗の知らせ（例外の文言）にも入れる。CI では標準出力が見えないため。
- **Q6**：ブランチ `fix/260928-quality-followup` から `develop` へ fast-forward で統合する（C1〜C10）。
- **Q8**：手元の監視の確かめ（Step 7）は、使い捨ての環境で行う。VM のメモリが足りないため、そのあいだ配備したアプリを止める（`docker compose stop app`、終わったら起動し直す）。止める前にお知らせする。
- **Q9**：Dependabot は、着手のときに `git fetch origin --prune` を行う。小さな更新はまとめて1回の `verify` で試し、落ちたら分ける。vitest と coverage-v8 は1組で判定する。

A. すべて推奨どおりでよい
B. 変えたいものがある（どれをどう変えるかを書く）
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- Q1（O1）: A。見送る更新（eclipse-temurin の大きな版・logback-appender のすべて）を `dependabot.yml` の `ignore` に入れ、外す時期をコメントに書く。verify を通らずに見送った更新は入れない。
- Q2（O2）: A。手元の監視で名前と値を確かめられたときだけ、メールの送信の p95 のパネルを `mastersmith.mail.send` のバケットに戻し、README の「既知の欠け」も直す。
- Q3: A。`.page-link` は残し、文字用の色（`.dsl-link` と同じ）に直す。見つからない画面も E2E の 100 で確かめる。要件の FR2.3 との差は `code-summary.md` に記録する。
- Q7: A。C1 は固定先の更新だけにし、確かめる E2E の一覧は直後の C2 に置く。C1〜C10 をまとめて fast-forward で統合し、決まりとの差を `code-summary.md` に書く。
- Q4〜Q6・Q8・Q9: A。すべて計画の推奨どおり（E2E の 100 で確かめる、0 本の待ちは4か所すべて 30 秒・診断はログと失敗の文言の両方、ブランチ `fix/260928-quality-followup` から fast-forward、手元の監視の確かめは使い捨ての環境で配備したアプリを止める前に知らせる、着手時に `git fetch origin --prune`）。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct


## Plan Approval

計画 `code-generation-plan.md`（Step 1〜28、コミット C1〜C10、埋め込みの Testing Contract を含む）と `unit-test-instructions.md` を、このとおりに実行してよいかを確かめます。

[Approval Fingerprint]: sha256:v3:b7544c45d75db63e2322a1c8d4ba2d87faeca9fe57d8caeab447f79e80d325cf
[Planned Source]: 801a26bd7b0c73a2b3a2d55176095d0235abb3f574823bce420ea3cd8b9e6e33

- Approve Plan
- Request Changes

[Answer]: Approve Plan

## G1.（生成の途中で見つかった点）処理中の数の指標にもバケットが付いたこと（NFR3）

p95 のバケットの設定（`management.metrics.distribution.slo`）は、指標の名前の前方一致で当たります。そのため、処理中の要求の数を表す `http_server_requests_active_milliseconds_bucket`・`mastersmith_mail_send_active_milliseconds_bucket` にも同じ境界のバケットが付きました。

- 増える系列: 前者が 14 本、後者が 8 本
- 既定のバケット（数十個）は出ていません。
- 処理中の数の指標だけを外すには、Java の `MeterFilter`（`common.observability` など）が要ります。そのパッケージは全体の合計で判定する一覧にあるため、テストを足して一覧から外す作業が付きます。

A. このまま受け入れる。README に書いたとおり、系列が増えることを記録する。
B. Java の `MeterFilter` を足して、処理中の数の指標のバケットを外す。計画に無い変更のため、差として記録する。一覧のパッケージに触れるなら、テストを足して一覧から外す作業も行う。
X. Other (please specify)

[Answer]: A

## G2.（生成の途中で見つかった点）make-you-chic-ui に残るコントラストの不足（Tabs・ボタンの hover）

固定先を `310e1ec` に上げると、これまでの既知の違反（primary のボタン・2語の氏名のアバター・dark の誤りの文字）はすべて解消しました。ただし、`310e1ec` の直しの範囲の外で、次の2件が見つかりました。

- **所見1 Tabs**：DSL の管理の画面の選ばれたタブ（`.mycui-tab.active`。文字の色は `--color-primary`）のコントラストが足りません。green・orange の light（計算で 3.16・3.41）と、blue・purple の dark（3.71・3.56）の組です。実測で違反を確かめました。今は E2E の 100 の中で、その組とその状態に限った既知の違反として扱い、README の「残る既知の制約」に書いています。
- **所見2 ボタンの hover**：green・orange の primary のボタンは、マウスを重ねたときのコントラストが計算で 3.54・3.43 です。E2E はどれも hover の状態を検査していません。

`vendor/` の中身はこのリポジトリから変えられません（`project.md` の Forbidden）。どう扱いますか？

A. 2件とも既知の制約として受け入れる。今回は E2E の既知の違反（所見1）と README の記述にとどめ、make-you-chic-ui のリポジトリ側への直しの依頼を、次の Intent の候補として記録する。
B. A に加えて、make-you-chic-ui のリポジトリ側で直してもらい、その固定先を今回の Intent の中で取り込む（取り込むまで Code Generation を止める）。
C. A に加えて、所見2（hover）も E2E で検査し、既知の違反として一覧に載せる。
X. Other (please specify)

[Answer]: C

## G3.（生成の途中で見つかった点）README を計画の列挙より広く直したこと

計画の Step 13 は、README の画面の記述の直しとして、特定の節だけを挙げていました。実際には、U5〜U8 の各節の「既知の制約」も直しています。直さないと、解消した制約が README に残ったままになるためです。

A. 受け入れる。計画との差として `code-summary.md` に記録する。
B. 計画の列挙の範囲に戻す（U5〜U8 の節の直しを取り消す）。
X. Other (please specify)

[Answer]: A

## G4.（生成の途中で見つかった点）既存の Jackson の脆弱性（High）で統合前の検査が止まる

`jackson-databind` に High の脆弱性（GHSA-q4xh-88c3-wmh7、CVSS 7.5、2026-09-28 公開）が出ており、OSV-Scanner で `./gradlew verify` が止まります。変更の前の `develop` でも同じです。直った版は 3.1.6 と 3.2.2 です。`team.md` の「High 以上の知らせは次の Bolt に入る前に取り込む」に当たります。

networknt 3.0.7 を取り込むと Jackson が 3.2 系に上がるため、3.1 系の直った版には戻せません。両方をあわせて決める必要があります。

A. networknt 3.0.6 に戻し（3.0.7 は見送り）、Jackson を 3.1.6 に上げる。Spring Boot の管理の 3.1 系のままパッチだけを上げ、`project.md` の「3.0.6 を選んだ理由」の決まりも守られる。
B. networknt 3.0.7 を取り込み、Jackson を 3.2.2 に上げる。Spring Boot の管理の版から外れる（3.2 系）。
C. Spring Boot の修正版を待ち、今回は統合しない（Code Generation を止める）。
X. Other (please specify)

[Answer]: A

## G5.（生成の途中で見つかった点）`DisplaySettingsProvider.test.tsx` の不安定の疑い

1回の `verify` で「applies saved preferences at once…」の1件が落ちました。そのファイルだけを8回流すとすべて通り、`verify` を流し直しても通りました。原因は確かめていません。見立ては、`act` の直後に言語の切り替えを同期で確かめているため、並行の負荷で反映が間に合わなかったというものです。

`team.md` には「不安定なテストは原因を直すまで統合しない」、`project.md` には「再現できなければ、不安定とは確かめられていない扱いとして統合してよい」とあります。

A. 見立てに沿って直す（反映を `waitFor` などで待つ形にする）。計画に無い変更として記録し、並行の負荷をかけて繰り返し流して確かめる。
B. 負荷をかけて再現を試みる。再現すれば原因を確かめて直す。再現しなければ、不安定とは確かめられていない扱いとして記録し、コードは変えない。
C. 今回の範囲に入れず、所見として記録して次の Intent に回す。
X. Other (please specify)

[Answer]: B
