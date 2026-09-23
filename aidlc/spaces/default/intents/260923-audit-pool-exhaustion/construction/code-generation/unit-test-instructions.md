# テストの手順 — 接続プールの上限の引き上げと再現テスト（F2）

## テストの道具と設定

- JUnit 5 ＋ AssertJ ＋ Spring Boot Test（既存の設定のまま。追加の依存は無い）。
- 結合テストは Gradle の `integrationTest` タスク（`*IT`）で、Spring と組み込みの H2（`TestDatabase.register` の一時ディレクトリのファイル）を起動する。コンテナは使わない。
- 前提: JDK 25（Gradle のツールチェーン）。

## この Intent のテストを実行するコマンド

プロジェクトのルートで実行する。どれもこの Intent で加える・使うテストのクラスだけに絞っている。

```bash
# 再現テスト（同時 10 件・20 件の成功のログイン）
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.auth.web.ConcurrentLoginAuditIT'

# プールの上限の既定値と環境変数
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.config.DataSourcePoolIT'

# 環境変数でプールの上限を 10 に戻し、再現テストが F2 を検出して失敗することを確かめる（修正の後の試し。失敗が期待どおり）
MASTERSMITH_DB_MAXIMUM_POOL_SIZE=10 ./gradlew :backend:integrationTest --tests 'cherry.mastersmith.auth.web.ConcurrentLoginAuditIT' --rerun

# 実行の準備の確認（既存のテストが単独で動くこと）
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.auth.service.LoginConcurrencyIT'
```

統合の前の検査はプロジェクト全体で1回だけ行う（Build and Test の段と Step 1・9）。テストの件数とカバレッジを実測するため、テストのタスクを消してから実行する。

```bash
./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

## テストの一覧（Minimal ＋ bugfix の回帰テスト）

| テスト | 種類 | 確かめること | 要件 |
|---|---|---|---|
| `ConcurrentLoginAuditIT`（同時 10 件） | 結合（HTTP） | 応答がすべて 200、`LOGIN_SUCCEEDED` の行が 10 件、監査の失敗と接続の待ちの時間切れが0件 | FR3、NFR1 |
| `ConcurrentLoginAuditIT`（同時 20 件） | 結合（HTTP） | 同じ条件で 20 件 | FR3、NFR1 |
| `DataSourcePoolIT`（既定値） | 結合 | 環境変数なしで上限が 30 | FR1.1 |
| `DataSourcePoolIT`（環境変数） | 結合 | `MASTERSMITH_DB_MAXIMUM_POOL_SIZE=12` で上限が 12 | FR1.2 |
| `DataSourcePoolIT`（同時に借りる） | 結合 | 既定の設定で 30 本を同時に借りられる | FR1.1 |

不具合は、同時の要求がプールの上限に達したときにだけ起きる。単体テストでは再現できないため、回帰テストは結合テストで書く（bugfix の範囲の下限: 再現できる最も狭い層）。

## カバレッジの下限

- JaCoCo の行 80%・分岐 70%（`backend/build.gradle.kts` の既存の設定）を下げない。除外も増やさない。
- 本番のコードの変更は無いため、カバレッジの値は変わらない見込みである。Step 1 と Step 9 の実測で確かめる。

## 差し替えと待ち合わせの方針

- 要求の重なりは、`AuditWriteBarrierConfig`（`@TestConfiguration`、`@Primary` の `LongSupplier`）で作る。監査が書き込みの時間を測る最初の呼び出しで、N 件がそろうまで待たせる。このとき各要求は業務の接続を持ったままである。実時刻の `sleep` は使わない。
- 待ち合わせには上限（20 秒。HTTP の要求の時間切れ 30 秒より短くし、失敗のときも結果を確かめられるようにする）をつける。そろわなかった場合はそのまま先へ進ませ、テストが止まらないようにする（上限 10 での失敗の試しでも、テストは時間内に終わる）。
- 照合の cost は 4（既存の結合テストと同じ `mastersmith.auth.password.bcrypt-cost=4`）にする。
- 監査の失敗・接続の待ちの時間切れは、Spring Boot の `OutputCaptureExtension` でログの出力を見て確かめる。

## テストのデータ

- 利用者は、テストの実行ごとに `UserAccountService.createUser` で N 人作る（メールアドレスは `concurrent-login-<UUID>@example.com`、パスワードは日本語を含むテスト用の値）。
- 監査の行は `AuditRows`（`audit/testsupport`）で、そのテストの利用者のメールアドレスに絞って数える。ほかのテストの行や実行順に依存させない。
- DB は `@TempDir` の一時ディレクトリの H2 ファイルで、テストのクラスごとに分かれる。秘密情報の役の値は、ソースに書かず、`TestDatabase.randomSecret()` などで作る。
