# 障害ごとの手順書（runbooks）

前の2つの手順書を正とし、この Intent（利用者の管理: U1 利用停止・U2 ページ送りの共通化・U3 管理の API・U4 403 の画面・U5 利用者の管理の画面）で増えた障害（RB-21 から）と、古くなった箇所の直しだけを書きます（Q1: A）。障害のときは、まず下の「索引」で RB の置き場を引きます。

- **元の手順書**: `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/runbooks.md`（0節・RB-01〜RB-12・13節 警報ごとの初動の表・14節 定期の確認・15節）。
- **前の手順書**: `aidlc/spaces/default/intents/260925-user-management/operation/incident-response/runbooks.md`（RB-13〜RB-20・21節・22節・23節と、元の手順書の直し）。
- どの障害でも最初に行うこと（状態・ログを見る、作り直す前にログを保存する、記録を始める）は、元の手順書の 0節のままです。
- 記号: 【承認】は、AI が行うときに依頼者の承認が要る操作です。【依頼者】は依頼者が行う操作です（`.env` の編集、秘密情報が要るログイン、画面の操作）。印の無いものは読み取りだけです。
- ログの文言・code・監査の種類は、`backend/src/main/java/` のソースか、この Intent の `operation/observability-setup/` の確かめで確かめたものです（2026-10-03）。確かめられなかったものは「推測」と書きます。
- **値を出さない。** メールアドレス・氏名・利用者 ID・送り元の IP・パスワード・トークンは、コマンドの出力・記録・報告に出しません。件数と、ロガー・キーの名前だけで見ます（`aidlc/spaces/default/memory/project.md` の Corrections・Forbidden）。
- 警報の確かめの区別（`operation/observability-setup/alarms.md` 2節）: 鳴ることを確かめたのは `ms-forbidden` だけです。`ms-pool-pending`・`ms-error-logs`・`ms-audit-fail` は performance-validation で確かめます（この段の次）。`ms-5xx-ratio`・`ms-audit-slow` は Unverified です。以下で「拾う」と書くときは、この区別を添えます。

## 索引（RB-01〜RB-27）

| RB | 症状 | 重さ | 置き場 |
|---|---|---|---|
| RB-01 | アプリが止まる・healthy にならない | 高 | 元の手順書（起動が止まる設定の誤りは前の手順書の RB-15 も） |
| RB-02 | メモリの上限（2g）に近い・OOMKilled | OOMKilled なら高、近いだけなら低 | 元の手順書 |
| RB-03 | 内部DB のファイルの伸び | 低（ディスクが尽きたら高） | 元の手順書 |
| RB-04 | 見本の対象DB が止まった・応答しない | 低 | 元の手順書 |
| RB-05 | 対象DB の設定の誤り | 低 | 元の手順書 |
| RB-06 | 起動のときに適用中の DSL を読めない | 低（依頼者の判断で高） | 元の手順書 |
| RB-07 | `DSL_BUSY` が続く | 低 | 元の手順書 |
| RB-08 | 監査の書き込みの失敗・接続プールの待ち | 低（続く・多い、内部DB の不調なら高） | 元の手順書（この手順書の 1節の直しと RB-25 を合わせて読む） |
| RB-09 | ログインの失敗の多発・ロック・回り込みの試み・送り元の不一致 | 低（知らないログインの成功などは高） | 元の手順書（ロックの解き方は 1節の直し） |
| RB-10 | 配備の後の不具合（戻す） | 高 | 元の手順書（この Intent の戻しは RB-26） |
| RB-11 | 秘密情報の漏えいの疑い | 高 | 元の手順書（前の手順書とこの手順書の 1節の直しを合わせて読む） |
| RB-12 | そのほかの起動のときのログ | 低 | 元の手順書 |
| RB-13 | メールの受け手が止まっている・招待のメールが届かない | 低 | 前の手順書 |
| RB-14 | 招待を使えない設定（503 `INVITATION_NOT_CONFIGURED`） | 低 | 前の手順書 |
| RB-15 | 起動が止まる設定の誤り（招待の長さ・メールのテンプレート） | 高 | 前の手順書 |
| RB-16 | 招待の定期の削除の失敗 | 低 | 前の手順書 |
| RB-17 | 総当たりの疑い（登録の完了・今のパスワード） | 低（知らない登録があれば高） | 前の手順書（手順 5 は 1節の直し） |
| RB-18 | 招待のトークン・URL が漏れた疑い | 高 | 前の手順書（手順 4 は 1節の直し） |
| RB-19 | `127.0.0.1` で開いたときの拒否・見た目の設定の不正 | 低（最初の描画が止まるなら高） | 前の手順書 |
| RB-20 | 前の Intent（260925-user-management）の版を戻すときの注意 | 高 | 前の手順書（この Intent の戻しは RB-26） |
| RB-21 | 管理者の誤った操作（印・停止・失敗回数） | 低 | この手順書 |
| RB-22 | 使える管理者がいなくなった | 高 | この手順書（Q2 の手順と確かめの結果） |
| RB-23 | 停止中の利用者が使えない・停止を解きたい | 低 | この手順書 |
| RB-24 | 行の排他の時間切れ（409 `USER_ADMIN_BUSY`、既存の経路の 500） | 低（500 が続くなら高） | この手順書 |
| RB-25 | 5つの操作の確定の後の監査の書き込みの失敗 | 低（続く・多いなら高） | この手順書（RB-08 の足し） |
| RB-26 | この Intent の版を戻すときの注意（戻している間は利用停止が効かない） | 高 | この手順書 |
| RB-27 | 行の「操作」のメニューのはみ出し（既知の不具合） | 低 | この手順書 |

