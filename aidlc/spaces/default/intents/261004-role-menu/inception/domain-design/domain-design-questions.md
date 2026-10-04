# Domain Design の質問

承認済みの要件・ストーリー・画面イメージと、コード知識ベース（K-32〜K-38、`component-inventory.md`）をもとに、部品の境界と持ち主を決めます。

## 決まっていること（質問にしない）

- **今ある部品は名前を変えない**: 利用者（`user`）・認証（`auth`）・認可（`access`）・監査（`audit`）・利用者の管理（`useradmin`）・DSL（`dsl`・`dslmanage`）・対象DB（`targetdb`）と、画面の骨組み（registry・navigation・layout・routing）。
- **管理者の印は別系統のまま**: 管理画面の操作は今の `access`（`/api/admin/**` は管理者だけ）で守り、ロールは業務データの権限だけに使う（要件 C1）。新しい管理の API は `/api/admin/` の下に置く。
- **DSL のスキーマの階層**: `dsl`（型・JSON Schema・検証）と `dslmanage`（既定の DSL の生成）に足す。書式の版 2 だけを受け付ける（ストーリー US6.1、M1・F2・F3）。
- **監査**: 監査は出来事を受けて、確定の後に別のトランザクションで記録する（今の仕組み）。各機能は `audit` を知らず出来事を出す。
- **境界の決まり（ArchUnit）**: 新しく作る機能は `<機能>BoundaryArchitectureTest` を置き、既存の機能に手を入れる Bolt で境界テストが無ければ足す（今回は `access` の見込み）。既存の境界の検査を緩める・変えるときは、コード生成の計画に明記して依頼者の承認を得る（`team.md`）。
- **画面**: 機能は `features/<id>/` に置き、機能どうしは直接 import しない（ESLint で止める。今の違反1件は最初の Bolt で直す）。権限の設定の木の部品は `src/shared/` に置く（画面の段の決定）。入れ子のサイドバーは make-you-chic-ui に依頼する。
- **実現できるかの判断**: classic の範囲で実現可能性の評価の段が無いため、この段の ADR にまとめる（`project.md` の学び）。対象は、要求ごとの実効の権限の解決の重さ、組み込みの H2（1つのアプリ）での同時の重なりの守り、DSL の版 2 への切り替えの影響、make-you-chic-ui への依頼が間に合わないとき。

質問は5問です。

---

## Q1. 役割・権限の機能の部品の切り方（バックエンド）

ロール・主権限と補助権限の設定・グループとメンバー・割り当て・作業ロール・実効の権限の解決・YAML の受け渡しを、どう部品に分けるかです。

A. 1つの新しい機能 `role`（ロール・設定・グループ・割り当て・作業ロール・解決・YAML の受け渡し）にまとめる。中は層（web・service・domain・repository）と用途名の下位パッケージ（例: `role/resolve`・`role/transfer`）で分ける
B. 2つに分ける: `role`（ロール・設定・解決・YAML の受け渡し）と `group`（グループ・メンバー）。割り当てと作業ロールは `role` に置き、`role` が `group` のメンバーを読む
C. 3つに分ける: 設定と管理の `role`、解決だけを持つ小さな `permission`（後の Intent の業務データの画面と、メニューが依存する口）、グループの `group`
X. Other (please specify)

[Answer]: B

## Q2. 作業ロールの覚え先

A. 役割・権限の機能（Q1 の `role`）の表に、利用者ごとの作業ロールを持つ（利用者の ID と作業ロールの ID）。`user` は役割を知らないまま
B. `user` の利用者の表に作業ロールの列を足す（`user` が役割の ID を持つ）
C. 既存の自分の設定（プリファレンス）の仕組みに作業ロールを足す
X. Other (please specify)

[Answer]: A

## Q3. 業務のメニューの木を作って返す部品

DSL の `menus` を読み（`dsl` の `ActiveDslModelProvider`）、作業ロールの実効の権限で絞って画面に返す部品です。

