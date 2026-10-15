# コード生成の計画（261015-deps-ui-carryover）

- 範囲: bugfix（深さ Minimal、Test Strategy Minimal）。単位の分割が無い（zero-Unit）ため、成果物は `aidlc/spaces/default/intents/261015-deps-ui-carryover/construction/code-generation/` に置く。Bolt・単位の手続きは無い。
- 正とする入力: `aidlc/spaces/default/intents/261015-deps-ui-carryover/inception/requirements-analysis/requirements.md`（FR1〜FR12、NFR1〜NFR6、制約の3つのブランチの順、前提 A1〜A4）。
- 読んだ時点: `develop` の HEAD `c85f513`。読み取り（Read・grep・`git log`・`git -C vendor/make-you-chic-ui show a9309c4`）だけで確かめた。コード・テスト・設定・サブモジュール・git の状態は変えていない。
- 方法: Testing Contract（末尾）の test-after。層（ドメイン → 業務処理 → API → 画面部品 → 設定 → 文書）ごとに実装を書き、同じブランチの中でその層のテストを書いて流し、通ってから次の層へ進む。不具合の直し（FR3.1・FR4.1）は、決まり（`project.md` の Mandated）に従い、再現のテストを直しと同じコミットに入れる。再現のテストは、直す前のコードで落ちることを一度確かめてから直す（方法を TDD に変えるものではなく、bugfix の床の再現の確かめ）。

## 1. 全体の進め方

| ブランチ | 中身 | 要件 | 統合の形 | ブランチ名の案（統合の後に消す） |
|---|---|---|---|---|
| ① 依存の更新 | Dependabot の 20 件、Dependabot が見ない固定先のそろえ | FR1、NFR1・NFR6 | `develop` へ squash（1コミット） | `fix/261015-deps-ui-carryover-1-deps` |
| ② 画面・コードの小さな直し | make-you-chic-ui の `a9309c4` の取り込み、K1〜K5、FR12 の一覧の直し | FR2〜FR6・FR12、NFR1・NFR2・NFR4・NFR5 | `develop` へ fast-forward（固定先の更新の専用のコミットを残す。`team.md` の例外） | `fix/261015-deps-ui-carryover-2-ui` |
| ③ k6 の台本と性能の準備 | k6 の setup の独立、1行の長さの上限、BUSY の結合テストの確かめ、測りの手順 | FR7〜FR10、NFR1〜NFR4 | `develop` へ squash（1コミット） | `fix/261015-deps-ui-carryover-3-perf` |

- 順は ① → ② → ③。②・③ は ① を統合した `develop` から作る。各ブランチの統合の前に、colima を起動し `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`（README の値）を渡して、`caffeinate -dims` で包んだ `./gradlew verify` を通す（NFR1）。対象DB のテストが SKIPPED のときは統合しない。流した後に `pmset -g log` で Sleep・DarkWake が無いことを確かめ、入っていたら流し直す（`project.md` の学び）。
- 画面に関わるブランチ（①は vite・vitest など画面のビルドの道具を上げる、②は画面の直し）は、統合の前に `./gradlew e2eTest` を手元で流して通す（FR1.4・NFR5）。③ は画面のコードに触れない（DSL の誤りの文言はサーバーが組み立てる）ため E2E は流さない。
- squash の統合の前に、監査ログを含む `aidlc/` の未コミットの変更を先にコミットしておく（`project.md` の学び。`git restore --source=HEAD -- aidlc/` は使わない）。
- コード生成の段の承認の場は、最後のコードのコミットの後、記録だけのコミット（`aidlc/` の下だけ）を積む前に開く（`project.md` の学び）。段の前に `frontend/playwright-report`・`frontend/test-results` など git の対象外の生成物を消しておく。
- コミットは、実行の前に必ず依頼者に提案して承認を得る。`origin` への `git push` は依頼者が行う。生成の担当はコミットの案（3節）を作るだけで、コミットは承認の後に行う。
- FR8・FR9.1・FR9.3・FR9.4 の k6 と測定は、Performance Validation の段が無いため Build and Test で流す（`project.md` の学び）。この段では台本と手順の準備まで（6節）。

## 2. 手順（Step）

### ブランチ ① 依存の更新（FR1）

- [x] **Step 1**（FR1・NFR1）`aidlc/` の未コミットの変更が無いことを確かめ（あれば先にコミットを提案）、`develop` から `fix/261015-deps-ui-carryover-1-deps` を作る。
- [x] **Step 2**（FR1.2・FR1.2a・FR1.2c）読み取りだけで版とダイジェストを確かめ、表にして `code-summary.md` に記録する。
  - `docker buildx imagetools inspect` で、`eclipse-temurin:25.0.4.1_1-jre-noble`、`grafana/otel-lgtm:0.36.0`、`axllent/mailpit:v1.31.5` の index のダイジェストと、Manifests に linux/arm64・linux/amd64 があることを確かめる。
  - FR1.2a: `eclipse-temurin:25.0.4.1_1-jdk-noble` があるかを同じ形で確かめる。無ければ前提 A1 のとおり jdk の固定先（`docker/hikari-pool.sh` 48 行・`perf/README.md` 76 行・`README.md` 392 行）は `25.0.4_7-jdk-noble` のまま残し、理由と次にそろえる時期を `.github/dependabot.yml` のコメントと `code-summary.md` に書く。
  - FR1.2c: `grafana/k6` の、その時点で公開されている最新の版（タグの一覧で確かめる）と index のダイジェスト。
  - Actions の4件（`actions/setup-node` v7.1.0・`gradle/actions/setup-gradle` v6.4.0・`actions/upload-artifact` v7.0.2・`actions/download-artifact` v8.0.2。版は各プルリクエストの版）のタグが指すコミットのハッシュ（`gh api` の読み取り）。
- [x] **Step 3**（FR1.1）Gradle の更新: `gradle/libs.versions.toml` の `spotless` を 8.10.4、`jqwik` を 1.10.2 にする。wrapper は `./gradlew wrapper --gradle-version 9.8.1` で上げ（`gradle-wrapper.properties`・`gradlew`・`gradlew.bat`・`gradle-wrapper.jar` のうち変わるもの）、もう一度 `./gradlew wrapper` を流して wrapper 自身の差を落ち着かせる。lockfile は `./gradlew :backend:resolveAndLockAll --write-locks` で書き直し、`backend/gradle.lockfile`・`settings-gradle.lockfile` の差が今回上げたものとその推移依存だけであることを確かめる。
- [x] **Step 4**（FR1.1・NFR6）npm の更新（`frontend/`）: 10 件（`@vitejs/plugin-react`・`@typescript-eslint/parser`・`oxlint`・`@types/node`・`vite`・`axe-core`・`stylelint`・`eslint`・`@vitest/coverage-v8`・`vitest`）を各プルリクエストの版で `package.json` に書き、`npm install` で `package-lock.json` を書き直す（`frontend/.npmrc` の `ignore-scripts=true` のまま）。`vitest` と `@vitest/coverage-v8` は同じ版にそろえる。`vite`・`@vitejs/plugin-react` は `config/npm-build-tools.txt` の成果物を作る道具で、開発時の依存でも High 以上で止まることを OSV-Scanner で確かめる。
- [x] **Step 5**（FR1.1）Actions の更新: `.github/workflows/ci.yml` の4つの `uses:` のハッシュと末尾のコメントの版を Step 2 の値に替える。
- [x] **Step 6**（FR1.1・FR1.2・FR1.2b）イメージの更新（Step 2 の版とダイジェスト）。替える箇所の一覧は4.1節。
  - `Dockerfile` の eclipse-temurin の jre。
  - `compose.yaml` の `grafana/otel-lgtm`・`axllent/mailpit`、`docker/perf/compose.yaml` の `axllent/mailpit`。
  - `perf/README.md` の jre（5 か所）・otel-lgtm（1 か所）・k6（11 か所。FR1.2c）、jdk（Step 2 の結果による）。
  - `README.md` の jre（4 か所）、mailpit（552 行。ダイジェストを足して版を v1.31.5 にそろえる。FR1.2b）、jdk（Step 2 の結果による）。
  - `docker/hikari-pool.sh` の `JDK_IMAGE`（Step 2 の結果による）。
- [x] **Step 7**（FR1.2d）`.github/dependabot.yml` の「ダイジェストでのイメージの固定」のコメントを、実際の固定先と一致させる。書いてある版の文字（`25.0.4_7-jre-noble`）を新しい版にし、「Dependabot が見ない固定先」に `README.md` の mailpit を足し、`grafana/k6` は知らせが来ない固定先であること（上げる時期は Intent の依存の更新のとき）を書く。jdk を据え置いたときは、その理由と時期を書く。
- [x] **Step 8**（FR1.2・FR1.2d）残りの確かめ: 古い版の文字（`25.0.4_7-jre`、`otel-lgtm:0.34.0`、`mailpit:v1.31.2`、`grafana/k6:2.3.0`、古いダイジェストの先頭 12 文字）を `grep -rn`（`vendor/`・`node_modules`・`build`・`aidlc/` を除く）で探し、据え置いたもの以外が残らないことを確かめて結果を記録する。
- [x] **Step 9**（FR1.4・NFR1）D-13 の答えにより、新しい jre のイメージで `docker build` し、使い捨ての名前・一時のボリュームで起動して `/actuator/health` の 200 を待つ（イメージに HEALTHCHECK が無いため bash の `/dev/tcp` で待つ。`project.md` の学び）。確かめた後にコンテナ・ボリュームを消す。配備したアプリは止めない（VM のメモリの空きを先に確かめ、足りなければ依頼者に諮る）。
- [x] **Step 10**（FR1.3・FR1.4・NFR1・NFR6）`./gradlew verify` と `./gradlew e2eTest` を流す。通らない更新は、原因を確かめ、FR1.3 に従ってその件だけ見送る（`.github/dependabot.yml` の `ignore` に理由と外す時期を書き、版を戻す）。重大度 High 以上の脆弱性の直しを含む更新は見送らずに直す。見送りが出たときは、その場で依頼者に伝える。
- [x] **Step 11**（FR1）コミットの案（3節の C1-1〜C1-5）を依頼者に提案し、承認の後にコミットする。`develop` へ squash で統合（件名の案: 「Dependabot の知らせ 20 件を取り込み、見ない固定先のイメージをそろえる」）し、作業ブランチを消す。
- [x] **Step 12**（FR1.1・FR1.4）依頼者の push の後、`gh pr list` で 20 件の状態を確かめ、D-12 の答えに従ってプルリクエストを閉じる（取り込んだ件と見送った件の一覧と理由を `code-summary.md` に記録）。統合の後の CI の確かめは Build and Test に引き継ぐ（6節）。

### ブランチ ② make-you-chic-ui の取り込みと、画面・コードの小さな直し（FR2〜FR6・FR12）

