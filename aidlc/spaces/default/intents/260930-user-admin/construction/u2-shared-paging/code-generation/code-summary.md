# Code Summary — U2 ページ送りの共通化（u2-shared-paging）

B2（共通の土台）の U2 のコード生成のまとめ。U2 は、招待の一覧のページ送りの計算を、口と振る舞いを変えずにサーバーの `cherry.mastersmith.common.paging.Paging`（契約 C2）と画面の `frontend/src/shared/paging/paging.ts`（UiPaging、契約 C5）へ移し、招待のサーバーと画面を新しい置き場に切り替えた。新しい API・依存・スキーマ・設定・指標・警報・ログは足していない。経過と実測の詳細は `generation-notes.md`。パスはリポジトリのルートからの相対パス。

- 作業ブランチ: `feature/260930-user-admin-b2`（`develop` の f324c43 から）
- 終えた手順: Step 1〜13 と、Step 14 の記録（このファイル・`source-manifest.json`・`traceability.json`）。Step 14 のコミット（C1・C2）は依頼者の承認を待っている。
- レビュー（iteration 1、READY、Minor 5件）の後、依頼者の決定で R-01（性質ベースのテスト2件の書き換え）と R-02（`Paging` の説明文）を直し、R-03 の持ち主を 9節に書いた（7節・`generation-notes.md` の「レビューの指摘と直し」）。
- U2 の最後のコミットのハッシュ: （C2 のコミットの後に記録する）
- Step 15（B2 全体の統合の前の関門と統合）は、U4（u4-admin-forbidden-ui）の生成と記録の後に、B2 全体で1回だけ行う（verify・`osvScan --rerun-tasks`・E2E・報告の片付け・`develop` への統合）。U2 の依頼では行っていない。

## 1. 作ったもの・変えたもの・消したもの

| 区切り | パス | 種類 | 中身 |
|---|---|---|---|
| C1 | `backend/src/main/java/cherry/mastersmith/common/paging/Paging.java` | 作った | `InvitationPaging` の4つの口（`PAGE_SIZE`・`parsePage`・`pageOf`・`offsetOf`）を名前・引数・結果・例外を変えずに移した。説明文を BR1.1〜BR1.5 と管理の一覧に共通の書き方に直し、`PAGE_SIZE` に画面の値と同じにすることを書いた。レビューの R-02 で、`parsePage` に 10 桁以上も空になること、`pageOf` に int を超えると `ArithmeticException` になることを説明文に足した（本体は変えていない）。import は JDK だけ、Spring の注釈なし、`package-info.java` なし |
| C1 | `backend/src/test/java/cherry/mastersmith/common/paging/PagingTest.java` | 作った | 移した事例すべて＋jqwik の性質3つ＋`PAGE_SIZE` 20 の固定1件（10 のメソッド、展開して 21 件）。説明文に種の再現の仕方を書いた |
| C1 | `backend/src/main/java/cherry/mastersmith/invitation/service/InvitationService.java` | 変えた | import と4つの口の参照先を `Paging` に替えた（フォーマッタが `new InvitationPage(...)` の引数を1行にまとめた） |
| C1 | `backend/src/test/java/cherry/mastersmith/invitation/repository/InvitationRepositoryIT.java` | 変えた | import と `PAGE_SIZE` の2か所の参照先（フォーマッタが2つの代入を1行ずつにまとめた） |
| C1 | `backend/src/main/java/cherry/mastersmith/invitation/domain/InvitationPaging.java` | 消した | `Paging` へ移した |
| C1 | `backend/src/test/java/cherry/mastersmith/invitation/domain/InvitationPagingTest.java` | 消した | `PagingTest` へ移した |
| C2 | `frontend/src/shared/paging/paging.ts` | 作った | 7つの export（`PAGE_SIZE`・`PagerDirection`・`PageRange`・`pageCount`・`pageRange`・`correctedPage`・`pagerButtonDisabledAfter`）と処理を変えずに移した。説明文を BR2.1〜BR2.5 と共通の書き方に直し、`PAGE_SIZE` にサーバーの値と同じにすることを書いた |
| C2 | `frontend/src/shared/paging/paging.test.ts` | 作った | 移した7件＋fast-check の性質4件＝11 件。説明文に seed と path の再現の仕方を書いた。レビューの R-01 で、性質のうち2件（「次へ」が押せなくなる条件、行があれば補正しない）を、実装の式を使わずに境目を見る形に書き換えた |
| C2 | `frontend/src/features/invitation/InvitationList.tsx` | 変えた | import の場所だけ |
| C2 | `frontend/src/features/invitation/useInvitationAdmin.ts` | 変えた | import の場所だけ（Prettier が複数行に折り返した） |
| C2 | `frontend/src/features/invitation/paging.ts` | 消した | `src/shared/paging/` へ移した |
| C2 | `frontend/src/features/invitation/paging.test.ts` | 消した | `src/shared/paging/` へ移した |

