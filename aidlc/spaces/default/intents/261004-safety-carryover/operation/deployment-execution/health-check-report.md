# 健全性の確かめ（health-check-report）

Intent 261004-safety-carryover の配備の後の健全性です。配備の手順は `aidlc/spaces/default/intents/261004-safety-carryover/operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`、Build and Test の結果は `aidlc/spaces/default/intents/261004-safety-carryover/construction/build-and-test/test-results.md`。

## 1. コンテナ

| コンテナ | 状態（2026-10-04T04:42:39Z の後） | 備考 |
|---|---|---|
| `mastersmith-app-1` | healthy | イメージ `mastersmith:local`（`fefeafbd299b…`）。上限 CPU 4・メモリ 2g（既定） |
| `mastersmith-mailpit-1` | healthy | 触っていない |
| `mastersmith-targetdb-postgres-1` | 動作中 | 触っていない |

## 2. アプリ

| 項目 | 値 |
|---|---|
| `/actuator/health` | 200・`{"status":"UP"}` |
| 起動の時間 | 1回目 6.269 秒（救済を含む）。前の版の配備の記録は 5.462〜5.518 秒 |
| 起動のログ | ERROR 0。WARN は前からの案内2件と救済の1件（1回目）、前からの案内2件（2回目） |
| 内部DB の移行 | 無し（版 9） |
| 有効な管理者 | 2 人（救済の前は 1 人） |

## 3. バックアップ

| 時点 | ファイル（`~/.mastersmith-backup/`、権限 700） |
|---|---|
| 配備の前 | `mastersmith-data-202610041339-pre-safety-carryover.tgz` |
| 配備の後 | `mastersmith-data-202610041342-after-safety-carryover.tgz` |

## 4. 判定

健全。スモークテスト（`smoke-test-results.md`）もすべて合格。
