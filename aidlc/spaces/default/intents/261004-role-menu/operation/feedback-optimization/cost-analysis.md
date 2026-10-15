# 費用と資源（cost-analysis）

Intent `261004-role-menu` の費用と資源の振り返りです。パスは、断りが無ければ `aidlc/spaces/default/intents/261004-role-menu/` を省いたリポジトリのルートからの相対パスで書きます。時刻は JST です（括弧の中は UTC）。

## 1. 費用

- クラウドの費用はありません。配備先は開発者の PC 上のコンテナで、クラウドの基盤は配備先が決まるまで作りません（`team.md`・`project.md` の Deployment）。
- クラウドの費用の見積もりは、配備先が決まってから行います。
- Cost Explorer・Trusted Advisor は、VM・コンテナの資源の上限と実測に読み替えます（`project.md` の Deployment の学び）。

## 2. 資源の上限と実測

この段の値は 2026-10-15 11:39〜11:45（02:39〜02:45Z）に読み取りだけで確かめました。

| もの | 上限 | 実測 | 出典 |
|---|---|---|---|
| colima の VM | CPU 4・メモリ 6GiB・ディスク 100GiB | メモリ 全体 5,910MiB・使用 1,025MiB・使える量 4,884MiB（アプリ・Mailpit・見本の対象DB が動いているとき）。ディスク 96G のうち 25G 使用・72G 空き | `colima list`・`colima ssh -- free -m`・`df -h /` |
| 配備したアプリ | CPU 4・メモリ 2GiB（最大ヒープは 50% の 1,024MB） | 520.7MiB（25.43%）・CPU 0.30%（待機中、08:51 の起動から約 3 時間） | `docker stats --no-stream` |
| Mailpit（profile `mail`） | 256MiB | 41.67MiB | 同上 |
| 見本の PostgreSQL（profile `targetdb-postgres`） | 512MiB | 56.44MiB | 同上 |
| 手元の監視（lgtm、見たいときだけ） | 1.5GiB（1,536m） | 動いていない（Exited、約 10 時間前に止めた）。Observability Setup の確かめの間は 1.23GiB（上限の 82%）、Performance Validation の警報の確かめの回は最大 1.074GiB | `docker ps -a`、`operation/observability-setup/alarms.md` 6節、`operation/performance-validation/test-results.md` 3.4 |
| 使い捨ての試験の環境（`docker/perf/compose.yaml`） | アプリ 2g（Observability Setup は 1g、警報の確かめの回は上限 4 の接続プール） | 試験のあいだだけ。消した（コンテナ・ボリューム・網・一時ディレクトリが残っていないことを確かめた） | `test-results.md` 1節、`alarms.md` 6節 |
| 戻しの練習の環境（`-p mastersmith-rollback`） | 練習のアプリ 2g・練習の対象DB 512m | 練習のアプリは最大 544MiB。練習の後に消した | `operation/deployment-execution/deployment-log.md` 13節 |
| 内部DB（配備） | — | ボリューム 77.82kB | `docker system df -v` |
| PC のディスク（`/System/Volumes/Data`） | 460GiB | 片付けの前後とも空き 38GiB（92%、依頼者の承認の場の記録）。この段の書き込みの時点は空き 41GiB（91%） | `df -h ~` |

- 配備したアプリのメモリは、前の Intent の同じ段（427.3MiB、起動から約 17 分）より多い値です。起動からの時間と、この Intent で足した機能の差があり、比べる条件はそろっていません。
- PC の空きが 38GiB から 41GiB に増えた理由は確かめていません（片付けの後の読みでは変わらなかったため、片付けによるものではない見込みです）。

## 3. メモリの余裕（Performance Validation で分かったこと）

| 場面 | 値 | 見積もり・上限との比べ | 出典 |
|---|---|---|---|
| 10 MiB の DSL の投入と表示 | GC の後のヒープ 573〜746MB（8 回の GC）、GC の直前の最大 911MB。全体の GC の直前の最大 1,020MB、Full GC 0 回、`memory.peak` 1,514MB、OOMKilled なし | 見積もり（256〜384MB）を超えた。最大ヒープ 1,024MB にほぼ届いた。測り方の違い（前の生成・投入の残りを含むヒープ全体の値）も含む | `test-results.md` 2.1、`nfr-validation-matrix.md` 3節 |
| `dslMixed`（10 MiB の投入とプレビュー 1 人 ＋ ログイン 10 人・3 分） | `memory.peak` 1,869MB、OOMKilled なし、ログインの 500 は 0 | コンテナの上限 2g の 87%。余裕は小さい | `test-results.md` 2.2 |
| 権限の YAML の上限ちょうど（5 MiB、19 ロール × 1 万カラム） | ヒープの最大（読めた点）674MB、`memory.peak` 1,302MB、OOMKilled なし | 目標の時間は満たした | `test-results.md` 3.2 |
| 悪い形の 5 MiB の権限の YAML | ヒープが上限に届く | 受け入れた制約（1行の長さの上限は DSL と共通の部品の検討に回した） | `construction/build-and-test/build-and-test-summary.md` 5.2 |
| 単体テストの JVM | ヒープ 1,536m（navigation R-02） | 次に尽きたら、境界テストの読み込みを共有する直しを先に考える | `build-and-test-summary.md` 5.4 |

