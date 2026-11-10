# 要件（261110-deps-forks-review）

Initial description: 依存の定期更新（Spring Boot・Jackson の上書き・opentelemetry-logback-appender の固定・H2 と -Dh2.compactThreads=1・コンテナのイメージのダイジェストなどの見直し）と、知識ベース（codekb）の古い記述（packagesJudgedByTotal を現役の仕組みとして書いている箇所。Intent 261108-ci-checks-release のレビュー R-06）の Reverse Engineering での更新、結合テストの並べ方 -PitForks の値の再評価（2 と 3 でそれぞれ複数回実行し、どちらが良いかを決める） [desc]
Workflow-selected scope: bugfix（深さ Minimal、Test Strategy Minimal） [scope]

## Intent の分析

機能は足さず、依存・実行環境・道具を今の最新に追いつかせ、CI の結合テストの並べ方の既定の値を実測で決め直すメンテナンスである [desc]。目的は次の3つ。

- **目的1（依存の最新化）**: 前の定期更新（Intent 261101、2026-11-01）と同じ広さで、サブモジュールを除いて最新の安定版にそろえる [Q1]。
- **目的2（固定と古い記述の見直し）**: Spring Boot と連動する固定は、前提が変わったものだけを外して試す [Q2]。知識ベースの古い記述（R-06）は Reverse Engineering で直した（K-142）。決まりの文書と `dependabot.yml` に残る古い版の記述（K-143）を直す [desc]。
- **目的3（`-PitForks` の既定の値）**: CI で 2 と 3 を各3回流し、検査の job の時間の中央値で既定の値を決める [Q3][Q4]。

利用者から見える画面と API の形・DB スキーマは変えない。依存の更新で WAR とイメージの中身が変わるため、通常どおり配備する。

## 機能の要件

### FR1 依存の最新化（部品 `build-and-verify`・`container-runtime`）

- **FR1.1** Java を 25 系の最新のパッチにする。toolchain（`gradle/libs.versions.toml`）・CI の `java-version`・Dockerfile の JRE のイメージ・`perf/versions.env` と `docker/hikari-pool.sh` の JDK のイメージをそろえる [Q1]。
- **FR1.2** Node を 26 系の最新のパッチにする。Node の版を書いた箇所（`frontend/package.json` の `engines`・`build.gradle.kts` の版の確かめ・CI の `node-version` ほか）をそろえる [Q1]。
- **FR1.3** Spring Boot（今は 4.1.2）を最新の安定版にする。Jackson は個別に上書きせず Spring Boot の BOM の版を使う（K-138）。Jackson の版が変わるときは OSV-Scanner で High 以上が出ないことで確かめる [Q1]。
- **FR1.4** frontend の npm の依存と backend の Gradle の依存を、大きな版も含めて最新の安定版にし、lockfile（`frontend/package-lock.json`・`backend/gradle.lockfile`・`settings-gradle.lockfile`）を更新する。`./gradlew verify` または E2E が通らず、直すのが大きい更新だけを見送り、`.github/dependabot.yml` の `ignore` に理由と外す時期を書く [Q1]。
  - 「直すのが大きい」の目安: 直すのにアプリの本体（`src/main`）の5ファイルを超える変更、画面の見た目・API の応答の形・DB スキーマの変更、または既存の決まり（`team.md`・`project.md`）の書き換えが要るもの。目安に当たるかは Code Generation の計画で依頼者に諮って決める。
  - 今ある `ignore` の行（opentelemetry-logback-appender・networknt は FR2 で、`typescript` の大きな版・`eclipse-temurin` の大きな版・otel-collector の `0.164.0-386` はこの Intent で）を1行ずつ見直し、外す時期に来たものは外して試す。`eclipse-temurin` の大きな版の見送りは Java を 25 系に留めるため残す。
