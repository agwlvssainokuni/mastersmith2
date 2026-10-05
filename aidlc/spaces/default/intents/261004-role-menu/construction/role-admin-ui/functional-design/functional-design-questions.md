# 機能設計の質問 — U6 role-admin-ui

対象の単位: U6 role-admin-ui（kind: ui、大きさ L）。`features/roleadmin`（S3 ロールの一覧・S4 権限の設定・S5 割り当て）、`features/groupadmin`（S6 グループの一覧・詳細）、`features/roletransfer`（S7 権限の受け渡し）、`features/useradmin` へのロールの読み取りの表示（S9）、管理のメニューへの登録。作るのは B8（B7 navigation の後、B9 app-frame-ui の前）。ui の単位のため、成果物は `functional-spec.md`（画面の流れと状態の遷移の正）・`frontend-components.md`・`traceability.json` で、`entities.md`・`rules.md` は作らない（段の定義の `produces_kinds`）。

質問は 6 問です。上流・承認の場・先に確定した単位（cross-cutting・group・role）・`team.md`・`project.md`・コードで決まっている点は、下の「決まっていること」に書き、質問にしていません。

---

## 決まっていること

### 画面と置き場（単位・部品・画面の段）

- 機能は `features/roleadmin`・`features/groupadmin`・`features/roletransfer` を新しく作り、`features/useradmin` を広げる。機能どうしは直接 import しない（U1 の ESLint の制限、`team.md` の Code Style）。共有の木と登録の型は U1 のものを使い、U7 には依存しない（`aidlc/spaces/default/intents/261004-role-menu/inception/units-generation/unit-of-work.md` の U6）。
- 管理のメニューへの登録は `section: 'ADMIN'`・`visibleWhen: 'ADMIN'` で、アイコンは任意（`aidlc/spaces/default/intents/261004-role-menu/construction/cross-cutting/functional-design/frontend-components.md` の 3節、BR5）。区画の見出し・並び・既定のアイコンの当て方は U7 が持つ。
- 画面の形は画面の段の答え（RQ1〜RQ8: すべて A）と `mockups.md` の S3〜S7・S9・10a 節、`interaction-spec.md`、`accessibility-checklist.md`、`design-system-mapping.md` のとおり。ロールとグループは別の画面、ロールの詳細は「権限の設定」「割り当て」のタブ、権限の設定は左に木（スキーマ→テーブル）・右に表、保存は1つの表（スキーマかテーブル）の分をまとめて、確かめの表示は背景のクリックで閉じずはじめのフォーカスは「やめる」、表の右端の Dropdown は `bottom-end`、確かめる幅は 360px・768px・1280px。
- 〔Should〕は AC1.2.7（今の DSL に無い設定の表示と消す操作）・AC1.2.16（業務のメニューに出るか）・AC2.2.9（外すと作業ロールが変わることの確かめ）。

### API（契約 C6・C7 と、group・role の機能設計で足した差）

