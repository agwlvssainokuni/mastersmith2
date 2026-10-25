# 費用と資源（cost-analysis）

配備先は開発者の PC 上のコンテナのため、費用（Cost Explorer・Trusted Advisor）を VM・コンテナの資源の上限と実測に読み替えます（`aidlc/spaces/default/memory/project.md` の Deployment の学び）。

## 1. 費用

クラウドの費用はありません（クラウドの基盤を作っていない）。PC の電力と、GitHub Actions の公開のリポジトリの実行の時間（検査の job は 1 回 約 22〜31 分、`operation/deployment-execution/deployment-log.md` 7.1）だけです。

## 2. 資源の上限と実測

| 対象 | 上限 | 実測 | 出どころ |
|---|---|---|---|
| colima の VM | CPU 4・メモリ 6GiB（中の全体 5,910 MiB）・ディスク 100GiB | VM の中のディスク 96G のうち 29G 使用 | この段の `colima list`・`colima ssh -- df -h /` |
| 配備したアプリ | メモリ 2GiB・CPU 4、最大ヒープ 50%（1,024 MB） | この段 536.4MiB。配備の直後は 642 MiB（前の版）、Observability Setup の間 705 MiB | この段の `docker stats`、`operation/environment-provisioning/environment-inventory.md` 2節、`operation/observability-setup/slo-config.md` 2節 |
| 見本の対象DB（PostgreSQL） | 512MiB | 26.5MiB | この段の `docker stats` |
| Mailpit | 256MiB | 8.5MiB | 同上 |
| 手元の監視（lgtm） | 1,536MiB | 止めている | — |
| 使い捨てのアプリ（負荷の試験） | メモリ 2GiB・CPU 4 | `memory.peak` 最大 969.7 MiB（`bizSearchWide`）、`bizCreateLarge` 最大 886.5 MiB | `operation/performance-validation/nfr-validation-matrix.md` 1節 |
| 使い捨ての対象DB（負荷の試験） | PostgreSQL 768m、MariaDB 1408m（決まりで上げた）、MySQL 2688m（F1: A） | `anon` の最大 MariaDB 1,197.0 MiB（85.01%）、MySQL 2,426.9 MiB（90.3%）。OOMKilled なし | 同上、`load-test-plan.md` 1.2 |

## 3. メモリの余裕（Performance Validation で分かったこと）

- **アプリ**: 業務データの重い場面でも `memory.peak` は上限の 47% 以下で、余裕があります。`dslMixed` は Build and Test で 0.873・0.870（線 0.9 まで約 55〜62 MiB）で余裕が小さく、`RETENTION_TIME=0` を入れるかは後の Intent で決めます（`construction/build-and-test/build-and-test-summary.md` 5節）。
- **対象DB**: 試験の値（README と基盤の設計の表）は、実際の表の大きさ（MySQL 1,017,806,848 B、MariaDB 747,274,240 B）より小さく、80% の決まりで上げて流しました。上げた後でも `anon` は 85% の線を超えました。読み取りのプールを 10 に上げる案は、対象DB のメモリの余裕をさらに減らしうるため、第2の束で合わせて確かめます。
- **監査の行**: 1件 約 108 B（3種類でそろった）。設計の見積もり 約 0.5 KB より小さく、依頼者1人の操作の量では内部DB の増え方は小さい見込みです（1万件で約 1 MB）。

## 4. 同時に動かせる組み合わせ

| 組み合わせ | 上限の合計 | 約 5,910 MiB に対する余裕 |
|---|---|---|
| 配備した環境（アプリ・見本の PG・Mailpit） | 2,816 MiB | 3,094 MiB |
| 上に lgtm を足す | 4,352 MiB | 1,558 MiB |
| 負荷の試験の MySQL の回（使い捨てのアプリ 2g・MySQL 2688m・見本の PG・Mailpit。配備したアプリは止める） | 5,504 MiB | 406 MiB |
| 負荷の試験の MariaDB の回（同じく MariaDB 1408m） | 4,224 MiB | 1,686 MiB |

業務データの負荷の試験の間は、配備したアプリを止める必要があります（今回は約 4 時間 27 分）。MySQL の回は余裕が小さく、lgtm は動かせません。

## 5. ディスクの使い方と片付け（Q3: A）