- 大きな入力の見積もりの直しは、`feedback-loop.md` の P2 に持ち越します。`project.md` には「大きな入力のヒープの見積もりは、設計の段で試しの測定で裏付ける」を学びとして残しました（2026-10-15）。

## 4. 同時に動かせる組み合わせ

| 組み合わせ | メモリの上限の合計 | 6GiB の VM（全体 5,910MiB）に |
|---|---|---|
| 配備したアプリ＋見本の対象DB | 2.5GiB | 収まる |
| ＋Mailpit（今の状態） | 2.75GiB | 収まる |
| ＋手元の監視 | 4.25GiB | 収まる |
| 今の状態＋戻しの練習の環境（練習のアプリ 2g・練習の対象DB 512m） | 5,376MiB | 収まる（余裕は約 0.2〜0.5GiB）。**手元の監視は起動しない**条件つき（`operation/environment-provisioning/environment-inventory.md` 9節）。この Intent の練習は配備したアプリを止めずに行えた |
| 今の状態＋使い捨ての試験の環境（アプリ 2g） | 約 4.75GiB（＋手元の監視で約 6.25GiB） | 手元の監視も動かすと上限を超えうる。大きな入力で上限まで使うため、試験のあいだは配備したアプリを止めた（Performance Validation で約 1 時間 44 分） |

## 5. ディスクの使い方と片付け（H1〜H5、Q3: B・F1: A）

### 5.1 この段で行った片付け（依頼者の承認済み）

2026-10-15 11:36:55〜11:36:57（02:36:55〜02:36:57Z）に行いました。この段の「環境を変えない」の決まりとの差は `drift-report.md` 3節に書きました。

- `docker image rm` で、`mastersmith` のタグ 16 個を外しました: `role-menu-b7`・`perf-dsl`・`safety-carryover`・`pre-safety-carryover`・`user-admin-followup`・`pre-user-admin-followup`・`pre-user-admin`・`pre-log-deps-cleanup`・`pre-quality-followup`・`pre-user-management`・`storage-memory-fix`・`storage-memory-base`・`pre-storage-memory`・`followup-fixes`・`pre-followup`・`pre-dsl`。
- `docker builder prune -f` で、ビルドのキャッシュ 102.4MB を回収しました。
- 残したタグは4つです: `mastersmith:local`（`e0a5825d257c`、配備中）・`role-menu-before-dslfix`（`3247c5bdab79`）・`role-menu-before-s8fix`（`e41a5b02fd0b`）・`pre-role-menu`（`fefeafbd299b`）。**前の Intent までの版へは、イメージでは戻せなくなりました**（戻すときは、その版のコミットから WAR とイメージを作り直す）。
- 名前の無いボリュームと手元の監視のボリュームは残しました（F1: A）。配備したアプリは healthy のままです。

| 種類 | 前 | 後 |
|---|---|---|
| イメージ | 33 個・10.32GB（回収できる 4.788GB） | 17 個・8.295GB（回収できる 3.487GB） |
| ビルドのキャッシュ | 401.7MB（回収できる 102.4MB） | 299.3MB（回収できる 0B） |
| ボリューム | 64 個・10.15GB（回収できる 9.262GB） | 変わらず |
| PC の空き（`df -h ~`） | 38GiB（92%） | 38GiB（92%） |

- PC の空きが変わらなかったのは、片付けが colima の VM のディスクの中で行われ、VM のディスクの像（PC の上のファイル）が縮まないためと見ています（**未検証**）。VM の中のディスクは、この段の読みで 25G 使用・72G 空きです。
- 回収できるイメージ 3.487GB は、使っているコンテナの無いイメージの量です。戻し先のタグ3つと、テスト・試験・確かめに使うイメージ（ダイジェスト固定の対象DB、`grafana/k6`、`eclipse-temurin` の JDK など）を含み、片付けの候補ではありません。`mastersmith` の4つのイメージは層を共有しており、表の大きさの合計ほどは場所を使っていません。

### 5.2 残る候補（依頼者の判断事項）

