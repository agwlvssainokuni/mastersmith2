# Code Summary — U8 インスタンスの見た目の設定（u8-instance-appearance）

承認済みの計画（`code-generation-plan.md`）の Step 1〜17 を実行した結果の記録。パスはリポジトリのルートからの相対パス。作業のブランチは `develop`（先頭 `9a9a6337252e8ed9fcfc6865544d409da406513b`）から作った `feature/260925-user-management-u8`。コミットはしていない（3節の区切りで依頼者の承認を得てから行う）。

## 1. 作ったもの

### 本番のコード（新しいパッケージ `cherry.mastersmith.appearance`）

| ファイル | 役割 | 決まり |
|---|---|---|
| `backend/src/main/java/cherry/mastersmith/appearance/package-info.java` ほか `config`・`service`・`web` の `package-info.java` | 機能の範囲と層の説明 | — |
| `appearance/config/AppearanceProperties.java` | `mastersmith.appearance.*` を文字列のまま受ける `record`。既定値なし・`@Validated` なし・列挙に結び付けない | BR1.7、NFR9.1 |
| `appearance/service/BrandColor.java`・`FontFamily.java` | 許される値の列挙。`value()`（小文字の名前）・`defaultValue()`・`allowedValues()` | BR1.1〜BR1.3、C7 |
| `appearance/service/Resolution.java` | 項目ごとの判定の結果（採った値と警告の有無） | BR1.4 |
| `appearance/service/AppearanceResolver.java` | 全域の純粋な関数。`strip` の後 `Locale.ROOT` で小文字にして比べる | BR1.1〜BR1.5、NFR9.2・NFR9.8 |
| `appearance/service/ResolvedAppearance.java`・`AppearanceService.java` | 起動時に1回だけ判定して `final` のフィールドに持つ。許されない値の項目ごとに WARN を1件（キー `property`・`defaultValue`・`allowedValues`） | BR1.6・BR2.1、NFR6.2・NFR6.3・NFR9.4 |
| `appearance/web/AppearanceController.java`・`AppearanceResponse.java` | `GET /api/appearance` → 200 と2項目 | BR3.1・BR3.4・BR3.5、C7 |
| `appearance/web/AppearanceSecurityContributor.java` | order 410。`GET /api/appearance` だけを `permitAll` | BR3.2・BR3.3、NFR4.1〜NFR4.4・NFR4.7 |

### 手を入れたファイル

| ファイル | 変更 |
|---|---|
| `backend/src/main/resources/application.yaml` | `mastersmith.appearance.brand-color`・`font-family`（環境変数、無ければ空）と説明のコメント |
| `.env.example` | 見た目の設定の節（値は空、許される値と既定をコメントで） |
| `common/security/SecurityRuleContributor.java`・`config/SecurityConfig.java`・`auth/web/AuthSecurityContributor.java`・`access/web/AdminSecurityContributor.java`・`auth/web/TokenAuthenticationEntryPoint.java`（いずれも `backend/src/main/java/cherry/mastersmith/` の下） | order の割り当ての説明文だけを、機能の名前の割り当て（auth 100 台・access 200 台・invitation 300 台・appearance 400 台、x00・x50 はテストの決まり）に書き直した。コードの中身は変えていない（9節の決定 2） |
| `backend/build.gradle.kts` | `packagesJudgedByTotal` から `common.security`・`config`・`access.web` を外し、説明のコメントを足した（9節の決定 3）。除外は足していない |
| `perf/k6/scenarios.js`・`perf/README.md` | 場面 `appearance`（トークンなしの GET、checks は 200 と2項目、閾値 `http_req_duration{name:appearance}` の `p(95)<300`） |
| `README.md` | 環境変数の表に2行、「API のアクセス制御（U3）」の公開の一覧に `GET /api/appearance`、新しい節「インスタンスの見た目の設定（U8）」、差し込み口の表の order の割り当て |

