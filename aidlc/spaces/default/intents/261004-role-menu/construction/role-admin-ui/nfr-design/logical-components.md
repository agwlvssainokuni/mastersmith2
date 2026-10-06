# 論理の部品 — U6 role-admin-ui

## 出典

- この単位の承認済みの NFR 要件 `construction/role-admin-ui/nfr-requirements/` の `performance-requirements.md`（NFR2.2・NFR2.4・NFR2.5〜NFR2.13）・`security-requirements.md`（NFR1.1・NFR1.4〜NFR1.12）・`tech-stack-decisions.md`（NFR4.1〜NFR4.7・NFR6.1〜NFR6.10、9節の承認の場の直し）。ui の単位は scalability・reliability・observability の要件を作らない（NFR 要件の段の `produces_kinds`）ため、段の定義が必須とするそれらの入力は無く、上の3つを入力にする。
- 承認済みの機能設計 `construction/role-admin-ui/functional-design/functional-spec.md`（D1〜D32・W3.1〜W9.1・7節・8節）と `frontend-components.md`。
- 契約 `inception/contract-design/contract-summary.md`（C2・C6・C7）と部品の一覧 `inception/domain-design/components.md`（RoleAdminUi・GroupAdminUi・RoleTransferUi・UserAdminUi・SharedTreeView・ApiClient）。
- 統合の点（読み取りだけ）: `construction/role/nfr-design/`（`RoleTransferSlot` の `ROLE_BUSY`）。
- この段の答え: `nfr-design-questions.md` の Q1 A、まとめの確認は Looks correct（「決まっていること」「設計の要点」を含む）。
- NFR 要件の読み直しの記録で直していない指摘 R-02〜R-08（この段で設計として手当てする）。

## 1. 部品の一覧

| ID | 部品（置き場） | 受け持ち | 受け持つ NFR |
|---|---|---|---|
| L1 | 登録（`features/roleadmin`・`groupadmin`・`roletransfer` の `registration.ts`、`useradmin` の行の操作の追加） | 道・サイドバーの項目（`section: 'ADMIN'`・`visibleWhen: 'ADMIN'`）・遅延読み込み（`lazy`）。隠すことは見せ方だけ | NFR1.1・NFR2.13 |
| L2 | API の関数（各機能の `api/*Api.ts`・`api/types.ts`、`useradmin` の `getUserRoles`） | 道の組み立て（ID は `encodeURIComponent`、権限の木は問い合わせの引数）、本文は決めた項目だけ、応答は型の項目だけを写す。形の誤りは通信の失敗 | NFR1.4・NFR1.9・NFR2.2 |
| L3 | 失敗の文言（各機能の `failureMessage.ts`、`roletransfer` の `reasonMessage.ts`） | `code` と状態だけから文言の鍵を選ぶ。`detail`・`title` を読まない。知らない code・`reason` は汎用 | NFR1.8 |
| L4 | 最新の読み込み（各機能の `latestLoad.ts`） | 読み込みの世代を参照で持ち、古い答え・画面を離れた後の答えを捨てる（要求は取り消さない） | NFR2.11 |
| L5 | 待ちの案内（各機能の `useSlowNotice.ts`） | 送信の開始から 5 秒の時計、応答で止める。送信中の2回目の呼び出しを捨てる | NFR2.10 |
| L6 | 未保存の確かめ（`roleadmin/useUnsavedGuard.ts`・`LeaveConfirmDialog.tsx`） | `useBlocker` と `beforeunload`、木の節の選びの確かめ、`Tabs` の「留まる」の後のフォーカスの戻し | NFR6.7・NFR4.7 |
| L7 | 差分の写し（`roleadmin/permissionDraft.ts`） | 写しの更新と `entries` の組み立て（純粋な関数） | NFR1.4・NFR6.2 |
| L8 | 受け渡しの状態（`roletransfer/useRoleTransfer.ts`・`TransferFilePicker.tsx`・`transferLimits.ts`・`safeFileName.ts`・`saveFile.ts`） | ファイルの大きさの事前の確かめ（`MAX_TRANSFER_BYTES`）、確かめた本文の文字列と指紋と結果を画面の状態に持つ（Q1 A）、書き出しのファイル名、上限を超えた書き出しの案内（ヘッダー） | NFR1.5・NFR1.6・NFR1.10・NFR2.4・NFR2.7・NFR2.8・NFR6.6 |
| L9 | 名前の検査（`shared/validation/validateAdminName.ts`、新しい） | 前後の空白（全角を含む）を除いて 1〜64 コードポイント・制御文字を含まない。roleadmin と groupadmin が使う | NFR1.4 |
| L10 | ダウンロードの応答（`shared/api-client/apiClient.ts` の `ApiDownload`） | 応答の `headers` を足す（互換の変更） | NFR6.6 |
| L11 | ルーターの入口（`frontend/src/main.tsx`） | `createBrowserRouter`＋`RouterProvider` で今の `App` を包む。今の受け渡し（表示の設定・`StrictMode` など）は保つ | NFR6.7 |
| L12 | 画面の部品（各機能のページ・表・Modal・候補・結果の表・誤りの一覧、`useradmin/UserRolesDialog.tsx`） | 名前を文字として描く、状態を文字で示す、確かめの表示の形、文言の ja・en | NFR1.7・NFR4.1・NFR4.2・NFR4.3・NFR4.5・NFR4.6・NFR2.12 |
| L13 | E2E の部品（`frontend/e2e/` の U6 の検査のファイルと F の流れのファイル、見本の作り方） | 実際のブラウザの axe・幅・はみ出し、画面の時間の測り、F の流れ、差し替えの見本と型、報告の値の確かめ | NFR1.11・NFR2.5〜NFR2.9・NFR4.4・NFR4.5・NFR6.3・NFR6.10 |

