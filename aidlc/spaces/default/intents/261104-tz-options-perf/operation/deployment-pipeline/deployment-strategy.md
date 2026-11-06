# 配備の方式とスモークテスト（deployment-strategy）

Intent 261104-tz-options-perf の配備の方式とスモークテストです。流れは `cd-config.md` の 3節にあります。方式は前の配備（Intent `261101-maintenance-update`）と同じです。

前の配備との差は3つです。

- 起動の確かめに、V14 の移行が1件当たることを足した。
- スモークテストに、引き受けた Intent 261102 の各単位（U2〜U6）が配備の段に回した確かめを、すべて入れた（F1: A）。
- 表示のタイムゾーンの選択肢を、Chrome に加えて Firefox と Safari でも確かめる（Q3: A）。

## 1. 方式

- **1 台の置き換え**（recreate）。`docker compose up -d --build --no-deps app` でアプリだけを入れ替え、イメージ ID の一致を `docker inspect` で確かめる（食い違えば `--force-recreate --no-deps app`）。ブルー・グリーンやカナリアは、PC 上の 1 台のコンテナでは意味がないため使わない。
- 環境の段（dev → staging → prod）は持たない。本番配備の承認者は依頼者。承認は、アプリが止まる順 5〜7 の前に 1 回（`cd-config.md`）。
- 内部DB のスキーマ変更は V14 の1つ（列を足すだけ。前進のみ・後方互換）。機能フラグは使わない（表示のタイムゾーンは利用者が選ぶまで「ブラウザに合わせる」のままで、既存の利用者の表示は変わらない）。
- 配備の完了は、healthy とスモークテストのすべてが通ったとき。通るまで完了とはみなさない。

## 2. 起動の確かめ

| # | 確かめ | 行う人 | 通る条件 |
|---|---|---|---|
| S1 | healthy・V14・起動のログ | AI | 下の4点がすべて通る |

S1 の4点:

1. **健全性**: `mastersmith-app-1` が healthy で、`/actuator/health` が 200（compose と同じ bash の `/dev/tcp` で待つ）。メモリの上限 2 GiB・CPU 4。
2. **イメージ**: コンテナのイメージ ID が `mastersmith:local` の ID と一致する。
3. **V14**: 起動のログで Flyway の `Migrating schema` が 1 件で、版が 14（版と件数だけを見る）。
4. **起動のログ**: JSON として読み、ロガーと項目の名前と件数だけを出す。
   - ERROR 0 件。
   - 救済の WARN 0 件。
   - WARN は前の版から出ているもの（Flyway の H2 の版の案内・Bean の後処理の案内）だけ。
   - 初期管理者の INFO にメールアドレスそのものが出ていない（キー `maskedEmail` の有無と、`.env` の値の件数で見る。値は表示しない）。

## 3. スモークテスト（Q3・F1・F2: A）

値は記録に書かない。書かないものは、メールアドレス・パスワード・業務データの値・主キーの値・DSL の本文。パスワードが要る操作と画面の操作は依頼者が行い、AI はアプリのログ・監査の記録・内部DB と対象DB の数で裏付ける。★の操作は監査に残るため、行う前に 3.2 の期待の件数を依頼者に伝える。

前提:

- `cd-config.md` の 4.1 の数えで、初期管理者が停止しておらず管理の印を持つ。違うときは `cd-config.md` の順 8 で合わせ直してから始める。
- 配備したアプリで適用中の DSL に `sales.customers` が載り、`created_at` が一覧・詳細・作成と更新の欄・検索の対象になっている。AI が DSL の画面か適用中の DSL の取り出しで、値を出さずに設定だけを確かめる。載っていなければ S4・S5 は成り立たないため、`sales.customers` を含む DSL を使い捨ての確かめとして適用してよいかを依頼者に諮る（勝手に適用しない）。
- 見本の対象DB に書き込みのアカウントがあり、アプリの `.env` に書き込みの接続の項目がある（項目の有無だけを見る）。
- 確かめに使う表示のタイムゾーンは `America/New_York`（夏時間のある地域。この時期は `-05:00`）。端数の時差は `Asia/Kolkata`（`+05:30`）・`Asia/Kathmandu`（`+05:45`）・`America/St_Johns`（`-03:30`）で見る。

### 3.1 手順

