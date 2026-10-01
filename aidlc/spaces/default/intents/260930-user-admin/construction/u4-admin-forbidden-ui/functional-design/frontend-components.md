# Frontend Components — U4 管理の画面の 403 の共通の扱い（u4-admin-forbidden-ui）

この文書は U4 の部品の階層、props と state、操作の流れ、API との受け渡しを書く。画面の流れと状態の移り変わり、決まり（D1〜D14）の正本は `functional-spec.md` である。コードの断片は形を示すための短いものに限る。

## 1. 部品の階層

```
main.tsx（BrowserRouter）
└─ App
   └─ ThemeProvider → ToastProvider → ModalStackProvider
      └─ LoginStateGate（ログイン状態。features/auth の提供元を subscribe）
         └─ DisplaySettingsProvider（表示の設定。applyOwnProfile を足す）
            └─ I18nProvider
               └─ FeatureRegistryProvider
                  └─ AdminForbiddenProvider（新しい。権限が無い URL を持つ）
                     └─ AppRouter（decideRoute に ADMIN_FORBIDDEN を足す）
                        ├─ SCREEN（SHELL）: ShellLayout
                        │    ├─ 権限が無い URL のとき: AdminForbiddenView（新しい）
                        │    └─ それ以外: 登録された画面
                        │         ├─ AdminAreaPage（features/admin）
                        │         ├─ DslAdminPage（features/dsl、useDslAdmin）
                        │         ├─ InvitationAdminPage（features/invitation、useInvitationAdmin）
                        │         └─ UserAdminPage（U5 が足す）
                        ├─ ADMIN_FORBIDDEN: ShellLayout → AdminForbiddenView
                        ├─ HOME・NOT_FOUND: ShellLayout → HomePage・NotFoundPage（今のまま）
                        └─ REDIRECT_TO_LOGIN・LOGIN_LAYOUT（今のまま）
```

文字の代替: `AdminForbiddenProvider` は `FeatureRegistryProvider` の内側、`AppRouter` の外側に置く（登録と今の URL を読むため。ルーターは `App` の外側にある）。ShellLayout は、今の URL が権限が無い URL のとき、子の画面の代わりに `AdminForbiddenView` を描く。振り分けが `ADMIN_FORBIDDEN` を返したときも、ShellLayout の中に `AdminForbiddenView` を描く。テストの補助 `renderWithProviders` も同じ位置に `AdminForbiddenProvider` を置く。

## 2. 置き場ごとのモジュール

| ファイル | 新しい・変える | 中身 |
|---|---|---|
| `frontend/src/shared/api-client/adminForbidden.ts` | 新しい | `isAdminForbidden`、定数 `ADMIN_API_PREFIX`（`/api/admin/`）・`ACCESS_DENIED`・`FORBIDDEN_STATUS`（403） |
| `frontend/src/shared/api-client/apiClient.ts` | 変える | `refreshSessionOnce` を足す（今の `refreshOnce` を外へ出す）。ほかは変えない |
| `frontend/src/app/admin-forbidden/AdminForbiddenProvider.tsx` | 新しい | 状態（権限が無い URL）、`useAdminForbidden`、ShellLayout が読む `useIsAdminForbiddenHere` |
| `frontend/src/app/admin-forbidden/AdminForbiddenView.tsx`（と `.css`） | 新しい | S6 の表示 |
| `frontend/src/app/admin-forbidden/forbiddenHeading.ts` | 新しい | 見出しの文言の鍵を決める純粋な関数 `forbiddenHeadingKey` |
| `frontend/src/app/routing/decideRoute.ts`・`AppRouter.tsx` | 変える | 判断に `ADMIN_FORBIDDEN` を足し、描き分ける |
| `frontend/src/app/layout/ShellLayout.tsx` | 変える | 権限が無い URL のとき子を `AdminForbiddenView` に置き換える |
| `frontend/src/app/display-settings/displaySettingsStore.ts` | 変える | `applyOwnProfileFor` を足す |
| `frontend/src/app/display-settings/DisplaySettingsProvider.tsx` | 変える | 値に `applyOwnProfile` を足す |
| `frontend/src/app/display-settings/useApplyOwnProfile.ts` | 新しい | 口 `useApplyOwnProfile` |
| `frontend/src/app/i18n/messages/ja.ts`・`en.ts` | 変える | `adminForbidden.message`・`adminForbidden.homeLink`・`adminForbidden.heading` |
| `frontend/src/app/App.tsx`・`app/testing/renderWithProviders.tsx` | 変える | `AdminForbiddenProvider` を置く |
| `frontend/src/features/admin/AdminAreaPage.tsx`・`adminAreaStatus.ts` | 変える | 403 の扱いの置き換え（6.2） |
| `frontend/src/features/dsl/useDslAdmin.ts`・`DslAdminPage.tsx` | 変える | 403 の扱いの置き換え（6.2） |
| `frontend/src/features/invitation/useInvitationAdmin.ts` | 変える | 403 の扱いの置き換え（6.2） |

