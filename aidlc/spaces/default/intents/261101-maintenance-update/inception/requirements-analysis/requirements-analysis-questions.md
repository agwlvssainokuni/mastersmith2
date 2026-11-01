# Requirements Analysis の質問（Intent 261101-maintenance-update）

依頼: メンテナンスの Intent。依存ライブラリの最新化、Java を 25 系（LTS）の最新に、Node を 26 系（LTS）の最新に、make-you-chic-ui と java-mustache-processor の最新化、GitHub Actions の最新化、古いイメージの削除。加えて、Gradle の wrapper、Spring Boot と連動する固定の見直し、コンテナのイメージ（対象DB・Mailpit・lgtm・otel-collector・k6）、検査の道具（Gitleaks・OSV-Scanner・SpotBugs と FindSecBugs・palantir-java-format・Playwright とブラウザ・axe-core）、Dependabot の ignore の見直し、前の Intent 261029-reference-lookup-cleanup からの小さな直し（P1・T1〜T3・A2・A3）。さらに、README.md と perf/README.md に過去の経緯が残って重くなっているので最新化する（依頼者が Reverse Engineering の段で追加）。

## 決まっていること（質問にしない）

- **Java**: 25 系の最新のパッチにする。toolchain（`libs.versions.toml` の `java`）・CI の `java-version`・Dockerfile の JRE のイメージ・`perf/versions.env` と `docker/hikari-pool.sh` の JDK のイメージ・README・perf/README をそろえる（K-82）。イメージは版のタグと複数アーキテクチャの index のダイジェストで固定する（`team.md` の Deployment）。
- **Node**: 26 系の最新にする。`frontend/package.json` の `engines`（今は `>=24 <25`）、`build.gradle.kts` の `startsWith("v24.")`、CI の `node-version` を含む 6 ファイル・10 か所をそろえる（K-81）。`NODE_OPTIONS=--no-experimental-webstorage` が Node 26 で使えるかを確かめる。
- **サブモジュール**: 上流の `main` の最新に上げる。上げる前に、新しいコミットが上流の `main` から辿れることと、make-you-chic-ui の `package-lock.json` の前後の差（`hasInstallScript` の増加）を読み取りで確かめ、前後のハッシュを記録する。`hasInstallScript` が増えたら取り込む前に依頼者に諮る。統合は固定先の更新の専用のコミットで fast-forward（`team.md`）。A2（`braces`）と A3（依頼（5））は上流のソースで確かめ、公開されていれば取り込み、無ければ見送って記録する。
- **GitHub Actions・Gradle の wrapper・検査の道具**: 最新の版にする。Actions はコミットのハッシュで固定し、版をコメントに書く。Gitleaks・OSV-Scanner は `ci.yml`・`.pre-commit-config.yaml`・README の固定先をそろえる（K-84）。
- **コンテナのイメージ**: 対象DB は `./gradlew verify`（3種類の結合テスト）を通った最新の版にし、`compose.yaml`・`docker/perf/compose.yaml`・`TargetDbImages`・README・perf/README をそろえる（`project.md` の Tech Stack の学び）。Mailpit・lgtm・collector・k6 も最新にし、入れ替えた後は `docker inspect` で版とダイジェストを確かめる。
- **Jackson の BOM の上書き**: Reverse Engineering で、上書きは既に無いと確かめた（K-85。今の 3.1.7 は Spring Boot の管理の版）。この項目は対象から外す。
- **axe-core**: 上げて新しい規則の違反が出たら、規則を外さずに違反をすべて直してから上げる。E2E と vitest-axe の両方を流して件数を記録する（`team.md`）。
- **Dependabot の受け方**: GitHub の画面でマージせず、手元でまとめて更新して `verify` を通してから統合する。見送る更新は `.github/dependabot.yml` の `ignore` に理由と外す時期を書く（`team.md`）。
- **E2E**: frontend の本体と依存が変わるため、統合の前に E2E を流す（`team.md` の Way of Working）。
- **README の古い記述**: README.md 37 行の make-you-chic-ui の固定先 `ecb07e9` は今の `cab4207`（または更新後の値）に直す（K-86）。
- **配備**: 通常どおり Deployment Pipeline と Deployment Execution で配備し、`main` へは依頼者の承認を得て fast-forward で取り込む。

## 質問

### Q1. Spring Boot の上げ幅と、連動する固定の扱い

今は Spring Boot 4.1.2 です。連動する固定は、`opentelemetry-logback-appender` 2.28.1-alpha（Spring Boot の OpenTelemetry との食い違いのため）、networknt 3.0.6（Jackson を引き上げないため）、H2 の `-Dh2.compactThreads=1`（H2 2.4.240 の詰め直しの競合のため、3か所）です。

