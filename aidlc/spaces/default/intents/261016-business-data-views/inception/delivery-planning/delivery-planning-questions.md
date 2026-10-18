# Delivery Planning の質問（Intent 261016-business-data-views）

ここでは、作る単位（U1〜U5、`aidlc/spaces/default/intents/261016-business-data-views/inception/units-generation/unit-of-work.md`）をどの順に、どのまとまりで作るかを決める。まとまりの1つを Bolt と呼ぶ（Bolt は、1つ以上の単位を設計から統合まで通して、動くものにする1回の作り込み）。単位どうしの依存は U2 → U1、U3 → U1・U2・U4 で、U1・U4・U5 は並べて作れる（`unit-of-work-dependency.md`）。

## 決まっていること（質問にしない）

- 作業は依頼者1名と AI で、Bolt ごとに `develop` から作る短命のブランチで行い、squash で `develop` に戻す。サブモジュールの固定先の更新を含む Bolt は fast-forward で統合する（team.md の Way of Working）。worktree は使わない。
- 最初の Bolt だけを単独で承認する特別な手順（walking skeleton、最初に全体を一通り動かす薄い作り込み）は行わない。アプリの骨格は既にある（team.md の Walking Skeleton）。
- make-you-chic-ui への依頼（5）（`aidlc/spaces/default/intents/261016-business-data-views/make-you-chic-ui-request-5.md`）は、機能設計の承認の前に出す。上流の持ち主は依頼者で、間に合わなければ frontend の側の手当てを続けるかを Functional Design の承認の場で諮る（team.md）。
- Units Generation のレビューの R-01 により、U4 の axe-core 4.14 の引き上げは、U3 の新しい画面ができた後でないと合格（E2E の全件の通過と違反 0 件）できない。そのため U4 は前半（固定先の更新・名前の関数・既存の一覧・トップバー）と後半（axe-core の引き上げ）の2つの Bolt に分ける。大きい単位を複数の Bolt に分けるときは、設計の段は1つの単位として通し、コード生成の計画で Bolt ごとに分ける（`project.md` の学び）。
- 手を入れる見込みのパッケージのうち、全体の合計で判定しているもの（`packagesJudgedByTotal`）は、この段でカバレッジを実測して見積もる（team.md の Testing Posture）。

## DQ1. 何から作るか

A. 危ないものから先に作る: 3種類の対象DB の方言と行の SQL の部品を持つ U1 を早く作り、ADR-007 の実現の判断を確かめる。CI を安定させるため、不安定なテストを直す U5 を最初に置く。順は U5 → U1 → U4 前半 → U2 → U3 前半 → U3 後半 → U4 後半（推奨）
B. 見える価値から先に作る: U1 → U3 前半（一覧・詳細の画面）で業務データを見られる状態を先に作り、その後に U2 → U3 後半 → U4 前半 → U4 後半 → U5
C. 持ち越しを先に片付ける: U5 → U4 前半 → U1 → U2 → U3 前半 → U3 後半 → U4 後半
X. Other (please specify)

[Answer]: A

## DQ2. 点数を付けて順位を決める型（WSJF: 価値と急ぎと危険の減り方を、大きさで割った点数）を使うか

A. 使わない。依存がほぼ一本道で、順の選択肢が少ないため、DQ1 の考え方だけで順を決める（前の Intent と同じ）（推奨）
B. 使う（価値・急ぎ・危険の減り方・大きさを1〜5で付ける）
X. Other (please specify)

[Answer]: A

## DQ3. 1つの Bolt の大きさ

A. 原則1つの単位を1つの Bolt にし、大きい U3 は前半（一覧・詳細・共通の部品）と後半（作成・更新・削除）に、U4 は前半と後半（axe-core）に分ける。Bolt は7つ（推奨）
B. 1つの単位を1つの Bolt にする（U3 を分けない。U4 は上の理由で2つ）。Bolt は6つ
C. 小さい単位をまとめる（U5 と U4 前半を1つの Bolt にする）。Bolt は6つ
X. Other (please specify)

[Answer]: A

