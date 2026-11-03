# Functional Design の質問（U5 u5-menu-icons-home）

対象の単位: U5 `u5-menu-icons-home`（種類 ui。メニューの応答の印・アイコンの決め方・ホーム・管理の仮の画面の廃止・上の帯のロゴ）。出典は `unit-of-work.md`・`unit-of-work-story-map.md`・`requirements.md`（FR7〜FR11・NFR1.3・NFR2.2・NFR3.2・NFR4.2・NFR6.1）・`stories.md`（US3.1〜US3.3・US4.2〜US4.4・US5.2・US5.3 の一部）・`contract-summary.md` の C5・C7・`decisions.md` の ADR-005〜ADR-007・`mockups.md` の S5・S6・S8・既存のコード（読み取りだけ）。

## 決まっていること（質問にしない）

上流（要件・ストーリー・ADR・契約・画面イメージ）で決まっている点。

| 項目 | 決まっている内容 | 出どころ |
|---|---|---|
| メニューの応答の印 | `GET /api/me/navigation` の項目に `tableKind`（`TABLE`・`VIEW`・`null`）を足すだけ。既存の項目は変えない。見られる項目にだけ付く。出どころは適用中の DSL の `DslTable.view`。対象DB の問い合わせは足さない | C5・ADR-005・AC4.4.3・AC4.4.4・NFR1.3・NFR4.2 |
| 許すアイコンの名前の一覧 | サーバー（`allowed-icons.txt`）と画面（`allowedIcons.ts`）の2か所を、make-you-chic-ui の固定先 `d112795` のアイコン（24 個）とそろえる。上流の依頼 8 は取り込み済み（固定先の更新 `2aafe750`）。一覧は U1 の統合の時点で 24 個にそろっている | C7・AC4.4.5・FR11.4 |
| 管理の区画のアイコン | 利用者＝`user`、招待＝`mail`、グループ＝`users`、ロール＝`shield`、ロールの受け渡し＝`download`、DSL＝`edit`。上流の取り込みが済んだため、6 項目すべてに付ける（画面イメージの Q7 A は取り込み前の扱い） | AC4.3.1・AC4.3.3・mockups.md の S6 |
| 業務の既定のアイコン | DSL の `icon` があればそれ（許す一覧で照らす）。無いときは種類で決める: テーブル＝`table`、ビュー＝`view`、子を持つ項目（フォルダ）＝`folder`。取り込み済みのため、3 種類を見分ける | AC4.4.1・AC4.4.2・FR11.2 |
| ホーム | 見出し（h1）、作業ロールの 1 行、「業務」の見出し（h2）とカード、管理の権限を持つ人にだけ「管理」の見出し（h2）とカード。カードはアイコンと名前だけ。新しい API を呼ばず、対象DB へ問い合わせない。今の案内（`home.notConfigured`・`home.noRole`・`home.nothingVisible`・`home.loadError`）は残す。カードの移り先はテーブル・ビューならその画面、フォルダならメニューの並びで最初に画面を持つ子孫 | S5・AC3.1.1〜AC3.1.8・AC3.2.1〜AC3.2.3 |
| 管理の仮の画面の廃止 | サイドバーの「管理」の項目と `frontend/src/features/admin` を外す。廃止した道 `/admin` は、ほかの存在しない道と同じ「見つからない」画面にする。サーバー側の `GET /api/admin/check` と 403 の判定は残す。検査（E2E 030・090・130 と単体テスト）は別の画面か別のテストへ移し、移す前と後の件数は Build and Test で記録する | FR8.1〜FR8.4・ADR-007・AC3.3.1〜AC3.3.4 |
| 上のバーのロゴ | 上流の `AppShell` の `logo` の口が取り込み済み（`d112795`）。U6 の `AppIcon`（`shared/app-icon/`）を渡す。飾りとして読み上げから外す。口を渡さない画面（ログイン）は今のまま | AC4.2.1・US4.2 |
| 信頼できない入力 | 表示名・`icon` は信頼できない入力として扱う。表示名は文字として出す（HTML として描かない）。一覧に無い `icon` から外部の URL や `javascript:` の道は作らない。`label` の `<`・`>`・`&`・`"`・`'` は文字として出る | AC4.4.6 |
| 必須のテスト | メニューの応答の `tableKind`（TABLE・VIEW・null・見られない項目に付かない）のサーバー側のテスト。アイコンを決める関数・ホームのカードを作る関数に `fast-check` の性質ベースのテスト。ホーム・サイドバーの axe（ブランドカラーとテーマのすべての組・長い名前・展開した状態）。仮の画面で確かめていた検査の移し | team.md の N 階層のメニューの必須のテスト・AC5.2.1〜AC5.2.4 |

