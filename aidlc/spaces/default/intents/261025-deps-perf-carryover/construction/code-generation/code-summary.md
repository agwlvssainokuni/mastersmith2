# コード生成のまとめ（261025-deps-perf-carryover）

**Collaborator:** aidlc-developer-agent

範囲は bugfix（深さ Minimal、Test Strategy Minimal）、brownfield、Unit の分割なし。4つの作業ブランチ（B1〜B4）を、それぞれ `develop` へ squash で統合済み。確かめた事実・計画との差・実測の詳細は `generation-notes.md`、要件との対応は `traceability.json`、変えたパスの一覧は `source-manifest.json`。パスはリポジトリのルートからの相対パス。

## 1. 作ったもの（Bolt とコミット）

| Bolt | コミット | 件名 | 要点 |
|---|---|---|---|
| B1 | 98ef3e5 | Spring Boot を 4.1.2 に上げ、Tomcat・Jackson の上書きを外す | `gradle/libs.versions.toml`・`backend/build.gradle.kts`・`backend/gradle.lockfile`・`.github/dependabot.yml`。Tomcat 11.0.26 → 11.0.27、Jackson は 3.1.7 のまま。appender（2.28.1-alpha）と networknt（3.0.6）の固定は残し、コメントと外す時期を更新。テストの補助 `LoggingLevelResetTestExecutionListener` を足した |
| B2 | 3292282 | Dependabot の更新 17 件を手元で取り込む | イメージ（collector 0.163.2・mailpit v1.31.6・otel-lgtm 0.37.0・mysql 26.10.0）をタグと index のダイジェストで固定、spotbugs 6.5.13・setup-java 6.1.0・gradle-wrapper 9.8.2、npm の更新。`vendor/make-you-chic-ui` の固定先は上げていない |
| B3 | 3bd1f4c | 読み取りのプール既定 10・時間切れ後の接続破棄・符号なしの型名・table-access 削除ほか | FR4.2（既定 10）、FR3.2（C2）、FR3.1（C1）、FR3.3（C3）、FR3.5（C5）、FR3.7（C7 の R-02・R-05・R-06・R-07・R-08） |
| B4 | 5bebcab | 運用の道具・台本・手順書を整える | FR3.4（C4）、FR3.6（C6）、FR3.8〜FR3.11、FR4.3 の文書、FR4.6、FR4.8 の P5 の台本、Build and Test の手順書 |

## 2. 要件ごとの結果

「満たした」は、この段で作り、この段で確かめたもの。測っていない目標は満たしたとせず、持ち越しとして書く。

