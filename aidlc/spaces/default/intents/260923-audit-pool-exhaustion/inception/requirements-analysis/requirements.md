# 要件定義 — 同時ログインでの接続プールの枯渇と監査の欠落（F2）の修正

## 意図の分析

- 依頼: 「同時10件のログインでコネクションプールが尽き、監査の書き込みが失敗する（F2）を直す」[desc]
- 種類: 不具合の修正（Workflow-selected scope: bugfix）[scope]。深さは Minimal。
- 目的: 想定の規模（同時にログインする利用者 10 名）で、ログインの監査の行が欠けないようにする。前の Intent の負荷の試験では、同時 10 件の成功のログインで、10 件すべての `LOGIN_SUCCEEDED` の行が欠けた。応答は 200 のままで、約 7.2 秒かかった。
- 原因（コードの調査の結果）: ログインの処理は、確定の後も業務の接続（1本目）を返さない。そのまま、監査の記録が同じスレッドで2本目の接続を同じプール `mastersmith-db`（上限 10）から借りる。同時の数がプールの上限に達すると、全員が1本目を持ったまま2本目を待つ。接続を借りる待ちの上限（5 秒）で、監査の書き込みが失敗する。同じ2本使いは、ログインの失敗とログアウトにもある（`aidlc/spaces/default/codekb/mastersmith2/architecture.md` の「対話の図」、`code-quality-assessment.md` の TD-1・TD-2）。
- 直し方の方針: 依頼者の判断で、プログラムは変えない。業務の接続プールの上限を大きくして直す。2本使いの形は残し、そのことで残る危険は既知の制約として記録する [Q1][Q2][FQ1][FQ3]。設定の変更はプール全体に効くが、同時実行で確かめるのはログインの成功だけとする。ログインの失敗とログアウトは未検証のまま残す（残る未確定の点を参照）。

## 機能要件

### FR1 接続プールの上限の既定値と設定

- **FR1.1** 内部DBの接続プール `mastersmith-db` の上限（`maximum-pool-size`）の既定値を、10 から 30 に変える [FQ1][FQ2]。
  - 合格の条件: 環境変数を設定せずに起動したアプリで、プールの上限が 30 である（Actuator の指標 `hikaricp_connections_max{pool="mastersmith-db"}` が 30、または同等の確認）。
- **FR1.2** プールの上限を環境変数で変えられるようにする。環境変数の名前は、既存の `MASTERSMITH_DB_*` にそろえて `MASTERSMITH_DB_MAXIMUM_POOL_SIZE` とする [Q3][FQ1][assumption]。
  - 結び付け方: 既存の `MASTERSMITH_DB_URL` などと同じく、`backend/src/main/resources/application.yaml` の `spring.datasource.hikari.maximum-pool-size` にプレースホルダーを書く（`${MASTERSMITH_DB_MAXIMUM_POOL_SIZE:30}`）。既定値 30 はプレースホルダーの既定値として持つ。Java のコードは加えない。
  - 合格の条件: 環境変数に値（例: 12）を設定して起動すると、プールの上限がその値になる。
- **FR1.3** README の環境変数の表と `.env.example` に FR1.2 の環境変数を加える。既定値（30）、意味、FR5 の制約への参照を書く。`.env.example` では、既存の任意の項目と同じくコメントにする。
  - 合格の条件: README の表と `.env.example` に、その環境変数の行がある。
- **FR1.4** プログラム（ログイン・ログアウト・トークンの更新・監査の処理）は変えない。変えるのは設定と、その設定を確かめるテスト・文書だけとする [Q1][Q2]。
  - 合格の条件: `backend/src/main/java/` の差分が無い（設定ファイル `application.yaml` の変更は除く）。

### FR2 修正の前の試しとその記録

- **FR2.1** 既定値を変える前に、FR3 の再現テストを次の2つの設定で実行し、結果を Code Generation の成果物に記録する [Q3]。
  - プールの上限 10（現在の値）: 失敗することを確かめる。監査の行の欠落、または接続の待ちの時間切れで失敗し、F2 が再現する。
  - プールの上限 30（FR1.1 の値）: 通ることを確かめる。
  - 合格の条件: 2つの設定での結果（通った・失敗した、監査の行の数、待ちの時間切れの有無、かかった時間）が記録されている。

