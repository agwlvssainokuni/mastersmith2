# 環境の一覧（environment-inventory）

Intent `260922-auth-audit-base`（auth-audit-foundation）の配備先として用意した環境の一覧。配備先は開発者の PC 上のコンテナだけで、クラウドの環境（VPC・IAM・Secrets Manager など）は作らない（`operation/deployment-pipeline/cd-config.md`（cd-config）2節、project.md の Deployment）。決定は `environment-provisioning-questions.md`（Q1〜Q4）にある。基盤の設計は U1〜U4 の `construction/<単位>/infrastructure-design/infrastructure-specification.md`（infrastructure-specification）である。

- 用意した日時: 2026-09-23 12:07（日本時間）
- 用意した者: AI（依頼者の回答 Q2 による）
- リポジトリの版: `9130cfa`（作業ツリーには本段の変更 `compose.yaml`・`.env.example`・README が未コミットで残っている状態で用意した）

## 1. 環境の一覧

| 環境 | 状態 | 用途 | 備考 |
|---|---|---|---|
| 開発者の PC（colima 上の Docker） | **用意した** | アプリのコンテナ1台を動かす唯一の配備先 | 2〜5節 |
| CI（GitHub Actions のランナー） | 既にある（本段では触れない） | 検査と WAR の保存 | `construction/ci-pipeline/ci-config.md` |
| 検証・本番 | 無い | — | 配備先が決まってから（`cd-config.md` 9節） |

## 2. 実行の基盤（Docker）

| 項目 | 値 | 設計との関係 |
|---|---|---|
| Docker | クライアント・サーバーとも 29.8.1 | — |
| Docker Compose | 5.5.1 | U1 の `infrastructure-specification.md` 1章（`docker compose up`） |
| colima の VM | プロファイル `default`、aarch64、CPU **2**、メモリ **2GiB**、ディスク 100GiB、実行環境 docker、macOS Virtualization.Framework、マウント sshfs | 設計はコンテナの CPU の上限 4 を求めるが、VM は作り直さずにコンテナの上限を 2 に下げて動かす（Q1。3節） |

## 3. 構成の要素

| 要素 | 名前・値 | 作り方 | 設計の出どころ |
|---|---|---|---|
| WAR | `backend/build/libs/mastersmith.war`（SHA-256 の先頭 `d176967101b57b50`） | `./gradlew :backend:bootWar`（入力が変わっていないため、既にあった WAR をそのまま使った） | U1 の `infrastructure-specification.md` 1章（Q3: イメージの中ではビルドしない） |
| イメージ | `mastersmith:local`（`sha256:16928f5fc875…`、arm64、ベース `eclipse-temurin:25.0.4_7-jre-noble`） | `docker compose up -d --build`（WAR が同じため、既にあったイメージの層がそのまま使われた） | U1 の `infrastructure-specification.md` 1章（Q2: Temurin JRE 25、版の番号まで固定） |
| ネットワーク | `mastersmith_default` | `docker compose up` が作った | — |
| ボリューム | `mastersmith_mastersmith-data`（local ドライバー、2026-09-23 12:07 作成）。中身は内部DBのファイル `mastersmith.mv.db` だけ（初回の起動で Flyway が V1〜V4 を当てた状態） | `docker compose up` が作った。**次の段のために残している** | U1 の `infrastructure-specification.md` 1章（`/app/data`） |
| コンテナ | `mastersmith-app-1`（確認の後に `docker compose stop` で止めた。消していない） | `docker compose up -d` | U1〜U4 の `infrastructure-specification.md` |
| `.env` | リポジトリの直下。Git 管理外（`.gitignore` の `.env`）、権限 `-rw-------`（所有者だけ） | AI が作った（Q3） | 4節 |

## 4. 設定と秘密情報（`.env`）

値は記録しない。変数の名前と扱いだけを書く。

