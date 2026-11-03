# セキュリティの要件 — u5-menu-icons-home

出典: 要件（`inception/requirements-analysis/requirements.md` の NFR1.3・NFR3.1・NFR3.2・NFR4.2・NFR5.3・NFR6.1・FR7〜FR11）、ストーリー（`inception/user-stories/stories.md` の US3.1〜US3.3・US4.2〜US4.4・US5.2・US5.3）、機能設計（`construction/u5-menu-icons-home/functional-design/functional-spec.md` の D1〜D32、`frontend-components.md`）、契約（`inception/contract-design/contract-summary.md` の C5・C7）、`team.md`（Testing Posture の N 階層のメニュー・認証・認可・監査、Way of Working の E2E）、`project.md`（Forbidden・Mandated）。

U5 は、メニューの応答に `tableKind` を足し、アイコンの決め方・ホームのカード・ロゴを画面に足し、管理の仮の画面を外す単位（種類 ui）。新しい API・表・依存は足さない。セキュリティの要件は、「印が見られない項目の存在を明かさない」「メニューの値を信頼できない入力として扱う」「画面で権限を足さず、廃止で権限の確かめを弱めない」「応答を足すだけにする」に絞る。技術の選択は `tech-stack-decisions.md`。

## 番号の付け方

inception の NFR と同じ番号は、同じ意味でだけ使う。NFR1.3（メニューの応答の印は見られる項目にだけ付く）の細目は NFR1.3.1〜NFR1.3.5。上流の NFR に当たる行が無い追加の要件（メニューの値を信頼できない入力として扱う、画面で権限を足さない、廃止で権限の確かめを弱めない）は、近い NFR1.3 の枝番（NFR1.3.3〜NFR1.3.5）に寄せた。NFR3.1（axe）は NFR3.1.1〜NFR3.1.2、NFR3.2（アイコンは飾り）は NFR3.2.1、NFR4.2（応答は足すだけ）は NFR4.2.1、NFR5.3（E2E は新しい流れを足さない）は NFR5.3.1、NFR6.1（文言は日英）は NFR6.1.1。

## 要件の一覧

