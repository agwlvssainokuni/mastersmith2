# 配備の記録（deployment-log）

Intent 261016-business-data-views の配備の記録です。手順は `operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md` のとおり行いました（順の番号は `cd-config.md` の 3節）。時刻はすべて UTC です。値（パスワード・メールアドレス・DSL の本文）は記録していません。

## 1. 配備した版

| 項目 | 値 |
|---|---|
| 配備したコード | `develop` の先頭。アプリのコードは `d220447`（B7）と同じ。順 1 で `git diff --stat d220447 HEAD -- . ':!aidlc'` が `22de65f` の4ファイル（README・`AbstractBusinessDataReadIT.java`・`frontend/vitest.config.ts`・`perf/README.md`）だけと確かめた。アプリのソースに未コミットの変更は無し |
| WAR | `backend/build/libs/mastersmith.war`、SHA-256 `6426111d63cc41e9f1a8472f7c91adba98be33c1ddc93ab7debfcf69bfe041f4`（順 2 で `./gradlew :backend:bootWar --rerun-tasks` を `caffeinate` で包んで作り直した。作り直す前と同じ値）。中に `cherry/mastersmith/businessdata/` の 178 ファイル（web・service・repository のクラス 50）、`V13__u2_business_data_audit_columns.sql`、`static/assets/` の 92 ファイルがあることを確かめた |
| 新しいイメージ | `mastersmith:local`（`sha256:4cec01e79026…`） |
| 戻し先 | `mastersmith:pre-business-data-views`（`sha256:f103aee1702a…`、前の Intent の版。順 3 で付けた） |
| 内部DB のスキーマ | 版 12 → **13**（V13 の1件が入れ替えの起動で当たった） |
| `.env`・`.env.targetdb` | 書き込みの2項目と見本の書き込みのパスワードを足した（3節） |
| 見本の対象DB | `mastersmith-targetdb-postgres-1` をボリュームを残したまま作り直し、書き込みのアカウント `mastersmith_writer` を足した（3節） |
| Mailpit・otel-lgtm | 入れ替えていない（lgtm は起動していない） |

## 2. 時刻と停止の時間

| 時刻 | 出来事 |
|---|---|
| 2026-10-20 23:48:18 | T0（監査の数え始め）。アプリを止めた |
| 23:48:18〜 | 内部DB を `~/.mastersmith-backup/mastersmith-data-202610210848-pre-business-data-views.tgz`（39,275 バイト、権限 600）へ複写（配備の前のバックアップ）。複写を読み取りで開いて数えた（4節） |
| 停止の間 | `.env` の複写と書き足し、見本の PG の作り直しと `03-writer-account.sh`（3節） |
| 23:49:11 | `docker compose --profile targetdb-postgres up -d --build app` の後に healthy（停止から **約 53 秒**。見込みの約 3〜4 分の内） |
| その後〜 | スモークテスト S1〜S12（`smoke-test-results.md`） |
| 2026-10-21 00:15:48〜00:15:59 | S13 の配備の後の複写（`~/.mastersmith-backup/mastersmith-data-<日時>-after-business-data-views.tgz`、権限 600）。停止から healthy まで **約 11 秒** |
| その後 | 戻しの練習（6節） |

## 3. 書き込みの設定（順 6・順 7）

- 前提: `.env` の `MASTERSMITH_TARGET_DB_WRITE_` で始まる行は 0、`.env.targetdb` の `MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD=` の行は 0。
- `.env` を `$HOME/.mastersmith-env-before-writer`（権限 600）へ中身を表示せずに複写した（書き込みだけを止める手の元。`rollback-runbook.md` 3.1）。
- 乱数のパスワードを表示せずに足した（コマンドは `cd-config.md` 3.2 のとおりで、流す前に依頼者の承認を得た）。値が空でない行の件数は `MASTERSMITH_TARGET_DB_WRITE_USERNAME` 1・`MASTERSMITH_TARGET_DB_WRITE_PASSWORD` 1・`MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD` 1。
- 見本の PG のコンテナだけを作り直して `03-writer-account.sh` を流した。確かめは `1|t|t|t|f|f|f`（ロール 1、`sales.customers` に INSERT・UPDATE・DELETE あり、スキーマの CREATE・データベースの CREATE・PUBLIC の TEMP なし）で期待どおり。番号は開けていない（PORTS は `5432/tcp` だけ）。

## 4. 入れ替えの前の複写で数えたもの（値は表示していない）

| 数えたもの | 結果 | 判断 |
|---|---|---|
| 初期管理者の行・停止・印 | 1 行、停止 0、印 1 | 救済は働かない見込み（Q1: A と合う） |
| `flyway_schema_history` の最大の版 | 12 | 入れ替えで V13 が1件当たる見込み |
| `dsl_applied_revisions` の行と、最新の本文に現れる回数 | 7 行、`customers` 6 回・`customer_orders` 4 回 | 適用中の DSL に見本のテーブルがある。DSL の生成・適用は要らない |
| `dsl_previews` の行 | 0 | — |
| `roles` の行 | 3 | — |
| 初期管理者の割り当て・作業ロールの保存 | 直接 0、グループ経由 3、作業ロールの保存 1 | 見込み（割り当てなし）と違うため、スモークテストを合わせ直した（5節） |