- グループ（`aidlc/spaces/default/intents/261004-role-menu/construction/group/functional-design/functional-spec.md` の 2節・8節）: 一覧は `page` だけを受けて 20 件、グループの ID の順（`size` は受けない）。詳細はメンバーをページ送りせずに返す。名前の規則は前後の空白（全角を含む）を除いて 1〜64 コードポイント・制御文字は拒否・重なりは大文字と小文字を区別しない。排他の待ちの上限切れは `GROUP_BUSY`（409）。存在しない利用者のメンバーの外しは `GROUP_NO_CHANGE`。
- ロール（`aidlc/spaces/default/intents/261004-role-menu/construction/role/functional-design/functional-spec.md` の 2節・8節・10節）: 一覧は `page` だけで 20 件・ロールの ID の順、`userCount`・`groupCount`・`ROLE_IN_USE` の数は直接の割り当ての数。名前の規則はグループと同じ。権限の保存（PUT）は `entries` に載せた対象だけを置き換える差分の保存で、null は「設定なし」。今の DSL に無い対象は値を設定できず、消す（`POST .../permissions/clear`）だけ。排他の待ちの上限切れは `ROLE_BUSY`（409）。木の API は開いた階層の分だけを返し、各節に `explicit`・`effective`・`inheritedFrom`（`EXPLICIT`・`TABLE`・`SCHEMA`・`DEFAULT`）・`inMenu`・`inCurrentDsl`・`hasChildren` を持つ。
- 受け渡し: 確かめ・適用は本文を `application/yaml` で送り、適用の指紋はヘッダー `X-Role-Transfer-Fingerprint`（契約 C7 の multipart との差、role の Q8 A）。`TransferCheck` の各ロールに `roleId`、各変更に `inCurrentDsl` が足されている。拒否 `ROLE_TRANSFER_INVALID` の `errors` は `reason`（`TOO_DEEP`・`TOO_MANY_ALIASES`・`TAG_NOT_ALLOWED`・`DUPLICATE_KEY`・`SYNTAX`・`UNSUPPORTED_VERSION`・`MISSING_KEY`・`UNKNOWN_KEY`・`INVALID_VALUE`・`INVALID_ROLE_NAME`・`DUPLICATE_ROLE`・`INVALID_TARGET_NAME`・`COLUMN_AUX_NOT_ALLOWED`）・`line`・`column`・`path` で最大 100 件、サーバーの文言（message）は持たない。大きさの上限は仮に 10 MiB（NFR 要件の段で確定）で、超えると 413 `PAYLOAD_TOO_LARGE`、本文の型の誤りは 415。
- 読み取りの API: `GET /api/admin/roles/{roleId}/assignments`（利用者は出どころ DIRECT・GROUP つき、グループ）、`GET /api/admin/groups/{groupId}/roles`、`GET /api/admin/users/{userId}/roles`（ロールと出どころ）。
- 候補の検索は既存の利用者の一覧 `GET /api/admin/users?page=&q=`（20 件、招待中の人は行が無く出ない）。グループの一覧には検索の引数が無い。
- 誤りは Problem Details の `code` で分け、`detail`・`title` は画面に出さない（既存の `failureMessage.ts` と同じ形）。管理の API の 403 `ACCESS_DENIED` は骨組みの `useAdminForbidden`（`frontend/src/app/admin-forbidden/`）に渡し、骨組みの権限なしの表示にする。`ROLE_BUSY`・`GROUP_BUSY` は「ほかの操作と重なった。もう一度」の案内にする（role の 10節）。

### テストの決まり（`team.md`・`project.md`）

- 画面のテストは対象と同じ場所の `*.test.tsx` で、Vitest＋Testing Library＋user-event＋vitest-axe。画面部品ごとに axe を1件。描画の後に反映される値は `waitFor` で待つ。テストの説明文は英語、テストのメールアドレスは `example.com` だけ。
- 役割・権限の画面の出し分け: メニュー・画面を隠すことをサーバー側の検査の代わりにしない。権限の無い画面へ直接移ると 403 の表示（骨組みの `ADMIN_FORBIDDEN` と `useAdminForbidden`）。
- 実際のブラウザの axe は、誤りを出した状態・現実に近いデータ（2語の氏名・長い名前）で、ブランドカラーとテーマのすべての組（`frontend/e2e/support/displayCombos.ts` の 20 組）について行い、開いた Dropdown・Modal が画面の中に収まることも確かめる。この検査は E2E の本数に数えない。E2E で API の答えを差し替えるときは、見本を1つにして画面の側の型を付け、本物の応答と項目の名前・型が一致することを流れの E2E で毎回確かめる（`project.md` の学び）。
- E2E の流れは Intent 261004-role-menu で最大2本（F と I）。役割・権限を変える操作は、その流れで自分で作った利用者とロールだけを対象にし、初期管理者は変えない。画面・認証に関わる変更は統合の前に手元で E2E を流す。

### コードと make-you-chic-ui で確かめた事実

