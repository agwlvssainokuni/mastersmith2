# 検査とテスト

統合の前の1コマンドの検査（全件の `./gradlew verify` と、統合の前の速い入口 `./gradlew verifyQuick`）、リリースの前の確かめ、ビルドした WAR での画面の確認（E2E）の手順と決まりをまとめた文書です。

## 1コマンドの検査（統合の前の関門）

```bash
./gradlew verify
```

次の順に実行し、1つでも失敗したら後ろの段は実行しません。CI（GitHub Actions）も同じタスクを呼びます。

| 順 | 段（タスク） | 内容 |
|---|---|---|
| 0 | 準備（`verifyPrepare`） | 道具の確認、make-you-chic-ui の `npm ci`・ビルドと、2つのサブモジュール（make-you-chic-ui・java-mustache-processor）の追跡されるファイルが変わっていないことの確認、画面の `npm ci` |
| 1 | フォーマット（`verifyFormat`） | Spotless（palantir-java-format、ライセンスヘッダーを含む。バックエンドのメールのテンプレートの Mustache のコメント `{{! ... }}` の形のライセンスヘッダーも確かめる）、Prettier |
| 2 | リンタ（`verifyLint`） | oxlint、ESLint、Stylelint |
| 3 | ライセンスヘッダー（`verifyLicense`） | 画面のファイル（`.svg` を含む）のヘッダー（Java・Gradle の Kotlin DSL・メールのテンプレートは 1 の段の Spotless が確かめる）と、画面の SVG の中身の許可リスト検査（`npm run svg:check`。詳しくは `docs/app-icon.md`） |
| 4 | ビルド（`verifyBuild`） | Java のコンパイル、`tsc --noEmit`、Vite のビルドと、`dist` の素材（アイコン・favicon）が `frontend/public/` と同じで `index.html` の favicon の指定が正しいことの確認（`npm run dist:check`） |
| 5 | 単体テスト（`verifyUnitTest`） | JUnit（`*Test`）、Vitest |
| 6 | 結合テスト（`verifyIntegrationTest`） | Spring と組み込みの H2 を起動するテスト、対象DB（MySQL・MariaDB・PostgreSQL）のコンテナを使うテスト、JVM の中で起動するテスト用の SMTP の受け手（SubEtha SMTP）でメールを実際に受けるテスト（`*IT`） |
| 7 | カバレッジの下限（`verifyCoverage`） | JaCoCo・`@vitest/coverage-v8`。行 80%・分岐 70% を下回ったら失敗。バックエンドは全体の合計に加えて、新しく作るパッケージ（`cherry.mastersmith.targetdb` 以後）ごとにも同じ下限を当てる（既存のパッケージは全体の合計で判定する。`backend/build.gradle.kts` の `packagesJudgedByTotal`） |
| 8 | 安全の検査（`verifySecurity`） | SpotBugs＋FindSecBugs（重大度 High と、SQL インジェクション系（パターン名が `SQL_` で始まる）・予測できる乱数（`PREDICTABLE_RANDOM`）・メールのヘッダーへの差し込み（`SMTP_HEADER_INJECTION`）の指摘は priority によらず失敗。誤検知は `backend/config/spotbugs-exclude.xml` に理由を書いて外す）、OSV-Scanner（下の判定）、Gitleaks（リポジトリの履歴全体） |
| 9 | 成果物と量の確認（`verifyArtifact`） | 画面を同梱した実行可能 WAR（`backend/build/libs/mastersmith.war`）、WAR の中の DSL の JSON Schema と正本の一致、WAR の中の素材（アイコン・favicon）と `frontend/public/` の一致（`verifyFaviconInWar`）、初回の読み込みの JavaScript の量（500KB を超えたら警告だけ） |

メールのテストはコンテナを使いません。テスト用の SMTP の受け手を JVM の中で起動し、TLS の証明書はテストの実行のたびに JDK の `keytool` で一時のディレクトリに作ります（リポジトリに置かない）。そのため、コンテナの実行環境が無くても飛ばされません（飛ばされうるのは対象DB のテストだけです）。

画面の単体テストには、時間を測って目標を超えたら失敗にするものが2つあります。どちらも目標を延ばしません。画面のテストは `verify` の中でカバレッジなし（5 の段）とカバレッジつき（7 の段の `frontendCoverage`）の2回流れ、どちらでも判定します。失敗したときは、各回の値・Node の版・CPU が出力に出ます。

- `frontend/src/shared/format/timeZone/conversionPerformance.test.ts`: 時点の書式（`formatInstant`）1万回を5回測り、中央値で合計 500 ミリ秒・1回あたり平均 0.05 ミリ秒以内。
- `frontend/src/features/preferences/TimeZoneOptionsPerformance.test.ts`: 表示のタイムゾーンの選択肢（ブラウザが答えるすべての名前と時差）の組み立てを、毎回新しい名前の配列で5回測り、中央値で 100 ミリ秒以内。Node は `Temporal` を持つため、テストでは `Temporal` の道を測ります（`Temporal` を持たないブラウザの `Intl` の道の結果が同じことは `offsetLabel.test.ts` が確かめます）。

各段だけを実行することもできます（例: `./gradlew verifyFormat`）。報告は `backend/build/reports/`（テスト・JaCoCo・SpotBugs）、`frontend/coverage/`、`build/reports/osv-scanner/osv.json` に出ます。

### 全件の `verify` と統合の前の `verifyQuick` の使い分け

入口は2つあります。どちらを流すかは `aidlc/spaces/default/memory/team.md` の Way of Working の決まりによります。

- **全件の `./gradlew verify`**: 上の表のすべて。CI は引数なしのこれを流します。手元では次の場面で必ず流します。
  - Intent の最後の Build and Test（`:backend:cleanTest :backend:cleanIntegrationTest` を付けて流し、テストの件数とカバレッジの実測を記録する）
  - リリースの前（`develop` から `main` への取り込みの前）
  - `backend/build.gradle.kts` の `packagesJudgedByTotal` のパッケージに手を入れる Bolt の統合の前
  - Dependabot の知らせ（依存の版と lockfile の更新）の取り込みの前
- **`./gradlew verifyQuick`**: 上の場面以外の Bolt の統合の前に流します。`verify` と同じ段の表と順から、3つのタスクを外し、JaCoCo の報告を単体テストだけの参考値に置き換えたものです。外した結合テストとバックエンドのカバレッジの下限は、次の全件の `verify` と CI（統合の後の再確認）で確かめます。

```bash
./gradlew verifyQuick                              # 結合テストを流さない
./gradlew verifyQuick -PitTests=AuditTraceIdIT     # 当たる結合テストだけを 6 の段で流す
```

| 段 | タスク | `verify` | `verifyQuick` |
|---|---|---|---|
| 0 準備 | `checkToolchain`・`vendorInstall`・`vendorBuild`・`vendorUnchanged`・`mustacheVendorUnchanged`・`frontendInstall` | ○ | ○ |
| 1 フォーマット | `spotlessCheck`（ルート・`:backend`）・`frontendFormatCheck`・`perfToolFormatCheck` | ○ | ○ |
| 2 リンタ | `frontendLint`・`frontendLintCss`・`perfToolLint`・`perfToolEslint` | ○ | ○ |
| 3 ライセンス | `frontendLicenseCheck`・`frontendSvgCheck` | ○ | ○ |
| 4 ビルド | `compileJava`・`compileTestJava`・`frontendTypecheck`・`perfToolTypecheck`・`frontendBuild`・`frontendDistCheck` | ○ | ○ |
| 5 単体テスト | `:backend:test`（`*Test`） | ○ | ○ |
| 5 単体テスト | `frontendTest`（計測なし） | ○ | 外す |
| 6 結合テスト | `:backend:integrationTest`（`*IT`） | ○ 全件（`-PitForks=N` で手元だけ並べられる） | 外す。`-PitTests=…` を付けたときだけ当たる IT を流す |
| 7 カバレッジ | `jacocoTestReport` | ○（単体＋結合の記録） | `jacocoQuickReport` に置き換える（単体テストだけの参考値。下の「参考値の読み方」） |
| 7 カバレッジ | `jacocoTestCoverageVerification` | ○ | 外す |
| 7 カバレッジ | `frontendCoverage`（計測つき・下限の判定） | ○ | ○ |
| 8 セキュリティ | `spotbugsGate`・`osvScan`・`gitleaksScan`・`perfEvidenceScan` | ○ | ○ |
| 9 成果物 | `bootWar`・`verifyDslSchemaInWar`・`verifyFaviconInWar`・`frontendBundleSize` | ○ | ○ |