- 新しいファイルの先頭には Apache License 2.0 のヘッダー（`/* ... */`、2026、agwlvssainokuni）を置く。エクスポートは名前付きだけにする（`team.md` の Code Style）。
- `src/app/admin-forbidden/` は `features/*` を読まない。`features/*` は `src/app/admin-forbidden/` と `src/app/display-settings/` の口だけを読む。

## 3. 口（契約 C4）

### 3.1 判定 `isAdminForbidden`（`src/shared/api-client/adminForbidden.ts`）

```text
export const ADMIN_API_PREFIX = '/api/admin/'
export const ACCESS_DENIED = 'ACCESS_DENIED'
/** 管理の API の「権限が無い」（D1）。副作用なし */
export function isAdminForbidden(path: string, error: unknown): boolean
// true の条件: path が ADMIN_API_PREFIX で始まる
//   かつ error が { kind: 'response', status: 403, code: 'ACCESS_DENIED' } の形
```

- `error` は `unknown` で受ける（`functional-spec.md` 10節の G4）。`kind`・`status`・`code` の型を確かめてから比べ、知らない値では例外を出さず false を返す。
- パスは呼び出し元の定数（各画面の API の根のパス）で、利用者の入力ではないため、正規化はしない。

### 3.2 読み直しの口 `refreshSessionOnce`（`src/shared/api-client/apiClient.ts`）

```text
/** 登録済みのトークンの更新を1回呼ぶ。同時の更新（401 の更新を含む）は1回にまとめる */
export function refreshSessionOnce(): Promise<boolean>
```

- 中身は今の `refreshOnce` と同じ（登録された手段が無ければ false を返し、何もしない）。401 の扱い（`apiFetch`）は変えない。
- 結果が false のときに `onUnauthenticated` を呼ぶかは ApiClient では決めない。今の `features/auth` の `refresh` は失敗するとログインの状態を自分で消すため、読み直しの失敗は D9 のとおりログアウトになる。

### 3.3 失敗を渡す口 `useAdminForbidden`（`src/app/admin-forbidden/AdminForbiddenProvider.tsx`）

```text
/** 管理の画面が API の失敗を渡す口（D2）。権限が無いなら S6 に切り替えて true */
export function useAdminForbidden(): (error: unknown, apiPath: string) => boolean
```

- 返す関数は、その部品が描かれた時点の URL のパス（`useLocation`）を覚える。呼ばれたら次を行う。
  1. `isAdminForbidden(apiPath, error)` が false なら、何もせず false を返す。
  2. true なら、覚えた URL を Provider に知らせ、true を返す。
- Provider は知らせを受けたら、知らせの URL が今の URL と同じときだけ、権限が無い URL として覚える（D4）。権限が無い URL を新しく覚えたとき（または URL が今と違って覚えなかったとき）は `refreshSessionOnce` を1回呼ぶ。今覚えている URL と同じ知らせが重ねて届いたときは呼ばない（D7）。読み直しの結果は待たない。
- Provider の外で呼ぶと例外にする（`functional-spec.md` 6節）。
- 返す関数は、描画のたびに作り直さない（`useCallback` で、URL が変わったときだけ作り直す）。各画面の `useCallback` の依存に入れても、読み込みが繰り返されない。