| 節 | 中身 | 置き場 |
|---|---|---|
| 警報ごとの初動の表 | 16 件の警報ごとの最初に見るもの | 元の手順書の 13節、前の手順書の 21節、この手順書の 2節 |
| 定期の確認 | アプリを使う前の確かめ | 元の手順書の 14節、前の手順書の 22節、この手順書の 3節 |
| 確かめられなかったこと | 推測と未確認の一覧 | 元の手順書の 15節、前の手順書の 23節、この手順書の 4節 |

## 1. 前の2つの手順書から直す箇所

| 箇所 | 前の記述 | 今 |
|---|---|---|
| 元の手順書の RB-08（監査の書き込みの失敗・接続プールの待ち） | 確定の後に2本目の接続で監査を書くのはログイン・ログアウト | **利用者の管理の5つの操作**（印を付ける・外す・止める・停止を解く・失敗回数を戻す）も、確定の後に2本目の接続で監査を書きます（README の「監査ログ（U4）」の「既知の制約」）。欠けたときの見分け方は RB-25 |
| 元の手順書の RB-08 の「接続の待ち」の行と、既存の経路の行の排他の時間切れ | 時間切れの例外の種類は推測 | ログイン（実在の利用者）・招待・送り直し・取り消し・登録の完了の**行の排他**の時間切れ（3 秒）は、応答は 500 のまま、ログが2行になりました: repository の WARN「行の排他を取れませんでした」（キー `lockKind`・`exceptionClass`）と、値を含まない例外（`RowLockUnavailableException`）の ERROR「想定外のエラーが起きました」。排他されていた行の値は出ません（U3 の `security-design.md` 7.2・12節の SD-5・SD-6）。見方は RB-24。**接続プールの待ちの時間切れの例外の種類は、今も推測のまま**です |
| 元の手順書の RB-09 の「ロックの多発」の初動 | 「解く画面・API は無い。管理者がロックされたら 30 分待つ」 | ほかの有効な管理者が、利用者の管理の画面の「失敗回数を戻す」（`POST /api/admin/users/{userId}/reset-login-failures`、204）でロックを解けます。監査に `LOGIN_FAILURES_RESET` が残ります。戻した後は失敗回数を 0 から数え直します（README の「利用者の管理の API」）。管理者が1人だけでその人がロックされたときは、今までどおり 30 分待ちます（ロック中の管理者も有効な管理者に数えるため、RB-22 には当たりません）。この操作では `ms-lock`（ロックの多発）は増えません（`alarms.md` 2節） |
| 元の手順書の RB-11 の取り替えの表の「利用者のパスワード」の行（前の手順書の直しを含む）「その間は疑いのある利用者の利用を控える」 | 利用を控える | **利用を止めます**（停止）。【依頼者】ほかの有効な管理者が、利用者の管理の画面で「利用を止める」（`POST /api/admin/users/{userId}/suspend`、204）。止めると、その利用者のリフレッシュトークンはすべて無効になり、アクセストークンも次の要求から拒まれます（U1 の `security-design.md` 2.4・3節）。ただし、**自分自身**（409 `USER_ADMIN_SELF_OPERATION`）と**最後の有効な管理者**（409 `USER_ADMIN_LAST_ADMIN`）は止められません。この場合は RB-22 の手順で別の管理者を作ってから止めます。ほかの利用者のパスワードを管理者が変える手段は、今も無いです |
| 元の手順書の RB-11 の取り替えの表の「リフレッシュトークン」の行と 15節、前の手順書の 23節「すべてのリフレッシュトークンをまとめて無効にする仕組みは無い」 | 仕組みが無い | **利用者ごとには**、止めることでその利用者のリフレッシュトークンをすべて無効にできます。停止を解いても、無効にしたトークンは戻りません（本人がログインし直す）。すべての利用者のトークンをまとめて無効にする仕組みは、今も無いです |
| 前の手順書の RB-17 の手順 5「その利用者の利用を控える」、RB-18 の手順 4（RB-17 の手順 5 と同じ扱い） | 利用を控える | 上の RB-11 の行と同じく、その利用者を止めます（自分自身と最後の有効な管理者は RB-22） |
| 前の手順書の 1節の 13節の行・21節の最後の行、`escalation-matrix.md` の 5節「p95 の警報3件は鳴らない」 | バケットが無く、式が値を出さない | 古くなりました。Intent 260928-quality-followup の FR4 でアプリの指標 `http.server.requests` にバケットが足され、p95 の式が値を出せるようになりました。この Intent の確かめでは、`ms-login-p95` が値（487.5、しきい値 1000 の内側）を返しました。`ms-refresh-p95`・`ms-check-p95` は、確かめの間に当たる要求が無く「結果なし」でした（`alarms.md` 3節）。3件が実際に鳴ることは確かめていません |
| 元の手順書の 13節「管理画面への拒否の増加」の最初に見るもの | 監査の `ACCESS_DENIED` で利用者と接続元IPを見る | 利用者の管理の API の 403 も数に入り、**鳴ることを確かめました**（`ms-forbidden`、`alarms.md` 2節）。入口の 403 は指標では `uri="UNKNOWN"` に入り、道で分けられません。道は監査の `ACCESS_DENIED` の行の `request_path` で、業務の層の確かめ直しの 403 は監査の `USER_ADMIN_*` の `FAILURE`・`NOT_ADMIN` で見ます（`log-queries.md` 2節・3節） |
| 元の手順書の RB-10・前の手順書の RB-20（版を戻す） | 戻し先 `mastersmith:pre-dsl`・`mastersmith:pre-user-management` | この Intent の配備の後は、戻し先が `mastersmith:pre-user-admin` です。注意は RB-26 |

