# コードの品質（mastersmith2）

> 2026-10-16（記録のコミット `09b795d`）の Full rescan で作り直した。所見の本文はこの文書に K-53・K-57・K-58・K-59 を書いた。テストの件数とカバレッジの実測の値はこの段では取っていない（`./gradlew` を流していない）。

## テスト

- 置き場: `backend/src/test/java/cherry/mastersmith/`（機能ごと、単体 `XxxTest`・結合 `XxxIT`、機能ごとの `testsupport/`）、`frontend/src/**/*.test.ts(x)`（対象と同じ場所）、`frontend/e2e/`（18 本、`./gradlew e2eTest`、`verify` と CI の外）。
- 道具: JUnit 5・AssertJ・Spring Boot Test・Testcontainers（対象DB の3種類）・jqwik・ArchUnit／Vitest・Testing Library・user-event・vitest-axe・fast-check／Playwright と axe-core。
- 境界の構造の検査（ArchUnit）は 18 個: `ArchitectureTest`・`ApiAccessArchitectureTest` と、`access`・`appearance`・`audit`・`auth`・`dsl`・`dslmanage`（2つ）・`group`・`invitation`・`mail`・`navigation`・`role`・`targetdb`（`TargetDbBoundaryArchitectureTest` と `testsupport/ExtensionOrderArchitectureTest`）・`user`・`useradmin`。業務データの機能に深く関わるのは `TargetDbBoundaryArchitectureTest`・`RoleBoundaryArchitectureTest`・`NavigationBoundaryArchitectureTest`・`DslBoundaryArchitectureTest`（K-49・K-50・K-61）。
- API の分類の網羅は `ApiAccessArchitectureTest`・`ApiAccessConsistencyIT`・`PublicApiInventoryTest`（ファイルの有無だけ確かめた）。

## カバレッジ

