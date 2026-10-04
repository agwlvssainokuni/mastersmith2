# 操作の仕様 — role-menu

出典: `mockups.md`（画面 S1〜S10）、ストーリー `aidlc/spaces/default/intents/261004-role-menu/inception/user-stories/stories.md`、この段の答え `refined-mockups-questions.md`。部品の仕様は `.claude/knowledge/aidlc-design-agent/component-spec-template.md` の形で書く。画面の幅ごとの振る舞い（区切り 768px・1024px、確かめる幅 360px・768px・1280px）は `mockups.md` の 10a 節を正とし、各部品の Responsive Behaviour はその要点を書く。

## 共通の決まり

- 確かめの表示（Modal）は背景のクリックで閉じず、Escape と「やめる」で閉じる。はじめのフォーカスは「やめる」、閉じたらフォーカスを開いたボタンに戻す（前の Intent と同じ）。
- 操作の結果（保存・作成・削除・切り替え・適用）は、画面の上の Alert か Toast と、`aria-live="polite"` の読み上げで伝える。拒否は理由と次にすることを文字で出し、`code` や内部の値は出さない。
- 読み込み中は既存の画面と同じ表示（Skeleton は使わず、文字「読み込んでいます」と押せないボタン）。読み込みの失敗は「読み込めませんでした。[もう一度]」。
- 403 を受けたときは、既存の骨組みの 403 の扱い（管理の画面全体の権限なしの表示）に従う。
- 文言は日本語と英語。ロール・グループ・テーブルの名前は訳さない。

---

## WorkRoleSwitcher（作業ロールの切り替え、S1）

| Field | Value |
|---|---|
| Component | WorkRoleSwitcher |
| Description | トップバーで今の作業ロールを示し、自分のロールから切り替える |
| Category | navigation |

### States

| State | Description | Trigger |
|---|---|---|
| default | 「作業ロール: 営業 ▼」 | ロールが2つ以上 |
| single | 「作業ロール: 営業」（ボタンにしない） | ロールが1つ |
| none | 「作業ロール: なし」、押すと案内 | ロールが無い |
| open | 自分のロールの一覧と「自分の権限を見る」 | 押す・Enter・Space・↓ |
| switching | 選んだ項目を押せなくして待つ | 切り替えの要求の間 |
| error | 一覧を閉じず、「切り替えられませんでした」 | 拒否・通信の失敗 |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| roles | 配列（id・name） | yes | — | 自分のロール（直接とグループ経由の和） |
| currentRoleId | 数・null | yes | — | 今の作業ロール |
| onSwitch | 関数 | yes | — | 切り替えの要求 |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | make-you-chic-ui の Dropdown で実際に使える形にする: トリガーのボタン（Dropdown が付ける `aria-haspopup="true"`・`aria-expanded`）と、`role="menuitem"` の一覧。選択中は項目の名前に文字「（使用中）」を含めて示す（例: 「営業（使用中）」）。`menuitemradio` と `aria-checked` は、上流への依頼（選択中の印）が取り込まれた場合の将来の形とし、今回は使わない |
| Keyboard interaction | Enter・Space・↓ で開く、↑↓ で移る、Enter で選ぶ、Escape で閉じる（Dropdown の既存の操作） |
| Label / aria-label | 「作業ロール: 営業」（見える文字と同じ。768px 未満で見える文字が「営業 ▼」に縮んでも名前は同じ） |
| Screen reader | 切り替えたら「作業ロールを経理に切り替えました」 |
| Focus management | 選んだ後と閉じた後はボタンに戻す |

---

## NavTree（業務のメニューの木、S1）

| Field | Value |
|---|---|
| Component | NavTree（make-you-chic-ui の入れ子のサイドバーに依頼。`make-you-chic-ui-request.md`） |
| Description | DSL の `menus` を作業ロールで絞った木と、管理のメニューを見出しで分けて出す |
| Category | navigation |

### States

| State | Description | Trigger |
|---|---|---|
| default | 開閉の状態を保った木 | 読み込みの後 |
| current | 今の画面の項目に `aria-current="page"` と太字・左の線 | 道が項目と一致 |
| collapsed / expanded | まとまりの開閉 | 開閉のボタン |
| empty | 業務の見出しを出さない | 絞った木が空・DSL が無い |
| reloading | 作業ロールの切り替えの後に読み直す（今の木は残す） | 切り替え |
| error | 業務の部分に「メニューを読み込めませんでした。[もう一度]」 | 読み込みの失敗 |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | `nav`（名前は言語に合わせる「メインメニュー」「Main menu」）、見出しで「業務」「管理」を分け、各まとまりは `ul` の入れ子。tree の役割は使わない |
| Keyboard interaction | Tab でリンクと開閉のボタンを順にたどる。開閉のボタンは Enter・Space |
| Label / aria-label | 開閉のボタンは「販売を開く／閉じる」、`aria-expanded` |
| Screen reader | 今の項目は「現在のページ」 |
| Focus management | 開閉してもフォーカスはボタンに残る |

- 開閉の状態は利用者のブラウザ（セッションの間）に覚える。直接開いた画面では今の項目の祖先を開く。
- テーブルの画面の道は、テーブルの名前をエンコードして組み立てる（スキーマ名・テーブル名の2つ）。

---

## PermissionTree と PermissionTable（権限の設定、S4・S8）

