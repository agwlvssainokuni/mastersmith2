# コード生成のまとめ（Intent 261107-verify-quick-local）

ブランチ `fix/261107-verify-quick-local`（`develop` から作成）。`develop` への統合（squash）は、Build and Test の全件の `verify` が通ってから行う（2026-11-08 依頼者の決定）。

## 変えた・作ったファイル

| ファイル | 中身 | コミット |
|---|---|---|
| `backend/build.gradle.kts` | `-PitTests`（空の値は失敗、値があるときは `*IT` のクラスに限って絞り、当たらなければ失敗）、`-PitForks`（1 以上の整数で `maxParallelForks`）、`jacocoQuickReport`（`test.exec` だけ、`build/reports/jacoco/quick/`、参考値の表示、毎回作り直す） | C1 `f1053991` |
| `build.gradle.kts` | `verifyQuick`（`verifyStages` から `frontendTest`・`integrationTest`（`-PitTests` が無いとき）・`jacocoTestCoverageVerification` を外し、`jacocoTestReport` の位置に `jacocoQuickReport`）、`verify` と `-PitTests` が一緒なら失敗 | C2 `e638c4bf` |
| `scripts/verify-graph-check.mjs`（新規） | タスクの一覧と `-P` の誤りの扱いの 8 項目の確かめの台本 | C2 |
| `scripts/verify-graph-baseline.txt`（新規） | 変える前の `./gradlew verify --dry-run` のタスク 63 件 | C2 |
| `aidlc/spaces/default/memory/project.md`・`team.md` | 決まりの書き換え（下の節） | C3 `428b7424` |
| `docs/verification.md`・`README.md` | 使い分けの小節（対照表・`-PitTests`・`-PitForks`・参考値の読み方・全件の場面）、前提の道具・colima・案内の行 | C4 `6ee613c7` |

変えていないもの: `verifyStages`・段のタスク・`verify` の定義、`jacocoTestReport`・`jacocoTestCoverageVerification`・`packagesJudgedByTotal`、`-P` が無いときの `integrationTest` の設定、`.github/workflows/ci.yml`、アプリのソース（`src/main`）。新しい依存は無い。

## 主な決定

- JaCoCo の参考値は、`jacocoTestReport` の読み先を入口で切り替えず、別のタスク `jacocoQuickReport` にした（計画の「依頼者に確かめたいこと」。計画の承認で承認された）。`verify` の報告を上書きしないため。
- `jacocoQuickReport` は `test` に依存させず順だけを付け（既存の `jacocoTestReport` と同じ形）、UP-TO-DATE で飛ばされて参考値が出なくならないよう毎回作り直す（約 3 秒）。
- `-PitTests`・`-PitForks` の値の誤りは設定の段階で失敗する（どのタスクを流しても、`--dry-run` でも失敗する）。
- `verify` に `-PitTests` を付けたときは失敗にした（レビューの R-01）。空・空白・カンマだけの値も失敗（R-05）。

## テスト

| 確かめ | 結果 |
|---|---|
| `node scripts/verify-graph-check.mjs`（8 項目） | 8 passed, 0 failed |
| `./gradlew :backend:spotlessCheck spotlessCheck` | 通る |
| `./gradlew verifyQuick -PitTests=AuditTraceIdIT` | 通る。結合テストは `AuditTraceIdIT` の3件だけ（失敗・飛ばし 0）。2分44秒（`:backend:test` は UP-TO-DATE） |
| `./gradlew :backend:jacocoQuickReport`（前の回の `integrationTest.exec` を残したまま） | 参考値は行 87.3%（13323 / 15258）・分岐 86.0%（5994 / 6966）。報告の sessioninfo は単体テストの記録の1件だけ（FR3.1） |
| `./gradlew verifyQuick -PitTests=NoSuchClassIT` | 「No tests found for given includes」で失敗し、後ろの段は流れない（FR2.2）。19秒 |
| `-PitTests=`・`-PitForks=0`・`-PitForks=x` | 理由を示して失敗 |

