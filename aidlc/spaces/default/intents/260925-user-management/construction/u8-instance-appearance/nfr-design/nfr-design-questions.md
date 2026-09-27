# NFR Design — Questions（U8 インスタンスの見た目の設定 / u8-instance-appearance）

U8 は、ブランドカラーとフォントファミリーを設定から起動時に1回だけ読み、ログインなしで読める `GET /api/appearance`（契約 C7）で返す小さなバックエンドです（種類 service のため、性能・セキュリティ・拡張性・信頼性・観測性の各設計と logical-components・traceability のすべてを作ります）。非機能の設計のほとんどは、承認済みの NFR 要件・機能設計・契約と既存のコードの前例で決まっているため、まず NFR 設計の要点（案）を示し、上流から決まらない2点（差し込み口の order の割り当てと、内部DB の接続を借りないことの結合テストの作り）だけを質問にします。

読んだ上流:

- 承認済みの NFR 要件 `aidlc/spaces/default/intents/260925-user-management/construction/u8-instance-appearance/nfr-requirements/`（NFR4.1〜NFR4.8、NFR5.1・NFR5.2、NFR6.1〜NFR6.6、NFR9.1〜NFR9.8、`nfr-requirements-questions.md` の Q1: A と要点 13 件）と、承認の場の決定（Minor の R-01: NFR4.7 の「U3」が前の Intent の旧 U3（`access`）か、この Intent の u3-invitation かを分かるように書き分ける。コード生成の計画で拾う扱い）
- 承認済みの機能設計 `construction/u8-instance-appearance/functional-design/`（`functional-spec.md` の1節の置き場・W1〜W3・4節の境界、`rules.md` の BR1.1〜BR3.6）
- 契約 `aidlc/spaces/default/intents/260925-user-management/inception/contract-design/contract-summary.md`（共通の決まりの認可、C7、未解決の点の C7）
- 部品の一覧 `inception/domain-design/components.md`（InstanceAppearance の `depends_on: []`）
- U3 の承認済みの機能設計・NFR 要件 `construction/u3-invitation/functional-design/rules.md`・`nfr-requirements/security-requirements.md`（登録の完了の2つの POST を U3 の差し込み口で公開にし、order は既存と U8 の値に重ならないものをコード生成で決める）
- 決まり `aidlc/spaces/default/memory/team.md`・`project.md`
- 既存のコード `backend/src/main/java/cherry/mastersmith/common/security/SecurityRuleContributor.java`・`SecurityExtensionValidator.java`・`config/SecurityConfig.java`・`auth/web/AuthSecurityContributor.java`（order 110）・`access/web/AdminSecurityContributor.java`（order 210）・`common/web/CacheControlFilter.java`・`backend/build.gradle.kts`（`packagesJudgedByTotal`）
- 既存のテスト `backend/src/test/java/cherry/mastersmith/ArchitectureTest.java`・`auth/AuthBoundaryArchitectureTest.java`（機能ごとの境界テストの前例）・`access/testsupport/PublicApiTestRules.java`（order 250）・`auth/testsupport/ProtectedTestEndpoint.java`（order 150）・`common/testsupport/TestSecurityExtensions.java`・`common/security/SecurityExtensionValidatorTest.java`（100・150・200）・`config/H2CompactionByPoolSuspensionIT.java`（プールの一時停止）・`common/observability/ExternalExportIT.java`（結合テストで `MeterRegistry` を読む前例）

## NFR 設計の要点（案）

