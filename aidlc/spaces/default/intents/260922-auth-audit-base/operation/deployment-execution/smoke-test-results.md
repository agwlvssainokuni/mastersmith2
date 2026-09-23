# スモークテストの結果（smoke-test-results）

Intent `260922-auth-audit-base`（auth-audit-foundation）を版 `7040876` で配備した後のスモークテストの結果。項目は `operation/deployment-pipeline/deployment-strategy.md`（deployment-strategy）3節の7項目で、手で行った（`operation/deployment-pipeline/cd-config.md`（cd-config）1節）。分担は、ブラウザでのログインの操作を依頼者が、それ以外を AI が行った（`deployment-execution-questions.md` の確認済みの要約）。環境は `operation/environment-provisioning/environment-inventory.md`（environment-inventory）のとおり。

- 実行した日時: 2026-09-23 12:22〜12:28（日本時間）
- 判定: **合格**（7項目すべて）

## 1. 項目ごとの結果

| # | 項目 | 行った者 | 確かめ方 | 結果 | 判定 |
|---|---|---|---|---|---|
| 1 | 健全性 | AI | `docker compose ps`、`curl http://127.0.0.1:8080/actuator/health` | `healthy`、`200 {"status":"UP"}` | 合格 |
| 2 | ログイン画面 | AI（HTML）、依頼者（ブラウザ） | `curl http://127.0.0.1:8080/` と `/login`、ブラウザで表示 | 200、`<title>MasterSmith</title>` と画面の差し込み先がある。ブラウザでログイン画面が表示された | 合格 |
| 3 | ログイン | 依頼者 | 初期管理者でログイン | ホームが表示された（依頼者の回答「すべてできた」） | 合格 |
| 4 | 管理者向け領域 | 依頼者 | メニューの「管理」を開く | 管理者向け領域が表示された | 合格 |
| 5 | ログアウト | 依頼者 | ユーザーメニューのログアウト | ログイン画面に戻った | 合格 |
| 6 | 監査イベント | AI | アプリを止めてボリュームを複写し、複写を H2 2.4.240 の道具で読み取り（`ACCESS_MODE_DATA=r`）で開いて `audit_events` を読んだ（個人に関する値は表示せず、項目に値があるかだけを見た） | 5件（下の表）。ログインとログアウトの `LOGIN_SUCCEEDED`・`LOGGED_OUT` が記録されている | 合格 |
| 7 | ログ | AI | `docker compose logs app` の全行を JSON として読み、ERROR と WARN を数えた | 全行が JSON。ERROR 0 件。WARN 6 件はすべて想定どおり（2節） | 合格 |

### 記録された監査イベント

時刻は UTC で保存されている（project.md の Corrections「保存する時刻は UTC」）。メールアドレスとトレースIDは値があることだけを確かめた。

| ID | 発生の時刻（UTC） | 種類 | 結果 | 失敗の理由 | メールアドレス | トレースID | 要求のパス |
|---|---|---|---|---|---|---|---|
| 1 | 03:23:59 | `LOGIN_FAILED` | `FAILURE` | `PASSWORD_MISMATCH` | あり | あり | なし |
| 2 | 03:24:10 | `LOGIN_SUCCEEDED` | `SUCCESS` | — | あり | あり | なし |
| 3 | 03:25:31 | `LOGGED_OUT` | `SUCCESS` | — | あり | あり | なし |
| 4 | 03:25:54 | `LOGIN_SUCCEEDED` | `SUCCESS` | — | あり | あり | なし |
| 5 | 03:27:17 | `LOGGED_OUT` | `SUCCESS` | — | あり | あり | なし |

設計の「ログインとログアウトで監査イベントが2件」（U4 の `construction/u4-audit-log/infrastructure-design/cicd-pipeline.md` 3章）に対して5件あるのは、ブラウザでの確認の間に、パスワードの入力の誤り1回と、ログインとログアウトの2往復があったためである。どの出来事も欠けずに記録されており、存在しないメールアドレスではないログインの失敗も `PASSWORD_MISMATCH` として残っている。要求のパスはアクセスの拒否（`ACCESS_DENIED`）だけに入る項目で、今回は拒否が無いため空で正しい。

## 2. ログの WARN の内訳

| 時刻（日本時間） | 内容 | 判断 |
|---|---|---|
| 起動時 | Flyway の自動構成の Bean についての Spring の WARN | 既知（環境の用意の段と同じ） |
| 起動時 | H2 2.4.240 が Flyway の確認済みの版より新しい | 既知（`operation/environment-provisioning/validation-report.md` 1節の 18） |
| 12:23:51 | `REFRESH_FAILED`（401） | 画面を開いたときに、ログインしていない状態でトークンの更新を試みて 401 になり、ログイン画面を出す。想定どおり |
| 12:23:59 | `AUTHENTICATION_FAILED`（401） | パスワードの入力の誤り1回。監査イベントの ID 1 と一致する |
| 12:27:24・12:27:25 | `REFRESH_FAILED`（401）×2 | ログアウトの後に画面を開き直したときの更新の試み。想定どおり |

## 3. 秘密情報の確認

起動からスモークテストまでのログを、`.env` の値で検索した（値は表示していない）。

| 値 | ログに出た件数 | 判断 |
|---|---|---|
| 初期管理者のパスワード | 0 | 合格 |
| 署名鍵 | 0 | 合格 |
| 初期管理者のメールアドレス | 1 | 設計どおり。INFO「初期管理者を作成しました」のキー `email` に出る（`InitialAdminInitializer`）。メールアドレスは出してはいけないものに入っていない（project.md の Forbidden はパスワード・トークン・署名鍵） |

## 4. 行わなかった確認

| 確認 | 理由 | 代わり |
|---|---|---|
| 管理者でない利用者の拒否（「ページが見つかりません」） | 本 Intent には管理者でない利用者を作る機能が無い | 結合テストと E2E（U3 の `cicd-pipeline.md` 3章。`construction/build-and-test/test-results.md`（build-test-results）の実行結果） |
| 戻しの後のスモークテスト | 戻しの練習を行わなかった（Q3） | 次に版を替えるとき |

## Sources

- `aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-execution/deployment-execution-questions.md`（確認済みの要約）
- `aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/deployment-strategy.md`（3節の7項目）、`cd-config.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/operation/environment-provisioning/environment-inventory.md`、`validation-report.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u4-audit-log/infrastructure-design/cicd-pipeline.md`（監査イベントの確認）、`construction/u3-access-control/infrastructure-design/cicd-pipeline.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/build-and-test/test-results.md`
- `backend/src/main/resources/db/migration/V4__u4_audit_event.sql`（`audit_events` の項目）
- 依頼者のブラウザでの確認の回答（「すべてできた」）、2026-09-23 12:22〜12:28 の `curl`・`docker compose logs`・H2 の Shell の出力

## Assumptions & Open Questions

None.
