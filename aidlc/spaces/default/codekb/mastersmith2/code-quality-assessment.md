# コードの品質の評価（mastersmith2）

確かめ方はファイルの読み取りだけで、Gradle・npm・Docker は実行していない。件数はファイルを数えた値である。

## テスト

| 対象 | 置き場 | ファイル数 | 道具 |
|---|---|---|---|
| バックエンドの単体 | `backend/src/test/java/`（`*Test`） | 100 | JUnit 5・AssertJ・jqwik・ArchUnit |
| バックエンドの結合 | 同上（`*IT`） | 69 | Spring Boot Test（`@SpringBootTest` 45 クラス、うち `RANDOM_PORT` 35、`@DirtiesContext` 0）・Testcontainers |
| 画面 | `frontend/src/`（`*.test.ts(x)`） | 47 | Vitest・Testing Library・user-event・vitest-axe・fast-check |
| E2E | `frontend/e2e/` | 4 | Playwright（`verify` と CI の外） |

- カバレッジの下限: JaCoCo の全体（行 80%・分岐 70%）と、新しいパッケージだけのパッケージごとの下限（既存の 22 パッケージは一覧で外す）。画面は `@vitest/coverage-v8` の `thresholds`。
- テストの JVM（`backend/build.gradle.kts` 111〜128 行）: すべての `Test` タスクで最大ヒープ `1g`、`-XX:+EnableDynamicAgentLoading -Xshare:off`。`integrationTest` は1つの JVM で、`maxParallelForks` の指定は無い。
- 今回の4件に関わる既存のテスト: `config/H2DefragOnCloseTest`、`dslmanage/repository/DslManageRepositoryIT`、`dslmanage/web/DslAdminApiIT`・`DslConcurrencyIT`、`dslmanage/service/DslStartupIT`、`auth/web/AccessTokenApiIT`。

## 検査・CI・文書

- Java: Spotless（palantir-java-format、ライセンスヘッダー）、SpotBugs＋FindSecBugs（`SQL_` で始まる指摘は priority にかかわらず止める）、ArchUnit の層の検査。
- 画面: Prettier・oxlint・ESLint・Stylelint・`tsc`、ライセンスヘッダーの検査スクリプト。
- CI: `.github/workflows/ci.yml`（`develop` へのプッシュで `./gradlew verify`）、Dependabot、pre-commit（Gitleaks とフォーマット）。
- 文書: `README.md`（既知の制約・戻し方を含む）と `perf/README.md`。Javadoc は日本語で決まりの番号と理由を書く形がそろう。TODO・FIXME・HACK は `backend/src`・`frontend/src` に 0 件。

## 技術的負債（今回の4件）

各項目は「確かめた事実」と「未検証の仮説」を分けて書く。仮説は要件・設計の段で確かめる。

### TD-1 動いている間、内部DB のファイルが伸び続ける（依頼の1件目）

確かめた事実:

- 本文は `dsl_previews.yaml_bytes`・`dsl_applied_revisions.yaml_bytes` の `BINARY LARGE OBJECT` に置く（`V5__u4_dsl_management.sql`）。
- 投入は固定の鍵の1行への `MERGE`（`DslPreviewRepository` の `PLACE_SQL`）で毎回新しい本文を書く。適用は `INSERT ... SELECT` でプレビューの本文を履歴へ写し（`DslAppliedRevisionRepository` の `COPY_SQL`）、同じトランザクションでプレビューの行を消し、21 件目以降の古い履歴を消す（`DslRecordStore.apply` 117〜130 行）。1回の投入と適用で本文が少なくとも2回書かれる。
- 接続先は `jdbc:h2:file:./data/mastersmith;DEFRAG_ALWAYS=TRUE`（`application.yaml` 137 行）。詰め直しは閉じるときだけで、動いている間に詰め直す設定は無い。
- `README.md` 452 行が既知の制約として記録している: 10MB の DSL の投入と適用で1回あたり約 10.8MB、21 回で約 278MB、40 回で約 483MB、頭打ちにならない。止めて起動し直すと約 16MB。
- `V5` の説明の見積もり（プレビュー1件と履歴 20 件で最大約 210MB）は、保存する本文の論理的な大きさで、ファイルの大きさではない。

未検証の仮説:

- 動いている間に消した場所が再利用されない理由（H2 2.4.240 の MVStore が古いチャンクを保持する条件、詰め直しの条件、LOB の消し方）は、コードからは確かめられない。
- 直し方の候補（どれも未検証）: 本文を1か所に置きプレビューと履歴は識別で指す表の形、本文の圧縮、本文を DB の外のファイルに置く、H2 の設定による動いている間の詰め直し。

守る制約:

- 表の形を変えるときは V7 以降・前進のみ・1つ前の版のアプリが動く後方互換。1つ前の版は `yaml_bytes` の列を読むため、列を消す・中身の形（圧縮など）を変えると前の版のイメージに戻せなくなる（README の「戻し方」）。
- 本文は受け取ったバイト列のまま保存し、識別（SHA-256）とダウンロードがバイト単位で一致する。適用は1つのトランザクションで履歴への追加・プレビューの削除・古い履歴の削除を行い、確定の後だけモデルの差し替えと監査の出来事を行う。

