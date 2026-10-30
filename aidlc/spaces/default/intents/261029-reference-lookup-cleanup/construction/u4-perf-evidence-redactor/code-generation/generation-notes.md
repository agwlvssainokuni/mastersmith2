# 生成の記録 — U4 u4-perf-evidence-redactor（Bolt B5）

生成の担当（開発担当）の記録。計画 `code-generation-plan.md` の Step 1〜21 を行い、Step 22（コミット・統合）は行っていない。ブランチは `feature/261029-reference-lookup-cleanup-b5`（`develop` の 9f28abd から）。

## 1. 手順ごとの記録

| Step | 結果 |
|---|---|
| 1 | ブランチは用意済み。`aidlc/` の外に未コミットの変更なし。git の対象外の E2E の生成物 `frontend/test-results`（1 件）を消した（`frontend/playwright-report` は無かった） |
| 2 | `perf/redact-k6-evidence.mjs` の骨組み（ヘッダー・説明・定数・公開の名前・起動の部分）。`import` は `node:` だけ |
| 3 | `unit-test-instructions.md` 3.1節のコマンドで 3 件 passed・skipped と todo 0。Vitest は `frontend/` の外の `.mjs` を `import()` で読めた（計画の前提どおり。5.1節 P3 の切り替え先は使わない） |
| 4〜6 | 層 1（形の定義・`redactJwt`・`findResidual`・`redactTree`・`findUnredactedSetup`）。木は明示の積み上げでたどり、道（キー名）は親へのつながりで持ち、衝突のときだけ文字列にする |
| 7 | 層 1 のテスト 34 件（例 22・性質 9・実行の環境と公開の名前と定数 3。下の 3節） |
| 8 | 正規表現の形の時間を測り、1 回のなめに替えた（下の 2節） |
| 9・10 | 層 2（`main`・`checkAndWrite`・報告の部品・JSON の口・テキストの口）とテスト 45 件 |
| 11・12 | 層 3（`--scan`）とテスト 36 件 |
| 13 | 64 MiB の所要時間（下の 2節）と見せるもの（下の 4節） |
| 14 | `node perf/redact-k6-evidence.mjs --scan aidlc`: 調べたファイル 97 件（`evidence/` は 2 つ: `261027-perf-targets-review` と `261025-deps-perf-carryover` の build-and-test）、バイナリとして読んだもの 0 件、リンク 0 件、検出 0 件、入力の誤り 0 件。終わりのコード 0。止める条件に当たらない |
| 15 | `build.gradle.kts` に `perfEvidenceScan`・`perfToolFormatCheck`・`perfToolLint` を足し、`verifyFormat`・`verifyLint`・`verifySecurity` の一覧と、その 3 つと `verifyLicense` の説明を直した。`./gradlew perfEvidenceScan perfToolFormatCheck perfToolLint spotlessCheck` が通った |
| 16 | 変える前に、同じ判定（`checkContent`）を `perf/` の `.mjs`・`.js` の 9 ファイル（既存 8・道具 1）に当てて全部通った。`check-license-header.mjs` を直し、`./gradlew frontendLicenseCheck` が通った。作業フォルダの外の写しで、道具の先頭を `/**` にしたものとヘッダーの無い `.js` を置くと、2 件とも示して終わりのコード 1 になった |
| 17 | `perf/k6/scenarios.js` を読み取りで確かめた（下の 5節） |
| 18 | `perf/README.md` の末尾に H2「記録（evidence/）に置く前の伏せ字」と H3 6 つを足した。`evidence/` を含む行は見出し 1・道具の呼び方 2 の 3 行だけ。地の文は「記録の置き場（名前が `evidence` のディレクトリ）」と書き、`evidence/` を書かない。Intent の名前（`26` で始まる 6 桁）と日付は 0 件 |
| 19 | 完成した JWT の形の検索: 道具・README・3 つのテスト・`build.gradle.kts`・`check-license-header.mjs` の 7 ファイルとも 0 件。`gitleaks dir` も 7 ファイルとも 0 件 |
| 20 | 下の 3節・6節 |
| 21 | `code-summary.md`・`source-manifest.json`・`traceability.json` を書いた |

