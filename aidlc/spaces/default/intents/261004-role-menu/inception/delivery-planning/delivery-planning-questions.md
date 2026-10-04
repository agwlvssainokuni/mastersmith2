# Delivery Planning の質問

承認済みの7つの単位（U1 cross-cutting・U2 dsl-v2・U3 group・U4 role・U5 navigation・U6 role-admin-ui・U7 app-frame-ui）を、どの順で作るかを決めます。作る1回ぶんのまとまりを Bolt（ひとまとまりの作る作業で、終わると動くものができる）と呼びます。

## 決まっていること（質問にしない）

- **進め方**: 依頼者1名と AI で作る。Bolt ごとに `develop` から短命のブランチを作って squash で戻し、統合の前に `./gradlew verify` を通す（`team.md`）。
- **最初の Bolt の特別な手順（walking skeleton、全体を薄く通す最初の版）は行わない**（`team.md`）。骨格はすでにある。
- **依存の形**: U3 → U1、U4 → U1・U2・U3、U5 → U1・U2・U4、U6 → U1・U3・U4、U7 → U1・U4・U5（`unit-of-work-dependency.md`）。U1 と U2 は依存が無い。
- **大きい単位**: U4 は設計の段は1つの単位として通し、コード生成の計画で Bolt に分ける（`project.md`）。
- **make-you-chic-ui**: 入れ子のサイドバーは上流への追加を先に依頼し、取り込みは U7 の Bolt の前の固定先の更新の専用のコミット。間に合わないときに自前に切り替えるかは、その時点で依頼者に諮る（`team.md`）。

## カバレッジの今の値（2026-10-04 に実測）

`:backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport` で実測した（`project.md` の学び）。全体の合計で判定している7パッケージ（`packagesJudgedByTotal`）の今の値と、手が入る見込み:

| パッケージ | 行 | 分岐 | 手が入る見込み |
|---|---|---|---|
| access.service | 100.0%（11/11） | 分岐なし | 低い（U1 は `access.web` の注釈と構造の検査で、`access.service` は触らない見込み） |
| audit.repository | 行なし（インターフェースだけ） | — | U3 の監査の列の追加で手が入る見込み（行が無いため下限は満たしやすい） |
| common.error.domain | 100.0%（41/41） | 94.4%（17/18） | 低い（PAYLOAD_TOO_LARGE は使い回し） |
| common.error.service | 100.0%（28/28） | 81.2%（13/16） | 低い |
| common.health | **79.2%（42/53）** | 100.0%（2/2） | U1 が API の注釈を付けるときに手が入れば、行の下限 80% に足りない。手が入るなら U1 でテストを足す |
| common.i18n.domain | 100.0%（38/38） | 89.3%（25/28） | 低い |
| common.web | 96.5%（110/114） | 85.2%（46/54） | U1 の注釈で手が入りうる。今の値は下限を満たす |

手を入れたパッケージは下限を満たして一覧から外す（`team.md`）。common.health 以外は今でも下限を満たすため、手を入れたら一覧から外すだけで済む見込み。

質問は6問です。

---

## Q1. 何から作るか

A. 危ないところから: U2（DSL の版 2。既存の DSL の機能に広く手が入り、版を上げると既存の DSL が無い扱いになる）→ U1 → U3 → U4 → U5 → U6 → U7
B. 横断の準備から: U1（API の分類の印と ESLint の制限。以後のすべての API が使う）→ U2 → U3 → U4 → U5 → U6 → U7
C. 価値の早いところから: U1 → U3 → U2 → U4 → U6（管理の画面）→ U5 → U7（業務のメニュー）
X. Other (please specify)

[Answer]: A

## Q2. 順位を点数（WSJF: 価値と急ぎと危なさを大きさで割る式）で決めるか

依存の形がほぼ一本道で、順の選び方は U1 と U2 の前後、U5 と U6 の前後くらいしかありません。

A. 点数は付けず、危なさと依存の考え方だけで決める（前の Intent と同じ）
B. WSJF の点数を付けて決める
X. Other (please specify)

[Answer]: A

## Q3. Bolt の大きさ

A. 原則1つの単位を1つの Bolt にし、U4 だけを3つの Bolt に分ける（設定と解決 → 割り当てと作業ロール → 権限の YAML の受け渡し）。全部で9つの Bolt
B. 1つの単位を1つの Bolt にする（U4 も1つ、7つの Bolt）。U4 の中の分け方はコード生成の計画で決める
C. 小さい単位をまとめる（U1 と U2、U3 と U4 の前半など）
X. Other (please specify)

[Answer]: A

## Q4. 同時に作るか

A. 1つずつ順に作る（依頼者1名で確かめるため。依存の上で並べられる組も順に作る）
B. 依存の上で並べられる組（U1 と U2、U5 と U6）は同時に作る
X. Other (please specify)

[Answer]: A

## Q5. make-you-chic-ui への依頼と、間に合わないときの扱い

A. 入れ子のサイドバーの依頼の口は U7 の機能設計で確定させ、すぐ上流に依頼する。U7 の Bolt の前に取り込めないときは、その時点で依頼者に諮る。自前で作っても満たす受け入れ基準（今の項目・開閉・言語・はみ出さない）は、U7 の機能設計で切り分けておく（ストーリーの確かめの R-05）
B. 待たずに、最初から frontend の側で自前のサイドバーを作り、取り込まれたら置き換える
X. Other (please specify)

[Answer]: A

## Q6. Construction の作り方（担当の割り当て）

A. この会話の中で1つずつ作り、段ごとに依頼者が承認する（設計の段はすべての単位を通してから次の段へ進み、コード生成は最後にまとめる。前の Intent と同じ）
B. 単位ごとに、設計からコード生成までを先に通してから次の単位へ進む（最初に動くコードが早く出るが、段の承認が単位の終わりにまとめて来る）
C. 複数のチームで単位を受け持ち、それぞれが承認する（依頼者1名のため、ふつうは当たらない）
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

- 作る順は危ないところから: U2 → U1 → U3 → U4 → U5 → U6 → U7（Q1: A）。点数は付けず、危なさと依存の考え方だけで決める（Q2: A）。
- Bolt は原則1つの単位を1つにし、U4 だけ3つに分けて全部で9つ（Q3: A）。U4 の分け方は、解決の口が割り当てと作業ロールを要るため、「ロールと設定」→「割り当て・作業ロール・解決の口」→「権限の YAML の受け渡し」の順にする（質問の案の「設定と解決」を、依存に合わせて並べ替えた）。
  - B1 U2 dsl-v2 / B2 U1 cross-cutting / B3 U3 group / B4 U4 前（ロールの管理と権限の設定・木の API）/ B5 U4 中（割り当て・作業ロール・解決の口・自分の権限）/ B6 U4 後（権限の YAML の受け渡し）/ B7 U5 navigation / B8 U6 role-admin-ui / B9 U7 app-frame-ui
- 1つずつ順に作る（Q4: A）。
- make-you-chic-ui の入れ子のサイドバーは、U7 の機能設計で依頼の口を確定してすぐ上流に依頼し、B9 の前に取り込めなければその時点で依頼者に諮る。自前でも満たす基準は U7 の機能設計で切り分ける（Q5: A）。
- Construction は、この会話の中で段ごとにすべての単位を通してから次の段へ進み、コード生成は最後にまとめる（stage-major、Q6: A）。担当は1つの会話（solo）。
- カバレッジ: 全体の合計で判定している7パッケージのうち、下限に足りないのは common.health（行 79.2%）だけ。手を入れた Bolt でテストを足し、一覧から外す。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
