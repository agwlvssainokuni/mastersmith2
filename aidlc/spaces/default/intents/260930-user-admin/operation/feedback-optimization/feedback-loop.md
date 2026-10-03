# 次への入力（feedback-loop）

Intent `260930-user-admin`（利用者の管理の画面）の振り返りと、次の Ideation への入力です。

- 持ち越しは3つの束にまとめました（Q1: A）。
- 優先の順は案です。決めるのは依頼者です。
- 各項目の ID（K・T・S・P・D・H）は、この段の質問の記録（`feedback-optimization-questions.md`）の一覧と同じです。

## 1. この Intent でできたこと

- 管理者が、利用者の一覧を検索・ページ送りで見られるようになりました（1,000 名で p95 6.1 ms 以下）。
- 管理者が、利用者の氏名と言語を変えられるようになりました。
- 管理者が、次の5つの操作をできるようになりました。どれも監査に残ります。
  - 管理者の印を付ける・外す
  - 利用を止める・停止を解く
  - 失敗回数を戻す（ロック中なら解除を兼ねる）
- 停止中の利用者は、ログインの照合・トークンの更新・アクセストークンの認証の3つの入口のすべてで拒まれます（V9 の `users.suspended`）。
- 最後の有効な管理者を無くす操作は拒まれます。同時の操作でも 0 人になりません。
- 管理の画面の 403 を、画面の骨組みでまとめて扱うようにしました。招待の一覧のページ送りを共通の部品に移しました。
- 次の段まで済みました。
  - 配備（V9、`cc28d1f`）とスモークテスト
  - 監視（`dashboards.md`・`alarms.md`）
  - 障害の手順（`operation/incident-response/runbooks.md` の RB-21〜RB-27、`incident-plan.md`）
  - 負荷の試験（`operation/performance-validation/`、引き継いだ 16 件のうち Met 13 件・Unverified 3 件）

## 2. 次の Intent の候補と優先の順の案（Q1: A）

### 第1の束（優先 1）: 画面とテストの直し

**問題**: 利用者の管理の画面に、キーボードの利用者と画面の右端で困る不具合が残っています。また、性能の見積もりと警報の一部を確かめきれていません。漏えいの疑いも1つ残っています。

| ID | 中身 | 出どころ |
|---|---|---|
| K1 | **閉じた後のフォーカス（N-19）**: 確かめの表示を閉じた後、フォーカスが行の「操作」に戻らず body に移る。U5-NFR7.1 は Not Met。AC2.1.8・AC3.1.7・AC4.1.9・AC5.1.7 は条件つき。招待の画面など既存の画面でも起きている見込み（未確認）。直し方は2つ。(1) make-you-chic-ui の直した版へ固定先を上げる専用のコミット（C2′）。(2) E2E 110・120 の確かめの形を書き換える | `construction/code-generation/gate-decisions.md` 4節、`construction/build-and-test/build-and-test-summary.md` 4節、`construction/u5-user-admin-ui/code-generation/make-you-chic-ui-request-3.md` |
| K2 | **行の「操作」のメニューのはみ出し**: Dropdown を `placement="bottom-end"` にする。E2E 120 のはみ出しの確かめを、開いたメニューが画面の中に収まることまで広げる | `operation/deployment-execution/deployment-log.md` 3節、`runbooks.md` RB-27、`project.md` の学び（2026-10-03） |
| K3 | **言語の欄の送信中の防ぎ**: 送信中も言語の選択を変えられ、送った値と画面の値がずれうる | `construction/build-and-test/test-results.md` 7節 |
| T1 | **U3-NFR6.3 の (B) と警報3件の確かめ直し**: 準備のログインの失敗を含まない形に台本を直し、接続プールの上限を下げて流し直す。確かめるものは、2本目を借りる待ちが出ること、警報 `ms-pool-pending`・`ms-error-logs`・`ms-audit-fail` が鳴ること、409 `USER_ADMIN_BUSY` の2行（L3・L4）の結び付き | `operation/performance-validation/nfr-validation-matrix.md` 2節・3.1節 |
| T2 | **AC2.2.6 のテスト**: 印を外した直後の要求の 403 と、その監査の行を、1つのテストで続けて確かめる | `build-and-test-summary.md` 5節 |
| T3 | **出力を捕まえるテストの範囲の弱さ**: `CapturedOutput` が背景のスレッドの出力も含む。`MailConfigurationIT` が1回落ちた（不安定と確かめられていない扱い） | `gate-decisions.md` 4節 |
| T4 | **`perf/README.md` の注意書き**: `hikaricp.connections.acquire` の単位（秒・ミリ秒）が外部エクスポートの有無で変わる。`baseUnit` を見ることを書く | `nfr-validation-matrix.md` 4節 |
| S1 | **一意の制約の違反の例外の文（Q-H）**: H2 の 23505 の例外の文に、重なった値（メールアドレスなど）が入りうる。TRACE の `TraceAspect` に出うる（未検証）。一意の制約に当たる要求を TRACE と INFO で送り、出力に値が無いことを確かめるテストを足す | `gate-decisions.md` 4節（U3 計画 Q-H、U3 レビュー R-04） |

