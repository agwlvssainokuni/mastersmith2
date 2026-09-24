# Requirements Analysis — 質問

この Intent は、前の Intent（260923-dsl-schema-loader）の振り返りで後に回した小さな修正7件です。次の点は、依頼の文や既存の決まり・コードの調査から決まっているため、質問にしていません。

- 2件目（ロックの状態の行が無い利用者が同時に初めてログインすると 500 になる）: 同時の初めてのログインでも 500 にせず、どちらのログインも普段どおり判定する（成功・失敗・失敗の回数の数え方を変えない）。不具合を再現するテストを直しと同じコミットに入れる（project.md の Mandated）。直し方（重複を「既にある」として扱う等）はコード生成の段で決める。
- 3件目（負荷の試験の台本 `dslMixed` が同じ利用者を2つの VU に割り当てる）: 場面ごとに重ならない利用者を選ぶように直す。
- 5件目（compose のアプリのメモリの上限の既定が 1g）: 既定を 2g にし、食い違っている `compose.yaml`・`docker/perf/compose.yaml`・`.env.example`・README・`docker/check-container-limits.sh` を合わせて直す。
- 7件目（make-you-chic-ui の Modal・Alert）: make-you-chic-ui 側のコミット `edb1f94`（Modal/Alert にアクセシビリティ関連の拡張インタフェースを追加）で、Modal の本文が `aria-describedby` で自動で結ばれ、閉じるボタンの名前を `closeLabel`（Modal）・`dismissLabel`（Alert）で渡せるようになった。今の固定先は `5258c8b` で、その次のコミットが `edb1f94` である。

資料と調査で決まらない点だけを尋ねます。

---

## Q1. Loki へ送るログのキーと値に、個人に関する値を含めてよいか（1件目）

1件目を直すと、ログのキーと値（`dsl.operation` など）が Loki へ属性として送られ、絞り込めるようになります。調査で、パスワード・トークンの値は載っていない一方、次の個人に関する値も一緒に送られるようになることが分かりました。

- 監査の書き込みに失敗したときの ERROR（`AuditEventListener.java` 151〜158 行）: 入力されたメールアドレス・送り元の IP・User-Agent・要求のパス
- 初期管理者を作ったときなどの INFO（`InitialAdminInitializer.java` 82・88・91 行）: 初期管理者のメールアドレス

外部への送り出しは既定で無効で、今の送り先は開発者の PC 上の手元の監視（grafana/otel-lgtm）だけです。標準出力の JSON のログには、今もこれらの値が項目として出ています。

A. すべてのキーと値を送る。今の送り先は手元の監視だけで、標準出力にも同じ値が出ているため、個人に関する値も含めてよい。パスワード・トークン・署名鍵が入らないことはテストで確かめる
B. すべてのキーと値を送るが、メールアドレス・IP・User-Agent は送り出しのときに伏せる（値を置き換える）。標準出力のログは今のまま
C. 決めたキー（`dsl.*` など、個人に関する値を含まないもの）だけを送る。ほかのキーは送らない
D. 上の2か所のログからメールアドレス・IP・User-Agent を外す（標準出力のログにも出さない）。そのうえで、すべてのキーと値を送る
X. Other (please specify)

[Answer]: B

## Q2. 起動時の Hibernate の案内（改行を含む1件のログ）をどう扱うか（4件目）

起動のときに Hibernate が出す案内（11 行ほど）が、改行を含んだ1件のログになり、1行1件の JSON の決まりから外れています。どのロガーが出しているかは、実際のログで確かめてから直します。

A. そのロガーの水準を上げ（WARN など）、案内を出さない。中身は起動の設定の確認で、運用には要らないため
B. 案内は出したまま、ログの出力で改行を含むメッセージを1行に直す（改行を別の記号に置き換える）。すべてのロガーに効く
C. A と B の両方（案内は出さず、ほかのロガーの改行を含むメッセージも1行に直す）
X. Other (please specify)

[Answer]: B

## Q3. アプリのコンテナに見本の対象DB の管理者のパスワードを渡さない方法（6件目）

