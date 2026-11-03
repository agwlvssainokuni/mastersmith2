# セキュリティの設計 — u1-time-zone-conversion

出典: セキュリティの要件（`construction/u1-time-zone-conversion/nfr-requirements/security-requirements.md` の NFR1.4.1〜NFR1.4.3・NFR1.5.1・NFR1.5.2・NFR5.1.1・NFR5.2.1・NFR5.2.2）、技術の選択（同 `tech-stack-decisions.md`）、機能設計（`functional-design/functional-spec.md`・`rules.md` の BR1.1〜BR1.15）、質問と答え（`nfr-design-questions.md` の Q1: A・Q2: A・Q3: A）、捨ての試しの結果（同ファイル）、`team.md`・`project.md`。

U1 は、画面の側の純粋な関数群（種類 library）。通信・保存・ログ・DOM への書き込み・乱数・現在時刻に触れず、認証・認可・個人情報を扱わない。そのため、この設計は「入力を信頼せず、どんな入力でも例外で壊れない」「出力を HTML として扱わせない」「値を外へ出さない」「ブラウザの既定に頼らない」「依存とソースの決まりを守る」を、関数の中の検査の順序と、検査の置き場として決める。性能・拡張・信頼性・観測の独立した文書は、種類の決まりで作らない（性能の設計は `logical-components.md` の LC7 に置く）。

設計の決定には、追跡のため `SD<n>.<m>` の番号を付ける（この文書の中で定義する番号）。

## SD1. 入力の検査（NFR1.4.1）

U1 の公開の関数（`formatInstant`・`parseLocalDateTime`・`canInterpretTimeZone`・`compareInstants`。`preserveInstant` は恒等で検査しない）は、すべて次の順序で入力を検査してから、計算に進む。どの段で外れても、例外を投げず、結果の種別で返して終わる。

| 順序 | 検査 | 外れたときの結果 | 決定 |
|---|---|---|---|
| 1 | 型: 引数が文字列でない（`null`・`undefined`・数・オブジェクト。API の応答の型が実行時に崩れた場合） | 時点は `unreadable`（`raw` は空の文字列）、ローカル時刻は `invalid: format`、名前は `invalid-time-zone` | SD1.1 |
| 2 | 長さの上限（UTF-16 の長さで数える）: 時点の文字 40 以内、ローカル時刻の文字 26 以内、タイムゾーンの名前 64 以内 | 時点は `unreadable`、ローカル時刻は `invalid: format`、名前は `invalid-time-zone` | SD1.2 |
| 3 | 書式（固定の正規表現）。時点の文字は `^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\.[0-9]{1,9})?(Z|z|[+-][0-9]{2}:[0-9]{2})$`、ローカル時刻は `^[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}(\.[0-9]{1,6})?$`。数字は `[0-9]`（全角の数字・他の文字体系の数字を通さない） | 時点は `unreadable`、ローカル時刻は `invalid: format` | SD1.3 |
| 4 | 範囲: 月 1〜12、日は月ごとの日数（うるう年を含む）、時 0〜23、分・秒 0〜59、年 0001〜9999、時差の時 0〜23・分 0〜59 | 時点は `unreadable`、ローカル時刻は `invalid: out-of-range` | SD1.4 |
| 5 | 計算（`Intl.DateTimeFormat`）の例外: `RangeError` は、前の検査を通った値では起きない見込みだが、起きたときも、結果の種別に写して返す | `unreadable`・`invalid: out-of-range`・`invalid-time-zone`（どの値の検査で起きたかで決める） | SD1.5 |