一時の確かめ `backend/src/test/java/cherry/mastersmith/invitation/web/RequestLineLimitProbeIT.java`（A-01）は、流して記録した後に消した（ワークツリーに無く、`source-manifest.json` に載せない）。手を入れていないもの: `invitation.web`・`invitation.repository`（本体）、`application.yaml`・`logback-spring.xml`、`backend/build.gradle.kts`、`frontend/vitest.config.ts`、文言、`frontend/e2e/`、`README.md`、依存と lockfile、`vendor/`、既存の ArchUnit のテスト。

## 2. 主な決定

- **振る舞いを変えない移し替え**: 口の宣言と本体（説明文を除く）は移す前と同じ（`git show develop:<パス>` と並べて確かめた）。移す前のファイルは残していない。
- **層の順（test-after）**: サーバーのページ送り → 招待のサーバーの呼び出し側 → 画面のページ送り → 招待の画面 → 構造の検査の順に、実装の後にその層のテストを流し、通ってから次へ進んだ。新しい部品を先に作り、使い手を切り替えてから古い部品を消した（D-4）。
- **`PAGE_SIZE` の一致（Q-C）**: 両側をテストで 20 に固定し、説明文で互いの場所を示した。
- **`packagesJudgedByTotal`・計測の除外・下限は変えていない**。`common.paging` は新しいパッケージとして自動で下限の対象。

## 3. テストの件数とカバレッジの実測

いずれも colima の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡し、`caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` で実測した。基準は Step 1（f324c43、6 分 9 秒）、U2 の後は Step 13（6 分 13 秒）。どちらも BUILD SUCCESSFUL で、対象DB のテストは飛ばされていない。

| 種類 | 基準 | U2 の後 | 差の説明 |
|---|---|---|---|
| バックエンドの単体 | 1283 | 1287 | +4: `InvitationPagingTest` の 17 件を消し、`PagingTest` の 21 件（移した 17 件＋性質3つ＋固定1件）を足した |
| バックエンドの結合 | 587 | 587 | 0（結合テストのクラスは足していない） |
| フロントエンド | 91 ファイル・732 件 | 91 ファイル・736 件 | +4: 性質4つ（ファイルは移しただけ） |

失敗・エラー・飛ばしたものはどれも 0。

| 範囲 | 基準 行・分岐 | 見込み（計画 4.3） | U2 の後 行・分岐 |
|---|---|---|---|
| バックエンドの全体 | 98.8%（5653/5720）・94.5%（2069/2190） | — | 98.8%（5652/5719）・94.5%（2069/2190） |
| `invitation.domain` | 98.4%（246/250）・96.7%（117/121） | 98.3%（233/237）・96.4%（107/111） | 98.3%（233/237）・96.4%（107/111） |
| `invitation.service` | 100.0%（332/332）・93.5%（87/93） | 変わらない | 100.0%（331/331）・93.5%（87/93） |
| `invitation.web` | 97.3%（107/110）・87.0%（20/23） | 変えない | 97.3%（107/110）・87.0%（20/23） |
| `invitation.repository` | 100.0%（2/2）・分岐なし | 変えない | 100.0%（2/2）・分岐なし |
| `common.paging` | — | 100.0%（13/13）・100.0%（10/10） | 100.0%（13/13）・100.0%（10/10） |
| フロントエンドの全体 | 97.44%（2322/2383）・92.67%（1443/1557） | — | 97.44%（2322/2383）・92.67%（1443/1557） |

