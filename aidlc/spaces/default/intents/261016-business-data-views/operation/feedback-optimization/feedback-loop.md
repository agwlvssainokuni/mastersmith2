# 次への入力（feedback-loop）

Intent 261016-business-data-views の振り返りです。次の Ideation（次の Intent の範囲を決める場）への入力として、運用の結果と持ち越しをまとめます。束ね方と優先の順は Q1: A の案で、決めるのは依頼者です。

## 1. この Intent でできたこと

- 業務データの一覧・詳細・作成・更新・削除の画面と API（U1〜U3、Bolt B2・B4〜B7）を作り、配備しました（2026-10-20）。権限（主権限・補助権限）の当て方、主キーの無いテーブル・ビューのサーバー側の拒否、カラムの出し分け、書き込みの接続と資格情報の分離、業務データの書き込みの監査（V13）が入りました。
- 持ち越しの直し: アクセシビリティ（U4、axe-core 4.14.0）、不安定なテスト2件と `memory.peak` の内訳の台本（U5）、make-you-chic-ui の固定先の更新（`b0a1e79`、配備の後の Vite の直しで `ecb07e9`）。
- 監視: 業務データの区画（パネル 40〜45）と警報 `ms-biz-target-db` を足しました。
- 手順書: RB-36〜RB-47 と索引を足しました。
- 負荷の試験: 3種類の対象DB で目標 12 件を判定し、Met 8・Not Met 4 でした。
- 前の Intent の持ち越しのうち、F1（業務データの画面 J・K）を作り、K1（make-you-chic-ui の依頼の残り）を上流の取り込みで片付けました。

## 2. 次の Intent の候補と優先の順の案（Q1: A）

### 第1の束（優先 1）: 依存の更新と小さな直し

| ID | 中身 | 出どころ |
|---|---|---|
| A1 | Dependabot の知らせ 15 件（#54〜#68）を手元でまとめて更新し `./gradlew verify` を通す。イメージ（collector・mailpit・otel-lgtm）は版のタグと index のダイジェストで固定し、Dependabot が見ない固定先もそろえる | `construction/ci-pipeline/ci-config.md` 7節、この段の `gh pr list` |
| A2 | make-you-chic-ui の lockfile の `braces` 3.0.3（直る版が無く警告）。上流で直ったら固定先を上げる | `operation/deployment-execution/deployment-log.md` 7.1 |
| A3 | make-you-chic-ui への依頼（5）（Table の横スクロールの枠の名前、1ページの件数の選択）が公開されていれば取り込み、自前の `ScrollRegion`・`PageSizeSelect` を置き換える。無ければ除いて進める | `construction/build-and-test/build-and-test-summary.md` 5節 |
| A4 | 次の配備で、`main` の先頭（固定先 `ecb07e9`）から WAR を作り直して配備し、配備した版と `main` を揃える | この段の `drift-report.md` 2節の 1 |
| C1 | DSL の型の名前に符号なしを持たせる（G13。MySQL・MariaDB の範囲の外の整数が 400 でなく 409 になる） | `construction/u2-businessdata-write/code-generation/generation-notes.md` 2.5 |
| C2 | 文の時間切れの後に接続を捨てる（G15。MySQL の件数の問い合わせが止まると次の1回も 503 になる） | 同上 |
| C3 | 使わなくなった `GET /api/me/table-access` とそのテストを消す | `construction/u3-businessdata-screens/functional-design/functional-spec.md` 11節 |
| C4 | `docker/hikari-pool.sh compact` が補充の途中に流すと取りこぼす | `construction/code-generation/generation-notes.md` 4節 |
| C5 | パスワードの変更と表示の設定の画面の、切り替えの直後の成功の知らせ | 同上 |
| C6 | `K6_IMAGE` の digest と H2 の jar の取り方の2か所書き（U5 R-04） | `construction/u5-carryover-tests/code-generation/code-summary.md` 7節 |
| C7 | U1 のレビューの Minor の残り（R-02・R-05〜R-08） | `construction/code-generation/memory.md` |
| G2 | 監視の確かめ用の、待ちを入れた少ない要求の台本（負荷の試験の場面を流すと lgtm が止まった） | `operation/observability-setup/measurement-notes.md` 7節 |
| G4 | 内部DB の複写を数える道具で、列の名前を `INFORMATION_SCHEMA` で先に確かめ、誤りの文を捨てて件数だけ出す形にする | `deployment-log.md` 4節 |
| G9 | リリースの前に `osvScan --rerun`、`dist` に axe-core が入らないことの確かめを手順に入れる | `construction/ci-pipeline/quality-gates.md` 3節・4節 |
| — | `perf/README.md` (c) の `grep -o` が複数行の JSON で何も出さない（261015 の 5.3。今も残っている見込み） | `aidlc/spaces/default/intents/261015-deps-ui-carryover/construction/build-and-test/build-and-test-summary.md` 5.3 |

