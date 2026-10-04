# 作る単位（Unit of Work）— role-menu

出典: 部品の一覧 `aidlc/spaces/default/intents/261004-role-menu/inception/domain-design/components.md`（18 部品）、ADR `decisions.md`（ADR-001〜008）、要件 `requirements.md`、ストーリー `stories.md`、この段の答え `units-generation-questions.md`（UQ1〜UQ4、まとめの確認で分け方の計画を承認）。

## 単位の一覧

| Unit ID | Directory | 名前 | 種別（kind） | 大きさの見込み | 配備の形 |
|---|---|---|---|---|---|
| U1 | u1-cross-cutting | cross-cutting | library | M | 組み込み（WAR の中） |
| U2 | u2-dsl-v2 | dsl-v2 | library | L | 組み込み（WAR の中） |
| U3 | u3-group | group | service | M | 組み込み（WAR の中） |
| U4 | u4-role | role | service | XL | 組み込み（WAR の中） |
| U5 | u5-navigation | navigation | service | M | 組み込み（WAR の中） |
| U6 | u6-role-admin-ui | role-admin-ui | ui | L | 組み込み（WAR に同梱する画面） |
| U7 | u7-app-frame-ui | app-frame-ui | ui | L | 組み込み（WAR に同梱する画面） |

- アプリは今までどおり1つの実行可能 WAR（画面のビルド結果を同梱）で、すべての単位はその中に入る（`team.md` の Deployment）。単位は設計と作業のまとまりで、別々に配備しない。種別の `service` は、WAR の中で API を持つ単位を指す。
- 作る順は Delivery Planning で決める（この段は依存の形だけを決める）。

## U1 cross-cutting（library、M）

- **説明**: どの機能にも属さない横断の準備。
- **持ち物**:
  - API の分類の注釈（公開・ログインだけ・管理者）を `common/security` に定義し、既存のすべての API（コントローラーの口）に付ける。印の無い API と、印と安全の決まりの食い違いを落とす構造の検査を、既存の `ArchitectureTest` と同じ置き場に置く（ADR-008、NFR1.3、AC1.1.15）。
  - 画面の機能どうしの直接の import を ESLint の `no-restricted-imports` で止め、既存の違反1件（`features/registration/useRegistration.ts` → `../auth/authSession`）を直す（`team.md` の Code Style、要件 C5）。
  - 共有の木 `shared/tree`（SharedTreeView。U6 の権限の設定と U7 の自分の権限で使う）（この段の確かめの直し R-01）。
  - 画面の登録の型（`app/registry/types.ts` の AccessLevel・VisibleWhen など）の拡張。U6 の管理のメニューへの登録と、U7 の骨組みの組み立ての両方がこの型を使う（R-02）。
- **境界**: U1 が印を付けるのは既存の API だけ。新しい機能（`group`・`role`・`navigation`）や U2 が足す・変える API への印は、それぞれの単位で付ける（注釈の定義と検査は U1）。印の対象外の入口（actuator・画面の静的配信・フィルターが受けるログインなど）の扱いは機能設計で決める（ドメイン設計の確かめの R-07）。
- **注意**: 既存の API に印を付けると、全体の合計で判定しているパッケージ（`common.web`・`access.service` など、`packagesJudgedByTotal`）の本体に手が入りうる。パッケージごとの下限の作業（下限を満たして一覧から外す、`team.md`）は、そのパッケージの本体に手を入れる単位が持つ。U1 と U2〜U5 が同じパッケージに手を入れるときは、先に手を入れた単位が下限の作業を持ち、作る順は Delivery Planning で決める（R-03）。`access` の本体に手を入れるなら `AccessBoundaryArchitectureTest` を足す。

## U2 dsl-v2（library、L）

- **説明**: DSL の書式の版 2（スキーマの階層）とメニューの深さの上限、安全な YAML の読み込みの口。
- **持ち物**:
  - `dsl`: 型・JSON Schema・検証を版 2 に広げる（スキーマ名・表示名と、その下のテーブル。形は複数を受け付ける）。版 1 は書式の版の誤り。メニューの項目はスキーマ名とテーブル名の組でテーブルを指す。メニューの深さの上限（新しい投入と復元では拒否、起動時の読み直しでは深さだけ外して深すぎる枝を出さず警告）（ADR-005、US5.2・US6.1）。service の層に上限つきの安全な YAML の読み込みの口（ADR-007）。
  - `dslmanage`: 既定の DSL を版 2 で生成し、対象DB の設定のスキーマ名を書く。版 1 の履歴の復元は 4xx で拒否する。
  - 画面（`features/dsl`）: 版 2 の誤りの文言、スキーマの階層の表示（S10）。
- **境界**: 役割・権限を知らない（`role`・`navigation` から片方向で読まれる）。DSL の既存のテスト（上限・タグ・重複キー・`$ref`）を版 2 の形に書き換える（AC6.1.10）。
- **注意**: 種別は library だが、画面（`features/dsl`）の小さな変更を持つ。画面の部分は機能設計で `frontend-components.md` を作って設計する（library の単位に画面の成果物が作られない抜けを防ぐ。R-04）。

