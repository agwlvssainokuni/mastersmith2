# コード生成の計画（Code Generation Plan）— U6 役割・権限の管理の画面（role-admin-ui）、Bolt B8

U6 role-admin-ui は ui の単位で、管理者がロール・グループ・権限・割り当てを整える画面と、権限の受け渡し（YAML）の画面を作る。

- roleadmin（新しい機能）: S3 ロールの一覧 `/admin/roles`、S4 権限の設定 `/admin/roles/:roleId`、S5 割り当て `/admin/roles/:roleId/assignments`
- groupadmin（新しい機能）: S6 グループの一覧 `/admin/groups` と詳細 `/admin/groups/:groupId`
- roletransfer（新しい機能）: S7 権限の受け渡し `/admin/role-transfer`
- useradmin（既存の機能を広げる）: S9 行の操作「ロールを見る」と読み取りの Modal
- 共有の変更: 画面の入口 `frontend/src/main.tsx` を data router に替える（L11）、`ApiDownload` に `headers` を足す（L10）、名前の画面の検査 `validateAdminName`（L9）
- E2E: U6 の画面の検査（140、本数に数えない）と F の流れ（150、この Intent の2本までの1本）、差し替えの口 `adminApiRoute.ts` に直列化済みの本文の形を足す（L13）

バックエンド・移行・Gradle・Vitest と Playwright の設定・依存（`package.json`・`package-lock.json`）は変えない。方法は test-after（下の Testing Contract のとおり）。Test Strategy は Standard、範囲は classic。

この計画は Part 1（計画）だけで、コードはまだ書いていない。手順はすべて未実施（`[ ]`）である。末尾の Testing Contract（`contract_sha256` は `sha256:0800cfde…7349`）が Part 2 の方法と順の正である。計画の段で見つかった7つの点は、依頼者が答え（すべて A）、13節に確定の形で書いた。

## 1. 入力にした設計

すべて `aidlc/spaces/default/intents/261004-role-menu/` の下にある。

| 種類 | 文書 |
|---|---|
| 機能設計 | `construction/role-admin-ui/functional-design/` の `functional-spec.md`（D1〜D32・W3.1〜W9.1・4節の状態の遷移・5節の code の表・7節の未保存の確かめ・8節のテストの方針）・`frontend-components.md`（部品・props・型・文言）・`traceability.json`。末尾の「承認の場の決定と直し」（R-01）を含む |
| NFR 要件 | `construction/role-admin-ui/nfr-requirements/` の `security-requirements.md`（NFR1.1・NFR1.4〜NFR1.12）・`performance-requirements.md`（NFR2.2・NFR2.4〜NFR2.13）・`tech-stack-decisions.md`（NFR4.1〜NFR4.7・NFR6.1〜NFR6.10、9節の承認の場の決定 R-01） |
| NFR 設計 | `construction/role-admin-ui/nfr-design/` の `logical-components.md`（L1〜L13）・`performance-design.md`（9節の承認の場の決定 R-01・R-02）・`security-design.md` |
| 基盤の設計 | `construction/role-admin-ui/infrastructure-design/` の `infrastructure-specification.md`・`cicd-pipeline.md`（verify の段・入口の差し替えの確かめ・E2E の 140・150・7節の B8 で確かめること）・`monitoring-design.md` |
| 読み直しの記録 | `.aidlc-reviews/` の role-admin-ui の機能設計（2回）・NFR 要件（2回）・NFR 設計（2回）・基盤の設計（1回） |
| Inception | `inception/units-generation/unit-of-work.md`（U6）・`unit-of-work-story-map.md`（US1.1・US1.2・US2.1・US2.2・US3.1・US5.3）、`inception/requirements-analysis/requirements.md`（FR1〜FR4・FR7・FR11、NFR1・NFR4・NFR6）、`inception/user-stories/stories.md`、`inception/refined-mockups/`（S3〜S7・S9・10a 節）、`inception/contract-design/contract-summary.md`（C2・C6・C7）、`inception/domain-design/components.md`、`inception/delivery-planning/bolt-plan.md`（B8） |
| 使う API の実装（読み取りだけ） | `backend/src/main/java/cherry/mastersmith/group/web/`（B3）と `role/web/`（B4〜B6）の DTO・道・code、`construction/{group,role}/code-generation/code-summary.md` |
| 先の Bolt の記録（読み取りだけ） | `construction/{cross-cutting,dsl-v2,navigation}/code-generation/code-summary.md`（共有の木 `src/shared/tree`、登録の型、ESLint の制限、`DslErrorList` の 360px の直し D-20・D-21） |
| 既存のコード（読み取りだけ） | `frontend/src/main.tsx`・`app/App.tsx`・`app/routing/AppRouter.tsx`・`decideRoute.ts`・`app/registry/`・`app/admin-forbidden/`・`app/testing/renderWithProviders.tsx`・`shared/`・`features/useradmin/`・`features/dsl/`・`e2e/`・`eslint.config.js`・`vitest.config.ts`・`playwright.config.ts`・`scripts/check-bundle-size.mjs`、`vendor/make-you-chic-ui`（固定先 e82b651 の Table・Tabs・Modal・Dropdown・Select・Alert・Badge・Button のソース） |
| 決まりの層 | `aidlc/spaces/default/memory/` の `org.md`・`team.md`・`project.md`・`phases/construction.md` |

## 2. 承認の場の決定と、この計画での読み方

### 2.1 承認の場の決定

監査ログ（`audit/sakura-local-4e42a93f87ce.md`）の Construction の設計の段の GATE は次のとおり。決定の文は短いため、中身は各文書の末尾の「承認の場の決定と直し」の節で読んだ。基盤の設計の承認の場の決定 (4)(5) は、監査ログには `User Input: Approve` だけが残り、決定の本文は指揮役から伝えられた（group の計画 2.1 の決定 (2) と同じ扱い）。

| 段 | GATE | 決定の文 | U6 に当たる中身（出典） | この計画での扱い |
|---|---|---|---|---|
| 機能設計（2026-10-05） | GATE_REJECTED → GATE_APPROVED（Approve） | 推奨の案のとおり直す | R-01: ロール1件を読む `GET /api/admin/roles/{roleId}` を role（B4）の設計に足した。権限の木の API はスキーマ名・テーブル名を問い合わせの引数で受ける形に変わった（`functional-spec.md` 13節） | どちらも B4 で作られていた（`RoleAdminController.detail`、`RolePermissionController` の `schema`・`table`）。2.3 で突き合わせた。D32 は切り替え先なしで作る（Step 14） |
| 機能設計（まとめの確認） | Looks correct | Q1 A・Q2 B・Q3〜Q6 A | Q2 B: data router と `useBlocker`（使えなければ 7.3 の形）。Q6 A: E2E は B8 で F の流れの前半を書き、B9 が同じファイルに足す | Step 4・5（入口の差し替えと確かめ）、Step 25（150） |
| NFR 要件（2026-10-05） | GATE_REJECTED → GATE_APPROVED（Approve） | Major 11 件だけを直す | R-01: F の流れのファイルは始めに自分で版 2 の DSL を管理の API で投入して適用する。前のテストの状態に頼らず、後始末はしない。番号は 040 より後（`tech-stack-decisions.md` NFR6.3・9節） | 150 の始めに `E2EF_` の DSL を `POST /api/admin/dsl/preview?source=PASTE` と `POST /api/admin/dsl/apply`（本文 `{previewId}`）で適用する（Step 25） |
| NFR 要件（まとめの確認） | Looks correct | Q1 A・Q2 A | Q1 A: 画面の時間は 140 の中の測りのテストで記録だけ。Q2 A: 書き出しの上限超えは応答のヘッダー `X-Role-Transfer-Exceeds-Import-Limit` で知る（`ApiDownload` に `headers` を足す） | Step 6（L10）、Step 24（測り） |
| NFR 設計（2026-10-06） | GATE_REJECTED → GATE_APPROVED（Approve） | 推奨の案のとおり直す | R-01: 測りの見本は前もって `JSON.stringify` した文字列にし、差し替えの口は `body` をそのまま `route.fulfill` に渡す（`adminApiRoute.ts` に `body: string` の形を足す）。測り始めは `click`・`change` の捕捉の段か、要求の `responseEnd`。R-02: 時計は画面の中の `performance.now()` の1つ、判定は 5 回の中央値（最大・1回目も記録）（`performance-design.md` 9節） | Step 23（口の `body` の形）、Step 24（測りのテスト） |
| NFR 設計（まとめの確認） | Looks correct | Q1 A | 確かめのときに読んだ本文の文字列（最大 10 MiB）を画面の状態に持ち、適用でそれを送る。選び直し・適用の成功・画面を離れたときに捨てる | Step 18（`useRoleTransfer`） |
| 基盤の設計（2026-10-06） | GATE_APPROVED（Approve） | Approve（まとめの確認は Looks correct） | 設計の要点 1〜6（バックエンド・CI・Gradle・Vitest と Playwright の設定・依存・秘密を変えない、B8 の最初に入口の差し替えを確かめる、E2E の 140・150、量と時間は Build and Test で記録） | 3節・6節のとおり |
| 基盤の設計（承認の場の決定 (4)） | — | 指揮役から伝達 | **入口の量の判定は、前の値を先に取る**。B8 の変更の前（develop の B7 の統合の後、3a30982）に `frontendBuild` と `frontendBundleSize` を流し、入口の合計と、manifest の入口から静的な import をたどった集まりを記録する | Step 3（前の値）、Step 27（後の値と判定）。10節 |
| 基盤の設計（承認の場の決定 (5)） | — | 指揮役から伝達 | **新しい画面は入口の静的な import に入れず、manifest の `isDynamicEntry` で確かめる**。基盤の設計の読み直し R-01（「増えていない」では data router の分で Not Met になりうる）の手当て。app-frame-ui の基盤の設計 Q1 A と同じ形 | 判定は「`features/roleadmin`・`features/groupadmin`・`features/roletransfer` の画面の塊が manifest で `isDynamicEntry: true` で、入口からたどった静的な import の集まりに無く、`UserRolesDialog` も入口の集まりに無いこと（利用者の管理の画面の遅延読み込みの塊に入る）」。入口の合計の差は記録だけ（data router の分を含む）。10節 |

### 2.2 読み直しで残った指摘の扱い（承認の場で直していないもの）

| 指摘 | 中身 | この単位での扱い |
|---|---|---|
| 機能設計 R-02（Minor） | 画面と API の組の 403 の表のテストと、道の直接入力の `ADMIN_FORBIDDEN` のテストが 8.1 に無い | **手当てする**（NFR1.1 と `security-design.md` 1節で決まった形）。機能ごとに `*Forbidden.test.tsx`（パラメーターの表）と `registration.test.tsx`（`decideRoute`）を置く（Step 13・15・17・19・21） |
| 機能設計 R-03（Minor） | 画面の確かめがある AC（AC1.1.12・AC3.1.4・AC3.1.13 ほか）が Deferred のまま | **手当てする**。コード生成の `traceability.json` では、画面の確かめを持つ AC を「OK（部分）」にして画面のテストを target に書く（Step 28） |
| 機能設計 R-04（Minor）・NFR 設計 R-07（Suggestion） | 7.1・L11 の断片が今の `main.tsx` の前処理（`rewriteDesignSystemCopies`・`installLanguageResolver`・`startAppearanceLoad`）と `StrictMode` を落としている | **手当てする**。前処理の3行と `StrictMode` をそのまま残し、`createBrowserRouter` は前処理の後に作る（Step 4） |
| 機能設計 R-05（Suggestion） | S4 と S5 が同じ画面か、タブの切り替えの後のフォーカス | NFR4.7 で決着（同じ `RoleDetailPage` を2つの道に登録）。今の `AppRouter` を通しても作り直されないことを確かめるテストを足す（Step 15、5節 (e)） |
| 機能設計 R-06・R-07（Suggestion） | 知らない `reason` の文言、上限の値の差し込み | NFR1.8・NFR4.6 で決着。そのとおりに作る（Step 8・18） |
| 機能設計 R-08（Suggestion） | カラムが数百を超えたときの描画 | NFR2.12 で決着（分割しない、1,000 カラムは記録だけ） |
| 機能設計 R-09（Minor） | 権限の木の道の「直しの例の形」という条件つきの言い回し | **記録だけ**。2.3 で role の確定の形（`/permissions/tables?schema=`・`/permissions/columns?schema=&table=`）と一致を確かめたことを `code-summary.md` に書く。承認済みの文書は書き換えない |
| NFR 要件 R-02（Minor）・NFR 設計 R-03（Minor） | traceability の NFR6.4 の行の寄せ方・target がカバレッジの記述を指さない | **手当てする**（コード生成の `traceability.json` の側）。NFR6.4 の target は `vitest.config.ts` の `thresholds` と Step 22・29 の実測を指し、NFR6.5〜NFR6.8 は別の行に立てる（Step 28） |
| NFR 要件 R-03（Minor）・R-07（Suggestion） | 画面の時間の Not Met の扱い・NFR2.7 の測り始め | NFR 設計 4.3・4.4 で決着。そのとおりに測る（Step 24） |
| NFR 要件 R-04（Minor） | `check-bundle-size.mjs` は入口だけを数える | 基盤の設計の承認の場の決定 (4)(5) で決着（10節） |
| NFR 要件 R-05（Minor） | useradmin の既存のテストを直す作業と `apiClient.test.ts` への影響が要件の行に無い | **手当てする**。5節の (a)〜(c) に名指しで載せ、Step 7・21 で直す |
| NFR 要件 R-06（Suggestion） | 既存の `playwright-secret-check-reporter.ts` が新しい E2E に効くか | **手当てする**。150 は `recordSecretValues` で値を書き、Step 29 の E2E の全体の後に、報告の部品の結果（失敗 0）と json の報告の文字列の検索を記録する |
| NFR 要件 R-08（Suggestion） | E2E の番号の決め方の二重の言い方、見本の名前の重なり | **手当てする**（13節 Q1: A）。番号は 140・150（130・135 の後、040 の後）。DSL の見本の名前は接頭辞 `E2EF_`（040 の `e2e_dept`、U7 の `E2EI_` と重ならない） |
| NFR 設計 R-04（Minor） | 実際のブラウザの axe の範囲を設計として書いた節が無い | **手当てする**（この計画の 9節に 140 の構成を書き、`traceability.json` の NFR4.4・NFR4.5 の target を 9節と 140 のテストに向ける） |
| NFR 設計 R-05（Minor） | `transferLimits.test.ts` の「role の値と同じ」の比べる先 | **手当てする**。定数のテストは契約の値 10,485,760 を数で書いて比べ、コメントに role の `RoleTransferLimits.DEFAULT_MAX_BYTES` と設定 `mastersmith.role-transfer.max-bytes` を名指しする（変えるときは両方を同じ変更で直す、useradmin の `USER_SEARCH_MAX_CODE_POINTS` と同じ書き方）。サーバーの境界は role の `RoleTransferLimitsApiIT` が持つ |
| NFR 設計 R-06（Suggestion） | 確かめた本文の UTF-16 での最大約 20 MiB と置換文字による増え | **記録だけ**。`code-summary.md` の受け入れた制約に1行書く |
| NFR 設計 R-08（Suggestion） | E2E の番号の確かめが計画の手順に無い | **手当てする**。Step 1 で `frontend/e2e/` の今の番号と app-frame-ui の案（160・170）を突き合わせる |
| NFR 設計 R-09（Minor） | 口の `body` の形の `contentType` と 400 以上の扱い | **手当てする**（Step 23）。`body` の形は `status` を必須にし、`contentType` は `json` の形と同じ規則（400 以上は `application/problem+json`、それ以外は `application/json`）、`failedReplies` も同じに数える。省いたときの既定は置かない（型で必須） |
| NFR 設計 R-10（Minor） | NFR2.6 の回ごとに保存する値を変える扱い、終わりの条件の書き方 | **手当てする**（Step 24）。NFR2.6 は回ごとに保存する値を NONE・READ・FULL の順に替え、終わりの条件に保存した値を含める。終わりの条件は項目ごとの関数で書き、測り始めの記録は最初の1回だけを残す（app-frame-ui の guard にそろえる） |
| 基盤の設計 R-01（Major） | 入口の量の判定 | 承認の場の決定 (4)(5) で決着（2.1・10節） |
| 基盤の設計 R-02（Minor） | 140 で書き出しの応答のヘッダーを見本で返せない | **手当てする**（13節 Q2: A）。口に `headers` を足さず、書き出しの確かめは画面のテストだけにする |
| 基盤の設計 R-03（Minor） | 150 への追加と I の流れの関係 | app-frame-ui の基盤の設計で決着（F は 150 の1本、I は 170 の1本、B9 が 150 に足すのは同じ1本の中の手順） |
| 基盤の設計 R-04（Minor） | 150 の Mailpit のメールの扱い | 既存の決まり（`e2e/support/mailpit.ts` は GET だけ、メールは消さない、README の片付けの手順）のまま。app-frame-ui の基盤の設計と同じ。受信箱の件数に頼る E2E が無いことは Step 1 で検索して確かめる |
| 基盤の設計 R-05（Minor） | data router への差し替えの後の確かめ（ログアウトとログイン・深い道の読み込み直し・戻る進む） | **手当てする**。ログインとログアウトは既存の 020 が覆う。深い道の読み込み直し（`/admin/roles/{id}`・`/admin/groups/{id}`）と戻る・進むは 140 に「道の扱い」のテストを1件置く（Step 24） |
| 基盤の設計 R-06（Suggestion） | 入れ替えの後に開いたままの古いタブの遅延読み込みの失敗 | **記録だけ**。配備の段への引き継ぎ（12節）に「入れ替えの後は開いたタブを読み込み直す」をスモークテストの注意として書く |
| 基盤の設計 R-07（Suggestion） | group・role の監視の区画への参照の確かめ | **記録だけ**。Observability Setup へ引き継ぐ（12節） |
| 基盤の設計 R-08（Suggestion） | NFR2.13 の target | R-01 の決着に合わせ、コード生成の `traceability.json` の NFR2.13 の target を 10節と Step 27 に向ける |