- **SD1.1（型の確かめ）**: TypeScript の型は文字列でも、画面は API の応答（実行時には型を保証しない）を受けるため、実行時の型の確かめを置く。これにより、`null` から `.length` を読む例外が起きない。
- **SD1.2（長さを先に確かめる）**: 長さの上限は、正規表現にかける前に確かめる。上限は、書式の最長（時点の文字は `YYYY-MM-DDTHH:mm:ss.123456789+hh:mm` の 35 文字、ローカル時刻は `YYYY-MM-DD HH:mm:ss.123456` の 26 文字）に、時点だけ余裕を持たせた値（40）にした。NFR Requirements の前提 [assumption]（具体の上限は NFR Design で決める）を、この値に決める。名前の 64 は、IANA の最長の名前（`America/Argentina/ComodRivadavia` などの 32 文字前後）の約 2 倍。上限を超える入力は、正規表現に入れず、計算にも進まない。
- **SD1.3（正規表現の形）**: すべての正規表現は、先頭と末尾に固定し（`^…$`）、量指定子を入れ子にせず、選択（`|`）の枝は固定の長さで重ならない形にする。したがって処理時間は入力の長さに比例し、指数的にならない（捨ての試し: 10 万文字で約 0.3 ミリ秒。SD1.2 の上限で、実際にはその手前で止まる）。
- **SD1.4（範囲の確かめを計算の前に行う）**: 月・日・時・分・秒・年の範囲は、`Date` に渡す前に自前で確かめる（`Date` は 2月30日を3月2日などに黙って繰り上げるため、先に確かめないと誤った時点になる）。うるう秒の 60 は許さない（BR1.6）。
- **SD1.5（計算の例外を結果に写す）**: `Intl.DateTimeFormat` の生成・整形が `RangeError` を投げうる箇所（名前の解釈、極端な時点）だけを、狭い `try` で囲み、結果の種別に写す。例外のメッセージ・名前は、結果にもログにも載せない（SD3.1）。「例外を捕捉して何もしない」形にはしない（捕捉したら必ず結果の種別を返す）。`try` は、`Intl` を呼ぶ部品（`logical-components.md` の LC1・LC2）の内側に閉じ、ほかの部品には広げない。

**結果の型の決まり**: 公開の関数の戻り値は、判別可能な union（`kind` で分岐）だけで、`throw` を使わない。呼ぶ側（U2・U4）は `kind` を `switch` で尽くす。`unreadable` の `raw`・`ok` の `text` は、ただの文字列である（SD2.1）。

## SD2. 出力の扱い（NFR1.4.2）

- **SD2.1（出力は文字として扱う）**: `formatInstant` の `text`・`unreadable` の `raw`・`parseLocalDateTime` の時点の文字は、HTML ではない文字である。U1 は、DOM に書き込まず、HTML も作らず、文字を連結して返すだけにする。`raw`（元の文字をそのまま返す結果）には、`<script>` や `<img onerror>` などが入りうるが、U1 は変換も無害化もしない（呼ぶ側が文字として出すため）。
- **SD2.2（呼ぶ側の決まりを契約に書く）**: 呼ぶ側（U2・U4）は、U1 の結果の文字を、React の通常の差し込み（`{text}`）だけで出し、`dangerouslySetInnerHTML` を使わず、リンクの先（`href`）にしない。この決まりは、`team.md` の「画面に出す値」の決まりと、`react/no-danger`（oxlint）で既に強制されている。U1 の単体テストでは、`raw` に `<img onerror>` を入れて、そのまま文字で返ることを確かめる（SD7.1）。画面側の確かめは U2・U4 の画面のテストが受け持つ。

## SD3. 値を外へ出さない（NFR1.4.3）

- **SD3.1（実行時）**: U1 は、`console`・通信・保存（`localStorage`・`sessionStorage`・`IndexedDB`・`document.cookie`）・DOM・グローバルな状態へ、時刻・タイムゾーンの名前・元の文字を出さない。例外を投げない（SD1.5）ため、例外のメッセージにも入らない。
- **SD3.2（検査の置き場。コード生成で足す）**: 次の2つで、SD3.1 を自動で確かめる。
  - **静的な検査**: `frontend/src/shared/format/` の U1 のファイルに対し、ESLint の `no-restricted-globals`（`console`・`fetch`・`XMLHttpRequest`・`localStorage`・`sessionStorage`・`document`・`window`）と `no-restricted-imports`（通信・保存・画面の部品への import）を上書きの設定で足す。テストのファイルは対象の外にする（`team.md` の機能どうしの import の規則と同じ扱い）。既存の `formatDateTime.ts`（DSL・招待の管理が使う、別の関数）は、この設定の対象の外に置く。
  - **実行時の検査**: 単体テストで `console` の各メソッドを差し替え、全関数を正常・異常の入力で呼んで、呼び出しが 0 回であることを確かめる（NFR1.4.3）。

## SD4. ブラウザの既定に頼らない（NFR5.2.1）

