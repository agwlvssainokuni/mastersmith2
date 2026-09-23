# Units Generation — 質問

この段では、Domain Design の部品（`inception/domain-design/components.md`）を、Construction で1つずつ設計・実装する「単位」にまとめ、単位どうしの依存を決めます。どの単位から作るか（順序）は、次の次の Delivery Planning で決めるため、ここでは聞きません。

前提:
- 配備は今までどおり、画面を同梱した実行可能 WAR 1つのアプリ（`aidlc/spaces/default/memory/team.md` の Code Style）。単位はすべてこの1つのアプリに組み込まれる。
- 部品の依存は `dslmanage`（DefaultDslGeneration・DslPreviewAnalysis・DslLifecycle）→ `dsl`（DslDefinition・ActiveDslModel）・`targetdb`（TargetDatabase）の一方向（ADR-009）。
- 既存の部品のうち、AuditLog と ApiClient は今回広げる。

---

## Q1. 単位の分け方

A. 部品のまとまりごとに5つにする: U1 対象DB（TargetDatabase）、U2 DSL の定義（DslDefinition・ActiveDslModel）、U3 既定の DSL の生成（DefaultDslGeneration）、U4 DSL の管理（DslLifecycle・DslPreviewAnalysis・AuditLog の拡張）、U5 DSL の管理画面（DslAdminUi・ApiClient の拡張）
B. パッケージごとに3つにする: U1 対象DB、U2 DSL の定義、U3 DSL の管理（生成・プレビュー・適用・監査・画面をまとめる）
C. ストーリーの流れで縦に切る（読み込み・投入・プレビュー・適用・履歴の単位ごとに、バックエンドと画面をまとめる）。同じ部品を複数の単位が触る
X. Other (please specify)

[Answer]: A

## Q2. 画面を独立した単位にするか

A. 画面（DslAdminUi と ApiClient の拡張）は1つの単位にし、DSL の管理（API）の単位に依存させる
B. 画面は分けず、DSL の管理の単位に含める（バックエンドと画面を同じ単位で作る）
X. Other (please specify)

[Answer]: A

## Q3. 監査の拡張（AuditLog）を置く単位

AuditLog の拡張は、監査の表への列の追加（Flyway の移行）と出来事の種類の追加です。

A. DSL の管理の単位に含める（出来事を出す側と同じ単位で作り、試す）
B. 小さな独立した単位にする（監査の表の拡張だけを先に作れるようにする）
X. Other (please specify)

[Answer]: A

## Q4. 依存の無い単位を並行して作るか

1人と AI で作るため、並行して作る場合は AI の作業を並べて進めることになります。

A. 依存の無い単位（例: 対象DB と DSL の定義）は並行して作れる形にしておく（実際に並行するかは Delivery Planning で決める）
B. 依存の順に1つずつ作る前提にする
X. Other (please specify)

[Answer]: B

---

## Consolidated Summary Confirmation

- 単位は部品のまとまりごとに5つにする（Q1: A）
  - U1 対象DB（TargetDatabase）— 種別 library、依存なし
  - U2 DSL の定義（DslDefinition・ActiveDslModel）— 種別 library、依存なし
  - U3 既定の DSL の生成（DefaultDslGeneration）— 種別 library、U1・U2 に依存
  - U4 DSL の管理（DslLifecycle・DslPreviewAnalysis・AuditLog の拡張）— 種別 service、U1・U2・U3 に依存
  - U5 DSL の管理画面（DslAdminUi・ApiClient の拡張）— 種別 ui、U4 に依存
- 画面は独立した単位（U5）にし、DSL の管理の単位（U4）に依存させる（Q2: A）
- 監査の拡張（監査の表の列と出来事の種類の追加）は、DSL の管理の単位（U4）に含める（Q3: A）
- 単位は依存の順に1つずつ作る前提にする。依存の図には U1 と U2 が互いに依存しないことを記録するが、並行して作ることはしない（Q4: B）
- すべての単位は、画面を同梱した実行可能 WAR 1つのアプリに組み込まれる

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
