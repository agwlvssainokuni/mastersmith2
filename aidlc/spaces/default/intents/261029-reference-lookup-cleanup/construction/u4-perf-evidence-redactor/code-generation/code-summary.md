# コード生成のまとめ — U4 u4-perf-evidence-redactor（Bolt B5）

単位 U4（library・S）を、計画 `code-generation-plan.md` のとおり Bolt B5 の 1 つで作った。作業ブランチは `feature/261029-reference-lookup-cleanup-b5`（`develop` の 9f28abd から）。この時点ではコミット・統合をしていない（計画 Step 22 は依頼者の承認の後）。生成の細かい記録は `generation-notes.md`。

## 1. 作ったもの

- **道具 `perf/redact-k6-evidence.mjs`**（新規。Node の組み込みだけ）。3 つの口:
  - JSON の口（契約 C7）: `node perf/redact-k6-evidence.mjs <入力の summary json> <evidence/ の中の出力>`。JWT の形を `[REDACTED-JWT]` に、上の階層の `setup_data` の下の文字列を `[REDACTED-SETUP]` に替え、字下げ 2・LF・末尾の改行 1 つの JSON で置く。
  - テキストの口: `--text <入力> <evidence/ の中の出力>`。中身全体に同じ置換を当て、改行・BOM・末尾の改行を変えない。
  - evidence の検査の口: `--scan <根>`。名前が `evidence` のディレクトリの下のすべてのファイルの中身とパスを調べる。
  - 終わりのコード: 0 成功・1 呼び出しの誤り・2 入力の誤り・3 キーの衝突・4 残存の検出・5 書き出しの失敗（3 つの口で同じ意味）。
  - 公開の名前: `JWT_MARK`・`SETUP_MARK`・`MAX_INPUT_BYTES`（64 MiB）・`BINARY_EXTENSIONS`（9 つ、凍結）・`redactJwt`・`findResidual`・`redactTree`・`findUnredactedSetup`・`checkAndWrite`・`main`。`export default` は無い。
  - 一時ファイル `.<出力の名前>.<プロセス番号>.<乱数 8 桁の 16 進>.tmp` を `wx` で作り、名前を替えて置く。失敗・中断で消す。
- **テスト**（新規、`frontend/src` の直下、`// @vitest-environment node`）: `perfEvidenceRedactor.test.ts`（純粋な関数と性質）・`perfEvidenceRedactorCli.test.ts`（JSON の口・テキストの口）・`perfEvidenceScan.test.ts`（evidence の検査の口）。
- **`build.gradle.kts`**（A1）: `perfEvidenceScan`（`verifySecurity`）・`perfToolFormatCheck`（`verifyFormat`）・`perfToolLint`（`verifyLint`）を足し、4 つの段の説明を直した。`verifySecurity` は一覧と説明の 2 か所の両方。
- **`frontend/scripts/check-license-header.mjs`**（A2）: `perf/` の `.mjs`・`.js` も歩き、表示のパスをリポジトリのルートからの相対にそろえた。
- **`perf/README.md`**（A3）: 末尾に H2「記録（evidence/）に置く前の伏せ字」と H3 6 つ（伏せる対象・唯一の入口と終わりのコード・対象外の標準出力とログ・置いた後の確かめ・台本の決まり・一時ファイル）。

## 2. 変えたファイル

アプリのソースではない道具とテストと検査の設定、7 ファイル（作成 4・変更 3・削除 0）。一覧は `source-manifest.json`。

| ファイル | 種類 |
|---|---|
| `perf/redact-k6-evidence.mjs` | 作成 |
| `frontend/src/perfEvidenceRedactor.test.ts` | 作成 |
| `frontend/src/perfEvidenceRedactorCli.test.ts` | 作成 |
| `frontend/src/perfEvidenceScan.test.ts` | 作成 |
| `build.gradle.kts` | 変更 |
| `frontend/scripts/check-license-header.mjs` | 変更 |
| `perf/README.md` | 変更（末尾に節を足しただけ。既存の節の文は変えていない） |

- 変えていないもの: `frontend/vitest.config.ts`・`tsconfig.json`・`eslint.config.js`・`.oxlintrc.json`・`.prettierrc.json`・`.prettierignore`・`package.json`・`package-lock.json`・`.npmrc`、`.github/workflows/ci.yml`、`.pre-commit-config.yaml`、`.gitleaks.toml`、`perf/k6/scenarios.js`、バックエンドのソースとテスト、境界テスト、SpotBugs の除外、カバレッジの下限と除外。新しい依存は無い。

## 3. 計画 5.1節 P1〜P7 の結果

| # | 結果 |
|---|---|
| P1 | テストは計画どおり 3 ファイルを `frontend/src` の直下に置いた。補助の関数は各ファイルの中に置き、`src` に非テストのファイルは無い |
| P2 | `vitest.config.ts` は変えていない。3 ファイルとも `unit` の project で流れた |
| P3 | `frontend/` の外の `.mjs` を `import()` で読めた（Step 3）。ADR-006 の切り替え先は使っていない |
| P4 | 3 ファイルとも `node` の環境。各ファイルに `window` が無いことの 1 件がある |
| P5 | `coverage.include` は変えていない。道具だけのカバレッジ（`unit-test-instructions.md` 4節）: 行 94.2%（423/449）・分岐 90.61%（193/213） |
| P6 | Prettier・oxlint は `build.gradle.kts` から画面の設定のファイルで道具 1 つに当てた。設定のファイルを明示すれば `frontend/` の外のファイルにも効くこと（違反の写しで oxlint の `no-eval`・`no-unused-vars`、Prettier の書式の違いが出ること）を確かめた。ライセンスヘッダーは `perf/` の 9 ファイル（既存 8・道具 1）が組み込む前の判定で通った |
| P7 | `tsconfig.json` は変えていない。テストは `interface PerfEvidenceRedactorModule` で型を付け、`tsc --noEmit` が通った |

