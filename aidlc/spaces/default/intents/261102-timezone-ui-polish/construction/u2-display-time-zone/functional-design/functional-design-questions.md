# Functional Design の質問（U2 u2-display-time-zone）

対象の単位: U2 `u2-display-time-zone`（種類 service。表示のタイムゾーンの設定の API・プリファレンスの選択・表示のタイムゾーンの配布・管理の画面の時刻）。出典は `unit-of-work.md`・`unit-of-work-story-map.md`・`requirements.md`（FR1・FR4・NFR1.2・NFR3.3・NFR4.1・NFR6.1）・`stories.md`（US1.1・US1.2・US5.3）・`contract-summary.md` の C2・C3・`decisions.md` の ADR-001・ADR-003・`mockups.md` の S1・既存のコード（読み取りだけ）。

## 決まっていること（質問にしない）

上流（要件・ストーリー・ADR・契約・画面イメージ）で決まっている点。

| 項目 | 決まっている内容 | 出どころ |
|---|---|---|
| 保存の置き場 | 利用者の設定の行（内部DB の `users` の表）に NULL 可の列を1つ足す。新しい表は作らない。前進のみで、足した列を消す移行は無い。値が無い状態が「ブラウザに合わせる」 | ADR-001・FR1.2・NFR4.1・AC1.1.8・AC5.3.1 |
| API の形 | `GET/PUT /api/me/preferences` に5つ目の項目 `displayTimeZone`（文字列か `null`）を足すだけ。本文に項目が無いときは保存済みの値を変えない（前の版の `PUT` と互換）。明示の `null` は値を消す（応答は `null`）。空の文字列・固定のオフセット表記（`+09:00`・`Z`）・サーバーが知らない名前・文字列でない型は 400 `VALIDATION_FAILED`、`fieldErrors` は `field` が `displayTimeZone`、`reason` が既存の `INVALID_VALUE`。新しい `code`・`reason` は足さない。保存済みの値は変わらない | C3・AC1.1.2・AC1.1.3 |
| 本人だけ・監査しない | 認証あり、本人の行だけ。本文の利用者 ID・他人の設定は読み捨てる。拒否も成功も監査に残さない（今のプリファレンスと同じ）。管理者が他人の設定を変える操作は作らない | NFR1.2・AC1.1.5・AC1.1.6・A5・C3 |
| 3つの状態の区別 | 項目が無い・明示の `null`・名前の値の3つを区別できる型で受ける。新しい依存の部品は足さない。型の名前・置き場は機能設計で決める | C3 の「項目の有無を区別して受ける仕組み」 |
| 画面の配布 | フック `useDisplayTimeZone`（置き場 `frontend/src/app/display-settings/`）。`status` は `loading`・`ready`、`timeZone`（loading のときは `null`）、`source`（`saved`・`browser`・`saved-unresolvable`）、`savedName`。loading の間は時刻の欄を出さない。保存の後は読み込み直さずに差し替わる。ログイン後の画面だけで使う | C2・AC1.2.1・AC1.2.5 |
| 効かせ方 | 設定があればその値。無ければブラウザのタイムゾーン。保存済みの名前を今のブラウザが解釈できないときは、保存の値を変えずにブラウザのタイムゾーンで見せ、そのことを画面に出す | FR1.5・AC1.2.2・AC1.2.4 |
| 名前の解釈は U1 | 名前を今のブラウザが解釈できるかは U1 の `canInterpretTimeZone`。固定のオフセット表記は解釈できない名前として扱う。ブラウザの既定のタイムゾーンを暗に使わず、タイムゾーンは必ず明示に渡す | C1・AC1.2.6・ADR-003 |
| 選択の部品 | `Select` を `FormField` で包む。先頭が「ブラウザに合わせる」、続けて `Asia/Tokyo (+09:00)` の形（IANA の名前と今の時差）。ブラウザが知らない名前は並べない。保存の拒否は `Select` の下に誤りの文、保存済みの名前を解釈できないときは `Select` の上に `Alert`（warning）。文言は日本語と英語の両方 | S1・FR1.1・NFR3.3・NFR6.1・AC1.1.1・AC1.1.4・AC1.1.7 |
| 管理の画面の範囲 | 利用者の一覧・招待の一覧・DSL の状態と履歴（確認の表示を含む）の時刻を、表示のタイムゾーンで見せる。設定の読み込みの前は時刻の欄を出さない | FR4.1・AC1.2.3・AC1.2.5 |
| 必須のテスト | 認証・認可・監査の必須のテストのうち、本人だけ・未認証 401・要求の本文の改ざん（他人の設定が変わらない）。名前の照合は純粋な関数にして jqwik の性質ベースのテスト。テストのタイムゾーンは明示に固定する。axe は、ブランドカラーとテーマのすべての組・誤りの状態・長い名前で行う | unit-of-work.md の U2・team.md の Testing Posture・AC5.2.1〜AC5.2.3 |

## コードを読んで設計で決めること（質問にしない。承認の場で覆せる）

