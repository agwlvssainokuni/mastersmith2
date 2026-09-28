# 費用と資源（cost-analysis）

## 1. 費用（Q2: A）

- クラウドの費用はありません。配備先は開発者の PC 上のコンテナで、クラウドの基盤は配備先が決まるまで作りません（project.md の Deployment）。
- クラウドの費用の見積もりは、配備先が決まってから行います。
- Cost Explorer・Trusted Advisor は、VM・コンテナの資源の上限と実測に読み替えます（project.md の Deployment の学び）。

## 2. 資源の上限と実測（2026-09-29）

| もの | 上限 | 実測 | 出典 |
|---|---|---|---|
| colima の VM | CPU 4・メモリ 6GiB・ディスク 100GiB | メモリ 使用 1,018MiB・空き 4,892MiB（アプリと見本の DB だけのとき）、ディスク 34G 使用・63G 空き | `colima list`・`colima ssh -- free -m`・`df -h /`（02:22） |
| 配備したアプリ | CPU 4・メモリ 2g（最大ヒープは 50%） | 520MiB・CPU 0.3%（待機中、起動から 7 分）。Performance Validation の使い捨てのアプリは、負荷の後に約 1,013MiB | `docker stats`、`test-results.md` |
| 見本の PostgreSQL | 512MiB | 26MiB | `docker stats` |
| 手元の監視（lgtm、見たいときだけ） | 1536m | 動かしていない。ボリューム 701MB（Observability Setup で使い捨ての環境から送ったデータを含む） | `docker system df -v` |
| Mailpit（見たいときだけ） | 256m | 動かしていない | `docker compose ps` |
| 使い捨ての試験の環境 | アプリ 2g・Mailpit 256m | 試験のあいだだけ。消した | `test-results.md` |
| 内部DB（配備） | — | 64KB | `du -sh /data`（読み取りだけ） |
| 内部DB（使い捨て、試験の後） | — | 117MB（招待 35,368 件・監査 約 61 万件） | `test-results.md` |

## 3. 同時に動かせる組み合わせ

| 組み合わせ | メモリの上限の合計 | 6GiB の VM に |
|---|---|---|
| 配備したアプリ＋見本の DB | 2.5GB | 収まる（ふだん） |
| ＋Mailpit（招待のとき） | 2.75GB | 収まる |
| ＋手元の監視 | 4.25GB | 収まる |
| ＋使い捨ての試験の環境（アプリ 2g・Mailpit） | 6.5GB 前後 | 上限を超えうる。試験のあいだは配備したアプリを止める（今の手順どおり） |

## 4. 見直しの提案（依頼者の判断事項）

| 提案 | 理由 | 扱いの案 |
|---|---|---|
| bcrypt を使う API の余裕を見直す | ログイン・今のパスワードの誤り・登録の完了の p95 が 1 秒の目標まで 69〜96 ms。CPU の上限（4）を下げる・同時の数が増えると届かなくなりうる | 配備先を決めるときに、CPU の割り当てと同時の数の想定をあわせて見直す。目標は緩めない |
| 手元の監視のボリュームを片付ける | 701MB。Observability Setup の確かめのデータ（使い捨ての環境から送った値）が、配備したアプリの値と同じ名前で混ざっている | 次に監視を見るときに、依頼者の判断で `docker volume rm mastersmith_mastersmith-monitoring`（README の「手元の監視」） |
| 古いイメージのタグを整理する | 前の Intent の試験・戻し先のタグ（`storage-memory-*`・`followup-fixes`・`pre-followup`・`pre-dsl` など）が残る | 戻し先 `pre-user-management` と `local` 以外を、依頼者の判断で消す |

- この段では、どれも変えていません（読み取りだけ）。

## Sources

- `operation/feedback-optimization/feedback-optimization-questions.md`（Q2: A）
- `operation/environment-provisioning/environment-inventory.md`
- `operation/performance-validation/test-results.md`・`nfr-validation-matrix.md`
- `README.md`（「コンテナの資源の上限」「手元の監視（Grafana）」）
- 前の Intent の同じ段: `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/feedback-optimization/cost-analysis.md`
- この段で実行したコマンドの出力（`colima list`・`colima ssh`・`docker stats`・`docker system df -v`・`docker compose ps`）

## Assumptions & Open Questions

None.
