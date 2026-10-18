# Units Generation の質問（Intent 261016-business-data-views）

部品の一覧（`aidlc/spaces/default/intents/261016-business-data-views/inception/domain-design/components.md`、10部品）と ADR-001〜008、ストーリー（17件）をもとに、部品を作業の単位（Unit）にまとめる。単位の順（どれを先に作るか）は Delivery Planning で決めるため、ここでは尋ねない。

## 決まっていること（質問にしない）

- 配備の形は、今までどおりフロントエンドの成果物を同梱した1つの実行可能 WAR（team.md の Deployment）。単位はすべてこの WAR の中の部品で、別々には配備しない。
- 単位の種類（kind）は、API を持つバックエンドを `service`、画面を `ui` とする（前の Intent と同じ読み方）。
- 境界テストを緩める変更（`RoleBoundaryArchitectureTest` に `businessdata` を足す）は、それを含む単位のコード生成の計画で承認を得る（ADR-002）。

## UQ1. 単位の分け方

A. 5つにする: U1 業務データの読み取りの API（一覧・検索・詳細、出し分けの関数、行の SQL の部品の読み取り、権限・DSL の読み取り、`role` の境界テストの変更）・U2 業務データの書き込みの API（作成・更新・削除、検証・一意、`targetdb` の書き込みの接続、監査の種類）・U3 業務データの画面（一覧・詳細・作成・更新・削除の確かめと `src/shared/` の部品）・U4 アクセシビリティの持ち越し（make-you-chic-ui の固定先 `b0a1e79`、行の「操作」の名前、axe-core 4.14、トップバー）・U5 テストと測定の持ち越し（不安定なテスト2件、`memory.peak`）（推奨）
B. 4つにする: A の U1 と U2 を1つのバックエンドの単位（業務データの API）にまとめる。ほかは A と同じ
C. 操作ごとの縦の切り方にする: 一覧と検索・詳細・作成と更新・削除をそれぞれ、バックエンドと画面を合わせた単位にする（横断の出し分けの関数と SQL の部品は最初の単位に置く）。持ち越しは A の U4・U5 と同じ
X. Other (please specify)

[Answer]: A

## UQ2. テストと測定の持ち越し（ストーリーにしないもの: FR15・FR16）の扱い

A. 1つの単位（UQ1 の U5）にし、種類を `library` とする（設計の段の多くは当たらず、コード生成と Build and Test が主になる）（推奨）
B. 単位にせず、不安定なテスト2件はそのテストを持つ単位（`RoleConcurrencyIT` は U1、`AdminForbiddenProvider.test.tsx` は U3）のコード生成に入れ、`memory.peak` は Build and Test だけで扱う
X. Other (please specify)

[Answer]: A

## UQ3. 単位の間の並行の作業

A. 依存の上で並べて作れる単位（例: 画面と読み取りの API の口を決めた後の書き込みの API、持ち越しの単位）を依存の図に記録するが、作る順と並べるかは Delivery Planning で決める（推奨）
B. 依存の順に1つずつ作る前提にし、並べて作れる組は記録だけにする（前の Intent と同じ）
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

分け方の計画（UQ1: A・UQ2: A・UQ3: A）:

- U1 `u1-businessdata-read`（`service`、L）: 業務データの一覧・検索・詳細の API。出すカラム・入力できるカラム・許す操作を求める純粋な関数、判定の順と拒否の code、行の SQL を組み立てて実行する部品（読み取りの側）と DB の種類ごとの方言、`targetdb` の読み取りの接続の口、権限の解決の口と DSL の読み取り、`RoleBoundaryArchitectureTest` に依存してよい相手を足す変更、`businessdata` の境界テスト。部品: BusinessData（読み取り）・TargetDb（読み取りの口）・RoleManagement・DslDefinition。依存: なし。
- U2 `u2-businessdata-write`（`service`、L）: 作成・更新・削除の API、入力の検証（一意の事前の問い合わせ・`pattern` の時間の上限）、`targetdb` の書き込みの接続の設定と DataSource、対象DB 用のトランザクション、2行以上に当たる更新・削除の巻き戻し、監査の出来事と監査の種類。部品: BusinessData（書き込み）・TargetDb（書き込みの接続）・AuditLog。依存: U1（行の SQL の部品と出し分けの関数を使う）。
- U3 `u3-businessdata-screens`（`ui`、L）: 一覧（検索・表・ページ送り）・詳細・作成・更新の画面と削除の確かめ、`src/shared/` の NULL の表示・横のスクロールの枠・1ページの件数の選択・離れるときの確かめ、業務データの API の呼び出し、道の登録。部品: BusinessDataScreens・SharedUi・ApiClient・AppShell（道の登録）。依存: U1・U2（API）・U4（Dropdown の固定先と「操作: …」の名前の関数）。
- U4 `u4-a11y-carryover`（`ui`、M）: make-you-chic-ui の固定先を `b0a1e79` に上げる、`src/shared/` の「操作: …」の名前の関数と既存の管理の一覧の直し、トップバーの利用者のメニューのトリガーの名前（E2E 080 の違反の部品を確かめて、frontend の側か依頼（5）で直す）、axe-core 4.14 の取り込みと `ignore` を外すこと。部品: AdminLists・SharedUi（名前の関数）・AppShell（トップバー）。依存: なし。
- U5 `u5-carryover-tests`（`library`、S）: 不安定なテスト2件（`RoleConcurrencyIT` の #7・`AdminForbiddenProvider.test.tsx`）の直しと、`dslMixed` の `memory.peak` の測り直し（`anon` の最大を並べる・ページキャッシュを減らす手）。部品を増やさない。依存: なし。
- 依存の図: U2 → U1、U3 → U1・U2・U4。U1・U4・U5 は互いに依存せず、並べて作れる。作る順と並べるかは Delivery Planning で決める（UQ3: A）。
- 配備はすべて1つの WAR の中。種類によって作る設計の文書が変わる（`library` の U5 は業務のモデルや拡張性の文書を持たない）。

Does this all look correct before I generate the unit artifacts?

- Looks correct
- Request changes

[Answer]: Looks correct
