# コード生成の記録 — u1-time-zone-conversion

画面の側の純粋な日時変換の関数群を `frontend/src/shared/format/timeZone/` に作った。方法は test-after（部品ごとに実装 → テスト → 通ってから次へ）。新しい依存は足していない（`frontend/package.json`・lockfile は無変更）。コミットはしていない。

## Files

作った（`frontend/src/shared/format/timeZone/`）:

| ファイル | 内容 |
|---|---|
| `types.ts` | 結果の型（LC6）: `FormatResult`・`ParseResult`・`CompareResult`・`OverlapChoice` |
| `timeZoneGate.ts` | 名前の門（LC1）: 名前の検査・整形器の取り出しとキャッシュ（上限 128 件で全体を捨てる）・`canInterpretTimeZone` |
| `wallClock.ts` | 壁時計（LC2）: 時点 → 壁時計と時差の秒、壁時計 → 候補の時点（0・1・2 通り）、日数の計算（Date に頼らない） |
| `instantText.ts` | 時点の文字（LC3）: 厳密な読み取り・UTC の時点の文字の組み立て・`preserveInstant`・`compareInstants` |
| `formatInstant.ts` | 表示の整形（LC4） |
| `parseLocalDateTime.ts` | ローカル時刻の変換（LC5） |
| `index.ts` | 公開の入口（名前付きのエクスポートだけ） |
| テスト 8 本 | `timeZoneGate`・`wallClock`・`instantText`・`formatInstant`・`parseLocalDateTime`・`conversionBoundary`・`conversionProperties`・`conversionPerformance` の各 `.test.ts` |

変えた: `frontend/eslint.config.js`（`timeZoneConversionRules` を足し、`sharedImportRule` の後に置いた）。既存の `formatDateTime.ts` は変えていない。

## Decisions

- 時差は `longOffset` を使わず、`formatToParts` の壁時計の数字を UTC とみなした秒と元の時点の差から求める（Q1: A）。整形のロケールは `en-US-u-ca-gregory-nu-latn`・`hourCycle: 'h23'`・`era: 'short'`。紀元前は年を `1 - 年` で表して扱う。
- 暦の計算（日数 ↔ 年月日）は `Date` に頼らず自前の先発グレゴリオ暦の計算で行う。`Date` の繰り上げを避け、年が負・0001 でも同じに動く。
- 候補の時点は、壁時計の前後 24 時間の時差から 2 つ作り、その時点の壁時計が合うものを数える（0 通り＝`gap`・2 通り＝`overlap`）。先 = 小さい時点。
- 表示のタイムゾーンへ直した年が 0001〜9999 を外れるとき `formatInstant` は `unreadable`（Q2: A）。
- 名前の検査は型 → 長さ（1〜64）→ 使える文字 → 固定のオフセット表記の除外 → `Intl` の受け付け。時点の文字は長さ 40、ローカル時刻は 26。
- `overlapChoice` は `earlier`・`later` のときだけ使う（実行時に別の値が来ても無視して `overlap` を返す）。
- `compareInstants` は秒が同じなら端数（9 桁にそろえて比べる）で大小を決める。
- ESLint の上書きは、`src/shared/format/timeZone/**/*.ts` のテストでないファイルに、`no-restricted-globals`（`console`・`fetch`・`XMLHttpRequest`・`WebSocket`・`localStorage`・`sessionStorage`・`indexedDB`・`document`・`window`・`navigator`）と `no-restricted-imports` を当てる。`no-restricted-imports` は `src/shared/` 全体の既存の決まり（app・features の禁止）と同じ `files` に重なり、後の設定が置き換えるため、その patterns も含めて書いた。`formatDateTime` への依存も止める。スクラッチの入力で、`react` の import・`console`・`fetch` が止まることを確かめた。
- 依存の向き: LC4・LC5 → LC2・LC3 → LC1。LC3（instantText）は暦の計算に wallClock の純粋な日数の計算だけを使う（LC3 → LC2。逆向きの import は無い。計画の「LC2・LC3 → LC1」に対し、LC3 から LC2 への向きを足した）。

## Tests

実測（`frontend/` で `npm run test:coverage`、全体）: 246 ファイル・2464 件がすべて通過。この単位の 8 ファイルは 24（timeZoneGate）+15（wallClock）+42（instantText）+21（formatInstant）+36（parseLocalDateTime）+8（conversionBoundary）+6（conversionProperties）+1（conversionPerformance）= 153 件（`it.each` の展開を含む。`it` の定義は約 70 本。計画の目安の約 55 件より多いのは表のテストの展開による）。

カバレッジ（全体 行 95.09%・分岐 90.55%、下限 行 80%・分岐 70% を満たす。設定は変えていない）:

| 範囲 | 文 | 分岐 | 関数 | 行 |
|---|---|---|---|---|
| `shared/format/timeZone/`（U1 のフォルダ） | 98.88% | 97.07% | 100% | 98.85% |

