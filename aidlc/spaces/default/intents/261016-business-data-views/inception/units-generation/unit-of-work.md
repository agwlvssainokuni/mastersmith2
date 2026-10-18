# 作る単位（Unit of Work）— business-data-views

出典: 部品の一覧 `aidlc/spaces/default/intents/261016-business-data-views/inception/domain-design/components.md`（10部品）と `decisions.md`（ADR-001〜008）、要件 `aidlc/spaces/default/intents/261016-business-data-views/inception/requirements-analysis/requirements.md`、ストーリー `aidlc/spaces/default/intents/261016-business-data-views/inception/user-stories/stories.md`、この段の答え `units-generation-questions.md`（UQ1〜UQ3: A）。単位の順と並べ方は Delivery Planning で決める（この文書は順を勧めない）。

## 単位の一覧

| Unit ID | Directory | 名前 | 種類（kind） | 大きさ | 配備 |
|---|---|---|---|---|---|
| U1 | u1-businessdata-read | 業務データの読み取りの API | service | L | 1つの WAR に同梱（embedded） |
| U2 | u2-businessdata-write | 業務データの書き込みの API | service | L | 1つの WAR に同梱（embedded） |
| U3 | u3-businessdata-screens | 業務データの画面 | ui | L | WAR に同梱する画面の成果物（embedded） |
| U4 | u4-a11y-carryover | アクセシビリティの持ち越し | ui | M | WAR に同梱する画面の成果物（embedded） |
| U5 | u5-carryover-tests | テストと測定の持ち越し | library | S | 配備物を変えない（テストと負荷の試験の台本） |

すべての単位は、フロントエンドの成果物を同梱した1つの実行可能 WAR の中の部品で、別々には配備しない（team.md の Deployment）。種類は、Construction の設計の段で作る文書の範囲を決める（`library` の U5 は業務のモデルや拡張性の文書を持たない）。

## U1 u1-businessdata-read（service、L）

- **境界**: 新しい機能 `businessdata` の読み取りの側。業務データの一覧・検索・詳細の API（`/api/` の下、「ログインだけ」の分類）。
- **持つもの**:
  - 出すカラム・入力できるカラム・許す操作を求める1つの純粋な関数（`businessdata.domain`、ADR-005）。作成・更新・削除の可否もここで求め、応答に操作の可否として返す（書き込みの API は U2）。
  - 判定の順と拒否の code（業務データの `XxxProblemTypes`・`XxxProblemTypeCatalog`、ADR-006）。
  - 行の SQL を組み立てて実行する唯一の部品（RowSqlExecutor）の読み取りの側と、DB の種類ごとの方言（NULL の並び・部分一致のエスケープ・時差つきの型・値の型の対応）（ADR-001・ADR-007）。
  - `targetdb.service` の読み取りの接続の口（今の読み取りの DataSource を渡す口）と、業務データの読み取りの目的の時間の上限。
  - 権限の解決の口（`role.service`）と DSL の提供口（`ActiveDslModelProvider`）の読み取り。
  - `RoleBoundaryArchitectureTest` に `businessdata` を依存してよい相手として足す変更（ADR-002。コード生成の計画で承認を得る）と、`BusinessDataBoundaryArchitectureTest`（依存してよい相手、`prepareStatement`・`createStatement` を呼んでよい場所を RowSqlExecutor だけに絞る）。
  - 行の値を持つ型の伏せ字（`toString`）と `*SecretLeakIT`、SpotBugs の `SQL_` の除外の絞り込み。
- **部品**: BusinessData（読み取り）・TargetDb（読み取りの口）・RoleManagement（境界テストの変更）・DslDefinition（使うだけ）。
- **実装の注意**:
  - 判定の順で、テーブルが NONE なら行の有無にかかわらず 403（AC3.3.6）。書けないカラムを条件・並べ替えに入れたら同じ 400。
  - 一覧は1ページの件数（20・50・100）、全体の件数、`sortable` の1カラムに主キーの順を足した並べ替え、読む行の数の上限。
  - 詳細で2行以上が当たったら「行を1つに決められない」で拒否（監査に残さない）。
  - 3種類の対象DB の結合テスト（team.md）。業務データを読むだけのテストでもスキーマを作って消す。
  - 既存の `packagesJudgedByTotal` のパッケージ（`access.service` など）に手を入れるときは、その Bolt で下限を満たして一覧から外す（team.md）。

## U2 u2-businessdata-write（service、L）

- **境界**: `businessdata` の書き込みの側。作成・更新・削除の API。
- **持つもの**:
  - RowSqlExecutor の書き込みの側（作成・変えたカラムだけの更新・削除、当たった行が1行でなければ巻き戻し）。
  - 入力の検証: DSL の検証と DB の型（必須・長さ・範囲・型）、`pattern` の照合（時間の上限）、一意の事前の問い合わせと DB の一意の制約の違反の読み替え、外部キーの違反の「参照先に無い値」。空の入力の値（文字の型は空の文字列、ほかは NULL）、`password` は入れたときだけ、時差つきの型は UTC。
  - `targetdb` の書き込みの接続の設定と DataSource（`.env` だけから受け取る、設定が無ければ書き込みの口は「設定が無い」と答える）、対象DB 用のトランザクションの管理者（ADR-008）。`.env.example`・見本の対象DB の書き込みのアカウント（DSL の対象のスキーマの行の操作だけ）・README の手順。
  - 監査の出来事（`businessdata.domain`）と `AuditEventType` の種類、`AuditEventListener` での記録（ADR-003）。成功・業務の失敗・接続の失敗・時間切れ（理由の code を分ける）。
