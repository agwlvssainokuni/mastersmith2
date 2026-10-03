# 戻しの手順書（rollback-runbook）

Intent 260930-user-admin の配備を戻す手順です。README の「戻し方」を正とし、この Intent の差（V9 と利用停止）を書きます。流れの全体は `cd-config.md`、中止の条件は `deployment-strategy.md` の 5節にあります。

## 1. 戻すきっかけ

次のどれかが起きたら、依頼者に伝えて戻すかを決めてもらいます（配備の途中の中止は `deployment-strategy.md` の 5節の条件のとおり戻す）。

| きっかけ | 例 |
|---|---|
| 起動しない | healthy にならない、Flyway の V9 が失敗した |
| スモークテストが通らない | `deployment-strategy.md` の S1〜S5 のどれかが通らず、原因が設定だけと分からない |
| 動きの問題 | ERROR のログが出続ける。ログに秘密・メールアドレスそのものが出る。ログイン・トークンの更新・管理の画面が使えない |
| 後の段で見つかった問題 | Performance Validation・Observability Setup などの段で、この版が原因の重大な問題が見つかり、その場で直せない |

戻す前に、**3節の既知の制約（戻している間は利用停止が効かない）を依頼者に伝えます**。戻す前の停止中の利用者の確かめは行いません（Q2: C、F1: A）。

## 2. 戻しの形: イメージだけ

- 戻し先は、配備の前に付けたタグ `mastersmith:pre-user-admin`（イメージ ID `c77c1bb247f9…`、ソース `8489241`、前の Intent で配備した版）。
- `.env` は変えていないため戻さない。内部DB はそのまま使い、**スキーマは戻さない**（V9 の `users.suspended` の列と値は残る。`team.md` の Deployment の前進のみ）。
- **バックアップは取らない判断（Q1: B）**: V9 は既定 false の列を足すだけで、1つ前の版は列を読まずに動く見込みのため、配備の前のバックアップは取っていません。そのため、データを配備の前の状態に戻す手（バックアップの展開）はありません。データが壊れたときは、その時点の内部DB を複写して調べ、扱いを依頼者に諮ります。

## 3. 既知の制約: 戻している間は利用停止が効かない

V9 を当てた後に1つ前の版へ戻すと、次のとおりになります（README の「戻し方」「スキーマの変更（Flyway）」、U1 の `security-requirements.md` の NFR10.3・R2、U3 の `infrastructure-specification.md` の 5節）。

| 状態 | 戻している間の動き |
|---|---|
| 停止中の利用者 | **ログインの照合・トークンの更新・アクセストークンの認証の3つの入口で受け付けられる**。1つ前の版は `users.suspended` を読まないため。停止中のログインも新しいトークンを取れる |
| 止めたときに無効にしたリフレッシュトークン | 無効のまま（無効の印は前からある列）。ただし、停止中の利用者はログインし直せば新しいトークンを取れる |
| 止め直し | **できない**。1つ前の版には利用者の管理の画面と API が無い |
| 管理者の印・氏名・言語・ログインの失敗回数 | 1つ前の版も同じ列を読むため、配備の後の操作の結果のまま効く |
| 監査の行 | 配備の後に U3 の操作で残った新しい種類・理由の値の行は残る。1つ前の版の本番のコードは監査の行を読まない（追記だけ。`cd-config.md` の 5.1） |

- この制約は依頼者の決定で受け入れました（F1: A）。今の配備先は開発者の PC だけで利用者は依頼者だけ、停止中の利用者は配備の時点で 0 人（`deployment-strategy.md` の S4 で確かめる）の見込みです。
- **直した版を配備し直すと、停止が戻ります**。停止の列は戻している間も内部DB に残っているため、直した版が起動した直後から3つの入口で停止を判定します。
- [assumption] 戻している間に停止中の利用者がログインして取ったリフレッシュトークンは、直した版でも停止中は通りません（トークンの更新の入口で停止を判定するため）。ただし、そのトークンは無効にされていないため、後で停止を解くと有効期限まで使えます。無効にしたいときは、直した版で停止を解いてからもう一度止めます（止める操作がその利用者のリフレッシュトークンをすべて無効にする。README の「利用者の管理の API」）。この2つの操作は監査に残るため、行う前に依頼者が決めます。
- `project.md` の Mandated（利用者の状態の判定は3つの入口のすべてでサーバー側で行う）と `team.md` の Deployment（1つ前の版が動く後方互換）との差は、`cd-config.md` の 4節に記録しています。

## 4. アプリを戻す

```bash
docker image inspect mastersmith:pre-user-admin --format '{{.Id}}'   # 戻し先のイメージがあること（sha256:c77c1bb247f9…）
docker compose stop app                                             # 止めるときに H2 のファイルが詰め直される
# 必要なら、今の内部DB のデータを調べるために複写する（README の「内部DBのバックアップと戻し方」。権限 700 の ~/.mastersmith-backup/ へ）
MASTERSMITH_IMAGE_TAG=pre-user-admin docker compose --profile targetdb-postgres up -d --no-build app
MASTERSMITH_IMAGE_TAG=pre-user-admin docker compose --profile targetdb-postgres ps app   # healthy を待つ
```

