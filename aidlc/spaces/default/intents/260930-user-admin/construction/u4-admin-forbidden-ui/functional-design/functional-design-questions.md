# Functional Design の質問 — u4-admin-forbidden-ui

単位 U4（`aidlc/spaces/default/intents/260930-user-admin/inception/units-generation/unit-of-work.md`、種類 ui）の機能設計のための質問です。受け持つストーリーは US2.2（AC2.2.1〜AC2.2.6、M5 B、差2）、関わるストーリーは US5.1（自分の氏名と言語の反映の口、FR6.3）です（`aidlc/spaces/default/intents/260930-user-admin/inception/user-stories/stories.md`）。画面は S6 と 9節の D1（`aidlc/spaces/default/intents/260930-user-admin/inception/refined-mockups/mockups.md`）と、`interaction-spec.md` の AdminForbiddenNotice。契約は C4（`aidlc/spaces/default/intents/260930-user-admin/inception/contract-design/contract-summary.md`）、ADR は ADR-005。既存のコードは `frontend/src/shared/api-client/`（`apiClient.ts`・`apiError.ts`）、`frontend/src/app/`（`layout/ShellLayout.tsx`・`login-state/LoginStateGate.tsx`・`display-settings/`・`routing/`・`navigation/navigationItems.ts`・`registry/types.ts`・`pages/NotFoundPage.tsx`）、`frontend/src/features/auth/`（`authSession.ts`・`loginStateProvider.ts`）、管理の画面（`features/admin/`・`features/dsl/`・`features/invitation/`）と、その 403 を確かめているテストを確かめました。

## 設計の要点（案）

上流の決定とコードの確認から導ける、この単位の機能設計の見通しです。質問の答えで決まる点は（Qn）と書きます。

1. **今の 403 の扱い（書き換える対象）**: 3つの画面がそれぞれ別の形で 403 を扱っている。
   - 管理の入口: `features/admin/adminAreaStatus.ts` の `statusFromError` が 403 を code によらず `NotFound` にし、`AdminAreaPage.tsx` が `app/pages/NotFoundPage` を出す。
   - DSL の管理: `features/dsl/useDslAdmin.ts` の `checkForbidden`（状態 403 だけで判定、code は見ない）が `forbidden` を立て、`DslAdminPage.tsx` が `NotFoundPage` を出す。確かめの表示（`DslConfirmDialog`）は閉じない。
   - 招待の管理: `features/invitation/useInvitationAdmin.ts` が 403 を一般の 4xx の文言（`invitation.errorGeneral.client`）で一覧・入力の表示の中に出す。読み直しはしない。
   - 書き換えるテスト: `features/admin/AdminAreaPage.test.tsx`（2件）・`adminAreaStatus.test.ts`（403 を `NotFound` とする3件、性質ベースのテスト1件を含む）・`features/dsl/DslAdminPage.test.tsx`（1件）・`features/invitation/InvitationAdminPage.test.tsx`（一覧・入力の表示・送り直しの3か所）・`features/invitation/failureMessage.test.ts`（403 を一般の文言とする1件）。E2E（`frontend/e2e/030-admin-access.e2e.ts`）は 403 を確かめていないため変えない。
