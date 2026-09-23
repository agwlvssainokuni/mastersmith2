# NFR Requirements — U1 対象DB（u1-target-db）— 質問

この段では、U1 の非機能の要件（数値の目標と守る決まり）を決めます。U1 の受け持ちは、要件の NFR4（接続情報を出さない・受け取らない）、NFR6（識別子の SQL インジェクションを防ぐ）、NFR7（対象DB が無くても起動する）、NFR12（実際の DB のコンテナで結合テスト）と、NFR1 の一部（30 秒の内のメタデータの読み取り）です（`inception/units-generation/unit-of-work.md`）。

すでに決まっていること:
- 接続先は `mastersmith.target-db.*` の設定だけから受け取り、読み取り専用で、起動時には接続しない。ヘルスチェックに含めない（`construction/u1-target-db/functional-design/rules.md` の BR1.1〜BR1.9）。
- 接続は「既定の候補にしない」指定で足す（ADR-006）。この段で、本番とは別の試しのコードで小さく確かめる（`inception/delivery-planning/risk-and-sequencing-rationale.md`）。
- 接続・問い合わせに待ち時間の上限を置く（BR1.8）。数値はこの段で決める。
- 新しい依存を足すときはライセンスを確かめ、Apache License 2.0 と異なるものは採用の理由を記録する（`aidlc/spaces/default/memory/team.md` の Code Style）。
- 対象DB の結合テストは、版を固定したイメージのコンテナで起動した実際の MySQL・MariaDB・PostgreSQL で行う（`aidlc/spaces/default/memory/project.md` の Mandated）。

---

## Q1. 接続と問い合わせの待ち時間の上限

スキーマの読み込み（既定の DSL の生成）は全体で 30 秒以内が目標です（NFR1）。対象DB が応答しないときは、この上限で打ち切って「接続できない」とします（ストーリーの AC1.1.8）。

A. 接続 5 秒・問い合わせ 1回あたり 20 秒（30 秒の目標の内に、生成と保存の時間を残す）
B. 接続 3 秒・問い合わせ 1回あたり 10 秒（早く諦める。大きなスキーマで問い合わせが遅いと打ち切られるおそれ）
C. 接続 10 秒・問い合わせ 1回あたり 25 秒（遅い対象DB にも待つ。30 秒の目標を超えるおそれ）
X. Other (please specify)

[Answer]: A

## Q2. 対象DB の接続のプールの大きさ

スキーマの読み込みは管理者の手動の操作で、まれです。後続の Intent（J・K）では業務データの読み書きに同じ接続を使います（ADR-006）。

A. 最大 2、使っていないときは接続を持たない（最小 0、しばらく使わなければ閉じる）。後続の Intent で必要になったら見直す
B. 最大 5、最小 0（後続の Intent の業務データの操作を見込む）
C. 最大 1、最小 0（同時の読み込みは1つだけ）
X. Other (please specify)

[Answer]: B

## Q3. テストに使う DB の版（固定するイメージ）

A. 長く支援される版にそろえる: MySQL 8.4（LTS）・MariaDB 11.8（LTS）・PostgreSQL 18。細かい版（パッチ）はコード生成で固定する
B. それぞれの最新の版にそろえる（MySQL は短期支援の Innovation 版を含む）
C. 長く支援される版に加えて、1つ前の版でも確かめる（6種類。テストの時間と colima のメモリが増える）
X. Other (please specify)

[Answer]: A

## Q4. JDBC ドライバー（ライセンス）

リポジトリは Apache License 2.0 で、ビルドの成果物（WAR）はドライバーを同梱します。MySQL Connector/J は GPL v2 に FOSS 例外（オープンソースのプロジェクトに組み込んでよいという特例）が付いたもの、MariaDB Connector/J は LGPL 2.1、PostgreSQL JDBC は BSD 2-Clause です。

