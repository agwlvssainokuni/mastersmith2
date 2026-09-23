# 性能テストの手順（performance-test-instructions）

本Intentの単位は測れる性能の目標（NFR1 系）を持つため、Test Strategy が Standard でも本書を作る。

**大切な前提**: ここに挙げた目標のうち、**負荷をかけた測定が要るもの（応答時間のパーセンタイル、同時 10 件での目標）は Build and Test では判定しない**。判定するのは後の **Performance Validation** の段である。Build and Test は、(a) 負荷をかけずに確かめられる目標（発行する SQL の回数、起動の時間、成果物の大きさ、設定）をここで確かめ、(b) 残りを `Unverified`（判定の持ち主は Performance Validation）として `build-and-test-summary.md` の対象の検証表に残す。

測定の環境は、当面の配備先である**開発者の PC 上のコンテナ**とする。クラウドの配備先が決まったら測り直す。

## 1. 測る対象（負荷をかけた測定が要るもの — Performance Validation が持つ）

| 目標 | 出典 | 値 | 測り方 |
|---|---|---|---|
| U1 NFR1.1 | u1 performance-requirements | ヘルスチェックの 95% が 500 ミリ秒以内 | 同時 10 件の `GET /actuator/health` を繰り返し、95 パーセンタイル |
| U1 NFR1.3 | 同上 | 共通の処理（トレース・ログ・エラー応答）が加える時間が 95% で 20 ミリ秒以内 | 何もしない API と、その仕組みを通さない場合の差 |
| U2 NFR1.1 | u2 performance-requirements | ログインの API の 95% が1秒以内（成功・失敗とも） | 同時 10 件の `POST /api/auth/login` を繰り返し |
| U2 NFR1.2 | 同上 | トークンの更新の API の 95% が1秒以内 | 同時 10 件の更新 |
| U2 NFR1.3 | 同上 | bcrypt の照合1回が 100〜500 ミリ秒 | 照合そのものの時間を測る（cost 12） |
| U2 NFR1.4 | 同上 | 認証つきの要求ごとの処理が 95% で 50 ミリ秒以内 | 何もしない認証つき API |
| U2 NFR1.5 | 同上 | 同じ利用者のログインを1つずつ行う処理が、別の利用者を待たせない | 別の利用者の同時のログイン |
| U2 NFR1.6 | u2 scalability-requirements | 想定の規模（50 名・同時 10 名）を1台で処理する | 上記の負荷の試験 |
| U3 NFR1.1 | u3 performance-requirements | 管理者向け領域の確認用 API の 95% が 300 ミリ秒以内（成功・403 とも） | 同時 10 件 |
| U3 NFR1.3 | 同上 | 拒否の記録が 401／403 の応答を 100 ミリ秒以上遅らせない | 監査ログを有効にした状態の 403 の応答時間 |
| U4 NFR1.1 | u4 performance-requirements | 監査イベント1件の書き込みが、同時 10 件で 95% が 50 ミリ秒以内 | 書き込みの時間を負荷の中で測る |
| U4 NFR1.2 | 同上 | 書き込みが U2 の1秒・U3 の 100 ミリ秒の内側に収まる。**組み込みの H2 が書き込みを1つずつ進めるため、元の操作の確定と監査の書き込み（別のトランザクション）が待ち合う遅れを名指しで測る** | 管理者でない利用者の 403 を同時 10 件で繰り返す（最も余裕の少ない経路） |

いずれも `Unverified`（持ち主: Performance Validation）として残す。Build and Test では負荷の環境を立てないため、ここで測った数字を目標の判定に使わない。

## 2. Build and Test で確かめる目標（負荷をかけずに確かめられるもの）

