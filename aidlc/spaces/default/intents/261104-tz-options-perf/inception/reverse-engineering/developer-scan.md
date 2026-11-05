## Developer Code Scan Results

> Intent `261104-tz-options-perf`（bugfix・Minimal・Test Strategy Minimal）の Reverse Engineering。Focused scan（依頼者の選択）。記録のコミット `a789a6f02fbb72285a838a77b85d841c38075e45`。開発担当が1人で走査した（設計担当は今回の構成に無い）。
>
> 直す不具合（依頼者の記述のまま）: 「CI の frontendCoverage で U2 の性能のテスト TimeZoneOptionsPerformance.test.ts（タイムゾーンの選択肢の組み立て 100 ms 以内、NFR2.1.6）が中央値 106 ms で落ちた（f2f2eb9b の CI、AMD EPYC 7763、カバレッジの計測つき。2回目の失敗）。目標の 100 ms と測り方は変えず、buildTimeZoneOptions の組み立てを速くして（時差だけを求める軽い形など）、CI と同じカバレッジありの条件で余裕を持って通るようにする。」
>
> 確かめ方: ファイルの読み取り（cat・sed・grep・ls・wc）だけ。テスト・ビルド・npm・gradlew・docker は実行していない。git は読み取りの `git log --oneline -1`（`formatDateTime.ts` が消えたコミットの確認）を1回だけ使った（依頼の「git を使わない」に反したため、ここに明記する）。行の番号は読んだ時点のファイルのもので、書いた後に grep で確かめ直した。

### Scan Coverage

- **Analyzed deeply**:
  - `frontend/src/features/preferences/`（`buildTimeZoneOptions.ts`・`TimeZoneOptionsPerformance.test.ts`・`buildTimeZoneOptions.test.ts`・`TimeZoneSelect.tsx` は1行ずつ。ほかの本体のファイル `registration.ts`・`PreferencesPage.tsx`・`preferencesApi.ts`・`failure.ts`・`errorMessages.ts`・`formChecks.ts` は全体、`usePreferencesForm.ts`・`PreferencesForm.tsx`・`TimeZoneSelect.test.tsx`・`testing/` は頭の説明と時差の関わる行）
  - `frontend/src/shared/format/`（`timeZone/` の本体 7 ファイル `timeZoneGate.ts`・`formatInstant.ts`・`wallClock.ts`・`instantText.ts`・`parseLocalDateTime.ts`・`types.ts`・`index.ts` と、テストの `timeZoneGate.test.ts`・`conversionPerformance.test.ts`、`dateTimeInput.ts`・`formatInstantToMinutes.ts`）
  - `frontend/vitest.config.ts`
- **Skimmed only**:
  - `frontend/src/app/display-settings/`（`browserTimeZone.ts`・`decideDisplayTimeZone.ts` の `canInterpretTimeZone` の呼び出しの grep だけ）
  - `frontend/src/features/tables/`・`frontend/src/features/useradmin/`（`shared/format` を使うファイルの grep だけ）
  - `frontend/eslint.config.js`・`frontend/eslint-rules/timeZoneSelectors.js`（時差の規則の節だけ）
  - `frontend/package.json`・`frontend/package-lock.json`（vitest・coverage-v8 の版と `test`・`test:coverage` の行）
  - `build.gradle.kts`（`frontendTest`・`frontendCoverage` の定義の行）
  - `.github/workflows/ci.yml`（`verify` の job の頭と Node の版）
  - `frontend/src/shared/format/timeZone/` の残りのテスト（`conversionBoundary.test.ts`・`conversionProperties.test.ts` の `supportedValuesOf` の行だけ）
- **Left out**:
  - `frontend/node_modules/`・`frontend/dist/`・`frontend/coverage/`・`frontend/playwright-report/`・`frontend/test-results/`（ビルドの出力・依存）
  - `vendor/`（依頼で対象外）
  - `aidlc/`・`.claude/` ほか AI-DLC の導入物（依頼で対象外）

### Packages Found

