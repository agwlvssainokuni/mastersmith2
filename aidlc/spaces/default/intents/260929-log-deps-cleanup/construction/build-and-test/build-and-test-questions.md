# Build and Test の質問（260929-log-deps-cleanup）

コード生成の段で、作業ブランチの上で verify（単体 1243 件・結合 566 件、失敗 0）と E2E の全体（110 件）が通り、`develop` へ fast-forward で統合済み（HEAD `e13808b`）。この段で行うことを決めます。

## Question 1（develop での verify）
統合した `develop`（`e13808b`）は、コード生成の段で verify を流した作業ブランチと同じ中身です（その後の変更はワークフローの記録だけ）。この段で verify をもう一度流しますか？

A. 流し直す（`:backend:cleanTest :backend:cleanIntegrationTest` 付きで約 7 分。この段の実測として記録する）
B. 流し直さず、コード生成の段の実測を正とする
X. Other (please specify)

[Answer]: A

## Question 2（CI の確かめ）
CI（GitHub Actions の `./gradlew verify`）は `develop` への push で動きます。push はあなたが行います。CI の確かめをどうしますか？

A. これから push するので、CI の結果をこの段で確かめる（push したら知らせてもらう）
B. push は済んでいるので、この段で結果を確かめる
C. この段では確かめず、Deployment Pipeline の段で確かめる
X. Other (please specify)

[Answer]: B

## Question 3（team.md の直し、FR6.1）
`aidlc/spaces/default/memory/team.md` の Testing Posture の「（`backend/build.gradle.kts` の `packagesJudgedByTotal` の一覧。`user.domain`・`user.repository`・`user.service` を含む 22 パッケージ）」を、次の文言に直します。よいですか？（直した後、専用のコミットを提案します）

提案: 「（`backend/build.gradle.kts` の `packagesJudgedByTotal` の一覧。2026-09-29 の時点で 12 パッケージ。`user.domain`・`user.repository`・`user.service` は Intent 260925-user-management で一覧から外れ、含まない）」

A. 提案の文言で直す
B. 別の文言にする（Other に書く）
X. Other (please specify)

[Answer]: A

## Question 4（メールアドレスの決まりの読み方の記録）
コード生成のレビューの指摘 R-01: `project.md` の Forbidden「メールアドレスをアプリのログに含めない」は文面のまま残り、(1) 伏せ字（先頭の1文字＋`***`＋`@`＋ドメイン）は反しないとみなしたこと（F1: A）、(2) 監査の記録の失敗の ERROR は既知の例外として残ること、がどの決まりにも書かれていません。記録しますか？

A. この段の学びとして `project.md` に記録する（学びの確かめの場で、文言を選んでもらう）
B. 記録しない（Intent の記録に残るだけ）
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- `develop`（`e13808b`）で `:backend:cleanTest :backend:cleanIntegrationTest verify` を流し直し、この段の実測として記録する（Q1: A）。
- push は済んでいるので、この段で CI（GitHub Actions）の結果を確かめる（Q2: B）。
- team.md の Testing Posture の `packagesJudgedByTotal` の記述を提案の文言に直し、専用のコミットを提案する（Q3: A、FR6.1）。Dependabot の方針は足さない（FR6.2）。
- メールアドレスの決まりの読み方（伏せ字は反しない・監査の失敗の ERROR は既知の例外）を、この段の学びとして project.md に記録する（Q4: A）。
- 警報 ms-check-p95 がしきい値そのもので鳴ることは確かめず `Unverified` とし、持ち主の段を明記して引き継ぐ（レビューの R-04、project.md の Testing Posture の学び）。
- Test Strategy は Minimal のため、追加の手順書（結合・性能・セキュリティ）は、この段で流す検査（verify の中の結合テスト・OSV-Scanner・Gitleaks・SpotBugs、E2E）を記録する範囲で作る。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct

## 検査の途中の質問

## Question T1（CI の失敗）
push で動いた CI（run 36593277868、`e13808b`）が、frontend の単体テスト `src/app/layout/ShellLayout.test.tsx` の「shows the new name right after the preferences are saved」1件で失敗しました（731/732 件は成功。backend の検査の前に止まった）。手元では、`develop` の verify（clean 付き）が成功し、このテストのファイルだけを 12 回、frontend の全体を2並列で4回流してもすべて成功し、再現しません。今回の変更（make-you-chic-ui の CSS と contrast.test、`@types/node` のパッチ）はこのテストの経路（氏名の保存の後の表示）に触れていません。見立て（未検証）: テストが保存の直後に同期で名前を探すため、遅い CI で、ログイン状態の非同期の更新が保存の後に届くと、保存した利用者の設定が古い結び付きとして捨てられうる。どうしますか？

A. CI の失敗したジョブを再実行して（`gh run rerun --failed`、AI が行う）、通るかを見る。通れば不安定と確かめられていない扱いとして進め、見立てと結果を記録する
B. 見立てのとおりテストの待ち方を直す（ログイン状態が落ち着くのを待ってから保存し、保存の後は `findByRole` で待つ）。直しは計画に無い変更として専用のコミットにし、verify と CI で確かめる
C. A を行い、通っても B の直しも行う
X. Other (please specify)

[Answer]: A

## Question T2（Dependabot の otel-collector の知らせ #21）
push の後、Dependabot が `compose.yaml` の `otel/opentelemetry-collector` を `0.161.0` から `0.161.0-386` に上げるプルリクエスト #21 を作りました。`-386` は 32 ビット x86 向けのタグで、新しい版ではなく、Dependabot がタグの後ろの部分を版と読み違えたものと見られます（`docker/perf/compose.yaml` などほかの置き場は確かめていません）。どうしますか？

A. この Intent で、`.github/dependabot.yml` の docker-compose（と docker）に、`-386` などのプラットフォームが付いたタグを知らせない ignore を足す（計画に無い変更として専用のコミットにする）。#21 はあなたが閉じる
B. #21 を閉じるだけにする（同じ知らせがまた来うる）
C. 次の Intent に回す（#21 は開いたまま）
X. Other (please specify)

[Answer]: X（依頼者の答え: 「0.162.0 にする。」）

## Question T3（再実行した CI の失敗）
再実行した CI（run 36593277868 の2回目）では、frontendTest は全件成功しましたが、frontendCoverage（テストをもう一度流す段）で `src/features/preferences/PreferencesPage.test.tsx` の「drops the preview when leaving the screen without saving」が失敗しました（310 行、画面を移った直後に `<html>` の `data-theme` が消えていることを同期で確かめている）。1回目の失敗（ShellLayout.test.tsx）と同じく、表示の設定の反映を待たずに確かめるテストで、遅い CI で反映が後になると見立てています（未検証、手元では再現しない）。どうしますか？

A. 2つのテストを、反映を待って確かめる形（`waitFor`・`findByRole`）に直す。計画に無い変更として専用のコミットにし、verify を流して統合・push の後に CI で確かめる。直す前に、同じ形（操作の直後に表示の設定を同期で確かめる）のテストをほかにも洗い出して報告する
B. もう一度 CI を再実行して様子を見る
C. この Intent では直さず、受け入れた失敗として記録し、次の Intent で直す（team.md の「CI が失敗したら次に進む前に直す」との差を記録する）
X. Other (please specify)

[Answer]: A
