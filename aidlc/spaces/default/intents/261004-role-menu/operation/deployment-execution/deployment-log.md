# 配備の記録（deployment-log）

Intent 261004-role-menu（F: ロールベースの権限、I: N 階層のメニュー）の配備の記録です。手順は `operation/deployment-pipeline/cd-config.md`（3節の 11 の順）・`deployment-strategy.md`・`rollback-runbook.md`、環境は `operation/environment-provisioning/environment-inventory.md`・`validation-report.md`。質問と決定は `deployment-execution-questions.md`（Q1: A 今から配備する、Q2: A 初期管理者を画面で変えていない、Q3: A 確かめ用のロールとグループは最後に消す、まとめの確認 Looks correct）。

この記録は、順 1〜3 と順 4 の準備までを書いた時点のものです（配備したアプリは止めていない）。順 4 以降は、依頼者が順 4 を終えた後に書き足します。

- 秘密の値・個人に関する値は書きません。`.env` は開いていません。
- 時刻は UTC（括弧の中は JST）。

## 1. 時系列（2026-10-13 UTC ＝ 2026-10-14 JST）

| 時刻（UTC） | 順 | 出来事 |
|---|---|---|
| 23:14:00（08:14:00） | 1 | 関門の確かめを始めた（git・CI・WAR。すべて読み取りだけ） |
| 23:14 頃 | 1 | 関門の確かめがすべて通った（2節） |
| 23:14:24（08:14:24） | 2 | `docker tag mastersmith:local mastersmith:pre-role-menu` を実行し、ID が同じことを確かめた（3節） |
| 23:14 頃 | 2 | 配備したアプリは `Up 6 days (healthy)` のまま、`/actuator/health` は 200 |
| 23:14 頃 | 3 | 救済の見込みを記録した（4節）。アプリは止めていない |
| — | 4 | 準備まで（5節）。版 1 のプレビューの破棄と T0 の控えは、依頼者の了承の後に行う |
| 23:15:41（08:15:41） | 4 | T0（監査の数え始め）を指揮役が控えた |
| 23:17:39〜23:17:46 | 4 | 前の版のアプリのログに `REFRESH_FAILED`（401）の WARN が3件（依頼者のブラウザの古いリフレッシュトークン。ログイン画面に戻った） |
| 23:17:51 | 4 | `LOGIN_SUCCEEDED` 1件（依頼者のログイン） |
| 23:17:53 | 4 | 前の版のアプリのログに `DSL_PREVIEW_NOT_FOUND`（404）の WARN が1件（6節） |
| 23:18:29 | 4 | `LOGGED_OUT` 1件（依頼者の報告に無かった操作。6節） |
| 23:20:06（08:20:06） | 5 | `docker compose stop app`（終了コード 143。ここからアプリが止まる） |
| 23:20:07 | 5 | 配備の前の複写 `~/.mastersmith-backup/mastersmith-data-202610140820-pre-role-menu.tgz`（21,047 B、権限 600）を作った |
| 23:20 頃 | 5 | 複写を読み取りで開いて数えた（7節）。救済の見込みどおり |
| 23:20:47 | 6 | WAR の SHA-256 を確かめ直し（同じ）、`docker compose --profile targetdb-postgres up -d --build app` を始めた |
| 23:20:49 | 6 | 新しいコンテナが起動した |
| 23:20:57（08:20:57） | 6・7 | healthy。アプリが止まっていたのは 23:20:06〜23:20:57 の約 51 秒 |
| 23:21 頃 | S1・S2 | 起動のログ・移行・JVM の設定を確かめた（8節・9節）。依頼者の画面の操作（S3 以降）を待つ |

## 2. 順 1: 関門

| 確かめ | 結果 | 判定 |
|---|---|---|
| ブランチ | `develop` | 通る |
| `develop` の先頭と `origin/develop` | どちらも `2669c62`（Environment Provisioning を承認）。同じ | 通る |
| 未コミットの変更 | `aidlc/` の下だけ（監査の記録 `audit/sakura-local-4e42a93f87ce.md` の変更と、この段のディレクトリ `operation/deployment-execution/`）。アプリのソースには無い（`project.md` の学びどおり `aidlc/` の下は除いて判断） | 通る |
| アプリのコードが `005730a` と同じこと | `git diff 005730a HEAD -- . ':!aidlc'` は `README.md` の1行の追加だけ（OSV-Scanner を手元で流し直す `./gradlew osvScan --rerun` の案内）。`005730a` は `HEAD` の祖先。`005730a..HEAD` は4コミット（`ae794e4`・`91cd83e`・`a3feff8`・`2669c62`） | 通る |
| サブモジュールの固定先 | `vendor/make-you-chic-ui` が `5bf1ffe`、`vendor/java-mustache-processor` が `8d44c36`。`005730a` からの差は無い | 通る |
| `005730a` の CI | run 38432556919（push、`develop`、headSha `005730a`）が試行 1 回目で `success`（ジョブ `./gradlew verify` が success、`リリースに WAR を添付する` は skipped。タグの push でないため） | 通る |
| その後の CI（参考） | `ae794e4`・`91cd83e`・`a3feff8` の push の CI は success。`2669c62` の push の CI（run 38482643592）は確かめた時点で実行中（`aidlc/` の記録だけのコミット） | 参考 |
| WAR の SHA-256 | `backend/build/libs/mastersmith.war`（102,383,237 B、2026-10-14 00:45:50 JST 作成）の SHA-256 は `517c847b4764733868223f543326ae3f9173a28f54c3a07068971730bef17e10`。先頭 16 文字が `environment-inventory.md` 5節の `517c847b47647338` と同じ | 通る |
| WAR の直しの値 | `WEB-INF/classes/cherry/mastersmith/role/service/RoleTransferLimits.class` の `javap -constants` で `DEFAULT_MAX_BYTES = 5242880`・`MAX_EXPANDED_NODES = 500000`。`environment-inventory.md` 5節と同じ | 通る |
| `main` の fast-forward の可否（参考） | `main`（`d5aea52`）は `develop` の祖先。順 10 で fast-forward できる | 参考 |

- verify・CI・E2E は、`cd-config.md` の 3.1 のとおり Build and Test と CI Pipeline の結果を正とし、流し直していない。この段では `./gradlew` を流していない（WAR を変えないため）。

## 3. 順 2: 戻し先のタグ

| 項目 | 値 |
|---|---|
| タグを付ける前 | `mastersmith:pre-role-menu` は無かった。動いている `mastersmith-app-1` のイメージは `sha256:fefeafbd299be1678e7be939ee7d9ef1f239ff13af3597c9f8a173fe8430da35`（`mastersmith:local`、作成 2026-10-04 12:42:49 JST） |
| 実行 | `docker tag mastersmith:local mastersmith:pre-role-menu`（23:14:24 UTC） |
| タグを付けた後 | `mastersmith:pre-role-menu` と `mastersmith:local` の ID がどちらも `sha256:fefeafbd299be1678e7be939ee7d9ef1f239ff13af3597c9f8a173fe8430da35`。`rollback-runbook.md` の「`sha256:fefeafbd299b…` であること」を満たす |
| `mastersmith` のタグの数 | 18（前の 17 に `pre-role-menu` が加わった）。イメージは消していない |
| アプリ | 止めていない。`Up 6 days (healthy)`、`/actuator/health` は 200 |

## 4. 順 3: 救済の見込み

- 依頼者の答え（Q2: A）: 前の配備（261004-safety-carryover、2026-10-04）の後に、初期管理者のパスワードを画面で変えた・利用を止めた・管理者の印を外した、のどれもしていない。
- 見込み: 新しい版の起動で救済は働かない（`INITIAL_ADMIN_RESCUED` 0 件、起動のログに救済の WARN 0 件）。
- この時点ではアプリを止めないため、内部DB の複写での数え（`.env` の初期管理者の利用者の `suspended`・`admin_flag`、`admin_flag` が真の行の数、`dsl_applied_revisions`・`dsl_previews` の行の数）は、順 5 の入れ替えの前の複写で行う（`cd-config.md` の 4節）。数えた結果が見込みと違えば、入れ替え（順 6）の前に依頼者の了承を得る。

