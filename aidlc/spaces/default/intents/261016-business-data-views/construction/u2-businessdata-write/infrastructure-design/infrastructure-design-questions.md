# Infrastructure Design の質問 — U2 u2-businessdata-write

配備先は、開発者の PC の上のコンテナだけです。読み替え方は U1 と同じです。

## 前の段から持ち越した論点

**NFR 設計の決定**
- F1: A（perf の MySQL のメモリ）
- F2: A（PG の TEMPORARY を取り上げる・結合テストで確かめる・README）

**レビューの指摘**
- 表ごとの GRANT（R-06、NFR4.16）
- 書き込みの接続の資格情報・プール・時間の上限・初期化

**U1 の基盤の設計のレビューの指摘（U2 では先に避ける）**
- R-01: 通信の待ちの計算
- R-02: k6 の setup の前提の担当

**U3 と関わる点**
- E2E 180 で使う書き込みのアカウント

## この PC の実行環境（読み取りで確かめた結果）

- colima と、動いているコンテナは U1 と同じ（約 5,910 MiB）。
- 見本の PG のボリュームは 2026-09-25 に作ったもの。初期化の台本はボリュームが空のときだけ動く。そのため、台本を足しても、配備した見本の PG には書き込みのアカウントができない（→ Q4）。
- `.env.targetdb` の項目は、管理者と読み取りの2つだけ。
- perf の compose は、見本と同じ初期化の台本を使う。`perf/dsl-timing.sh` は、一時の環境ファイルに読み取りのパスワードだけを書いている（→ Q2）。

## 決まっていること（質問にしない）

**接続**
- アプリのコンテナ、内部DB のプール（30）、読み取りのプール（5）は変えない。
- 書き込みの接続は、名前つきの部品として足す。
  - プールの名前は `mastersmith-target-db-write`。
  - READ COMMITTED、上限 5、minIdle 0、借りる待ち 3s、起動時は接続しない。
  - 接続先は、読み取りの設定をそのまま使う。
- 通信の待ちは「書き込みの時間の上限＋5 秒」で計算する（U1 の R-01 を先に避ける）。
  - 既定の値は 10 秒のまま。
  - 書き込みのプールは専用のため、計算に入るのは書き込みの上限だけ。
- ドライバーの設定
  - LOAD DATA LOCAL を許さない。
  - MySQL は `useAffectedRows=false`。
  - MySQL・MariaDB は `connectionInitSql` で `time_zone` と `sql_mode` を付ける（定数）。
- 書き込みの資格情報は `.env` だけから受け取る。
  - 片方だけが設定されているときは、項目の名前だけを WARN で出し、書き込みは 409 `BUSINESS_WRITE_NOT_CONFIGURED` にする。起動と読み取りは続ける。

**負荷の試験**
- perf の対象DB の値は U1 と同じ。プールを 10 に上げる案は採らない（F1: A）。
- 大きな本文を同時に書き込むと対象DB のメモリが尽きうることを、既知の制約として README に書く。
- VM と lgtm の扱いは U1 の Q3: A のまま。

**監視**
- 警報は足さない。`ms-pool-pending` などの既存の警報は、プールで絞っていないため書き込みも拾う。Observability Setup で実測する。
- ダッシュボードの区画は Observability Setup が持つ。B4 は `docker/monitoring/` に触れない。

**CI と E2E**
- `ci.yml` は変えない。
- B4 は `businessdata` の本体に触れるため、統合の前に E2E の全体を流す。U2 は E2E のファイルを足さない。

**内部DB**
- 内部DB に V13（監査の3列、空を許す列を足すだけ）を足す。前進だけで、後方互換を保つ。
- 戻すのはイメージだけ（V13 は残る）。

**README**
- 次の3か所の記述は事実と合わなくなるため直す。
  - 「アプリは接続を読み取り専用にし、書き込み・DDL を発行するコードを持たない」
  - 「見本の DB が持つのは見本のスキーマと読み取りのアカウントだけ」
  - `.env.example` の「アカウントは読み取りの権限だけ」
- 次の2つを足す。
  - 413 とエスケープの注意
  - 対象DB のメモリの注意

**負荷の試験の前提と書き手**
- k6 の場面の setup の前提（DSL の適用・ロール・割り当て・作業ロール・トークン・種への GRANT）は、B4 が用意する（U1 の R-02 を先に避ける）。

