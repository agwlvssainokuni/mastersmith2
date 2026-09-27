# Observability Requirements — U8 インスタンスの見た目の設定（u8-instance-appearance）

U8 の指標・トレース・ログ・健全性の要件です。手元の監視（grafana/otel-lgtm を compose の profile で必要なときだけ起動し、ダッシュボードと警報の決まりはファイルでリポジトリに置く）の決まりに従う（`aidlc/spaces/default/memory/project.md` の Deployment）。答えは `nfr-requirements-questions.md`（Consolidated Summary Confirmation: Looks correct）。

要件定義に観測性の NFR が無いため、次のとおり寄せます。

- 指標とトレース（応答時間の目標 NFR6.1 を運用で見る仕組み）は **NFR6**（応答時間）の枝番に寄せる。
- 起動時の警告のログ（FR8.2 の「警告のログを出す」と BR2.1）、監査に残さないこと、健全性は、要件 FR8.2・FR9.1 に由来し NFR の ID が無いため、確かめ方を決める **NFR9**（テスト）の枝番に寄せる。

出典の略号: NFR・FR は `aidlc/spaces/default/intents/260925-user-management/inception/requirements-analysis/requirements.md`、BR はこの単位の `construction/u8-instance-appearance/functional-design/rules.md`、「要点 n」はこの段の `nfr-requirements-questions.md` の「NFR の要点（案）」の番号。枝番はこの単位の中で振る。

## 1. 指標とトレース

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR6.5 | 独自の指標は足さない。要求の数・誤りの数・応答時間は、既存の HTTP の要求の指標（Micrometer の `http.server.requests`。`uri` のラベルが `/api/appearance`、`status`・`outcome` のラベルで誤りを分ける）で見る | observability-setup の段で、実際に起動して `uri` のラベルの値を確かめ、既存のダッシュボード（`docker/monitoring/dashboards/`）の式で `/api/appearance` の値が出ることを確かめる（`project.md` の Corrections: 式は書く前に名前を確かめ、書いた後に実行して確かめる）。新しいパネル・警報を足すかはその段で決める | NFR6、要点 10、`aidlc/spaces/default/memory/phases/operation.md`（健全性と誤りの割合の指標） |
| NFR6.6 | トレースは既存の自動の仕組み（Micrometer Tracing、W3C Trace Context）のまま使い、独自のスパン・属性を足さない。外部エクスポートは既存の決まりどおり既定で無効 | 既存のトレースのテストのまま（U8 で足すテストは無い） | NFR6、要点 10、`team.md` の Deployment |

## 2. ログ

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR9.4 | 許されない値を既定に置き換えたとき、起動時に項目ごとに1件だけ警告のログを出す。水準は WARN、スタックトレースなし、構造化ログのキーと値で「項目の名前（例: `mastersmith.appearance.brand-color`）」「使った既定の値（例: `blue`）」「許される値の一覧」を出す。設定された値そのものは出さない。要求のたびには出さない。設定が無い・空のときは出さない | 結合テスト（ログの出力を集めて、`red`・`mono` を設定したとき WARN がちょうど2件・スタックトレースなし・キーがそろい・`red`・`mono` の文字列を含まないこと、GET を送っても件数が増えないこと、設定なしでは0件であること） | FR8.2、BR1.3・BR1.4・BR1.5・BR2.1、要点 7 |
| NFR9.5 | `GET /api/appearance` の読み取りは監査の出来事を出さない（監査ログとアプリのログは別の仕組みで、監査をログで代用しない） | 結合テスト（GET の前後で監査の表の件数が変わらない） | FR9.1、BR3.5、要点 10 |

アプリのログにトレース ID が含まれることは既存の決まりのまま。要求ごとのアクセスのログは足さない。

## 3. 健全性

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR9.6 | U8 は健全性の判定（HealthIndicator）を足さない。起動の後に失敗しうる依存を持たないため、健全性は既存の `/actuator/health` のまま | 確かめるテストは無い（足さないことの記録）。既存の健全性のテストがそのまま通ること | 要点 10、`reliability-requirements.md` の NFR5.1 |

## 4. SLI と SLO

- SLI は `http.server.requests` の `uri="/api/appearance"` の 200 の割合と応答時間の 95 パーセンタイル。
- 応答時間の目標は `performance-requirements.md` の NFR6.1（同時 10 件で 300 ミリ秒以内）。可用性の SLO は U8 だけには置かず、判定は `Unverified` として持ち主の段に引き継ぐ（`reliability-requirements.md` の NFR9.3）。

## 5. 上流との差

承認済みの文書と食い違う記述は無い。要件に観測性の NFR が無いため、指標とトレースを NFR6、ログ・監査・健全性を NFR9 の枝番に寄せたことだけを記録する（`traceability.json` の NFR6・NFR9 の対象に含める）。