## 5. 順 4 の準備（版 1 のプレビューの破棄）

- 依頼者が今の版（`273c021`、版 1 の DSL）の画面で行う。秘密（ログイン）が要るため AI は行わない。
- 監査の数え始めの時刻 T0 は、依頼者の了承を得た後、依頼者が画面を開く（ログインする）**前**に AI が `date -u +%Y-%m-%dT%H:%M:%SZ` で控え、この記録に書き足す。ログインし直したときの `LOGIN_SUCCEEDED` も T0 以降に数えるため。
- 依頼者に伝える手順（文言は `273c021` の `frontend/src/features/dsl/messages.ts` と `DslPreviewPanel.tsx`・`DslConfirmDialog.tsx` で確かめた）:
  1. `http://localhost:8080/` を開く（`127.0.0.1` では開かない）。ログインしていなければ初期管理者でログインする。
  2. 左のサイドバーの「DSL」（`/admin/dsl`）を開く。
  3. 上の「今の状態」の「プレビュー」の欄を見る。「プレビューはありません」なら何もしないで終わる（`DSL_PREVIEW_DISCARDED` は 0 件）。
  4. プレビューがあるときは、「プレビュー」のタブで「破棄する」を押す。「プレビューを破棄しますか」の確かめが出たら、もう一度「破棄する」を押す（「やめる」に初めのフォーカスがある）。「プレビューを破棄しました」と出て、「今の状態」の「プレビュー」が「プレビューはありません」になることを見る。
  5. 「スキーマを読み込む」「投入する」「適用する」「プレビューに戻す」は押さない（版 1 の DSL が増える・置き換わり、監査に残るため）。
  6. 終わったら、プレビューがあったか（破棄したか）と、ログインしたか（何回か）、ほかに監査に残る操作（ログインの失敗など）をしたかを AI に伝える。
- 監査に残るもの: 破棄したとき `DSL_PREVIEW_DISCARDED` 1 件（結果は成功）。ログインしたとき `LOGIN_SUCCEEDED` がその回数だけ。どちらも追記だけで消せない。
- 破棄した後の確かめは、順 5 の複写で `dsl_previews` が 0 行であることで行う。

## 6. 順 4 の結果（版 1 のプレビューの破棄）

- 依頼者の答え（指揮役が記録）: プレビューがあって画面で破棄した、ログイン 1 回、ほかに監査に残る操作は無い。
- 複写（7節）で数えた T0 以降の監査は `LOGIN_SUCCEEDED` 1・`LOGGED_OUT` 1 だった。**`DSL_PREVIEW_DISCARDED` は 0 件**で、見込みの 1 件と違う。`LOGGED_OUT` 1 件は見込みに無かった。
- 2026-10-13 の 1 日を広げて数えても `DSL_PREVIEW_DISCARDED` は無い。内部DB の中で最後の `DSL_PREVIEW_DISCARDED` は 2026-09-25、最後の `DSL_GENERATED` は 2026-09-29 13:01:34Z、その直後の `DSL_APPLIED` は 2026-09-29 13:01:51Z だった（適用でプレビューは無くなる）。
- 前の版のアプリのログでは、ログインの 2 秒後（23:17:53Z）に `DSL_PREVIEW_NOT_FOUND`（404）の WARN が1件あった。`273c021` の `DslLifecycle` では、プレビューの読み出しと破棄のどちらも、プレビューが無いときにこの 404 を返す。どちらでも監査には残らない（破棄の拒否は指標だけ）。
- 見立て（未確かめ）: T0 の時点で版 1 のプレビューは既に無く（9 月 29 日の適用の後に置かれていない）、23:17:53Z の 404 は DSL の画面を開いたときのプレビューの読み出しによるもの。依頼者が見た「プレビュー」は、プレビューの欄ではなく別の表示（適用中の DSL など）だった可能性がある。
- 判断: 破棄の目的（入れ替えの前に版 1 のプレビューが無いこと）は、複写の `dsl_previews` が 0 行であることで満たした。監査の期待の件数は `DSL_PREVIEW_DISCARDED` 0・`LOGIN_SUCCEEDED` 1・`LOGGED_OUT` 1 に直す見込みで、依頼者に確かめる（`project.md` の学び）。救済の見込みとは関係しないため、入れ替えを止めずに進めた。

## 7. 順 5: 入れ替えの前の複写と数え

- 置き場: `~/.mastersmith-backup/`（権限 700）。ファイル `mastersmith-data-202610140820-pre-role-menu.tgz`（21,047 B、権限 600）。中身は表示していない。
- 数え方: 複写を権限 700 の作業用のディレクトリに展開し、H2 2.4.240 の `org.h2.tools.Shell` で `ACCESS_MODE_DATA=r`（読み取り）で開いた（H2 Shell は文を表示しない）。初期管理者の照合は、`.env` の `MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL` の値をシェルの変数に読み込んで問い合わせの条件に使い、値は表示していない。数えた後、作業用のディレクトリは消した。スクリプトは `~/.mastersmith-backup/count-db.sh`（リポジトリの外）。

| 数えたもの | 結果 | 見込み | 判定 |
|---|---|---|---|
| 初期管理者（`.env` のメールアドレス）の行 | 1 行。停止 0・管理者の印 1 | 停止していない・印あり | 見込みどおり |
| 利用者の全体 | 2 人。管理者の印 2・停止していない管理者 2・停止中 0 | 記録（戻しの練習の比べの元） | — |
| `dsl_applied_revisions` | 5 行 | 1 以上なら S1 で ERROR 1 件 | ERROR 1 件を見込む |
| `dsl_previews` | 0 行 | 0 | 見込みどおり |
| T0 以降の `audit_events` | `LOGIN_SUCCEEDED` SUCCESS 1・`LOGGED_OUT` SUCCESS 1 | `DSL_PREVIEW_DISCARDED` 1・`LOGIN_SUCCEEDED` 1 | 違う（6節） |
| Flyway の版 | 9 | 9 | 見込みどおり |

- 救済の見込み: 停止なし・印ありで、条件 (a)・(b) に当たらない。条件 (c)（パスワードの不一致）は、ハッシュを照合する手段を使わず、依頼者の答え（Q2: A）に頼った。起動のログで救済の WARN が 0 件であることで裏付けた（9節）。

## 8. 順 6・7: 入れ替えと起動の確かめ

| 確かめ | 結果 | 判定 |
|---|---|---|
| WAR（入れ替えの直前） | SHA-256 `517c847b4764733868223f543326ae3f9173a28f54c3a07068971730bef17e10`（2節と同じ）。イメージの中の `/app/mastersmith.war` も同じ値 | 通る |
| 配備したイメージ | `mastersmith:local` ＝ `sha256:e41a5b02fd0bee35c7718b92854ec77b66beced7a5481e9135a697127994462c`（イメージの作成時刻は 2026-10-14 00:45:51 JST。ビルドのキャッシュの層をそのまま使ったため） | 記録 |
| 戻し先 | `mastersmith:pre-role-menu` ＝ `sha256:fefeafbd299b…`（変わっていない） | 通る |
| 健全性 | 23:20:57Z に healthy（起動から約 8 秒）。`/actuator/health` は 200・`{"status":"UP"}` | 通る |
| 上限 | メモリ 2,147,483,648 B（2g）・CPU 4（NanoCpus 4,000,000,000）・`restart: no`・OOMKilled false | 通る |
| JVM の設定 | PID 1 のコマンド行に `-XX:+ExitOnOutOfMemoryError` が 1 件、`-XX:MaxRAMPercentage=50.0` が 1 件 | 通る |
| 移行 | Flyway は「Current version of schema "PUBLIC": 9」の後、`Migrating schema` が 3 件（10 - u3 group・11 - u4 role・12 - u4 role assignment）、「Successfully applied 3 migrations … now at version v12」 | 通る |
| ほかのコンテナ | `mastersmith-mailpit-1`・`mastersmith-targetdb-postgres-1` は動いたまま（止めていない） | 通る |

## 9. S1・S2: 起動のログ（値は表示していない）

