# Code Generation Plan — U3 利用者の管理の API（u3-user-admin-api）

U3 のコード生成の計画を示す。U3 は service の単位で、新しい機能のパッケージ `useradmin` に管理者だけが使う7つの API（一覧 GET `/api/admin/users`、氏名と言語の変更 PUT `/api/admin/users/{userId}/profile`、5つの操作 POST `/api/admin/users/{userId}/grant-admin`・`revoke-admin`・`suspend`・`resume`・`reset-login-failures`）を作り、既存の `user`・`auth`・`audit` に口と値を足す。設計は1つの単位として通したため、この計画1つで2つの Bolt を書く（`project.md` の学び「大きい単位は設計を1つで通し、コード生成の計画で Bolt ごとに分ける」）。

- **B3 一覧と氏名・言語の変更（U3 前半）**: `useradmin` の骨組み、一覧・検索・ページ送り、氏名と言語の変更（Step 1〜16）。
- **B4 管理の操作と最後の管理者の保護（U3 後半）**: 5つの操作、最後の管理者の保護、監査、上限切れの扱い、既存の経路の上限切れの漏えいの直し（Step 17〜43）。

B3 を `develop` に統合し、CI を確かめてから B4 を始める（3節）。

この計画の中のパスは、特に断らない限りリポジトリのルートからの相対パスで、記録のディレクトリは `aidlc/spaces/default/intents/260930-user-admin/`（以下「記録」）とする。Java のクラスは `backend/src/main/java/cherry/mastersmith/`（テストは `backend/src/test/java/cherry/mastersmith/`）の下を、`cherry.mastersmith` からのパッケージ名で書く。

## 1. 入力にした設計

| 文書（記録の下） | 使うところ |
|---|---|
| `construction/u3-user-admin-api/functional-design/functional-spec.md`・`rules.md`・`entities.md`・`traceability.json` | 状態の移り変わり（1節）、手順 2.1〜2.10、判定の順と応答の表（3節）、失敗の場合（4節）、B3・B4 の分け方の目安（8.1）、申し送り（8.2）、上流との差 D1〜D13（C8 より D6〜D9・D13 を優先、R-06）、決まり BR1.1〜BR7.6、値の型、承認の場の決定 R-01〜R-07 |
| `construction/u3-user-admin-api/nfr-requirements/` の6つの文書 | NFR1.1〜NFR1.5・NFR3.1〜NFR3.4・NFR4.1〜NFR4.5・NFR5.1〜NFR5.11・NFR6.1〜NFR6.3・NFR8.1・NFR9.1〜NFR9.8・NFR10.1・NFR10.2・NFR11.1・NFR11.2、残る危険 R1〜R3、承認の場の決定 R-01〜R-06 |
| `construction/u3-user-admin-api/nfr-design/` の7つの文書 | 試しのコードの記録（`reliability-design.md` 1節、H2 2.4.240・Hibernate 7.4.5）、最後の管理者の保護の作り（2節）、上限切れを repository の中で受ける形（5.2、ND-1）、B4 の結合テストの表（5.3）、待ち合わせの口（6節）、伏せ字の型の経路（`security-design.md` 4節）、検索の比べ方 `ilike`（6.2、SD-4）、例外とログ（7節・7.1〜7.3、E1〜E4）、一覧の問い合わせ（`performance-design.md` 2節、索引を足さない）、ログの形（`observability-design.md` 3節）、部品の一覧（`logical-components.md`）、承認の場の決定 |
| `construction/u3-user-admin-api/infrastructure-design/infrastructure-specification.md`・`cicd-pipeline.md`・`monitoring-design.md` と `construction/infrastructure-design/gate-decisions.md` | 書き込みの問い合わせの上限切れの中央の手当て（6.2、10節の I-D1〜I-D4）、検査の段・カバレッジ・負荷の試験・E2E・統合（`cicd-pipeline.md` 1〜6節）、README に足すこと（9節）、承認の場の決定 R-01〜R-04 と書き手の3点 |
| `inception/contract-design/contract-summary.md` の共通の決まりと C1・C2・C3・C6・C8・エラーの code の一覧 | 口の形・応答・監査の出来事・code |
| `inception/delivery-planning/bolt-plan.md` の B3・B4 と共通の完了の条件 | 完了の条件、squash の統合 |
| `inception/units-generation/unit-of-work.md`（U3）・`unit-of-work-story-map.md` | 単位の境界、作らないもの、ストーリーの受け持ち（US1.1・US2.1・US3.1・US4.1・US5.1 の主、US3.2 の従） |
| `inception/requirements-analysis/requirements.md` の FR1〜FR8・NFR1〜NFR11 と `inception/user-stories/stories.md` | 要件と受け入れ基準 |
| 前の単位の記録（`construction/u1-user-suspension/code-generation/`・`u2-shared-paging/code-generation/`・`u4-admin-forbidden-ui/code-generation/`） | 2.2 の申し送り |
| 決まり `aidlc/spaces/default/memory/team.md`・`project.md` | 作業の場・統合・コミット、Testing Posture（管理者の印の変更・ロックの解除・最後の管理者の保護・管理の API の認可・要求の改ざん・管理の操作の監査・利用者の管理の漏えい、`packagesJudgedByTotal`）、Code Style（結果の型・ProblemTypes と Catalog・境界テスト・testsupport・伏せ字の `toString`）、Forbidden・Mandated |

## 2. 承認の場の決定と、この計画での読み方

### 2.1 承認の場の決定（監査ログと各文書の「承認の場の決定」の節から洗い出したもの）

`project.md` の学び（計画の前に承認の場の決定を洗い出す）に従い、監査ログ `audit/sakura-local-4e42a93f87ce.md` の `GATE_REJECTED`（Request Changes の理由）・`GATE_APPROVED` と、各文書の終わりの「承認の場の決定」の節、`gate-decisions.md`、NFR 設計の2回目のレビューの記録（`.aidlc-reviews/nfr-design/units/u3-user-admin-api/956a484ea854c0bc/1.json`）を読んだ。U3・B3・B4 に関わるものと、この計画での扱いは次のとおり。

| 段 | 決定・指摘 | この計画での扱い | 手順 |
|---|---|---|---|
| 機能設計（R-01） | 検索の文字と氏名は controller から UserRepository の口まで伏せ字の型のまま渡す。文字列にするのは record の中と SpEL の中だけ | `SearchText`・`ProfileCommand`・`ProfileUpdate` の経路で作り、レビューで確かめる | Step 4・6・8・10・14 |
| 機能設計（R-02） | 上限切れは業務処理が `setRollbackOnly()` を明示的に付けて巻き戻す。BR3.1 の根拠と切り替え先 | 作る。切り替え先へは切り替えない（NFR 設計の確かめ 1・2 が通った） | Step 30〜32 |
| 機能設計（R-03） | 待ち合わせの口で止める時間は 3000 ミリ秒より短くする | テストの手伝いの上限を決めて作る（7節・`unit-test-instructions.md` 5節） | Step 32 |
| 機能設計（R-05） | 戻せる（resettable）は「失敗回数 1 以上、または解除の予定の時刻がある」 | `LockView` と失敗回数を戻す1段目で同じ定義にする | Step 4・30 |
| 機能設計（R-06） | C8 より機能設計 9節の D6〜D9・D13 を優先する | 口の形は D6〜D9・D13 のとおり（4節） | Step 8・30 |
| 機能設計（R-07） | 検索の小文字化は `Locale.ROOT` | `SearchText` の中で行う | Step 4 |
| 機能設計（受け入れ） | D6（失敗回数を戻す口を2段に）・D8（`findAdminSummary`）・BR3.6（ログインの判定にも待ち合わせの口）・BR1.4（page と q がどちらも誤りなら page）・BR2.7（氏名と言語は 400 を 404 より先）・D1（`useradmin.web` から `access.domain` へ1本） | そのとおり作る | Step 8・10・30・33・37 |
| 機能設計（申し送り） | B3・B4 の分け方、最後のページより後と全体 0 件の一覧の結合テスト、2つ目の操作が排他の待ちに入ったことの確かめ方 | 3節・Step 11・Step 32（`H2SessionWaits` で実行中の文を見る） | Step 11・32 |
| NFR 要件（R-01〜R-06） | NFR10 は条件つき（索引を足さなければ当たらない）、接続プール上限 10 の場面、警報の書き方、k6 の回数と正とする値、列の名前、5つの操作の台本の受け入れの条件 | 索引と移行を足さない。負荷の試験の台本に入れる | Step 39 |
| NFR 設計（Request Changes の決定 ②） | ログイン・招待・登録の完了の上限切れも repository の中で受け、行の値をログに出さない形を B4 で入れる | E1〜E4 の直し | Step 19〜25 |
| NFR 設計（R-02〜R-04 と受け入れ） | 本番の業務処理が Busy で巻き戻しの印を付ける単体テスト、受ける例外の範囲（型と誤りの番号）、TRACE と INFO の両方で確かめ続ける。ND-1・R5（`İ`・`ß`）・BUSY のログ2行を受け入れ | 作る | Step 19〜25・31・32・36 |
| NFR 設計の承認（レビューの R-01 の選択肢 (2)、`infrastructure-specification.md` 10節の I-D1） | 書き込みの問い合わせの上限切れは、`TraceAspect` の出力と `GlobalExceptionHandler` の ERROR の側で、原因をつながず型の名前だけにする中央の手当てに寄せる。`common.observability`・`common.error.web` を一覧から外して下限を満たす。TRACE と INFO の両方の漏えいのテスト | 作る | Step 23〜25・38 |
| NFR 設計の2回目のレビュー（R-02、基盤の設計の書き手の点 1） | E2〜E4 を EntityManager に移しても、メソッドの名前・引数・戻り値を今と同じにし、`InvitationService` の既存の単体テストの差し替えを壊さない | Spring Data の独自の断片で同じ署名のまま移す | Step 21・22 |
| NFR 設計（申し送り） | SD-4（`ilike ... escape` と SpEL を Spring Data の問い合わせが受け付けるか）を B3 の最初の手順で確かめる | Step 3 | Step 3 |
| 基盤の設計（Q1 A、I-D3） | B4 は統合の前に E2E を流す。B3 は画面・認証の経路に触れなければ流さない | Step 15・42 | Step 15・42 |
| 基盤の設計（R-02 の申し送りと書き手の点 3） | 今の verify の時間の基準値を測り、B4 の上限切れのテストで延びる時間と CI の 60 分を見積もる | Step 1・17・41、4.6 | Step 1・17・41 |
| 基盤の設計（R-03 の申し送り） | B3 の変更が画面・認証の経路に触れないことを確かめる項目と、触れたら E2E を流す条件 | Step 14・15 | Step 14・15 |
| 基盤の設計（U5 R-02 の全単位の決定） | E2E の後は json の報告から結果を記録してから報告を消し、消したことと共有していないことを記録する。B3 で流すときも同じ扱い | Step 15・42 | Step 15・42 |
| 基盤の設計（R-01、I-D4） | 戻し先の版は停止の列を知らない。戻す前に停止中の利用者を確かめる手順は deployment-pipeline で決める | この段では作らない。「Build and Test に引き継ぐこと」に写す | — |

### 2.2 前の単位からの申し送り

| 出どころ | 申し送り | この計画での扱い | 手順 |
|---|---|---|---|
| U1 のレビュー R-03（`u1-user-suspension/code-generation/code-summary.md` 8節・11節） | `setSuspended` は `clearAutomatically = true` のため、呼ぶ前に読み込んだエンティティが切り離される。C1 の口を呼んだ後に先に読み込んだエンティティを使わないことの確かめを B4 の計画の必須の項目にする | 必須の項目にする: (a) `useradmin` は JPA のエンティティに依存しない（境界テスト）、(b) 排他の結果も要約も投影の値で持つ、(c) 止める操作の後に印・氏名・言語が変わっていないことと停止とトークンの無効化が確定することを結合テストで確かめる、(d) レビューで確かめる | Step 28・30・32・37・40 |
| U1 の C1 の口 | `UserAccountService#setSuspended(long, boolean)`（MANDATORY、0 行なら `IllegalStateException`）、`RefreshTokenRevocationService#revokeAllRefreshTokens(long)`（MANDATORY、`RevokeAllResult`）。`isSuspended` は使わない（FS の D11） | 止める操作で同じトランザクションに両方を呼び、対象がいると排他の口で確かめてから呼ぶ | Step 30 |
| U2（`u2-shared-paging/code-generation/code-summary.md` 9節） | 利用者の一覧の最後のページより後と全体 0 件の結合テスト | B3 の一覧の結合テストに入れる | Step 11 |
| U2 | page の文字列と TRACE（受け入れた残る危険 R3）。出うる範囲を確かめる | page を文字列で受ける形は招待の一覧と同じ。出うるのは `UserAdminController#list`・`UserAdminService#list` の引数だけであることをソースで確かめて記録する | Step 14 |
| U2（レビューの R-03） | 8KB を超える要求の行は Tomcat が `text/html` の 400 を返す。利用者の一覧の入口で同じ挙動になることを確かめる | B3 の一覧の結合テストに1件入れる | Step 11 |
| U2 | 共通のページ送りは `common.paging.Paging`（`parsePage`・`offsetOf`・`PAGE_SIZE`） | そのまま使う。`useradmin` にページ送りの計算を重ねて書かない | Step 8 |
| U4（`u4-admin-forbidden-ui/code-generation/generation-notes.md` の終わり） | 管理の API の 403 が code `ACCESS_DENIED` で返ることを、新しい管理の API でも保つ | 認可の入口の 403 と確かめ直しの 403 の両方で code `ACCESS_DENIED` を確かめる | Step 11・34 |

### 2.3 この計画での読み方

- **層の順（test-after）**: Testing Contract の `ordering` のとおり、層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、通ってから次の層へ進む。B3・B4 のそれぞれの中で「ドメイン → DB アクセス → 業務処理 → web → 構造の検査 → 漏えい → 1コマンドの検査 → 記録」の順にする。画面の層は無い（U5）。
- **B4 の前半に既存の経路の直しを置く**: 既存のログイン・招待・登録の完了と書き込みの問い合わせの上限切れの漏えいの直し（E1〜E4 と中央の手当て）は、U3 の5つの操作と独立して作れ、`project.md` の Mandated（不具合を再現するテストを同じコミットに含める）のため1つのコミットにまとめたい。そこで B4 の最初の区切り（Step 19〜25）に、その中で「判定の部品 → DB アクセス → 中央の手当て → 漏えいの結合テスト」の順で置く。U3 の後半の層の順はその後（Step 26〜36）。
- **SD-4 は B3 の最初に**: 層の順の例外として、一覧の検索の問い合わせ（`ilike ... escape` と SpEL）を Step 3 で先に書いて確かめる（申し送り）。通らなければ native の問い合わせに切り替え、差を記録する。
- **既存の ArchUnit を緩めない**: 既存の `ArchitectureTest` と機能ごとの境界テストは変えない。新しい `UserAdminBoundaryArchitectureTest` を足す（`team.md` の Code Style、NFR11.1）。
- **口の形**: C8 は書き換えず、機能設計 9節の D6〜D9・D13 を優先する（R-06）。名前の細部はこの計画で決める（4節、8節）。
- **既存のテストの手直し**: 監査の種類・理由の列挙を足しても、既存の `AuditEventTest#namesFitIntoTheColumns` は値を足すだけで通る。`AuditSecretLeakIT` の列の一覧は U3 が列を足さないため変えない（FS の 8.2 の申し送りを確かめた結果）。

## 3. 作業の場とコミットの区切り

### 3.1 作業のブランチと統合の形

| Bolt | ブランチ | 作る時点 | 統合 |
|---|---|---|---|
| B3 | `develop` から `feature/260930-user-admin-b3` | この計画の Plan Approval と記録のコミット R1 の後（Step 1） | `develop` へ squash の1コミット。件名の案「B3 利用者の一覧と氏名・言語の変更（U3 前半）: useradmin の骨組み・一覧と検索・ページ送り・氏名と言語の変更」 |
| B4 | B3 の統合（と CI の確かめ）の後の `develop` から `feature/260930-user-admin-b4` | Step 17 | `develop` へ squash の1コミット。件名の案「B4 利用者の管理の操作と最後の管理者の保護（U3 後半）: 5つの操作・監査・上限切れ・既存の経路の上限切れの漏えいの直し」 |

