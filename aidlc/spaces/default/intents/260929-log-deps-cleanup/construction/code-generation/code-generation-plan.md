# コード生成の計画（Intent 260929-log-deps-cleanup）

- scope: bugfix、深さ: Minimal、Test Strategy: Minimal、単位の分割なし（単位なしで1回だけ実行する）。
- 計画を書いた時点の `develop` の HEAD: `3c38081`（Requirements Analysis の承認のコミット）。
- 入力: `aidlc/spaces/default/intents/260929-log-deps-cleanup/inception/requirements-analysis/requirements.md`（FR1〜FR6・NFR1〜NFR4）、同じディレクトリの `requirements-analysis-questions.md`、要件のレビューの記録（R-01〜R-06）、`aidlc/spaces/default/intents/260929-log-deps-cleanup/inception/reverse-engineering/developer-scan.md`、`aidlc/spaces/default/codekb/mastersmith2/`（K-10〜K-16）。
- 機能設計・NFR の設計の段は無い（bugfix）。要件とコードの調査から直接計画する。
- テストの流し方は `unit-test-instructions.md`（同じディレクトリ）に書く。

## 1. 要件との差（承認済みの要件の文書は書き換えない）

### 1.1 FR2.1・FR2.2 は行わない（依頼者の決定、2026-09-29）

- 要件のレビューの指摘 R-01（監査の書き込みの失敗の ERROR を伏せ字にすると、後から手で監査の記録を補う手がかりが失われる）を受けて、依頼者は「監査の記録の失敗の ERROR の `enteredEmail` は伏せ字にしない」と決めた。
- そのため、この Intent では FR2.1（監査の失敗の ERROR の `enteredEmail` を伏せ字にする）と FR2.2（README 774 行の書き直し）を行わない。`backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java` と README 774 行は変更しない。監査の失敗の ERROR に関するテストも足さない（既存の `AuditEventListenerTest`・`AuditWriteFailureIT` の「メールアドレスを含むこと」の確かめはそのまま残る）。
- 監査の失敗の ERROR は、前の Intent（260925-user-management の U4）で決めた例外として今のまま残る（Q2: A、F2: A と同じ扱い）。`project.md` の Forbidden「NEVER メールアドレスをアプリのログとエラー応答に含めず…」との差は、**既知の例外として残る**（標準出力のログにはメールアドレスそのものが載る。外部エクスポートでは `SanitizingLogRecordExporter` がキー `enteredEmail` の値を `[REDACTED]` に置き換える）。
- traceability の予定（8節）でも、FR2.1・FR2.2 は実装しない扱い（`N/A`、理由は上の決定）にする。
- 要件の NFR1 の「監査の記録の失敗の経路でメールアドレスそのものが出ないこと」も、同じ決定により確かめの対象から外す（初期管理者の作成の経路だけを確かめる）。

### 1.2 要件の承認の場で受け入れたリスク（R-02〜R-06）の扱い

| 指摘 | この計画での扱い |
|---|---|
| R-02（Jackson をすべての版で ignore にすると、team.md の Dependabot の決まりとの差が出る） | `.github/dependabot.yml` のコメントに、Dependabot の知らせを受けない代わりに OSV-Scanner の関門で気づくこと、関門が働くのは `./gradlew verify` と CI の実行のときだけであること、team.md の「High 以上の知らせは次の Bolt の前に取り込む」との差であることを書く（Step 16）。team.md には足さない（FR6.2）。 |
| R-03（ローカル部が1文字のとき、先頭の1文字で全部見える） | FR1.2 の形のまま（`a***@example.com`）とし、境界のテストで今の形を明示して固定する（Step 6）。依頼者の答え Q-A: A（6節）で確定。 |
| R-04（300 が残らないことの確かめ方） | 警報の決まりの中の summary の文言としきい値の2か所を含む5か所（8節の FR5.2 の表）を直し、`docker/monitoring` の全体と README の監視・警報の節で `300` を検索して、残りがバケット・目標と無関係のもの（例 ポート `3000`）だけであることを合格の条件にする（Step 18）。 |
| R-05（伏せ字の処理の置き場） | 1.1 の決定で使うのが `user` だけになったため、`common` ではなく `cherry.mastersmith.user.domain.EmailAddress` の純粋な関数 `mask(String)` に置く。機能の間の依存が増えないため、ArchUnit の境界テスト（`backend/src/test/java/cherry/mastersmith/ArchitectureTest.java` など）は変えない・緩めない。既存の `AdminAccessDeniedEvent`・`AuditEvent` の `toString` の `***`（値のすべてを伏せる。TraceAspect の文字列化の対策）は目的が違うため、そのまま残し形をそろえない。 |
| R-06（FR4・FR6 に合否の判定が無い） | 8節の対応表に、FR4.1〜FR4.5・FR6.1 ごとの「ファイル X の Y が Z になっている」の合格の条件を書く。 |

### 1.3 計画の中で決めたこと（要件の未解決の点と前提）

- 伏せ字の置き場（要件の「未解決の点」の1つ目）: 1.2 の R-05 のとおり `EmailAddress.mask`。
- 伏せ字の形の細部（FR1.2・A1 の具体化）:
  - `@` の位置は最後の `@`（`lastIndexOf`）で分ける。先頭の1文字は Unicode のコードポイントで数える（サロゲートペアを割らない）。
  - 値が null・空 → `null` を返す（呼び出し側はキーを載せない。A1）。`@` を含まない・ローカル部が空 → `***`。それ以外 → 先頭の1文字＋`***`＋`@`＋ドメイン。
  - 初期管理者の3か所では、設定の検査（形式の確かめ）を通った値だけを渡すため、キーは必ず載り、値は常に「先頭の1文字＋`***`＋`@`＋ドメイン」になる。
- ログのキーの名前は `email` から `maskedEmail` に変える（依頼者の答え Q-B: B、6節）。値が伏せ字であることが名前で分かる。外部エクスポートの `SanitizingLogRecordExporter` の `MASKED_KEYS` は変えない（`maskedEmail` を足さない）ため、外部エクスポートでは伏せ字の値がそのまま送られる。README 664 行付近の外部エクスポートの説明と 215 行の起動の確かめの説明を、この扱いに合わせて直す（Step 20）。
- E2E の 100 で `077f5b4` に上げた後に別の違反が新しく出たときは、既知の違反の一覧に足して進め、README の既知の制約に書き、code-summary.md と承認の場で報告する（依頼者の答え Q-E: B、6節）。
- team.md（FR6.1）はこの段では直さず、Build and Test で直す（依頼者の答え Q-C: A、6節。Testing Contract の入力が変わるため）。
- hover の文字が白に替わった後の組ごとのコントラスト（要件の「未解決の点」の2つ目）: E2E 100 の実測で違反が無いことを確かめ、README の表の値は make-you-chic-ui の CSS の値から計算で出す（今の README と同じく「計算の値」と書く。Step 12）。

## 2. 影響の範囲（Blast Radius）

