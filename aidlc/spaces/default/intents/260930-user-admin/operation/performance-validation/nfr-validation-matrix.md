# 性能の目標の判定（nfr-validation-matrix）

Build and Test から引き継いだ、この段が持ち主の 16 件（`construction/build-and-test/build-and-test-summary.md` の Unverified のうち performance-validation の分）と、Observability Setup から移された警報3件の判定です。実測は `test-results.md`（2026-10-03、使い捨ての環境、同時 10）です。閾値は出典の値のままで、緩めていません。正とする値は k6 の値（クライアント側）です。

## 1. Build and Test から引き継いだ 16 件

| ID | 目標 | 実測 | 判定 | 根拠 |
|---|---|---|---|---|
| U3-NFR5.1 | 一覧（利用者 1,000 名）の同時 10 件で p95 1 秒以内。区分 a〜d ごと | a 3.6 ms・b 4.1 ms・c 6.1 ms・d 3.0 ms。checks の失敗 0 | Met | `test-results.md` 2節 |
| U3-NFR5.3 | 氏名と言語の変更の同時 10 件で p95 1 秒以内（成功 204・入力の誤り 400） | 成功 3.1 ms・入力の誤り 3.9 ms | Met | 2節 |
| U3-NFR5.4 | 5つの操作を同時 10 件で操作ごとに p95 1 秒以内、204 の率 1（409 BUSY・NO_CHANGE と 5xx が 0 件） | 印を付ける 7.4・外す 7.3・止める 6.9・解く 5.8・戻す 4.8 ms（各 100 回）。checks 500 / 0 | Met | 2節 |
| U3-NFR5.5 | 止める操作の悪い側（未無効 100 件・無効 1,000 件）で p95 1 秒以内 | p95 106.9 ms（100 名を1回ずつ）。checks 200 / 0 | Met | 2節 |
| U1-NFR5.2 | まとめての無効化を含む止める操作が、未無効 100 件・無効 1,000 件の悪い側で p95 1 秒以内 | U3-NFR5.5 と同じ場面で 106.9 ms | Met | 2節 |
| U3-NFR5.6 | 目標の負荷で BUSY を例外にせず p95 に混ぜない（BUSY が1件でも出たら合格としない） | 上限 30 のすべての場面と上限 10 の (A)・(B) で BUSY 0 件（checks の失敗 0、アプリのログ 0 件） | Met | 2節・4節 |
| U3-NFR6.2 | 5つの操作と一覧を同時 10 件で流しても接続プール（上限 30）が尽きず、監査が欠けない | `userAdminPool` の前後で時間切れの累計 0・待ちの最大 4.9 ms・500 が 0 件・checks 228,653 / 0。5つの操作の組 377 回と監査の5つの種類の `SUCCESS` の件数が一致 | Met | 3節・5節 |
| U3-NFR6.3 | 上限 10 で (A) 同時 5 は時間切れの累計 0 で件数が一致、(B) 同時 10 は2本目を借りる待ちが出る（待ちの最大が 0 より明らかに大きい、または時間切れの累計が 1 以上） | (A) 時間切れ 0・待ちの最大 3.0 ms・件数一致（満たした）。(B) 5分48秒・操作ごとに 4,000 回で、時間切れ 0・待ちの最大 6.0 ms（上限 30 で待ちが起こりえない場面の 4.9〜9.0 ms と同じ水準）・待ちの数は 22 回すべて 0・欠けた監査 0（**待ちが出なかった**） | **Unverified**（当初の判定は Not Met。依頼者の決定で Unverified とし後の Intent へ持ち越す。3.1節） | 4.1節・4.2節 |
| U1-NFR5.1b | ログインと更新の既存の目標（同時 10 件で p95 1 秒）を変えない | ログイン 939.6 ms（余裕 60 ms、最大 1,043 ms）・更新 2.8 ms | Met | 2節 |
| U4-NFR5.1b | 更新の API の既存の目標（p95 1 秒）を当てる | 更新 2.8 ms | Met | 2節 |
| U1-NFR6.1b | 接続プールの使い方（上限 30）が変わらないことを、既存のログインの場面の負荷で確かめる | `loginSuccess` の前後で時間切れ 0・待ちの最大 8.0 ms・待ちの数 0（前の Intent は時間切れ 0・最大 12.5 ms） | Met | 3節 |
| U2-NFR5.1b | 招待の一覧の既存の目標（同時 10 件で p95 1 秒）を保つ | 1ページ目 2.7 ms・最後のページ 2.6 ms（招待中 45 件、最後のページ 3） | Met | 2節 |
| U1-NFR5.4 | 想定の規模（利用者 50 名・同時 10 件、未無効の行 約 80 件）を1台で処理 | 1台（CPU 4・2g）で、利用者 1,142 名・未無効 100 件の悪い側を含む全場面が閾値を満たした | Met | 2節 |
| U3-NFR5.8 | 想定の規模を1台で処理し、1,000 名で目標を満たす | 1,000 名で一覧の4区分と NFR5.3〜NFR5.5 が閾値を満たした | Met | 2節 |
| U5-NFR5.1 | 「利用者の管理」を開いてから一覧の1ページ目まで 2 秒以内（5回すべて） | この段では測らない（Q4: A）。参考: Build and Test の E2E（WAR、空に近い内部DB）で最大 103 ms。一覧の API は 1,000 名で p95 3.6 ms | Unverified（持ち主: feedback-optimization、配備先が決まったとき） | 質問 Q4、`build-and-test-summary.md` |
| U5-NFR5.2 | 「次へ」から2ページ目まで 1.5 秒以内（5回すべて） | 同上。参考: E2E で最大 80 ms（API の時間を含まない） | Unverified（持ち主: feedback-optimization） | 同上 |