- worktree は使わない（`team.md` の Way of Working）。ブランチの作成・コミット・統合・ブランチの削除は、それぞれ依頼者の承認を得てから行う。`origin` への `git push` は依頼者が行う。AI はプッシュしない。
- サブモジュールの固定先は変えないため、fast-forward の例外には当たらない。
- **統合の前の関門**（どちらの Bolt も）: colima が動いていることを確かめ、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡し、`caffeinate -i` で包んで `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を通し、`./gradlew osvScan --rerun-tasks` を通す（Step 15・41）。B4 は加えて E2E（Step 42）。B3 は画面・認証の経路に触れたときだけ E2E を流す（Step 14・15）。
- **統合の後**: 依頼者がプッシュした後の CI の `verify` を確かめる。失敗したら次の Bolt に進む前に `team.md` の「不安定なテストと CI の失敗」の決まりで扱う（Step 16・43）。

### 3.2 記録の扱い（B2 と同じ形）

この段の記録（記録の `construction/u3-user-admin-api/code-generation/` の下・`aidlc-state.md`・監査ログ）は、作業ブランチの上で **記録だけのコミット**（`aidlc/` の下だけを含み、アプリのソースを含まない）にする。統合のときは squash の範囲から `aidlc/` を外し、記録は `develop` の上で別の記録のコミットにする。記録だけのコミットは、依頼者の承認を得て次の時点で作る。

| 区切り | 中身 | 時点 |
|---|---|---|
| R1 | この計画・`unit-test-instructions.md`・Plan Approval の記録（`code-generation-questions.md`・`aidlc-state.md`・監査ログ） | Plan Approval の後、Step 1 でブランチを作った直後（B3 のブランチの最初のコミット） |
| R2 | B3 の生成の記録（`generation-notes.md` の B3 の節、`code-summary.md`・`source-manifest.json`・`traceability.json` の B3 までの版、計画の Step 1〜14 のチェック） | Step 16 の最初（C1〜C3 の後） |
| R3 | B3 の関門の記録（Step 15 の実測・E2E を流したときの結果と報告を消したこと）と、その時点の `aidlc-state.md`・監査ログ | Step 16 の統合の前 |
| R4 | B4 の生成の記録（`generation-notes.md` の B4 の節、`code-summary.md`・`source-manifest.json`・`traceability.json` の仕上げ、計画の Step 17〜40 のチェック） | Step 43 の最初（C4〜C8 の後） |
| R5 | B4 の関門の記録（Step 41・42 の実測・E2E の結果・報告を消したこと）と、その時点の `aidlc-state.md`・監査ログ | Step 43 の統合の前 |

- 記録だけのコミットに `aidlc/` の外のファイルが入っていないことを `git show --stat` で確かめる。アプリのソースのコミット（C1〜C8）には `aidlc/` の下を入れない。
- B3 の統合の後の `develop` の記録のコミットは、R1〜R3 と統合の後に書き足した記録（統合のハッシュ・ブランチを消したこと）を含む。B4 も同じ（R4・R5 と統合の後の記録）。
- **squash の範囲から `aidlc/` を外す手順**（どれも依頼者の承認を得てから行う。B3・B4 で同じ）:
  1. 統合の前に、作業フォルダに未コミットのアプリのソースが無いこと、`aidlc/` の未コミットの変更は R3（R5）に入れたことを `git status` で確かめる。
  2. `develop` に移り、`git merge --squash feature/260930-user-admin-b3`（B4 は `-b4`）で取り込み、`git restore --staged --worktree --source=HEAD -- aidlc/` で `aidlc/` の下の変更をこの squash から外す。`git diff --cached --stat -- aidlc/` が空で、`git diff --cached --stat` が `source-manifest.json` の一覧と一致することを確かめてから1コミットにする。
  3. `git checkout feature/260930-user-admin-b3 -- aidlc/spaces/default/intents/260930-user-admin`（B4 は `-b4`）で作業ブランチの先頭の記録を `develop` の作業フォルダに戻し、統合の後の記録を書き足して、`develop` の上の記録のコミット（件名の案「B3 のコード生成の記録（計画・承認・まとめ・網羅の記録・関門・統合 <ハッシュ>）」、B4 は「B4 の…」）にする。`git diff --cached --stat` が `aidlc/` の下だけであることを確かめる。
  4. `git diff feature/260930-user-admin-b3 develop -- aidlc/`（B4 は `-b4`）が統合の後に書き足した分だけであることを確かめる。
  5. 依頼者の承認を得て作業ブランチを消す。

### 3.3 アプリのソースのコミットの区切り（提案）

生成の担当はコミットしない。各 Bolt の生成の後に、依頼者の承認を得て、作業ブランチの上で次の区切りでコミットする（`project.md` の Change Control）。メッセージは日本語で、末尾に `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` を付ける。途中のコミットで verify を流し直すことはしない。通ることを確かめるのは、すべての区切りを含む作業ブランチ（Step 15・41）。

| 区切り | Bolt | 中身 | 手順 |
|---|---|---|---|
| C1 | B3 | 一覧の検索の問い合わせと値の型（`user.domain` の `SearchText`・`ProfileUpdate`・`ProfileValidation`、`auth.domain.LockView`、`useradmin.domain.UserAdminProblemTypes`、`user.repository` の問い合わせと `UserAdminRow`、`LoginAttemptStateRepository#findBySubjectIds`）とテスト | Step 3〜7 |
| C2 | B3 | 業務処理（`user.service` の C8 の口の一覧と氏名・言語、`auth.service.LockAdministrationService#lockViewsOf`、`useradmin.service`）とテスト | Step 8・9 |
| C3 | B3 | web（`useradmin.web`）・漏えいのテスト・境界テスト・README | Step 10〜14 |
| C4 | B4 | 既存の経路の上限切れの漏えいの直し（`common.persistence`、E1〜E4、`TraceAspect`・`GlobalExceptionHandler` の中央の手当て）と、それを再現するテスト（同じコミット、`project.md` の Mandated） | Step 19〜25 |
| C5 | B4 | 拒否の判定と監査のドメイン（`useradmin.domain` の操作の型・判定・出来事・code、`audit.domain` の種類・理由・写し）とテスト | Step 26・27 |
| C6 | B4 | 排他と5つの操作（`user.repository`・`user.service`・`auth.repository`・`auth.service`・`useradmin.service`・`audit.service`）とテスト | Step 28〜32 |
| C7 | B4 | web の5つの API・監査と改ざんと漏えいの結合テスト | Step 33〜36 |
| C8 | B4 | 境界テスト・`packagesJudgedByTotal`・README・負荷の試験の台本と手順書 | Step 37〜39 |

件名の案は Step 16・43 で示す（例 C4「B4 既存の経路の排他の待ちの上限切れで行の値をログに出さない（E1〜E4・書き込みの問い合わせ）」）。

## 4. 作るもの・手を入れるもの

「一覧」の列は `backend/build.gradle.kts` の `packagesJudgedByTotal`（2026-10-02 の時点で 9 パッケージ: `access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.error.web`・`common.health`・`common.i18n.domain`・`common.observability`・`common.web`）に入っているかを示す。

### 4.1 B3 の本体（`src/main`）

| パッケージ | 部品 | 新しい・手を入れる | 中身 | 一覧 |
|---|---|---|---|---|
| `user.domain` | `SearchText` | 新しい（record） | 要求の q の生の値を包む。`isBlank()`（前後の Unicode の White_Space を除いた後が空か。範囲は `DisplayName.strip` と同じ）、`isTooLong()`（除いた後のコードポイントが 254 を超えるか）、`likePattern()`（除いた後を `Locale.ROOT` で小文字にし、`\`・`%`・`_` を `\` でエスケープし、前後に `%` を付けた値を `RedactedText` で返す）。`toString` は値を伏せる。等しさで値をログに出さない | 外 |
| `user.domain` | `ProfileUpdate` | 新しい（record） | 前後の空白を除いた氏名と `Language`。作れるのは決まりに合う値だけ。`toString` で氏名を伏せる（`Preferences` と同じ形） | 外 |
| `user.domain` | `ProfileValidation` | 新しい（純粋な関数） | `validate(displayName, language)`（既存の `DisplayName.check`・`Language.check` を使い、項目の順 displayName・language で誤りを集める）と `toProfileUpdate`。`PreferencesValidation` と同じ形 | 外 |
| `user.repository` | `UserAdminRow` | 新しい（record） | 一覧と要約の投影（利用者 ID・メールアドレス・氏名・言語・印・停止・登録した日時）。`toString` でメールアドレスと氏名を伏せる（8節の D-1、9節の Q-A） | 外 |
| `user.repository` | `UserRepository` | 手を入れる | 検索なしの行 `findAdminRows(Pageable)`、検索ありの行 `findAdminRowsBySearch(RedactedText, Pageable)`、検索ありの件数 `countBySearch(RedactedText)`（どれも投影でパスワードのハッシュの列を読まない。並びは `createdAt`・`userId` の昇順。比べ方は `u.email ilike :#{#pattern.value()} escape '\'` と `u.displayName` の同じ条件の OR）、氏名と言語の更新 `updateProfile(long, ProfileUpdate)`（2列だけ、`@Modifying(clearAutomatically = true)`、SpEL で取り出す）。検索なしの件数は既存の `count()` | 外 |
| `user.service` | `UserAdminSummary`・`UserAdminSlice`・`ProfileCommand`・`ProfileUpdateResult` | 新しい | 要約（ハッシュ値なし、`toString` でメールアドレスと氏名を伏せる）、1ページの結果（items・total）、氏名と言語の入力（`toString` で氏名を伏せる、`PreferencesCommand` と同じ形）、結果（`Updated`・`NotFound`・`Invalid(errors)`） | 外 |
| `user.service` | `UserAccountService` | 手を入れる | `findAdminPage(SearchText, long offset, int limit)`（`@Transactional(readOnly = true)`。search が null か `isBlank()` なら検索なし。読み始めの位置が全体の件数以上なら行を読まない）、`updateProfile(long, ProfileCommand)`（MANDATORY。先に検証し、誤りは DB に触れずに `Invalid`、更新した行が 0 なら `NotFound`） | 外 |
| `auth.domain` | `LockView` | 新しい（record と純粋な関数） | `locked`・`lockedUntil`・`resettable` の3つ。`LockView.of(LoginAttemptState の値 or 無し, now)`（行が無ければ `(false, null, false)`、locked は「解除の予定の時刻があり now < その時刻」、resettable は「失敗回数 1 以上、または解除の予定の時刻がある」）。失敗回数そのものは持たない | 外 |
| `auth.repository` | `LoginAttemptStateRepository` | 手を入れる | `findBySubjectIds(Collection<Long>)`（排他なし、`subjectId in :ids` の1回。正の ID だけを渡す）。既存のメソッドは変えない | 外 |
| `auth.service` | `LockAdministrationService` | 新しい | C8 の Authentication の口。`lockViewsOf(Collection<Long>)`（読み取りだけ、注入した `Clock`、`Map<Long, LockView>`） | 外 |
| `useradmin.domain` | `UserAdminProblemTypes` | 新しい | B3 では 404 `USER_NOT_FOUND`（ja・en の説明文、対象の利用者の値を載せない）と `all()` | 新しい（下限の対象） |
| `useradmin.service` | `UserAdminService` | 新しい | `list(long actorId, String page, SearchText q)`（`Paging.parsePage` → q の長さ → 読み取りだけで `findAdminPage` と `lockViewsOf` → self）、`updateProfile(long userId, ProfileCommand)`（`TransactionTemplate` の1つのトランザクション、排他しない、確かめ直ししない、監査を出さない） | 新しい |
| `useradmin.service` | `UserAdminListResult`・`UserAdminPage`・`UserAdminEntry`・`UserAdminProblemTypeCatalog` | 新しい | 一覧の結果（`Listed`・`InvalidPage`・`Invalid(errors)`）、1ページ（items・page・size・total）、1行（要約・`LockView`・self、`toString` で伏せる）、code の起動時の一覧 | 新しい |
| `useradmin.web` | `UserAdminController` | 新しい | `GET /api/admin/users`（`@RequestParam page` は文字列、`q` は `SearchText`）、`PUT /api/admin/users/{userId}/profile`（`userId` は `long`）。結果の型を場合を尽くす `switch` で応答か `BusinessException` に変える | 新しい |
| `useradmin.web` | `AdminUser`・`AdminUserPage`・`ProfileRequest` | 新しい（record） | 応答の 11 項目と self／ページ（items・page・size 20・total）／要求（displayName・language の2つだけ。知らない項目は読み捨てる）。どれも `toString` でメールアドレスと氏名を伏せる | 新しい |
| `useradmin.web` | `SearchTextConverter`・`UserAdminWebConfig` | 新しい | `Converter<String, SearchText>`（包むだけ、検証・ログなし、例外を出さない）。Bean にせず、`@Configuration(proxyBeanMethods = false)` の `WebMvcConfigurer#addFormatters` で `new` して登録する（`DslWebConfig` と同じ形、BR1.3） | 新しい |
| `useradmin.web` | `UserAdminRequestContextResolver`・`UserAdminFieldErrors` | 新しい | 操作した人の利用者 ID と送り手の情報（`InvitationRequestContextResolver` と同じ形）／項目ごとの誤りを載せた 400（`invitation.web.FieldErrors` と同じ形。機能の間で使い回さない） | 新しい |
| （文書） | `README.md` | 手を入れる | 「利用者の管理の API（Intent 260930-user-admin の U3）」の節を足し、B3 の2つの API・検索の決まり・q の上限・既知の差（`İ`・`ß`）を書く（8節の D-5） | — |

手を入れない: `common.*`（`common.paging` は使うだけ）、`access.*`（B3 では `access.domain` も使わない）、`auth.web`・`auth.service.LoginService` などの認証の経路、`config`、`frontend/`、`application.yaml`・`compose.yaml`・`Dockerfile`・`.env.example`、`backend/build.gradle.kts`、`docker/monitoring/`。新しい依存・設定・移行・指標・警報は足さない。

### 4.2 B3 のテスト（`src/test`）

| 置き場 | 部品 | 新しい・手を入れる |
|---|---|---|
| `user/domain` | `SearchTextTest`（性質ベースのテストを含む）・`ProfileValidationTest` | 新しい |
| `auth/domain` | `LockViewTest`（性質ベースのテストを含む） | 新しい |
| `useradmin/domain` | `UserAdminProblemTypesTest` | 新しい |
| `user/repository` | `UserAdminQueriesIT`（SD-4・並び・ページ・検索・文字どおり・既知の差・読む列・`updateProfile`） | 新しい |
| `auth/repository` | `LoginAttemptStateRepositoryIT` | 手を入れる（`findBySubjectIds` の1〜2件） |
| `user/service` | `UserAccountServiceTest`（手を入れる）・`UserAdminAccountIT`（新しい） | — |
| `auth/service` | `LockAdministrationServiceTest` | 新しい |
| `useradmin/service` | `UserAdminServiceTest`・`UserAdminProblemTypeCatalogTest` | 新しい |
| `useradmin/web` | `SearchTextConverterTest`・`UserAdminWebTypesTest`（応答と要求の `toString`）・`UserAdminRequestContextResolverTest` | 新しい |
| `useradmin/web` | `UserAdminListApiIT`・`UserAdminListQueryCountIT`・`UserAdminProfileApiIT`・`UserAdminSecretLeakIT` | 新しい |
| `useradmin` | `UserAdminBoundaryArchitectureTest` | 新しい |
| `useradmin/testsupport` | `UserAdminApi`（一覧と氏名と言語の要求の手伝い）・`UserAdminFixtures`（利用者・ロックの状態の行を用意する手伝い。メールアドレスは `example.com` だけ） | 新しい |

### 4.3 B4 の本体（`src/main`）

