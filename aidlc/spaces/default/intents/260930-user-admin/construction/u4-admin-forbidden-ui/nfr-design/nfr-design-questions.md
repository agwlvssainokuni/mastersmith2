# NFR Design の質問 — u4-admin-forbidden-ui

単位 U4（`aidlc/spaces/default/intents/260930-user-admin/inception/units-generation/unit-of-work.md`、種類 ui）の NFR 設計のための質問です。ui の単位のため、成果物は `performance-design.md`・`security-design.md`・`logical-components.md`・`traceability.json` の4つです（拡張性・信頼性・観測性は service の単位だけ）。確かめた資料は、この単位の承認済みの NFR 要件 `construction/u4-admin-forbidden-ui/nfr-requirements/`（`performance-requirements.md` の NFR5.1・NFR9.1〜NFR9.4、`security-requirements.md` の NFR1.1〜NFR1.5・NFR3.1・NFR3.2・NFR9.5 と残る危険 R1〜R4・承認の場の決定 R-01〜R-04 と申し送り、`tech-stack-decisions.md` の NFR7.1〜NFR7.3・NFR8.1・NFR8.2・NFR9.6〜NFR9.12）、NFR 要件の再レビューの新しい Minor の R-05（R4 の戻り方の補足）、承認済みの機能設計 `construction/u4-admin-forbidden-ui/functional-design/`（`functional-spec.md` の D1〜D14 と承認の場の決定、`frontend-components.md` の 1節〜8節）、機能設計の再レビューの新しい Minor の R-07（`applyOwnProfile` が描いた時点のログイン状態を持つ）、契約 `inception/contract-design/contract-summary.md` の C4、今のコード（`frontend/src/app/display-settings/DisplaySettingsProvider.tsx`・`frontend/src/app/layout/ShellLayout.tsx`・`frontend/src/app/registry/types.ts` の `LoginState`・`frontend/src/features/auth/loginStateProvider.ts`、E2E の `frontend/e2e/060-invitation-accessibility.e2e.ts` と手伝いの `support/loginPreferences.ts`・`support/pageProblems.ts`・`support/displayCombos.ts`・`support/axe.ts`、`vendor/make-you-chic-ui` の `Avatar.tsx` の `getInitials`）です。U4 はサーバーのコードと依存を変えず、配備先（開発者の PC 上のコンテナ）にも基盤の論点はありません。新しく決める論点は、NFR 要件と機能設計がコード生成に回した作りの3点（130 で2語の氏名を作る置き場、差し替えた 403 のコンソールの表示の除き方、R-07 の直しの範囲）です。

## 設計の要点（案）

