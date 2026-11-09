# コード生成のまとめ（Intent 261108-ci-checks-release）

zero-Unit（scope: bugfix）。作業ブランチ `feature/261108-ci-checks-release`。Bolt 1〜3 はコミット済み、Bolt 4・5 は未コミット（コミットは依頼者に尋ねてから積む）。Bolt 6（リリース）は Build and Test と配備の段で行う。

## 1. 作った・変えたファイル

| Bolt | コミット | ファイル | 内容 |
|---|---|---|---|
| 1 `scripts/` の検査 | `69624004` | `frontend/scripts/check-license-header.mjs` | ライセンスヘッダーの検査の対象にルートの `scripts/` を足した |
| 1 | 〃 | `frontend/package.json` | `format`・`format:check` に `prettier --config .prettierrc.json ../scripts`、`lint` の後ろに `lint:scripts`（ルートへ移って oxlint と ESLint を `scripts` に当てる） |
| 1 | 〃 | `frontend/eslint.config.js` | `rootScriptsRule`（`scripts/**/*.{js,mjs}` に Node の大域の名前などの決まり） |
| 1 | 〃 | `scripts/program-size.mjs` | 書式と lint の指摘の直し（動きは変えない。`toFixed(100)` の1行だけ `oxlint-disable-next-line oxc/number-arg-out-of-range`） |
| 2 R-07 と一覧の撤去 | `68917323` | `backend/src/test/java/cherry/mastersmith/common/health/TimeBoundedDbHealthIndicatorTest.java` | テスト2件（実行の枠が確認を受け付けない・待っている間の割り込み） |
| 2 | 〃 | `backend/build.gradle.kts` | `packagesJudgedByTotal` と `excludes` を消し、経緯を短い説明文に残した |
| 2 | 〃 | `scripts/ci-summary.mjs` | 一覧の読み取りと「全体で判定」の印を外した |
| 2 | 〃 | `docs/verification.md` | 22・42・73〜81・160・163 行（R-07 の節を消した） |
| 3 CI の並べ | `5e6a7919` | `.github/workflows/ci.yml` | `workflow_dispatch` の入力 `it_forks`、`env` の `IT_FORKS_DEFAULT: "3"`、`./gradlew verify -PitForks="$IT_FORKS"` |
| 4 台本 A1〜A3 | 未コミット | `scripts/ci-attempts.mjs`（新規） | A2: 範囲のコミットごとの CI の実行と試行ごとの結論、失敗した試行の job・手順・落ちたテスト |
| 4 | 〃 | `scripts/ops-readonly-check.mjs`（新規） | A1: コンテナの版とダイジェストと `compose.yaml` の比べ、環境変数の名前、WAR の SHA-256、`.env` の時刻、`docker system df` |
| 4 | 〃 | `scripts/count-db-copy.mjs`（新規） | A3: 内部DB の複写を読み取りで開いて組ごとに数える（Java のコードを中に持ち、実行ごとに `build/` の下へ書いて動かす） |
| 4 | 〃 | `.pre-commit-config.yaml` | Prettier の確認の対象に `scripts/` の `.js`・`.mjs` を足した（フック `prettier-check-scripts`。依頼者がこの回に足した決定） |
| 4 | 〃 | `docs/verification.md` | 「リリースの前の確かめ」に 3（A2 の使い方）を足した（FR5.4） |
| 4 | 〃 | `docs/internal-db.md` | 「内部DBの複写を数える」に A3 の使い方と、組と前の道具の対応の表を足した（FR5.4） |
| 4 | 〃 | `docs/operations.md` | A1 の使い方の節を足した（FR5.4） |
| 5 決まりと手順書 | 未コミット | `docs/verification.md` | 結合テストの段の表の `-PitForks` の欄・`verify-graph-check.mjs` の説明・`-PitForks` の節（CI でも使う）・「台本の扱い」（`scripts/` は検査の対象） |
| 5 | 〃 | `aidlc/spaces/default/memory/team.md` | 19 行（全件の `verify` の場面から一覧の場面を外す）・21 行（`-PitForks` は CI でも使う）・54 行（一覧の行を、消した経緯と「すべてのパッケージに当てる」に）・155 行（「手を入れる」の読みをその行に書いた）。それぞれに「（2026-11-09 Intent 261108-ci-checks-release で書き換え）」 |
| 5 | 〃 | `aidlc/spaces/default/memory/project.md` | 131 行（Mandated の全件の `verify` の場面から一覧の場面を外す）。同じ印 |

