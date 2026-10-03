# テストの結果（test-results）

Intent `260930-user-admin`（U1〜U5、Bolt B1〜B5）の Build and Test の実測です。段全体のレビューの R-03（各単位の `code-summary.md` にレビューの後の変更と関門の結果が反映されていない）を受けて、各 Bolt の統合の前の関門・統合の後の CI・最新の実測を、この文書の1か所にまとめます。

この段では検査を流し直していません。依頼者の決定（`build-and-test-questions.md` の Q1: A・Q2: A）で、次の実測をこの段の実測とします。

- バックエンド・osvScan: `develop` の先頭 `7689ade` とアプリのソースが同じ、U3 の直しの作業ブランチ（`b126bdc`）の clean 付きの実測。`7689ade` は `b126bdc` に記録（`aidlc/` の下）だけを足したコミットです。
- 画面のテストと E2E: B5 の統合の前の関門（`81d2423`）の実測。その後の変更（`b126bdc`）はバックエンドの監査の組み立てに失敗したときのログの値だけで、画面と API の応答は変わっていません。

## 1. 結果の要約

| 項目 | 結果 | 時点 |
|---|---|---|
| ビルドと1コマンドの検査（`:backend:cleanTest :backend:cleanIntegrationTest verify`） | **成功**（10 分 27 秒） | `b126bdc` |
| バックエンドの単体 | **1508 件**、失敗 0・飛ばし 0 | `b126bdc` |
| バックエンドの結合 | **689 件**、失敗 0・飛ばし 0（対象DB のテストも飛ばしていない） | `b126bdc` |
| バックエンドのカバレッジ（全体） | 行 **98.9%**（6278/6348）・分岐 **94.8%**（2335/2462） | `b126bdc` |
| 画面のテスト（Vitest） | **109 ファイル・955 件**、すべて成功 | `81d2423` |
| 画面のカバレッジ（全体） | 行 **97.42%**（2881/2957）・分岐 **92.85%**（1870/2014） | `81d2423` |
| 依存関係の脆弱性（`osvScan --rerun-tasks`） | **成功**。失敗の条件に当たるもの 0 件、警告 16 件（すべて npm の開発用） | `b126bdc` |
| E2E（`e2eTest`） | **13 ファイル・152 件がすべて expected**（飛ばし 0・想定外 0・不安定 0） | `81d2423` |
| 初回の JavaScript（gzip） | 125.5 KB（目安 500 KB の内） | `81d2423` |
| CI（B1〜B5 の統合の後） | すべて success（6節） | 各 Bolt |
| CI（承認の後の push、run 37106195579） | **success**（約 15 分。失敗したテストなし） | `7689ade` |

## 2. ビルドと1コマンドの検査（`b126bdc`）

- コマンド: colima の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡し、`caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`。
- 結果: BUILD SUCCESSFUL、10 分 27 秒。書式・リンタ・ライセンスヘッダー・ビルド・単体・結合・カバレッジ・Gitleaks・SpotBugs の関門・成果物の段が通った。
- 続けて `caffeinate -i ./gradlew osvScan --rerun-tasks` が成功（失敗の条件 0 件・警告 16 件）。
- 変更は `AuditEventFactory`・`AuditEventListener` と `AuditEventListenerTest`（再現のテスト2件を足した）の3ファイルだけです（`git show --stat b126bdc`）。
- 出典: この段の依頼で渡された実測（依頼者との会話の記録）と、`construction/code-generation/gate-decisions.md` の6節。

### 2.1 カバレッジ（バックエンド、パッケージごと）

下限は全体とパッケージごとに行 80%・分岐 70% です。`packagesJudgedByTotal`（全体の合計で判定する既存のパッケージ）は、この Intent で 12 から 7 に減りました（B1 で `auth.domain`・`auth.repository`・`access.domain`、B4 で `common.error.web`・`common.observability` を外した。`backend/build.gradle.kts`）。一覧を増やした変更・計測の除外を増やした変更はありません。

