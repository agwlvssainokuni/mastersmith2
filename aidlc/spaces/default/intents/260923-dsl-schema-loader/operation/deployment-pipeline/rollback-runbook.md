# 戻し方の手順書（rollback-runbook）

今回の配備（この Intent の版、`037a33b` 以降の `develop` の先頭）で問題が出たときの戻し方。依頼者の決定（Q3: A）により、**直前の版 `10742a3` に、配備の前に残したイメージ `mastersmith:pre-dsl` で戻す**。スキーマは V5・V6 のまま戻さず、データまで戻す必要があるときだけ配備の前のバックアップを戻す。見本の対象DB は戻しの対象外（古い版は対象DB の設定を読まない）。戻すかどうかは依頼者が決める。AI-DLC の作業の中で AI が戻す場合は、依頼者の承認を得てから行う。

## 1. 戻すと決める基準

前の Intent の `aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/rollback-runbook.md` 1節と同じ（healthy にならない、起動の失敗、スモークテストの失敗、配備の後の不具合）。今回の変更に固有のきっかけと、戻さずに直すものは `deployment-strategy.md` 4節にある。要点は次のとおり。

| きっかけ | 扱い |
|---|---|
| 新しい版が起動しない・healthy にならない・ログインや管理が動かない | 2節で版を戻す |
| Flyway の V5・V6 が失敗して起動が止まった | 2節に加えて4節でデータも戻す |
| 古い版が V5・V6 の当たった内部DB で起動しない（U4-MIGRATION が成り立たない） | 4節でデータも戻す |
| 見本の対象DB が起動しない・生成が「未設定」「接続できない」になる | 版は戻さない。`.env` を直すか、6節で見本の DB を作り直す |
| 大きな DSL の投入と適用で内部DB のファイルが増える（U4-STORAGE-RUN） | 版は戻さない。既知の制約として、アプリを起動し直す（`deployment-strategy.md` 5節） |

## 2. 直前の版に戻す（データはそのまま。通常はこれ）

| 順 | 手順 | コマンドの例 | 通る条件 |
|---|---|---|---|
| 1 | 戻し先のイメージがあることを確かめる | `docker image inspect mastersmith:pre-dsl --format '{{.Id}}'` | `sha256:8441534a745f…`（`10742a3` のイメージ）。無ければ5節の代わりの手 |
| 2 | 新しい版のアプリを止める（止めるときに H2 のファイルが詰め直される。約 1 秒） | `docker compose stop app` | 止まった |
| 3 | 今のボリュームを複写する（戻した後に調べるため） | 下の「手順 3 のコマンド」 | `~/.mastersmith-backup/mastersmith-data-<日時>-before-rollback.tgz` ができた |
| 4 | 戻し先のイメージで起動する（作り直さない） | `MASTERSMITH_IMAGE_TAG=pre-dsl docker compose up -d --no-build app` | エラーなく終わる |
| 5 | 健全になるまで待つ | `MASTERSMITH_IMAGE_TAG=pre-dsl docker compose ps app` | `app` が `healthy`（最長で約 2 分） |
| 6 | 動いているイメージと上限を確かめる | `docker inspect mastersmith-app-1 --format '{{.Image}} {{.HostConfig.Memory}} {{.HostConfig.NanoCpus}}'` | 手順 1 の ID、`2147483648 4000000000` |
| 7 | スモークテストを行う（古い版の項目だけ） | `deployment-strategy.md` 3節のうち、健全性・ログイン画面・ログイン・管理者向け領域・ログアウト・ログ・監査イベント（`LOGIN_SUCCEEDED`・`LOGGED_OUT`） | すべて通る。DSL の管理画面は古い版に無い |
| 8 | 戻した版を記録する | — | `10742a3` が、いま動いている版の記録になる |

手順 3 のコマンド:

