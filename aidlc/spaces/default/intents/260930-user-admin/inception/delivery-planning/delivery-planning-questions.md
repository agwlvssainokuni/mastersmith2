# Delivery Planning の質問

この段では、承認済みの5つの単位（`aidlc/spaces/default/intents/260930-user-admin/inception/units-generation/unit-of-work.md`）を、どの順に、どうまとめて作るかを決めます。作るときのひとまとまりを **Bolt** と呼びます。Bolt は、作業ブランチ1本で作り、テストと `./gradlew verify` を通して `develop` に統合するまでの1回分の作業です。

## 決まっていること（質問にしない）

- **進め方**（`team.md`）:
  - Bolt は1つずつ順に作り、同時には作らない（Units Generation の UQ3 A）。
  - 作業ブランチは `develop` から作り、squash で `develop` に戻す。make-you-chic-ui の固定先の更新を含む Bolt は、専用のコミットを残すため fast-forward で統合してよい。
  - walking skeleton（最初に端から端までの細い流れを作る特別な手順）は行わない。骨格はもうある。
- **作るのは AI**: 担当は開発担当（aidlc-developer-agent）だけで、レビューと承認は依頼者が行う。チームの編成の段は無い。
- **外からの待ち**: 無い。make-you-chic-ui の Dropdown の押せない項目と理由の文は、`3481488` で取り込み済み（push 済み）。U5 で固定先を更新する。
- **単位の依存**（`unit-of-work-dependency.md`）: U3 は U1・U2 の後、U5 は U2・U3・U4 の後に作る。U1・U2・U4 は、たがいに依存しない。
- **カバレッジの一覧の作業**（`team.md`）: `packagesJudgedByTotal` の一覧にあるパッケージに手を入れる Bolt では、テストを足して下限（行 80%・分岐 70%）を満たし、一覧から外す。この Intent で手が入る見込みは `auth.domain`（U1・U3）と `auth.repository`（U1・U3）。最初に手を入れる Bolt で一覧から外す。今の値は下の「実測」に書く。
- **Contract Design からの申し送り**（U3 の機能設計で決める）:
  - R-04: `USER_ADMIN_BUSY` を監査に残すか。
  - R-06: `SearchText` の Converter の置き場と登録の仕方。
  - R-07: ロックの状態の行と users の行を排他する順。

## 実測（Bolt の大きさの見積もりの材料）

2026-10-01 に `develop`（`eb7c982`）で、`:backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport` を流して測った（単体テストと結合テストの合計、5分27秒、すべて成功）。

| パッケージ | 行 | 分岐 | 一覧 | 手を入れる単位 | 見立て |
|---|---|---|---|---|---|
| `auth.domain` | 97.9%（94/96） | 100.0%（18/18） | あり | U1・U3 | すでに下限を満たす。最初に手を入れる Bolt で一覧から外すだけ |
| `auth.repository` | 93.9%（31/33） | 50.0%（1/2） | あり | U1・U3 | 分岐は2つだけ。足りない1つの分岐のテストを足せば下限を満たす。作業は小さい |
| `auth.service` | 99.0% | 91.7% | なし | U1・U3 | すでにパッケージごとの下限の対象 |
| `auth.web` | 100.0% | 95.0% | なし | U1 | 同上 |
| `audit.domain`・`audit.service` | 99.5%・100.0% | 97.7%・84.4% | なし | U1・U3 | 同上。`audit.repository`（一覧あり）には手が入らない見込み |
| `user.domain`・`user.service`・`user.web` | 99.5%・100.0%・96.5% | 97.6%・95.5%・84.6% | なし | U1・U3 | 同上 |
| `invitation.service`・`invitation.web` | 100.0%・97.3% | 93.5%・87.0% | なし | U2 | 同上 |
| `access.*` | 100.0% | 93.8〜100.0% | `access.domain`・`access.service` はあり | なし（見込み） | U3 で操作する管理者の確かめ直しに `ACCESS_DENIED` を使う形が決まり、`access` の本体に手が入るなら、その Bolt で一覧から外す（今の値はどちらも下限を満たす） |

カバレッジの一覧の作業は、どの Bolt でも小さい見込みのため、Bolt の分け方の判断には響かない。

---

## Q1. Bolt のまとめ方と順

A. 1つの単位を1つの Bolt にする。順は U1 → U2 → U4 → U3 → U5。危険の大きい U1（3つの入口・`auth` の一覧の作業）を先にし、画面の土台の U4 を U3 の前に置く。
B. U2 と U4 を1つの Bolt にまとめ、4つの Bolt にする。順は B1 U1 → B2 U2・U4 → B3 U3 → B4 U5。U2 と U4 はどちらも招待の画面（InvitationUi）に手を入れるため、まとめると同じファイルを2回直さずに済む（Units Generation のレビューの R-01）。代わりに、B2 はサーバーと画面の両方にまたがる。
C. サーバーと画面で分け、3つの Bolt にする（B1 U1・U2・U3 → B2 U4 → B3 U5）。Bolt の数は少ないが、B1 が大きく、統合までが長い。
X. Other (please specify)

[Answer]: B