## コードを読んで設計で決めること（質問にしない。承認の場で覆せる）

読み取りだけで確かめた事実から、機能設計の決まり（D 番号つき）として書く点。

| 項目 | 決めること | 確かめた事実 |
|---|---|---|
| `tableKind` を足す場所 | `MenuFilter`（純粋な関数）の中で決める。`MenuFilterInput` に、テーブルの組から種類を返す操作を足す（本番は `DslModel.findTable` の `DslTable.view`）。`NavNode` に `tableKind` を足し、移れる項目だけが持てる。写しの権限と適用中の DSL の食い違い（DSL を差し替えた直後）で DSL にテーブルが見つからないときは `null` にし、画面は既定のアイコンに落とす | `MenuFilter` は DSL の木を受けて純粋に絞り、`NavigationService` は DSL を 1 回読むだけ。`NavigationService` で足すとテーブルを引く経路が二重になる |
| 画面の `NavNode` の読み方 | `tableKind` は `TABLE`・`VIEW`・`null`・項目が無い、のどれも通し、知らない値は `null` とみなす（応答を拒否しない）。足すだけの変更で、前の版の画面が読んでも壊れない | `parseNode` は知らない項目を読み捨てる作り。知らない `emptyReason` だけが通信の失敗になる |
| アイコンを決める場所 | 画面の `resolveNavIcon` の 1 か所を、`(icon, tableKind, 子の有無)` を受ける関数に広げる。サーバーが返す `icon` は DSL の `icon` を許す一覧で照らした後の名前で、DSL に無い・一覧に無いときは `list`（サーバーの既定）が返る。画面は「サーバーが `list` を返した」と「DSL が `list` を指定した」を区別できないため、種類の既定を当てるのは `icon` が `list` のとき（既定）とする | `NavIconPolicy.iconOf` は一覧に無い・空のとき `list` を返し、応答は `icon` を必須にしている |
| 管理のカードの出どころ | サイドバーの管理の区画と同じ `selectAdminSidebarItems` の戻り（ラベル・アイコン・道）から作る。管理者でなければ空。ホームで権限を足さない | `buildNavSections` が管理の項目を作る入力と同じ |
| 業務のカード | メニューの最上位の項目から作る。サイドバーの `mapBusinessNode` と同じ規則（名前の決め方・アイコンの照らし方・移れない項目の落とし方）を共有の関数にする。フォルダの移り先は、子を行きがけの順にたどって最初の移れる項目の道。移れる項目が子を持つときは、自分の道 | `NavNode` の形の決まりで、応答の木のどの項目も、自分か子孫に移れる項目を必ず 1 つ持つ（移れない項目は子を 1 つ以上持ち、子の無い移れない項目は応答に出ない） |
| カードの部品 | `Card` は `<div>` の入れ物で、リンクにする口が無い。`react-router` の `Link` の中に `Card` を置き、押せる範囲は `Link` にする。アイコンは `Icon`（飾り）、名前は文字 | 上流の `Card` は `HTMLAttributes<HTMLDivElement>` だけ（ADR-009 の切り替え先） |
| 作業ロールの 1 行 | `useWorkRole().current?.name`。無いとき（読み込み中でない）は「作業ロール: なし」。名前は文字として出す。読み込み中は 1 行を出さない | `WorkRoleRef` は `{ roleId, name }`、`status` で読み込み中を見分けられる |
| 廃止の範囲 | `frontend/src/features/admin/` の全ファイル、`registrationModules.ts` の読み込み、文言 `admin.*`。`/admin` は登録の道に無くなり「見つからない」になる。管理者でない利用者が `/admin` を開いたときも、登録の道が無いため同じく「見つからない」（今の「権限が無い」の表示ではなくなる） | `decideRoute` は登録の道に合わなければ `NOT_FOUND` を返し、`ADMIN_FORBIDDEN` は登録された `ADMIN` の道に限る |
| 検査の移し先 | E2E 030 の「ログイン → 管理の入口 → ログアウト」は、ホームの管理のカードから利用者の管理の画面を開く流れに書き換える。E2E 130 の「権限が無い」の表示の 20 組は、DSL の管理の画面を開き、その読み込みの API の GET を 403 に差し替える形に移す。E2E 090 の `/admin` の確かめは、実在する管理の道 1 つだけにする。単体テストは、ホーム・サイドバーのテストへ移す。移す前と後の件数は Build and Test で記録する | 030・130 は `/admin` を開く操作と `/api/admin/check` の差し替えに頼る。`AdminForbiddenProvider` は任意の `/api/admin/` の 403 を拾う（`/api/admin/check` に限らない） |

