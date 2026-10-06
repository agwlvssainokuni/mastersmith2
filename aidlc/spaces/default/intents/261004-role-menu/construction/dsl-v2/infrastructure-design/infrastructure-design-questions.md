# 基盤の設計の質問 — U2 dsl-v2

単位 U2 dsl-v2（library。画面 `features/dsl` の小さな変更を含む。B1 で作る）の基盤の設計の前に、決まっていない点を確かめます。この単位が作る成果物は、段の定義の `produces_kinds` により `cicd-pipeline.md` と `traceability.json` の2つです（library のため、`infrastructure-specification.md`・`monitoring-design.md` は作りません）。

読んだもの: この単位の承認済みの NFR 設計（`nfr-design/security-design.md`・`logical-components.md`・`traceability.json`、`security-design.md` 6節の承認の場の直し R-01）、NFR 要件（`nfr-requirements/security-requirements.md`・`tech-stack-decisions.md`）、機能設計（`functional-design/functional-spec.md`）、`inception/domain-design/components.md`、`inception/contract-design/contract-summary.md`（C3）、`inception/delivery-planning/bolt-plan.md`（B1）、既存の CI とビルド（`.github/workflows/ci.yml`・`build.gradle.kts`・`backend/build.gradle.kts`）、`compose.yaml`・`docker/perf/compose.yaml`・`Dockerfile`、`perf/README.md`・`perf/dsl-timing.sh`、README、`team.md`・`project.md`。この PC の実行環境は読み取りだけで確かめた（colima: CPU 4・メモリ 6GiB）。

## 決まっていること（質問にしない）

### 基盤の範囲

- 配備先が決まるまで、基盤の設計は開発者の PC 上のコンテナの範囲に限り、クラウドの基盤（IaC・検証環境・警報の通知の先）は作らない（`project.md` の Deployment）。
- 内部DB の表・Flyway の移行・API の道と数は変えない（`logical-components.md` 3節）。`compose.yaml`・`docker/perf/compose.yaml`・`Dockerfile`・`.env.example` も変えない。本番のヒープは今のまま（`mem_limit` の既定 2g、`Dockerfile` の `MaxRAMPercentage=50.0`。`compose.yaml` と `docker/perf/compose.yaml` の既定はどちらも 2g で、本番と使い捨ての環境がそろっている）。
- ログは、起動時の WARN（深すぎる枝を落としたとき）と既存の ERROR・WARN だけで、新しい指標・警報・ダッシュボードは足さない（`security-design.md` 4.8）。

### ビルドの変更（設計で決まっている作業）

- JSON Schema は `dsl-schema-v2.json` に替え、`dsl-schema-v1.json` は置かない（機能設計 BR1.7、`security-design.md` 4.3）。そのため B1 で、ファイル名を持つ次の箇所をそろえて直す（コードで確かめた今の参照の一覧）。

| 箇所 | 今の参照 | 直す中身 |
|---|---|---|
| `backend/build.gradle.kts` | `dslSchemaSource`（`processResources` の `static/dsl` への複写）と `verifyDslSchemaInWar`（WAR の中の2か所の照合）が `dsl-schema-v1.json` を名指し | `dsl-schema-v2.json` に替える。仕組み（正本1つ・ビルドで複写・WAR の中の2か所を照合）は変えない |
| `DslFormat.SCHEMA_RESOURCE`・`SCHEMA_PUBLIC_PATH` | `dsl/dsl-schema-v1.json`・`/dsl/dsl-schema-v1.json` | v2 に替える |
| `DslSchemaPublicationIT` | `static/dsl/dsl-schema-v1.json` | v2 に替える |
| `frontend/src/features/dsl/submitInput.ts`・`DslSubmitForm.test.tsx` | `/dsl/dsl-schema-v1.json` | v2 に替える |
| README（DSL の書式の節・DSL の画面の節） | v1 の正本・URL・`yaml-language-server` の例 | v2 に替え、v1 の URL は無くなることを書く |

- `verifyDslSchemaInWar` は `verify` の 9 成果物の段（`verifyArtifact`）で毎回流れるため、複写の漏れや食い違いは統合の前と CI で落ちる。

### CI と1コマンドの検査（既にあるものの記録）