- 画面のルーターは `BrowserRouter`（`frontend/src/main.tsx`、宣言の形）。react-router 8.4.0 の `useBlocker` は data router（`createBrowserRouter`）の中でしか使えない（`node_modules/react-router` の `useBlocker` は `useDataRouterContext` を要する）。今は画面の外への移動を止める仕組みが無い。
- 画面の登録の道は `matchPath` で照らすため、`/admin/roles/:roleId` のような引数つきの道を登録できる（`frontend/src/app/routing/decideRoute.ts`）。今ある登録に引数つきの道は無い。
- 既存の利用者の管理の画面には「利用者の詳細」の画面・行の開閉は無い（`frontend/src/features/useradmin/UserAdminPage.tsx`・`UserTable.tsx`。行の操作は Dropdown の `UserRowActions.tsx`）。
- make-you-chic-ui の固定先 e82b651 の `Table` は `labels`（ページ送りの文言の上書き、a405dd1）と行の開閉 `renderDetail`・`expandedRowIds`・`onExpandedChange`（2f8e05d）を持つ。開閉のボタンの名前はすべての行で同じ `labels.toggleRowDetail`、ページ送りは常に出る（`vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Table/Table.tsx`）。`design-system-mapping.md` の「Table に行の開閉が無い」は今の固定先とは違う。
- `Tabs` は `activeIndex`・`onChange` で外から切り替えられる。`Modal` は `closeOnBackdropClick`・`initialFocusRef`・`finalFocusRef`・`role`（`alertdialog`）を持つ。`Dropdown` の項目は `disabled` と `description` を持つ。
- make-you-chic-ui の対応（5bf1ffe）は入れ子のサイドバーだけで、木の部品・Dropdown の `menuitemradio`・Table の行の入れ子は対象外。共有の木は U1 の `shared/tree`、作業ロールの選択中の印は U7 の持ち物で、U6 が新しく上流に頼む部品は無い。
- DSL の画面の誤りの一覧（`frontend/src/features/dsl/DslErrorList.tsx`）・ファイルの保存（`features/dsl/api/saveFile.ts`）・ファイルの選択（`DslSubmitForm.tsx`、`File.size` で先に判定）は `features/dsl` の中にあり、ほかの機能から import できない。DSL の誤りはサーバーの文言（message）と総数を持つが、権限の YAML の誤りは `reason` だけで総数を持たない。
- 一覧のページ送りの計算は `frontend/src/shared/paging/paging.ts`（20 件）、名前の長さの数え方は `frontend/src/shared/validation/codePoints.ts`。

---

## この段で決める設計の要点（質問にしない案。まとめの確認で確かめる）

