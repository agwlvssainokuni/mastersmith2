# 配備の記録（deployment-log）— Intent 261108-ci-checks-release

`operation/deployment-pipeline/cd-config.md` の 3節（順 1〜15）のとおりに配備した。配備したのは `main` の先頭 `a9ddc077` で、前の配備の後に Intent の外で積んだ画面の直し `04b797d8` が入る（Deployment Pipeline の質問の前提）。時刻は UTC（2026-11-10）。

## 1. 配備した版

| 項目 | 値 |
|---|---|
| 配備したソース | `main` の `a9ddc077`（アプリのコードの最後の変更は `04b797d8`。`develop` の先頭との差は `aidlc/` だけ） |
| WAR | `./gradlew :backend:bootWar --rerun-tasks`（14:44:20〜14:45:05、45 秒、`caffeinate -dims`）。SHA-256 `3805cffbadebddc59cfe4a53abff0d422d3a8a717558d6f38f51fdc3256bc9f3`。`static/` の画面のファイルは `frontend/dist` と同じ（WAR だけにあるのはバックエンドの資源 `dsl/dsl-schema-v2.json`）。画面の JavaScript に `dsl.status.at` が 0 件（前の版のイメージの WAR は SHA-256 `e2065c43…` で 261104 の記録と同じ、1 件）。移行のファイル 14 件。コンテナの中の WAR も同じ SHA-256 |
| 新しいイメージ | `mastersmith:local`（`sha256:0d4b6e8866a9…`）。コンテナのイメージ ID と一致（作った後と、順 11 の起動し直しの後の2回） |
| 戻し先 | `mastersmith:pre-ci-checks-release`（`sha256:4ee5fb45593e…`、Intent 261104 で配備した版） |
| 変えなかったもの | `.env`（更新の時刻 2026-11-02）・`Dockerfile`・見本の PG・Mailpit。内部DB のスキーマ（版 14）。実行時のタイムゾーンの設定の 4 か所 |

## 2. 流れの結果

| 順 | 時刻 | すること | 結果 |
|---|---|---|---|
| 1 | 14:4x | 関門 | アプリのソースに未コミットの変更 0 件。`main` の取り込みの後の最初の `develop` のプッシュの CI（`24ed24e6`、実行 42704002194）は1回目で成功、検査の job 26 分 19 秒（21:35:56〜22:02:15、2026-11-09。台本 A2） |
| 2 | 14:44〜14:45 | WAR の作り直し | 1節のとおり。`MASTERSMITH_IMAGE_TAG` 0 件 |
| 3 | 14:45:29 | 台本 A1（配備の前） | 見本の PG・Mailpit の版とダイジェストが `compose.yaml` と一致（食い違い 0 件）。アプリのコンテナは無い。WAR の SHA-256 が順 2 と一致。`.env` の名前 18 個（値は読まない） |
| 4 | 14:4x | 戻し先のタグ | `mastersmith:pre-ci-checks-release` を付け、ID が `4ee5fb45593e` で始まることを確かめた |
| 5 | — | 救済の見込み | 依頼者の答え Q1: A（初期管理者について変更なし）。順 6 の数え（停止 0・印 1）と起動のログ（救済の WARN 0）で裏付けた |
| 6 | 14:54:01〜14:54:02 | 配備の前の複写 | T0 = 14:54:01Z。アプリのコンテナが無いことを確かめ、止める操作なし。`mastersmith-data-202611102354-pre-ci-checks-release.tgz`（44,230 バイト、権限 600）。2.1 の数え |
| 7 | 14:54:16〜14:54:22 | 入れ替え | `docker compose up -d --build --no-deps app` でコンテナを新しく作った。イメージ ID が一致し、作り直しは要らなかった |
| 8 | 14:54:32 | 健全性 | healthy・`/actuator/health` 200。S1 は `health-check-report.md` |
| 9 | — | 合わせ直し | 不要（2.1 が見込みどおり） |
| 10 | 14:55〜14:59 | スモークテスト | S2〜S4 通過（`smoke-test-results.md`） |
| 11 | 15:00:09〜15:00:19 | 配備の後の複写と環境の確かめ | `mastersmith-data-202611110000-after-ci-checks-release.tgz`（43,722 バイト、権限 600）。止めていたのは約 10 秒、15:00:19 に healthy、イメージ ID 一致。2.2 の数え（S5）。台本 A1 を流し直し、アプリ・見本の PG・Mailpit の食い違い 0 件 |
| 12 | — | 配備の記録のコミット | この記録の初稿でコミットする |
| 13 | — | 最初の `develop` のプッシュの CI | 順 1 で成功を確かめ済み |
| 14 | 15:0x | 古いイメージの片付け | 依頼者の答え Q4: A。3.1 |
| 15 | — | プッシュ | 依頼者が `develop` をプッシュする。`main` は `a9ddc077` のまま |

- アプリが止まったのは順 11 の約 10 秒だけ（配備の前はもともと動いていなかった）。

### 2.1 配備の前の複写の数え（台本 A3、`--since 2026-11-06T21:37:07Z --set basic,audit`）

