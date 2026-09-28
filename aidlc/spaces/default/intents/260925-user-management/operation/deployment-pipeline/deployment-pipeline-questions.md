# Deployment Pipeline — Questions

前提（2026-09-28 時点。読み取りだけで確かめた）:

- 今動いているアプリは `mastersmith:local`（イメージ `305fddf4aa93`、2026-09-27 起動）です。前の Intent（260925-storage-memory-fixes）の版で、この Intent のコードはまだ配備していません。
- Mailpit（`mastersmith-mailpit-1`）と、見本の対象DB（`mastersmith-targetdb-postgres-1`）が動いています。
- `.env` には、ベース URL・SMTP・差出人の行がありません（項目の名前だけを見て、値は見ていません）。
- develop の先頭は `37a3a4f` です。U1〜U8 は develop に統合済みのため、配備の流れに統合の手順は要りません。

次のことは、承認済みの設計・前の段の決定・project.md の決まりで決まっているため、質問にしません。

| 決まっていること | 出典 |
|---|---|
| 配備先は開発者の PC 上のコンテナだけ。1台の置き換え。版はコミットのハッシュで見分ける | team.md・project.md の Deployment、U2・U3 の `cicd-pipeline.md` |
| 配備の前に、内部DB のバックアップ（`~/.mastersmith-backup/`、権限 700）と、今のイメージへの戻し用のタグを取る。V7・V8 をまとめて1回 | U2 の `infrastructure-specification.md` 4〜5節 |
| 戻し用のタグの名前は `pre-user-management`（設計の例のとおり） | U2 の `infrastructure-specification.md` |
| 配備の前に `.env` を中身を表示せずにホームの下へ複写する。そのうえで、秘密でない4行（`MASTERSMITH_WEB_BASE_URL=http://localhost:8080`・`SPRING_MAIL_HOST=mailpit`・`SPRING_MAIL_PORT=1025`・`MASTERSMITH_MAIL_FROM`）を、値を表示せずに足す | U3 の `infrastructure-specification.md`、U3 の Infrastructure Design の Q1: A |
| 戻しは二段とする。第一の手はイメージだけを戻す。第二の手はバックアップの展開（データが壊れたときだけ）。戻すときに初期管理者のメールアドレスを変えない | U2・U3 の `infrastructure-specification.md`、Infrastructure Design の承認の場の D4 |
| 配備の後に戻しの練習（V7・V8 をまとめて1回、`mastersmith-rollback` で 18080、ベース URL の上書き1行、Hibernate の `validate` の確かめ）を行う | U2 の Infrastructure Design の Q1: A、U3 の `infrastructure-specification.md` |
| 画面は `http://localhost:8080` で開く（`127.0.0.1` は Origin の確かめで 403） | 承認の場の D6、README |
| スモークテストの基本の項目：健全性（UP）、ログイン・ログアウト、ログイン画面の `<html lang>` と言語の切り替え（U4）、招待の一覧が開ける（U5）、フラグメントの無い `/register` で「このリンクは使えません」（U6）、アプリのログに `/register#token=` と `@example.com` が無いこと（件数だけ、U3） | 各単位の `cicd-pipeline.md` |
| 監査に残る確かめは、送る前に依頼者に伝える。パスワードの要る操作は依頼者が行い、AI はログと監査で裏付ける | project.md の Corrections |
| 今のパスワードの総当たりの見つけ方の問い合わせ（R1）の運用は、observability-setup で扱う | Build and Test の引き継ぎ |
| Grafana でログを見られるようにする（lgtm に `GF_USERS_VIEWERS_CAN_EDIT`、`.env` の外部エクスポートの2行）のは、配備の後の observability-setup | 依頼者の決定（この段の記録） |
| 今動いている版が戻し先として正しいか（前の Intent の版か）の実物の確かめは、deployment-execution | U2 の `reliability-requirements.md` |

## Q1. CI が失敗したままでの配備の関門

Build and Test では、CI（`66fe981`）が2回とも時間切れで失敗しました。依頼者の決定で、直すのは次の Intent です。前の Intent の配備の流れには「CI が失敗したら次の作業に進む前に原因を直す」がありました。

A. 手元の `./gradlew verify`（テストのタスクを作り直して流す）と、Mailpit を起動した `./gradlew e2eTest` を配備の関門とする。CI の失敗は既知として記録し、配備を進める
B. CI が通るまで配備しない（CI の直しをこの Intent の中で先に行う）
C. 配備そのものを次の Intent に回す（この Intent の Operation の段は記録だけにする）
X. Other (please specify)

[Answer]: A

## Q2. 配備の前の負荷の確かめ（k6）

project.md の決まりでは、配備の前の k6 を省けるのは「Build and Test で、配備と同じソースのイメージを、配備と同じ上限で負荷の試験済みのとき」だけです。今回の Build and Test では、k6 の場面を用意して読み込みを確かめただけで、負荷はかけていません。performance-validation の段は、配備（deployment-execution）の後に並んでいます。

A. 配備の前に、使い捨ての環境（`docker/perf/compose.yaml`、配備と同じ 2g）で、この Intent の主な場面（招待・送り直し・登録の完了・パスワードの変更・プリファレンスの保存・見た目の設定）を短く流し、止まらないこと・失敗が無いことだけを確かめる。p95 の判定は performance-validation で行う
B. 配備の前には流さず、配備の後の performance-validation にすべて任せる（決まりとの差を記録する）
C. performance-validation を、配備の前に済ませる順に変えてもらう（流れの組み替え）
X. Other (please specify)

[Answer]: B

## Q3. 配備の前に複写した `.env` を戻す場面

U3 の設計には、「戻すときはこの複写を戻す」という記述と、「第一の手では `.env` は戻さない（ベース URL・SMTP の行は1つ前の版でも害が無い）」という記述の両方があります。

