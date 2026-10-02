# Code Generation Plan — U2 ページ送りの共通化（u2-shared-paging）

U2 のコード生成の計画を示す。Bolt は B2 共通の土台（`inception/delivery-planning/bolt-plan.md`、この Intent の2つ目の Bolt）で、B2 は U2 と U4（u4-admin-forbidden-ui）を同じ作業ブランチで作る。この計画は U2 の分だけを扱い、U2 の生成を先に行う。U4 はこの後の別の計画で作る。

U2 は library の単位で、招待の一覧のページ送りの計算（サーバーの `invitation.domain.InvitationPaging`、画面の `frontend/src/features/invitation/paging.ts`）を、口と振る舞いを変えずにサーバーの `cherry.mastersmith.common.paging.Paging`（契約 C2）と画面の `frontend/src/shared/paging/paging.ts`（UiPaging、契約 C5）へ移し、招待のサーバーと画面を新しい置き場に切り替える。新しい API・依存・スキーマ・設定は持たない。

この計画の中のパスは、特に断らない限りリポジトリのルートからの相対パスで、記録のディレクトリは `aidlc/spaces/default/intents/260930-user-admin/`（以下「記録」）とする。Java のクラスは `backend/src/main/java/cherry/mastersmith/`（テストは `backend/src/test/java/cherry/mastersmith/`）の下を、`cherry.mastersmith` からのパッケージ名で書く。

## 1. 入力にした設計

| 文書（記録の下） | 使うところ |
|---|---|
| `construction/u2-shared-paging/functional-design/functional-spec.md`・`rules.md`・`entities.md`・`traceability.json` | 手順（2.1〜2.4 の切り替えの手順）、失敗の場合の表（3節）、決まり BR1.1〜BR1.6・BR2.1〜BR2.5・BR3.1〜BR3.5、性質ベースのテスト（6節）、後の段へ渡すこと（7節）、上流との差 D1・D2、承認の場の決定 R-01〜R-04、エンティティは 0 件 |
| `construction/u2-shared-paging/nfr-requirements/security-requirements.md`・`tech-stack-decisions.md` | NFR3.1・NFR5.1・NFR5.2・NFR9.1〜NFR9.11・NFR11.1、残る危険 R1・R2、承認の場の決定 R-01〜R-05（TRACE の範囲の確かめ・`package-info.java` を置かない など） |
| `construction/u2-shared-paging/nfr-design/security-design.md`・`logical-components.md`・`nfr-design-questions.md` | 検証の置き場（2節）、桁あふれと読み取りの位置（3節）、TRACE に出うる範囲と受け入れた残る危険 R3（4節）、構造の境界（5節）、テストとカバレッジ（7節）、残る危険 R1〜R4（10節）、上流との差 S-1、承認の場の決定 A-01〜A-04、部品の一覧（`logical-components.md` 1節）・テストの部品（5節） |
| `construction/u2-shared-paging/infrastructure-design/cicd-pipeline.md` と `construction/infrastructure-design/gate-decisions.md` | verify の段への入り方（2節）、カバレッジ（3節）、E2E（4節）、統合の形（5節）、B2 で確かめること（8節）、承認の場の決定（R-01〜R-03 の申し送り、E2E の報告の扱い）、2回目の承認の場の U2 の申し送り（単位ごとの squash の条件、報告を消す前に json から記録する項目） |
| `inception/contract-design/contract-summary.md` の C2・C5（と共通の決まり） | Paging の4つの口（`PAGE_SIZE`・`parsePage`・`pageOf`・`offsetOf`）、UiPaging の7つの export、振る舞いを変えないこと |
| `inception/delivery-planning/bolt-plan.md` の B2 と共通の完了の条件 | 完了の条件（招待の振る舞いを変えない、性質ベースのテスト）、E2E を統合の前に流すこと、単位ごとの squash にしてよいこと |
| `inception/units-generation/unit-of-work.md`（U2）・`unit-of-work-story-map.md`（US1.1 の従） | 単位の境界、作らないもの（利用者の一覧）、確かめ方、`common.paging` は新しいパッケージで下限の対象 |
| `inception/requirements-analysis/requirements.md` の FR1.3・FR1.5 と `inception/user-stories/stories.md` の US1.1（AC1.1.1・AC1.1.5・AC1.1.7 のページ送りの部分） | 要件と受け入れ基準 |
| 前の単位の記録 `construction/u1-user-suspension/code-generation/code-summary.md`・`generation-notes.md` | B1 の経過（基準の verify の失敗と依頼者への諮り、`osvScan` が UP-TO-DATE になること、Mailpit がもとから動いていたときの扱い、E2E の報告の片付けの記録の形） |
| 決まり `aidlc/spaces/default/memory/team.md`・`project.md` | 作業の場・統合・コミット、Testing Posture（性質ベースのテスト・種の記録・カバレッジ・`packagesJudgedByTotal`・E2E・画面のテストの `waitFor`）、Code Style（名前・ライセンスヘッダー・ArchUnit・`src/shared/`）、Change Control、Forbidden・Mandated |

既存のコード（読むだけで確かめた）: `invitation/domain/InvitationPaging.java`・`InvitationPagingTest.java`、`invitation/service/InvitationService.java`（`pageOf`・`parsePage`・`offsetOf`・`PAGE_SIZE` の使い手、`list`・`readPage`）、`invitation/repository/InvitationRepositoryIT.java`（`PAGE_SIZE` の使い手）、`invitation/web/InvitationAdminApiIT.java`（一覧の 400・最後のページの次の空の 200・`size` 20）、`invitation/InvitationBoundaryArchitectureTest.java`・`ArchitectureTest.java`（`common` への依存を禁じていない）、`backend/build.gradle.kts`（`packagesJudgedByTotal` は 9 パッケージで `invitation.*` と `common.paging` を含まない）、`frontend/src/features/invitation/paging.ts`・`paging.test.ts`・`InvitationList.tsx`・`useInvitationAdmin.ts`、`frontend/e2e/060-invitation-accessibility.e2e.ts`・`090-invitation-registration-flow.e2e.ts`、`backend/src/main/resources/logback-spring.xml`・`application.yaml`、`common/observability/SingleLineMessageJsonProvider.java`・`JsonLogFormatTest.java`。

## 2. 承認の場の決定と、この計画での読み方

### 2.1 承認の場の決定（監査ログと各文書の「承認の場の決定」の節から洗い出したもの）

`project.md` の学び（計画の前に承認の場の決定を洗い出す）に従い、監査ログ `audit/sakura-local-4e42a93f87ce.md` の `GATE_REJECTED`（Request Changes の理由）・`GATE_APPROVED` と、U2 の各文書の終わりの「承認の場の決定」の節、`gate-decisions.md` を読んだ。U2 と B2 に関わるものと、この計画での扱いは次のとおり。

| 段 | 決定・指摘 | この計画での扱い | 手順 |
|---|---|---|---|
| 機能設計（Request Changes、R-01） | AC1.1.1・AC1.1.5・AC1.1.7 の OK は「部分（ページ送りの部分だけ）」 | コード生成の `traceability.json` でも同じく「部分」と書き、残りは u3-user-admin-api・u5-user-admin-ui へ Deferred にする | Step 14 |
| 機能設計（R-02、申し送り） | 最後のページより後と全体 0 件の応答の結合テストは U3 の計画で足す | U2 では足さない。「Build and Test に引き継ぐこと」に B4（U3）の持ち物として写す | — |
| 機能設計（R-03、申し送り） | `invitation.domain` のカバレッジを測り直して記録する。`package-info.java` を置くかは慣習に合わせる | 実測は Step 1・13（4.3 の表）。`package-info.java` は置かない（NFR 要件の R-05 で決まった） | Step 1・3・13 |
| 機能設計（7節、計画で決める） | 画面の文言（訳の鍵）を共通に移すか | 移さない（依頼者の決定。9節の Q-D） | Step 9 |
| 機能設計（7節、計画で決める） | B2 の統合を単位ごとの squash にするか | 単位ごとの squash（依頼者の決定。条件つき、3節。9節の Q-A） | Step 13〜15 |
| NFR 要件（Request Changes、R-02） | page の文字列が TRACE のログに出うる範囲（呼び出し元の `web`・`service` の引数）を、コード生成のレビューで確かめる | Step 12 で確かめて記録する | Step 12 |
| NFR 要件（R-04） | 「空にする」判定（`offsetOf`）と読み取りの位置（`PageRequest`）が同じ page から導かれることを、招待の一覧の結合テストで確かめる | 既存の `InvitationAdminApiIT` の一覧のテスト（2ページ目が1件・3ページ目が空の 200）を変えずに流す。`InvitationService#readPage` は参照先だけを変える | Step 5・6 |
| NFR 要件（R-05） | `common.paging` に `package-info.java` を置かない | 置かない | Step 3 |
| NFR 要件の Request Changes の理由（監査ログ） | 「コード生成の計画（U2 の結合テスト …）」への申し送り | 上の R-04 の確かめ（既存の結合テストを変えずに通す）として扱う。U2 で新しい結合テストのクラスは足さない（8節の D-2） | Step 6 |
| NFR 設計（Request Changes、A-01） | 要求の行の上限 8KB（Spring Boot 4 の組み込みのサーバーの既定）が要求の行にも効くかを確かめ、記録する。崩れたら、根拠1・2だけで受け入れ（Q1 A）が成り立つことを書き足す | 一時の確かめで確かめて記録する（依頼者の決定。9節の Q-B） | Step 12 |
| NFR 設計（A-02） | ログの出力が制御文字（改行など）をエスケープする形かを確かめるか、残る危険として記録する | 既存の `SingleLineMessageJsonProvider` と `JsonLogFormatTest` で確かめ、記録する（コードは変えない） | Step 12 |
| NFR 設計（A-03） | サーバーと画面の `PAGE_SIZE`（20）の一致を機械で見つける確かめを足すか、画面が応答の size を使う形にするか | 両側の値をテストで 20 に固定し、説明文で互いの場所を示す（依頼者の決定。9節の Q-C） | Step 3・4・7・8・12 |
| NFR 設計（A-04） | 移す前の値と、`InvitationPaging` を除いた見込みの値を実測して並べ、足すテストの量を見積もる | 4.3 の表に並べた（見込みでは下限を満たし、足すテストは無い）。Step 1・13 で実測する | Step 1・13 |
| 基盤の設計（1回目、R-01） | E2E の 060・090 がページ送りを操作するかを確かめ、操作しないなら画面のページ送りの確かめは Vitest だけが担うと書き分ける | 確かめた結果を 2.2 に書いた（060 の実データの1件は「次へ」「前へ」を押す。090 は操作しない） | Step 15 |
| 基盤の設計（1回目、R-02） | 実測の表に `parsePage` の分岐を網羅できる見込みを並べる | 4.3 の2つ目の表 | Step 4・13 |
| 基盤の設計（1回目、R-03・2回目の U2 の申し送り） | 単位ごとの squash にするなら、U2 のコミットが単独で `./gradlew verify` を通ることを確かめる。通らないなら Bolt 全体の1コミット | 3節の条件として計画の前提にする | Step 13〜15 |
| 基盤の設計（1回目、U5 R-02 の全単位の決定と2回目の U2 の申し送り） | E2E の後は json の報告から結果を記録してから `frontend/playwright-report/`・`frontend/test-results/` を消し、消したことと共有していないことを記録する。記録する項目は件数と 060・090 の結果 | 記録する項目を Step 15 に決めた | Step 1・15 |

### 2.2 この計画での読み方