この段で流していないテスト（理由）:
- 全件の `./gradlew verify`（`cleanTest`・`cleanIntegrationTest` 付き）と、`-PitTests` なしの `./gradlew verifyQuick`、`-PitForks=<N>` の `verify`: 依頼者の決定で、Build and Test で時間を測りながら1回ずつ流す（長い全件を二重に流さないため）。既存のテストが通り続けること（NFR3）、時間（NFR1）、並べた `verify` のカバレッジの値が直列と一致すること（R-02）はそこで確かめる。
- カバレッジの値: アプリのソースを変えていないため変わらない見込み。下限は Build and Test の全件の `verify` で判定する。

## 計画との差

- 計画の Step 6 の項目 1・3・8 は「一覧・順が一致」と書いたが、台本はサブモジュールの合成ビルドのタスク（`:java-mustache-processor:*`）を順の比べから外し、含まれるかだけを比べる。これらのタスクは段の順の決まりの外にあり、流すたびに位置が変わったため（基準では `compileJava` が `vendorInstall` の前、別の回では後）。台本の冒頭のコメントにも書いた。
- 計画の Step 7 の全件の `verify` は、依頼者の決定（2026-11-08）で Build and Test に移した（計画の文も直した）。
- Step 7 の「前の回の `integrationTest.exec` が残った状態での参考値」は、`-PitTests` なしの `verifyQuick` を流さず、`:backend:jacocoQuickReport` を単独で流して報告の sessioninfo を読んで確かめた。
- Step 7 の始めに colima が止まっていたため、担当が `colima start` で起動した（今も動いている）。

## 承認の場の Request Changes による直し（2026-11-08）

依頼者は承認の場で Request Changes を選び、コードのレビューの R-01〜R-05 を、示した直し方の案のとおりに「この内容ですべて直して」と決めた（計画の Step 11）。

| 指摘 | 直したこと | ファイル |
|---|---|---|
| R-01 `-PitTests` の飛ばしの検出 | `-PitTests` を付けたときだけ、結合テストの JVM にシステムプロパティ `mastersmith.requireContainerRuntime=true` を渡す。`ContainerRuntimeCheck` は、このプロパティか `CI=true` があるとき、コンテナの実行環境が無ければ飛ばさずに失敗にする（判定を純粋な関数 `requiresContainerRuntime` に分け、単体テストを1件足した）。`-PitTests` が無いときの動きは変えていない。`team.md`・`project.md`・`docs/verification.md` の「警告の有無を確かめる」の文を「飛ばさずに失敗する」に直した | `backend/build.gradle.kts`・`backend/src/test/java/cherry/mastersmith/targetdb/testsupport/ContainerRuntimeCheck.java`・`ContainerRuntimeCheckTest.java`・`team.md`・`project.md`・`docs/verification.md` |
| R-02 NFR2 の対応 | `traceability.json` の NFR2 を Deferred（Build and Test の全件の `verify`）にした | `traceability.json` |
| R-03 確かめの台本の扱い | `docs/verification.md` に、台本は手で流す回帰の確かめで `verify`・CI に入っていないこと、検査に載せる（ライセンスヘッダーの検査の対象に `scripts/` を足す）かは後の Intent で決めること、`verify` の段を変える Intent は比べの元を取り直して記録に書くことを書いた | `docs/verification.md` |
| R-04 決まりの行の印 | 今回書き換えた・足した決まりの行（`team.md` 11 行・`project.md` 4 行）の行末に「（2026-11-08 Intent 261107-verify-quick-local で書き換え）」または「（… で追加）」を足した | `team.md`・`project.md` |
| R-05 全件の場面の判定 | `docs/verification.md` に、変えた `src/main` のファイルが `packagesJudgedByTotal` の 7 パッケージに当たるかを一覧するコマンドを足し、`team.md` の全件の場面の行に「判定の結果を Bolt の統合の記録に書く」を足した | `docs/verification.md`・`team.md` |