| 変更するファイル | 変更の中身 | 利用元・参照 | 関係するテスト・設定 | 影響 |
|---|---|---|---|---|
| `backend/src/main/java/cherry/mastersmith/user/domain/EmailAddress.java` | 静的な関数 `mask(String)` を足す（既存の `normalize`・`isValid` は変えない） | `InitialAdminInitializer` だけから使う | `EmailAddressTest`（足す）。`user.domain` はパッケージごとのカバレッジの下限の対象 | 小 |
| `backend/src/main/java/cherry/mastersmith/user/service/InitialAdminInitializer.java` | INFO の3か所（88・96・99 行）のキー `email` を、キー `maskedEmail`・値 `EmailAddress.mask(email)` に替える。Javadoc 38〜40 行を書き直す。文言は変えない | Spring の起動（`SmartInitializingSingleton`） | `InitialAdminInitializerTest`・`InitialAdminIT`（直す）。README 215 行の起動の確かめ。`user.service` はパッケージごとの下限の対象 | 小 |
| `backend/src/test/java/cherry/mastersmith/user/domain/EmailAddressTest.java` | `mask` の例のテストと性質ベースのテストを足す | ― | `:backend:test` | 小 |
| `backend/src/test/java/cherry/mastersmith/user/service/InitialAdminInitializerTest.java` | `creates()` の「含まれること」を逆にし、キー `maskedEmail` に伏せ字が載りキー `email` が無いことを確かめる。`existing()`・`duplicate()` にも同じ確かめを足す | ― | `:backend:test` | 小 |
| `backend/src/test/java/cherry/mastersmith/user/service/InitialAdminIT.java` | `createsOnce` に、標準出力にメールアドレスそのものが無く伏せ字があることの確かめを足す | ― | `:backend:integrationTest` | 小 |
| `vendor/make-you-chic-ui`（サブモジュールの固定先） | `310e1ecf2fe8b4a388cb8ae098a59144dc93acc5` → `077f5b4`（完全なハッシュは Step 9 で記録）。中身は変えない | `frontend/package.json` の `file:` の依存、`build.gradle.kts` の `vendorInstall`・`vendorBuild`・`vendorUnchanged` | 画面のすべての部品（Tabs・Button の CSS と、テーマの変数2つの追加）。E2E 050〜100、frontend の単体テスト、CI の `submodules: true` | 中（画面の全体の見た目。ただし差分は CSS と contrast.test の4ファイル、依存の変更なし） |
| `frontend/e2e/100-app-text-contrast.e2e.ts` | `STATE_KNOWN_VIOLATIONS` から2件を外して空の配列にし（仕組みは残す。新しい違反が出たときはそれだけを載せる。Q-E: B）、27〜33 行・55〜71 行の注記を直す | E2E だけ | `./gradlew e2eTest`、`tsc`（`frontend/tsconfig.json` の `include` に `e2e`）、prettier・oxlint・eslint | 小 |
| `gradle/libs.versions.toml` | 29 行 `spotless = "8.10.2"` → `"8.10.3"` | `build.gradle.kts` 24 行・`backend/build.gradle.kts` 29 行の `alias(libs.plugins.spotless)` | `./gradlew verify` の 1 の段（書式・ライセンスヘッダー）。lockfile には載らない | 小（書式の判定が変わると、全 Java ファイルに及ぶ。Step 14 で確かめる） |
| `frontend/package.json`・`frontend/package-lock.json` | `@types/node` を 26.6.2 → 26.6.3 | 型の検査（`tsconfig.json` の `types: ["node"]`）、vite・vitest の peer | `npm ci`・`npm run typecheck`・`osvScan` | 小 |
| `.github/dependabot.yml` | npm に `typescript` の semver-major の ignore を足す。gradle の `tools.jackson:jackson-bom` の `update-types` を外してすべての版に。コメントを直す | GitHub の Dependabot | 手元では YAML の読み込みだけ確かめられる | 小 |
| `docker/monitoring/provisioning/alerting/mastersmith.yaml` | `ms-check-p95` の summary（227 行）としきい値（248 行）を 500 に | 手元の監視（lgtm）の読み込み | NFR4 の起動の確かめ | 小 |
| `docker/monitoring/dashboards/mastersmith-overview.json` | SLI の表（83 行）、パネルの説明（368 行）、しきい値の段（395 行）を 500 に | 手元の監視の読み込み | JSON の読み込みの確かめ、NFR4 | 小 |
| `backend/src/main/resources/application.yaml` | 281〜284 行付近の `slo` のコメント（「警報のしきい値 1000 ms を境界に含める」）に 500 ms も境界に含めることを足す。値は変えない | Spring の設定 | `HistogramBucketsIT`（境界の数は変わらないため変更なし） | 小（コメントだけ） |
| `README.md` | 38 行（固定先）、158・957・972〜993 行（既知の違反とコントラストの節）、215 行（起動のログの伏せ字とキー `maskedEmail`）、664 行付近（外部エクスポートでは `maskedEmail` の伏せ字がそのまま送られること）、682・703 行と SLO の記述（500 ms） | 人が読む | ― | 小 |

