# 配備の流れの構成（cd-config）

Intent 261108-ci-checks-release の配備の流れです。この Intent は bugfix で、検査・CI・確かめの台本を整え、`develop` を `main` へ取り込みました（`main` は `a9ddc077`、依頼者がプッシュ済み）。要件 FR6.3（Q6: A）で、取り込んだ版を配備の段に通します。

- **配備先**: これまでどおり、開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト名 `mastersmith`）。
- **配備の仕方**: 自動の配備は持たず、依頼者の承認のうえで手で配備する（`team.md`・`project.md` の Deployment）。本番配備の承認者は依頼者。
- **この文書の元**: 前の配備の手順（`aidlc/spaces/default/intents/261104-tz-options-perf/operation/deployment-pipeline/`）と、リポジトリの `Dockerfile`・`compose.yaml`・`docs/deployment.md`・`docs/internal-db.md`・`docs/operations.md` を正とし、今回の差だけを書く（`project.md` の Deployment の学び）。
- **上流**: この Intent の流れには CI Pipeline と Infrastructure Design の段が無い（5節）。上流は Build and Test の記録（`construction/build-and-test/`）と要件（FR6.3）である。
- **決定**: `deployment-pipeline-questions.md`（Q1〜Q3 すべて A）。

## 1. 配備する版と、今の状態

| 項目 | 値 |
|---|---|
| 今の状態 | **アプリのコンテナ `mastersmith-app-1` が無い**（`docker ps -a` に無い。いつ消えたかは記録から分からない）。前の配備のイメージ `mastersmith:local`（`4ee5fb45593e`、261104 で配備した版）と内部DB のボリューム `mastersmith_mastersmith-data` は残っている。見本の PG（`postgres:18.6`）と Mailpit（`v1.33.0`）が動いている |
| 配備する版 | `main` の先頭 `a9ddc077`（前の配備の `d9e35417` から、前の取り込み `b2990415` を経て 41 コミット先）。配備に入るアプリのコードの最後の変更は `04b797d8`。`develop` の先頭（記録のコミット）とは、配備に入るファイルの差が 0 |
| 変わるもの（配備に入る） | `04b797d8`「使われていない画面の関数・型・文言の鍵を消す」: `frontend/src` の 15 ファイル（テスト 2 を含む）から、使われていない関数・型と、画面に出ていない文言の鍵（`dsl.status.at`・`roletransfer.slow`、日英）を消し、注の誤りを直した。画面の振る舞いは変えない。どの Intent にも属さないコミット（261104 の配備の後） |
| 変わらないもの | `backend/src/main`・`Dockerfile`・`.env.example`・`backend/gradle.lockfile`（前の配備から差 0）。Spring Boot・H2・HikariCP・JRE の版 |
| 配備に入らない変更 | `backend/build.gradle.kts`（検査とカバレッジの判定のタスクだけ）・`compose.yaml`（Mailpit の `MP_SMTP_DISABLE_RDNS` だけ。動いている Mailpit には既に当たっている）・`.github/`・`scripts/`・`frontend/package.json` の検査の命令・テスト・`docs/`・`aidlc/` |
| 内部DB のスキーマ | 変更なし（最後の移行は V14 のまま） |
| `.env` | 変更なし |
| 監査の種類 | 変更なし |
| 戻し先 | 入れ替えの前に前の配備のイメージに付けるタグ `mastersmith:pre-ci-checks-release`（`4ee5fb45593e`） |
| 片付けの候補（Q3: A） | `mastersmith:pre-tz-options-perf`（`ffd2b8380460`、261101 で配備した版。新しい戻し先より古い、655 MB） |

## 2. 今回の変更と、配備への影響

| 変更 | 配備への影響 |
|---|---|
| 使われていない画面のコードと文言の鍵の削除（`04b797d8`） | 画面のファイル（JavaScript）が変わる。WAR の中の画面のファイルに `dsl.status.at` が 0 件であることで、新しい版が入ったことを確かめる。消したコードがあった画面（DSL の状態・業務データの一覧のページ送り・役割・グループ・役割の引き継ぎ・利用者の管理・表示のタイムゾーン）を、スモークテストの S3 で見る（Q1: A） |
| アプリのコンテナが無い | 入れ替えの操作（`docker compose up -d --build --no-deps app`）でコンテナを新しく作る。止める操作は要らず、複写はボリュームを使うコンテナが無いうちに取る |
| Mailpit の設定（`compose.yaml`） | 配備には入らない。この Intent の Build and Test で作り直して当て済み。順 3 と順 11 の台本 A1 で、動いている Mailpit の版とダイジェストが `compose.yaml` と一致することを確かめる |

