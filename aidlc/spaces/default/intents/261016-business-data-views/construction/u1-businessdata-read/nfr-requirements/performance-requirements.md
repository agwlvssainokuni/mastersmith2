# 性能の要件 — U1 u1-businessdata-read

## 出典

- 要件 `aidlc/spaces/default/intents/261016-business-data-views/inception/requirements-analysis/requirements.md`（NFR1・NFR3・NFR7、前提 A1）
- ストーリー `inception/user-stories/stories.md`（AC1.3.7・AC1.3.16）
- この単位の機能設計 `construction/u1-businessdata-read/functional-design/`（`functional-spec.md` 8節、`rules.md` の BR5.6・BR5.7・BR8.4）
- ADR-007 の「全体の件数の問い合わせの重さ」（`inception/domain-design/decisions.md`）
- この段の答え `construction/u1-businessdata-read/nfr-requirements/nfr-requirements-questions.md`（Q1: A・Q2: A、まとめの確認 Looks correct）
- 前の Intent の実測（`aidlc/spaces/default/intents/261004-role-menu/operation/performance-validation/test-results.md`。メニューの p95 181.1 ms、置き場の問い合わせ 35.1 ms）と、k6 の台本 `perf/k6/scenarios.js`
- `aidlc/spaces/default/memory/team.md`・`project.md`（k6 の判定、使い捨ての環境、`verify` の時間）

## ID の対応表

全体の振り分けは `security-requirements.md` の「ID の振り方」にある。この成果物に書くのは次のもの。

| 上流の ID | この単位での扱い | この単位の ID |
|---|---|---|
| NFR1 応答時間 | 当たる（読み取りの3本。作成・更新・削除は U2、画面の時間は U3） | NFR1.1・NFR1.2・NFR1.3 |
| NFR3 対象DB | 部分（性能を3種類で測ること。振る舞いは `reliability-requirements.md`） | NFR3.2 |
| NFR7 テスト | 部分（`verify` と CI の時間の記録） | NFR7.3 |

## 要件

