# コード生成の計画（Intent 260928-quality-followup）

scope bugfix・深さ Minimal・Test Strategy Minimal・単位の分割なし（Units Generation を持たない流れのため、成果物は `aidlc/spaces/default/intents/260928-quality-followup/construction/code-generation/` に置く）。

- 正本の要件: `aidlc/spaces/default/intents/260928-quality-followup/inception/requirements-analysis/requirements.md`（FR1〜FR6・NFR1〜NFR6・前提 A1〜A5・未決の点 O1〜O4）と、答えの記録 `requirements-analysis-questions.md`（Q1〜Q5・F1・F2、まとめの確認は Looks correct）。
- コードの事実: コード知識ベース `aidlc/spaces/default/codekb/mastersmith2/`（所見 K-1〜K-10）と、計画を書く時点（`develop` の `f7902e7`）でのソースの読み取り。
- 設計の段（Functional Design・NFR・Infrastructure Design）は無い。ストーリーも無いため、各手順は要件の ID（FR・NFR）に直接対応づける。
- この計画を書く担当は、アプリのコード・設定・README・サブモジュールを変えていない。以下の手順は、計画の承認の後に別の依頼で実行する。
- テストの道具・絞ったコマンド・カバレッジの目標・モックの方針・テストデータは `unit-test-instructions.md`（同じディレクトリ）に書く。

## 1. 要件の承認の場で受け入れた指摘の扱い

| 指摘 | 内容 | この計画での扱い |
|---|---|---|
| R-01 | FR6 で `POST /api/registration/verify` の拒否が監査に残るかを確かめていない | Step 4 で、README に書く前にソースと既存の結合テストで確かめる。計画を書く時点の読みでは、`RegistrationService.verify`（118〜132 行）は出来事を出さず、既存の `RegistrationApiIT`（「verify … is not audited」）と `InvitationAuditIT`（「verify … are not audited」）が「監査に残らない」を確かめている。Step 4 で読み直し、テストを流して結果を記録する |
| R-02 | FR3.4 の `team.md` の決まりとの差の記録 | Step 17 で、`code-summary.md` に節「team.md の決まりとの差（FR3.4）」を必ず書く。Build and Test でも同じ差を `test-results.md` に書くよう、7節で引き継ぐ |
| R-03 | Dependabot の本数（手元の参照で 15 本、前の記録で 11 件）の食い違い | Step 3 で、着手の時点に確かめ直す。`git fetch` は依頼者の了承を得てから行う |

## 2. 計画を書く時点で見つかった食い違い（計画の承認で確かめる）

- **`.page-link` は使われている（FR2.3 の前提と違う）**: 要件の FR2.3 とコード知識ベースの K-3 は「`frontend/src/app/pages/Page.css` の `.page-link` はどの画面からも使われていない」とするが、`frontend/src/app/pages/NotFoundPage.tsx` の 31 行が `className="page-link"` で使っている（U1 から。見つからない画面の「ホームへ」のリンク）。K-3 の検索が `.tsx` の中の文字列 `page-link` を見落としたと見られる。確定済みの要件とコード知識ベースは書き換えず、差を `code-summary.md` に記録する。扱いは 6節の Q3 で依頼者が決める。
- **画面の単体テストではコントラストを確かめられない**: 要件の FR2 の受け入れの基準は「vitest-axe か E2E」とするが、`frontend/vitest.config.ts` は `css: false` で jsdom は色を計算しないため、vitest-axe の `color-contrast` は判定できない。コントラストの確かめは実際のブラウザ（Playwright の E2E）で行う（6節の Q4）。
- **CI では結合テストの標準出力が見えない**: `backend/build.gradle.kts` の `testLogging` は `showStandardStreams = false`（`exceptionFormat` は FULL）で、CI は試験の報告を成果物として保存しない。FR3.3 の診断をログに出すだけでは、CI で落ちたときに読めない。そこで、診断の値をログに出すのに加えて、時間切れの失敗の知らせ（例外の文言）にも同じ値を入れる（Step 14。6節の Q5）。

## 3. 影響の範囲（変えるファイルと、それぞれの影響）

| ファイル | 変更 | 影響（Blast Radius） | 要件 |
|---|---|---|---|
| `vendor/make-you-chic-ui`（サブモジュールの固定先） | `735ef04` → `310e1ec` | 中。画面の部品の文字の色が変わる（Button・Badge・Avatar・Alert・FormField）。中身は変えない | FR1.1 |
| `frontend/e2e/support/axe.ts` | 既知の違反の一覧を実測に合わせて減らす。空になった専用の仕組み（アバター・誤りの文字）は外す | 低。E2E（`verify` と CI の外）だけ | FR1.2 |
| `frontend/e2e/050`〜`080-*.e2e.ts` | 既知の違反の説明のコメントと、外した仕組みの呼び出し | 低。E2E だけ | FR1.2 |
| `frontend/src/features/preferences/PreferencesForm.css` | `.preferences-choice-error` を `--color-danger-text` へ | 低。プリファレンスの画面の誤りの文字の色 | FR2.1 |
| `frontend/src/features/dsl/DslSubmitForm.css` | `.dsl-link` を文字用の色へ（候補 `--color-primary-subtle-text`） | 低。DSL の画面のリンクの色 | FR2.2 |
| `frontend/src/app/pages/Page.css`（と Q3 の答えによって `NotFoundPage.tsx`） | `.page-link` の扱い（推奨は残して文字用の色へ） | 低。見つからない画面のリンクの色 | FR2.3 |
| `frontend/e2e/100-app-text-contrast.e2e.ts`（新規） | アプリ独自の CSS の文字のコントラストの検査（流れではない） | 低。E2E だけ | FR2・NFR1 |
| `backend/src/test/java/cherry/mastersmith/config/H2CompactionByPoolSuspensionIT.java` | 0 本の待ちを 30 秒へ、時間切れの診断 | 低。テストのコードだけ（カバレッジの対象外） | FR3.1・FR3.3 |
| `frontend/src/features/invitation/InvitationAdminPage.test.tsx` | 1件だけ上限 15 秒、失敗したときの時間の診断 | 低。テストのコードだけ | FR3.2・FR3.3 |
| `backend/src/main/resources/application.yaml` | `management.metrics.distribution.slo` に2つの指標の境界 | 中。すべての要求の指標にバケットが付く（系列が増える。NFR3 で数を限る）。Java のコードは変えない | FR4.1・FR4.2 |
| `backend/src/test/java/cherry/mastersmith/common/observability/HistogramBucketsIT.java`（新規） | バケットの境界の結合テスト | 低。テストのコードだけ | FR4.1・FR4.2・NFR3 |
| `docker/monitoring/provisioning/alerting/mastersmith.yaml`・`docker/monitoring/dashboards/mastersmith-overview.json` | 実際の指標の名前と違うときだけ式を直す。O2 の答えでメールの送信の p95 のパネル | 低。手元の監視だけ | FR4.3・FR4.4 |
| `README.md` | 既知の制約（コントラスト）・E2E の一覧・手元の監視・新しい節「警報と対応の手順」 | 低。文書だけ | FR1.3・FR2・FR4.4・FR6.1 |
| `gradle/libs.versions.toml`・`backend/gradle.lockfile`・`gradle/wrapper/*`・`gradlew`・`gradlew.bat` | 取り込む Gradle の更新 | 中。依存の版（Step 19 の判定を通ったものだけ） | FR5.1〜FR5.2 |
| `frontend/package.json`・`frontend/package-lock.json` | 取り込む npm の更新 | 中。画面のビルドと検査の道具（判定を通ったものだけ） | FR5.1 |
| `compose.yaml`・`backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java` | 取り込むイメージの版と digest | 中。見本の対象DB と対象DB の結合テスト・手元の監視 | FR5.1〜FR5.3 |
| `.github/dependabot.yml` | O1 の答えが A のときだけ、見送る更新の `ignore` | 低。更新の知らせの設定だけ | FR5.4・O1 |

