# 依存関係（mastersmith2）

部品の版は `technology-stack.md`、部品ごとの責務は `component-inventory.md` に書き、ここでは依存の向きと管理の決まりを書く。この文書に本文を書いた所見は K-39（2026-10-15、下の「依存の更新の知らせと固定先」）だけ（境界テストの欠けは K-38、`code-quality-assessment.md`）。

> 2026-10-15（コミット `4f6c27d`）の更新: 「依存の更新の知らせと固定先」（K-39）を足し、サブモジュールの固定先を確かめ直した。「内部の依存」の図と表は前回（コミット `d5aea52`）の記録のままで、`261004-role-menu` で加わった `role`・`group`・`navigation` の向きは入っていない（今回は import を検索していない）。

## 外部への依存

### 実行時に接続するもの

| 相手 | 接続の仕方 | 既定 |
|---|---|---|
| 内部DB（H2、組み込み・ファイル） | Spring Boot の `DataSource`（HikariCP） | 有効。単一インスタンス前提。権限（`users.admin_flag`）も要求ごとにここから読む（K-32） |
| 対象DB（MySQL・MariaDB・PostgreSQL のどれか1つ） | `targetdb`、設定は環境変数だけ | 設定したときだけ。読み取りだけ |
| SMTP（手元では Mailpit） | `mail`、接続先と資格情報は環境変数（`.env`）だけ | 接続先の設定が無ければ送らない（`project.md` の Mandated） |
| OTLP の受け手 | 観測の設定 | 無効 |

### ビルド時・検査時に使うもの

- Maven Central（Gradle の依存の唯一の取得元。ビルドのプラグインは Gradle Plugin Portal）、npm のレジストリ。
- Git サブモジュール `vendor/make-you-chic-ui`（固定先 `5bf1ffe`、npm の `file:`）と `vendor/java-mustache-processor`（固定先 `8d44c36`・タグ 0.1.0、Gradle の composite build）。2026-10-15 に `git submodule status` で確かめた。
- Gitleaks・OSV-Scanner、コンテナの実行環境（対象DB の結合テスト）。

## 依存の管理の決まり

- Gradle の依存は lockfile で固定、npm は `npm ci`（`package-lock.json`）。
- 新しい依存は、採用の前にライセンスと、推移依存で既存の部品の版を引き上げないかを確かめる（`team.md` の Code Style、`project.md` の Corrections）。
- make-you-chic-ui に部品（例: 入れ子のナビゲーション。K-36）を足してもらうときは上流のリポジトリで変え、固定先の更新を承認を得た専用のコミットで行い、更新の前後のコミットのハッシュを記録する（`project.md` の Mandated、`team.md` の Way of Working）。
- Dependabot のプルリクエストは GitHub の画面でマージせず、手元でまとめて更新し `./gradlew verify` を通してから `develop` に統合して閉じる（`team.md` の Way of Working）。

## 依存の更新の知らせと固定先（2026-10-15）

### K-39 依存の更新 20 件と、手でそろえる固定先

確かめた事実:

- 開いている Dependabot のプルリクエストは 20 件（開発担当の `gh pr list`。#24・#25・#27・#32・#34〜#49）。内訳は npm 10・Gradle 3（#37 jqwik・#46 wrapper・#47 Spotless）・Actions 4（#25・#40・#43・#44）・docker 1（#38 eclipse-temurin）・docker-compose 2（#35 mailpit・#36 otel-lgtm）で、振り返りの U1 の一覧と一致する。版の一覧は `technology-stack.md` の「更新の候補」。
- `.github/dependabot.yml` の項目は gradle・npm・github-actions・docker・docker-compose の5つ。ignore は Gradle の3つ（`opentelemetry-logback-appender-1.0`・`com.networknt:json-schema-validator`・`tools.jackson:jackson-bom`、どれも全版）、npm の `typescript`（大きな版だけ）、docker の `eclipse-temurin`（大きな版だけ）（50〜93 行）。
- Dependabot が見ない固定先に、今回上げるイメージが次のとおりある（`team.md` の Deployment「上げるときは Dependabot が見ない固定先も同じ版とダイジェストにそろえる」に当たる）。