| 確かめ | 結果 | 判定 |
|---|---|---|
| ログの行数とレベル | 43 行（すべて JSON）。INFO 39・WARN 2・ERROR 1 | — |
| ERROR | `DslStartupLoader`「適用中の DSL を読めないため、適用中の DSL が無い状態で起動します」1 件だけ（`dsl_applied_revisions` が 5 行のため見込みどおり）。キーは `dsl.errorKinds`・`dsl.hash`・`dsl.revisionId` などで、本文や個人に関する値は無い | 通る |
| WARN | Flyway の H2 の版の案内 1 件と、Spring の Bean の案内（`stringOrNumberMigrationVersionConverter`）1 件だけ。どちらも前の版から出ているもの | 通る |
| 救済 | WARN「初期管理者を救済しました」0 件。INFO「初期管理者は既にいるため、作成しませんでした」1 件（キー `maskedEmail` あり、キー `email` 無し） | 見込みどおり（救済は働かない） |
| メールアドレスの漏れ | `.env` の初期管理者のメールアドレスの値が起動のログに現れる件数 0（値は表示せず件数だけ）。キー `email` を持つ行 0 | 通る |
| 監査 `INITIAL_ADMIN_RESCUED`・`INITIAL_ADMIN_CREATED` | ログからは救済も作成も無い。監査の件数は S11 の複写で確かめる | S11 で確かめる |

S1・S2 は通った。S3 以降（依頼者の画面の操作）は、依頼者の了承の後に行う。

## 10. S8 の再現（使い捨ての環境）

依頼者の答え（S8 の扱い A）で、`perf/README.md` の形の使い捨ての環境で S8 の再現を試した。配備したアプリ・mailpit・見本の対象DB は止めず、変えていない。値・秘密は表示も記録もしていない。

| 項目 | 内容 |
|---|---|
| 時刻 | 2026-10-14 03:30:20Z に起動し、03:37:37Z に片付けた |
| 環境 | compose の project 名 `mastersmith-s8`（`docker/perf/compose.yaml`）。番号は `127.0.0.1:18080`。イメージは配備と同じ `mastersmith:local`（`sha256:e41a5b02fd0b…` を確かめた）。上限は CPU 2・メモリ 1g。対象DB は perf の compose の profile `targetdb-postgres`（見本のスキーマ `sales`、読み取りだけのアカウント）を使い捨てで起動した。監視（lgtm）は起動していない |
| 仮の値 | 署名鍵・初期管理者（`example.test`）・対象DB のパスワードを乱数で作った。置き場はホームの下の権限 700 の一時ディレクトリ |
| 手順 | 仮の管理者でログイン（API）→ 既定の DSL の生成（201）・適用（200）→ ロールを2つ作成（201）→ テーブルの段だけに主権限 READ を保存（甲は `customers`、乙は `orders`。どちらも 204）→ 自分に割り当て（204）→ 作業ロールを乙に切り替え（204） |
| 比べた条件 | 作業ロールなし（ロールを割り当てる前）と、作業ロール乙（テーブルの段だけに設定）。スキーマの段だけに設定した場合は試していない |

### 10.1 API（`curl`、`--max-time 30`）

| 要求 | 状態コード | 本文 | JSON として | かかった時間 |
|---|---|---|---|---|
| `/api/me/permissions/schemas`（作業ロールなし） | 200 | 199 B | 正しい（`workRole` は null、`sales` は NONE） | 0.018 秒 |
| `/api/me/permissions/schemas`（乙） | 200 | 224 B。`Content-Length` どおり最後まで届いた | 正しい | 0.028 秒 |
| `/api/me/permissions/tables?schema=sales`（乙） | 200 | 1,025 B。最後まで届いた | 正しい（`orders` だけ READ・`inMenu` 真、ほかは NONE） | 0.008 秒 |
| `/api/me/permissions/columns?schema=sales&table=orders` | 200 | 984 B。最後まで届いた | 正しい（カラムはすべて READ） | 0.010 秒 |
| `/api/me/permissions/columns?schema=sales&table=customers` | 200 | 1,016 B。最後まで届いた | 正しい | 0.008 秒 |

項目の名前と型は、画面の `parseMyPermissions` の確かめと合っている（`roleId` は数、`tableName`・`columnName` は null か文字、`effective` の `main` は NONE・READ・FULL、`create`・`delete` は真偽値）。

### 10.2 画面（Playwright のヘッドレスの Chromium。`frontend/node_modules` の playwright を、リポジトリの外の台本から使った）

| 場面 | 結果 |
|---|---|
| ホームから画面の中の移動で `/me/permissions` を開く | `schemas` 200 の後、約 1 秒で木（`sales`）と「左の木から対象を選んでください。」が出た。木を開くと `tables` が 200 でテーブルが5つ並び、`orders` を選ぶと `columns` が 200 で READ と「業務のメニュー: 出る」とカラムの表が出た |
| 直接の URL で開く（読み込み直し） | 更新（200）→ 作業ロール（200）→ `schemas`（200）の後、すぐに木が出た |
| 画面の上で作業ロールを切り替える | `schemas` を読み直し（200 が2件）、新しい作業ロールで出し直した |
| 切り替えのメニューの「自分の権限を見る」から開く | すぐに木が出た |
| アクセストークンの寿命を 20 秒にして、切れた後に木を開く・選ぶ・入口から開く | `tables`・`columns`・`schemas` がいったん 401 → 更新（200）→ 送り直し（200）となり、どれも表示された |
| 狭い幅（390 px）で同じ3つの場面 | どれもすぐに表示された |

どの場面でも、「読み込んでいます」が残る・誤りになる症状は出なかった。

### 10.3 ログと眠り

- 使い捨てのアプリの WARN・ERROR は、起動の案内2件（Flyway・Spring）と `REFRESH_FAILED` 401 が2件だけだった。後者は、Cookie の無いブラウザが最初の画面で更新を試すもので、見込みどおり。誤りの応答と例外は無かった。
- 台本は `caffeinate -dims` で包んだ。`pmset -g log` で、試した時間（12:30〜12:37 JST）に Sleep・DarkWake は無かった。

### 10.4 見分け

- A（本文の途中での失敗）: 再現しない。どの段も `Content-Length` どおりに本文が届き、JSON として正しく、10〜30 ミリ秒で返った。
- B（401 から更新で止まる）: 再現しない。トークンが切れた後も、更新と送り直しで表示された。
- 配備したイメージ・既定の DSL（版 2）・テーブルの段だけの設定・作業ロールの切り替えという組では起きないため、原因は使い捨ての環境と配備した環境の差にあると見られる。候補は、依頼者のブラウザ（種類、前の版のときから開いていたタブ・保存された資源、ほかのタブ）、配備した内部DB の中身（前からある利用者・グループ・ロール・DSL の履歴）、対象DB の設定（スキーマ）。どれも確かめていない。
- 次の見分けには、依頼者が配備したアプリで、ブラウザの開発者ツールを開いたまま S8 をもう一度行い、Network の `/api/me/permissions/*` の状態コード・大きさ・時間と、Console の誤りを見ることが要る（ロールの作成などの監査に残る操作を伴うため、行うかは依頼者が決める）。

### 10.5 片付け

03:37:37Z に `docker compose -p mastersmith-s8 … down -v` で、コンテナ・ボリューム（`perf-data`・`perf-targetdb-postgres`）・ネットワークを消し、一時ディレクトリ（仮の鍵・資格情報・台本・応答の写し）も消した。`mastersmith-s8` のコンテナ・ボリュームが残っていないことと、配備したアプリ・mailpit・見本の対象DB が動いたままであることを確かめた。

## 11. 直した版の配備（S8 の不具合の直し a16cd68）

S8 の不具合（「自分の権限を見る」の木で、テーブルの節を開くと自分自身を子に持ち描画が止まらない。10節・`fix-notes.md`）を直した版を、`cd-config.md` の順 5〜7 と S1 に準じて配備し直した。スキーマの変更は無い。

### 11.1 時系列（2026-10-14 UTC）

