# Requirements Analysis の質問（Intent 260928-quality-followup）

依頼の文（原文）: 「コントラストの Not Met（make-you-chic-ui の固定先を 7865c28・310e1ec に更新）、CI の2つの時間切れ、http.server.requests と mastersmith.mail.send の p95 のバケット（p95 の警報3件を働かせる）、Dependabot の開いた知らせの取り込みを直す。あわせて alarms.md・log-queries.md・runbooks.md の RB-17 の誤りを README と手順書で正す。」

所見の番号（K-1〜K-10）は、コード知識ベース `aidlc/spaces/default/codekb/mastersmith2/business-overview.md` の一覧を指します。

## 質問にせず、決まっていることとして扱う点

- **make-you-chic-ui の固定先の更新**: `735ef04` から `310e1ec` へ上げる（間の `7865c28` を含む一直線の履歴。K-1）。承認を得た専用のコミットで行い、前後のハッシュを記録する（`project.md` の Mandated）。統合は短命のブランチから `develop` への fast-forward でよい（`team.md` の Way of Working）。
- **E2E の既知の違反の一覧と README**: 更新でコントラストの違反が消えると、E2E（`frontend/e2e/support/axe.ts`）は一覧と合わずに失敗する（K-2）。一覧と README の「既知の制約（ブランドカラーのコントラスト）」の節を、同じ変更で今の実際に合わせる。画面に関わる変更なので、統合の前に `./gradlew e2eTest` を手元で流す（`team.md` の Testing Posture）。
- **前の Intent の承認済みの記録は書き換えない**: 誤りは新しい置き場で正し、差を明記する（`project.md` の Way of Working と Change Control）。
- **Dependabot の受け方**: GitHub の画面ではマージせず、手元で版と lockfile をまとめて更新し、`./gradlew verify` を通してから `develop` に統合する（`team.md` の Way of Working）。プルリクエストを閉じる操作と `git push` は依頼者が行う。
- **`team.md` の記述の古さ（K-10）**: 全体の合計で判定するパッケージの一覧を「22 個」と書いているが、今は 12 個。直すかどうかはこの段の学びの手順で確かめる（メモリの直接の書き換えはしない）。

---

## Q1. CI で時間切れになった2つのテストの直し方

CI では次の2つのテストが時間切れで失敗しました。どちらも手元の `verify` では通っており、原因はまだ確かめていません。

- **K-4** `H2CompactionByPoolSuspensionIT`：接続が 0 本になるのを 10 秒待つところで時間切れ。仮説は、接続の補充・定期の処理・CI の遅さの3つです。
- **K-5** `InvitationAdminPage.test.tsx` の1件：1つのテストで招待・送り直し・取り消しを続けて行い、Vitest の既定の 5 秒を超えました。

`team.md` には「不安定なテストは原因を直すまで統合しない」という決まりがあります。一方、前の Intent では「再現できなければ、不安定とは確かめられていない扱いで統合してよい」と確かめています。これを踏まえて、どう直しますか？

A. 両方とも、まず診断を足して原因を確かめ、確かめた原因を直す。K-4 では、失敗したときにプールの接続の数とスレッドの状態を出す。K-5 では、テストの時間を測る。再現できないときは、診断を残したまま、見立てに沿った最小の直しをして統合する。
B. K-5 はテストの作りを変えて短くする（流れを複数のテストに分ける、入力を `user.paste` にするなど）。K-4 は A と同じく、診断を足して原因を確かめてから直す。
C. 両方とも、待ちの上限を延ばすだけにする（K-4 は 10 秒を 30 秒に、K-5 はそのテストだけ上限を延ばす）。原因は確かめない。
D. B に加えて、CI で5回続けて流して通ることを確かめてから、直ったとみなす。
X. Other (please specify)

[Answer]: C

## Q2. p95 のバケットの範囲

p95 の警報3件（ログイン・トークンの更新・管理者の確認。しきい値はどれも 1000 ms）は、`http.server.requests` にヒストグラムのバケットが無いため、値を持たず鳴らない状態です（K-6）。`mastersmith.mail.send` も同じ状態です。バケットをどう出しますか？ 既定のバケットは数十個あり、URI・メソッド・状態の組み合わせの分だけ、監視の系列が増えます。

A. 決めた境界だけを出す（`slo`）。例：100・250・500・1000・2000・5000 ms。メールは送信の時間切れを含むように 10000 ms まで出す。系列は少なく済み、1000 ms の警報の境界が正確になる。ただし、p95 の値は境界の間を補った近似になる。
B. 既定のバケット（`percentiles-histogram`）を使い、最小と最大（例：5 ms〜10 秒）で範囲を絞る。値は細かくなるが、系列は A より多い。
C. 既定のバケットを絞らずにそのまま出す。
X. Other (please specify)

[Answer]: A