`src/main`・`vendor/` は変えていない。新しい npm の依存は足していない。`aidlc/spaces/default/memory-archive/` は変えていない。

## 2. 各 Step の確かめの結果

### Bolt 1〜3（1回目）

- Step 1・4: `npm --prefix frontend run license:check`・`format:check`・`lint` が通る。`scripts/` の1本のヘッダーを崩すと `license:check` が落ちることを確かめて戻した。`node scripts/program-size.mjs`・`GITHUB_STEP_SUMMARY= node scripts/ci-summary.mjs`・`node scripts/verify-graph-check.mjs` が前と同じに動く（`verify-graph-check.mjs` は 8 passed。`verify` には入れていない）。
- Step 5: `./gradlew :backend:test --tests 'cherry.mastersmith.common.health.*'` で `common.health` のテスト 8 件が通る（単体テストだけの記録で行 53/53）。パッケージの値の実測は Build and Test の全件の `verify` で行う。
- Step 6〜8: `--dry-run` でタスクの組み立てが壊れていないこと。`ci-summary.mjs` の印の列に「全体で判定」が出ないこと。
- Step 10: `ci.yml` が YAML として読める。`verify-graph-check.mjs` が 8 passed。

### Bolt 4・5（2回目）

- 検査: `npm --prefix frontend run license:check`・`format:check`・`lint`（`lint:scripts` を含む）がすべて終了コード 0。新しい3本は Prettier で整えた。oxlint の `no-control-regex`（ESC の文字の正規表現）は、正規表現を文字の番号から作る形に直して通した（指摘を外す注釈は使っていない）。
- A2 `scripts/ci-attempts.mjs`:
  - `node scripts/ci-attempts.mjs --from b2990415`（`--to` は既定の `origin/develop`、`git fetch` の後）: 23 コミット。CI の実行のあるもの 7 件（すべて試行 1 で success）、実行の無いもの 16 件、先頭 `4fe8e790` は通っている、終了コード 0。
  - 手での照合: `gh run view` で 42311006177（`6aa79768`）・42318201944（`369f6577`）・42478385164（`9fd4f7f1`）・42489430268（`4fe8e790`）がどれも試行 1・success、`gh run list --commit` で `13c00c35`・`71a8b436` が 0 件。台本の表と一致した。
  - 範囲に失敗が無いため、前の Intent の再実行のあるコミットで失敗の道も確かめた: `--from ecb688d4~1 --to ecb688d4` で「1: failure → 2: failure → 3: success」、試行 1 は Vitest の `TimeZoneOptionsPerformance.test.ts` と `> Task :frontendTest FAILED`、試行 2 は `conversionProperties.test.ts` と `> Task :frontendCoverage FAILED` を拾い、再実行で通った実行 1 件と出た。`gh run view 41679218431 --attempt 2` が failure で一致。`--from 9e9a000c~1 --to 9e9a000c --no-logs` は先頭が通っていないで終了コード 1。存在しない範囲（`--from nosuchref`）は終了コード 3。
- A1 `scripts/ops-readonly-check.mjs`（colima は止まったまま、`DOCKER_HOST` なし）:
  - 流す前にソースで確かめた: `.env` は行ごとに最初の `=` の左側だけを切り出して名前の形に当て、右側は切り出さない。`docker inspect` は `--format` で名前・イメージ・サービスのラベル・状態だけを取り出す（`Config.Env` を読まない）。`DOCKER_HOST` の値と道具の標準エラーは出さない。
  - 結果: 1 と 5 に「コンテナが無い（Docker の実行環境に繋がらない…）」、2 は `.env` 18 個・`.env.example` 4 個と片方にだけある名前、3 は WAR の SHA-256、4 は `.env` の時刻が出た。終了コード 0。
  - 出力の検索: `=` を含む行 0 行、`/Users`・ユーザー名・`@example`・`password`・`secret` を含む行 0 行。分からない引数は終了コード 3。
  - 動いているコンテナでの確かめ（食い違いの表示を含む）は Build and Test・配備の段に回す。
