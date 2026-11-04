# 配備の流れ（CI/CD）— u4-business-data-time

出典: NFR Design（`construction/u4-business-data-time/nfr-design/performance-design.md`・`security-design.md`・`logical-components.md`、レビュー `nfr-design/reviews/review-01.md`）、NFR Requirements（`nfr-requirements/performance-requirements.md`・`security-requirements.md`・`tech-stack-decisions.md`）、機能設計（`functional-design/functional-spec.md`）、既存の流れの実物（読み取りだけ: ルートの `build.gradle.kts` の `verify` と `e2eTest`、`Dockerfile`、`.github/workflows/ci.yml`）、`team.md`・`project.md`（Deployment・Way of Working・Testing Posture）。書き方は前の単位（U5）と同じ。

U4 は独立した配備の対象ではなく、新しいサーバー・表・列・API・環境変数・コンテナ・クラウドの資源を持たない。新しい設計の論点が無いため、質問は作らず、既にある検査の流れの記録として書く（`project.md`）。

## 1. 流れの中の位置

| 段 | 内容 | U4 で変わること |
|---|---|---|
| 作業ブランチ | `develop` から短命のブランチ（`feature/261102-timezone-ui-polish-b4` など）を作って作業する | なし |
| 統合前の1コマンドの検査 | `./gradlew verify`（フォーマット → リンタ → ライセンスヘッダー → ビルド → 単体テスト → 結合テスト → カバレッジ → 安全の検査 → 成果物） | 段の並びと関門は変えない。足す・直すテストが単体・結合の段で走る（2 節） |
| E2E | `./gradlew e2eTest`（`verify` と CI の外） | 統合の前には流さない（2026-11-04 の依頼者の決定。`team.md` Way of Working）。Intent の最後（Build and Test）とリリースの前に全体を1回流す。新しい流れ 200 を足し、185 に axe の状態を足す |
| 統合 | `develop` へ squash マージ（1 Bolt が `develop` の1コミット。メッセージは日本語） | 固定先の更新は無い |
| CI | GitHub Actions が `develop` へのプッシュ時に `./gradlew verify` を流す。`origin` への `git push` は依頼者自身が行う | CI の側の変更は無い |
| 成果物 | `frontend/dist` を同梱した実行可能 WAR。版はコミットのハッシュで識別 | 画面とサーバーの両方が変わるため、WAR を作り直して配備する |
| 配備 | 開発者の PC 上のコンテナ。配備のたびに起動確認とスモークテスト | 3 節 |

## 2. 検査の関門（この単位が走らせるもの）

実行順は `verify` の既存の段の並びのまま。どれも失敗すると `verify` 全体が失敗し、統合と CI を止める。新しい Gradle のタスク・npm スクリプトは足さない。

| 段 | 検査 | 内容 |
|---|---|---|
| フォーマット | Spotless（Java）・Prettier（画面） | 足す・直すファイルが書式に合う |
| リンタ | oxlint・ESLint・Stylelint | `react/no-danger`、`no-console`、`export default` の禁止、機能どうしの import の制限（`features/tables` は `app/display-settings`・`shared/` だけを使う） |
| ライセンスヘッダー | 既存の検査 | 足す Java・TypeScript・CSS・E2E の先頭に Apache License 2.0 のヘッダー。`businessDataSchema.sql` は今のまま |
| ビルド | Java のコンパイル・SpotBugs（FindSecBugs）・TypeScript の型検査・Vite のビルド | `labelValueType` の型が通る。SQL を足さないため `SQL_` の指摘は当たらない。SpotBugs の除外・ArchUnit の境界テスト（`BusinessDataBoundaryArchitectureTest` など）は緩めない |
| 単体テスト | JUnit（サーバー）、Vitest・fast-check・vitest-axe（画面） | `ReferencePolicyTest`（`labelValueType` の状態ごとの有無と値）、応答の JSON の形、画面の純粋な関数（初めの値・送る値・検索の条件・`formatCell`）と性質ベースのテスト、部品のテスト（`DateTimeField`・`TimeZoneNotice`・フォーム・一覧・詳細・検索・ピッカー）と `vitest-axe`。今の `formDiff`・`searchConditions`・`pickerSearch`・`valueFormat` のテストの書き換え（件数を記録する）。日時のテストはタイムゾーンを引数で固定し、Node の `TZ` を変えても同じ結果になることを1件確かめる（手元は `Asia/Tokyo`、CI の機械は UTC のため。NFR5.2.1） |
| 結合テスト | Spring を起動する `XxxIT` | `ReferenceConnectionUsageIT`（`labelValueType` が付く定義で接続の数が同じ。足すか今の場面で確かめるかはコード生成の計画で1つに決める）、`ReferenceSecretLeakIT`・`BusinessDataSecretLeakIT`・他テーブル参照の認可のテスト（変更なし）、U3 の3種類の対象DB の結合テスト（変更なし） |
| カバレッジ | JaCoCo・`@vitest/coverage-v8` | 全体の下限（行 80%・分岐 70%）。サーバーは `businessdata.domain`・`businessdata.web`（パッケージごとの下限の対象）を満たす。除外を増やさない。`src/features/tables/` の値は Build and Test で記録する |
| 安全の検査 | Gitleaks・OSV-Scanner | 新しい依存が無く、lockfile を変えない |
| 成果物 | WAR の組み立て | 既存のタスクのまま |

