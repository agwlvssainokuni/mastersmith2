# NFR 設計の質問 — U3 group

対象の単位: U3 group（kind: service）。作る成果物は `performance-design.md`・`security-design.md`・`scalability-design.md`・`reliability-design.md`・`observability-design.md`・`logical-components.md`・`traceability.json` の7つです。

読んだもの:

- この単位の承認済みの NFR 要件 `construction/group/nfr-requirements/`（7つの成果物と、承認の場の直し R-01・R-02、承認の場の決定「group の R-02（20 VU の場面の期待）は NFR 設計の始めに決めて記録する」）と、NFR 要件の読み直しの記録（`.aidlc-reviews/nfr-requirements/units/group/0921d0fe911cb972/1.json` の R-02〜R-08）
- この単位の承認済みの機能設計 `construction/group/functional-design/`（`functional-spec.md`・`rules.md`・`entities.md`）
- 契約 `inception/contract-design/contract-summary.md`（C4・C6・C10）、部品の一覧 `inception/domain-design/components.md`（GroupManagement）
- U4 role の NFR 要件の `scalability-requirements.md` の NFR2.7（上限に届いた証拠の形。読み取りだけ）
- コード: `common/observability/TraceAspect.java`（対象の層と、例外の文を TRACE に出す設定 `exception-message`）、`backend/src/main/resources/application.yaml`（`MASTERSMITH_TRACE_EXCEPTION_MESSAGE` の既定は `$[exception]` を含む）、`user/repository/UserRowLockRepository.java`（排他の待ちの上限 3000 ms をヒントで渡し、例外を中で受ける）、`invitation/lock/InvitationLockQueriesImpl.java`（TraceAspect の対象の外の用途名の下位パッケージで排他を取り、`RowLockFailures` で区分する）

質問は 3 問です。

---

## 決まっていること

### 承認済みの要件から（設計で形にするだけ）

- 7つの口すべてに p95 1 秒。k6 は操作ごとに1回の繰り返しに要求1つの8場面で、判定は場面ごとの `iteration_duration` の p95 と checks の率 1。トークンは `setup()` で取り、場面は 3 分まで（NFR2.5）。
- 接続は書き込みで最大2本、読み取りで最大1本。決定的な確かめは結合テスト `GroupConnectionUsageIT`（NFR2.7）。
- 同時の重なりはグループの行の排他を順番の決め手にし、行の排他・一意の鍵・主キーの待ちの上限切れは `GROUP_BUSY`（NFR3.3）。
- 違反の後は1つ目のトランザクションを巻き戻して終え、続けて2つ目の `TransactionTemplate`（書き込みなし）で失敗の出来事だけを出す（NFR3.4）。
- 監査は成功と業務の拒否を残し、`detail` は Java の文字列の長さで 16,384 まで（NFR5.1・NFR5.5）。
- 指標は既存の `http.server.requests` だけ。WARN は `GROUP_BUSY` の1回だけ（NFR5.2・NFR5.3）。
- 新しい依存は足さない。移行の番号はコード生成の時点の次の空き番号（NFR3.6）。

### 排他・待ちの作り（既存の形を使う）

- 排他の待ちの上限は既存と同じ 3000 ms で、`jakarta.persistence.lock.timeout` のヒントで問い合わせごとに渡す（`UserRowLockRepository` と同じ）。待ちの上限切れの区分けは既存の `common.persistence.RowLockFailures`（`isLockFailure`・`warn`）と `RowLockAttempt`（取れた・取れない）を使う。
- 待ち合わせの口 `GroupBarrier`（本番は何もしない `NoOpGroupBarrier`、テストは `@Primary`）は、グループの行の排他を取った直後に呼ぶ。

### 漏えい（TraceAspect と例外の文）

- TraceAspect は `web`・`service`・`domain`・`repository` の層の方法の引数・戻り値に加えて、投げられた例外の文（既定の `exception-message` が `$[exception]` を含む）を TRACE に出す。
- 一意・主キー・外部キーの違反や排他の上限切れの例外の文（H2 の例外の連なり）には、行の値（グループの名前・ID）が入りうる（`project.md` の学び）。そのため、違反・上限切れを起こしうる書き込み・排他は、例外が TraceAspect の対象の方法の外へ出ない形で受け、区分に変えて返す（置き場は Q3）。
- 書き込みの問い合わせ（JPQL の更新・削除、`@Modifying`）も同じ扱いにする（`project.md` の学び）。
- メンバーの氏名・メールアドレスは、`user.service` のまとめて読む口から web の DTO を作るまで、伏せ字の型のまま渡す（NFR1.6）。

