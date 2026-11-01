# 要件（261101-maintenance-update）

依頼: メンテナンスの Intent。依存ライブラリの最新化、Java を 25 系（LTS）の最新に、Node を 26 系（LTS）の最新に、make-you-chic-ui と java-mustache-processor の最新化、GitHub Actions の最新化、古いイメージの削除。加えて、Gradle の wrapper、Spring Boot と連動する固定の見直し、コンテナのイメージ（対象DB・Mailpit・lgtm・otel-collector・k6）、検査の道具（Gitleaks・OSV-Scanner・SpotBugs と FindSecBugs・palantir-java-format・Playwright とブラウザ・axe-core）、Dependabot の ignore の見直し、前の Intent 261029-reference-lookup-cleanup からの小さな直し（P1・T1〜T3・A2・A3）。 [desc]
追加の依頼: README.md と perf/README.md に過去の経緯が残って重くなっているので最新化する（依頼者が Reverse Engineering の段で追加）。
範囲: bugfix（深さ Minimal、Test Strategy Minimal） [scope]

## Intent の分析

機能は足さず、依存・実行環境・道具・文書を今の最新に追いつかせるメンテナンスである [desc]。目的は次の4つ。

- **目的1（実行環境と依存の最新化）**: Java 25 系・Node 26 系・Spring Boot・npm と Gradle の依存・サブモジュール・GitHub Actions・コンテナのイメージ・検査の道具を、Code Generation の時点の最新の安定版にそろえる。大きな版も含め、崩れて直すのが大きいものだけを理由つきで見送る [Q1][Q2]。
- **目的2（固定の見直し）**: Spring Boot と連動して置いた固定と `dependabot.yml` の `ignore` を、外せるかを1つずつ試して減らす [Q1][Q2]。
- **目的3（文書の最新化）**: README.md と perf/README.md を、今の手順と設定だけを読める形にする [Q3]。
- **目的4（小さな直しと片付け）**: 前の Intent から持ち越した P1・T1〜T3・A2・A3 を片付け、配備の後に古いイメージを消す [Q4][Q5][Q6]。

利用者から見える画面と API の形・DB スキーマは変えない。ただし実行環境（JRE・Spring Boot）が変わるため、通常どおり配備し、負荷の試験で変化を確かめる [Q7]。N2（`users.admin_flag` を縮める段）は依頼者の決定（2026-11-01）で行わない。

## 機能の要件

### FR1 Java 25 系の最新化（部品 `build-and-verify`・`container-runtime`）

- **FR1.1** Java を 25 系の最新のパッチにする。toolchain（`gradle/libs.versions.toml` の `java`）・CI の `java-version` をそろえる [desc]。
- **FR1.2** Dockerfile の JRE のイメージ、`perf/versions.env` と `docker/hikari-pool.sh` の JDK のイメージを同じ版にし、版のタグと複数アーキテクチャの index のダイジェストで固定する（K-82、`team.md` の Deployment）。
- **FR1.3** README・perf/README の Java の版の記述をそろえる。

### FR2 Node 26 系の最新化（部品 `build-and-verify`）

- **FR2.1** Node を 26 系（LTS）の最新にする [desc]。`frontend/package.json` の `engines`（今は `>=24 <25`）、`build.gradle.kts` の版の確かめ（今は `startsWith("v24.")`）、CI の `node-version` を含む、Node の版を書いた 6 ファイル・10 か所をすべてそろえる（K-81）。
- **FR2.2** `test` の `NODE_OPTIONS=--no-experimental-webstorage` が Node 26 で使えるかを確かめ、使えなければ同じ目的の設定に直す（K-81）。

### FR3 Spring Boot と連動する固定（部品 `build-and-verify`）

- **FR3.1** Spring Boot（今は 4.1.2）を最新の安定版（小さな版の更新を含む）にする [Q1]。
- **FR3.2** 連動する固定 `opentelemetry-logback-appender` 2.28.1-alpha・networknt 3.0.6・H2 の `-Dh2.compactThreads=1`（3か所）を、1つずつ外して `./gradlew verify` と E2E が通るかを試す。通れば外し、通らなければ理由を `project.md` の学びの言い方に合わせて記録して残す。`dependabot.yml` の `ignore` の該当の行も同じに直す [Q1]。
- **FR3.3** Jackson の BOM の上書きは既に無い（K-85。今の 3.1.7 は Spring Boot の管理の版）ため、この Intent の対象から外す。Spring Boot を上げて Jackson の版が変わるときは、OSV-Scanner で High 以上が出ないことで確かめる。

