# 単体テストの手順 — U5 u5-carryover-tests（Bolt B1）

この文書は、U5 のテストの書き方と走らせ方を決める。Test Strategy は standard（部品ごとに 5〜8 件、要所の境目に結合テスト）、methodology は test-after（層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて流し、すべて通ってから次の層へ）。手順の順は `code-generation-plan.md` の 5節の Step に従う。

U5 は本番のコードを変えない。テストの対象は、テストの補助（待ち合わせ・スレッドの記録・`Actor` の伏せ字）、直す2件のテストそのもの、`perf/` の台本の3つである。

## 1. 道具と設定

| 用途 | 道具 | 設定の置き場 |
|---|---|---|
| 単体テスト（名前が `Test` で終わるクラス） | JUnit 5・AssertJ | `backend/build.gradle.kts` の `tasks.test`（既存。変えない） |
| 結合テスト（名前が `IT` で終わるクラス） | Spring Boot のテスト・組み込みの H2（内部DB）・`java.net.http` の要求 | `backend/build.gradle.kts` の `integrationTest`（既存。変えない） |
| 画面のテスト | Vitest・Testing Library（jsdom）・`waitFor` | `frontend/vitest.config.ts`（既存。変えない） |
| `perf/` の台本の確かめ | bash（`bash -n`）と、確かめの関数の試しの台本 | `perf/lib/`（計画の 10節の D3: A） |

- 新しい依存は足さない（`tech-stack-decisions.md` の TS1）。テストの設定のファイル（`build.gradle.kts`・`vitest.config.ts`）も変えない。`shellcheck`・`shfmt` はこの PC に無いため使わない。
- テストの JVM は既存の設定のまま（`user.timezone=Asia/Tokyo`・`java.net.preferIPv4Stack=true`・`h2.compactThreads=1`）。
- テストの説明文（`@DisplayName`・`describe`・`it`）は英語。コメントは日本語。新しいファイルの先頭に Apache License 2.0 のヘッダー（Java は `/* ... */`、シェルは既存の台本と同じ `#` の形）を置く。
- 置き場は対象と同じパッケージ。テストの補助は `backend/src/test/java/cherry/mastersmith/audit/testsupport/` と `.../role/testsupport/` に置く（team.md の Code Style）。

## 2. 走らせる前の準備（毎回）

コンテナの実行環境（colima）が動いていることを確かめ、README の2つの変数をシェルに渡す（`project.md` の学び）。U5 のテストは対象DB を使わないが、Step 14 の verify は対象DB のテストを含むため、同じ準備で流す。

```bash
colima status
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

- 数分を超える実行（verify）は `caffeinate -dims` で包み、流す前に `pmset -g assertions`、流した後に `pmset -g log` で Sleep・DarkWake が無かったかを確かめる。入っていたら結果を使わずに流し直す。
- 画面のテストは `frontend/` で流す。`NODE_OPTIONS=--no-experimental-webstorage` を付ける（`frontend/package.json` の `test` と同じ）。

## 3. この単位のテストの走らせ方

どのコマンドも、この単位のテストのクラス・ファイルだけに絞っている（プロジェクトの全体は、計画の Step 14 の verify でだけ流す）。プロジェクトのルートで流す（画面のテストは `cd frontend` の後）。

### 3.1 走らせ方の確かめ（計画の Step 1。最初のテストの Step の前）

変更の前に、既存のテストが今の形で通り、絞り込みの形が動くことを確かめ、件数を記録する（brownfield）。

```bash
# テストの補助のパッケージの単体テスト（絞り込みの形の確かめ）
./gradlew :backend:test --tests 'cherry.mastersmith.role.testsupport.LargeRoleTransferYamlTest'
# 直す前の #7 を含む結合テスト（4件）
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.role.web.RoleConcurrencyIT'
# 直す前の画面のテスト（12件）
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/app/admin-forbidden/AdminForbiddenProvider.test.tsx)
```

### 3.2 層ごとのコマンド

| 層（Step） | コマンド |
|---|---|
| テストの補助（Step 3） | `./gradlew :backend:test --tests 'cherry.mastersmith.audit.testsupport.AuditWriteReorderBarrierTest' --tests 'cherry.mastersmith.role.testsupport.TestRoleBarrierTest' --tests 'cherry.mastersmith.role.testsupport.RoleActorsTest'` |
| #7（Step 4・5） | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.role.web.RoleConcurrencyIT'` |
| #7 の繰り返し（Step 6。5回） | `for i in 1 2 3 4 5; do ./gradlew :backend:integrationTest --tests 'cherry.mastersmith.role.web.RoleConcurrencyIT' --rerun \|\| break; done` |
| 画面のテスト（Step 7・8） | `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/app/admin-forbidden/AdminForbiddenProvider.test.tsx)` |
| 画面のテストの繰り返し（Step 7・8。5回） | `(cd frontend && for i in 1 2 3 4 5; do NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/app/admin-forbidden/AdminForbiddenProvider.test.tsx \|\| break; done)` |
| 確かめの関数の試し（Step 10） | `bash perf/lib/perf-db-url-test.sh` |
| 親が止まることの確かめ（Step 10） | `mkdir -p build/perf-results/u5-check; ls -d "${HOME}"/.mastersmith-perf-* 2> /dev/null > build/perf-results/u5-check/tmpdirs-before.txt; PERF_DB_URL='jdbc:h2:tcp://example.invalid/x' ./perf/dsl-timing.sh postgres; echo "exit=$?"; ls -d "${HOME}"/.mastersmith-perf-* 2> /dev/null \| diff build/perf-results/u5-check/tmpdirs-before.txt -`（終了コードが 1 で、差が無いこと。`build/` の下は git の対象外で、コミットしない） |
| 台本の構文（Step 12） | `bash -n perf/dsl-timing.sh perf/lib/perf-db-url.sh perf/lib/perf-db-url-test.sh perf/dsl-mixed-memory.sh` |

