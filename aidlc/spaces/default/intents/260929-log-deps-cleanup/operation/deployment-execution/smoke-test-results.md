# スモークテストの結果（smoke-test-results）

`aidlc/spaces/default/intents/260929-log-deps-cleanup/operation/deployment-pipeline/deployment-strategy.md` の 3節の確かめ。個人に関する値は表示せず、件数とキーの有無だけを見た。

| # | 確かめ | 結果 | 根拠 |
|---|---|---|---|
| S1 | 起動のログ | 通過 | healthy（約16秒）。構造化ログ 40 件のうち ERROR 0、WARN 2（Spring の BeanPostProcessorChecker と、Flyway の「H2 2.4.240 は検証済みの版より新しい」。どちらも前の版から出ているもの） |
| S2 | 初期管理者のログ（FR1） | 通過 | ロガー `cherry.mastersmith.user.service.InitialAdminInitializer` の「既にいるため」の INFO が1件。キーは `maskedEmail` だけで、`email` は無い |
| S3 | ログの漏れ | 通過 | `docker compose logs app` の中に、`.env` の `MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL` の値が 0 件（大文字と小文字を区別しない数えでも 0 件） |
| S4 | ログイン | 通過 | 依頼者が初期管理者でログインし、ホームが開けた。配備の後の監査に `LOGIN_SUCCEEDED` が1件（20:42:41 UTC） |
| S5 | ログアウト | 通過 | 依頼者がログアウトし、ログイン画面に戻った。配備の後の監査に `LOGGED_OUT` が2件（20:42:34・20:43:12 UTC）。20:43:12 が今回のログアウト。ログインより前の 20:42:34 は、ブラウザに残っていた前のセッションのログアウトと見られる（見立て） |

監査の確かめは README の「監査ログの確かめ方」のとおり、アプリを止めて内部DB を複写し、複写を読み取り（`ACCESS_MODE_DATA=r`）で開いて `audit_events` を種類と結果ごとに数えた。

画面の見た目（選ばれたタブと primary のボタンの hover の文字の色）は確かめていない（Q2: B。E2E の 100 で確かめ済み）。

## Sources

- `aidlc/spaces/default/intents/260929-log-deps-cleanup/operation/deployment-pipeline/deployment-strategy.md`
- `aidlc/spaces/default/intents/260929-log-deps-cleanup/operation/deployment-pipeline/cd-config.md`
- `aidlc/spaces/default/intents/260929-log-deps-cleanup/construction/build-and-test/test-results.md`
