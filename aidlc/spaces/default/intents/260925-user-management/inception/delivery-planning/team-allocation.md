# Team Allocation — user-management

Bolt は、1つ以上の単位をまとめて作り終え、動くものができる1回の作る区切りのこと（一覧は `bolt-plan.md`）。mob は、同じものを一緒に作る小さな組のこと。

この Intent の範囲（classic）には Team Formation の段が無い。チームは依頼者1名と AI の1つだけで（`team.md` の Way of Working、この段の Q4: A）、すべての Bolt を同じ組が1つずつ作る。

| Bolt | 単位 | 作る担当 | 承認 |
|---|---|---|---|
| B1 メールの土台 | U1 | AI（開発担当 aidlc-developer-agent） | 依頼者 |
| B2 利用者の設定 | U2 | AI（開発担当 aidlc-developer-agent） | 依頼者 |
| B3 招待と登録の完了 | U3 | AI（開発担当 aidlc-developer-agent） | 依頼者 |
| B4 見た目と表示の土台 | U8・U4 | AI（開発担当 aidlc-developer-agent） | 依頼者 |
| B5 画面 | U5・U6・U7 | AI（開発担当 aidlc-developer-agent） | 依頼者 |

- 設計の段とコード生成の各段で、依頼者が承認する（段ごとの承認）。
- `origin` への `git push` は依頼者が行う（`team.md`）。
- 複数のチームで単位を分けて持つ形は取らない。