- `frontend/src/features/preferences` — 画面の機能（React の部品・フック・純粋な関数） — TypeScript — 自分の設定（氏名・言語・テーマ・文字の大きさ・表示のタイムゾーン）とパスワードの変更の画面。表示のタイムゾーンの選択肢の組み立て `buildTimeZoneOptions` を持つ。
- `frontend/src/shared/format/timeZone` — 共有の純粋な関数（日時の変換、U1） — TypeScript — 名前の門と整形器のキャッシュ（`timeZoneGate`）、時点 → 表示の文字（`formatInstant`）、壁時計と時差（`wallClock`）、時点の文字の読み書き（`instantText`）、ローカル時刻 → 時点（`parseLocalDateTime`）。公開の入口は `index.ts`。
- `frontend/src/shared/format`（上の外の2つ） — 共有の薄い関数 — TypeScript — `formatInstantToMinutes.ts`（分までの表示）・`dateTimeInput.ts`（業務データの日時の入力の初めの値と送る形）。どちらも `formatInstant`・`parseLocalDateTime` の上に乗る。
- 前回の知識ベースにあった `shared/format/formatDateTime.ts` は無い（2026-11-03 のコミット `102608aa` の U2 で消えた）。

### Build System

- **Type**: npm（Vite・Vitest）を Gradle のタスクから呼ぶ。
- **Config Files**: `frontend/vitest.config.ts`（深く読んだ）、`frontend/package.json`（`test` = `NODE_OPTIONS=--no-experimental-webstorage vitest run`、`test:coverage` = 同じに `--coverage`。22・23 行）、`build.gradle.kts`（`frontendTest` 216 行が `test`、`frontendCoverage` 217〜220 行が `test:coverage`。`verify` の段の 474 行に `frontendCoverage`）。
- **Build Dependencies**: 性能のテストは `frontendTest`（計測なし）と `frontendCoverage`（V8 のカバレッジの計測つき）の両方で流れる。CI（`ci.yml` の `verify` の job、`runs-on: ubuntu-latest`、Node 26）は `./gradlew verify` を呼ぶため、両方を通る。落ちたのは計測つきの方。
- `vitest.config.ts` の要点（1〜80 行）:
  - `environment: 'jsdom'`、`projects` が2つ: `eslint-config`（ESLint の設定を読む2本、`groupOrder: 0`）と `unit`（`src/**/*.test.{ts,tsx}`・`e2e/support/**/*.test.ts`、`groupOrder: 1`）。性能のテストは `unit` の中で、ほかのテストのファイルと同時に流れる。`pool`・`maxWorkers`・`fileParallelism`・`isolate` の指定は無い（Vitest の既定で並列）。
  - `coverage`: `provider: 'v8'`、`allowExternal: true`、`include` は `src/**/*.{ts,tsx}` と `../perf/redact-k6-evidence.mjs`、`exclude` は `src/main.tsx`・`*.d.ts`・テストのファイル、`thresholds` は `lines: 80`・`branches: 70`。性能のテストだけを計測から外す設定・性能のテストを別の project に分ける設定は無い。

### APIs Discovered

- 今回の範囲は画面の中の関数で、HTTP の口は変えない。`preferences` が呼ぶ口は `GET`・`PUT /api/me/preferences`・`POST /api/me/password`（`preferencesApi.ts`、変更なし）。
- 内部の口（画面の中の関数）:
  - `buildTimeZoneOptions(names, saved, now, browserLabel): readonly TimeZoneOption[]`（`features/preferences/buildTimeZoneOptions.ts` 88〜101 行）と `readSupportedTimeZoneNames()`（35〜41 行）。呼ぶのは `TimeZoneSelect.tsx` 53〜57 行だけ（`useState` で名前の一覧と時刻を1回決め、`useMemo` で `value`・`browserLabel` が変わるたびに呼ぶ）。
  - `shared/format/timeZone/index.ts` の公開: `formatInstant`・`compareInstants`・`preserveInstant`・`parseLocalDateTime`・`canInterpretTimeZone` と型。`getFormatter`・`clearFormatterCache`・`formatterCacheSize`・`wallAt`・`offsetSecondsAt` は公開しない（テストは `timeZoneGate` を直接読む）。