`b126bdc` で変わったのは `audit.domain`・`audit.service` だけです。そのほかのパッケージは、バックエンドのソースが同じ B4 の流し直した関門（2026-10-03 01:31〜01:41、`b126bdc` の前で最後のバックエンドの変更）の値を写します（B5 はバックエンドを変えておらず、B5 の関門の全体の値 行 98.90%（6276/6346）・分岐 94.83%（2331/2458）も B4 と同じ）。

| パッケージ | 行 | 分岐 | 判定の形 | 値の時点 |
|---|---|---|---|---|
| `audit.service` | 100.0% | 86.1% | パッケージごと | `b126bdc`（B4 の関門は 100.0%・85.3%） |
| `audit.domain` | 99.6% | 98.4% | パッケージごと | `b126bdc`（B4 は 99.6%・98.3%） |
| `useradmin.web` | 98.8%（83/84） | 93.8%（15/16） | パッケージごと（新しい） | B4 |
| `useradmin.service` | 99.3%（141/142） | 95.9%（47/49） | パッケージごと（新しい） | B4 |
| `useradmin.domain` | 100.0%（83/83） | 100.0%（52/52） | パッケージごと（新しい） | B4 |
| `common.persistence` | 100.0%（26/26） | 100.0%（20/20） | パッケージごと（新しい） | B4 |
| `invitation.lock` | 100.0%（21/21） | 100.0%（2/2） | パッケージごと（新しい） | B4 |
| `common.paging` | 100.0%（13/13） | 100.0%（10/10） | パッケージごと（新しい） | B2 |
| `common.observability` | 97.7%（126/129） | 95.5%（42/44） | パッケージごと（B4 で一覧から外した） | B4 |
| `common.error.web` | 97.0%（196/202） | 88.9%（88/99） | パッケージごと（B4 で一覧から外した） | B4 |
| `auth.domain` | 98.2%（112/114） | 100.0%（38/38） | パッケージごと（B1 で一覧から外した） | B4 |
| `auth.repository` | 100.0%（49/49） | 100.0%（8/8） | パッケージごと（B1 で一覧から外した） | B4 |
| `access.domain` | 100.0%（53/53） | 100.0%（29/29） | パッケージごと（B1 で一覧から外した） | B4 |
| `auth.service` | 99.3%（265/267） | 93.0%（80/86） | パッケージごと | B4 |
| `auth.web` | 100.0%（123/123） | 95.5%（21/22） | パッケージごと | B1（R-01・R-02 の直しの後） |
| `user.domain` | 99.6%（238/239） | 97.8%（135/138） | パッケージごと | B4 |
| `user.repository` | 100.0%（28/28） | 100.0%（6/6） | パッケージごと | B4 |
| `user.service` | 99.7%（306/307） | 95.1%（116/122） | パッケージごと | B4 |
| `user.web` | 96.5%（55/57） | 84.6%（11/13） | パッケージごと | B1（R-01・R-02 の直しの後） |
| `invitation.domain` | 98.3%（233/237） | 96.4%（107/111） | パッケージごと | B2 |
| `invitation.service` | 100.0%（331/331） | 93.5%（87/93） | パッケージごと | B2 |
| `invitation.web` | 97.3%（107/110） | 87.0%（20/23） | パッケージごと | B2 |
| `invitation.repository` | 100.0%（2/2） | 分岐なし | パッケージごと | B4 |

- どのパッケージも下限を満たしました。全体の合計で判定している7つのパッケージのうち、単独で下限を下回るのは `common.health`（行 79.2%、この Intent で手を入れていない）だけです。
- 「値の時点」が B1・B2 のパッケージは、その後の Bolt で本体を変えていないものです。この段ではパッケージごとの値を `b126bdc` で取り直していません（依頼者の決定 Q1: A。全体の値と `audit.*` は `b126bdc` の実測）。

### 2.2 カバレッジ（画面、`81d2423`）

| 範囲 | 行 | 分岐 |
|---|---|---|
| 画面の全体（`frontend/coverage/coverage-summary.json`） | 97.42%（2881/2957） | 92.85%（1870/2014） |
| `features/useradmin` の下の合計（`testing/` を含む） | 96.49%（467/484） | 92.17%（365/396） |