見込みどおりで、下限（行 80%・分岐 70%）をすべて満たし、足すテストは無かった（残る危険 R1 は当たらなかった）。`invitation.service` と全体の行の数が1つ減ったのは、フォーマッタの行のまとめ直しのため。

Step 13 の verify では、フォーマット・リンタ・ライセンスヘッダー・ビルド・単体・結合・カバレッジ・`gitleaksScan`・`spotbugsGate`・成果物の段がすべて通った（除外は足していない）。`osvScan` は UP-TO-DATE（lockfile を変えていないため）で、Step 15 で `--rerun-tasks` を付けて流す。**この verify の通過を、単位ごとの squash の条件（U2 のすべての変更だけがある作業フォルダで `verify` が通ること）の確かめとする。** C1・C2 は Step 13 の後にソースを変えずにコミットする。

単位のテストの実行（名指し）: `PagingTest` 21 件、Step 6 の `PagingTest`＋`invitation.*` の単体 148 件・結合 60 件、画面の `paging.test.ts` 11 件、`src/shared/paging`＋`src/features/invitation` の 12 ファイル・105 件、構造の検査 45 件、`JsonLogFormatTest` 9 件。すべて通過。typecheck・prettier・oxlint・eslint も通過。

## 4. NFR 設計の申し送り（A-01〜A-04）とレビューの確かめ

| 項目 | 確かめた方法 | 結果 |
|---|---|---|
| A-01 要求の行の上限 | 設定の検索、`spring-boot-web-server-4.1.1.jar` の設定の説明（読み取りだけ）、一時の結合テスト（消した） | `server.max-http-request-header-size` の設定は無く、既定は 8KB（Tomcat 11.0.26 は要求の行とヘッダーの合計に当てる）。page 7,000 文字は 400 `application/problem+json`・`VALIDATION_FAILED`（アプリに届いた）。9,000 文字は 400 `text/html`（Tomcat の誤りの頁、アプリに届かない）。設計の見込みどおり |
| A-02 ログの行の偽造 | 既存の `JsonLogFormatTest`（9 件通過） | `SingleLineMessageJsonProvider` が `message` の改行の並びを「 ⏎ 」に置き換え、ほかの制御文字は JSON でエスケープされる。外部エクスポート（既定で無効）は1行の形に頼らない。コードは変えていない |
| A-03 `PAGE_SIZE` の一致 | テストと説明文 | サーバーは `PagingTest#pageSizeIsTwenty` と既存の `InvitationAdminApiIT`・`InvitationServiceTest` の `size` 20、画面は `paging.test.ts` の `expect(PAGE_SIZE).toBe(20)`。片方だけの変更はその側のテストで止まる。両方の説明文が互いを示す。E2E の 060 の実データの1件の 20 行目の表示は Step 15 で確かめる |
| A-04 `invitation.domain` の見込み | Step 1 と Step 13 の実測 | 3節のとおり見込みと一致 |
| TRACE に出うる範囲（NFR3.1、R-02、R3） | ソースの検索 | `common.paging` は `TraceAspect.EXPRESSION` の対象外で Spring の部品でもない。page の文字列が出うるのは `InvitationAdminController#list(String)`・`InvitationService#list(String)` の引数だけ。拒否は値を持たない `ListResult.InvalidPage` から `BusinessException(VALIDATION_FAILED)` で、応答・ログ・トレースの属性に値を入れていない |
| NFR9.5 招待のテストの中身 | `git diff develop` | 招待の既存のテストの差は import と `PAGE_SIZE` の参照先（とフォーマッタの行のまとめ直し）だけ。移したテストの差は参照先・説明文・足した確かめだけ |
| NFR5.1 問い合わせの回数 | 差分 | 数える1回と読む1回は変わらない |
| NFR5.2・NFR9.11・カバレッジの設定 | `git diff --stat develop -- docker/monitoring gradle/libs.versions.toml backend/gradle.lockfile frontend/package.json frontend/package-lock.json backend/build.gradle.kts backend/config/spotbugs-exclude.xml frontend/vitest.config.ts .gitleaks.toml` | 空 |
| NFR11.1 構造 | `ArchitectureTest`・`*BoundaryArchitectureTest` | 変えずに 45 件通過。`Paging.java` の import は JDK だけ |