1. **部品の構成（logical-components）**: 置き場は機能設計の2節のとおりとする。判定 `isAdminForbidden` と読み直しの口 `refreshSessionOnce` は `frontend/src/shared/api-client/`、状態と表示（`AdminForbiddenProvider`・`useAdminForbidden`・`AdminForbiddenView`・`forbiddenHeadingKey`）は `frontend/src/app/admin-forbidden/`、自分の氏名と言語の口は `frontend/src/app/display-settings/` に置く。失敗の範囲はブラウザの中の1つのタブに閉じ、サーバー・内部DB・ほかのタブには及ばない。Provider は状態用と関数用の2つの context に分け、状態が変わっても `report` と各画面の関数の同一性が変わらない形にする（部品 3.3）。依存の向きは `features/*` → `src/app/` の口 → `src/shared/api-client/` の一方向で、`src/app/admin-forbidden/` は `features/*` を、ApiClient は `src/app/` を読まない（NFR9.12）。
2. **性能（performance-design）**: S6 への置き換えは、画面が 403 を渡したその描画の中で終え、読み直しの結果を待たない（NFR9.1）。読み直しは `inFlightRef` で終わるまで重ねず、同じ URL の重なり・結び付かない 403・401 の更新との重なりのどれでも更新の API は1回（NFR9.2）。決まった間隔の確かめ・時間切れは置かない。画面の時間の数値の目標は置かず（NFR9.3）、読み直しの時間は更新の API の既存の目標（同時 10 件で p95 1 秒）に任せて測り直さない（NFR5.1）。初回の JavaScript は既存の `check-bundle-size.mjs` で前後の値を記録する（NFR9.4、警告だけ）。キャッシュ・プール・非同期の仕組みは足さない。
3. **画面の判定とサーバーの判定（security-design）**: S6 とメニューを隠すことは表示だけで、管理の API の 401・403・200 と監査はサーバーのまま（`backend/` の差分なし、NFR1.1）。「権限が無い」は純粋な関数 `isAdminForbidden` の1か所で、`/api/admin/` の下・403・`ACCESS_DENIED` の3つがそろうときだけ true、知らない値は例外を出さず false（NFR1.2、性質ベースのテストで網羅）。管理者でない利用者の管理の画面は、部品を作らないため管理の API を呼ばない（NFR1.3）。401・ログアウトの扱いは変えない（NFR1.4）。画面の側の印はトークンの応答からだけ入り、氏名と言語の口は印を変えない（NFR1.5）。
4. **画面に出す値と保存（security-design）**: S6 は見出し・決まった文言・「ホームへ戻る」（アプリの中の `Link`）だけを出し、`detail` と原因を出さない。U4 の部品はコンソールに出力しない。ブラウザの保存に書くのは言語だけで、氏名は書かない（NFR3.1）。HTML を直接埋め込まず、外部の資源を足さず、CSP を変えない（NFR9.5）。
5. **残る危険 R4 の書き方（NFR 要件のレビューの R-05）**: `security-design.md` の残る危険に R4 を引き継ぎ、戻り方に次の一文を足す。「S6 にいるあいだは管理の API を呼ばず、更新は API が 401 を返したときにしか走らないため、アクセストークンの期限が切れても自動では戻らない。確実に戻る手段は再読み込みと再ログインで、期限切れの更新で戻るのは、期限が過ぎた後にほかの画面が API を呼んだときに限る」。NFR 要件の承認で「実際の動きで受け入れ」と決まっているため質問にしない。
6. **自分の氏名と言語の反映（機能設計のレビューの R-07）**: `applyOwnProfile` は、描いた時点のログイン状態を閉じ込めず、呼ばれた時点の最新のログイン状態と、そのログイン状態で求めた「見せ方を除く当てている値」（テーマと文字の大きさ）を ref から読む。ref は `useLayoutEffect` で描画のたびに新しくし（描画の中で ref に書かない）、`applyOwnProfile` は依存を持たない `useCallback` で Provider が生きている間は同じ関数にする。保存の途中でトークンの更新が入っても、更新の後のログイン状態に結び付けて氏名と言語が当たることを `DisplaySettingsProvider.test.tsx` に足す。直しの範囲（既存の `applyUserPreferences` などにも当てるか）は Q3。
7. **実際のブラウザの検査 130（NFR7.3）**: `frontend/e2e/130-admin-forbidden-accessibility.e2e.ts` は、060 と同じ組み立て（`watchPage` → `prepareCombo` → `routeLoginPreferences` → `loginAsAdmin` → 組の確かめ）に、`GET /api/admin/check` だけを 403・`ACCESS_DENIED` の見本に差し替える `page.route` を足し、既存の 20 組で axe・はみ出し・`role="status"`・見出しのフォーカスを確かめる。上の帯の氏名は、ログインと復元の応答の `user.displayName` を架空の2語に書き換えて作り、Avatar の文字が2文字であることを確かめる（作りの置き場は Q1）。組の値の書き換え（`user.theme`・`user.fontSize`）と同じ2本の API を書き換えるため、1つの差し替えで両方を当てる必要がある（Playwright は同じ要求に複数の差し替えを重ねても、先に応答を返した1つしか効かない）。差し替えた 403 によるコンソールの読み込みの失敗の表示は、130 の側だけで除く（Q2）。管理の POST は送らず、資格情報は環境変数で渡し、json の報告を文字列で検索する（NFR3.2）。
8. **テストと関門**: 部品のテストは `frontend-components.md` 7節の一覧のとおり。フロントエンドのカバレッジの下限（行 80%・分岐 70%）を保ち、除外を足さない。バックエンドのパッケージには手を入れない（NFR9.8）。新しい依存は足さない（NFR9.6）。130 は `./gradlew verify` と CI の外に置き、流れの E2E の本数に数えない（NFR9.10）。
9. **traceability**: NFR 要件の ID（NFR1.1〜NFR1.5・NFR3.1・NFR3.2・NFR5.1・NFR7.1〜NFR7.3・NFR8.1・NFR8.2・NFR9.1〜NFR9.12）を、4つの成果物の節へ1対1で結ぶ。新しい枝番は足さない。NFR2・NFR4・NFR6・NFR10・NFR11 は NFR 要件と同じく N/A とする。

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| 判定・状態・表示・反映の置き場と口の名前（`isAdminForbidden`・`refreshSessionOnce`・`useAdminForbidden`・`AdminForbiddenView`・`forbiddenHeadingKey`・`useApplyOwnProfile`） | 機能設計 `frontend-components.md` 2節・3節、契約 C4 |
| Provider の仕組み（パスが変わった描画の中で捨てる、今の URL は ref で読む、2つの context、`report` は同一、読み直しは終わるまで重ねない） | 機能設計の承認の場の R-02、`frontend-components.md` 3.3 |
| ForbiddenByApi から ForbiddenByRoute へ移っても ShellLayout と S6 を作り直さない（同じ形の木） | 機能設計の承認の場の R-05、NFR7.1 |
| S6 の Alert は info（`role="status"`）、見出しへフォーカスを移す | 機能設計 D12、`frontend-components.md` 3.4、NFR7.1 |
| 画面の時間の数値の目標を置かない。読み直しの時間は更新の API の既存の目標のまま測り直さない | NFR 要件の Q2 A、NFR5.1・NFR9.3 |
| 130 は既存の 20 組で、ForbiddenByApi だけを実際のブラウザで確かめる。ForbiddenByRoute は部品のテストで確かめる | NFR7.3、NFR 要件の承認の場の R-04 |
| 130 の2語の氏名は、サーバーの状態を変えず、ログインと復元の本物の応答の `user.displayName` だけをページの中で書き換えて作る。値は架空の2語で、注記・添付・標準出力に出さない | NFR7.3、NFR 要件の承認の場の R-01 |
| 共有の `watchPage`（`support/pageProblems.ts`）の既定の除外（未ログインの更新の 401 だけ）は変えない | NFR7.3、NFR 要件の申し送り |
| R4（古い印のまま管理の画面を開くと S6 になる）は、実際の動き（再読み込み・再ログイン・期限が過ぎた後のほかの画面の API で戻る）で受け入れ済み。ForbiddenByRoute でも読み直しを呼ぶ形にはしない | NFR 要件の承認（`aidlc-state.md` の記録、コミット 24dbfb3）、`security-requirements.md` の残る危険 R4 と申し送り |
| `applyOwnProfile` は呼ばれた時点の最新のログイン状態を読む形にする（ref など）。関数の同一性を保つ | 機能設計の再レビューの R-07（Functional Design の承認でコード生成の計画へ申し送り） |
| `LoginState` は利用者の ID を持たない。ログアウトの後は `loggedIn` が false になるため、`applyOwnProfile` は何もしない | `frontend/src/app/registry/types.ts`、機能設計 D13 |
| 新しい依存を足さない。`vendor/make-you-chic-ui` を変えない。CSP を変えない。`backend/` を変えない | NFR9.6・NFR9.7・NFR9.5・NFR1.1 |
| 配備先は開発者の PC 上のコンテナで、U4 に基盤の設計の論点は無い | `project.md` の Deployment の学び |

