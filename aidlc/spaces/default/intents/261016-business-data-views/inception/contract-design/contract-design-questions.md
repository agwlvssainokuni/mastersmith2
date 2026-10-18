# Contract Design の質問（Intent 261016-business-data-views）

単位の依存（`aidlc/spaces/default/intents/261016-business-data-views/inception/units-generation/unit-of-work-dependency.md`）の4つのつなぎ目（U2→U1・U3→U1・U3→U2・U3→U4）と、単位の中の監査の出来事・既存の口（`targetdb`・`role`）の使い方を契約にする。書く前に既存のコードの口を確かめた（`project.md` の学び）。

## 確かめた事実

- 権限の解決の口 `EffectivePermissionResolver`（`role.service`）は、2つ以上の対象を判定するときは `snapshotFor(userId)` を1回呼び、その写し `PermissionSnapshot` で判定する決まり。写しは適用中の DSL（`DslModel`）とその識別（`dslHash`）を持つ。業務データの機能はこの写しの DSL を判定と SQL の識別子の照合の両方に使えば、2つの間で DSL がずれない（Domain Design のレビューの R-03）。
- 既存の一覧の API（利用者の管理）は `GET` で `page` と検索の文字 `q` を問い合わせの部分に置く。トレースの属性からは問い合わせの部分が落ちる（`UrlQueryStrippingObservationFilter`）が、道の部分（`url.path`・`http.target`・`uri`）は残る。
- 置き場の API `GET /api/me/table-access?schema=..&table=..` は、見られないときに 403 `ACCESS_DENIED`、欠けた引数に 400 `VALIDATION_FAILED` を返す。エラー応答は Problem Details に安定した `code` を足した形（`@RestControllerAdvice` の1か所）。
- `targetdb.domain` には `DatabaseProduct`・`ReadPurpose`（`GENERATE`・`COMPARE`）・`SqlIdentifiers`（引用の入り口）がある。

## 決まっていること（質問にしない）

- 単位はすべて1つの WAR の中で、画面とサーバーは同じ版で配備されるため、API に版の番号を付けない。変更は同じ変更で画面とサーバーを合わせる。足すだけの変更では、画面は知らない項目を無視する。
- 業務データの機能は、要求ごとに `snapshotFor` を1回呼び、写しの DSL を判定と識別子の照合に使う（上の事実）。
- 監査の出来事は `businessdata.domain` の型で、`AuditEventListener` が受ける（ADR-003）。

## CQ1. 行の主キーと検索の値の運び方（API）

`project.md` の Forbidden により、行の値（主キーの値を含む）をトレースの属性に含めない。

A. 行の値は API の道にも問い合わせの部分にも載せず、本文で運ぶ: 一覧・検索は `POST /api/tables/{schema}/{table}/rows/search`（本文に検索の条件・ページ・件数・並び）、詳細は `POST .../rows/get`、作成は `POST .../rows`、更新は `PATCH .../rows`（本文に主キーと変えたカラム）、削除は `POST .../rows/delete`（本文に主キー）。道に載るのはスキーマ名とテーブル名（DSL の識別子）だけ（推奨）
B. 決まった REST の形にする: 一覧は `GET .../rows?検索の条件・ページ`（問い合わせの部分はトレースから落ちる）、詳細・更新・削除は `GET`・`PATCH`・`DELETE .../rows/{エンコードした主キー}`。トレースの道の属性を道の雛形（`/rows/{key}`）に置き換える手当てを足す
C. B と同じだが、主キーは問い合わせの部分（`.../row?key=...`）に置く（トレースから落ちる手当てを使い回す）
X. Other (please specify)

[Answer]: A

## CQ2. 詳細・更新の画面の道（ブラウザの URL）

画面の段で、一覧の検索の値はブラウザの道に載せない決定（SM5）をした。詳細・更新の画面の道に主キーの値を載せるかは決まっていない。

A. 主キーの値を画面の道に載せない。詳細・更新の画面は、画面の中（ブラウザの履歴の状態）に行の主キーを持ち、戻る・進む操作では保つ。再読み込みでは一覧へ戻る（推奨）
B. 主キーの値をエンコードして画面の道に載せ、再読み込み・ブックマークでも同じ行を開ける（主キーの値がブラウザの履歴に残る）
X. Other (please specify)

[Answer]: A

## CQ3. テーブルの定義（カラム・検索の項目・入力・許す操作）の渡し方

A. テーブルの定義の API（`GET /api/tables/{schema}/{table}/definition`）を分け、出すカラム（一覧・詳細）・検索の項目・入力できるカラムとフォーム部品と検証・許す操作を返す。行の API は行の値と全体の件数だけを返す。どちらの API も要求ごとに権限で判定し、行の API は定義の識別（`dslHash` と作業ロール）を返して、画面が古い定義を使っていれば読み直す（推奨）
B. 一覧・詳細の応答に毎回、定義を含める（定義の API を置かない）
X. Other (please specify)

