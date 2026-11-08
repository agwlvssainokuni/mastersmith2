# コード生成の計画（Intent 261108-ci-checks-release）

## Summary

- Builds: `scripts/` の検査、`packagesJudgedByTotal` の撤去と `common.health` のテスト、CI の結合テストの並べ、確かめの台本 A1〜A3、決まりと手順書の書き換え
- Touches: `frontend/scripts/check-license-header.mjs`・`frontend/package.json`（とリンタ・書式の設定）、`scripts/*.mjs`、`backend/build.gradle.kts`、`backend/src/test/.../common/health/`、`.github/workflows/ci.yml`、`docs/verification.md`・README、`team.md`・`project.md`
- Tests: `common.health` の単体テスト 2 件（JUnit）。台本は手元で流して合格の条件（FR5.5）を確かめる。既存の全テストは Build and Test の全件の `verify` で緑のまま

## 前提と入力

- 要件: `inception/requirements-analysis/requirements.md`（FR1〜FR6、NFR1〜NFR4）。承認の場の決定: レビューの R-01〜R-06 を直した版を承認（2026-11-09）。
- 知識ベース: `aidlc/spaces/default/codekb/mastersmith2/`（K-127〜K-137）。
- 単位の分割は無い（bugfix、zero-Unit）。Bolt 1〜5 はこの段で作り、Bolt 6（リリース）は Build and Test と配備の段で行う。
- 作業ブランチ: `develop` から `feature/261108-ci-checks-release` を1本作り、Bolt ごとにコミットを区切る。統合は Bolt ごとの squash（`team.md` の Way of Working の「単位ごとの squash」をこの計画の承認で選ぶ。`develop` に Bolt 1〜5 の5コミット）。
- `src/main` は変えない（要件の制約）。`packagesJudgedByTotal` のパッケージに手を入れる Bolt には当たらない（テストと `build.gradle.kts` だけの変更）。
- 統合の前の検査は、Build and Test の全件の `./gradlew verify`（Bolt 2 でカバレッジの判定の形を変えるため、`verifyQuick` では代えない）。

## 影響の範囲（検索で洗い出した既存のもの）

- `packagesJudgedByTotal` を使う所: `backend/build.gradle.kts` 313〜321 行（一覧）と 344 行（`excludes`）、その上の説明文（296〜312 行）。`scripts/ci-summary.mjs` 34・161・178〜179・224・237・260 行（読み取りと印）。`docs/verification.md` 22・42・73〜81・160・163 行。`team.md` 19 行（Way of Working）・54 行（Testing Posture）・155 行（Code Style の「手を入れる」の読み）。`project.md` 131 行（Mandated）。`aidlc/spaces/default/memory-archive/` は過去の写しなので変えない。
- `common.health` の届いていない行: `TimeBoundedDbHealthIndicator` の 94〜100 行（確認を実行の枠に渡せなかったとき）と 118〜121 行（待ちが割り込まれたとき）。既存のテスト `TimeBoundedDbHealthIndicatorTest`（実行の枠を渡すコンストラクター 79 行を使える）・`HealthEndpointIT`。
- ライセンスヘッダーの検査: `frontend/scripts/check-license-header.mjs` は `frontend/` と `perf/` だけを見る（K-130）。書式とリンタは `frontend/package.json` の `format:check`（`prettier --check .`）と `lint`（`oxlint . && eslint .`）で、どちらも `frontend/` の中だけ。
- CI: `.github/workflows/ci.yml` の `on:` に `workflow_dispatch` がある。`verify` の job は `./gradlew verify` を `-PitForks` なしで流す。`runs-on: ubuntu-latest`（2か所、変えない）。
- `-PitForks` の決まり: `team.md` Way of Working の「`-PitForks=<N>` を付けると…（手元の `verify`・`-PitTests` を付けた `verifyQuick` に効く。CI は変えない）」、`docs/verification.md` 90〜103 行。
- A3 の今の道具: ホームの下の `count-db.sh`・`count-db2.sh`・`count-db3.sh` と Java の補助（`~/.mastersmith-tools/`）。内部DB の複写（tgz）を作業の置き場に展開し、H2 の Shell を読み取りで開いて件数だけを出す。初期管理者のメールアドレスを `.env` から読む（値は出さない）。H2 の jar は Gradle のキャッシュの固定のパス。

