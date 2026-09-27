# Functional Design の質問 — u4-display-foundation

単位 U4（`aidlc/spaces/default/intents/260925-user-management/inception/units-generation/unit-of-work.md`、種類 ui）の機能設計のための質問です。U4 は主に受け持つストーリーを持たず、US1.1・US3.2・US4.1 に関わり、共通の決まり CR1・CR2・CR6 の土台を受け持ちます（`unit-of-work-story-map.md`）。契約は C3（受ける側）・C7（受ける側）・C9（持ち主）（`aidlc/spaces/default/intents/260925-user-management/inception/contract-design/contract-summary.md`）。依存する単位の機能設計 `construction/u2-user-preferences/functional-design/`・`construction/u8-instance-appearance/functional-design/` と、既存のコード `frontend/src/app/`・`frontend/src/shared/api-client/`・`frontend/src/features/auth/`・`vendor/make-you-chic-ui/packages/make-you-chic-ui/src/theme/`（変更しない）を確かめました。

ui の単位のため、成果物は `functional-spec.md`（画面の流れと状態の移り変わりの正本）・`frontend-components.md`・`traceability.json` です（`entities.md`・`rules.md` は作りません）。

## 設計の要点（案）

上流の決定とコードの確認から導ける、この単位の機能設計の見通しです。質問の答えで決まる点は（Qn）と書きます。

