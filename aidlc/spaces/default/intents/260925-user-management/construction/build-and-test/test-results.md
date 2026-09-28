# テストの結果（test-results）

Build and Test の段で流した検査の実測です（2026-09-28）。数値は、すべてこの段で実測したものです。

- 対象：develop の `66fe981` に、この段の未コミットの変更（`perf/k6/scenarios.js`・`perf/README.md`・`README.md` の表）を加えた作業フォルダ
- 環境：colima の VM（CPU 4・メモリ 6GiB）。配備したアプリ・Mailpit・PostgreSQL の対象DB は動かしたまま

## 1. 結果の要約

| 検査 | 結果 |
|---|---|
| 手元の `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` | **成功**（6分28秒） |
| 手元の `./gradlew e2eTest`（Mailpit を起動） | **成功**（3分25秒、90 件通過） |
| k6 の台本の読み込み（24 場面） | **成功** |
| U7 のときに1回落ちたテストの繰り返し（50 回） | **50 回通過**。不安定と確かめられていない |
| CI（GitHub Actions、`66fe981`、run 36433076151） | **失敗**（1回目・2回目とも、別々のテストの時間切れ） |
| 目標の判定（249 件） | Met 208・**Not Met 5**・Unverified 36（`build-and-test-summary.md` の Target Verification Matrix） |

この段の失敗の条件（コマンドの失敗、または Not Met・Unverified の目標がある）に当たります。扱いは 8 節のとおり、依頼者に諮ります。

## 2. ビルドと1コマンドの検査（手元）

- コマンド：`DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock" TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`
- 結果：`BUILD SUCCESSFUL in 6m 28s`
  - 比べる相手は各単位のコード生成の実測で、B3 の後が 5 分 43 秒、U5 が 6 分 2 秒、U6 が 6 分 5 秒、U7 が 6 分でした。
- すべての段が通りました（`verifyPrepare`〜`verifyArtifact`）。SpotBugs・OSV-Scanner・Gitleaks の `verifySecurity` を含みます。

| テスト | 件数 | 失敗 | エラー | 飛ばし |
|---|---|---|---|---|
| バックエンドの単体テスト（`*Test`） | 1,234 | 0 | 0 | 0 |
| バックエンドの結合テスト（`*IT`。対象DB 3種類と SubEtha SMTP を含む） | 562 | 0 | 0 | 0 |
| フロントエンド（Vitest、91 ファイル） | 732 | 0 | — | 0 |

### 2.1 カバレッジ

| 対象 | 行 | 分岐 | 下限 |
|---|---|---|---|
| バックエンドの全体（JaCoCo） | 98.8%（5,600/5,669） | 94.4%（2,050/2,172） | 行 80%・分岐 70% |
| フロントエンドの全体（`@vitest/coverage-v8`） | 97.44%（2,322/2,383） | 92.67%（1,443/1,557） | 行 80%・分岐 70% |

- バックエンドのパッケージは 47 あります。そのうち 35 はパッケージごとに下限を当てています。下限を下回ったパッケージはありません。
- 全体の合計で判定する `packagesJudgedByTotal` は 12 です。team.md の Testing Posture にある 22 から減っています。この Intent で手を入れたパッケージを一覧から外し、パッケージごとの下限に戻したためです。

