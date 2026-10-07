# 単体テストの手順（Unit Test Instructions）— U2 DSL の書式の版 2（dsl-v2）

計画: `code-generation-plan.md`（同じディレクトリ）。Test Strategy は Standard（部品ごとに 5〜8 件と、要所の結合テスト）、方法は test-after（層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて流す）。

ここに書くコマンドは、どれもこの単位のテストだけを名指しで流す（パッケージの名前の型か、ファイルの道）。素の `./gradlew test`・`./gradlew verify`・`npm test` はこの単位の確かめには使わない（統合の前の関門の `verify` は計画の Step 26 で別に流す）。

## 1. テストの道具と設定（既存のものをそのまま使う）

| 対象 | 道具 | 設定 | この単位で変えること |
|---|---|---|---|
| バックエンドの単体（`*Test`、`*PropertyTest` を含む） | JUnit 5・AssertJ・ArchUnit・jqwik 1.10.1 | `backend/build.gradle.kts` の `tasks.test`（`includeTestsMatching("*Test")`、テストの JVM のヒープ 1g、失敗の詳細をすべて出す `exceptionFormat = FULL`） | 変えない |
| バックエンドの結合（`*IT`） | Spring Boot Test・組み込みの H2（`TestDatabase`）・Testcontainers（対象DB の MySQL・MariaDB・PostgreSQL、版を固定したイメージ） | `integrationTest`（`includeTestsMatching("*IT")`） | 変えない |
| 画面 | Vitest 5.0.2・Testing Library（jsdom）・user-event 14.6.7・vitest-axe 0.1.0・fast-check 4.10.2・`@vitest/coverage-v8` | `frontend/vitest.config.ts`・`vitest.setup.ts` | 変えない |
| E2E（参考。単体テストではない） | Playwright（`./gradlew e2eTest`、`verify` と CI の外） | `frontend/playwright.config.ts` | 変えない |

新しい依存は足さない（`backend/gradle.lockfile`・`frontend/package-lock.json` を変えない）。

### 1.1 前提（コンテナの実行環境）

対象DB を使う結合テスト（`dslmanage.generate` の `*IT`・`DslTargetDbIT` など）は colima が動いていることを前提にする。コマンドの前に、README のとおりシェルに次を渡す（渡さないと対象DB のテストが SKIPPED になる、`project.md` の学び）。

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

## 2. テストを始める前に動くことを確かめるコマンド（計画の Step 2）

```bash
# バックエンド（既存のテストでコマンドが動くことを確かめる）
./gradlew :backend:test --tests 'cherry.mastersmith.dsl.*'
./gradlew :backend:integrationTest --tests cherry.mastersmith.dsl.DslSchemaPublicationIT

# 画面（既存のテストで動くことを確かめる）
cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/dsl
```

## 3. この単位のテストを流すコマンド

テストの結果が UP-TO-DATE で飛ばされたときは、`:backend:cleanTest`（単体）・`:backend:cleanIntegrationTest`（結合）を前に付けて流し直し、実際に流れた件数だけを記録する（`project.md` の学び）。パッケージの型は `cherry.mastersmith.dsl.*` と `cherry.mastersmith.dslmanage.*` を分けて書く（`dsl*` とすると `dslmanage` も当たるため）。

### 3.1 バックエンド（層ごと。計画の手順の番号）

```bash
# Step 5: 読み込みの部品と安全な読み込みの口（版 1 のままの既存のテストを含む）
./gradlew :backend:test --tests 'cherry.mastersmith.dsl.*'

# Step 9: 版 2 の書式・モデル・検証・深さ（dsl の層の単体と結合）
./gradlew :backend:test --tests 'cherry.mastersmith.dsl.*'
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.dsl.*'

# Step 11: 既定の DSL の生成（結合は対象DB の3種類。1.1 の環境変数が要る）
./gradlew :backend:test --tests 'cherry.mastersmith.dslmanage.generate.*'
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.dslmanage.generate.*'

# Step 13: 業務処理
./gradlew :backend:test --tests 'cherry.mastersmith.dslmanage.*'

# Step 15: API と結合の全体（この単位の結合テストすべて。1.1 の環境変数が要る）
./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.dsl.*' \
  --tests 'cherry.mastersmith.dslmanage.*'
```