### Frameworks & Libraries

- Vitest — 5.0.5（`package-lock.json` 5429 行） — 画面の単体テスト（性能のテストを含む）
- `@vitest/coverage-v8` — 5.0.5（`package-lock.json` 1993 行） — カバレッジの計測（V8 の計測）
- jsdom — `^30.1.3` — テストの DOM
- fast-check — `^4.10.2` — 性質ベースのテスト（`buildTimeZoneOptions.test.ts` の最後）
- React — `^19.3.1` — `TimeZoneSelect` の `memo`・`forwardRef`・`useState`・`useMemo`
- Node — `>=26 <27`（`engines`）、CI は `node-version: "26"`
- `Intl`（ECMA-402、Node 同梱の ICU） — `Intl.supportedValuesOf('timeZone')` と `Intl.DateTimeFormat`

### Test Coverage

- **Test Directories**: 対象と同じ場所（`features/preferences/*.test.ts(x)`・`shared/format/**/*.test.ts`）と `features/preferences/testing/`（描画の補助と見本）。
- **Test Frameworks**: Vitest・Testing Library・user-event・vitest-axe・fast-check。
- **Coverage Config**: present（`vitest.config.ts` の `coverage`、上の Build System）。
- 性能に関わるテスト:
  - `features/preferences/TimeZoneOptionsPerformance.test.ts`（53 行、NFR2.1.6）: `readSupportedTimeZoneNames()` の一覧を1回読み、5 回くり返す。各回で `clearFormatterCache()` を呼び、`[...base]` で新しい配列を作り（`WeakMap` のキャッシュに当たらない）、`performance.now()` で `buildTimeZoneOptions(names, null, NOW, 'browser')` の1回だけを挟む。各回で `options.length > 100` を確かめる。5 回を並べ替えた `sorted[2]`（中央値）が `LIMIT_MS = 100` 以下で合格。`console.info` に個数・5 回の値・中央値・Node の版・CPU を出す。`saved` は null（保存済みの名前を足す道は測らない）。最初の回は ICU の時差のデータの読み込みも含む本当に冷たい回で、2〜5 回目は整形器のキャッシュだけが冷たい回（ICU の内部の状態は測りの外で残る。未検証）。
  - `shared/format/timeZone/conversionPerformance.test.ts`（NFR2.1.1・2.1.2）: 整形器を温めた後に `formatInstant` を 1 万回、5 回の中央値で合計 500 ms・1 回 0.05 ms 以内。整形器の生成は測りから外している（`// 整形器の生成を測りから外すため、1 回だけ温めておく`）。
  - `features/preferences/buildTimeZoneOptions.test.ts`: 並び・UTC の追加・解釈できない名前の除外・時差のラベル（夏時間・分の端数）・保存済みの名前の扱い・`readSupportedTimeZoneNames` の落ち方・fast-check の性質（先頭・重複なし・昇順・UTC あり）。**`'builds the offsets once per array …'`（111〜122 行）は `vi.spyOn(timeZone, 'formatInstant')`（公開の入口の名前空間）の呼び出しの回数を数え、1 回目が 0 より多く、同じ配列の2回目以降で増えないことを確かめる**。組み立てを `formatInstant` を呼ばない形に変えると、このテストの `first > 0` が落ちる。
  - `shared/format/timeZone/timeZoneGate.test.ts`: 同じ名前の 1 万回で `Intl.DateTimeFormat` の生成が 1 回、キャッシュの上限を超えたら全体を捨てても結果が同じ、など。

### Code Quality Indicators