- **FR1.4a** make-you-chic-ui の部品（peer 依存・部品の色のコントラストなど）が原因で上げられない依存は、サブモジュールを上げずに見送り、`.github/dependabot.yml` の `ignore`（または見送りの記録）に理由と外す時期を書く。make-you-chic-ui のリポジトリへの依頼は次の Intent に回し、依頼の文面だけをこの Intent の記録（`<record>/make-you-chic-ui-request-<n>.md`）に置く（`team.md` の Way of Working）。axe-core の引き上げ（FR1.7）が部品の違反で止まるときも同じに扱う [Q1]。
- **FR1.5** Gradle の wrapper、検査の道具（Gitleaks・OSV-Scanner・SpotBugs と FindSecBugs・palantir-java-format・Playwright とブラウザ・axe-core）、GitHub Actions（コミットのハッシュで固定し版をコメントに書く）を最新にする [Q1]。
- **FR1.6** コンテナのイメージ（対象DB の postgres・mysql・mariadb、Mailpit・lgtm・otel-collector・k6）を最新にする。対象DB は `./gradlew verify` の3種類の結合テストを通った最新の版にする。どのイメージも版のタグと複数アーキテクチャの index のダイジェストで固定し、`.github/dependabot.yml` に書いた固定先の一覧と Dependabot が見ない固定先（`TargetDbImages`・`docker/perf/compose.yaml`・`perf/versions.env`・README など）をそろえる [desc][Q1]。
- **FR1.7** axe-core を上げて新しい規則の違反が出たときは、規則を外さずに違反をすべて直してから上げ、E2E と vitest-axe の両方の件数を記録する。axe-core の版が lockfile で1つにそろっているかを確かめる（`team.md`）。
- **FR1.8** 新しく足す依存（推移依存で増えるものを含む）は、採用の前にライセンスを確かめ、Apache License 2.0 と異なるものは理由を記録する（`team.md`）。
- **FR1.9** サブモジュール（make-you-chic-ui・java-mustache-processor）は上げない [Q1]。

### FR2 連動する固定の見直し（部品 `build-and-verify`・`container-runtime`）

- **FR2.1** 先に上流の変化を確かめる: 新しい Spring Boot が持つ OpenTelemetry の版、新しい Spring Boot が持つ Jackson の版と networknt の新しい版が求める Jackson の版、H2 の新しい版の有無 [Q2]。
- **FR2.2** 前提が変わった固定だけを、1つずつ外して（または上げて）全件の `./gradlew verify` と E2E で試す。opentelemetry-logback-appender は、固定の理由が「外部エクスポートの有効時に失敗する」ことのため、加えて外部エクスポートを有効にした状態でアプリを起動し、起動が失敗しないことと手元の受け手（compose の lgtm）にログが届くことを確かめる（既定の無効のままの `verify` と E2E だけでは合格にしない）。通れば外し、`dependabot.yml` の `ignore` の該当の行も直す。通らなければ理由を `project.md` の学びの言い方に合わせて記録して残す [Q2]。
- **FR2.3** 前提が変わっていない固定は外さず、版の記述（理由のコメント・`project.md` の Tech Stack）だけを今の値に直す [Q2]。
- **FR2.4** `-Dh2.compactThreads=1` は今の3か所（Dockerfile・`backend/build.gradle.kts` のテストの JVM・`frontend/playwright.config.ts` の E2E の起動）のまま扱い、外すときは3か所と確かめの `H2CompactThreadsTest` をそろえる（K-139）。
- **FR2.5** E2E で起動する WAR の JVM に `-Duser.timezone` が見当たらない点（K-139、未検証）を確かめる。`team.md` の Deployment の「4か所を同じ値にそろえる」に合わないときは、直し方を Code Generation の計画で依頼者に諮る。

### FR3 古い記述の直し（部品 `build-and-verify`）

- **FR3.1** `project.md` の Tech Stack の「Jackson の上書きは 3.1.7（BOM を platform で読む形）」の行と「Spring Boot 4.1.1」の記述を、今の形（上書きは無く、Jackson は Spring Boot の BOM の版）と FR1.3 で上げた版に直す（K-143）[desc]。
- **FR3.2** `.github/dependabot.yml` の gradle の説明の「4.1.1」を、FR1.3 で上げた版に直す（K-143）。
- **FR3.3** 書き換えた行には、ほかの書き換えと同じく「（<書き換えたコミットの日付> Intent 261110-deps-forks-review で書き換え）」を付け（日付は書き換えのコミットを作る日の YYYY-MM-DD）、前の文と後の文を Code Generation の記録に並べて残す。

### FR4 `-PitForks` の既定の値の再評価（部品 `build-and-verify`）

