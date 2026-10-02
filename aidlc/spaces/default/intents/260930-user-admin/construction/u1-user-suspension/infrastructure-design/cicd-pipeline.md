# CI/CD Pipeline — U1 利用停止の状態と3つの入口（u1-user-suspension）

U1 の検査の流れ（CI と1コマンドの検査）、B1 の最初の手順で行う移行のテストの片付け、V9 の確かめ方、B1 の統合の前に確かめることを示します。U1 は library の単位で、既存の `user`・`auth`・`access`・`audit` に手を入れ、`auth.service` に部品を1つ足します。API・独自の配備・新しい環境変数を持たないため、配備の流れ（Dockerfile・`compose.yaml`・`.env.example`・イメージ）は変えません。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。

この文書の中身は新しい設計ではなく、既にある仕組みの上で U1 が何を足し・何を消すかの記録です。

- 答え: `infrastructure-design-questions.md`（質問 0 問、「決まっていること」の表と設計の要点 1〜10、まとめの確認は Looks correct）
- 上流: `construction/u1-user-suspension/nfr-design/security-design.md`（とくに 5.1・7節・8節・9節・11節・12節と「承認の場の決定（Request Changes、2026-10-02）」）・`logical-components.md`、`construction/u1-user-suspension/nfr-requirements/security-requirements.md`・`tech-stack-decisions.md`、`construction/u1-user-suspension/functional-design/functional-spec.md`（6節・7節）、`inception/domain-design/components.md`、`inception/contract-design/contract-summary.md`（C1・C7）、`inception/delivery-planning/bolt-plan.md`（B1 と共通の完了の条件）（どれも `aidlc/spaces/default/intents/260930-user-admin/` の下）
- 既にある仕組み（正とする）: `.github/workflows/ci.yml`・`build.gradle.kts`（`verifyStages` の段 0〜9、`e2eTest`）・`backend/build.gradle.kts`（`integrationTest`・`packagesJudgedByTotal`・`jacocoTestCoverageVerification`・`spotbugsGate`）・`.idea/.gitignore`・`README.md`・`docker/monitoring/provisioning/alerting/mastersmith.yaml`

## 1. CI（GitHub Actions）

`.github/workflows/ci.yml` は変えません。

| 項目 | 今の形 | U1 での扱い |
|---|---|---|
| きっかけ | `develop` へのプッシュ・`v*` のタグ・手動 | 変えない |
| サブモジュール | `actions/checkout` の `submodules: true` で固定先のコミットを取得 | 変えない。B1 はサブモジュールの固定先を更新しない |
| 実行 | `./gradlew verify`（`ubuntu-latest`、制限時間 60 分） | 変えない。U1 のテストは段 5・段 6 に入る（2節） |
| 秘密 | CI は秘密を使わない | 変えない。U1 は新しい秘密・環境変数を持たない |
| 道具 | Gitleaks・OSV-Scanner を版と SHA-256 で固定して入れる | 変えない。新しい道具・依存は無い（`tech-stack-decisions.md`） |
| 成果物 | WAR をコミットのハッシュの名前で保存 | 変えない |

V7・V8 の移行と後方互換のテスト4つを消すため（4節）、段 6 の時間は短くなる方向で、制限時間 60 分への影響はありません。`verify` の時間は Build and Test で測ります。

## 2. 1コマンドの検査（`./gradlew verify`）の段と関門

段は増やしません。U1 のテストは既存の段に入ります。どれか1つでも失敗したら全体を失敗とします（既存のとおり）。