### 第2の束（優先 2）: MySQL・MariaDB の読み取りの性能の直し

| ID | 中身 | 出どころ |
|---|---|---|
| B1 | Not Met 4 件（U1-NFR1.1・1.3・3.2・2.4）。部分一致（10 VU で表の全部を件数とページの2回読む）と MariaDB の深い OFFSET。直し方の候補は、件数を「多数」と出す（ADR-007 の切り替え先）・読み取りのプールを 10 にする・索引の案内。画面の見え方に関わるため要件で決める。目標は緩めない | `operation/performance-validation/nfr-validation-matrix.md` 2節・4節、`test-results.md` 5節 |
| B2 | 対象DB の `anon` の最大 MariaDB 85.01%・MySQL 90.3%。読み取りのプールを 10 に上げる案はこの余裕を減らしうる（U2 の NFR 設計 F1 で、MySQL に同時に 10 本書くと OOMKilled になった） | `nfr-validation-matrix.md` 3節・4節 |
| B3・B4 | `dslMixed` の `memory.peak` の余裕（約 55〜62 MiB）と、`RETENTION_TIME=0` を配備物に入れるか | `construction/build-and-test/memory-peak-measurement.md` 3節 |
| B5・G1 | `perf/README.md` の 2B に `--default-character-set=utf8mb4` を足す。試験の値（README・基盤の設計 2.1）を実際の表の大きさに合わせる。流す順（書き込みの後の2回目の読み取りが表をプールから押し出す）を見直す | `load-test-plan.md` 1.2・1.3、`test-results.md` 3.3 |
| B6 | 画面の時間（U3-NFR1.7）を B7 の後に測り直す | `build-and-test-summary.md` の Assumptions |
| P5〜P7 | 前から: 任意の重なり（DSL の投入と権限の YAML の確かめ）、置き場の p95 の目標の見直し、単体テストの JVM のヒープ | `aidlc/spaces/default/intents/261004-role-menu/operation/feedback-optimization/feedback-loop.md` 2節 |

直した後は、直した G1 の手順で3種類の対象DB を流し直します。配備したアプリを止める時間（今回は約 4 時間 27 分）が要ります。

### 第3の束（優先 3）: 次の機能の Intent

| ID | 中身 | 出どころ |
|---|---|---|
| N1 | 他テーブル参照の選択肢（REFERENCE）と参照ピッカー（LOOKUP）。今は検索の CHOICE を EQUALS に、作成・更新を文字の入力に読み替えている | `inception/requirements-analysis/requirements.md`（FR3.6・FR6.2・範囲の外） |
| N2 | F2: `users.admin_flag` を縮める段と、管理の可否をロールで表す形（広げてから縮める二段の後の段。戻したときの権限のずれの確かめを入れる） | 同上（Q2）、`team.md` の Deployment |
| N3〜N6 | 書き出し・取り込み・まとめての更新と削除、楽観的な排他、読み取りの監査と前後の値の監査、日時のタイムゾーンの変換 | 同上（範囲の外） |

N1 を中心にし、N2〜N6 から入れるものを要件定義で決める案です。

### 配備先の決定を待つもの

