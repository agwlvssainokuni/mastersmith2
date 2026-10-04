# 配備の記録（deployment-log）

Intent 261004-safety-carryover の配備の記録です。手順は `aidlc/spaces/default/intents/261004-safety-carryover/operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`、戻しは `rollback-runbook.md`。Build and Test の結果は `aidlc/spaces/default/intents/261004-safety-carryover/construction/build-and-test/test-results.md`。質問と決定は `deployment-execution-questions.md`（Q1: C、Q2: A、F1: A、まとめの確認 Looks correct）。

## 1. 配備した版

| 項目 | 値 |
|---|---|
| 配備した版 | `develop` の `273c021`（アプリのソースは `532b39f`、コードは squash で統合した `b084c07` と同じ） |
| CI | run 37176658773（`532b39f`）success |
| 配備したイメージ | `mastersmith:local`（`sha256:fefeafbd299b…`） |
| 戻し先 | `mastersmith:pre-safety-carryover`（`sha256:f124296eb7ca…`、前の Intent で配備した版） |
| 内部DB の移行 | 無し（V9 のまま。`Migrating schema` 0 件） |
| `.env` | 変えていない |
| `main` | `47ec27b` → `273c021`（fast-forward、タグなし。Q3: A）。`origin` への push は依頼者が行う |

## 2. 時系列（UTC、2026-10-04）

| 時刻 | 出来事 |
|---|---|
| 03:42:59〜03:55:18、04:09:38〜04:13:45 | Build and Test の負荷の試験のためにアプリを止めた（Build and Test の記録） |
| 04:19:12 | `develop`（`532b39f`）の push の後の CI が始まり、success で終わった |
| 04:39:32 | 戻し先のタグを付け、アプリを止めた（`docker compose stop app`） |
| 04:39 頃 | 配備の前の複写 `~/.mastersmith-backup/mastersmith-data-202610041339-pre-safety-carryover.tgz` を取り、初期管理者の行を数えた: 1 行、停止中、管理者の印あり（停止していない管理者はほかに1人）。依頼者は救済が働くことを了承した（F1: A） |
| 04:41:00 | `docker compose --profile targetdb-postgres up -d --build app` で入れ替えを始めた |
| 04:41:11 | healthy（入れ替えの開始から約 11 秒。アプリが止まっていたのは 04:39:32〜04:41:11 の約 1 分 39 秒） |
| 04:41 頃 | 起動のログで救済の WARN 1 行（条件 `SUSPENDED`）を確かめた（S1・S2） |
| 04:42 頃 | 依頼者が初期管理者でログインし、ホームが開けた（S3） |
| 04:42:28〜04:42:39 | アプリを止めて配備の後の複写 `mastersmith-data-202610041342-after-safety-carryover.tgz` を取り、起動し直した（S4。約 11 秒） |
| 04:42 頃 | 2回目の起動では救済は働かず、INFO「初期管理者は既にいるため、作成しませんでした」 |
| 04:43 頃 | `main` を fast-forward |

## 3. 救済の結果（要件 FR8.2・FR8.3）

- 見込み: 救済が働く（停止中）。依頼者は画面でパスワードを変えていない（Q1: C）。
- 結果: 救済が働き、条件は `SUSPENDED` だけ（パスワードは `.env` と一致していた）。初期管理者の停止が解け、有効な管理者は 1 人から 2 人になった。リフレッシュトークンはすべて無効になった（依頼者のブラウザはログインし直し）。
- 見込みと結果は一致した（FR8.3 の「救済が働いたとき」の条件をすべて満たした。`smoke-test-results.md`）。

## 4. 前の手順・計画との差

| 計画 | 実際 | 理由 |
|---|---|---|
| 配備の前の E2E で救済の WARN を件数で確かめる（D5・D7、R-05） | E2E は 153 件 passed。WARN は数えられなかった | Playwright の設定で E2E のアプリの標準出力を捨てているため（`cd-config.md` の 4節） |
| 救済が働く見込みのとき了承を得てから入れ替える | そのとおり（F1: A） | — |

## 5. 戻し

行っていない。戻すときは `rollback-runbook.md`（イメージだけ。救済で変わった初期管理者の状態は戻らない。元に戻すには配備の前の複写を戻す）。

## Sources

- `aidlc/spaces/default/intents/261004-safety-carryover/operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`
- `aidlc/spaces/default/intents/261004-safety-carryover/construction/build-and-test/test-results.md`
- `deployment-execution-questions.md`、`smoke-test-results.md`、`health-check-report.md`
