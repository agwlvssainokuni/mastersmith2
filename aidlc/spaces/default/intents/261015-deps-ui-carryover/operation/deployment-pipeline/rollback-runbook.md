# 戻しの手順書（rollback-runbook）

Intent 261015-deps-ui-carryover の戻し方です。前の手順（`aidlc/spaces/default/intents/261004-role-menu/operation/deployment-pipeline/rollback-runbook.md`）からの差だけを書きます。

## 1. 戻すきっかけ

- 起動が healthy にならない、ERROR が出続ける、ログに秘密・メールアドレスそのものが出る、スモークテスト（`deployment-strategy.md` の S5〜S9）が通らない、配備の後に性能の問題が出る（`memory.peak` の持ち越しを含む）。
- 戻すかどうかは依頼者に諮って決める。

## 2. 戻しの形（第一の手）: イメージだけ

- 戻し先は、配備の前に付けたタグ `mastersmith:pre-deps-ui-carryover`（イメージ ID `e0a5825d257c…`、前の Intent で配備した版）。
- この Intent に内部DB の移行と `.env` の変更は無いため、内部DB と `.env` はそのまま使う。戻しの練習は行わない（前の Intent の扱い。`project.md` の学び）。
- 新しい版が内部DB に書く形は前の版と同じ（新しい表・列は無い）。前の版は1行の長さの上限を持たないため、新しい版で適用した DSL も前の版で読める。

### 2.1 手順

```bash
docker image inspect mastersmith:pre-deps-ui-carryover --format '{{.Id}}'   # sha256:e0a5825d257c… であること
docker compose stop app
# 調べるために、今の内部DB を複写する（README の「内部DBのバックアップと戻し方」。名前は …-before-rollback.tgz）
MASTERSMITH_IMAGE_TAG=pre-deps-ui-carryover docker compose --profile targetdb-postgres up -d --no-build app
MASTERSMITH_IMAGE_TAG=pre-deps-ui-carryover docker compose --profile targetdb-postgres ps app   # healthy を待つ
```

- **戻している間は、アプリを起動・作り直す `docker compose` のコマンドに毎回 `MASTERSMITH_IMAGE_TAG=pre-deps-ui-carryover` を付けます。** 付けずに `docker compose up -d` を行うと、`mastersmith:local`（新しい版）で作り直されます。
- **`--no-build` を必ず付けます**（付けないと、戻し先のイメージが上書きされる）。
- 戻し先のタグのイメージが無いときは、README の「戻し方」のとおり、前の Intent の配備の版を `git worktree add` で取り出して WAR とイメージを作り直します。
- 開いているタブを読み込み直す（画面の部品が前の版に戻るため）。
- サブモジュールの固定先は戻さない（イメージに画面のビルドの結果が同梱されているため。要件 FR11.3）。
- Mailpit は戻さない（v1.31.5 のまま。手元でメールを見るだけの受け手で、アプリの版と関わらない）。

### 2.2 戻した後の確かめ

- `app` が healthy、`/actuator/health` が 200・UP。起動のログの ERROR が 0 件。
- 依頼者がログインでき、ホームが開ける。
- 前の版に戻したときの差: 畳んだサイドバーの印・英語のトップバーの文言・Dropdown の収まりは前の形（CSS の上書き）に戻り、DSL の状態の応答から落とした項目の数が無くなる。1行の長さの上限は無くなる。

## 3. 配備の前の複写を戻すとき（第二の手）

- 内部DB が壊れたとき・データを前の状態に戻す必要があるときだけ。配備の前の複写（`~/.mastersmith-backup/mastersmith-data-<日時>-pre-deps-ui-carryover.tgz`）を README の「戻し方」のとおり戻してから、2節の手順で戻し先のイメージを起動する。複写の後のデータと監査の行は失う。
- この Intent はスキーマを変えないため、通常は使わない。

## 4. アプリが止まったとき（`ExitOnOutOfMemoryError`）

- 前の手順の 7節のとおり。ヒープが尽きると JVM が止まり（終了コード 3）、`restart: "no"` のため起き直さない。`docker compose start app` で約 10 秒で戻る（前の Intent の Incident Response で実測）。
- `dslMixed` の `memory.peak` は Build and Test で上限の 93〜94% だったが、ヒープは最大ヒープに届かず OOM は起きなかった（持ち越し）。続けて止まるときはイメージだけで戻すかを依頼者に諮る。

## Sources

- 前の手順: `aidlc/spaces/default/intents/261004-role-menu/operation/deployment-pipeline/rollback-runbook.md`
- `cd-config.md`・`deployment-strategy.md`（この段）、`construction/build-and-test/test-results.md` 4節
- README（内部DBのバックアップと戻し方・戻し方）、`compose.yaml`（`MASTERSMITH_IMAGE_TAG`）

## Assumptions & Open Questions

- 戻し先のイメージ `mastersmith:pre-deps-ui-carryover` は、配備の前（`cd-config.md` の順 3）に付ける。