- 数え方: 複写をホームの下の権限 700 の作業用のディレクトリに展開し、H2 2.4.240 の JDBC で `ACCESS_MODE_DATA=r` で開いた。道具はリポジトリの外の `~/.mastersmith-backup/count-db2.sh` で、数だけを出した。
- **決まりとの差**: 1回目の問い合わせで `users` の主キーの列の名前を誤り（`id` ではなく `user_id`）、H2 の誤りの表示に初期管理者のメールアドレス（`example.com` の予約のドメイン）が一度出た。列の名前を直して数え直した。`project.md` の Corrections の「個人に関する値は表示せず、値の有無だけを確かめる」との差で、この記録には値を書いていない。次からは、問い合わせの列の名前を `INFORMATION_SCHEMA` で先に確かめ、誤りの表示を出さない形（誤りの文を捨てて件数だけ出す）で数える。

## 5. 判断

- **救済の見込み**（Q1: A）: 依頼者は前の配備の後に初期管理者を変えていないと答え、入れ替えの前の複写でも停止なし・印ありだった。救済は働いていない（`smoke-test-results.md` の S1・S2・S13）。
- **スモークテストの合わせ直し**（依頼者の決定 A）: 初期管理者にグループ経由のロールの割り当て3つと作業ロールの保存が前からあったため、`deployment-strategy.md` 3節の S5〜S7・S11 と 3.2 の期待の件数を次のように合わせ直した。
  - 確かめ用のロールは「スモーク確認乙」だけを作り、甲は作らない。
  - 作業ロールは、今の作業ロール → 乙 → 元の作業ロールと切り替える（自動で選ばれる流れを使わない）。
  - 片付けで乙の割り当てを外して乙を消す。
  - 期待: `ROLE_CREATED` 1・`ROLE_PERMISSION_CHANGED` 1（保存した回数）・`ROLE_ASSIGNED` 1・`WORK_ROLE_SWITCHED` 2・`BUSINESS_ROW_*` 各 1・`ROLE_UNASSIGNED` 1・`ROLE_DELETED` 1。
- **権限の保存の回数**: 配備の後の複写で `ROLE_PERMISSION_CHANGED` が 2 件だった。依頼者に確かめると権限の設定の保存を2回押していたため、期待を保存した回数の 2 に直し、操作と合うと判定した。
- **確かめ用のロール**: 乙は消した（残していない）。初期管理者の割り当てと作業ロールの保存、ロールの数は配備の前と同じ。
- **配備の前の k6**: 流していない（`cd-config.md` 3.3。Performance Validation に任せる）。
- **配備の完了**: healthy とスモークテスト S1〜S13 がすべて通った（`team.md` の Deployment）。

## 6. 戻しの練習（順 11、`rollback-runbook.md` 6節）

配備の後の複写で、`mastersmith:pre-business-data-views` を `-p mastersmith-rollback`（`127.0.0.1:18080`）で起動しました。配備したアプリは止めていません。lgtm は起動していません。

| # | 確かめ | 結果 |
|---|---|---|
| D1 | 前の版の起動 | 通過。healthy、`/actuator/health` 200。ERROR 0。Flyway の `Migrating schema` 0、「version (13) that is newer than the latest available migration (12)」の WARN 1件。起動は止まらなかった |
| D2 | 書き込みの項目があっても誤りが出ない | 通過。`MASTERSMITH_TARGET_DB_WRITE_` を含む行 0。レベルの件数は INFO 37・WARN 3・ERROR 0 で、WARN は D1 の Flyway の1件、Flyway の H2 の版の案内、Spring の Bean の案内の3件だけ |
| D3 | ★ログインと画面 | 通過。依頼者がログインでき、管理の画面が開け、業務のメニューの項目で前の版の「準備中」の画面になった |
| D4 | ★新しい監査の行のある内部DB での動き | 通過。`6dd9bb0` の `backend/src/main` に `findAllByOrderByOccurredAtAsc` の呼び出しは無い（宣言だけで、呼ぶのはテストだけ）。練習の監査に `LOGIN_SUCCEEDED` 1・`LOGGED_OUT` 1 が追記され、ほかの種類の件数（`BUSINESS_ROW_*` を含む）は配備の後の複写と同じ |
| D5 | ログアウト | 通過。依頼者が練習の画面でログアウトした（`LOGGED_OUT` 1） |

- メモリ: 練習のアプリ 488MiB、配備したアプリ 475MiB。
- 片付け: 数を取り終えた後に、練習のコンテナ・ボリューム・ネットワーク・一時の場所（`$HOME/.mastersmith-rollback-practice`）を消した。`rollback` の名前のコンテナ・ボリュームは 0。配備したアプリは healthy・`/actuator/health` 200 のまま。配備の前と配備の後の複写は残した。
- 判定: 第一の手（イメージだけの戻し）で、V13 の後の内部DB と書き込みの項目のある `.env` のまま前の版が動くことを確かめた。