1. **置き場**: 表示の設定の口（契約 C9 の `useDisplaySettings`・`saveBrowserDisplaySettings`）は `frontend/src/app/display-settings/` に置く。AppFrame（`frontend/src/app/`）の一部で、機能（`features/*`）はこの口だけを使い、make-you-chic-ui の `useTheme` を直接呼ばない。
2. **表示の設定の3つの軸の持ち方**: 言語（ja・en）・テーマの選択（light・dark・system）・文字の大きさ（sm・md・lg）を1つの状態として持つ。状態は「当てている値（保存済み）」と「選んだ時点の見せ方（未保存のプレビュー）」の2段で、画面に当たるのはプレビューがあればプレビュー、無ければ当てている値。`setPreview`・`clearPreview` はプレビューだけを変え、ブラウザに保存しない（S2・S4、AC4.1.11）。
3. **テーマ system の解決**: テーマの選択が system のときは、OS の配色の設定（`prefers-color-scheme`）を見て light・dark に解き、make-you-chic-ui の `setTheme` には解いた値だけを渡す（make-you-chic-ui の ThemeMode は light・dark の2値だけ）。開いている間の OS の切り替えに追従するかは（Q1）。
4. **解く関数は純粋な関数にする**: 「保存の値の読み取りと検証（壊れた値・知らない値は無いものとして扱う）」「テーマの選択と OS の配色から light・dark を解く」「ブラウザの言語設定から ja・en を解く（既存の `resolveLanguage`）」を純粋な関数にし、性質ベースのテスト（fast-check、失敗時の種を記録）の対象にする（`team.md`）。
5. **ブラウザへの保存**: そのブラウザに「最後に保存された表示の設定」（言語・テーマの選択・文字の大きさ）を localStorage の1つの鍵にまとめて持つ。利用者ごとではなくブラウザごと（FR5.5）。読み書きは例外を投げない形（localStorage が使えないときは保存せずに動く）。3つの値は項目ごとに検証し、無い・壊れた項目はその項目だけ既定（言語はブラウザの言語設定、テーマは system、文字の大きさは md）で解く。make-you-chic-ui 自身の保存（`design-system-*` の鍵）との関係は（Q3）。
6. **保存する時点**: (a) ログインの成功と画面を開いたときのセッションの復元（利用者の設定を当てて保存、FR5.4）、(b) プリファレンスの保存の成功（U7 が `applyUserPreferences` を呼ぶ）、(c) 登録の完了（U6 が `saveBrowserDisplaySettings` を呼ぶ、AC3.2.18）、(d) ログインの画面の言語の切り替え（言語だけを書き換え、ほかの2つは保存済みのまま、S3）。ログアウトでは消さない（ログアウトの後のログインの画面は最後に保存された値、AC4.1.6）。
7. **ログインの前後の当て方**: ログインの前は保存された値（無ければブラウザの言語設定・OS の配色・md）。ログインの後は利用者の設定（C3 の `language`・`theme`・`fontSize`）を当て、同じブラウザに残った前の利用者の値は使わない（FR5.4、AC4.1.5・AC4.1.9）。
8. **利用者の設定の受け取り方**: 既存の差し込み口「ログイン状態の提供元」の `LoginState`（`frontend/src/app/registry/types.ts`）に、利用者の表示の設定（言語・テーマ・文字の大きさ）を任意の項目として足し、`displayName` には C3 の `displayName` を入れる（今はメールアドレス、M7）。AppFrame は `features/auth` に依存せず、ログイン状態の変化を受けて当てる。AuthUi（`features/auth`）の `CurrentUser` と `toLoginState` を C3 に合わせて広げる。
9. **最初の画面から正しい見た目**: 既存の `LoginStateGate` は復元の最初の答えが出るまで何も描かない。ログイン状態が変わったときは、同じ描画の中で（画面に出る前に）表示の設定と言語を当ててから、ログインの後の画面を描く。前の利用者の見た目が途中で見えないことを部品のテストで確かめる（AC4.1.5）。
10. **保存の後と更新の後の食い違い**: AppFrame は「今の利用者の表示の設定と氏名」を持ち、ログイン状態の変化（ログイン・復元・トークンの更新の応答）と `applyUserPreferences`（保存の後）のうち後に来たほうで置き換える。トークンの更新の応答は内部DB の値（保存の後の値）を返すため、両者は食い違わない。ユーザーメニューの氏名も同じ値から出す（AC4.1.8）。
11. **表示の言語の切り替えの口**: 今の `I18nProvider` は起動時に1回だけ言語を決める（K-5）。言語を状態として持ち、`setLanguage` で文言・`<html lang>`・要求の言語をまとめて変える（CR1.3）。`setLanguage` はブラウザに保存しない（保存は 6. の時点だけ）。登録の完了の画面の「言語」の項目を選んだ時点の切り替え（refined-mockups の Q4: C）もこの口を使う。
12. **要求の言語（ADR-005、CR1.2）**: ApiClient は、すべての要求（認証の API を含む）に、今の表示の言語を `Accept-Language` として付ける。ログインの後は表示の言語が利用者の言語（FR7.1）のため、「ログインの前は画面の言語、ログインの後は利用者の言語」がこの1つの決まりで満たされる。プレビュー中の未保存の言語は無い（プリファレンスの画面の言語は保存するまで変えない、M8）。AppFrame が ApiClient に「今の言語を返す関数」を登録する（既存の `registerAuthHandlers` と同じ形。ApiClient は AppFrame に依存しない）。
13. **見た目の設定の API（C7、U8 の Q1 A）**: ApiClient に、トークンを付けず・401 での更新と送り直しもしない「公開の API のパス」として `/api/appearance` を足す（今の認証の API のパスと同じ扱い）。AppFrame は起動時に1回だけ読み、`brandColor`・`fontFamily` を make-you-chic-ui の `setBrand`・`setFontFamily` で当てる。読み取りに失敗したら、ブラウザに残る前の値（無ければ make-you-chic-ui の既定 blue・sans）のまま表示し、画面は止めない（CR2 は Should）。利用者の操作で変える手段は置かない（FR8.1）。読み終わるまでの見せ方は（Q2）。
14. **ユーザーメニュー**: ShellLayout のトップバーに出す名前を `displayName`（氏名）にする。「プリファレンス」「パスワードの変更」の項目は U7 が機能の登録で足し、U4 は登録の仕組みを変えない。
15. **ログインの画面の広げ（AuthUi、S3）**: (a) 右上に言語の切り替え（Could、CR1.5、`LoginLanguageSwitch`。「日本語」「English」を各言語の名前と `lang` 属性つきで示す、CR6.6）。(b) 登録の完了から移ったときは、登録が終わった旨の案内（Alert）を出し、メールアドレスの欄に招待のメールアドレスを入れておく（AC3.2.17、S2 の [assumption] は承認済み）。メールアドレスは URL に載せず、ブラウザにも保存しない。U4 が画面の中のメモリだけで1回だけ受け渡す口を用意し、U6 が完了のときに渡し、ログインの画面が1回読んだら消す（読み込み直すと案内は出ない）。
16. **登録の完了の画面（U6）とプリファレンスの画面（U7）への約束**: U6 は開いた時点で招待の言語を `setLanguage` で当て、テーマ・文字の大きさは保存された値のまま（FR4.3）、選んだ時点で `setPreview`、完了で `saveBrowserDisplaySettings`。U7 は選んだ時点で `setPreview`、「元に戻す」と画面を離れるときに `clearPreview`、保存の成功で `applyUserPreferences`（AC4.1.11・AC4.1.12）。この決まりを `frontend-components.md` に口の使い方として書く。
17. **テスト**: 画面部品のテスト（Vitest・Testing Library・user-event、各部品に vitest-axe を1件）で、AC4.1.2〜AC4.1.6・AC4.1.8・AC4.1.9・AC3.2.17・AC3.2.18 と CR1.2・CR1.3・CR1.5・CR2（画面側: `<html>` の `data-brand`・`data-font-family`、利用者の操作で変えられないこと）を確かめる。OS の配色は `matchMedia` の差し替えで作る。ApiClient の `Accept-Language` と `/api/appearance` のトークンなしはユニットのテストで確かめる。フロントエンドのカバレッジの下限（行 80%・分岐 70%）を守る。
18. **既存への影響**: AppFrame・I18nProvider・ApiClient・ShellLayout・LoginStateGate・AuthUi は既存の画面すべてに当たるため、既存の画面のテスト（`frontend/src/app/testing/renderWithProviders.tsx` を使うものを含む）が通り続けることを確かめる。make-you-chic-ui は変更しない。

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| 表示の設定を当てる仕組み（テーマ system の解決・ブラウザへの保存・言語の切り替えの口・ユーザーメニューの氏名）は AppFrame に足す | ADR-007、Domain Design の Q7 A |
| 口の形は `useDisplaySettings`（language・theme・fontSize・setPreview・clearPreview・applyUserPreferences・setLanguage）と `saveBrowserDisplaySettings`。置き場は `frontend/src/app/` の側で U4 が決める | 契約 C9 |
| ログインと更新の応答の `user` に displayName・language・theme・fontSize が足される（theme は system のまま返る） | 契約 C3、U2 の機能設計 |
| 見た目の設定は `GET /api/appearance` の `brandColor`・`fontFamily`（ログインなし）で受け、起動時に読んで当てる。配信する HTML への埋め込みはしない | ADR-006、契約 C7 |
| 画面の側で `/api/appearance` を呼ぶときはトークンを付けない（U4 の ApiClient の変更）。サーバーの側は今の作りのまま | U8 の機能設計の Q1 A |
| `/api/appearance` の応答は `no-store`。読み終わるまでの一瞬の古い見た目の防ぎ方は画面の側（U4）で扱う | U8 の機能設計の設計の要点 9 |
| ApiClient は、ログインの前は画面の言語、ログインの後は利用者の言語を `Accept-Language` に入れる。サーバーの決め方は変えない | ADR-005、CR1.2 |
| 画面の言語が変わったら `<html lang>` も変える | CR1.3 |
| ログインしたら内部DB の設定を当て、同じブラウザの前の利用者の値は使わない。最初の画面から当たり、前の利用者の見た目が途中で見えない | FR5.4、AC4.1.5、AC4.1.9 |
| ログインの前の画面は、そのブラウザに最後に保存された言語・テーマ・文字の大きさ。無ければブラウザの言語設定と OS の配色 | FR5.5、AC4.1.6 |
| テーマ system は OS の配色で表示する（開いている間の追従は機能設計で決める） | FR5.3、AC4.1.3、要件の未解決の点 |
| プリファレンスの画面はテーマと文字の大きさを選んだ時点で見せ、保存しないと戻す。言語は保存で切り替える | ストーリーの M8 A、AC4.1.11、S4 |
| 登録の完了の画面は開いた時点でテーマ・文字の大きさをブラウザの値で表示し、選んだ時点で見せ、完了で選んだ3つをブラウザに保存してログインの画面へ | refined-mockups の Q3 A、AC3.2.18、ストーリーの M4 A |
| 言語の切り替えはログインの画面だけに置き、切り替えた言語をブラウザに保存する。登録の完了の画面は「言語」の項目を選ぶと表示の言語が変わる | refined-mockups の Q4 C、CR1.5（Could） |
| 登録の完了の後のログインの画面は、完了の案内を出し、メールアドレスを URL に載せない。メールアドレスの欄に招待のメールアドレスを入れておく | AC3.2.17、`mockups.md` の S2（[assumption]、Refined Mockups の承認で受け入れ） |
| ユーザーメニューの名前はメールアドレスではなく氏名。保存した直後から変わる | ストーリーの M7 A、AC4.1.8 |
| ブランドカラー・フォントファミリーは利用者の操作では変えられない。make-you-chic-ui の保存の値はインスタンスの設定で上書きする | FR8.1、`design-system-mapping.md` の 3節 |
| 言語の選択肢は各言語の名前（「日本語」「English」）で示す | CR6.6 |
| make-you-chic-ui は変更しない。テーマ system と利用者ごとの設定の当て方は frontend の側で扱う | `project.md` の Forbidden、要件の制約 |
| 画面部品ごとにアクセシビリティの検査を1件、テストの説明文は英語、テストは対象と同じ場所の `*.test.ts(x)` | `team.md` の Testing Posture |
| アクセストークンと利用者はメモリだけに持つ（localStorage に置かない）。表示の設定はトークンではないためブラウザに保存してよい | 既存の `frontend/src/features/auth/authSession.ts`、FR5.5 |

