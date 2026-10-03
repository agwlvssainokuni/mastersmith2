# 配備の記録（260930-user-admin）

配備先は開発者の PC 上のコンテナ（compose のプロジェクト `mastersmith`）。手順は承認済みの `aidlc/spaces/default/intents/260930-user-admin/operation/deployment-pipeline/deployment-strategy.md`・`cd-config.md` のとおり（Q1: A、まとめの確認 Looks correct）。

## 1. 配備した版

| 項目 | 値 |
|---|---|
| アプリのソース | `b126bdc`（`develop` の先頭 `cc28d1f` とアプリのソースは同じ。その後は `aidlc/` の下の記録だけ） |
| 関門 | clean 付きの verify・osvScan・E2E の全体（Build and Test）、push の後の CI（run 37106195579）が success |
| 新しいイメージ | `mastersmith:local` = `sha256:f386b55f16a8…`（2026-10-03 作成） |
| 戻し先 | `mastersmith:pre-user-admin` = `sha256:c77c1bb247f9…`（前の Intent で配備した版） |
| スキーマ | V8 → V9（`u1 user suspension`） |
| `.env` | 変えていない |

## 2. 手順と時刻（UTC）

| 順 | 操作 | 結果 |
|---|---|---|
| 1 | WAR の確かめ: `./gradlew :backend:bootWar` | UP-TO-DATE（作り直し不要）。WAR の SHA-256 は前後で同じ（先頭 `ad9f8171ac86a817`）。入力のソースが変わっていないため、手元の WAR は `b126bdc` と同じ中身 |
| 2 | `docker tag mastersmith:local mastersmith:pre-user-admin`（12:54:19Z） | タグの ID が `c77c1bb247f9` で始まることを確かめた |
| 3 | 依頼者の承認の後、`docker compose --profile targetdb-postgres up -d --build app`（12:54:56Z 開始） | 終了コード 0。`app` だけを作り直して入れ替え（Recreate）。mailpit・targetdb-postgres は触っていない |
| 3' | healthy を待つ | 12:55:13Z に healthy（開始から約 17 秒） |
| 4 | スモークテスト S1〜S5 | `smoke-test-results.md`。S4 で行の「操作」のメニューのはみ出しが見つかり、依頼者の決定 B で後の Intent で直す既知の不具合とした |
| 4' | S5 のためアプリを止めて内部DB を複写し、起動し直した（13:04 頃、約 10 秒停止） | 13:04:42Z に healthy。複写は `~/.mastersmith-backup/mastersmith-data-202610032204-after-user-admin.tgz`（権限 600、配備の後のバックアップを兼ねる） |
| 5 | 依頼者の承認の後、`main` を `develop` の先頭へ fast-forward（`git fetch . develop:main`。記録のファイルが未コミットのため `main` へ切り替えずに進めた） | `main`: `d748598` → `cc28d1f`。タグは付けていない。push は依頼者が行う |

## 3. 予定との差

- **S5 の複写の操作が一度止められた**: 内部DB のボリュームを複写する操作が、個人に関する値の取り扱いとして実行の許可の仕組みに止められた（コマンドは実行されず、アプリは動いたまま）。依頼者の指示「AIが実施。」で、同じ操作を行った。
- **配備の後の管理の操作**: 予定（S4 まで管理の操作はしない）と違い、配備の後に `USER_ADMIN_GRANTED` 2 件・`USER_ADMIN_REVOKED` 1 件・`USER_SUSPENDED` 1 件・`USER_RESUMED` 1 件と `LOGIN_FAILED` 1 件が記録されていた。依頼者が「自分が行った」と答えた（画面で確かめた操作）。停止中の利用者は 0 人で終わっている。
- **行の「操作」のメニューのはみ出し**: S4 で見つかった。原因は `frontend/src/features/useradmin/UserRowActions.tsx` が make-you-chic-ui の Dropdown を既定の `bottom-start` で使い、表の右端の列でメニューが右へはみ出すこと（Dropdown は左右には位置を変えない。`placement="bottom-end"` の口がある）。E2E 120 の「はみ出し」の確かめは画面全体の横のスクロールだけを見ていて拾えなかった。依頼者の決定 B で、後の Intent への持ち越しに足す。

## 4. 戻すとき

`aidlc/spaces/default/intents/260930-user-admin/operation/deployment-pipeline/rollback-runbook.md` のとおり、イメージだけを `mastersmith:pre-user-admin` へ戻す。戻している間は利用停止が効かない（既知の制約、F1: A）。今の停止中の利用者は 0 人。

## Sources

- `aidlc/spaces/default/intents/260930-user-admin/operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`
- `aidlc/spaces/default/intents/260930-user-admin/operation/environment-provisioning/environment-inventory.md`
- `aidlc/spaces/default/intents/260930-user-admin/construction/build-and-test/test-results.md`
- `aidlc/spaces/default/intents/260930-user-admin/operation/deployment-execution/deployment-execution-questions.md`

## Assumptions & Open Questions

None.
