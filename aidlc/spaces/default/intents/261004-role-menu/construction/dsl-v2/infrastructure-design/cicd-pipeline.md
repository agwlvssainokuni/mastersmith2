# CI/CD Pipeline — U2 DSL の書式の版 2（dsl-v2）

U2 の検査の流れ（CI と1コマンドの検査）、ビルドの変更、負荷の道具、E2E、戻し方、B1 で確かめることを示します。U2 は library の単位（画面 `features/dsl` の小さな変更を含む、B1 で作る）で、DSL の書式を版 2（スキーマの階層・メニューの組）に替え、上限つきの安全な YAML の読み込みの口（`SafeYamlReader`）とメニューの深さの上限を足します（契約 C3）。新しい依存・API・内部DB の表・設定・秘密は足さず、CI のワークフローとコンテナの設定も **変えません**。変わる基盤は、JSON Schema のファイル名を名指しするビルドの箇所（3節）と、負荷の道具の版 2 への書き換えと同時の投入の口（5節、この段の Q1: A）だけです。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤・IaC・警報の通知先は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。

## 出典

- 答え: `infrastructure-design-questions.md`（Q1: A、まとめの確認は Looks correct）
- 上流: `construction/dsl-v2/nfr-design/security-design.md`（3節の試し T1〜T4、4.1〜4.9、6節の承認の場の直し R-01）・`logical-components.md`（2〜8節）・`traceability.json`、`construction/dsl-v2/nfr-requirements/security-requirements.md`・`tech-stack-decisions.md`、`construction/dsl-v2/functional-design/functional-spec.md`（BR1.7、8節の書き換えの一覧）、`inception/domain-design/components.md`（DslDefinition・DslManagement・DslAdminUi）、`inception/contract-design/contract-summary.md`（C3）、`inception/delivery-planning/bolt-plan.md`（B1）（どれも `aidlc/spaces/default/intents/261004-role-menu/` の下）
- library の単位のため、NFR 設計の段は `performance-design.md`・`scalability-design.md`・`reliability-design.md`・`observability-design.md` を作っていない。性能と規模は `logical-components.md` 4節、信頼性（500 にしない・起動を止めない）とログは `security-design.md` 4.4・4.8 が扱い、この文書はその確かめの置き場を対応づける。
- 既にある仕組み（正とする、読むだけ）: `.github/workflows/ci.yml`・`build.gradle.kts`・`backend/build.gradle.kts`・`compose.yaml`・`docker/perf/compose.yaml`・`Dockerfile`・`perf/README.md`・`perf/dsl-timing.sh`・`perf/k6/scenarios.js`・`frontend/e2e/`・README

## 1. CI（GitHub Actions）

`.github/workflows/ci.yml` は変えません。

| 項目 | 今の形 | U2 での扱い |
|---|---|---|
| きっかけ | `develop` へのプッシュ・`v*` のタグ・手動 | 変えない。CI は統合の後の再確認（`team.md` の Way of Working） |
| 実行 | `./gradlew verify`（`ubuntu-latest`、制限時間 60 分） | 変えない。対象DB の3種類のコンテナを使う結合テストも今までどおり毎回 |
| サブモジュール | `submodules: true` で固定先のコミットを取得 | 変えない。U2 は固定先を更新しない |
| 依存の入れ方 | lockfile どおり（`npm ci`、Gradle の lockfile） | 変えない。U2 は lockfile を変えない |
| 道具 | Gitleaks・OSV-Scanner の版と SHA-256 を固定して入れる | 変えない |
| 秘密 | CI は秘密を使わない | 変えない（9節） |
| 成果物 | WAR をコミットのハッシュの名前で保存 | 変えない。WAR の中の JSON Schema が `dsl-schema-v2.json` に替わる（3節） |

CI が失敗したときは、次の Bolt に進む前に `team.md` の Testing Posture「不安定なテストと CI の失敗」の決まりで扱います。

## 2. 1コマンドの検査（`./gradlew verify`）の段と関門