読み取りだけで確かめた事実から、機能設計の決まり（rules）として書く点。

| 項目 | 決めること | 確かめた事実 |
|---|---|---|
| 名前の照合の範囲 | 受けるのは、サーバーの `ZoneId` が持つ地域の名前の集合（`ZoneId.getAvailableZoneIds()`）への、大文字小文字を区別した完全一致だけ。前後の空白は除かず、長さの上限は 64 文字（U1 の名前の門と同じ）。保存は送られた名前のまま（別名を正式名に直さない） | `ZoneId.of` は `UTC+9`・`GMT+09:00` なども受けるため、固定のオフセットを確実に弾くには集合への一致が要る。JDK 25 の集合は `Asia/Calcutta` も `Asia/Kolkata` も含む。いま使っているブラウザ（V8）の `Intl.supportedValuesOf` は `Asia/Calcutta` を返すため、正式名だけに絞ると画面が出した名前を拒否してしまう |
| `Select` の並び | 地域ごとのまとまり（`optgroup`）は使わず、1列の並びにする。ブラウザが返す順（名前の昇順）のまま。キーボードの先頭の文字入力で `Asia/T` のように進められる | make-you-chic-ui の `Select` は標準の `select` で、選択肢は `label`・`value` の平らな配列だけ |
| 選択肢に無い保存済みの名前 | 保存済みの名前が一覧に無いとき（解釈できない名前、または解釈できるが一覧に無い名前）は、その名前の選択肢を「ブラウザに合わせる」の次に足し、選択が保存値を映すようにする。解釈できる名前には時差を添え、解釈できない名前は名前だけ | 保存値が選択肢に無いと、標準の `select` は先頭の項目を選んでいるように見せ、何も触らずに保存すると値が消えてしまう |
| 内部DB の列と移行 | 列の名前は `display_time_zone`、`VARCHAR(64)`、NULL 可、既定なし。移行は `V14`（今の最後は `V13`） | `backend/src/main/resources/db/migration` の最後の版が `V13` |
| 5つ目の持ち方 | 今の `Preferences`（氏名・言語・テーマ・文字の大きさの4つ。利用者の作成と約 17 のテストが使う）は変えず、表示のタイムゾーンは別の値として運ぶ。保存は「変えない・消す・置く」を区別して、列を書き分ける | `new User(..., Preferences)` と 17 か所のテストの生成が4つの引数を前提にする |
| 設定の読み込み | ログイン後に1回 `GET /api/me/preferences` を読む（ログインの応答は変えない）。プリファレンスの画面の読み込み・保存の成功も、効いている値を更新する | ログイン状態の `preferences` は言語・テーマ・文字の大きさだけを持ち、C3 は `/api/me/preferences` の項目を足すだけと決めている |
| ブラウザのタイムゾーン | 読む場所は配布の部品の1か所だけ（`Intl.DateTimeFormat().resolvedOptions().timeZone`）。取り出し方は差し替えられるようにする。解釈できなければ `UTC` | ブラウザの既定を暗に読む場所を1か所に閉じ、テストで固定するため |

以下の質問は、上の決まりだけでは決まらない点に絞った（3問）。

## Q1. [開発] 設定の読み込みに失敗したとき、時刻をどう見せるか

文脈: 表示のタイムゾーンは、ログインの後の `GET /api/me/preferences` で読みます。読み込み中は時刻の欄を出さない（AC1.2.5）ことは決まっていますが、読み込みが失敗したとき（接続の失敗・サーバーの 5xx）の扱いが契約 C2（`loading` と `ready` の2つだけ）に無く、ずっと読み込み中のままにも、ブラウザのタイムゾーンで出すこともできてしまいます。プリファレンスの画面は、読み込みに失敗すると失敗の表示と「再読み込み」を出す今の作りです。

**推奨: B。** 理由: 「ブラウザのタイムゾーンの時刻を、保存した設定のように一瞬でも出さない」（AC1.2.5 の趣旨）は、失敗のときにも同じく守るべきです。保存してあるタイムゾーンが読めないまま別のタイムゾーンの時刻を出すと、利用者は気づかずに読み違えます。時刻の欄だけ止め、ほかの内容は今までどおり使えるので、失敗の影響は小さく済みます。A は画面がいつも動く代わりに間違いを黙って出し、C は設定が読めない原因が直るまで自動で待つだけで、読めない間の見せ方が決まりません。

- A. ブラウザのタイムゾーンで出す（`ready`・`source` は `browser`）。画面はいつも動くが、保存したタイムゾーンと違う時刻を黙って出しうる。
- B. 時刻の欄を出さず、失敗の表示にする（`status` に `error` を足す。C2 への足すだけの変更）。時刻の欄には読み込み中と同じ場所に「表示のタイムゾーンを読み込めません」の短い文を出し、画面の上の1か所に「再読み込み」を置く。プリファレンスの画面の読み込みや保存が成功した時点でも直る。
- C. 読み込み中のまま、間隔を空けて自動で再試行する（最大の回数を決める）。回数を超えたら B の表示にする。
- X. Other (please specify)