- **Linting**: oxlint・ESLint（`frontend/eslint.config.js`）。時差の規則（`eslint-rules/timeZoneSelectors.js` の S1〜S4）: `Intl.DateTimeFormat` を `timeZone` の指定なしで作る・呼ぶことを止め、文言は「時刻の整形は src/shared/format/timeZone を使ってください」。`src/shared/format/timeZone/**/*.ts`（テストを除く）には、通信・保存・DOM・console を使わず画面・通信の部品・他の機能に依存しない決まり（`timeZoneConversionRules`、211 行から）。直した形で新しい関数を置くなら `shared/format/timeZone` の中に置き、`index.ts` から名前付きで出すのが今の決まりに合う（見立て）。
- **CI/CD**: `.github/workflows/ci.yml` の `verify` の job（`ubuntu-latest`、`timeout-minutes: 60`、Node 26、`./gradlew verify`）。
- **Documentation**: 各ファイルの頭に日本語の説明（Intent・単位・BR・NFR の番号つき）がある。`buildTimeZoneOptions.ts` 17〜21 行に作り（配列ごとに 1 回、`WeakMap`、時差は now で 1 回）が書かれている。

### Technical Debt Signals

**T-1 選択肢の組み立ての1件ごとの仕事（確かめた事実。`buildTimeZoneOptions.ts` と `shared/format/timeZone`）**

1件の名前ごとに、`entriesFor`（57〜74 行）→ `offsetLabel`（49〜55 行）が次を行う。

| 段 | 呼び出し | `Intl.DateTimeFormat` の生成 | `formatToParts` | 名前の検査（正規表現 2 つ） | その他 |
|---|---|---|---|---|---|
| 1 | `canInterpretTimeZone(name)` → `getFormatter`（`timeZoneGate.ts` 53〜83 行） | 1（キャッシュに無いとき。測りでは毎回無い） | 0 | 1 | `formatterCache.set`、128 件で全体を捨てる |
| 2 | `formatInstant(instant, name)` → `getFormatter`（`formatInstant.ts` 53 行） | 0（直前に入れたもの） | 0 | 1 | — |
| 3 | `parseInstantText(instant)`（`formatInstant.ts` 57 行） | 0 | 0 | 0 | 書式の正規表現 1・日数の計算。**名前によらず同じ時点の文字を毎回読み直す** |
| 4 | `wallAt(name, epoch)`（`formatInstant.ts` 61 行 → `wallClock.ts` 119〜142 行） | 0 | 1 | 1 | `parts.find` 7 回・`Number` 6 回 |
| 5 | `offsetSecondsAt(name, epoch)`（`formatInstant.ts` 62 行 → `wallClock.ts` 145〜148 行 → `wallAt`） | 0 | 1 | 1 | **段 4 と同じ時点で壁時計を求め直す** |
| 6 | 文字の組み立て（`formatInstant.ts` 69〜75 行）→ `offsetOf`（`buildTimeZoneOptions.ts` 44〜47 行） | 0 | 0 | 0 | `pad` 7 回・テンプレート 3・時差の正規表現 1 |

- 合計: 1件あたり `Intl.DateTimeFormat` の生成 1 回・`formatToParts` 2 回・`getFormatter` 4 回（名前の正規表現 8 回）・時点の文字の読み直し 1 回。欲しいのは時差の文字（`±HH:mm`）だけだが、日付・時刻・端数までの表示の文字を作ってから正規表現で時差を切り出している。
- 名前の数: `Intl.supportedValuesOf('timeZone')` の個数（測りの出力 `names=`）。Node 26 の ICU で数百（約 400〜430 の見込み。今回は数えていない。未検証）。UTC が無ければ足す。
- 並べ替え: `[...new Set(names)].sort(compareCodePoints)` を1回（数百件。小さい）。
- 整形器の生成の引数（`timeZoneGate.ts` 63〜73 行）: ロケール `en-US-u-ca-gregory-nu-latn`、`timeZone`、`hourCycle: 'h23'`、`era`・`year`・`month`・`day`・`hour`・`minute`・`second`。7 つの項目を持つ形の生成。
- 見立て（未検証）: 時間の大半は名前ごとの `Intl.DateTimeFormat` の生成（ICU の時差の規則とパターンの生成）で、`formatToParts` 2 回と JS の処理（正規表現・`find`・文字の組み立て）がそれに続く。CI の中央値 106 ms を名前の数で割ると、1件あたり約 0.25 ms。`conversionPerformance` の値（生成を除いた `formatInstant` 1 回あたり 0.05 ms 以内の目標）と比べると、生成が支配的と考えられるが、内訳は測っていない。

