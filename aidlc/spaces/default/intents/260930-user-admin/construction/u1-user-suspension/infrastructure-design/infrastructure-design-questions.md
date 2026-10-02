# Infrastructure Design の質問 — u1-user-suspension（利用停止の状態と3つの入口、library）

U1 は、既存の `user`・`auth`・`access`・`audit` に手を入れ、`auth.service` に部品を1つ足す library の単位です。library の単位のため、この段の成果物は `cicd-pipeline.md`・`traceability.json` だけです（`infrastructure-specification.md`・`monitoring-design.md` は service・ui・packaging の単位だけ。段の定義 `.claude/aidlc-common/stages/construction/infrastructure-design.md` の `produces_kinds`）。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。

U1 の基盤に関わる点は、承認済みの NFR 要件・NFR 設計（とくに承認の場の決定）と、既にある仕組みでほぼ決まっていました。新しく決める論点が無いため、**この単位では質問を作りません**（`project.md` の Way of Working「Construction の設計の段で、単位に新しく決める論点が無いときは、質問を作らず、設計の要点を要約として依頼者に確認する」）。下の「決まっていること」と「設計の要点（案）」を、要約として確かめていただきます。

読んだ上流と既にある仕組み:

- この単位の承認済みの NFR 設計 `aidlc/spaces/default/intents/260930-user-admin/construction/u1-user-suspension/nfr-design/`（`security-design.md` の1〜12節と末尾の「承認の場の決定（Request Changes、2026-10-02）」、`logical-components.md` の1〜8節、`traceability.json`、`nfr-design-questions.md` の Q1 B・Q2 A・Q3 A・Q4 A）。NFR Design の最後の承認（2026-10-01、`Approve`）では、U1 の基盤に関わる新しい決定は無い
- この単位の承認済みの NFR 要件 `nfr-requirements/`（`security-requirements.md` の NFR5.5・NFR9.4〜NFR9.6・NFR10.1〜NFR10.3 と末尾の「承認の場の決定（Request Changes、2026-10-01）」、`tech-stack-decisions.md`）
- この単位の承認済みの機能設計 `functional-design/functional-spec.md`（6節「この単位の作業」・7節「後の段へ渡すこと」）
- `aidlc/spaces/default/intents/260930-user-admin/inception/domain-design/components.md`、`inception/contract-design/contract-summary.md`（C1・C7）、`inception/delivery-planning/bolt-plan.md`（B1 と、すべての Bolt に共通の完了の条件）
- 既にある仕組み（読むだけ）: `.github/workflows/ci.yml`、`build.gradle.kts`（verify の段 0〜9）、`backend/build.gradle.kts`（`integrationTest`・`packagesJudgedByTotal`・`jacocoTestCoverageVerification`）、`.idea/.gitignore`、`.gitignore`、`README.md`（247〜255 行の戻しの注意、736〜742 行のスキーマの変更）、`docker/monitoring/provisioning/alerting/mastersmith.yaml`
- 前の Intent の手本: `aidlc/spaces/default/intents/260925-user-management/construction/u1-mail/infrastructure-design/`（library の単位の形）

## 決まっていること（質問にしない点）

