# 要件定義（Intent 261104-tz-options-perf）

## 意図の分析

- **種類**: 不具合の直し（bugfix）。深さ Minimal・Test Strategy Minimal。
- **起き方**: CI の `frontendCoverage`（カバレッジの計測つきの画面のテスト）で、表示のタイムゾーンの選択肢の組み立ての性能のテスト `TimeZoneOptionsPerformance.test.ts`（目標 100 ms 以内、前の Intent の NFR2.1.6）が、中央値 106 ms で落ちた（コミット f2f2eb9b の CI、AMD EPYC 7763）。同じテストの2回目の失敗。
- **目的**: 目標と測り方を変えずに、`buildTimeZoneOptions` の組み立てそのものを速くし、CI と同じカバレッジの計測つきの条件で余裕を持って通るようにする。あわせて、同じ形の退行を機械の速さに頼らず捕まえられるようにする。
- **範囲**: 画面（フロントエンド）の純粋な関数とそのテストだけ。HTTP の口・サーバー・内部DB・画面の見た目は変えない。

### 現状の要点（コードの知識ベースより）

- 業務の位置づけ: 表示のタイムゾーンは、利用者の自分の設定の画面（`preferences`）で選ぶ（`aidlc/spaces/default/codekb/mastersmith2/business-overview.md`）。
- 作りと流れ: `TimeZoneSelect` が `buildTimeZoneOptions` を呼び、名前ごとに `shared/format/timeZone` の `canInterpretTimeZone`・`formatInstant` を通して時差のラベルを作る（`aidlc/spaces/default/codekb/mastersmith2/architecture.md` の Interaction Diagrams 7）。
- 置き場: `frontend/src/features/preferences/buildTimeZoneOptions.ts` と `frontend/src/shared/format/timeZone/`（`aidlc/spaces/default/codekb/mastersmith2/code-structure.md`）。
- 遅さの原因（走査の所見 T-1・T-2。`inception/reverse-engineering/developer-scan.md`）: 数百の名前の1件ごとに、`Intl.DateTimeFormat` の生成 1 回・`formatToParts` 2 回（同じ時点の壁時計を求め直す）・名前の検査 4 回・同じ時点の文字の読み直し 1 回を行い、表示の文字全体を組んでから時差だけを正規表現で切り出している。整形器のキャッシュの上限 128 件が名前の数より小さく、組み立てのたびに全体の捨て直しが起きる。

## 機能要件

### FR1 選択肢の中身を変えない

- **FR1.1** 各選択肢のラベルは、今と同じ `名前 (±HH:mm)` の形で、同じ時点の同じ時差を示す（夏時間・分の端数の時差を含む）。
- **FR1.2** 選択肢の並び（UTC を含む昇順）・UTC が一覧に無いときの追加・解釈できない名前の除外・重複の除去は、今と同じ結果にする。
- **FR1.3** 保存済みの名前が一覧に無いときの扱いと、ブラウザに合わせる選択肢の扱いは、今と同じ結果にする。

受け入れの条件:

- Given 今の `buildTimeZoneOptions.test.ts`・`TimeZoneSelect.test.tsx` の見本（`America/New_York`・分の端数の時差・夏時間の切り替えの前後・解釈できない名前・保存済みの名前）、When 直した後の組み立てを呼ぶ、Then 返す選択肢（値・ラベル・並び）が直す前と同じである。
- Given 解釈できない名前を含む一覧、When 組み立てる、Then その名前は選択肢に出ない。
- Given fast-check の任意の名前の一覧、When 組み立てる、Then 既存の性質（先頭・重複なし・昇順・UTC あり）が保たれる。

### FR2 1件の名前あたりの仕事を減らす

- **FR2.1** 選択肢を組み立てるときの名前ごとの仕事から、同じ時点の壁時計の求め直し、名前によらない時点の文字の読み直し、表示の文字全体の組み立てと時差の切り出し、同じ名前の重複した検査を除く。
- **FR2.2** 1件の名前あたりの `Intl.DateTimeFormat` の生成の回数と `formatToParts` の呼び出しの回数に上限を置く。上限の値は、Code Generation で作りを決めた後に、その作りの回数で決める（Q2: A）。

受け入れの条件:

- Given 整形器のキャッシュを空にした状態と N 件の名前の一覧、When 組み立てる、Then `Intl.DateTimeFormat` の生成の回数と `formatToParts` の呼び出しの回数が、それぞれ「N × 決めた上限」以下である。
- Given 作りが今の重い形に戻った場合、When 同じテストを流す、Then 回数の上限を超えてテストが落ちる。

### FR3 選択肢の組み立てで共有の整形器のキャッシュを追い出さない

- **FR3.1** 選択肢の組み立てに使う整形器は、時刻の表示で共有する整形器のキャッシュ（上限 128 件）に入れない（Q4: A）。
- **FR3.2** 時刻の表示（`formatInstant` など）は、今と同じ結果を返す。

受け入れの条件:

- Given 表示のタイムゾーンの整形器が共有のキャッシュに入っている状態、When 選択肢を組み立てる、Then 組み立ての後も、共有のキャッシュの件数と中身が組み立ての前と同じである。
- Given キャッシュの上限を超える名前の数で時刻の表示を呼んだ場合、When 結果を比べる、Then 既存のテスト（`keeps working past the cache limit …`）どおり結果は変わらない。

## 非機能要件

### NFR1 性能（目標と測り方は変えない）

