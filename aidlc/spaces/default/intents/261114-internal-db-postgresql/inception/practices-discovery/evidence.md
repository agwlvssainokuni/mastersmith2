# Evidence

> Intent `261114-internal-db-postgresql` の Practices Discovery のリードの下書き（Step 2、担当 `aidlc-pipeline-deploy-agent`）。協力者は無効のため、支援役のレビュー（Step 3）は無い。面談（Step 4）の答えと統合の判断は、統合の回（Step 5）で書き足す。

## 前提

- Brownfield の再実行。`aidlc/spaces/default/memory/team.md` の5つの節（Way of Working・Walking Skeleton・Testing Posture・Deployment・Code Style）は確定した内容で、今の基準としてそのまま引き継いだ。`project.md` も読み、そこにある決まりは `team-practices.md`・`discovered-rules.md` に重ねて書いていない。
- 依頼の中身: 内部DB（今は組み込みの H2）を PostgreSQL でも動かせるようにする。内部DB と対象DB は別扱いのまま変えない。今回は PostgreSQL だけで、MySQL・MariaDB は後の Intent。
- 記録のコミット: `0da3d9cacf7897128b173198eaf929869232fce8`（`git rev-parse HEAD`）。

## 読んだもの

### コードの知識の置き場（`aidlc/spaces/default/codekb/mastersmith2/`、2026-11-14 の Focused scan で更新済み）

| 文書 | 読んだ所 | 推したこと |
|---|---|---|
| `code-structure.md` | 「内部DB の移行（K-144）」「内部DB のエンティティの写し方（K-149）」「内部DB に頼る置き場」 | 移行は1つの置き場（V1〜V14）で DB の種類ごとの書き分けが無い。書き分けの置き場は設計の論点で、進め方の質問にはしない |
| `technology-stack.md` | K-150（内部DB の部品）・版の固定先の表・CI の実行環境・K-139 | `flyway-database-postgresql` が lockfile に無く、足すなら新しい依存になる（`team.md` の Code Style のライセンスの確かめが当たる）。内部DB 用の PostgreSQL のイメージの固定先はどこにも無い。見本の対象DB の `postgres:18.6@sha256:e3d24f69…` は `compose.yaml`・`docker/perf/compose.yaml`・`TargetDbImages` の3か所で同じ |
| `dependencies.md` | 「内部DB への依存（K-150）」・CI と Dependabot の確かめ（2026-11-14） | CI のジョブに DB のサービスの定義は無く、内部DB は H2 のファイル、対象DB は Testcontainers。Dependabot の `docker-compose` の項は `compose.yaml` だけを見る |
| `code-quality-assessment.md` | K-151（テストが内部DB の H2 に頼る）・K-152（H2 だけの運用の道具と文書）・K-153・CI の節 | `*IT` 278 本のうち 275 本が H2 の内部DB を起動する。全部を PostgreSQL でも流すと `verify` と CI の時間が大きく増える（測っていない）。バックアップ・戻し・件数の数え・詰め直し・負荷の試験の道具は H2 のファイルが前提 |
| `architecture.md` | 「内部DB の作り（K-146〜K-148）」・K-154 | 接続先は環境変数（`MASTERSMITH_DB_URL`・`MASTERSMITH_DB_USERNAME`・`MASTERSMITH_DB_PASSWORD`）で替えられる形だが、今のままでは PostgreSQL で動かない。K-154 の (d)（流す範囲と時間）・(e)（イメージと固定先の一覧）が進め方の論点に当たる |
| `business-overview.md` | 冒頭・利用者と役割の表・所見の一覧 | 運用する人（今は依頼者）が H2 の内部DB のバックアップ・戻し・詰め直し・監査の複写の読み取りを `docs/internal-db.md`・`docs/operations.md` の手順で行っている。配備の手順の書き分けが進め方の論点になる |

### そのほか（読むだけ）

- `aidlc/spaces/default/knowledge/aidlc-shared/` のすべて（`team.md` の Knowledge Pointers の決まり）。とくに `study-internal-db-postgresql.md`（下調べ。`lock_timeout`・`ON DELETE RESTRICT`・失敗の後のトランザクション・移行の書き分けの候補・テストと CI の時間）と `ops-notes.md`（配備・運用・負荷の試験の細かい学び。内部DB の複写の数え方は H2 の JDBC の道具が前提）。
- `.github/workflows/ci.yml`: ジョブ `verify` は `./gradlew verify -PitForks="$IT_FORKS"`（既定 3）、`timeout-minutes: 60`。DB のサービスの定義は無い。
- `.github/dependabot.yml`: イメージのダイジェストでの固定と、Dependabot が見ない固定先の一覧（説明文）。内部DB 用の PostgreSQL は載っていない。
- `compose.yaml`: `app` は `.env` を読み、内部DB はボリューム `mastersmith-data:/app/data` の H2 のファイル。見本の対象DB の `targetdb-postgres` は profile で起動し、資格情報は `.env.targetdb` から渡す（アプリの `.env` と分けている）。
- `docs/verification.md`: CI の検査の job の時間（2026-11-11、`it_forks` 3 の中央値 21分32秒、2 の中央値 28分40秒）。`team.md` の線は約 42 分。
- `git log`（最近の 15 件）: 前の Intent `261110-deps-forks-review` まで、`develop` への統合・記録だけのコミット・段ごとの承認の形が `team.md`・`project.md` の決まりどおりに続いている。進め方を変える兆しは無い。

