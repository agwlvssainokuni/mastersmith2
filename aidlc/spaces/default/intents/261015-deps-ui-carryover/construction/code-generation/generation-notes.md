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

## ブランチ ③（Step 31〜43・45）

### Step 31

- `develop` の先頭 `60b49e5`（② の fast-forward の統合）から `fix/261015-deps-ui-carryover-3-perf` を作った。作業ツリーには、計画の Step 30 の印と、この Intent の監査ログ（`audit/sakura-local-4e42a93f87ce.md`）の未コミットの変更があり、そのまま持ってきた（生成の担当はコミットしない）。

### Step 32（配備済みの DSL の最も長い行、D-5: A）

- 依頼者の了承（「止めてよい」）のうえで行った。README の「監査ログの確かめ方」の手順で、`docker compose stop app` → ボリューム `mastersmith_mastersmith-data` を `~/.mastersmith-backup/`（権限 700）へ `tar czf`（新しい jre のイメージの中で）→ `docker compose start app` の順にし、複写を取った直後にアプリを起動し直した。
- 止めた時間: 停止の開始 2026-10-15 14:54:02 UTC → 起動の命令 14:54:07（5 秒）→ `/actuator/health` 200 は 14:54:14（停止から 12 秒）。その後の状態は `healthy`。
- 測り方: 複写を権限 700 の作業用のディレクトリに展開し、H2 2.4.240 の JDBC で `ACCESS_MODE_DATA=r`（読み取り）で開いた。リポジトリの外（スクラッチ）の小さな Java の道具で、各行の `yaml_bytes` を UTF-8 で解読し、改行（`\n`・`\r`・`\r\n`）で区切った1行のコードポイントの数の最大だけを出した（本文は表示していない）。
- 結果: `dsl_applied_revisions` 6 行（各行の最も長い行 58・58・58・58・58・58、全体の最大 58）、`dsl_previews` 0 行。上限 65,536 を大きく下回る。配備の起動で読めなくなる（`appliedUnreadable`）おそれは無い。
- 測った後に、作業用のディレクトリと複写（`mastersmith-data-202610152354-linelen.tgz`）を消した。配備の前（Deployment Execution）にもう一度測る（D-5: A、計画 6節の 8）。

### Step 33（ドメインの層: 1行の長さの上限）

- `YamlLimits` に5つ目の項目 `maxLineLength`（1 以上）を足した。`SafeYamlParser.parse` で UTF-8 の解読（先頭のバイト順の印を除いた後）の直後、SnakeYAML に渡す前に `checkLineLength` で行ごとの長さを確かめる。改行は `\n`・`\r`（`\r\n` は1つ）で、改行は数えない。サロゲートの組は1つと数える。上限を超えた最初の行で `YamlRejection`（種類 `SIZE_LIMIT`（D-3: A）、位置はその行と上限＋1 の列、場所なし、文言の鍵 `dsl.limit.line`、埋める値は上限の値だけ）にする。
- `DslFormat.MAX_LINE_LENGTH = 65_536`（D-2: A）、`DslMessageKeys.LINE_LIMIT`（`all()` にも足した）。`LoaderOptions` の値は変えていない。
- SnakeYAML は YAML 1.1 の改行（U+0085・U+2028・U+2029）も行の区切りに数えるが、計画どおり `\n`・`\r` だけで区切った。そのため、それらの文字を含む行は、この確かめでは長めに数える（厳しい側）。位置の行の番号も SnakeYAML の位置とずれうる。

### Step 34（ドメインの層のテスト）

