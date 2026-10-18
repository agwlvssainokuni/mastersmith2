# 性能の要件 — U3 u3-businessdata-screens

U3 は種類 `ui` の単位で、業務データの画面（一覧・詳細・作成・更新・削除の確かめ・開けないときの表示）を受け持つ。API の応答時間（p95 1 秒）は U1・U2 の k6 の場面と Performance Validation が受け持つため、この文書は画面の側の時間・要求の数・規模・検査の時間に限る。

## 上流の ID との対応

ID は U2 の割り振り（NFR1.7・2.10・3.7・4.20・8.7 から）に従う。NFR5・NFR6 は U4 の次（5.6・6.3）から振る。NFR7 は U4 と重ならないよう 7.8 から振る（「上流との差」の4）。

| 上流 | この単位の扱い | この文書の ID | ほかの文書の ID |
|---|---|---|---|
| NFR1 応答時間 | 部分（画面の側。API は U1・U2） | NFR1.7〜NFR1.10 | — |
| NFR2 規模 | 当たる（画面の描画と記憶） | NFR2.10・NFR2.11 | NFR4.22（記憶の上限）は `security-requirements.md` |
| NFR3 対象DB | 部分（画面は DB の種類で分岐しない） | NFR3.7 | — |
| NFR4 セキュリティ | — | — | `security-requirements.md`（NFR4.1〜4.4・4.20〜4.26） |
| NFR5 アクセシビリティ | — | — | `security-requirements.md`（NFR5.6〜5.10） |
| NFR6 言語 | — | — | `security-requirements.md`（NFR6.3・6.4） |
| NFR7 テスト | 部分（時間の記録） | NFR7.8 | NFR7.9〜7.13 は `security-requirements.md` |
| NFR8 運用 | — | — | `security-requirements.md`（NFR8.7） |