**推奨: A**。理由: メンテナンスの目的は最新に追いつくことで、Spring Boot の小さな版の更新は互換を保つことが多く、`verify` と E2E で崩れを拾えます。固定は外す条件（`project.md`・`dependabot.yml` のコメント）が書いてあるため、外せるかを1つずつ試し、外せないものは理由を記録して残せば、次の見直しの手がかりになります。

A. Spring Boot は最新の安定版（小さな版の更新を含む）にする。連動する3つの固定は、外して `verify` と E2E が通るかを1つずつ試し、通れば外し、通らなければ理由を記録して残す（推奨）
B. Spring Boot は 4.1 系の最新のパッチだけにする。連動する固定は今のまま残す
C. Spring Boot は最新の安定版にするが、連動する固定は試さず今のまま残す
X. Other (please specify)

[Answer]: A

### Q2. 大きな版（major）の更新と `ignore` の見直し

npm・Gradle の依存には大きな版の更新が出ているものがありえます。`dependabot.yml` の `ignore` は5つ（appender・networknt・`typescript` の大きな版・`eclipse-temurin` の大きな版・collector の `0.163.2-386`）です（K-87）。

**推奨: A**。理由: 依頼は「最新化」で、大きな版を見送り続けると、後でまとめて上げる手間と危険が増えます。崩れたものだけを理由つきで見送れば、決まり（`ignore` に理由と外す時期）とも合います。`eclipse-temurin` の大きな版は Java を 25 系に留める決定のため残します。

A. 大きな版も含めて最新にする。`verify`・E2E が通らず直すのが大きいものだけを見送り、`ignore` に理由と外す時期を書く。`typescript` の大きな版の見送りも外して試す。`eclipse-temurin` の大きな版の見送りは残し、collector の `-386` の見送りはそのときのタグに合わせて直す（推奨）
B. 大きな版は見送り、小さな版とパッチだけを最新にする。`ignore` は今のまま
C. A と同じだが、`typescript` の大きな版だけは今回も見送る
X. Other (please specify)

[Answer]: A

### Q3. README.md と perf/README.md の最新化の形

README.md は 1,678 行で、Intent・単位・Bolt の名前を含む見出しが 25 個、経緯の参照が 164 か所あります。perf/README.md は 1,753 行で、末尾の「## 履歴」が約 190 行です（K-88・K-89）。決めた理由と経緯は、`aidlc/` の各 Intent の記録と git の履歴に残っています。

**推奨: A**。理由: README は「今どう使うか」を読む文書で、経緯は `aidlc/` の記録が正です。別のファイルに移すと、同じ経緯を2か所で持つことになり、また重くなります。手順の中身（コマンド・前提・設定の値）は変えず、読む順を今の使い方に合わせるだけにすれば、壊す危険も小さく済みます。

A. 今の手順と設定だけを残す形に書き直す。Intent・単位・Bolt の名前を見出しと本文から外し、経緯（「〜で足した」「前は〜だった」）と perf/README の「履歴」は消す（`aidlc/` の記録と git の履歴に残る）。手順のコマンド・前提・値は変えない（推奨）
B. A と同じく書き直すが、消す経緯は `docs/history.md` などの別のファイルに移す
C. 本文は今のまま残し、古い値（固定先など）だけを直す
X. Other (please specify)

[Answer]: A

### Q4. 古いイメージの片付けの範囲と時点

前の Intent の振り返りで、`mastersmith` の古いタグは片付けました。残る候補は、固定先から外れた古い版の依存のイメージ（約 2.8GB）と、名前の無いボリューム（回収できる 12.56GB の多く）です。今回の版の更新で、さらに古くなるイメージが増えます。

**推奨: A**。理由: 依頼の「古いイメージは削除」に当たり、更新を配備した後に片付ければ、新しい版に替わった後の古い版も一度で消せます。ほかのプロジェクト（`devenv` など）が使うイメージを消さないよう、使うコンテナが 0 件であることを1つずつ確かめます。名前の無いボリュームは中身を見分けられないため、残します。

A. 配備の後（Deployment Execution の終わり）に、`compose.yaml` などの固定先から外れた古い版のイメージと、`mastersmith` の戻し先より古いタグを、使うコンテナが 0 件であることを1つずつ確かめ、消す一覧の承認を得てから消す。名前の無いボリュームは残す（推奨）
B. A に加えて、名前の無いボリュームも、使うコンテナが無いことを確かめて消す（`docker volume prune`）
C. 今すぐ（Code Generation の前に）、今の時点の古い版のイメージだけを消す
X. Other (please specify)

[Answer]: A

