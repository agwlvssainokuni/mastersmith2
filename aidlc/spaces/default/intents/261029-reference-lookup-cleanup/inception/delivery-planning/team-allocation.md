# Bolt の担当 — reference-lookup-cleanup

Bolt は、1 つ以上の作る単位（Unit of Work）を、設計から統合まで通して動くものにする 1 回の作り込みのこと。この文書は、各 Bolt を誰が作り、誰が確かめて承認するかを書く。

出典: Bolt の計画 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/delivery-planning/bolt-plan.md`、単位 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/units-generation/unit-of-work.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/units-generation/unit-of-work-dependency.md`、要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`、部品 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/components.md`、チームの進め方 `aidlc/spaces/default/memory/team.md`（依頼者 1 名と AI で開発し、レビューと承認は依頼者が行う）。

classic の範囲のため、チームの編成の段（Team Formation）は行っていない。チームは 1 つで、複数のチームの間の調整表（Program Board）は無い。

## 担当

| Bolt | 単位 | 作る担当 | 確かめと承認 |
|---|---|---|---|
| B1 | U1（前半） | aidlc-developer-agent（AI） | 依頼者 |
| B2 | U1（後半） | aidlc-developer-agent（AI） | 依頼者 |
| B3 | U2 | aidlc-developer-agent（AI） | 依頼者 |
| B4 | U3 | aidlc-developer-agent（AI） | 依頼者 |
| B5 | U4 | aidlc-developer-agent（AI） | 依頼者 |
| B6 | U5 | aidlc-developer-agent（AI）。コンテナの作り直しは依頼者の了承の後 | 依頼者 |

- すべての Bolt を AI（aidlc-developer-agent）が作り、依頼者が確かめて承認する。設計の段は、段ごとのリードの担当（アーキテクトなど）が受け持つ。
- `origin` への `git push` は依頼者自身が行う。AI はプッシュしない（team.md）。
- コミットは、実行の前に依頼者に提案して承認を得る（`project.md` の Change Control）。
- 同時に動かす担当の数を増やしすぎない（利用の上限に達しうる。`project.md` の学び）。Bolt は 1 つずつ順に作る（DQ4: A）。

## Sources

- この段の答え: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/delivery-planning/delivery-planning-questions.md`（DQ4: A）
- Bolt の計画: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/delivery-planning/bolt-plan.md`

## Assumptions & Open Questions

None.