- `SafeYamlParserTest` に5件: 65,536 ちょうどの行は読み、65,537 は 2 行目・65,537 列の `SIZE_LIMIT`・`dsl.limit.line`・埋める値 `65536`（誤りの文字列に目印が出ない）、`\r\n`・`\r` の行（改行を数えない）、全角とサロゲートの組をコードポイントで数える、長いコメントの行も読む前に最初の行で止める、バイト順の印を数えない。
- `SafeYamlParserPropertyTest` に2つの性質（jqwik。種は jqwik の報告に出る）: 上限（20）以下の行だけの文書は行の長さで拒否されない（3種類の改行）、上限を超える行を1つ含む文書は必ずその行で行の長さの拒否になる。
- 大きさの境界を1行で作るテストの直し（4.1節）: 検索の結果、行を分ける必要があったのは2つだった。
  - `role/testsupport/LargeRoleTransferYaml`（5 MiB ちょうどの権限の YAML。テストと負荷の試験の準備の両方が使う）: 末尾の埋め草のコメントを1行にしていたのを、1行 1,000 バイト（`PADDING_LINE_BYTES`）までに分けた。`LargeRoleTransferYamlTest` にすべての行が 1,000 バイト以内の確かめを足した。
  - `role/transfer/RoleTransferReaderTest` の `flowSequence`（展開後の節 500,000 の境界。流れの書き方の並びを約 100 万文字の1行にしていた）: 要素 1,000 個ごとに改行して続ける形にした。1回目の単体テストの流しで、ここが行の長さの拒否で落ちて分かった（計画 4.1節の一覧には無かったファイル）。
  - `DslAdminApiIT`・`DefaultDslReaderTest` の 10 MiB の埋め草は、前から 1,000 文字の行に分けていて、直す必要は無かった。`perf/make-large-dsl.mjs` も約 100 文字の行で影響しない。

### Step 35・36（業務処理の層・既定の DSL の生成）

- `SafeYamlLimits` に5つ目の項目 `maxLineLength`、`DefaultSafeYamlReader` で `YamlLimits` に渡す。超えたときの区分は既存の対応（`SIZE_LIMIT` → `TOO_LARGE`）で写る。
- `DefaultDslReader.DSL_LIMITS` に `DslFormat.MAX_LINE_LENGTH`、`RoleTransferLimits.MAX_LINE_LENGTH`（= `DslFormat.MAX_LINE_LENGTH`。role は dsl.domain に依存してよい境界）を `toSafeYamlLimits()` に渡した。
- `DslErrorMessages` に `dsl.limit.line` の ja「{0} 行目が1行の長さの上限（{1} 文字）を超えています。」・en「Line {0} exceeds the line length limit ({1} characters).」を足した。埋める値が上限だけのため、`message()` でこの鍵のときだけ `{0}` に誤りの位置の行、`{1}` に上限を埋める（計画の「行は誤りの位置から出す」の形）。
- D-3: A のため、権限の YAML では行の上限の拒否も `TooLarge` → 413 `PAYLOAD_TOO_LARGE` になる（本文が 5 MiB の内でも）。画面の roletransfer の文言は「大きすぎる」のまま（計画 4.1節のとおり）。
- D-4: A: `DslTreeBuilder.MAX_LABEL_CODE_POINTS = 4_096` を置き、表示名の `ja` にするコメント（制御文字を除いた後）をコードポイントで切る `truncate` を足した（サロゲートの組を分けない）。

### Step 37（業務処理の層のテスト）