- 画面のテストの二段の確かめ（Step 7）の試し T1・T2 は、作業フォルダの中で一時の変更を当てて上の「画面のテスト」のコマンドで流し、終わったら戻す。戻したことは `git diff --exit-code frontend/src/app/admin-forbidden/AdminForbiddenProvider.test.tsx`（Step 8 の前）で確かめる。
- #7 の二段の確かめの1段目（Step 4）は、直す前の確かめ方のまま入れ替えた順の場合だけを流す。そのときはメソッドで絞ってよい: `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.role.web.RoleConcurrencyIT.<入れ替えた順のメソッドの名前>'`。
- 記録のループの空回し（Step 12。計画の 10節の D5: A）はテストではなく確かめで、計画の Step 12 の手順で行う（配備したアプリのコンテナ `mastersmith-app-1` を 5 秒読むだけ）。

### 3.3 この単位のテストをまとめて流す（Build and Test でも使う）

```bash
./gradlew :backend:test \
  --tests 'cherry.mastersmith.audit.testsupport.AuditWriteReorderBarrierTest' \
  --tests 'cherry.mastersmith.role.testsupport.TestRoleBarrierTest' \
  --tests 'cherry.mastersmith.role.testsupport.RoleActorsTest'
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.role.web.RoleConcurrencyIT'
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/app/admin-forbidden/AdminForbiddenProvider.test.tsx)
bash perf/lib/perf-db-url-test.sh
```

- テストの件数を報告するときは、Gradle がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`--rerun` を付けるか `:backend:cleanTest :backend:cleanIntegrationTest` を付けて流し直し、実測の数字だけを書く（`project.md` の学び）。
- `dslMixed` の `memory.peak` の測り（`perf/dsl-mixed-memory.sh` を4回）は、配備したアプリを止める測りで、テストではない。Build and Test で `perf/README.md` の (b) 節の手順で行う。

## 4. 部品ごとのテストと件数の目安

### 4.1 テストの補助の単体テスト

| ファイル | 件数 | 確かめること |
|---|---|---|
| `AuditWriteReorderBarrierTest` | 7 | 準備していない間は止めない／`held` の1回目で止まり、`releaser` の2回目で放される（`heldArrived` 真・`releasedByTimeout` 偽）／`releaser` の1回目では放されない／ほかのスレッドの呼び出しでは放されない／放す条件が先に満たされていたら止めずに通す（`heldArrived` 真・`releasedByTimeout` 偽）／上限で放したら `releasedByTimeout` 真／`disarm` で止めているスレッドが放され、`armFor` で前の回の状態が消える |
| `TestRoleBarrierTest` | 3 | `Gate.arrivedThread()` が止まったスレッド／`Signal.passedThread()` が最初に通ったスレッドで、後に通ったスレッドで変わらない／来る前・通る前は `null` |
| `RoleActorsTest` | 2 | `toString` にメールアドレスとトークンの値が含まれず、`userId` が含まれる／`email()`・`token()` は値をそのまま返す |

### 4.2 直す2件のテスト

| ファイル | 件数 | 確かめること |
|---|---|---|
| `RoleConcurrencyIT` | 5（#7 の2件と、変えない #1 の2件・#4 の1件） | #7 の自然の順と入れ替えた順のそれぞれで: 2件目が 204 のとき、監査の2行を中身で見分ける（「前が空・後が READ」と「前が READ・後が FULL」が1行ずつ。BR1.1）、最後の主権限が FULL（BR1.2）、待ち合わせが止めて放したこと（`heldArrived` 真・`releasedByTimeout` 偽）と2行の ID の順がその場合に作った順であること（BR1.5）。自然の順も入れ替えた順も、同じ待ち合わせで確実に作る（計画の 10節の D2: A）。409 の扱いは、自然の順は `ROLE_BUSY` の code だけ（BR1.3）、入れ替えた順は待ち合わせの結果の確かめを 409 の分岐より前に置き、409 はテストの失敗にする（D1: A） |
| `AdminForbiddenProvider.test.tsx` | 12（直すのは1件） | 「keeps the same function before and after the URL becomes forbidden」で、効果の回数がちょうど 1 になるのを `waitFor` で待ってから写す（BR2.1）。403 の後に画面が「forbidden」、口の関数が同じ、効果の回数が変わらない（BR2.2） |