- 共有（`shared/`）に置くのは L9 と L10 だけ。ほかは機能の中に持つ（機能どうしの import の禁止）。L4・L5 は3つの機能に同じ形で持ち、形をそろえるため `frontend-components.md` の名前と同じにする。
- 状態は画面ごとのフックが持ち、部品は値と操作を受けて描くだけ（機能設計 6節）。

## 2. make-you-chic-ui の部品の口（固定先 e82b651 のソースで確かめた）

| 部品 | 確かめた口（ファイル） | 設計での使い方 |
|---|---|---|
| Tabs | `activeIndex`・`defaultActiveIndex`・`onChange`・`aria-label`。矢印のキーでその場で選び、フォーカスも移す（自動の切り替え、`Tabs.tsx` の `focusAndSelect`） | 道でタブを決め、`onChange` で道を変える。未保存で `useBlocker` が止めたとき、`activeIndex` は今のタブのまま、フォーカスだけが隣に残るため、「留まる」の後に今のタブのボタンへフォーカスを戻す（L6） |
| Select | ネイティブの `select`。`SelectHTMLAttributes` から `value`・`defaultValue`・`onChange` を除いた属性をそのまま渡せる（`Select.tsx`） | `aria-label` に対象の名前を入れる。開いた一覧はブラウザが描くため、はみ出しの確かめの対象外 |
| Modal | `open`・`onClose`・`title`・`size`・`initialFocusRef`・`finalFocusRef`・`closeLabel`・`closeOnBackdropClick`（既定は真）・`role`（`dialog`・`alertdialog`）、`aria-modal="true"`（`Modal.tsx`） | 確かめは `role="alertdialog"`・`closeOnBackdropClick={false}`・はじめのフォーカスは「やめる」「留まる」、閉じたら開いたボタンへ戻す |
| Table | `labels`（ページ送りと詳細の文言）・`renderDetail`（使わない）・ページ送りを押せなくする口は無い（`Table.tsx`） | 一覧（S3・S6）に使い、読み直しの間の押下は `onPageChange` の側で捨てる。S7 の結果の表は素の table（機能設計 9節 (e)） |
| Dropdown | `trigger`・`items`（`disabled`・`description`）・`placement`（`bottom-start`・`bottom-end`） | 表の右端の行の操作は `bottom-end`。開いたメニューが表示の中に収まることを L13 で確かめる |

## 3. 共有の部品と入口の変更

- **L10**（NFR6.6）: `ApiDownload` に `headers: Headers` を足す。既存の `blob`・`contentDisposition` は変えない。DSL のダウンロードは変えない。`apiClient.download.test.ts` に項目を足し、既存の確かめは変えない（読み直しの R-05）。
- **L11**（NFR6.7）: 断片は形だけ。

```tsx
// 説明用の断片（今の main.tsx の起動の前処理と appearance の受け渡しは保つ）
const appearance = startAppearanceLoad()
const router = createBrowserRouter([{ path: '*', element: <App appearance={appearance} /> }])
createRoot(rootElement).render(
  <StrictMode>
    <RouterProvider router={router} />
  </StrictMode>,
)
```

- コード生成の最初に、差し替えた後に既存の画面のテストと E2E（010〜130）が変わらないこと、`useBlocker` がサイドバーの移動で止まることを確かめる。成り立たなければ戻して機能設計 7.3 の形にし、差を記録する。
- **L13 の差し替えの口**（承認の場の直し R-01、`performance-design.md` の 4.1・9節）: `frontend/e2e/support/adminApiRoute.ts` の `MockReply` に、前もって直列化した本文（`body: string`）を受ける形を足し、`reply` はその文字列を `route.fulfill` の `body` にそのまま渡す。今の `json` を受けて直列化する形は残し、既存の 120・130 は変えない。

## 4. 依存の向き

- 機能（roleadmin・groupadmin・roletransfer・useradmin）→ `shared/`（api-client・paging・validation・modal・tree）と `app/`（registry の型・admin-forbidden・display-settings）。機能どうしは依存しない（U1 の ESLint の制限）。
- `shared/` は `app/`・`features/` を読まない。
- 新しい npm の依存は無い（NFR6.5）。

## 5. 上流との差

- この段の Q1 A で、確かめた本文の文字列を画面の状態に持ち、適用でそれを送る形にした（機能設計 W7.4 の「同じファイル」の読みを、読んだ本文と決めた）。ファイルを書き換えたら選び直す案内を足す。
- role の NFR 設計の `RoleTransferSlot` により、確かめと適用の `ROLE_BUSY` は行の排他の上限切れと区別できない。S7 では「ほかの読み込みか操作と重なりました」の文言にし、ファイルの選択と前の結果を残す（機能設計 D4 の読み替え）。
- `Tabs` の自動の切り替えにより、未保存で止めたときのフォーカスの戻しを足した（機能設計 7.2 に無い細部）。

## 6. コード生成（B8）への引き継ぎ

- L11 の確かめを計画の最初の手順にする。
- L4・L5 を3つの機能に同じ形で置く。名前とテストの形をそろえる。
- L13 の番号は、U6 の検査を 140、F の流れを 150 とする案で、app-frame-ui と合わせて計画で確定する（読み直しの R-08）。