| ID | 要件 | 根拠・決まり | 確かめ方 |
|---|---|---|---|
| NFR1.3.1 | `tableKind` は、その利用者が見られる移れる項目（テーブルの主権限が `READ`・`FULL`）にだけ付き、見られない項目は応答に出ない（今の絞りのまま）。フォルダと、DSL にテーブルが見つからない項目は `null`。値は `TABLE`・`VIEW`・`null` の 3 つだけで、スキーマ名・DSL の識別・ロールの ID・権限の値・テーブルの組以外の情報を載せない。認証が無い要求は 401 のまま | NFR1.3・`functional-spec.md` D2・D4・D5・C5 | サーバーの結合テスト `NavigationApiIT`（`TABLE`・`VIEW`・フォルダの `null`・DSL 差し替え後の `null`）、`NavigationAuthorizationApiIT`（未認証 401）、`NavigationMenuAccessConsistencyIT`（見られない項目が応答にも `tableKind` にも出ない）が通る。`MenuFilterPropertyTest` に性質を足す（`tableKind` は移れる項目にだけ付く・移れない項目は常に `null`・権限を足しても見えている項目の `tableKind` は変わらない） |
| NFR1.3.2 | `tableKind` を足したことで、アプリのログ・トレースの属性・エラー応答にメニューの項目の値（表示名・テーブルの名前）が出る形にならない。`NavNode`・`NavNodeResponse` の `toString` は、位置の道・移れるか・子の数だけの今の形のまま（`tableKind` を含めるときも値の名前を足さない）。TRACE を有効にしてもテーブルの名前が出ない | `team.md` Code Style（個人に関する値・業務データの値を持つ型の `toString`）・`project.md` Forbidden | 既存の `NavigationSecretLeakIT`・`NavigationRecordsToStringTest` が、`tableKind` を足した型で通る（`toString` の形が変わらない確かめを足す） |
| NFR1.3.3 | メニューの値（表示名・`icon`・`tableKind`）を、画面で信頼できない入力として扱う。表示名は文字として描き（`<`・`>`・`&`・`"`・`'` を含んでも HTML として解釈されない。`dangerouslySetInnerHTML` を使わない。`react/no-danger` が止める）、`icon` は許す一覧と照らして一覧に無ければ既定に落とし、`tableKind` は `TABLE`・`VIEW` 以外を `null` にする。カードとサイドバーの道は、業務は `toTablePlaceholderPath` の 1 か所、管理は登録済みの道だけで作り、外部の URL・`javascript:` の道を作らない。カードの `Link` の `to` はアプリの中の道だけ | AC4.4.6・`functional-spec.md` D29・`team.md` Testing Posture（N 階層のメニューの「信頼できない入力」） | 画面の部品のテスト: 表示名に `<script>`・`<img onerror>`・`"`・`'` を含む項目が、サイドバーとホームのカードで文字として出る。`icon` に `javascript:`・`https://…`・大文字違い・空・知らない名前を入れても既定に落ち、道が作られない。`parseNode` に知らない `tableKind` を入れると `null`。`fast-check` の性質ベースのテスト（任意の文字列の表示名・`icon` で、描かれた結果に要素が増えない・道がアプリの中の形から外れない）。サーバー側は既存の `NavigationUntrustedInputIT` が通る |
| NFR1.3.4 | 画面で権限を足さない。ホームの業務のカードは、サーバーが返したメニューの応答の項目だけから作り（応答に無い項目のカードは作れない）、管理のカードは、ログイン状態の `admin`（サーバーの応答）が真のときだけ、サイドバーの管理の区画と同じ登録から作る。管理の画面・管理の API の権限の判定はサーバーに置く（画面で入口を隠すことをサーバー側の検査の代わりにしない） | NFR1.3・`project.md` Mandated（役割・権限の判定は API ごとにサーバー側）・`functional-spec.md` D16・D18 | 画面の部品のテスト: 管理者でないときに管理のカードが出ない・応答に無い項目のカードが出ない（`buildHomeCards` の性質ベースのテスト）。サーバー側の既存の確かめ（`AdminPathBoundaryIT`・`AdminAuthorizationManagerTest`・`AdminAccessDeniedHandlerTest`・`AdminAuthenticationEntryPointTest` など）が変更なしで通る |
| NFR1.3.5 | 管理の仮の画面（`/admin`・`features/admin`）を外しても、権限の確かめを弱めない。サーバー側の管理の権限の確認 API `GET /api/admin/check` と 403 の判定は残し、未認証 401・管理の権限を持たない 403・持つ 200 のサーバー側のテストを変えずに通す。仮の画面で確かめていた画面の検査（権限が無いときの表示・アクセシビリティ・管理の権限の確認の API）は、別の画面か別のテストへ移して残し、移す前と後の件数を Build and Test で記録する。廃止した道 `/admin` は、どの利用者でも「見つからない」で、管理者でない利用者に対して存在や権限の有無を明かさない | FR8.3・FR8.4・`functional-spec.md` D24〜D26・`team.md` Testing Posture（画面を廃止する・移すときの検査） | サーバー: 管理の権限の確認 API の結合テスト（401・403・200）が変更なしで通る。画面: `/admin` が `NOT_FOUND`（`decideRoute` の単体テスト・`AppRouter.test.tsx`）。E2E: 030・090・130 の書き換え・移しの後も全件が通り、移す前と後の件数を記録する。権限が無いときの表示（`ADMIN_FORBIDDEN`）は登録された管理の道（DSL の管理の画面など）で E2E 130 が残す |
| NFR3.1.1 | ホームとサイドバーのアクセシビリティを、実際のブラウザの axe（WCAG 2.1 AA）で違反 0 件にする。ブランドカラーとテーマのすべての組で行い、次の状態を含める: 業務のカードと管理のカードの両方があるとき・管理のカードだけのとき・業務のカードが 0 件で案内を出すとき・読み込みの失敗と「もう一度」・長い名前のカード・サイドバーの長い項目名と展開した状態・2 語の氏名（Avatar の頭文字が 2 文字）・日本語と英語。規則を外す・要素を `exclude` する形で通さない | NFR3.1・AC5.2.1・AC5.2.2・`project.md` Forbidden（axe の規則を外さない）・Mandated の学び（誤りの状態と現実に近いデータ） | Playwright の既存の画面の枠の axe の検査（E2E 160 など）を書き換え・拡張して、ホームのカードとアイコンの付いたサイドバーを加える（本数に数えない）。違反 0 件を件数で Build and Test に記録する |
| NFR3.1.2 | 画面全体の横のスクロールを出さない（375 px を含む）。ホームのカードの折り返し、上のバーのロゴを足した狭い幅の上のバー、開いたメニュー・ポップアップが画面の中に収まる | NFR3.1・`project.md` の学び（はみ出しの確かめ）・`functional-spec.md` D27・D31 | E2E の既存のはみ出しの測り（`support/overflow.ts`）を、ホームとロゴのある上のバーに当てる（375 px の幅を含む） |
| NFR3.2.1 | アイコン（サイドバーの項目・ホームのカード・上のバーのロゴ）は飾りとして読み上げから外し（`aria-hidden`）、名前が文字で伝わるようにする。アイコンの見分けだけで情報（テーブル・ビュー・フォルダの違い、管理の項目の違い）を伝えない。カードは 1 つのリンクで、名前が読み上げられ、キーボードの Tab で届き Enter で押せ、フォーカスの輪が見える | NFR3.2・AC4.3.4・AC5.2.4・`functional-spec.md` D21 | 画面の部品のテスト（`HomeCard`・`HomeCardList`・`HomePage`・`ShellLayout`）: アイコンがアクセシビリティツリーに出ない・カードのリンクの名前が表示名・見出しの階層（h1→h2）。`vitest-axe` を部品ごとに 1 件。アイコンの色は `currentColor` で、色の対比は E2E の axe の色のコントラストの検査で確かめる |
| NFR4.2.1 | メニューの応答の変更は項目を足すだけにする（`id`・`label`・`icon`・`table`・`navigable`・`items` の名前・型・値を変えない）。画面は `tableKind` の無い応答（前の版）と、知らない `tableKind` の値を読んでも壊れず、`null` として扱う。この単位は内部DB・設定・環境変数を変えない。戻しはイメージだけ | NFR4.2・AC5.3.2・`functional-spec.md` D1・D9・D32 | サーバー: `NavigationApiIT` で既存の項目の値が変わらないことと、`tableKind` が足されたこと。画面: `parseNode` の単体テストで、項目なし・`null`・`TABLE`・`VIEW`・知らない値の 5 通り。アイコンを決める関数が、`tableKind` の無い応答でも例外を投げず既定に落ちること |
| NFR5.3.1 | この単位は E2E の流れを新しく足さない。既存の E2E の書き換え・移し（030 のホームの管理のカードから開く流れ、130 の権限が無い表示の DSL の管理の画面への移し、090 の `/admin` の確かめの直し、160 の画面の枠の axe の拡張）で確かめ、本数に数えない。この単位は `frontend/src` の本体と `navigation` の `src/main` に触れるため、統合の前に `./gradlew e2eTest` を流して通す | NFR5.3・`team.md` Way of Working（E2E を流す条件）・Testing Posture（画面を廃止する・移すときは本数に数えない） | 統合の前の `./gradlew e2eTest` の全件の通過。移す前と後の E2E の件数を Build and Test で記録する |
| NFR6.1.1 | 足す文言（ホームの説明の直し・作業ロールの 1 行・「業務」「管理」の見出し）は、日本語と英語の両方を用意し、既定を日本語とする。作業ロールの名前は文字として差し込む（HTML として解釈しない）。管理の区画の項目の名前は各機能の登録の文言のまま。文言の鍵は `functional-spec.md` D28 と `frontend-components.md` 3 節のとおり | NFR6.1・`functional-spec.md` D28 | 既存の文言のテスト（`messages.test.ts`: 日本語と英語の鍵の対応）が通る。`HomePage` の部品のテストを `ja` と `en` の両方の言語で行う |

