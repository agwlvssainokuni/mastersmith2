# 配備の流れ（CI/CD）— u3-target-db-time

出典: NFR Design（`construction/u3-target-db-time/nfr-design/security-design.md`・`logical-components.md`）、NFR Requirements（`nfr-requirements/security-requirements.md`・`tech-stack-decisions.md`）、機能設計（`functional-design/functional-spec.md`）、部品の一覧（`inception/domain-design/components.md`）、契約（`inception/contract-design/contract-summary.md`）、`team.md`（Way of Working・Testing Posture・Deployment）、`project.md`（Deployment）。書き方は前の単位（`construction/u1-time-zone-conversion/infrastructure-design/cicd-pipeline.md`）と同じ。

U3 は、対象DB の日時・時刻の値の読み書き、接続のタイムゾーン、既定の DSL の生成の直し（種類 library。アプリに入る）。変えるのはバックエンドの `src/main`（`businessdata`・`targetdb`・`dslmanage/generate`）で、独立した配備の対象ではなく、実行可能 WAR に同梱される。新しいサーバー・表・列・API・画面・ネットワーク・コンテナ・クラウドの資源を持たないため、インフラの仕様（`infrastructure-specification.md`）と監視の設計（`monitoring-design.md`）は、種類 library の決まりで作らない（`cicd-pipeline.md` と `traceability.json` だけを作る）。配備先は開発者の PC 上のコンテナまでで、クラウドの基盤は作らない（`project.md` Deployment）。新しい設計の論点が無いため、質問は作らず、既にある流れの記録として書く（`project.md`）。

## 1. 流れの中の位置

| 段 | 内容 | U3 で変わること |
|---|---|---|
| 作業ブランチ | `develop` から短命のブランチ（`feature/261102-timezone-ui-polish-<bolt>`）を作って作業する | なし |
| 統合前の1コマンドの検査 | `./gradlew verify`（フォーマット → リンタ → ライセンスヘッダー → ビルド → 単体テスト → 結合テスト → カバレッジ下限 → 秘密情報の検出・静的解析・依存関係の脆弱性検査） | バックエンドの検査（Spotless・SpotBugs・ArchUnit・JaCoCo）に U3 のソースとテストが入る。結合テストは3種類の対象DB（MySQL・MariaDB・PostgreSQL のコンテナ）で毎回動く（`team.md` Testing Posture） |
| E2E | `./gradlew e2eTest`（`verify` と CI の外） | `backend/src/main` の業務データの機能（`businessdata`）と業務データを読む `targetdb` に触れるため、統合の前に E2E も流して通す（`team.md` Way of Working）。U3 は画面を持たず E2E の流れは足さない（業務データの日時の代表の流れ1本は u4-business-data-time が受け持つ）。既存の E2E が通ることを確かめる |
| 統合 | `develop` へ squash マージ（1 Bolt が `develop` の1コミット。メッセージは日本語） | なし |
| CI | GitHub Actions が `develop` へのプッシュ時に `verify` と同じ検査を流す。`origin` への `git push` は依頼者自身が行う | なし |
| 成果物 | フロントエンドの `dist` を同梱した実行可能 WAR。版はコミットのハッシュで識別 | U3 は WAR の中のバックエンドのコードに入る。単体では配備しない |
| 配備 | 開発者の PC 上のコンテナ。配備のたびに起動確認とスモークテスト | U3 の確かめは、日時を表示する画面（u4-business-data-time）と既定の DSL の生成の確かめ（Deployment Execution）で行う |

## 2. 検査の関門と、U3 の確かめの対応

