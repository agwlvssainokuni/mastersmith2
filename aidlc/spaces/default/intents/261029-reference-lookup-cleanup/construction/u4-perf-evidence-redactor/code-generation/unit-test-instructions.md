# 単体テストの手順 — U4 u4-perf-evidence-redactor

Test Strategy は Standard（部品ごとに 5〜8 件の要の振る舞い。この単位は設計が例のテストの表と性質を細かく決めているため、それを満たす数にする）。方法は test-after（`code-generation-plan.md` の Testing Contract が正）。どのコマンドもこの単位の 3 つのテストのファイルか道具のファイルに絞ってある。特に断りの無いものはプロジェクトのルートで流す。

## 1. 道具と設定

| 道具 | 使い方 |
|---|---|
| Vitest（`frontend/` の既存の `^5.0.5`） | 3 つのテストのファイルを `frontend/vitest.config.ts` の `unit` の project で流す。設定は変えない（`src/**/*.test.{ts,tsx}` に当たる置き場に置く） |
| fast-check（既存の `^4.10.2`） | 性質ベースのテスト（JSON の 5 つ・テキストの 3 つ・定義の突き合わせ）。`fc.check` の結果を受ける手伝いの関数 `assertProperty` を使い、失敗のときは種（`seed`）・道（`counterexamplePath`）・回数だけを表示する（反例の値は出さない） |
| Node 24 の組み込み | 道具（`perf/redact-k6-evidence.mjs`）は `node:` の組み込みだけを読む。テストは `node:fs`・`node:os`・`node:path`・`node:url`・`node:child_process` を使う |

- 3 つのファイルとも先頭に `// @vitest-environment node` を置く（`unit` の既定の `jsdom` で流さない）。
- 道具は、テストの中で `pathToFileURL(resolve(import.meta.dirname, '..', '..', 'perf', 'redact-k6-evidence.mjs'))` を `await import(/* @vite-ignore */ url)` で読み、ファイルの中の `interface PerfEvidenceRedactorModule` で型を付ける（`any` を使わない）。
- 新しい依存・設定のファイルは足さない。Vitest の時間の上限（既定）は延ばさない。

| テストのファイル | 対象の部品 | 主な中身 |
|---|---|---|
| `frontend/src/perfEvidenceRedactor.test.ts` | 置換・残存の検査・木の置換（LC1〜LC3） | 公開の名前の一覧（`default` が無い）、定数、境界の例 約 20 件、性質 8 件と定義の突き合わせ、1 MiB の `-eyJ` の時間 |
| `frontend/src/perfEvidenceRedactorCli.test.ts` | JSON の口・テキストの口（LC4・LC5・LC7） | 終わりのコード 0〜5、出力のパスと一時ファイルの前後、表示に値が出ない、64 MiB の境界、中断の片付け、子のプロセスの 2 件 |
| `frontend/src/perfEvidenceScan.test.ts` | evidence の検査の口（LC6） | `security-design.md` 8.5節の表のすべての行、位置の 10 件の上限、`.` で始まる名前、子のプロセスの 1 件 |

## 2. 前提

- `frontend/` で依存が入っていること（`./gradlew frontendInstall`、または `cd frontend && npm ci`）。この単位のテストは make-you-chic-ui の成果物を読まないため、`vendorBuild` は要らない。
- Node 24（`node --version` が `v24.`）。
- コンテナの実行環境は要らない（この単位のテストと `perfEvidenceScan` は対象DB を使わない）。ただし統合の前の `./gradlew verify` は、今までどおり colima を動かし、README の `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を付けて流す。
- 長い実行（verify）は `caffeinate -dims` で包み、流した後に `pmset -g log` で Sleep・DarkWake が無かったことを確かめる。

## 3. この単位のテストを流すコマンド

### 3.1 最初のテストの前に流せることを確かめる（計画の Step 3）

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx --no-install vitest run --project unit src/perfEvidenceRedactor.test.ts)
```

