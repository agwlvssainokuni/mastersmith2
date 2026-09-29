# コードの構成（mastersmith2）

各部品の責務と状態は `component-inventory.md`、部品の間の依存は `dependencies.md` に書き、ここでは置き場と決まりだけを書く。ファイルの数は前回（`e68f54d`）に `git ls-files` で数えた値で、今回は数え直していない（その後の変更で増えたファイルは本文に書いた）。

## リポジトリの最上位

| パス | 分類 | 内容 |
|---|---|---|
| `backend/` | バックエンド | Gradle のサブプロジェクト。Java のソース・テスト・設定・Flyway のスキーマ変更・SpotBugs の除外 |
| `frontend/` | 画面 | npm のプロジェクト（React＋TypeScript、Vite）。ルートの Gradle から呼ぶ。E2E は `frontend/e2e/` |
| `vendor/make-you-chic-ui/` | デザインシステム | Git サブモジュール（npm の `file:` の依存）。固定先 `310e1ec`（更新の候補 `077f5b4`、K-12）。このリポジトリからは変えない |
| `vendor/java-mustache-processor/` | Mustache のエンジン | Git サブモジュール（Gradle の composite build）。固定先 `8d44c36`（`0.1.0`）。このリポジトリからは変えない |
| `.gitmodules` | サブモジュールの定義 | 上の2つの URL と置き場 |
| `gradle/`・`settings.gradle.kts`・`build.gradle.kts` | ビルド | 版の一覧（`gradle/libs.versions.toml`。ライブラリと Gradle のプラグインの版）、Wrapper（9.8.0）、1コマンドの検査 `verify` と OSV-Scanner の判定 |
| `backend/gradle.lockfile`・`settings-gradle.lockfile`・`frontend/package-lock.json` | 版の固定 | Gradle の依存と npm の依存の実際の版。Gradle のプラグインは載らない |
| `config/` | 検査の設定 | ライセンスヘッダーのひな形、OSV の判定で止める画面の道具の一覧（`npm-build-tools.txt`） |
| `.github/` | CI と更新の通知 | `workflows/ci.yml`・`dependabot.yml`（K-14） |
| `Dockerfile`・`compose.yaml`・`.env.example` | コンテナ | アプリのイメージとサービス、profile で起動する監視・メールの受け手・見本の対象DB |
| `docker/` | 付属の環境 | 手元の監視（`monitoring/` の警報とダッシュボード、K-15）、OTLP の受け手（`otel-collector/`）、見本の対象DB、プールの道具（`jmx/`・`hikari-pool.sh`） |
| `perf/` | 負荷の試験 | k6 の台本と使い捨ての環境（流し読み） |
| `README.md` | 文書 | 起動・環境変数・E2E・監視・警報と対応の手順・監査・招待・既知の制約・戻し方（1,069 行） |
| `aidlc/`・`.claude/` | ワークフローの記録と枠組み | アプリのソースではない |

## バックエンド（`backend/src/main/java/cherry/mastersmith/`、前回 421 ファイル）

```
cherry.mastersmith
├── MastersmithApplication
├── config                                          6   Security の連鎖の組み立て・画面の配信・観測
├── common/{error,health,i18n,observability,security,web}   38
├── auth/{domain,service,repository,web}            50  ログイン・トークン・ロック・ログアウト・期限切れのトークンの定期の削除
├── access/{domain,service,web}                     20  管理者のみの API の認可と 401・403
├── audit/{domain,service,repository}               14  監査イベントの追記
├── user/{domain,service,repository,web}            55  利用者・パスワード・プリファレンス・初期管理者の作成（service の InitialAdminInitializer）
├── invitation/{domain,repository,service,web}      63  招待・送り直し・取り消し・登録の完了・招待の定期の削除
├── mail/{config,domain,service,template,transport}  28  SMTP の送信と Mustache のテンプレート
├── appearance/{config,service,web}                 14  インスタンスの見た目の設定（GET /api/appearance）
├── targetdb/{config,domain,repository,service}     26  対象DB の接続とスキーマの読み取り
├── dsl/{domain,parse,validate,service}             53  DSL の型・安全な読み込み・検証・適用中のモデル
└── dslmanage/{domain,generate,repository,service,web}   53  既定の DSL の生成・投入・プレビュー・適用・履歴
```

