# 生成の途中の記録（Intent 260929-log-deps-cleanup）

次の依頼と code-summary.md で使う、生成の途中で分かった数字・結果の記録。数字はすべて実測。

## Step 1 作業のブランチ

- `develop` の HEAD: `3c3808189ac70c1c8c34055b6603b7ecac933b89`
- 作業のブランチ `fix/260929-log-deps-cleanup` を作って切り替えた。
- `git status`: 変更はワークフローの記録（`aidlc/spaces/default/intents/260929-log-deps-cleanup/`）の中だけ。
- `git submodule status`: 2つとも固定先のまま（`+` なし）。`vendor/java-mustache-processor` `8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4`、`vendor/make-you-chic-ui` `310e1ecf2fe8b4a388cb8ae098a59144dc93acc5`。

## Step 2 テストの仕組みの確かめ（変更の前のコード）

- 2.1 `./gradlew :backend:test --tests …EmailAddressTest --tests …InitialAdminInitializerTest`: 成功。`EmailAddressTest` 11 件、`InitialAdminInitializerTest` 8 件（失敗・SKIPPED 0）。
- 2.2 `./gradlew :backend:integrationTest --tests …InitialAdminIT`: 成功。3 件（失敗・SKIPPED 0）。
- 2.3 prettier・oxlint・eslint（`e2e/100-app-text-contrast.e2e.ts`）: すべて成功。

## Step 3 全体の基準（変更の前、`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`）

