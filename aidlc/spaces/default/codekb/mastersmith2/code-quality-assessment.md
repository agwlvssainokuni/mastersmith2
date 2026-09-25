# コードの品質の評価（mastersmith2）

確かめ方はファイルの読み取りだけで、Gradle・npm・Docker は実行していない。件数は `git ls-files` でファイルを数えた値で、実行の件数ではない。

## テスト

| 対象 | 置き場 | ファイル数 | 道具 |
|---|---|---|---|
| バックエンドの単体 | `backend/src/test/java/`（`*Test`、タスク `test`） | 102 | JUnit Jupiter・AssertJ・jqwik・ArchUnit |
| バックエンドの結合 | 同上（`*IT`、タスク `integrationTest`） | 70 | Spring Boot Test・Spring Security Test・内部DB は組み込みの H2・対象DB は Testcontainers |
| 画面 | `frontend/src/`（`*.test.ts(x)`） | 47 | Vitest・Testing Library・user-event・vitest-axe・fast-check |
| E2E | `frontend/e2e/`（skeleton・auth・admin-access・dsl-admin） | 4 | Playwright（`verify` と CI の外） |

- `user` の既存のテスト（ファイル名だけ確かめた）: `EmailAddressTest`・`PasswordPolicyTest`・`PasswordTest`・`DummyPasswordHashTest`・`InitialAdminInitializerTest`・`UserAccountServiceTest`・`UserRepositoryIT`・`UserSchemaIT`・`InitialAdminIT`。
- 構造の検査（確かめた事実）:
  - `ArchitectureTest`: web は repository を直接使わない・`@Transactional` は service の層だけ・コントローラーはエンティティを返さない・コンストラクター注入だけ・Lombok なし。
  - `AuthBoundaryArchitectureTest`: `auth` は `user.domain` のエンティティと `user.repository` に依存しない・`user` は `auth` に依存しない・`auth` と `user` は `audit` に依存しない・`User.getPasswordHash` は `user` の中だけで呼ぶ。
  - `AuditBoundaryArchitectureTest`（DisplayName だけ確かめた）: 監査の repository は更新・削除を持たない・`audit` だけが使う・`audit` は web の層を持たない。
- 結合テストで `/api/**` を公開にしたいときは `access/testsupport/PublicApiTestRules`（`mastersmith.test-fixture.public-api=true`）を使う。本番の設定には無い。
- 認証・認可・監査のテストは `team.md` の Testing Posture の一覧（ロックの境界・存在の推測の防止・トークンの境界と改ざん・401／403／200・監査の必須項目・秘密の漏えい）が必須。今回の新しい機能（招待・登録の完了・パスワードの変更）にも当てはまる。

## カバレッジ

- バックエンド: JaCoCo。全体の合計で行 80%・分岐 70%。加えてパッケージごとに同じ下限を当てるが、`packagesJudgedByTotal`（`backend/build.gradle.kts` 199〜222 行）の既存の 22 パッケージは外す。計測から外すのは起動クラスと `*Properties` だけ。
- 画面: `@vitest/coverage-v8` の `thresholds` で行 80%・分岐 70%（`frontend/vitest.config.ts`）。

### K-11 既存の `user.*` はパッケージごとの下限の対象外

- 事実: `packagesJudgedByTotal` に `user.domain`・`user.repository`・`user.service` が入っている（ほかに `access.*`・`audit.*`・`auth.*`・`common.*`・`config`）。これらに手を入れても、そのパッケージは全体の合計でだけ判定される。一覧に無い新しいパッケージは、自動でパッケージごとの下限の対象になる。前の Intent（`260923-dsl-schema-loader`）で既存のパッケージの実測が下限を下回ったため、`team.md` の決まりどおりこの形にした（`project.md` の Testing Posture）。
- 帰結: 利用者の登録・招待・プリファレンスを既存の `user` のパッケージに足すと、その部分のカバレッジはパッケージ単位では強制されない。新しいパッケージ（例: `user` とは別の機能のパッケージ）に置けば強制される。除外の一覧を増やすことは `team.md` で禁じられている。

## 検査・CI・文書

- Java: Spotless（palantir-java-format 2.98.0、ライセンスヘッダー `/* ... */`）、SpotBugs 4.10.4 ＋ FindSecBugs 1.14.0（`spotbugsGate` で priority 1 と `SQL_` で始まる指摘を止める。除外は `backend/config/spotbugs-exclude.xml`）、ArchUnit の層と境界の検査。
- 画面: Prettier・oxlint・ESLint（react-hooks）・Stylelint・`tsc`、ライセンスヘッダーは `frontend/scripts/check-license-header.mjs`。
- コミット前: `.pre-commit-config.yaml`（Gitleaks・Spotless・Prettier、流し読み）。
- CI: `.github/workflows/ci.yml`。`develop` へのプッシュと `v*` のタグで `./gradlew verify` を実行し、WAR を成果物として保存する。タグのときはリリースに WAR を添付する。
- 文書: README は詳しい（環境変数・戻し方・API のアクセス制御の一覧・監査の既知の制約・差し込み口の一覧）。Javadoc はほぼすべてのクラスとメソッドにあり、日本語で要件の ID（BR・NFR・ADR）と理由を書く形がそろう。

## 技術的負債と注意点

確かめた事実:

- `TODO`・`FIXME`・`HACK` は `backend/` と `frontend/src` に0件（開発担当が検索で確かめた）。
- `@SuppressWarnings` 等は2ファイル（`common/error/web/DefaultErrorResponseWriter.java`・`common/observability/SanitizingLogRecordExporter.java`、中身は未確認）。画面の lint の抑止は `frontend/src/types/vitest-axe-matchers.d.ts` の1ファイルだけ。
- 監査の記録が業務の接続を持ったまま2本目を借りる既知の制約は残っている（README、`architecture.md` の Interaction Diagrams 4）。
- 監査の出来事の追加は `audit` の中の変更を伴う（K-7、`component-inventory.md` の `audit`）。

今回の Intent に関わる所見の本文は、ほかの文書に1回ずつ書いた（一覧は `business-overview.md`）。

| ID | 品質の観点での要点 |
|---|---|
| K-1・K-2 | 利用者の状態を足すと、ログイン・更新・トークンの認証の3つの経路で拒否を確かめるテストが要る（既存の経路は状態を見ないため、漏れると招待中の利用者が使える） |
| K-6 | 公開の API を足すと README の公開の一覧と、401／403 のテストの対象が変わる |
| K-8 | パスワードの変更の後の扱いは決まっておらず、`team.md` の「トークン」の必須テストに相当する確かめをどこまで書くかも決まっていない |
| K-9・K-10 | メールの送信は外の相手を待つ初めての処理で、失敗・遅延・接続の持ち続けを確かめるテストと負荷の試験が要る（仮説） |
| K-11 | 既存の `user.*` に足す部分はパッケージごとの下限で守られない |
