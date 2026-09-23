# Observability Requirements — U4 DSL の管理（u4-dsl-management）

U4 の指標・ログ・ダッシュボードを示す。手元の監視（grafana/otel-lgtm を compose の profile で必要なときだけ起動し、ダッシュボードと警報の決まりはファイルでリポジトリに置く）の決まりに従う（`aidlc/spaces/default/memory/project.md` の Deployment）。答えは `nfr-requirements-questions.md`（Q4: A）。

## 1. 指標

| ID | 指標（Micrometer の Timer） | タグ | 用途 |
|---|---|---|---|
| NFR1.16 | `mastersmith.dsl.operation`（操作の時間と件数） | `operation`: generate・submit・restore・apply・discard・compare、`outcome`: success・rejected・failed・busy | 性能の目標（NFR1.6・NFR1.8・NFR1.10）を運用で見る。拒否・失敗・`DSL_BUSY` の件数を見る |

- タグの値は決まった語だけにし、DSL の識別・本文・利用者・接続先を入れない（件数の爆発と漏えいを防ぐ）。
- 既存の HTTP の指標（`http.server.requests`）はそのまま使う。
- 指標の名前とタグは、書く前に実際に起動して確かめ、書いた後にダッシュボードの式をすべて実行して確かめる（project.md の Corrections）。

## 2. ログ

| ID | 要件 | 確かめ方 |
|---|---|---|
| NFR1.17 | 操作ごとに INFO を1件、構造化ログ（キーと値）で出す: 操作・結果・時間・DSL の識別（先頭 12 文字）・出どころ。本文・接続先・パスワード・部品の例外の文言を出さない | 結合テスト（ログの出力を集めて確かめる） |
| NFR5.3 | 想定内の失敗（422・409・413・503）は WARN 以下でスタックトレースなし、想定外（500）は ERROR でスタックトレース付き。変換する境界で1回だけ出す（team.md の Code Style） | 既存の共通の変換のテストに足す |

トレース ID は既存の決まりのとおりログに含まれる。監査ログ（内部DB）とアプリのログは別の仕組みで、監査をログで代用しない。

## 3. ダッシュボードと警報

- 既存のダッシュボード（`docker/monitoring/dashboards/mastersmith-overview.json`）の並びに、DSL の操作の1枚を足す（操作ごとの件数・結果・時間の 95 パーセンタイル）。ファイルで置き、画面からは変えない。
- 警報は足さない（管理者の手動の操作で、まれなため。Q4: A）。
- SLO は配備先が決まってから決める（team.md の Deployment）。今は NFR1 の目標を指標で見られるようにするまで。