- バックエンドは JaCoCo（`backend/build.gradle.kts` の `jacocoTestCoverageVerification`）。全体とパッケージごとに行 80%・分岐 70%。
- 全体の合計で判定する既存のパッケージ `packagesJudgedByTotal` は 7 つ（`backend/build.gradle.kts` 230〜238 行: `access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）。`team.md` の記録と一致する（アーキテクトが確かめ直した）。新しく作るパッケージはパッケージごとの下限の対象に自動で入る。業務データの監査を `audit` に足すと `audit.repository`、管理の判定を変えると `access.service` に手が入りうる（一覧から外す作業が付く。`team.md` の Testing Posture）。
- 画面は `@vitest/coverage-v8` の `thresholds`（前の記録。今回は `vitest.config.ts` を開いていない）。

## リンタと静的解析

- Java: Spotless（palantir-java-format・ライセンスヘッダー）、SpotBugs＋FindSecBugs（`backend/config/spotbugs-exclude.xml`。`SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` は priority にかかわらず止める、`team.md`）。業務データの問い合わせを組み立てると `SQL_` の指摘の対象になりうる（K-51 の識別子の引用）。
- 画面: Prettier・oxlint・ESLint（`frontend/eslint.config.js`。機能どうしの import と `src/shared/` から `src/app/`・`src/features/` への import を止める）・Stylelint。

## CI と依存

- `.github/workflows/ci.yml`（`develop` への push で `./gradlew verify`。今回は開いていない）。
- `.github/dependabot.yml`（npm・gradle・github-actions・イメージ）。`axe-core` の `>= 4.14.0` の見送り（K-60、本文は `dependencies.md`）。

## ドキュメント

- Javadoc・コメントは日本語で、要件・設計の ID（BR・NFR・契約の番号）と Intent の名前を細かく引く。`frontend/src/features/README.md` に機能の作り方の決まり、`perf/README.md` に負荷の試験の手順。

## 技術的負債

- 業務データの一覧・詳細の土台が無く、テーブルを指すメニューの項目は準備中の画面へ移る（K-52、本文は `architecture.md`）。
- 監査の受け手が各機能の出来事の型をすべて import する集まりの形（K-54、本文は `architecture.md`）。
- `targetdb/domain/SqlIdentifiers.java` は「今は使わない」識別子の引用の入り口（K-51、本文は `api-documentation.md`）。

**K-53 管理の可否は今も `users.admin_flag` だけで決まり、ロールの仕組みには「管理」の権限が無い（F2 の論点。`access`・`user`・`useradmin`・`auth`）**

- 確かめた事実（開発担当の本体のソースの grep）:
  - 管理の API の判定 `backend/src/main/java/cherry/mastersmith/access/web/AdminAuthorizationManager.java` は、要求の主体 `AuthenticatedUser.admin()` だけを見る。`AuthenticatedUser` は要求ごとに内部DB から読んだ `user.admin()` で作る（`auth/web/AccessTokenAuthenticationProvider.java` 67 行）。流れは `architecture.md` の Interaction Diagrams 5。
  - `admin_flag` を読む・書く場所: `user/domain/User.java`（列 `admin_flag`）、`user/repository/UserRepository.java`（一覧・詳細の射影、`adminFlag = true and suspended = false` の数え（181 行）、`updateAdminFlag`（195 行））、`user/repository/UserRowLockRepository.java`（81 行、最後の管理者の保護の行の排他）、`user/service/UserAccountService.java`（`setAdmin`・初期管理者の救済の条件 464 行）、`useradmin/service/UserAdminService.java`（`GRANT_ADMIN`・`REVOKE_ADMIN`、操作する人が管理者かの判定 354 行）、`auth/web/CurrentUserResponse.java`（画面へ `admin` を返す）。画面の `LoginState.admin`（`frontend/src/app/registry/types.ts`）が管理のメニューの出し分けに使われる。
  - ロールの権限は対象（スキーマ・テーブル・カラム）への主権限・補助権限だけで、管理の権限を表す値も組み込みのロールも無い（`role/domain/` の型の一覧、`V11__u4_role.sql`）。`V11`・`V12` の移行のコメントは「`users.admin_flag` は残し、行を移さない」。
  - テストのソースで `admin_flag`・`adminFlag` を含むファイルは 34（アーキテクトが `grep -l` で数え直した）。
  - 前の Intent の振り返り（`261004-role-menu` の `feedback-loop.md` の F2）も「ロールでは管理の API を使えない。消すのは後の Intent で、戻したときの権限のずれの確かめを入れる」。
- 今回の Intent への意味: F2 は列を消すだけでは済まず、先に「管理の可否をロールで表す形」（管理の権限の値か組み込みのロール、最後の管理者の保護の数え直し、初期管理者の作成と救済、管理者の印の付け外しの API と監査の読み替え、画面の `admin`）を決める必要がある。`team.md` の Deployment の「広げてから縮める二段」と「前の版へ戻したときの権限のずれの確かめ」も当たる。業務データの機能とは独立に大きい。
- 気になる点: このワークフローの範囲は classic で、範囲を決める段（`scope-definition`）は SKIP。F2 をこの Intent に入れるかは要件定義の段で扱うことになる見込みで、指揮役が確かめる。

## 不安定なテスト（前の Intent からの持ち越し）

**K-57 `RoleConcurrencyIT` の #7 は、監査の行の ID の順に頼っており、コードの作りの上で順は決まっていない**

- 確かめた事実:
  - テスト（`backend/src/test/java/cherry/mastersmith/role/web/RoleConcurrencyIT.java` 145〜185 行）は、1件目の保存を排他の後（`AFTER_LOCK`）で止め、2件目を排他の前（`BEFORE_LOCK`）まで進めてから1件目を放す。監査の行を `ORDER BY audit_event_id` で読み（173 行）、1件目に「`before` が null・`after` が READ」、2件目に「`before` が READ・`after` が FULL」を求める（177・178 行。アーキテクトが確かめ直した）。
  - 監査は確定の後（`AFTER_COMMIT`）に、各要求のスレッドで `REQUIRES_NEW` の別のトランザクションで書く（`AuditEventListener`・`AuditEventRecorder`、`architecture.md` の Interaction Diagrams 4）。1件目の確定から1件目の監査の書き込みまでの間に、2件目は排他を得て確定し、自分の監査を書きうる。`audit_event_id` は書いた順に振られるため、2件目の行が先になりうる。
  - CI の記録（`261015-deps-ui-carryover` の `deployment-log.md`）: 1件目に後の保存の監査（`before` が READ、`after` が FULL）が来て落ちた。手元の 5 回では再現しなかった。
- 見立て: 上の作りから、CI の失敗はこの競り合いで説明できる（手元では再現していないため、確かめた原因ではない）。直し方の候補は、テストが行の順に頼らず中身で2行を見分ける（`before` が null の行と READ の行がそれぞれ1つずつある）か、1件目の監査が書かれたのを待ってから2件目を確定させる待ち合わせを足すこと。前者はテストだけの直しで、本番の監査の順の決まり（あるなら）は変えない。

**K-58 `AdminForbiddenProvider.test.tsx` の1件は、描画の後の効果の回数を、効果が済む前に写しうる形**

- 確かめた事実: テスト「keeps the same function before and after the URL becomes forbidden」（`frontend/src/app/admin-forbidden/AdminForbiddenProvider.test.tsx` 231〜242 行）は、`await screen.findByTestId('probe')` の直後に `effectRuns` を写し（`runsBefore`）、403 を知らせた後に `effectRuns` が変わらないことを求める。`effectRuns` は部品の `useEffect(() => { effectRuns += 1 }, [report])`（67〜69 行）で増える（アーキテクトが確かめ直した）。CI の失敗は `expected 1 to be +0`（`261015-deps-ui-carryover` の `test-results.md` 3節）。
- 見立て（未検証）: `findByTestId` は要素が現れた時点で解け、`useEffect` がまだ動いていないことがある（遅い CI で起きやすい）。写した後に初めの効果が動くと、写した値 0 に対して 1 になる。`team.md` の「描画の後に反映される値は `waitFor` で待って確かめる」に当たる形で、写す前に `effectRuns` が初めの1回に達したことを `waitFor` で待てば、確かめたい中身（403 の後に `report` が変わらない）は保てる見込み。
- K-57・K-58 の扱い: どちらもテストの側の直しで足りる見込み（未検証）。`team.md` の「同じテストが二度目に落ちたら原因を直すまで次へ進まない」と、`project.md` の「不具合を直すときは再現するテストを同じコミットに」に当たるかは指揮役が判断する。

## 性能の測り方（前の Intent からの持ち越し）

**K-59 `dslMixed` の `memory.peak` は、ページキャッシュを含むコンテナの最大で測っている**

- 確かめた事実:
  - 測り方（`perf/README.md` の「(b) 大きな入力のヒープ」）: 使い捨てのアプリを作り直してから `dslMixed` を流し、`docker exec mastersmith-perf-app-1 cat /sys/fs/cgroup/memory.peak /sys/fs/cgroup/memory.max` で読み、`peak / max` が 0.9 未満を合格とする。
  - 同じ README の `--storage` の行と 150 行は、`memory.current` がページキャッシュを含み、止まるかどうかは `memory.stat` の `anon` と `OOMKilled` で見ると書く。`--storage` は `anon` と `file` を回ごとに記録するが、`dslMixed` の手順は `memory.peak` だけを読む。
  - JVM は `-XX:MaxRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError`（`Dockerfile` の ENTRYPOINT）、コンテナの上限は `compose.yaml` の `mem_limit: ${MASTERSMITH_CONTAINER_MEMORY:-2g}`。
  - 前の Intent の記録: 94.4%・取り直し 93.1%、peak の時の内訳は `anon` 68.3%・回収できるページキャッシュ 23.7%。物差しを `anon` に替えると Met になるが、目標の測り方を後から緩めることになるため選ばず持ち越した（`project.md` の学び）。
- 今回の Intent への意味: cgroup v2 の `memory.peak` は全体（`anon` と `file` など）の最大で、`anon` だけの最大を直接読む値は無い。`anon` の最大を物差しにするなら、`dslMixed` の間に `memory.stat` を短い間隔で読み続ける手順が要る（`--storage` と同じ形）。物差しを変えることは目標の測り方の変更に当たり、`project.md` の「目標を緩めて満たしたことにはしない」により、要件定義で依頼者が明示に決める論点（変える理由・前の値との並べ方を記録する）。測り方を変えずにページキャッシュを減らす（H2 のファイルの読み書き・上限の見直し）方向もある。

## Sources

- 開発担当の調査の Test Coverage・Code Quality Indicators・Technical Debt Signals・K-53・K-57〜K-59。
- アーキテクトの確かめ直し（境界テストの数、`packagesJudgedByTotal`、`admin_flag` のテストのファイル数、テストの行）。
