# テストの手順（Intent 261107-verify-quick-local）

## 1. 道具と設定

- 確かめの台本: `scripts/verify-graph-check.mjs`（Node 26 で動く。追加の依存は無い。子のプロセスで `./gradlew` を流す）。
- 比べの元: `scripts/verify-graph-baseline.txt`（変える前の `./gradlew verify --dry-run` のタスクの一覧。計画の Step 1 で取る）。
- 実行の確かめは Gradle のタスクそのもの（`verifyQuick`・`:backend:integrationTest`・`:backend:jacocoQuickReport`）を流して行う。
- colima の PC では、README の `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡す。長い実行は `caffeinate -dims` で包む（`project.md` の Testing Posture）。

## 2. この Bolt のテストの流し方

確かめの台本（タスクの一覧と `-P` の誤りの扱いの 8 項目。`--dry-run` だけを流し、テストは流さない）:

```bash
node scripts/verify-graph-check.mjs
```

終了コード 0 で 8 項目すべて合格、1 で不合格（不合格の項目と理由を出す）。

実行の確かめ（計画の Step 7）:

```bash
./gradlew verifyQuick -PitTests=AuditTraceIdIT
./gradlew verifyQuick -PitTests=NoSuchClassIT
./gradlew :backend:jacocoQuickReport
```

- 1つ目: 通り、`AuditTraceIdIT` だけが結合テストとして流れ（`backend/build/test-results/integrationTest/` の XML が `AuditTraceIdIT` だけ）、参考値の行が表示される。
- 2つ目: 当たるテストが無く失敗する。
- 3つ目: 前の回の `backend/build/jacoco/integrationTest.exec` を残したまま流し、`backend/build/reports/jacoco/quick/` の報告の Sessions が単体テストの記録だけであることを確かめる。

## 3. 期待するカバレッジ

- この Bolt はアプリのソースを変えないため、バックエンドとフロントエンドのカバレッジの値は変わらない見込み。カバレッジの下限（行 80%・分岐 70%、パッケージごとの下限と `packagesJudgedByTotal`）は、全件の `verify`（`:backend:cleanTest :backend:cleanIntegrationTest` を付ける）で Build and Test が全体のテストの上で確かめる。確かめの台本はカバレッジを判定しない。
- `verifyQuick` の参考値は判定に使わない。

## 4. Build and Test に引き継ぐこと

- 時間（NFR1）: 同じ PC・同じ条件で、`./gradlew verify`、`./gradlew verifyQuick`（`-PitTests` なし）、`./gradlew verify -PitForks=<N>` の時間を測って並べる。`verifyQuick` が `verify` より短いことを合格の条件とする。N は 2 から試し、通った値を記録する。
- 並べた `verify` の値（R-02）: 直列の `verify` と `-PitForks=<N>` の `verify` の、バックエンドの全体の行・分岐のカバレッジの値を並べ、一致を確かめる。食い違ったら依頼者に諮る。
- 並べて失敗するテストが出たら、`team.md` の「不安定なテストと CI の失敗」の扱いに従い、依頼者に諮る。

## 5. 見本・データ

- テストのデータは作らない。結合テストの絞り込みの見本には既存の `AuditTraceIdIT` を使う（内部DB だけを使い、コンテナに頼らない見込み）。
- 当たらない見本の名前 `NoSuchClassIT` は、どの既存のクラスにも当たらない名前として使う。
