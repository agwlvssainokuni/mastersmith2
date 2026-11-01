# 費用と資源（cost-analysis）

配備先は開発者の PC 上のコンテナのため、費用（Cost Explorer・Trusted Advisor）を VM・コンテナの資源の上限と実測に読み替えます（`aidlc/spaces/default/memory/project.md` の Deployment の学び）。

## 1. 費用

クラウドの費用はありません（クラウドの基盤を作っていない）。PC の電力と、GitHub Actions の公開のリポジトリの実行の時間（検査の job は 1 回 約 31〜38 分。`5458e3eb` 38分4秒・`be6e61c5` 36分55秒、`operation/deployment-execution/deployment-log.md` 2節）だけです。

## 2. 資源の上限と実測

| 対象 | 上限 | 実測 | 出どころ |
|---|---|---|---|
| colima の VM | CPU 4・メモリ 6GiB（中の全体 5,911 MiB）・ディスク 100GiB | この段: メモリ使用 1,034 MiB・使える量 4,877 MiB。ディスク 96G のうち 30G 使用（Environment Provisioning の 40G から減った。`drift-report.md` の X1） | この段の `colima list`・`colima ssh -- free -m`・`df -h /`、`operation/environment-provisioning/environment-inventory.md` 1節 |
| 配備したアプリ | メモリ 2GiB・CPU 4、最大ヒープ 50% | この段 546MiB（CPU 0.82%）。配備の前の版は 505 MiB | この段の `docker stats`、`environment-inventory.md` 2節 |
| 見本の対象DB（PostgreSQL） | 512MiB | 26.1MiB | この段の `docker stats` |
| Mailpit | 256MiB | 7.7MiB | 同上 |
| 手元の監視（lgtm） | 1,536MiB | 止めている | — |
| 使い捨てのアプリ（負荷の試験） | メモリ 2GiB・CPU 4 | `memory.peak` 最大 983,625,728 B（45.8%、MariaDB の `bizSearchWide`）。`bizCreateLarge` 最大 44.5%（MySQL） | `operation/performance-validation/test-results.md` 5節 |
| 使い捨ての対象DB（負荷の試験） | PostgreSQL 768m、MySQL 2688m、MariaDB 1408m | `anon` の最大 PG 107.8 MiB（14.0%）、MySQL 2,110.6 MiB（78.5%）、MariaDB 1,225.5 MiB（87.0%）。OOMKilled なし | 同 5節 |

## 3. メモリの余裕（Performance Validation で分かったこと）

- **アプリ**: 参照と業務データの重い場面でも `memory.peak` は上限の 46% 以下で、余裕があります。
- **対象DB**: 表の大きさの合計はプールの 75.8〜79.5% で、80% の決まりの内でした（設定は変えていない）。MariaDB の `anon` の最大 87.0% は線 92% の内ですが、前の Intent の 85.01% より高く、余裕が縮んでいます（`feedback-loop.md` の P2）。MySQL は前の Intent の 90.3% から 78.5% に下がりました（回の間に種を入れ直した流し方の差を含む）。
- **プール**: 読み取りの借りる待ちの最大は `refPickerContains` で 1,094〜1,413 ms（MySQL・MariaDB）。上限 3 秒の内ですが、余裕は大きくありません（`feedback-loop.md` の P3）。

## 4. 同時に動かせる組み合わせ

| 組み合わせ | 上限の合計 | 約 5,911 MiB に対する余裕 |
|---|---|---|
| 配備した環境（アプリ・見本の PG・Mailpit） | 2,816 MiB | 3,095 MiB |
| 上に lgtm を足す | 4,352 MiB | 1,559 MiB |
| 負荷の試験の MySQL の回（使い捨てのアプリ 2g・MySQL 2688m・見本の PG・Mailpit。配備したアプリは止める） | 5,504 MiB | 407 MiB |
| 負荷の試験の MariaDB の回（同じく MariaDB 1408m） | 4,224 MiB | 1,687 MiB |

負荷の試験の間は、配備したアプリを止める必要があります（今回は約 3 時間 17 分。前の Intent の 4 時間 27 分より短い）。

## 5. ディスクの使い方と片付け（Q2: A）

