# 費用と資源（cost-analysis）

## 1. 費用

- クラウドの費用はありません。配備先は開発者の PC 上のコンテナで、クラウドの基盤は配備先が決まるまで作りません（`team.md`・`project.md` の Deployment）。
- クラウドの費用の見積もりは、配備先が決まってから行います。
- Cost Explorer・Trusted Advisor は、VM・コンテナの資源の上限と実測に読み替えます（`project.md` の Deployment の学び）。

## 2. 資源の上限と実測（2026-10-03 23:49〜23:55 JST、読み取りだけ）

| もの | 上限 | 実測 | 出典 |
|---|---|---|---|
| colima の VM | CPU 4・メモリ 6GiB・ディスク 100GiB | メモリ 使用 950MiB・使える 4,959MiB（アプリ・Mailpit・見本の DB が動いているとき）。ディスク 24G 使用・73G 空き | `colima list`・`colima ssh -- free -m`・`df -h /` |
| 配備したアプリ | CPU 4・メモリ 2GiB（最大ヒープは 50%） | 427.3MiB（20.86%）・CPU 0.33%（待機中、起動から約 17 分） | `docker stats --no-stream` |
| Mailpit（profile `mail`） | 256MiB | 14.14MiB | 同上 |
| 見本の PostgreSQL（profile `targetdb-postgres`） | 512MiB | 26.52MiB | 同上 |
| 手元の監視（lgtm、見たいときだけ） | 1.5GiB（1536m） | 動いていない（Performance Validation の後に止めた） | `docker ps -a` |
| 使い捨ての試験の環境 | アプリ 2g・Mailpit 256m | 試験のあいだだけ（約 25 分）。消した。試験の後の内部DB のファイルは 77MB | `operation/performance-validation/test-results.md` 1節 |
| 内部DB（配備） | — | ボリューム 69.63kB | `docker system df -v` |
| PC のディスク | 460GiB | 374GiB 使用・空き 46GiB（90%）。Environment Provisioning の時点は空き 30GiB（93%） | `df -h /System/Volumes/Data`、`operation/environment-provisioning/environment-inventory.md` 1節 |

- 配備したアプリのメモリは、前の Intent の同じ段（520MiB）より少ない値です。起動からの時間が短いためと見ています（比べる条件はそろっていません）。

## 3. 同時に動かせる組み合わせ

| 組み合わせ | メモリの上限の合計 | 6GiB の VM に |
|---|---|---|
| 配備したアプリ＋見本の DB | 2.5GiB | 収まる |
| ＋Mailpit（今の状態） | 2.75GiB | 収まる |
| ＋手元の監視 | 4.25GiB | 収まる |
| ＋使い捨ての試験の環境（アプリ 2g・Mailpit 256m） | 約 6.5GiB | 上限を超えうる。試験のあいだは配備したアプリを止める（今の手順どおり。この Intent でも約 24 分半止めた） |

## 4. ディスクの使い方と片付けの候補（H1〜H4、Q3: A）

この段では片付けていません（Q3: A）。量と候補を並べ、行うかは依頼者が決めます。

| ID | もの | 量（この段） | 候補と注意 |
|---|---|---|---|
| H1 | 名前の無いボリューム | 61 個（全 64 個のうち）。回収できる 9.262GB | 使い捨ての環境とテストの名残に、ほかのプロジェクト（`devenv_*`・`mastermeister-devenv_*`・`sqlapp2_*`）のものが混ざる。`docker volume prune` は使わず、`mastersmith` のものを選んで消す |
| H2 | 古いタグの `mastersmith` のイメージ | タグは 11 個（下の注）。イメージ全体で回収できる 1.954GB | 残すもの: `local`（配備中）・`pre-user-admin`（今の戻し先）。前の戻し先 `pre-log-deps-cleanup` は残すか依頼者が決める。消す候補: `pre-quality-followup`・`pre-user-management`・`pre-storage-memory`・`pre-followup`・`pre-dsl`・`storage-memory-fix`・`storage-memory-base`・`followup-fixes` の 8 個 |
| H3 | 手元の監視のボリューム `mastersmith_mastersmith-monitoring` | 796.5MB（Environment Provisioning の時点は 744.5MB） | Observability Setup と Performance Validation で使い捨ての環境から送った値が、配備したアプリの値と同じ名前で混ざっている。消すと過去の監視のデータも消える |
| H4 | PC のディスク | 空き 46GiB（90%） | 今すぐ配備や試験を止める量ではない。H1〜H3 で約 10GB を空けられる |

- 注（H2 の数え方）: 質問の記録では「古いタグのイメージ 10 個」と書きました。これは `local` を除いたタグの数（`pre-user-admin` を含む）です。今の戻し先を除くと 9 個、前の戻し先も残すと 8 個です。
- イメージの数が Environment Provisioning の記録と合わない点は、`drift-report.md` の 2節に書きました。

## 5. 見直しの提案（依頼者の判断事項）

| 提案 | 理由 | 扱いの案 |
|---|---|---|
| ログインの余裕と CPU の割り当てを見直す（P1） | ログインの p95 は 939.6 ms で、1 秒の目標まで 60 ms（前の Intent は 904 ms・余裕 96 ms）。停止の判定を足した影響か、ぶれかは切り分けていない。CPU の上限を下げる・同時の数が 10 を超えると、届かなくなりうる（`nfr-validation-matrix.md` 4節） | `feedback-loop.md` の第2の束で切り分ける。配備先を決めるときに、CPU の割り当てと同時の数の想定をあわせて見直す。目標は緩めない |
| 片付け（H1〜H3） | 4節のとおり | 依頼者の判断で行う。行うときは、消すものの一覧を示して承認を得てから行う |
| 使い捨ての試験の環境の大きさ | 配備したアプリと同時に動かせず、試験のたびにアプリを止めている | 配備先が決まるまでは今の手順（止めて試験し、起動し直す）を続ける。VM を広げる案は、PC のメモリの余裕と合わせて依頼者が決める |

## Sources

- `operation/feedback-optimization/feedback-optimization-questions.md`（Q3: A、確認済みの要約）
- `operation/environment-provisioning/environment-inventory.md`（1節・5節・6節）・`validation-report.md`
- `operation/performance-validation/test-results.md`（1節）・`nfr-validation-matrix.md`（4節）
- `operation/observability-setup/slo-config.md`（基準の値）
- 前の Intent の同じ段: `aidlc/spaces/default/intents/260925-user-management/operation/feedback-optimization/cost-analysis.md`
- この段で実行したコマンドの出力（`colima list`・`colima ssh -- free -m`・`colima ssh -- df -h /`・`docker stats --no-stream`・`docker ps -a`・`docker images`・`docker system df`（`-v`）・`docker volume ls`・`df -h`）

## Assumptions & Open Questions

None.