```bash
mkdir -p ~/.mastersmith-backup && chmod 700 ~/.mastersmith-backup
docker run --rm -v mastersmith_mastersmith-data:/data -v "$HOME/.mastersmith-backup":/backup eclipse-temurin:25.0.4_7-jre-noble \
  tar czf /backup/mastersmith-data-$(date +%Y%m%d%H%M)-before-rollback.tgz -C /data .
```

- `MASTERSMITH_IMAGE_TAG` は `compose.yaml` の既にある口（`image: mastersmith:${MASTERSMITH_IMAGE_TAG:-local}`）である。**戻している間は、アプリを起動・作り直す `docker compose` のコマンドに毎回 `MASTERSMITH_IMAGE_TAG=pre-dsl` を付ける。** 付けずに `docker compose up -d` を行うと、`mastersmith:local`（新しい版）でアプリが作り直される。付け忘れを避けたいときは、依頼者が `.env` に `MASTERSMITH_IMAGE_TAG=pre-dsl` の1行を足し、新しい版に戻すときに消す（`.env` は compose の変数の展開にも使われる）。
- 手順 4 の `--no-build` は必ず付ける。付けずに `--build` を付けると、今の WAR から作ったイメージに `pre-dsl` のタグが付き、戻し先のイメージが上書きされる。
- 見本の対象DB（`targetdb-postgres`）は動かしたままでよい。古い版は `MASTERSMITH_TARGET_DB_*` を読まず、`.env` に残した9項目は使われない。止めるときは `docker compose --profile targetdb-postgres stop targetdb-postgres`（データはボリュームに残る）。
- 古い版の内部DB の既定の接続先には `;DEFRAG_ALWAYS=TRUE` が無い。戻している間は DSL の操作が無いため、ファイルの増え方の問題（U4-STORAGE-RUN）は起きない。
- 戻している間も、内部DB の DSL のプレビューと履歴の表（V5）と監査の列（V6）は残る。古い版は使わない。新しい版に戻すと、そのまま使われる。

## 3. スキーマは戻さない（U4-MIGRATION）

- V5 は表 `dsl_previews`・`dsl_applied_revisions` を作るだけ、V6 は `audit_events` に NULL を許す列（`actor_user_id`・`dsl_hash`・`dsl_source`・`rejection_kind`）を足すだけで、削除や変更は無い。
- 古い版は、自分の知らない先の移行（V5・V6）が当たった内部DB で起動することになる。Flyway は既定で知らない先の移行を無視し（`application.yaml` は `validate-on-migrate: true` のほかに無視の決まりを変えていない）、Hibernate は自分の表と列だけを確かめる（検証だけ）。古い版が監査イベントを書くとき、V6 の列は NULL になる。この前提で起動できる見込みである。
- 両者の H2 の版は同じ（`10742a3` から Spring Boot の版を変えていないため、H2 の版も同じ）で、ファイルの形の違いで開けなくなることは無い見込み。
- この見込みは Deployment Execution で確かめる（Build and Test の U4-MIGRATION は `Unverified`、持ち主は deployment-execution）。確かめ方（配備の後に実際に2節の戻しを練習するか、配備したものとは別の使い捨ての環境で古いイメージを移行後の内部DB の複写につなぐか）は Deployment Execution で依頼者と決める。

## 4. データも戻す（Flyway が失敗したとき、U4-MIGRATION が成り立たないとき、データが壊れたときだけ）

配備の前に取ったバックアップ（`deployment-strategy.md` 2節の手順 8、`~/.mastersmith-backup/mastersmith-data-<日時>-before-dsl.tgz`）を戻し、V4 までの内部DB に戻す。**配備の後に記録された DSL の操作（プレビュー・履歴）と監査イベント（スモークテストのログイン・生成・ログアウトを含む）、利用者の変更は失われる**ため、依頼者の判断で行う。