- **FR4.1** 比べは Build and Test の段で行う。依存の更新を `develop` に統合し、依頼者が `origin` へプッシュした後に、同じコミットで CI の手動の実行（`gh workflow run ci.yml --ref <比べのコミットを指すブランチ> -f it_forks=<N>`）を 2・3・2・3・2・3 の順に交互に各3回流す。手動の実行を起こす前に依頼者の承認を得る [Q3]。
- **FR4.1a** 6回の間（約 2.5〜3 時間）は比べのコミットを変えない。比べの間に `develop` へ別のコミット（記録だけのコミットを含む）をプッシュするときは、比べのコミットに短命のブランチ（例: `ci-forks/261110`）を付けて `--ref` にそのブランチを渡し、6回すべてが同じコミット（`headSha` で確かめる）で動いたことを記録する。ブランチは比べの後に消す。
- **FR4.2** 各回の検査の job の始めと終わりの時刻・成否・失敗したテストを `gh run view --json attempt,jobs` で試行ごとに記録する。プッシュで動いた CI（既定の 3）の回は参考として並べ、比べの6回には数えない [Q3]。
- **FR4.3** 決め方: 検査の job の時間の中央値が短い方を既定の値にする。差が1分未満なら 2 にする。どちらかで失敗・不安定なテストが出たら、`team.md` の Testing Posture の「不安定なテストと CI の失敗」の決まりで原因を確かめてから決める [Q4]。選んだ値の最も遅かった回が CI の上限の7割（約 42 分）を超えたときは、決める前に依頼者に諮る（NFR5）。
- **FR4.4** 決めた値に合わせて `.github/workflows/ci.yml` の `IT_FORKS_DEFAULT` とそのコメント（「CI の上で 2 と 3 を比べて決める」）を直し、`docs/verification.md` の `-PitForks` の節に CI の比べの結果（回ごとの時間と中央値）を足す。手元の推奨（N=3）は手元の実測によるため変えない [Q3][Q4]。
- **FR4.5** 既定の値を変えたときは、その変更のコミットのプッシュで動く CI の結果も記録する。

### FR5 負荷の試験（部品 `perf-and-monitoring`）

- **FR5.1** Spring Boot・H2・JRE のどれかの版が変わったときは、Build and Test で判定の場面（業務データの読み書き・参照の場面・`dslMixed` の `memory.peak`・対象DB の `anon`）を3種類の対象DB で1回ずつ流し、前の値と並べて記録する。線を割った場面だけ2回目を流す [Q5]。
- **FR5.2** どれも変わらないときは負荷の試験を省き、省いた理由を Build and Test の記録に書く [Q5]。FR1.1・FR1.3 で JRE と Spring Boot の版は変わる見込みが高く、実際には FR5.1 を流す見込みである。変わったかどうかは、Code Generation の終わりに前後の版（JRE のイメージのタグ・Spring Boot・H2）を並べて記録し、Build and Test はその記録で分岐を決める。
- **FR5.3** 合否の線と物差しは変えない。全体を `caffeinate -dims` で包み、眠りが入った回は使わず流し直す。k6 の結果を記録に置くときは認証の値を `perf/redact-k6-evidence.mjs` で伏せて確かめる（`project.md`）。線を割った場面が2回目でも割れたときは、目標を緩めずに Not Met のまま記録し、持ち越し先を依頼者に諮る。

### FR6 配備と取り込み

- **FR6.1** 通常どおり配備する。WAR とイメージを作り直して入れ替え、起動確認とスモークテストを行う。配備の前に戻し先のタグを付ける。前の配備されていない Intent の「次の配備へ引き継ぐこと」の一覧があれば読んで手順に入れる（`project.md` の Deployment）。
- **FR6.2** 配備の後に、固定先から外れた古い版のイメージと、`mastersmith` の戻し先より古いタグを、使うコンテナが 0 件であることを確かめ、消す一覧の承認を得てから消す。
- **FR6.3** `main` への取り込みは、前の取り込みの後のすべてのコミットの CI を試行ごとに確かめてから、依頼者の承認を得て `develop` から fast-forward で行う。`git push` は依頼者が行う（`team.md`・`project.md`）。

## 非機能の要件

