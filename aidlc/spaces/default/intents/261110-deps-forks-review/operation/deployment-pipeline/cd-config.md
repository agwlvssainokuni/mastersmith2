# 配備の流れの構成（cd-config）

Intent 261110-deps-forks-review の配備の流れです。この Intent は bugfix で、依存・道具・イメージを最新にし、CI の `-PitForks` の既定の値を確定しました。サーバーと画面の依存が変わり WAR の中身が変わるため、通常どおり配備します（要件 FR6.1）。

- **配備先**: これまでどおり、開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト名 `mastersmith`）。
- **配備の仕方**: 自動の配備は持たず、依頼者の承認のうえで手で配備する（`team.md`・`project.md` の Deployment）。本番配備の承認者は依頼者。
- **この文書の元**: 前の配備の手順（`aidlc/spaces/default/intents/261108-ci-checks-release/operation/deployment-pipeline/`）と、リポジトリの `Dockerfile`・`compose.yaml`・`docs/deployment.md`・`docs/internal-db.md`・`docs/operations.md` を正とし、今回の差だけを書く（`project.md` の Deployment の学び）。
- **上流**: この Intent の流れには CI Pipeline と Infrastructure Design の段が無い（5節）。上流は Build and Test の記録（`construction/build-and-test/`）と要件（FR6）である。
- **決定**: `deployment-pipeline-questions.md`（Q1〜Q3 すべて A）。

## 1. 配備する版と、今の状態

| 項目 | 値 |
|---|---|
| 今の状態 | アプリのコンテナ `mastersmith-app-1` は止まっている（Exited (143)、この Intent の Code Generation の前から）。イメージは前の配備の `mastersmith:local`（`0d4b6e8866a9`、261108-ci-checks-release で配備した `a9ddc077` の版）。見本の PG・Mailpit・lgtm・collector も止まっている。内部DB のボリューム `mastersmith_mastersmith-data` は残っている |
| 配備する版 | Q1: A で、配備の前に `develop` を `main` へ fast-forward した後の `main` の先頭（今は `b8d61840` の見込み。取り込みのときの `develop` の先頭）。前の配備の `a9ddc077` の後のコミット |
| 変わるもの（配備に入る） | `backend/gradle.lockfile`: networknt json-schema-validator 3.0.6 → 3.0.9・Jackson（core・databind・bom）3.1.7 → 3.1.8・itu 1.14.0 → 1.17.0。`frontend/package.json`・`package-lock.json`: 画面の依存（i18next 27・react-i18next 18 ほか）。`frontend/src/features/tables/DateTimeField.tsx`・`SearchPanel.tsx`: i18next 27 の型に合わせて文言の関数を包んだだけ（業務データの検索の欄の日時のヒントと誤りの文言） |
| 変わらないもの | `backend/src/main`・`Dockerfile`・`.env.example`（前の配備から差 0）。Spring Boot 4.1.2・H2 2.4.240・JRE のイメージ（`eclipse-temurin:25.0.5_8-jre-noble@sha256:a98128a5…`） |
| 配備に入らない変更 | `compose.yaml`（Mailpit・lgtm・collector・postgres・mysql の版とダイジェスト。Build and Test で作り直して当て済み）・`docker/perf/compose.yaml`・`.github/`・`gradle/`（Wrapper 9.9.1・Spotless・`libs.versions.toml` の検査の道具）・`frontend/playwright.config.ts`・テスト・`docs/`・`perf/`・`aidlc/` |
| 内部DB のスキーマ | 変更なし（最後の移行は V14 のまま） |
| `.env` | 変更なし |
| 監査の種類 | 変更なし |
| 戻し先 | 入れ替えの前に前の配備のイメージに付けるタグ `mastersmith:pre-deps-forks-review`（`0d4b6e8866a9`） |
| 片付けの候補（FR6.2） | 7節の一覧 |

## 2. 今回の変更と、配備への影響

| 変更 | 配備への影響 |
|---|---|
| Jackson 3.1.8（JSON の読み書き） | すべての API の JSON が通る。S2 の 401 の Problem Details と、S3 のログインの後の画面の表示で確かめる |
| networknt 3.0.9（DSL の JSON Schema の検証） | 起動のとき、適用中の DSL を読み直す経路で使う。S1 の起動のログ（ERROR 0 件）と、S3 の DSL の状態・業務データの一覧が開けることで確かめる。DSL の投入はしない（監査に残るため） |
| 画面の依存（i18next 27 ほか） | 画面のファイル（JavaScript）が変わる。WAR の中の画面のファイルの名前が `frontend/dist` と同じことを確かめる。S3 で日本語と英語の両方の文言を見る（Q2: A） |
| 日時の文言の包み直し | 業務データの検索の欄の日時のヒントと誤りの文言。S3 で見る |
| アプリのコンテナが止まっている | 入れ替えの操作（`docker compose up -d --build --no-deps app`）でコンテナを作り直す。止める操作は要らず、複写はアプリが止まっているうちに取る |

