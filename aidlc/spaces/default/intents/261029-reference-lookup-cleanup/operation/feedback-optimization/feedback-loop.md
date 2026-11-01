# 次への入力（feedback-loop）

Intent 261029-reference-lookup-cleanup の振り返りです。次の Ideation（次の Intent の範囲を決める場）への入力として、運用の結果と持ち越しをまとめます。束ね方と優先の順は Q1: A の案で、決めるのは依頼者です。

## 1. この Intent でできたこと

- 他テーブル参照の選択肢（REFERENCE、200 件まで）と参照ピッカー（LOOKUP）の API と画面（U1・U3）、作成・更新での保存前の参照の確かめ（U2）を作り、配備しました（2026-11-01 05:01Z、WAR `4cd5c416…`）。参照先を読めない利用者には選択肢を返さず、存在しない値と権限の無い値を同じ 409 で拒否します。
- k6 の結果の JWT の自動の伏せ字の道具（U4、`perf/redact-k6-evidence.mjs` と `perfEvidenceScan`）を入れ、Performance Validation の記録（`evidence/` の 36 ファイル）で使いました。
- コンテナの版そろえ（U5。Mailpit・lgtm を `compose.yaml` から作り直した）と `perf/README.md` の整理（今の基準の一覧・履歴）、Dependabot の #81・#82 の取り込み。
- 監視: パネル 46・47 と区画の説明を足し、`ms-biz-target-db` が参照の 503 で鳴ることを確かめました。
- 手順書: RB-48〜RB-56 を足しました。
- 負荷の試験: 3種類の対象DB で Met 10・Not Met 0・Unverified 0 でした。
- CI だけで起きた失敗2つ（Tooltip の時計、`ReferenceSameResponseIT` の偶然の一致）を直し、上流の make-you-chic-ui に依頼（7）を出して `cab4207` を取り込みました。
- 前の Intent の持ち越しのうち、N1（REFERENCE・LOOKUP）を片付けました。

## 2. 持ち越しの一覧

### 2.1 前の Intent（`261016-business-data-views`）の持ち越しの今の状態

| 前の ID | 状態 | 根拠 |
|---|---|---|
| A1・C1〜C7・G2・G4・G9・`perf/README.md` (c) | 片付いた（Intent `261025-deps-perf-carryover` の FR1・FR3） | 同 Intent の要件と Build and Test |
| B1〜B6・G1・P5〜P7 | 片付いた。`261025-deps-perf-carryover` の FR4 で扱い、残った Not Met は Intent `261027-perf-targets-review` で目標の緩和を明示して記録し、新しい線で全 Met（前の線での Not Met 5 件は事実として記録されたまま） | 両 Intent の Build and Test |
| A4（`main` と配備した WAR の食い違い） | 片付いた（今の配備した WAR は `main` と同じソース） | この段の `drift-report.md` 1.4 |
| N1（REFERENCE・LOOKUP） | 片付いた（この Intent） | 1節 |
| A2（make-you-chic-ui の lockfile の `braces` 3.0.3） | **残る**。上流の `cab4207` でも 3.0.3 のまま | この段の読み取り |
| A3（make-you-chic-ui の依頼（5）） | **残る**。上流に公開されておらず、自前の `ScrollRegion`・`PageSizeSelect` を使い続けている | `261025-deps-perf-carryover` の要件 FR1.5 |
| N2〜N6 | **残る** | 2.2 |
| E1〜E9・D3・D5〜D7・D9 | **残る**（配備先の決定を待つ） | 2.2 |
| F1〜F11・R1〜R3 | 記録だけ（受け入れ済みの危険） | 前の Intent の `feedback-loop.md` 2節 |

### 2.2 次の Intent の候補と優先の順の案（Q1: A）

Q1: A の案は、次の Intent を「N2〜N6 から要件定義で選ぶ機能の Intent」とし、その最初の Bolt に小さな直しを入れる形です。今回は Dependabot の知らせが 0 件、性能の Not Met も 0 件で、小さな直しだけでは1つの Intent にするほどの量がないためです。

#### 次の Intent の最初の Bolt（小さな直し）

