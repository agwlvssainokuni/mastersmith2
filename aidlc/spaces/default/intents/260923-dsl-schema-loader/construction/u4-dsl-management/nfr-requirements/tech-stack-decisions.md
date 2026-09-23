# Tech Stack Decisions — U4 DSL の管理（u4-dsl-management）

U4 の技術の選定と、承認済みの文書との差の一覧を示す。答えは `nfr-requirements-questions.md`（Q1〜Q4）。

## 1. 選定

U4 は新しい依存を足さない。

| 対象 | 選定 | 理由 |
|---|---|---|
| 重い処理の同時の数の制限 | Java 標準の `Semaphore`（許可 1、待たずに試す `tryAcquire`） | Q2: A。アプリは1つ（ADR-004）なので、アプリの中の制限で足りる |
| 照合の待ち時間 | U1 の読み取りに、呼び出しごとの待ち時間（接続 3 秒・問い合わせ 5 秒）を渡す | Q1: A。生成（5 秒・20 秒）と照合で上限を分ける。渡し方（U1 の読み取りの口に待ち時間の引数を足すか、照合用の接続の設定を分けるか）は NFR 設計で決める |
| 指標 | Micrometer の Timer（既存の Spring Boot の構成） | Q4: A |
| 投入の API の本文の上限 | 既存の本文の上限の仕組み（`mastersmith.web.max-request-body-size`、既定 1MB）に、この API の道だけ上限を変える口を足す | NFR2.6。作りは NFR 設計で決める |
| ダッシュボード | 既存の `docker/monitoring/dashboards/` に JSON で足す | project.md の Deployment |

## 2. 承認済みの文書との差

承認済みの文書は書き換えない（`aidlc/spaces/default/memory/project.md` の Way of Working）。実装・README・画面の文言は、コード生成でこの表に合わせる。

| 決定 | 文書 | 承認済みの記述 | 実装での扱い |
|---|---|---|---|
| 新しい code `DSL_BUSY`（503）（Q2: A） | `inception/contract-design/contract-summary.md` の C6（code の一覧と各 API の応答） | `DSL_BUSY` は無い | 生成・投入・戻し・プレビューの表示の API に 503 `DSL_BUSY` を足す。`ProblemTypeCatalog` に日英の説明つきで登録する |
| 同上 | `construction/u5-dsl-admin-ui/functional-design/functional-spec.md` の 6節（code ごとの文言） | `DSL_BUSY` の文言は無い | 画面に「ほかの処理中です。少し待ってからやり直してください」（ja・en）を足す。U5 の NFR 要件で扱う |
| 同上 | `construction/u4-dsl-management/functional-design/functional-spec.md` の 6節（失敗の場合） | 重なりの扱いは無い | 503 `DSL_BUSY`、状態を変えず、監査の出来事を出さない（NFR1.14） |
| 照合の待ち時間（Q1: A） | `construction/u1-target-db/functional-design/rules.md` の BR1.8（上限の時間で打ち切る、数値は NFR 設計） | 1組の上限 | U1 の NFR 要件（5 秒・20 秒）は生成、照合は 3 秒・5 秒 |
| 同上 | `inception/contract-design/contract-summary.md` の C1・C3（`TargetSchemaReader.readSchema` は引数なし） | 呼び出しごとの待ち時間を渡す口が無い | NFR 設計で、読み取りの口に待ち時間の引数を足すか、照合用の設定を分けるかを決める。口を変えるなら U1 の作りも変わる（U1 の NFR 設計とコード生成で扱う） |
| DSL の大きさの上限 10MB（U3 の NFR 要件、Q1: B） | 契約 C6、U4 の機能設計 BR1.2・BR1.6 ほか | 5MB | 10MB（差の一覧は U3 の tech-stack-decisions.md 3節） |
