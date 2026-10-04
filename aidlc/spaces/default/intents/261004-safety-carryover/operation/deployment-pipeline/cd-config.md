# 配備の流れの構成（cd-config）

Intent 261004-safety-carryover（安全の機能の判断と持ち越し）の配備の流れです。

- **配備先**: これまでどおり、開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト名 `mastersmith`）。
- **配備の仕方**: 自動の配備は持たず、依頼者の承認のうえで手で配備する（`team.md`・`project.md` の Deployment）。
- **この文書の元**: この Intent の流れに CI Pipeline・Infrastructure Design の段が無い（CI の構成 `ci-config`・関門 `quality-gates`・基盤の仕様 `infrastructure-specification`・`cicd-pipeline` は作られていない）ため、前の Intent の配備の手順（`aidlc/spaces/default/intents/261003-user-admin-followup/operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`。以下「前の手順」）と、リポジトリの `.github/workflows/ci.yml`・`compose.yaml`・`Dockerfile`・README を正とし、今回の差だけを書く（`project.md` の Deployment の学び）。
- **決定**: `deployment-pipeline-questions.md`（Q1〜Q3 すべて A、まとめの確認 Looks correct）。

## 1. 配備する版と、今動いている版

| 項目 | 値 |
|---|---|
| 今動いている版 | イメージ `mastersmith:local`（`f124296eb7ca`、healthy）。前の Intent で配備した版。Build and Test の負荷の試験のために 2026-10-04T03:42:59Z〜03:55:18Z と 04:09:38Z〜04:13:45Z（UTC）に止め、同じコンテナで起動し直した |
| 配備する版 | `develop` の先頭 `532b39f`（コードは squash で統合した `b084c07`）。push の後の CI run 37176658773 が success（`./gradlew verify` success）。Build and Test の verify・E2E（この段で 153 件 passed） |
| 内部DB のスキーマ | 変わらない（V9 のまま。新しい移行のファイルは無い） |
| `.env` | 変わらない |
| `Dockerfile` | `FROM` にダイジェストを付けた（同じタグ `eclipse-temurin:25.0.4_7-jre-noble`、arm64・amd64 を含む index） |
| `compose.yaml` | 監視の2つのイメージ（profile `monitoring`）にダイジェストを付けた。`app`・Mailpit・見本の対象DB は変わらない |
| `application.yaml` | `logging.level` に Tomcat の `dispatcherServlet` のロガーを OFF にする1行（FR4.2）。イメージの中にあり、`.env` では変えない |
| 戻し先 | 配備の前に今のイメージに付けるタグ `mastersmith:pre-safety-carryover` |
| 見本の対象DB・Mailpit | `mastersmith-targetdb-postgres-1`・`mastersmith-mailpit-1`。今回は触らない |
| 実行環境 | colima の VM は CPU 4・メモリ 6GiB。`app` の上限は CPU 4・メモリ 2g（既定） |

## 2. 今回の変更と、配備への影響

| 要件 | 変更 | 配備への影響 |
|---|---|---|
| FR1（S2） | 起動のたびに、`.env` の初期管理者の利用者が停止中・印なし・パスワード不一致なら救い、作成と救済を監査に残す | **配備の起動で救済が働きうる**。働くと、初期管理者のパスワードが `.env` の値に戻り、ログイン中の端末はログインし直しになる。配備の前に確かめる（3節の順 3、Q1: A） |
| FR4（二重の ERROR） | Tomcat の `dispatcherServlet` のロガーを OFF | 応答を書き始めた後の例外や `/error` に回らない経路の例外のログも残らない（Code Generation のレビューの R-02、受け入れ済み） |
| FR7 | イメージのダイジェスト | アプリのイメージは同じタグの JRE を固定したダイジェストで作る |
| FR3・FR5・FR6 | テスト・文書・`team.md` だけ | 配備への影響は無い |

- `.env` の変更は無いため、`.env` の複写は取らない。
- 配備の前の k6 は流さない。Build and Test で、同じソースのイメージ（`mastersmith:safety-carryover`）で負荷の試験を済ませた（`project.md` の学び「試験済みのときは省く」）。

## 3. 流れ