## Steps

### Bolt 1: `scripts/` の検査（FR1）

- [ ] **Step 1: 作業ブランチと検査の道具の確かめ**
  - `develop` から `feature/261108-ci-checks-release` を作る。
  - `npm --prefix frontend run license:check`・`format:check`・`lint` が今のまま通ることを確かめる（比べの元）。
- [ ] **Step 2: ライセンスヘッダーの検査に `scripts/` を足す**（FR1.1）
  - `frontend/scripts/check-license-header.mjs` に、ルートの `scripts/` の `.mjs`（`perf/` と同じ `/* ... */` の形）を足す。対象の一覧の出し方は今の形に合わせる。
- [ ] **Step 3: 書式とリンタに `scripts/` を足す**（FR1.2）
  - `frontend/` の Prettier・oxlint・ESLint とその設定を使い回し、ルートの `scripts/*.mjs` も対象にする（例: `frontend/package.json` の `format:check` と `lint` に `../scripts` を足す、または設定の対象のパスに足す。どちらが今の設定と無理なく合うかを試して決め、`code-summary.md` に書く）。新しい npm の依存は足さず、ルートに `package.json` を作らない。
  - 今ある `scripts/*.mjs` で出る指摘を直す（書式は Prettier で整え、動きを変えない）。
- [ ] **Step 4: Bolt 1 の確かめ**
  - 3つの検査がすべて通る。わざと `scripts/` の1本からヘッダーを外すと検査が落ちることを確かめて元に戻す。
  - `node scripts/program-size.mjs`・`GITHUB_STEP_SUMMARY= node scripts/ci-summary.mjs`・`node scripts/verify-graph-check.mjs` が前と同じに動く（`verify-graph-check.mjs` は `verify` に入れない。FR1.3）。

### Bolt 2: `packagesJudgedByTotal` の撤去と R-07（FR2）

- [ ] **Step 5: `common.health` のテストを足す**（FR2.1）
  - `TimeBoundedDbHealthIndicatorTest` に2件足す（説明文は英語）。
    - 実行の枠が確認を受け付けない（止めた実行の枠を渡す）とき、DOWN を返し、次の確認を始められる（`inFlight` が戻る）こと。
    - 待っている間にスレッドが割り込まれたとき、DOWN を返し、割り込みの印が残ること。
  - `src/main` は変えない。
  - `./gradlew :backend:test --tests 'cherry.mastersmith.common.health.*'` で通ることを確かめる。
- [ ] **Step 6: 一覧と除外の仕組みを消す**（FR2.2）
  - `backend/build.gradle.kts` の `packagesJudgedByTotal` と `excludes = packagesJudgedByTotal`、その説明文を消し、パッケージごとの下限をすべてのパッケージに当てる形にする。経緯（どの Intent で何を外したか）は短い説明文に残す。
- [ ] **Step 7: 一覧を読むものを直す**（FR2.3）
  - `scripts/ci-summary.mjs`: 一覧の読み取りと「全体で判定」の印を外す（一覧が無くても下限を読めること。読めないときの扱いは今のまま「—」と理由）。
  - `docs/verification.md`: 22・42・73〜81・160・163 行を一覧の無い形に直す。R-07 の確かめのコマンドと「全件の `verify` の場面に当たるかを確かめる」節は、場面そのものが無くなるため消す。
  - README に一覧への言及があれば直す。
- [ ] **Step 8: Bolt 2 の確かめ**
  - `./gradlew :backend:cleanTest :backend:test :backend:jacocoTestReport :backend:jacocoTestCoverageVerification` は結合テストの記録が無いと全体の値が下がるため、カバレッジの判定は Build and Test の全件の `verify` で確かめる（`common.health` の値と、全パッケージが下限を満たすこと。`audit.repository` が数えられないことも）。この段では Step 5 のテストと `--dry-run` でタスクの組み立てが壊れていないことを確かめる。
  - `GITHUB_STEP_SUMMARY= node scripts/ci-summary.mjs` が手元の報告で動き、印の列に「全体で判定」が出ないこと。