A. 3つとも公式のドライバーを使い同梱する。MySQL Connector/J は FOSS 例外で Apache License 2.0 のプロジェクトに同梱できること、MariaDB Connector/J は LGPL の条件（差し替えられる形での同梱）を満たすことを、採用の理由として記録する
B. MySQL にも MariaDB Connector/J を使い、GPL のドライバーを避ける（MariaDB のドライバーの MySQL への対応は限られ、動作の確かめが増える）
C. PostgreSQL のドライバーだけ同梱し、MySQL・MariaDB のドライバーは利用者が自分で入れる（配布は単純になるが、導入の手間が増える）
X. Other (please specify)

[Answer]: A

## Q5. 読み取り専用の守り方

A. アプリ側で接続を読み取り専用にし、対象DB のアカウントを読み取りの権限だけにすることを README で推奨する（アカウントの権限をアプリは調べない）
B. A に加えて、初めて接続したときにアカウントの書き込み権限の有無を調べ、書き込めるなら WARN を1件出す（DB の種類ごとに調べ方が違い、作りが増える）
X. Other (please specify)

[Answer]: A

## Q6. 「既定の候補にしない」指定の試し方（ADR-006）

A. 使い捨ての作業ブランチで、対象DB の接続を1つ足した状態の結合テストを1本動かし、ログイン・監査・Flyway・ヘルスチェックが内部DB を向き続けるかを確かめる。結果は tech-stack-decisions.md に記録し、試しのコードは捨てる（本物は B2 で作る）
B. A と同じことを確かめ、試しのテストはそのまま残して B2 で本物に取り込む（develop に試しのコードがしばらく残る）
X. Other (please specify)

[Answer]: A

---

## 試しの結果（Q6: A、ADR-006）

使い捨ての作業ブランチで、対象DB の接続の役（`@Bean(defaultCandidate = false)`、届かない先を指す HikariCP、読み取り専用・最大 5・最小 0・接続の待ち 5 秒・起動時に接続しない）を足し、次を確かめた。試しのコードとブランチは捨てた。

- 型で注入される `DataSource` は内部DB（H2 のファイル）のまま。接続の Bean は2つ（`dataSource`・`targetDataSource`）になるが、既定の候補は内部DB だけ
- Flyway と JPA のトランザクションは内部DB を使う（Flyway の適用 4 件）
- ヘルスチェックは 200（UP）で、対象DB の接続のプールは起動時に一度も接続していない
- 届かない対象DB への接続は約 5 秒（5016 ミリ秒）で失敗する
- 試しの接続を足したまま、既存の結合テストを含む 248 件（試しの 4 件を含む）がすべて通った（ログイン・監査・アクセス制御・ヘルスチェック・Flyway を含む）

→ ADR-006 の条件は満たされた。切り替え先（内部DB を明示して既定にする形）は使わない。

## Consolidated Summary Confirmation

- **待ち時間**（Q1: A）: 接続 5 秒・問い合わせ 1回あたり 20 秒。超えたら「接続できない（TIMEOUT）」
- **プール**（Q2: B）: 最大 5・最小 0、使わない接続は 60 秒で閉じる。起動時に接続しない（ヘルスチェックに含めない）
- **テストの DB**（Q3: A）: MySQL 8.4（LTS）・MariaDB 11.8（LTS）・PostgreSQL 18 のイメージを、細かい版まで固定して Testcontainers で起動する（Testcontainers は MIT）
- **ドライバー**（Q4: A）: MySQL Connector/J（GPL v2＋FOSS 例外）・MariaDB Connector/J（LGPL 2.1）・PostgreSQL JDBC（BSD 2-Clause）を同梱し、採用の理由を tech-stack-decisions.md に記録する
- **読み取り専用**（Q5: A）: 接続を読み取り専用にし、読み取りの権限だけのアカウントを README で推奨する
- **接続の足し方**: ADR-006 のとおり（試しで確かめた）
- **成果物**: U1 は library のため、security-requirements.md（NFR4・NFR6・NFR7 の枝番）・tech-stack-decisions.md（上の選定と試しの結果、NFR1・NFR12 の枝番）・traceability.json を作る。30 秒・10 秒の目標の測定は Build and Test

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