| ID | 中身 | 出どころ |
|---|---|---|
| P1 | MySQL の 1038（並べ替えの領域の不足）の深いページの確かめ。Build and Test の確かめ（2,000 行・200 行が 10,000 字・1ページ 20 件）では再現しなかった。見立て（未検証）は「`LIMIT ? OFFSET ?` の形のため上位 N 件の並べ替えで済んだ」。深いページ（`OFFSET` が大きい）や1ページの件数を増やした形を NFR 設計の捨ての試しで確かめ、直すかを要件で決める | `construction/build-and-test/k6-trial-record.md` 4.4節 |
| T1 | k6 の伏せ字の道具のカバレッジ（行 94.2%・分岐 90.61%）が `verify` の下限の外で、下がっても気づく手段が無い（U4 R-02）。`coverage.include` に足すか | `construction/build-and-test/test-results.md` 10節 |
| T2 | 道具に ESLint と型の検査が当たらない（U4 R-03） | 同上 |
| T3 | `BusinessDataWriteService` の参照の段で、手がかりの無い想定外の失敗のときに `stage` の無い WARN が出る経路（値は出ない） | 同上 |
| A2・A3 | 上流の make-you-chic-ui を読み取りの `git fetch`・`git show` で確かめ、`braces` の直しと依頼（5）が公開されていれば取り込む。無ければ除いて進める | 2.1 |
| — | その時点で届いている Dependabot の知らせ（High 以上が出たら Bolt の前に取り込む） | `team.md` の Way of Working |

#### 次の Intent の機能（要件定義で選ぶ）

| ID | 中身 | 出どころ |
|---|---|---|
| N2 | F2: `users.admin_flag` を縮める段と、管理の可否をロールで表す形。`team.md` の Deployment のとおり、縮める前の版を先に配備して確かめ、列を消す移行を別の配備に分け、戻しは配備の前の内部DB の複写を戻す形にする（配備が2回要る） | 前の Intent の `feedback-loop.md`、`team.md` の Deployment |
| N3 | 書き出し・取り込み・まとめての更新と削除 | 同上 |
| N4 | 同時の更新の照らし合わせ（楽観的な排他） | 同上 |
| N5 | 読み取りの監査・更新の前後の値の監査 | 同上 |
| N6 | 日時のタイムゾーンの変換 | 同上 |
| N7 | この Intent の範囲の外にしたもの: 複数行を選ぶピッカー、一覧・詳細での参照のラベル併記、別のスキーマの表の参照、既定の DSL の生成で LOOKUP を出すこと、参照先の選択肢のキャッシュ、選択肢の 200 件の上限を DSL で変えること | `inception/requirements-analysis/requirements.md`（範囲の外・未解決の点） |

#### 見張るもの（直すかは後で決める）

| ID | 中身 | 出どころ |
|---|---|---|
| P2 | MariaDB の対象DB の `anon` の最大が2回目 87.0%（前の Intent 85.01%、線 92% の内）。余裕が縮んでいる。README に必要なメモリの目安を書くか | `operation/performance-validation/test-results.md` 5節 |
| P3 | `refPickerContains` の p95 は MySQL 1,337〜1,385 ms・MariaDB 1,141〜1,464 ms（線 3,000 ms の内）。読み取りのプールの借りる待ちの最大は 1,094〜1,413 ms で、上限 3 秒への余裕は大きくない | 同 3節・4節 |

#### 配備先の決定を待つもの