| 関門 | U3 の確かめ | 根拠 |
|---|---|---|
| フォーマット・リンタ（Spotless の palantir-java-format） | U3 のソースが検査を通る | SD6.2 |
| ライセンスヘッダー | U3 の新しいソースの先頭に Apache License 2.0 の `/* ... */` のヘッダー（2026、`agwlvssainokuni`） | SD6.2 |
| ビルド | 依存性の注入はコンストラクター注入のみ。日時の型の対応・種類ごとの差は repository の層（と `targetdb.config`）に閉じ、service の層は種類で分岐しない | SD6.2・LC1〜LC6 |
| 構造検査（ArchUnit） | 既存の境界テスト（全体の層の決まり・`TargetDbBoundaryArchitectureTest`・`RoleBoundaryArchitectureTest`・業務データの境界テスト）を緩めず、`java.sql.Connection` の `prepareStatement`・`createStatement` を呼んでよい場所を広げない。足す場合は「足す形」にとどめる。緩める・許可の一覧を広げる必要が出たら、Code Generation の計画に明記して依頼者の承認を得る | SD3.5・SD6.2 |
| 静的解析（SpotBugs＋FindSecBugs） | `SQL_` の指摘は priority にかかわらず統合を止める。除外の設定（`backend/config/spotbugs-exclude.xml`）は広げない。日時の読み書きで、利用者の入力を SQL の文字に組み込む経路を足さない | SD3.5・SD7.10 |
| 単体テスト | 桁を決める部品・読めない値の見分け・番兵の確かめ・入力の確かめ。jqwik の性質ベース（失敗時の乱数の種を記録）。**最悪の場合の測り**: 1万のセルがすべて読めない値でも、変換が合計 500 ミリ秒以内（平均 0.05 ミリ秒以内）。この合格基準を Code Generation の単体テストに置き、測った環境（JDK の版・CPU）を成果物に書く（NFR Design のレビュー R-03）。通常の混在の測りも同じ基準 | NFR2.1.3・NFR2.1.4・LC7.2 |
| 結合テスト（3種類の対象DB のコンテナ） | 日時・時刻の型（`datetime`・`timestamp`・`date`・`time`・`timestamptz`・`timetz` ほか）を、一覧・詳細・作成・更新で確かめる。桁 0・3・6・日付の変わり目・読めない値・`timestamp` の範囲外・初期化（読み取りと書き込みの両方が UTC、初期化は接続を作るとき1回）・初期化の失敗（本番のコードに口を足さず、テストの中で `connectionInitSql` だけ差し替え）・`user.timezone` を変えても不変・TRACE の `XxxSecretLeakIT`・外へ送る設定を有効にしたトレースの属性・接続の数と問い合わせの数が変更の前後で同じ（`*ConnectionUsageIT` の形） | SD7.1〜SD7.11・NFR2.1.1 |
| カバレッジ下限 | 全体の合計に行 80%・分岐 70%。新しく作るパッケージはパッケージごとにも同じ下限。**`packagesJudgedByTotal`（7パッケージ）に当たるかを確かめる**: U3 が触れる見込みのパッケージ（`businessdata.repository`・`businessdata.service` ほか・`targetdb.config`・`dslmanage.generate`）は、今の一覧には載っていない。一覧のパッケージに `src/main` で手を入れることになったときは、テストを足して下限を満たし、一覧から外す（一覧は増やさない）。各パッケージの今の値の実測は Code Generation の計画で行う（`:backend:cleanTest :backend:cleanIntegrationTest` を付けた `verify`）。カバレッジの計測から外すものを増やさない | `team.md` Testing Posture |
| 秘密情報の検出（Gitleaks） | テストデータに実在の値・資格情報を置かない（予約のドメインだけ） | `project.md` Forbidden |
| 依存関係の脆弱性（OSV-Scanner） | 新しい依存を足さない。`backend/build.gradle.kts`・`backend/gradle.lockfile` を変えない。実行時のタイムゾーンの設定の4か所（Dockerfile の `-Duser.timezone`・compose の `TZ`・テストの JVM の `user.timezone`・E2E の起動）も変えない | SD6.1・SD1.5・SD7.11 |

