# Domain Design の質問

承認済みの要件・ストーリー・画面イメージと、コード知識ベース（とくに K-1〜K-9、`dependencies.md` の K-3）をもとに、部品の境界と持ち主を決めます。

## 決まっていること（質問にしない）

- **今ある部品は名前を変えない:** 利用者（`user`）・認証（`auth`）・認可（`access`）・監査（`audit`）・招待（`invitation`）と、画面の骨組み。前の Intent の Domain Design の部品名は UserAccount・Authentication・AccessControl・AuditLog・AppFrame・ApiClient などで、今回もこの名前で書く。
- **境界の決まり（ArchUnit）:**
  - `user` は `auth` を知らない。
  - `auth`・`user` は `audit` を知らない（監査は出来事で知らせる）。
  - `invitation` の外から `invitation` に依存してよいのは `audit` だけ。
  - 既存の境界の検査を緩める・変えるときは、コード生成の計画に明記して依頼者の承認を得る（`team.md`）。
- **監査:** 監査は出来事を受けて、確定の後に別のトランザクションで記録する（今の仕組み）。業務の理由で拒否した操作も、巻き戻しの後に失敗として残す。
- **管理の API:** `/api/admin/` の下に置き、安全の決まりは足さない（K-5）。
- **ロックの状態:** `auth` の表（`login_attempt_states`）にあり、時刻で判定する。失敗回数の取り消しは、ログインの判定と同じ行の排他を使う（K-2）。
- **画面:** 1つの画面を新しい機能として `features/<id>/` に置き、機能どうしで直接 import しない（`team.md`）。
- **make-you-chic-ui の Dropdown:** 押せない項目と理由の文は取り込み済み（`3481488`）。固定先の更新は、Delivery Planning とコード生成の計画で扱う。
- **実現できるかの判断:** classic の範囲で実現可能性の評価の段が無いため、この段の ADR にまとめる（`project.md` の学び）。対象は、組み込みの H2 での最後の管理者の保護の同時性、リフレッシュトークンのまとめての無効化、管理の画面すべての 403 の扱い。

質問は5問です。

---

## Q1. 利用者の管理の操作をまとめる部品の置き場

一覧（利用者＋ロックの状態）と、印・停止・失敗回数・氏名と言語の操作をまとめる部品が要ります。`user` は `auth` を知らないため、`user` には置けません（K-3）。

A. 新しい部品（例: UserAdministration、パッケージ `useradmin`）を作る。`user` と `auth` の service の口を使い、監査には出来事で知らせる。新しい境界の検査を足す（`access`・`dslmanage` と同じく、`auth` に依存してよい側になる）
B. `auth` の側に置く（`auth` は既に `user` の service の口に依存している。`auth` の責務が「ログインとトークン」から「利用者の管理」まで広がる）
C. 既存の `access`（認可）に置く（管理者の判定と近いが、`access` は今は判定と入口だけを持つ）
X. Other (please specify)

[Answer]: A

## Q2. 利用停止の状態の持ち主

利用停止を表す状態は、今どこにもありません（K-1）。3つの入口（ログインの照合・トークンの更新・アクセストークンの認証）が、この状態を読みます。

A. 利用者（UserAccount）が持つ（`users` の表に停止の状態を足す。管理者の印と同じ場所で、入口は今と同じく `user` の service の口から読む）
B. 認証（Authentication）が持つ（`auth` の表に足す。入口はすべて `auth` にあるが、一覧は `user` と `auth` の両方から読むことになる）
C. Q1 の新しい部品が持つ（新しい表。入口が新しい部品を読むことになり、依存の向きが増える）
X. Other (please specify)

[Answer]: A

## Q3. 止めたときのリフレッシュトークンの無効化の起こし方

止めたときは、その利用者のリフレッシュトークンをすべて無効にします（FR3.3）。リフレッシュトークンは `auth` の表にあります。

A. 利用者の管理の部品（Q1）が、止める処理の中で `auth` の service の口を直接呼ぶ（同じトランザクション。処理の流れが1か所で読める）
B. 停止の出来事（例: UserSuspended）を出し、`auth` が受けて同じトランザクションで無効にする（`UserCreatedEvent` → ロックの状態の行を作る、と同じ形。`auth` は止める側を知らなくてよい）
X. Other (please specify)

[Answer]: A

## Q4. 一覧のページ送りの部品（招待の画面と同じ形）

招待の一覧のページ送りの部品（サーバーの `InvitationPaging`、画面の `features/invitation/paging.ts`）は、境界の決まりにより、利用者の管理から直接は使えません（K-3）。

A. サーバーは `common`、画面は `src/shared/` に移して、招待と利用者の管理で共有する（招待のコードに手が入り、カバレッジの一覧のパッケージに当たれば下限の作業が付く）
B. 利用者の管理の側に同じ形を作り、招待のコードには触れない（重複が増える）
X. Other (please specify)

[Answer]: A

## Q5. 管理の画面すべての 403 の表示（ストーリーの M5 B）の置き場

管理の入口・DSL の管理・招待の管理・利用者の管理のすべてで、403 を受けたら同じ表示にし、ログインの状態を読み直して管理のメニューを消します。

A. 画面の骨組み（AppFrame・ApiClient）に、403 を受けたときの共通の仕組みと表示の部品を置き、各管理の画面はそれを使う（各画面の 403 の扱いを1か所にまとめる）
B. 共通の表示の部品だけを骨組みに置き、403 を受けたときの扱い（表示への切り替え・読み直し）は各管理の画面で書く
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめ:

- Q1 A: 利用者の管理の操作をまとめる新しい部品（例: UserAdministration、パッケージ `useradmin`）を作る。`user` と `auth` の service の口を使い、監査には出来事で知らせる。新しい境界の検査を足す
- Q2 A: 利用停止の状態は利用者（UserAccount）が持つ。`users` の表に足し、3つの入口は今と同じく `user` の service の口から読む
- Q3 A: 止めたときのリフレッシュトークンの無効化は、利用者の管理の部品が止める処理の中で `auth` の service の口を直接呼ぶ（同じトランザクション）
- Q4 A: ページ送りの部品は、サーバーは `common`、画面は `src/shared/` に移して、招待と利用者の管理で共有する（招待のコードに手が入る）
- Q5 A: 管理の画面すべての 403 の扱い（共通の仕組みと表示の部品）は画面の骨組み（AppFrame・ApiClient）に置き、各管理の画面はそれを使う

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