## 3. 流れ

| 順 | 段 | すること | 行う人 | 承認 |
|---|---|---|---|---|
| 1 | 関門と `main` への取り込み（Q1: A） | アプリのソースに未コミットの変更が無いこと（`aidlc/` の下は除く）を確かめる。`git fetch` の後に台本 A2（`node scripts/ci-attempts.mjs`）で、前の取り込み `a9ddc077` の後のすべてのコミットの CI を試行ごとに確かめ、先頭の CI が通っていることを確かめる（走っている最中なら終わりを待つ）。verify・E2E は Build and Test の結果を正とし、流し直さない（3.1）。承認を得て `git switch main && git merge --ff-only develop` を行い、`develop` に戻る | AI | 依頼者の承認（`main` の取り込み）。プッシュは依頼者 |
| 2 | WAR の作り直し | `./gradlew :backend:bootWar --rerun-tasks` で WAR を作り直し、SHA-256 を記録する。WAR の中の `static/` の画面のファイルの名前が今の `frontend/dist` と同じこと、`WEB-INF/lib` に `json-schema-validator-3.0.9`・`jackson-databind-3.1.8`・`itu-1.17.0` があり前の版（3.0.6・3.1.7・1.14.0）が無いことを確かめる（`Dockerfile` は手元の WAR を写すだけ。`project.md` の学び）。シェルに `MASTERSMITH_IMAGE_TAG` が残っていないこと（`env \| grep -c MASTERSMITH_IMAGE_TAG` が 0）を確かめる | AI | — |
| 3 | 配備の前の環境の確かめ | Mailpit（`docker compose --profile mail up -d mailpit`）と見本の PG（`docker compose --profile targetdb-postgres up -d targetdb-postgres`。E2E 用のポートの上乗せは使わない）を起動する。台本 A1 `node scripts/ops-readonly-check.mjs --war backend/build/libs/mastersmith.war` で、動いているコンテナの版とダイジェストが `compose.yaml` と一致し、WAR の SHA-256 が順 2 と一致することを確かめる。`.env` は項目の名前の数だけ（値は出ない） | AI | — |
| 4 | 戻し先のタグ | `docker tag mastersmith:local mastersmith:pre-deps-forks-review`。付けた後に ID が `0d4b6e8866a9` で始まることを確かめる | AI | — |
| 5 | 救済の見込み | 依頼者に、前の配備の後に初期管理者のパスワードを画面で変えた・止めた・印を外したことがあるかを尋ねる。順 6 の複写の数えで裏付ける | AI と依頼者 | — |
| 6 | 配備の前の複写 | T0 を控える。アプリのコンテナが止まっていることを確かめる（動いていれば `docker compose stop app`）。内部DB を `~/.mastersmith-backup/`（権限 700）へ `…-pre-deps-forks-review.tgz`（権限 600）として複写する（4節）。台本 A3 で数える（4.1。Q3: A で `.env` を読むことの許しを得た） | AI | 依頼者の承認（アプリを起動して内部DB を使い始めるため。順 6〜8 をまとめて承認） |
| 7 | 入れ替え | `docker compose up -d --build --no-deps app`。入れ替えの後に `docker inspect` でコンテナのイメージ ID と `mastersmith:local` の ID が一致するかを確かめ、食い違えば `--force-recreate --no-deps app` で作り直す（`project.md` の学び）。見本の PG・Mailpit は作り直さない | AI | 順 6 の承認に含む |
| 8 | 健全性 | `deployment-strategy.md` の S1（healthy・`/actuator/health` の 200・移行が当たらないこと・起動のログ・コンテナの中の WAR の SHA-256） | AI | — |
| 9 | 4.1 の結果での合わせ直し | 4.1 の数が前提と違うとき（初期管理者の状態など）は、スモークテストの前に依頼者と合わせ直し、監査の期待を直す | AI と依頼者 | 依頼者の決定 |
| 10 | スモークテスト（Q2: A） | `deployment-strategy.md` の S2〜S4。AI の未認証の確かめとアプリのログを先に行い、依頼者には Chrome でのログインと画面の見た目だけを頼む（`project.md` の Deployment）。監査に残るのはログイン（とログアウト）だけ。ログインの前に伝える | AI と依頼者 | 監査に残る操作は前もって伝える |
| 11 | 配備の後の複写と環境の確かめ（Q2・Q3: A） | 依頼者に伝えてから、アプリを止めて内部DB を `…-after-deps-forks-review.tgz`（権限 600）として複写し、すぐ起動し直す（約 10 秒）。起動し直した後も healthy とイメージ ID の一致を確かめる。台本 A3 を `--since <T0> --set basic,audit` で流し、監査の種類と結果ごとの件数を `deployment-strategy.md` の 3.1 と照らす（S5）。台本 A1 を流し直し、アプリのコンテナを含めて版とダイジェストを確かめる | AI | 依頼者に伝えてから |
| 12 | 配備の記録のコミット | Deployment Execution の記録をコミットする | AI | 段の成果物の初稿と段の承認の時は尋ねずに行う（`aidlc/` の下だけ） |
| 13 | 古いイメージの片付け（FR6.2） | 片付けの前の `docker system df` を記録する。7節の一覧のイメージを使うコンテナが 0 件（`docker ps -a --filter ancestor=<ID>` が 0 件）であることを確かめ、消す一覧を示して承認を得てから `docker rmi` で消す。消した後に `docker system df` を記録する。一覧の外のイメージには触れない | AI | 依頼者の承認（消す一覧） |
| 14 | プッシュ | `main`（順 1 の取り込み）と、配備の記録のコミットを積んだ `develop` を `origin` へプッシュするのは依頼者。`main` は順 1 の先頭のまま動かさない（記録のコミットは `develop` だけ） | 依頼者 | — |

