# 性能の要件 — U1 u1-reference-read

出典: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/functional-spec.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/rules.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`

## 出典

- 要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`（NFR2、NFR5、前提 A6）
- ストーリー `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/user-stories/stories.md`（AC3.5.4）
- この単位の機能設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/`（`functional-spec.md` 4節・10節、`rules.md` の BR5.7・BR8.2・BR8.6）
- この段の答え `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-requirements/nfr-requirements-questions.md`（Q3: A と「決まっていること」）
- 単位の分け方の答え `inception/units-generation/` の質問（Q3: A。k6 の新しい場面は U1 のコード生成では書かず、Build and Test で書いて流す）
- k6 の台本 `perf/k6/scenarios.js`（業務データの場面 `bizSearchFirst`・`bizSearchContains` など、部分一致の線 `BIZ_P95_LIMIT_MS` の 3000 ms）と種 `perf/biz/make-biz-seed.sh`（`bizperf` の `biz_item` 100,000 行、`c_010` の部分一致の目印）、手順 `perf/README.md`
- `aidlc/spaces/default/memory/team.md`・`project.md`（k6 の判定、使い捨ての環境、`verify` の時間、目標の物差しを変えない）

## ID の対応表

全体の表は `security-requirements.md` の「ID の振り方」にある。この文書に書くのは次のもの。

| 上流の ID | この単位での扱い | この単位の ID |
|---|---|---|
| NFR2 性能 | 当たる（応答時間と測り方） | NFR2.1〜NFR2.5 |
| NFR5 テスト | 部分（`verify` と CI の時間の記録） | NFR5.3 |

## 要件

| ID | 要件 | 測り方・確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR2.1 | 参照先の読み取りの応答時間は、要件 NFR2 の線を目標とし、緩めない。場面ごとに k6 の `checks` の率 1 も満たす。<br>・選択肢つきの定義の取得（REFERENCE の参照先がちょうど 200 行、`purpose` が `CREATE`）: p95 < 1,000 ms<br>・候補が多い定義の取得（参照先が 201 行で `TOO_MANY`）: p95 < 1,000 ms<br>・ピッカーの一致の検索（`EQUALS`）: p95 < 1,000 ms<br>・ラベルの1件読み: p95 < 1,000 ms<br>・ピッカーの部分一致の検索（`CONTAINS`）: p95 < 3,000 ms（前の Intent で依頼者が決めた緩めた線） | 場面ごとの `iteration_duration{scenario:<名前>}` の p95 と `checks` の率。場面の仮の名前は `refDefinition200`・`refDefinition201`・`refPickerEquals`・`refLabel`・`refPickerContains`（確定は Build and Test。既存の `biz` の場面と同じく 10 VU・3 分の constant-vus） | Build and Test（台本と最初の値）、Performance Validation（合否） |
| NFR2.2 | k6 の場面の判定の決まり（`project.md` の学び）: どの場面も1回の繰り返しに要求1つとし、繰り返しの中に待ちを置かない。判定は単調な時計の `iteration_duration` で行い、`http_req_duration` は並べて記録するだけにする。VU ごとのトークンは `setup()` で取り、場面の長さはトークンの有効期限より短い 3 分にする。setup は前の場面の状態に頼らず、自分で前提（利用者・ロール・DSL）を作る。台本全体を `caffeinate -dims` で包み、流す前後に `pmset` で眠りが無かったかを確かめる。配備した環境とは別の使い捨ての環境（`perf/README.md`）で流す | 台本を書いたとき、`k6 inspect --include-system-env-vars` で場面と閾値の名前を確かめる。短い試し走り（各場面 1 VU・10 秒）で閾値の名前の指標が出ることを確かめる（判定ではない） | Build and Test |
| NFR2.3 | 対象DB を使う場面は、MySQL・MariaDB・PostgreSQL の3種類で、1種類ずつ使い捨ての環境を作って流し、どの種類でも NFR2.1 を満たす（AC3.5.4） | 種類ごとに結果を並べて記録する。1種類でも満たさなければ Not Met とし、目標を緩めずに依頼者に諮る | Performance Validation |
| NFR2.4 | 測るデータは既存の perf の種を使い回す（Q3: A）。LOOKUP の参照先は既存の大きな表 `biz_item`（100,000 行。部分一致は既存の `bizSearchContains` と同じ `c_010` の目印を当てる）。REFERENCE の参照先は、小さなコードの表をちょうど 200 行と 201 行の2つ、種に足す（行は架空で、行の番号だけから決まる。乱数を使わない）。参照元の表と DSL の options（REFERENCE・LOOKUP）も同じ種と `perf/make-biz-dsl.mjs` に足し、種と DSL を同時に変える | 種を入れた後、表ごとの行の数（200・201・100,000）と、部分一致の目印の当たりの数を数えてから流す（`project.md` の学び）。MySQL の種は文字の組 `utf8mb4` で流す | Build and Test |
| NFR2.5 | 測るまでは各目標を `Unverified` とし、持ち主の段（Performance Validation）を明記して引き継ぐ。満たさないときに目標の値や物差し（`iteration_duration` の p95）を変えない（`team.md`・`project.md`）。保存前の確かめつきの作成の場面（AC3.5.4 の (3)）は U2 の要件で扱う | 結果の記録で、場面ごとに Met・Not Met・Unverified を書く | Performance Validation |
| NFR5.3 | この単位のコード生成の後と Build and Test で、`./gradlew verify` の時間と CI の検査の job の時間を測って記録する。CI の上限（60 分）の7割の約 42 分を超えたら、テストの分け方を依頼者に諮る（`team.md`）。3種類の対象DB の結合テストが増えるため、増えた時間も並べて記録する | `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `verify` で実測する。CI は試行ごとの結果を確かめる（`project.md`） | Code Generation、Build and Test |

## 補足

- 合否の回は場面ごとに1回とする。ミリ秒の単位の目標（接続の待ちの最大など）は置かないため、「数 ms の目標は複数回流す」の学びは当たらない。
- 選択肢の読み取りは件数の文を流さず、読む行も 201 までのため、表の全部を数える既存の一覧（`bizSearchFirst`）より軽い見込み。部分一致は索引を使えず表の全部を読むため、前の Intent の `bizSearchContains` と同じく MySQL・MariaDB で線に近づく見込みがある（前の Intent の Performance Validation の Not Met の記録）。件数の文が無い分だけ1文で済む。
- 定義の取得の時間には、内部DB の写しの読み取りと、REFERENCE の数だけの問い合わせが乗る。場面の DSL は REFERENCE を1つとし、REFERENCE が複数のときの全体の上限は `reliability-requirements.md` の NFR3.5 で守る。

## 後の段に回すこと

- **NFR 設計**: 接続プールが上限に届く場面を置かない理由（`scalability-requirements.md` の NFR3.3）の確定と、種の表の名前と形の決め。
- **Build and Test**: k6 の場面と種・DSL の台本、`k6 inspect`、試し走り、最初の値の記録。
- **Performance Validation**: NFR2.1・NFR2.3 の合否の回。

## U2・U3 とそろえる点

- 場面の名前は `ref` から始める。U2 の保存前の確かめつきの作成の場面も `ref` で始め、判定の決まり（NFR2.2）と種（NFR2.4）を共有する。
- 枝番は U1 が `.1`〜`.20`、U2 が `.21`〜 を使う。
