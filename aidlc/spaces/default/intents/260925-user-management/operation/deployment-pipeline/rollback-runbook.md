# 戻しの手順書（rollback-runbook）

Intent 260925-user-management の配備を戻す手順と、配備の直後に行う戻しの練習の手順です。README の「戻し方」「内部DBのバックアップと戻し方」を正とし、この Intent で増えた注意を足します。

## 1. 戻しの二段

| 手 | いつ使うか | すること | `.env` | データ |
|---|---|---|---|---|
| **第一の手** | 新しい版の動きに問題があるが、データは壊れていない | 戻し先のイメージ `mastersmith:pre-user-management` で起動し直す（イメージを作り直さない） | **戻さない**（足した4行は1つ前の版でも害が無い。Q3: A） | V7・V8 の後の内部DB のまま使う（スキーマは戻さない） |
| **第二の手** | データが壊れた、Flyway の V7・V8 が途中で失敗した、配備の途中で中止する | バックアップを展開してから、戻し先のイメージで起動する | **複写を戻す**（Q3: A） | 配備の前の状態に戻る。**配備の後に出した招待と、登録した利用者・監査の記録は消える** |

H2 は DDL を巻き戻せないため、V7・V8 の途中の失敗には第二の手を使います（U2・U3 の `infrastructure-specification.md`）。

## 2. 第一の手

```bash
docker image inspect mastersmith:pre-user-management --format '{{.Id}}'   # 戻し先のイメージがあること
docker compose stop app                                                  # 止めるときに H2 のファイルが詰め直される
# 調べるために、今の内部DB のデータを複写する（README の「内部DBのバックアップと戻し方」）
MASTERSMITH_IMAGE_TAG=pre-user-management docker compose --profile targetdb-postgres up -d --no-build app
MASTERSMITH_IMAGE_TAG=pre-user-management docker compose --profile targetdb-postgres ps app   # healthy を待つ
```

- **戻している間は、アプリを起動・作り直す `docker compose` のコマンドに、毎回 `MASTERSMITH_IMAGE_TAG=pre-user-management` を付けます。** 付け忘れを避けたいときは、`.env` に1行足し、新しい版に戻すときに消します。
- **`--no-build` を必ず付けます**（付けないと、戻し先のイメージが上書きされる）。
- 戻した後は、健全性とスモークテスト（`deployment-strategy.md` の S1・S2・S5・S10・S11）で確かめます。1つ前の版には、S3・S4・S6〜S9 の機能がありません。

## 3. 第二の手

```bash
docker compose stop app
docker run --rm -v mastersmith_mastersmith-data:/data -v "$HOME/.mastersmith-backup":/backup eclipse-temurin:25.0.4_7-jre-noble \
  sh -c 'rm -rf /data/* && tar xzf /backup/<配備の前のバックアップ> -C /data && chown -R 10001:10001 /data'
# .env を、配備の前に取った複写に戻す（中身を表示しない）
(umask 077 && cp "$HOME/<配備の前の .env の複写>" .env)
MASTERSMITH_IMAGE_TAG=pre-user-management docker compose --profile targetdb-postgres up -d --no-build app
```

- バックアップと `.env` の複写のファイル名は、deployment-execution の記録に残します。

## 4. 戻したときの注意（この Intent で増えたもの）

| 注意 | 中身 | 出典 |
|---|---|---|
| 初期管理者のメールアドレス | **`.env` の `MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL` を配備のときのまま変えない。** 変えると、1つ前の版が `display_name`（V7 の必須の列）を渡さずに利用者を足そうとして失敗しうる（未確認） | U2 の `infrastructure-specification.md`、承認の場の D4、README |
| 空の内部DB | 空の内部DB で1つ前の版を起動するときは、V7 を当てていない内部DB を使う | 同上 |
| 招待（V8） | 1つ前の版は、招待の表を読み書きしない。今の版に戻し直すと、招待の表は戻す前のまま使われ、戻している間に期限を過ぎた招待は期限切れになる（管理者が一覧から送り直す） | U3 の `reliability-requirements.md`、README |
| 登録の完了の画面（U6） | 戻している間は `/register` の画面が無く、届いた招待のリンクは開けない。トークンは有効期限まで使えるため、今の版に戻し直せば同じリンクで続けられる | U6 の `cicd-pipeline.md` |
| ベース URL | 1つ前の版も `MASTERSMITH_WEB_BASE_URL` を読むため、Origin の確かめは同じ値に固定される。同じ `http://localhost:8080` で開けば動く | U3 の `infrastructure-specification.md`、README |
| JVM の引数 | 戻し先のイメージには `-Dh2.compactThreads=1` が無い。CPU 4 の今の配備では、もともと1本で動きは変わらない。CPU を増やした配備先では、詰め直しのスレッドが2本以上になりうる | U3 の `code-summary.md`、project.md の Tech Stack |
| ブラウザの保存（U4） | U4 の表示の設定の鍵は、1つ前の版では読まれない。写しの鍵は make-you-chic-ui が読む既存の形のため、害は無い見込み。**確かめない（見込みとして記録。Q6: B）** | U4 の `cicd-pipeline.md` |
| 見た目の設定（U8） | 1つ前の版には `/api/appearance` が無く、画面は既定のまま描く。`.env` の2項目は消さなくてよい | U8 の `cicd-pipeline.md` |
| メール（U1）・Mailpit | `.env` の SMTP の行は、1つ前の版が読まないだけで害は無い。Mailpit は止めるだけ | U1 の `cicd-pipeline.md` |
| 画面（U5・U7） | イメージだけを戻す。消すものは無い | U5・U7 の `cicd-pipeline.md` |
| Hibernate の `validate` | 1つ前の版は、V7 の余分な列と V8 の余分な表があっても起動する見込み（4 節の練習で確かめる）。外れたら、速やかに依頼者に諮る | U2 の `code-summary.md`、U3 の `code-generation-plan.md` |