### Bolt 3: CI の結合テストの並べ（FR4）

- [ ] **Step 9: CI に `-PitForks` を付ける**（FR4.1・FR4.2）
  - `.github/workflows/ci.yml` の `workflow_dispatch` に入力 `it_forks`（結合テストを並べる数。既定は空）を足す。`verify` の手順は `./gradlew verify -PitForks=<N>` とし、N は入力があればその値、無ければ既定の値にする（既定は手元の推奨の 3。Build and Test で CI の上の 2 と 3 を比べ、2 の方がよければ既定を 2 に直す小さなコミットを Build and Test で積む）。
  - 入力の値は Gradle の側（`backend/build.gradle.kts` の `itForks` の読み取り）がすでに 1 以上の整数かを確かめて落とすので、CI の側では値をそのまま渡す。
  - `push` と `release` の job・きっかけは変えない。
- [ ] **Step 10: Bolt 3 の確かめ**
  - YAML として読めること。`node scripts/verify-graph-check.mjs` を流し、`-PitForks` の扱いが壊れていないこと。CI の上の確かめは Build and Test（依頼者のプッシュの後、`gh workflow run ci.yml --ref feature/261108-ci-checks-release -f it_forks=2` と `=3`）。

### Bolt 4: 確かめの台本 A1〜A3（FR5）

- [ ] **Step 11: A2 `scripts/ci-attempts.mjs`**（FR5.2）
  - 引数で範囲（既定は `origin/main..origin/develop`。`--from <commit>`・`--to <commit>` で変えられる）を受け、範囲のコミットごとに `gh run list --commit <sha> --workflow ci.yml --json ...` と `gh run view <id> --json attempt,jobs,conclusion` を読み、CI の実行の有無・試行ごとの結論・再実行で通ったものの1回目の失敗の job と手順を表で出す。終了コードは、範囲の先頭のコミットの CI が通っていなければ 1、走っている最中なら 2、すべて通っていれば 0。
- [ ] **Step 12: A1 `scripts/ops-readonly-check.mjs`**（FR5.1）
  - 読み取りだけで次を出す: 動いているコンテナごとの版とダイジェスト（`docker inspect` の `RepoDigests`）と `compose.yaml` の `image:` の比べ（食い違いを「食い違い」と示す）、`.env` と `.env.example` の環境変数の名前の数と、片方にだけある名前（値は読まず名前だけ）、`backend/build/libs/mastersmith.war` の SHA-256、`.env` の更新の時刻、`docker system df` の要約。
  - `DOCKER_HOST` は環境から受ける。`.env` の値は出さない（出力に `=` の右側が入らないことを出力の検索で確かめる）。
- [ ] **Step 13: A3 `scripts/count-db-copy.mjs`**（FR5.3）
  - 今の3つの道具を読んで、数える問い合わせ（利用者・管理者・停止・DSL の版・プレビュー・役割・割り当て・監査の種類ごとの件数・スキーマの版など）を1つにまとめる。どの組を流すかは引数（`--set basic|roles|...`）で選べるようにし、今の道具と同じ組を出せること。
  - 複写（tgz）と起点の時刻は引数、作業の置き場は `build/` の下（終わったら消す）、H2 の jar は引数か環境変数（無ければ Gradle のキャッシュから探す）、初期管理者のメールアドレスは `.env` から読むが出力に出さない。H2 は読み取り（`ACCESS_MODE_DATA=r`）で開く。行の値は出さない。
  - ホームのパス・秘密は台本に書かない。