### Q5. P1（MySQL の 1038 の深いページ）の扱い

業務データの一覧は、並べ替えのカラムをそのまま `ORDER BY` に使い `LIMIT ? OFFSET ?` で読みます。参照先の並びは `SUBSTRING` で切って 1038（並べ替えの領域の不足）を避けていますが、一覧の深いページで 1038 が起きるかは確かめていません（K-92）。

**推奨: A**。理由: 起きるかどうかで直す要否が決まるため、まず確かめるのが先です。結合テストで起こせれば、`team.md` の「不具合を直すときは再現のテストを同じコミットに含める」に沿って直せます。起こせなければ、確かめた範囲を記録して閉じられます。

A. MySQL の結合テストで、長い文字のカラムで並べ替えた深いページ（大きな `OFFSET`・1ページの件数の上限）を流して確かめる。起きれば、直し方を Code Generation の計画で依頼者に諮って直し、再現のテストを同じコミットに入れる。起きなければ確かめた範囲を記録して閉じる（推奨）
B. 確かめるだけにし、起きても直しは次の Intent に回す
C. この Intent では扱わない
X. Other (please specify)

[Answer]: A

### Q6. T1〜T3（伏せ字の道具の検査の範囲と WARN の経路）の扱い

T1: `perf/redact-k6-evidence.mjs` のカバレッジは Vitest の `coverage.include`（`src/` だけ）の外で、下がっても気づけません。T2: 同じ道具に ESLint と `tsc` が当たらず、Prettier と oxlint だけです（K-90）。T3: `BusinessDataWriteService.warnReference` は `stage` が null のときキーを出さず、`stage` の無い WARN になりうる経路があります（値は出ない。K-91）。

**推奨: A**。理由: どれも小さな直しで、版の更新で検査の道具の設定に触れるこの Intent と同じ場所です。T1 は道具に下限を当てて下がったら気づけるようにし、T2 は ESLint だけ当て `tsc` は JS の道具のため型の検査を `// @ts-check` などで足すかを計画で決めれば足ります。T3 はテストで経路を確かめてから直します。

A. T1〜T3 をすべて直す。T1 は道具を `coverage.include` に足して下限を当てる。T2 は ESLint を当て、型の検査の当て方は Code Generation の計画で決める。T3 は `stage` が null になる経路をテストで確かめ、WARN に区分が出るように直す（推奨）
B. T1・T2 だけ直し、T3 は記録だけにする
C. どれも記録だけにして次の Intent に回す
X. Other (please specify)

[Answer]: A

### Q7. 負荷の試験（k6）の範囲

この Intent の流れには Performance Validation の段が無いため、k6 と測定は Build and Test で流します（`project.md` の学び）。Java・Spring Boot・対象DB のイメージが変わるため、応答の時間やメモリ（`memory.peak`・`anon`）が変わりうります。前の Intent では判定の場面を 3種類の DB で2回ずつ流し、配備したアプリを約 3 時間 17 分止めました。

**推奨: A**。理由: 対象DB のイメージ（MySQL・MariaDB・PostgreSQL）も上げるため、DB ごとの場面を流さないと変化に気づけません。一方、メンテナンスで目標や台本は変えないため、各1回で前の Intent の値と並べれば変化の有無を見分けられます。崩れた場面だけ2回目を流せば、止める時間を抑えられます。

A. 判定の場面（業務データの読み書き・参照の場面・`dslMixed` の `memory.peak`・対象DB の `anon`）を 3種類の DB で1回ずつ流し、前の Intent（261029・261027）の値と並べる。線を割った場面だけ2回目を流す（推奨）
B. 判定の場面を 3種類の DB で2回ずつ流す（前の Intent と同じ。止める時間が長い）
C. PostgreSQL と `dslMixed` だけを1回流す（MySQL・MariaDB の変化は見ない）
X. Other (please specify)

[Answer]: A

## Sources

- 依頼の文（`aidlc/spaces/default/intents/261101-maintenance-update/project-description.json`）と、依頼者が Reverse Engineering の段で足した README の最新化
- コードの知識ベース `aidlc/spaces/default/codekb/mastersmith2/`（`business-overview.md` の所見 K-81〜K-93、`architecture.md`、`code-structure.md`、`technology-stack.md`、`dependencies.md`、`code-quality-assessment.md`）
- 前の Intent の持ち越し `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/feedback-optimization/feedback-loop.md` 2節
- `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 「最新」は、Code Generation の時点で公開されている安定版を指す（プレリリース・RC は含めない）。
- [assumption] N2（`users.admin_flag` を縮める段）は依頼者の決定（2026-11-01）で行わないため、この Intent の範囲にも入れない。