- **振る舞いを変えない移し替え**: Paging と UiPaging は、移す前の `InvitationPaging`・`paging.ts` と同じ名前・引数・結果・例外のまま移す（BR1.5・BR2.5、C2・C5）。変えるのは説明文（BR1.6・BR2.5 の共通の書き方）と、足すテストだけ。移す前のファイルは残さない（中で新しい部品を呼ぶ形でも残さない）。
- **層の順（test-after）**: Testing Contract の `ordering` のとおり、層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、通ってから次の層へ進む。U2 の層は「サーバーのページ送り（ドメイン）→ 招待のサーバーの呼び出し側（業務処理と結合テストの参照）→ 画面のページ送り → 招待の画面（画面部品の import）→ 構造の検査」の順とする。どの手順の終わりでもコンパイルが通るよう、新しい部品を先に作り、使い手を切り替えた後に古い部品を消す。
- **招待の既存のテストは中身を変えない**: 変えるのは import の行と、`InvitationPaging.PAGE_SIZE` を `Paging.PAGE_SIZE` にする参照だけ（NFR9.5）。移すテスト（`InvitationPagingTest` → `PagingTest`、`paging.test.ts` → `shared/paging/paging.test.ts`）は今の事例をすべて残し、足すのは性質ベースのテストと 9節の Q-C の固定の確かめだけ。
- **E2E とページ送りの操作（基盤の設計の R-01）**: `frontend/e2e/060-invitation-accessibility.e2e.ts` のうち、表示の設定の組ごとの 20 件は一覧の API を見本（全件数 23 の1ページ目）に差し替えて描くだけで、ページ送りのボタンは押さない。同じファイルの実データの1件（`list and next page time with real invitations, without CSP violations`）は、招待を投入した本物の API で「次へ」（2ページ目の表示）と「前へ」を押す。`090-invitation-registration-flow.e2e.ts` はページ送りに触れない。そのため、本物のサーバー（Paging）と画面（UiPaging）を通したページ送りの操作は E2E の 060 の実データの1件だけが確かめ、補正（`correctedPage`）・押せなくなるボタンとフォーカス・境目の値・性質は Vitest（`shared/paging/paging.test.ts`・`InvitationList.test.tsx`・`InvitationAdminPage.test.tsx`）が担う。060 の実データの1件は、招待を使える設定が無い WAR では飛ばされる（そのファイルの説明のとおり）。飛ばされたときは、画面のページ送りの確かめは Vitest だけが担ったと記録する（Step 15）。
- **既存の ArchUnit を緩めない**: `ArchitectureTest` と機能ごとの境界テストは変えない（`team.md` の Code Style、NFR11.1）。`common.paging` は機能のパッケージではないため、新しい `<機能>BoundaryArchitectureTest` は足さない（`security-design.md` 5節）。
- **`packagesJudgedByTotal` は変えない**: `invitation.*` は一覧に無く（すでにパッケージごとの下限の対象）、`common.paging` は新しいパッケージとして自動で下限の対象になる。一覧を増やさない。計測の除外を増やさない（`team.md` の Testing Posture、NFR9.8・NFR9.9）。
- **TRACE の残る危険 R3 は受け入れ済み**: page の文字列を受ける招待の controller と業務処理の形（`String` で受ける）は変えない（NFR 設計の Q1 A）。U2 では出うる範囲と要求の行の上限を確かめて記録するだけ（Step 12）。

## 3. 作業の場とコミットの区切り

- **記録の扱い**: ブランチを作る前に、この段の計画と承認の記録（記録の `construction/u2-shared-paging/code-generation/` の下と `aidlc-state.md`・監査ログ）を、依頼者の承認を得て `develop` に記録のコミットとして入れることを提案する（B1 と同じく、記録は B2 の squash に含めない。コミットは提案して承認を得てから行う、`project.md` の Change Control）。
- **作業のブランチ**: `develop` から短命のブランチ `feature/260930-user-admin-b2` を作る（`team.md` の Way of Working）。ブランチの作成は依頼者の承認を得てから行う。worktree は使わない。B2 は U2 と U4 を同じブランチで作り、U2 の生成を先に、U4 はこの後の別の計画で行う。
- **コミット**: 生成の担当はコミットしない。U2 の生成の後に、依頼者の承認を得て、作業ブランチの上で次の区切りでコミットする（`project.md` の Change Control の学び）。メッセージは日本語。U2 のコミットは、U4 の作業を始める前にすべて作業ブランチに入れる（U2 のコミットが U4 のコミットより前に並ぶようにする。単位ごとの squash の条件）。

| 区切り | 中身 | 手順 |
|---|---|---|
| C1 | サーバーのページ送りを `common.paging` へ移す（`Paging`・`PagingTest` の追加、`InvitationService`・`InvitationRepositoryIT` の参照の切り替え、`InvitationPaging`・`InvitationPagingTest` の削除） | Step 3〜6 |
| C2 | 画面のページ送りを `src/shared/paging/` へ移す（`paging.ts`・`paging.test.ts` の追加、`InvitationList.tsx`・`useInvitationAdmin.ts` の import の切り替え、`features/invitation/paging.ts`・`paging.test.ts` の削除） | Step 7〜10 |