## 5. 計画・承認済みの文書との差

承認済みの文書は書き換えていない。

- 計画 8節の D-1〜D-5 のとおり作った。
- **フォーマッタによる行のまとめ直し**: 名前が短くなったため palantir-java-format が `InvitationService#readPage` の引数（6行→1行）と `InvitationRepositoryIT` の2つの代入（2行ずつ→1行ずつ）を、import の場所が長くなったため Prettier が `useInvitationAdmin.ts` の import を折り返し直した。計画の「import と参照先だけ」を書式の検査が求める形で超えたが、式と値は変わらない（依頼者の決定 (1) で受け入れた）。
- **画面の性質ベースのテストの2件（レビューの R-01、依頼者の決定）**: 計画 Step 8 は「『次へ』が押せなくなるのは page が `pageCount(total)` 以上のときだけ（`page >= pageCount(total)` と一致する）」「行が1件以上あれば補正しない」と書いていたが、そのとおりの形は実装の式の書き写しで実装の誤りを見つけられないため、境目を見る形にした: 全件数を最後のページ `last` と最後のページの件数（1〜20）から組み立て、`last` とその後では「次へ」が押せず、`last - 1` と1ページ目では押せること、最後より後のページは行が 0 件なら `last` へ補正し、行が1件以上なら補正しないこと、最後のページは補正しないこと。性質の数（4件）と件数（11 件）は変わらない。計画の本文は変えていない。
- **`Paging` の説明文（レビューの R-02、依頼者の決定）**: 計画 Step 3 の「振る舞いの説明（受ける値・返す値・例外）は変えない」に加え、移す前の説明文に無かった2つの規則（`parsePage` の 10 桁以上は空、`pageOf` の `ArithmeticException`）を書き足した。本体は変えていない。
- **性質の作り方**: 「数字以外・10 文字以上・`"0"` は空」の性質は、計画の例の `@StringLength(min = 10)` ではなく `@Provide` の `Arbitraries.oneOf`（数字の列に数字以外の文字を差し込んだ列・10〜30 文字の数字の列・1〜9 文字の `0` の列）で作った。範囲は同じ。

## 6. 網羅の記録（`traceability.json`）

44 件（OK 34・Deferred 10・GAP 0）。OK の target はすべて実在する1つの実装かテストのファイル。

- AC1.1.1・AC1.1.5・AC1.1.7 の OK は **部分（ページ送りの部分だけ）**（機能設計の R-01）。残りの部分は主の単位 u3-user-admin-api と画面の u5-user-admin-ui が持つ。
- AC1.1.2〜AC1.1.4・AC1.1.6・AC1.1.13 は u3-user-admin-api、AC1.1.8〜AC1.1.12 は u5-user-admin-ui へ Deferred。
- BR1.1〜BR3.5 と NFR3.1・NFR5.1・NFR5.2・NFR9.1〜NFR9.11・NFR11.1 はすべて OK。

## 7. 依頼者の決定

