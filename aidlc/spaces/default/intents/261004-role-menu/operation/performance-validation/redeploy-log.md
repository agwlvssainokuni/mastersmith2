# 配備し直しの記録（redeploy-log）

Intent 261004-role-menu の Performance Validation の段で、DSL の管理の画面の英語の表示に全角の記号が出る不具合を直した版を、配備し直した記録です（`fix-notes.md`）。

- 手順は `operation/deployment-pipeline/cd-config.md` の順 5〜7 と S1 に準じ、前回の配備し直し（`operation/deployment-execution/deployment-log.md` の 11節）と同じ形で行いました。
- `deployment-log.md` は Deployment Execution の承認の後のため、書き換えずにここに書きます。
- 値・秘密・個人に関する値は書きません。`.env` は開いていません。
- 時刻は UTC です（括弧の中は JST）。

## 1. 時系列（2026-10-14 UTC ＝ 2026-10-15 JST）

| 時刻（UTC） | 出来事 |
|---|---|
| 22:01:52（07:01:52） | 関門の確かめを始めた（2節） |
| 22:02 頃 | 画面の資源を、今の `develop` の `frontend/` から作り直した結果と WAR の中身で比べた（2.2） |
| 22:03:18（07:03:18） | `docker compose stop app`（ここからアプリが止まる） |
| 22:03:19 | 入れ替えの前の複写 `~/.mastersmith-backup/mastersmith-data-202610150703-pre-dslfix.tgz`（29,963 B、権限 600）を作り、読み取りで数えた（3節） |
| 22:03:25 | 前の版のイメージに `mastersmith:role-menu-before-dslfix` のタグを付け、WAR の SHA-256 を確かめ直した（同じ）。`docker compose --profile targetdb-postgres up -d --build app` を始めた |
| 22:03:30 | 新しいコンテナが起動した |
| 22:03:40（07:03:40） | healthy。アプリが止まっていたのは 22:03:18〜22:03:40 の約 22 秒 |
| 22:04 頃 | 起動の確かめと S1 を終えた（4節・5節） |

## 2. 関門

### 2.1 版と差

| 確かめ | 結果 | 判定 |
|---|---|---|
| `develop` の先頭 | `954028c`（Performance Validation: 質問と台本・画面の直しの記録）。`a16cd68` の後は 10 コミット（`b265a2f`・`2161ce0`・`6d7f27c`・`c0f1995`・`344f0a1`・`f581916`・`3f685c0`・`add7169`・`4ccabb5`・`954028c`） | 通る |
| `a16cd68` からの `aidlc/` の外の差 | 17 ファイル。`frontend/` の 12（画面の直し 5：`messages.ts`・`DslDiffTable.tsx`・`DslErrorList.tsx`・`DslMenuTree.tsx`・`DslSubmitForm.tsx`。残りはテストだけ）、`perf/` の 3、**`docker/monitoring/dashboards/mastersmith-overview.json` の 1**（`c0f1995`、Observability Setup のダッシュボード） | 指揮役の見込み（`frontend/` と `perf/` だけ）と違う（2.3） |
| 移行・サブモジュール | `db/migration/` と `vendor/` の差は無い。固定先は `5bf1ffe`・`8d44c36` | 通る |
| 作業フォルダ | 未コミットの変更は監査の記録 `audit/sakura-local-4e42a93f87ce.md` だけ。`frontend/`・`backend/` は `HEAD` と同じ | 通る |
| WAR | `backend/build/libs/mastersmith.war`（102,383,315 B、2026-10-15 04:53:37 JST 作成）の SHA-256 は `4bf6e68ca1d996b47b419023590a8034c0656fe4cce26f07d0d809fa741febd3`（指揮役が知らせた値と同じ） | 通る |

### 2.2 WAR の中身が今の `develop` と同じこと

WAR は、コミットの前の作業フォルダから作られました。`frontend/src` の 11 ファイル（直しの 5 ファイルを含む）は、ファイルの更新の時刻が WAR より後です。そのため時刻では判断せず、中身で比べました。