- A3 `scripts/count-db-copy.mjs`（指示どおり流していない）:
  - 中の Java のコードは、DB を開かずにコンパイルだけを確かめた（`java --source 25` で引数なしに起こし、`main` の `a[0]` で止まる＝コンパイルは通る）。
  - `.env` を読む前に止まる誤りの道だけを流した: `--copy` なし・分からない組・`--since` の形の誤り・存在しない複写で、それぞれ終了コード 3。作業の置き場（`build/count-db-copy/`）は残っていない。
  - 前の道具との件数の照合（FR5.5）は、指揮役が依頼者の許しを得て流す。
  - **照合の結果（指揮役、2026-11-09、依頼者の許しを得て流した）**: 複写 `mastersmith-data-202611070636-after-tz-options-perf.tgz`、起点 `2026-11-06T00:00:00Z` で、`node scripts/count-db-copy.mjs --set basic,roles,reflookup`（終了コード 0）と前の道具 `count-db.sh`・`count-db2.sh`・`count-db3.sh`（どれも終了コード 0）を流した。`basic` と `count-db.sh`、`roles` と `count-db2.sh`、`reflookup` と `count-db3.sh` の全項目の件数が一致した（例: 利用者 3・有効な管理者 1・DSL の版 8・ロール 3・監査の全体 339・起点からの監査 15、種類と結果ごとの件数も同じ。スキーマの版 14）。4つの出力に `@` を含む行は無かった。作業の置き場（リポジトリの `build/count-db-copy/`、ホームの `work-*`）は残っていない。H2 の jar は lockfile の版（2.4.240）を Gradle のキャッシュから見つけた。

## 3. A3 の組と前の道具の対応

| 組（`--set`） | 前の道具 | 引き継いだもの | 前の道具との違い |
|---|---|---|---|
| `basic`（既定） | `~/.mastersmith-backup/count-db.sh` | 初期管理者の3項目（SUM の形）、利用者の数・管理者・有効な管理者・停止（SUM）、DSL の適用の版とプレビューの数、起点からの監査の種類と結果ごとの件数、スキーマの版 | 前は H2 Shell の表の形で出していた。今は「名前 値」の行。メールアドレスと起点の時刻を SQL の文字列に埋めず、バインド変数で渡す |
| `roles` | `~/.mastersmith-backup/count-db2.sh` | `basic` の初期管理者・スキーマの版・DSL の数、最新の DSL の `customers`・`customer_orders` の回数（`customers_hits`・`customer_orders_hits`）、ロールの数、初期管理者のロール（直接・グループ経由）と作業ロールの選択 | 語の回数を SQL の `REPLACE` ではなく Java で数える（重ならない出現の数で同じ値の見込み）。出力の形は `basic` と同じ |
| `reflookup` | `~/.mastersmith-tools/count-db3.sh`（`CountRefLookup.java`） | すべての項目（DSL の語の回数・他テーブル参照の識別子・権限の設定・初期管理者のロールと作業ロール・監査の合計と起点からの内訳・業務データの監査の列の空の数） | `.env` の場所を固定のパスでなく引数（既定 `.env`）で受ける。メールアドレスは子へ標準入力で渡す |
| `tz` | `~/.mastersmith-tools/CountTz.java` | スキーマの版の最大・V14 の行・`users.display_time_zone` の列の有無と行の数・DSL の `customers`・`created_at` の回数 | なし（同じ問い合わせ） |
| `audit` | `~/.mastersmith-tools/SinceCount.java` | 起点からの監査の種類と結果ごとの件数・合計・全体の数 | 前はコンテナの中の `/data` の複写を開いていた。今は手元の展開した複写を開く。「合計」「全体」の名前を `audit_since_t0`・`audit_events_total` にした |
| （組にしない） | `~/.mastersmith-tools/CountRows.java`・`Cols.java` | — | 表と列を指定して数える道具（`docs/internal-db.md` に手順がある）と、列の一覧の道具。決まった組ではないため台本にまとめず、手順書の道具のまま残す |

- H2 の jar は、前の道具の固定のパスに代えて、引数 `--h2-jar`・環境変数 `H2_JAR`・Gradle のキャッシュ（`backend/gradle.lockfile` の版）の順で探す。作業の置き場は、前のホームの下の `work-*` に代えて、リポジトリの `build/count-db-copy/` の下（権限 700、終わったら消す）。
- 同じ置き場の `lkeys.sh`・`lmq.sh`・`lq.sh`・`pq.sh`・`tq.sh`・`poolbase.sh` は内部DB の複写を数える道具ではないため、A3 の対象にしていない。ホームの下の古い道具の片付けは、依頼者の確認の後に行う（この段では消していない）。

## 4. 要件との対応