- **統合の前の関門（B2 全体で1回、U4 の生成の後）**: colima の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡し `caffeinate -i` で包んで `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を通し、`./gradlew osvScan --rerun-tasks` を通す（`verify` の中の `osvScan` は lockfile が変わらないと UP-TO-DATE で前の結果を使うため。B1 の経過）。続けて E2E（`./gradlew e2eTest`、Mailpit を profile `mail` で起動）を流し、json から結果を記録してから `frontend/playwright-report/`・`frontend/test-results/` を消す（Step 15）。
- **統合の形（9節の Q-A の決定: 単位ごとの squash）**: U4 の生成と B2 の関門が終わった後に、`develop` へ squash で統合する。単位ごとの squash（U2 と U4 で2コミット）にする条件は、**U2 の最後のコミット（C2 の後の作業ブランチの先頭）の中身が、単独で `./gradlew verify` を通っていること**（基盤の設計の R-03）。この計画では次で確かめる:
  - Step 13 で、U2 のすべての変更だけがある作業フォルダ（U4 の変更が無い状態）で `verify` を通す。
  - Step 14 で、Step 13 の後にソースを変えずに C1・C2 をコミットし、`git status` で未コミットのアプリのソースが無いこと（コミットの中身が通した作業フォルダと同じこと）を確かめ、U2 の最後のコミットのハッシュを記録する。
  - Step 13 の後に U2 のソースを直したとき（レビューの指摘など）は、U4 を始める前に Step 13 をやり直す。U4 を始めた後に U2 のソースを直す必要が出たとき（B2 の関門で U2 の不具合が分かったときなど）は、U2 の最後のコミットが単独で通ることを確かめられないため、Bolt 全体の1コミットに切り替える。
  - 統合の手順: 依頼者の承認を得て、`develop` の上で U2 の最後のコミットまでを squash して1コミット（件名の案「B2 ページ送りの共通化（U2）: サーバーを common.paging・画面を src/shared/paging へ移す」）、続けて作業ブランチの残り（U4）を squash して1コミットにする。件名は日本語で単位の中身が分かるものにする（`team.md`）。サブモジュールの固定先は変えないため、fast-forward の例外には当たらない。
  - 条件を満たさないときは、B2 全体を1コミットで squash する。
- **統合の後**: 依頼者の承認を得て作業ブランチを消す。`origin` への `git push` は依頼者が行う。AI はプッシュしない。

## 4. 作るもの・手を入れるもの・消すもの

### 4.1 本体（`src/main`）

| 置き場 | 部品 | 新しい・手を入れる・消す | 中身 | 一覧（`packagesJudgedByTotal`） |
|---|---|---|---|---|
| `common.paging` | `Paging` | 新しい | `InvitationPaging` の4つの口（`PAGE_SIZE`・`parsePage(String)`・`pageOf(long)`・`offsetOf(int)`）を名前・引数・結果・例外を変えずに移す。素のクラス（`final`・private のコンストラクター・static のメソッド）で、Spring の部品にしない。依存は JDK（`java.util.OptionalInt`・`java.util.regex.Pattern`）だけ。説明文を管理の一覧に共通の書き方と U2 の決まりの番号（BR1.x）に直す（BR1.6）。`PAGE_SIZE` の説明文に、画面の `frontend/src/shared/paging/paging.ts` の `PAGE_SIZE` と同じ値にすること（9節の Q-C）を書く。`package-info.java` は置かない | 一覧の外（新しいパッケージとして自動で下限の対象） |
| `invitation.domain` | `InvitationPaging` | 消す | `Paging` へ移した後に消す | 一覧の外（すでに下限の対象） |
| `invitation.service` | `InvitationService` | 手を入れる（参照先だけ） | import を `cherry.mastersmith.common.paging.Paging` に替え、`InvitationPaging.pageOf`・`parsePage`・`offsetOf`・`PAGE_SIZE` の4か所の名前を `Paging` に替える。処理の流れ（検証の空で `ListResult.InvalidPage`、`offsetOf(page) >= total` で行を読まない、`PageRequest.of(page - 1, PAGE_SIZE)`）は変えない | 一覧の外 |
| `frontend/src/shared/paging/` | `paging.ts`（UiPaging） | 新しい | `features/invitation/paging.ts` のすべての export（`PAGE_SIZE`・`PagerDirection`・`PageRange`・`pageCount`・`pageRange`・`correctedPage`・`pagerButtonDisabledAfter`）を名前・引数・結果を変えずに移す。名前つきの export だけで、`export default`・`enum` を使わない。React・window・API に触れない。先頭の説明文を管理の一覧に共通の書き方と U2 の決まりの番号（BR2.x）に直す。`PAGE_SIZE` の説明文に、サーバーの `Paging.PAGE_SIZE` と同じ値にすることを書く | — |
| `frontend/src/features/invitation/` | `paging.ts` | 消す | `shared/paging/paging.ts` へ移した後に消す | — |
| `frontend/src/features/invitation/` | `InvitationList.tsx`・`useInvitationAdmin.ts` | 手を入れる（import の場所だけ） | `from './paging'` を `from '../../shared/paging/paging'` に替える（既存の `../../shared/format/formatDateTime` と同じ相対の書き方）。ほかは変えない | — |

手を入れない: `invitation.web`（`InvitationAdminController` は page を `String` で受ける形のまま）、`invitation.repository`、`common` のほかのパッケージ、`application.yaml`・`logback-spring.xml`、`backend/build.gradle.kts`（`packagesJudgedByTotal`・計測の除外）、`frontend/vitest.config.ts`、`frontend/src/app/i18n` などの文言（9節の Q-D）、`frontend/e2e/`、`README.md`、`compose.yaml`・`.env.example`・Dockerfile、`.github/`、`docker/monitoring/`、`gradle/libs.versions.toml`・`backend/gradle.lockfile`・`frontend/package.json`・`frontend/package-lock.json`、`vendor/`。新しい依存・指標・警報・ログは足さない。

### 4.2 テスト（`src/test` と画面のテスト）

| 置き場 | 部品 | 新しい・手を入れる・消す | 中身 |
|---|---|---|---|
| `common/paging` | `PagingTest` | 新しい（`InvitationPagingTest` を移す） | 今の事例をすべて残す（位置とページの境目 1・20・21・40・41、指定なしは 1 と正の整数、拒否する入力 `0`・`-1`・`1.5`・`abc`・空・` 1`・`+1`・`9999999999`、`offsetOf` の 1・2・`Integer.MAX_VALUE`、1 未満の例外、jqwik の性質1つ）。jqwik の性質3つを足す（FS 6節）。`PAGE_SIZE` が 20 であることの確かめを1件足す（9節の Q-C）。先頭の説明文に、失敗のときの種の再現の仕方を書く（NFR9.7） |
| `invitation/domain` | `InvitationPagingTest` | 消す | `PagingTest` へ移した後に消す |
| `invitation/repository` | `InvitationRepositoryIT` | 手を入れる（参照先だけ） | import と `InvitationPaging.PAGE_SIZE` の2か所を `Paging.PAGE_SIZE` に替える。中身は変えない |
| `frontend/src/shared/paging/` | `paging.test.ts` | 新しい（`features/invitation/paging.test.ts` を移す） | 今の事例をすべて残す（ページの数、範囲、0 件、補正する・しない、ボタン、fast-check の性質1つ）。fast-check の性質4つを足す（FS 6節）。先頭の説明文に、失敗のときの seed と path の再現の仕方を書く（NFR9.7） |
| `frontend/src/features/invitation/` | `paging.test.ts` | 消す | `shared/paging/paging.test.ts` へ移した後に消す |
| 招待のほかのテスト | `InvitationServiceTest`・`InvitationAdminApiIT`・`InvitationAuditIT`・`InvitationList.test.tsx`・`InvitationAdminPage.test.tsx` ほか | 変えない | どれも `InvitationPaging`・`./paging` を直接参照しない（検索で確かめた）。参照先の変更だけで通ったままであることを確かめる |
| 構造の検査 | `ArchitectureTest`・`InvitationBoundaryArchitectureTest` ほか | 変えない | 変更なしで通ることを確かめる |

### 4.3 カバレッジの実測と見込み（A-04、基盤の設計の R-02）

手元に残っていた JaCoCo の報告 `backend/build/reports/jacoco/test/jacocoTestReport.xml`（2026-10-02 15:00 の生成。全体の値 行 5653/5720・分岐 2069/2190 が、B1 の R-01 の直しの後の verify の記録と一致する。ソースは `develop` の ded2653 と同じ）から読んだ。Step 1 でもう一度実測し、値が違えば Step 1 の値を基準とする。

| 範囲 | 行 | 分岐 | 扱い |
|---|---|---|---|
| 全体 | 98.8%（5653/5720） | 94.5%（2069/2190） | 下限を満たす |
| `invitation.domain`（今） | 98.4%（246/250） | 96.7%（117/121） | パッケージごとの下限の対象 |
| うち `InvitationPaging` | 100.0%（13/13） | 100.0%（10/10） | `common.paging` へ移る |
| `invitation.domain` から `InvitationPaging` を除いた見込み | 98.3%（233/237） | 96.4%（107/111） | 下限（行 80%・分岐 70%）を満たす見込み。足すテストは無い（残る危険 R1 は当たらない見込み） |
| `common.paging`（見込み） | 100.0%（13/13） | 100.0%（10/10） | 移すテストで今と同じ値になる見込み。新しいパッケージとして自動で下限の対象 |
| `invitation.service` | 100.0%（332/332） | 93.5%（87/93） | 参照先だけの変更。値は変わらない見込み |
| `invitation.web` | 97.3%（107/110） | 87.0%（20/23） | 変えない |
| `invitation.repository` | 100.0%（2/2） | 分岐なし | 変えない |

`Paging` の分岐の網羅の見込み（`InvitationPaging` の 10 の分岐が今すべて通っている。移すテストの事例で同じく通る）:

| 口 | 分かれ道 | 真の側を通す事例 | 偽の側を通す事例 |
|---|---|---|---|
| `parsePage` | `raw == null` | `parsePage(null)` → 1 | `"1"`・`"3"` ほか |
| `parsePage` | 数字 1〜9 文字に合わない | `"-1"`・`"1.5"`・`"abc"`・空・`" 1"`・`"+1"`・`"9999999999"` → 空 | `"1"`・`"3"`・`"0"` |
| `parsePage` | 値が 1 以上 | `"1"`・`"3"` | `"0"` → 空 |
| `pageOf` | 位置が 1 未満 | `pageOf(0)` → 例外 | 位置 1・20・21・40・41 |
| `offsetOf` | ページが 1 未満 | `offsetOf(0)` → 例外 | ページ 1・2・`Integer.MAX_VALUE` |

足す jqwik の性質（1〜999,999,999 は受ける、数字以外・10 文字以上・`"0"` は空、`offsetOf` と `pageOf` の往復）も同じ分かれ道を通る。画面のカバレッジは全体の合計（`frontend/vitest.config.ts` の `thresholds`）で判定し、`src/shared/paging/` を計測から外さない（NFR9.10）。B1 の基準のフロントエンドの全体は 行 97.44%・分岐 92.67%（参考）。

## 5. 手順

各層で実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む（test-after、Testing Contract の `ordering`）。各 Step の実行のコマンドは `unit-test-instructions.md` の2節のとおりで、U2 に関わるテストのクラスとファイルを名指しして流す。

### Step 1: 作業の場の用意と、変更の前の基準（ブランチの作成は依頼者の承認を得てから）

- [x] `develop` の先頭のハッシュを `git rev-parse HEAD` で記録する。アプリのソースに未コミットの変更が無いことを `git status` で確かめる（ワークフローの記録は外して判断する、`project.md` の学び）
- [x] 依頼者の承認を得て、`develop` から `feature/260930-user-admin-b2` を作る
- [x] `frontend/playwright-report/`・`frontend/test-results/` が手元に残っていないことを確かめる。残っていれば、中を開かずに消し、消したこと（中身の種類と件数だけ）と共有していないことを記録する（`gate-decisions.md` の U5 R-02 の決定。どちらも `.gitignore` の対象）
- [x] Dependabot の開いている知らせ（`gh pr list --state open` と `origin` の `dependabot/*` のブランチ）の一覧を読み取りだけで確かめ、重大度 High 以上の脆弱性の直しがあれば B2 に入る前に取り込むかを依頼者に諮る（`team.md` の Way of Working）。無ければ記録だけ
- [x] 変更の前の基準をとる: colima が動いていることを `colima status` で確かめ、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行し、テストの件数（バックエンドの単体・結合、フロントエンド、失敗・飛ばした）と、全体と次のパッケージの行・分岐のカバレッジを `backend/build/reports/jacoco/test/jacocoTestReport.xml` から記録する: `invitation.domain`（クラス `InvitationPaging` の値と、それを除いた値も）・`invitation.service`・`invitation.web`・`invitation.repository`。フロントエンドの全体の値も記録する（brownfield の Test Baseline、`project.md` の学び）
- [x] 基準の検査が U2 と関わらない理由（新しく公表された脆弱性で `osvScan` が止まる・`gitleaksScan` の指摘など）で失敗したときは、ここで止めて依頼者に諮る（B1 の Step 1 と同じ扱い。直しは別のブランチで行うかを依頼者が決める）
- [x] 対応: B2 の共通の完了の条件、`gate-decisions.md`、NFR9.8（A-04）

### Step 2: テストの実行の準備（最初のテストより前）

- [x] `unit-test-instructions.md` の「最初のテストより前の確かめ」のコマンドで、既存の単体テスト（JUnit・jqwik）・結合テスト・画面のテスト（Vitest・fast-check）の道具が、作業ブランチの上で動くことを確かめる:
  - `./gradlew :backend:test --tests 'cherry.mastersmith.invitation.domain.InvitationPagingTest'`
  - `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.invitation.repository.InvitationRepositoryIT'`
  - `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/invitation/paging.test.ts)`
- [x] 新しいテスト（`common.paging.PagingTest`・`src/shared/paging/paging.test.ts`）は、作るまで名指しすると「一致するテストが無い」で失敗する。これは想定どおりで、作った Step からコマンドが通ることを `unit-test-instructions.md` の記述と合わせる
- [x] 対応: Testing Contract の `runner_step`、NFR9.6

### Step 3: サーバーのページ送り（ドメイン）— 実装

- [x] `backend/src/main/java/cherry/mastersmith/common/paging/Paging.java` を作る。`InvitationPaging` の本体（定数・コンパイル済みの `Pattern`・private のコンストラクター・3つのメソッド）を、名前・引数の型・結果の型・例外の種類と文言の形を変えずに移す（BR1.5、C2）
- [x] 説明文を直す（BR1.6）: クラスの説明を「管理の一覧（招待の一覧・利用者の一覧）のページ送りの計算。DB・時刻・設定・ほかの機能に依存しない純粋な関数。1ページは 20 件」の形にし、招待の段の決まりの番号（BR2.3・BR5.2・NFR9.7）を U2 の番号（BR1.1〜BR1.5）に替える。`pageOf` の説明の「その招待が載るページ」を「その行が載るページ」に直す。振る舞いの説明（受ける値・返す値・例外）は変えない
- [x] `PAGE_SIZE` の説明文に「画面の `frontend/src/shared/paging/paging.ts` の `PAGE_SIZE` と同じ値にする（変えるときは両方を同じ変更で直す）」を書く（A-03、9節の Q-C の決定）
- [x] 先頭に `/* ... */` の Apache License 2.0 のヘッダー（2026、agwlvssainokuni）を置く。`package-info.java` は置かない（NFR9.9）。Spring の注釈を付けない
- [x] この時点では `InvitationPaging` を残す（使い手を Step 5 で切り替えてから消す。各 Step の終わりでコンパイルを通すため）
- [x] 対応: BR1.1〜BR1.6、C2、NFR9.1・NFR9.2・NFR9.9・NFR11.1、A-03

### Step 4: サーバーのページ送り — テスト（単体・性質）

- [x] `backend/src/test/java/cherry/mastersmith/common/paging/PagingTest.java` を作り、`InvitationPagingTest` の事例をすべて、`@DisplayName`・`@Label`・`@Property(tries = 500)` の宣言を変えずに移す（参照先を `Paging` に替えるだけ）。移した後に `invitation/domain/InvitationPagingTest.java` を消す
- [x] jqwik の性質を3つ足す（FS 6節、どれも `@Property(tries = 500)`、説明文は英語）:
  - 1〜999,999,999 の整数 n で、`parsePage(Integer.toString(n))` が n になる
  - 数字以外の文字を1つ以上含む文字列・10 文字以上の数字の文字列・`"0"` は、`parsePage` が空になる（生成の仕方: 数字の列に数字以外の文字を差し込む、`@StringLength(min = 10)` の数字だけの列、`"0"` と先頭が 0 の列のうち値が 0 のもの）
  - 1〜`Integer.MAX_VALUE - 1` の page で、`offsetOf(page + 1) - offsetOf(page)` が 20、`pageOf(offsetOf(page) + 1)` が page に戻る
- [x] `PAGE_SIZE` が 20 であることの確かめを1件足す（説明文 `the page size is 20, the same value as PAGE_SIZE of the screen`。A-03、9節の Q-C の決定）
- [x] クラスの説明文（日本語）に、失敗のときの種の再現の仕方を書く: jqwik は失敗の報告の `seed` を `@Property(seed = "...")` に与えて再現する。失敗した例の記録は `build/jqwik-database`（リポジトリに残さない、`backend/src/test/resources/junit-platform.properties`）。Gradle のテストの出力は失敗の詳細をすべて出す設定のため、CI の記録にも種が残る（NFR9.7）
- [x] テストのデータは整数と page の文字列だけで、個人に関する値を置かない
- [x] `unit-test-instructions.md` 2.2 のコマンドで流す
- [x] 対応: BR1.1〜BR1.5、NFR9.1・NFR9.2・NFR9.6・NFR9.7、AC1.1.5・AC1.1.7（ページ送りの部分）、A-03

### Step 5: 招待のサーバーの呼び出し側の置き換え — 実装

- [x] `invitation/service/InvitationService.java` の import を `cherry.mastersmith.common.paging.Paging` に替え、`InvitationPaging.pageOf`（招待中の重なりのページ）・`parsePage`（一覧の検証）・`offsetOf`（空にする判定）・`PAGE_SIZE`（読み取りの大きさと応答の size）の参照を `Paging` に替える。ほかの行は変えない（BR3.1〜BR3.5、NFR9.2 の「同じ page と PAGE_SIZE から導く」形のまま）
- [x] `invitation/repository/InvitationRepositoryIT.java` の import と `InvitationPaging.PAGE_SIZE` の2か所を `Paging.PAGE_SIZE` に替える（テストの中身は変えない）
- [x] `invitation/domain/InvitationPaging.java` を消す
- [x] `backend/src` で `InvitationPaging` を検索し、参照が残っていないことを確かめる
- [x] 対応: BR3.1〜BR3.5、BR1.5（`InvitationPaging` を残さない）、NFR9.5、R2（招待の側の形）

### Step 6: 招待のサーバー — テスト（既存の単体・結合を変えずに流す）

- [x] `unit-test-instructions.md` 2.3 のコマンドで、`PagingTest` と招待の既存の単体テスト・結合テスト（`cherry.mastersmith.invitation.*`）を流し、すべて通ることを確かめる。とくに次が変更なしで通ること:
  - `InvitationAdminApiIT` の `the list orders newest first by id, pages by 20, empties beyond the last page and rejects bad pages`（20 件・2ページ目が1件・3ページ目が全体の件数つきの空の 200・`0`・`-1`・`1.5`・`abc`・空が 400 `VALIDATION_FAILED`）と、`size` 20 の確かめ、招待中の重なりの `page` 2 の確かめ（BR1.4）
  - `InvitationServiceTest` の一覧（`InvalidPage`・最後のページより後の空）、`InvitationAuditIT` の招待中の重なりのページ
  - `InvitationRepositoryIT`（参照先だけを変えたもの）
- [x] 失敗したときは、テストではなく `Paging` か `InvitationService` の写し間違いを直す（テストを書き換えない。NFR9.5）
- [x] 対応: BR1.2〜BR1.4・BR3.1〜BR3.4、NFR9.1〜NFR9.3・NFR9.5、AC1.1.5・AC1.1.7（ページ送りの部分）、NFR 要件の R-04

### Step 7: 画面のページ送り — 実装

- [x] `frontend/src/shared/paging/paging.ts` を作る。`features/invitation/paging.ts` のすべての export を、名前・引数・結果・処理を変えずに移す（BR2.1〜BR2.5、C5）
- [x] 先頭の説明のコメントを直す: 招待の段の参照（`functional-spec.md` の D1・W2、`frontend-components.md` の 2節）を、管理の一覧（招待の一覧・利用者の一覧）に共通の書き方と U2 の決まりの番号（BR2.1〜BR2.5）に替える。関数の JSDoc の「招待」に限った言い方（例「全 43 件」の例は残す）を共通の言い方に直し、振る舞いの説明は変えない。`correctedPage`・`pagerButtonDisabledAfter` の「W2 の 5」「W2 の 3」の参照を BR2.3・BR2.4 に替える
- [x] `PAGE_SIZE` の JSDoc に「サーバーの `cherry.mastersmith.common.paging.Paging.PAGE_SIZE` と同じ値にする（変えるときは両方を同じ変更で直す）」を書く（A-03、9節の Q-C の決定）
- [x] 先頭に `/* ... */` の Apache License 2.0 のヘッダーを置く。名前つきの export だけで、`export default`・`enum` を使わない
- [x] この時点では `features/invitation/paging.ts` を残す（Step 9 で使い手を切り替えてから消す）
- [x] 対応: BR2.1〜BR2.5、C5、NFR11.1、A-03

### Step 8: 画面のページ送り — テスト（単体・性質）

- [x] `frontend/src/shared/paging/paging.test.ts` を作り、`features/invitation/paging.test.ts` の事例（`describe('paging', …)` の7件）を、`it` の説明文と期待の値を変えずに移す（import の場所だけ `./paging` のまま新しい置き場から読む）。移した後に `features/invitation/paging.test.ts` を消す
- [x] fast-check の性質を4つ足す（FS 6節、既定の回数 100 回、説明文は英語）:
  - 隣り合うページの範囲が隙間なくつながり（次の from が前の to + 1）、すべてのページの件数の和が total になる（total は 1〜100,000）
  - `pageCount(total)` を c とすると、total が 1 以上なら `(c - 1) * 20 < total <= c * 20`
  - 「次へ」が押せなくなるのは page が `pageCount(total)` 以上のときだけ（`pagerButtonDisabledAfter('next', page, total)` が `page >= pageCount(total)` と一致する）
  - 行が1件以上あれば `correctedPage` は補正しない（undefined）
- [x] 既存の `expect(PAGE_SIZE).toBe(20)` は残す（A-03 の画面の側の固定）
- [x] 先頭の説明のコメントに、失敗のときの再現の仕方を書く: fast-check は失敗の報告に `seed` と `path` を出すため、`fc.assert(property, { seed, path })` の第2引数に与えて再現する（NFR9.7）
- [x] 先頭に Apache License 2.0 のヘッダーを置く。テストのデータは整数だけ
- [x] `unit-test-instructions.md` 2.4 のコマンドで流す
- [x] 対応: BR2.1〜BR2.4、NFR9.6・NFR9.7、AC1.1.1・AC1.1.7（ページ送りの部分）、A-03

### Step 9: 招待の画面の置き換え — 実装

- [x] `frontend/src/features/invitation/InvitationList.tsx` の `import { PAGE_SIZE, pageRange, type PagerDirection } from './paging'` を `'../../shared/paging/paging'` から読む形に替える。ほかは変えない
- [x] `frontend/src/features/invitation/useInvitationAdmin.ts` の `import { correctedPage, pageRange, pagerButtonDisabledAfter, type PagerDirection } from './paging'` を同じく替える。ほかは変えない
- [x] `frontend/src/features/invitation/paging.ts` を消す
- [x] `frontend/src` と `frontend/e2e` で `features/invitation/paging` と `from './paging'`（招待のフォルダーの中）を検索し、古い参照が残っていないことを確かめる
- [x] 画面の文言（`invitation.pager.status`・`invitation.action.prev`・`invitation.action.next` などの訳の鍵）は移さない（9節の Q-D の決定、BR2.5 の前提）
- [x] 対応: BR3.5、BR2.5、NFR9.5・NFR11.1

### Step 10: 招待の画面 — テスト（既存の画面のテストを変えずに流す）と画面の静的検査

- [x] `unit-test-instructions.md` 2.5 のコマンドで、`src/shared/paging` と `src/features/invitation` の画面のテストを流し、すべて通ることを確かめる。とくに、ページ送りを操作する既存のテスト（`InvitationList.test.tsx` の `names the table, reaches its buttons with Tab, and shows the pager status and its edges`、`InvitationAdminPage.test.tsx` の `moves between pages and focuses the heading when the pressed button becomes disabled`・`focuses the heading when the pending row is gone, and corrects a page past the end` ほか）が変更なしで通ること
- [x] 型の検査（`npm run typecheck`）と、変えたフォルダーに絞った書式・リンタの検査（Prettier・oxlint・ESLint、`unit-test-instructions.md` 2.5）を通す
- [x] 失敗したときは、テストではなく `shared/paging/paging.ts` の写し間違いや import を直す（テストを書き換えない。NFR9.5）。描画の後に反映される値の確かめが時間で揺れたときは、上限を延ばさずに原因を確かめる（`team.md` の Testing Posture）
- [x] 対応: BR2.1〜BR2.5・BR3.5、NFR9.4・NFR9.5・NFR11.1

### Step 11: 構造の検査と古い参照の確かめ

- [x] `unit-test-instructions.md` 2.6 のコマンドで、`ArchitectureTest` と機能ごとの境界テスト（`*BoundaryArchitectureTest`、とくに `InvitationBoundaryArchitectureTest`）を変更なしで流して通ることを確かめる。`InvitationBoundaryArchitectureTest` の決まり（`auth`・`audit`・`dsl` などへの依存の禁止）は `common` への依存を禁じておらず、`invitation` から `common.paging` への向きは許されている。通らないときは本体の置き場を直し、テストを緩める必要が出たら生成を止めて依頼者に諮る（`team.md` の Code Style、NFR11.1）
- [x] `common.paging` がどの機能（`invitation`・`user`・`auth` など）にも依存しないことを、`Paging.java` の import が JDK だけであることで確かめる
- [x] `git diff --stat develop` と検索で、`InvitationPaging`・`features/invitation/paging` への参照がアプリのソースとテストに残っていないことを確かめる（`cicd-pipeline.md` 8節 (i)）。記録のディレクトリとコードの知識ベース（`aidlc/spaces/default/codekb/`）の記述は、この段では書き換えない
- [x] 対応: NFR11.1、BR1.5・BR2.5、`cicd-pipeline.md` 8節 (i)

### Step 12: コードのレビューでの確かめ（申し送りの A-01〜A-03・NFR の確かめ）

各項目の確かめた方法と結果を `generation-notes.md` に書き、`code-summary.md` に写す。

- [x] **NFR9.5（招待のテストの中身）**: `git diff develop -M -- backend/src/test frontend/src/features/invitation` で、招待のテストの差が import の行と `PAGE_SIZE` の参照先だけであること、`InvitationPagingTest` → `PagingTest` と `paging.test.ts` の移動の差が、参照先・説明文・足した確かめだけであることを確かめる（`cicd-pipeline.md` 8節 (ii)）
- [x] **口の型（NFR3.1、C2・C5）**: `Paging` の4つの口と UiPaging の7つの export の名前・引数・結果・例外が移す前と同じであることを、移す前のファイル（`git show develop:<パス>`）と並べて確かめる
- [x] **TRACE に出うる範囲（NFR3.1、NFR 要件の R-02、R3）**: `Paging` が Spring の部品でなく（注釈が無い）、`common.observability.TraceAspect.EXPRESSION` の対象（`web`・`service`・`domain`・`repository` の層の Spring の部品）に当たらないこと、page の文字列が TRACE に出うるのは `InvitationAdminController#list(String)` と `InvitationService#list(String)` の引数だけで、ほかのログ・トレースの属性・エラー応答に出していないことを、ソースの検索で確かめる
- [x] **A-01（要求の行の上限）**: 9節の Q-B の決定（A）のとおり、次で確かめて記録する:
  - `backend/src/main/resources/application.yaml` が `server.max-http-request-header-size` を変えていないことと、使っている Spring Boot の版の既定の値（`ServerProperties` の既定）を、依存の部品の中身（読み取りだけ）で確かめる
  - 一時の結合テスト（例 `backend/src/test/java/cherry/mastersmith/invitation/web/RequestLineLimitProbeIT.java`。既存の招待のテストは変えない）で、管理者のアクセストークンつきで page に 8KB を超える文字列（例 9,000 文字の数字）を付けた一覧の要求と、上限より短い長い文字列（例 7,000 文字）の要求を送り、前者がアプリに届かず組み込みのサーバーで拒否されること（状態コードを記録する）と、後者が 400 `VALIDATION_FAILED` になることを確かめる。流した後にそのファイルを消し、`git status` で残っていないことを確かめる（`source-manifest.json` には載せない）
  - 結果が設計の見込み（8KB が要求の行にも効く）と違ったときは、根拠1（page の文字列は個人に関する値・秘密ではない）・根拠2（TRACE は既定で無効）だけで受け入れ（Q1 A）が成り立つことを `code-summary.md` に書き、承認の場で依頼者に伝える。設計の文書は書き換えない