---

## Q1. テーマが system のとき、画面を開いている間に OS の配色（ライト・ダーク）が切り替わったら追従しますか？

理由: 要件の未解決の点で、この段の持ち主とされています（`stories.md` の「後の段に回す点」、契約 C9 の未解決の点）。make-you-chic-ui は開いた時点の OS の配色だけを見て、開いている間の切り替えには追従しません（K-4）。AC4.1.3 の確かめ方が決まりません。

A. 追従する（OS の配色の変化を受けて、画面を開いたまま light・dark を切り替える。ログインの前後、登録の完了とプリファレンスの画面で system を選んで見せている間も同じ）
B. 追従しない（開いた時点・ログイン・保存・選んだ時点で解き、その後の OS の切り替えは画面を読み込み直すまで映らない）
X. Other (please specify)

[Answer]: A

## Q2. インスタンスの見た目の設定（ブランドカラー・フォントファミリー）を読み終わるまで、画面をどう見せますか？

理由: ADR-006 で起動時に API で読む形にしたため、読み終わるまで前に保存された値で一瞬描かれるおそれがあり、防ぎ方はこの段で決めることになっています（契約 C7 の未解決の点、U8 の設計の要点 9）。なお今の画面は、画面を開いたときのセッションの復元（トークンの更新の要求）の答えが出るまで何も描きません（`frontend/src/app/login-state/LoginStateGate.tsx`）。見た目の設定はこの復元と並べて読めます。

