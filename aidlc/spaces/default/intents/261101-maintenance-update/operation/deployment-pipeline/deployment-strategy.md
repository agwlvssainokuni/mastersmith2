# 配備の方式とスモークテスト（deployment-strategy）

Intent 261101-maintenance-update の配備の方式とスモークテストです。流れは `cd-config.md` の 3節にあります。方式は前の Intent（`261029-reference-lookup-cleanup`）と同じです。

前の Intent との差は2つです。

- 起動の確かめに、イメージの中の JRE の版を足した。
- スモークテストで、この Intent で変わった所（画面の部品・一覧の並び・メールのテンプレートの描画）だけを確かめる（Q1: A）。

## 1. 方式

- **1 台の置き換え**（recreate）。`docker compose up -d --build --no-deps app` でアプリだけを入れ替え、イメージ ID の一致を `docker inspect` で確かめる（食い違えば `--force-recreate --no-deps app`）。ブルー・グリーンやカナリアは、PC 上の 1 台のコンテナでは意味がないため使わない。
- 環境の段（dev → staging → prod）は持たない。本番配備の承認者は依頼者。承認は、アプリが止まる順 5〜7 の前に 1 回（`cd-config.md`）。
- 内部DB のスキーマ変更は無い（前進のみ・後方互換）。機能フラグは使わない（画面の機能と API の形を変えていない）。
- 配備の完了は、healthy とスモークテストのすべてが通ったとき。通るまで完了とはみなさない。

## 2. 起動の確かめ

| # | 確かめ | 行う人 | 通る条件 |
|---|---|---|---|
| S1 | healthy・JRE の版・起動のログ | AI | 下の4点がすべて通る |

S1 の4点:

1. **健全性**: `mastersmith-app-1` が healthy で、`/actuator/health` が 200（compose と同じ bash の `/dev/tcp` で待つ）。メモリの上限 2 GiB・CPU 4。
2. **イメージ**: コンテナのイメージ ID が `mastersmith:local` の ID と一致する。
3. **JRE の版**: `docker compose exec -T app java -version` が 25.0.5 を示す。
4. **起動のログ**: JSON として読み、ロガーと項目の名前と件数だけを出す。
   - ERROR 0 件。
   - Flyway の `Migrating schema` 0 件。
   - 救済の WARN 0 件。
   - WARN は前の版から出ているもの（Flyway の H2 の版の案内・Bean の後処理の案内の 2 件）だけ。
   - 初期管理者の INFO にメールアドレスそのものが出ていない（キー `maskedEmail` の有無と、`.env` の値の件数で見る。値は表示しない）。

## 3. スモークテスト（Q1: A）

この Intent で変わった所を、配備した版で一度ずつ確かめる。

| 変わった所 | 確かめる手順 |
|---|---|
| make-you-chic-ui と frontend の依存 | S2 |
| 一覧の並び（P1、R-01） | S3 |
| java-mustache-processor 0.1.1 | S4 |

役割・権限・業務データの作成・更新・削除・参照の選択は変えていないため、確かめない（`cd-config.md` の 5節）。

値は記録に書かない。書かないものは、メールアドレス・パスワード・業務データの値・主キーの値・招待の URL とトークン・メールの本文。パスワードが要る操作と画面の操作は依頼者が行い、AI はアプリのログ・監査の記録・Mailpit の件数で裏付ける。★の操作は監査に残るため、行う前に 3.2 の期待の件数を依頼者に伝える。

### 3.1 手順

前提: `cd-config.md` の 4.1 の数えで、初期管理者が停止しておらず管理の印を持つ。違うときは `cd-config.md` の順 8 で合わせ直してから始める。

| # | 確かめ | 行う人 | 通る条件 |
|---|---|---|---|
| S2 | ログインと画面の骨組み | 依頼者 | 開いているタブを読み込み直し、ログインが要れば初期管理者でログインする。サイドバー・トップバー・ホームが出て、表示の崩れ・エラー表示が無い |
| S3 | 業務データの一覧の並び（R-01） | 依頼者 | 業務のメニューから `customers` の一覧を開く。文字のカラムの見出しを押して昇順・降順に並べ替えられ、エラー表示が無い。1 行の詳細が開ける。AI はアプリのログで、同じ時刻の業務データの要求に 5xx・ERROR が無いことを確かめる |
| S4 | ★招待のメール（0.1.1 の描画） | 依頼者 | 「招待」の画面で、架空の宛先 `smoke-maintenance@example.com` に招待を発行する（氏名などの入力は架空の値）。Mailpit の画面（`http://127.0.0.1:8025`）でメールが1通届き、件名が空でなく、本文に `{{` が残っていない。招待の URL は開かない。AI は Mailpit の API で件数と件名の有無だけを数える（本文と宛先は表示しない） |
| S5 | ★招待の取り消し | 依頼者 | S4 の招待を「取り消す」で取り消し、確かめの表示を経て、招待中の一覧から無くなる |
| S6 | 監査と状態の確かめ | AI | 配備の後の複写（`cd-config.md` の順 10）を読み取りで開く。`audit_events` の種類と結果ごとの件数を、配備の前の複写（4.1）と比べて 3.2 と照らす。招待中の招待の行の数が 4.1 と同じ（S4 の招待が残っていない）。依頼者が「済んだ」と答えた後も、複写で状態を数えてから判定する（`project.md` の学び） |

