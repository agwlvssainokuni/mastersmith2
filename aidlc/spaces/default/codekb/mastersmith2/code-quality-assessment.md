# コードの品質の評価（mastersmith2）

## テスト

- バックエンド: `*Test` 60 件（単体）、`*IT` 44 件（Spring と組み込み H2 を起動する結合）。結合テストの DB は `TestDatabase.register`（`@TempDir` の H2 ファイル）で、プールの大きさは本番と同じ 10。
- フロントエンド: `*.test.ts(x)` 29 件。E2E は Playwright 3 件（`verify` と CI の外）。
- カバレッジの下限: JaCoCo（`backend/build.gradle.kts` 131〜186 行、行 80%・分岐 70%、除外は起動クラスと `*Properties`）、Vitest（`frontend/vitest.config.ts` 33〜43 行、lines 80・branches 70）。
- 境界の検査: `ArchitectureTest`・`AuthBoundaryArchitectureTest`・`AuditBoundaryArchitectureTest`（ArchUnit）。

## 検査の道具と CI

- Java: Spotless（palantir-java-format ＋ ライセンスヘッダー）、SpotBugs ＋ FindSecBugs（`spotbugsGate` は High だけで失敗、除外は `backend/config/spotbugs-exclude.xml`）。
- 画面: oxlint・ESLint・Prettier・Stylelint、ライセンスヘッダーは `frontend/scripts/check-license-header.mjs`。
- 秘密情報: Gitleaks（`.gitleaks.toml`、`.pre-commit-config.yaml` でもコミット前に実行）。依存の脆弱性: OSV-Scanner（`config/npm-build-tools.txt`）、Dependabot（gradle・npm・github-actions・docker）。
- CI: `.github/workflows/ci.yml`。`develop` へのプッシュと `v*` タグで `./gradlew verify`。アクションは SHA で固定。

## 文書

`README.md`（起動・環境変数・U 間の連携の表）、`perf/README.md`、`frontend/src/features/README.md`。Java は全クラスに日本語の Javadoc があり、設計の番号（BR・NFR）を参照している。TODO/FIXME/HACK は 0 件。`@SuppressWarnings("unchecked")` が main に1件（`common/error/web/DefaultErrorResponseWriter.java` 79 行）。

## 技術的な負債とリスク

### TD-1（F2、重大度: 高）ログインの接続を持ったまま、確定の後に監査が2本目を借りる

- 経路: `LoginService.login` の `TransactionTemplate.execute`（`backend/src/main/java/cherry/mastersmith/auth/service/LoginService.java` 119 行）→ 確定 → 後始末の前の afterCommit で `AuditEventListener.onAuthenticationEvent`（`backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java` 87〜91 行、同じスレッド）→ `AuditEventRecorder.record`（`backend/src/main/java/cherry/mastersmith/audit/service/AuditEventRecorder.java` 51〜54 行、`REQUIRES_NEW`）が新しい接続を借りる。spring-orm の `DELAYED_ACQUISITION_AND_HOLD` により、ログインの接続は EntityManager を閉じる（後始末）まで返らない。
- 結果: 同時の成功のログインがプールの数（10）に達すると全員が待ち合い、`connection-timeout` 5000ms の後に 10 件とも `CannotCreateTransactionException`。`AuditEventListener` が受け止めて ERROR「監査イベントの記録に失敗しました」を出し、応答は 200（約 7.2 秒）。**監査の行が欠ける**。
- 経緯: 前の Intent の U4 の nfr-design で「一時的に2本使う」ことを受け入れ、負荷の試験で確かめるとしていた。performance-validation の F2 で抜けられない待ち合いが確かめられた。図は `architecture.md` の「対話の図」。

### TD-2（重大度: 中）同じ2本使いがログインの成功以外にもある

- `LOGIN_FAILED`（`LoginService.java` 134・152 行。ユーザーが無い・パスワード誤り・ロック中）。前回の負荷の試験で失敗のログインが尽きなかった理由は未確認（経路の偏りの可能性）。
- `LOGGED_OUT`（`backend/src/main/java/cherry/mastersmith/auth/service/LogoutService.java` 76 行の `@Transactional` の中の 93 行）。
- 2本目の無い経路: `TokenRefreshService.refresh`（出来事なし）、`ACCESS_DENIED`（トランザクションの外で publish）。
- 直す範囲をログインの成功だけにするか、`AuthenticationEvent` の経路全体にするかを要件の段で決める必要がある。