- 下限（行 80%・分岐 70%）を満たします。B2 の基準（行 97.61%・分岐 93.01%）からの下がりは 0.19・0.16 ポイントです。
- 記録のための目安（行・分岐とも 90%）を分岐だけ下回るファイルが4つあります: `useUserAdmin.ts` 85.06%・`UserRowActions.tsx` 83.33%・`lockedUntil.ts` 87.5%・`profileInput.ts` 75.0%（部品の側の守りで画面からは届かない参照の守り、`??` の右辺）。下限を満たすため、コード生成の承認の場で受け入れました（`construction/code-generation/gate-decisions.md` の3節）。
- U4 で作ったファイル（`adminForbidden.ts`・`forbiddenHeading.ts`・`AdminForbiddenView.tsx`・`useApplyOwnProfile.ts`）は行・分岐とも 100%、`AdminForbiddenProvider.tsx` は行 100%・分岐 95%（B2 の関門）。

### 2.3 静的な検査と成果物

| 検査 | 結果 |
|---|---|
| SpotBugs＋FindSecBugs（`spotbugsGate`） | 通過。新しいコードの指摘は priority 2・3 の警告だけ（`EI_EXPOSE_REP2`・`CT_CONSTRUCTOR_THROW`・`SPRING_ENDPOINT`・`SERVLET_HEADER_USER_AGENT`）。`backend/config/spotbugs-exclude.xml` は Intent の間で変わっていない |
| Gitleaks（`gitleaksScan`） | 通過（no leaks found）。`.gitleaks.toml` は Intent の間で変わっていない |
| OSV-Scanner（`osvScan --rerun-tasks`） | 失敗の条件 0 件・警告 16 件。走査したパッケージは `backend/gradle.lockfile` 252・`frontend/package-lock.json` 382・`vendor/make-you-chic-ui/package-lock.json` 404。警告はすべて npm の開発用（`braces@3.0.3` が frontend と vendor に各1、vendor の `brace-expansion@5.0.9` 3・`undici@8.10.0` 11。B5 の関門の内訳） |
| 初回の JavaScript（`frontendBundleSize`、`81d2423`） | 125.5 KB（gzip、`index-*.js` 108.8 KB・`useTranslation-*.js` 16.7 KB）。U4 の前 123.1 KB → U4 の後 122.7 KB → U5 (2) 122.8 KB → U5 (3) 125.5 KB |
| 依存の差 | `gradle/libs.versions.toml`・`backend/gradle.lockfile`・`frontend/package.json`・`frontend/package-lock.json` は Intent の始め（`ded2653` の親）から `7689ade` まで差が無い。足したスキーマは `V9__u1_user_suspension.sql` だけ（`git diff --stat`、読み取り） |
| サブモジュール | `vendor/make-you-chic-ui` は `3d9521a`（U5 で `077f5b4` から上げた）、`vendor/java-mustache-processor` は `8d44c36`（0.1.0、変えていない）。`vendorUnchanged` は B5 の関門の verify で通過 |

## 3. 画面からの一連の操作（E2E、B5 の関門 `81d2423`）

- コマンド: Mailpit（`mastersmith-mailpit-1`、もとから動いていた。healthy、`/api/v1/info` は 200）で `caffeinate -i ./gradlew e2eTest`。BUILD SUCCESSFUL、6 分 13 秒（Playwright は 6.2 分）。
- json の `stats`: expected **152**・skipped 0・unexpected 0・flaky 0、約 371.6 秒。失敗と再実行は無し。

| ファイル | 件数 | 結果 |
|---|---|---|
| 010・020・030・040 | 2・2・1・1 | expected |
| 050・060・070・080 | 21・21・20・21 | expected |
| 090・100 | 1・20 | expected |
| 110（U5、代表の流れ E2E-M9） | 1 | expected（約 7.0 秒）。`skip-reason` の注記なし。注記 `user-admin-flow` は lockAttempts 5・管理者の画面の問題 0・CSP の違反 0 |
| 120（U5、20 組の検査と測り） | 21 | expected。20 組 × 12 回 ＝ 240 回の検査で axe の違反 0（規則なし）・はみ出し 0。どの組も routeSeen 9・blocked（GET 以外の打ち切り）0・failedReplies 2（409 と通信の失敗）・除いたコンソールの表示 2・残りの問題 0・CSP の違反 0。120 の全体は約 137.4 秒 |
| 130（U4、S6 の 20 組の検査） | 20 | expected |

