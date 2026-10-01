# Units Generation の質問

承認済みの部品の一覧（`aidlc/spaces/default/intents/260930-user-admin/inception/domain-design/components.md`）と ADR-001〜ADR-008、要件・ストーリーをもとに、Construction で作る単位（Unit）の分け方を決めます。この段は単位どうしの依存（どれがどれを先に要するか）だけを決め、作る順（どれを先に届けるか）は次の Delivery Planning で決めます。

## 決まっていること（質問にしない）

- **配備の形:** 1つの実行可能 WAR（画面の `dist` を同梱）で、1インスタンスの組み込みの H2（`team.md`）。単位は配備の単位ではなく、作って確かめる単位になる。
- **部品の境界:** 部品の境界と持ち主は、承認済みの部品の一覧と ADR のとおり。1つの部品を複数の単位に分けるときは、どの単位がどの口を作るかを単位の定義に書く。
- **単位の種別:** `service`（API を持つ）・`library`（アプリの中で使う部品で単独では動かない）・`ui`（画面）を使う（前の Intent と同じ読み方）。

## 分け方の案

| 案 | 単位 | 中身 |
|---|---|---|
| A（5単位） | U1 利用停止の状態と3つの入口（library） | UserAccount に停止の状態（V9）、Authentication の3つの入口での停止の判定（ADR-008）、リフレッシュトークンのまとめての無効化の口、停止中のログインの監査の理由 |
| | U2 ページ送りの共通化（library） | Paging（`common.paging`）と UiPaging（`src/shared/`）へ移し、招待のサーバーと画面を切り替える |
| | U3 利用者の管理の API（service） | UserAdministration（一覧・印・停止・失敗回数・氏名と言語、拒否の順、最後の管理者の保護と排他）、UserAccount の変更と排他の口、Authentication の失敗回数の取り消しとロックの判定の結果の口、AuditLog の種類と理由、`useradmin` の境界の検査 |
| | U4 管理の画面の 403 の共通の扱い（ui） | ApiClient・AppFrame の 403 の共通の扱いと表示、管理の入口・DSL・招待の画面の置き換え |
| | U5 利用者の管理の画面（ui） | UserAdminUi（一覧・検索・行の操作のメニュー・確かめ・入力・知らせ）、make-you-chic-ui の固定先の更新、E2E の代表の流れ |
| B（3単位） | U1 バックエンド（service） | A の U1・U2 のサーバー・U3 をまとめる |
| | U2 画面の共通（ui） | A の U2 の画面・U4 をまとめる |
| | U3 利用者の管理の画面（ui） | A の U5 |
| C（細かく、8単位ほど） | — | A の U3 を操作ごと（一覧・印・停止・失敗回数・氏名と言語）に分ける |

依存の見込み（A の場合）: U3 は U1・U2 に、U5 は U3・U4・U2 に依存する。U1・U2・U4 はたがいに依存しない。

---

## Q1. 単位の分け方

A. 案 A（5単位）。データと入口の変更（U1）、共通の部品の移設（U2）、管理の API（U3）、画面の共通の扱い（U4）、管理の画面（U5）に分ける
B. 案 B（3単位）。バックエンド・画面の共通・管理の画面に大きく分ける
C. 案 C（8単位ほど）。管理の API を操作ごとに細かく分ける
X. Other (please specify)

[Answer]: A

## Q2. 機能の外の小さな作業の置き場

この Intent には、機能の外の小さな作業が3つあります（Practices Discovery の決定と、画面イメージの段で決まったこと）。

- `frontend/.npmrc` に `ignore-scripts` を入れる
- `.idea/.gitignore` に `dataSources.xml`・`dataSources/` を足す
- make-you-chic-ui の固定先を `3481488` 以降に更新する（Dropdown の押せない項目と理由の文を使うため）

A. 関係の近い単位に入れる。`ignore-scripts` と固定先の更新は画面の単位（案 A なら U5）、`.idea` は最初に作る単位に入れる
B. 3つをまとめた独立の単位（種別 `packaging`）にする
X. Other (please specify)

[Answer]: A

## Q3. 依存の上で並行して作れる単位の扱い

A. 依存の図には、並行して作れる単位を記録するだけにし、実際は1つずつ順に作る前提とする（前の Intent と同じ。順序は Delivery Planning）
B. 依存の無い単位は並行して作る前提とする（作業ブランチを分けて同時に進める）
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめと、分け方の計画（Step 4 の計画の確認を兼ねる）:

- Q1 A: 5つの単位に分ける
  - U1 `u1-user-suspension`（library）: UserAccount に利用停止の状態（V9）、Authentication の3つの入口での停止の判定（停止の確かめをロックの判定より前、ADR-008）、リフレッシュトークンのまとめての無効化の口、停止中のログインの監査の理由。ストーリー US3.2
  - U2 `u2-shared-paging`（library）: Paging（`common.paging`）と UiPaging（`src/shared/`）への移設と、招待のサーバーと画面の切り替え。ストーリーは US1.1 の一部（ページ送り）
  - U3 `u3-user-admin-api`（service）: UserAdministration（一覧・印・停止と解除・失敗回数・氏名と言語、拒否の順、最後の管理者の保護と排他）、UserAccount の変更と排他の口、Authentication の失敗回数の取り消しとロックの判定の結果の口、AuditLog の種類と理由、`useradmin` の境界の検査。ストーリー US1.1・US2.1・US3.1・US4.1・US5.1 のサーバー側
  - U4 `u4-admin-forbidden-ui`（ui）: ApiClient・AppFrame の 403 の共通の扱いと表示、管理の入口・DSL・招待の画面の置き換え。ストーリー US2.2
  - U5 `u5-user-admin-ui`（ui）: UserAdminUi（一覧・検索・行の操作のメニュー・確かめ・入力・知らせ）、E2E の代表の流れ。ストーリー US1.1・US2.1・US3.1・US4.1・US5.1 の画面側
  - 依存: U3 は U1・U2 に、U5 は U2・U3・U4 に依存する。U1・U2・U4 はたがいに依存しない
- Q2 A: 機能の外の小さな作業は関係の近い単位に入れる。`frontend/.npmrc` の `ignore-scripts` と make-you-chic-ui の固定先の更新（`3481488` 以降、専用のコミット）は U5、`.idea/.gitignore` の `dataSources.xml`・`dataSources/` は U1 に入れる（「最初に作る単位」は Delivery Planning で決まるため、U1 に置き、Delivery Planning で最初の Bolt が別の単位になれば移す）
- Q3 A: 並行して作れる単位（U1・U2・U4）は依存の図に記録するだけで、1つずつ順に作る前提とする（順序は Delivery Planning）

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
