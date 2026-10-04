# 配備の流れの構成（cd-config）

Intent 261003-user-admin-followup（利用者の管理の後始末、第1の束）の配備の流れです。

- **配備先**: これまでどおり、開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト名 `mastersmith`）。
- **配備の仕方**: 自動の配備は持たず、依頼者の承認のうえで手で配備する（`team.md`・`project.md` の Deployment）。
- **この文書の元**: この Intent の流れに CI Pipeline・Infrastructure Design の段が無いため、前の Intent の配備の手順（`aidlc/spaces/default/intents/260930-user-admin/operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`。以下「前の手順」）を正とし、今回の差だけを書く（`project.md` の Deployment の学び）。
- **決定**: `deployment-pipeline-questions.md`（Q1 A・Q2 A と、まとめの確認 Looks correct）。

## 1. 配備する版と、今動いている版

| 項目 | 値 |
|---|---|
| 今動いている版 | イメージ `mastersmith:local`（`f386b55f16a8`、healthy）。前の Intent で配備した版で、アプリのソースは `b126bdc`（前の Intent の `operation/deployment-execution/deployment-log.md`）。T1 の負荷の試験のために 2026-10-03T23:32:27Z〜23:53:57Z（UTC）に止め、同じコンテナで起動し直した |
| 配備する版 | `develop` の先頭。アプリのソースは `767f1cd`（README の直し）までの版。コードの最後の変更は `d288e0e`、統合の後の `a1b41e7` で clean 付きの verify が通り、push の後の CI run 37162114053 が success（`construction/build-and-test/test-results.md`）。その後の `c50e64c`（`perf/README.md`）と `767f1cd`（`README.md`）は文書だけで、どちらも統合の前に verify を通した |
| 内部DB のスキーマ | 変わらない（V9 のまま） |
| `.env`・`compose.yaml`・`Dockerfile` | 変わらない（`b126bdc` からの差を `git diff --name-only` で確かめた） |
| `application.yaml` | 1行足した: `logging.level` の `org.hibernate.orm.jdbc.error: OFF`（S1。一意の制約の違反の文に重なったメールアドレスが入るため）。イメージの中にあり、`.env` では変えない |
| make-you-chic-ui | 固定先 `3d9521a` → `e82b651`（専用のコミット `a272fd3`）。イメージを作るときにだけ効く |
| 戻し先 | 配備の前に今のイメージに付けるタグ `mastersmith:pre-user-admin-followup` |
| 見本の対象DB・Mailpit | `mastersmith-targetdb-postgres-1`・`mastersmith-mailpit-1`。今回は触らない |
| 実行環境 | colima の VM は CPU 4・メモリ 6GiB。`app` の上限は CPU 4・メモリ 2g（既定） |

## 2. 今回の変更と、配備への影響

| 要件 | 変更 | 配備への影響 |
|---|---|---|
| FR1（K1） | 確かめの表示を閉じた後のフォーカスを行の「操作」へ戻す（`finalFocusRef`）。招待の画面で、閉じ終わってから読み直す（G1） | イメージの中の画面が変わる |
| FR2（K2） | 行の「操作」のメニューを `bottom-end` に | 同上 |
| FR3（K3） | 送信中は言語の選択を押せなくする | 同上 |
| FR8（S1） | `org.hibernate.orm.jdbc.error` を OFF。想定外の誤りの ERROR で一意の違反はクラスの名前だけ | 一意の違反以外の SQL の失敗についても、Hibernate のこのロガーの WARN（`SQL Error`）が出なくなる（Code Generation のレビュー R-03。受け入れ済み）。障害の調べ方は `application.yaml` のコメントのとおり |
| FR4〜FR7 | テスト・負荷の台本・手順書だけ | 配備への影響は無い |

- `.env` の変更は無いため、`.env` の複写は取らない。
- 配備の前の k6 は流さない。Build and Test の T1 で、同じアプリのソースのイメージ（`mastersmith:user-admin-followup`、`a1b41e7`）で負荷の試験を済ませた（`project.md` の学び「試験済みのときは省く」）。

## 3. 流れ

前の手順の 3節の流れのうち、今回の差は次のとおりです（バックアップと `main` への取り込みの扱いを含む）。