## RB-21 管理者の誤った操作（印・停止・失敗回数）

**見分け方**

| 手がかり | 内容 |
|---|---|
| 症状 | 管理者が誤って、ほかの利用者の印を外した・付けた、利用を止めた、ログインの失敗回数を戻した。または、利用者から「管理の画面が 403 になった」「ログインできない」と知らされた |
| 画面 | 利用者の管理の一覧の行の「管理者」「停止中」「ロック中」の表示（【依頼者】が見る） |
| 監査 | 5つの操作は成功も拒否も監査に残ります（`USER_ADMIN_GRANTED`・`USER_ADMIN_REVOKED`・`USER_SUSPENDED`・`USER_RESUMED`・`LOGIN_FAILURES_RESET`、操作した人・対象・結果・トレースID）。誰がいつ何をしたかは、README の「監査ログの確かめ方」の手順で `log-queries.md` 3節の問い合わせを流して見ます（【承認】アプリを数秒止めて複写する） |
| ログ | 成功はアプリのログに出ません。拒否は WARN「要求をエラー応答に変換しました」（キー `code`・`status`） |
| 警報 | 無い（409 は警報にしない。`alarms.md` 1節） |

**重さ**: 低（ほかに有効な管理者がいれば画面から戻せる）。戻せる管理者がいないときは RB-22（高）。

**手順**（どれも【依頼者】ほかの有効な管理者が、利用者の管理の画面で行う。どれも監査に残り、消せない）

| 誤った操作 | 戻し方 | 戻した後のこと |
|---|---|---|
| 印を外した | 「管理者にする」（`grant-admin`） | 次の要求から管理の API が 200 に戻る（トークンに印を持たないため、ログインし直しは要らない） |
| 印を付けた | 「管理者から外す」（`revoke-admin`） | 次の要求から管理の API が 403 になる。トークンは無効にしない |
| 利用を止めた | 「停止を解く」（`resume`） | **止めたときに無効にしたリフレッシュトークンは戻りません**。本人がログインし直す。止めている間に失敗回数は増えておらず、止める前の回数から続く |
| 失敗回数を戻した | **取り消せません**。戻した後は失敗回数を 0 から数え直す | 攻撃の途中で戻した疑いがあるなら、RB-09 を見て、必要なら止める（1節の RB-11 の行） |

- 自分自身の印は外せず、自分自身は止められません（409 `USER_ADMIN_SELF_OPERATION`）。そのため「自分で自分を誤って外した・止めた」は起きません。
- 停止中の利用者の印は変えられません（409 `USER_ADMIN_TARGET_SUSPENDED`）。印を戻すときは、先に停止を解きます。
- 戻す操作が 409 `USER_ADMIN_NO_CHANGE` になったら、ほかの管理者が既に戻しています。一覧を読み直します。
- 戻す操作が 409 `USER_ADMIN_BUSY` になったら RB-24。

**確かめ方**: 一覧の行の表示が元に戻った。監査の複写を読んだときに、戻す操作の `SUCCESS` が1件ある（件数だけ）。

## RB-22 使える管理者がいなくなった（Q2: A）

最後の有効な管理者の保護（409 `USER_ADMIN_LAST_ADMIN`）で、有効な管理者（印があり停止していない。ロック中も数える）は 0 人になりません。ただし、有効な管理者が**ログインできない**ことは防げません。アプリには、画面と API の外から印・停止を戻す口がありません（`InitialAdminInitializer` は `.env` のメールアドレスの利用者がいないときに作るだけで、既にいる利用者の印・停止は戻さない。ソースで確かめた）。

**当たる場面**

- 依頼者の初期管理者が、もう1人の管理者に印を外された・止められ、そのもう1人の資格情報が分からない（パスワードを忘れた・漏えいの疑いで使えない）。
- 依頼者の初期管理者の資格情報が漏れた疑いがあり、それが最後の有効な管理者のため止められない（自分自身は 409 `USER_ADMIN_SELF_OPERATION`、ほかの管理者からでも 409 `USER_ADMIN_LAST_ADMIN`）。
- 当たらない場面: 管理者がロックされただけ（30 分で解ける。ほかの管理者がいれば失敗回数を戻せる。1節の RB-09 の行）。ほかの有効な管理者がログインできる（RB-21）。**この Intent の版を戻している間**（1つ前の版には利用者の管理の画面が無い。先に新しい版へ戻す。RB-26）。