変えないもの: `vendor/` の中身、`backend/src/main/java` の Java のコード（バケットは設定だけで済ませ、`packagesJudgedByTotal` のパッケージに手を入れない。設定だけで済まないと分かったら、Java を足す前に止めて依頼者に確かめる）、カバレッジの下限と除外、SpotBugs・OSV-Scanner・Gitleaks の関門、既存の ArchUnit の境界テスト、`.github/workflows/ci.yml`、`Dockerfile`、`docker/hikari-pool.sh`、前の Intent の承認済みの記録。

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
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) \n- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) \n- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) \n- 同時の重なりを確実に作るため、本番のコードを変えずに、監査の書き込みの時間を測る LongSupplier（AuditEventListener で2本目を借りる直前に呼ばれる）をテストで差し替えて待ち合わせる方式にした。既存の LoginConcurrencyIT は 8 スレッドでプールの 10 に届かず、前回の失敗のログインで尽きなかった理由の1つと見られる。 (learned 2026-09-23) \n- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) \n- 修正の前の設定（例: 上限 1g）も修正の後の環境（例: CPU 4 の VM）で流し（pre1g）、要件の前提（VM を上げても F3 が起きる）を実測で裏付ける。 (learned 2026-09-23) \n- パッケージごとのカバレッジの下限と SpotBugs の SQL_ の関門を U1 の計画に入れた。team.md の決まりだが今のビルドに無く、この Intent で最初に作る単位のため。U1 の設計の文書には無い作業。 (learned 2026-09-24) \n- U4 で既存の AuditSecretLeakIT の列の一覧に V6 の4列を足し、テストの JVM のヒープを 1g にした。前者は承認済みの V6 と必ず食い違うため、後者は構造の検査がクラスを持ち続け U3 の 10MB 超えのテストでヒープが尽きたため。どちらも計画に無い変更で、依頼者に確かめる。 (learned 2026-09-24) \n- 応答しない対象DB の TIMEOUT の確かめに、テストの中で開いた ServerSocket（受け付けて何も返さない）を使う。外の端末に頼らず確実に再現できる代わりに、本物の DB の遅延ではない。 (learned 2026-09-24) \n- パッケージごとのカバレッジの下限は新しいパッケージだけに当てた。実測で audit.service（行 77.2%）・common.health（行 79.2%）・auth.repository（分岐 50.0%）が単独で下回ったため（team.md の決まりどおり）。既存の 22 パッケージを一覧で外し、新しいパッケージは自動で対象になる。 (learned 2026-09-24) \n- 負荷の試験で接続プールが尽きたかを確かめるときは、使い捨てのアプリにだけ MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics を渡して /actuator/metrics の hikaricp の値を読み、数秒ごとの使用中の数ではなく、待ちの時間切れの累計と借りるまでの待ちの最大で判断する（配備したアプリの公開の範囲は変えない）。 (learned 2026-09-24) \n- k6 などの長い試験は caffeinate -i を付けて流し、PC の自動のスリープで要求が止まって結果が崩れるのを防ぐ（バッテリー駆動のまま 414 秒スリープし、要求が 6分52秒止まった）。内部DB に SQL で直接入れた試験用の利用者はロックの状態の行が無いため、同時のログインを流す前に1人ずつログインさせて行を作る。 (learned 2026-09-24) \n- 軽い API の性能は、投入を重ねて内部DB のファイルが膨らんだ状態（悪い側の条件）のまま測る。メモリの最大（memory.peak）を比べる試験の前は、アプリのコンテナを作り直して前の最大の値を消す。 (learned 2026-09-24) \n- Performance Validation の段が無いため、2件目の直しの負荷の試験での確かめ（Q5: A）の持ち主を Build and Test とした（project.md の学びどおり）。 (learned 2026-09-24) \n- colima の PC で ./gradlew verify を流すときは、README の DOCKER_HOST と TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE をシェルに渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗した。付けて流し直した結果を基準とした。 (learned 2026-09-25) \n- unit-test-instructions.md の k6 inspect のコマンドは --include-system-env-vars が無いと場面の名前が undefined になり確かめにならなかった。承認済みの文書は変えず、実際には付けて流し、code-summary.md に差を記録した。 (learned 2026-09-25) \n- 1回目の verify で AccessTokenApiIT の 6 件が接続の失敗で落ちた。変更の経路に触れず、同じ時刻に Gradle の作業プロセスとの接続も時間切れだったため PC の負荷による一時的な失敗と見立て、段の中の直しの1回目としてコードを変えずに verify を流し直して通した。原因は確かめていない。 (learned 2026-09-25) \n- 依頼者の判断で、試験のイメージを mastersmith:local ではなく mastersmith:followup-fixes で作り、perf/README の手順（local を作り直す）から外れた。配備したアプリは k6 のあいだ止め、約 17 分後に同じコンテナで起動し直した。 (learned 2026-09-25) \n- 試験の後にロックの状態の行の数を数える問い合わせの列の名前を誤り、使い捨ての環境を消した後で取り直せなかった。合格の条件（ログインの checks と 500 の件数）で判定した。消す前に確かめの結果を見てから片付けるべきだった。 (learned 2026-09-25) \n- Q4（確かめられたときだけ直す）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違いうるため、追加の質問 F3 で、再現できなければ不安定と確かめられていない扱いとして統合してよいことを確かめた。 (learned 2026-09-25) \n- 要件 FR2.2（目標は計画の承認の場で決める）を満たすため、依頼者の決定（Q1: A）で、計画を書く前に dslMixed でメモリの内訳を測ることにした。測る間は配備したアプリを止める。 (learned 2026-09-25) \n- 生成で、テストの既定で MBean の登録を無効にする置き場を、計画の候補の TestDatabase ではなくテストの EnvironmentPostProcessor にした。TestDatabase を使わずに Spring を起動するテストのクラスが 15 あり、漏れるため。再現の結合テストは 12 回では伸びが小さく判定がはっきりしないため、24 回・履歴の上限 3 にした。 (learned 2026-09-25) \n- AccessTokenApiIT は、同じ例外の文言を仕組み（colima の IPv4 の転送と Java の IPv6 の待ち受けの番号の重なり）ごと再現できたことを原因の確認とみなし、依頼者の決定（D7）でテストの JVM に preferIPv4Stack を付けて直した。実際の1回目がこれで起きたかは確かめられず、今回の繰り返しでは重なりは起きなかった。 (learned 2026-09-25) \n- Test Strategy は Minimal だが、要件 FR5 と計画の「Build and Test に引き継ぐこと」のため、結合・性能・セキュリティの手順書も作り、この段で dslMixed・--storage --compact（40 回）・詰め直しの最中のログイン・refresh・dslCycle を流した。 (learned 2026-09-25) \n- 計画の dslCycle を最初の台本に入れ忘れ、配備したアプリをもう一度（約 4 分）止めて流した。負荷の試験の台本を書く前に、計画の「Build and Test に引き継ぐこと」の項目を一つずつ台本の手順と突き合わせる。 (learned 2026-09-25) \n- 実際のブラウザのアクセシビリティの検査（axe）は、誤りを出した状態と、現実に近いデータ（2語の氏名など）で、ブランドカラーとテーマのすべての組について行う。user-management の U7 の 080 で初めて、make-you-chic-ui の Avatar（頭文字が2文字のとき）と dark の FormField の誤りの文字のコントラスト不足が出た（050〜070 は誤りの状態を見ず、氏名が1語だったため出なかった）。 (learned 2026-09-28) \n- Playwright の webServer.env に置いた値は json の報告に残る。E2E の仮の資格情報はプロセスの環境変数で渡し、報告の部品の確かめと json の文字列の検索で、パスワード・トークン・メールアドレスが入っていないことを確かめる（user-management の U4 で見つかった）。 (learned 2026-09-28) \n- CI（66fe981）の verify が2回とも別々のテストの時間切れ（H2CompactionByPoolSuspensionIT の接続の待ち 10 秒、InvitationAdminPage.test.tsx の既定 5 秒）で失敗し、U4〜U7 の NFR7.1 が Not Met だったが、依頼者の決定「次のintentでコントラストと一緒に直す。このintentではこのまま進める。」で受け入れた失敗とした。team.md の「CI が失敗したら次に進む前に直す」と食い違うため test-results.md 8.1 に差を記録した。 (learned 2026-09-28) \n- k6 の登録の完了と取り消しの場面は、決定の文言（招待を VU の数だけ）ではなく既定 100 回・招待 100 件とした。トークンは1回しか使えず 10 件では p95 の意味が薄いため、U3 の NFR6.4 の「流す回数以上の招待を用意する」に合わせた。承認の場で確かめる。 (learned 2026-09-28) \n- caffeinate -i は場面ごとではなく、試験の台本全体を包む形で付ける。場面ごとに起こし直すと切れ目で守りが外れ、PC が眠る（user-management の Performance Validation で 92 秒眠り、要求が 1 分 29 秒遅れた）。遅れが出たときは pmset -g log でスリープを確かめる。 (learned 2026-09-28) \n- 応答しないメールの受け手は、Mailpit を docker pause で一時停止して作る（接続は受け付け、何も返さない）。コンテナを止めると接続の失敗になり、時間切れの確かめにならない（user-management の Performance Validation）。 (learned 2026-09-28) \n- Q1 で原因を確かめずに上限を延ばす答え（C）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違うため追加の質問 F1 で確かめ、延ばしたうえで失敗時の診断を足し、差を記録する形（B）になった。Q3 の「すべて試す」は verify で判定できない Temurin 26・logback-appender の扱いを F2 で確かめ、見送り（C）になった。 (learned 2026-09-29)"
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
  "input_sha256": "sha256:6c56b45bf082f0a76c28828ca569bc665aae7d258541f17435beb72c6366943e",
  "contract_sha256": "sha256:6ad64aa39e203075b71f9ae94d166309dd17aebee4f86cd7399015ea81c0d2af"
}
```

methodology は test-after。順序は contract の `plan_profile.steps` に従い、層ごとに実装してから、その層のテストを書いて流し、通ってから次の層へ進む。この Intent に当てはまる層と読み替えは次のとおり。

| plan_profile の段 | この Intent での読み替え | 手順 |
|---|---|---|
| Project structure and production configuration skeleton | 作業ブランチ・前提の確かめ・基準の測定 | Step 1・Step 3・Step 4 |
| Verify the existing test runner/configuration and record the exact unit-scoped command | 既存の Gradle・Vitest・Playwright の実行の確かめと、絞ったコマンドの記録 | Step 2 |
| Data model / database behavior | 当てはまらない（スキーマの変更なし） | なし |
| Repository / data access | 当てはまらない | なし |
| Business logic | 当てはまらない（Java の業務のコードの変更なし）。代わりに、テストの側の直し（FR3）をこの位置で「直してから流す」 | Step 14〜Step 17 |
| API / endpoint | 観測の設定（`application.yaml` の指標のバケット） | Step 5〜Step 8 |
| Frontend behavior | サブモジュールの更新とアプリ独自の CSS | Step 9〜Step 13 |
| Environment/build configuration | 依存の更新（Dependabot） | Step 18〜Step 23 |
| Documentation and traceability | README の新しい節・最後の検査・記録 | Step 24〜Step 28 |

実際の実行の順は Step の番号どおりとする。FR3（テストの直し）を画面の後に置くのは、Step 9〜Step 13 の E2E の実行と独立で、Step 24 の最後の `verify` の前に済めばよいため。

## 4. 手順

### 準備

- [x] **Step 1: 作業ブランチと前提の確かめ**（FR1.1・NFR6）
  - `develop`（`f7902e7` 以降の最新）から短命のブランチ `fix/260928-quality-followup` を作る（名前は 6節の Q6 で確かめる）。
  - アプリのソースに未コミットの変更が無いことを `git status` で確かめる（ワークフローの記録のディレクトリは外して判断する）。
  - `git submodule status` で `vendor/make-you-chic-ui` が `735ef04`、`vendor/java-mustache-processor` が `8d44c36` であることを記録する。
  - colima が動いていることを確かめ、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡す（`project.md` の Testing Posture）。`.env`・鍵ファイル・`reference/` は開かない。

- [x] **Step 2: テストの実行の確かめと基準の測定**（NFR4・NFR6）
  - 既存の実行の仕組み（`./gradlew :backend:test`・`:backend:integrationTest`、`frontend` の Vitest、`./gradlew e2eTest` と Playwright）を変えずに使う。`unit-test-instructions.md` の絞ったコマンドが動くことを、変更の前に1回ずつ流して確かめる。
  - 基準として、変更の前の `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を流し、テストの件数（単体・結合・画面）・SKIPPED の件数・カバレッジ（全体の合計）を記録する。対象DB のテストが SKIPPED になったら環境変数を直して流し直す。
  - 変更の前の `./gradlew e2eTest`（Mailpit を起動して）を流し、050〜090 が今の既知の違反の一覧で通ることを記録する（Step 10 の比べる元）。

