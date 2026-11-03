# 監視の設計 — u4-business-data-time

出典: NFR Design（`construction/u4-business-data-time/nfr-design/performance-design.md`・`security-design.md`・`logical-components.md`）、NFR Requirements（`nfr-requirements/performance-requirements.md` の NFR2.1.1・NFR2.1.2）、既存の実物（読み取りだけ: `docker/monitoring/dashboards/mastersmith-overview.json` の「業務データ（Intent 261016-business-data-views）」の区画・警報の決まり）、`project.md`（Deployment・監視）。NFR Design に独立した観測の設計は無い（ui の単位は作らない）。

U4 は新しい API・指標・ログ・警報を足さない。日時の変換はブラウザの中で、サーバーに新しい処理の区間が無い。既存の監視で足りるかを確かめ、足りない点を明記する。

## Metrics & KPIs

| Metric | Source | Threshold | Why it matters |
|---|---|---|---|
| 業務データの API（定義・一覧・詳細・参照）の応答時間（95 パーセンタイル） | 既存の `http.server.requests`（ダッシュボードの「業務データ」の区画） | 今の目標のまま（変えない） | `labelValueType` を足しても遅くならないこと（NFR2.1.2）。測るのは負荷の試験で、常時の警報は無い（受け入れた制約） |
| 業務データの API の要求の数（状態コードごと） | 同上 | 5xx が 0 | 定義の応答の組み立てが壊れていないこと（NFR4.2.1） |
| 1 要求の接続の数 | 結合テスト（`BusinessDataConnectionUsageIT`・`ReferenceConnectionUsageIT`）。常時の指標は無い | 変更前と同じ | 問い合わせが増えていないこと（NFR2.1.1） |
| 画面の時刻の整形の回数 | 画面の単体テスト（常時の指標は無い） | 時点の値の数以下 | 画面の変換が重くならないこと（NFR2.1.3） |

## Alerts

| Alert | Condition | Severity | Routes to |
|---|---|---|---|
| 新しい警報は足さない | — | — | — |

既存の警報（5xx の割合 `ms-5xx-ratio`、アプリの不在 `ms-app-absent`、接続プールの待ち `ms-pool-pending` など）が、業務データの API の失敗も含めて効く。画面の側の夏時間の誤り・読み込みの失敗は利用者への案内で伝え、サーバーに届かないため警報の対象にならない。手元の監視は常時動かしていないため、通知の先も無い（`project.md` Deployment）。

## SLIs / SLOs

| SLI | SLO target | Measurement window |
|---|---|---|
| 業務データの API の応答時間（95 パーセンタイル） | 今の目標のまま（変更なし） | 負荷の試験の1回の実行。常時の SLO は置かない（手元の監視は常時動かさない。`project.md`: Unverified とし、基準の値を並べて記録する） |

## Logs & Tracing

| 項目 | 内容 |
|---|---|
| ログ | 新しいログを足さない。`ReferenceField`・`ReferenceFieldResponse` の `toString` に型の名前が増えるだけで、値・ラベルは出さない（TRACE を有効にしても出ないことを `ReferenceSecretLeakIT` で確かめる） |
| トレース | 変更なし。業務データの要求のトレースに新しい区間を足さない |
| 画面 | 画面のエラー報告の仕組みは無い。夏時間の誤り・読み込みの失敗は画面の案内で伝え、値をコンソールに出さない（`security-design.md` 2.5） |

## Dashboards

新しいパネルは足さない。ダッシュボードの「業務データ」の区画（業務データの API と参照の API の要求の数・応答時間）を、配備の後の確かめで使う（`cicd-pipeline.md` 3 節）。

## 監視の確かめで気をつけること（`project.md` の学び）

- 要求を送って指標を見るときは、指標の送信の周期（1 分）を複数またぐ。
- 監視の確かめの要求は、負荷の試験の場面ではなく、待ちを入れた少ない要求で送る。
- 手元の監視のコンテナを入れ替えた後は、`docker inspect` でイメージの版とダイジェストが `compose.yaml` と一致することを確かめる。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- NFR Design: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/nfr-design/performance-design.md`・`security-design.md`・`logical-components.md`
- NFR Requirements: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/nfr-requirements/performance-requirements.md`
- 既存の実物（読み取り）: `docker/monitoring/dashboards/mastersmith-overview.json`
- 進め方: `aidlc/spaces/default/memory/project.md`

## Assumptions & Open Questions

- [assumption] 手元の監視は常時動かさず、配備の後の確かめと負荷の試験のときだけ起動する（今の方針）。
- 未解決の点は無い。