[Answer]: A

## Requested Changes Feedback

承認の場で依頼者が Request Changes を選んだ（2026-10-18）。アーキテクチャのレビュー役の指摘 R-01〜R-09 を、次の推奨の案で直す（新しく決める論点は無い）:

- R-01: 更新・作成で FULL でないカラム（READ）を本文に入れたときは、ストーリーの決定（SL）のとおり、書けないカラムと同じ 400 と同じ code にする。C1 の decide と C3 の 403 は「テーブルが NONE」「CREATE・DELETE が不可」「FULL のカラムが1つも無いテーブルの更新」に限る。
- R-02: C3 の OpenAPI に `{schema}`・`{table}` の道の引数と、Problem Details の応答の参照を足す。
- R-03: 作成の本文は、入れたカラムだけを送り、空の入力は送らない（送らないカラムは対象DB の既定値。FR6.4・X9）。更新で空にしたカラムの空の文字列・null への写しは画面が行い（文字の型は `""`、ほかは `null`。SM2）、サーバーは型と照らして確かめる。
- R-04: 新しい API 6本はすべて `@ApiAccess` の「ログインだけ」（AUTHENTICATED）に分類すると契約に書く。
- R-05: 書き込みの成功の応答にも `definitionId` を返し、送った `definitionId` が今と違うときは、行を変えずに 409（定義が変わった）で拒否して画面に読み直させる（「返すか拒否する」を拒否にそろえる）。読み取りでは今の権限で返し、`definitionChanged: true` を付ける。
- R-06: 書けないカラムの 400 の `fieldErrors` は、NONE のカラムと DSL に無いカラムで同じ形（`field` は要求に入っていた名前、`reason` は同じ値）にする。
- R-07: C4 に依頼（5）の3点（Topbar のトリガーの名前・Table の横のスクロールの領域・Table の1ページの件数の選択）と、取り込むときの固定先の再更新の決まりを足し、Open questions に上流の対応の時期を足す。
- R-08: C5 に、`audit` が `businessdata.domain` の出来事の型に依存する向きを明記し、`audit` の境界テストへの足しとカバレッジの下限の確かめを U2 の作業として書く。
- R-09: DSL の識別子（スキーマ名・テーブル名）は道の1区切りとしてエンコードし（`/`・`%`・`?`・`#` を含む名前も `%2F` などで1区切りに収める）、サーバーは復号した名前を DSL の名前と照らすと書く。ページの番号の始まりは既存の一覧と同じ 1 と確かめたため、Open questions から外す。

## Consolidated Summary Confirmation

- 行の値の運び方（CQ1: A）: 業務データの API の道には DSL の識別子（スキーマ名・テーブル名、エンコード済み）だけを置き、行の値（主キー・検索の値・入力の値）は本文で運ぶ。一覧・検索 `POST /api/tables/{schema}/{table}/rows/search`、詳細 `POST .../rows/get`、作成 `POST .../rows`、更新 `PATCH .../rows`（主キーと変えたカラム）、削除 `POST .../rows/delete`（主キー）。
- 画面の道（CQ2: A）: 詳細・更新の画面のブラウザの道に主キーを載せず、ブラウザの履歴の状態に持つ。戻る・進むでは保ち、再読み込みでは一覧へ戻る。
- 定義の渡し方（CQ3: A）: `GET /api/tables/{schema}/{table}/definition` が、出すカラム（一覧・詳細）・検索の項目・入力できるカラムとフォーム部品と検証・許す操作・定義の識別（`dslHash` と作業ロール）を返す。行の API は行の値・全体の件数・定義の識別を返し、画面は識別が違えば定義を読み直す。どちらの API も要求ごとに権限で判定する。
- 契約にするつなぎ目: C1 U2→U1（同じ機能の中の型と口: 行の SQL の部品・出し分けの関数・拒否の code）、C2 U3→U1（定義・一覧・詳細の HTTP）、C3 U3→U2（作成・更新・削除の HTTP）、C4 U3→U4（make-you-chic-ui の固定先と「操作: …」の名前の関数）、C5 業務データの出来事 → 監査、C6 `targetdb.service` の接続の口（読み取り・書き込み）、C7 権限の解決の口の使い方（`snapshotFor` を1回、写しの DSL で判定と照合）。
- 版の番号は付けない（1つの WAR で同じ版を配備）。足すだけの変更では、受ける側は知らない項目を無視する。

- 承認の場の直し（Request Changes、R-01〜R-09）: 上の「Requested Changes Feedback」の推奨の案のとおり契約を直す（FULL の不足は書けないカラムと同じ 400、C3 の道の引数、作成は空を送らない・空の写しは画面、API の分類、書き込みの定義の識別の 409、fieldErrors の形、C4 の依頼（5）、audit の依存の向き、識別子のエンコード）。

Does this all look correct before I generate the contract summary?

- Looks correct
- Request changes

[Answer]: Looks correct
