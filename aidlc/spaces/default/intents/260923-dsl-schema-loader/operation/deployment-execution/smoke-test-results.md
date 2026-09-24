# スモークテストの結果（smoke-test-results）

配備した版 `7bc1b68` に対するスモークテスト（`aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/deployment-pipeline/deployment-strategy.md` 3節）。パスワードが要る操作は依頼者が行い、AI はログと監査イベントで裏付けた（project.md の決まり）。スモークテストの要求は監査ログに残り、消せないことを、送る前に依頼者に伝えた。個人に関する値（メールアドレス・接続元IP）は表示せず、値があるかどうかだけを数えた。

## 1. 結果

| 項目 | 確かめ方 | 結果 | 行った人 |
|---|---|---|---|
| 健全性 | `docker compose --profile targetdb-postgres ps`・`curl -s http://localhost:8080/actuator/health` | `healthy`・`{"status":"UP"}` | AI |
| ログイン画面 | ブラウザで `http://localhost:8080/` | 表示された（依頼者の報告「全て通りました」） | 依頼者 |
| ログイン | 初期管理者でログイン | ホームが表示された | 依頼者 |
| 管理者向け領域 | サイドバーの「管理」 | 表示された | 依頼者 |
| DSL の管理画面を開く | サイドバーの「DSL」（`/admin/dsl`） | 表示され、エラーの知らせは出なかった | 依頼者 |
| 今の状態の取得 | 画面の上部の「今の状態」 | 表示された | 依頼者 |
| 見本の対象DB からの既定の DSL の生成 | 「スキーマを読み込む」→ 確かめる表示で進める | 成功し、プレビューに `sales` のテーブルとビューが並んだ。「未設定」「接続できない」にならなかった。ログの `DSL の操作を終えました`（`dsl.operation` が `generate`、`dsl.outcome` が `success`、216ms） | 依頼者（ログは AI） |
| ログアウト | ユーザーメニューのログアウト | ログイン画面に戻った | 依頼者 |
| ログ | `docker compose logs app` | ERROR は 0 件。WARN は 10 件で、起動の定型の2件（Flyway の H2 の版の案内・Spring の Bean の案内）と、エラー応答の変換の8件（プレビューが無いときの 404 `DSL_PREVIEW_NOT_FOUND`、ログインの前の画面の読み込みでの 401 `REFRESH_FAILED`。どちらも想定どおりの動き）。対象DB の設定の WARN は無い（`mastersmith-target-db - Starting...`・`Start completed.` の INFO だけ） | AI |
| ログに接続情報が無い | `docker compose logs app \| grep -c -e targetdb-postgres -e mastersmith_reader` | 0 件 | AI |
| 監査イベント | アプリを止めて内部DB を複写し、複写したファイルを H2 の道具で読み取り（`ACCESS_MODE_DATA=r`）で開いて `audit_events` を数えた（配備の後 16:20:30Z 以降） | `LOGIN_SUCCEEDED` 2 件・`LOGGED_OUT` 2 件（結果・入力されたメールアドレス・接続元IP・トレースIDがある）、`DSL_GENERATED` 4 件（操作した人 `actor_user_id`・DSL の識別・出どころ `GENERATED`・接続元IP・トレースIDがある） | AI |

## 2. スモークテストの外で依頼者が試した操作

監査イベントには、スモークテストの項目より多い操作が記録されていた。依頼者が画面で投入・破棄・適用も試したもので、いずれも必要な項目が記録されていた。

| 出来事 | 件数 | 操作した人・DSL の識別・接続元IP・トレースID |
|---|---|---|
| `DSL_SUBMITTED` | 3 | すべてある |
| `DSL_PREVIEW_DISCARDED` | 3 | すべてある |
| `DSL_APPLIED` | 2 | すべてある。ログの `dsl.operation` が `apply`・`dsl.outcome` が `success` |

内部DB の DSL の表: 適用の履歴 2 件（`dsl_applied_revisions`）、今のプレビュー 0 件（`dsl_previews`）。

## 3. 気づいたこと（配備の合否には影響しない）

- 起動のときの Hibernate の案内（`Database JDBC URL`・`Database driver` などの11行）は、改行を含んだまま1件のログになっており、`docker compose logs` では 11 行に分かれて見える（1行1件の JSON の決まりから外れる）。前の版からの動きと見られ、今回の変更によるものではない。Observability Setup で扱うかを依頼者に諮る。
- Hibernate の案内の `Database JDBC URL` には、接続先の指定（`;DEFRAG_ALWAYS=TRUE`）が表示されない（H2 が表示のときに外す）。WAR の中の設定には入っていることを確かめた（`deployment-log.md` 1節）。

## Sources

- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/deployment-pipeline/deployment-strategy.md`（3節）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/deployment-execution/deployment-execution-questions.md`（確認済みの要約）
- `README.md`（「監査ログの確かめ方」）
- 実行したコマンド: `docker compose logs app`・`curl -s http://localhost:8080/actuator/health`・`docker compose stop app`・`docker run ... tar czf`・`docker compose start app`・`java -cp h2-2.4.240.jar org.h2.tools.Shell -url "jdbc:h2:file:<複写>;ACCESS_MODE_DATA=r"`（件数と値の有無だけを数えた）

## Assumptions & Open Questions

None.