`compose.yaml`・`Dockerfile`・`SecurityConfig` の決まりの並び・`CacheControlFilter`・`GlobalExceptionHandler`・Flyway は変えていない。新しい依存は足していない（lockfile は変わっていない）。`vendor/` は触れていない。

### テスト（`backend/src/test/java/cherry/mastersmith/appearance/` の下）

| テスト | 件数（実測） | 主に確かめること |
|---|---|---|
| `config/AppearancePropertiesTest` | 11 | 文字列のまま結び付く、無い・空で失敗しない、任意の文字列、環境変数の名前、`@Validated` なし |
| `service/AppearanceResolverTest` | 42（jqwik の性質 3 を含む） | W1 の判定の例、全角の空白・タブ、ロケール（tr・az・lt）に左右されない、すべての許される値、性質3つ |
| `service/AppearanceServiceTest` | 7 | WARN の件数（0・0・2・1）、キーと値、スタックトレースなし、設定された値を出さない、`current()` が同じインスタンス |
| `web/AppearanceSecurityContributorTest` | 3 | order 410、110・210・100・150・200・250・310 と重ならず最後に並ぶ、同じ値で起動が止まる |
| `AppearanceBoundaryArchitectureTest` | 4 | DB アクセスに依存しない、common 以外の機能（config を含む）に依存しない、ほかから依存されない、web が config を直接使わない |
| `web/AppearanceApiIT` | 17 | security-design.md 3節の表、ヘッダー、監査の件数が変わらない、接続を借りない、健全性の応答が変わらない |
| `web/AppearanceStartupIT` | 4 | `red`・`mono` で起動し blue・sans・WARN 2件・3回の GET で増えない、目印の値がログ全体と応答に出ない、設定なしで WARN 0件、`GREEN`・`Serif` で green・serif |
| `testsupport/ConnectionAcquireCounter` | —（テストの支え） | 接続を借りた回数を HikariCP の指標で数える |

## 2. 実測（Step 1 と Step 17）