- `verifyQuick` の画面のテストは、計測つきの `frontendCoverage` の1回だけです。画面のカバレッジの下限（行 80%・分岐 70%）は `verifyQuick` でも判定し、下回れば失敗します。
- 1つのタスクが失敗したら後ろの段を流さないのは `verify` と同じです。どちらの入口でも、WAR（`backend/build/libs/mastersmith.war`）は 9 の段で作られます。
- タスクの一覧と順は `./gradlew verifyQuick --dry-run` で確かめられます。`node scripts/verify-graph-check.mjs` は、`verify` の一覧が変わっていないこと、`verifyQuick` の一覧と順、下の `-P` の誤りの扱いを `--dry-run` だけで確かめます（テストは流しません）。
- この台本は、検査の入口（`build.gradle.kts`・`backend/build.gradle.kts` の段やタスク）を変えたときに手で流す回帰の確かめです。`verify` にも CI にも入っていません（ライセンスヘッダーの検査の対象に `scripts/` を足して検査に載せるかは、後の Intent で決める）。
- 比べの元 `scripts/verify-graph-baseline.txt` は、今の `verify` のタスクの一覧（`--dry-run` の出力のタスクのパスの行だけ、出力の順のまま）です。`verify` の段やタスクを意図して変える Intent では、変えた後に `./gradlew verify --dry-run` を流して比べの元を取り直し、取り直したことと前後の差を、その Intent の記録に書きます。取り直さないと台本の項目 1 が誤って落ちます。

#### 全件の `verify` の場面に当たるかを確かめる（`packagesJudgedByTotal`）

`packagesJudgedByTotal` のパッケージに手を入れる Bolt（`src/main` の変更。説明文だけの直しを含む）は、統合の前に全件の `verify` を流します。当たるかは、統合の前に次で確かめます（何も出なければ当たらない）。結果（当たったパッケージ、または当たらないこと）は Bolt の統合の記録に書きます。

```bash
git diff --name-only develop... -- backend/src/main | grep -E 'cherry/mastersmith/(access/service|audit/repository|common/error/domain|common/error/service|common/health|common/i18n/domain|common/web)/[^/]+$'
```

この一覧は `backend/build.gradle.kts` の `packagesJudgedByTotal` と同じ 7 パッケージです（2026-11-08 の時点）。一覧から外れたパッケージがあれば、このコマンドからも外します。

#### 結合テストを絞る（`-PitTests`）

