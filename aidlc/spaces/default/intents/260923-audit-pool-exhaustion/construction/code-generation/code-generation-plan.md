# Code Generation の計画 — 接続プールの上限の引き上げと再現テスト（F2）

## 対象と前提

- 対象: 単位の分割の無い不具合の修正（scope: bugfix、Test Strategy: Minimal）。成果物はこのディレクトリ（`aidlc/spaces/default/intents/260923-audit-pool-exhaustion/construction/code-generation/`）に置く。
- 入力: `aidlc/spaces/default/intents/260923-audit-pool-exhaustion/inception/requirements-analysis/requirements.md`（FR1〜FR6、NFR1〜NFR4）と、コードの知識ベース `aidlc/spaces/default/codekb/mastersmith2/`。
- 直し方: プログラム（`backend/src/main/java/`）は変えない。変えるのは設定（`application.yaml`）、テスト、文書だけとする（FR1.4）。
- 作業の場所: 単位の分割が無いため、Bolt の作業ブランチは作らずに `develop` の上で作業する。修正と再現テストは、依頼者の承認を得て1つのコミットにまとめる（`project.md` の Mandated: 不具合の修正は、それを再現するテストと同じコミットに含める）。

## 変更するファイル

| ファイル | 変更 | 要件 |
|---|---|---|
| `backend/src/main/resources/application.yaml` | `spring.datasource.hikari.maximum-pool-size` を `${MASTERSMITH_DB_MAXIMUM_POOL_SIZE:30}` にする。日本語のコメントで意味と既定値と README の制約への参照を書く | FR1.1、FR1.2 |
| `backend/src/test/java/cherry/mastersmith/audit/testsupport/AuditWriteBarrierConfig.java`（新規） | テストの補助。監査が書き込みの時間を測る部品（`AuditConfig` の `LongSupplier`、`AuditEventListener` の 114 行で2本目の接続を借りる直前に呼ばれる）を差し替え、指定した件数の要求がそろうまで待ち合わせる（待ちの上限つき） | FR3.4 |
| `backend/src/test/java/cherry/mastersmith/auth/web/ConcurrentLoginAuditIT.java`（新規） | 再現テスト。HTTP で同時に N 件（10・20）の成功のログインを流し、応答・監査の行の数・接続の待ちの時間切れを確かめる | FR3.1〜FR3.4、NFR1 |
| `backend/src/test/java/cherry/mastersmith/config/DataSourcePoolIT.java`（新規） | プールの上限の既定値が 30 であること、`MASTERSMITH_DB_MAXIMUM_POOL_SIZE` で変えられることを確かめる | FR1.1、FR1.2 |
| `README.md` | 「環境変数」の表に `MASTERSMITH_DB_MAXIMUM_POOL_SIZE` の行を加える。「監査ログ（U4）」の節に、既知の制約（2本使いと、上限に達したときの再発）を加える | FR1.3、FR5.1 |
| `.env.example` | 内部DBの項目に、`MASTERSMITH_DB_MAXIMUM_POOL_SIZE` をコメントとして加える（既存の任意の項目と同じ書き方） | FR1.3 |

## 再現テストの作り方（FR3）

- `@SpringBootTest(webEnvironment = RANDOM_PORT)`、`TestDatabase.register`（組み込み H2 のファイル）、照合の cost 4 の設定で起動し、既存の `AuthApi`（`auth/testsupport`）で `POST /api/auth/login` を送る。利用者はテストごとに作る（N 人、同じパスワード）。
- 同時の開始は、`CountDownLatch` で全スレッドをそろえてから一斉に送る。スレッドの数は N 以上にする（既存の `LoginConcurrencyIT` は 8 スレッドで、プールの 10 に届かなかった）。
- 要求の重なりを確実にするため、`AuditWriteBarrierConfig` の差し替え部品が、各要求の「書き込みの開始の時刻」の呼び出しで、N 件がそろうまで待たせる。この時点で、各要求は確定の後の業務の接続（1本目）を持ったままである。そろった後、各要求が2本目を借りに行くので、プールの上限が N 以下なら F2 の待ち合いが必ず起きる。実時刻の `sleep` は使わない。待ち合わせには上限（20 秒。HTTP の要求の時間切れ 30 秒より短くし、失敗のときも結果を確かめられるようにする）をつけ、そろわなかったときもテストが止まらないようにする。
- 合格の条件（N = 10・20 のそれぞれ）:
  - 応答がすべて 200
  - `audit_events` の `LOGIN_SUCCEEDED` の行が、そのテストの利用者の分だけ N 件ある（`AuditRows` で数える）
  - 出力捕捉（`OutputCaptureExtension`）で、ERROR「監査イベントの記録に失敗しました」と「Connection is not available」が1件も無い