| # | 確かめ | 行う人 | 通る条件 |
|---|---|---|---|
| S2 | ★ログイン・ホーム・サイドバー・ロゴ（Chrome） | 依頼者 | 開いているタブを読み込み直し、初期管理者でログインする（ログインが要るとき）。ホームに見出し・説明・作業ロールの 1 行・「業務」のカード・「管理」のカード 6 つが出て、アイコンは飾りで名前が文字で読める。サイドバーの業務の項目のアイコンがテーブル・ビュー・フォルダで見分けられ、管理の区画に「管理」の仮の項目が無く、6 項目が違うアイコンで並ぶ。上のバーの左端にアプリのロゴが出て、ウィンドウを狭めても畳むボタンとユーザーメニューが押せる |
| S3 | 表示のタイムゾーンの選択（Chrome、Temporal の道） | 依頼者 | プリファレンスの画面（`/me/preferences`）に「表示のタイムゾーン」が出て、「ブラウザに合わせる」が選ばれている。選択肢（約 400）を開いて操作でき、`Asia/Kolkata` が `+05:30`、`Asia/Kathmandu` が `+05:45`、`America/St_Johns` が `-03:30` の時差の表記で並ぶ。`America/New_York` を選んで保存し、読み込み直しても選ばれたまま。管理の画面（利用者の一覧・招待・DSL の状態）の時刻が `YYYY-MM-DD HH:mm -05:00` の形で出る。開発者ツールの通信で、ログインの直後の `GET /api/me/preferences` が 1 回で、画面を移っても増えない（プリファレンスの画面を開くと 1 回足される） |
| S4 | 業務データの時刻の読み取り（U3・U4） | 依頼者 | `sales.customers` の一覧を開く。見出しの下に案内の1行が出て、`created_at` が `America/New_York` の時刻と時差で出て、端数の桁が表示される。詳細を開くと同じ時刻と案内の1行が出る。検索で `created_at` の範囲を入れると両端を含む結果になる |
| S5 | ★業務データの時刻の書き込み（U4） | 依頼者 | 作成の画面で、時刻の欄に「America/New_York (-05:00) で入力」の案内が出る。夏時間で存在しない時刻（例: `2027-03-14 02:30`）を入れると誤りが出て、重なる時刻（例: `2026-11-01 01:30`）を入れると2択が出る（どちらも保存を押さずに確かめる）。名前に目印 `U4 確かめ` を付けた行を1件作る。その行を更新で、時刻ではない欄（`note`）だけ直して保存し、時刻が変わらない。最後にその行を削除する |
| S6 | ホームのカードの移り先と `/admin`（U5） | 依頼者 | 業務のカードを押すと、移り先の業務データの画面へ移る（フォルダのカードは最初の移れる子孫の画面）。サイドバーの今の項目が変わる。`/admin` を直接開くと、シェルの中の「見つからない」画面になる。`/admin/users` などの管理の道は開ける。管理者でない利用者がいるときだけ（★）、その利用者でログインしてホームに「管理」の見出しとカードが出ないこと、管理の道を開くと「権限が無い」の表示になることを確かめる |
| S7 | favicon とアプリのアイコン（U6） | AI と依頼者 | AI: `curl -sI http://127.0.0.1:8080/favicon.ico`（未認証）が 200・`Content-Type` が `image/` で始まる・`X-Content-Type-Options: nosniff`。先頭 4 バイトが `00 00 01 00`。`/favicon.svg` が 200・`image/svg+xml`・`nosniff`。依頼者: 初めて開く窓（プライベートの窓）で、タブの favicon とログイン画面のアプリ名の上のアイコンが出る。ブランドカラーとテーマを変えると、ログイン画面のアイコンの色が文字の色に追従する。`/favicon.ico` と `/favicon.svg` を開くと、画像として出る（HTML の画面が出ない） |
| S8 | ★Firefox・Safari（Q3: A） | 依頼者 | それぞれでログインし、S3 の選択肢の確かめ（約 400 の選択肢・3つの端数の時差の表記が Chrome と同じ）と、別の名前（Firefox は `Asia/Kolkata`、Safari は `America/St_Johns`）を選んで保存し、管理の画面の時刻がその時差で出ること、S4 の一覧の `created_at` がその時差で出ることを確かめる。タブの favicon が出る（初めて開く窓で）。Safari は Intl の道を通る。Firefox の版を控える |
| S9 | ★既定の DSL の生成と破棄（U3） | 依頼者 | Chrome で、DSL の画面から既定の DSL を生成する。生成が失敗せず、プレビューで `sales.customers.created_at` などの端数を持つ時刻の列にだけ `dbType.precision` が出る。確かめた後にプレビューを破棄する（適用しない） |
| S10 | 片付け・監査と状態の確かめ | 依頼者と AI | 依頼者: Chrome で表示のタイムゾーンを「ブラウザに合わせる」に戻して保存する（S8 で別の名前を保存した後のため、最後に1回）。AI: 配備の後の複写（`cd-config.md` の順 10）を読み取りで開き、`flyway_schema_history` の最大の版が 14、`users.display_time_zone` の列が在り、値の入った行が 0 件、`audit_events` の種類と結果ごとの件数を 4.1 と比べて 3.2 と照らす。見本の対象DB で `sales.customers` の名前に目印 `U4 確かめ` を含む行が 0 件（読み取りだけのアカウントで件数だけを数える）。スモークテストの間（T0 から S10 の前まで）のアプリのログで、ERROR と、業務データ・メニュー・プリファレンスの API の 5xx を数え、どちらも 0 件（F2: A） |