**T-2 整形器のキャッシュの上限（128）が名前の数より小さい（確かめた事実）**

- `MAX_FORMATTER_CACHE_SIZE = 128`（`timeZoneGate.ts` 24 行）。上限に達すると全体を `clear()` してから入れる（78〜81 行）。
- 組み立ては数百の名前を順に入れるため、1回の組み立ての中でキャッシュが3回ほど全体で捨てられる（同じ名前は入れた直後に使うため、1件の中の 2〜5 段は当たる）。組み立ての後のキャッシュには最後の 128 件以下しか残らず、利用者の表示のタイムゾーン（`decideDisplayTimeZone`・`formatInstant` を使うほかの画面）の整形器が追い出されうる（作り直しが1回増えるだけで、結果は変わらない。テスト `keeps working past the cache limit …` が確かめている）。
- 測りのテストは各回で `clearFormatterCache()` を呼ぶため、キャッシュの上限を上げても測りの値は変わらない（キャッシュは測りの各回の中では1件につき1回しか作らないため）。

**T-3 測り方（確かめた事実）**

- 5 回の中央値・各回で整形器のキャッシュを空にし新しい配列・`performance.now()` で組み立て1回だけを挟む・100 ms は延ばさない（テストの頭の説明 17〜19 行）。依頼は「目標の 100 ms と測り方は変えず」。
- テストは `unit` の project の中で、ほかの jsdom のテストのファイルと同時に流れる（`vitest.config.ts` に並列の制限は無い）。カバレッジの計測（V8）は JS の関数・分岐の実行に手間を足す（見立て。ICU の中の生成の時間にはかからないと考えられるが未検証）。CI の機械（AMD EPYC 7763）でのほかのテストとの競り合いの大きさは分からない（未検証）。
- 失敗は 2 回目（依頼の記述）。`team.md` の「同じテストが二度目に落ちたら、原因を直すまで次へ進まない」に当たる。

**T-4 直し方の候補（提案。コードは変えていない。どれも未検証で、Code Generation の前に捨ての試しで測ること）**

- (a) 時差だけを求める軽い関数を `shared/format/timeZone` に足す: 時点の秒を1回だけ求め（`parseInstantText` を名前ごとに呼ばない。または `now.getTime()` から直接秒にする）、`wallAt` を1回だけ呼んで `wallToSeconds(wall) - epoch` で時差の秒を出し（`formatToParts` を 2 回から 1 回へ）、時差の文字は `formatOffset` 相当で作る（表示の文字の組み立てと `offsetOf` の正規表現を省く）。名前の検査も1回にする。`canInterpretTimeZone` と `formatInstant` の二重の `getFormatter` を省く。JS の仕事は大きく減るが、`Intl.DateTimeFormat` の生成の回数（1件 1 回）は変わらない。
- (b) 時差を求めるための整形器の項目を減らす: 7 項目の整形器の代わりに、時差の計算に要る項目だけ（または `timeZoneName: 'longOffset'`）の整形器を作る。生成の時間が項目の数でどれだけ変わるかは未検証。`wallClock.ts` の頭の説明に「`timeZoneName: 'longOffset'` は使わない。NFR Design の Q1: A」とあり、前の Intent の決定との差になるため、使うなら依頼者に確かめる。共有のキャッシュ（`getFormatter`）と別の整形器にすると、T-2 の追い出しも起きない。
- (c) 共有のキャッシュに入れない: 組み立て用の整形器を共有の `formatterCache` に入れなければ、128 件の全体の捨て直しと表示のタイムゾーンの追い出しが無くなる。測りの値への効き目は小さい見込み。
- 避けるべき形（測り方を変えることになるため）: 名前と時刻の時差を、`clearFormatterCache()` で消えないモジュールの別のキャッシュに持つと、2〜5 回目が温かい回になり中央値が測りの意図（冷たい回）から外れる。採るなら依頼者の決定が要る（`team.md` の「物差しを変えるのは要件定義で依頼者が明示に決めたときだけ」）。