- [x] **Step 3: Dependabot の本数の確かめ直し**（FR5.1・FR5.5・R-03）
  - 依頼者に了承を得てから `git fetch origin --prune` を行い、`git branch -r` で `origin/dependabot/*` を数える。GitHub で開いているプルリクエストの数は、依頼者に画面で確かめてもらう（AI は `gh` でプルリクエストを操作しない）。
  - 計画を書く時点の手元の参照は 15 本（8節の一覧）。増減があれば一覧に反映し、取得の後に増えた更新は、依頼者に確かめたうえで Step 18 の同じ判定で扱う。
  - 各ブランチの元のコミット（古い `develop` から作られているもの）と、変わるファイルを記録する。ブランチはそのまま統合せず、版の変更だけを手元で当て直す（Step 18）。

- [x] **Step 4: README に書く内容のソースでの確かめ**（FR6.3・R-01）
  - 登録の完了の拒否: `RegistrationService.complete`（152〜159 行）が形の誤ったトークンでも `RegistrationFailedEvent` を出し、`AuditEventListener`（208 行）が `REGISTRATION_FAILED` を記録すること、応答が 404 `REGISTRATION_LINK_INVALID` であること、アプリのログのレベルを読む。
  - リンクの確かめの拒否（R-01）: `RegistrationService.verify` と `RegistrationController` を読み、出来事を出すか・応答・ログのレベルを確かめる。既存の `RegistrationApiIT`・`InvitationAuditIT` の該当のテストを流し、「監査に残らない」を実行で裏付ける。
  - 招待メールの送信の失敗: 招待が 201・送り直しが 200 で `sendResult: FAILED` を返すこと、ログのレベル（WARN・INFO）、`AuditEventType` に当たる種類があるかを、ソース（`InvitationAdminController`・招待の service・`SmtpMailSender`）で確かめる。警報 `ms-5xx-ratio`・`ms-error-logs` の式（`docker/monitoring/provisioning/alerting/mastersmith.yaml`）がどちらも拾わないことを式で確かめる。
  - 結果（確かめた場所・行・テストの名前）を `code-summary.md` に記録する。前の Intent の記録（`alarms.md`・`log-queries.md`・`runbooks.md` の RB-17）は書き換えない。