| 順 | 段 | すること | 行う人 | 承認 |
|---|---|---|---|---|
| 1 | 関門 | アプリのソースに未コミットの変更が無いこと（`aidlc/` の下は除く）と、`develop` と `origin/develop` の差を確かめる。`develop` の push がまだなら依頼者に頼む。verify・E2E・CI は Build and Test の結果を正とし、流し直さない | AI | — |
| 2 | 戻し先のタグ | `docker tag mastersmith:local mastersmith:pre-user-admin-followup`。付けた後に ID が `f386b55f16a8` で始まることを確かめる | AI | — |
| 3 | 入れ替え | `docker compose --profile targetdb-postgres up -d --build app` で WAR とイメージを作り直し、アプリだけを入れ替えて healthy を待つ（Q1: A） | AI | 依頼者の承認（アプリが止まるため） |
| 4 | 確かめ | スモークテスト（`deployment-strategy.md` の 3節） | AI と依頼者 | ログインは監査に残るため、送る前に依頼者に伝える |
| 5 | `main` への取り込み | `main` を `develop` の先頭へ fast-forward する。タグは付けない | AI | 依頼者の承認 |
| 6 | プッシュ | `origin` へ `develop` と `main` をプッシュする | 依頼者 | — |

- バックアップと戻しの練習の段は置かない（スキーマと `.env` の変更が無いため。`project.md` の学び）。
- 配備の完了は、healthy とスモークテストのすべてが通ったとき（`team.md` の Deployment。要件の確かめの指摘 R-02 の完了の基準）。

## 4. 決まりと上流との差（記録）

| 決まり・上流 | 今回の扱い | 理由 |
|---|---|---|
| 要件の FR9.1（前の手順を正とし今回の差だけを書く） | そのとおり | — |
| 要件の確かめの指摘 R-02（配備の完了の基準が無い） | 3節と `deployment-strategy.md` の 3節に完了の基準を書いた | 要件の承認の場で、配備の段で具体にするとした |
| Build and Test の承認の場の決定（`README.md` の古い固定先の記述をこの段で直す） | この段で直し、短命のブランチ `fix/261003-readme-pin` で verify を通してから `develop` へ squash で統合した（`767f1cd`） | 統合の形は、この段のまとめの確認（Looks correct）で受け入れられた |
| `team.md` の Deployment「リリース時に `main` にタグを付ける」 | `main` へは取り込むが、タグは付けない | 前の Intent と同じ。`v*` のタグの push で公開の GitHub のリリースが作られるため、配備先が決まったときに改めて決める |

## 5. Build and Test から引き継いだもの

| ID | 扱い |
|---|---|
| FR9.1（Deferred） | この段と Deployment Execution で扱う |
| FR4.2-c（Unverified、BUSY の L3・L4 の結び付き） | 依頼者の決定で次の Intent へ持ち越し。配備では扱わない |
| Tomcat の ERROR 239 件 | 依頼者の決定で次の Intent へ持ち越し。配備では扱わない（負荷の試験の下でだけ出たもの） |

## 6. 成果物と版の識別

- 成果物は、フロントエンドの `dist` を同梱した実行可能 WAR から作るイメージ `mastersmith:local`（`team.md` の Deployment）。
- 版はコミットのハッシュで識別し、イメージの ID とともに Deployment Execution の記録に残す。

## Sources

- 前の手順: `aidlc/spaces/default/intents/260930-user-admin/operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`、`operation/deployment-execution/deployment-log.md`
- `deployment-pipeline-questions.md`（決まっていること、Q1・Q2、まとめの確認）
- `inception/requirements-analysis/requirements.md`（FR9.1）、要件の確かめの記録（R-02）
- `construction/code-generation/code-summary.md`・`gate-decisions.md`（コミットの一覧、R-03）
- `construction/build-and-test/test-results.md`・`build-and-test-summary.md`・`t1-load-test-results.md`（verify・CI・T1、承認の場の決定）
- `README.md`（コンテナでの起動と確認、戻し方）、`compose.yaml`・`Dockerfile`・`backend/src/main/resources/application.yaml`（読むだけ）
- `aidlc/spaces/default/memory/team.md`・`project.md`（Way of Working・Deployment）

## Assumptions & Open Questions

None.