| パッケージ（`cherry.mastersmith.` を略） | 判定 | 行 | 分岐 |
|---|---|---|---|
| `access.domain` | 全体の合計で判定 | 100.0% (52/52) | 100.0% (28/28) |
| `access.service` | 全体の合計で判定 | 100.0% (11/11) | n/a |
| `access.web` | パッケージごと | 100.0% (73/73) | 93.8% (15/16) |
| `appearance.service` | パッケージごと | 100.0% (49/49) | 100.0% (12/12) |
| `appearance.web` | パッケージごと | 100.0% (13/13) | n/a |
| `audit.domain` | パッケージごと | 99.5% (212/213) | 97.7% (43/44) |
| `audit.repository` | 全体の合計で判定 | n/a | n/a |
| `audit.service` | パッケージごと | 100.0% (148/148) | 84.4% (27/32) |
| `auth.domain` | 全体の合計で判定 | 97.9% (94/96) | 100.0% (18/18) |
| `auth.repository` | 全体の合計で判定 | 93.9% (31/33) | 50.0% (1/2) |
| `auth.service` | パッケージごと | 99.0% (199/201) | 91.7% (55/60) |
| `auth.web` | パッケージごと | 100.0% (120/120) | 95.0% (19/20) |
| `common.error.domain` | 全体の合計で判定 | 100.0% (41/41) | 94.4% (17/18) |
| `common.error.service` | 全体の合計で判定 | 100.0% (28/28) | 81.2% (13/16) |
| `common.error.web` | 全体の合計で判定 | 96.9% (190/196) | 88.2% (82/93) |
| `common.health` | 全体の合計で判定 | 79.2% (42/53) | 100.0% (2/2) |
| `common.i18n.domain` | 全体の合計で判定 | 100.0% (38/38) | 89.3% (25/28) |
| `common.observability` | 全体の合計で判定 | 97.5% (115/118) | 94.4% (34/36) |
| `common.security` | パッケージごと | 100.0% (14/14) | 100.0% (6/6) |
| `common.web` | 全体の合計で判定 | 96.5% (110/114) | 85.2% (46/54) |
| `config` | パッケージごと | 98.8% (83/84) | 76.7% (23/30) |
| `dsl.domain` | パッケージごと | 100.0% (236/236) | 94.8% (110/116) |
| `dsl.parse` | パッケージごと | 98.7% (220/223) | 92.1% (117/127) |
| `dsl.service` | パッケージごと | 98.5% (130/132) | 100.0% (52/52) |
| `dsl.validate` | パッケージごと | 97.4% (263/270) | 90.7% (117/129) |
| `dslmanage.domain` | パッケージごと | 100.0% (130/130) | 92.3% (24/26) |
| `dslmanage.generate` | パッケージごと | 99.3% (303/305) | 100.0% (145/145) |
| `dslmanage.repository` | パッケージごと | 100.0% (74/74) | 100.0% (4/4) |
| `dslmanage.service` | パッケージごと | 100.0% (506/506) | 98.5% (130/132) |
| `dslmanage.web` | パッケージごと | 100.0% (131/131) | 86.7% (26/30) |
| `invitation.domain` | パッケージごと | 98.4% (246/250) | 96.7% (117/121) |
| `invitation.repository` | パッケージごと | 100.0% (2/2) | n/a |
| `invitation.service` | パッケージごと | 100.0% (332/332) | 93.5% (87/93) |
| `invitation.web` | パッケージごと | 97.3% (107/110) | 87.0% (20/23) |
| `mail.config` | パッケージごと | 98.0% (97/99) | 96.2% (101/105) |
| `mail.domain` | パッケージごと | 100.0% (68/68) | 98.3% (59/60) |
| `mail.service` | パッケージごと | 100.0% (61/61) | 100.0% (18/18) |
| `mail.template` | パッケージごと | 97.2% (105/108) | 95.7% (44/46) |
| `mail.transport` | パッケージごと | 96.3% (52/54) | 96.7% (29/30) |
| `targetdb.config` | パッケージごと | 99.2% (125/126) | 98.5% (66/67) |
| `targetdb.domain` | パッケージごと | 100.0% (120/120) | 96.9% (62/64) |
| `targetdb.repository` | パッケージごと | 96.0% (119/124) | 87.5% (28/32) |
| `targetdb.service` | パッケージごと | 100.0% (29/29) | 100.0% (18/18) |
| `user.domain` | パッケージごと | 99.5% (199/200) | 97.5% (115/118) |
| `user.repository` | パッケージごと | n/a | n/a |
| `user.service` | パッケージごと | 100.0% (227/227) | 95.5% (84/88) |
| `user.web` | パッケージごと | 96.5% (55/57) | 84.6% (11/13) |

### 2.2 成果物