- colima は動作中。`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡して実行。`BUILD SUCCESSFUL in 6m 37s`。
- バックエンドの単体テスト（`*Test`）: 1234 件（151 クラス）、失敗 0・誤り 0・SKIPPED 0。
- バックエンドの結合テスト（`*IT`）: 565 件（114 クラス）、失敗 0・誤り 0・SKIPPED 0。
- JaCoCo（単体＋結合の合算）: 全体 行 98.8%（5600/5669）・分岐 94.4%（2050/2172）。
  - `user.domain`: 行 99.5%（199/200）・分岐 97.5%（115/118）。
  - `user.service`: 行 100.0%（227/227）・分岐 95.5%（84/88）。
- frontend（Vitest）: 91 ファイル・732 件すべて成功。カバレッジ 文 97.21%・分岐 92.67%・関数 97.66%・行 97.44%。
- `vendorUnchanged`・`mustacheVendorUnchanged` は成功。`osvScan` は UP-TO-DATE（前回の成功の結果を使った）。

## Step 4 E2E 100 の基準（変更の前、固定先 `310e1ec`）

- `./gradlew :backend:bootWar`、`docker compose --profile mail up -d mailpit`、`npx playwright test e2e/100-app-text-contrast.e2e.ts`: 20 件すべて成功（42.3 秒）。秘密の確かめの報告は「3 項目を確かめた」で成功。
- `frontend/test-results/e2e-results.json` の注記（`axe`、80 件＝20 組×4 状態）の集計:
  - 既知の違反 `dsl-schema-link` / `color-contrast tab-1`: 10 組（(a) dark sm・md・lg blue、(b) blue dark md、(b) green light md、(b) purple dark md、(b) orange light md、(c) dark sm・md・lg blue 375px）。
  - 既知の違反 `primary-button-hover` / `color-contrast preferences-save-button`: 4 組（(b) green light md・dark md、(b) orange light md・dark md）。
  - `unexpected`: 0 件。
- 集計の方法: 作業用のスクリプト（リポジトリの外）で json の注記だけを読み、組の名前と違反の名前だけを出した（資格情報は読んでいない）。

## Step 5・Step 6 ドメインの層（`EmailAddress.mask`）

- 実装: 最後の `@` で分け、先頭の1コードポイント＋`***`＋`@`＋ドメイン。null・空 → null。`@` が無い・ローカル部が空 → `***`。`at >= 1` のとき先頭のコードポイントは必ず `@` より前で終わるため、その場合分けは置いていない（通らない分岐を作らない）。
- テストを足した（`EmailAddressTest`）: 例 6 件（通常・ローカル部1文字・先頭がサロゲートペア・`@` が2つ・`@` なし・ローカル部が空）、null と空 2 件、性質ベース 1 件（英数字 2〜30 文字のローカル部と `[a-z]{1,20}.example` のドメイン）。
- 計画との差: 性質ベースの「結果にローカル部そのものが含まれないこと」は、`@` より前の部分で確かめた（ドメインの側にローカル部と同じ並び、例 `ex` と `.example`、が偶然あり得て、結果の全体で見ると実装が正しくても落ちるため）。
- 2.1 のコマンド: 成功。`EmailAddressTest` 20 件（前 11 件＋9 件）、`InitialAdminInitializerTest` 8 件。失敗・SKIPPED 0。jqwik の乱数の種は結果の XML に出る（例 `-1815547952993594754`・`7094549074223740882`）。

## Step 7・Step 8 業務処理の層（初期管理者の作成のログ）

- 実装: INFO の3か所のキー `email`（値 そろえたメールアドレス）を、キー `maskedEmail`・値 `EmailAddress.mask(email)` に替えた。文言と WARN は変えていない。クラスの Javadoc の「据え置き」の記述を、伏せ字だけを載せ外部エクスポートでも伏せ字がそのまま送られることに書き直した。
- `InitialAdminInitializerTest`: 3か所（`existing`・`creates`・`duplicate`）で、本文とキー・値にメールアドレスそのもの（そろえた値、`creates` では設定の値 `Admin@Example.com` も）が無く、キー `email` が無く、キー `maskedEmail` が `a***@example.com` であることを確かめる形にした（`creates` の「含まれること」は逆にした）。件数は 8 件のまま。
- `InitialAdminIT.createsOnce`: 標準出力の全体に `admin@example.com`・`Admin@Example.COM` が無いこと（`JsonLogRecords.assertContainsNoSecret`）と、ロガー `InitialAdminInitializer` の JSON の記録が「作成しました」「既にいるため」の順の2件で、どちらも `level` INFO・`maskedEmail` `a***@example.com`・キー `email` なしであることを確かめる形にした。件数は 3 件のまま。
- 2.1・2.2 のコマンド: 成功（`EmailAddressTest` 20・`InitialAdminInitializerTest` 8・`InitialAdminIT` 3、失敗・SKIPPED 0）。
- 再現の確かめ: `git stash push -- backend/src/main/java/cherry/mastersmith/user/service/InitialAdminInitializer.java` で直しの前に戻して流した結果、`InitialAdminInitializerTest` は 8 件中 3 件が失敗（`existing`・`creates`・`duplicate`。設定の不備の5件は成功）、`InitialAdminIT` は 3 件中 1 件が失敗（`createsOnce`）。直したテストが不具合を捕まえることを確かめた。`git stash pop` で戻し、`git diff` で直しが戻ったこと（`maskedEmail` 5 か所）を確かめ、流し直して成功した。失敗時の出力（メールアドレスを含みうる）はリポジトリの外の一時のファイルに置き、表示せずに消した。

## Step 9 make-you-chic-ui の固定先

- `git -C vendor/make-you-chic-ui cat-file -t 077f5b4` → `commit`（手元にあった。fetch はしていない）。
- 前の固定先（`git ls-tree HEAD vendor/make-you-chic-ui`）: `310e1ecf2fe8b4a388cb8ae098a59144dc93acc5`
- 後の固定先（`checkout --detach 077f5b4` の後の `rev-parse HEAD`）: `077f5b48ce84cd020ecec2d925836a085f9d9e11`
- 取り込む2コミット: `a34d611`（Tabs: active タブの文字色コントラスト不足を修正）・`077f5b4`（Button: primary ボタンの hover/active 時の文字色コントラスト不足を修正）。差分は `Button.css`・`Tabs.css`・`theme/contrast.test.ts`・`theme/semantic.css` の4ファイル（44 行追加・5 行削除）。
- `git -C vendor/make-you-chic-ui status --porcelain` は空（中身は変えていない）。ステージ・コミットはしていない（C1 で承認を得て行う）。

## Step 10 固定先を上げた後の E2E 100（`STATE_KNOWN_VIOLATIONS` は2件のまま）

- `./gradlew :backend:bootWar`: `vendorBuild`・`frontendBuild` が動き、成功（`frontend/node_modules/make-you-chic-ui` は `vendor/…/packages/make-you-chic-ui` への symlink のため、作り直した make-you-chic-ui が使われる）。
- 100: 8 件成功・12 件失敗（48.3 秒）。失敗の 12 件は、Step 4 で既知の違反が当たっていた組（タブ 10 組とボタンの hover 4 組の和、12 組）と一致し、どれも「既知の違反が一覧と違います」で失敗した（見込みどおり）。秘密の確かめの報告は成功。
- 注記（`axe`、64 件）の集計: `knownViolations` 0 件、`unexpected` 0 件。2件とも当たらなくなった（解消した）と判断した。
- 注記が 80 件ではなく 64 件なのは、失敗した組がその状態で止まり、後の状態を検査していないため（green・orange の4組は `dsl-schema-link`・`not-found-link` を、ほかの8組は `not-found-link` を検査していない）。未検査の状態は Step 11 の実行で確かめる。
- 新しい違反（`unexpected`）は、検査した範囲では出ていない。

## Step 11 E2E 100 の既知の違反の一覧

- `frontend/e2e/100-app-text-contrast.e2e.ts`: `STATE_KNOWN_VIOLATIONS` を空の配列にした（型 `StateKnownViolation` と、一覧と違えば失敗にする仕組みは残した）。冒頭の注記と一覧の上の注記を、`077f5b4` で2件とも解消したこと、一覧は空であること、一覧と違う違反が出たら失敗にすることに直した（注記の中の変数の名前 `--color-primary-emphasis-text`・`--color-primary-hover-text`＝白は、`310e1ec..077f5b4` の `Tabs.css`・`Button.css`・`semantic.css` の差分で確かめた）。
- 2.3 の prettier・oxlint・eslint: 成功。あわせて `npx tsc --noEmit -p tsconfig.json`（`e2e` を含む）も成功。
- 2.4 の手順で 100: 20 件すべて成功（44.4 秒）。注記 80 件（20 組×4 状態）、`knownViolations` 0 件・`unexpected` 0 件（Step 10 で未検査だった状態も含め、新しい違反は出なかった。Q-E: B で足すものは無し）。秘密の確かめの報告は成功。
- 終わった後に Mailpit を `docker compose stop mailpit`・`docker compose rm -f mailpit` で片付けた。

## Step 12 コントラスト比の計算の値（README の Step 20 で「計算の値」として使う）

- 元の値: `vendor/make-you-chic-ui/packages/make-you-chic-ui/src/theme/tokens.css`・`semantic.css`（077f5b4、読み取りだけ）。WCAG 2.x の相対輝度の式で、作業用のスクリプト（リポジトリの外）で計算した。背景はこれまでの README・E2E の注記と同じく light `#fafafa`（`--color-bg`）、dark `#0b0f19`（`--gray-950`）。
- 計算の方法の確かめ: 同じスクリプトで直す前の値（タブの文字 brand-500: light green 3.16・orange 3.41、dark blue 3.71・purple 3.56、hover の文字 gray-900 on brand-600: green 3.54・orange 3.43）を出し、E2E の注記に書かれていた値と一致した。
- primary の Button の hover（背景 `--color-primary-hover`＝brand-600、文字 `--color-primary-hover-text`＝`#fff`。テーマに依らない）:
  - blue `#1d4ed8` 6.70:1、green `#15803d` 5.02:1、purple `#7e22ce` 6.98:1、orange `#c2410c` 5.18:1（4色とも 4.5:1 以上）