以下の質問は、上の決まりだけでは決まらない点に絞った（2 問）。

## Q1. [デザイン] 画面を開けて、さらに子の項目も持つ項目のアイコンは、どちらを先にするか

文脈: 業務のメニューには、テーブルかビューを指して開ける項目が、子の項目も持つ形があります（サイドバーでは項目の名前が移動のリンクで、開閉の矢印が別に付きます）。DSL の `icon` が無いとき、項目の種類（テーブル・ビュー・フォルダ）でアイコンを決めること（AC4.4.2）は決まっていますが、「テーブルでもあり、子も持つ」項目はどちらにも当たり、優先が決まっていません（契約 C5 の未解決の点）。ホームのカードも同じ規則で決まります。

**推奨: A。** 理由: 項目の名前を押して開く先はそのテーブル（またはビュー）なので、アイコンも「何を開くか」を示す方が、押す前の見当と合います。子を持つことは、サイドバーの開閉の矢印がすでに示しています。ホームのカードは子の一覧を出さないため、フォルダのアイコンだと「開くとテーブルが出る」と見誤ります。B は、まとまりが見分けやすい代わりに、テーブルとして開ける項目がフォルダに見えます。

- A. 画面の種類（テーブル・ビュー）を先にする。子を持つだけの項目（開けない項目）だけがフォルダ。
- B. 子を持つなら、いつもフォルダにする。テーブルかビューのアイコンは、子を持たない項目だけ。
- X. Other (please specify)

[Answer]: A

## Q2. [開発] 許す一覧に無い `icon` を DSL に書いたとき、どうするか

文脈: DSL の `icon` に、許すアイコンの名前の一覧に無い名前（打ち間違い・上流に無い名前）を書いたときの扱いが、ストーリー AC4.4.1 で「拒否か既定のアイコンか」と未決です。今のサーバーは、一覧に無い・空の `icon` を黙って既定の `list` に置き換えてメニューを返し（ログも出さない）、画面も同じく `list` に落とします。拒否にするなら、DSL の投入の検証（今は `icon` を検証しない）に新しい規則を足すことになります。

**推奨: A。** 理由: 今の動き（サーバーも画面も `list` に落とす）をそのまま保てるため、変更が増えず、前の版の画面とも一致します。メニュー全体が 1 項目のアイコンの名前のために止まる（B）ことはありません。また、一覧は make-you-chic-ui の固定先を更新するたびにそろえ直すので、B だと更新の前後で、すでに適用した DSL が投入の検証を通らなくなる恐れがあります。アイコンは見た目の補助で、名前が文字で伝わるため（NFR3.2）、既定に落ちても業務は止まりません。

- A. 既定のアイコン（`list`。種類の既定に落とす）にする。今の動きのまま。DSL の投入の検証は変えない。
- B. DSL の投入の検証で拒否する（`icon` が一覧に無い DSL は適用できない）。
- X. Other (please specify)

[Answer]: A

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 単位: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/units-generation/unit-of-work.md`・`unit-of-work-story-map.md`
- 要件・ストーリー・契約・設計の決定・画面イメージ: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/requirements-analysis/requirements.md`・`user-stories/stories.md`・`contract-design/contract-summary.md`・`domain-design/decisions.md`・`refined-mockups/mockups.md`
- 既存のコード（読み取りだけ）: `backend/src/main/java/cherry/mastersmith/navigation/`・`frontend/src/app/navigation/`・`frontend/src/app/pages/HomePage.tsx`・`frontend/src/app/layout/ShellLayout.tsx`・`frontend/src/features/admin/`
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 管理の区画の 6 項目のアイコン（利用者＝`user`、招待＝`mail`、グループ＝`users`、ロール＝`shield`、ロールの受け渡し＝`download`、DSL＝`edit`）は、画面イメージの Q7 A の割り当てに、取り込み済みの 3 つを足した形。ほかの割り当てがよければ承認の場で直す。
- [assumption] E2E の移し先（030 の書き換え・130 の DSL の管理の画面への移し）の細部は、コード生成の計画で決める。