- **部品**: BusinessData（書き込み）・TargetDb（書き込みの接続）・AuditLog。
- **実装の注意**:
  - 対象DB を待つあいだ内部DB の接続を持たない。監査は対象DB の確定（失敗なら巻き戻し）の後。
  - 書き込みのテストは、3種類の対象DB でクラスごとにスキーマと表を作り、別の接続で読み直して確かめる（team.md）。
  - 既存の `AuditSecretLeakIT` の列の一覧が変わりうる（前の Intent の学び）。

## U3 u3-businessdata-screens（ui、L）

- **境界**: `frontend/src/features/tables` を引き継いで広げる業務データの画面（S1〜S6）と、`src/shared/` の部品。
- **持つもの**:
  - 一覧（検索の領域・表・ページ送り・1ページの件数の選択）・詳細・作成・更新の画面と削除の確かめ、開けない・つながらないときの表示（ADR-004）。
  - `src/shared/` の NULL の表示・Table の横のスクロールの枠・1ページの件数の選択・保存せずに離れるときの確かめ（依頼（5）の取り込みまでの手当てを含む）。
  - 業務データの API の呼び出しと応答の型（ApiClient。`BIGINT`・`DECIMAL` は文字のまま、`any` を使わない）。
  - 一覧の状態を画面の中で保つ仕組み（SM5）。
  - 機能の登録（`registration.ts`）と道の振り分け、テーブルを指すメニューの項目からの道（今の置き場の置き換え）。
- **部品**: BusinessDataScreens・SharedUi・ApiClient・AppShell（道の登録）。
- **実装の注意**:
  - 出すカラム・入力・操作の入口は、サーバーが返す操作の可否で決める（画面で判定し直さない）。
  - 業務データの代表の E2E 1本（見本の PostgreSQL を前提）と、axe（ブランドカラーとテーマのすべての組、誤りの状態・100 カラムの表を含む）。
  - 今の置き場の E2E と単体テスト（K-52）を新しい形に合わせる（本数に数えない）。

## U4 u4-a11y-carryover（ui、M）

- **境界**: 前の Intent からのアクセシビリティの持ち越し（FR14）。
- **持つもの**:
  - make-you-chic-ui の固定先を `a9309c4` から `b0a1e79` に上げる（承認を得た専用のコミット、上流の `main` から辿れること・vendor の lockfile の差の記録、fast-forward の統合）。
  - `src/shared/` の「操作: <行を見分ける値>」の名前の関数と、既存の管理の一覧（`useradmin`・`groupadmin`・`roleadmin`）の行の「操作」のトリガーの直し。
  - E2E 080 の違反の要素を axe-core 4.14 で確かめ、トップバーの利用者のメニューのトリガーの名前を、今の Topbar の口で frontend の側から直せるなら直し、直せなければ依頼（5）を送って取り込む。
  - axe-core を 4.14 以降に上げ、`.github/dependabot.yml` の `ignore` を外す（E2E 全件の通過と違反 0 件、規則・要素を外さない）。上流が間に合わなければ、引き上げだけを持ち越すかを諮る。
- **部品**: AdminLists・SharedUi（名前の関数）・AppShell（トップバー）。
- **実装の注意**: 依頼（5）の文面は `aidlc/spaces/default/intents/261016-business-data-views/make-you-chic-ui-request-5.md`（機能設計の承認の前に出す）。

## U5 u5-carryover-tests（library、S）

- **境界**: ストーリーにしない持ち越し（FR15・FR16）。部品を増やさない。
- **持つもの**:
  - 不安定なテスト2件の直し: `RoleConcurrencyIT` の #7（監査の行の ID の順に頼る形をやめる。K-57）、`AdminForbiddenProvider.test.tsx`（効果の回数を `waitFor` で待つ。K-58）。直したことの確かめは team.md の二段。
  - `dslMixed` の `memory.peak` の測り直し: 物差しは `memory.peak` のまま、`memory.stat` の `anon` の最大を並べて測る手順を `perf/` に足し、ページキャッシュを減らす手（H2 のファイルの読み書きの見直しなど）を試す。コンテナの上限 2g は上げない（FR16）。
- **部品**: なし（RoleManagement と AppShell のテスト、`perf/` の台本と手順）。
- **実装の注意**: 測定の実行は Build and Test で行う（使い捨ての環境、`caffeinate -dims`、`project.md` の学び）。

## 単位と部品の対応

| 部品（components.md） | U1 | U2 | U3 | U4 | U5 |
|---|---|---|---|---|---|
| BusinessData | 読み取りの側・出し分けの関数・SQL の部品・方言 | 書き込みの側・検証・監査の出来事 | — | — | — |
| TargetDb | 読み取りの接続の口 | 書き込みの接続の設定と DataSource | — | — | — |
| RoleManagement | 境界テストの変更 | — | — | — | テスト（RoleConcurrencyIT） |
| DslDefinition | 使うだけ | 使うだけ | — | — | — |
| AuditLog | — | 監査の種類と記録 | — | — | — |
| ApiClient | — | — | 業務データの API の呼び出し | — | — |
| BusinessDataScreens | — | — | 画面 | — | — |
| SharedUi | — | — | NULL・枠・件数・離れる確かめ | 「操作: …」の名前の関数 | — |
| AdminLists | — | — | — | 行の「操作」の名前の直し | — |
| AppShell | — | — | 道の登録 | トップバー | テスト（AdminForbiddenProvider） |
