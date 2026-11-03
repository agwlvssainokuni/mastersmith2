# 監視の設計 — u5-menu-icons-home

出典: NFR Design（`construction/u5-menu-icons-home/nfr-design/performance-design.md`・`security-design.md`・`logical-components.md`）、NFR Requirements（`nfr-requirements/performance-requirements.md` の NFR2.1.2・NFR2.2.1）、既存の実物（読み取りだけ: `docker/monitoring/dashboards/mastersmith-overview.json`・警報の決まり）、`project.md`（Deployment・監視）。NFR Design に独立した観測の設計（`observability-design.md`）は無い（ui の単位は作らない）。

U5 は新しい API・指標・ログ・警報を足さない。既存の監視で足りるかを確かめ、足りない点を明記する。

## Metrics & KPIs

| Metric | Source | Threshold | Why it matters |
|---|---|---|---|
| `GET /api/me/navigation` の応答時間（95 パーセンタイル） | 既存の `http.server.requests`（手元の監視のダッシュボードの「業務のメニューと置き場」の区画） | 1 秒以内（前の Intent の目標を変えない） | `tableKind` を足しても遅くならないこと（NFR2.1.2）。測るのは負荷の試験（`navMenu`）で、常時の警報は無い（受け入れた制約） |
| `GET /api/me/navigation` の要求の数（状態コードごと） | 同上 | 5xx が 0 | 応答の組み立てが壊れていないこと（NFR1.3.1・NFR4.2.1） |
| 1 要求の接続の数 | 結合テスト（`NavigationConnectionUsageIT`・`NavigationCallCountTest`）。常時の指標は無い | 変更前と同じ | 問い合わせが増えていないこと（NFR2.1.1） |
| ホームを開いたときの `/api/` への要求の数 | E2E の確かめ（常時の指標は無い） | 業務のメニュー・作業ロール・プリファレンスの既存の要求のみ | ホームが新しい API を呼ばないこと（NFR2.2.1） |

## Alerts

| Alert | Condition | Severity | Routes to |
|---|---|---|---|
| 新しい警報は足さない | — | — | — |

既存の警報（5xx の割合 `ms-5xx-ratio`、アプリの不在 `ms-app-absent`、接続プールの待ち `ms-pool-pending` など）が、メニューの API の失敗も含めて効く。メニューの API だけを見る警報は前の Intent から無く、この単位でも足さない（応答時間は負荷の試験で測る。`performance-requirements.md` の NFR2.1.2）。手元の監視は常時動かしていないため、通知の先も無い（`project.md` Deployment）。

## SLIs / SLOs

| SLI | SLO target | Measurement window |
|---|---|---|
| メニューの API の応答時間（95 パーセンタイル） | 1 秒以内（変更なし） | 負荷の試験の 1 回の実行（3 分）。常時の SLO は置かない（手元の監視は常時動かさない。`project.md`: Unverified とし、基準の値を並べて記録する） |

## Logs & Tracing

| 項目 | 内容 |
|---|---|
| ログ | 新しいログを足さない。許す一覧に無い `icon` の置き換えは今までどおりログを出さない。`NavNode`・`NavNodeResponse` の `toString` は変えない（業務のテーブルの名前・表示名をログに出さない。TRACE を有効にしても出ないことを `NavigationSecretLeakIT` で確かめる） |
| トレース | 変更なし。メニューの要求のトレースに新しい区間を足さない |
| 画面 | 画面のエラー報告の仕組みは無い。ホームの失敗は利用者に案内と「もう一度」で伝える（既存） |

## Dashboards

新しいパネルは足さない。ダッシュボードの「業務のメニューと置き場」の区画（`/api/me/navigation` の要求の数と応答時間）を、配備の後の確かめで使う（`cicd-pipeline.md` 3 節）。

## 監視の確かめで気をつけること（`project.md` の学び）

- 要求を送って指標を見るときは、指標の送信の周期（1 分）を複数またぐ。1 回ずつの送信では値が小さく見える。
- 監視の確かめの要求は、負荷の試験の場面ではなく、待ちを入れた少ない要求で送る。
- 手元の監視のコンテナを入れ替えた後は、`docker inspect` でイメージの版とダイジェストが `compose.yaml` と一致することを確かめる。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- NFR Design: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/nfr-design/performance-design.md`・`security-design.md`・`logical-components.md`（観測の独立した文書は、ui の単位が作らないため無い）
- NFR Requirements: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/nfr-requirements/performance-requirements.md`
- 既存の実物（読み取り）: `docker/monitoring/dashboards/mastersmith-overview.json`
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 手元の監視は、この Intent の配備の後の確かめの間だけ起動する（常時は動かさない）。
- 未解決の点は無い。