1. **部品の構成（logical-components）**: 新しいパッケージ `cherry.mastersmith.appearance` に3つの層だけを置く。`config` に設定の型（2項目を文字列で受ける `record`、`@Validated` なし。NFR9.1）、`service` に値の判定の純粋な関数と、起動時に1回だけ解決した変わらない値（`record`）を持つ Bean、`web` に `GET /api/appearance` の受け口・応答の `record` の DTO・公開の決まりの差し込み口の Bean。`domain`・`repository` は作らない（機能設計の1節）。差し込み口を `web` に置くのは既存の前例（`auth/web/AuthSecurityContributor`・`access/web/AdminSecurityContributor`）どおり。
2. **判定の関数の置き場**: 判定（前後の空白を除き `Locale.ROOT` で大文字・小文字を問わず比べる全域の関数）は `service` に置く。書き方は既存の `targetdb/domain/DatabaseProduct` にならうが、承認済みの機能設計が `domain` を作らないと決めているため、置き場は `service` とする（承認済みの設計と食い違わない）。許される値（色4つ・フォント2つ）は `service` の中の型で表し、`web` は小文字の名前だけを DTO に写す。
3. **性能**: 判定と警告は、`service` の Bean を作るとき（コンストラクター注入で設定を受け取った時点）に1回だけ行う。要求の処理は保持した `record` の2つの値を DTO に写して返すだけで、設定を読み直さず、判定もしない（NFR6.2）。キャッシュの見出しは既存の `CacheControlFilter` の `no-store` のまま（NFR4.8）。負荷の試験は既存の `perf/k6/scenarios.js` に場面を1本足し（名前はコード生成）、performance-validation の段で同時 10 件・p95 300 ミリ秒（NFR6.1）を測る。
4. **拡張性**: 状態は起動時に作る変わらない `record` 1つだけで、ロック・同期・共有の仕組みを置かない（NFR6.3）。インスタンスを増やす場合も、各インスタンスが同じ環境変数を起動時に読めば同じ値を返す（NFR6.4）。
5. **信頼性（失敗の道を作らない）**: 設定は文字列で受け、判定はどの入力でも許される値を返す全域の関数とし、例外を投げない。外部の呼び出し・内部DB・ほかの単位への依存が無いため、サーキットブレーカー・再試行・時間切れ・代わりの値の仕組みは当てない（当てる相手が無い）。健全性の判定（HealthIndicator）は足さない（NFR9.6）。想定外の失敗は既存の共通のエラー応答（500 / `INTERNAL_ERROR`）に任せる（NFR9.2）。
6. **信頼性（内部DB に依存しないことの構造の検査、NFR5.1）**: 既存の機能ごとの境界テストの前例（`AuthBoundaryArchitectureTest` など）どおり、`AppearanceBoundaryArchitectureTest` を足す。決まりは「`appearance..` は `..repository..`・`javax.sql..`・`jakarta.persistence..`・`org.springframework.jdbc..`・`org.springframework.data..`・`com.zaxxer.hikari..` に依存しない」「`appearance..` は `common..` を除くほかの機能のパッケージに依存しない」「ほかの機能は `appearance..` に依存しない」。既存の `ArchitectureTest`（全体の層の決まり）は変えない。
7. **信頼性（接続を借りないことの結合テスト、NFR5.2）**: 作りは Q2 で決める。
8. **セキュリティ（公開の決まり）**: U8 の差し込み口は `authorizeHttpRequests` に「メソッド GET・道 `/api/appearance`」の認証なしの決まりを1つ足すだけにし、トークンの検証・401 の入口の処理・403 の処理・ヘッダー・CSRF は変えない（既存の `auth`・`access` の設定のまま）。メソッドを GET に限って合わせるため、`HEAD`・`OPTIONS` と末尾に `/` の付いた道は公開の決まりに当たらず、`/api/**` の既定（ログインが必要）で 401 になる見込み（NFR4.3。実際の扱いはコード生成のテストで確かめ、違えば記録する）。サーバー側の結合テストは、未認証の GET が 200、未認証の POST が 401 / `AUTHENTICATION_REQUIRED`、使えるトークン付きの POST が 405 / `METHOD_NOT_ALLOWED` で `Allow` に GET を含む、未認証の HEAD が 401、使えないトークン付きの GET が 401、使えるトークン付きの GET が 200（NFR4.1〜NFR4.4）。これらのテストでは、`/api/**` を公開にするテスト用の決まり（`PublicApiTestRules`、`mastersmith.test-fixture.public-api`）を有効にしない。
9. **セキュリティ（order）**: 割り当ての値と、`SecurityRuleContributor` の説明文の書き方は Q1 で決める。どの値でも、U8 の決まりはほかの決まりと道が重ならないため、呼ばれる順番でふるまいは変わらない。重なりは既存の `SecurityExtensionValidator` で起動が止まる（NFR4.7）。
10. **セキュリティ（応答とログの中身）**: 応答の DTO は `brandColor`・`fontFamily` の2項目の `record` だけで、値は C7 の列挙の小文字の名前（NFR4.5）。回数の制限は設けない（NFR4.6）。起動時の警告は SLF4J のキー・値の API で、WARN・スタックトレースなし、項目の名前・使った既定の値・許される値の一覧だけを出し、設定された値は出さない（NFR9.4。キーの名前はコード生成で決める）。警告は Spring の文脈ごとに Bean を作るときの1回だけで、要求のたびには出ない。
11. **観測性**: 独自の指標・スパン・属性・健全性の判定は足さない。要求の数・誤り・応答時間は既存の `http.server.requests`（`uri="/api/appearance"`）とトレースで見る（NFR6.5・NFR6.6）。observability-setup の段で既存のダッシュボードの式に値が出ることを確かめる。手元の監視を常に動かしていない間の SLO の判定は `Unverified` とし、持ち主の段に引き継ぐ（NFR9.3）。監査の出来事は出さない（NFR9.5）。
12. **テストとカバレッジ**: 判定の関数に jqwik の性質ベースのテストを当て、失敗時の乱数の種を記録する（NFR9.8）。`appearance.config`・`appearance.service`・`appearance.web` はパッケージごとのカバレッジの下限（行 80%・分岐 70%）の対象になり、計測の除外は増やさない（NFR9.7）。`SecurityRuleContributor` の説明文の書き足し（Q1）は、`packagesJudgedByTotal` にある `common.security` の説明文だけの変更で、コードの中身を変えない扱い（承認済みの NFR9.7 のとおり）。

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| `GET /api/appearance` は同時 10 件で p95 300 ミリ秒以内。performance-validation の k6 で測る | NFR6.1（Q1: A） |
| 値は起動時に1回だけ解決し、変わらない値として持つ。要求の処理で読み直さない。待ち合わせを置かない | NFR6.2・NFR6.3、BR1.6 |
| パッケージは `appearance` の `web`・`service`・`config` だけで、`domain`・`repository` は作らない | 機能設計 `functional-spec.md` の1節、`tech-stack-decisions.md` の選定 |
| 設定は文字列で受け、列挙や `@Validated` に結び付けない。起動を止めない | NFR9.1、BR1.7 |
| 公開は GET だけ。ほかのメソッドは既存の 401・405 に任せ、HEAD・OPTIONS も公開にしない | NFR4.1〜NFR4.3、BR3.2 |
| 使えないトークン付きの GET の 401 は受け入れる | NFR4.4、BR3.3 |
| 応答は2項目だけ。回数の制限は設けない。ヘッダーとキャッシュは既存のまま | NFR4.5・NFR4.6・NFR4.8、BR3.4・BR3.6 |
| 内部DB に依存しないことを ArchUnit と結合テストの両方で確かめる | NFR5.1・NFR5.2 |
| 独自の指標・スパン・健全性の判定は足さない。監査に残さない | NFR6.5・NFR6.6・NFR9.5・NFR9.6 |
| 新しい依存は足さない | `tech-stack-decisions.md` の1節 |
| 機能ごとの境界テスト（`XxxBoundaryArchitectureTest`）を置き、既存の境界テストを緩めない | `team.md` の Code Style、既存の `auth`・`audit`・`dsl` などの前例 |

