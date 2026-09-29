# 配備の流れの構成（cd-config）

Intent 260929-log-deps-cleanup（ログのメールアドレス・依存の整理）の配備の流れです。

- **配備先**: これまでどおり、開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト名 `mastersmith`）。
- **配備の仕方**: 自動の配備は持たず、依頼者の承認のうえで手で配備する（`team.md`・`project.md` の Deployment）。
- **この文書の元**: この Intent には CI Pipeline（`ci-config`・`quality-gates`）と Infrastructure Design（`infrastructure-specification`・`cicd-pipeline`）の段が無い。そのため、前の Intent の配備の手順（`aidlc/spaces/default/intents/260928-quality-followup/operation/deployment-pipeline/`）と、既存の CI（`.github/workflows/ci.yml`）・`compose.yaml`・README の手順を正とし、今回の差だけを書く（`project.md` の Deployment）。
- **決定**: `deployment-pipeline-questions.md`（Q1・Q2 とまとめの確認）。

## 1. 配備する版と、今動いている版

| 項目 | 値 |
|---|---|
| 今動いている版 | イメージ `mastersmith:local`（`sha256:252bc44e…`、2026-09-29 21:33 作成）。前の Intent（260928-quality-followup）で配備した版 |
| 配備する版 | `develop` の先頭。アプリのソースは `8489241`（Build and Test で手元の `verify` と CI 36615809940 が通った版）。その後のコミットはワークフローの記録だけ |
| 見本の対象DB | `mastersmith-targetdb-postgres-1`（profile `targetdb-postgres`、digest `5a5a84b1…`）。今回は変えない |
| 手元の監視 | `mastersmith-lgtm-1`（profile `monitoring`）が動いている。コード生成の段で、500 ms にした警報の決まりとダッシュボードを読み込み直した。今回は触らない |
| 戻し先 | 配備の前に今のイメージに付けるタグ `mastersmith:pre-log-deps-cleanup` |

## 2. 今回の変更と、配備への影響

| 変更 | 配備への影響 |
|---|---|
| 初期管理者の作成のログ（キー `maskedEmail` の伏せ字） | イメージの中の WAR が変わる。起動のたびの「既にいるため」の INFO に、メールアドレスそのものが出なくなる |
| make-you-chic-ui の固定先 `077f5b4` | イメージの中の画面が変わる（選ばれたタブと primary のボタンの hover の文字の色） |
| 依存（spotless・spotbugs の Gradle プラグイン・`@types/node`） | ビルドの道具と型だけで、実行時の依存は変わらない |
| `dependabot.yml` | 配備に影響しない |
| 警報 `ms-check-p95` とダッシュボード（500 ms） | 動いている lgtm に読み込み済み。配備の手順は無い |
| `compose.yaml` の otel-collector 0.162.0 | profile `observability` で、今は動いていない。次に起動したときに効く |
| `application.yaml` | コメントだけの変更 |
| 内部DB のスキーマ・`.env` | 変わらない。バックアップは要らない（`project.md` の Deployment） |

## 3. 流れ

| 順 | 段 | すること | 行う人 | 承認 |
|---|---|---|---|---|
| 1 | 関門 | アプリのソースに未コミットの変更が無いこと（ワークフローの記録と監査ログは除く）と、`develop` が `origin/develop` と同じであることを確かめる。`verify`・E2E・CI は Build and Test の結果を正とし、流し直さない | AI | — |
| 2 | 戻し先のタグ | `docker tag mastersmith:local mastersmith:pre-log-deps-cleanup` | AI | — |
| 3 | 入れ替え | `docker compose --profile targetdb-postgres up -d --build app` でアプリだけを作り直す。healthy を待つ | AI | 依頼者の承認（アプリが止まるため） |
| 4 | 確かめ | スモークテスト（`deployment-strategy.md` の 3節） | AI と依頼者 | ★の操作は送る前に依頼者に伝える |
| 5 | `main` への取り込み | `main` を `develop` の先頭へ fast-forward する。タグは付けない（Q1: A） | AI | 依頼者の承認 |
| 6 | プッシュ | `origin` へ `develop` と `main` をプッシュする | 依頼者 | — |

## 4. 決まりとの差（記録）

| 決まり | 今回の扱い | 理由 |
|---|---|---|
| `team.md` の Deployment「リリース時に `main` にタグを付ける」 | `main` へは取り込むが、タグは付けない（Q1: A） | 前の Intent と同じ。`v*` のタグのプッシュで公開の GitHub のリリースが作られるため、配備先が決まったときに改めて決める |
| `team.md` の Way of Working「CI が失敗したら、次に進む前に原因を直す」 | 満たしている（CI の失敗は Build and Test でテストを直して解消し、CI 36613967625・36615809940 が成功） | — |

## 5. 成果物と版の識別

- 成果物は、フロントエンドの `dist` を同梱した実行可能 WAR から作るイメージ `mastersmith:local`（`team.md` の Deployment）。版はコミットのハッシュで識別し、deployment-execution の記録に残す。

## Sources

- `README.md`（コンテナでの起動と確認、戻し方）、`.github/workflows/ci.yml`、`compose.yaml`
- `construction/build-and-test/build-and-test-summary.md`・`test-results.md`（関門の結果）
- `construction/code-generation/code-summary.md`（変えたファイル）
- `operation/deployment-pipeline/deployment-pipeline-questions.md`（Q1・Q2・まとめの確認）
- `aidlc/spaces/default/intents/260928-quality-followup/operation/deployment-pipeline/`（前の Intent の配備の手順）
- `aidlc/spaces/default/memory/team.md`・`project.md`（Way of Working・Deployment）

## Assumptions & Open Questions

None.