- CI（`.github/workflows/ci.yml`）は `develop` へのプッシュで `./gradlew verify` を流すだけの形のままで、変えない（`project.md` の「CI の仕組みが既に実装されているときは記録として書く」）。新しい Actions・秘密・道具の取得は足さない。
- 新しい依存は足さない（SnakeYAML 2.7・networknt 3.0.6・jqwik 1.10.1、画面は fast-check 4.10.2 ほか今の版。`logical-components.md` 6節）。lockfile と OSV-Scanner の対象は変わらない。
- テストの JVM のヒープ（`maxHeapSize = "1g"`）は変えない。別名の爆発のテストのヒープの増えは約 130 MB（試しの T2）で足りる見込み。足りなければ計画に無い変更として依頼者に確かめる（前の Intent と同じ扱い）。
- `./gradlew verify` の段に、この単位で次が足される・書き換わる（どれも既存のタスクの中に入る）。

| verify の段 | 既存のタスク | この単位で足される・書き換わるもの |
|---|---|---|
| 5 単体テスト | `:backend:test`・`frontendTest` | `dsl.parse`・`dsl.service`・`dsl.validate`・`dsl.domain` の単体テスト（版 2 の上限・タグ・重複キー・`$ref`、深さの境界、別名の爆発の 5 秒、jqwik の性質ベースのテスト）、画面 `features/dsl` のテストと vitest-axe |
| 6 結合テスト | `:backend:integrationTest` | DSL の既存の結合テストを版 2 に書き換え、深さの境界・起動時の読み直し・TRACE の漏えいのテストを足す。対象DB の3種類（コンテナ）は今までどおり毎回 |
| 7 カバレッジ | JaCoCo・`@vitest/coverage-v8` | `dsl`・`dslmanage` の手を入れるパッケージは、すでにパッケージごとの下限の対象（`packagesJudgedByTotal` に入っていない） |
| 9 成果物 | `bootWar`・`verifyDslSchemaInWar` | v2 のファイル名での照合 |

### 道具・E2E・統合

- 版 1 の DSL を持つ負荷の道具（`perf/dsl-timing.sh`・`perf/make-large-dsl.mjs`・`perf/make-pattern-dsl.mjs`・`perf/ui/*`・`perf/k6/scenarios.js`）と E2E（`040-dsl-admin.e2e.ts`）の版 2 への書き換えは B1 のコード生成が持つ。Build and Test と Performance Validation は書き換えた道具を流すだけ（`logical-components.md` 6節、読み直しの R-04）。E2E の書き換えは本数に数えない。
- B1 は DSL の画面に手を入れるため、統合の前に手元で `./gradlew e2eTest` を流す（`team.md` の Testing Posture）。E2E は今までどおり CI の外。
- 統合は B1 の短命のブランチから `develop` へ squash で戻す（サブモジュールの固定先の更新は含まない）。

### 配備の段への引き継ぎ（`logical-components.md` 7節のとおり。この段では書き直さない）

- 版を上げる入れ替えでは、版 1 の適用中の DSL が読めず DSL が無い扱いで起動する。入れ替えの後に版 2 の既定の DSL を生成・適用し、版 1 のプレビューが残っていれば先に破棄する（監査に残る操作のため、行う前に依頼者に伝える）。
- 前の版のアプリへの戻しは、内部DB の表を変えないためイメージだけで行える。戻す前に版 2 のプレビューを破棄し、戻した後に版 1 の既定の DSL を生成し直して適用する。戻しの練習で、前の版の起動・DSL が無い状態・生成し直しで使えることの3つを確かめる。
- これらは deployment-pipeline・deployment-execution の段の手順に入れる。この段の `cicd-pipeline.md` には、引き継ぎがあることと出典だけを書く。

## Q1 10 MiB の投入のヒープと同時の投入の確かめを、どの道具で行うか

### 背景

承認済みの NFR 設計（`logical-components.md` 4節、承認の場の直し R-01）は、Build and Test で、本番と同じヒープ（`mem_limit` 2g・`MaxRAMPercentage` 50）の使い捨ての環境で次を確かめると決めています。

- 上限ちょうどの 10 MiB の版 2 の DSL の投入とプレビューの表示（照合を含む）が 10 秒以内に終わり、OutOfMemoryError とコンテナの OOMKilled が無く、`memory.peak` が `mem_limit` を超えないこと。
- 2つの投入を同時に送ったとき、片方が 503 `DSL_BUSY` になり、もう片方が通ること。

