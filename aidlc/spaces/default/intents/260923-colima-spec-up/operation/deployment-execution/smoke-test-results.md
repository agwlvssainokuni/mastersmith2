# スモークテストの結果（smoke-test-results）

配備した版 `10742a3` に対するスモークテスト（`aidlc/spaces/default/intents/260923-colima-spec-up/operation/deployment-pipeline/deployment-strategy.md` 2節、前の Intent と同じ7項目）。2026-09-23 17:12〜17:22 JST。パスワードが要る操作は依頼者が行い、AI は監査イベントとログで裏付けた（`project.md` の Corrections）。個人に関する値（メールアドレス・接続元IP）は表示していない。

| 項目 | 確かめ方 | 結果 | 行った者 | 判定 |
|---|---|---|---|---|
| 健全性 | `docker compose ps`、`curl http://127.0.0.1:8080/actuator/health` | `healthy`、`{"status":"UP"}` | AI | 合格 |
| ログイン画面 | ブラウザで `http://localhost:8080/`（AI は `GET /` が 200・`text/html`・`<title>MasterSmith</title>` であることも確かめた） | ログイン画面が表示された | 依頼者 | 合格 |
| ログイン | 初期管理者でログイン | ホームが表示された | 依頼者 | 合格 |
| 管理者向け領域 | メニューの「管理」 | 管理者向け領域が表示された | 依頼者 | 合格 |
| ログアウト | ユーザーメニューのログアウト | ログイン画面に戻った | 依頼者 | 合格 |
| 監査イベント | アプリを止めてボリュームをホームの下の一時の場所に複写し、複写を読み取り（`ACCESS_MODE_DATA=r`）で開いた（配備の時刻 17:12 以降の行） | `LOGIN_SUCCEEDED`（17:13:11）と `LOGGED_OUT`（17:21:02）の2件。どちらも結果 `SUCCESS`、トレースIDあり | AI | 合格 |
| ログ | `docker compose logs app`（停止の前の 37 行） | ERROR 0 件。1行1件の JSON（JSON でない行 0 件。起動の時の「Picked up ...」の行も無い）。WARN 3 件（起動の時の既知の2件と、ログインの前に画面がトークンの更新を試みた 401 `REFRESH_FAILED` の1件。前の Intent と同じ）。監査の失敗・接続の時間切れは 0 件 | AI | 合格 |

- 依頼者の報告は「すべて通った」（`deployment-execution-questions.md` の Q4）。
- 監査イベントの確認のための停止と起動し直しは `deployment-log.md` 3節。複写したファイルと H2 の JAR は、確かめの後に消した。

## Sources

- 依頼者のスモークテストの報告（「すべて通った」）
- `docker compose ps`、`curl`、`docker compose logs` の出力、監査イベントの表の読み取りの結果

## Assumptions & Open Questions

- None.
