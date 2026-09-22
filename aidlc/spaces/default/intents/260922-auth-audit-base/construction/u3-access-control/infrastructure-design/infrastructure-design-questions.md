# Infrastructure Design — Questions（U3 管理画面のアクセス制御 / u3-access-control）

U3 の基盤の設計で、これまでの段階で決まっていない点を確認します。

決定済みの事項（再確認はしません）: U3 は U1 の基盤（コンテナ1台、`docker compose`、`./gradlew verify`、GitHub Actions、pre-commit）と、U2 の基盤の設計（タイムゾーン `Asia/Tokyo`、CPU の上限 4）の上で動く。U3 に固有の環境変数・資源は無い（U3 の `nfr-design/logical-components.md` 4章）。転送元のヘッダーを信頼するかどうかの設定（U1）が、アクセス拒否の出来事の接続元IP の取り方にも効く。

新しく決めていただく論点はありません。設計は上の決定から導きます。

## Consolidated Summary Confirmation

- 基盤: U3 は独自のコンテナ・サービス・環境変数を持たず、U1・U2 の基盤をそのまま使う
- 監視: 403 の件数（HTTP の指標と WARN のログ）、`REQUEST_REJECTED` の件数、監査ログの ACCESS_DENIED の件数を、運用で見る候補とする。確認用 API の応答時間の目標（95% が 300 ミリ秒以内）を Performance Validation で測る
- 検査: `./gradlew verify` の単体テスト・結合テストに、401／403／204 の認可のテスト、パスの境界（細工されたパス・大文字・末尾のスラッシュ）、存在しない管理 API の 403、既定でログイン必須、出来事の有無、画面の確認中の表示とアクセシビリティを足す。流れの形は変えない
- 配備: 配備の確認に、管理者で管理者向け領域が開けること、管理者でない利用者には「ページが見つかりません」になることを足す（スモークテスト）

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