| 点 | 決まっている中身 | 根拠 |
|---|---|---|
| 成果物の範囲 | `cicd-pipeline.md` と `traceability.json` だけ。U1 は API・独自の配備・新しい環境変数を持たないため、配備の流れ（Dockerfile・`compose.yaml`・`.env.example`・イメージ）は変えない | 段の定義の `produces_kinds`、`security-design.md` 8.4 |
| CI | `.github/workflows/ci.yml` は変えない。同じ `./gradlew verify` を呼び、秘密を使わない。新しい道具・依存・サブモジュールは無い | `tech-stack-decisions.md`（新しい依存なし）、`team.md` の Deployment |
| verify の段 | 段は増やさない。U1 のテストは段 5（単体）と段 6（結合、組み込みの H2）に入り、コンテナを使わないため、コンテナの実行環境が無いときも飛ばさない | `team.md` の Testing Posture（内部DB は組み込みの H2）、`build.gradle.kts` |
| V7・V8 の移行のテストの削除 | `user/repository/V7MigrationIT.java`・`V7BackwardCompatibilityIT.java`、`invitation/repository/V8MigrationIT.java`・`V8BackwardCompatibilityIT.java`、`backend/src/test/resources/db/migration-through-v6`・`migration-through-v7` を B1 の最初の手順で消す。複写の置き場を参照しているのは消すテスト2つだけで、ビルドの設定（`build.gradle.kts`・`backend/build.gradle.kts`）・`.gitleaks.toml`・SpotBugs の除外の設定からは参照されていない（読み取りで確かめた）。段 6 は短くなる方向で、CI の制限時間 60 分への影響は無い | `security-design.md` 8.3、Q2 A・Q3 A |
| 消した後のカバレッジ | 消すのはテストだけで `src/main` は変わらないため、`packagesJudgedByTotal` に関わらない。一覧の外の `invitation.*`・`user.repository` がパッケージごとの下限を満たし続けることを、B1 で実測して記録する | `security-design.md` 8.3、`logical-components.md` 7節 |
| 待ちの確かめの手伝い | `V8MigrationIT` の `INFORMATION_SCHEMA.SESSIONS` を見る手伝いを `backend/src/test/java/cherry/mastersmith/auth/testsupport` へ移し（消す前に移す）、絞り込み・接続の URL・上限の時間を引数にする。`LoginAttemptStateRepositoryIT` の新しいテストで使い、`lockDummyForUpdate` の上限 3 秒より前に確かめを終える。`sleep` に頼らない。テストのソースだけの変更で、基盤の設定は変わらない | `security-design.md` 8.3・9.2（承認の場の決定 R-04） |
| V9 の確かめ | V9 の移行のテスト・後方互換の自動のテスト・戻しの練習は置かない。確かめは起動時の Flyway の `validate-on-migrate` と Hibernate の `validate`、エンティティの読み書きだけ。事後の裏付けは deployment-execution のスモークテスト（既存の初期管理者のログインと利用者の読み取り） | `security-design.md` 8.2・8.4・11節 R4・12節 S-1・S-2・S-4・S-9（Q1 B・Q4 A、承認の場の決定 R-02） |
| 戻しの手順 | 戻しの練習は行わないため、この段から deployment-pipeline に渡す練習の手順は無い。戻しの手順そのもの（イメージだけか、内部DB の写しを使うか、戻す前に停止中の利用者を確かめる手順）と、`README.md` の戻しの節（247〜255 行）への V9 の注意の追記は deployment-pipeline の持ち物 | `functional-spec.md` 7節、`security-design.md` 8.4・12節 S-4 |
| `README.md` の 741〜742 行 | V7・V8 の行の「(1) 自動の結合テスト（`V7MigrationIT`・`V7BackwardCompatibilityIT`／`V8MigrationIT`・`V8BackwardCompatibilityIT`）」を、Intent 260930-user-admin の B1 で消したことと理由（依頼者の決定）に置き換える。V9 の行を足し、移行のテストを作らないことと、1つ前の版に戻している間は停止が効かないことを書く | `security-design.md` 8.3 の表の最後の行、NFR10.3 |
| `.idea/.gitignore` | `/dataSources.xml` の1行を足す（`/dataSources/` と `/dataSources.local.xml` はすでにある）。`dataSources.xml` は今は Git の管理に入っていない。ルートの `.gitignore` は変えない | `functional-spec.md` 6節、`bolt-plan.md` の B1、UQ2 A |
| `AuthenticationEvent` の伏せ字 | `auth.domain` の `AuthenticationEvent` に `toString` の上書きを足し、`enteredEmail` を `***` にする。確かめの TRACE は漏えいのテストの設定の中だけで有効にし、アプリの既定のログのレベル（`application.yaml`）は変えない | `security-design.md` 5.1（承認の場の決定 R-01） |
| `packagesJudgedByTotal` | `auth.domain`・`auth.repository`・`access.domain` を一覧から外す見込み（`AuthenticationEvent` も `auth.domain` の中のため、外す対象は変わらない）。`access.service` は手を入れず残る。外す対象は生成の最後に、実際に `src/main` を変えたパッケージと突き合わせて決め、`:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測して記録する。一覧を増やさず、計測の除外を増やさない | `team.md` の Testing Posture、`tech-stack-decisions.md` の NFR9.5、`security-design.md` 9.2 |
| 静的解析・依存 | 足す問い合わせは名前つきの引数の JPQL だけで、SpotBugs ＋ FindSecBugs の関門を除外を足さずに通す。lockfile・OSV-Scanner・Gitleaks・Dependabot の設定は変わらない | `security-design.md` 9.3、NFR9.4 |
| 監視 | 新しい指標・警報・ダッシュボードの行を足さない。`docker/monitoring/provisioning/alerting/mastersmith.yaml` が変わらないことをレビューで確かめる | NFR5.5、`security-design.md` 7節 |
| E2E | U1 は E2E の流れを足さない。B1 は認証に関わるため、統合の前に `./gradlew e2eTest` を手元で流す（verify と CI の外のまま） | `bolt-plan.md` の共通の完了の条件、`team.md` の Testing Posture |
| 統合 | B1 は `develop` への squash の1コミット。件名と計画に「移行のテストの片付けを含む」と書く。サブモジュールの更新は含まない | `team.md` の Way of Working、`security-design.md` 8.3 |

## 質問

この単位で判断の分かれる論点は無いため、質問は 0 問です。

## 要約として確かめる設計の要点

1. 基盤の変更は無い。V9 は起動時に Flyway がコンテナの中の内部DB（H2 のファイル）に当て、`compose.yaml`・`.env`・Dockerfile・イメージの設定は変えない。
2. CI（`ci.yml`）と `./gradlew verify` の段は変えない。U1 のテストは段 5・段 6 に入り、コンテナが無くても飛ばない。
3. B1 の最初の手順で、待ちの確かめの手伝いを `auth/testsupport` に移してから、V7・V8 の移行と後方互換のテスト4つと複写の置き場2つを消す。どれもビルドの設定から参照されていない。消した後も `invitation.*`・`user.repository` がパッケージごとの下限を満たすことを実測する。
4. V9 は移行のテスト・戻しの練習を置かず、起動時の検証とエンティティの読み書きで確かめる。事後の裏付けと戻しの手順（戻す前に停止中の利用者を確かめる、`README.md` の戻しの節の V9 の注意）は deployment-pipeline・deployment-execution に引き継ぐ。
5. `README.md` の 741〜742 行は、消したテストの名前を「B1 で消した（依頼者の決定）」に置き換え、V9 の行（移行のテストを作らない、戻している間は停止が効かない）を足す。
6. `.idea/.gitignore` に `/dataSources.xml` を足す。
7. `AuthenticationEvent` の伏せ字は `auth.domain` の変更で、TRACE はテストの設定の中だけで有効にする。アプリの既定のログのレベルは変えない。
8. `packagesJudgedByTotal` から、実際に `src/main` を変えた一覧のパッケージ（見込みは `auth.domain`・`auth.repository`・`access.domain`）を外し、clean を付けた verify で実測して記録する。
9. 新しい依存・指標・警報は無い。B1 は統合の前に E2E を手元で流し、`develop` へ squash の1コミットで統合する。
10. `traceability.json` は、基盤に関わる枝番（NFR3.1・NFR5.5・NFR9.4〜NFR9.6・NFR10.1〜NFR10.3）を `cicd-pipeline.md` の節と設定・タスク・ファイルに対応づける。NFR10.1・NFR10.2 は NFR 設計と同じく `Deferred`（依頼者の決定）とする。

## Consolidated Summary Confirmation

質問はありません。上の「決まっていること」と要点 1〜10 のとおりに成果物（`cicd-pipeline.md`・`traceability.json`）を作ってよいかを確かめます。

- Looks correct
- Request changes

[Answer]: Looks correct