### FR3 不具合を再現するテスト

- **FR3.1** 同時に N 件の成功のログインを流す結合テスト（`*IT`、Spring と組み込み H2 を起動する）を加え、N = 10 と N = 20 の両方で実行する [Q4]。
- **FR3.2** 各 N で、次の3つをすべて満たすことを合格とする [Q4]。
  - 応答がすべて 200 である
  - 監査の `LOGIN_SUCCEEDED` の行が、ログインの数（N 件）だけ記録されている（1件も欠けない）
  - 接続を借りる待ちの時間切れ（`CannotCreateTransactionException`、または ERROR「監査イベントの記録に失敗しました」）が1件も起きない
- **FR3.3** このテストは、FR1.1 の変更と同じコミットに含める（`aidlc/spaces/default/memory/project.md` の Mandated: 不具合を修正するときは、その不具合を再現するテストを同じコミットに含める）。
- **FR3.4** テストは、同時の開始をそろえる（全スレッドがそろってから一斉に要求を送る）。実行順や実時刻の待ち（`sleep`）には依存させない。テストの説明文は英語で書く（`team.md` の Testing Posture）。

### FR4 既存の決まりとテストの維持

- **FR4.1** 監査の既存の決まりを変えない。決まりは次の4つ（前の Intent の U4。確かめているテストは `code-quality-assessment.md` の「修正が守るべき既存の決まりとテスト」）。
  - 確定の後に記録する
  - 元の操作が取り消されたら記録しない
  - 要求と同じスレッドで記録し、トレースIDを一致させる
  - 監査の失敗で元の操作を失敗させない（ERROR 1件、再試行なし）
  - 合格の条件: `AuditAuthenticationEventsIT`・`AuditTraceIdIT`・`AuditRollbackIT`・`AuditWriteFailureIT`・`AuditWriteTimingIT`・`AuditEventRecorderTest`・`LoginServiceTest`・`LoginConcurrencyIT`・ArchUnit の境界のテストが、変更なしですべて通る。
- **FR4.2** 統合の前の1コマンドの検査（`./gradlew verify`）が、すべて通る。

### FR5 残る危険の記録

- **FR5.1** 次の点を、既知の制約として README に記録する [FQ3]。
  - 監査の記録は、業務の接続を持ったまま2本目を借りる。そのため、プールの上限（既定 30）以上の同時の要求がログイン・ログアウトに来ると、再び待ち合いが起きうる。
  - Tomcat の同時処理のスレッドの上限は未設定（既定 200）で、プールの上限より多い要求が同時に来うる。
  - 同時の数を増やす場合は、FR1.2 の環境変数で上限を上げる。その際は、メモリの上限とヘルスチェックへの影響を確かめる。
  - 合格の条件: README に、この制約の記述がある。

### FR6 配備した後の確認

- **FR6.1** 配備した後、いつものスモークテストを行う [Q6]。
- **FR6.2** 加えて、使い捨ての環境（`perf/README.md` の手順。仮の署名鍵・仮の利用者、終わったら消す）で k6 を使い、同時 10 件の成功のログインを流し直す [Q6]。
  - 合格の条件: 流したログインの数と、監査の `LOGIN_SUCCEEDED` の行の数が一致する。アプリのログに「Connection is not available」と ERROR「監査イベントの記録に失敗しました」が1件も無い。

## 非機能要件

- **NFR1 監査の欠落なし**: 同時 20 件までの成功のログインで、監査の行の欠落が 0 件、接続の待ちの時間切れが 0 件（FR3 の結合テストで確かめる）。
- **NFR2 資源の上限の内側**: 既定値 30 の設定で、アプリのコンテナのメモリの上限（`compose.yaml` の `mem_limit: 1g`）を変えずに、起動とスモークテストと FR6.2 の確認が通る。
- **NFR3 品質の下限の維持**: カバレッジの下限（行 80%・分岐 70%）を下げない。除外も増やさない（`team.md` の Testing Posture）。
- **NFR4 秘密情報**: 新しい設定と記録に秘密情報を含めない。プールの上限の値は秘密情報ではない。

