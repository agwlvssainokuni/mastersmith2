# 単位をまたぐ要件の網羅（cross-unit-traceability）

## 判定

**合格（Pass）**。要件定義の FR・NFR（62 件、親と枝番）と、ストーリーの受け入れ基準（AC、101 件）の合計 163 件は、すべてテスト（または Build and Test の実測）までたどれた。

- コード生成の traceability.json で直接 `OK`（対象のファイルが実在）: 99 件
- 機能設計の決まり（BR）・ストーリー・NFR 要件の枝番を経る2段の連鎖で `OK`: 60 件（project.md の決まりどおり。traceability.json は要件の ID を直接持たず、単位ごとの ID で持つため）
- コード生成の traceability.json に `OK` が無く、この段で埋めたもの: 4 件（AC1.2.3・AC3.2.5・FR1.7・NFR11。下の「この段で埋めた4件」）

見つかった問題（承認の場で伝えること）:

1. **本番のソースや設定を指す対応（2 件）**: 下の表で「本番のソースや設定を指す」と書いた要件は、確かめたテストではなく実装のファイルに `OK` が付いている。依頼者の決定（Q7: A）により、この段では直さずに記録する。U4 の同じ種類の3件（NFR1.15・NFR5.5・BR8.2）は Code Generation の承認の前に直した（A1）。
2. **AC3.2.5 がどの単位の traceability.json にも無い**: 性能の受け入れ基準で、U4・U5 の設計と計画は参照しているが、コード生成の対応の表から漏れていた。この段の実測で満たしたことを確かめた。

## 照合の方法

1. 要件定義（`aidlc/spaces/default/intents/260923-dsl-schema-loader/inception/requirements-analysis/requirements.md`）から FR・NFR の ID を、ストーリー（`aidlc/spaces/default/intents/260923-dsl-schema-loader/inception/user-stories/stories.md`）から3段の AC の ID を集めた。
2. 各単位の `construction/<単位>/code-generation/traceability.json` で、状態が `OK` かつ対象のファイルが実在するものを直接の対応とした。
3. 直接の対応が無い FR は、各単位の `functional-design/rules.md` の `source` にその FR を持つ決まり（BR）と、ストーリーの traceability.json でその FR に対応するストーリーの AC を経て、コード生成で `OK` のものをたどった。
4. 直接の対応が無い NFR（親）は、各単位の `nfr-requirements/traceability.json` でその NFR の枝番（NFRx.y）を経て、コード生成で `OK` のものをたどった。
5. 表の「持ち主」は、根拠が見つかった単位。根拠の列は、たどれたうち先頭の2つだけを挙げた。

## この段で埋めた4件

| ID | 判定 | 根拠 | 内容 |
|---|---|---|---|
| AC1.2.3 | Met（Build and Test の実測） | perf/dsl-timing.sh・build/perf-results/dsl-run1/summary.md | 既定の DSL の生成が 100 × 100 で 30 秒以内。U3 は N/A で Build and Test に引き継いだ。この段の実測（NFR1.6）で 3種類の DB とも 1.54〜2.64 秒 |
| AC3.2.5 | Met（Build and Test の実測） | perf/dsl-timing.sh・build/perf-results/dsl-run1/summary.md | 100 × 100 の DSL と同じ規模の対象DB で、投入から照合を終えてプレビューに置くまで 10 秒以内。コード生成のどの traceability.json にも載っていなかった（U4・U5 の設計と計画は参照している）。この段の実測（NFR1.8）で投入 0.57〜0.69 秒・照合込みの表示 0.046〜0.200 秒 |
| FR1.7 | OK（間接） | backend/src/test/java/cherry/mastersmith/dsl/validate/DslSchemaValidatorTest.java | 権限とカラムの編集の可否を DSL に含めない、という「含めない」要件。JSON Schema がすべての物に additionalProperties: false を置き、知らない項目を SYNTAX_UNKNOWN_PROPERTY で拒否することで守られる。コード生成の traceability.json には載っていない |
| NFR11 | Met（Build and Test の実測） | ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify（verifyCoverage） | カバレッジの下限。コード生成の traceability.json には載っていない（各単位の nfr-requirements の traceability.json は verify の verifyCoverage を持ち主にしている）。この段の実測でバックエンド行 98.1%・分岐 94.1%、画面 行 97.9%・分岐 93.6%、新しいパッケージはすべて下限以上 |

## 要件ごとの対応

