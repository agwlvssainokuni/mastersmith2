# 規模の要件 — U1 u1-reference-read

出典: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/functional-spec.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/rules.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`

## 出典

- 要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`（NFR2・NFR3、FR1.2・FR2.3）
- ストーリー `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/user-stories/stories.md`（AC1.1.5・AC1.2.5・AC3.5.1・AC3.5.2・AC3.5.4）
- この単位の機能設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/`（`functional-spec.md` 4節・10節、`rules.md` の BR4.3・BR5.7・BR7.3・BR8.2・BR8.3・BR8.6・BR11.4）
- 契約 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`（C5）
- この段の答え `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-requirements/nfr-requirements-questions.md`（Q4: A・F1: A と「決まっていること」）
- コード（読み取りだけ）: `backend/src/main/java/cherry/mastersmith/businessdata/repository/ColumnValueCodec.java`（`LIST_TEXT_LIMIT` = 100、`DETAIL_TEXT_LIMIT` = 10,000）、`businessdata/domain/ColumnWriteRulesFactory.java`（書き込みの値の上限 `MAX_VALUE_LENGTH` = 10,000 文字）、`targetdb/config/TargetDbProperties.java`（読み取りのプールの上限 既定 10）
- `aidlc/spaces/default/memory/team.md`（接続の数のテスト、行の数の上限）と `project.md` の学び（上限を下げた k6 の場面を置かず `*ConnectionUsageIT` で決定的に確かめる）

## ID の対応表

全体の表は `security-requirements.md` の「ID の振り方」にある。

| 上流の ID | この単位での扱い | この単位の ID |
|---|---|---|
| NFR2 性能 | 部分（読む量とメモリ） | NFR2.6〜NFR2.9 |
| NFR3 接続 | 当たる（接続の数と同時の数） | NFR3.1〜NFR3.3 |