- 参考 active（背景 `--color-primary-active`＝brand-700、文字 `#fff`）: blue 8.72:1、green 7.13:1、purple 8.72:1、orange 7.31:1
- Tabs の選ばれたタブの文字（`--color-primary-emphasis-text`。light は brand-700 on `#fafafa`、dark は brand-400 on `#0b0f19`）:
  - blue light `#1e40af` 8.36:1・dark `#60a5fa` 7.53:1
  - green light `#166534` 6.83:1・dark `#4ade80` 10.99:1
  - purple light `#6b21a8` 8.35:1・dark `#c084fc` 7.25:1
  - orange light `#9a3412` 7.00:1・dark `#fb923c` 8.46:1（8組とも 4.5:1 以上）
- 補足: 直した後のタブの下線（`border-bottom-color`）は `--color-primary`（brand-500）のまま（文字ではないため、文字のコントラストの対象外）。

## Step 13・Step 14 spotless

- `gradle/libs.versions.toml` の `spotless` を `8.10.2` → `8.10.3` にした（1行）。
- `./gradlew spotlessCheck :backend:spotlessCheck`: 成功（`spotlessKotlinGradleCheck`・`backend:spotlessJavaCheck`・`backend:spotlessMailTemplatesCheck`）。書式の判定の変化は無く、既存のファイルは引っかからなかった。`./gradlew buildEnvironment` で `com.diffplug.spotless.gradle.plugin:8.10.3` が解決されていることを確かめた。