| 時刻（UTC） | 出来事 |
|---|---|
| 07:54:04（16:54:04 JST） | 関門の確かめを始めた（11.2） |
| 07:54 頃 | 前の版（`e41a5b02fd0b`）が配っている `MyPermissionsPage` の資源に直しの形が無いこと（0 件）を、比べの元として確かめた |
| 07:54:29（16:54:29 JST） | `docker compose stop app`（ここからアプリが止まる） |
| 07:54:30 | 初回の配備の後の複写 `~/.mastersmith-backup/mastersmith-data-202610141654-after-role-menu.tgz`（29,028 B、権限 600）を作った（初回の配備の後のバックアップを兼ねる） |
| 07:54 頃 | 複写を読み取りで開いて数えた（11.3） |
| 07:54:52 | 前の版のイメージに `mastersmith:role-menu-before-s8fix` のタグを付け、WAR の SHA-256 を確かめ直した（同じ）。`docker compose --profile targetdb-postgres up -d --build app` を始めた |
| 07:54:57 | 新しいコンテナが起動した |
| 07:55:07（16:55:07 JST） | healthy。アプリが止まっていたのは 07:54:29〜07:55:07 の約 38 秒 |
| 07:55:21 | 起動の確かめと S1 を終えた（11.4・11.5）。依頼者の S8 の確かめ直しを待つ |

### 11.2 関門

| 確かめ | 結果 | 判定 |
|---|---|---|
| `develop` の先頭 | `a16cd68`（自分の権限の木でテーブルの節に開閉を出さない）。`origin/develop` は `2669c62` のまま（push は依頼者が行う） | 通る |
| 作業ブランチの最後とのソースの差 | `git diff 08d1e71 a16cd68` は空（0 行） | 通る |
| 未コミットの変更 | `aidlc/` の下の監査の記録だけ | 通る |
| `876042b` からのアプリの差 | `frontend/` の4ファイルだけ（`useMyPermissions.ts` の直し、そのテスト2つ、E2E 160 の2行）。`005730a` からの差は、これに README の1行を足した5ファイル | 通る |
| 移行・サブモジュール | `005730a` から `db/migration/` に差は無い。サブモジュールの固定先も同じ（`5bf1ffe`・`8d44c36`） | 通る |
| WAR | `backend/build/libs/mastersmith.war`（102,383,236 B、2026-10-14 15:47:53 JST）の SHA-256 は `f5293bed7fe2062271a34b84b48586d28431f294d75b1045ed459f0b8c58a376`（指揮役が知らせた値と同じ） | 通る |
| WAR の中の直し | `WEB-INF/classes/static/assets/MyPermissionsPage-Cq9CMLeF.js` に直しの形（`hasChildren` を `tableName===null` で絞る形）が1件ある | 通る |

### 11.3 入れ替えの前の複写と数え（T0 ＝ 2026-10-13T23:15:41Z、T1 ＝ 2026-10-14T06:17:59Z）

数え方は7節と同じ（H2 Shell、`ACCESS_MODE_DATA=r`、値は表示しない。作業用のディレクトリは数えた後に消した）。

| 数えたもの | 結果 |
|---|---|
| 初期管理者（`.env` のメールアドレス） | 1 行。停止 0・管理者の印 1（救済の条件 (a)・(b) に当たらない） |
| 利用者 | 2 人。管理者の印 2・停止中 0（7節と同じ） |
| ロール・権限の設定 | ロール 2・権限の設定 0 行 |
| 割り当て | 利用者へのロールの割り当て 2・グループへの割り当て 0・作業ロールの保存 1 |
| グループ | グループ 0・メンバー 0 |
| DSL | 適用の履歴 6 行（7節の 5 行から S4 の適用で 1 増えた）・プレビュー 0 行 |
| Flyway の版 | 12 |
| 監査の最後の行 | 2026-10-14 06:21:43Z |

T0 以降の監査（結果はすべて `SUCCESS`。結果が失敗の行は 0）:

| 種類 | T0〜T1 | T1 以降 | 合計 |
|---|---|---|---|
| `LOGIN_SUCCEEDED` | 2 | 0 | 2 |
| `LOGGED_OUT` | 1 | 0 | 1 |
| `DSL_GENERATED` | 1 | 0 | 1 |
| `DSL_APPLIED` | 1 | 0 | 1 |
| `ROLE_CREATED` | 2 | 2 | 4 |
| `ROLE_PERMISSION_CHANGED` | 2 | 0 | 2 |
| `ROLE_ASSIGNED` | 2 | 2 | 4 |
| `WORK_ROLE_SWITCHED` | 1 | 1 | 2 |
| `GROUP_CREATED` | 1 | 0 | 1 |
| `GROUP_MEMBER_ADDED` | 1 | 0 | 1 |
| `GROUP_MEMBER_REMOVED` | 1 | 0 | 1 |
| `GROUP_DELETED` | 1 | 0 | 1 |
| `ROLE_UNASSIGNED` | 2 | 0 | 2 |
| `ROLE_DELETED` | 2 | 0 | 2 |
| `DSL_PREVIEW_DISCARDED`・`INITIAL_ADMIN_RESCUED`・`INITIAL_ADMIN_CREATED` | 0 | 0 | 0 |

- T1 以降の操作: ロールの作成 2・割り当て 2・作業ロールの切り替え 1 だけ。権限の設定の保存（`ROLE_PERMISSION_CHANGED`）は T1 以降 0 件で、残っているロール甲・乙は権限なしという指揮役の記録と合う。T1 以降のログインは 0 件（ログイン中のまま操作した見込み）。
- T0〜T1 の分は、初回の S3〜S10（作成・権限・割り当て・切り替え・グループ・片付け）の見込みとそろう。ログイン 2・ログアウト 1 のうち、ログイン 1・ログアウト 1 は順 4 の分（6節）。

### 11.4 入れ替えと起動の確かめ

| 確かめ | 結果 | 判定 |
|---|---|---|
| 配備したイメージ | `mastersmith:local` ＝ `sha256:3247c5bdab79b19a7cef28b8ca602b14a49914fadf7758064ebc8d528eff8292`（作成 2026-10-14 16:54:54 JST） | 記録 |
| 戻し先 | `mastersmith:pre-role-menu` ＝ `sha256:fefeafbd299b…`（初回の前の版。消していない）、`mastersmith:role-menu-before-s8fix` ＝ `sha256:e41a5b02fd0b…`（直す前の版。タグを付けて残した） | 通る |
| イメージの中の WAR | `/app/mastersmith.war` の SHA-256 は `f5293bed…8c58a376`（11.2 と同じ） | 通る |
| 健全性 | 07:55:07Z に healthy（起動から約 10 秒）。`/actuator/health` は 200・`{"status":"UP"}` | 通る |
| 上限 | メモリ 2g（2,147,483,648 B）・CPU 4・`restart: no`・OOMKilled false | 通る |
| JVM の設定 | PID 1 のコマンド行に `-XX:+ExitOnOutOfMemoryError` 1 件・`-XX:MaxRAMPercentage=50.0` 1 件 | 通る |
| 移行 | 「Current version of schema "PUBLIC": 12」「Schema "PUBLIC" is up to date. No migration necessary.」。新しい移行は無い | 通る |
| 配っている画面の資源 | `/` の `index-CpFR0PzS.js` が読む `MyPermissionsPage-Cq9CMLeF.js`（WAR の中と同じ名前）に直しの形が1件。前の版の `MyPermissionsPage-D4hwEj89.js` では 0 件だった | 通る |
| ほかのコンテナ | Mailpit・見本の対象DB は動いたまま | 通る |

### 11.5 S1: 起動のログ（値は表示していない）

| 確かめ | 結果 | 判定 |
|---|---|---|
| 行数とレベル | 39 行（すべて JSON）。INFO 36・WARN 2・ERROR 0 | 通る |
| ERROR | 0 件（版 2 の DSL が適用済みのため、「適用中の DSL を読めない」は出ない） | 見込みどおり |
| WARN | Flyway の H2 の版の案内 1 件と、Spring の Bean の案内 1 件だけ（前の版から出ているもの） | 通る |
| 救済 | 救済の WARN 0 件。INFO「初期管理者は既にいるため、作成しませんでした」1 件（キー `maskedEmail` あり、`email` 無し） | 見込みどおり |
| メールアドレスの漏れ | `.env` の初期管理者のメールアドレスの値がログに現れる件数 0、キー `email` を持つ行 0 | 通る |

