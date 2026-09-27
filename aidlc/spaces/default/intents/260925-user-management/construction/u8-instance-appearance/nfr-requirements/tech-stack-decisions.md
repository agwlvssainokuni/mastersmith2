# Tech Stack Decisions — U8 インスタンスの見た目の設定（u8-instance-appearance）

U8 の技術の選定と、テストの要件です。技術は前の Intent から変えない（Java・Spring Boot・Spring Security・Micrometer・JUnit・jqwik・ArchUnit。`aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`）。答えは `nfr-requirements-questions.md`（Consolidated Summary Confirmation: Looks correct）。

出典の略号: NFR・FR は `aidlc/spaces/default/intents/260925-user-management/inception/requirements-analysis/requirements.md`、BR はこの単位の `construction/u8-instance-appearance/functional-design/rules.md`、「要点 n」はこの段の `nfr-requirements-questions.md` の「NFR の要点（案）」の番号。枝番はこの単位の中で振る。

## 1. 選定

U8 は新しい依存を足さない。ライセンスの確認は要らない（`aidlc/spaces/default/memory/team.md` の Code Style）。

| 対象 | 選定 | 理由 |
|---|---|---|
| 置き場 | 新しいパッケージ `cherry.mastersmith.appearance` に `web`・`service`・`config` を置く。`domain`・`repository` は作らない | 機能設計の functional-spec.md の1節。内部DB に触れない（NFR5.1） |
| 設定の受け取り | この機能の設定の型（`XxxProperties` の `record`、`appearance` のパッケージの中、Spring Boot の設定の結び付け）。2項目を文字列で受け取り、列挙に結び付けない。検証の注釈（`@Validated`）を付けない | BR1.7、NFR9.1。列挙や検証で受けると、許されない値で起動が止まる |
| 設定の項目 | `mastersmith.appearance.brand-color`・`mastersmith.appearance.font-family`（環境変数 `MASTERSMITH_APPEARANCE_BRAND_COLOR`・`MASTERSMITH_APPEARANCE_FONT_FAMILY`）。`application.yaml` は既存の書き方どおり空を既定にし、`.env.example`（値は空）と README に足す | 機能設計の functional-spec.md の2節 |
| 値の判定 | 前後の空白を除き（`String.strip`）、実行環境の言語の設定に左右されない大文字・小文字の比べ方（`Locale.ROOT`）で許される値と比べる純粋な関数。既存の `targetdb/domain/DatabaseProduct` と同じ書き方 | BR1.1・BR1.2、要点 11 |
| 値の保持 | 起動時に1回だけ解決し、変わらない値（`record`）として業務処理の Bean に持つ | BR1.6、NFR6.2・NFR6.3 |
| 公開の決まり | 既存の差し込み口 `SecurityRuleContributor` を U8 の Bean として置き、`GET` と `/api/appearance` に限って認証を求めない決まりを足す | BR3.2、NFR4.1・NFR4.7 |
| 応答 | `record` の DTO（`XxxResponse`）で2項目だけを返す。エラーの `code` の一覧は作らない | `team.md` の Code Style、BR3.1、NFR4.5 |
| ログ | SLF4J の `LoggerFactory.getLogger` とキー・値の API | `team.md` の Code Style、NFR9.4 |
| 指標・トレース | 既存の Micrometer の HTTP の指標と Micrometer Tracing のまま | NFR6.5・NFR6.6 |
| 負荷の試験 | 既存の k6 の `perf/k6/scenarios.js` に場面を1本足す | NFR6.1、Q1: A |

## 2. テストの要件

テストの置き場と名前は既存の決まりどおり（単体は `XxxTest`、Spring を起動する結合テストは `XxxIT`、説明文は英語）。個々のテストは各要件の「確かめ方」に書いた。

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR9.7 | 新しいパッケージ `cherry.mastersmith.appearance`（下位の `web`・`service`・`config`）は、パッケージごとのカバレッジの下限（行 80%・分岐 70%）の対象になる。U8 のために計測の除外を増やさない（除外は `team.md` の決まりの範囲と、今のビルドの設定のまま）。既存の一覧（`backend/build.gradle.kts` の `packagesJudgedByTotal`）で外しているパッケージには手を入れない見込みで、`common.security` の説明文の書き足し（NFR4.7）はコードの中身を変えない | `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、値を記録する（`project.md` の Testing Posture） | NFR9、`team.md` の Testing Posture、要点 12 |
| NFR9.8 | 値の判定の純粋な関数に、jqwik の性質ベースのテストを当てる。性質: 任意の文字列を与えても結果は常に許される値のどれか / 許される値の大文字・小文字と前後の空白の揺れは同じ結果になる / 許される値に一致しない空でない値は既定になり「警告あり」になる。失敗時の乱数の種を記録する | 単体テスト（jqwik） | NFR9、BR1.1〜BR1.4、`team.md` の Testing Posture、要点 11 |

## 3. 承認済みの文書との差

承認済みの文書は書き換えない（`aidlc/spaces/default/memory/project.md` の Way of Working）。この段の決定で、承認済みの文書と食い違う点は無い。

| 決定 | 文書 | 承認済みの記述 | この段での扱い |
|---|---|---|---|
| 応答時間の目標 300 ミリ秒（Q1: A） | `inception/requirements-analysis/requirements.md` の NFR6 | U8 の API の目標は無い | 追加（NFR6.1）。食い違いではない |
| HEAD・OPTIONS を公開にしない | `construction/u8-instance-appearance/functional-design/rules.md` の BR3.2 | 「GET だけ公開」。HEAD の名指しは無い | BR3.2 を文字どおりに読んだ追加（NFR4.3）。実際の扱いはテストで確かめ、違えばコード生成で記録する |
| `SecurityRuleContributor` の説明文に U8 の order の範囲を足す | 同じ `common/security/SecurityRuleContributor.java` の説明文 | U2 の 100 台・U3 の 200 台だけ | コード生成の計画で拾う（機能設計の R-01 を受け入れた扱いのまま） |
