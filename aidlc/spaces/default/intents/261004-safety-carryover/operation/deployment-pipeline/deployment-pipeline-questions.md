# Deployment Pipeline の質問（Intent 261004-safety-carryover）

## 決まっていること（質問にしない）

- 配備先は開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト `mastersmith`）で、依頼者の承認のうえで手で配備する（`team.md`・`project.md` の Deployment）。CI Pipeline・Infrastructure Design の段が無いため、前の Intent（`261003-user-admin-followup`）の配備の手順を正とし、今回の差だけを書く（`project.md` の学び）。
- 方式は1台の置き換え。戻しはイメージだけ（戻し先のタグ `mastersmith:pre-safety-carryover`）。内部DB のスキーマは変わらない（V9 のまま。新しい移行は無い）。`.env` は変えない。
- 変わるもの: アプリのソース（救済の口と監査、`application.yaml` の Tomcat のロガー）、`Dockerfile` の `FROM` のダイジェスト、`compose.yaml` の監視の2つのイメージのダイジェスト（profile `monitoring` だけ）。
- 配備の前の k6 は流さない。Build and Test で同じソースのイメージ（`mastersmith:safety-carryover`）で測った（`project.md` の学び「試験済みのときは省く」）。
- 配備の前の E2E（`./gradlew e2eTest`）はこの段で流し、E2E のアプリの起動のログで救済の WARN が 0 件であることも件数で確かめる（Code Generation の D5・D7: B、Build and Test の Q3: B、レビューの R-05）。
- `develop` の CI（run 37176658773）の結果を、この段で確かめて記録する（Build and Test の Q6: B）。

## 質問

### Q1. 配備の前に、初期管理者が救済の条件に当たりうるかの確かめ方（要件 FR8.2）

新しい版は起動のたびに、`.env` の初期管理者の利用者が「停止中」「管理者の印が無い」「今のパスワードが `.env` のパスワードと違う」のどれかに当たれば救い、パスワードを `.env` の値に戻し、その利用者のリフレッシュトークン（ログイン中の端末）を無効にします。

A. 依頼者に「初期管理者のパスワードを画面で変えた・止めた・印を外したことがあるか」を尋ね、あわせて配備のときにアプリを止めた後、内部DB の複写（配備の前のバックアップを兼ねる）を読み取りで開いて、初期管理者の停止と印の有無だけを数える（メールアドレスは表示せず、`.env` の値で照らす）。救済が働く見込みなら、入れ替えの前に了承を得る
B. 依頼者に尋ねるだけにする（内部DB は読まない）
X. Other (please specify)

[Answer]: A

### Q2. 配備の後の確かめ（スモークテスト）の範囲

画面の変更は無いため、画面の確かめ（前回の S4・S5）は入れない案です。

A. 健全性と起動のログ（ERROR 0、救済の WARN の件数が Q1 の見込みどおり、初期管理者の INFO のキーに `maskedEmail` があり `email` が無い、新しい移行が無い）、依頼者のログイン、監査の記録（`INITIAL_ADMIN_RESCUED`・`INITIAL_ADMIN_CREATED` の件数が見込みどおり、`LOGIN_SUCCEEDED` が1件以上）
B. A に加えて、依頼者が利用者の管理の画面を開けることも確かめる
X. Other (please specify)

[Answer]: A

### Q3. `main` への取り込み

A. スモークテストが通った後、依頼者の承認を得て `main` を `develop` の先頭へ fast-forward で取り込む。タグは付けない（前の Intent と同じ）
B. 今回は `main` に取り込まない
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

答えのまとめ:

- 配備の前の初期管理者の確かめ（Q1: A）: 依頼者に、初期管理者のパスワードを画面で変えた・止めた・印を外したことがあるかを尋ねる。配備のときにアプリを止めた後、内部DB を `~/.mastersmith-backup/` へ複写し（配備の前のバックアップを兼ねる）、複写を読み取りで開いて初期管理者の停止と印の有無だけを数える（メールアドレスは表示せず `.env` の値で照らす）。救済が働く見込みなら、入れ替えの前に了承を得る。
- スモークテスト（Q2: A）: 健全性と起動のログ（ERROR 0、救済の WARN が見込みどおり、初期管理者の INFO のキー、新しい移行なし）、依頼者のログイン、監査の件数（`INITIAL_ADMIN_*` が見込みどおり、`LOGIN_SUCCEEDED` が1件以上）。画面の確かめは入れない。
- `main`（Q3: A）: スモークテストの後、承認を得て `develop` の先頭へ fast-forward。タグは付けない。
- 決まっていること: 配備の前に E2E を流して救済の WARN 0 件を確かめる、CI の結果を記録する、k6 は流さない、戻しはイメージだけ（`mastersmith:pre-safety-carryover`）。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
