# 配備の流れの構成（cd-config）

Intent 261027-perf-targets-review（性能・資源の目標の見直し、k6 の線と README の直し、Dependabot #80 の見送り）の配備の流れです。

- **配備先**: これまでどおり、開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト名 `mastersmith`）。
- **配備の仕方**: 自動の配備は持たず、依頼者の承認のうえで手で配備する（`team.md`・`project.md` の Deployment）。本番配備の承認者は依頼者。
- **この文書の元**: 前の Intent の配備の手順（`aidlc/spaces/default/intents/261025-deps-perf-carryover/operation/deployment-pipeline/`、その元は `261016-business-data-views`）と、リポジトリの `Dockerfile`・`compose.yaml`・README を正とし、今回の差だけを書く（`project.md` の Deployment の学び）。この Intent の流れには CI Pipeline・Infrastructure Design の段が無い（bugfix）ため、既にあるものを検査した結果を根拠にする。
- **決定**: `deployment-pipeline-questions.md`（Q1: A、Q2: A、Q3: B、Q4: A、まとめの確認 Looks correct）。

## 1. 配備する版と、今動いている版

| 項目 | 値 |
|---|---|
| 今動いている版 | イメージ `mastersmith:local`（`sha256:3075065a2653…`、WAR の SHA-256 は `ff50498b…`、healthy）。前の Intent で配備した版。Build and Test の測り直しのために止め、`docker compose up -d app` で起動し直した（`evidence/remeasure-results.md` 7・8節）。同じ ID に試験用のタグ `mastersmith:perf-targets-review` も付いている |
| 配備する版 | `develop` の先頭 `154c27b`（`origin/develop` は `ab0d806`）。`main`（`94538ef`）からの `aidlc/` の外の差は `perf/`（README・`dsl-mixed-memory.sh`・`k6/scenarios.js` と新しいテスト 2 本）と `.github/dependabot.yml` の 6 ファイルだけ |
| 変わるもの | **アプリの中身は変わらない**。`backend/src/main`・`frontend/src`・`Dockerfile`・`compose.yaml`・内部DB の移行・依存の版はどれも `main` と同一。手元の WAR の SHA-256 は動いているイメージの中の WAR と同じ（`ff50498b…`）。作り直しても同じになる見込みで、違えば（ビルドの再現性の差）理由を記録して依頼者に諮る |
| 内部DB のスキーマ | **変更なし**（V13 のまま）。H2 の版も変わらない |
| `.env` | **変更なし**。値は開かない |
| 見本の対象DB | `mastersmith-targetdb-postgres-1`（`postgres:18.6`）。Build and Test の E2E のあと上乗せ無しで作り直し済みで、PORTS は `5432/tcp` だけ（`127.0.0.1:15432` は閉じている）。今回は作り直さない |
| 戻し先 | 入れ替えの前に今のイメージに付けるタグ `mastersmith:pre-perf-targets-review`（`3075065a2653`） |
| Mailpit | 動いている（`axllent/mailpit:v1.32.0`）。作り直さない |
| Dependabot | 開いている PR は #80 の 1 件（`0.163.2-386`、linux/386 だけ）。`.github/dependabot.yml` の `ignore` に理由と外す時期を書いて見送る（2e09e69）。取り込む更新は無い |

## 2. 今回の変更と、配備への影響

| 変更 | 配備への影響 |
|---|---|
| `perf/`（k6 の線、README、`dsl-mixed-memory.sh`、テスト 2 本） | 配備する WAR・イメージには入らない（手元で試験を流す道具）。アプリの動きは変わらない |
| `.github/dependabot.yml` | 配備と無関係（GitHub の Dependabot の設定）。`develop` の push で反映される |
| `aidlc/` | 記録だけ |

アプリの中身が変わらないため、配備は「手順どおりに作り直した版を入れ替え、起動と最小限の動作を確かめる」ものになる。依頼者の決定（Q1: A、定義の段階の Q5: B）で、変わらなくても通常どおり行う。

## 3. 流れ

