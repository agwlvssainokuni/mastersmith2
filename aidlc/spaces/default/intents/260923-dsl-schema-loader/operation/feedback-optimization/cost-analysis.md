# 費用と資源の見直し（cost-analysis）

決定は `feedback-optimization-questions.md`（Q2: A）。配備先が決まるまでクラウドの基盤は作らない（project.md の Deployment）。

## 1. 費用

- **クラウドの費用は無い**。配備先は開発者の PC 上のコンテナで、AWS Cost Explorer・Trusted Advisor・費用の予算の警報の対象になるものは無い。
- 外部のサービスで費用がかかるものは無い（GitHub Actions は公開のリポジトリで使っている。team.md の Way of Working・Deployment）。
- クラウドの費用の見積もりは、配備先が決まってから行う。

## 2. 資源（colima の VM: CPU 4・メモリ 6GiB・ディスク 96G）

### 2.1 上限と実測

| もの | 上限 | 実測 | 出典 |
|---|---|---|---|
| VM のメモリ | 5,910MiB（OS の分を除く） | 使用 876MiB・利用できる 5,033MiB（配備したアプリと見本の DB だけが動いている時点） | `colima ssh -- free -m`（2026-09-25T07:09） |
| 配備したアプリ | 2g・CPU 4（`.env`。compose の既定は 1g、`drift-report.md` D1） | 待機中 408MiB・CPU 0.4%。10MB の DSL とログインの重ねでプロセスのメモリの最大は上限の 93%（使い捨ての環境） | `docker stats`、`operation/performance-validation/test-results.md` 3節 |
| 見本の対象DB（PostgreSQL） | 512MiB | 32MiB | `docker stats` |
| 手元の監視（見たいときだけ） | 1536m | Grafana が 900m では止まったため引き上げた | `operation/observability-setup/dashboards.md` 3節 |
| 使い捨ての試験の環境 | アプリ 2g（または 1g）＋対象DB 1つ 最大 768MB＋k6（上限なし） | — | `perf/README.md` |
| `./gradlew verify` の中のコンテナ | — | VM のメモリの最大 約 1,424MiB | `construction/build-and-test/build-and-test-summary.md`（TP-TDB-PLACE） |
| VM のディスク | 96G | 使用 33G・空き 64G。使われていないイメージ 約 10.8GB・ボリューム 約 9.1GB・ビルドのキャッシュ 約 1.0GB | `colima ssh -- df -h /`・`docker system df` |
| 内部DB（H2） | 目安 300MB で起動し直す（README） | 44KB。投入を重ねると動いている間は増え続け、止めると詰め直される（2.4GB → 9.5MB を使い捨ての環境で確かめた） | `drift-report.md`、`performance-validation/test-results.md` 4節 |

### 2.2 同時に動かせる組み合わせ（上限の合計の見積もり）

| 組み合わせ | 上限の合計 | VM（5,910MiB）に対して | 扱い |
|---|---|---|---|
| 配備したアプリ＋見本の対象DB（ふだん） | 約 2.5GB | 余裕あり | 今の運用 |
| ＋手元の監視 | 約 4.0GB | 余裕あり | 見たいときだけ起動（今のまま） |
| ＋`./gradlew verify` | 約 3.9GB（監視なし） | 余裕あり | 統合前の検査は配備したアプリを止めずに流せる |
| ＋使い捨ての試験の環境（2g） | 約 5.3GB＋k6 | 上限に近い | 負荷の試験の間は配備したアプリを止める（`perf/README.md` の手順 0。Performance Validation でもそうした） |
| 手元の監視＋試験の環境を同時 | 6GB を超える | 収まらない | 同時に動かさない（README の「手元の監視」） |

## 3. 見直しの提案（依頼者の判断事項）

| # | 提案 | 効果 | 手間・危険 | 急ぎ |
|---|---|---|---|---|
| C1 | VM の大きさは今のまま（CPU 4・6GiB） | ふだんの運用・監視・統合前の検査には足りる | 負荷の試験の間は配備したアプリを止める運用が続く | 変えなくてよい |
| C2 | 使われていない Docker のイメージ・ボリューム・ビルドのキャッシュ（合わせて 約 21GB）を消す（`docker image prune`・`docker volume prune` など） | VM のディスクの空きが 64G から約 85G に増える | 消したものは戻せない。どれが要るかを依頼者が確かめてから行う。今は空きが十分 | 急がない |
| C3 | compose のアプリのメモリの上限の既定を 2g にするか、10MB の DSL を扱うには 2g が要ることを README に明記する | `.env` に項目が無い PC でも 10MB の DSL で止まらない | コードの変更（compose・README）。VM が小さい PC では 2g が収まらない | 後の Intent（`feedback-loop.md`） |
| C4 | 内部DB のファイルの伸び（U4-STORAGE-RUN）とメモリの余裕（2g で 93%）を減らす作りの見直し | 起動し直しの運用の手間と、止まる危険が減る | 設計の変更を含む | 後の Intent（`feedback-loop.md`） |
| C5 | 負荷の試験など長い作業は、PC を電源につなぎ `caffeinate -i` で流す | PC のスリープで結果が崩れるのを防ぐ（Performance Validation で 414 秒のスリープがあった） | 無し（project.md に学びとして保存済み） | 今から |

## Sources

- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/feedback-optimization/feedback-optimization-questions.md`（Q2: A、確認済みの要約）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/feedback-optimization/drift-report.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/performance-validation/test-results.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/observability-setup/dashboards.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/deployment-execution/deployment-log.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/build-and-test/build-and-test-summary.md`
- `compose.yaml`、`README.md`（「コンテナの資源の上限」「手元の監視」「DSL の管理」）、`perf/README.md`
- 実行したコマンド: `colima list`・`colima ssh -- df -h /`・`colima ssh -- free -m`・`docker stats --no-stream`・`docker system df`

## Assumptions & Open Questions

- [assumption] C2〜C4 を行うかと時期は、依頼者が決める。