- テストの説明文（`@DisplayName`）は英語で書く。

## 実行の手順

Testing Contract の方針は test-after（層ごとに実装してから、その層のテストを書いて実行する）である。今回は本番のコードの層の変更が無く、変えるのは設定だけである。そのうえで、要件 FR2.1（修正の前に、プールの上限 10 で失敗し、30 で通ることを記録する）に従い、再現テストを設定の変更より先に書いて、変更の前後で実行する。

- [x] Step 1: 変更の前の基準を取る。`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行し、テストの件数（単体・結合）、失敗の数、カバレッジ（行・分岐）を記録する（`project.md` の Testing Posture: 実測の数字だけを報告する）。
- [x] Step 2: テストの実行の準備を確かめる。`./gradlew :backend:integrationTest --tests 'cherry.mastersmith.auth.service.LoginConcurrencyIT'` が単独で動くことを確かめ、この Intent のテストの実行のコマンドを `unit-test-instructions.md` のとおりに固める。
- [x] Step 3: テストの補助 `AuditWriteBarrierConfig.java` と、再現テスト `ConcurrentLoginAuditIT.java` を書く（ライセンスヘッダー、日本語の Javadoc、英語の `@DisplayName`）。
- [x] Step 4: 修正の前の試し（FR2.1）。`application.yaml` を変える前（上限 10）に `ConcurrentLoginAuditIT` を実行し、N = 10・20 のどちらも失敗して F2 が再現することを確かめる。結果（通った・失敗した、監査の行の数、待ちの時間切れの件数、かかった時間）を控える。
- [x] Step 5: 設定を変える。`application.yaml` の `maximum-pool-size` を `${MASTERSMITH_DB_MAXIMUM_POOL_SIZE:30}` にし、コメントを書く（FR1.1、FR1.2）。
- [x] Step 6: 修正の後の試し（FR2.1）。`ConcurrentLoginAuditIT` を実行し、N = 10・20 のどちらも通ることを確かめる。加えて、環境変数 `MASTERSMITH_DB_MAXIMUM_POOL_SIZE=10` を付けて実行し、再び失敗すること（環境変数が効き、テストが F2 を検出できること）を確かめる。結果を控える。
- [x] Step 7: `DataSourcePoolIT.java` を書いて実行する。既定の設定で `HikariDataSource` の上限が 30 であること、`MASTERSMITH_DB_MAXIMUM_POOL_SIZE=12` を設定すると 12 になることを確かめる（FR1.1、FR1.2）。
- [x] Step 8: 文書を直す。README の「環境変数」の表に行を加え、「監査ログ（U4）」の節に既知の制約を書く。`.env.example` にコメントの行を加える（FR1.3、FR5.1）。
- [x] Step 9: 統合の前の検査。`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行し、すべて通ること、既存のテスト（FR4.1 に挙げたもの）が変更なしで通ること、カバレッジの下限（行 80%・分岐 70%）を満たすことを確かめる。`git diff --stat -- backend/src/main/java` が空であること（FR1.4）を確かめる。
- [x] Step 10: 成果物を書く。`code-summary.md`（変更したファイル、Step 1・4・6・9 の実測の結果、判断、計画との差）、`traceability.json`（FR・NFR の ID ごとの対象ファイル）、`source-manifest.json`（変更したパスの一覧）。
- [ ] Step 11: コミットを提案する。依頼者の承認を得て、修正・再現テスト・文書を1つのコミットにする（日本語のメッセージ）。AI は `git push` をしない。

## 要件との対応