| パッケージ | 部品 | 新しい・手を入れる | 中身 | 一覧 |
|---|---|---|---|---|
| `common.persistence` | `RowLockFailures` | 新しい（パッケージも新しい） | `isLockFailure(Throwable)`: 連なりの中に JPA の `LockTimeoutException`・`PessimisticLockException`・`QueryTimeoutException` があるか、`SQLException` の誤りの番号が 50200・40001 か SQLState が HYT00・40001 のものがあれば真（Spring の `CannotAcquireLockException` などに変換された後の連なりも見る）。`warn(Logger, String lockKind, Throwable)`: WARN にキーと値で `lockKind` と `exceptionClass`（クラスの名前）だけを出し、例外そのものは渡さない | 新しい（下限の対象） |
| `common.persistence` | `RowLockUnavailableException` | 新しい | 決まった文だけを持ち、原因をつながない実行時の例外（`DataIntegrityViolationException` の系統にしない）。排他の種類（`lockKind`）だけを持ってよい | 新しい |
| `common.persistence` | `RowLockAttempt<T>` | 新しい（sealed interface と record） | `Acquired<T>(T value)`・`Busy<T>()`。repository の排他の口が返す | 新しい |
| `common.observability` | `LockFailureSafeTraceInterceptor`・`TraceAspect` | 新しい・手を入れる | Spring の `CustomizableTraceInterceptor` を継ぎ、例外が `RowLockFailures.isLockFailure` に当たるときは、`$[exception]` の置き換えと書き出しで元の例外を渡さず、`exceptionClass` のクラスの名前だけにする（スタックトレースも元の連なりを出さない）。`TraceAspect#createInterceptor` がこれを作る。設定の項目と既定値は変えない（I-D1） | **一覧にある → 外す** |
| `common.error.web` | `GlobalExceptionHandler` | 手を入れる | 想定外の誤りの ERROR で、例外が `RowLockFailures.isLockFailure` に当たるときは `setCause` をせず、`exceptionClass` を足す。応答（500 `INTERNAL_ERROR`）・件数（ERROR は1件）は変えない | **一覧にある → 外す** |
| `auth.repository` | `LoginAttemptStateRepository` | 手を入れる | E1: `lockForUpdate` の本体で `PersistenceException` を受け、`RowLockFailures.isLockFailure` なら `warn`（`LOGIN_ATTEMPT_ROW`）の後に `RowLockUnavailableException` を投げる（ほかはそのまま投げる）。`tryLockForUpdate(long)`（同じ問い合わせと判定を共有し、`RowLockAttempt<Optional<LoginAttemptState>>` を返す） | 外 |
| `invitation.repository` | `InvitationLockQueries`・`InvitationLockQueriesImpl`・`InvitationRepository` | 新しい・手を入れる | E2〜E4: Spring Data の独自の断片として、`findByEmailAndStateForUpdate(InvitationEmail, InvitationState)`・`findByIdForUpdate(long)`・`findByTokenHashForUpdate(byte[])` を **今と同じ名前・引数・戻り値** で EntityManager を直接使う実装に移す（`PESSIMISTIC_WRITE`、上限 3000）。上限切れは E1 と同じく受けて `RowLockUnavailableException`（`INVITATION_ROW`）。`InvitationRepository` は断片を継ぎ、3つの `@Lock` の宣言を消す。`findPendingByEmailForUpdate` の既定のメソッドと呼び出し元は変えない | 外 |
| `user.repository` | `UserRowLockRepository` | 新しい | EntityManager を直接使う排他の口。`lockAdminRowsAndTarget(long targetUserId)`（`select u.userId from User u where u.adminFlag = true or u.userId = :target order by u.userId`、`PESSIMISTIC_WRITE`、上限 3000、ID だけを読む）と `lockUserRow(long)`。どちらも上限切れを本体の中で受け、`RowLockFailures.warn`（`ADMIN_ROWS`・`USER_ROW`）の後に `RowLockAttempt.Busy` を返す | 外 |
| `user.repository` | `UserRepository` | 手を入れる | `findAdminRow(long)`（投影）、`findActiveAdminIds()`（`adminFlag = true and suspended = false` の ID）、`updateAdminFlag(long, boolean)`（`@Modifying(clearAutomatically = true, flushAutomatically = true)`、1列だけ） | 外 |
| `user.service` | `AdminRowsLock`・`UserRowLock` と `UserAccountService` | 新しい・手を入れる | `lockAdminRowsInIdOrder(long)`（MANDATORY。排他の後に別の問い合わせで対象の要約と有効な管理者の ID の集合を読む。Busy はそのまま写す）、`lockUserRow(long)`（MANDATORY）、`findAdminSummary(long)`（排他しない、呼び出し元に入る）、`setAdmin(long, boolean)`（MANDATORY、0 行なら `IllegalStateException`） | 外 |
| `auth.service` | `LoginFailureResetPreparation`・`LockAdministrationService` | 新しい・手を入れる | `prepareFailureReset(long)`（MANDATORY。`tryLockForUpdate` → `Ready`・`NothingToReset`・`Busy`、排他の直後に待ち合わせの口）と `completeFailureReset(long)`（MANDATORY。既存の `update(id, 0, null)` の明示の更新1回、行を作らない） | 外 |
| `auth.service` | `LoginAttemptBarrier`・`NoOpLoginAttemptBarrier`・`LoginService` | 新しい・手を入れる | 待ち合わせの口 `afterLock(long userId)`（本番は何もしない）。`LoginService#decide` で実在の利用者の行を `lockForUpdate` で得た直後に呼ぶ（ダミーの行では呼ばない） | 外 |
| `useradmin.domain` | `AdminOperation`・`RejectionReason`・`OperationFacts`・`RejectionPolicy`・`UserAdminAuditFailure`・`UserAdminAuditEvent`・`UserAdminProblemTypes` | 新しい・手を入れる | 5つの操作の区分、拒否の理由（判定の順）、事実の組、判定の純粋な関数 `decide(operation, facts)`、監査の理由（理由5つ＋`NOT_ADMIN`）、監査の出来事（操作・操作した人・要求の利用者 ID・成否・理由・時刻・送り手。`toString` に個人に関する値が無い）、409 の code 5つ（SELF_OPERATION・TARGET_SUSPENDED・NO_CHANGE・LAST_ADMIN・BUSY） | 新しい |
| `useradmin.service` | `OperationResult`・`UserAdminBarrier`・`NoOpUserAdminBarrier`・`UserAdminService` | 新しい・手を入れる | 結果（`Done`・`Rejected(reason)`・`OperatorNotAdmin`・`Busy`）、待ち合わせの口 `beforeCount(AdminOperation, long targetUserId)`、5つの操作（`TransactionTemplate` の1つのトランザクション、FS の 2.2〜2.7 の順。Busy なら先に `status.setRollbackOnly()` を付け、その後に DB に触れずに `Busy` を返す） | 新しい |
| `useradmin.web` | `UserAdminController` | 手を入れる | 5つの POST（`/{userId}/grant-admin` など）。`OperationResult` を場合を尽くす `switch` で 204・404・409・403 `ACCESS_DENIED`（`access.domain.AccessProblemTypes`）に変える | 新しい |
| `audit.domain` | `AuditEventType`・`AuditFailureReason`・`AuditEventFactory` | 手を入れる | 種類5つ（`USER_ADMIN_GRANTED`・`USER_ADMIN_REVOKED`・`USER_SUSPENDED`・`USER_RESUMED`・`LOGIN_FAILURES_RESET`）、理由4つ（`SELF_OPERATION`・`TARGET_SUSPENDED`・`NO_CHANGE`・`LAST_ACTIVE_ADMIN`）、`from(UserAdminAuditEvent)`（操作と理由を場合を尽くす `switch` で写す。`enteredEmail` は空。招待の出来事の写し方と同じ形） | 外 |
| `audit.service` | `AuditEventListener` | 手を入れる | `onUserAdminAuditEvent`（`@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`、既存と同じ形） | 外 |
| （ビルド） | `backend/build.gradle.kts` | 手を入れる | `packagesJudgedByTotal` から `common.observability`・`common.error.web` を消し、説明文に B4 で外したことを足す（一覧を増やさない、除外を増やさない） | — |
| （文書） | `README.md` | 手を入れる | 「利用者の管理の API」の節に5つの操作・code・拒否の順を足す。「監査ログ（U4）」に種類5つと理由（`USER_NOT_FOUND`・`NOT_ADMIN`・`SELF_OPERATION`・`TARGET_SUSPENDED`・`NO_CHANGE`・`LAST_ACTIVE_ADMIN`）と残らない場合を足す。「既知の制約（同時の要求と接続プール）」に5つの操作も1件に2本使うことと、管理の操作とログインの失敗が重なると上限 30 を超えうることを足す（`infrastructure-specification.md` 9節） | — |
| （負荷の試験） | `perf/k6/scenarios.js`・`perf/README.md` | 手を入れる | 一覧 a〜d・氏名と言語・5つの操作・止める（悪い側）・接続プール（上限 30・上限 10）の場面と、試験用のデータの入れ方（8節の D-2、9節の Q-D） | — |

手を入れない: `access.*`（`access.domain` は使うだけ）、`common.security`・`config`・`common.web`・`common.error.domain`・`common.error.service`、`application.yaml`・`logback-spring.xml`・`compose.yaml`・`docker/perf/compose.yaml`・`Dockerfile`・`.env.example`、`docker/monitoring/`、`frontend/`。新しい依存・設定の項目・移行・指標・警報は足さない。排他の待ちの上限（3000 ミリ秒）は既存の定数にそろえ、設定にしない。

### 4.4 B4 のテスト（`src/test`）

| 置き場 | 部品 | 新しい・手を入れる |
|---|---|---|
| `common/persistence` | `RowLockFailuresTest`・`RowLockUnavailableExceptionTest` | 新しい |
| `auth/repository` | `LoginAttemptStateRepositoryTest`（EntityManager を差し替えた単体）・`LoginAttemptStateRepositoryIT`（手を入れる） | — |
| `invitation/repository` | `InvitationLockQueriesImplTest`（単体）・`InvitationRepositoryIT`（手を入れる） | — |
| `common/observability` | `LockFailureSafeTraceInterceptorTest` | 新しい |
| `common/error/web` | `GlobalExceptionHandlerTest` | 手を入れる |
| `auth/web` | `AuthLockTimeoutLeakIT`（ログインの E1・ダミーの行がすべて排他されたときの代わりの道・トークンの更新の書き込み） | 新しい |
| `invitation/web` | `InvitationLockTimeoutLeakIT`（招待 E2・送り直しと取り消し E3・登録の完了 E4） | 新しい |
| `user/web` | `MePreferencesLockTimeoutLeakIT`（表示の設定の保存の書き込み） | 新しい |
| `useradmin/domain` | `RejectionPolicyTest`（性質ベースのテストを含む）・`UserAdminAuditEventTest`・`UserAdminProblemTypesTest`（手を入れる） | — |
| `audit/domain` | `AuditEventFactoryTest`（手を入れる） | — |
| `user/repository` | `UserRowLockRepositoryTest`（単体）・`UserRowLockRepositoryIT`・`UserAdminQueriesIT`（手を入れる） | — |
| `user/service` | `UserAccountServiceTest`（手を入れる）・`UserAdminLockPortsIT`（新しい） | — |
| `auth/service` | `LockAdministrationServiceTest`・`LoginServiceTest`（手を入れる）・`FailureResetPortIT`（新しい） | — |
| `audit/service` | `AuditEventListenerTest`（手を入れる）・`UserAdminAuditIT`・`UserAdminAuditWriteFailureIT`（新しい） | — |
| `useradmin/service` | `UserAdminServiceTest`（手を入れる）・`UserAdminOperationsIT`・`UserAdminConcurrencyIT`・`ResetLoginConcurrencyIT`・`SuspendWhileLoginIT` | — |
| `useradmin/web` | `UserAdminOperationsApiIT`・`UserAdminResetLoginFailuresApiIT`・`UserAdminBusyApiIT`・`UserAdminMassAssignmentIT`（新しい）・`UserAdminSecretLeakIT`（手を入れる） | — |
| `useradmin` | `UserAdminBoundaryArchitectureTest` | 手を入れる |
| `useradmin/testsupport` | `TestUserAdminBarrier`・`RecordingTransactionManager`・`RowLockHolder`（別の接続で行を `FOR UPDATE` で持ち続ける手伝い） | 新しい |
| `auth/testsupport` | `TestLoginAttemptBarrier` | 新しい |

`RowLockHolder` は `useradmin/testsupport` に置き、`auth`・`invitation`・`user` の漏えいのテストでは各機能の `testsupport` に同じ形の小さな手伝いを置くか、`common/testsupport` に1つ置くかを Step 25 で決める（機能の間でテストの手伝いを使い回さない決まりのため。`common/testsupport` は共通の置き場として既存の `TestDatabase` などがある。8節の D-7）。

### 4.5 カバレッジ（手を入れるパッケージと今の値）

今の値は、`develop`（0402a53）の B2 の関門の `verify`（`:backend:cleanTest :backend:cleanIntegrationTest` を付けたもの、2026-10-02 21:28）が残した `backend/build/reports/jacoco/test/jacocoTestReport.xml` から読んだ（全体 行 98.8%（5652/5719）・分岐 94.5%（2069/2190））。Step 1・17 で実測し直して記録する。

| パッケージ | Bolt | 一覧 | 今の値（行・分岐） | 作業 |
|---|---|---|---|---|
| `useradmin.web`・`useradmin.service`・`useradmin.domain` | B3・B4 | 新しい | — | 自動でパッケージごとの下限の対象。テストで満たす |
| `common.persistence` | B4 | 新しい | — | 同上 |
| `common.observability` | B4 | **一覧にある** | 97.5%（115/118）・94.4%（34/36） | テストを足して下限を満たし、一覧から外す（I-D2） |
| `common.error.web` | B4 | **一覧にある** | 96.9%（190/196）・88.2%（82/93） | 同上 |
| `user.domain` | B3・B4 | 外 | 99.5%（208/209）・97.6%（121/124） | 下限を満たし続ける |
| `user.repository` | B3・B4 | 外 | 計測の行なし（インターフェースだけ） | B4 で `UserRowLockRepository`・`UserAdminRow` が入り計測の対象になる。テストで満たす |
| `user.service` | B3・B4 | 外 | 100.0%（236/236）・95.6%（86/90） | 下限を満たし続ける |
| `auth.domain` | B3 | 外（B1 で外した） | 98.0%（98/100）・100.0%（18/18） | 同上 |
| `auth.repository` | B3・B4 | 外（B1 で外した） | 100.0%（33/33）・100.0%（2/2） | 同上（E1 と `tryLockForUpdate` の catch の分岐を単体テストで通す） |
| `auth.service` | B3・B4 | 外 | 99.1%（222/224）・92.4%（61/66） | 同上 |
| `audit.domain`・`audit.service` | B4 | 外 | 99.5%（214/215）・97.8%（44/45）／100.0%（148/148）・84.4%（27/32） | 同上 |
| `invitation.repository` | B4 | 外 | 100.0%（2/2）・分岐なし | 同上（断片の実装の catch の分岐を単体テストで通す） |
| `access.domain` | B3・B4 | 外（B1 で外した） | 100.0%（53/53）・100.0%（29/29） | 使うだけで本体を変えない（作業なし） |

- 「手を入れる」は `src/main` の変更のすべて（説明文だけの直しを含む）。B3 で一覧のパッケージに手が入る見込みは無い。B4 は `common.observability`・`common.error.web` の2つ。Step 14・38 で `git diff --name-only develop -- backend/src/main` を一覧と突き合わせ、ほかに当たったら同じく外す。
- 計測の除外（`coverageExclusions`）は増やさない。一覧に戻す・増やす変更はしない。

### 4.6 verify の時間と CI の 60 分（基盤の設計の R-02 の申し送り）

| 項目 | 値 | 出どころ |
|---|---|---|
| 今の手元の `verify`（clean を付けたもの）の時間 | 6 分 17 秒（B2 の関門）、6 分 20 秒（B1 の関門） | `u4-admin-forbidden-ui/code-generation/generation-notes.md`・`u1-user-suspension/code-generation/code-summary.md` |
| B3 の基準値 | Step 1 で実測（時間・テストの件数）。あわせて `develop` の最新の CI の実行の時間を `gh run list` の読み取りで記録する | Step 1 |
| B4 の増加の見込み | 上限切れを起こす結合テストは1件ごとに約 3 秒待つ。U3 の5つの操作（5件）、漏えいの確かめ（5つの操作と E1〜E4 の経路と書き込みの問い合わせを TRACE と INFO の両方。1つの文脈の中でログのレベルを切り替える形で約 20 件）で、約 25 件 × 約 3 秒 ＝ 約 1 分 15 秒。書き込みの問い合わせの待ちの上限（H2 の既定の値）は試していないため Step 25 で実測する。新しい Spring の文脈（結合テストのクラス）の起動の分を加え、手元の `verify` は約 9〜11 分の見込み | 見積もり（未測定） |
| 許容 | 手元の `verify` が B3 の基準値から 5 分以内の増加、かつ CI の実行が 30 分（制限時間 60 分の半分）以内。超えたら、待つ時間を短くするために排他の待ちの上限を変えることはせず（NFR4.4）、止めて依頼者に諮る（テストのまとめ方（文脈の共有など）の見直しを案として示す） | 9節の Q-C の決定（A） |

