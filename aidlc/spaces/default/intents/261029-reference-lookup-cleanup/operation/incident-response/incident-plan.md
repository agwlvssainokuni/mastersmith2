# 障害の対応の計画（incident-plan）

前の5つの計画を正とし、この Intent の差だけを書きます（`incident-response-questions.md` 2節の書き方）。
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/incident-plan.md`（以下「元の計画」）
- `aidlc/spaces/default/intents/260925-user-management/operation/incident-response/incident-plan.md`（以下「2つ目の計画」）
- `aidlc/spaces/default/intents/260930-user-admin/operation/incident-response/incident-plan.md`（以下「3つ目の計画」）
- `aidlc/spaces/default/intents/261004-role-menu/operation/incident-response/incident-plan.md`（以下「4つ目の計画」）
- `aidlc/spaces/default/intents/261016-business-data-views/operation/incident-response/incident-plan.md`（以下「5つ目の計画」）

次の点は変えません（`incident-response-questions.md` 2節の「決まっていること」）。
- 重さの段階（高・低の2段）
- 見つけてから閉じるまでの流れ（見つける → 状態を見てログを残す → 手当て → 確かめ → 記録して閉じる → 高なら振り返る）
- 判断者・連絡・記録の仕方。受け手と判断者は依頼者1名で、AI は頼まれたときに調べと手順の実行を補助する（`escalation-matrix.md`）
- 復旧の目標（内部DB は RTO 1日・RPO は最後の手での複写まで。見本の PG は RPO の外で、初期化の台本で作り直す。本物の対象DB はその持ち主のバックアップに任せる。5つ目の計画の 4節）

障害のときに最初に開くのは `runbooks.md` の索引です（RB-01〜RB-56）。

## 1. 範囲の差

- 範囲に、この Intent で増えたものを加えます。
  - 他テーブル参照の読み取り（U1。定義の取得の `CREATE`・`UPDATE` の参照の情報、`POST /api/tables/reference/rows` のピッカーの検索とラベルの1件読み、対象DB の読み取りの目的 `REFERENCE`、設定 `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE`・`MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL`）
  - 保存前の参照の確かめ（U2。作成・更新の ⑩）
  - 参照の画面（U3）
  - k6 の結果の伏せ字の道具（U4。`perf/redact-k6-evidence.mjs`）
  - コンテナの版のそろえ（U5。Mailpit・otel-lgtm 0.38.0）と perf の文書
- 警報・ダッシュボードの決まりの差: 警報は変えていません（17 件のまま）。ダッシュボードにパネル 46・47（参照の API の要求の数と 503）を足しました（`operation/observability-setup/dashboards.md`）。
- 移行・`.env` の変更はありません。AWS の仕組み（SSM Automation・Incident Manager・AWS Backup）は、配備先が決まるまで作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。

## 2. 重さの段階の差（元の計画の 1節に足す）

参照の障害は、ログイン・管理の画面・一覧・詳細・監査には響かないため、多くは低です。

| 重さ | 足す例 |
|---|---|
| **高** | ・参照の2つの設定の誤りで業務データがすべて止まった（`runbooks.md` の RB-51。直すのは行を消すだけ）<br>・k6 の結果の記録に認証の値が入り、コミット・プッシュした疑い（RB-55）<br>・この Intent の版を戻す（RB-56）<br>・作成の必須の参照項目が選べず（RB-53）、作成ができないことで業務が止まる<br>・参照の読み取りが対象DB に届かず（RB-48）、作成・更新ができない時間が業務で困る長さになる |
| **低** | ・参照の読み取り・保存前の確かめの 503（RB-48・RB-49）、時間切れ（RB-50）<br>・409 `BUSINESS_REFERENCE_NOT_FOUND` の見分け（RB-52）<br>・選べない項目（RB-53）<br>・CI の画面のテストの「処理されない誤り」（RB-54。配備したアプリに影響しない） |

## 3. 見つけ方の差（元の計画の 2節の「1. 見つける」に足す）

- **配備したアプリは外部エクスポートが無効**のため、今は配備したアプリについて警報が鳴りません（`operation/observability-setup/alarms.md` 4節）。配備したアプリの参照の障害は、主に次で見つけます。
  - 依頼者が画面を使っていて、作成・更新の画面が開けない・ピッカーの「検索できません」・「選べない値です」・選べない項目の説明文に気づく
  - 定期の確認（`runbooks.md` 3節。前の日の参照の読み取りの失敗の WARN の件数、`budgetExceeded` の件数、作り直した後の設定の WARN）
- 手元の監視を有効にしているときは、`ms-biz-target-db` が参照の道・定義の取得・保存前の確かめの 503 でも鳴ります（使い捨ての環境で、止めてから 81 秒で firing。`alarms.md` 2節）。最初に見る手順は RB-48・RB-49。
- 警報で見つけられないもの: 409 `BUSINESS_REFERENCE_NOT_FOUND`（パネル 43 と code ごとの件数で見る）、選べない項目（画面だけ）、設定の誤り（起動の WARN）、CI の失敗（CI の画面）、記録の認証の値（`gitleaksScan`）。
- ログの問い合わせは `operation/observability-setup/log-queries.md` 2節の R1〜R5 を使います。`| json` を付けません。

## 4. 記録の残し方の差（元の計画の 3節に足す）

- 参照の障害の記録には、code・状態コード・項目の誤りの `reason`・WARN のロガーとキー（`reason`・`stage`・`budgetExceeded`・`sqlState`）の件数だけを書き、参照の値・ラベル・参照先のテーブルやカラムの名前を書きません（`project.md` の Forbidden）。
- ログを手順の外（上流への依頼・課題の文面など）へ写すときは、スタックトレースを写しません。HikariCP の WARN のスタックトレースには対象DB のホスト名が入ります（依頼者の決定 C で許容し、直さない。`log-queries.md` 3節）。
- 上限を延ばした（RB-50 の手順 4）ときは、原因・延ばした値・日時・戻すときの手を記録に書きます。

## 5. 事後の振り返りの差（元の計画の 4節に足す）

- RB-50 の手順 4（上限を延ばす）を使ったときは、延ばす前に分かった原因（対象DB の状態・参照先の作り）と、延ばした後に一覧・詳細の待ちが増えていないかを確かめます。
- RB-53 で DSL を REFERENCE から LOOKUP に替えたときは、参照先の行がいつ 200 を超えたか、利用者が気づくまでの時間を記録します（配備先が決まったときに、参照先の行の数を見張るかの判断に使う）。
- RB-55 を使ったときは、伏せ字の道具を通さなかった経路と、`--scan`・`gitleaks dir` を飛ばした理由を確かめます。

## 6. この Intent の事後の振り返り（重さ 低、Q2: A）

運用の段の決まり（`aidlc/spaces/default/memory/phases/operation.md`）は P1・P2 に振り返りを求めます。次の2つは CI だけで起きた失敗で、配備したアプリと利用者には影響していないため重さは**低**で、決まりの対象の外です。同じ形の失敗に次に早く気づくため、要点だけを書きます。詳細は記録を参照します。

### 6.1 画面のテストの「処理されない誤り」（Tooltip の時計）

| 項目 | 内容 |
|---|---|
| 経過（UTC） | 2026-11-01、CI の run 41264314230（`e1e028d1`）の1回目の `:frontendTest` と2回目の `:frontendCoverage` で、全件 passed のまま `Errors 1 error`（`ReferenceError: window is not defined`）で失敗。02:30〜02:59 に時間の上限 30 分で手元の再現を試み、再現しなかった。同じ誤りの二度目のため、`team.md` の決まりで直すまで進まないとした。直しの後、`cb23466b`（run 41270200479）の1回目で success（検査の job 03:41:58Z〜04:10:47Z） |
| 影響 | CI の失敗と、直すまでの統合の停止だけ。配備したアプリ・利用者・データへの影響は無い（外された部品への更新を React は無視する） |
| 原因 | make-you-chic-ui の Tooltip が、外されたときに表示の予約（300 ms の `window.setTimeout`）を取り消していなかった。行のリンクを押して画面を移るテストがファイルの最後にあると、jsdom の片付け（`window` が消える）の後に予約が走りうる。CI の実行機の遅さで、片付けと作業プロセスの終わりの間が延びたと見る（見立て。どの作業プロセスの終わり方で隙間ができるかは確かめていない） |
| 寄与した要因 | 上流の部品に外されたときの後片付けが無いこと。このリポジトリの画面のテストの共通の後片付けが、残った時計の予約を扱っていなかったこと。手元の負荷では隙間を作れなかったこと |
| うまくいったこと | 時間の上限を先に決めて再現を試みた。予約の順を確実に作る確かめ（D1: 片付けの後に `window` を外して 400 ms 待つ、D2: 片付けの後に鳴った予約を数える）で、直す前に落ち（3回とも 4 errors、D2 で 4）、直した後に通る（3回とも 0）ことを記録した |
| 直したこと | このリポジトリ: `frontend/vitest.setup.ts` で、各テストの `cleanup()` の後に残った時計の予約を取り消す（B4、`cac6fa54`）。上流: Tooltip に外されたときの後片付け（依頼 `make-you-chic-ui-request-7.md` → 上流 `cab4207`、固定先の更新 `cb23466b`。`package-lock.json` の差 0 行・`hasInstallScript` の増加なし） |
| 再発を防ぐもの | B4 を外さない。make-you-chic-ui の固定先を `cab4207` 以降に保つ。同じ形で落ちたときの見分け方は `runbooks.md` の RB-54 |
| 記録 | `construction/ci-pipeline/flaky-repro-record.md`（1節〜8節）、`make-you-chic-ui-request-7.md` |

### 6.2 結合テストの偶然の一致（`ReferenceSameResponseIT`）

| 項目 | 内容 |
|---|---|
| 経過 | 2026-10-31、CI の run 41161783903（`a75baec4`）の1回目で `ReferenceSameResponseIT` の判定の順のテストが失敗し、2回目で success（03:40:13Z〜04:16:45Z） |
| 原因 | 応答の本文に目印 "abc" が無いことを確かめる形で、乱数の16進のトレースID に偶然に当たった（1回の実行で約 0.73%）。本番の応答は値を返していない |
| 直したこと | `a3099f80`（テストだけの変更。目印を16進に無い文字を含む値に替えた）。直す前は CI と同じ本文の確かめが落ち、直した後は通ることを確かめた。乱数のトレースID 1,000,000 件で、新しい目印の当たりは 0 件 |
| 再発を防ぐもの | 目印の文字列で「応答に値が無い」ことを確かめるテストでは、16進の字に無い文字を含む目印にする（`runbooks.md` の RB-54 の注） |
| 記録 | `construction/ci-pipeline/ci-config.md` 6節、`construction/build-and-test/test-results.md` 8節 |

## 7. 配備先が決まったときに見直すこと（5つ目の計画の 8節に足す）

- 配備したアプリの外部エクスポートと通知の先を置き、`ms-biz-target-db` が参照の 503 でも配備したアプリで鳴るようにする（今は鳴らない）。
- REFERENCE の参照先の行が 200 に近づいたことを見張るか（今は画面の説明文で気づく。RB-53）。
- 部分一致の検索の目標 3,000 ms を見分けるバケットを足すか（`operation/observability-setup/anomaly-config.md` 3節）。
- HikariCP の WARN のスタックトレースに対象DB のホスト名が出ることの扱い（今は決定 C で許容。配備先のログの置き場と見る人が変わるときに見直す）。

## Sources

- この段の質問 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/incident-response/incident-response-questions.md`（1節・2節、Q1〜Q4: すべて A）
- 前の5つの計画（冒頭の一覧）
- この Intent の Observability Setup: `operation/observability-setup/alarms.md`・`log-queries.md`・`dashboards.md`・`anomaly-config.md`
- この Intent の配備: `operation/deployment-pipeline/rollback-runbook.md`、`operation/deployment-execution/deployment-log.md`
- NFR 設計: `construction/u1-reference-read/nfr-design/reliability-design.md`・`security-design.md`、`construction/u2-reference-write-check/nfr-design/reliability-design.md`・`security-design.md`
- 基盤の設計: `construction/u1-reference-read/infrastructure-design/infrastructure-specification.md`、`construction/u2-reference-write-check/infrastructure-design/infrastructure-specification.md`
- CI: `construction/ci-pipeline/flaky-repro-record.md`・`ci-config.md`、`construction/build-and-test/test-results.md`、`make-you-chic-ui-request-7.md`
- この段の `runbooks.md`・`escalation-matrix.md`
- 決まり: `aidlc/spaces/default/memory/team.md`・`project.md`・`phases/operation.md`

## Assumptions & Open Questions

- [assumption] 6.1 の原因のうち、CI で隙間ができた仕組み（作業プロセスの終わり方）は見立てで、確かめていません。直したことは D1・D2 と CI で確かめています。
- 新しい手順は使い捨ての環境で試していません（Q1: A。`runbooks.md` 4節）。