- **道と登録**: `/admin/roles`（S3）・`/admin/roles/:roleId`（S4、権限の設定のタブ）・`/admin/roles/:roleId/assignments`（S5、割り当てのタブ）・`/admin/groups`・`/admin/groups/:groupId`（S6）・`/admin/role-transfer`（S7）。タブは道で決め、`Tabs` を外から切り替える（AC1.1.8 の「割り当ての画面へ移る手がかり」、S6・S9 からのリンクで直接開ける）。登録は `featureId` が `roleadmin`・`groupadmin`・`roletransfer`、サイドバーの項目は「ロール」240・「グループ」250・「権限の受け渡し」260（今の 230「利用者の管理」の後）、アイコンは付けない（U7 の既定に任せ、今の4件とそろえる）。ほかの機能の画面へのリンク（DSL の管理・ロールの一覧）は道の文字列を書き、機能を import しない。
- **一覧（S3・S6）**: make-you-chic-ui の `Table` に `labels` を画面の言語で渡し、`shared/paging` で 20 件のページ送り（読み直しの間の押下は捨てる、利用者の管理と同じ）。行の操作は「開く」と Dropdown（名前の変更・削除、`bottom-end`）。削除は数によらず押せる形にし、拒否はサーバーの `ROLE_IN_USE`・`GROUP_IN_USE` の残りの数で文言を作り、ロールなら割り当てのタブへのリンクを添える（AC1.1.8・AC2.1.7）。
- **名前の入力（作成・名前の変更の Modal）**: 画面でも前後の空白（全角を含む）を除いて 1〜64 コードポイント・制御文字を確かめて入力欄の下に出す（`aria-describedby`）。残りの数を示す。判定の正はサーバー（`team.md`）。重なり・変えるものが無いはサーバーの code で入力欄の下に出す（AC1.1.2・AC1.1.9・AC1.1.14）。
- **権限の設定（S4）**: 左の木は `shared/tree` で、スキーマの節は子あり、テーブルの節は子なし（カラムは右の表）。節の id はスキーマ名とテーブル名を JSON の配列にした文字列で作り、名前に何が入っても重ならない。右の表の値は、選んだ節の親の段の応答（スキーマなら1段目、テーブルならテーブルの段）と、テーブルを選んだときのカラムの段から作る。実効の値は「READ（明示）」「READ（スキーマ 販売DB から継承）」「NONE（既定）」の文字で出し、Select の名前に対象を含める（AC1.2.2〜AC1.2.4・AC1.2.14）。〔Should〕テーブルの設定に「業務のメニュー: 出る・出ない」を `inMenu` から文字で出す（AC1.2.16）。
- **未保存の変更と保存**: 変えた値は「対象 → 明示の値」の写しで持ち、元の値に戻した対象は写しから消す。「保存」は写しが空なら押せず理由を文字で示す（AC1.2.17）。保存は写しの対象だけを `entries` に載せる（差分の保存）。写しを作る関数は純粋な関数にし、fast-check で性質（変えていなければ `entries` が空、戻したら消える、`scope` の外を含まない）を確かめる。保存が成功したら、選んでいる段と親の段（木の印・実効の値・`inMenu` が変わりうる）を読み直し、結果を Alert と読み上げで伝え、フォーカスは保存のボタンに残す。
- **保存の拒否の後**: `ROLE_BUSY` は写しを残して「もう一度保存」の案内。`ROLE_NO_CHANGE` は案内を出して読み直す（写しを捨てる）。`PERMISSION_TARGET_NOT_IN_DSL` は読み直して「⚠ 今の DSL に無い」の印を付け、今の DSL に残る対象の写しは残す（AC1.2.13）。`DSL_NOT_APPLIED` は対象が無い案内と DSL の管理へのリンクに切り替える（AC1.2.6）。`ROLE_NOT_FOUND` は「このロールはありません」とロールの一覧へのリンク。〔Should〕今の DSL に無い設定の「設定を消す」は確かめなしで送り、結果を Alert で出して読み直す（消すのは使われていない設定のため）。
- **割り当て（S5）**: 利用者の表は氏名・メールアドレス・出どころ（「直接」「グループ 〇〇」を文字で並べる）・操作。「外す」は出どころに直接があるときだけ出し、直接の割り当てだけを外す。外した後に読み直し、グループ経由で残るなら行と出どころが変わる（AC2.2.7・AC2.2.8）。グループの表は名前と「外す」。グループの候補は `GET /api/admin/groups` を 20 件ずつのページ送りで出す（検索の引数が無いため）。割り当て済みは候補に「割り当て済み」と出して選べない。
- **グループ（S6）**: 詳細はメンバー（氏名・メールアドレス・利用停止の印・外す）と、割り当てたロールの読み取り（ロールの詳細の割り当てのタブへのリンク）。メンバーの外しは確かめなしで送り、外した後にその利用者のロール（`GET /api/admin/users/{userId}/roles`）を読み、このグループの経由で無くなったロールを Alert に文字で出す（mockups.md の S6、AC2.1.9）。
- **受け渡し（S7）**: 書き出しは ApiClient のダウンロードで受け、`Content-Disposition` の名前で保存する（保存の小さな関数は `roletransfer` の中に持ち、`dsl` は変えない）。ファイルの選択は DSL と同じくネイティブの `input type="file"` を見える label とボタンで包み、選んだ時点で `File.size` を上限と比べて超えれば送らずに文字で示す（上限ちょうどは送る。判定の正はサーバー）。本文はテキストとして読んで `application/yaml` で送り、適用は確かめの指紋をヘッダーに入れる。確かめの結果の表は DSL の違いの表と同じ素の table で、行ごとの開閉のボタンの名前にロールの名前を入れる（「営業の変わる点を開く」）。開いた行の変わる対象は 100 件ずつのページ送りで出し（数千件でも描く行を抑える。AC3.1.9）、理由は「READ → 設定なし（ファイルに無い）」のように文字で出す。変わる点が無ければ「変わる点はありません」で適用を押せない（AC3.1.14）。`ROLE_TRANSFER_STALE` はファイルの選択を残して確かめのやり直しの案内（AC3.1.11）。誤りの一覧は DSL の誤りの一覧と同じ見た目の部品を `roletransfer` の中に作り、`reason` を日本語と英語の文言に写す（場所は行・列・道、無ければ「—」）。
- **S9 のロールの表示**: Q5 の答えの形で、利用者のロールと出どころを読み取りで出し、ロールの一覧へのリンクを置く（新しい操作は足さない）。
- **文言**: 画面の文言は日本語と英語（既定は日本語）で、各機能の `messages`。ロール・グループ・スキーマ・テーブル・カラムの名前は訳さず文字として出す（`<`・`&` を含んでも文字のまま）。
- **ブラウザの検査（本数に数えない）**: `frontend/e2e/` に U6 の画面のアクセシビリティの検査を1つ置く（120 と同じ形）。S3〜S7・S9 の主な状態（一覧・開いた Dropdown・名前の誤りの Modal・保存の誤りを出した S4・未保存の確かめ・候補の Modal・確かめの結果の行を開いた S7・誤りの一覧の S7）を、2語の氏名・64 文字の名前の見本で、表示の設定の 20 組すべてについて axe（違反 0 件）と横のはみ出し・開いた部品が画面に収まることを確かめる。幅の 360px・768px・1280px は既定の1組で確かめる（768px 未満で S4 が縦に積まれ、表の1列目が見える）。API は `support/adminApiRoute.ts` で差し替え、見本に画面の型を付ける。
- **上流との差（成果物に記録する）**: `design-system-mapping.md` の「Table に行の開閉が無い」は今の固定先と違うが、S7 は開閉のボタンの名前を行ごとに付けられずページ送りが常に出るため、素の table のままにする。Q1・Q2・Q4・Q5 の答えによる差（API の追加、AC1.2.15・AC2.2.9 の範囲、S9 の置き場）。

