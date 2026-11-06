# コード生成のまとめ（Intent 261104-tz-options-perf）

> Step 1〜11 の記録（2026-11-06〜07）。Step 3 の後に、依頼者の決定で計画を Temporal の道を足す形に直し、承認し直した（計画の「計画の直し」の節）。

## 作った・直したファイル

コミット 23faf271（共有の関数）:

- `frontend/src/shared/format/timeZone/offsetLabel.ts`（新規）: 時差のラベルを求める `timeZoneOffsetText(timeZone, epochSeconds): string | null`（`index.ts` から出す）。道を選ぶ内部の口 `timeZoneOffsetTextWith(timeZone, epochSeconds, temporal | null)`。`TemporalLike` はこのファイルの中の最小の型で、`temporalFrom(candidate)` が形を確かめて `globalThis.Temporal` を読む（`tsconfig.json` の `lib` は変えていない）。処理の順: 名前の形の検査 → 時点の秒の検査（整数・年 0001〜9999）→ 道ごとに時差を求める → 表示の年が 0001〜9999 に収まるかの検査（`formatInstant` が読めない値にする範囲と同じ）。Temporal の道で秒未満の端数が出たら null（2 つの道の結果を分けないため）。
- `frontend/src/shared/format/timeZone/offsetLabel.test.ts`（新規、93 件）
- `timeZoneGate.ts`: `passesNameCheck` を export。キャッシュに入れない `createUncachedFormatter(name)` を足し、`getFormatter` はそれで作る形にした（動きは同じ）。
- `wallClock.ts`: `wallAt` の parts の読み取りを `wallWith(formatter, epochSeconds)` に切り出した（動きは同じ）。
- `formatInstant.ts`: `formatOffset` を export しただけ。
- `index.ts`: `timeZoneOffsetText` を足した（内部の部品は出していない）。

コミット 7e94498d（組み立て）:

- `frontend/src/features/preferences/buildTimeZoneOptions.ts`: 時点の秒を組み立てごとに1回 `Math.floor(now.getTime()/1000)` で求め、`timeZoneOffsetText` を呼ぶ形にした。`canInterpretTimeZone`・`formatInstant`・`offsetOf` は使わない。`WeakMap` と返す形はそのまま。
- `buildTimeZoneOptions.test.ts`（10 件 → 12 件）

ESLint・tsconfig・カバレッジの設定と `vendor/` は変えていない。

## 決めたこと

- 時差は、Temporal があれば `Temporal.Instant` → `toZonedDateTimeISO(name).offsetNanoseconds` で求め、整形器を作らない。無ければ（Safari など）名前ごとに整形器 1 つ・`formatToParts` 1 回の Intl の道で求める（依頼者の決定 B）。
- 組み立て用の整形器は共有のキャッシュに入れず、使い捨てる。同じ配列の2回目以降は今の `WeakMap` で求め直さない（R-05 への答え）。
- 回数の上限: Temporal の道は生成 0・`formatToParts` 0、Intl の道は1件あたり各 1 回以下。

## Step 3b Temporal の道の捨ての試し

- 置き場: `~/.mastersmith-scratch/tz-options-perf/probe3.mjs`。Node v26.13.0・ICU 78.3・tz 2026e・Apple M2、名前 418 個（UTC を足して 419 個）。
- 一致: 419 個 × 31 の時点の 12,989 組で、Temporal の道・Intl の道（era つき 7 項目・`formatToParts` 1 回）とも、今の形との食い違いは 0。
- 名前の受け入れ: 469 件（全部の名前と、大文字小文字の違い・別名・時差の形・斜線の誤り・空白や改行・使えない文字・空・64 文字・65 文字の 50 件）で、今の `canInterpretTimeZone`・Temporal の道・Intl の道の食い違いは 0。
- 5 回の時間（中央値）: 今の形 21.75 ms、Temporal の道 0.41 ms（0.019 倍）、Intl の道 18.64 ms（0.857 倍）。
- ブラウザの対応（2026-11-06、MDN の browser-compat-data の `Temporal/Instant.json`・`Temporal/ZonedDateTime.json`）: Chrome 144・Edge（Chrome と同じ）・Firefox 139・Node.js 26.0.0 は対応、Safari（iOS も）は preview。Safari では Intl の道で動く。