| 要件 | 満たした場所 | 状態 |
|---|---|---|
| FR1.1 | `frontend/scripts/check-license-header.mjs` | 済み |
| FR1.2 | `frontend/package.json`・`frontend/eslint.config.js`・`.pre-commit-config.yaml` | 済み |
| FR1.3 | `verify-graph-check.mjs` を `verify` に入れていない | 済み |
| FR1.4 | `docs/verification.md`（台本の扱い） | 済み |
| FR2.1 | `TimeBoundedDbHealthIndicatorTest` の2件 | テストは済み。パッケージの値の判定は Build and Test |
| FR2.2・FR2.3 | `backend/build.gradle.kts`・`scripts/ci-summary.mjs`・`docs/verification.md` | 済み（README に一覧の言及は無かった） |
| FR2.4 | `team.md`・`project.md` | 済み |
| FR3.1 | `runs-on: ubuntu-latest` のまま | 済み（変更なし） |
| FR3.2 | — | Build and Test |
| FR4.1 | `.github/workflows/ci.yml` | 済み |
| FR4.2 | `.github/workflows/ci.yml`（入力 `it_forks`） | N の比べは Build and Test |
| FR4.3 | `team.md` 21 行・`docs/verification.md` | 済み |
| FR5.1 | `scripts/ops-readonly-check.mjs` | 作った。動いているコンテナでの確かめは Build and Test・配備の段 |
| FR5.2 | `scripts/ci-attempts.mjs` | 済み（照合も済み） |
| FR5.3 | `scripts/count-db-copy.mjs` | 作った。前の道具との件数の照合は流す許しの後 |
| FR5.4 | `docs/verification.md`・`docs/internal-db.md`・`docs/operations.md` | 済み |
| FR5.5 | 2節 | A2 は済み。A1 の動いているコンテナ・A3 の照合は後の段 |
| FR6.1〜FR6.3 | — | Build and Test と配備の段 |
| NFR1・NFR2・NFR4 | — | Build and Test |
| NFR3 | 3本の台本の決まりと、A1・A2 の出力の検索 | A1・A2 は済み。A3 は流したときに確かめる |

## 5. 計画との差

- **lint の呼び方（1回目、Step 3）**: 計画の例（`lint` に `../scripts` を足す）ではなく、`lint:scripts` でルートへ移って `frontend/node_modules/.bin/oxlint -c frontend/.oxlintrc.json scripts` と `eslint --config frontend/eslint.config.js scripts` を呼ぶ形にした。oxlint が `..` を対象に受け付けず、ESLint が設定の置き場の外のファイルを見ないため。
- **Prettier の `--config`（1回目、Step 3）**: `../scripts` には `frontend/` の設定が当たらないため、`--config .prettierrc.json` で明示した。
- **oxlint の1行（1回目、Step 3）**: `scripts/program-size.mjs` の `toFixed(100)` の1行だけ `oxlint-disable-next-line oxc/number-arg-out-of-range` を置いた（依頼者が「そのまま」と決めた）。
- **pre-commit の追加（2回目、Step 12〜14 と同じ Bolt 4 の中で行った）**: 依頼者がこの回に足した決定で、`.pre-commit-config.yaml` にフック `prettier-check-scripts`（`scripts/` の `.js`・`.mjs` に `frontend/` の Prettier と設定を当てる）を足した。計画には無かった。`team.md` の「書式の検査をコミットの直前に流す」に合わせるため。
- **A2 の終了コード 3**: 計画は 0・1・2 だけを決めていた。道具の誤り（gh が無い・範囲のコミットが見つからないなど）を「通っていない」と分けるため 3 を足した。A1・A3 も引数の誤りなどを 3 にそろえた。
- **A2 の終了コード 0 の読み**: 計画の「すべて通っていれば 0」を「先頭のコミットの最も新しい実行の最後の試行が success なら 0」と読んだ。前のコミットの失敗（後のコミットで直したもの）は、表とまとめに出し、終了コードは変えない（下の「依頼者に確かめたいこと」の 1）。
- **A3 の Java の置き場**: 計画は `scripts/count-db-copy.mjs` の1本。H2 を Node から開けない（新しい依存を足さない）ため、数える Java のコードを台本の中に持ち、実行ごとに作業の置き場へ書いて `java` のソースの直接の実行で動かす形にした（`.java` のファイルをリポジトリに置かない）。
- **A3 の組の数**: 計画の例（`basic|roles|...`）に `reflookup`・`tz`・`audit` を足した（前の Java の補助 `CountTz.java`・`SinceCount.java` も同じ組で出せるように）。
- **A1 の版が変数のサービス**: `app` の `image:` は `${MASTERSMITH_IMAGE_TAG:-local}` で決まり、`.env` の値を読まないと版を決められないため、名前だけを比べる形にした。
- **手順の決まりからのずれ（2回目、記録のため）**: A1 の最初の確かめで、出力を `build/scratch/` に書き（`mkdir` をシェルで流した）、その後すぐに消して、以後は出力を直接読んだ。リポジトリに残ったものは無い。