[Answer]: B

## Q2. [開発] 管理の画面（利用者・招待・DSL）の時刻の書式をどうするか

文脈: いま3つの画面は `formatDateTime`（言語別の書式と時差の略号。日本語 `2026-09-24 10:15 JST`、英語 `Sep 24, 2026, 10:15 GMT+9`）と、利用者の一覧の「ロックの解除の予定」の `formatLockedUntil`（今日なら時刻だけ）が、`Intl` で直接変換しています。ADR-003 とストーリー AC1.2.6 は、変換を U1 の1か所に閉じ、各画面はその部品だけを使うと決めています。U1 の `formatInstant` は `2026-11-02 09:00:00 +09:00`（言語に依らず、時差は数字）を返し、既存の `formatDateTime` には U1 から依存しない作りになっています。業務データの画面は `formatInstant` の書式で出るので、書式をどこまでそろえるかが分かれます。

**推奨: C。** 理由: 変換を U1 の1か所に閉じられ（AC1.2.6）、管理の画面は秒まで要らないので分までに絞れます。時差を略号（`JST`・`IST` は国で意味が分かれる）でなく数字で出すため、業務データの画面と同じ時刻を同じ書き方で読めます。A は変更が小さい代わりに、変換の経路が2つ残り、AC1.2.6 と ADR-003 の決まりに反します。B は一番単純ですが、一覧の1行に秒まで出て、管理の画面では読みにくくなります。

- A. 今の書式（`formatDateTime`・`formatLockedUntil`）を残し、表示のタイムゾーンを必須の引数として明示に渡すだけにする。画面の見た目は変わらないが、変換が U1 と `Intl` 直の2系統になる（AC1.2.6・ADR-003 との差を記録する）。
- B. `formatInstant` の文字をそのまま使う（`2026-11-02 09:00:00 +09:00`）。業務データの画面と完全に同じ書式になる。
- C. `formatInstant` の文字から秒以下を落とし、分までにする（`2026-11-02 09:00 +09:00`）。変換は U1 の1か所。「ロックの解除の予定」は、表示のタイムゾーンで今日なら時刻だけ（`09:00 +09:00`）、今日でなければ日付つき。今の `formatDateTime`・`formatLockedUntil` は使わなくなる。
- X. Other (please specify)

[Answer]: C

## Q3. [開発] 選択肢に `UTC` を足すか

文脈: 画面の選択肢は、ブラウザの `Intl.supportedValuesOf('timeZone')` の返す名前（FR1.1「ブラウザが知っているすべての IANA のタイムゾーン」）を並べます。確かめたところ、いま使っているブラウザ（V8）の返す 418 個には `UTC` が入っていません（`Intl.DateTimeFormat` は `UTC` を受け付けます）。サーバーは `UTC` を受けます（JDK 25 の集合に入っている）。端末のタイムゾーンが日本の人が、時刻を UTC で見たいときの選び方が分かれます。

**推奨: A。** 理由: `UTC` はブラウザが解釈でき、サーバーも受け、「ブラウザに合わせる」では端末が UTC の人にしか選べません。運用で時刻を UTC に固定して見たい人（ログと突き合わせる管理者など）が自然に選べるようにするためです。足すのは1個で、並びは名前の昇順のまま末尾に近い位置に入れるだけです。B は FR1.1 の文言どおりで一番単純ですが、`UTC` を選ぶ手段が無くなります。

- A. `UTC` を足す（ブラウザの一覧に無いときだけ。名前の昇順の位置に入れ、時差は `+00:00`）。
- B. 足さない（ブラウザの一覧のとおり）。
- X. Other (please specify)

[Answer]: A

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 単位: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/units-generation/unit-of-work.md`・`unit-of-work-story-map.md`
- 要件・ストーリー: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/requirements-analysis/requirements.md`・`inception/user-stories/stories.md`
- 契約・決定: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-summary.md`（C1・C2・C3）・`inception/domain-design/decisions.md`（ADR-001・ADR-003）
- 画面イメージ: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/refined-mockups/mockups.md`（S1）・`design-system-mapping.md`
- 今のコード（読み取りだけ）: `backend/src/main/java/cherry/mastersmith/user/`、`backend/src/main/resources/db/migration/`、`frontend/src/app/display-settings/`・`app/work-role/`・`features/preferences/`・`features/useradmin/`・`features/invitation/`・`features/dsl/`・`shared/format/`、`vendor/make-you-chic-ui` の `Select`
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 「コードを読んで設計で決めること」の各行は、承認の場で覆せる。覆す場合は、該当の決まりを書き直す。
- [assumption] ブラウザの `Intl.supportedValuesOf` の内容（`UTC` を含まない・`Asia/Calcutta` を返す）は、手元の V8 で確かめた値。ほかのブラウザの内容は未確認で、選択肢は実行時にブラウザから取るため設計は変わらない。
- Q1〜Q3 の答えが出てから、`entities.md`・`rules.md`・`functional-spec.md`・`frontend-components.md`・`traceability.json` を書く。
