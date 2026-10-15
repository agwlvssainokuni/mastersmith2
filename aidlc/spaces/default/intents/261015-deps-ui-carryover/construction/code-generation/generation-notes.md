# 生成の記録（261015-deps-ui-carryover）

途中の判断と、計画との差を、進めながら書き足す。

## ブランチ ①（Step 1〜11）

### Step 1

- `develop` の先頭 `cf6d5de` から `fix/261015-deps-ui-carryover-1-deps` を作った。
- 作る時点で `aidlc/` の未コミットの変更は、この Intent の監査ログ（`audit/sakura-local-4e42a93f87ce.md`）への追記だけだった。この段の進行でフックが書き足すもので、生成の担当はコミットしない（依頼者の承認が要る）。統合の前にコミットする案をコミットの案に入れる。

### Step 2

- 版とダイジェストの表は `code-summary.md` 1節。
- FR1.2a: `eclipse-temurin:25.0.4.1_1-jdk-noble` があったため、jdk も jre と同じ版にそろえる（前提 A1 の「無ければ据え置く」の分岐は起きない）。
- FR1.2c: `grafana/k6` の 2 系の最新は今の `2.3.0` のままで、版とダイジェストを変えない。Step 6 の k6 の 11 か所は書き換えが無い。
- Actions のタグの確かめで、zsh では `set -- $r` で単語に分かれず、1回目の `gh api` が 404 になった（`project.md` の学びと同じ症状）。`git ls-remote` を1行ずつ書いて確かめ直した。

### Step 3（Gradle）

- `spotless` 8.10.3 → 8.10.4、`jqwik` 1.10.1 → 1.10.2。wrapper は `./gradlew wrapper --gradle-version 9.8.1` の後にもう一度 `./gradlew wrapper` を流した。変わったのは `gradle-wrapper.properties`（配布の URL）と `gradle-wrapper.jar` だけで、`gradlew`・`gradlew.bat` は変わらない。jar の blob（`9fa7f288…`）は Dependabot の #46 の差分と同じ。
- `./gradlew :backend:resolveAndLockAll --write-locks` の差は `backend/gradle.lockfile` の jqwik の5行（1.10.1 → 1.10.2）だけ。`settings-gradle.lockfile` は変わらない。Spotless はプラグインで、lockfile に載らない（前からそう）。

### Step 4（npm）

- 10 件を各プルリクエストと同じ範囲の書き方（`^` 付き）で `package.json` に書いて `npm install` を流したところ、npm が範囲の中の新しいパッチの版（`oxlint` 1.88.0・`@typescript-eslint/parser` 8.71.2・`vitest` と `@vitest/coverage-v8` 5.0.4）まで上げた。計画（各プルリクエストの版）に合わせ、この4つを `npm install --save-dev <名前>@<版>` で入れ直した。直接の依存の版は 20 件のプルリクエストと一致する（`vitest` と `@vitest/coverage-v8` は 5.0.3 でそろう）。
- 推移依存は vite・vitest・oxlint などの依存として上がった（`rolldown` 1.2.9 → 1.2.14、`@oxc-project/types` 0.150.0 → 0.154.0、`postcss` 8.5.28 → 8.5.30 など。`why-is-node-running` だけ 3.2.2 → 3.2.1 に下がった。vitest 5.0.3 の依存の範囲による）。
- `npm install` が `npm audit` の High 7 件を表示した。中身は1件の勧告（`braces` の GHSA-vfj7-8cjw-p6xm、`<=3.0.3`、直った版が無い）が `micromatch`・`fast-glob`・`globby`・`stylelint`・`stylelint-config-*` に連なったもの。`develop` の lockfile でも同じ版（`braces` 3.0.3）で、今回の更新で増えたものではない。`stylelint` 経由の開発時だけの依存で、`config/npm-build-tools.txt` に無いため、決まり（`team.md` の Deployment の学び）では警告の扱い。関門の判定は verify の OSV-Scanner で確かめる。
- `frontend/.npmrc` の `ignore-scripts=true` のまま入れた。

### Step 5（Actions）

- `ci.yml` の4つの `uses:` を、`code-summary.md` 1.2節のハッシュと版のコメントに替えた。

### Step 6・7・8（イメージと固定先の一覧）

