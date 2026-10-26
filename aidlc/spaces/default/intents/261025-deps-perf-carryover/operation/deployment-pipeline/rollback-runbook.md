# 戻しの手順書（rollback-runbook）

Intent 261025-deps-perf-carryover の配備を戻す手順です。README の「戻し方」と、前の手順（`aidlc/spaces/default/intents/261016-business-data-views/operation/deployment-pipeline/rollback-runbook.md`）を正とし、差を中心に書きます。今回はスキーマも `.env` も変わらないため、戻しは**イメージだけ**で、戻しの練習と配備の前の内部DB の複写は行いません（`cd-config.md` の 4節・5節）。

## 1. 戻すきっかけ

- 起動が healthy にならない、ERROR が出続ける、ログに秘密・メールアドレスそのもの・業務データの値が出る、スモークテスト（`deployment-strategy.md` の S1〜S9）が通らない、配備の後に性能の問題が出る（配備の前の k6 を省いたため。`cd-config.md` の 3.3）。
- 戻すかどうかは依頼者に諮って決める。
- アプリが `ExitOnOutOfMemoryError` で止まっただけのときは、すぐには戻さない（5節）。
- Not Met 5 件（部分一致の p95 など）は、配備の前から分かっている持ち越しの目標で、戻すきっかけではない。

## 2. 戻しの形（第一の手）: イメージだけ

- 戻し先は、配備の前に付けたタグ `mastersmith:pre-deps-perf-carryover`（イメージ ID `4cec01e79026…`、前の Intent で配備した版。WAR の SHA-256 は `6426111d…`）。
- 内部DB はそのまま使い、スキーマは戻さない（V13 のまま。移行は当たっていない）。
- `.env`・`.env.targetdb`・見本の PG の `mastersmith_writer` は変えていないため、戻すことに伴う作業は無い。
- 戻すと、読み取りのプールの既定は 5 に戻る（前の版の既定。`.env` に項目が無いため）。画面は前の版の部品に戻り、`GET /api/me/table-access` の道が戻る。

### 2.1 手順

```bash
docker image inspect mastersmith:pre-deps-perf-carryover --format '{{.Id}}'   # sha256:4cec01e79026… であること
docker compose stop app
# 調べるために、今の内部DB を複写する（README の「内部DBのバックアップと戻し方」。名前は …-before-rollback.tgz、置き場は ~/.mastersmith-backup/、複写は権限 600）
MASTERSMITH_IMAGE_TAG=pre-deps-perf-carryover docker compose --profile targetdb-postgres up -d --no-build app
MASTERSMITH_IMAGE_TAG=pre-deps-perf-carryover docker compose --profile targetdb-postgres ps app   # healthy を待つ
```

- **戻している間は、アプリを起動・作り直す `docker compose` のコマンドに毎回 `MASTERSMITH_IMAGE_TAG=pre-deps-perf-carryover` を付けます。** 付けずに `docker compose up -d` を行うと、`mastersmith:local`（新しい版）で作り直されます。
- **`--no-build` を必ず付けます**（付けないと、戻し先のイメージが上書きされる）。
- 戻し先のタグのイメージが無いときは、README の「戻し方」のとおり、前の取り込み（`bdba082`）を `git worktree add` で取り出して WAR とイメージを作り直す。
- 開いているタブを読み込み直す（画面の部品が前の版に戻るため）。
- サブモジュールの固定先は戻さない（イメージに画面のビルドの結果が同梱されているため。固定先は今回の Intent で変えていない）。

### 2.2 戻した後の確かめ

| # | 確かめ | 行う人 | 通る条件 |
|---|---|---|---|
| R1 | healthy と起動のログ | AI | `app` が healthy、`/actuator/health` が 200。ERROR 0 件。Flyway は `Migrating schema` 0 件で、WARN は前の版から出ているもの（Spring の Bean の案内、Flyway の H2 の版の案内）のほかに無い |
| R2 | ★ログインと画面 | 依頼者 | 初期管理者でログインし、ホームと業務データの一覧が開ける。`LOGIN_SUCCEEDED` が監査に残る（送る前に伝える） |

### 2.3 新しい版へ上げ直すとき

- 移行は当たらない。直した WAR で `docker compose --profile targetdb-postgres up -d --build app` を行う（`MASTERSMITH_IMAGE_TAG` を付けない）。

## 3. 配備の前の複写を戻すとき（第二の手）

今回は配備の前の複写を取らないため、この手は**使えない**。H2 の版も移行も変わらないため、内部DB が壊れる見込みは低い。入れ替えの起動で内部DB が壊れたと分かったときは、戻す前に依頼者に諮る。手元にある複写は、前の Intent の `~/.mastersmith-backup/` のもの（`…-after-business-data-views.tgz` など）と、配備の後に取る `…-after-deps-perf-carryover.tgz` のみで、戻すとそれ以降のデータと監査の行を失う。

## 4. 戻したときの状態

| 項目 | 戻している間の動き |
|---|---|
| 内部DB | そのまま。前の版も同じ V13 のスキーマで動く |
| 読み取りのプール | 既定 5 に戻る。負荷の下では、部分一致の検索で借りる待ちの 503 が増える（前の Intent の Not Met の状態に戻る） |
| `GET /api/me/table-access` | 前の版の道が戻る（画面も前の版で、この道を呼ぶ） |
| 見本の PG | 変わらない。番号は閉じたまま |

## 5. アプリが止まったとき（`ExitOnOutOfMemoryError`）

- 前の手順（`aidlc/spaces/default/intents/261016-business-data-views/operation/deployment-pipeline/rollback-runbook.md` 7節）のとおり。ヒープが尽きると JVM が止まり（終了コード 3・OOMKilled false）、`restart: "no"` のため起き直さない。`docker compose --profile targetdb-postgres start app` で約 10 秒で戻る。
- 対象DB（見本の PG）が止まったとき（`OOMKilled` true など）は、アプリは動き続け、業務データの API が 503 を返す。`docker compose --profile targetdb-postgres start targetdb-postgres` で起動し直す。

## Sources

- 前の手順: `aidlc/spaces/default/intents/261016-business-data-views/operation/deployment-pipeline/rollback-runbook.md`、`aidlc/spaces/default/intents/261015-deps-ui-carryover/operation/deployment-pipeline/rollback-runbook.md`
- `cd-config.md`・`deployment-strategy.md`・`deployment-pipeline-questions.md`
- リポジトリ: README（「戻し方」「内部DBのバックアップと戻し方」）、`compose.yaml`（`MASTERSMITH_IMAGE_TAG`・`restart: "no"`）
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment）、`project.md`（Deployment・Corrections の学び）

## Assumptions & Open Questions

- [assumption] 前の版のイメージ `4cec01e79026` は、今の内部DB（V13、監査の行に今回の変更で増えた種類は無い）で起動する。今の本物のデータで動いているイメージそのものであり、Build and Test の後の `docker compose start app` でも起動を確かめている。戻しの練習は行わない（前の Intent `260928-quality-followup` と同じ扱い）。
