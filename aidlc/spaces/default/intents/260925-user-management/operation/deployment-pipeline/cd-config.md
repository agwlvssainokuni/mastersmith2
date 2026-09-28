# 配備の流れの構成（cd-config）

Intent 260925-user-management（U1〜U8）の配備の流れです。

- **配備先**：これまでどおり、開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト名 `mastersmith`）です。
- **配備の仕方**：自動の配備は持たず、依頼者の承認のうえで手で配備します（team.md・project.md の Deployment）。
- **この文書の元**：8単位の Infrastructure Design（`construction/u*/infrastructure-design/cicd-pipeline.md`・`infrastructure-specification.md`）と CI Pipeline（`construction/ci-pipeline/ci-config.md`・`quality-gates.md`）を正とし、README の「コンテナでの起動と確認」「戻し方」「内部DBのバックアップと戻し方」の手順に沿って書きます。
- **決定**：`deployment-pipeline-questions.md` にあります。

## 1. 配備する版と、今動いている版

| 項目 | 値 |
|---|---|
| 今動いている版 | イメージ `mastersmith:local`（`sha256:305fddf4…`、2026-09-27 起動）。前の Intent（260925-storage-memory-fixes）で配備した版とみられる（実物の確かめは deployment-execution） |
| 見本の対象DB | `mastersmith-targetdb-postgres-1`（profile `targetdb-postgres`、今回は触らない） |
| Mailpit | `mastersmith-mailpit-1`（profile `mail`）。配備の前に止めて消す（Q5: A） |
| 配備する版 | 配備のときの `develop` の先頭。U1〜U8 は Construction で develop に統合済みのため、統合の手順は無い |
| 戻し先 | 配備の前に今のイメージに付けるタグ `mastersmith:pre-user-management` |

## 2. 今回の変更と、配備への影響

| 変更 | 配備への影響 |
|---|---|
| 内部DB のスキーマ V7（users に4列、audit_events に対象の2列）・V8（招待の表） | 起動のときに Flyway が当てる。**配備の前のバックアップが必須**（U2・U3 の `infrastructure-specification.md`）。スキーマは前進のみ・後方互換 |
| メールの送信（U1）と招待（U3） | `.env` に秘密でない4行を足す（`MASTERSMITH_WEB_BASE_URL=http://localhost:8080`・`SPRING_MAIL_HOST=mailpit`・`SPRING_MAIL_PORT=1025`・`MASTERSMITH_MAIL_FROM`）。変更の前に `.env` を複写する。`compose.yaml` の `app` の `environment` には足さない（`env_file` で読むため） |
| 招待の3項目（有効期限・保存の期間・定期の削除） | 既定のまま。`.env` に足さない |
| 見た目の設定（U8） | 既定（blue・sans）。`.env` の2項目は見た目を変えたいときだけ |
| 画面（U4〜U7）・プリファレンス（U2） | 環境変数は増えない |
| Dockerfile の `-Dh2.compactThreads=1` | イメージの中の既定。戻し先のイメージには無い（`rollback-runbook.md`） |
| 画面を開く URL | `http://localhost:8080`（ベース URL と Origin の確かめが共有のため。`127.0.0.1` では 403） |

## 3. 流れ