- jre（Dockerfile 1・README 4・perf/README 5）、jdk（hikari-pool.sh 1・README 1・perf/README 1）、otel-lgtm（compose 1・perf/README 1）、mailpit（compose 1・docker/perf/compose 1）を、古い「版@ダイジェスト」の組をそのまま新しい組に置き換える形で替えた。行の番号は計画 4.1節のとおりだった。
- README 552 行の mailpit は、ダイジェストを足して v1.31.5 にした（FR1.2b）。文中の既定の値（`--max` 500、`--max-age` 既定で無し）は、v1.31.5 の `--help` で同じと確かめた。
- k6 は版とダイジェストを変えない（Step 2）。11 か所は今の版のまま。
- `.github/dependabot.yml`: 版の文字を `25.0.4.1_1-jre-noble` にし、「Dependabot が見ない固定先」の README.md に Mailpit を足した。jdk は Dependabot が見る固定先が無いこと（jre の知らせのときに同じ版のタグでそろえる）と、`grafana/k6` は知らせが来ず Intent の依存の更新のときに上げることを書いた。jdk の据え置きは無いため、その理由の文は書いていない。
- Step 8 の検索（`vendor/`・`node_modules`・`build`・`aidlc/`・`.git`・`.gradle`・`dist` を除く）: `25.0.4_7-j`・`otel-lgtm:0.34.0`・`mailpit:v1.31.2`・古いダイジェストの先頭 12 文字（jre・jdk・otel-lgtm・mailpit）・古い Actions のハッシュの先頭 12 文字は、すべて 0 件。`grafana/k6:2.3.0` は perf/README.md の 11 件が残る（据え置きで意図どおり）。

### Step 9（新しい jre のイメージの起動、D-13: A）

- WAR を新しい依存で作り直した（`./gradlew :backend:bootWar`、新しい vite で画面のビルドが通った）。`backend/build/libs/mastersmith.war` は上書きされた（SHA-256 `be6d2e15…`）。配備したアプリのイメージは前に作ったもので、動いているコンテナには関わらない。配備の段では WAR を作り直して SHA-256 を確かめる（`project.md` の学び）。
- 使い捨てのタグ `mastersmith:deps-carryover-check` でイメージを作り、名前 `mastersmith-depscheck`・一時のボリューム `mastersmith-depscheck-data`・仮の署名鍵と仮の初期管理者（値は表示しない。ホームの下の権限 700 の一時ディレクトリ）・メモリ 1g で起動した。始める前の VM の空きは約 4.8 GB（総量 6 GiB）。
- 起動から約 10 秒で `/actuator/health` が 200。中の Java は `25.0.4.1+1`（Temurin）。起動のログは INFO 51・WARN 2・ERROR 0 で、WARN の2件（Spring の BeanPostProcessorChecker、Flyway の H2 の版）は配備したアプリの起動でも出る既知のもの。
- 計画との差: 待ち方は計画の「bash の `/dev/tcp`」ではなく、ポートを 127.0.0.1:18081 に公開して PC から `curl` で待った。生成の担当の実行環境のガードが `bash -c` を含むコマンドを止めたため。確かめる中身（200 の応答）は同じ。
- 確かめた後に、コンテナ・ボリューム・イメージ・一時ディレクトリを消した。配備したアプリ（`mastersmith-app-1`）は止めていない。

### Step 10（verify と E2E、見送り）

