# 単位のテストの手順書（Intent 261110-deps-forks-review）

単位の分割の無い bugfix（深さ Minimal・Test Strategy Minimal）。依存と道具の更新のため、新しいテストは置かない見込みで、既存のテストで確かめる。

## テストの道具と設定

- backend: JUnit Jupiter・AssertJ・Mockito・Testcontainers（対象DB の3種類）・jqwik・ArchUnit。単体は `XxxTest`（`:backend:test`）、結合は `XxxIT`（`:backend:integrationTest`）。設定は `backend/build.gradle.kts`。
- frontend: Vitest・Testing Library・vitest-axe・fast-check（`frontend/vitest.config.ts`）。E2E は Playwright（`frontend/playwright.config.ts`、`./gradlew e2eTest`）。
- 前提: colima が動いていること。`DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡す（`docs/target-db.md`）。長い検査は `caffeinate -dims` で包み、流す前に `pmset -g assertions`、流した後に `pmset -g log` で眠りが無かったかを確かめる。

## この Intent のテストの流し方

### 1. 固定に関わる絞ったテスト（Step 2 と Step 5 の各試しの後）

連動する固定（opentelemetry-logback-appender・networknt・`-Dh2.compactThreads=1`）の理由を確かめる既存のテストだけを流す。

```sh
DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock" TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock caffeinate -dims ./gradlew verifyQuick -PitTests='OtlpLogExportIT,ExternalExportIT,H2CompactionByPoolSuspensionIT'
```

```sh
./gradlew :backend:test --tests 'cherry.mastersmith.config.H2CompactThreadsTest'
```

networknt の試し（Step 5 (b)）では、DSL の読み込みのテストを加える。

```sh
./gradlew :backend:test --tests 'cherry.mastersmith.dsl.*'
```

### 2. frontend の依存の更新ごと（Step 6）

```sh
cd frontend && npm run typecheck && npm run lint && npm test
```

（スクリプトの名前は `frontend/package.json` で確かめ、違えば同じ目的のものを使う。）

### 3. まとまりの統合の前（Step 9・Step 13）

```sh
DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock" TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify -PitForks=3
```

```sh
DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock" caffeinate -dims ./gradlew e2eTest
```

（E2E の見本の PG と `docker/e2e/compose.targetdb-port.yaml` の起動は `docs/verification.md` の手順に従う。）

### 4. 決まりの文書の直しの統合の前（Step 15）

```sh
./gradlew verifyQuick
```

### 5. 外部エクスポートを有効にした手元の起動（Step 5 (a) で appender を上げたときだけ）

compose の lgtm を起動し、`docs/operations.md` の外部エクスポートの設定を有効にしてアプリを起動する。起動が失敗しないことと、lgtm の Loki にアプリのログが届くことを確かめる（値は表示せず、ロガーの名前と件数だけを見る）。

## 期待するカバレッジ

- backend: 全体とすべてのパッケージごとに行 80%・分岐 70%（JaCoCo）。全件の `verify` で判定する。
- frontend: 全体の合計に行 80%・分岐 70%（`@vitest/coverage-v8` の `thresholds`）。
- この下限は全件のテストで判定する。下限・除外は変えない。

## モックと見本

- 新しいモックは作らない。既存のテストの見本（`testsupport` の下）をそのまま使う。

## テストのデータ

- 既存のテストのデータのまま。対象DB は Testcontainers で版を固定したイメージを起動する（Step 11 で上げたイメージ）。

## 要件ごとの確かめ（Minimal: 1要件1つ）

| 要件 | 確かめ |
|---|---|
| FR1.1・FR1.6 | 全件の `verify`（3種類の対象DB の結合テスト）・E2E・`docker inspect` |
| FR1.3・FR1.4・FR1.5 | 全件の `verify`（OSV-Scanner の High 以上なし）・E2E |
| FR1.7 | vitest-axe と E2E の axe の違反 0 件・lockfile の axe-core が1つの版 |
| FR2.2〜FR2.4 | 上の 1 と 5 |
| FR2.5 | Step 8 の記録 |
| FR3 | `verifyQuick` と文面の承認 |