どちらも colima を動かし、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡して `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行した（`caffeinate -i` 付き）。対象DB のテストの飛ばしは 0 件。

| 項目 | Step 1（変更の前） | Step 17（変更の後） |
|---|---|---|
| verify の結果 | 成功 | 成功（4 分 57 秒） |
| 単体テスト（`test`） | 989 件（失敗 0・飛ばし 0） | 1,056 件（失敗 0・飛ばし 0）。U8 の分 67 件 |
| 結合テスト（`integrationTest`） | 472 件（失敗 0・飛ばし 0） | 493 件（失敗 0・飛ばし 0）。U8 の分 21 件 |
| 全体のカバレッジ | 行 98.6%（4753/4822）・分岐 94.4%（1802/1908） | 行 98.6%（4815/4884）・分岐 94.5%（1814/1920） |
| Gitleaks | — | no leaks found |
| OSV-Scanner | — | verify の中では UP-TO-DATE（lockfile は変わっていない）。`./gradlew osvScan --rerun` で流し直し、失敗の条件に当たるもの 0 件・警告 0 件 |
| SpotBugs の関門 | — | 通過（除外は足していない）。U8 のコードへの指摘は priority 3 の `IMPROPER_UNICODE`（`AppearanceResolver` の小文字化）と `SPRING_ENDPOINT`（`AppearanceController`）の2件で、関門の基準未満の警告 |

既存のテストは減っておらず、失敗もない。Step 16 の verify（同じコマンド、5 分 4 秒）も同じ件数・同じカバレッジで通った。

## 3. パッケージごとのカバレッジと一覧から外したパッケージ

Step 16・Step 17 の実測（同じ値）。下限は行 80%・分岐 70%。

| パッケージ | Step 1（外す前） | Step 17 | 扱い |
|---|---|---|---|
| `cherry.mastersmith.appearance.service` | — | 行 100%（49/49）・分岐 100%（12/12） | 新しいパッケージ。自動で下限の対象 |
| `cherry.mastersmith.appearance.web` | — | 行 100%（13/13）・分岐なし | 新しいパッケージ。自動で下限の対象 |
| `cherry.mastersmith.appearance.config` | — | 報告に出ない | `AppearanceProperties` だけで、既存の除外 `**/*Properties.class`（設定値だけのクラス）に当たる。除外は足していない（計画 8節） |
| `cherry.mastersmith.common.security` | 行 100%（14/14）・分岐 100%（6/6） | 行 100%・分岐 100% | `packagesJudgedByTotal` から外した |
| `cherry.mastersmith.config` | 行 92.9%（78/84）・分岐 73.3%（22/30） | 行 92.9%・分岐 73.3% | 外した（下限を満たすため、テストの追加は不要だった） |
| `cherry.mastersmith.access.web` | 行 100%（73/73）・分岐 93.8%（15/16） | 行 100%・分岐 93.8% | 外した |

一覧に足したパッケージはない。一覧は 15 件から 12 件になった。

## 4. 接続を借りた回数の数え方の判定（Step 3、U8 R-02）

- 既存の結合テストと同じ形の Spring の文脈（`@SpringBootTest(RANDOM_PORT)` と `TestDatabase`）で、一時の判定用のテストを流して確かめた（確かめた後に消した）。
  - (a) `MeterRegistry`（`SimpleMeterRegistry`）に `hikaricp.connections.acquire`（タグ `application=mastersmith`・`pool=mastersmith-db`）のタイマーがある → 成り立った。
  - (b) `DataSource#getConnection` を1回呼んで閉じると件数が 0 → 1 に増えた → 成り立った。
- よって**指標で数える形**にした（`ConnectionAcquireCounter`）。`DataSource` を包む `BeanPostProcessor` は作っていない。本番のコードは変えていない。
- `AppearanceApiIT` の確かめは、定期の削除の cron を止め（`mastersmith.auth.refresh-token-cleanup.cron=-`）、使用中・待ちの接続が 0 になるまで Awaitility で待ち、先に数え方が働くこと（`getConnection` で増える）を確かめてから、トークンなしの GET を3回送り、再び待ってから件数が同じことを確かめる。

## 5. HEAD・末尾の `/` の実際の扱い（9節の決定 5 の (a)）

| 要求 | 見込み | 実際 |
|---|---|---|
| HEAD・トークンなし | 401 | 401 / `AUTHENTICATION_REQUIRED` |
| HEAD・使えるトークン付き | GET と同じ処理が本文なしで返る | 200・本文なし |
| `/api/appearance/`（末尾の `/`）・トークンなし | 401 | 401 |
| `/api/appearance/extra`・トークンなし | （表に無い。足した確かめ） | 401 |
| OPTIONS・トークンなし | （NFR4.3、公開にしない） | 401 |
| POST・PUT・PATCH・DELETE・使えるトークン付き | 405 / `METHOD_NOT_ALLOWED`、`Allow` に GET | 見込みどおり |

見込みと違うものはなく、GET と HEAD の外のメソッドや別の道が公開になることもなかったため、生成は止めていない。

## 6. E2E の結果（Step 17、9節の決定 4）

- `docker compose --profile mail up -d mailpit` で Mailpit を起動し、`./gradlew e2eTest` を流した。**6 件すべて通過**（010 skeleton 2件・020 auth 2件・030 admin access 1件・040 DSL admin 1件、18.2 秒）。U8 の E2E は足していない。
- Mailpit は止めず消していない（README の手順のとおり開発者が片付ける）。配備したアプリ（`mastersmith-app-1`）と `.env` は変えていない。

## 7. 上流・計画との差（この段で決めたこと）

承認済みの文書は書き換えていない。

| 対象 | 計画・設計 | 実際の作り | 理由 |
|---|---|---|---|
| 層ごとの順番 | 層ごとに実装を書き、その層のテストが通ってから次の層へ（test-after の ordering） | ベースラインの verify の待ち時間に、config・service・web の本番のコードを先に下書きした。下書きした service・web のファイルは作業の場の外へ退避し、config → テスト → service（判定）→ テスト → service（保持）→ テスト → web → テストの順に戻して、各層のテストが通ってから次の層を戻した | ordering の「通ってから次の層へ進む」を守るため。下書きの中身は戻した後も変えていない |
| `AppearanceStartupIT` の件数 | 3 件（`red`・`mono`、設定なし、`GREEN`・`Serif`） | 4 件。「ログと本文に `red`・`mono` の文字列が無い」は、`AppearanceService` のログの値と応答の本文で `red`・`mono` を確かめ、ログ全体への漏れは目印の値（`color-appearance-leak-check` など）で起動する1件を足して確かめた | ログ全体に `red` という短い文字列が無いことは、無関係な語に当たりうるため確かめにならない（unit-test-instructions.md 6節の目印の値の書き方に合わせた） |
| `AppearanceApiIT` の確かめ | security-design.md 3節の表とヘッダー・監査・接続 | 加えて PATCH の 405、PUT・PATCH・DELETE・OPTIONS のトークンなしの 401、`/api/appearance/extra` の 401、`Referrer-Policy`、`/actuator/health` の応答が `status` だけのまま（NFR9.6）を確かめた | NFR4.2・NFR4.3・NFR4.8・NFR9.6 の確かめを厚くするため。新しい応答は作っていない |
| 判定の結果の型 | `Resolution<T>(T value, boolean warned)` の例 | そのとおり `Resolution` を `appearance.service` に置いた | — |
| 警告のログの文言 | 日本語 | `見た目の設定に許されない値が指定されたため、既定の値を使います` | — |
| `AdminSecurityContributor` のクラスの説明文 | 決定 2 の範囲は `ORDER` の説明 | クラスの説明文の「U3 がフィルターの連鎖に足す決まり」「U2 の order 110 の設定を」は order の割り当てではなく単位の名前の記述のため変えていない。`SecurityConfig` の冒頭・引数の「U2・U3」も同じ理由で変えていない | 決定 2 の範囲（割り当てに触れた説明文）に限るため |
| README の差し込み口の表の「使う単位」 | 決定 2 は order の文だけ | 「U2、U3」のままで U8 を足していない | 計画の範囲の外のため。承認の場で足すかを確かめたい |

計画の8節の差（部品の名前、警告のキー、`appearance.config` のカバレッジ、k6 の場面の名前、W3.3 の入口の処理の名前など）は計画のとおりに作った。

## 8. Build and Test に引き継ぐこと

| 項目 | 内容 | 持ち主 |
|---|---|---|
| カバレッジの実測 | 同じコマンドでもう一度実測する（2節・3節の値と比べる） | Build and Test |
| k6 の台本の読み込み | この段では k6 が手元に無く、`node --check` で構文だけを確かめた。`k6 inspect --include-system-env-vars`（`SCENARIO=appearance`）で読み込めることと閾値を確かめる | Build and Test |
| 応答時間の測定 | NFR6.1（同時 10 件で p95 300 ミリ秒以内、全件 200・2項目） | performance-validation |
| 指標と警報・SLO | `uri="/api/appearance"` のラベル、ダッシュボードの式（NFR6.5）。SLO は `Unverified`（NFR9.3） | observability-setup・feedback-optimization |
| スモークテスト | 配備の後に未認証の `GET /api/appearance` が 200 で2項目、起動のログに見た目の設定の WARN が無いこと | deployment-pipeline・deployment-execution |
| u3-invitation の order | 310 を B3 の計画の承認の場で確かめる | B3（U3 のコード生成） |
| U4 との突き合わせ | 画面がトークンを付けずに呼び、C7 の2項目を使うこと | B4 の後半（U4） |
| SpotBugs の priority 3 の警告 | `IMPROPER_UNICODE`（小文字化して ASCII の名前と比べるだけで、結果は許される値のどれかに限られる）・`SPRING_ENDPOINT`（公開の API であることの知らせ）。関門の基準未満で、除外は足していない | 記録だけ |
