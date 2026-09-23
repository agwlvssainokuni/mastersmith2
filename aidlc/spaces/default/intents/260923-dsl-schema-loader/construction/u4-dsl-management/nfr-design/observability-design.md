# Observability Design — U4 DSL の管理（u4-dsl-management）

U4 の観測の要件（`construction/u4-dsl-management/nfr-requirements/observability-requirements.md`）を満たす作りの方針。

## 1. 指標（NFR1.16）

| 指標 | 型 | タグ |
|---|---|---|
| `mastersmith.dsl.operation` | Timer（95 パーセンタイルを出す） | `operation`: generate・submit・restore・apply・discard・compare、`outcome`: success・rejected・failed・busy |

- 大きさで断った投入（413）は、Filter の知らせの口で U4 が受け、`operation=submit`・`outcome=rejected` として記録する（監査と同じ知らせを使う）。
- 操作の終わりで1回記録する。`busy` は許可が取れなかった要求（scalability-design.md）。`compare` はプレビューの表示の中の照合だけの時間。
- タグの値は決まった語だけ。DSL の識別・本文・利用者・接続先を入れない。
- 名前とタグは、書く前に実際に起動して指標の出口（手元の監視）で確かめる（`aidlc/spaces/default/memory/project.md` の Corrections）。

## 2. ログ（NFR1.17・NFR5.3）

- 操作ごとに INFO を1件、キーと値で出す: `dsl.operation`・`dsl.outcome`・`dsl.durationMs`・`dsl.hash`（先頭 12 文字）・`dsl.source`。本文・接続先・パスワード・部品の例外の文言を出さない。
- 想定内の失敗（413・409・422・503）は変換する境界で WARN 以下・スタックトレースなし、想定外（500）は ERROR・スタックトレース付き（既存の共通の変換）。
- トレース ID は既存の決まりのとおりログに入る。監査ログ（内部DB）とアプリのログは別。

## 3. ダッシュボード

- `docker/monitoring/dashboards/` に DSL の操作の1枚を足す（操作ごとの件数、結果ごとの件数、時間の 95 パーセンタイル）。ファイルで置き、画面から変えない。
- 書いた後にすべての式を実行して確かめる（project.md の Corrections）。
- 警報は足さない（NFR 要件の Q4: A）。