## Q1. 130 で上の帯の氏名を2語にする書き換えを、どこに置きますか？

130 は、組の値（`user.theme`・`user.fontSize`）を当てるために既存の `routeLoginPreferences`（`support/loginPreferences.ts`）を使います。2語の氏名の書き換えも同じ2本の API（POST `/api/auth/login`・`/api/auth/session/refresh`）の応答に当てるため、別の `page.route` を重ねると、先に応答を返した差し替えだけが効き、もう一方が当たりません。そのため、1つの差し替えで組の値と氏名の両方を当てる形にする必要があります。今の手伝いは `user` に渡した値を広げて重ねる（`{ ...user, ...preferences }`）作りのため、省略できる項目を1つ足すだけで両方を当てられます。

A. 共有の手伝いの引数の型 `LoginPreferences` に、省略できる `displayName` を足す（推奨）。渡したときだけ `user.displayName` を書き換え、渡さないときの動作は今と同じ（050〜090 は渡さないため影響が無い）。書き換えの仕組みが1か所のまま。コード生成で、型の変更の後も 050〜100 が通ることを `./gradlew e2eTest` で確かめて記録する
B. 共有の手伝いは変えず、130 の中に組の値と氏名を一緒に書き換える差し替えを書く（`routeLoginPreferences` の写し）。共有のファイルに手が入らないが、同じ書き換えの仕組みが2か所になり、後で片方だけ直されるおそれがある
C. 共有の手伝いに、`user` の任意の項目を書き換える新しい関数（例: `routeLoginUser(page, overrides)`）を足し、`routeLoginPreferences` をその上に作り直す。一般の形になるが、既存の5本の E2E が使う関数の中身を書き換えることになる
X. Other (please specify)