### FR4 npm・Gradle の依存と検査の道具（部品 `build-and-verify`）

- **FR4.1** frontend の npm の依存と backend の Gradle の依存を、大きな版も含めて最新の安定版にする。lockfile（`frontend/package-lock.json`・`backend/gradle.lockfile`・`settings-gradle.lockfile`）を更新する [Q2]。
- **FR4.2** `./gradlew verify` または E2E が通らず、直すのが大きい更新だけを見送り、`.github/dependabot.yml` の `ignore` に理由と外す時期を書く [Q2]。
- **FR4.3** `dependabot.yml` の `ignore` を見直す。`typescript` の大きな版の見送りは外して試す。`eclipse-temurin` の大きな版の見送りは Java を 25 系に留めるため残す。collector の `0.163.2-386` の見送りは、そのときのタグに合わせて直す（K-87）[Q2]。
- **FR4.4** Gradle の wrapper（今は 9.8.2）を最新にする [desc]。
- **FR4.5** 検査の道具を最新にする: Gitleaks（`ci.yml`・`.pre-commit-config.yaml`・README）、OSV-Scanner（`ci.yml`・README）、SpotBugs と FindSecBugs、palantir-java-format、Playwright とブラウザ（`@playwright/test` と同じ版）、axe-core（K-84）[desc]。
- **FR4.6** axe-core を上げて新しい規則の違反が出たときは、規則を外さずに違反をすべて直してから上げ、E2E と vitest-axe の両方の件数を記録する。axe-core の版が lockfile で1つにそろっているかを確かめる（`team.md`）。
- **FR4.7** 新しく足す依存（推移依存で増えるものを含む）は、採用の前にライセンスを確かめ、Apache License 2.0 と異なるものは理由を記録する（`team.md`）。

### FR5 サブモジュール（部品 `make-you-chic-ui`・`java-mustache-processor`）

- **FR5.1** make-you-chic-ui（今は `cab4207`）と java-mustache-processor（今は `8d44c36`、0.1.0）を上流の `main` の最新に上げる [desc]。
- **FR5.2** 上げる前に、新しいコミットが上流の `main` から辿れることと、make-you-chic-ui の `package-lock.json` の前後の差（増えた・変わったパッケージと `hasInstallScript`）を読み取りで確かめ、前後のハッシュと同じ記録に書く。`hasInstallScript` の付いた項目が増えたときは、取り込む前に依頼者に諮る（`team.md`）。
- **FR5.3** 統合は固定先の更新の専用のコミットで、短命のブランチから `develop` へ fast-forward で行う（`team.md`）。java-mustache-processor を上げて推移依存が変わるときは、lockfile と OSV-Scanner の対象に載ることを確かめ直す。
- **FR5.4** A2: make-you-chic-ui の lockfile の `braces`（今は 3.0.3）が上流で直っていれば取り込む。A3: 依頼（5）（1ページの件数の選択と、横のスクロールの領域）が上流に公開されていれば取り込み、frontend の自前の `ScrollRegion`・`PageSizeSelect` を make-you-chic-ui の部品に置き換える。どちらも上流のソース（読み取りの `git show` など）で確かめ、無ければ見送って記録する（K-86）。

### FR6 GitHub Actions（部品 `build-and-verify`）

- **FR6.1** `.github/workflows/` の Actions を最新の版にし、コミットのハッシュで固定して版をコメントに書く [desc]。

### FR7 コンテナのイメージ（部品 `container-runtime`・`perf-and-monitoring`）

- **FR7.1** 対象DB（postgres・mysql・mariadb）のイメージを、`./gradlew verify`（3種類の結合テスト）を通った最新の版にする。`compose.yaml`・`docker/perf/compose.yaml`・`TargetDbImages`・README・perf/README をそろえる（K-83、`project.md` の Tech Stack の学び）。
- **FR7.2** Mailpit・lgtm・otel-collector・k6（`perf/versions.env`）のイメージを最新にする。どのイメージも版のタグと複数アーキテクチャの index のダイジェストで固定し、`.github/dependabot.yml` に書いた固定先の一覧と、Dependabot が見ない固定先もそろえる（`team.md` の Deployment）。
- **FR7.3** 入れ替えた後は、`docker inspect` でコンテナのイメージの版とダイジェストが `compose.yaml` の記述と一致することを確かめ、食い違うときは作り直す（`team.md` の Deployment）。

