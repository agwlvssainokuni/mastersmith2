# 次への入力（feedback-loop）

Intent `260923-dsl-schema-loader`（ロードマップの Intent A・D・E）で後に回したこと、未確認のまま残ったこと、運用で分かったことを、次の Intent の候補として束ねる。決定は `feedback-optimization-questions.md`（Q4: A）。**順は案で、決めるのは依頼者**。

## 1. 候補の束と順の案

### 束 1（最初の案）: 小さな不具合の修正の Intent（scope の例: bugfix）

| # | 内容 | 出どころ | 直し方の見込み |
|---|---|---|---|
| 1-1 | Loki でログのキーと値（`dsl.operation`・`dsl.outcome` など）を絞り込めない。外部へ送るときにキーと値が属性として渡っていない | `operation/observability-setup/log-queries.md` 2節・`slo-config.md` 2節（依頼者の判断: 後の小さな Intent で直す） | `ObservabilityConfig` の出力の設定を1行足す（`setCaptureKeyValuePairAttributes(true)`）。送るキーと値に秘密情報が入らないことの点検とテストを足す（project.md の Forbidden） |
| 1-2 | ロックの状態の行が無い利用者が同時に初めてログインすると、MERGE の主キーの重複で 500 `INTERNAL_ERROR` になる | `operation/performance-validation/test-results.md` 3.2（F3。承認の決定: 後の Intent） | `createIfAbsent` の重複を「既にある」として扱う、または行の作成を別のトランザクションで確実に行う。再現するテストを同じコミットに入れる（project.md の Mandated） |
| 1-3 | 負荷の試験の台本 `dslMixed` が、同じ利用者を2つの VU に割り当てる | 同上（F4） | `loginLoop` の利用者の選び方を場面ごとの番号にする。README の試験用の利用者の入れ方に「先に1人ずつログインする」を足すか、1-2 を直して不要にする |
| 1-4 | 起動のときの Hibernate の案内（11 行）が、改行を含んだ1件のログになる（1行1件の JSON の決まりから外れる） | `operation/observability-setup/observability-setup-questions.md` Q3（依頼者の判断: 記録だけにし、後の Intent の課題） | 該当のロガーの水準を上げて出さないか、改行を含むメッセージの扱いを決める（`application.yaml` の変更、配備し直しが要る） |

- 同じ部品（ログ・認証）にまたがる小さな変更で、まとめても1つの Bolt に収まる見込み。

### 束 2: メモリと内部DB のファイルの伸び

| # | 内容 | 出どころ |
|---|---|---|
| 2-1 | 動いている間、DSL の投入と適用のたびに内部DB のファイルが増え、止めるまで縮まない（U4-STORAGE-RUN。既知の制約として受け入れ済み。README に見方と起動し直しの目安） | `construction/build-and-test/build-and-test-summary.md`（U4-STORAGE-RUN）、`operation/performance-validation/test-results.md` 4節（投入 270 回で 2.4GB） |
| 2-2 | 10MB の DSL とログインの重ねで、2g でもプロセスのメモリが上限の 93%。1g では止まる（NFR1.12 は 1g で Not Met） | `operation/performance-validation/test-results.md` 3.1・`nfr-validation-matrix.md` |
| 2-3 | compose のメモリの上限の既定は 1g で、2g は各 PC の `.env` の設定（記録の「配備の既定 2g」との食い違い） | `drift-report.md` D1、`cost-analysis.md` C3 |
| 2-4 | アプリのコンテナが、見本の対象DB の管理者のパスワードを環境変数で持っている（`.env` 全体を読むため） | `drift-report.md` D2 |

- 2-1・2-2 は設計の見直し（本文の保存の仕方、読み込みのメモリの使い方）を含むため、束 1 より大きい。2-3・2-4 は設定と README の変更で、束 1 に入れることもできる。

### 束 3: 依頼者の確認や配備先の決定を待つもの