### 3.1 画面の時間（120 の添付 `user-admin-screen-ms`、記録のみ）

| 場面 | 1回目 | 2〜5回目 | 最大 | 目標 | 時間の中身 |
|---|---|---|---|---|---|
| `list` | 98 ms | 103・63・48・46 ms | 103 ms | 2000 ms（U5 の NFR5.1） | 本物の一覧の API を差し替えの口を通して読む（口の上乗せを含む） |
| `nextPage` | 65 ms | 57・79・80・60 ms | 80 ms | 1500 ms（U5 の NFR5.2） | 見本の応答での描画の時間（API の時間を含まない） |

- 時間はテストの成否にしません。E2E の WAR は PC の上の一時の内部DB で、配備したアプリと条件が違います。本番での判定は `Unverified`（持ち主は performance-validation・observability-setup・feedback-optimization）です。

### 3.2 報告に秘密が入っていないことと片付け

- 報告の部品: 「E2E の報告に残してはならない値は含まれていません（値の種類 7・確かめたファイル: json の報告 1・json の報告の添付 299・trace 0・失敗の画面の写し 0・そのほか 0・前の html の報告 0・見つかった件数 0）」。
- `frontend/playwright-report/` は作られませんでした。記録した後に `frontend/test-results/` を中を開かずに消しました（ファイル 2・ディレクトリ 106）。共有していません。json から読んだのは `stats`・テストの題・状態・時間・注記と添付 `user-admin-screen-ms` だけです。
- この段では E2E を流し直していないため、片付ける報告はありません。

## 4. Bolt ごとの統合の前の関門（段全体のレビュー R-03 のまとめ）

どれも colima の設定を渡した `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` と、`osvScan --rerun-tasks`、E2E の結果です。

| Bolt・時点 | verify の時間 | 単体・結合 | バックエンドの全体 行・分岐 | 画面 | osvScan | E2E | 出典 |
|---|---|---|---|---|---|---|---|
| Intent の前の基準（`782a9f6`） | 6 分 26 秒 | 1243・566 | 98.8%・94.4% | 91 ファイル・732 件 | — | — | U1 の `code-summary.md` 2節 |
| B1（U1、R-01・R-02 の直しの後） | 6 分 14 秒 | 1283・587 | 98.8%（5653/5720）・94.5%（2069/2190） | 91・732 | 失敗 0・警告 14 | 10 ファイル・110 件 expected（225 秒） | U1 の `code-summary.md` 2節・5節 |
| B2（U2・U4、090 の直しの後のやり直し） | 6 分 17 秒 | 1287・587 | 98.8%（5652/5719）・94.5%（2069/2190） | 95・801、行 97.61%・分岐 93.01% | 失敗 0・警告 14 | 11 ファイル・130 件 expected（249.5 秒） | U4 の `generation-notes.md`「090 の直しと関門のやり直し」 |
| B3（U3 前半、Step 15） | 6 分 37 秒 | 1355・620 | 98.9%（5883/5951）・94.6%（2156/2280） | 95・801 | 失敗 0・警告 14 | 流していない（E2E の条件に当たらない） | U3 の `code-summary.md` 2節 |
| B4（U3 後半、流し直した関門） | 10 分 34 秒 | 1506・689 | 98.9%（6276/6346）・94.8%（2331/2458） | 95 ファイル通過 | 失敗 0・警告 14 | 11 ファイル・130 件 expected（約 259.6 秒） | U3 の `code-summary.md` 2節 |
| B5（U5、`81d2423`、C2′ の前） | 9 分 2 秒 | 1506・689 | 98.90%（6276/6346）・94.83%（2331/2458） | 109・955、行 97.42%・分岐 92.85% | 失敗 0・警告 16 | 13 ファイル・152 件 expected（約 371.6 秒） | U5 の `generation-notes.md`「Step 22（C2′ の前）」 |
| U3 の直し（`b126bdc`） | 10 分 27 秒 | 1508・689 | 98.9%（6278/6348）・94.8%（2335/2462） | （画面は変えていない） | 失敗 0・警告 16 | 流していない（Q2: A） | `construction/code-generation/gate-decisions.md` 6節、この段の依頼 |

