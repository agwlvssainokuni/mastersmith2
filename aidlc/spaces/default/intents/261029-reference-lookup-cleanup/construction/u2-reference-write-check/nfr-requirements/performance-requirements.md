# 性能の要件 — U2 u2-reference-write-check

出典: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/functional-design/functional-spec.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/functional-design/rules.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`

## 出典

- 要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`（NFR2・NFR5）
- ストーリー `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/user-stories/stories.md`（AC3.5.4 の場面 (3)「保存前の確かめつきの作成」）
- この単位の機能設計（`functional-spec.md` の 9節、`rules.md` の BR4.1・BR4.7）
- この段の答え `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-requirements/nfr-requirements-questions.md`（Q1: A・Q3: A）
- U1 の性能の要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-requirements/performance-requirements.md`（判定の決まりと種を共有する。場面の名前は `ref` から始める）
- 手順と既存の場面 `perf/README.md`「業務データの書き込みの場面」（`bizCreate`・`bizUpdate` の p95 < 1,000 ms、`bizWritePoolLimit`）、台本 `perf/k6/scenarios.js`、種 `perf/biz/make-biz-seed.sh`、DSL `perf/make-biz-dsl.mjs`

## ID の対応表

全体の表は `security-requirements.md` の「ID の振り方」にある。

| 上流の ID | この単位での扱い | この単位の ID |
|---|---|---|
| NFR2 性能 | 当たる（応答時間と測り方） | NFR2.21〜NFR2.25 |
| NFR5 テスト | 部分（`verify` と CI の時間の記録） | NFR5.23 |

## 要件

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR2.21 | 参照の確かめが入った作成の応答時間は、既存の書き込みの線と同じ p95 < 1,000 ms を目標とし、緩めない（Q1: A）。k6 の `checks` の率 1 も満たす | 新しい場面の `iteration_duration{scenario:<名前>}` の p95 と `checks` の率 | Build and Test（台本と最初の値）、Performance Validation（合否） |
| NFR2.22 | 新しい場面（仮の名前 `refCreateChecked`。確定は Build and Test）は、参照のカラムを持つ専用の参照元の表への作成で、保存前の段のいちばん重い道（必須の参照項目の件数の読み取り `countState` と、値の確かめ `check` の両方）を毎回通す: 参照元の表は、U1 が種に足す REFERENCE のちょうど 200 行の参照先を指す NOT NULL の参照のカラム（`AVAILABLE`）を1つ持ち、値は 200 行を順に回して必ず参照先にある値を送る（201 を返す成功の道）。1回の繰り返しに要求1つ、10 VU・3 分の constant-vus（既存の `biz` の場面と同じ）。setup は前の場面の状態に頼らず、書き込みのロールと利用者・作業ロールを自分で用意し、定義で書き込みが使えることを確かめる。参照元の表と DSL の参照の options は、種（`make-biz-seed.sh`）と `make-biz-dsl.mjs` に足し、行は架空で行の番号だけから決める。台本の変更の順序は、**U1 が参照先（200 行）と読み取りの場面のための変更を先に足し、その後に U2 が参照元の表と参照の options を足す**（U2 の参照元の表は U1 の参照先を指すため）。Build and Test の手順にこの順序を書く。書き込みのアカウントに新しい表の行の操作を与える | 種を入れた後に、参照先の行の数（200）と参照元の表が空であることを数えてから流す。`k6 inspect --include-system-env-vars` で場面と閾値の名前を確かめ、短い試し走り（1 VU・10 秒）で 201 と閾値の指標が出ることを確かめる（判定ではない） | Build and Test |
| NFR2.23 | 既存の場面の意味を保つ（Q3: A）: 性能の試験の DSL では `biz_new` の `ref_id` を参照（REFERENCE）にしない。`bizWritePoolLimit` は今のとおり外部キーの違反の道（最も長く書き込みの接続を持つ道）を通し、前の Intent の結果と並べて比べられるようにする。`bizCreate`・`bizUpdate` の目標（p95 < 1,000 ms）は変えず、参照の無い作成・更新が遅くならないことを並べて記録する（保存前の段は接続を借りない。BR1.2） | DSL の生成の台本の差分で `ref_id` に参照の options が無いこと。`bizWritePoolLimit` の 409 の数と書き込みのプールの待ちを前の Intent の記録と並べる | Build and Test、Performance Validation |
| NFR2.24 | 判定の決まりは U1 の性能の要件と同じにする（`project.md` の学び）: 判定は単調な時計の `iteration_duration` で行い、`http_req_duration` は並べて記録するだけ。繰り返しの中に待ちを置かない。トークンは `setup()` で取り、場面はトークンの有効期限より短い 3 分。台本全体を `caffeinate -dims` で包み、流す前後に `pmset` で眠りが無かったかを確かめる。配備した環境とは別の使い捨ての環境で、MySQL・MariaDB・PostgreSQL の3種類を1種類ずつ流し、どの種類でも NFR2.21 を満たす（AC3.5.4）。流す順は読み取りの場面の後（`perf/README.md` の見直しの案に従う。書き込みで参照元の表が増えるため） | 種類ごとに結果を並べて記録する。1種類でも満たさなければ Not Met とし、目標を緩めずに依頼者に諮る | Build and Test、Performance Validation |
| NFR2.25 | 測るまでは NFR2.21 を `Unverified` とし、持ち主の段（Performance Validation）を明記して引き継ぐ。満たさないときに目標の値や物差し（`iteration_duration` の p95）を変えない（`team.md`・`project.md`）。更新の確かめの道（更新前の行の読み取り＋値が変わった参照の `check`。件数は読まない）は、作成の道より読む量が少ないため場面を置かず、接続の数と時間の上限は結合テストで確かめる（`scalability-requirements.md` の NFR3.21、`reliability-requirements.md` の NFR3.25） | 結果の記録で、場面ごとに Met・Not Met・Unverified を書く | Performance Validation |
| NFR5.23 | この単位のコード生成の後と Build and Test で、`./gradlew verify` の時間と CI の検査の job の時間を測って記録する。CI の上限（60 分）の7割の約 42 分を超えたら、テストの分け方を依頼者に諮る（`team.md`）。3種類の対象DB の書き込みの結合テストが増えるため、増えた時間も並べて記録する | `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `verify` で実測する。CI は試行ごとの結果を確かめる（`project.md`） | Code Generation、Build and Test |

