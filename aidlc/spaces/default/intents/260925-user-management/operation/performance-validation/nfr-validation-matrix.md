# 性能の目標の判定（nfr-validation-matrix）

Build and Test から引き継いだ、この段が持ち主の 21 件の判定です（`construction/build-and-test/build-and-test-summary.md` の Unverified のうち performance-validation の分）。実測は `test-results.md` です（2026-09-29、使い捨ての環境、同時 10）。

## 1. 判定の一覧

| ID | 目標 | 実測 | 判定 | 根拠 |
|---|---|---|---|---|
| U2-NFR6.1 | `GET /api/me/preferences` の同時 10 件で p95 1 秒以内 | p95 1.53 ms | Met | `test-results.md` 2節 |
| U2-NFR6.2 | `PUT /api/me/preferences` の同時 10 件で p95 1 秒以内（成功・入力の誤り） | 成功 2.01 ms・誤り 3.69 ms | Met | 2節 |
| U2-NFR6.3 | パスワードの変更の成功の同時 10 件で p95 2 秒以内 | p95 1.79 s（余裕 0.21 秒） | Met | 2節 |
| U2-NFR6.4 | 今のパスワードの誤り・入力の誤りの同時 10 件で p95 1 秒以内 | 誤り 905 ms（余裕 95 ms）・入力の誤り 3.35 ms（流し直し） | Met | 2節・3節 |
| U2-NFR6.5 | ログインと更新の応答を広げた後も p95 1 秒以内、問い合わせを増やさない | ログイン 904 ms（余裕 96 ms）・更新 2.32 ms。問い合わせの数は Build and Test で確かめ済み | Met | 2節、`build-and-test-summary.md` |
| U2-NFR5.2 | 同時 10 件で hikaricp の待ちの時間切れ 0、待ちの最大が 5 秒より十分小さい、成功と監査の件数が一致 | 時間切れ 0・待ちの最大 12.5 ms・`PASSWORD_CHANGED` の成功 398 と誤り 792 が k6 と一致 | Met | 5節・6節 |
| U2-NFR5.3 | 同時 10 件で最大 20 本、上限 30 に収まる | 時間切れ 0・待ちの最大 12.5 ms（上限 30 の中で借りられた）。使用中の本数の最大は測っていない | Met | 5節 |
| U2-NFR6.7 | 想定の規模を1台で処理し NFR6.1〜6.4 を満たす | 1台（CPU 4・2g）で U2 の全場面が閾値を満たした | Met | 2節 |
| U3-NFR6.1 | 招待・送り直しは SMTP を含めて同時 10 件で p95 5 秒以内 | 招待 29.4 ms・送り直し 20.9 ms（受け手は同じ VM の Mailpit） | Met | 2節 |
| U3-NFR6.2 | 受け手が応答しない・拒むときも FAILED で応答、1件で 5 秒以内 | 応答しない 3.02〜3.08 秒（`TIMEOUT`）・止まっている 0.003〜0.075 秒（`CONNECTION_FAILED`）。どちらも FAILED | Met | 4節 |
| U3-NFR6.3 | 一覧・取り消し・リンクの確かめの同時 10 件で p95 1 秒以内 | 一覧 90.1・91.2 ms、取り消し 5.12 ms、確かめ 1.77〜2.24 ms | Met | 2節 |
| U3-NFR6.4 | 登録の完了の成功の同時 10 件で p95 1 秒以内 | p95 931 ms（余裕 69 ms、100 回） | Met | 2節 |
| U3-NFR6.5 | 入力の誤りとリンクの拒否の同時 10 件で p95 1 秒以内（BR7.4 は対象外） | 入力の誤り 2.96 ms・拒否 2.19・2.37 ms | Met | 2節 |
| U3-NFR5.2 | 登録の完了は1つのトランザクションで排他して読み作成する。接続の待ちは NFR5.3 で確かめる | 作りは Build and Test で通過。`registrationComplete` の後も時間切れ 0・待ちの最大 3.5 ms。利用者 100 名が重なりなく作られた | Met | 5節・6節 |
| U3-NFR5.3 | 同時 10 件で hikaricp の待ちの時間切れ 0、待ちの最大が 5 秒より十分小さい、成功と監査の件数が一致 | 時間切れ 0・待ちの最大 12.5 ms・招待・送り直し・取り消し・登録の完了・拒否の監査が k6 と一致 | Met | 5節・6節 |
| U3-NFR5.4 | 同時 10 件で最大 20 本、上限 30 に収まる | U2-NFR5.3 と同じ | Met | 5節 |
| U3-NFR6.7 | 想定の規模を1台で処理し NFR6.1・6.3〜6.5 を満たす | 1台で U3 の全場面が閾値を満たした | Met | 2節 |
| U8-NFR6.1 | `GET /api/appearance` の同時 10 件で p95 300 ミリ秒以内 | p95 0.96 ms | Met | 2節 |
| U5-前提 | 一覧・取り消し p95 1 秒、招待・送り直し p95 5 秒（同時 10 件） | U3-NFR6.1・6.3 と同じ | Met | 2節 |
| U6-前提 | リンクの確かめ・登録の完了の成功とも同時 10 件で p95 1 秒 | U3-NFR6.3・6.4 と同じ | Met | 2節 |
| U7-NFR6.4（API の時間） | 取得・保存 p95 1 秒、変更の成功 p95 2 秒、誤り p95 1 秒（同時 10 件） | U2-NFR6.1〜6.4 と同じ | Met | 2節 |