## Step 15 `@types/node`

- `npm install --save-dev @types/node@26.6.3 --no-audit --no-fund`: 「changed 1 package」。
- `git diff --stat frontend/`（100 の E2E のファイルを除く）: `package.json` 1行（範囲 `^26.2.0` → `^26.6.3`）、`package-lock.json` 4行（ルートの devDependencies の範囲と `node_modules/@types/node` の `version`・`resolved`・`integrity`）だけ。`undici-types` など、ほかの部品の解決は変わっていない。
- `npm ci --no-audit --no-fund`: 成功（`node_modules/@types/node` は 26.6.3、`node_modules/make-you-chic-ui` は vendor への symlink のまま）。`npm run typecheck`: 終了コード 0。

## Step 16 Dependabot

- `.github/dependabot.yml`: npm に `typescript` の `version-update:semver-major` だけの ignore とコメント（理由は `@typescript-eslint/*` の peer が `typescript` の `>=4.8.4 <6.1.0`。手元の `frontend/node_modules/@typescript-eslint/{parser,project-service,tsconfig-utils,typescript-estree}` の peer で確かめた。`@typescript-eslint/*` は ignore に入れない。外す時期）を足した。gradle の `tools.jackson:jackson-bom` の `update-types` を外し（すべての版）、コメントを、すべての版を知らせないこと・OSV-Scanner の関門（`./gradlew verify` と CI の実行のときだけ働く）で気づくこと・team.md の「High 以上の知らせは次の Bolt の前に取り込む」との差（R-02）・外す時期に書き直した。
- `ruby -e 'require "yaml"; YAML.load_file(".github/dependabot.yml")'`: 成功。ignore の中身: gradle 3件（jackson-bom は `update-types` なし）、npm 1件（typescript・semver-major）、docker 1件（変更なし）。
- FR4.5: `git diff gradle/libs.versions.toml frontend/package.json` に `jackson`・`typescript` の行の変更は無い（`jackson = "3.1.6"`・`"typescript": "^6.0.3"` のまま）。

## Step 17・Step 18 警報 ms-check-p95

- 5か所を 500 にした: `mastersmith.yaml` の summary・しきい値、`mastersmith-overview.json` の SLI の表・パネル 11 の説明（「目標 500 ミリ秒（U3 の NFR1.1 の 300 ミリ秒を、Intent 260929-log-deps-cleanup で http.server.requests のバケットの境界の 500 ミリ秒に緩めた）」）・しきい値の段。`application.yaml` の `slo` のコメントを「警報のしきい値 500 ms（確認用 API、Intent 260929-log-deps-cleanup）・1000 ms を境界に含める。」にした（値は変えていない）。
- `python3 -m json.tool`・`ruby YAML.load_file`: どちらも成功。
- `grep -rn '300' docker/monitoring` の残り: パネル 11 の説明の中の経緯の「300 ミリ秒」（緩めた元の値の記述。しきい値ではない）、`30000`（「操作ごとの時間（95 パーセンタイル）」のしきい値）、`3000`（「送信の時間（95 パーセンタイル）」のしきい値）。確認用 API の目標・しきい値としての 300 は残っていない。

## Step 19 手元の監視での確かめ（NFR4）

