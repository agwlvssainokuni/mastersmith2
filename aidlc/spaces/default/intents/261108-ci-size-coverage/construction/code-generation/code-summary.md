# コードの生成の要約（Intent 261108-ci-size-coverage）

- 段: Code Generation（zero-Unit、scope: express、depth: Minimal）
- 作業ブランチ: `feature/261108-ci-size-coverage`（`develop` の `d4bc0d65` から作った。コミットはまだ積んでいない）
- 計画: `code-generation-plan.md`（Step 1〜Step 9）、確かめの手順: `unit-test-instructions.md`

## 1. 作った・変えたファイル

| ファイル | 種類 | 中身 |
|---|---|---|
| `scripts/program-size.mjs` | 新規 | 依頼者の手元の Python の台本を Node に移した規模の台本。分類（`classify`）・外すもの（`EXCLUDE`）・集計・表は移す元のとおり。`--base <commit>` で `build/program-size-base` に取り出して差の列を足す。関数（`countSize`・`countBase`・`computeDiff`・`renderReport`・`collectMeta`・`classify` など）を `export` する |
| `scripts/ci-summary.mjs` | 新規 | CI の要約を「規模 → テストの件数 → カバレッジ」の順に組み立て、`$GITHUB_STEP_SUMMARY`（無ければ標準出力）に書く。どの失敗でも終了コード 0 |
| `.github/workflows/ci.yml` | 変更 | `verify` の job の最後に3つの手順を追加（cloc を入れる・要約を書く・HTML の報告を保存する）。先頭のコメントに1行追加。既存の手順・きっかけ・`release` の job は変えていない |
| `docs/verification.md` | 変更（追記） | 「規模・テストの件数・カバレッジの報告（CI の要約）」の節を「リリースの前の確かめ」の前に追加 |

変えていないもの: `backend/src`・`frontend/src`・`vendor/`・ルートと backend の `build.gradle.kts`・`.env`（開いていない）。新しい npm の依存は足していない（Node の組み込みの `node:child_process`・`node:fs`・`node:path`・`node:url` だけ）。

## 2. 実装の要点

### `scripts/program-size.mjs`

- git が管理するファイルの一覧は `git ls-files -z` で読む（移す元は `-z` なしだが、このリポジトリに ASCII の外の文字のパスは 0 件であることを確かめたため、同じ一覧になる）。cloc は移す元と同じ `--by-file --json --quiet --list-file=-` に一覧を標準入力で渡す。
- 集計の並びは移す元と同じ（cloc の出力の順に初めて現れた順を保ち、コードの行の多い順に安定な並べ替え）。サーバーの機能ごとの表は、移す元が集合の並び（Python のハッシュの種で変わりうる）だったため、名前の順に並べてからコードの行の多い順に並べる形にした（下の 3.2 で、種を変えても一致することを確かめた）。
- 小数の書き方は Python の `{:.Nf}` に合わせた（JavaScript の `toFixed` はちょうど半分を大きい方へ丸めるため、そのときだけ偶数へ丸める）。整数は3桁ごとのカンマ。
- 「数えた対象」: 数えた日時（`ja-JP` の時間帯の略称。手元は JST）、ブランチ（`GITHUB_REF_NAME`、無ければ `git rev-parse --abbrev-ref HEAD`）と先頭とその日時、`origin/main` の先頭（無ければ「—」）、数えたファイルの数、cloc の版。未コミットの件数は出さない（FR1.7）。台本と報告に依頼者の手元の置き場のパスは書いていない（FR1.6）。
- `--base`: 空・0 の並び・コミットの名前の形でない値・履歴に無いコミットは、取り出さずに理由を返す。取り出しは `git worktree add --detach`、後片付けは `finally` で `git worktree remove --force`（消せなければフォルダを消す）と `git worktree prune`。前の実行の残りがあれば先に消す。比べる元にしかない分類も、今の値を 0 として差の表に載せる。
- `--base` を渡したときは「数えた対象」に「比べる元」の行を足し、出せないときは集計の頭に「前の回との差は出せない: <理由>」の1行を出す（標準エラーにも同じ1行）。`--base` を渡さないときの出力は移す元と同じ形（差の列も「比べる元」の行も出ない）。
- 報告に載せる比べる元の値は、コミットの名前に使う文字（英数字と `._/~^@-`）だけに限った（引数の値をそのまま Markdown に入れないため。NFR3）。
- 失敗（git・cloc が無いなど）は、理由を標準エラーに出して終了コード 1。

