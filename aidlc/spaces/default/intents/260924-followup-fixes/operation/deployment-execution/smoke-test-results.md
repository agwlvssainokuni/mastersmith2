# スモークテストの結果（smoke-test-results）

2026-09-25、配備した版 `87cc0fc`（`mastersmith:local` = `sha256:a9cfa9dc…`）。手順は `aidlc/spaces/default/intents/260924-followup-fixes/operation/deployment-pipeline/deployment-strategy.md` 3節。ブラウザでの操作は依頼者が行い、AI はログと監査イベントで裏付けた。スモークテストの要求が監査ログに残ることは、送る前に依頼者に伝えた。

## 1. 結果

| 項目 | 確かめ方 | 結果 | 行った人 |
|---|---|---|---|
| 健全性 | `curl -s http://localhost:8080/actuator/health` | `{"status":"UP"}` | AI |
| ログイン画面・ログイン・管理者向け領域 | ブラウザ | 通過（依頼者の報告「すべて通った」） | 依頼者 |
| DSL の管理画面と対象DB への接続 | 「スキーマを読み込む」で既定の DSL を生成 | 通過（依頼者の報告）。アプリのログに `dsl.operation` `generate`・`dsl.outcome` `success` が 2 件。「接続できない」は出ていない（`.env.targetdb` に移した値で見本の対象DB に接続できた） | 依頼者（AI がログで裏付け） |
| 閉じるボタンの名前（FR7、任意） | 確認の表示 | 依頼者の報告に個別の言及なし（任意の項目。画面のテストで確かめ済み） | 依頼者 |
| ログアウト | ユーザーメニュー | 通過（依頼者の報告） | 依頼者 |
| ログ | `docker compose logs app` を JSON として数えた | 44 行（INFO 40・WARN 4）、ERROR 0。メッセージに改行を含む行 0。Hibernate の案内（`org.hibernate.orm.connections.pooling`）は ` ⏎ ` で1件（FR4.1） | AI |
| ログに接続情報が無い | `docker compose logs app \| grep -c -e targetdb-postgres -e mastersmith_reader` | 0 | AI |
| 監査イベント | アプリを止めて内部DB を複写し、複写を読み取りで開いて、配備（02:29:42 UTC）以降の `audit_events` を種類ごとに数えた（個人に関する値は表示していない） | `LOGIN_SUCCEEDED` 1・`DSL_GENERATED` 2・`LOGGED_OUT` 2、すべて SUCCESS。ほかに依頼者の操作による `DSL_APPLIED` 1・`DSL_PREVIEW_DISCARDED` 1（SUCCESS） | AI |

- WARN 4 件の内訳: 起動の既存の案内 2 件と、`DSL_PREVIEW_NOT_FOUND`（404）2 件。後者は、適用・破棄の後にプレビューが無い状態で画面がプレビューを読みに行ったときの想定内の応答（業務エラー、スタックトレースなし）。
- `LOGGED_OUT` がログインより1件多いのは、入れ替えの前からブラウザに残っていたログインの分のログアウトと見ている（2件とも SUCCESS）。
- 監査イベントの確かめのためにアプリを止めた時間は数秒。起動し直した後に healthy を確かめた。

## 2. 判定

合格。すべての必須の項目が通った。任意の項目（閉じるボタンの名前）は個別の報告なし。

## Sources

- `aidlc/spaces/default/intents/260924-followup-fixes/operation/deployment-pipeline/deployment-strategy.md`（3節）
- `aidlc/spaces/default/intents/260924-followup-fixes/operation/deployment-execution/deployment-log.md`

## Assumptions & Open Questions

- 閉じるボタンの名前の画面での確かめは任意とし、依頼者から個別の報告は無い。