## Q1. 書き込みのアカウントと環境変数の名前

A. アカウントは `mastersmith_writer` にする（推奨）。
- アプリの環境変数は次の4つ。
  - `MASTERSMITH_TARGET_DB_WRITE_USERNAME`
  - `MASTERSMITH_TARGET_DB_WRITE_PASSWORD`
  - `MASTERSMITH_TARGET_DB_WRITE_POOL_MAXIMUM_SIZE`（既定 5）
  - `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_WRITE`（既定 5s）
- 使っていない接続を閉じるまでの時間は、読み取りと共用する。
- 見本の DB のパスワードは `.env.targetdb` の `MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD` にする。

B. すべてを `WRITER_` でそろえる（時間の上限は、既存の `QUERY_TIMEOUT_*` の並びから外れる）。

C. A の形で、使っていない接続を閉じるまでの時間も、書き込み専用に足す。

X. Other (please specify)

[Answer]: A

## Q2. 見本の初期化への足し方

A. 3種類に `docker/targetdb/<種類>/03-writer-account.sh` を足す（推奨）。
- パスワードが空なら、読み取りの台本と同じく止める（黙って飛ばさない）。
- PG の `REVOKE TEMPORARY` は、この台本の中で行う。
- 台本は何度流しても同じ結果になる形にし、既存のボリュームへの手順（Q4）にも同じ台本を使う。
- `perf/dsl-timing.sh` と README の一時の環境ファイルに、書き込みのパスワードの生成を足す。

B. A と同じ台本を足すが、パスワードが空なら書き込みのアカウントだけを飛ばす（PG の REVOKE は必ず行う。アカウントが無いことには、後の 503 で気づく）。

C. 今の `02-reader-account.sh` を広げ、1つの台本にまとめる。

X. Other (please specify)

[Answer]: A

## Q3. 付与の範囲（R-06・NFR4.16）

捨ての試しで、MySQL・MariaDB でも表ごとの GRANT が効くと分かりました。

A. 見本は、スキーマ全体に DML を付与する（推奨）。
- PG は、後から作る表と sequence にも付ける。
- 残る広さ（DSL に無い表も DB の上では書ける。アプリはサーバー側で DSL の表だけに絞る）を、README と文書に書く。
- 利用者の DB で表ごとに絞る書き方（参照先の表の SELECT も要る）も、README に書く。

B. 見本も、表ごとに付与する（DSL や perf・E2E の表が変わるたびに足す必要があり、漏れると 503 になる）。

C. 見本は A とし、利用者の DB には、README で表ごとの付与を推奨として書く。

X. Other (please specify)

[Answer]: A

## Q4. 配備した見本の PG への適用と戻し

A. ボリュームを残したまま、既存のボリュームへの手順で足す（推奨）。この手順は BR13.4 で README に書くもので、今回が初めての実地の確かめになる。
1. `.env.targetdb` に WRITER の行を足す。
2. コンテナだけを作り直す。
3. `03-writer-account.sh` を流す（値は表示しない）。
4. ロール・付与があること、TEMP が無いことを件数で確かめる。
5. `.env` をリポジトリの外へ複写してから、書き込みの2行を足し、アプリを作り直す。

戻すときはイメージだけを戻す。消す手順（`DROP ROLE`）も README に置く。

B. ボリュームを消して作り直し、初期化の台本をすべて走らせる（初期化の道を確かめられる。見本の行は失われる）。

C. 今回の配備では書き込みの資格情報を入れず、書き込みは 409 のまま配備する（スモークテストで書き込みを確かめられない）。

X. Other (please specify)

[Answer]: A

## Q5. E2E の書き込みのアカウント（U3 の 180 と共有）

A. E2E が実行ごとに、管理者の資格情報で一時のロールを作り、終わったら消す（推奨）。
- 一時のロールは書き込み用で、読み取りも同じ形にする。
- パスワードは乱数にする。
- 付与は、その実行の専用のスキーマの DML だけにする。
- 資格情報はプロセスの環境変数で渡し、`SECRET_ENV_NAMES` に足す。
- 始めに、前の実行の残りを消す。
- 見本の `mastersmith_writer` があるかどうかに頼らない（team.md の「前提を自分で作る」）。