### TD-3（重大度: 高、Mandated 違反の予防）不具合を再現するテストが無い

- `LoginConcurrencyIT` は失敗のログインの同時5件・4件と、ほかの利用者の待ちなし（8 スレッド、78 行）だけ。成功のログインをプールの数だけ同時に流すテストが無い。
- `AuditWriteFailureIT` の `CONNECTION_FAILURE` は `FailingAuditEventRepositoryConfig`（76〜78 行）で例外を投げる模擬であり、本物のプールの枯渇ではない。
- project.md の Mandated（不具合を修正するときは再現するテストを同じコミットに含める）により、同時にプールの数（10）以上の成功のログインを流し、監査の行が欠けないこと（と接続の待ちが起きないこと）を確かめる結合テストが要る。結合テストのプールは本番と同じ 10 なので、そのまま再現できる。

### TD-4（重大度: 中）ヘルスチェックが業務と同じプールを使う

- `backend/src/main/java/cherry/mastersmith/common/health/TimeBoundedDbHealthIndicator.java` 127 行で同じプールから1本借りる。制限時間は既定 2 秒（`mastersmith.health.db-timeout`）で、プールの借りる待ち（5 秒）より短いため、枯渇中は DOWN を返しうる。前の Intent の F1 とも関係する。

### TD-5（重大度: 低〜中）プールの大きさの変更は副作用の確認が要る

- 修正の候補「内部DBのプールの数を増やす」は、2本使いの形を残したまま、尽きるまでの同時の数を上げるだけである（同時の数が新しい上限に達すれば再び起きる）。Tomcat のスレッドの上限は既定（設定なし）で、プールより多い要求が同時に来うる。
- 増やす場合は、組み込み H2 の同時の接続での振る舞い、アプリのメモリ上限 1GB（前の Intent の F3）、ヘルスチェック（TD-4）への影響を合わせて見る必要がある。現在の値の一覧は `architecture.md` の「接続のプールの現在の設定」。

### 修正が守るべき既存の決まりとテスト

| 決まり | 確かめているテスト |
|---|---|
| 記録は要求と同じスレッドで、INSERT は1回 | `AuditAuthenticationEventsIT` 165〜175 行 |
| 監査の行のトレースIDが要求のものと一致 | `AuditTraceIdIT` 121・134・147 行 |
| 元のトランザクションが取り消されたら記録しない | `AuditRollbackIT` 133〜167 行 |
| 書き込みの失敗でも応答は変わらず、ERROR 1件、再試行なし（BR3.1） | `AuditWriteFailureIT` 112〜195 行 |
| 200ms 超で WARN | `AuditWriteTimingIT`・`SlowAuditWriteConfig` |
| 追記は新しいトランザクション（REQUIRES_NEW） | `AuditEventRecorderTest` 54 行 |
| `decide` の中で出来事を publish | `LoginServiceTest` 119〜241 行 |
| audit の `@Transactional` は `audit.service` だけ、トランザクションの境界は service の層だけ | `AuditBoundaryArchitectureTest` 94〜121 行、`ArchitectureTest` 102 行 |

これらのどれかを変える直し方を選ぶ場合は、前の Intent の設計の決定（U4 の nfr-design「別スレッドに移すとトレースIDの一致と確定の後に記録の決まりを変える」）を見直す判断として、要件と設計の段で明示的に扱う必要がある。

### 直した後の確かめに使えるもの

- 監視の `hikaricp_connections_pending` の警報（`docker/monitoring/provisioning/alerting/mastersmith.yaml` 268 行）。
- 負荷の試験の手順（`perf/README.md`、k6 の `constant-vus`）。使い捨ての環境で行う。

### そのほか

- ルートに管理外の内部DBのバックアップ `mastersmith-data-*.tgz`（3つ）と `.env` がある。`.gitignore`（100 行・91 行）で除外済み。コミットの対象にしないこと（中身は読んでいない）。

## 健全性のまとめ

全体としては検査・テスト・文書が揃った健全なコードベースである。既知の不具合は監査の書き込みと接続の扱いの組み合わせ（TD-1〜TD-5）に集中している。部品ごとの評価は `component-inventory.md` を参照。
