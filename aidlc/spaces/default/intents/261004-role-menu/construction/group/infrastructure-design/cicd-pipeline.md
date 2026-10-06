# CI/CD Pipeline — U3 group

U3 の検査の流れ（CI と1コマンドの検査）、内部DB の移行、負荷の試験の台本と使い捨ての環境、E2E、統合、戻し方、B3 で確かめることを書きます。

U3 は service の単位で、B3 で作ります。新しい依存・設定・秘密・イメージは足しません。そのため、CI と1コマンドの検査の段・関門は **変えません**。この文書は、既にある仕組みの記録として、どの段で何を確かめるかを書きます。

この単位で増えるのは次の4つです。

- 内部DB の移行1つ（3節）
- k6 の場面と、データの用意の手順（5節）
- テスト（2節）
- ダッシュボードの区画（`monitoring-design.md` 5節。持ち主は Observability Setup）

配備先は開発者の PC 上のコンテナだけです（`aidlc/spaces/default/memory/project.md` の Deployment）。

## 出典

- 答え: `infrastructure-design-questions.md`（Q1: A、まとめの確認は Looks correct）
- 上流（どれも `aidlc/spaces/default/intents/261004-role-menu/` の下）:
  - `construction/group/nfr-design/performance-design.md`（2節）
  - `construction/group/nfr-design/scalability-design.md`（2節）
  - `construction/group/nfr-design/reliability-design.md`（3節・4節）
  - `construction/group/nfr-design/security-design.md`（2〜6節）
  - `construction/group/nfr-design/observability-design.md`
  - `construction/group/nfr-design/logical-components.md`（1節・2節・5節）
  - `construction/group/nfr-requirements/tech-stack-decisions.md`
  - `construction/group/functional-design/functional-spec.md`
  - `inception/domain-design/components.md`
  - `inception/contract-design/contract-summary.md`（C1・C4・C6・C10）
  - `inception/delivery-planning/bolt-plan.md`（B3）
- 既にある仕組み（正とする。読むだけ）:
  - `.github/workflows/ci.yml`
  - `build.gradle.kts`・`backend/build.gradle.kts`
  - `backend/src/main/resources/db/migration/`
  - `perf/README.md`・`perf/k6/scenarios.js`
  - `docker/perf/compose.yaml`
  - `frontend/e2e/`

## 1. CI（GitHub Actions）

`.github/workflows/ci.yml` は変えません。

| 項目 | 今の形 | U3 での扱い |
|---|---|---|
| きっかけ | `develop` へのプッシュ・`v*` のタグ・手動 | 変えない。CI は統合の後の再確認（`team.md` の Way of Working） |
| 実行 | `./gradlew verify`（`ubuntu-latest`、制限時間 60 分） | 変えない。上限切れのテストで時間が延びる（6節） |
| サブモジュール・依存の入れ方 | 固定先のコミット、lockfile どおり | 変えない。U3 は lockfile を変えない（`tech-stack-decisions.md`） |
| 秘密 | CI は秘密を使わない | 変えない（8節） |
| 成果物 | WAR をコミットのハッシュの名前で保存 | 変えない。WAR に移行のファイルが1つ増える |

CI が失敗したときは、次の Bolt に進む前に `team.md` の Testing Posture「不安定なテストと CI の失敗」の決まりで扱います。

## 2. 1コマンドの検査（`./gradlew verify`）の段と関門

段・関門・設定は増やさず、変えません。どれか1つでも失敗したら、全体が失敗します。

| 段 | U3 で確かめること | 関門（失敗の条件） | 当たる要件 |
|---|---|---|---|
| 0〜3 準備・フォーマット・リンタ・ライセンスヘッダー | 足す Java と移行の SQL が、既存の検査の対象になる | 既存のとおり | — |
| 4 ビルド | `group.*`（`group.store` を含む）、`audit`・`user`・`useradmin` の変更のコンパイル | コンパイルの誤り | — |
| 5 単体テスト | `GroupStoreClassificationTest`（例外の区分）、`GroupNameProperties`（jqwik、失敗のときの乱数の種は既存の `exceptionFormat = FULL` で残る）、`GroupBoundaryArchitectureTest`（依存の向き、`group.store` を使うのは `group.service` だけ、`group.repository` に書き込みが無い）、U1 の `ApiAccessArchitectureTest`（7つの口の `ApiAccess(ADMIN)`）、detail の長さ・列挙の名前の長さの単体テスト | 1件でも失敗 | NFR1.3・NFR1.8・NFR1.11・NFR3.4・NFR5.5・NFR6.5・NFR6.6 |
| 6 結合テスト | `logical-components.md` 5節の `*IT`（認可の 28 行、一括代入、IDOR、同時の重なり、上限切れ、違反の読み替え、2つ目のトランザクション、監査、漏えい、WARN、指標の `uri`、問い合わせの回数、接続の本数、監査の書き込みの失敗、移行の後の監査の行）と、U1 の `ApiAccessConsistencyIT` | 1件でも失敗 | NFR1.1〜NFR1.9・NFR2.3・NFR2.6・NFR2.7・NFR3.1・NFR3.3〜NFR3.6・NFR5.1〜NFR5.3・NFR6.1 |
| 7 カバレッジ | 4節のとおり | 全体またはパッケージごとの行 80%・分岐 70% を下回る。除外は足さない | NFR6.4 |
| 8 安全の検査 | SpotBugs ＋ FindSecBugs（`SQL_` は priority にかかわらず止める。名前の付いた引数の問い合わせ）、OSV-Scanner、Gitleaks を、除外を足さずに通す | 既存の基準 | NFR1.9 |
| 9 成果物 | 既存の `bootWar`・`verifyDslSchemaInWar`・初回の読み込みの量 | 既存のとおり | — |