- `-PitTests=<形>` は、テストのクラス名の形（Gradle のテストの絞り込みと同じワイルドカード）です。カンマで区切って複数を指定できます（例: `-PitTests=AuditTraceIdIT,*LoginIT`）。対象は結合テスト（`*IT` のクラス）だけです。
- 当たる結合テストが1つも無いとき（打ち間違いなど）は失敗します。空・空白・カンマだけの値も、理由を示して失敗します。
- `verify` には付けられません（付けると理由を示して失敗します。`verify` は結合テストを全件流す入口のため）。結合テストだけを流すときは `./gradlew :backend:integrationTest -PitTests=<形>` も使えます。
- 対象DB のコンテナを使うテストを流すときは、`verify` と同じくコンテナの実行環境（colima）を動かし、`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡します（`docs/target-db.md`）。`-PitTests` を付けたときは、コンテナの実行環境が無いと、対象DB のテストを飛ばさずに失敗します（CI と同じ扱い。Gradle が結合テストの JVM にシステムプロパティ `mastersmith.requireContainerRuntime=true` を渡し、`ContainerRuntimeCheck` が失敗にする）。`verifyQuick` はバックエンドのカバレッジの下限を判定しないため、飛ばしを失敗で知らせるためです。失敗したら、colima を起動して流し直してから統合します。`-PitTests` を付けない全件の `verify` の手元の扱い（警告を出して飛ばし、パッケージごとのカバレッジの下限で失敗する）は変わりません。

#### 結合テストを並べる（`-PitForks`）

- `-PitForks=<N>` を付けると、結合テストを N 個の JVM に分けて並べて流します。付けないときは今までどおり1つの JVM で流します。1 以上の整数でない値は、理由を示して失敗します。
- 全件の `./gradlew verify -PitForks=<N>` と、`-PitTests` を付けた `verifyQuick` に効きます。CI は付けずに流します（変えません）。
- 各 JVM の JaCoCo の記録は同じ `backend/build/jacoco/integrationTest.exec` に追記されます。並べるとテストの JVM のヒープ（1 つあたり 1g）と対象DB のコンテナが N 倍になりうるため、N は手元の資源に合わせて選んでください。並べて落ちるテストが出たときは、`team.md` の「不安定なテストと CI の失敗」の決まりに従います。
- 推奨は **N=3** です。2026-11-08 に、8 CPU・16GiB の PC（colima は 4 CPU・6GiB）で `./gradlew :backend:cleanIntegrationTest :backend:integrationTest` を N ごとに1回ずつ流して測りました（Intent 261107-verify-quick-local）。どの N でも 1,716 件が通り（失敗 0 件、飛ばし 10 件）、N=3 が最も速くなりました。N=4 で遅くなったのは、colima の CPU（4）をテストの JVM と対象DB のコンテナで取り合ったためと見ています（確かめてはいません）。各 N を1回ずつ測っただけなので、揺れを含みます。PC や colima の資源が違うときは、測り直して選んでください。

  | N | 結合テストの時間 | N=1 に比べた速さ | colima の VM のメモリの最大 |
  |---|---|---|---|
  | 1（付けない） | 23分05秒 | 1.00 倍 | 1,617 MiB |
  | 2 | 15分26秒 | 1.50 倍 | 1,595 MiB |
  | 3 | 9分43秒 | 2.38 倍 | 2,389 MiB |
  | 4 | 13分53秒 | 1.66 倍 | 1,681 MiB |

#### JaCoCo の参考値の読み方（`verifyQuick`）

`verifyQuick` の 7 の段の `:backend:jacocoQuickReport` は、単体テストの実行記録（`backend/build/jacoco/test.exec`）だけから報告を作り、次の1行を表示します。

```text
JaCoCo の参考値（単体テストの記録 test.exec だけ。下限の判定はしない）: 行 NN.N%（覆った数 / 全体）、分岐 NN.N%（覆った数 / 全体）。報告: …/backend/build/reports/jacoco/quick。下限（行 80%・分岐 70%）の判定は、全件の ./gradlew verify と CI で行う。
```

- 報告は `backend/build/reports/jacoco/quick/`（`jacocoQuickReport.xml` と `html/`）に出ます。`verify` の報告（`backend/build/reports/jacoco/test/`）は上書きしません。
- `-PitTests` で流した結合テストの記録も、前の回の `verify` が残した `integrationTest.exec` も読みません。読んだ記録は、HTML の報告の「Sessions」の一覧（XML の `sessioninfo`）で確かめられます。
- 結合テストでしか通らない層（`web` など）は低く出ます。この値は判定にも、カバレッジの報告にも使いません（下限を下回っても失敗しません）。カバレッジの実測の値は、全件の `verify` の報告から読みます。
- 報告は毎回作り直します（UP-TO-DATE で飛ばしません）。`./gradlew :backend:jacocoQuickReport` だけを流して、手元の `test.exec` の参考値を見ることもできます。

### 依存関係の脆弱性の判定（OSV-Scanner）

検査の対象は `backend/gradle.lockfile`、`frontend/package-lock.json`、`vendor/make-you-chic-ui/package-lock.json` です。

| 対象 | 統合を止める（失敗） | 警告だけ |
|---|---|---|
| バックエンド（Gradle） | 重大度 High 以上（CVSS 7.0 以上） | それ未満 |
| 画面の実行時の依存関係（`frontend/package.json` の `dependencies` とその依存。make-you-chic-ui の実行時の依存関係を含む） | 重大度 High 以上 | それ未満 |
| 画面の開発用の依存関係（`devDependencies` とその依存。make-you-chic-ui の開発用の依存関係を含む） | 成果物を作る道具（`config/npm-build-tools.txt` に書いたもの）の重大度 High 以上 | それ以外のすべて |
| すべての npm の依存関係 | 悪意のあるパッケージ（OSV の ID が `MAL-` で始まるもの）は重大度によらず失敗 | — |

- 実行時か開発用かは、lockfile の各項目の印（`dev`）で判定します。lockfile で見つからないものは実行時として扱います。
- 重大度の分からないものは警告として表示します。警告も毎回すべて表示されるので、定期的に見直してください。
- 画面を作る道具（Vite など）を足したり入れ替えたりしたときは、`config/npm-build-tools.txt` も見直してください。
- 手元の `./gradlew verify` では、lockfile が変わらないと OSV-Scanner（`osvScan`）が前回の結果のまま飛ばされます（UP-TO-DATE）。新しく公表された脆弱性を手元で確かめるときは、`./gradlew osvScan --rerun` で流し直してください。CI はまっさらな環境で動くため、毎回確かめます。

## 規模・テストの件数・カバレッジの報告（CI の要約）

CI（`.github/workflows/ci.yml` の `verify` の job）は、`./gradlew verify` の後に、プログラムの規模・バックエンドのテストの件数・カバレッジを実行の要約（GitHub の Actions の実行の画面の Summary）に書きます。手元でも同じ台本で数えられます。

### 手元で規模を数える

前提は git と cloc です（手元は `brew install cloc`。CI は `apt-get install cloc`）。台本は Node の組み込みだけで動き、npm の依存はありません。リポジトリのルートで流します。

```bash
node scripts/program-size.mjs > build/program-size.md                   # 今の作業ツリーを数える
node scripts/program-size.mjs --base <commit> > build/program-size.md   # 大分類と中分類の表に、そのコミットとの差の列を足す
```

- 数えるのは git が管理するファイルだけです（未コミットの変更は、管理しているファイルの今の中身として数えます。管理していないファイルと `.env` などの git の対象外のファイルは数えません）。外すもの（`aidlc/`・`vendor/`・AI の道具の設定・lockfile など）と分類の決まりは、`scripts/program-size.mjs` の冒頭にあります。
- `--base <commit>` は、そのコミットの中身を `git worktree` で `build/program-size-base` に取り出して同じ決まりで数え、「コードの差」「ファイルの差」（符号つき）の列を足します。取り出した作業ツリーは終わったら消します。比べる元を取り出せないときは差の列を「—」にし、理由を1行出します。
- cloc の版が違うと値がずれることがあります。報告の「道具」の行に cloc の版が出ます（同じコミット・同じ版なら、手元と CI の値は一致します）。
- git や cloc が無いときは、理由を標準エラーに出して終了コード 1 で終わります。
- CI の要約と同じものを手元で見るときは `node scripts/ci-summary.mjs --base <commit> > build/ci-summary.md` を流します（`$GITHUB_STEP_SUMMARY` が無ければ標準出力に書きます）。

### CI の要約の読み方

要約は「規模 → テストの件数 → カバレッジ」の順に並びます。台本は `scripts/ci-summary.mjs` です。

| 欄 | 出どころ |
|---|---|
| プログラムの規模 | `scripts/program-size.mjs`（cloc）。中分類と言語より後の表は畳んであります |
| テストの件数（バックエンド） | `backend/build/test-results/test/` と `integrationTest/` の `TEST-*.xml`（通った・失敗・飛ばした）。画面（Vitest）の件数は載せません |
| カバレッジ（バックエンド） | `backend/build/reports/jacoco/test/jacocoTestReport.xml`（全体と、畳んだパッケージごとの表）。下限と `packagesJudgedByTotal` は `backend/build.gradle.kts` から読みます |
| カバレッジ（画面） | `frontend/coverage/coverage-summary.json`（全体と、畳んだ領域ごとの表。領域はファイルごとの値の和） |

- 割合は小数 2 桁で切り捨てます（`coverage-summary.json` と同じ）。パッケージごとの表の「下限との差」はポイントで、印は「満たす」「下限まで 5 ポイント未満」「下限を下回る」「全体で判定（packagesJudgedByTotal）」のどれかです。`packagesJudgedByTotal` のパッケージは全体の合計で判定するため、下限を下回っていても `verify` は失敗しません。
- **「—」**: 値が出せないことを表します。規模の差の列では、比べる元が無いとき（手動の実行・タグのプッシュ・ブランチの最初のプッシュ（0 が並ぶ値）・履歴に無いコミット）で、理由が「前の回との差は出せない: …」の1行に出ます。比べる元は、`develop` へのプッシュのときだけ、プッシュの前の先頭（`github.event.before`）です。分岐の無いパッケージの分岐も「—」です。
- **「報告なし（./gradlew verify が途中で落ちた見込み）」**: その欄の報告のファイルが無いことを表します。`./gradlew verify` がテストやカバレッジの報告を作る前に落ちた回に出ます。あわせて、その手順に警告（`::warning::`）が出ます。cloc が入らなかったときは、規模の欄に「作れなかった（cloc が見つからない…）」と出ます。
- 要約を書く手順は `./gradlew verify` が落ちた回にも流し（`if: always()`）、どの欄が作れなくても CI を失敗にしません（`continue-on-error: true`、台本の終了コードも 0）。CI の合否は今までどおり `./gradlew verify` で決まります。
- 要約と成果物に載せるのは、分類・パッケージ・領域の名前と、行数・件数・割合だけです（リポジトリは公開のため）。

### カバレッジの HTML の報告（成果物）

CI の実行ごとに、JaCoCo の HTML の報告（`backend/build/reports/jacoco/test/html/`）と画面のカバレッジの報告（`frontend/coverage/`）を、1つの成果物 `coverage-html-<コミットのハッシュ>` として **14 日** 保存します。実行の画面の Summary の下の「Artifacts」から zip で取れます（`gh run download <run-id> -n coverage-html-<コミットのハッシュ>` でも取れます）。zip の中の `backend/build/reports/jacoco/test/html/index.html` と `frontend/coverage/index.html` を開きます。報告が無い回（`verify` が早く落ちた回）は、成果物が作られないか、ある分だけになります（失敗にはしません）。

### 道具の採用の理由と、台本の扱い

- cloc は GPL-2.0 の道具です。CI と手元で規模を数えるために動かすだけで、WAR やイメージには入れず、ソースにも組み込みません（`team.md` の Code Style の新しい依存の決まりに沿った採用の理由）。CI では版を固定せず、`ubuntu-latest` の apt の版を入れます。報告に版が出ます。
- ルートの `scripts/` の台本（`program-size.mjs`・`ci-summary.mjs` を含む）は、CI の報告の道具であり本体の機能ではないため、単体テスト・ライセンスヘッダー・書式・リンタの検査の対象の外です（Intent 261108-ci-size-coverage での依頼者の決定）。変えたときは、手元で流して出力を確かめてください。

## リリースの前の確かめ

`develop` から `main` へ取り込む前（リリースの前）に、`./gradlew verify` と E2E のほかに、次の2つを手で確かめます。結果は、その回の Deployment Execution の記録に書きます。

**1. 新しく公表された脆弱性（`osvScan --rerun`）**: ソースを変えなくても、取り込みの前に新しい公表で失敗することがあります。`verify` の中の `osvScan` は lockfile が変わらないと飛ばされる（UP-TO-DATE）ため、流し直します。

```bash
./gradlew osvScan --rerun
```

期待の出力: 最後の行が `OSV-Scanner: 失敗の条件に当たるもの 0 件、警告 N 件`（N は開発用の依存関係の警告の件数。数は公表によって変わる）で、`BUILD SUCCESSFUL`。「失敗の条件に当たるもの」が 1 件以上なら取り込まない（原因の依存と版を記録し、依頼者に諮る）。

**2. 画面の成果物に axe-core が入っていないこと**: axe-core（アクセシビリティの検査の道具）は開発用の依存関係で、画面のビルド結果（`frontend/dist`）と WAR に入れません。画面を作り直した後（`./gradlew verify` の後）に、次のコマンドが **何も出さない**（終わりの値が 1）ことを確かめます。

```bash
grep -rIl 'axe-core\|axe\.min' frontend/dist     # 期待の出力: なし（終わりの値 1）
unzip -l backend/build/libs/mastersmith.war | grep -i axe   # 期待の出力: なし（終わりの値 1）
```

- 1行目が何かのファイル名を出したら、axe-core が画面の成果物に入っている。入れた依存（`dependencies` に axe-core や vitest-axe を足していないか）と、本体のソースが axe-core を読み込んでいないか（`frontend/src` のテストのファイル以外）を調べる。
- 画面を作り直していない（`frontend/dist` が古い）ときは、`./gradlew verify`（または `:frontend:` の画面のビルド）の後に流す。

## ビルドした WAR での画面の確認（E2E）

`./gradlew verify` と CI には入れていません。統合の前と、リリースの前に手で実行します。

```bash
cd frontend && npx playwright install chromium && cd ..   # 初回と Playwright の更新のとき
docker compose --profile mail up -d mailpit                # メールの受け手（Mailpit）を起動しておく
# 見本の PostgreSQL を E2E の間だけ 127.0.0.1:15432 に開ける（.env.targetdb が要る。下の「E2E の見本の PostgreSQL」）
docker compose -f compose.yaml -f docker/e2e/compose.targetdb-port.yaml \
  --profile targetdb-postgres up -d targetdb-postgres
