# コードの品質の評価（mastersmith2）

テストの件数はファイルを数えた値で、実行した結果ではない（今回のスキャンでは Gradle・npm・Docker を実行していない）。実測の件数とカバレッジは Build and Test で取る（`project.md` の Testing Posture）。

## テストとカバレッジ

| 対象 | 置き場 | 数（ファイル） | 道具 |
|---|---|---|---|
| バックエンドの単体テスト | `backend/src/test/java/` の `*Test` | 99 | JUnit 5・jqwik・ArchUnit（8 クラス） |
| バックエンドの結合テスト | 同上の `*IT` | 68 | Spring Boot Test と組み込みの H2、対象DB は Testcontainers |
| テストの補助 | 同上の残り | 全体で 208 | `TestDatabase`・`HttpTestClient`・`LogEvents` など |
| 画面の単体テスト | `frontend/src/**/*.test.ts(x)` | 47 | Vitest＋Testing Library（jsdom）＋user-event＋vitest-axe＋fast-check |
| E2E | `frontend/e2e/` | 4 | Playwright（`./gradlew e2eTest` だけ、`verify` と CI の外） |
| 負荷の試験 | `perf/k6/scenarios.js` | — | k6（テストの関門の外） |

- カバレッジの下限は行 80%・分岐 70%。バックエンドは JaCoCo（`backend/build.gradle.kts` 155〜253 行）で全体の合計に加え、パッケージごとの下限を新しいパッケージだけに当てる（既存の 22 パッケージは一覧 `packagesJudgedByTotal` で外す）。画面は `frontend/vitest.config.ts` の `thresholds`。
- ログの秘密情報の確かめは `*SecretLeakIT`（auth・access・audit・targetdb）と `common/testsupport/LogEvents` で、標準出力側のログの出来事が対象である。

### 今回の7件に関わる既存のテスト

| 件 | テスト | 確かめていること・いないこと |
|---|---|---|
| TD-1 | `common/observability/ExternalExportIT` | `/v1/logs` が届くことは確かめるが、秘密の値が入らないことを確かめるのは `/v1/traces` だけ（192〜202 行付近）。送ったログのキーと値は確かめていない |
| TD-2 | `auth/repository/LoginAttemptStateRepositoryIT`・`auth/service/LoginServiceTest`・`auth/service/LoginConcurrencyIT` | `createIfAbsent` の冪等は1スレッドだけ、行が無いときの作成は模擬だけ、同時の失敗は行がある利用者だけ。行が無い利用者の同時の初めてのログインのテストは無い |
| TD-5 | `docker/check-container-limits.sh` | 変数なしでメモリの上限 1g（1073741824）を期待する（93 行） |
| TD-7 | `frontend/src/features/dsl/DslAdminPage.test.tsx`・`DslConfirmDialog.test.tsx` | 535 行が `Alert` の閉じるボタンを名前「閉じる」で探す |

## リンタと静的解析

| 対象 | 道具 | 関門 |
|---|---|---|
| Java | Spotless（palantir-java-format、ライセンスヘッダー） | 違反で失敗 |
| Java | SpotBugs＋FindSecBugs（`spotbugsGate`、除外は `backend/config/spotbugs-exclude.xml`） | priority 1 と `SQL_` で失敗（`backend/build.gradle.kts` 288〜329 行） |
| Java | ArchUnit（`ArchitectureTest`・`*BoundaryArchitectureTest`） | 層と機能の境界 |
| 画面 | Prettier・oxlint・ESLint（react-hooks）・Stylelint・`scripts/check-license-header.mjs`・`tsc --noEmit` | 違反で失敗 |
| 秘密情報 | Gitleaks（pre-commit と `verify`） | 検出で失敗 |
| 依存関係 | OSV-Scanner・Dependabot | `dependencies.md` の決まり |

`TODO`・`FIXME`・`HACK` は見当たらない。抑止は `DefaultErrorResponseWriter.java` の `@SuppressWarnings("unchecked")` と `frontend/src/types/vitest-axe-matchers.d.ts` の oxlint の1件だけ。

## CI/CD と検査の関門

`./gradlew verify`（`build.gradle.kts` 311〜379 行）が、ローカルの統合前の関門と CI の両方の入口である。段は 0 準備（サブモジュールのビルドと変更の無さの確認を含む）→ 1 フォーマット → 2 リンタ → 3 ライセンスヘッダー → 4 ビルド → 5 単体テスト → 6 結合テスト（組み込みの H2 と対象DB のコンテナ）→ 7 カバレッジ → 8 安全の検査 → 9 成果物。

