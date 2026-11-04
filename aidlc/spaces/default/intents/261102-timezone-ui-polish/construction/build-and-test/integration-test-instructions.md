# 結合の確かめの手順 — Intent 261102-timezone-ui-polish

Test Strategy は Standard。単位の間の境目と、外の部品（対象DB・ブラウザ）との境目を確かめる。どれも Build and Test で流した（`test-results.md`）。

## 単位の間の境目

| 境目 | 受け渡し | 確かめるもの | 置き場 |
|---|---|---|---|
| U1 → U2・U4 | 画面の変換の関数（`shared/format/timeZone`） | 表示のタイムゾーンでの書式・入力の判定が、U2 の管理の画面と U4 の業務データの画面で同じ結果になる | 画面のテスト（Vitest）と E2E 100・185・200 |
| U2 → U4 | 表示のタイムゾーンの配布（`useDisplayTimeZone` の `loading`・`ready`・`error`） | U4 の一覧・詳細・フォーム・検索が3つの状態で決まりどおりに振る舞う（失敗の間はフォームを開かない） | `useTablesTimeZone`・ページのテスト、E2E 185 の読み込みの失敗の状態 |
| U3 → U4 | 業務データの API の時刻の値（UTC の時点・桁の固定・`timetz` の時差） | U4 の画面が桁を落とさずに出し（`.000000`）、更新で変えていない日時を送らない | 3種類の対象DB の結合テスト（U3）、E2E 200（実際の PostgreSQL） |
| U4 のサーバー → U4 の画面 | 定義の応答の `labelValueType` | 型の有無で参照のラベルの書式が変わり、項目が無い・知らない値でも壊れない | `ReferenceLookupTest`・`BusinessDataReadControllerTest`・`referenceField.test.ts`、E2E 195 の形の比べ |
| U5 → U6 | ホーム・上のバーのロゴ（`AppIcon`） | 画面の枠とホームのアクセシビリティ | E2E 050・160 |

## 外の部品との境目

| 相手 | 確かめ | 命令 |
|---|---|---|
| 対象DB（MySQL・MariaDB・PostgreSQL） | Testcontainers の結合テスト（日時の型・桁・読めない値・初期化のタイムゾーン） | `./gradlew :backend:cleanIntegrationTest :backend:integrationTest`（`verify` の中） |
| 実際のブラウザ（Chromium） | Playwright の E2E 298 件（流れと axe） | `./gradlew e2eTest` |
| 内部DB（組み込みの H2） | 移行 V14 と互換の結合テスト | `verify` の中 |

## カバレッジの目標

- 全体と、手を入れたパッケージごとに行 80%・分岐 70% 以上（`team.md`）。画面は全体の `thresholds`。`src/features/tables/` の値を記録する。
- 下限・除外は変えない。

## 環境とデータ

- E2E は見本の PostgreSQL の実行ごとのスキーマ（`e2e_biz_<時刻>_<印>`）と一時のロールを使い、流し終えたら消す。表 `e2e_event` は U4 で足した。
- 時刻の確かめは Playwright の `timezoneId`（`Asia/Tokyo`）を固定し、流れの中で表示のタイムゾーンを `America/New_York` に変える。

## Sources

- 各単位の `construction/*/code-generation/unit-test-instructions.md`
- `construction/u4-business-data-time/infrastructure-design/cicd-pipeline.md`
- `docs/verification.md`