判定の関数の置き場（候補の論点の3つ目）は、承認済みの機能設計が `domain` を作らないと決めているため質問にせず、要点 2 のとおり `service` とします。

## Q1. 差し込み口（`SecurityRuleContributor`）の order の割り当てを、どうしますか？

理由: 既存の説明文は「U2 は 100 台（100〜199）、U3 は 200 台（200〜299）」ですが、この「U2・U3」は前の Intent（`260922-auth-audit-base`）の単位（`auth` の 110、`access` の 210）のことで、この Intent の u2-user-preferences・u3-invitation と名前が重なります（NFR 要件の承認の場の R-01）。この Intent では u3-invitation（Bolt B3）と U8（Bolt B4）がそれぞれ新しい差し込み口を足し、U3 の承認済みの設計は「U8 の値に重ならないものをコード生成で決める」としています。テストの決まりは 100・150・200・250 を使い、本番の値は 110・210 と「x10」を使う慣習です（重なるとテストの文脈で起動が止まるため、テストの値も避けます）。どの値でも、各単位の決まりは道が重ならないため、ふるまいは変わりません。

- A. 単位の番号ではなく機能の名前で 100 台ずつ割り当てる。`auth` 100 台・`access` 200 台・`invitation` 300 台・`appearance` 400 台とし、U8 は 410、u3-invitation は 310 を使う前提にする（作る順の B3 → B4 と同じ並び）。`SecurityRuleContributor` の説明文の割り当てを機能の名前に書き直し、「x00・x50 はテストの決まりが使う」と足す。U3 の値は、U3 の NFR 設計・コード生成にこの割り当てとして引き継ぐ（推奨）
- B. U8 だけを決め、300 台（310）を使う。u3-invitation の値は U3 のコード生成で決める（説明文には `appearance` の 300 台だけを足し、「U2・U3」は前の Intent の単位だと注記する）
- X. Other (please specify)

