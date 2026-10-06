# NFR 設計の質問 — U6 role-admin-ui

対象の単位: U6 role-admin-ui（kind: ui、作るのは B8）。作る成果物は `performance-design.md`・`security-design.md`・`logical-components.md`・`traceability.json` の4つです（段の定義の `produces_kinds`。scalability・reliability・observability は ui の単位に作らない）。段の定義の `consumes` は scalability・reliability・observability の要件を必須に挙げていますが、ui の単位の NFR 要件はそれらを作らないため、承認済みの `performance-requirements.md`・`security-requirements.md`・`tech-stack-decisions.md` の3つを入力にします。

読んだもの:

- この単位の承認済みの NFR 要件 `construction/role-admin-ui/nfr-requirements/`（NFR1.1〜NFR1.12・NFR2.2〜NFR2.13・NFR4.1〜NFR4.7・NFR6.1〜NFR6.10、9節の承認の場の直し R-01）と、その読み直しの記録で直していない指摘 R-02〜R-08。
- 承認済みの機能設計 `construction/role-admin-ui/functional-design/`（`functional-spec.md` の D1〜D32・W3.1〜W9.1・7節・8節、`frontend-components.md`）、契約 `inception/contract-design/contract-summary.md`、部品の一覧 `inception/domain-design/components.md`。
- 統合の点（読み取りだけ）: `construction/role/nfr-design/`。画面が受ける code が2つ変わりました。
  - `RoleTransferSlot`: 確かめと適用は同時に1つだけ通り、2つ目は待たずに `ROLE_BUSY`。
  - 同じ利用者の初めての作業ロールの切り替えの重なりも `ROLE_BUSY` を返す。これは U7 の画面の持ち物。
- コード（読み取りだけ）:
  - `frontend/playwright.config.ts`・`frontend/playwright-secret-check-reporter.ts`・`frontend/e2e/support/secretValues.ts`。報告の部品は、`recordSecretValues` が書いた値と、`createRegisteredUser` の値の形（`u7-perf-…@example.com`・`e2e-u7-pw-…`）で報告を探します。
  - `frontend/scripts/check-bundle-size.mjs`。入口から静的に読み込むファイルだけを数え、遅延読み込みの塊は数えません。
  - make-you-chic-ui（固定先 e82b651）の `Tabs`・`Select`・`Modal`。

質問は 1 問です。ほかの点は承認済みの要件・機能設計・`team.md`・`project.md` で決まっているため、「決まっていること」と「設計の要点」に書き、まとめの確認で確かめます。

---

## 決まっていること

### 要件と機能設計で決まっていること

- 画面の判定はサーバーの代わりにしない。管理の API の 403 は骨組みの `useAdminForbidden` に渡す（NFR1.1）。
- 送る項目は決めたものだけにする（NFR1.4）。名前は文字として描く（NFR1.7）。失敗の文言は `code` で選ぶ（NFR1.8）。応答は型の項目だけを写す（NFR1.9）。
- 書き出しのファイル名は安全な形にする（NFR1.10）。値をコンソール・保存・URL に出さない（NFR1.6）。E2E の報告に値を残さない（NFR1.11）。
- 画面の時間は Q1 A のとおり測り、記録して成否にしない（NFR2.5〜NFR2.9）。待ちの案内と二重の送信の防ぎ（NFR2.10）、古い答えを捨てる（NFR2.11）、カラムを分割しない（NFR2.12）、遅延読み込み（NFR2.13）。
- 依存は足さない（NFR6.5）。共有の `ApiDownload` にヘッダーを足す（NFR6.6）。data router に替え、使えなければ機能設計 7.3 に切り替える（NFR6.7）。
- 実際のブラウザの axe の範囲は NFR4.4 のとおりで、E2E の本数に数えない（`project.md` の読み方）。
  - 誤りの状態と開いた部品を含める。
  - 見本は 2語の氏名・64 文字の名前・`<`・`&` を含む名前。
  - 表示の設定の 20 組すべてで確かめ、開いた Dropdown・Modal が表示の中に収まることも見る。
  - 幅（360px・768px・1280px）は既定の1組で確かめる（NFR4.5）。
- F の流れの E2E は NFR6.3（承認の場の直し R-01）のとおり。
  - ファイルの始めに自分で版 2 の DSL を投入して適用し、前のテストの状態に頼らず、後始末はしない。
  - 番号は 040 より後に置く。B8 で前半、B9 で同じファイルに後半を足す。
- 画面のテストは、描画の後の値を `waitFor`・`findBy*` で待ち、時間の上限を延ばさない（NFR6.9）。差し替えの見本は1つにまとめて型を付け、本物と一致することを流れの E2E で毎回確かめる（NFR6.10）。