**重さ**: 高（利用者の管理の操作がすべてできない。漏えいの疑いでは止める手段も無い）。

**手順**

1. 元の手順書の 0節を行う（状態を見る、【承認】作り直す前にログをリポジトリの外へ保存する、記録を始める）。
2. 【依頼者】`.env` の `MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL` を**まだ利用者にいない**新しいメールアドレスに、`MASTERSMITH_AUTH_INITIAL_ADMIN_PASSWORD` を新しいパスワード（12 文字以上、UTF-8 で 72 バイト以内）に替える。
   - 既に利用者にいるメールアドレス（管理者でない利用者を含む）にすると、何も作られません（ログは「初期管理者は既にいるため、作成しませんでした」）。印も付きません（ソースで確かめた。確かめの結果の (b)・(d) と同じ動き）。
   - 元の値を後で戻すために、変える前の `.env` をリポジトリの外（ホームの下、権限 700）へ中身を表示せずに複写してよい（`project.md` の Deployment の学び）。
   - 招待中のメールアドレスを使うと、利用者は作られる見込みですが、その招待は登録の完了で使えなくなる見込みです（推測。避ける）。
3. 【承認】`docker compose --profile targetdb-postgres up -d app` でアプリを作り直す（`.env` は起動のときにだけ読まれる）。`docker compose --profile targetdb-postgres ps app` で healthy を待つ。
4. 起動のログを件数だけで見る（値は出ない。キー `maskedEmail` は伏せ字）。
   ```bash
   docker compose logs --no-log-prefix app | grep -c '初期管理者を作成しました'                       # 1 が期待値
   docker compose logs --no-log-prefix app | grep -c '初期管理者は既にいるため、作成しませんでした'   # 0 が期待値（1 なら手順 2 のアドレスが既にいる）
   docker compose logs --no-log-prefix app | grep -c '初期管理者を作成しませんでした'                 # 0 が期待値（1 なら設定の誤り。キー reason）
   ```
   - **作成は監査に残りません**（確かめの結果の (c)）。障害の記録に、作った時刻と「新しい管理者を作った」ことを書きます（アドレスは書かない）。
5. 【依頼者】新しい管理者でログインし（監査に `LOGIN_SUCCEEDED`）、利用者の管理の画面で戻す（どれも監査に残る）。
   - 元の利用者が止められている: 先に「停止を解く」、次に「管理者にする」（停止中は印を変えられない。409 `USER_ADMIN_TARGET_SUSPENDED`）。
   - 元の利用者の印が外されている: 「管理者にする」。
   - 漏えいの疑いのある利用者: 「利用を止める」（もう最後の有効な管理者ではないため止められる。その利用者のリフレッシュトークンはすべて無効になる）。
6. 【依頼者】`.env` の2行を元のメールアドレスとパスワードに戻し（手順 2 の複写を戻してよい）、【承認】`docker compose --profile targetdb-postgres up -d app` で作り直す。起動のログの「初期管理者は既にいるため、作成しませんでした」が 1、「初期管理者を作成しました」が 0 であること（確かめの結果の (d)）。
   - 元に戻すのは、`operation/deployment-pipeline/rollback-runbook.md` 5節の「初期管理者のメールアドレスは変えない」に合わせるためです。
7. 新しく作った管理者は消せません（利用者を消す仕組みが無い）。要らなければ、【依頼者】元の管理者でログインし直して、その管理者を「利用を止める」（監査に `USER_SUSPENDED`）。もう一度要るときは停止を解きます。

**確かめ方**: 手順 4・6 の件数、【依頼者】元の管理者でログインして利用者の管理の画面が開ける、一覧の行の表示が意図どおり。

**採らなかった手（Q2）**: 内部DB を書き込みで開いて SQL で印・停止の列を直接書き換える手（監査に残らず、`project.md` の Mandated「権限・状態を変える管理の操作は監査に残す」を外れる）と、最新の複写を展開して戻す手（複写の後の利用者・監査・招待の記録が消える）は、この手順書に載せません。手順 2〜5 で戻せないときは、依頼者がその場で判断します。

### RB-22 の確かめの結果（Q3: A、2026-10-03）

使い捨ての環境（`docker/perf/compose.yaml`、プロジェクト `mastersmith-perf`）で、手順 2〜6 の「初期管理者のメールアドレスを替えて作り直す」部分を確かめました。配備したアプリ・リポジトリの `.env` には触れず、API の要求は1件も送っていません。

**条件**