- `DefaultDslReaderTest`: 投入の読み方で上限ちょうどは読み、＋1 は見本の次の行の `LINE_LIMIT` 1件。起動時の読み方も ＋1 を `DslStartupReadResult.Invalid` にする（`DslStartupLoader` はこれを `appliedUnreadable` の道で扱う。既存の道で、ローダーのテストは足していない）。
- `SafeYamlReaderTest`: 上限 8 で ちょうどは読み、9 文字の行は `TOO_LARGE`・2 行目・9 列（文字列に値が出ない）。`maxLineLength` 0 は呼ぶ側の誤り。
- `RoleTransferReaderTest`: 65,537 文字のコメントの行は `TOO_LARGE`・3 行目、65,536 は読む。
- `RoleTransferServiceTest`: 大きさの上限の内（200,000 バイト）でも、1行の長さの上限を超えれば確かめ・適用とも `TooLarge`。
- `RoleTransferWriterTest`: ロール 64 コードポイント・対象の名前 128 コードポイント（逃がす `"`・`\` を交互に含む）の書き出しが上限の内で、読み直して同じ。対象の名前 128 コードポイントを最長の逃がし（` ` → 6 文字）で書いても上限の内で、安全な読み込みで読める。
  - **計画との差**: 計画は「スキーマ・テーブル・カラム 256 文字（表の列の幅）」で確かめるとしていたが、名前の検証の上限は 128 コードポイント（`PermissionTargetName.MAX_CODE_POINTS`）で、256 文字の名前は保存できない。試しに 256 文字を最長の逃がしで書くと、約 1,540 文字のキーになり、SnakeYAML の単純なキーの長さの上限（1,024）で読めなかった（1行の長さの上限とは関係のない既存の制約）。そのため、保存できる最大の 128 コードポイントで確かめた。
- `DslTreeBuilderTest`: 既存の「長いコメントは切り詰めない（1万文字）」を、上限ちょうど（4,096）は切らない確かめに変え、1万文字の漢字と 4,097 個の絵文字のコメントが 4,096 コードポイントで切られ、生成した DSL のすべての行が上限の内であることを足した。
- `DslYamlWriterTest`: 4,096 コードポイントの、二重引用符・逆斜線・改行と書き出しで逃がす文字（U+FFFE → `￾`）を含むコメントを書き出し、最も長い行が 20,000 文字を超えて 65,536 以内で、`SafeYamlParser` で読めて表示名が元と同じ。
- `DslLifecycleTest`: 行の上限の文言が ja・en とも行と上限を埋めること。
- `SafeYamlReaderSecretLeakIT`・`TransferYamlTrees`・`DslSamples`・`SafeYamlReaderTest`・`RoleTransferLimitsApiIT` の作り方を5つの引数に直した（計画の一覧の残り（`YamlTreeConverterTest`・`DslResultTypesTest`・`ValidationTestSupport`・`GenerateTestSupport`・`DslYaml`・`RoleTransferServiceTest` など）は `DslSamples.DSL_YAML_LIMITS` などを通して使っていて、直す必要が無かった）。
- `./gradlew :backend:test`（単体の全体）が通った。

### Step 38（API の層の結合テスト）

- `DslAdminApiIT`: 上限ちょうどの行の投入は 201。＋1 は 422 `application/problem+json`・`DSL_INVALID`・`total` 1・誤り1件（`kind` `SIZE_LIMIT`、`line` は見本の次の行、`column` 65,537、文言は ja・en とも行と上限）。応答に目印・本文・`Exception`・`snakeyaml` が出ない。受け付けなかった投入の監査は理由 `SIZE_LIMIT` で、本文を読んだため識別（`dsl_hash`）が入る（本文の大きさで断ったときは識別なし。違いは既存の道のまま）。
- `RoleTransferLimitsApiIT`: 権限の YAML の確かめで、65,536 の行は 200、65,537 は 413 `PAYLOAD_TOO_LARGE`（Problem Details。目印・本文・部品の例外の文なし）。

### Step 39（BUSY の結び付き、FR10）

- 読み取りの結果: `RoleBusyLogTraceIT` は `ROLE_BUSY`（ロールの行・名前の鍵の2つの待ち）と受け渡しの入口で、`GroupBusyLogIT` は `GROUP_BUSY`（行・名前の鍵・メンバーの主キーの3つの待ち）で、応答の `code` と応答の `traceId` の WARN がちょうど1行（排他の種類と例外のクラスの名前だけ）を確かめていた。
- 足りなかった道: ロールの割り当ての API がグループの行の待ちで返す `GROUP_BUSY`（`RoleAssignmentController` の `GroupBusy`）。`RoleBusyLogTraceIT` に1件足した（別の接続でグループの行を `FOR UPDATE` で持ち続け、`POST /api/admin/roles/{id}/assignments` に `groupId` を送る → 409 `GROUP_BUSY`、応答の `traceId` の WARN がちょうど1行・`lockKind` `GROUP_ROW`・項目は決まったものだけ、ログにグループの名前と部品の文が出ない、割り当ての行は 0）。WARN は group の `StoreFailureClassifier` が1回出し、role の側は値で `Busy` を受けるため重ならない。
- `RoleBusyLogTraceIT`（3件）・`GroupBusyLogIT`（1件）が通った。k6 では上限切れを狙っていない。

### Step 40（k6 の台本、FR7.1）

- `roleAssignEnsured`（204 と 409 `ROLE_NO_CHANGE` を受け入れる）を足し、`roleAdminRead` の setup の2つ、`workRoleRead` の2つ、`myPermissionsTree` の1つ、`roleAssignmentsRead` の2つの割り当てで使った（`workRoleSwitch`・`rolePoolLimit` は今のまま `roleAssign`）。
- `workRoleRead` の確かめを「ロールの数が 2」から「setup で割り当てた2つのロールの ID が一覧に含まれる」にした。
- **計画との差（小さな足し）**: `myPermissionsTree` の setup の最後で、利用者ごとに作業ロールを悪い側のロールへ切り替える（`PUT /api/me/work-role`、204）を足した。前の場面（`workRoleSwitch`・`workRoleRead`）が保存した作業ロールが残ると、木が悪い側のロールで開かず確かめの不一致になりうるため（FR7.1 の「前の場面の状態に頼らない」に含まれると読んだ）。切り替えは使い捨ての環境の監査に `WORK_ROLE_SWITCHED` として残る。
- `k6 inspect --include-system-env-vars`（`grafana/k6:2.3.0@sha256:9c2dee7f…`、`-e SCENARIO=…`）で、`workRoleRead`・`myPermissionsTree`・`roleAssignmentsRead`・`roleAdminRead` の台本が読めた。流すのは Build and Test。

### Step 41（測りの手順、FR7.2・FR8・FR9.1・FR9.4）

- `perf/README.md` に「性能の測り直し」の節を足した: (a) ロールの書き込みの待ちの最大の合否の回を、回ごとにアプリを作り直して 5 回（`role-pool-5.sh`。回ごとの `jvm.gc.pause`・`docker stats`、回の始めと終わりの時刻、判定の読み方、20 ms 以上の回の見立てに使うログと指標）、(b) 10 MiB の DSL の投入と、1行の長さの上限の内で作る悪い形の 5 MiB の権限の YAML（流れの書き方で節 500,000 ちょうど、要素 1,000 個ごとに改行、残りを1行 1,000 バイトまでのコメント）の確かめ・適用の間のヒープを 0.5 秒ごとに読む手順と、`dslMixed` の `memory.peak`、(c) ロールの3場面を手順書の順と単独で流す手順。
- (b) の悪い形の作り方（Python の部分）は、スクラッチで実際に流し、5,242,880 バイトちょうど・最も長い行 2,008 文字になることを確かめた（作ったファイルは消した）。ほかの台本（`heap-run.sh`・`role-pool-5.sh`・`role-three.sh`）は流していない。道・指紋の見出し・ログインの応答の項目は、ソースと台本で照らした。
- ロールの管理の節の表と説明（`workRoleRead`・`myPermissionsTree`、setup の決まり）と、上限ちょうどの YAML の埋め草のコメントを行に分けたことを直した。

### Step 42（README）

- DSL の読み込みの上限の表に「1行の長さ」の行を足し、既定の DSL の生成の節に長いコメントを 4,096 文字で切ることを書いた。権限の YAML の「信頼できない入力の守り」に 1行の長さ 65,536 と、超えたときの 413 と画面の文言を足した。

### Step 43（verify）

- `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（2026-10-15 15:14:53〜15:29:43 UTC、14分49秒、`caffeinate -dims`、colima の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡した。流す前の電池 89%）: 成功。backend の単体 2,133 件・結合 1,171 件（失敗 0・SKIPPED 0。対象DB のテストも流れた）、frontend 184 ファイル・1,589 件。カバレッジは backend 全体 行 98.0%・分岐 92.6%、frontend 行 95.36%・分岐 90.38%。手を入れたパッケージ（行・分岐）: `dsl.parse` 98.4・89.2、`dsl.domain` 100.0・96.1、`dsl.service` 97.1・93.5、`dslmanage.service` 97.9・97.0、`dslmanage.generate` 99.4・100.0、`role.service` 96.9・91.1（すべてパッケージごとの下限の内。一覧の 7 パッケージには手を入れていない）。`osvScan` は UP-TO-DATE（lockfile は ② から変わらない）。
- この間の `pmset -g log` に Sleep・DarkWake は無い。
- 画面のコードに触れていないため E2E は流していない（計画 1節）。

