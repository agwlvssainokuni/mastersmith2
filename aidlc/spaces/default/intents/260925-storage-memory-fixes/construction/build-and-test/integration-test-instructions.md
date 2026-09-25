# 結合テストの手順（integration-test-instructions）

Test Strategy は Minimal のため、新しい結合テストはコード生成の再現のテスト1件（`H2CompactionByPoolSuspensionIT`）だけで、この段では既存の結合テストの全体を `verify` の中で流して、変更の後も通ることを確かめる（NFR6）。

## 1. 道具と前提

- Spring Boot Test・組み込みの H2（内部DB）・Testcontainers（対象DB の MySQL・MariaDB・PostgreSQL）。colima が動いていること。
- テストの JVM は `-Djava.net.preferIPv4Stack=true`（FR3 の直し）で、MBean の登録はテストの既定で無効（`TestHikariMbeansEnvironmentPostProcessor`）。再現の結合テストだけが重ならないプールの名前で有効にする。

## 2. 流すもの

```bash
./gradlew :backend:integrationTest --rerun \
  --tests 'cherry.mastersmith.config.H2CompactionByPoolSuspensionIT' \
  --tests 'cherry.mastersmith.config.DataSourcePoolIT' \
  --tests 'cherry.mastersmith.dslmanage.repository.DslManageRepositoryIT' \
  --tests 'cherry.mastersmith.dslmanage.web.DslAdminApiIT'
# AccessTokenApiIT のクラスだけのくり返し（FR3）
for i in $(seq 1 10); do ./gradlew :backend:integrationTest --rerun --tests 'cherry.mastersmith.auth.web.AccessTokenApiIT' -q || echo "run $i failed"; done
# 結合テストの全体は verify の中（build-instructions.md）
```

## 3. 合格の条件

- 失敗・誤り・飛ばしが 0 件（対象DB のテストも飛ばされない）。
- `AccessTokenApiIT` の 10 回がすべて成功。

## Sources

- 計画: `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/code-generation-plan.md`（Testing Contract・D1〜D9・Build and Test に引き継ぐこと）
- 単体テストの手順: `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/unit-test-instructions.md`
- 生成のまとめ: `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/code-summary.md`
- 生の結果（コミットしない）: `build/perf-results/bt/`