- [x] **Step 13**（NFR1）① を統合した `develop` から `fix/261015-deps-ui-carryover-2-ui` を作る。
- [x] **Step 14**（FR2.3・A2）固定先の更新の前の準備（D-1 の A）: `frontend/src/app/layout/ShellLayout.tsx` の `navLabels` を、型の注記の無いオブジェクトとして作ってから渡す形にし、`containsCurrent` の文言（`nav.containsCurrent`。ja「{{label}}（今のページを含む）」・en「{{label}} (contains the current page)」の案）を足す。今の `5bf1ffe` の `SidebarLabels` にも、`a9309c4` の `SidebarLabels`（`containsCurrent` が必須）にも合う形にして、このコミットの時点で型の検査が通ることを確かめる。`frontend/src/app/i18n/messages/ja.ts`・`en.ts` に鍵を足す。
- [x] **Step 15**（FR2.1）固定先の更新の専用のコミット: `vendor/make-you-chic-ui` を `5bf1ffe` から `a9309c4` に上げる（中身は変えない）。`vendorInstall`・`vendorBuild` を通し、型の検査と画面のビルドが通ることを確かめる。更新の前後のハッシュ（`5bf1ffe…` → `a9309c4ee863b162236571a7d6300c62fd4b4f70`）を `code-summary.md` に記録する。`a9309c4` は package の版の定義と lockfile を変えない（`git show --stat` で確かめた）。
- [x] **Step 16**（FR3.1・NFR4）ドメインの層（dsl）: 再現のテスト `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslSecretLeakIT.java` を書き（TRACE を有効にして、目印の値を持つ誤りのある DSL を投入の API に送り、アプリのログに目印が出ないことを確かめる。`SafeYamlReaderSecretLeakIT` と同じ形）、直す前のコードで落ちることを確かめる。そのうえで `dsl/domain/DslError.java` と `dslmanage/domain/DslErrorItem.java`（D-11）の `toString` を、種類・行・列・文言の鍵と埋める値の件数だけを出す形に上書きする（`path` と埋める値・組み立てた文言は出さない）。`DslReadResult.Invalid`・`DslStartupReadResult.Invalid` は中の `DslError` の `toString` を使うため、これで伏せられる。
- [x] **Step 17**（FR3.1・FR4.1・FR4.2・NFR4）ドメインの層（navigation）: 再現のテスト `backend/src/test/java/cherry/mastersmith/navigation/web/NavigationSecretLeakIT.java`（TRACE で `GET /api/me/navigation`・`GET /api/me/table-access` を呼び、DSL の表示名の目印がログに出ないこと）を書き、直す前に落ちることを確かめてから、`DisplayLabel`・`NavTableRef`・`TableAccessResult.Visible`（domain）と `DisplayLabelResponse`・`NavTableRefResponse`・`TableAccessResponse`（web）の `toString` を上書きする（D-10）。`MenuFilter` の Javadoc に、深さの前提（`DslFormat.MAX_MENU_DEPTH` = 5 の検証を通ったモデルだけが来ること、境界のテストの持ち主は dsl であること）を書く。
- [x] **Step 18**（FR3.1・FR4.1）ドメインの層のテストを流す: Step 16・17 の再現のテストと、`DslResultTypesTest`・`DslManageDomainTest`・`NavNodeTest`・`MenuFilterTest`、`toString` の単体の確かめ（`DslErrorTest`・`NavigationRecordsToStringTest` を足す。値が出ず件数と種類が出る）。
- [x] **Step 19**（FR3.2・R-08、D-7 の A のとき）業務処理と API の層: `DslStatus` に「起動時に落としたメニューの項目の数」（`prunedMenuItems`）を足し、`DslStartupLoader` が落とした数を `appliedUnreadable` と同じ形で持ち、状態の応答（`dslmanage/web/DslResponses.java`）に載せる。項目を足すだけで、既存の項目は変えない。
- [x] **Step 20**（FR3.2・FR3.3）業務処理と API の層のテスト: `DslStartupLoaderTest`・`DslStartupIT` に「深すぎる枝を落として起動したら状態の応答に数が出る、落とさなければ 0」を足す。`DslAdminApiIT` の状態の応答の確かめを新しい項目に合わせる。
- [x] **Step 21**（FR2.2・FR2.4・FR2.5）画面の層（骨組み）: `ShellLayout.tsx` で `AppShell` に `topbarLabels`（`topbar.expandSidebar`・`topbar.collapseSidebar`・`topbar.userMenu` の鍵を ja・en に足す）を渡す。`frontend/src/app/work-role/WorkRoleSwitcher.css` の `:has()` での上書き（71 行の `@media` の塊と説明のコメント）を外し、make-you-chic-ui の既定の `max-width` に任せる。表の右端の Dropdown（`UserRowActions`・`GroupRowActions`・`RoleRowActions`、`placement="bottom-end"`）は既定で画面に収まるため、`menuClassName`・`menuStyle` は使わない（使う場面が出たら記録する）。`InvitationList.css` の `:has()` は Dropdown の上書きではないため変えない。
- [x] **Step 22**（FR5.1・FR5.2、FR12.1 の K4）画面の層（共有の部品・登録）: `frontend/src/shared/tree/SharedTreeNodeItem.tsx` の読み込みの失敗の文を `role="alert"` にし、フォーカスを受ける区画に名前（`aria-label`、木の項目の名前を含む）を付ける。`frontend/src/app/registry/types.ts`・`validateRegistrations.ts` と `features/README.md` は D-9 の答えのとおりに直し、決まりに反する組（`visibleWhen: 'LOGGED_IN'` と `section: 'ADMIN'`）のテストの見本を、決まりに合う組か、反する見本と分かる名前にする。`features/README.md` の `useLogout` の節に、呼ぶ側が API の失敗と成功を区別しないこと（`LoginStateGate` が失敗でも状態を替える契約で、`LoginStateGate.test.tsx` が確かめていること）を一文書く（R-06）。
- [x] **Step 23**（FR6.1）画面の層（共有のフック）: `frontend/src/shared/slow-notice/useSlowNotice.ts` を作り（3つの複製と同じ中身。説明のコメントだけ直す）、groupadmin（`useGroupDetail.ts`・`useGroupList.ts`）・roleadmin（`useRoleAssignments.ts`・`useRolePermissions.ts`・`useRoleList.ts`）・roletransfer（`useRoleTransfer.ts`）の import を替え、3つの `useSlowNotice.ts` を消す。`no-restricted-imports`（機能どうしの import の禁止）は変えない。
- [x] **Step 24**（FR3.2・R-07・R-08）画面の層（DSL）: D-6 の A のとき、`frontend/src/features/dsl/messages.ts` の `dsl.diff.none` と違いの見出しの説明を「スキーマ・テーブル・カラムの違いを比べる。メニューの違いは比べない」が分かる文言にする（ja・en）。D-7 の A のとき、`DslStatusPanel.tsx` に、落とした項目が1以上なら警告（数と上限の値）を出し、`api/types.ts`・`testing/fixtures.ts`・`frontend/e2e/support/adminApiRoute.ts` の状態の見本に新しい項目を足す（E2E の見本は本物の応答と項目の名前・型を一致させる。`project.md` の学び）。
- [x] **Step 25**（FR2.5・FR3.3・FR5.3・FR6.2）画面の層のテストを流す:
  - `ShellLayout.test.tsx`: トップバーの文言が ja・en で出し分けられる、畳んだ状態で今の項目を子孫に持つ1段目のまとまりの名前に `containsCurrent` の文言が付く、axe。
  - `WorkRoleSwitcher.test.tsx`: 外した上書きに頼らずに今までどおり動く。
  - `SharedTreeView.test.tsx`: 失敗の文が `role="alert"` で出る、フォーカスの区画の名前、axe（失敗の状態）。描画の後に出る値は `waitFor` で待つ。
  - `validateRegistrations.test.ts`・`navigationItems.test.ts`・`forbiddenHeading.test.ts`（見本の直し）。
  - `frontend/src/shared/slow-notice/useSlowNotice.test.tsx`（3つのテストを1つにまとめる）と、3つの機能の画面のテスト。
  - `DslDiffTable.test.tsx`・`DslPreviewPanel.test.tsx`（文言）、`DslStatusPanel.test.tsx`・`DslAdminPage.test.tsx`（警告）。
- [x] **Step 26**（FR2.5・FR3.2・NFR5）E2E の書き換え（新しい流れは足さない。既存の書き換えは本数に数えない）:
  - `160-app-frame-accessibility.e2e.ts`: 英語の表示でトップバーの文言が英語になる。畳んだ状態で今の項目を含む1段目のまとまりに印が出る（クラス `contains-current` と読み上げの名前）。axe は、ブランドカラーとテーマのすべての組で、畳んだ状態（印が出ている）と開いた状態の両方を見る。作業ロールの一覧の `expectContained` は上書きを外した後も通る。
  - `120-user-admin-accessibility.e2e.ts`・`140-role-admin-accessibility.e2e.ts`: 表の右端の行の操作の Dropdown を開き、開いたメニューの矩形が画面の中に収まる（360px を含む）。
  - `060-invitation-accessibility.e2e.ts`・`120-user-admin-accessibility.e2e.ts`: 360px の幅で画面全体の横のはみ出しが無い（R-04）。はみ出しが見つかり小さな直しで済まないときは、FR12.3 に従って範囲の外へ出す案を依頼者に諮る。
  - D-7 の A のとき、`040-dsl-admin.e2e.ts` か `135-dsl-admin-accessibility.e2e.ts` の状態の見本が新しい項目を含んでも通る。