| 段 | U1 で入るもの | 関門（失敗の条件） | 場所 |
|---|---|---|---|
| 0 準備 | 変えない | 既存のとおり | `build.gradle.kts` |
| 1 フォーマット | U1 の Java のソースとテスト（palantir-java-format、ライセンスヘッダー） | 形が違う・ヘッダーが無い | `backend/build.gradle.kts` の Spotless |
| 4 ビルド | `V9__u1_user_suspension.sql`、`User` の `suspended`、`RefreshTokenRevocationService`、`AuthenticationEvent` の `toString` など | コンパイルの失敗 | `backend/src/main/` |
| 5 単体テスト | 停止の区分を尽くす変換、`AuthenticationEvent` の `toString` が `enteredEmail` を伏せること、`CountingPasswordEncoder`・`SqlStatementCounter` で回数をそろえる確かめ | 1件でも失敗 | `backend/src/test/java/cherry/mastersmith/auth/`・`access/`・`user/` |
| 6 結合テスト | 3つの入口の停止中の拒否と解いた直後の受け付け、止める前のトークンの扱い、まとめての無効化、MANDATORY の口、一括代入の防止、TRACE を有効にした漏えいのテスト、`LoginAttemptStateRepositoryIT` の新しいテスト、`InvitationSchemaIT` に移す確かめ | 1件でも失敗。組み込みの H2 だけを使いコンテナを使わないため、コンテナの実行環境が無いときも飛ばさない | 同上と `invitation/repository/` |
| 7 カバレッジ | 一覧から外すパッケージ（5節）が単独で行 80%・分岐 70% を満たす。一覧の外の `user.*`・`auth.service`・`invitation.*` も引き続き満たす | 全体またはパッケージごとの下限を下回る。除外は足さない | `backend/build.gradle.kts` の JaCoCo |
| 8 安全の検査 | 足す問い合わせ（停止の列だけの更新、まとめての無効化）は名前つきの引数の JPQL だけ | SpotBugs ＋ FindSecBugs の priority 1、`SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` は priority にかかわらず失敗。除外の設定（`backend/config/spotbugs-exclude.xml`）は足さない。OSV-Scanner・Gitleaks は既存の基準のまま | `backend/build.gradle.kts`・`build.gradle.kts` |
| 9 成果物 | 変えない | 既存のとおり | 既存 |

補足:

- 段 2（リンタ）・段 3（画面のライセンスヘッダー）は、U1 が画面を変えないため対象がありません。
- 漏えいのテストで使う TRACE のログは、テストの設定の中だけで有効にします。アプリの既定のログのレベル（`backend/src/main/resources/application.yaml`）は変えません（`security-design.md` 5.1）。
- 時刻に依存する確かめは `auth/testsupport` の `MutableClock` で動かし、`sleep` と実時刻に頼りません。

## 3. 統合の前の関門と E2E

B1 の完了の条件（`bolt-plan.md` の共通の完了の条件）を、次の順に満たしてから統合します。

1. 作業ブランチ（例 `feature/260930-user-admin-b1`）で `./gradlew verify` を通す。コンテナの実行環境（colima）が動いている状態で流す（U1 のテストは使わないが、対象DB の結合テストが同じ段にあるため。`team.md` の Way of Working）。
2. カバレッジの値は `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、記録する（5節）。
3. B1 は認証に関わるため、統合の前に `./gradlew e2eTest` を手元で流す。`e2eTest` は始める前に Mailpit（`http://127.0.0.1:8025/api/v1/info`）に届くかを確かめるため、先に `docker compose --profile mail up -d mailpit` で起動しておく。U1 は E2E の流れを足さない（停止中の利用者の画面の流れは U5 の E2E）。E2E は verify と CI の外のまま。
4. `develop` へ squash の1コミットで統合する。件名と計画に「移行のテストの片付けを含む」と書く。サブモジュールの更新は含まない。
5. `origin` へのプッシュは依頼者が行い、その後の CI が通ることを確かめる。CI が失敗したら `team.md` の「不安定なテストと CI の失敗」の決まりで扱う。

## 4. 移行のテストの片付け（B1 の最初の手順）

V9 を足す前の最初の手順として、次の順に行います（`security-design.md` 8.3、Q2 A・Q3 A）。手順を逆にすると、移す前に手伝いが消えます。