### 3.4 表示 `AdminForbiddenView`（`src/app/admin-forbidden/AdminForbiddenView.tsx`）

- Props は持たない。見出しは今の URL と登録から `forbiddenHeadingKey` で決める（D11）。
- 描くもの: `section`（`aria-labelledby` で見出しを指す、`data-testid="admin-forbidden-view"`）の中に、`h1`（`tabIndex={-1}`）と、make-you-chic-ui の `Alert`（`variant="info"`、`role="status"` で読み上げられる）。Alert の本文に文言 `adminForbidden.message` と、ホームの URL へのリンク（react-router の `Link`、文言 `adminForbidden.homeLink`）を置く。
- 描いた直後（`useEffect`）に見出しへフォーカスを移す（D12）。振り分けの判断が変わって描き直されたとき（4節の ForbiddenByApi → ForbiddenByRoute）も、同じ見出しへ移る。
- 「ホームへ戻る」を Alert の `action`（ボタン）ではなくリンクにするのは、移る先の URL を持つ操作のため（今の `NotFoundPage` の「ホームへ」と同じ作り）。

### 3.5 見出しの関数 `forbiddenHeadingKey`（`src/app/admin-forbidden/forbiddenHeading.ts`）

```text
/** 今の URL に登録されたサイドバーの項目の文言の鍵。無ければ共通の見出しの鍵（D11） */
export function forbiddenHeadingKey(
  pathname: string, registrations: readonly FeatureRegistration[],
): string
```

- 全機能の `sidebarItems` から、`path` が今の URL に合うものを探す（`visibleWhen` で絞らない）。合うものが無ければ `adminForbidden.heading` を返す。

### 3.6 自分の氏名と言語の口 `useApplyOwnProfile`（`src/app/display-settings/useApplyOwnProfile.ts`）

```text
export function useApplyOwnProfile(): (profile: { displayName: string; language: 'ja' | 'en' }) => void
```

- `useDisplaySettings()` の新しい項目 `applyOwnProfile` を返す。ログインしていないときは何もしない関数になる（D13）。
- `applyOwnProfile` は、`displaySettingsStore.ts` の `applyOwnProfileFor(binding, profile, current)` を呼ぶ。`binding` は今のログイン状態、`current` はそのとき当てている値（見せ方を除く）のテーマと文字の大きさ。
- `applyOwnProfileFor` は次を行う（Q4 A、D13・D14）。
  - 保存の後の利用者の設定（`savedUser`）を、`binding` に結び付けた「氏名＝渡した氏名・言語＝渡した言語・テーマと文字の大きさ＝`current` の値」にする。
  - ブラウザの保存は言語だけを書き換える（今の `writeStoredLanguage`）。テーマと文字の大きさの保存の値は触らない。
  - 見せ方に言語が残っていれば、言語の分だけ捨てる。テーマと文字の大きさの見せ方は残す。
  - 言語が ja・en でなければ、言語は変えず氏名だけを当てる。
- 今の `applyUserPreferences`（プリファレンスの保存の後、4つの値を当てて保存する）と `setLanguage`（保存しない言語の見せ方）は変えない。

### 3.7 使う単位ごとの約束

| 使う側 | 口 | 約束 |
|---|---|---|
| U5 の利用者の管理の画面 | `useAdminForbidden` | 一覧の読み込み・行の操作・氏名と言語の保存の、すべての失敗の扱いの入口で、失敗と `/api/admin/users`（U5 の API の根のパスの定数）を渡す。true なら自分の誤りの表示も、一覧の読み直しもしない |
| U5 の利用者の管理の画面 | `useApplyOwnProfile` | 氏名・言語の保存が成功し、変えた行が自分自身のときだけ、保存した氏名と言語を渡す |
| U5 の登録 | サイドバーの項目 | 画面の見出しとサイドバーの項目の名前を同じ文言（「利用者の管理」）にする（S6 の見出しになるため、D11） |
| 既存の3つの画面 | `useAdminForbidden` | 6.2 のとおり |