- [ ] **Step 14: Bolt 4 の確かめ**（FR5.5）
  - A2: `b2990415` の後のコミットで流し、手で `gh run view` で確かめた数件と一致すること。
  - A1: 依頼者が colima を動かしたときに流す（今は止めている。止めたままなら「コンテナが無い」と出ることを確かめ、残りは Build and Test・配備の段で流す）。
  - A3: 依頼者の手元の最新の複写で、今の道具と件数が一致すること（`.env` を読む台本を流すので、流す前に依頼者に伝えて許しを得る）。
  - 使い方を `docs/verification.md`（A2）と README か `docs/`（A1・A3）に書く（FR5.4）。ホームの下の古い道具の片付けは、依頼者の確認の後に行う（この段では消さない）。

### Bolt 5: 決まりと手順書の書き換え（FR1.4・FR2.4・FR4.3・FR3）

- [ ] **Step 15: `docs/verification.md` の残り**
  - 「`scripts/` は検査の外」とした記録を今回の決定に書き換える（FR1.4）。
  - `-PitForks` の節に「CI でも使う（既定 N と入力 `it_forks`）」を足す（FR4.3）。
- [ ] **Step 16: `team.md`・`project.md` の書き換え**（FR2.4・FR4.3）
  - `team.md`: 19 行（全件の `verify` を流す場面から `packagesJudgedByTotal` の場面を外す）、54 行（Testing Posture の一覧の行を、一覧を消した経緯と「パッケージごとの下限はすべてのパッケージに当てる」に書き換える）、155 行（「手を入れる」の読みの参照先を書き換える）、Way of Working の `-PitForks` の行（CI でも使う）。それぞれに「（2026-11-09 Intent 261108-ci-checks-release で書き換え）」の印を付ける。
  - `project.md` 131 行（Mandated の全件の `verify` の場面から `packagesJudgedByTotal` を外す）。同じ印を付ける。
  - `aidlc/spaces/default/memory-archive/` は変えない。
- [ ] **Step 17: 記録**
  - `code-summary.md`（各 Step の確かめの結果、依頼者に確かめたいこと）と `traceability.json` を書く。
  - コードのコミットは Bolt ごとに区切り（Bolt 1〜5）、積む前に依頼者に尋ねる（`project.md` の Change Control）。

### Bolt 6: リリース（FR6、この段では作らない）

- Build and Test: 全件の `verify`（`caffeinate -dims`、`-PitForks=3`）、E2E 全体、CI の手動の実行で N=2・N=3 を比べて既定の N を決める（FR4.2）、Ubuntu 26 の道具の版の記録（FR3.2）、A2 で前の取り込みの後のコミットの CI を確かめる（FR6.1）。
- 統合（Bolt ごとの squash）→ 依頼者のプッシュ → `develop` の CI → 取り込みの直前に A2 を流し直す → 依頼者の承認で `main` へ fast-forward（FR6.2）→ 配備の段（FR6.3）。

## 要件と手順の対応

| 要件 | 手順 |
|---|---|
| FR1.1〜FR1.3 | Step 1〜4 |
| FR1.4 | Step 15 |
| FR2.1 | Step 5 |
| FR2.2・FR2.3 | Step 6〜8 |
| FR2.4 | Step 16 |
| FR3.1・FR3.2 | 変更なし（`ubuntu-latest` のまま）。確かめは Build and Test |
| FR4.1・FR4.2 | Step 9・10（比べは Build and Test） |
| FR4.3 | Step 15・16 |
| FR5.1〜FR5.5 | Step 11〜14 |
| FR6.1〜FR6.3 | Bolt 6（Build and Test と配備の段） |
| NFR1 | Build and Test（CI の時間を前の 35分47秒 と並べる） |
| NFR2 | Step 6〜8 と Build and Test の全件の `verify`・CI |
| NFR3 | Step 11〜14（出力の検索） |
| NFR4 | Build and Test |

## 依頼者の決定との差

- `team.md` の Testing Posture は「テストのない機能は完成とみなさない」。台本 A1〜A3 と `scripts/` の直しには単体テストを置かない（要件の 5節の前提。前の Intent の依頼者の読み方を引き継ぐ）。代わりに FR5.5 の合格の条件を手元で確かめる。`common.health` のテストは本体のテストとして置く。