## 5. 手順

各層で実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む（test-after、Testing Contract の `ordering`）。各 Step の実行のコマンドは `unit-test-instructions.md` の2節のとおりで、U3 のテストのクラスを名指しして流す。

---

## B3 一覧と氏名・言語の変更（Step 1〜16）

### Step 1: 作業の場の用意と、変更の前の基準（ブランチの作成は依頼者の承認を得てから）

- [x] `develop` の先頭のハッシュを `git rev-parse HEAD` で記録する。アプリのソースに未コミットの変更が無いことを `git status` で確かめる（ワークフローの記録は外して判断する、`project.md` の学び）
- [x] 依頼者の承認を得て、`develop` から `feature/260930-user-admin-b3` を作り、記録のコミット R1 を作る（3.2）
- [x] `frontend/playwright-report/`・`frontend/test-results/` が残っていないことを確かめる。残っていれば中を開かずに消し、消したこと（種類と件数だけ）と共有していないことを記録する（`gate-decisions.md` の U5 R-02 の決定）
- [x] Dependabot の開いている知らせ（`origin` の `dependabot/*` のブランチ）を読み取りだけで確かめ、重大度 High 以上の脆弱性の直しがあれば B3 に入る前に取り込むかを依頼者に諮る（`team.md` の Way of Working）。無ければ記録だけ
- [x] 変更の前の基準をとる: colima が動いていることを確かめ、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行し、かかった時間、テストの件数（単体・結合・画面、失敗・飛ばした）、全体と 4.5 の表のパッケージの行・分岐のカバレッジを `backend/build/reports/jacoco/test/jacocoTestReport.xml` から記録する（brownfield の Test Baseline、4.6）
- [x] `gh run list --branch develop --limit 3`（読み取り）で最新の CI の実行の時間を記録する（4.6）
- [x] 対応: B3 の共通の完了の条件、基盤の設計の R-02、`gate-decisions.md`

### Step 2: テストの実行の準備（最初のテストより前）

- [x] `unit-test-instructions.md` の 2.1 のコマンドで、既存の単体テストと結合テストの道具が作業ブランチの上で動くことを確かめる（`UserAccountServiceTest`・`UserRepositoryIT`）
- [x] まだ作っていない新しいテストのクラスを `--tests` に名指しすると Gradle の「一致するテストが無い」で失敗する。これは想定どおりで、作った Step からコマンドが通ることを `unit-test-instructions.md` と合わせる
- [x] U3 のテストは組み込みの H2 だけを使うため、Step ごとの実行に colima の設定は要らない（対象DB のテストを含む `verify` だけが要る）
- [x] 対応: Testing Contract の `runner_step`

### Step 3: 一覧の検索の問い合わせの確かめ（SD-4、B3 の最初の手順）

- [x] `UserRepository` に、検索ありの件数 `countBySearch(RedactedText pattern)` と行 `findAdminRowsBySearch(RedactedText pattern, Pageable)` を、HQL の `ilike :#{#pattern.value()} escape '\'`（メールアドレスと氏名の OR）で書く。投影の `UserAdminRow`（4.1）もこの Step で作る
- [x] `UserAdminQueriesIT` の最初のテストで、アプリの起動（Spring Data が `@Query` を解析する）と、`RedactedText` のパターン（`%taro%` など、テストの中で作る）で件数と行が返ることを確かめる
- [x] 受け付けないとき（起動で問い合わせの解析が失敗する、`ilike`・`escape` か SpEL の引数が使えない）は、同じ形の `ILIKE ... ESCAPE '\'` を使う native の問い合わせ（名前つきの引数と SpEL のまま、`nativeQuery = true`）に切り替え、差を `generation-notes.md` に書く（`security-design.md` 6.2）
- [x] H2 の方言で `ILIKE` に写ったことを、`spring.jpa.show-sql` を使わずに `SqlStatementCounter` の記録した SQL の文で確かめる（値は `?` のまま）
- [x] 対応: SD-4、BR1.5、NFR9.1、NFR3.1

### Step 4: ドメイン — 実装

- [x] `user.domain.SearchText`・`ProfileUpdate`・`ProfileValidation`（4.1）。`SearchText` の空白の範囲は `DisplayName.strip` と同じ Unicode の White_Space、小文字化は `Locale.ROOT`、エスケープの文字は `\`。どれも `String` を返す公開のメソッドを持たない（`likePattern()` は `RedactedText`）
- [x] `auth.domain.LockView` と判定の関数（4.1）。境界は既存の `LockPolicy` と同じ（解除の予定の時刻ちょうどはロック中でない）
- [x] `useradmin.domain.UserAdminProblemTypes`（`USER_NOT_FOUND` 404 と `all()`）。説明文に対象の利用者のメールアドレス・氏名・ID を載せない
- [x] 対応: BR1.3・BR1.4・BR1.5・BR1.7・BR5.1・BR5.2・BR2.3、NFR3.1・NFR3.2・NFR8.1・NFR9.1、R-05・R-07

### Step 5: ドメイン — テスト（単体）

- [x] `SearchTextTest`: 前後の半角・全角の空白を除く、内側の空白を残す、空白だけは空、254 コードポイントは受け付け 255 は長すぎる（サロゲートペアを含めてコードポイントで数える）、`%`・`_`・`\` のエスケープ、小文字化は `Locale.ROOT`（トルコ語の Locale を既定にしても同じ結果）、`toString` に値が出ない。性質ベースのテスト（jqwik）: どんな入力でもパターンの前後を除いた中にエスケープされていない `%`・`_` が無い、パターンを元に戻すと除いた後の小文字の値になる
- [x] `ProfileValidationTest`: 氏名の REQUIRED・TOO_LONG（254／255）・INVALID_CHARACTER、言語の `ja`・`en` 以外（`JA`・`fr`・null）、両方の誤りの順、`ProfileUpdate` の `toString` に氏名が出ない
- [x] `LockViewTest`: 行が無い、失敗回数 0・解除の予定なし、失敗回数 n・解除の予定なし、now < t、now = t（ロック中でない・戻せる）、now > t、失敗回数 0・解除の予定 t（R-05 の守り）。性質ベースのテスト: `locked` なら `lockedUntil` があり `resettable`、`locked` でなければ `lockedUntil` が無い、`resettable` は「失敗回数 ≥ 1 か解除の予定あり」と同じ
- [x] `UserAdminProblemTypesTest`: code・状態コード 404・ja と en の説明文があり、説明文に利用者の値の置き場所が無い
- [x] 対応: NFR9.8（性質ベースのテスト、失敗時の種は `junit-platform.properties` の既存の設定で報告に出る）、NFR9.7

### Step 6: DB アクセス — 実装

- [x] `UserRepository` の検索なしの行 `findAdminRows(Pageable)`（並び `order by u.createdAt, u.userId`）と `updateProfile(long, ProfileUpdate)`（`UPDATE User u SET u.displayName = :#{#profile.displayName()}, u.language = :#{#profile.language()} WHERE u.userId = :userId`）。Step 3 の2つと合わせて、どの問い合わせも `String` の引数を持たない（利用者 ID・`Pageable`・`RedactedText`・`ProfileUpdate` だけ）
- [x] `LoginAttemptStateRepository#findBySubjectIds(Collection<Long>)`（排他なし、`select s from LoginAttemptState s where s.subjectId in :ids`）
- [x] 対応: BR1.1・BR1.2・BR1.6・BR1.7・BR5.2、NFR5.2、NFR3.1

### Step 7: DB アクセス — テスト（結合）

- [x] `UserAdminQueriesIT`（`unit-test-instructions.md` の 2.3）:
  - 並び: 登録した日時の古い順、同じ日時は利用者 ID の小さい順（入れた順と日時の向きをずらしたデータ）
  - ページ: 20 件ずつ、最後のページの件数、`Pageable` の位置
  - 検索: メールアドレスか氏名の部分一致、ASCII の英字・全角の英字・アクセントつきの欧文字で大文字と小文字を区別しない、`%`・`_`・`\` が文字どおり（`100% off_sale\x` だけに当たり `100X offXsale` に当たらない）、件数が行と同じ条件
  - 既知の差（R5、9節の Q-E）: `İ` を含む検索は `İ` を含む氏名に当たらない、`ß` と `SS` は別、を決めた側の動作として1件ずつ固定する
  - 読む列: 記録した SQL の文に `password_hash` が入らない
  - `updateProfile`: 氏名と言語の2列だけが変わり、テーマ・文字の大きさ・メールアドレス・印・停止・パスワードのハッシュは変わらない。いない ID は 0 行。同じ値でも 1 行
- [x] `LoginAttemptStateRepositoryIT` に、`findBySubjectIds` が指定した ID の行だけを排他なしで返し（別の接続が行を排他していても待たない）、ダミーの行を返さないことを足す
- [x] 対応: BR1.1・BR1.5・BR1.7・BR5.2、NFR5.2・NFR9.1、R5

### Step 8: 業務処理 — 実装

- [x] `user.service` の `UserAdminSummary`・`UserAdminSlice`・`ProfileCommand`・`ProfileUpdateResult` と `UserAccountService#findAdminPage`・`#updateProfile`（4.1）。投影の `UserAdminRow` を `UserAdminSummary` に写す
- [x] `auth.service.LockAdministrationService#lockViewsOf`（読み取りだけのトランザクションに入る。行が無い ID は `(false, null, false)`）
- [x] `useradmin.service.UserAdminService#list`・`#updateProfile`、`UserAdminListResult`・`UserAdminPage`・`UserAdminEntry`・`UserAdminProblemTypeCatalog`。一覧は読み取りだけの `TransactionTemplate` の中で `findAdminPage` と `lockViewsOf` を読む（BR1.6）。判定の順は page → q（BR1.4、どちらも誤りなら page）。氏名と言語は `TransactionTemplate` の中で `updateProfile` を呼ぶ（入力の誤りは DB に触れずに返る）
- [x] 業務のログは出さない（`observability-design.md` 3節）
- [x] 対応: BR1.1〜BR1.9・BR2.8・BR5.1〜BR5.4・BR7.4、C2・C8（D9・D13）

### Step 9: 業務処理 — テスト（単体・結合）

- [x] `UserAccountServiceTest` に足す: `findAdminPage` が検索なし・空白だけ・検索ありで問い合わせを選ぶ、読み始めの位置が件数以上なら行を読まない、`updateProfile` の誤りは DB に触れない・0 行は `NotFound`・`ProfileUpdate` の値（前後の空白を除いた氏名）、要約と結果の `toString` に値が出ない
- [x] `LockAdministrationServiceTest`: 時計の now で判定する、行の無い ID、空の ID の集まりで問い合わせない
- [x] `UserAdminServiceTest`（B3 の分）: page の誤り、q が長すぎる（`q`・`TOO_LONG`）、両方の誤りは page、空白だけの q は検索なし、self の判定、氏名と言語の結果の写し、監査の出来事を出さない
- [x] `UserAdminProblemTypeCatalogTest`: `UserAdminProblemTypes.all()` を返す
- [x] `UserAdminAccountIT`（`user.service` の結合）: 一覧の1回の読み取りで件数と行がそろう、`updateProfile` が呼び出し元のトランザクションの外では使えない（MANDATORY）
- [x] 対応: BR1.4・BR1.6・BR1.8・BR5.1〜BR5.3、NFR9.7

### Step 10: web — 実装

- [x] `useradmin.web` の `UserAdminController`（GET 一覧・PUT 氏名と言語）・`AdminUser`・`AdminUserPage`・`ProfileRequest`・`SearchTextConverter`・`UserAdminWebConfig`・`UserAdminRequestContextResolver`・`UserAdminFieldErrors`（4.1）
- [x] 一覧: `InvalidPage` は 400 `VALIDATION_FAILED`（項目なし、招待の一覧と同じ）、`Invalid` は 400 `VALIDATION_FAILED`（`fieldErrors` に `q`・`TOO_LONG`、値は載せない）。応答の日時は ISO 8601 の UTC。ロック中でないときの `lockedUntil` は null か項目なし
- [x] 氏名と言語: 204（本文なし）、`Invalid` は 400 `VALIDATION_FAILED`（`fieldErrors`）、`NotFound` は 404 `USER_NOT_FOUND`。`userId` が整数でないときは既存の `TypeMismatchException` の 400
- [x] 新しい公開の決まり（`SecurityRuleContributor`）は足さない。既存の `/api/admin/**` の管理者の判定に乗る
- [x] 対応: BR1.3・BR1.8・BR2.7・BR2.8・BR5.4・BR7.1・BR7.5、NFR1.1・NFR3.3・NFR8.1、C3

### Step 11: web — テスト（単体・結合）

- [x] 単体: `SearchTextConverterTest`（包むだけ、空文字・空白・長い値でも例外を出さない）、`UserAdminWebTypesTest`（`AdminUser`・`AdminUserPage`・`ProfileRequest` の `toString` にメールアドレス・氏名が出ない）、`UserAdminRequestContextResolverTest`（匿名・数でない主体は 401）
- [x] `UserAdminListApiIT`:
  - 200 の項目が 11 個と self だけ（`additionalProperties: false` の形。ハッシュ値・失敗回数・ダミーの行の印が無い）、size 20・total・page
  - 並びと 20 件、停止中の利用者と初期管理者を含む、self は自分の行だけ真
  - ロックの表示（`MutableClock`）: ロック中・解除の予定ちょうど（ロック中でない・戻せる）・解除の予定を過ぎた・行が無い・失敗回数 1 で解除の予定なし
  - 最後のページより後の page は items が空で total つきの 200、全体が 0 件（利用者を消せないため、検索で当たらない状態と、`total` が 0 になる問い合わせの結果で確かめる）の 200（U2 の申し送り）
  - page の 0・数でない・空・10 桁は 400、q の 254 コードポイントは 200・255 は 400（`q`・`TOO_LONG`、応答に q の値が無い）、空白だけの q は全件、page と q がどちらも誤りなら page の 400
  - 要求の行が 8KB を超える（page が 9,000 文字の数字）と Tomcat が `text/html` の 400 を返し、アプリに届かない（U2 の申し送り。応答の形を記録する）
  - 認可: 未認証 401 `AUTHENTICATION_REQUIRED`、管理者でない 403 `ACCESS_DENIED`（U4 の申し送り）、管理者 200、停止中の管理者 401（NFR1.3）。どれも監査の管理の操作の行が増えない（403 は既存のアクセスの拒否だけ、q を付けた要求でも監査のパスに q が入らない）
- [x] `UserAdminListQueryCountIT`（`SqlStatementCounter`）: 20 件のページで内部DB への問い合わせが3回（件数・行・ロックの状態）、最後のページより後で1回（件数だけ）、行の数で増えない（NFR5.2）
- [x] `UserAdminProfileApiIT`: 204 で氏名と言語だけが変わる、自分自身も変えられる、同じ値でも 204、入力の誤り（氏名の空・255・制御文字、言語 `JA`・`fr`）は 400 で `fieldErrors` の項目と理由だけ、対象がいない 404 `USER_NOT_FOUND`（ja・en の説明文、`Accept-Language: en`）、入力の誤りと対象がいないが重なると 400、`userId` が数でないと 400、本文に `admin`・`suspended`・`email`・`theme`・失敗回数の項目を入れても氏名と言語だけが変わる（BR5.4・要求の改ざん）、未認証 401・管理者でない 403 `ACCESS_DENIED`・停止中の管理者 401、どれも監査に残らない
- [x] 対応: AC1.1.1〜AC1.1.7・AC1.1.13・AC5.1.1・AC5.1.4〜AC5.1.6・AC5.1.8、NFR1.1・NFR1.3・NFR1.5・NFR3.2・NFR3.3・NFR5.2・NFR8.1・NFR9.1・NFR9.7

### Step 12: 漏えいのテスト（TRACE と INFO）