### 観測の設定（API の層）

- [x] **Step 5: 指標のバケットの設定を書く**（FR4.1・FR4.2・NFR3・NFR5）
  - `backend/src/main/resources/application.yaml` の `management.metrics` に `distribution.slo` を足す。`http.server.requests` は `100ms,250ms,500ms,1000ms,2000ms,5000ms`、`mastersmith.mail.send` は `100ms,250ms,500ms,1000ms,2000ms,5000ms,10000ms`。`percentiles-histogram` は使わない（既定のバケットを出さない）。
  - 日本語のコメントで、境界の理由（警報のしきい値 1000 ms を含む、メールは送信の時間切れを含む 10000 ms まで）と、系列の数を絞った理由を書く。
  - 指標の窓口の公開の範囲（`management.endpoints.web.exposure.include: health`）は変えない。
  - 設定だけで当たらない（名前の前方一致で当たらない等）と分かったら、`MeterFilter` などの Java を足す前に止め、`packagesJudgedByTotal`（`common.observability` を含む）の作業が付くことと合わせて依頼者に確かめる。

- [x] **Step 6: バケットの結合テストを書いて流す**（FR4.1・FR4.2・NFR3・NFR5）
  - 新しい `backend/src/test/java/cherry/mastersmith/common/observability/HistogramBucketsIT.java`（Apache License 2.0 のヘッダー、説明文は英語）。
    - `POST /api/auth/login` へ実際に要求を送り（誤ったパスワードで 401。テストの内部DB の中だけ）、`MeterRegistry` の `http.server.requests`（`uri=/api/auth/login`）の `takeSnapshot().histogramCounts()` の境界が 100・250・500・1000・2000・5000 ms のちょうど6つであること。
    - `mastersmith.mail.send` の名前の Timer（登録先の `MeterFilter` を通るもの）の境界が 7 つ（〜10000 ms）であること。
    - 既定のバケット（`percentiles-histogram`）が出ていない（境界の数がちょうど決めた数）こと。
  - 公開の範囲が変わっていないことは、既存の `cherry.mastersmith.config.ExposureIT` を同時に流して確かめる。
  - `unit-test-instructions.md` の絞ったコマンドで流し、通ることを確かめる。

- [x] **Step 7: 手元の監視で実際の名前と式を確かめる**（FR4.3・FR4.4・NFR3・NFR5・O2。あわせて FR5.1 の `grafana/otel-lgtm` 0.34.0 の試し）
  - 送る前に依頼者に伝える: 確かめの要求（ログイン・トークンの更新・管理者の確認・招待）は監査ログに残るため、配備したアプリではなく使い捨ての環境（`perf/README.md` の手順。仮の署名鍵・仮の利用者、終わったら消す）で行う。colima の VM のメモリのため、配備したアプリをこの間止めるかを依頼者に確かめる（6節の Q8）。
  - 外部エクスポートを使い捨てのアプリにだけ有効にし、`lgtm` を起動する。`compose.yaml` の `grafana/otel-lgtm` を 0.34.0 にした状態で行い、下の確かめがすべて通れば 0.34.0 を取り込む（Step 22 の一覧に記録）。
  - 書く前の確かめ: Prometheus で、`http.server.requests` と `mastersmith.mail.send` の実際の名前（例 `http_server_requests_milliseconds_bucket`・`mastersmith_mail_send_milliseconds_bucket`）と `le` の値が、決めた境界と `+Inf` だけであることを確かめる（`project.md` の Corrections）。
  - 要求は、送信の周期（1 分）を複数またいで（3 分以上）くり返し送る。招待はメールを Mailpit へ送る（外部の SMTP には送らない）。
  - 警報 `ms-login-p95`・`ms-refresh-p95`・`ms-check-p95` の式と、ダッシュボードの3つの p95 のパネルの式を流し、NaN ではない値を返すことを確かめる。名前が違えば、警報とダッシュボードの式を実際の名前に直し、直した後にすべての式を流し直す。
  - O2 の答えが A のとき、メールの送信の p95 のパネルの式を `traces_spanmetrics_latency_bucket` から `mastersmith.mail.send` のバケットに替え、流して値を確かめる。
  - 確かめの後、使い捨ての環境を消す前に、確かめの結果（式ごとの値・名前・`le` の一覧）を見てから片付ける（`project.md` の学び）。止めた配備したアプリは起動し直し、健全になることを確かめる。

- [x] **Step 8: README の手元の監視の記述を直す**（FR4.4）
  - `README.md` の「手元の監視（Grafana）」の「既知の欠け。次の Intent でアプリの設定で直す」の段落を、今の実際（バケットの境界、p95 の警報3件が値を持つこと、O2 の答えによるメールの送信のパネルの作り）に合わせて直す。

### 画面の層

- [x] **Step 9: make-you-chic-ui の固定先を上げる**（FR1.1・A5）
  - `vendor/make-you-chic-ui` を `310e1ec` にする（間に `7865c28` を含む一直線の履歴。手元にあることは計画の時点で確かめた）。中身は変えない。
  - `./gradlew verifyPrepare`（`vendorInstall`・`vendorBuild`・`vendorUnchanged`）を流し、`frontend/package-lock.json` が書き換わらないこと（A5）を `git diff` で確かめる。書き換わったら理由を確かめ、`code-summary.md` に記録する。
  - コミットは C1（専用。前後のハッシュ `735ef04` → `310e1ec` をメッセージに書く）として提案する。

