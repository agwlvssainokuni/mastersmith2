# 配備の記録（deployment-log）— Intent 261110-deps-forks-review

`operation/deployment-pipeline/cd-config.md` の 3節（順 1〜14）のとおりに配備した。配備したのは、依頼者の承認（Q1: A）を得て `develop` から fast-forward で取り込んだ `main` の先頭 `82825a67`。時刻は UTC（2026-11-12）。

## 1. 配備した版

| 項目 | 値 |
|---|---|
| 配備したソース | `main` の `82825a67`（前の取り込み `a9ddc077` から 27 コミット先。配備に入る変更は `888a4cc1` の `backend/gradle.lockfile`・`frontend/package.json`・`frontend/package-lock.json`・`frontend/src/features/tables/DateTimeField.tsx`・`SearchPanel.tsx`） |
| WAR | `./gradlew :backend:bootWar --rerun-tasks`（17:07:23〜17:08:06、43 秒、`caffeinate -dims`）。SHA-256 `53abadc0d733a70754605fbc9da45a1579668f06736671be541f00df1188f3f4`。`WEB-INF/lib` は networknt `json-schema-validator-3.0.9`・`jackson-core-3.1.8`・`jackson-databind-3.1.8`・`itu-1.17.0` だけで、前の版は無い。`static/` の画面のファイルは `frontend/dist` と同じ（WAR だけにあるのはバックエンドの資源 `dsl/dsl-schema-v2.json`）。コンテナの中の WAR も同じ SHA-256 |
| 新しいイメージ | `mastersmith:local`（`sha256:e4eb8ce7de01…`）。コンテナのイメージ ID と一致（作った後と、順 11 の起動し直しの後の2回） |
| 戻し先 | `mastersmith:pre-deps-forks-review`（`sha256:0d4b6e8866a9…`、Intent 261108-ci-checks-release で配備した `a9ddc077` の版） |
| 変えなかったもの | `.env`（更新の時刻 2026-11-02）・`Dockerfile`・内部DB のスキーマ（版 14）・実行時のタイムゾーンの設定の 4 か所（E2E の起動の `-Duser.timezone` は配備に入らない） |

## 2. 流れの結果

| 順 | 時刻 | すること | 結果 |
|---|---|---|---|
| 1 | 17:06〜20:30 | 関門と `main` への取り込み | アプリのソースに未コミットの変更 0 件。台本 A2 で前の取り込みの後の 27 コミットの CI を確かめ、最後の試行が通っていない実行 0 件（`b4c8d77e` の実行 43038873152 は1回目 cancelled・2回目 success）。`b8d61840` の実行 43255567312 は試行 1 で success（検査の job 26分11秒）。先頭の `82825a67` の実行 43265830641 は試行 1 で success（検査の job 26分46秒、17:06:13〜17:32:59）。依頼者の承認（Q1: A）の後に `git merge --ff-only 82825a67` で `main` を進めた（`develop` の先頭の記録のコミットは含めない） |
| 2 | 17:07〜17:08 | WAR の作り直し | 1節のとおり。`MASTERSMITH_IMAGE_TAG` 0 件 |
| 3 | 17:0x | 配備の前の環境の確かめ | Mailpit と見本の PG を起動し、台本 A1 で版とダイジェストが `compose.yaml` と一致（食い違い 0 件）。WAR の SHA-256 が順 2 と一致。`.env` の名前 18 個（値は読まない） |
| 4 | 17:0x | 戻し先のタグ | `mastersmith:pre-deps-forks-review` を付け、ID が `0d4b6e8866a9` で始まることを確かめた |
| 5 | — | 救済の見込み | 依頼者の答え Q2: A（前の配備の後に初期管理者を変えていない）。2.1 の数えで裏付けた |
| 6 | 20:30:49〜20:30:50 | 配備の前の複写 | T0 = 20:30:49Z。アプリのコンテナが止まっていることを確かめ、止める操作なし。`mastersmith-data-202611130530-pre-deps-forks-review.tgz`（43,709 バイト、権限 600）。2.1 の数え |
| 7 | 20:30:57〜20:31:0x | 入れ替え | `docker compose up -d --build --no-deps app` でコンテナを作り直した。イメージ ID が一致し、作り直しは要らなかった |
| 8 | 20:31:13 | 健全性（S1） | 通過（`health-check-report.md`） |
| 9 | — | 合わせ直し | 不要（2.1 が見込みどおり） |
| 10 | 20:31:34〜22:15:01 | スモークテスト（S2〜S4） | 通過（`smoke-test-results.md`）。依頼者の答え「A, login 1, logout 1」 |
| 11 | 22:15:25〜22:15:38 | 配備の後の複写と環境の確かめ | `mastersmith-data-202611130715-after-deps-forks-review.tgz`（44,048 バイト、権限 600）。止めていたのは約 11 秒、22:15:36 に healthy、イメージ ID 一致。2.2 の数え（S5）。台本 A1 を流し直し、アプリ・見本の PG・Mailpit の食い違い 0 件 |
| 12 | — | 配備の記録のコミット | この記録 |
| 13 | 22:2x | 古いイメージの片付け | 3.1 のとおり（Q5: A） |
| 14 | — | プッシュ | 依頼者が `main`（`82825a67`）と `develop` をプッシュする |