**T-5 影響を受けるテスト（確かめた事実）**

- `buildTimeZoneOptions.test.ts` 111〜122 行は `formatInstant` の呼び出しを数える。T-4 (a) で `formatInstant` を呼ばない形にするなら、このテストの数える相手（新しい関数か `Intl.DateTimeFormat` の生成）を変える必要がある。ほかのテスト（ラベルの値・並び・性質）は返す値が同じなら変わらない。
- `TimeZoneSelect.test.tsx` は `names` と `now` を差し替えてラベルの文字（`'America/New_York (-05:00)'` など）を確かめる。ラベルの形は変えない前提。

**T-6 そのほか（事実）**

- `buildTimeZoneOptions` の保存済みの名前の道（99 行）は、一覧に無い名前に限り `offsetLabel` をもう1回呼ぶ（`now.toISOString()` も呼び直す）。1件だけで影響は小さい。
- `WeakMap` のキャッシュは `at`（時刻）を持つが読まない（72 行で書くだけ）。同じ配列なら最初の時刻の時差を使い続ける（説明どおり）。
- 前の知識ベースの記述のうち古くなったもの: `frontend-app-core` の「共通の日時の書式（`shared/format/formatDateTime.ts`）」と K-96 の「利用者ごとの時差の設定は無い」（今は `preferences` の表示のタイムゾーンと `app/display-settings` の `DisplayTimeZoneProvider` がある。`formatDateTime.ts` は消えた）。`code-quality-assessment.md` の K-90（`coverage.include` が `src/` だけ）は、今は `allowExternal` と `../perf/redact-k6-evidence.mjs` が入っている（`vitest.config.ts` 30〜35・68〜69 行）。

## Handoff Summary

- **Intent-relevant finding**: `buildTimeZoneOptions`（`frontend/src/features/preferences/buildTimeZoneOptions.ts` 49〜74 行）は、数百の名前の1件ごとに、`canInterpretTimeZone` と `formatInstant` を通して `Intl.DateTimeFormat` の生成 1 回（7 項目）・`formatToParts` 2 回（`wallAt` と `offsetSecondsAt` が同じ時点で壁時計を求め直す。`shared/format/timeZone/wallClock.ts` 119〜148 行）・`getFormatter` 4 回・同じ時点の文字の読み直し 1 回を行い、表示の文字全体を組んでから正規表現で時差だけを切り出している。測り（`TimeZoneOptionsPerformance.test.ts`）は各回で整形器のキャッシュを空にした5回の中央値のため、生成の回数と1件あたりの JS の仕事がそのまま時間になる。`timeZoneGate.ts` の整形器のキャッシュの上限 128 は名前の数より小さく、組み立ての中で全体の捨て直しが起きる。
- **Risks / follow-up**:
  - 目標 100 ms と測り方（5 回の中央値・各回でキャッシュを空にする）は変えない。`clearFormatterCache()` で消えない別のキャッシュで時差を持つ形は、測りの意図を変えるため採らない（採るなら依頼者の決定）。
  - JS の仕事を減らす (a) は効くが、生成の回数は減らない。生成が支配的なら (b)（項目の少ない整形器・`longOffset`）も要る。どちらがどれだけ効くかは、Code Generation の前に CI に近い条件（カバレッジの計測つき・ほかのテストと同時）で捨ての試しで測る。`longOffset` は前の Intent の NFR Design の Q1: A との差になるため依頼者に確かめる。
  - `buildTimeZoneOptions.test.ts` 111〜122 行（`formatInstant` の呼び出しを数える）は、`formatInstant` を呼ばない形にすると直す必要がある。ラベルの形（`名前 (±HH:mm)`）・並び・UTC の追加・解釈できない名前の除外は変えない。
  - 新しい関数を置くなら `shared/format/timeZone` の中（ESLint の S2 の文言と `timeZoneConversionRules` の決まり）。`team.md` の「不具合を直すときは再現するテストを同じコミットに含める」と、2回目の失敗の決まりが当たる。
