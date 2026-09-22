# Infrastructure Design — Questions（U4 監査ログ / u4-audit-log）

U4 の基盤の設計で、これまでの段階で決まっていない点を確認します。

決定済みの事項（再確認はしません）: U4 は U1 の基盤（コンテナ1台、`/app/data` の名前付きボリュームを実行の利用者だけが読み書き、停止した状態での複写によるバックアップ、`./gradlew verify`、GitHub Actions）と、U2 の基盤の設計（タイムゾーン `Asia/Tokyo`、CPU の上限 4）の上で動く。監査ログは内部DBに無期限に保存し、変更・削除の機能を持たず、改ざんへの備えは OS の権限（U4 の NFR Requirements の Q1）。1年 約 180MB（索引を含めて 250MB 程度）の見積もり（U4 の `nfr-design/scalability-design.md`）。

新しく決めていただく論点はありません。設計は上の決定から導きます。

## Consolidated Summary Confirmation

- 基盤: U4 は独自のコンテナ・サービス・環境変数を持たない。監査イベントの表は U1 の内部DB（`/app/data` のボリューム）に置き、Flyway の U4 のファイルで表と日時の索引、要求のパスの項目を作る
- 保護: 監査ログの守りは、ボリュームを実行の利用者だけが読み書きできる権限にすることと、U1 のバックアップ（停止して複写）で行う。ボリュームを消す操作（`docker compose down -v` など）で監査ログも消えることを README に注意として書く
- 監視: 書き込みの失敗（U4 の ERROR のログ）と、記録の量（ボリュームの使用量）を運用で見る。指標は作らず、ログと監査ログの件数から見る
- 検査: `./gradlew verify` に、必須の監査ログのテスト（対象の出来事ごとの必須の項目、存在しないメールアドレスでの失敗の記録、書き込みの失敗で操作が失敗しないこと）、2つの受け取りの経路、取り消しで記録しないこと、切り詰め（性質ベースのテスト）、トレースIDの一致、変更・削除の処理が無いこと（構造の検査）、SQL の回数を足す。流れの形は変えない
- 配備: 配備の確認に、初期管理者のログインとログアウトで監査イベントが2件記録されることを足す

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