- 初回に読む JavaScript（gzip）：合計 **123.1 KB**
  - 内訳：`index-*.js` 98.7 KB・`useTranslation-*.js` 16.6 KB・`hooks-*.js` 7.8 KB
  - U7 のコード生成の後と同じ値です。上限は置かないため、記録だけにします（Q4: A）。
- WAR：`backend/build/libs/mastersmith.war`
  - 中に `WEB-INF/classes/mail/templates/invitation_ja.html`・`invitation_en.html` があります。

## 3. 画面からの一連の操作（E2E）と実際のブラウザの検査

- コマンド：`./gradlew e2eTest`（Mailpit を起動した状態。`http://127.0.0.1:8025/api/v1/info` が 200）
- 結果：`BUILD SUCCESSFUL in 3m 25s`、90 件通過、飛ばし 0、想定外 0、不安定 0

| ファイル | 件数 | 時間（秒） |
|---|---|---|
| `010-skeleton` | 2 | 2 |
| `020-auth` | 2 | 3 |
| `030-admin-access` | 1 | 1 |
| `040-dsl-admin` | 1 | 6 |
| `050-display-accessibility` | 21 | 11 |
| `060-invitation-accessibility` | 21 | 52 |
| `070-registration-accessibility` | 20 | 19 |
| `080-preferences-accessibility` | 21 | 92 |
| `090-invitation-registration-flow` | 1 | 9 |

- 検査の部分（050〜080）は計 174 秒でした（080 の測りを含みます）。流れの部分（010〜040・090）は計 21 秒でした。

### 3.1 画面の時間（`frontend/test-results/e2e-results.json` の注記から写したもの）

| 場面 | 5回の値（ミリ秒） | 目標 | 目標以内 |
|---|---|---|---|
| 最初の画面（050、U4 の NFR6.1） | 108・104・103・103・76 | 2,000 | 5/5 |
| 招待の一覧（060、U5） | 108・113・111・121・139 | 2,000 | 5/5 |
| 招待の一覧の次のページ（060、U5） | 79・76・83・63・54 | 1,500 | 5/5 |
| プリファレンスを開く（080、U7） | 103・68・70・104・120 | 2,000 | 5/5 |
| パスワードの変更を開く（080、U7） | 51・49・49・59・49 | 2,000 | 5/5 |
| 保存（080、U7） | 61・55・58・56・61 | 1,500 | 5/5 |
| パスワードの変更（080、U7） | 813・818・812・816・828 | 2,500 | 5/5 |
| 登録の完了の画面（090、U6） | 102・111・102・105・105 | 2,000 | 5/5 |

- 060 の測りの前に、招待を 21 件作りました（送信 21・失敗 0）。
- 080 の本物の応答と見本の形の照合（`realResponseShape`）は真でした。
- 測ったのは、E2E の WAR（PC の上、空の内部DB）です。配備したアプリ（コンテナ、使い続けた内部DB）ではありません。

### 3.2 実際のブラウザの検査（axe）

| ファイル | 組・状態の数 | 想定外の違反 | 既知の違反（color-contrast） | 横のはみ出し |
|---|---|---|---|---|
| 050（U4） | 20 | 0 | Button の primary（`login-form-submit-button`・`login-language-switch-ja`）各 4 組 | なし |
| 060（U5） | 74 | 0 | `invitation-invite-button`・`invitation-invite-submit` 各 4 組 | なし |
| 070（U6） | 40 | 0 | `registration-submit-button` 4 組 | なし |
| 080（U7） | 80 | 0 | Avatar 76 組、FormField の誤りの文字 各 10 組（4か所）、保存・パスワードの変更のボタン 各 8 組 | なし |

- 既知の違反は、make-you-chic-ui の側で直っています（`7865c28`・`310e1ec`）。固定先の更新は、この Intent の後の別の作業とします（Q7: A・F1: A）。
- `incomplete`（axe が判定できなかったもの）は 060 だけでした。`color-contrast`（重なりの背景など）が 48 組、`bypass` が 40 組です。
- CSP の違反とスクリプトのエラーは、どのテストでもありませんでした。テストの中の確かめで、見つかれば失敗します。