確かめ:
- `./gradlew :backend:spotlessCheck spotlessCheck :backend:test --tests 'cherry.mastersmith.targetdb.testsupport.ContainerRuntimeCheckTest'`: 通る（6秒）。
- コンテナに届かない状態（`DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を存在しない道にして、colima は止めない）で `./gradlew :backend:integrationTest -PitTests=DslTargetDbIT`: `DslTargetDbIT` が「CI と -PitTests で絞った結合テストではコンテナの実行環境が必要です…」で失敗した（2秒。飛ばしにならない）。
- `node scripts/verify-graph-check.mjs`: 8 passed, 0 failed。

## 依頼者に確かめたいこと

- Build and Test で流す `-PitForks` の N（計画では 2 から試す）。並べて失敗するテストが出たときは、その場で扱いを諮る。
- `docs/verification.md` の `-PitForks` の注意（JVM のヒープと対象DB のコンテナが N 倍になりうる）は、Reverse Engineering の見立て（K-116）を写したもので未検証。Build and Test の実測で直すかを決める。

## 決まりの書き換え（前と後）

計画の Step 8（FR5.1〜FR5.5）。`aidlc/spaces/default/memory/project.md` と `team.md` を手で直接書き換えた（AI-DLC の学びの道具は使っていない）。行末の `(learned …)`・`(affirmed …)`・`(merged …)`・`<!-- cid:… -->` の印は、文を書き換えた行でも残した（下の表では印を省く）。

### project.md

| 節 | 前 | 後 | 要件 |
|---|---|---|---|
| Mandated | ALWAYS 統合前に、フォーマット・リンタ・ビルド・全テスト・秘密情報の検出・依存関係の脆弱性検査（重大度 High 以上）が通っていることを確認する | ALWAYS 統合前に、フォーマット・リンタ・ビルド・テスト（結合テストとバックエンドのカバレッジの下限の判定を除く）・秘密情報の検出・依存関係の脆弱性検査（重大度 High 以上）が通っていることを `./gradlew verifyQuick` で確認し、全テスト（結合テストを含む）とバックエンドのカバレッジの下限の判定は、全件の `./gradlew verify` を流す場面（Build and Test・リリースの前・`packagesJudgedByTotal` のパッケージに手を入れる Bolt・Dependabot の知らせの取り込み。`team.md` の Way of Working）と CI で確認する | FR5.1 |
| Way of Working | ローカルの1コマンドの検査は Gradle の1つのタスク（./gradlew verify）を入口にし、フロントエンドの検査（npm）と外部の道具（Gitleaks・OSV-Scanner）も Gradle から呼ぶ。CI も同じタスクを呼ぶ。 | ローカルの1コマンドの検査は Gradle のタスクを入口にする。全件の入口は `./gradlew verify`、統合の前の速い入口は `./gradlew verifyQuick`（`verify` の段の表からタスクを外したもの。使い分けは `team.md` の Way of Working）とし、フロントエンドの検査（npm）と外部の道具（Gitleaks・OSV-Scanner）も Gradle から呼ぶ。CI は全件の `./gradlew verify` を呼ぶ。 | FR5.4 |
| Testing Posture | テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 | テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けた全件の `./gradlew verify` で実行し直し、実測の数字だけを報告する。`./gradlew verifyQuick` の JaCoCo の値は単体テストだけの参考値で、カバレッジの報告には使わない。 | FR5.4 |
| Testing Posture | colima の PC で `./gradlew verify` を流すときは、README の `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡す。渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗する。 | colima の PC で `./gradlew verify`、または `-PitTests` を付けた `./gradlew verifyQuick` を流すときは、README の `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`（書き方は `docs/target-db.md`）をシェルに渡す。渡さないと対象DB のテストが SKIPPED になり、`verify` ではパッケージごとのカバレッジの下限で失敗する。`verifyQuick` はバックエンドのカバレッジの下限を判定しないため失敗せずに通るので、出力の警告で飛ばしていないことを確かめる。 | FR5.4 |

