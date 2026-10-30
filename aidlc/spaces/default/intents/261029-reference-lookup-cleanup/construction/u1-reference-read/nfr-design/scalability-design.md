# 規模の設計 — U1 u1-reference-read

参照先の読み取りが、接続・同時の数・読む量・メモリを要求の数に比例する範囲に抑える作りを決める。配備先は開発者の PC 上のコンテナで、1つのアプリのインスタンスを前提にする（自動の増減は無い）。

## 出典

- この単位の NFR 要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-requirements/scalability-requirements.md`（NFR2.6〜NFR2.9・NFR3.1〜NFR3.3）
- この単位の機能設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/`（`rules.md` の BR4.7・BR8.2・BR8.3・BR8.6）
- 契約 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`（C5）
- 試しの結果 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-design/trial-results.md`
- `aidlc/spaces/default/memory/project.md` の学び（上限を下げた k6 の場面を置かず、接続の数は `*ConnectionUsageIT` で決定的に確かめる）

## 1. 状態を持たない

- 参照先の選択肢・ピッカーの結果・ラベルは保存もキャッシュもしない（`tech-stack-decisions.md` の選ばなかったもの）。権限と参照先の変更を次の要求ですぐ映すため。
- 1要求の中だけで、同じ参照先を2つの項目が指すときは1回だけ読む（BR4.7、NFR2.9）。

## 2. 接続の使い方

| 処理 | 内部DB | 対象DB の読み取りの接続 | 流れる文 | 要件 |
|---|---|---|---|---|
| 定義（`VIEW`） | 写しの読み取り | 借りない | 0 | NFR7.3 |
| 定義（`CREATE`・`UPDATE`、REFERENCE が 0） | 写しの読み取り | 借りない | 0 | NFR3.1 |
| 定義（REFERENCE が n、参照先が m 種類） | 写しの読み取り | 1回 | m（同じ参照先は1回） | NFR3.1・NFR2.9 |
| ピッカーの検索 | 写しの読み取り | 1回 | 1（件数の文を流さない） | NFR3.1 |
| ラベルの1件読み | 写しの読み取り | 1回 | 1 | NFR3.1 |
| `check`・`countState` | 読まない（U2 の写しを受ける） | U2 が借りた接続を受ける（自分で借りない） | 呼ぶごとに 1 | NFR3.1 |

- 内部DB の接続は、写しを取り終えて返してから対象DB の接続を借りる（BR8.3、NFR3.2）。`ReferenceLookup` はトランザクションを開かない。
- 書き込みの接続は使わない。
- 確かめ: 既存の `BusinessDataConnectionUsageIT` と同じ形の結合テストで、REFERENCE が 0・1・3・5 個の定義（同じ参照先を指す2つの項目を含む）、検索、ラベルについて、対象DB の読み取りの接続を借りた回数と流れた文の数を決定的に数える。応答しない対象DB（テストの中の `ServerSocket`）を待つ間に内部DB の使用中の接続が 0 であることを、定義・検索・ラベルで確かめる。

## 3. 同時の数（NFR3.3 の確定）

- 参照の読み取りは、既存の対象DB の読み取りのプール（上限 既定 10）を一覧・詳細と共有する。新しいプールを作らない。
- 1要求は対象DB の接続を1本だけ持ち、内部DB と2本を同時に持たない。そのため詰まりは「同時の要求の数 > プールの上限」のときだけ起き、借りる待ち（上限 3 秒）を超えると既存の 503 `BUSINESS_TARGET_DB_UNAVAILABLE` になる。
- 上限を下げた k6 の場面は置かない（確定）。理由: 1要求1本の読み取りだけで、2本使いの経路が無いため、k6 では見積もりの誤りを見分けにくい。接続の数は2節の結合テストで決定的に確かめる（`project.md` の学び）。代わりに、負荷の下の借りる待ちの長さは測らない（受け入れた制約）。
- Performance Validation の使い捨ての環境では、読み取りのプールの上限を 5 に固定して前の Intent の業務データの場面と並べて読む。流す前に 5 であることを確かめ、プールの待ちの時間切れの累計を記録する（使い捨てのアプリにだけ指標を公開する）。配備したアプリの値は変えない。

## 4. 読む量の上限

| 読み方 | 読む行（`LIMIT`・`setMaxRows`） | 値の長さ | 表示の長さ |
|---|---|---|---|
| REFERENCE の選択肢・`countState` | 201 | 10,000 字以下の行だけ（10,001 字で切って読む） | ラベル 100 字 |
| ピッカーの検索 | 21（`OFFSET (page-1)*20`） | 同じ | ラベル・一覧の列 100 字 |
| ラベルの1件読み | 1 | 同じ | ラベル 100 字 |
| `check` | 201（比べるのは 200 行まで） | 同じ | 出さない |

選択肢の上限 200 件は固定で、DSL で変えない（FR1.2）。1つの定義に置ける REFERENCE の数には上限を置かず、時間は全体の上限（`reliability-design.md` 1節）で抑える（NFR2.9）。

## 5. メモリの容量

試しの値（`trial-results.md` 2.4、201 行 × 10,000 字の日本語）:

| 見積もり | 要件（NFR2.8） | 試し |
|---|---|---|
| 1要求で残るヒープ | 約 10〜15 MB | 値の文字列 3.9 MB ＋ ドライバーの写し・JSON の写し（各約 6 MB）＝ 約 16 MB を超えない |
| 1要求の一時の割り当て | — | 38.8 MB（すぐ回収される） |
| 10 VU の同時の最悪 | 約 100〜150 MB | 残る量 約 40 MB、一時の割り当て 約 390 MB／巡 |
| 応答の JSON | 最大約 6 MB | 5.8 MB |

アプリのヒープの設定（`MaxRAMPercentage` など）は変えない。2,000,000 字の値を持つ参照先でも SQL で除くため、この見積もりを超えて読まない（切らずに読むと1要求で約 420 MB を割り当てる）。

## 6. 規模の閾値と見直しの合図

| 合図 | 見直すこと |
|---|---|
| Performance Validation で `refPickerContains` が MySQL・MariaDB で 3,000 ms を超える、またはプールの待ちの時間切れが 0 でない | 目標と物差しを緩めず Not Met として依頼者に諮る（NFR2.3） |
| 参照先に 10,000 行を大きく超える表を選ぶ DSL が増える | 選択肢はもともと表の全部を並べるため、REFERENCE ではなく LOOKUP を勧める（DSL の作り方の案内。この単位では仕組みを足さない） |
| 深いページのピッカー（PostgreSQL で 1,000 ページ目 約 0.5 秒）が問題になる | Performance Validation の参考の値を見てから後の Intent で扱う |

## 上流との差

なし（NFR3.3 の「NFR 設計で確定する」を3節で確定した）。

## 後の段に回すこと

- **Code Generation**: 2節の接続の数の結合テスト、4節の上限の境界の結合テスト（`reliability-design.md` 4節）。
- **Performance Validation**: 読み取りのプールの上限 5 の確かめと、待ちの時間切れの累計の記録。