### NFR 要件の読み直しで直していない指摘の扱い

承認済みの要件の文書は書き換えず、この段の設計の成果物に書く。

- **R-03**（B3 での 403 の利用者）: B3 では管理者の印を持たない利用者で確かめ、B5 で「全スキーマを FULL にしたロールを作業ロールにする、管理者の印だけを欠く利用者」を足す。`team.md` の「要る権限だけを欠く利用者」との B3〜B4 の間の差として `security-design.md` に書き、U4 への引き継ぎにも残す。
- **R-04**（NFR1.10 の受け入れた窓）: 設計では検証の対象ではない「受け入れた制約」の節に置き、要件の行としては扱わない。
- **R-05**（`groupIdsOfUser` の時間）: 時間の目安は、U4 の NFR2.3 の `snapshotFor` 1回 300 ミリ秒（`groupIdsOfUser` の時間を含む）で確かめる。group の側は問い合わせの数（1回）と、利用者の ID の索引だけを持つ。
- **R-06**（警報の名指し）: Observability Setup で既存の式を名指しする。この段では `observability-design.md` に引き継ぎとして書く。
- **R-07**（同じ名前の同時の作成・変更の 409 の code）: Q2 の捨ての試しの結果で決め、この段の承認で確定する（持ち主は NFR Design）。
- **R-08**（場面の書き方）: k6 の繰り返しの中に `sleep` や待ちを置かない。`groupRename` の2つの名前は VU の番号を含めて、ほかの VU と重ならない形にする（名前の鍵は大文字と小文字を区別しない全体で一意のため）。`performance-design.md` と B3 の台本への引き継ぎに書く。

---

## Q1 接続プールの上限の場面（groupPoolLimit）で何を「見積もりどおり」「見積もりの誤り」と読むか

背景: 承認済みの NFR2.7 は、接続プールの上限を 10 に下げた場面を2回流すとしています。

- 4 VU の回（4 × 2 = 8 本 < 10 本）: 時間切れの累計 0・500 が 0 件を期待する。
- 20 VU の回: 「借りるまでの待ちの最大が 0 ミリ秒より大きい」を期待し、時間切れと 500 が出ても見積もりどおりとする。

20 VU の期待は、プールが尽きなくてもほぼ常に成り立つため、尽きたことの証拠になりません（NFR 要件の読み直しの R-02。承認の場で「NFR 設計の始めに決めて記録する」と決定）。

一方、グループの書き込み（メンバーの追加と外し）は、確定の後の監査の記録で1本目を持ったまま2本目を借ります（既存の監査の形）。そのため上限 10 に対して書き込みの同時が 10 以上になると、10 本すべてが1本目を持ったまま全員が2本目を待ち、待ちの上限 5000 ms まで時間切れになりえます。これは見積もりが正しくても起きる既知の制約です（`project.md` の Decided「同時の数が上限に達すると再び起きうる」）。U4 role の NFR2.7 は「上限に届いた証拠（`pending` 1 以上）を求めつつ、20 VU で時間切れ 0 を合格」とする形ですが、NFR 要件の読み直しの R-10 で、この既知の制約のために見積もりが正しくても不合格になりうると指摘されました。group も同じ2本使いのため、同じ穴を持たない形を選びます。

前提として、1要求で同時に持つ接続の本数（書き込み 2・読み取り 1）は、結合テスト `GroupConnectionUsageIT` で決定的に確かめます（承認済みの NFR2.7）。

A. 見積もりの合否は「本数」と「上限ちょうど」で決め、20 VU は既知の制約の記録にする。
- 合否は3つで決める。
  - `GroupConnectionUsageIT`: 書き込み 2・読み取り 1。3 以上は見積もりの誤り。
  - 4 VU の回: 承認済みのまま、時間切れの累計 0・500 が 0 件。
  - **上限ちょうどの 5 VU の回**（5 × 2 = 10 本 = 上限）を足す。見積もりどおりなら、2本目を借りるときに必ず空きがあり、待ちは起きない。期待は `pending` の最大 0・時間切れの累計 0・500 が 0 件。`pending` が 1 以上になったら、1要求で3本以上使う経路、または接続を返さずに持ち越す経路がある見積もりの誤りとして扱う。ただしその前に、使い捨てのアプリにほかの要求や定期の処理が無かったかをログで確かめる。
