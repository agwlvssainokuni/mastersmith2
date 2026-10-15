# 結合テストと E2E の手順（Intent 261015-deps-ui-carryover）

Test Strategy は Minimal のため、新しい結合テストの手順書は作らない。この Intent で足した・変えた結合テストは `./gradlew verify` の中で流れる。ここには、その一覧と、Intent の最後に1回だけ流す E2E の手順を書く。

## この Intent の結合テスト

| テスト | 要件 | 確かめること |
|---|---|---|
| `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslSecretLeakIT.java` | FR3.1・NFR4 | TRACE を有効にしても、DSL の誤りの抜粋（利用者が書いた値）がログに出ない |
| `backend/src/test/java/cherry/mastersmith/navigation/web/NavigationSecretLeakIT.java` | FR4.1・NFR4 | TRACE で navigation の表示名と、`ActiveDslModelStore` の DSL のモデルの文字列が出ない |
| `backend/src/test/java/cherry/mastersmith/dslmanage/service/DslStartupIT.java` | FR3.2・FR3.3 | 起動時に落としたメニューの項目の数が状態に出る |
| `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`・`role/web/RoleTransferLimitsApiIT.java` | FR9.2 | 1行の長さの上限（65,536）ちょうどは受け付け、超えると Problem Details で拒否し、部品の例外のメッセージを含めない |
| `backend/src/test/java/cherry/mastersmith/role/web/RoleBusyLogTraceIT.java`・`group/web/GroupBusyLogIT.java` | FR10 | `ROLE_BUSY`・`GROUP_BUSY`（割り当ての道を含む）の応答の traceId と WARN の1行の結び付き |
| `backend/src/test/java/cherry/mastersmith/invitation/testsupport/InvitedPersonIdsIT.java` | 不安定なテストの直し | 招待の ID の書き換えが重ならない（再現のテスト） |

実行: `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（`build-instructions.md`）。1つだけ流すときは `./gradlew :backend:integrationTest --tests '<クラスの完全な名前>'`。

## E2E（Intent の最後に1回）

依頼者の決定で、E2E はブランチごとの統合の前ではなく、この段で1回だけ流す（① と ② は統合の前にも流して通した）。

```bash
caffeinate -dims ./gradlew e2eTest
```

- 4つの統合（`3472705`・`60b49e5`・`0b04d3a`・`776738e`）の後の `develop` で流す。
- 実際のブラウザの axe は、160 などで、ブランドカラーとテーマのすべての組について行われる。axe-core は 4.13.0（4.14.0 は見送り）。
- ブランチ ④ の書体（`@fontsource/noto-sans-jp` 5.3.1）は、ビルドした画面に新しい版の書体のファイルが入ることを確かめ、画面に出る文字の表示は配備の後のスモークテストで確かめる（コード生成のレビューの R-03）。