- [x] **Step 10: E2E を流して既知の違反の一覧を合わせる**（FR1.2・NFR1）
  - `./gradlew e2eTest`（Mailpit を起動して）で 050〜090 を流し、Step 2 の結果と比べて、消えた既知の違反を組・状態ごとに記録する。
  - `frontend/e2e/support/axe.ts` の `KNOWN_VIOLATIONS`・`INVITATION_KNOWN_VIOLATIONS`・`REGISTRATION_KNOWN_VIOLATIONS`・`PREFERENCES_KNOWN_VIOLATIONS`・`AVATAR_KNOWN_COMBOS`・FormField の誤りの文字の扱いを、実測に合わせる。消えたものは一覧から除く。一覧が空になった専用の仕組み（`withAvatarKnownViolation`・`withFormFieldErrorKnownViolation` と、その呼び出し）は外し、使われない関数を残さない。`splitKnownViolations` の汎用の仕組みは残す。
  - 050〜080 のファイルの冒頭のコメントと失敗の知らせの文言を、今の一覧に合わせる。
  - 違反が残る組があれば、一覧に残し、理由（make-you-chic-ui のどの部品の、どのトークンか）を `code-summary.md` に記録する。
  - 050〜090 がすべて通るまで流し直す。

- [x] **Step 11: アプリ独自の CSS の文字の色を直す**（FR2.1・FR2.2・FR2.3・NFR1）
  - `.preferences-choice-error` を `color: var(--color-danger-text)` にする。
  - `.dsl-link` を文字用の色にする。候補は `--color-primary-subtle-text`（ライトは brand-700、ダークは brand-400）。Step 12 の検査で満たさない組があれば `--color-text` に替え、選んだ理由を記録する。
  - `.page-link` は 6節の Q3 の答えに従う（推奨 A: 残して `.dsl-link` と同じ文字用の色にする）。
  - Stylelint（`frontend` の `lint:css`）と、関係する画面の単体テスト（`PreferencesForm`・`PreferencesPage`・`DslSubmitForm`・`NotFoundPage`）を絞ったコマンドで流す。

- [x] **Step 12: アプリ独自の CSS のコントラストの E2E を書いて流す**（FR2・NFR1・NFR6）
  - 新しい `frontend/e2e/100-app-text-contrast.e2e.ts`（Apache License 2.0 のヘッダー、説明文は英語、コメントは日本語）。流れの E2E ではないため、`team.md` の「Intent ごとに代表の流れを1本まで」の本数に数えない（`project.md` の Corrections の読み方）。
  - 表示の設定の 20 組（`frontend/e2e/support/displayCombos.ts` の `DISPLAY_COMBOS`）ごとに、次の状態で axe（`runAxe`）を流し、`color-contrast` を含む違反が無いことを確かめる。
    - プリファレンスの画面で、サーバーが選択のまとまりの誤りを返した状態（`.preferences-choice-error` が出る）。`PUT /api/me/preferences` だけを 400 の Problem Details（項目の誤り付き）に差し替える。差し替えの見本は1か所にまとめて画面の側の型を付ける（`project.md` の Corrections）。形は `frontend/src/features/preferences/` の API の型と、バックエンドの誤りの応答の形で確かめてから書く。
    - DSL の管理の画面の投入の欄（`.dsl-link` が出る）。
    - 見つからない画面（Q3 で `.page-link` を残すとき。`not-found-home-link` が出る）。
  - 初期管理者でログインする（`frontend/e2e/support/adminLogin.ts`）。サーバーの状態を変える要求（保存・投入）は送らない。題・注記・添付に、メールアドレス・パスワード・トークンを入れない。
  - 対象の要素が画面に出ていることを確かめてから検査する（出ていなければ失敗にする）。
  - Playwright で 100 だけを流して通ることを確かめる（`unit-test-instructions.md`）。

- [x] **Step 13: README の画面の記述を直す**（FR1.3・FR2）
  - 「画面の表示の設定（U4）」の「既知の制約（ブランドカラーのコントラスト）」の節を、更新の後の実測に合わせて書き直す（解消した組、残った組があればその理由）。表のコントラスト比は、make-you-chic-ui の更新の後の実際の値で書く。
  - 「ビルドした WAR での画面の確認（E2E）」の表に 100 を足し、050〜080 の説明の既知の違反の記述を今の一覧に合わせる。
  - `.preferences-choice-error` の色の記述（949 行付近）を直す。

### テストの直し（FR3）

- [x] **Step 14: 接続が 0 本になるまでの待ちを延ばし、診断を足す**（FR3.1・FR3.3・NFR5）
  - `H2CompactionByPoolSuspensionIT` の `ZERO_CONNECTIONS_WAIT` を 10 秒から 30 秒にする。同じ定数を使う4か所（196・235・251・264 行）すべてに当たる（どれも CI の速さに左右される同じ種類の待ちのため。6節の Q5）。コメントに、運用の道具（`docker/hikari-pool.sh` の `--zero-wait` の既定 10 秒）とテストの上限が違うことと、その理由（FR3、原因は確かめていない）を書く。道具の既定は変えない。
  - プールの接続の数を待つ3か所（196・251・264 行）を、時間切れのときに診断を出す小さな補助（テストのクラスの中の private のメソッド）に通す。Awaitility の `ConditionTimeoutException` を受けたら、`HikariPoolMXBean` の `getTotalConnections`・`getActiveConnections`・`getIdleConnections`・`getThreadsAwaitingConnection` をキーと値で ERROR のログに出し、同じ値を入れた `AssertionError`（元の例外を原因に付ける）を投げ直す。例外を握りつぶさない。
  - 出す値は数だけ（NFR5）。接続の URL・利用者・SQL・スレッドの名前は出さない。

- [x] **Step 15: 接続の待ちのテストを流す**（FR3.1・FR3.3）
  - `H2CompactionByPoolSuspensionIT` を絞ったコマンドで流し、3件が通ることを確かめる。
  - 診断がわざと時間切れのときに出ることの確かめは、コードに残さない形で Build and Test で1回だけ行う（要件の FR3 の受け入れの基準。7節）。

- [x] **Step 16: 招待の画面のテストの1件の上限を延ばし、診断を足す**（FR3.2・FR3.3・NFR5）
  - `InvitationAdminPage.test.tsx` の「keeps addresses and names out of storage, the URL and the console in every flow」だけ、`it` の第3引数で上限を 15 秒（`15_000`）にする。ほかのテストと `vitest.config.ts` の既定（5 秒）は変えない。
  - テストの始めに時刻を取り、Vitest の `onTestFailed` で失敗したときにかかった時間（ミリ秒）を出す。このテストは `console` の各メソッドを見張って何も出ないことを確かめるため、出力は `console` ではなく `process.stderr.write` で行う。出すのは時間とテストの名前だけ（メールアドレス・氏名は出さない）。
  - 絞ったコマンドでこのファイルを流し、通ることを確かめる。

- [x] **Step 17: team.md の決まりとの差を記録する準備**（FR3.4・R-02・A1）
  - `code-summary.md` に節「team.md の決まりとの差（FR3.4）」を必ず書く。中身: 対象の決まり2つ（「不安定なテストは原因を直すまで統合しない」「CI が失敗したら次に進む前に原因を直す」）、今回の判断（原因は確かめず、上限を延ばすことを直しとして統合する。F1: B）、前提 A1、直さない原因の候補（K-4 の仮説 a〜c、K-5 の見立て）、次に落ちたときの手がかり（Step 14・Step 16 の診断）。
  - Build and Test でも同じ差を書くことを、7節に引き継ぐ。