---

## Q1 ロールの詳細を道から直接開いたときに、ロールの名前をどう読むか

背景: 契約 C7 と role の機能設計に、ロール1件を読む `GET /api/admin/roles/{roleId}` がありません（グループには詳細の GET があります）。ロールの詳細（S4・S5）を道 `/admin/roles/:roleId` で直接開く・読み込み直すと、見出しの「ロール: 営業」に出す名前と、ロールがあるかを読む口がありません。S6・S9・拒否の文言（AC1.1.8）からロールの詳細へのリンクも、この道を使う前提です。

A. U4 に `GET /api/admin/roles/{roleId}`（`Role` を返す、無ければ `ROLE_NOT_FOUND` 404、ADMIN、監査なし）を足す互換の変更を頼み、B4 で作る。role の機能設計は承認の場でこの追加を Request Changes で直し、それまでは U6 の設計に「直した後の形」として書いて上流との差に記録する（`project.md` の学び）（推奨: 直接開く・読み込み直す・リンクで移るのどれでも同じに動き、ロールが削除されたことも 404 で分かる。足すだけの小さな変更）
B. API は変えず、一覧から移るときだけ名前を画面の遷移の状態で渡す。直接開いた・読み込み直したときは名前を出さず「ロール #12」とし、ロールの有無は割り当ての GET の 404 で知る
C. 詳細を道に持たず、ロールの一覧の画面の中で選んだロールの詳細を出す（読み込み直すと一覧に戻り、S6・S9・拒否の文言のリンクはロールの一覧へ移るだけ）
X. Other (please specify)

[Answer]: A **Mode:** guided

## Q2 保存していない変更の確かめ（AC1.2.15）を、どこまでの移動に当てるか

背景: AC1.2.15 は「別のロールや別の画面へ移ろうとする」ときに確かめを求めます。画面のルーターは `BrowserRouter` で、react-router の `useBlocker`（画面の外への移動を止める仕組み）は data router（`createBrowserRouter`）の中でしか使えません。今はサイドバー・ユーザーメニュー・ブラウザの戻るによる移動を止める仕組みがありません。