変えないもの: `AuditEventListener.java`・README 774 行（1.1）、`SanitizingLogRecordExporter.java`（`MASKED_KEYS` に `maskedEmail` を足さない。Q-B）、`aidlc/spaces/default/memory/team.md`（この段では直さず Build and Test で直す。Q-C）、ArchUnit の境界テスト、`backend/build.gradle.kts` の `packagesJudgedByTotal`（`user.*` は既に一覧に無い）、`gradle/libs.versions.toml` の `jackson = "3.1.6"` と `frontend/package.json` の `typescript` の版（FR4.5）、`vendor/java-mustache-processor`、`vendor/make-you-chic-ui` の中身。

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
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる。ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる（既存のパッケージは全体の合計で判定する）。どちらになるかは、パッケージごとの下限を入れる Bolt で既存のパッケージのカバレッジを実測して決め、結果を記録する。\n- 全体の合計で判定している既存のパッケージ（`backend/build.gradle.kts` の `packagesJudgedByTotal` の一覧。`user.domain`・`user.repository`・`user.service` を含む 22 パッケージ）に手を入れる Bolt では、テストを足してそのパッケージの下限（行 80%・分岐 70%）を満たし、一覧から外してパッケージごとの下限の対象に必ず戻す。カバレッジは、その Bolt のテストを足した後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、値を記録する。一度外したパッケージは一覧に戻さず、一覧を増やす変更はしない。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）。Intent `260923-dsl-schema-loader` の Build and Test で `verify` の時間と colima の VM のメモリを実測したうえで、この形に決めた。コンテナの実行環境が無いときの扱いは Way of Working のとおり。\n- 外へ送るメール（SMTP）を使う機能のテストは、送信の部品をモックで置き換えて済ませず、テストの中で JVM の中に起動するテスト用の SMTP の受け手で実際に受け、宛先・件名・本文（HTML）・言語を確かめる。受け手にコンテナは使わない（コンテナの実行環境が無いときに飛ばしてよいテストは、対象DB のテストだけのままとする）。送信の失敗（受け手が接続を拒む・応答しない）のテストも必ず入れる。実在の宛先へは送らない。受け手の具体の道具は、採用の前のライセンスの確認とあわせて設計の段で決める。\n- 画面からの一連の操作を確かめるテスト（E2E）は Playwright（`./gradlew e2eTest`）で書き、`./gradlew verify` と CI の外に置く。本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す。新しく足す流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作る。E2E は、画面・認証に関わる変更を統合する前と、リリースの前に手元で実行する。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。不安定なテストは放置せず、原因を直すまで統合しない。\n- 認証・認可・監査に関わる機能では、次のテストを必ず書く。★印の項目は要件（しきい値や動作）が未確定のため、要件定義で決めてからテストを書く。\n  - アカウントロック: 失敗回数のしきい値の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロック中は正しいパスワードでも拒否、ログイン成功時の失敗回数の扱い。★しきい値、回数を数える期間、ロックの解除方法\n  - ログイン: 存在しないユーザーとパスワード誤りで、応答からユーザーIDの存在を推測できないこと\n  - トークン: 有効期限の境界（直前は有効／直後は無効）、署名の改ざん、署名方式の指定を悪用した改ざん（`alg: none` 等）、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセストークンが有効期限まで使えること（決定済みの仕様として明示する）。★各トークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか\n  - 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い／不正なときの動作、パスワードがログに出ない。★設定が無いときに起動を止めるか\n  - 認可: 未認証（401）、管理者フラグなし（403）、管理者（200）をサーバー側のテストで確かめる（画面で管理メニューを隠すことはサーバー側の検査の代わりにしない）\n  - 監査ログ: 対象イベントごとに必須項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること。★監査ログの書き込みに失敗したときに操作を失敗させるか\n  - 秘密情報の漏えい: ログ・監査ログの出力にパスワード・トークンの値が含まれないこと\n  - 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる、外部エクスポートが既定で無効であること\n  - 画面: ログイン画面のアクセシビリティ検査、ロック時のメッセージ表示、ログアウトでトークンが破棄されること\n  - 招待と登録の完了: 招待の有効期限の境界（直前は有効／直後は無効）、使い終えた招待・取り消した招待の再使用の拒否、改ざんした招待・存在しない招待の拒否（応答から利用者の存在を推測できないこと）、招待中（登録が終わっていない）の利用者はログイン・トークンの更新・アクセストークンの認証のどれでも拒否されること。★招待の有効期限の値、再送・取り消しの扱い\n  - パスワードの変更: 今のパスワードの確かめ、パスワードの規則の境界、変更の後のリフレッシュトークンの扱い（決めた側の動作を明示したテストにする）。★パスワードの規則、変更の後にほかの端末のリフレッシュトークンを無効にするか\n- 利用者へメールを送る機能では、Code Style と `project.md` のメールの決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 本文のエスケープ: 差し込む利用者の値（氏名・メールアドレスなど）に `<`・`>`・`&`・`\"`・`'` を含めても、タグや属性としてそのまま出ないこと。利用者の値をエスケープしない差し込み（`{{{ }}}`・`{{& }}`）で入れていないこと、属性の値を二重引用符で囲んでいること（エンジンの `{{ }}` は `'` を置き換えないため）\n  - テンプレートの描画: 日本語と英語のすべてのテンプレートを描けて、件名が空でないこと、差し込み漏れ（`{{` の残り）が無いこと、ライセンスヘッダーが描いた本文に出ないこと\n  - 招待の URL: 設定したベース URL だけから組み立て、要求の Host ヘッダーから組み立てないこと。ベース URL の設定が無いときに要求の Host を変えても、招待の URL に出ないこと。★ベース URL の設定が無いときの動作\n  - トークンと URL の漏えい: 招待のトークンと招待の URL が、アプリのログ・監査ログ・トレースの属性・エラー応答に含まれないこと（既存の `*SecretLeakIT` と同じ形で確かめる）\n  - ヘッダーへの差し込み: 宛先・件名・差し込む値に改行（CR・LF）を含めても、メールのヘッダーが増えず拒否されること\n  - 送信の失敗: 送信が失敗したとき（受け手が接続を拒む・応答しない）に、宛先のメールアドレス・SMTP の応答・資格情報が応答とログに含まれないこと。★送信が失敗したときの業務の動作（招待を失敗させるか、再送に回すか）\n- 利用者が投入する DSL（YAML）を読み込む機能では、Code Style の「信頼できない入力」の決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。上限の具体的な数値は設計の段で決め、決めた値の境界で確かめる。\n  - 大きさ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 入れ子の深さ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 別名（アンカー）: 展開の数が上限を超えると拒否され、別名の展開の爆発で処理が止まらない\n  - タグ: 任意の型を作るタグ（`!!` など）を含む DSL は拒否され、型が作られない\n  - 重複キー: 同じキーが重なる DSL はエラーになり、後の値で黙って上書きされない\n  - JSON Schema の `$ref`: 外部の URL を取りに行かない\n  - 拒否の応答: Problem Details の形で返り、YAML・JSON Schema の部品の例外のメッセージを含まない\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22)"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) \n- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) \n- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) \n- 同時の重なりを確実に作るため、本番のコードを変えずに、監査の書き込みの時間を測る LongSupplier（AuditEventListener で2本目を借りる直前に呼ばれる）をテストで差し替えて待ち合わせる方式にした。既存の LoginConcurrencyIT は 8 スレッドでプールの 10 に届かず、前回の失敗のログインで尽きなかった理由の1つと見られる。 (learned 2026-09-23) \n- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) \n- 修正の前の設定（例: 上限 1g）も修正の後の環境（例: CPU 4 の VM）で流し（pre1g）、要件の前提（VM を上げても F3 が起きる）を実測で裏付ける。 (learned 2026-09-23) \n- パッケージごとのカバレッジの下限と SpotBugs の SQL_ の関門を U1 の計画に入れた。team.md の決まりだが今のビルドに無く、この Intent で最初に作る単位のため。U1 の設計の文書には無い作業。 (learned 2026-09-24) \n- U4 で既存の AuditSecretLeakIT の列の一覧に V6 の4列を足し、テストの JVM のヒープを 1g にした。前者は承認済みの V6 と必ず食い違うため、後者は構造の検査がクラスを持ち続け U3 の 10MB 超えのテストでヒープが尽きたため。どちらも計画に無い変更で、依頼者に確かめる。 (learned 2026-09-24) \n- 応答しない対象DB の TIMEOUT の確かめに、テストの中で開いた ServerSocket（受け付けて何も返さない）を使う。外の端末に頼らず確実に再現できる代わりに、本物の DB の遅延ではない。 (learned 2026-09-24) \n- パッケージごとのカバレッジの下限は新しいパッケージだけに当てた。実測で audit.service（行 77.2%）・common.health（行 79.2%）・auth.repository（分岐 50.0%）が単独で下回ったため（team.md の決まりどおり）。既存の 22 パッケージを一覧で外し、新しいパッケージは自動で対象になる。 (learned 2026-09-24) \n- 負荷の試験で接続プールが尽きたかを確かめるときは、使い捨てのアプリにだけ MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics を渡して /actuator/metrics の hikaricp の値を読み、数秒ごとの使用中の数ではなく、待ちの時間切れの累計と借りるまでの待ちの最大で判断する（配備したアプリの公開の範囲は変えない）。 (learned 2026-09-24) \n- k6 などの長い試験は caffeinate -i を付けて流し、PC の自動のスリープで要求が止まって結果が崩れるのを防ぐ（バッテリー駆動のまま 414 秒スリープし、要求が 6分52秒止まった）。内部DB に SQL で直接入れた試験用の利用者はロックの状態の行が無いため、同時のログインを流す前に1人ずつログインさせて行を作る。 (learned 2026-09-24) \n- 軽い API の性能は、投入を重ねて内部DB のファイルが膨らんだ状態（悪い側の条件）のまま測る。メモリの最大（memory.peak）を比べる試験の前は、アプリのコンテナを作り直して前の最大の値を消す。 (learned 2026-09-24) \n- Performance Validation の段が無いため、2件目の直しの負荷の試験での確かめ（Q5: A）の持ち主を Build and Test とした（project.md の学びどおり）。 (learned 2026-09-24) \n- colima の PC で ./gradlew verify を流すときは、README の DOCKER_HOST と TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE をシェルに渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗した。付けて流し直した結果を基準とした。 (learned 2026-09-25) \n- unit-test-instructions.md の k6 inspect のコマンドは --include-system-env-vars が無いと場面の名前が undefined になり確かめにならなかった。承認済みの文書は変えず、実際には付けて流し、code-summary.md に差を記録した。 (learned 2026-09-25) \n- 1回目の verify で AccessTokenApiIT の 6 件が接続の失敗で落ちた。変更の経路に触れず、同じ時刻に Gradle の作業プロセスとの接続も時間切れだったため PC の負荷による一時的な失敗と見立て、段の中の直しの1回目としてコードを変えずに verify を流し直して通した。原因は確かめていない。 (learned 2026-09-25) \n- 依頼者の判断で、試験のイメージを mastersmith:local ではなく mastersmith:followup-fixes で作り、perf/README の手順（local を作り直す）から外れた。配備したアプリは k6 のあいだ止め、約 17 分後に同じコンテナで起動し直した。 (learned 2026-09-25) \n- 試験の後にロックの状態の行の数を数える問い合わせの列の名前を誤り、使い捨ての環境を消した後で取り直せなかった。合格の条件（ログインの checks と 500 の件数）で判定した。消す前に確かめの結果を見てから片付けるべきだった。 (learned 2026-09-25) \n- Q4（確かめられたときだけ直す）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違いうるため、追加の質問 F3 で、再現できなければ不安定と確かめられていない扱いとして統合してよいことを確かめた。 (learned 2026-09-25) \n- 要件 FR2.2（目標は計画の承認の場で決める）を満たすため、依頼者の決定（Q1: A）で、計画を書く前に dslMixed でメモリの内訳を測ることにした。測る間は配備したアプリを止める。 (learned 2026-09-25) \n- 生成で、テストの既定で MBean の登録を無効にする置き場を、計画の候補の TestDatabase ではなくテストの EnvironmentPostProcessor にした。TestDatabase を使わずに Spring を起動するテストのクラスが 15 あり、漏れるため。再現の結合テストは 12 回では伸びが小さく判定がはっきりしないため、24 回・履歴の上限 3 にした。 (learned 2026-09-25) \n- AccessTokenApiIT は、同じ例外の文言を仕組み（colima の IPv4 の転送と Java の IPv6 の待ち受けの番号の重なり）ごと再現できたことを原因の確認とみなし、依頼者の決定（D7）でテストの JVM に preferIPv4Stack を付けて直した。実際の1回目がこれで起きたかは確かめられず、今回の繰り返しでは重なりは起きなかった。 (learned 2026-09-25) \n- Test Strategy は Minimal だが、要件 FR5 と計画の「Build and Test に引き継ぐこと」のため、結合・性能・セキュリティの手順書も作り、この段で dslMixed・--storage --compact（40 回）・詰め直しの最中のログイン・refresh・dslCycle を流した。 (learned 2026-09-25) \n- 計画の dslCycle を最初の台本に入れ忘れ、配備したアプリをもう一度（約 4 分）止めて流した。負荷の試験の台本を書く前に、計画の「Build and Test に引き継ぐこと」の項目を一つずつ台本の手順と突き合わせる。 (learned 2026-09-25) \n- 実際のブラウザのアクセシビリティの検査（axe）は、誤りを出した状態と、現実に近いデータ（2語の氏名など）で、ブランドカラーとテーマのすべての組について行う。user-management の U7 の 080 で初めて、make-you-chic-ui の Avatar（頭文字が2文字のとき）と dark の FormField の誤りの文字のコントラスト不足が出た（050〜070 は誤りの状態を見ず、氏名が1語だったため出なかった）。 (learned 2026-09-28) \n- Playwright の webServer.env に置いた値は json の報告に残る。E2E の仮の資格情報はプロセスの環境変数で渡し、報告の部品の確かめと json の文字列の検索で、パスワード・トークン・メールアドレスが入っていないことを確かめる（user-management の U4 で見つかった）。 (learned 2026-09-28) \n- CI（66fe981）の verify が2回とも別々のテストの時間切れ（H2CompactionByPoolSuspensionIT の接続の待ち 10 秒、InvitationAdminPage.test.tsx の既定 5 秒）で失敗し、U4〜U7 の NFR7.1 が Not Met だったが、依頼者の決定「次のintentでコントラストと一緒に直す。このintentではこのまま進める。」で受け入れた失敗とした。team.md の「CI が失敗したら次に進む前に直す」と食い違うため test-results.md 8.1 に差を記録した。 (learned 2026-09-28) \n- k6 の登録の完了と取り消しの場面は、決定の文言（招待を VU の数だけ）ではなく既定 100 回・招待 100 件とした。トークンは1回しか使えず 10 件では p95 の意味が薄いため、U3 の NFR6.4 の「流す回数以上の招待を用意する」に合わせた。承認の場で確かめる。 (learned 2026-09-28) \n- caffeinate -i は場面ごとではなく、試験の台本全体を包む形で付ける。場面ごとに起こし直すと切れ目で守りが外れ、PC が眠る（user-management の Performance Validation で 92 秒眠り、要求が 1 分 29 秒遅れた）。遅れが出たときは pmset -g log でスリープを確かめる。 (learned 2026-09-28) \n- 応答しないメールの受け手は、Mailpit を docker pause で一時停止して作る（接続は受け付け、何も返さない）。コンテナを止めると接続の失敗になり、時間切れの確かめにならない（user-management の Performance Validation）。 (learned 2026-09-28) \n- Q1 で原因を確かめずに上限を延ばす答え（C）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違うため追加の質問 F1 で確かめ、延ばしたうえで失敗時の診断を足し、差を記録する形（B）になった。Q3 の「すべて試す」は verify で判定できない Temurin 26・logback-appender の扱いを F2 で確かめ、見送り（C）になった。 (learned 2026-09-29) \n- 診断の確かめで、上限 1 ミリ秒は Awaitility の問い合わせの間隔（100 ミリ秒）より短く設定の誤りで弾かれ、150 ミリ秒では手元で接続がすぐ 0 本になり時間切れにならなかった。上限 500 ミリ秒と、待ちの条件を一時的に満たせない形（== -1）にして時間切れを起こした。確かめた後に git checkout で戻した。 (learned 2026-09-29) \n- 警報が鳴ることは、しきい値を 50 ms に下げた警報の決まりの写しを使い捨ての環境に読み込ませて確かめた（Q1: A）。本当に遅い応答を作るより早く PC の負荷も小さいが、しきい値の値そのもので鳴ることは確かめていない。途中で ms-check-p95 のしきい値が 1000 ms ではなく 300 ms（バケットの境界に無い）と分かり、要件の前提 A3 と食い違った。 (learned 2026-09-29)"
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
  "input_sha256": "sha256:6f8e25fd602e2e5ea0ed0252313fc50d10d86e4ab30a8ac29c8aeb5f55c05d3c",
  "contract_sha256": "sha256:e5edce8e792a371ae424706b828fe5f8495818303d0d5c584d98ccfff6b7adc8"
}
```

## 3. 手順

methodology は test-after。層ごとに「実装 → その層のテストを書いて流す → 通ってから次へ」の順に並べる。この Intent で当たる層は、ドメイン（`EmailAddress`）・業務処理（`InitialAdminInitializer`）・画面（E2E の検査の一覧）で、DB・リポジトリ・API の層の変更は無い。ほかは設定・依存・文書。

コミットは生成の担当が行わない。生成の後に依頼者の承認を得てまとめて行う（5節）。`git push` は依頼者が行う。

### 準備と変更の前の基準（Test Baseline）

- [x] **Step 1** 作業のブランチを作る（全 FR）。`develop` の HEAD（`git rev-parse HEAD` で記録）から短命のブランチ `fix/260929-log-deps-cleanup` を作って切り替える。`git status` で、変更がワークフローの記録（`aidlc/spaces/default/intents/260929-log-deps-cleanup/`）の外に無いことを確かめる。`git submodule status` で、2つのサブモジュールが固定先のまま（先頭に `+` が無い）ことを確かめる。
- [x] **Step 2** テストの仕組みの確かめと、この Intent のテストだけを流すコマンドの確かめ（test runner readiness）。`unit-test-instructions.md` の 2.1・2.2 のコマンドを変更の前のコードで流し、通ることを確かめる（`EmailAddressTest`・`InitialAdminInitializerTest`・`InitialAdminIT`）。frontend の書式・リンタのコマンド（2.3）を `e2e/100-app-text-contrast.e2e.ts` に流して通ることを確かめる。
- [x] **Step 3** 全体の基準を取る（brownfield の Test Baseline、NFR2）。colima が動いていることと環境変数（`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`、`unit-test-instructions.md` の 1節）を確かめてから `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を流し、単体・結合テストの件数（成功・失敗・SKIPPED）、全体の行・分岐のカバレッジ、`user.domain`・`user.service` のパッケージのカバレッジ、frontend のテストの件数とカバレッジを記録する。SKIPPED が 0 でなければ、環境を直して流し直す（`project.md` の Testing Posture）。失敗があれば変更に入らずに報告する。
- [x] **Step 4** E2E 100 の基準を取る（FR3.2、A3）。`unit-test-instructions.md` の 2.4 の手順（`./gradlew :backend:bootWar`、Mailpit の起動、100 だけを流す）で、変更の前の固定先 `310e1ec` で 100 が通ること（既知の違反の2件が一覧どおりに当たること）を確かめ、結果（`frontend/test-results/e2e-results.json` の注記の `knownViolations`）を記録する。