### 2.3 使う API の実装との突き合わせ

画面の設計（`functional-spec.md` 1.2・`frontend-components.md` 3節）と、B3〜B6 で作った web の層の DTO・道・code を読み取りだけで照らした。依頼者が B4〜B6 の承認で受け入れた形（role の `code-summary.md` B4.5・B5.5・B6.5、group の 5節）も合わせて並べる。画面は実装の形に合わせて作り、承認済みの設計の文書は書き換えない。

| # | 項目 | 画面の設計 | 実装（読んだファイル） | 判定 | 画面での扱い |
|---|---|---|---|---|---|
| A1 | ロール1件 | `GET /api/admin/roles/{roleId}` → `{roleId, name, createdAt, updatedAt}`、404 `ROLE_NOT_FOUND` | `RoleAdminController.detail`・`RoleResponse` | 一致 | D32 のとおり |
| A2 | 権限の木の道 | `/permissions/schemas`・`/permissions/tables?schema=`・`/permissions/columns?schema=&table=` | `RolePermissionController`（引数の名前 `schema`・`table`） | 一致（機能設計の読み直し R-09 の条件つきの言い回しは解消） | 名前は `URLSearchParams` で引数にする |
| A3 | 木の応答の外側 | 「200 `PermissionNodes`」とだけ書き、外側の形が無い | `PermissionNodesResponse(items)`（`{items: [...]}`） | 設計の書き漏れ | 画面は `items` を読む。`items` が無い・配列でない応答は通信の失敗 |
| A4 | 木の節の `displayName` | 文字列 | Accept-Language で選んだ1つの文字列。今の DSL に無い節は物理名（B4-10、依頼者の答え A） | 一致 | D11 の継承の元の名前は、親の節の `displayName` を画面が持つ一覧（スキーマの一覧・テーブルの一覧）から引く |
| A5 | `inheritedFrom` | 実効の値ごとに継承の元を出す（D11。補助権限は「可」「不可」） | 主権限の継承の元だけ（B4-11、依頼者の答え A） | 補助権限の出どころが応答に無い | 13節 Q4: A。補助権限は「可」「不可」と、明示のときだけ「（明示）」を添え、主権限の `inheritedFrom` を流用しない |
| A6 | 保存の本文 | `{scope: {schemaName, tableName or null}, entries: [...]}` | `PermissionSaveRequest(scope: PermissionTargetRequest(schemaName, tableName, columnName), entries: PermissionEntryRequest(schemaName, tableName, columnName, main, create, delete))`。`scope.columnName` が null でなければ 400 | 一致（`scope` に `columnName` の項目がある） | `scope` は `schemaName`・`tableName` だけを送る（`columnName` は送らない） |
| A7 | 保存の範囲 | `scope` の外を含めない（D12） | スキーマの `scope` はそのスキーマの段の対象だけ、テーブルの `scope` はそのテーブルとそのカラム。範囲の外・重なり・空の `entries` は 400（B4-7、依頼者の答え A） | 一致（スキーマの範囲は「スキーマの段だけ」と狭い） | `toEntries` の性質に「スキーマの範囲ではスキーマの段の対象だけを返す」を足す（Step 9）。写しが空なら保存を押せない（D13）ため空の `entries` は送らない |
| A8 | 主権限の値 | `NONE`・`READ`・`FULL`・null | `main` は文字列、null は設定なし。知らない文字列は 400 | 一致 | Select の値は4つだけ |
| A9 | 割り当ての一覧 | `{users: [userId, displayName, email, suspended, sources], groups: [groupId, name]}`、出どころは `{kind: 'DIRECT'}` か `{kind: 'GROUP', groupId, name}` | `RoleAssignmentsResponse`、出どころは `RoleSourceResponse(type, groupId, name)` で `type` は `DIRECT`・`GROUP`（`RoleSourceResponse.from`） | 項目の名前が `type`（設計の型は `kind`）。role の `code-summary.md` B5-5 は「`USER`・`GROUP`」と書き、実装と食い違う | 画面は応答の `type` を読み、`DIRECT`・`GROUP` 以外は通信の失敗。画面の中の型は設計どおり `kind` に写す。記録の食い違いは 13節 Q5: A（実装を正とし、role の記録は書き換えず U6 の `code-summary.md` に根拠とともに書く） |
| A10 | 割り当ての本文の 400 | 一方だけを送る | 両方が無いとき `userId` の `REQUIRED`、両方あるとき `groupId` の `INVALID_VALUE`（B5-4、依頼者の答え A） | 一致 | 画面は一方だけを送るため届かない想定。届いたら D3 の汎用の文言 |
| A11 | 外しの相手がいない | W5.4・W5.5 に `ROLE_NO_CHANGE`・`GROUP_NOT_FOUND` | 存在しない利用者・グループの外しは 404 ではなく `ROLE_NO_CHANGE`（B5.5 の 3、依頼者の答え A「U6 の設計で扱う」） | 一致（`GROUP_NOT_FOUND` は外しでは届かない） | `ROLE_NO_CHANGE` は「すでに外れています」と読み直し。`GROUP_NOT_FOUND` の分岐は残す（害が無い） |
| A12 | 利用者・グループのロール | `[{roleId, name, sources}]`・`[{roleId, name}]` | `RoleLookupController` は配列をそのまま返す（外側の `items` は無い） | 一致 | 配列でない応答は通信の失敗 |
| A13 | グループ | 一覧 `{items: [groupId, name, memberCount, assignedRoleCount], page, size, total}`・詳細 `{groupId, name, members}`・作成 201 `Group` | `GroupPageResponse`・`GroupRowResponse`・`GroupDetailResponse`・`GroupMemberResponse`・`GroupResponse` | 一致 | — |
| A14 | 使われている削除の数 | `ROLE_IN_USE` の `assignedUsers`・`assignedGroups`、`GROUP_IN_USE` の `members`・`assignedRoles` | Problem Details の追加の項目（`RoleAdminController.ASSIGNED_USERS` など） | 一致 | 整数のときだけ読む（D9） |
| A15 | 権限の受け渡し | 書き出し・確かめ・適用、ヘッダー `X-Role-Transfer-Fingerprint`・`X-Role-Transfer-Exceeds-Import-Limit`、`TransferCheck`・`TransferResult`、`errors [{reason, line, column, path}]` | `RoleTransferController`・`TransferCheckResponse`（`added`・`changed`・`cleared`・`notInDsl` を含む）・`TransferResultResponse`・`TransferErrorItem`（`line`・`column` は null がありうる） | 一致 | `path` は点の形のまま出す（B6-3、依頼者の答え A）。`line`・`column` が null なら「—」 |
| A16 | `reason` の値 | 13 個 | `TransferError.Reason` の 13 個（`TOO_DEEP`・`TOO_MANY_ALIASES`・`TAG_NOT_ALLOWED`・`DUPLICATE_KEY`・`SYNTAX`・`UNSUPPORTED_VERSION`・`MISSING_KEY`・`UNKNOWN_KEY`・`INVALID_VALUE`・`INVALID_ROLE_NAME`・`DUPLICATE_ROLE`・`INVALID_TARGET_NAME`・`COLUMN_AUX_NOT_ALLOWED`） | 一致 | 13 個の文言を ja・en で持ち、知らない値は汎用（NFR1.8） |
| A17 | 本文の上限 | `MAX_TRANSFER_BYTES` 10,485,760 | `RoleTransferLimits.DEFAULT_MAX_BYTES = 10_485_760`（設定 `mastersmith.role-transfer.max-bytes`） | 一致 | 2.2 の NFR 設計 R-05 |
| A18 | 停止中の管理者 | 管理の API の 403 は骨組みへ | 停止中の管理者は 401 `AUTHENTICATION_REQUIRED`（group の G-14・role の D-34、依頼者の答え A） | 一致（画面の 403 の経路は変わらない） | 401 は ApiClient が更新を試み、だめならログインへ（既存） |
| A19 | 名前の規則 | 前後の空白を除き 1〜64 コードポイント・制御文字なし（D7） | `GroupName`: White_Space を除き 64 コードポイント、Cc と U+2028・U+2029 を拒み、Cf は拒まない（G-4、依頼者の答え A）。role の `RoleName` も同じ範囲（Step 1 で読んで確かめる） | 一致（「制御文字」は Cc と U+2028・U+2029） | `validateAdminName` は Cf を拒まない（`validateDisplayName` の Cc・Cf とは違う）。テストに U+200B（Cf）を受け付ける境界を入れる |
| A20 | 候補の検索 | `GET /api/admin/users?page=1&q=`（20 件） | 既存の useradmin の一覧の API。招待中の人を返さない、`suspended` を持つ | 一致 | 検索の文字は上限 254 コードポイント（`USER_SEARCH_MAX_CODE_POINTS`）を画面でも確かめる（13節 Q3: A。`shared/validation/searchText.ts` へ移した関数を使う） |

### 2.4 この計画での読み方（承認済みの文書との差。文書は書き換えない）

- **入口の量の判定**: NFR 設計 5節・基盤の設計 5節の「入口が B8 の前と比べて増えていない」ではなく、基盤の設計の承認の場の決定 (4)(5) の形（前の値を先に取り、新しい画面の塊が入口の静的な import に無く `isDynamicEntry` であることで判定。合計の差は記録だけ）で判定する（10節）。
- **応答の形**: 2.3 の A3（木の応答の `items`）・A6（`scope` に `columnName` を送らない）・A9（出どころの `type`）は実装の形に合わせる。
- **名前の画面の検査**: `frontend-components.md` の `validateAdminName` は「制御文字を含まない」とだけ書く。サーバーの規則（Cc と U+2028・U+2029、Cf は拒まない）にそろえる（2.3 の A19）。
- **`ConfirmActionKind` の型**: useradmin の行の操作の種類に `viewRoles` を足すと、`ConfirmActionKind = Exclude<RowActionKind, 'editProfile'>` に `viewRoles` が入り、確かめの Modal の表（`useUserAdmin.ts` の `OPERATIONS` など）が型の誤りになる。`Exclude<RowActionKind, 'editProfile' | 'viewRoles'>` に直す（`frontend-components.md` 6節の「`UserRowActions.tsx` は変えない」は保つ）。
- **E2E の番号**: 140（U6 の画面の検査、本数に数えない）・150（F の流れ）とする。130・135 の後、040 の後で、app-frame-ui の 160・170 と重ならない（13節 Q1: A）。
- **F の流れの比べの範囲**（NFR6.10）: 140 で差し替える見本（ロールの一覧・ロール1件・権限の木の3段・割り当て・利用者のロール・グループの一覧・詳細・グループのロール・確かめの応答）のすべてを、150 の中で本物の応答と項目の名前と型で比べる。グループの形は 150 の中で自分の `E2EF_` のグループを API で作って読み、確かめの応答は自分の `E2EF_` のロールを作る YAML を `check` だけに送って読む（適用しない）（13節 Q6: A）。

## 3. Bolt・ブランチ・統合・コミットの区切り