### 5.1 この段で行った片付け（依頼者の承認済み、2026-10-25 10:20 ごろ UTC）

| 片付けたもの | 中身 |
|---|---|
| `mastersmith` のタグ 8 個 | `perf-business-data-views`（`local` と同じイメージの別名。タグだけ外れた）・`business-data-views`・`business-data-views-b4`・`deps-ui-carryover`・`pre-deps-ui-carryover`・`role-menu-before-dslfix`・`role-menu-before-s8fix`・`pre-role-menu`。どれも使っているコンテナは 0 |
| ビルドのキャッシュ | `docker builder prune -af`（21 個・1.462GB） |
| 9月の `.env` の複写 | `~/.mastersmith-backup/env-202609251129-before-followup`（1,418 B、中身は開いていない） |

| 種類 | 片付けの前 | 片付けの後 |
|---|---|---|
| イメージ | 26 個・13.3GB（回収できる 7.82GB） | 19 個・12.31GB（回収できる 7.344GB）。`mastersmith` のタグは `local`・`pre-business-data-views` の2つ。名前の無いイメージ 0 個 |
| ビルドのキャッシュ | 21 個・1.462GB | 0 個・0B |
| ボリューム | 64 個・10.15GB（回収できる 9.262GB） | 同じ（残した） |
| PC の空き | 34GiB（92%） | 33GiB（93%）。VM のディスクは消しても PC 側で縮まない |

- 片付けの後も、配備したアプリは healthy・`/actuator/health` 200 のままでした。
- この段の決まり（コード・設定・環境を変えない）との差として、依頼者の決定 Q3: A で片付けました。

### 5.2 残る候補（依頼者の判断事項）

- 名前の無いボリューム（回収できる 9.262GB）。ほかのプロジェクトのものと見分けられないため残しました。
- 使っていないイメージ（古い `grafana/otel-lgtm`・`mysql`・`mariadb` などの `<none>`、回収できる 7.344GB）。対象DB のイメージは結合テストで使うため、版を上げた後に消すのが安全です。
- 手元の監視のコンテナ `mastersmith-lgtm-1`（イメージ 0.34.0 のまま、`drift-report.md` 1.4）とボリューム。
- PC のディスク 93%。VM のディスク（100GiB の上限）を縮めるには colima の作り直しが要ります。

## 6. 見直しの提案（依頼者の判断事項）

1. **負荷の試験の対象DB の試験の値**: README・基盤の設計の値を実際の表の大きさに合わせるか、種を小さくするか（第2の束、G1・B5）。
2. **業務データの負荷の試験の停止の時間**: 約 4 時間 27 分止めました。種の誤りと決定の待ちが無ければ 3〜4 時間です。種類ごとに配備したアプリを起こし直す形（前の Q1 の B）も、VM の余裕からは MariaDB・PostgreSQL の回では取りえます。
3. **対象DB のメモリの余裕**: MariaDB 85.01%・MySQL 90.3%。本番の対象DB の大きさは利用者の環境で決まるため、案内（README）に必要なメモリの目安を書くかを第2の束で決めます。

## Sources

- `aidlc/spaces/default/intents/261016-business-data-views/operation/feedback-optimization/feedback-optimization-questions.md`（Q3: A と片付けの一覧の承認）
- `operation/environment-provisioning/environment-inventory.md`（1節・2節・6節・7節）・`validation-report.md`
- `operation/performance-validation/nfr-validation-matrix.md`・`test-results.md`・`load-test-plan.md`（1.2）
- `operation/observability-setup/slo-config.md`（2節・3節）
- `operation/deployment-execution/deployment-log.md`（7.1）
- `construction/build-and-test/build-and-test-summary.md`（5節）
- この段のコマンドの出力: `colima list`・`colima ssh -- df -h /`・`docker stats --no-stream`・`docker system df`（前後）・`docker image ls`・`docker rmi mastersmith:<タグ>`・`docker builder prune -af`・`df -h ~`

## Assumptions & Open Questions

- 名前の無いボリュームの中身は確かめていません（ほかのプロジェクトのものを含む見込み）。
- PC の空きが片付けの後に 1GiB 減ったのは、ほかの操作（PC の上の作業）によるものと見られ、確かめていません。
