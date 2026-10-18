# 性能の要件 — U2 u2-businessdata-write

## 出典

- 要件: `aidlc/spaces/default/intents/261016-business-data-views/inception/requirements-analysis/requirements.md`（NFR1・NFR3・NFR7、前提 A1、FR10.4）
- ストーリー: `inception/user-stories/stories.md`（AC2.4.5・AC3.3.4）
- この単位の機能設計: `construction/u2-businessdata-write/functional-design/`
  - `functional-spec.md` の 3節・8節
  - `rules.md` の BR4.2・BR6.2・BR9.4・BR9.5
- U1 の NFR 要件: `construction/u1-businessdata-read/nfr-requirements/performance-requirements.md`（NFR1.2 の判定の決まり、場面の名前、「U2・U3 とそろえる点」）
- この段の答え: `construction/u2-businessdata-write/nfr-requirements/nfr-requirements-questions.md`（Q1・Q2・Q6 は A。まとめの確認は Looks correct）
- k6 の台本: `perf/k6/scenarios.js`（決めた回数の場面 `ONE_SHOT_SCENARIOS`。`ITERATIONS` の既定 100）
- 決まり: `aidlc/spaces/default/memory/team.md`・`project.md`
  - k6 の判定と使い捨ての環境
  - 1回しか使えない行の場面は、流す回数以上の行を用意する
  - プールの見積もりは上限に届く形で確かめる

## ID の対応表

全体の振り分けは `security-requirements.md` の「ID の振り方」にある。

| 上流の ID | この単位での扱い | この単位の ID |
|---|---|---|
| NFR1 応答時間 | 当たる。作成・更新・削除の3本と検証の時間が対象。読み取りは U1、画面は U3 が受け持つ | NFR1.4・NFR1.5・NFR1.6 |
| NFR3 対象DB | 部分。性能を3種類で測ることだけを受け持つ。振る舞いは `reliability-requirements.md` | NFR3.4 |
| NFR7 テスト | 部分。`verify` と CI の時間の記録だけを受け持つ | NFR7.4 |

## 要件

### NFR1.4 作成・更新・削除の応答時間

- 書き込みの API の応答時間は、95 パーセンタイルで 1 秒以内とする。k6 の `checks` の率は 1 とする。目標は緩めない。
- 次の場面を 10 VU で、時間を重ねずに順に流す（Q6: A）。
  - `bizCreate`（3 分）: 送るカラムは 100 カラムの表の FULL のカラム。値は架空の値にし、一意と外部キーを満たす。
  - `bizUpdate`（3 分）: VU ごとに別の行の1カラムを変える。同じ行の排他の待ちを測りに混ぜない。後勝ち（AC2.2.3）は結合テストで確かめる。
  - `bizDelete`（既定 100 回）: 行は1回しか使えないため、setup で回数以上の行を自分で作る（`project.md` 2026-09-28）。
- 判定の決まりは、U1 の NFR1.2 をそのまま当てる（1回の繰り返しに要求1つ。`iteration_duration`。トークンは `setup()` で取る。`caffeinate -dims`。使い捨ての環境）。
- 確かめ方: 場面ごとに `iteration_duration{scenario:<名前>}` の p95 < 1000 ms と、`checks` の率 1 で判定する。
- 持ち主の段: Performance Validation

### NFR1.5 違反の経路の応答時間

- `bizWritePoolLimit`（10 VU・3 分）を流す。中身は、参照先に無い値での作成である。外部キーの違反 → 巻き戻し → 同じ接続での確かめ、と最も長く接続を持つ経路を通る（BR6.2）。
- 応答は 409 `BUSINESS_REFERENCE_NOT_FOUND` を正しい答えとして `checks` に入れる。
- 合格は次の両方とする。満たせないときは目標を緩めず、依頼者に諮る。
  - p95 < 1 秒
  - `scalability-requirements.md` の NFR2.7 の条件
- 確かめ方: NFR1.4 と同じ。
- 持ち主の段: Performance Validation

### NFR1.6 検証の時間

- pattern の照合を含む入力の検証は、照合の合計の締め切り（300 ms。`security-requirements.md` の NFR4.7）と余裕 100 ms の中で終える。最悪でも 400 ms 以内とする（Q2: A、AC2.4.5）。
- 確かめ方: 重い正規表現（`(a+)+$`）と長い値（`aaaa…b`）を 100 カラムに置いた単体テストで、検証全体の時間を、注入した時計と実時間の両方で測る。
  - 締め切りの判定は、注入した時計で決める。
  - 実時間は 400 ms 以内であることを確かめる。CI の負荷で揺れるため、合格の線には余裕をとる。具体の線は NFR 設計で決める。