| 要件 | 結果 | 根拠 |
|---|---|---|
| FR1 依存の更新と固定先のそろえ（FR1.1〜FR1.5） | 満たした（FR1.1 のプルリクエストを閉じる操作を除く） | B2。Dependabot の知らせは作業中に 15 件から 17 件に変わり、その版で取り込んだ。見送った件は無い。`hasInstallScript` の付いた項目は増えていない（`fsevents` の 1 件のまま）。固定先は `grep` で旧い版が残らないことを確かめた。E2E は B3 の統合の前に全件を流し、256 件通過、報告の json に資格情報・接続先の値が入らないことを文字列で確かめた。プルリクエストを閉じる操作は、依頼者が push した後に依頼者に確かめて行う（Build and Test へ） |
| FR2 Spring Boot 4.1.2（FR2.1〜FR2.4） | 満たした | B1。`runtimeClasspath` の解決で Tomcat 11.0.27・Jackson 3.1.7・OpenTelemetry 1.62.0 を確かめ、lockfile の差を記録。`OtlpLogExportIT`・`ExternalExportIT` が通った。`osvScan --rerun` は失敗の条件に当たるもの 0 件 |
| FR3.1 符号なしの型の名前（C1） | 満たした | B3。R-08 の案 A。`unsigned` を含む名前だけ 400、含まない名前は従来どおり（範囲内の外は 409）。直す前に落ちる再現を確かめた |
| FR3.2 時間切れの後に接続を捨てる（C2） | 満たした | B3。MySQL は直す前に落ち、直した後に通った。MariaDB は直す前でも通った（ドライバーの違い。`generation-notes.md`） |
| FR3.3 `GET /api/me/table-access` の削除（C3） | 満たした | B3。消えたことの確かめ `NavigationApiIT.tableAccessPathIsGone`。台本の側は B4 |
| FR3.4 `compact` の取りこぼし（C4） | 満たした（再現はできず） | B4。補充が済むのを待つ形に直した。取りこぼしは 139 回の試みで再現できず、見立て（未検証）と試みの範囲を記録した |
| FR3.5 画面の成功の知らせ（C5） | 満たした | B3。`PasswordChangePage` は直す前に決定的に失敗し、直した後に通った。`PreferencesPage` は見える差が出ず、直す前に失敗するテストは作れなかった |
| FR3.6 固定の値を 1 か所に（C6） | 満たした | B4。`perf/versions.env`・`perf/h2-shell.sh`。`grep` で k6 のイメージと H2 の jar の取り方が各 1 か所 |
| FR3.7 C7 の 5 件 | 満たした | B3。R-02・R-05・R-06・R-07・R-08 とも再現または確かめを同じコミットに含めた |
| FR3.8 監視の確かめ用の台本（G2） | 台本は作った。lgtm が止まらないことの確かめは Build and Test へ | B4。`monitorLight` を使い捨ての環境で 20 秒流して checks 13 件・失敗 0。lgtm は起動していない |
| FR3.9 複写を数える道具（G4） | 満たした | B4。存在しない列でも誤りの文を出さず `列なし` と書く。個人に関する値は表示していない |
| FR3.10 リリースの前の確かめ（G9） | 満たした（取り込みの前にもう一度流す） | B4。`osvScan --rerun` は 0 件、`dist`・WAR に axe-core は入っていない。Build and Test でもう一度流す |
| FR3.11 `checks` の `grep` | 満たした | B4。`jq -c '.metrics.checks // .root_group.checks'` に直し、同じ報告で値が出ることを確かめた |
| FR4.1・FR4.2 読み取りのプール 10 | 既定の変更は満たした。3種類の対象DB の測りは Build and Test へ | B3 で既定を 10 に変更（書き込みは 5 のまま）。Not Met 4 件の測り直しと OOMKilled・`anon` の記録は未実施 |
| FR4.3 部分一致の索引 | 満たした | B4。README・`perf/README.md`・JSON Schema の説明に書いた（画面は変えない） |
| FR4.4 接続の数 | 満たした | B3。`*ConnectionUsageIT` と `TargetDbStartupIT` がプール 10 のもとで通った。上限 5 に頼る数え方は無かった |
| FR4.5 `dslMixed` の `memory.peak` | 持ち越し（Build and Test） | 測っていない。手順は `unit-test-instructions.md` の 7.1 |
| FR4.6 種の流し方と試験の値 | 満たした（2B' の確かめは未実行） | B4。`--default-character-set=utf8mb4` を足し、試験の値を前の Intent の実績に直した。種を入れた後の確かめ（2B'）は文書で、実際には流していない |
| FR4.7 画面の時間 | 持ち越し（Build and Test） | 測っていない。E2E 185 を Intent の最後の全件の中で測る |
| FR4.8 P5〜P7 | P5 は台本を作った（流すのは Build and Test）。P6 は替えた（5節）。P7 は `verify` で尽きなかった | P5 `dslRoleOverlap` は `k6 inspect` で読めることまで。P7 は 4 回の `verify` で `OutOfMemoryError` の失敗なし |
| FR4.9 3種類の負荷の試験 | 持ち越し（Build and Test） | 手順を `unit-test-instructions.md` に書いた。配備したアプリを止める時間は約 4 時間の見込み |
| FR5 配備（FR5.1〜FR5.3） | 持ち越し（Deployment Execution） | この段の範囲の外 |
| NFR1 性能の目標を緩めない | 守った（測りは Build and Test） | 目標の数値と物差しは変えていない |
| NFR2 安全 | 満たした | 各ブランチで `osvScan` が 0 件。`*SecretLeakIT` が通り続け、R-07 の再現も足した |
| NFR3 品質の関門 | 満たした | 各ブランチで `verify` が通った（4節）。E2E は B3 の統合の前に通した |
| NFR4 互換 | 満たした | 符号なしを持たない既存の DSL は変更なしで通った。DB スキーマの変更は無い |
| NFR5 テストの扱い | 満たした | FR3.1・FR3.2・FR3.5 は再現のテストを同じコミットに含めた。テストの説明文は英語 |

## 3. 計画との差と依頼者の決定