### FR8 README.md と perf/README.md の最新化（部品 `build-and-verify`・`perf-and-monitoring`）

- **FR8.1** README.md と perf/README.md を、今の手順と設定だけを残す形に書き直す。Intent・単位・Bolt の名前を見出しと本文から外し、経緯（「〜で足した」「前は〜だった」など）と perf/README の「## 履歴」を消す。経緯は `aidlc/` の各 Intent の記録と git の履歴に残る [Q3]。
- **FR8.2** 手順のコマンド・前提・設定の値は変えない（FR1〜FR7 で版が変わったものは新しい値にする）。書き直しの前後で、README が載せるコマンドと環境変数の名前の一覧が、消したもの・足したものを除いて一致することを確かめる [Q3]。
- **FR8.3** README.md の make-you-chic-ui の固定先 `ecb07e9`（37 行）を今の値に直す（K-86）。
- **FR8.4** 決まりの文書（`team.md`・`project.md`）と、ほかの文書が README の節の名前・番号を参照している箇所を洗い出し、書き直しで参照が切れないようにする。

### FR9 前の Intent からの小さな直し（部品 `businessdata`・`perf-and-monitoring`）

- **FR9.1** P1: MySQL の結合テストで、長い文字のカラムで並べ替えた業務データの一覧の深いページ（大きな `OFFSET`・1ページの件数の上限）を流し、1038（並べ替えの領域の不足）が起きるかを確かめる。起きれば、直し方を Code Generation の計画で依頼者に諮って直し、再現のテストを同じコミットに入れる。起きなければ、確かめた範囲（行の数・文字の長さ・`OFFSET`・件数）を記録して閉じる（K-92）[Q5]。
- **FR9.2** T1: `perf/redact-k6-evidence.mjs` を Vitest の `coverage.include` に足し、frontend と同じ下限（行 80%・分岐 70%）を当てる（K-90）[Q6]。
- **FR9.3** T2: 同じ道具に ESLint を当てる。型の検査の当て方（`// @ts-check` と JSDoc、TypeScript への書き換えなど）は Code Generation の計画で決める（K-90）[Q6]。
- **FR9.4** T3: `BusinessDataWriteService.warnReference` で `stage` が null になる経路（`StepAttempt.Unexpected`）をテストで確かめ、WARN に区分（`stage` に当たるキー）が出るように直す。値（行の値・参照先の値）は出さないまま（K-91、`project.md` の Forbidden）[Q6]。

### FR10 負荷の試験（部品 `perf-and-monitoring`）

- **FR10.1** 判定の場面（業務データの読み書き・参照の場面・`dslMixed` の `memory.peak`・対象DB の `anon`）を、3種類の対象DB で1回ずつ流し、前の Intent（261029-reference-lookup-cleanup・261027-perf-targets-review）の値と並べて記録する。線を割った場面だけ2回目を流す [Q7]。
- **FR10.2** 合否の線と物差しは変えない（今の `perf/k6/scenarios.js` の `thresholdsFor` と `perf/README.md` の線のとおり）。持ち主は Build and Test（この Intent の流れに Performance Validation の段が無いため。`project.md` の学び）。配備したアプリを止めて流し、全体を `caffeinate -dims` で包み、眠りが入った回は使わず流し直す。
- **FR10.3** k6 の結果を記録（`evidence/`）に置くときは、認証の値を `perf/redact-k6-evidence.mjs` で伏せ、`--scan`・`gitleaks dir`・`eyJ` の検索で確かめる（`project.md` の Forbidden）。
- **FR10.4** 線を割った場面が2回目でも割れたときは、目標を緩めずに Not Met のまま記録し、持ち越し先を依頼者に諮る。

### FR11 配備・片付け・取り込み

- **FR11.1** 通常どおり配備する。WAR とイメージを作り直して入れ替え、起動確認とスモークテストを行い、戻し方（直前の版のイメージでの再配備）を用意する。配備の前に、戻し先のタグを付けておく。
- **FR11.2** 配備の後（Deployment Execution の終わり）に、`compose.yaml` などの固定先から外れた古い版のイメージと、`mastersmith` の戻し先より古いタグを、使うコンテナが 0 件であることを1つずつ確かめ、消す一覧の承認を得てから消す。名前の無いボリュームは残す [Q4]。前後の `docker system df` を記録する。
- **FR11.3** `main` への取り込みは、前の取り込みの後のすべてのコミットの CI を試行ごとに確かめてから、依頼者の承認を得て `develop` から fast-forward で行う。`git push` は依頼者が行う（`team.md`・`project.md`）。