### 3.3 json の報告に秘密が入っていないこと

- アクセストークンの形（`eyJ` で始まる長い文字列）：0 件
- 招待の URL（`/register#token=`）：0 件
- メールアドレス：`e2e-noreply@example.com`（E2E の差出人）の1種類だけです。初期管理者と、測定・流れの招待の宛先のメールアドレスはありませんでした。

## 4. 負荷の試験の台本（k6）

- `perf/k6/scenarios.js` に U2 の6場面と U3 の8場面を足しました。`perf/README.md` に手順を足しました（`performance-test-instructions.md` の 2 節）。
- `node --check perf/k6/scenarios.js` は通りました。
- `docker run --rm --network none ... grafana/k6:2.3.0 inspect --include-system-env-vars` で、既存の 10 場面と新しい 14 場面のすべてが読み込めました（rc=0）。thresholds は、各単位の NFR 要件の値のとおりに出ました。
- 負荷をかけての測定はしていません。持ち主は performance-validation です。

## 5. U7 のときに1回だけ落ちたテスト（Q1: A）

- `NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/app/display-settings/DisplaySettingsProvider.test.tsx` を 50 回流しました。**50 回通過・0 回失敗**（96 秒）でした。
- この段の verify のフロントエンドの全体の実行（732 件）でも通りました。
- 再現しなかったため、「不安定と確かめられていない」として記録します。前例（260925-storage-memory-fixes の F3）に従います。原因は確かめていません。
- U7 の記録によると、落ちたのはフロントエンドの全体の実行の中でした。この段では、依頼者の決定でファイルだけを繰り返しました。全体の中での重なりによる失敗は、この確かめでは拾えません。

## 6. CI（GitHub Actions）と Dependabot（Q3: A）

依頼者が develop（`66fe981`）をプッシュしました。

| 回 | 結果 | 失敗したテスト | 失敗の中身 |
|---|---|---|---|
| 1回目 | 失敗（結合テスト 562 件中 1 件） | `cherry.mastersmith.config.H2CompactionByPoolSuspensionIT` の「the H2 file grows while connections are open and shrinks after suspend, evict and resume via the pool MBean」 | `ConditionTimeoutException`：プールの接続が 0 本になるのを待つところ（`ZERO_CONNECTIONS_WAIT` 10 秒、196 行）で時間切れ |
| 2回目（依頼者の手での流し直し） | 失敗（フロントエンド 732 件中 1 件） | `src/features/invitation/InvitationAdminPage.test.tsx` の「keeps addresses and names out of storage, the URL and the console in every flow」 | `Test timed out in 5000ms`（Vitest の既定の上限） |

- どちらの回も、サブモジュール（`vendor/java-mustache-processor` を含む）を固定先で取得する段は成功しました。
- 2回目では `H2CompactionByPoolSuspensionIT` が通りました（伸びた大きさ 46,710,784 B → 詰め直しの後 8,421,376 B）。
- 1回目では、`InvitationAdminPage.test.tsx` の同じテストが 4,583 ミリ秒かかって通っていました。既定の上限 5 秒のすぐ手前です。
- 前回プッシュした `fd44e79` の CI は成功していました。`fd44e79..66fe981` の間に、バックエンドのコードの変更はありません（ルートの `build.gradle.kts` の1行だけ）。
- 手元の verify と E2E では、どちらのテストも通りました。
- 原因は確かめていません。どちらも「CI の実行環境で、テストの中の時間の上限すれすれになる」形です。
- team.md の決まりでは、不安定なテストは原因を直すまで統合しません。また、CI が失敗したら次に進む前に原因を直します。この段の失敗として扱います（8 節）。

Dependabot について：
- docker-compose の実行（2026-09-28T06:28、成功）で、compose のイメージを調べていました。`axllent/mailpit v1.31.2` も含まれています。
- compose の対象DB のイメージ3つに、更新のプルリクエスト（#6・#8・#9）が出ています。
- Dependabot のプルリクエストは合わせて 11 件あり、開いたままです（Way of Working の決まりで、手元でまとめて取り込みます）。gradle の実行の1つ（2026-09-28T06:28）は failure でした。この段の範囲外として記録だけにします。

