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

## ブランチ ②（Step 13〜30）

### Step 13

- `develop` の先頭 `3472705`（① の squash の統合）から `fix/261015-deps-ui-carryover-2-ui` を作った。作業ツリーには、計画の Step 11 の印と、この Intent の監査ログ（`audit/sakura-local-4e42a93f87ce.md`）の未コミットの変更があり、そのまま持ってきた（生成の担当はコミットしない）。

### Step 14（固定先の更新の前の準備、D-1: A）

- `ShellLayout.tsx` の `navLabels` を、型の注記（`SidebarLabels`）を外したオブジェクトにし、`containsCurrent`（`nav.containsCurrent`。ja「{{label}}（今のページを含む）」・en「{{label}} (contains the current page)」）を足した。`AppShell` の `navLabels` は `Partial<SidebarLabels>` で、注記の無い変数として渡すため、`5bf1ffe` の型（`containsCurrent` が無い）でも余分な項目として咎められない。引数には `(label: string)` の型を書いた（暗黙の any を避けるため）。`SidebarLabels` の import は使わなくなったので外した。
- `5bf1ffe` の成果物（`dist`）のまま `npm run typecheck`・`src/app/layout`・`src/app/i18n` のテスト（66 件）が通ることを確かめた。

### Step 15（固定先の更新の専用の変更）

- `git -C vendor/make-you-chic-ui checkout a9309c4ee863b162236571a7d6300c62fd4b4f70` だけを行った（中身は変えていない。`git -C vendor/make-you-chic-ui status --porcelain` は空）。親での固定先の記録が `5bf1ffe3cb7ee5c49963c3d7e8d4e8d0f032aae6` → `a9309c4ee863b162236571a7d6300c62fd4b4f70` に変わる。
- `./gradlew vendorBuild frontendTypecheck frontendBuild` が通り、`src/app` のテスト（37 ファイル・347 件）が通った。`a9309c4` は package の版の定義と lockfile を変えない（`git diff --stat 5bf1ffe a9309c4 -- package.json package-lock.json packages/make-you-chic-ui/package.json` は空）。

### Step 16（FR3.1、再現のテストと直し）

- `dslmanage/web/DslSecretLeakIT` を書き、直す前に2件とも落ちることを確かめた（2026-10-15 22:19）。落ちた行（値は目印だけ）:
  - `DefaultDslReader#read()` の EXIT（`DslReadResult.Invalid` の中の `DslError` の `path` と `messageArgs`）
  - `ErrorResponseFactory#create()`・`GlobalExceptionHandler#handleBusiness()` の EXIT（`ProblemDetail` の `errors` の `DslErrorItem` の `path` と `message`）
- `DslError.toString` を「種類・行・列・文言の鍵・埋める値の件数」、`DslErrorItem.toString` を「種類・行・列」だけにした（D-11: A）。応答の本文は Jackson が項目の値をそのまま書くため、変わらない。直した後に2件とも通った。
- 既存のテストで `DslError` の文字列の中身に頼るものは無かった（`toString()` を使うテストは、どれも値が出ないことの確かめ）。

### Step 17（FR4.1・FR4.2）

- `navigation/web/NavigationSecretLeakIT` を書き、直す前に落ちることを確かめた（22:21）。落ちた行は `NavigationService#tableAccess()`（`TableAccessResult.Visible` の `DisplayName`）と `NavigationController#tableAccess()`（`TableAccessResponse` の `DisplayLabelResponse`）。
- **計画との差（依頼者に諮る）**: 同じテストで、`dsl.service.ActiveDslModelStore#current()` の EXIT が、適用中の DSL のモデル（`ActiveDsl.Present` の `DslModel`）を丸ごと文字列にして、すべてのスキーマ・テーブル・メニューの表示名を TRACE に出していると分かった。これは dsl の部品の文字列化で、計画の FR4.1 の対象（navigation の6つの record）の外のため、直さずに、このテストではこのロガーの行だけを確かめから外した（外す範囲は1つのロガーだけ。テストの中のコメントに理由を書いた）。直すなら `DslModel`（dsl.domain）の `toString` を識別の先頭と件数だけにする1か所の変更で、`ActiveDsl.Present`・`DslReadResult.Valid`・`DslStartupReadResult.Valid` の文字列も伏せられる。表示名は個人に関する値ではなく業務の名前だが、FR4.1・NFR4 の「利用者が書いた値」に当たる。
- `DisplayLabel`・`NavTableRef`・`TableAccessResult.Visible`・`DisplayLabelResponse`・`NavTableRefResponse`・`TableAccessResponse` の `toString` を上書きし、名前と表示名を `***` にした（`Visible`・`TableAccessResponse` は主権限だけを出す）。D-10: A のとおり、controller と service の引数のスキーマ名・テーブル名はそのまま（業務の名前として受け入れる）。
- `MenuFilter` の Javadoc に、深さの前提（`DslFormat.MAX_MENU_DEPTH` = 5 の検証を通ったモデルだけが来る、起動時は深すぎる枝を落とす、この関数は深さを数えない、境界のテストの持ち主は dsl の `MenuDepthTest`・`MenuDepthPropertyTest`）を書いた。