| 決定 | 中身 |
|---|---|
| Q-A（統合の形） | A: 単位ごとの squash（U2 と U4 で2コミット）。条件は U2 の最後のコミットが単独で `verify` を通ること（Step 13 で確かめた） |
| Q-B（要求の行の上限の確かめ方） | A: 一時の結合テストで確かめて記録し、消す（4節） |
| Q-C（`PAGE_SIZE` の一致） | A: 両側をテストで 20 に固定し、説明文で互いを示す |
| Q-D（画面の文言） | A: 移さない |
| 生成の後 (1) | `InvitationRepositoryIT` のフォーマッタによる行のまとめ直しは、NFR9.5 の「中身を変えない」に反しないとみなす |
| 生成の後 (2) | A-01 の結果を、残る危険 R3 の根拠3の確かめとして受け入れる |
| レビュー R-01 | 直す: `paging.test.ts` の性質2件を、実装の式を使わずに境目を見る形に書き換える（テストだけ） |
| レビュー R-02 | 直す: `Paging#parsePage` に 10 桁以上も空、`Paging#pageOf` に `ArithmeticException` を説明文で書き足す（本体は変えない） |
| レビュー R-03 | 持ち主を書く: 8KB を超える要求の扱い（U3 の一覧の入口の確かめ、HTML の 400 を受けた画面の扱い）を 9節に足す |
| レビュー R-04 | 直さない: この作業の決まりの付記は「Claude Opus 5.5」で、レビュー役の指摘（Sonnet 5.5）は誤り。10節の案のままとする |
| レビュー R-05 | B2 の関門（Step 15）の verify で、招待の既存のテストが変更なしで通ることを見る |

## 8. 承認の場で確かめること

- C1・C2 のコミットの区切りと件名（10節）。
- U2 の単位ごとの squash の件名と本文の案（10節）。統合は Step 15 で、U4 の後に行う。
- **レビューの直しと単位ごとの squash の条件**: R-02 の直し（`Paging.java` の説明文）と R-01 の直し（`paging.test.ts`）は Step 13 の verify の後の変更で、名指しのテスト・型・書式・リンタ・`spotlessJavaCheck` は通したが、`./gradlew verify` 全体は流していない（依頼者の指示で B2 の関門で流す）。計画 3節の「Step 13 の後に U2 のソースを直したときは、U4 を始める前に Step 13 をやり直す」に当たるため、C1・C2 のコミットと U4 の開始の前に verify を流し直すか、説明文とテストだけの変更として Step 15 の verify で代えて単位ごとの squash の条件を満たしたとみなすかを確かめる。3節の実測（件数・カバレッジ）は直しの前の値で、直しで件数と行の数は変わらない見込み（性質は差し替えで4件のまま、`Paging` は説明文だけ）。
- Step 13 の後に U2 のソースを直すときは、U4 を始める前に Step 13 をやり直す。U4 を始めた後に U2 のソースを直すことになれば、Bolt 全体の1コミットに切り替える（計画 3節）。

## 9. Build and Test に引き継ぐこと