### 4.3 `perf/` の台本

| 台本 | 件数 | 確かめること |
|---|---|---|
| `perf/lib/perf-db-url-test.sh` | 悪い入力 15・正しい入力 4 | 計画の Step 10 の入力の表のとおり。件ごとに「止めた・通した」と期待の一致を出し、止めたときの標準エラーに入力の値が出ないこと |
| `perf/dsl-timing.sh` の呼び出しの位置 | 1 | 悪い `PERF_DB_URL` で終了コード 1、一時ディレクトリを作らない |

## 5. カバレッジの目標

- 本体のソースを変えないため、バックエンドとフロントエンドのカバレッジの値（全体の合計 行 80%・分岐 70%、新しいパッケージごとの下限）は変わらない見込み。Step 14 の verify で、下限を満たすことと全体の値を記録する。
- テストの補助はテストのソースで、カバレッジの対象ではない。除外の設定は変えない（team.md の Testing Posture）。

## 6. モックと偽物の使い方

- モックは使わない。結合テストは本物の Spring の文脈と組み込みの H2 で、重なりは待ち合わせの口（`TestRoleBarrier` と `AuditWriteReorderBarrier`）で作る（team.md の「重なりはスレッドの数に頼らず待ち合わせで確実に作る」）。
- 監査の書き込みの時間を測る `LongSupplier` は、`AuditWriteReorderBarrierConfig` の `@Primary` の Bean で差し替える。`RoleConcurrencyIT` にだけ `@Import` し、同じ型を差し替える `AuditWriteBarrierConfig`・`SlowAuditWriteConfig` と同じクラスに入れない（入れると Spring の起動が止まる）。
- 待ち合わせの単体テストは、スレッドを `CountDownLatch` でそろえて順を作り、`sleep` で順を作らない。上限で放す場合だけは短い上限（200 ミリ秒）を渡す。結合テストは `TestRoleBarrier.WAIT_LIMIT`（20 秒）を渡し、延ばさない。
- 画面のテストは既存の `renderWithProviders` と `fakeProvider` のまま。`waitFor` と Vitest の上限は既定のまま延ばさない。

## 7. テストのデータ

- #7 のロールは既存の `RoleFixtures.uniqueName("同時の保存")` で作り、管理者は既存の `RoleActors.admin()`（メールアドレスは予約のドメイン、パスワードは明らかなテストの値）を使う。
- 失敗の文に出すのは、監査の行の `detail`（前と後の権限の値）・件数・真偽・スレッドの名前だけ。アクセストークン・メールアドレス・パスワードは出さない（NFR7.1、`security-design.md` S4.1）。
- `perf/` の試しの入力は見本の値だけで、秘密を含まない。外部の URL の見本は予約のドメイン（`example.invalid`）を使う。
- テストは内部DB の一時の置き場（`@TempDir`）を使い、実行の順に頼らない（既存の形）。

## Sources

- 計画: `aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/code-generation/code-generation-plan.md`（5節の Step、10節の D1〜D5）
- U5 の設計: `aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/functional-design/rules.md`（BR1〜BR4）、`nfr-requirements/security-requirements.md`（NFR4.5〜NFR4.9・NFR7.1〜NFR7.3）、`nfr-design/logical-components.md`（4節）・`security-design.md`（S1〜S4）、`infrastructure-design/cicd-pipeline.md`（2節・4節）
- コード: `backend/build.gradle.kts`（`tasks.test`・`integrationTest` の絞り込み）、`frontend/package.json`（`test`）、`frontend/vitest.config.ts`、`backend/src/test/java/cherry/mastersmith/role/web/RoleConcurrencyIT.java`、`backend/src/test/java/cherry/mastersmith/role/testsupport/`、`backend/src/test/java/cherry/mastersmith/audit/testsupport/`、`frontend/src/app/admin-forbidden/AdminForbiddenProvider.test.tsx`、`perf/dsl-timing.sh`
- 前の単位の手順の形: `aidlc/spaces/default/intents/261016-business-data-views/construction/u1-businessdata-read/code-generation/unit-test-instructions.md`・`u4-a11y-carryover/code-generation/unit-test-instructions.md`
- 決まり: `aidlc/spaces/default/memory/team.md`（Testing Posture・Code Style）、`aidlc/spaces/default/memory/project.md`（Testing Posture の学び）

## Assumptions & Open Questions

- [assumption] Gradle の `--rerun`（1つのタスクだけを流し直す）がこのリポジトリの Gradle で使える。使えないときは、繰り返しの各回の前に `:backend:cleanIntegrationTest` を付ける。
- 計画の 10節の決定（D1〜D5: A）は、この文書の 1・3・4 に反映済み。