| ID | もの | 量（この段） | 候補と注意 |
|---|---|---|---|
| H1 | 名前の無いボリューム | ボリューム全体で回収できる 9.262GB。名前の無い（64 桁の ID の）ボリュームは 50 個で、どれも compose の project の印が無い。使われていないボリュームは 61 個（ほかのプロジェクト `devenv`・`mastermeister-devenv`・`sqlapp2` の名前付き 11 個を含む） | テストの対象DB の名残と見られるが、ほかのプロジェクトのものと見分けられない。`docker volume prune` は使わず、依頼者がほかのプロジェクトとあわせて判断する。個数の数え方の食い違いは `drift-report.md` 2節 |
| H2 | 古いタグのイメージ | この段で片付けた（5.1） | 残る4つのうち、`role-menu-before-s8fix`・`role-menu-before-dslfix` は、次の Intent の配備で新しい戻し先ができたときに外せる |
| H3 | 手元の監視のボリューム `mastersmith_mastersmith-monitoring` | 825.8MB（前の Intent の同じ段は 796.5MB） | Observability Setup と Performance Validation で使い捨ての環境から送った値が、配備したアプリと同じ `service_name="mastersmith"` で混ざっている。消すと過去の監視のデータも消える |
| H4 | ビルドのキャッシュ | 299.3MB（回収できる 0B） | 使っているもの。次の配備の `--build` でまた増える |
| H5 | PC のディスク | 空き 38〜41GiB（91〜92%） | 今すぐ配備や試験を止める量ではない。H1・H3 で約 10GB を VM の中で空けられるが、PC の空きに返るかは未検証（5.1） |

## 6. 見直しの提案（依頼者の判断事項）

| 提案 | 理由 | 扱いの案 |
|---|---|---|
| 大きな入力のヒープの見積もりを直し、メモリの上限と最大ヒープの割合を見直す（P2） | 10 MiB の DSL の投入で、GC の後でも見積もりの約 2 倍。`dslMixed` の `memory.peak` は上限 2g の 87% | `feedback-loop.md` の第1の束で、見積もりを試しの測定で直す。上限（2g）や最大ヒープの割合（50%）を変えるか、1行の長さの上限を置くかは、その結果で決める。目標は緩めない |
| 置き場の p95 の目標の見直し（P6） | 置き場の問い合わせの p95（35.1ms・31.1ms）は目標 1 秒の約 30 分の 1 | 緩めるのではなく、締める側の検討として第1の束で決める |
| 使い捨ての試験の環境の大きさ | 大きな入力の試験では、配備したアプリと同時に動かせず止めている（この Intent で約 1 時間 44 分） | 配備先が決まるまでは今の手順（止めて試験し、起動し直す）を続ける。VM を広げる案は、PC のメモリの余裕とあわせて依頼者が決める |
| 片付けの残り（H1・H3） | 5.2 のとおり | 依頼者の判断で行う。行うときは、消すものの一覧を示して承認を得てから行う |

## Sources

- `operation/feedback-optimization/feedback-optimization-questions.md`（Q3: B・F1: A、確認済みの要約）
- `operation/environment-provisioning/environment-inventory.md`（1節・6節・7節・9節）
- `operation/deployment-execution/deployment-log.md`（13節・14節）・`health-check-report.md`
- `operation/observability-setup/alarms.md`（6節）・`slo-config.md`
- `operation/performance-validation/test-results.md`（1節〜3節）・`nfr-validation-matrix.md`（3節）
- `construction/build-and-test/build-and-test-summary.md`（5.2・5.4）
- 前の Intent の同じ段: `aidlc/spaces/default/intents/260930-user-admin/operation/feedback-optimization/cost-analysis.md`
- この段の片付けの記録（2026-10-15 02:36:55〜02:36:57 UTC、依頼者の承認済み）: `docker image rm`（16 個のタグ）・`docker builder prune -f`、前後の `docker system df` と `df -h ~`
- この段で実行したコマンドの出力（2026-10-15 02:39〜02:45 UTC）: `colima list`・`colima ssh -- free -m`・`colima ssh -- df -h /`・`docker stats --no-stream`・`docker ps -a`・`docker images`・`docker image ls mastersmith`・`docker system df`（`-v`）・`docker volume ls`・`docker volume inspect`（compose の project の印だけ）・`df -h ~`

## Assumptions & Open Questions

- [assumption] 片付けで PC の空きが変わらなかったのは、colima の VM のディスクの像が縮まないためと見ている（未検証）。
- [assumption] この段の書き込みの時点で PC の空きが 41GiB に増えた理由は確かめていない。片付けの直後の読みでは 38GiB のままだったため、片付けによるものではないと見ている。