### Step 18

- 足した単体のテスト: `dsl/domain/DslErrorTest`（3件）、`dslmanage/domain/DslManageDomainTest` に `DslErrorItem` の2件、`navigation/domain/NavigationRecordsToStringTest`（3件。web の2つの応答の record も同じテストで確かめた）。
- `dsl.domain`・`dslmanage.domain`・`navigation.*`・`dsl.validate`・`dsl.parse`・`dsl.service` と `*ArchitectureTest` の単体（298 件）、navigation の結合テストと `DslAdminApiIT` が通った。

### Step 19・20（FR3.2・R-08、D-7: A）

- 落とした項目の数の置き場は、計画の「`appliedUnreadable` と同じ形」に合わせ、新しい部品を作らずに既存の `UnreadableAppliedRevision`（dslmanage.service、パッケージの中だけ）に `rememberPruned(UUID, int)`・`prunedMenuItems(UUID)` を足した。読めなかった版と落とした版は同じ起動で両方にならないため、どちらかを覚えるともう片方を消す。部品の名前は今の役目より狭くなったが、`DslLifecycle` と `DslStartupLoader` の作り方（コンストラクターの引数）を変えずに済む。Javadoc に役目を書き足した。
- `DslStatus` に `int prunedMenuItems`（0 以上を確かめる）を足し、`DslLifecycle.status()` と `DslStatusResponse` に載せた。既存の項目は変えていない。
- テスト: `DslStartupLoaderTest`（落とした数を覚える・落とさずに読めたら消す・読めなかったら消す）、`UnreadableAppliedRevisionTest`（3件）、`DslStartupIT`（深さ 6 で起動したら `prunedMenuItems` 6、読めない版と上限の内の版では 0）、`DslAdminApiIT`（空の状態で 0）、`DslLifecycleTest` の `new DslStatus` の引数を直した。

### Step 21（FR2.2・FR2.4）

- `ShellLayout.tsx` で `AppShell` に `topbarLabels`（`topbar.expandSidebar`・`topbar.collapseSidebar`・`topbar.userMenu`。ja は make-you-chic-ui の既定と同じ文言、en は「Expand the sidebar」「Collapse the sidebar」「Menu for {{name}}」）を渡した。
- `WorkRoleSwitcher.css` の `:has()` の `@media` の塊と説明のコメントを外し、ファイルの先頭のコメントに、一覧の幅を make-you-chic-ui の Dropdown の既定に任せたことを書いた。表の右端の Dropdown に `menuClassName`・`menuStyle` は使っていない（使う場面は無かった）。`InvitationList.css` は変えていない。

### Step 22（FR5.1・FR5.2、K4）

- `SharedTreeNodeItem.tsx`: 子の読み込み中・失敗の区画に `role="group"` と名前（`labels.childArea(node.label)`）を付け、失敗の文を `role="alert"` の `span` で包んだ（再試行のボタンは alert の外）。名前は呼ぶ側の文言にするため、`SharedTreeLabels` に必須の `childArea` を足し、使う2か所（roleadmin の `PermissionPanel`・mypermissions の `MyPermissionsPage`）と文言（ja「{{name}}の子」・en「Children of {{name}}」）を足した。共有の部品の型の変更で、計画の「フォーカスを受ける区画に名前（木の項目の名前を含む）」を文言の口として行った形（小さな差）。`aria-label` だけで役割の無い `div` は axe の `aria-prohibited-attr` に当たるため `role="group"` にした。
- D-9: A: `features/README.md` に「管理者でない利用者向けの項目はサイドバーに登録しない（業務は DSL のメニュー、自分向けはユーザーメニュー）」を書き、`types.ts`・`validateRegistrations.ts` のコメントに同じ決まりを書いた（検査そのものは前から `visibleWhen: 'ADMIN'` だけを通す）。決まりに反する見本（`validateRegistrations.test.ts` の3か所・`navigationItems.test.ts` の2か所・`forbiddenHeading.test.ts` の1か所の `visibleWhen: 'LOGGED_IN'` と `section: 'ADMIN'` の組）を `visibleWhen: 'ADMIN'` に直した。反する組をわざと使う1件（`rejects an item in the ADMIN section that is visible to every logged-in user`）は、題で反する見本と分かるため残した。
- R-06: `features/README.md` の `useLogout` の節に一文を足した。