- 配備の完了は、healthy とスモークテストのすべてが通ったとき（`team.md` の Deployment）。
- アプリはもともと止まっているため、順 6〜8 の間に止まるものは無い。順 11 の複写の間に約 10 秒止まる。
- 開いているタブがあれば、順 8 の後に読み込み直す（新しい画面のファイルを読むため）。
- 戻しの練習は行わない。移行と `.env` の変更が無く、戻しはイメージだけのため（`project.md` の Deployment の 2026-11-07 の決まり）。
- 順 13 の片付けは、配備が完了してから行う（戻し先のタグ `mastersmith:pre-deps-forks-review` は残す）。
- 監視（lgtm・collector）は配備では起動しない（Build and Test の B3 で起動を確かめ済み）。

### 3.1 配備の前の検査（流し直さないもの）

| 検査 | 結果 | 出典 |
|---|---|---|
| `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify -PitForks=3`（`c07dfbb7`。`develop` の `e566faa4` と同じ中身） | 通過（14分36秒。単体 2,939・結合 1,716〈飛ばし 10〉・画面 3,063×2、失敗 0。全 71 パッケージが下限を満たす） | `construction/build-and-test/test-results.md` 1節 |
| E2E | 298 件 passed、axe の違反 0 | 同 2節 |
| `e566faa4` の後の差 | `.github/workflows/ci.yml` のコメント・`docs/verification.md`（`c59185b9`、`verifyQuick` が通った）と `aidlc/` の記録だけ。配備に入るファイルの差は 0 | `git diff --stat e566faa4 HEAD` |
| CI | 台本 A2 で前の取り込みの後の 25 コミットを確かめ、最後の試行が通っていない実行 0 件（`b4c8d77e` の実行 43038873152 は1回目 cancelled・2回目 success）。`-PitForks` の比べの6回とプッシュの CI はすべて試行 1 で success。先頭の CI は順 1 で確かめる | 同 3節、`deployment-pipeline-questions.md` |
| k6（負荷の試験） | 流さない。Spring Boot・H2・JRE が変わらず FR5.2 に当たる（B2: A） | 同 5節 |

## 4. 内部DB の複写

- 移行が無いため、配備の前の複写はバックアップとして取る（戻しの第二の手。`rollback-runbook.md` の 3節）。配備の後の複写は、スモークテストで監査に残る操作（ログイン）を行うため、監査の確かめに使う（`project.md` の Deployment の 2026-11-07 の決まり）。
- 取り方は `docs/internal-db.md` の「内部DBのバックアップと戻し方」のとおり。置き場は `~/.mastersmith-backup/`（権限 700、複写は 600）。`mktemp -d` の macOS の一時ディレクトリは使わない（colima の VM から見えない。`project.md` の学び）。取り方のイメージは前の配備と同じ JRE のイメージ（`Dockerfile` は変わっていない）。