### Step 45

- `code-summary.md` に ③ の節と、①〜③ の全体の節を書き足した。`source-manifest.json` は `cf6d5de`（① の前の `develop`）から作業ツリーまでの `aidlc/` の外の変更 118 パス（消した4つと名前を変えた2つの元の道を含む）。`traceability.json` は要件の FR1.1〜FR12.3・NFR1〜NFR6 の 44 件（OK 28・Deferred 13・N/A 3）。

### Step 12（push の後のプルリクエスト、D-12: A。2026-10-16）

- 依頼者の push で `origin/develop` が `60b49e5` に届いたことを確かめた。
- 取り込んだ 19 件のうち 15 件（#49・#48・#45・#44・#43・#42・#41・#40・#38・#37・#36・#35・#27・#25・#24）は Dependabot が自動で閉じた。見送った #39（axe-core）も閉じた。
- 開いたまま残った 4 件（#47 Spotless 8.10.4・#46 gradle-wrapper 9.8.1・#34 stylelint 17.16.0・#32 eslint 10.12.0）は `develop` に同じ版で取り込み済みと確かめ、`gh pr close` のコマンドを依頼者に示した（閉じるのは依頼者）。

### 依頼者の決定（ブランチ ③ の後、計画に無い変更）

- push の後に届いた Dependabot の知らせ 4 件（#53 prettier 3.9.10・#52 vitest 5.0.4・#51 @vitest/coverage-v8 5.0.4・#50 @fontsource/noto-sans-jp 5.3.1）も、この Intent で取り込む（依頼者「#50〜#53 もこのintentで対応して欲しい」）。ブランチ ③ の統合の後に、依存の更新だけの短いブランチ ④（`fix/261015-deps-ui-carryover-4-deps`、squash）で行う。
- E2E は Intent の最後（Build and Test）に1回だけ流し、ブランチごとの統合の前は `./gradlew verify` だけにする（依頼者「E2Eはintentの最後に実行するので良いかな。都度実行は./gradlew verifyだけにして。」）。`team.md` の「画面・認証に関わる変更は統合の前に E2E」との差として記録する。① と ② は統合の前に E2E を流して通っている。`@fontsource/noto-sans-jp` は画面の書体のため、配備の後のスモークテストで文字の表示を確かめる。

