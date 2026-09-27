# Observability Design — U8 インスタンスの見た目の設定（u8-instance-appearance）

U8 の指標・トレース・ログ・健全性・SLI/SLO の設計です。手元の監視（grafana/otel-lgtm を compose の profile で必要なときだけ起動し、ダッシュボードと警報の決まりはファイルでリポジトリに置く）の決まりに従う（`aidlc/spaces/default/memory/project.md` の Deployment）。答えは `nfr-design-questions.md`（Consolidated Summary Confirmation: Looks correct）。

出典の略号: NFR はこの単位の NFR 要件 `construction/u8-instance-appearance/nfr-requirements/` の枝番（枝番はこの単位の中で振る。指標とトレースは NFR6、ログ・監査・健全性は NFR9 に寄せている）、BR は `construction/u8-instance-appearance/functional-design/rules.md`、「要点 n」は `nfr-design-questions.md` の「NFR 設計の要点（案）」の番号。

## 1. 指標

- 独自の指標は足さない。要求の数・誤りの数・応答時間は、既存の HTTP の要求の指標（Micrometer の `http.server.requests`）で見る。`uri` のラベルが `/api/appearance`、`status`・`outcome` のラベルで誤りを分ける（NFR6.5）。
- 「内部DB の接続を借りない」ことの指標は運用には置かない。確かめは結合テストで行う（`reliability-design.md` の3.2節）。
- ダッシュボードと警報: observability-setup の段で、実際に起動して `uri` のラベルの値を確かめ、既存のダッシュボード（`docker/monitoring/dashboards/`）の式で `/api/appearance` の値が出ることを確かめる。新しいパネル・警報を足すかはその段で決める。式は書く前に名前を確かめ、書いた後にすべて実行して確かめる（`project.md` の Corrections）。

## 2. トレース

- 既存の自動の仕組み（Micrometer Tracing、W3C Trace Context）のまま使い、独自のスパン・属性を足さない。U8 の要求は既存の HTTP のスパンとして記録される（NFR6.6）。
- 外部エクスポートは既存の決まりどおり既定で無効（`team.md` の Deployment）。
- アプリのログにトレース ID が含まれることは既存の決まりのまま（起動時の警告のログは要求の外で出るため、トレース ID は付かない）。

## 3. ログ

### 3.1 起動時の警告（NFR9.4、BR2.1）

| 項目 | 設計 |
|---|---|
| 出す時点 | 業務処理の Bean を作るとき（判定の直後）に、許されない値だった項目ごとに1件。要求のたびには出さない。設定が無い・空のときは出さない |
| 水準 | WARN、スタックトレースなし |
| 書き方 | SLF4J の `LoggerFactory.getLogger` と、キー・値の API（`addKeyValue`）。文字列の連結で値を組み立てない（`team.md` の Code Style） |
| 出す値 | 項目の名前（例: `mastersmith.appearance.brand-color`）・使った既定の値（例: `blue`）・許される値の一覧（例: `blue, green, purple, orange`）。キーの名前はコード生成で決める |
| 出さない値 | 設定された値そのもの（誤って秘密を書いた場合を含む） |

説明用の断片（キーの名前は仮）:

```java
LOG.atWarn()
        .setMessage("見た目の設定の値が許されないため既定を使います")
        .addKeyValue("property", "mastersmith.appearance.brand-color")
        .addKeyValue("defaultValue", "blue")
        .addKeyValue("allowedValues", "blue, green, purple, orange")
        .log(); // 設定された値は渡さない
```

- 確かめ方: 結合テストでログの出力を集め、`red`・`mono` を設定したとき WARN がちょうど2件・スタックトレースなし・キーがそろい・`red`・`mono` の文字列を含まないこと、GET を送っても件数が増えないこと、設定なしでは0件であることを確かめる。
- Spring の文脈ごとに Bean を作るため、テストで文脈を作り直すとそのたびに出る。本番は起動につき1回。

### 3.2 要求ごとのログ

要求ごとのアクセスのログは足さない。想定外の失敗は既存の共通のエラー応答の境界で1回だけ出る（`team.md` の Code Style）。

### 3.3 監査（NFR9.5、BR3.5）

`GET /api/appearance` の読み取りは監査の出来事を出さない。監査ログとアプリのログは別の仕組みで、監査をログで代用しない。結合テストで、GET の前後で監査の表の件数が変わらないことを確かめる。

## 4. 健全性と SLI/SLO

- 健全性の判定（HealthIndicator）は足さない。U8 は起動の後に失敗しうる依存を持たないため、健全性は既存の `/actuator/health` のまま。既存の健全性のテストがそのまま通ること（NFR9.6）。
- SLI: `http.server.requests` の `uri="/api/appearance"` の 200 の割合と、応答時間の 95 パーセンタイル。
- SLO: 応答時間は `performance-design.md` の NFR6.1（同時 10 件で p95 300 ミリ秒以内）。可用性の SLO は U8 だけには置かず、判定は `Unverified` として持ち主の段（observability-setup・feedback-optimization）に引き継ぐ（NFR9.3、`reliability-design.md` の5節）。
- 警報の考え方: U8 だけの警報は置かない。アプリ全体の誤りの割合・健全性の警報（既存）で拾う。足すかは observability-setup の段で決める。

## 5. 相関の ID

相関の ID は既存の W3C Trace Context（`traceparent`）のまま。U8 は独自の ID を作らない。

## 6. 上流との差

承認済みの文書と食い違う設計は無い。
