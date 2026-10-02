# Code Generation の質問 — U2 ページ送りの共通化（u2-shared-paging、Bolt B2 の前半）

## 計画の前の確かめ（依頼者の決定）

計画の9節の4問は、依頼者が答えた（2026-10-02）。4問とも A。

- Q-A B2 の統合の形: A（単位ごとの squash。U2 の最後のコミットが単独で verify を通ることを確かめる）
- Q-B 要求の行の上限 8KB の確かめ方: A（一時の結合テストで確かめて記録し、ファイルは消す）
- Q-C サーバーと画面の `PAGE_SIZE` の一致: A（両側をテストで 20 に固定し、説明文で互いの場所を示す）
- Q-D 画面の文言（訳の鍵）を共通に移すか: A（移さない）

## Plan Approval

`code-generation-plan.md`（埋め込みの Testing Contract を含む）と `unit-test-instructions.md` のとおりに、U2 のコードを生成してよいかを確かめる。

[Approval Fingerprint]: sha256:v3:0a7742cdaef456223c31702362d5a51f43f3ff13140aaa0f1fbc8db9a48ed4b3
[Planned Source]: a5d172c5d9e7b0489b6a86073f968a984d8c0f6e1934cc2202cc5aba6dad6a6f

- Approve Plan
- Request Changes

[Answer]: Approve Plan