- **SD4.1（名前の検査。`canInterpretTimeZone` と、名前を受ける関数の入口）**: 次の順で判定し、1つでも外れたら解釈できない（`invalid-time-zone`・偽）とする。
  1. 文字列で、長さが 1〜64（SD1.1・SD1.2）。
  2. 使ってよい文字だけ: `^[A-Za-z0-9_+\-/]+$`（IANA の名前は英数字・`_`・`-`・`+`・`/` だけ。制御文字・空白・引用符・`<`・`>`・全角を弾く）。
  3. 固定のオフセット表記でない: `^[+-][0-9]{2}(:?[0-9]{2})?$` に一致しない（捨ての試しで、`+09:00` は `Intl.DateTimeFormat` が受け付けることが分かった。BR1.10 は固定のオフセット表記を解釈できないとするため、`Intl` に渡す前に弾く）。`Z` は `Intl` が拒否する。
  4. `Intl.DateTimeFormat` が、その名前を `timeZone` として受け付ける（`RangeError` を出さない。SD1.5）。
- **SD4.2（ロケールと整形の固定）**: 整形のロケールは、固定の `en-US-u-ca-gregory-nu-latn`（グレゴリオ暦・ラテン数字）とし、`hourCycle: 'h23'` を指定する。ブラウザの既定のロケール・既定のタイムゾーンを読まない（`undefined` を渡さない）。捨ての試しで、`ar-EG`・`ja-JP`・`en-US` の画面のロケールでも、この固定により同じ結果になることを確かめた。年の符号は `era` で区別する（紀元前を `era: 'short'` で読み分け、SD1.4 の範囲の外として扱う。`logical-components.md` の LC2）。
- **SD4.3（既定への黙った置き換えをしない）**: 解釈できない名前を、ブラウザのタイムゾーンや `UTC` に黙って代えない（誤った時点を保存して業務データを壊すため）。`invalid-time-zone` を返し、呼ぶ側が扱う（BR1.12）。名前の大文字・小文字は、`Intl` が受け付ける形（`asia/tokyo` など）を、そのまま受け付ける（名前の正規化は U2 の選択の一覧が使う `Intl.supportedValuesOf('timeZone')` の側の役割で、U1 は入力を書き換えない）。

## SD5. 純粋さとキャッシュ（NFR5.2.2）

- **SD5.1（キャッシュは閉じた最適化）**: `Intl.DateTimeFormat` の使い回しは、`logical-components.md` の LC1 の中の、タイムゾーンの名前をキーにしたモジュールの中の `Map` だけで行う。外へ公開せず、結果は、キャッシュの有無で変わらない（同じ入力は同じ結果。BR1.14）。キャッシュには、解釈できた名前だけを入れる（解釈できない名前は `Intl` の生成で例外になり、入らない）。キャッシュの大きさには上限（128 件）を置き、超えたら全体を捨てて作り直す（利用者が選べる名前は約 400 件で、通常の利用では上限に当たらない。上限は、名前の大文字・小文字の別名や、長い間の画面の利用でのメモリの伸びを防ぐための守り）。捨てても結果は変わらないため、挙動に影響しない。

## SD6. 依存とソース（NFR1.5.1・NFR1.5.2）

- **SD6.1（依存を足さない）**: 新しい依存は足さない（NFR Requirements の Q1: A）。`frontend/package.json` と lockfile は、この単位では変えない。足す場合は、`team.md` の決まり（採用の前のライセンスの確認・採用の理由の記録・OSV-Scanner の対象）に従う。
- **SD6.2（ソースの決まり）**: ソースの先頭に Apache License 2.0 のヘッダー（`/* ... */`、2026、`agwlvssainokuni`）を置く。TypeScript の `strict`・`any` の禁止・`export default` の禁止・`enum` の禁止を守り、`./gradlew verify` のフロントエンドの検査（Prettier・oxlint・ESLint・tsc・ライセンスヘッダー）を通す。公開の入口は、名前付きのエクスポートだけ。

## SD7. 確かめの設計