| 順 | 作業 | 基盤への影響 |
|---|---|---|
| 1 | `invitation/repository/V8MigrationIT.java` の待ちの確かめの手伝い（`INFORMATION_SCHEMA.SESSIONS` を見る）を `backend/src/test/java/cherry/mastersmith/auth/testsupport` へ移し、絞り込み（文の表の名前）・接続の URL・上限の時間を引数にする | テストのソースだけ。ビルドの設定は変わらない |
| 2 | `LoginAttemptStateRepositoryIT` に、ダミーの行8つを排他した状態で `lockDummyForUpdate` を呼ぶテストを足し、移した手伝いで待ちに入ったことを `lockDummyForUpdate` の上限 3 秒より前に確かめる | 段 6 に入る |
| 3 | `InvitationSchemaIT` に `V8MigrationIT` の (b) を1件足し、`stateChangesAndUniqueness` に SQLState 23505 と制約の名前 `UK_INVITATIONS_PENDING_EMAIL` の確かめを足す（R-05） | 段 6 に入る |
| 4 | `user/repository/V7MigrationIT.java`・`V7BackwardCompatibilityIT.java`、`invitation/repository/V8MigrationIT.java`・`V8BackwardCompatibilityIT.java` を消す | 段 6 が短くなる |
| 5 | `backend/src/test/resources/db/migration-through-v6`・`migration-through-v7` を消す | 消すテストだけが使う複写 |

参照の確かめ（読み取りで確かめた）:

| 置き場 | 消すテスト・複写の置き場の参照 |
|---|---|
| `build.gradle.kts`・`backend/build.gradle.kts` | 無い（`integrationTest` は `*IT` の名前で拾うだけ） |
| `.gitleaks.toml`・`backend/config/spotbugs-exclude.xml` | 無い |
| `README.md` | 有る（7節で直す） |
| ほかのテスト | 消す4つのテストの中だけ |

消すのはテストだけで `src/main` は変わらないため、`packagesJudgedByTotal` には関わりません。一覧の外の `invitation.*`・`user.repository` が、消した後もパッケージごとの下限を満たすことを B1 の実測で確かめて記録します。

## 5. カバレッジの一覧（`packagesJudgedByTotal`）

`team.md` の Testing Posture のとおり、手を入れた一覧のパッケージはテストを足して下限を満たし、一覧から外します。

| パッケージ | 手を入れるクラス | 扱い |
|---|---|---|
| `cherry.mastersmith.auth.domain` | `LoginFailureReason`・`TokenFailureReason`・`AuthenticationEvent` | 一覧から外す見込み |
| `cherry.mastersmith.auth.repository` | `RefreshTokenRepository`（足りない分岐は `LoginAttemptStateRepository#lockDummyForUpdate` で、4節の順 2 のテストで通す） | 一覧から外す見込み |
| `cherry.mastersmith.access.domain` | `AccessDeniedReason` | 一覧から外す見込み |
| `cherry.mastersmith.access.service` | 手を入れない | 一覧に残る |

- 外す対象は生成の最後に、実際に `src/main` を変えたパッケージと突き合わせて決めます（「手を入れる」には説明文だけの直しも含む）。
- 値は clean を付けた `./gradlew verify` で実測し、コード生成の成果物に記録します。
- 一覧を増やさず、計測の除外（`coverageExclusions`）も増やしません。

## 6. V9 の確かめと戻し

| 項目 | 扱い | 出典 |
|---|---|---|
| V9 の当て方 | 起動時に Flyway がコンテナの中の内部DB（H2 のファイル）に当てる。`compose.yaml`・`.env`・Dockerfile・イメージの設定は変えない | `security-design.md` 8.4 |
| 確かめ | 起動時の Flyway の `validate-on-migrate` と Hibernate の `ddl-auto: validate`、エンティティでの停止の状態の読み書きだけ。移行のテスト・後方互換の自動のテスト・戻しの練習は置かない | `security-design.md` 8.2（Q1 B・Q4 A、承認の場の決定 R-02） |
| 既存の行が false になること | テストでは確かめず、V9 の1文（`ALTER TABLE users ADD COLUMN suspended BOOLEAN DEFAULT FALSE NOT NULL`）をコードのレビューで確かめる | `security-design.md` 8.1・8.2 |
| 事後の裏付け | deployment-execution のスモークテストで、既存の初期管理者でログインでき、利用者の読み取り（`/api/me` など）が通ることを記録する。手順は deployment-pipeline の手順書に書く | `security-design.md` 8.2・11節 R4 |
| 戻しの練習 | 行わない。この段から deployment-pipeline に渡す練習の手順は無い | `security-design.md` 8.2・12節 S-4 |
| 戻しの手順 | deployment-pipeline の持ち物。イメージだけで戻すか内部DB の写しを使うか、戻す前に停止中の利用者を確かめる手順、`README.md` の戻しの節（247〜255 行の V7・V8 の注意の並び）への V9 の注意の追記を、そこで決めて書く | `functional-spec.md` 7節、NFR10.3 |
| 戻している間 | 1つ前の版では停止が効かない（停止中の利用者もログインで新しいトークンを取れる。止めたときに無効にしたリフレッシュトークンは無効のまま）。受け入れた制約 | `security-design.md` 11節 R2 |