| # | 内容 | 出どころ | 待つもの |
|---|---|---|---|
| 3-1 | 古い版 `10742a3` が、V5・V6 の当たった内部DB で起動できるか（U4-MIGRATION）は未確認 | `operation/deployment-execution/deployment-log.md` 3節 | 実際に戻すとき、または次の配備の前の戻しの練習 |
| 3-2 | アクセシビリティの手での確認 7 件（キーボード・Tab の順・フォーカス・拡大・読み上げ・画面の切り替え・設計の決まり） | `construction/build-and-test/build-and-test-summary.md`（A11Y-*） | 依頼者の手での確認 |
| 3-3 | make-you-chic-ui の Modal・Alert に口が無く、英語表示でも閉じるボタンが「閉じる」のまま、確認の表示の本文が `aria-describedby` で結ばれていない | Code Generation のレビューの指摘（依頼者の判断: make-you-chic-ui 側で対応） | make-you-chic-ui 側の変更と、サブモジュールの固定先の更新（project.md の Mandated） |
| 3-4 | make-you-chic-ui に無い部品（ファイルの選択・違いの表の行の開閉・メニューの木）を frontend 側で作った。後の Intent でも要るなら make-you-chic-ui 側への追加を相談する | project.md の学び（refined-mockups） | 後続の画面の Intent の設計 |
| 3-5 | 障害の対応の未決の点（記録の置き場、監査記録が抜けたときに埋める手順、リフレッシュトークンをまとめて無効にする機能・利用者のパスワードを変える機能が無い、H2 のパスワードの変え方が未確認、手順の中のログの文字列の一部が推測） | `operation/incident-response/incident-plan.md`・`runbooks.md` | 依頼者の決定。機能の追加は利用者の管理を扱う Intent で |
| 3-6 | 仮の目標（SLO）の正式な値、常時の測定、知らせの先、クラウドの基盤、部品表（SBOM） | `slo-report.md` 3節、team.md の Deployment | 配備先の決定 |

### 束 4: ロードマップの次の Intent

- DSL のモデルの提供口（`ActiveDslModel`）を使う後続の Intent（I・J・K など）。業務データの CRUD を扱う Intent では、監査記録の共通の仕組みも検討する（project.md の Decided）。
- 出どころ: `inception/units-generation/unit-of-work.md`、`aidlc/spaces/default/memory/project.md`（Decided）。

## 2. 運用で分かったこと

| # | 分かったこと | どう生かすか |
|---|---|---|
| O1 | 手元の監視を常に動かしていないため、稼働率や誤りの予算は測れない（`slo-report.md`） | 配備先を決めるときに、常時の収集と知らせの先を一緒に決める |
| O2 | 投入を重ねると内部DB のファイルが膨らむが、軽い API の時間には影響が見られなかった（2.4GB で 95 パーセンタイル 1.1ms） | 起動し直しの目安（300MB）は、時間ではなくディスクとメモリのために守る |
| O3 | 長い作業の途中で PC がスリープし、試験の結果が崩れた | 電源につなぎ `caffeinate -i` で流す（project.md に保存済み） |
| O4 | 直接 SQL で入れた利用者は、アプリで作った利用者と状態が違う（ロックの状態の行が無い） | 試験の準備はできるだけアプリの口を通す。1-2 を直す |
| O5 | 手元の監視（Grafana）は 900m では止まった | 監視の上限は 1536m（済み）。監視と負荷の試験を同時に動かさない |

## 3. 手作業のうち、自動にできそうなもの

| 手作業 | 今の頻度 | 自動にする案 |
|---|---|---|
| 配備の前の内部DB の複写（アプリを止めて `tar`） | 配備ごと・大きな DSL の操作の前 | 配備の手順の台本にまとめる（止める → 複写 → 起動 → healthy の確かめ） |
| 内部DB のファイルの大きさの確かめと起動し直し | 大きな DSL の投入と適用を重ねたとき | 大きさの指標と警報をアプリに足す（2-1 と一緒に。Observability Setup の Q2 では手順だけにした） |
| 負荷の試験の準備（使い捨ての環境・試験用の利用者・Hikari の値の記録） | 性能を確かめる段ごと | `perf/` の台本に、試験用の利用者の用意と Hikari・cgroup の記録を取り込む |

## Sources

- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/feedback-optimization/feedback-optimization-questions.md`（Q4: A、確認済みの要約）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/feedback-optimization/slo-report.md`・`cost-analysis.md`・`drift-report.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/observability-setup/log-queries.md`・`slo-config.md`・`observability-setup-questions.md`・`dashboards.md`・`alarms.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/performance-validation/test-results.md`・`nfr-validation-matrix.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/deployment-execution/deployment-log.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/incident-plan.md`・`runbooks.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/build-and-test/build-and-test-summary.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/inception/units-generation/unit-of-work.md`
- `aidlc/spaces/default/memory/project.md`（Decided・Mandated・Forbidden・学び）

## Assumptions & Open Questions

- [assumption] 束の分け方と順は案であり、次の Intent の範囲と順は依頼者が決める。