```bash
date -u +%Y-%m-%dT%H:%M:%SZ          # T0 を控える
docker ps --filter name=mastersmith-app-1 --format '{{.Names}}'   # 空であること（動いていれば docker compose stop app）
mkdir -p ~/.mastersmith-backup && chmod 700 ~/.mastersmith-backup
docker run --rm -v mastersmith_mastersmith-data:/data -v "$HOME/.mastersmith-backup":/backup eclipse-temurin:25.0.5_8-jre-noble@sha256:a98128a57de944a72bd31fbc79b898edd5a5864d3652071d5bbd2160ab7d7dce \
  tar czf /backup/mastersmith-data-$(date +%Y%m%d%H%M)-pre-deps-forks-review.tgz -C /data .
chmod 600 ~/.mastersmith-backup/*-pre-deps-forks-review.tgz
ls -l ~/.mastersmith-backup/          # ファイルができたこと・権限 600（中身は開かない）
node scripts/count-db-copy.mjs --copy ~/.mastersmith-backup/<上の複写> --since 2026-11-10T15:00:19Z --set basic,audit
```

- `--since` は前の配備の後の複写を取り終えてアプリを起動し直した時刻（261108-ci-checks-release の `deployment-log.md` の順 11）。前の配備の後に監査に残った操作の内訳を出すため。

### 4.1 配備の前の複写で数えるもの（台本 A3、`--set basic,audit`）

台本は複写を `build/count-db-copy/` の下の実行ごとの置き場（権限 700）に展開し、H2 を読み取りで開いて数だけを出し、終わったら置き場を消す（`docs/internal-db.md`）。値（メールアドレス・ロールの名前・DSL の本文）は出さない。流した後に `build/count-db-copy/` に `work-*` が残っていないことを確かめる。

| 数えるもの | 見込み | 違ったとき |
|---|---|---|
| スキーマの版 | 14 | 違えば止めて依頼者に諮る |
| 初期管理者の行・停止・管理者の印（`.env` にあるかの 0・1 を含む） | 行あり・停止していない・印あり | 当たらなければ救済が働く見込み。入れ替えの前に依頼者の了承を得る |
| 利用者の数・管理者・有効な管理者・停止 | — | 配備の後の数え（順 11）と比べる基準 |
| 監査の全体の数と、前の配備の後（`--since`）の種類と結果ごとの件数 | 全体 342 以上（261108-ci-checks-release の配備の後の数え）。前の配備の後の件数は、依頼者がアプリを使った分だけ | 当たらない種類があれば依頼者に確かめる |

## 5. 決まりと上流との差（記録）

- **上流の段が無い**: この Intent の流れには CI Pipeline と Infrastructure Design の段が無い（scope `bugfix`）。段の定義の `consumes`（`ci-config`・`quality-gates`・`infrastructure-specification`・`cicd-pipeline`）はこの Intent の記録に無く、前の配備の手順とリポジトリの `Dockerfile`・`compose.yaml`・`docs/`、Build and Test の記録を正とした（`project.md` の学び）。CI の設定（`.github/workflows/ci.yml`）はこの Intent で変えた（Actions と OSV-Scanner の版、`IT_FORKS_DEFAULT` のコメント）。関門の基準と段の並びは変えていない。
- **前の Intent の「次の配備へ引き継ぐこと」**: 前に配備されていない Intent は無い（前の配備は 261108-ci-checks-release で、それより前の引き継ぎはその配備で扱った）。この Intent の引き継ぎ（配備・古いイメージの片付け・`main` への取り込みの前の CI の確かめ）は、順 1・順 13 と 7節に入れた。
- **アプリのコンテナが止まっている**: 前の配備は「コンテナが無い」状態からだった。今回はコンテナが止まって残っている。入れ替えの操作で作り直すため、手順は前の配備と同じ形で扱える。
- **戻しの練習を行わない**: 移行と `.env` の変更が無いため（`project.md` の Deployment の 2026-11-07 の決まり）。
- **E2E**: 配備の前の E2E は Build and Test の 298 件で満たす（流し直さない）。リリースの前の E2E（`team.md` の Way of Working）も、配備に入るファイルの差が 0 のため、この結果を正とする。

