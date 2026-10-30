# 性能の要件 — U3 u3-reference-screens

出典: 機能設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/functional-design/functional-spec.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/functional-design/frontend-components.md`、要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`（NFR2・NFR3）。U3 は種類 `ui` の単位で `rules.md` を持たない。

U3 は作成・更新の画面に足す参照の部品（`ReferenceSelectField`・`ReadonlyReferenceField`・`LookupField`・`LookupPickerDialog`）を受け持つ。参照先の読み取りの API の応答時間（要件 NFR2 の線）は U1 の k6 の場面と Performance Validation が受け持つため、この文書は画面の側の要求の数と形・規模・初回の JavaScript の量・検査の時間に限る。

## 上流の ID との対応

枝番は単位の間で重ならないよう、U3 は `NFRx.41` から振る（質問の「決まっていること」）。

| 上流 | この単位の扱い | この文書の ID | ほかの文書の ID |
|---|---|---|---|
| NFR1 セキュリティ | — | — | `security-requirements.md`（NFR1.41〜NFR1.47） |
| NFR2 性能 | 部分（画面の側。API の時間は U1） | NFR2.41〜NFR2.45 | — |
| NFR3 接続 | 当たらない（対象DB・内部DB の接続を持たない。接続の数は U1・U2 の結合テスト） | — | — |
| NFR4 アクセシビリティ | — | — | `security-requirements.md`（NFR4.41〜NFR4.45） |
| NFR5 テスト | — | — | `security-requirements.md`（NFR5.41〜NFR5.43） |
| NFR6 多言語 | — | — | `security-requirements.md`（NFR6.41・NFR6.42） |
| NFR7 互換性 | — | — | `security-requirements.md`（NFR7.41） |
| NFR8 運用 | 当たらない（U4・U5） | — | — |
| NFR9 供給網 | — | — | `security-requirements.md`（NFR9.41）、`tech-stack-decisions.md` |

## 要件

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR2.41 | 画面の側の時間の目標は置かない（Q1: A）。応答時間の目標（選択肢の読み取りとピッカーの一致の検索は p95 1 秒未満、部分一致の検索は p95 3 秒未満）は U1 の k6 の場面で測り、画面では測り直さない。画面は、要求の状態の移り変わりが正しいことだけを確かめる。<br>・ピッカーで「検索」を押すと「検索中」（`role="status"`）が出て「検索」が押せなくなり、答えが来たら結果・0 件・失敗のどれか1つに切り替わり「検索中」が消える（D24〜D27）<br>・更新の画面の LOOKUP の項目は、ラベルの1件読みを待つ間 `aria-busy` で値だけを出し、答えが来たら「ラベル (値)」か値だけに切り替わる（D18）<br>・作成・更新の画面の定義の読み込みの間は、既存のとおり画面全体が「読み込み中」（D10） | 画面の部品のテストで、遅らせた応答を使い、待つ間の表示と答えの後の表示を `waitFor` で確かめる | Code Generation |
| NFR2.42 | 要求の数と形。<br>・作成・更新の画面を開くたびに、定義を `purpose` つきで1回読む。機能の中の定義の記憶を読み書きしない（D1・D2）<br>・ピッカーの「検索」1回・ページ送り1回・「再度検索」「前のページへ」1回で、検索の要求を1本だけ送る。空の条件だけ・1,000 文字を超える条件・型として読めない一致の条件では送らない（D23）<br>・要求に世代を付け、古い答え・ダイアログを閉じた後・画面を離れた後の答えを捨てる（D18・D24・D29）。検索中の `Table` のページ送りの押下は捨てる<br>・更新の画面のラベルの1件読みは、値が空でない LOOKUP の項目ごとに1回だけで、REFERENCE の項目と一覧・詳細では読まない（D18）。同時に送る数の上限を置くかは NFR 設計で決める（機能設計の 9節）<br>・画面は独自の時間切れを置かず、決まった間隔の自動の読み直し（ポーリング）をしない。保存中の 5 秒を超えた知らせ（既存）は変えない。検索で同じ知らせを使うかは NFR 設計で決める（機能設計の 9節） | フックと部品のテストで、操作ごとの要求の数、送らない場合に要求が無いこと、遅らせた古い応答が新しい表示を上書きしないことを確かめる | NFR 設計（同時の数・知らせ）、Code Generation |
| NFR2.43 | 規模。<br>・REFERENCE の選択肢は最大 200 件（U1 の上限）を、仮想化なしでサーバーの並びのまま描く（`select`・`radio`。D6）。`radio` で 200 件を描くときの見た目と時間は NFR 設計で確かめる（機能設計の 9節）<br>・ピッカーの結果は1ページ 20 件で、件数を数えない（D25）<br>・長いラベル・値は画面で切らず、CSS で折り返して全文を DOM に残す（D25） | 200 件の選択肢と 20 件の結果を描く画面の部品のテスト。長い名前（100 文字以上）の全文が DOM にあること | NFR 設計（200 件の `radio`）、Code Generation |
| NFR2.44 | 初回の JavaScript。参照の部品は業務データの画面（`features/tables`、`lazy` で登録済み）の中に置き、入口に入れない。既存の確かめ `frontend/scripts/check-bundle-size.mjs`（入口から静的に読まれるファイルの gzip の合計、目安 500 KB を超えたら警告。今は約 165 KB）をそのまま使い、入口の値と業務データの画面の chunk の gzip の大きさを、変更の前と後で記録する。<br>・業務データの画面の chunk は、既存のスクリプトが測らないため、同じビルドの結果 `dist/.vite/manifest.json` を読んで測る: キーが `src/features/tables/` で始まる項目（`lazy` で読む一覧・詳細・作成と更新の画面。`isDynamicEntry`）を起点に、その `imports` を静的にたどって集めたファイルのうち、入口の合計に入るファイルを除いた `.js` をそれぞれ gzip にし、合計する（入口の測りと同じ `gzipSync` の数え方）。起点ごとの値と合計を記録する。測り方を一時の手順とするか、スクリプトに足すかは NFR 設計・コード生成で決める | 登録のテストで `lazy` が保たれていること。測った値をコード生成の成果物に書く | Code Generation |
| NFR2.45 | 時間の記録。<br>・E2E の 190（代表の流れ）・195（axe）の時間と E2E の全体の時間を、`caffeinate -dims` を付けて測る。流した後の `pmset -g log` に Sleep・DarkWake が入っていたら、結果を使わずに流し直す（`project.md` の学び）<br>・195 の組（ブランドカラー×テーマ×状態×幅）で E2E の全体が、変更の前の E2E の全体の時間の 1.5 倍を超えたら、組の分け方を依頼者に諮る（上限で落とさない）<br>・`./gradlew verify` と CI の時間を、この単位の Bolt の後と Build and Test で記録する（`team.md`） | 件数・始めと終わりの時刻・眠りの有無の記録 | Code Generation、Build and Test |

