# 担当の割り当て — internal-db-postgresql

Bolt は、1つ以上の作る単位（Unit of Work）を、設計から統合まで通して動くものにする1回の作り込みのこと。この文書は、各 Bolt を誰が作るかを書く。mob（複数の担当が一緒に1つの作業を進める組）は組まない。

この Intent の範囲（classic）ではチームの編成の段を行っていないため、すべての Bolt を開発の担当（aidlc-developer-agent、AI）が作り、依頼者が承認とレビューを行う（`team.md` の Way of Working）。担当が1つのため、複数のチームの並び（Program Board）は作らない。

| Bolt | 単位 | 作る担当 | 設計の段の主な担当 | 依頼者が見るもの |
|---|---|---|---|---|
| B1 | U1 u1-internal-db-platform | aidlc-developer-agent | aidlc-architect-agent（機能設計・NFR）、aidlc-quality-agent（テストの土台） | 捨ての試しの結果・機能設計・コード生成の計画・確認の場 |
| B2 | U2 u2-portable-sql | aidlc-developer-agent | aidlc-architect-agent | 機能設計・コード生成の計画・確認の場 |
| B3 | U3 u3-postgres-behaviour-tests | aidlc-developer-agent | aidlc-quality-agent | 機能設計・コード生成の計画・確認の場 |
| B4 | U4 u4-local-runtime-docs | aidlc-developer-agent | aidlc-architect-agent | compose と文書・確認の場 |

- 捨ての試し（Q3: A）は、U1 の機能設計の前に指揮役がまとめて行い、結果を U1 の機能設計に渡す。
- 担当を同時に多く動かさない（`project.md` の学び。利用の上限で止まることがある）。

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/delivery-planning/bolt-plan.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/units-generation/unit-of-work.md`
- `aidlc/spaces/default/memory/team.md`（Way of Working）
