# 戻しの手順書（rollback-runbook）

Intent 260929-log-deps-cleanup の配備を戻す手順です。README の「戻し方」を正とし、前の Intent の手順書（`aidlc/spaces/default/intents/260928-quality-followup/operation/deployment-pipeline/rollback-runbook.md`）との差を書きます。流れの全体は `cd-config.md`、中止の条件は `deployment-strategy.md` の 4節にあります。

## 1. 戻しの形

スキーマの変更も `.env` の変更も無いため、戻しは **イメージだけ**。内部DB のデータはそのまま使う。

## 2. アプリを戻す

```bash
docker image inspect mastersmith:pre-log-deps-cleanup --format '{{.Id}}'   # 戻し先のイメージがあること
docker compose stop app
MASTERSMITH_IMAGE_TAG=pre-log-deps-cleanup docker compose --profile targetdb-postgres up -d --no-build app
MASTERSMITH_IMAGE_TAG=pre-log-deps-cleanup docker compose --profile targetdb-postgres ps app   # healthy を待つ
```

- 戻している間は、アプリを起動・作り直す `docker compose` のコマンドに毎回 `MASTERSMITH_IMAGE_TAG=pre-log-deps-cleanup` を付ける。
- `--no-build` を必ず付ける（付けないと戻し先のイメージが上書きされる）。
- 戻した後は、健全性とスモークテストの S1・S4・S5（`deployment-strategy.md` の 3節）で確かめる。

## 3. 戻したときの注意（この Intent の差）

| 注意 | 中身 |
|---|---|
| ログ | 戻し先のイメージは、初期管理者の INFO にキー `email` でメールアドレスそのものを出す（`project.md` の Forbidden に反する状態に戻る）。戻すのは短い間にとどめる |
| 画面の見た目 | 戻し先のイメージは make-you-chic-ui の `310e1ec` のため、選ばれたタブと primary のボタンの hover の文字のコントラストの不足が戻る |
| 監視 | 警報とダッシュボード（500 ms）は lgtm に読み込まれたまま。戻しても変わらない |
| `main` | `main` を取り込んだ後にアプリを戻しても、`main` は戻さない（タグは付けていない） |

## 4. 戻しの練習

行わない。スキーマの変更が無く、戻し先のイメージは配備の直前まで動いていた版そのもののため。

## Sources

- `README.md`（戻し方）
- `cd-config.md`・`deployment-strategy.md`・`deployment-pipeline-questions.md`
- `aidlc/spaces/default/intents/260928-quality-followup/operation/deployment-pipeline/rollback-runbook.md`

## Assumptions & Open Questions

None.