[Answer]: A

## Q2. トークンを付けない `GET /api/appearance` が内部DB の接続を借りないこと（NFR5.2）を、結合テストでどう確かめますか？

理由: NFR5.2 は「測り方の具体はコード生成で決める」としていました。U4 の W2 のとおりこの応答はすべての画面の最初の描画を待たせるため、接続プールが尽きたときや、内部DB の詰め直しの一時停止（JMX の操作）のあいだも待たされないことが要ります。確かめ方は2つ考えられます。1つは、接続を借りた回数を要求の前後で比べる方法です（既存の結合テストは `MeterRegistry` を読む前例があり、HikariCP の指標 `hikaricp.connections.acquire` の件数を使える見込み。テストの文脈で指標が取れなければ、テストの中だけで `DataSource` の接続の取得を数える包みに替える）。認証・認可の段階を含む要求の全体で、借りた回数が増えないことを確かめられ、ほかの結合テストと同じ文脈で動きます。もう1つは、プールを一時停止した状態で GET を送り、短い時間で 200 が返ることを見る方法です（既存の `H2CompactionByPoolSuspensionIT` と同じ手順）。心配している場面そのものを再現できますが、テストの既定で切っている MBean の登録を有効にした専用の Spring の文脈が要り、テストの時間が増えます。借りた回数が増えないなら一時停止で待たされることも無いため、前者だけでも論理的には足ります。

- A. 接続を借りた回数（`hikaricp.connections.acquire` の件数、取れなければテストの中だけの数える包み）が、トークンを付けない GET の前後で増えないことを確かめる。既存の結合テストと同じ文脈で動かす（推奨）
- B. プールを一時停止した状態で、トークンを付けない GET が短い時間（例: 2 秒）のうちに 200 を返すことを確かめる（専用の文脈）
- C. A と B の両方
- X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめ:

- NFR 設計の要点（案）は冒頭の 12 件のとおり（部品の構成は config・service・web の3層、判定の関数は service、要求の処理は保持した値を写すだけ、失敗の道を作らない、AppearanceBoundaryArchitectureTest で内部DB に依存しないことを確かめる、公開の決まりは GET と /api/appearance だけで HEAD・OPTIONS は 401 の見込み、応答は2項目・警告のログに設定の値を出さない、独自の指標は足さない、判定の関数に jqwik、新しいパッケージはパッケージごとの下限の対象）
- Q1 A: 差し込み口の order は単位の番号ではなく機能の名前で 100 台ずつ割り当てる（auth 100 台・access 200 台・invitation 300 台・appearance 400 台）。U8 は 410、u3-invitation は 310 とし、`SecurityRuleContributor` の説明文を機能の名前に書き直して x00・x50 はテストの決まりが使うことを足す（NFR 要件の R-01 を片づける）。U3 の NFR 設計とコード生成に 310 を引き継ぐ
- Q2 A: トークンを付けない GET の前後で接続を借りた回数（`hikaricp.connections.acquire` の件数、取れなければテストの中だけの数える包み）が増えないことを、既存の結合テストと同じ文脈で確かめる

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
