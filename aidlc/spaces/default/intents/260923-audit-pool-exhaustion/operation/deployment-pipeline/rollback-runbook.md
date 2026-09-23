# 戻し方の手順書（rollback-runbook）

今回の配備（コミット `d948544` 以降の版）で問題が出たときの戻し方。依頼者の決定（Q1: A、FQ1: B）により、**まず設定で戻し**、それで直らなければ**直前の版に戻す**。戻すかどうかは依頼者が決める。AI-DLC の作業の中で AI が戻す場合は、依頼者の承認を得てから行う。

## 1. 戻すと決める基準

前の Intent の `aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/rollback-runbook.md` 1節と同じ（healthy にならない、起動の失敗、スモークテストの失敗、配備の後の不具合）。今回の変更に固有のきっかけは、上限 30 に原因があると見られるメモリの不足、ヘルスチェックの DOWN、内部DBの接続の不具合である。

## 2. 設定で戻す（第一の手）

今回の変更は設定の値だけのため、版を替えずに、上限を元の値 10 に戻せる（FQ1: B）。

| 順 | 手順 | コマンドの例 |
|---|---|---|
| 1 | `.env` に1行加える | `MASTERSMITH_DB_MAXIMUM_POOL_SIZE=10` |
| 2 | アプリを作り直さずに再起動する | `docker compose up -d app`（`.env` の変更を読み直すため、`restart` ではなく `up -d` で作り直す） |
| 3 | 健全になるまで待つ | `docker compose ps` で `healthy` |
| 4 | スモークテストを行う | `deployment-strategy.md` 3節 |
| 5 | 記録する | 動いている版のハッシュは変わらず、設定だけが違うことを記録する |

- **この状態では F2（同時 10 件のログインで監査が欠ける）が元に戻る。** 原因を直したら、`.env` の行を消して `docker compose up -d app` で上限 30 に戻す。
- 監査の記録が欠けうる期間を記録に残す（いつからいつまで上限 10 だったか）。

## 3. 直前の版に戻す（設定で直らないとき）

前の Intent の `aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/rollback-runbook.md` 2節の手順（直前の版のコミットを `git worktree add` で取り出し、`./gradlew :backend:bootWar` で WAR を作り、`docker compose up -d --build` で起動する）で、直前の版 **`7040876`** に戻す。

- 2節で `.env` に加えた行は、直前の版では使われない（直前の版は上限 10 の固定値）。消しても残してもよいが、残す場合は、新しい版に戻すときに消す。
- スキーマは今回変わっていないため、戻すときのスキーマの扱いは要らない。

## 4. データも戻す・してはいけないこと

前の Intent の `aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/rollback-runbook.md` 4・5節のまま。ボリュームを消す操作（`docker compose down -v` など）はしない。

## 5. 戻し方の練習（今回は行わない）

依頼者の決定（Q3: B）により、今回の配備では戻しの練習を行わない。

| 残る未確認の前提 | 次に確かめる機会 |
|---|---|
| 3節の手順（直前の版を取り出して作り直す）が、実際に動くこと | スキーマの変更がある版を配備するとき |
| 直前の版が、新しい版が当てたスキーマの変更を含む DB で起動できること（前の Intent から残る） | 同上 |
| 2節の設定の戻しが動くこと | 同上。ただし、Build and Test の段で、環境変数で上限を 10 にしたときに設定が効くことはテストで確かめた（`construction/build-and-test/test-results.md` 2章のコマンド3） |

## Sources

- `deployment-pipeline-questions.md`（Q1: A、FQ1: B、Q3: B）
- 前の Intent の `aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/rollback-runbook.md`
- 前の Intent の Deployment Execution の記録（前回配備した版 `7040876`）
- `aidlc/spaces/default/intents/260923-audit-pool-exhaustion/construction/build-and-test/test-results.md`

## Assumptions & Open Questions

- 未確認の前提は5節の表のとおり。