### `scripts/ci-summary.mjs`

- 引数: `--base`（空可）・`--backend-reports`（既定 `backend/build`）・`--frontend-coverage`（既定 `frontend/coverage`）。
- 規模: `program-size.mjs` の関数を呼び、見出しを1段下げ、中分類と言語より後の表を `<details>` で畳む。比べる元は CI が渡す値（空なら理由の1行）。
- テストの件数: `test-results/test/` と `test-results/integrationTest/` の `TEST-*.xml` の最初の `<testsuite>` の属性（tests・failures・errors・skipped）を足す。失敗は failures と errors の和、通ったは tests − 失敗 − 飛ばした。
- バックエンドのカバレッジ: JaCoCo の XML を正規表現で読む（XML の部品を使わず、DOCTYPE の外部の DTD を読まない）。`<package>` の塊を外した残りの `counter` が全体、`<package>` の中から `<class>`・`<sourcefile>` を外した残りがパッケージの値。下限（全体とパッケージごとの LINE・BRANCH）と `packagesJudgedByTotal` は `backend/build.gradle.kts` の `jacocoTestCoverageVerification` の設定と一覧から読む（読めなければ警告と理由を出し、差を「—」にする）。パッケージごとの表に、下限との差（ポイント）と印（満たす・下限まで 5 ポイント未満・下限を下回る・全体で判定（packagesJudgedByTotal））を付け、`<details>` で畳む。
- 画面のカバレッジ: `coverage-summary.json` の `total` の4つ（行・分岐・関数・文）は json の `pct` をそのまま載せ、領域（`features/<機能>`・`app/<名前>`・`shared/<名前>`・`(src 直下)`・`(src の外)`）はファイルごとの covered と total を足して求め、`<details>` で畳む。
- 割合は小数 2 桁で切り捨て（`coverage-summary.json` の `pct` と同じ形。下限の近くで切り上げて「満たす」に見せないため）。
- 報告のファイルが無い欄は「報告なし（./gradlew verify が途中で落ちた見込み）」と書き、`::warning title=CI の要約::…` を出す。想定外の失敗も `try` で受けて警告にし、終了コードは常に 0（FR4.5）。
- 載せるのは分類・パッケージ・領域の名前と行数・件数・割合だけ。JUnit の XML の `hostname` や `coverage-summary.json` のファイルの絶対パスは載せない（NFR3）。

### `.github/workflows/ci.yml`

- WAR の保存の後に、次の3つを足した（どれも `if: always()` と `continue-on-error: true`）。
  1. 「規模を数える道具（cloc）を入れる」: `sudo apt-get update && sudo apt-get install -y cloc`
  2. 「規模・テストの件数・カバレッジを実行の要約に書く」: `node scripts/ci-summary.mjs --base "$SIZE_BASE"`。`SIZE_BASE` は `github.event_name == 'push' && github.ref_type == 'branch'` のときだけ `github.event.before`、それ以外（手動の実行・タグ）は空。式の値は `env` を通して渡す（`run` の中に式を直接書かない）。
  3. 「カバレッジの HTML の報告を成果物として保存する」: `actions/upload-artifact@cf430e030ddbb5b0abf93d22962f4752f3646cd9 # v7.0.2`（既存と同じ固定）、名前 `coverage-html-${{ github.sha }}`、`backend/build/reports/jacoco/test/html/` と `frontend/coverage/`、14 日、`if-no-files-found: ignore`。

## 3. 確かめの結果（Step 1・2・4・6・7）

確かめの出力は `build/`（git の対象外）の下に置いた。どれもリポジトリのルートで流した。

### 3.1 Step 1・2（道具）

