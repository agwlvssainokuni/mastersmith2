# project.md・team.md から外した学びの記録

毎回は読み込まれない。2026-11-04 に project.md・team.md から移した経緯の記録。

## この記録の置き方

外した項目の元の文は、整理の前のファイルを丸ごと写した次の2つにすべて残っている（残した項目も含む）。

- `project-original-2026-11-04.md`（整理の前の project.md）
- `team-original-2026-11-04.md`（整理の前の team.md）
- git では、コミット `bd422205` の `aidlc/spaces/default/memory/project.md`・`team.md` と同じ中身。

以下は、見出しごとにどんな項目を外したかの要約。

## 外した項目の分け方（project.md）

- `## Way of Working`: Intent・単位ごとの経緯（U2・U3 の試し、U4・U5 の答え、U3 のレビュー、B1 の順、コード生成の順 など）。
- `## Testing Posture`: Intent ごとの測定・失敗・持ち越しの記録（pre1g、LongSupplier、AuditSecretLeakIT のヒープ、CI の個別の失敗、memory.peak の不合格、logoutIntent の直し など）。team.md と重なるもの（waitFor、k6 の setup、E2E を最後に流す決まり、JWT の伏せ字）。
- `## Change Control`: 承認なしのコミットの個別の記録。
- `## Deployment`: Intent ごとの配備の差・救済・停止の時間・個別の件数の記録。
- `## Code Style`: U2 の書き込みのプール。
- `## Tech Stack`: Jackson 3.1.6 の上書きの行（3.1.7 の行に置き換わった）。
- `## Corrections`: Intent ごとの判断の記録と、同じ教えの繰り返し（まとめた決まりの元の項目を含む）。
- `## Interpretations`・`## Deviations`・`## Tradeoffs`: すべて（business-data-views の個別の記録）。

## 外した文（team.md）

- Testing Posture の E2E の行: 「（Intent 261004-role-menu は最大2本）」
- Testing Posture の対象DB の結合テストの行: 「Intent `260923-dsl-schema-loader` の Build and Test で `verify` の時間と colima の VM のメモリを実測したうえで、この形に決めた。」
- Testing Posture の業務データの E2E の行: 「Intent 261016-business-data-views で足す E2E は業務データの代表の流れ1本までとし、持ち越しの直し（make-you-chic-ui の固定先の更新・axe-core の取り込み）と既存の E2E の書き換えは本数に数えない。」
- Testing Posture の参照ピッカーの行: 「…この Intent の機能の代表の流れとして1本足す。」「i18next 系の更新と」
- Testing Posture: 「Intent 261102-timezone-ui-polish（N6 と画面の仕上げを束ねたもの）で足す E2E は、N6 の日時の代表の流れ1本までとし、画面の仕上げ（アイコン・favicon・ホーム）は既存の E2E の書き換えと実際のブラウザの axe で確かめる（axe と表示の確かめは本数に数えない）。管理の仮の画面に新しい操作の流れを足すと決めたときは、要件定義で1本を足すかを決める。」
- Code Style フロントエンド: 「前にあった違反の1件（`features/registration/useRegistration.ts` が `../auth/authSession` を読む）は直っている（2026-10-16 に確かめた。残る `RegistrationPage.test.tsx` の import はテストのファイルのため対象の外）。」
- Code Style 静的解析: 「既存の `PREDICTABLE_RANDOM` の指摘（`auth/repository/LoginAttemptStateRepository` の `lockDummyForUpdate` の1件）は、関門に入れた Bolt で誤検知と確かめ、除外の設定に理由を書いて外した。」
- Code Style 静的解析: 「最初に取り込んだ Bolt で、推移依存（`slf4j-api`）が lockfile に載り OSV-Scanner の対象になることを確かめた」