| ID | 中身 | 出どころ |
|---|---|---|
| E1〜E9 | 前から: 外部エクスポートと通知の先（今は配備したアプリではどの警報も鳴らない）・JVM が止まったときの起き直し、`ms-forbidden` の題としきい値、`ms-audit-fail`・`ms-audit-slow`、SLO の正式な値、平常の幅と異常の検出、本物の対象DB のバックアップ、書き込みのアカウントの絞り方と秘密の置き場、外部エクスポートの受け手の大きさ、blue/green など | 前の Intent の `feedback-loop.md` 2節 |
| E10 | REFERENCE の参照先の行が 200 に近づいたことを見張るか（今は画面の説明文で気づく。RB-53） | `operation/incident-response/incident-plan.md` 7節 |
| E11 | 部分一致の検索の目標 3,000 ms を見分けるバケットを足すか（Observability Setup の Q4: A で今は足さない） | 同上、`operation/observability-setup/anomaly-config.md` 3節 |
| E12 | HikariCP の WARN のスタックトレースに対象DB のホスト名が出ること（決定 C で許容。ログの置き場と見る人が変わるときに見直す） | `incident-plan.md` 7節、`operation/observability-setup/log-queries.md` 3節 |
| D3・D5〜D7・D9 | 前から: 区画の p95 の警報、画面の誤りを集める仕組み、画面の時間の測り方、`ms-pool-pending` の式、招待の削除の失敗など | 前の Intent の `feedback-loop.md` |

#### 記録に残すだけ

| ID | 中身 | 出どころ |
|---|---|---|
| T4 | AC3.1.7（U2 → U3）と AC3.4.5（U1 → U3）の追跡の記録の抜け（実装とテストは U3 にある。Build and Test の承認の場で受け入れ済み）。次の Intent では、ほかの単位のストーリーの画面側の基準を、その単位の `traceability.json` に載せる | `construction/build-and-test/build-and-test-summary.md` 5節 |
| T5 | `deployment-strategy.md` S1 の WARN の名前の差。次の配備の手順は Spring の項目の名前で探す形で書く | `operation/deployment-execution/deployment-log.md` 3節、`runbooks.md` |
| X1 | lgtm の `0.34.0` と古いタグ無しの lgtm 2つが記録の無いまま消えた。`0.34.0` に戻すときは固定のダイジェストで pull し直す | この段の `drift-report.md` 2節 |
| F・R | 前からの受け入れ済みの危険（戻し先は `pre-reference-lookup-cleanup` に替わった） | 前の Intent の `feedback-loop.md` |

#### 片付け（Q2: A の結果）

この段でタグ 5 個とビルドのキャッシュを片付けました（`cost-analysis.md` 5.1）。残る候補は、名前の無いボリューム（回収できる 12.56GB の多く）・固定先から外れた古い版のイメージ（約 2.8GB）・PC のディスク 88% です（`cost-analysis.md` 5.2）。

## 3. 運用から分かったこと（進め方の学び）

### 3.1 配備と取り込み

- 入れ替えの停止は約 27 秒で、移行と `.env` の変更の無い配備はイメージの入れ替えだけで済みました。WAR は作り直しの前後で同じ SHA-256 になり、`aidlc/` の記録だけの差では WAR が変わらないことを確かめられました。
- `deployment-strategy.md` の S1 に書いた探す文字列がログに出る名前と違い、書いたとおりに探すと WARN が出ていても 0 件になる形でした（T5）。手順書に探す文字列を書く前に、ログに出る名前をソースで確かめます。

### 3.2 CI と不安定なテスト

- Tooltip の時計の失敗は二度目で直すまで進まず、時間の上限を先に決めた再現の試みと、予約の順を確実に作る確かめ（直す前に落ち、直した後に通る）で決まりどおりに直しました。上流への依頼（7）も短く通りました。
- 目印の文字列で「応答に値が無い」ことを確かめるテストは、乱数のトレースID に偶然に当たりうるため、16進に無い文字を含む目印にします。

### 3.3 試験と測りの進め方

- 種の確かめ（行の数・目印の語の当たり・最長の文字数・表の大きさとプールの割合）を先にし、回の間に種を入れ直したことで、3回目を流さずに済み、停止は前の Intent より約 1 時間 10 分短くなりました。
- 伏せ字の道具で k6 の記録を置き、`--scan`・`gitleaks dir`・`eyJ` の検索がどれも 0 件でした。前の Intent のような force-push は起きていません。

### 3.4 記録と環境

- 記録の無いまま、環境が2つ変わっていました（X1 の lgtm の古いイメージ、X2 の `.env` の更新の時刻）。どちらも今の動作には影響していませんが、環境を変える操作は、どの段で行ったかを記録に残すことを改めて守ります。

## 4. 手作業の繰り返しの自動化の案

