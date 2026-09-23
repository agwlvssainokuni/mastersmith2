# スモークテストの結果（smoke-test-results）

配備した版 `3287050` に対するスモークテスト（`aidlc/spaces/default/intents/260923-audit-pool-exhaustion/operation/deployment-pipeline/deployment-strategy.md` 3節、前の Intent と同じ7項目）。2026-09-23 06:13〜06:15 UTC。パスワードが要る操作は依頼者が行い、AI は監査イベントとログで裏付けた（`project.md` の Corrections）。個人に関する値（メールアドレス・接続元IP）は表示していない。

| 項目 | 確かめ方 | 結果 | 行った者 | 判定 |
|---|---|---|---|---|
| 健全性 | `docker compose ps`、`curl http://127.0.0.1:8080/actuator/health` | `healthy`、`{"status":"UP"}` | AI | 合格 |
| ログイン画面 | ブラウザで `http://localhost:8080/`（AI は `GET /` が 200・`text/html` であることも確かめた） | ログイン画面が表示された | 依頼者 | 合格 |
| ログイン | 初期管理者でログイン | ホームが表示された | 依頼者 | 合格 |
| 管理者向け領域 | メニューの「管理」 | 管理者向け領域が表示された | 依頼者 | 合格 |
| ログアウト | ユーザーメニューのログアウト | ログイン画面に戻った | 依頼者 | 合格 |
| 監査イベント | アプリを止めてボリュームを一時の場所に複写し、複写を読み取りで開いた（配備の時刻 06:12:43 以降の行） | `LOGIN_SUCCEEDED`（06:14:14）と `LOGGED_OUT`（06:14:31）の2件。どちらも結果 `SUCCESS`、トレースIDあり | AI | 合格 |
| ログ | `docker logs mastersmith-app-1` | ERROR 0 件。WARN 3 件（起動の時の既知の2件、ログインの前に画面がトークンの更新を試みた 401 `REFRESH_FAILED` の1件）。監査の失敗と接続の時間切れは 0 件。1行1件の JSON | AI | 合格 |

- 監査イベントの確認のため、06:15 にアプリを止め、確かめた後に `docker compose start app` で起動し直した。起動し直した後も `healthy`・`{"status":"UP"}` を確かめた。
- 複写したファイルと、読み取りに使った H2 の JAR は、確かめの後に消した。

## Sources

- 依頼者のスモークテストの報告（「すべて通った」）
- `docker compose ps`、`curl`、`docker logs` の出力、監査イベントの表の読み取りの結果

## Assumptions & Open Questions

- None.