段・関門は増やしません。設定の変更は 3節の JSON Schema のファイル名だけです。どれか1つでも失敗したら全体を失敗とします（既存のとおり）。

| 段 | U2 で確かめること | 関門（失敗の条件） | 当たる要件 |
|---|---|---|---|
| 0 準備 | 変わらない（サブモジュールを変えていないことの確認を含む） | 既存のとおり | — |
| 1 フォーマット | 変える・足す Java（Spotless、palantir-java-format）と画面の TypeScript（Prettier） | 書式の違い | — |
| 2 リンタ | 画面 `features/dsl` の変更が oxlint・ESLint の対象になる。除外を足さない | 1件でも error | NFR4.1 |
| 3 ライセンスヘッダー | 足すファイル（`dsl-schema-v2.json` を除く Java・TypeScript）の先頭の Apache License 2.0 のヘッダー | ヘッダーが無い・形が違う | — |
| 4 ビルド | 版 2 のモデル（`DslModel`・`DslSchema`・`DslMenuItem`）と `SafeYamlReader` の口のコンパイル、`tsc --noEmit`、Vite のビルド。`processResources` が `dsl-schema-v2.json` を `static/dsl/` へ複写する（3節） | コンパイル・型の誤り | NFR1.9・NFR1.14 |
| 5 単体テスト | `dsl.parse`・`dsl.service`・`dsl.validate`・`dsl.domain` の単体テスト（版 2 の大きさ・深さ・別名・タグ・重複キー・`$ref`、スキーマ名とテーブル名の重複キー、メニューの深さ 5 と 6 の境界、別名の爆発の 5 秒を DSL の上限と小さな上限で1件ずつ、深さの数え方と枝の落とし方の jqwik）、変えない `DslBoundaryArchitectureTest`、画面 `features/dsl` のテストと vitest-axe | 1件でも失敗 | NFR1.5・NFR1.7・NFR1.8・NFR1.12・NFR1.14・NFR4.1・NFR4.3・NFR6.1・NFR6.5・NFR6.6 |
| 6 結合テスト | DSL の既存の結合テストを版 2 に書き換えたもの（投入・プレビュー・適用・履歴・照合・`DslConcurrencyIT`・`DslSchemaPublicationIT`）、深さの境界（投入と復元）、起動時の読み直し（深さだけ外して WARN、ほかの誤りは Absent）、読めないプレビューの 422、TRACE を有効にした入力と結果の漏えいのテスト。対象DB の3種類は今までどおり毎回 | 1件でも失敗 | NFR1.9〜NFR1.13・NFR3.3・NFR5.3〜NFR5.5・NFR6.1・NFR6.5 |
| 7 カバレッジ | 4節のとおり | 全体またはパッケージごとの行 80%・分岐 70% を下回る。除外は足さない | NFR6.4 |
| 8 安全の検査 | SpotBugs ＋ FindSecBugs（priority 1 と `SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION`）、OSV-Scanner、Gitleaks を除外を足さずに通す | 既存の基準 | NFR1.13 |
| 9 成果物 | `bootWar` と `verifyDslSchemaInWar`（WAR の中の `WEB-INF/classes/dsl/dsl-schema-v2.json` と `WEB-INF/classes/static/dsl/dsl-schema-v2.json` が正本と同じ）、初回の読み込みの量 | WAR が作れない・JSON Schema が無いか正本と違う・量の上限を超える | NFR1.9 |

補足:

- jqwik・fast-check の失敗のときの乱数の種は、既存の仕組みのまま CI の記録に残ります（Gradle のテストの出力は失敗の詳細をすべて出す設定 `exceptionFormat = FULL`）（NFR6.6）。
- 新しい指標・警報・ダッシュボードは足しません。起動時の WARN（深すぎる枝を落としたとき）は既存の構造化ログの仕組みで出るだけで、警報の決まり（`docker/monitoring/`）は変えません（NFR5.3、`security-design.md` 4.8）。

## 3. JSON Schema の v2 への替え（ビルドの変更と影響の一覧）