### FR1 ドメインの層（伏せ字の関数）

- [x] **Step 5** 実装（FR1.2）。`backend/src/main/java/cherry/mastersmith/user/domain/EmailAddress.java` に `public static String mask(String email)` を足す。形は 1.3 のとおり。Javadoc（日本語）に、形・null と空の扱い・`project.md` の Forbidden に反しないとみなす根拠（F1: A）・ローカル部が1文字のときは先頭の1文字でローカル部が全部見えること（R-03 を受け入れた扱い）を書く。
- [x] **Step 6** テストを書いて流す（FR1.2、NFR1）。`backend/src/test/java/cherry/mastersmith/user/domain/EmailAddressTest.java` に次を足し、`unit-test-instructions.md` の 2.1 のコマンドで流す。説明文は英語。
  - 例のテスト（`@ParameterizedTest`）: 通常の値（`admin@example.com` → `a***@example.com`）、ローカル部が1文字（`a@example.com` → `a***@example.com`。R-03 の今の形の固定）、先頭がサロゲートペアの文字（コードポイントで1文字）、`@` が無い値 → `***`、ローカル部が空（`@example.com`）→ `***`、null と空 → null。
  - 性質ベースのテスト（jqwik、`@Property`）: 英数字のローカル部（2文字以上）とドメインから作った値で、結果にローカル部そのものが含まれないこと、結果が先頭の1文字で始まり `***@`＋ドメインで終わること。失敗時は jqwik が乱数の種を表示し、`.jqwik-database` で再現できる（team.md の Testing Posture）。

