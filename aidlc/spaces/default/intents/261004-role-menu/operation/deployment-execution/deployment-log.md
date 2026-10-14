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

## Sources

- `deployment-execution-questions.md`（決まっていること、Q1〜Q3、まとめの確認）
- `operation/deployment-pipeline/cd-config.md`（1節・3節・3.1・4節）・`deployment-strategy.md`（2節・3.2）・`rollback-runbook.md`（1節）
- `operation/environment-provisioning/environment-inventory.md`（2節・5節・6.1）・`validation-report.md`（E19・WAR の作り方の差）
- 前の記録の形: `aidlc/spaces/default/intents/261004-safety-carryover/operation/deployment-execution/deployment-log.md`
- 実行した確かめ（2026-10-13 23:14 UTC 頃）: `git rev-parse HEAD origin/develop main`、`git status --porcelain`、`git diff 005730a HEAD -- . ':!aidlc'`、`git log --oneline 005730a..HEAD`、`git merge-base --is-ancestor`、`git submodule status`、`gh run view 38432556919 --json …`、`gh run list --branch develop`、`shasum -a 256` と `javap -constants`（WAR）、`docker inspect mastersmith-app-1`、`docker image inspect`（`mastersmith:local`・`mastersmith:pre-role-menu`）、`docker images mastersmith`、`docker ps`、`curl` で `/actuator/health`、`docker tag`
- リポジトリ（読むだけ）: `git show 273c021:frontend/src/features/dsl/messages.ts`・`registration.ts`、`git grep 273c021`（`DslPreviewPanel.tsx`・`DslConfirmDialog.tsx`）
- 順 5〜7・S1・S2 の確かめ（2026-10-13 23:20〜23:21 UTC）: `docker compose stop app`、README の「内部DBのバックアップと戻し方」の `docker run … tar czf`、`org.h2.tools.Shell`（`ACCESS_MODE_DATA=r`）、`docker logs --since`（前の版のアプリ。レベル・ロガー・`code`・`status` だけ）、`docker compose --profile targetdb-postgres up -d --build app`、`docker inspect`（`State.Health`・`HostConfig`）、`docker compose exec app`（`/proc/1/cmdline` の件数・`sha256sum`）、`curl` で `/actuator/health`、`docker compose logs --no-log-prefix app` と `jq`（レベル・ロガー・メッセージ・キーの名前・件数だけ）
- `git show 273c021:backend/src/main/java/cherry/mastersmith/dslmanage/service/DslLifecycle.java`（破棄と 404 の経路）、`backend/src/main/resources/db/migration/`（表と列の名前）、README（コンテナでの起動と確認、初期管理者の救済、戻し方、内部DBのバックアップと戻し方、監査ログの確かめ方）
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
