# 技術の選択 — U5 u5-carryover-tests

U5 は、新しい依存・イメージ・パッケージを足さない。今ある道具だけで、テストを直し、測りの台本を作る。

## 上流の ID との対応表

| この文書の決め | 上流 | 関わるこの単位の ID |
|---|---|---|
| TS1 新しい依存を足さない | FR15・FR16、NFR7 | NFR7.3 |
| TS2 使う道具（既存） | FR15.1・FR15.2・FR16.2 | NFR7.1・NFR7.2・NFR4.9 |
| TS3 H2 の版と設定を保つ | FR16.3 | NFR4.7 |
| TS4 記録のループの動かし方 | FR16.2 | NFR4.6・NFR4.9 |

## TS1 新しい依存を足さない

- Gradle の依存・npm のパッケージ・コンテナのイメージは足さない。`backend/gradle.lockfile`・`frontend/package-lock.json` は変えない。
- そのため、ライセンスの確認（team.md の Code Style）と、推移依存の確かめ（`project.md` の学び）は要らない。
- 依存の脆弱性の関門は、今までどおり `./gradlew verify` の中の OSV-Scanner とする。

## TS2 使う道具（既存）

| 用途 | 道具 | 置き場・版 |
|---|---|---|
| #7 の直し | JUnit・AssertJ・Spring Test、既存の監査の書き込みの時間を測る `LongSupplier` の差し替えの口 | `backend/src/test/java/cherry/mastersmith/audit/testsupport/AuditWriteBarrierConfig.java` と同じ形 |
| 止める相手の特定 | 既存の `TestRoleBarrier` の `Gate`（スレッドの記録を足す） | `backend/src/test/java/cherry/mastersmith/role/testsupport/TestRoleBarrier.java` |
| 画面のテストの直し | Vitest・Testing Library の `waitFor` | `frontend/src/app/admin-forbidden/AdminForbiddenProvider.test.tsx` |
| 負荷 | `grafana/k6:2.3.0`（版とダイジェストで固定済み） | `perf/README.md` の `k6d` |
| 台本 | bash・zsh・docker compose、`caffeinate -dims`・`pmset` | `perf/`、`docker/perf/compose.yaml` |

## TS3 H2 の版と設定を保つ

- H2 は 2.4.240 のまま上げない。
- `-Dh2.compactThreads=1`（`Dockerfile` 52 行）と `DEFRAG_ALWAYS=TRUE`（`backend/src/main/resources/application.yaml` 166 行）は保つ（`project.md` の Tech Stack）。
- `RETENTION_TIME` は、使い捨ての環境の接続先の上書きでだけ試す。渡し方（URL の設定か `INIT` か）はコード生成で確かめ、縛りは `security-requirements.md` の NFR4.7 に従う。
- 効いた手を配備物に取り込むかは、Build and Test の結果の時点で依頼者に諮る（BR4.2）。

## TS4 記録のループの動かし方

- 記録のループは、1つのループをコンテナの中で回す。1秒ごとに `docker exec` を起こさない（functional-spec 3節）。
- ループは、アプリのイメージにある `bash` で動かす。健全性の確かめ（`docker/perf/compose.yaml` の healthcheck）と同じ前提で、`Dockerfile` は変えない。

形の例（説明のための断片。名前と列の並びはコード生成で確定する）:

```bash
docker exec mastersmith-perf-app-1 bash -c '
  cg=/sys/fs/cgroup
  while :; do
    printf "%s\t%s\t%s\t%s\n" "$(date -u +%H:%M:%SZ)" "$(cat $cg/memory.current)" \
      "$(cat $cg/memory.peak)" "$(awk "/^anon /{print \$2}" $cg/memory.stat)"
    sleep 1
  done' >> build/perf-results/<名前>-memory.tsv
```

- ループは読むだけにし、書くのはホームの側だけにする（NFR4.9）。

## 上流との差

無し。

## 後の段に回すこと

- コード生成の計画で決めること: R-03〜R-06 と、BR4.1 の確かめ（`PERF_DB_URL` の効き、`RETENTION_TIME` の渡し方）。
- Build and Test で行うこと: `memory.peak` の2回の測りと、ページキャッシュを減らす手の試し。

## Sources

- 質問と確認: `aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/nfr-requirements/nfr-requirements-questions.md`（Looks correct）
- 機能設計: `aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/functional-design/functional-spec.md`（2節・3節・4節）、`aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/functional-design/rules.md`（BR4.1・BR4.2）
- コードと設定: `Dockerfile`（52 行）、`backend/src/main/resources/application.yaml`（161〜166 行）、`docker/perf/compose.yaml`、`perf/README.md`（170〜183 行の `k6d`）
- 決まり: `aidlc/spaces/default/memory/team.md`（Code Style の依存の追加）、`aidlc/spaces/default/memory/project.md`（Tech Stack）