| 順 | 手順 | コマンドの例 |
|---|---|---|
| 1 | アプリを止める | `docker compose stop app` |
| 2 | 今のボリュームを複写する（失われる記録を後から調べるため。2節の手順 3 で取ったなら不要） | 2節の手順 3 のコマンド |
| 3 | ボリュームの中身を消し、配備の前のバックアップを展開し、所有者を 10001 に戻す | 下の「手順 3 のコマンド」 |
| 4 | 戻し先のイメージで起動する | `MASTERSMITH_IMAGE_TAG=pre-dsl docker compose up -d --no-build app` |
| 5 | 健全性とスモークテスト | 2節の手順 5〜8 と同じ |

手順 3 のコマンド（README の「内部DBのバックアップと戻し方」の置き場をホームの下に替えたもの）:

```bash
docker run --rm -v mastersmith_mastersmith-data:/data -v "$HOME/.mastersmith-backup":/backup eclipse-temurin:25.0.4_7-jre-noble \
  sh -c 'rm -rf /data/* && tar xzf /backup/<配備の前のバックアップのファイル> -C /data && chown -R 10001:10001 /data'
```

- バックアップと、戻す直前の複写には、メールアドレスと接続元IPが入る。`~/.mastersmith-backup/`（権限 700）の外へ移さず、リポジトリの中に置かない（リポジトリは公開）。
- データを戻した後に新しい版へ進むと、V5・V6 がもう一度当たる。

## 5. 戻し先のイメージが無いときの代わりの手

`mastersmith:pre-dsl` を消してしまったときは、README の「戻し方」（前の Intent の `aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/rollback-runbook.md` 2節）の手順で、直前の版 `10742a3` のコミットを `git worktree add` で取り出して WAR を作り直す。

- 取り出した場所の WAR を今のリポジトリに複写して `docker compose up -d --build` すると、`mastersmith:local` が古い版になる。`10742a3` と今の版の `Dockerfile` は同じため、起動の形は変わらない。
- 作り直しには `vendor/make-you-chic-ui` のビルドと `npm ci` のための時間とネットワークが要る。
- 新しい版に戻すときは、今の作業ツリーで `./gradlew verify` をやり直して WAR を作る（手元の WAR が古い版で上書きされているため）。

## 6. 見本の対象DB の扱い（戻しの対象外）

見本の対象DB は業務のデータではなく、見本のスキーマと読み取りのアカウントだけを持つ。版を戻すときも、そのまま動かしてよい。

- 止める: `docker compose --profile targetdb-postgres stop targetdb-postgres`
- 作り直す（パスワードを入れずに初めて起動した、パスワードを変えた、見本のスキーマを作り直したいとき）: `.env` の2つのパスワードを直してから、次を行う。見本のスキーマと読み取りのアカウントは、ボリュームが無い状態で起動したときだけ作られる。

```bash
docker compose --profile targetdb-postgres rm -sf targetdb-postgres
docker volume rm mastersmith_mastersmith-targetdb-postgres
docker compose --profile targetdb-postgres up -d targetdb-postgres
docker compose logs targetdb-postgres     # 初期化の誤りが無いこと
```

- 読み取りのパスワードを変えたときは、`.env` の `MASTERSMITH_TARGET_DB_PASSWORD` も同じ値にし、`docker compose --profile targetdb-postgres up -d app` でアプリのコンテナを作り直す。
- 消してよいのは見本の DB のボリューム（`mastersmith_mastersmith-targetdb-postgres`）だけ。内部DB のボリューム（`mastersmith_mastersmith-data`）は消さない。

## 7. 新しい版に戻す（戻した後、原因を直してから）

| 順 | 手順 | コマンドの例 |
|---|---|---|
| 1 | `.env` に `MASTERSMITH_IMAGE_TAG=pre-dsl` を足していたら消す（依頼者） | — |
| 2 | 直した版で `deployment-strategy.md` 2節の手順 1〜3・8〜15 を行う | `docker compose --profile targetdb-postgres up -d --build` など |
| 3 | 直した版を配備するまで、`mastersmith:pre-dsl` は消さない | — |