- 始めた時点で `lgtm`・`app`・`targetdb-postgres` は動作中。`docker compose --profile monitoring up -d --force-recreate --no-deps lgtm` で `lgtm` だけを作り直した（`--no-deps` は、ほかのコンテナに触れないために足した。`lgtm` に depends_on は無い）。healthy になった。`app`（`da985bf25466`）・`targetdb-postgres`（`e26a73012bdd`）のコンテナの ID と起動の時刻は前後で変わっていない。
- `/api/prometheus/grafana/api/v1/rules`: 規則 16 件、`health` が `error` のものは 0 件。`確認用 API の遅れ`（ms-check-p95）は `health` ok・`state` inactive、summary は「…500 ミリ秒を超えた。…」。
- しきい値は `/api/ruler/grafana/api/v1/rules`（ログインなしで 200）で読み、`ms-check-p95` の C の evaluator が `{"type": "gt", "params": [500]}`。
- `/api/search`: `mastersmith-overview`（MasterSmith の概要）。`/api/dashboards/uid/mastersmith-overview`: パネル 11 のしきい値の段が `[green null, red 500]`、説明は新しい文、SLI の表の行が「確認用 API の 95 パーセンタイル | 500 ミリ秒以内」。
- 警報の式を Grafana のデータソースの問い合わせ（`/api/datasources/proxy/uid/prometheus/api/v1/query`）で実行: HTTP 200・`status` success・結果は空のベクトル（誤りなく評価された。直近 5 分に `/api/admin/check` の系列が無いため値は無い）。
- 参考: 直近1日の `http_server_requests_milliseconds_bucket{service_name="mastersmith"}` の `le` は `+Inf` だけで、500 の境界の系列は今の Prometheus には無かった（配備したアプリが今は指標を送っていないとみられる。Q-D: A により要求は送らず、これ以上は確かめていない）。
- `/api/admin/check` には要求を送っていない。`lgtm` は始めた時点で動いていたため、止めずに動いている状態のまま残した。

## Step 20 README

- 38 行（固定先を `077f5b4` にし、`edb1f94` → `735ef04` → `310e1ec` → `077f5b4` の経緯）、158 行（100 の既知の違反は無く一覧は空）、215 行（INFO はキー `maskedEmail` に伏せ字だけ）、664 行（`maskedEmail` は伏せる対象にせず、外部エクスポートでも伏せ字がそのまま送られる）、682 行（しきい値 500 ms・1000 ms）、703 行（`ms-check-p95` を 500 ms と、300 ms から緩めた理由の1文）、957 行（残っていた既知の制約は `077f5b4` で解消）、976 行（`077f5b4` からは hover・active の文字と選ばれたタブの文字も出し分ける）、990〜993 行（「残る既知の制約」を「解消した既知の制約」に書き直し、Step 12 の計算の値の表と active の値を足した）。113・122・130・138・909・924・938 行の `310e1ec` の記述はそのまま。
- 作業の途中で、990〜993 行の置き換えの範囲の終わりを誤って前の節の見出し（同じ名前の「### 契約との差」が 859 行にもある）で取り、内容が重なった。作業用の写し（リポジトリの外）と `git show HEAD:README.md` から組み直し、`git diff --stat README.md` が 24 行追加・11 行削除、「### 契約との差」が元と同じ3か所であることを確かめた。
- `grep -n '300' README.md` のうち「手元の監視（Grafana）」「警報と対応の手順」の節に残るのは、676 行のポート `3000` と、705 行の「以前の 300 ms から 500 ms に緩めました」の経緯の文だけ。

## Step 21 統合の前の関門（失敗、止めた）

- colima は動作中（VM は CPU 4・メモリ 約 5.8GiB）。`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡し、`caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を流した。`BUILD FAILED in 6m 29s`。
- 通った段: `vendorUnchanged`・`mustacheVendorUnchanged`、書式（`verifyFormat`）、リンタ（`verifyLint`）、ライセンスヘッダー（`verifyLicense`）、ビルド・型の検査（`verifyBuild`）、単体テスト（`verifyUnitTest`）。
  - バックエンドの単体テスト: 1243 件（151 クラス）、失敗 0・誤り 0・SKIPPED 0（Step 3 の 1234 件＋`EmailAddressTest` の 9 件）。
  - frontend（Vitest）: 91 ファイル・732 件すべて成功。