### 依存の更新（Dependabot）

- [x] **Step 18: 更新ごとの試し方と判定の基準を決めて当てる**（FR5.1〜FR5.5・NFR4）
  - ブランチはそのまま統合しない。各ブランチの版の変更だけを、`fix/260928-quality-followup` の上に手元で当て直す（`team.md` の受け方）。lockfile は手元で作り直す（Gradle は `./gradlew :backend:dependencies --write-locks`、npm は `frontend` で版を指定した `npm install` の後に `npm ci` で入ることを確かめる）。
  - 判定の区分（一覧の「判定」の値）:
    - **取り込み**: 下の関門をすべて通った。
    - **見送り**: 要件で見送りと決めた（Temurin 26・logback-appender 2.31.1-alpha。FR5.4）。
    - **通らずに見送り**: 試したが関門を通らなかった。当て直した変更を元に戻し、通らなかった段と理由（エラーの要点）を記録する。
  - 関門（NFR4）: `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` が通る（対象DB のテストを含む。SKIPPED が無い）。カバレッジの下限（全体の合計とパッケージごと）を下回らない。OSV-Scanner に新しい High 以上が無い。SpotBugs・Gitleaks が通る。画面の道具（Vite・TypeScript・Vitest・Prettier）の更新は、あわせて `./gradlew e2eTest` を流す（Step 24 でまとめて流してよい）。
  - 試す単位: 小さな更新（archunit・snakeyaml・Gradle wrapper・prettier・vite・postgres の digest）は同じ生態系ごとにまとめて1回の `verify` で試してよい。落ちたら1つずつに分けて原因の更新を突き止める。大きな更新と決まりとぶつかる更新（networknt 3.0.7・typescript 7・vitest 5 の組・mysql・mariadb）は1つずつ試す。`vitest` と `@vitest/coverage-v8` は同じ大きな版のため、1組として試す（2本のブランチを1つの判定にする）。
  - 決まりとぶつかる更新は、関門を通った場合に限り、決まりを変えて取り込む（FR5.2）。変えた決まり・理由・確かめた結果を `code-summary.md` に書き、学びの手順で `project.md` に反映するかを依頼者に確かめる（メモリは直接書き換えない）。

- [x] **Step 19: Gradle の更新を試す**（FR5.1・FR5.2）
  - archunit 1.5.1・snakeyaml 2.7: `gradle/libs.versions.toml` の版を上げ、lockfile を作り直して試す。
  - Gradle wrapper 9.8.0: `./gradlew wrapper --gradle-version 9.8.0` で `gradle-wrapper.properties`・`gradle-wrapper.jar`・`gradlew`・`gradlew.bat` を作り直し（今の `gradle-wrapper.properties` の書き方をそろえる）、試す。
  - networknt-json-schema-validator 3.0.7: 版を上げ、依存の木（`./gradlew :backend:dependencyInsight --dependency jackson` など）で Jackson の版が Spring Boot の管理の版から変わるかを確かめて記録する。関門を通れば取り込み、`project.md` の学び（3.0.6 を選んだ理由）を変える候補として記録する。
  - opentelemetry-logback-appender 2.31.1-alpha は試さず見送る（FR5.4。理由は Step 22）。

- [x] **Step 20: npm の更新を試す**（FR5.1）
  - prettier 3.9.9・vite 8.3.1: 版を上げて試す。prettier の上げでフォーマットが変わったら、フォーマットの結果の差だけを同じ更新に含める。
  - typescript 7.0.2: 版を上げて試す。型の検査（`typecheck`）・ESLint・oxlint・ビルドが通らなければ、コードを直さずに「通らずに見送り」とする（大きな版の移行はこの Intent の範囲の外）。
  - vitest 5.0.2 と @vitest/coverage-v8 5.0.2: 1組で試す。設定（`vitest.config.ts`・`vitest.setup.ts`）の書き換えが要るときは、下限（`thresholds` の 80・70）を変えない範囲の書き換えだけにし、書き換えが大きい・下限の意味が変わるときは「通らずに見送り」として依頼者に確かめる。
  - `config/npm-build-tools.txt` の道具（Vite・TypeScript など）の High 以上の脆弱性は統合を止める（`team.md` の Deployment）。

- [x] **Step 21: 対象DB のイメージの更新を試す**（FR5.1〜FR5.3・NFR4）
  - postgres 18.6 の digest・mysql 26.7.0・mariadb 13.0.2: Dependabot のブランチの `compose.yaml` の差から新しい版と digest を読み、`compose.yaml` と `TargetDbImages` の `*_VERSION`・`*_DIGEST` を一緒に上げる（`dependabot.yml` のコメントの決まり）。
  - 1つずつ、対象DB の結合テスト（`cherry.mastersmith.targetdb` のパッケージ）を絞ったコマンドで流し、通ったら `verify` で確かめる。
  - mysql・mariadb は `TargetDbImages` の Javadoc の「長く支援される版（8.4・11.8 の系列）」の決まりから外れる。取り込むときは Javadoc の記述を今の版に合わせて直し、決まりを変えたことを `code-summary.md` に記録する（FR5.2）。見本の対象DB（compose の profile）での起動の確かめは、結合テストで代える。

- [x] **Step 22: 見送る更新と、`dependabot.yml` の扱い**（FR5.4・FR5.5・O1）
  - eclipse-temurin 26: ビルドと CI の JDK 25（`gradle/libs.versions.toml` の `java = "25"`、`ci.yml` の `java-version: "25"`）と版が分かれ、`verify` はイメージを作らないため確かめられない（F2: C）。
  - opentelemetry-logback-appender 2.31.1-alpha: 2.28.1-alpha に固定した理由（Spring Boot 4.1.1 の OpenTelemetry 1.62 との食い違い、外部エクスポートを有効にしたときの失敗）は、外部エクスポートが既定で無効の `verify` では現れないため確かめられない（F2: C、`project.md` の Tech Stack）。
  - O1 の答えが A のとき、`.github/dependabot.yml` に `ignore` を足す（eclipse-temurin の大きな版の更新、opentelemetry-logback-appender のすべての更新）。コメントに、外す時期（JDK をビルド・CI とそろえて上げるとき、Spring Boot を上げるとき）を書く。「通らずに見送り」の更新は `ignore` に入れない（次の版で直りうるため）。

- [x] **Step 23: 更新の一覧を作る**（FR5.5）
  - 更新（ブランチ）ごとに1行で、判定（取り込み・見送り・通らずに見送り）と理由・確かめた関門を `code-summary.md` に一覧にする。Step 3 で確かめ直した本数と、GitHub で開いているプルリクエストの数の差も書く。
  - プルリクエストを閉じる操作と `git push` は依頼者が行う。一覧はその手がかりにする。

### 文書・最後の検査・記録