| ID | 判定 | 持ち主（単位） | 根拠（ファイル（ID）） | たどり方・注 |
|---|---|---|---|---|
| AC1.1.1 | OK | u1-target-db・u3-default-dsl-generation・u4-dsl-management | `backend/src/test/java/cherry/mastersmith/targetdb/repository/AbstractSchemaQueriesIT.java`（AC1.1.1）・`backend/src/test/java/cherry/mastersmith/dslmanage/generate/AbstractDefaultDslGeneratorIT.java`（AC1.1.1） | 直接 |
| AC1.1.2 | OK | u3-default-dsl-generation | `backend/src/test/java/cherry/mastersmith/dslmanage/generate/DslTreeBuilderTest.java`（AC1.1.2） | 直接 |
| AC1.1.3 | OK | u3-default-dsl-generation | `backend/src/test/java/cherry/mastersmith/dslmanage/generate/DslTreeBuilderTest.java`（AC1.1.3） | 直接 |
| AC1.1.4 | OK | u3-default-dsl-generation | `backend/src/test/java/cherry/mastersmith/dslmanage/generate/DslTreeBuilderTest.java`（AC1.1.4） | 直接 |
| AC1.1.5 | OK | u3-default-dsl-generation・u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/generate/TargetSchemaDslGeneratorTest.java`（AC1.1.5）・`backend/src/test/java/cherry/mastersmith/dslmanage/web/DslTargetDbIT.java`（AC1.1.5） | 直接 |
| AC1.1.6 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC1.1.6）・`frontend/src/features/dsl/DslAdminPage.test.tsx`（AC1.1.6） | 直接 |
| AC1.1.7 | OK | u1-target-db・u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/targetdb/service/AbstractTargetSchemaReaderIT.java`（AC1.1.7）・`backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC1.1.7） | 直接 |
| AC1.1.8 | OK | u1-target-db・u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/targetdb/service/AbstractTargetSchemaReaderIT.java`（AC1.1.8）・`backend/src/test/java/cherry/mastersmith/dslmanage/web/DslTargetDbIT.java`（AC1.1.8） | 直接 |
| AC1.1.9 | OK | u1-target-db・u3-default-dsl-generation | `backend/src/test/java/cherry/mastersmith/targetdb/repository/AbstractSchemaQueriesIT.java`（AC1.1.9）・`backend/src/test/java/cherry/mastersmith/dslmanage/generate/AbstractDefaultDslGeneratorIT.java`（AC1.1.9） | 直接 |
| AC1.1.10 | OK | u1-target-db | `backend/src/test/java/cherry/mastersmith/targetdb/repository/AbstractSchemaQueriesIT.java`（AC1.1.10） | 直接 |
| AC1.1.11 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslTargetDbIT.java`（AC1.1.11） | 直接 |
| AC1.2.1 | OK | u1-target-db・u3-default-dsl-generation | `backend/src/test/java/cherry/mastersmith/targetdb/repository/AbstractSchemaQueriesIT.java`（AC1.2.1）・`backend/src/test/java/cherry/mastersmith/dslmanage/generate/AbstractDefaultDslGeneratorIT.java`（AC1.2.1） | 直接 |
| AC1.2.2 | OK | u3-default-dsl-generation | `backend/src/test/java/cherry/mastersmith/dslmanage/generate/TypeCategoryMappingTest.java`（AC1.2.2） | 直接 |
| AC1.2.3 | Met（Build and Test の実測） | Build and Test | perf/dsl-timing.sh・build/perf-results/dsl-run1/summary.md | 既定の DSL の生成が 100 × 100 で 30 秒以内。U3 は N/A で Build and Test に引き継いだ。この段の実測（NFR1.6）で 3種類の DB とも 1.54〜2.64 秒 |
| AC2.1.1 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC2.1.1）・`frontend/src/features/dsl/DslSubmitForm.test.tsx`（AC2.1.1） | 直接 |
| AC2.1.2 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC2.1.2）・`frontend/src/features/dsl/DslSubmitForm.test.tsx`（AC2.1.2） | 直接 |
| AC2.1.3 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC2.1.3）・`frontend/src/features/dsl/DslAdminPage.test.tsx`（AC2.1.3） | 直接 |
| AC2.1.4 | OK | u5-dsl-admin-ui | `frontend/src/features/dsl/DslSubmitForm.test.tsx`（AC2.1.4） | 直接 |
| AC2.1.5 | OK | u5-dsl-admin-ui | `frontend/src/features/dsl/submitInput.test.ts`（AC2.1.5） | 直接 |
| AC2.1.6 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC2.1.6） | 直接 |
| AC2.2.1 | OK | u2-dsl-definition・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dsl/validate/DslSchemaValidatorTest.java`（AC2.2.1）・`frontend/src/features/dsl/DslErrorList.test.tsx`（AC2.2.1） | 直接 |
| AC2.2.2 | OK | u2-dsl-definition | `backend/src/test/java/cherry/mastersmith/dsl/validate/DslSemanticValidatorTest.java`（AC2.2.2） | 直接 |
| AC2.2.3 | OK | u2-dsl-definition | `backend/src/test/java/cherry/mastersmith/dsl/parse/SafeYamlParserTest.java`（AC2.2.3） | 直接 |
| AC2.2.4 | OK | u2-dsl-definition | `backend/src/test/java/cherry/mastersmith/dsl/validate/DslSemanticValidatorTest.java`（AC2.2.4） | 直接 |
| AC2.2.5 | OK | u2-dsl-definition・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dsl/service/DefaultDslReaderTest.java`（AC2.2.5）・`frontend/src/features/dsl/DslErrorList.test.tsx`（AC2.2.5） | 直接 |
| AC2.2.6 | OK | u2-dsl-definition | `backend/src/test/java/cherry/mastersmith/dsl/validate/DslSchemaValidatorTest.java`（AC2.2.6） | 直接 |
| AC2.2.7 | OK | u2-dsl-definition | `backend/src/test/java/cherry/mastersmith/dsl/service/DefaultDslReaderTest.java`（AC2.2.7） | 直接 |
| AC2.2.8 | OK | u2-dsl-definition・u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dsl/service/DefaultDslReaderTest.java`（AC2.2.8）・`backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC2.2.8） | 直接 |
| AC2.2.9 | OK | u5-dsl-admin-ui | `frontend/src/features/dsl/DslAdminPage.test.tsx`（AC2.2.9） | 直接 |
| AC2.2.10 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC2.2.10） | 直接 |
| AC2.3.1 | OK | u2-dsl-definition・u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dsl/service/DefaultDslReaderTest.java`（AC2.3.1）・`backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC2.3.1） | 直接 |
| AC2.3.2 | OK | u2-dsl-definition | `backend/src/test/java/cherry/mastersmith/dsl/parse/SafeYamlParserTest.java`（AC2.3.2） | 直接 |
| AC2.3.3 | OK | u2-dsl-definition | `backend/src/test/java/cherry/mastersmith/dsl/parse/SafeYamlParserTest.java`（AC2.3.3） | 直接 |
| AC2.3.4 | OK | u2-dsl-definition | `backend/src/test/java/cherry/mastersmith/dsl/parse/SafeYamlParserTest.java`（AC2.3.4） | 直接 |
| AC2.3.5 | OK | u2-dsl-definition | `backend/src/test/java/cherry/mastersmith/dsl/parse/SafeYamlParserTest.java`（AC2.3.5） | 直接 |
| AC2.3.6 | OK | u2-dsl-definition | `backend/src/test/java/cherry/mastersmith/dsl/validate/DslSchemaValidatorTest.java`（AC2.3.6） | 直接 |
| AC2.3.7 | OK | u2-dsl-definition | `backend/src/test/java/cherry/mastersmith/dsl/validate/DslSchemaValidatorTest.java`（AC2.3.7） | 直接 |
| AC2.3.8 | OK | u2-dsl-definition | `backend/src/test/java/cherry/mastersmith/dsl/service/DefaultDslReaderTest.java`（AC2.3.8） | 直接 |
| AC2.3.9 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC2.3.9） | 直接 |
| AC2.3.10 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC2.3.10） | 直接 |
| AC3.1.1 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC3.1.1）・`frontend/src/features/dsl/DslPreviewPanel.test.tsx`（AC3.1.1） | 直接 |
| AC3.1.2 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/service/DslSummaryAndDiffTest.java`（AC3.1.2）・`frontend/src/features/dsl/DslPreviewPanel.test.tsx`（AC3.1.2） | 直接 |
| AC3.1.3 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/service/DslSummaryAndDiffTest.java`（AC3.1.3）・`frontend/src/features/dsl/DslDiffTable.test.tsx`（AC3.1.3） | 直接 |
| AC3.1.4 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/service/DslSummaryAndDiffTest.java`（AC3.1.4）・`frontend/src/features/dsl/DslDiffTable.test.tsx`（AC3.1.4） | 直接 |
| AC3.1.5 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/service/DslSummaryAndDiffTest.java`（AC3.1.5）・`frontend/src/features/dsl/DslDiffTable.test.tsx`（AC3.1.5） | 直接 |
| AC3.1.6 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC3.1.6）・`frontend/src/features/dsl/DslStatusPanel.test.tsx`（AC3.1.6） | 直接 |
| AC3.1.7 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC3.1.7）・`frontend/src/features/dsl/DslAdminPage.test.tsx`（AC3.1.7） | 直接 |
| AC3.2.1 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslTargetDbIT.java`（AC3.2.1）・`frontend/src/features/dsl/DslWarningList.test.tsx`（AC3.2.1） | 直接 |
| AC3.2.2 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslTargetDbIT.java`（AC3.2.2）・`frontend/src/features/dsl/DslWarningList.test.tsx`（AC3.2.2） | 直接 |
| AC3.2.3 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslTargetDbIT.java`（AC3.2.3）・`frontend/src/features/dsl/DslWarningList.test.tsx`（AC3.2.3） | 直接 |
| AC3.2.4 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslTargetDbIT.java`（AC3.2.4）・`frontend/src/features/dsl/DslPreviewPanel.test.tsx`（AC3.2.4） | 直接 |
| AC3.2.5 | Met（Build and Test の実測） | Build and Test | perf/dsl-timing.sh・build/perf-results/dsl-run1/summary.md | 100 × 100 の DSL と同じ規模の対象DB で、投入から照合を終えてプレビューに置くまで 10 秒以内。コード生成のどの traceability.json にも載っていなかった（U4・U5 の設計と計画は参照している）。この段の実測（NFR1.8）で投入 0.57〜0.69 秒・照合込みの表示 0.046〜0.200 秒 |
| AC3.2.6 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslTargetDbIT.java`（AC3.2.6） | 直接 |
| AC3.3.1 | OK | u5-dsl-admin-ui | `frontend/src/features/dsl/DslAdminPage.test.tsx`（AC3.3.1） | 直接 |
| AC3.3.2 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC3.3.2） | 直接 |
| AC3.3.3 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC3.3.3）・`frontend/src/features/dsl/DslAdminPage.test.tsx`（AC3.3.3） | 直接 |
| AC3.3.4 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC3.3.4） | 直接 |
| AC3.4.1 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC3.4.1）・`frontend/src/features/dsl/DslAdminPage.test.tsx`（AC3.4.1） | 直接 |
| AC3.4.2 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC3.4.2） | 直接 |
| AC3.4.3 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC3.4.3）・`frontend/src/features/dsl/DslAdminPage.test.tsx`（AC3.4.3） | 直接 |
| AC3.4.4 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslTargetDbIT.java`（AC3.4.4） | 直接 |
| AC3.4.5 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC3.4.5）・`frontend/src/features/dsl/api/dslApi.test.ts`（AC3.4.5） | 直接 |
| AC4.1.1 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC4.1.1）・`frontend/src/features/dsl/DslAdminPage.test.tsx`（AC4.1.1） | 直接 |
| AC4.1.2 | OK | u5-dsl-admin-ui | `frontend/src/features/dsl/DslAdminPage.test.tsx`（AC4.1.2） | 直接 |
| AC4.1.3 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC4.1.3） | 直接 |
| AC4.1.4 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC4.1.4） | 直接 |
| AC4.1.5 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/service/DslStartupIT.java`（AC4.1.5） | 直接 |
| AC4.1.6 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/service/DslStartupIT.java`（AC4.1.6） | 直接 |
| AC4.1.7 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslTargetDbIT.java`（AC4.1.7） | 直接 |
| AC4.1.8 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC4.1.8） | 直接 |
| AC4.1.9 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC4.1.9） | 直接 |
| AC4.1.10 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC4.1.10） | 直接 |
| AC4.2.1 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC4.2.1）・`frontend/src/features/dsl/DslAdminPage.test.tsx`（AC4.2.1） | 直接 |
| AC4.2.2 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC4.2.2）・`frontend/src/features/dsl/DslAdminPage.test.tsx`（AC4.2.2） | 直接 |
| AC4.2.3 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslConcurrencyIT.java`（AC4.2.3） | 直接 |
| AC4.2.4 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslConcurrencyIT.java`（AC4.2.4） | 直接 |
| AC5.1.1 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC5.1.1）・`frontend/src/features/dsl/DslHistoryTable.test.tsx`（AC5.1.1） | 直接 |
| AC5.1.2 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC5.1.2）・`frontend/src/features/dsl/DslHistoryTable.test.tsx`（AC5.1.2） | 直接 |
| AC5.1.3 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC5.1.3）・`frontend/src/features/dsl/DslAdminPage.test.tsx`（AC5.1.3） | 直接 |
| AC5.1.4 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC5.1.4）・`frontend/src/features/dsl/DslAdminPage.test.tsx`（AC5.1.4） | 直接 |
| AC5.1.5 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslTargetDbIT.java`（AC5.1.5）・`frontend/src/features/dsl/DslPreviewPanel.test.tsx`（AC5.1.5） | 直接 |
| AC5.1.6 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC5.1.6）・`frontend/src/features/dsl/DslAdminPage.test.tsx`（AC5.1.6） | 直接 |
| AC5.1.7 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC5.1.7） | 直接 |
| AC5.2.1 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC5.2.1）・`frontend/src/features/dsl/DslHistoryTable.test.tsx`（AC5.2.1） | 直接 |
| AC5.2.2 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC5.2.2）・`frontend/src/features/dsl/DslAdminPage.test.tsx`（AC5.2.2） | 直接 |
| AC5.2.3 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC5.2.3）・`frontend/src/features/dsl/api/dslApi.test.ts`（AC5.2.3） | 直接 |
| AC6.1.1 | OK | u1-target-db | `backend/src/test/java/cherry/mastersmith/targetdb/config/TargetDbStartupIT.java`（AC6.1.1） | 直接 |
| AC6.1.2 | OK | u1-target-db | `backend/src/test/java/cherry/mastersmith/targetdb/config/TargetDbStartupIT.java`（AC6.1.2） | 直接 |
| AC6.1.3 | OK | u1-target-db | `backend/src/test/java/cherry/mastersmith/targetdb/service/AbstractTargetSchemaReaderIT.java`（AC6.1.3） | 直接 |
| AC6.1.4 | OK | u1-target-db | `backend/src/test/java/cherry/mastersmith/targetdb/config/TargetDbSecretLeakIT.java`（AC6.1.4） | 直接 |
| AC6.1.5 | OK | u1-target-db | `backend/src/test/java/cherry/mastersmith/targetdb/config/TargetDbStartupIT.java`（AC6.1.5） | 直接 |
| AC6.2.1 | OK | u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAccessControlIT.java`（AC6.2.1） | 直接 |
| AC6.2.2 | OK | u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAccessControlIT.java`（AC6.2.2） | 直接 |
| AC6.2.3 | OK | u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAccessControlIT.java`（AC6.2.3） | 直接 |
| AC6.2.4 | OK | u5-dsl-admin-ui | `frontend/src/features/dsl/registration.test.tsx`（AC6.2.4） | 直接 |
| AC6.2.5 | OK | u5-dsl-admin-ui | `frontend/src/features/dsl/DslMenuTree.test.tsx`（AC6.2.5） | 直接 |
| AC6.2.6 | OK | u5-dsl-admin-ui | `frontend/src/features/dsl/DslAdminPage.test.tsx`（AC6.2.6） | 直接 |
| AC6.3.1 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC6.3.1） | 直接 |
| AC6.3.2 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（AC6.3.2） | 直接 |
| AC6.3.3 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAuditWriteFailureIT.java`（AC6.3.3） | 直接 |
| AC6.3.4 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslConcurrencyIT.java`（AC6.3.4） | 直接 |
| FR1 | OK | u2-dsl-definition | `backend/src/test/java/cherry/mastersmith/dsl/service/DefaultDslReaderTest.java`（BR2.1）・`backend/src/test/java/cherry/mastersmith/dsl/validate/DslSchemaValidatorTest.java`（BR2.2） | BR・AC |
| FR1.1 | OK | u2-dsl-definition | `backend/src/test/java/cherry/mastersmith/dsl/validate/DslSchemaValidatorTest.java`（BR2.2） | BR・AC |
| FR1.2 | OK | u2-dsl-definition | `backend/src/test/java/cherry/mastersmith/dsl/validate/DslSemanticValidatorTest.java`（BR3.2） | BR・AC |
| FR1.3 | OK | u2-dsl-definition | `backend/src/test/java/cherry/mastersmith/dsl/validate/DslSemanticValidatorTest.java`（BR3.4）・`backend/src/test/java/cherry/mastersmith/dsl/validate/DslSemanticValidatorTest.java`（BR3.5） | BR・AC |
| FR1.4 | OK | u3-default-dsl-generation | `backend/src/main/java/cherry/mastersmith/dslmanage/generate/DslTreeBuilder.java`（BR1.5） | BR・AC。本番のソースや設定を指す（Q7: A により直さず記録） |
| FR1.5 | OK | u2-dsl-definition | `backend/src/test/java/cherry/mastersmith/dsl/service/DefaultDslReaderTest.java`（BR2.1） | BR・AC |
| FR1.6 | OK | u2-dsl-definition | `backend/src/test/java/cherry/mastersmith/dsl/validate/DslSchemaValidatorTest.java`（BR2.2） | BR・AC |
| FR1.7 | OK（間接） | Build and Test | backend/src/test/java/cherry/mastersmith/dsl/validate/DslSchemaValidatorTest.java | 権限とカラムの編集の可否を DSL に含めない、という「含めない」要件。JSON Schema がすべての物に additionalProperties: false を置き、知らない項目を SYNTAX_UNKNOWN_PROPERTY で拒否することで守られる。コード生成の traceability.json には載っていない |
| FR2 | OK | u1-target-db | `backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDbProperties.java`（BR1.1）・`backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDbSettings.java`（BR1.2） | BR・AC |
| FR2.1 | OK | u1-target-db | `backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDbProperties.java`（BR1.1）・`backend/src/main/java/cherry/mastersmith/targetdb/repository/SchemaRows.java`（BR2.7） | BR・AC |
| FR2.2 | OK | u1-target-db | `backend/src/test/java/cherry/mastersmith/targetdb/config/TargetDbPropertiesTest.java`（BR1.4）・`backend/src/test/java/cherry/mastersmith/targetdb/config/TargetDbStartupIT.java`（AC6.1.5） | BR・AC |
| FR2.3 | OK | u1-target-db | `backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDataSourceConfig.java`（BR1.5）・`backend/src/test/java/cherry/mastersmith/targetdb/config/TargetDbStartupIT.java`（AC6.1.5） | BR・AC |
| FR2.4 | OK | u1-target-db | `backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDbSettings.java`（BR1.2）・`backend/src/test/java/cherry/mastersmith/targetdb/config/TargetDbSettingsTest.java`（BR1.3） | BR・AC |
| FR2.5 | OK | u1-target-db・u4-dsl-management | `backend/src/test/java/cherry/mastersmith/targetdb/TargetDbBoundaryArchitectureTest.java`（BR1.6）・`backend/src/test/java/cherry/mastersmith/dslmanage/service/DslLifecycleTest.java`（BR4.7） | BR・AC |
| FR3 | OK | u1-target-db | `backend/src/test/java/cherry/mastersmith/targetdb/repository/AbstractSchemaQueriesIT.java`（BR2.1）・`backend/src/test/java/cherry/mastersmith/targetdb/repository/AbstractSchemaQueriesIT.java`（BR2.3） | BR・AC |
| FR3.1 | OK | u3-default-dsl-generation・u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/service/DslLifecycleTest.java`（BR1.1）・`backend/src/test/java/cherry/mastersmith/dslmanage/generate/DslTreeBuilderTest.java`（AC1.1.2） | BR・AC |
| FR3.2 | OK | u1-target-db | `backend/src/test/java/cherry/mastersmith/targetdb/repository/AbstractSchemaQueriesIT.java`（BR2.1）・`backend/src/test/java/cherry/mastersmith/targetdb/repository/AbstractSchemaQueriesIT.java`（BR2.3） | BR・AC |
| FR3.3 | OK | u1-target-db・u3-default-dsl-generation | `backend/src/test/java/cherry/mastersmith/targetdb/domain/TargetSchemaTest.java`（BR2.5）・`backend/src/main/java/cherry/mastersmith/dslmanage/generate/DslTreeBuilder.java`（BR1.2） | BR・AC |
| FR3.4 | OK | u3-default-dsl-generation | `backend/src/main/java/cherry/mastersmith/dslmanage/generate/DslTreeBuilder.java`（BR1.3）・`backend/src/main/java/cherry/mastersmith/dslmanage/generate/DslTreeBuilder.java`（BR1.4） | BR・AC |
| FR3.5 | OK | u3-default-dsl-generation | `backend/src/main/java/cherry/mastersmith/dslmanage/generate/DslTreeBuilder.java`（BR2.1）・`backend/src/main/java/cherry/mastersmith/dslmanage/generate/TypeCategoryMapping.java`（BR2.2） | BR・AC。本番のソースや設定を指す（Q7: A により直さず記録） |
| FR3.6 | OK | u3-default-dsl-generation・u4-dsl-management | `backend/src/main/java/cherry/mastersmith/dslmanage/generate/DslYamlWriter.java`（BR5.1）・`backend/src/main/java/cherry/mastersmith/dslmanage/generate/TargetSchemaDslGenerator.java`（BR5.2） | BR・AC |
| FR4 | OK | u2-dsl-definition | `backend/src/test/java/cherry/mastersmith/dsl/service/DefaultDslReaderTest.java`（BR1.1）・`backend/src/test/java/cherry/mastersmith/dsl/parse/SafeYamlParserTest.java`（BR1.2） | BR・AC |
| FR4.1 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（BR1.2）・`frontend/src/features/dsl/DslSubmitForm.test.tsx`（AC2.1.4） | BR・AC |
| FR4.2 | OK | u2-dsl-definition | `backend/src/test/java/cherry/mastersmith/dsl/parse/SafeYamlParserTest.java`（BR1.5）・`backend/src/test/java/cherry/mastersmith/dsl/validate/DslSchemaValidatorTest.java`（BR2.2） | BR・AC |
| FR4.3 | OK | u2-dsl-definition | `backend/src/test/java/cherry/mastersmith/dsl/service/DefaultDslReaderTest.java`（BR2.3）・`backend/src/test/java/cherry/mastersmith/dsl/validate/DslSemanticValidatorTest.java`（BR3.7） | BR・AC |
| FR4.4 | OK | u2-dsl-definition | `backend/src/test/java/cherry/mastersmith/dsl/service/DefaultDslReaderTest.java`（BR1.1）・`backend/src/test/java/cherry/mastersmith/dsl/parse/SafeYamlParserTest.java`（BR1.2） | BR・AC |
| FR4.5 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（BR1.2）・`backend/src/test/java/cherry/mastersmith/dslmanage/repository/DslManageRepositoryIT.java`（BR1.4） | BR・AC |
| FR5 | OK | u3-default-dsl-generation・u4-dsl-management | `backend/src/main/java/cherry/mastersmith/dslmanage/generate/DslYamlWriter.java`（BR5.1）・`backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（BR2.1） | BR・AC |
| FR5.1 | OK | u3-default-dsl-generation・u4-dsl-management | `backend/src/main/java/cherry/mastersmith/dslmanage/generate/DslYamlWriter.java`（BR5.1）・`backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（BR2.1） | BR・AC |
| FR5.2 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（BR2.1）・`backend/src/test/java/cherry/mastersmith/dslmanage/service/DslReconcilerTest.java`（BR2.4） | BR・AC |
| FR5.3 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（BR2.5）・`backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（BR3.1） | BR・AC |
| FR6 | OK | u2-dsl-definition | `backend/src/test/java/cherry/mastersmith/dsl/domain/DslModelTest.java`（BR5.2）・`backend/src/test/java/cherry/mastersmith/dsl/service/ActiveDslModelStoreTest.java`（BR5.3） | BR・AC |
| FR6.1 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslConcurrencyIT.java`（BR4.2）・`backend/src/test/java/cherry/mastersmith/dslmanage/web/DslConcurrencyIT.java`（BR4.6） | BR・AC |
| FR6.2 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（BR4.1）・`backend/src/test/java/cherry/mastersmith/dslmanage/web/DslConcurrencyIT.java`（BR4.3） | BR・AC |
| FR6.3 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/service/DslStartupIT.java`（BR5.1）・`backend/src/test/java/cherry/mastersmith/dslmanage/service/DslStartupIT.java`（BR5.2） | BR・AC |
| FR6.4 | OK | u2-dsl-definition | `backend/src/test/java/cherry/mastersmith/dsl/domain/DslModelTest.java`（BR5.2）・`backend/src/test/java/cherry/mastersmith/dsl/service/ActiveDslModelStoreTest.java`（BR5.3） | BR・AC |
| FR7 | OK | u2-dsl-definition・u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dsl/service/DefaultDslReaderTest.java`（BR5.1）・`backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（BR1.3） | BR・AC |
| FR7.1 | OK | u2-dsl-definition・u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dsl/service/DefaultDslReaderTest.java`（BR5.1）・`backend/src/test/java/cherry/mastersmith/dslmanage/web/DslConcurrencyIT.java`（BR4.2） | BR・AC |
| FR7.2 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（BR1.3）・`backend/src/test/java/cherry/mastersmith/dslmanage/service/DslStartupIT.java`（AC4.1.5） | BR・AC |
| FR7.3 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslConcurrencyIT.java`（BR4.2）・`backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（BR4.4） | BR・AC |
| FR8 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（BR2.5）・`backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（BR6.2） | BR・AC |
| FR8.1 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（BR2.5）・`backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（BR6.2） | BR・AC |
| FR9 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（BR8.3）・`frontend/src/features/dsl/DslAdminPage.test.tsx`（AC6.2.6） | BR・AC |
| FR9.1 | OK | u5-dsl-admin-ui | `frontend/src/features/dsl/DslAdminPage.test.tsx`（AC6.2.6）・`backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAccessControlIT.java`（AC6.2.2） | BR・AC |
| FR9.2 | OK | u4-dsl-management・u5-dsl-admin-ui | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（BR8.3）・`frontend/src/features/dsl/DslAdminPage.test.tsx`（AC6.2.6） | BR・AC |
| FR9.3 | OK | u5-dsl-admin-ui | `frontend/src/features/dsl/DslAdminPage.test.tsx`（AC6.2.6）・`backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAccessControlIT.java`（AC6.2.2） | BR・AC |
| FR10 | OK | u2-dsl-definition・u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dsl/service/DefaultDslReaderTest.java`（BR5.1）・`backend/src/test/java/cherry/mastersmith/dslmanage/web/DslConcurrencyIT.java`（BR4.6） | BR・AC |
| FR10.1 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（BR7.1）・`backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（BR7.4） | BR・AC |
| FR10.2 | OK | u2-dsl-definition・u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dsl/service/DefaultDslReaderTest.java`（BR5.1）・`backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`（BR7.1） | BR・AC |
| FR10.3 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslConcurrencyIT.java`（BR4.6）・`backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAuditWriteFailureIT.java`（BR7.3） | BR・AC |
| NFR1 | OK | u1-target-db | `backend/src/main/java/cherry/mastersmith/targetdb/service/JdbcTargetSchemaReader.java`（NFR1.2）・`backend/src/test/java/cherry/mastersmith/targetdb/repository/AbstractSchemaQueriesIT.java`（NFR1.3） | NFR の枝番 |
| NFR2 | OK | u2-dsl-definition | `backend/src/test/java/cherry/mastersmith/dsl/service/DefaultDslReaderTest.java`（NFR2.1）・`backend/src/test/java/cherry/mastersmith/dsl/parse/SafeYamlParserTest.java`（NFR2.4） | NFR の枝番 |
| NFR3 | OK | u2-dsl-definition | `backend/src/test/java/cherry/mastersmith/dsl/validate/DslSchemaValidatorTest.java`（NFR3.3）・`backend/src/test/java/cherry/mastersmith/dsl/parse/SafeYamlParserTest.java`（NFR3.2） | NFR の枝番 |
| NFR4 | OK | u1-target-db | `backend/src/test/java/cherry/mastersmith/targetdb/config/TargetDataSourceConfigTest.java`（NFR4.5）・`backend/src/test/java/cherry/mastersmith/targetdb/TargetDbBoundaryArchitectureTest.java`（NFR4.1） | NFR の枝番 |
| NFR5 | OK | u2-dsl-definition・u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dsl/validate/DslSchemaValidatorTest.java`（NFR5.2）・`backend/src/test/java/cherry/mastersmith/dsl/parse/SafeYamlParserPropertyTest.java`（NFR5.1） | NFR の枝番 |
| NFR6 | OK | u1-target-db | `backend/src/test/java/cherry/mastersmith/targetdb/repository/AbstractSchemaQueriesIT.java`（NFR6.4）・`backend/src/main/java/cherry/mastersmith/targetdb/repository/SchemaRows.java`（NFR6.2） | NFR の枝番 |
| NFR7 | OK | u1-target-db | `backend/src/test/java/cherry/mastersmith/targetdb/config/TargetDataSourceConfigTest.java`（NFR7.5）・`backend/src/test/java/cherry/mastersmith/targetdb/config/TargetDbStartupIT.java`（NFR7.4） | NFR の枝番 |
| NFR8 | OK | u4-dsl-management | `backend/src/test/java/cherry/mastersmith/dslmanage/repository/DslManageRepositoryIT.java`（NFR8.3）・`backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAuditWriteFailureIT.java`（NFR8.5） | NFR の枝番 |
| NFR9 | OK | u3-default-dsl-generation・u5-dsl-admin-ui | `backend/src/main/java/cherry/mastersmith/dslmanage/generate/DslTreeBuilder.java`（NFR9.2）・`frontend/src/features/dsl/registration.test.tsx`（NFR9.1） | NFR の枝番 |
| NFR10 | OK | u5-dsl-admin-ui | `frontend/src/features/dsl/DslAdminPage.test.tsx`（NFR10.1） | NFR の枝番 |
| NFR11 | Met（Build and Test の実測） | Build and Test | ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify（verifyCoverage） | カバレッジの下限。コード生成の traceability.json には載っていない（各単位の nfr-requirements の traceability.json は verify の verifyCoverage を持ち主にしている）。この段の実測でバックエンド行 98.1%・分岐 94.1%、画面 行 97.9%・分岐 93.6%、新しいパッケージはすべて下限以上 |
| NFR12 | OK | u1-target-db | `backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbTestDatabase.java`（NFR12.2）・`backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java`（NFR12.1） | NFR の枝番 |

## Sources

- `aidlc/spaces/default/intents/260923-dsl-schema-loader/inception/requirements-analysis/requirements.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/inception/user-stories/stories.md`・`aidlc/spaces/default/intents/260923-dsl-schema-loader/inception/user-stories/traceability.json`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/<単位>/code-generation/traceability.json`（5単位）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/<単位>/functional-design/rules.md`・`aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/<単位>/nfr-requirements/traceability.json`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/build-and-test/build-and-test-questions.md`（Q7: A）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/build-and-test/test-results.md`（この段の実測）

## Assumptions & Open Questions

None.
