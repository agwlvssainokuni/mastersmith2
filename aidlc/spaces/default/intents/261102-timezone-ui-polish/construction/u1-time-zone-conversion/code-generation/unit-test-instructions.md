# 単位のテストの手順 — u1-time-zone-conversion

出典: コード生成の計画（`code-generation-plan.md`）、NFR Design（`nfr-design/security-design.md` の SD7・`logical-components.md` の LC7）、`team.md` Testing Posture。

U1 は、画面の側の純粋な日時変換の関数群（`frontend/src/shared/format/timeZone/`）。バックエンド・対象DB・コンテナを使わず、Vitest（jsdom）だけで流せる。方法は test-after（部品ごとに実装 → その部品のテスト → 通ってから次へ）。テストの量は Standard（部品ごとに 5〜8 件）。

## テストの枠組みと設定

- 枠組み: Vitest（`frontend/vitest.config.ts`）＋ jsdom ＋ `fast-check`（性質ベース）。すべて既に入っている。新しい依存は足さない。
- 置き場: 対象と同じ場所に `*.test.ts`（`frontend/src/shared/format/timeZone/`）。テストの説明文（`describe`・`it`）は英語、テストデータは日本語でよい。
- 時刻・タイムゾーン: テストは機械の既定のタイムゾーンに結果を預けない。名前は必ず明示に渡す。`TZ` やロケールを変えて確かめるテストは、その値を明示に指定する（手元は `Asia/Tokyo`、CI は UTC）。実時刻・`sleep` に頼らない（全関数は引数だけから結果を決める）。

## この単位だけを流すコマンド（`frontend/` で実行）

```
NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/shared/format/timeZone/
```

この単位のフォルダの全テストを流す（フォルダの絞り込み）。1つのファイルだけを流すときは、`src/shared/format/timeZone/formatInstant.test.ts` のように、パスを正確に書く。最初のテストを書く前（Step 1）に、このコマンドが実行できる（フォルダが空なら「テストが無い」で終わる）ことを確かめる。

## 期待する件数（Standard）

| ファイル | 件数の目安 | 内容 |
|---|---|---|
| `timeZoneGate.test.ts` | 7〜8 | 名前の検査・キャッシュ・整形器の生成回数 |
| `wallClock.test.ts` | 7〜8 | 時差の秒・切り替えの前後・端数の時差・存在しない/重なる/1日が飛ぶ・範囲外の年 |
| `instantText.test.ts` | 7〜8 | 読み取り・長さの境界・異常な入力・`preserveInstant`・`compareInstants` |
| `formatInstant.test.ts` | 7〜8 | 日付の変わり目・夏時間・端数・`unreadable`・`invalid-time-zone` |
| `parseLocalDateTime.test.ts` | 8〜10 | 書式・範囲・`gap`・`overlap`・`overlapChoice`・UTC の年の範囲外 |
| `conversionBoundary.test.ts` | 4〜5 | `console` が 0 回・`TZ`/ロケール不変・名前の最長 |
| `conversionProperties.test.ts` | 6 | 例外が出ない・同じ入力は同じ結果・往復・端数の桁・解釈できない名前（失敗時の乱数の種を記録する） |
| `conversionPerformance.test.ts` | 1 | 1万の値の変換（5回の測りの中央値） |

合計は約 55 件（性質ベースと性能の測りを含む）。

## 期待するカバレッジ

- 全体の合計の下限（`team.md`）: 行 80%・分岐 70%（`frontend/vitest.config.ts` の `thresholds`）。**この下限は、プロジェクト全体のテストを流す Build and Test が判定する**（この単位だけを流すと、ほかの単位のソースの分が足りず、下限で終了コードが非 0 になることがある。そのとき、選んだテストがすべて通っていれば、テストの失敗ではない）。下限の設定は変えない。
- この単位のフォルダのカバレッジの値は、`npm run test:coverage`（全体のコマンド。この単位のコマンドとは別に流す）で読み、`code-summary.md` に記録する。目安は行・分岐とも 90% 以上（純粋な関数のため）。

## 性能の測り（NFR2.1.1・NFR2.1.2）

- 固定の時点 1 万個（夏時間の地域・端数の桁 0・3・6 を混ぜる）を、同じタイムゾーンで `formatInstant` に通し、`performance.now()` の差を5回測る。判定は5回の中央値で、合計 500 ミリ秒以内・1つあたりの平均 0.05 ミリ秒以内。目標は緩めない。
- 失敗時は、5回の値と環境（Node の版・CPU）をテストの出力に出す。測った環境を `code-summary.md` に書く。
- 捨ての試しの値（約 60 ミリ秒 / 1 万）の数倍まで増えても、目標内に収まる見立て（`nfr-design/logical-components.md` の LC7）。

## モック・スタブの方針

- `Intl` はモックしない（ブラウザの実際の `Intl` の結果を確かめるため）。ただし、整形器の生成回数を数えるテストと、`Intl.DateTimeFormat` が例外を投げる場合のテストは、`vi.spyOn` で包んで数える・投げさせる（元の実装を呼び出す）。
- `console` は `vi.spyOn(console, '<method>')` で呼び出しが 0 回であることを確かめる。

## テストデータ

- 固定の時点・タイムゾーン・端数の表（`it.each`）で表にして書く。テストデータに個人に関する値・実在の宛先を置かない。
- 性質ベースの生成は、表示の年が 0001〜9999 に収まる時点（往復の性質）と、任意の文字列（例外が出ない性質）に分ける。失敗時は `fast-check` の種と反例を出し、手元で再現できるようにする。

## この単位に当たらないテスト

- E2E の流れ・画面のアクセシビリティの検査（axe）は、U1 が画面を持たないため、この単位では足さない。`frontend/src` の本体に触れるため、統合の前に既存の E2E（`./gradlew e2eTest`）を流して通すことだけは行う（`team.md`）。
- 時差を持たない型が時差を変えても値が変わらないことの確かめは、呼ぶ側が通さないことの確かめで、U4 が受け持つ。