## 要件

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR2.6 | 読む行の数を、問い合わせの上限（`LIMIT`）と JDBC の取り出しの上限（`setMaxRows`）の両方で同じ値に絞る（`team.md` の (c)、BR8.6）: REFERENCE の選択肢と `countState` は 201、ピッカーの検索は 21（読み飛ばす数は `OFFSET`）、ラベルの1件読みは 1、`check` は 201。選択肢の上限は 200 件に固定する（FR1.2。DSL で変えない） | 参照先が 199・200・201・10,000 行の境界の結合テストで、選択肢の数・`TOO_MANY`・取り出した行の数が 201 を超えないことを確かめる（AC1.2.1・AC1.2.5）。文に付いた上限の値も確かめる | Code Generation |
| NFR2.7 | ラベルとピッカーの一覧の列は、既存の一覧と同じ 100 文字（`LIST_TEXT_LIMIT`）で切って読み、切ったことの印は返さない（Q4: A、BR7.3）。バイナリ・password のラベルは `null` | 100・101 文字のラベルの境界の結合テスト（3種類の DB）。切った結果に印が無いこと | Code Generation |
| NFR2.8 | 読み取り側の値の長さに上限を置く（F1: A）。`valueColumn` は SQL の側で 10,001 文字に切って読み（切った値は比べにも返す値にも使わない）、読んだ長さが 10,000 文字（アプリから書ける値の上限 `ColumnWriteRulesFactory.MAX_VALUE_LENGTH`）を超える行は、REFERENCE の選択肢とピッカーの検索の結果から外し、保存前の確かめ（`check`）では厳密に一致する行が無い場合と同じ「無い」（`NOT_FOUND_OR_DENIED`）とする。こうした値はアプリから保存できないため、外して失うものは無い。外した行は応答に印を付けず、選択肢の数・`TOO_MANY` の判定・ピッカーの次のページの有無の数にも数えない（数え方の細部は NFR 設計で決める）。最悪の量の見積もり（1要求）:<br>・選択肢の読み取り 201 行 × 値 10,001 文字 = 約 2,010,000 文字。Java の文字列で最大約 4 MB（UTF-16 の2バイト）、ラベル 201 × 100 文字 = 約 20,000 文字（約 40 KB）を足す<br>・応答の JSON は UTF-8 で、日本語の値なら最大約 6 MB（1文字3バイト）<br>・JDBC の読み取りの間の行の写しと JSON に書く間の写しを合わせ、1要求でヒープを最大約 10〜15 MB 使う見込み。10 VU が同時に最悪の参照先を読むと約 100〜150 MB<br>・ピッカーの1ページ（21 行 × (値 10,001 ＋ 一覧の列 × 100 文字)）は選択肢より小さい<br>・アプリの外で入れた長い値（種の `c_010` の 2,000,000 字の行など）を `valueColumn` にした DSL でも、この見積もりを超えて読まない | 3種類の対象DB の結合テストで、値の長さが 10,000 文字の行は選択肢・ピッカーの結果に入り `check` で見つかり、10,001 文字の行は選択肢・ピッカーから外れ `check` で `NOT_FOUND_OR_DENIED` になることを確かめる。2,000,000 文字の行を含む参照先でも、取り出した値の長さが 10,001 文字を超えないこと（文に付いた切る長さを確かめる）。ヒープは NFR 設計の捨ての試し（リポジトリの外）で、値 10,001 文字以上の行を 201 行持つ参照先の選択肢の応答を作り、GC の後と最大のヒープの増え方を測って見積もりを裏付ける（`project.md` の学び: 大きな入力のヒープの見積もりは試しで裏付ける）。結果の要点（版・設定・数値）を NFR 設計の成果物に写す | NFR 設計、Code Generation |
| NFR2.9 | 1つの定義に置ける REFERENCE の数には上限を置かない（機能設計の Q3: A。DSL のカラムの数の既存の上限が実際の上限）。数が多いときの時間は、定義の取得の全体の上限（`reliability-requirements.md` の NFR3.5、10 秒）で抑える。同じ参照先は1回だけ読む（BR4.7） | REFERENCE が 1・3・5 個の結合テスト（NFR3.1 と同じテストの中）で、同じ参照先を指す2つの項目で問い合わせが1回だけ流れることを確かめる | Code Generation |
| NFR3.1 | 1要求で借りる対象DB の読み取りの接続は1回まで。定義では読む REFERENCE をすべて同じ接続で順に読み、読む項目が無ければ借りない。検索・ラベルは1回借りて文を1つだけ流す（件数の文を流さない）。書き込みの接続は使わない（`team.md` の (j)、NFR3、BR8.2） | 既存の `BusinessDataConnectionUsageIT` と同じ形の結合テストで、REFERENCE が 0・1・3・5 個の定義、検索、ラベルの1件読みのそれぞれで、対象DB の読み取りの接続を借りた回数（0 または 1）と流れた文の数を決定的に数える（AC1.1.5・AC3.5.1）。`check`・`countState` は U2 が借りた接続を受けるだけで自分で借りないこと（U2 の保存前の確かめ全体の回数は U2 が確かめる） | Code Generation |
| NFR3.2 | 内部DB の接続を持ったまま対象DB を待たない。権限の写しを取り終えて内部DB の接続を返してから、対象DB の接続を借りる（BR8.3） | 応答しない対象DB（テストの中で開いた `ServerSocket`）を待っているあいだの内部DB の使用中の接続の数が 0 であることを、定義・検索・ラベルのそれぞれで確かめる（AC3.5.2） | Code Generation |
| NFR3.3 | 同時の数は、既存の対象DB の読み取りのプール（上限 既定 10）を一覧・詳細と共有する。1要求1本で、内部DB と2本を同時に持たないため、詰まりは「同時の要求の数 > プールの上限」のときだけ起き、借りる待ちの上限を超えると既存の 503 `BUSINESS_TARGET_DB_UNAVAILABLE` になる。プールの上限を下げた k6 の場面は置かず、接続の数は NFR3.1 の結合テストで決定的に確かめる（`project.md` の学び。読み取りだけで1要求1本のため、k6 では見積もりの誤りを見分けにくい）。代わりに、負荷の下での借りる待ちの長さは測らない。k6 の場面を流す使い捨ての環境の読み取りのプールの上限は、前の Intent の業務データの場面と同じ 5 に固定し、結果を前の Intent の値と並べて読めるようにする（`TargetDbProperties` の既定の 10 との差。配備したアプリの値は変えない） | NFR 設計で、置かない理由をこの形で確定する。Performance Validation の 10 VU の場面で、使い捨てのアプリの読み取りのプールの上限が 5 であることを流す前に確かめ、プールの待ちの時間切れの累計を並べて記録する（使い捨てのアプリにだけ指標を公開する。`project.md`）。累計が 0 でない場面の扱いは `performance-requirements.md` の NFR2.3 のとおり | NFR 設計、Performance Validation |

## 補足

- 値の上限 10,000 文字は書き込みの側の上限で、既存のコードには読み取りの側の上限が無い。この単位では NFR2.8 で読み取りの側にも上限を置き、10,000 文字を超える値の行を外す（F1: A）。
- 機能設計との差: 機能設計の BR7.3 は「`valueColumn` は切らずに読む」とする。NFR2.8 は、10,000 文字までの値を切らずに返す点は BR7.3 のままとし、読み取りの SQL で 10,001 文字に切ることと、超えた行を外すことを足す。承認済みの機能設計の文書は書き換えない。
- 参照先の選択肢はキャッシュしない（要件の範囲の外）。毎回読むため、同時の数と読む量は要求の数に比例する。

## 後の段に回すこと

- **NFR 設計**: NFR2.8 のヒープの捨ての試しと、外した行の数え方（選択肢の数・`TOO_MANY`・ピッカーの次のページ）の細部と、3種類の DB で 10,001 文字に切る SQL の書き方、NFR3.3 の置かない理由の確定。
- **Code Generation**: NFR2.6〜NFR2.9・NFR3.1・NFR3.2 のテスト。