- CI（`.github/workflows/ci.yml`）: `develop` へのプッシュと `v*` のタグで `verify` を動かす。サブモジュールは固定先で取得する。
- 配備: `Dockerfile` と `compose.yaml` で開発者の PC に限る。

## 文書

- `README.md`（約 600 行）: 道具・検査・起動・環境変数・対象DB・DSL・監視・監査・差し込み口。メモリの上限は 204〜208 行（既知の制約）と 230 行（環境変数の表、既定 1g）。監査の書き込みの失敗の ERROR に記録しようとした項目を載せることは 541 行に明記。
- `perf/README.md`（2g で流す手順、18・109 行）、`frontend/src/features/README.md`。
- Java は日本語の Javadoc、TypeScript はファイル先頭の説明が丁寧で、設計の番号（BR・NFR・ADR）を参照している。

## 技術的負債

今回の Intent の7件（番号は Intent の説明の番号と同じ）。場所と事実は開発担当のスキャンにより、`LoginService`・`LoginAttemptStateRepository`・`ObservabilityConfig`・`compose.yaml`・`perf/k6/scenarios.js`・`V3`・`logback-spring.xml`・`application.yaml` の該当行はアーキテクトが読み直して確かめた。

| ID | 所見 | 場所 | 影響 |
|---|---|---|---|
| TD-1 | OTLP のログの出力が、キーと値を属性として送る設定（`setCaptureKeyValuePairAttributes`）を呼んでいない。Loki では本文の文字列だけで絞り込める | `backend/src/main/java/cherry/mastersmith/config/ObservabilityConfig.java` 93〜101 行、出力の取り付けは有効時だけ（67〜71 行）。標準出力は `logback-spring.xml` 48 行の `<keyValuePairs/>` で項目になる | `dsl.operation` などで警報・ダッシュボードを絞れない（`docker/monitoring/provisioning/alerting/mastersmith.yaml` は本文の文字列だけで絞っている） |
| TD-2 | ロックの状態の行が無い利用者の同時の初めてのログインで、両方が行を見つけられず `MERGE` を行うと、後の方が主キーの重複になりうる（500） | `auth/service/LoginService.java` 161〜168 行（`lockUserRow`）、`auth/repository/LoginAttemptStateRepository.java` 65〜73 行（`lockForUpdate`）・102〜110 行（`createIfAbsent`）、`V3__u2_authentication.sql` 21〜26 行 | 行は通常 `LoginAttemptStateInitializer` で利用者と同時に作るため、起きるのは SQL で直接入れた利用者（負荷の試験の利用者）など。`decide` は `transaction.execute` の中（119 行） |
| TD-3 | `dslMixed` の `loginLoop` が `exec.vu.idInTest`（場面をまたいだ通しの番号）で利用者を選ぶため、`logins` 側の2つの VU が同じ `perf-userNN` になりうる | `perf/k6/scenarios.js` 49〜58 行・163 行 | 負荷の試験の結果が同じ利用者の重なりで崩れうる。単独の場面（`loginSuccess`・`refresh`）は重ならない |
| TD-4 | 起動時の Hibernate の案内が改行を含む1件のログになる。ロガーごとの水準の指定が無い | `logback-spring.xml` 56〜58 行（ルート INFO と JSON の出力1つ）、`application.yaml` 246〜259 行（`org.hibernate` の指定なし） | 1行1件の JSON の読みやすさ。どのロガーの名前で出ているかは未確認 |
| TD-5 | アプリのコンテナのメモリの上限の既定が 1g。10MB の DSL には 2g が要る | `compose.yaml` 63 行（説明 57〜61 行）、`docker/perf/compose.yaml` 57 行、`.env.example` 24〜26 行、README 204〜208・230 行、`docker/check-container-limits.sh` 20・93 行。一方 `perf/dsl-timing.sh` 55 行と `perf/README.md` 18・109 行は 2g、`compose.yaml` 99〜101 行の lgtm の説明は「アプリ（上限 2GB）」 | 既定と説明・確かめが食い違っている。既定を変えるときは5か所以上を合わせて直す |
| TD-6 | `app` のコンテナが見本の対象DB の管理者のパスワードを環境変数で持つ | `compose.yaml` 32〜34 行（`env_file: .env`）、`.env.example` 72〜73 行、見本の対象DB の3サービス（122・135・148 行付近） | アプリが対象DB に使うのは `MASTERSMITH_TARGET_DB_*` だけで、管理者のパスワードは要らない。負荷の試験の環境は別の環境ファイルで分けている |
| TD-7 | 今の make-you-chic-ui の checkout では、`Modal`・`Alert` の閉じるボタンが `aria-label="閉じる"` 固定で、`Modal` は `aria-labelledby` だけ（`aria-describedby` の口が無い） | `vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Modal/Modal.tsx` 23〜32・87〜88・97 行、`Alert.tsx` 69 行。使用箇所は `frontend/src/features/dsl/DslConfirmDialog.tsx` 108 行と `DslAdminPage.tsx` 134 行 | 英語の表示で閉じるボタンが日本語のまま、確かめの本文がダイアログの説明として結び付かない。直した版への固定先の更新が要る |