./gradlew e2eTest                                          # ファイルを選ぶなら -Pe2e.files=e2e/180-business-data-flow.e2e.ts
docker compose --profile targetdb-postgres up -d targetdb-postgres   # 流し終えたら上乗せ無しで作り直してポートを閉じる
```

E2E の WAR はメールを手元の受け手 Mailpit（`127.0.0.1:1025`）へ送ります（`docs/mail.md` の「メール」）。`./gradlew e2eTest` は始める前に Mailpit の API（`http://127.0.0.1:8025/api/v1/info`）に届くかを確かめ、届かなければ起動の手順を示して失敗します（Mailpit の起動・停止はしません）。

E2E は `./gradlew e2eTest` から流します。ファイルを選ぶときは `-Pe2e.files=e2e/<ファイル>`（カンマで区切って複数）を付けます。`npx playwright test` を直接流すと、対象DB の環境変数（`./gradlew e2eTest` が作って渡す）が無いため、設定の読み込みで止まります。

### E2E の見本の PostgreSQL

業務データの代表の流れ（180）は、手元で試す対象DB の見本の PostgreSQL（`targetdb-postgres`。配備したアプリと共有）を使います。`./gradlew e2eTest` は見本の PG の起動・停止をしません。

- 起動: 上の上乗せ（`docker/e2e/compose.targetdb-port.yaml`）を付けて作り直し、見本の PG を `127.0.0.1:${E2E_TARGETDB_PORT:-15432}` に開けます（`compose.yaml` の既定は開けません）。見本の PG の起動には `.env.targetdb`（`docs/target-db.md` の「手元で試す対象DB」）が要ります。作り直しはボリュームを残します。作り直しの間、配備したアプリの業務データの読み取りが短い間失敗します（戻りは自動の見込み）。
- 前提の確かめ（`./gradlew e2eTest` が始める前に行い、満たさなければ手順を示して止める）: (a) コンテナの中で `pg_isready` が応答する、(b) `127.0.0.1:15432` に TCP で届く、(c) DB `business` の PUBLIC に `TEMPORARY` が無い（無ければ `docs/target-db.md` の「既にある見本の DB に書き込みのアカウントを足す」の取り上げの1文を流す）、(d) 公開の番号への誤ったパスワードの接続が拒まれる（`trust` に当たらない。拒まれなければ、すぐに上乗せ無しで作り直して閉じます）。(d) は見本の PG と同じイメージ（同じ digest）の一時の psql を `docker run --rm --network host` で流します。確かめるのは docker の実行環境の側（colima では VM の中）の `127.0.0.1` の公開の番号への接続で、PC の側の `127.0.0.1:15432` の経路は (b) の TCP の到達だけを確かめます（どちらも公開の番号の転送から入るため、`pg_hba.conf` で当たる行は同じ見込みです）。
- 準備: 実行ごとに、スキーマ `e2e_biz_<UTC の年月日時分秒>_<16進 8 文字>` と表（業務データの `e2e_item` と架空の行 25 件、他テーブル参照の `e2e_customer`・`e2e_product`・`e2e_order` と架空の行、日時の `e2e_event`（`timestamptz(6)`、行なし）。`frontend/e2e/support/businessDataSchema.sql`）、一時の読み取りのロール `mastersmith_e2e_r_<印>` と書き込みのロール `mastersmith_e2e_w_<印>`（LOGIN、乱数のパスワード、2 時間で失効。書き込みのロールは行の操作だけで DDL と `TEMPORARY` は無い）を1つのトランザクションで作ります。管理者は `docker compose exec` でコンテナの中のソケットから入り、管理者のパスワードを読みません。ロールの名前とパスワードは `./gradlew e2eTest` のプロセスの環境変数だけで WAR と Playwright に渡します（ファイル・ログ・`webServer.env` に置かない。報告の確かめの対象に入れる）。
- 片付け: E2E の後に（失敗しても）`e2eTargetDbCleanup` が、その実行のロールの接続を切り、`DROP OWNED BY`・`DROP ROLE`・`DROP SCHEMA … CASCADE` で消し、消した件数と接頭辞の残りの件数だけをログに出します。前の実行が途中で止められて残ったものは、次の実行の始めに接頭辞（`e2e\_biz\_%`・`mastersmith\_e2e\_%`）で消します。
- psql の誤りの出力は、失敗した文に一時のパスワードを含みうるため Gradle のログに出さず、段の名前と SQLSTATE と件数だけを出します。見本の PG のサーバーのログ（`docker logs`）には失敗した文が残りうります（2 時間で失効する使い捨ての値）。
- 前の残りを消す段が失敗したときは、手で次を流します（接頭辞に当たるものだけを消します）。

```bash
docker compose --profile targetdb-postgres exec -T targetdb-postgres psql -U target_admin -d business -v ON_ERROR_STOP=1 <<'SQL'
SELECT count(pg_terminate_backend(pid)) FROM pg_stat_activity WHERE usename LIKE 'mastersmith\_e2e\_%';
SELECT format('DROP OWNED BY %I', rolname) FROM pg_roles WHERE rolname LIKE 'mastersmith\_e2e\_%' \gexec
SELECT format('DROP ROLE %I', rolname) FROM pg_roles WHERE rolname LIKE 'mastersmith\_e2e\_%' \gexec
SELECT format('DROP SCHEMA %I CASCADE', nspname) FROM pg_namespace WHERE nspname LIKE 'e2e\_biz\_%' \gexec
SQL
```

- 流し終えたら、上乗せ無しで作り直してポートを閉じます（`docker compose --profile targetdb-postgres up -d targetdb-postgres`）。

WAR をビルドし、一時ディレクトリの内部DBで起動して、`frontend/e2e/` のすべての確認を CSP 違反やスクリプトのエラーなしに通ることを確かめます（番号は 18081。`E2E_PORT` で変えられます）。1つの WAR と内部DBを共有するため、ファイル名の番号の順に1本ずつ実行します。