## 2. 処理の時間と 1 回のなめへの切り替え（Step 8・Step 13。計画 4節・`security-design.md` 2.1節）

- 正規表現の形（`matchAll`）で `-eyJ` が続き `.` の無い文字列に `redactJwt` と `findResidual` を当てた時間（リポジトリの外の測りの台本、1 回ずつ）: 8 KiB 20.4 ms、16 KiB 80.8 ms、32 KiB 324.1 ms、64 KiB 1,322.5 ms。2 倍ごとに約 4 倍（2 乗）で、1 MiB は約 340 秒の見込み。Vitest の既定の上限 5 秒を大きく超えるため、1 回のなめに替えた（時間の上限は延ばしていない）。
- 1 MiB の例のテストを正規表現の形のまま Vitest で流すことはしなかった。同期の正規表現は Vitest の時間の上限で止められず、数分固まるため。上の測りの値で判断した（計画の手順との差）。
- 替えた後: 区間（base64url の文字の続き）ごとに左から 1 度だけ読み、`eyJ` を探す位置は前へ戻らない。初めは 1 文字ずつの JS のループで、1 MiB 7.2 ms・16 MiB 92.7 ms・64 MiB 373.5 ms だった。
- ところが、道具だけのカバレッジの計測（`unit-test-instructions.md` 4節）の下では、64 MiB ちょうどのテキストの口の例が既定の 5 秒を超えて落ちた（計測なしでは通っていた）。V8 のカバレッジは計測の対象の外でも全体の実行を遅くするため、verify の `frontendCoverage` でも落ちうる。時間の上限は延ばさず、区間の切れ目を 1 文字の集合の繰り返しだけの粘着の正規表現（`/[A-Za-z0-9_-]*/y`・`/[^A-Za-z0-9_-]*/y`。戻りが無く線形）で読む形に替え、`findResidual` の行の数え方も改行を `indexOf` で飛ぶ形にした。替えた後は 1 MiB 1.4 ms・16 MiB 21.6 ms・64 MiB 84.4 ms で、計測の下でも 3 ファイル 115 件が約 1 秒で通った。
- 定義を保つことの確かめ: テストの中に同じ正規表現を正解として置いた性質のテスト（1,000 回）に加え、リポジトリの外の一度きりの突き合わせ（小さな字の集合のランダムな文字列 30 万件で、置き換えの結果と見つけた位置を正規表現と比べた）で食い違い 0 件。
- 64 MiB に近い入力での `--scan` の所要時間（`/usr/bin/time -p`、リポジトリの外の一時の `evidence/`）:
  - `-eyJ` の続く 67,108,864 バイト（上限ちょうど）: real 0.09〜0.10 秒（3 回）。
  - 区間の多い悪い側の形（2 区間で止まる候補 `eyJa.b ` の続き、67,108,860 バイト）: real 0.75 秒。
  - JWT の形が 7,456,540 件ある形（`--scan` が 10 件の位置と残りの件数を表示）: real 1.36 秒。
  - どれも verify の時間を目に見えて伸ばさない。作ったファイルは消した。

## 3. テストの件数（Step 7・10・12・20）

`unit-test-instructions.md` 3.5節のコマンド（3 ファイル）: 3 ファイル passed、115 件 passed、skipped・todo 0、約 1 秒。

| テストのファイル | passed |
|---|---|
| `frontend/src/perfEvidenceRedactor.test.ts` | 34 |
| `frontend/src/perfEvidenceRedactorCli.test.ts` | 45 |
| `frontend/src/perfEvidenceScan.test.ts` | 36 |