## テストの結果

- `offsetLabel.test.ts` 93 件: 時差の値 11 件（UTC・Tokyo・+05:45・Adelaide の夏冬・St_Johns・New_York の切り替えの前後・1880 年 Tokyo の +09:18:59）を 2 つの道で、`formatInstant` から切り出した値との一致つき。解釈できない名前・形の悪い名前・文字列でない値・読めない時点・範囲外の年。性質ベースのテスト（全部の名前 × 0001〜9999 年の任意の時点、300 回）。共有のキャッシュ。回数（Temporal の道 0・0、Intl の道 1 件あたり 1 以下、形の検査で落ちる名前は Intl に問い合わせない、テストの環境では Temporal の道を使う）。`temporalFrom` の判定と変わった Temporal（例外・秒未満の端数・整数の秒）。
- `buildTimeZoneOptions.test.ts` 12 件: `'builds the offsets once per array …'` は数える相手を `timeZoneOffsetText` に変えた（1 回目は名前の数＋UTC、同じ配列の2回目は増えない）。回数の退行のテスト（全部の名前で生成 0・`formatToParts` 0）と共有のキャッシュのテストを足した。
- 範囲を絞ったコマンド（4 ファイル）: 117 件すべて通過。
- NFR2.2（直す前の作りで落ちること。Step 6 の前に流した）:
  - `AssertionError: expected { created: 419, formatToParts: 838 } to deeply equal { created: +0, formatToParts: +0 }`
  - キャッシュのテスト: `AssertionError: expected 36 to be 1 // Object.is equality`（`formatterCacheSize()` が 1 → 36）

## Step 8 直した後の値

```
[time-zone-options] names=418 runs(ms)=2.5,0.8,0.7,0.6,0.6 median=0.7 node=v26.13.0 cpu=Apple M2
[time-zone-options] names=418 runs(ms)=1.9,1.0,0.7,0.7,0.7 median=0.7 node=v26.13.0 cpu=Apple M2
[time-zone-options] names=418 runs(ms)=2.9,1.0,0.7,0.7,0.7 median=0.7 node=v26.13.0 cpu=Apple M2
```

- 中央値の中央値 **0.7 ms**（T0 31.5 ms の約 0.022 倍）。NFR1.2 の線（15.75 ms 以下）を満たした。3 回とも 274 ファイル・3057 件が通過、終わりの値 0。`pmset -g log` で 07:58〜08:04 に Sleep・DarkWake・Thermal 無し。

## Step 9 `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`