| イメージ（プルリクエスト） | Dependabot が見る場所 | 手でそろえる固定先 |
|---|---|---|
| eclipse-temurin の jre（#38、`25.0.4_7` → `25.0.4.1_1`） | `Dockerfile` 17 行 | `perf/README.md`（40・239・324・567・824 行）・`README.md`（352・362・735・1135 行） |
| eclipse-temurin の jdk（同じ版、プルリクエストは無い） | 無い | `docker/hikari-pool.sh` 48 行（`25.0.4_7-jdk-noble@sha256:2feab631…`、アーキテクトが確かめた）・`perf/README.md` 76 行・`README.md` 392 行 |
| grafana/otel-lgtm（#36、0.34.0 → 0.36.0） | `compose.yaml` | `perf/README.md` 436 行 |
| axllent/mailpit（#35、v1.31.2 → v1.31.5） | `compose.yaml` | `docker/perf/compose.yaml` 134 行（ダイジェスト付き）・`README.md` 552 行（タグだけ、ダイジェスト無し） |

- Actions はコミットのハッシュで固定し、コメントに版の名前を書く（`.github/workflows/ci.yml`）。更新はハッシュとコメントの両方を替える。Gitleaks 8.30.1・OSV-Scanner 2.6.0 は `ci.yml` の env で版と SHA-256 を固定し、Dependabot の対象外。
- `vite`（#41）・`@vitejs/plugin-react`（#49）は `config/npm-build-tools.txt` の成果物を作る道具で、devDependencies でも High 以上で統合を止める（`team.md` の Deployment）。
- 対象DB のイメージ（`TargetDbImages`・`compose.yaml`・`docker/perf/compose.yaml`）は今回の 20 件に無い。

見立て（未検証）:

- grafana/k6 は `perf/README.md` にしか無く、Dependabot が見るファイルに無いため、更新の知らせが来ない（`dependabot.yml` の「見ない固定先」の一覧に名前はある）。
- eclipse-temurin の `25.0.4.1_1` は版の付け方が変わる（`25.0.4_7` → `25.0.4.1_1`）。同じ版の jdk のタグと複数アーキテクチャの index のダイジェストがあるかは確かめていない。jre だけ上げると、接続プールの運用の道具（`docker/hikari-pool.sh`）と手順書の jdk が古いまま残る。
- 土台のイメージ・Tomcat・Jackson などの実行時の部品を上げた後は、性能の基準の値（P1〜P5）を同じ使い捨ての環境で取り直す前提になる（振り返りの進め方の案）。

## 内部の依存（バックエンドのパッケージ間）

今回（コミット `d5aea52`）、各パッケージの `import cherry.mastersmith.*` を検索して確かめた。図は import の向きだけを描き、出来事による逆向きの知らせ（`user` の出来事を `auth` が受けるなど）は線にしていない。

```mermaid
flowchart TD
  config --> common
  auth -- "user.service・user.domain" --> user
  auth --> common
  access -- "auth.domain・auth.web" --> auth
  access --> common
  access -- "SecurityHeaderProperties" --> config
  user --> common
  invitation -- "user.domain・user.service" --> user
  invitation --> mail
  invitation --> common
  appearance --> common
  audit -- "出来事の型" --> user
  audit -- "出来事の型" --> auth
  audit -- "出来事の型" --> access
  audit -- "出来事の型" --> invitation
  audit -- "出来事の型" --> dslmanage
  audit -- "出来事の型" --> useradmin
  dslmanage --> dsl
  dslmanage --> targetdb
  dslmanage --> user
  dslmanage --> auth
  dslmanage --> common
  useradmin -- "user.domain・user.service" --> user
  useradmin -- "auth.domain・auth.service" --> auth
  useradmin -- "access.domain" --> access
  useradmin --> common
```

<!-- Text fallback: config は common を使う。auth は user（service と domain）と common を使う。access は auth（domain と web）・common・config（SecurityHeaderProperties）を使う。user は common だけを import する。invitation は user・mail・common を使う。appearance は common だけを使う。audit は出来事の型のために user・auth・access・invitation・dslmanage・useradmin を使う。dslmanage は dsl・targetdb・user・auth・common を使う。useradmin は user（domain と service）・auth（domain と service）・access の domain・common を使う。mail・dsl・targetdb・common はアプリの中のほかのパッケージを import しない。循環する依存は無い。 -->

