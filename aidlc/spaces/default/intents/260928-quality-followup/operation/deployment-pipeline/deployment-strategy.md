# 配備の戦略（deployment-strategy）

Intent 260928-quality-followup の配備の仕方と、配備の後の確かめです。流れの全体は `cd-config.md`、戻しは `rollback-runbook.md` にあります。前の Intent の戦略（`aidlc/spaces/default/intents/260925-user-management/operation/deployment-pipeline/deployment-strategy.md`）を正とし、今回の差を書きます。

## 1. 方式

| 項目 | 値 |
|---|---|
| 方式 | 1台の置き換え（Recreate）。配備先が開発者の PC 上のコンテナ1つのため |
| 止まる時間 | 新しい版の起動の間（スキーマの変更は無い） |
| 機能の切り替え（feature flag） | 使わない |
| 環境の昇格 | 無い（検証環境・本番環境は配備先が決まってから） |
| 承認 | 依頼者 |

## 2. 配備の前の関門

| 確かめ | 通る条件 |
|---|---|
| 未コミットの変更 | アプリのソースに無い（ワークフローの記録と監査ログのディレクトリは除く） |
| `verify`（対象DB を含む）・`e2eTest` | Build and Test の結果を正とする（BUILD SUCCESSFUL、SKIPPED 0、E2E 110 件通過。`construction/build-and-test/test-results.md` の 1・2節）。配備するソースは同じ（`d1fda19`）なので流し直さない |
| CI | `d1fda19` の CI 36567275650 が success |
| 配備の前の k6 | 流さない。応答の処理を変えておらず、負荷の目標も無い |

## 3. 配備の後の確かめ（スモークテスト）

健全性（`/actuator/health` が UP）と、下のすべてが通るまで、配備の完了としません（`team.md` の Deployment）。パスワードの要る操作は依頼者が行い、AI はアプリのログと監査の記録で裏付けます。個人に関する値は表示しません。★の付いた操作は、配備した内部DB と監査に行が残ります。送る前に依頼者に伝えます。

| # | 確かめ | 行う人 | 通る条件 |
|---|---|---|---|
| S1 | 起動のログ | AI | healthy になる。ERROR が無い。見た目の設定の WARN が無い（ロガーと件数だけを見る） |
| S2 | 見本の対象DB | AI | `targetdb-postgres` が新しい digest（`5a5a84b1…`）のイメージで動いている |
| S3 | ログイン画面と画面の版 | AI | `http://localhost:8080/` でログイン画面が出る。配信される CSS に make-you-chic-ui の文字用の色（`--color-primary-text` など）が含まれる |
| S4 | ★ログインと管理 | 依頼者（AI は監査で裏付け） | 初期管理者でログインし、ホーム・「管理」が開ける。`LOGIN_SUCCEEDED` が記録される |
| S5 | ★見た目（コントラスト） | 依頼者 | 表示の設定でブランドカラーを green か orange にして、primary のボタンの文字が濃い色になることを目で確かめ、元に戻す（プリファレンスの保存は監査に残らない。内部DB の設定の値だけが変わり、元に戻す） |
| S6 | ★DSL（見本の対象DB の読み取り） | 依頼者 | 「DSL」の画面で今の状態が表示される。見本の対象DB のスキーマを読み込み、既定の DSL をプレビューに置ける（`DSL_GENERATED` が監査に残る。`AuditEventType` で確かめた）。生成はプレビューを置き換えるため、送る前にプレビューの有無を確かめ、確かめた後はプレビューを破棄する（`DSL_PREVIEW_DISCARDED` が残る）。適用はしない |
| S7 | ★ログアウト | 依頼者 | ログイン画面に戻る。`LOGGED_OUT` が記録される |
| S8 | ログの漏れ | AI | `docker compose logs app` の中の `@example.com` と `/register#token=` の件数が 0（件数だけを見る） |

## 4. 中止の条件

次のどれかが起きたら配備を中止し、`rollback-runbook.md` に従って戻します。

| 条件 | 戻し方 |
|---|---|
| 起動が healthy にならない | 戻し先のイメージで起動し直す |
| スモークテストのどれかが通らず、その場で原因が設定だけと分からない | 同上 |
| ERROR のログが出続ける、ログに秘密・メールアドレスが出る | 同上 |
| 見本の対象DB が新しい digest で起動しない、または DSL の生成に失敗する | 見本の対象DB を古い digest で起動し直す（`rollback-runbook.md` の 3節） |

## 5. 監視

- 手元の監視（Grafana、profile `monitoring`）はこの配備では起動しない。次に起動したときに、`grafana/otel-lgtm` 0.34.0 と、直したダッシュボード（メールの送信の p95 のパネル）が効く。p95 の警報3件が値を持ち鳴ることは Build and Test で確かめた（`construction/build-and-test/test-results.md` の 6節）。
- 配備の直後は、`docker compose logs app` と監査の記録（README の「監査ログの確かめ方」）で確かめる。

## Sources

- `cd-config.md`・`deployment-pipeline-questions.md`
- `construction/build-and-test/test-results.md`
- `README.md`（コンテナでの起動と確認、監査ログの確かめ方）
- `aidlc/spaces/default/intents/260925-user-management/operation/deployment-pipeline/deployment-strategy.md`
- `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

None.
