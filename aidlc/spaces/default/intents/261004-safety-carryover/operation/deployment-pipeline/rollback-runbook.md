# 戻しの手順書（rollback-runbook）

Intent 261004-safety-carryover の配備を戻す手順です。README の「戻し方」と、前の Intent の `rollback-runbook.md`（以下「前の手順」、`aidlc/spaces/default/intents/261003-user-admin-followup/operation/deployment-pipeline/rollback-runbook.md`）を正とし、差だけを書きます。CI の構成・関門・基盤の仕様（`ci-config`・`quality-gates`・`infrastructure-specification`・`cicd-pipeline`）は、この Intent の流れに無い段のため作られていません。

## 1. 戻すきっかけ

前の手順の 1節と同じです（起動しない、スモークテストが通らない、ERROR が出続ける・ログに秘密やメールアドレスそのものが出る、後で重大な問題が見つかりその場で直せない）。戻す前に、依頼者に伝えて戻すかを決めてもらいます。

## 2. 戻しの形: イメージだけ

- 戻し先は、配備の前に付けたタグ `mastersmith:pre-safety-carryover`（イメージ ID `f124296eb7ca…`、前の Intent で配備した版）。
- `.env` は変えていないため戻さない。内部DB はそのまま使い、スキーマは変わっていない（V9 のまま）。
- 配備の前の内部DB の複写（`cd-config.md` の 3節の順 3）が、配備の前のバックアップになる。救済で変わった状態まで戻したいときに限り、依頼者の承認のうえでこの複写を戻す（3節）。

## 3. 戻したときの状態

| 項目 | 戻している間の動き |
|---|---|
| 救済の口 | 無くなる。初期管理者が使えなくなったときの戻し方は、前の手順の RB-22（`.env` を新しいメールアドレスに替えて作り直す）に戻る |
| 救済で変わった利用者の状態 | **戻らない**。配備の起動で救済が働いていれば、初期管理者の停止の解除・印・失敗回数・パスワード（`.env` の値）・無効にしたリフレッシュトークンはそのまま残る。元の状態に戻すには、配備の前の複写を戻す（その後の監査の行とデータも失う） |
| 監査 | 配備の後に記録した `INITIAL_ADMIN_RESCUED`・`INITIAL_ADMIN_CREATED` の行は残る。前の版はこれらの種類を書かないだけで、行は読まない（監査を見る画面・API は無い） |
| ログ | Tomcat の `dispatcherServlet` のロガーが戻り、接続の待ちの時間切れなどで同じ例外の ERROR が二重に出る状態に戻る |
| 画面 | 変わらない（今回の配備は画面を変えていない） |

## 4. アプリを戻す

```bash
docker image inspect mastersmith:pre-safety-carryover --format '{{.Id}}'   # sha256:f124296eb7ca… であること
docker compose stop app
MASTERSMITH_IMAGE_TAG=pre-safety-carryover docker compose --profile targetdb-postgres up -d --no-build app
MASTERSMITH_IMAGE_TAG=pre-safety-carryover docker compose --profile targetdb-postgres ps app   # healthy を待つ
```

- **戻している間は、アプリを起動・作り直す `docker compose` のコマンドに毎回 `MASTERSMITH_IMAGE_TAG=pre-safety-carryover` を付けます。** 付けずに `docker compose up -d` を行うと、`mastersmith:local`（新しい版）で作り直され、救済がまた働きえます。
- **`--no-build` を必ず付けます**（付けないと、戻し先のイメージが上書きされる）。
- 戻し先のタグのイメージが無いときは、README の「戻し方」のとおり、前の Intent の配備の版を `git worktree add` で取り出して WAR とイメージを作り直します。

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
| 試験用のイメージ | `mastersmith:safety-carryover`（Build and Test の負荷の試験で使った版）は戻し先ではない。戻し先は `pre-safety-carryover` だけ |

## 6. 戻しの練習

行いません。スキーマと `.env` の変更が無く、戻し先の版はこの内部DB で配備の直前まで動いていた（`project.md` の学び）。

- 未確認のまま残る前提: 配備の起動で救済が働いた場合に、戻し先の版がその後の状態（救済で変わった利用者・新しい種類の監査の行）で問題なく動くこと。救済は既存の列の値を変えるだけで、新しい種類の監査の行は前の版が読まないため、動く見込みとする（未確認）。

## Sources

- 前の手順: `aidlc/spaces/default/intents/261003-user-admin-followup/operation/deployment-pipeline/rollback-runbook.md`、`aidlc/spaces/default/intents/260930-user-admin/operation/incident-response/runbooks.md`（RB-22）
- `cd-config.md`・`deployment-strategy.md`・`deployment-pipeline-questions.md`
- `construction/code-generation/code-summary.md`、`inception/requirements-analysis/requirements.md`（FR1・FR4）
- `README.md`（戻し方）
- `aidlc/spaces/default/memory/project.md`（Deployment の学び、Forbidden）

## Assumptions & Open Questions

- [assumption] 戻し先の版が、救済の後の状態と新しい種類の監査の行を含む内部DB で問題なく動く（6節。未確認）。