| ID | 中身 | 出どころ |
|---|---|---|
| E1 | 配備したアプリの外部エクスポートと通知の先（今は配備したアプリではどの警報も鳴らない）。JVM が止まったときの起き直し（前の D1） | `operation/incident-response/incident-plan.md` 8節、`operation/observability-setup/alarms.md` の Assumptions |
| E2 | `ms-forbidden` の題・注記・しきい値（業務データの 403 も入り、監査に残らない。前の D2） | `alarms.md` 4節 |
| E3 | `ms-audit-fail`・`ms-audit-slow` が鳴ること（前の D4） | `alarms.md` 6節 |
| E4 | SLO の正式な値・計画した停止の数え方・バックアップの期間など（前の D8） | この段の `slo-report.md` 3節、`slo-config.md` |
| E5 | 平常の幅を取って閾値を見直す・異常の検出 | `operation/observability-setup/anomaly-config.md` 3節 |
| E6 | 本物の対象DB のバックアップの持ち主と、戻したときの監査との食い違い | `incident-plan.md` 8節 |
| E7 | 書き込みのアカウントの付与をテーブルごとに絞るか・資格情報を秘密の置き場へ移すか | 同上、`build-and-test-summary.md` の U2-NFR4.16 |
| E8 | 外部エクスポートの受け手（lgtm）の大きさ | `alarms.md` 5節 |
| E9 | blue/green などの配備の形 | `operation/deployment-pipeline/deployment-strategy.md` 1節 |
| D3・D5〜D7・D9 | 前から: 区画の p95 の警報、画面の誤りを集める仕組み、画面の時間の測り方、`ms-pool-pending` の式、招待の削除の失敗など | 前の Intent の `feedback-loop.md` |

### 受け入れ済みの危険（記録に残すだけ）

| ID | 中身 | 出どころ |
|---|---|---|
| F1〜F5 | G13・G15、主キーが NONE のテーブルでも主キーで並べる（U1 R-04）、1語の氏名のトリガーの incomplete と新しいタブで開くと一覧へ戻る（U3 R-04・R-07）、保存の後に画面を離れると知らせが捨てられる（U3 R-09） | `build-and-test-summary.md` 5節 |
| F6〜F9 | 一意の制約の無いカラムの同時の重なりと後勝ち、確定の後の監査の失敗、監査の行の増え方と大きな本文の同時の書き込み、件数とページの時点の差 | `requirements.md` の制約、`runbooks.md` の RB-39、U1・U2 の NFR 設計 |
| F10・F11 | 設計との差として受け入れたもの、配備の前の k6 を省いたこと、MySQL の 90.3% を合格の条件で Met としたこと | 各単位の `code-summary.md`、`cd-config.md` 3.3、`nfr-validation-matrix.md` 4節 |
| R1〜R3 | 前から（戻し先は `pre-business-data-views` に替わった） | 前の Intent の `feedback-loop.md` |

### 片付け（Q3: A の結果）

この段でタグ 8 個・ビルドのキャッシュ・9月の `.env` の複写を片付けました（`cost-analysis.md` 5.1）。残る候補は、名前の無いボリューム（9.262GB）・使っていないイメージ（7.344GB）・手元の監視のコンテナとボリューム・PC のディスク 93% です（`cost-analysis.md` 5.2）。

## 3. 運用から分かったこと（進め方の学び）

### 3.1 配備と取り込み

- 記録だけのコミットの CI でも、新しく公表された脆弱性（vendor の `vite@8.2.1`）で落ちました。`main` への取り込みの前の CI の確かめは、記録だけのコミットにも意味があります。
- vendor の固定先だけを上げると、配備した WAR と `main` の先頭が組む道具の版で食い違いました（A4）。固定先の更新が配備の後に入るときは、配備し直すかを取り込みの時点で決めます。

### 3.2 CI と不安定なテスト

- `logoutIntent.test.tsx` が二度目に落ちたため、決まりどおり段の途中で直しました（手順の入れ替えで原因を確かめた）。
- `9292432` の CI の1回目の失敗も、試行ごとの確かめで見つけて記録しました。

### 3.3 試験と測りの進め方

- 種を入れた後に、目印の語の当たりと最長の文字数を数えてから表の大きさを測ります（MySQL の文字の組の誤りで、誤った大きさから試験の値を決めかけた）。
- 書き込みの場面の後の読み取りは、表がプールから押し出された悪い側の状態になります。流す順と、作り直してから測るかを計画に書きます。
- 監視の式の確かめは、負荷の試験の場面ではなく、待ちを入れた少ない要求で送ります（lgtm がメモリの上限で止まった）。
- 業務データの 503 は `docker pause` でも UNAVAILABLE になり、TIMEOUT の経路は使い捨ての環境では起こせませんでした。TIMEOUT は結合テスト（ServerSocket の方式）を正とします。