版 2 の書式 `dsl-schema-v2.json` を同梱し、ビルドで画面の静的なファイルの置き場へ写して `/dsl/dsl-schema-v2.json` でログインなしに配ります。`dsl-schema-v1.json` は置きません（機能設計 BR1.7、`security-design.md` 4.3）。仕組み（正本は `backend/src/main/resources/dsl/` の1つ、`processResources` で `static/dsl/` へ複写、WAR の中の2か所を正本と照合）は変えず、ファイル名を名指しする箇所だけを B1 でそろえて直します。

| 箇所 | 今の参照 | 直す中身 | 落ちる所 |
|---|---|---|---|
| `backend/build.gradle.kts` の `dslSchemaSource` | `src/main/resources/dsl/dsl-schema-v1.json` | `dsl-schema-v2.json` に替える。上の説明のコメントも替える | 段 4（ファイルが無ければ複写が失敗）・段 9 |
| `backend/build.gradle.kts` の `verifyDslSchemaInWar` | WAR の中の `dsl/dsl-schema-v1.json`・`static/dsl/dsl-schema-v1.json` | v2 の2か所に替える | 段 9 |
| `backend/src/main/java/cherry/mastersmith/dsl/domain/DslFormat.java` | `SCHEMA_RESOURCE`・`SCHEMA_PUBLIC_PATH` が v1 | v2 に替える | 段 5・段 6 |
| `backend/src/test/java/cherry/mastersmith/dsl/DslSchemaPublicationIT.java` | `static/dsl/dsl-schema-v1.json` | v2 に替える | 段 6 |
| `frontend/src/features/dsl/submitInput.ts`・`DslSubmitForm.test.tsx` | `/dsl/dsl-schema-v1.json` | v2 に替える | 段 5 |
| README（DSL の書式の節・DSL の画面の節） | v1 の正本・URL・`yaml-language-server` の例 | v2 に替え、v1 の URL は無くなること（エディターの補完の設定を v2 に替えること）を書く | コード生成のレビュー |

- 公開する書式のファイルに秘密を含めず、JSON Schema の部品は外部の `$ref` を取りに行かない設定（`fetchRemoteResources(false)`）のまま使います（NFR1.9）。
- 既存の `SecurityConfig` は `/api/**` の外をログインなしで通すため、公開のための安全の決まりは足しません（前の Intent の学び）。
- 前の版のイメージへ戻すと `/dsl/dsl-schema-v1.json` が配られ、v2 は配られなくなります。どちらもそのイメージが受け付ける書式と一致するため、戻しの手順で直すものはありません。

## 4. カバレッジとテストの JVM（段 5〜7）

| 対象 | 今の扱い | U2 での扱い |
|---|---|---|
| `packagesJudgedByTotal`（`backend/build.gradle.kts`、7 パッケージ） | 全体の合計で判定する既存のパッケージの一覧 | 変えない。U2 が手を入れる `dsl.*`・`dslmanage.*` のパッケージは一覧に無く、すでにパッケージごとの下限（行 80%・分岐 70%）の対象 |
| `dsl.service` に足す口・上限の型・結果の型 | 既存のパッケージ | パッケージごとの下限を満たす。上限の型の範囲の確かめ（外れたら例外）の分かれ道も単体テストで通す |
| 画面（`frontend/vitest.config.ts` の `thresholds`） | 全体の合計で行 80%・分岐 70% | `features/dsl` の変更を計測から外さない |
| 計測の除外 | 起動クラス・設定値だけのクラス・自動生成コード・`vendor/` | 増やさない |
| テストの JVM のヒープ（`maxHeapSize = "1g"`） | 構造の検査と 10MB を超える DSL のテストのために 1g | 変えない。別名の爆発のテストのヒープの増えは約 130 MB（試しの T2）で足りる見込み。足りなければ計画に無い変更として依頼者に確かめる |