- **NFR1 目標を変えない**: 性能・資源の目標と物差し、カバレッジの下限（行 80%・分岐 70%、backend はすべてのパッケージごとにも）は変えない。カバレッジの計測の除外を増やさない（`team.md`）。
- **NFR2 安全**: 統合の前に、全件の `./gradlew verify` の OSV-Scanner で High 以上が出ない（frontend の実行時の依存と成果物を作る道具の High、悪意のあるパッケージは止める）。npm は `ignore-scripts=true` のまま入れる。パスワード・トークン・業務データの行の値・対象DB の接続情報を、ログ・監査・トレースの属性・エラー応答・記録に含めない。
- **NFR3 品質の関門**: 依存の更新の取り込みの前に全件の `./gradlew verify`（colima の `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡し、`caffeinate -dims` で包む）が通る（`verifyQuick` で代えない）。E2E は Build and Test で全体を1回流す。テストの件数とカバレッジは `:backend:cleanTest :backend:cleanIntegrationTest` を付けた実測で報告する。既存の ArchUnit の境界テストと SpotBugs の除外は緩めない。
- **NFR4 互換と戻し**: 画面の機能・API の応答の形・DB スキーマ（Flyway の移行）を変えない。戻しは直前の版のイメージでの再配備で行える。
- **NFR5 時間**: 全件の `./gradlew verify` の時間と CI の検査の job の時間を記録し、CI の上限の7割（約 42 分）を超えたらテストの分け方を依頼者に諮る（`team.md`）。

## 制約

- 配備先は開発者の PC 上のコンテナの範囲。実在の宛先・外部の SMTP へメールを送らない。
- `vendor/make-you-chic-ui`・`vendor/java-mustache-processor` の中身は直接変更しない。
- Gradle の依存の取得元は Maven Central だけ（ビルドのプラグインは Gradle Plugin Portal でよい）。
- `git push` は依頼者が行う。CI の手動の実行は、起こす前に依頼者の承認を得る。
- コミットは、段の初稿と承認のときと `aidlc/` の下だけの記録は尋ねずに行い、それ以外は提案して承認を得る（`project.md`）。
- 公開のリポジトリのため、秘密情報・個人情報をコミットしない。

## 前提

- 「最新」は、Code Generation の時点で公開されている安定版を指す（プレリリース・RC は含めない）[assumption]。
- CI の機械（`ubuntu-latest`）は回ごとに資源と込み具合が揺れる。そのため1回ずつの値ではなく中央値で比べる [Q4]。
- 依存の更新で Flyway の移行は足さないため、戻しのために内部DB の複写を戻す必要は無い [assumption]。

## 範囲の外

- サブモジュール（make-you-chic-ui・java-mustache-processor）の更新と、make-you-chic-ui のリポジトリへの依頼の取り込み（依頼の文面を置くところまでは FR1.4a で行う）[Q1]。
- Java の大きな版の変更（25 系に留める）と Node の大きな版の変更（26 系に留める）。
- 手元の `-PitForks` の推奨の値の見直し（手元の実測は前の Intent の値のまま）[Q3]。
- 新しい機能、配備先（クラウド）の決定。

## 未解決の点

- 新しい Spring Boot が持つ OpenTelemetry・Jackson の版、H2 の新しい版の有無は、Code Generation の始めに確かめる（FR2.1）。
- E2E の WAR の JVM の `-Duser.timezone`（FR2.5）の要否は、Code Generation の計画で決める。

## Sources

- 依頼の文: [desc]
- 範囲: [scope]
- 質問と答え: `aidlc/spaces/default/intents/261110-deps-forks-review/inception/requirements-analysis/requirements-analysis-questions.md`（Q1〜Q5）
- 知識ベース: `aidlc/spaces/default/codekb/mastersmith2/business-overview.md`（K-138〜K-143）・`architecture.md`（K-140）・`code-structure.md`・`technology-stack.md`（K-138・K-139）・`code-quality-assessment.md`（K-142・K-143）
- 決まり: `aidlc/spaces/default/memory/team.md`（Way of Working・Testing Posture・Deployment・Code Style）・`aidlc/spaces/default/memory/project.md`（Tech Stack・Deployment・Testing Posture）
- 知識の置き場: `aidlc/spaces/default/knowledge/aidlc-shared/ops-notes.md`（負荷の試験）
- 前の定期更新の要件: `aidlc/spaces/default/intents/261101-maintenance-update/inception/requirements-analysis/requirements.md`
- CI の時間: `gh run view`（run 42489430268・42535156503・42535169021・42633708198・42647513146・42704002194・42840361602）

## Assumptions & Open Questions

- 「最新」の定義と、Flyway の移行を足さない前提は [assumption]。Code Generation の計画で確かめる。
- 上の「未解決の点」の2つは、Code Generation の始めに確かめる。
