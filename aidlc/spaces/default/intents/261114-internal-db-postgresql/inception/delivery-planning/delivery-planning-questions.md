# Delivery Planning の質問（Intent 261114-internal-db-postgresql）

Bolt は、1つ以上の作る単位（Unit of Work）を、設計から統合まで通して動くものにする1回の作り込みのことです。この段は、4つの単位（U1 u1-internal-db-platform・U2 u2-portable-sql・U3 u3-postgres-behaviour-tests・U4 u4-local-runtime-docs）を作る順（Bolt の並び）と、それぞれの終わりの条件を決めます。単位の依存（U3→U1・U2、U4→U1）は決まっているので、その範囲で順を選びます。

## 決まっていること（質問にしない）

- **作る前提**: 依頼者1名と AI で、Bolt は1つずつ順に作る（Construction Execution: serial）。Bolt ごとに `develop` から短命のブランチ（`feature/261114-internal-db-postgresql-b<n>`）を作り、squash で `develop` に戻す。worktree は使わない（`team.md` の Way of Working）。
- **最初の Bolt の特別な手順（walking skeleton: 最初に全体を一通り動かす薄い作り込み）**: 行わない（`team.md` の Walking Skeleton。アプリの骨格は既にある）。
- **設計の順**: U1 の機能設計を、U2・U3 の機能設計より前に置く（Units Generation のレビュー R-03、Contract Design の C3・C4）。U1 の機能設計が決める組（バイナリの列の型と写し方）・テストの土台のパッケージの名前を、U2・U3 が使う。依存の図に辺は無いが、この順を守る並びだけを選択肢にした。
- **統合の前の検査**: 各 Bolt の統合の前に `./gradlew verifyQuick`（変えた所に関わる結合テストは `-PitTests` で絞る）を通す。全件の `./gradlew verify` は Build and Test とリリースの前に流す（`team.md`）。内部DB の PostgreSQL の結合テストは、明示の指定（`:backend:internalDbPostgresTest`、C4）を付けたときだけ流す。
- **カバレッジ**: 全体とすべてのパッケージの下限（行 80%・分岐 70%）を、PostgreSQL の結合テストを流さない普段の `verify` だけで満たす（NFR6）。PostgreSQL のときだけ通るコードは U1 の単体テストで満たす。
- **E2E**: 画面と認証・権限・業務データの本体を変えないため、Bolt ごとには流さない。Intent の最後（Build and Test）に全体を1回流す（`team.md`）。
- **外の待ち**: make-you-chic-ui などの上流への依頼は無い。新しい依存は Flyway の PostgreSQL の部品だけで、採用の前にライセンスと依存の木を確かめる（U1、NFR8）。
- **Construction の確認の場のコマンド**: この Intent に既に記録してある `./gradlew verify`（colima の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` 付き）を使い続ける。
- **配備の確かめ**: compose の内部DB の PostgreSQL での起動確認・スモークテスト・バックアップと戻しの練習は、Deployment Execution で行う（`team.md` の Deployment）。

## Q1. [デリバリー] 何を先に作るか

U1 は最初に置きます（設計の順の制約と、U3・U4 が U1 に頼るため）。残る U2・U3・U4 の並べ方を決めたいです。U3 は PostgreSQL の上で全機能の振る舞いを確かめるため、U2 の書き直しの漏れや、Auth・Invitation の SQL の見込みの外れ（U2 の小さな直しになる）が最も見つかりやすい単位です。U4 は compose と文書で、U1 で決まる設定に合わせて書きます。

**推奨: A。** 理由: 危険の大きいもの（本体の直しにつながりうる U3）を早く確かめ、直しが U2 に戻ってきても文書（U4）を書き直さずに済みます。U4 は最後に置くと、それまでの直しの結果（設定・対応する版・手順）を一度で書けます。

A. 危険を先にする: U1（土台）→ U2（書き直し）→ U3（PostgreSQL での振る舞いの確かめ）→ U4（compose と文書）
B. 手元で早く触れるようにする: U1 → U4（compose で PostgreSQL を起動できるようにする）→ U2 → U3
C. 本体の変更を先にまとめる: U1 → U2 → U4 → U3（U3 の直しが出たら U4 の文書も見直す）
X. Other (please specify)

[Answer]: A

## Q2. [デリバリー] Bolt の大きさ

U1 は大きさ L（種類の判断・PostgreSQL 用の移行 V1〜V14・テストの土台・最初の結合テスト）、U2・U3 は M、U4 は S です。

**推奨: A。** 理由: 4つの単位は確かめ方（U1 は単体テストと最初の結合テスト、U2 は H2 の結合テストの全件、U3 は PostgreSQL の結合テスト、U4 は compose と文書）が違い、1単位を1 Bolt にすると統合の後の戻しとレビューが単位ごとに閉じます。U1 が大きいときは、設計の段は1つの単位として通し、コード生成の計画で Bolt の中の手順に分けます（`project.md` の学び）。

A. 1単位を1 Bolt にする（4 Bolt）
B. 大きい U1 を前半（種類の判断・移行・単体テスト）と後半（テストの土台・最初の結合テスト）の2 Bolt に分ける（5 Bolt）
C. U2 と U3 を1 Bolt にまとめる（書き直しと PostgreSQL での確かめを同じ統合にする。3 Bolt）
X. Other (please specify)

[Answer]: A

## Q3. [デリバリー] 捨ての試しをいつ行うか

設計の前に、リポジトリの外の捨ての試しで確かめることが2組あります（`project.md` の学び、ADR-006）。

- U1 の機能設計の前: バイナリの列の型と写し方の組（C3。DSL の本文の2列とトークンのハッシュの2列）、V8 の生成列の一意を条件つきの一意の索引に置き換えられるか。
- U2 の機能設計の前: Auth・Invitation の今の SQL（`MERGE INTO ... USING (VALUES ...)`・`FETCH FIRST` の副問い合わせ付きの削除）が PostgreSQL で通るか（C5）、同時の実行での `MERGE` の一意の違反の出方。

**推奨: A。** 理由: どちらも同じ PostgreSQL 18 のコンテナで確かめられ、まとめると準備が1回で済みます。U2 の範囲が変わるか（C5 の見込みの外れ）が U1 の機能設計の時点で分かり、U1 の機能設計で単一の写し方が見つからなかったとき（C3）に依頼者に諮る内容も、U2 への影響と一緒に示せます。

A. 2組をまとめて、U1 の機能設計の前に行う（結果は捨ての試しの記録と `aidlc/spaces/default/knowledge/aidlc-shared/study-internal-db-postgresql.md` に足す）
B. それぞれの単位の機能設計の直前に分けて行う（U2 の分は U1 の Bolt が終わった後）
X. Other (please specify)

[Answer]: A

## Q4. [デリバリー] Construction の進め方（単位ごとか、段ごとか）

**推奨: A。** 理由: U1 の機能設計が先という順は、単位ごとに進めれば自然に守られます。U1 を設計からコード生成まで通してテストの土台ができてから U2・U3 の設計に入るので、U2・U3 の設計で土台の実際の形（注釈・タスク）を確かめながら書けます。単位ごとの完了の確かめ（Construction の確認の場）も、単位ごとに行えます。

A. 単位ごと（unit-major）: 1つの単位を設計の段からコード生成まで通し、確かめてから次の単位へ進む（今の既定）
B. 段ごと（stage-major）: 設計の段を全単位で通してから、コード生成を Bolt の順に行う（設計の指摘の持ち越しが全単位まとまる）
X. Other (please specify)

[Answer]: A

## Q5. [デリバリー] PostgreSQL の結合テストを、各 Bolt の終わりの条件でどう流すか

Construction の確認の場のコマンド（全件の `./gradlew verify`）は、PostgreSQL の結合テストを流しません（FR5.2）。U1 の後は、明示の指定 `./gradlew :backend:internalDbPostgresTest` が使えます。

**推奨: A。** 理由: `team.md` は PostgreSQL の結合テストを「必要と判断したときに明示して流す」としており、確認の場のコマンドに入れると、PostgreSQL に触れない Bolt（U4）でも毎回流れ、長いテストを重ねることになります。PostgreSQL の経路を変える Bolt の終わりの条件に入れれば、必要な場面だけで流れます。

A. 確認の場のコマンドは今のまま（`./gradlew verify`）にし、PostgreSQL の結合テストは、PostgreSQL の経路を変える Bolt（U1・U2・U3）の終わりの条件に入れて明示して流し、結果と時間を記録する
B. 確認の場のコマンドに `:backend:internalDbPostgresTest` を足し、U1 の後のすべての確認の場で流す（コマンドの変更は別に承認を得る）
X. Other (please specify)

[Answer]: A
