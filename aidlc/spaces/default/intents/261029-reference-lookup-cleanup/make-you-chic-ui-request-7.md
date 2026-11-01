# make-you-chic-ui への依頼（7）

依頼元: MasterSmith（Intent 261029-reference-lookup-cleanup、CI Pipeline の段）
置き場の決まり: `aidlc/spaces/default/memory/team.md` の Way of Working（依頼ごとに文面を Intent の記録に置く。上流の対応は上流のソースで確かめてから固定先を上げる）
今の固定先: `4c63d11`（`4c63d11bff10ac08af1269fdafa0eb115fc6b528`。上流の `main` の先頭と同じ。2026-11-01 に `git fetch` で確かめた）

## 1. Tooltip が外されたときに、表示の予約（300 ms の時計）を取り消す

- **場所**: `packages/make-you-chic-ui/src/components/Tooltip/Tooltip.tsx` の 65〜68 行（`scheduleShow`）。配布物 `dist/index.js` では 1197〜1199 行。
  - `scheduleShow` は、マウスが入る（`onMouseEnter`）・フォーカスが来る（`onFocus`）と `window.setTimeout(() => setVisible(true), SHOW_DELAY_MS)`（300 ms）を予約する。
  - 予約を取り消すのは `hide`（70〜73 行。`onMouseLeave`・`onBlur`・Escape）だけで、**部品が外されたとき（unmount）に予約を取り消す後片付け（`useEffect` の cleanup）が無い**。
- **困っていること（症状）**: 行のリンクを Tooltip で包んだ一覧の画面で、リンクを押して別の画面へ移ると、一覧（と Tooltip）は外されるが予約は残り、300 ms 後に外された部品へ `setVisible(true)` が走る。
  - 本番では、外された部品への無駄な更新が1回走るだけ（React は無視する）。
  - MasterSmith の画面のテスト（Vitest＋jsdom）では、テストのファイルの最後のテストがリンクを押して約 100 ms で終わると、jsdom の環境が片付けられた（`window` が消えた）後に予約が走り、react-dom が `window.event` を読んで `ReferenceError: window is not defined` になる。テストはすべて通っても、Vitest が「処理されない誤り」として全体を失敗にする。2026-11-01 の CI（run 41264314230）で、同じ誤りが2回続けて起きた。
  - スタック: `Timeout._onTimeout` → `dist/index.js:1198`（`setVisible`）→ `dispatchSetState` → `requestUpdateLane` → `resolveUpdatePriority`（react-dom が `window.event` を読む）。
- **再現の考え方（最小）**: Tooltip で包んだボタンを描き、`fireEvent.mouseEnter`（または `focus`）の直後に `unmount()` する。本物の時計のまま、`window` を一時的に外して 400 ms 待つと、`ReferenceError: window is not defined` が処理されない誤りとして出る。偽の時計（`vi.useFakeTimers`）なら、`unmount()` の後に `vi.getTimerCount()` が 1 のまま残ることで分かる。
- **お願いしたい形**: 外されたときに予約を取り消す後片付けを足す。例:

  ```tsx
  useEffect(() => () => window.clearTimeout(showTimeoutRef.current), [])
  ```

- **足してほしい単体テスト**（`Tooltip.test.tsx`）: 偽の時計で、`mouseEnter`（と `focus`）の直後に `unmount()` したとき、予約が残らない（`vi.getTimerCount()` が 0）こと、300 ms 進めても誤り・警告が出ないこと。
- **取り込み**: 上流の `main` に入ったら、上流のソース（読み取りだけの `git show`）で後片付けとテストを確かめ、固定先の更新の専用のコミットで上げる。前後の `package-lock.json` の差（増えた・変わったパッケージと、`hasInstallScript` の付いた項目が増えていないか）を同じ記録に書く。

## MasterSmith の側の手当て（上流の直しを待たない）

- 上流の直しが入るまでの間、`frontend/vitest.setup.ts` で、テストの中で予約されたまま残った時計（`window.setTimeout`・`setInterval`）を、各テストの描画の片付けの後にすべて取り消すようにした（CI Pipeline の段の直し B4）。上流の直しを取り込んだ後も害は無く、そのまま残す。

## 依頼しないもの

- Tooltip の表示の遅れ（300 ms）や、ほかの振る舞いの変更

## 回答（上流からの回答の要点。依頼者が貼った文面から）

- **結論**: 対応済み。上流の `main` の `cab4207`（`cab42074cf040e3c03127db8c92582cc329f934d`）。
- **変更点**: Tooltip に、外されたときに表示の予約（300 ms の時計）を取り消す `useEffect` の後片付けを足した。単体テストを2件足した（`Tooltip.tsx`・`Tooltip.test.tsx` だけの変更）。
- **確認した内容（MasterSmith の側）**: 上流のソースを読み取りだけの `git show` で確かめた。`cab4207` が上流の `main` から辿れること、変更が上の2ファイルだけであること、`package-lock.json` の差が 0 行（増えた・変わったパッケージなし、`hasInstallScript` の付いた項目の増加なし）。依頼者の決定（58fa4ef4）で取り込んだ。
- **固定先**: `4c63d11bff10ac08af1269fdafa0eb115fc6b528` → `cab42074cf040e3c03127db8c92582cc329f934d`（専用のコミット `cb23466b`、`develop` へ fast-forward）。`cb23466b` の CI（run 41270200479）は1回目で success。
- **lockfile の差**: なし。
- **こちらの守り**: `frontend/vitest.setup.ts` の守り（`cac6fa54`）は、上流の直しの後も残してよい（上流の回答どおり、そのまま残す）。
