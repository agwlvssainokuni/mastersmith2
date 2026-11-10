# Requirements Analysis の質問（261110-deps-forks-review）

依頼: 依存の定期更新（Spring Boot・Jackson の上書き・opentelemetry-logback-appender の固定・H2 と `-Dh2.compactThreads=1`・コンテナのイメージのダイジェストなどの見直し）、知識ベースの古い記述（R-06）の Reverse Engineering での更新、結合テストの並べ方 `-PitForks` の値の再評価（2 と 3 でそれぞれ複数回実行し、どちらが良いかを決める）。

## 決まっていること（質問にしない）

- 知識ベースの古い記述（R-06）は Reverse Engineering で直した（承認済み。K-142）。
- Jackson の BOM の上書きは今のビルドに無い（コミット `98ef3e51`。今の Jackson 3.1.7 は Spring Boot 4.1.2 の BOM の値。K-138）。この Intent では Jackson を個別に上書きしない。`project.md` の Tech Stack の古い記述（Jackson の上書き 3.1.7・「Spring Boot 4.1.1」）と `dependabot.yml` の説明の「4.1.1」は、今の形に直す（K-143）。
- 版の固定は opentelemetry-logback-appender 2.28.1-alpha と networknt 3.0.6 の2つ。`-Dh2.compactThreads=1` は Dockerfile・テストの JVM・E2E の起動の3か所（K-139）。
- コンテナのイメージは版のタグと複数アーキテクチャの index のダイジェストで固定し、Dependabot が見ない固定先もそろえる（`team.md` の Deployment）。
- 依存の更新の取り込みの前には全件の `./gradlew verify` を流す（`team.md` の Way of Working）。E2E は Build and Test とリリースの前に1回流す。
- 目標（カバレッジの下限・性能の線と物差し）は変えない。
- `-PitForks` の今の形: CI の既定は 3（`ci.yml` の `IT_FORKS_DEFAULT`）、手動の実行の入力 `it_forks` で 1〜8 を選べる。手元の推奨も 3。
- これまでの CI の検査の job の時間（`gh run view` で確かめた）:
  - N=1（`-PitForks` を入れる前、`4fe8e790`、push）: 35分47秒
  - N=2（`ded97b38`、手動の実行、2026-11-08）: 29分07秒（1回だけ）
  - N=3（`ded97b38`、手動の実行、上の N=2 の直後）: 21分39秒
  - N=3（push の3回。`a9ddc077`・`24ed24e6`・`7f36d8aa`）: 26分27秒・26分19秒・26分11秒
  - N=3（`5b115bfc`、push）: 21分29秒で失敗（`UserRowLockRepositoryIT`。直しは `a9ddc077`）
- この Intent の段: Code Generation・Build and Test・Deployment Pipeline・Deployment Execution（依存の更新で WAR とイメージの中身が変わるため配備する）。

## Q1. [開発] 依存の更新の広さ

前の定期更新（Intent 261101、2026-11-01）は、Java・Node のパッチ、Spring Boot、npm と Gradle の依存（大きな版を含む）、Gradle の wrapper、検査の道具、GitHub Actions、コンテナのイメージ、サブモジュールまで全面に上げました。今回の依頼は、Spring Boot と連動する固定・H2・コンテナのイメージのダイジェストを名指ししています。どこまで上げますか。

A. 前回と同じ全面（Java 25 系・Node 26 系のパッチ、Spring Boot、npm と Gradle の依存の大きな版を含む最新、Gradle の wrapper、検査の道具、GitHub Actions、コンテナのイメージ、2つのサブモジュール）
B. A からサブモジュール（make-you-chic-ui・java-mustache-processor）を除く
C. 依頼に名指ししたもの（Spring Boot とそれに連動する固定・H2・コンテナのイメージ）と、その更新に必要なものだけ
X. Other (please specify)

[Answer]: B

## Q2. [開発] 連動する固定の見直し方

固定は opentelemetry-logback-appender 2.28.1-alpha（Spring Boot が持つ OpenTelemetry と合わせるため）と networknt 3.0.6（Jackson を Spring Boot の版から引き上げないため）、H2 の `-Dh2.compactThreads=1`（H2 2.4.240 の詰め直しの競合を避けるため）の3つです。どう見直しますか。

A. 3つを1つずつ外して（または上げて）、全件の `./gradlew verify` と E2E が通るかを試す。通れば外し、通らなければ理由を記録して残す（前回と同じやり方）
B. 先に上流の変化（Spring Boot の新しい版が持つ OpenTelemetry と Jackson の版、H2 の新しい版の有無）を確かめ、前提が変わったものだけを A のやり方で試す。変わっていないものは固定のまま、版の記述だけ直す
C. 試さずに固定のまま残す
X. Other (please specify)

[Answer]: B

## Q3. [品質] `-PitForks` の 2 と 3 の比べ方

N=2 は1回しか測っていないため、同じ条件で複数回流して比べます。CI の手動の実行（`gh workflow run ci.yml --ref <ブランチ> -f it_forks=<N>`）は1回あたり約 22〜30 分かかり、前の実行が終わるまで次は待ちます。何回ずつ、どのコミットで流しますか。

A. CI の手動の実行で、2 と 3 を各3回（同じコミットで 2・3・2・3・2・3 と交互に）流す。依存の更新を統合した後のコミットで流す（約 2.5〜3 時間）
B. A と同じ回数を、依存の更新の前の今の `develop`（`7f36d8aa` 以降の記録だけのコミット）で流す（依存の更新と切り分けられる）
C. CI で各2回（交互に）に加え、手元の `./gradlew verify` でも 2 と 3 を各1回流す
X. Other (please specify)

[Answer]: A

## Q4. [品質] `-PitForks` の決め方（物差し）

A. 検査の job の時間の中央値が短い方を選ぶ。差が1分未満なら、資源の少ない 2 を選ぶ。どちらかで失敗・不安定なテストが出たら、原因を確かめてから決める
B. 検査の job の時間の平均が短い方を選ぶ（差の大きさは問わない）
C. 最も遅かった回（最悪の値）が短い方を選ぶ（CI の上限 60 分とその7割の 42 分の線からの余裕を重く見る）
X. Other (please specify)

[Answer]: A

## Q5. [品質] 負荷の試験（k6）

前回の定期更新では、判定の場面を3種類の対象DB で1回ずつ流し、前の値と並べました（線を割った場面だけ2回目）。今回も流しますか。

A. 前回と同じく、Build and Test で判定の場面を3種類の対象DB で1回ずつ流し、前の値と並べる（線と物差しは変えない）
B. Spring Boot・H2・JRE の版が変わったときだけ A を行い、変わらないときは省いて理由を記録する
C. 流さない（配備の後の起動確認とスモークテストだけで確かめる）
X. Other (please specify)

[Answer]: B