### 関門の基準（変えないもの）

- 新しい依存を足さない（`tech-stack-decisions.md`）。
- `./gradlew verify` の時間は直近の記録で約 28 分（CI の上限の7割の約 42 分が線）。U4 の追加は単体・部品のテストと結合テストの数本で、増えは分のオーダーに収まる見込み。Bolt の後と Build and Test で測って記録し、42 分を超えたら依頼者に諮る。
- E2E は流れを1本だけ足す（200）。185 への状態の追加は本数に数えない。

## 3. 配備・戻し方・スモークテスト

### 3.1 配備の手順（Dockerfile は WAR を写すだけ）

1. 配備の前に、配備するコミットで `./gradlew verify` が作った WAR を使う。そのコミットのハッシュと WAR の SHA-256 を並べて記録し、WAR の中身に `labelValueType` の文字列が入っていること（`ReferenceFieldResponse` のクラスと `static/assets/` の JS の両方）を確かめる（Dockerfile は手元の WAR を写すだけで、日時では古い WAR を見分けられないため）。
2. `docker compose up -d --build app` の後、`docker inspect` でコンテナのイメージ ID が `mastersmith:local` の ID と一致することを確かめ、食い違うときは `--force-recreate --no-deps` で作り直す。
3. ヘルスチェックが通るまで配備完了とみなさない。
4. 内部DB・設定の変更が無いため、入れ替えの前の内部DB の複写は要らない。

### 3.2 戻し方

前の版のイメージへの入れ替えだけ。内部DB・環境変数・設定の変更が無く、応答は項目を足すだけ（前の版の画面は `labelValueType` を読み捨てる）ため、戻しで失うデータは無い。戻すと、業務データの時刻は UTC の表示と `datetime-local` の入力に戻る。戻しの練習は行わない（前の Intent の方針と同じ）。

### 3.3 スモークテスト（配備の後。手順は Deployment Pipeline、実行は Deployment Execution）

ログインなどパスワードが要る操作は依頼者が行い、AI は画面の結果の報告と監査・ログで裏付ける（`project.md`）。業務データの書き込みは、見本の対象DB の架空の行か、確かめ用に作った行だけで行う。

前提（スモークテストの前に確かめる）:
- 使う表は、見本の対象DB（`docker/targetdb/postgres/01-sample-schema.sql`）で時点を表す型の列を持つ唯一の表 `sales.customers`（`created_at TIMESTAMP WITH TIME ZONE`）。
- 配備したアプリで適用中の DSL に `sales.customers` が載り、`created_at` が一覧・詳細に出て、作成・更新の欄と検索（範囲）の対象になっていること。DSL の画面か適用中の DSL の取り出しで、値を出さずに設定だけを確かめる。載っていなければ S1〜S5 は成り立たないため、Deployment Execution で、`sales.customers` を含む DSL を使い捨ての確かめとして適用してよいかを依頼者に諮る（勝手に適用しない）。
- 書き込みのアカウント（`03-writer-account.sh`）が見本の対象DB にあり、アプリの `.env` に書き込みの接続の設定があること（項目の有無だけを見る。値は見ない）。依頼者の利用者が `sales.customers` の作成・更新の権限を持つこと。
- 後始末: S3・S4 で作った行は、名前に確かめ用の目印（例: `U4 確かめ`）を付けて作り、スモークテストの終わりに画面の削除で消す。消したことと件数を記録する。前からある見本の行は変えない。