## 3. 流れ

| 順 | 段 | すること | 行う人 | 承認 |
|---|---|---|---|---|
| 1 | 関門 | アプリのソースに未コミットの変更が無いこと（`aidlc/` の下は除く）を確かめる。verify・E2E・前の取り込みの後の CI は Build and Test とリリースの前の確かめの結果を正とし、流し直さない（3.1）。`main` の取り込みの後の最初の `develop` のプッシュの CI（`24ed24e6`、実行 42704002194）の結論を台本 A2（`node scripts/ci-attempts.mjs --from a9ddc077`）で確かめる。走っている最中なら配備を進め、順 13 で確かめ直す | AI | — |
| 2 | WAR の作り直し | `./gradlew :backend:bootWar --rerun-tasks` で WAR を作り直し、SHA-256 を記録する。WAR の中の `static/` の画面のファイルの名前が今の `frontend/dist` と同じこと、`dsl.status.at` が 0 件であること（前の配備の WAR では 1 件以上）を確かめる。`Dockerfile` は手元の WAR を写すだけ（`project.md` の学び）。シェルに `MASTERSMITH_IMAGE_TAG` が残っていないこと（`env \| grep -c MASTERSMITH_IMAGE_TAG` が 0）を確かめる | AI | — |
| 3 | 配備の前の環境の確かめ | 台本 A1 `node scripts/ops-readonly-check.mjs --war backend/build/libs/mastersmith.war`。動いているコンテナ（見本の PG・Mailpit）の版とダイジェストが `compose.yaml` と一致し、WAR の SHA-256 が順 2 と一致することを確かめる。`.env` は項目の名前の数だけ（値は出ない） | AI | — |
| 4 | 戻し先のタグ | `docker tag mastersmith:local mastersmith:pre-ci-checks-release`。付けた後に ID が `4ee5fb45593e` で始まることを確かめる | AI | — |
| 5 | 救済の見込み | 依頼者に、前の配備の後に初期管理者のパスワードを画面で変えた・止めた・印を外したことがあるかを尋ねる。順 6 の複写の数えで裏付ける | AI と依頼者 | — |
| 6 | 配備の前の複写（Q2: A） | T0 を控える。アプリのコンテナが無いため止める操作は要らない（コンテナがあれば `docker compose stop app`）。内部DB を `~/.mastersmith-backup/`（権限 700）へ `…-pre-ci-checks-release.tgz`（権限 600）として複写する（4節）。台本 A3 で数える（4.1。`.env` を読むことは Q2: A で許しを得た） | AI | 依頼者の承認（アプリを起動して内部DB を使い始めるため。順 6〜8 をまとめて承認） |
| 7 | 入れ替え | `docker compose up -d --build --no-deps app`。入れ替えの後に `docker inspect` でコンテナのイメージ ID と `mastersmith:local` の ID が一致するかを確かめ、食い違えば `--force-recreate --no-deps app` で作り直す（`project.md` の学び）。見本の PG・Mailpit は作り直さない | AI | 順 6 の承認に含む |
| 8 | 健全性 | `deployment-strategy.md` の S1（healthy・`/actuator/health` の 200・移行が当たらないこと・起動のログ） | AI | — |
| 9 | 4.1 の結果での合わせ直し | 4.1 の数が前提と違うとき（初期管理者の状態など）は、スモークテストの前に依頼者と合わせ直し、監査の期待を直す | AI と依頼者 | 依頼者の決定 |
| 10 | スモークテスト（Q1: A） | `deployment-strategy.md` の S2〜S4。AI の未認証の確かめとアプリのログを先に行い、依頼者には Chrome でのログインと画面の見た目だけを頼む（`project.md` の Deployment）。監査に残るのはログイン（とログアウト）だけ。ログインの前に伝える | AI と依頼者 | 監査に残る操作は前もって伝える |
| 11 | 配備の後の複写と環境の確かめ（Q1・Q2: A） | 依頼者に伝えてから、アプリを止めて内部DB を `…-after-ci-checks-release.tgz`（権限 600）として複写し、すぐ起動し直す（約 10 秒）。起動し直した後も healthy とイメージ ID の一致を確かめる。台本 A3 を `--since <T0> --set basic,audit` で流し、監査の種類と結果ごとの件数を `deployment-strategy.md` の 3.1 と照らす（S5）。台本 A1 を流し直し、アプリのコンテナを含めて版とダイジェストを確かめる | AI | 依頼者に伝えてから |
| 12 | 配備の記録のコミット | Deployment Execution の記録をコミットする | AI | 段の成果物の初稿と段の承認の時は尋ねずに行う（`aidlc/` の下だけ） |
| 13 | 最初の `develop` のプッシュの CI | 順 1 で走っている最中だったときは、台本 A2 で結論と試行ごとの結果を確かめて記録する。失敗していたら `team.md` の「不安定なテストと CI の失敗」の決まりで扱い、依頼者に諮る | AI | — |
| 14 | 古いイメージの片付け（Q3: A） | 片付けの前の `docker system df` を記録する。`mastersmith:pre-tz-options-perf`（`ffd2b8380460`）を使うコンテナが 0 件（`docker ps -a --filter ancestor=<ID>` が 0 件）であることを確かめ、消す一覧を示して承認を得てから `docker rmi` で消す。消した後に `docker system df` を記録する。ほかのイメージには触れない | AI | 依頼者の承認（消す一覧） |
| 15 | プッシュ | 配備の記録のコミット（`develop` だけ）を `origin` へプッシュするのは依頼者。`main` は `a9ddc077` のまま動かさない（配備する版が既に `main` の先頭のため） | 依頼者 | — |