- [x] `UserAdminSecretLeakIT`（B3 の分）: 1つの Spring の文脈の中で、`cherry.mastersmith` のロガーを TRACE にした場合と既定の INFO の場合の両方で（Spring Boot の `LoggingSystem` でテストの中で切り替え、終わったら戻す。切り替えが追跡に効かないときは2つのクラスに分け、差を記録する）、q を付けた一覧と氏名と言語の変更を呼び、出力（標準出力と標準エラー）のどの行にも、利用者のメールアドレス・氏名・検索の文字・変更した氏名・パスワードのハッシュ値が出ないことを確かめる。TRACE の場合は、`UserAdminController#list`・`UserAdminService#list`・`UserAccountService#findAdminPage`・`UserRepository#findAdminRowsBySearch`・`#updateProfile` の ENTER・EXIT の行が出て `***` を含むことも確かめる（追跡が効いていることの確かめ）
- [x] 応答の本文と監査の行（増えないこと）にハッシュ値・トークン・失敗回数が無い
- [x] 対応: AC1.1.6、NFR3.1〜NFR3.4、BR7.4・BR7.5、`team.md` の「利用者の管理の漏えい」

### Step 13: 構造の検査

- [x] `UserAdminBoundaryArchitectureTest`（B3 の版）:
  - `useradmin` は `user.repository`・`user.web`・`auth.repository`・`auth.web`・`invitation`・`dsl`・`dslmanage`・`targetdb`・`mail`・`appearance`・`audit` に依存しない
  - `useradmin` は JPA のエンティティ（`@Entity`）に依存しない（U1 R-03 の守り (a)）
  - `access` に依存してよいのは `useradmin.web` だけで、`access.domain.AccessProblemTypes` だけ（B3 では使わないが規則を置く）
  - `user` は `auth` に依存しない（既存の `AuthBoundaryArchitectureTest` が確かめていることを、`useradmin` の足した口の後も変えずに通す）
- [x] 既存の `ArchitectureTest` と機能ごとの境界テストを変えずに流して通す
- [x] 対応: BR7.6、NFR11.1、`team.md` の Code Style

### Step 14: 文書とレビューでの確かめ（B3）

- [x] `README.md` に「利用者の管理の API（Intent 260930-user-admin の U3）」の節を足す（4.1、8節の D-5）
- [x] 伏せ字の経路（`security-design.md` 4節）: `useradmin`・`user`・`auth` の足した口のどの層のメソッドの引数・戻り値にも、検索の文字・メールアドレス・氏名を `String` で渡していないことを `git diff` の検索で確かめて記録する
- [x] page の文字列（U2 の残る危険 R3）: page の文字列が出うるのは `UserAdminController#list`・`UserAdminService#list` の引数だけであることを確かめて記録する
- [x] **E2E を流す条件（基盤の設計の R-03）**: `git diff --name-only develop` が次のどれかに当たれば Step 15 で E2E を流す: `frontend/` の下、`auth.web`、`auth.service.LoginService`・`TokenRefreshService`・`LogoutService`、`access.web`、`common.security`、`config`、`LoginAttemptStateRepository`・`RefreshTokenRepository`・`UserRepository` の既存のメソッドの本文、`common.observability`・`common.error`。当たらなければ「流さない」と根拠（ファイルの一覧）を記録する
- [x] `packagesJudgedByTotal`: `git diff --name-only develop -- backend/src/main` を 4.5 の一覧と突き合わせ、一覧のパッケージに手が入っていないことを確かめる（入っていたらテストを足して外す）
- [x] 対応: BR7.4、NFR3.1、`team.md` の Testing Posture、`cicd-pipeline.md` 5節

### Step 15: 1コマンドの検査（B3 の統合の前の関門）

- [x] colima の設定を渡し `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行し、すべての段が通ることを確かめる。対象DB のテストが SKIPPED になっていないことを確かめる（`project.md` の学び）
- [x] かかった時間・テストの件数・全体と 4.5 のパッケージの行・分岐を実測の数字で記録し、Step 1 の基準と比べる。新しい `useradmin` の各パッケージが下限を満たす。下回ったらテストを足す。下限・除外は変えない
- [x] `./gradlew osvScan --rerun-tasks` を通す。SpotBugs ＋ FindSecBugs・Gitleaks を除外を足さずに通す（`backend/config/spotbugs-exclude.xml`・`.gitleaks.toml` に差が無い）。`backend/gradle.lockfile`・`frontend/package-lock.json` に差が無い
- [x] Step 14 の条件に当たったときだけ E2E（Step 42 と同じ手順）を流し、json から結果を記録してから報告を消す（`gate-decisions.md` の U5 の B3 の申し送り）
- [x] 失敗が一時的に見えるときは `team.md` の「不安定なテストと CI の失敗」の決まりで扱う
- [x] 対応: B3 の共通の完了の条件、`project.md` の Mandated（統合の前の確認）

### Step 16: 記録、コミットの提案、統合（B3）

- [x] `generation-notes.md`（B3 の節）、`code-summary.md`・`source-manifest.json`・`traceability.json` の B3 までの版（B4 で受け持つ受け入れ基準は `Deferred`、持ち主 B4。9節の Q-B）を作る。記録のコミット R2 を提案する
- [x] 3.3 の C1〜C3 の区切りで、コミットの内容とメッセージの案を依頼者に示して承認を得る（生成の担当はコミットしない）
- [x] 関門の記録を書き、R3 を提案する。依頼者の承認を得て 3.2 の手順で `develop` へ squash の1コミットで統合し、記録のコミットを作り、作業ブランチを消す。プッシュは依頼者が行う
- [x] 依頼者のプッシュの後、CI の `verify` が通ることを確かめる。通るまで B4 に入らない
- [x] 対応: 段の記録、`project.md` の Change Control、`team.md` の Way of Working

---

## B4 管理の操作と最後の管理者の保護（Step 17〜43）

### Step 17: 作業の場の用意と、変更の前の基準（ブランチの作成は依頼者の承認を得てから）

- [x] B3 の統合と CI の合格を確かめ、`develop` の先頭のハッシュを記録する。アプリのソースに未コミットの変更が無いことを確かめる
- [x] 依頼者の承認を得て、`develop` から `feature/260930-user-admin-b4` を作る
- [x] 報告のディレクトリが残っていないこと、Dependabot の知らせを Step 1 と同じく確かめる
- [x] 基準: B3 の Step 15 の実測（時間・件数・カバレッジ）を B4 の基準とする。`develop` が B3 の統合から変わっていれば、Step 1 と同じ形で実測し直す
- [x] 対応: B4 の共通の完了の条件、基盤の設計の R-02

### Step 18: テストの実行の準備（最初のテストより前）

- [x] `unit-test-instructions.md` の 2.10 のコマンドで、B4 で手を入れる既存のテスト（`GlobalExceptionHandlerTest`・`InvitationRepositoryIT`・`InvitationServiceTest`）が作業ブランチの上で通ることを確かめる
- [x] 対応: Testing Contract の `runner_step`

### Step 19: 排他の失敗の判定の部品 — 実装（`common.persistence`）

- [x] `RowLockFailures`・`RowLockUnavailableException`・`RowLockAttempt`（4.3）。判定は型と誤りの番号の両方で行い、原因の連なりを最後までたどる（`security-design.md` 7.1）
- [x] WARN のキーは `lockKind`・`exceptionClass` だけ。メッセージは決まった文
- [x] 対応: `security-design.md` 7.1、ND-3、SD-5、NFR3.1・NFR3.4

### Step 20: 排他の失敗の判定の部品 — テスト（単体）

- [x] `RowLockFailuresTest`: JPA の3つの型、原因に誤りの番号 50200・40001 や SQLState HYT00・40001 を持つ `SQLException` の連なり（Spring の `CannotAcquireLockException` に包まれたものを含む）を排他の失敗とし、ほかの番号（23505 など）・連なりに `SQLException` が無いもの・null を排他の失敗としない。`warn` が例外の文と原因をログに渡さない（ログの出来事に throwable が無く、キーが2つ）
- [x] `RowLockUnavailableExceptionTest`: 原因が無い、文が決まっている、`DataIntegrityViolationException` の系統でない
- [x] 対応: `security-design.md` 7.1 の確かめ

### Step 21: 既存の排他の読み取り E1〜E4 の直し — 実装（DB アクセス）

- [x] E1: `LoginAttemptStateRepository#lockForUpdate` を 4.3 のとおり直す。`lockDummyForUpdate` の代わりの道は `lockForUpdate` を通るため、同じ直しに含まれる。この Step で `tryLockForUpdate` も足す（同じ問い合わせと判定を共有する）
- [x] E2〜E4: `invitation.repository.InvitationLockQueries` と `InvitationLockQueriesImpl`（Spring Data の独自の断片、名前は `Impl` の後置の既定）を作り、3つのメソッドを今と同じ名前・引数・戻り値で移す。メールアドレスは今と同じ `InvitationEmail` で受け、文字列は本体の中でだけ取り出す。`InvitationRepository` から3つの `@Lock`・`@QueryHints`・`@Query` の宣言を消し、断片を継ぐ。`InvitationService`・`RegistrationService` と既存の単体テストは変えない
- [x] 排他の失敗でない例外は今までどおりそのまま投げる。応答は今までどおり 500 `INTERNAL_ERROR`（`GlobalExceptionHandler` の想定外の誤り）、巻き戻し・監査も今と同じ
- [x] 対応: `security-design.md` 7.2（SD-6）、NFR 設計の2回目のレビューの R-02

### Step 22: 既存の排他の読み取りの直し — テスト（単体・結合）

- [x] `LoginAttemptStateRepositoryTest`（EntityManager と問い合わせを差し替えた単体）: 上限切れの各型で `lockForUpdate` は `RowLockUnavailableException`・`tryLockForUpdate` は `Busy`、例外がメソッドの外へそのまま出ない、ほかの例外はそのまま出る
- [x] `LoginAttemptStateRepositoryIT` に足す: 別の接続で行を持ち続けて `lockForUpdate` が約 3 秒で `RowLockUnavailableException`、`tryLockForUpdate` が `Busy`、行が無ければ空、持たれていなければ得る
- [x] `InvitationLockQueriesImplTest`（単体）: 3つのメソッドで同じ確かめ
- [x] `InvitationRepositoryIT` に足す: 3つのメソッドが今までどおり排他つきで読める（招待中・ID・トークンのハッシュ）、別の接続で行を持ち続けると約 3 秒で `RowLockUnavailableException`
- [x] 既存の `InvitationServiceTest`・`RegistrationServiceTest`・`InvitationConcurrencyIT`・`RegistrationConcurrencyIT` を変えずに流して通す
- [x] 対応: `security-design.md` 7.1・7.2 の確かめ、NFR9.6

### Step 23: 書き込みの問い合わせの上限切れの中央の手当て — 実装

- [x] `common.observability.LockFailureSafeTraceInterceptor` と `TraceAspect#createInterceptor` の切り替え（4.3）。Spring の `CustomizableTraceInterceptor` の保護されたメソッド（例外の文言の置き換えと書き出し）を上書きする。上書きできる口が版で違うときは、呼び出しを包んで受け直す形にし、差を記録する
- [x] `GlobalExceptionHandler#log` の直し（4.3）
- [x] 設定の項目（`mastersmith.trace.*`・`logging.level`・`logback-spring.xml`）と既定値は変えない。`MASTERSMITH_TRACE_LOG_EXCEPTION_STACK_TRACE` が true のままでも、排他の失敗の連なりの文が出ない
- [x] 対象は「排他の失敗の連なりを持つ例外」すべてで、書き込みの問い合わせ（`UserRepository` の `updatePreferences`・`updatePasswordHashIfUnchanged`、`RefreshTokenRepository` の `@Modifying` の2本、`InvitationRepository` の `@Modifying`、`LoginAttemptStateRepository` の `update`・`createIfAbsent`）を個別に直さない（I-D1）
- [x] 対応: `infrastructure-specification.md` 6.2・10節の I-D1、`monitoring-design.md` 4節の M-D2、NFR3.1・NFR3.4

### Step 24: 中央の手当て — テスト（単体）

- [x] `LockFailureSafeTraceInterceptorTest`: 排他の失敗の連なりの例外では EXCEPTION の行がクラスの名前だけで、元の例外の文・原因の文（値に見立てた見分けやすい文字）・スタックトレースの原因が出ない。ほかの例外は今までどおり（例外の文とスタックトレース）。ENTER・EXIT は変わらない
- [x] `GlobalExceptionHandlerTest` に足す: 排他の失敗の連なりの例外は 500 `INTERNAL_ERROR` のままで、ERROR が1件、原因が無く `exceptionClass` がある。ほかの想定外の例外は今までどおり原因つき
- [x] 既存の `TraceAspectIT`・`ErrorResponseIT` を変えずに流して通す
- [x] 対応: NFR3.4、I-D1

### Step 25: 既存の経路の上限切れの漏えいの結合テストと再現の確かめ

- [x] 別の接続で行を持ち続ける手伝い（`RowLockHolder`）で上限切れを起こし、1つの Spring の文脈の中で TRACE と既定の INFO の両方（Step 12 と同じ切り替え）で、次を確かめる。どれも (a) 応答が今までどおり、(b) 出力のどの行にも、排他されていた行の値（見分けやすい値で入れたメールアドレス・氏名・招待のトークンのハッシュ値の16進・解除の予定の時刻）と `MVStoreException` の文が無い、(c) WARN に排他の種類とクラスの名前が出る（読み取りの排他の経路）・ERROR が1件で原因が無い
  - `AuthLockTimeoutLeakIT`: 実在の利用者のログイン（E1、500）、ダミーの行の8つをすべて別の接続で持ったときの存在しないメールアドレスのログイン（代わりの道、500。9節の Q-G）、トークンの更新の書き込み（リフレッシュトークンの行を持ったとき、500）
  - `InvitationLockTimeoutLeakIT`: 招待（E2）、送り直しと取り消し（E3）、登録の完了（E4）。どれも 500
  - `MePreferencesLockTimeoutLeakIT`: 表示の設定の保存の書き込み（利用者の行を持ったとき、500）
- [x] 書き込みの問い合わせの待ちの上限（H2 の既定の値）は試していないため、待った時間を実測して記録する（4.6）。待ちに入ったことは `H2SessionWaits` で確かめる
- [x] Hibernate の `SqlExceptionHelper` の WARN・ERROR の行に値が入らないこと（`security-design.md` 7.3 の前提）を同じテストの出力の全体の確かめで確かめる。書き込みの問い合わせで値が入ると分かったら、止めて依頼者に諮る
- [x] **再現の確かめ（9節の Q-F）**: テストが通った後、直しの本体（Step 21・23 の `src/main` の変更）だけを一時的に元に戻した状態で、この Step のテストが落ちる（値か `MVStoreException` の文が出る）ことを確かめ、落ちた件数と出た値の種類だけを記録してから、直しを戻し、`git diff` で戻したことを確かめる。値そのものは記録しない
- [x] 対応: `security-design.md` 7.2・7.3・10節、`infrastructure-specification.md` 6.2、`project.md` の Mandated（不具合を再現するテストを同じコミットに）・Forbidden

### Step 26: ドメイン — 実装（拒否の判定と監査）

- [x] `useradmin.domain` の `AdminOperation`・`RejectionReason`（判定の順の並び）・`OperationFacts`（真偽だけ）・`RejectionPolicy#decide`（FS の 2.2 の擬似コードのとおり、操作ごとに当たる理由だけを順に調べ、最初の1つ）・`UserAdminAuditFailure`・`UserAdminAuditEvent`・`UserAdminProblemTypes` の 409 の5つ（4.3）
- [x] `audit.domain` の `AuditEventType`（5つ）・`AuditFailureReason`（4つ）・`AuditEventFactory#from(UserAdminAuditEvent)`（場合を尽くす `switch`）。名前はどれも 32 文字以内（最長 `LOGIN_FAILURES_RESET` 20 文字、`LAST_ACTIVE_ADMIN` 17 文字）
- [x] 対応: BR2.1〜BR2.3・BR6.1・BR6.2、C6、NFR9.3・NFR10.1・NFR8.1

### Step 27: ドメイン — テスト（単体）