- 層の決まりは `team.md` の Code Style のとおりで、ArchUnit で確かめている（今回は読み直していない）。
- 設定とスキーマ（`backend/src/main/resources/`）: `application.yaml`（独自の設定は `mastersmith.*`。観測は `management.*`。`management.metrics.distribution.slo` に境界のバケット）、`db/migration/`（Flyway、前進のみ、V1〜V8）、メールのテンプレート。

### 今回の Intent に関わるファイル（深く読んだもの）

| 場所 | 役割 | 所見 |
|---|---|---|
| `user/service/InitialAdminInitializer.java` | 起動時の初期管理者の作成と、その INFO・WARN のログ | K-11 |
| `backend/src/test/.../user/service/InitialAdminInitializerTest.java`・`InitialAdminIT.java` | ログの中身と標準出力の確かめ | K-11 |
| `frontend/e2e/100-app-text-contrast.e2e.ts`・`frontend/e2e/support/axe.ts` | コントラストの検査と既知の違反の一覧 | K-13 |
| `vendor/make-you-chic-ui/packages/make-you-chic-ui/src/`（Tabs・Button の CSS、`theme/semantic.css`・`theme/contrast.test.ts`） | `310e1ec..077f5b4` の差分 | K-12 |
| `.github/dependabot.yml`・`gradle/libs.versions.toml`・`frontend/package.json`・`frontend/package-lock.json`・`backend/gradle.lockfile`・`config/npm-build-tools.txt` | 版の定義と固定、更新の知らせの ignore | K-14 |
| `build.gradle.kts`・`backend/build.gradle.kts` | spotless の適用、OSV-Scanner の判定、Jackson の BOM、`packagesJudgedByTotal` | K-10・K-14 |
| `docker/monitoring/provisioning/alerting/mastersmith.yaml`・`docker/monitoring/dashboards/mastersmith-overview.json` | p95 の警報としきい値のパネル・SLI の表 | K-15 |
| `.github/workflows/ci.yml` | `verify` を流す CI（E2E は流さない） | K-13 |
| `README.md` | サブモジュール・E2E・手元の監視・警報と対応の手順・コントラストの既知の制約・初期管理者の起動の確かめの節 | K-11・K-13・K-15・K-16 |

## テストの構成

- `backend/src/test/java/cherry/mastersmith/`（前回 317 ファイル）: 本体と同じパッケージ構成。単体は `*Test`（タスク `test`）、Spring と H2 を起動する結合は `*IT`（タスク `integrationTest`）。ログの確かめの共通の補助は `common/testsupport` の `LogEvents`（ログの出来事を取り込む）と `JsonLogRecords`（標準出力の JSON のログに秘密が無いことを確かめる）。秘密の漏えいの確かめは `*SecretLeakIT` の7つ。前回の後に `common/observability/HistogramBucketsIT.java` が足された。
- 対象DB の結合テストは Testcontainers で、イメージは `targetdb/testsupport/TargetDbImages.java` の digest の定数で固定する（`compose.yaml` と同じ値）。
- 画面は対象と同じ場所の `*.test.ts(x)`（前回 91 ファイル）。
- E2E は `frontend/e2e/`（10 本の `*.e2e.ts`（010〜100）と `support/` の補助 13 ファイル）。050〜080 がブラウザのアクセシビリティの検査、090 が招待から登録までの流れ、100 がアプリの画面の文字のコントラストの検査（前回の後に足された）。既知の違反の一覧は `support/axe.ts`（すべて空）と `100-app-text-contrast.e2e.ts` の `STATE_KNOWN_VIOLATIONS`（2件、K-13）。

件数はファイルを数えた値で、実行の件数ではない。

## 画面の構成（`frontend/src/`、前回テストを除き 142 ファイル）

```
frontend/src
├── main.tsx
├── app/
│   ├── App.tsx・registry/・routing/・navigation/・layout/・i18n/・login-state/
│   ├── display-settings/    利用者とインスタンスの見た目の設定の解決と保存
│   ├── login-handoff/       ログインの後の受け渡し
│   └── pages/               共通の画面（NotFoundPage など）と Page.css
├── shared/{api-client,validation,format}
└── features/{auth,admin,dsl,invitation,registration,preferences}   機能ごとの画面と registration.ts
```

新しい機能は `features/<featureId>/registration.ts` を置くだけで読み込まれる（前回までの記録）。CSS は部品と同じ場所に素の CSS で置く（`team.md` の Code Style）。