| ID | 要件 | 測り方・確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR1.1 | 業務データの読み取りの API の応答時間は、95 パーセンタイルで 1 秒以内、k6 の `checks` の率は 1 とする。目標は緩めない。悪い側の規模（`scalability-requirements.md` の NFR2.1）で、次の場面を 10 VU・3 分で、時間を重ねずに順に流す（Q1: A）。<br>・`bizSearchFirst`: 1ページ 100 件・100 カラム・条件なし・既定の並び。全体の件数の問い合わせを含む（AC1.3.7）<br>・`bizSearchContains`: TEXT のカラムの部分一致で、表の全部を読む形<br>・`bizSearchLast`: 最後のページ（読み飛ばす行の数が多い OFFSET）<br>・`bizDetail`: 主キーで1行の詳細<br>・`bizDefinition`: 定義（対象DB に接続しないため、PostgreSQL の環境だけで流す） | 判定は NFR1.2 のとおり。場面ごとの `iteration_duration{scenario:<名前>}` の p95 < 1000 ms と `checks` の率 1 | Performance Validation |
| NFR1.2 | k6 の場面の判定の決まり。<br>・どの場面も1回の繰り返しに要求1つとし、繰り返しの中に待ちを置かない。操作ごとに場面を分け、`op` のタグに頼らない<br>・判定は単調な時計の `iteration_duration` で行い、`http_req_duration{name:…}` は並べて記録するだけにする（colima の VM の時計のずれ）<br>・VU ごとのアクセストークンは `setup()` で取り、場面の長さはトークンの有効期限より短い 3 分にする<br>・setup は前の場面の状態に頼らず、準備の場面 `bizPrepare` が作った表・行・DSL・ロールだけを使う<br>・台本全体を `caffeinate -dims` で包み、流す前後に `pmset` で眠りが無かったかを確かめる。使い捨ての環境（`perf/README.md`）で流す | B2 で台本を書いたとき、`k6 inspect --include-system-env-vars` で場面と閾値の名前を確かめる。短い試し走り（各場面 1 VU・10 秒）で、閾値の名前の指標が出ることを確かめる（判定ではない） | Code Generation（B2）、Performance Validation |
| NFR1.3 | 全体の件数の数え上げ（BR5.6）と深い OFFSET（`bizSearchLast`）を、NFR1.1 の測りに含める。満たせないときは目標を緩めず、ADR-007 の切り替え先（件数を「多数」と出す）を要件の変更として依頼者に諮る | NFR1.1 の結果で判定する | Performance Validation |
| NFR3.2 | NFR1.1 の対象DB を使う4場面（`bizSearchFirst`・`bizSearchContains`・`bizSearchLast`・`bizDetail`）は、MySQL・MariaDB・PostgreSQL の3種類で、1種類ずつ使い捨ての環境を作って流す（Q1: A）。どの種類でも NFR1.1 を満たす | 種類ごとに結果を並べて記録する。1種類でも満たさなければ Not Met | Performance Validation |
| NFR7.3 | B2 の後と Build and Test で、`./gradlew verify` の時間と CI の時間を測って記録する。CI の上限の7割の約 42 分を超えたら、テストの分け方を依頼者に諮る（team.md） | `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `verify` で実測する（`project.md`） | Code Generation（B2）、Build and Test |

## 補足

- 合否の回は、場面ごとに1回とする。ミリ秒の単位の目標（接続の待ちの最大など）は置かないため、「数 ms の目標は複数回流す」の学びは当たらない（`scalability-requirements.md` の NFR2.4）。
- 応答の大きさの悪い側（`bizSearchWide`）は、メモリの確かめの場面として `scalability-requirements.md` の NFR2.5 に置く。応答時間の判定の場面には数えない。

## 上流との差

- 承認の場の決定（`construction/functional-design/memory.md` の Open questions）: `nullable` は U2 が足す検証の材料とし、`formPart` の値域から `lookup` を外し、U3 は `nullable`・`required` が欠けたら形の崩れとする。U1 の `rules.md` の BR2.8 は `formPart` の値域を書いていない。U1（B2）は `formPart` も `nullable` も返さないため、この単位の振る舞いと性能の要件に差は無い（`functional-spec.md` の X10 と一致する）。
- R-07（読み取りの口の例外の通り道と `TargetReadSession` の置き場）は `reliability-requirements.md` の NFR4.8 で受け、置き場を NFR 設計に回す。R-08（時差つきの検索の値のオフセット）は `reliability-requirements.md` の NFR3.3 で受ける。
- 要件の前提 A1（「測り方は NFR 要件の段で決める」）は、この文書の NFR1.1・NFR1.2・NFR3.2 で決めた。

## 後の段に回すこと

- **NFR 設計**: 深いページの OFFSET の重さの見立てと、件数とページを同じ貸し出しで読むときの一貫性（`functional-spec.md` 8節）。受け取る量の絞り方・時差の扱い・ヒープの見積もり・口の置き場は、それぞれ `scalability-requirements.md`・`reliability-requirements.md` に書いた。
- **Code Generation（B2）**: k6 の場面と `bizPrepare` の台本、行を作る種の台本（`perf/` の下）、試し走り。
- **Performance Validation**: NFR1.1・NFR1.3・NFR3.2 の合否の回。
- **Observability Setup**: 3本の API の p95 の区画（`observability-requirements.md` の NFR8.3）。

## U2・U3 とそろえる点

- 場面の名前は `biz` から始める。U1 は `bizDefinition`・`bizSearchFirst`・`bizSearchContains`・`bizSearchLast`・`bizDetail`・`bizSearchWide`。U2 は `bizCreate`・`bizUpdate`・`bizDelete` と、書き込みのプールが上限に届く場面 `bizWritePoolLimit`。
- 準備の場面 `bizPrepare` は1つにし、U1 と U2 で同じ表・同じ種を使う。
- 判定の決まり（NFR1.2）は U2 の場面にも同じものを当てる。
- U3 の画面の時間の要件は NFR1.4 以降に振る（U1 は NFR1.1〜NFR1.3）。
- 枝番の振り分け: NFR4 の足す番号は U1 が 4.5・4.6・4.8、U2 が 4.7（pattern の時間の上限）と 4.9〜4.19、U3 が 4.20 以降とする（下書きの「U3 は 4.10 以降」は U2 と重なるため直した。U2 が 4.19 で足りなければ、U3 の始めを後ろへずらして番号を重ねない）。