### 3.4 記録

- 消す予定のもの（`.env` の複写）を消し忘れ、後の段で見つけました。手順書に「この段の終わりに消す」と書いたものは、段の承認の前に確かめます。

## 4. 手作業の繰り返しの自動化の案

| # | 案 | 理由 |
|---|---|---|
| A1 | 負荷の試験の種の確かめ（行の数・目印の語の当たり・最長の文字数・表の大きさとプールの割合）を1つの台本にし、80% の線と VM の余裕の確かめも含める | 今回、手で数えて誤った大きさを一度報告した |
| A2 | 内部DB の複写を数える道具を、列の名前を先に確かめる形でリポジトリの外に1つにまとめる（`count-db.sh`・`count-db2.sh` が2つある） | 列の名前の誤りでメールアドレスが一度出た |
| A3 | Dependabot の知らせのまとめての更新の手順（版の一覧・lockfile の更新・固定先のイメージのダイジェストの更新）を台本にする | Intent ごとに十数件たまり、毎回同じ手順を手で行う |
| A4 | `main` への取り込みの前の CI の試行ごとの確かめ（`gh run view --json attempt,jobs`）を台本にする | 毎回の取り込みで同じ確かめを手で行い、書き漏らしの学びが2回ある |

## 5. 道具の不具合とつまずき（AI-DLC の道具）

- 担当の安全の確かめが10回続けて返らず、一度止まりました（Performance Validation。PostgreSQL の2回目は背景で最後まで流れた）。
- 段の承認の後に、次の段の決まりの読み込みが、コミットの承認を待つ間にも続けるよう求められました（コミットの承認を先に尋ねて進めた）。

## 6. この段で見つかったこと（依頼者に確かめたい）

- 手元の監視のコンテナが古いイメージ（0.34.0）のままでした（`drift-report.md` 1.4）。次に起動するときに作り直します。
- 9月の `.env` の複写が残っていました（Q3: A で消した）。

## 7. 依頼者に頼むこと

1. この段の記録のコミットの承認と、`develop`・`main` の push（AI はプッシュしない）。
2. `main` への取り込み（Q4: A。`c099a2f` の後の CI を試行ごとに確かめてから、承認を得て fast-forward）。
3. 次の Intent の範囲の決定（2節の束と順の案）。

## Sources

- `aidlc/spaces/default/intents/261016-business-data-views/operation/feedback-optimization/feedback-optimization-questions.md`（Q1〜Q4: A、まとめの確認 Looks correct、片付けの一覧の承認）
- この段の `slo-report.md`・`cost-analysis.md`・`drift-report.md`
- `operation/performance-validation/nfr-validation-matrix.md`・`test-results.md`・`load-test-plan.md`
- `operation/observability-setup/dashboards.md`・`alarms.md`・`slo-config.md`・`measurement-notes.md`・`anomaly-config.md`
- `operation/incident-response/incident-plan.md`・`runbooks.md`
- `operation/deployment-execution/deployment-log.md`、`operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`
- `construction/build-and-test/build-and-test-summary.md`・`memory-peak-measurement.md`、`construction/ci-pipeline/ci-config.md`・`quality-gates.md`、各単位の `code-generation/code-summary.md`・`generation-notes.md`
- `inception/requirements-analysis/requirements.md`
- 前の Intent の記録: `aidlc/spaces/default/intents/261004-role-menu/operation/feedback-optimization/feedback-loop.md`、`aidlc/spaces/default/intents/261015-deps-ui-carryover/construction/build-and-test/build-and-test-summary.md`
- `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- 優先の順は案です。次の Ideation で依頼者が決めます。
- A3 の依頼（5）の上流の公開は、この段では確かめていません（次の Intent の始めに上流のソースで確かめる）。
- `perf/README.md` (c) の `grep -o` の形が今も残っているかは、読み取りの調べで見たもので、流して確かめてはいません。