## U3 group（service、M）

- **説明**: グループとメンバー。
- **持ち物**: `group` の機能（グループの作成・名前の変更・削除、メンバーの足し外し、メンバーの読み取りの口、削除してよいかを問う口（インターフェース）の定義）、管理の API（`/api/admin/` の下、U1 の注釈で印を付ける）、グループの監査の種類と出来事（`audit` に種類を足す）、`GroupBoundaryArchitectureTest`（US2.1、FR4.1・FR4.1a）。
- **境界**: `role` を知らない（ADR-002）。問う口の実装は U4。利用者の確かめは既存の `user` の口（`findById`・`findAdminPage`）。

## U4 role（service、XL）

- **説明**: ロール・主権限と補助権限の設定・実効の権限の解決・利用者とグループへの割り当て・作業ロール・権限の YAML の受け渡し。
- **持ち物**: `role` の機能（FR1〜FR8・FR12 の役割・権限の部分）、管理の API（`/api/admin/` の下）と作業ロール・自分の権限の API（`/api/me` の下、ログインだけ）、U3 の問う口の実装、グループに割り当てたロールと利用者のロールの読み取りの API（画面 S6・S9 向け）、監査の種類と出来事、`RoleBoundaryArchitectureTest`（US1.1・US1.2・US2.2・US3.1・US4.1・US4.2）。
- **境界**: `group`（メンバー）・`user`（既存の口）・`dsl`（提供口と安全な YAML の読み込みの口）に依存する（ADR-001・ADR-004・ADR-007）。トークンには権限を入れない（ADR-003）。
- **注意**: 大きいため、設計の段は1つの単位として通し、コード生成の計画で Bolt に分ける（UQ2: A、`project.md` の学び）。ストーリーの確かめで出た切れ目の案（設定と解決、割り当てと作業ロール、受け渡し）を Delivery Planning の入力にする。

## U5 navigation（service、M）

- **説明**: 業務のメニューの木を作業ロールの実効の主権限で絞って返す。テーブルの画面の置き場の権限の問い合わせ。
- **持ち物**: `navigation` の機能（FR9.1〜FR9.3・FR9.5・FR10）、`/api/me` の下のログインだけの API、`NavigationBoundaryArchitectureTest`（US5.1）。
- **境界**: `dsl`（menus）と `role`（解決の口）に依存する（ADR-001）。管理のメニューは返さない（画面の登録）。

## U6 role-admin-ui（ui、L）

- **説明**: 管理の画面（ロール・グループ・権限の受け渡し）と、既存の利用者の管理の画面へのロールの表示。
- **持ち物**: `features/roleadmin`（S3・S4・S5）、`features/groupadmin`（S6）、`features/roletransfer`（S7）、`features/useradmin` の詳細へのロールの読み取りの表示（S9）、管理のメニューへの登録（ロール・グループ・権限の受け渡し。登録の型は U1 が広げる）。
- **境界**: API は `ApiClient` を通す。機能どうしは直接 import しない（U1 の ESLint の制限）。共有の木と登録の型は U1 のものを使い、U7 には依存しない。

## U7 app-frame-ui（ui、L）

- **説明**: 骨組みの画面（作業ロールの切り替え、業務と管理のメニュー）と、自分の権限・テーブルの置き場の画面。
- **持ち物**: 骨組み（`app`）の作業ロールの切り替え（トップバー）、業務と管理のメニュー（サイドバー、N 階層。登録の型は U1 が広げたものを使う）（S1）、`features/mypermissions`（S8、Should）、`features/tables`（S2）、make-you-chic-ui の固定先の更新（入れ子のサイドバーを取り込む専用のコミット、UQ4: A）。
- **境界**: 入れ子のサイドバーは make-you-chic-ui への依頼が前提で、間に合わないときに自前に切り替えるかは、その時点で依頼者に諮る（`team.md`）。共有の木と登録の型は U1 のものを使い、U6 には依存しない。

## 単位と部品の対応

| 部品（components.md） | 単位 |
|---|---|
| AccessControl（API の分類の検査の置き場の調整）・SharedTreeView・AppFrame の登録の型の拡張 | U1 |
| DslDefinition・DslManagement・DslAdminUi | U2 |
| GroupManagement | U3 |
| RoleManagement・AuditLog（役割・権限の種類） | U4（AuditLog のグループの種類は U3） |
| Navigation | U5 |
| RoleAdminUi・GroupAdminUi・RoleTransferUi・UserAdminUi | U6 |
| AppFrame（作業ロールの切り替え・メニュー）・MyPermissionsUi・TablePlaceholderUi | U7 |
| UserAccount・ApiClient | 変えない（読み取りの口・呼び出しの部品として使う） |