| 順 | 段 | すること | 行う人 | 承認 |
|---|---|---|---|---|
| 1 | 関門 | アプリのソースに未コミットの変更が無いこと（`aidlc/` の下は除く）と、`git diff --stat main HEAD -- . ':!aidlc'` が `perf/`・`.github/dependabot.yml` の 6 ファイルだけであることを確かめる。verify・E2E 256 件・CI は Build and Test の結果を正とし流し直さない（3.1） | AI | — |
| 2 | WAR の作り直し | `./gradlew :backend:bootWar --rerun-tasks` で WAR を作り直し、SHA-256 を記録して `ff50498b…` と比べる。中に画面のビルドと読み取りのプールの既定 10 があることを中身で確かめる（`Dockerfile` は手元の WAR を写すだけ。`project.md` の学び）。アプリが止まる時間を短くするため、停止の前に行う。**シェルに `MASTERSMITH_IMAGE_TAG` が残っていないこと**を確かめる（`env \| grep -c` が 0） | AI | — |
| 3 | 戻し先のタグ | `docker tag mastersmith:local mastersmith:pre-perf-targets-review`。付けた後に ID が `3075065a2653` で始まることを確かめる | AI | — |
| 4 | 救済の見込み | 依頼者に、前の配備の後に初期管理者のパスワードを画面で変えた・止めた・印を外したことがあるかを尋ねる。配備の後の複写で `INITIAL_ADMIN_RESCUED` の件数を数えて裏付ける | AI と依頼者 | — |
| 5 | 止める | 監査の数え始めの時刻 T0 を控え、`docker compose stop app`。スキーマ・`.env` の変更が無いため、配備の前の内部DB の複写は取らない | AI | 依頼者の承認（アプリが止まるため。順 5〜7 をまとめて承認） |
| 6 | 入れ替え | `docker compose --profile targetdb-postgres up -d --build app` で順 2 の WAR からイメージを作り直してアプリだけを入れ替える（見本の PG は作り直さない） | AI | 順 5 の承認に含む |
| 7 | 健全性 | healthy を待ち、`/actuator/health` の 200 を確かめる。起動のログで Flyway の移行が 0 件・現在の版が 13 であること、WARN が前の版から出ているもの（Flyway の H2 の版・Bean の案内）のほかに無いことを確かめる（`deployment-strategy.md` の S1・S2） | AI | — |
| 8 | スモークテスト（Q2: A） | `deployment-strategy.md` の S1〜S5。ロールは作らず、初期管理者の今の割り当てを使う。ログインは監査に残るため前もって伝える | AI と依頼者 | 監査に残る操作は前もって伝える |
| 9 | 配備の後の複写 | アプリを止めて内部DB を `~/.mastersmith-backup/`（権限 700）へ `…-after-perf-targets-review.tgz`（権限 600）として複写し、すぐ起動し直す（約 10 秒、依頼者に伝えてから）。複写を読み取りで開き、T0 以降の `audit_events` を種類と結果ごとに数える（S6）。個人に関する値は表示しない | AI | 依頼者に伝えてから |
| 10 | 試験用タグの片付け（Q4: A） | `docker rmi mastersmith:perf-targets-review`（タグだけ。`mastersmith:local` と `pre-perf-targets-review` のイメージ本体は残る） | AI | — |
| 11 | 配備の記録のコミット | Deployment Execution の記録をコミットする | AI | 依頼者の承認（コミットの前に提案） |
| 12 | `main` への取り込み | 取り込みの前に、前の取り込み（`94538ef`）の後のすべてのコミットの CI を試行ごと（`gh run view --json attempt,jobs`）に確かめ、再実行で通ったものは 1 回目の失敗とそのテストを記録する。WAR を作ったコミットと `main` の先頭の差が `aidlc/`・`perf/`・`.github/dependabot.yml` だけであることを確かめて記録する。`main` が `develop` の祖先であることを確かめ、`main` を `develop` の先頭へ fast-forward する。タグは付けない | AI | 依頼者の承認 |
| 13 | 取り込みの結果の追記 | 取り込みと push の結果を、`main` の後の `develop` だけのコミットで追記する | AI | 依頼者の承認（コミットの前に提案） |
| 14 | プッシュと PR #80 | `origin` へ `develop` と `main` をプッシュするのは依頼者。PR #80 は依頼者が GitHub の画面で、5節の理由で閉じる（Q3: B） | 依頼者 | — |

- 配備の完了は、healthy とスモークテストのすべてが通ったとき（`team.md` の Deployment）。
- アプリが止まるのは順 5 から順 7 の healthy まで（見込み約 1〜2 分）と、順 9 の複写（約 10 秒）。
- 開いているタブは、順 7 の後に読み込み直す。
- どの段のコミットも、行う前に依頼者に提案して承認を得る（`project.md` の Change Control）。

### 3.1 配備の前の検査（流し直さないもの）