## 要件

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR1.7 | 画面の側の時間の目標（Q1: A）。API の時間を含めない。<br>・件数の選択で 100 を選んでから、100 件 × 100 カラム（各セル 100 字・切った印つき）の表が出るまで 1.0 秒以内<br>・メニューの項目を押してから、既定の 20 件の表が出るまで 0.5 秒以内<br>・行の「詳細」を押してから、100 カラム（10,000 字の値を含む）が出て見出しにフォーカスが移るまで 0.5 秒以内<br>・「更新」を押してから、100 カラムの入力が出るまで 0.5 秒以内 | 測りのテスト（NFR1.8） | Code Generation（B5・B6）、Build and Test |
| NFR1.8 | 測りの決まり（前の Intent の app-frame-ui の NFR2.8 と同じ形）。<br>・測りのテストは axe の E2E 185 の中に1件置き、表示の設定の 20 組の繰り返しの外で、既定の1組（テーマ light・文字の大きさ md・ブランドカラー blue・既定の幅）だけで動かす<br>・API は差し替えの口と型の付いた見本で遅れなく返す<br>・測り始めは操作（押す・選ぶ）を送った時点、測り終わりは判定が真になった後の次の `requestAnimationFrame`（`frontend/e2e/support/u7Measure.ts` と同じ形）<br>・各 5 回測り、中央値で判定する。最大と1回目も並べて記録する<br>・中央値が目標を超えたら Not Met、最大だけが超えたら Met として最大を記録する。測れない回があれば `Unverified` として理由を書く<br>・値は記録だけで、テストの成否にしない。Not Met なら承認の場で依頼者が扱いを決め、目標を緩めない。仮想化を入れるかはその場で諮る（`tech-stack-decisions.md`）<br>・記録は名前と時間だけにし、行の値を含めない | 5 回の値を添付に記録し、Build and Test が目標ごとの中央値・最大・1回目と判定を書く | Code Generation（B5・B6）、Build and Test |
| NFR1.9 | 要求の数と形。<br>・定義はテーブルごとに記憶にあれば読まない（D4。定義が変わったときだけ読み直す）<br>・一覧の操作（検索・ページ・並べ替え・件数）1回で、一覧の API を1本だけ送る<br>・要求に世代を付け、古い答えと画面を離れた後の答えを捨てる（D14）<br>・画面は独自の時間切れを置かず、決まった間隔の自動の読み直し（ポーリング）をしない<br>・保存中は 5 秒を超えたら「時間がかかっています」を出す（D23） | フックのテスト（`useTableDefinition`・`useRowSearch` など）で、操作ごとの要求の数と、遅らせた古い応答が新しい表示を上書きしないことを確かめる | Code Generation（B5・B6） |
| NFR1.10 | 初回の JavaScript。業務データの画面は `lazy` で登録し、入口に入れない。`frontend/scripts/check-bundle-size.mjs` の入口の値（gzip、目安 500KB、超えても警告だけ）と、業務データの画面の chunk の gzip の大きさを、B5・B6 の前と後で記録する | 登録のテストで `lazy` を確かめ、測った値をコード生成の成果物に書く | Code Generation（B5・B6） |
| NFR2.10 | 規模。100 カラム × 100 件の表を、仮想化なしで描く（セルごとの Tooltip を含む）。記憶には、最大 100 テーブル分（NFR2 の最大）の一覧の状態と、最大 100 個の主キーの印を持つ（Q3: A。上限の決まりは `security-requirements.md` の NFR4.22） | NFR1.7 の測りに 100 × 100 を含める。記憶の上限は単体テストで確かめる | Code Generation（B5・B6） |
| NFR2.11 | 長い値は U1 の上限（一覧 100 字・詳細 10,000 字。U1 の NFR2.5）に合わせる。画面ではそれ以上切らず、一覧は CSS の1行の省略で行い、`truncatedColumns` のセルに切ったことの印を付ける（D17・D18） | 画面の部品のテストで、受けた値の全体が読み上げの名前と Tooltip に入り、DOM の文字が切られていないこと | Code Generation（B5） |
| NFR3.7 | 画面は対象DB の種類で分岐しない。時差つきの型の値は UTC の `+00:00` を付けて出し、入力は `datetime-local` に「UTC」と添えて送るときに `+00:00` を付ける（U1 の NFR3.3、D15・D19）。E2E は PostgreSQL だけで流し、3種類の DB の振る舞いは U1・U2 の結合テストが受け持つ | 書式の関数の単体テスト。コードに DB の種類の分岐が無いことのレビュー | Code Generation（B5・B6） |
| NFR7.8 | 時間の記録。<br>・180・185 の時間と E2E の全体の時間を、B5・B6・Build and Test で `caffeinate -dims` を付けて測る<br>・流した後の `pmset -g log` に Sleep・DarkWake が入っていたら、結果を使わずに流し直す<br>・E2E の全体が基準（前の Intent の 232 件・約 10分35秒。値はコード生成の記録で確かめて写す）の 1.5 倍を超えたら、185 の組の分け方を依頼者に諮る（上限で落とさない。Q5: A）<br>・`./gradlew verify` と CI の時間を、B5・B6 の後と Build and Test で記録する（team.md） | 件数・始めと終わりの時刻・眠りの有無の記録 | Code Generation（B5・B6）、Build and Test |

## 受け入れた制約

- 画面の時間は手元の PC の1台で測り、統合を止めない（前の Intent と同じ）。
- 100 × 100 の表は仮想化なしで描くため、セルの数に比例して重くなる。目標を超えたときの手当ては承認の場で決める。

## 上流との差

