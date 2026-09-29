# 配備の流れの構成（cd-config）

Intent 260928-quality-followup（品質の後始末）の配備の流れです。

- **配備先**: これまでどおり、開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト名 `mastersmith`）。
- **配備の仕方**: 自動の配備は持たず、依頼者の承認のうえで手で配備する（`team.md`・`project.md` の Deployment）。
- **この文書の元**: この Intent には CI Pipeline（`ci-config`・`quality-gates`）と Infrastructure Design（`infrastructure-specification`・`cicd-pipeline`）の段が無い。そのため、前の Intent の配備の手順（`aidlc/spaces/default/intents/260925-user-management/operation/deployment-pipeline/`）と、既存の CI（`.github/workflows/ci.yml`）・`compose.yaml`・README の手順を正とし、今回の差だけを書く（`project.md` の Deployment）。
- **決定**: `deployment-pipeline-questions.md`（Q1・Q2・F1 とまとめの確認）。

## 1. 配備する版と、今動いている版

| 項目 | 値 |
|---|---|
| 今動いている版 | イメージ `mastersmith:local`（`sha256:9e5243a3…`、2026-09-29 00:31 作成）。前の Intent（260925-user-management）で配備した版とみられる（実物の突き合わせは deployment-execution） |
| 配備する版 | `develop` の先頭。アプリのソースは `d1fda19`（Build and Test で手元の `verify`・E2E と CI が通った版）。その後のコミットはワークフローの記録だけ。統合は Build and Test で済んでいる |
| 見本の対象DB | `mastersmith-targetdb-postgres-1`（profile `targetdb-postgres`）。古い digest（`86c951e0…`）で動いている。新しい digest（`5a5a84b1…`）で作り直す（Q1: A） |
| Mailpit | `mastersmith-mailpit-1`（profile `mail`）。E2E のために起動したまま。配備の前に止めて消す（前の Intent の Q5: A と同じ扱い） |
| 戻し先 | 配備の前に今のイメージに付けるタグ `mastersmith:pre-quality-followup` |

## 2. 今回の変更と、配備への影響

| 変更 | 配備への影響 |
|---|---|
| make-you-chic-ui の固定先の更新とアプリ独自の CSS（コントラスト） | イメージの中の画面が変わる（green・orange の primary のボタンの文字が濃い色になるなど）。環境変数は増えない |
| `application.yaml` の p95 のバケット（`slo`） | 外部エクスポートは既定で無効のため、配備した環境では指標は外に出ない。手元の監視を起動したときに効く |
| 依存の更新（Gradle・npm・Jackson 3.1.6） | イメージの中の WAR が変わる。環境変数は増えない |
| `compose.yaml` の見本の対象DB（mysql・mariadb・postgres の digest）と `grafana/otel-lgtm` 0.34.0 | 動かしている `targetdb-postgres` だけを今回作り直す（Q1: A）。データはボリュームに残る。mysql・mariadb は profile で起動していないため影響なし。otel-lgtm は次に profile `monitoring` で起動したときに新しい版になる |
| ダッシュボードのファイル（メールの送信の p95 のパネル） | 次に手元の監視を起動したときに読まれる |
| 内部DB のスキーマ | 変わらない（Flyway の新しい版は無い）。バックアップは要らない（`project.md` の Deployment） |
| `.env` | 変えない |

## 3. 流れ

| 順 | 段 | すること | 行う人 | 承認 |
|---|---|---|---|---|
| 1 | 関門 | アプリのソースに未コミットの変更が無いこと（ワークフローの記録と監査ログは除く）と、`develop` が `origin/develop` と同じか先にあることを確かめる。`verify`・E2E・CI は Build and Test の結果を正とし、流し直さない | AI | — |
| 2 | Mailpit の片付け | `docker compose stop mailpit && docker compose rm -f mailpit`（E2E が送ったメールを消す） | AI | — |
| 3 | 戻し先のタグ | `docker tag mastersmith:local mastersmith:pre-quality-followup` | AI | — |
| 4 | 入れ替え | `docker compose --profile targetdb-postgres up -d --build` でアプリを作り直し、見本の対象DB を新しい digest で作り直す。healthy を待ち、起動のログに ERROR が無いことを見る | AI | 依頼者の承認（アプリが止まるため） |
| 5 | 確かめ | スモークテスト（`deployment-strategy.md` の 3節） | AI と依頼者 | ★の操作は送る前に依頼者に伝える |
| 6 | 片付け | 古い digest のイメージ（`postgres:18.6` の `86c951e0…`）は残してよい。戻し先のタグは次の Intent まで残す | AI | — |
| 7 | `main` への取り込み | `main` を `origin/main` に合わせて作り、`develop` の先頭へ fast-forward する。タグは付けない（F1: B） | AI（依頼者の承認） | 依頼者の承認 |
| 8 | プッシュ | `origin` へ `develop` と `main` をプッシュする | 依頼者 | — |

## 4. 決まりとの差（記録）

| 決まり | 今回の扱い | 理由 |
|---|---|---|
| `team.md` の Deployment「成果物の版はコミットのハッシュで識別し、リリース時に `main` にタグを付ける」 | `main` へは取り込むが、タグは付けない（Q2: A・F1: B） | `v*` のタグのプッシュで CI が公開の GitHub のリリースを作り WAR を添付するため、依頼者がタグと公開のリリースを配備先が決まったときに改めて決めると判断した |
| `team.md` の Way of Working「CI が失敗したら、次に進む前に原因を直す」 | 満たしている（前の Intent から続いていた CI の失敗は、この Intent の CI 36567275650 で解消） | — |

## 5. 成果物と版の識別

- 成果物は、フロントエンドの `dist` を同梱した実行可能 WAR から作るイメージ `mastersmith:local`（`team.md` の Deployment）。
- 版はコミットのハッシュで識別し、deployment-execution の記録に残す。
- CI の成果物（`mastersmith-<ハッシュ>` の WAR）は CI 36567275650 で作られている。配備には、手元で作り直すイメージ（同じソース）を使う。

## Sources

- `README.md`（コンテナでの起動と確認、戻し方）
- `.github/workflows/ci.yml`（`v*` のタグのプッシュでのリリース）、`compose.yaml`
- `construction/build-and-test/build-and-test-summary.md`・`test-results.md`（関門の結果）
- `construction/code-generation/code-summary.md`（変えたファイル）
- `operation/deployment-pipeline/deployment-pipeline-questions.md`（Q1・Q2・F1・まとめの確認）
- `aidlc/spaces/default/intents/260925-user-management/operation/deployment-pipeline/`（前の Intent の配備の手順）
- `aidlc/spaces/default/memory/team.md`・`project.md`（Way of Working・Deployment）

## Assumptions & Open Questions

- 今動いているイメージが前の Intent の配備の版かは、名前と作成日時だけで見立てた。deployment-execution で前の Intent の記録と突き合わせて確かめる。