### 5.1 この段で行った片付け（依頼者の承認済み、2026-11-01 12:25 ごろ UTC、指揮役が実行）

| 片付けたもの | 中身 |
|---|---|
| `mastersmith` のタグ 5 個 | `perf-reference-lookup-cleanup`（`b672c6765fad`）・`pre-perf-targets-review`（`3075065a2653`）・`carryover`（`009a757de120`）・`pre-deps-perf-carryover`（`4cec01e79026`）・`pre-business-data-views`（`f103aee1702a`）。どれも使うコンテナが 0 であることを確かめてから消した |
| ビルドのキャッシュ | `docker builder prune`（Total 514.5MB） |

| 種類 | 片付けの前 | 片付けの後 |
|---|---|---|
| イメージ | 24 個・10.66GB（回収できる 5.419GB） | 19 個・10.05GB（回収できる 5.019GB）。`mastersmith` のタグは `local`（`d8a467b60f6f`）・`pre-reference-lookup-cleanup`（`e0ede6ddd5c4`）の2つ |
| ビルドのキャッシュ | 14 個・1.028GB（回収できる 310.5MB） | 9 個・513MB（回収できる 0B） |
| ボリューム | 74 個・13.39GB（回収できる 12.56GB） | 同じ（残した） |
| PC の空き | 54GiB（88%） | 55GiB（88%）。VM のディスクは消しても PC 側で縮まない |

- 片付けの後も、配備したアプリはイメージ `d8a467b6…` のまま healthy でした。
- イメージの減りが 0.61GB と小さいのは、消したタグの多くが `local` と層を共有していたためです。
- この段の決まり（コード・設定・環境を変えない）との差として、依頼者の決定 Q2: A で片付けました。

### 5.2 残る候補（依頼者の判断事項）

- 名前の無いボリューム（60 個。回収できる 12.56GB の多く）。ほかのプロジェクトのものと見分けられないため残しました。
- 固定先から外れた古い版のイメージ（`mysql` `ade067ae2fb1` 1.32GB・`eclipse-temurin:25.0.4_7` の jdk と jre・`eclipse-temurin` `589ff4cc3f71`・`otel/opentelemetry-collector:0.162.0`、約 2.8GB）。ほかのプロジェクトが使う見込みを否定できないため残しました（Q2 の B を選ばなかった）。
- PC のディスク 88%。VM のディスク（100GiB の上限）を縮めるには colima の作り直しが要ります。

## 6. 見直しの提案（依頼者の判断事項）

1. **MariaDB の対象DB のメモリの余裕**: `anon` の最大 87.0%。本番の対象DB の大きさは利用者の環境で決まるため、README に必要なメモリの目安を書くかを後の Intent で決めます（P2）。
2. **負荷の試験の停止の時間**: 約 3 時間 17 分。種の確かめと流す順が整ったため、前の Intent より約 1 時間 10 分短くなりました。

## Sources

- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/feedback-optimization/feedback-optimization-questions.md`（Q2: A と片付けの承認）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/environment-provisioning/environment-inventory.md`（1節・2節・6節）・`validation-report.md`（N3）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/performance-validation/test-results.md`（1節・2節・4節・5節）・`nfr-validation-matrix.md`・`load-test-plan.md`
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/observability-setup/slo-config.md`（2節・3節）・`dashboards.md`・`alarms.md`
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/deployment-execution/deployment-log.md`（2節）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/incident-response/incident-plan.md`（7節）
- 前の Intent の記録: `aidlc/spaces/default/intents/261016-business-data-views/operation/feedback-optimization/cost-analysis.md`
- この段のコマンドの出力: `colima list`・`colima ssh -- free -m`・`colima ssh -- df -h /`・`docker stats --no-stream`・`docker system df`（前後）・`docker images`・`docker rmi mastersmith:<タグ>`・`docker builder prune`・`df -h ~`

## Assumptions & Open Questions

- 名前の無いボリュームの中身は確かめていません（ほかのプロジェクトのものを含む見込みのため、Q2: A のとおり残しました）。
- PC の空きの前後の差（54GiB → 55GiB）は、片付けのほかの PC の上の作業も含みうるため、片付けの効果としては数えていません。
