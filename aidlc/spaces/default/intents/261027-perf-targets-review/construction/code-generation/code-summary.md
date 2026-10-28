# コード生成の要約（Intent 261027-perf-targets-review）

## 変えたファイル

- `perf/k6/scenarios.js`: `BIZ_P95_LIMIT_MS` を足し、`thresholdsFor` の業務データの読み取りの分岐で場面名ごとの p95 の上限を使う。`bizSearchContains` 3000 ms、`bizSearchLast`・`bizSearchLastSorted` 2000 ms、それ以外は 1000 ms、`bizSearchWide` は記録だけ。DB の種類は区別しない（Q1: A）。`checks` の率 1 は残した。
- `perf/k6/thresholds-test.sh`（新規）: `k6 inspect` の閾値を場面ごとに期待の表と照らす。7 場面を照らす（計画の 6 場面に `bizDefinition` を足した）。
- `perf/dsl-mixed-memory.sh`: `summarize` の `judge` を 0.95 未満の判定にし、前の線の `judge_090` を併記。`anon_max_ratio` は変えない。
- `perf/dsl-mixed-memory-test.sh`（新規）: 比 0.9101・0.9600・0.8500 の 3 件で `judge`・`judge_090`・`anon_max_ratio` を照らす。
- `perf/README.md`: 末尾に見直しの節を足し、既存の 862・1142〜1146・1297・1304 行付近に新しい線への案内を足した。前の Intent の結果の文は書き換えていない。
- `.github/dependabot.yml`: docker-compose の項に、`otel/opentelemetry-collector` の `0.163.2-386` の `ignore` を理由と外す時期つきで足した。

## テスト（直す前に落ち、直した後に通った）

| テスト | 直す前 | 直した後 |
|---|---|---|
| `bash perf/k6/thresholds-test.sh` | 終了コード 1。`bizSearchContains` が `p(95)<1000`（期待 3000）、`bizSearchLast`・`bizSearchLastSorted` が `p(95)<1000`（期待 2000） | 終了コード 0。7 場面とも期待どおり |
| `bash perf/dsl-mixed-memory-test.sh` | 終了コード 1。3 件とも FAIL（`judge` が旧い線、`judge_090` の行が無い） | 終了コード 0。0.9101 は `under-0.95`・`not-under-0.9`、0.9600 は両方 not-under、0.8500 は両方 under |
| `bash perf/lib/perf-db-url-test.sh`（既存） | — | 終了コード 0（19 件、値の漏れ 0 件） |
| `./gradlew verify`（`caffeinate -dims`、colima） | — | BUILD SUCCESSFUL、3 分 29 秒、SKIPPED 0。Java・TypeScript のソースを変えていないため、バックエンドのテストの多くは UP-TO-DATE |

注: 上の `verify` の件数は、バックエンドのテストが UP-TO-DATE で飛ばされた回の値で、テストの件数・カバレッジの報告には使わない（`project.md` の決まり）。Build and Test で `:backend:cleanTest :backend:cleanIntegrationTest` を付けて測る。

## 計画との差

- **R-04（配備）**: 計画の読み替えのとおり、Q5 の答え（通常どおり配備）を優先する。要件 FR4.1 の「取り込む更新が無ければ配備しない」とは食い違う。#80 は見送るため取り込む更新は無いが、`main` の先頭から WAR とイメージを作り直して入れ替える。
- **FR の番号の並び**: 承認済みの要件は FR2.1→FR2.5→FR2.6→FR2.2 の順に並ぶ。書き換えず、ここで読み方を案内する（FR2.5 が測り方、FR2.6 が線の確かめ、FR2.2 以降が記録）。
- `thresholds-test.sh` で `bizDefinition` も照らした。k6 のコンテナ起動が一度だけ空の出力になったため、読めなかったときだけ 1 回やり直す処理を足した。`k6 inspect` では `-e SCENARIO=…` を引数に渡すと閾値が null になるため、`docker run -e SCENARIO` と `--include-system-env-vars` の形にした（README に記載）。
- `shellcheck`・`shfmt` はこの PC に無く、新しいシェルは `bash -n` までしか確かめていない。`./gradlew verify` のフォーマット・リンタの対象外（`perf/` はシェルの検査の対象でない）。
- `dsl-mixed-memory-test.sh` は本体から `complete_rows` と `summarize` を `awk` で取り出す。本体の関数の書き出しの形を変えると取り出しに失敗して FAIL になる。
- `scenarios.js` は Prettier で整形していない（`perf/` はフロントエンドの検査の対象外）。

## Dependabot の PR（FR3）

- 開いているのは #80（`otel/opentelemetry-collector` 0.163.2 → 0.163.2-386）の 1 件。`docker buildx imagetools inspect` で `0.163.2-386` は linux/386 だけと確かめた（通常のタグは amd64・arm64 などを含む）。手元の PC（arm64）で動かないため見送り、`.github/dependabot.yml` に `ignore` を足した。
- PR #80 を GitHub 上で閉じる操作は、統合の後に依頼者の確認を得て行う（`gh` の変更の操作のため）。

## 次の段へ引き継ぐこと

- 測り直し（FR2）、`k6 inspect` の最終の確かめ、E2E の 1 回、CI の確かめ: Build and Test。
- WAR とイメージの作り直し、配備、`main` への取り込み、PR #80 を閉じること: Deployment Pipeline・Deployment Execution。
