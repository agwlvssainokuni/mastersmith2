# S8「自分の権限を見る」の木が固まる不具合の直し

Intent 261004-role-menu の Deployment Execution のスモークテスト S8 で見つかった不具合の直しの記録。作業ブランチは `fix/261004-role-menu-mypermissions-tree`（develop の 876042b から作成）。コミット・統合・配備のやり直しは、依頼者の承認を得てから指揮役が行う（この記録の時点では未コミット）。

## 1. 原因

「自分の権限を見る」（`frontend/src/features/mypermissions/`）の木で、テーブルの節の ▸ を押すと、テーブルの節が同じ id の自分自身を子に持ち、描画が止まらずブラウザが固まる。画面だけの不具合で、権限の有無とは関係しない。

- `useMyPermissions.ts` の `toTreeNode` が、API の `hasChildren`（カラムがあるテーブルの節では真）をテーブルの節にもそのまま渡していたため、テーブルの節に ▸ が出ていた。設計では木はスキーマとテーブルの2段で、カラムは右の表に出す（functional-spec.md の D24）。
- ▸ を押すと `loadChildren` がテーブルの id（`["SALES","ORDER"]` の形）で呼ばれ、`const [schema] = JSON.parse(id)` で先頭の名前だけを取り、`listMyTables(schema)` で同じスキーマのテーブルの一覧（押したテーブル自身を含む）を返していた。
- 共有の木（`shared/tree/SharedTreeNodeItem.tsx`・`SharedTreeView.tsx` の `visit`）は、節の id が子と重ならない前提（`types.ts` の「1つの木の中で読み込んだすべての節を通じて重ならない」）で作られており、自分自身を子に持つと繰り返しが止まらない。
- ロールの権限の設定（`features/roleadmin/useRolePermissions.ts` の `treeNodeOf`）は `hasChildren && tableName === null` にしており、起きない。

## 2. 直したファイル

依頼者が決めた範囲（主な直し＋守り＋再現のテスト。共通の木 `SharedTreeView` には手を入れない）で直した。

| ファイル | 変更 |
|---|---|
| `frontend/src/features/mypermissions/useMyPermissions.ts` | 主な直し: `toTreeNode` の `hasChildren` を `node.hasChildren && node.tableName === null` にした（roleadmin の `treeNodeOf` と同じ形）。守り: `loadChildren` は名前が1つの id（スキーマ）以外では要求を出さずに空の配列を返す。 |
| `frontend/src/features/mypermissions/MyPermissionsPage.test.tsx` | 見本のテーブル `TABLE` を本物の API と同じ `hasChildren: true` に直し、再現のテストを1件足した。 |
| `frontend/src/features/mypermissions/useMyPermissions.test.tsx` | 新しいファイル（フックの単体テストの既存のファイルが無かったため）。再現のテストを2件置いた。Apache-2.0 のヘッダー（2026, agwlvssainokuni）付き。 |
| `frontend/e2e/160-app-frame-accessibility.e2e.ts` | 「my permissions with the column table」の段に、開閉のボタン（`shared-tree-toggle`）がスキーマの1つだけであることを確かめる1行を足した（既存の E2E の書き足しで、本数に数えない）。E2E の見本（`e2e/support/navTreeFixtures.ts` の `myTablesWire`）はすでにテーブルの節を `hasChildren: true` で返しており、変えていない。 |

守りで「拒否」ではなく「空を返す」を選んだ理由: 拒否すると共有の木が「読み込みの失敗」と再試行のボタンを出すが、何度押しても直らず、利用者に誤った案内になる。空を返すのは roleadmin の `loadChildren`（スキーマ以外の id では `[]` を返す）とも同じ扱いである。理由はコードのコメントにも書いた。

## 3. テスト（直す前に失敗したことの確かめ）

再現のテストを先に書き、直す前に流して3件すべてが失敗することを確かめてから直した。

| テスト | 直す前 | 直した後 |
|---|---|---|
| `MyPermissionsPage` › shows no expand button on a table node even when the API says it has children（スキーマを開いた後、`shared-tree-toggle` が1つだけ。▸ は押さない） | 失敗 | 成功 |
| `useMyPermissions` › keeps hasChildren on a schema node and turns it off on a table node | 失敗（テーブルの節の `hasChildren` が真） | 成功 |
| `useMyPermissions` › does not send a request when asked for the children of a table node | 失敗（テーブルの一覧を返し、要求を出した） | 成功 |