- **戻している間は、アプリを起動・作り直す `docker compose` のコマンドに毎回 `MASTERSMITH_IMAGE_TAG=pre-user-admin` を付けます。** 付けずに `docker compose up -d` を行うと、`mastersmith:local`（新しい版）で作り直されます。付け忘れを避けたいときは、`.env` に `MASTERSMITH_IMAGE_TAG=pre-user-admin` の1行を足し、新しい版に戻すときに消します（`.env` の中身は表示しない）。
- **`--no-build` を必ず付けます**（付けないと、戻し先のイメージが上書きされる）。
- 戻し先のタグのイメージが無いときは、README の「戻し方」のとおり、`8489241` を `git worktree add` で取り出して WAR とイメージを作り直します。

### 4.1 戻した後の確かめ

| # | 確かめ | 行う人 | 通る条件 |
|---|---|---|---|
| R1 | healthy と起動のログ | AI | `app` が healthy。ERROR が 0 件（レベルとロガーと件数だけを見る。`deployment-strategy.md` の S1 のコマンド）。Flyway は「スキーマの版（9）が、この版の知っている最新（8）より新しい」旨の WARN を出して起動を続ける見込み（前の Intent の戻しの練習で、版 8 と 6 の組み合わせで同じ WARN を出して起動を続けた）。Hibernate の `validate` は余分な列 `suspended` を許す見込み |
| R2 | ★ログイン | 依頼者（AI は監査で裏付けてもよい） | 初期管理者でログインし、ホームが開ける。`LOGIN_SUCCEEDED` が監査に残る（送る前に依頼者に伝える） |

- R1・R2 は、1つ前の版が V9 の後の内部DB で動くこと（U1-NFR10.2、この Intent では未確かめ）を、実際に戻したときに確かめる機会になります。結果は、その時点の段の記録に残します。
- 1つ前の版には利用者の管理の画面が無いため、サイドバーに「利用者の管理」は出ません（想定どおり）。

## 5. 戻したときのそのほかの注意

| 注意 | 中身 |
|---|---|
| 初期管理者のメールアドレス | `.env` の `MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL` は配備のときのまま変えない（README の V7 の注意。今回も `.env` は変えない） |
| 画面 | 戻し先のイメージは make-you-chic-ui の `077f5b4` のため、利用者の管理の画面と 403 の共通の表示（U4）が無くなる。管理の画面の 403 は、前の版の各画面の扱いに戻る |
| 監視 | この Intent は指標・警報・ダッシュボードを足していないため、戻しても変わらない |
| `main` | `main` を取り込んだ後にアプリを戻しても、`main` は戻さない（タグは付けていない）。直した版を配備し直すときに `main` を進める |
| 見本の対象DB・Mailpit | 戻しの対象外。動かしたままでよい |

## 6. 戻しの練習

行いません。U1 の決定（`construction/u1-user-suspension/infrastructure-design/cicd-pipeline.md` の 6節、NFR 設計の Q4 A）と、バックアップを取らない判断（Q1: B）によります。

- 未確認のまま残る前提: 1つ前の版（`8489241`）が V9 の後の内部DB で起動し、ログイン・更新・監査が動くこと（U1-NFR10.2）。根拠は見込みだけです（`cd-config.md` の 5節）。
- 次に確かめる機会: 実際に戻したときの 4.1 の確かめ、または配備先が決まって戻しの練習を置くとき。

## Sources

- `README.md`（戻し方、内部DBのバックアップと戻し方、スキーマの変更（Flyway）、利用者の管理の API）
- `cd-config.md`・`deployment-strategy.md`・`deployment-pipeline-questions.md`（Q1・Q2・F1・まとめの確認）
- `construction/ci-pipeline/ci-config.md`・`construction/ci-pipeline/quality-gates.md`
- `construction/u1-user-suspension/infrastructure-design/cicd-pipeline.md`（6節、承認の場の決定 R-03）
- `construction/u1-user-suspension/nfr-requirements/security-requirements.md`（NFR10.2・NFR10.3、R2）
- `construction/u3-user-admin-api/infrastructure-design/infrastructure-specification.md`（5節・5.1）・`cicd-pipeline.md`（6節）
- `construction/u2-shared-paging/infrastructure-design/cicd-pipeline.md`・`construction/u4-admin-forbidden-ui/infrastructure-design/infrastructure-specification.md`・`cicd-pipeline.md`・`construction/u5-user-admin-ui/infrastructure-design/infrastructure-specification.md`・`cicd-pipeline.md`（戻しの節。どれもイメージだけで戻る）
- `aidlc/spaces/default/intents/260929-log-deps-cleanup/operation/deployment-pipeline/rollback-runbook.md`（前の Intent の手順書）
- `aidlc/spaces/default/intents/260925-user-management/operation/deployment-execution/health-check-report.md`（戻しの練習の起動のログ）

## Assumptions & Open Questions

- [assumption] 戻している間に停止中の利用者が取ったトークンの、直した版での扱い（3節）は、README と設計の記述から読んだもので、テストで確かめた動きではない。
- [assumption] 戻した後の Flyway の WARN と Hibernate の `validate` の振る舞い（4.1 の R1）は、前の Intent の版 8 と 6 の組み合わせの結果からの見込み。