- **NFR1.1** 性能の目標（前の Intent の NFR2.1.6、選択肢の組み立て 100 ms 以内）と測り方（`TimeZoneOptionsPerformance.test.ts` の5回の中央値、各回で整形器のキャッシュを空にし新しい配列を使う、`performance.now()` で組み立て1回だけを挟む）は変えない。`clearFormatterCache()` で消えない別のキャッシュに時差を持つ形は、測りの意図（冷たい回）を変えるため採らない。
- **NFR1.2** 手元の PC で、カバレッジの計測つき（`frontendCoverage` と同じ条件）の中央値を、直す前の手元の値の半分以下にする（Q1: A）。直す前と直した後の値（5回の値・中央値・名前の数・Node の版・CPU）を記録する。
- **NFR1.3** push の後の CI（`frontendCoverage`）で性能のテストが通ることを確かめ、試行ごとの結果と中央値を記録する（Q1: A。`project.md` の CI の結果の記録の決まり）。

受け入れの条件:

- Given 直す前の手元のカバレッジつきの中央値 T0、When 直した後に同じ条件で流す、Then 中央値が T0 / 2 以下である。
- Given 直した後のコミットを依頼者が push した、When CI が流れる、Then `frontendCoverage` の性能のテストが 100 ms 以下で通り、その値が記録されている。

### NFR2 機械の速さに頼らない退行のテスト

- **NFR2.1** FR2.2 の回数を数えるテストを、`./gradlew verify` と CI で毎回流れる画面の単体テストとして足す（Q2: A）。性能のテストと違い、機械の速さと同時に流れるほかのテストに左右されない。
- **NFR2.2** このテストを、直しと同じコミットに含める（`project.md` の Mandated「不具合を修正するときは、その不具合を再現するテストを同じコミットに含める」）。直す前の作りで流すと落ちることを記録する。

受け入れの条件:

- Given 直す前の作り、When 回数のテストを流す、Then 上限を超えて落ちる。
- Given 直した後の作り、When 回数のテストを流す、Then 通る。

### NFR3 既存の検査とテストを保つ

- **NFR3.1** `./gradlew verify` のすべての検査（フォーマット・リンタ・ライセンスヘッダー・型の検査・全テスト・カバレッジの下限 行 80%・分岐 70%・秘密情報の検出・静的解析・依存関係の脆弱性検査）が通る。
- **NFR3.2** ESLint の時差の規則（`Intl.DateTimeFormat` を `timeZone` の指定なしで作らない、`shared/format/timeZone` の依存の決まり）を緩めない。新しい関数を置くなら `frontend/src/shared/format/timeZone/` の中に置く。

受け入れの条件:

- Given 直した後のコミット、When `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` を流す、Then すべて通り、テストの件数とカバレッジの実測の値が記録されている。

## 制約

- 時差の表記 `timeZoneName: 'longOffset'` は使わない。前の Intent（261102-timezone-ui-polish の U1 の NFR Design、Q1: A）の「時差は壁時計の数字と元の時点の差から求める」を保つ（Q3: A）。項目を減らした整形器（`longOffset` を使わないもの）は使ってよい。捨ての試しで足りないと分かったときは、改めて依頼者に諮る。
- 新しい依存を足さない（ブラウザの標準の `Intl` だけを使う）。
- 同じテストの2回目の失敗のため、直すまで次へ進まない（`team.md` の Testing Posture の不安定なテストと CI の失敗の決まり）。
- ソースファイルの先頭に Apache License 2.0 のヘッダーを置く。コメントとコミットのメッセージは日本語、テストの説明文は英語。
- E2E はブランチごとの統合の前には流さず、Intent の最後（Build and Test）に流す（`team.md` の Way of Working）。

## 前提

- 直し方の効き目（JS の仕事の削減で足りるか、整形器の生成の時間が支配的か）は分かっていない（走査の所見 T-1・T-4、未検証）。Code Generation の計画の前に、カバレッジの計測つきで捨ての試しを行い、候補ごとの値を測ってから作りを決める（`project.md` の「部品の振る舞いに頼る前提は捨ての試しで確かめる」）。
- 手元の PC は CI の機械より速いと見込む。そのため、手元の合格の線を直す前の値との比で決めた（NFR1.2）。CI の値は push の後にしか分からない。
- `buildTimeZoneOptions.test.ts` の `'builds the offsets once per array …'`（`formatInstant` の呼び出しを数える）は、組み立てが `formatInstant` を呼ばない形になると直す必要がある。直すときも「同じ配列の2回目以降は時差を求め直さない」という確かめの意図は保つ。

## 範囲の外

- 性能の目標の値（100 ms）と測り方の変更。
- 選択肢のラベルの形・並び・内容の変更。
- 整形器のキャッシュの上限（128 件）の変更（FR3 で組み立てがキャッシュを使わなくなるため、上限を変える要は無い）。
- 時刻の表示（`formatInstant`）・時刻の入力（`parseLocalDateTime`）の作りの変更（共有の部品を足す・使い回す範囲を除く）。
- `longOffset` の採用（制約のとおり）。
- サーバー・API・内部DB・E2E の追加。

## 未解決の点（後の段へ）

- FR2.2 の回数の上限の値（Code Generation の計画で、捨ての試しで決めた作りの回数から決める）。
- 捨ての試しで、`longOffset` を使わない形では NFR1.2 に届かないと分かった場合の扱い（そのときに依頼者に諮る）。

## Sources

- 依頼の記述（`project-description.json`）
- `inception/requirements-analysis/requirements-analysis-questions.md`（Q1〜Q4、すべて A）
- `aidlc/spaces/default/codekb/mastersmith2/business-overview.md`・`architecture.md`・`code-structure.md`
- `inception/reverse-engineering/developer-scan.md`（所見 T-1〜T-6）
- 前の Intent の記録（`261102-timezone-ui-polish` の U1 の NFR Design の Q1: A）
- `aidlc/spaces/default/memory/team.md`・`project.md`