| ファイル | 確かめる流れ |
| --- | --- |
| `010-skeleton.e2e.ts` | 画面の骨格（ログイン画面の枠と CSP の応答ヘッダー） |
| `020-auth.e2e.ts` | 初期管理者のログインとログアウト、誤ったパスワードの表示 |
| `030-admin-access.e2e.ts` | 代表の流れ「ログイン → ホームの管理のカード（利用者の管理）から管理の画面を開く → ログアウト」 |
| `040-dsl-admin.e2e.ts` | DSL の管理「ログイン → DSL の管理 → 貼り付けで投入 → プレビュー → 適用 → 今の状態が適用中 → ログアウト」（対象DB は見本の PG の実行のスキーマで、投入する DSL のスキーマ `e2e` と違うため、照合は「スキーマの名前の違い」の警告になる。見本の PG の読み取りが失敗すると「接続できない」の警告になり落ちる） |
| `050-display-accessibility.e2e.ts` | ログインの画面の表示の設定の 20 組（テーマ × 文字の大きさ、ブランドカラー × テーマ、幅 375px のテーマ × 文字の大きさ）ごとのアクセシビリティの検査（axe-core、WCAG 2.0・2.1 の A・AA）と横のはみ出し、最初の画面が出るまでの時間の測定（5 回）。ログインはしない |
| `060-invitation-accessibility.e2e.ts` | 招待の管理の画面の表示の設定の 20 組と 360px の悪い側の組（dark・lg・blue）ごとのアクセシビリティの検査（一覧・警告・招待の入力の Modal・取り消しの確かめの Modal。axe-core、WCAG 2.0・2.1 の A・AA）と横のはみ出し、一覧と次のページが出るまでの時間の測定（5 回）。初期管理者でログインする |
| `070-registration-accessibility.e2e.ts` | 登録の完了の画面の表示の設定の 20 組 × 2 つの状態（フォーム・使えないリンク）ごとのアクセシビリティの検査（axe-core、WCAG 2.0・2.1 の A・AA）と横のはみ出し。ログインしない。確かめの API だけを見本に差し替え、完了の要求は送らない |
| `080-preferences-accessibility.e2e.ts` | プリファレンスとパスワードの変更の画面の表示の設定の 20 組 × 2 画面 × 2 つの状態（最初・画面の確かめの誤り）ごとのアクセシビリティの検査（axe-core、WCAG 2.0・2.1 の A・AA）と横のはみ出し、画面を開く・保存・パスワードの変更の時間の測定（5 回ずつ）。初期管理者でログインし、ユーザーメニューから移る |
| `090-invitation-registration-flow.e2e.ts` | 招待と登録の代表の流れ「管理者でログイン → 招待 → Mailpit からリンクを取り出す → 登録の完了 → 新しい利用者でログイン → 管理の画面（`/admin/invitations`）は「権限が無い」の表示、`/admin` は「ページが見つかりません」 → ログアウト」と、リンクを開いてからフォームが出るまでの時間の測定（5 回） |
| `100-app-text-contrast.e2e.ts` | アプリ独自の CSS の文字（プリファレンスの画面の選択のまとまりの誤り `.preferences-choice-error`、DSL の管理の画面の JSON Schema のリンク `.dsl-link`、見つからない画面の「ホームへ」のリンク `.page-link`）の表示の設定の 20 組ごとのアクセシビリティの検査（axe-core、WCAG 2.0・2.1 の A・AA。文字のコントラストを含む）。初期管理者でログインする |
| `110-user-admin-flow.e2e.ts` | 利用者の管理の代表の流れ「管理者でログイン → 利用者の管理で流れの中で作った利用者 U を検索 → U をロック → ロックを解除（失敗回数を戻す）して U が入れる → 本物の 409・400 と見本の形の照らし合わせ → U の利用を止めて U が使えない → 停止を解いて U が入れる → 管理者でない U の画面で本物の 403 から『権限が無い』の表示」 |
| `120-user-admin-accessibility.e2e.ts` | 利用者の管理の画面の表示の設定の 20 組と 360px の悪い側の組（dark・lg・blue）× 12 回の検査（11 の状態で、押せない項目はホバーとキーボードのフォーカスを分けて検査する。一覧・自分の行のメニュー・押せない項目にホバーとキーボードのフォーカス・確かめの Modal・業務の失敗の知らせ・成功の Toast・氏名と言語の入力の Modal とその誤り・検索の上限の誤り・検索に当たらない・読み込みの失敗）ごとのアクセシビリティの検査（axe-core、WCAG 2.0・2.1 の A・AA。文字のコントラストを含む）と横のはみ出し、開いた行の操作のメニューが表示の中に収まること、一覧と次のページが出るまでの時間の測定（5 回）。初期管理者でログインし、管理の API は見本に差し替える |
| `130-admin-forbidden-accessibility.e2e.ts` | 管理の画面の「権限が無い」の表示（S6）の表示の設定の 20 組ごとのアクセシビリティの検査（axe-core、WCAG 2.0・2.1 の A・AA。文字のコントラストを含む）と横のはみ出し、「ホームへ戻る」でホームへ移れること。初期管理者でログインし、サイドバーの「DSL」から DSL の管理の画面を開き、その読み込みの API（`GET /api/admin/dsl/` の下）だけを 403 に差し替える |
| `135-dsl-admin-accessibility.e2e.ts` | DSL の管理の画面（S10）の書式の版 2 で足した状態（適用中の DSL が使われていない注意・読めないプレビューの誤りの一覧と案内・長いスキーマ名と表示名の見出しの行と開いた行）の、ブランドカラー × テーマ × 幅（360px・768px・1280px）の 24 組ごとのアクセシビリティの検査（axe-core、WCAG 2.0・2.1 の A・AA。文字のコントラストを含む）と横のはみ出し。初期管理者でログインし、DSL の管理の状態とプレビューの API だけを見本に差し替える |
| `140-role-admin-accessibility.e2e.ts` | ロール・グループ・権限の受け渡しの画面（S3〜S7）と利用者のロールの Modal（S9）の、表示の設定の 20 組ごとのアクセシビリティの検査（axe-core、WCAG 2.0・2.1 の A・AA。12 の状態。誤りの状態・2語の氏名・64 文字のロール名・長いメールアドレス・`<` と `&` を含む名前）と横のはみ出し、開いた Dropdown と Modal が表示の中に収まること。既定の組で 360px・768px・1280px の幅（表の右端の行の操作のメニューが表示の中に収まることを含む）、道の扱い（深い道の読み込み直し・戻る・進む・未保存の戻る）、画面の時間の測り（記録だけ）。管理の API はすべて差し替え、サーバーへ書き換えを届けない |
| `150-role-permission-flow.e2e.ts` | ロールと権限の代表の流れ「`E2EF_` の版 2 の DSL を API で適用 → 招待から利用者を作る → 初期管理者でロールを作り、テーブルの主権限を READ にして保存し、利用者を割り当てる → 利用者の作業ロールと業務のメニューを API で確かめる → 2つ目のロールを API で割り当て、利用者で画面からログインしてトップバーで作業ロールを切り替えると、サイドバーのテーブルの項目が消え、戻すと出る → 140 と 160 の見本と本物の応答の形を比べる」。後始末はしない |
| `160-app-frame-accessibility.e2e.ts` | 骨組みの画面（作業ロールの切り替え・5段まで開いた業務のメニュー・業務データの一覧（末端のテーブル・権限なし）・自分の権限・ホームの入口のカード（業務と管理の6枚。アイコンは飾り）とサイドバーのアイコンと上のバーのロゴ（押せない飾り、幅 480px 未満では出さない）・ホームの案内（設定なし・表示なし・読み込みの失敗）・畳んだサイドバー）の、表示の設定の 20 組ごとのアクセシビリティの検査（axe-core、WCAG 2.0・2.1 の A・AA。文字のコントラストを含む。40 文字の名前・40 文字のロール名・記号を含む名前）と横のはみ出し、開いた作業ロールの一覧と5段のサイドバーが表示の中に収まること、畳んだサイドバーで今のページを含む1段目のまとまりに印（クラス `contains-current` と読み上げの名前）が付くこと（axe は畳んだ状態と開いた状態の両方）。既定の組で 360px・768px・1280px と悪い側（lg・dark・360px）の幅、英語の表示（トップバーの文言と畳んだ状態の印も英語）、画面の時間の測り（記録だけ）。骨組みの API と業務データの API（`/api/tables/*`）はすべて差し替え、内部DB に書かない |
| `170-menu-navigation-flow.e2e.ts` | 業務のメニューの代表の流れ「`E2EI_` の版 2 の DSL（5段のメニュー、末端がテーブル T1）を API で適用 → ロールを作り T1 を READ・T2 を NONE にする → 招待から利用者を作って割り当てる → 利用者で画面からログインし、5段を開いて T1 の業務データの一覧を開く（再読み込みでも同じ。業務データの API は型を付けた見本で差し替える） → T2 の一覧の道を直接開くと 403 の表示 → 160 の見本と本物の応答（業務のメニュー・自分の権限）の形を比べる → `/logout` の直接の表示と、ユーザーメニューからのログアウト（戻る・再読み込みでもログインの画面）」。後始末はしない |
| `180-business-data-flow.e2e.ts` | 業務データの代表の流れ「実行のスキーマの DSL を API で適用 → ロール（表は FULL・作成・削除あり）を作り招待から利用者を作って割り当てる → 利用者で画面からログインし、メニューから一覧（25 件。ページ送りで 21〜25 件）→ 検索 → 作成 → 詳細 → 更新 → 削除の確かめ → 一覧」。操作と保存はキーボード（Enter）で行い、保存の後に未保存の確かめが出ないことを確かめる。業務データの見本（185・170 が差し替えに使う）と本物の応答の形を比べる。見本の PG を使い、後始末はしない（適用した DSL は残る） |
| `185-business-data-accessibility.e2e.ts` | 業務データの画面（一覧・詳細・作成・更新・削除の確かめ・離れる確かめ・保存されたか分からない知らせ・開けないとき、時点を表す型の列の表示・案内の1行・日時の入力の誤り（読めない値・存在しない時刻）・2回ある時刻の2択・検索の日時の欄を別のテストで 20 組）の、表示の設定の 20 組ごとと 200% の拡大（640×360 の CSS ピクセルで代える。既定の組と dark・lg）のアクセシビリティの検査（axe-core、WCAG 2.0・2.1 の A・AA。規則と要素は外さない）と横のはみ出し。100 カラム × 20 行の一覧（NULL の行・長い表示名）・開いた検索の項目と誤り・右端の行の「操作」のメニューの収まり・読み込み中・10,000 字の値を含む詳細・作成の誤りのまとめ・離れる確かめ・保存されたか分からない知らせ・更新・削除の確かめ・空の一覧・権限の無いテーブルの 403 の表示を回る。ログインの利用者は2語の氏名。既定の組で画面の時間の測り（判定の値と参考の「塗りまで」を記録し、成否にしない）。業務データの API と骨組みの API はすべて型を付けた見本で差し替え、適用中の DSL と見本の PG に頼らない |
| `190-reference-picker-flow.e2e.ts` | 他テーブル参照の代表の流れ「共有の DSL を投入・適用し、自作のロール（注文は FULL・作成あり、顧客と商品は READ）と招待から登録した利用者を作る → 利用者でログインし、メニューから注文の表の作成 → 顧客の選択肢を1つ選ぶ → 商品の「選ぶ…」をキーボードで開き、条件を入れて Enter → 行の「選ぶ」 → 欄に「ラベル (値)」 → 保存（Enter） → 作った行の更新の画面で同じラベル」。見本の PG を使う。参照先の権限を欠く利用者は結合テストで確かめる |
| `195-reference-picker-accessibility.e2e.ts` | 作成・更新の画面の他テーブル参照の部品と参照ピッカー（初期・結果・0 件・長い名前、差し替えで作る失敗・入力不可・誤り）の、表示の設定の組ごとのアクセシビリティの検査（axe-core、規則と要素は外さない）。前提（DSL・ロール・利用者）は自分で作り、本物の状態は見本の PG の実行のスキーマの行で作る。差し替えで作る状態は保存を起こさない |
| `200-business-data-time-flow.e2e.ts` | 業務データの日時の代表の流れ「専用の DSL（`E2ET_`）を投入・適用し、表 `e2e_event`（`timestamptz(6)`）に FULL・作成ありのロールと利用者を作る → 利用者のプリファレンスで表示のタイムゾーンを `America/New_York` にする（ブラウザは `Asia/Tokyo` のまま） → 作成の画面で存在しない時刻 `2026-03-08 02:30:00` は誤りで保存されない → 2回ある時刻 `2026-11-01 01:30:00` を2択の「後（-05:00）」で保存 → 一覧と詳細で `2026-11-01 01:30:00.000000 -05:00` → 更新で名前だけ直して保存し、日時が変わらない（要求の本文に日時の欄の鍵が無い）」。185 の時刻の状態の見本と本物の応答の形を比べる。見本の PG を使い、後始末はしない（適用した DSL は残る） |