- アプリが止まったのは順 11 の約 11 秒だけ（配備の前はもともと止まっていた）。
- 監視（lgtm・collector）は起動していない（Build and Test の B3 で確かめ済み）。Mailpit と見本の PG は動いたまま。

### 2.1 配備の前の複写の数え（台本 A3、`--since 2026-11-10T15:00:19Z --set basic,audit`）

| 項目 | 見込み | 結果 |
|---|---|---|
| スキーマの版 | 14 | 14 |
| 初期管理者（`.env` にあるか・行・停止・印） | 1・1・0・1 | 1・1・0・1 |
| 利用者の数・管理者・有効な管理者・停止 | — | 3・1・1・0 |
| DSL の適用の版・プレビュー | — | 8・0 |
| 前の配備の後の監査 | 依頼者が使った分だけ | 0 件 |
| 監査の全体 | 342 以上 | 342 |

- 置き場 `build/count-db-copy/` に `work-*` は残っていない。

### 2.2 配備の後の複写の数え（S5、`--since 2026-11-12T20:30:49Z --set basic,audit`）

| 項目 | 期待 | 結果 |
|---|---|---|
| スキーマの版 | 14 | 14 |
| 初期管理者・利用者・DSL・プレビュー | 2.1 と同じ | 同じ |
| T0 の後の監査 | `LOGIN_SUCCEEDED` 1・`LOGGED_OUT` 1（依頼者の答え「login 1, logout 1」） | `LOGIN_SUCCEEDED` `SUCCESS` 1・`LOGGED_OUT` `SUCCESS` 1、ほか 0 |
| 監査の全体 | 344 | 344 |

- 置き場 `build/count-db-copy/` に `work-*` は残っていない。台本 A3 が `.env` を読んだのは、許しを得た 2 回（2.1・2.2）だけ。

## 3. 片付けとリリース

### 3.1 古いイメージの片付け（順 13、FR6.2、Q5: A）

| 名前 | ID | 理由 | 使うコンテナ |
|---|---|---|---|
| `mastersmith:pre-ci-checks-release` | `4ee5fb45593e` | Intent 261104 で配備した版。新しい戻し先より古い | 0 件 |
| `grafana/otel-lgtm`（0.39.0） | `4c8056c9c05f` | 固定先は 0.40.0 | 0 件 |
| `otel/opentelemetry-collector`（0.164.0） | `28b6d41ac51b` | 固定先は 0.165.0 | 0 件 |
| `axllent/mailpit`（v1.33.0） | `9b3a463be153` | 固定先は v1.33.1 | 0 件 |
| `postgres`（18.6 の前のダイジェスト） | `3a4264520e12` | 固定先は新しいダイジェスト | 0 件 |
| `mysql`（26.10.0 の前のダイジェスト） | `7b16ec7caa94` | 固定先は新しいダイジェスト | 0 件 |

