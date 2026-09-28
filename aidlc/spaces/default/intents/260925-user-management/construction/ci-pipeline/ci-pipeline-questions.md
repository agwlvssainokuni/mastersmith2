# CI Pipeline — Questions

前提（2026-09-28 時点）:

- この Intent では、CI の定義（`.github/workflows/`）を変えていません。変わったのは次の3つです。
  - `./gradlew verify` の中身（`build.gradle.kts`・`backend/build.gradle.kts`・`settings.gradle.kts`）：java-mustache-processor の composite build、SpotBugs の関門の追加、メールのテンプレートのライセンスヘッダー、パッケージごとのカバレッジの下限の対象の追加
  - Dependabot の設定（`.github/dependabot.yml`）：docker-compose を追加
- そのため、この段は既にある CI の記録として書きます（project.md の Deployment）。
- 次のことは決まっているため、質問にしません。
  - **CI の失敗**：`66fe981` の CI は2回とも、別々のテストの時間切れで失敗しています。直すのは次の Intent です（Build and Test の依頼者の決定、`build-and-test/test-results.md` の 8.1 節）。この段では、既知の失敗として記録します。
  - **E2E は CI の外に置く**：前の Intent の CI Pipeline の Q1 と、team.md の Testing Posture のとおりです。
  - **ブランチの流れ**：develop へ統合し、プッシュをきっかけに CI を流します（team.md の Way of Working）。
- 読み取りだけで確かめて、次の3つが分かりました。これを質問にします。

## Q1. Dependabot の gradle の実行が「失敗」になる件

2026-09-28 の Dependabot の gradle の実行は failure でした。それより前の実行は成功しています。

- ログには、`org.owasp.dependencycheck`・`gradle-wrapper`・`org.slf4j:slf4j-api`・`net.jqwik:jqwik` について `dependency_file_not_resolvable`（No build file found）が出ています。
- これらは、composite build で組むサブモジュール `vendor/java-mustache-processor` の中のビルドの依存です（例: `cherry-mustache-core/build.gradle.kts`）。
- サブモジュールの中身はこのリポジトリから変えないため、Dependabot はこれらを更新できません。
- 同じ実行で、このリポジトリの依存の更新のプルリクエスト（#11 networknt）は作られています。

A. `.github/dependabot.yml` の gradle の設定で、サブモジュール（`vendor/**`）を対象から外す（`exclude-paths`）。サブモジュールの依存の更新は、java-mustache-processor のリポジトリ側で行う
B. 設定は変えず、失敗の表示を既知のこととして記録するだけにする
C. 次の Intent で扱う
X. Other (please specify)

[Answer]: A

## Q2. GitHub の Dependabot alerts（脆弱性の知らせ）が無効になっている件

`gh` で知らせの一覧を読もうとしたところ、「Dependabot alerts are disabled for this repository」と返りました。

- team.md には「Dependabot の知らせのうち、重大度 High 以上は次の Bolt に入る前に取り込む」とあります。しかし、今は脆弱性の知らせが届いていません。版の更新のプルリクエストだけが届いています。
- 脆弱性の関門は、`./gradlew verify` の OSV-Scanner（重大度 High 以上で失敗）が担っています。

A. 依頼者が GitHub の設定（Settings → Code security）で Dependabot alerts（と security updates）を有効にする。AI は有効になった後に、読み取りで確かめて記録する
B. 無効のままにし、脆弱性の関門は OSV-Scanner だけとする（team.md の文言との差を記録する）
C. 次の Intent で扱う
X. Other (please specify)

[Answer]: B

## Q3. 開いたままの Dependabot のプルリクエスト（11 件）の取り込みの時期

11 件の内訳は次のとおりです。

| 分類 | 更新 |
|---|---|
| npm | vite 8.3.1・prettier 3.9.9・typescript 7.0.2・vitest 5.0.1・@vitest/coverage-v8 5.0.1 |
| gradle | networknt 3.0.7・opentelemetry-logback-appender 2.31.1-alpha |
| docker | eclipse-temurin 26 |
| docker-compose | postgres・mariadb 13.0.2・mysql 26.7.0 |

- team.md では、手元でまとめて版と lockfile を更新し、`./gradlew verify` を通してから統合します。
- 版を大きく上げるもの（typescript 7、vitest 5、Java 26、MySQL 26 など）があります。また、project.md の決まりで固定しているものがあります（opentelemetry-logback-appender は 2.28.1-alpha に固定。networknt 3.0.7 は Jackson を引き上げるため 3.0.6 を選んだ）。

A. 次の Intent（コントラストと CI の時間切れを直す作業）で、まとめて見直す
B. この Intent の Operation の段より前に取り込む
C. 別の Intent を立てて扱う
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

回答をまとめると、この段では次のとおり進めます。

- **CI の記録**：既にある CI の記録として `ci-config.md` と `quality-gates.md` を書きます。
  - CI の定義は変えていません。この Intent で `verify` に増えた中身と、Build and Test が記録した検査と CI の段との対応を書きます。
  - `66fe981` の CI の2回の失敗（時間切れ）は、既知の失敗として記録します。直すのは次の Intent です。
- **Q1: A**：`.github/dependabot.yml` の gradle の設定に `exclude-paths`（`vendor/**`）を足し、サブモジュールを Dependabot の対象から外します。
  - サブモジュールの依存の更新は、java-mustache-processor のリポジトリ側で行います。
  - 効き目（gradle の実行が成功になること）は、依頼者のプッシュの後の次の Dependabot の実行で確かめます。
- **Q2: B**：Dependabot alerts は無効のままにします。脆弱性の関門は `./gradlew verify` の OSV-Scanner（重大度 High 以上で失敗）だけとします。team.md の「High 以上の知らせは次の Bolt の前に取り込む」との差は、`quality-gates.md` に記録します。
- **Q3: A**：開いたままの Dependabot のプルリクエスト 11 件は、次の Intent（コントラストと CI の時間切れを直す作業）でまとめて見直します。
- **段の境目の確かめ**：Construction から Operation への確かめの結果を `verification/phase-check-construction.md` に書きます。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