- [x] **Step 27**（FR12.1・FR3.2・FR5.2）バックエンドのテストだけの直し（R-05）: `ApiAccessArchitectureTest` と `ApiAccessConsistencyIT` に直書きの下限 `MIN_ENDPOINTS = 34` を、`backend/src/test/java/cherry/mastersmith/common/testsupport/ApiEndpointCounts.java` の1つの定数にまとめ、今の口の数（生成の時に数える）に上げる。説明文（`@DisplayName` の 34）も定数に合わせる。
- [x] **Step 28**（文書）`README.md` の E2E の表（160・120・140・060 の確かめの中身）と、make-you-chic-ui の固定先の記述（`5bf1ffe` → `a9309c4`）、トップバーの文言の口、作業ロールの一覧の上書きを外したことを直す。`features/README.md` に `shared/slow-notice` を足す。
- [x] **Step 29**（NFR1・NFR2・NFR5）`./gradlew verify` と `./gradlew e2eTest` を流す。カバレッジは `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、手を入れたパッケージ（4.3節）の値を記録する。
- [x] **Step 30**（FR2〜FR6）コミットの案（3節の C2-1〜C2-7）を依頼者に提案し、承認の後にコミットする。fast-forward で統合するため、各コミットの時点で型の検査（コンパイル）が通ることをコミットの前に確かめる。`develop` へ fast-forward で統合し、作業ブランチを消す。

### ブランチ ③ k6 の台本と性能の準備（FR7〜FR10）

- [x] **Step 31**（NFR1）② を統合した `develop` から `fix/261015-deps-ui-carryover-3-perf` を作る。
- [x] **Step 32**（FR9.2）配備済みの内部DB の DSL の最も長い行の測り（D-5 の A）。依頼者に、配備したアプリを止めること（数秒〜数十秒）を伝えて了承を得てから、README の手順（アプリを止めて内部DB を複写し、`ACCESS_MODE_DATA=r` で開く）で、`dsl_applied_revisions` と `dsl_previews` のすべての行の `yaml_bytes` について、行ごとの最も長い行の長さ（コードポイント）だけを出す（本文は表示しない）。複写はホームの下（権限 700）に置き、終わったら消す。アプリを起動し直し、止めた時間を記録する。最も長い行が D-2 の値を超えるときは、そこで止めて依頼者に諮る。
- [x] **Step 33**（FR9.2・NFR4）ドメインの層（dsl.parse・dsl.domain）: `YamlLimits` に `maxLineLength` を足し、`SafeYamlParser.parse` で UTF-8 の解読の後、読む前に行ごとの長さ（改行 `\n`・`\r` で区切ったコードポイントの数。改行は数えない）を確かめ、上限を超えた最初の行で `YamlRejection`（種類は D-3 の答え、行はその行、列は上限＋1、文言の鍵は `DslMessageKeys` に足す `dsl.limit.line`、埋める値は上限の値だけ）にする。`DslFormat` に `MAX_LINE_LENGTH`（D-2 の値）を足す。`LoaderOptions` の値は変えない。
- [x] **Step 34**（FR9.2）ドメインの層のテスト: `SafeYamlParserTest` に境界（上限ちょうどの行は受け付ける、上限＋1 は拒否、`\r\n`・`\r` の行、全角を含む行はコードポイントで数える、拒否の誤りに本文が入らない）を足し、`SafeYamlParserPropertyTest` に「上限以下の行だけの文書は行の長さで拒否されない」の性質を足す（jqwik、失敗時の種を記録）。4.1節の「大きさの境界を1行で作るテスト」を、行を分ける形に直す。
- [x] **Step 35**（FR9.2）業務処理の層（dsl.service・role.service・dslmanage）: `SafeYamlLimits` に `maxLineLength` を足し、`DefaultSafeYamlReader` で `YamlLimits` に渡す。`DefaultDslReader.DSL_LIMITS` と `RoleTransferLimits.limits()` に D-2 の値を渡す。`DslErrorMessages` に `dsl.limit.line` の ja・en の文言（例「{0} 行目が1行の長さの上限（{1} 文字）を超えています」の形で、行は誤りの位置から出す）を足す。D-3 の B のときは新しい種類を `DslErrorKind`・`SafeYamlRejectionKind`・画面の型に足す。
- [x] **Step 36**（FR9.2）既定の DSL の生成（dslmanage.generate）: D-4 の答えのとおり、`DslTreeBuilder.label` で表示名の `ja`（DB のコメント）を上限の内に収める（A のとき 4,096 コードポイントで切る）。
- [x] **Step 37**（FR9.2）業務処理の層のテスト: `DefaultDslReaderTest`（投入の読み方と起動時の読み方の両方で上限を超える行を拒否する。起動時は `appliedUnreadable` の道に入る）、`SafeYamlReaderTest`、`RoleTransferReaderTest`、`DslTreeBuilderTest`（長いコメントを切る、切った後も書き出した行が上限の内）、`DslYamlWriterTest`（4,096 コードポイントの、二重引用符・逆斜線・書き出しで逃がす文字を含むコメントの表示名を書き出し、`SafeYamlParser` で読めて最も長い行が上限の内）、`RoleTransferWriterTest`（名前の上限の長さ（ロール 64 コードポイント、スキーマ・テーブル・カラム 256 文字、逃がす文字を含む）の書き出しが上限の内で読める）。`SafeYamlReaderSecretLeakIT` などの `SafeYamlLimits` の4つの引数の作り方を直す。
- [x] **Step 38**（FR9.2・NFR4）API の層のテスト: `DslAdminApiIT` に、上限＋1 の行を持つ DSL の投入が 422 の Problem Details（`DSL_INVALID`、誤りの一覧に行の上限の誤り）で拒否され、応答に YAML の部品の例外の文と本文が入らないことを足す。`RoleTransferLimitsApiIT` に、権限の YAML の確かめで同じ拒否（Problem Details、部品の例外の文なし）を足す。上限ちょうどの行は受け付けることを片方で確かめる。
- [x] **Step 39**（FR10.1・FR10.2・FR10.3）BUSY の結び付きの確かめ: `RoleBusyLogTraceIT`・`GroupBusyLogIT` を読み、`ROLE_BUSY`・`GROUP_BUSY` の両方で、応答の `code` と、応答の `traceId` の WARN がちょうど1行あること（L3 と L4 の結び付き）を確かめていることを記録する（読み取りでは両方とも確かめている）。足りない道として、ロールの割り当ての API がグループの行の待ちで返す `GROUP_BUSY`（`RoleAssignmentController` 156 行）の結び付きが確かめられていなければ、`RoleBusyLogTraceIT` に1件足す。k6 では上限切れを狙わない。
- [x] **Step 40**（FR7.1）k6 の台本（`perf/k6/scenarios.js`）: `roleAssign` に「割り当て済み（409 `ROLE_NO_CHANGE`）も受け入れる」形を足し、ロールの3場面（`workRoleRead`・`myPermissionsTree`・`roleAssignmentsRead`）の setup で使う。`workRoleRead` の確かめをロールの数（`length === 2`）ではなく、自分のロール（`data.roleSwitchIds[vu]` の2つ）が含まれることにする。`roleAdminRead` の setup の割り当ても同じ形にする。`k6 inspect --include-system-env-vars` で台本が読めることを確かめる（`project.md` の学び）。
- [x] **Step 41**（FR8・FR9.1・FR9.4・FR7.2）測りの手順の準備: `perf/README.md` に、(a) ロールの書き込みの待ちの最大の合否の回を 5 回流す手順（使い捨ての環境、`caffeinate -dims` で台本全体を包む、前後の `pmset`、20 ms 以上の回の見立てに使うログ・指標）、(b) 10 MiB の DSL と、1行の長さの上限の内で作る悪い形の 5 MiB の権限の YAML のヒープの測り（前の悪い形の 1.5 MB の1行のコメントは上限で拒否されるため、新しい悪い形は行を分けた形にする）、(c) ロールの3場面を手順書の順と単独の両方で流す手順、を足す。
- [x] **Step 42**（FR9.2・文書）`README.md` の DSL の読み込みの上限の表（670 行付近）と権限の YAML の上限（909 行付近）に1行の長さの上限を足す。既定の DSL の生成で長いコメントを切ること（D-4）を書く。
- [x] **Step 43**（NFR1・NFR2）`./gradlew verify` を流し、`:backend:cleanTest :backend:cleanIntegrationTest` を付けてカバレッジを実測して記録する。
- [ ] **Step 44**（FR7〜FR10）コミットの案（3節の C3-1〜C3-5）を依頼者に提案し、承認の後にコミットする。`develop` へ squash で統合（件名の案: 「k6 の台本の setup を独立させ、YAML の1行の長さに上限を置く」）し、作業ブランチを消す。
- [x] **Step 45**（文書・追跡）`code-summary.md`・`source-manifest.json`・`traceability.json` を書き、各 Step の印を付ける。FR12.1 の一覧の各行の結果（直した・範囲の外へ出した）を `code-summary.md` に写す。

## 3. コミットの区切りの案

計画の手順ごとではなく、場所・機能ごとに区切り、各コミットの時点でビルド（型の検査・コンパイル）が通る区切りにする（`project.md` の学び）。② は fast-forward で `develop` にそのまま残るため、特にこの決まりを守る。

| ブランチ | 案 | 中身 | 時点でビルドが通る理由 |
|---|---|---|---|
| ① | C1-1 | Gradle（wrapper・Spotless・jqwik・lockfile） | 版だけの変更 |
| ① | C1-2 | npm（`frontend/package.json`・`package-lock.json`） | 同上 |
| ① | C1-3 | Actions（`ci.yml`） | CI の外では動かない |
| ① | C1-4 | イメージ（Dockerfile・compose・docker/・README・perf/README） | 同上 |
| ① | C1-5 | `.github/dependabot.yml`（固定先の一覧・見送り） | 同上 |
| ② | C2-1 | `navLabels` の準備（`containsCurrent`、ja・en の鍵） | 型の注記の無いオブジェクトで、古い・新しい両方の `SidebarLabels` に合う |
| ② | C2-2 | `vendor/make-you-chic-ui` の固定先の更新（専用、前後のハッシュをメッセージに書く） | C2-1 で必須の項目を先に満たしている |
| ② | C2-3 | dsl・dslmanage の `toString` と `DslSecretLeakIT`（再現のテストと直しを同じコミット） | backend だけ |
| ② | C2-4 | navigation の `toString`・Javadoc と `NavigationSecretLeakIT` | backend だけ |
| ② | C2-5 | 起動時に落とした項目の数（backend と画面とテスト。D-7 の A のとき） | API と画面の型を同じコミットで変える |
| ② | C2-6 | 画面の骨組み（トップバーの文言、`:has()` を外す、共有の木、登録の見本、README）と `useSlowNotice` のまとめ、DSL の文言 | frontend だけ |
| ② | C2-7 | E2E の書き換え、`ApiEndpointCounts`、README の E2E の表 | テストと文書 |
| ③ | C3-1 | 1行の長さの上限（dsl.parse・dsl.domain・dsl.service・role.service とテスト） | `YamlLimits`・`SafeYamlLimits` の作り方を使うテストも同じコミットで直す |
| ③ | C3-2 | 既定の DSL の生成のコメントを切る（D-4 の A）とテスト | dslmanage.generate だけ |
| ③ | C3-3 | API の結合テスト（DSL・権限の YAML）、BUSY の結合テスト | テストだけ |
| ③ | C3-4 | k6 の台本 | perf だけ |
| ③ | C3-5 | README・perf/README | 文書 |

統合のコミットのメッセージは日本語で、末尾に決まりの共著の行を付ける。

## 4. 影響の範囲とカバレッジ・境界テスト

### 4.1 変えるものと、それを使っている既存のテスト・E2E・手順書

| 変えるもの | 使っている・確かめているもの（検索で洗い出した） | 扱い |
|---|---|---|
| `gradle/libs.versions.toml` の `spotless`・`jqwik`、wrapper | `backend/gradle.lockfile`・`settings-gradle.lockfile`、jqwik の性質ベースのテスト（`*PropertyTest`）、CI の `setup-gradle` | Step 3。verify で確かめる |
| `frontend/package.json`・`package-lock.json` の 10 件 | 画面のテストすべて、`frontend/vitest.config.ts`（`thresholds`）、ESLint・oxlint・Stylelint の設定、`config/npm-build-tools.txt`（vite・plugin-react） | Step 4・10 |
| `.github/workflows/ci.yml` の4件 | CI だけ | Step 5、CI は Build and Test |
| eclipse-temurin の jre | `Dockerfile` 17 行、`perf/README.md` 40・239・324・567・824 行、`README.md` 352・362・735・1135 行、`.github/dependabot.yml` 75 行のコメント | Step 6・7・9 |
| eclipse-temurin の jdk | `docker/hikari-pool.sh` 48 行、`perf/README.md` 76 行、`README.md` 392 行 | Step 2 の結果による |
| `grafana/otel-lgtm` | `compose.yaml` 83 行、`perf/README.md` 436 行 | Step 6 |
| `axllent/mailpit` | `compose.yaml` 121 行、`docker/perf/compose.yaml` 134 行、`README.md` 552 行（ダイジェスト無し） | Step 6（FR1.2b） |
| `grafana/k6` | `perf/README.md` 48・177・248・335・360・454・578・599・662・765・841 行 | Step 6（FR1.2c） |
| 対象DB のイメージ（`TargetDbImages`・compose・`docker/perf/compose.yaml`） | 今回の 20 件に無い | 変えない |
| make-you-chic-ui `a9309c4`（`SidebarLabels.containsCurrent` が必須に、`TopbarLabels`・`topbarLabels`、Dropdown の既定の `max-width` と `menuClassName`・`menuStyle`、畳んだ1段目の `contains-current` と読み上げの名前） | `ShellLayout.tsx`（`navLabels` が型の注記つきのため必須の項目が無いと型の検査で落ちる）、`ShellLayout.test.tsx`、`AppRouter.test.tsx`（`sidebar-toggle`）、`WorkRoleSwitcher.css`・`.test.tsx`、E2E の 150・160・170（畳んだ・開いた状態と `sidebar-toggle-biz:*`）、140・120（Dropdown の収まり）、`e2e/support/userMenu.ts`（トリガーを CSS の選択子で探すため名前の変化に影響されない） | Step 14・15・21・25・26。畳んだ状態の1段目のボタンを名前の一致で探すテストが無いことを生成の前にもう一度検索で確かめる |
| `DslError`・`DslErrorItem` の `toString` | `DslResultTypesTest`・`DslManageDomainTest`、`toString()` を呼ぶテスト（`DslSchemaValidatorTest`・`DslSemanticValidatorTest`・`SafeYamlParserTest`・`SafeYamlReaderTest`・`DslApi`・`DslYaml`）。どれも誤りの文字列の中身に頼っていないかを生成の時に確かめる | Step 16・18 |
| navigation の record の `toString`・`MenuFilter` の Javadoc | `NavNodeTest`、`NavigationUntrustedInputIT`・`NavigationConnectionUsageIT`・`NavigationQueryExposureIT`（`toString()` を呼ぶ） | Step 17・18 |
| `DslStatus` に項目を足す（D-7 の A） | `DslAdminApiIT`（状態の応答の確かめ）、`DslStartupIT`・`DslStartupLoaderTest`・`UnreadableAppliedRevisionTest`、画面の `api/types.ts`・`testing/fixtures.ts`・`DslStatusPanel.test.tsx`・`DslAdminPage.test.tsx`、E2E の `support/adminApiRoute.ts` の状態の見本 | Step 19・20・24・26 |
| `SharedTreeNodeItem.tsx` | `SharedTreeView.test.tsx`、`MyPermissionsPage.test.tsx`、ロールの権限の木の画面のテスト、E2E の 140・160（`shared-tree`） | Step 22・25 |
| 登録の区画（`types.ts`・`validateRegistrations.ts`・README） | `validateRegistrations.test.ts`、`navigationItems.test.ts`、`forbiddenHeading.test.ts`（`visibleWhen: 'LOGGED_IN'` と `section: 'ADMIN'` の見本） | Step 22・25 |
| `useSlowNotice` を `shared/` へ | groupadmin の `useGroupDetail.ts`・`useGroupList.ts`、roleadmin の `useRoleAssignments.ts`・`useRolePermissions.ts`・`useRoleList.ts`、roletransfer の `useRoleTransfer.ts`、3つの `useSlowNotice.test.tsx`、`eslintImportRules.test.ts`（変えない） | Step 23・25 |
| DSL の違いの文言（D-6 の A） | `DslDiffTable.test.tsx`・`DslPreviewPanel.test.tsx`、E2E の 040・135 が `適用中の DSL と違いはありません` の文字を探していないかを生成の時に確かめる | Step 24・25 |
| `MIN_ENDPOINTS` | `ApiAccessArchitectureTest`・`ApiAccessConsistencyIT` | Step 27 |
| `YamlLimits`・`SafeYamlLimits` の項目を足す | 作り方を使うテスト: `SafeYamlParserTest`・`SafeYamlParserPropertyTest`・`YamlTreeConverterTest`・`DefaultDslReaderTest`・`SafeYamlReaderTest`・`SafeYamlReaderSecretLeakIT`・`DslResultTypesTest`・`DslSamples`・`ValidationTestSupport`・`GenerateTestSupport`・`DslYaml`・`RoleTransferServiceTest`・`TransferYamlTrees`・`RoleTransferReaderTest`・`RoleTransferLimitsApiIT` | Step 33〜38 |
| 1行の長さの上限そのもの | 大きさの上限ちょうどの YAML を長い1行（`"a".repeat(...)` や1行のコメント）で作るテスト（DSL の 10 MiB・権限の 5 MiB の境界。`DslAdminApiIT`・`RoleTransferLimitsApiIT`・`RoleTransferReaderTest` など）は、大きさより先に行の上限で拒否されうる。生成の時に検索で洗い出し、行を分ける形に直す。`perf/make-large-dsl.mjs` の行は最大約 100 文字で影響しない。前の Intent の悪い形の権限の YAML（1.5 MB の1行のコメント）は上限で拒否される（Build and Test の測りの形を変える。6節） | Step 34・37・38・41 |
| 起動時の読み込み | 配備済みの内部DB の DSL に上限を超える行があると、起動時に読めず `appliedUnreadable` になる | Step 32 で測る。配備の前にもう一度（D-5） |
| 画面の roletransfer の誤りの文言（`failureMessage.ts`） | D-3 の A では行の上限の拒否も `TOO_LARGE` として「大きすぎる」の文言で出る | D-3 で確かめる |
| `DslTreeBuilder.label`（D-4 の A） | `DslTreeBuilderTest`・`DslGenerationPropertyTest`・`DefaultDslGenerator*IT`（3種類の対象DB のコメント） | Step 36・37 |
| `perf/k6/scenarios.js` | `perf/README.md` の場面の順、前の Intent の `performance-test-instructions.md`（書き換えない） | Step 40・41 |
| BUSY の結合テスト | `RoleBusyLogTraceIT`・`GroupBusyLogIT`・`GroupRoleAssignmentConcurrencyIT` | Step 39 |

### 4.2 境界テスト（`<機能>BoundaryArchitectureTest`）

本体のソース（`src/main`）に手を入れる見込みの機能は dsl・dslmanage・navigation・role で、どれも境界テストがある（`DslBoundaryArchitectureTest`・`DslManageBoundaryArchitectureTest`・`DslManageGenerateBoundaryArchitectureTest`・`NavigationBoundaryArchitectureTest`・`RoleBoundaryArchitectureTest`）。足す境界テストは無い。既存の境界テストは緩めない・消さない（新しい依存の向きは生まない見込み。生まれたら計画に戻して依頼者の承認を得る）。group は Step 39 でテストだけを変える見込みで、`src/main` には手を入れない。

### 4.3 カバレッジ

| 手を入れる見込みのパッケージ | `packagesJudgedByTotal` に入っているか | 作業 |
|---|---|---|
| `dsl.domain`・`dsl.parse`・`dsl.service` | 入っていない | パッケージごとの下限（行 80%・分岐 70%）の対象のまま |
| `dslmanage.domain`・`dslmanage.service`・`dslmanage.web`・`dslmanage.generate` | 入っていない | 同上 |
| `navigation.domain`・`navigation.web` | 入っていない | 同上 |
| `role.service` | 入っていない | 同上 |

一覧の 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）には手を入れない見込みで、一覧から外す作業は生じない。生成の途中でこの 7 つの `src/main` に手を入れる必要が出たら、そこで止めて計画に戻し、テストを足して下限を満たし一覧から外す作業を足す（`team.md`）。`ApiEndpointCounts` は `src/test` だけで「手を入れる」に当たらない。画面のカバレッジは `frontend/vitest.config.ts` の `thresholds`（行 80・分岐 70）を変えない。下限・除外は緩めない。

### 4.4 前提 A2 の確かめ（make-you-chic-ui `a9309c4`）

`a9309c4` は今使っている口を壊さない。ただし `SidebarLabels` に必須の `containsCurrent` が足され、型の注記つきで作っている `navLabels` が型の検査で落ちる（`ShellLayout.tsx` 124 行）ため、影響の範囲に入れ、Step 14 で先に満たす。`navSections` などほかの口は変わらない。畳んだ状態の1段目のボタンの読み上げの名前が、今の項目を含むときだけ変わる（Step 25・26 で確かめる）。

## 5. FR12.1 の一覧（前の Intent のレビューの指摘）

元の記録: `aidlc/spaces/default/intents/261004-role-menu/.aidlc-reviews/code-generation/units/<単位>/152fd96411988a79/1.json`（コード生成の段の読み直し）と `aidlc/spaces/default/intents/261004-role-menu/construction/build-and-test/build-and-test-summary.md` 5.4。同じ番号が単位ごとにあるため、単位（dsl-v2・navigation・cross-cutting）で区別する。「範囲の外の候補」の印は、FR12.3 で後の Intent に回す案があるもの。

| 束 | どのレビューの指摘か | 対象（画面・型・ファイル） | 直った状態（合否） | 印・決め |
|---|---|---|---|---|
| K2 | dsl-v2 R-04（Minor） | 招待（`frontend/e2e/060-invitation-accessibility.e2e.ts`）・利用者の管理（`120-user-admin-accessibility.e2e.ts`）の画面の 360px が未確認。あわせて `frontend/src/features/dsl/DslErrorList.tsx` の横に動く表の領域と表の名前の重なり | 060・120 の E2E に 360px の横のはみ出しなしの確かめがあり通る。名前の重なりは D-8 の答えのとおり（A なら記録だけ、B なら3つの表の名前を直し画面のテストが通る） | D-8 |
| K2 | dsl-v2 R-05（Suggestion） | `dsl/domain/DslError.java`（と `DslReadResult.Invalid`・`DslStartupReadResult.Invalid` の文字列）、`dslmanage/domain/DslErrorItem.java`（D-11） | `DslSecretLeakIT`（TRACE）で目印の値がログに出ない。直す前は落ちることを確かめた記録がある | FR3.1 |
| K2 | dsl-v2 R-07（Suggestion） | `dslmanage/service/DslDiffCalculator.java` はメニューを比べない。画面の `dsl.diff.none`「適用中の DSL と違いはありません」が誤解を招く | 違いが無いときの文言と違いの見出しの説明が、メニューを比べないことを伝える（ja・en）。`DslDiffTable.test.tsx`・`DslPreviewPanel.test.tsx` で確かめる | D-6。B（メニューの違いを計算する）は範囲の外の候補 |
| K2 | dsl-v2 R-08（Suggestion） | `dslmanage/service/DslStartupLoader.java` が深すぎる枝を落としても WARN のログだけで、管理者の画面に出ない | 状態の応答に落とした項目の数があり、1以上なら `DslStatusPanel` に警告が出る。`DslStartupIT`・`DslStatusPanel.test.tsx` で確かめる | D-7。**範囲の外の候補**（応答に項目を足し画面を変えるため。B なら記録だけ） |
| K3 | navigation R-03（Suggestion） | `navigation/domain/DisplayLabel.java`・`NavTableRef.java`・`TableAccessResult.Visible`、`navigation/web/DisplayLabelResponse.java`・`NavTableRefResponse.java`・`TableAccessResponse.java`（`MenuFilterInput` は関数だけを持ち値を持たないため対象外） | `NavigationSecretLeakIT`（TRACE）で DSL の表示名の目印がログに出ない。`toString` の単体の確かめが通る | FR4.1、D-10 |
| K3 | navigation R-04（Suggestion） | `navigation/domain/MenuFilter.java` の Javadoc | 深さの前提（`DslFormat.MAX_MENU_DEPTH` = 5 の検証済みのモデル、境界のテストの持ち主は dsl）が書かれている（読んで確かめる） | FR4.2 |
| K4 | cross-cutting R-02（Minor） | `frontend/src/shared/tree/SharedTreeNodeItem.tsx` の読み込みの失敗の文（`role`・`aria-live` が無い）とフォーカスを受ける区画（名前が無い） | 失敗の文が `role="alert"` で、区画に名前がある。`SharedTreeView.test.tsx` でロールと名前と axe を確かめる | FR5.1 |
| K4 | cross-cutting R-03（Minor） | `frontend/src/app/registry/types.ts`（`SidebarSection` は `'ADMIN'` だけ）・`validateRegistrations.ts`、決まりに反する見本（`validateRegistrations.test.ts`・`navigationItems.test.ts`・`forbiddenHeading.test.ts`） | D-9 の A: 管理者でない利用者向けの項目はサイドバーに登録しない（業務のメニューは DSL から出し、自分向けの項目はユーザーメニューに置く）ことが `features/README.md` に書かれ、見本が決まりに合う組（か反する見本と分かる名前）になってテストが通る | D-9。B（区画を足す）は範囲の外の候補 |
| K4 | cross-cutting R-05（Suggestion） | `ApiAccessArchitectureTest`・`ApiAccessConsistencyIT` の直書きの下限 34 | 1つの定数（`common/testsupport/ApiEndpointCounts`）にまとめ、今の口の数に上げて両方のテストが通る | 小さな直し |
| K4 | cross-cutting R-06（Suggestion） | `frontend/src/features/README.md` の `useLogout` の節 | 呼ぶ側が失敗と成功を区別しないこと、提供元が失敗でも状態を替える契約を `LoginStateGate.test.tsx` が確かめていることが一文で書かれている | 小さな直し |
| K4 | cross-cutting R-07（Suggestion） | 前の Intent の `code-summary.md`（境界テストの数 11 と 10 の差）・`traceability.json`（NFR6.4・BR1.7 が既存のファイルを指す） | 前の Intent の確定済みの記録は書き換えない（`project.md` の決まり）。今の境界テストの数（15 ファイル）と、差の扱いを今回の `code-summary.md` に記録する | 記録だけ |

## 6. Build and Test に引き継ぐこと

この段では台本・手順・テストの準備までとし、次は Build and Test で流す（Performance Validation の段が無いため。`project.md` の学び）。Build and Test の台本を書く前に、この一覧の項目を一つずつ台本の手順と突き合わせる。

1. **FR1.4**: 3つの統合の後の CI を、試行ごと（`gh run view --json attempt,jobs`）に確かめ、再実行で通ったものは1回目の失敗とテストを記録する。取り込んだ・見送ったプルリクエストの一覧。
2. **FR8.1〜FR8.4**: 新しい土台のイメージで、ロールの書き込みの待ちの最大を合否の回 5 回（`perf/README.md` の Step 41 の手順）。20 ms 以上の回の数と見立て、外した回と理由。直すときは依頼者に諮る。
3. **FR9.1・FR9.4**: 10 MiB の DSL の投入のヒープの試しの測り、1行の長さの上限の内の悪い形の 5 MiB の権限の YAML、`dslMixed` の `memory.peak`（上限の 90% 未満）。前の Intent の悪い形（1.5 MB の1行のコメント）は FR9.2 で拒否されるため、行を分けた悪い形で測り、形の違いを記録する。
4. **FR9.3**: 上の測りをもとに、メモリの上限と `MaxRAMPercentage` を変えるか決める（変えるときは値・根拠・戻し方）。
5. **FR7.2**: ロールの3場面を手順書の順と単独で流し、409 `ROLE_NO_CHANGE` と確かめの不一致で止まらないこと。
6. **A3**: P1・P4 の対象の振る舞いが依存の更新で変わっていないこと。
7. **イメージ**: D-13 の B のときは新しい jre のイメージの起動の確かめ。新しい jdk のイメージ（据え置かなかったとき）で `docker/hikari-pool.sh status` が動くこと。
8. **FR9.2**: 配備の前（Deployment Execution）に、配備済みの内部DB の DSL の最も長い行を測り直す（D-5 の A）。
9. **NFR3**: 目標は緩めない。測れなかった目標は `Unverified`、外れた目標は `Not Met`。

## 7. 依頼者に確かめたいこと（計画の承認の前に尋ねる）

**依頼者の答え（2026-10-15、計画の承認の前）: D-1〜D-13 はすべて A。** 手順の中の「D-n の答えにより」「A のとき」は、すべて A の側で行う。D-3 の B・D-6 の B・D-7 の B・D-9 の B の分岐は行わない。

### D-1 ② の固定先の更新のコミットの並べ方（FR2.1）

`a9309c4` は `SidebarLabels.containsCurrent` を必須にするため、固定先だけを上げたコミットでは画面の型の検査が落ちる。fast-forward で各コミットが `develop` に残る。

- A. 先に、古い・新しい両方の型に合う形で `navLabels` に `containsCurrent` を足すコミットを置き、次に固定先の更新だけの専用のコミット、その後に残りの直し（推奨。専用のコミットと「各コミットでビルドが通る」の両方を守れる）
- B. 固定先の更新と `navLabels` の直しを1つのコミットにする（専用のコミットの決まりから外れる）
- C. 固定先の更新だけの専用のコミットにし、そのコミットだけ型の検査が落ちることを記録する
- X. Other (please specify)

### D-2 1行の長さの上限の値（FR9.2）

数え方は、UTF-8 を解読した後の1行のコードポイントの数（改行は数えない）とし、DSL と権限の YAML に同じ値を当てる案。根拠（コードからの見積もり）:

- 既定の DSL: `DslYamlWriter` は幅の制限なし・折り返しなし（`setWidth(Integer.MAX_VALUE)`・`setSplitLines(false)`）で、長い行は表示名の `ja`（DB のコメント）から生じる。MySQL・MariaDB のコメントはテーブル 2,048 文字・カラム 1,024 文字が上限だが、PostgreSQL のコメントには実用上の上限が無い（D-4）。名前（識別子）は 63〜64 文字。書き出しで逃がす文字は最悪1文字が 10 文字（`\UXXXXXXXX`）になる。
- 権限の書き出し: `RoleTransferWriter` の行は最も深くて字下げ 7 段＋名前（ロール 64 コードポイント、スキーマ・テーブル・カラムは表の列で 256 文字）＋`: FULL` で、逃がす文字を含めても約 2,600 文字未満。
- 配備済みの DSL: Step 32 で測る。

- A. 65,536 コードポイント（推奨。D-4 の A と組むと既定の DSL の最長は約 41,000 文字で収まり、MySQL・MariaDB のコメントは切られない。前の Intent の悪い形（1.5 MB の1行のコメント）と、流れの書き方で全体を数行に詰めた悪い形は拒否される）
- B. 16,384 コードポイント（守りは強いが、D-4 の切る長さを 1,000 程度に下げる必要があり、長いコメントの表示名が切られやすい）
- C. 1,048,576 コードポイント（既定の DSL はほぼ確実に収まるが、1行 1 MB の悪い形を止められず守りが弱い）
- X. Other (please specify)

### D-3 上限を超えたときの誤りの種類（FR9.2）

- A. 既存の大きさの種類（DSL は `SIZE_LIMIT`、権限の YAML は `TOO_LARGE`）を使い、DSL の文言の鍵だけ `dsl.limit.line` を足す（推奨。応答の種類の列挙と画面の型を変えずに済む。代わりに、権限の YAML の画面では行の上限の拒否も「大きすぎる」の文言で出る）
- B. 新しい種類（`LINE_LIMIT`・`LINE_TOO_LONG`）を足す（区別ははっきりするが、契約の列挙と画面の型・文言・テストが増える）
- X. Other (please specify)

### D-4 既定の DSL の生成で長いコメントを切るか（FR9.2）

PostgreSQL の長いコメントがあると、生成した既定の DSL が上限で読めず、生成が想定外の失敗（500）になりうる。

- A. 表示名の `ja` にするコメントを 4,096 コードポイントで切る（推奨。MySQL・MariaDB の上限より長く、普通は切られない。最悪の逃がしでも 65,536 の内）
- B. 切らない（PostgreSQL の 6,500 文字を超えるような長いコメントでは生成が失敗する。失敗の扱いは今のまま）
- C. 上限を超える行が出たら、生成を業務の誤り（新しい code）として断る（API の code が増える）
- X. Other (please specify)

### D-5 配備済みの DSL の最も長い行をいつ測るか（FR9.2）

- A. ③ の始め（Step 32、値を決める前）に測り、配備の前（Deployment Execution）にもう一度測る（推奨。値の判断を早くでき、間の変化も拾える。アプリを2回短く止める）
- B. 配備の前（Deployment Execution）だけで測る（止めるのは1回だが、超えていたら③ をやり直す）
- C. ③ の始めだけで測る（間に DSL を投入・適用すると見落とす）
- X. Other (please specify)

### D-6 K2 R-07（メニューの違いを比べない）の直し方

- A. 画面の文言で「スキーマ・テーブル・カラムの違いを比べ、メニューは比べない」を伝える（推奨。小さな直し）
- B. メニューの違いも計算して画面に出す（違いの計算と応答と画面が変わるため、範囲の外へ出して後の Intent に回す）
- X. Other (please specify)

### D-7 K2 R-08（落とした枝を画面に出さない）の直し方

起動時に枝を落とすのは、深さ 5 を超える DSL が適用済みのときだけで、今の投入と適用は深さを確かめるため普通は起きない。

- A. 状態の応答に落とした項目の数を足し、DSL の管理の画面に警告を出す（推奨。`appliedUnreadable` と同じ形で、既存の口に項目を足すだけ）
- B. 範囲の外へ出し、WARN のログで足りるとして記録する（FR12.3）
- X. Other (please specify)

### D-8 K2 R-04 の、横に動く表の領域と表の名前の重なり

`DslErrorList`・`TransferErrorList`・`TransferCheckTable` の3か所は、領域（「横に動かして読めます」）と表（中身の名前）に別の名前がある。axe の違反は無い。

- A. 直さず記録だけにし、360px の確かめだけを足す（推奨。名前の役目が違い、読み上げの重なりは軽い）
- B. 3か所とも表の `aria-label` を外し、領域の名前だけにする（画面のテストと E2E の名前での探し方を直す）
- X. Other (please specify)

### D-9 K4 R-03（管理者でない利用者向けのサイドバーの区画）

- A. 区画は管理だけのままとし、管理者でない利用者向けの項目はサイドバーに登録しない（業務は DSL のメニュー、自分向けはユーザーメニュー）ことを README に書き、決まりに反するテストの見本を直す（推奨。小さな直し）
- B. 管理者でない利用者向けの区画を足す（登録の型と検査とサイドバーの組み立てが変わるため、範囲の外へ出す）
- X. Other (please specify)

### D-10 K3 R-03 の伏せる範囲

`GET /api/me/table-access` のスキーマ名・テーブル名は、controller と service の引数（`String`）としても TRACE に出る。値は業務の名前で、個人に関する値ではない。

- A. record の `toString` だけを伏せ、引数のスキーマ名・テーブル名は今のまま（業務の名前として受け入れ、記録する）（推奨）
- B. controller から service まで、名前を伏せ字の型で受け渡す形にする（service の口が変わる）
- X. Other (please specify)

### D-11 FR3.1 で `DslErrorItem` も伏せるか

`DslErrorItem`（dslmanage.domain）は、埋める値を組み立てた文言（`message`）を持ち、同じ抜粋を出しうる。

- A. `DslError` と `DslErrorItem` の両方を伏せる（推奨。同じ値の漏れ道を1つの直しで閉じる）
- B. 要件の文言どおり `DslError` だけを伏せる
- X. Other (please specify)

### D-12 取り込んだ Dependabot のプルリクエストの閉じ方（FR1.1）

- A. 依頼者の push の後、Dependabot が自動で閉じるのを待ち、残ったものの一覧と `gh pr close` のコマンドを AI が示して依頼者が閉じる（推奨。リモートの操作を依頼者に残す）
- B. 依頼者の push の後、承認を得て AI が `gh pr close` で閉じる
- X. Other (please specify)

### D-13 ① の新しいイメージの起動の確かめ

`./gradlew verify` はイメージを作らないため、新しい eclipse-temurin の jre で起動できるかは確かめられない。

- A. ① の統合の前に、使い捨てのコンテナでイメージを作って起動し、ヘルスチェックを確かめる（推奨。Step 9）
- B. Build and Test と配備の段に任せる
- X. Other (please specify)

## Testing Contract

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "team",
  "ordering": "テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。",
  "scope": "bugfix",
  "test_strategy": "minimal",
  "project_type": "brownfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる。ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる（既存のパッケージは全体の合計で判定する）。どちらになるかは、パッケージごとの下限を入れる Bolt で既存のパッケージのカバレッジを実測して決め、結果を記録する。\n- 全体の合計で判定している既存のパッケージ（`backend/build.gradle.kts` の `packagesJudgedByTotal` の一覧。2026-10-04 の時点で 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）。`user.domain`・`user.repository`・`user.service` は Intent 260925-user-management で一覧から外れ、含まない）に手を入れる Bolt では、テストを足してそのパッケージの下限（行 80%・分岐 70%）を満たし、一覧から外してパッケージごとの下限の対象に必ず戻す。「手を入れる」は、そのパッケージの本体のソース（`src/main`）の変更のすべて（説明文だけの直しを含む）を指し、テストだけの変更は含めない。手を入れる見込みのパッケージとそれに伴う作業は、Delivery Planning とコード生成の計画で、各パッケージの今の値を実測して見積もる。カバレッジは、その Bolt のテストを足した後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、値を記録する。一度外したパッケージは一覧に戻さず、一覧を増やす変更はしない。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）。Intent `260923-dsl-schema-loader` の Build and Test で `verify` の時間と colima の VM のメモリを実測したうえで、この形に決めた。コンテナの実行環境が無いときの扱いは Way of Working のとおり。\n- 外へ送るメール（SMTP）を使う機能のテストは、送信の部品をモックで置き換えて済ませず、テストの中で JVM の中に起動するテスト用の SMTP の受け手で実際に受け、宛先・件名・本文（HTML）・言語を確かめる。受け手にコンテナは使わない（コンテナの実行環境が無いときに飛ばしてよいテストは、対象DB のテストだけのままとする）。送信の失敗（受け手が接続を拒む・応答しない）のテストも必ず入れる。実在の宛先へは送らない。受け手の具体の道具は、採用の前のライセンスの確認とあわせて設計の段で決める。\n- 画面からの一連の操作を確かめるテスト（E2E）は Playwright（`./gradlew e2eTest`）で書き、`./gradlew verify` と CI の外に置く。本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す。新しく足す流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作る。複数の機能の Intent を1つに束ねた Intent では、束ねた元の Intent ごとに数える（Intent 261004-role-menu は最大2本）。既存の E2E を新しい形（権限の形など）に合わせて書き換えることは、本数に数えない。利用者の状態を変える操作（利用停止・管理の権限の変更・ロックなど）は、その流れで自分で作った利用者だけを対象にし、初期管理者の状態は変えない。役割・権限を変える操作も、その流れで自分で作った利用者と役割だけを対象にし、初期管理者は変えない（E2E は1つのアプリ・内部DB・初期管理者を全ファイルで共有して順に流すため）。E2E は、画面・認証に関わる変更を統合する前と、リリースの前に手元で実行する。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- リポジトリは公開のため、テストデータのメールアドレスは予約のドメイン（`example.com` など）だけにし、実在しそうな氏名・宛先を置かない。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- 画面のテストで、描画の後（`useEffect` などの効果）に反映される値は、操作の直後に同期で確かめず、`waitFor` で待って確かめる。テストの時間の上限（Vitest の既定 5 秒など）は、原因を確かめずに延ばさない。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。\n- 不安定なテストと CI の失敗は、次の1つの決まりで扱う。\n  - 手元で再現した不安定なテストは、原因を直すまで統合しない（次へ進まない）。\n  - 手元で再現しないものは、再現の試みに先に時間の上限を決め、見立てと試みの範囲を記録したうえで、CI の再実行で通れば進めてよい（不安定と確かめられていない扱い）。\n  - 同じテストが二度目に落ちたら、原因を直すまで次へ進まない。\n  - 失敗を直さずに次の Intent へ持ち越すのは、依頼者の決定があるときだけとし、決まりとの差を記録する。\n- 認証・認可・監査に関わる機能では、次のテストを必ず書く。★印の項目は要件（しきい値や動作）が未確定のため、要件定義で決めてからテストを書く。この一覧の「管理者」は管理の権限を持つ利用者を、「管理の権限」はその判定に使う権限を指し、権限の形（真偽値1つか役割・権限か）によらずに読む。権限の具体の形（役割・権限の名前など）は、要件で決まった後に書き足す。\n  - アカウントロック: 失敗回数のしきい値の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロック中は正しいパスワードでも拒否、ログイン成功時の失敗回数の扱い。★しきい値、回数を数える期間、ロックの解除方法\n  - ログイン: 存在しないユーザーとパスワード誤りで、応答からユーザーIDの存在を推測できないこと\n  - トークン: 有効期限の境界（直前は有効／直後は無効）、署名の改ざん、署名方式の指定を悪用した改ざん（`alg: none` 等）、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセストークンが有効期限まで使えること（決定済みの仕様として明示する）。★各トークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか\n  - 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い／不正なときの動作、パスワードがログに出ない。★設定が無いときに起動を止めるか\n  - 認可: 未認証（401）、その権限を持たない（403）、持つ（200）をサーバー側のテストで確かめる（画面で管理メニューを隠すことはサーバー側の検査の代わりにしない）\n  - 監査ログ: 対象イベントごとに必須項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること。★監査ログの書き込みに失敗したときに操作を失敗させるか\n  - 秘密情報の漏えい: ログ・監査ログの出力にパスワード・トークンの値が含まれないこと\n  - 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる、外部エクスポートが既定で無効であること\n  - 画面: ログイン画面のアクセシビリティ検査、ロック時のメッセージ表示、ログアウトでトークンが破棄されること\n  - 招待と登録の完了: 招待の有効期限の境界（直前は有効／直後は無効）、使い終えた招待・取り消した招待の再使用の拒否、改ざんした招待・存在しない招待の拒否（応答から利用者の存在を推測できないこと）、招待中（登録が終わっていない）の利用者はログイン・トークンの更新・アクセストークンの認証のどれでも拒否されること。★招待の有効期限の値、再送・取り消しの扱い\n  - パスワードの変更: 今のパスワードの確かめ、パスワードの規則の境界、変更の後のリフレッシュトークンの扱い（決めた側の動作を明示したテストにする）。★パスワードの規則、変更の後にほかの端末のリフレッシュトークンを無効にするか\n  - 利用停止: 停止中の利用者は、ログインの照合・トークンの更新・アクセストークンの認証の3つの入口のすべてで拒否されることを、入口ごとにサーバー側のテストで確かめる。停止を解いた直後は3つの入口のすべてで受け付けること。停止の前に出したリフレッシュトークン・アクセストークンの扱いは、決めた側の動作を明示したテストにする。★停止を応答から推測できてよいか、停止の前に出したトークンの扱い\n  - 管理の権限の変更: 管理の権限を与えた直後・外した直後の次の要求で、管理の API の 403／200 がサーバー側で切り替わること（画面が持つ古い権限に頼らない）。権限を外す前に出したトークンの扱いと、自分の管理の権限を外す操作の扱いは、決めた側の動作を明示したテストにする。★権限を外す前に出したトークンの扱い、自分の管理の権限を外す操作の扱い\n  - ロックの解除: 解除の直後に正しいパスワードで入れること、解除の後の失敗回数の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロックの状態の行が無い利用者の解除。時刻は注入した時計で動かし、実時刻と `sleep` に頼らない。★解除の後の失敗回数の扱い、ロックの状態の行が無いときの応答\n  - 最後の管理者の保護: 最後の有効な管理者（管理の権限を持つ利用者）から管理の権限を外す・利用を止める操作は拒否され、拒否の後に状態が変わっていないこと。2人の管理者が同時に互いの管理の権限を外す・利用を止めても、有効な管理者が 0 人にならないこと。同時の重なりはスレッドの数に頼らず、待ち合わせで確実に作る。★最後の管理者の数え方（止めた管理者を数えるか）\n  - 管理の API の認可: 足す管理の API のすべてについて、未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。停止中の管理者は管理の API を呼べないこと。\n  - 要求の改ざん: 管理の API の外（`/api/me` など）から、要求の本文の値を変えて自分の管理の権限や利用者の状態を変えられないこと（一括代入の防止）。\n  - 管理の操作の監査: 利用者の権限・状態を変える操作（管理の権限の付け外し・利用停止と再開・ロックの解除）ごとに、操作した人・対象の利用者・結果が記録されること。★拒否した操作（403・最後の管理者の拒否など）を記録するか\n  - 利用者の管理の漏えい: 一覧・詳細の応答と監査の行に、パスワードのハッシュ値・リフレッシュトークン・ロックの判定の内部の値が含まれないこと。TRACE のログを有効にして一覧・詳細を読んでも、メールアドレス・氏名がアプリのログに出ないこと（既存の `*SecretLeakIT` と同じ形で確かめる）。\n- 役割・権限の機能では、次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 権限ごとの API の認可: 足す・変える API のすべてについて、未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。403 を確かめる利用者は、権限が何も無い利用者ではなく、要る権限だけを欠く利用者（ほかの権限は持つ）にする。API と権限の組は、パラメーターを使うテストで表として書く。停止中の利用者は、権限があっても通らないこと。\n  - API の分類の網羅: アプリが持つすべての API が「公開・ログインだけ・権限が要る（どの権限か）」のどれかに明示して分類されていることを確かめるテストを置き、分類の無い API があれば失敗させる（`/api/**` の既定は「ログインだけ」で、権限の決まりを書き忘れるとログイン中の誰でも呼べるため）。足した API を分類に足し忘れると落ちる形にする。分類の持ち方と仕組みの置き場は設計の段で決める。\n  - 割り当ての変更の反映: 役割・権限を付けた直後・外した直後の次の要求で、403／200 がサーバー側で切り替わること（画面が持つ古い権限に頼らない）。★変更の前に出したトークンの扱い\n  - 権限の昇格・一括代入・IDOR の防止: 操作する人が持たない権限を、自分や他人に与えられないこと。要求の本文の値を変えて自分の役割・権限を変えられないこと、役割の作成・変更の本文に許していない項目（ID・組み込みの印・作成者など）を足しても反映されないこと。役割・割り当ての ID を差し替えた要求（他人の割り当て・存在しない役割など）は拒否され、状態が変わらないこと。★組み込みの役割の有無と、その削除・権限の取り上げの保護\n  - 自分自身への操作と最後の管理者の読み替え: 自分の役割を外す操作と、最後の有効な管理者を役割でどう数えるかは、決めた側の動作を明示したテストにする。同時の重なりはスレッドの数に頼らず、待ち合わせで確実に作る。★自分の役割を外す操作の扱い、最後の有効な管理者の役割での数え方\n  - 監査: 割り当ての変更ごとに、操作した人・対象・変えた中身・結果が記録されること。★役割そのものの作成・変更・削除を記録するか、拒否した操作（403・昇格の拒否など）を記録するか\n  - 画面の出し分け: メニュー・画面を隠すことを、サーバー側の検査の代わりにしない。権限の無い画面へ直接移ったときに 403 の表示になること。\n  - 性質ベースのテスト: 役割から権限の集合を求める関数（サーバー側）と、権限でメニューの枝を落とす関数（画面側）は純粋な関数にし、jqwik・fast-check で性質（持たない権限の API・項目が出ない、役割を足しても権限は減らない など）を確かめる。\n- N 階層のメニュー（ナビゲーションの木）の画面では、次のテストを必ず書く。深さの上限の具体的な値は設計の段で決め、決めた値の境界で確かめる。\n  - 開閉と今の項目: 開閉の状態（`aria-expanded`）と今の項目（`aria-current`）が正しく出ること、キーボードだけでたどれること。\n  - 深さの上限: 上限ちょうどは受け付け、上限を超えると拒否されること。\n  - 表示の木を作る関数: 定義の木から表示の木を作る純粋な関数（権限で枝を落とす・並べる）に、fast-check の性質ベースのテストを当てる（例: 権限の無い項目が出ない、子の無い枝が残らない）。\n  - 信頼できない入力: メニューの定義（`label`・`icon`・`table`）を信頼できない入力として扱う。表示名に `<`・`>`・`&`・`\"`・`'` を含めても文字として出る（HTML として描かない）こと、`icon` を許す名前の一覧と照らすこと、`table` の名前から組み立てる道をエンコードし、アプリの中の決めた形から外れない（外部の URL・`javascript:` の道を作らない）こと。★一覧に無い `icon` を拒否するか既定のアイコンにするか\n  - make-you-chic-ui の部品に頼る振る舞い: 開閉・`aria-current`・キーボードなど、make-you-chic-ui の部品に頼る振る舞いも、このリポジトリの画面のテストと実際のブラウザの axe で確かめる（make-you-chic-ui のテストはこのリポジトリの CI の対象外のため）。axe は、展開した状態・深い階層・長い名前で、ブランドカラーとテーマのすべての組について行う。\n- 利用者へメールを送る機能では、Code Style と `project.md` のメールの決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 本文のエスケープ: 差し込む利用者の値（氏名・メールアドレスなど）に `<`・`>`・`&`・`\"`・`'` を含めても、タグや属性としてそのまま出ないこと。利用者の値をエスケープしない差し込み（`{{{ }}}`・`{{& }}`）で入れていないこと、属性の値を二重引用符で囲んでいること（エンジンの `{{ }}` は `'` を置き換えないため）\n  - テンプレートの描画: 日本語と英語のすべてのテンプレートを描けて、件名が空でないこと、差し込み漏れ（`{{` の残り）が無いこと、ライセンスヘッダーが描いた本文に出ないこと\n  - 招待の URL: 設定したベース URL だけから組み立て、要求の Host ヘッダーから組み立てないこと。ベース URL の設定が無いときに要求の Host を変えても、招待の URL に出ないこと。★ベース URL の設定が無いときの動作\n  - トークンと URL の漏えい: 招待のトークンと招待の URL が、アプリのログ・監査ログ・トレースの属性・エラー応答に含まれないこと（既存の `*SecretLeakIT` と同じ形で確かめる）\n  - ヘッダーへの差し込み: 宛先・件名・差し込む値に改行（CR・LF）を含めても、メールのヘッダーが増えず拒否されること\n  - 送信の失敗: 送信が失敗したとき（受け手が接続を拒む・応答しない）に、宛先のメールアドレス・SMTP の応答・資格情報が応答とログに含まれないこと。★送信が失敗したときの業務の動作（招待を失敗させるか、再送に回すか）\n- 利用者が投入する DSL（YAML）を読み込む機能では、Code Style の「信頼できない入力」の決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。上限の具体的な数値は設計の段で決め、決めた値の境界で確かめる。\n  - 大きさ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 入れ子の深さ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 別名（アンカー）: 展開の数が上限を超えると拒否され、別名の展開の爆発で処理が止まらない\n  - タグ: 任意の型を作るタグ（`!!` など）を含む DSL は拒否され、型が作られない\n  - 重複キー: 同じキーが重なる DSL はエラーになり、後の値で黙って上書きされない\n  - JSON Schema の `$ref`: 外部の URL を取りに行かない\n  - 拒否の応答: Problem Details の形で返り、YAML・JSON Schema の部品の例外のメッセージを含まない\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22)"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) \n- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) \n- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) \n- 同時の重なりを確実に作るため、本番のコードを変えずに、監査の書き込みの時間を測る LongSupplier（AuditEventListener で2本目を借りる直前に呼ばれる）をテストで差し替えて待ち合わせる方式にした。既存の LoginConcurrencyIT は 8 スレッドでプールの 10 に届かず、前回の失敗のログインで尽きなかった理由の1つと見られる。 (learned 2026-09-23) \n- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) \n- 修正の前の設定（例: 上限 1g）も修正の後の環境（例: CPU 4 の VM）で流し（pre1g）、要件の前提（VM を上げても F3 が起きる）を実測で裏付ける。 (learned 2026-09-23) \n- パッケージごとのカバレッジの下限と SpotBugs の SQL_ の関門を U1 の計画に入れた。team.md の決まりだが今のビルドに無く、この Intent で最初に作る単位のため。U1 の設計の文書には無い作業。 (learned 2026-09-24) \n- U4 で既存の AuditSecretLeakIT の列の一覧に V6 の4列を足し、テストの JVM のヒープを 1g にした。前者は承認済みの V6 と必ず食い違うため、後者は構造の検査がクラスを持ち続け U3 の 10MB 超えのテストでヒープが尽きたため。どちらも計画に無い変更で、依頼者に確かめる。 (learned 2026-09-24) \n- 応答しない対象DB の TIMEOUT の確かめに、テストの中で開いた ServerSocket（受け付けて何も返さない）を使う。外の端末に頼らず確実に再現できる代わりに、本物の DB の遅延ではない。 (learned 2026-09-24) \n- パッケージごとのカバレッジの下限は新しいパッケージだけに当てた。実測で audit.service（行 77.2%）・common.health（行 79.2%）・auth.repository（分岐 50.0%）が単独で下回ったため（team.md の決まりどおり）。既存の 22 パッケージを一覧で外し、新しいパッケージは自動で対象になる。 (learned 2026-09-24) \n- 負荷の試験で接続プールが尽きたかを確かめるときは、使い捨てのアプリにだけ MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics を渡して /actuator/metrics の hikaricp の値を読み、数秒ごとの使用中の数ではなく、待ちの時間切れの累計と借りるまでの待ちの最大で判断する（配備したアプリの公開の範囲は変えない）。 (learned 2026-09-24) \n- k6 などの長い試験は caffeinate -i を付けて流し、PC の自動のスリープで要求が止まって結果が崩れるのを防ぐ（バッテリー駆動のまま 414 秒スリープし、要求が 6分52秒止まった）。内部DB に SQL で直接入れた試験用の利用者はロックの状態の行が無いため、同時のログインを流す前に1人ずつログインさせて行を作る。 (learned 2026-09-24) \n- 軽い API の性能は、投入を重ねて内部DB のファイルが膨らんだ状態（悪い側の条件）のまま測る。メモリの最大（memory.peak）を比べる試験の前は、アプリのコンテナを作り直して前の最大の値を消す。 (learned 2026-09-24) \n- Performance Validation の段が無いため、2件目の直しの負荷の試験での確かめ（Q5: A）の持ち主を Build and Test とした（project.md の学びどおり）。 (learned 2026-09-24) \n- colima の PC で ./gradlew verify を流すときは、README の DOCKER_HOST と TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE をシェルに渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗した。付けて流し直した結果を基準とした。 (learned 2026-09-25) \n- unit-test-instructions.md の k6 inspect のコマンドは --include-system-env-vars が無いと場面の名前が undefined になり確かめにならなかった。承認済みの文書は変えず、実際には付けて流し、code-summary.md に差を記録した。 (learned 2026-09-25) \n- 1回目の verify で AccessTokenApiIT の 6 件が接続の失敗で落ちた。変更の経路に触れず、同じ時刻に Gradle の作業プロセスとの接続も時間切れだったため PC の負荷による一時的な失敗と見立て、段の中の直しの1回目としてコードを変えずに verify を流し直して通した。原因は確かめていない。 (learned 2026-09-25) \n- 依頼者の判断で、試験のイメージを mastersmith:local ではなく mastersmith:followup-fixes で作り、perf/README の手順（local を作り直す）から外れた。配備したアプリは k6 のあいだ止め、約 17 分後に同じコンテナで起動し直した。 (learned 2026-09-25) \n- 試験の後にロックの状態の行の数を数える問い合わせの列の名前を誤り、使い捨ての環境を消した後で取り直せなかった。合格の条件（ログインの checks と 500 の件数）で判定した。消す前に確かめの結果を見てから片付けるべきだった。 (learned 2026-09-25) \n- Q4（確かめられたときだけ直す）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違いうるため、追加の質問 F3 で、再現できなければ不安定と確かめられていない扱いとして統合してよいことを確かめた。 (learned 2026-09-25) \n- 要件 FR2.2（目標は計画の承認の場で決める）を満たすため、依頼者の決定（Q1: A）で、計画を書く前に dslMixed でメモリの内訳を測ることにした。測る間は配備したアプリを止める。 (learned 2026-09-25) \n- 生成で、テストの既定で MBean の登録を無効にする置き場を、計画の候補の TestDatabase ではなくテストの EnvironmentPostProcessor にした。TestDatabase を使わずに Spring を起動するテストのクラスが 15 あり、漏れるため。再現の結合テストは 12 回では伸びが小さく判定がはっきりしないため、24 回・履歴の上限 3 にした。 (learned 2026-09-25) \n- AccessTokenApiIT は、同じ例外の文言を仕組み（colima の IPv4 の転送と Java の IPv6 の待ち受けの番号の重なり）ごと再現できたことを原因の確認とみなし、依頼者の決定（D7）でテストの JVM に preferIPv4Stack を付けて直した。実際の1回目がこれで起きたかは確かめられず、今回の繰り返しでは重なりは起きなかった。 (learned 2026-09-25) \n- Test Strategy は Minimal だが、要件 FR5 と計画の「Build and Test に引き継ぐこと」のため、結合・性能・セキュリティの手順書も作り、この段で dslMixed・--storage --compact（40 回）・詰め直しの最中のログイン・refresh・dslCycle を流した。 (learned 2026-09-25) \n- 計画の dslCycle を最初の台本に入れ忘れ、配備したアプリをもう一度（約 4 分）止めて流した。負荷の試験の台本を書く前に、計画の「Build and Test に引き継ぐこと」の項目を一つずつ台本の手順と突き合わせる。 (learned 2026-09-25) \n- 実際のブラウザのアクセシビリティの検査（axe）は、誤りを出した状態と、現実に近いデータ（2語の氏名など）で、ブランドカラーとテーマのすべての組について行う。user-management の U7 の 080 で初めて、make-you-chic-ui の Avatar（頭文字が2文字のとき）と dark の FormField の誤りの文字のコントラスト不足が出た（050〜070 は誤りの状態を見ず、氏名が1語だったため出なかった）。 (learned 2026-09-28) \n- Playwright の webServer.env に置いた値は json の報告に残る。E2E の仮の資格情報はプロセスの環境変数で渡し、報告の部品の確かめと json の文字列の検索で、パスワード・トークン・メールアドレスが入っていないことを確かめる（user-management の U4 で見つかった）。 (learned 2026-09-28) \n- CI（66fe981）の verify が2回とも別々のテストの時間切れ（H2CompactionByPoolSuspensionIT の接続の待ち 10 秒、InvitationAdminPage.test.tsx の既定 5 秒）で失敗し、U4〜U7 の NFR7.1 が Not Met だったが、依頼者の決定「次のintentでコントラストと一緒に直す。このintentではこのまま進める。」で受け入れた失敗とした。team.md の「CI が失敗したら次に進む前に直す」と食い違うため test-results.md 8.1 に差を記録した。 (learned 2026-09-28) \n- k6 の登録の完了と取り消しの場面は、決定の文言（招待を VU の数だけ）ではなく既定 100 回・招待 100 件とした。トークンは1回しか使えず 10 件では p95 の意味が薄いため、U3 の NFR6.4 の「流す回数以上の招待を用意する」に合わせた。承認の場で確かめる。 (learned 2026-09-28) \n- caffeinate -i は場面ごとではなく、試験の台本全体を包む形で付ける。場面ごとに起こし直すと切れ目で守りが外れ、PC が眠る（user-management の Performance Validation で 92 秒眠り、要求が 1 分 29 秒遅れた）。遅れが出たときは pmset -g log でスリープを確かめる。 (learned 2026-09-28) \n- 応答しないメールの受け手は、Mailpit を docker pause で一時停止して作る（接続は受け付け、何も返さない）。コンテナを止めると接続の失敗になり、時間切れの確かめにならない（user-management の Performance Validation）。 (learned 2026-09-28) \n- Q1 で原因を確かめずに上限を延ばす答え（C）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違うため追加の質問 F1 で確かめ、延ばしたうえで失敗時の診断を足し、差を記録する形（B）になった。Q3 の「すべて試す」は verify で判定できない Temurin 26・logback-appender の扱いを F2 で確かめ、見送り（C）になった。 (learned 2026-09-29) \n- 診断の確かめで、上限 1 ミリ秒は Awaitility の問い合わせの間隔（100 ミリ秒）より短く設定の誤りで弾かれ、150 ミリ秒では手元で接続がすぐ 0 本になり時間切れにならなかった。上限 500 ミリ秒と、待ちの条件を一時的に満たせない形（== -1）にして時間切れを起こした。確かめた後に git checkout で戻した。 (learned 2026-09-29) \n- 警報が鳴ることは、しきい値を 50 ms に下げた警報の決まりの写しを使い捨ての環境に読み込ませて確かめた（Q1: A）。本当に遅い応答を作るより早く PC の負荷も小さいが、しきい値の値そのもので鳴ることは確かめていない。途中で ms-check-p95 のしきい値が 1000 ms ではなく 300 ms（バケットの境界に無い）と分かり、要件の前提 A3 と食い違った。 (learned 2026-09-29) \n- 表示の設定を画面の操作の直後に同期で確かめるテストが、負荷の高い CI で2件（ShellLayout・PreferencesPage）落ちた。描画の後の効果（useEffect）で反映される値は waitFor で待って確かめる。同じ形の RegistrationPage も合わせて直した。 (learned 2026-09-29) \n- Delivery Planning でのカバレッジの実測は、./gradlew verify 全体ではなく :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport で行い、jacocoTestReport.xml から手を入れる見込みのパッケージの値を読む（user-admin で約 5 分）。 (learned 2026-10-01) \n- 接続プールの見積もりを確かめる負荷の試験には、上限に届く形（プールの上限を下げた場面など）を含める。上限に届かない負荷では、見積もりが誤っていても合格する（user-admin の U3 の NFR 要件のレビュー R-02）。 (learned 2026-10-01) \n- 画面のはみ出しの確かめ（E2E・axe）では、画面全体の横のスクロールだけでなく、開いたメニュー・ポップアップなど画面に固定で置く部品が画面の中に収まることも確かめる。表の右端に置く make-you-chic-ui の Dropdown は placement に bottom-end を指定する（user-admin の配備の後のスモークテストで、行の「操作」のメニューが右へはみ出していたのを E2E 120 が拾えなかった）。 (learned 2026-10-03) \n- k6 の http_req_duration が、colima の VM の時計が負荷の間にホストより約 2 秒ずれて合わせ直されるため一部崩れた（待ちの最小 0 ms、繰り返しより長い約 3 秒の値）。1回の繰り返しが要求1つの loginSuccess では、単調な時計の iteration_duration とサーバー側の最大が合うため、判定は iteration_duration で行い、http_req_duration は並べて記録した。前の Intent の 939.6 ms も同じ影響を受けていた可能性がある。 (learned 2026-10-04) \n- 1〜6回目で繰り返しの時間の p95 が2回 1 秒を超え、依頼者が VM の時計を合わせて測り直しを求めた（F1・F2 の Other）。測り直しの7〜9回目は 1 秒を下回ったため、1〜6回目を FR2.2a の条件がそろわない回として外した（承認の場で確かめる）。時計のずれを ssh の前後のホスト時刻の中間と比べる誤った測り方で 2.2 秒と読み、測り方を直して範囲（VM−終わり、VM−始め）で記録し直した。配備したアプリは2回止めた（約 12 分と約 4 分）。 (learned 2026-10-04) \n- k6 の判定は1回の繰り返しに要求1つの場面の iteration_duration で行い、トークンは setup() で取って場面を 3 分にした。時計のずれに強い代わりに、操作が2つ以上の場面は op のタグが iteration_duration に付くかを台本で確かめる必要がある（group のレビューの R-01 を先に避けた）。 (learned 2026-10-05) \n- 上限を下げた k6 の場面を置かず、接続の数は結合テスト NavigationConnectionUsageIT で決定的に確かめる。読み取りだけで1要求1本のため詰まりが起きず、role のレビューの R-02 の「k6 では見積もりの誤りを見分けにくい」を避けられる。代わりに、負荷の下での待ちの長さは測らない。 (learned 2026-10-05) \n- 違反のテストは先の側を確定させてから書く待たない違反、上限切れは H2 の上限まで放さない形にした（Q3: A）。時間の境に合否を預けない代わりに、待った後の違反の経路は区分の単体テストだけで確かめる。 (learned 2026-10-06) \n- 長いテスト（verify・E2E・k6）は caffeinate -dims で包み、流す前に pmset -g assertions で眠らない状態か、流した後に pmset -g log で Sleep・DarkWake が無かったかを確かめ、入っていたら結果を使わずに流し直す。DarkWake の Thermal Emergency のスリープは caffeinate -i では止められず、role-menu の B8 で画面のテストが約 660 秒止まって時間切れになった。 (learned 2026-10-13) \n- コード生成の計画の段で、変える型・表・移行の番号・API・画面の骨組みを使っている既存のテストと E2E を検索で洗い出し、影響の範囲に入れる。role-menu では移行の最後の版を前提にしたテスト（AuditMigrationCompatibilityIT）、SQL の文の数を数えるテスト（GroupAdminListQueryCountIT）、狭い幅のはみ出し（E2E の 375px）で計画の外の失敗が起き、生成が止まった。 (learned 2026-10-13) \n- 木の画面の単体テスト・E2E・スモークテストでは、本物と同じ形の応答（子の有無の印を含む）を使い、すべての段の開閉を押して確かめる（role-menu の配備の後のスモークテストで、「自分の権限を見る」の木でテーブルの開閉を押すと自分自身を子に持って固まる不具合が見つかった。単体の見本のテーブルは子が無い形で本物と違い、E2E は選ぶだけで開閉を押していなかった）。 (learned 2026-10-14) \n- 画面の不具合を使い捨ての環境で再現するときは、依頼者がした操作（どのボタン・どの開閉を押したか、画面の文言とその場所）を先に聞き、同じ操作を試す。role-menu の S8 で、選ぶだけで開閉を押さずに再現せず「環境の差」と誤って見立て、依頼者の再試行で条件が分かった。 (learned 2026-10-14) \n- k6 の場面の setup は、前の場面が残した状態（ロールの割り当てなど）に頼らず、自分で前提を作るか片付けてから始める形で書く（role-menu の Performance Validation で、workRoleRead・myPermissionsTree・roleAssignmentsRead が前の場面の割り当てで 409 ROLE_NO_CHANGE や確かめの不一致になり、割り当てを外して流し直した）。 (learned 2026-10-15) \n- 接続の待ちの最大のように数 ms の目標は1回の値が揺れるため、合否の回を複数回流すことを負荷の試験の計画に入れる（role-menu の U4-NFR2.7b で1回目 23.55 ms、作り直した流し直しで 8.55 ms となり、原因を確かめないまま Not Met で持ち越した）。 (learned 2026-10-15) \n- 大きな入力（10 MiB の DSL の投入など）のヒープの見積もりは、設計の段で試しの測定で裏付ける（role-menu の Performance Validation で GC の後でも 573〜746 MB あり、見積もりの 256〜384 MB を超えた。dslMixed の memory.peak は上限 2g の 87%）。 (learned 2026-10-15)"
    }
  ],
  "obligations": {
    "strategy": "minimal",
    "strategy_volume": [
      "One verifiable test per requirement at the narrowest effective level.",
      "At least one happy-path unit test per component.",
      "Unit tests are the default; a bugfix/security scope floor may require an integration or E2E regression when that is the narrowest level that reproduces the defect."
    ],
    "scope_floor": [
      "Include a targeted regression for the bug or vulnerability.",
      "Keep the existing test suite green."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "test-after",
    "runner_step": "Verify the existing test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Verify the existing test runner/configuration and record the exact unit-scoped command.",
      "Data model / database behavior - implement.",
      "Data model / database behavior - write and run its tests after implementation.",
      "Repository / data access - implement.",
      "Repository / data access - write and run its tests after implementation.",
      "Business logic - implement.",
      "Business logic - write and run its tests after implementation.",
      "API / endpoint - implement.",
      "API / endpoint - write and run its tests after implementation.",
      "Frontend behavior - implement.",
      "Frontend behavior - write and run its tests after implementation.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:a5cbb088ed4f37f63de2d657259244bc33b9637f24304f4383c3dfdaaeac6a01",
  "contract_sha256": "sha256:aa86a187ed0e2f248826798bb387224fa0fc27ba20f887f210bf3c034d1b4efc"
}
```

## 8. 要件と Step の対応

| 要件 | Step |
|---|---|
| FR1.1〜FR1.4 | 1〜12 |
| FR2.1〜FR2.5 | 14・15・21・25・26・28 |
| FR3.1 | 16・18 |
| FR3.2・FR3.3（FR12 の K2） | 19・20・24・25・26 |
| FR4.1・FR4.2 | 17・18 |
| FR5.1〜FR5.3（FR12 の K4） | 22・25・27・28 |
| FR6.1・FR6.2 | 23・25 |
| FR7.1・FR7.2 | 40・41（流すのは Build and Test） |
| FR8.1〜FR8.4 | 41（流すのは Build and Test） |
| FR9.1・FR9.3・FR9.4 | 41（流すのは Build and Test） |
| FR9.2 | 32〜38・42 |
| FR10.1〜FR10.3 | 39 |
| FR11 | この段の外（Deployment Pipeline・Deployment Execution）。D-5 の測り直しを引き継ぐ |
| FR12.1〜FR12.3 | 5節の一覧、22・24・26・27 |
| NFR1 | 10・29・43（各ブランチの統合の前） |
| NFR2 | 4.3節、29・43 |
| NFR3 | 6節 |
| NFR4 | 16・17・33・38 |
| NFR5 | 10・26・29 |
| NFR6 | 4・10 |

## Sources

- 要件: `aidlc/spaces/default/intents/261015-deps-ui-carryover/inception/requirements-analysis/requirements.md`、質問と答え `requirements-analysis-questions.md`（Q1〜Q7）
- 開発担当の読み取り: `aidlc/spaces/default/intents/261015-deps-ui-carryover/inception/reverse-engineering/developer-scan.md`、コード知識ベース `aidlc/spaces/default/codekb/mastersmith2/`
- 前の Intent の持ち越し: `aidlc/spaces/default/intents/261004-role-menu/construction/build-and-test/build-and-test-summary.md` 5.2〜5.4、`test-results.md` 3.2、レビューの記録 `aidlc/spaces/default/intents/261004-role-menu/.aidlc-reviews/code-generation/units/{dsl-v2,navigation,cross-cutting}/152fd96411988a79/1.json`
- make-you-chic-ui: `git -C vendor/make-you-chic-ui show a9309c4`（Sidebar・Topbar・AppShell・Dropdown の差）、`aidlc/spaces/default/intents/261004-role-menu/make-you-chic-ui-request-3.md`
- コードの読み取り（この段）: `ShellLayout.tsx`・`WorkRoleSwitcher.css`・`SharedTreeNodeItem.tsx`・`registry/types.ts`・`validateRegistrations.ts`・3つの `useSlowNotice.ts`・`features/dsl/messages.ts`、`DslError.java`・`DslErrorItem.java`・`DslStatus.java`・`DslStartupLoader.java`・`DslLifecycle.java`・`SafeYamlParser.java`・`YamlLimits.java`・`DslFormat.java`・`DslMessageKeys.java`・`DslYamlWriter.java`・`DslTreeBuilder.java`・`RoleTransferWriter.java`・`RoleTransferLimits.java`・`V11__u4_role.sql`、navigation の record と `MenuFilter.java`、`TraceAspect`、`RoleBusyLogTraceIT`・`GroupBusyLogIT`、`ApiAccessArchitectureTest`・`ApiAccessConsistencyIT`、`perf/k6/scenarios.js`、`backend/build.gradle.kts`（`packagesJudgedByTotal`・`resolveAndLockAll`）、`build.gradle.kts`（`e2eTest`）、`.github/dependabot.yml`・`.github/workflows/ci.yml`、イメージの固定先（`grep`）
- 決まり: `aidlc/spaces/default/memory/team.md`（Way of Working・Testing Posture・Code Style・Deployment）、`aidlc/spaces/default/memory/project.md`（Forbidden・Mandated・Change Control・Testing Posture・Corrections の学び）、`aidlc/spaces/default/memory/phases/construction.md`

## Assumptions & Open Questions

- 前提 A1（jdk の同じ版のタグ）は Step 2 で確かめる。A2 は 4.4節で確かめ、`containsCurrent` が必須になることを影響の範囲に入れた。A3 は Build and Test。A4 は 5節で全件を特定し、範囲の外の候補に印を付けた。
- 7節の D-1〜D-13 を、計画の承認の前に依頼者に尋ねる。答えで Step の中身（19・20・22・24・33〜37 など）が変わる。
- `MIN_ENDPOINTS` の新しい値（今の口の数）と、配備済みの DSL の最も長い行は、生成の時に数える。
