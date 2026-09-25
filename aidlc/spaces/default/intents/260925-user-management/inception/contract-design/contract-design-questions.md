# Contract Design の質問

単位（`aidlc/spaces/default/intents/260925-user-management/inception/units-generation/unit-of-work.md`）と依存（`unit-of-work-dependency.md`）の辺ごとに、渡すものの形・やり取りの仕方・失敗のときの動きを決めます。部品は `components.md`、要件は `requirements.md`。

決まっていること（質問にしない）:
- エラー応答は既存の形（RFC 9457 Problem Details に `code` と `traceId`）で返し、1つの `code` は1つの状態コードに固定し、機能ごとの `ProblemTypeCatalog` に置く（`aidlc/spaces/default/codekb/mastersmith2/api-documentation.md`、`team.md` の Code Style）。
- API の版は URL に入れない。画面と API は同じ WAR で同時に変わるため、形を変えるときは同じ変更で直し、項目の追加は受け側が知らない項目を無視することで安全にする（前の Intent の決定を引き継ぐ）。
- アプリの中の部品の間では、想定内の失敗は結果の型で返し、想定外の失敗だけを例外にする。API の側で結果を見て Problem Details に変える（前の Intent の決定を引き継ぐ）。
- メールの送信は1回だけ行い、自動の再試行はしない。失敗は送信の結果として返し、管理者が送り直す（要件の Q4 B）。
- 招待の一覧は 20 件ごとのページ送り（画面の Q7 B）。

---

## Q1. 招待のリンクの形とトークンの渡し方

招待のトークンはログ・トレースの属性・エラー応答に出してはいけません（`project.md` の Forbidden）。今のトレースは URL の問い合わせ（`?` の後）を属性から外していますが、パスは残ります（開発担当の確認）。

A. リンクは `https://<ベース URL>/register#token=<トークン>` とする。`#` の後はブラウザがサーバーに送らないため、配信の要求にもトレースにも残らない。画面はトークンを取り出して、要求の本文に入れて確かめと完了の API を呼ぶ
B. リンクは `https://<ベース URL>/register?token=<トークン>` とする（問い合わせはトレースの属性から外れる。配信の要求のアクセスの記録には残りうる）。画面は同じく本文に入れて API を呼ぶ
C. リンクは `https://<ベース URL>/register/<トークン>` とする（パスがトレースに残るため、トレースの設定で外す作りを足す）
X. Other (please specify)

[Answer]: A

## Q2. 招待の送信に失敗したときの応答

要件では、送信に失敗しても招待は作られ、管理者が一覧で失敗を確かめて送り直します（FR2.4）。

A. 招待（送り直し）の API は 201（送り直しは 200）で招待を返し、本文の送信の結果を `FAILED` にする。画面は本文を見て失敗の表示を出す（エラー応答にしない）
B. 招待は作ったうえで、エラー応答（例: 502 と送信の失敗の `code`）を返す。画面はエラーとして扱い、一覧を読み直す
X. Other (please specify)

[Answer]: A

## Q3. 自分のプリファレンスとパスワードの変更の API の形

A. `GET /api/me/preferences` で氏名・言語・テーマ・文字の大きさを返し、`PUT /api/me/preferences` で4つをまとめて置き換える。パスワードの変更は `POST /api/me/password`（今のパスワードと新しいパスワード2つ）
B. `PATCH /api/me/preferences` で変えた項目だけを送る（ほかは A と同じ）
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめ:

- Q1 A: 招待のリンクは `https://<ベース URL>/register#token=<トークン>` とし、画面はトークンを取り出して要求の本文に入れて、確かめと完了の API を呼ぶ（トークンは配信の要求にもトレースにも残らない）
- Q2 A: 招待の API は 201、送り直しの API は 200 で招待を返し、本文の送信の結果を `FAILED` にする（送信の失敗はエラー応答にしない）
- Q3 A: `GET /api/me/preferences` と `PUT /api/me/preferences`（氏名・言語・テーマ・文字の大きさをまとめて置き換える）、パスワードの変更は `POST /api/me/password`
- 決まっていること: エラー応答は既存の Problem Details（1つの code は1つの状態コード）、API の版は URL に入れない、部品の間の想定内の失敗は結果の型、メールの送信は1回だけで自動の再試行はしない、招待の一覧は 20 件ごと

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