| ID | 確かめ | 対応する要件 | 方法 |
|---|---|---|---|
| SD7.1 | 異常な入力で、例外が出ず、100 ミリ秒以内に結果の種別が返る（10 万文字・制御文字・`<script>`・`'`・`"`・改行・サロゲートペア・全角の数字・`null`・数）。`raw` に `<img onerror>` を入れても、そのまま文字で返る | NFR1.4.1・NFR1.4.2 | Vitest（単体）と `fast-check`（任意の文字列で例外が出ない。失敗時の乱数の種を記録する） |
| SD7.2 | 正規表現が、入れ子の量指定子を持たない（長さの上限の境界: 上限ちょうどは書式の検査へ進み、上限 + 1 は検査の前に外れる） | NFR1.4.1 | Vitest（境界値の単体テスト） |
| SD7.3 | `console` の呼び出しが 0 回。U1 のファイルが通信・保存・DOM の API を使わない（静的な検査） | NFR1.4.3 | Vitest と ESLint の上書きの設定（SD3.2） |
| SD7.4 | `Intl` を呼ぶ時点で、画面のテストの `TZ`・ロケールを変えても、同じ結果になる。ロケールが `ja-JP`・`ar-EG` でも数字は半角 | NFR5.2.1 | Vitest（`TZ` と `navigator.language` を変えて流す） |
| SD7.5 | 固定のオフセット表記（`+09:00`・`+0900`）・`Z`・空・長すぎる・使ってはいけない文字を含む名前が `invalid-time-zone` になる。`Asia/Tokyo`・`UTC`・`asia/tokyo` は受け付ける | NFR5.2.1・BR1.10 | Vitest（表を使うテスト） |
| SD7.6 | 同じ入力を、キャッシュがある・ない（キャッシュを空にした）状態で呼び、結果が等しい。キャッシュの上限（128 件）を超えても結果が変わらない | NFR5.2.2 | Vitest と `fast-check`（同じ入力は同じ結果） |
| SD7.7 | 日付の変わり目・月末・年末・うるう日・夏時間の切り替えの前後・分の端数の時差（`+05:45`・`+09:30`・`-03:30`）・秒の端数 0・3・6 桁・往復・時差を持たない型は時差を変えても変わらない（呼ぶ側が通さないことの確かめは U4） | NFR5.1.1 | Vitest と `fast-check`。往復の性質は、表示の年が 0001〜9999 に収まる時点に絞る（年が外れる時点は `unreadable` になるため。`nfr-design-questions.md` の Q2: A） |
| SD7.8 | 新しい依存が増えていない | NFR1.5.1 | コード生成の差分で `package.json`・lockfile に変更が無いことを確かめる |

## 脅威の見立ての更新（U1 の範囲）

| 脅威 | 対応する設計 |
|---|---|
| 悪意のある長い・異常な文字による処理の停止（ReDoS・メモリ） | SD1.1〜SD1.3（型・長さを先に確かめ、入れ子の量指定子を使わない） |
| 暦の繰り上げによる誤った時点（2月30日が3月2日になる） | SD1.4（`Date` に渡す前に範囲を確かめる） |
| 文字を HTML として描かれて XSS になる | SD2.1・SD2.2（U1 は DOM に触れない。呼ぶ側の決まりを契約に書く） |
| タイムゾーン・時刻の値の漏えい | SD3.1・SD3.2（出さないことの静的な検査と実行時の検査） |
| 既定のタイムゾーン・ロケールに黙って代わり、誤った時点を保存する | SD4.1〜SD4.3（明示に受け取り、固定のロケール、`invalid-time-zone` を返す） |
| 名前の別名の大量の入力で、キャッシュが伸び続ける | SD5.1（キャッシュの上限 128 件） |

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- セキュリティの要件: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u1-time-zone-conversion/nfr-requirements/security-requirements.md`（NFR1.4.1〜NFR1.4.3・NFR1.5.1・NFR1.5.2・NFR5.1.1・NFR5.2.1・NFR5.2.2）
- 技術の選択: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u1-time-zone-conversion/nfr-requirements/tech-stack-decisions.md`
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u1-time-zone-conversion/functional-design/functional-spec.md`・`rules.md`
- 質問と答え: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u1-time-zone-conversion/nfr-design/nfr-design-questions.md`（Q1: A・Q2: A・Q3: A）

## Assumptions & Open Questions

- [assumption] 名前の上限 64 文字は、IANA の最長の名前（32 文字前後）の約 2 倍の見立て。実際の最長は、コード生成のテストで `Intl.supportedValuesOf('timeZone')` の全件を通して確かめる。
- [assumption] キャッシュの上限 128 件は、利用者が選べる名前（約 400 件）の全部を同時に使わない見立て。通常の利用で上限に当たらないことと、当たっても結果が変わらないことは、SD7.6 で確かめる。
- Firefox・Safari での値の確かめは、この PC に入っておらず未確認（Unverified）。持ち主の段は Deployment Execution のスモークテストで、依頼者が実際の画面で見る（Q3: A）。確かめるまで、対象のブラウザ（NFR Requirements の Q3: A）のうち Chromium 系だけが確認済みである。
- 未解決の点は、上の1件のほか無い。