2. **判定の条件（C4 の `isAdminForbidden`）**: 要求のパスが `/api/admin/` で始まり、状態が 403 で、code が `ACCESS_DENIED` のときだけ「権限が無い」とする。状態 403 だけで判定している今の DSL の `checkForbidden` より狭くなる。ほかの 403（code が無い・違う）は各画面の一般の誤りの扱いのまま。置き場は `frontend/src/shared/api-client/`（C4）。純粋な関数として性質ベースのテスト（fast-check）の対象にする。
3. **知らせ方**（Q1）: 管理の画面の失敗を、呼び出し元が渡すか、ApiClient が自分で知らせるか。
4. **表示の切り替えの持ち主**: AppFrame の中に「この URL で権限が無いと分かった」状態を1つ持つ部品（仮の名前 `AdminForbiddenProvider`）を置き、`ShellLayout` がコンテンツの領域を `AdminForbiddenView`（S6）に置き換える。中身を置き換えるため、画面の部品は外れ、その中の確かめ・入力の表示（Modal）も閉じる（AC2.2.1）。状態は URL のパスに結び付け、別の URL へ移ったら捨てる（今の `AppRouter` は SCREEN の画面どうしで `ShellLayout` を作り直さないため、パスで区別する）。
5. **URL の振り分けとの関係**（Q2）: 今の `app/routing/decideRoute.ts` は、ログインしていて管理者でない人が `access: 'ADMIN'` の画面を開くと `NOT_FOUND`（「ページが見つかりません」）を返す。403 の後にログインの状態を読み直して管理者の印が外れると、開いている管理の画面がこの振り分けで「ページが見つかりません」に変わってしまい、AC2.2.4 と食い違う。
6. **ログインの状態の読み直し**（Q3）: 今の画面は、管理者の印をトークンの応答（ログイン・更新）からだけ受け取り、`features/auth/authSession.ts` のメモリに持つ。`LoginStateProvider`（差し込み口4）は `getLoginState` と `subscribe` だけで、読み直しの口が無い。読み直した状態が `LoginStateGate` に届けば、サイドバーの管理のメニューは `buildSidebarEntries`（`visibleWhen: 'ADMIN'`）で自然に消える（AC2.2.2）。
7. **S6 の見出し**（Q5）: 画面イメージは「画面の見出しは残す」とし、表示を出したら見出しへフォーカスを移す（`interaction-spec.md` の AdminForbiddenNotice）。今の3つの画面の見出しは、サイドバーの項目の名前と同じ文言である（「管理」・「DSL」・「利用者の招待」）。
8. **表示の部品（AdminForbiddenView）**: make-you-chic-ui の `Alert`（info）に「この画面を使う権限がありません」（en: "You do not have permission to use this page."）と「ホームへ戻る」（ホームの URL へ読み込み直しなしで移る）を置く。文言は骨組みの文言（`app/i18n/messages/ja.ts`・`en.ts`）に足す。今の `notFound.homeLink`（「ホームへ」）とは別の鍵にする。表示したら見出しへフォーカスを移す。画面部品ごとのアクセシビリティの検査（vitest-axe）を1件入れる。
9. **自分の氏名と言語の反映の口**（Q4）: `useApplyOwnProfile` は、上の帯の氏名・画面の言語・`Accept-Language` を変え、テーマと文字の大きさは変えない（C4）。今の `DisplaySettingsProvider` の `applyUserPreferences` は4つの値（氏名・言語・テーマ・文字の大きさ）を受け取ってブラウザに保存し、`setLanguage` は保存しない見せ方だけを置く。`Accept-Language` は画面の言語から決まる（`installLanguageResolver`）ため、画面の言語が変われば要求の言語も変わる。
10. **置き場と名前（C4 の未解決の点）**: 判定の関数 `isAdminForbidden` は `src/shared/api-client/`、表示と状態と口（`AdminForbiddenView`・`useAdminForbidden`・`useApplyOwnProfile`）は `src/app/` の下の新しいディレクトリ（仮に `src/app/admin-forbidden/`、氏名と言語の口は `src/app/display-settings/`）に置く。機能（features）はすでに `app/i18n`・`app/display-settings`・`app/pages` を読んでおり、骨組みの状態を使う口は `src/app/` に置く今の形に合う。名前は C4 のままとし、変えたときは U5 の機能設計に伝える。
11. **401 の扱いは変えない**: `apiFetch` の 401／`AUTHENTICATION_REQUIRED` の更新と送り直し、`onUnauthenticated` は今のまま（C4、AC3.2.9）。管理の API の外（`/api/me/` など）の 403 には何もしない（AC2.2.3）。
12. **サーバー側**: 403 の応答と監査の「アクセスの拒否」（AC2.2.6）は今のサーバーの動作のままで、この単位では変えない（サーバーのテストは U3）。

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| 管理の画面すべて（入口・DSL・招待・利用者の管理）の 403 を、画面の骨組みの1か所で同じ表示にする | ADR-005、M5 B、差2 |
| 表示の文言は「この画面を使う権限がありません」とだけ書き、原因（管理者ではなくなった）は書かない。en は "You do not have permission to use this page." | 画面イメージ 9節の D1（RQ6 C） |
| 表示は Alert（info）と「ホームへ戻る」。出したら画面の見出しへフォーカスを移す | 画面イメージ S6、`interaction-spec.md` の AdminForbiddenNotice |
| 一覧・操作・入力を出さず、開いていた確かめ・入力の表示は閉じる | AC2.2.1、画面イメージ S6 |
| 403 を受けたらログインの状態を読み直し、サイドバーの管理のメニューを消す。時間の経過では判定しない | AC2.2.2、差2 |
| 判定の条件は、パスが `/api/admin/` で始まり、状態 403、code `ACCESS_DENIED` | C4 の `isAdminForbidden` |
| 403 は既存の `ACCESS_DENIED`（管理者でない）だけに使い、業務の拒否は 404・409 | Contract Design の共通の決まり（Q3 A） |
| 管理の API の外の画面は今までどおり使え、ログアウトされない | AC2.2.3 |
| 401 の扱い（更新と送り直し、ログインの画面への移動）は今の ApiClient のまま | C4 の behaviour、AC3.2.9 |
| 自分の氏名と言語だけを当てる口を足し、テーマと文字の大きさは変えない | C4 の `useApplyOwnProfile`、FR6.3 |
| 既存の3つの画面とそのテストの書き換えが範囲に入る。招待の画面は B2 の中で一度だけ直す | ADR-005、`bolt-plan.md` の B2 |
| 403 はテストで差し替えて作る（E2E には初期管理者しかいない） | `bolt-plan.md` の B2（Units Generation の R-02）、`frontend/e2e/030-admin-access.e2e.ts` の注記 |
| 画面の判定はサーバー側の判定の代わりにしない | `project.md` の Mandated、`team.md` の Testing Posture |
| 文言は日本語と英語の両方を用意し、既定は日本語 | `team.md` の Code Style |