A. 復元と同じく、見た目の設定の答えが出るまで画面を描かない（並べて読むため、待ちはほとんど増えない）。読み取りに失敗したら、前に当てた値（無ければ blue・sans）で描き、読み直さない
B. 待たずに前に当てた値で描き、読み終わったら当てる（設定を変えた後の最初の表示と、初めて開くブラウザでは、一瞬違う色・フォントが見える）
C. A と同じく待つが、待ちに上限（例: 2 秒）を置く。上限を超えたら前に当てた値で描き、答えが届いたら当てる
X. Other (please specify)

[Answer]: A

## Q3. ブラウザに保存する表示の設定と、make-you-chic-ui 自身の保存を、どう関係させますか？

理由: make-you-chic-ui の ThemeProvider は、テーマ（light・dark）・文字の大きさ・ブランドカラー・フォントファミリーを軸ごとに localStorage（`design-system-*` の鍵）に保存し、値を当てるたびに書き込みます。同じブラウザのほかのタブも、その書き込みを受けて見た目を変えます（`vendor/make-you-chic-ui/packages/make-you-chic-ui/src/theme/ThemeProvider.tsx`）。一方、その鍵は system と言語を持たず、選んだ時点の見せ方（未保存）でも書き換わるため、「最後に保存された値」（FR5.5）の置き場にはなりません（設計の要点 5）。ほかのタブへの映り方が選び方で変わります。

