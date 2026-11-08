# テストの手順（Intent 261108-ci-checks-release）

## 1. 道具と設定

- バックエンドの単体テスト: JUnit 5（既存の `backend` の `test` タスク）。設定は変えない。
- 書式・リンタ・ライセンスヘッダー: `frontend/` の Prettier・oxlint・ESLint と `frontend/scripts/check-license-header.mjs`（対象に `scripts/` を足す）。
- 台本 A1〜A3 には単体テストを置かない（要件の 5節の前提）。手元で流して FR5.5 の合格の条件を確かめる。
- colima の PC では、README の `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡す（A1 と全件の `verify`）。

## 2. この作業のテストの流し方（範囲を絞ったもの）

バックエンド（`common.health` のテストだけ）:

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.common.health.*'
```

- `TimeBoundedDbHealthIndicatorTest` の既存のテストと、足した2件（確認を受け付けないとき・待ちが割り込まれたとき）が通ること。

検査（`scripts/` を足した後）:

```bash
npm --prefix frontend run license:check
npm --prefix frontend run format:check
npm --prefix frontend run lint
```

- 3つとも通ること。`scripts/` の1本からヘッダーを外すと `license:check` が落ちること（確かめた後に戻す）。

台本:

```bash
node scripts/ci-attempts.mjs --from b2990415
node scripts/ops-readonly-check.mjs
node scripts/count-db-copy.mjs <複写の tgz> <起点の時刻> --set basic
GITHUB_STEP_SUMMARY= node scripts/ci-summary.mjs
node scripts/verify-graph-check.mjs
```

- `ci-attempts.mjs`: コミットごとの CI の有無と試行ごとの結論が出て、手で `gh run view` で確かめた数件と一致すること。
- `ops-readonly-check.mjs`: 5つの項目が出て、出力に環境変数の値が入らないこと（colima が止まっているときは「コンテナが無い」と出ること）。
- `count-db-copy.mjs`: 今の道具（`count-db.sh`・`count-db2.sh`・`count-db3.sh`）と同じ複写で件数が一致すること（`.env` を読むため、流す前に依頼者の許しを得る）。
- `ci-summary.mjs`: 印の列に「全体で判定」が出ず、手元の報告で動くこと。
- `verify-graph-check.mjs`: 8 項目が通ること（タスクの組み立てが壊れていないこと）。

## 3. 期待するカバレッジ

- `common.health` の行のカバレッジ: 今の 42/53（79.25%）から、足した2件で 80% 以上（見込み 53/53 近く）。
- カバレッジの下限（全体の行 80%・分岐 70%、すべてのパッケージごとの同じ下限）は、この段の範囲の実行では判定しない。Build and Test が全件の `./gradlew verify`（`:backend:cleanTest :backend:cleanIntegrationTest` を付ける）で、`packagesJudgedByTotal` を消した後に全パッケージが下限を満たすことを確かめる。

## 4. 見本・データ

- テストのデータは作らない。`common.health` のテストは、止めた実行の枠と、割り込みの印を立てたスレッドで場合を作る（実時刻に頼らない）。
- A3 は依頼者の手元の内部DB の複写（ホームの下の tgz）を使う。リポジトリに置かない。
