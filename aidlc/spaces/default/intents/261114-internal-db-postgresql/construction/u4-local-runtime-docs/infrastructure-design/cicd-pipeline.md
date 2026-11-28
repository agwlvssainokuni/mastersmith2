# 配送の流れ（CI）と運用の手順 — u4-local-runtime-docs

この単位は CI の流れ（`.github/workflows/ci.yml`）を変えません。変えるのは、`.github/dependabot.yml` のコメント 1 か所（固定先の一覧）と、手元の運用の文書です。

## 段と関門の対応

| 段 | 流れ | 関門 | この単位の変更 |
|---|---|---|---|
| 取得・準備 | checkout → JDK 25 → Node 26 → Gradle → Gitleaks・OSV-Scanner | — | なし |
| 検査 | `./gradlew verify -PitForks=$IT_FORKS` | 失敗で全体を失敗 | 文書・compose・`.env.example` の変更が、フォーマット・リンタ・Gitleaks の範囲に入る。テスト・依存の追加は無い |
| 成果物 | WAR の保存など | — | なし |
| PostgreSQL の結合テスト | 手動の実行の入力 `internal_db_postgres`（U1） | U1 のとおり | なし。文書（`docs/verification.md`）に、入力名の正しい書き方 `internal_db_postgres` と流し方を書く（U1 の `cicd-pipeline.md` の誤記 `internal_db_postgresql` は書き換えず、文書に正しい名前を書く） |

## 変更の中身

- `.github/dependabot.yml` の docker の項のコメント「次のイメージは…固定している」の一覧に、内部DB 用の `postgres`（`compose.yaml` の `internal-db-postgres`。`targetdb-postgres` と同じ版・ダイジェストで、上げるときは一緒に上げる）を足す。`ignore` や設定の値は変えない。
- 使う Actions は今のコミットのハッシュ固定のまま。秘密情報は使わない。

## 運用の手順（文書に書く。Deployment Execution で 1 回練習する）

| 手順 | 形 | 注意 |
|---|---|---|
| 起動 | `docker compose --profile internal-db-postgres up -d`（`.env` に 3 つの変数と `MASTERSMITH_DB_*` を設定してから） | `.env` の値は文書に書かない |
| バックアップ | アプリを止め、`docker compose exec -T internal-db-postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > ~/.mastersmith-backup/<名前>.dump`。`sh -c` の単一引用符で、コンテナの中の環境変数を展開し、手元のシェルでは展開させない。`-T` で TTY を付けず、`-Fc` の出力が壊れないようにする。置き場は `~/.mastersmith-backup/`（権限 700）。コミットしない | 審査 N-02 の答え。パスワードを引数に書かない（コンテナの中の接続は信頼設定のソケット） |
| 戻し | アプリを止め、`docker compose exec -T internal-db-postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists' < ~/.mastersmith-backup/<名前>.dump`。戻した後に件数（利用者・ロール・監査の種類ごと）を数えて一致を確かめる | 失うもの（バックアップの後のデータと監査の行）を手順に書く |
| 件数の数え | `docker compose exec -T internal-db-postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -c "<件数だけの問い合わせ>"'`（個人に関する値を出さない） | H2 の `scripts/count-db-copy.mjs` は H2 のときだけ |
| H2 に戻す | `.env` の `MASTERSMITH_DB_URL`・`MASTERSMITH_DB_USERNAME` の行を消し、`MASTERSMITH_DB_PASSWORD` を空に戻し、アプリを作り直す | H2 のデータは別のボリューム（`mastersmith-data`）に残っている |

## 時間の扱い（NFR5.1）

- 通常の `push` の検査の job の時間は、この単位でほぼ変わらない（テスト・依存を足さない）。Build and Test で、CI の検査の job の時間を基準（中央値 21分32秒）の 1.1 倍（約 23分42秒）と約 42 分の線と並べて記録する。

## デプロイ・戻し・昇格

- 手元の配備の既定（H2）は変えない。内部DB の PostgreSQL は profile で選ぶ。配備の戻しは、直前の版のイメージでの再配備（今の手順のまま）に加え、PostgreSQL を選んでいたときは、`pg_restore` による戻し（上の表）。DB スキーマの変更は前進のみで、1 つ前の版のアプリが動く後方互換を保つ（U1 の PostgreSQL 用の移行は H2 と同じ版の番号・同じ表）。
- 環境の昇格・フィーチャーフラグ・blue-green は、配備先が決まるまで無い（`team.md` の Deployment）。

## 秘密情報と固定先（Dependabot）

- 新しい依存・イメージ・Action を足さない（内部DB 用のイメージは `targetdb-postgres` と同じ版とダイジェスト）。`TargetDbImages` は変えない（既存の定数を U1 のテストの土台も使う）。

## Sources

- `.github/workflows/ci.yml`・`.github/dependabot.yml`・`docs/verification.md`・`docs/internal-db.md`（読み取り）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u4-local-runtime-docs/nfr-design/security-design.md`（DD-6・DD-9）・`reviews/review-01.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/infrastructure-design/cicd-pipeline.md`
- `aidlc/spaces/default/memory/team.md`（Deployment）

## Assumptions & Open Questions

- [assumption] `pg_dump`・`pg_restore`・`psql` が、コンテナの中の `POSTGRES_USER` と信頼設定のソケット接続で、パスワードなしで動く。Code Generation の最初の確かめで、使い捨てのコンテナで確かめる。
