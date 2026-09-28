# 性能の試験の手順（performance-test-instructions）

Intent `260925-user-management` の性能の目標の確かめ方と、その持ち主の段を示します。

この Intent の流れには performance-validation の段があります。そのため、役割を次のように分けます（各単位の NFR 要件の前提の節、`build-and-test-questions.md` の前提）。

- **Build and Test（この段）**
  - 1回ずつの時間：画面の時間の測り（E2E の中）
  - k6 の場面と手順書の用意
  - 台本が読み込めることの確かめ
- **performance-validation**
  - 同時 10 件の 95 パーセンタイル
  - 接続プールの余裕
  - 規模の確かめ

## 1. この段で測るもの（画面の時間）

`./gradlew e2eTest`（Mailpit を起動して）の中の測定のテストが、場面ごとに5回測ります。値は `frontend/test-results/e2e-results.json` に出ます。

| 単位 | ファイル | 目標（出典） |
|---|---|---|
| U4 | `050-display-accessibility.e2e.ts` | 最初の画面の時間が 2 秒以内（U4 の NFR6.1） |
| U5 | `060-invitation-accessibility.e2e.ts` | 招待の管理の画面の時間（U5 の NFR6.1・NFR6.2） |
| U6 | `090-invitation-registration-flow.e2e.ts` | 登録の完了の画面の時間（U6 の NFR6.1） |
| U7 | `080-preferences-accessibility.e2e.ts` | プリファレンスの画面の時間（U7 の NFR6.1〜NFR6.3） |

- 目標の値は、各単位の `nfr-requirements/performance-requirements.md` の値をそのまま使います。緩めません。
- 目標を超えたら、切り分けて依頼者に相談します。
- 測りが `test.skip` で飛ばされたときは `Unverified` とし、理由を書きます。
- 運用の中での判定は observability-setup・feedback-optimization が持ち主です。
- E2E の WAR は PC の上で空の内部DB を使います。配備したアプリはコンテナで、使い続けた内部DB です。値を比べるときは、この違いを明記します（U7 の計画）。
- 初回に読む JavaScript の量（U4 の NFR6.3・NFR6.5、U5・U7 の NFR6.6、U6 の NFR6.3）は、`./gradlew verify` の `verifyArtifact`（`frontendBundleSize`）の出力を写します。上限は置きません（Q4: A）。

## 2. 負荷の試験の場面（performance-validation で流す）

台本は `perf/k6/scenarios.js`、手順は `perf/README.md` です。この段では U2・U3 の場面を足しました。

| 場面 | 閾値（p95、同時 10） | 出典 |
|---|---|---|
| `preferencesGet` | 1 秒 | U2 の NFR6.1 |
| `preferencesSave`・`preferencesInvalid` | 1 秒 | U2 の NFR6.2 |
| `passwordChange` | 2 秒 | U2 の NFR6.3 |
| `passwordMismatch`・`passwordInvalid` | 1 秒 | U2 の NFR6.4 |
| `invite`・`invitationResend` | 5 秒 | U3 の NFR6.1 |
| `invitationList`（1ページ目と最後のページ）・`invitationCancel`・`registrationVerify` | 1 秒 | U3 の NFR6.3 |
| `registrationComplete` | 1 秒 | U3 の NFR6.4 |
| `registrationInvalid`・`registrationRejected` | 1 秒 | U3 の NFR6.5 |
| `appearance`（U8 のコード生成で追加済み） | 300 ミリ秒 | U8 の NFR6.1 |

- どの場面にも、閾値の p95 に加えて `checks` の率 1 を置きます。状態コードの誤りの速い応答で合格にならないようにするためです。
- 登録の完了のトークンは、k6 の `setup` で管理者として招待を出し、使い捨ての環境の Mailpit の API（`http://mailpit:8025`）でメールの本文のリンクから取り出します（Q2: A）。トークン・リンク・本文は、ログにも結果にも出しません。k6 は `--network mastersmith-perf_default` で動かします。
- パスワードの変更の場面は、専用の仮の利用者（perf-pw01〜）を VU ごとに1人当て、前後のパスワードを交互に使います。
- 登録の完了と取り消しの場面は、既定で 100 回（`ITERATIONS`）流します。トークンは1回しか使えないため、招待も 100 件用意します。
- BR7.4 の経路は入れません（U3 の NFR 要件、`Unverified`）。
- 試験の後、アプリのログに `/register#token=` と `@example.com` が無いことを件数で確かめます。
- 測る間は配備したアプリを止めます。`caffeinate -i` を付けて流します（project.md の Testing Posture）。
- 使い捨ての環境を消す前に、確かめの結果（接続プールの待ちの時間切れの累計、成功の件数と監査の件数の一致）を見ます。

### この段で確かめること

```bash
node --check perf/k6/scenarios.js
for s in <場面の名前>; do
  docker run --rm --network none -v "$PWD/perf/k6:/scripts:ro" -e SCENARIO=$s \
    grafana/k6:2.3.0 inspect --include-system-env-vars /scripts/scenarios.js
done
```

すべての場面が読み込めて、scenarios と thresholds が上の表のとおりに出ることを確かめます。

## 3. この段では確かめない性能の目標

| 目標 | 持ち主 |
|---|---|
| 同時 10 件の p95（上の表のすべて） | performance-validation |
| 接続プールの余裕（U2 の NFR5.2、U3 の NFR5.3） | performance-validation |
| 規模（U2・U3 の scalability-requirements） | performance-validation |
| 指標・警報の名前（`http_server_requests_*`・`mastersmith.mail.send`） | observability-setup |

## Sources

- 各単位の `nfr-requirements/performance-requirements.md`・`nfr-design/performance-design.md`（`construction/u2-user-preferences/` 〜 `construction/u8-instance-appearance/`）
- 各単位の `code-generation-plan.md`（「Build and Test に引き継ぐこと」）・`unit-test-instructions.md`・`code-summary.md`
- `perf/k6/scenarios.js`・`perf/README.md`・`docker/perf/compose.yaml`
- `aidlc/spaces/default/memory/project.md`（Testing Posture）
- `construction/build-and-test/build-and-test-questions.md`（Q2・Q4）

## Assumptions & Open Questions

- 登録の完了と取り消しの場面の回数の既定 100 は、依頼者の決定の文言（招待を VU の数だけ出す）と違います。トークンは1回しか使えないため、10 件では p95 の意味が薄くなります。そのため、U3 の NFR6.4 の「流す回数以上の招待を用意する」に合わせました。承認の場で確かめます。
- Mailpit の API の応答の形は、E2E の部品（`frontend/e2e/support/mailpit.ts`）と同じ前提です。実際に動くかは、performance-validation の最初の実行で確かめます。