## 4. 部品ごとの props と state

| 部品・モジュール | props | state・持つ値 | 備考 |
|---|---|---|---|
| `AdminForbiddenProvider` | `children` | 権限が無い URL（パスの文字列か無し） | 今の URL が変わったら捨てる（D5）。値は `{ forbiddenPath, report }` を context で渡す |
| `AdminForbiddenView` | なし | なし（見出しへの ref だけ） | `useLocation`・`useFeatureRegistry`・`useMessages` を読む |
| `ShellLayout` | `children`（今のまま） | なし | 今の URL が権限が無い URL のとき、`children` の代わりに `AdminForbiddenView` を描く。サイドバー・上の帯・ユーザーメニューは今のまま |
| `AppRouter` | なし | なし | `ADMIN_FORBIDDEN` のとき `ShellLayout` の中に `AdminForbiddenView` |
| `decideRoute` | — | — | `RouteDecision` に `{ kind: 'ADMIN_FORBIDDEN' }` を足す。`access: 'ADMIN'` の画面で、ログインしていて管理者でないときに返す（今の `NOT_FOUND` の代わり）。ログインしていないときは今までどおりログインの画面へ |
| `DisplaySettingsProvider` | 今のまま | 今のまま | 値に `applyOwnProfile` を足す |
| `AdminAreaPage` | 今のまま | 状態 `AdminAreaStatus` に `Forbidden` を足し、`NotFound` をなくす | `Forbidden` のときは何も描かない（ShellLayout が S6 に置き換えているため） |
| `useDslAdmin`・`DslAdminPage` | 今のまま | `forbidden` の状態をなくす | `DslAdminPage` の「ページが見つかりません」の分かれ道をなくす |
| `useInvitationAdmin` | 今のまま | 今のまま | 4つの失敗の扱いに渡す1行を足す |

```text
// ShellLayout の置き換えの形（説明用）
const forbiddenHere = useIsAdminForbiddenHere()
return (
  <AppShell navItems={navItems} user={user} userMenuItems={userMenuItems}>
    {forbiddenHere ? <AdminForbiddenView /> : children}
  </AppShell>
)
```

## 5. 操作の流れ

### 5.1 管理の画面の操作で 403（DSL の確かめの表示の中で「適用」を押した例）

1. 利用者が「適用」を押す。`useDslAdmin` が API を呼ぶ。
2. 403（`ACCESS_DENIED`）が返り、失敗の扱いの入口（今の `checkForbidden`）が `report(error, DSL_API_ROOT)` を呼ぶ。
3. `report` が true を返す。`useDslAdmin` は Alert の文言も、状態・履歴の読み直しも行わずに終える。
4. Provider が権限が無い URL を `/admin/dsl` にする。ShellLayout が描き直され、`DslAdminPage` が外れて、確かめの表示（Modal）も閉じる。`AdminForbiddenView` が描かれ、見出し「DSL」へフォーカスが移る。
5. Provider が `refreshSessionOnce` を1回呼ぶ。応答の印が管理者でなければ、サイドバーの「管理」「DSL」「利用者の招待」が消え、振り分けは `ADMIN_FORBIDDEN` を返して S6 のまま続く。
6. 「ホームへ戻る」でホームへ移る。権限が無い URL は捨てられる。

### 5.2 画面を開いたときの並べた読み込みで 403 を重ねて受ける

- DSL の管理は、開いたときに状態・プレビュー・履歴を並べて読む。3つとも 403 のとき、最初の `report` で S6 になり読み直しを1回呼ぶ。2つ目と3つ目は同じ URL のため、`report` は true を返すが読み直しを重ねない（D7）。外れた後の結果を捨てる今の仕組み（`mounted`・`isLatest`）で、画面は何もしない。

### 5.3 自分の氏名と言語の反映（U5 が呼ぶ）

