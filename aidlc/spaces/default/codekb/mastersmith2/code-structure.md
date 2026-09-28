# コードの構成（mastersmith2）

各部品の責務と状態は `component-inventory.md`、部品の間の依存は `dependencies.md` に書き、ここでは置き場と決まりだけを書く。ファイルの数は `git ls-files` で数えた値である。

## リポジトリの最上位

| パス | 分類 | 内容 |
|---|---|---|
| `backend/` | バックエンド | Gradle のサブプロジェクト。Java のソース・テスト・設定・Flyway のスキーマ変更・SpotBugs の除外 |
| `frontend/` | 画面 | npm のプロジェクト（React＋TypeScript、Vite）。ルートの Gradle から呼ぶ。E2E は `frontend/e2e/` |
| `vendor/make-you-chic-ui/` | デザインシステム | Git サブモジュール（npm の `file:` の依存）。固定先 `735ef04`。このリポジトリからは変えない |
| `vendor/java-mustache-processor/` | Mustache のエンジン | Git サブモジュール（Gradle の composite build）。固定先 `8d44c36`（`0.1.0`）。このリポジトリからは変えない |
| `gradle/`・`settings.gradle.kts`・`build.gradle.kts` | ビルド | 版の一覧（`gradle/libs.versions.toml`）、Wrapper（9.7.1）、1コマンドの検査 `verify` |
| `config/` | 検査の設定 | ライセンスヘッダーのひな形、OSV の判定で止める画面の道具の一覧（`npm-build-tools.txt`） |
| `.github/` | CI と更新の通知 | `workflows/ci.yml`・`dependabot.yml` |
| `Dockerfile`・`compose.yaml`・`.env.example` | コンテナ | アプリのイメージとサービス、profile で起動する監視・メールの受け手・見本の対象DB |
| `docker/` | 付属の環境 | 手元の監視（`monitoring/` の警報とダッシュボード）、OTLP の受け手（`otel-collector/`）、見本の対象DB、プールの道具（`jmx/`・`hikari-pool.sh`） |
| `perf/` | 負荷の試験 | k6 の台本と使い捨ての環境（流し読み） |
| `README.md` | 文書 | 起動・環境変数・監視・監査・招待・既知の制約・戻し方（約 950 行） |
| `aidlc/`・`.claude/` | ワークフローの記録と枠組み | アプリのソースではない。運用の手順書（`runbooks.md` など）はここの Intent の記録にだけある（K-9） |

## バックエンド（`backend/src/main/java/cherry/mastersmith/`、421 ファイル）

```
cherry.mastersmith
├── MastersmithApplication
├── config                                          6   Security の連鎖の組み立て・画面の配信・観測
├── common/{error,health,i18n,observability,security,web}   38
├── auth/{domain,service,repository,web}            50  ログイン・トークン・ロック・ログアウト・期限切れのトークンの定期の削除
├── access/{domain,service,web}                     20  管理者のみの API の認可と 401・403
├── audit/{domain,service,repository}               14  監査イベントの追記
├── user/{domain,service,repository,web}            55  利用者・パスワード・プリファレンス（web は MeController）
├── invitation/{domain,repository,service,web}      63  招待・送り直し・取り消し・登録の完了・招待の定期の削除
├── mail/{config,domain,service,template,transport}  28  SMTP の送信と Mustache のテンプレート
├── appearance/{config,service,web}                 14  インスタンスの見た目の設定（GET /api/appearance）
├── targetdb/{config,domain,repository,service}     26  対象DB の接続とスキーマの読み取り
├── dsl/{domain,parse,validate,service}             53  DSL の型・安全な読み込み・検証・適用中のモデル
└── dslmanage/{domain,generate,repository,service,web}   53  既定の DSL の生成・投入・プレビュー・適用・履歴
```

- 前回のコード知識ベース（記録のコミット `c438dc0`）の後に `invitation`・`mail`・`appearance`・`user/web` が増えた（`c438dc0..HEAD` は 49 コミット）。今回これらは一覧と検索までで、深く読んだのは次の表のファイルだけである。
- 層の決まりは `team.md` の Code Style のとおりで、ArchUnit で確かめている（今回は読み直していない）。
- 設定とスキーマ（`backend/src/main/resources/`）: `application.yaml`（独自の設定は `mastersmith.*`。観測は `management.*`）、`db/migration/`（Flyway、前進のみ、V1〜V8。V7 は利用者のプリファレンスと監査の対象の列、V8 は招待の表）、メールのテンプレート。

### 今回の Intent に関わるファイル（深く読んだもの）

| 場所 | 役割 | 所見 |
|---|---|---|
| `backend/src/main/resources/application.yaml` | `management` の節（公開は health だけ、OTLP の指標の送信は 60 秒ごと・既定で無効）。分布の設定は無い | K-6 |
| `mail/service/SmtpMailSender.java` | 送信と `Observation` `mastersmith.mail.send` | K-6 |
| `dslmanage/service/DslOperationMetrics.java` | `publishPercentileHistogram()` を付ける唯一の指標 | K-6 |
| `invitation/service/RegistrationService.java` | 登録の完了（`complete`）。形の誤ったトークンでも拒否の出来事を出す | K-8 |
| `backend/build.gradle.kts` | テストの JVM・`integrationTest`・カバレッジの検証と `packagesJudgedByTotal` | K-4・K-10 |
| `build.gradle.kts` | サブモジュールの準備と変更なしの確かめ、OSV-Scanner の対象、`verify` の段 | K-1・K-7 |

## テストの構成

- `backend/src/test/java/cherry/mastersmith/`（317 ファイル）: 本体と同じパッケージ構成。単体は `*Test`（151 ファイル、タスク `test`）、Spring と H2 を起動する結合は `*IT`（108 ファイル、タスク `integrationTest`）。対象DB の結合テストは Testcontainers で、イメージは `targetdb/testsupport/TargetDbImages.java` の digest の定数で固定する（`compose.yaml` と同じ値）。
- `config/H2CompactionByPoolSuspensionIT.java`: プールの一時停止による詰め直しの結合テスト（K-4、`architecture.md` の Interaction Diagrams 3）。
- 画面は対象と同じ場所の `*.test.ts(x)`（91 ファイル）。Vitest は `testTimeout` を置かず、既定の 5 秒で動く（`frontend/vitest.config.ts`・`vitest.setup.ts`）。
- E2E は `frontend/e2e/`（9 本の `*.e2e.ts` と `support/` の補助 13 ファイル）。050〜080 がブラウザのアクセシビリティの検査、090 が招待から登録までの流れ。既知の違反の一覧は `support/axe.ts`（K-2）。

件数はファイルを数えた値で、実行の件数ではない。

## 画面の構成（`frontend/src/`、テストを除き 142 ファイル）

```
frontend/src
├── main.tsx
├── app/
│   ├── App.tsx・registry/・routing/・navigation/・layout/・i18n/・login-state/
│   ├── display-settings/    利用者とインスタンスの見た目の設定の解決と保存（流し読み）
│   ├── login-handoff/       ログインの後の受け渡し（流し読み）
│   └── pages/               共通の画面（NotFoundPage など）と Page.css
├── shared/{api-client,validation,format}
└── features/{auth,admin,dsl,invitation,registration,preferences}   機能ごとの画面と registration.ts
```

新しい機能は `features/<featureId>/registration.ts` を置くだけで読み込まれる（前回の記録）。CSS は部品と同じ場所に素の CSS で置く（`team.md` の Code Style）。