- 20 VU の回は合否に使わない。
  - 上限に届いた証拠（`pending` の最大が 1 以上かつ `acquire` の最大が 10 ミリ秒以上。成り立たなければ無効にして 40 VU で流し直す）を確かめる。
  - 待ちの最大・時間切れの数・500 の数・`GROUP_BUSY` の数を、既知の制約の大きさとして記録する。
  - 時間切れと 500 は既知の制約として期待に含める。ただし 500 の原因が接続の待ちの時間切れ以外（ほかの例外・例外の文の漏えい）なら、不具合として直す。
- 承認済みの NFR2.7 との差（5 VU の回を足す、20 VU の期待の書き方）は `scalability-design.md` に書く。
- U4 role も NFR 設計の段で同じ論点（R-10）を扱うため、この形（本数は結合テスト、合否は上限ちょうどの回、上限を超える回は既知の制約の記録）を引き継ぎに書き、そろえることを勧める。

（推奨: 見積もりの誤りと既知の制約を、数で見分けられる。上限ちょうどの回は、見積もりが正しければ待ちが 0 になり、誤っていれば待ちが出るため、上限に届く形でありながら合否の線がはっきりする。20 VU は時間切れが出ても不合格にならず、既知の制約の大きさが記録に残る）

B. 合否は `GroupConnectionUsageIT` と 4 VU の回だけで決める（承認済みのまま）。20 VU の回は、上限に届いた証拠を確かめたうえで、待ち・時間切れ・500 の数を既知の制約として記録するだけにする。5 VU の回は足さない（試験は1回少なく、承認済みの要件との差も小さい。ただし上限に届く回は記録だけで、負荷の下での見積もりの誤りは見分けにくい）

C. U4 role の今の NFR2.7 と同じ形にそろえる。20 VU の回で、証拠（`pending` の最大が 1 以上かつ `acquire` の最大が 10 ミリ秒以上）を求め、時間切れの累計 0・500 が 0 件を合格とし、外れたら見積もりの誤りとする（2つの単位で形はそろうが、R-10 の指摘のとおり、見積もりが正しくても既知の制約で不合格になりうる）

X. Other (please specify)

[Answer]: A **Mode:** guided

## Q2 捨ての試しのコードの作り方と範囲・時間の上限

背景: 設計が頼る H2 と HikariCP の振る舞いは、この段でリポジトリの外の捨ての試しのコードで先に確かめ、結果の要点（版・設定・数値・設計への意味）を成果物に写します（`project.md` の学び、NFR3.7）。確かめる項目は次の7つです。試しはこの質問の答えの後に行います。

1. 同じ名前（大文字と小文字だけが違う鍵を含む）の同時の作成で、重なった側が一意の違反になるか、待ちの上限切れになるか
2. 同じ名前への同時の変更で、1 と同じこと
3. 同じメンバーの同時の追加（主キー）で、1 と同じこと
4. 削除とメンバーの追加の重なりで、行の排他が順番を決め、外部キーが最後の守りとして働くこと
5. 待ちの上限切れ（3000 ms のヒント）の後の巻き戻しと、巻き戻しの印が付いた1つ目のトランザクションを終えた後に、2つ目の `TransactionTemplate` が問題なく確定すること（R-11 の形）
6. 上の例外の連なりの文に、行の値（名前・ID）が入るか
7. Hibernate と Spring が、違反と上限切れを別の例外の型に変えるか（`RowLockFailures` で見分けられるか）

A. リポジトリの外（作業用の一時の場所）に、Spring Boot 4.1.1・Spring Data JPA・Hibernate・HikariCP・H2 2.4.240（`-Dh2.compactThreads=1`）を lockfile と同じ版でそろえた最小のアプリを作り、7項目を確かめる。時間の上限は 2 時間で、超えたら確かめられた項目までを記録し、残りは結合テストの期待を両方の結果で受ける形にして依頼者に諮る（推奨: 項目 5〜7 は Spring と Hibernate の例外の変え方と TransactionTemplate の振る舞いに頼るため、素の JDBC では確かめられない）
B. リポジトリの外の素の JDBC と H2 だけの小さな Java で、項目 1〜4・6 を確かめる（時間の上限 1 時間）。項目 5・7 はコード生成の結合テストで確かめ、期待はそこで決める
C. 捨ての試しはせず、コード生成（B3）の結合テストの結果で期待と 409 の code を決める（`project.md` の学びと NFR3.7 とは違う進め方になる）
X. Other (please specify)