- **K1 の進め方**: make-you-chic-ui の側の直しの公開を待ちます（2026-10-03 の時点で未公開）。公開が間に合わなければ、K1 を除いて先に進め、公開の後に専用のコミットで取り込みます。
- **完了の目安（測れる形）**:
  - E2E 110・120 で、閉じた後のフォーカスが行の「操作」に戻ること、開いたメニューが画面の中に収まることを確かめて通る。
  - (B) で待ちの最大が 0 より明らかに大きいか時間切れの累計が 1 以上になり、警報3件が `Alerting` になる。
  - S1 の確かめのテストが、TRACE と INFO で重なった値を出さないことを確かめて通る。
- **推す理由**: どれも小さく、同じ画面とテストの周りで1回の Intent に収まります。S1 は `project.md` の Forbidden（メールアドレスをアプリのログに含めない）に関わるため、早く確かめたいものです。

### 第2の束（優先 2）: 安全の機能の判断

**問題**: 管理者がいなくなったときに戻す口が `.env` の作り直しだけで、監査にも残りません。ログインの時間の余裕も縮んでいます。

| ID | 中身 | 出どころ |
|---|---|---|
| S2 | **使える管理者がいなくなったときの救済の口と、初期管理者の作成の監査**: 今は `.env` を替えて作り直す手順（RB-22）で、作成は監査に残らない。要件定義で、口を設けるか、作成を監査に残すかを決める | `operation/incident-response/incident-plan.md` 7節、`runbooks.md` RB-22、依頼者の決定 |
| P1 | **ログインの余裕**: ログインの p95 は 939.6 ms で、目標 1 秒まで 60 ms（前の Intent は 904 ms・余裕 96 ms）。停止の判定を足した影響か、ぶれかを切り分ける。あわせて CPU の割り当てを見直す。目標は緩めない | `nfr-validation-matrix.md` 4節、`cost-analysis.md` 5節 |

- **完了の目安**: S2 は要件で決めた口・監査の有無がテストで確かめられること。P1 は停止の判定の有無を入れ替えた同じ条件の k6 で、差がぶれの幅に入るかを数字で示すこと。

### 第3の束（優先 3）: 配備先の決定を待つもの

**問題**: 配備先（クラウドなど）が決まらないと確かめられない、または決められない事項です。

| ID | 中身 | 出どころ |
|---|---|---|
| D1 | `ms-pool-pending` の式を、時間切れの累計の増加を見る形に見直す | `operation/observability-setup/alarms.md` 2.1節、`incident-plan.md` 7節 |
| D2 | `ms-5xx-ratio`・`ms-audit-slow` が U3 の失敗で鳴ることの確かめ（本番のコードを変えずに失敗を起こす手が無い） | `alarms.md` 2節 |
| D3 | 画面の時間（U5-NFR5.1 2 秒・NFR5.2 1.5 秒）の本番での判定 | `nfr-validation-matrix.md` 3.2節 |
| D4 | SLO の正式な値（30 日の窓、誤りの予算）と、管理の API の専用のパネル | `operation/observability-setup/slo-config.md`、`observability-setup-questions.md` Q3、`slo-report.md` 3節 |
| D5 | スキーマの変更があるときに、配備の前のバックアップを取る決まりにするか | `incident-plan.md` 7節 |
| D6 | 要求の回数の制限（R1）、バックアップの保存の期間（権限 644 の古いバックアップの扱いを含む）、外部の法令の枠組み、`v*` のタグと公開のリリース | `operation/environment-provisioning/validation-report.md`、`cd-config.md` 4節、`drift-report.md` 2節 |
| D7 | 前から: 招待の定期の削除の失敗（N3）と総当たり（R1）の警報 | 前の Intent の `aidlc/spaces/default/intents/260925-user-management/operation/feedback-optimization/feedback-loop.md` |
| D8 | 前から: 実在の SMTP（資格情報・暗号化）と、招待の時間の測り直し | 同上 |
| D9 | 前から: `/api/appearance` の回数の制限 | 同上 |
| D10 | 前から: 警報の通知の先 | 同上 |

### 受け入れ済みの危険（記録に残すだけ）