## 非機能の要件

- **NFR1 目標を変えない**: 性能・資源の目標と物差し、カバレッジの下限（行 80%・分岐 70%、backend はパッケージごとにも）は変えない。カバレッジの計測の除外を増やさない。`packagesJudgedByTotal` の一覧を増やさない（`team.md`）。
- **NFR2 安全**: 統合の前に、`./gradlew verify` の OSV-Scanner で High 以上が出ない（frontend の実行時の依存と成果物を作る道具の High、悪意のあるパッケージは止める。`team.md`）。npm は `ignore-scripts=true` のまま入れる。パスワード・トークン・業務データの行の値・対象DB の接続情報を、ログ・監査・トレースの属性・エラー応答・記録に含めない。
- **NFR3 品質の関門**: 統合の前に `./gradlew verify`（colima の `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡し、`caffeinate -dims` で包む）が通る。frontend の本体・依存が変わるブランチは E2E（見本の PG と `docker/e2e/compose.targetdb-port.yaml`）も統合の前に通す。テストの件数とカバレッジは `:backend:cleanTest :backend:cleanIntegrationTest` を付けた実測で報告する。既存の ArchUnit の境界テストと SpotBugs の除外は緩めない（緩める必要があれば Code Generation の計画で依頼者の承認を得る）。
- **NFR4 互換と戻し**: 画面の機能・API の応答の形・DB スキーマ（Flyway の移行）を変えない。戻しは直前の版のイメージでの再配備で行える。
- **NFR5 時間**: `./gradlew verify` の時間と CI の検査の job の時間を記録し、CI の上限の7割（約 42 分）を超えたらテストの分け方を依頼者に諮る（`team.md`）。

## 制約

- 配備先は開発者の PC 上のコンテナの範囲。実在の宛先・外部の SMTP へメールを送らない。
- `vendor/make-you-chic-ui`・`vendor/java-mustache-processor` の中身は直接変更しない。
- Gradle の依存の取得元は Maven Central だけ（ビルドのプラグインは Gradle Plugin Portal でよい）。
- `git push` は依頼者が行う。コミットは、段の初稿と承認のときは尋ねずに行い、それ以外は提案して承認を得る（`project.md`）。
- 公開のリポジトリのため、秘密情報・個人情報をコミットしない。

## 前提

- 「最新」は、Code Generation の時点で公開されている安定版を指す（プレリリース・RC は含めない）[assumption]。
- Node 26 系は 2026-11-01 の時点で LTS である [desc]。
- 配備したアプリの内部DB のスキーマは変わらないため、戻しのために内部DB の複写を戻す必要は無い（Flyway の移行を足さない前提）[assumption]。

## 範囲の外

- 新しい機能（N3〜N7）と N2（`users.admin_flag` を縮める段。依頼者の決定で行わない）。
- 配備先（クラウド）の決定と、それを待つ E1〜E12・D。
- Java の大きな版の変更（25 系に留める）。
- 名前の無いボリュームの片付け [Q4]。

## 未解決の点

- 大きな版の更新のうち、どれを見送るかは Code Generation で `verify` と E2E を流して決まる。見送ったものは計画との差として記録し、Build and Test で一覧にする。
- P1 の直し方は、1038 が起きたときに Code Generation の計画で依頼者に諮る。
- T2 の型の検査の当て方は Code Generation の計画で決める。
- A3 の置き換えは、上流に依頼（5）が公開されているかで決まる。

## Sources

- 依頼の文（`aidlc engine workspace project-description` の description）と、依頼者が Reverse Engineering の段で足した README の最新化
- 質問と答え: `aidlc/spaces/default/intents/261101-maintenance-update/inception/requirements-analysis/requirements-analysis-questions.md`（Q1〜Q7 すべて A）
- コードの知識ベース `aidlc/spaces/default/codekb/mastersmith2/`（`business-overview.md` の所見 K-81〜K-93、`architecture.md`、`code-structure.md`、`technology-stack.md`、`dependencies.md`、`code-quality-assessment.md`）
- 前の Intent の持ち越し `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/feedback-optimization/feedback-loop.md` 2節
- `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 「最新」は Code Generation の時点の安定版（プレリリース・RC を含めない）。
- [assumption] Flyway の移行を足さないため、戻しはイメージだけで行える。
- 見送る大きな版・P1 の直し方・T2 の型の検査・A3 の置き換えは、Code Generation で決まる（未解決の点）。