この単位の単体と結合をまとめて流すとき（1.1 の環境変数を渡して）:

```bash
./gradlew :backend:cleanTest :backend:cleanIntegrationTest \
  :backend:test --tests 'cherry.mastersmith.dsl.*' --tests 'cherry.mastersmith.dslmanage.*' \
  :backend:integrationTest --tests 'cherry.mastersmith.dsl.*' --tests 'cherry.mastersmith.dslmanage.*'
```

`--tests` はその前に書いたタスクだけに効くため、タスクごとに書く。

### 3.2 画面

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/features/dsl \
  src/shared/api-client/apiClient.download.test.ts
npm run typecheck
```

### 3.3 E2E の書き換えの確かめ（計画の Step 19。全体は Step 27）

```bash
# Mailpit を起動しておく
docker compose --profile mail up -d mailpit
./gradlew :backend:bootWar
# 書き換えた流れ（040）と、足す DSL の画面の検査（135。計画の 10節 Q2: A、本数に数えない）
cd frontend && npx playwright test e2e/040-dsl-admin.e2e.ts e2e/135-dsl-admin-accessibility.e2e.ts
```

終わったら `frontend/playwright-report`・`frontend/test-results` を消す。

## 4. テストの中身（Standard、部品ごと）

テストの説明文（`@DisplayName`・`describe`・`it`）は英語で書く。

| 部品 | ファイル | 確かめること | 件数の目安 |
|---|---|---|---|
| 安全な読み込みの口 | `backend/src/test/java/cherry/mastersmith/dsl/service/SafeYamlReaderTest.java`（名前は案） | 小さな上限の値（例: 大きさ 64 バイト・深さ 3・別名 0 と 1・節 20）で、4つの上限のそれぞれ「ちょうどは受け付け、1つ超えると拒否」。深さと別名は N と N+1 で、拒否が区分（`TOO_DEEP`・`TOO_MANY_ALIASES`）と位置（行・列）つきで、`SYNTAX`・位置なしに写らない（NFR 設計 R-05）。別名 0: 対応表を指す別名1つで拒否、文字の値を指す別名は通る。区分の残り（`TAG_NOT_ALLOWED`・`DUPLICATE_KEY`・`SYNTAX`、`TOO_LARGE` は読まずに返る）。拒否に部品の例外の文が無い。上限の範囲外（大きさ・深さ・節が 0、別名が −1）は `IllegalArgumentException`。別名の爆発する入力（9 段 × 9 個、約 540 バイト）が DSL の上限と小さな上限のそれぞれで、テストの中で測った時間 5 秒の内に `TOO_MANY_ALIASES`。`toString` に木の中身（目印の文字列）が無い | 15〜20 |
| 安全な読み込みの漏えい | `backend/src/test/java/cherry/mastersmith/dsl/service/SafeYamlReaderSecretLeakIT.java`（名前は案） | TRACE を有効にして目印の文字列を含む YAML を読ませ、入力（`byte[]`）と結果のどちらもアプリのログに目印が出ない（既存の `*SecretLeakIT` と同じ形） | 2 |
| 読み込み（版 2） | `backend/src/test/java/cherry/mastersmith/dsl/service/DefaultDslReaderTest.java` | 版 1・版が無い・整数でない版は `UNSUPPORTED_VERSION` 1件で構文と意味を行わない。スキーマ 0・2 は場所 `schemas` の意味の誤り、1 は通る。スキーマ名・同じスキーマの中のテーブル名の重なりは `DUPLICATE_KEY`。メニューの組の無いスキーマ・無いテーブル。深さ 5 は受け付け、6 は6段目の項目ごとに1件（子孫に重ねない、上限の値を埋める、位置つき）。`readAtStartup` は深さだけを外し、落とした数を返し、深さ以外の誤り（版 1 を含む）は Invalid。外部の `$ref` を取りに行かない（`CountingHttpServer`）。既存の大きさ・タグ・重複キーの確かめを版 2 で | 既存＋8〜11 |
| 検証 | `dsl/validate/DslSchemaValidatorTest.java`・`DslSemanticValidatorTest.java` | 版 2 の構文（根・スキーマ・メニューの組に書式に無い項目）、外部キー・選択肢の参照は同じスキーマの中、場所の道 `schemas.<スキーマ>.tables.<テーブル>…` | 既存の書き換え＋3 |
| 深さの関数 | `dsl/domain/MenuDepthTest.java`・`MenuDepthPropertyTest.java`（名前は案） | 例: まとまりかテーブルかによらず項目1つを1段、6 段目を子ごと落とす、子を落としてテーブルも子も持たなくなったまとまりを上へさかのぼって落とす（落とした数に加える）、上限内の木は変わらない。性質（jqwik）: 落とした木は深さ 5 以下、残った項目の親子と並びは元と同じ、テーブルも子も持たないまとまりが無い、落とした数＋残った数＝元の数 | 10 |
| モデル | `dsl/domain/DslModelTest.java`・`DslResultTypesTest.java`・`dsl/service/ActiveDslModelStoreTest.java` | `formatVersion` 2 だけ、`schemas`・`menus` は変更できない、`findTable(TableRef)` の当たり・外れ・大文字と小文字の区別、`TableRef`・`DslSchema` の必須の値 | 既存の書き換え＋5 |
| JSON Schema の公開 | `dsl/DslSchemaPublicationIT.java` | `static/dsl/dsl-schema-v2.json` が正本と同じ（ログインなしで取れる） | 既存の書き換え |
| 境界 | `dsl/DslBoundaryArchitectureTest.java`・`dslmanage/DslManageBoundaryArchitectureTest.java`・`dslmanage/DslManageGenerateBoundaryArchitectureTest.java` | 変えずに通る（`SafeYamlReader` の公開の型に `dsl.parse` の型が出ない） | 既存 |
| 生成 | `dslmanage/generate/DslTreeBuilderTest.java`・`TargetSchemaDslGeneratorTest.java`・`DslYamlWriterTest.java`・`DslGenerationPropertyTest.java`・`AbstractDefaultDslGeneratorIT.java`（MySQL・MariaDB・PostgreSQL） | 版 2、`schemas` に設定のスキーマ名1つ（表示名 ja・en ともスキーマ名）、平らなメニューの `{schema, name}`、生成した本文がそのまま版 2 の検証を通る、スキーマ名を含み接続先・ポート・ユーザー名・パスワード・`jdbc:`・PostgreSQL の `database` を含まない | 既存の書き換え＋3 |
| 照合・違い・要約 | `dslmanage/service/DslReconcilerTest.java`・`DslSummaryAndDiffTest.java` | 名前が同じときだけテーブルを比べ、場所の道が `schemas.…`。違えば `SCHEMA_MISMATCH` 1件で文に DSL のスキーマ名だけ（設定のスキーマ名が無い）。スキーマの区分 ADDED・REMOVED・CHANGED（表示名）・UNCHANGED、適用中が無ければすべて ADDED。`schemaCount`、表示名の未設定にスキーマの表示名、メニューの木の節が組 | 既存の書き換え＋8 |
| 起動時の読み直し | `dslmanage/service/DslStartupLoaderTest.java`・`UnreadableAppliedRevisionTest.java` | 深すぎる枝を落として Present と WARN 1件（キー `dsl.hash`・`dsl.prunedMenuItems`・`dsl.menuDepthLimit` だけ）、深さ以外の誤りは ERROR 1件・Absent・版の ID を覚える、通れば覚えた ID を消す、適用中が無ければ消す。置き場は覚えた ID と同じときだけ真 | 4＋4 |
| 業務処理 | `dslmanage/service/DslLifecycleTest.java`・`dslmanage/domain/DslManageDomainTest.java` | 読めないプレビューの表示は `DSL_INVALID`（行は残る）、適用はキャッシュが無く識別が同じとき確定の前に確かめて 422、識別が違えば確かめずに 409、確定の後に履歴の本文を読み直さない、`appliedUnreadable` | 既存の書き換え＋4 |
| API と結合 | `dslmanage/web/DslAdminApiIT.java`・`dslmanage/service/DslStartupIT.java` ほか（`DslManageRepositoryIT`・`DslTargetDbIT`・`DslConcurrencyIT`・`DslAccessControlIT`・`DslAuditWriteFailureIT` は見本の書き換え） | 版 2 の投入・プレビュー・適用・ダウンロード・履歴。版 1・深さ 6・スキーマ 0 と 2 の投入と復元は 422 で応答に部品の例外のクラス名と文が無い。深さ 5 は受け付け。内部DB に版 1 の本文を直接置いたプレビューの表示と適用は 422。拒否の後に内部DB を読み直して適用中・プレビュー・履歴が変わらない。版 1・深すぎる本文のダウンロードは本文をそのまま返す。起動: 版 1 の適用中で Absent・ERROR 1件・ヘルスチェック UP・`appliedUnreadable` 真、深さ 6 の適用中で Present・WARN 1件・目印の表示名とテーブル名がログに無い | 12〜15 |
| 画面 | `frontend/src/features/dsl/DslStatusPanel.test.tsx`・`DslPreviewPanel.test.tsx`・`DslAdminPage.test.tsx`・`DslMenuTree.test.tsx`・`DslDiffTable.test.tsx`・`DslWarningList.test.tsx` | 今の状態の注意の有無とダウンロード、読めないプレビュー（誤りの一覧・案内・ダウンロードと破棄・適用が無い、破棄で戻る、投入の成功で消える）、要約のスキーマの数、`SCHEMA_MISMATCH` は「照合できなかった」に入り食い違いの件数・適用の確かめの件数に入らない（計画 10節 Q1: A）、「スキーマ名.テーブル名」と `<`・`>`・`&`・`"`・`'` の文字としての表示、見出しの行と区分・同じ名前のテーブルの開閉が混ざらない・「すべて表示」、部品ごとに vitest-axe 1件（注意あり・読めないプレビュー・見出しの行） | 部品ごとに 1〜5 |
| 適用の確かめの件数（計画 10節 Q5: A） | `frontend/src/features/dsl/diffCounts.test.ts`（名前は案）・`DslConfirmDialog.test.tsx`・`DslAdminPage.test.tsx` | スキーマの区分の数え方（ADDED・REMOVED・CHANGED・UNCHANGED、適用中が無いときはすべて増えた）、テーブルの件数はすべてのスキーマの合計、適用の確かめにスキーマの件数の行が出る（0 件でも出る、日本語と英語）、`useDslAdmin` から適用の確かめへスキーマの件数が渡る | 7 |
| DSL の画面の検査（E2E、計画 10節 Q2: A。単体テストではなく本数に数えない） | `frontend/e2e/135-dsl-admin-accessibility.e2e.ts` | API の答えを見本で差し替え、F2（今の状態の注意）・F3（読めないプレビュー）・F5（長いスキーマ名・表示名の見出しの行）を、ブランドカラーとテーマのすべての組・幅 360・768・1280 で実際のブラウザの axe とはみ出しの確かめにかける。番号は role-admin-ui（140・150）・app-frame-ui（160・170）の予定と今の 010〜130 に重ならない 135 | 状態ごとに 1 |
| 画面の書き換え | `testing/fixtures.ts`・`submitInput.test.ts`・`DslSubmitForm.test.tsx`・`api/dslApi.test.ts`・`api/saveFile.test.ts`・`DslErrorList.test.tsx`・`DslHistoryTable.test.tsx`・`frontend/src/shared/api-client/apiClient.download.test.ts` | 版 2 の本文と v2 の道（振る舞いの確かめは変えない） | 既存の書き換え |