050 について:

- 流れの確かめではないため、「機能の Intent ごとに代表の流れを1本まで」の本数に数えません。
- 最初の画面の時間（目標は手元の PC でキャッシュが空の状態から 2 秒以内）は記録だけで、失敗にはしません。CSP の違反と、見た目の設定が `sans` のときの Noto Serif JP のフォントの読み込みは失敗にします。
- 既知の違反は `frontend/e2e/support/axe.ts` の `KNOWN_VIOLATIONS` に名前と対象を指定して扱います。一覧は空で、どの違反も失敗にします。一覧に載せた既知の違反が消えたときも失敗にするため、一覧を変えたときはこの文書も見直します。
- 050 だけを流すときも Mailpit と見本の PG の起動が要ります（`./gradlew e2eTest -Pe2e.files=e2e/050-display-accessibility.e2e.ts`）。

060 について:

- 流れの確かめではないため、「機能の Intent ごとに代表の流れを1本まで」の本数に数えません。
- 組ごとに、ログインの画面から初期管理者でログインし、サイドバーの「利用者の招待」から開きます。一覧の API（`GET /api/admin/invitations?page=…`）の答えだけを見本（`frontend/e2e/support/invitationFixtures.ts`）に差し替えます。招待の入力と取り消しの確かめの Modal は開くだけで、要求は送りません。
- ログインの後は利用者の設定が当たるため、組のテーマと文字の大きさは、ログイン（`POST /api/auth/login`）と復元（`POST /api/auth/session/refresh`）の本物の応答の `user.theme`・`user.fontSize` の2項目だけを書き換えて当てます（`frontend/e2e/support/loginPreferences.ts`）。サーバーの状態（利用者の設定）は変えません。070 はログインの前の画面のため表示の設定の鍵（`mastersmith.display-settings`）で当て、080 はプリファレンスの答え（`GET /api/me/preferences`）の差し替えで当てます（どちらも `loginPreferences.ts` は使いません）。
- 一覧と次のページの時間（目標は一覧 2 秒・次のページ 1.5 秒）は記録だけで、失敗にはしません。CSP の違反、画面の問題、本物の一覧の応答と見本の形の違いは失敗にします。測定は招待の API で招待を 21 件置くため、1 回の実行で Mailpit に 21 通のメールが届きます（Mailpit は消しません）。招待を使える設定が無い WAR では、測定を飛ばして理由を注記に残します（E2E の WAR には SMTP とベース URL が渡るため、念のための備え）。
- 既知の違反は `frontend/e2e/support/axe.ts` の `INVITATION_KNOWN_VIOLATIONS` に画面の状態ごとの名前で扱います。どの状態も空です。ほかの違反と、一覧と一致しない既知の違反は失敗にします。
- 060 だけを流すときも Mailpit と見本の PG の起動が要ります（`./gradlew e2eTest -Pe2e.files=e2e/060-invitation-accessibility.e2e.ts`）。

070 について:

- 流れの確かめではないため、「機能の Intent ごとに代表の流れを1本まで」の本数に数えません。
- 登録の完了の画面はログインの前の画面のため、組のテーマと文字の大きさは 050 と同じく、読み込みの前に表示の設定の鍵（`mastersmith.display-settings`）へ置いて当てます（`loginPreferences.ts` は使いません）。
- フォームの状態は、確かめの API（`POST /api/registration/verify`）の答えだけを見本（`frontend/e2e/support/registrationFixtures.ts` の `VERIFY_SAMPLE`、どの招待にも当たらないトークン `a11y-sample-token`）に差し替えて出します。使えないリンクの状態は、差し替えずにフラグメントの無い `/register` を開いて出します。完了の要求は送らず、サーバーの状態（内部DB・監査・招待）を変えません。
- 既知の違反は `frontend/e2e/support/axe.ts` の `REGISTRATION_KNOWN_VIOLATIONS` に画面の状態ごとの名前で扱います。どの状態も空です。ほかの違反と、一覧と一致しない既知の違反は失敗にします。CSP の違反も失敗にします。
- 070 だけを流すときも Mailpit と見本の PG の起動が要ります（`./gradlew e2eTest -Pe2e.files=e2e/070-registration-accessibility.e2e.ts`。070 はメールを送りませんが、E2E の WAR はメールを Mailpit へ送る設定で起動します）。

080 について:

- 表示のタイムゾーン: `GET /api/me/preferences` の取得の回数は、ログインの直後に表示のタイムゾーンの提供元が 1 回（`GETS_BY_DISTRIBUTION`）、プリファレンスの画面を開くと画面自身が 1 回（`GETS_BY_PREFERENCES_SCREEN`）で、080・100 は差し替えをログインの前に置くため、どちらも差し替えに当たる（合計 2 回。`expect.poll` で待って確かめる。`frontend/e2e/support/preferencesFixtures.ts` の定数）。見本は `preferencesSample(theme, fontSize, displayTimeZone = null)` の 5 項目で、`hasPreferencesShape` は 5 つ目（文字列か `null`）まで確かめる。080 は、保存済みの名前を今のブラウザが解釈できない見本（`Mars/Olympus`）の状態 `preferences-zone-unresolvable`（警告の Alert と、選択が「ブラウザに合わせる」に見えること）も検査します。ブラウザのタイムゾーンは Playwright の `timezoneId: 'Asia/Tokyo'` で明示に固定します。

- 流れの確かめではないため、「機能の Intent ごとに代表の流れを1本まで」の本数に数えません。
- 組ごとに、ログインの画面から初期管理者でログインし、ユーザーメニューの「プリファレンス」「パスワードの変更」から開きます。プリファレンスの画面は開いた時点に内部DB の値へ画面をそろえるため、組のテーマと文字の大きさは、`GET /api/me/preferences` の答えだけを見本（`frontend/e2e/support/preferencesFixtures.ts`、氏名は `検査 太郎`）に差し替え、そのそろえ（本物の `applyUserPreferences`）で当てます。ブランドカラーは 050 と同じく `/api/appearance` の差し替えです。`loginPreferences.ts` は使いません。
- 画面の確かめの誤りの状態は、氏名を空にして「保存する」、3つを空のまま「変更する」で出します。検査では `PUT`・`POST /api/me/…` を送りません（送られていれば失敗）。
- 既知の違反は、状態ごとの名前の一覧（`frontend/e2e/support/axe.ts` の `PREFERENCES_KNOWN_VIOLATIONS`）で扱います。一覧は空です。ほかの違反と、一覧と一致しない既知の違反は失敗にします。CSP の違反も失敗にします。
- 測定は、招待の API で招待を1件置き、Mailpit のメールからリンクを取り出し、登録の完了の API で利用者を1人作って行います（`frontend/e2e/support/registeredUser.ts`、宛先は実行ごとに重ならない `u7-perf-…@example.com`）。1 回の実行で Mailpit に1通届き、新しい利用者と、招待・パスワードの変更の監査が一時の内部DB に残ります。Mailpit の API は読むだけです。
- 時間（目標は開く 2 秒・保存 1.5 秒・パスワードの変更 2.5 秒）は記録だけで、失敗にはしません。本物の `GET /api/me/preferences` の応答と見本の形の違い、CSP の違反、画面の問題は失敗にします。招待を使える設定が無い・Mailpit に届かない WAR では、測定を飛ばして理由を注記に残します（念のための備え）。
- 080 だけを流すときも Mailpit と見本の PG の起動が要ります（`./gradlew e2eTest -Pe2e.files=e2e/080-preferences-accessibility.e2e.ts`）。