- 16 件の内訳: **Met 13 件・Unverified 3 件（U3-NFR6.3 の (B)、U5-NFR5.1・NFR5.2）**。U3-NFR6.3 は当初 Not Met と判定し、依頼者の決定（3.1節）で Unverified にした。

### 共同の持ち主の項目（16 件の外）

| ID | 目標 | 実測 | 判定 | 根拠 |
|---|---|---|---|---|
| U3-NFR5.11 | SLO は決めず、基準の値を記録する（observability-setup と共同） | 基準の値として 2節の k6 の p95 を記録した（一覧 3.0〜6.1 ms、氏名と言語の変更 3.1〜3.9 ms、5つの操作 4.8〜7.4 ms、止める悪い側 106.9 ms）。サーバー側の `http_server_requests` の p95 は、手元の監視を止めて測ったため並べていない | Met（基準の値の記録） | 2節 |

## 2. Observability Setup から移された警報

| 警報 | 目標 | 実測 | 判定 | 根拠 |
|---|---|---|---|---|
| `ms-pool-pending` | U3 の失敗（上限 10 の (B)）で鳴る | (B) の始まりから終わりの後 5 分まで、30 秒ごとの 22 回すべて `inactive`。待ちの数は 0 のまま | Unverified（失敗が起きなかった） | `test-results.md` 4.3節 |
| `ms-error-logs` | 同上 | 22 回すべて `inactive`。ERROR のログ 0 件 | Unverified（同上） | 4.3節 |
| `ms-audit-fail` | 同上 | 22 回すべて `inactive`（NoData）。監査の失敗のログ 0 件 | Unverified（同上） | 4.3節 |

- 外部エクスポートと手元の監視は働いていました（Loki・Prometheus に値が届いた）。鳴らなかったのは、警報の条件（待ち・ERROR・監査の失敗）が起きなかったためで、警報の不具合を示すものではありません。
- あわせて、409 `USER_ADMIN_BUSY` の2行（L3・L4）の `traceId` での結び付きも、BUSY が起きなかったため Unverified です（Q3: A のとおり feedback-optimization へ渡す）。

## 3. Not Met と Unverified の扱い

### 3.1 U3-NFR6.3 の (B)（当初 Not Met → 依頼者の決定で Unverified）