- [x] **A-02（ログの行の偽造）**: TRACE の行の page の文字列は `message` の項目に入る。`backend/src/main/resources/logback-spring.xml` の1行1件の JSON の出力で、`common.observability.SingleLineMessageJsonProvider` が改行の並び（CRLF・CR・LF）を「 ⏎ 」に置き換え、ほかの制御文字は JSON の文字列としてエスケープされることを、既存の `JsonLogFormatTest`（`values containing line breaks stay on one line and cannot forge another record`・`line breaks in a message are replaced so the message stays on one line`）を流して確かめ、記録する。コードは変えない。外部エクスポート（既定で無効）は1行の形に頼らないことも記録する
- [x] **A-03（`PAGE_SIZE` の一致）**: 9節の Q-C の決定（A）のとおり、サーバーの側は `PagingTest` の固定（Step 4）と既存の `InvitationAdminApiIT`・`InvitationServiceTest` の `size` 20 の確かめ、画面の側は `paging.test.ts` の `PAGE_SIZE` 20 の確かめで、片方だけの値の変更がその側のテストで止まることと、両方の説明文が互いの場所を示していることを確かめる。E2E の 060 の実データの1件は 20 行目の表示（`rows.nth(19)`）で間接に確かめる（Step 15）
- [x] **NFR5.1（問い合わせの回数）**: `InvitationService#list`・`readPage` の差が参照先の名前だけで、数える1回と読む1回が変わらないことを差分で確かめる
- [x] **NFR5.2・NFR9.11・カバレッジの設定（変えないもの）**: `git diff --stat develop -- docker/monitoring gradle/libs.versions.toml backend/gradle.lockfile frontend/package.json frontend/package-lock.json backend/build.gradle.kts backend/config/spotbugs-exclude.xml frontend/vitest.config.ts .gitleaks.toml` が空であることを確かめる（`cicd-pipeline.md` 8節 (v)）
- [x] 対応: NFR3.1・NFR5.1・NFR5.2・NFR9.5・NFR9.11、A-01〜A-03、NFR 要件の R-02、R3・R4