| 項目 | 値 |
|---|---|
| イメージ | `mastersmith:local`（`sha256:f386b55f16a8…`、配備した版と同じ。ソース `b126bdc`） |
| 上限 | CPU 2・メモリ 1g（`MASTERSMITH_CONTAINER_CPUS`・`MASTERSMITH_CONTAINER_MEMORY` をシェルで渡した） |
| 設定 | ホームの下の一時の置き場（権限 700）の環境ファイルだけで渡した（`MASTERSMITH_PERF_ENV_FILE`）。仮の署名鍵と仮のパスワードは乱数で作り、表示していない。仮の初期管理者は A・B とも予約のドメイン `@example.test` |
| 作り直し | (a) は `up -d --wait app`、(b)〜(d) は環境ファイルの1行を替えて `up -d --wait --force-recreate app`。台本の全体を `caffeinate -i` で包んだ |
| 数え方 | ログは各回のコンテナの `docker logs` を件数だけで数えた。内部DB は、アプリを止めてからボリュームを一時の置き場へ複写し、複写を H2 の道具で読み取り（`ACCESS_MODE_DATA=r`）で開いて件数だけを数えた |
| 時刻 | 22:43:03〜22:43:40（JST）。各回 10 秒ほどで healthy |

**結果**

| 回 | 操作 | 健全性 | 「作成しました」 | 「既にいるため」 | 「作成しませんでした」 | 利用者 | 管理者 | 有効な管理者 | 監査の行 |
|---|---|---|---|---|---|---|---|---|---|
| (a) | A で起動 | healthy | 1 | 0 | 0 | 1 | 1 | 1 | 0 |
| (b) | A のまま作り直す | healthy | 0 | 1 | 0 | 1 | 1 | 1 | 0 |
| (c) | まだいない B に替えて作り直す | healthy | 1 | 0 | 0 | 2 | 2 | 2 | 0 |
| (d) | A に戻して作り直す | healthy | 0 | 1 | 0 | 2 | 2 | 2 | 0 |

- (b)・(d): 既にいる利用者のメールアドレスでは何も作られず、管理者の数が変わりませんでした。
- (c): 新しい管理者が作られ、管理者の数が1つ増えました。監査の行は増えていません（作成は監査に残らない）。
- 漏えいの確かめ（各回）: キー `maskedEmail` の行は1件ずつで、A・B のメールアドレスそのものと仮のパスワードがログに出た件数はどれも 0 でした。
- 複写のときに `cp` が「所有者を保てない」と2行警告しました（colima の受け渡しの場所の制約と見られる）。ファイルは複写され、各回で件数が変わることから、毎回新しい複写を読めたと判断しました。

**確かめていないこと**

- 手順 5（新しい管理者でのログインと、画面での戻し）。5つの操作そのものは Observability Setup と Build and Test で確かめ済みのため、ここでは行っていません。
- 配備した内部DB（利用者・監査の行がある状態）での作り直し。使い捨ての環境は空の内部DB から始めています。
- 招待中のメールアドレスを使ったとき（手順 2 の推測）。

**片付け**（結果を見てから行った）: `docker compose -p mastersmith-perf -f docker/perf/compose.yaml down -v` でコンテナ・ボリューム `mastersmith-perf_perf-data`・網 `mastersmith-perf_default` を消し、一時の置き場（環境ファイル・仮の鍵とパスワード・内部DB の複写・台本）を消しました。`docker compose -p mastersmith ps` で、配備したアプリ `mastersmith-app-1`（`mastersmith:local`、Up・healthy、確かめの前と同じ起動の時刻）・`mastersmith-mailpit-1`（Up・healthy）・`mastersmith-targetdb-postgres-1`（Up）が元どおりであることを確かめました。

## RB-23 停止中の利用者が使えない・停止を解きたい

**見分け方**

| 手がかり | 内容 |
|---|---|
| 症状 | 利用者がログインできない、画面が急にログインの画面に戻る |
| 応答 | ログイン 401 `AUTHENTICATION_FAILED`・トークンの更新 401 `REFRESH_FAILED`・アクセストークンの認証 401 `AUTHENTICATION_REQUIRED`。**ほかの失敗と見分けがつきません**（停止を応答に出さない。U1 の `security-design.md` 2.5） |
| 画面 | 利用者の管理の一覧の行の「停止中」（【依頼者】が見る。いちばん早い見分け方） |
| 監査 | 停止中のログインだけが `LOGIN_FAILED`（理由 `ACCOUNT_SUSPENDED`）で残ります。トークンの更新とアクセストークンの認証の拒否は監査に残りません。誰が止めたかは `USER_SUSPENDED` の `SUCCESS` で見ます（【承認】README の「監査ログの確かめ方」） |
| ロック | 停止中かつロック中の利用者の理由は `ACCOUNT_SUSPENDED` です。停止中のログインの試みは失敗回数に数えません |
| 警報 | 無い |

**重さ**: 低。

**手順**

1. 【依頼者】一覧で停止中かを見る。止めた理由（漏えいの疑いなど）が残っていないかを、障害の記録で確かめる。
2. 解いてよければ、【依頼者】「停止を解く」（`POST /api/admin/users/{userId}/resume`、204。監査に `USER_RESUMED`）。停止を解く操作は、操作する人自身には使えません（409 `USER_ADMIN_SELF_OPERATION`）。
3. 本人にログインし直してもらう。**止めたときに無効にしたリフレッシュトークンは戻りません**。止める前に取ったアクセストークンは、有効期限（既定 5 分）の内なら解いた後に再び通ります（U1 の `security-design.md` 3節）。
4. 止める前のロックの状態と失敗回数はそのまま続きます。ロック中なら、必要に応じて「失敗回数を戻す」（1節の RB-09 の行）。

**確かめ方**: 一覧の行の「停止中」が消えた。本人がログインできた（【依頼者】が確かめる）。