| # | 案 | 理由 |
|---|---|---|
| A1 | 振り返りの段の読み取りの確かめ（コンテナの版とダイジェストと `compose.yaml` の比べ、環境変数の名前の数え、WAR の SHA-256、`.env` の時刻、`docker system df`）を1つの台本にする | Intent ごとに同じ確かめを手で行い、今回もずれ（X1・X2）を手で見つけた |
| A2 | `main` への取り込みの前の CI の試行ごとの確かめ（`gh run view --json attempt,jobs`）を台本にする | 前の Intent からの案で、まだ手で行っている |
| A3 | 内部DB の複写を数える道具を1つにまとめる（`~/.mastersmith-backup/` に古い `count-db.sh`・`count-db2.sh`、`~/.mastersmith-tools/` に `count-db3.sh` などがある） | 道具が2か所に分かれ、権限のずれ（X3）も起きた |

## 5. 道具の不具合とつまずき（AI-DLC の道具）

- 決定の要約を出す道具（review-brief summary）が今回も動かず、答えのまとめだけで確認しました（User Stories の記録）。
- 振り返りの調べで、`.env` の項目の名前を数える読み取りが自動の権限判定で止められ、指揮役が依頼者の承認を得て行いました。

## 6. この段で見つかったこと（依頼者に確かめた）

- X1: lgtm の古いイメージが記録の無いまま消えていた（記録だけ。Q3: A）。
- X2: `.env` の更新の時刻 08:43:55Z に当たる記録が無かった。依頼者に心当たりは無く、項目の名前は 18 で増減は無かった。値の変化は確かめていない（Q3: A）。
- X3: 数える道具の2ファイルが 644 だった（600 に直した。Q3: A）。

## 7. 次の手順と依頼者に頼むこと

1. この段の記録のコミット（段の初稿と段の承認のとき。`project.md` の決まり）。
2. **`main` への取り込み（Q4: A）**: この段の記録のコミットの後に、`be6e61c5` の後のすべてのコミットの CI を試行ごとに確かめます（`gh run view --json attempt,jobs`。この段の調べの時点で `afe8aa45` は実行中、`96011fe0` は待ち）。失敗が見つかれば取り込みを止めて相談します。通れば、依頼者の承認を得て `develop` を `main` へ fast-forward で取り込みます。`v*` のタグは付けません。
3. `develop`・`main` の push（依頼者が行う。AI はプッシュしない）。
4. **次にアプリを作り直す配備の前に、依頼者が `.env` の中身を確かめる**（X2）。
5. 次の Intent の範囲の決定（2.2 の束と順の案）。

## Sources

- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/feedback-optimization/feedback-optimization-questions.md`（Q1〜Q4: A と X2 の追加の確認）
- この段の `slo-report.md`・`cost-analysis.md`・`drift-report.md`
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/performance-validation/test-results.md`・`nfr-validation-matrix.md`・`load-test-plan.md`
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/observability-setup/dashboards.md`・`alarms.md`・`slo-config.md`・`log-queries.md`・`anomaly-config.md`
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/incident-response/incident-plan.md`・`runbooks.md`
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/deployment-execution/deployment-log.md`、`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/deployment-pipeline/deployment-strategy.md`
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/build-and-test/build-and-test-summary.md`・`test-results.md`・`k6-trial-record.md`
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`
- 前の Intent の記録: `aidlc/spaces/default/intents/261016-business-data-views/operation/feedback-optimization/feedback-loop.md`、`aidlc/spaces/default/intents/261025-deps-perf-carryover/inception/requirements-analysis/requirements.md`・`construction/build-and-test/cross-unit-traceability.md`、`aidlc/spaces/default/intents/261027-perf-targets-review/inception/requirements-analysis/requirements.md`・`construction/build-and-test/build-and-test-summary.md`
- `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- 優先の順は案です（Q1: A）。次の Ideation で依頼者が決めます。
- A2・A3 の上流の公開は `git fetch` で確かめていません（次の Intent の始めに上流のソースで確かめる）。
- `261025-deps-perf-carryover` で片付いたとした項目は、同 Intent の要件と Build and Test の記録で見たもので、項目ごとの結果は1つずつは確かめ直していません。
