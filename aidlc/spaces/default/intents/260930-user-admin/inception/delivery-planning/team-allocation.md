# Team Allocation — user-admin

この文書は、どの Bolt を誰が作るかを示す。Bolt は、1つ以上の単位をまとめて作り終え、動くものができる1回の作る区切りのこと。mob（同じ作業を一緒に進める担当の組）は、この Intent では組まない。

出典: `bolt-plan.md`、`aidlc/spaces/default/intents/260930-user-admin/inception/practices-discovery/team-practices.md`、この段の答え `delivery-planning-questions.md`。

## 体制

- 依頼者1名と AI で開発する。レビューと承認は依頼者が行う（`team.md`）。
- 範囲は classic のため、チームの編成の段（Team Formation）は無い。すべての Bolt を開発担当（aidlc-developer-agent、AI）が作る。
- チームは1つのため、複数のチームの作業の並びを合わせる表（Program Board）は作らない。

## Bolt と担当

| Bolt | 単位 | 作る担当 | 承認 | 手伝う担当 |
|---|---|---|---|---|
| B1 利用停止の土台 | U1 | aidlc-developer-agent | 依頼者 | aidlc-architect-agent（設計の段） |
| B2 共通の土台（ページ送りと 403） | U2・U4 | aidlc-developer-agent | 依頼者 | aidlc-design-agent（403 の表示） |
| B3 一覧と氏名・言語の変更 | U3 前半 | aidlc-developer-agent | 依頼者 | aidlc-architect-agent（設計の段） |
| B4 管理の操作と最後の管理者の保護 | U3 後半 | aidlc-developer-agent | 依頼者 | aidlc-architect-agent（同時性）、aidlc-quality-agent（待ち合わせのテスト） |
| B5 利用者の管理の画面 | U5 | aidlc-developer-agent | 依頼者 | aidlc-design-agent（画面・アクセシビリティ） |

手伝う担当は、各段の定義が呼ぶときだけ加わる（設計の段のリードと支援役、コード生成の後のレビュー役）。

## 進め方

- Bolt は B1 から B5 まで1つずつ順に作る。同時には作らない。
- 依頼者は、設計の段ごとにすべての単位をまとめて1回承認し、コード生成では Bolt ごとに承認する（Q4 A）。
- `origin` への `git push` は依頼者が行う。AI はプッシュしない（`team.md`）。
- 外のチーム・外の承認に頼る作業は無い（`external-dependency-map.md`）。