## RB-24 行の排他の時間切れ（409 `USER_ADMIN_BUSY`、既存の経路の 500）

**見分け方**

| 経路 | 応答 | ログ（ソースで確かめた） | 監査 | 警報 |
|---|---|---|---|---|
| 利用者の管理の5つの操作 | 409 `USER_ADMIN_BUSY`（画面は「ほかの処理と重なったため、操作できませんでした。少し待ってから、もう一度操作してください。」。状態は変わらない） | WARN「行の排他を取れませんでした」（キー `lockKind`: `ADMIN_ROWS`・`USER_ROW`・`LOGIN_ATTEMPT_ROW`・`REFRESH_TOKEN_ROWS`、`exceptionClass`）と、WARN「要求をエラー応答に変換しました」（`code` `USER_ADMIN_BUSY`）の2行 | 残らない | 無い（409 は警報にしない） |
| ログイン（実在の利用者）・招待・送り直し・取り消し・登録の完了（U3 の `security-design.md` 7.2 の E1〜E4） | 500 `INTERNAL_ERROR`（今までどおり。巻き戻る） | WARN「行の排他を取れませんでした」（`lockKind` `LOGIN_ATTEMPT_ROW`・`INVITATION_ROW`）と、ERROR「想定外のエラーが起きました」（スタックトレースは値を含まない `RowLockUnavailableException`）の2行 | 今までどおり（排他の前に監査の出来事を出す経路は無い） | `ms-5xx-ratio`（Unverified）・`ms-error-logs`（5 分に 5 件を超えたとき。performance-validation で確かめる） |

- 2行は同じ `traceId` で結び付きます（`team.md` の「例外のログは境界で1回」との差として受け入れた SD-5）。Loki の問い合わせは `log-queries.md` 2節の L3（BUSY の件数）・L4（排他の WARN の種類ごと）です。2行の結び付きは、実際に BUSY を起こして確かめてはいません（`log-queries.md` 2節、performance-validation の (B) の場面で見る）。
- 排他の待ちの上限は 3 秒です。同じ利用者・管理者の行への操作が重なったとき、または長いトランザクションが行を持っているときに起きます。

**重さ**: 低。既存の経路の 500 が続く（ログイン・招待ができない）なら高。

**手順**

1. 少し（30 秒ほど）待ってやり直す。断られた操作は状態を変えず、監査にも残りません。
2. 続くなら、件数と種類を見る（手元の監視が動いていなければ `docker compose logs` で数える。値は出ない）。
   ```bash
   docker compose logs --no-log-prefix app | grep -c '行の排他を取れませんでした'
   docker compose logs --no-log-prefix app | grep '行の排他を取れませんでした' | grep -o '"lockKind":"[A-Z_]*"' | sort | uniq -c
   docker compose logs --no-log-prefix app | grep -c '"code":"USER_ADMIN_BUSY"'
   ```
3. 同じ `traceId` の前後のログで、長く動いている操作を探す。元の手順書の RB-07（`DSL_BUSY`）・RB-08（接続プールの待ち）と同じ見方をする（負荷の試験を配備したアプリに向けていないか、内部DB の不調）。
4. ほかに操作していないのに数分続く（排他が外れない）なら、【承認】元の手順書の 0節の 3 でログを保存し、`docker compose --profile targetdb-postgres restart app` で起動し直す（アプリを止めると排他は外れる）。

**確かめ方**: やり直した操作が 204 になる。新しい「行の排他を取れませんでした」が出ない。

## RB-25 5つの操作の確定の後の監査の書き込みの失敗（RB-08 の足し）

**見分け方**

| 手がかり | 内容 |
|---|---|
| 症状 | 利用者の管理の操作は成功（204）・拒否（409・404・403）のとおりに応答し、状態も変わったのに、監査の行が欠ける |
| ログ | ERROR「監査イベントの記録に失敗しました」（キー `auditEventType`（例 `USER_SUSPENDED`）・`result`・`failureReason`・`actorUserId`・`targetUserId`・`exceptionType` ほか。ソースで確かめた） |
| 原因 | 確定の後に2本目の接続を借りる待ちの時間切れ（5 秒）など。管理の操作とログインの失敗が同時に重なり、接続プールの上限（既定 30）を超えたとき（README の「既知の制約（同時の要求と接続プール）」） |
| 警報 | `ms-audit-fail`（1 時間に1件でも）・`ms-pool-pending`・`ms-error-logs`。**どれも U3 の失敗で鳴ることは、performance-validation で確かめる**（`alarms.md` 2節）。`ms-pool-pending` は短い枯渇では鳴らない見込み（`alarms.md` 2.1節の申し送り） |

**重さ**: 低。続く・多いなら高（元の手順書の RB-08 と同じ）。

**手順**: 元の手順書の RB-08 の手順 1〜5 のとおり。加えて:

- 欠けた操作は、ERROR のキー（`auditEventType`・`actorUserId`・`targetUserId`・`result`）と時刻で分かります。これらの値は障害の記録（リポジトリの外、`~/.mastersmith-incidents/`）にだけ書き、報告には件数と種類と時刻だけを書きます。欠けた記録を補う手順は、今も決まっていません（元の手順書の 15節）。
- 操作の状態は欠けていないため、一覧の表示で今の状態を確かめられます（【依頼者】）。