## Q3. Dependabot の知らせのうち、どれを取り込むか

手元には Dependabot のブランチが 15 本あります（K-7。前の Intent の記録では、開いているプルリクエストは 11 件）。その中に、既存の決まりとぶつかる更新があります。

- **ぶつかる更新**：`networknt-json-schema-validator` 3.0.7（Jackson を引き上げる）、`opentelemetry-logback-appender` 2.31.1-alpha（2.28.1-alpha に固定している）、`eclipse-temurin` 26（JDK 25 と版が分かれる）、`mysql` 26.7.0・`mariadb` 13.0.2（大きな版の飛びで、対象DB の読み取りとの互換は確かめていない）
- **大きな版の更新**：`typescript` 7、`vitest`・`@vitest/coverage-v8` 5
- **小さな更新**：`archunit` 1.5.1、`snakeyaml` 2.7、Gradle wrapper 9.8.0、`prettier` 3.9.9、`vite` 8.3.1、`grafana/otel-lgtm` 0.34.0、`postgres` 18.6 の digest の更新

A. 小さな更新だけを取り込む。ぶつかる更新と大きな版の更新は見送り、見送る理由をこの Intent の記録と README（または `dependabot.yml` の無視の設定）に書く。
B. A に加えて、大きな版の更新（TypeScript 7・Vitest 5）も試す。`verify` が通れば取り込み、通らなければ見送る。
C. すべてを試し、`verify` を通ったものだけ取り込む。ぶつかる決まりは、通った場合に限って変える。
D. A と同じ範囲だけを取り込む。見送るものは `dependabot.yml` の `ignore` に入れ、同じ知らせが来ないようにする。
X. Other (please specify)

[Answer]: C

## Q4. 運用の記録の誤りを正す置き場（「手順書」をどこに置くか）

前の Intent の運用の記録に、コードと合わない記述があります（K-8）。

- 登録の完了で拒否されたとき、実際は監査に `REGISTRATION_FAILED` が残る（log-queries.md と runbooks.md の RB-17）。
- 招待メールの送信の失敗は、実際は既存の警報に当たらない（alarms.md）。

これらの手順書は `aidlc/` の中の、前の Intent の承認済みの記録にしかありません。リポジトリには手順書の置き場がありません（K-9）。依頼の「README と手順書で正す」の手順書を、どこに置きますか？

A. README に「警報と対応の手順」の節を足し、正しい形で書く。この Intent の記録には、前の記録との差を明記する。
B. リポジトリに運用の文書の置き場（例：`docs/operations/` の alarms.md・log-queries.md・runbooks.md）を新しく作り、前の Intent の手順書を正しい形で移す。README からはそこへ案内する。
C. 手順書はこの Intent の運用の記録（Deployment Pipeline の段の成果物など）に正誤表として書く。README は、合っていない箇所だけを直す。
X. Other (please specify)

[Answer]: A

## Q5. make-you-chic-ui の直しが及ばない、アプリ自身の CSS の文字の色

make-you-chic-ui の更新で、部品の文字の色は直ります。ただし、アプリが独自に持つ CSS には及びません（K-3）。

- `.preferences-choice-error`：プリファレンスの画面の誤りの文字。dark のテーマで、直す前の FormField と同じコントラスト不足になりうる。サーバーが誤りを返したときだけ出るため、E2E で検査されていない可能性がある。
- `.dsl-link`：DSL の画面のリンク。塗りの色を文字の色に使っている。E2E の検査の対象外の画面にある。
- `.page-link`：どの画面からも使われていない。

これらを今回の範囲に入れますか？

A. 入れる。`.preferences-choice-error` と `.dsl-link` を make-you-chic-ui で足された文字用の色（`--color-danger-text`・`--color-primary-subtle-text` など）に切り替え、使われていない `.page-link` は消す。コントラストは単体の検査（vitest-axe）か E2E で確かめる。
B. A と同じく2つを直すが、`.page-link` は残す。
C. 入れない。所見として記録し、後の Intent に回す。
X. Other (please specify)

[Answer]: A

## F1.（Q1 の追加の質問）上限を延ばすだけにすることと team.md の決まりの関係

Q1 では「C. 上限を延ばすだけ（原因は確かめない）」を選んでいただきました。これは `team.md` の2つの決まりと食い違いうるため、確かめます。

- 「不安定なテストは放置せず、原因を直すまで統合しない」
- 「CI が失敗したら、次の Bolt に進む前に原因を直す」

どう扱いますか？

A. 時間切れの原因は「上限が CI の runner の速さに対して短すぎたこと」とみなし、上限を延ばすことを直しとして統合する。`team.md` の決まりとの差（原因を確かめていないこと）は、この Intent の記録に明記する。
B. A と同じく上限を延ばす。あわせて、失敗したときに状態（K-4 はプールの接続の数とスレッド、K-5 はかかった時間）をログに出す診断も足し、次に落ちたときの手がかりを残す。差は A と同じく記録する。
C. 上限を延ばしたうえで、CI で数回（例：3回）続けて通ることを確かめてから、直ったとみなす。
X. Other (please specify)