## Q2. U3（管理の API、大きさ XL）を分けるか

A. 分けない。U3 を1つの Bolt で作る。
B. 2つの Bolt に分ける。前半は一覧・検索・ロックの判定の結果と、氏名と言語の変更（US1.1・US5.1。行の排他と監査が無い）。後半は、管理者の印・利用停止・失敗回数の取り消しを、最後の管理者の保護・同時の重なり・監査と一緒に作る（US2.1・US3.1・US4.1）。1回の統合が小さくなる代わりに、同じ単位を2回に分けて統合する。
X. Other (please specify)

[Answer]: B

## Q3. 検索（Should）を作る時期と E2E

E2E の代表の流れ（ストーリーの M9 A）は、自分で作った利用者を検索で探します。検索を後に回すと、E2E の書き方を変える必要があります。

A. 検索を後に回さず、U3・U5 の中で一覧と一緒に作る。E2E は検索で利用者を探す。
B. 検索を最後に回し、時間が足りなければ切り離せるようにする。E2E はページ送りで利用者を探す形で書く。
X. Other (please specify)

[Answer]: A

## Q4. Construction の設計の段の進め方

A. 設計の段ごとに、すべての単位を通す（機能設計を U1〜U5 → NFR 要件を U1〜U5 → … → コード生成を Bolt の順に）。前の Intent と同じ。段ごとの承認は1回で済むが、動くコードはすべての設計が終わってから。
B. 単位ごとに、設計からコード生成までを通してから次の単位へ進む（Bolt の順）。最初の動くコード（U1）が早く出て、その結果を後の単位の設計に生かせる。段の承認は終わりにまとめて来る。
X. Other (please specify)

[Answer]: A

## Q5. 気がかりな点を早めに確かめるか

ドメイン設計の ADR-007 は、実現できるかの確かめを後の段に回しています。対象は、最後の管理者の保護の同時性、停止中のログインで読み書きの回数をそろえる形、管理の画面すべての 403 の扱いです。

A. 特別な前倒しはしない。ADR-007 の持ち主の段（機能設計・NFR 設計・コード生成）で確かめる。
B. NFR 要件の段で、本番とは別の試しのコードで、最後の管理者の保護の同時性（待ち合わせで重ねた2つの操作）だけを先に確かめる（前の Intent の dsl-schema-loader と同じ形）。
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめと、Bolt の計画:

- Q1 B・Q2 B で、Bolt は次の5つになる（U3 を2つに分けたため、Q1 B の4つから1つ増える）。
  - **B1 U1（利用停止の状態と3つの入口）**: V9 の移行、3つの入口での停止の判定、リフレッシュトークンのまとめての無効化の口、停止中のログインの監査の理由、`.idea/.gitignore`。`auth.domain`・`auth.repository` を一覧から外す。
  - **B2 U2・U4（ページ送りの共通化と 403 の共通の扱い）**: `common.paging` と `src/shared/` のページ送り、招待の切り替え、ApiClient・AppFrame の 403 の扱い、管理の入口・DSL・招待の画面の置き換え、自分の氏名と言語だけを当てる口。招待の画面はこの Bolt で一度だけ直す。統合は単位ごとの squash（2コミット）にするかを、コード生成の計画で決める（`team.md`）。
  - **B3 U3 前半（一覧と氏名・言語の変更）**: 一覧・検索・ロックの判定の結果（C3 の GET、C8 の findAdminPage・lockViewsOf）、氏名と言語の変更（C3 の PUT）。行の排他と監査は無い。US1.1・US5.1 のサーバー側。
  - **B4 U3 後半（印・停止・失敗回数の取り消し）**: 5つの操作、拒否の順、最後の管理者の保護（行の排他・同時の重なりの待ち合わせのテスト）、監査の出来事、`USER_ADMIN_BUSY`。US2.1・US3.1・US4.1 のサーバー側。Contract Design の申し送り R-04・R-07 はここで決まる（R-06 は B3）。
  - **B5 U5（利用者の管理の画面）**: 画面一式、E2E の代表の流れ、make-you-chic-ui の固定先の更新（`3481488` 以降、専用のコミット）、`frontend/.npmrc` の `ignore-scripts`。統合は fast-forward でよい（`team.md`）。
  - 順は B1 → B2 → B3 → B4 → B5。依存の向き（U3 は U1・U2 の後、U5 は U2・U3・U4 の後）を満たす。単位の依存の図の順（U1・U2・U4 はどれから作ってもよい）から外れるところは無い。
- Q3 A: 検索は一覧と一緒に B3（サーバー）と B5（画面）で作る。E2E は検索で利用者を探す。
- Q4 A: Construction の設計の段は、段ごとにすべての単位を通す（stage-major。前の Intent と同じ）。コード生成は Bolt の順に行う。
- Q5 A: ADR-007 の確かめは、持ち主の段（機能設計・NFR 設計・コード生成）で行う。前倒しはしない。
- 担当はすべて開発担当（aidlc-developer-agent）。外からの待ちは無い。カバレッジの一覧の作業は B1 で済む見込み（`auth.domain` は満たし済み、`auth.repository` は分岐1つ）。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
