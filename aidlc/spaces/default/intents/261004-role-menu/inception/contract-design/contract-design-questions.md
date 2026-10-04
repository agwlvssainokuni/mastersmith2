# Contract Design の質問

承認済みの単位（`aidlc/spaces/default/intents/261004-role-menu/inception/units-generation/`、7単位）の依存の線と、画面と API の境界について、契約（何を・どの形で・どの手段で受け渡し、失敗したらどうするか）を決めます。

## 決まっていること（質問にしない）

- **外部の利用者**: 管理の API・自分の API を使うのは、このアプリの画面だけ。版の番号は付けず、画面と同じ WAR で一緒に変える（前の Intent と同じ）。後の Intent（業務データの画面 J・K）が使う実効の権限の解決の口は、アプリの中の外部の利用者向けの契約として書く（前の Intent の C8 と同じ扱い）。
- **誤りの形**: RFC 9457 の Problem Details に、安定した `code` を足した形。1つの code に1つの状態コードを固定する。code の定数は `<機能>.domain.XxxProblemTypes`、一覧は `<機能>.service.XxxProblemTypeCatalog`。入力の誤り（`VALIDATION_FAILED`）など共通の code は使い回す（`team.md` の Code Style）。
- **業務処理の結果**: 想定内の失敗は結果の型（sealed interface の record）で返し、controller が網羅の `switch` で応答か `BusinessException` に変える（`team.md`）。
- **応答の形の前例**: 一覧は `{ items, page, size, total }`、日時は ISO 8601 の時点（`Instant`）、項目名は camelCase。操作の成功は本文なしの 204、作成は 201（前の Intent と同じ）。
- **ログの決まり**: `TraceAspect` が web・service・domain・repository の引数と戻り値を TRACE で文字列にするため、メールアドレス・氏名・検索の文字は文字列にすると伏せ字になる型（`RedactedText`・`SearchText` の形）で受け渡す（`project.md` の学び）。
- **単位の間（同じアプリの中）の契約**: service の口（Java の型）と、出来事（Spring の出来事）で受け渡す。監査は出来事を受けて確定の後に記録する。
- **部品の境界**: ADR-001〜008（`role` → `group` は問う口で循環させない、`role`・`navigation` は `dsl` の提供口を読む、安全な YAML の読み込みは `dsl` の service の口、API の分類は `common/security` の注釈）。
- **パスの形**: 管理は `/api/admin/roles`・`/api/admin/groups`・`/api/admin/role-transfer`、自分は `/api/me/work-role`・`/api/me/permissions`・`/api/me/navigation`、テーブルの置き場の問い合わせは `/api/me/tables/{schemaName}/{tableName}/access`（名前はエンコード）。操作は前の Intent と同じく、資源への POST・PUT・DELETE と、操作ごとの下位パスへの POST の組み合わせ。

質問は3問です。

---

## Q1. 権限の YAML の import の「確かめ」と「適用」の結びつけ方（FR7.3a）

適用のときに検証し直し、確かめた一覧と違えば拒否します。どう結びつけるかです。

A. 確かめの応答に、変わる点の一覧から作った指紋（ハッシュ）を含める。適用は同じ YAML と指紋を送り、サーバーは YAML を検証し直して一覧を作り直し、指紋が一致しなければ拒否する（サーバーは確かめの結果を保存しない）
B. 確かめの結果（YAML と一覧）を内部DB に一時的に保存し、確かめの ID を返す。適用は ID を送り、サーバーは保存した YAML で検証し直して一覧を比べる（DSL のプレビューと同じ考え）
C. 適用は YAML だけを送り、サーバーは検証し直した一覧を画面に返して、画面がもう一度確かめる（2回目の確かめを挟む）
X. Other (please specify)

[Answer]: A

## Q2. 実効の権限の解決の口（後の Intent とメニュー向け）の形