| 項目 | 見込み | 結果 |
|---|---|---|
| スキーマの版 | 14 | 14 |
| 初期管理者（`.env` にあるか・行・停止・印） | 1・1・0・1 | 1・1・0・1 |
| 利用者の数・管理者・有効な管理者・停止 | — | 3・1・1・0 |
| DSL の適用の版・プレビュー | — | 8・0 |
| 前の配備の後の監査 | 依頼者が使った分だけ | `LOGGED_OUT` `SUCCESS` 1（前の配備のスモークテストの後のログアウトに当たる） |
| 監査の全体 | 339 以上 | 340 |

- 置き場 `build/count-db-copy/` に `work-*` は残っていない。

### 2.2 配備の後の複写の数え（S5、`--since 2026-11-10T14:54:01Z --set basic,audit`）

| 項目 | 期待 | 結果 |
|---|---|---|
| スキーマの版 | 14 | 14 |
| 初期管理者・利用者・DSL・プレビュー | 2.1 と同じ | 同じ |
| T0 の後の監査 | `LOGIN_SUCCEEDED` 1・`LOGGED_OUT` 1（依頼者の答え「login 1, logout 1」） | `LOGIN_SUCCEEDED` `SUCCESS` 1・`LOGGED_OUT` `SUCCESS` 1、ほか 0 |
| 監査の全体 | 342 | 342 |

- 置き場 `build/count-db-copy/` に `work-*` は残っていない。台本 A3 が `.env` を読んだのは、許しを得た 2 回（2.1・2.2）だけ。

## 3. 片付けとリリース

### 3.1 古いイメージの片付け（順 14、Q4: A）

| 名前 | ID | 理由 | 使うコンテナ |
|---|---|---|---|
| `mastersmith:pre-tz-options-perf` | `ffd2b8380460` | 261101 で配備した版。新しい戻し先より古い | 0 件 |

- `docker system df` のイメージ: 片付けの前 13 個・7.767GB（回収できる 6.389GB）→ 後 12 個・7.563GB（回収できる 6.185GB）。
- 残したもの: `mastersmith:local`（`0d4b6e8866a9`）と戻し先 `mastersmith:pre-ci-checks-release`（`4ee5fb45593e`）。ほかのイメージには触れていない。

### 3.2 リリース

- `main` は、Deployment Pipeline の前に依頼者の承認を得て `a9ddc077` へ fast-forward し、依頼者がプッシュした（前の取り込みは `b2990415`）。取り込みの前の CI の試行の確かめは `construction/build-and-test/test-results.md` と監査の記録のとおり。
- 取り込みの後の最初の `develop` のプッシュ（`24ed24e6`）の CI は1回目で成功（順 1）。CI の `-PitForks=3` の既定での検査の job は 26 分 19 秒で、42 分の線の内（この Intent の Build and Test の 26 分 27 秒とほぼ同じ）。
- タグは付けていない（`team.md` の Deployment）。

## 4. 前の Intent からの引き継ぎ（FR6.3）

| 引き継ぎ元 | 項目 | 結果 |
|---|---|---|
| 261107-verify-quick-local | 配備の段で確かめる項目 | 無し |
| 261107-verify-quick-local | リリースの前の全件の `verify`・前の取り込みの後の CI の試行 | この Intent の Build and Test とリリースの前の確かめで済ませた |
| 261108-ci-size-coverage | CI の要約の差の列 | 依頼者が `develop` のプッシュの CI で確かめた（数字で出ていた） |
| 261108-ci-size-coverage | CI の job の時間 | 39 分 03 秒 → この Intent の `-PitForks=3` で 26 分 27 秒、取り込みの後の最初のプッシュで 26 分 19 秒 |
| この Intent | 取り込みの後の最初の `develop` のプッシュの CI | 1回目で成功（順 1） |

## 5. 上流との差・気づいたこと

- **アプリのコンテナが無かった**: 配備の前に `mastersmith-app-1` が無かった（いつ消えたかは記録から分からない）。内部DB の数え（2.1）では、前の配備の後の監査はログアウト1件だけで、ボリュームを別のものが書き換えた形跡は無い。手順は `cd-config.md` の 5節のとおり、止める操作を省き、入れ替えの操作でコンテナを作った。
- **Intent の外のコミットを配備した**: `04b797d8`（使われていない画面のコードと文言の鍵の削除）。S2 の画面のファイルの比べと S3 の画面の確かめで、配備した版に入ったことと画面が崩れていないことを確かめた。
- `cd-config.md` の 3節の順 13 は、順 1 の時点で CI が終わっていたため、順 1 で済んだ。

## Sources

- 流れ: `operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`
- 質問と答え: `deployment-execution-questions.md`（Q1〜Q4）
- Build and Test: `construction/build-and-test/test-results.md`・`build-and-test-summary.md`
- 確かめ: `node scripts/ci-attempts.mjs --from a9ddc077`・`gh run view 42704002194`、`node scripts/ops-readonly-check.mjs`、`node scripts/count-db-copy.mjs`、`docker compose`・`docker inspect`・`docker logs`・`docker system df`、`shasum -a 256`・`unzip -Z1`
- 前の配備: `aidlc/spaces/default/intents/261104-tz-options-perf/operation/deployment-execution/deployment-log.md`

## Assumptions & Open Questions

None.