S1 は通った。S8 の確かめ直しと片付けは、依頼者の画面の操作を待つ。

## 12. S11: 配備の後の複写と監査の数え

### 12.1 時系列（2026-10-14 UTC）

| 時刻（UTC） | 出来事 |
|---|---|
| 07:56:31 | T2（S8 の確かめ直しの数え始め。指揮役が控えた） |
| 13:06:12〜13:09:45 | T2 以降の監査の行 7 件（12.3） |
| 13:10:43 | 依頼者の答え（指揮役が記録）: S8 の確かめ直しはすべて見込みどおり、片付けも済んだ |
| 13:11:05（22:11:05 JST） | `docker compose stop app` |
| 13:11:06 | 配備の後の複写 `~/.mastersmith-backup/mastersmith-data-202610142211-after-role-menu.tgz`（30,590 B、権限 600）を作り、`docker compose start app` で同じコンテナを起動し直した |
| 13:11:14 | healthy・`/actuator/health` 200。アプリが止まっていたのは約 9 秒 |

### 12.2 状態（数え方は7節と同じ。値は表示していない）

| 数えたもの | 結果 | 見込み | 判定 |
|---|---|---|---|
| 初期管理者 | 1 行、停止 0・管理者の印 1 | 同じ | 通る |
| 利用者 | 2 人、管理者の印 2・停止 0 | 配備の前（7節）と同じ | 通る |
| ロール | **2** | 0 | **違う** |
| 権限の設定 | **2 行** | 0（ロールを消すと一緒に消える） | **違う** |
| 利用者へのロールの割り当て | **2** | 0 | **違う** |
| 作業ロールの保存 | **1** | 0 | **違う** |
| グループへの割り当て・グループ・メンバー | 0・0・0 | 0 | 通る |
| DSL | 適用の履歴 6・プレビュー 0 | 同じ | 通る |
| Flyway の版 | 12 | 12 | 通る |

### 12.3 監査（結果はすべて `SUCCESS`、失敗の行は 0）

| 種類 | T0〜T1 | T1〜T2 | T2 以降 | 合計 | T2 以降の見込み |
|---|---|---|---|---|---|
| `LOGIN_SUCCEEDED` | 2 | 0 | 0 | 2 | 回数どおり（0 なら合う） |
| `LOGGED_OUT` | 1 | 0 | 0 | 1 | 0 |
| `DSL_GENERATED` | 1 | 0 | **1** | 2 | 0 |
| `DSL_PREVIEW_DISCARDED` | 0 | 0 | **1** | 1 | 0 |
| `DSL_APPLIED` | 1 | 0 | 0 | 1 | 0 |
| `ROLE_CREATED` | 2 | 2 | 0 | 4 | 0 |
| `ROLE_PERMISSION_CHANGED` | 2 | 0 | 2 | 4 | 2 |
| `ROLE_ASSIGNED` | 2 | 2 | 0 | 4 | 0 |
| `WORK_ROLE_SWITCHED` | 1 | 1 | 3 | 5 | 回数どおり |
| `ROLE_UNASSIGNED` | 2 | 0 | **0** | 2 | **2** |
| `ROLE_DELETED` | 2 | 0 | **0** | 2 | **2** |
| `GROUP_CREATED`・`GROUP_MEMBER_ADDED`・`GROUP_MEMBER_REMOVED`・`GROUP_DELETED` | 各 1 | 0 | 0 | 各 1 | 0 |
| `INITIAL_ADMIN_RESCUED`・`INITIAL_ADMIN_CREATED` | 0 | 0 | 0 | 0 | 0 |

T2 以降の行の順（時刻 UTC と種類だけ）: 13:06:12 `ROLE_PERMISSION_CHANGED` → 13:07:16 `WORK_ROLE_SWITCHED` → 13:07:53 `ROLE_PERMISSION_CHANGED` → 13:07:59 `WORK_ROLE_SWITCHED` → 13:08:50 `DSL_GENERATED` → 13:09:26 `DSL_PREVIEW_DISCARDED` → 13:09:45 `WORK_ROLE_SWITCHED`。アプリのログの T2 以降の WARN は、13:08:46Z の `DSL_PREVIEW_NOT_FOUND`（404）1件だけ（ERROR は 0）。

### 12.4 判定

- S8 の確かめ直しの操作（権限の保存 2、作業ロールの切り替え）は監査で裏付けられた。
- **片付け（割り当てを外す・ロールを消す）は内部DB に反映されていない**。T2 以降の `ROLE_UNASSIGNED`・`ROLE_DELETED` は 0 件、ロール 2・割り当て 2・作業ロールの保存 1・権限の設定 2 が残っている。片付けを試みた失敗の行も、ログの WARN・ERROR も無いため、片付けの要求はサーバーに届いていない見込み（依頼者の答えとの食い違い）。
- 予定に無い `DSL_GENERATED` 1・`DSL_PREVIEW_DISCARDED` 1 があった（生成した版 2 のプレビューを破棄した。適用中の DSL は変わっていない）。
- S11 は、片付けの食い違いが解けるまで **未通過** とする。依頼者に確かめる。
- 依頼者の答え（13:15:06Z）: 片付けはまだしていなかった（不具合ではない）。DSL の生成と破棄の各 1 件は依頼者の操作で、期待の件数に足す。片付けの後に複写を取り直して数え直した（12.5）。13:11 の複写は消さずに残す。

### 12.5 数え直し（片付けの後）

| 時刻（UTC） | 出来事 |
|---|---|
| 13:15:42〜13:16:04 | 依頼者の片付けの監査の行 5 件（下の表） |
| 13:18:24 | 依頼者の答え（指揮役が記録）: 片付けは済んだ、ほかの操作なし |
| 13:18:34（22:18:34 JST） | `docker compose stop app` |
| 13:18:35 | 配備の後の複写（取り直し）`~/.mastersmith-backup/mastersmith-data-202610142218-after-role-menu-final.tgz`（30,074 B、権限 600）を作り、`docker compose start app` で起動し直した |
| 13:18:45 | healthy・`/actuator/health` 200。アプリが止まっていたのは約 11 秒 |

状態:

| 数えたもの | 結果 | 見込み | 判定 |
|---|---|---|---|
| 初期管理者 | 1 行、停止 0・管理者の印 1 | 同じ | 通る |
| 利用者 | 2 人、管理者の印 2・停止 0 | 配備の前と同じ | 通る |
| ロール・権限の設定・利用者への割り当て・グループへの割り当て・作業ロールの保存 | 0・0・0・0・0 | 0 | 通る |
| グループ・メンバー | 0・0 | 0 | 通る |
| DSL | 適用の履歴 6・プレビュー 0 | 同じ | 通る |
| Flyway の版 | 12 | 12 | 通る |

T0 からの監査の全体（T2 以降は 13:18:34Z まで）:

| 種類 | 結果 | T0〜T1 | T1〜T2 | T2 以降 | 合計 | T2 以降の期待 |
|---|---|---|---|---|---|---|
| `LOGIN_SUCCEEDED` | SUCCESS | 2 | 0 | 0 | 2 | 0（回数どおり） |
| `LOGGED_OUT` | SUCCESS | 1 | 0 | 0 | 1 | 0 |
| `DSL_GENERATED` | SUCCESS | 1 | 0 | 1 | 2 | 1 |
| `DSL_PREVIEW_DISCARDED` | SUCCESS | 0 | 0 | 1 | 1 | 1 |
| `DSL_APPLIED` | SUCCESS | 1 | 0 | 0 | 1 | 0 |
| `ROLE_CREATED` | SUCCESS | 2 | 2 | 0 | 4 | 0 |
| `ROLE_PERMISSION_CHANGED` | SUCCESS | 2 | 0 | 2 | 4 | 2 |
| `ROLE_ASSIGNED` | SUCCESS | 2 | 2 | 0 | 4 | 0 |
| `WORK_ROLE_SWITCHED` | SUCCESS | 1 | 1 | 3 | 5 | 3 |
| `ROLE_UNASSIGNED` | SUCCESS | 2 | 0 | 2 | 4 | 2 |
| `ROLE_DELETED` | SUCCESS | 2 | 0 | 2 | 4 | 2 |
| `ROLE_DELETED` | **FAILURE** | 0 | 0 | **1** | 1 | 0 |
| `GROUP_CREATED`・`GROUP_MEMBER_ADDED`・`GROUP_MEMBER_REMOVED`・`GROUP_DELETED` | SUCCESS | 各 1 | 0 | 0 | 各 1 | 0 |
| `INITIAL_ADMIN_RESCUED`・`INITIAL_ADMIN_CREATED` | — | 0 | 0 | 0 | 0 | 0 |