### FR1 業務処理の層（初期管理者の作成のログ）

- [x] **Step 7** 実装（FR1.1・FR1.3・FR1.4）。`backend/src/main/java/cherry/mastersmith/user/service/InitialAdminInitializer.java` の INFO の3か所（88・96・99 行）の `addKeyValue("email", email)` を `addKeyValue("maskedEmail", EmailAddress.mask(email))` にする（キーの名前を替える。Q-B: B）。文言（「初期管理者を作成しました」「初期管理者は既にいるため、作成しませんでした」）と WARN（81〜84 行）は変えない。クラスの Javadoc 38〜40 行の「ログの項目（キー `email` を含む）は前の Intent のまま（…据え置き…）」を、「キー `maskedEmail` に伏せ字（先頭の1文字＋`***`＋`@`＋ドメイン）だけを載せ、メールアドレスそのものは載せない。`maskedEmail` は外部エクスポートの伏せる対象のキーではないため、伏せ字がそのまま送られる（Intent 260929-log-deps-cleanup の FR1、`project.md` の Forbidden）」の趣旨に書き直す。
- [x] **Step 8** テストを書いて流す（FR1.1、NFR1、bugfix の再現テスト）。
  - `backend/src/test/java/cherry/mastersmith/user/service/InitialAdminInitializerTest.java`: `creates()` の `render(event)).contains("admin@example.com")` を、メールアドレスそのもの（`admin@example.com`）が本文とキー・値に含まれず、キー `maskedEmail` の値が `a***@example.com` で、キー `email` が無いことの確かめに直す。`existing()`・`duplicate()` にも同じ確かめを足す（3か所すべてを覆う）。`@DisplayName` の英語の説明も直す（例 `…logged at INFO with the masked email only`）。
  - `backend/src/test/java/cherry/mastersmith/user/service/InitialAdminIT.java`: `createsOnce` で、`output.getAll()` に `admin@example.com`（そろえた値）と `Admin@Example.COM`（設定の値）がどちらも含まれず、`a***@example.com` がキー `maskedEmail` の値として含まれ、初期管理者の INFO にキー `email` が無いことを確かめる（1回目の作成と2回目の既にいる、の両方のログを含む。`JsonLogRecords.parse` で JSON の項目として読む）。`JsonLogRecords.assertContainsNoSecret` を使えるなら使う。
  - `unit-test-instructions.md` の 2.1・2.2 のコマンドで流す。
  - 再現の確かめ: テストを書いた後に、`InitialAdminInitializer.java` だけを一時的に変更の前に戻して（`git stash push -- backend/src/main/java/cherry/mastersmith/user/service/InitialAdminInitializer.java`）同じコマンドを流し、直したテストが失敗する（不具合を捕まえる）ことを確かめてから戻す（`git stash pop`、`git diff` で戻ったことを確かめる）。結果を code-summary.md に記録する。

### FR3 画面の層（make-you-chic-ui の固定先と E2E の既知の違反）