| 目標 | 値 | 確かめ方（コマンド／テスト） |
|---|---|---|
| U1 NFR1.2 | ヘルスチェックの内部DBの確認は制限時間（既定 2 秒）で打ち切り、DOWN を返す | `common.health.TimeBoundedDbHealthIndicatorTest`、`common.health.HealthEndpointIT`（`./gradlew :backend:test :backend:integrationTest`） |
| U1 NFR1.4 | 起動から要求を受け付けられるまで 30 秒以内 | `docker compose up -d` のあと、`/actuator/health` が UP を返すまでの時間を測る |
| U1 NFR1.5 | 初回の読み込みの JavaScript の合計が、圧縮後 500KB 以内 | `./gradlew verify` の 9 の段（`frontendBundleSize`） |
| U1 NFR1.6 | ログの出力が要求の処理を待たせない（標準出力へ出す） | 設定の確認（`logback-spring.xml`）と `common.observability.JsonLogFormatTest` |
| U1 NFR1.11 | 止めるときに処理中の要求を終える（既定 30 秒） | 設定の確認（`server.shutdown=graceful`） |
| U1 NFR1.7・NFR1.8 | 接続の数の既定 10 本、接続先を設定だけで切り替えられる | 設定の確認、`common.db.DatabasePersistenceIT` |
| U3 NFR1.2 | 判定が内部DBへの問い合わせを増やさない | 発行する SQL の回数を数えるテスト（`access.web.AdminAccessIT`） |
| U4 NFR1.3 | 監査イベント1件の書き込みは追記1回だけで、ほかの表を読まない | `audit.repository.AuditEventRepositoryIT`、`audit.service.AuditEventListenerTest` |
| U4 NFR1.5・U1 NFR1.9・U3 NFR1.5 | 件数と大きさの見積もり | 見積もりの記録（各単位の scalability-requirements と monitoring-design） |

## 3. 起動の時間の測り方（U1 NFR1.4）

```bash
./gradlew :backend:bootWar
docker compose up --build -d
START=$(date +%s)
until curl -sf localhost:8080/actuator/health | grep -q '"UP"'; do sleep 1; done
echo "起動にかかった秒数: $(( $(date +%s) - START ))"
docker compose down
```

`.env` に `MASTERSMITH_DB_PASSWORD` と `MASTERSMITH_AUTH_SIGNING_KEY` を用意しておく。

## 4. Performance Validation の段への引き継ぎ

Performance Validation では次を用意する。

1. 負荷をかける道具（例: k6、Gatling、`hey`）を決める。**まだ決めていない**（Performance Validation の段で決める）。
2. 想定の規模の負荷の形（利用者 50 名・同時 10 名。ログイン／更新／管理者向け領域の確認／403 の4つの流れ）。
3. 測る指標: 応答時間の 50・95・99 パーセンタイル、1秒あたりの処理数、エラーの割合。
4. 上の 1 節の目標ごとに、実測と目標を並べた表。
5. 組み込みの H2 の書き込みの待ち合い（U4 NFR1.2、レビュー指摘 R-02）を名指しで測る試験。
6. 目標を満たさないときは、下げるのではなく、原因（bcrypt の cost、接続の数、監査の書き込みの経路）を直す案を出す。

証拠の置き場所は、Performance Validation の段の記録のディレクトリとする。

## 5. 決めごと

- 目標を満たすために値を緩めない。満たせないときは原因と直し方を出して人に判断を仰ぐ（org.md の Testing Posture）。
- 測定の環境（開発者の PC 上のコンテナ）は、配備先が決まるまでの暫定である。配備先が決まったら測り直し、目標そのものを見直すかどうかを決める。

## Sources

- `construction/u1-app-skeleton/nfr-requirements/performance-requirements.md`、`scalability-requirements.md`、`reliability-requirements.md`
- `construction/u2-authentication/nfr-requirements/performance-requirements.md`、`scalability-requirements.md`
- `construction/u3-access-control/nfr-requirements/performance-requirements.md`、`scalability-requirements.md`
- `construction/u4-audit-log/nfr-requirements/performance-requirements.md`、`scalability-requirements.md`
- 各単位の `code-generation/traceability.json`（`Deferred` の行が持ち主の段を示す）
- `build.gradle.kts`（`frontendBundleSize`）、`README.md`（コンテナでの起動と確認）

## Assumptions & Open Questions

- 負荷をかける道具は未決。Performance Validation の段で決める。