## 5. 戻しの練習（配備の直後に続けて行う。Q7: A）

V7・V8 をまとめて1回行います。配備した内部DB の複写で1つ前の版を起動し、後方互換を確かめます。練習の間も、配備したアプリは動かしたままです（VM 6GiB に、配備したアプリと練習の環境の各 2g が入る）。

1. 配備した `app` を止め、配備の後（V7・V8 の後）の内部DB のバックアップを取り、`app` を起動し直します（README の「内部DBのバックアップと戻し方」）。
2. 配備の `.env` を、中身を表示せずにホームの下（権限 700）へ複写します。`mktemp -d` は使いません（colima の VM から見えないため）。
3. 複写の末尾に `MASTERSMITH_WEB_BASE_URL=http://localhost:18080` を足します（足さないと 18080 からのログインが 403）。
4. 環境変数を渡します。
   ```bash
   export MASTERSMITH_PERF_ENV_FILE="$HOME/<練習用の .env の複写>" MASTERSMITH_CONTAINER_CPUS=4 MASTERSMITH_CONTAINER_MEMORY=2g
   ```
   `-f` の compose は `.env` を読まないためです。
5. `docker compose -p mastersmith-rollback -f docker/perf/compose.yaml create app` でボリュームを作ります。そこに、1 のバックアップを `mastersmith-rollback_perf-data` へ展開し、持ち主を 10001 にします。
6. 1つ前の版を起動します。
   ```bash
   MASTERSMITH_IMAGE_TAG=pre-user-management docker compose -p mastersmith-rollback -f docker/perf/compose.yaml up -d --wait app
   ```
   `127.0.0.1:18080` に起動し、`/actuator/health` が 200 になることを確かめます。
7. 依頼者が `http://localhost:18080` で、ログイン・トークンの更新（再読み込み）・ログアウトを行います。AI は次の2つで裏付けます。
   - 練習の側のログ：「初期管理者は既にいるため、作成しませんでした」の INFO があり、ERROR が無いこと。これで、Hibernate の `validate` が V7 の列と V8 の表を許したことが分かります。
   - 止めた後に、練習の H2 を読み取り（`ACCESS_MODE_DATA=r`）で開いた `audit_events` の件数（`LOGIN_SUCCEEDED`・`LOGGED_OUT`）
8. 結果を deployment-execution の記録に書いてから、`docker compose -p mastersmith-rollback -f docker/perf/compose.yaml down -v` で練習の環境を消します。あわせて、練習用の `.env` の複写と練習用のバックアップも消します。配備の前のバックアップと `.env` の複写は残します。

気をつける点は次のとおりです。
- 練習の SMTP は配備の値（`mailpit:1025`）のままです。ただし、練習の網に Mailpit は無く、1つ前の版は招待を持たないため、送信は起きません。
- 署名鍵が同じため、練習で出したトークンは、配備したアプリでも通りえます。ログアウトで更新のトークンを無効にしてから消します。
- 練習の操作は、練習の環境の監査にだけ残ります。練習の環境ごと消えます。

## Sources

- `construction/ci-pipeline/ci-config.md`・`construction/ci-pipeline/quality-gates.md`
- `construction/u2-user-preferences/infrastructure-design/infrastructure-specification.md`（4〜6節）・`cicd-pipeline.md`
- `construction/u3-invitation/infrastructure-design/infrastructure-specification.md`・`cicd-pipeline.md`
- `construction/u1-mail/`・`construction/u4-display-foundation/` 〜 `construction/u8-instance-appearance/` の `infrastructure-design/cicd-pipeline.md`
- `construction/u2-user-preferences/code-generation/code-summary.md`・`construction/u3-invitation/code-generation/code-summary.md`
- `operation/deployment-pipeline/deployment-pipeline-questions.md`（Q3・Q6・Q7）
- `README.md`（戻し方、内部DBのバックアップと戻し方）
- `aidlc/spaces/default/memory/project.md`（Deployment・Corrections・Tech Stack）

## Assumptions & Open Questions

- 初期管理者のメールアドレスを変えたときの1つ前の版の振る舞い（利用者の追記の失敗）は、確かめていません（設計の記述のとおり、未確認）。
