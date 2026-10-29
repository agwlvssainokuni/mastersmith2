# Deployment Execution の質問（Intent 261027-perf-targets-review）

入れ替えの前の確かめと、アプリを止める承認のための質問です。流れは `operation/deployment-pipeline/cd-config.md` の 3節。

## 入れ替えの前の確かめ（質問にしない）

| 確かめ | 結果 |
|---|---|
| アプリのソースに未コミットの変更が無い（`aidlc/` の下は除く） | 0 件 |
| `git diff --stat main HEAD -- . ':!aidlc'` | `perf/`・`.github/dependabot.yml` の 6 ファイルだけ（219 行追加・12 行削除）。`backend/src/main`・`frontend/src`・`Dockerfile`・`compose.yaml` は同一 |
| WAR の作り直し（`./gradlew :backend:bootWar --rerun-tasks`） | 成功。SHA-256 は作り直しの前後とも `ff50498b88a97ac122ac39336ed946908389b1ddcacb14665e8412deead6f9f0`（動いているイメージの中の WAR と同じ）。画面のビルド（`static/` 98 件）と読み取りのプールの既定 10 が WAR の中にある |
| シェルに `MASTERSMITH_IMAGE_TAG` が残っていない | 0 件 |
| 動いているコンテナ | `mastersmith-app-1`（`mastersmith:local`、healthy）、`mastersmith-mailpit-1`（healthy）、`mastersmith-targetdb-postgres-1`（PORTS は `5432/tcp` だけ） |
| 電源・ディスク | AC 電源で 100%。ディスクの使用 32% |
| 内部DB の複写の置き場 | `~/.mastersmith-backup/`（権限 700）。既存の複写は権限 600 |
| Dependabot の PR | #80 のみ（見送り。依頼者が閉じる） |

## Q1. 初期管理者の状態（救済の見込み）

アプリの起動時に、初期管理者の利用者が停止中・管理者の印なし・パスワードが `.env` の値と違うと、自動で `.env` の値に戻す救済が働きます。入れ替えの起動でも働きえます。前の配備の後に、初期管理者について次のことをしましたか？

A. していない（パスワードを画面で変えていない・止めていない・管理者の印を外していない）。救済は働かない見込み
B. 初期管理者のパスワードを画面で変えた。入れ替えの起動で `.env` の値に戻ることを承知で進める
C. 初期管理者を止めた、または管理者の印を外した。入れ替えの前に `.env` を先に替える必要があるため、ここで止めて相談する
X. Other (please specify)

[Answer]: A

## Q2. アプリを止めて入れ替えてよいか

アプリが約 1〜2 分止まります（`cd-config.md` の順 5〜7）。止める前に今のイメージへ戻し先のタグ `mastersmith:pre-perf-targets-review` を付けます。入れ替えの後にスモークテスト（初期管理者でのログインなど）があり、ログインの操作はあなたにお願いします。

A. 今から入れ替える
B. 少し待つ（準備ができたら知らせる）
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- Q1（A）: 初期管理者について、前の配備の後に画面でパスワードを変えた・止めた・印を外したことは無い。救済は働かない見込みで、実際にも救済のログは 0 件だった。
- Q2（A）: 入れ替えを行った。戻し先のタグ `mastersmith:pre-perf-targets-review`（`3075065a2653`）を付けて停止し、WAR の SHA-256 が `ff50498b…` と同じことを確かめて入れ替えた。
- 入れ替えの実際: 1 回目の `up -d --build app` は、新しくビルドされたイメージ（`e0ede6ddd5c4`、中身は同じ）にコンテナを作り直さず、旧イメージ（`3075065a2653`）のまま起動し直しただけだった。`--force-recreate` で作り直し、動いているコンテナを `mastersmith:local`（`e0ede6ddd5c4`）にそろえた。healthy・`/actuator/health` 200・ERROR 0・Flyway の移行 0 件（現在の版 13）。
- スモークテスト: S1・S2・S5 は AI、S3（ログイン 1 回）・S4（一覧と詳細に問題なし）は依頼者。配備の後の複写で T0（`2026-10-29T03:17:57Z`）以降の監査を数えた: `LOGIN_SUCCEEDED` 1、`LOGGED_OUT` 1（どちらも SUCCESS）。FAILURE・UNKNOWN は 0。
- 試験用タグ `mastersmith:perf-targets-review` はタグだけを消した。`main` への取り込みは、`154c27b` の CI の結果を試行ごとに確かめ、依頼者の承認を得てから行う。`git push` と PR #80 を閉じる操作は依頼者が行う。

Does this all look correct before I generate the artifacts?

- Looks correct
- Request changes

[Answer]: Looks correct
