# Practices Discovery の質問（Intent 261114-internal-db-postgresql）

## 決まっていること（質問にしない）

`team.md`・`project.md` の5つの節（Way of Working・Walking Skeleton・Testing Posture・Deployment・Code Style）は、今の内容をそのまま引き継ぐ。統合先は `develop`、テストの進め方は test-after、統合の前は `./gradlew verifyQuick`、Build and Test とリリースの前は全件の `./gradlew verify`、E2E は Intent の最後とリリースの前、AI はプッシュしない、などは変えない。下書きは `team-practices.md` にある。

以下は、内部DB に PostgreSQL を足すことで、今の決まりが決めていない点、または今の文のままでは合わなくなる点だけを尋ねる。機能の中身（どの SQL をどう書き換えるか、H2 から PostgreSQL へデータを移すか、既定の内部DB を変えるか）は、要件定義・設計の段で尋ねる。

## Q1. [品質] 内部DB を PostgreSQL で動かす結合テストの範囲と、流す場面

今の決まりでは、内部DB のテストは組み込みの H2 で行います（team.md の Testing Posture）。結合テスト 278 本のうち 275 本が H2 を起動していて、CI の検査の時間の中央値は約 21 分半、上限の目安は約 42 分です（全件を2回流すと目安を超える見込みです。時間は測っていません）。

推奨: A。H2 と PostgreSQL の差は、移行・一意の違反の判定・排他と待ち・ネイティブの SQL・時刻など一部の経路に集まっているため（下調べの記録）、そこを確かめるテストを足せば、時間を大きく増やさずに守れます。

A. H2 での全件は今のまま残し、PostgreSQL に固有の振る舞いを確かめる結合テストを別に置く。全件の `verify` と CI で毎回流し、`verifyQuick` では `-PitTests` で絞ったときだけ流す。team.md の文を「H2 を基本とし、PostgreSQL に固有の結合テストは版を固定したコンテナで行う」に書き換える
B. 275 本すべてを H2 と PostgreSQL の両方で流す（確かめる範囲は最大だが、時間がほぼ倍）
C. PostgreSQL の分を `verify` と CI の外の手動のタスクにする（速いが、壊れても気づくのが遅れる）
X. Other (please specify)

[Answer]:

## Q2. [品質] コンテナの実行環境（colima）が無いときに飛ばしてよい範囲

今の決まりでは、colima が無いとき警告を出して飛ばしてよいのは「コンテナで動かす対象DB のテストだけ」です（team.md の Way of Working）。Q1 で内部DB の PostgreSQL のテストもコンテナを使うと、この文に当たりません。

推奨: A。今の守り（飛ばした状態では統合しない・CI では必ず流す）をそのまま広げられ、手元と CI で同じ判断になるためです。

A. 内部DB の PostgreSQL のテストも対象DB と同じ扱いにする（警告を出して飛ばす。飛ばした状態では統合しない。CI では必ず流す）
B. 内部DB の分は飛ばさずに失敗にする（固いが、colima を起動していない途中の実行がすべて止まる）
X. Other (please specify)

[Answer]:

## Q3. [リリース] 内部DB の PostgreSQL のコンテナのイメージの版とダイジェスト

内部DB 用のイメージの固定先は、まだどこにもありません。対象DB の PostgreSQL は `postgres:18.6` を、ダイジェストで3か所（`compose.yaml`・`docker/perf/compose.yaml`・テストの `TargetDbImages`）に固定しています。対象DB の版は「verify を通った最新」にする決まりで、固定先の一覧は `.github/dependabot.yml` に書く決まりです。

推奨: A。上げる知らせと手で揃える先が1つの版にまとまり、上げ忘れが起きにくいためです。

A. 対象DB の PostgreSQL と同じ版とダイジェストを使い、上げるときは一緒に上げる。内部DB 用の置き場（テストの定数・compose）を `dependabot.yml` の固定先の一覧に足す
B. 内部DB は別の版（長く支援される版など）で別に固定する（上げ方を分けられるが、固定先と知らせが倍になる）
X. Other (please specify)

[Answer]:

## Q4. [リリース] 手元の配備で使う内部DB と、配備の段での確かめ方

今の手元の配備（開発者の PC 上のコンテナ）は H2 で動いていて、配備の前の内部DB の複写・数え・戻しの手順も H2 が前提です（ボリュームの `tar`、H2 の道具）。既定の内部DB を変えるかどうかは要件の段で決めますが、配備の段の進め方としてどうするかをお尋ねします。

推奨: A。今の配備とデータを動かさずに、PostgreSQL で起動・動作することを配備の段でも確かめられるためです。

A. 手元の配備はこれまでどおり H2 で行う。PostgreSQL は compose の profile などで選べる形にし、Deployment Execution で使い捨ての PostgreSQL の内部DB を相手に、起動・ヘルスチェック・スモークテストを確かめる。PostgreSQL のバックアップと戻し（`pg_dump` など）の手順を文書に足し、そこで1回練習する
B. 今回から手元の配備を PostgreSQL に切り替える（H2 からのデータの移し替えが要る）
C. H2 と PostgreSQL の両方を常に配備する（資源と手順が倍）
X. Other (please specify)

[Answer]:

## Q5. [品質] Intent の最後の E2E を PostgreSQL の内部DB でも流すか

E2E は Intent の最後とリリースの前に全体を1回流す決まりで、今は H2 のファイルを内部DB にして動いています。この Intent では Performance Validation の段を行わないため、負荷の試験（k6）の要否は NFR 要件の段で決めます。

推奨: A。画面から通して PostgreSQL で動くことを確かめられるのは E2E だけのためです。

A. この Intent の Build and Test では、E2E の全件を H2 と PostgreSQL の内部DB で1回ずつ流す（リリースの前は今の決まりのまま）
B. E2E は H2 だけで流す（PostgreSQL での画面の通しは確かめない）
C. Build and Test に加えて、リリースの前の E2E も毎回両方で流す（リリースの手間が増える）
X. Other (please specify)

[Answer]:

## Q6. [セキュリティ] 内部DB についての固い制約を足すか（select all that apply）

対象DB には「実際の DB で結合テストを行い H2 で代用しない」「接続情報をログ・応答に含めない」という固い制約がありますが、内部DB には同じものがありません。内部DB の接続は `MASTERSMITH_DB_URL`・`MASTERSMITH_DB_USERNAME`・`MASTERSMITH_DB_PASSWORD` で受け取っています。

推奨: A と B の両方。対象DB に置いた守りと同じものを内部DB にもそろえるためです。

A. ALWAYS 内部DB を PostgreSQL で動かす経路の結合テストは、版を固定したイメージのコンテナで起動した実際の PostgreSQL で行い、H2 で代用しない
B. NEVER 内部DB の接続情報（パスワード・資格情報を含む JDBC の URL）をログ・監査ログ・エラー応答に含めず、環境変数（.env）以外から受け取らない
C. どちらも足さない（進め方の文（Q1）にとどめる）
X. Other (please specify)

[Answer]:
