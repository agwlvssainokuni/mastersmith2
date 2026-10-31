# Code Generation Plan Approval

AI-DLC built this plan without asking because plan approval is off for this
piece of work. This file is the record and asks nothing; to look at a plan
before it is built, ask in chat.

## Plan Approval

Built without asking: plan approval is off for this piece of work (from env AIDLC_DISABLE_PLAN_APPROVAL_GUARD).

- Builds: 作成・更新の画面の他テーブル参照の部品（`ReferenceSelectField`・`ReadonlyReferenceField`・`LookupField`・`LookupPickerDialog`）、参照先の検索とラベルの1件読みの呼び出し、定義の `purpose`、保存の誤りの写しと日英の文言、参照ピッカーの代表の E2E 190 と axe の E2E 195（...
- Touches: `frontend/src/features/tables/`（`reference/` の下を新しく作る。`businessDataTypes.ts`・`businessDataParse.ts`・`businessDataApi.ts`・`useTableDefinition.ts`・`BusinessDataFormPage.tsx`・`formDiff.ts`・`f...
- Tests: 画面の単体テスト 約 150 件（fast-check の性質ベース 約 10 件、vitest-axe 約 20 件を含む。新しいテストのファイル 9・既存のテストのファイル 7 に足す）と E2E 21 件（190 の1件・195 の 20 組）、既存の E2E 180 の書き換え（DSL の置き場）

Full plan: aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/code-generation/code-generation-plan.md
Test instructions: aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/code-generation/unit-test-instructions.md

[Approval Fingerprint]: sha256:v3:b5983421b55f484690742b7aea41a3e0b6858a0db4d4ff45ad620d105ed2994a
[Planned Source]: c5ba42a8e30322a4348c08c12d8a23160437976235bc4df57a5826eec3bcbe4d


[Answer]: Plan approval off
