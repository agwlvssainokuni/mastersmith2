# 配備の記録（deployment-log）

Intent 261101-maintenance-update。流れは `operation/deployment-pipeline/cd-config.md` の 3節（順 1〜16）。時刻は UTC。値（`.env` の値・メールアドレス・業務データの値・招待の URL とトークン・メールの本文）は書かない。

## 1. 結果

- **配備した版**: `develop` の `3aa9db1e` から作り直した WAR（アプリのコードは `5950d786`）。WAR の SHA-256 は **`fe3382c51f62d33e30281c80b0ed794ea495c0d05c8015d592dddc3ef8ff6c41`**（Build and Test の WAR と同じ。その後の差は `aidlc/` の記録と `.idea/misc.xml` だけ）。
- **動いているコンテナ**: `mastersmith-app-1`、イメージ `mastersmith:local`（`sha256:ffd2b8380460…`）、JRE Temurin `25.0.5+8`、メモリ 2 GiB・CPU 4、restart 0、healthy。
- **戻し先**: `mastersmith:pre-maintenance-update`（`sha256:d8a467b60f6f…`、前の Intent で配備した版）。
- **アプリが止まった時間**: 入れ替えで 12:16:24Z〜12:17:23Z の約 1 分（見込みの 2〜3 分より短い）、配備の後の複写で 13:43:11Z〜13:43:21Z の約 10 秒。
- **スモークテスト**: S1〜S6 はすべて通過（`smoke-test-results.md`）。健全性は `health-check-report.md`。
- **変わらないもの**: `.env`、内部DB のスキーマ（版 13）、見本の PG・Mailpit のコンテナ（作り直していない）。
- **質問の答え**（`deployment-execution-questions.md`）: Q1 A・Q2 A。
- **残り**: 順 12〜15（片付け・`main` への取り込み・結果の追記）と順 16（依頼者のプッシュ）。

## 2. 手順の記録

| 順 | 時刻 | すること | 結果 |
|---|---|---|---|
| 1 | — | 関門 | 通過。アプリのソース（`aidlc/` の外）の未コミット 0 件。HEAD `3aa9db1e`・`main` `80e1ec8c`・`origin/develop` `45a5872a`（まだ push していない4つ `82987fde`・`dc21dfca`・`7aaa8c39`・`3aa9db1e` は `aidlc/` の記録と `.idea/misc.xml` だけ）。この Intent の CI の run はすべて1回目で成功。電源は AC（100%）。verify・E2E・k6・CI は Build and Test の結果を正とし、流し直していない（`cd-config.md` 3.1） |
| 2 | 停止の前 | `./gradlew :backend:bootWar --rerun-tasks` | 成功。SHA-256 `fe3382c5…` は Build and Test の WAR と同じ（`BOOT-INF/lib`・`static/` の比べは不要）。シェルの `MASTERSMITH_IMAGE_TAG` 0 件 |
| 3 | — | `docker tag mastersmith:local mastersmith:pre-maintenance-update` | ID `d8a467b60f6f…` を確認 |
| 4 | — | 救済の見込みを依頼者に確認（Q1） | A（初期管理者について変更なし）。順 5 の数え（停止 0・印 1）と順 7 の救済 0 件で裏付けた |
| 5 | 12:16:24（T0） | `docker compose stop app`。複写と数え（Q2: A の承認の後） | 複写 `~/.mastersmith-backup/mastersmith-data-202611022116-pre-maintenance-update.tgz`（権限 600、40,012 バイト）。数えの結果は 2.1。展開した作業ディレクトリは消した。展開のときの所有者の変更の誤り（`Cannot change ownership`）は、ホームの下へ展開するときに出る想定内のもの |
| 6 | 12:17:10（T1） | `docker compose up -d --build --no-deps app` | コンテナのイメージ ID `ffd2b8380460…` が `mastersmith:local` と一致（作り直しの追加は不要）。見本の PG・Mailpit は作り直されていない |
| 7 | 12:17:23 | 健全性 S1 | healthy、`/actuator/health` 200、JRE `25.0.5+8`。ERROR 0 件。詳細は `health-check-report.md` |
| 8 | — | 4.1 の結果での合わせ直し | 不要（2.1 のとおり前提どおり） |
| 9 | 13:40 ごろ | スモークテスト S2〜S5（依頼者） | 依頼者「1: OK, 2: OK, 3: OK, 4: OK. ログアウトしました。」。Mailpit の基準は S4 の前に 58 通（12:17:44Z ごろ）。AI の裏付けは `smoke-test-results.md` |
| 10 | 13:43:11〜13:43:21 | `stop app` → 複写 → `start app` | 複写 `~/.mastersmith-backup/mastersmith-data-202611022243-after-maintenance-update.tgz`（権限 600、41,109 バイト）。起動し直した後もイメージ ID が一致、healthy、restart 0。数えの結果は 2.2（S6） |
| 11 | — | 配備の記録のコミット | 段の成果物の初稿として尋ねずにコミットする |
| 12〜13 | — | 片付けの前後の `docker system df` と古いイメージの片付け | まだ |
| 14 | — | `main` への取り込み | まだ。`origin/develop` は依頼者のプッシュで `3aa9db1e` になった |
| 15・16 | — | 結果の追記・プッシュ | まだ |