1. U5 が自分の行の氏名・言語の保存の成功を受け、`applyOwnProfile({ displayName, language })` を呼ぶ。
2. `savedUser` が新しくなり、上の帯の氏名と画面の言語が同じ描画で変わる。`<html lang>` と要求の言語は今の仕組み（言語が変わったときの `setAppliedLanguage`）で切り替わる。
3. テーマと文字の大きさは変わらない。

## 6. API との受け渡し

### 6.1 ApiClient の変更

- 足すのは `refreshSessionOnce` の1つだけ。要求の送り方・トークンの付与・401 での更新と送り直し・`Accept-Language` の付与・トークンを付けないパスの一覧は変えない（D10）。
- 「権限が無い」の判定は ApiClient の要求の流れには入れず、`isAdminForbidden` として別のファイルに置く（Q1 A。ApiClient は 403 を知らせない）。
- 管理の API の失敗（`ApiError`）は要求のパスを持たないため、パスは呼び出し元が渡す。

### 6.2 既存の3つの画面の置き換え

| 画面 | 失敗の扱いの入口 | 渡すパス | 置き換えの中身 |
|---|---|---|---|
| 管理の入口 | `AdminAreaPage` の確かめの失敗の受け取り（`statusFromError`） | `ADMIN_CHECK_PATH`（`/api/admin/check`） | `statusFromError` は渡す関数を引数に受け、true なら `Forbidden`、false なら `Error` を返す（下の断片）。403 を code によらず `NotFound` にする扱いはなくす |
| DSL の管理 | `useDslAdmin` の `checkForbidden`（読み込みの3つと `handleFailure` から呼ばれる） | `DSL_API_ROOT`（`/api/admin/dsl`） | `checkForbidden` を「`report(error, DSL_API_ROOT)` を返す」形にする。状態 403 だけで判定する定数 `FORBIDDEN` と `forbidden` の状態はなくす |
| 招待の管理 | `useInvitationAdmin` の一覧の読み込み・招待・送り直し・取り消しの失敗の受け取り（今 `isUnauthorized` を確かめている4か所） | `INVITATION_API_ROOT`（`/api/admin/invitations`） | `isUnauthorized(error)` の確かめと並べて、`report(error, INVITATION_API_ROOT)` が true なら何もせず終える |

```text
// 管理の入口の形（説明用）
export function statusFromError(
  error: unknown,
  report: (error: unknown, apiPath: string) => boolean,
): AdminAreaStatus {
  return report(error, ADMIN_CHECK_PATH) ? 'Forbidden' : 'Error'
}
```

- 招待の `failureMessageKey` は変えない。`ACCESS_DENIED` の 403 はもう `failureMessageKey` に届かないため、今の単体テストの「403 を一般の文言にする」確かめは、code の無い・違う 403（`report` が false を返し、一般の文言になるもの）の確かめに書き直す。

## 7. テストで確かめる内容

テストの説明文は英語で書く。403 は偽物の API・偽物の `fetch` で作る。画面のテストは `renderWithProviders` で、画面を `ShellLayout` の中に描いて S6 を確かめる。

