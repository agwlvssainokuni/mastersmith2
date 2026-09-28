# Deployment Execution — Questions

配備の手順は、承認済みの `operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md` のとおりです。

次のことは、配備の手順の段と環境の段で決まっているため、質問にしません。

- 配備の前の関門：手元の `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` と、Mailpit を起動した `./gradlew e2eTest` です。CI の失敗は既知として扱います。
- 配備の前の k6：流しません（performance-validation で行います）。
- スキーマの変更：V7・V8 は、起動のときに Flyway が当てます。その前に、内部DB のバックアップと、戻し用のタグ `pre-user-management` を取ります。
- 依存するサービス：見本の対象DB と Mailpit です。どちらも健全なことを、環境の段で確かめました（`environment-provisioning/validation-report.md`）。
- 配備の後の確かめ：S1〜S12 と、配備の直後の戻しの練習です。
- 承認：次の3つは、行う直前に依頼者の承認をいただきます。
  - アプリを止める操作（バックアップ）
  - `.env` への4行の追加
  - 新しい版の起動

決まっていないのは、次の2つです。

## Q1. 配備の時間帯

配備の前の関門（verify と E2E）に、約 10 分かかります。その後、次の間はアプリが止まります。
- バックアップの間（1分ほど）
- 新しい版の起動の間（イメージの作り直しと Flyway の V7・V8 で数分）

止めている間に出た招待は、送信が FAILED になります（今は招待の機能が配備されていないため、実際には起きません）。

A. この段の中で続けて行う（関門が通ったら、すぐに止めて入れ替える）
B. 関門だけ先に流し、止めて入れ替える時刻は依頼者が指定する
X. Other (please specify)

[Answer]: A

## Q2. 差出人のメールアドレス（`.env` の `MASTERSMITH_MAIL_FROM`）

- 秘密ではない値です。
- 実在の宛先には送らないため、予約されたドメイン（`example.com`）のアドレスにします（U3 の Infrastructure Design の決定）。
- 形は、小文字で、前後に空白を置きません。
- 表示名（`MASTERSMITH_MAIL_FROM_NAME`）は、既定の `MasterSmith` のままにします。

A. `noreply@example.com`
B. `mastersmith@example.com`
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

回答をまとめると、この段では次の順に進めます。承認をいただくのは、★の直前です。

1. 配備の前の関門を流します。
   - 未コミットの変更が無いことを確かめ、配備する版のコミットのハッシュを控えます。
   - `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡して、`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を流します。
   - Mailpit を起動した状態で、`./gradlew e2eTest` を流します。
2. Mailpit を止めて消します（E2E のメールを消すため）。
3. 今動いているイメージが、前の Intent の配備の版かを確かめます。そのうえで、戻し用のタグ `mastersmith:pre-user-management` を付けます。
4. ★アプリを止めて、内部DB をバックアップします（`~/.mastersmith-backup/`）。
5. ★`.env` を、中身を表示せずにホームの下（権限 700）へ複写します。そのうえで、次の4行を足します。足した後は、項目の有無だけを数えます。
   - `MASTERSMITH_WEB_BASE_URL=http://localhost:8080`
   - `SPRING_MAIL_HOST=mailpit`
   - `SPRING_MAIL_PORT=1025`
   - `MASTERSMITH_MAIL_FROM=noreply@example.com`（Q2: A）
6. ★`docker compose --profile targetdb-postgres up -d --build` で新しい版を起動し、healthy を待ちます（V7・V8 が当たる）。
7. 配備の後の確かめ S1〜S12 を行います。S5・S7・S9・S10（監査に残る操作）と S8 は依頼者に行っていただき、送る前にお伝えします。S7 の前に Mailpit を起動し直し、確かめが終わったら止めて消します。
8. 配備の直後に続けて、戻しの練習を行います（`rollback-runbook.md` の 5 節）。練習のログイン・更新・ログアウトは、依頼者に行っていただきます。
9. 片付けます。練習の環境と練習用の複写を消します。配備の前のバックアップと `.env` の複写は残します。

配備は、この段の中で続けて行います（Q1: A）。どこかで中止の条件に当たったら、`rollback-runbook.md` のとおりに戻します。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