- B4 の1回目の関門（レビューの指摘への対応の後、2026-10-03 01:18〜01:26）は、結合テスト 689 件のうち `mail/config/MailConfigurationIT` の1件が、別のテストの文脈が出した OTLP の指標の送信の WARN を取り込んで落ちました（変更の経路の外。単独の流し直しでは 7 件通過）。依頼者の決定で `team.md` の「不安定なテストと CI の失敗」の決まりで扱い、流し直して通りました（不安定と確かめられていない扱い）。その後、B5 の関門・`b126bdc`・B5 の CI では落ちていません。出力を捕まえる範囲の弱さは後の Intent へ持ち越しました（7節）。
- B2 の1回目の関門では、E2E の 090 の1件が U4 の決まり（管理者でない利用者の管理の画面は S6）どおりに画面が変わったため、古い期待で決まって失敗しました（不安定ではない）。依頼者の決定で 090 の期待を S6 に直し（U4 の N-16）、関門をやり直しました。
- verify の時間: B3 の基準（6 分 37 秒）から `b126bdc` の 10 分 27 秒まで +3 分 50 秒で、U3 の計画 4.6 の許容（+5 分以内）に収まります。前の Intent（`260925-user-management`）の Build and Test の 6 分 28 秒からは +3 分 59 秒で、主に排他の上限切れを起こす結合テスト（1件ごとに約 3 秒待つ）と新しいテストの文脈の起動の分です（U3 の計画 4.6 の見込み 9〜11 分の内）。

## 5. 負荷の試験の台本（k6）

- `perf/k6/scenarios.js` と `perf/README.md` は B4（`4bd600d`）の後に変わっていません（`git log 4bd600d..7689ade -- perf/` が 0 件）。そのため、B4 の Step 39 の `k6 inspect --include-system-env-vars`（`grafana/k6:2.3.0`）の結果をこの段の結果とします。
  - `userAdminList`（constant-vus 10・1 分、閾値 `checks`・`{name:userAdminList}`）、`userAdminProfile`（閾値 2 つの name）、`userAdminOps`（per-vu-iterations 10 回、閾値 5 つの操作の name）、`userAdminSuspendWorst`（per-vu-iterations 10 回）、`userAdminPool`（constant-vus 10、閾値は `checks` だけ）の5つがすべて読み込めた。
  - `VUS=5` の `userAdminOps` は 20 回になることも確かめた。既存の `health`・`invitationList` も変わらず読み込めた。
- 手順書の試験用のデータを入れる SQL は、捨ての H2 のファイルで件数（1,000・管理者 10・対象 10・悪い側 100・未無効のトークン 10,000・全体 110,000）が意図どおりであることを確かめ済みです（B4 の Step 39）。
- 測定（p95・接続プール・規模）は performance-validation の段で行います。

## 6. CI（GitHub Actions）

| 統合 | `develop` のコミット | CI の結果 | 時間 | 出典 |
|---|---|---|---|---|
| B1（U1） | squash `ded2653` | success | — | `construction/code-generation/gate-decisions.md` 1節 |
| B2（U2・U4） | `9b42c2d`・`1de9ef6`（単位ごとの squash） | success | — | 同上 |
| B3（U3 前半） | squash `f6bf385` | success（run 37015756225） | 9 分 21 秒 | 同上、U3 の `code-summary.md` 2節 |
| B4（U3 後半） | squash `4bd600d` | success（run 37036697597） | 12 分 29 秒 | 同上 |
| B5（U5） | fast-forward `17af97d`〜`afd69c3`（記録 R4 `335aba6`） | success（run 37080444724） | 約 12 分 | 同上 |
| U3 の直しとコード生成の承認 | `b126bdc`・`7689ade`（push 済み） | success（run 37106195579）。ジョブ `./gradlew verify` は success、`リリースに WAR を添付する` は skipped（タグでないため）。失敗したテストは無く、`MailConfigurationIT` も落ちていない | 約 15 分（2026-10-03T07:23:38Z〜07:38:52Z） | オーケストレーターが確かめた結果 |