---

## Q1. 管理の画面の 403 を知らせる仕組み

いま 401 は、ApiClient が `registerAuthHandlers` で登録された手段（`refresh`・`onUnauthenticated`）を呼び、画面の部品は何もしない形です。一方、C4 は「呼び出し元が API の失敗を `useAdminForbidden` に渡す」形を案にしています。なお、DSL と招待の画面のテストは API を差し替えた偽物（ApiClient を通らない）で 403 を作っているため、ApiClient が知らせる形では、そのテストだけでは S6 にならず、テストの作り方が変わります。また ApiClient の失敗（`ApiError`）は要求のパスを持たないため、呼び出し元が渡す形ではパス（各画面の API の根のパスの定数）も渡します。
理由: 403 の扱いの漏れにくさと、既存の画面・テストの書き換えの量が、どちらを選ぶかで変わるため。

A. 呼び出し元が渡す（C4 のとおり）。各画面の失敗の扱いの入口（DSL の `checkForbidden`、招待の失敗の扱い、入口の `statusFromError`）で `useAdminForbidden` に失敗とパスを渡し、true なら自分の誤りの表示をしない（推奨。承認済みの契約のままで、偽物の API を使う今のテストの形を保てる。DSL と招待は失敗の扱いがすでに1か所に集まっており、漏れは少ない）
B. ApiClient が知らせる。401 と同じく、`/api/admin/` の 403／`ACCESS_DENIED` を受けたら登録された手段（例: `onAdminForbidden`）を呼び、AppFrame が自動で切り替える。画面は 403 を自分で扱わない（後で足す管理の画面も何もしなくてよいが、偽物の API を使う画面のテストは S6 を確かめられず、ApiClient と AppFrame のテストで確かめる形になる）
C. A と B の両方。ApiClient が知らせて AppFrame が切り替え、画面は `isAdminForbidden` で自分の誤りの表示だけを飛ばす
X. Other (please specify)

[Answer]: A

## Q2. 管理者でない人が管理の画面の URL を開いたときの表示

今の振り分け（`decideRoute`）は、ログインしていて画面の側の印が管理者でない人が管理の画面を開くと、サーバーに問わずに「ページが見つかりません」を出します。403 の後にログインの状態を読み直して印が外れると、開いている画面がこの振り分けで「ページが見つかりません」に変わり、S6 が消えます（AC2.2.4 と食い違う）。印が外れた後に管理の画面を開き直したとき（AC2.2.5 の後）も同じです。
理由: 振り分けの決まり（骨組みのテストを含む）を変えるかどうかで、S6 を保つ仕組みの形が変わるため。

A. 振り分けを変える。ログインしていて管理者でない人が `access: 'ADMIN'` の画面を開いたら、「ページが見つかりません」ではなく S6 を出す（推奨。読み直しの後も、開き直しても同じ表示になり、S6 を保つ特別な仕組みが要らない。S6 の文言は原因を書かないため、印を持ったことの無い人にも誤りにならない。管理の画面の URL があることは分かるが、サーバーの 403 と同じ程度で、画面の一覧は配る JavaScript からも読める）
B. 振り分けは変えない。403 で S6 にした URL は、別の URL へ移るまで S6 を振り分けより優先して保つ。印が外れた後に開き直したときは「ページが見つかりません」になる（AC2.2.5 の後の開き直しは、画面の側の印が管理者のままの間だけ S6）
X. Other (please specify)

[Answer]: A

## Q3. ログインの状態の読み直しの方法

管理者の印は、トークンの応答（ログイン・更新）からだけ画面に入ります。骨組み（`src/app/`）は `features/auth` を直接読めず、差し込み口4（`LoginStateProvider`）には読み直しの口がありません。トークンの更新（`/api/auth/session/refresh`）は新しいトークンと今の印を返し、同時の更新は ApiClient の `refreshOnce` が1回にまとめます。印を外してもリフレッシュトークンは無効にならない（FR2.3）ため、更新は通常は成功し、ログアウトにはなりません（AC2.2.3）。
理由: 読み直しでサーバーに問うか、どの口から呼ぶかで、骨組みと auth の境界と、足す口が変わるため。

