# Performance Design — U3 管理画面のアクセス制御（u3-access-control）

U3 の性能の要件（`performance-requirements.md` の NFR1.1〜NFR1.3）を満たす設計。処理の流れは U3 の `functional-spec.md`（WF1・WF2）、技術は U3 の `tech-stack-decisions.md` に従う。安全・拡張性・信頼性・観測性の要件（`security-requirements.md`・`scalability-requirements.md`・`reliability-requirements.md`・`observability-requirements.md`）は各設計書で扱う。設計の方針の確定回答は `nfr-design-questions.md` にある。`contract-summary.md` は、本ワークフローで Contract Design を行わないため無い。

## 1. 確認用 API の予算（NFR1.1）

同時 10 件の要求で 95% が 300 ミリ秒以内（成功・403 とも）。

| 手順 | 予算の目安 | 備考 |
|---|---|---|
| U1 の共通の処理 | 20 ms | U1 の `nfr-design/performance-design.md` |
| U2 の要求ごとの処理（トークンの検証と利用者の読み取り） | 50 ms | U2 の `nfr-design/performance-design.md` 3章 |
| U3 のパスの照合と管理者の判定 | 1 ms 未満 | メモリの中の比較だけ |
| 確認用 API の本体（204） | 1 ms 未満 | DB を使わない |
| 403 のときの出来事の通知と記録 | 100 ms 以内 | 2章 |
| 合計 | 成功 約 70 ms、403 約 170 ms | 300 ms に余裕がある |

## 2. アクセスの判定と拒否の記録（NFR1.2、NFR1.3）

- **DB の問い合わせを増やさない（NFR1.2）**: 管理者かどうかは、U2 が要求ごとに読んだ AuthenticatedUser の値を使う。U3 は DB を読まない。テストで、確認用 API の1要求で発行される SQL が U2 の利用者の読み取り1回だけであることを確かめる。
- **拒否の記録（NFR1.3）**: 403 と期限切れ以外の 401 では、出来事を同じスレッドで知らせ、U4 がその場で自分のトランザクションで記録する（U4 の決まり 1.3。内部DBの更新を伴わない出来事のため）。U4 の書き込みの目標（95% が 50 ms 以内）により、応答の遅れは 100 ms 以内に収まる。
- 危険: 内部DBが遅いと、拒否の応答も遅れる。U1 の設計の接続を借りる待ち（5 秒）と問い合わせの上限（10 秒）が最悪の場合の上限になる。記録の失敗は応答を変えない（`reliability-design.md`）。
- 測り方: Performance Validation で、非管理者の同時 10 件の要求の 403 の 95 パーセンタイルを測る。
