# CI/CD Pipeline — U5 DSL の管理画面（u5-dsl-admin-ui）

U5 の検査の流れ。既存の流れに足すものだけを示す。

## 1. 検査の流れ（既存の `./gradlew verify` の中の画面の検査）

| 段 | U5 のテスト |
|---|---|
| フォーマット・リンタ・型検査 | 既存（Prettier・oxlint・ESLint・Stylelint・`tsc`）。`react/no-danger` で HTML の埋め込みを止める |
| 画面部品のテスト（Vitest・Testing Library・user-event・vitest-axe） | 部品ごとの振る舞い（開いた行だけを描く、`File.size` で読む前に判定、誤りの先頭 100 件、`DSL_BUSY` の Alert、日英の文言、`<script>` を含む文字列が文字のまま出る）と、部品ごとのアクセシビリティ検査1件 |
| ApiClient の拡張のテスト | Problem Details の本文を渡す口と、既存の呼び方が変わらないこと |
| カバレッジ | 既存の下限（行 80%・分岐 70%）をフロントエンドにも当てる |

## 2. 一括のアクセス制御のテスト（U5 の機能設計の BR8.1a）

- U5 の Bolt で、バックエンドの結合テスト `DslAccessControlIT`（`backend/src/test/java/cherry/mastersmith/dslmanage/web/`）を足す。C6 の 11 本の API の未認証 401・管理者でない 403（監査の出来事つき）・管理者 200 と、401・403 で状態が変わらないことを確かめる。`integrationTest` の段で動く。

## 3. 画面の時間の測定（Build and Test）

- E2E（Playwright、既存の `./gradlew e2eTest`、`verify` の外）で、画面を開いてから表示まで（照合を除く 3 秒・照合を含む 11 秒）、違いの表の行を開くまで（0.5 秒）、10MB のファイルの読み込み（2 秒）を測って記録する。統合の関門のテストにしない。
- E2E の代表的な流れに「DSL の管理画面を開く」を足すかは Build and Test で決める（要件の OQ7）。

## 4. 配備・戻し・秘密情報

- 配備と戻しは既存の手順のまま（画面は WAR に同梱されるため、U5 だけの配備は無い）。
- U5 は秘密情報を扱わない（アクセストークンは既存の ApiClient だけが扱う）。