- [x] `RejectionPolicyTest`: 操作ごとの理由の当てはまり（失敗回数を戻すは自分自身・対象が停止中を当てない、止めるは対象が停止中を当てない、印を付ける・停止を解くは最後の管理者を当てない）、理由が重なるときの順、どれにも当たらないとき。性質ベースのテスト（jqwik）: 返す理由はその操作に当たりうる理由に限る、返した理由より前の理由の条件はどれも偽、事実がどれにも当たらなければ拒否しない
- [x] `UserAdminAuditEventTest`: `toString` に個人に関する値の項目が無い
- [x] `UserAdminProblemTypesTest`: 6つの code と状態コード、ja・en の説明文
- [x] `AuditEventFactoryTest` に足す: 5つの操作の成功と6つの理由の写し、`enteredEmail` が空、対象は要求の利用者 ID のまま（いない ID も）
- [x] 既存の `AuditEventTest#namesFitIntoTheColumns` を変えずに流して通す
- [x] 対応: NFR9.8・NFR9.3・NFR10.1

### Step 28: DB アクセス — 実装（排他と印）

- [x] `user.repository.UserRowLockRepository`（4.3）。排他の問い合わせは ID だけを読み、エンティティを持続の文脈に載せない
- [x] `UserRepository#findAdminRow`・`#findActiveAdminIds`・`#updateAdminFlag`（4.3）
- [x] 失敗回数を戻す1段目の `LoginAttemptStateRepository#tryLockForUpdate` は Step 21 で作った。2段目は既存の `update(id, 0, null)` を使う（8節の D-4）
- [x] 対応: BR3.1・BR3.3〜BR3.5・BR4.1・BR4.2・BR4.5、ND-1、NFR4.3

### Step 29: DB アクセス — テスト（単体・結合）

- [x] `UserRowLockRepositoryTest`（EntityManager を差し替えた単体）: 上限切れの各型で `Busy` と WARN、例外がメソッドの外へ出ない、ほかの例外はそのまま出る
- [x] `UserRowLockRepositoryIT`: 管理者の行と対象の行を利用者 ID の昇順に排他する（入れた順と登録した日時の向きをずらしたデータで、別の接続が途中の行を持つとそれより大きい ID はまだ排他されていない。`reliability-design.md` 1.4 の本番版）、停止中の管理者の行も含む、対象がいなくても管理者の行を排他する、別の接続が行を持つと約 3 秒（3000 ミリ秒以上）で `Busy`、`lockUserRow` は対象の行だけ。問い合わせの実行計画が主キーの走査のまま（`PRIMARY_KEY` と `index sorted`）であることを `EXPLAIN` で1件確かめる（`reliability-design.md` 4節）
- [x] `UserAdminQueriesIT` に足す: `findActiveAdminIds` は印あり・停止なし（ロック中を含む）、`findAdminRow`、`updateAdminFlag` は印の列だけを変え、書いた後の読み取りが新しい値を返す
- [x] 対応: NFR4.3・NFR4.5、`reliability-design.md` 2.1・4節

### Step 30: 業務処理 — 実装（5つの操作）

- [x] `user.service` の `AdminRowsLock`・`UserRowLock` と `UserAccountService` の4つの口（4.3）。`lockAdminRowsInIdOrder` は排他の後に `findAdminRow` と `findActiveAdminIds` を別の問い合わせで読む（待つ間に確定した変更を含める）
- [x] `auth.service` の `LoginFailureResetPreparation`・`LockAdministrationService#prepareFailureReset`・`#completeFailureReset`、`LoginAttemptBarrier`・`NoOpLoginAttemptBarrier`、`LoginService` の待ち合わせの口の呼び出し（実在の利用者の行の排他の直後だけ）
- [x] `useradmin.service` の `OperationResult`・`UserAdminBarrier`・`NoOpUserAdminBarrier` と `UserAdminService` の5つの操作（FS の 2.2〜2.7）:
  - 印を付ける・外す・止める: 管理者の行と対象の行の排他 → Busy なら `setRollbackOnly()` → 待ち合わせの口 → 事実の組（`leavesNoActiveAdmin` は対象を除いた有効な管理者が空か）→ `RejectionPolicy#decide` → 確かめ直し（排他の後の集合に操作した人が入るか）→ 書き換え（`setAdmin`、止めるは `setSuspended(対象, true)` と `revokeAllRefreshTokens(対象)`）→ 監査の出来事
  - 停止を解く: 対象の行だけの排他 → 判定 → `findAdminSummary(操作した人)` で確かめ直し → `setSuspended(対象, false)`
  - 失敗回数を戻す: `findAdminSummary(対象)`（いなければ対象がいない）→ `prepareFailureReset` → 判定（戻せない）→ `findAdminSummary(操作した人)` で確かめ直し → `completeFailureReset`
  - 拒否（業務の理由・確かめ直し）は書き込みをせずに確定させ、失敗の監査の出来事を出す。Busy は監査を出さない
  - 排他の前に書き込みが無いこと（`reliability-design.md` 5.3 の終わり）を流れで確かめて `generation-notes.md` に書く
- [x] `audit.service.AuditEventListener#onUserAdminAuditEvent`
- [x] **U1 R-03**: `useradmin.service` は排他の結果・要約を投影の値だけで持ち、C1 の口を呼んだ後に、先に読んだ値で何かを書かない（監査の出来事は ID と区分だけ）
- [x] 対応: BR2.1〜BR2.6・BR3.1〜BR3.6・BR4.1〜BR4.6・BR6.1〜BR6.3・BR7.3、C1・C6・C8（D5〜D8・D11）、NFR1.4・NFR4.1〜NFR4.4・NFR9.4・NFR9.5

### Step 31: 業務処理 — テスト（単体）

- [x] `UserAdminServiceTest` に足す（C8・C1 の口と待ち合わせの口を差し替え、`useradmin/testsupport/RecordingTransactionManager` を `TransactionTemplate` に渡す）:
  - 5つの操作のそれぞれで、口が Busy を返すと `setRollbackOnly()` が1回呼ばれ、その後に待ち合わせの口・判定・確かめ直し・書き換えの口・監査の出来事を呼ばず、結果が `Busy`（`reliability-design.md` 5.3 の2行目、R-02）
  - 判定の順の各理由で書き換えの口を呼ばず、失敗の出来事を1件出す（理由つき）
  - 確かめ直しで外れていたら `OperatorNotAdmin` と `NOT_ADMIN` の出来事、書き換えない
  - 成功で書き換えの口を正しい引数で呼び、成功の出来事を1件出す。止めるは `setSuspended` の後に `revokeAllRefreshTokens` を呼ぶ
  - 失敗回数を戻す: 対象がいない（1段目を呼ばない、負の ID も）、1段目が `NothingToReset`、`Busy`
- [x] `UserAccountServiceTest` に足す: `setAdmin` の 0 行は `IllegalStateException`、`lockAdminRowsInIdOrder` が repository の `Busy` を写す・`Acquired` の後に要約と集合を読む
- [x] `LockAdministrationServiceTest` に足す: 1段目の `Ready`・`NothingToReset`（行が無い・失敗回数 0 で解除の予定なし）・`Busy`、`Ready` の判定は失敗回数 0 でも解除の予定があれば（R-05）、排他の直後に待ち合わせの口を呼ぶ
- [x] `LoginServiceTest` に足す: 実在の利用者の行の排他の直後に待ち合わせの口を1回呼ぶ、ダミーの行と行が無い場合は呼ばない
- [x] `AuditEventListenerTest` に足す: 出来事を記録の部品へ渡す
- [x] 対応: NFR4.3（R-02）・NFR1.4・NFR9.3

### Step 32: 業務処理 — テスト（結合）

- [x] `UserAdminOperationsIT`（業務処理の層を直接呼ぶ。FS の 2.10、FR4.4）: 操作した人がロック中の管理者なら受け付ける、停止中の管理者なら `LAST_ACTIVE_ADMIN` で拒否し状態が変わらない（印を外す・止めるの両方、AC2.1.11・AC3.1.9）。止める操作の途中（`revokeAllRefreshTokens` で例外を起こす差し替え）で失敗させると、停止とトークンの両方が戻る（NFR9.5）。**U1 R-03**: 止める操作の後に、印・氏名・言語が変わらず、停止とトークンの無効化が確定している
- [x] `UserAdminConcurrencyIT`（待ち合わせ。`TestUserAdminBarrier` で1つ目を数える直前で止め、2つ目が排他の待ちに入ったことを `H2SessionWaits` で確かめてから進める。止める時間の上限は 3000 ミリ秒より短い）:
  - 互いの印を外す（AC2.1.6）・一方が外し他方が止める（AC2.1.12）・互いに止める（AC3.1.5）: 有効な管理者がちょうど1人、監査が成功1行と失敗1行（`LAST_ACTIVE_ADMIN`）
  - 待つ間に新しく印が付いた行が数えに入る（確かめ 1b の本番版）: 1つ目が C に印を付ける間に、2つ目（業務処理を直接呼ぶ、操作した人 C）が唯一の元の管理者の印を外すと、C を数えて受け付ける
  - 待つ間に操作した人の印が外れた（印の操作）・停止を解く・失敗回数を戻すの確かめ直し: 403 にあたる `OperatorNotAdmin`、監査 `NOT_ADMIN`、状態が変わらない（NFR1.4）
- [x] `ResetLoginConcurrencyIT`（AC4.1.5、`TestLoginAttemptBarrier`、`MutableClock`）: 失敗回数がしきい値−1 の利用者で、戻す側が先に排他を取る場合（ログインは待ち、0 から数えて失敗回数 1・ロックなし）と、ログインが先の場合（ロックの後に戻して 0・ロックなし）。どちらも 5xx にならない
- [x] `SuspendWhileLoginIT`（確かめ 3 の本番版）: 止める操作を待ち合わせの口で止めている間に、同じ利用者のログインのトークンの追記が待たずに通る。その後の止める操作の確定でトークンはまとめて無効になる（ログインで出たものの扱いは M8 B の隙として、決めた側の動作を記録する）
- [x] `UserAdminLockPortsIT`（`reliability-design.md` 5.3 の3行目）: テストの中の `TransactionTemplate` で先に1行を書き換えてから `lockAdminRowsInIdOrder`・`lockUserRow` を呼び、別の接続が行を持つため Busy を受けて `setRollbackOnly()` で返すと、書き換えが残らず例外も出ない
- [x] `FailureResetPortIT`: 同じ形で `prepareFailureReset` の Busy。`completeFailureReset` は行を作らない
- [x] 対応: NFR4.1〜NFR4.5・NFR1.4・NFR9.5、FR4.3・FR4.4・FR5.4、`team.md` の「最後の管理者の保護」「ロックの解除」、`reliability-design.md` 2.2・5.3

### Step 33: web — 実装（5つの操作）

- [x] `UserAdminController` に5つの POST を足す（4.3）。`OperationResult` を場合を尽くす `switch` で、`Done` は 204、`Rejected` は理由の code（`USER_NOT_FOUND` 404・ほかは 409）、`OperatorNotAdmin` は 403 `ACCESS_DENIED`（`AccessProblemTypes.ACCESS_DENIED`）、`Busy` は 409 `USER_ADMIN_BUSY`（新しく作る `BusinessException`、原因をつながない）
- [x] 対応: BR2.3・BR2.6・BR2.7・BR4.6・BR7.1、C3、NFR3.3・NFR8.1

### Step 34: web — テスト（結合）

- [x] `UserAdminOperationsApiIT`:
  - 5つの操作の成功 204 と、3節の表の各 code（自分自身・対象が停止中・変えるものが無い・対象がいない 404・userId が数でない 400）、ja・en の説明文、本文に利用者の値が無い
  - 認可: 5つの操作のそれぞれで未認証 401・管理者でない 403 `ACCESS_DENIED`・管理者 204・停止中の管理者 401。401・403 で状態と管理の操作の監査が変わらない（NFR1.1・NFR1.3）
  - 管理者の印の変更（NFR1.2）: 印を付けた直後の次の要求で対象の管理の API が 200、外した直後の次の要求で 403。外す前に出したアクセストークンでも外した後は 403。トークンは無効にならない（ログインし直さずに `/api/me` は使える）。自分の印を外すと 409 `USER_ADMIN_SELF_OPERATION`
  - 止める: 対象のリフレッシュトークンがすべて使えなくなり（401 `REFRESH_FAILED`）、停止を解いても戻らない
- [x] `UserAdminResetLoginFailuresApiIT`（`MutableClock`、しきい値は設定の既定 5）: ロック中の利用者を戻した直後に正しいパスワードでログインできる、戻した後はしきい値−1 回の失敗でロックされずしきい値ちょうどでロックされる、解除の予定を過ぎた利用者も戻せる、ロックの状態の行が無い利用者は 409 `USER_ADMIN_NO_CHANGE` で行が作られない、自分自身と停止中の利用者にも許し停止は変わらない
- [x] `UserAdminBusyApiIT`（AC4.1.11）: 5つの操作のそれぞれで、別の接続で行を持ち続けると 409 `USER_ADMIN_BUSY`（500 にならない）、かかった時間が 3000 ミリ秒以上、状態が変わらない、監査の行が増えない、応答に待った行や対象の値が無い
- [x] `UserAdminMassAssignmentIT`（要求の改ざん、NFR1.5・AC2.1.7）: `PUT /api/me/preferences`・`POST /api/me/password` の本文に `admin`・`suspended`・失敗回数の項目を入れても、自分の印・停止・失敗回数が変わらない（`/api/me/preferences` は今までどおり 200）
- [x] 対応: AC2.1.1〜AC2.1.5・AC2.1.7・AC2.1.10・AC2.1.14・AC3.1.1〜AC3.1.4・AC3.1.6・AC3.1.10・AC3.1.12・AC4.1.1〜AC4.1.4・AC4.1.6〜AC4.1.8・AC4.1.10〜AC4.1.12、NFR1.1〜NFR1.5・NFR3.3・NFR8.1、`team.md` の「管理者の印の変更」「ロックの解除」「管理の API の認可」「要求の改ざん」、U4 の申し送り

### Step 35: 監査 — テスト（結合）

- [x] `UserAdminAuditIT`（`audit/testsupport/AuditRows`）: 5つの操作の成功と、各理由の失敗（`USER_NOT_FOUND`・`SELF_OPERATION`・`TARGET_SUSPENDED`・`NO_CHANGE`・`LAST_ACTIVE_ADMIN`・`NOT_ADMIN`）の行の項目（種類・結果・理由・操作した人・要求の利用者 ID（いない ID のまま）・送り手の IP・User-Agent・トレースID・日時）、`entered_email` が空。残さない場合（一覧・氏名と言語・BUSY・入力の誤り・401・認可の入口の 403 は既存のアクセスの拒否だけ）に行が増えない。トレースID がアプリのログと一致する
- [x] `UserAdminAuditWriteFailureIT`（`audit/testsupport/FailingAuditEventRepositoryConfig`、既存の `AuditWriteFailureIT` と同じ形）: 監査の書き込みが失敗しても、成功の 204・拒否の 409 と状態は変わらず、アプリのログに ERROR（メールアドレス・氏名を含まない）
- [x] 既存の `AuditSecretLeakIT`（列の一覧）を変えずに流して通す
- [x] 対応: AC2.1.3〜AC2.1.5・AC3.1.4・AC3.1.6・AC4.1.6、NFR9.3・NFR9.4、BR6.1〜BR6.4、`team.md` の「管理の操作の監査」、`project.md` の Mandated

### Step 36: 漏えいのテスト（B4 の分）

- [x] `UserAdminSecretLeakIT` に足す（Step 12 と同じく TRACE と INFO の両方）: 5つの操作（成功と拒否）、5つの操作のそれぞれの上限切れ（別の接続で行を持ち続ける）。出力のどの行にも、利用者のメールアドレス・氏名・パスワードのハッシュ値・失敗回数・排他されていた行の値・`MVStoreException` の文が無い。上限切れの WARN が2行（排他の種類とクラスの名前、code）で同じトレースID（SD-5）。応答と監査の行にハッシュ値・トークン・失敗回数が無い
- [x] 対応: NFR3.1〜NFR3.4、`security-design.md` 4節・7節・7.3、`team.md` の「利用者の管理の漏えい」

### Step 37: 構造の検査（B4）