A. 画面の中の移動（木の別の対象・タブ・「← ロールの一覧」）は Modal で確かめ、読み込み直し・タブを閉じるはブラウザの `beforeunload` の確かめにする。サイドバー・ユーザーメニュー・ブラウザの戻るの移動は止めない。AC1.2.15 の「別の画面へ」の一部を満たさないことを差として記録する（骨組みに手を入れない）
B. 画面のルーターを data router（`createBrowserRouter` に、今の `App` を1つの道で包む形）に替え、`useBlocker` でサイドバー・ブラウザの戻るを含む移動も確かめる。`frontend/src/main.tsx` と、`useBlocker` を使う部品のテストの包み方が変わる。替えて今の画面と E2E が変わらないことを、コード生成の最初に小さく確かめ、使えなければ A に切り替える（推奨: AC1.2.15 をそのまま満たせ、変える所は入口の包み方だけで済む見込み。B9 の骨組みの作業より前に入る）
C. 骨組みに「移る前に確かめる口」を足し、サイドバーとユーザーメニューのリンクが押されたときに画面へ問う（骨組みの部品に手が入り、ブラウザの戻るは止められない）
X. Other (please specify)

[Answer]: B **Mode:** guided

## Q3 割り当て（S5）とメンバー（S6）の候補の選び方

背景: 契約 C6・C7 の追加の API は1件ずつ（`userId` か `groupId` を1つ）です。`interaction-spec.md` は候補の一覧を「Checkbox か Button の並び」と書いており、1人ずつか複数をまとめてかは決まっていません。利用者は 50 人程度まで（NFR2.1）、候補は氏名かメールアドレスの一部で絞り、最大 20 件を出します（AC2.1.8）。

A. 1人ずつ: 候補の行の「足す」ボタンで1件を送り、Modal は開いたまま、その候補を「割り当て済み」（S6 は「メンバー済み」）に変えて続けて足せる。結果は Modal の中の読み上げで伝え、閉じたら一覧を読み直す（推奨: API と同じ単位で、失敗が1件ずつ分かる。続けて足す手間も小さい）
B. Checkbox で複数を選び、「足す」で1件ずつ順に送る。一部が失敗したら件ごとに結果を出す（まとめて選べるが、途中の失敗と同時の変更の見せ方が増える）
C. 1人ずつで、足したら Modal を閉じる（もう1人足すには開き直す）
X. Other (please specify)

[Answer]: A **Mode:** guided

## Q4 外すと作業ロールが変わることの確かめ（AC2.2.9、Should）をどう出すか

背景: AC2.2.9 は、作業ロールにしているロールを外すときに、管理者に確かめを出すことを求めます。管理の API には、ほかの利用者の今の作業ロールを返すものがありません（契約 C7 の `RoleAssignments`、FR6.2 は他人の権限を確かめる口を持たない）。利用者のロールと出どころは `GET /api/admin/users/{userId}/roles` で読め、外すとそのロールが無くなるか（出どころが直接だけか）と、残りの「最初のロール」（ロールの ID の小さい順、role の Q1 A）は画面で求められます。

A. 外すとその利用者からロールが無くなる（出どころが直接だけ）ときに確かめを出し、「このロールを作業ロールにしていれば、〔残りの最初のロール〕に変わります（残りが無ければ作業ロールが無くなります）」と条件つきで示す。グループ経由で残るときは確かめずに外す。API は変えない（推奨: 今の API だけで、作業ロールが変わりうる場合をもれなく知らせられる。他人の作業ロールを管理者に見せる口を足さずに済む）
B. U4 の割り当ての一覧に各利用者の有効な作業ロールを足し、そのロールが作業ロールのときだけ確かめを出す（U4 の設計の変更が要り、他人の作業ロールが管理者に見えるようになる）
C. Should のため今回は作らず、外した結果の Alert だけにする（AC2.2.9 を満たさないことを差として記録する）
X. Other (please specify)

[Answer]: A **Mode:** guided

## Q5 利用者の管理の画面（S9）のどこに、その人のロールを出すか

背景: `mockups.md` の S9 は「既存の利用者の詳細」にロールと出どころを読み取りで足すとしていますが、今の利用者の管理の画面には詳細の画面・行の開閉がありません（一覧の表と、行の「操作」の Dropdown だけ）。読み取りの API は1人ずつ（`GET /api/admin/users/{userId}/roles`）で、まとめて読む API はありません。mockups.md は「既存の画面の他の部分は変えない」としています。