## ブランチ ④（計画に無い追加、依頼者の決定。Dependabot の知らせ 4 件）

### 作り方と取り込んだ版

- `develop` の先頭 `0b04d3a` から `fix/261015-deps-ui-carryover-4-deps` を作った（`aidlc/` の未コミットの記録はそのまま持ってきた）。計画のファイルは変えていない。
- 4 件を各プルリクエストと同じ版・同じ範囲の書き方で入れた（`npm install --save-dev prettier@3.9.10 vitest@5.0.4 @vitest/coverage-v8@5.0.4`、`npm install --save @fontsource/noto-sans-jp@5.3.1`。`frontend/.npmrc` の `ignore-scripts=true` のまま）。直接の依存は #50〜#53 の差分の版と一致し、npm が範囲の中のもっと新しい版へ上げたものは無かった（入れ直しは不要）。
  - #53 `prettier` 3.9.9 → 3.9.10、#52 `vitest` 5.0.3 → 5.0.4、#51 `@vitest/coverage-v8` 5.0.3 → 5.0.4（`vitest` と同じ版にそろう）、#50 `@fontsource/noto-sans-jp` 5.3.0 → 5.3.1（実行時の依存）。
  - lockfile の推移依存の変化: `@vitest/mocker`・`@vitest/spy` 5.0.3 → 5.0.4、`obug` 2.2.1 → 3.0.0（`vitest`・`@vitest/coverage-v8` の依存。開発時だけ）。ほかは変わらない。