- [x] `UserAdminBoundaryArchitectureTest` に足す:
  - 書き換えの口（`UserAccountService#setAdmin`・`#setSuspended`、`RefreshTokenRevocationService#revokeAllRefreshTokens`、`LockAdministrationService#completeFailureReset`・`#prepareFailureReset`）を呼ぶのは `useradmin.service` だけ（それぞれの持ち主のクラスを除く。NFR11.2・BR7.3）
  - `useradmin` の外で `useradmin` に依存してよいのは `audit` だけで、`useradmin.domain` だけ
  - `useradmin.web` から `access` への依存は `AccessProblemTypes` だけ
  - 排他の問い合わせを EntityManager で `user.repository` に置く形が既存の層の決まりに合うことを、既存の `ArchitectureTest` が変更なしで通ることで確かめる
- [x] 既存の `ArchitectureTest`・`AuthBoundaryArchitectureTest`・`InvitationBoundaryArchitectureTest`・`AuditBoundaryArchitectureTest` などを変えずに流して通す（`common.persistence` への依存を禁じていないことを含む）
- [x] 対応: BR7.3・BR7.6、NFR11.1・NFR11.2、U1 R-03 の守り (a)

### Step 38: カバレッジの一覧と文書

- [x] `git diff --name-only develop -- backend/src/main` で実際に手を入れたパッケージを一覧と突き合わせる。見込みは `common.observability`・`common.error.web` の2つ。`backend/build.gradle.kts` の `packagesJudgedByTotal` から消し、説明文に「Intent 260930-user-admin の B4（U3）で common.error.web・common.observability を外した」を足す。ほかに当たれば同じく外す。一覧に足さない。除外を増やさない
- [x] `README.md` の3つの節（4.3）を書く。監査の種類と理由の名前は Step 26 の定義で確かめてから書く（`project.md` の学び）
- [x] 対応: NFR9.6、I-D2、`infrastructure-specification.md` 9節

### Step 39: 負荷の試験の台本と手順書（流すのは performance-validation）

- [x] `perf/k6/scenarios.js` に場面を足す（名前の案: `userAdminList`（`LIST_CASE` で a〜d）・`userAdminProfile`・`userAdminOps`（操作ごとの tag で p95 と `checks` を閾値に置く）・`userAdminSuspendWorst`・`userAdminPool`）。閾値は `http_req_duration` の p95 1000 ms と `checks` の率 1（緩めない）。5つの操作の台本は、操作する管理者を対象にしない・初期管理者を操作する人にも対象にもしない・対象は VU ごとに分ける・組で状態を戻しながらくり返す（NFR5.4 の受け入れの条件）。失敗回数を戻す組の準備のログインの失敗は判定と回数に数えない
- [x] `perf/README.md` に「利用者の管理の場面（Intent 260930-user-admin の U3）」の節を足す: 試験用の利用者 1,000 名・管理者・対象・未無効 100 件と無効 1,000 件のリフレッシュトークンを SQL で入れる手順（メールアドレスは予約のドメインだけ）、接続プールの場面（`MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics`、上限 10 の場面は `MASTERSMITH_DB_MAXIMUM_POOL_SIZE=10`）、hikaricp の時間切れの累計と待ちの最大で判断すること、監査の件数を数えてから片付けること、`caffeinate -i` で台本の全体を包むこと
- [x] 書く前に `performance-requirements.md` の NFR5.1〜NFR5.7・`reliability-requirements.md` の NFR6.2・NFR6.3・`cicd-pipeline.md` 4節の表の項目を1つずつ台本の手順と突き合わせ、対応を `generation-notes.md` に書く（`project.md` の学び）
- [x] `k6 inspect --include-system-env-vars`（`grafana/k6:2.3.0` のコンテナ、`perf/README.md` の既存の形）で、足した場面ごとに読み込めることと場面の名前が出ることを確かめる（測定はしない）
- [x] 対応: NFR5.1〜NFR5.7・NFR6.2・NFR6.3、`cicd-pipeline.md` 4節、8節の D-2

### Step 40: コードのレビューでの確かめ（B4）

- [x] 伏せ字の経路: B4 で足した口のどの層の引数・戻り値にも個人に関する値を `String` で渡していない
- [x] **U1 R-03**: `useradmin` に JPA のエンティティを持つ変数・項目が無く、C1 の口の後に先に読んだ値で書く処理が無い（境界テストと読み合わせ）
- [x] 5つの操作の流れで排他の前に書き込みが無い（Step 30）
- [x] 排他の待ちの上限は 3000 ミリ秒の既存の定数にそろい、テストの待ち合わせで止める時間の上限は 3000 ミリ秒より短い（NFR4.4、R-03）
- [x] 監査の種類と理由の名前が 32 文字以内（NFR10.1）。移行のファイルを足していない（NFR10.2 は当たらない）
- [x] `backend/config/spotbugs-exclude.xml`・`.gitleaks.toml` に差が無い。問い合わせは名前つきの引数と SpEL だけ（NFR9.2）
- [x] 既存の警報の決まり（`docker/monitoring/provisioning/alerting/mastersmith.yaml`）が変わっていない（NFR5.10）
- [x] 対応: NFR3.1・NFR4.4・NFR5.10・NFR9.2・NFR10.1、U1 R-03 の守り (d)

### Step 41: 1コマンドの検査（B4 の統合の前の関門）

- [x] colima の設定を渡し `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を通す。対象DB のテストが SKIPPED になっていない
- [x] かかった時間・テストの件数・全体と 4.5 のパッケージ（外した `common.observability`・`common.error.web` と新しい `common.persistence`・`useradmin.*` を含む）の行・分岐を実測の数字で記録し、Step 17 の基準と比べる。下回ったらテストを足す。下限・除外は変えない
- [x] 時間の増加を 4.6 の許容と比べ、CI の 60 分に対する見込みを記録する。許容を超えたら止めて依頼者に諮る（9節の Q-C）
- [x] `./gradlew osvScan --rerun-tasks` を通す。SpotBugs・Gitleaks を除外を足さずに通す。lockfile に差が無い
- [x] 失敗が一時的に見えるときは `team.md` の「不安定なテストと CI の失敗」の決まりで扱う
- [x] 対応: B4 の共通の完了の条件、基盤の設計の R-02、`project.md` の Mandated

### Step 42: E2E（B4 の統合の前に手元で）

- [x] 事前に `docker compose --profile mail up -d mailpit` で Mailpit を起動し（`/api/v1/info` が 200）、`npx playwright install chromium` が済んでいることを確かめる。報告のディレクトリが無いことを確かめる
- [x] `caffeinate -i ./gradlew e2eTest` を流す（いまある 010〜130 のすべて。U3 は E2E の流れを足さない）
- [x] `frontend/test-results/e2e-results.json` の `stats` とファイルごとの結果を記録してから、`frontend/playwright-report/`・`frontend/test-results/` を中を開かずに消し、消したことと共有していないことを記録する（`gate-decisions.md` の U5 R-02 の決定）
- [x] 失敗したら原因を直してから Step 41 からやり直す。Mailpit は見終わったら止める（もとから動いていたときはそのまま）
- [x] 対応: Q1 A（I-D3）、`cicd-pipeline.md` 5節

### Step 43: 記録、コミットの提案、統合（B4）

- [x] `generation-notes.md`（B4 の節）、`code-summary.md`（作ったもの・変えたもの、Step 1・15・17・41 の実測、外したパッケージと値、時間と CI の見込み、E2E の結果と報告を消したこと、再現の確かめ、上流との差、依頼者の決定、承認の場で確かめること）、`source-manifest.json`（B3・B4 で作った・変えたアプリのソースとテストのパスすべて）、`traceability.json`（U3 の受け入れ基準・決まり・NFR）を仕上げる。記録のコミット R4 を提案する
- [x] 3.3 の C4〜C8 の区切りで、コミットの内容とメッセージの案を依頼者に示して承認を得る
- [x] 関門の記録を書き、R5 を提案する。依頼者の承認を得て 3.2 の手順で `develop` へ squash の1コミットで統合し、記録のコミットを作り、作業ブランチを消す。プッシュは依頼者が行う
- [x] 依頼者のプッシュの後、CI の `verify` が通ることと、その時間を確かめる
- [x] 対応: 段の記録、`project.md` の Change Control、`team.md` の Way of Working

## 6. ストーリー・要件と手順の対応

| ストーリー・要件 | 受け入れ基準・決まり・NFR | Bolt | 手順 |
|---|---|---|---|
| US1.1 利用者の一覧を見て、利用者を探す（主） | AC1.1.1（11 項目・並び・20 件）・AC1.1.7（停止中・初期管理者を含む） | B3 | Step 6〜11 |
| US1.1 | AC1.1.2・AC1.1.3（ロックの判定の3つ、失敗回数を出さない） | B3 | Step 4〜11 |
| US1.1 | AC1.1.4（部分一致・大文字小文字・特殊文字） | B3 | Step 3・5・7・11 |
| US1.1 | AC1.1.5（page と q の誤り、最後のページより後） | B3 | Step 8〜11 |
| US1.1 | AC1.1.6（漏えい） | B3 | Step 4・10〜12・14 |
| US1.1 | AC1.1.13（認可、監査なし） | B3 | Step 11 |
| US1.1 | AC1.1.8〜AC1.1.12（画面） | — | U5（Deferred） |
| US2.1 管理者の印を付ける・外す（主） | AC2.1.1・AC2.1.2（成功・トークンに触れない） | B4 | Step 30〜35 |
| US2.1 | AC2.1.3〜AC2.1.5・AC2.1.10（拒否の順・監査・いない ID） | B4 | Step 26・27・30〜35 |
| US2.1 | AC2.1.6・AC2.1.12（同時の重なり） | B4 | Step 32 |
| US2.1 | AC2.1.7（要求の改ざん） | B3・B4 | Step 11・34 |
| US2.1 | AC2.1.11（業務処理の層を直接呼ぶ） | B4 | Step 32 |
| US2.1 | AC2.1.14（認可） | B4 | Step 34 |
| US2.1 | AC2.1.8・AC2.1.9・AC2.1.13（画面） | — | U5（Deferred） |
| US3.1 利用を止める・停止を解く（主） | AC3.1.1・AC3.1.2・AC3.1.10（止める・解く・トークン・ロックを変えない） | B4 | Step 30〜34 |
| US3.1 | AC3.1.3・AC3.1.4・AC3.1.6（拒否・監査・いない ID） | B4 | Step 30〜35 |
| US3.1 | AC3.1.5（互いに止める）・AC3.1.9（直接呼ぶ） | B4 | Step 32 |
| US3.1 | AC3.1.12（認可） | B4 | Step 34 |
| US3.1 | AC3.1.7・AC3.1.8・AC3.1.11（画面） | — | U5（Deferred） |
| US3.2（従。主は U1） | 止める操作の結合の確かめ（AC3.2.1〜AC3.2.10 は U1） | B4 | Step 32・34（U1 に Deferred） |
| US4.1 ロックを解く（主） | AC4.1.1〜AC4.1.4・AC4.1.7・AC4.1.8・AC4.1.10 | B4 | Step 30〜34 |
| US4.1 | AC4.1.5（ログインとの重なり） | B4 | Step 32 |
| US4.1 | AC4.1.6（いない ID の監査） | B4 | Step 35 |
| US4.1 | AC4.1.11（上限切れ） | B4 | Step 32・34・36 |
| US4.1 | AC4.1.12（認可） | B4 | Step 34 |
| US4.1 | AC4.1.9（画面） | — | U5（Deferred） |
| US5.1 利用者の氏名・言語を直す（主） | AC5.1.1・AC5.1.4〜AC5.1.6・AC5.1.8 | B3 | Step 4〜11 |
| US5.1 | AC5.1.2・AC5.1.3・AC5.1.7（画面） | — | U5（Deferred） |
| 一覧と検索 | BR1.1〜BR1.9 | B3 | Step 3〜12 |
| 拒否の判定 | BR2.1〜BR2.6 | B4 | Step 26・27・30〜34 |
| 拒否の判定 | BR2.7・BR2.8 | B3・B4 | Step 10・11・33・34 |
| 排他と最後の管理者 | BR3.1〜BR3.6 | B4 | Step 28〜32 |
| 5つの操作 | BR4.1〜BR4.6 | B4 | Step 30〜34 |
| 氏名と言語 | BR5.1〜BR5.4 | B3 | Step 4〜11 |
| 監査 | BR6.1〜BR6.4 | B4 | Step 26・27・30・35 |
| 認可・改ざん・漏えい・構造 | BR7.1・BR7.4〜BR7.6 | B3・B4 | Step 10〜14・33〜37 |
| 認可・改ざん・漏えい・構造 | BR7.2・BR7.3 | B4 | Step 34・37 |
| 認可 | NFR1.1・NFR1.3 | B3・B4 | Step 11・34 |
| 印の切り替え・確かめ直し・改ざん | NFR1.2・NFR1.4・NFR1.5 | B3・B4 | Step 11・32・34 |
| 漏えい | NFR3.1〜NFR3.4 | B3・B4 | Step 12・19〜25・36 |
| 排他・保護・行き詰まり | NFR4.1〜NFR4.5 | B4 | Step 28〜32・40 |
| 性能（回数） | NFR5.2 | B3 | Step 11 |
| 性能（測定） | NFR5.1・NFR5.3〜NFR5.8・NFR6.2・NFR6.3 | B4 | Step 39（測定は performance-validation、Deferred） |
| 観測 | NFR5.9・NFR5.10・NFR5.11 | B4 | Step 40（確かめは observability-setup、Deferred） |
| 接続の数の見積もり | NFR6.1 | B4 | Step 38（README）・39 |
| 多言語 | NFR8.1 | B3・B4 | Step 11・34 |
| 入力と静的解析 | NFR9.1・NFR9.2 | B3・B4 | Step 7・11・15・40・41 |
| 監査 | NFR9.3〜NFR9.5 | B4 | Step 32・35 |
| カバレッジ・必須テスト・性質ベース | NFR9.6〜NFR9.8 | B3・B4 | Step 5・15・27・38・41 |
| 移行 | NFR10.1・NFR10.2 | B4 | Step 40（移行なし） |
| 構造 | NFR11.1・NFR11.2 | B3・B4 | Step 13・37 |
| 既存の経路の漏えいの直し | `security-design.md` 7.2（E1〜E4）・I-D1 | B4 | Step 19〜25 |
| 申し送り | 2.1・2.2 の表 | B3・B4 | 各表の手順 |

## 7. テストの量（Standard）と必須テストとの対応

部品ごとに 5〜8 件の単体テストと、主な境界の結合テストを置く（Testing Contract の `strategy_volume`）。どのテストも成功の場合と、少なくとも2つの失敗・境目の場合を含める（`phases/construction.md` の Testing Standards）。

| 部品 | 単体（`*Test`） | 結合（`*IT`） | Bolt |
|---|---|---|---|
| 検索の文字・氏名と言語の値（`user.domain`） | `SearchTextTest`（6〜8 件と性質 2）・`ProfileValidationTest`（5〜7 件） | — | B3 |
| ロックの判定（`auth.domain`） | `LockViewTest`（6〜8 件と性質 1） | — | B3 |
| code（`useradmin.domain`） | `UserAdminProblemTypesTest`（2 件 → B4 で 3〜4 件） | — | B3・B4 |
| 一覧の問い合わせ・氏名と言語の更新（`user.repository`） | — | `UserAdminQueriesIT`（8〜10 件 → B4 で 3 件足す） | B3・B4 |
| ロックの状態の読み取り（`auth.repository`） | — | `LoginAttemptStateRepositoryIT`（1〜2 件） | B3 |
| C8 の一覧と氏名（`user.service`）・ロックの判定の口（`auth.service`） | `UserAccountServiceTest`（5〜6 件）・`LockAdministrationServiceTest`（3〜4 件） | `UserAdminAccountIT`（2〜3 件） | B3 |
| 一覧と氏名の業務処理（`useradmin.service`） | `UserAdminServiceTest`（6〜8 件）・`UserAdminProblemTypeCatalogTest`（1 件） | — | B3 |
| web（一覧・氏名と言語） | `SearchTextConverterTest`（3 件）・`UserAdminWebTypesTest`（3 件）・`UserAdminRequestContextResolverTest`（3 件） | `UserAdminListApiIT`（12〜15 件）・`UserAdminListQueryCountIT`（2〜3 件）・`UserAdminProfileApiIT`（10〜12 件） | B3 |
| 漏えい | — | `UserAdminSecretLeakIT`（B3 で 2〜4 件、B4 で 4〜6 件足す） | B3・B4 |
| 構造 | `UserAdminBoundaryArchitectureTest`（B3 で 4 件、B4 で 3 件足す） | — | B3・B4 |
| 排他の失敗の判定（`common.persistence`） | `RowLockFailuresTest`（6〜8 件）・`RowLockUnavailableExceptionTest`（2〜3 件） | — | B4 |
| E1〜E4（`auth.repository`・`invitation.repository`） | `LoginAttemptStateRepositoryTest`（5〜6 件）・`InvitationLockQueriesImplTest`（5〜6 件） | `LoginAttemptStateRepositoryIT`（3〜4 件）・`InvitationRepositoryIT`（3〜4 件） | B4 |
| 中央の手当て（`common.observability`・`common.error.web`） | `LockFailureSafeTraceInterceptorTest`（5〜6 件）・`GlobalExceptionHandlerTest`（2 件） | `AuthLockTimeoutLeakIT`（3〜6 件）・`InvitationLockTimeoutLeakIT`（4〜8 件）・`MePreferencesLockTimeoutLeakIT`（1〜2 件） | B4 |
| 拒否の判定と監査のドメイン | `RejectionPolicyTest`（6〜8 件と性質 3）・`UserAdminAuditEventTest`（2 件）・`AuditEventFactoryTest`（3〜4 件） | — | B4 |
| 排他の口（`user.repository`・`user.service`・`auth.service`） | `UserRowLockRepositoryTest`（5〜6 件）・`UserAccountServiceTest`（3〜4 件）・`LockAdministrationServiceTest`（4〜5 件）・`LoginServiceTest`（2 件） | `UserRowLockRepositoryIT`（5〜7 件）・`UserAdminLockPortsIT`（2〜3 件）・`FailureResetPortIT`（2 件） | B4 |
| 5つの操作（`useradmin.service`・`audit.service`） | `UserAdminServiceTest`（10〜14 件）・`AuditEventListenerTest`（1 件） | `UserAdminOperationsIT`（4〜6 件）・`UserAdminConcurrencyIT`（5〜7 件）・`ResetLoginConcurrencyIT`（2 件）・`SuspendWhileLoginIT`（1〜2 件） | B4 |
| web（5つの操作） | — | `UserAdminOperationsApiIT`（15〜20 件）・`UserAdminResetLoginFailuresApiIT`（5〜7 件）・`UserAdminBusyApiIT`（5 件）・`UserAdminMassAssignmentIT`（2〜3 件） | B4 |
| 監査 | — | `UserAdminAuditIT`（8〜12 件）・`UserAdminAuditWriteFailureIT`（2 件） | B4 |

`team.md` の Testing Posture の必須テスト（U3 に当たるもの）との対応:

| 必須のテスト（★は設計で決まった値） | 確かめるテスト |
|---|---|
| 管理者の印の変更: 付けた直後・外した直後の次の要求で 403／200 が切り替わる。★外す前に出したトークン → 無効にしない（外した後の管理の API は 403）、★自分の印を外す → 409 `USER_ADMIN_SELF_OPERATION` | `UserAdminOperationsApiIT` |
| ロックの解除: 解除の直後に正しいパスワードで入れる、解除の後の失敗回数の境界、行が無い利用者。★解除の後の失敗回数 → 0 から数える、★行が無いとき → 409 `USER_ADMIN_NO_CHANGE`（行を作らない）。時刻は注入した時計 | `UserAdminResetLoginFailuresApiIT`・`ResetLoginConcurrencyIT`・`LockAdministrationServiceTest` |
| 最後の管理者の保護: 拒否と状態が変わらない、2人の同時の操作で 0 人にならない（待ち合わせ）。★数え方 → 印ありで停止していない、ロック中も数える | `UserAdminOperationsIT`・`UserAdminConcurrencyIT`・`RejectionPolicyTest` |
| 管理の API の認可: 7つの API で 401・403・200（204）、停止中の管理者は呼べない | `UserAdminListApiIT`・`UserAdminProfileApiIT`・`UserAdminOperationsApiIT` |
| 要求の改ざん: 管理の API の外から印・状態を変えられない | `UserAdminMassAssignmentIT`・`UserAdminProfileApiIT` |
| 管理の操作の監査: 操作ごとに操作した人・対象・結果。★拒否した操作 → 業務の拒否と確かめ直しの 403 は記録、BUSY・入力の誤り・認可の入口は記録しない | `UserAdminAuditIT`・`UserAdminAuditWriteFailureIT` |
| 利用者の管理の漏えい: 応答と監査の行にハッシュ値・トークン・ロックの内部の値が無い。TRACE で一覧・詳細を読んでもメールアドレス・氏名がログに出ない | `UserAdminSecretLeakIT`（TRACE と INFO）・`UserAdminListApiIT` |
| 認可（共通）: 未認証 401・管理者でない 403・管理者 200 | 上と同じ |
| 秘密情報の漏えい（共通）: ログ・監査ログにパスワード・トークンが無い | `UserAdminSecretLeakIT`・`AuthLockTimeoutLeakIT`・`InvitationLockTimeoutLeakIT`・`MePreferencesLockTimeoutLeakIT` |
| 利用停止（主は U1）: 止める操作の後に3つの入口で拒否 | U1 のテスト（B1）。U3 は `UserAdminOperationsApiIT` でリフレッシュトークンの拒否を確かめる |

性質ベースのテスト（jqwik）は、純粋な関数の `LockView` の判定（B3）・`SearchText` のパターン（B3）・`RejectionPolicy#decide`（B4）に当てる（NFR9.8）。