## 5. カバレッジの目標

| 対象 | 目標 | 測り方 |
|---|---|---|
| バックエンドの全体 | 行 80%・分岐 70% 以上 | 統合の前の `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（計画の Step 26）。レポートは `backend/build/reports/jacoco/` |
| `dsl.domain`・`dsl.parse`・`dsl.service`・`dsl.validate`・`dslmanage.domain`・`dslmanage.generate`・`dslmanage.service`・`dslmanage.web`（と値の記録だけの `dslmanage.repository`） | パッケージごとに行 80%・分岐 70% 以上（どれも `packagesJudgedByTotal` に無く、すでに対象） | 変更の前の基準（計画の Step 3。`./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport` の `jacocoTestReport.xml`）と変更の後（Step 26）の値を並べて記録し、下がったパッケージは理由を書く。上限の型の範囲の確かめ（例外）の分かれ道も単体テストで通す |
| `packagesJudgedByTotal` | 7 パッケージのまま（増やさない。この単位で外すものは無い） | `backend/build.gradle.kts` の一覧に差が無いこと |
| 画面の全体 | 行 80%・分岐 70% 以上（`frontend/vitest.config.ts` の `thresholds`） | 同じ `verify` の段 7。`features/dsl` を計測から外さない |

計測の除外は増やさない。下限・閾値・テストの JVM のヒープ（1g）の設定は変えない。足りなければ止めて依頼者に確かめる。

## 6. モック・差し替えの指針

- **読み込みの部品と安全な読み込みの口**: SnakeYAML・JSON Schema の部品はモックにしない。本物の部品に小さな上限の値を渡して境界を作る。時間の判定は `System.nanoTime` でテストの中で測る（実時刻の待ちや `sleep` に頼らない）。
- **外部の `$ref`**: 既存の `dsl/testsupport/CountingHttpServer` で、取りに行った回数が 0 であることを確かめる。
- **起動時の読み直し**: 単体は `DslRecordStore`・`DslReader`・`ActiveDslModelHolder` を手で作った代わり（既存の `DslStartupLoaderTest` と同じ形）、ログはキーと値で確かめる。結合（`DslStartupIT`）は内部DB に本文を直接置いてから Spring を起動する（既存の形）。
- **読めないプレビュー**: 内部DB の `dsl_previews` に版 1 の本文を直接置き、キャッシュに無い状態（新しい起動の文脈、または `DslManageTestHooks` でキャッシュを空にする）で表示と適用を呼ぶ。拒否の後は内部DB を読み直して確かめる（状態コードだけで合格にしない）。
- **対象DB**: 本物の MySQL・MariaDB・PostgreSQL のコンテナ（版を固定したイメージ）で、H2 やモックで代用しない（`project.md` の Mandated）。照合の単体テストは写し（`TargetSchema`）を手で作る。
- **TRACE の漏えい**: 既存の `*SecretLeakIT` と同じく、ロガーを TRACE にしてログを集め、目印の文字列を探す。
- **画面**: API は既存の fetch の差し替え（`testing/renderDsl.tsx`）と版 2 の見本（`testing/fixtures.ts`）。make-you-chic-ui はモックにしない。描画の後に反映される値は `waitFor` で待つ。テストの時間の上限を原因を確かめずに延ばさない。`role="alert"` は読み上げを前提にせず、見える文字と `role` の有無だけを確かめる。
- **同時の重なり**: 既存の `DslConcurrencyIT` の待ち合わせ（スレッドの数に頼らない）をそのまま使う。

## 7. テストデータ

- DSL の見本は版 2 の形で、スキーマ名は `public`・`sales` など一般の名前、テーブル名は `dept_mst`・`emp_mst` など今の見本の名前、表示名は日本語でよい（例「部署」「社員」）。
- 深さの境界の見本: `menus` の直下を 1 段目として 5 段ちょうどと 6 段、子を落とすとまとまりが空になる形（さかのぼりの確かめ）。
- スキーマの数: 0（`schemas: {}`）・1・2。重複キー: 同じスキーマ名2回、同じスキーマの中に同じテーブル名2回。
- 名前の違うスキーマの照合: 設定のスキーマ名と違う名前（例 `other_schema`）。警告の文に設定のスキーマ名が無いことを確かめる。
- エスケープの確かめ: 表示名・スキーマ名に `<script>alert(1)</script>`・`A&B "q" 'q'`。長い名前（例 60 文字のスキーマ名・表示名）は見出しの行の確かめに使う。
- 漏えいの目印: 表示名・テーブル名・YAML の値に固有の目印の文字列（例 `LEAK_MARKER_7f3a`）を入れ、ログに出ないことを確かめる。
- 利用者とメールアドレスは既存の見本（`example.com` などの予約のドメイン）だけを使い、実在しそうな氏名・宛先を置かない。対象DB の接続情報はテストのコンテナの値だけで、ログ・応答に出ないことを確かめる。
- 10 MiB の本文はこの単位の単体テストでは作らない（大きさの境界は既存の見本の作り方で、ちょうどと1バイト超え）。10 MiB の投入は Build and Test の `perf/dsl-timing.sh` が作る。
- 結合テストの一時の内部DB は `@TempDir` で消え、対象DB のテストは既存の作り方（テストのクラスごとのスキーマ、終わったら消す）のまま。片付けの手順を足さない。
