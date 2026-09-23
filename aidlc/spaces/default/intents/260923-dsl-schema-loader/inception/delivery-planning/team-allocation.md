# Team Allocation — dsl-schema-loader

Bolt（1回分の作りの区切り。設計から実装・テストまでを通し、動くものができて終わる）ごとの担当を示す。

この Intent の範囲（classic）ではチームの編成の段（Team Formation）を行っていない。開発は依頼者1名と AI で行い、レビューと承認は依頼者が行う（`aidlc/spaces/default/memory/team.md` の Way of Working）。このため、すべての Bolt を AI の開発担当（aidlc-developer-agent）が作る。mob（複数の担当が同じ作業に一緒に取り組む形）は、Construction の各段で必要な担当（アーキテクト・品質・セキュリティなど）を呼び込む形で行う。

| Bolt | 単位 | 作る担当 | 承認 |
|---|---|---|---|
| B1 DSL の定義 | U2 | aidlc-developer-agent（AI） | 依頼者 |
| B2 対象DB | U1 | aidlc-developer-agent（AI） | 依頼者 |
| B3 既定の DSL の生成 | U3 | aidlc-developer-agent（AI） | 依頼者 |
| B4 DSL の管理（基本） | U4 の Must | aidlc-developer-agent（AI） | 依頼者 |
| B5 DSL の管理（履歴） | U4 の Should | aidlc-developer-agent（AI） | 依頼者 |
| B6 DSL の管理画面 | U5 | aidlc-developer-agent（AI） | 依頼者 |

- チームは1つで、単位ごとに持ち主を分けない（1つの場で作る）。
- Bolt は並行して作らない（依存の順に1つずつ）。
- `origin` へのプッシュは依頼者が行う。AI はプッシュしない。