- 期待: 3 件（`window` が無い・公開の名前・定数）が passed、skipped と todo が 0。道具の読み込みの誤りで流れなければ止めて、計画の 5.1節 P3 の切り替え先を依頼者に諮る。

### 3.2 層 1（純粋な関数。計画の Step 7・8）

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx --no-install vitest run --project unit src/perfEvidenceRedactor.test.ts)
```

### 3.3 層 2（JSON の口・テキストの口。計画の Step 10）

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx --no-install vitest run --project unit src/perfEvidenceRedactorCli.test.ts)
```

### 3.4 層 3（evidence の検査の口。計画の Step 12）

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx --no-install vitest run --project unit src/perfEvidenceScan.test.ts)
```

### 3.5 この単位のすべて（計画の Step 20、Build and Test でも流す）

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx --no-install vitest run --project unit src/perfEvidenceRedactor.test.ts src/perfEvidenceRedactorCli.test.ts src/perfEvidenceScan.test.ts)
```

- 期待: 3 ファイルのすべてが passed、skipped と todo が 0、passed は合わせて 1 件以上（約 80 件の見込み）。件数はファイルごとに記録する。

### 3.6 性質のテストの失敗を再現する

- 失敗の表示の `seed=<数> path=<道>` を、その性質の `assertProperty(prop, { seed: <数>, path: '<道>' })` に一時に渡して、3.2節のコマンドで流す（手順はテストのファイルの先頭のコメントにもある）。反例の値は表示しないため、再現は種と道で行う。確かめた後に引数を戻す。

## 4. この単位のカバレッジの確かめ（道具のファイルだけ）

画面の全体の下限（行 80%・分岐 70%）は `frontend/vitest.config.ts` の `coverage.include`（`src/**/*.{ts,tsx}`）に当たり、道具の `.mjs` は入らない（計画 5.1節 P5。設定は変えない）。この単位の道具に同じ下限が当たるかを、次のコマンドで道具だけを計測して確かめる。設定の `thresholds`（行 80%・分岐 70%）がそのまま道具だけに当たる。下限の判定で終わりのコードが 0 以外になりうるため、ほかの手順と分けて単独で流す。

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx --no-install vitest run --project unit --coverage --coverage.include=../perf/redact-k6-evidence.mjs --coverage.allowExternal --coverage.reportsDirectory=../build/u4-tool-coverage src/perfEvidenceRedactor.test.ts src/perfEvidenceRedactorCli.test.ts src/perfEvidenceScan.test.ts)
```

- 期待: 道具のファイルの行 80% 以上・分岐 70% 以上。値を `code-summary.md` に書く。子のプロセスで起動した分は計測に入らない（起動の部分は `main` の直接の呼び出しで通す）。
- このコマンドが、選んだテストがすべて passed のまま下限の判定だけで 0 以外で終わったときは、失敗したテストではなく下限の未達として値とともに記録し、依頼者に伝える（下限や除外を下げない）。
- 出力の置き場 `build/u4-tool-coverage` は git の対象外（ルートの `.gitignore` の `build/`）。画面の `frontend/coverage` を上書きしない。

## 5. テストのほかの、この単位の確かめのコマンド

```bash
# 導入の前の一度の実行（計画の Step 14）。件数を記録し、検出か入力の誤りが 1 件でもあれば止めて依頼者に諮る
node perf/redact-k6-evidence.mjs --scan aidlc

# verify に組み込んだタスクを単独で流す（計画の Step 15・16）
./gradlew perfEvidenceScan perfToolFormatCheck perfToolLint frontendLicenseCheck

# 完成した JWT の形がソースに無いこと（計画の Step 19、NFR8.71）。ファイルごとの件数だけを出し、行は表示しない。すべて 0 であること
grep -cE '(^|[^A-Za-z0-9])eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+' \
  perf/redact-k6-evidence.mjs perf/README.md \
  frontend/src/perfEvidenceRedactor.test.ts frontend/src/perfEvidenceRedactorCli.test.ts frontend/src/perfEvidenceScan.test.ts