### Step 13: U2 の1コマンドの検査（単位ごとの squash の条件とカバレッジの実測）

- [x] U4 の変更がまだ無いこと（作業フォルダの変更が U2 の分だけであること）を `git status` で確かめる
- [x] colima が動いていることを確かめ、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行し、すべての段（フォーマット・リンタ・ライセンスヘッダー・ビルド・単体・結合・カバレッジ・安全の検査・成果物）が通ることを確かめる。対象DB のテストが SKIPPED になっていないことを確かめる（`project.md` の学び）
- [x] テストの件数（バックエンドの単体・結合、フロントエンド）と、全体と Step 1 と同じパッケージ（`invitation.domain`・`invitation.service`・`invitation.web`・`invitation.repository`）と `common.paging` の行・分岐のカバレッジを実測の数字で記録し、Step 1 の基準と 4.3 の見込みと比べる。件数の差（`PagingTest` の足した性質3つと固定の1件、`paging.test.ts` の足した性質4つ）を数で説明する
- [x] `invitation.domain` と `common.paging` がそれぞれ行 80%・分岐 70% を満たすことを確かめる。下回ったら、計測の除外を増やさず、同じパッケージのほかのクラスのテストを足す（残る危険 R1、NFR9.8・NFR9.9）。下限・除外・`packagesJudgedByTotal` は変えない（Testing Contract）。画面の全体の合計が下限を満たすことを確かめる（NFR9.10）
- [x] SpotBugs ＋ FindSecBugs・Gitleaks を除外を足さずに通すことを確かめる（NFR9.4）。`osvScan` が UP-TO-DATE だったときは、この時点では流し直さない（lockfile を変えないため。B2 の関門の Step 15 で `--rerun-tasks` で流す）
- [x] 失敗が一時的に見えるときは、`team.md` の「不安定なテストと CI の失敗」の決まりで扱う（手元で再現したら原因を直す）
- [x] この verify が通ったことを、単位ごとの squash の条件（3節）の確かめとして記録する
- [x] 対応: NFR9.4・NFR9.8〜NFR9.10、A-04、基盤の設計の R-02・R-03、`project.md` の Mandated（統合の前の確認）

### Step 14: 記録とコミットの提案（U2）

- [x] `code-summary.md`（作ったもの・変えたもの・消したもの、Step 1 と Step 13 の実測と 4.3 の見込みとの比べ、Step 12 の確かめの結果（A-01〜A-03・TRACE の範囲・テストの差）、計画・承認済みの文書との差、承認の場で確かめること）、`source-manifest.json`（作った・変えた・消したアプリのソースとテストのパスすべて。一時の確かめのファイルは消したため載せない）、`traceability.json`（AC1.1.1・AC1.1.5・AC1.1.7 を「部分」で OK、残りの 10 件を u3-user-admin-api・u5-user-admin-ui へ Deferred、BR1.1〜BR3.5 と NFR3.1・NFR5.1・NFR5.2・NFR9.1〜NFR9.11・NFR11.1 の実装とテストのファイル）を作る（コード生成の段の手順）
- [ ] 3節の C1・C2 の区切りで、コミットの内容とメッセージの案を依頼者に示して承認を得る（生成の担当はコミットしない）
- [ ] 承認を得てコミットした後、`git status` で U2 のアプリのソースに未コミットの変更が無いことを確かめ、U2 の最後のコミットのハッシュを `code-summary.md` に記録する（単位ごとの squash の条件、3節）。U4 の作業はこの後に始める
- [ ] 対応: 段の記録、`project.md` の Change Control、`team.md` の Way of Working

### Step 15: B2 の統合の前の関門と統合（U4 の生成の後に B2 全体で1回）

この Step は U2 の生成の依頼では行わない。U4 の生成と記録が終わった後に、B2 全体で1回だけ行う（U4 の計画はこの Step と同じ1回を指し、二度は流さない）。

- [ ] colima が動いていることを確かめ、`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡して `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を B2 全体（U2・U4 のコミットの後の作業ブランチ）で通し、件数とカバレッジ（Step 13 と同じパッケージと全体、画面の全体）を記録する
- [ ] `caffeinate -i ./gradlew osvScan --rerun-tasks` を流し、通ること（走査したパッケージの数、失敗の条件に当たるもの 0 件、警告の件数）を記録する（`verify` の中の `osvScan` は UP-TO-DATE になりうるため。B1 の経過）
- [ ] Mailpit が動いているかを確かめ（`docker compose ps`、`http://127.0.0.1:8025/api/v1/info` が 200）、動いていなければ `docker compose --profile mail up -d mailpit` で起動する。`.env` は開かない
- [ ] `caffeinate -i ./gradlew e2eTest` を実行する（`cicd-pipeline.md` 4節。U2 は E2E のファイルを足さない）
- [ ] `frontend/test-results/e2e-results.json` から次を `code-summary.md`（B2 の記録）に記録する。読むのは `stats` とテストごとの題・状態・注記の種類だけで、ほかの中身は開かない:
  - 件数（`stats` の成功・失敗・飛ばした・不安定と時間）と、ファイルごとの件数と結果
  - `060-invitation-accessibility.e2e.ts` の結果: 表示の設定の組ごとの 20 件の結果と、実データの1件（`list and next page time with real invitations, without CSP violations`）の状態。飛ばされたときは注記 `skip-reason` があることと、そのときは本物のサーバーを通したページ送りの操作は確かめられず、画面のページ送りの確かめは Vitest だけが担ったことを記録する（2.2）
  - `090-invitation-registration-flow.e2e.ts` の結果（ページ送りに触れない流れとして、変更なしで通ったこと）
- [ ] 記録した後に `frontend/playwright-report/`・`frontend/test-results/` を消し、消したこと（ファイルとディレクトリの件数）と、報告を誰にも共有していないことを記録する（`gate-decisions.md` の U5 R-02 の決定、2回目の承認の場の U2 の申し送り）
- [ ] Mailpit は、この Step で起動したときは見終わったら止める。もとから動いていたときは止めない（B1 の経過）
- [ ] E2E が失敗したら原因を直してから、この Step の verify からやり直す。U2 のソースを直したときは、単位ごとの squash の条件が崩れるため、Bolt 全体の1コミットに切り替える（3節）
- [ ] 依頼者の承認を得て、3節の形（単位ごとの squash、または条件を満たさないときは Bolt 全体の1コミット）で `develop` へ統合する。統合の後、依頼者の承認を得て作業ブランチを消す。プッシュは依頼者が行う
- [ ] 対応: B2 の共通の完了の条件、`cicd-pipeline.md` 4節・5節・8節 (iv)、基盤の設計の R-01・R-03、`gate-decisions.md`、`project.md` の Mandated

## 6. ストーリー・要件と手順の対応

| ストーリー・要件 | 受け入れ基準・決まり・NFR | 手順 |
|---|---|---|
| US1.1 利用者の一覧を見て、利用者を探す（従。主は U3） | AC1.1.1 の部分（1ページ 20 件、応答の page・size・total、画面のページの数と「n〜m 件目」） | Step 3〜10 |
| US1.1 | AC1.1.5 の部分（page の誤りは 400 `VALIDATION_FAILED` で監査なし、最後のページより後は 200 の全体の件数つきの空の一覧） | Step 3〜6 |
| US1.1 | AC1.1.7 の部分（20 件で1ページ、21 件で2ページで2ページ目は1件） | Step 3〜10 |
| US1.1 | AC1.1.2〜AC1.1.4・AC1.1.6・AC1.1.13（u3-user-admin-api）、AC1.1.8〜AC1.1.12（u5-user-admin-ui） | Deferred（Step 14 の `traceability.json`） |
| サーバーの計算（Paging） | BR1.1〜BR1.4 | Step 3・4・6 |
| サーバーの作りの決まり | BR1.5（純粋な関数・口を変えない・`InvitationPaging` を消す）・BR1.6（説明文） | Step 3・5・11・12 |
| 画面の計算（UiPaging） | BR2.1〜BR2.4 | Step 7・8・10 |
| 画面の作りの決まり | BR2.5（純粋な関数・export を変えない・文言は移さない） | Step 7・9・11・12 |
| 呼び出し元の決まり | BR3.1〜BR3.4（招待の一覧の検証・空の一覧・応答の項目・並び） | Step 5・6 |
| 呼び出し元の決まり | BR3.5（計算を自分で持たない） | Step 5・9・11 |
| 秘密と個人に関する値 | NFR3.1（口の型、TRACE に出うる範囲、R3） | Step 12 |
| 性能・観測 | NFR5.1（問い合わせの回数・`invitationList` は performance-validation）・NFR5.2（指標・警報を足さない） | Step 12、Build and Test に引き継ぐこと |
| テストと関門 | NFR9.1〜NFR9.3（検証・桁あふれ・エラー応答） | Step 4・6 |
| テストと関門 | NFR9.4（静的解析・検査の関門） | Step 10・13・15 |
| テストと関門 | NFR9.5（招待の振る舞いとテストを変えない） | Step 6・10・12 |
| テストと関門 | NFR9.6・NFR9.7（性質ベースのテストと種） | Step 4・8 |
| テストと関門 | NFR9.8〜NFR9.10（カバレッジ） | Step 1・13・15 |
| テストと関門 | NFR9.11（依存を足さない） | Step 12 |
| 構造 | NFR11.1 | Step 3・7・11 |
| NFR 設計の申し送り | A-01（要求の行の上限）・A-02（ログの行の偽造）・A-03（`PAGE_SIZE` の一致）・A-04（`invitation.domain` の見込み） | Step 12（A-01〜A-03）、4.3・Step 1・13（A-04） |
| 基盤の設計の申し送り | R-01（E2E の 060・090 とページ送り）・R-02（`parsePage` の分岐の見込み）・R-03（単位ごとの squash の条件）・E2E の報告の扱い | 2.2・Step 15（R-01）、4.3（R-02）、3節・Step 13〜15（R-03）、Step 1・15（報告） |
| B2 の完了の条件（U2 の分） | `common.paging`・`src/shared/` へ移す、招待の振る舞いとテストを変えない、性質ベースのテスト | Step 3〜13 |

## 7. テストの量（Standard）

部品ごとに 5〜8 件の単体テストと、主な境界の結合テストを置く（Testing Contract の `strategy_volume`）。U2 の部品は2つ（Paging・UiPaging）。主な境界（招待の一覧の API と招待の画面）は、既存の結合テストと画面のテストを変えずに流して確かめる。