| ID | 中身 | 出どころ |
|---|---|---|
| S3 | V9 の後に戻している間は利用停止が効かない。U1-NFR10.2（1つ前の版が V9 の後の内部DB で動く）は未確認。次に確かめる機会は、実際に戻したときか、配備先が決まって戻しの練習を置くとき | `operation/deployment-pipeline/cd-config.md` 4節・5節 |
| S4 | U3 R-02: 操作の前の確かめ直しを排他なしで行う隙 | `gate-decisions.md` 4節 |
| S5 | 8KB を超える要求の HTML の 400 | 同上 |

### 片付け（依頼者の判断事項、Q3: A）

H1〜H4 の量と候補は `cost-analysis.md` 4節に書きました。この段では片付けていません。

### この段で見つかったこと（依頼者に確かめたい）

- 手元のイメージが Environment Provisioning の記録より減り（46 個 → 22 個）、結合テスト用の MySQL・MariaDB のイメージが無くなっています。原因は確かめていません（`drift-report.md` 2節）。次の `./gradlew verify` でダイジェストで取り直すため、ネットワークが要ります。
- 承認済みの記録どうしに、表現の食い違いが1つあります。`gate-decisions.md` 6節の「B5 の squash」は、git の履歴では U3 の直しの統合に当たります（`drift-report.md` 2節）。

## 3. 手作業の繰り返しの自動化の案

毎回の手順で手で組み立てているものです。どれも、後の Intent で依頼者が採るかを決めます。

| 案 | 今の手作業 | 自動化の形 | 守ること | 出どころ |
|---|---|---|---|---|
| A1 監査の数え上げと配備の後のバックアップ | アプリを止める → 内部DB のボリュームを複写 → 起動し直して healthy を待つ → 複写を `ACCESS_MODE_DATA=r` で開く → 配備の時刻以降の `audit_events` を種類・結果ごとに数える → 展開した複写を消す。配備のたびに手で組み立て、この Intent では許可の仕組みに一度止められた | 1つのスクリプト（引数は数える起点の時刻）。種類・結果ごとの件数と Flyway の最後の版・停止中の利用者の数だけを出し、個人に関する値は出さない。複写はホームの下（権限 700）に時刻つきの名前（600）で置く | 止める前に依頼者の承認を得る。`mktemp -d` を使わない（VM から見えない）。数える前に、スモークテストで監査に残る操作をしたかを依頼者に聞く | `deployment-log.md` 2節・3節、`smoke-test-results.md` S5、`project.md` の Deployment・Corrections の学び |
| A2 使い捨ての試験の環境の準備と片付け | 一時の環境ファイル（仮の署名鍵・仮の管理者）を作る → 配備したアプリを止める → 使い捨ての環境を起動 → 利用者を SQL で入れる → 1人ずつログインしてロックの状態の行を作る → `caffeinate -i` で台本全体を包んで k6 を流す → `/actuator/metrics` の hikaricp を `baseUnit` つきで読む → 結果を確かめてから片付ける → 配備したアプリを起動し直す | `perf/` に準備・実行・片付けを1つにまとめたスクリプト。片付けの前に、確かめの結果がそろったかを止まって確かめる | 片付けの前に確かめの結果を見る（前の Intent の学び）。zsh で変数にコマンドを入れない（関数にする）。上限に届く形の場面を含める | `operation/performance-validation/test-results.md`、`load-test-plan.md`、`project.md` の Testing Posture の学び |
| A3 起動のログの確かめ | `docker logs` を jq で読み、レベルの件数・Flyway の版・初期管理者の INFO のキーの名前を出す。値を出さない形を毎回考えている | 確かめ用の jq の決まった式を README か `perf/` に置く。値を出さず、キーの名前と件数だけを出す | 値を表示しない | `smoke-test-results.md` S2、この段の `drift-report.md` 1.2節 |
| A4 設定のずれの確かめ | この段の `docker inspect`・`docker compose config`・`.env` の項目の件数・イメージとボリュームの一覧を、毎回手で組み立てている | 読み取りだけのスクリプトにし、前回の結果との差を出す。イメージの一覧（タグとダイジェスト）も比べる | `.env` を開かず、`grep -c` の件数だけを出す。`docker compose config` の環境の値を出さない | この段の `drift-report.md` |

## 4. 道具の不具合（AI-DLC の道具）