- **B2 で E2E を流さなかった**: 依頼者の決定で、B2 では `./gradlew e2eTest` を流さず、B3 の統合の前と Build and Test に回した（計画の Q2: A との差）。Playwright（#64）を含む B2 の E2E の確かめは、B3 の統合の前の全件（256 件）で行った。Intent の最後にもう一度流す。
- **B1 の計画外のテスト補助**: Spring Boot 4.1.2 で文脈の一時停止の後片付けが働かなくなり、TRACE の水準が次のテストへ漏れて 13 件が落ちた。`LoggingLevelResetTestExecutionListener` と `spring.factories` への登録を足した（直す前に落ち、直した後に通ることを確かめた。品質の目標は弱めていない）。
- **B2 の知らせの変動**: 計画の 15 件が作業中に 17 件に変わり（#70・#74 が新規、5 件は置き換え）、collector は 0.163.1 でなく 0.163.2、gradle-wrapper 9.8.2（#74）も取り込んだ。
- **B3 で書き込みの本文 `values` の DSL に無い名前も空の `field` にした**: 依頼者の決定（B3 前半の確認事項 1）。計画の R-07 の範囲（検索の条件・並べ替え・主キー）の外だが、同じ漏えいの経路のため `WriteRequestValidator` も同じ形にした。再現は 40,000 文字の名前（Jackson の名前の上限に収まる長さ）で確かめた。
- **`ApiEndpointCounts.MIN_ENDPOINTS` を 73 → 72**: 道を 1 本消したため。数え方の下限を口の数に合わせたもので、検査は弱めていない。
- **B4 で `dsl-schema-v2.json` と `TargetDbImages.java` に触れた**: 検索の設定（`searchOperator`・`search`）の説明が Step 19 に無かったため、`dsl-schema-v2.json` に `description` を足した（構造・版は変えない）。`TargetDbImages.java` は B2 の mysql の固定、B4 で Javadoc だけを「26.10 の系列」に直した。
- **MariaDB・PostgreSQL の試験の値を前の Intent の実績にした**: 計画は `docker/perf/compose.yaml` の既定としたが、既定（512m）は表の大きさに足りないため、前の Intent が実際に使った値（MariaDB 896M／1408m、PostgreSQL 256MB／768m。MySQL は 1280M／2688m）にした。
- **C6 の JDK のイメージは `perf/versions.env` に集めなかった**: `perf/h2-shell.sh` が使うのは JRE のイメージで、JDK のイメージは perf の手順の外（`docker/hikari-pool.sh` など）にあるため。
- **P6 の替え方**: 測る対象の `GET /api/me/table-access` を消したため、置き場の p95 は測り直せない。代わりに残っている業務のメニュー（`navMenu`）の p95 を測り直して締める案を提案する形にする。目標の数値は変えない。前の Intent の承認済みの記録は書き換えない（5節の確認事項）。
- **C4 の取りこぼしは再現できなかった**: 139 回の試みで 0 回。見立て（接続の作成が速く、窓がほとんど開かない。未検証）と試みの範囲（PC に負荷をかけた再現は行っていない）を記録しただけ。`--fill-wait` を超える経路は確かめていない。
- **他の `grep` の不具合は持ち越し**: `perf/README.md` の `groupPoolLimit_*` を探す `grep` も複数行の報告では何も出さない見込みだが、FR3.11 の範囲外のため直していない（確かめてもいない）。
- **使い捨ての環境の一部の `health` の約 44% が非 2xx**: 直前の `compact` の直後でプールが補充の最中だったこと等が見立てだが、確かめていない。FR3.11 の確かめには関係しない。
- **README の対象DB の版の記述**: B3 前半の確認事項 6 のとおり古かったため、B4 で直した（MySQL 26.10.0・MariaDB 13.0.2・PostgreSQL 18.6）。

## 4. 各検査の実測

| 項目 | B1 | B2 | B3 | B4 |
|---|---|---|---|---|
| `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` | 21分38秒（1回目は 20分17秒で 13 件落ち、直して流し直し） | 27分49秒 | 22分27秒（2回目。1回目は 1 件で落ち、約 2分11秒で停止） | 22分38秒 |
| 単体テスト | 2,589 件（失敗 0） | 2,589 件（失敗 0） | 2,612 件（失敗 0・SKIPPED 0） | 2,612 件（失敗 0・SKIPPED 0） |
| 結合テスト | 1,389 件（失敗 0・SKIPPED 9） | 1,389 件（失敗 0・SKIPPED 9） | 1,375 件（失敗 0・SKIPPED 10） | 1,375 件（失敗 0・SKIPPED 10） |
| 画面のテスト | 記録なし | 226 ファイル・1,975 件 | 226 ファイル・1,977 件 | 226 ファイル・1,977 件 |
| `osvScan --rerun` | 失敗の条件 0 件、警告 15 件 | 失敗の条件 0 件、警告 12 件 | 失敗の条件 0 件 | 失敗の条件 0 件、警告 12 件（Step 44） |