## 7. 目標の判定（要約）

目標ごとの判定は、`build-and-test-summary.md` の `## Target Verification Matrix` にあります。

| 判定 | 件数 | 中身 |
|---|---|---|
| Met | 208 | — |
| Not Met | 5 | U4〜U7 の NFR7.1（どの組でもコントラスト 4.5:1。既知の違反の組では 3.30:1 など）、U1-NFR8.3 の (d)（CI で verify が通る） |
| Unverified | 36 | performance-validation 21・observability-setup 6・deployment-pipeline と deployment-execution 6・持ち主の段なし 3（記録だけの要件、または確かめの対象外） |

## 8. 失敗の扱い（依頼者に諮る）

この段の手順の失敗の段階に従って、次のように判断しました。

1. **段の中での直し**：CI の2つの時間切れは、段の足場（ビルドの設定・テストの設定）ではなく、テストのコードの中の待ちの上限にあります。
   - `InvitationAdminPage.test.tsx`（U5 のコード生成で作ったテスト）：テストの上限（既定 5 秒）
   - `H2CompactionByPoolSuspensionIT`（前の Intent のテスト）：`ZERO_CONNECTIONS_WAIT`（10 秒）
   - 段の中では直さず、2 に進みました。
2. **分けて影響を見積もる**：原因は、コード生成で作ったテストのコードにあります（失敗の段階の決まりでは、テストのコードもコード生成の側に当たります）。直し方の候補と影響は、承認の前の問いで示します。
3. Construction Autonomy Mode が未設定（gated と同じ扱い）のため、自動では戻りません。依頼者に諮ります。

### 8.1 依頼者の決定（2026-09-28）

失敗の扱いの問いに、依頼者は次のとおり答えました。

> 次のintentでコントラストと一緒に直す。このintentではこのまま進める。

これを受けて、次のように扱います。
- **この Intent では直しません**：CI の2つの時間切れ（`H2CompactionByPoolSuspensionIT`・`InvitationAdminPage.test.tsx` の1件）と、U4〜U7 の NFR7.1（コントラスト）の Not Met。受け入れた失敗として記録し、この段の承認の場へ進みます。
- **次の Intent で直します**：make-you-chic-ui の固定先の更新（`7865c28`・`310e1ec`）と既知の違反の扱いの取り外しに合わせて行います。
- **決まりとの差**：team.md の Way of Working には「CI が失敗したら、次の Bolt に進む前に原因を直す」、Testing Posture には「不安定なテストは原因を直すまで統合しない」とあります。この決定は、どちらとも食い違います。依頼者の判断として、差を記録します。
- **残る危険**：CI は失敗したままです。
  - 次の Intent で直すまで、CI の結果は統合の後の再確認として役に立ちません。
  - 目標の判定（Met 208・Not Met 5・Unverified 36）は変わりません。

## Sources

- 手元の `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` の出力と報告（`backend/build/test-results/`・`backend/build/reports/jacoco/test/jacocoTestReport.xml`・`frontend/coverage/`）
- 手元の `./gradlew e2eTest` の `frontend/test-results/e2e-results.json`（注記 `first-screen-ms`・`invitation-screen-ms`・`preferences-screen-ms`・`registration-form-ms`・`axe`）
- GitHub Actions の CI run 36433076151（1回目・2回目）と Dependabot の実行・プルリクエスト（`gh run view`・`gh pr list`）
- 各単位の `code-generation-plan.md`・`unit-test-instructions.md`・`code-summary.md`（比べる相手の実測、引き継ぎ）
- `construction/build-and-test/build-and-test-questions.md`（Q1〜Q8・F1）
- `aidlc/spaces/default/memory/team.md`（Way of Working・Testing Posture）・`project.md`（Testing Posture）

## Assumptions & Open Questions

- CI の2つの時間切れの原因は確かめていません。CI の実行環境（GitHub の runner）の速さの揺れと見ていますが、仮説です。