090 について:

- 招待と登録の代表の流れの E2E です。前のファイルが作った状態に頼らず、管理者のログインと招待を自分で行い、宛先は実行ごとに重ならない値（`e2e-invitee-<実行時刻>-<乱数>@example.com`）にします。
- 1 回の実行で招待を1件置き、Mailpit に1通届き、新しい利用者と監査の記録が一時の内部DB に残ります（E2E の内部DB は実行ごとの一時のディレクトリ）。
- 招待メールのリンクは Mailpit の API（`GET /api/v1/search`・`GET /api/v1/message/{ID}`）を読むだけで取り出し、書き込まず、メールを消しません（`frontend/e2e/support/mailpit.ts`。API の場所は `build.gradle.kts` の `mailpitInfoUrl` と同じ `http://127.0.0.1:8025`）。
- リンクを開いてからフォームが出るまでの時間（目標は手元の PC でキャッシュが空の状態から 2 秒以内）を5回測り、値と目標以内の回数を注記と添付に残します。時間では失敗させません。同じ5回で、アドレス欄に `#token=` が残らないこと・CSP の違反が無いこと・ブラウザの保存にトークンが無いことは失敗の条件です。
- 本物の確かめの応答の項目の名前と型が見本（`VERIFY_SAMPLE`）と同じことを毎回確かめます（値は比べません）。
- 090 だけを流すときも Mailpit と見本の PG の起動が要ります（`./gradlew e2eTest -Pe2e.files=e2e/090-invitation-registration-flow.e2e.ts`）。
- HTML の報告は作らず、トレースは既定で残しません（下の「結果と報告」）。手元でトレースを有効にした実行のトレースには、090 で開いた使い捨てのトークンを含むリンクが載ります（E2E の一時の内部DB の中の値）。共有しません。json の結果・注記・添付・`test.step` の題には、リンク・トークン・宛先・パスワードを入れません。

100 について:

- 流れの確かめではないため、「機能の Intent ごとに代表の流れを1本まで」の本数に数えません。
- 組ごとに初期管理者でログインし、ユーザーメニューの「プリファレンス」を開いて組を当て（080 と同じく `GET /api/me/preferences` の答えを見本に差し替える）、その後は読み込み直さずに画面の中で移ります（サイドバーの「DSL」の「投入」のタブ、見つからない画面 `/e2e-app-text-contrast-not-found`）。ログインの状態は利用者の保存した設定を持ち、読み込み直すとそれに戻るためです。
- 選択のまとまりの誤りは、`PUT /api/me/preferences` だけを 400 `VALIDATION_FAILED` の見本（`frontend/e2e/support/preferencesFixtures.ts` の `preferencesValidationProblem`、テーマの項目の誤り）に差し替えて出します。保存・投入の要求はサーバーへ送りません（送られていれば失敗）。対象の要素が出ていることを確かめてから検査し、押した後はマウスを要素の外へ移して、hover でない状態の色を検査します。あわせて、プリファレンスの画面の「保存する」（primary のボタン）にマウスを重ね、背景が hover の色に変わったことを確かめてから検査します（要求は送りません）。
- 既知の違反はありません。100 の中の `STATE_KNOWN_VIOLATIONS` は空で、どの違反も失敗にします。一覧に組と状態と要素を指定して載せた既知の違反が一覧どおりに当たらなければ（当たらなくなったときも）失敗にする仕組みは残しています。
- 100 だけを流すときも Mailpit と見本の PG の起動が要ります（`./gradlew e2eTest -Pe2e.files=e2e/100-app-text-contrast.e2e.ts`）。

110 について:

- 表示のタイムゾーンの提供元はログインの直後に 1 回だけ読むため、利用者を停止した後の最初の 401 は、プリファレンスの画面を開いた後の画面自身の取得になる（待つのは待ち始めた以後の応答だけ）。

- 利用者の管理の代表の流れの E2E です（120・130 は流れの本数に数えません）。前のファイルが作った状態に頼らず、利用者 U を招待と登録の完了の API で自分で作ります（`frontend/e2e/support/registeredUser.ts`。宛先は実行ごとに重ならない `u7-perf-<runTag>@example.com`、`runTag` は時刻と 16 文字の乱数の16進）。状態を変えるのは U だけで、初期管理者の印・停止・ロックは変えません。U は消しません（利用者を消す仕組みは無く、宛先は実行ごとに重なりません）。
- 1 回の実行で招待を1件置き、Mailpit に1通届き、U と、招待・登録の完了・ログインの成功と失敗・失敗回数を戻す操作（変えるものが無い拒否の1件を含む）・利用停止と再開・アクセスの拒否の監査が一時の内部DB に残ります。
- ロックは U の宛先と誤ったパスワードでログインの API を 5 回（既定のしきい値。`webServer` はしきい値の設定を渡していません）呼んで作ります。
- 何も差し替えません。例外は最後の確かめだけで、管理者でない U の新しいページのログインの応答の `user.admin` だけを真に書き換え（復元・更新の応答は書き換えない）、サイドバーの「利用者の管理」から本物の一覧の API の 403 `ACCESS_DENIED` を受けて「権限が無い」の表示が出て、ログインの状態の読み直しで「利用者の管理」のリンクが消えることを確かめます。サーバーの状態は変えません。
- 本物の一覧・409（`USER_ADMIN_NO_CHANGE`）・400（`VALIDATION_FAILED`）の応答の項目の名前と型が、120 の見本（`frontend/e2e/support/userAdminFixtures.ts`）と同じことを毎回確かめます（値は比べません。違いは項目の名前だけで知らせます）。
- 招待を使える設定が無い・Mailpit に届かない WAR では、理由の種類だけを注記（`skip-reason`）に残して飛ばします（念のための備え）。統合とリリースの前の実行では、110 に `skip-reason` の注記が無いことを確かめます。
- U の宛先・パスワード・氏名と `runTag` は、作った直後に値のファイル（`frontend/test-results/e2e-secret-values.json`、権限 600）に書き、報告の部品が探し終えたら消します（下の「結果と報告」）。`test.step` の題・注記・添付には入れません。
- 110 だけを流すときも Mailpit と見本の PG の起動が要ります（`./gradlew e2eTest -Pe2e.files=e2e/110-user-admin-flow.e2e.ts`）。

120 について:

- 流れの確かめではないため、「機能の Intent ごとに代表の流れを1本まで」の本数に数えません。
- 組ごとに、ログインの画面から初期管理者でログインし、サイドバーの「利用者の管理」から開きます。組のテーマと文字の大きさは 060 と同じくログインと復元の本物の応答を書き換えて当てます（`loginPreferences.ts`）。
- 管理の API（`/api/admin` ちょうどと `/api/admin/` の下）は、ページごとに1つの差し替えの口（`frontend/e2e/support/adminApiRoute.ts`）で受けます。一覧の GET と止める操作の POST は見本（`frontend/e2e/support/userAdminFixtures.ts`。2語の氏名・長いメールアドレス・ロック中・利用停止・管理者・「あなた」の行）で返し、見本の無い GET は本物へ通さず通信の失敗で返し、GET 以外で見本の無い要求は打ち切って記録します。組の終わりに、口が受けた件数が 1 以上で、打ち切った要求が 0 件であることを確かめます（内部DB と Mailpit に書きません）。
- 差し替えた失敗（409・通信の失敗）に Chrome が出す「Failed to load resource:」の表示は、位置の URL が管理の API の道のものだけを 120 の中で数えて引きます（共有の `support/pageProblems.ts` は変えません）。CSP の違反とほかの問題は失敗にします。既知の違反はありません。
- 一覧と次のページの時間（目標は一覧 2 秒・次のページ 1.5 秒）は、既定の1組（light・md・blue・既定の幅）だけで5回ずつ測り、注記と添付 `user-admin-screen-ms` に記録するだけで、失敗にはしません（本番での判定は配備先が決まった後）。一覧は本物の一覧の API を差し替えの口を通して読む時間（口の上乗せを含む）、次のページは見本の応答での描画の時間（API の時間を含まない）です。
- 120 だけを流すときも Mailpit と見本の PG の起動が要ります（`./gradlew e2eTest -Pe2e.files=e2e/120-user-admin-accessibility.e2e.ts`）。

130 について:

