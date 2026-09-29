# 戻しの手順書（rollback-runbook）

Intent 260928-quality-followup の配備を戻す手順です。README の「戻し方」を正とし、前の Intent の手順書（`aidlc/spaces/default/intents/260925-user-management/operation/deployment-pipeline/rollback-runbook.md`）との差を書きます。流れの全体は `cd-config.md`、中止の条件は `deployment-strategy.md` の 4節にあります。

## 1. 戻しの形

この Intent にはスキーマの変更も `.env` の変更も無いため、戻しは **イメージだけ** です（前の Intent の「第一の手」にあたる）。内部DB のデータはそのまま使います。バックアップの展開（前の Intent の「第二の手」）は要りません。

## 2. アプリを戻す

```bash
docker image inspect mastersmith:pre-quality-followup --format '{{.Id}}'   # 戻し先のイメージがあること
docker compose stop app
MASTERSMITH_IMAGE_TAG=pre-quality-followup docker compose --profile targetdb-postgres up -d --no-build app
MASTERSMITH_IMAGE_TAG=pre-quality-followup docker compose --profile targetdb-postgres ps app   # healthy を待つ
```

- 戻している間は、アプリを起動・作り直す `docker compose` のコマンドに毎回 `MASTERSMITH_IMAGE_TAG=pre-quality-followup` を付ける。
- `--no-build` を必ず付ける（付けないと戻し先のイメージが上書きされる）。
- 戻した後は、健全性とスモークテストの S1・S3・S4・S7・S8（`deployment-strategy.md` の 3節）で確かめる。

## 3. 見本の対象DB を戻す（見本の対象DB だけに問題があるとき）

`compose.yaml` は新しい digest（`5a5a84b1…`）を指しているため、古い digest（`86c951e0…`）で起動し直すには、その場で `compose.yaml` を書き換えずに、次の一時の上書きのファイルを使う。

```bash
# 一時の上書き（コミットしない。終わったら消す）
cat > /tmp/targetdb-rollback.yaml <<'EOF'
services:
  targetdb-postgres:
    image: postgres:18.6@sha256:86c951e05bf56c93d95d397747fb8820ac76cc3bedb78f43abd83eedbe3666ae
EOF
docker compose -f compose.yaml -f /tmp/targetdb-rollback.yaml --profile targetdb-postgres up -d --no-build targetdb-postgres
```

データはボリューム `mastersmith_mastersmith-targetdb-postgres` にあり、どちらの digest でも同じ 18.6 のため、そのまま読める見込み（確かめていない）。

## 4. 戻したときの注意（この Intent の差）

| 注意 | 中身 |
|---|---|
| 画面の見た目 | 戻し先のイメージは make-you-chic-ui の古い固定先（`735ef04`）のため、コントラストの不足（primary のボタン・アバター・dark の誤りの文字）が戻る |
| 監視 | 戻し先の WAR にはバケットの設定が無いため、手元の監視を起動しても p95 の警報3件は値を持たない（前の状態に戻る） |
| 依存 | 戻し先の WAR の Jackson は 3.1.5 で、High の脆弱性（GHSA-q4xh-88c3-wmh7）を持つ。戻すのは短い間にとどめる |
| 内部DB | スキーマは変わっていないため、戻し先のイメージでそのまま起動できる |
| `main` | `main` を取り込んだ後にアプリを戻しても、`main` は戻さない（リリースのタグは付けていない） |

## 5. 戻しの練習

行わない。スキーマの変更が無く、戻し先のイメージは前の Intent で配備して動いていた版そのもの（配備の直前まで動いていた）のため。未確認のまま残る前提は、3節の見本の対象DB を古い digest で起動し直せることだけ（`project.md` の Corrections の記録の決まり）。

## Sources

- `README.md`（戻し方）
- `cd-config.md`・`deployment-strategy.md`・`deployment-pipeline-questions.md`
- `construction/code-generation/code-summary.md`
- `aidlc/spaces/default/intents/260925-user-management/operation/deployment-pipeline/rollback-runbook.md`

## Assumptions & Open Questions

- 見本の対象DB の古い digest での起動し直しは確かめていない（5節）。
