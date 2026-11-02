# スモークテストの結果（smoke-test-results）

Intent 261101-maintenance-update。手順は `operation/deployment-pipeline/deployment-strategy.md` の 2節・3節。時刻は UTC。値（メールアドレス・パスワード・業務データの値・主キーの値・招待の URL とトークン・メールの本文）は書かない。

- T0（配備の前の停止）: `2026-11-02T12:16:24Z`
- T1（入れ替えの起動）: `2026-11-02T12:17:10Z`
- S2〜S5: 依頼者が 13:40 ごろに行った（対象DB の接続プールの起動のログ 13:40:16Z、招待のメールの送信 13:41:18Z）。画面の操作とログインは依頼者、裏付けは AI。

## 1. 結果の一覧

| # | 確かめ | 行う人 | 結果 | 根拠 |
|---|---|---|---|---|
| S1 | healthy・JRE の版・起動のログ | AI | **通過** | healthy 12:17:23Z、`/actuator/health` 200、イメージ ID が `mastersmith:local`（`ffd2b8380460…`）と一致、JRE `25.0.5+8`。ERROR 0 件、WARN は前の版から出ている 2 件だけ、`Migrating schema` 0 件、救済 0 件、初期管理者のメールアドレスそのものの件数 0。詳細は `health-check-report.md` |
| S2 | ログインと画面の骨組み | 依頼者 | **通過** | 依頼者「OK」。監査 `LOGIN_SUCCEEDED` 1 |
| S3 | 業務データの一覧の並び（R-01） | 依頼者・AI | **通過** | 依頼者「OK」（`customers` の昇順・降順と詳細）。AI: 同じ時間のアプリのログに ERROR・WARN 0 件（5xx の記録なし） |
| S4 | ★招待のメール（java-mustache-processor 0.1.1 の描画） | 依頼者・AI | **通過** | 依頼者「OK」。AI: Mailpit に 13:41:18Z のメールが 1 通、件名は空でない、本文の HTML とテキストのどちらにも `{{` 0 件、宛先 1 件（宛先と本文は表示していない）。監査 `INVITATION_ISSUED` `SUCCESS` 1、招待の送信の結果は成功 3（前 2）・失敗 0 |
| S5 | ★招待の取り消し | 依頼者・AI | **通過** | 依頼者「OK」。監査 `INVITATION_CANCELLED` `SUCCESS` 1。招待中の行は 0（前と同じ）、取り消し済み 1 |
| S6 | 監査と状態の確かめ | AI | **通過** | 2節 |

- 配備の完了の条件（healthy とスモークテストがすべて通る。`team.md` の Deployment）を満たした。

## 2. S6: 配備の後の複写の数え

配備の前の複写（`~/.mastersmith-backup/mastersmith-data-202611022116-pre-maintenance-update.tgz`）と配備の後の複写（`…-202611022243-after-maintenance-update.tgz`、どちらも権限 600）を `~/.mastersmith-tools/count-db3.sh` と `CountRows.java` で読み取りだけで開き、種類と結果ごとの件数を比べた。`audit_events` の全体は 310 → 314。

| 種類 | 結果 | 期待（`deployment-strategy.md` 3.2） | 前 → 後 | 差 | 判定 |
|---|---|---|---|---|---|
| `LOGIN_SUCCEEDED` | `SUCCESS` | ログインした回数 | 63 → 64 | 1 | 合う（S2 で1回） |
| `INVITATION_ISSUED` | `SUCCESS` | 1 | 2 → 3 | 1 | 合う |
| `INVITATION_CANCELLED` | `SUCCESS` | 1 | 0 → 1 | 1 | 合う |
| `LOGGED_OUT` | `SUCCESS` | ログアウトしたら足す | 56 → 57 | 1 | 合う（依頼者が S5 の後にログアウトした） |
| 結果 `FAILURE` | — | 0 | 26 → 26 | 0 | 合う |
| 結果 `UNKNOWN` | — | 0 | 0 → 0 | 0 | 合う |
| ほかの種類（`INITIAL_ADMIN_RESCUED`・`INVITATION_RESENT`・`DSL_*`・`ROLE_*`・`BUSINESS_ROW_*`・`LOGIN_FAILED`・`ACCESS_DENIED` など） | — | 0 | 変わらず | 0 | 合う |
| 合計 | | | 310 → 314 | 4 | |

- 結果ごと: `SUCCESS` 284 → 288、`FAILURE` 26 → 26。`failure_reason` が空の行が 4 件増え、ほかの理由（`PASSWORD_MISMATCH` 4・`ROLE_IN_USE` 1・`TOKEN_MISSING` 21）は変わらない。新しい `INVITATION_ISSUED` は `SUCCESS` で理由は空（送信の成功は監査の結果と理由に失敗として入らない）。
- 招待の行: 招待中 0 → 0、使用済み 2 → 2、取り消し済み 0 → 1、置き換え 0 → 0。送信の結果は成功 2 → 3・失敗 0 → 0。S4 の招待は残っていない。
- 初期管理者: 停止 0・印 1・直接の割り当て 0・グループ経由 3・作業ロールの保存 1（前と同じ）。`roles` 3・`permission_settings` 5・利用者 3・有効な管理者 1・DSL の適用 7・プレビュー 0 も前と同じ。

## 3. 期待との差・気づき

1. **Mailpit の件数**: S4 の前は 58 通だったが、S4 の後は 1 通（今回の招待のメールだけ）だった。前からのメールも依頼者が消したものと読む（Mailpit にはボリュームが無く、消しても配備には関わらない）。届いたことは、残った1通の作成の時刻（13:41:18Z）とアプリのメールの送信のログの時刻が合うことで確かめた。
2. **ログアウト**: 依頼者が S5 の後にログアウトしたと報告し、`LOGGED_OUT` 1 件を期待に足した（`deployment-strategy.md` 3.2 の注記どおり）。
3. **REFRESH_FAILED は出なかった**: 前の Intent では入れ替えの前から開いていたタブの古いトークンの更新で WARN が1件出たが、今回は 0 件だった。

## Sources

- `operation/deployment-pipeline/deployment-strategy.md`（2節・3節・3.2）・`cd-config.md`（4.1・3節の順 10）
- `deployment-log.md`・`health-check-report.md`・`deployment-execution-questions.md`
- 依頼者の報告（「1: OK, 2: OK, 3: OK, 4: OK. ログアウトしました。」）
- `docker logs`（jq でロガーと件数だけ）・Mailpit の API（件数・作成の時刻・件名の長さ・`{{` の件数だけ）・複写の読み取り（`count-db3.sh`・`CountRows.java`）

## Assumptions & Open Questions

- [assumption] Mailpit の 58 通 → 1 通は、依頼者が S4 の前後に前からのメールを消したため（依頼者には尋ねていない。配備の判定には関わらない）。