A. U4 の鍵（言語・テーマの選択・文字の大きさ）を正とし、make-you-chic-ui の鍵は写しとして扱う。画面を開いたとき、最初に描く前に U4 の鍵の値で make-you-chic-ui の鍵を書き直す（読み込み直した直後に古い見た目が出ないように）。ほかのタブへの映り方は make-you-chic-ui の今の動きのまま受け入れる（テーマ・文字の大きさの見た目は、選んだ時点の見せ方も含めてほかのタブに映り、見せ方をやめると戻る。言語とテーマの選択の system はほかのタブに映らない）
B. A に加えて、U4 の鍵の変化（保存・ログイン・ログインの画面の言語の切り替え）をほかのタブでも受けて、言語・テーマの選択・文字の大きさをそろえる（あるタブで保存すると、開いているほかのタブも保存した値になる）
C. U4 の鍵には言語とテーマの選択の system かどうかだけを持ち、テーマ（light・dark）と文字の大きさは make-you-chic-ui の鍵をそのまま使う（仕組みは小さいが、選んだ時点の見せ方のまま読み込み直すと、保存していない値が「最後に保存された値」として残る）
X. Other (please specify)

[Answer]: A

## Q4. フォントファミリーが serif の設定のとき、明朝体のフォントをどう用意しますか？

理由: make-you-chic-ui の serif は 'Noto Serif JP'・ヒラギノ明朝・游明朝・serif の順に指定しています（`vendor/make-you-chic-ui/packages/make-you-chic-ui/src/theme/tokens.css`）が、今の画面は Noto Sans JP だけを自前で配信し（`frontend/src/main.tsx`）、Noto Serif JP は入っていません。組み込みガイドは両方を利用側の依存に足す手順です。CSP は `font-src 'self'` のため外部のフォントは読めません。CR2（Should）の serif がどう見えるかが決まりません。

A. `@fontsource/noto-serif-jp` を足し、sans と同じ太さ（400〜700）の japanese・latin を自前で配信する（OS によらず同じ見た目。ライセンスは sans と同じ OFL-1.1 で、Apache License 2.0 と異なるため設計の記録に理由を残す。フォントのファイルは serif を当てたときだけ読まれるが、配信する `dist` は大きくなる）
B. A と同じく足すが、太さは 400 と 700 だけにする（`dist` の増えを抑える。500・600 は近い太さで代わりに描かれる）
C. 足さない（serif のときは OS の明朝体（ヒラギノ明朝・游明朝など）に任せ、OS によって見た目が変わることを受け入れる）
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめ:

- 設計の要点（案）は冒頭の「設計の要点（案）」の 18 件のとおり（ログイン状態に利用者の表示の設定を任意の項目として足す、登録の完了からログインの画面へのメールアドレスの受け渡しは画面の中のメモリだけで1回、見た目の設定の API にはトークンを付けない（U8 の Q1 A）などを含む）
- Q1 A: テーマが system のときは、画面を開いている間の OS の配色の切り替えにも追従する
- Q2 A: 見た目の設定は、セッションの復元と並べて読み、答えが出るまで画面を描かない。読み取りに失敗したら前に当てた値（無ければ blue・sans）で描き、読み直さない
- Q3 A: U4 の鍵（言語・テーマの選択・文字の大きさ）を正とし、make-you-chic-ui の鍵は写しとして扱い、最初に描く前に U4 の鍵の値で書き直す。ほかのタブへの映り方は make-you-chic-ui の今の動きのまま受け入れる
- Q4 A: `@fontsource/noto-serif-jp` を足し、sans と同じ太さ（400〜700）の japanese・latin を自前で配信する（ライセンスは OFL-1.1 のため、採用の理由を設計の記録に残す）

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