### 今回の論点に関わる依存の要点

| 向き | 中身（今回確かめたもの） | 関わる所見 |
|---|---|---|
| `access` → `auth` | `auth.domain.AuthenticatedUser`（判定の主体）・`TokenAuthenticationException`・`TokenFailureReason`・`ClientInfo`、`auth.web.ClientInfoResolver`・`TokenAuthenticationEntryPoint` | K-32・K-33 |
| `access` → `config` | `access/web/AccessRequestRejectedHandler.java` が `config.SecurityHeaderProperties` を使う | — |
| `auth` → `user` | 認証の入口が `UserAccountService.findById` で利用者（`admin`・停止）を読む | K-32 |
| `useradmin` → `user` | 管理者の印・停止の書き換えの口と `findActiveAdminIds`。書き換えの口を呼べるのは `useradmin.service` だけ（境界テスト） | K-34 |
| `useradmin` → `access` | `access.domain.AccessProblemTypes` だけ（境界テスト） | K-34 |
| `audit` → 各機能 | 出来事の型（`access` の拒否、`useradmin` の印の付け外しほか） | K-38 |
| `dslmanage` → `dsl` | `ActiveDslModelProvider` を使う唯一の外の読み手（`DslPreviewAnalysis`） | K-37 |
| 差し込み口 | `auth`・`access`・`invitation`・`appearance` の `*SecurityContributor` → `common.security.SecurityRuleContributor`。`config.SecurityConfig` がそれらを集めて当てる | K-33 |

`access` の機能の境界（どの機能に依存してよいか・依存されてよいか）を確かめる ArchUnit のテストは無い（K-38）。

### 境界の決まり（今回の論点に関わるもの）

- `user` は `auth` を知らず、`auth` は `user` の service の口と値の型だけを使う（`backend/src/test/java/cherry/mastersmith/auth/AuthBoundaryArchitectureTest.java`、今回は決まりの一覧を読んだ）。
- `useradmin` が使ってよい `access` は `access.domain.AccessProblemTypes` だけで、印・停止・トークン・失敗回数を変える口を呼べるのは `useradmin.service` だけ（`UserAdminBoundaryArchitectureTest.java` 113 行〜・191 行〜）。
- 機能から `audit` への知らせは出来事で行い、機能は `audit` に依存しない。
- 既存の ArchUnit の境界の検査を緩める・変える場合は、コード生成の計画に明記して依頼者の承認を得る。新しい機能は自分の `<機能>BoundaryArchitectureTest` を足し、既存のものは書き換えない（`team.md` の Code Style）。

## 画面の依存

- `main.tsx` が `app/App.tsx` を起動し、`App.tsx` は make-you-chic-ui・見た目の設定・登録・振り分けを組み合わせる。
- 登録は各機能（`admin`・`auth`・`dsl`・`invitation`・`preferences`・`registration`・`useradmin`）の `registration.ts` を `app/registry/registrationModules.ts` が読み込む。骨組み（`app/navigation`・`app/routing`・`app/layout`・`app/admin-forbidden`）は登録の型（`app/registry/types.ts`）とログイン状態にだけ依存し、個々の機能を import しない（今回読んだファイルで確かめた）。
- 機能は `shared/api-client` と make-you-chic-ui の部品を使う。`app/admin-forbidden/AdminForbiddenProvider.tsx` は `shared/api-client/adminForbidden.ts`（判定）と `apiClient.ts`（`refreshSessionOnce`）を使う（K-35）。
- `ShellLayout.tsx` は make-you-chic-ui の `AppShell` に `navItems` を渡す。サイドバーの描画は make-you-chic-ui の中の `Sidebar` で、骨組みからは差し替えられない（K-36）。

## ビルドのタスクの依存

- `verify` の中に画面の検査・バックエンドの単体と結合のテスト・カバレッジ・SpotBugs・Gitleaks・OSV-Scanner がある。`e2eTest` は `verify` の外。
- `frontend` → `make-you-chic-ui`（サブモジュールのパッケージを `file:` で読む）。`backend` → `java-mustache-processor`（composite build）。`:backend:bootWar` → 画面のビルド（WAR に `dist` を同梱）。