- `docker rmi` で6つとも消した。`docker system df` のイメージ: 片付けの前 18 個・13.38GB（回収できる 8.214GB）→ 後 12 個・7.566GB（回収できる 2.397GB）。
- 残したもの: `mastersmith:local`（`e4eb8ce7de01`）・戻し先 `mastersmith:pre-deps-forks-review`（`0d4b6e8866a9`）・固定先のイメージ（Mailpit `aef2aacc11f0`・lgtm `72e05804790e`・collector `34b24b740c3c`・postgres `e3d24f6906d0`・mysql `3819b2b23835`・mariadb `f4054da26e3a`・k6 `2dd4756ec6e1`・JRE `a98128a57de9`・JDK `95e350ad1eb7`）・`testcontainers/ryuk:0.14.0`。

### 3.2 リリース（FR6.3）

- `main` は、順 1 で依頼者の承認（Q1: A）を得て `a9ddc077` から `82825a67` へ fast-forward した。プッシュは依頼者が行う。
- 取り込みの前の確かめ: 全件の `verify`（14分36秒、失敗 0）と E2E 298 件は Build and Test（`c07dfbb7`。その後の差は `ci.yml` のコメント・`docs/verification.md`・記録だけ）。前の取り込みの後のすべてのコミットの CI を試行ごとに確かめた（順 1）。
- タグは付けていない（`team.md` の Deployment）。

## 4. 引き継ぎ（この Intent の Build and Test の「次の配備へ引き継ぐこと」）

| 項目 | 結果 |
|---|---|
| 前の配備されていない Intent の一覧 | 無し（前の配備は 261108-ci-checks-release で、それより前の引き継ぎはその配備で扱った） |
| 古い版のイメージと古い戻し先のタグの片付け（FR6.2） | 3.1 のとおり消した |
| `main` への取り込みの前の CI の試行ごとの確かめ（FR6.3） | 順 1 で確かめた。`b4c8d77e` のプッシュの CI は1回目 cancelled（待ちの入れ替え）・2回目 success |

## 5. 上流との差・気づいたこと

- **アプリのコンテナが止まっていた**: 配備の前に `mastersmith-app-1` が止まっていた（Exited (143)）。内部DB の数え（2.1）では、前の配備の後の監査は 0 件で、ボリュームを別のものが書き換えた形跡は無い。入れ替えの操作でコンテナを作り直した。
- **`main` を `develop` の先頭ではなく `82825a67` へ進めた**: 順 1 の後に `develop` に積んだ記録のコミット（Q1〜Q3 の答え）は、CI がまだ流れていないため取り込まなかった。`cd-config.md` の順 1 は `git merge --ff-only develop` と書いたが、CI を確かめたコミットに合わせた。配備に入るファイルの差は 0。
- `cd-config.md` の S3 は日本語と英語の両方で見た。S4 の `DSL_PREVIEW_NOT_FOUND` 404 は、DSL の状態の画面を開いた回数（日本語と英語）に当たり、前の配備と同じ想定内の応答。

## Sources

- 流れ: `operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`
- 質問と答え: `deployment-execution-questions.md`（Q1〜Q5）
- Build and Test: `construction/build-and-test/test-results.md`・`build-and-test-summary.md`
- 確かめ: `node scripts/ci-attempts.mjs`・`gh run view 43255567312`・`43265830641`、`node scripts/ops-readonly-check.mjs`、`node scripts/count-db-copy.mjs`、`docker compose`・`docker inspect`・`docker logs`・`docker rmi`・`docker system df`、`shasum -a 256`・`unzip -Z1`
- 前の配備: `aidlc/spaces/default/intents/261108-ci-checks-release/operation/deployment-execution/deployment-log.md`

## Assumptions & Open Questions

None.