| Field | Value |
|---|---|
| Component | PermissionTree（左）・PermissionTable（右） |
| Description | スキーマ→テーブルの木と、選んだ対象の明示の値・実効の値・継承の元・メニューに出るかの表 |
| Category | input（S4）・display（S8） |

### States

| State | Description | Trigger |
|---|---|---|
| default | 木の1段目（スキーマ）だけを読み込み済み | 画面を開く |
| loadingChildren | 開いたスキーマのテーブル、選んだテーブルのカラムを読み込む | 開く・選ぶ |
| dirty | 「保存していない変更: n 件」、保存と取り消しを押せる | Select を変える |
| saving | 表を押せなくして保存を待つ | 保存 |
| saved | Alert「保存しました」 | 保存の成功 |
| rejected | Alert に理由（変えるものが無い・DSL に無い など） | 拒否 |
| noDsl | 対象が無い案内と DSL の管理へのリンク | 適用済みの DSL が無い |
| orphan | 「⚠ 今の DSL に無い」の行と「設定を消す」 | DSL に無い設定（Should） |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| roleId | 数 | S4 は yes | — | 対象のロール（S8 は自分の作業ロール） |
| readOnly | 真偽 | no | false | S8 は true |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | 木は NavTree と同じ開閉のボタンの形、表は `table` と見出しのセル |
| Keyboard interaction | Tab で木と表を移る。Select は既存の make-you-chic-ui の Select の操作 |
| Label / aria-label | Select の名前は「受注番号の主権限」のように対象を含める |
| Screen reader | 実効の値は「READ、テーブルから継承」と文字で読ませる |
| Focus management | 保存の後は Alert の文を読ませ、フォーカスは保存のボタンに残す |

- 保存していない変更があるまま、木の別の対象・タブ・画面へ移るときは Modal「保存していない変更があります。移ると変更は保存されません。」［留まる］［移る］（はじめのフォーカスは「留まる」）。

### Responsive Behaviour（PermissionTree と PermissionTable）

| Breakpoint | Behaviour |
|---|---|
| mobile (<768px) | 縦に積む（上に木の区画、下に表）。木で選ぶと表の見出しへフォーカスを移す。表の1行を2段にし、上に対象の名前と実効の値、下に Select |
| tablet (768–1024px) | 左の木を 220px に詰める |
| desktop (>1024px) | 左の木 280px、右に表 |

### Responsive Behaviour（WorkRoleSwitcher・NavTree）

| Breakpoint | Behaviour |
|---|---|
| mobile (<768px) | 作業ロールは「営業 ▼」に縮める。サイドバーは AppShell の既存の開閉に任せ、開いた表示の中で NavTree を同じ形で出す |
| tablet (768–1024px) | サイドバーの幅を詰め、長い名前は省略して Tooltip |
| desktop (>1024px) | 既定 |

---

## RoleAssignmentPanel（割り当て、S5）とメンバー（S6）

- 「利用者を足す」「メンバーを足す」は Modal の中の TextInput（氏名かメールアドレスの一部）と候補の一覧。候補は最大 20 件を出し、続きは文字を足して絞る。招待中の人は出さず、利用停止は「（利用停止）」、割り当て済み・メンバー済みは「割り当て済み」と出して選べない。
- 「外す」は確かめなしで行い、結果を Alert で出す。ただし〔Should〕本人の作業ロールが変わる・無くなるときだけ確かめの表示を出す（AC2.2.9）。
- 出どころの列は「直接」「グループ 〇〇」を文字で並べる。

---

## RoleTransferPage（権限の受け渡し、S7）

| State | Description | Trigger |
|---|---|---|
| idle | 書き出すボタンとファイルの選択 | 画面を開く |
| exporting | 書き出すボタンを押せなくする | 書き出す |
| checking | 確かめるボタンを押せなくする | 確かめる |
| checked | ロールごとの要約の表（開くと対象と前後の値） | 確かめの成功 |
| noChange | 「変わる点はありません」、適用は押せない | 変わる点が無い |
| invalid | DSL の投入と同じ誤りの一覧 | 検証の誤り |
| applying | 確かめの表示の後、適用を待つ | 適用する |
| applied | Alert「作成 1・置き換え 1」 | 適用の成功 |
| stale | 「確かめた後に設定が変わった」の案内と確かめるボタン | 適用時の再検証で拒否 |

- ファイルの大きさは選んだ時点で上限と比べ、超えるときは送らずに文字で示す（上限の値は NFR 要件の段で決める）。この事前の確かめは案内のためで、判定の正はサーバー側の検証とする（`team.md` の信頼できない入力の決まり）。上限ちょうどのファイルは送る（AC3.1.7 の境界と食い違わせない）。サーバーが拒否したときは invalid の誤りの一覧に理由を出す。

### Responsive Behaviour（RoleTransferPage）

| Breakpoint | Behaviour |
|---|---|
| mobile (<768px) | 確かめの結果の表は内側で横にスクロールし、ロールの列を固定する。適用のボタンは表の下に幅いっぱい |
| tablet (768–1024px) | 列を折り返す |
| desktop (>1024px) | 既定 |

---

## TablePlaceholderPage（テーブルの画面の置き場、S2）

- 道: `/tables/{スキーマ名}/{テーブル名}`（名前はエンコード）。開いたときにサーバーに権限を問い、READ・FULL なら準備中、NONE・DSL に無いときは同じ権限なしの表示。
- 作業ロールを切り替えたときは問い直す。