- 依頼者が画面で触れるときは、監査に残る操作（ログインの回数・ログインの失敗・管理の操作・業務データの書き込みの回数）をその場で聞き、期待の件数を合わせてから数える。「済んだ」の答えの後も、複写と対象DB で数えてから判定する（`project.md` の学び）。
- 複数のタブ・ブラウザは同期しない（U2 の SC4）。保存した窓以外は、読み込み直すまで古い値のまま。
- ブラウザは favicon を強く保持する。既存の窓で古い図柄が見えても、初めて開く窓で出れば通る。初めて開く窓でも出ないときの手当て（ファイル名の変更など）は、見つかったときに決める（U6 の SD4.3）。

### 3.2 監査の期待（`AuditEventType` で確かめた種類だけ）

`backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java` と、出す側（`DslLifecycle`・`AdminAccessDeniedEvent`）で確かめた。表示のタイムゾーンの保存、業務データの一覧・詳細・検索、favicon の要求、画面の中の時刻の誤りと2択は、どの種類にも当たらず監査に残らない。

| 種類 | 結果 | 期待の件数 | どの操作 |
|---|---|---|---|
| `LOGIN_SUCCEEDED` | `SUCCESS` | 依頼者がログインした回数（Chrome・Firefox・Safari と、管理者でない利用者で各1回の見込み。リフレッシュトークンで続けば減る） | S2・S6・S8 |
| `DSL_GENERATED` | `SUCCESS` | 1 | S9 |
| `DSL_PREVIEW_DISCARDED` | `SUCCESS` | 1 | S9 |
| `BUSINESS_ROW_CREATED` | `SUCCESS` | 1 | S5 |
| `BUSINESS_ROW_UPDATED` | `SUCCESS` | 1 | S5 |
| `BUSINESS_ROW_DELETED` | `SUCCESS` | 1 | S5 |
| `ACCESS_DENIED` | — | 管理者でない利用者で管理の道を開いたときの、拒否された管理の API の回数（S6 を行わなければ 0） | S6 |
| `LOGGED_OUT` | `SUCCESS` | 依頼者がログアウトした回数 | — |
| 上のほかの結果 `FAILURE`・`UNKNOWN` のすべて | — | 0 | 予定に無い |
| `DSL_SUBMITTED`・`DSL_APPLIED`・`INVITATION_*`・`INITIAL_ADMIN_RESCUED`・`ROLE_*`・`WORK_ROLE_SWITCHED`・`GROUP_*`・`USER_*`・`REGISTRATION_*`・`PASSWORD_CHANGED`・`LOGIN_FAILED`・`LOGIN_FAILURES_RESET` | — | 0 | 予定に無い |

### 3.3 261102 の単位ごとの確かめとの対応

