# 配備の記録（261003-user-admin-followup）

配備先は開発者の PC 上のコンテナ（compose のプロジェクト `mastersmith`）。手順は承認済みの `aidlc/spaces/default/intents/261003-user-admin-followup/operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md` のとおり（Q1: A、まとめの確認 Looks correct）。

## 1. 配備した版

| 項目 | 値 |
|---|---|
| アプリのソース | `develop` の `2fcc545` の作業フォルダ（コードの最後の変更は `d288e0e`。その後は文書 `c50e64c`・`767f1cd` と `aidlc/` の下の記録だけ） |
| 関門 | `a1b41e7` で clean 付きの verify、push の後の CI run 37162114053 が success。文書の2つは統合の前に verify を通した（Build and Test・Deployment Pipeline） |
| WAR | `./gradlew :backend:bootWar` が UP-TO-DATE（今のソースから作られたもの）。SHA-256 の先頭 `df2cfea02fbeceb4`。イメージの中の `/app/mastersmith.war` も同じ値 |
| 新しいイメージ | `mastersmith:local` = `sha256:f124296eb7ca…` |
| 戻し先 | `mastersmith:pre-user-admin-followup` = `sha256:f386b55f16a8…`（Intent 260930-user-admin で配備した版、ソース `b126bdc`） |
| スキーマ | 変わらない（V9 のまま。新しい移行 0 件） |
| `.env` | 変えていない |

## 2. 手順と時刻（UTC）

| 順 | 操作 | 結果 |
|---|---|---|
| 1 | 関門の確かめ（読み取りだけ） | アプリのソースに未コミットの変更は無い。`origin/develop` は `a1b41e7` で、`aidlc/` の外の差は文書2つだけ |
| 2 | `docker tag mastersmith:local mastersmith:pre-user-admin-followup`（00:15:09Z） | ID が `f386b55f16a8` で始まることを確かめた |
| 3 | `docker compose --profile targetdb-postgres up -d --build app`（00:15:09Z 開始） | 終了コード 0。イメージを作り直し（`Built`）、`app` だけを入れ替えた（`Recreate`）。Mailpit・見本の対象DB は触っていない |
| 3' | healthy を待つ | 00:15:19Z に healthy（開始から約 10 秒）。`/actuator/health` は 200・UP |
| 3'' | WAR の確かめ | `bootWar` が UP-TO-DATE、WAR とイメージの中の WAR の SHA-256 が一致 |
| 4 | スモークテスト S1〜S5 | `smoke-test-results.md`。すべて合格 |
| 4' | S6 のためアプリを止めて内部DB を複写し、起動し直した（00:19:36Z 停止、00:19:46Z healthy） | 複写は `~/.mastersmith-backup/mastersmith-data-202610040919-after-user-admin-followup.tgz`（権限 600。配備の後のバックアップを兼ねる）。展開した写しは数えた後に消した |
| 5 | 依頼者の承認の後、`main` を `develop` の先頭へ fast-forward（`git fetch . develop:main`。`main` が `develop` の祖先であることを確かめた） | `main`: `cc28d1f` → `2fcc545`。タグは付けていない。push は依頼者が行う |

## 3. 予定との差

- **S4・S5 の間の管理の操作**: 予定（確かめの表示は確定させない）と違い、依頼者が画面で、管理者の印を外して付ける操作と、利用を止めて戻す操作を確定させた（依頼者の答え: 「管理者の印を外してつけた、利用を止めて戻した。」）。S6 の期待の件数をこれに合わせ、4件（`USER_ADMIN_REVOKED`・`USER_ADMIN_GRANTED`・`USER_SUSPENDED`・`USER_RESUMED`、どれも SUCCESS）が記録されていることを確かめた。停止中の利用者は 0 人で終わっている。
- **S6 の Flyway の版の問い合わせ**: 表の名前の大文字・小文字を誤って1回失敗した（`flyway_schema_history` は引用符が要る）。版 9 のままであることは S2（起動のログの新しい移行 0 件・検証 1 件）で確かめている。
- **記録の保存し直し**: 記録の3つの文書をまとめの確認の後にシェルで書いたため、承認の場が2回断られた。依頼者の選んだ戻し方（まとめを確かめ直す）で、まとめの確認を記録し直し、3つの文書を中身を変えずに保存し直した。

## 4. 戻すとき

`aidlc/spaces/default/intents/261003-user-admin-followup/operation/deployment-pipeline/rollback-runbook.md` のとおり、イメージだけを `mastersmith:pre-user-admin-followup` へ戻す。戻すと、一意の違反のときにメールアドレスがログに出る状態に戻る（戻す前に依頼者に伝える）。

## Sources

- `aidlc/spaces/default/intents/261003-user-admin-followup/operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`
- `aidlc/spaces/default/intents/261003-user-admin-followup/construction/build-and-test/test-results.md`
- `aidlc/spaces/default/intents/261003-user-admin-followup/operation/deployment-execution/deployment-execution-questions.md`
- `README.md`（内部DBのバックアップと戻し方、監査ログの確かめ方）

## Assumptions & Open Questions

None.
