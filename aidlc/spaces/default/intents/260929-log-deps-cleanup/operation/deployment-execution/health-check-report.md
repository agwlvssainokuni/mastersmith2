# 健全性の確かめ（health-check-report）

## 1. 配備の直後

| 項目 | 結果 |
|---|---|
| `mastersmith-app-1` の健全性 | healthy（入れ替えの後、約16秒） |
| 監査の確かめのための停止の後 | healthy に戻った |
| `mastersmith-lgtm-1` | healthy（今回は触っていない） |
| `mastersmith-targetdb-postgres-1` | running（今回は触っていない） |

## 2. ログ

- ERROR 0 件。WARN 2 件は前の版から出ている既知のもの（`smoke-test-results.md` の S1）。
- 初期管理者の INFO にメールアドレスそのものが出ていないこと（FR1）を確かめた（S2・S3）。

## 3. 判定

配備は完了。中止の条件（`deployment-strategy.md` の 4節）に当たるものは無く、戻しは行っていない。

## Sources

- `aidlc/spaces/default/intents/260929-log-deps-cleanup/operation/deployment-pipeline/deployment-strategy.md`
- `aidlc/spaces/default/intents/260929-log-deps-cleanup/operation/deployment-pipeline/cd-config.md`
- `aidlc/spaces/default/intents/260929-log-deps-cleanup/construction/build-and-test/test-results.md`
