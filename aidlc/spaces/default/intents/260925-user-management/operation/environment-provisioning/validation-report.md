# 環境の確かめの結果（validation-report）

Intent 260925-user-management の配備先の環境を、承認済みの設計の値と突き合わせた結果です。確かめは、読み取りだけで行いました（2026-09-29）。アプリの入れ替え・`.env` の変更・コンテナの起動と停止は、deployment-execution で行います。

**判定: 合格**（設計の値を満たせない点は無し）

## 1. 設計の値との突き合わせ

| # | 設計の値（出典） | 確かめ方 | 結果 |
|---|---|---|---|
| E1 | VM は CPU 4・メモリ 6GiB（README の「コンテナの資源の上限」、U2 の `infrastructure-specification.md`） | `colima list` | 満たす |
| E2 | アプリのコンテナの上限は CPU 4・メモリ 2g（`.env` の `MASTERSMITH_CONTAINER_*`） | `docker inspect` の `HostConfig` | 満たす（2,147,483,648 B・4 CPU） |
| E3 | 配備したアプリと戻しの練習の環境（各 2g）を同時に動かせる（U2 の `infrastructure-specification.md`） | 上限の合計（約 4.8GB）と VM（6GiB） | 満たす |
| E4 | バックアップの置き場は `~/.mastersmith-backup/`、権限 700（README） | `ls -ld` | 満たす |
| E5 | 戻しの練習は `127.0.0.1:18080`、プロジェクト `mastersmith-rollback`（`rollback-runbook.md`） | `docker compose -p mastersmith-rollback -f docker/perf/compose.yaml config --quiet`、番号の空き | 満たす |
| E6 | Mailpit は profile `mail`、`127.0.0.1` だけ、版はダイジェスト固定（U1 の `cicd-pipeline.md`） | `compose.yaml`・`docker inspect`・API の 200 | 満たす |
| E7 | 配備の `.env` に4行（ベース URL・SMTP・差出人）を足す（U3 の `infrastructure-specification.md`、`cd-config.md`） | 項目の名前だけを数えた（値は見ない） | 今は無い。deployment-execution で足す（予定どおり） |
| E8 | 使うイメージ（Mailpit・`eclipse-temurin:25.0.4_7-jre-noble`）が手元にある | `docker images` | 満たす |
| E9 | compose の設定が壊れていない | `docker compose --profile targetdb-postgres --profile mail config --quiet` | 満たす |
| E10 | 今のアプリが健全 | `curl http://localhost:8080/actuator/health` | 満たす（UP） |

## 2. セキュリティの確かめ（セキュリティの担当の観点）

| # | 確かめ | 結果 |
|---|---|---|
| S1 | 公開する番号は PC の `127.0.0.1` だけ（アプリ 8080・Mailpit 8025 と 1025・Grafana 3000・戻しの練習 18080）。見本の対象DB は公開しない | 満たす（`compose.yaml`・`docker/perf/compose.yaml`・`docker inspect` の `PortBindings`） |
| S2 | 秘密を含む設定のファイル（`.env`・`.env.targetdb`）は Git の管理外で、権限が絞られている | 満たす（`git check-ignore`、どちらも 600） |
| S3 | アプリのコンテナは root で動かない | 満たす（利用者 `10001:10001`） |
| S4 | バックアップと `.env` の複写は、リポジトリの外（ホームの下、権限 700）に置く | 満たす（置き場は 700。複写は deployment-execution で `umask 077` で作る） |
| S5 | Mailpit は受けたメールを外へ中継しない。受けたメールには有効な招待のリンクが入るため、配備の前と確かめの後に止めて消す（`cd-config.md`、Q5: A） | 手順に入っている（deployment-execution） |
| S6 | 秘密の値を画面・記録に出さない | この段では値を開いていない（`.env` は項目の名前だけ） |

## 3. 個人に関する値の扱い（コンプライアンスの担当の観点）

- 配備した内部DB には、利用者のメールアドレス・氏名・パスワードのハッシュと、監査の記録（入力されたメールアドレス・送り元の IP・ユーザーエージェント）が入ります。この Intent では、次のものが加わります。
  - 招待の宛先のメールアドレス
  - 利用者の表示の設定
- 配備の後の確かめ（S7・S9）では、予約されたドメイン（`@example.com` など）の宛先だけを使います。実在の宛先には送りません（project.md の Forbidden）。
- バックアップ（`~/.mastersmith-backup/`）には、上の個人に関する値がそのまま入ります。置き場の権限は 700 で、リポジトリの外です。前の Intent のバックアップの消し方と保存の期間は決まっていません。配備先が決まったときに見直す点として残します。
- 外部の法令の枠組み（GDPR・PCI-DSS など）に当たる扱いは、配備先が開発者の PC の間は対象外です。配備先が決まったときに見直します（project.md の Deployment）。

## 4. 持ち越すこと

| こと | 持ち主 |
|---|---|
| `.env` への4行の追加、バックアップ、戻し用のタグ、入れ替え | deployment-execution |
| Grafana の Explore（`GF_USERS_VIEWERS_CAN_EDIT`）とログの外部エクスポート | observability-setup |
| 古いイメージのタグと古いバックアップの整理 | 依頼者の判断（この Intent では行わない） |

## Sources

- 読み取りの結果（`colima list`・`docker inspect`・`docker compose config --quiet`・`docker images`・`ls -l`・`git check-ignore`・`curl`）
- 各単位の `construction/u*/infrastructure-design/infrastructure-specification.md`（U1〜U3 は `cicd-pipeline.md` も）
- `operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`
- `aidlc/spaces/default/memory/project.md`（Deployment・Corrections・Forbidden）

## Assumptions & Open Questions

- バックアップの保存の期間と消し方は、配備先が決まったときに見直します。