- BUILD SUCCESSFUL、1680 秒（28 分、2026-11-07 08:03:45〜08:31:45）。`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡し、colima の起動を確かめてから流した。
- バックエンドの単体テスト 2936 件（失敗 0）、結合テスト 1716 件（失敗 0・飛ばし 10。ほかの DB 用のテストが前提の条件で飛ぶもので作りどおり。コンテナが無いための SKIPPED ではない）、フロントエンド 274 ファイル・3057 件。
- フロントエンドのカバレッジ: 全体 行 95.49%・分岐 91%、`src/shared/format/timeZone/` 行 98.53%・分岐 96.56%（`offsetLabel.ts` 行 100%・分岐 96.55%）、`src/features/preferences/` 行 96.61%・分岐 90.69%。
- `pmset -g log` で 08:00〜08:32 に Sleep・DarkWake・Thermal 無し。

## 実行していないテスト

- E2E（`./gradlew e2eTest`）: `team.md` の決まりで Build and Test で流す。
- CI（NFR1.3）: push の後に流れるため、Build and Test で記録する。
- Safari（Temporal が無いブラウザ）での実際の動き: この PC の Playwright は Chromium だけ。Intl の道は単体テスト（Temporal を無しにして呼ぶ）で確かめた範囲だけ。

## 計画との差

1. 計画を Step 3 の後に直した（Temporal の道を足す。依頼者の決定 B、承認し直し済み）。
2. Intl の道の整形器は、「時差の計算に要る項目だけ」ではなく era つきの 7 項目にした（紀元前の年を正しく読むため。Step 3 で 6 項目と速さは同じ）。
3. 計画の Touches に無い `wallClock.ts`（`wallWith` の切り出し）と `formatInstant.ts`（`formatOffset` の export）にも手を入れた。動きは変えず、既存のテストが通った。
4. `now` に `Invalid Date` が渡ったとき: 直す前は `toISOString()` の RangeError。今は時差が求まらず、選択肢が先頭（「ブラウザに合わせる」）だけになる。画面は `new Date()` しか渡さない。
5. 前の Intent（U1 の NFR Design の Q1: A「時差を求める道を1つにする」）との差: 選択肢の組み立てだけに Temporal の道を足した。時刻の表示・入力の求め方は変えていない。
6. Step 1〜3 の箱は、計画を直したときに外れたため付け直した。

## Step 12 承認の場の Request Changes（2026-11-07、レビューの R-01〜R-03）

- R-01: **Safari など Temporal が無い環境（Intl の道）では、選択肢の組み立ての時間はほぼ速くならない**（Step 3b の試しで今の形の約 0.86 倍）。これは依頼者の決定 B（Temporal で求め、無い環境は Intl に戻る）の範囲で、依頼者は承認の場でレビューの指摘を受けてこの直し方を選んだ。性能の目標（100 ms）のテストは Node（Temporal の道）で測る。Intl の道の守りとして、`offsetLabel.test.ts` に全部の名前を Intl の道で呼んで名前ごとに生成 1 回以下・`formatToParts` 1 回以下を確かめるテストを足した（`creates at most one formatter and calls formatToParts at most once for each of all the names on the intl path, …`）。組み立てが名前ごとに `timeZoneOffsetText` を1回だけ呼ぶことは `buildTimeZoneOptions.test.ts` の既存のテストが確かめる。
- R-02: `buildTimeZoneOptions.ts` で時点の秒が有限でない（`Invalid Date`）とき、空の結果を `WeakMap` に保存しないようにした。テスト `gives only the browser choice for an invalid date and does not keep that empty result for the array` を足した（選択肢は「ブラウザに合わせる」だけ。同じ配列で正しい時刻を渡すと作り直す）。直しを一時的に外すと `expected 4 to be 7` で落ちることを確かめた。
- R-03: `offsetLabel.test.ts` に、全部の名前（`Intl.supportedValuesOf('timeZone')` と UTC）× 4 時点（2026-01-15T00:00Z・2026-03-08T06:59:59Z・07:00:00Z・2026-07-15T00:00Z）で 2 つの道のラベルが一致し、どの名前も「無し」にならないテスト 4 件を足した。
- R-04: この段では扱わず、Build and Test で同じ条件の前後を比べる。
- 範囲を絞ったコマンド（4 ファイル）: 123 件すべて通過（14.06 秒）。型の検査・ライセンスヘッダーの検査・変えた 3 ファイルの oxlint・ESLint・Prettier は通過。`npm run test:coverage` と `./gradlew verify` は流していない（Build and Test で流す）。

## 気になる点（確かめていない）

- `verify` の中の `conversionPerformance` の `[timeZone performance]` は中央値 101〜104 ms（上限 500 ms の内）。直す前の範囲を絞った実行では 74 ms。変換の関数は変えておらず、verify の中の負荷の違いと見ている。
- verify のログに `OtlpMeterRegistry` の "Failed to publish metrics" の WARN が 1 件（終わるときの外部エクスポートの結合テストのもの）。今回の変更とは関係が無い見込み。

## Step 1 準備

- `develop` から `fix/tz-options-perf` を作って移った。
- 前の Intent の E2E の報告 `frontend/test-results/`（git の対象外）を消した（`frontend/playwright-report` は元から無かった）。
- colima は動いていた（macOS Virtualization.Framework、aarch64、docker）。

## Step 2 直す前の値（T0）

- 範囲を絞ったコマンド: 3 ファイル・22 件がすべて通った。
- `npm run test:coverage`（カバレッジの計測つき、`frontendCoverage` と同じ条件）: 3 回とも 273 ファイル・2962 件がすべて通り、終わりの値は 0（カバレッジの下限も通過）。
- 報告の形: Vitest 5 はエージェントの中で動くと通ったテストの console 出力を隠すため、`npm run test:coverage -- --reporter=default` で流した（測る中身は同じ。行が取れなかった最初の1回は数えていない）。Step 8 も同じ形で流す。
- 3 回の出力:

```
[time-zone-options] names=418 runs(ms)=53.3,52.2,51.9,31.3,32.6 median=51.9 node=v26.13.0 cpu=Apple M2
[time-zone-options] names=418 runs(ms)=36.0,31.0,30.6,33.7,31.5 median=31.5 node=v26.13.0 cpu=Apple M2
[time-zone-options] names=418 runs(ms)=33.7,27.8,26.1,27.9,27.2 median=27.8 node=v26.13.0 cpu=Apple M2
```

- **T0 = 31.5 ms**（3 回の中央値の中央値）。NFR1.2 の手元の合格の線は 15.75 ms 以下。
- 1 回目の中央値（51.9 ms）だけ大きい。流す前は眠らない状態で、流した後の `pmset -g log` では 01:24〜01:34 に Sleep・DarkWake・Thermal は無かった。原因は分かっていない。

## Step 3 捨ての試し

- 置き場: `~/.mastersmith-scratch/tz-options-perf/`（権限 700、ファイル 600。`probe.mjs`・`probe2.mjs`）。リポジトリのファイルは変えていない。
- 環境: Node v26.13.0、ICU 78.3、tz 2026e、Apple M2。名前 418 個（UTC を足して 419 個）。
- 3 つの形とも各回で整形器を作り直した。`longOffset` は使っていない。3 つの形を交互に 5 周流した。

| 形 | 5 回の値 (ms) | 中央値 | (i) との比 | 生成 / `formatToParts`（1 回の組み立て） |
|---|---|---|---|---|
| (i) 今の形 | 55.3・21.1・23.4・21.8・22.7 | 22.7 | — | 419 / 838 |
| (ii) 7 項目・`formatToParts` 1 回 | 19.3・18.5・17.5・18.3・17.9 | 18.3 | 0.809 | 419 / 419 |
| (iii) 6 項目（era を除く）・`formatToParts` 1 回 | 20.5・18.4・18.0・18.3・17.7 | 18.3 | 0.808 | 419 / 419 |

- V8 のカバレッジ（`NODE_V8_COVERAGE`）を有効にしても比はほぼ同じ（0.815・0.818）。
- 一致: 419 個の名前 × 31 の時点（2026 年の各月 1 日・15 日、夏時間の切り替えの前後、1900・1970・2100 年）の 12,989 組で、(ii)・(iii) のラベルは (i) と全部一致した（1900 年は 274 個の名前で秒つきの時差 `±HH:mm:ss`、これも一致）。
- 費用の内訳（`probe2.mjs`）: 整形器の生成だけで 16.6 ms（ロケール `en-US-u-ca-gregory-nu-latn`）。項目を `timeZone` だけにしても 15.5 ms。`formatToParts` だけは約 1.0 ms。**時間の大半は名前ごとの `new Intl.DateTimeFormat`** で、`formatToParts` を減らしても約 19% しか縮まない。

## Step 3 の判定（計画の Step 3 の止まる条件に当たった）

- 計画の作り（(iii) 相当）の見込み: 31.5 × 0.81 ≈ 25.5 ms。NFR1.2 の線（15.75 ms 以下）に届かない。計画どおり Step 4 に進まず、依頼者に諮る。
- 並べる候補（試しの値。検討はまだ）:
  1. ロケールを `en-US` にする: 生成 16.6 → 10.5 ms、全体で (i) の約 0.6 の見込み。半分には届かない。暦と数字の形を拡張で固定している今の決まりを手放すため、要件の判断が要る。
  2. Temporal で時差を求める: Node 26 で使え、全部の名前で約 0.3 ms。ESLint の時差の規則（`timeZoneConversionRules`）・対象のブラウザの対応・前の Intent の決定（U1 の Q1: A、時差は `formatToParts` の壁時計から求める）との関係を確かめる要がある。
  3. 目標の物差し（NFR1.2 の線、または NFR2.1.6 の測り方）を見直す: `team.md` の決まりで、要件定義で依頼者が明示に決めたときだけ変えられる。
- 依頼者の指示（2026-11-06）で、Step 3 まででコミットして一時停止にした。