## 脅威の見立て（U5 の範囲）

| 脅威 | 見立て | 対応 |
|---|---|---|
| 印（`tableKind`）で、見られない項目の存在やテーブルの種類を明かす | 応答に出る項目は絞った後の見られるものだけで、`tableKind` は絞った後の移れる項目にだけ付くため、新しい漏えいは生じない。DSL を差し替えた直後の食い違いでも `null` で、名前や識別は載らない | NFR1.3.1 |
| メニューの定義（表示名・`icon`）に悪意のある文字列が入り、画面で実行される・外部へ誘導される | 表示名は DSL の作者（管理者）が書く値だが、信頼できない入力として扱う。React の文字としての描画と許す一覧の照合、道の組み立ての 1 か所化で防ぐ | NFR1.3.3 |
| ホームの管理のカードで、権限の無い人に管理の入口が見える・押せる | 画面の出し分けは見せ方だけで、管理の API と管理の道はサーバーが守る。`admin` はサーバーの応答。出し分けを誤っても、押した先は 403 の表示になる | NFR1.3.4・NFR1.3.5 |
| 仮の画面の廃止で、権限の確かめの検査が失われる | サーバー側の API と 403 の判定は残し、画面の検査は別の画面へ移す | NFR1.3.5 |
| `tableKind` を足したことでログに項目の値が出る | `toString` の形を変えない | NFR1.3.2 |
| 前の版の画面が新しい応答で壊れる・新しい画面が古い応答で壊れる | 足すだけの変更と、`null` として読む | NFR4.2.1 |

