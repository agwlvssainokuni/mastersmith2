# Unit Test Instructions — U5 DSL の管理画面（u5-dsl-admin-ui）

U5 のテストの道具・実行のしかた・カバレッジの目標・差し替えの方針・テストのデータの扱いを示す。テストの量は Standard（部品ごとに 5〜8 件の画面部品のテストと、境界の結合テスト）に、team.md の「画面部品ごとにアクセシビリティ検査1件」を加える。

## 1. 道具と設定

| 用途 | 道具 | 設定の場所 |
|---|---|---|
| 画面部品・単体のテスト | Vitest・Testing Library（jsdom）・user-event・vitest-axe（既存） | `frontend/vite.config.ts`（または既存の Vitest の設定）、`frontend/package.json` の `test`・`test:coverage` |
| フロントエンドのカバレッジ | `@vitest/coverage-v8`（`thresholds` で行 80%・分岐 70%、既存） | 既存の設定 |
| アクセス制御の一括の確かめ | JUnit 5・既存の HTTP の補助・監査の補助 | `backend/build.gradle.kts` の `integrationTest` |

新しいテストの設定のファイルと依存は足さない。テストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置く。

## 2. この単位のテストの実行のしかた

リポジトリのルートで実行する。

画面の DSL の機能のテスト:

```bash
npm --prefix frontend run test -- src/features/dsl
```

ApiClient の拡張のテスト:

```bash
npm --prefix frontend run test -- src/shared/api-client
```

アクセス制御の一括の確かめ（バックエンドの結合テスト、組み込みの H2）:

```bash
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.dslmanage.web.DslAccessControlIT'
```

- `src/features/dsl` のコマンドは、最初のテスト（計画の Step 3）より前は対象のテストが無いため失敗する（Vitest は一致するテストが無いと失敗する）。これは想定どおり。
- 画面の依存は `./gradlew verify` の準備の段（`frontendInstall`・`vendorBuild`）で入る。単独で実行する前に、一度 `./gradlew frontendInstall vendorBuild` を実行しておく。
- テストの件数を報告するときは、バックエンドは `:backend:cleanIntegrationTest` を先に付けて実行し、実測の数字だけを報告する（project.md の Testing Posture）。

## 3. カバレッジの目標

- フロントエンド: 行 80% 以上・分岐 70% 以上（既存の `thresholds`）。`frontend/src/features/dsl/` と ApiClient の拡張の部分も、全体の下限を下げないようにテストを置く。
- バックエンド: `DslAccessControlIT` はテストだけで本番のコードを足さないため、既存の下限（全体と新しいパッケージごと）を保つ。
- 計測から外すものを足さない。

## 4. 差し替え（モック・スタブ）の方針

- 画面部品のテストでは、部品に値と操作を渡して振る舞いを確かめる。`DslAdminPage` のテストでは `dslApi` の関数を差し替え（`vi.fn`）、成功・422・409・404・503（設定が無い・接続できない・`DSL_BUSY`）・通信の失敗を返す。
- `dslApi` のテストでは、既存の ApiClient のテストの形に合わせて `fetch` を差し替え、要求の形（パス・方法・`Content-Type`・本文・`source`・`previewId`）と応答の読み取りを確かめる。
- ファイルの大きさの判定は、`File` の `size` を大きくしたテスト用のファイル（中身は小さい）で確かめ、読み込みの関数が呼ばれないことを確かめる。10MB の中身を実際に作らない。
- 時刻の表示は、テストで時差と表示言語を固定して確かめる。実時刻に依存しない。
- `DslAccessControlIT` は本物のアプリ（組み込みの H2）を起動し、既存の補助で管理者・管理者でない利用者・未認証の要求を作る。対象DB の設定は無い状態で行う（スキーマの読み込みの管理者の成功は、生成の結果に関わらず 401・403 でないことと、設定が無いときの 503 で確かめる。計画の Step 10）。

## 5. テストのデータ

- DSL の本文・プレビューの応答・誤りの一覧の応答は、テストの中で C6 の形の値として組み立てる。101 件以上の誤り・101 件以上の未設定の表示名・100 カラムのテーブルもテストの中で作る。
- 表示名・コメント・誤りの文言に `<script>` や HTML の記号を含むものを入れ、文字のまま出ることを確かめる。
- テストの説明文（`describe`・`it`、`@DisplayName`）は英語。テストデータは日本語でよい。
