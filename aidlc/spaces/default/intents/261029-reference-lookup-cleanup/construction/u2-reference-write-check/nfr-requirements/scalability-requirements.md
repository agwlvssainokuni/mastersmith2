# 規模の要件 — U2 u2-reference-write-check

出典: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/functional-design/functional-spec.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/functional-design/rules.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`

## 出典

- 要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`（NFR2・NFR3）
- ストーリー `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/user-stories/stories.md`（AC3.2.7・AC3.5.1・AC3.5.2）
- この単位の機能設計（`rules.md` の BR1.2・BR4.1・BR4.2・BR4.7、`functional-spec.md` の 9節）
- 契約 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`（C5、ADR-003 は `inception/domain-design/decisions.md`）
- U1 の規模の要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-requirements/scalability-requirements.md` と U1 の追加の質問 F1: A（値のカラムは 10,001 文字に切って読み、10,000 文字を超えた値の行は「無い」とする）
- この段の「決まっていること」（接続・件数の読み取りを1回にまとめる・束ねるかは NFR 設計）
- `aidlc/spaces/default/memory/team.md`（(c) 件数の上限、(j) 接続の数）と `project.md` の学び（上限を下げた k6 の場面を置かず `*ConnectionUsageIT` で決定的に確かめる）

## ID の対応表

全体の表は `security-requirements.md` の「ID の振り方」にある。

| 上流の ID | この単位での扱い | この単位の ID |
|---|---|---|
| NFR2 性能 | 部分（読む量とメモリ） | NFR2.26〜NFR2.28 |
| NFR3 接続 | 当たる（接続の数と同時の数） | NFR3.21〜NFR3.23 |

## 要件

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR2.26 | 保存1回で読む行の数に上限を置く（`team.md` の (c)）。U2 は U1 の口の上限（問い合わせの上限と JDBC の取り出しの上限の両方で 201 行）をそのまま使い、自分で上限を緩めない。1要求の最大は「更新前の行 1 ＋ 必須の参照項目の参照先の数 × 201（参照先ごとに1回。BR4.7） ＋ 確かめる参照の数 × 201」 | 同じ参照先を指す2つの必須の参照項目で `countState` の問い合わせが1回だけ流れることと、参照先が 201 行を超えるときに取り出した行の数が 201 を超えないことを結合テストで確かめる | Code Generation |
| NFR2.27 | 参照先の値のカラムは、U1 の決定のとおり SQL の側で 10,001 文字に切って読み、10,000 文字（書き込みの値の上限）を超えた値の行は確かめで「無い」とする（U1 の F1: A）。そのため1回の `check` のメモリは最悪で約 201 行 × 10,001 文字（Java の文字列で約 4 MB）で、確かめは順に行い前の結果の行を持ち続けないため、1要求の確かめのメモリは参照の数によらず約 4 MB に収まる見込み。本文の上限は既存の 4 MiB のまま | 10,000 文字・10,001 文字の値を持つ参照先の境界の結合テスト（10,000 文字の値は保存でき、それを超える値の行は「無い」）。ヒープの見積もりの裏付けは U1 の NFR 設計の試しと合わせる | NFR 設計、Code Generation |
| NFR2.28 | 1回の保存で値の違う参照が複数あるときに、`check` を参照ごとに順に呼ぶか、1つの問い合わせ（IN の条件など）に束ねるかは、問い合わせの回数と読む行の数を NFR 設計で実測して決める（機能設計の 9節）。どちらでも読み取りの接続は1本、時間の上限は NFR3.24・NFR3.25 を守り、結果（項目の誤りの並び）は変えない（BR2.7） | NFR 設計で、参照 1・3・5 個の保存で両方の形の文の数と時間を捨ての試しで測り、決めた形を記録する | NFR 設計 |
| NFR3.21 | 1要求で借りる対象DB の読み取りの接続は 0 か 1 回、書き込みの接続は 0 か 1 回（BR4.1・BR1.2、NFR3、ADR-003）。読み取りの接続は保存前の段（⑩）で確かめる・読むものがあるときだけ借り、⑩ の読み取り（更新前の行・件数・確かめ）を同じ接続で順に行い、返してから書き込みの接続を借りる。2本を同時に持たない。確かめで拒否・失敗したときは書き込みの接続を借りない。参照の無い作成・更新・削除の接続の数は今と同じ | 既存の `BusinessDataConnectionUsageIT` と同じ形の結合テストで、参照が 0・1・3・5 個の作成、値の変わらない更新、値の変わった更新、拒否、時間切れのそれぞれについて、読み取りと書き込みの接続を借りた回数と、両方を同時に持った瞬間が無いことを決定的に数える（AC3.5.1・AC3.2.7） | Code Generation |
| NFR3.22 | 内部DB の接続を持ったまま対象DB を待たない（BR4.2）。権限の写しを取り終えて内部DB の接続を返してから ⑩ の読み取りの接続を借り、監査の書き込みは対象DB の接続を返した後に行う | 応答しない対象DB（テストの中で開いた `ServerSocket`）で保存前の確かめを待たせ、その間の内部DB の使用中の接続の数が 0 であることを確かめる（AC3.5.2） | Code Generation |
| NFR3.23 | 同時の数は、既存の対象DB の読み取りのプール（上限 既定 10。一覧・詳細・U1 と共有）と書き込みのプール（上限 5）に任せる。1要求が同時に持つ対象DB の接続は1本だけのため、詰まりは「同時の要求の数 > それぞれのプールの上限」のときだけ起き、借りる待ちの上限を超えると既存の 503 `BUSINESS_TARGET_DB_UNAVAILABLE` になる（保存しない）。読み取りのプールの上限を下げた k6 の場面は置かず、接続の数は NFR3.21 の結合テストで決定的に確かめる（`project.md` の学び）。書き込みのプールの上限に届く確かめは既存の `bizWritePoolLimit` のまま（`performance-requirements.md` の NFR2.23） | NFR 設計で置かない理由をこの形で確定する。Performance Validation の新しい場面で、読み取りと書き込みのプールの待ちの時間切れの累計が 0 であることを並べて記録する（使い捨てのアプリにだけ公開した metrics。`project.md` の学び） | NFR 設計、Performance Validation |

## 補足

- 参照の確かめで書き込みの接続を持つ時間は増えない（確かめは書き込みの接続を借りる前に終わる）。増えるのは読み取りの接続を持つ時間で、作成1回につき最大で「借りる1回 ＋ 件数・確かめの文」。
- 1つの表に置ける参照のカラムの数には上限を置かない（DSL のカラムの数の既存の上限が実際の上限）。数が多いときの時間は、保存前の段の全体の上限（`reliability-requirements.md` の NFR3.25、10 秒）で抑える。