- 性質ベースのテストは 9 件（JSON の (1)〜(5)、テキストの (t1)〜(t3)、定義の突き合わせ）。fast-check の既定の 100 回（突き合わせだけ 1,000 回）。層 1 のファイルを違う種で 8 回流し、8 回とも通った。
- 道具だけのカバレッジ（4節のコマンド）: 行 94.2%（423/449）・分岐 90.61%（193/213）・文 94.09%・関数 89.74%。下限（行 80%・分岐 70%）を満たす。測っていない主な行は、本物の `SIGINT`・`SIGTERM` の受け口と、直接起動されたときの起動の部分（子のプロセスの分は計測に入らない）。
- 書式・リンタ・型: 3 つのテストのファイルに Prettier・oxlint・ESLint・`tsc --noEmit` が通った。道具に `prettier --check --config frontend/.prettierrc.json` と `oxlint -c frontend/.oxlintrc.json` が通った。

## 4. 見せるもの（Step 13。値は表示していない）

- 架空の summary json（JWT の形を実行の時に組み立てて、`metrics` のキー・`setup_data` の下・`Bearer` の後ろに入れたもの）を JSON の口に通した: 置き換えた JWT の形 2 件（`setup_data` の外の 2 件）、`[REDACTED-SETUP]` にした値 3 件、終わりのコード 0。置いたものに `--scan` を当てて検出 0 件・終わりのコード 0。
- 残存の検査が失敗する例: 書き出しの段の関数 `checkAndWrite` に置換を通していない JWT の形を渡すと終わりのコード 4、出力も一時ファイルも作られなかった。

## 5. 台本の読み取りの確かめ（Step 17。NFR8.70・AC4.1.9。値は書かない）

- `console.log` は 5 か所（787・816・934・956・1006 行）。出すのは件数・ページ・VU の番号・試験用の宛先（`example.com`。1006 行）だけ。`console.error`・`console.warn` は無い（計画の「今 6 か所」は 5 か所だった。836 行はコメント）。
- `fail()` の文言は状態コード・code・件数・試験用の宛先・ロールの名前だけで、トークン・Cookie・パスワードを含まない。
- タグの値（`tags: { name: … }`）は固定の名前か、操作の名前を足したもの（1822・1851・1929 行の `…_${op}`）だけ。check の名前・group の名前も固定の文字列。
- `setup()`（726〜771 行）はトークン（管理者・利用者・登録の完了のトークンなど）を入れたオブジェクトを返す。これは summary json の `setup_data` に入り、JSON の口で `[REDACTED-SETUP]` になる。
- リフレッシュトークンの Cookie（`mastersmith_refresh`、217 行）は VU ごとの状態 `vuState.cookie` にだけ持つ（2722・2729 行）。Authorization ヘッダーは要求のたびに組み立てる（774・877・890・1022・1337・1364 行など）。どちらも summary json に入らない。
- 止める条件（秘密を `setup_data` の外と標準出力・ログに出している）には当たらない。

## 6. verify（Step 20）

- `./gradlew verify`（colima の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を付け、`caffeinate -dims` で包んだ）: BUILD SUCCESSFUL、終わりのコード 0、所要 2 分 44 秒（2026-10-30T15:31:59Z〜15:34:44Z）。流した後の `pmset -g log` に Sleep・DarkWake は 0 件。
- 新しいタスク `perfToolFormatCheck`・`perfToolLint`・`perfEvidenceScan` が流れて成功した（`perfEvidenceScan`: 調べたファイル 97 件・検出 0 件・入力の誤り 0 件）。
- 画面のテスト（`frontendTest`・`frontendCoverage`）: 229 ファイル・2,092 件 passed（U4 の 3 ファイル 115 件を含む）。全体のカバレッジの下限も通った。
- バックエンドのテスト（`:backend:test`・`:backend:integrationTest`）は入力が変わらないため UP-TO-DATE で飛ばされた（バックエンドのソースは変えていない。件数は報告しない。`project.md` の決まり）。そのため 2 分 44 秒は全件を流した時間ではない。

