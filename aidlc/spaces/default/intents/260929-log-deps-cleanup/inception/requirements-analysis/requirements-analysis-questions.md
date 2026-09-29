# Requirements Analysis の質問（260929-log-deps-cleanup）

## 決まっていること（質問にしない）

依頼の文と、依頼者が Intent の作成の直後（2026-09-29）に決めたこと、コードの調査（`aidlc/spaces/default/codekb/mastersmith2/` の K-10〜K-16）で決まっている点です。依頼の文（「TypeScript 7 と typescript-eslint の ignore を決め」）は書き換えず、決めた内容との差を要件に記録します。

- Dependabot（`.github/dependabot.yml`）の npm では、`typescript` の大きな版（semver-major、7 系）だけを ignore にする。typescript-eslint は ignore に入れない（TypeScript 7 に対応した版の知らせは受ける）。TypeScript 7.0.2 のプルリクエスト #5 は取り込まない。
- Jackson（`tools.jackson:jackson-bom`）は、パッチを含むすべての版を ignore にする。3.1.7 のプルリクエスト #20 は取り込まない。脆弱性の直しには OSV-Scanner の関門だけで気づくことを `dependabot.yml` のコメントに書く。
- spotless 8.10.3（#19）と `@types/node` 26.6.3（#18）を取り込む（`gradle/libs.versions.toml` の1行と、`frontend/package.json`・`package-lock.json`）。
- make-you-chic-ui の固定先を `310e1ec` から `077f5b4` へ上げる（Tabs の `a34d611` と Button の hover・active の `077f5b4` の2コミット、CSS と contrast.test の4ファイル）。承認を得た専用のコミットで前後のハッシュを記録し、`develop` へは fast-forward で統合する（team.md・project.md の決まり）。E2E の 100 の `STATE_KNOWN_VIOLATIONS` の2件と README の説明（158・957・990〜993 行）を外し、E2E（100）を手元で流して2件が当たらなくなったことを確かめる。README 38 行の古い固定先 `735ef04` も直す。
- 初期管理者の作成（`InitialAdminInitializer`）の INFO のログ3か所からメールアドレスを外し、単体テスト（今は「含まれること」を確かめている）を「含まれないこと」に直し、結合テスト（`InitialAdminIT`）にも「含まれないこと」の確かめを足す（不具合を再現するテストを同じコミットに含める決まり）。

## Question 1
初期管理者のログからメールアドレスを外したあと、ログに何を残しますか？（今は、既にいるとき・同時の起動で既にいたとき・作ったときの INFO の3か所がキー `email` でメールアドレスを出しています。README 215 行は、起動の確かめで「初期管理者を作成しました」の INFO を見る手順です）

A. 文言だけを残し、キーと値は何も載せない
B. メールアドレスの代わりに、作った（または既にいた）利用者の利用者 ID を載せる
C. メールアドレスを伏せ字にした形（例: 先頭の1文字とドメインだけ）で載せる
X. Other (please specify)

[Answer]: C

## Question 2
監査イベントの記録に失敗したときの ERROR（`監査イベントの記録に失敗しました`）にも、入力されたメールアドレスがキー `enteredEmail` で載ります。前の Intent（260925-user-management の U4）で「後から手で記録を補えるように」と決めて README 774 行に書いた扱いです。今回の直しに含めますか？

A. 含めない。初期管理者の3か所だけを直し、監査の失敗の ERROR は決めた例外のまま残す（例外であることを要件に記録する）
B. 含める。監査の失敗の ERROR からもメールアドレスを外す（後から手で補う手がかりは利用者 ID・トレース ID などに頼る）
X. Other (please specify)

[Answer]: A

## Question 3
警報 `ms-check-p95`（確認用 API `/api/admin/check` の 95 パーセンタイル）のしきい値 300 ms は、指標のバケットの境界（100・250・500・1000・2000・5000 ms）に無く、250〜500 の間の按分の見積もりで判定しています。どう直しますか？（どちらでも、警報・ダッシュボードのパネルのしきい値・SLI の表・README の表の4か所をそろえます）

A. バケットの境界に 300 ms を足す（しきい値 300 ms は変えない。`application.yaml` の `slo` を変え、`http.server.requests` の系列が組ごとに1つ増える）
B. しきい値を境界の 250 ms に下げる（設定の変更は監視の側だけ。今より厳しくなる）
C. しきい値を境界の 500 ms に上げる（設定の変更は監視の側だけ。今より緩くなる）
X. Other (please specify)

[Answer]: C

## Question 4
「team.md も修正する」で直す中身はどれですか？（select all that apply）