そのほかの兆し（今回の範囲の外、記録だけ）:

- 大きなファイル: `frontend/src/features/dsl/useDslAdmin.ts`（492 行）・`dslmanage/service/DslLifecycle.java`（462 行）・`frontend/src/features/dsl/messages.ts`（418 行）。
- Dependabot がサブモジュールと vendor の npm を対象にしていない（脆弱性は OSV-Scanner が vendor の lockfile も検査する）。
- 前の Intent で後に回した束 2（内部DB のファイルの伸び、10MB の DSL とログインの重ねでのメモリ）は未解決。
- 1要求で接続を2本使う監査の形（プールの上限 既定 30）は README の既知の制約のまま。

## この Intent に向けた懸念

どう扱うかは要件と設計の段で決める（ここでは決めない）。

| ID | 懸念 | 事実と根拠 |
|---|---|---|
| C-1 | TD-1 でキーと値を送ると、個人に関する値が Loki に送られる | `audit/service/AuditEventListener.java` 151〜158 行: 監査の書き込みの失敗の ERROR に、記録しようとした全項目（入力されたメールアドレス・送り元の IP・User-Agent・要求のパスなど）を載せる。`user/service/InitialAdminInitializer.java` 82・88・91 行: 初期管理者のメールアドレスを INFO に載せる。`LoginService.java` 146 行: ロックした利用者の ID。パスワード・トークンの値を載せる呼び出しは見当たらない。`project.md` の Forbidden（パスワード・トークン・署名鍵を外部へのエクスポートに含めない）に加え、メールアドレスなどを送るかどうかの判断が要る。送ったログの中身を確かめるテストは無い |
| C-2 | TD-5 の既定を変えるときに合わせて直す場所 | `compose.yaml` 63 行、`docker/perf/compose.yaml` 57 行、`.env.example` 24〜26 行、README 204〜208・230 行、`docker/check-container-limits.sh` 20・93 行（既定 1g を期待する確かめ）。colima の VM の大きさ（CPU 4・メモリ 6GiB の前提）とも関わる |
| C-3 | TD-4 のロガーの名前が未確認 | 案内を出すロガーの名前は、読み取りだけのスキャンでは確かめていない。実際のログの `logger` の項目で確かめてから水準を決める必要がある |
| C-4 | TD-7 のサブモジュールの固定先が未照合 | checkout のコミットは `5258c8bb987b0fa6ffd0ad7c4eadc7d4006da52d`（`.git/modules/vendor/make-you-chic-ui/HEAD`）で、親のリポジトリの gitlink とは照合していない。直した後の make-you-chic-ui のコミットも、このリポジトリからは確かめていない。更新は専用のコミットで前後のハッシュを記録する（`project.md` の Mandated） |
| C-5 | 既存テストへの影響 | TD-7: `DslAdminPage.test.tsx` 535 行が閉じるボタンの名前「閉じる」に依存する。TD-2: 認証に関わるため、失敗の場合のテストと不具合を再現するテストを同じコミットに含める（`project.md` の Mandated）。TD-5: `check-container-limits.sh` の期待値。TD-1: `ExternalExportIT` に送ったログの中身の確かめを足すかどうか |
| C-6 | TD-6 の直し方と既存の手順 | アプリに渡す環境変数を分けると、README の対象DB の手順と `.env.example` の説明に関わる。負荷の試験の環境は既に別の環境ファイルの形である |

## 健全性のまとめ

- 層の境界・エラー応答・ログの形式・秘密情報の扱い・検査の関門は、決まりがコードとテストで確かめられている。
- degraded: `auth`（TD-2）・`perf-and-monitoring`（TD-3）。at-risk: `config`・`common-observability`・`audit`・`user`（TD-1・TD-4）、`container-runtime`（TD-5・TD-6）、`frontend-feature-dsl`・`make-you-chic-ui`（TD-7）。一覧は `component-inventory.md`。