## 7. README とリポジトリの設定

| 対象 | 変更 | 出典 |
|---|---|---|
| `README.md` の「スキーマの変更（Flyway）」の V7・V8 の行（741〜742 行） | 「(1) 自動の結合テスト（`V7MigrationIT`・`V7BackwardCompatibilityIT`／`V8MigrationIT`・`V8BackwardCompatibilityIT`）」を、Intent 260930-user-admin の B1 で消したことと理由（依頼者の決定）に置き換える | `security-design.md` 8.3 |
| 同じ節に V9 の行を足す | `V9__u1_user_suspension.sql` が `users.suspended` を足す前進のみの変更であること、移行のテストを作らないこと、1つ前の版に戻している間は停止が効かないこと | NFR10.3 |
| `.idea/.gitignore` | `/dataSources.xml` の1行を足す（`/dataSources/` と `/dataSources.local.xml` はすでにある）。`dataSources.xml` は今は Git の管理に入っていない。ルートの `.gitignore` は変えない | `functional-spec.md` 6節、`bolt-plan.md` の B1、UQ2 A |
| `application.yaml` | 変えない（TRACE はテストの設定の中だけ） | `security-design.md` 5.1 |
| `.env.example`・`compose.yaml`・Dockerfile | 変えない | `security-design.md` 8.4 |

`.idea/dataSources.xml` には手元の DB の接続先が入りうるため、管理外にしておくことは公開のリポジトリに接続の情報を載せない守りになります（Gitleaks の pre-commit・verify・CI の守りに加える）。

## 8. 依存・静的解析・秘密

| 項目 | 扱い |
|---|---|
| 依存 | 新しい依存は無い。`backend/gradle.lockfile`・`frontend/package-lock.json` は変わらない見込み。変わったときは lockfile の差をコード生成の成果物に記録する |
| OSV-Scanner | 既存の基準（重大度 High 以上で失敗）のまま。検査の対象の lockfile の一覧は変えない |
| Gitleaks | 既存のまま。`.gitleaks.toml` に例外を足さない。テストデータのメールアドレスは `example.com` などの予約のドメインだけ |
| SpotBugs ＋ FindSecBugs | 2節の段 8。除外を足さない。既存の `PREDICTABLE_RANDOM` の除外（`lockDummyForUpdate`）はそのまま |
| Dependabot | `.github/dependabot.yml` は変えない |
| 秘密 | U1 は新しい秘密を持たない。CI に秘密を渡さない |

## 9. 監視

| 項目 | 扱い | 出典 |
|---|---|---|
| 指標・警報・ダッシュボード | 新しく足さない。停止中のログインと更新は既存の HTTP の指標と `ms-login-p95`・`ms-refresh-p95` に含まれ、停止中のログインの理由（`ACCOUNT_SUSPENDED`）は監査ログで追える | NFR5.5、`security-design.md` 7節 |
| 警報の決まり | `docker/monitoring/provisioning/alerting/mastersmith.yaml` が変わらないことを、コード生成のレビューで確かめる | NFR5.5 |
| 性能 | まとめての無効化の時間は U3 の止める操作の p95 1 秒に含め、performance-validation が悪い側の条件（未無効 100 件・無効 1,000 件）で測る。U1 の段で負荷の環境は作らない | NFR5.2 |

## 10. B1 で確かめること

