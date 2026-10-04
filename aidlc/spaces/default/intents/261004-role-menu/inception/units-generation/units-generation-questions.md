# Units Generation の質問

承認済みの部品の一覧（`aidlc/spaces/default/intents/261004-role-menu/inception/domain-design/components.md`、18 部品）と ADR-001〜008 をもとに、作る単位（Unit）と依存の形を決めます。作る順（どれを先に届けるか）は、次の Delivery Planning で決めます。

## 決まっていること（質問にしない）

- **配備の形**: アプリは今までどおり1つの実行可能 WAR（フロントエンドのビルド結果を同梱）で、すべての単位はその中に入る（`team.md` の Deployment）。単位は「別々に配備するもの」ではなく、設計と作業のまとまり。
- **部品の境界**: `role` → `group`（問う口で循環させない、ADR-002）、`navigation` → `dsl`・`role`、`role` → `dsl`（安全な YAML の読み込みの口、ADR-007）。画面は `ApiClient` を通す。
- **大きい単位**: 設計の段は1つの単位として通し、コード生成の計画で Bolt に分けてよい（`project.md` の学び）。
- **make-you-chic-ui**: 入れ子のサイドバーは上流への追加を先に依頼し、取り込みは固定先の更新の専用のコミット（`team.md`）。

## 部品のまとまりの案

| 案の単位 | 含む部品 | 種別の案 |
|---|---|---|
| dsl-v2 | DslDefinition・DslManagement・DslAdminUi（DSL の版 2、メニューの深さ、安全な YAML の読み込みの口） | library |
| group | GroupManagement | service |
| role | RoleManagement（ロール・設定・解決・割り当て・作業ロール・受け渡し）・AuditLog の種類の追加 | service |
| navigation | Navigation | service |
| role-admin-ui | RoleAdminUi・GroupAdminUi・RoleTransferUi・UserAdminUi の表示・SharedTreeView | ui |
| app-frame-ui | AppFrame（作業ロールの切り替え・業務と管理のメニュー）・MyPermissionsUi・TablePlaceholderUi | ui |

質問は4問です。

---

## Q1. 単位の細かさ

A. 上の表の6つの単位にする
B. もっと大きくまとめ、4つにする（dsl-v2・バックエンドの役割と権限（group・role・navigation）・管理の画面・骨組みの画面）
C. もっと細かくし、画面を機能ごと（roleadmin・groupadmin・roletransfer・mypermissions・tables・骨組み）に分ける（9〜10 単位）
X. Other (please specify)

[Answer]: A

## Q2. `role` の単位の大きさ

`role` は XL の見込みです（ロール・設定と解決・割り当て・作業ロール・YAML の受け渡し）。

A. 1つの単位のまま設計を通し、コード生成の計画で Bolt に分ける（前の Intent と同じ）
B. 権限の YAML の受け渡しを別の単位（role-transfer）に分ける
C. 解決の口と作業ロールを別の単位に分ける（後の Intent の依存先を小さく見せる）
X. Other (please specify)

[Answer]: A

## Q3. 横断の準備（API の分類の印、画面の機能どうしの import の制限、既存の違反1件の直し）の置き場

ADR-008（既存のすべての API に分類の印を付け、構造の検査を足す）と、`team.md` の ESLint の制限（最初に手を入れる Bolt で今の違反を直す）は、どの単位にも属さない横断の準備です。

A. 独立した単位（例: cross-cutting、種別 library）にする
B. いちばん先に作る単位（Delivery Planning で決まる）に入れる
C. API の分類は `role` に、ESLint の制限は `role-admin-ui` に入れる
X. Other (please specify)

[Answer]: A

## Q4. make-you-chic-ui の固定先の更新の置き場

A. 入れ子のサイドバーを使う `app-frame-ui` の単位に入れる（取り込みはその単位の Bolt の前の専用のコミット）
B. 独立した単位（例: design-system-update、種別 packaging）にし、`app-frame-ui` がそれに依存する
X. Other (please specify)

[Answer]: A

---

## Requested Changes Feedback

承認の場で、アーキテクチャの確かめの指摘 R-01〜R-04 を推奨の案で直すことを依頼者が選んだ（Request Changes）。

- R-01・R-02: 共有の木（`shared/tree`）と、画面の登録の型の拡張（AccessLevel・VisibleWhen）を U1 cross-cutting に移す。U6 と U7 は互いに依存せず、両方が U1 に依存する（循環を出さない）。
- R-03: U1 が付けるのは既存の API への印だけとし、U2〜U5 が足す API はそれぞれの単位で印を付ける。パッケージごとの下限の作業は、そのパッケージに手を入れる単位が持つ。
- R-04: U2 の画面（`features/dsl`）の部分は、機能設計で `frontend-components.md` を作って設計する。

---

## Consolidated Summary Confirmation

分け方の計画（Step 4 の計画の確認をこのまとめの確認に含める）:

| 単位 | 含むもの | 種別 | 大きさの見込み | 依存（topology のみ） |
|---|---|---|---|---|
| U1 cross-cutting | 既存のすべての API への分類の印（`common/security` の注釈）と構造の検査（ADR-008）、ESLint の機能どうしの import の制限と既存の違反1件の直し、共有の木（`shared/tree`）、画面の登録の型の拡張 | library | M | なし |
| U2 dsl-v2 | DslDefinition・DslManagement・DslAdminUi（DSL の版 2・スキーマの階層・メニューの深さの上限・安全な YAML の読み込みの口） | library | L | なし |
| U3 group | GroupManagement（グループ・メンバー・削除してよいかを問う口の定義）と監査の種類 | service | M | U1 |
| U4 role | RoleManagement（ロール・設定・解決・割り当て・作業ロール・権限の YAML の受け渡し・問う口の実装）と監査の種類 | service | XL（コード生成の計画で Bolt に分ける） | U1・U2・U3 |
| U5 navigation | Navigation（業務のメニュー・テーブルの置き場の権限の問い合わせ） | service | M | U1・U2・U4 |
| U6 role-admin-ui | RoleAdminUi・GroupAdminUi・RoleTransferUi・UserAdminUi の表示 | ui | L | U1・U3・U4 |
| U7 app-frame-ui | AppFrame（作業ロールの切り替え・業務と管理のメニュー）・MyPermissionsUi・TablePlaceholderUi・make-you-chic-ui の固定先の更新 | ui | L | U1・U4・U5 |

- 単位は6つの案に横断の準備を独立させた7つ（Q1: A・Q3: A）。`role` は1つの単位のまま設計を通し、コード生成の計画で Bolt に分ける（Q2: A）。make-you-chic-ui の固定先の更新は `app-frame-ui` に入れる（Q4: A）。
- すべて1つの WAR に入る（配備の形は今までどおり）。作る順は Delivery Planning で決める。

- 承認の場の指摘の直し（Requested Changes Feedback）: 共有の木と登録の型の拡張を U1 に移し、U6・U7 は U1 に依存する。U1 は既存の API への印だけ。U2 の画面は機能設計で frontend-components.md を作る。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
