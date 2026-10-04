# Deployment Execution の質問（Intent 261004-safety-carryover）

## 配備の前の確かめ（読み取りだけで確かめた。2026-10-04）

- アプリのソースに未コミットの変更は無い（`aidlc/` の下を除く）。`develop` は `origin/develop`（`532b39f`）より記録だけのコミット1つ（`273c021`）進んでいて、アプリのソースの差は無い。CI（run 37176658773）は success。
- 内部DB の移行は無い（V9 のまま）。`.env` は変えない（初期管理者のメールアドレスとパスワードの行は、値があることだけを確かめた）。
- 今動いている版: `mastersmith:local`（`f124296eb7ca`、healthy）。見本の対象DB と Mailpit は動いている。戻し先のタグ `mastersmith:pre-safety-carryover` はまだ無い（配備の順 2 で付ける）。
- 配備の手順は `aidlc/spaces/default/intents/261004-safety-carryover/operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`。

## 質問

### Q1. 初期管理者の状態（救済が働く見込みの確かめ。要件 FR8.2）

新しい版は起動のときに、`.env` の初期管理者の利用者が「停止中」「管理者の印が無い」「今のパスワードが `.env` のパスワードと違う」のどれかに当たれば救い、パスワードを `.env` の値に戻し、ログイン中の端末をログインし直しにします。停止と印は、配備のときに内部DB の複写で数えます。パスワードはアプリでしか照らせないため、依頼者に伺います。

A. 初期管理者のパスワードを画面で変えたことも、止めたことも、印を外したこともない（救済は働かない見込み）
B. 初期管理者のパスワードを画面で変えたことがある（救済が働く見込み。パスワードが `.env` の値に戻る）
C. 初期管理者を止めた、または印を外したことがある
D. 分からない
X. Other (please specify)

[Answer]: C

### Q2. 配備の時刻

配備ではアプリを止めます（内部DB の複写と初期管理者の確かめ、入れ替え。数分の見込み）。

A. 今すぐ行う
B. 後で行う（時刻を伝える）
X. Other (please specify)

[Answer]: A

## 追加の質問

### F1. 初期管理者が停止中だった（内部DB の複写で確かめた）

配備の前の複写（`~/.mastersmith-backup/mastersmith-data-202610041339-pre-safety-carryover.tgz`）で、`.env` の初期管理者の利用者は1行で、停止中・管理者の印あり だった。停止していない管理者はほかに1人いる。このまま新しい版を起動すると救済が働き、停止が解け、パスワードが `.env` の値で書き直され、リフレッシュトークンがすべて無効になり、監査に `INITIAL_ADMIN_RESCUED` が1行残る。

A. このまま入れ替える（救済が働くことを了承する）
B. 救済させない。入れ替える前に依頼者が `.env` の初期管理者の設定を替える（または外す）。替え終わったら知らせる
C. 配備をやめ、前の版で起動し直す
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

答えのまとめ:

- 初期管理者（Q1: C、複写の確かめ）: 停止中・管理者の印あり。救済が働く見込みで、依頼者は了承した（F1: A）。
- 配備の時刻（Q2: A）: 今すぐ。アプリは 2026-10-04T04:39:32Z に止め、配備の前の複写（`mastersmith-data-202610041339-pre-safety-carryover.tgz`）を取った。戻し先のタグ `mastersmith:pre-safety-carryover`（`f124296eb7ca`）を付けた。
- 入れ替え: `docker compose --profile targetdb-postgres up -d --build app` で入れ替え、healthy を待つ。
- スモークテストの見込み: 救済の WARN が1行（条件 `SUSPENDED`、依頼者が画面でパスワードを変えていれば `SUSPENDED+PASSWORD`）、`INITIAL_ADMIN_RESCUED` が1行（`rejection_kind` が同じ値）、`INITIAL_ADMIN_CREATED` が 0、ERROR 0、移行なし。依頼者は `.env` のパスワードでログインし、`LOGIN_SUCCEEDED` が1件以上。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