- 持ち主の段: Code Generation（B4）

### NFR3.4 3種類の対象DB で測る

- NFR1.4・NFR1.5 の4場面を、MySQL・MariaDB・PostgreSQL の3種類で流す。種類ごとに使い捨ての環境を1つずつ作る。
- どの種類でも NFR1.4・NFR1.5 を満たすこと。
- 確かめ方: 種類ごとに結果を並べて記録する。1種類でも満たさなければ Not Met とする。
- 持ち主の段: Performance Validation

### NFR7.4 verify と CI の時間

- B4 の後と Build and Test で、`./gradlew verify` の時間と CI の時間を測って記録する。
- CI の上限の7割（約 42 分）を超えたら、テストの分け方を依頼者に諮る。
- 確かめ方: `:backend:cleanTest :backend:cleanIntegrationTest` を付けて実測する。
- 持ち主の段: Code Generation（B4）、Build and Test

## 補足

- 合否の回は、場面ごとに1回とする。ミリ秒の単位の目標（待ちの最大など）は置かない。
- 1要求の最悪の時間（およそ 18 秒、`security-requirements.md` の NFR4.4）は、応答しない対象DB での上限である。応答時間の目標とは別に扱う。

## 上流との差

- **NFR1.4 を U2 が使うこと**
  - U1 の `performance-requirements.md` の「U2・U3 とそろえる点」には「U3 の画面の時間は NFR1.4 以降」とある。
  - 書き込みの API の時間も NFR1 に要るため、U2 が NFR1.4〜NFR1.6 を使い、U3 は NFR1.7 から振る（まとめの確認）。
  - U1 の文書は書き換えず、差をここに書く。
- **承認の場の決定**（`construction/functional-design/memory.md` の Open questions）
  - `nullable` は U2 が足す検証の材料とし、`formPart` の値域から `lookup` を外す。
  - `required`・`nullable` はすべてのカラムで返す。
  - これは定義の応答の項目の話で、この文書の性能の要件に差は無い。
  - `functional-spec.md` 1.1 と X10 との差は `security-requirements.md` に書く。
- **Q1: A による読み替え**
  - 事前の問い合わせと違反の後の確かめは、それぞれ1文にまとめる。
  - BR5.1・BR6.2 の「カラムごとに問い合わせる」の読みを替えるため、NFR1.5 の経路は文が3つまでになる。
- 要件の前提 A1（測り方は NFR 要件の段で決める）は、NFR1.4〜NFR1.6・NFR3.4 で決めた。

## 後の段に回すこと

- **NFR 設計の試し**
  - 一意の事前の問い合わせと、違反の後の確かめを、それぞれ1文にまとめる形（3種類の DB）。
  - pattern の打ち切りで、何文字ごとに締め切りを見るか（時計を読む重さ）。NFR1.6 の実時間の合格の線もここで決める。
- **Code Generation（B4）**
  - `bizCreate`・`bizUpdate`・`bizDelete`・`bizWritePoolLimit` の台本と、`bizPrepare` に足す行。
  - 台本は `k6 inspect --include-system-env-vars` で場面の名前と閾値を確かめ、各場面 1 VU・10 秒の試し走りをする。
- **Performance Validation**: NFR1.4・NFR1.5・NFR3.4 の合否の回。
- **Observability Setup**: 書き込みの3本の p95 の区画（`observability-requirements.md` の NFR8.5）。
- **基盤の設計**: この文書の要件には持ち主が無い。

## U3 とそろえる点

- **番号**: U3 は NFR1.7・2.10・3.7・4.20・7.7・8.7 から振る。
- **定義の項目**: 平らに置く。`required`・`nullable` は必ず値を持ち、`formPart` に `lookup` は来ない。
- **値の長さ**: 10,000 字の上限（U1 の詳細と同じ値）を、画面の入力の `maxLength` にも当てる。切ったカラムを更新で読み取りにする U3 の D19 は、画面だけの守りになる。
- **pattern**: 画面では当てない。`PATTERN_TIMEOUT` の文は画面が用意する。
- **503 の出し分け**: `BUSINESS_TARGET_DB_TIMEOUT` は「保存されたか分からない」、`UNAVAILABLE` は「保存されていない」として出す。
- **413**: 4 MiB を超えた本文の文を画面が用意する。画面で先に大きさを確かめるかは U3 で決める。
- **E2E**: 業務データの代表の流れ1本まで。U2 は E2E を足さない。
- **画面の時間**: U3 の画面の時間の要件は、U2 の NFR1.4 の API の時間を前提にする。