- [x] **Step 9** サブモジュールの固定先を上げる（FR3.1）。`git -C vendor/make-you-chic-ui cat-file -t 077f5b4` で手元にあることを確かめ（fetch はしない。無ければ止めて報告する）、前の固定先（`git ls-tree HEAD vendor/make-you-chic-ui` の完全なハッシュ）を記録してから `git -C vendor/make-you-chic-ui checkout --detach 077f5b4` で替え、後の完全なハッシュ（`git -C vendor/make-you-chic-ui rev-parse HEAD`）を記録する。`git -C vendor/make-you-chic-ui status --porcelain` が空（中身を変えていない）ことを確かめる。ステージとコミットはしない（5節の C1 で、承認を得て専用のコミットにする）。
- [x] **Step 10** 固定先を上げた後の E2E 100 の実測（FR3.2、A3）。`STATE_KNOWN_VIOLATIONS` を変えずに 2.4 の手順（`./gradlew :backend:bootWar` で make-you-chic-ui と画面を作り直す）で 100 を流す。2件が当たらなくなっていれば、100 は「既知の違反が一覧と違います」で失敗する見込みで、注記の `knownViolations` が空であることで2件が解消したと判断する。ほかの違反（`unexpected`）が新しく出たときは、その組・状態・要素だけを Step 11 で `STATE_KNOWN_VIOLATIONS` に足して進め、Step 20 で README の既知の制約に書き、code-summary.md と承認の場で報告する（依頼者の答え Q-E: B）。
- [x] **Step 11** 実装とテスト（FR3.2）。`frontend/e2e/100-app-text-contrast.e2e.ts` の `STATE_KNOWN_VIOLATIONS` から今の2件を外して空の配列にする（Step 10 で新しい違反が出たときは、その実測の組・状態・要素だけを載せる。型 `StateKnownViolation` と、一覧どおりに当たらなければ失敗にする仕組みは、`frontend/e2e/support/axe.ts` の一覧と同じく次の既知の制約のために残す）。27〜33 行と 55〜71 行の注記を、`077f5b4` で2件とも解消したこと、一覧の中身（空、または新しく足した違反とその経緯）、一覧と違う違反が出たら失敗にすることに直す。2.3 の書式・リンタのコマンドを流し、2.4 の手順で 100 を流して通ることを確かめる。
- [x] **Step 12** README の表の値の計算（FR3.3、要件の未解決の点）。`vendor/make-you-chic-ui/packages/make-you-chic-ui/src/theme/` の `tokens.css`・`semantic.css` の値（読み取りだけ）から、primary のボタンの hover（背景 `--color-primary-hover`＝brand-600 に文字 `--color-primary-hover-text`＝白）と、選ばれたタブ（`--color-primary-emphasis-text`、ライトは brand-700、ダークは brand-400）の組ごとのコントラスト比を、WCAG の相対輝度の式で計算する（計算は作業用のスクリプトをリポジトリの外の一時の場所で行い、リポジトリには置かない）。値は Step 20 の README に「計算の値」として書く。

### FR4 依存と Dependabot

- [x] **Step 13** spotless を上げる（FR4.1）。`gradle/libs.versions.toml` 29 行を `spotless = "8.10.3"` にする。
- [x] **Step 14** spotless の確かめ（FR4.1）。`./gradlew spotlessCheck :backend:spotlessCheck` を流す。書式の判定が変わって既存のファイルが引っかかったときは、`spotlessApply` で直さずに止めて報告する（全 Java ファイルに及ぶため、依頼者が判断する）。
- [x] **Step 15** `@types/node` を上げる（FR4.2）。`frontend` で `npm install --save-dev @types/node@26.6.3 --no-audit --no-fund` を流し、`git diff --stat frontend/` で `package.json` の `@types/node` の範囲と `package-lock.json` の `node_modules/@types/node`（と、それに伴う `undici-types` があればその項目）だけが変わったことを確かめる。ほかの部品の解決が変わっていたら止めて報告する。続けて `npm ci --no-audit --no-fund` と `npm run typecheck` を流す（lockfile どおりに入ること、型の検査が通ること）。
- [x] **Step 16** Dependabot の設定（FR4.3・FR4.4・FR4.5、R-02）。`.github/dependabot.yml` を次のとおりに直す。
  - npm（50〜54 行）に `ignore` を足す: `dependency-name: "typescript"`、`update-types: ["version-update:semver-major"]`。コメントに理由（`@typescript-eslint/*` の peer が `typescript <6.1.0` で、7 系を取り込むと型の検査・ESLint が壊れうる。typescript-eslint は ignore に入れない。要件の [decided]）と外す時期（`@typescript-eslint/*` が TypeScript 7 を受け付けたとき）を書く。
  - gradle の `tools.jackson:jackson-bom` の `update-types`（48〜49 行）を外し、すべての版を知らせない形にする。42〜44 行のコメントを、すべての版を知らせないこと、脆弱性の直しには OSV-Scanner の関門（`./gradlew verify` と CI の実行のときだけ働く）で気づくこと、team.md の「重大度 High 以上の知らせは次の Bolt の前に取り込む」との差であること（R-02）、外す時期（Spring Boot を上げるとき）に書き直す。
  - `ruby -e 'require "yaml"; YAML.load_file(".github/dependabot.yml")'` で YAML として読めることを確かめる。`gradle/libs.versions.toml` の `jackson = "3.1.6"` と `frontend/package.json` の `typescript` の版を変えていないことを `git diff` で確かめる（FR4.5）。

### FR5 警報 ms-check-p95

- [x] **Step 17** 実装（FR5.1・FR5.2・FR5.3）。8節の FR5.2 の表の5か所を 500 にそろえる: `docker/monitoring/provisioning/alerting/mastersmith.yaml` の summary（227 行「300 ミリ秒」）としきい値（248 行 `300`）、`docker/monitoring/dashboards/mastersmith-overview.json` の SLI の表（83 行「300 ミリ秒以内」）・パネルの説明（368 行「目標 300 ミリ秒（U3 の NFR1.1）」を、Intent 260929-log-deps-cleanup で 500 ミリ秒に緩めたことが分かる文に）・しきい値の段（395 行 `"value": 300`）。`backend/src/main/resources/application.yaml` の `slo` のコメントに、警報のしきい値 500 ms・1000 ms を境界に含めることを書く（値は変えない）。
- [x] **Step 18** 設定の確かめ（FR5.2、R-04）。`python3 -m json.tool docker/monitoring/dashboards/mastersmith-overview.json > /dev/null` と `ruby -e 'require "yaml"; YAML.load_file("docker/monitoring/provisioning/alerting/mastersmith.yaml")'` で読めることを確かめる。`grep -rn '300' docker/monitoring` の結果に、確認用 API の目標・しきい値としての 300 が残っていないこと（残るのは `3000` など無関係のものだけ）を確かめ、結果を記録する。README の検索は Step 20 の後に行う。
- [x] **Step 19** 手元の監視での確かめ（NFR4）。`docker compose --profile monitoring up -d --force-recreate lgtm` で警報の決まりとダッシュボードを読み込み直し、Grafana の API（ログインなしの閲覧で読める `/api/prometheus/grafana/api/v1/rules`）で `ms-check-p95` が読み込まれ、しきい値が 500 であること、評価で誤り（`health` が `error`）になっていないことを確かめる。ダッシュボードの確認用 API のパネルのしきい値の段が 500 であることを `/api/search` と `/api/dashboards/uid/<uid>` で確かめる。警報の式（`histogram_quantile(0.95, …uri="/api/admin/check"…)`）を Prometheus（Grafana の Explore、またはデータソースの問い合わせの API）で実行し、式が誤りなく評価されることを確かめる（値が NaN でもよい）。確かめはここまでとし、`/api/admin/check` に要求は送らない（依頼者の答え Q-D: A）。監視を起動する前に止まっていたなら、確かめの後に `docker compose --profile monitoring stop lgtm` で元の状態に戻す。

### 文書

