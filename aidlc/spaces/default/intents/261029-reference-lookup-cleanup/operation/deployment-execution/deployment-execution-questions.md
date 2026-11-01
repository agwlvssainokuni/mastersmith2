# Deployment Execution の質問（Intent 261029-reference-lookup-cleanup）

入れ替えの前の確かめと、アプリを止める承認のための質問です。流れは `operation/deployment-pipeline/cd-config.md` の 3節（順 1〜15）で、承認済みです。

## 決まっていること（質問にしない）

| 項目 | 決まっている内容 | 出典 |
|---|---|---|
| 流れ | `cd-config.md` の順 1〜15 のとおりに行う。アプリが止まるのは順 5〜7（見込み約 2〜3 分）と順 10（約 10 秒） | `cd-config.md` 3節 |
| 配備する版 | `develop` の先頭から作り直した WAR（アプリのコードは `cb23466b`）。内部DB の移行と `.env` の変更は無い | `cd-config.md` 1節 |
| 戻し先 | 入れ替えの前に今のイメージへ `mastersmith:pre-reference-lookup-cleanup`（`e0ede6ddd5c4`）を付ける。戻しはイメージだけ、戻しの練習はしない | `rollback-runbook.md` 2節 |
| 配備の前の k6 | 流さない。判定は Performance Validation に任せる（Q2: B、`project.md` の「試験済みのときだけ省く」との差は `cd-config.md` 3.2 に記録済み） | `deployment-pipeline-questions.md` Q2 |
| 内部DB の複写 | 配備の前と後の2回、`~/.mastersmith-backup/`（700）へ権限 600 で取り、読み取りで開いて数だけを数える（値は表示しない） | `cd-config.md` 4節・4.1、Q3: A |
| 数える道具 | リポジトリの外の `~/.mastersmith-tools/count-db3.sh` と `CountRefLookup.java`（この段で用意。列の名前は Flyway の移行 V2〜V13 で確かめた。メールアドレスは SQL の文に埋めず引数で渡し、SQL の誤りは例外のクラスの名前と SQLState だけを出す）。前の Intent の複写で、前の記録と同じ数（`LOGIN_SUCCEEDED` 1・`LOGGED_OUT` 1 など）が出ることを確かめた | 環境の段の申し送り H3 |
| スモークテスト | `deployment-strategy.md` の S1〜S9。パスワードが要る操作と画面の操作（S2〜S8）はあなたにお願いし、AI はログ・監査・見本の PG の件数で裏付ける | `deployment-strategy.md` 3節、Q1: A |
| 試験用のタグ | `mastersmith:reference-lookup-cleanup`（`eab1676f26de`）を、使っているコンテナが 0 件であることを確かめてから順 12 で消す | Q4: A |
| 記録のコミットと `main` | 配備の記録をコミットしてから（順 11）、`main` を fast-forward し（順 13）、取り込みと push の結果は `main` の後の `develop` だけのコミットで追記する（順 14）。どのコミットも行う前に提案して承認を得る。タグは付けない | `cd-config.md` 3節、前の Intent と同じ |
| `main` の前の CI の確かめ | `d797ef74` の後のすべてのコミットの CI を試行ごとに確かめ、再実行で通ったものは1回目の失敗とそのテストを記録する。記録だけのコミットの CI も確かめる | `cd-config.md` 順 13 |
| プッシュ | `origin` へのプッシュはあなたが行う | `team.md` |

## 入れ替えの前の確かめ（この時点の結果）

| 確かめ | 結果 |
|---|---|
| アプリのソースに未コミットの変更（`aidlc/` の下を除く） | 0 件 |
| `develop` の先頭 | `5458e3eb`（`main` の `d797ef74` から 68 コミット先）。`origin/develop` より 4 コミット先（`cba9d95f`〜`5458e3eb`、どれも `aidlc/` の下の記録だけ） |
| `23519159` の CI（run 41273010129） | **実行中**（この記録の時点で約 20 分。これまでの検査の job は 28〜36 分） |
| 前の CI の気づき（順 13 で記録する） | `e1e028d1` は2回の試行とも failure（固定先の更新 `cb23466b` で直り、`cb23466b` は1回目で success）。`a75baec4`・`cf887ad8` は2回目の試行で success |
| 電源 | **バッテリー駆動（77%）**。WAR の作り直しと入れ替えの前に電源をつないでください（`caffeinate -dims` で包みますが、熱による眠りは止められないため） |
| 2つの新しい設定の点検の WARN の見分け方 | 下の「気づき」のとおり、ログに出る項目の名前は `mastersmith.target-db.query-timeout.reference`・`mastersmith.target-db.reference-read-total` |

### 気づき（承認済みの記録との差）

- `deployment-strategy.md` の S1 は「点検の WARN のうち項目名 `QUERY_TIMEOUT_REFERENCE`・`REFERENCE_READ_TOTAL` を含むものが 0 件」と書いていますが、実際の WARN（`TargetDataSourceConfig`、`items` のキー）に出るのは Spring の項目の名前 `mastersmith.target-db.query-timeout.reference`・`mastersmith.target-db.reference-read-total` です（`backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDbSettings.java` の 70・73 行目）。書いた文字列で探すと、WARN が出ていても 0 件になります。
- この段では、承認済みの記録は書き換えず、**`TargetDataSourceConfig` の WARN が 0 件**（点検の WARN そのものが出ていない）ことと、2つの Spring の項目の名前で探して 0 件であることの両方で確かめます。差は `deployment-log.md` に記録します。今動いている版のログでも `TargetDataSourceConfig` の WARN は 0 件でした。

## Q1. 初期管理者の状態（救済の見込み）