## 7. 残り（この記録の時点で未実施）

| 順 | すること | 状態 |
|---|---|---|
| 12 | この段の配備の記録のコミット | 済み（`ed713ea`） |
| 13 | `main` への取り込み | 依頼者の承認を得てから行う。前の取り込み（`6dd9bb0`）の後のすべてのコミットの CI を試行ごと（`gh run view --json attempt,jobs`）に確かめ、再実行で通ったものは1回目の失敗とそのテストを記録してから、`main` を `develop` の先頭へ fast-forward する。タグは付けない |
| 14 | 取り込みと push の結果の追記 | `main` の後の `develop` だけのコミットで、この節に追記する |
| 15 | `origin` への push（`develop`・`main`） | 依頼者が行う |

### 7.1 取り込みの前の CI の確かめと、Vite の直し（2026-10-21〜25）

順 13 の確かめで、前の取り込み（`6dd9bb0`）の後の CI のうち、次の2つの run が落ちていた。

| コミット | run | 試行 | 結果 | 失敗 |
|---|---|---|---|---|
| `7f2575c`（CI Pipeline の段の記録） | 39514642704 | 1回目〜3回目 | 失敗 | `osvScan`: `vendor/make-you-chic-ui/package-lock.json` の `vite@8.2.1`（GHSA-587h-7wmj-h5pm、CVSS 7.5、成果物を作る道具の High） |
| `ed713ea`（この段の配備の記録） | 39522990618 | 1回目・2回目 | 失敗 | 同じ `osvScan` の1件 |

- テストの失敗は無く、どちらも新しく公表された脆弱性による（`a031ba1`・`d220447`・`31d434c` の CI の後に公表された見込み）。`frontend` の Vite は 8.3.4 で当たらず、配備した WAR のビルドには使っていない。
- 依頼者の決定「直してから取り込む」で `main` への取り込みを止め、`aidlc/spaces/default/intents/261016-business-data-views/make-you-chic-ui-request-6.md` の依頼を出した。
- 上流の対応は、報告ではなく上流のソース（`git show` を読み取りだけで使う）で確かめた。
  - `ecb07e9` は上流の `main` の先頭で、`b0a1e79` から1コミット、変わったのは `package.json`・`package-lock.json` だけ。
  - lockfile の差は、増えた 1（`@rolldown/binding-android-arm-eabi`）・消えた 0・版の変わった 24 で、`hasInstallScript` は前後とも `fsevents` だけ。
  - `vite` 8.3.4、`undici` 8.12.3、`brace-expansion` 5.0.12、`postcss-selector-parser` 7.1.7、`source-map-js` 1.2.2。`braces` 3.0.3 は直る版が無く残る（開発時だけ使うもの、警告）。
- 固定先の更新は、短命のブランチ `fix/261016-business-data-views-vite` で専用のコミット `5797582`（`b0a1e79` → `ecb07e9`）と README の固定先の直し `4d963c9` を積み、`develop` へ fast-forward で統合した（固定先の更新の例外）。
- 統合の前の確かめ:
  - `./gradlew verify` は 5分27秒で通った。画面のテスト 1975 件 passed、OSV-Scanner は失敗の条件 0 件・警告 15 件。バックエンドのテストは入力が変わらず UP-TO-DATE で飛ばされた（変更は `vendor/` だけ）。
  - E2E（`./gradlew e2eTest`）は 256 件 passed（13分32秒）。流す間だけ見本の PG を 127.0.0.1:15432 を開ける上乗せで作り直し（依頼者の決定 A）、終わった後に上乗せを外して作り直した（`5432/tcp`）。PC のスリープは無かった。
- 依頼者の push の後の CI（run 40147342538、`4d963c9`）は1回目の試行で成功した。検査の job は 31分0秒（00:36:21Z〜01:07:21Z）、OSV-Scanner は失敗の条件 0 件・警告 15 件。
- 配備したアプリは作り直していない。Vite の直しは make-you-chic-ui を組む道具だけで、配備した WAR の中身に当たらないため。

## Sources

- `operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`
- `operation/deployment-execution/deployment-execution-questions.md`（Q1: A、Q2: A）・`memory.md`
- `construction/build-and-test/build-and-test-summary.md`・`test-results.md`、`construction/ci-pipeline/ci-config.md`（配備の前の検査の結果）
- 前の Intent の同じ段の記録: `aidlc/spaces/default/intents/261015-deps-ui-carryover/operation/deployment-execution/`

## Assumptions & Open Questions

- 負荷の下の性能（業務データの k6 の場面、Unverified 12 件）は Performance Validation が持つ。配備の後に性能の問題が出たときは `rollback-runbook.md` の 2節で戻すかを依頼者に諮る。
- 業務データの API の指標・警報の確かめ（Unverified 4 件）は Observability Setup が持つ。