- CI は統合の後の再確認として、手元と同じ `./gradlew verify` を流します（サブモジュールを固定先で取得し、lockfile どおりに入れる）。E2E は意図して CI の外に置いています（`team.md` の Testing Posture）。
- CI の時間は U3 の計画 4.6 の許容（30 分以内）に収まっています（最大は push の後の約 15 分）。
- run 37106195579 が失敗したときは、`team.md` の「不安定なテストと CI の失敗」の決まりで扱います。`MailConfigurationIT` が落ちたときは二度目として、原因を直すまで次へ進みません（U3 の `code-summary.md` 8節）。

## 7. 後の Intent への持ち越しと、この段で足したもの

- 持ち越しの一覧は `construction/code-generation/gate-decisions.md` の4節のとおりです（N-19 閉じた後のフォーカス、Q-H 一意の制約の違反の例外の文、出力を捕まえるテストの範囲の弱さ、V9 の後の戻し、8KB を超える要求の HTML の 400、U3 R-02 の確かめ直しの隙、`ms-pool-pending` の式の見直し）。4節の「U3 監査の組み立ての失敗のログ」は `b126bdc` で解きました。
- **この段で足した持ち越し**（依頼者の決定 Q3: A）: U5 の2回目のレビューの R-03（Minor）。送信中は氏名の欄が読み取り専用になったが、言語の選択（ラジオ）は送信中も変えられ、送った値と画面の値がずれうる。N-19 と同じ画面の直しとして後の Intent で扱います（`EditProfileDialog`・`useUserAdmin`）。
- **この段で足した持ち越し**（依頼者の決定、検査の後の確かめ）: AC2.2.6 の、管理者の印を外した直後の要求の監査の行を1つのテストで続けて確かめるテスト（今回は2つのテストの組み合わせで網羅と判定した。`cross-unit-traceability.md`）。

## 8. 目標の判定（要約）

目標ごとの判定は `build-and-test-summary.md` の Target Verification Matrix にあります。要約は次のとおりです。

- 判定の件数: 153 件。Met 130 件・Not Met 1 件・Unverified 22 件。
- Not Met: U5-NFR7.1（WCAG 2.1 AA のうちフォーカスの戻し先。実際のブラウザでは閉じた後のフォーカスが行の「操作」に戻らず body に移る。N-19、後の Intent への持ち越しとして承認済み）。
- Unverified: 性能・接続プール・規模（performance-validation）、観測・SLO（observability-setup）、移行の事後の裏付けと戻しの手順（deployment-pipeline・deployment-execution）、画面の時間の本番での判定。

## Sources

- `aidlc/spaces/default/intents/260930-user-admin/construction/build-and-test/build-and-test-questions.md`（Q1〜Q3 とまとめの確認）
- `aidlc/spaces/default/intents/260930-user-admin/construction/code-generation/gate-decisions.md`（1節・4節・6節）
- 各単位の `code-summary.md`・`generation-notes.md`（`aidlc/spaces/default/intents/260930-user-admin/construction/u1-user-suspension/` 〜 `u5-user-admin-ui/` の `code-generation/`）
- `backend/build.gradle.kts`（`packagesJudgedByTotal`）、`git show --stat b126bdc`・`git log`・`git diff --stat ded2653~1 7689ade`・`git submodule status`（読み取り）
- `aidlc/spaces/default/memory/team.md`（Testing Posture・Way of Working）・`aidlc/spaces/default/memory/project.md`（Testing Posture）

## Assumptions & Open Questions

- `b126bdc` の verify の実測（件数・全体のカバレッジ・`audit.*` の値・時間）と osvScan の結果は、この段の依頼で渡された値です。JaCoCo の報告の全体は、この段では読み直していません（手元の報告は後の実行で上書きされうるため）。