**確かめ方**: 新しい ERROR「監査イベントの記録に失敗しました」が出ない。

## RB-26 この Intent の版を戻すときの注意（戻している間は利用停止が効かない）

手順は `operation/deployment-pipeline/rollback-runbook.md` です（戻し先 `mastersmith:pre-user-admin`、イメージだけ、スキーマ V9 は戻さない）。元の手順書の RB-10 と前の手順書の RB-20 に次を足します。

- **戻している間は利用停止が効きません**（`rollback-runbook.md` 3節の既知の制約、依頼者が受け入れた F1: A）。停止中の利用者も3つの入口で受け付けられ、止め直す画面と API もありません。戻す前の停止中の利用者の確かめは行いません（Q2: C）。
  - 漏えいの疑いで止めている利用者がいると依頼者が知っているなら、戻すとその利用者が使えるようになることを、戻すかの判断に入れます。
- 戻している間は、RB-21〜RB-23 の画面の操作も、RB-22 の手順もできません。先に直した版へ戻します（直した版が起動した直後から、停止は3つの入口で再び効く）。
- **配備の前のバックアップは取っていません**（`rollback-runbook.md` 2節、Q1: B）。データを配備の前の状態に戻す手はありません。今ある最新の複写は `~/.mastersmith-backup/mastersmith-data-202610032204-after-user-admin.tgz`（配備の後、`operation/deployment-execution/deployment-log.md` 2節）です。
- 初期管理者のメールアドレスは変えないで戻します（`rollback-runbook.md` 5節）。RB-22 の途中（`.env` のメールアドレスを替えている間）に戻さないでください。
- 1つ前の版が V9 の後の内部DB で動くことは、まだ確かめていません（`rollback-runbook.md` 6節）。戻したときの 4.1 の確かめの結果を、その時点の記録に残します。

## RB-27 行の「操作」のメニューのはみ出し（既知の不具合）

| 手がかり | 内容 |
|---|---|
| 症状 | 利用者の管理の一覧で、行の「操作」のメニューを開くと、表の右端でメニューが画面の右へはみ出し、項目の一部が見えないことがある |
| 原因 | `frontend/src/features/useradmin/UserRowActions.tsx` が make-you-chic-ui の Dropdown を既定の `bottom-start` で使っている（Dropdown は左右に位置を変えない。`placement="bottom-end"` の口がある）。Deployment Execution のスモークテストの S4 で見つかった（`operation/deployment-execution/deployment-log.md` 3節） |
| 扱い | 依頼者の決定 B で、後の Intent で直す既知の不具合 |

**重さ**: 低（データと監査には影響しない）。

**回避の方法（推測。確かめていない）**: ブラウザの窓を広げる、表示の倍率を下げる、画面を横にスクロールしてから開く。メニューの項目が押せないときは、ほかの管理者の画面、または窓の幅を変えてから操作します。

## 2. 警報ごとの初動の表（この Intent の差）

元の手順書の 13節と前の手順書の 21節の表に、次を足します。警報は 16 件のまま、ファイルも変えていません（`alarms.md` 1節）。

| 警報名 | 変わったこと | 鳴ることの確かめ | 参照する手順 |
|---|---|---|---|
| 管理画面への拒否の増加（`ms-forbidden`） | 利用者の管理の API の 403（入口の 403 と業務の層の確かめ直しの 403）も数に入る | 確かめた | 1節の 13節の行、RB-21 |
| 監査の書き込みの失敗（`ms-audit-fail`） | 5つの操作の確定の後の監査の失敗も数に入る | performance-validation で確かめる | RB-25 |
| コネクションプールの待ち（`ms-pool-pending`） | 5つの操作も1件で接続を2本使う | performance-validation で確かめる（短い枯渇では鳴らない見込み） | RB-25・RB-24 |
| ERROR のログの増加（`ms-error-logs`） | 既存の経路の行の排他の時間切れ（500）の ERROR、5つの操作の監査の失敗の ERROR | performance-validation で確かめる | RB-24・RB-25 |
| 5xx の割合の増加（`ms-5xx-ratio`） | 既存の経路の行の排他の時間切れの 500 も数に入る | Unverified | RB-24 |
| 監査の書き込みの遅れ（`ms-audit-slow`） | 5つの操作の監査の遅れも数に入る | Unverified | 元の手順書の RB-08 |
| ロックの多発（`ms-lock`） | 失敗回数を戻す操作では増えない | — | 1節の RB-09 の行 |
| ログインの応答の遅れ・トークンの更新の応答の遅れ・確認用 API の遅れ | 値を出せるようになった（前の手順書の「鳴らない」は古い） | `ms-login-p95` が値を返すことだけ確かめた | 1節の p95 の行 |

- 409（`USER_ADMIN_BUSY`・`USER_ADMIN_LAST_ADMIN`・`USER_ADMIN_NO_CHANGE` など）、停止中の利用者の拒否、管理者の誤った操作、使える管理者がいなくなったことには、警報がありません。画面・監査・ログの件数で見ます（RB-21〜RB-24、`log-queries.md` 2節・3節）。