## DQ4. Bolt を並べて作るか

A. 1つずつ順に作る。作業は1つの作業フォルダで短命のブランチを使うため（推奨）
B. 依存の無い Bolt（U5・U1・U4 前半）は並べて作る
X. Other (please specify)

[Answer]: A

## DQ5. 作り方の進め方（設計の段の回し方）

A. 設計の段ごとに全単位を通す（機能設計を U1〜U5 で行ってから NFR 要件へ、…、最後にコード生成を Bolt の順に行う）。前の Intent と同じ（推奨）
B. 単位ごとに設計からコード生成までを通してから次の単位へ進む（最初の動くコードが早く出るが、承認の場は単位の終わりにまとまって来る）
X. Other (please specify)

[Answer]: A

## DQ6. いちばん気がかりなこと（先に確かめたいもの。複数選んでよい）

A. 3種類の対象DB の方言（NULL の並び・部分一致のエスケープ・時差つきの型・違反の見分け）
B. axe-core 4.14 で出る違反の範囲と、make-you-chic-ui の対応の時期
C. 書き込みの接続・対象DB のトランザクション・監査の書き込みの順
D. 画面の量（100 カラムの表・一覧の状態の保ち方・作成と更新の画面）
X. Other (please specify)

[Answer]: A, B, C, D

## Consolidated Summary Confirmation

- 順の考え方（DQ1: A・DQ2: A）: 危ないものから先に作り、WSJF の点数は使わない。CI を安定させるため U5 を最初に置く。
- Bolt（DQ3: A、7つ。1つずつ順に作る DQ4: A）:
  - B1 U5 テストと測定の持ち越し: 不安定なテスト2件の直しと、`dslMixed` の `memory.peak` の測り方の台本（`anon` の最大）。測定そのものは Build and Test。
  - B2 U1 読み取りの API: 出し分けの関数・行の SQL の部品と方言（3種類の対象DB）・定義と一覧と詳細の API・`role` の境界テストの変更（承認を得る）。ADR-007 の実現の判断（方言）を確かめる。
  - B3 U4 前半: make-you-chic-ui の固定先 `b0a1e79`（fast-forward の統合）・「操作: …」の名前の関数と既存の管理の一覧・トップバーの名前。axe-core 4.14 を手元で当てて違反の範囲を先に確かめる（DQ6 の B）。
  - B4 U2 書き込みの API: 書き込みの接続・対象DB のトランザクション・作成と更新と削除・検証と一意・監査の種類と `audit` の境界テスト（DQ6 の C）。
  - B5 U3 前半: 一覧（検索・表・ページ送り）と詳細の画面、`src/shared/` の部品、道の登録（DQ6 の D）。
  - B6 U3 後半: 作成・更新の画面と削除の確かめ、離れるときの確かめ、業務データの代表の E2E。
  - B7 U4 後半: axe-core 4.14 の引き上げと `ignore` を外すこと（E2E 全件の通過と違反 0 件）。依頼（5）の取り込み（間に合えば）と固定先のもう一度の更新。間に合わなければ引き上げを持ち越すかを諮る。
- 設計の段の回し方（DQ5: A）: 設計の段ごとに全単位を通し、コード生成を Bolt の順に行う（stage-major、前の Intent と同じ）。
- 進め方: この場で1つずつ作り、その都度承認する（solo）。
- 気がかり（DQ6: A・B・C・D すべて）: 方言は B2、axe の範囲は B3、書き込みと監査は B4、画面の量は B5・B6 で早めに確かめる。
- カバレッジの見積もり: 全体の合計で判定しているパッケージのうち、手を入れる見込みは `audit.repository`（数える行が無い）と `common.web`（行 96.5%・分岐 85.2%）。手を入れたら一覧から外す。`common.health`（行 79.2%）は手を入れない見込み。
- 外の依存: make-you-chic-ui への依頼（5）（持ち主は依頼者。機能設計の承認の前に出し、B7 で取り込む）。

Does this all look correct before I generate the delivery plan?

- Looks correct
- Request changes

[Answer]: Looks correct
