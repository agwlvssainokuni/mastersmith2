# 単体テストの手順（Intent 261104-tz-options-perf）

## テストの道具と設定

- Vitest 5.0.5・`@vitest/coverage-v8` 5.0.5・jsdom・fast-check（`frontend/package.json`）。設定は `frontend/vitest.config.ts`（変えない）。
- 新しい依存は足さない。設定ファイルも変えない。

## この作業のテストの流し方（範囲を絞ったコマンド）

`frontend/` で流す（`(cd frontend && …)` の形でサブシェルにする）。

この作業で足す・直すテストだけ（カバレッジの計測なし。Step 5・Step 7 で使う）:

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run --project unit src/shared/format/timeZone/offsetLabel.test.ts src/features/preferences/buildTimeZoneOptions.test.ts src/features/preferences/TimeZoneSelect.test.tsx src/features/preferences/TimeZoneOptionsPerformance.test.ts)
```

Step 2 で今のコードで通ることを確かめるときは、まだ無い `src/shared/format/timeZone/offsetLabel.test.ts` を外して流す:

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run --project unit src/features/preferences/buildTimeZoneOptions.test.ts src/features/preferences/TimeZoneSelect.test.tsx src/features/preferences/TimeZoneOptionsPerformance.test.ts)
```

性能の前後の実測（Step 2・Step 8。CI の `frontendCoverage` と同じ条件で、画面のテスト全体をカバレッジの計測つきで流す。カバレッジの下限で終わりの値が非 0 になりうるため、ほかの手順と分けて単独で流す）:

```bash
(cd frontend && caffeinate -dims npm run test:coverage)
```

出力の `[time-zone-options] names=… runs(ms)=… median=… node=… cpu=…` の行を記録する。3回流す。

## 期待するカバレッジ

- フロントエンドのカバレッジの下限（行 80%・分岐 70%、`vitest.config.ts` の `thresholds`）は、画面のテスト全体に当たるもので、Build and Test とこの段の Step 9（`./gradlew verify`）で全体を流して確かめる。範囲を絞った実行では判定しない。下限の値は変えない。
- 新しい `offsetLabel.ts` は、Step 5 のテストで行・分岐とも下限を上回ることを目指し、Step 9 の報告で値を記録する。

## モックと差し替え

- 時刻は実時刻に頼らず、固定の時点（`2026-07-15T00:00:00Z` など）と夏時間の切り替えの前後の時点を渡す。
- 回数のテストは `vi.spyOn(Intl, 'DateTimeFormat')` と `Intl.DateTimeFormat.prototype.formatToParts` の spy で数え、テストの終わりに `mockRestore` で戻す。整形器のキャッシュは `clearFormatterCache()` で空にしてから数える。
- Temporal の道と Intl の道は、`offsetLabel.ts` の道を選ぶ内部の口に Temporal を渡すか無しにするかで切り替えて、両方を流す。`globalThis.Temporal` を消したり差し替えたりしない（同時に流れるほかのテストのファイルに響かないようにするため）。テストの環境（Node 26）は Temporal を持つ。
- 共有のキャッシュの状態は、既存の `timeZoneGate` の `formatterCacheSize()`・`getFormatter()` を直接読む（新しい口は足さない）。

## テストのデータ

- タイムゾーンの名前は、実行する Node の `Intl.supportedValuesOf('timeZone')` と、固定の名前（UTC・`Asia/Tokyo`・`Asia/Kathmandu`・`Australia/Adelaide`・`America/St_Johns`・`America/New_York`）と、解釈できない名前・形の悪い名前（長すぎる名前・使えない文字）。
- 性質ベースのテストは fast-check を使い、失敗したときの種を出力から再現できるようにする。
- テストの説明文は英語。