| 項目 | 扱い | 出典 |
|---|---|---|
| Bolt | B8（U6 role-admin-ui だけ）。B1〜B7 は統合済み（B2 65a76c5・B1 44677cc・B3 f9d1134・B4 3d4f344・B5 b1c2760・B6 fcc25b5・B7 3a30982） | `bolt-plan.md` |
| 作業ブランチ | `feature/261004-role-menu-b8`。計画の承認の後に、`develop` の先頭から作る（worktree は使わない） | `team.md` の Way of Working |
| 統合の前の関門 | colima の共有を確かめたうえで `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（Step 29）と、E2E の全体 `caffeinate -i ./gradlew e2eTest`（010〜150、Step 29）。B8 は画面と認可に関わるため E2E の全体を流す | `team.md` の Testing Posture、`cicd-pipeline.md` 4節・6節 |
| 統合の形 | `develop` への squash（1コミット）。単位は1つのため単位ごとの squash は使わない。件名は「B8 役割・権限の管理の画面: ロール・グループ・権限の受け渡しの画面と利用者のロールの表示」とする。サブモジュールの更新は無い | `team.md` |
| 統合の前のコミット | `aidlc/` の未コミットの変更（監査ログを含む）を、統合の前に記録のコミットにまとめる。記録のコミットの直後にも監査ログが追記されるため、統合の直前にもう一度確かめる。`git restore` で `aidlc/` を戻さない | `project.md` の Change Control |
| コード生成の段の承認の場 | 最後のコードのコミットの後、記録だけのコミットを積む前に開く | `project.md` の Change Control |
| プッシュ | 依頼者自身が行う | `team.md` |
| 長いコマンド | `verify`・`e2eTest`・画面のテストの全体は `caffeinate -i` で包む。テストを流している間に同じ作業フォルダでビルド（`frontendBuild`・`bootWar`）を流さない | `project.md` の学び |

コミットの区切り（作業ブランチの上。各コミットの時点で、画面の型の検査・リンタ・そのコミットまでの画面のテストが通る形にする。実行の前に依頼者の承認を得る）:

| コミット | 中身 | 手順 | import の向きの確かめ |
|---|---|---|---|
| C1 | 入口の data router への差し替え（`main.tsx`）と、その確かめのテスト `app/routing/DataRouterBlocker.test.tsx` | Step 4〜5 | `main.tsx` は `app/` だけを読む。差し替えを1つのコミットにし、7.3 の切り替えのときに戻しやすくする |
| C2 | 共有の部品: `ApiDownload` の `headers`（L10）、`shared/validation/validateAdminName.ts`（L9）、検索の文字の確かめの共有（13節 Q3: A）と useradmin の import の直し、それぞれのテスト | Step 6〜7 | `shared/` は `app/`・`features/` を読まない（ESLint の制限）。useradmin は `shared/` だけを読む |
| C3 | 3つの新しい機能の純粋な関数と API の関数、useradmin の `getUserRoles`、それぞれのテスト（画面と登録は含まない） | Step 8〜11 | 各機能は `shared/` と自分の中だけを読む。機能どうしの import が無いこと（`npm run lint`） |
| C4 | roleadmin の画面（S3・S4・S5）と登録・文言・テスト | Step 12〜15 | roleadmin → `shared/`・`app/`（registry の型・admin-forbidden・display-settings）だけ |
| C5 | groupadmin の画面（S6）と登録・文言・テスト | Step 16〜17 | 同上（roleadmin を読まない。ロールへのリンクは道の文字列） |
| C6 | roletransfer の画面（S7）と登録・文言・テスト | Step 18〜19 | 同上（`features/dsl` を読まない） |
| C7 | useradmin の広げ（S9）と既存のテストの直し | Step 20〜21 | useradmin は roleadmin の型を読まない |
| C8 | E2E の部品（`adminApiRoute.ts` の `body`、見本の作り方、測りの部品）と 140・150 | Step 23〜25 | E2E は `src/` の型だけを読む（`import type`） |
| C9 | `README.md` の E2E の表と画面の説明 | Step 26 | 本体のコードを含まない |
| （記録） | `aidlc/` の下だけ（計画のチェック・`code-summary.md`・`source-manifest.json`・`traceability.json`・`generation-notes.md`） | Step 28・30 | 承認の場は C9 の後、記録のコミットの前に開く |

## 4. 作るもの・手を入れるもの

道はすべて `frontend/` の下。新しいファイルの先頭には Apache License 2.0 のヘッダー（`/* ... */`、2026、agwlvssainokuni）を置く。CSS は部品と同じ場所の素の CSS。`export default`・`enum` は使わない。名前とファイルは `frontend-components.md` の案を正とし、ここで決めた差は 11節に書く。

### 4.1 共有と入口（手を入れる・足す）

| 道 | 変更 | 出典 |
|---|---|---|
| `src/main.tsx` | `BrowserRouter` を `createBrowserRouter([{ path: '*', element: <App appearance={appearance} /> }])` と `RouterProvider` に替える。前処理（`rewriteDesignSystemCopies(getPrefersDark())`・`installLanguageResolver()`・`startAppearanceLoad()`）と `StrictMode` はそのまま、ルーターは前処理の後に作る | L11、NFR6.7、2.2 の機能設計 R-04 |
| `src/app/routing/DataRouterBlocker.test.tsx`（新） | テスト用の登録（`useBlocker` を使う画面を1つ持つ）で `App` を `createMemoryRouter`＋`RouterProvider` に包んで描き、サイドバーの項目の押下・ブラウザの戻る（`router.navigate(-1)`）が止まること、止めないときは移ることを確かめる。差し替えの後も `App` の中の振り分けが変わらないことの見張り | NFR6.7、`cicd-pipeline.md` 3節 |
| `src/shared/api-client/apiClient.ts` | `ApiDownload` に `headers: Headers` を足す（`blob`・`contentDisposition` は変えない） | L10、NFR6.6 |
| `src/shared/validation/validateAdminName.ts`（新） | `validateAdminName(value) → 'required' か 'tooLong' か 'invalidCharacter' か undefined`。`trimDisplayName`・`countCodePoints` を使い、上限 64（`ADMIN_NAME_MAX_CODE_POINTS` を `limits.ts` に足す）、拒むのは Cc と U+2028・U+2029 | L9、D7、2.3 の A19 |
| `src/shared/validation/searchText.ts`（新、13節 Q3: A） | useradmin の `searchInput.ts` の `checkSearchInput`・`USER_SEARCH_MAX_CODE_POINTS` を移す（中身は変えない）。useradmin・roleadmin・groupadmin が使う | `team.md` の「共有は src/shared/ へ」、`frontend-components.md` の CandidateDialog |

### 4.2 機能 roleadmin（`src/features/roleadmin/`、新）

| 道 | 中身 | 出典 |
|---|---|---|
| `registration.ts` | 道3つ（`lazy` の `RoleListPage`、S4・S5 は同じ `lazy` の `RoleDetailPage`）、サイドバー「ロール」（240、`section: 'ADMIN'`・`visibleWhen: 'ADMIN'`、アイコンなし） | D1、L1、NFR4.7 |
| `messages.ts`・`useRoleAdminText.ts` | 文言（鍵は `roleadmin.`、ja・en）。Table の `labels`・Modal の `closeLabel`・Alert の `dismissLabel`・共有の木の `labels` も持つ | NFR4.3・NFR4.6 |
| `api/types.ts`・`api/roleAdminApi.ts` | 型と 18 の関数（`frontend-components.md` 3節）。応答は型の項目だけを写す | L2、2.3 |
| `failureMessage.ts` | code と状態から文言の鍵を選ぶ（5節の表）。`detail`・`title` を読まない | L3、D3 |
| `latestLoad.ts`・`useSlowNotice.ts` | 読み込みの世代（古い答えを捨てる）・5 秒の待ちの案内と送信中の2回目を捨てる口（3つの機能に同じ形） | L4・L5、NFR2.10・NFR2.11 |
| `permissionDraft.ts`・`permissionLabels.ts`・`removalImpact.ts` | 純粋な関数（D11・D12・D19） | L7 |
| `RoleListPage.tsx`・`useRoleList.ts`・`RoleNameDialog.tsx`・`DeleteConfirmDialog.tsx`・`RoleRowActions.tsx` | S3（W3.1〜W3.6） | D5〜D9 |
| `RoleDetailPage.tsx`・`useRole.ts` | S4・S5 の枠（見出し・「← ロールの一覧」・Tabs・D32） | D32、NFR4.7 |
| `PermissionPanel.tsx`・`useRolePermissions.ts`・`PermissionTable.tsx` | S4（W4.1〜W4.7、4.3 の状態） | D10〜D14 |
| `LeaveConfirmDialog.tsx`・`useUnsavedGuard.ts` | 未保存の確かめ（`useBlocker`・`beforeunload`、「留まる」の後のタブへのフォーカスの戻し） | D15、L6 |
| `AssignmentPanel.tsx`・`useRoleAssignments.ts`・`CandidateDialog.tsx` | S5（W5.1〜W5.5） | D16〜D19 |
| `testing/fixtures.ts`・`testing/renderRoleAdmin.tsx` | テストの見本と描き方（`createMemoryRouter` の描き方を含む） | NFR6.9 |
| `*.css` | 部品ごと（幅の決まり D31、768px 未満の縦積み） | D31 |

### 4.3 機能 groupadmin（`src/features/groupadmin/`、新）

`registration.ts`（`/admin/groups`・`/admin/groups/:groupId`、サイドバー「グループ」250）・`messages.ts`・`useGroupAdminText.ts`・`failureMessage.ts`・`latestLoad.ts`・`useSlowNotice.ts`・`api/types.ts`・`api/groupAdminApi.ts`・`removedRoles.ts`・`GroupListPage.tsx`・`useGroupList.ts`・`GroupNameDialog.tsx`・`DeleteConfirmDialog.tsx`・`GroupRowActions.tsx`・`GroupDetailPage.tsx`・`useGroupDetail.ts`・`MemberTable.tsx`・`CandidateDialog.tsx`・`testing/`・`*.css`（`frontend-components.md` 4節、D17・D20・D21・D32）。

### 4.4 機能 roletransfer（`src/features/roletransfer/`、新）

`registration.ts`（`/admin/role-transfer`、サイドバー「権限の受け渡し」260）・`messages.ts`・`useRoleTransferText.ts`・`failureMessage.ts`・`reasonMessage.ts`（`reason` の 13 個と汎用）・`latestLoad.ts`・`useSlowNotice.ts`・`api/types.ts`・`api/roleTransferApi.ts`・`transferLimits.ts`（`MAX_TRANSFER_BYTES = 10_485_760`）・`safeFileName.ts`・`saveFile.ts`・`changePaging.ts`・`RoleTransferPage.tsx`・`useRoleTransfer.ts`・`TransferFilePicker.tsx`・`TransferCheckTable.tsx`・`TransferErrorList.tsx`・`ApplyConfirmDialog.tsx`・`testing/`・`*.css`（`frontend-components.md` 5節、L8、D22〜D26）。

### 4.5 useradmin の広げ（`src/features/useradmin/`、手を入れる）

| 道 | 変更 |
|---|---|
| `rowActions.ts` | `ROW_ACTION_KINDS` の最後に `viewRoles`（どの行でも押せる）。`ConfirmActionKind` を `Exclude<RowActionKind, 'editProfile' \| 'viewRoles'>` に直す（2.4） |
| `useUserAdmin.ts`・`UserAdminPage.tsx` | `viewRoles` で `UserRolesDialog` を開き、閉じたら行の「操作」へフォーカスを戻す（既存の `dialogReturnRef`） |
| `UserRolesDialog.tsx`（新）・`UserRolesDialog.css`（新） | D27 の読み取りの Modal |
| `api/types.ts`・`api/userAdminApi.ts` | `UserRole`・`RoleSourceType` と `getUserRoles(userId)` |
| `messages.ts` | `useradmin.menu.viewRoles`・`useradmin.roles.*`（ja・en） |
| `searchInput.ts`・`searchInput.test.ts`（13節 Q3: A） | `shared/validation/searchText.ts` へ移し、import を直す |

### 4.6 E2E（`frontend/e2e/`）

| 道 | 変更 |
|---|---|
| `support/adminApiRoute.ts` | `MockReply` に `{ status: number; body: string }` の形を足す（2.2 の NFR 設計 R-09）。今の `json` の形は残す |
| `support/roleAdminFixtures.ts`（新） | 140 と 150 が使う見本の作り方（関数）。画面の側の型（各機能の `api/types.ts`）を `import type` で付ける。2語の氏名・64 文字のロール名・長いメールアドレス（`example.com`）・`<`・`&` を含む名前、100・1,000 カラム、25 ロール × 1万の変わる点 |
| `support/u6Measure.ts`（新） | 画面の中の `performance.now()` の聞き手と見張り（`page.evaluate`）、5 回の中央値・最大・1回目の記録（`performance-design.md` 4.2〜4.4） |
| `support/shapeCompare.ts`（新） | 本物の応答と見本の項目の名前と型を比べる小さな関数（NFR6.10） |
| `140-role-admin-accessibility.e2e.ts`（新） | 9節 |
| `150-role-permission-flow.e2e.ts`（新） | 9節 |

### 4.7 文書

- `README.md`: E2E のファイルの表に 140・150 の行を足す。管理の画面の説明に、ロール・グループ・権限の受け渡しの画面と、未保存の確かめ（骨組みのログアウトでは確かめが出ないことがある）を足す。

### 4.8 変えないもの（Step 27 で差が無いことを確かめる）

`backend/` のすべて、`frontend/package.json`・`frontend/package-lock.json`、`frontend/vitest.config.ts`・`vitest.setup.ts`・`playwright.config.ts`・`vite.config.ts`・`eslint.config.js`・`.oxlintrc.json`・`tsconfig.json`・`scripts/`、`frontend/src/app/` の `main.tsx` の外（`DataRouterBlocker.test.tsx` を足すだけ）、`vendor/`、`docker/`、`.github/`、`build.gradle.kts`。

## 5. 既存のテスト・E2E・lint・幅への影響の洗い出し

前の Bolt では、計画の影響の範囲の外の既存のテスト・lint・幅で止まった例が多かった（dsl-v2 の D-20・D-21、role の B4-1・B5-1、safety-carryover の G1・G2）。計画の段で、変える型・登録・ルート・共有の部品を使っている所を `git grep` で洗い出した。

| # | 変えるもの | 当たる所（検索で見つけたもの） | 扱い |
|---|---|---|---|
| (a) | useradmin の `ROW_ACTION_KINDS` に `viewRoles` を足す | `rowActions.test.ts`（並びの期待 `toEqual([...])` が2か所、性質のテスト）、`UserRowActions.test.tsx`（項目の並びの期待）、`ConfirmActionDialog.test.tsx`（`ConfirmActionKind` の表）、`useUserAdmin.ts` の `OPERATIONS`（`Record<ConfirmActionKind, …>`）、E2E 120（自分の行のメニューを矢印で押せない項目までたどる。最後に足すため変わらない見込み）・110（項目の名前で選ぶ） | 並びの期待は最後に `viewRoles` を足す形に直す（期待を緩めない）。`ConfirmActionKind` は 2.4 のとおり直し、確かめの表は変えない。E2E は Step 29 の全体で確かめる |
| (b) | useradmin の `searchInput.ts` を `shared/` へ移す（13節 Q3: A） | `useUserAdmin.ts` の import、`searchInput.test.ts` | テストも移し、中身は変えない。`git mv` で履歴を残す |
| (c) | `ApiDownload` に `headers` を足す | `features/dsl/api/dslApi.ts`（分割代入で `blob`・`contentDisposition` だけを読む）、`apiClient.download.test.ts`、`apiClient.test.ts`（`apiDownload` の呼び出し）。`ApiDownload` の値を手で作るテストは無い | 既存は変えない。`apiClient.download.test.ts` に `headers` の確かめを足す |
| (d) | `main.tsx` を data router にする | `main.tsx` はカバレッジの計測の外。`App` を描くテスト（`App.test.tsx`）と `renderWithProviders.tsx` は `MemoryRouter` のまま。`useBlocker` は data router の外で呼ぶと例外になる | `useBlocker` を使う部品（`PermissionPanel`・`RoleDetailPage`）を描くテストは `createMemoryRouter` だけで描く。`renderWithProviders` は変えず、roleadmin の `testing/renderRoleAdmin.tsx` に data router の描き方を持つ。`App.test.tsx` で `/admin/roles/:roleId` を描くテストは書かない |
| (e) | S4・S5 を同じ `RoleDetailPage` で2つの道に登録する（NFR4.7） | `AppRouter.tsx` は `decision.route` ごとに `<Routes><Route path={route.path} …/></Routes>` を描く。道が変わると `Route` の `path` が変わるため、作り直し（フォーカスと読み込みの状態が消える）が起きるかは実物で確かめる必要がある | Step 15 で、`App` を `createMemoryRouter` で描き、タブの切り替えの後もロールの読み込みが1回だけでフォーカスがタブに残ることを確かめる。作り直しが起きたら止まって依頼者に尋ねる（切り替え先の案: 1つの登録 `/admin/roles/:roleId/assignments?` の任意の区切りにする。`matchPath` は任意の区切りを受け付ける） |
| (f) | サイドバーに3つの項目を足す | `ShellLayout.test.tsx` は自前の登録を使う（影響なし）。`App.test.tsx` の既定の登録（`registrationModules`）で描くテストは、管理者のサイドバーの項目が3つ増える。E2E の 090（管理者でない利用者に「管理」「利用者の招待」「DSL」のリンクが無い）・130・135・120（360px のはみ出し・axe） | 項目の数を数える既存のテストは無かった。E2E の 090 に「ロール」「グループ」「権限の受け渡し」を足すかは本数に数えない書き換えだが、この Bolt では足さない（管理者でない利用者の道の直接入力は 140 と `registration.test.tsx` が確かめる）。360px の既存の画面への影響は Step 29 の全体で確かめる |
| (g) | 新しい機能のディレクトリを3つ足す | `eslint.config.js` は `src/features/` のディレクトリの一覧から機能どうしの import の制限を作る。`eslintImportRules.test.ts` は useradmin・registration・auth の名前で確かめる | 設定は変えない。新しい機能にも制限が自動で当たることを Step 22 の `npm run lint` で確かめる |
| (h) | 横に動く表と誤りの一覧（D31） | dsl-v2 の D-20: 360px で場所の長い誤りの一覧が横に動き、axe の `scrollable-region-focusable` が出た（中にフォーカスできる要素が無い領域）。直しには oxlint の `jsx-a11y/no-noninteractive-tabindex` の1行の除外を依頼者の承認で使った。骨組みのサイドバーは 360px でも 220px のままで、本文の列は約 108px（D-21） | 中に押せる要素（Select・ボタン・Dropdown）がある表は、領域の中にフォーカスの先があるため当たらない見込み。中に押せる要素が無い `TransferErrorList` と S7 の開いた行の変わる点の表は、D-20 と同じ形（`role="region"`・`tabIndex={0}`・読み上げの名前）が要る（13節 Q7: A。oxlint の `jsx-a11y/no-noninteractive-tabindex` はその行だけを日本語の理由つきのコメントで外す）。すべての表と行は `minmax(0, 1fr)`・`min-width: 0`・`overflow-wrap: anywhere` で、文書の横のはみ出しを出さない |
| (i) | 差し替えの口に `body` の形を足す | `adminApiRoute.ts` を使うのは 120 だけ（130・135 は自前の `page.route`） | `json` の形の振る舞いは変えない。Step 24 で 120・130・135 を流して変わらないことを確かめる（`cicd-pipeline.md` 7節 (iii)） |
| (j) | E2E 150 が DSL を `E2EF_` に替える | 150 は最後の番号。後に流れるのは B9 の 160・170（自分で DSL を適用し直す）。040 は前に流れる | 影響なし。受信箱の件数に頼る E2E が無いことを Step 1 で `git grep` で確かめる |
| (k) | 画面のテストのヒープと時間 | 画面のテストは今 112 ファイル以上。U6 で約 45 ファイルが増える。テストの時間の上限（既定 5 秒）は原因を確かめずに延ばさない | 重い見本（1,000 カラム・25 万の変わる点）は画面のテストに入れず、140 の測りだけで使う。画面のテストの見本は小さく（数件〜100 件）する |

## 6. 手順

方法は test-after。層（純粋な関数 → API の関数 → 画面の部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて流し、通ってから次の層へ進む。画面の部品は機能ごとに層を閉じる（実装 → テスト）。コマンドの正確な形は `unit-test-instructions.md`。テストの説明文は英語、見本のメールアドレスは `example.com` だけ。描画の後に反映される値は `waitFor`・`findBy*` で待ち、テストの時間の上限は延ばさない。

- [x] **Step 1: 作業の場の用意**。計画の承認の後に `develop` の先頭（3a30982 か、その後の記録のコミット）から `feature/261004-role-menu-b8` を作る（依頼者の承認を得てから）。`git status` で作業フォルダが空であること、`frontend/playwright-report`・`frontend/test-results` が無いこと（あれば段の始めに消す、`project.md` の学び）、`vendor/make-you-chic-ui` の固定先が e82b651 であることを確かめる。読み取りで次を確かめて `generation-notes.md` に書く: (1) `frontend/e2e/` の今の番号（010〜135）と app-frame-ui の案（160・170）で 140・150 が空いていること（2.2 の NFR 設計 R-08）、(2) `role/domain/RoleName.java` の制御文字の範囲が `GroupName` と同じこと（2.3 の A19）、(3) 受信箱の件数に頼る E2E が無いこと（5節 (j)）。
- [x] **Step 2: テストの実行の準備**（最初のテストより前）。名指しのコマンドが動くことを、既存のテスト（`src/features/useradmin`・`src/shared/tree`・`src/shared/api-client/apiClient.download.test.ts`）と `npm run typecheck` で確かめ、失敗 0 を記録する。
- [x] **Step 3: 変更の前の基準**（基盤の設計の承認の場の決定 (4)）。B8 の変更より前に、`caffeinate -i ./gradlew frontendBundleSize` を流し、入口の JavaScript の合計（gzip）と、`frontend/dist/.vite/manifest.json` の入口から静的な import をたどった集まり（ファイルの一覧）を `generation-notes.md` に書く。`caffeinate -i npm run test:coverage`（`frontend/` で）で画面のテストの件数とカバレッジ（行・分岐）を記録する。`frontend/package.json`・`frontend/package-lock.json`・`backend/gradle.lockfile` の SHA-256 を記録する。
- [x] **Step 4: 入口の差し替え — 実装**（L11、NFR6.7）。`src/main.tsx` を 4.1 のとおり替える（前処理と `StrictMode` を保つ）。
- [x] **Step 5: 入口の差し替え — テストと確かめ**（`cicd-pipeline.md` 3節）。`src/app/routing/DataRouterBlocker.test.tsx` を書いて流す。画面のテストの全体（`npm run test`）が Step 3 と同じ件数で通ること、`./gradlew :backend:bootWar` の後に既存の E2E（010〜135）の全体が通ることを確かめる（Mailpit を起動しておく。この間はビルドを流さない）。**どれかが成り立たなければ、ここで止まって依頼者に尋ねる**（機能設計 7.3 の形に切り替えるか。切り替えるなら `main.tsx` を戻し、`useUnsavedGuard` は画面の中の移動と `beforeunload` だけにし、差を `code-summary.md` に書く）。C1 のコミットの提案（実行は承認の後）。
- [x] **Step 6: 共有の部品 — 実装**。`ApiDownload` の `headers`（L10）、`validateAdminName.ts` と `limits.ts` の `ADMIN_NAME_MAX_CODE_POINTS`（L9）、検索の文字の確かめを `shared/validation/searchText.ts` へ移し、useradmin の import を直す（13節 Q3: A）。
- [x] **Step 7: 共有の部品 — テスト**。`apiClient.download.test.ts` に `headers` が返る・既存の項目が変わらないことを足す。`validateAdminName.test.ts`（64 と 65 コードポイント、サロゲートペア、空白だけ、全角の空白、前後の空白を除いた後の長さ、Cc・U+2028・U+2029 は拒む、U+200B（Cf）は受け付ける）と fast-check の性質（前後に White_Space を足しても結果が変わらない）。移した `searchText.test.ts`。useradmin のテストと DSL のダウンロードのテストを流す。C2 のコミットの提案。
- [x] **Step 8: 純粋な関数 — 実装**（業務の決まりの層）。roleadmin の `permissionDraft.ts`（`applyChange`・`toEntries`・写しの鍵は対象の名前の JSON の配列）・`permissionLabels.ts`（13節 Q4: A の形。補助権限は「可」「不可」と、明示のときだけ「（明示）」）・`removalImpact.ts`・`failureMessage.ts`、groupadmin の `removedRoles.ts`・`failureMessage.ts`、roletransfer の `changePaging.ts`・`reasonMessage.ts`・`failureMessage.ts`・`safeFileName.ts`・`transferLimits.ts`。
- [x] **Step 9: 純粋な関数 — テスト**。各関数の `*.test.ts`。fast-check（失敗時の種を記録する）: `permissionDraft`（写しが空なら `entries` も空、サーバーの値に戻すと写しから消える、`scope` の外を含まない（スキーマの範囲はスキーマの段の対象だけ、テーブルの範囲はそのテーブルとそのカラムだけ、2.3 の A7）、同じ対象は1つ、鍵は名前に何が入っても重ならない）、`removalImpact`（直接以外の出どころがあれば無くならない、残りの最初は ID の最小）、`removedRoles`（グループのロールのうち外した後に無いものだけ、直接で残るロールを含まない）、`changePaging`（範囲が重ならず全件を覆う、100 件ずつ）、`reasonMessage`（任意の文字列で例外にならず、知らない値は汎用）。`failureMessage`（3つ）: `detail`・`title` に目印を入れても文言が変わらない、知らない code・code の無い応答・通信の失敗は汎用。`safeFileName`（`roles.yaml`・`../x.yaml`・`a/b.yaml`・制御文字・空・`x.txt`・無い）。`transferLimits`（10,485,760、2.2 の NFR 設計 R-05）。
- [x] **Step 10: API の関数 — 実装**（L2）。`roleadmin/api`・`groupadmin/api`・`roletransfer/api` の型と関数、useradmin の `getUserRoles`。応答は型の項目だけを写し、2.3 の形（A3 の `items`、A6 の `scope`、A9 の `type`、A12 の配列、A15 の null を許す項目）に合わせる。道の ID と名前は `encodeURIComponent`、権限の木は `URLSearchParams`。確かめ・適用は `Content-Type: application/yaml` と指紋のヘッダー。
- [x] **Step 11: API の関数 — テスト**。各機能の `api/*Api.test.ts`（`apiRequest`・`apiDownload` を差し替えて、送る道・引数・方法・本文の項目が決めたものだけであること、`/`・`?`・`#`・`%` を含む名前と ID の道、余分な項目（`passwordHash` など）を足した応答を写した値に余分な項目が無いこと、欠けた・型の違う・列挙の知らない値の応答が通信の失敗になること、書き出しの `Content-Disposition` と `X-Role-Transfer-Exceeds-Import-Limit`）。useradmin の `userAdminApi.test.ts` に `getUserRoles` を足す。C3 のコミットの提案。
- [x] **Step 12: roleadmin の S3 — 実装**。`messages.ts`・`useRoleAdminText.ts`・`latestLoad.ts`・`useSlowNotice.ts`・`RoleListPage`・`useRoleList`・`RoleNameDialog`・`DeleteConfirmDialog`・`RoleRowActions`・`testing/fixtures.ts`・CSS。
- [x] **Step 13: roleadmin の S3 — テスト**。`RoleListPage.test.tsx`・`RoleNameDialog.test.tsx`・`DeleteConfirmDialog.test.tsx`・`RoleRowActions.test.tsx`・`latestLoad.test.ts`・`useSlowNotice.test.tsx`（8節の表）。部品ごとに vitest-axe を1件（誤りの状態・64 文字の名前を含める）。
- [x] **Step 14: roleadmin の S4・S5 と登録 — 実装**。`RoleDetailPage`・`useRole`・`PermissionPanel`・`useRolePermissions`・`PermissionTable`・`useUnsavedGuard`・`LeaveConfirmDialog`・`AssignmentPanel`・`useRoleAssignments`・`CandidateDialog`・`testing/renderRoleAdmin.tsx`・`registration.ts`。保存の後の読み直しは、スキーマの一覧を新しい配列にして共有の木に子を読み直させる（共有の木の「`nodes` を替えると開いている節を読み直す」の決まり）。
- [x] **Step 15: roleadmin の S4・S5 と登録 — テスト**。`RoleDetailPage.test.tsx`・`PermissionPanel.test.tsx`・`PermissionTable.test.tsx`・`useUnsavedGuard.test.tsx`・`LeaveConfirmDialog.test.tsx`・`AssignmentPanel.test.tsx`・`CandidateDialog.test.tsx`・`registration.test.tsx`（U6 の3つの道で、管理者でないとき `decideRoute` が `ADMIN_FORBIDDEN`、未ログインはログインへ、管理者は画面）・`RoleAdminForbidden.test.tsx`（画面と API の組の 403 の表、NFR1.1）・`RoleAdminEnglish.test.tsx`（en で文言がすべて英語、部品の日本語の既定が出ない）。`useBlocker` を使う部品は `createMemoryRouter` で描く。5節 (e) の作り直しの確かめ（`App` を `createMemoryRouter` で描き、S4 → S5 のタブの切り替えの後もロールの読み込みが1回でフォーカスがタブに残る）。作り直しが起きたら止まって依頼者に尋ねる。画面のテストの全体を流す。C4 のコミットの提案。
- [x] **Step 16: groupadmin — 実装**（S6、4.3）。
- [x] **Step 17: groupadmin — テスト**。`GroupListPage.test.tsx`・`GroupNameDialog.test.tsx`・`GroupDetailPage.test.tsx`・`MemberTable.test.tsx`・`CandidateDialog.test.tsx`・`registration.test.tsx`・`GroupAdminForbidden.test.tsx`・`GroupAdminEnglish.test.tsx`。C5 のコミットの提案。
- [x] **Step 18: roletransfer — 実装**（S7、4.4。確かめた本文の文字列と指紋を画面の状態にだけ持つ、NFR 設計 Q1 A）。`TransferErrorList` と開いた行の変わる点の表の領域は 11節 D-14 の形（13節 Q7: A）。
- [x] **Step 19: roletransfer — テスト**。`RoleTransferPage.test.tsx`・`useRoleTransfer.test.tsx`・`TransferFilePicker.test.tsx`（10,485,760 は送る・10,485,761 は送らない）・`TransferCheckTable.test.tsx`・`TransferErrorList.test.tsx`・`ApplyConfirmDialog.test.tsx`・`registration.test.tsx`・`RoleTransferForbidden.test.tsx`・`RoleTransferEnglish.test.tsx`。C6 のコミットの提案。
- [x] **Step 20: useradmin の広げ — 実装**（S9、4.5）。
- [x] **Step 21: useradmin の広げ — テスト**。`UserRolesDialog.test.tsx`（loading・ready・empty・failed、`USER_NOT_FOUND`、403 は骨組みへ、題に氏名、出どころの並び、閉じたら行の「操作」へ戻る、axe）を足し、5節 (a) の既存のテストを直す（期待を緩めない）。`UserAdminPage.test.tsx` に「ロールを見る」の流れを1件足す。useradmin のテストの全体を流す。C7 のコミットの提案。
- [x] **Step 22: 画面の全体の検査**。`frontend/` で `npm run format:check`・`npm run lint`・`npm run lint:css`・`npm run license:check`・`npm run typecheck`・`caffeinate -i npm run test:coverage`。カバレッジが行 80%・分岐 70% 以上であること（除外を足さない）、件数とカバレッジを Step 3 と並べて記録する。
- [x] **Step 23: E2E の部品 — 実装**。`adminApiRoute.ts` の `body` の形（2.2 の NFR 設計 R-09）、`roleAdminFixtures.ts`・`u6Measure.ts`・`shapeCompare.ts`。
- [x] **Step 24: 140 — 実装と実行**（9.1）。`./gradlew :backend:bootWar` の後に `npx playwright test e2e/140-role-admin-accessibility.e2e.ts`。あわせて 120・130・135 を流し、口の変更が既存を変えないことを確かめる（5節 (i)）。測りの最初の実行で、差し替えた応答でも `responseEnd` の項目が得られるかを確かめ、得られなければ操作から測る形にしてその旨を記録する（`cicd-pipeline.md` 7節 (iv)）。値は記録だけ（Not Met でも統合は止めない）。
- [x] **Step 25: 150 — 実装と実行**（9.2）。`npx playwright test e2e/150-role-permission-flow.e2e.ts`。報告の部品の結果と json の報告の文字列の検索で、パスワード・トークン・メールアドレス・氏名が無いことを確かめる。C8 のコミットの提案。
- [x] **Step 26: 文書**（4.7）。C9 のコミットの提案。
- [x] **Step 27: 取り残しと変えないものの確かめ、入口の量の判定**。`git diff --stat develop` で 4.8 のファイルに差が無いこと、lockfile の SHA-256 が Step 3 と同じこと（NFR6.5）、`TODO` が無いこと、新しいファイルすべてにライセンスヘッダーがあること。`caffeinate -i ./gradlew frontendBundleSize` で後の値を取り、10節の判定をする。
- [x] **Step 28: 記録**（`aidlc/` の下）。`code-summary.md`（作ったもの・決めたこと・計画との差・承認の場で確かめたいこと・後に回すこと・受け入れた制約）、`source-manifest.json`（アプリのソースの道すべて）、`traceability.json`（7節の AC と D の番号、NFR の枝番と集約の行。2.2 の手当てを含む）、`generation-notes.md`（Step ごとのコマンドと結果）。
- [x] **Step 29: 統合の前の関門**。colima の共有を確かめ、README の環境変数（`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`）を付けて `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`。続けて Mailpit を起動して `caffeinate -i ./gradlew e2eTest`（010〜150）。件数・カバレッジ・時間を記録し、報告の確かめの道具で残してはならない値が 0 件であることを確かめ、報告のフォルダーを消す。落ちたら `team.md` の不安定なテストの決まりで扱う。
- [ ] **Step 30: コミットと承認の場と統合**。最後のコードのコミットの後にコード生成の段の承認の場を開き、その後に記録のコミット、`aidlc/` の未コミットの変更の確かめ、`develop` への squash の提案（どれも実行の前に依頼者の承認を得る）。統合の後に作業ブランチを消す。

## 7. ストーリー・要件と手順の対応

AC は機能設計の `traceability.json` で U6 が受け持つもの（OK と OK の部分）。サーバーの持ち物（Deferred）は U3 group・U4 role・U1・U7 が持ち、この Bolt では画面の扱いだけを作る。D は `functional-spec.md` 2節、W は 3節。

| ストーリー | AC（画面の部分） | D・W | NFR | 手順（実装・テスト） |
|---|---|---|---|---|
| US1.1 ロールを作る・名前を変える・削除する | AC1.1.1・AC1.1.2・AC1.1.3・AC1.1.4・AC1.1.7・AC1.1.8・AC1.1.9・AC1.1.14、画面の部分として AC1.1.12（D32 の表示） | D5〜D9・D32、W3.1〜W3.6 | NFR1.4・NFR1.7・NFR1.8・NFR2.10・NFR2.11・NFR4.1 | 6・7（名前の検査）、8〜11、12・13、14・15（D32） |
| US1.2 テーブルの権限を設定する | AC1.2.1・AC1.2.2・AC1.2.3・AC1.2.4・AC1.2.6・AC1.2.7・AC1.2.13・AC1.2.14・AC1.2.15・AC1.2.16・AC1.2.17 | D10〜D15、W4.1〜W4.7 | NFR1.4（`entries`）・NFR2.5・NFR2.6・NFR2.12・NFR4.2・NFR4.7・NFR6.2（`permissionDraft`）・NFR6.7 | 4・5（入口）、8・9、10・11、14・15、24（測り） |
| US2.1 グループを作り、メンバーを整える | AC2.1.1・AC2.1.2・AC2.1.3・AC2.1.4・AC2.1.7・AC2.1.8・AC2.1.9・AC2.1.10・AC2.1.13 | D7〜D9・D17・D20・D21・D32、W6.1〜W6.4 | NFR6.2（`removedRoles`） | 8〜11、16・17 |
| US2.2 ロールを利用者・グループに割り当てる | AC2.2.1・AC2.2.2・AC2.2.7・AC2.2.8・AC2.2.9・AC2.2.15・AC2.2.16 | D16〜D19・D27、W5.1〜W5.5・W9.1 | NFR6.2（`removalImpact`） | 8〜11、14・15、20・21 |
| US3.1 権限を書き出し・読み込む | AC3.1.1・AC3.1.2・AC3.1.3・AC3.1.5・AC3.1.6・AC3.1.9・AC3.1.10・AC3.1.11・AC3.1.12・AC3.1.14、画面の部分として AC3.1.4（STALE の表示）・AC3.1.7（送る前の大きさ）・AC3.1.13（部品の例外の文を出さない） | D22〜D26、W7.1〜W7.4 | NFR1.5・NFR1.6・NFR1.10・NFR2.4・NFR2.7・NFR2.8・NFR6.2（`changePaging`・`reasonMessage`）・NFR6.6 | 6・7（`headers`）、8〜11、18・19、24（測り） |
| US5.3 管理の画面の登録 | AC5.3.1・AC5.3.2 | D1・D2 | NFR1.1・NFR2.13 | 14・16・18（登録）、15・17・19（登録と 403 の表のテスト）、27（入口の量） |
| 横断（画面の共通） | — | D3・D4・D28〜D31 | NFR1.6〜NFR1.9・NFR1.11・NFR1.12・NFR4.3〜NFR4.6・NFR6.1・NFR6.3〜NFR6.5・NFR6.8〜NFR6.10 | 13・15・17・19・21（axe・en・漏えい）、22（静的検査・カバレッジ）、23〜25（E2E）、27（依存）、29（関門） |

要件との対応（`project.md` の2段の連鎖）: 要件 FR1〜FR4・FR7・FR11 と NFR1・NFR4・NFR6 → 機能設計の D1〜D32 と NFR 要件の枝番 → コード生成の `traceability.json`（Step 28）。

## 8. テストの量（Standard）

Standard は部品ごとに 5〜8 件と、要所の結合（この単位では画面の部品を本物のフックと差し替えた API でつないだテストと E2E）。下の件数は目安で、確かめる中身（`functional-spec.md` 8.1・NFR の確かめ方）を落とさないことを優先する。部品ごとに vitest-axe を1件含める。

| 置き場 | テスト | 目安の件数 | 主な中身 |
|---|---|---|---|
| 共有 | `DataRouterBlocker.test.tsx`・`apiClient.download.test.ts`（足す分）・`validateAdminName.test.ts`・`searchText.test.ts` | 3・2・10・（移すだけ） | 2.4・Step 7 |
| roleadmin の純粋な関数 | `permissionDraft.test.ts`・`permissionLabels.test.ts`・`removalImpact.test.ts`・`failureMessage.test.ts` | 8・8・6・6 | fast-check を含む（Step 9） |
| roleadmin の API | `api/roleAdminApi.test.ts` | 15 | 18 の関数の道・本文・写し（Step 11） |
| S3 | `RoleListPage.test.tsx`・`RoleNameDialog.test.tsx`・`DeleteConfirmDialog.test.tsx`・`RoleRowActions.test.tsx`・`latestLoad.test.ts`・`useSlowNotice.test.tsx` | 10・8・5・5・4・5 | 一覧・空・失敗と再試行・ページ送り（読み直しの間の押下を捨てる）・D7 の境界とサーバーの code ごとの文言・削除の確かめ（はじめのフォーカスは「やめる」、やめると送らない）・`ROLE_IN_USE` の数とリンク（数が読めないとき）・BUSY で入力が残る・`detail` の目印が出ない・Dropdown が `bottom-end`・`<script>` などが文字のまま・待ちの案内（4.9 秒で出ず 5 秒で出る、応答で消える）・送信中の2回目で API が呼ばれない・古い答えを捨てる・画面を離れた後の答え |
| S4 | `RoleDetailPage.test.tsx`・`PermissionPanel.test.tsx`・`PermissionTable.test.tsx`・`useUnsavedGuard.test.tsx`・`LeaveConfirmDialog.test.tsx` | 8・14・8・6・4 | D32（無い ID・正の整数でない道）、タブの切り替えでフォーカスが残り読み込みが1回（NFR4.7）、木の開閉（`aria-expanded`）と選び（`aria-current`）・キーボード、実効の値の文字（明示・継承の元・既定）、カラムに補助権限の欄が無い、Select の名前に対象、保存の可否と理由、`entries` に変えた対象だけ、保存の後の読み直し、D14 の code ごとの写しの扱い、noDsl、orphan の印と消す操作（Should）、未保存の確かめ（木の節・タブ・サイドバーの移動・ブラウザの戻る、留まる・移る、留まる後のタブへのフォーカス）、`beforeunload` の張り外し |
| S5 | `AssignmentPanel.test.tsx`・`CandidateDialog.test.tsx` | 10・8 | 出どころの文字、外すは直接のときだけ、外した後に出どころが変わる、`ROLE_NO_CHANGE`（相手がいない外しを含む、2.3 の A11）、候補の検索（20 件・済みの印・利用停止・足した後も開いたまま・閉じたら読み直す・Enter で検索・検索の文字の上限）、グループの候補のページ送り、D19 の条件つきの確かめ（直接だけ・グループ経由で残る・残りが無い・読めない） |
| roleadmin の登録・横断 | `registration.test.tsx`・`RoleAdminForbidden.test.tsx`・`RoleAdminEnglish.test.tsx` | 5・（表の行の数）・3 | NFR1.1・NFR4.3・NFR4.6、操作の後に `console` が呼ばれず、ブラウザの保存に書かれず、`window.location` に検索の文字が入らない（NFR1.6） |
| groupadmin | 純粋な関数2つ・API・S6 の部品6つ・登録と横断3つ | 各 5〜10 | D17・D20（無くなるロールあり・なし・読めない）・D21・D32 と同じ形の missing |
| roletransfer | 純粋な関数5つ・API・S7 の部品5つ・フック・登録と横断3つ | 各 5〜10 | 大きさの境界、本文の型と指紋のヘッダー、確かめた本文がそのまま適用で送られる、選び直しで本文と指紋が捨てられる、`X-Role-Transfer-Exceeds-Import-Limit` が `true`・無い・`false`・ほかの値、結果の表の行の開閉の名前・100 件ずつ・理由と ⚠ の文字・noChange で適用を押せない・適用の確かめ・STALE でファイルの選択が残る・誤りの一覧の件数とフォーカス・`reason` の文言が ja・en にある・S7 の BUSY の文言（入口と行の排他を区別しない） |
| useradmin | `UserRolesDialog.test.tsx`（新）、`rowActions.test.ts`・`UserRowActions.test.tsx`・`UserAdminPage.test.tsx`（直す・足す）、`userAdminApi.test.ts`（足す） | 8・（直し） | Step 21 |
| E2E | 140（9.1）・150（9.2） | — | 本数は 150 の1本（140 は数えない） |

見込みの件数: 画面のテストのファイルが約 45、件数が約 350 増える。実際の件数は Step 22・29 で記録する。

## 9. 実際のブラウザの検査（140）と F の流れ（150）

### 9.1 140 U6 の画面の検査（`140-role-admin-accessibility.e2e.ts`、本数に数えない）

- **前提**: 初期管理者で画面からログインし（`loginAsAdmin`）、`/api/admin/` の下はすべて `routeAdminApi(page, baseURL, 'inspect')` で受ける。見本は `roleAdminFixtures.ts` の関数で作り、画面の側の型を付ける。本物の利用者・ロール・グループを作らず、書き換えをサーバーへ届けない。各テストの終わりに `expectNoBlockedWrites`（受けた件数 1 以上・打ち切り 0 件）。
- **状態**（`tech-stack-decisions.md` NFR4.4、`functional-spec.md` 8.2）: S3 の一覧・開いた Dropdown・名前の誤りの Modal、S4 の保存の誤り（BUSY）・未保存の確かめ・今の DSL に無い印、S5 の候補の Modal（利用停止・割り当て済み）・外すときの確かめ（D19）、S6 の詳細とメンバーの外しの Alert（D20）、S7 の結果の行を開いた状態と誤りの一覧、S9 の Modal。
- **見本**: 2語の氏名（既存の見本の「計測 花子」と同じく、実在しそうでない架空の名前）、64 文字のロール名、長いメールアドレス（`example.com`）、`<`・`&` を含む名前。
- **組**: 表示の設定の 20 組すべて（`support/displayCombos.ts`）。組ごとに1つのテストで、上の状態を順に開いて axe（`runAxe`、違反 0 件、`REQUIRED_RULES` が流れた）・画面全体の横のはみ出しなし（`measureHorizontalOverflow`）・開いた Dropdown と Modal が表示の中に収まる（`measureViewportContainment`）を確かめる。
- **幅**（NFR4.5）: 既定の1組で 360px・768px・1280px。768px 未満で S4 の木と表が縦に積まれ、木で選ぶと表の見出しへフォーカスが移ること、一覧と結果の表の1列目が見えること、Modal が幅いっぱい（左右 16px）であること、どの幅でも横のスクロールが無いこと。
- **道の扱い**（2.2 の基盤の設計 R-05）: `/admin/roles/{id}` と `/admin/groups/{id}` を直接開いて読み込み直しても画面が出ること、S3 → S4 → S5 と移ってブラウザの戻る・進むで道と画面がそろうこと、未保存があるときのブラウザの戻るで確かめが出ること。
- **画面の時間の測り**（`performance-design.md` 4節）: 既定の1組だけで1件のテストにまとめる。見本は前もって `JSON.stringify` した文字列を口の `body` で返す。項目は NFR2.5（100 カラム、記録だけで 1,000 カラム）・NFR2.6（回ごとに保存する値を替える）・NFR2.7（25 ロール × 1万の変わる点、ヒープも記録）・NFR2.8・NFR2.12（1回）。5 回の中央値で判定し、最大と1回目も添付に書く（名前と時間だけ）。目標を超えても統合は止めず、Build and Test が Not Met と値で記録する。
- **書き出し**（13節 Q2: A）: 140 では書き出しのボタンの見た目と押せない形だけを見て、応答のヘッダーの確かめは画面のテスト（`apiClient.download.test.ts`・`roleTransferApi.test.ts`・`useRoleTransfer.test.tsx`）に任せる。差し替えの口に `headers` は足さない。

### 9.2 150 F の流れ（`150-role-permission-flow.e2e.ts`、この Intent の2本までの1本）

前提を自分で作り、初期管理者の状態とロールは変えない。名前は走らせるごとに一意（`E2EF_` と `runTag`）。後始末はしない（Mailpit のメールも消さない）。

1. 初期管理者の資格情報（プロセスの環境変数）で API のトークンを取り、`E2EF_` の版 2 の DSL（スキーマ1つ、テーブル2つ、カラム数個、テーブルを指すメニューの項目1つ）を `POST /api/admin/dsl/preview?source=PASTE` で投入し、`POST /api/admin/dsl/apply`（本文 `{previewId}`）で適用する（照合の警告のまま適用できる）。
2. `createRegisteredUser` で招待から登録まで済ませた利用者を作り、直後に `recordSecretValues` でメールアドレス・パスワード・氏名・`runTag` を値のファイルに書く。
3. 初期管理者で画面からログインし、サイドバーの「ロール」から S3 を開き、`E2EF_` のロールを作る。
4. 作ったロールを開き（S4）、テーブルの主権限を READ にして保存し、「保存しました」と実効の値「READ（明示）」を確かめる。
5. 「割り当て」のタブ（S5）で、2 の利用者を検索して割り当て、表に出どころ「直接」で出ることを確かめる。
6. 2 の利用者で API にログインし、`GET /api/me/work-role` が作ったロールを返し、`GET /api/me/navigation` に 1 のメニューの項目が出ることを確かめる。
7. 見本の形の比べ（NFR6.10、13節 Q6: A）: 初期管理者のトークンで、ロールの一覧・ロール1件・権限の木の3段・割り当て・利用者のロールを読み、`E2EF_` のグループを API で作ってグループの一覧・詳細・グループのロールを読み、`E2EF_` の新しいロールを作る YAML を `POST /api/admin/role-transfer/check` に送って確かめの応答を読み（適用しない）、`shapeCompare` で `roleAdminFixtures.ts` の見本と項目の名前と型が一致することを確かめる。
8. `test.step` の題・注記・添付に値を入れない。初期管理者の資格情報を `webServer.env` に置かない（既存のとおり）。

B9（U7）が同じファイルの 6 の後に、画面での作業ロールの切り替えとサイドバーの業務のメニューの変化を足す（本数は1本のまま）。

## 10. 入口の量の判定（基盤の設計の承認の場の決定 (4)(5)）

| 手順 | すること | 判定・記録 |
|---|---|---|
| 1（Step 3、変更の前） | `caffeinate -i ./gradlew frontendBundleSize` を流し、出力の入口の合計（gzip）と、`frontend/dist/.vite/manifest.json` の `isEntry` の項目から `imports` をたどった集まり（ファイルの一覧）を記録する | 前の値（判定には使わず、比べの基準） |
| 2（Step 14・16・18・20） | 新しい画面は登録で `lazy(() => import('./XxxPage').then(...))` にし、`registration.ts`（入口から静的に読まれる）は画面の部品を静的に import しない。`messages.ts` だけを静的に読む。`UserRolesDialog` は `UserAdminPage` から読む（`UserAdminPage` は既存の遅延読み込み） | 登録のテストで `screen` が `lazy` の部品であることを確かめる |
| 3（Step 27、変更の後） | 同じコマンドで後の値を取り、manifest を読む | **判定**: (i) `src/features/roleadmin/RoleListPage.tsx`・`RoleDetailPage.tsx`、`src/features/groupadmin/GroupListPage.tsx`・`GroupDetailPage.tsx`、`src/features/roletransfer/RoleTransferPage.tsx` の項目が manifest で `isDynamicEntry: true` であること。(ii) 入口からたどった静的な import の集まりに、これらの塊と `UserRolesDialog` を含む塊が無いこと。(iii) 前後の合計の差と、遅延読み込みの塊の gzip の大きさは記録だけ（data router の分の増えを含む。500KB の警告は今のまま）。満たさなければ原因の静的な import を直す |
| 4（Build and Test） | Step 27 の記録を `cicd-pipeline.md` 5節の記録として引き継ぐ | — |

- 判定は manifest を読むだけで、`check-bundle-size.mjs` は変えない（`cicd-pipeline.md` 7節 (vi)）。読む小さなスクリプトは作業の場の一時のファイル（scratchpad）に置き、リポジトリに足さない。
- app-frame-ui（B9）の入口の量の判定（基盤の設計 Q1 A）と同じ形。

## 11. この計画で決めたこと・承認済みの文書との差

承認済みの設計の文書は書き換えず、差をここと `code-summary.md` に書く（`project.md` の決まり）。

| ID | 決めたこと | 理由・出典 |
|---|---|---|
| D-1 | 入口の量の判定を、基盤の設計の承認の場の決定 (4)(5) の形にする（10節） | NFR 設計 5節・基盤の設計 5節の「増えていない」との差。読み直し R-01 |
| D-2 | 木の応答は `{items}` を読む。保存の `scope` に `columnName` を送らない。出どころは応答の `type`（`DIRECT`・`GROUP`）を読み、画面の型の `kind` に写す | 2.3 の A3・A6・A9 |
| D-3 | `toEntries` の「範囲の外」を実装の範囲（スキーマの範囲はスキーマの段だけ）で定める | 2.3 の A7（role の B4-7） |
| D-4 | `validateAdminName` は Cc と U+2028・U+2029 を拒み、Cf を拒まない | 2.3 の A19（group の G-4） |
| D-5 | `ConfirmActionKind` から `viewRoles` も外す | 2.4、5節 (a) |
| D-6 | E2E の番号は 140・150 | 13節 Q1: A |
| D-7 | 150 で、140 の見本のすべての形を本物と比べる（自分の `E2EF_` のグループを作り、確かめの API だけを呼ぶ） | NFR6.10 の読み方、13節 Q6: A |
| D-8 | 入口の差し替えの確かめを恒久のテスト `DataRouterBlocker.test.tsx` にする | NFR6.7 の「小さく確かめる」を見張りとして残す |
| D-9 | 新しい画面の読み直しは共有の木の `nodes` の差し替えで行う（保存の後、スキーマの一覧を新しい配列にする） | cross-cutting の共有の木の決まり（`nodes` を替えると開いている節を読み直す） |
| D-10 | 測りの部品 `u6Measure.ts`・見本の比べ `shapeCompare.ts` を E2E の support に足す | 4.6。`frontend-components.md` に無いファイル |
| D-11 | 重い見本（1,000 カラム・25 万の変わる点）は画面のテストに入れず、140 の測りだけで使う | 5節 (k)。画面のテストのヒープと時間 |
| D-12 | 090 の E2E（管理者でない利用者のリンクの無いことの確かめ）に新しい3つの項目は足さない | 5節 (f)。140 と `registration.test.tsx` が道の直接入力を確かめる |
| D-13 | 補助権限の実効の値は「可」「不可」と、明示のときだけ「（明示）」を添える | 13節 Q4: A。機能設計 D11 の継承の元の表示との差（応答の `inheritedFrom` は主権限だけ） |
| D-14 | `TransferErrorList` と S7 の開いた行の変わる点の表の領域に `role="region"`・`tabIndex={0}`・読み上げの名前を付け、oxlint の1行の除外を日本語の理由つきで使う | 13節 Q7: A（dsl-v2 の D-20 と同じ形）。`.oxlintrc.json` と ESLint の設定は変えない |
| D-15 | role の `code-summary.md` B5-5 の食い違い（`USER`・`GROUP` と書くが実装は `DIRECT`・`GROUP`）は、実装を正とし、U6 の `code-summary.md` に根拠（`RoleSourceResponse.from`）とともに書く。role の記録は書き換えない | 13節 Q5: A |

Part 2 の途中で計画に無い判断が要ったときは、止まって依頼者に尋ね（G1・G2 … の番号で）、答えを `generation-notes.md` と `code-summary.md` の計画との差に書く。

## 12. Build and Test・配備の段・U7 app-frame-ui へ引き継ぐこと

- **Build and Test**: 入口の量の前後の値と判定（10節）、遅延読み込みの塊の大きさ、画面の時間（140 の測り、5 回の中央値・最大・1回目、Not Met のときは値で記録して承認の場で扱いを決める）、E2E の全体の時間、`verify` の時間、画面のテストの件数とカバレッジ。
- **配備の段**: 前の版に戻すと U6 の画面が無くなる（戻しはイメージだけ）。入れ替えの後は開いているタブを読み込み直す（古い遅延読み込みの塊の要求に HTML が返るため。基盤の設計の読み直し R-06）。スモークテストで依頼者がロール・グループ・割り当て・受け渡しを触ったときは、監査に残る操作をその場で聞き、監査の確かめの期待の件数を合わせる（`project.md` の学び）。
- **Observability Setup**: 画面が呼ぶ API の時間は group・role の監視の区画で見る（`monitoring-design.md` 3節）。区画の名前と節の番号の実在の確かめ（基盤の設計の読み直し R-07）。
- **U7 app-frame-ui（B9）**: `main.tsx` は data router（Step 5 で 7.3 に切り替えたときは `BrowserRouter` のまま）。管理の区画の項目の今の項目は、詳細の道（`/admin/roles/:roleId` など）との前方一致で決めるかを U7 で決める。150 の 6 の後に作業ロールの切り替えとサイドバーの変化を足す。骨組みのログアウトでは未保存の確かめが出ないことがある（機能設計 7.2）。

## 13. 依頼者の答え（計画の承認の前に確かめたこと）

計画の段で見つかった7つの点を依頼者に尋ね、すべて A の答えを得た（指揮役から伝達）。答えはこの計画（2.2・2.3・2.4・4節・5節・6節の手順・9節・11節）と `unit-test-instructions.md` に反映した。

| 点 | 答え | 確定した扱い | 反映した所 |
|---|---|---|---|
| Q1 E2E の番号 | **A** | U6 の画面の検査を 140、F の流れを 150 にする（130・135 の後、040 の後、app-frame-ui の 160・170 と重ならない） | 2.4、4.6、9節、11節 D-6、Step 1・24・25 |
| Q2 140 での書き出しのヘッダー（基盤の設計の読み直し R-02） | **A** | 差し替えの口に `headers` を足さない。書き出しのファイル名と上限超えの案内は画面のテスト（`apiClient.download.test.ts`・`roleTransferApi.test.ts`・`useRoleTransfer.test.tsx`）だけで確かめ、140 は書き出しのボタンの状態と axe だけを見る | 2.2、9.1、Step 11・19・24 |
| Q3 候補の検索の文字の確かめの置き場 | **A** | useradmin の `searchInput.ts`（上限 254、前後の空白を除く）を `shared/validation/searchText.ts` へ中身とテストを変えずに移し、useradmin・roleadmin・groupadmin が使う。useradmin の import を直す | 2.3 の A20、3節 C2、4.1・4.5、5節 (b)、Step 6・7 |
| Q4 補助権限の出どころの表示（2.3 の A5） | **A** | 補助権限（CREATE・DELETE）は実効の値「可」「不可」を出し、明示の値があるときだけ「（明示）」を添える。明示が無いときは出どころを添えず、主権限の `inheritedFrom` を流用しない | 2.3 の A5、11節 D-13、Step 8・9 |
| Q5 role の記録の食い違い（2.3 の A9） | **A** | 実装の `DIRECT`・`GROUP` を正として U6 はそれを読む。role の `code-summary.md` は書き換えず、U6 の `code-summary.md` に食い違いと判定の根拠を書く | 2.3 の A9、11節 D-15、Step 28 |
| Q6 150 で形を比べる範囲（NFR6.10） | **A** | 140 の見本のすべての形を比べる。150 の中で自分の `E2EF_` のグループを API で1つ作り（残す）、`E2EF_` の新しいロールを作る YAML を `POST /api/admin/role-transfer/check` にだけ送る（適用しない） | 2.4、9.2 の 7、11節 D-7、Step 25 |
| Q7 中に押せる要素の無い、横に動く表の領域（5節 (h)） | **A** | `TransferErrorList` と S7 の開いた行の変わる点の表は dsl-v2 の D-20 と同じ形（`role="region"`・`tabIndex={0}`・読み上げの名前（ja・en）・フォーカスの見た目）にし、oxlint の `jsx-a11y/no-noninteractive-tabindex` はその行だけを日本語の理由つきの行の除外のコメントで外す。`.oxlintrc.json` と ESLint の設定は変えない | 5節 (h)、11節 D-14、Step 18・19 |

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
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる。ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる（既存のパッケージは全体の合計で判定する）。どちらになるかは、パッケージごとの下限を入れる Bolt で既存のパッケージのカバレッジを実測して決め、結果を記録する。\n- 全体の合計で判定している既存のパッケージ（`backend/build.gradle.kts` の `packagesJudgedByTotal` の一覧。2026-10-04 の時点で 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）。`user.domain`・`user.repository`・`user.service` は Intent 260925-user-management で一覧から外れ、含まない）に手を入れる Bolt では、テストを足してそのパッケージの下限（行 80%・分岐 70%）を満たし、一覧から外してパッケージごとの下限の対象に必ず戻す。「手を入れる」は、そのパッケージの本体のソース（`src/main`）の変更のすべて（説明文だけの直しを含む）を指し、テストだけの変更は含めない。手を入れる見込みのパッケージとそれに伴う作業は、Delivery Planning とコード生成の計画で、各パッケージの今の値を実測して見積もる。カバレッジは、その Bolt のテストを足した後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、値を記録する。一度外したパッケージは一覧に戻さず、一覧を増やす変更はしない。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）。Intent `260923-dsl-schema-loader` の Build and Test で `verify` の時間と colima の VM のメモリを実測したうえで、この形に決めた。コンテナの実行環境が無いときの扱いは Way of Working のとおり。\n- 外へ送るメール（SMTP）を使う機能のテストは、送信の部品をモックで置き換えて済ませず、テストの中で JVM の中に起動するテスト用の SMTP の受け手で実際に受け、宛先・件名・本文（HTML）・言語を確かめる。受け手にコンテナは使わない（コンテナの実行環境が無いときに飛ばしてよいテストは、対象DB のテストだけのままとする）。送信の失敗（受け手が接続を拒む・応答しない）のテストも必ず入れる。実在の宛先へは送らない。受け手の具体の道具は、採用の前のライセンスの確認とあわせて設計の段で決める。\n- 画面からの一連の操作を確かめるテスト（E2E）は Playwright（`./gradlew e2eTest`）で書き、`./gradlew verify` と CI の外に置く。本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す。新しく足す流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作る。複数の機能の Intent を1つに束ねた Intent では、束ねた元の Intent ごとに数える（Intent 261004-role-menu は最大2本）。既存の E2E を新しい形（権限の形など）に合わせて書き換えることは、本数に数えない。利用者の状態を変える操作（利用停止・管理の権限の変更・ロックなど）は、その流れで自分で作った利用者だけを対象にし、初期管理者の状態は変えない。役割・権限を変える操作も、その流れで自分で作った利用者と役割だけを対象にし、初期管理者は変えない（E2E は1つのアプリ・内部DB・初期管理者を全ファイルで共有して順に流すため）。E2E は、画面・認証に関わる変更を統合する前と、リリースの前に手元で実行する。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- リポジトリは公開のため、テストデータのメールアドレスは予約のドメイン（`example.com` など）だけにし、実在しそうな氏名・宛先を置かない。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- 画面のテストで、描画の後（`useEffect` などの効果）に反映される値は、操作の直後に同期で確かめず、`waitFor` で待って確かめる。テストの時間の上限（Vitest の既定 5 秒など）は、原因を確かめずに延ばさない。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。\n- 不安定なテストと CI の失敗は、次の1つの決まりで扱う。\n  - 手元で再現した不安定なテストは、原因を直すまで統合しない（次へ進まない）。\n  - 手元で再現しないものは、再現の試みに先に時間の上限を決め、見立てと試みの範囲を記録したうえで、CI の再実行で通れば進めてよい（不安定と確かめられていない扱い）。\n  - 同じテストが二度目に落ちたら、原因を直すまで次へ進まない。\n  - 失敗を直さずに次の Intent へ持ち越すのは、依頼者の決定があるときだけとし、決まりとの差を記録する。\n- 認証・認可・監査に関わる機能では、次のテストを必ず書く。★印の項目は要件（しきい値や動作）が未確定のため、要件定義で決めてからテストを書く。この一覧の「管理者」は管理の権限を持つ利用者を、「管理の権限」はその判定に使う権限を指し、権限の形（真偽値1つか役割・権限か）によらずに読む。権限の具体の形（役割・権限の名前など）は、要件で決まった後に書き足す。\n  - アカウントロック: 失敗回数のしきい値の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロック中は正しいパスワードでも拒否、ログイン成功時の失敗回数の扱い。★しきい値、回数を数える期間、ロックの解除方法\n  - ログイン: 存在しないユーザーとパスワード誤りで、応答からユーザーIDの存在を推測できないこと\n  - トークン: 有効期限の境界（直前は有効／直後は無効）、署名の改ざん、署名方式の指定を悪用した改ざん（`alg: none` 等）、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセストークンが有効期限まで使えること（決定済みの仕様として明示する）。★各トークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか\n  - 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い／不正なときの動作、パスワードがログに出ない。★設定が無いときに起動を止めるか\n  - 認可: 未認証（401）、その権限を持たない（403）、持つ（200）をサーバー側のテストで確かめる（画面で管理メニューを隠すことはサーバー側の検査の代わりにしない）\n  - 監査ログ: 対象イベントごとに必須項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること。★監査ログの書き込みに失敗したときに操作を失敗させるか\n  - 秘密情報の漏えい: ログ・監査ログの出力にパスワード・トークンの値が含まれないこと\n  - 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる、外部エクスポートが既定で無効であること\n  - 画面: ログイン画面のアクセシビリティ検査、ロック時のメッセージ表示、ログアウトでトークンが破棄されること\n  - 招待と登録の完了: 招待の有効期限の境界（直前は有効／直後は無効）、使い終えた招待・取り消した招待の再使用の拒否、改ざんした招待・存在しない招待の拒否（応答から利用者の存在を推測できないこと）、招待中（登録が終わっていない）の利用者はログイン・トークンの更新・アクセストークンの認証のどれでも拒否されること。★招待の有効期限の値、再送・取り消しの扱い\n  - パスワードの変更: 今のパスワードの確かめ、パスワードの規則の境界、変更の後のリフレッシュトークンの扱い（決めた側の動作を明示したテストにする）。★パスワードの規則、変更の後にほかの端末のリフレッシュトークンを無効にするか\n  - 利用停止: 停止中の利用者は、ログインの照合・トークンの更新・アクセストークンの認証の3つの入口のすべてで拒否されることを、入口ごとにサーバー側のテストで確かめる。停止を解いた直後は3つの入口のすべてで受け付けること。停止の前に出したリフレッシュトークン・アクセストークンの扱いは、決めた側の動作を明示したテストにする。★停止を応答から推測できてよいか、停止の前に出したトークンの扱い\n  - 管理の権限の変更: 管理の権限を与えた直後・外した直後の次の要求で、管理の API の 403／200 がサーバー側で切り替わること（画面が持つ古い権限に頼らない）。権限を外す前に出したトークンの扱いと、自分の管理の権限を外す操作の扱いは、決めた側の動作を明示したテストにする。★権限を外す前に出したトークンの扱い、自分の管理の権限を外す操作の扱い\n  - ロックの解除: 解除の直後に正しいパスワードで入れること、解除の後の失敗回数の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロックの状態の行が無い利用者の解除。時刻は注入した時計で動かし、実時刻と `sleep` に頼らない。★解除の後の失敗回数の扱い、ロックの状態の行が無いときの応答\n  - 最後の管理者の保護: 最後の有効な管理者（管理の権限を持つ利用者）から管理の権限を外す・利用を止める操作は拒否され、拒否の後に状態が変わっていないこと。2人の管理者が同時に互いの管理の権限を外す・利用を止めても、有効な管理者が 0 人にならないこと。同時の重なりはスレッドの数に頼らず、待ち合わせで確実に作る。★最後の管理者の数え方（止めた管理者を数えるか）\n  - 管理の API の認可: 足す管理の API のすべてについて、未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。停止中の管理者は管理の API を呼べないこと。\n  - 要求の改ざん: 管理の API の外（`/api/me` など）から、要求の本文の値を変えて自分の管理の権限や利用者の状態を変えられないこと（一括代入の防止）。\n  - 管理の操作の監査: 利用者の権限・状態を変える操作（管理の権限の付け外し・利用停止と再開・ロックの解除）ごとに、操作した人・対象の利用者・結果が記録されること。★拒否した操作（403・最後の管理者の拒否など）を記録するか\n  - 利用者の管理の漏えい: 一覧・詳細の応答と監査の行に、パスワードのハッシュ値・リフレッシュトークン・ロックの判定の内部の値が含まれないこと。TRACE のログを有効にして一覧・詳細を読んでも、メールアドレス・氏名がアプリのログに出ないこと（既存の `*SecretLeakIT` と同じ形で確かめる）。\n- 役割・権限の機能では、次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 権限ごとの API の認可: 足す・変える API のすべてについて、未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。403 を確かめる利用者は、権限が何も無い利用者ではなく、要る権限だけを欠く利用者（ほかの権限は持つ）にする。API と権限の組は、パラメーターを使うテストで表として書く。停止中の利用者は、権限があっても通らないこと。\n  - API の分類の網羅: アプリが持つすべての API が「公開・ログインだけ・権限が要る（どの権限か）」のどれかに明示して分類されていることを確かめるテストを置き、分類の無い API があれば失敗させる（`/api/**` の既定は「ログインだけ」で、権限の決まりを書き忘れるとログイン中の誰でも呼べるため）。足した API を分類に足し忘れると落ちる形にする。分類の持ち方と仕組みの置き場は設計の段で決める。\n  - 割り当ての変更の反映: 役割・権限を付けた直後・外した直後の次の要求で、403／200 がサーバー側で切り替わること（画面が持つ古い権限に頼らない）。★変更の前に出したトークンの扱い\n  - 権限の昇格・一括代入・IDOR の防止: 操作する人が持たない権限を、自分や他人に与えられないこと。要求の本文の値を変えて自分の役割・権限を変えられないこと、役割の作成・変更の本文に許していない項目（ID・組み込みの印・作成者など）を足しても反映されないこと。役割・割り当ての ID を差し替えた要求（他人の割り当て・存在しない役割など）は拒否され、状態が変わらないこと。★組み込みの役割の有無と、その削除・権限の取り上げの保護\n  - 自分自身への操作と最後の管理者の読み替え: 自分の役割を外す操作と、最後の有効な管理者を役割でどう数えるかは、決めた側の動作を明示したテストにする。同時の重なりはスレッドの数に頼らず、待ち合わせで確実に作る。★自分の役割を外す操作の扱い、最後の有効な管理者の役割での数え方\n  - 監査: 割り当ての変更ごとに、操作した人・対象・変えた中身・結果が記録されること。★役割そのものの作成・変更・削除を記録するか、拒否した操作（403・昇格の拒否など）を記録するか\n  - 画面の出し分け: メニュー・画面を隠すことを、サーバー側の検査の代わりにしない。権限の無い画面へ直接移ったときに 403 の表示になること。\n  - 性質ベースのテスト: 役割から権限の集合を求める関数（サーバー側）と、権限でメニューの枝を落とす関数（画面側）は純粋な関数にし、jqwik・fast-check で性質（持たない権限の API・項目が出ない、役割を足しても権限は減らない など）を確かめる。\n- N 階層のメニュー（ナビゲーションの木）の画面では、次のテストを必ず書く。深さの上限の具体的な値は設計の段で決め、決めた値の境界で確かめる。\n  - 開閉と今の項目: 開閉の状態（`aria-expanded`）と今の項目（`aria-current`）が正しく出ること、キーボードだけでたどれること。\n  - 深さの上限: 上限ちょうどは受け付け、上限を超えると拒否されること。\n  - 表示の木を作る関数: 定義の木から表示の木を作る純粋な関数（権限で枝を落とす・並べる）に、fast-check の性質ベースのテストを当てる（例: 権限の無い項目が出ない、子の無い枝が残らない）。\n  - 信頼できない入力: メニューの定義（`label`・`icon`・`table`）を信頼できない入力として扱う。表示名に `<`・`>`・`&`・`\"`・`'` を含めても文字として出る（HTML として描かない）こと、`icon` を許す名前の一覧と照らすこと、`table` の名前から組み立てる道をエンコードし、アプリの中の決めた形から外れない（外部の URL・`javascript:` の道を作らない）こと。★一覧に無い `icon` を拒否するか既定のアイコンにするか\n  - make-you-chic-ui の部品に頼る振る舞い: 開閉・`aria-current`・キーボードなど、make-you-chic-ui の部品に頼る振る舞いも、このリポジトリの画面のテストと実際のブラウザの axe で確かめる（make-you-chic-ui のテストはこのリポジトリの CI の対象外のため）。axe は、展開した状態・深い階層・長い名前で、ブランドカラーとテーマのすべての組について行う。\n- 利用者へメールを送る機能では、Code Style と `project.md` のメールの決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 本文のエスケープ: 差し込む利用者の値（氏名・メールアドレスなど）に `<`・`>`・`&`・`\"`・`'` を含めても、タグや属性としてそのまま出ないこと。利用者の値をエスケープしない差し込み（`{{{ }}}`・`{{& }}`）で入れていないこと、属性の値を二重引用符で囲んでいること（エンジンの `{{ }}` は `'` を置き換えないため）\n  - テンプレートの描画: 日本語と英語のすべてのテンプレートを描けて、件名が空でないこと、差し込み漏れ（`{{` の残り）が無いこと、ライセンスヘッダーが描いた本文に出ないこと\n  - 招待の URL: 設定したベース URL だけから組み立て、要求の Host ヘッダーから組み立てないこと。ベース URL の設定が無いときに要求の Host を変えても、招待の URL に出ないこと。★ベース URL の設定が無いときの動作\n  - トークンと URL の漏えい: 招待のトークンと招待の URL が、アプリのログ・監査ログ・トレースの属性・エラー応答に含まれないこと（既存の `*SecretLeakIT` と同じ形で確かめる）\n  - ヘッダーへの差し込み: 宛先・件名・差し込む値に改行（CR・LF）を含めても、メールのヘッダーが増えず拒否されること\n  - 送信の失敗: 送信が失敗したとき（受け手が接続を拒む・応答しない）に、宛先のメールアドレス・SMTP の応答・資格情報が応答とログに含まれないこと。★送信が失敗したときの業務の動作（招待を失敗させるか、再送に回すか）\n- 利用者が投入する DSL（YAML）を読み込む機能では、Code Style の「信頼できない入力」の決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。上限の具体的な数値は設計の段で決め、決めた値の境界で確かめる。\n  - 大きさ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 入れ子の深さ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 別名（アンカー）: 展開の数が上限を超えると拒否され、別名の展開の爆発で処理が止まらない\n  - タグ: 任意の型を作るタグ（`!!` など）を含む DSL は拒否され、型が作られない\n  - 重複キー: 同じキーが重なる DSL はエラーになり、後の値で黙って上書きされない\n  - JSON Schema の `$ref`: 外部の URL を取りに行かない\n  - 拒否の応答: Problem Details の形で返り、YAML・JSON Schema の部品の例外のメッセージを含まない\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22)"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) \n- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) \n- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) \n- 同時の重なりを確実に作るため、本番のコードを変えずに、監査の書き込みの時間を測る LongSupplier（AuditEventListener で2本目を借りる直前に呼ばれる）をテストで差し替えて待ち合わせる方式にした。既存の LoginConcurrencyIT は 8 スレッドでプールの 10 に届かず、前回の失敗のログインで尽きなかった理由の1つと見られる。 (learned 2026-09-23) \n- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) \n- 修正の前の設定（例: 上限 1g）も修正の後の環境（例: CPU 4 の VM）で流し（pre1g）、要件の前提（VM を上げても F3 が起きる）を実測で裏付ける。 (learned 2026-09-23) \n- パッケージごとのカバレッジの下限と SpotBugs の SQL_ の関門を U1 の計画に入れた。team.md の決まりだが今のビルドに無く、この Intent で最初に作る単位のため。U1 の設計の文書には無い作業。 (learned 2026-09-24) \n- U4 で既存の AuditSecretLeakIT の列の一覧に V6 の4列を足し、テストの JVM のヒープを 1g にした。前者は承認済みの V6 と必ず食い違うため、後者は構造の検査がクラスを持ち続け U3 の 10MB 超えのテストでヒープが尽きたため。どちらも計画に無い変更で、依頼者に確かめる。 (learned 2026-09-24) \n- 応答しない対象DB の TIMEOUT の確かめに、テストの中で開いた ServerSocket（受け付けて何も返さない）を使う。外の端末に頼らず確実に再現できる代わりに、本物の DB の遅延ではない。 (learned 2026-09-24) \n- パッケージごとのカバレッジの下限は新しいパッケージだけに当てた。実測で audit.service（行 77.2%）・common.health（行 79.2%）・auth.repository（分岐 50.0%）が単独で下回ったため（team.md の決まりどおり）。既存の 22 パッケージを一覧で外し、新しいパッケージは自動で対象になる。 (learned 2026-09-24) \n- 負荷の試験で接続プールが尽きたかを確かめるときは、使い捨てのアプリにだけ MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics を渡して /actuator/metrics の hikaricp の値を読み、数秒ごとの使用中の数ではなく、待ちの時間切れの累計と借りるまでの待ちの最大で判断する（配備したアプリの公開の範囲は変えない）。 (learned 2026-09-24) \n- k6 などの長い試験は caffeinate -i を付けて流し、PC の自動のスリープで要求が止まって結果が崩れるのを防ぐ（バッテリー駆動のまま 414 秒スリープし、要求が 6分52秒止まった）。内部DB に SQL で直接入れた試験用の利用者はロックの状態の行が無いため、同時のログインを流す前に1人ずつログインさせて行を作る。 (learned 2026-09-24) \n- 軽い API の性能は、投入を重ねて内部DB のファイルが膨らんだ状態（悪い側の条件）のまま測る。メモリの最大（memory.peak）を比べる試験の前は、アプリのコンテナを作り直して前の最大の値を消す。 (learned 2026-09-24) \n- Performance Validation の段が無いため、2件目の直しの負荷の試験での確かめ（Q5: A）の持ち主を Build and Test とした（project.md の学びどおり）。 (learned 2026-09-24) \n- colima の PC で ./gradlew verify を流すときは、README の DOCKER_HOST と TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE をシェルに渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗した。付けて流し直した結果を基準とした。 (learned 2026-09-25) \n- unit-test-instructions.md の k6 inspect のコマンドは --include-system-env-vars が無いと場面の名前が undefined になり確かめにならなかった。承認済みの文書は変えず、実際には付けて流し、code-summary.md に差を記録した。 (learned 2026-09-25) \n- 1回目の verify で AccessTokenApiIT の 6 件が接続の失敗で落ちた。変更の経路に触れず、同じ時刻に Gradle の作業プロセスとの接続も時間切れだったため PC の負荷による一時的な失敗と見立て、段の中の直しの1回目としてコードを変えずに verify を流し直して通した。原因は確かめていない。 (learned 2026-09-25) \n- 依頼者の判断で、試験のイメージを mastersmith:local ではなく mastersmith:followup-fixes で作り、perf/README の手順（local を作り直す）から外れた。配備したアプリは k6 のあいだ止め、約 17 分後に同じコンテナで起動し直した。 (learned 2026-09-25) \n- 試験の後にロックの状態の行の数を数える問い合わせの列の名前を誤り、使い捨ての環境を消した後で取り直せなかった。合格の条件（ログインの checks と 500 の件数）で判定した。消す前に確かめの結果を見てから片付けるべきだった。 (learned 2026-09-25) \n- Q4（確かめられたときだけ直す）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違いうるため、追加の質問 F3 で、再現できなければ不安定と確かめられていない扱いとして統合してよいことを確かめた。 (learned 2026-09-25) \n- 要件 FR2.2（目標は計画の承認の場で決める）を満たすため、依頼者の決定（Q1: A）で、計画を書く前に dslMixed でメモリの内訳を測ることにした。測る間は配備したアプリを止める。 (learned 2026-09-25) \n- 生成で、テストの既定で MBean の登録を無効にする置き場を、計画の候補の TestDatabase ではなくテストの EnvironmentPostProcessor にした。TestDatabase を使わずに Spring を起動するテストのクラスが 15 あり、漏れるため。再現の結合テストは 12 回では伸びが小さく判定がはっきりしないため、24 回・履歴の上限 3 にした。 (learned 2026-09-25) \n- AccessTokenApiIT は、同じ例外の文言を仕組み（colima の IPv4 の転送と Java の IPv6 の待ち受けの番号の重なり）ごと再現できたことを原因の確認とみなし、依頼者の決定（D7）でテストの JVM に preferIPv4Stack を付けて直した。実際の1回目がこれで起きたかは確かめられず、今回の繰り返しでは重なりは起きなかった。 (learned 2026-09-25) \n- Test Strategy は Minimal だが、要件 FR5 と計画の「Build and Test に引き継ぐこと」のため、結合・性能・セキュリティの手順書も作り、この段で dslMixed・--storage --compact（40 回）・詰め直しの最中のログイン・refresh・dslCycle を流した。 (learned 2026-09-25) \n- 計画の dslCycle を最初の台本に入れ忘れ、配備したアプリをもう一度（約 4 分）止めて流した。負荷の試験の台本を書く前に、計画の「Build and Test に引き継ぐこと」の項目を一つずつ台本の手順と突き合わせる。 (learned 2026-09-25) \n- 実際のブラウザのアクセシビリティの検査（axe）は、誤りを出した状態と、現実に近いデータ（2語の氏名など）で、ブランドカラーとテーマのすべての組について行う。user-management の U7 の 080 で初めて、make-you-chic-ui の Avatar（頭文字が2文字のとき）と dark の FormField の誤りの文字のコントラスト不足が出た（050〜070 は誤りの状態を見ず、氏名が1語だったため出なかった）。 (learned 2026-09-28) \n- Playwright の webServer.env に置いた値は json の報告に残る。E2E の仮の資格情報はプロセスの環境変数で渡し、報告の部品の確かめと json の文字列の検索で、パスワード・トークン・メールアドレスが入っていないことを確かめる（user-management の U4 で見つかった）。 (learned 2026-09-28) \n- CI（66fe981）の verify が2回とも別々のテストの時間切れ（H2CompactionByPoolSuspensionIT の接続の待ち 10 秒、InvitationAdminPage.test.tsx の既定 5 秒）で失敗し、U4〜U7 の NFR7.1 が Not Met だったが、依頼者の決定「次のintentでコントラストと一緒に直す。このintentではこのまま進める。」で受け入れた失敗とした。team.md の「CI が失敗したら次に進む前に直す」と食い違うため test-results.md 8.1 に差を記録した。 (learned 2026-09-28) \n- k6 の登録の完了と取り消しの場面は、決定の文言（招待を VU の数だけ）ではなく既定 100 回・招待 100 件とした。トークンは1回しか使えず 10 件では p95 の意味が薄いため、U3 の NFR6.4 の「流す回数以上の招待を用意する」に合わせた。承認の場で確かめる。 (learned 2026-09-28) \n- caffeinate -i は場面ごとではなく、試験の台本全体を包む形で付ける。場面ごとに起こし直すと切れ目で守りが外れ、PC が眠る（user-management の Performance Validation で 92 秒眠り、要求が 1 分 29 秒遅れた）。遅れが出たときは pmset -g log でスリープを確かめる。 (learned 2026-09-28) \n- 応答しないメールの受け手は、Mailpit を docker pause で一時停止して作る（接続は受け付け、何も返さない）。コンテナを止めると接続の失敗になり、時間切れの確かめにならない（user-management の Performance Validation）。 (learned 2026-09-28) \n- Q1 で原因を確かめずに上限を延ばす答え（C）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違うため追加の質問 F1 で確かめ、延ばしたうえで失敗時の診断を足し、差を記録する形（B）になった。Q3 の「すべて試す」は verify で判定できない Temurin 26・logback-appender の扱いを F2 で確かめ、見送り（C）になった。 (learned 2026-09-29) \n- 診断の確かめで、上限 1 ミリ秒は Awaitility の問い合わせの間隔（100 ミリ秒）より短く設定の誤りで弾かれ、150 ミリ秒では手元で接続がすぐ 0 本になり時間切れにならなかった。上限 500 ミリ秒と、待ちの条件を一時的に満たせない形（== -1）にして時間切れを起こした。確かめた後に git checkout で戻した。 (learned 2026-09-29) \n- 警報が鳴ることは、しきい値を 50 ms に下げた警報の決まりの写しを使い捨ての環境に読み込ませて確かめた（Q1: A）。本当に遅い応答を作るより早く PC の負荷も小さいが、しきい値の値そのもので鳴ることは確かめていない。途中で ms-check-p95 のしきい値が 1000 ms ではなく 300 ms（バケットの境界に無い）と分かり、要件の前提 A3 と食い違った。 (learned 2026-09-29) \n- 表示の設定を画面の操作の直後に同期で確かめるテストが、負荷の高い CI で2件（ShellLayout・PreferencesPage）落ちた。描画の後の効果（useEffect）で反映される値は waitFor で待って確かめる。同じ形の RegistrationPage も合わせて直した。 (learned 2026-09-29) \n- Delivery Planning でのカバレッジの実測は、./gradlew verify 全体ではなく :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport で行い、jacocoTestReport.xml から手を入れる見込みのパッケージの値を読む（user-admin で約 5 分）。 (learned 2026-10-01) \n- 接続プールの見積もりを確かめる負荷の試験には、上限に届く形（プールの上限を下げた場面など）を含める。上限に届かない負荷では、見積もりが誤っていても合格する（user-admin の U3 の NFR 要件のレビュー R-02）。 (learned 2026-10-01) \n- 画面のはみ出しの確かめ（E2E・axe）では、画面全体の横のスクロールだけでなく、開いたメニュー・ポップアップなど画面に固定で置く部品が画面の中に収まることも確かめる。表の右端に置く make-you-chic-ui の Dropdown は placement に bottom-end を指定する（user-admin の配備の後のスモークテストで、行の「操作」のメニューが右へはみ出していたのを E2E 120 が拾えなかった）。 (learned 2026-10-03) \n- k6 の http_req_duration が、colima の VM の時計が負荷の間にホストより約 2 秒ずれて合わせ直されるため一部崩れた（待ちの最小 0 ms、繰り返しより長い約 3 秒の値）。1回の繰り返しが要求1つの loginSuccess では、単調な時計の iteration_duration とサーバー側の最大が合うため、判定は iteration_duration で行い、http_req_duration は並べて記録した。前の Intent の 939.6 ms も同じ影響を受けていた可能性がある。 (learned 2026-10-04) \n- 1〜6回目で繰り返しの時間の p95 が2回 1 秒を超え、依頼者が VM の時計を合わせて測り直しを求めた（F1・F2 の Other）。測り直しの7〜9回目は 1 秒を下回ったため、1〜6回目を FR2.2a の条件がそろわない回として外した（承認の場で確かめる）。時計のずれを ssh の前後のホスト時刻の中間と比べる誤った測り方で 2.2 秒と読み、測り方を直して範囲（VM−終わり、VM−始め）で記録し直した。配備したアプリは2回止めた（約 12 分と約 4 分）。 (learned 2026-10-04) \n- k6 の判定は1回の繰り返しに要求1つの場面の iteration_duration で行い、トークンは setup() で取って場面を 3 分にした。時計のずれに強い代わりに、操作が2つ以上の場面は op のタグが iteration_duration に付くかを台本で確かめる必要がある（group のレビューの R-01 を先に避けた）。 (learned 2026-10-05) \n- 上限を下げた k6 の場面を置かず、接続の数は結合テスト NavigationConnectionUsageIT で決定的に確かめる。読み取りだけで1要求1本のため詰まりが起きず、role のレビューの R-02 の「k6 では見積もりの誤りを見分けにくい」を避けられる。代わりに、負荷の下での待ちの長さは測らない。 (learned 2026-10-05) \n- 違反のテストは先の側を確定させてから書く待たない違反、上限切れは H2 の上限まで放さない形にした（Q3: A）。時間の境に合否を預けない代わりに、待った後の違反の経路は区分の単体テストだけで確かめる。 (learned 2026-10-06)"
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
  "input_sha256": "sha256:1b1210c2e61bcacd9398989674a4d7c1a18abbfde6a25bf393014ab8e8f2f363",
  "contract_sha256": "sha256:0800cfde8b8915bab9d374a3c5479c6961cc23a534522745ba4170d1b16e7349"
}
```