1. **承認の場の決定**（`aidlc/spaces/default/intents/261016-business-data-views/construction/functional-design/memory.md` の Open questions）: `nullable` は U2 が足し、`formPart` の値域から `lookup` を外し、U3 は `formPart`・`nullable`・`required` が欠けたら形の崩れとする。機能設計の 6.2 の `text`・`lookup` の行は、`text` だけの行と読み替える（文書は書き換えない）。性能の要件には響かない。
2. **413 の行が増える**: 機能設計の 6.1 の表示の表に、413 `PAYLOAD_TOO_LARGE` の行が無い。Q4: A で文を足す（`security-requirements.md` の NFR4.23・NFR6.3）。性能の要件には響かない。
3. **team.md の E2E の読み方**: team.md の「業務データを表示する E2E は見本の対象DB（PostgreSQL）がコンテナで動いていることを前提にする」は、代表の流れ 180 に当てると読む。170 の書き換えと 185 は、型の付いた見本で応答を差し替える（Q2: A）。185 の中の測り（NFR1.8）も差し替えで行う。
4. **ID の振り方**: U2 の割り振りでは U3 の NFR7 は 7.7 からだが、U4 がすでに NFR7.4〜7.7 を使っているため、7.8 から振る。あわせて、U4 の NFR4.5〜4.9・NFR7.1〜7.7 は U1・U2 の番号と重なっている。U4 を直すかは承認の場で諮る。

## 後の段に回すこと

- **NFR 設計**: 記憶（印・一覧の状態・定義）の作りと上限での捨て方。`markClean()` の「1回だけ飛ばす」の旗の作り（R-12）。
- **Infrastructure Design**: E2E のアプリ（PC の上の `java -jar`）から見本の PostgreSQL に届く形。今の `compose.yaml` の `targetdb-postgres` は PC にポートを開けていない（137行）。
- **コード生成の計画**: R-04（作業ロールの確かめの後の画面）、R-06（保存の 403 の文）、主キーのカラムを更新で変えるとき（U2 で書けるか、保存の後の主キーの作り直し）。測りのテストの判定の名前と見本の生成の関数。

## U1・U2・U4 とそろえる点

- API の応答時間の判定（k6 の `biz` の場面）は U1・U2 のものを使い、U3 は測り直さない。
- 長い値の字数は U1 の NFR2.5、時差の扱いは U1 の NFR3.3 に合わせる。
- E2E の全体の時間は U4 の NFR7.2 と同じ回で測り、180・185 の分を分けて書く。
- 表示の組の 20 組（`frontend/e2e/support/displayCombos.ts`）は U4 と同じものを使う。

## Sources

- 質問と答え: `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/nfr-requirements/nfr-requirements-questions.md`（Q1〜Q5: A、まとめの確認: Looks correct）
- 要件: `aidlc/spaces/default/intents/261016-business-data-views/inception/requirements-analysis/requirements.md`（NFR1〜NFR3・NFR7、前提 A1）
- 機能設計: `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/functional-design/functional-spec.md`（D4・D14・D15・D17〜D19・D23・D30、6.1・6.2、11節・12節）、`frontend-components.md`（4節・7節）
- U1: `aidlc/spaces/default/intents/261016-business-data-views/construction/u1-businessdata-read/nfr-requirements/performance-requirements.md`・`scalability-requirements.md`（NFR2.5）・`reliability-requirements.md`（NFR3.3）
- U2: `aidlc/spaces/default/intents/261016-business-data-views/construction/u2-businessdata-write/nfr-requirements/nfr-requirements-questions.md`（番号の割り振り）
- U4: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/nfr-requirements/performance-requirements.md`（NFR7.2）、レビュー `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/nfr-requirements/units/u4-a11y-carryover/00d44a7a2452e6b7/1.json`（R-05）
- 前の Intent: `aidlc/spaces/default/intents/261004-role-menu/construction/app-frame-ui/nfr-requirements/performance-requirements.md`（NFR2.8・NFR2.10）、`aidlc/spaces/default/intents/261004-role-menu/construction/build-and-test/build-and-test-summary.md`（289〜291行）
- コード: `frontend/scripts/check-bundle-size.mjs`、`frontend/e2e/support/u7Measure.ts`、`frontend/playwright.config.ts`、`vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Table/Table.tsx`・`Tooltip/Tooltip.tsx`、`compose.yaml`（134〜166行）
- 決まり: `aidlc/spaces/default/memory/team.md`（Testing Posture）、`aidlc/spaces/default/memory/project.md`（学び）

## Assumptions & Open Questions

- 100 × 100 の表を仮想化なしで 1.0 秒以内に描けるかは未確認で、B5 の測りで確かめる（NFR1.7）。