## 3. 定期の確認（この Intent の差）

元の手順書の 14節と前の手順書の 22節の表に、次を足します。

| 確かめ | コマンド | 通る条件 |
|---|---|---|
| 行の排他の時間切れ（前の日） | `docker compose logs --no-log-prefix --since 24h app` を `grep -c '行の排他を取れませんでした'` で数える | 0（0 でなければ RB-24） |
| 監査の書き込みの失敗（前の日） | `docker compose logs --no-log-prefix --since 24h app` を `grep -c '監査イベントの記録に失敗しました'` で数える | 0（0 でなければ RB-25・元の手順書の RB-08） |
| 使える管理者（利用者の管理をする前） | 【依頼者】利用者の管理の一覧で、ログインできる有効な管理者が依頼者のほかにいるかを見る | 依頼者の初期管理者が有効な管理者であること。そうでなければ RB-22 を考える |

## 4. 確かめられなかったこと

| 項目 | 内容 |
|---|---|
| RB-22 | 新しい管理者でのログインと画面での戻し、配備した内部DB での作り直し、招待中のメールアドレスを使ったとき（RB-22 の確かめの結果の「確かめていないこと」） |
| RB-24 | BUSY を実際に起こしたときの2行の結び付き（`log-queries.md` 2節、performance-validation） |
| RB-25 | `ms-audit-fail`・`ms-pool-pending`・`ms-error-logs` が U3 の失敗で鳴ること（performance-validation）。`ms-5xx-ratio`・`ms-audit-slow` は Unverified（feedback-optimization） |
| RB-26 | 1つ前の版が V9 の後の内部DB で動くこと（`rollback-runbook.md` 6節） |
| RB-27 | 回避の方法（推測） |
| 1節 | `ms-refresh-p95`・`ms-check-p95` が値を返すこと、p95 の3件が実際に鳴ること |
| 後の Intent への持ち越し（参照だけ） | N-19 閉じた後のフォーカス、Q-H 一意の制約の違反の例外の文、出力を捕まえるテストの範囲、U3 監査の組み立ての失敗のログ、`ms-pool-pending` の式の見直し、行の「操作」のメニューのはみ出し（`construction/code-generation/gate-decisions.md` 4節、`deployment-log.md` 3節） |

## Sources

- 元の手順書（正とする）: `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/runbooks.md`
- 前の手順書（正とする）: `aidlc/spaces/default/intents/260925-user-management/operation/incident-response/runbooks.md`
- `operation/incident-response/incident-response-questions.md`（決まっていること、Q1: A・Q2: A・Q3: A、確認済みの要約）
- `operation/observability-setup/alarms.md`（1節・2節・2.1節・3節）・`dashboards.md`（2節）・`log-queries.md`（1〜3節）
- `construction/u3-user-admin-api/nfr-design/reliability-design.md`（5節・8節・10節）・`security-design.md`（2節・7節・7.2節・12節）、`construction/u1-user-suspension/nfr-design/security-design.md`（2節・3節）
- `construction/u3-user-admin-api/infrastructure-design/infrastructure-specification.md`（5節）・`monitoring-design.md`、`construction/u4-admin-forbidden-ui/infrastructure-design/infrastructure-specification.md`、`construction/u5-user-admin-ui/infrastructure-design/infrastructure-specification.md`
- `construction/code-generation/gate-decisions.md`（4節）
- `operation/deployment-pipeline/rollback-runbook.md`（2節・3節・5節・6節）、`operation/deployment-execution/deployment-log.md`（1〜3節）
- `README.md`（「利用者の管理の API」「監査ログ（U4）」「既知の制約（同時の要求と接続プール）」「監査ログの確かめ方」）、`perf/README.md`、`docker/perf/compose.yaml`
- ソース: `backend/src/main/java/cherry/mastersmith/user/service/InitialAdminInitializer.java`、`common/persistence/RowLockFailures.java`・`RowLockUnavailableException.java`、`user/repository/UserRowLockRepository.java`、`auth/repository/LoginAttemptStateRepository.java`、`invitation/lock/InvitationLockQueriesImpl.java`、`useradmin/service/UserAdminService.java`、`audit/service/AuditEventListener.java`、`auth/service/LoginService.java`、`backend/src/main/resources/db/migration/V2__u2_user_account.sql`・`V9__u1_user_suspension.sql`
- 実行したコマンドの出力（2026-10-03 22:43〜22:44）: 使い捨ての環境の `docker compose -p mastersmith-perf -f docker/perf/compose.yaml up -d --wait [--force-recreate] app`、`docker logs mastersmith-perf-app-1` の件数、`java -cp h2-2.4.240.jar org.h2.tools.Shell -url "jdbc:h2:file:...;ACCESS_MODE_DATA=r"`（複写の件数）、`docker compose -p mastersmith-perf ... down -v`、`docker compose -p mastersmith ps`

## Assumptions & Open Questions

- RB-22 の手順 2 の「招待中のメールアドレスを使ったときの動き」と、RB-27 の回避の方法は推測です（4節）。
- RB-22 の手順 5・6 の画面での戻しと、配備した内部DB での作り直しは確かめていません。実際に使ったときに、その結果を障害の記録に残します。