- `npm install` は前と同じ `npm audit` の High 7 件（`braces` の1件の勧告の連なり、`stylelint` 経由の開発時だけの依存）を表示した。今回の更新で増えたものではない。
- prettier 3.9.10 の書式の検査（`npm run format:check`）は全ファイルが通り、書式の直しは無い。`vendor/` は変えていない。

### verify（統合の前の関門）: 1件の失敗で止まった

- `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（2026-10-15 15:35:46〜15:49:48 UTC、14分1秒、`caffeinate -dims`、colima の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡した。流す前の電池 85%）: **失敗**。
  - 書式・リンタ・ライセンスヘッダー・画面のビルドと型の検査は通った。frontend 184 ファイル・1,589 件は通った（vitest 5.0.4 で流れた）。backend の単体 2,133 件は通った（失敗 0・SKIPPED 0）。
  - backend の結合 1,171 件のうち 1 件が失敗（SKIPPED 0。対象DB のテストも流れた）: `invitation.web.InvitedPersonAuthenticationIT` の「logging in with the invited email is the same 401 as an unknown user」。`setUp` の `UPDATE invitations SET invitation_id = ? WHERE email = ?` が主キーの重なり（`DuplicateKeyException`）で落ちた。
  - 結合テストで止まったため、その後のカバレッジの下限の検証とセキュリティ検査は verify の中では流れていない。カバレッジは今回測れていない。
- 見立て（コードの読み取り。直していない）: このテストは各テストの前に招待を1件作り、その ID を `910_000 + Math.abs(email.hashCode() % 1000)`（email は毎回乱数の UUID）に書き換える。3 つのテストが同じ Spring の文脈（同じ内部DB）で招待を作り、消さないため、3 件の ID が 1,000 の枠の中で重なると落ちる（1回の実行で約 0.3%）。今回の変更は `frontend/` の依存だけで backend に触れておらず、更新とは関わらない、前からある不安定なテストと見る。
- 扱い: `team.md` の決まり（手元で再現した不安定なテストは、原因を直すまで統合しない）に当たる。直しはテストだけで済む見込み（例: ID の書き換えをやめる、重ならない値を選ぶ）だが、依頼の範囲（依存の更新）の外のため、生成の担当は直さず、指揮役・依頼者に諮る。
- OSV-Scanner: verify の中では流れなかったため、`./gradlew osvScan` だけを流した。失敗の条件 0 件・警告 18 件（`frontend` の `braces` 1件と `vendor/make-you-chic-ui` の lockfile の開発用 17 件。① と同じ顔ぶれ）。
- この間の `pmset -g log` に Sleep・DarkWake は無い（Chrome の画面の眠りの防止の出入りだけ）。
- E2E は流していない（依頼者の決定。Intent の最後に1回流す）。
- コミットはしていない。見送った更新は無い。

### 計画に無い変更（依頼者の決定 A）: InvitedPersonAuthenticationIT の招待の ID の重なりを ④ で直す

- 再現のテスト: 招待の ID の選び方をテストの手伝い `backend/src/test/java/cherry/mastersmith/invitation/testsupport/InvitedPersonIds.java`（`reassign`）に移し、まず前と同じ式（`910_000 + Math.abs(email.hashCode() % 1000)`）のまま、再現の結合テスト `InvitedPersonIdsIT.java`（同じ置き場）を書いた。前の式で同じ枠に入る2つのメールアドレスを乱数に頼らず探し（`same-slot-<n>@example.com` の最初の組）、2件の招待を入れてから両方を書き換える。直す前に流すと、2件目の書き換えが `verify` と同じ `DuplicateKeyException`（主キー key 910324）で落ちることを確かめた（2026-10-15 16:14 UTC ごろ、2件中1件失敗）。
- 直し: `InvitedPersonIds.reassign` の ID を、下限 910,000・内部DB の招待の ID の最大＋1・利用者の ID の最大＋1 のうち最も大きい値にした（1つの SQL の `GREATEST`）。`InvitedPersonAuthenticationIT` は `setUp` でこの手伝いを呼ぶ形にし、定数 `INVITATION_ID` を消した。招待の ID が利用者の ID と重ならないこと（`accessTokensAreRefused` が前提とする）と、3つのテストの意図（応答から存在を推測できない・トークンの拒否）は変えていない。`src/main` は変えていない。再現のテストには「書き換えた ID がどの利用者の ID でもない」も足した。
- 同じ形の検索（`hashCode() %`・`% 1000`・`Math.abs(` を backend の test と frontend で）: ID をハッシュ値で作るのはこの1か所だけ。似た形が2つあるが同じ原因ではないため直していない。
  - `invitation/service/InvitationSendResultIT.java` 135 行: `token_hash` を `Math.abs(email.hashCode())` の 16 進で作る。一意の制約はあるが、1回の実行で1件だけ入れ、32 ビットの値のため重なりは実用上起きない。
  - `mail/template/MailTemplateRegistryTest.java` 175 行: 言語の選び分け（`% 2`）で、一意の値ではない。
- 単独の繰り返し: `InvitedPersonAuthenticationIT` と `InvitedPersonIdsIT` を `--rerun` で 10 回流した（16:14:41〜16:16:36 UTC、`caffeinate -dims`）。10 回とも 5 件が通った（失敗 0・SKIPPED 0）。
- verify の流し直し（`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`、16:16:43〜16:30:47 UTC、14分4秒、`caffeinate -dims`、colima の2つの変数、電池 75%）: backend の単体 2,133 件・結合 1,173 件（足した2件を含む）は失敗 0・SKIPPED 0 で通った。frontend の `frontendTest`（1,589 件）も通った。ところが、その後の `frontendCoverage`（同じテストをカバレッジ付きでもう一度流す）で、`src/app/navigation/BusinessNavigationProvider.test.tsx` の「drops the tree and the saved expanded ids when logged out」が1件落ちた（`sessionStorage` の開閉の記録が null でなく `["biz:0"]`）。
  - 見立て（コードの読み取り。直していない）: 状態の `idle` は描画の中で決まり、開閉の記録の消去（`clearExpanded`）は描画の後の効果（`useEffect`）で動く。テストは `waitFor` で `idle` を待った後、記録の消去を同期で確かめているため、効果の前に確かめると落ちる。`team.md` の「描画の後に反映される値は `waitFor` で待つ」に当たる形。今回の依存の更新（vitest 5.0.4 など）が時間の取り方を変えたかは確かめていない。前の Intent の 0415b94・005730a から同じテストで、①〜③ の verify では落ちていない。
  - 再現の試み（上限 30 分のうち約 4 分）: そのファイルをカバレッジ付きで単独 15 回、frontend 全体をカバレッジ付きで 3 回流し、すべて通った（再現しない）。
  - 直しの案（テストだけ）: 203 行の確かめを `await waitFor(() => expect(sessionStorage.getItem(NAV_EXPANDED_STORAGE_KEY)).toBeNull())` にする。依頼の範囲の外のため、生成の担当は直さず、指揮役・依頼者に諮る。
- 残りの関門: 上の verify は `frontendCoverage` で止まったため、続けて `./gradlew verify`（clean なし、16:35:42〜16:38:14 UTC）を流した。backend のテストは直前の clean の実行の結果で UP-TO-DATE、frontend のテストとカバレッジは流し直して 1,589 件が通り、カバレッジの下限・Gitleaks（漏えい無し）・SpotBugs の関門・OSV-Scanner（UP-TO-DATE。lockfile は単独で流した時と同じで、失敗の条件 0・警告 18）が通った（BUILD SUCCESSFUL）。
  - カバレッジ（JaCoCo は clean の実行の結果から）: backend 全体 行 98.0%・分岐 92.6%。frontend 行 95.36%・分岐 90.38%。`src/main` に手を入れたパッケージは無い（テストだけの変更）。招待のパッケージは参考で `invitation.web` 行 97.3%・分岐 87.0%、`invitation.service` 行 100.0%・分岐 93.5%。
- この間の `pmset -g log` に Sleep・DarkWake は無い。E2E は流していない（依頼者の決定）。

### 計画に無い変更（依頼者の決定 A）: BusinessNavigationProvider のテストの待ち方を ④ で直す

- 直し: `frontend/src/app/navigation/BusinessNavigationProvider.test.tsx` の「drops the tree and the saved expanded ids when logged out」の 203 行（ログアウトの後の開閉の記録の消去の確かめ）を、`await waitFor(() => expect(sessionStorage.getItem(NAV_EXPANDED_STORAGE_KEY)).toBeNull())` で待って確かめる形にした。テストの時間の上限は変えていない。
- 近いテストの確かめ（開閉の記録 `NAV_EXPANDED_STORAGE_KEY` を確かめる箇所を検索）: 同じ原因の形はほかに無く、直していない。
  - `features/auth/LogoutPage.test.tsx` 79 行: 記録の消去は `logout()` と同じ効果の中で `logout()` より先に動き、`waitFor` で `logout` の呼び出しを待った後に確かめるため、効果の後になる。88 行は変わらないことの確かめ。
  - `app/layout/ShellLayout.test.tsx` 667 行: 記録の書き込み（効果）は1回目の `user.click` の中で動き、その後に別の操作と `waitFor` を挟んで確かめる。698・709 行は初めの値が変わらないことの確かめ、712 行以降は既に `waitFor` で待っている。
  - `navExpansion.test.ts` は関数を直接呼ぶ単体テストで、効果を挟まない。
- 繰り返し: そのファイルをカバレッジ付きで単独 10 回流し、10 回とも通った（16:59:18〜16:59:45 UTC）。
- verify の流し直し（`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`、16:59:54〜17:14:21 UTC、14分26秒、`caffeinate -dims`、colima の2つの変数、電池 63%）: **成功**。backend の単体 2,133 件・結合 1,173 件（失敗 0・SKIPPED 0。対象DB のテストも流れた）、frontend 184 ファイル・1,589 件（`frontendTest` と `frontendCoverage` の両方）。カバレッジは backend 全体 行 98.0%・分岐 92.6%、frontend 行 95.36%・分岐 90.36%（下限の内）。Gitleaks は漏えい無し、SpotBugs の関門は通過、OSV-Scanner は UP-TO-DATE（lockfile は単独で流した時と同じで、失敗の条件 0・警告 18）。`src/main` に手を入れたパッケージは無い（④ はテストと依存だけ）。
- この間の `pmset -g log` に Sleep・DarkWake は無い。E2E は流していない（依頼者の決定）。これを ④ の統合の前の確かめの結果とする。