## 7. 計画の幅の中で決めたこと・計画との差

- **1 回のなめ**: 計画 4節の見込みどおり替えた。さらに、カバレッジの計測の下での遅さのため、区間の切れ目を粘着の正規表現で読む形にした（上の 2節）。形の定義（正規表現）は道具の説明のコメントとテストの正解に置き、道具の中に使わない正規表現の定数を残していない（oxlint の `no-unused-vars` のため）。
- **1 MiB の例の確かめの順**: 正規表現の形の 1 MiB を Vitest で流さず、小さい大きさの測りで判断した（上の 2節）。
- **io の口**: 計画の `stat`・`readFile`・`realpath`・`open`・`write`・`rename`・`unlink`・`onSignal` に、`close`（書いたファイルを閉じる）と `readdir`（`--scan` で使う）を足した。`main`・`checkAndWrite` は差し替えた口だけを受け、残りは本物を使う。
- **io の差し替えの使い方**: 計画の 3 つ（表示・`rename` の失敗・`onSignal`）に加えて、`--scan` の「読めないファイル・ディレクトリ」の例で `readFile`・`readdir` を、想定外の誤りの例で `stat` を失敗させた（実行する人の権限に頼らずに入力の誤りの経路を確かめるため）。
- **想定外の誤り**: `main` の中で想定していない例外が出たときは、種類の名前（`TypeError` など）だけを表示して終わりのコード 2 で終える（新しいコードは足さない。文言は値を含みうるため出さない）。
- **中断の終わりのコード**: `SIGINT`・`SIGTERM` を受けたら一時ファイルを消してから 130・143（128＋番号）で終える。0〜5 の意味は変えない。
- **`--scan` のリンク**: 名前が `evidence` のディレクトリの下（またはリンク自身の名前が `evidence`）の記号のリンクだけを数えて表示する。`aidlc/` の中のほかのリンクは表示しない。
- **`--scan` の根の名前**: 根そのものの名前が `evidence` のときは、その下をすべて対象にする（README の手順で `evidence` のディレクトリを渡すため。`security-design.md` 9節）。
- **`--scan` の表示**: 検出・誤りは標準エラー、バイナリ・リンク・集計は標準出力。集計は「調べたファイル・うちバイナリとして読んだもの・リンク・検出（件数とファイルの数）・入力の誤り」。
- **道（パスの表示）の形**: `$["setup_data"]["a"][0]` の形（キーは JSON の文字列の形。改行などが表示を崩さない）。
- **Gradle の Prettier・oxlint の呼び方**: 実行ファイルは `frontendDir.file("node_modules/.bin/…").asFile.absolutePath` の絶対パスで呼ぶ（Exec の作業ディレクトリはルートのまま、対象と設定はルートからの相対）。oxlint は `..` を含むパスを受け付けないため、`frontend/` から `../perf/…` では呼べない。ルートから呼ぶ計画の形で通った。
- **README の `evidence/` の行**: U5 の Q2（`evidence/` を含む行を 1 行ずつ道具の呼び方に当たるかで見る）を崩さないよう、地の文に `evidence/` を書かず、見出しと 2 つの呼び方の行だけにした。置いた後の確かめの行は `<記録の置き場>/evidence`（末尾の `/` 無し）。

## 統合と CI — 指揮役の記録

- develop へ squash（13883f6）。作業ブランチは消した。依頼者が push。
- CI（run 41100718494、13883f6）: 1回目の試行で success。検査の job は 17:40:25Z〜18:15:50Z の 35分25秒（42 分の線の内）。再実行なし。
- 要件との対応表は、行が注釈つきの文字列を指して確かめに 37 件はじかれたため、実在する1つのファイルを指す形に直し、説明を note に移した（Deferred の行は持ち主の段のまま）。
