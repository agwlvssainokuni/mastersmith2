# 戻しの手順書（rollback-runbook）

Intent 261003-user-admin-followup の配備を戻す手順です。README の「戻し方」と、前の Intent の `rollback-runbook.md`（以下「前の手順」）を正とし、差だけを書きます。

## 1. 戻すきっかけ

前の手順の 1節と同じです（起動しない、スモークテストが通らない、ERROR が出続ける・ログに秘密やメールアドレスそのものが出る、後で重大な問題が見つかりその場で直せない）。戻す前に、依頼者に伝えて戻すかを決めてもらいます。

## 2. 戻しの形: イメージだけ

- 戻し先は、配備の前に付けたタグ `mastersmith:pre-user-admin-followup`（イメージ ID `f386b55f16a8…`、ソース `b126bdc`、Intent 260930-user-admin で配備した版）。
- `.env` は変えていないため戻さない。内部DB はそのまま使い、スキーマは変わっていない（V9 のまま）。
- **バックアップは取らない**: スキーマと `.env` の変更が無く、戻し先の版は今の内部DB で動いていた版のため（`project.md` の学び）。配備の後の確かめ（`deployment-strategy.md` の S6）の複写が、配備の後のバックアップを兼ねる。

## 3. 戻したときの状態

| 項目 | 戻している間の動き |
|---|---|
| 利用停止 | **効く**（戻し先も V9 の後の版で、3つの入口で停止を判定する）。前の Intent の「戻している間は停止が効かない」制約は、今回の戻しには当たらない |
| 画面 | 今回の直しが無い状態に戻る: 確かめの表示を閉じた後のフォーカスが `body` に落ちる（N-19）、右端の行の「操作」のメニューが欠ける、送信中も言語の選択を変えられる、招待の画面の取り消しの後のフォーカスの競争 |
| ログ | 一意の制約の違反のとき、Hibernate の `org.hibernate.orm.jdbc.error` の WARN に重なったメールアドレスが出る状態に戻る（S1。`project.md` の Forbidden に反する既知の不具合）。戻している間に利用者の作成・招待で重なりが起きると、アプリのログにメールアドレスが残りうる |
| 監査 | 変わらない（今回の配備は監査の書き方を変えていない） |

- ログの状態に戻ることは、戻す前に依頼者に伝える。戻している間に重なりが起きたかは、アプリのログを `org.hibernate.orm.jdbc.error` のロガーの件数だけで確かめる（値は表示しない）。

## 4. アプリを戻す

```bash
docker image inspect mastersmith:pre-user-admin-followup --format '{{.Id}}'   # sha256:f386b55f16a8… であること
docker compose stop app
MASTERSMITH_IMAGE_TAG=pre-user-admin-followup docker compose --profile targetdb-postgres up -d --no-build app
MASTERSMITH_IMAGE_TAG=pre-user-admin-followup docker compose --profile targetdb-postgres ps app   # healthy を待つ
```

- **戻している間は、アプリを起動・作り直す `docker compose` のコマンドに毎回 `MASTERSMITH_IMAGE_TAG=pre-user-admin-followup` を付けます。** 付けずに `docker compose up -d` を行うと、`mastersmith:local`（新しい版）で作り直されます。
- **`--no-build` を必ず付けます**（付けないと、戻し先のイメージが上書きされる）。
- 戻し先のタグのイメージが無いときは、README の「戻し方」のとおり、`b126bdc` を `git worktree add` で取り出して WAR とイメージを作り直します。

### 4.1 戻した後の確かめ

| # | 確かめ | 行う人 | 通る条件 |
|---|---|---|---|
| R1 | healthy と起動のログ | AI | `app` が healthy。ERROR が 0 件（レベルとロガーと件数だけを見る） |
| R2 | ★ログイン | 依頼者 | 初期管理者でログインし、ホームが開ける。`LOGIN_SUCCEEDED` が監査に残る（送る前に依頼者に伝える） |

## 5. 戻したときのそのほかの注意

| 注意 | 中身 |
|---|---|
| `main` | `main` を取り込んだ後にアプリを戻しても、`main` は戻さない（タグは付けていない）。直した版を配備し直すときに `main` を進める |
| 見本の対象DB・Mailpit | 戻しの対象外。動かしたままでよい |
| 試験用のイメージ | `mastersmith:user-admin-followup`（T1 で使った版）は戻し先ではない。戻し先は `pre-user-admin-followup` だけ |

## 6. 戻しの練習

行いません。スキーマと `.env` の変更が無く、戻し先の版はこの内部DB で配備の直前まで動いていた（`project.md` の学び）。

- 未確認のまま残る前提: 無し（戻し先の版が今の内部DB で動くことは、配備の直前まで動いていたことで裏付けられる）。

## Sources

- 前の手順: `aidlc/spaces/default/intents/260930-user-admin/operation/deployment-pipeline/rollback-runbook.md`
- `cd-config.md`・`deployment-strategy.md`・`deployment-pipeline-questions.md`
- `construction/code-generation/code-summary.md`（S1 の確かめの結果）
- `README.md`（戻し方）
- `aidlc/spaces/default/memory/project.md`（Deployment の学び、Forbidden）

## Assumptions & Open Questions

None.