## この単位に当たらない inception の NFR

NFR1.1（自作の SVG の安全）は u6-app-icon-assets、NFR1.2（本人だけが自分の設定を変える）・NFR3.3（タイムゾーンの選択）・NFR4.1（内部DB の列を足すだけ）は u2-display-time-zone、NFR5.1（日時の必須のテスト）・NFR5.2（テストのタイムゾーンの固定）は日時を扱う単位（u3・u4）が受け持つ。U5 はロゴに U6 の `AppIcon` を使うだけで、SVG の中身を作らない。

## 上流との差（承認済みの要件・設計は書き換えず、ここに記録する）

- **`/admin` の扱い（機能設計 D24 の補足）**: 機能設計は「どの利用者でも `/admin` は見つからない」と書くが、`decideRoute` は未ログインの利用者を先にログインの画面へ送る（レビュー役の Minor の指摘）。NFR1.3.5 の「管理者でない利用者に対して存在や権限の有無を明かさない」は、ログイン済みの利用者に対する「見つからない」を指す。未ログインはログインの画面（今の動き）。機能設計の文言の直しは承認の場の扱いで、この要件は実際の `decideRoute` の動きに合わせて書く。
- **検査の置き場（NFR1.3.5）**: `GET /api/admin/check` を画面が呼ばなくなるため、この API の確かめは k6 とサーバーの結合テストだけが持つ。画面側の「確認 API の呼び出し」を確かめていた単体テストは、API の呼び出しを持たないホーム・サイドバーの検査へは移せない。移す前と後の件数に、この「移さず外すもの」を別に数えて記録する（Build and Test）。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 要件・ストーリー: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/requirements-analysis/requirements.md`・`inception/user-stories/stories.md`
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/functional-design/functional-spec.md`・`frontend-components.md`
- 契約: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-summary.md`（C5・C7）
- 規則（rules）: U5 は ui の単位で `rules.md` を作らないため無い。決まりは `functional-spec.md` の D 番号
- 機能設計のレビュー: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/functional-design/reviews/review-01.md`（D24 の未ログインの扱いの指摘）
- 既存のコード（読み取りだけ）: `frontend/src/app/routing/decideRoute.ts`、`backend/src/test/java/cherry/mastersmith/navigation/`、`backend/src/test/java/cherry/mastersmith/access/web/`
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 機能設計のレビュー役の指摘（D24: 未ログインは `NOT_FOUND` ではなくログインの画面へ送られる）は、承認の場で機能設計の文言を直す扱いで、この要件は実際の動きに合わせた。
- [assumption] サーバー側の管理の確認 API の結合テストは、今の `access` パッケージのテストが 401・403・200 を確かめている前提。Code Generation の計画で、実際のテストの名前を洗い出して確かめる。
- 未解決の点: 無い。