### make-you-chic-ui の部品のソースで確かめた口（固定先 e82b651）

- **`Tabs`**:
  - 外から `activeIndex`・`onChange` で切り替えられる。
  - 矢印のキーで隣のタブに移ると、その場で選んだことになり（自動の切り替え）、フォーカスも移る。
  - 使い方: 未保存の写しがあると、道の移動は `useBlocker` で止まる。そのとき `activeIndex` は今のタブのまま、フォーカスだけが隣のタブに残る。そのため「留まる」を選んだら、フォーカスを今のタブへ戻す（設計の要点）。
- **`Select`**:
  - ネイティブの `select` で、`aria-label` などの属性をそのまま渡せる。
  - 開いた一覧はブラウザが描くため、画面のはみ出しの確かめの対象外。
- **`Modal`**:
  - `role`（`alertdialog` を渡せる）・`aria-modal`・`closeOnBackdropClick`（既定は真。確かめでは偽を渡す）・`initialFocusRef`・`finalFocusRef` を持つ。
- **`Table`・`Dropdown`**: 機能設計で確かめたとおり（`labels`・`placement="bottom-end"`・項目の `disabled` と `description`）。

### role の NFR 設計から受けること

- `ROLE_BUSY` は、行の排他の上限切れのほかに、確かめ・適用の入口（`RoleTransferSlot`）でも返る。2つは画面からは区別できない（同じ code）。
- S7 の確かめと適用で `ROLE_BUSY` を受けたときは、「ほかの読み込みか操作と重なりました。少し待ってからもう一度お試しください」と出す。ファイルの選択と、前の確かめの結果を残す（機能設計 D4 の読み替え）。
- 監査に残らない拒否であることは画面に関わらない。

---

## 設計の要点（質問にしない案。まとめの確認で確かめる）

### 部品の構成（`logical-components.md`）

- 機能設計の `frontend-components.md` の部品を論理の部品として並べ、各部品が受け持つ NFR の ID を書く。
- 守りの部品（失敗の文言・ファイル名・大きさの確かめ・差分の保存・古い答え・待ちの案内・未保存の確かめ）は、純粋な関数かフックとして1か所に置く。
- 共有するもの（`shared/`）は次の2つだけ。ほかは機能の中に持つ（機能どうしの import の禁止）。
  - `shared/validation/validateAdminName.ts`（新しい）
  - `shared/api-client/apiClient.ts` の `ApiDownload` の項目の追加（NFR6.6）

### 古い答えを捨てる形（NFR2.11）

- 各フックが読み込みの世代（数）を参照で持ち、読み込みを始めるたびに進める。答えを受けたとき、世代が違えば捨てる。
- 画面を離れたら、世代を進めて後の答えを捨てる。
- 要求そのものは取り消さない（既存の ApiClient に取り消しの口が無く、送った書き換えはサーバーで確定しうるため）。

### 待ちの案内と二重の送信（NFR2.10）

- 1つのフック（`useSlowNotice`）が送信の開始から 5 秒の時計を持ち、応答で止める。
- 画面のテストは偽の時計で 4.9 秒と 5 秒の境界を確かめる。実時刻と `sleep` に頼らない。

### 未保存の確かめ（NFR6.7・機能設計 7節）

- `useUnsavedGuard` が `useBlocker` と `beforeunload` を受け持つ。
- `Tabs` の矢印のキーで止まったときは、上の「`Tabs`」の項のとおり「留まる」の後にフォーカスを今のタブへ戻す。

### S7 の大きな応答（NFR2.7・NFR2.8）

- 確かめの応答は JSON として1回読み、型の項目だけを写した値を画面の状態に持つ。描くのはロールの要約と、開いたロールの 100 件だけ。
- 適用の成功・ファイルの選び直し・画面を離れたときに、結果の値を捨てる。
- 指紋とファイルの扱いは Q1。

### 画面の時間の測り（読み直しの R-03・R-07）

- NFR2.7 の測り始めは、差し替えの口が確かめの応答を返した時点（`route.fulfill` を呼ぶ直前の時刻）に固定する。JSON の読み込みと描画を含み、測り終わりは要約の表の最後の行が見えた時点。
- 目標を超えた値は、Build and Test が `Not Met` と値を記録し、承認の場で依頼者が扱いを決める。目標を緩めて満たしたことにはしない（`project.md` の学び）。

### 初回の JavaScript（読み直しの R-04）

- `check-bundle-size.mjs` は入口の大きさだけを数えるため、NFR2.13 の確かめは「入口が増えていない」ことの確かめとする（新しい画面は遅延読み込みで入口に入らない）。
- 遅延読み込みの3つの機能の塊の gzip の大きさは、Build and Test で manifest から読んで記録だけする。台本は変えない。