| 単位の確かめ | この手順 |
|---|---|
| U2 の S1（V14 の当たり） | S1 |
| U2 の S2〜S4（選択・保存・管理の画面の時差・通信の回数） | S3 |
| U2 の S5（「ブラウザに合わせる」に戻す片付け） | S10 |
| U2 の S6（無効な名前の拒否） | 省く（画面から作れない。結合テストと 261102 の k6 の `invalid` で確かめ済み。`cd-config.md` の 5節） |
| U2 の S7・S8（複写での列と版、保存で監査が増えない） | S10 |
| U3（日時の列の値と端数の桁、既定の DSL の生成） | S4・S9 |
| U4 の S1・S2・S5（一覧・詳細・検索の範囲） | S4 |
| U4 の S3・S4（作成と更新の欄、時刻が変わらない更新）と後始末 | S5 |
| U4 の S6（参照の選択肢に時点の型のラベルがある表） | 見本の対象DB に当たる表が無いため省く（U4 の設計の「用意できるとき」） |
| U4・U5 の S7（監視の区画） | S10 のアプリのログの数え（F2: A） |
| U5 の S1・S3・S4（ホーム・サイドバー・ロゴ） | S2 |
| U5 の S2・S5・S6（カードの移り先・`/admin`・管理者でない利用者） | S6 |
| U6 の S1〜S3（`/favicon.ico`・`/favicon.svg`） | S7（AI） |
| U6 の S4（`.png`） | 採っていないため省く（`frontend/public/` に `.png` の素材は無い） |
| U6 の S5・S6・SD4.3（タブの favicon・アイコンの色・画像として出る） | S7（依頼者）・S8 |
| U1・U6 の Firefox・Safari（Unverified） | S8 |
| この Intent の Intl の道（Build and Test では jsdom だけ） | S8 の Safari |

## 4. 通らなかったとき

- 通らない点があれば、`rollback-runbook.md` の 2節で戻すかを依頼者に諮る。
- S1 で起動しない・Flyway の誤りが出るときは、まずログのロガーと例外のクラスの名前を読み（値は出さない）、V14 の当たりによるものかを確かめてから諮る。V14 の途中の失敗のときは、`rollback-runbook.md` の 4節（配備の前の複写を戻す）を検討する。
- S3・S8 で選択肢が出ない・時差の表記がブラウザで食い違うときは、ブラウザの名前と版と、食い違った名前を控える（値はタイムゾーンの名前だけで、個人の値ではない）。Safari だけの食い違いは Intl の道の不具合とみなし、戻すかを諮る（戻すと 261102 の作りの選択肢に戻る）。
- S4・S5 で業務データの要求が 5xx になるときは、アプリのログの `code` と SQLState を読む（値は出さない）。
- S5 で作った行が残ったまま戻すときは、戻す前に依頼者が画面で削除するか、残すかを決めて記録する（戻した版でも削除はできる）。
- 戻すと決めるまでは、状態（行の数・監査）を変える操作をしない。

## Sources

- 前の手順: `aidlc/spaces/default/intents/261101-maintenance-update/operation/deployment-pipeline/deployment-strategy.md`
- 引き受けた Intent の設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/` の `u1-time-zone-conversion/`〜`u6-app-icon-assets/` の `infrastructure-design/cicd-pipeline.md`、`u6-app-icon-assets/nfr-design/security-design.md`（SD4.3）、`u2-display-time-zone/infrastructure-design/monitoring-design.md`
- `cd-config.md`・`rollback-runbook.md`・`deployment-pipeline-questions.md`
- Build and Test: `construction/build-and-test/test-results.md`
- リポジトリ: `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java`、`backend/src/main/java/cherry/mastersmith/dslmanage/service/DslLifecycle.java`、`backend/src/main/java/cherry/mastersmith/access/domain/AdminAccessDeniedEvent.java`、`frontend/src/features/preferences/`（`messages.ts`・`registration.ts`）、`docker/targetdb/postgres/01-sample-schema.sql`、`compose.yaml`
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment）、`project.md`（Deployment・Corrections の学び）

## Assumptions & Open Questions

- [assumption] 初期管理者でログインする操作と画面の操作は依頼者が行う（パスワードを AI が扱わない）。
- [assumption] アプリの公開の番号は `127.0.0.1:8080`（`compose.yaml`）。違えば S7 の URL を `compose.yaml` の値に合わせる。
- [assumption] 管理者でない利用者が手元にいるかは、S6 の前に依頼者に尋ねる。いなければ S6 の後半を省き、結合テストと E2E で確かめ済みとして記録する。
- [assumption] S9 の生成は、見本の対象DB の今の表から既定の DSL を作るだけで、適用中の DSL を変えない（破棄するため）。