| テストのファイル | 新しい・書き換え | 確かめる内容 |
|---|---|---|
| `shared/api-client/adminForbidden.test.ts` | 新しい | 3つの条件がそろうときだけ true（`/api/admin/` の下・403・`ACCESS_DENIED`）。`/api/me/…` の 403、`/api/administrator` のような接頭辞だけ似たパス、code の無い・違う 403、401・404・409・500、通信の失敗、`null`・文字列などの知らない値は false で例外を出さない。性質ベースのテスト（fast-check、種を記録）でパス・状態・code の組を網羅する |
| `shared/api-client/apiClient.test.ts` | 足す | `refreshSessionOnce` が登録された更新を呼ぶ。401 の更新と同時に呼んでも更新は1回。登録が無ければ false |
| `app/admin-forbidden/AdminForbiddenProvider.test.tsx` | 新しい | `report` が true のとき今の URL が S6 になり、更新が1回呼ばれる。同じ URL で重ねても更新は1回。false の失敗では何も変わらない。渡した部品の URL と今の URL が違うと S6 にならず、更新は呼ばれる。URL が変わると S6 が消える。Provider の外で呼ぶと例外 |
| `app/admin-forbidden/AdminForbiddenView.test.tsx` | 新しい | ja・en の文言。見出しへフォーカスが移る（`waitFor`）。「ホームへ戻る」でホームへ移る。アクセシビリティの検査（vitest-axe）1件 |
| `app/admin-forbidden/forbiddenHeading.test.ts` | 新しい | 登録された項目の鍵を返す（`visibleWhen: 'ADMIN'` の項目も、管理者でないログイン状態で探せる）。無ければ共通の鍵。性質ベースのテスト（登録に無い URL はいつも共通の鍵） |
| `app/layout/ShellLayout.test.tsx` | 足す | 権限が無い URL のとき子の代わりに S6。サイドバーと上の帯は残る。読み直しで印が外れると管理のメニューが消える（`waitFor`） |
| `app/routing/decideRoute.test.ts`・`AppRouter.test.tsx` | 書き換え | ログインしていて管理者でない利用者の `ADMIN` の画面は `ADMIN_FORBIDDEN`（S6）。ログインしていなければログインの画面へ。登録に無い URL は今までどおり `NOT_FOUND` |
| `app/display-settings/displaySettingsStore.test.ts`・`DisplaySettingsProvider.test.tsx` | 足す | `applyOwnProfile` で氏名と言語が変わり、テーマと文字の大きさ（ほかのタブで変わった保存の値を含む）が変わらない。ブラウザの保存は言語だけが変わる。`Accept-Language` が新しい言語になる。ログインしていないときは何もしない。ja・en 以外の言語では言語を変えない。次のログイン状態でサーバーの値に戻る |
| `features/admin/AdminAreaPage.test.tsx` | 書き換え（2件） | 確かめの API の 403・`ACCESS_DENIED` で S6（「ページが見つかりません」ではない）。表示のたびに確かめをやり直すテストも S6 で確かめる |
| `features/admin/adminAreaStatus.test.ts` | 書き換え（4件） | `report` が true なら `Forbidden`、false なら `Error`。性質ベースのテストは「`ACCESS_DENIED` 以外の 403 も `Error`」に直す |
| `features/dsl/DslAdminPage.test.tsx` | 書き換え（1件）・足す | 状態の読み込みの 403 で S6。確かめの表示を開いた状態で操作が 403 になると、確かめの表示が閉じて S6 になる。code の無い 403 は今の誤りの扱い |
| `features/invitation/InvitationAdminPage.test.tsx` | 書き換え（3か所） | 一覧・入力の表示・送り直しの 403・`ACCESS_DENIED` で S6（一般の文言ではない）。入力の表示は閉じる |
| `features/invitation/failureMessage.test.ts` | 書き換え（1件） | code の無い・違う 403 は一般の 4xx の文言（6.2） |

- E2E（`frontend/e2e/030-admin-access.e2e.ts`）は変えない（403 を確かめていない。E2E には初期管理者しかいない）。
- 今の画面のテストのうち `renderWithProviders` を使うものは、`AdminForbiddenProvider` が並びに入っても通り続けることを確かめる。

## 8. 既存への影響

- 管理者でない利用者が管理の画面の URL を開いたときの表示が、「ページが見つかりません」から S6 に変わる（D6、Q2 A）。管理の画面の URL があることは分かるが、サーバーの 403 と同じ程度で、画面の一覧は配る JavaScript からも読める。
- 管理の入口の 403 は、code が `ACCESS_DENIED` でなければ「ページが見つかりません」ではなく一般の誤りの文言になる（G9）。DSL の管理も、code の無い 403 は今の一般の誤りの扱いになる。
- 招待の管理の 403 は、一般の文言から S6 に変わり、入力の表示も閉じる。
- `NotFoundPage` は、登録に無い URL のためにそのまま残る。管理の入口と DSL の管理からは使わなくなる。
- 401 の扱い、ログイン・ログアウト、表示の設定のほかの口（`applyUserPreferences`・`setLanguage`・`setPreview`）は変わらない。