| 部品 | 単体（サーバー `*Test`・画面 `*.test.ts`） | 結合・画面の既存のテスト（変えずに流す） |
|---|---|---|
| Paging（`common.paging`） | `PagingTest`: 移す6件（境目の値の引数つき1件、指定なしと正の整数1件、拒否する入力の引数つき1件、`offsetOf` 1件、1 未満の例外1件、jqwik の性質1件）＋足す jqwik の性質3件＋`PAGE_SIZE` の固定1件＝10 件 | `InvitationAdminApiIT`（一覧の 20 件・2ページ目・最後のページより後の空の 200・不正な page の 400・`size` 20・招待中の重なりの page）、`InvitationAuditIT`（招待中の重なりのページ）、`InvitationRepositoryIT`（参照先だけ）、`InvitationServiceTest`（一覧の `InvalidPage` と空） |
| UiPaging（`src/shared/paging`） | `paging.test.ts`: 移す7件（ページの数・範囲・0 件・補正する・補正しない・ボタン・fast-check の性質1件）＋足す fast-check の性質4件＝11 件 | `InvitationList.test.tsx`（ページ送りの状態の文とボタンの端）、`InvitationAdminPage.test.tsx`（ページの移動とフォーカス、最後のページより後の補正）、E2E の 060 の実データの1件（B2 の関門、Step 15） |
| 構造 | 既存の `ArchitectureTest`・`*BoundaryArchitectureTest`（変えない） | — |

- 単体テストが 5〜8 件を超えるのは、移すテストの事例をすべて残す決まり（NFR9.5）と、性質を足す決まり（NFR9.6、FS 6節）を合わせたため。
- U2 は新しい結合テストのクラスを足さない（8節の D-2）。Standard の「主な境界の結合テスト」は、招待の一覧の API の既存の結合テスト（`InvitationAdminApiIT`）が、Paging を通した検証・空の一覧・応答の size を確かめているため、それを変えずに流すことで満たす。
- 性質ベースのテストは、jqwik が `@Property(tries = 500)`、fast-check が既定の 100 回（NFR9.6）。失敗のときの種の再現の仕方は各テストの先頭に書く（NFR9.7、`unit-test-instructions.md` 5節）。
- どのテストも成功の場合と、少なくとも2つの失敗・境目の場合を含める（`phases/construction.md` の Testing Standards）。Paging は拒否する入力8種と 1 未満の例外、UiPaging は 0 件・最後のページを読んだ後の補正しない場合・押せなくなる端を含む。
- U2 は認証・認可・監査・メール・DSL の機能ではないため、`team.md` のそれらの必須テストは当たらない。ページの番号の拒否を監査に残さないこと（BR3.1）は、既存の招待のテストのまま。

## 8. この計画で決めたこと・承認済みの文書との差

承認済みの文書は書き換えず、差をここと `code-summary.md` に記録する（`project.md` の決まり）。

| ID | 対象 | 承認済みの形 | この計画での扱い | 理由 |
|---|---|---|---|---|
| D-1 | 移すテストの中身（FS 2.4 の3、`logical-components.md` 5節） | 今の事例をすべて残し、FS 6節の性質（jqwik 3つ・fast-check 4つ）を足す | 加えて `PagingTest` に `PAGE_SIZE` が 20 であることの確かめを1件足し、両方の `PAGE_SIZE` の説明文に互いの場所を書く（9節の Q-C の決定） | NFR 設計の承認の場の A-03（一致を機械で見つける確かめを計画で決める）への答え。振る舞いは変えない |
| D-2 | NFR 要件の Request Changes の理由（監査ログ）の「コード生成の計画（U2 の結合テスト …）」 | （申し送りの文言だけ） | U2 で新しい結合テストのクラスは足さず、`security-requirements.md` の R-04（招待の一覧の既存の結合テストで「空にする」判定と読み取りの位置の一致を確かめる）として、`InvitationAdminApiIT` を変えずに流す。A-01 の一時の確かめ（9節の Q-B の決定）は残さない | 承認済みの `security-requirements.md`・`security-design.md`・`cicd-pipeline.md` が、招待の既存のテストを変えずに通すこと（NFR9.5）と、利用者の一覧の側の結合テストは U3 が足すことを決めているため |
| D-3 | 基盤の設計の R-01 の見込み（`cicd-pipeline.md` の承認の場の決定の表） | 「操作しないなら、画面のページ送りの確かめは Vitest だけが担うと書き分ける」 | 確かめた結果、E2E の 060 の実データの1件は本物の API で「次へ」「前へ」を押す（090 は操作しない）。本物のサーバーと画面を通したページ送りの操作は 060 の1件が、補正・フォーカス・境目・性質は Vitest が担うと書き分けた（2.2）。060 の1件が飛ばされたときは Vitest だけと記録する（Step 15） | 実際の E2E のファイルの中身（`060-invitation-accessibility.e2e.ts` の実データの1件）。E2E のファイルは変えない |
| D-4 | 移し替えの順（FS 2.4 の1〜6） | `InvitationPaging` を移して消し、使い手の参照先を変える | 新しい部品を先に作ってテストし（Step 3・4・7・8）、使い手を切り替えてから古い部品を消す（Step 5・9） | test-after の層の順のまま、各 Step の終わりでコンパイルと型の検査を通すため。できあがりは同じ |
| D-5 | B2 の関門と統合の置き場（`cicd-pipeline.md` 4節・5節） | B2 で1回の verify と E2E、統合の形はコード生成の計画で決める | U2 の単独の verify（Step 13）を単位ごとの squash の条件の確かめとし、B2 全体の verify・`osvScan --rerun-tasks`・E2E・統合は U4 の生成の後に1回（Step 15）とした | 基盤の設計の R-03（U2 のコミットが単独で verify を通ること）を満たしつつ、E2E を B2 で1回に保つため |

ほかに、上流（要件・機能設計・契約 C2・C5・NFR 要件・NFR 設計・基盤の設計）と違う作りはない。

## 9. 依頼者の決定

計画の承認の前に諮った4つの論点について、依頼者が次のとおり決めた（4問とも A）。計画の各 Step と8節はこの決定に合わせてある。

**Q-A B2 の統合の形**（機能設計の 7節、基盤の設計の R-03）

- 依頼者の答え: **A（単位ごとの squash）**
- 決定: U2 と U4 で `develop` の2コミットにする。条件は3節のとおり、U2 の最後のコミットの中身が単独で `./gradlew verify` を通っていること（Step 13・14 で確かめる）。U4 を始めた後に U2 のソースを直したときは、Bolt 全体の1コミットに切り替える
- 選ばなかった案: B（B2 全体を1コミットで squash する）
- 理由: U2 は振る舞いを変えない移し替えで、U4（403 の扱い）とは目的が違い、`develop` の履歴で別々に追えるほうが戻しと調べものに向く。条件の確かめは Step 13 の verify で足り、追加の時間は小さい

**Q-B 要求の行の上限 8KB の確かめ方**（NFR 設計の A-01）

- 依頼者の答え: **A（一時の結合テストで確かめて記録し、消す）**
- 決定: 設定と依存の版の既定の値を読み取りで確かめたうえで、一時の結合テストで 8KB を超える page と超えない長い page を送って確かめ、結果を記録してからそのファイルを消す（Step 12）。テストは残さない
- 選ばなかった案: B（同じ確かめを恒久の結合テストとして新しいクラスで残す）、C（設定と依存の版の既定の値の読み取りだけで確かめ、要求は送らない）
- 理由: A-01 は「確かめ、結果を記録する」申し送りで、受け入れた残る危険 R3 の根拠3の確かめに当たる。`project.md` の学び（部品の振る舞いに頼る前提は捨ての試しで確かめる、確かめた後に戻す）に沿う

**Q-C サーバーと画面の `PAGE_SIZE`（20）の一致の確かめ**（NFR 設計の A-03）

- 依頼者の答え: **A（両側をテストで 20 に固定し、説明文で互いの場所を示す）**
- 決定: サーバーは `PagingTest` に1件足し、既存の `InvitationAdminApiIT`・`InvitationServiceTest` の `size` 20 と合わせる。画面は既存の `paging.test.ts` の `PAGE_SIZE` 20 で固定する。両方の `PAGE_SIZE` の説明文に互いの場所を書く。片方だけ変えるとその側のテストで止まり、説明文で相手を直すことに気づける
- 選ばなかった案: B（画面が応答の `size` を使う形に変える。契約 C5 と招待の画面の振る舞いが変わる）、C（E2E の 060 の実データの1件に一致の確かめを足す。「060・090 は変更なしで通す」（`cicd-pipeline.md` 4節）と食い違う）

**Q-D 画面の文言（訳の鍵）を共通に移すか**（機能設計の 7節、BR2.5）

- 依頼者の答え: **A（移さない）**
- 決定: 招待の画面は今の鍵（`invitation.pager.status`・`invitation.action.prev`・`invitation.action.next` など）のまま。U5 の利用者の管理の画面は、自分の機能の鍵を U5 の計画で用意する
- 選ばなかった案: B（ページ送りの文言の鍵を共通の鍵に移し、招待の画面を切り替える）
- 理由: 契約 C5 は計算だけを共通にし、文言は範囲の外（BR2.5 の前提、ADR-004 の中立の結果）。U2 の「振る舞いを変えない」範囲に収まる