B. 見本の `mastersmith_writer` を使い、E2E の setup で専用のスキーマへの付与を足す。

C. U3 の基盤の設計に任せる（U2 は、DDL を与えないことだけを条件にする）。

X. Other (please specify)

[Answer]: A

## Q6. 見本の初期化の台本の確かめ方（F2: A の結合テスト）

A. 3種類の対象DB を、実際の `docker/targetdb/<種類>/` の台本を使ったコンテナで起動して確かめる（推奨）。
- 書き込みのアカウントで、DML は通り、DDL・PG の一時の表は拒まれる。
- 読み取りのアカウントでは書けない。
- verify にコンテナが3つ増える（約 1 分の見込み。B4 の後に実測する）。

B. テストの手伝いの部品で、同じ付与をクラスごとのスキーマに作って確かめる（見本の台本そのものは確かめない）。

C. PG だけを A の形にし、MySQL・MariaDB は B の形にする。

X. Other (please specify)

[Answer]: A

## Q7. perf の MySQL 1792m（と MariaDB 1g）の裏付け

U1 の 1792m は、「バッファープール以外の量（約 614 MiB）は、プールを大きくしても増えない」という未検証の見立ての上にあります。project.md の学び（大きな入力のメモリの見積もりは、設計の段で試しで裏付ける）に当たります。

A. この段で、短い捨ての試しを行ってから文書を書く（推奨）。
- 768M/1792m の MySQL と 512M/1g の MariaDB を、1つずつ立てる。
- 4 MiB の INSERT を5本同時に6回流し、`memory.peak` と OOMKilled を見る。
- 約 15 分。配備したアプリは止めない。
- 超えたら、U1 の承認の場で値を直す。

B. 試さず、Performance Validation で測る（止まったら Not Met として、その段で諮る）。

X. Other (please specify)

[Answer]: A

## 捨ての試しの結果（Q7: A、約 18 分）

試しの条件は次のとおりです。
- MySQL: プール 768M・上限 1792m
- MariaDB: プール 512M・上限 1g
- 書き込み: 4 MiB の INSERT を5本ずつ同時に流す。参考に 10 本でも流す。
- バッファープールは、表で 95〜98% まで埋めた状態も試した。

| 場合 | MySQL の memory.peak | MySQL の anon | MariaDB の memory.peak | MariaDB の anon |
|---|---|---|---|---|
| 5本 × 6回 | 1,019 MiB（57%） | 783 MiB | 404 MiB（40%） | 387 MiB |
| 埋めた状態で 5本 × 6回 | 1,792 MiB（上限） | 1,581 MiB | 844 MiB（82%） | 835 MiB |
| 埋めた状態で 10本 × 2回 | 1,792 MiB（上限） | 1,607 MiB（90%） | 848 MiB（83%） | 839 MiB |

- どちらも、OOMKilled は false で、`oom_kill` は 0 だった。
- MySQL は、上限に 4,326 回当たった。そのたびに、捨てられるページキャッシュを捨てて凌いだ。MariaDB は 0 回だった。
- U1 の見立て「プール以外の量は約 614 MiB で、プールを大きくしても増えない」は崩れた。実測は約 840 MiB だった。
- 対象DB の `memory.peak` は、捨てられるページキャッシュを含むため、問題が無くても上限に張りつく。

## F1. perf の MySQL のメモリの上限

A. MySQL の上限を 2304m に上げる。プールは 768M のまま（推奨）。
- anon の最大は、上限の約 70% になる。
- VM の判定: perf のアプリ＋MySQL＋見本の PG・Mailpit で 5,120 MiB。約 5,910 MiB に収まる。
- U1 の基盤の設計の値と根拠（2.1・2.3）は、U1 の承認の場で、試しの値にそろえる。

B. 2048m にする。anon の最大は約 78%、VM の判定は 4,864 MiB。

C. 1792m のままにする。上限に張りつくことを既知の制約にする。

X. Other (please specify)

[Answer]: A

## F2. 対象DB のメモリの合否の判断

A. 対象DB の合否は「OOMKilled が false、かつ `oom_kill` が 0」で判断する（推奨）。
- 記録するもの: `memory.peak`・`anon` の最大・上限に当たった回数・バッファープールの使用。
- アプリの `memory.peak` の線（2g の 90% 未満）は、今のまま変えない。