| 確かめ | 結果 |
|---|---|
| `git checkout -b feature/261108-ci-size-coverage` | 切り替えた |
| `cloc --version` | 2.10 |
| `node --version` | v26.13.0 |
| `python3 --version` | 3.9.6 |
| `git check-ignore -v build/x` | `.gitignore:48:build/` で対象外 |
| git が管理するパスのうち ASCII の外の文字を含むもの | 0 件 |
| 移す元の Python の台本を流す（出力は `build/program-size-python.md`） | 終了コード 0 |

### 3.2 Step 4（`scripts/program-size.mjs`、NFR5・FR1.8）

| コマンド | 終了コード | 結果 |
|---|---|---|
| `node scripts/program-size.mjs > build/program-size-node.md` | 0 | Markdown が出た |
| Python 版と Node 版の「## 要点」から最後までの `diff` | 0 | **差なし**（要点・大分類・中分類・中分類と言語・サーバーの製品の層と機能・サーバーのテストの種類・機能ごとの製品とテスト・画面の製品とテストの領域のすべての表） |
| 「数えた対象」の比べ | — | 数えたファイルはどちらも 2,461 件、`main` の先頭はどちらも `b2990415`（手元は `main` と `origin/main` が同じ）。違いは日時・ブランチの行の未コミットの件数（Node 版は出さない）・台本の名前だけ（計画どおり比べから外した行） |
| Python 版をハッシュの種 1・2・3 で流して同じ `diff` | 0（3回とも） | 機能ごとの表の並びも一致 |
| `node scripts/program-size.mjs --base HEAD~1 > build/program-size-node-base.md` | 0 | 大分類と中分類に差の列が出た。HEAD~1（`74970ab1`）は記録だけのコミットのため差はすべて 0。「比べる元」の行に `74970ab1` と日時 |
| `node scripts/program-size.mjs --base HEAD~30 > build/program-size-node-base30.md` | 0 | 差が出ることを確かめた（例: 大分類のテスト +317 行・+1 ファイル、ビルド・検査・運用 +351 行・+2 ファイル） |
| `node scripts/program-size.mjs --base 0000000000000000000000000000000000000000 > build/program-size-node-nobase.md` | 0 | 差の列が「—」、「前の回との差は出せない: 比べる元が 0 の並び（ブランチの最初のプッシュなど）で、前の回が無い」の1行 |
| `node scripts/program-size.mjs --base deadbeefdeadbeef > build/program-size-node-missing.md` | 0 | 差の列が「—」、「比べる元のコミット deadbeefdeadbeef を取り出せない（履歴に無い）」 |
| ``node scripts/program-size.mjs --base '`x|y`'`` | 0 | 「比べる元の値がコミットの名前の形でない」（値を報告に載せない） |
| 各回の後の `ls build/program-size-base` と `git worktree list` | — | フォルダは残らず、作業ツリーは本体の1つだけ |
| `GITHUB_REF_NAME=develop node scripts/program-size.mjs` | 0 | ブランチの欄が `develop` になった |
| cloc の無い PATH で `node scripts/program-size.mjs` | 1 | 標準エラーに「規模を数えられない: cloc が見つからない（…）」 |
| 小数の丸め（`fixed(0.25,1)`・`fixed(2.5,0)`・`fixed(1.25,1)`・`fixed(2.675,2)`） | — | `0.2`・`2`・`1.2`・`2.67`（Python と同じ） |

最後の形の台本（比べる元の値の手当てを足した後）で、Python 版との `diff` と `--base` の確かめを流し直し、同じ結果だった。

### 3.3 Step 6（`scripts/ci-summary.mjs`、FR2〜FR4.5）

手元の直近の報告を使った（`jacocoTestReport.xml` と `coverage-summary.json` は 2026-11-08 13 時台の全件の実行のもの）。`./gradlew` のテストは流していない。

