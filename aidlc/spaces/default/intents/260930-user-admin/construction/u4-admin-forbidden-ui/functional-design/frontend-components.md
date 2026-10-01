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
                        ├─ SCREEN（SHELL）: Routes → Route（登録の path）→ ShellLayout
                        │    ├─ 権限が無い URL のとき: AdminForbiddenView（新しい）
                        │    └─ それ以外: 登録された画面
                        │         ├─ AdminAreaPage（features/admin）
                        │         ├─ DslAdminPage（features/dsl、useDslAdmin）
                        │         ├─ InvitationAdminPage（features/invitation、useInvitationAdmin）
                        │         └─ UserAdminPage（U5 が足す）
                        ├─ ADMIN_FORBIDDEN: Routes → Route（登録の path）→ ShellLayout → AdminForbiddenView
                        ├─ HOME・NOT_FOUND: ShellLayout → HomePage・NotFoundPage（今のまま）
                        └─ REDIRECT_TO_LOGIN・LOGIN_LAYOUT（今のまま）
```

文字の代替: `AdminForbiddenProvider` は `FeatureRegistryProvider` の内側、`AppRouter` の外側に置く（登録と今の URL を読むため。ルーターは `App` の外側にある）。ShellLayout は、今の URL が権限が無い URL のとき、子の画面の代わりに `AdminForbiddenView` を描く。振り分けが `ADMIN_FORBIDDEN` を返したときは、SCREEN（SHELL）と同じ形の木（`Routes` → 登録の path の `Route` → `ShellLayout`）の中に `AdminForbiddenView` を描く。木の形と、ShellLayout の子の位置にある部品の種類（`AdminForbiddenView`）が同じため、ForbiddenByApi から ForbiddenByRoute へ移っても ShellLayout・AppShell・AdminForbiddenView は作り直されない（`functional-spec.md` 4節、R-05）。テストの補助 `renderWithProviders` も同じ位置に `AdminForbiddenProvider` を置く。

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
| `frontend/src/features/dsl/testing/renderDsl.tsx`・`features/invitation/testing/renderInvitation.tsx` | 変える（テストの補助） | 画面を ShellLayout の中に描く選択（例: 引数 `options.withShell`、既定は今のまま描く）と、ログイン状態の提供元（`renderWithProviders` の `provider`）を渡す口を足す（7節） |
| `frontend/src/features/admin/AdminAreaPage.test.tsx` の描き方（`render`） | 変える（テスト） | 画面を ShellLayout の中に描き、管理者のログイン状態の提供元を渡す（7節） |

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

- 返す関数は、その部品が描かれた時点の URL のパス（`useLocation`、以下「画面の URL」）を持つ。呼ばれたら次を行う。
  1. `isAdminForbidden(apiPath, error)` が false なら、何もせず false を返す。
  2. true なら、画面の URL を Provider の `report` に知らせ、true を返す。
- 画面の URL は描画の時点の値でよい（画面が外れた後に届いた 403 では、外れる前の URL が正しい値である）。比べる相手の「今の URL」は、Provider が ref で持つ最新の値を `report` の中で読む（D4、R-02）。

Provider の仕組み（R-02）:

| 項目 | 決まり |
|---|---|
| 状態 | 権限が無い URL（`forbidden`、パスか無し）を `useState` で持つ。同じ値を ref（`forbiddenRef`）にも写し、`report` の中の判定に使う |
| 捨て方 | 前の描画で見たパス（`seenPath`、`useState`）と今のパスが違えば、その描画の中で `forbidden` を無しにする（React の「前の描画の値と比べて状態を直す」形）。`useEffect` で捨てる形は取らない（古い S6 が一度描かれ、A → B → A で残りうるため） |
| 出すかの判定 | ShellLayout が読む値は「`forbidden` が今のパスと同じときだけそのパス、ほかは無し」として描画の中で求める。捨て方と二重に守る |
| 今の URL | `currentPathRef` に入れ、`useLayoutEffect` でパスが変わるたびに新しくする。`forbiddenRef` も同じ時点で、パスが違えば無しにする |
| `report` | 依存を持たない `useCallback` で作り、Provider が生きている間は同じ関数のまま。中で `currentPathRef`・`forbiddenRef`・`inFlightRef` だけを読む |
| 知らせの扱い | 画面の URL が `currentPathRef` と同じなら: `forbiddenRef` が既にその URL なら何もしない（重なった 403）。違えば `forbidden` をその URL にし、読み直しを呼ぶ。画面の URL が今と違えば（W5）: 覚えずに、読み直しを呼ぶ |
| 読み直しの回数 | `inFlightRef` に骨組みが呼んだ読み直しの約束を持ち、終わるまでは新しく呼ばない。終われば（成功・失敗とも）無しに戻す。結果は待たない |
| context | 状態用（ShellLayout が読む「ここが権限が無い URL か」）と関数用（`report`）の2つに分ける。状態が変わっても `report` の context の値は変わらず、`useAdminForbidden` が返す関数も変わらない |

```text
// AdminForbiddenProvider の中（説明用）
const { pathname } = useLocation()
const [forbidden, setForbidden] = useState<string | null>(null)
const [seenPath, setSeenPath] = useState(pathname)
if (seenPath !== pathname) { setSeenPath(pathname); setForbidden(null) } // 描画の中で捨てる
useLayoutEffect(() => { currentPathRef.current = pathname; if (forbiddenRef.current !== pathname) forbiddenRef.current = null }, [pathname])
const report = useCallback((screenPath: string) => {
  const here = screenPath === currentPathRef.current
  if (here && forbiddenRef.current === screenPath) return // 重なった 403
  if (here) { forbiddenRef.current = screenPath; setForbidden(screenPath) }
  if (inFlightRef.current === null) {
    inFlightRef.current = refreshSessionOnce().finally(() => { inFlightRef.current = null })
  }
}, [])
const forbiddenHere = forbidden === pathname
```

- Provider の外で呼ぶと例外にする（`functional-spec.md` 6節）。
- `useAdminForbidden` が返す関数は `useCallback` で、依存は `report`（いつも同じ）と画面の URL だけとする。画面の URL が変わらない限り同じ関数のため、各画面の `useCallback`・`useEffect` の依存に入れても読み込みが繰り返されない。

### 3.4 表示 `AdminForbiddenView`（`src/app/admin-forbidden/AdminForbiddenView.tsx`）

- Props は持たない。見出しは今の URL と登録から `forbiddenHeadingKey` で決める（D11）。
- 描くもの: `section`（`aria-labelledby` で見出しを指す、`data-testid="admin-forbidden-view"`）の中に、`h1`（`tabIndex={-1}`）と、make-you-chic-ui の `Alert`（`variant="info"`、`role="status"` で読み上げられる）。Alert の本文に文言 `adminForbidden.message` と、ホームの URL へのリンク（react-router の `Link`、文言 `adminForbidden.homeLink`）を置く。
- Alert の `role`: make-you-chic-ui の `Alert.tsx`（`vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Alert/Alert.tsx`）は、`variant` が `danger`・`warning` のとき `role="alert"`、それ以外（`info` を含む）のとき `role="status"` を付ける。この段でソースを読んで確かめた（R-05）。テストでも S6 の Alert が `role="status"` であることを確かめる（7節）。読み上げは、見出しへのフォーカスで見出しが読まれ、Alert は穏やかな知らせ（status）として後に続く。Alert を `alert` にして割り込ませる形は取らない。
- 描いた直後（`useEffect`、部品が作られたときだけ）に見出しへフォーカスを移す（D12）。ForbiddenByApi から ForbiddenByRoute へ移るときは、振り分けが同じ形の木を描くため部品は作り直されず、フォーカスの移し直しは起きない（1節、R-05）。
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
- `current` の取り方（R-01）: `DisplaySettingsProvider` の中で、画面の値を決めるのと同じ入力に `preview: null` を渡して `decideScreenSettings` を呼び、その結果の `theme`・`fontSize` を使う。土台はログインの後は `savedUser`（今のログイン状態に結び付いたもの）か `userPreferences` で、`stored` ではない（`chooseBase` のとおり。無い軸は既定の値）。Provider の値の `theme`・`fontSize`（見せ方を含む、画面に今見えている値）は使わない。

```text
// DisplaySettingsProvider の中（説明用）
const applied = decideScreenSettings({ ...input, preview: null, prefersDark: false })
const applyOwnProfile = (profile: OwnProfile) => {
  if (!loginState.loggedIn) return
  applyOwnProfileFor(loginState, profile, { theme: applied.theme, fontSize: applied.fontSize })
}
```

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
| `AdminForbiddenProvider` | `children` | 権限が無い URL（パスの文字列か無し）、前の描画で見たパス、ref（今の URL・権限が無い URL・読み直しの約束） | 今の URL が変わったら、その描画の中で捨てる（D5）。状態用の context（ここが権限が無い URL か）と関数用の context（`report`、いつも同じ）を分けて渡す（3.3） |
| `AdminForbiddenView` | なし | なし（見出しへの ref だけ） | `useLocation`・`useFeatureRegistry`・`useMessages` を読む |
| `ShellLayout` | `children`（今のまま） | なし | 今の URL が権限が無い URL のとき、`children` の代わりに `AdminForbiddenView` を描く。サイドバー・上の帯・ユーザーメニューは今のまま |
| `AppRouter` | なし | なし | `ADMIN_FORBIDDEN` のとき、SCREEN（SHELL）と同じ形（`Routes` → `Route path={route.path}` → `ShellLayout`）の中に `AdminForbiddenView` を描く。ShellLayout を作り直さないため（1節、R-05）。今の管理の画面はすべて SHELL のため、STANDALONE の管理の画面は考えない |
| `decideRoute` | — | — | `RouteDecision` に `{ kind: 'ADMIN_FORBIDDEN', route }`（合った登録の画面）を足す。`route` は AppRouter が同じ形の木を描くために使う。`access: 'ADMIN'` の画面で、ログインしていて管理者でないときに返す（今の `NOT_FOUND` の代わり）。ログインしていないときは今までどおりログインの画面へ |
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

テストの説明文は英語で書く。403 は、DSL の管理・招待の管理では画面に渡す偽物の API（ApiClient を通らない）、管理の入口では偽物の `fetch`（ApiClient を通る）で作る（`functional-spec.md` 8節）。画面のテストは `renderWithProviders`（DSL・招待は描画の補助 `renderDsl`・`renderInvitation` を通して）で、画面を `ShellLayout` の中に描いて S6 を確かめる。

| テストのファイル | 新しい・書き換え | 確かめる内容 |
|---|---|---|
| `shared/api-client/adminForbidden.test.ts` | 新しい | 3つの条件がそろうときだけ true（`/api/admin/` の下・403・`ACCESS_DENIED`）。`/api/me/…` の 403、`/api/administrator` のような接頭辞だけ似たパス、code の無い・違う 403、401・404・409・500、通信の失敗、`null`・文字列などの知らない値は false で例外を出さない。性質ベースのテスト（fast-check、種を記録）でパス・状態・code の組を網羅する |
| `shared/api-client/apiClient.test.ts` | 足す | `refreshSessionOnce` が登録された更新を呼ぶ。401 の更新と同時に呼んでも更新は1回。登録が無ければ false |
| `app/admin-forbidden/AdminForbiddenProvider.test.tsx` | 新しい | `report` が true のとき今の URL が S6 になり、更新が1回呼ばれる。同じ URL で重ねても更新は1回。false の失敗では何も変わらない。URL が変わると S6 が消える。Provider の外で呼ぶと例外。あわせて R-02 の3点を確かめる: (1) A で S6 → B へ移る → ブラウザの戻る（`MemoryRouter` の `navigate(-1)`）で A に戻ると、A は S6 ではなく画面の中身になる。B へ移った直後の最初の描画でも S6 が描かれない (2) 画面が外れた後（A から B へ移った後）に、A で作った関数へ 403 を渡すと、B は S6 にならず、更新は呼ばれる (3) 権限が無い URL を覚える前と後で、`report` と `useAdminForbidden` が返す関数が同じもの（同一）であり、それを依存に入れた `useEffect` が繰り返されない。読み直しが終わる前に結び付かない 403 が重なっても、更新は1回 |
| `app/admin-forbidden/AdminForbiddenView.test.tsx` | 新しい | ja・en の文言。Alert が `role="status"` である（R-05）。見出しへフォーカスが移る（`waitFor`）。「ホームへ戻る」でホームへ移る。アクセシビリティの検査（vitest-axe）1件 |
| `app/admin-forbidden/forbiddenHeading.test.ts` | 新しい | 登録された項目の鍵を返す（`visibleWhen: 'ADMIN'` の項目も、管理者でないログイン状態で探せる）。無ければ共通の鍵。性質ベースのテスト（登録に無い URL はいつも共通の鍵） |
| `app/layout/ShellLayout.test.tsx` | 足す | 権限が無い URL のとき子の代わりに S6。サイドバーと上の帯は残る。AC2.2.2 のきっかけごとに、ログイン状態の提供元が管理者でない新しい状態を知らせると管理のメニューが消える（`waitFor`）: 403 の後の読み直し（偽物の更新）、トークンの更新の応答、ログインし直しの応答（R-06） |
| `app/routing/decideRoute.test.ts`・`AppRouter.test.tsx` | 書き換え・足す | ログインしていて管理者でない利用者の `ADMIN` の画面は `ADMIN_FORBIDDEN`（S6）。ログインしていなければログインの画面へ。登録に無い URL は今までどおり `NOT_FOUND`。画面の読み直し（起動の時の更新で印が外れた状態から描き始める）で、管理のメニューが無く管理の画面が S6 になる（AC2.2.2、R-06）。ForbiddenByApi（画面が 403 を受けた）から ForbiddenByRoute（読み直しで印が外れた）へ移っても、S6 の見出しの要素が同じもの（作り直されていない）で、フォーカスが見出しに残り、サイドバーの開閉の状態が変わらない（R-05） |
| `app/display-settings/displaySettingsStore.test.ts`・`DisplaySettingsProvider.test.tsx` | 足す | `applyOwnProfile` で氏名と言語が変わり、テーマと文字の大きさ（ほかのタブで変わった保存の値を含む）が変わらない。テーマを見せ方で試しに当てた状態（`setPreview`）で `applyOwnProfile` を呼ぶと、`savedUser` のテーマは見せ方の前の当てている値のままで、見せ方は残る。その後に見せ方を捨てると、画面のテーマが当てている値に戻る（試しのテーマが保存されない、R-01）。ブラウザの保存は言語だけが変わる。`Accept-Language` が新しい言語になる。ログインしていないときは何もしない。ja・en 以外の言語では言語を変えない。次のログイン状態でサーバーの値に戻る |
| `features/admin/AdminAreaPage.test.tsx` | 書き換え（描き方と2件） | テストの中の `render` を、画面を ShellLayout の中に描き、管理者のログイン状態の提供元（`provider`）を渡す形に直す（R-03）。403 は今までどおり偽物の `fetch` で ApiClient を通して作る。確かめの API の 403・`ACCESS_DENIED` で S6（「ページが見つかりません」ではない）。表示のたびに確かめをやり直すテストも S6 で確かめる |
| `features/dsl/testing/renderDsl.tsx`・`features/invitation/testing/renderInvitation.tsx` | 書き換え（テストの補助） | ShellLayout の中に描く選択と、ログイン状態の提供元を渡す口を足す（R-03）。既定は今のまま（ShellLayout なし）とし、部品だけを描く今の多くのテスト（`DslPreviewPanel.test` など）は変えない。画面のテスト（`DslAdminPage.test`・`InvitationAdminPage.test`）だけが ShellLayout の中に描く。403 は今までどおり画面に渡す偽物の API（ApiClient を通らない）で作る |
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