今は `compose.yaml` の `app` が `.env` 全体を読むため、見本の対象DB の管理者のパスワード（`MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD`）と読み取りのアカウントのパスワードの元（`MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD`）も、アプリの環境変数に入っています。アプリが使うのは `MASTERSMITH_TARGET_DB_*` だけです。

A. 見本の対象DB のための値を、別の環境ファイル（例: `.env.targetdb`、見本は `.env.targetdb.example`）に分ける。アプリは `.env` だけを読み、見本の対象DB のサービスは別のファイルを読む。今 `.env` にこの値を入れている人は移す手順が要る
B. `app` は `.env` を読むのをやめ、アプリが使う変数を `compose.yaml` の `environment:` に1つずつ並べて渡す。`.env` の形は今のまま
C. `.env` は今のまま全体を読むが、`app` にだけ見本の対象DB の値を空で上書きする（`environment:` で空にする）
X. Other (please specify)

[Answer]: A

## Q4. make-you-chic-ui の固定先と、閉じるボタンの名前（7件目）

make-you-chic-ui のリポジトリの `main` の最新は `edb1f94` で、origin にも送られています。確認の表示（Modal）と結果の知らせ（Alert）の閉じるボタンの名前を決めます。

A. 固定先を `edb1f94` に更新する（専用のコミットで前後のハッシュ `5258c8b` → `edb1f94` を記録する）。閉じるボタンの名前は、画面の言語に合わせて日本語「閉じる」・英語「Close」にする
B. A と同じだが、閉じるボタンの名前を、何を閉じるかが分かる形にする（例: 日本語「確認を閉じる」・英語「Close confirmation」）
X. Other (please specify)

[Answer]: A

## Q5. 負荷の試験の手順の「先に1人ずつログインする」をどうするか（2件目・3件目の後）

今は、内部DB に SQL で直接入れた試験用の利用者にはロックの状態の行が無いため、同時のログインを流す前に1人ずつログインさせて行を作る、という手順があります（project.md に学びとして記録済み）。2件目を直すと、この手順が無くても 500 にならなくなります。

A. 手順から外す。2件目の直しを負荷の試験でも確かめるため、行が無い利用者のまま同時のログインを流す
B. 手順は残す（試験の結果を前回と比べやすくするため）。2件目の直しは結合テストだけで確かめる
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

- 1件目（Loki）: すべてのキーと値を属性として送るが、メールアドレス・IP・User-Agent は送り出しのときに伏せる（値を置き換える）。標準出力の JSON のログは今のまま。パスワード・トークン・署名鍵が入らないことも含め、送ったログの中身をテストで確かめる（Q1: B）
- 2件目（同時の初めてのログインで 500）: 行が無い利用者の同時の初めてのログインでも 500 にせず、普段どおり判定する。不具合を再現するテストを直しと同じコミットに入れる（決まっていること）
- 3件目（dslMixed の利用者の重なり）: 場面ごとに重ならない利用者を選ぶ（決まっていること）
- 4件目（Hibernate の案内）: 案内は出したまま、ログの出力で改行を含むメッセージを1行に直す。すべてのロガーに効く（Q2: B）
- 5件目（メモリの上限の既定）: 既定を 2g にし、`compose.yaml`・`docker/perf/compose.yaml`・`.env.example`・README・`docker/check-container-limits.sh` を合わせて直す（決まっていること）
- 6件目（管理者のパスワード）: 見本の対象DB の値を別の環境ファイル（例: `.env.targetdb`、見本は `.env.targetdb.example`）に分け、アプリは `.env` だけを読む。今 `.env` に入れている人のための移す手順を README に書く（Q3: A）
- 7件目（make-you-chic-ui）: 固定先を `5258c8b` → `edb1f94` に専用のコミットで更新し、閉じるボタンの名前は画面の言語に合わせて「閉じる」・「Close」にする。Modal の本文は `aria-describedby` で結ばれる（Q4: A）
- 負荷の試験の手順: 「先に1人ずつログインして行を作る」を外し、行が無い利用者のまま同時のログインを流して2件目の直しを確かめる（Q5: A）

Does this all look correct before I generate the requirements artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