| 確かめ | 結果 | 判定 |
|---|---|---|
| 新しい文言の鍵 | WAR の中の画面の資源（`DslAdminPage-cS42TGFr.js`・`index-Ck_XRDyk.js`）に、`dsl.diff.changedItemsSeparator`・`dsl.menu.tableName`・`dsl.errors.kindSeparator`・`dsl.submit.fileNameSize` の4つが、日本語と英語で1つずつある（例: `kindSeparator` は「：」と「: 」） | 通る |
| 前の書き方の名残 | `join("・")` と、全角のコロンを直接に描く形はどちらも 0 件 | 通る |
| 画面の資源の全体 | 今の `develop` の `frontend/` から、リポジトリの外の置き場へ `npx vite build --outDir` で作り直した結果（82 ファイル）が、WAR の `WEB-INF/classes/static/` とバイト単位で同じ。違いは、バックエンドの資源として入る `static/dsl/`（JSON Schema）が WAR にだけあることだけ | 通る |
| 画面の資源の外 | 配備していた WAR（`a16cd68`、`f5293bed…`）と新しい WAR を展開して比べた。違いは `WEB-INF/classes/static/` の中（`index.html`・`.vite/manifest.json`・`assets/`）だけで、バックエンドのクラスと部品（`WEB-INF/classes` のほか・`WEB-INF/lib`）は同じ | 通る |

### 2.3 見込みとの差（ダッシュボード）

- `docker/monitoring/dashboards/mastersmith-overview.json` は、Observability Setup の段で足したダッシュボード（`c0f1995`）です。
- このファイルは手元の監視（`lgtm`、profile `monitoring`）が読むだけで、WAR にもアプリのイメージ（`Dockerfile` は WAR を写すだけ）にも入りません。
- アプリの配備には影響しないため、配備を続けました。

## 3. 入れ替えの前の複写と数え

- 置き場は `~/.mastersmith-backup/`（権限 700）、ファイルは `mastersmith-data-202610150703-pre-dslfix.tgz`（権限 600）です。前の複写は消していません。
- 複写は H2 Shell で `ACCESS_MODE_DATA=r`（読み取り）で開き、作業用のディレクトリは数えた後に消しました。

| 数えたもの | 結果 | 見込み |
|---|---|---|
| 前回の数え（2026-10-14 13:18:34Z）の後の監査の行 | 0 件 | 0 件か少数（依頼者は画面を触っていない見込み） |
| 利用者 | 2 人、管理者の印 2・停止 0 | 前回と同じ |
| ロール・割り当て | 0・0 | 前回と同じ |
| DSL | 適用の履歴 6・プレビュー 0 | 前回と同じ |
| Flyway の版 | 12 | 12 |

## 4. 入れ替えと起動の確かめ

| 確かめ | 結果 | 判定 |
|---|---|---|
| 配備したイメージ | `mastersmith:local` ＝ `sha256:e0a5825d257c1b1f861b3e553d8ba6d916eac590264fad593d5ea620f311cc53`（作成 2026-10-15 07:03:27 JST） | 記録 |
| 戻し先 | `mastersmith:role-menu-before-dslfix` ＝ `sha256:3247c5bdab79…`（直す前の版。タグを付けて残した）。`mastersmith:role-menu-before-s8fix`（`e41a5b02fd0b`）・`mastersmith:pre-role-menu`（`fefeafbd299b`）も残っている | 通る |
| イメージの中の WAR | `/app/mastersmith.war` の SHA-256 は `4bf6e68c…741febd3`（2.1 と同じ） | 通る |
| 健全性 | 22:03:40Z に healthy（起動から約 10 秒、Spring の起動 6.251 秒）。`/actuator/health` は 200・`{"status":"UP"}` | 通る |
| 上限 | メモリ 2g・CPU 4・`restart: no`・OOMKilled false | 通る |
| JVM の設定 | PID 1 のコマンド行に `-XX:+ExitOnOutOfMemoryError` 1 件・`-XX:MaxRAMPercentage=50.0` 1 件 | 通る |
| 移行 | 「Current version of schema "PUBLIC": 12」「Schema "PUBLIC" is up to date. No migration necessary.」 | 通る |
| 配っている画面の資源 | `/` が読む `index-Ck_XRDyk.js` と `DslAdminPage-cS42TGFr.js` に `dsl.errors.kindSeparator` がある（WAR の中と同じ名前） | 通る |
| ほかのコンテナ | Mailpit・見本の対象DB は動いたまま（止めていない） | 通る |

