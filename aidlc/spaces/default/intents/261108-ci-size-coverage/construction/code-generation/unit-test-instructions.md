# テストの手順（Intent 261108-ci-size-coverage）

## 1. 道具と設定

- 新しい自動テストは置かない（依頼者の決定 Q4: C・F1。`inception/requirements-analysis/requirements.md` 6節）。テストの道具の設定（Vitest・JUnit）は変えない。
- 確かめに使うもの: Node 26（`node`）、git、cloc（手元は 2.10、`brew install cloc`）、Python 3（移す元の台本との比べだけ。依頼者の手元にある台本を使い、リポジトリには置かない）。
- 既存のテスト（`./gradlew verify` の全件）は、Build and Test で緑のままであることを確かめる（アプリのソースは変えない）。

## 2. この作業の確かめの流し方（範囲はこの作業の台本だけ）

規模の台本（リポジトリのルートで）:

```bash
node scripts/program-size.mjs > build/program-size-node.md
node scripts/program-size.mjs --base HEAD~1 > build/program-size-node-base.md
node scripts/program-size.mjs --base 0000000000000000000000000000000000000000 > build/program-size-node-nobase.md
```

- 1つ目: 終了コード 0、Markdown が出る。
- 2つ目: 大分類と中分類の表に差の列が出る。終わった後に `build/program-size-base` が残らない。
- 3つ目: 差の列が「—」で、理由が1行出る。

Python 版との一致（NFR5）: 同じコミットで移す元の台本を流し、日時・ブランチ・道具の行を除いて `diff` で比べる（差が無いこと）。

要約の台本:

```bash
node scripts/ci-summary.mjs --base HEAD~1 > build/ci-summary-local.md
GITHUB_STEP_SUMMARY=build/ci-summary-empty.md node scripts/ci-summary.mjs --backend-reports build/no-such-dir --frontend-coverage build/no-such-dir; echo "exit=$?"
```

- 1つ目: 規模 → テストの件数 → カバレッジの順に出る。全体の値が `backend/build/reports/jacoco/test/jacocoTestReport.xml` と `frontend/coverage/coverage-summary.json` の値と一致する（手元に直近の報告があることが前提。無ければ `./gradlew :backend:test :backend:integrationTest :backend:jacocoTestReport` と画面のカバレッジの報告を作ってから流す）。
- 2つ目: 欄に「報告なし」が出て、標準出力に `::warning::` が出て、`exit=0`。

CI の手順（`.github/workflows/ci.yml`）は YAML として読めることを確かめ、Build and Test で CI の1回の実行の要約と成果物を見て確かめる。

## 3. 期待するカバレッジ

- この作業はアプリのソースを変えないため、バックエンドとフロントエンドのカバレッジの値は変わらない見込み。カバレッジの下限（行 80%・分岐 70%、パッケージごとの下限と `packagesJudgedByTotal`）は、Build and Test が全件の `./gradlew verify` で確かめる。
- `scripts/` の台本はカバレッジの計測の対象の外（依頼者の決定）。

## 4. 見本・データ

- テストのデータは作らない。手元の実際の報告（JaCoCo の XML・`coverage-summary.json`・JUnit の XML）を使う。
- 報告の無い場合の確かめには、存在しないフォルダ（`build/no-such-dir`）を渡す。
