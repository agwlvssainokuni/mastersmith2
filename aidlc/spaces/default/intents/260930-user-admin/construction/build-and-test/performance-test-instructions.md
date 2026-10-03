# 性能の試験の手順（performance-test-instructions）

Intent `260930-user-admin` の性能の目標の確かめ方と、その持ち主の段を示します。

この Intent の流れには performance-validation の段があります（`aidlc-state.md` の OPERATION PHASE、EXECUTE）。そのため、役割を次のように分けます（各単位の NFR 要件の前提の節、`build-and-test-questions.md` の前提）。

- **Build and Test（この段）**
  - 画面の時間の測り（E2E 120 の中）を写す。記録のみで成否にしない
  - 初回に読む JavaScript の量を写す
  - k6 の場面と手順書が用意され、読み込めることの確かめ
- **performance-validation**
  - 同時 10 件の 95 パーセンタイル（API）
  - 接続プールの余裕と、上限に届く形での見積もりの確かめ
  - 想定の規模（利用者 1,000 名の一覧など）の確かめ
- **observability-setup・feedback-optimization**
  - 指標のラベル（`uri`・`le`）の実際の値、既存の警報が拾うこと、SLO の基準の値
  - 画面の時間の本番での判定

目標の値は、各単位の `nfr-requirements/performance-requirements.md`・`reliability-requirements.md`・`scalability-requirements.md` の値をそのまま使います。緩めません。目標に届かないときは、原因をログと状態で確かめて依頼者に相談します（`project.md` の Testing Posture）。

## 1. この段で写すもの

### 1.1 画面の時間（E2E 120 の測り、記録のみ）

`./gradlew e2eTest`（Mailpit を起動して）の中の 120 の測りのテストが、既定の1組（light・md・blue・既定の幅）で5回ずつ測ります。値は `frontend/test-results/e2e-results.json` の添付 `user-admin-screen-ms`（base64）に出ます。

| 場面 | 目標（出典） | 測る中身 |
|---|---|---|
| `list` | 「利用者の管理」を開いてから一覧の1ページ目の行が出るまで 2 秒以内（U5 の NFR5.1） | サイドバーを選ぶ直前から最初の行が見えるまで。本物の一覧の API を差し替えの口を通して読む（口の上乗せを含む、`apiTimeIncluded: true`） |
| `nextPage` | 「次へ」を押してから2ページ目の行が出るまで 1.5 秒以内（U5 の NFR5.2） | 2ページ分の見本の応答での描画の時間（API の時間を含まない、`apiTimeIncluded: false`） |

- 1回目と、2〜5回目と最大を分けて記録します。時間はテストの成否にしません（U5 の基盤の設計の R-01）。
- E2E の WAR は PC の上で空に近い内部DB を使います。配備したアプリはコンテナで、使い続けた内部DB です。本番での判定は `Unverified` とし、持ち主は performance-validation・observability-setup・feedback-optimization です。
- U4 の S6（権限が無い）の画面の時間には、数値の目標を置きません（U4 の NFR9.3）。

### 1.2 初回に読む JavaScript の量

`./gradlew verify` の `verifyArtifact`（`frontendBundleSize`、`frontend/scripts/check-bundle-size.mjs`）の出力を写します。gzip で 500KB 以内を目安とし、超えても警告だけです（U4 の NFR9.4、U5 の NFR5.6）。

| 時点 | 値（gzip） | 出典 |
|---|---|---|
| U4 の前（B2 の始め） | 123.1 KB | U4 の `code-summary.md` 3節 |
| U4 の後 | 122.7 KB | 同上 |
| U5 (1) 固定先を上げる前 | 122.7 KB | U5 の `code-summary.md` 3節 |
| U5 (2) 固定先を上げた後・画面の前（`364e9d6`） | 122.8 KB | 同上 |
| U5 (3) B5 の関門（`81d2423`） | 125.5 KB（`index-*.js` 108.8 KB・`useTranslation-*.js` 16.7 KB） | U5 の `generation-notes.md` の「Step 22（C2′ の前）」 |

## 2. 負荷の試験の場面（performance-validation で流す）

台本は `perf/k6/scenarios.js`、手順は `perf/README.md` の「利用者の管理の場面（Intent 260930-user-admin の U3）」です。この Intent では U3 の B4（Step 39）で5つの場面を足しました。測定は使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚しません（`project.md` の Testing Posture）。