アプリの起動時に、初期管理者の利用者が停止中・管理者の印なし・パスワードが `.env` の値と違うと、自動で `.env` の値に戻す救済が働きます。入れ替えの起動でも働きえます。前の配備（2026-10-29）の後に、初期管理者について次のことをしましたか？

推奨: A（もし当てはまれば）。理由: 前の Intent の配備の後の複写では、初期管理者は停止していない・印あり・救済のログ 0 件でした。順 5 の複写の数え（`init_admin_suspended`・`init_admin_flag`）でも裏付けます。パスワードの一致だけは複写から数えられないため、あなたに伺います。

A. していない（パスワードを画面で変えていない・止めていない・管理者の印を外していない）。救済は働かない見込み
B. 初期管理者のパスワードを画面で変えた。入れ替えの起動で `.env` の値に戻ることを承知で進める
C. 初期管理者を止めた、または管理者の印を外した。入れ替えの前に `.env` を先に替える必要があるため、ここで止めて相談する
X. Other (please specify)

[Answer]: A

## Q2. アプリを止めて入れ替えてよいか・いつ始めるか

順 5〜7 でアプリが約 2〜3 分止まります（止めた後に複写と数えを行い、そのまま入れ替えます）。止める前の順 1〜4（関門・WAR の作り直し・戻し先のタグ・救済の確かめ）はアプリを止めずに行えます。`23519159` の CI は `aidlc/` の記録だけのコミットですが、新しい脆弱性の公表は記録だけのコミットの CI でも落ちるため（前の Intent の学び）、結果は配備の判断の材料になります。

推奨: A。理由: 止めない準備（順 1〜4、WAR の作り直しは数分）を CI と並べて進め、止める前に CI の結論を確かめれば、待ち時間を増やさずに「CI が落ちたのに止めてしまった」を避けられます。CI が失敗したときは止めずにあなたに諮ります。

A. 順 1〜4 を今から行い、`23519159` の CI が success になってから順 5〜7（停止と入れ替え）を続けて行う。CI が失敗したら止めずに相談する
B. CI を待たずに、順 1〜7 を今から続けて行う
C. 順 1〜4 だけ行い、止める時刻は改めて知らせる
X. Other (please specify)

[Answer]: A

## Q3. スモークテスト（S2〜S8）の進め方と知らせ方

S2〜S8 はログインと画面の操作のため、あなたにお願いします。★の操作（S3〜S8）は監査に残り、期待の件数は `deployment-strategy.md` 3.2（`ROLE_CREATED` 1・`ROLE_PERMISSION_CHANGED` 1・`ROLE_ASSIGNED` 1・`WORK_ROLE_SWITCHED` 2・`BUSINESS_ROW_CREATED`・`UPDATED`・`DELETED` 各 1・`ROLE_UNASSIGNED` 1・`ROLE_DELETED` 1、ログインした回数の `LOGIN_SUCCEEDED`）です。保存の押し直し・ログインの誤り・ログアウトなど予定に無い操作をしたときは、期待の件数を合わせるために教えてください（前の Intent の学び）。

推奨: A。理由: S2 は 2つの新しい設定の誤りで対象DB 全体が使えなくなっていないかを見る必須の確かめ（U1 の R-01）で、ここで止まるなら S3 以降のロールの作成などで監査の行を増やす前に戻すかを決めたいためです。S3〜S8 は続けて行う方が切り替えの前後がそろい、手数も少なく済みます。

A. 2回に分ける: まず S2 だけ行って結果を知らせ、AI がログを確かめた後に S3〜S8 を続けて行い、終わったら「済んだ」と予定に無い操作の有無を知らせる
B. S2〜S8 を続けて行い、終わったら結果と予定に無い操作の有無をまとめて知らせる
C. 1つずつ（S2・S3・…・S8 の各々の後に）知らせ、AI がその都度ログを確かめる
X. Other (please specify)

[Answer]: A

## Q4. `main` への取り込みをこの段の終わりに行うか

`cd-config.md` の順 13 は、この段の中で `main` を fast-forward する流れです。一方、配備の前の k6 は流さず判定を Performance Validation（この後の段）に任せています（Q2: B）。

推奨: A。理由: 承認済みの流れ（順 11〜14）どおりで、`main` は「配備した版」の置き場として配備と同時にそろえるのがこれまでの形です（前の Intent も同じ）。Performance Validation で目標を外れても、これまでどおり記録して後の Intent で扱う形になり、`main` を戻す必要は生じない見込みです。取り込みの前の CI の確かめで失敗が見つかったときは、取り込みを止めて相談します（前の Intent の vite の例と同じ）。

A. この段の終わりに、CI を試行ごとに確かめてから `main` を fast-forward する（順 11〜14 のとおり）
B. この段では `main` を取り込まず、Performance Validation の結果を見てから取り込む（承認済みの流れとの差を記録する）
X. Other (please specify)

[Answer]: A

## Q5. 未プッシュの記録のコミットのプッシュ

`develop` には `origin/develop` より先の記録だけのコミットが 4 つ（`cba9d95f`〜`5458e3eb`）あります。順 13 の前に、これらを含む先頭の CI を確かめる必要がありますが、CI はプッシュの後にしか動きません。

推奨: A。理由: 今プッシュしていただくと、その CI（約 30 分）が配備やスモークテストと並んで進み、順 13 の前に結果がそろいます。最後にまとめてプッシュすると、`main` の取り込みの前に CI を待つ時間が加わります（`23519159` の CI が終わってから押していただくと、run が重ならず待ちが短くなります）。

A. `23519159` の CI が終わった後に、`develop` をプッシュする（あなたが行う）
B. 今はプッシュしない。順 11 の配備の記録のコミットの後にまとめてプッシュし、その CI を待ってから順 13 へ進む
X. Other (please specify)

[Answer]: A