| 順 | 段 | すること | 行う人 | 承認 |
|---|---|---|---|---|
| 1 | 関門 | アプリのソースに未コミットの変更が無いこと（`aidlc/` の下は除く）と、`develop` と `origin/develop` が一致することを確かめる。verify・CI・E2E はこの段と Build and Test の結果を正とし、流し直さない | AI | — |
| 2 | 戻し先のタグ | `docker tag mastersmith:local mastersmith:pre-safety-carryover`。付けた後に ID が `f124296eb7ca` で始まることを確かめる | AI | — |
| 3 | 救済の見込みの確かめ（Q1: A） | 依頼者に、初期管理者のパスワードを画面で変えた・止めた・印を外したことがあるかを尋ねる。アプリを止め（`docker compose stop app`）、内部DB を `~/.mastersmith-backup/`（権限 700）へ複写し（配備の前のバックアップを兼ねる）、複写を読み取り（`ACCESS_MODE_DATA=r`）で開いて、`.env` の初期管理者のメールアドレス（表示しない）の利用者の停止と印の有無だけを数える。救済が働く見込みなら、入れ替えの前に依頼者の了承を得る | AI と依頼者 | 依頼者の承認（アプリが止まるため） |
| 4 | 入れ替え | `docker compose --profile targetdb-postgres up -d --build app` で WAR とイメージを作り直し、アプリだけを入れ替えて healthy を待つ | AI | 順 3 の承認に含む |
| 5 | 確かめ | スモークテスト（`deployment-strategy.md` の 3節、Q2: A） | AI と依頼者 | ログインは監査に残るため、送る前に依頼者に伝える |
| 6 | `main` への取り込み（Q3: A） | `main` を `develop` の先頭へ fast-forward する。タグは付けない | AI | 依頼者の承認 |
| 7 | プッシュ | `origin` へ `develop` と `main` をプッシュする | 依頼者 | — |

- 配備の完了は、healthy とスモークテストのすべてが通ったとき（`team.md` の Deployment）。
- 順 3 でアプリを止めてから順 4 で入れ替えるまでの間、アプリは止まっている。順 3 で救済の見込みを依頼者と確かめる時間を含む。

## 4. CI と配備の前の E2E（この段で確かめた）

| 項目 | 結果 |
|---|---|
| CI | run 37176658773（`532b39f`、2026-10-04T04:19:12Z）success。ジョブ「./gradlew verify」success、「リリースに WAR を添付する」skipped（タグの push ではないため） |
| E2E | `./gradlew e2eTest`（`caffeinate -i`、Mailpit 起動済み）: 153 件 passed（6.8 分）、BUILD SUCCESSFUL |
| E2E の救済の WARN | 件数では数えられなかった。Playwright の設定（`frontend/playwright.config.ts` の `webServer.stdout: 'ignore'`）で E2E のアプリの標準出力を捨てているため。E2E は毎回一時の内部DB に初期管理者を新しく作るため救済は働きえず、初期管理者のログインを含む 153 件が通ったことで裏付けた |

## 5. 決まりと上流との差（記録）

| 決まり・上流 | 今回の扱い | 理由 |
|---|---|---|
| 要件の FR8.1（前の手順を正とし今回の差だけを書く） | そのとおり | — |
| 要件の FR8.2（配備の前に救済の見込みを確かめる） | 3節の順 3 | Q1: A |
| 要件の NFR4・Code Generation の D5・D7、レビューの R-05（配備の前の E2E で救済の WARN 0 件を件数で確かめる） | E2E は通ったが、WARN の件数は数えられなかった（4節） | E2E のアプリの標準出力を捨てる設定のため。救済が働きえない条件で裏付けた |
| `team.md` の Deployment「リリース時に `main` にタグを付ける」 | `main` へは取り込むが、タグは付けない（Q3: A） | 前の Intent と同じ。`v*` のタグの push で公開の GitHub のリリースが作られるため、配備先が決まったときに改めて決める |

## 6. 成果物と版の識別

- 成果物は、フロントエンドの `dist` を同梱した実行可能 WAR から作るイメージ `mastersmith:local`（`team.md` の Deployment）。
- 版はコミットのハッシュで識別し、イメージの ID とともに Deployment Execution の記録に残す。

## Sources

- 前の手順: `aidlc/spaces/default/intents/261003-user-admin-followup/operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`
- `deployment-pipeline-questions.md`（決まっていること、Q1〜Q3、まとめの確認）
- `inception/requirements-analysis/requirements.md`（FR1・FR4・FR8・NFR4）
- `construction/code-generation/code-summary.md`・`code-generation-questions.md`（D5・D7）
- `construction/build-and-test/test-results.md`・`build-and-test-summary.md`（verify・負荷の試験・Q3・Q6）
- `.github/workflows/ci.yml`・`compose.yaml`・`Dockerfile`・`backend/src/main/resources/application.yaml`・`frontend/playwright.config.ts`・`README.md`（読むだけ）
- `aidlc/spaces/default/memory/team.md`・`project.md`（Way of Working・Deployment）

## Assumptions & Open Questions

None.