A. 第一の手（イメージだけ）では戻さない。第二の手（バックアップの展開）と、配備の途中で中止するときだけ、複写を戻す
B. どの戻しでも複写を戻さない（足した4行は残す）
C. どの戻しでも複写を戻す
X. Other (please specify)

[Answer]: A

## Q4. 配備の後の確かめに足す項目（複数選べます）

基本のスモークテスト（上の表）に、次のうちどれを足すかを選んでください。★の付いた項目は、配備した内部DB と監査に行が残ります（消す操作はありません）。

A. 未認証の `GET /api/appearance` が 200 で2項目（U8。AI が行う。監査に残らない）
B. ★招待から登録の完了まで（U3・U5・U6）：依頼者が画面から予約されたドメイン（例 `@example.com`）の宛先に招待を出し、Mailpit の画面でメールを見て（U1-NFR11.1 の目視）、リンクから登録を終え、その利用者でログインする。利用者1人と監査の記録が残る
C. ★プリファレンスの保存（U7）：依頼者がログインして、表示の設定を変えて保存し、元に戻す。監査の記録が残る
D. ★パスワードの変更（U7・U2）：依頼者が、B で作った利用者のパスワードを変える（初期管理者のパスワードは変えない）。監査の記録が残る
X. Other (please specify)

[Answer]: A, B, C, D

## Q5. Mailpit に残っているメールの片付け

Mailpit には、Build and Test の E2E が送った招待のメール（1回の実行で 21 通など、有効なリンクを含む）が残っています。U3 の決まりは、「Mailpit のメールには有効なリンクが入るため、見終えたら止めて消す」です。

A. 配備の前に Mailpit を止めて消し（`docker compose stop mailpit && docker compose rm -f mailpit`）、配備の後の確かめのときに起動し直す。確かめが終わったら、また止めて消す
B. 配備の前には消さず、配備の後の確かめが終わってから止めて消す
C. 消さずに動かしたままにする
X. Other (please specify)

[Answer]: A

## Q6. 戻したときのブラウザに残る表示の設定（U4）の確かめ

U4 は、表示の設定をブラウザの保存（localStorage）に残します。直前の版はその鍵を読まないため、害は無い見込みです（U4 の `cicd-pipeline.md`）。

A. 戻しの練習で、新しい版を開いたことのあるブラウザのまま 18080 の1つ前の版を開き、画面が崩れずに出ることを依頼者が目で確かめる（練習の手順に1行足す）
B. 確かめず、見込みとして記録する
X. Other (please specify)

[Answer]: B

## Q7. 配備の後に戻しの練習を行う時期

設計（U2 の Q1: A）では、配備の後に戻しの練習を行います。練習の間も配備したアプリは動いたままです。VM（6GiB）は、配備したアプリと練習の環境（各 2g）を同時に動かせます。

A. deployment-execution の中で、配備とスモークテストの直後に続けて行う
B. deployment-execution で配備だけを行い、練習は別の日（Operation の段の終わりまで）に行う
X. Other (please specify)

[Answer]: A

## Q8. `main` へのリリースとタグ付け

team.md では、リリースのときに依頼者の承認を得て `develop` から `main` へ取り込み、`main` にタグを付けます。前の Intent は「この Intent では行わない」と明記していました。CI は今、失敗したままです。

A. この Intent では行わない（次の Intent で CI を直した後に検討する）
B. この Intent の配備の後に行う（依頼者の承認を得て、`main` へ取り込み、タグを付ける）
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

回答をまとめると、配備の手順は次のとおりです。

- **関門（Q1: A）**：配備の前に、手元の `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` と、Mailpit を起動した `./gradlew e2eTest` を流し、通ったら配備します。CI の失敗（`66fe981` の2つの時間切れ）は、既知として記録します。直すのは次の Intent です。
- **k6（Q2: B）**：配備の前には流さず、配備の後の performance-validation にすべて任せます。project.md の「Build and Test で試験済みのときだけ配備の前の k6 を省く」との差を `cd-config.md` に記録します。
- **配備の前の用意**：
  - Mailpit を止めて消す（Q5: A）
  - 今のイメージに戻し用のタグ `pre-user-management` を付ける
  - アプリを止め、内部DB をバックアップする
  - `.env` を中身を表示せずにホームの下へ複写し、秘密でない4行を値を表示せずに足す
- **配備**：WAR とイメージを作り直して起動します（V7・V8 が当たる）。健全性（UP）を待ちます。
- **配備の後の確かめ**（★は、送る前に依頼者に伝える）：
  - 基本のスモークテスト：ログイン・ログアウト、言語の切り替え、招待の一覧、`/register`、ログの件数
  - A：`GET /api/appearance` が 200 で2項目
  - ★B：Mailpit を起動し直す。依頼者が `@example.com` などの宛先に招待し、Mailpit の画面でメールを見て登録を終え、その利用者でログインする
  - ★C：依頼者がプリファレンスを変えて保存し、元に戻す
  - ★D：B の利用者のパスワードを変える
  - 確かめが終わったら、Mailpit を止めて消します。
- **戻しの練習（Q7: A）**：配備と確かめの直後に続けて行います。V7・V8 をまとめて1回、`mastersmith-rollback` の 18080 で、Hibernate の `validate` を確かめます。戻したときのブラウザの表示の設定は確かめず、見込みとして記録します（Q6: B）。
- **戻しの手順**：
  - 第一の手：イメージだけを戻します。`.env` は戻しません。
  - 第二の手：バックアップを展開します。
  - 配備の途中の中止と第二の手のときだけ、`.env` の複写を戻します（Q3: A）。
- **main へのリリースとタグ付け（Q8: A）**：この Intent では行いません。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