[Answer]: A

## Q2. 差し替えた 403 によるブラウザのコンソールの表示を、130 の側でどう除きますか？

`GET /api/admin/check` を 403 に差し替えると、Chrome はコンソールに「Failed to load resource: the server responded with a status of 403 (Forbidden)」を出します。この文には URL が入らず、URL は表示の位置（`message.location().url`）にだけ入ります。共有の `watchPage` は問題を文字列で集めるため、集めた後の一覧からは URL で絞れません。NFR 要件は「除外は 130 の側だけで行い、共有の `watchPage` の既定は変えない」と決めています。

A. 共有の `watchPage` に、除く URL のパスを渡せる省略できる引数（既定は空）を足し、130 だけが `/api/admin/check` を渡す。位置の URL で1本に絞れ、既定の動作は変わらない。ただし除外の仕組みが共有のファイルに入るため、「130 の側だけで行う」の読み方として依頼者の確認が要る
B. 共有の `watchPage` は変えず、130 の中で別のコンソールの見張りを足し、位置の URL が `/api/admin/check` の読み込みの失敗の表示を数える。合否は、`watch.problems` からその件数だけ同じ文を引いた残りが 0 件であることで判定する（推奨）。共有のファイルに手が入らず NFR 要件の文のとおりで、件数で引くため、ほかの URL の同じ文の失敗は残って見つかる
C. 130 の中に `watchPage` の写しを書き、位置の URL で除く。絞り方は A と同じだが、見張りの仕組みが2か所になる
X. Other (please specify)

[Answer]: B

## Q3. `applyOwnProfile` を最新のログイン状態で動かす直し（R-07）を、どこまで当てますか？

今の `DisplaySettingsProvider` は、値の関数（`applyUserPreferences`・`setPreview`・`setLanguage`）がどれも描いた時点のログイン状態を閉じ込めています（`useMemo` の依存に `loginState`）。R-07 が指摘した「保存の応答を待つ間にトークンの更新が入ると、古いログイン状態に結び付けて当て、すぐ捨てられる」は、プリファレンスの画面の保存の後の `applyUserPreferences` にも同じ形で起こりえます。ただし機能設計は「今の `applyUserPreferences` と `setLanguage` は変えない」と決めています（`frontend-components.md` 3.6）。

A. 新しい `applyOwnProfile` だけを、ref で最新のログイン状態と当てている値を読む形にする（推奨）。機能設計の範囲のままで、既存の口の動き（E2E の 080 を含む）を変えない。既存の `applyUserPreferences` に同じ形の危険があることは `security-design.md`（または `logical-components.md`）の残る危険として記録し、後の Intent への申し送りにする
B. `applyOwnProfile` に加えて、既存の `applyUserPreferences` も同じ ref の形に直す。同じ危険をまとめて消せるが、機能設計の「変えない」と違うため上流との差に記録し、プリファレンスの画面のテストと 080 を流し直す作業が U4 に加わる
C. ref を使わず、ログイン状態の提供元（`LoginStateGate`）から呼ばれた時点の値を読む口を足す。描画を待たずに最新の値を読めるが、骨組みのログイン状態の口が増え、U4 の範囲を超える
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめと、U4 の NFR 設計の計画:

- 設計の要点（案）のとおりに作る。成果物は ui の4つ（performance・security・logical-components・traceability）。
- Q1 A: 130 で上の帯の氏名を架空の2語にするため、共有の手伝いの型 `LoginPreferences` に省略できる `displayName` を足し、ログインと更新の応答の書き換えを1つの差し替えにまとめる。渡さない既存の E2E（050〜090）の動きは変わらない。
- Q2 B: 差し替えた 403 のコンソールの表示は、共有の `watchPage` を変えず、130 の中に足すコンソールの見張りで除く。位置の URL が `/api/admin/check` の表示を数え、その件数だけ同じ文を問題の一覧から引く。
- Q3 A: 機能設計の申し送り R-07 は、新しい `applyOwnProfile` だけを、最新のログイン状態を ref で読む形にして直す。既存の `applyUserPreferences` の同じ形の危険は、残る危険として記録し、後の Intent へ申し送る。
- NFR 要件の申し送り R-05（R4 に「S6 にいるあいだは自動では戻らない」の一文）は、設計の要点の5のとおり成果物に書く。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
