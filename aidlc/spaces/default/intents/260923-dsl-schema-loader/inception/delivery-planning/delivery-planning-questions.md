# Delivery Planning — 質問

この段では、Construction（設計と実装）で作る順序を決めます。作る単位ごとに「Bolt」（1回分の作りの区切り。設計から実装・テストまでを通し、動くものができて終わる）にまとめ、その順序と、それぞれの完了の条件を決めます。

すでに決まっていること:
- 単位は5つ（U1 対象DB、U2 DSL の定義、U3 既定の DSL の生成、U4 DSL の管理、U5 DSL の管理画面）。依存は U3→U1・U2、U4→U1・U2・U3、U5→U4（`inception/units-generation/unit-of-work-dependency.md`）。
- 依存の順に1つずつ作り、並行しては作らない（Units Generation の Q4: B）。
- アプリの骨格はすでにあるため、最初の Bolt に骨格を通す特別な手順（walking skeleton、最初に全体を細く通して確かめる進め方）は行わない（`aidlc/spaces/default/memory/team.md` の Walking Skeleton）。
- 作るのは AI（開発担当）で、依頼者が承認する。外のチームの待ちは無い。
- Bolt の作業ブランチは `develop` から作り、統合前の1コマンドの検査（`./gradlew verify`）を通してから `develop` へ squash マージする（team.md の Way of Working）。

---

## Q1. 最初に作る単位

U1 と U2 は互いに依存しないため、どちらを先にしてもよい順序です。どちらにも、確かめないと先に進めない不確かな点があります（`inception/domain-design/decisions.md` の ADR-006・ADR-008・ADR-010）。

A. U2（DSL の定義）を先にする: U2 → U1 → U3 → U4 → U5。最も不確かな「誤りの行・列を求める作り」と、YAML・JSON Schema の部品の選定を先に確かめる
B. U1（対象DB）を先にする: U1 → U2 → U3 → U4 → U5。既存の構成に手を入れる「対象DB の接続の足し方」と、Testcontainers の導入を先に確かめる
X. Other (please specify)

[Answer]: A

## Q2. Bolt の大きさ

U4（DSL の管理）は規模が最も大きく（XL）、Must のストーリーと Should のストーリー（前の版に戻す・適用中の DSL のダウンロード）を含みます。

A. 1つの単位を1つの Bolt にする。ただし U4 は2つに分ける: U4-1（保存・投入・プレビュー・適用・破棄・監査・プレビュー中のダウンロード、Must）と U4-2（履歴の一覧と戻し・適用中のダウンロード、Should）。Bolt は全部で6つ
B. 1つの単位を1つの Bolt にする（U4 も分けない）。Bolt は全部で5つ
X. Other (please specify)

[Answer]: A

## Q3. Should のストーリー（前の版に戻す・適用中の DSL のダウンロード）の扱い

A. この Intent の中で作る（U4・U5 の Bolt に入れる）
B. この Intent では作らず、後の Intent に回す（履歴の書き込みは Must の適用に含まれるので、後から足せる）
X. Other (please specify)

[Answer]: A

## Q4. 設計と実装の進め方

Construction では、単位ごとに機能設計 → NFR 要件 → NFR 設計 → インフラの設計 → コード生成 の順に進みます。

A. 設計の段ごとに、すべての単位を通す（機能設計を5つの単位ぶん行ってから NFR 要件へ、…、最後にコード生成を単位の順に）。設計どうしの食い違いを早く見つけやすい。各段の承認は段ごとに行う
B. 単位ごとに、設計からコード生成までを終えてから次の単位へ進む。動くコードが早くできる。承認は、単位の区切りの最後にまとめて段ごとに行う
X. Other (please specify)

[Answer]: A

## Q5. いちばん気がかりなこと

最初に確かめて安心したい点を選んでください（複数選択可、select all that apply）。

A. 誤りの行・列を求める作り（ADR-008）
B. 対象DB の接続の足し方で、ログイン・監査・Flyway が内部DB を向き続けること（ADR-006）
C. 3種類の DB のテストの時間と、colima の VM のメモリ（Testcontainers）
D. 100テーブル×100カラムでの性能（30 秒・10 秒）
X. Other (please specify)

[Answer]: A, B

---

## Consolidated Summary Confirmation

- 作る順序は U2 → U1 → U3 → U4 → U5 とする。U1 と U2 の順は依存の上ではどちらでもよく、最も不確かな U2 を先にする（Q1: A）
- Bolt は1つの単位を1つとし、U4 だけ2つに分ける。U4-1（保存・投入・プレビュー・適用・破棄・監査・プレビュー中のダウンロード、Must）と U4-2（履歴の一覧と戻し・適用中のダウンロード、Should）。Bolt は全部で6つ: B1 U2、B2 U1、B3 U3、B4 U4-1、B5 U4-2、B6 U5（Q2: A）
- Should のストーリー（前の版に戻す・適用中の DSL のダウンロード）もこの Intent で作る（Q3: A）
- 設計と実装は、設計の段ごとにすべての単位を通し、最後にコード生成を Bolt の順に行う。承認は段ごと（Q4: A）
- いちばん気がかりなのは、誤りの行・列を求める作り（ADR-008）と、対象DB の接続の足し方で既存の働きが内部DB を向き続けること（ADR-006）（Q5: A, B）。コード生成は最後になるため、この2点は NFR 要件の段で小さく試して（本番のコードとは別の試しのコード）確かめ、確かめられなければ ADR の切り替え先を依頼者に諮る
- 外のチームの待ちは無い。外部の依存は、Testcontainers のコンテナのイメージ（MySQL・MariaDB・PostgreSQL）と新しいライブラリ（JDBC ドライバー・YAML・JSON Schema）の取得だけ
- 作るのは AI（開発担当）で、依頼者が承認する（チームは1つ）

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