A. 新しい機能 `navigation` を作る。`dsl` と、役割・権限の機能の解決の口に依存し、`/api/me/navigation` のような「ログインだけ」の API を持つ
B. 役割・権限の機能（`role`）の中に置く（`role` が `dsl` に依存する）
C. `dsl` の中に置く（`dsl` が役割・権限の機能に依存する）
X. Other (please specify)

[Answer]: A

## Q4. 権限の設定の対象（スキーマ・テーブル・カラム）を DSL から読む向きと、DSL の適用との関係

設定の画面は適用済みの DSL のスキーマ・テーブル・カラムを並べ、DSL から消えた対象の設定は名前で持ち続けます（FR2.3・FR2.4）。

A. 役割・権限の機能が `dsl` の `ActiveDslModelProvider` を読む（片方向）。DSL を適用しても役割・権限の機能には知らせず、設定の画面・解決のたびに今の DSL と名前で照らし合わせる
B. A に加えて、DSL の適用の出来事を役割・権限の機能が受け、消えた対象の数などを前もって数えておく
C. 役割・権限の機能が、適用のたびに DSL の対象を自分の表へ写す（写しと照らし合わせる）
X. Other (please specify)

[Answer]: A

## Q5. 画面の機能の切り方（フロントエンド）

A. 機能を `roleadmin`（ロールの一覧・詳細の権限の設定と割り当て）・`groupadmin`（グループ）・`roletransfer`（権限の受け渡し）・`mypermissions`（自分の権限）・`tables`（テーブルの画面の置き場）に分け、作業ロールの切り替えと業務のメニューは骨組み（app）に置く
B. 管理の画面を1つの機能 `rolemanagement`（ロール・グループ・受け渡し）にまとめ、`mypermissions` と `tables` は別にする。作業ロールと業務のメニューは骨組み
C. A と同じだが、作業ロールの切り替えも機能（`workrole`）にし、骨組みの差し込み口から入れる
X. Other (please specify)

[Answer]: A

---

## 追加の質問

## F1. グループの削除の確かめと、部品の依存の向き（Q1: B から）

Q1 で `role` と `group` に分け、割り当て（グループへのロールの割り当てを含む）は `role` に置くことにしました。`role` は `group` のメンバーを読むため、依存は `role` → `group` の向きです。一方、「ロールの割り当てが残っているグループは削除できない」（FR4.1a）を確かめるには、`group` が `role` の割り当てを知る必要があり、そのままでは依存が循環します。

A. `group` が「削除してよいかを問う口」（インターフェース）を自分の側に持ち、`role` がそれを実装する（依存の向きを逆にする。`group` は `role` を知らない）
B. グループの削除は `role` の側の業務処理が受け持つ（`role` が自分の割り当てを確かめてから `group` の削除を呼ぶ）。グループの削除の API も `role` の側に置く
C. グループへのロールの割り当ては `group` に置く（`group` → `role` の向き。利用者への割り当ては `role`）。利用者のロールを求めるときは、`role` が `group` の割り当ても読む…と循環するため、利用者のロールの和を求める部品を別に置く
X. Other (please specify)

[Answer]: A

---

## Requested Changes Feedback

承認の場で、アーキテクチャの確かめの指摘 R-01〜R-06 を直すことを依頼者が選んだ（Request Changes）。R-01・R-02 は決め方を下の F2・F3 で確かめる。R-03〜R-06 は確かめの求める対応どおりに直す（R-03 は今の `user` の口（`findById`・`findAdminPage`）で足りることを確かめ「変えない」の根拠を書く、R-04 はメニューの項目がスキーマ名とテーブル名でテーブルを指し、`navigation` と置き場の画面の道が同じ鍵を使うと書く、R-05 は設定の階層の表し方と割り当ての一意の鍵を機能設計で決めると注に書く、R-06 は GroupAdminUi の責務にロールの読み取りの表示を足し、答える API を `role` の責務に書く）。