- 配備の完了は、healthy とスモークテストのすべてが通ったとき（`team.md` の Deployment）。
- アプリはもともと動いていないため、順 6〜8 の間に止まるものは無い。順 11 の複写の間に約 10 秒止まる。
- 開いているタブがあれば、順 8 の後に読み込み直す（新しい画面のファイルを読むため）。
- 戻しの練習は行わない。移行と `.env` の変更が無く、戻しはイメージだけのため（`project.md` の Deployment の 2026-11-07 の決まり）。
- 順 14 の片付けは、配備が完了してから行う（戻し先のタグ `mastersmith:pre-ci-checks-release` は残す）。

### 3.1 配備の前の検査（流し直さないもの）

| 検査 | 結果 | 出典 |
|---|---|---|
| `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify -PitForks=3`（この Intent） | 通過（15 分 34 秒。単体 2,939・結合 1,716〈飛ばし 10。DB の種類別の前提〉、失敗 0。全 71 パッケージが下限を満たす） | `construction/build-and-test/test-results.md` |
| E2E | 298 件 passed（Mailpit の逆引きを止めた後） | 同 |
| CI | 前の取り込み（`b2990415`）の後の 39 コミットを台本 A2 で確かめた。失敗は統合の後の 42633708198（`UserRowLockRepositoryIT`）の1件で、直した後の `a9ddc077` の実行 42647513146 が1回目で成功（26 分 27 秒） | 同（5節）、`aidlc/spaces/default/intents/261108-ci-checks-release/audit/` のリリースの前の確かめ |
| k6（負荷の試験） | 流さない。サーバーの処理と内部DB は前の配備（261104）と同じで、差は画面の使われていないコードの削除だけ | `project.md` の学び（試験済みのときは省く） |

## 4. 内部DB の複写（Q2: A）

- 移行が無いため、配備の前の複写はバックアップとして取る（戻しの第二の手。`rollback-runbook.md` の 3節）。配備の後の複写は、スモークテストで監査に残る操作（ログイン）を行うため、監査の確かめに使う（`project.md` の Deployment の 2026-11-07 の決まり）。
- 取り方は `docs/internal-db.md` の「内部DBのバックアップと戻し方」のとおり。置き場は `~/.mastersmith-backup/`（権限 700、複写は 600）。`mktemp -d` の macOS の一時ディレクトリは使わない（colima の VM から見えない。`project.md` の学び）。取り方のイメージは前の配備と同じ `eclipse-temurin:25.0.5_8-jre-noble@sha256:a98128a57de9…`（`Dockerfile` は変わっていない）。