- 21 件すべて Met です。閾値は出典の値のままで、緩めていません。
- 判定の元は k6 の `http_req_duration` です。アプリの指標 `http_server_requests` は p95 を出せないため使っていません（Observability Setup の `dashboards.md` 2節）。

## 2. 注意と残る危険

- **余裕の小さい目標**: bcrypt を計算する API の p95 は目標まで 69〜96 ms です（ログイン・今のパスワードの誤り・登録の完了）。パスワードの変更は 2 回の計算で余裕 0.21 秒です。
  - CPU の上限（4）を下げる、VM の CPU を減らす、同時の数が 10 を超えると、届かなくなりえます。
  - 配備先を決めるときに、CPU の割り当てとあわせて見直します（feedback-optimization に引き継ぐ）。
- **受け手の時間**: 招待・送り直しの時間は、同じ VM の Mailpit への送信です。実在の受け手では、SMTP の時間（時間切れは 3 秒）が加わります。配備先が決まり実在の受け手を使うときに、測り直します。
- **k6 と Mailpit の CPU**: 同じ VM の CPU を分け合っているため、アプリだけの時間はこれより短い見込みです。
- **運用の中での判定**: 配備したアプリでの p95 の運用の中での判定は、この段の外です（`slo-config.md`、feedback-optimization）。

## 3. 承認の場で決めたこと

- `test-results.md` 6節の食い違い：形の誤ったトークンでの登録の完了の拒否は、監査に残ります。承認済みの次の2つの文書は、残らないと読める書き方です。依頼者の決定（「記録だけ」）で、2つの文書は書き換えず、`test-results.md` 6節を正とします。
  - Observability Setup の `log-queries.md` 3節
  - Incident Response の `runbooks.md` の RB-17

## Sources

- `operation/performance-validation/test-results.md`・`load-test-plan.md`・`performance-validation-questions.md`
- `construction/build-and-test/build-and-test-summary.md`（Unverified の一覧）
- `construction/u2-user-preferences/nfr-requirements/performance-requirements.md`・`reliability-requirements.md`・`scalability-requirements.md`
- `construction/u3-invitation/nfr-requirements/performance-requirements.md`・`reliability-requirements.md`・`scalability-requirements.md`
- `construction/u8-instance-appearance/nfr-requirements/performance-requirements.md`、`construction/u5-invitation-ui/nfr-requirements/performance-requirements.md`、`construction/u6-registration-ui/nfr-requirements/performance-requirements.md`、`construction/u7-preferences-ui/nfr-requirements/performance-requirements.md`
- `operation/observability-setup/dashboards.md`・`slo-config.md`

## Assumptions & Open Questions

None.
