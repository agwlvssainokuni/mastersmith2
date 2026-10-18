# 作る人の割り当て — business-data-views

この文書は、Bolt（1つ以上の作る単位を設計から統合まで通して動くものにする1回の作り込み）を、だれが作るかを書く。mob（同じ作業を数人で一緒に進める組）の組み方を決める文書だが、この Intent では人のチームは1つだけ。

## 割り当て

classic の範囲で Team Formation の段は無い。作業は依頼者1名と AI で行い（team.md の Way of Working）、Construction はこの場で単位を1つずつ作り、その都度依頼者が承認する（この段の答え「この場で1つずつ」、単位の持ち方 solo）。

| Bolt | 単位 | 作る担当 | レビューと承認 |
|---|---|---|---|
| B1 | U5 u5-carryover-tests | aidlc-developer-agent（AI） | 依頼者 |
| B2 | U1 u1-businessdata-read | aidlc-developer-agent（AI） | 依頼者 |
| B3 | U4 u4-a11y-carryover（前半） | aidlc-developer-agent（AI） | 依頼者 |
| B4 | U2 u2-businessdata-write | aidlc-developer-agent（AI） | 依頼者 |
| B5 | U3 u3-businessdata-screens（前半） | aidlc-developer-agent（AI） | 依頼者 |
| B6 | U3 u3-businessdata-screens（後半） | aidlc-developer-agent（AI） | 依頼者 |
| B7 | U4 u4-a11y-carryover（後半） | aidlc-developer-agent（AI） | 依頼者 |

- 設計の段（機能設計・NFR 要件・NFR 設計・基盤の設計）は、各段のリードの担当（アーキテクト・プラットフォームなど）が受け持ち、依頼者が承認する。
- `origin` への `git push` は依頼者自身が行う（AI はプッシュしない）。
- 複数のチームの割り当ての表（Program Board: 複数のチームの Bolt の受け持ちと依存を並べた表）は、チームが1つのため作らない。