## 6. 成果物と版の識別

- 成果物は順 2 で作り直した実行可能 WAR と、それを写したイメージ `mastersmith:local`。
- 版はコミットのハッシュで識別する。配備する版は順 1 で取り込んだ `main` の先頭。
- どのコミットをリリースしたかは、`main` の先頭と Deployment Execution の記録で追う（`team.md` の Deployment）。

## 7. 古いイメージの片付けの候補（FR6.2）

`compose.yaml`・`perf/versions.env`・`Dockerfile`・`backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java` の固定先と照らした（2026-11-13 の `docker images`）。

| イメージ | ID | 大きさ | 理由 |
|---|---|---|---|
| `mastersmith:pre-ci-checks-release` | `4ee5fb45593e` | 655 MB | 新しい戻し先 `mastersmith:pre-deps-forks-review` より古い（Intent 261104 で配備した版） |
| `grafana/otel-lgtm`（0.39.0） | `4c8056c9c05f` | 3.58 GB | 固定先は 0.40.0（`72e05804790e`） |
| `otel/opentelemetry-collector`（0.164.0） | `28b6d41ac51b` | 212 MB | 固定先は 0.165.0（`34b24b740c3c`） |
| `axllent/mailpit`（v1.33.0） | `9b3a463be153` | 51 MB | 固定先は v1.33.1（`aef2aacc11f0`） |
| `postgres`（18.6 の前のダイジェスト） | `3a4264520e12` | 672 MB | 固定先は新しいダイジェスト（`e3d24f6906d0`） |
| `mysql`（26.10.0 の前のダイジェスト） | `7b16ec7caa94` | 1.1 GB | 固定先は新しいダイジェスト（`3819b2b23835`） |

- 残すもの: `mastersmith:local`・`mastersmith:pre-deps-forks-review`・固定先のイメージ（lgtm `72e05804790e`・collector `34b24b740c3c`・Mailpit `aef2aacc11f0`・postgres `e3d24f6906d0`・mysql `3819b2b23835`・mariadb `f4054da26e3a`・k6 `2dd4756ec6e1`・JRE `a98128a57de9`・JDK `95e350ad1eb7`）・`testcontainers/ryuk:0.14.0`。
- 止まっているコンテナ（lgtm・collector・Mailpit・見本の PG）は固定先のイメージを使っている（Build and Test で作り直した）。消す前に `docker ps -a --filter ancestor=<ID>` で 0 件を確かめる。

## Sources

- 前の手順: `aidlc/spaces/default/intents/261108-ci-checks-release/operation/deployment-pipeline/`（`cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`）、同じ Intent の `operation/deployment-execution/deployment-log.md`（配備した `a9ddc077`・イメージ `0d4b6e8866a9`・配備の後の数え）
- 引き継ぎの一覧: この Intent の `construction/build-and-test/build-and-test-summary.md`
- 要件: `inception/requirements-analysis/requirements.md`（FR5・FR6）
- 質問と答え: `deployment-pipeline-questions.md`（Q1〜Q3）
- 手順の続き: `deployment-strategy.md`（起動の確かめとスモークテスト）・`rollback-runbook.md`（戻し）
- Build and Test: `construction/build-and-test/build-and-test-summary.md`・`test-results.md`、コード生成: `construction/code-generation/code-summary.md`
- リポジトリ: `Dockerfile`、`compose.yaml`、`perf/versions.env`、`.env.example`、`docs/deployment.md`、`docs/internal-db.md`、`docs/operations.md`、`scripts/ops-readonly-check.mjs`・`scripts/count-db-copy.mjs`・`scripts/ci-attempts.mjs`、`git diff --stat a9ddc077 HEAD`・`git diff a9ddc077 HEAD -- frontend/src backend/gradle.lockfile`、`docker ps -a`・`docker images`・`docker image inspect`・`docker system df`（読み取り）
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment・Way of Working）、`project.md`（Deployment・Change Control）、`aidlc/spaces/default/knowledge/aidlc-shared/ops-notes.md`

## Assumptions & Open Questions

- [assumption] 初期管理者の状態は前の配備のまま。救済の見込みは順 5 で依頼者に確かめ、4.1 で裏付ける。
- [assumption] 先頭の `b8d61840` の CI は通る（Build and Test の後の差は記録だけ）。通らなければ順 1 で止め、`team.md` の「不安定なテストと CI の失敗」の決まりで扱う。