### Step 23（FR6.1）

- `src/shared/slow-notice/useSlowNotice.ts` を作り（中身は3つの複製と同じ。説明のコメントだけ直した）、6つのフックの import を替え、3つの `useSlowNotice.ts` と3つのテストを消した。テストは roletransfer の版（`deferred` をテストの中に持つ）を元に1つにまとめた（`src/shared/` は機能の `testing/` を読めないため）。`no-restricted-imports` は変えていない。

### Step 24（FR3.2、D-6: A・D-7: A）

- `dsl.diff.none` を「スキーマ・テーブル・カラムに、適用中の DSL との違いはありません（メニューの違いは比べていません）」にし、違いの表の上に `dsl.diff.scope`「スキーマ・テーブル・カラムの違いを比べます。メニューの違いは比べません。」を常に出す形にした（en も）。「違いの見出しの説明」は、見出し（`dsl.preview.diffHeading`）の文言を変えずに、表の上の説明の1文で伝える形にした。
- `DslStatusPanel.tsx` に、`prunedMenuItems` が1以上のときの警告（`dsl.status.prunedTitle`・`prunedBody`。数と上限の値）を足した。上限の値は画面に定数が無いため、`MENU_DEPTH_LIMIT = 5`（サーバーの `DslFormat.MAX_MENU_DEPTH` と同じ値）を同じファイルに置いた。`api/types.ts`・`testing/fixtures.ts`・`DslAdminPage.test.tsx` の見本・E2E の `135-dsl-admin-accessibility.e2e.ts` の状態の見本に項目を足した。計画の `frontend/e2e/support/adminApiRoute.ts` には状態の見本が無かった（見本は 135 の中にあった）。

### Step 25

- 足した・直した画面のテスト: `ShellLayout.test.tsx`（トップバーの文言の ja・en、畳んだ状態の印の ja・en と axe、今の項目を含まないときは印なし。3件）、`SharedTreeView.test.tsx`（`role="alert"` と区画の名前とフォーカス。1件と既存の2件の確かめの足し）、`DslStatusPanel.test.tsx`（3件）、`DslDiffTable.test.tsx`（1件と既存の1件の足し）、`DslPreviewPanel.test.tsx`（1行）、`DslAdminPage.test.tsx`（1件）、`src/shared/slow-notice/useSlowNotice.test.tsx`。
- `WorkRoleSwitcher.test.tsx`・登録の見本のテスト・3つの機能の画面のテストは、変えずに（見本の直しを除く）通った。

### Step 26（E2E の書き換え）

- `160`: 20 組の検査の最後の状態を「畳んだサイドバーと印」にし、印（`contains-current` と読み上げの名前の「（今のページを含む）」、今の項目を含まない1段目の項目には付かない）とトップバーの「サイドバーを開く」を確かめ、axe を流す（開いた状態の axe は前の状態で流れている）。終わった後に開き直す。英語のテストに、トップバーの文言（`Collapse the sidebar`・`Menu for …`・`Expand the sidebar`）と英語の印、畳んだ状態の axe を足した。作業ロールの一覧の `expectContained` はそのまま。
- `120`・`060`: 20 組に、360px の悪い側の組 `(d) dark lg blue 360px`（`support/displayCombos.ts` の `WORST_360_COMBO`。20 組の外）を足した。120 の組のテストは表の右端の行の操作のメニューの収まり（`expectMenuInViewport`）を含むため、360px の収まりもこれで確かめる。
- `140`: 幅のテスト（360px・768px・1280px）に、表の右端の行の操作のメニューを開いて `expectContained` で収まりを確かめる手順を足した。
- 新しい流れは足していない。

### Step 27（R-05）

- `common/testsupport/ApiEndpointCounts.MIN_ENDPOINTS` を作り、2つのテストの直書きの 34 を置き換えた。今の口の数は静的な検査の `ApiAccessRules.endpointKeys` で数えて **67**（一時に大きな値を入れて落とし、失敗の文の数を読んだ）。説明文の 34 も定数の名前に替えた。

### Step 28（文書）

- `README.md`: make-you-chic-ui の固定先を `a9309c4` にし、経緯に今回の差（Dropdown の既定の `max-width`・`menuClassName`・`menuStyle`、`containsCurrent`、`topbarLabels`）と、画面の側の対応（`topbar.*` の鍵、`:has()` の上書きを外した）を書いた。E2E の表の 060・120・140・160 の行を今回の確かめに合わせた。
- `frontend/src/features/README.md`: 送信の待ちの案内（`src/shared/slow-notice/`）の節、サイドバーの区画の決まり、`useLogout` の一文、共有の木の `childArea` を足した。