- 落ちた段: `:backend:integrationTest`。565 件（114 クラス）中 1 件失敗、誤り 0・SKIPPED 0。そのため、カバレッジの検証・`osvScan` などの後の段は流れていない（カバレッジの値は取れていない）。
- 落ちたテスト: `cherry.mastersmith.common.observability.OtlpLogExportIT` の `personalValuesMasked`（「personal values in exported logs are masked」）。`OtlpLogExportIT.java:246` の `[email] Expecting actual: 0 to be greater than: 0`。
- 見立て: 変更の経路に直接関わる失敗で、一時的な失敗ではない（流し直しはしていない）。このテストは、伏せるキー `email`・`enteredEmail`・`sourceIp`・`userAgent` のそれぞれについて、外部へ送ったログに `[REDACTED]` の値の属性が1件以上あることを確かめる。キー `email` を出していたのは初期管理者の作成の INFO だけ（テストの注記「初期管理者の作成の INFO（起動のとき）と、監査の書き込みの失敗の ERROR の両方で、キーは残り値は伏せられる」、`backend/src/main/java` でキー `email` を載せる箇所はほかに無い）。Step 7 でキーを `maskedEmail` に替えた（Q-B: B）ため、キー `email` の属性が 0 件になった。`ADMIN_EMAIL`・利用者のメールアドレス・User-Agent が送られていないことの確かめ（250〜252 行）は、この失敗の行より後のため、この実行では評価されていない。
- 計画の影響の範囲（2節）に `OtlpLogExportIT` が無かった（キーの名前の変更が外部エクスポートのテストに及ぶことを計画で洗い出していなかった）。テストを直すかどうか・どう直すかは依頼者の判断が要るため、ここで止めた。Step 22・Step 23 は行っていない。

## G1・G2 の後（依頼者の答え G1: A、G2: A）

- `OtlpLogExportIT`: `personalValuesMasked` はキー `email` の属性が 0 件であることを確かめる形にし（`enteredEmail`・`sourceIp`・`userAgent` の確かめと、メールアドレス・User-Agent が送られていないことの確かめは残した）、`maskedEmailExportedAsIs`（`maskedEmail` が伏せ字 `o***@example.com` のまま送られ、`[REDACTED]` が 0 件、メールアドレスそのものが無い）を足した。`SanitizingLogRecordExporter` は変えていない。
- このテストだけ（`:backend:spotlessJavaCheck :backend:integrationTest --tests …OtlpLogExportIT`）: 成功、4 件すべて成功。

## Step 21 統合の前の関門（2回目、成功）

- `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`: `BUILD SUCCESSFUL in 6m 19s`。`vendorUnchanged`・`mustacheVendorUnchanged`・`verifyFormat`・`verifyLint`・`verifyLicense`・`verifyBuild`・`verifyUnitTest`・`verifyIntegrationTest`・`verifyCoverage`（`jacocoTestCoverageVerification`）・`spotbugsGate`・`gitleaksScan`・`osvScan`（実行された）・`verifySecurity`・`verifyArtifact` がすべて成功。
- 単体テスト 1243 件（151 クラス）、結合テスト 566 件（114 クラス）、失敗・誤り・SKIPPED とも 0。frontend 91 ファイル・732 件成功、文 97.21%・分岐 92.67%・関数 97.66%・行 97.44%。
- JaCoCo: 全体 行 98.8%（5607/5676）・分岐 94.4%（2056/2178）。`user.domain` 行 99.5%（206/207）・分岐 97.6%（121/124）。`user.service` 行 100.0%（227/227）・分岐 95.5%（84/88）。

## Step 22 E2E の全体

- `docker compose --profile mail up -d mailpit` の後に `./gradlew e2eTest`: 110 件すべて成功（3.9 分、SKIPPED 0・flaky 0）。100 の注記は `knownViolations` 0 件・`unexpected` 0 件。秘密の確かめの報告は「3 項目を確かめた」で成功。
- Mailpit を `docker compose stop mailpit`・`docker compose rm -f mailpit` で片付けた。`app`・`lgtm`・`targetdb-postgres` は動いたまま。

## Step 23 段の記録

- `code-summary.md`・`traceability.json`（23 件。OK 19・N/A 2（FR2.1・FR2.2）・Deferred 2（FR6.1・FR6.2）。OK の target はすべて実在するファイル）・`source-manifest.json`（`unit` は単位なしの前の Intent 260928-quality-followup と同じく null。`writes` は `git diff --name-only` のワークフローの記録の外の 16 パスと一致、`vendor/make-you-chic-ui` を含む）を書いた。