- SKIPPED は他の DB 種別向けの `assumeTrue` だけ。対象DB のテストは `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡して実行された。結合テストが B2 から B3 で 14 件減ったのは、消した道のテストを外したため。
- `verify` の時間は CI の上限（60 分）の 7 割（約 42 分）に対し、最長でも B2 の 27分49秒（約 66%）、B3・B4 は約 53〜54%。
- E2E（`./gradlew e2eTest`、B3 の統合の前）: **256 件通過**（失敗 0・flaky 0・skipped 0）、13.0 分。Playwright 1.64.1、chromium-1248 を `npx playwright install chromium` で入れた。報告の json に資格情報・接続先の値が無いことを確かめた（値は表示していない）。
- カバレッジ（B3 の `verify`）: バックエンド全体 行 97.7%・分岐 91.4%。手が入ったパッケージはすべてパッケージごとの下限（行 80%・分岐 70%）以上で、最小は `targetdb.service` の 89.1%／75.4%。`packagesJudgedByTotal` の 7 パッケージには手を入れていない（一覧は変えず、除外も増やしていない）。フロントエンド全体 行 95.06%・分岐 89.79%（B4 後は分岐 89.77%）。
- `pmset -g log` で、各 `verify` の間に `Sleep`・`DarkWake` が無いことを確かめた。

## 5. 依頼者に確かめたい点（承認の場で）

1. **P6 の替え方**: 置き場の p95 は対象の道を消したため測り直せない。代わりに `navMenu` の p95 を測り直して締める案を提案する形にしてよいか（目標の数値は変えない）。
2. **書き込みの `values` の名前**: 空の `field` にした範囲は、DSL に無い名前と読めないカラム（主権限 NONE）まで。DSL にある書けないカラム（`ro`・主キーの更新）は名前を返したまま。これでよいか。
3. **`PreferencesPage` の直し**: 直す前に失敗するテストを作れなかった（見える差が出ない）。一貫性のための直しと順の固定の確かめにとどまる。これを FR3.5 の再現として受け入れるか。
4. **R-02 の「文の数 0」**: 文の数ではなく、対象DB の接続を借りた回数 0 で確かめた。
5. **R-05 の止める段**: 中継の改造ではなく、止める前に届いた文を一般ログ（`general_log`）で読んで確かめる形にした（MySQL・MariaDB だけ）。
6. **`RowDialect.baseName`**: 整数の型に限って、括弧の後ろの `unsigned` を残す。
7. **MariaDB の再現**: C2 の再現のテストは MariaDB では直す前でも通った（ドライバーの違い）。
8. **ブランチを統合した後の後片付け**: Dependabot のプルリクエスト（17 件）を閉じる操作は、依頼者が push した後に確かめてから行う。
9. **E2E の json に残る一時の内部DB の道**: `jdbc:h2:file:/var/folders/…`（起動の引数。対象DB の接続先ではない）が json に残るのは前からの形。

## 6. Build and Test へ引き継ぐこと

- **FR4.9**: 3種類の対象DB で業務データの負荷の試験を前回と同じ場面・同じ線で流し直し、Not Met 4 件を含む目標の満たし方を件数で記録する。上限に届く形の場面（読み取りのプール 10 に対し 12 VU の `bizSearchContains`）、対象DB の OOMKilled・`oom_kill`・`anon` の最大も記録する（FR4.1・FR4.2）。配備したアプリを止める時間は約 4 時間の見込み。長い試験は `caffeinate -dims` で包む。
- **FR4.5**: `dslMixed` の `memory.peak` を測り直す（線は上限 2g の 90% 未満）。`RETENTION_TIME=0` を配備物に入れるかは、既定の回と試しの回の測定結果を添え、書き込みの途中で止まったときに壊れうる点も添えて、依頼者に諮る。
- **FR4.7**: 画面の時間（U3-NFR1.7a〜e）は、Intent の最後の E2E の全件の中で測る。B2 の分も含め、E2E を Intent の最後に再度流す。
- **FR4.8**: P5 `dslRoleOverlap` を流す。P6 は上の確認事項 1 の結果に従う。
- **FR3.8**: `monitorLight` を流して lgtm が止まらないことと式の値を確かめる。
- **FR3.10**: 取り込みの前に `osvScan --rerun` と axe-core の確かめをもう一度流す。
- **FR1.1**: 依頼者が push した後に、Dependabot のプルリクエストを閉じる。
- **FR3.4**: 取りこぼしを再現できなかった点（見立て）を、PC に負荷をかけた状態で確かめ直すかを諮る。
- **FR5（Deployment Execution）**: WAR の SHA-256 と中身の確認、`main` の先頭から WAR を作り直して配備、起動確認とスモークテスト、取り込みの前の CI の試行ごとの確かめ。
- 済んだもの: README の対象DB の版の記述の直し（B4）。Dependabot の固定先の一覧と実際のそろえ（B2）。