| コマンド | 終了コード | 結果 |
|---|---|---|
| `node scripts/ci-summary.mjs --base HEAD~1 > build/ci-summary-local.md` | 0 | 規模 → テストの件数 → カバレッジの順に出た。警告なし |
| 全体の値と報告の比べ（バックエンド） | — | 行 14,904 / 15,258 = 97.67%、分岐 6,375 / 6,966 = 91.51%。XML の根の直下の `counter`（LINE missed 354・covered 14,904、BRANCH missed 591・covered 6,375）と**一致** |
| 全体の値と報告の比べ（画面） | — | 行 95.49%（9,031 / 9,457）・分岐 91.01%・関数 96.38%・文 95.37%。`coverage-summary.json` の `total` と**一致** |
| テストの件数と、`grep`・`perl` での別の数えの比べ | — | 単体テスト 312 クラス・2,937 件（飛ばした 0）、結合テスト 278 クラス・1,716 件（飛ばした 10）、失敗 0。**一致** |
| パッケージの数と印 | — | パッケージ 71 個（XML の `<package` も 71）、「全体で判定（packagesJudgedByTotal）」の印 7 個。例: `common.health` の行 79.24%（XML の package の直下の LINE 42 / 53）・下限との差 −0.76 |
| 画面の領域の表のファイルの数の和 | — | 325（`coverage-summary.json` の 326 項目から total を除いた数と一致） |
| `GITHUB_STEP_SUMMARY=build/ci-summary-empty.md node scripts/ci-summary.mjs --backend-reports build/no-such-dir --frontend-coverage build/no-such-dir; echo "exit=$?"` | `exit=0` | テストの件数の2行・バックエンドと画面のカバレッジの欄に「報告なし（./gradlew verify が途中で落ちた見込み）」、標準出力に `::warning::` が4つ。規模の欄は「比べる元が渡されていない（手動の実行・タグのプッシュなど）」の理由で差が「—」 |
| cloc の無い PATH で `node scripts/ci-summary.mjs --base HEAD~1` | 0 | 規模の欄に「作れなかった（cloc が見つからない…）」と警告1つ。ほかの欄は出た |

### 3.4 Step 7（`.github/workflows/ci.yml`）

- Ruby の YAML の読み込み（`ruby -ryaml`）で読めることを確かめた。`verify` の手順は 7 → 10。足した3つは `if: always()`・`continue-on-error: true` で、`SIZE_BASE` の式と成果物の設定（名前・2つのパス・14 日・`ignore`）が意図どおりに読めた。`on`（develop へのプッシュ・`v*` のタグ・手動の実行）と jobs（`verify`・`release`）は変わらない。
- `git diff --stat` で `ci.yml` は追加だけ（28 行の追加）だった時点で確かめた（先頭のコメントの1行を足した後も追加だけ）。
- CI での実際の動き（apt の cloc・要約の表示・成果物）は、Build and Test の CI の1回の実行で確かめる。

### 3.5 この段で流していないテスト

- 既存の全件のテスト（`./gradlew verify`）: アプリのソースを変えていないため、この段では流していない。Build and Test で全件の `./gradlew verify` を流して緑のままであることを確かめる（計画と `unit-test-instructions.md` のとおり）。
- CI の1回の実行（NFR1 の時間・要約の表示・成果物・`apt-get install cloc` が使えるかの前提）: Build and Test で確かめる。

## 4. 要件との対応