今ある道具を確かめると、前者は `perf/dsl-timing.sh`（`docker/perf/compose.yaml` の使い捨ての環境、既定 2g）がすでに 10MB の投入の時間・`memory.peak`・OOMKilled の有無を記録しており、B1 で版 2 に書き換えればそのまま流せます。一方、後者（本番と同じヒープでの同時の投入）を流す口は今の道具にありません（同時の扱いそのものは、テストの部品で待ち合わせる `DslConcurrencyIT` が結合テストで確かめていますが、テストの JVM のヒープ 1g で、本番のコンテナではありません）。

同時の投入は、2つの要求のどちらが先に排他（`DslHeavyOperationGate`）を取るかで結果の並びが変わり、先の投入が終わってから後の投入が届くと両方が通ります。そのため、確かめの道具は「2つの状態コードの組（成功1つ・503 `DSL_BUSY` 1つ）」で判定し、両方が通ったときは重ならなかった回として記録し直す必要があります。

### 選択肢

A. **`perf/dsl-timing.sh` に同時の投入の口を足す（推奨）**。B1 のコード生成で、版 2 への書き換えとあわせて、10 MiB の版 2 の DSL の投入を2つ同時に送るオプション（名前は Code Generation で決める。例 `--busy`）を足す。2つの状態コード・応答の `code`・時間・`memory.peak`・OOMKilled の有無を結果の置き場に記録し、成功1つと 503 `DSL_BUSY` 1つの組で判定する（重ならなかった回は数えず、決めた回数の内でやり直す）。`perf/README.md` に使い方を書く。Build and Test は道具を流して結果を記録するだけ。
  - 理由: 承認済みの設計の確かめ（本番と同じヒープ、同時の投入）を、手順が残る形で満たせる。道具の書き換えの持ち主を B1 とした決定（読み直しの R-04）と合い、後の Intent（業務データの投入など）でも同じ確かめを流し直せる。負荷の道具をリポジトリに置き README に手順を書く今までのやり方（`project.md` の学び）とも合う。

B. **道具は足さず、Build and Test がその場の手順で確かめる**。`perf/dsl-timing.sh` で1件の投入を測った後、同じ使い捨ての環境に `curl` を2つ並べて送り、状態コードと `memory.peak` を記録する。手順と結果は Build and Test の成果物にだけ残す。
  - B1 の作業は増えないが、手順がリポジトリの道具に残らず、流し直すときに手順を書き起こし直す必要がある。

C. **同時の投入の確かめは結合テスト（`DslConcurrencyIT`）に任せ、本番と同じヒープでは1件の投入だけを測る**。
  - 作業はいちばん少ないが、承認済みの設計（`logical-components.md` 4節「2つの投入を同時に送ったとき、片方が 503 `DSL_BUSY` になり、もう片方が通ることを確かめる」）と食い違う。選ぶときは承認済みの文書を書き換えず、差をこの段の成果物に記録する（`project.md` の決まり）。

X. Other (please specify)

[Answer]: A **Mode:** guided

## Consolidated Summary Confirmation

答えのまとめ（dsl-v2 の基盤の設計）:

- Q1 A: B1 で `perf/dsl-timing.sh` に「2つ同時に投入する」オプションを足し、Build and Test で本番と同じヒープ（mem_limit 2g・MaxRAMPercentage 50）の使い捨ての環境に 10 MiB の版 2 の DSL を2つ同時に投入して、状態コードの組（成功1つ・503 `DSL_BUSY` 1つ）・`memory.peak`・OOMKilled の有無を記録する。重ならなかった回はやり直す。
- 決まっていること（JSON Schema を v2 に替えるためファイル名を名指ししている箇所をそろえて直す: `backend/build.gradle.kts` の `dslSchemaSource`・`verifyDslSchemaInWar`、`DslFormat`、`DslSchemaPublicationIT`、`frontend/src/features/dsl/submitInput.ts`・`DslSubmitForm.test.tsx`、README。CI・依存・テストの JVM のヒープ・compose.yaml・docker/perf/compose.yaml・Dockerfile・内部DB は変えない。負荷の道具と E2E 040 の版 2 への書き換えは B1 が持ち、B1 も統合の前に手元で E2E を流す。配備の段への引き継ぎは NFR 設計の 7節のとおりで、この段の成果物には出典だけを書く）は案のとおり。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