[Answer]: B

## F2.（Q3 の追加の質問）`verify` だけでは判定できない更新の確かめ方

Q3 では「C. すべて試し、`verify` を通ったものだけ取り込む」を選んでいただきました。ただし、次の3つは `verify` を通っても、決まりとぶつかる点を確かめたことになりません。

- **`eclipse-temurin` 26**：`verify` はコンテナのイメージを作らない。また、ビルドと CI は JDK 25（`gradle/libs.versions.toml` の `java = "25"`）のままなので、実行だけが 26 になる。
- **`opentelemetry-logback-appender` 2.31.1-alpha**：2.28.1-alpha に固定した理由は、外部へのエクスポートを有効にしたときの失敗（`project.md` の Tech Stack）。外部へのエクスポートは既定で無効なので、`verify` では現れない。
- **`mysql` 26.7.0・`mariadb` 13.0.2**：`TargetDbImages` の digest の定数と一緒に上げれば、対象DB の結合テストで読み取りを確かめられる。ただし、大きな版の飛びになる。

これらをどう判定しますか？

A. それぞれに、`verify` の外の確かめを足す。通ったら取り込む。
   - Temurin 26：イメージを作って起動し、ヘルスチェックとスモークテストを通す。
   - logback-appender：外部へのエクスポートを有効にして起動し、手元の監視にログが届くことを確かめる。
   - mysql・mariadb：`TargetDbImages` の digest を上げて、対象DB の結合テストを通す。
B. A と同じ。ただし Temurin 26 を取り込むときは、ビルドと CI の JDK（`java = "25"` と CI の JDK）も 26 にそろえる。
C. `verify` で判定できない Temurin 26 と logback-appender は見送る。mysql・mariadb は A と同じく結合テストで判定する。
X. Other (please specify)

[Answer]: C

## Consolidated Summary Confirmation

- Q1（CI の時間切れ）: C。待ちの上限を延ばす。`H2CompactionByPoolSuspensionIT` の接続が 0 本になるまでの待ちを 10 秒から 30 秒に、`InvitationAdminPage.test.tsx` の該当の1件だけ Vitest の上限を延ばす（値は既定の 5 秒の3倍の 15 秒を案とする）。
- F1（team.md の決まりとの関係）: B。上限を延ばすことを直しとして統合する。あわせて、失敗したときの状態（K-4 はプールの全体・使用中・空きの接続の数と待っているスレッドの数、K-5 はかかった時間）をログに出す診断を足す。「原因は確かめていない」という `team.md` の決まりとの差を、この Intent の記録に明記する。
- Q2（p95 のバケット）: A。`slo` で決めた境界だけを出す。`http.server.requests` は 100・250・500・1000・2000・5000 ms、`mastersmith.mail.send` は 100・250・500・1000・2000・5000・10000 ms。p95 の警報3件（ログイン・トークンの更新・管理者の確認）が値を持つことを確かめる。
- Q3（Dependabot）: C。すべて試し、`verify` を通ったものだけを取り込む。ぶつかる決まり（networknt の Jackson、`TargetDbImages` の digest など）は、通った場合に限って変える。
- F2（`verify` だけでは判定できない更新）: C。Temurin 26 と logback-appender 2.31.1-alpha は見送る。mysql 26.7.0・mariadb 13.0.2 は `TargetDbImages` の digest と一緒に上げ、対象DB の結合テストで判定する。見送るものは、理由をこの Intent の記録に書く。プルリクエストを閉じる操作は依頼者が行う。
- Q4（手順書の置き場）: A。README に「警報と対応の手順」の節を足して正しい形で書く（登録の完了の拒否は監査に `REGISTRATION_FAILED` が残る、招待メールの送信の失敗は既存の警報に当たらない）。前の Intent の記録は書き換えず、この Intent の記録に差を明記する。
- Q5（アプリ独自の CSS）: A。`.preferences-choice-error` と `.dsl-link` を make-you-chic-ui の文字用の色に切り替え、使われていない `.page-link` は消す。コントラストは vitest-axe か E2E で確かめる。
- 決まっていること（質問にしなかった点）: make-you-chic-ui を `735ef04` から `310e1ec` に上げる（専用のコミット、前後のハッシュを記録、fast-forward での統合でよい）。E2E の既知の違反の一覧と README の既知の制約の節を今の実際に合わせ、統合の前に `./gradlew e2eTest` を流す。`team.md` の古い記述（22 個→12 個）は、この段の学びの手順で扱う。

Does this all look correct before I generate the requirements artifact?

- Looks correct
- Request changes

[Answer]: Looks correct