## 5. S1: 起動のログ（値は表示していない）

| 確かめ | 結果 | 判定 |
|---|---|---|
| 行数とレベル | 39 行（すべて JSON）。INFO 36・WARN 2・ERROR 0 | 通る |
| WARN | Flyway の H2 の版の案内 1 件と、Spring の Bean の案内 1 件だけ（前の版から出ているもの） | 通る |
| 救済 | 救済の WARN 0 件。INFO「初期管理者は既にいるため、作成しませんでした」1 件（キー `maskedEmail` あり、`email` 無し） | 通る |
| メールアドレスの漏れ | `.env` の初期管理者のメールアドレスの値がログに現れる件数 0 | 通る |

S1 は通りました。画面の英語の表示は、Performance Validation の UI の台本で確かめます（指揮役の指示。この記録では依頼者に頼む手順を作っていません）。

## 6. 戻すとき

- イメージだけを戻します（`rollback-runbook.md` の 2節の形。`MASTERSMITH_IMAGE_TAG=role-menu-before-dslfix docker compose up -d --no-build app`）。
- スキーマの変更は無いため、データの戻しは要りません。

## Sources

- `fix-notes.md`・`performance-validation-questions.md`・`script-fixes.md`（この段の記録）
- `operation/deployment-execution/deployment-log.md`（11節・12.5・14節）、`operation/deployment-pipeline/cd-config.md`（3節・6節）・`rollback-runbook.md`（2節）
- 実行した確かめ（2026-10-14 22:01〜22:04 UTC）: `git log`、`git diff a16cd68 HEAD -- . ':!aidlc'`（`--stat`・`--name-only`）、`git submodule status`、`git status --porcelain`、`git diff --quiet HEAD -- frontend backend`、`find -newer`（WAR）、`shasum -a 256`、`unzip`（WAR の `static`）と `grep`（文言の鍵の件数）、`npx vite build --outDir`（リポジトリの外）と `diff -rq`、`docker cp`（配備していた WAR）と展開しての `diff -rq`、`docker compose stop app`、`docker run … tar czf`、`org.h2.tools.Shell`（`ACCESS_MODE_DATA=r`）、`docker tag`、`docker compose --profile targetdb-postgres up -d --build app`、`docker inspect`、`docker compose exec app`（`sha256sum`・`/proc/1/cmdline` の件数）、`curl`（`/`・`/assets/…`・`/actuator/health`）、`docker compose logs --no-log-prefix app` と `jq`（レベル・ロガー・メッセージの先頭・キーの名前・件数だけ）

## Assumptions & Open Questions

- [assumption] WAR の中身が今の `develop` と同じことは、画面の資源のバイト単位の一致（作り直した結果との比べ）と、バックエンドの部分の一致（配備していた `a16cd68` の WAR との比べ）で判断した。`a16cd68` の後にバックエンドのソースの差は無い。
- `a16cd68` からの差に、指揮役の見込みに無い `docker/monitoring/dashboards/mastersmith-overview.json`（`c0f1995`）があった。アプリの配備には関わらないと判断して続けた（2.3）。
- 画面の英語の表示（全角の記号が出ないこと）は、この記録では確かめていない。Performance Validation の UI の台本で確かめる。