## 受け入れた制約

- 画面の側の遅さは測らない（Q1: A）。画面が遅くても k6 の目標では見つからないが、前の Intent の業務データの画面で画面の時間の目標を置いた経緯（100 件×100 カラムの表）に比べ、この単位が描くのは最大 200 件の選択肢と 20 件の結果で小さい。
- ラベルの1件読みは LOOKUP の項目の数だけ並んで送られる。同時の数の上限は NFR 設計で決め、決めるまでは項目の数をそのまま送る前提とする。

## 後の段に回すこと

- **NFR 設計**: ラベルの1件読みの同時の数の上限、200 件の `radio` の描画の確かめ、検索に「時間がかかっています」の知らせを使うか（機能設計の 9節）。
- **Code Generation**: NFR2.44 の値の記録、NFR2.45 の時間の記録。
- **Performance Validation**: 参照先の読み取りの応答時間（U1 の受け持ち）。この単位からは引き継がない。

## Sources

- 質問と答え: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/nfr-requirements/nfr-requirements-questions.md`（Q1: A と「決まっていること」）
- 要件: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`（NFR2・NFR3）
- 機能設計: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/functional-design/functional-spec.md`（D1・D2・D6・D10・D18・D23〜D27・D29、9節）、`frontend-components.md`（4節・7節・8節）
- U1 の性能の要件: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-requirements/performance-requirements.md`（k6 の場面と目標）
- 書き方の手本: `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/nfr-requirements/performance-requirements.md`
- コード: `frontend/scripts/check-bundle-size.mjs`、`frontend/package.json`
- 決まり: `aidlc/spaces/default/memory/team.md`（Testing Posture）、`aidlc/spaces/default/memory/project.md`（学び: `caffeinate`・`pmset`）

## Assumptions & Open Questions

- [assumption] `features/tables` は今も `lazy` で登録されている（前の Intent の決め）。Code Generation で登録のテストで確かめる。
- [assumption] NFR2.45 の 1.5 倍の線は前の Intent の業務データの画面と同じ値を使う。変更の前の E2E の全体の時間は Code Generation の最初の実行で測って写す。
- 測っていないため、NFR2.41〜NFR2.45 はすべて Code Generation と Build and Test で確かめるまで `Unverified` とする。