| 要件 | 満たした場所 | Step | 状態 |
|---|---|---|---|
| FR1.1〜FR1.4 | `scripts/program-size.mjs`（`classify`・`EXCLUDE`・`countSize`・`renderReport`） | Step 3・4 | OK（Python 版と表が一致） |
| FR1.5 | `scripts/program-size.mjs`（「道具」の行に cloc の版） | Step 3 | OK |
| FR1.6 | `scripts/program-size.mjs`（先頭のヘッダー、手元の置き場のパスを書かない） | Step 3 | OK |
| FR1.7 | `scripts/program-size.mjs`（`collectMeta`） | Step 3・4 | OK |
| FR1.8 | `scripts/program-size.mjs`（`countBase`・`computeDiff`） | Step 3・4 | OK |
| FR1.9 | `scripts/program-size.mjs`（直接流すと標準出力に Markdown） | Step 3・4 | OK |
| FR2.1〜FR2.5 | `scripts/ci-summary.mjs`（`parseJacoco`・`parseGradleFloors`・画面の領域・`<details>`） | Step 5・6 | OK |
| FR3.1 | `scripts/ci-summary.mjs`（`readJUnitDir`） | Step 5・6 | OK |
| FR4.1〜FR4.4・FR4.6 | `.github/workflows/ci.yml` | Step 7 | OK（CI での動きは Build and Test） |
| FR4.5 | `scripts/ci-summary.mjs`（報告なし・警告・終了コード 0）と `ci.yml` の `continue-on-error` | Step 5〜7 | OK |
| FR5.1 | `docs/verification.md` | Step 8 | OK |
| NFR1 | — | Step 7 | Deferred（Build and Test の CI の実行で測る） |
| NFR2 | `.github/workflows/ci.yml`（`./gradlew verify` の手順は変えず、足した手順は `continue-on-error`） | Step 5・7 | OK |
| NFR3 | `scripts/ci-summary.mjs`・`scripts/program-size.mjs` | Step 3・5 | OK |
| NFR4 | `scripts/program-size.mjs`（cloc の版を載せる） | Step 3 | Deferred（手元と CI の値の一致は Build and Test で比べる） |
| NFR5 | `scripts/program-size.mjs` | Step 4 | OK（3.2 のとおり差なし） |

## 5. 決まりとの差と、計画との差

- **自動テストを置かない（依頼者の決定 Q4: C・F1）**: Testing Contract の Minimal（要件ごとの単体テスト）と `team.md` の「テストのない機能は完成とみなさない」に対し、依頼者の決定で、この Intent の台本（`scripts/program-size.mjs`・`scripts/ci-summary.mjs`）には単体テストとライセンスヘッダーの検査を足していない。代わりに 3.2・3.3 の確かめと、Build and Test の CI の1回の実行で確かめる。台本の先頭にはライセンスヘッダーを置いた（検査の対象の外だが、`project.md` の Mandated に合わせた）。
- **計画に足したもの（計画の範囲の中の細部）**:
  - HTML の報告の保存の手順にも `continue-on-error: true` を付けた（計画は `if: always()` と `if-no-files-found: ignore` だけ）。成果物の置き場の一時の失敗で CI を失敗にしないため（NFR2・FR4.5 の「要約の手順の成否は CI の結果に響かない」）。
  - 画面のカバレッジの全体の表に、`frontend/vitest.config.ts` の `thresholds`（行 80・分岐 70）を読んだ「下限」と「下限との差」の列を足した（関数・文は下限が無いため「—」）。
  - `--base` を渡したときの「比べる元」の行と、比べる元の値の形の確かめ（NFR3）。
  - サーバーの機能ごとの表の同じ行数の並びを、名前の順に固めた（移す元はハッシュの種で変わりうる並び）。
- 計画の手順の中身（Step 1〜8）は計画どおり。手順の並べ替え・省きは無い。

## 6. 気になる点（Build and Test への申し送り）

- CI の `apt-get install cloc` の版は手元（2.10）と違う見込み（`ubuntu-latest` の apt の版）。版が違うと規模の値がずれうる（NFR4）。Build and Test で CI の要約の「道具」の行の版と、同じコミットの手元の値を比べる。
- 手元の `backend/build/reports/jacoco/test/html/` は 2026-10-25 の古いものだった（XML は 2026-11-08）。CI はまっさらな環境で毎回作るため、成果物には影響しない。

## 7. 依頼者に確かめたいこと

なし。

（指揮役の確かめ: 2026-11-08 22:22 に手元で `node scripts/ci-summary.mjs --base HEAD~1` を流し、規模の表と差の列が出ることを見た。手元では、作業ツリーの未コミットの変更も今の中身として数えるため（移す元と同じ）、差の列にコミット前の `ci.yml`・`docs/verification.md` の分が出る。git の対象に入っていない新しいファイル（コミット前の `scripts/*.mjs`）は数えない。）
