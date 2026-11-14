# Evidence

> Intent `261114-internal-db-postgresql` の Practices Discovery のリード（担当 `aidlc-pipeline-deploy-agent`）の下書き（Step 2）と統合（Step 5）。協力者は無効のため、支援役のレビュー（Step 3）と貢献のファイルは無い。参加者はリードと依頼者（面談、Step 4）だけ。

## 前提

- Brownfield の再実行。`aidlc/spaces/default/memory/team.md` の5つの節（Way of Working・Walking Skeleton・Testing Posture・Deployment・Code Style）は確定した内容で、今の基準としてそのまま引き継いだ。`project.md` も読み、そこにある決まりは `team-practices.md`・`discovered-rules.md` に重ねて書いていない。
- 依頼の中身: 内部DB（今は組み込みの H2）を PostgreSQL でも動かせるようにする。内部DB と対象DB は別扱いのまま変えない。今回は PostgreSQL だけで、MySQL・MariaDB は後の Intent。
- 記録のコミット: 下書きは `0da3d9cacf7897128b173198eaf929869232fce8`、統合は `8e38b6f9e7932c090b10a1d5d3f388a7e8fccda1`（どちらも `git rev-parse HEAD`）。

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

## 面談で確かめた点（下書きの候補）

下書きの候補 P1〜P6 は、`practices-discovery-questions.md` の Q1〜Q6 に当たる。Q7 は Q1 の答えの確かめとして指揮役が足した。

## 面談の決定（Step 4）

答えは `practices-discovery-questions.md` のとおり。読み方は指揮役が依頼者に伝え済みのもので、統合はこの読み方に従った。

| 問い | 答え | 読み方 | 反映先 |
|---|---|---|---|
| Q1 結合テストの範囲と流す場面 | X（H2 の全件は残す。PostgreSQL に固有の結合テストはバリエーションとして別に作り、明示したときに流す） | Q7 と合わせて読む（下の Q7） | `team-practices.md` の Testing Posture の内部DB のテストの行を書き換え |
| Q2 colima が無いときに飛ばす範囲 | A | 内部DB の PostgreSQL の結合テストも対象DB と同じ扱い（警告を出して飛ばす。飛ばした状態では統合しない）。「CI では必ず流す」は、Q1・Q7 と合わせて「明示して CI で流したときは飛ばさない」と読む | `team-practices.md` の Way of Working のコンテナの実行環境の行を書き換え |
| Q3 イメージの版とダイジェスト | A | 対象DB の PostgreSQL と同じ版とダイジェストを使い、一緒に上げる。内部DB 用の固定先（テストの定数・compose）を `.github/dependabot.yml` の固定先の一覧に足す | `team-practices.md` の Deployment に1行を追加 |
| Q4 手元の配備と配備の段の確かめ方 | A | 手元の配備は H2 のまま。PostgreSQL は compose の profile などで選べる形にし、Deployment Execution で使い捨ての PostgreSQL の内部DB で起動・ヘルスチェック・スモークテストを確かめる。`pg_dump` などのバックアップと戻しの手順を文書に足し、そこで1回練習する。既定の内部DB を変えるかは要件の段で決める | `team-practices.md` の Deployment に1行を追加 |
| Q5 E2E を PostgreSQL でも流すか | B | E2E は H2 だけで流す。今の決まりのままで、書き換えは要らない | 変更なし |
| Q6 固い制約 | A, B | 選択肢の文をそのまま固い制約にする | `discovered-rules.md` の Mandated・Forbidden に1つずつ |
| Q7 「明示されたとき」の中身 | B | 明示の指定（名前は設計で決める）を付けたときだけ流し、通常の `verify`・`verifyQuick`・CI（`develop` へのプッシュ）では流さない。流す場面は決めず、必要と判断したときに流す | Q1 と同じ行 |

あわせて、対象DB の結合テストを全件の `verify` と CI で毎回流す決まりは変えない（Testing Posture の該当の行はそのまま）。

## リードの判断（統合）

- 書き換え・足したのは、上の表の「反映先」の4か所だけ。ほかの行は下書き（= `team.md` の今の文）のまま。
- Testing Posture の内部DB のテストの行は、`team.md` の今の行の learned の印と cid の注記を残し、末尾に書き換えの注記を付けた。もとの文の「Walking Skeleton の … 内部DBについてはこの読み方とする」の読みは残した。
- Q6 の Mandated は Testing Posture の書き換えた行と重なるが、固い制約として依頼者が選んだため、両方に置き、行から `project.md` の Mandated を参照する形にした。
- Q7 の B は、Q1 の自由回答の文（「明示されたときに実行、通常は実行しない」）をそのまま決まりにしたもので、流す場面を決めない。

## 残る不確かさ

- 内部DB の結合テストを PostgreSQL で流したときの時間は測っていない（`./gradlew` を流していない）。通常の `verify` と CI では流さないため 42 分の線には響かないが、明示して流したときの時間は分からない。
- `flyway-database-postgresql` のライセンスと、Spring Boot 4.1.2 の BOM が版を管理するかは確かめていない（Maven Central を見ていない）。`team.md` の Code Style のライセンスの確かめと、`project.md` の依存の木の確かめが設計の段で当たる。
- 流す場面を決めない（Q7 の B）ため、PostgreSQL の結合テストが長く流れず、壊れても気づくのが遅れるおそれが残る。依頼者が選んだ形で、どこで流したかを Build and Test などの記録に残すかは、設計・Build and Test の段で確かめる。
- 内部DB の PostgreSQL のイメージを対象DB と一緒に上げるとき（Q3）、上げた版で内部DB の PostgreSQL の結合テストを明示して流すかは決めていない（対象DB の版は「verify を通った最新」の決まりで、内部DB の PostgreSQL のテストは `verify` に入らない）。
- 明示の指定の名前と、CI での明示の実行の形（手動の実行の入力など）は設計の段で決める。
- E2E と負荷の試験の台本が内部DB の H2 にどこまで頼るか（`frontend/playwright.config.ts`・`perf/`）は、`code-quality-assessment.md` の K-152 の範囲でしか読んでいない。E2E は H2 だけで流すため（Q5）、この Intent では響かない見込み。
- 既定の内部DB を H2 から変えるか、H2 から PostgreSQL へデータを移すかは要件の段で決める。

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
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/practices-discovery/practices-discovery-questions.md`（面談の Q1〜Q7 と答え）