- 流れの確かめではないため、「機能の Intent ごとに代表の流れを1本まで」の本数に数えません。
- 組ごとに、ログインの画面から初期管理者でログインし、サイドバーの「DSL」から DSL の管理の画面を開きます。差し替えるのは DSL の管理の画面の読み込みの API（`GET /api/admin/dsl/` の下）だけで、403・`ACCESS_DENIED` の見本（130 の中の定数。`detail` に画面に出てはいけない目印の文字を入れる）を返します。ほかの管理の API は差し替えず、管理の API への GET 以外の要求を送らないことを確かめます。
- 組のテーマと文字の大きさと、上の帯のアバターの頭文字が2文字になる架空の2語の氏名は、060 と同じくログインと復元の本物の応答を書き換えて当てます（`frontend/e2e/support/loginPreferences.ts` の省略できる `displayName`。渡さない 060 の動作は変わりません）。サーバーの状態（初期管理者の印・氏名・設定）は変えません。
- S6 が出た後のログインの状態の読み直し（`POST /api/auth/session/refresh`）の応答を待ってから確かめます。E2E には初期管理者しかおらず、読み直しの後も管理者のままのため、S6 のまま続きます。管理者でない利用者が URL を開いたときの S6 は、部品のテスト（`frontend/src/app/routing/`）で確かめます。
- 差し替えた 403 に Chrome が出す「Failed to load resource:」の表示は、位置の URL が `/api/admin/dsl/` の下のものだけを 130 の中で数えて引きます（共有の `support/pageProblems.ts` は変えません）。CSP の違反とほかの問題は失敗にします。既知の違反はありません。
- 画面の時間は測りません。注記には組の名前・件数・規則の名前・はみ出し・アバターの文字の数だけを残し、氏名・メールアドレス・パスワード・トークンは入れません。
- 130 だけを流すときも Mailpit と見本の PG の起動が要ります（`./gradlew e2eTest -Pe2e.files=e2e/130-admin-forbidden-accessibility.e2e.ts`）。

140・150 について:

- 140 は流れの確かめではないため、「機能の Intent ごとに代表の流れを1本まで」の本数に数えません。150 はロールと権限の代表の流れ（作業ロールの切り替えを含む）です。
- 140 は管理の API をすべて差し替えの口（`frontend/e2e/support/adminApiRoute.ts`）で受け、本物の利用者・ロール・グループを作りません。画面の時間の測りの値は注記と添付 `u6-screen-ms` に記録するだけで、成否にしません。
- 150 は走らせるごとに一意の名前（`E2EF_` と実行の印）の DSL・利用者・ロール・グループを作り、後始末をしません。適用中の DSL は 150 の `E2EF_` のものに置き換わります（後の番号の E2E は自分で DSL を適用し直します）。

160・170・180・185 について:

- 160 は流れの確かめではないため、「機能の Intent ごとに代表の流れを1本まで」の本数に数えません。170 は業務のメニューの代表の流れです。
- 160 は骨組みの API（`/api/me/work-role`・`/api/me/navigation`・`/api/me/permissions/` の下）を差し替えの口（`frontend/e2e/support/frameApiRoute.ts`）で、業務データの API（`/api/tables/*`）を差し替えの口（`frontend/e2e/support/businessDataApiRoute.ts`）で受け、内部DB に書きません。画面の時間の測りの値は注記と添付 `u7-screen-ms` に記録するだけで、成否にしません。
- 170 は業務データの API だけを型を付けた1つの見本（`frontend/e2e/support/businessDataFixtures.ts`）で差し替えます。業務データの本物の応答との形の比べは、代表の流れ 180 が受け持ちます。
- 170 は走らせるごとに一意の名前（`E2EI_` と実行の印）の DSL・ロール・利用者を作り、後始末をしません。適用中の DSL は 170 の `E2EI_` のものに置き換わります。170 より後に流れる 180 は自分で DSL を適用し直し、185 は業務データの API を差し替えるため、適用中の DSL に頼りません。
- 160・170・180・185 だけを流すときも Mailpit と見本の PG の起動が要ります（`./gradlew e2eTest -Pe2e.files=e2e/160-app-frame-accessibility.e2e.ts` など）。
- 180 は業務データの代表の流れです。走らせるごとに一意の名前（`E2EB_` と実行の印）のロール・利用者を作り、後始末をしません。適用中の DSL は 180 の実行のスキーマのものに置き換わります（実行のスキーマは片付けで消えるため、その後は照合の警告になる）。
- 185 は流れの確かめではないため、本数に数えません。画面の時間の測りの値は注記と添付 `u3-screen-ms` に記録するだけで、成否にしません。見本の行の値は目印（`E2E-BIZ-MARK`）を含む架空の値で、報告の確かめ（`playwright-secret-check-reporter.ts`）がその目印を探します。
- ユーザーメニューを開く手伝いは `frontend/e2e/support/userMenu.ts` です。トップバーに作業ロールの切り替え（同じ形の Dropdown）が並ぶため、作業ロールの入れ物の外のトリガーを開きます。

190・195・200 について:

- 190 は他テーブル参照の代表の流れ、200 は業務データの日時の代表の流れです。195 は流れの確かめではないため、本数に数えません。
- どれも前のファイルの状態に頼らず、DSL の投入と適用・ロール・利用者を自分で作り、後始末はしません（適用中の DSL はそのファイルのものに置き換わります。後に足すファイルは自分で DSL を投入・適用します）。初期管理者の状態・ロール・プリファレンスは変えません。
- 200 は見本の PG の実行のスキーマの表 `e2e_event`（`frontend/e2e/support/businessDataSchema.sql`）に、その流れで1行だけ作ります。行の値は架空の値で、目印（`E2E-BIZ-MARK`）を付けて値のファイルに書き、`test.step` の題・注記・添付には入れません。プリファレンスを変えるのは、その流れで作った利用者だけです。
- 190・195・200 だけを流すときも Mailpit と見本の PG の起動が要ります（`./gradlew e2eTest -Pe2e.files=e2e/200-business-data-time-flow.e2e.ts` など）。

E2E は Mailpit に届いたメールを消しません。`./gradlew e2eTest` の後に片付けるときは、`docs/mail.md` の「手元でメールを見る」の2行（`docker compose stop mailpit` と `docker compose rm -f mailpit`）で止めて消します。

### 結果と報告

- 結果は `frontend/test-results/e2e-results.json`（json の報告）だけに出ます。HTML の報告（`frontend/playwright-report/`）は作りません（HTML の報告は入力の手順の題に入れた値を書くため）。json の報告と `frontend/test-results/` はコミット・共有しません。
- トレースは既定で残しません（`E2E_TRACE` が無いときは `off`）。`E2E_TRACE` に `off`・`on`・`retain-on-failure` の外の値を置くと、設定の読み込みで止まります。
- 実行ごとに作る仮の署名鍵・初期管理者のメールアドレス・仮のパスワードは、`webServer.env` ではなく Playwright のプロセスの環境変数で WAR に渡します。報告の部品（`frontend/playwright-secret-check-reporter.ts`）が、実行の終わりに、これらの値と、110 が作る利用者の値（値のファイル）と値の形（`u7-perf-…@example.com`・`e2e-u7-pw-…`・氏名「計測 花子」）を、json の報告（添付と標準出力の base64 は復号して）・`frontend/test-results/` の下のすべてのファイル（zip は展開して）・前の `frontend/playwright-report/` から探します。見つかったとき、読めない・展開できないファイルがあるときは実行を失敗にします（値は表示せず、値とファイルの種類ごとの件数だけを出します）。
- `frontend/playwright-report/` が残っていれば、報告の部品が探して警告（値があれば失敗）を出します。中を開かずに消してください。

```bash
rm -rf frontend/playwright-report
```

- 失敗したときの画面の写し（`frontend/test-results/` の下の `error-context.md`）は止めていません。110 などが失敗すると、写しに入った利用者の氏名・メールアドレス（初期管理者のメールアドレスを含む）を報告の部品が見つけて失敗の知らせを出します。もともと失敗した実行のため合否は変わりません。写しを読んで原因を確かめた後に、`frontend/test-results/` を消してください（共有しません）。110・120 は、失敗したときだけ値を伏せた手がかり（通った手順の題・要求のメソッドと道の型と状態コード・画面の問題の件数・画面の道・行の数）を添付 `user-admin-diagnostics` に残します。
- それでも原因が分からないときだけ、手元でトレースを有効にして流し直します。この実行は報告の部品がトレースの中の値を見つけて失敗になりうるため、合否には使いません。統合とリリースの前の確かめは `E2E_TRACE` を付けない実行で行います。

```bash
E2E_TRACE=retain-on-failure ./gradlew e2eTest      # 調べたいファイルだけなら E2E_TRACE=retain-on-failure ./gradlew e2eTest -Pe2e.files=e2e/110-user-admin-flow.e2e.ts
(cd frontend && npx playwright show-trace test-results/<テストのディレクトリ>/trace.zip)
rm -rf frontend/test-results                       # 見た後に消す（共有しない）
```