## Testing Contract

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "team",
  "ordering": "テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。",
  "scope": "classic",
  "test_strategy": "standard",
  "project_type": "brownfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる。ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる（既存のパッケージは全体の合計で判定する）。どちらになるかは、パッケージごとの下限を入れる Bolt で既存のパッケージのカバレッジを実測して決め、結果を記録する。\n- 全体の合計で判定している既存のパッケージ（`backend/build.gradle.kts` の `packagesJudgedByTotal` の一覧。2026-09-29 の時点で 12 パッケージ。`user.domain`・`user.repository`・`user.service` は Intent 260925-user-management で一覧から外れ、含まない）に手を入れる Bolt では、テストを足してそのパッケージの下限（行 80%・分岐 70%）を満たし、一覧から外してパッケージごとの下限の対象に必ず戻す。「手を入れる」は、そのパッケージの本体のソース（`src/main`）の変更のすべて（説明文だけの直しを含む）を指し、テストだけの変更は含めない。手を入れる見込みのパッケージとそれに伴う作業は、Delivery Planning とコード生成の計画で、各パッケージの今の値を実測して見積もる。カバレッジは、その Bolt のテストを足した後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、値を記録する。一度外したパッケージは一覧に戻さず、一覧を増やす変更はしない。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）。Intent `260923-dsl-schema-loader` の Build and Test で `verify` の時間と colima の VM のメモリを実測したうえで、この形に決めた。コンテナの実行環境が無いときの扱いは Way of Working のとおり。\n- 外へ送るメール（SMTP）を使う機能のテストは、送信の部品をモックで置き換えて済ませず、テストの中で JVM の中に起動するテスト用の SMTP の受け手で実際に受け、宛先・件名・本文（HTML）・言語を確かめる。受け手にコンテナは使わない（コンテナの実行環境が無いときに飛ばしてよいテストは、対象DB のテストだけのままとする）。送信の失敗（受け手が接続を拒む・応答しない）のテストも必ず入れる。実在の宛先へは送らない。受け手の具体の道具は、採用の前のライセンスの確認とあわせて設計の段で決める。\n- 画面からの一連の操作を確かめるテスト（E2E）は Playwright（`./gradlew e2eTest`）で書き、`./gradlew verify` と CI の外に置く。本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す。新しく足す流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作る。利用者の状態を変える操作（利用停止・管理者の印の変更・ロックなど）は、その流れで自分で作った利用者だけを対象にし、初期管理者の状態は変えない（E2E は1つのアプリ・内部DB・初期管理者を全ファイルで共有して順に流すため）。E2E は、画面・認証に関わる変更を統合する前と、リリースの前に手元で実行する。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- リポジトリは公開のため、テストデータのメールアドレスは予約のドメイン（`example.com` など）だけにし、実在しそうな氏名・宛先を置かない。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- 画面のテストで、描画の後（`useEffect` などの効果）に反映される値は、操作の直後に同期で確かめず、`waitFor` で待って確かめる。テストの時間の上限（Vitest の既定 5 秒など）は、原因を確かめずに延ばさない。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。\n- 不安定なテストと CI の失敗は、次の1つの決まりで扱う。\n  - 手元で再現した不安定なテストは、原因を直すまで統合しない（次へ進まない）。\n  - 手元で再現しないものは、再現の試みに先に時間の上限を決め、見立てと試みの範囲を記録したうえで、CI の再実行で通れば進めてよい（不安定と確かめられていない扱い）。\n  - 同じテストが二度目に落ちたら、原因を直すまで次へ進まない。\n  - 失敗を直さずに次の Intent へ持ち越すのは、依頼者の決定があるときだけとし、決まりとの差を記録する。\n- 認証・認可・監査に関わる機能では、次のテストを必ず書く。★印の項目は要件（しきい値や動作）が未確定のため、要件定義で決めてからテストを書く。\n  - アカウントロック: 失敗回数のしきい値の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロック中は正しいパスワードでも拒否、ログイン成功時の失敗回数の扱い。★しきい値、回数を数える期間、ロックの解除方法\n  - ログイン: 存在しないユーザーとパスワード誤りで、応答からユーザーIDの存在を推測できないこと\n  - トークン: 有効期限の境界（直前は有効／直後は無効）、署名の改ざん、署名方式の指定を悪用した改ざん（`alg: none` 等）、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセストークンが有効期限まで使えること（決定済みの仕様として明示する）。★各トークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか\n  - 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い／不正なときの動作、パスワードがログに出ない。★設定が無いときに起動を止めるか\n  - 認可: 未認証（401）、管理者フラグなし（403）、管理者（200）をサーバー側のテストで確かめる（画面で管理メニューを隠すことはサーバー側の検査の代わりにしない）\n  - 監査ログ: 対象イベントごとに必須項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること。★監査ログの書き込みに失敗したときに操作を失敗させるか\n  - 秘密情報の漏えい: ログ・監査ログの出力にパスワード・トークンの値が含まれないこと\n  - 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる、外部エクスポートが既定で無効であること\n  - 画面: ログイン画面のアクセシビリティ検査、ロック時のメッセージ表示、ログアウトでトークンが破棄されること\n  - 招待と登録の完了: 招待の有効期限の境界（直前は有効／直後は無効）、使い終えた招待・取り消した招待の再使用の拒否、改ざんした招待・存在しない招待の拒否（応答から利用者の存在を推測できないこと）、招待中（登録が終わっていない）の利用者はログイン・トークンの更新・アクセストークンの認証のどれでも拒否されること。★招待の有効期限の値、再送・取り消しの扱い\n  - パスワードの変更: 今のパスワードの確かめ、パスワードの規則の境界、変更の後のリフレッシュトークンの扱い（決めた側の動作を明示したテストにする）。★パスワードの規則、変更の後にほかの端末のリフレッシュトークンを無効にするか\n  - 利用停止: 停止中の利用者は、ログインの照合・トークンの更新・アクセストークンの認証の3つの入口のすべてで拒否されることを、入口ごとにサーバー側のテストで確かめる。停止を解いた直後は3つの入口のすべてで受け付けること。停止の前に出したリフレッシュトークン・アクセストークンの扱いは、決めた側の動作を明示したテストにする。★停止を応答から推測できてよいか、停止の前に出したトークンの扱い\n  - 管理者の印の変更: 印を付けた直後・外した直後の次の要求で、管理の API の 403／200 がサーバー側で切り替わること（画面が持つ古い印に頼らない）。印を外す前に出したトークンの扱いと、自分の印を外す操作の扱いは、決めた側の動作を明示したテストにする。★印を外す前に出したトークンの扱い、自分の印を外す操作の扱い\n  - ロックの解除: 解除の直後に正しいパスワードで入れること、解除の後の失敗回数の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロックの状態の行が無い利用者の解除。時刻は注入した時計で動かし、実時刻と `sleep` に頼らない。★解除の後の失敗回数の扱い、ロックの状態の行が無いときの応答\n  - 最後の管理者の保護: 最後の有効な管理者の印を外す・利用を止める操作は拒否され、拒否の後に状態が変わっていないこと。2人の管理者が同時に互いの印を外す・利用を止めても、有効な管理者が 0 人にならないこと。同時の重なりはスレッドの数に頼らず、待ち合わせで確実に作る。★最後の管理者の数え方（止めた管理者を数えるか）\n  - 管理の API の認可: 足す管理の API のすべてについて、未認証（401）・管理者フラグなし（403）・管理者（200）をサーバー側のテストで確かめる。停止中の管理者は管理の API を呼べないこと。\n  - 要求の改ざん: 管理の API の外（`/api/me` など）から、要求の本文の値を変えて自分の管理者の印や利用者の状態を変えられないこと（一括代入の防止）。\n  - 管理の操作の監査: 利用者の権限・状態を変える操作（印の付け外し・利用停止と再開・ロックの解除）ごとに、操作した人・対象の利用者・結果が記録されること。★拒否した操作（403・最後の管理者の拒否など）を記録するか\n  - 利用者の管理の漏えい: 一覧・詳細の応答と監査の行に、パスワードのハッシュ値・リフレッシュトークン・ロックの判定の内部の値が含まれないこと。TRACE のログを有効にして一覧・詳細を読んでも、メールアドレス・氏名がアプリのログに出ないこと（既存の `*SecretLeakIT` と同じ形で確かめる）。\n- 利用者へメールを送る機能では、Code Style と `project.md` のメールの決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 本文のエスケープ: 差し込む利用者の値（氏名・メールアドレスなど）に `<`・`>`・`&`・`\"`・`'` を含めても、タグや属性としてそのまま出ないこと。利用者の値をエスケープしない差し込み（`{{{ }}}`・`{{& }}`）で入れていないこと、属性の値を二重引用符で囲んでいること（エンジンの `{{ }}` は `'` を置き換えないため）\n  - テンプレートの描画: 日本語と英語のすべてのテンプレートを描けて、件名が空でないこと、差し込み漏れ（`{{` の残り）が無いこと、ライセンスヘッダーが描いた本文に出ないこと\n  - 招待の URL: 設定したベース URL だけから組み立て、要求の Host ヘッダーから組み立てないこと。ベース URL の設定が無いときに要求の Host を変えても、招待の URL に出ないこと。★ベース URL の設定が無いときの動作\n  - トークンと URL の漏えい: 招待のトークンと招待の URL が、アプリのログ・監査ログ・トレースの属性・エラー応答に含まれないこと（既存の `*SecretLeakIT` と同じ形で確かめる）\n  - ヘッダーへの差し込み: 宛先・件名・差し込む値に改行（CR・LF）を含めても、メールのヘッダーが増えず拒否されること\n  - 送信の失敗: 送信が失敗したとき（受け手が接続を拒む・応答しない）に、宛先のメールアドレス・SMTP の応答・資格情報が応答とログに含まれないこと。★送信が失敗したときの業務の動作（招待を失敗させるか、再送に回すか）\n- 利用者が投入する DSL（YAML）を読み込む機能では、Code Style の「信頼できない入力」の決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。上限の具体的な数値は設計の段で決め、決めた値の境界で確かめる。\n  - 大きさ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 入れ子の深さ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 別名（アンカー）: 展開の数が上限を超えると拒否され、別名の展開の爆発で処理が止まらない\n  - タグ: 任意の型を作るタグ（`!!` など）を含む DSL は拒否され、型が作られない\n  - 重複キー: 同じキーが重なる DSL はエラーになり、後の値で黙って上書きされない\n  - JSON Schema の `$ref`: 外部の URL を取りに行かない\n  - 拒否の応答: Problem Details の形で返り、YAML・JSON Schema の部品の例外のメッセージを含まない\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22)"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) \n- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) \n- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) \n- 同時の重なりを確実に作るため、本番のコードを変えずに、監査の書き込みの時間を測る LongSupplier（AuditEventListener で2本目を借りる直前に呼ばれる）をテストで差し替えて待ち合わせる方式にした。既存の LoginConcurrencyIT は 8 スレッドでプールの 10 に届かず、前回の失敗のログインで尽きなかった理由の1つと見られる。 (learned 2026-09-23) \n- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) \n- 修正の前の設定（例: 上限 1g）も修正の後の環境（例: CPU 4 の VM）で流し（pre1g）、要件の前提（VM を上げても F3 が起きる）を実測で裏付ける。 (learned 2026-09-23) \n- パッケージごとのカバレッジの下限と SpotBugs の SQL_ の関門を U1 の計画に入れた。team.md の決まりだが今のビルドに無く、この Intent で最初に作る単位のため。U1 の設計の文書には無い作業。 (learned 2026-09-24) \n- U4 で既存の AuditSecretLeakIT の列の一覧に V6 の4列を足し、テストの JVM のヒープを 1g にした。前者は承認済みの V6 と必ず食い違うため、後者は構造の検査がクラスを持ち続け U3 の 10MB 超えのテストでヒープが尽きたため。どちらも計画に無い変更で、依頼者に確かめる。 (learned 2026-09-24) \n- 応答しない対象DB の TIMEOUT の確かめに、テストの中で開いた ServerSocket（受け付けて何も返さない）を使う。外の端末に頼らず確実に再現できる代わりに、本物の DB の遅延ではない。 (learned 2026-09-24) \n- パッケージごとのカバレッジの下限は新しいパッケージだけに当てた。実測で audit.service（行 77.2%）・common.health（行 79.2%）・auth.repository（分岐 50.0%）が単独で下回ったため（team.md の決まりどおり）。既存の 22 パッケージを一覧で外し、新しいパッケージは自動で対象になる。 (learned 2026-09-24) \n- 負荷の試験で接続プールが尽きたかを確かめるときは、使い捨てのアプリにだけ MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics を渡して /actuator/metrics の hikaricp の値を読み、数秒ごとの使用中の数ではなく、待ちの時間切れの累計と借りるまでの待ちの最大で判断する（配備したアプリの公開の範囲は変えない）。 (learned 2026-09-24) \n- k6 などの長い試験は caffeinate -i を付けて流し、PC の自動のスリープで要求が止まって結果が崩れるのを防ぐ（バッテリー駆動のまま 414 秒スリープし、要求が 6分52秒止まった）。内部DB に SQL で直接入れた試験用の利用者はロックの状態の行が無いため、同時のログインを流す前に1人ずつログインさせて行を作る。 (learned 2026-09-24) \n- 軽い API の性能は、投入を重ねて内部DB のファイルが膨らんだ状態（悪い側の条件）のまま測る。メモリの最大（memory.peak）を比べる試験の前は、アプリのコンテナを作り直して前の最大の値を消す。 (learned 2026-09-24) \n- Performance Validation の段が無いため、2件目の直しの負荷の試験での確かめ（Q5: A）の持ち主を Build and Test とした（project.md の学びどおり）。 (learned 2026-09-24) \n- colima の PC で ./gradlew verify を流すときは、README の DOCKER_HOST と TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE をシェルに渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗した。付けて流し直した結果を基準とした。 (learned 2026-09-25) \n- unit-test-instructions.md の k6 inspect のコマンドは --include-system-env-vars が無いと場面の名前が undefined になり確かめにならなかった。承認済みの文書は変えず、実際には付けて流し、code-summary.md に差を記録した。 (learned 2026-09-25) \n- 1回目の verify で AccessTokenApiIT の 6 件が接続の失敗で落ちた。変更の経路に触れず、同じ時刻に Gradle の作業プロセスとの接続も時間切れだったため PC の負荷による一時的な失敗と見立て、段の中の直しの1回目としてコードを変えずに verify を流し直して通した。原因は確かめていない。 (learned 2026-09-25) \n- 依頼者の判断で、試験のイメージを mastersmith:local ではなく mastersmith:followup-fixes で作り、perf/README の手順（local を作り直す）から外れた。配備したアプリは k6 のあいだ止め、約 17 分後に同じコンテナで起動し直した。 (learned 2026-09-25) \n- 試験の後にロックの状態の行の数を数える問い合わせの列の名前を誤り、使い捨ての環境を消した後で取り直せなかった。合格の条件（ログインの checks と 500 の件数）で判定した。消す前に確かめの結果を見てから片付けるべきだった。 (learned 2026-09-25) \n- Q4（確かめられたときだけ直す）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違いうるため、追加の質問 F3 で、再現できなければ不安定と確かめられていない扱いとして統合してよいことを確かめた。 (learned 2026-09-25) \n- 要件 FR2.2（目標は計画の承認の場で決める）を満たすため、依頼者の決定（Q1: A）で、計画を書く前に dslMixed でメモリの内訳を測ることにした。測る間は配備したアプリを止める。 (learned 2026-09-25) \n- 生成で、テストの既定で MBean の登録を無効にする置き場を、計画の候補の TestDatabase ではなくテストの EnvironmentPostProcessor にした。TestDatabase を使わずに Spring を起動するテストのクラスが 15 あり、漏れるため。再現の結合テストは 12 回では伸びが小さく判定がはっきりしないため、24 回・履歴の上限 3 にした。 (learned 2026-09-25) \n- AccessTokenApiIT は、同じ例外の文言を仕組み（colima の IPv4 の転送と Java の IPv6 の待ち受けの番号の重なり）ごと再現できたことを原因の確認とみなし、依頼者の決定（D7）でテストの JVM に preferIPv4Stack を付けて直した。実際の1回目がこれで起きたかは確かめられず、今回の繰り返しでは重なりは起きなかった。 (learned 2026-09-25) \n- Test Strategy は Minimal だが、要件 FR5 と計画の「Build and Test に引き継ぐこと」のため、結合・性能・セキュリティの手順書も作り、この段で dslMixed・--storage --compact（40 回）・詰め直しの最中のログイン・refresh・dslCycle を流した。 (learned 2026-09-25) \n- 計画の dslCycle を最初の台本に入れ忘れ、配備したアプリをもう一度（約 4 分）止めて流した。負荷の試験の台本を書く前に、計画の「Build and Test に引き継ぐこと」の項目を一つずつ台本の手順と突き合わせる。 (learned 2026-09-25) \n- 実際のブラウザのアクセシビリティの検査（axe）は、誤りを出した状態と、現実に近いデータ（2語の氏名など）で、ブランドカラーとテーマのすべての組について行う。user-management の U7 の 080 で初めて、make-you-chic-ui の Avatar（頭文字が2文字のとき）と dark の FormField の誤りの文字のコントラスト不足が出た（050〜070 は誤りの状態を見ず、氏名が1語だったため出なかった）。 (learned 2026-09-28) \n- Playwright の webServer.env に置いた値は json の報告に残る。E2E の仮の資格情報はプロセスの環境変数で渡し、報告の部品の確かめと json の文字列の検索で、パスワード・トークン・メールアドレスが入っていないことを確かめる（user-management の U4 で見つかった）。 (learned 2026-09-28) \n- CI（66fe981）の verify が2回とも別々のテストの時間切れ（H2CompactionByPoolSuspensionIT の接続の待ち 10 秒、InvitationAdminPage.test.tsx の既定 5 秒）で失敗し、U4〜U7 の NFR7.1 が Not Met だったが、依頼者の決定「次のintentでコントラストと一緒に直す。このintentではこのまま進める。」で受け入れた失敗とした。team.md の「CI が失敗したら次に進む前に直す」と食い違うため test-results.md 8.1 に差を記録した。 (learned 2026-09-28) \n- k6 の登録の完了と取り消しの場面は、決定の文言（招待を VU の数だけ）ではなく既定 100 回・招待 100 件とした。トークンは1回しか使えず 10 件では p95 の意味が薄いため、U3 の NFR6.4 の「流す回数以上の招待を用意する」に合わせた。承認の場で確かめる。 (learned 2026-09-28) \n- caffeinate -i は場面ごとではなく、試験の台本全体を包む形で付ける。場面ごとに起こし直すと切れ目で守りが外れ、PC が眠る（user-management の Performance Validation で 92 秒眠り、要求が 1 分 29 秒遅れた）。遅れが出たときは pmset -g log でスリープを確かめる。 (learned 2026-09-28) \n- 応答しないメールの受け手は、Mailpit を docker pause で一時停止して作る（接続は受け付け、何も返さない）。コンテナを止めると接続の失敗になり、時間切れの確かめにならない（user-management の Performance Validation）。 (learned 2026-09-28) \n- Q1 で原因を確かめずに上限を延ばす答え（C）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違うため追加の質問 F1 で確かめ、延ばしたうえで失敗時の診断を足し、差を記録する形（B）になった。Q3 の「すべて試す」は verify で判定できない Temurin 26・logback-appender の扱いを F2 で確かめ、見送り（C）になった。 (learned 2026-09-29) \n- 診断の確かめで、上限 1 ミリ秒は Awaitility の問い合わせの間隔（100 ミリ秒）より短く設定の誤りで弾かれ、150 ミリ秒では手元で接続がすぐ 0 本になり時間切れにならなかった。上限 500 ミリ秒と、待ちの条件を一時的に満たせない形（== -1）にして時間切れを起こした。確かめた後に git checkout で戻した。 (learned 2026-09-29) \n- 警報が鳴ることは、しきい値を 50 ms に下げた警報の決まりの写しを使い捨ての環境に読み込ませて確かめた（Q1: A）。本当に遅い応答を作るより早く PC の負荷も小さいが、しきい値の値そのもので鳴ることは確かめていない。途中で ms-check-p95 のしきい値が 1000 ms ではなく 300 ms（バケットの境界に無い）と分かり、要件の前提 A3 と食い違った。 (learned 2026-09-29) \n- 表示の設定を画面の操作の直後に同期で確かめるテストが、負荷の高い CI で2件（ShellLayout・PreferencesPage）落ちた。描画の後の効果（useEffect）で反映される値は waitFor で待って確かめる。同じ形の RegistrationPage も合わせて直した。 (learned 2026-09-29) \n- Delivery Planning でのカバレッジの実測は、./gradlew verify 全体ではなく :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport で行い、jacocoTestReport.xml から手を入れる見込みのパッケージの値を読む（user-admin で約 5 分）。 (learned 2026-10-01) \n- 接続プールの見積もりを確かめる負荷の試験には、上限に届く形（プールの上限を下げた場面など）を含める。上限に届かない負荷では、見積もりが誤っていても合格する（user-admin の U3 の NFR 要件のレビュー R-02）。 (learned 2026-10-01)"
    }
  ],
  "obligations": {
    "strategy": "standard",
    "strategy_volume": [
      "Five to eight tests per component.",
      "Unit tests plus integration tests for key boundaries.",
      "Add E2E, performance, or security tests when requirements demand them."
    ],
    "scope_floor": [
      "Keep the existing test suite green.",
      "This scope adds no extra new-test floor beyond the selected test strategy."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "test-after",
    "runner_step": "Verify the existing test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Verify the existing test runner/configuration and record the exact unit-scoped command.",
      "Data model / database behavior - implement.",
      "Data model / database behavior - write and run its tests after implementation.",
      "Repository / data access - implement.",
      "Repository / data access - write and run its tests after implementation.",
      "Business logic - implement.",
      "Business logic - write and run its tests after implementation.",
      "API / endpoint - implement.",
      "API / endpoint - write and run its tests after implementation.",
      "Frontend behavior - implement.",
      "Frontend behavior - write and run its tests after implementation.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:1d19ce9bfd0f0e6eba516940c5111b116a2d58af04709ea78cc47cbad0196f0a",
  "contract_sha256": "sha256:25c76d4e2f9a91edd5d55fbff38287bcce006bace671ccf3da4e81c3a6393f2a"
}
```

Testing Contract の `plan_profile.steps` との対応: 骨組みと本番の設定は U2 では新しい骨組みが無いため Step 1（作業の場と基準）、テストの実行の準備は Step 2（最初のテストの Step 4 より前）。データの形と DB の振る舞い・DB アクセスの層は U2 に無い（エンティティ 0 件、スキーマの変更なし。招待の DB アクセスは変えない）。業務処理の層は、サーバーのページ送りの計算（Step 3・4）と招待の業務処理の呼び出し側（Step 5・6）。API の層は、招待の一覧の API を変えず、既存の結合テストで Step 6 に確かめる。画面（Frontend behavior）の層は Step 7〜10。環境とビルドの設定は変えず、構造と関門の確かめを Step 11〜13・15、文書と記録を Step 12・14 で行う。

## Build and Test に引き継ぐこと

| 項目 | 引き継ぐ内容 | 持ち主 |
|---|---|---|
| カバレッジの実測 | `:backend:cleanTest :backend:cleanIntegrationTest` を付けた verify で、全体と、`invitation.domain`（`InvitationPaging` が抜けた後）・`invitation.service`・`common.paging`、画面の全体の値をもう一度実測して記録する（NFR9.8〜NFR9.10） | Build and Test |
| CI | 依頼者のプッシュの後、CI（`./gradlew verify`）が通ることを確かめる。失敗したら `team.md` の「不安定なテストと CI の失敗」の決まりで扱う | Build and Test（CI の結果の確かめ） |
| E2E の報告の片付け | B2 の関門（Step 15）で流した E2E の結果の記録と、報告を消したこと。Build and Test で E2E を流し直すときも、記録してから消す（`gate-decisions.md` の U4 の申し送りの持ち主の決め方に従う） | Build and Test |
| 招待の一覧の応答時間 | 既存の k6 の場面 `invitationList`（同時 10 件で p95 1 秒）で確かめる。U2 のために新しい場面は足さない（NFR5.1） | performance-validation |
| 最後のページより後と全体 0 件の結合テスト（利用者の一覧） | U3 の一覧の API に「最後のページより後」と「全体 0 件」の応答の結合テストを足し、「空にする」判定と読み取りの位置が同じ page から導かれることを確かめる（機能設計の R-02、NFR 要件の R-04、残る危険 R2） | B4（U3 のコード生成の計画）。B3 で一覧の API を作るときは B3 |
| page の文字列と TRACE（利用者の一覧） | U3 の一覧の controller と業務処理が page の文字列を受ける形を、招待と同じ受け入れた残る危険 R3 として扱い、出うる範囲を確かめる | B3（U3 のコード生成の計画） |
| UiPaging の使い手 | U5 の利用者の管理の画面が、操作の後の読み直しで `correctedPage` を使うこと（画面イメージ S5）。文言の鍵は U5 が自分で持つ（9節の Q-D の決定） | B5（U5 のコード生成の計画） |
| 配備の後の確かめ | 配備の後のスモークテストに、招待の一覧のページ送りの確かめを入れるか | deployment-pipeline |
| コードの知識ベースの古い記述 | `aidlc/spaces/default/codekb/mastersmith2/` の `InvitationPaging` の記述（`api-documentation.md` ほか）は、次の Reverse Engineering で直す。この段では書き換えない | 次の Intent の reverse-engineering |