- 判定の条件「(B) で2本目を借りる待ちが出る」を満たしませんでした。出典の要件は「(B) で待ちが出なければ2本目を借りていない、として見積もりを見直す」としています。
- 見立て（確かめていない）: 見積もりの誤りではなく、場面の形が上限に届いていない見込みです。1つの組の時間の大半が準備のログインの失敗の bcrypt（約 0.5 秒）と k6 の順の送信で、5つの操作は 1 件 1 ms 前後のため、サーバーの中で同時に動く操作が上限 10 本を使い切る数（5〜6 件）に届かなかったと見ています。手元の監視の使用中の本数（1 分ごと）の最大は 2 でした。借りた回数は要求1件あたり約 3 回で、監査の記録で2本目を借りていることとは矛盾しません。
- 決まりどおり、設定や台本を変えて流し直していません。考えられる手（どれも依頼者の決定が要る）:
  - A. 準備のログインの失敗を含まない形（例: 止めると解くだけをくり返す）で、上限をさらに下げて（例: 上限 4〜6）流し直す。台本か設定の変更が要る
  - B. 見積もりの確かめを結合テストの待ち合わせ（`project.md` の Testing Posture の LongSupplier の方式）に任せ、この段の判定は Not Met のまま記録する
  - C. Unverified として feedback-optimization へ渡す
- この判定が Not Met のため、2節の警報3件も、失敗を起こせないまま Unverified になっています。A を選べば、同じ流し直しで警報も確かめられる見込みです。
- **依頼者の決定（2026-10-03、承認の場の前）**: C。上限に届かない負荷では見積もりを確かめられない（`project.md` の Testing Posture の学び）ため、Not Met ではなく Unverified とし、後の Intent へ持ち越す。持ち越す作業: 準備のログインの失敗を含まない形に台本を直し、上限を下げて (B) と警報3件（2節）を確かめ直す。

### 3.2 U5-NFR5.1・NFR5.2（Unverified）

- Q4: A のとおり、配備先が決まったときに feedback-optimization で本番の条件で判定します。目標は緩めていません。

## 4. 注意と残る危険

- **ログインの余裕**: ログインの p95 は 939.6 ms で、目標まで 60 ms です（前の Intent は 904 ms・余裕 96 ms）。停止の判定を足した影響か、ぶれかは切り分けていません。CPU の上限を下げる・同時の数が 10 を超えると、届かなくなりえます。配備先を決めるときに見直します（feedback-optimization）。
- **`hikaricp.connections.acquire` の単位**: 外部エクスポートの有無で `/actuator/metrics` の単位が秒とミリ秒で変わりました（`test-results.md` 3節）。手順書に従って値を読むときは、`baseUnit` を必ず見ます。手順書（`perf/README.md`）への注意書きは、依頼者の決定で後の Intent で足す。
- **手順 0 との差**: イメージを作り直さず配備と同じ `mastersmith:local` を使ったこと、k6 の生データを一時の置き場に置いて残さなかったことは、依頼者の決定で差として受け入れた。
- **招待の一覧の件数**: 今回の招待中は 45 件で、前の Intent（35,168 件）より少ない条件です。目標は件数を決めていないため Met としました。
- **k6・lgtm の CPU**: 同じ VM の CPU を分け合っているため、アプリだけの時間はこれより短い見込みです。

## Sources

- `operation/performance-validation/test-results.md`・`load-test-plan.md`・`performance-validation-questions.md`
- `construction/build-and-test/build-and-test-summary.md`（Unverified の一覧）・`performance-test-instructions.md`
- `construction/u3-user-admin-api/nfr-requirements/performance-requirements.md`・`reliability-requirements.md`・`scalability-requirements.md`
- `construction/u1-user-suspension/nfr-requirements/security-requirements.md`（NFR5.2・NFR5.4）
- `construction/u5-user-admin-ui/nfr-requirements/performance-requirements.md`
- `operation/observability-setup/alarms.md`（2節）・`log-queries.md`（2節）・`slo-config.md`

## Assumptions & Open Questions

- (B) で待ちが出なかった理由の見立ては確かめていません。