## 8. この計画で決めたこと・承認済みの文書との差

承認済みの文書は書き換えず、差をここと `code-summary.md` に記録する（`project.md` の決まり）。

| ID | 対象 | 承認済みの形 | この計画での扱い | 理由 |
|---|---|---|---|---|
| D-1 | 一覧の投影の型（`security-design.md` 4節の表、`performance-design.md` 2.1） | `user.repository` は投影で `UserAdminSummary`（`user.service` の型）を直接作る | `user.repository` に投影の record `UserAdminRow`（`toString` で伏せる）を置き、`UserAccountService` が `UserAdminSummary` に写す（9節の Q-A） | `UserAdminSummary` を repository の問い合わせの中で作ると `user.repository` が `user.service` に依存し、パッケージの依存が循環する。今の repository は service に依存していない。読む列と伏せ字の決まりは変わらない |
| D-2 | 負荷の試験の台本と手順書の持ち主（`cicd-pipeline.md` 4節、NFR5.7） | 台本と手順書と `k6 inspect` は Build and Test | この計画の依頼のとおり B4 で書き、`k6 inspect` で読み込みまで確かめる（Step 39）。Build and Test は `k6 inspect` を流し直して確かめる（9節の Q-D） | 依頼（コード生成の計画に台本と手順書の用意を入れる）。測定の持ち主（performance-validation）は変わらない |
| D-3 | B4 の E2E の範囲（`cicd-pipeline.md` 5節） | 010〜100 | いまある 010〜130 のすべて | B2（U4）で 130 が足されたため。流れの本数は増えない |
| D-4 | 失敗回数を戻す2段目（`logical-components.md` 1節の `LoginAttemptStateRepository`「0・無しの明示の更新」） | 0・無しの明示の更新 | 新しいメソッドを足さず、既存の `update(id, 0, null)`（明示の更新1回）を使う | 同じ問い合わせが既にあり、行を作らない |
| D-5 | README に足すこと（`infrastructure-specification.md` 9節） | 監査ログ・既知の制約の2節に足す、API のアクセス制御は変えない | 加えて「利用者の管理の API（Intent 260930-user-admin の U3）」の節を足す（B3 で一覧と氏名・言語、B4 で5つの操作） | 招待（U3）・プリファレンス（U2）と同じく機能ごとの節を持つ README の形にそろえる |
| D-6 | 書き込みの問い合わせの上限切れの起こし方（`infrastructure-specification.md` 6.2 の確かめ） | 例: 管理の操作が行を持つ間のパスワードの変更 | 別の接続で行を `FOR UPDATE` で持ち続けて起こす（表示の設定の保存とトークンの更新の2つの経路）。管理の操作で持つ形は使わない | 管理の操作の待ち合わせの口は書き換えの前にあり、どの行をいつ持つかを確実に作れるのは別の接続のため。中央の手当ては経路によらない |
| D-7 | テストの手伝いの置き場 | `useradmin/testsupport`・`auth/testsupport`（`logical-components.md` 6節） | `useradmin` のテストは `auth/testsupport` の `H2SessionWaits`・`MutableClock`・`SqlStatementCounter`・`TestUserSuspension`・`AuthApi` をそのまま使う（`useradmin` は本体でも `auth` に依存してよいため）。別の接続で行を持つ手伝いの置き場は Step 25 で決める（4.4） | U1 は機能の間をまたがないため手伝いをまとめなかった（U1 の Q-A）。U3 は本体の依存の向き（`useradmin` → `auth`）と同じ向きの使い方になる |
| D-8 | 部品の名前（`logical-components.md` の「例」） | 例として示した名前 | 4節のとおり決めた: `LockAdministrationService`（C8 の Authentication の口）、`UserRowLockRepository`、`common.persistence` の `RowLockFailures`・`RowLockUnavailableException`・`RowLockAttempt`、`InvitationLockQueries`（断片）、`LockFailureSafeTraceInterceptor`、`UserAdminBarrier`・`LoginAttemptBarrier`、`UserAdminAuditFailure` | 設計が名前の決定を計画に任せた |
| D-9 | 監査の結合テストの置き場 | （設計に無い） | `audit/service` に置く（`audit/testsupport` の手伝いを機能の間をまたがずに使うため） | テストの手伝いの置き場の決まり |
| D-10 | ログのレベルの切り替え | TRACE と INFO の両方で確かめる | 1つの Spring の文脈の中で `LoggingSystem` でロガーのレベルを切り替える。切り替えが追跡に効かないときは2つのクラスに分け、差を記録する | 文脈の数を増やさず `verify` の時間を抑える（4.6） |

## 9. 依頼者の決定

計画の承認の前に諮った論点について、依頼者が次のとおり決めた。計画の各 Step と8節はこの決定に合わせてある。

**Q-A 一覧の投影の型をどこに置くか**（8節の D-1、承認済みの設計との差）

- 依頼者の答え: **A**
- 決定: `user.repository` に投影の record `UserAdminRow`（`toString` で伏せる）を置き、`UserAccountService` で `UserAdminSummary` に写す。パッケージの依存は循環しない（4.1、Step 3・6・8）
- 選ばなかった案: B（設計のとおり repository の問い合わせで `UserAdminSummary` を直接作る。`user.repository` → `user.service` の依存が生まれる）、C（`UserAdminSummary` を `user.domain` に移す）

**Q-B B3 の終わりの記録の形**

- 依頼者の答え: **A**
- 決定: B3 の終わりに `code-summary.md`・`source-manifest.json`・`traceability.json` の B3 までの版を書き（B4 で受け持つ受け入れ基準は `Deferred`、持ち主 B4）、B4 の終わりに仕上げる（Step 16・43）
- 選ばなかった案: B（B3 の終わりは `generation-notes.md` だけにする）

**Q-C B4 の verify の時間の許容**（基盤の設計の R-02、4.6）

- 依頼者の答え: **A**
- 決定: 手元の `verify` が B3 の基準から 5 分以内の増加、かつ CI の実行が 30 分以内。超えたら止めて依頼者に諮る（Step 41）
- 選ばなかった案: B（実測を記録するだけ）、C（ほかの値）

**Q-D 負荷の試験の台本と手順書**（8節の D-2）

- 依頼者の答え: **A**
- 決定: B4 で書き、`k6 inspect` で読み込みまで確かめる。Build and Test は流し直して確かめる（Step 39）
- 選ばなかった案: B（基盤の設計のとおり Build and Test で書く）

**Q-E 検索の既知の差（`İ`・`ß`、R5）をテストに固定するか**

- 依頼者の答え: **A**
- 決定: 決めた側の動作として1件ずつ結合テストに固定する（Step 7）
- 選ばなかった案: B（固定せず README に書くだけ）

**Q-F 既存の経路の漏えいの直しの「再現」の確かめ方**（`project.md` の Mandated）

- 依頼者の答え: **B**
- 決定: テストが通った後に、直しの本体だけを一時的に元に戻してテストが落ちることを確かめ、落ちた件数と出た値の種類だけを記録してから戻す。値そのものは記録しない（Step 25）
- 選ばなかった案: A（コードの読み合わせで確かめるだけ）

**Q-G ダミーの行がすべて排他されたときのログイン（E1 の代わりの道）を漏えいのテストに入れるか**

- 依頼者の答え: **A**
- 決定: 入れる。別の接続でダミーの行の8つをすべて持ち、存在しないメールアドレスでログインして 500 とログに値が無いことを確かめる（Step 25）
- 選ばなかった案: B（入れない）

**Q-H 一意の制約の違反の例外の文に値が入りうる件**（計画を書く中で気づいた、未検証の既存の危険）

- 中身: H2 の一意の制約の違反（誤りの番号 23505）の例外の文には、重なった値（例: メールアドレス）が入りうる。その例外が repository の層のメソッドの外へ出ると、TRACE のときに `TraceAspect` がその文を出しうる（例: 利用者の作成・招待の追記で一意の制約に当たったとき）。値が実際に入るか・どの経路で出るかは確かめていない
- 依頼者の決定: **今回は直さず、記録して後の Intent へ回す（残る危険）**。この計画の Step は足さない。B4 の中央の手当て（Step 23）は排他の失敗の連なりだけを対象にし、一意の制約の違反の例外は対象にしない
- 記録の先: 「Build and Test に引き継ぐこと」の表と、`code-summary.md` の残る危険

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

## Build and Test に引き継ぐこと

| 項目 | 内容 | 持ち主 |
|---|---|---|
| カバレッジの実測 | B4 の統合の後の `develop` で `:backend:cleanTest :backend:cleanIntegrationTest` を付けた verify で、4.5 のパッケージの値を測り直す | Build and Test |
| verify の時間と CI | B3・B4 の前後の時間と、CI の 60 分に対する余裕（4.6、Q-C） | Build and Test |
| CI | 依頼者のプッシュの後、CI が通ることを確かめる。失敗したら `team.md` の決まりで扱う | Build and Test |
| 負荷の試験の台本 | `k6 inspect --include-system-env-vars` を流し直して読み込みと場面の名前を確かめる（Q-D） | Build and Test |
| 性能と接続プール | NFR5.1・NFR5.3〜NFR5.6・NFR6.2・NFR6.3（1,000 名の一覧、5つの操作、止める操作の悪い側、上限 30・上限 10）。上限 10 の場面の条件と BUSY の件数の扱い（NFR 要件のレビューの R-07・R-08） | performance-validation |
| 観測 | 7つの API の `uri`・`le` の実際の値、拾うと書いた既存の警報が鳴ること、監査の種類と理由の名前（B4 の後の定義で） | observability-setup |
| 戻しの条件 | 戻し先の版は停止の列を知らず、戻している間は停止中の利用者が3つの入口を通れる。戻す前に停止中の利用者の件数を確かめ、いれば扱いを依頼者に確かめる手順（I-D4、U1 R-03 と同じ条件）。戻し先のイメージのコミットで監査の行を読む本番のコードが無いことの再確認 | deployment-pipeline |
| スモークテスト | 監査に残る要求（5つの操作）は送る前に依頼者に伝える。初期管理者だけでは最後の管理者の拒否（`LAST_ACTIVE_ADMIN`）を見せられない（自分自身の操作は先に `SELF_OPERATION`） | deployment-pipeline |
| SLO | 判定は Unverified（NFR5.11） | observability-setup・performance-validation |
| 画面 | AC1.1.8〜AC1.1.12・AC2.1.8・AC2.1.9・AC2.1.13・AC3.1.7・AC3.1.8・AC3.1.11・AC4.1.9・AC5.1.2・AC5.1.3・AC5.1.7、8KB を超える要求の HTML の 400 の画面の扱い | B5（U5） |
| 一意の制約の違反の例外の文（残る危険、9節の Q-H） | H2 の一意の制約の違反（23505）の例外の文に重なった値（メールアドレスなど）が入りうり、repository の外へ出ると TRACE の `TraceAspect` が出しうる（未検証）。今回は直さない | 後の Intent |
| `ms-pool-pending` の式の見直し | 時間切れの累計を見る式への見直し（NFR5.10 の申し送り） | 配備先が決まったとき |