補足:

- 6段の結合テストは、組み込みの H2 の一時の内部DB だけで動きます。コンテナを使わないため、コンテナの実行環境が無くても飛ばされません。
- 同時の重なりは、4つの点の待ち合わせの口（`GroupBarrier`、テストでは `group/testsupport` の `@Primary` の部品）で、時間に頼らずに作ります（`reliability-design.md` 4.2）。

## 3. 内部DB の移行

| 項目 | 扱い | 出典 |
|---|---|---|
| 置き場と番号 | `backend/src/main/resources/db/migration/` の次の空き番号。V10 と固定しない。B1・B2 は移行を足さないため、今の見込みは `V10__u3_group.sql`（既存の形 `V<番号>__u<単位の番号>_<内容>.sql`） | 要件 NFR3.6、`tech-stack-decisions.md` |
| 中身 | `groups`・`group_members`（`user_id` の索引を含む）と `audit_events` の3列。既存の列と行は変えない | `reliability-design.md` 3節、`infrastructure-specification.md` 2節 |
| 当たる時 | アプリの起動時（Flyway、既存の設定）。テストでは結合テストの起動で毎回当たる | `application.yaml`（変えない） |
| 確かめ | 段 6 の結合テストで、移行の後に既存の監査の行が読め、足した3列が空であること | `reliability-requirements.md` NFR3.6 |
| U4 との順 | U4 role の移行は、B4 以降のコード生成の時点の次の空き番号。group の表を外部キーで指す | `logical-components.md` 6節 |

## 4. カバレッジ（段 7）

| 対象 | 今の扱い | U3 での扱い |
|---|---|---|
| `packagesJudgedByTotal`（7 パッケージ） | 全体の合計で判定する既存のパッケージの一覧 | 変えない。U3 が本体に手を入れる `group.*`・`audit.domain`・`audit.service`・`user.domain`・`user.service`・`useradmin.domain` は一覧に無い（`audit.repository` には手を入れない） |
| `group.*`（新しい。`group.store` を含む） | 無い | 自動でパッケージごとの下限（行 80%・分岐 70%）の対象になる |
| 計測の除外 | 起動クラス・設定値だけのクラス・自動生成コード・`vendor/` | 増やさない |