| 項目 | 内容 | 持ち主 |
|---|---|---|
| カバレッジの実測 | `:backend:cleanTest :backend:cleanIntegrationTest` を付けた verify で、全体・`invitation.domain`・`invitation.service`・`common.paging`・画面の全体をもう一度測る | Build and Test |
| CI | 依頼者のプッシュの後、CI が通ることを確かめる。失敗したら `team.md` の「不安定なテストと CI の失敗」で扱う | Build and Test |
| E2E の報告の片付け | Step 15 の E2E の結果の記録と報告を消したこと。流し直すときも記録してから消す | Build and Test |
| 招待の一覧の応答時間 | 既存の k6 の場面 `invitationList`（同時 10 件で p95 1 秒） | performance-validation |
| 最後のページより後と全体 0 件の結合テスト（利用者の一覧） | U3 の一覧の API に足す（機能設計の R-02、NFR 要件の R-04、R2） | B3・B4（U3 のコード生成の計画） |
| page の文字列と TRACE（利用者の一覧） | U3 の一覧も受け入れた残る危険 R3 として扱い、出うる範囲を確かめる | B3（U3 のコード生成の計画） |
| 8KB を超える要求（レビューの R-03） | 要求の行の上限 8KB（Spring Boot 4.1.1・Tomcat 11.0.26 の既定）を超える要求（例: page が 9,000 文字の数字）は、Tomcat が `text/html` の 400 を返してアプリに届かない（Problem Details にならない。A-01 の確かめ）。利用者の一覧の入口で同じ挙動になることの確かめは U3 のコード生成の計画で、画面の ApiClient が HTML の 400 を受けたときの扱い（確かめるか、残る危険として受け入れるか）は U5 のコード生成で決める | B3（U3 のコード生成の計画）・B5（U5） |
| UiPaging の使い手 | U5 の画面が操作の後の読み直しで `correctedPage` を使う。文言の鍵は U5 が持つ | B5（U5 のコード生成の計画） |
| 配備の後の確かめ | スモークテストに招待の一覧のページ送りを入れるか | deployment-pipeline |
| コードの知識ベースの古い記述 | `aidlc/spaces/default/codekb/` の `InvitationPaging` の記述 | 次の Intent の reverse-engineering |

## 10. 提案するコミットの区切りと統合の案

コミットは依頼者の承認を得てから、作業ブランチの上で行う（生成の担当はコミットしていない）。U2 のコミットは U4 の作業を始める前にすべて入れる。この段の記録（`aidlc/` の下）は、B1 と同じく B2 の squash に含めず、別に記録のコミットとして提案する。

**C1** 件名: 「B2 U2: サーバーのページ送りを common.paging へ移す（Paging・PagingTest、招待の参照先の切り替え）」

- `backend/src/main/java/cherry/mastersmith/common/paging/Paging.java`（新）
- `backend/src/test/java/cherry/mastersmith/common/paging/PagingTest.java`（新）
- `backend/src/main/java/cherry/mastersmith/invitation/service/InvitationService.java`（変）
- `backend/src/test/java/cherry/mastersmith/invitation/repository/InvitationRepositoryIT.java`（変）
- `backend/src/main/java/cherry/mastersmith/invitation/domain/InvitationPaging.java`（消）
- `backend/src/test/java/cherry/mastersmith/invitation/domain/InvitationPagingTest.java`（消）

**C2** 件名: 「B2 U2: 画面のページ送りを src/shared/paging へ移す（UiPaging・性質ベースのテスト、招待の画面の import の切り替え）」

- `frontend/src/shared/paging/paging.ts`（新）
- `frontend/src/shared/paging/paging.test.ts`（新）
- `frontend/src/features/invitation/InvitationList.tsx`（変）
- `frontend/src/features/invitation/useInvitationAdmin.ts`（変）
- `frontend/src/features/invitation/paging.ts`（消）
- `frontend/src/features/invitation/paging.test.ts`（消）

**単位ごとの squash（Step 15、U4 の後）** の案:

- 件名: 「B2 ページ送りの共通化（U2）: サーバーを common.paging・画面を src/shared/paging へ移す」
- 本文の案:
  - 招待の一覧のページ送りの計算を、口と振る舞いを変えずにサーバーの cherry.mastersmith.common.paging.Paging と画面の src/shared/paging/paging.ts へ移し、招待のサーバーと画面の参照先を切り替えた。InvitationPaging と features/invitation/paging.ts は消した。
  - 移したテストの事例はすべて残し、性質ベースのテスト（jqwik 3つ・fast-check 4つ）と PAGE_SIZE 20 の固定を足した。
  - 新しい API・依存・スキーマ・設定は無い。U2 だけの ./gradlew verify が通ることを確かめた（単体 1287・結合 587・画面 736、common.paging 行・分岐 100%）。
  - 末尾に `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` を付ける。