片付けの行の順（時刻 UTC）: 13:15:42 `ROLE_DELETED` **FAILURE**（`failure_reason` は `ROLE_IN_USE`）→ 13:15:49 `ROLE_UNASSIGNED` → 13:15:57 `ROLE_UNASSIGNED` → 13:16:02 `ROLE_DELETED` → 13:16:04 `ROLE_DELETED`。アプリのログにも同じ時刻（22:15:42 JST）に `ROLE_IN_USE`（409）の WARN が1件ある。ほかの T2 以降の WARN は `DSL_PREVIEW_NOT_FOUND`（404）2 件（13:08:46Z・13:16:18Z、DSL の画面を開いたときの読み出し）と、起動し直しの案内だけ。ERROR は 0。

判定:

- 期待（T2 以降の成功の行と、ロール・割り当て・作業ロールの保存・権限の設定がすべて 0）はすべて満たした。
- 期待と違うのは、失敗の行 1 件（`ROLE_DELETED` の `ROLE_IN_USE`）だけ。割り当てを外す前にロールを消そうとして、仕様どおり拒否された（409、状態は変わっていない）と読める。その後に割り当てを外して消し直し、成功している。業務の拒否が正しく働き監査に残った例であり、配備の不具合ではない。
- S11 は、この 1 件を依頼者の操作の順の違いとして受け入れれば **通る**。依頼者に確かめる（`deployment-strategy.md` の 4節「期待と違う件数が出て、依頼者の操作で説明できない」には当たらない見込み）。

## 13. 戻しの練習（`rollback-runbook.md` の 6節）

### 13.1 環境（2026-10-14 UTC）

| 時刻（UTC） | 出来事 |
|---|---|
| 13:11 頃 | 前提を確かめた: `mastersmith-lgtm-1` は Exited、`mastersmith-rollback_*` のボリュームは無い。VM のメモリは全体 5,910 MiB・使える量 4,849 MiB |
| 13:11 頃 | 練習用の場所 `~/.mastersmith-rollback-role-menu/`（権限 700）に、`.env` の複写 `app.env`（権限 600、中身は表示していない）と、乱数のパスワードの `targetdb.env`（権限 600、`POSTGRES_PASSWORD`・`MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD` の2行）を作った。`app.env` の末尾に `MASTERSMITH_WEB_BASE_URL=http://localhost:18080` と `MASTERSMITH_TARGET_DB_PASSWORD`（読み取りのアカウントと同じ乱数）を足した |
| 13:12:08 | `docker compose -p mastersmith-rollback -f docker/perf/compose.yaml create app` でボリューム `mastersmith-rollback_perf-data` を作り、12節の複写（`…202610142211-after-role-menu.tgz`）を展開し、持ち主を 10001 にした |
| 13:12:14 | `MASTERSMITH_IMAGE_TAG=pre-role-menu docker compose -p mastersmith-rollback -f docker/perf/compose.yaml --profile targetdb-postgres up -d --wait app targetdb-postgres` |
| 13:12:23 | 練習のアプリと練習の対象DB が healthy |

- 練習のアプリのイメージは `sha256:fefeafbd299b…`（`mastersmith:pre-role-menu`）、上限は 2g。配備したアプリは止めておらず、`http://localhost:8080/actuator/health` は 200 のまま。
- メモリの使用（13:12 頃）: 練習のアプリ 421 MiB・練習の対象DB 27 MiB・配備したアプリ 569 MiB・Mailpit 42 MiB・見本の対象DB 56 MiB。
- 12節の片付けの食い違いのため、練習の元の複写にはロール 2・割り当て 2・作業ロールの保存 1・権限の設定 2 が入っている（G-19 の確かめには、新しい表に行がある状態として使える）。

### 13.2 AI だけで確かめたもの

| # | 確かめ | 結果 | 判定 |
|---|---|---|---|
| D1 | 前の版の起動 | `/actuator/health` 200。Flyway は「Successfully validated 12 migrations」「Current version of schema "PUBLIC": 12」「Schema "PUBLIC" has a version (12) that is newer than the latest available migration (9) !」（WARN）「Schema "PUBLIC" is up to date. No migration necessary.」。`Migrating schema` 0 件、検証の失敗は無い。Hibernate の検証で止まらない | 通る（版 12 が前の版より新しいという WARN は、戻したときに出る見込みの1件） |
| D2 | DSL が無い状態での起動（AC6.1.8） | ERROR は `DslStartupLoader`「適用中の DSL を読めないため、適用中の DSL が無い状態で起動します」1件だけ。キー `dsl.errorKinds` の値は `UNSUPPORTED_VERSION`（版 2 の DSL を前の版が読めない） | 通る |
| ログ | ほかのログ | 41 行（すべて JSON）。INFO 36・WARN 3（Flyway の H2 の版の案内、Spring の Bean の案内、上の版 12 の WARN）・ERROR 1。救済の WARN 0、INFO「初期管理者は既にいるため、作成しませんでした」。初期管理者のメールアドレスの値がログに現れる件数 0 | 通る |
| D4（読み） | 前の版が監査の行を実体として読まないこと | `git grep` で `273c021` の `backend/src/main` を調べ、`AuditEventRepository` の呼び出しは `AuditEventRecorder` の `repository.save` だけ。`findById`・`findAllByOrderByOccurredAtAsc` を呼ぶ所は無い | 通る（画面の操作を含む D4 の残りは D3・D5 で確かめる） |

### 13.3 依頼者の画面の操作（D3・D5・D6）と、止めた後の確かめ

- 依頼者の答え（13:18:24Z）: 18080 で D3（ログイン・管理の画面）・D5（DSL の生成と適用）・D6（ログアウト）はすべて見込みどおり。
- 13:19:16Z に練習のアプリを止めた（終了コード 143、OOMKilled false。止める直前のメモリの使用は 544 MiB）。止める前の練習のアプリのログは、WARN 9（起動の案内3、`REFRESH_FAILED` 401 が1、`DSL_PREVIEW_NOT_FOUND` 404 が5）・ERROR 1（起動の DSL の1件）。初期管理者のメールアドレスの値の件数 0。
- 練習のボリュームを練習用の場所（権限 700）へ複写し、読み取り（`ACCESS_MODE_DATA=r`）で開いて数えた。

| 数えたもの | 結果 | 見込み | 判定 |
|---|---|---|---|
| 管理者の印が真の行 | 2（利用者 2、停止 0） | 配備の前の複写（7節）の 2 | 同じ（権限のずれは無い） |
| 練習の監査（13:12Z 以降） | 13:16:50 `LOGIN_SUCCEEDED` → 13:17:25 `LOGGED_OUT` → 13:17:36 `LOGIN_SUCCEEDED` → 13:17:40 `DSL_GENERATED` → 13:17:53 `DSL_APPLIED` → 13:18:04 `LOGGED_OUT`。結果はすべて SUCCESS | `LOGIN_SUCCEEDED` 1・`DSL_GENERATED` 1・`DSL_APPLIED` 1・`LOGGED_OUT` 1 | ログインとログアウトが各 1 件多い（一度ログアウトしてログインし直した）。依頼者に確かめる |
| 練習の DSL | 適用の履歴 7（元の 6 に前の版での適用が 1 増えた）・プレビュー 0 | 適用が 1 増える | 通る |
| 練習で出たリフレッシュトークン | 2 件、どちらも無効化済み（失効していないもの 0） | D6 のログアウトで無効 | 通る |
| Flyway の版 | 12（前の版は移行を当てていない） | 12 | 通る |
| ロール・割り当て | 2・2（練習の元の複写は 13:11 のもので、片付けの前の状態） | — | 前の版は新しい表を読まずに動いた（G-19） |

### 13.4 判定