### Step 29（verify と E2E）

- `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（2026-10-15 22:40:09〜22:55:33、15分23秒、`caffeinate -dims`、colima の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡した）: 成功。backend の単体 2,115 件・結合 1,168 件（失敗 0・SKIPPED 0。対象DB のテストも流れた）、frontend 184 ファイル・1,589 件。カバレッジは backend 全体 行 97.9%・分岐 92.5%、frontend 行 95.36%・分岐 90.38%。手を入れたパッケージ（行・分岐）: `dsl.domain` 100.0・96.1、`dslmanage.domain` 99.3・89.3、`dslmanage.service` 97.9・97.4、`dslmanage.web` 100.0・90.0、`navigation.domain` 100.0・92.9、`navigation.web` 95.1・100.0（すべてパッケージごとの下限の内。一覧の 7 パッケージには手を入れていない）。`osvScan` は lockfile が ① の統合の後から変わらないため UP-TO-DATE（① の verify で失敗の条件 0 件・警告 18 件）。この間の `pmset -g log` は Assertions だけで、Sleep・DarkWake は無い。
- `./gradlew e2eTest`（22:56:41〜23:07:34）: 232 件 passed・失敗 0（① の 230 件に、060・120 の 360px の組の2件が増えた）。この間の `pmset -g log` も Assertions だけ。
- Mailpit は ① と同じく、既に動いている compose の `mastersmith-mailpit-1` を使った。配備したアプリ（`mastersmith-app-1`）は止めていない。
- `frontend/test-results`・`frontend/playwright-report` は E2E の生成物（git の対象外）として残っている（消す時期は指揮役が決める）。

### Step 30（コミットの案）

- 生成の担当はコミットしない。案は返事に書いた。計画 3節の C2-1〜C2-7 の並びのとおり、各区切りで型の検査・コンパイルが通るように組んだ（C2-5 は backend と画面の型を同じコミットにする）。

### 計画に無い変更（依頼者の決定 A）: DSL のモデルの文字列を伏せる

- 依頼者の決定「A. ②で直す」により、Step 17 で見つけた `dsl.service.ActiveDslModelStore#current()` の TRACE の漏れ道を ② で直した。
- 再現: `NavigationSecretLeakIT` から `ActiveDslModelStore` のロガーの行の除外（定数・補助のメソッド・コメント）を外し、直す前に落ちることを確かめた（2026-10-15 23:11）。落ちた行は `EXIT  ActiveDslModelStore#current(): Present[model=DslModel[dslHash=…, formatVersion=2, schemas=[DslSchema[… label=DisplayName[ja=…目印…]…` で、テーブルとメニューの表示名の目印がそのまま出ていた。
- 直し: `dsl/domain/DslModel.java` の `toString` を、識別の先頭 12 文字・版・スキーマの数・最上位のメニューの数だけを出す形（`DslModel[dslHash=0123456789ab…, formatVersion=2, schemas=1, menus=2]`）に上書きした。`ActiveDsl.Present`・`DslReadResult.Valid`・`DslStartupReadResult.Valid` は中の `DslModel` の文字列を使うため、これで伏せられる（`Present`・`Valid` の `dslHash` の項目は今までどおり全体が出る。識別は秘密ではない）。
- テスト: `dsl/domain/DslModelToStringTest`（3件。名前と表示名が出ない、包む結果の型3つの文字列にも出ない、メニューが無いときの件数）を足した。`DslModel` の文字列に頼るテストは無かった（検索で `DslModel[`・`schemas=[`・`Present[model` は 0 件）。直した後に `NavigationSecretLeakIT` が除外なしで通り、dsl・dslmanage・navigation の単体が通った。
- verify の流し直し（2026-10-15 23:13:10〜23:27:31、14分20秒、`caffeinate -dims`、colima の2つの変数、`:backend:cleanTest :backend:cleanIntegrationTest`）: 成功。backend の単体 2,118 件・結合 1,168 件（失敗 0・SKIPPED 0）、frontend 1,589 件。カバレッジは backend 全体 行 97.9%・分岐 92.5%（`dsl.domain` 行 100.0%・分岐 96.1%）、frontend 行 95.36%・分岐 90.38%。`osvScan` は UP-TO-DATE（lockfile は変わらない）。この間の `pmset -g log` は Assertions だけで、Sleep・DarkWake は無い。流す前のバッテリーは 28%。画面に触れないため E2E は流し直していない（指揮役の指示）。