B. 対象DB の `anon` の最大にも線（例: 上限の 80% 未満）を置いて判断する。

X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

**infrastructure-specification**
- アプリ・内部DB のプール・読み取りのプールは変えない。新しいコンテナは無い。
- 書き込みの接続は、名前つきの部品 `mastersmith-target-db-write` で受ける。
  - READ COMMITTED・上限 5・minIdle 0・借りる待ち 3s・起動時は接続しない。
  - 通信の待ちは「書き込みの上限＋5 秒」で計算する。
  - MySQL・MariaDB は `connectionInitSql` を流す。LOAD DATA LOCAL は許さない。
- 名前（Q1: A）
  - アカウントは `mastersmith_writer`。
  - 環境変数は `MASTERSMITH_TARGET_DB_WRITE_USERNAME`・`_PASSWORD`・`_POOL_MAXIMUM_SIZE`、`MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_WRITE`。
  - 見本の DB は `MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD`。
  - `.env.example`・`.env.targetdb.example` にコメントの行と項目を足す。
- 見本の初期化（Q2: A）
  - 3種類に `03-writer-account.sh` を足す。パスワードが空なら止める。何度流しても同じ結果になる形にする。
  - PG の `REVOKE TEMPORARY` も、この台本で行う。
  - `perf/dsl-timing.sh` と README の一時の環境ファイルに、書き込みのパスワードの生成を足す。
- 付与（Q3: A）
  - 見本はスキーマ全体に DML を付与する。PG は sequence と後から作る表にも付ける。
  - 広さと、表ごとに絞る書き方を README に書く。
- 配備した見本の PG（Q4: A）
  - ボリュームを残し、台本を手で流して足す。件数で確かめる。
  - `.env` は、外へ複写してから2行を足す。
  - 戻しはイメージだけ。`DROP ROLE` の手順を README に置く。
- 内部DB の V13 は前進だけ（後方互換）で、戻しはイメージだけ。
- README の3か所を直し、413 とエスケープの注意・対象DB のメモリの注意を足す。
- perf の MySQL は、上限 2304m・プール 768M にする（F1: A）。MariaDB は 512M/1g、PG は 256MB/768m のまま。
  - U1 の文書の値と根拠（2.1・2.3）は、U1 の承認の場で、試しの値にそろえる。

**monitoring-design**
- 書き込みの3本の p95・409 と 503 の数・書き込みのプールの値を見る。
- 警報は足さない。既存の警報が拾うかは、Observability Setup で実測する。先に1回書き込んでから式を流す。
- SLO は、k6 の4場面の p95 < 1000 ms・`checks` 1・待ちの時間切れ 0・503 が 0。
  - `bizCreateLarge` は、アプリの `memory.peak` < 2g の 90%。
- 対象DB のメモリの合否は「OOMKilled が false、かつ `oom_kill` が 0」で判断する。`memory.peak`・`anon` の最大・上限に当たった回数・プールの使用は記録だけにする（F2: A）。

**cicd-pipeline**
- CI は変えない。
- verify に足すもの
  - 書き込みの IT（3種類）
  - 見本の初期化の台本を、実際の台本で起動したコンテナで確かめる IT（3種類。DML は通り、DDL と PG の一時の表は拒まれる。Q6: A）
  - 名前つきの管理者が内部DB を退けない IT
  - 接続の数の IT・漏えいの IT・応答しない対象DB の IT
- SpotBugs の除外を広げるときは承認を得る。
- B4 の統合の前に E2E の全体を流す。
- k6 の5場面と setup の前提（DSL・ロール・割り当て・作業ロール・トークン・種への GRANT）は B4 が書く。流すのは Performance Validation。
- E2E の書き込みのアカウント（Q5: A）
  - 実行ごとに、管理者の資格情報で一時のロールを作る。パスワードは乱数、権限は専用のスキーマの DML だけ。
  - 終わったら消す。
  - プロセスの環境変数で渡し、`SECRET_ENV_NAMES` に足す。
  - この答えは U3 に渡す。
- 統合は squash。push は依頼者が行う。

**traceability**
- U2 の NFR の枝番に行を立てる。OK には基盤の作業を必ず1つ当てる。テストだけが根拠のものは N/A とし、置き場を書く。

Does this all look correct before I generate the infrastructure design?

- Looks correct
- Request changes

[Answer]: Looks correct