- [x] **Step 20** README を直す（FR1.3・FR3.3・FR5.3）。
  - 38 行: 固定先を `077f5b4` にし、経緯（`735ef04` → `310e1ec`（Intent 260928-quality-followup）→ `077f5b4`（この Intent。Tabs の選ばれたタブと primary のボタンの hover の文字のコントラストの直し））を書く。
  - 158 行（100 の説明）: 既知の違反は無く一覧は空で、既知の違反が出たら失敗にすることに直す。113・122・130・138・909・924・938 行の `310e1ec` の記述は、事実の経緯のためそのまま残す。
  - 957 行（DSL の管理の画面のコントラスト）と 972〜993 行の「既知の制約（ブランドカラーのコントラスト）」: `077f5b4` で残る既知の制約の2件が解消したことに書き直し、Step 12 の計算の値（hover の背景と白の文字、選ばれたタブの文字）を表か箇条で足す。976 行の前提（hover も含めた文字の出し分け）も直す。Step 10 で新しい違反を一覧に足したときは、その組・状態・要素と、make-you-chic-ui の部品のためこのリポジトリから直さないことを、既知の制約として同じ節と 158 行に書く（Q-E: B）。
  - 215 行（起動の確かめ）: INFO にはメールアドレスそのものは載らず、キー `maskedEmail` に伏せ字（例 `a***@example.com`）だけが載ることを1文足す（手順そのものは変えない。FR1.3・A2・Q-B）。
  - 664 行付近（外部エクスポートの説明）: 伏せる4つのキーの説明は変えずに、初期管理者の作成のログのキー `maskedEmail` は伏せ字のため伏せる対象にしておらず、外部エクスポートでも伏せ字の値がそのまま送られることを1文足す（Q-B: B）。
  - 682 行（バケットの境界の説明）の「警報のしきい値 1000 ms を含む」を「警報のしきい値 500 ms・1000 ms を含む」に、703 行の表の `ms-check-p95` を 500 ms にし、確認用 API の目標を 300 ms から 500 ms に緩めたこと（Intent 260929-log-deps-cleanup の FR5、F3: A）を表の下に1文で書く。
  - `grep -n '300' README.md` で、監視・警報の節（「手元の監視（Grafana）」「警報と対応の手順」）に確認用 API の 300 が残っていないことを確かめる（R-04）。

### 最後の検査と記録