## F2. 権限の YAML の安全な読み込みの置き場（R-01）

今の安全な読み込み（`SafeYamlParser`・`LimitingParser`）は `dsl.parse` にあり、境界テスト（`DslBoundaryArchitectureTest` の `parseAndValidateStayInside`）が `dsl` の外から `dsl.parse` への依存を禁じています。`role` はすでに `dsl` の提供口に依存します。

A. `dsl` の service の層に「上限つきの安全な YAML の読み込み」の口を出し、`role` はその口を使う（`dsl.parse` へは直接依存しない。境界テストは変えない）。上限の値は呼ぶ側が渡す
B. 安全な読み込みを共通の置き場（例: `common/yaml`）へ移し、`dsl` と `role` が使う（境界テストを移した形に直すため、コード生成の計画に明記して依頼者の承認を得る）
C. `role` の中に同じ上限の読み込みを作る（境界テストは変えないが、守りが2か所になる）
X. Other (please specify)

[Answer]: A

## F3. API の分類（公開・ログインだけ・管理者）の持ち方と、確かめるテストの置き場（R-02）

今は `/api/**` の既定が「ログインだけ」で、権限の決まりを書き忘れた API とログインだけの意図の API を見分けられません。AccessControl を新しい機能に依存させないため、部品の依存からは外します。

A. 各 API（コントローラーの口）に分類の印（共通の置き場 `common/security` に定義する注釈）を付け、印の無い API があれば落ちるテストを、全体の構造の検査（既存の `ArchitectureTest` と同じ置き場）に置く。`access` は新しい機能を知らない
B. 分類の一覧（API の道と分類の表）をテストの側に持ち、アプリの全 API と突き合わせるテストを置く（印は付けない）
C. `/api/**` の既定を「拒否」に変え、ログインだけの API も明示の決まりに書く（既存の決まりを変えるため、既存のテストの書き換えが多い）
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

- バックエンドは `role`（ロール・主権限と補助権限の設定・実効の権限の解決・YAML の受け渡し・利用者とグループへの割り当て・作業ロール）と `group`（グループ・メンバー）の2つの新しい機能に分ける（Q1: B）。依存は `role` → `group`（`role` がメンバーを読む）。グループの削除の確かめは、`group` が「削除してよいかを問う口」を持ち `role` が実装する形で、循環させない（F1: A）。
- 作業ロールは `role` の表に利用者ごとに持ち、`user` は役割を知らないまま（Q2: A）。
- 業務のメニューの木は新しい機能 `navigation` が作る。`dsl` の提供口と `role` の解決の口に依存し、「ログインだけ」の API を持つ（Q3: A）。
- 権限の設定の対象は、`role` が `dsl` の提供口を読み（片方向）、DSL の適用の出来事は受けず、画面・解決のたびに今の DSL と名前で照らし合わせる（Q4: A）。
- 画面は機能 `roleadmin`・`groupadmin`・`roletransfer`・`mypermissions`・`tables` に分け、作業ロールの切り替えと業務のメニューは骨組み（app）に置く（Q5: A）。
- 決まっていること（今ある部品の名前、管理者の印は別系統、DSL のスキーマの階層は `dsl`・`dslmanage`、監査は出来事、境界テスト、画面の決まり、実現できるかの判断を ADR に）は、質問のファイルの冒頭のとおり。

- 承認の場の指摘の直し（Requested Changes Feedback）: 権限の YAML の安全な読み込みは `dsl` の service の層に上限つきの口を出し、`role` はその口を使う。境界テストは変えない（F2: A）。API の分類は各 API に `common/security` の注釈で印を付け、印の無い API があれば落ちるテストを既存の `ArchitectureTest` と同じ置き場に置く。AccessControl の依存は外す（F3: A）。R-03〜R-06 は確かめの求める対応どおりに直す。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