| 不具合 | 起きたこと | 回避した形 | 出どころ |
|---|---|---|---|
| 決定の要約の道具 `review-brief summary` が動かない | 「main(argv) を持たない（main が無い）」という誤りで止まる。260925-user-management・260928-quality-followup・260929-log-deps-cleanup・この Intent の4つの Intent で続いている | 答えのまとめだけを示して確認した | `project.md` の Corrections の学び、`inception/practices-discovery/memory.md` |
| Request Changes が断られ続けた | Code Generation の承認の場で、Request Changes が「recovery-question choice was not Request Changes」で5回断られた。原因は分かっていない | 製品の不具合として報告した。依頼者の決定で U3 を直してから承認の場を開き直した | `construction/code-generation/gate-decisions.md` 6節 |
| 承認の場が記録だけのコミットで止まる | 先頭に記録だけのコミット（`aidlc/` の下だけ）が続くと、コードの作業が見えないとして断られた（`REQUIRED_SOURCE_WORK_MISSING`） | 承認の場は、最後のコードのコミットの後、記録だけのコミットを積む前に開く（学びとして保存） | `gate-decisions.md` 6節、`project.md` の Change Control の学び（2026-10-03） |
| 承認の場が git の対象外のファイルで止まる | 段の途中で消した E2E の報告（`frontend/playwright-report`・`frontend/test-results`）と IDE が書き換えた `.idea/workspace.xml` が、どの単位の一覧にも載せられず（git の対象外は記録に使えない）、戻せもしなかった | 依頼者の明示の承認を得て、承認の場を開く操作1回だけに `AIDLC_SKIP_SOURCE_FRESHNESS=1` を付けた。次からは段を始める前に git の対象外の生成物を消す（学びとして保存） | `gate-decisions.md` 6節、`project.md` の Testing Posture の後の学び（2026-10-03） |
| 統合の手順で監査ログの追記を消した | U3 の直しの squash の統合で `git restore --source=HEAD -- aidlc/` を使い、未コミットの監査ログ（`audit/sakura-local-4e42a93f87ce.md`）の追記（承認の場の断り・Request Changes の断り・そのときの問いと答え）を消して戻せなかった | `gate-decisions.md` 6節を、その間の記録の代わりにした。squash の前に `aidlc/` の未コミットの変更をコミットする（学びとして保存） | `gate-decisions.md` 6節、`project.md` の Change Control の学び（2026-10-03）、`drift-report.md` 2節（記録の表現の食い違い） |

- 道具の側の直しは、このリポジトリの外（AI-DLC の道具）の話です。このリポジトリでは回避の手順を学びとして残しました。

## 5. 運用から分かったこと（進め方の学び）

`project.md` に学びとして残したものと、この段で気づいたことです。

- 画面のはみ出しの確かめは、開いたメニュー・ポップアップが画面の中に収まることまで見る（Deployment Execution で保存）。
- スモークテストで依頼者が画面を触るときは、監査に残る操作をしたかをその場で聞き、期待の件数を合わせてから数える（同上）。
- 上限に届かない負荷では、接続プールの見積もりが誤っていても合格する。上限に届く形の場面を含める（NFR 要件で保存）。この Intent の (B) は、場面の形が上限に届かず確かめられなかった。
- 設定のずれの確かめでは、コンテナと設定に加えて、手元のイメージの一覧も前の記録と比べる。この段で、結合テスト用のイメージが無くなっていたことが分かった（`drift-report.md` 2節）。

## 6. 依頼者に頼むこと

- `develop` のプッシュ。今は `origin/develop` より 6 コミット先で（記録だけ）、この段の記録も加わります。プッシュは依頼者が行います。
- 次の Intent の範囲と順の決定（2節の案をもとに）。
- 片付け（H1〜H3）を行うかの決定（`cost-analysis.md` 4節）。
- 手元のイメージが減った原因の確認（2節の「この段で見つかったこと」）。
- 記録の表現の食い違い（`gate-decisions.md` 6節の「B5 の squash」）を直すかの決定。

## Sources

- `operation/feedback-optimization/feedback-optimization-questions.md`（後の Intent への持ち越しの一覧、Q1〜Q3、確認済みの要約）
- `operation/feedback-optimization/slo-report.md`・`cost-analysis.md`・`drift-report.md`
- `construction/code-generation/gate-decisions.md`（4節・6節）、`construction/build-and-test/build-and-test-summary.md`（4節・5節）・`test-results.md`（7節）、`construction/u5-user-admin-ui/code-generation/make-you-chic-ui-request-3.md`
- `operation/deployment-pipeline/cd-config.md`、`operation/deployment-execution/deployment-log.md`・`smoke-test-results.md`
- `operation/environment-provisioning/validation-report.md`
- `operation/observability-setup/dashboards.md`・`alarms.md`・`slo-config.md`・`observability-setup-questions.md`
- `operation/incident-response/incident-plan.md`・`runbooks.md`
- `operation/performance-validation/test-results.md`・`load-test-plan.md`・`nfr-validation-matrix.md`
- `inception/practices-discovery/memory.md`
- 前の Intent: `aidlc/spaces/default/intents/260925-user-management/operation/feedback-optimization/feedback-loop.md`
- `aidlc/spaces/default/memory/project.md`（学び）

## Assumptions & Open Questions

None.