### TD-2 10MB の DSL の投入とログインの重ねでメモリが上限に近づく（依頼の2件目）

確かめた事実:

- 1回の投入で同時に生きる形: 要求の本文の `byte[]`（`DslAdminController.submit` の `@RequestBody byte[]`）、`SafeYamlParser.decode` の文字列、SnakeYAML の `Composer` が作る文書全体の節の木（位置の `Mark` 付き）、Jackson の木と節ごとの JSON Pointer を鍵にした `PositionMap`、検証の途中のもの、`DslModel`。
- `DslContent` は作るときと `yamlBytes()` のたびに本文を `clone()` する。リポジトリは Hibernate が読んだ配列から `DslContent` を作り、ダウンロードや戻しで `yamlBytes()` がもう一度複写する。
- 大きなモデルを2つ持ち続ける: 適用中（`ActiveDslModelStore`）とプレビュー（`DslPreviewCache`）。どちらも `AtomicReference` の1件。
- 重い操作は同時に1つ（`DslHeavyOperationGate`）だが、ログインなどほかの要求とは重なる。
- JVM は `-XX:MaxRAMPercentage=75.0`（`Dockerfile` 41 行）で、コンテナの上限 既定 2g（`compose.yaml` 63 行・`docker/perf/compose.yaml` 57 行）なら最大ヒープは約 1.5GiB。
- 前の Intent の測定（`dslMixed`）: `anon` 1,894MiB（上限の 93%）、`memory.events` の `max` 2,340 回。

未検証の仮説:

- `anon` の値は、ヒープが最大近くまで広がった分とヒープ以外の分の合計と読めるが、内訳は測っていない。
- `memory.events` の `max` はコンテナの上限に当たって回収が起きた回数で、ページキャッシュ（H2 のファイルの書き込みで増える）の回収も含むため、プロセスのメモリが足りないことと同じとは限らない。
- 内訳は GC のログ・NMT・ヒープの内訳で分けて確かめる必要がある。直す前と直した後は、前の Intent と同じ `dslMixed` の条件で、`perf/dsl-timing.sh` の `anon`・`file` を分けた記録の形で比べるのがよい。

守る制約:

- DSL を信頼できない入力として扱う決まり（大きさ・深さ・別名の上限、タグの拒否、重複キー、位置つきの誤り）を弱めない。`LimitingParser` と `PositionMap` はこの決まりのためにある。
- ログに本文を出さない。

### TD-3 `AccessTokenApiIT` の一時的な失敗（依頼の3件目）

確かめた事実:

- テストは `RANDOM_PORT` で起動したアプリに `HttpTestClient`（JDK の `HttpClient`、版の指定なしで既定の HTTP/2 を試す、接続の待ち 5 秒・要求の待ち 30 秒、`http://localhost:<番号>`）で送る。`@BeforeEach` ごとに新しい `AuthApi`（新しい `HttpClient`）を作るため、テストをまたいだ古い接続の再利用は起きない。
- 結合テストは1つの JVM（最大ヒープ 1g）で、Spring の文脈のキャッシュの上限は既定のまま。`RANDOM_PORT` の文脈はそれぞれ組み込みの Tomcat と Hikari のプール（上限 30、`minimum-idle` の指定なし）を持ち続ける。
- 前の Intent の記録: 6 件すべてが 0.587 秒のうちに `Connection reset`・`header parser received no bytes` で落ち、同じ時刻に Gradle の作業プロセスとの接続も時間切れだった。クラスだけの再実行 3 回と verify の2回目は通った。

未検証の仮説（原因の候補）:

- (a) PC の負荷による一時的な失敗。
- (b) 空いた番号の衝突: Tomcat は全アドレスで待ち受け、`localhost` への要求は 127.0.0.1 へ行く。colima が Testcontainers のコンテナの番号を PC の 127.0.0.1 に転送していると、同じ番号を先に特定のアドレスで取った相手に要求が届き、すぐに切られうる。
- (c) 1つの JVM にキャッシュされた多くの文脈（Tomcat と Hikari のプール）による資源の使い過ぎ。
- 1回目の実行のテストの報告（`backend/build/test-results/`）は、その後の実行で上書きされている見込みで、後から確かめる材料は残っていない可能性が高い。

守る制約:

- 原因が分からないまま「直した」とはできない（`team.md` の「不安定なテストは原因を直すまで統合しない」）。再現の手段と、再現できなかったときの扱いを要件の段で決める。

### TD-4 `perf/dsl-timing.sh` の説明の食い違い（依頼の4件目）

確かめた事実:

- 33 行の説明が「（既定 2g。配備と同じ値。要件の条件は 1g）」のまま。既定の値（55 行 `MASTERSMITH_CONTAINER_MEMORY:-2g`）と `perf/README.md` 109 行はすでに 2g で、直すのは説明の文だけ。

## その他の所見

- `DslLifecycle` は 462 行で、DSL の管理の操作をすべて持つ（責務はそろっているが大きい）。
- 警告の抑止は2か所（`common/observability/SanitizingLogRecordExporter.java`・`common/error/web/DefaultErrorResponseWriter.java`）。