| # | 確かめ | 判定 |
|---|---|---|
| D1 | 前の版の起動（Flyway の誤りなし、`Migrating schema` 0、版 12 を前の版の 9 より新しいと WARN で案内するだけ） | 通る |
| D2 | DSL が無い状態での起動（ERROR 1 件、`UNSUPPORTED_VERSION`） | 通る |
| D3 | 管理の可否（依頼者が管理の画面を開けた。管理者の印が真の行が配備の前と同じ 2） | 通る。403 の側（管理者でない利用者）は、パスワードを依頼者が知る管理者でない利用者がいないため行っていない（前の版の結合テストで確かめ済みの振る舞いとして扱う） |
| D4 | G-19（`GROUP_*`・`ROLE_*` の監査の行と新しい3列、新しい表の行がある内部DB で D1〜D3・D5 が失敗しない。前の版のコードは監査の行を `save` だけで扱う） | 通る |
| D5 | 前の版での DSL の生成し直しと適用 | 通る |
| D6 | ログアウト（練習で出したリフレッシュトークンはすべて無効） | 通る |

### 13.5 片付け

- 13:19:43Z に `docker compose -p mastersmith-rollback -f docker/perf/compose.yaml --profile targetdb-postgres down -v` で、練習のコンテナ・ボリューム（`perf-data`・`perf-targetdb-postgres`）・ネットワークを消した。
- 練習用の場所 `~/.mastersmith-rollback-role-menu/`（`.env` の複写・対象DB の環境ファイル・練習の H2 の複写）を消した。
- `rollback` の名前のコンテナ・ボリューム・ネットワークが 0、練習用の場所が無いことを確かめた。`mastersmith:pre-role-menu`（`fefeafbd299b`）は残っている。配備したアプリ・Mailpit・見本の対象DB は動いたまま。

## 14. 片付け（Environment Provisioning の Q2: A）

名前の無いイメージ（dangling）とビルドのキャッシュだけを片付けた。タグ付きのイメージとボリュームには触れていない。

| 時刻（UTC） | 操作 |
|---|---|
| 13:19:49 | 前の量を記録した |
| 13:19:49〜13:19:57 | `docker image prune -f`（名前の無いイメージは 0 個で、回収 0B）、`docker builder prune -a -f`（ビルドのキャッシュ 2.323GB を消した） |
| 13:19:57 | 後の量を記録した |

| 種類 | 前 | 後 |
|---|---|---|
| イメージ（`docker system df`） | 32 個・10.12GB（回収できる 4.591GB） | 32 個・10.12GB（同じ） |
| 名前の無いイメージ | 0 個 | 0 個 |
| コンテナ | 4 個（動いている 3）・35.45MB | 同じ |
| ボリューム | 64 個・10.12GB | 64 個・10.12GB（同じ） |
| ビルドのキャッシュ（`docker system df`） | 23 個・2.323GB（回収できる 889.7MB） | 0 個・0B |
| ビルドのキャッシュ（`docker builder du`） | 2.323GB（回収できる 2.323GB） | 0B |

- イメージは Environment Provisioning の記録（30 個）より 2 個多い。この段で作った `mastersmith:local`（`3247c5bdab79`）と、タグを付けて残した `mastersmith:role-menu-before-s8fix`（`e41a5b02fd0b`）の分。
- タグの一覧は片付けの前後で同じ（全 32 行）。`mastersmith` のタグは 19 個: `local`（`3247c5bdab79`、配備した版）・`pre-role-menu`（`fefeafbd299b`、戻し先）・`role-menu-before-s8fix`（`e41a5b02fd0b`、直す前の版）・`role-menu-b7`・`perf-dsl`・`safety-carryover`・`pre-safety-carryover`・`user-admin-followup`・`pre-user-admin-followup`・`pre-user-admin`・`pre-log-deps-cleanup`・`pre-quality-followup`・`storage-memory-fix`・`pre-user-management`・`pre-storage-memory`・`storage-memory-base`・`followup-fixes`・`pre-followup`・`pre-dsl`。
- 次にイメージを作るときは、ビルドのキャッシュが無いため層を作り直す（初回だけ時間が延びる）。
- `~/.mastersmith-backup/` には、この段の複写 4 件（`…202610140820-pre-role-menu`・`…202610141654-after-role-menu`・`…202610142211-after-role-menu`・`…202610142218-after-role-menu-final`、すべて権限 600）と、数えるスクリプト `count-db.sh`（値を含まない）が残る。作業用のディレクトリは残っていない。

## Sources

- `deployment-execution-questions.md`（決まっていること、Q1〜Q3、まとめの確認）
- `operation/deployment-pipeline/cd-config.md`（1節・3節・3.1・4節）・`deployment-strategy.md`（2節・3.2）・`rollback-runbook.md`（1節）
- `operation/environment-provisioning/environment-inventory.md`（2節・5節・6.1）・`validation-report.md`（E19・WAR の作り方の差）
- 前の記録の形: `aidlc/spaces/default/intents/261004-safety-carryover/operation/deployment-execution/deployment-log.md`
- 実行した確かめ（2026-10-13 23:14 UTC 頃）: `git rev-parse HEAD origin/develop main`、`git status --porcelain`、`git diff 005730a HEAD -- . ':!aidlc'`、`git log --oneline 005730a..HEAD`、`git merge-base --is-ancestor`、`git submodule status`、`gh run view 38432556919 --json …`、`gh run list --branch develop`、`shasum -a 256` と `javap -constants`（WAR）、`docker inspect mastersmith-app-1`、`docker image inspect`（`mastersmith:local`・`mastersmith:pre-role-menu`）、`docker images mastersmith`、`docker ps`、`curl` で `/actuator/health`、`docker tag`
- リポジトリ（読むだけ）: `git show 273c021:frontend/src/features/dsl/messages.ts`・`registration.ts`、`git grep 273c021`（`DslPreviewPanel.tsx`・`DslConfirmDialog.tsx`）
- 順 5〜7・S1・S2 の確かめ（2026-10-13 23:20〜23:21 UTC）: `docker compose stop app`、README の「内部DBのバックアップと戻し方」の `docker run … tar czf`、`org.h2.tools.Shell`（`ACCESS_MODE_DATA=r`）、`docker logs --since`（前の版のアプリ。レベル・ロガー・`code`・`status` だけ）、`docker compose --profile targetdb-postgres up -d --build app`、`docker inspect`（`State.Health`・`HostConfig`）、`docker compose exec app`（`/proc/1/cmdline` の件数・`sha256sum`）、`curl` で `/actuator/health`、`docker compose logs --no-log-prefix app` と `jq`（レベル・ロガー・メッセージ・キーの名前・件数だけ）
- `git show 273c021:backend/src/main/java/cherry/mastersmith/dslmanage/service/DslLifecycle.java`（破棄と 404 の経路）、`backend/src/main/resources/db/migration/`（表と列の名前）、README（コンテナでの起動と確認、初期管理者の救済、戻し方、内部DBのバックアップと戻し方、監査ログの確かめ方）
- 11節の確かめ（2026-10-14 07:54〜07:55 UTC）: `git diff 08d1e71 a16cd68`・`git diff 876042b a16cd68`・`git diff 005730a a16cd68`、`shasum -a 256`、`unzip`（WAR の `static/assets`）、`curl`（`/`・`/assets/…`・`/actuator/health`）、`docker compose stop app`・`docker run … tar czf`、`org.h2.tools.Shell`（`ACCESS_MODE_DATA=r`）、`docker tag`、`docker compose --profile targetdb-postgres up -d --build app`、`docker inspect`、`docker compose exec app`、`docker compose logs --no-log-prefix app` と `jq`
- 12・13節の確かめ（2026-10-14 13:11〜13:13 UTC）: `docker compose stop app`・`start app`、`docker run … tar czf`・`tar xzf`、`org.h2.tools.Shell`（`ACCESS_MODE_DATA=r`）、`docker logs`（レベル・ロガー・`code`・`status`・キーの名前だけ）、`openssl rand -hex 24`、`docker compose -p mastersmith-rollback -f docker/perf/compose.yaml`（`create`・`up -d --wait`）、`docker stats --no-stream`、`colima ssh -- free -m`、`git grep 273c021 -- backend/src/main`、`rollback-runbook.md` の 6節
- 12.5・13.3〜14節の確かめ（2026-10-14 13:18〜13:20 UTC）: 配備の後の複写の取り直しと読み取り、`docker logs`（練習のアプリと配備したアプリ。レベル・ロガー・`code`・`status` だけ）、練習のボリュームの複写の読み取り、`docker compose -p mastersmith-rollback … down -v`、`docker image prune -f`・`docker builder prune -a -f`、`docker system df`・`docker builder du`・`docker images`、`gh run list --branch develop`・`gh api …/actions/runs/38435315180/attempts/1`・`gh run view 38435315180 --attempt 1 --log-failed`
- `aidlc/spaces/default/memory/team.md`・`project.md`（Deployment・Change Control・Corrections）

