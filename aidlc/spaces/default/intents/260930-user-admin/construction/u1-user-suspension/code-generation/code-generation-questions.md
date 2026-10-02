# Code Generation の質問 — U1 利用停止の土台（u1-user-suspension、Bolt B1）

## 計画の前の確かめ（依頼者の決定）

計画の9節の3問は、依頼者が答えた（2026-10-02）。

- Q-A 待ちの確かめの手伝いを `TestInvitationBarrier` と1つにまとめるか: A（まとめない。`auth/testsupport/H2SessionWaits` に置く）
- Q-B 漏えいのテストの形: A（新しい `AuthSuspensionSecretLeakIT` を足し、既存の `AuthSecretLeakIT` は変えない）
- Q-C 決定の外で TRACE にメールアドレスを出す既存の箇所: B（`LoginRequest`・`PasswordVerification` の `toString` を B1 で伏せ字にし、`WebSecretTypesTest` も直す。`UserAccountService` の `String` の引数の2つは記録して後の Intent へ）

## Plan Approval

`code-generation-plan.md`（埋め込みの Testing Contract を含む）と `unit-test-instructions.md` のとおりに、B1 のコードを生成してよいかを確かめる。

[Approval Fingerprint]: sha256:v3:b66129e846750697dabffe63c901f39541850a9fcebd22a69eb48894a80f9177
[Planned Source]: d94c080744d900ac9ce8742eae5f9abf66c907a4d301637661285977abfe8bb5

- Approve Plan
- Request Changes

[Answer]: Approve Plan
