# 戻し方の手順書（rollback-runbook）

## 1. 戻すと決める基準

`deployment-strategy.md` 4節の条件に当たったとき。設定の誤り（`.env`・`.env.targetdb` の値など）が原因とはっきりしている場合は、版を戻さずに設定を直す。戻すかどうかは依頼者が決める。

## 2. 直前の版に戻す（イメージだけ。Q4: B・F1: A）

戻すのはアプリのイメージだけ。`compose.yaml`・`.env`・`.env.targetdb` は新しいままにする。前の版のアプリは見本の対象DB の2項目を使わないため、`.env` を戻す必要は無い（戻すとアプリのコンテナが再び見本の対象DB の管理者のパスワードを持つ）。データはそのまま（スキーマは変わらない）。

```bash
docker tag mastersmith:pre-followup mastersmith:local    # 戻し先のイメージを配備のタグに付け直す
docker compose up -d --no-build app                        # アプリのコンテナだけを作り直す（見本の対象DB はそのまま）
docker compose ps                                           # app が healthy になるまで待つ（最長で約 2 分）
docker image inspect mastersmith:local --format '{{.Id}}'   # sha256:1585bd4e… であること
```

- 戻した後にスモークテスト（`deployment-strategy.md` 3節の健全性・ログイン・管理者向け領域・DSL の画面）を行う。
- 戻した版では、1件目〜7件目の直し（Loki の属性、同時の初めてのログイン、1行1件のログ、閉じるボタンの名前）は無くなる。コンテナの設定（メモリの既定 2g、`.env.targetdb`）は新しいまま残る。
- 戻した後、`develop` は新しい版のままになる。原因を直してから 5節で新しい版に戻す。

## 3. `.env` の複写を使うのは、移し替えに失敗したときだけ

`deployment-strategy.md` 2節の手順 6 で行の数が合わない、`.env` を壊した、などのときに限り、手順 5 の複写から戻す（値は表示しない）。

```bash
ls -l ~/.mastersmith-backup/ | awk '{print $1, $NF}'        # 複写があること（中身は開かない）
( umask 077; cp ~/.mastersmith-backup/env-<日時>-before-followup .env )
rm -f .env.targetdb                                          # 作りかけを消してから、手順 5 からやり直す
```

## 4. 見本の対象DB の扱い（アプリの戻しの対象外）

- 見本の対象DB が起動しないときは、アプリは戻さない。`docker compose logs targetdb-postgres` で原因を見る（値は表示しない）。
- `.env.targetdb` が無い・行が足りないと、作り直したコンテナの環境変数のパスワードが空になる。ボリュームがあれば DB は初期化されずに起動するが、初めて作るとき（ボリュームが無いとき）は作れない。3節の複写から2行を取り直す。
- 古い起動の入口に戻す手順は持たない（`compose.yaml` は新しいまま）。

## 5. 新しい版に戻す（戻した後、原因を直してから）

原因を直した版を `develop` に取り込み、`deployment-strategy.md` 2節の手順 3 と 7 以降をやり直す（手順 4 のタグは付け直さない。`mastersmith:pre-followup` を残す）。

## 6. してはいけないこと

- `docker compose down -v`（内部DB と見本の対象DB のボリュームが消える）。
- `.env`・`.env.targetdb`・複写の中身を表示する、コミットする（project.md の Forbidden）。
- 戻し先のタグ `mastersmith:pre-followup` を、戻すことが無いと決まる前に消す。

## 7. 戻し方の練習

戻し方の練習（2節を実際に行って戻れること）を Deployment Execution で行うかは、その段で依頼者に確かめる。行わないときは、未確認のまま残る前提と次に確かめる機会を記録する（project.md の Corrections）。

## Sources

- `aidlc/spaces/default/intents/260924-followup-fixes/operation/deployment-pipeline/deployment-pipeline-questions.md`（Q4: B、F1: A）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/deployment-pipeline/rollback-runbook.md`

## Assumptions & Open Questions

- 前の版のイメージは、新しい `compose.yaml` と `.env`（見本の対象DB の2行が無い）で起動できる見込み（前の版は2行を使わず、メモリの上限は `.env` の値が効く）。練習を行わないときは未確認のまま残る。