```bash
date -u +%Y-%m-%dT%H:%M:%SZ          # T0 を控える
docker ps -a --filter name=mastersmith-app-1 --format '{{.Names}}'   # 空であること（あれば docker compose stop app）
mkdir -p ~/.mastersmith-backup && chmod 700 ~/.mastersmith-backup
docker run --rm -v mastersmith_mastersmith-data:/data -v "$HOME/.mastersmith-backup":/backup eclipse-temurin:25.0.5_8-jre-noble@sha256:a98128a57de944a72bd31fbc79b898edd5a5864d3652071d5bbd2160ab7d7dce \
  tar czf /backup/mastersmith-data-$(date +%Y%m%d%H%M)-pre-ci-checks-release.tgz -C /data .
chmod 600 ~/.mastersmith-backup/*-pre-ci-checks-release.tgz
ls -l ~/.mastersmith-backup/          # ファイルができたこと・権限 600（中身は開かない）
node scripts/count-db-copy.mjs --copy ~/.mastersmith-backup/<上の複写> --since 2026-11-06T21:37:07Z --set basic,audit
```

- `--since` は前の配備の後の複写を取り終えてアプリを起動し直した時刻（261104 の `deployment-log.md` の順 10）。前の配備の後に監査に残った操作の内訳を出すため。

### 4.1 配備の前の複写で数えるもの（台本 A3、`--set basic,audit`）

台本は複写を `build/count-db-copy/` の下の実行ごとの置き場（権限 700）に展開し、H2 を読み取りで開いて数だけを出し、終わったら置き場を消す（`docs/internal-db.md`）。値（メールアドレス・ロールの名前・DSL の本文）は出さない。流した後に `build/count-db-copy/` に `work-*` が残っていないことを確かめる。

| 数えるもの | 見込み | 違ったとき |
|---|---|---|
| スキーマの版 | 14 | 違えば止めて依頼者に諮る |
| 初期管理者の行・停止・管理者の印（`.env` にあるかの 0・1 を含む） | 行あり・停止していない・印あり | 当たらなければ救済が働く見込み。入れ替えの前に依頼者の了承を得る（前の手順） |
| 利用者の数・管理者・有効な管理者・停止 | — | 配備の後の数え（順 11）と比べる基準 |
| 監査の全体の数と、前の配備の後（`--since`）の種類と結果ごとの件数 | 全体 339 以上（261104 の配備の後の数え）。前の配備の後の件数は、依頼者がアプリを使った分だけ | 当たらない種類があれば依頼者に確かめる。配備の後の数え（順 11）と比べる基準にもする |

## 5. 決まりと上流との差（記録）

- **上流の段が無い**: この Intent の流れには CI Pipeline と Infrastructure Design の段が無い（scope `bugfix`）。段の定義の `consumes`（`ci-config`・`quality-gates`・`infrastructure-specification`・`cicd-pipeline`）はこの Intent の記録に無く、前の配備の手順とリポジトリの `Dockerfile`・`compose.yaml`・`docs/`、Build and Test の記録を正とした（`project.md` の学び）。CI の設定（`.github/workflows/ci.yml`）はこの Intent で変えた（`-PitForks` の既定 3 と手動の実行の入力 `it_forks`）。関門の基準と段の並びは変えていない（`construction/code-generation/code-summary.md`）。
- **前の Intent の「次の配備へ引き継ぐこと」を読んだ（FR6.3）**:
  - 261107-verify-quick-local: 配備の段で確かめる項目は無し。リリースの前の全件の `verify` と、前の取り込みの後のすべてのコミットの CI の試行の確かめは、この Intent の Build and Test とリリースの前に済ませた（3.1）。
  - 261108-ci-size-coverage: 要約の差の列は、依頼者が `develop` のプッシュの CI で確かめた。CI の job の時間は、この Intent で `-PitForks=3` にして 26 分 27 秒になり、42 分の線から離れた（`construction/build-and-test/build-and-test-summary.md`）。配備の段で確かめる項目は無し。
  - この Intent: `main` へ取り込んだ後の最初の `develop` のプッシュの CI の時間と結果を、順 1 と順 13 で確かめる。