- 依頼者が画面で触れるときは、監査に残る操作（招待の送り直し・ログインの失敗・管理の操作など）をしたかをその場で聞き、期待の件数を合わせてから数える（`project.md` の学び）。
- S4 で、メールの受け手の設定が無い・招待が使えない（招待の画面に「使えない」の知らせが出る）ときは、その場で止めて依頼者に諮る（`.env` の項目は値を表示せずに件数だけで確かめる）。
- Mailpit に置いたメールは、確かめた後に依頼者が Mailpit の画面で消す（ボリュームが無いため、コンテナを作り直しても消える）。

### 3.2 監査の期待（`AuditEventType` で確かめた種類だけ）

`backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java` の種類で確かめた。業務データの一覧・詳細の読み取りと、Mailpit でメールを見ることは、どの種類にも当たらず監査に残らない。

| 種類 | 結果 | 期待の件数 | どの操作 |
|---|---|---|---|
| `LOGIN_SUCCEEDED` | `SUCCESS` | 依頼者がログインした回数（0 もありうる。入れ替えの後もリフレッシュトークンで続けば 0） | S2 |
| `INVITATION_ISSUED` | `SUCCESS` | 1 | S4 |
| `INVITATION_CANCELLED` | `SUCCESS` | 1 | S5 |
| 結果 `FAILURE`・`UNKNOWN` のすべて | — | 0 | 予定に無い |
| `INVITATION_RESENT`・`INITIAL_ADMIN_RESCUED`・`DSL_*`・`ROLE_*`・`WORK_ROLE_SWITCHED`・`GROUP_*`・`USER_*`・`REGISTRATION_*`・`PASSWORD_CHANGED`・`BUSINESS_ROW_*`・`ACCESS_DENIED`・`LOGIN_FAILED`・`LOGIN_FAILURES_RESET` | — | 0（依頼者がログアウトしたときは `LOGGED_OUT` を足す） | 予定に無い |

- 招待の送信の結果（Mailpit への送信の成否）が監査の行の結果や理由に入る形かは、Deployment Execution で `INVITATION_ISSUED` の結果（`result`）と理由（`failure_reason`）を種類ごとに数えて確かめる（値を並べてよい列だけ）。送信に失敗したときは、招待の画面と監査の理由で分かる。

## 4. 通らなかったとき

- 通らない点があれば、`rollback-runbook.md` の 2節で戻すかを依頼者に諮る。
- S1 で起動しない・ERROR が出るときは、まずログのロガーと例外のクラスの名前を読み（値は出さない）、JRE の版の差によるものかを確かめてから諮る。
- S3 で並べ替えが 5xx になるときは、アプリのログの `code` と SQLState を読む（値は出さない）。
- S4 でメールの本文に `{{` が残る・件名が空・本文が崩れるときは、java-mustache-processor 0.1.1 の描画の不具合とみなし、戻すかを諮る。戻すと 0.1.0 の描画に戻る。
- 戻すと決めるまでは、状態（行の数・監査）を変える操作をしない。S4 の招待が残ったまま戻すときは、戻す前に依頼者が画面で取り消すか、残すかを決めて記録する（戻した版でも取り消しはできる）。

## Sources

- 前の手順: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/deployment-pipeline/deployment-strategy.md`
- `cd-config.md`・`rollback-runbook.md`・`deployment-pipeline-questions.md`
- 要件: `inception/requirements-analysis/requirements.md`（FR11.1、NFR4）
- Build and Test: `construction/build-and-test/test-results.md`、`construction/code-generation/code-summary.md`（P1・T3、R-01）
- リポジトリ: `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java`、`frontend/src/features/invitation/`（招待の一覧と取り消しの確かめ）、`compose.yaml`（Mailpit の `127.0.0.1:8025`、ボリュームなし）、`backend/src/main/resources/application.yaml`（`mastersmith.web.base-url`・`mastersmith.mail.from`）
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment）、`project.md`（Deployment・Corrections の学び）

## Assumptions & Open Questions

- [assumption] 初期管理者でログインする操作と画面の操作は依頼者が行う（パスワードを AI が扱わない）。
- [assumption] 取り消した招待は招待中の一覧に出ない（`InvitationList` は招待中の人の一覧）。行は内部DB に取り消し済みとして残る。
- [assumption] `.env` のメールの接続先は手元の Mailpit を指す（`team.md` の Deployment の決まりで、外部の SMTP の設定は置いていない）。S4 の前に Mailpit が動いていることを確かめ、届かないときは止めて諮る。