| 場面 | 閾値（同時 10、p95 と `checks` の率 1） | 出典 |
|---|---|---|
| `userAdminList`（`LIST_CASE` a〜d: 検索なしの1ページ目・最後のページ・多く当たる検索・当たらない検索、利用者 1,000 名） | 1 秒 | U3 の NFR5.1・NFR5.8 |
| `userAdminProfile`（成功 204 と入力の誤り 400） | 1 秒 | U3 の NFR5.3 |
| `userAdminOps`（5つの操作を操作ごとに。409 `USER_ADMIN_BUSY`・`USER_ADMIN_NO_CHANGE` と 5xx が 0 件） | 1 秒 | U3 の NFR5.4・NFR5.6 |
| `userAdminSuspendWorst`（未無効 100 件・無効 1,000 件のリフレッシュトークンを持つ対象を止める） | 1 秒 | U3 の NFR5.5、U1 の NFR5.2 |
| `userAdminPool`（5つの操作と一覧を同時に。上限 30） | `checks` の率 1、hikaricp の待ちの時間切れの累計 0 | U3 の NFR6.2 |
| `userAdminOps` を接続プールの上限 10 で `VUS=5`・`VUS=10` | (A) 同時 5（見積もり 10 本で上限ちょうど）は待ちの時間切れの累計 0 で操作と監査の件数が一致、(B) 同時 10（見積もり 20 本）は2本目を借りる待ちが出る（待ちの最大が 0 より明らかに大きい、または時間切れの累計が 1 以上）。(B) の時間切れ・ERROR は想定どおりで p95 と件数の一致に数えない | U3 の NFR6.3 |
| 既存の `login`・`refresh`（停止の判定を足した後） | 1 秒（既存の目標のまま） | U1 の NFR5.1・NFR6.1、U4 の NFR5.1 |
| 既存の `invitationList`（1ページ目と最後のページ） | 1 秒（既存の目標のまま） | U2 の NFR5.1 |

- 正とする値は k6 の値（クライアント側）です。サーバー側の `http_server_requests` の p95 は参考として並べます。
- 接続プールは、使い捨てのアプリにだけ `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics` を渡し、`hikaricp.connections.timeout`（待ちの時間切れの累計）と `hikaricp.connections.acquire`（借りるまでの待ちの最大）で判断します。数秒ごとの使用中の数では判断しません。上限に届かない負荷では見積もりが誤っていても合格するため、上限を下げた場面（NFR6.3）を必ず含めます（`project.md` の Testing Posture）。
- 測る間は配備したアプリを止め、`caffeinate -i` で台本の全体を包みます。遅れが出たときは `pmset -g log` でスリープを確かめます。
- 5つの操作の準備のログインの失敗（`{name:userAdminPrepLogin}`）は p95 と `checks` に数えません。使い捨ての環境の監査に `LOGIN_FAILED` が残ります。
- 使い捨ての環境を消す前に、確かめの結果（接続プールの時間切れの累計、成功の件数と監査の件数の一致）を見ます。

### この段で確かめること（台本が読み込めること）

```bash
node --check perf/k6/scenarios.js
for s in userAdminList userAdminProfile userAdminOps userAdminSuspendWorst userAdminPool; do
  docker run --rm --network none -v "$PWD/perf/k6:/scripts:ro" -e SCENARIO=$s \
    grafana/k6:2.3.0 inspect --include-system-env-vars /scripts/scenarios.js
done
```

- すべての場面が読み込めて、scenarios と thresholds が上の表のとおりに出ることを確かめます（`--include-system-env-vars` が無いと場面の名前が `undefined` になります。`project.md` の Testing Posture）。
- **この段では流し直していません**。`perf/` は B4（`4bd600d`）の後に変わっていないため（`git log 4bd600d..7689ade -- perf/` が 0 件）、B4 の Step 39 の結果（5つの場面がすべて読み込めて場面の名前と閾値が出た、`VUS=5` の `userAdminOps` は 20 回、既存の `health`・`invitationList` も読み込めた）をこの段の結果とします（`test-results.md` の 5節）。この段の依頼で docker を使わないため、流し直しは performance-validation の最初に行います。

## 3. この段では確かめない性能の目標

| 目標 | 持ち主 |
|---|---|
| 同時 10 件の p95（上の表のすべて） | performance-validation |
| 接続プールの余裕と見積もり（U3 の NFR6.2・NFR6.3、U1 の NFR6.1 の後半） | performance-validation |
| 規模（U1 の NFR5.4、U3 の NFR5.8） | performance-validation |
| 指標のラベルの実際の値・既存の警報が拾うこと（U3 の NFR5.9・NFR5.10） | observability-setup |
| SLO の基準の値（U3 の NFR5.11） | observability-setup・performance-validation |
| 画面の時間の本番での判定（U5 の NFR5.1・NFR5.2） | performance-validation・observability-setup・feedback-optimization |

## Sources

- 各単位の `nfr-requirements/performance-requirements.md`・`reliability-requirements.md`・`scalability-requirements.md`・`nfr-design/performance-design.md`（`aidlc/spaces/default/intents/260930-user-admin/construction/u1-user-suspension/` 〜 `u5-user-admin-ui/`）
- 各単位の `code-generation-plan.md`（「Build and Test に引き継ぐこと」）・`code-summary.md`、`aidlc/spaces/default/intents/260930-user-admin/construction/u3-user-admin-api/code-generation/generation-notes.md`（Step 39）、`aidlc/spaces/default/intents/260930-user-admin/construction/u5-user-admin-ui/code-generation/generation-notes.md`（「Step 22（C2′ の前）」）
- `perf/k6/scenarios.js`・`perf/README.md`（「利用者の管理の場面」）、`git log 4bd600d..7689ade -- perf/`（読み取り）
- `aidlc/spaces/default/memory/project.md`（Testing Posture）
- `aidlc/spaces/default/intents/260930-user-admin/construction/build-and-test/build-and-test-questions.md`（前提）

## Assumptions & Open Questions

- k6 の台本の読み込みは、この段では流し直さず B4 の結果を使いました（`perf/` に差が無いため）。performance-validation の最初に `k6 inspect` を流し直してから測ります。