見本の `TABLE` を `hasChildren: true` に直しても、`mypermissions` の既存のテスト（17件）は直す前から成功していた（▸ を押さないため）。

## 4. 確かめの結果（実測）

| 確かめ | 結果 |
|---|---|
| フロントの書式（`npm run format:check`） | 成功 |
| lint（`npm run lint`、oxlint ＋ ESLint） | 成功（指摘 0 件） |
| 型（`npm run typecheck`） | 成功 |
| ライセンスヘッダー（`npm run license:check`） | 成功 |
| 直したファイルの単独（`vitest run src/features/mypermissions`）を3回 | 3回とも 4 ファイル・20 件すべて成功 |
| フロントの単体テスト全体（`npm test`）1回 | 186 ファイル・1,584 件すべて成功 |
| `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡し、`caffeinate -dims` で包む。2026-10-14 15:32:49〜15:47:53） | BUILD SUCCESSFUL（15分3秒）。バックエンドの単体テスト 2,102 件・結合テスト 1,165 件で失敗・誤り・飛ばしは 0 件（test-results の XML で数えた）。フロントのカバレッジは行 95.42%・分岐 90.25%（下限 80%・70% を満たす） |
| `./gradlew e2eTest`（`caffeinate -dims` で包む。15:48:30〜15:59:02） | BUILD SUCCESSFUL。230 件すべて成功（10.4 分）。足した1行を含む 160 の app frame の流れは 20 の組で成功 |
| スリープの確かめ（`pmset -g log` の verify と E2E の時間帯） | 記録は Assertions だけで、Sleep・DarkWake は無かった |
| WAR（`backend/build/libs/mastersmith.war`、verify で作り直し、E2E では `bootWar` が UP-TO-DATE） | SHA-256 `f5293bed7fe2062271a34b84b48586d28431f294d75b1045ed459f0b8c58a376`（102,383,236 バイト、15:47） |

配備したアプリ（:8080）・mailpit・見本の対象DB（postgres）は止めず、変えていない（E2E はポート 18081 で起動し、ぶつからなかった）。確かめの後も3つとも動いている。

## 5. 同じ共通の木を使うほかの画面の確かめ

`SharedTreeView` を使う画面は、`features/mypermissions/MyPermissionsPage.tsx` と `features/roleadmin/PermissionPanel.tsx`（ロールの権限の設定）の2つだけである（ほかは `features/README.md` の説明文）。

- ロールの権限の設定は、`treeNodeOf` が `hasChildren && tableName === null` でテーブルの節に開閉のボタンを出さず、`loadChildren` も `parseNodeId` の結果がスキーマでないとき（`tableName !== null`）は要求を出さずに `[]` を返す。節の id は `targetKey`（名前の JSON の配列で、スキーマは1つ・テーブルは2つ）で、スキーマの子（テーブル）は親と id が重ならない。節の id が子と重なる作りは無い。
- 直していない（依頼の範囲どおり）。

## Sources

- 依頼者の決定（指揮役の依頼の本文）: 直す範囲（主な直し＋守り＋再現のテスト、共通の木には手を入れない）、確かめの手順。
- `aidlc/spaces/default/intents/261004-role-menu/operation/deployment-execution/deployment-log.md` の S8 の記録（原因の読み、直しの方針）。
- コード: `frontend/src/features/mypermissions/useMyPermissions.ts`、`frontend/src/features/roleadmin/useRolePermissions.ts`、`frontend/src/features/roleadmin/permissionDraft.ts`、`frontend/src/shared/tree/types.ts`・`SharedTreeView.tsx`・`SharedTreeNodeItem.tsx`、`frontend/e2e/support/navTreeFixtures.ts`。
- 実行の記録: verify・E2E・vitest の出力、`backend/build/test-results/` の XML、`pmset -g log`。

## Assumptions & Open Questions

- [assumption] 守りは「空を返す」を選んだ（理由は2節）。拒否にしたい場合は依頼者に確かめる。
- 配備のやり直しの前に、上の WAR の SHA-256 と、配備に使う WAR が一致することを確かめる必要がある（Dockerfile は手元の WAR を写すだけのため。project.md の学び）。
- 統合の後の CI の結果は未確認（統合・push はこの記録の後）。

# 2. ShellLayout.test.tsx の Unhandled Rejection

develop の先頭の CI で出た不安定なテストの直しの記録。作業ブランチは `fix/261004-role-menu-shelllayout-test`（develop の先頭から作成）。この記録の時点では未コミット。直したのはテストだけで、画面の本体のコードは変えていない。確かめの途中で別のテスト（`AppRouter.test.tsx`）が1件落ち、依頼者の決定（A: 同じブランチで直す）で同じ形の直しを足した（2.5）。

## 2.1 起きたこと（CI の記録）

| 項目 | 内容 |
|---|---|
| run | 38435315180（push、`develop`、コミット `ae794e4`。`ae794e4` は `aidlc/` の下だけの記録のコミットで、`aidlc/` の外のコードは `005730a` と同じ（`git diff 005730a ae794e4` で `aidlc/` を除くと差が無い）） |
| 1回目 | failure。ジョブ `./gradlew verify` が 2026-10-13T17:01:44Z〜17:06:07Z で `:frontendTest` が失敗。テストは 185 ファイル・1,581 件すべて通過し、`Errors 1 error` の1件で失敗した |
| 2回目（失敗したジョブの再実行） | success。ジョブ `./gradlew verify` が 2026-10-13T17:41:30Z〜18:03:16Z |
| 誤りの中身 | `Unhandled Rejection` の `Error: down`。作った場所は `frontend/src/app/layout/ShellLayout.test.tsx:702:30`（`act(() => pending.reject(new Error('down')))`）。直前に動いていたテストは「keeps the saved expanded ids while the tree is loading or failed, and drops unknown ones once loaded」 |

同じファイルでは `0415b94` の CI の1回目にも失敗が出たが、そのときは別のテスト（作業ロールの切り替えのボタンを同期の `getByRole` で探していた）で、原因も違う（`005730a` で `findByRole` に直した。ci-config.md の 6.2）。team.md の「同じテストが二度目に落ちたら」には当たらないが、同じファイルで2回目の CI の失敗である。

## 2.2 原因

### 確かめた事実

- 偽の業務のメニューの読み込み（`frontend/src/app/testing/frameTestSupport.tsx` の `fakeNavigationLoad`）は、答えに `deferred()` を渡すと、呼ばれたときにその約束（`pending.promise`）をそのまま返す。約束に拒否を受け取る側が付くのは、`BusinessNavigationProvider` の `reload()` が `load().then(成功, 失敗)` を付けたときだけである。
- `reload()` は `useReloadTriggers` の効果（`useEffect`）から呼ばれ、その効果は `LoginStateGate` がログイン状態を非同期に受け取って子を描いた後に動く。一方、テストが待つメニューの枠（`navigation`）は業務のメニューの状態が `idle`（まだ読み込みを始めていない）でも出る。そのため「枠が出た」ことは「読み込みが呼ばれた」ことを保証しない。
- テストは枠が出た後に同期で `pending.reject(...)` を呼んでいた。読み込みがまだ呼ばれていなければ、拒否した約束に受け取る側が無く、Node はマイクロタスクを流し終えた時点で Unhandled Rejection として扱い、Vitest がテストの外の誤りとして数える。後から読み込みが呼ばれて受け取る側が付くとテストそのものは通るため、「1,581 件すべて通過・誤り 1 件」の CI の記録と合う。
- 手元で、拒否を枠を待つより前（`renderShell` の直後）に移した写しを流すと、CI と同じ形の報告（`Unhandled Rejection`・`Error: down`・同じテストの名前・Tests は全件 passed・Errors 1）が再現した（確かめた後に元に戻した）。
- 元のテストで、拒否の直前の読み込みの呼ばれた回数を一時的にファイルへ書き出して流すと、手元では単独 3 回・CPU の負荷（`yes` を 8 本）をかけて 6 本並べた 2 回（計 12 回）のすべてで 1（呼ばれた後）だった。元の形のままでは、手元では再現していない（再現の試みは上の範囲で止めた）。

### 見立て（確かめていない）

- 負荷の高い CI では、枠を描いた後の効果（`useReloadTriggers`）の実行が、`findByRole` が解けてテストが拒否を呼ぶより後になった、と見ている。React が通常の優先度の更新の効果を別のタスクで流すことと、Testing Library が `findBy` の後に `setTimeout(0)` で待つことの前後が、機械の速さで入れ替わるため、と読んでいるが、CI の上での順序は確かめていない。
- 本体のコードの不具合ではない（読み込みが遅れても、拒否は後から付く受け取る側で処理され、画面は読み込みの失敗を正しく出す）。

## 2.3 直し

`frontend/src/app/layout/ShellLayout.test.tsx` の当該のテストで、拒否する前に、偽の読み込みが呼ばれた（`nav.count > 0`）ことを `waitFor` で待つ1行を足し、理由をコメントに書いた。テストの時間の上限は変えていない。読み込みが呼ばれた後に拒否するので、拒否の時点で必ず受け取る側が付いている。テストの意図（読み込み中と失敗の間は保存した開閉の id を残す）は変わらない（呼ばれた後も状態は「読み込み中」のまま）。

### 同じ形を起こしうるほかの箇所の検索

テストのファイルで、手で決める約束を `.reject(` で拒否している箇所（`Promise.reject` と `mockRejected` を除く）を検索した。

| 箇所 | 判断 |
|---|---|
| `ShellLayout.test.tsx:702`（今回） | 直した |
| `BusinessNavigationProvider.test.tsx:140` | 拒否の前に状態が `reloading` になるのを待つ。`reloading` は `reload()` の中で読み込みの呼び出しと同じ処理で付くため、受け取る側は付いている。直さない |
| `WorkRoleSwitcher.test.tsx:70` | 拒否の前に「読み込んでいます」を待つ。この表示は `loading` の状態だけで出て（`idle` では出ない）、`loading` は `getWorkRole()` の呼び出しと同じ処理で付くため、受け取る側は付いている。直さない |
| `UserAdminPage.test.tsx:950・953` | 拒否の前に呼ばれた回数（3回）を `waitFor` で待つ。直さない |
| `SharedTreeView.test.tsx:228・316・345` | 拒否の前に `loadChildren` の呼ばれた回数を `waitFor` で待つ。直さない |
| `InvitationAdminPage.test.tsx:796・817` | 送る操作のクリックを `await` で終えてから拒否する。要求はクリックの処理の中で出る。直さない |

同じファイルの `ShellLayout.test.tsx` の 586 行・672 行のテストも、枠を待った後に同じ形の約束を解決する（`resolve`）が、解決は受け取る側が無くても誤りにならないため、Unhandled Rejection は起きない。直していない（ただし、遅い環境では読み込みが呼ばれる前に解決することがありうるため、672 行の「木が後から届く」の意図は弱まりうる。直すかは依頼者の判断に回す）。

## 2.4 確かめの結果（実測）

| 確かめ | 結果 |
|---|---|
| 書式（`prettier --check` で直したファイル） | 成功 |
| lint（`npm run lint`） | 成功（終了コード 0） |
| 型（`npm run typecheck`） | 成功 |
| ライセンスヘッダー（`npm run license:check`） | 成功 |
| 直したファイルの単独（`npm test -- src/app/layout/ShellLayout.test.tsx`）を5回 | 5回とも 37 件すべて成功、Errors 無し |
| フロントの単体テスト全体（`npm test`）1回 | 186 ファイル・1,584 件すべて成功、Errors 無し |
| `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡し、`caffeinate -dims` で包む。2026-10-14 22:34:43〜22:36:06） | **BUILD FAILED**（1分22秒）。`:frontendTest` で、今回の直しと関わらない `src/app/routing/AppRouter.test.tsx` の「clears the intent when a path other than /logout is rendered」が 1 件失敗した（`expected "vi.fn()" to be called 1 times, but got 0 times`、281 行。1,584 件のうち 1 failed・1,583 passed）。`ShellLayout.test.tsx` は成功し、Unhandled の誤りは出なかった。バックエンドのテスト・カバレッジ・静的解析・秘密情報・脆弱性の検査の段まで進んでいないため、件数とカバレッジは測れていない |
| `AppRouter.test.tsx` の単独を5回（失敗の後） | 5回とも 13 件すべて成功（手元の単独では再現しない） |
| スリープの確かめ（`pmset -g log` の 22:30〜22:40） | 記録は Assertions だけで、Sleep・DarkWake は無かった |
| 2.5 の直しの後の `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（同じ環境変数と `caffeinate -dims`。2026-10-14 22:40:09〜22:55:08） | **BUILD SUCCESSFUL**（14分59秒）。フロントは 186 ファイル・1,584 件すべて成功し、Unhandled の誤りは無かった。バックエンドの単体テスト 2,102 件・結合テスト 1,165 件で失敗・誤り・飛ばしは 0 件（`backend/build/test-results/` の XML で数えた）。対象DB のテストの飛ばしの警告は無かった。フロントのカバレッジは行 95.42%・分岐 90.34%（下限 80%・70% を満たす） |
| スリープの確かめ（`pmset -g log` の 22:39〜22:56） | 記録は Assertions だけで、Sleep・DarkWake は無かった |
| WAR（`backend/build/libs/mastersmith.war`） | テストだけの変更のため作り直されず、1節の SHA-256 `f5293bed7fe2062271a34b84b48586d28431f294d75b1045ed459f0b8c58a376`（15:47）のまま |

最初に clean のタスクを verify の後に並べた形（`verify :backend:cleanTest ...`）で流し始めたが、順序の誤りに気づいてすぐ止め（Gradle のデーモンも止めた）、上の順序で流し直した。止めた回の結果は使っていない。

### 計画の外の失敗（`AppRouter.test.tsx`）の見立て（確かめていない）

テストは `findByTestId('item-screen')`（画面が描かれたこと）を待った直後に、同期で `clearLogoutIntent` が1回呼ばれたことを確かめている。呼ぶのは描画の後の効果（ログアウトの印を消す、U7 の L15）と見られ、team.md の「描画の後の効果で反映される値は、操作の直後に同期で確かめず waitFor で待つ」に当たる形である。今回の ShellLayout と同じく、画面が出たことは効果が動いたことを保証しない。依頼者の決定（A: 同じブランチでテストだけ直す）で直した（2.5）。

配備したアプリ・mailpit・見本の対象DB は止めず、変えていない。E2E は画面の本体を変えないため流していない。

## 2.5 AppRouter.test.tsx の直し（計画の外の失敗、依頼者の決定 A）

### 原因（確かめた事実と見立て）

- 確かめた事実: `frontend/src/app/routing/AppRouter.tsx` は、`/logout` 以外の道が描かれたときに `useEffect` の中で `clearLogoutIntent()` を呼ぶ（U7 の L15）。`AppRouter` は `LoginStateGate` がログイン状態を非同期に受け取った後に描かれる。テストは `findByTestId('item-screen')`（画面が DOM に出たこと）を待った直後に、同期で `clearLogoutIntent` の呼ばれた回数を確かめていた。画面が出たことは描画の後の効果が動いたことを保証しないため、team.md の「描画の後の効果で反映される値は waitFor で待つ」に反する形である。
- 確かめた事実: 1回目の verify では 0 回で落ち、直す前の単独の5回はすべて成功した（手元の単独では再現しない）。
- 見立て（確かめていない）: verify の中の負荷で、効果の実行が `findByTestId` の解けた後の確かめより遅れた。2.2 の ShellLayout と同じ種類の前後の入れ替わりと見ている。
- 本体のコードの不具合ではない（効果は後から必ず動き、印は消える）。

### 直し

| テスト | 直し |
|---|---|
| clears the intent when a path other than /logout is rendered（281 行） | `await waitFor(() => expect(clearLogoutIntent).toHaveBeenCalledTimes(1))` に直し、理由をコメントに書いた |
| keeps the intent on /logout and clears it after moving to another path（295 行） | 同じ形（`/items/7` の画面が出た直後に同期で回数を確かめていた）のため、合わせて `waitFor` で待つ形に直した |

時間の上限は変えていない。

### 同じファイルで直さなかった箇所と理由

| 箇所 | 理由 |
|---|---|
| 63〜71 行（未ログインの表示） | 同期の `render` の結果（描いた DOM）の確かめで、効果の値ではない |
| 80〜113 行（`findByTestId` の後の `getByTestId` など） | 同じ描画で出る DOM の確かめで、効果で反映される値ではない |
| 208 行（管理の API に要求を送らない） | 呼ばれ**ない**ことの確かめで、直前に `act` でマイクロタスクを流している。`waitFor` は呼ばれないことの確かめを強めない |
| 238〜239 行（折りたたみとフォーカス） | `fireEvent.click` は `act` に包まれ、効果はその中で流れ終わる。直前のフォーカスも `waitFor` で待っている |
| 246〜250 行 | 直前に `act(() => login.set(...))` と `waitFor` で待った後の、同じ要素・状態の確かめ |

### 確かめ（実測）

| 確かめ | 結果 |
|---|---|
| 書式（`prettier --check` で直した2ファイル） | 成功 |
| lint・型・ライセンスヘッダー（`npm run lint`・`typecheck`・`license:check`） | すべて成功（終了コード 0） |
| 直したファイルの単独（`npm test -- src/app/routing/AppRouter.test.tsx`）を5回 | 5回とも 13 件すべて成功 |
| フロントの単体テスト全体（`npm test`）1回 | 186 ファイル・1,584 件すべて成功、Errors 無し |
| verify | 2.4 の2回目の行のとおり BUILD SUCCESSFUL |

## 2.6 CI の記録との差

承認済みの `construction/ci-pipeline/ci-config.md` の 6.1 は、`ae794e4` について「まだ push されていない（`origin/develop` は `005730a`）」と書いており、`ae794e4` の CI（run 38435315180）の1回目の失敗（2.1）と2回目の success が載っていない（CI Pipeline の段の記録の後に push されたためで、書き漏らしではなく記録の時点の後の出来事）。ci-config.md は書き換えず、差をここに記録する。

| 項目 | ci-config.md | 実際 |
|---|---|---|
| `ae794e4` の CI | 未 push として記載なし | run 38435315180。1回目 failure（`ShellLayout.test.tsx` の Unhandled Rejection、2.1）、2回目 success |
| 1回目の失敗の扱い | — | team.md の「手元で再現しないものは、見立てと試みの範囲を記録したうえで CI の再実行で通れば進めてよい」に沿って再実行で通し、続けてこの節で原因を見立てて直した |

## 2.7 Sources

- CI の記録: `gh run view 38435315180 --attempt 1 --log-failed`、`gh run view 38435315180 --json`（試行ごとの jobs）。
- コード: `frontend/src/app/layout/ShellLayout.test.tsx`、`frontend/src/app/testing/frameTestSupport.tsx`・`renderWithProviders.tsx`、`frontend/src/app/navigation/BusinessNavigationProvider.tsx`・`useReloadTriggers.ts`、`frontend/src/app/login-state/LoginStateGate.tsx`、`frontend/src/app/work-role/WorkRoleProvider.tsx`・`WorkRoleSwitcher.tsx`、`frontend/src/app/routing/AppRouter.test.tsx`。
- 実行の記録: vitest・verify の出力、`pmset -g log`。
- 既存の記録: `construction/build-and-test/fix-notes.md`、`construction/ci-pipeline/ci-config.md` の 6.1・6.2。

## 2.8 Assumptions & Open Questions

- [assumption] 2.2 の見立て（CI での効果の遅れ）は手元で元の形のままでは再現していない。直しは「拒否の時点で受け取る側が付いている」ことを順序で保証する形で、見立てが違っても同じ誤りは起きない。
- [assumption] 2.5 の `AppRouter.test.tsx` の見立て（verify の中での効果の遅れ）も確かめていない。直しは効果が動くのを待つ形で、見立てが違っても同期の確かめによる失敗は起きない。
- 統合の後の CI の結果は未確認（統合・push はこの記録の後）。