`./gradlew verify` の時間: 約 28 分（直近の記録。CI の上限 `timeout-minutes: 60` の7割の約 42 分が線）。U3 は3種類の対象DB の結合テストを足すため、Bolt の後と Build and Test で `verify` の時間と CI の時間（検査の job の始めから終わり）を測って記録し、42 分を超えたら依頼者にテストの分け方を諮る。コンテナの実行環境（colima）が前提で、`DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を README のとおりシェルに渡す。渡さないと対象DB のテストが SKIPPED になり、飛ばした状態では統合しない。

既存の p95 の前後の記録（NFR2.1.5）: 新しい k6 の場面・perf の種は足さない。既存の業務データの読み取りの場面を、U3 の統合の前後の版で並べて Build and Test に記録する。判定する場面と目標値は、`perf/README.md` と `perf/k6/scenarios.js` の今の定義のまま変えない（目標を緩めない）。

| 場面 | 判定（`iteration_duration` の p95） |
|---|---|
| `bizSearchFirst` | 1000 ミリ秒未満 |
| `bizDetail` | 1000 ミリ秒未満 |
| `bizSearchContains` | 3000 ミリ秒未満（`BIZ_P95_LIMIT_MS`） |
| `bizSearchLast`・`bizSearchLastSorted` | 2000 ミリ秒未満（`BIZ_P95_LIMIT_MS`） |

前の Intent では `bizSearchContains`・`bizSearchLast` が MySQL・MariaDB で Not Met だった（記録済み）。前後の比べは「U3 で悪化していないか」を見るもので、前の Not Met を満たしたことにはしない。測りは使い捨ての環境で行い、長い試験は `caffeinate -dims` で包み、流す前後にスリープの有無を確かめる（`project.md`）。

## 3. 戻し方と、配備の安全

- **戻し方**: 直前の版のイメージでの再配備（`team.md` Deployment）。内部DB のスキーマの変更は無い見込み（`backend/src/main/resources/db/migration` に移行ファイルを足さない。U3 は列・表を足さない）。Code Generation の差分で、移行ファイルが増えていないことを確かめる。足す場合は、前進のみ・1つ前の版のアプリが動く後方互換の決まりに従い、この文書に戻し方を書き足す。`.env` の変更も無い見込みで、内部DB の複写・`.env` の複写は要らない。
- **接続の初期化の変更の戻し方**: PostgreSQL の読み取り・書き込みの両方に接続を作るときの `SET TIME ZONE 'UTC'` を足す変更は、コードの中の設定の変更で、イメージを前の版に戻せば前の初期化（PostgreSQL は初期化なし）に戻る。戻した版でも、型付きの読み書き（`LocalDateTime`・`OffsetDateTime` など）の値は変わらない（NFR Design の D3: 試しで初期化が無くても値が変わらなかった）。変わりうるのは `timestamptz` を文字で読む経路だけ。戻した後の確かめに、見本の PostgreSQL の日時の列の表示が戻す前と一致することを入れる。MySQL・MariaDB の初期化は変えないため、戻しの影響を受けない。
- **配備の入れ替え後の確かめ**: コンテナを入れ替えたら、`docker inspect` でコンテナのイメージ ID が `mastersmith:local` の ID と一致することを確かめ、食い違うときは `--force-recreate --no-deps` で作り直す（`project.md`）。配備の前に、WAR の SHA-256 と中身（U3 の直しが入っていること）を確かめる（Dockerfile は手元の WAR を写すだけのため）。
- **スモークテスト**: 配備の後に、(1) 起動確認（ヘルスチェック）、(2) 見本の PostgreSQL の日時・時刻の列を持つテーブルの一覧・詳細で、値と端数の桁が表示される、(3) 既定の DSL の生成で、端数のある列だけ `dbType.precision` が出て、生成が失敗しない（生成は監査に残る操作のため、送る前に依頼者に伝え、確かめた後にプレビューを破棄する）。日時の表示のタイムゾーンの確かめは u2・u4 の画面で行う。
- **段階的な切り替え**: U3 は新しい API・画面を足さず、既存の業務データの API の中の値の読み書きが変わる。呼ぶ側（画面）の変更は U4 が行う。

## 4. 秘密情報の扱い

U3 は新しい秘密情報・環境変数・資格情報を持たない。対象DB の接続情報は既存の決まりどおり設定（`.env`）だけから受け取り、読み取りと書き込みで分ける（`project.md` Mandated）。接続の初期化の文は定数で、利用者の入力・設定の値を含まない（SD1.1）。接続情報・例外のメッセージ・日時・時刻の値は、ログ・監査・トレースの属性・エラー応答に出さない（`project.md` Forbidden。SD4）。CI の秘密情報の扱いは既存の流れのまま変えない。

## 5. 要注意の点

- **初期化の失敗の見分け方（NFR Design のレビュー R-03 の引き継ぎ）**: 初期化の失敗そのものは、アプリのログにも出ず（SQLState・例外のクラスの名前だけ）、Hikari の DEBUG のログにだけ残る。運用では、業務データの 503（借りる失敗）の警報だけが手がかりになり、借りるたびに `connectionTimeout`（3 秒）の待ちが出る。この見分け方（503 `UNAVAILABLE` が PostgreSQL の読み取り・書き込みの一方だけで続くとき、初期化の失敗を疑い、どうやって確かめるか）は、**Incident Response の手順書に書く**（持ち主の段: incident-response）。アプリは Hikari のロガーを DEBUG にしない（今の設定のまま）。
- **最悪の場合の変換の費用**: 全部が読めない値のページの測りは、NFR Design では未測定。Code Generation の単体テストの合格基準（500 ミリ秒）として置く。外れたときは目標を緩めず、原因を確かめて依頼者に諮る。実時間の測りは負荷の高い CI で不安定になりうるため、複数回の測りの中央値で判定し、失敗時は測った値と環境を出す形にする。落ちたときは `team.md` の「不安定なテストと CI の失敗」の決まりに従う。
- **ドライバーの版に頼る規則**: 桁（MySQL の `getPrecision` からの導出・MariaDB の `getScale`）・PostgreSQL の番兵の値は、pgjdbc 42.7.14・Connector/J 9.7.0・MariaDB 3.5.10 で確かめた。Dependabot の更新で版が上がったときは、3種類の結合テストで再確認する（`verify` の中で毎回動く）。
- **`timestamp` の範囲外の書き込みの動作**は未確認で、結合テストで製品ごとの決めた側の動作を明示してから固定する（SD3.4）。
- **対象DB のコンテナ**: 結合テストは版を固定したイメージのコンテナで動かし、H2 やモックで代用しない（`project.md` Mandated）。テストごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作って消す。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- NFR Design: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u3-target-db-time/nfr-design/security-design.md`・`logical-components.md`・`reviews/review-01.md`
- NFR Requirements: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u3-target-db-time/nfr-requirements/security-requirements.md`・`tech-stack-decisions.md`
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u3-target-db-time/functional-design/functional-spec.md`
- 部品の一覧: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/domain-design/components.md`
- 契約: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-summary.md`
- 前の単位の書き方: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u1-time-zone-conversion/infrastructure-design/cicd-pipeline.md`
- 既存の流れ: `perf/README.md`・`perf/k6/scenarios.js`（場面と目標値）、`backend/build.gradle.kts`（`packagesJudgedByTotal`）、`team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 内部DB のスキーマの変更が無い見込み（U3 は列・表を足さない）。移行ファイルの有無は Code Generation の差分で確かめる。
- [assumption] `./gradlew verify` の約 28 分は直近の記録で、U3 の3種類の結合テストの追加で増える。実際の値は Bolt の後と Build and Test で測る。
- [assumption] `packagesJudgedByTotal` の7パッケージに U3 が `src/main` で触れない見込み（触れる見込みのパッケージは今の一覧に無い）。実測と確かめは Code Generation の計画で行う。
- 未確認（Unverified）: 既存の p95 の前後の値（Build and Test で測る）、`timestamp` の範囲外の書き込みの動作（結合テストで決める）、`verify` と CI の実際の時間。
- 未解決の点は上の確認のほか無い。