| 検査 | 結果 | 出典 |
|---|---|---|
| `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` | 通過（21 分 28 秒、単体 2,612・結合 1,375〈SKIPPED 10。DB の種類別の前提〉、失敗 0） | `construction/build-and-test/test-results.md` 2節 |
| E2E | 256 件 passed（13.0 分） | 同 3節 |
| CI | `ab0d806` が 1 回目の試行で success（検査の job 31 分 56 秒）。作業の 5 コミットは `develop` への統合（`154c27b`）の後、依頼者が push したときに動く | 同 4節 |
| k6（負荷の試験） | **流さない**（3.2） | — |

### 3.2 配備の前の k6 を流さないこと（`project.md` の決まりとの差）

- この Intent の負荷の試験は、配備と同じ WAR（`ff50498b…`）・同じイメージ（`3075065a2653`）で Build and Test に測り直し済み。配備し直す WAR もアプリのコードが同じなので、同じソースのイメージを試験済みの扱いにでき、配備の前の k6 は流さない。
- 測り直しの結果と配備後の動きは、同じ中身のイメージを同じ上限（メモリ 2g・CPU 4）で動かすため、配備によって変わらない。

## 4. 配備の前の内部DB の複写を取らないこと

- スキーマの変更も `.env` の変更も無く、前の版のイメージでそのまま動くため、配備の前の複写は取らない（要件 NFR5、前の Intent と同じ扱い）。
- 配備の後の複写（順 9）は、監査の件数を数えるために必ず取り、配備の後のバックアップを兼ねる。置き場は `~/.mastersmith-backup/`（権限 700、複写は 600）で、`mktemp -d` は使わない（colima の VM から見えない。`project.md` の学び）。

## 5. 決まりと上流との差（記録）

- **FR4.1 と Q5: B**: 要件 FR4.1 は、取り込む更新が無くアプリの中身が変わらないときは配備を行わず「不要」と記録して進めてよいと書く。定義の段階の Q5: B と、この段の Q1: A（依頼者の決定）で、通常どおり配備する。要件の文は書き換えない。配備しても得られる確かめは、手順どおりの再ビルドで同じ WAR になること、入れ替え後の起動、`main` と配備した版がそろうことにとどまる。
- **配備の前の k6**: 3.2 のとおり。
- **PR #80 を閉じる理由の文面**（依頼者が GitHub の画面で閉じるときに貼る）:
  「`0.163.2-386` は linux/386 だけのイメージで、arm64・amd64 が無く（`docker buildx imagetools inspect` で確認）、手元の PC で動かないため取り込みません。通常のタグ `0.163.2` は複数アーキテクチャの index で別物です。`.github/dependabot.yml` の docker-compose の `ignore` に理由と外す時期を記録しました。」
- **試験用タグ**: Build and Test の測り直しで使った `mastersmith:perf-targets-review` は、順 10 で消す（Q4: A）。
- **E2E**: 配備の前の E2E は Build and Test の 256 件で満たす（流し直さない）。

## 6. 成果物と版の識別

- 成果物は順 2 で作り直した実行可能 WAR と、それを写したイメージ `mastersmith:local`。版はコミットのハッシュ（アプリのコードは `main` の `94538ef` と同一、配備を行う記録は `154c27b` 以降）で識別し、どのコミットをリリースしたかは `main` の先頭と Deployment Execution の記録で追う（`team.md` の Deployment）。

## Sources

- 前の手順: `aidlc/spaces/default/intents/261025-deps-perf-carryover/operation/deployment-pipeline/`（`cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`）
- 質問と答え: `aidlc/spaces/default/intents/261027-perf-targets-review/operation/deployment-pipeline/deployment-pipeline-questions.md`
- この Intent の要件・Code Generation・Build and Test: `inception/requirements-analysis/requirements.md`（FR3・FR4・NFR5）、`construction/code-generation/code-summary.md`、`construction/build-and-test/build-and-test-summary.md`・`test-results.md`・`cross-unit-traceability.md`・`evidence/remeasure-results.md`
- リポジトリ: `Dockerfile`、`compose.yaml`、`.github/dependabot.yml`、`git diff --stat main develop`
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment・Way of Working）、`project.md`（Deployment・Corrections の学び）

## Assumptions & Open Questions

- [assumption] `.env` の対象DB の項目と書き込みの 2 項目は前の Intent の配備のまま（`.env` は開いていない）。
- [assumption] 作り直した WAR の SHA-256 は `ff50498b…` と同じになる。違うときは理由（ビルドの再現性の差）を記録し、依頼者に諮ってから入れ替える。
- [assumption] 初期管理者の状態は前の配備のまま。救済の見込みは順 4 で依頼者に確かめる。