# 同じファイルに Gitleaks を流す（値を伏せる表示）。0 件であること
for f in perf/redact-k6-evidence.mjs frontend/src/perfEvidenceRedactor.test.ts frontend/src/perfEvidenceRedactorCli.test.ts frontend/src/perfEvidenceScan.test.ts; do
  gitleaks dir --redact --no-banner --config .gitleaks.toml --exit-code 1 "$f" || echo "検出: $f"
done
```

- 中身を確かめるために `cat`・`grep` で行を表示しない（件数と位置だけを見る）。

## 6. 目標のカバレッジ

| 対象 | 目標 | 判定する所 |
|---|---|---|
| 画面の全体（`frontend/src`） | 行 80%・分岐 70%（`team.md`、`vitest.config.ts` の `thresholds`） | Build and Test が全件（`./gradlew verify` の `frontendCoverage`）で判定する。この単位の 3 つのテストのファイルは計測の分母に入らない（テストのファイルは除外）。この単位はその下限と除外を変えない |
| 道具のファイル `perf/redact-k6-evidence.mjs` | 行 80%・分岐 70%（同じ値） | この単位の確かめ（4節）。verify の関門には入らない（計画 5.1節 P5） |
| バックエンド | 変えない | この単位はバックエンドのソースに触れない |

## 7. 差し替え（モック）の方針

- `vi.mock` で `node:fs` を差し替えない。道具の `main(argv, io)` の `io` を差し替えて、(a) 表示（標準出力・標準エラー）を捕まえる、(b) 名前の替え（`rename`）を失敗させてコード 5 を作る、(c) 中断の登録（`onSignal`）で受け取った片付けを呼ぶ、の 3 つだけに使う。それ以外の読み書きは本物の一時のディレクトリで行う（実行する人の権限に頼らずに確かめるため）。
- 終わりのコードが本物のプロセスでも同じことは、`spawnSync(process.execPath, [道具の絶対パス, …])` で 3 つの口に 1 件ずつ確かめる。
- 性質のテストの数は fast-check の既定（100 回）のままにし、1 回の入力を大きくしない（数 MB の文字列と 64 MiB の境界は例のテストで別に確かめる）。

## 8. テストのデータ

- JWT の形の値は、テストの中の `makeJwtLike(…)` が `'eyJ'` と base64url の区間を実行の時につないで作る。テストのソース・見本のファイルに完成した JWT の形の文字列を書かない（公開のリポジトリで、`gitleaksScan` と `perfEvidenceScan` を自分で落とさないため。NFR8.71）。
- 招待のトークンに似た値・ログの行・summary json の見本（`metrics`・`root_group`・`setup_data`）は架空の値だけにする。メールアドレスを使うときは予約のドメイン（`example.com`）だけ。
- ファイルは各テストで `os.tmpdir()` の下に `fs.mkdtemp` で作った一時のディレクトリ（その下に `evidence/`）に置き、`afterEach` で消す。リポジトリの作業フォルダ（特に `aidlc/` の下の `evidence/`）には何も置かない。
- 64 MiB の境界: 上限ちょうどは `a` が続く中身（テキストの口）、上限＋1 バイトは `truncate` で作る疎なファイル（中身を読む前に大きさで断るため）で作り、テストの終わりに消す。
- 1 MiB の `-eyJ` の続く文字列と 2 MiB の長い文字列は、テストの中で `repeat` で作る。

## 9. Build and Test で流すこと

- 3.5節のコマンドと 4節のコマンド、5節の検索と Gitleaks。
- 計画の 11節 H1〜H7（わざと残存を作った 1 回の verify で、Gradle の失敗の要約を含む実際の出力に値が出ないことの確かめ（NFR8.64、基盤の設計の R-05）を含む）。