[Answer]: A **Mode:** guided

## Q3 違反・上限切れを起こしうる書き込みと排他の置き場

背景: TraceAspect は `web`・`service`・`domain`・`repository` の方法から投げられた例外の文を TRACE に出します。そのため、例えば service から Spring Data の `saveAndFlush` を呼んで一意の違反が起きると、その例外の文（行の値が入りうる）が repository の代理の層で TRACE に出ます。既存の作りは2通りあります。

- `invitation/lock` の形: TraceAspect の対象の外の用途名の下位パッケージに、`EntityManager` を直接使う部品を置き、例外を中で受けて区分に変える。
- `UserRowLockRepository` の形: `repository` の層の自前のクラスの1つの方法の中で `EntityManager` を使い、例外をその方法の外へ出さない。中で Spring Data の方法を呼ばない。

A. `invitation/lock` と同じく、TraceAspect の対象の外の用途名の下位パッケージ（例: `group/store`）に、排他（グループの行）と、違反を起こしうる書き込み（作成・名前の変更・メンバーの追加・削除の flush）を1つの部品にまとめる。例外を中で受けて結果の型（成功・違反の種類・上限切れ）に変えて返し、ログには例外のクラスの名前だけを出す。読み取り（一覧・詳細・所属・数）は今までどおり `repository` の Spring Data で行う。`GroupBoundaryArchitectureTest` で、service がこの部品を通さずに書き込みの方法を呼ばないことを確かめる（推奨: 例外が TraceAspect の対象の方法を1つも通らず、既存の `invitation` と同じ形で、漏えいの経路を構造の検査で閉じられる）
B. `UserRowLockRepository` と同じく、`repository` の層の自前のクラスに置き、各方法の中で例外を受けて返す（パッケージは増えないが、方法の外へ例外が出ないことは各方法の書き方に頼り、構造の検査で閉じにくい）
C. 書き込みは Spring Data のまま service から呼び、TraceAspect の例外の文の設定（`MASTERSMITH_TRACE_EXCEPTION_MESSAGE`）から `$[exception]` を外す（全体の TRACE の形を変えるため、ほかの機能の診断にも響く）
X. Other (please specify)

[Answer]: A **Mode:** guided

## Consolidated Summary Confirmation

答えのまとめ（group の NFR 設計）:

- Q1 A: 接続の見積もりの合否は、`GroupConnectionUsageIT` の本数（書き込み 2・読み取り 1、3 以上は見積もりの誤り）、4 VU の回（時間切れ 0・500 が 0 件）、足す「上限ちょうどの 5 VU の回」（`pending` の最大 0・時間切れ 0・500 が 0 件。`pending` が 1 以上なら、ほかの接続の使い手が無いことをログで確かめたうえで見積もりの誤りと読む）で決める。20 VU の回は合否に使わず、上限に届いた証拠（`pending` の最大 1 以上かつ `acquire` の最大 10 ms 以上、無ければ 40 VU で流し直す）を確かめ、待ち・時間切れ・500・`GROUP_BUSY` の数を記録する。時間切れと 500 は既知の制約（書き込みの2本使い）として期待に含め、500 の原因が接続の待ちの時間切れ以外なら不具合として直す。承認済みの NFR2.7 との差を `scalability-design.md` に書き、role にも同じ形を勧める引き継ぎを書く。
- Q2 A: 捨ての試しは、リポジトリの外に lockfile と同じ版の Spring Boot・Hibernate・HikariCP・H2 で最小のアプリを作り、7 項目を確かめる。時間の上限は 2 時間で、超えたら確かめた所までを記録して依頼者に諮る。結果の要点を成果物に写す。
- Q3 A: 違反・排他の上限切れを起こしうる書き込みと排他は、TraceAspect の対象の層の外の用途名の下位パッケージ（例: `group/store`）にまとめ、例外は中で受けて結果の型に変える。構造の検査で抜け道を閉じる。
- 決まっていること（承認済みの NFR 要件の形、排他の作り、例外の文の漏えいの経路と `@Modifying` の扱い、読み直しで残った R-03〜R-08 の設計での手当て）は案のとおり。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
