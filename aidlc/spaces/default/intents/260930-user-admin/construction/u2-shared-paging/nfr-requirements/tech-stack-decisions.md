# Tech Stack Decisions — U2 ページ送りの共通化（u2-shared-paging）

U2 は新しい依存を足しません。既存の技術（コードの知識ベース `aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`、`gradle/libs.versions.toml`、`frontend/package.json`）の上で、既存のコードを移すだけです（この段の設計の要点 9、依頼者の Looks correct）。新しい依存が無いため、ライセンスの確かめ（`team.md` の Code Style）は要りません。出典の略号は `security-requirements.md` と同じ。

## 使う技術

| 用途 | 技術 | 選んだ理由 | 関係する要件 |
|---|---|---|---|
| サーバーのページ送りの計算（Paging） | Java（既存の版）の素のクラス（`cherry.mastersmith.common.paging`） | 状態を持たない純粋な関数のため、Spring の部品にしない。`InvitationPaging` の口・引数・結果・例外を変えずに移す | NFR9.1・NFR9.2・NFR11.1 |
| 一覧の API の入力の誤りの応答 | 既存の Problem Details の仕組み（`@RestControllerAdvice`、共通の `VALIDATION_FAILED`） | 新しい code を足さず、既存の応答の形を使い回す | NFR9.3 |
| 画面のページ送りの計算（UiPaging） | TypeScript（既存の設定、`strict`）の名前つきの export（`frontend/src/shared/paging/paging.ts`） | 機能どうしの直接の import を避けるため、複数の機能で共有するものを `src/shared/` に置く（`team.md` の Code Style） | NFR11.1 |
| 観測 | 既存の Micrometer・SLF4J の構造化ログ | 新しい指標・ログを足さない | NFR5.2 |

## テストの道具と品質の関門

| ID | 要件 | 確かめ方と持ち主の段 | 出典 |
|---|---|---|---|
| NFR9.6 | 性質ベースのテストの試行の数は、jqwik（1.10.1）が既存の `InvitationPagingTest` と同じ `@Property(tries = 500)` を、移す性質と足す3つの性質（FS の 6節）にそろえる。fast-check（4 系）は既存の `paging.test.ts` と同じ既定の回数（100 回）で、移す性質と足す4つの性質に当てる。どちらも数回の整数の計算で、`./gradlew verify` の時間に効かない | 移したテストの宣言をコード生成のレビューで確かめ、`./gradlew verify` で通す（code-generation） | NFR9、TM の Testing Posture（性質ベースのテスト）、FS の 6節、要点 11 |
| NFR9.7 | 失敗のときの乱数の種は、既存の仕組みのまま記録する。jqwik は失敗の報告に seed を出し（`backend/src/test/resources/junit-platform.properties` の `jqwik.database` は `build/` の下でリポジトリに残さない）、`@Property(seed = "...")` で再現する。fast-check は失敗の報告に seed と path を出し、`fc.assert` の第2引数に渡して再現する。Gradle のテストの出力は失敗の詳細をすべて出す設定（`backend/build.gradle.kts`）のため、CI の記録にも種が残る。移すテストの先頭の説明文に、この再現の仕方を書く | 移したテストの先頭の説明文と、既存の設定が変わらないことをコード生成のレビューで確かめる（code-generation） | NFR9、TM の Testing Posture（乱数の種）、要点 12 |
| NFR9.8 | `invitation.domain` は `packagesJudgedByTotal` に無く、すでにパッケージごとの下限（行 80%・分岐 70%）の対象である。`InvitationPaging` が抜けた後もこの下限を満たす。下回ったときは、計測の除外を増やさず、`invitation.domain` のほかのクラスのテストを足して満たす | コード生成の計画で今の値を実測し、切り替えの後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で測り直して値を記録する（code-generation・build-and-test） | NFR9、TM の Testing Posture（カバレッジ）、機能設計の承認の場の決定 R-03、要点 13 |
| NFR9.9 | `common.paging` は新しいパッケージのため、自動でパッケージごとの下限（行 80%・分岐 70%）の対象になる。移す単体テストで parsePage の分かれ道と2つの例外を通して満たす。`packagesJudgedByTotal` の一覧は増やさない、変えない。`package-info.java` は置かない。今あるのは `common/package-info.java` だけで、`common.error`・`common.health` などの下位のパッケージには置かない慣習のため（承認の場の決定、R-05） | NFR9.8 と同じ実測で、`common.paging` の値を記録する（code-generation・build-and-test） | NFR9、TM の Testing Posture（カバレッジ）、FS の 7節、要点 14 |
| NFR9.10 | 画面のカバレッジは全体の合計（行 80%・分岐 70%、`frontend/vitest.config.ts` の `thresholds`）のまま判定する。ファイルを `src/shared/paging/` へ移しても計測から外さない | `./gradlew verify` の画面のカバレッジの検証（code-generation・build-and-test） | NFR9、TM の Testing Posture（カバレッジ）、要点 15 |
| NFR9.11 | 新しい依存を足さない。サーバーは既存の JUnit 5・AssertJ・jqwik、画面は既存の Vitest・fast-check で作る。テストの説明文は英語、サーバーの単体テストは `XxxTest`（`backend/src/test/java/cherry/mastersmith/common/paging/PagingTest.java`）、画面のテストは対象と同じ場所の `*.test.ts`（`frontend/src/shared/paging/paging.test.ts`）に置く | `gradle/libs.versions.toml`・`backend/gradle.lockfile`・`frontend/package.json`・`frontend/package-lock.json` に変更が無いことをコード生成のレビューで確かめる（code-generation） | NFR9、TM の Testing Posture・Code Style、要点 9 |

使う道具は既存のとおり: JUnit 5・AssertJ・jqwik・ArchUnit（既存の層と機能の境界のテストを書き換えない）・JaCoCo・SpotBugs ＋ FindSecBugs・Spotless（palantir-java-format）・Vitest・`@vitest/coverage-v8`・fast-check・oxlint・ESLint・Prettier・Gitleaks・OSV-Scanner。性能の試験の道具（k6）には、この単位の新しい場面を足しません（NFR5.1）。

## 選ばなかったもの

| 候補 | 選ばなかった理由 |
|---|---|
| Spring Data の `Pageable`・`Page` に置き換える | 口と振る舞い（page の検証の規則、最後のページより後の扱い）が変わり、招待の振る舞いを変えない決まり（BR3.5・NFR9.5）と契約 C2 に反する |
| Paging を Spring の部品（`@Component`）にする | 状態を持たない純粋な関数で、注入の必要が無い。既存の `InvitationPaging` と同じ素のクラスのまま移す |
| 性質ベースのテストの試行の数を増やす | 500 回・100 回で今の性質を十分に確かめている。既存のテストにそろえ、読み手が迷わないようにする |
| 新しいページ送りの部品（画面）を足す | 画面の部品は make-you-chic-ui と使う側の画面の受け持ちで、U2 は計算だけを移す。文言（訳の鍵）を移すかはコード生成の計画で決める（FS の 7節） |