## リードの判断（下書き）

- 5つの節は `team.md` の文を1字も変えずに引き継いだ（照合のため、`team.md` の5つの節の各行が下書きにあることを文字どおり確かめた）。
- 足したのは2行だけ:
  - Way of Working の `- **Integration branch**: develop`。`team.md` の「日々の統合先は `develop` とする」の文が指す枝で、Bolt の作業ブランチの統合先も `develop`。`main` はリリースの取り込み先で、日々の作業の統合先ではないため、1つに決まる。
  - Testing Posture の `- **Methodology evidence**: ...`。`Methodology`（test-after）と `Ordering` は `team.md` にすでにあり、そのまま。
- 内部DB を PostgreSQL でも動かすことで、今の文のままでは合わなくなりうる行（Testing Posture の「内部DB（組み込みの H2）を使うテストは…組み込みの H2 で行う」、Way of Working の「コンテナで動かす対象DB のテストだけを飛ばす」、Deployment の「内部DB の複写」の前提など）は、下書きでは変えず、面談で確かめてから直す。

## 決まりからすでに答えが決まる点（面談の質問にしない）

- 新しい依存（Flyway の PostgreSQL の部品など）は、採用の前にライセンスを確かめ、Apache License 2.0 と違えば ADR に理由を残す。推移依存で既存の部品の版を引き上げないかを依存の木で確かめ、lockfile と OSV-Scanner の対象に載ることを確かめる（`team.md` の Code Style、`project.md` の Corrections）。
- コンテナのイメージは版のタグと複数アーキテクチャの index のダイジェストで固定し、固定先の一覧を `.github/dependabot.yml` に書く（`team.md` の Deployment）。どのイメージを使うかだけを面談で確かめる。
- テストを足した Bolt の後と Build and Test で `verify` と CI の時間を測り、約 42 分を超えたら分け方を諮る（`team.md` の Testing Posture）。
- DB スキーマの変更は前進のみで、1つ前の版のアプリが動く後方互換を保つ。既存の H2 の適用済みの移行は書き換えない（`team.md` の Deployment、`code-structure.md` の K-144）。移行の置き場の分け方そのものは設計の論点。
- 秘密情報は `.env` などに置き、コミットしない・ソースに書かない（`project.md` の Forbidden）。
- PostgreSQL の SQL の分岐を足して分岐のカバレッジが下がるなら、除外ではなくテストを足して満たす（`team.md` の Testing Posture）。
- 機能の中身（どの SQL をどう書き換えるか、`lock_timeout` の渡し方、`ON DELETE RESTRICT` の扱い、H2 から PostgreSQL へのデータの移し替えを範囲に入れるか、既定の内部DB を H2 のままにするか）は要件・設計の段に回す。

## 面談で確かめたい点（候補。指揮役が質問を作るための材料）

P1〜P6 の中身（根拠・推奨とその理由・他の選択肢）は、リードの返答の要約に書いた。ここには見出しだけを置く。

- P1: 内部DB の PostgreSQL の結合テストを、どの範囲で、`verify`・`verifyQuick`・CI のどこで流すか（`team.md` の「内部DB のテストは組み込みの H2 で行う」の行の書き換え、42 分の線）
- P2: コンテナの実行環境が無いときに飛ばしてよいテストの範囲を、内部DB の PostgreSQL のテストにも広げるか
- P3: 内部DB の PostgreSQL のコンテナのイメージの版とダイジェスト（見本の対象DB と同じものを使うか、固定先の一覧）
- P4: 手元の配備で内部DB を H2 と PostgreSQL のどちらで動かすか、配備の手順（バックアップ・戻し・件数の数え）をどう書き分けるか
- P5: Intent の最後の E2E（と、手元で行う負荷の試験）を、内部DB の PostgreSQL でも流すか
- P6: 固い制約の候補（内部DB の PostgreSQL の動きを H2 で代用して確かめない、内部DB の接続情報の扱い）

## 残る不確かさ

- 内部DB の結合テストを PostgreSQL で流したときの時間は測っていない（`./gradlew` を流していない）。P1 の推奨の時間の見込みは、CI の今の中央値（約 21.5 分）からの推定。
- `flyway-database-postgresql` のライセンスと、Spring Boot 4.1.2 の BOM が版を管理するかは確かめていない（Maven Central を見ていない）。
- E2E と負荷の試験の台本が内部DB の H2 にどこまで頼るか（`frontend/playwright.config.ts`・`perf/`）は、`code-quality-assessment.md` の K-152 の範囲でしか読んでいない。

## Sources

- `aidlc/spaces/default/codekb/mastersmith2/code-structure.md`
- `aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`
- `aidlc/spaces/default/codekb/mastersmith2/dependencies.md`
- `aidlc/spaces/default/codekb/mastersmith2/code-quality-assessment.md`
- `aidlc/spaces/default/codekb/mastersmith2/architecture.md`
- `aidlc/spaces/default/codekb/mastersmith2/business-overview.md`
- `aidlc/spaces/default/knowledge/aidlc-shared/study-internal-db-postgresql.md`
- `aidlc/spaces/default/knowledge/aidlc-shared/ops-notes.md`（ほか `aidlc/spaces/default/knowledge/aidlc-shared/` の checklist のすべて）
- `aidlc/spaces/default/memory/team.md`・`aidlc/spaces/default/memory/project.md`
- `.github/workflows/ci.yml`・`.github/dependabot.yml`・`compose.yaml`・`docs/verification.md`・`git log`