A. Testing Posture の `packagesJudgedByTotal` の記述を今のビルドに合わせる（今は「`user.domain`・`user.repository`・`user.service` を含む 22 パッケージ」と書いてあるが、`backend/build.gradle.kts` の一覧は 12 パッケージで `user.*` を含まない）
B. Way of Working の Dependabot の受け方に、ignore の方針（`typescript` の大きな版、Jackson はすべての版で ignore にし、脆弱性は OSV-Scanner の関門で気づく）を足す
C. 上の A・B のほかに直したい箇所がある（Other に書く）
X. Other (please specify)

[Answer]: A

## 追加の質問（答えの確かめ）

## Follow-up F1
Question 1 の答え（C. 伏せ字にした形で載せる）は、`project.md` の Forbidden「NEVER メールアドレスをアプリのログとエラー応答に含めず…」と食い違いうるため確かめます。伏せ字にした形をログに載せることを、この決まりに反しない（メールアドレスそのものは含めない）とみなしてよいですか。よい場合は、伏せ字の形も決めてください。

A. 反しないとみなす。形は「先頭の1文字＋`***`＋`@`＋ドメイン」（例 `a***@example.com`）
B. 反しないとみなす。形は「`***`＋`@`＋ドメイン」だけ（ローカル部を1文字も出さない）
C. 伏せ字はやめ、文言だけを残す（Question 1 の A に替える）
D. 伏せ字はやめ、利用者 ID を載せる（Question 1 の B に替える）
X. Other (please specify)

[Answer]: A

## Follow-up F2
Question 2 の答え（A. 監査の失敗の ERROR は含めない）では、その ERROR がメールアドレスをそのまま載せる状態が残り、同じく `project.md` の Forbidden と食い違ったままになります。どう扱いますか？

A. 前の Intent で決めた例外として残し、Forbidden との差を要件（範囲の外・既知の例外）に記録する
B. この Intent で、監査の失敗の ERROR のメールアドレスも F1 と同じ伏せ字の形にそろえる
C. この Intent で、監査の失敗の ERROR からメールアドレスを外す（Question 2 の B に替える）
X. Other (please specify)

[Answer]: B

## Follow-up F3
Question 3 の答え（C. しきい値を 500 ms に上げる）は、確認用 API の目標（ダッシュボードの SLI の表「95 パーセンタイル 300 ミリ秒以内」）を緩めることになります。目標を緩めることを受け入れますか？

A. 受け入れる。警報・パネル・SLI の表・README の表を 500 ms にそろえ、目標を 500 ms に変えたことを要件に記録する
B. 目標 300 ms は変えず、バケットの境界に 300 ms を足す（Question 3 の A に替える）
C. 目標をより厳しい 250 ms にする（Question 3 の B に替える）
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

決まっていること（冒頭）と答えのまとめです。

- 初期管理者の作成の INFO のログ3か所（既にいる・同時の起動で既にいた・作った）は、メールアドレスそのものを載せず、「先頭の1文字＋`***`＋`@`＋ドメイン」の伏せ字で載せる。この形は Forbidden（メールアドレスをアプリのログに含めない）に反しないとみなす（Q1: C、F1: A）。
- 監査イベントの記録の失敗の ERROR（キー `enteredEmail`）も、同じ伏せ字の形にそろえる。後から手で補う手がかりは伏せ字・利用者 ID・トレース ID になる。README 774 行の説明も直す（Q2: A、F2: B）。
- 単体テスト（今は「含まれること」）を「メールアドレスそのものが含まれず、伏せ字が含まれること」に直し、結合テストにも同じ確かめを足す。監査の失敗の ERROR にも同じ確かめを足す。
- 警報 `ms-check-p95` のしきい値を 300 ms からバケットの境界の 500 ms に上げ、警報・ダッシュボードのパネルのしきい値・SLI の表・README の表の4か所をそろえる。確認用 API の目標を 500 ms に緩めたことを要件に記録する（Q3: C、F3: A）。
- team.md は、Testing Posture の `packagesJudgedByTotal` の記述を今のビルド（12 パッケージ、`user.*` を含まない）に合わせる。Dependabot の ignore の方針は team.md には足さない（Q4: A）。
- Dependabot: npm は `typescript` の大きな版（7 系）だけを ignore（typescript-eslint は入れない）、Jackson はすべての版を ignore にして OSV-Scanner の関門で気づくことをコメントに書く。spotless 8.10.3 と `@types/node` 26.6.3 を取り込む。#5・#20 は取り込まない。
- make-you-chic-ui を `077f5b4` に上げ（専用のコミット、前後のハッシュを記録、fast-forward で統合）、E2E の 100 の既知の違反2件と README の説明を外し、E2E（100）を手元で流して確かめる。README 38 行の古い固定先も直す。

Does this all look correct before I generate the requirements artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