## 測らない範囲

合否の場面は NFR2.22 の 200 行の形（`countState` が `AVAILABLE`・`check` の当たりが1行）のままとし、次の経路の時間は k6 でも結合テストでも測らない。応答時間の目標（NFR2.21）の合否はこれらの経路を含まない。

- 参照先が 201 行以上で `countState` が `TOO_MANY` になる経路（必須の参照項目に値を送れば問い合わせずに 409、空なら 409。`check` の問い合わせが流れないため、200 行の形より軽い見込み）。結合テストでは結果（409 と問い合わせが流れないこと）だけを確かめる。
- 値のカラムが長い文字列の参照先での `check` の経路。結合テストでは結果と時間の上限（`reliability-requirements.md` の NFR3.24・NFR3.25）だけを確かめる。

## 補足

- 確かめの読み取りは、索引のある参照先（主キー・一意の `valueColumn`）なら1文あたり数 ms〜数十 ms の見込みで、既存の作成の時間に、借りる1回と2文（`countState`・`check`）が乗る。見込みは見立てで、合否は k6 で決める。
- 合否の回は場面ごとに1回とする。ミリ秒の単位の目標（接続の待ちの最大など）は置かないため、「数 ms の目標は複数回流す」の学びは当たらない。
- 書き込みで参照元の表の行が増えるため、作成の場面の後に読み取りの値が要るときは、`perf/README.md` の見直しの案のとおり対象DB を作り直してから流す。

## 後の段に回すこと

- **NFR 設計**: 値の違う `check` を束ねるかの決定（`scalability-requirements.md` の NFR2.28）が場面の文の数に与える影響の見積もり。
- **Build and Test**: k6 の場面と種・DSL の台本、`k6 inspect`、試し走り、最初の値の記録。手順には、種・DSL の台本の変更を U1 の分（参照先）を先に、U2 の分（参照元の表と参照の options）を後に足す順序を書く。