## 8. してはいけないこと

| 操作 | 理由 |
|---|---|
| バックアップの前にボリュームを消す（`docker compose down -v` など） | 監査ログを含む内部DB がすべて消える |
| `MASTERSMITH_IMAGE_TAG=pre-dsl` を付けて `--build` する | 戻し先のイメージが今の WAR のイメージで上書きされる |
| 新しい版が安定するまでに `mastersmith:pre-dsl` を消す（`docker rmi`、`docker image prune -a`） | 戻し先が無くなり、5節の作り直しが要る |
| スキーマの変更のファイル（`V5__*.sql`・`V6__*.sql`）を書き換える・消して戻そうとする | Flyway の確かめで起動が止まる。スキーマは前進のみ |
| 戻すために `.env` の署名鍵を替える | 発行済みのトークンがすべて無効になる。鍵の交換は戻しとは別の操作 |
| 内部DB のバックアップをリポジトリの中や `mktemp -d` の一時ディレクトリに置く | リポジトリは公開。一時ディレクトリは colima の VM から見えず、複写が空振りする |

## 9. 戻し方の練習

確かめていない戻し方は、戻し方として当てにできない。2節の戻しと3節の U4-MIGRATION は同じ前提（古い版が V5・V6 の当たった内部DB で起動する）に立つため、Deployment Execution で少なくとも U4-MIGRATION を確かめる。確かめ方（配備したアプリで実際に戻して進め直すか、使い捨ての環境で確かめるか）は Deployment Execution で依頼者と決める。練習をやめたときは、未確認のまま残る前提と次に確かめる機会をその段の成果物に記録する（`project.md` の Corrections）。

## Sources

- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/deployment-pipeline/deployment-pipeline-questions.md`（Q2: A、Q3: A、F2: A、要約の 4・5）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/deployment-pipeline/cd-config.md`、`deployment-strategy.md`
- `aidlc/spaces/default/intents/260923-colima-spec-up/operation/deployment-pipeline/rollback-runbook.md`、`aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/rollback-runbook.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/u4-dsl-management/infrastructure-design/infrastructure-specification.md`（5節）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/build-and-test/build-and-test-summary.md`（U4-MIGRATION は `Unverified`、持ち主 deployment-execution）
- `compose.yaml`（`image: mastersmith:${MASTERSMITH_IMAGE_TAG:-local}`、profile `targetdb-postgres`）、`README.md`（「戻し方」「内部DBのバックアップと戻し方」「手元で試す対象DB（compose の profile）」「消してはいけない操作」）、`backend/src/main/resources/application.yaml`（Flyway）、`backend/src/main/resources/db/migration/V5__u4_dsl_management.sql`・`V6__u4_dsl_audit_columns.sql`
- 読み取りだけで調べた今の環境（2026-09-25）: `docker image inspect mastersmith:local`（`sha256:8441534a745f…`）、`git diff 10742a3 HEAD -- Dockerfile gradle/libs.versions.toml`（`Dockerfile` と Spring Boot の版は変わらない）

## Assumptions & Open Questions

- [assumption] 古い版が V5・V6 の当たった内部DB で起動できること（U4-MIGRATION）は、Flyway と Hibernate の既定の振る舞いからの見込みで、まだ試していない。Deployment Execution で確かめる。
- [assumption] Flyway の V5・V6 が途中で失敗した内部DB では、失敗の記録のため古い版も起動できない見込みとして、4節（データも戻す）を当てた。実際には試していない。
- [assumption] `MASTERSMITH_IMAGE_TAG=pre-dsl docker compose up -d --no-build app` で、`compose.yaml` の `build` の指定があってもイメージを作らずに既存のイメージで起動することは、Docker Compose の `--no-build` の振る舞いからの見込みで、この環境では試していない。