## Testing Contract

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "team",
  "ordering": "テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。",
  "scope": "bugfix",
  "test_strategy": "minimal",
  "project_type": "brownfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- 統合の前の `./gradlew verifyQuick` は、バックエンドのカバレッジの下限を判定しない（単体テストの記録だけから参考値を `backend/build/reports/jacoco/quick/` に出して表示し、下回っても失敗にしない）。バックエンドの下限は、全件の `./gradlew verify` を流す場面（Way of Working）と CI で判定する。画面のカバレッジの下限は `verifyQuick` でも判定する。（2026-11-08 Intent 261107-verify-quick-local で追加）\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる。ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる（既存のパッケージは全体の合計で判定する）。どちらになるかは、パッケージごとの下限を入れる Bolt で既存のパッケージのカバレッジを実測して決め、結果を記録する。\n- 全体の合計で判定している既存のパッケージ（`backend/build.gradle.kts` の `packagesJudgedByTotal` の一覧。2026-10-16 の時点でも 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）。`user.domain`・`user.repository`・`user.service` は Intent 260925-user-management で一覧から外れ、含まない）に手を入れる Bolt では、テストを足してそのパッケージの下限（行 80%・分岐 70%）を満たし、一覧から外してパッケージごとの下限の対象に必ず戻す。「手を入れる」は、そのパッケージの本体のソース（`src/main`）の変更のすべて（説明文だけの直しを含む）を指し、テストだけの変更は含めない。手を入れる見込みのパッケージとそれに伴う作業は、Delivery Planning とコード生成の計画で、各パッケージの今の値を実測して見積もる。カバレッジは、その Bolt のテストを足した後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた全件の `./gradlew verify` で実測し（`verifyQuick` の参考値では代えない）、値を記録する。一度外したパッケージは一覧に戻さず、一覧を増やす変更はしない。（2026-11-08 Intent 261107-verify-quick-local で書き換え）\n- フロントエンドのカバレッジの下限は、今の形（全体の合計に当てる `thresholds`）のままとする。新しい機能のフォルダ（`src/features/<featureId>/`）のカバレッジの値は、Build and Test で記録する。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。対象DB の業務データを読むだけのテストでも表を作るため、この決まりを当てる。作る名前には実行ごとに一意の値を入れ、途中で止まった前の実行の残りに頼らない。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを全件の `./gradlew verify` の中と CI で毎回実行する。統合の前の `./gradlew verifyQuick` では、`-PitTests` で絞ったときに当たるものだけを流す。コンテナの実行環境が無いときの扱いは Way of Working のとおり。（2026-11-08 Intent 261107-verify-quick-local で書き換え）\n- テストを足した Bolt の後は、統合の前に流した1コマンドの検査（`./gradlew verifyQuick`。全件の場面に当たる Bolt では `./gradlew verify`）の時間を、Build and Test では全件の `./gradlew verify` の時間と CI の時間を測って記録する。CI の上限（`timeout-minutes: 60`）の7割の約 42 分を超えたら、テストの分け方を依頼者に諮る。（2026-11-08 Intent 261107-verify-quick-local で書き換え）\n- 画面からの一連の操作を確かめるテスト（E2E）は Playwright（`./gradlew e2eTest`）で書き、`./gradlew verify` と CI の外に置く。本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す。新しく足す流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作る。複数の機能の Intent を1つに束ねた Intent では、束ねた元の Intent ごとに数える。既存の E2E を新しい形（権限の形など）に合わせて書き換えることは、本数に数えない。利用者の状態を変える操作（利用停止・管理の権限の変更・ロックなど）は、その流れで自分で作った利用者だけを対象にし、初期管理者の状態は変えない。役割・権限を変える操作も、その流れで自分で作った利用者と役割だけを対象にし、初期管理者は変えない（E2E は1つのアプリ・内部DB・初期管理者を全ファイルで共有して順に流すため）。E2E を流す時点は Way of Working のとおり（Intent の最後とリリースの前）とする。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- リポジトリは公開のため、テストデータのメールアドレスは予約のドメイン（`example.com` など）だけにし、実在しそうな氏名・宛先を置かない。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- 画面のテストで、描画の後（`useEffect` などの効果）に反映される値は、操作の直後に同期で確かめず、`waitFor` で待って確かめる。テストの時間の上限（Vitest の既定 5 秒など）は、原因を確かめずに延ばさない。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。\n- 不安定なテストと CI の失敗は、次の1つの決まりで扱う。\n  - 手元で再現した不安定なテストは、原因を直すまで統合しない（次へ進まない）。\n  - 手元で再現しないものは、再現の試みに先に時間の上限を決め、見立てと試みの範囲を記録したうえで、CI の再実行で通れば進めてよい（不安定と確かめられていない扱い）。\n  - 同じテストが二度目に落ちたら、原因を直すまで次へ進まない。\n  - 失敗を直さずに次の Intent へ持ち越すのは、依頼者の決定があるときだけとし、決まりとの差を記録する。\n  - 不安定なテストを直したときは、二段で確かめる。疑った競り合い・待ちの抜けを待ち合わせや順の入れ替えで確実に起こせたときは、直す前のテストが落ち、直した後のテストが通ることを記録する。起こせないときは、上の「手元で再現しないもの」と同じく、試みの時間の上限を決めて試み、見立てと試みの範囲を記録する。\n- 権限ごとの API の認可のテストの共通の形（下の役割・権限、業務データ、他テーブル参照の一覧から「共通の形」として参照する）: 未認証（401）・要る権限だけを欠く利用者（403。権限が何も無い利用者ではなく、ほかの権限は持つ利用者）・権限を持つ（200）を、サーバー側のテストで確かめる。API と権限の組は、パラメーターを使うテストで表として書く。\n- axe-core を上げて新しい規則の違反が出たときは、規則を外して検査を通さず、違反をすべて直してから上げる（固い制約は `project.md` の Forbidden）。違反の要素が make-you-chic-ui の部品のものなら依頼を出し、固定先の更新で取り込んでから上げる。上げたときは、実際のブラウザの axe（E2E）と画面のテストの vitest-axe の両方を流し、E2E の全件の通過と違反 0 件を件数で記録する。あわせて、axe-core の版が lockfile で1つにそろっているかを確かめる。\n- 性能・資源の目標の測り方（物差し）を変えるのは、要件定義で依頼者が明示に決めたときだけとし、変える理由を記録する。変えた後も、少なくとも1回は前の物差しの値を並べて記録する。\n- 負荷の試験の使い捨ての環境の対象DB の行は、perf の台本（`perf/` の下）で決めた種から作る（行の数は NFR 要件の段で決める）。k6 の場面の setup は、前の場面が残した状態に頼らない。\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22) \n\n- 画面を廃止する・移すことで既存の E2E を書き換える・消すときは、本数に数えない。ただし、その画面で確かめていた検査（例: 管理の仮の画面の E2E 030・090・130 の 403 の表示・アクセシビリティ・管理の権限の確認の API）は、別の画面か別のテストへ移して残し、移す前と後の件数を Build and Test で記録する。"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けた全件の `./gradlew verify` で実行し直し、実測の数字だけを報告する。`./gradlew verifyQuick` の JaCoCo の値は単体テストだけの参考値で、カバレッジの報告には使わない。 (learned 2026-09-23)  （2026-11-08 Intent 261107-verify-quick-local で書き換え）\n- 実際のブラウザのアクセシビリティの検査（axe）は、誤りを出した状態と、現実に近いデータ（2語の氏名など）で、ブランドカラーとテーマのすべての組について行う。user-management の U7 の 080 で初めて、make-you-chic-ui の Avatar（頭文字が2文字のとき）と dark の FormField の誤りの文字のコントラスト不足が出た（050〜070 は誤りの状態を見ず、氏名が1語だったため出なかった）。すべての組で行うのは、その Intent で変えた画面だけとし、変えていない画面は E2E の既存の検査に任せる（2026-11-07 依頼者の決定で緩めた）。 (learned 2026-09-28) \n- Delivery Planning でのカバレッジの実測は、./gradlew verify 全体ではなく :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport で行い、jacocoTestReport.xml から手を入れる見込みのパッケージの値を読む（user-admin で約 5 分）。 (learned 2026-10-01) \n- 画面のはみ出しの確かめ（E2E・axe）では、画面全体の横のスクロールだけでなく、開いたメニュー・ポップアップなど画面に固定で置く部品が画面の中に収まることも確かめる。表の右端に置く make-you-chic-ui の Dropdown は placement に bottom-end を指定する（user-admin の配備の後のスモークテストで、行の「操作」のメニューが右へはみ出していたのを E2E 120 が拾えなかった）。 (learned 2026-10-03) \n- 木の画面の単体テスト・E2E・スモークテストでは、本物と同じ形の応答（子の有無の印を含む）を使い、すべての段の開閉を押して確かめる（role-menu の配備の後のスモークテストで、「自分の権限を見る」の木でテーブルの開閉を押すと自分自身を子に持って固まる不具合が見つかった。単体の見本のテーブルは子が無い形で本物と違い、E2E は選ぶだけで開閉を押していなかった）。 (learned 2026-10-14) \n- 画面の不具合を使い捨ての環境で再現するときは、依頼者がした操作（どのボタン・どの開閉を押したか、画面の文言とその場所）を先に聞き、同じ操作を試す。role-menu の S8 で、選ぶだけで開閉を押さずに再現せず「環境の差」と誤って見立て、依頼者の再試行で条件が分かった。 (learned 2026-10-14) \n- 長いテスト（verify・E2E・k6）は、試験の台本全体を `caffeinate -dims` で包む（場面ごとに起こし直すと切れ目で守りが外れる）。流す前に `pmset -g assertions` で眠らない状態か、流した後に `pmset -g log` で Sleep・DarkWake が無かったかを確かめ、入っていたら結果を使わずに流し直す（DarkWake の Thermal Emergency のスリープは `caffeinate -i` では止められない）。 (merged 2026-11-04)\n- colima の PC で `./gradlew verify`、または `-PitTests` を付けた `./gradlew verifyQuick` を流すときは、README の `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`（書き方は `docs/target-db.md`）をシェルに渡す。渡さないと対象DB のテストが SKIPPED になり、`verify` ではパッケージごとのカバレッジの下限で失敗する。`-PitTests` を付けた `verifyQuick` では、バックエンドのカバレッジの下限を判定しないため、飛ばさずに失敗する（CI と同じ扱い）。 (merged 2026-11-04)（2026-11-08 Intent 261107-verify-quick-local で書き換え）\n- Playwright の `webServer.env` に置いた値は json の報告に残る。E2E の仮の資格情報はプロセスの環境変数で渡し、報告の json の文字列の検索で、パスワード・トークン・メールアドレスが入っていないことを確かめる。 (merged 2026-11-04)"
    }
  ],
  "obligations": {
    "strategy": "minimal",
    "strategy_volume": [
      "One verifiable test per requirement at the narrowest effective level.",
      "At least one happy-path unit test per component.",
      "Unit tests are the default; a bugfix/security scope floor may require an integration or E2E regression when that is the narrowest level that reproduces the defect."
    ],
    "scope_floor": [
      "Include a targeted regression for the bug or vulnerability.",
      "Keep the existing test suite green."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "test-after",
    "runner_step": "Verify the existing test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Verify the existing test runner/configuration and record the exact unit-scoped command.",
      "Data model / database behavior - implement.",
      "Data model / database behavior - write and run its tests after implementation.",
      "Repository / data access - implement.",
      "Repository / data access - write and run its tests after implementation.",
      "Business logic - implement.",
      "Business logic - write and run its tests after implementation.",
      "API / endpoint - implement.",
      "API / endpoint - write and run its tests after implementation.",
      "Frontend behavior - implement.",
      "Frontend behavior - write and run its tests after implementation.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:ccf9af148e63c5ed13207cc19d0b94c67fd336b0d2c26a5483bb49499d52ad1f",
  "contract_sha256": "sha256:5374636d94c564db356c8a1597ec7df311047fa9102df32d44d9b9ef79227c85"
}
```