据え置いた行（`project.md`）:

- Testing Posture の Delivery Planning の実測の行（`:backend:test :backend:integrationTest :backend:jacocoTestReport` で測る）: 入口に依らず個別のタスクを名指しする文で、`verifyQuick` を入れても正しいため。
- 2つの環境変数の書き方は、README ではなく `docs/target-db.md`（README の 24 行から参照）にある。前の文の「README の」は残し、書き方の置き場を括弧で足した。

### team.md

| 節 | 前 | 後 | 要件 |
|---|---|---|---|
| Way of Working（統合の前の検査） | プルリクエストは使わない。統合の前に、CI と同じ検査（フォーマット・リンタ・ライセンスヘッダー・ビルド・全テスト・カバレッジ下限・秘密情報の検出・静的解析・依存関係の脆弱性検査）をローカルで **1コマンド** で実行し、すべて通ってから統合する。これが統合前の関門であり、同じ検査をプッシュ前に自動実行するフック（pre-push）を置いてもよい。 | プルリクエストは使わない。統合の前に、ローカルで **1コマンド** の検査を実行し、すべて通ってから統合する。これが統合前の関門であり、同じ検査をプッシュ前に自動実行するフック（pre-push）を置いてもよい。1コマンドの検査は、次の2つの入口を使い分ける。（下に3つの小項目）<br>・全件の `./gradlew verify`: CI と同じ検査（フォーマット・リンタ・ライセンスヘッダー・ビルド・全テスト・カバレッジ下限・秘密情報の検出・静的解析・依存関係の脆弱性検査）。次の場面では必ずこれを流す: Build and Test、リリースの前（`develop` から `main` への取り込みの前）、`packagesJudgedByTotal`（Testing Posture）のパッケージに手を入れる Bolt の統合の前、Dependabot の知らせ（依存の版と lockfile の更新）の取り込みの前。<br>・`./gradlew verifyQuick`: 上の場面以外の Bolt の統合の前に流す。`verify` から、画面のテストの計測なしの1回（`frontendTest`）・結合テスト（`:backend:integrationTest`）・バックエンドのカバレッジの下限の判定（`:backend:jacocoTestCoverageVerification`）を外したもの（画面のカバレッジの下限の判定は残る）。変えた所に関わる結合テストは `-PitTests=<テストのクラス名の形>`（カンマ区切り。当たるものが無ければ失敗）で絞って流してよい。外した結合テストとバックエンドのカバレッジの下限は、次の全件の `verify` と CI（統合の後の再確認）で確かめる。<br>・`-PitForks=<N>` を付けると、結合テストを N 個の JVM に分けて並べて流せる（手元の `verify`・`-PitTests` を付けた `verifyQuick` に効く。CI は変えない）。 | FR5.2 |
| Way of Working（E2E） | …ブランチごとの統合の前には流さず（統合は `./gradlew verify` で行う）、Intent の最後（Build and Test）とリリースの前に全体を1回流す… | …ブランチごとの統合の前には流さず（統合の前は上の1コマンドの検査（`./gradlew verifyQuick` または全件の `./gradlew verify`）で行う）、Intent の最後（Build and Test）とリリースの前に全体を1回流す…（括弧の中だけを変えた。R-04） | FR5.2 |
| Way of Working（Dependabot） | …手元で版と lockfile をまとめて更新し、1コマンドの検査（`./gradlew verify`）を通してから `develop` に統合し… | …手元で版と lockfile をまとめて更新し、全件の1コマンドの検査（`./gradlew verify`。`verifyQuick` では代えない）を通してから `develop` に統合し… | FR5.2 |
| Way of Working（コンテナの実行環境） | 統合前の1コマンドの検査は、コンテナの実行環境（colima）が動いていることを前提にする。コンテナの実行環境が無いときは、警告を出してコンテナで動かす対象DB のテストだけを飛ばす（黙って飛ばさない）。ただし、飛ばした状態では統合しない。… | 統合前の1コマンドの検査（全件の `verify` と、`-PitTests` を付けた `verifyQuick`）は、コンテナの実行環境（colima）が動いていることを前提にする。コンテナの実行環境が無いときは、警告を出してコンテナで動かす対象DB のテストだけを飛ばす（黙って飛ばさない）。ただし、飛ばした状態では統合しない（`verifyQuick` でも同じ。`verifyQuick` はバックエンドのカバレッジの下限を判定しないため、飛ばしても失敗にならないので、警告の有無を確かめる）。…（後ろの文は同じ） | FR5.2 |
| Testing Posture（足した1文） | （無し） | 統合の前の `./gradlew verifyQuick` は、バックエンドのカバレッジの下限を判定しない（単体テストの記録だけから参考値を `backend/build/reports/jacoco/quick/` に出して表示し、下回っても失敗にしない）。バックエンドの下限は、全件の `./gradlew verify` を流す場面（Way of Working）と CI で判定する。画面のカバレッジの下限は `verifyQuick` でも判定する。 | R-04 |
| Testing Posture（`packagesJudgedByTotal`） | …カバレッジは、その Bolt のテストを足した後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、値を記録する。… | …カバレッジは、その Bolt のテストを足した後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた全件の `./gradlew verify` で実測し（`verifyQuick` の参考値では代えない）、値を記録する。…（全件の `verify` で実測することは据え置き） | FR5.3 |
| Testing Posture（対象DB の結合テスト） | 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）。コンテナの実行環境が無いときの扱いは Way of Working のとおり。 | 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを全件の `./gradlew verify` の中と CI で毎回実行する。統合の前の `./gradlew verifyQuick` では、`-PitTests` で絞ったときに当たるものだけを流す。コンテナの実行環境が無いときの扱いは Way of Working のとおり。 | FR5.3 |
| Testing Posture（時間の記録） | テストを足した Bolt の後と Build and Test で、`./gradlew verify` の時間と CI の時間を測って記録する。CI の上限（`timeout-minutes: 60`）の7割の約 42 分を超えたら、テストの分け方を依頼者に諮る。 | テストを足した Bolt の後は、統合の前に流した1コマンドの検査（`./gradlew verifyQuick`。全件の場面に当たる Bolt では `./gradlew verify`）の時間を、Build and Test では全件の `./gradlew verify` の時間と CI の時間を測って記録する。CI の上限（`timeout-minutes: 60`）の7割の約 42 分を超えたら、テストの分け方を依頼者に諮る。 | FR5.3 |

据え置いた行（`team.md`、R-04）:

- Deployment の CI の行（「`develop` への統合後（プッシュ時）に、ローカルの1コマンド検査と同じ検査を実行する。…」）: CI は全件の `verify` を流し続け（FR4.3）、全件の `verify` と同じ検査であることは今も正しいため。
- Testing Posture の「カバレッジの下限は…バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる」の行: 全件の `verify` と CI では今のままのため。`verifyQuick` ではバックエンドの判定をしないことは、上の足した1文で書いた。
- Way of Working のサブモジュールの行（「統合の前に下の1コマンドの検査を通す」）: 「下の1コマンドの検査」が2つの入口の使い分けを指すようになり、文は変えなくても正しいため。
- Way of Working の OSV-Scanner の行（「OSV-Scanner は `./gradlew verify` と CI を流したときにだけ働き…」）と Testing Posture の E2E の行（「`./gradlew verify` と CI の外に置く」）: 計画の Step 8 の対象に無く、`verifyQuick` でも OSV-Scanner は流れ、E2E は外にあるため、文は誤りにならない。
