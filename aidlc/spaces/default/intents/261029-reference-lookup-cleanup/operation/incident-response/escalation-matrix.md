# 受け手と権限の表（escalation-matrix）

前の5つの表を正とし、この Intent の差だけを書きます（`incident-response-questions.md` 2節の書き方）。
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/escalation-matrix.md`（以下「元の表」）
- `aidlc/spaces/default/intents/260925-user-management/operation/incident-response/escalation-matrix.md`（以下「2つ目の表」）
- `aidlc/spaces/default/intents/260930-user-admin/operation/incident-response/escalation-matrix.md`（以下「3つ目の表」）
- `aidlc/spaces/default/intents/261004-role-menu/operation/incident-response/escalation-matrix.md`（以下「4つ目の表」）
- `aidlc/spaces/default/intents/261016-business-data-views/operation/incident-response/escalation-matrix.md`（以下「5つ目の表」）

## 1. 重さごとの受け手と期限

変えません（元の表の 1節）。依頼者1名と AI での運用です（`aidlc/spaces/default/memory/team.md` の Way of Working）。

| 重さ | 受け手・判断者 | 着手 | 戻すまでの期限 | その先の受け手 |
|---|---|---|---|---|
| 高 | 依頼者 | 気づいたらすぐ | 1日の内（RTO 1日） | 無い（1名での運用） |
| 低 | 依頼者 | 当日か翌日 | 置かない | 無い |

- 連絡の先は依頼者本人です。受け手が1名のため、呼び出しの順・交代の決まりは置きません。個人の連絡の情報は、公開のリポジトリに書きません（元の表の 1節）。
- AI は、依頼者に頼まれたときに調べと手順の実行を補助します。止める・戻す・データを戻す・`.env` を変える操作は、依頼者の承認を得てから行います。
- この Intent の高の例: 参照の2つの設定の誤りで業務データがすべて止まった（`runbooks.md` の RB-51）、記録の認証の値をコミット・プッシュした疑い（RB-55）、この Intent の版を戻す（RB-56）。
- make-you-chic-ui の部品が原因の失敗（RB-54）は、上流のリポジトリへの依頼が要ります。依頼の文面は依頼者の承認を得て `<record>/make-you-chic-ui-request-<n>.md` に置きます（`team.md` の Way of Working）。上流の持ち主は受け手ではなく、依頼の先です。

## 2. 承認なしで行ってよい調べの差（5つ目の表の 2節に足す）

どれも読み取りだけで、値を出さず、件数とキー・code の名前だけを見ます。

| 調べ | コマンドの例 | 気をつけること |
|---|---|---|
| 参照の読み取りの失敗の理由（RB-48・RB-50） | WARN「対象DB の参照先を読めませんでした」の `reason`・`budgetExceeded`・`sqlState` の値ごとの件数（`runbooks.md` の RB-48 の手順 1） | 出るのは理由・真偽・SQLState・例外のクラスの名前だけ。行の全体を表示しない |
| 保存前の確かめの失敗の段と理由（RB-49） | WARN「保存の前に対象DB の参照先を読めませんでした」の `stage`・`reason` の件数（RB-49 の手順 1） | 同上 |
| 参照の2つの設定の有無（RB-51） | 起動の WARN の `items` の項目名の件数、`grep -c` で `.env` の該当の行の数 | `.env` は開かず、項目の名前の行の数だけ |
| 業務の拒否の code の件数（RB-52） | WARN「要求をエラー応答に変換しました」の `"code":"BUSINESS_…"` の件数 | ほかの機能の `ACCESS_DENIED` なども数に入る |
| CI の試行ごとの結果（RB-54） | `gh run view <run> --json attempt,jobs`、失敗の job のログの `Errors`・`ReferenceError` の件数 | テストの名前とスタックの関数名までを記録に書く |
| 再発を防ぐものの有無（RB-54） | `frontend/vitest.setup.ts` の後片付けの有無、`git -C vendor/make-you-chic-ui merge-base --is-ancestor <cab4207> HEAD` | — |
| 記録の認証の値の件数（RB-55） | `node perf/redact-k6-evidence.mjs --scan <記録の置き場>/evidence`、`gitleaks dir --redact …` | 値を表示しない形（`--redact`）だけを使う |
| 手元の監視の版（`runbooks.md` 3節） | `docker inspect` のイメージとダイジェストを `compose.yaml` と比べる | — |

## 3. 依頼者の承認が要る操作の差（5つ目の表の 3節に足す）

| 操作 | 例 | 承認のときに示すこと |
|---|---|---|
| **参照の2つの設定の行を消す**（RB-51） | 【承認】`sed -i ''` で `.env` の該当の2行を値を表示せずに消し、`docker compose --profile targetdb-postgres up -d --force-recreate --no-deps app` | 既定（5 秒・10 秒）に戻るだけで失うものは無い。作り直しの間（数十秒）はアプリ全体が止まる。`.env` の複写は取らない（`rollback-runbook.md` 3節） |
| **参照の読み取りの上限を延ばす**（RB-50 の最後の手） | 【承認】`.env` に `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE` か `MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL` の行を足して作り直す | 先に原因（対象DB の状態・参照先の作り）を確かめたこと。延ばすと読み取りのプール（一覧・詳細と共有）を長く占め、最悪の待ちが長くなること。性能の目標は緩めないこと。戻すときは行を消すこと |
| DSL を REFERENCE から LOOKUP に替える（RB-53） | 【依頼者】DSL の画面で投入・適用 | DSL の投入・適用は監査に残る。適用し直すとロールの権限の対象の名前が変わることがある |
| 参照先のテーブルを読む権限を足す（RB-53） | 【依頼者】ロールの設定の画面 | `ROLE_PERMISSION_CHANGED` が監査に残る |
| 記録の履歴を直す（RB-55） | 【依頼者】amend・rebase と force-push、Dependabot の PR を閉じてブランチを消す | `origin` へのプッシュは依頼者。旧コミットを持つブランチが残る間は CI の `gitleaksScan` が落ちる |
| make-you-chic-ui への依頼と固定先の更新（RB-54） | 依頼の文面を置く、上流の直しを上流のソースで確かめてから固定先を上げる | `package-lock.json` の前後の差と `hasInstallScript` の増加の有無、前後のコミットハッシュを記録に書く（`team.md` の Way of Working） |
| 版を戻す（RB-56） | `operation/deployment-pipeline/rollback-runbook.md`（戻し先 `mastersmith:pre-reference-lookup-cleanup`） | イメージだけで戻る。戻すと保存前の参照の確かめが無くなる。設定の誤りだけなら先に RB-51 |

## 4. AI がしないことの差（5つ目の表の 4節に足す）

- 参照の値・ラベル、参照先のスキーマ・テーブル・カラムの名前を、コマンドの出力・記録・報告に出すこと（`project.md` の Forbidden）。
- 見本の PG に入り、参照先の行を読む・数えること（参照先に値があるか・200 行を超えたかの確かめを含む。確かめは【依頼者】が画面で行う）。
- 409 `BUSINESS_REFERENCE_NOT_FOUND` の「値が無い」と「読む権限が無い」を、サーバー側で見分ける仕組みや手順を足すこと（列挙の防止を崩す。U2 の BR7.2）。
- 参照の項目を、権限を欠く人のために文字入力へ切り替える手を勧めること（承認済みの行き止まり M1: A）。
- 参照の読み取りの上限を、原因を確かめずに延ばすこと・性能の目標を緩めること（RB-50。`project.md` の学び）。
- ログのスタックトレース（`exception_stacktrace`）を、手順の外（上流への依頼・課題の文面など）へ写すこと（対象DB のホスト名が入る。依頼者の決定 C）。
- k6 の結果を、伏せ字の道具を通さずに記録へ置くこと、`--scan`・`gitleaks dir` を飛ばしてコミットすること（RB-55）。
- `origin` へプッシュすること・履歴を書き換えること（依頼者が行う）。
- 画面のテストの時間の上限を、原因を確かめずに延ばすこと（`team.md` の Testing Posture）。

## 5. 知らせ（通知）

- 変えません（元の表の 5節）。通知の先は置かず、依頼者が Grafana の警報の一覧で見ます。警報は **17 件のまま**です（この Intent で足していない。`operation/observability-setup/alarms.md` 1節）。
- 参照の 503 は既存の `ms-biz-target-db` で拾います（使い捨ての環境で、止めてから 81 秒で firing）。409 `BUSINESS_REFERENCE_NOT_FOUND` は警報にしません。
- **配備したアプリは外部エクスポートが無効のため、今は配備したアプリの警報は鳴りません**。配備したアプリで気づくのは、画面の表示と定期の確認です（`runbooks.md` 3節）。
- CI の失敗の知らせは GitHub の画面とメールで依頼者に届きます（GitHub の設定。このアプリの仕組みではない）。

## Sources

- この段の質問 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/incident-response/incident-response-questions.md`（2節の決まっていること、Q1〜Q4: すべて A）
- 前の5つの表（冒頭の一覧）
- この Intent の Observability Setup: `operation/observability-setup/alarms.md`・`log-queries.md`
- この Intent の配備: `operation/deployment-pipeline/rollback-runbook.md`
- NFR 設計: `construction/u1-reference-read/nfr-design/reliability-design.md`・`security-design.md`、`construction/u2-reference-write-check/nfr-design/reliability-design.md`・`security-design.md`、`construction/u4-perf-evidence-redactor/nfr-design/security-design.md`
- 機能設計: `construction/u2-reference-write-check/functional-design/rules.md`（BR7.2）
- CI: `construction/ci-pipeline/flaky-repro-record.md`、`make-you-chic-ui-request-7.md`
- この段の `runbooks.md`・`incident-plan.md`
- `aidlc/spaces/default/memory/team.md`・`project.md`、`perf/README.md`（「記録（evidence/）に置く前の伏せ字」）

## Assumptions & Open Questions

None.