A. ApiClient に登録済みの更新の手段（`registerAuthHandlers` の `refresh`）を、`src/shared/api-client/` から呼べる関数（同時の更新は今の1回にまとめる仕組みに乗せる）として出し、`useAdminForbidden` がそれを呼ぶ。状態の変化は今の `subscribe` で `LoginStateGate` に届く（推奨。サーバーの今の印で決まり、差し込み口の型を変えずに済み、401 の更新と重なっても1回にまとまる）
B. 差し込み口4（`LoginStateProvider`）に任意の口 `reload` を足し、`features/auth` が実装する（中身はトークンの更新）。骨組みの型（`registry/types.ts`）とその検査が変わる
C. サーバーに問わず、画面の側の印だけを管理者でない扱いにする（差し込み口に印を下げる口を足す）。要求は増えないが、サーバーの値と食い違いうる
X. Other (please specify)

[Answer]: A

## Q4. 自分の氏名と言語だけを当てる口（useApplyOwnProfile）の作り

U5 は、管理者が自分の行の氏名・言語を変えたときにこの口を呼びます（FR6.3）。今の `applyUserPreferences` は氏名・言語・テーマ・文字の大きさの4つを受け取り、ブラウザに4つを保存し、ログインの状態に結び付けて当てます（プリファレンスの画面の保存の後と同じ）。`setLanguage` は保存しない見せ方で、氏名は変えません。
理由: 「テーマと文字の大きさは変えない」を、既存の口の組み合わせで満たすか、新しい口を足すかで、ブラウザの保存とテストが変わるため。

A. 表示の設定の店（`displaySettingsStore.ts`）に、氏名と言語だけを当てる関数を足す。テーマと文字の大きさは今の当てている値のままにし、ブラウザの保存は言語だけを書き換える（推奨。C4 の「氏名と言語だけ」をそのまま満たし、ほかのタブで変わったテーマ・文字の大きさを書き戻さない）
B. 今の `applyUserPreferences` に、今の画面のテーマと文字の大きさを合わせて渡す（新しい関数は足さない。ブラウザには4つを書く）
C. 当てる口を作らず、トークンの更新でログインの状態を読み直す（サーバーの値が当たる。要求が1回増え、新しいトークンが出る）
X. Other (please specify)

[Answer]: A

## Q5. S6 の見出し

画面イメージは「画面の見出しは残す」とし、表示を出したら見出しへフォーカスを移します。AppFrame がコンテンツの領域を置き換えると、各画面が描いている見出しも消えます。今の3つの画面の見出しは、サイドバーの項目の名前と同じ文言です。
理由: 見出しをだれが持つかで、AppFrame と各画面の受け渡し（引数）が変わるため。

A. AppFrame が、開いている URL に登録されたサイドバーの項目の名前を見出しに使う。項目が無い URL は共通の見出し（例: 「管理」）にする（推奨。各画面は何も渡さずに済み、後で足す利用者の管理の画面も登録だけで同じ形になる）
B. 各画面が `useAdminForbidden` に見出しの文言の鍵を渡し、AppFrame がそれを使う
C. 各画面が自分の見出しを描き続け、見出しの下の本文だけを `AdminForbiddenView` に置き換える（AppFrame は状態だけを持ち、描き分けは各画面が行う）
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめと、U4 の機能設計の計画:

- 設計の要点（案）の 1〜12 のとおりに作る。判定 `isAdminForbidden` は `src/shared/api-client/`、表示と口（`AdminForbiddenView`・`useAdminForbidden`・`useApplyOwnProfile`）は `src/app/` に置き、名前は C4 のままにする。
- Q1 A: 各画面の失敗の扱いの入口（入口の `statusFromError`、DSL の `checkForbidden`、招待の失敗の扱い）が `useAdminForbidden` に失敗とパスを渡す。true のときは自分の誤りの表示をしない。
- Q2 A: 振り分け（`decideRoute`）を変え、ログインしていて管理者でない人が管理の画面を開いたら、「ページが見つかりません」ではなく S6 を出す。C4 に無い変更のため、上流との差として記録する。
- Q3 A: 登録済みのトークンの更新を `src/shared/api-client/` から呼べる関数として出す。`useAdminForbidden` はそれでログインの状態を読み直し、管理のメニューは今の仕組みで消える。C4 に無い口のため、上流との差として記録する。
- Q4 A: `displaySettingsStore.ts` に、氏名と言語だけを当てる関数を足す。ブラウザの保存は言語だけを書き換える。
- Q5 A: S6 の見出しは、AppFrame が開いている URL のサイドバーの項目の名前を使う。項目が無い URL は共通の見出しにする。
- 書き換える既存のテストは5ファイル（`AdminAreaPage.test`・`adminAreaStatus.test`・`DslAdminPage.test`・`InvitationAdminPage.test`・invitation の `failureMessage.test`）。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
