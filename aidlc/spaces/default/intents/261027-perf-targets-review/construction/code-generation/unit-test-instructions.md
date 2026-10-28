# 単体テストの手順（Intent 261027-perf-targets-review、単位なし）

Test Strategy は Minimal、範囲は bugfix。変えるものは台本・シェルスクリプト・README・Dependabot の設定で、アプリのコードは変えない。bugfix の床として、不具合（緩めた線が台本に入っていない・判定が古い線のまま）を再現するテストを、直す前に落ち直した後に通る形で2件足す。既存のテストは緑のまま。テストの説明文は英語、データは架空の値だけ。

## 前提

- プロジェクトのルートで流す。`source perf/versions.env`（`K6_IMAGE` の定義を読む）。
- Docker（colima）が動いていること。動いていなければ、`thresholds-test.sh` は飛ばさずに失敗し、起動の手順を標準エラーに出す。
- `jq` が使えること。

## 足すテスト（この Intent の単位のテスト）

| テスト | 確かめること | 実行の命令（この単位だけ） |
|---|---|---|
| `perf/k6/thresholds-test.sh` | `k6 inspect --include-system-env-vars` の閾値が、`bizSearchContains` は `p(95)<3000`、`bizSearchLast`・`bizSearchLastSorted` は `p(95)<2000`、`bizSearchFirst`・`bizDetail` は `p(95)<1000`、`bizSearchWide` は `max>=0`、すべての読み取りの場面で `checks` の率 1 が残ること。場面名の取り違えで緩み過ぎた場面が無いこと | `bash perf/k6/thresholds-test.sh` |
| `perf/dsl-mixed-memory-test.sh` | `summarize` が、比 0.9101（`judge=under-0.95`・`judge_090=not-under-0.9`）、0.9600（どちらも not-under）、0.8500（どちらも under）を正しく出し、`anon_max_ratio` の出力が変わらないこと | `bash perf/dsl-mixed-memory-test.sh` |

どちらも、終了コード 0 ならすべて期待どおり、1 なら 1 件でも外れ。直す前（Step 2・4 の前）に実行して落ちることを記録し、直した後に通ることを記録する。

## 既存のテスト（緑のままであること）

- 統合の前の関門: `./gradlew verify`（フォーマット・リンタ・ライセンスヘッダー・ビルド・全テスト・カバレッジ下限・秘密情報の検出・静的解析・依存関係の脆弱性検査）。colima の PC では README の `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡し、`caffeinate -dims` で包む。
- 既存の `bash perf/lib/perf-db-url-test.sh` も1回流す。
- ライセンスヘッダー: 新しいシェルスクリプトの先頭に Apache License 2.0 のヘッダー（2026、agwlvssainokuni）を置く。

## カバレッジの目標

- この単位の変更は Java・TypeScript のソースを含まないため、JaCoCo と `@vitest/coverage-v8` の下限（行 80%・分岐 70%）は変わらず、`verify` が通ることで確かめる。

## モックとテストデータ

- `thresholds-test.sh` は k6 を実際に動かす（`inspect` は台本を読み込むだけで、アプリ・対象DB・ネットワークには触れない）。
- `dsl-mixed-memory-test.sh` の TSV と `peak.txt` は、テストの中で作る架空の数値だけ。実機の測定の値や資格情報は使わない。
