# Performance Requirements — U2 利用者のプリファレンスとパスワードの変更（u2-user-preferences）

U2 の性能の要件です。要件定義の NFR5（接続の使い方）と NFR6（応答時間）を、この単位の3本の API（GET・PUT `/api/me/preferences`、POST `/api/me/password`、契約 C4）と、ログイン・更新の応答の広げ（契約 C3）に当てます。

出典の略号: NFR・FR は `aidlc/spaces/default/intents/260925-user-management/inception/requirements-analysis/requirements.md`、BR はこの単位の `construction/u2-user-preferences/functional-design/rules.md`、Q1・Q2 はこの段の `nfr-requirements-questions.md`、要点 n は同じファイルの「設計の要点（案）」の番号、前 U2 は `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u2-authentication/nfr-requirements/performance-requirements.md`。

## 前提

- 測る環境は、当面の配備先である開発者の PC 上のコンテナ（colima の VM、CPU 4）とし、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者）で測る（`aidlc/spaces/default/memory/project.md` の Testing Posture）。
- 負荷の想定は前の Intent と同じ、利用者 最大 50 名・同時 10 件の要求（前 U2 の NFR1.1）。
- パスワードのハッシュは bcrypt の cost 12 で、照合・ハッシュ1回 約 278 ms（`aidlc/spaces/default/intents/260923-colima-spec-up/inception/reverse-engineering/developer-scan.md` の記録）。照合1回のログインの同時 10 件の p95 は、CPU 4 で成功 940 ms・失敗 926 ms（`aidlc/spaces/default/intents/260923-colima-spec-up/construction/build-and-test/test-results.md`）。
- 測定の持ち主は、この Intent の流れにある performance-validation の段（k6）。Build and Test では、負荷の試験の手順書と場面の用意を引き継ぐ。

## 要件

| ID | 要件 | 確かめ方と持ち主の段 | 出典 |
|---|---|---|---|
| NFR6.1 | プリファレンスの取得（GET `/api/me/preferences`）は、同時 10 件の要求で p95 が 1 秒以内 | k6 で同時 10 件を繰り返し、`http_req_duration` の p95 を測る（performance-validation） | NFR6、Q1 A |
| NFR6.2 | プリファレンスの保存（PUT `/api/me/preferences`）は、同時 10 件の要求で p95 が 1 秒以内（成功・入力の誤りとも） | NFR6.1 と同じ（performance-validation） | NFR6、Q1 A |
| NFR6.3 | パスワードの変更の成功（204）は、同時 10 件の要求で p95 が 2 秒以内。今のパスワードの照合と新しいパスワードのハッシュで bcrypt を2回計算するため | k6 で成功の場面を同時 10 件で流し、p95 を測る。場面ごとに変更の前後のパスワードを交互に使い、仮の利用者を 10 名以上用意する（performance-validation） | NFR6、Q1 A、BR4.3 |
| NFR6.4 | パスワードの変更の今のパスワードの誤り（400 PASSWORD_CURRENT_MISMATCH）と入力の誤り（400 VALIDATION_FAILED）は、同時 10 件の要求で p95 が 1 秒以内。誤りは照合1回（入力の誤りは照合なし）のため、ログインと同じ重さに収まる | k6 で誤りの場面を同時 10 件で流し、p95 を測る（performance-validation） | NFR6、Q1 A、BR4.1、BR4.2 |
| NFR6.5 | ログイン（POST `/api/auth/login`）とトークンの更新（POST `/api/auth/session/refresh`）の応答に4つの値を足しても、前の Intent の目標（同時 10 件で p95 1 秒以内）を保つ。4つの値は応答を作るときの既存の利用者の読み取りから取り、内部DB への問い合わせを増やさない | 既存の k6 のログイン・更新の場面を流し直す（performance-validation）。問い合わせを増やさないことは NFR 設計・コード生成で確かめる | 前 U2 の NFR1.1・NFR1.2、BR6.1、要点 3 |
| NFR6.6 | bcrypt の cost は既定の 12 のまま変えない（照合1回 100〜500 ms の前の目標のまま）。NFR6.3 を満たすために cost を下げない | 設定の既定値の確認（`backend/src/main/resources/application.yaml` の `mastersmith.auth.password.bcrypt-cost`） | 前 U2 の NFR1.3、Q1 A（C を選ばない）、要点 4 |
| NFR5.1 | パスワードの照合と新しいパスワードのハッシュはトランザクションの外で計算し、内部DB の接続を持つのは、保存されたハッシュの読み取りと新しいハッシュの書き込みの短い間だけにする（bcrypt の約 278 ms の間、接続を持たない）。細部は NFR 設計で決める | NFR 設計で作りを決め、コード生成のテストでトランザクションの境界を確かめる。接続の待ちは NFR5.2（`reliability-requirements.md`）の負荷の試験で見る | NFR5、要点 5 |

## 測り方の決まり

- 場面は、プリファレンスの取得・保存、パスワードの変更の成功・今のパスワードの誤り・入力の誤りに分け、場面ごとに p95 を判定する（1つの場面の遅さを、ほかの場面の速さで薄めない）。
- 長い試験は `caffeinate -i` を付けて流し、PC のスリープで結果が崩れるのを防ぐ（`project.md` の Testing Posture）。
- k6 は同じ VM の CPU を分け合うため、値に k6 の分が混ざりうる。この扱いは前の Intent と同じとし、結果に明記する。
- 目標に届かないときは、目標を緩めて「満たした」ことにはしない（`project.md` の Testing Posture）。原因を確かめ、依頼者に相談する。

## 上流との差

| ID | 上流 | 上流の記載 | この段の要件 | 理由と扱い |
|---|---|---|---|---|
| P-D1 | 要件 NFR6（`inception/requirements-analysis/requirements.md`） | 「プリファレンス・パスワードの変更・登録の完了の API は、既存の API と同じく 95 パーセンタイルで 1 秒以内 [assumption]」 | パスワードの変更の成功は p95 2 秒以内（NFR6.3）。今のパスワードの誤りと入力の誤り、プリファレンスの取得・保存は 1 秒以内のまま（NFR6.1・NFR6.2・NFR6.4） | 変更の成功は bcrypt を2回計算し、照合1回のログインでも同時 10 件の p95 が 940 ms（余裕 60 ms）のため、1 秒は届かない見込み（約 1.9 秒、未測定）。依頼者の決定（Q1 A）で、bcrypt の回数に合わせた目標にした。要件の文書は書き換えない。登録の完了の API は U3 の段で扱う |