## 4. 処理の時間（Step 8・Step 13）

- 正規表現の形は 2 乗の時間（64 KiB で 1,322.5 ms、1 MiB は約 340 秒の見込み）だったため、形の定義を保ったまま 1 回のなめに替えた（計画 4節の見込みどおり）。さらにカバレッジの計測の下で 64 MiB の例が 5 秒を超えたため、区間の切れ目を戻りの無い粘着の正規表現で読む形にした（時間の上限は延ばしていない）。
- 替えた後: 1 MiB 1.4 ms・16 MiB 21.6 ms・64 MiB 84.4 ms（`redactJwt`＋`findResidual`）。
- **64 MiB に近い入力の `--scan` の所要時間**: `-eyJ` の続く 67,108,864 バイトで real 0.09〜0.10 秒（3 回）。区間の多い悪い側の形で 0.75 秒、JWT の形が約 745 万件ある形で 1.36 秒。verify の時間を目に見えて伸ばさない。
- 定義の正規表現との一致は、性質のテスト（1,000 回）と一度きりの 30 万件の突き合わせで食い違い 0 件。

## 5. 導入の前の一度の実行（Step 14）

`node perf/redact-k6-evidence.mjs --scan aidlc`: 調べたファイル 97 件（`evidence/` は 2 つ）、バイナリとして読んだもの 0 件、リンク 0 件、検出 0 件、入力の誤り 0 件。終わりのコード 0。

## 6. 台本の読み取りの確かめ（Step 17）

`perf/k6/scenarios.js` の `console.log` は 5 か所（787・816・934・956・1006 行）で、件数・ページ・VU の番号・試験用の宛先だけを出す（計画の「6 か所」は 5 か所だった）。`fail()`・タグ・check の名前に秘密は無い。リフレッシュトークンの Cookie は VU ごとの状態だけ（2722・2729 行）、Authorization ヘッダーは要求ごとに組み立てる（774・877・890・1022・1337・1364 行など）ため、summary json に入らない。`setup()`（726〜771 行）が返すトークンは `setup_data` に入り、`[REDACTED-SETUP]` になる。止める条件には当たらない。

## 7. テストと verify（Step 20）

| テストのファイル | passed |
|---|---|
| `frontend/src/perfEvidenceRedactor.test.ts` | 34 |
| `frontend/src/perfEvidenceRedactorCli.test.ts` | 45 |
| `frontend/src/perfEvidenceScan.test.ts` | 36 |
| 計 | 115（skipped・todo 0） |

- `./gradlew verify`（colima の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を付け、`caffeinate -dims` で包んだ）: BUILD SUCCESSFUL、終わりのコード 0、所要 2 分 44 秒（2026-10-30T15:31:59Z〜15:34:44Z）。流した後の `pmset -g log` に Sleep・DarkWake は 0 件。
- 新しいタスク `perfToolFormatCheck`・`perfToolLint`・`perfEvidenceScan` が流れて成功した（`perfEvidenceScan`: 調べたファイル 97 件・検出 0 件・入力の誤り 0 件）。
- 画面のテスト（`frontendTest`・`frontendCoverage`）: 229 ファイル・2,092 件 passed（U4 の 3 ファイル 115 件を含む）。全体のカバレッジの下限も通った。
- バックエンドのテスト（`:backend:test`・`:backend:integrationTest`）は入力が変わらないため UP-TO-DATE で飛ばされた（バックエンドのソースは変えていない。件数は報告しない。`project.md` の決まり）。そのため 2 分 44 秒は全件を流した時間ではない。

## 8. 計画との差

- 1 MiB の例を正規表現の形のまま Vitest で流さず、小さい大きさの測りの伸び方で判断した（同期の正規表現は Vitest の時間の上限で止まらず固まるため）。
- 1 回のなめを、1 文字ずつのループではなく、区間の切れ目を粘着の正規表現で読む形にした（カバレッジの計測の下での時間のため）。
- `io` の口に `close`・`readdir` を足し、差し替えを計画の 3 つのほか、`--scan` の読めないファイル・ディレクトリ（`readFile`・`readdir`）と想定外の誤り（`stat`）の例にも使った。
- 想定外の例外は種類の名前だけを表示して終わりのコード 2、中断は 130・143 で終える。
- `perf/k6/scenarios.js` の `console.log` は計画の「6 か所」でなく 5 か所。
- テストの件数は計画の見積もり（約 80 件）より多い 115 件。

## 9. 依頼者に確かめたいこと

- 上の 8節の差（特に、1 回のなめを粘着の正規表現で読む形にしたこと、想定外の例外を終わりのコード 2 にしたこと）でよいか。
- 64 MiB の `--scan` の所要時間（4節）と、1 回のなめへの切り替えの判断を伝える（計画 4節・`security-design.md` 2.1節）。
- コミットの区切り（計画 9節の C1〜C3）と、統合の前の `aidlc/` の記録のコミット。

## 10. Build and Test に引き継ぐこと

計画 11節の H1〜H7 のとおり（わざと残存を作った 1 回の verify で実際の出力に値が出ないこと、ファイルごとの passed の件数、CI の試行ごとの記録、実際の summary json の `--scan` と `gitleaks dir`、verify と CI の時間、統合の後の検索、Intent の最後の E2E）。`traceability.json` では AC4.1.6・NFR5.62・NFR8.64・NFR8.69・BR4.3 を Deferred（Build and Test）にした。