実測は `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で行い、手を入れたパッケージの値を記録します（`team.md` の Testing Posture、`project.md` の学び）。

## 5. 負荷の道具の書き換えと同時の投入の口（Q1: A）

### 5.1 版 2 への書き換え（B1 が持つ）

版 1 の DSL を入力に持つ負荷の道具を、B1 のコード生成で版 2 に書き換えます。Build and Test と Performance Validation は、書き換えた道具を流すだけにします（`logical-components.md` 6節、読み直しの R-04）。

| 道具 | 書き換える中身 |
|---|---|
| `perf/dsl-timing.sh` | 投入・プレビュー・適用・`--storage` などで使う DSL を版 2 にする |
| `perf/make-large-dsl.mjs` | 生成した版 2 の DSL のテーブルを、スキーマの階層の中で写して 10 MiB 近くにする（誤りを含むものも版 2 で作る） |
| `perf/make-pattern-dsl.mjs` | `--pattern` の DSL を版 2 にする |
| `perf/ui/*` | 画面の時間・言語の確かめで貼り付ける DSL を版 2 にする |
| `perf/k6/scenarios.js` | `dslLight`・`dslCycle`・`dslMixed` の DSL の入力を版 2 にする |
| `perf/README.md` | 版 2 であることと、5.2 のオプションの使い方を書く |

### 5.2 同時の投入の口（`perf/dsl-timing.sh` に足す）

承認済みの設計（`logical-components.md` 4節、`security-design.md` 4.9）の「本番と同じヒープで、2つの投入を同時に送ったとき、片方が 503 `DSL_BUSY` になり、もう片方が通る」を、手順が残る形で確かめるため、B1 で `perf/dsl-timing.sh` にオプションを1つ足します（名前は Code Generation で決める。例 `--busy`）。

1. 使い捨ての環境（`docker/perf/compose.yaml`、アプリの `mem_limit` の既定 2g・イメージの `MaxRAMPercentage=50.0` で本番と同じヒープ）に、上限ちょうどに近い 10 MiB の版 2 の DSL（`perf/make-large-dsl.mjs` で作る）を用意する。
2. 同じ管理者で、2つの投入をほぼ同時に送る（2つの要求を並べて送り、両方の終わりを待つ）。
3. 2つの状態コード・応答の `code`・それぞれの時間を記録する。
4. 判定: 成功（2xx）1つと 503 `DSL_BUSY` 1つの組なら合格。両方が成功したときは、先の投入が終わってから後の投入が届いた（重ならなかった）回として数えず、決めた回数（Code Generation で決める）の内でやり直す。どちらも 503、または 5xx（`DSL_BUSY` 以外）・接続の失敗があれば不合格。
5. 終わった後に、コンテナの `memory.peak`（cgroup）と OOMKilled の有無・終了の状態を記録する（既存の `memory-peak.txt`・`state.txt` と同じ取り方）。
6. 結果は既存と同じ `build/perf-results/dsl-<日時>/` の下に、組ごとの1行の表として置く。応答とログに秘密情報は含まれない（仮の管理者のメールアドレスは `example.test` の見本の値）。

- 排他（`DslHeavyOperationGate`）の振る舞いそのものは、待ち合わせで重なりを確実に作る `DslConcurrencyIT`（段 6）が毎回確かめます。この口は、本番と同じヒープのコンテナで重なったときにアプリが止まらないことを、Build and Test で1回確かめるためのものです。
- 道具は `verify` と CI に入れません（今までの負荷の道具と同じ）。

## 6. Build and Test・Performance Validation で測るもの

| 確かめ | 道具 | 持ち主の段 | 判定 | 要件 |
|---|---|---|---|---|
| 10 MiB の版 2 の投入とプレビューの表示（照合を含む）の時間・`memory.peak`・OOMKilled | `perf/dsl-timing.sh`（PostgreSQL の1種類） | Build and Test | 10 秒以内、OutOfMemoryError・OOMKilled が無い、`memory.peak` が `mem_limit` を超えない | NFR2.5 |
| 2つの投入の同時の送信 | `perf/dsl-timing.sh` の 5.2 のオプション | Build and Test | 成功1つと 503 `DSL_BUSY` 1つ、OOMKilled が無い | NFR2.5・NFR2.6 |
| 想定の規模の既定の DSL（版 2）の大きさ | `perf/dsl-timing.sh`（生成の結果の大きさ） | Build and Test | 上限 10 MiB の内（試算 約 6.3 MB）。超えたら上限を上げるかを依頼者に諮る | NFR2.7 |
| 英語の文言 | `perf/dsl-timing.sh --lang` | Build and Test | 応答と画面に日本語の文字が無い | NFR2.8 |
| 95 パーセンタイルと同時の実行 | k6 の `dslLight`・`dslCycle`・`dslMixed`（版 2 の DSL） | Performance Validation | 既存の閾値（`perf/README.md`） | NFR2.6 |

- 使い捨ての環境は、配備したアプリと別のもの（仮の署名鍵・仮の利用者、終わったら消す）で、本物のデータと監査ログを汚しません（`project.md` の学び）。長い試験は台本全体を `caffeinate -i` で包みます。
- この PC の colima の VM は CPU 4・メモリ 6GiB（読み取りだけで確かめた）。使い捨ての環境（アプリ 2g・PostgreSQL 512m）と配備したアプリ（2g）を同時に動かしても VM の内に収まる見込みですが、止めるかどうかは Build and Test の手順で決めて記録します。

## 7. E2E（`./gradlew e2eTest`、verify と CI の外）

- E2E は意図して `verify` と CI の外に置いています（`build.gradle.kts` の `e2eTest`、`team.md` の Testing Posture）。代わりの実行の場は、統合の前とリリースの前の手元です。
- B1 は DSL の画面に手を入れるため、統合の前に手元で `./gradlew e2eTest` を流します。`frontend/e2e/040-dsl-admin.e2e.ts` を版 2 の DSL に書き換え（B1 が持つ。本数に数えない）、ほかの既存の E2E と一緒にすべて通ることを確かめます。
- U2 のために E2E のファイルは足しません（NFR6.7）。

## 8. 統合と配備の流れ

| 項目 | 扱い | 出典 |
|---|---|---|
| 作業ブランチ | `develop` から作る短命のブランチ（例 `feature/261004-role-menu-b1`）。worktree は使わない | `team.md` の Way of Working |
| 統合の前の関門 | `./gradlew verify`（コンテナの実行環境あり、対象DB のテストを飛ばさない）と E2E（7節） | `team.md` の Way of Working・Testing Posture |
| 統合の形 | `develop` への squash（1 Bolt が1コミット）。サブモジュールの更新は無いため fast-forward の例外には当たらない | `team.md` の Way of Working |
| プッシュ | 依頼者自身が行う。AI はプッシュしない | `team.md` の Way of Working |
| 配備 | 既存の手順（`Dockerfile`・`compose.yaml`・README）のまま。イメージの作り方・`.env`・ボリューム・JVM の設定を変えない | 1節 |
| 配備の段への引き継ぎ | 版を上げる入れ替え（版 1 の適用中の DSL は Absent になる、入れ替えの後に版 2 の既定の DSL を生成・適用、版 1 のプレビューは先に破棄）と、前の版への戻し（戻す前に版 2 のプレビューを破棄、戻した後に版 1 を生成し直して適用、戻しの練習で3点を確かめる）。中身は `logical-components.md` 7節のとおりで、deployment-pipeline・deployment-execution の段の手順に入れる。監査に残る操作は、行う前に依頼者に伝える | `logical-components.md` 7節 |

## 9. 戻し方と秘密

| 項目 | 扱い |
|---|---|
| DB スキーマ | 変えない（表・Flyway の移行を足さない）。適用中の DSL の本文は内部DB の既存の表に入ったまま |
| 前の版への戻し | 直前の版のイメージで起動し直す（イメージだけ）。手順は 8節の配備の段への引き継ぎのとおり |
| 設定（`.env`・`application.yaml`） | 変えない。戻すものは無い |
| 秘密 | 足さない。CI に秘密を渡さない。対象DB の接続情報は今までどおり `.env` だけから受け取り、既定の DSL の生成は写しのスキーマ名だけを DSL に書く（NFR1.13）。Gitleaks の除外を足さない |
| 依存 | 新しい依存を足さず、`gradle/libs.versions.toml`・`backend/gradle.lockfile`・`frontend/package.json`・`frontend/package-lock.json` を変えない（SnakeYAML 2.7・networknt 3.0.6・jqwik 1.10.1・fast-check 4.10.2 などの今の版のまま） |

## 10. DevSecOps と Compliance の視点

| 観点 | 確かめた結果 |
|---|---|
| 信頼できない入力 | DSL の上限（10 MiB・深さ 50・別名 100・展開後の節 1,000,000）とタグ・重複キー・外部の `$ref` の拒否を版 2 で段 5・6 に置く（`team.md` の DSL の必須のテスト、`project.md` の Mandated）。上限は `LoaderOptions` と `LimitingParser` に同じ値で渡す |
| 個人に関する値・秘密 | 読み込みの口の入力は `byte[]`、結果の文字列は区分・位置・節の数だけ。TRACE を有効にした結合テスト（段 6）で、入力と結果の目印の文字列がログに出ないことを確かめる（NFR1.11） |
| 接続情報 | 生成・照合の警告に接続先・ユーザー名・パスワード・JDBC の URL を含めない。漏えいのテストの「接続の項目を含まない」の確かめは残す（NFR1.13、`project.md` の Forbidden） |
| 境界 | `DslBoundaryArchitectureTest` を変えずに通す（段 5）。後の単位（U4 role・U5 navigation）は `dsl.service` の口と `dsl.domain` の型だけを使う（NFR1.14） |
| 静的解析・依存の脆弱性 | 既存の段 8 の関門を除外なしで通す。新しい依存が無いため、OSV-Scanner の対象も変わらない |
| 公開のリポジトリ | テストと負荷の道具のデータは予約のドメインと見本の名前だけで、個人に関する値・秘密を置かない |

## 11. B1 で確かめること

| 確かめ | 方法 | 成り立たないとき |
|---|---|---|
| (i) `dsl-schema-v1.json` の参照が残っていない（ビルド・コード・テスト・画面・README） | 段 4・段 9 とコード生成のレビュー（名前の検索） | 参照を直す |
| (ii) DSL の既存のテスト（上限・タグ・重複キー・`$ref`）が版 2 の形で通り、テストの JVM のヒープ 1g で足りる | 段 5・段 6 | テストを直す。ヒープが足りなければ依頼者に確かめる |
| (iii) カバレッジの実測の値（手を入れた `dsl`・`dslmanage` のパッケージと画面の全体） | 4節の実測 | 除外を増やさずテストを足す |
| (iv) 負荷の道具の版 2 への書き換えと 5.2 の口が動く | 使い捨ての環境で短く流す（目標の判定は Build and Test） | 道具を直す |
| (v) E2E の結果（040 を含む全体） | 7節 | 統合しない。原因を直す |
| (vi) 依存・lockfile・警報の決まり・`packagesJudgedByTotal`・計測の除外・CI・コンテナの設定が変わっていない | コード生成のレビュー | 元に戻す |

## 12. 上流との差

1. 承認済みの設計（`logical-components.md` 4節）は、本番と同じヒープで2つの投入を同時に送る確かめの「持ち主は Build and Test」とだけ書き、道具は決めていなかった。この段の Q1: A で、B1 が `perf/dsl-timing.sh` に同時の投入の口を足し、Build and Test はそれを流す形にした（5.2）。道具の書き換えの持ち主を B1 とした決定（読み直しの R-04）の範囲を、口の追加にも広げたものです。
2. そのほかに、上流（要件・機能設計・契約 C3・NFR 要件・NFR 設計）と違う作りはありません。JSON Schema の v2 への替え（3節）は機能設計 BR1.7 と `security-design.md` 4.3 のとおりで、名指しする箇所の一覧をコードで確かめて並べたものです。