A. 行の「操作」のメニューに「ロールを見る」を足し、読み取りの Modal で「ロール: 営業（直接・グループ 営業部）・経理（グループ 経理部）」とロールの一覧へのリンクを出す。開いたときに1回だけ読む（推奨: 表の列と形を変えず、既存の表のテストへの影響が小さい。Modal の題に氏名が入り、読み上げで誰のロールか分かる）
B. make-you-chic-ui の `Table` の行の開閉（`renderDetail`）を使い、行を開くとロールを読んで出す（表の左に開閉の列が増え、開閉のボタンの名前はすべての行で同じ「詳細」になる）
C. 一覧に「ロール」の列を足し、ページの 20 人ぶんを1人1回ずつ読む（1ページで 20 回の要求になり、表の幅が増える）
X. Other (please specify)

[Answer]: A **Mode:** guided

## Q6 F の流れの E2E（NFR6.3）を B8 と B9 のどちらで書くか

背景: F の流れ（管理者がロールを作って権限を設定し割り当て、利用者の作業ロールの切り替えでメニューが変わる）は U6 の管理の画面と U7 の作業ロールの切り替え・サイドバーをまたぎます。`bolt-plan.md` の B8 の終わりの条件に「E2E（F の流れ。B9 とまたがるため、どちらで書くかはコード生成の計画で決める）」とあります。B8 の時点では作業ロールの API（B5）と業務のメニューの API（B7）はあり、画面の切り替えとサイドバーの入れ子は B9 でできます。本数は Intent で最大2本（F と I）です。

A. B8 で F の流れの1本を、管理の画面の部分（自分で作った利用者に、自分で作ったロールのテーブルの権限を設定して割り当てる）と、その利用者でログインして作業ロールと業務のメニューを API の応答で確かめる形で書く。B9 で同じファイルに、画面での作業ロールの切り替えとサイドバーの変化を足して仕上げる（本数は1本のまま）（推奨: B8 の統合の前に U6 の画面を実際のブラウザの流れで確かめられ、B9 は足すだけで済む）
B. F の流れは B9 でまとめて1本書く。B8 では流れの E2E を足さず、U6 のアクセシビリティの検査（本数に数えない）と既存の E2E の通過で統合する
C. 役割の割り振りはコード生成の計画で決める（この段では決めない）
X. Other (please specify)

[Answer]: A **Mode:** guided

## Consolidated Summary Confirmation

答えのまとめ（role-admin-ui）:

- Q1 A: role に `GET /api/admin/roles/{roleId}`（`Role` を返し、無ければ `ROLE_NOT_FOUND` 404、ADMIN、監査なし）を足す互換の変更を頼み、B4 で作る。role の機能設計は承認の場で Request Changes で直し、それまではこの単位の設計に「直した後の形」として書いて上流との差に記録する。
- Q2 B: 画面のルーターを data router（`createBrowserRouter`）に替え、`useBlocker` でサイドバー・ブラウザの戻るを含む移動も確かめる。コード生成の最初に小さく確かめ、使えなければ A（画面の中の移動と `beforeunload` だけ）に切り替える。`main.tsx` に手が入る点は U7 に引き継ぐ。
- Q3 A: 割り当てとメンバーの候補は1人ずつ足し、Modal は開いたまま続けて足せる。
- Q4 A: 外すとその利用者からロールが無くなるときに「作業ロールにしていれば〔残りの最初のロール〕に変わります」と条件つきで確かめる。API は変えない。
- Q5 A: 利用者の管理の画面は、行の「操作」に「ロールを見る」を足し、読み取りの Modal でロールと出どころを出す。
- Q6 A: F の流れの E2E は B8 で管理の画面の部分と API の応答での確かめを書き、B9 で同じファイルに画面の切り替えを足す（本数は1本のまま）。
- 「この段で決める設計の要点」（道と登録、一覧、名前の入力、権限の設定の木と表、差分の保存と fast-check、保存の拒否の後の扱い、割り当て、グループ、受け渡しの画面、文言、ブラウザの axe と幅の検査、上流との差）もこのまま設計に入れる。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