### 既存のテストへの影響（読み直しの R-05）

- `useradmin` のメニューの項目の数を確かめる既存のテスト（`rowActions.test.ts`・`UserRowActions.test.tsx`・`UserAdminPage.test.tsx`）を、項目が1つ増える形に直す。
- `apiClient.download.test.ts` に応答のヘッダーの項目を足す。既存の項目の確かめは変えない。
- どちらもコード生成（B8）の計画の影響の範囲に入れ、`security-design.md` の確かめの表に載せる。

### E2E の報告（読み直しの R-06）

- F の流れが作る利用者は既存の `createRegisteredUser` で作り、作った直後に `recordSecretValues` で値を書く。こうすると、既存の報告の部品の値と値の形で探す対象に入る。
- ロール・DSL の名前は秘密でないため値のファイルに入れない。
- 実際のブラウザの検査のファイルは本物の利用者を作らず、見本の値（`example.com`）だけを使う。

### E2E の番号と見本の名前（読み直しの R-08）

- 番号は、既存の 130 より後で、040 より後の1つの並びに決める。
  - U6 の検査のファイルを 140、F の流れのファイルを 150 とする案にする。U7 の検査と I の流れは 160 以降。
  - 確定はコード生成の計画で、app-frame-ui と合わせて行う。
- 流れ用の版 2 の DSL の見本は、040 の見本（`e2e_dept` など）と I の流れの見本と、スキーマ・テーブル・メニューの名前が重ならないように、流れごとに接頭辞（F の流れは `E2EF_`）を付ける。

### traceability の寄せ方（読み直しの R-02）

- この段の `traceability.json` では、NFR6.5〜NFR6.8 を NFR6.4 の行に寄せた理由（統合の関門として近いため）を、その行の target に書く。
- 承認済みの NFR 要件の文書は書き換えない。

---

## Q1 確かめの後、適用で送る YAML をどこから取るか

背景: 適用は、確かめに使った YAML と同じ本文と指紋を送ります（機能設計 W7.4、契約 C7 の差）。

- 上限ちょうどのファイルは 10 MiB です。確かめの応答は数十 MB の JSON になりうり、画面はそれを持ちます（NFR2.7）。
- 確かめの後に、ファイルが手元で書き換えられることがあります。
- サーバーは適用のときに一覧を作り直して指紋を比べます。そのため、違う中身を送れば `ROLE_TRANSFER_STALE` で断られ、設定は変わりません（role の BR9.10）。

A. 確かめのときに読んだ本文の文字列を、画面の状態に持ち、適用でそれを送る（推奨: 確かめた中身と送る中身が必ず同じになり、ファイルの書き換えやブラウザの読み直しの失敗に左右されない。持つ量は最大 10 MiB で、確かめの応答より小さい）
  - ファイルの選び直し・適用の成功・画面を離れたときに捨てる。
  - ファイルの書き換えは反映されないため、書き換えたら「ファイルを選ぶ」から選び直す、と案内の文に書く。
B. 選んだファイル（`File`）だけを持ち、適用のときに読み直して送る（持つ量は小さい）。
  - ファイルが書き換えられていれば、サーバーが `ROLE_TRANSFER_STALE` で断り、確かめのやり直しの案内になる。
  - ブラウザによっては、書き換えられたファイルの読み直しが失敗する（読み直せない旨の文言を足す）。
C. B と同じだが、適用の前に読み直した本文の SHA-256 を、確かめのときの値と画面で比べ、違えば送らずに確かめのやり直しを案内する（サーバーの断りを待たずに分かるが、画面にハッシュの計算を足す）。
X. Other (please specify)

[Answer]: A **Mode:** guided

## Consolidated Summary Confirmation

答えのまとめ（role-admin-ui の NFR 設計）:

- Q1 A: 確かめのときに読んだ本文の文字列（最大 10 MiB）を画面の状態に持ち、適用でそれを送る。ファイルの選び直し・適用の成功・画面を離れたときに捨てる。ファイルを書き換えたら選び直すよう案内に書く。
- 決まっていることと設計の要点（make-you-chic-ui の Tabs・Select・Modal の口をソースで確かめた振る舞い、`RoleTransferSlot` などで返る `ROLE_BUSY` の S7 での出し方、読み直しで直していない R-02〜R-08 の設計での手当て、古い答えは世代の数で捨てる、待ちの案内は1つのフックが 5 秒の時計を持ち偽の時計で確かめる、shared に置くのは名前の検査の関数と `ApiDownload` の項目の追加の2つ、E2E の番号は案として 140・150 で確定はコード生成の計画で app-frame-ui と合わせる、流れ用の DSL の見本に接頭辞 `E2EF_`）は案のとおり。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