| 項目 | 確かめ | 期待 |
|---|---|---|
| S1 | プリファレンスで表示のタイムゾーンを選び、時点を表す型の列を持つ表の一覧を開く | 見出しの下に案内の1行が出て、時刻が選んだタイムゾーンと時差で出る |
| S2 | 詳細を開く | 同じ時刻と案内の1行 |
| S3 | 作成・更新の画面で時刻の欄を見る | 「{名前} ({時差}) で入力」の案内。夏時間の地域なら存在しない時刻の誤り・重なる時刻の2択が出る |
| S4 | 更新で別の欄だけ直して保存する | 時刻が変わらない |
| S5 | 検索で時刻の範囲を入れる | 両端を含む結果 |
| S6 | 参照の選択肢に時点を表す型のラベルがある表（用意できるとき） | ラベルが表示のタイムゾーンで出る |
| S7 | 手元の監視を起動して、ダッシュボードの「業務データ」の区画 | S1〜S6 の間の業務データの API の 5xx が 0 |

監査の期待: 業務データの読み取りは監査に残らない（今の決まり）。作成・更新は今の業務データの書き込みの監査の決まりのまま。依頼者が画面で行った書き込みの回数をその場で聞き、配備の後に数を合わせる（`project.md`）。

## 4. Build and Test・Deployment 以降へ渡すこと

| # | 渡すこと | 持ち主の段 |
|---|---|---|
| H1 | k6 の既存の業務データの場面（`bizDefinition`・一覧・詳細・参照の場面）を台本を変えずに流し、今の目標を満たすこと（NFR2.1.2）。測れなければ `Unverified` | Build and Test（Performance Validation の段が無いため） |
| H2 | E2E の全件（200 を含む）の通過と、185 の axe の違反 0 件を件数で記録する。表 `e2e_event` の追加が既存の流れに影響しないことは、既存の DSL（`businessDataDsl.ts`）が表を明示に列挙していることで足りる（180・185・190・195 は 200 より前に流れ、200 が置き換える DSL の影響も受けない）。`e2e_event` の SQL は準備の SQL（単一のトランザクション）に入り、誤ると全 E2E が止まるため、全体を流す前に準備だけ（または 180 を単独）で通ることを確かめる（コード生成の段で、見本の PostgreSQL の使い捨てのスキーマで `BEGIN … ROLLBACK` により構文を確かめ済み） | Build and Test |
| H3 | 書き換えた単体テスト（`formDiff`・`searchConditions`・`pickerSearch`・`valueFormat` など）の前と後の件数 | Build and Test |
| H4 | `verify` と CI の時間、`src/features/tables/` のカバレッジの値を記録する | Build and Test |
| H5 | 2択を別の `FormField` に置く組み方が axe を通ること（NFR Design のレビューの指摘。通らなければ legend なしの形へ切り替える案） | Code Generation の最初 |
| H6 | 配備の前に WAR の SHA-256 と中身を確かめる（3.1） | Deployment Execution |
| H7 | スモークテスト S1〜S7（3.3）と、監査の件数の確かめ | Deployment Execution |

## 5. 承認の場の決定

U4 の確かめの場（2026-11-04）で、依頼者が Request Changes を選び、レビュー（`reviews/review-01.md`）の指摘をまとめて直した。

| 指摘 | 直したところ |
|---|---|
| R-01（Major）スモークテストの前提 | 3.3 に、使う表 `sales.customers`、適用中の DSL に載っているかの確かめと載っていないときの扱い、書き込みのアカウント、作った行の後始末を書いた |
| R-02 WAR の確かめ方 | 3.1 の手順1を、コミットのハッシュと SHA-256 の対応づけと、`labelValueType` の文字列の有無の確かめにした |
| R-03 200 の DSL | `infrastructure-specification.md` の Shared Infrastructure と 4 節の H2 に、200 が適用中の DSL を置き換えることと、200 より後の流れの決まり、表の追加の影響の読み方を書いた |
| R-04 NFR5.2 | 2 節の単体テストの行に `TZ` に依らない確かめを足し、`traceability.json` の NFR5.2 をそこに向けた |
| R-05 準備の SQL | 4 節の H2 に、全体を流す前に準備だけで通ることを確かめる手順を足した |

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- NFR Design: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/nfr-design/performance-design.md`・`security-design.md`・`logical-components.md`・`reviews/review-01.md`
- NFR Requirements: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/nfr-requirements/`
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/functional-design/functional-spec.md`
- 既存の実物（読み取り）: `build.gradle.kts`・`Dockerfile`・`.github/workflows/ci.yml`・`perf/k6/scenarios.js`
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] `./gradlew verify` の直近の時間（約 28 分）は前の単位の記録の値。U4 の Bolt の後に測り直す。
- 未解決の点は無い。