- **どの Intent にも属さない変更を配備する**: `04b797d8` は 261104 の配備の後、Intent の外で積んだコミットで、まだ配備していない。この配備で入れる（調べて分かったこと。質問の前提に書いた）。
- **アプリのコンテナが無い**: 前の配備の手順は「動いているアプリを止めて複写し、入れ替える」形だった。今回はコンテナが無いため、止める操作を省き、入れ替えの操作でコンテナを新しく作る。ボリュームと前の版のイメージが残っているため、内部DB と戻し先は前の手順と同じ形で扱える。
- **戻しの練習を行わない**: 移行と `.env` の変更が無いため（`project.md` の Deployment の 2026-11-07 の決まり）。前の配備の「バックアップと戻しの練習は行わない」（2026-11-04 の学び）とは、配備の前の複写を取る点で 2026-11-07 の決まりに従った。
- **台本 A1・A3 を初めて配備に使う**: 前の配備の手で行った確かめ（`docker inspect` の版とダイジェスト・WAR の SHA-256・ホームの下の `count-db*.sh`）を、この Intent で作った台本に置き換えた。台本の合格の条件（FR5.5）は Build and Test で確かめ済み。
- **E2E**: 配備の前の E2E は Build and Test の 298 件で満たす（流し直さない）。

## 6. 成果物と版の識別

- 成果物は順 2 で作り直した実行可能 WAR と、それを写したイメージ `mastersmith:local`。
- 版はコミットのハッシュで識別する。配備する版は `main` の `a9ddc077`（アプリのコードは `04b797d8`）。
- どのコミットをリリースしたかは、`main` の先頭と Deployment Execution の記録で追う（`team.md` の Deployment）。

## Sources

- 前の手順: `aidlc/spaces/default/intents/261104-tz-options-perf/operation/deployment-pipeline/`（`cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`）、同じ Intent の `operation/deployment-execution/deployment-log.md`（配備した `d9e35417` と WAR の SHA-256）
- 引き継ぎの一覧: `aidlc/spaces/default/intents/261107-verify-quick-local/construction/build-and-test/build-and-test-summary.md`、`aidlc/spaces/default/intents/261108-ci-size-coverage/construction/build-and-test/build-and-test-summary.md`、この Intent の `construction/build-and-test/build-and-test-summary.md`
- 要件: `inception/requirements-analysis/requirements.md`（FR5・FR6）
- 質問と答え: `deployment-pipeline-questions.md`（Q1〜Q3）
- 手順の続き: `deployment-strategy.md`（起動の確かめとスモークテスト）・`rollback-runbook.md`（戻し）
- Build and Test: `construction/build-and-test/build-and-test-summary.md`・`test-results.md`、コード生成: `construction/code-generation/code-summary.md`
- リポジトリ: `Dockerfile`、`compose.yaml`、`.env.example`、`docs/deployment.md`、`docs/internal-db.md`、`docs/operations.md`、`scripts/ops-readonly-check.mjs`・`scripts/count-db-copy.mjs`・`scripts/ci-attempts.mjs`、`git log`・`git diff --stat d9e35417 a9ddc077`・`git show 04b797d8`、`docker ps -a`・`docker images`・`docker inspect`・`docker system df`（読み取り）
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment・Way of Working）、`project.md`（Deployment・Change Control）、`aidlc/spaces/default/knowledge/aidlc-shared/ops-notes.md`

## Assumptions & Open Questions

- [assumption] 初期管理者の状態は前の配備のまま。救済の見込みは順 5 で依頼者に確かめ、4.1 で裏付ける。
- [assumption] アプリのコンテナが無くなった間に、内部DB のボリュームを別のアプリが書き換えていない。4.1 のスキーマの版（14）と監査の全体の数を、前の配備の記録の配備の後の数え（`261104-tz-options-perf/operation/deployment-execution/deployment-log.md` の 2.2、監査の全体 339）と比べて裏付ける。前の配備の後にアプリを使った分だけ増えうるため、4.1 の `--since` の内訳（前の配備の後の種類ごとの件数）が、ログイン・ログアウトなど依頼者が使った操作に当たるかを確かめる（ほかの複写は数えない。Q2: A の許しは2回まで）。
