# 結合テストの手順（Intent 261027-perf-targets-review）

Test Strategy は Minimal。この Intent はアプリのコード・API・DB スキーマを変えないため、新しい結合テストは足さない。既存の結合テスト（対象DB の MySQL・MariaDB・PostgreSQL を Testcontainers で動かすもの）は `./gradlew verify` の中で毎回流れる（`build-instructions.md`）。

- 既存の結合テストが緑のままであること（`verify` の結果で確かめる）。
- 追加の確かめは、台本の線のテスト（`bash perf/k6/thresholds-test.sh`）と、判定のテスト（`bash perf/dsl-mixed-memory-test.sh`）。どちらも単体の位置づけで、Code Generation で足した。