実測は `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で行い、手を入れたパッケージの値を記録します（`team.md` の Testing Posture、`project.md` の学び）。

## 5. 負荷の試験の台本と使い捨ての環境（verify と CI の外）

### 5.1 持ち主

| 作業 | 持ち主 | 出典 |
|---|---|---|
| `perf/k6/scenarios.js` に場面を足す（`groupListFirst`・`groupListLast`・`groupDetail`・`groupRename`・`groupCreate`・`groupDelete`・`groupMemberAdd`・`groupMemberRemove`、`groupPoolLimit`） | B3 のコード生成 | `performance-design.md` 2節、`scalability-design.md` 2.2 |
| `perf/README.md` にデータの用意と流し方を書く | B3 のコード生成 | 同上 |
| 台本を `k6 inspect --include-system-env-vars` で確かめる（場面の名前・閾値・`setupTimeout`） | B3 のコード生成 | `performance-design.md` 2.3、`project.md` の学び |
| 使い捨ての環境で流して判定する | Performance Validation | `monitoring-design.md` 3節 |

### 5.2 使い捨ての環境

| 項目 | 扱い |
|---|---|
| 環境 | `docker/perf/compose.yaml`（変えない）。配備した環境のデータと監査ログには触れない。終わったら `down -v` で消す |
| 一時の `app.env` に足す値 | 合否の回: `MASTERSMITH_DB_MAXIMUM_POOL_SIZE=11`。記録の回: `10`。どちらも `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics`（使い捨てのアプリにだけ。配備したアプリの公開の範囲は変えない） |
| 試験用の利用者 | `perf/README.md` の既存の手順 2（アプリを止めて SQL で入れる）の人数を広げる（詳細の 1,000 人と、メンバーの追加と外しの分）。利用者を作る API は招待とメールの流れのため使わない |
| グループとメンバー | 場面ごとの `setup()` で、API を通して作る（`performance-design.md` 2.1）。要求の多い場面（グループ 1,000・メンバー 1,000 人）は `setupTimeout`（既定 60 秒）を広げる |
| 判定 | `iteration_duration{scenario:…}` の p95 と `checks` の率。`http_req_duration` は並べて記録するだけ。接続プールは `/actuator/metrics` の `hikaricp.connections.*` を試験の前・後・間に読む |
| 流し方 | 台本全体を `caffeinate -i` で包む。colima の VM（CPU 4・メモリ 6GiB）の CPU を分け合わないよう、手順 0 のとおり配備したアプリを止める |

## 6. 検査の時間

- 上限切れのテストは H2 の上限まで待つため、`verify` の時間が延びます。行の排他は 3 秒、一意の鍵・主キーは約 2 秒で、それぞれ数件あります。
- 目標の数値は置きません。Build and Test で、`verify` の全体の時間と延びた分を実測して記録します。CI の `timeout-minutes: 60` は変えません。

## 7. E2E（`./gradlew e2eTest`、verify と CI の外）

- E2E は意図して `verify` と CI の外に置いています。代わりに、統合の前とリリースの前に手元で流します（`team.md` の Testing Posture）。
- B3 は画面に手を入れません。ただし、次の2つに手が入ります。
  - 認可（7つの口の `ApiAccess(ADMIN)`）
  - 利用者の管理の code の移し替え（`USER_NOT_FOUND`。`useradmin.domain`・`user.domain`）
- そのため、統合の前に手元で `./gradlew e2eTest` を流し、全体が通ることを確かめます。利用者の管理の既存の E2E（`110-user-admin-flow`・`120-user-admin-accessibility`・`130-admin-forbidden-accessibility`）も含みます。
- U3 のために E2E のファイルは足しません。グループの画面は U6 が作ります。

## 8. 統合・配備・戻し方・秘密

| 項目 | 扱い | 出典 |
|---|---|---|
| 作業ブランチ | `develop` から作る短命のブランチ（例 `feature/261004-role-menu-b3`）。worktree は使わない | `team.md` の Way of Working |
| 統合の前の関門 | `./gradlew verify`（コンテナの実行環境あり）と E2E（7節） | `team.md` |
| 統合の形 | `develop` への squash（1 Bolt が1コミット）。サブモジュールの更新は無い | `team.md` |
| プッシュ | 依頼者自身が行う | `team.md` |
| 配備 | 既存の手順のまま。イメージの作り方・`compose.yaml`・`.env`・JVM の設定を変えない。起動で移行が当たる | `infrastructure-specification.md` 1節 |
| 配備の段への引き継ぎ | 入れ替えの前の内部DB の複写（バックアップを兼ねる）と、前の版のイメージでの戻しの練習。U4 の移行と合わせて1回 | `infrastructure-specification.md` 4節 |
| 戻し | 直前の版のイメージで起動し直す（イメージだけ）。足した表と列は残り、前の版から見えないだけ | `reliability-requirements.md` の「障害と戻し」 |
| 秘密 | 足さない。CI に秘密を渡さない。負荷の試験の資格情報は、今までどおりリポジトリの外の一時の環境ファイル（値は乱数、表示しない）で渡す。Gitleaks の除外を足さない | `perf/README.md`、`project.md` の Forbidden |
| 依存 | 新しい依存を足さない。lockfile を変えない | `tech-stack-decisions.md` |

## 9. B3 で確かめること

| 確かめ | 方法 | 成り立たないとき |
|---|---|---|
| (i) 移行の番号が、その時点の次の空き番号で、既存の移行と重ならない | 段 6 の起動（Flyway）とコード生成のレビュー | 番号を直す |
| (ii) 2節の段 5・6 のテストがすべて通る（上限切れ・違反の読み替え・漏えいを含む） | `verify` | 原因を直す。上限やテストを緩めない |
| (iii) カバレッジの実測の値と、`verify` の時間の延び | 4節・6節 | 除外を増やさず、テストを足す |
| (iv) k6 の場面が `k6 inspect` で読め、`setupTimeout` と閾値の式が意図どおり | 5.1 | 台本を直す |
| (v) E2E の全体 | 7節 | 統合しない。原因を直す |
| (vi) `application.yaml`（`org.hibernate.orm.jdbc.error: OFF`）・依存・`packagesJudgedByTotal`・`docker/monitoring/`・compose が変わっていない | コード生成のレビュー | 元に戻す |

## 10. 上流との差

- CI と検査の範囲では、承認済みの NFR 設計と違う作りはありません。
- 承認済みの `performance-design.md` 2.1 は、データの用意を `setup()` と書いています。この段では、利用者だけは `perf/README.md` の SQL の手順を広げて入れる形にしました。利用者を作る API が無い（招待とメールの流れになる）ためで、グループとメンバーは承認済みのとおり `setup()` で作ります。
- 監視の差（既存の p95 の警報がグループの API を拾わない）は、`monitoring-design.md` 7節に書きました。