### 2.1 配備の前の複写の数え（`cd-config.md` 4.1）

| 項目 | 見込み | 結果 |
|---|---|---|
| スキーマの版 | 前の配備と同じ | 13 |
| 適用済みの DSL・プレビュー | — | 7・0 |
| `roles`・`permission_settings` | — | 3・5 |
| 利用者の数・有効な管理者 | — | 3・1 |
| 初期管理者の停止・印 | 0・1 | 0・1 |
| 初期管理者の直接の割り当て・グループ経由・作業ロールの保存 | — | 0・3・1 |
| 招待の行・招待中 | 招待中は前の Intent の配備の後と同じ | 2・0 |
| `audit_events` の全体（基準） | — | 310（`SUCCESS` 284・`FAILURE` 26） |

### 2.2 配備の後の複写の数え（S6）

- `audit_events` の全体 314（`SUCCESS` 288・`FAILURE` 26）。差は `LOGIN_SUCCEEDED`・`INVITATION_ISSUED`・`INVITATION_CANCELLED`・`LOGGED_OUT` の各 1 件（内訳は `smoke-test-results.md` の 2節）。
- 招待の行 3（招待中 0・使用済み 2・取り消し済み 1）、送信の成功 3・失敗 0。
- 初期管理者・ロール・利用者・DSL の数は 2.1 と同じ。展開した作業ディレクトリは消した。

## 3. 計画との差・気づき

1. **数える道具**: `docs/internal-db.md` の `CountRows.java` を、文書のとおり `~/.mastersmith-tools/`（権限 600）に置いた。招待中の数は `count invitations state PENDING` で数えた（`group` は値を並べてよい列（`event_type`・`result`・`failure_reason`）に限るため、`state`・`send_result` は値ごとの `count` で数えた）。前の Intent の `count-db3.sh` も同じ複写で使い、初期管理者・DSL・ロールの数を出した。どちらも値を表示していない。最初に招待の列を `status` と書いて「列なし」と出た（誤りの文は出ていない）。列の名前を Flyway の移行（`V8__u3_invitation.sql`）で確かめて `state` に直した。
2. **止まった時間**: 見込み（約 2〜3 分）より短い約 1 分。
3. **Mailpit の件数**: S4 の前の 58 通が S4 の後に 1 通になった（前からのメールも依頼者が消したものと読む。`smoke-test-results.md` 3節）。

## Sources

- `operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`・`deployment-pipeline-questions.md`
- 前の記録: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/deployment-execution/`
- `deployment-execution-questions.md`（Q1・Q2）
- `construction/build-and-test/test-results.md`（配備する版の verify・E2E・k6・CI と WAR の SHA-256）
- 実行したコマンドの出力（`docker inspect`・`docker logs`・`/actuator/health`・`shasum -a 256`・Mailpit の API・複写の読み取り）と依頼者の報告
- リポジトリ: `Dockerfile`、`compose.yaml`、`docs/internal-db.md`、`backend/src/main/resources/db/migration/V8__u3_invitation.sql`

## Assumptions & Open Questions

- [assumption] 順 12〜16 は、この記録の初稿のコミットの後に行い、結果を順 15 のコミットで追記する（`cd-config.md` 3節）。