## 制約

- プログラムの修正はしない。設定・テスト・文書だけを変える（FR1.4）。
- 内部DBは組み込み H2 のままとし、単一のインスタンスを前提とする（前の Intent のドメイン設計）。
- 内部DBを使うテストは、コンテナではなく本番と同じ組み込み H2 で行う（`team.md` の Testing Posture）。
- 負荷の試験は、配備した環境とは別の使い捨ての環境で行う（`project.md` の Testing Posture）。

## 前提

- [assumption] 環境変数の名前 `MASTERSMITH_DB_MAXIMUM_POOL_SIZE` は、既存の `MASTERSMITH_DB_URL`・`MASTERSMITH_DB_USERNAME`・`MASTERSMITH_DB_PASSWORD` にそろえた提案である。質問では決めていない。
- 同時 N 件のログインで待ち合わないためには、プールの上限が N より大きいこと（N+1 本以上）が要る。既定値 30 は、同時 20 件に余裕を持たせた値である [FQ2]。
- 結合テストのプールの設定は本番と同じ（`TestDatabase.register` は URL だけを差し替える）。そのため、FR1.1 の既定値が結合テストにもそのまま効く（`code-structure.md` の「テストの配置」）。

## 範囲の外

- 2本使いの形の解消（業務の接続を返してから監査を記録する、監査専用のプールを用意する、など）。依頼者の判断で今回は行わない [Q1]。
- Tomcat のスレッドの上限の設定 [FQ3]。
- ヘルスチェックが業務と同じプールを使う問題、同時の要求での誤った DOWN（F1）[Q5]。
- 高い負荷でのメモリの上限（F3）と、CPU 2 でのログインの遅さ（F4）。
- 接続を借りる待ちの上限（`connection-timeout` 5000ms）の変更。

## 残る未確定の点

- ログインの失敗（`LOGIN_FAILED`）とログアウト（`LOGGED_OUT`）の経路は、同時実行で確かめない。どちらも同じ2本使いの形のため、プールの上限の引き上げ（FR1）が同じように効くと見込んでいるが、未検証である。前の Intent の負荷の試験では、失敗のログインでプールが尽きなかった理由も分かっていない（`code-quality-assessment.md` の TD-2）。この Intent で同時実行を確かめるのは、成功のログイン（FR3・FR6.2）だけである。

- 既定値 30 で、組み込み H2（ファイル保存）が同時の接続を問題なく扱えるか。FR2.1 の試しと FR3 の結合テストで確かめる。
- 既定値 30 で、メモリとヘルスチェックに影響が出ないか。NFR2 と FR6.2 で確かめる。

## Sources

- [desc] Initial description: 同時10件のログインでコネクションプールが尽き、監査の書き込みが失敗する（F2）を直す
- [scope] Workflow-selected scope: bugfix
- [Q1]〜[Q6]、[FQ1]〜[FQ3]: `aidlc/spaces/default/intents/260923-audit-pool-exhaustion/inception/requirements-analysis/requirements-analysis-questions.md` の回答
- コードの調査の結果: `aidlc/spaces/default/codekb/mastersmith2/business-overview.md`・`architecture.md`・`code-structure.md`・`code-quality-assessment.md`
- F2 の観測: 前の Intent の負荷の試験（`aidlc/spaces/default/intents/260922-auth-audit-base/operation/performance-validation/test-results.md` の F2）

## Assumptions & Open Questions

- 環境変数の名前 `MASTERSMITH_DB_MAXIMUM_POOL_SIZE` は提案であり、質問では決めていない（前提を参照）。
- 既定値 30 での H2・メモリ・ヘルスチェックへの影響は、FR2.1・FR3・NFR2・FR6.2 で確かめる（残る未確定の点を参照）。
- ログインの失敗とログアウトの経路に同時実行で効くかは、未検証のまま残す（残る未確定の点を参照）。