| 変数 | 入れたか | 値の出どころ | 備考 |
|---|---|---|---|
| `MASTERSMITH_CONTAINER_CPUS` | 入れた（2） | Q1 | `compose.yaml` の `cpus: ${MASTERSMITH_CONTAINER_CPUS:-4}` に使う。コンテナの環境変数にも渡るが、アプリは使わない |
| `MASTERSMITH_AUTH_SIGNING_KEY` | 入れた | `openssl rand -base64 32` の乱数を、画面に出さずに書き込んだ（Q3） | 32 バイト。値はどこにも表示・記録していない |
| `MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL`・`MASTERSMITH_AUTH_INITIAL_ADMIN_PASSWORD` | **入れていない**（行はコメントのまま） | 依頼者が後から入れる（Q3） | 入れるまで初期管理者は作られず、ログインはできない。入れて再起動すると作られる（アプリの警告の文言による） |
| `MASTERSMITH_DB_PASSWORD` | 入れていない（既定の空） | Q4 | 内部DBは空のパスワードで作られた。後から設定すると、DB 側の変更なしでは開けなくなる |
| そのほか | 入れていない | 既定値を使う | 外部エクスポートは既定で無効 |

秘密情報の置き場所は `.env` だけで、GitHub の秘密情報の保管やクラウドの秘密情報の保管は使わない（U1・U2 の `infrastructure-specification.md` 3章）。

## 5. 本段で変えたリポジトリのファイル

| ファイル | 変更 | 理由 |
|---|---|---|
| `compose.yaml` | `cpus: 4` を `cpus: ${MASTERSMITH_CONTAINER_CPUS:-4}` にし、理由のコメントを足した | Q1。既定は設計どおり 4 |
| `.env.example` | 「コンテナ（docker compose）」の区分に `MASTERSMITH_CONTAINER_CPUS` をコメントで足した | 同上 |
| `README.md` | 「前提の道具」の注意と「環境変数」の表に `MASTERSMITH_CONTAINER_CPUS` を足した | 同上 |

## 6. 後片付けと作り直し

| 操作 | コマンド | 注意 |
|---|---|---|
| コンテナを起動し直す | `docker compose up -d --wait` | `.env` と `compose.yaml` を読み直す |
| コンテナとネットワークを消す（データは残す） | `docker compose down` | ボリュームは残る |
| 環境をすべて消す | `docker compose down -v` と `.env` の削除 | **ボリュームの内部DB（監査ログを含む）も消える**。今は初回の起動の中身だけだが、利用を始めた後はバックアップの前に行わない（README の「消してはいけない操作」） |
| colima の資源を設計どおりにする | `colima stop` → `colima start --cpu 4 --memory 4` の後、`.env` の `MASTERSMITH_CONTAINER_CPUS` の行を消す | colima のほかのコンテナも一度止まる |

## Sources

- `aidlc/spaces/default/intents/260922-auth-audit-base/operation/environment-provisioning/environment-provisioning-questions.md`（Q1〜Q4、確認済みの要約）
- `aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/cd-config.md`（環境ごとの扱い、配備の前の条件）
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u1-app-skeleton/infrastructure-design/infrastructure-specification.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u2-authentication/infrastructure-design/infrastructure-specification.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u3-access-control/infrastructure-design/infrastructure-specification.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u4-audit-log/infrastructure-design/infrastructure-specification.md`
- 2026-09-23 12:01〜12:08 に実行した `docker version`・`docker compose version`・`colima status`・`colima list`・`docker info`・`docker image inspect`・`docker volume inspect`・`docker compose up`・`docker compose stop` の出力
- `compose.yaml`、`Dockerfile`、`.dockerignore`、`.env.example`、`.gitignore`

## Assumptions & Open Questions

- 初期管理者の値は依頼者が `.env` に入れる。入れるまでは、次の段（Deployment Execution）のスモークテストのログイン以降の項目を行えない。
- コンテナの CPU の上限が 2 のため、ログインの照合の時間の目標（同時 10 件で 1 秒以内、U2 の NFR1.1・NFR1.3）を満たさないおそれがある。どの上限の値で測るかは Performance Validation で決める。