| 順 | 段 | すること | 行う人 | 承認 |
|---|---|---|---|---|
| 1 | 関門 | 未コミットの変更が無いこと（アプリのソース。ワークフローの記録と監査ログは除く）を確かめる。`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡して `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を流す。Mailpit を起動して `./gradlew e2eTest` を流す（Q1: A） | AI | — |
| 2 | Mailpit の片付け | `docker compose stop mailpit && docker compose rm -f mailpit`（E2E が送った有効なリンクを含むメールを消す。Q5: A） | AI | — |
| 3 | 戻し先のタグ | `docker tag mastersmith:local mastersmith:pre-user-management` | AI | — |
| 4 | バックアップ | アプリを止め、内部DB のボリュームを `~/.mastersmith-backup/`（権限 700）へ tar で複写する | AI | 依頼者の承認（アプリが止まるため） |
| 5 | `.env` | `.env` を中身を表示せずにホームの下（権限 700）へ複写し、秘密でない4行を値を表示せずに足す。足した後は項目の有無だけを数える | AI | 依頼者の承認 |
| 6 | 入れ替え | `docker compose --profile targetdb-postgres up -d --build` で新しい版を起動し、healthy を待つ。起動のログで V7・V8 が当たったこと、メールの設定の状態 `CONFIGURED`、招待の INFO（enabled）、見た目の設定の WARN が無いことを見る | AI | 依頼者の承認 |
| 7 | 確かめ | スモークテスト（`deployment-strategy.md` の 3 節） | AI と依頼者 | ★の操作は送る前に依頼者に伝える |
| 8 | 戻しの練習 | 配備の直後に続けて行う（`rollback-runbook.md` の 4 節、Q7: A） | AI と依頼者 | — |
| 9 | 片付け | Mailpit を止めて消す。練習の環境・`.env` の複写を消す（配備の前のバックアップと `.env` の複写は残す） | AI | — |
| 10 | プッシュ | `origin` へ `develop` をプッシュする | 依頼者 | — |

## 4. 決まりとの差（記録）

| 決まり | 今回の扱い | 理由 |
|---|---|---|
| team.md の Way of Working「CI が失敗したら、次に進む前に原因を直す」 | CI（`66fe981`）の失敗（2つのテストの時間切れ）は既知として記録し、手元の verify と E2E を配備の関門にする（Q1: A） | 依頼者の決定（Build and Test の 8.1 節）。直すのは次の Intent |
| project.md の Deployment「Build and Test で、配備と同じソースのイメージを配備と同じ上限で負荷の試験済みのときは、配備の前の k6 を省く」 | Build and Test では負荷をかけていないが、配備の前の k6 は流さず、配備の後の performance-validation に任せる（Q2: B） | 依頼者の決定。配備の後に問題が出たときは、`rollback-runbook.md` の第一の手で戻す |
| team.md の Deployment「リリースのときに main へ取り込み、main にタグを付ける」 | この Intent では行わない（Q8: A） | CI が失敗したまま。次の Intent で CI を直した後に検討する |

## 5. 成果物と版の識別

- 成果物は、フロントエンドの `dist` を同梱した実行可能 WAR から作るイメージ `mastersmith:local` です（team.md の Deployment）。
- 版はコミットのハッシュで識別し、deployment-execution の記録に残します。
- CI の成果物（`mastersmith-<ハッシュ>` の WAR）は、CI が失敗している間は作られません。配備には、手元の verify で作った WAR を使います。

## Sources

- `construction/ci-pipeline/ci-config.md`・`construction/ci-pipeline/quality-gates.md`
- `construction/u1-mail/infrastructure-design/cicd-pipeline.md`、`construction/u2-user-preferences/infrastructure-design/infrastructure-specification.md`・`cicd-pipeline.md`、`construction/u3-invitation/infrastructure-design/infrastructure-specification.md`・`cicd-pipeline.md`、`construction/u4-display-foundation/` 〜 `construction/u8-instance-appearance/` の `infrastructure-design/cicd-pipeline.md`・`infrastructure-specification.md`
- `construction/build-and-test/build-and-test-summary.md`・`test-results.md`
- `operation/deployment-pipeline/deployment-pipeline-questions.md`（Q1〜Q8・まとめの確認）
- `README.md`（コンテナでの起動と確認、戻し方、内部DBのバックアップと戻し方、メール（U1）、招待と登録の完了（U3））
- `aidlc/spaces/default/memory/team.md`・`project.md`（Way of Working・Deployment・Corrections）
- `aidlc/spaces/default/intents/260925-storage-memory-fixes/operation/deployment-pipeline/`（前の Intent の配備の手順）

## Assumptions & Open Questions

- 今動いているイメージが前の Intent の配備の版かは、名前と作成日時だけで見立てました。deployment-execution で、ハッシュを前の Intent の記録と突き合わせて確かめます。