## 5a. 承認の場の Request Changes の直し（2026-11-09、指揮役、計画の Step 18〜23）

依頼者が承認の場で Request Changes を選び、レビューの R-02〜R-05・R-07 を直した。R-01 は計画の要約に「全件の `verify` の実測が通るまで統合しない」を足して扱い（6 パッケージは要件の段で実測済み）、R-06（知識ベースの古い記述）は次の Intent の Reverse Engineering に回した（依頼者が受け入れた）。

| 指摘 | 直したもの | 確かめ |
|---|---|---|
| R-02 | `scripts/ops-readonly-check.mjs`: 1 の比べを確かめていない（Docker に繋がらない・`compose.yaml` が無い）ときは終了コード 2、出力の最初に「1 の比べは確かめていない」、1 と 5 は「確かめていない」。`docs/operations.md` も合わせた | colima を止めたまま流して終了コード 2 と最初の行を確かめた |
| R-03 | `scripts/ci-attempts.mjs`: 先頭のコミットの判定を push の実行だけで行う（手動の実行は表に出すだけ）。push の実行が無ければ「push の実行なし」。範囲の両端の SHA を出す。`docs/verification.md` に `git fetch` と判定の読みを書いた | `git fetch origin` の後に `--from b2990415 --no-logs` で流し、範囲の両端 `b2990415..4fe8e790` が出て、先頭が通っている（終了コード 0）ことを確かめた |
| R-04 | `scripts/count-db-copy.mjs`: 起動の最初に `build/count-db-copy/` の前の `work-*` を消す（消した数を標準エラーに出す）。`docs/internal-db.md` に中断したときの確かめを書いた | 試しに残りを1つ置いて、存在しない複写で流し、「残り 1 件を消しました」と出て終了コード 3（`.env` を読む前に止まる）、残りが消えたことを確かめた |
| R-05 | `.github/workflows/ci.yml`: 入力の値を 1〜8 の整数に限り（`case` で確かめ、外れたら `::error::` で止める。値は環境変数のまま）、説明に範囲を書いた。`docs/verification.md` に、比べるまでの既定 3 と、失敗が続いたら `IT_FORKS_DEFAULT` を `"1"` に戻す手順を書いた | Ruby の YAML で手順を読み、`case` の判定を 3・8・0・9・abc・空で試した（3・8 だけ通る） |
| R-07 | `aidlc/spaces/default/memory/team.md`: Testing Posture の重なる2項目を1つにまとめた（Testing Contract は作り直した） | 計画の Testing Contract を新しい決まりで作り直し、計画の承認を取り直した |

検査: `npm --prefix frontend run license:check`・`format:check`・`lint` がすべて終了コード 0。

## 6. 依頼者に確かめたいこと

（下の 1〜3 は依頼者が決めた: 1 はこのまま、2 は書き換え、3 は許しを得て流し、件数が一致した。）

1. **A2 の終了コード 0 の読み**: 先頭のコミットの CI が通っていれば 0 とし、前のコミットの通っていない実行はまとめに件数で出すだけにした。前のコミットに1つでも通っていない実行があれば 1 にする形がよければ直す。推奨はこのまま（後のコミットで直した失敗で取り込みを止めないため。表とまとめで見落とさない）。
2. **`team.md` の Testing Posture の 53 行**（「ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる…」）は計画の Step 16 に無いため変えていない。一覧を消した後は、この「ただし」の読みが 54 行の新しい文（外すパッケージは無い）と並ぶ。過去の経緯として残すか、54 行に合わせて書き換えるかを決めてほしい。推奨は書き換え（読みの食い違いを残さないため）だが、計画の外のため依頼者の決定を待つ。
3. **A3 の照合を流す許し**: `.env` を読む台本のため、流す前に許しを得る（指揮役から尋ねる）。照合には今の道具と同じ複写（例: 最新の `mastersmith-data-202611070636-after-tz-options-perf.tgz`）と起点の時刻を使う。

## Sources

- 計画: `aidlc/spaces/default/intents/261108-ci-checks-release/construction/code-generation/code-generation-plan.md`
- 要件: `aidlc/spaces/default/intents/261108-ci-checks-release/inception/requirements-analysis/requirements.md`
- 前の道具（読み取りだけ）: ホームの下の `count-db.sh`・`count-db2.sh`・`count-db3.sh`・`CountRefLookup.java`・`CountTz.java`・`SinceCount.java`・`CountRows.java`・`Cols.java`
- 1回目の結果: 指揮役からの依頼の要約
