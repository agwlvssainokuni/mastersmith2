# 外の依存 — reference-lookup-cleanup

Bolt は、1 つ以上の作る単位（Unit of Work）を、設計から統合まで通して動くものにする 1 回の作り込みのこと。この文書は、外のチーム・外の API・承認の待ちなど、作業の外にあって Bolt を止めうるものを、それを使う Bolt と対応させて書く。

出典: Bolt の計画 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/delivery-planning/bolt-plan.md`、単位 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/units-generation/unit-of-work.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/units-generation/unit-of-work-dependency.md`、要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`、部品 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/components.md`、画面イメージ `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/refined-mockups/mockups.md`。

この Intent は、依頼者 1 名と AI の中でほぼ完結する。外のチームの API や、データの提供の待ちは無い。make-you-chic-ui への新しい依頼も無い（Refined Mockups で、既存の部品だけで作ると決めた）。

## 待ちになりうるもの

| 項目 | 持ち主 | かかる時間 | 止めうる Bolt | 遅れたとき |
|---|---|---|---|---|
| Dependabot の #81・#82 の取り込みの統合（Construction の前） | 依頼者（承認） | 1 回の `./gradlew verify` | B1 の開始（B4 の画面は新しい版で作る前提） | B1〜B3 はサーバー側のため先に進め、B4 の前に取り込む |
| 統合の後の CI（GitHub Actions）の通過 | 依頼者（`origin` へのプッシュ） | プッシュの後、約 30 分 | 次の Bolt の開始（CI が失敗したら直すまで進まない。team.md） | team.md の「不安定なテストと CI の失敗」の決まりで扱う |
| Mailpit の作り直しの了承 | 依頼者 | その場 | B6 | B6 の作り直しだけを後に回し、README の整理を先に進める |
| colima（コンテナの実行環境）が動いていること | 依頼者の PC | 起動に数分 | すべての Bolt の統合の前の `./gradlew verify` | 起動してから流し直す。対象DB のテストを飛ばした状態では統合しない（team.md） |
| 見本の対象DB（PostgreSQL）が動いていること | 依頼者の PC | 起動に数分 | B4 の参照ピッカーの E2E | 起動の手順を示して E2E を止める（team.md の決まり。起動・停止は E2E がしない） |

## Sources

- Bolt の計画: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/delivery-planning/bolt-plan.md`
- この段の答え: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/delivery-planning/delivery-planning-questions.md`（DQ7: A）
- チームの進め方: `aidlc/spaces/default/memory/team.md`（Way of Working・Testing Posture）

## Assumptions & Open Questions

None.