性質ベース（fast-check）: 例外が出ない（任意の入力）、同じ入力は同じ結果、往復（全 IANA 地域 + UTC、500 回。重なる時刻は先・後のどちらかが元の時点に戻る）、端数の桁が読み書きで保たれる、解釈できない名前は常に `invalid-time-zone`、時点の比べが反射的かつ反対称。乱数の種は失敗時に fast-check が出力に含める。

性能の測り（5 回の測りの中央値で判定。目標 合計 500 ミリ秒・1 つあたり平均 0.05 ミリ秒。緩めていない）:

- この単位だけを流したとき: 5 回 82.8・68.2・68.1・67.3・66.8 ミリ秒、中央値 68.1 ミリ秒、平均 0.0068 ミリ秒。
- 全体のカバレッジの実行中（並列の負荷あり）: 5 回 138.5・116.6・112.1・115.3・113.9 ミリ秒、中央値 115.3 ミリ秒、平均 0.0115 ミリ秒。
- 環境: Node v26.13.0、CPU Apple M2 ×8。目標に対し約 4〜7 倍の余裕。

実行したその他の検査（`frontend/`）: `npm run format:check`・`npm run lint`（oxlint + ESLint）・`npm run lint:css`・`npm run typecheck`・`npm run license:check` はすべて通過。Prettier は新しいフォルダだけに `--write` を流して整形した（既存のファイルは変わっていない）。

【呼び出し元が実測した結果】`./gradlew verify` は成功（28分36秒。アイコンの一覧の直しの後の3回目。1回目は型検査、2回目は一覧の照合の2件で落ちた）、既存の E2E（`./gradlew e2eTest`）は 277 件が通過（14分19秒。見本の PostgreSQL を起動してから流した）。以下は開発担当の記録のまま残す。未実行（呼び出し元が流す）だった: `./gradlew verify`（フォーマット・ライセンスヘッダー・サイズの検査・SpotBugs・OSV などを含む）と `./gradlew e2eTest`（`frontend/src` の本体に触れるため統合の前に既存の E2E を流す）。計画の Step 16 のうちこの 2 つは未了のため、Step 16 のチェックは付けていない。

## Deviations

- **アイコンの許可の一覧の拡張（計画に無い変更。依頼者の決定 A、メッセージ f935600d）**: make-you-chic-ui の固定先を d112795 に上げたときにアイコン 6 種（mail・users・shield・table・view・folder）が増え、`./gradlew verify` の型検査（`ALL_ICONS_COVERED`）が develop の今の形でも落ちた（U1 の変更とは無関係）。U1 の統合の前の専用の小さな変更として、4 か所をそろえた: `frontend/src/app/registry/allowedIcons.ts`（6 名を追加）、`frontend/src/app/registry/validateRegistrations.test.ts`（件数 18 → 24）、`backend/src/main/resources/navigation/allowed-icons.txt`（6 名を追加。サーバー側のメニューの `icon` の許可に効く）、`backend/src/test/java/cherry/mastersmith/navigation/service/AllowedNavIconListTest.java`（24 名）。メニューのアイコンの出し分けの中身は後の単位（アイコンとホームの単位）が作る。

- **UTC の年の範囲外（計画で確定した差）**: ローカル時刻を UTC に直した年が 0001〜9999 を外れるときは `{kind: "invalid", reason: "out-of-range"}`（例: `9999-12-31 23:00:00` を `America/New_York` → 10000 年、`0001-01-01 00:00:00` を `Asia/Tokyo` → 0 年）。承認済みの機能設計に無い扱い。同様に、時点の文字 `parseInstantText` は、UTC に直した年が範囲外（例 `0001-01-01T00:00:00+09:00`）なら読めない値にする（`formatInstant` は `unreadable`、`compareInstants` は `unreadable`）。
- **NFR Design のレビューの指摘の反映**: 名前と時刻が両方不正なときは名前の検査が先で `invalid-time-zone`（テストで確かめた）。`compareInstants` は `{kind: "ok", order}` か `{kind: "unreadable", side}`。暦は 0001 年・1500 年代・1582 年も先発グレゴリオ暦で往復することを `wallClock.test.ts` で確かめた。性能の判定は 5 回の中央値で、失敗時は値と環境を出す。
- 型の値に対する実行時の守り: `formatInstant` に文字列でない時点が渡されたとき、`unreadable` の `raw` は空の文字列（SD1.1）。
- `fractionDigits` は 9 桁を上限に切り上げる（契約は 0・3・6 だけを想定。範囲外の値でも壊れない）。
- ESLint の `no-restricted-globals` に、計画の一覧に無い `WebSocket`・`indexedDB`・`navigator` を足した（通信・保存・ブラウザの既定のロケールに頼る口を同じ決まりで止めるため。テストのファイルは対象外）。
- テストの件数は目安の約 55 件より多い（表のテストの展開）。