| 要件 | 計画の Step | 確かめ方 |
|---|---|---|
| FR1.1 既定値 30 | Step 5、7 | `DataSourcePoolIT`（既定の上限が 30） |
| FR1.2 環境変数で変えられる | Step 5、6、7 | `DataSourcePoolIT`（12 になる）、Step 6 の環境変数 10 での再失敗 |
| FR1.3 README・`.env.example` | Step 8 | 文書の差分 |
| FR1.4 プログラムを変えない | Step 9 | `git diff --stat -- backend/src/main/java` が空 |
| FR2.1 修正の前後の試しの記録 | Step 4、6、10 | `code-summary.md` の記録 |
| FR3.1〜FR3.4 再現テスト | Step 3、4、6 | `ConcurrentLoginAuditIT`（N = 10・20） |
| FR4.1・FR4.2 既存の決まりと検査 | Step 1、9 | `./gradlew verify` がすべて通る |
| FR5.1 既知の制約の記録 | Step 8 | README の差分 |
| FR6.1・FR6.2 配備した後の確認 | この段では行わない | Deployment Execution の段で行う |
| NFR1 欠落なし（同時 20 件まで） | Step 6 | `ConcurrentLoginAuditIT` |
| NFR2 資源の上限の内側 | この段では行わない | Deployment Execution の段（1g のコンテナで起動・スモークテスト・k6） |
| NFR3 品質の下限の維持 | Step 9 | JaCoCo の検証 |
| NFR4 秘密情報 | Step 8、9 | Gitleaks（`verify` に含まれる）、差分の確認 |

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
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。\n- 画面からの一連の操作を確かめるテスト（E2E）は、代表的な流れ1〜2本に絞って入れる（まずは「ログイン → 管理画面に入れるか → ログアウト」）。道具は設計のステージで決める。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。不安定なテストは放置せず、原因を直すまで統合しない。\n- 認証・認可・監査に関わる機能では、次のテストを必ず書く。★印の項目は要件（しきい値や動作）が未確定のため、要件定義で決めてからテストを書く。\n  - アカウントロック: 失敗回数のしきい値の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロック中は正しいパスワードでも拒否、ログイン成功時の失敗回数の扱い。★しきい値、回数を数える期間、ロックの解除方法\n  - ログイン: 存在しないユーザーとパスワード誤りで、応答からユーザーIDの存在を推測できないこと\n  - トークン: 有効期限の境界（直前は有効／直後は無効）、署名の改ざん、署名方式の指定を悪用した改ざん（`alg: none` 等）、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセストークンが有効期限まで使えること（決定済みの仕様として明示する）。★各トークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか\n  - 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い／不正なときの動作、パスワードがログに出ない。★設定が無いときに起動を止めるか\n  - 認可: 未認証（401）、管理者フラグなし（403）、管理者（200）をサーバー側のテストで確かめる（画面で管理メニューを隠すことはサーバー側の検査の代わりにしない）\n  - 監査ログ: 対象イベントごとに必須項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること。★監査ログの書き込みに失敗したときに操作を失敗させるか\n  - 秘密情報の漏えい: ログ・監査ログの出力にパスワード・トークンの値が含まれないこと\n  - 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる、外部エクスポートが既定で無効であること\n  - 画面: ログイン画面のアクセシビリティ検査、ロック時のメッセージ表示、ログアウトでトークンが破棄されること\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22)"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) \n- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) \n- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23)"
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
  "input_sha256": "sha256:6626a90c77121d61ba55eaf28a0dfa9477248ea963bb914113ff5c7781a3a0eb",
  "contract_sha256": "sha256:98d78fb8d68d765ddbc50e22418f499f6db08d1d49862a3846f96466b469b51c"
}
```

### Testing Contract の層の当てはめ

- データモデル・DB アクセス・業務処理・API・画面の層: 本番のコードの変更が無いため、該当しない。
- 設定（Environment/build configuration）: Step 5 で変え、Step 6・7 でテストする。
- 修正の前に再現テストを書いて実行する順序（Step 3・4）は、要件 FR2.1（修正の前の失敗の記録）と、bugfix の範囲の下限（不具合を再現する回帰テスト）による。方針自体は test-after のままであり、設定の変更（Step 5）の後にそのテスト（Step 6・7）を実行して通す。