| 確かめ | 成り立たないとき | 出典 |
|---|---|---|
| 4節の順（手伝いを移してから消す）と、消した後に段 6 が通ること | 移し漏れを直してからやり直す | `security-design.md` 8.3 |
| 一覧の外の `invitation.*`・`user.repository` が、消した後もパッケージごとの下限を満たす | 消したテストが担っていた分をテストで足す（除外は足さない） | `security-design.md` 8.3 |
| 一覧から外すパッケージ（5節）の下限と実測の値の記録 | テストを足す。外す対象を実際の変更と突き合わせ直す | NFR9.5 |
| SpotBugs の関門を除外を足さずに通る | 問い合わせを名前つきの引数に直す | NFR9.4 |
| `mastersmith.yaml` が変わっていない | 変更を取り消す | NFR5.5 |
| README の V7・V8・V9 の行（7節）と `.idea/.gitignore` | B1 を完了としない | NFR10.3、`functional-spec.md` 6節 |
| 統合の前の E2E（3節の 3） | 失敗の原因を直してから統合する | `bolt-plan.md` |

## 11. 上流との差

承認済みの文書は書き換えず、差をここに記録します（`project.md` の決まり）。この段で新しく決めたことは無く、差はどれも NFR 設計の段の決定を受け継いだものです。

| 対象 | 承認済みの形 | この段の設計 | 理由 |
|---|---|---|---|
| NFR 要件の承認の場の申し送り（NFR10.2） | 戻しの練習（1つ前の版のイメージを V9 の後の内部DB で起動する手順）は infrastructure-design で決める | 手順を作らない（6節） | NFR 設計の Q4 A と承認の場の決定 R-02 で戻しの練習を行わないと決めたため（`security-design.md` 12節 S-4）。`traceability.json` の NFR10.2 は NFR 設計と同じく `Deferred` |
| `functional-spec.md` 7節 | 「V9 の後方互換の確かめ方」の持ち主は nfr-design・infrastructure-design | NFR 設計で「確かめない」と閉じたものを受け、この段も手順を置かない | `security-design.md` 12節 S-6 |
| `team.md` の Deployment（1つ前の版が動く後方互換） | 保つ | 設計の上では保つ見込みだが、確かめを置かない | `security-design.md` 12節 S-9、11節 R4。事後の裏付けは deployment-execution のスモークテスト |

## 承認の場の決定（Request Changes、2026-10-02）

依頼者は、この段の承認の場で Request Changes を選んだ。U1 の文書の中身は直さず、次の扱いを記録する。

| 指摘 | 扱い | 中身 |
|---|---|---|
| R-03（V9 の後方互換と戻しの練習を置かない決定は、`team.md` の Deployment より確かめの水準が下がる） | 受け入れ（条件つき） | 1つ前の版（この Intent の前の版のイメージ）は V9 の停止の列を知らないため、戻すと起動はするが、停止中の利用者がログインの照合・トークンの更新・アクセストークンの認証の3つの入口を通れる（`project.md` の Mandated の「利用者の状態の判定はサーバー側で行う」が戻している間は効かない）。受け入れの条件として、戻す前に停止中の利用者がいるかを確かめ、いれば扱いを依頼者に確かめる手順を、deployment-pipeline の段で必ず決める（U3 の基盤の設計のレビュー R-01 と同じ条件） |
| R-01（`LoginCommand`・`AuthenticatedUser`・`CurrentUserResponse` の伏せ字のテストと、`auth.service`・`auth.web` のパッケージごとの下限） | B1 のコード生成の計画へ申し送る | NFR 設計の承認の場で B1 に加わった直しのテストと、`packagesJudgedByTotal` の一覧に無いパッケージの下限の扱いを計画に書く |
| R-02（待ちの確かめの手伝いが `invitation/testsupport` の `TestInvitationBarrier` にもある） | B1 のコード生成の計画へ申し送る | 移した後に3か所にならないよう、1つにまとめられるかを計画で確かめる |
| E2E の報告の扱い（U5 の基盤の設計の Q1 A・レビュー R-02 の決定） | 決定に従う | B1 の統合の前の `./gradlew e2eTest` の後も、json の報告から結果を記録してから `frontend/playwright-report/` と `frontend/test-results/` を消し、消したことと共有していないことを B1 のコード生成の記録に書く。いま手元に残っている報告は B1 の始めに消す |