- [x] **Step 24: README に「警報と対応の手順」の節を足す**（FR6.1・FR6.2・FR6.3）
  - Step 4 で確かめた内容だけを書く。
    - 登録の完了（`POST /api/registration/complete`）で拒否されたときは、形の誤ったトークンでも監査に `REGISTRATION_FAILED` が残る。応答は 404 `REGISTRATION_LINK_INVALID` で、5xx の割合の警報には当たらない。
    - リンクの確かめ（`POST /api/registration/verify`）の拒否は、Step 4 の結果（計画の時点の読みでは監査に残らない）と、確かめた方法。
    - 招待メールの送信の失敗（招待は 201、送り直しは 200 で `sendResult: FAILED`）は、既存の警報（5xx の割合・ERROR のログ）に当たらない。気づく方法（画面の表示・WARN のログの問い合わせ・ダッシュボードの送信の行）。
    - p95 の警報3件の意味と、鳴ったときに見る場所（Step 7 の結果に合わせる）。
  - 前の Intent の記録のどの記述をどう正したか（`alarms.md`・`log-queries.md`・`runbooks.md` の RB-17）の差は、README ではなく `code-summary.md` に書く（FR6.2）。

- [x] **Step 25: 最後の検査を流す**（NFR1〜NFR6・FR5 の受け入れの基準）
  - すべての変更（取り込んだ更新を含む）を入れた状態で、`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を流す（colima と環境変数あり、対象DB のテストが SKIPPED でないこと）。テストの件数・カバレッジ（全体の合計）を Step 2 と比べて記録する。
  - `./gradlew e2eTest`（Mailpit を起動して）で 050〜100 を流し、すべて通ることを確かめる。E2E の後、json の結果にパスワード・トークン・メールアドレスが入っていないこと（`playwright-secret-check-reporter.ts` の結果）を確かめる。
  - 1回目に落ちたときは、原因の見立てを記録してから直す。コードを変えずに流し直すときは、その理由を記録する。

- [x] **Step 26: 記録を作る**（すべての要件）
  - `code-summary.md`: 変えたファイル、Step 2 と Step 25 の実測、既知の違反の一覧の変化（Step 10）、選んだ色と理由（Step 11）、Step 4 の確かめ、節「team.md の決まりとの差（FR3.4）」（Step 17）、FR6.2 の前の記録との差、依存の更新の一覧（Step 23）と変えた決まり、計画との差、「依頼者に確かめたいこと」の節。
  - `source-manifest.json`: 作った・変えた・消したアプリのパスをすべて並べる（`vendor/make-you-chic-ui` のサブモジュールの固定先を含む）。
  - `traceability.json`: FR1.1〜FR6.3・NFR1〜NFR6 を並べ、`OK` の対象は実在するファイルにする。Build and Test で確かめるもの（FR3.3 の診断が出ること、O3 の警報が鳴ること、NFR2 の CI）は `Deferred` と持ち主の段を書く。

- [ ] **Step 27: コミットの提案**（5節）
  - 生成の担当はコミットしない。生成の後に、5節の分け方で依頼者の承認を得てまとめて行う。各コミットの前に pre-commit（Gitleaks・フォーマット）が通ることを確かめる。

- [ ] **Step 28: 統合の準備**（5節）
  - Step 25 が通った状態で、依頼者の承認を得て `fix/260928-quality-followup` から `develop` へ fast-forward で統合する。`git push` は依頼者が行う。統合の後の CI の結果の確かめは Build and Test に引き継ぐ（NFR2）。

## 5. コミットの分け方と統合の形

統合は、サブモジュールの固定先の更新を専用のコミットで残すため、`team.md` の Way of Working の例外（サブモジュールを含む変更は短命のブランチから fast-forward でよい）を使う。ブランチ名の案は `fix/260928-quality-followup`。コミットのメッセージは日本語で、依頼者の承認を得てから行う（`project.md` の Change Control）。

| コミット | 中身 | 要件 |
|---|---|---|
| C1 | `vendor/make-you-chic-ui` の固定先だけ（`735ef04` → `310e1ec`。メッセージに前後のハッシュ） | FR1.1 |
| C2 | E2E の既知の違反の一覧（`axe.ts`・050〜080）と README の既知の制約・E2E の説明 | FR1.2・FR1.3 |
| C3 | アプリ独自の CSS と E2E の 100、README の該当の記述 | FR2 |
| C4 | CI の時間切れの2件の直しと診断 | FR3 |
| C5 | 指標のバケット（`application.yaml`・`HistogramBucketsIT`）、警報とダッシュボードの式、README の手元の監視 | FR4 |
| C6 | README の「警報と対応の手順」 | FR6 |
| C7 | 取り込んだ Gradle の更新（`libs.versions.toml`・lockfile・wrapper） | FR5 |
| C8 | 取り込んだ npm の更新（`package.json`・`package-lock.json`、フォーマットの差） | FR5 |
| C9 | 取り込んだイメージの更新（`compose.yaml`・`TargetDbImages`。`grafana/otel-lgtm` を含む） | FR5 |
| C10 | `.github/dependabot.yml` の `ignore`（O1 が A のときだけ） | FR5.4・O1 |

- 取り込む更新が無い生態系のコミットは作らない。
- C1 だけの状態では、E2E（`verify` と CI の外）が既知の違反の一覧と合わずに落ちる。`verify` は C1 だけでも通る見込み。統合は C1〜C10 をまとめて fast-forward するため、`develop` の上で E2E が落ちる時点は残らない。
- `project.md` の Mandated「不具合を直すときは、再現するテストを同じコミットに含める」と、「サブモジュールの固定先の更新は専用のコミット」がこの Intent では両立しない（コントラストの直しは C1、確かめる E2E の一覧は C2）。6節の Q7 で確かめる。
- ワークフローの記録（この計画・`code-summary.md` など）のコミットは、段の進行役が別に提案する。

## 6. 計画の承認で確かめること

| 番号 | 論点 | 案 | 推奨 |
|---|---|---|---|
| Q1（O1） | 見送る更新を `dependabot.yml` の `ignore` に入れるか | A. 入れる（eclipse-temurin の大きな版、logback-appender のすべて。外す時期をコメントに書く）。「通らずに見送り」は入れない。B. 入れない（プルリクエストを閉じるだけ。新しい版ごとに知らせが来る） | A。logback-appender は alpha の版が頻繁に出て、Spring Boot を上げるまで同じ判定がくり返されるため。設定に残せば見送りの理由がリポジトリに残る |
| Q2（O2） | メールの送信の p95 のパネルを `mastersmith.mail.send` のバケットに戻すか | A. 戻す（Step 7 で名前と値を確かめられたときだけ。README の「既知の欠け」も直す）。B. 戻さない（トレースから作る値のまま。README は「アプリの指標にもバケットがある」ことだけを書く）。C. A に加えて、招待と登録の p95 のパネル（今は同じくトレースの値）も `http.server.requests` のバケットに替える | A。決めた境界（しきい値 1000 ms を含む）で測れ、トレースの2倍刻みより正確。C は要件（FR4.4）の外のため、望むときは差として記録する |
| Q3 | `.page-link` は `NotFoundPage.tsx` で使われていた（FR2.3 の前提と違う） | A. 残し、文字用の色（`.dsl-link` と同じ）に直す。E2E の 100 で見つからない画面も確かめる。B. `.page-link` とその `className` を消し、ブラウザの既定のリンクの色にする。C. 要件の文言どおり CSS だけ消す（`className` が指す先の無い状態になる） | A。リンクの見た目を保ち、コントラストも確かめられる。要件の文書は書き換えず、差を記録する |
| Q4 | FR2 のコントラストの確かめ方 | A. 新しい E2E の 100（流れではない検査）で、20 組 × 3 つの状態を確かめる。B. 既存の 080 に状態を足す（080 は「保存の要求を送らない」を確かめるため、差し替えの要求の数え方も変える） | A。既存の 080 の確かめ（書き込みの要求を送らない）を崩さずに済む |
| Q5 | 0 本の待ちの延長の範囲と、診断の出し方 | 定数の変更で4か所すべてを 30 秒にする。診断はログに加えて失敗の知らせ（例外の文言）にも入れる（CI では標準出力が見えないため） | このとおり |
| Q6 | ブランチ名と統合の形 | `fix/260928-quality-followup` から `develop` へ fast-forward（C1〜C10） | このとおり |
| Q7 | 専用のコミット（C1）と、再現するテストを同じコミットに含める決まりの関係 | A. C1 は固定先だけとし、確かめる E2E の一覧は直後の C2 に置く（まとめて fast-forward）。差を `code-summary.md` に書く。B. C1 に E2E の一覧と README も含める（専用のコミットではなくなる） | A。サブモジュールの決まり（専用のコミット）を優先し、同じ統合の中で確かめを伴わせる |
| Q8 | Step 7 の手元の監視の確かめの環境 | 使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、監査は使い捨ての内部DB にだけ残す。VM のメモリのため、配備したアプリをこの間止める（`docker compose stop app`、終わったら起動し直す） | このとおり。止める前に依頼者に伝える |
| Q9 | Dependabot の着手 | Step 3 で `git fetch origin --prune` を行う了承。小さな更新はまとめて1回の `verify` で試し、落ちたら分ける。vitest と coverage-v8 は1組で判定する | このとおり |

### 依頼者の決定（2026-09-29、`code-generation-questions.md`）

- Q1: A（`ignore` に入れる）・Q2: A（戻す）・Q3: A（`.page-link` は残して文字用の色に直す）・Q7: A（C1 は固定先だけ、E2E の一覧は C2）。
- Q4・Q5・Q6・Q8・Q9: 推奨どおり。
- 上の手順と表は、この決定のとおりに実行する。

## 7. Build and Test に引き継ぐこと

- FR3.3 の診断の確かめ: 待ちの上限を一時的に極端に短くして（例: `ZERO_CONNECTIONS_WAIT` を 1 ミリ秒、Vitest の該当の1件の上限を 1 ミリ秒）それぞれ1回流し、決めた項目（接続の数・待っているスレッドの数、かかった時間）が失敗の知らせとログに出ることを確かめる。確かめの後は元に戻し、コードに残さない。
- FR3.4・R-02: `test-results.md` にも「team.md の決まりとの差」を書く（Code Generation の `code-summary.md` と同じ内容で、Build and Test の時点の CI の結果を足す）。
- NFR2: 依頼者の `git push` の後の CI の `./gradlew verify` が通ることを1回以上確かめる。落ちたときは Step 14・Step 16 の診断の値を記録する。
- O3（FR4 の受け入れの基準）: 警報が鳴ることを、負荷をかけた使い捨ての環境で確かめるか、式に当てる値の確かめで代えるかを決めて行う。Step 7 の確かめの結果（名前・`le`・式の値）を手がかりに使う。
- 最後の実測: `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` のテストの件数とカバレッジ、`./gradlew e2eTest` の結果を、実測の数字だけで報告する。
- FR5 の受け入れの基準: 取り込んだ更新をすべて入れた状態で `verify` と `e2eTest` が通ること、一覧のすべての行に判定と理由があること。
- 学びの手順で確かめる候補: O4（`team.md` の `packagesJudgedByTotal` の記述を 22 個から今の 12 個に直すか）、FR5.2 で変えた決まり（networknt の Jackson、`TargetDbImages` の長く支援される版の系列）、Q3 の食い違い（コード知識ベースの検索の見落とし）。
- 前の Intent の記録との差（FR6.2）と、要件との差（FR2.3 の `.page-link`、FR2 の確かめを E2E で行ったこと）が `code-summary.md` にあることの確かめ。

## 8. Dependabot の更新の一覧（計画の時点。Step 3 で確かめ直す）

ブランチの名前は `origin/dependabot/` を省いて書く。

| ブランチ | 更新 | 試し方 | 予定の判定 |
|---|---|---|---|
| `docker/eclipse-temurin-26.0.2_10-jre-noble` | eclipse-temurin 25.0.4_7 → 26.0.2_10 | 試さない | 見送り（FR5.4） |
| `docker_compose/grafana/otel-lgtm-0.34.0` | grafana/otel-lgtm 0.33.1 → 0.34.0 | Step 7 の手元の監視の確かめ | 確かめが通れば取り込み |
| `docker_compose/mariadb-13.0.2` | mariadb 11.8.9 → 13.0.2 | `compose.yaml` と `TargetDbImages`、対象DB の結合テスト | 通れば取り込み（決まりを変える） |
| `docker_compose/mysql-26.7.0` | mysql 8.4.11 → 26.7.0 | 同上 | 通れば取り込み（決まりを変える） |
| `docker_compose/postgres-18.6` | postgres 18.6 の digest | 同上 | 通れば取り込み |
| `gradle/com.networknt-json-schema-validator-3.0.7` | networknt 3.0.6 → 3.0.7 | 1つで試し、Jackson の版を記録 | 通れば取り込み（決まりを変える） |
| `gradle/com.tngtech.archunit-archunit-junit5-1.5.1` | archunit 1.5.0 → 1.5.1 | Gradle の小さな更新とまとめて | 通れば取り込み |
| `gradle/gradle-wrapper-9.8.0` | Gradle wrapper 9.7.1 → 9.8.0 | 同上 | 通れば取り込み |
| `gradle/io.opentelemetry.instrumentation-opentelemetry-logback-appender-1.0-2.31.1-alpha` | logback-appender 2.28.1-alpha → 2.31.1-alpha | 試さない | 見送り（FR5.4） |
| `gradle/org.yaml-snakeyaml-2.7` | snakeyaml 2.6 → 2.7 | Gradle の小さな更新とまとめて | 通れば取り込み |
| `npm_and_yarn/frontend/prettier-3.9.9` | prettier 3.9.8 → 3.9.9 | npm の小さな更新とまとめて | 通れば取り込み |
| `npm_and_yarn/frontend/typescript-7.0.2` | typescript ^6.0.3 → ^7.0.2 | 1つで試す | 通れば取り込み |
| `npm_and_yarn/frontend/vite-8.3.1` | vite 8.3.0 → 8.3.1 | npm の小さな更新とまとめて | 通れば取り込み |
| `npm_and_yarn/frontend/vitest-5.0.2` | vitest ^4.1.11 → ^5.0.2 | coverage-v8 と1組で | 通れば取り込み |
| `npm_and_yarn/frontend/vitest/coverage-v8-5.0.2` | @vitest/coverage-v8 ^4.1.11 → ^5.0.2 | vitest と1組で | 通れば取り込み |