- 1回目の `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（2026-10-15 21:13:40〜21:29:11、15分30秒、`caffeinate -dims`、colima の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡した）: 成功。backend の単体 2,102 件・結合 1,165 件（失敗 0・SKIPPED 0。対象DB のテストも流れた）、frontend 186 ファイル・1,590 件。カバレッジは backend 全体 行 97.9%・分岐 92.5%、frontend 行 95.42%・分岐 90.29%。OSV-Scanner は失敗の条件 0 件・警告 18 件（`frontend` の `braces` 1件と、`vendor/make-you-chic-ui` の lockfile の開発用の 17 件。どれも開発時だけの依存で、成果物を作る道具の一覧に無い）。この間の `pmset -g log` に Sleep・DarkWake は無い。
- 1回目の `./gradlew e2eTest`（21:30:00〜21:38:29）: 130 件 passed・100 件 failed。
  - 原因: すべて axe の規則 `label-content-name-mismatch`（WCAG 2.5.3、見える文字と読み上げの名前の一致）の新しい違反。axe-core 4.13.0 では、この規則に `experimental` の印が付き既定では流れなかったが、4.14.0 で印が外れて既定で流れるようになった（両方の版の `axe.js` の規則の定義を見比べて確かめた）。
  - 落ちたファイルと件数: 080・100・120・130・160 が各 20 件。違反の要素は、make-you-chic-ui の Dropdown のトリガー（`dropdown-trigger`。表示の設定の選択の誤りの状態・利用者の一覧の行の操作など）と、作業ロールのメニューの項目（`button[aria-labelledby="…-item-0-label"]`）。060 は違反ではなく `incomplete`（判定できない）として出ていて、落ちていない。
  - 扱い: axe-core 4.14.0 は脆弱性の直しを含む更新ではないため、計画の FR1.3 のとおりこの件だけ見送った。`frontend/package.json`・`package-lock.json` の axe-core を 4.13.0（`develop` と同じ）に戻し、`.github/dependabot.yml` の npm の項の `ignore` に `axe-core` の `>= 4.14.0` を足して、理由と外す時期（この規則の違反を画面の側と make-you-chic-ui の側で直した後）をコメントに書いた。
  - 気になる点: 見送りは「新しい規則で見つかった違反」を先送りにするもので、違反そのもの（WCAG 2.5.3）は今の画面にもある。直すには、画面の側の名前の付け方と make-you-chic-ui の Dropdown のトリガーの直し（make-you-chic-ui のリポジトリ側への依頼）が要り、この計画の範囲の外。ブランチ ② で make-you-chic-ui を `a9309c4` に上げてトップバーの文言の口を使うと、一部の名前が変わる見込み。扱いは依頼者に諮る。
- 2回目の `./gradlew e2eTest`（axe-core を 4.13.0 に戻した後、21:40:45〜21:51:15）: 230 件 passed・失敗 0。この間の `pmset -g log` に Sleep・DarkWake は無い。
- Mailpit は、既に動いている compose の `mastersmith-mailpit-1`（v1.31.2 のまま）を E2E の受け手に使った。作り直すと配備した一式の中のサービスを変えるため、v1.31.5 への入れ替えは配備の段で行う（Build and Test・Deployment Execution に引き継ぐ）。E2E が確かめるのはメールが届くことで、Mailpit の版には頼らない。
- 2回目の `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（axe-core を戻した最後の状態、21:51:30〜22:06:11、14分41秒）: 成功。backend の単体 2,102 件・結合 1,165 件（失敗 0・SKIPPED 0）、frontend 1,590 件。カバレッジは backend 全体 行 97.9%・分岐 92.5%、frontend 行 95.42%・分岐 90.25%。OSV-Scanner は失敗の条件 0 件・警告 18 件（1回目と同じ）。この間の `pmset -g log` に Sleep・DarkWake は無い。これを ① の統合の前の確かめの結果とする。
- `frontend/test-results`・`frontend/playwright-report` は E2E の生成物（git の対象外）として残っている。`project.md` の学び（段の途中で消すと承認の場が止まりうる）があるため、生成の担当は消していない。消す時期は指揮役が決める。

### Step 11（コミットの案）

- 生成の担当はコミットしない。案は返事に書いた（C1-1〜C1-5 に、`aidlc/` の記録のコミットを分けて添えた）。各コミットは版・設定・文書だけの変更で、どの区切りでもビルドは通る。

### 依頼者の決定（ブランチ ① の後）

- axe-core 4.14 で初めて出た WCAG 2.5.3（見える文字と読み上げの名前の一致、規則 `label-content-name-mismatch`）の違反（make-you-chic-ui の Dropdown のトリガー、作業ロールのメニューの項目）は、依頼者の答え「A. 記録して後の Intent へ」により、今回は axe-core を見送るだけにし、違反と直し方（画面の側の名前の直し、make-you-chic-ui への Dropdown の直しの依頼）を記録して後の Intent に回す。計画は変えない。
- ブランチ ① のコミット（記録・C1-1〜C1-5）と `develop` への squash の統合（件名「Dependabot の知らせ 19 件を取り込み（axe-core は見送り）、見ない固定先のイメージをそろえる」）は、依頼者の承認「積んで統合する」で行う。
