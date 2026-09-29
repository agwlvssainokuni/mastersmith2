# Deployment Execution の質問（260929-log-deps-cleanup）

## 配備の前の確かめ（読み取りだけ、2026-09-29）

- アプリのソースに未コミットの変更は無い（残っているのはこの Intent のワークフローの記録と監査ログだけ）。
- `develop`（`7fd674c`）は `origin/develop`（`8489241`）より2つ先にあるが、差はワークフローの記録のコミット（`89dc4d2`・`7fd674c`）だけで、アプリのソースは `8489241`（CI 36615809940 が成功）と同じ。
- `main`（`c1ed553`）は `develop` の祖先で、fast-forward で取り込める。
- 動いているコンテナ: `app`（healthy）・`lgtm`（healthy）・`targetdb-postgres`。
- 内部DB のスキーマの変更（Flyway の新しい版）は無く、`.env` も変えない。配備の前の k6 とバックアップは行わない（`cd-config.md`）。

## Question 1（入れ替えとスモークテスト）
配備の手順（`cd-config.md` の 3節）のとおり、戻し先のタグを付けてからアプリのイメージを作り直して入れ替えます。入れ替えの間（イメージの作り直しの後の起動の約1分）アプリが止まります。スモークテストの S4・S5（`deployment-strategy.md`）では、あなたに初期管理者でログインとログアウトをしてもらい、`LOGIN_SUCCEEDED`・`LOGGED_OUT` が配備した内部DB の監査に残ります。今、入れ替えてよいですか？

A. 今、入れ替える（S4・S5 のログインとログアウトは、入れ替えの後に行う）
B. 入れ替えは今行い、S4・S5 は後で行う（そのときに知らせる）
C. 今は入れ替えない（時間を Other に書く）
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- 今、戻し先のタグ `mastersmith:pre-log-deps-cleanup` を付けてからアプリのイメージを作り直して入れ替え、healthy を待つ（Q1: A）。
- スモークテストの S1〜S3 は AI が値を出さずに確かめ、S4・S5 は入れ替えの後に依頼者がログインとログアウトをして、AI が監査で裏付ける。
- 通ったら、依頼者の承認を得て `main` を `develop` の先頭へ fast-forward で取り込む（タグは付けない）。プッシュは依頼者が行う。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