- [x] **Step 21** 統合の前の関門（NFR2）。colima と環境変数を確かめてから `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を流す。全段の成功、対象DB のテストの SKIPPED が 0、`vendorUnchanged`・`mustacheVendorUnchanged` の成功、`osvScan` の成功を確かめ、単体・結合テストの件数、全体と `user.domain`・`user.service` のカバレッジ（行 80%・分岐 70% 以上）を Step 3 の基準と並べて記録する。落ちたテストがあれば、変更の経路に関わるかを見立てて報告する（コードを変えずに流し直すのは、見立てを記録したうえで1回まで）。
- [x] **Step 22** E2E の全体（NFR3）。画面に関わる変更（固定先の更新）を統合する前の確かめとして、Mailpit を起動して `./gradlew e2eTest`（010〜100、少なくとも 100 を含む全体）を流し、すべて通ることを確かめる。`frontend/playwright-secret-check-reporter.ts` の確かめが通ること（json の結果にパスワード・トークン・メールアドレスが無いこと）も見る。終わったら Mailpit を `docker compose stop mailpit` と `docker compose rm -f mailpit` で片付ける。
- [x] **Step 23** 段の記録を書く。`aidlc/spaces/default/intents/260929-log-deps-cleanup/construction/code-generation/` に `code-summary.md`（変更の一覧、Step 3・Step 21 の数字、Step 4・Step 10 の E2E の実測（Q-E: B で一覧に足した違反があればその内容）、Step 8 の再現の確かめ、Step 12 の計算の値、Step 19 の結果、計画との差）、`traceability.json`（8節の対応。FR2.1・FR2.2 は `N/A`）、`source-manifest.json`（ワークフローの記録の外で変えたすべてのパス。`vendor/make-you-chic-ui` の固定先を含む。team.md はこの段で変えないため含めない）を書く。

## 4. Build and Test に引き継ぐこと

- 統合（5節の fast-forward）の後、依頼者の `git push` で動く CI（`.github/workflows/ci.yml` の `./gradlew verify`）の結果を確かめる。
- Dependabot のプルリクエスト #5（typescript 7.0.2）・#18（@types/node 26.6.3）・#19（spotless 8.10.3）・#20（jackson-bom 3.1.7）は、統合と push の後に依頼者が GitHub で閉じる（A4。AI は閉じない）。#5・#20 は ignore の設定で次から開かなくなることを、次の Dependabot の実行で確かめる。
- コード生成の段の承認の後、Build and Test の中で team.md を直す（FR6.1・FR6.2。Testing Contract の入力が変わるため、この段では直さない。依頼者の答え Q-C: A）。`aidlc/spaces/default/memory/team.md` の Testing Posture の「`user.domain`・`user.repository`・`user.service` を含む 22 パッケージ」を、今のビルドに合わせて「2026-09-29 の時点で 12 パッケージ。`user.*` は Intent 260925-user-management で外れて含まない」の趣旨に直し、Dependabot の方針は足さない。コミットは承認を得て専用に行う。
- 手元の監視の確かめは、この段では式が誤りなく評価されることまで（Q-D: A）。警報が数の値で評価されることは確かめていない。
- Q-E: B で E2E 100 の既知の違反の一覧に足したものがあれば、その扱い（make-you-chic-ui 側への直しの依頼など）。

## 5. 作業のブランチ・コミットの分け方・統合の形

- 作業のブランチ: `fix/260929-log-deps-cleanup`（`develop` から作る短命のブランチ。Step 1）。
- コミットは生成の担当が行わない。生成の後に、依頼者の承認を得てまとめて次のように分ける（メッセージは日本語）。
  - **C1** サブモジュールの固定先の更新だけ（`vendor/make-you-chic-ui` の gitlink だけをステージする）。件名に前後のハッシュ（`310e1ec` → `077f5b4`）、本文に完全なハッシュと取り込む2コミット（`a34d611`・`077f5b4`）を書く（`project.md` の Mandated）。
  - **C2** E2E 100 の既知の違反を外す（`frontend/e2e/100-app-text-contrast.e2e.ts`）と README のコントラスト・固定先の記述。
  - **C3** 初期管理者のログの伏せ字（`EmailAddress.java`・`InitialAdminInitializer.java`）と、その再現のテスト（`EmailAddressTest.java`・`InitialAdminInitializerTest.java`・`InitialAdminIT.java`）と README 215 行。直しと再現のテストを同じコミットに入れる（`project.md` の Mandated「不具合を修正するときは、その不具合を再現するテストを同じコミットに含める」）。
  - **C4** 依存と Dependabot（`gradle/libs.versions.toml`・`frontend/package.json`・`frontend/package-lock.json`・`.github/dependabot.yml`）。
  - **C5** 警報 ms-check-p95 のしきい値（`docker/monitoring/` の2ファイル・`application.yaml` のコメント・README の監視と警報の節）。
  - 段の記録（`aidlc/spaces/default/intents/260929-log-deps-cleanup/`）のコミットは、ワークフローの進め方に従い、それぞれ別に提案して承認を得る。
- 統合: Step 21・Step 22 が通った状態で、`develop` を `fix/260929-log-deps-cleanup` へ fast-forward で進める（サブモジュールの固定先の更新を専用のコミットとして残すため。`team.md` の Way of Working）。squash はしない。統合も依頼者の承認を得てから行い、`origin` への `git push` は依頼者が行う。統合の後に作業のブランチを消す。

## 6. 依頼者に確かめたこと（答え、`code-generation-questions.md`）

- **Q-A（R-03、伏せ字の形）**: A。FR1.2 の形のまま（ローカル部が1文字でも、先頭の1文字＋`***`＋`@`＋ドメイン）とし、今の形を境界のテストで固定する（Step 6）。
- **Q-B（ログのキーの名前）**: B。キーの名前を `email` から `maskedEmail` に変える。`SanitizingLogRecordExporter` の `MASKED_KEYS` は変えず、外部エクスポートでは伏せ字の値がそのまま送られる。README 664 行付近と 215 行を直す（Step 7・Step 8・Step 20）。
- **Q-C（team.md を直す時点）**: A。この段では直さず、コード生成の段の承認の後、Build and Test の中で直す（4節）。FR6.1・FR6.2 は traceability の予定で `Deferred`。
- **Q-D（NFR4 の確かめの深さ）**: A。警報の決まりとダッシュボードが読み込まれ、式が誤りなく評価されることまで。`/api/admin/check` に要求は送らない（Step 19）。
- **Q-E（固定先の更新の後の新しい違反）**: B。新しく出た違反は既知の違反の一覧に足して進め、README の既知の制約に書き、code-summary.md と承認の場で報告する（Step 10・Step 11・Step 20・Step 23）。

未解決の点: なし。

## 7. 計画を書いた時点で分かっていること・懸念

- TraceAspect は業務処理の層のメソッドの引数を TRACE のログに文字列で出す。`UserAccountService.existsByEmail(String)`（初期管理者が使う）は `String` のメールアドレスを受け取るため、そのロガーを TRACE にしたときだけ、メールアドレスそのものがログに出うる（既定では出ない）。FR1 の範囲（INFO の3か所）の外のため、この計画では変えない。
- 監査の失敗の ERROR は、1.1 の決定により、メールアドレスを載せたまま残る（既知の例外）。
- spotless 8.10.3 で書式の判定が変わるかは、Step 14 まで分からない。
- Q-B: B により、外部エクスポートを有効にした環境では、初期管理者の作成のログの伏せ字（先頭の1文字とドメイン）が外へ送られる（今は外部エクスポートは既定で無効）。

## 8. 要件と手順・テストの対応（traceability の予定）

| 要件 | 手順 | テスト・確かめ | 合格の条件 |
|---|---|---|---|
| FR1.1 | Step 7・Step 8 | `InitialAdminInitializerTest`（3か所）、`InitialAdminIT.createsOnce` | INFO の本文とキー・値に、そろえたメールアドレスも設定の値も無く、キー `email` が無く、キー `maskedEmail` の値が `a***@example.com` |
| FR1.2 | Step 5・Step 6 | `EmailAddressTest`（例と性質ベース） | 1.3 の形のとおりの値を返す |
| FR1.3 | Step 7・Step 20 | `InitialAdminIT` の文言の確かめ（既存） | 文言は変わらず、README 215 行の手順はそのまま使え、215 行と 664 行付近がキー `maskedEmail` の扱いを説明している |
| FR1.4 | Step 7 | 目で確かめる（レビュー） | Javadoc の「据え置き」の記述が無く、伏せ字だけを載せることが書かれている |
| FR2.1 | 行わない | 行わない | `N/A`（1.1。R-01 を受けた依頼者の決定、2026-09-29。`AuditEventListener.java` は変えない） |
| FR2.2 | 行わない | 行わない | `N/A`（1.1。README 774 行は変えない） |
| FR3.1 | Step 9 | `git ls-tree HEAD vendor/make-you-chic-ui`（C1 の後）、`vendorUnchanged` | 固定先が `077f5b4` で、C1 の本文に前後の完全なハッシュがある |
| FR3.2 | Step 10・Step 11 | E2E 100（Step 11・Step 22） | 今の2件が `STATE_KNOWN_VIOLATIONS` に無く（新しい違反を足したときはそれだけが載り）、100 が 20 組・すべての状態で通る |
| FR3.3 | Step 12・Step 20 | 目で確かめる（レビュー） | README 38・158・957・972〜993 行が `077f5b4` と解消を説明し、既知の違反の2件の説明が残っていない（新しく足した違反があれば既知の制約として書かれている） |
| FR4.1 | Step 13・Step 14 | `spotlessCheck`、`verify` | `gradle/libs.versions.toml` の `spotless` が `8.10.3` で、書式の検査が通る |
| FR4.2 | Step 15 | `npm ci`・`npm run typecheck`・`osvScan` | `frontend/package-lock.json` の `node_modules/@types/node` の `version` が `26.6.3` で、ほかの解決は変わらない |
| FR4.3 | Step 16 | YAML の読み込み | `.github/dependabot.yml` の npm に `typescript` の `version-update:semver-major` だけの ignore があり、`@typescript-eslint` の ignore は無い |
| FR4.4 | Step 16 | YAML の読み込み | `tools.jackson:jackson-bom` の ignore に `update-types` が無く（すべての版）、コメントに OSV-Scanner の関門と team.md との差がある |
| FR4.5 | Step 16 | `git diff` | `jackson = "3.1.6"` と `typescript` の `^6.0.3` が変わっていない |
| FR5.1 | Step 17 | Step 18・Step 19 | `ms-check-p95` のしきい値が 500 |
| FR5.2 | Step 17・Step 18・Step 20 | JSON・YAML の読み込みと `300` の検索 | 下の5か所と README 703 行が 500 で、確認用 API の 300 がどこにも残らない |
| FR5.3 | Step 20 | 目で確かめる（レビュー） | README に目標を 500 ms に緩めたことが書かれている（要件には記録済み） |
| FR6.1 | この段では行わない | ― | `Deferred`（Build and Test で直す。Q-C: A。Testing Contract の入力が変わるため。4節） |
| FR6.2 | この段では行わない | ― | `Deferred`（FR6.1 と一緒に Build and Test で扱う。この段では team.md を変えない） |
| NFR1 | Step 6・Step 8 | 上の FR1.1・FR1.2 のテスト | 初期管理者の作成の経路で、メールアドレスそのものがログに出ない（監査の失敗の経路は 1.1 により対象外） |
| NFR2 | Step 3・Step 21 | `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` | 全段が通り、SKIPPED が 0、`user.domain`・`user.service` を含む下限（行 80%・分岐 70%）を保つ |
| NFR3 | Step 22 | `./gradlew e2eTest` | 010〜100 がすべて通る |
| NFR4 | Step 19 | 手元の監視の API | `ms-check-p95` が読み込まれ、しきい値 500 で、評価が誤りにならない（要求は送らない。Q-D: A） |

FR5.2 の直す箇所（計画の時点の行）:

| ファイル | 行 | 今の値 |
|---|---|---|
| `docker/monitoring/provisioning/alerting/mastersmith.yaml` | 227 | summary の「300 ミリ秒を超えた」 |
| `docker/monitoring/provisioning/alerting/mastersmith.yaml` | 248 | しきい値 `300` |
| `docker/monitoring/dashboards/mastersmith-overview.json` | 83 | SLI の表の「300 ミリ秒以内」 |
| `docker/monitoring/dashboards/mastersmith-overview.json` | 368 | パネルの説明「目標 300 ミリ秒（U3 の NFR1.1）」 |
| `docker/monitoring/dashboards/mastersmith-overview.json` | 395 | しきい値の段 `"value": 300` |
| `README.md` | 703 | 「警報と対応の手順」の表の 300 ms |