## Assumptions & Open Questions

- [assumption] 今の内部DB に版 1 のプレビューがあるかは分からない（アプリを止めずに読む手段が、秘密の要る API か画面しかないため）。依頼者が順 4 の手順 3 で見て決める。→ 順 5 の複写で `dsl_previews` 0 行、T0 以降の `DSL_PREVIEW_DISCARDED` 0 件。破棄は行われていない見込み（6節）。依頼者に確かめる。
- [assumption] 初期管理者のパスワードが `.env` と一致すること（救済の条件 (c)）は、依頼者の答え（Q2: A）と、起動のログの救済の WARN 0 件で裏付けた。
- [assumption] WAR は順 6 まで変わらない（この段で `./gradlew` を流さない）。順 6 の直前にもう一度 SHA-256 を確かめる。
- `2669c62` の push の CI（run 38482643592）は確かめた時点で実行中だった。`aidlc/` の記録だけのコミットのため配備の関門にはしないが、順 10（`main` への取り込み）の前に結果を確かめる。

- T0（監査の数え始め）: 2026-10-13T23:15:41Z（依頼者に順 4 の手順を伝える直前に指揮役が控えた）
- 順 4（23:18:51Z 頃）: 依頼者の答え — プレビューがあって画面で破棄した、ログイン1回、ほかに監査に残る操作なし。期待: DSL_PREVIEW_DISCARDED 1・LOGIN_SUCCEEDED 1（T0 以降）。破棄の裏付けは順 5 の複写の dsl_previews 0 行で行う。
- 順 4 の確かめ（23:23:47Z）: 依頼者の答え — 破棄の表示は覚えていない・誤りの表示だった、23:18 頃にログアウトした。T0 以降の期待を DSL_PREVIEW_DISCARDED 0・LOGGED_OUT 1 に直す（入れ替えの前のプレビューは複写で 0 行と確かめ済み）。
- S3〜S10（23:43:32Z 報告）: 依頼者の答え — S8「自分の権限を見る」で権限が表示されなかった（API は呼ばれている模様）。ほかは見込みどおり。ログイン 1 回（成功のみ）、予定に無い操作なし。S8 は原因を調べる。
- S8 の表示（03:24:34Z 依頼者の答え）: 「読み込んでいます」が出たまま変わらず、しばらく待つとエラーの表示になった。API の応答は「読み込んでいます」の間に返っていたように見えた。仕様どおりの動きではなく、不具合の見込みとして原因を調べる。
- S8 の扱い（03:29:43Z 依頼者の答え）: 使い捨ての環境で再現して原因を見分ける（A）。S11・配備の後の複写・戻しの練習は原因が分かってから決める（A）。
- S8 の再試行（06:17:59Z 依頼者の答え）: 文言の場所は覚えていない。配備したアプリで開発者ツールを開いたまま S5〜S8 をやり直す（A）。T1（再試行の数え始め）: 2026-10-14T06:17:59Z。
- S8 の再試行の結果（06:26:04Z 依頼者の答え）: 配備したアプリで再現した。ロール甲・乙を作り、自分を割り当て、権限は付けない状態で「自分の権限を見る」を開き、木でテーブルの段を広げたところで「読み込んでいます」のまま固まり、しばらくして誤りの表示になった。ブラウザが反応しなくなった（画面の側の無限の繰り返しの見込み）。手順に無い操作もあった（中身は確かめる）。
- 再試行のほかの操作（06:26:31Z 依頼者の答え）: 中身は覚えていないため、配備の後の複写で T1 以降の監査を種類ごとに数えて示す。ロール甲・乙は残っている（直した版での確かめに使うか、後で片付ける）。
- S8 の原因（06:27:12Z、コードの読みで突き止めた）: 権限の有無とは関係なく、「自分の権限を見る」の木でテーブルの節の開閉を押すと、テーブルの節が同じ id の自分自身を子として持ち、描画が止まらなくなる（画面だけの不具合。useMyPermissions.ts の toTreeNode が API の hasChildren をテーブルの節にもそのまま渡し、loadChildren がテーブルの id でスキーマのテーブルの一覧を読み直すため）。10.4 の「環境の差に原因がある」という見立ては誤りで、使い捨ての環境ではテーブルの開閉を押していなかったために再現しなかった。
- S8 の直し（06:27:52Z 依頼者の答え A）: 今直して配備し直す。develop から fix/261004-role-menu-mypermissions-tree を作り、主な直し（テーブルの節に開閉を出さない）＋守り（スキーマ以外の id で子を読まない）＋再現のテスト。verify と E2E を通して squash で develop へ統合し、WAR とイメージを作り直して配備し直す。

- T2（S8 の確かめ直しの監査の数え始め）: 2026-10-14T07:56:31Z（依頼者に手順を伝える直前に指揮役が控えた）
- S8 の確かめ直し（13:10:43Z 依頼者の答え）: すべて見込みどおり（テーブルに開閉なし、選ぶと値が出る、固まらない）。片付けも済んだ。操作の回数は覚えていないため、T2 以降の監査を複写で数える。
- S11 の食い違いの確かめ（13:15:06Z 依頼者の答え）: 片付けはまだしていなかった（不具合ではない）。DSL の生成と破棄の各 1 件は依頼者の操作。期待の件数に足す。片付けをしてもらい、配備の後の複写を取り直して S11 を数え直す。戻しの練習 D3〜D6 は片付けの後に 18080 で行う。
- 片付けと戻しの練習（13:18:24Z 依頼者の答え）: 8080 の片付け（割り当てを外す → 2 つのロールを消す）は済んだ、ほかの操作なし。18080 の戻しの練習 D3・D5・D6 はすべて見込みどおり。
- [assumption] `ROLE_DELETED` の FAILURE 1 件（`ROLE_IN_USE`）と、戻しの練習のログイン・ログアウトの各 1 回の多さは、依頼者の操作によるものと読んだ。依頼者に確かめる。
- `ae794e4` の push の CI（run 38435315180）は、1回目が `frontendTest` で失敗し（`ShellLayout.test.tsx` の 702 行の `Error: down` の Unhandled Rejection。テストは 1,581 件すべて通過、エラーが 1 件）、2回目で success だった。CI Pipeline の記録（`construction/ci-pipeline/ci-config.md`）に無い。`ae794e4` は `aidlc/` の下だけのコミットで、コードは 1 回目で success だった `005730a` と同じ。扱いは依頼者に確かめる（`team.md` の「不安定なテストと CI の失敗」）。
- `a16cd68`（S8 の直し）と `876042b` は未 push（`origin/develop` は `2669c62`）。直した版の CI は push の後に確かめる。
- 監査の差の確かめ（13:25:07Z 依頼者の答え）: S11 の ROLE_DELETED FAILURE（ROLE_IN_USE）1 件と、戻しの練習のログイン・ログアウトの各 1 件の多さは、どちらも依頼者の操作（仕様どおりの拒否として受け入れる）。ae794e4 の CI の 1 回目の失敗（ShellLayout.test.tsx の Unhandled Rejection）は、main への取り込みの前に上限 30 分で調べて、テストだけの直しを短命のブランチで行う（A）。CI の記録の漏れも書き足す。
- push（13:30:40Z）: 依頼者が develop を push した（origin/develop = b265a2f）。CI run 38572240082・38572240463 が動き始めた。2669c62 の CI（run 38482643592）は success。
