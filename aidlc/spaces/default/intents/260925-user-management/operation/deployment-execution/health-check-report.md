# 健全性と戻しの確かめ（health-check-report）

配備した版の健全性と、配備の直後に行った戻しの練習の結果です（2026-09-29）。

**判定: 合格**（配備した版は健全。1つ前の版は、配備の後の内部DB で起動し、ログイン・更新・ログアウト・監査が動いた）

## 1. 配備した版の健全性

| 確かめ | 結果 |
|---|---|
| `docker compose ps` | `mastersmith-app-1` healthy（起動から 12 秒で healthy）、`mastersmith-targetdb-postgres-1` Up |
| `/actuator/health` | `{"status":"UP"}`（入れ替えの直後、スモークテストの後、配備の後のバックアップの後、片付けの後） |
| 配備の後のバックアップで止めた後 | 約7秒で healthy に戻った |
| コンテナの上限 | メモリ 2g・CPU 4（`.env` の値のまま） |

## 2. スキーマの変更

| 確かめ | 結果 |
|---|---|
| Flyway | 6 → 8。V7（利用者のプリファレンス）・V8（招待）を当てた（実行時間 35 ミリ秒） |
| 配備の前のバックアップ | `mastersmith-data-202609290030-before-user-management.tgz`（残す） |

## 3. 戻しの練習（配備の直後。Deployment Pipeline の Q7: A）

手順は `rollback-runbook.md` の 5 節です。

| # | 手順 | 結果 |
|---|---|---|
| 1 | 配備の後のバックアップ | アプリを約7秒止めて取った |
| 2〜4 | 練習用の `.env` の複写（ホームの下、権限 600）と `MASTERSMITH_WEB_BASE_URL=http://localhost:18080` の追記。`MASTERSMITH_PERF_ENV_FILE` などの環境変数 | 複写のベース URL は2行になり、後ろの 18080 が効く |
| 5 | `docker compose -p mastersmith-rollback -f docker/perf/compose.yaml create app` と、1 の展開（持ち主 10001） | 通った |
| 6 | `MASTERSMITH_IMAGE_TAG=pre-user-management … up -d --wait app` | `mastersmith:pre-user-management`（`sha256:305fddf4…`）が healthy。`127.0.0.1:18080` の `/actuator/health` が 200 |
| 7 | 依頼者が `http://localhost:18080` でログイン・再読み込み（トークンの更新）・ログアウト | 依頼者の報告は問題なし。練習の複写の `audit_events` に `LOGIN_SUCCEEDED` 1・`LOGGED_OUT` 1 |
| 8 | `down -v`、練習用の複写とバックアップを消す | 済み |

1つ前の版の起動のログは、次のとおりです。

- ERROR 0 件。
- Flyway は `Current version of schema "PUBLIC": 8` を読み、「スキーマの版（8）が、この版の知っている最新（6）より新しい」の WARN を出して、起動を続けた。
- **Hibernate の `validate` は、V7 の余分な列と V8 の余分な表を許した**（起動した。U2 の R-02、U3 の確かめ）。
- **初期管理者は作られなかった**（「既にいるため、作成しませんでした」）。初期管理者のメールアドレスを変えずに戻したためで、D4 の注意のとおり。
- 練習の間の業務のエラー応答は、次のとおり。
  - `REFRESH_FAILED` 2
  - `DSL_PREVIEW_NOT_FOUND` 2
  - `TARGET_DB_UNAVAILABLE`（503）1：練習の網に見本の対象DB が無いため、DSL の画面で出る想定どおりの応答

**確かめなかったこと**：戻したときのブラウザの表示の設定（U4 の localStorage）は、確かめていません。害は無い見込みとして記録します（Deployment Pipeline の Q6: B）。

## 4. 残したものと消したもの

| もの | 扱い |
|---|---|
| `~/.mastersmith-backup/mastersmith-data-202609290030-before-user-management.tgz` | 残す（第二の手の展開に使う） |
| `~/.mastersmith-env-before-user-management` | 残す（第二の手・中止のときに戻す） |
| イメージ `mastersmith:pre-user-management` | 残す（第一の手の戻し先） |
| 配備の後のバックアップ・練習用の `.env` の複写・確かめ用の複写 | 消した |
| 練習の環境（コンテナ・ボリューム `mastersmith-rollback_perf-data`・網） | 消した |
| Mailpit | 止めて消した（見たいときに profile `mail` で起動する） |

## Sources

- `operation/deployment-pipeline/rollback-runbook.md`・`deployment-strategy.md`・`cd-config.md`
- `operation/environment-provisioning/environment-inventory.md`
- `construction/build-and-test/test-results.md`
- この段で実行したコマンドの出力（`docker`・`curl`・H2 の `RunScript` による読み取り）

## Assumptions & Open Questions

- 戻したときのブラウザの表示の設定（U4）は確かめていません（Q6: B）。