A. 1回の要求の中で作る「作業ロールの権限の写し」（その利用者の作業ロールの設定をまとめて1回読む）を返し、写しに対象（スキーマ・テーブル・カラム）ごとに問い合わせる形にする。メニューの絞り込みや一覧の画面が、問い合わせのたびに DB を読まない
B. 対象ごとに呼ぶ口（利用者 ID と対象を受け、実効の主権限と補助権限を返す）だけにする
C. A と B の両方を出す（B は A の写しを使う）
X. Other (please specify)

[Answer]: C

## Q3. 作業ロールを画面がどう知るか（AC4.1.17）

A. 既存の「今の利用者」の応答（`/api/me` の応答）に、作業ロール（ID と名前）と自分のロールの一覧を足す。画面はログイン・トークンの更新の後にこれを読む
B. 作業ロール専用の `GET /api/me/work-role` を足し、自分のロールの一覧と今の作業ロールを返す。既存の「今の利用者」の応答は変えない
C. ログインとトークンの更新の応答そのものに作業ロールを含める
X. Other (please specify)

[Answer]: B

---

## Requested Changes Feedback

承認の場で、アーキテクチャの確かめの指摘 R-01〜R-08 を推奨の直し方で直すことを依頼者が選んだ（Request Changes）。

- R-01: C3 は今の `ActiveDsl`（Present・Absent）のまま書き、版 2 で直す既存の利用者（dslmanage の生成・DSL の画面の API・保存済みの DSL の読み込み・`DslFormat.CURRENT_VERSION`）を U2 の作業として列挙する。
- R-02: 監査の表と `AuditEvent` の変更（足す列・Flyway の移行・ファクトリー・`detail` の形と大きさの上限・伏せる規則）は先に作る U3 が持ち、U4 は種類を足す。
- R-03: 「有効な作業ロール」は解決の口（C5）が決め（保存値が自分のロールでなければ決めた順の最初に読み替える）、`GET /api/me/work-role` は読み取りだけで保存を書き換えない。順序の決め方は機能設計の必須の入力とする。
- R-04: 安全な YAML の読み込みの口は、既存の `SafeYamlParser`・`YamlParseResult` を包み、上限を引数にし、DSL の誤りの型に依らない結果の型を返すと書く。
- R-05: 今の DSL に無い対象の設定を消す操作は、本文の無い形（下位パスへの POST）にする。
- R-06: 作業ロールの切り替えで、存在しないロールと自分に無いロールを同じ `ROLE_NOT_ASSIGNED`（409）にそろえる。
- R-07: import の大きさ超過は既存の `PAYLOAD_TOO_LARGE`（413）を使い回し、multipart の上限の設定は U4 が持つと書く。
- R-08: 注釈と安全の決まりの食い違いの検査は `AdminPaths` の判定を正とし、両方向を検査すると書く。

---

## Consolidated Summary Confirmation

- 権限の YAML の import は、確かめの応答に変わる点の一覧の指紋（ハッシュ）を含め、適用は同じ YAML と指紋を送る。サーバーは YAML を検証し直して一覧を作り直し、指紋が一致しなければ拒否する。確かめの結果は保存しない（Q1: A）。
- 実効の権限の解決の口は、1回の要求の中で作る「作業ロールの権限の写し」と、対象ごとに呼ぶ口の両方を出す。対象ごとの口は写しを使う（Q2: C）。
- 作業ロールは専用の `GET /api/me/work-role`（自分のロールの一覧と今の作業ロール）で画面に渡す。既存の「今の利用者」の応答とログインの応答は変えない（Q3: B）。
- 決まっていること（外部の利用者、誤りの形と code、結果の型、応答の形、ログの伏せ字の型、単位の間は service の口と出来事、ADR-001〜008、パスの形）は質問のファイルの冒頭のとおり。

- 承認の場の指摘の直し（Requested Changes Feedback）: R-01〜R-08 を推奨の直し方で直す（C3 は ActiveDsl のまま、監査の変更は U3、有効な作業ロールは解決の口、ほか）。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
