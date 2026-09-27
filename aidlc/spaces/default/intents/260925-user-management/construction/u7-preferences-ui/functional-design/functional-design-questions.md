# Functional Design の質問 — u7-preferences-ui

単位 U7（`aidlc/spaces/default/intents/260925-user-management/inception/units-generation/unit-of-work.md`、種類 ui）の機能設計のための質問です。U7 は主に受け持つストーリーを持たず、US4.1・US5.1 の画面の側を受け持ち、共通の決まり CR1（文言）・CR6（画面の共通の決まり）に関わります（`unit-of-work-story-map.md`）。契約は C4（受ける側）・C9（受ける側）（`aidlc/spaces/default/intents/260925-user-management/inception/contract-design/contract-summary.md`）。画面は S4 プリファレンス・S5 パスワードの変更（`aidlc/spaces/default/intents/260925-user-management/inception/refined-mockups/` の `mockups.md` 7節・8節、`interaction-spec.md` 7節〜9節、`design-system-mapping.md`）。依存する単位の機能設計 `construction/u2-user-preferences/functional-design/`・`construction/u4-display-foundation/functional-design/` と、既存のコード `frontend/src/app/registry/`・`frontend/src/app/layout/ShellLayout.tsx`・`frontend/src/app/navigation/`・`frontend/src/app/routing/`・`frontend/src/features/auth/`・`frontend/src/shared/api-client/`・`vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/`（Dropdown・Button。変更しない）を確かめました。

ui の単位のため、成果物は `functional-spec.md`（画面の流れと状態の移り変わりの正本）・`frontend-components.md`・`traceability.json` です（`entities.md`・`rules.md` は作りません）。

## 設計の要点（案）

上流の決定とコードの確認から導ける、この単位の機能設計の見通しです。質問の答えで決まる点は（Qn）と書きます。

1. **置き場と登録**: 新しい機能 `frontend/src/features/preferences/` に、S4・S5 の2つの画面と `registration.ts` を置く。画面は `layout: 'SHELL'`・`access: 'LOGGED_IN'`。URL は API（`/api/me/...`）に合わせて `/me/preferences`・`/me/password` とする。画面の部品は既存の auth と同じく遅延読み込みにする。サイドバーには項目を足さない（入り口はユーザーメニューだけ、refined-mockups の Q2: B）。
2. **ユーザーメニューの2つの項目**: 「プリファレンス」「パスワードの変更」を、既存のログアウト（order 100）より前に並べる。選んだときに画面へ移る手段は（Q1）。
3. **プリファレンスの画面の読み込み（S4、AC4.1.1）**: 開いたら `GET /api/me/preferences`（C4）を呼び、読み込み中はフォームの位置に読み込み中の表示を出す。成功したら氏名・言語・テーマ・文字の大きさを入れたフォームを出す。テーマが system の利用者には system（「OS に合わせる」）が選ばれた状態で出す（今の見た目のライト・ダークではない）。読み込みの値と、画面に当たっている利用者の設定が違うときの扱いは（Q3）。
4. **読み込みの失敗**: 画面の中に `role="alert"` で失敗を出し、「もう一度読み込む」のボタンを置く（フォームは出さない。値の無いフォームで保存できないようにする）。401 は既存の ApiClient の更新と送り直しに任せ、更新もできなければ既存の流れでログインの画面へ移る。
5. **選んだ時点の見せ方（AC4.1.11、U4 の W10）**: テーマ・文字の大きさを選んだ時点で `setPreview(theme, fontSize)` を呼び、保存の前から画面に見せる。言語を選んでも口は呼ばず、案内「保存すると切り替わります」を出したまま画面の言語は変えない（ストーリーの M8）。
6. **「元に戻す」と画面を離れるとき**: 「元に戻す」はフォームの4つの値を今の設定（最後に読み込んだ値、または保存の成功の応答の値）に戻し、項目の誤りと失敗の表示を消し、`clearPreview` を呼ぶ。画面を離れるとき（部品が外れるとき）も `clearPreview` を呼ぶ。保存していない変更の確かめは出さない（S4 の [assumption]、Refined Mockups の承認で受け入れ）。「元に戻す」はフォームが今の設定と違うときだけ押せる。
7. **保存（PUT /api/me/preferences）**: 送る前に画面で検証し（Q2）、誤りがあれば送らずに項目の下に出して最初の誤りの項目へフォーカスを移す（CR6.1）。送信の間は「保存する」を押せなくし文言を「保存しています」にする（CR6.3、make-you-chic-ui の Button の `loading`）。成功したら応答の4つ（氏名は前後の空白を除いた値、U2 の Q3 A）で `applyUserPreferences` を呼び、フォームの値と今の設定を応答の値に置き換える。AppFrame が見せ方を捨て、当てている値・氏名・言語を置き換え、ブラウザにも保存する（U4 の D6 の (b)・D8）。
8. **保存の成功の知らせとフォーカス（AC4.1.12）**: `applyUserPreferences` の後の描画（新しい言語に切り替わった後）で Toast「保存しました」を出し、言語を変えたときも新しい言語で出す。make-you-chic-ui の Button は `loading` のあいだ `disabled` になり、ブラウザによってはフォーカスが外れるため、送信が終わったら（成功と、項目の誤りでない失敗）フォーカスを「保存する」に戻す。項目の誤りのときは最初の誤りの項目へ移す。
9. **保存の失敗**: 400 VALIDATION_FAILED は、応答の項目ごとの誤り（U2 の BR3.2）を対応する項目の下に出す（Q2）。通信の失敗・想定外の誤りは画面の中に `role="alert"` で出す。どちらも入れた値と見せ方は残す（`mockups.md` の S4 の状態の表）。
10. **パスワードの変更の画面（S5、POST /api/me/password）**: 今のパスワード（`autocomplete="current-password"`）・新しいパスワード・確かめ（どちらも `autocomplete="new-password"`、CR6.9）の3つの項目。入力の前の案内は新しいパスワードの「12 文字以上」だけで、72 バイトを超えたときだけ「長すぎます（半角で 72 文字、全角でおよそ 24 文字まで）」を項目の下に出す（refined-mockups の Q6: B）。送る前の検証は（Q2）。
11. **パスワードの変更の結果**: 204 なら Toast「パスワードを変更しました」を出し、3つの項目を空に戻し、フォーカスを「変更する」に戻す。ログインしたままで画面も移らない（AC5.1.8、FR6.3）。400 PASSWORD_CURRENT_MISMATCH は今のパスワードの項目に結び付けて誤りを出し、そこへフォーカスを移し、新しいパスワードの2つの値は消さない（AC5.1.7）。この code は 400 のため ApiClient の 401 の更新の流れに乗らず、ログインの画面へ移らない（AC5.1.6）。400 VALIDATION_FAILED は 9. と同じく項目ごとに出す。
12. **画面の検証の関数**: 氏名の検証とパスワードの規則の検証は、React に触れない純粋な関数にし、性質ベースのテスト（fast-check、失敗時の種を記録）の対象にする（`team.md`）。長さはコードポイント、バイト数は UTF-8 で数える（U2 の BR1.x・既存の `PasswordPolicy` と同じ数え方）。パスワードの規則の関数は登録の完了の画面（U6）と同じものを使うため、`frontend/src/shared/` の下の共通の置き場に置く。
13. **文言**: 新しい文言は ja・en の両方を `features/preferences/registration.ts` の `messages`（鍵は `preferences.` で始める）に置く。テーマ・文字の大きさの選択肢は骨組みの `display.theme.*`・`display.fontSize.*`、言語の選択肢は `LANGUAGE_NAMES`（「日本語」「English」と `lang` 属性、訳さない）を使う（U4 の 8節・D12）。
14. **部品と配置**: 氏名は TextInput と FormField、言語・テーマ・文字の大きさは RadioGroup（まとまりごとに `fieldset`・`legend`）、主な操作は Button（primary）、「元に戻す」は Button（secondary）、成功は Toast、失敗は Alert（`design-system-mapping.md`）。幅はコンテンツの幅 640px 前後、768px 未満は1列（`interaction-spec.md` 7節）。
15. **テスト**: 画面部品のテスト（Vitest・Testing Library・user-event、各画面に vitest-axe を1件、説明文は英語）で、AC4.1.1・AC4.1.11・AC4.1.12・AC5.1.6〜AC5.1.9 と、画面の側の AC4.1.4（保存で言語が切り替わる）・AC4.1.8（保存の直後にユーザーメニューの名前が変わる）・AC4.1.7（画面の検証と、サーバーの項目ごとの誤りの表示）、CR6.1・CR6.3・CR6.4・CR6.6・CR6.9 を確かめる。API は偽の応答で作る。フロントエンドのカバレッジの下限（行 80%・分岐 70%）を守る。
16. **U2 の確認の持ち越しの画面への影響**: R-01（NFR4 の 403 の場面が無い。一般の利用者も 200）は画面を `access: 'LOGGED_IN'` にすることと合い、画面の変更は無い。R-02（表示に関わる AC の OK と Deferred の分け方）は、U2 が Deferred で U7 に回した AC4.1.11・AC4.1.12・AC5.1.7・AC5.1.8・AC5.1.9 を U7 の `traceability.json` で受け、AC4.1.1 の「system が選ばれた状態の表示」も U7 で確かめる。R-03（C8 の PASSWORD_CHANGED の targetUserId）は監査の中身で、画面の変更は無い。

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| 画面は S4 プリファレンスと S5 パスワードの変更の別々の画面で、AppShell の中に置き、入り口はユーザーメニューの「プリファレンス」「パスワードの変更」 | refined-mockups の Q2: B、`mockups.md` 1節、AC5.1.9 |
| API は `GET`・`PUT /api/me/preferences`（4つをまとめて置き換え、応答は保存した4つ）と `POST /api/me/password`（3項目、成功は 204） | 契約 C4 |
| 今のパスワードの誤りは 400 `PASSWORD_CURRENT_MISMATCH`。入力の誤りは 400 `VALIDATION_FAILED`（項目ごとの誤り、入れた値は返さない） | 契約 C4、U2 の BR3.2・BR4.1・BR4.2・BR8.4 |
| 氏名は前後の空白（半角・全角）を除いてから判定し、除いた後が空なら拒否。上限は 254 文字（コードポイント）。制御文字（Cc）と見えない書式の文字（Cf）を含む氏名は拒否 | U2 の機能設計の Q1 A・Q2 B・Q3 A |
| パスワードの規則は 12 コードポイント以上かつ UTF-8 で 72 バイト以内、確かめは完全に一致。今と同じパスワードへの変更も通る | FR4.4・FR6.2、U2 の BR4.1・Q4 A |
| パスワードを変えてもログインしたまま（ほかの端末も）。今のパスワードの誤りでもログインしたまま | FR6.3、AC5.1.4〜AC5.1.6 |
| テーマ・文字の大きさは選んだ時点で見せ、保存しないと戻す。言語は保存した時点で切り替える | ストーリーの M8 A、AC4.1.11、U4 の W10 |
| 口は `useDisplaySettings`（`setPreview`・`clearPreview`・`applyUserPreferences` など）。U7 は選んだ時点で `setPreview`、言語では呼ばない、「元に戻す」と離れるときに `clearPreview`、保存の成功で `applyUserPreferences` の後に Toast | 契約 C9、U4 の frontend-components.md 3.4、W10 |
| 保存の成功で、ユーザーメニューの名前・画面の言語・`<html lang>`・要求の言語がすぐ変わり、ブラウザにも保存される（AppFrame が行う） | U4 の D6・D8・D14、AC4.1.4・AC4.1.8 |
| テーマ system を見せている間も OS の配色の切り替えに追従する（AppFrame が行う） | U4 の機能設計の Q1 A、D4 |
| 保存していない変更がある状態で画面を離れるときの確かめは出さない | `mockups.md` の S4 の [assumption]（Refined Mockups の承認で受け入れ） |
| パスワードの入力の前の案内は「12 文字以上」だけで、72 バイトを超えたときだけ長すぎる旨を出す | refined-mockups の Q6: B |
| 成功は Toast（`aria-live="polite"`）、失敗は画面の中の `role="alert"`。入力の誤りは項目の下に出して結び付け、送信で誤りが返ったら最初の誤りの項目へフォーカス。入れた値は消さない。送信の間はボタンを押せなくする | CR6.1・CR6.3・CR6.4、`interaction-spec.md` 1節 |
| 保存の後もフォーカスは保存のボタン。パスワードの変更の成功で3つの項目は空に戻る | AC4.1.12、AC5.1.8、`interaction-spec.md` 7節・8節 |
| テーマ・文字の大きさの選択肢の文言は骨組みの `display.theme.*`・`display.fontSize.*`、言語は「日本語」「English」 | U4 の 8節、CR6.6、`design-system-mapping.md` 4節 |
| 画面で隠すことはサーバー側の検査の代わりにしない（401 と 200 はサーバー側のテストで U2 が確かめる） | CR4、NFR4 |
| make-you-chic-ui は変更しない | `project.md` の Forbidden |
| 画面部品ごとにアクセシビリティの検査を1件、テストの説明文は英語、テストは対象と同じ場所の `*.test.ts(x)` | `team.md` の Testing Posture |

---

## Q1. ユーザーメニューの「プリファレンス」「パスワードの変更」を選んだとき、どうやって画面へ移りますか？

理由: 今のユーザーメニューの項目の登録（`frontend/src/app/registry/types.ts` の `UserMenuItemRegistration`）は、選んだときの操作を引数の無い関数（`action`）として持つだけで、画面の移動の手段を持ちません（今の項目はログアウトだけ）。make-you-chic-ui のユーザーメニューの項目（Dropdown の `MenuItem`）も `onClick` だけです。一方、サイドバーの項目は `path` を持ち、`ShellLayout` が React Router の `navigate` で移っています。U4 は登録の仕組みを変えないため、この単位で決めることになりました（U4 の担当からの持ち越し）。

A. ユーザーメニューの項目の登録に、任意の `path`（登録済みの画面の URL）を足す。`action` と `path` のどちらか一方だけを持つ形にし、登録の検査（`validateRegistrations`）でサイドバーと同じく「登録されていない URL」と「どちらも無い・両方ある」を誤りにする。`ShellLayout` は `path` を持つ項目を、サイドバーと同じく読み込み直しなしで移る。骨組み（`frontend/src/app/`）の型と検査と `ShellLayout` への安全な追加で、既存のログアウトはそのまま動く
B. 骨組みは変えず、`action` の中でブラウザの移動（`window.location.assign`）を使う。画面の全体を読み込み直すため、セッションの復元（トークンの更新）と見た目の設定の読み取りがやり直しになり、移るたびに一瞬画面が出ない
C. 骨組みに「画面を移る関数」を外から呼べる口（React Router の外から使える移動の口）を足し、`action` の中でそれを呼ぶ。登録の形は変えないが、登録の検査で URL の誤りを見つけられない
X. Other (please specify)

[Answer]: A

## Q2. 画面の側の入力の検証を、どこまで行いますか？

理由: 検証はサーバー側が正で、画面の検証はその代わりにしません（`team.md` の Code Style）。一方、入力の誤りを項目の下に出して結び付けること（CR6.1、AC5.1.7）と、72 バイトを超えたときだけ長すぎる旨を出すこと（refined-mockups の Q6: B）には、どの項目が何の理由で誤りかが要ります。U2 は `VALIDATION_FAILED` に項目ごとの誤り（項目の名前と理由）を付けると決めましたが、今の共通のエラー応答の仕組み（`GlobalExceptionHandler`）はまだ項目ごとの誤りを返していません（その形は U2 のコード生成で作ります）。

A. サーバーと同じ決まりを画面でもすべて確かめ、誤りがあれば送らない。氏名は前後の空白を除いた後の空・254 文字超え・制御文字や見えない書式の文字、パスワードは今のパスワードの空・新しいパスワードの 12 文字未満と 72 バイト超え・確かめの不一致。送った後にサーバーが項目ごとの誤りを返したときも、同じ項目の下に出す（画面とサーバーの決まりがずれたときの受け皿）
B. A と同じだが、氏名の使えない文字（制御文字・見えない書式の文字）は画面では確かめず、サーバーの項目ごとの誤りだけで出す（画面の決まりを減らす代わりに、その場合だけ送信の往復が要る）
C. 画面では確かめず、サーバーの項目ごとの誤りだけを項目の下に出す（決まりが1か所になる代わりに、すべての誤りで送信の往復が要り、項目ごとの誤りの形が決まるまで画面を作れない）
X. Other (please specify)

[Answer]: A

## Q3. プリファレンスの画面を開いたときに読んだ値が、画面に当たっている利用者の設定と違うときは、どうしますか？

理由: 画面に当たっている利用者の設定は、ログイン・セッションの復元・トークンの更新の応答（C3）と、最後の保存で決まります（U4 の D8）。ほかの PC で保存した後など、プリファレンスの画面で読んだ値（C4）がそれと違うことがあります。そのままでは、フォームは dark なのに画面は light に見える、「元に戻す」（`clearPreview`）で画面がフォームの値ではなく前の値に戻る、という食い違いが起きます。内部DB の値が正です（FR5.4）。

A. 読んだ値が当たっている値と違えば、読んだ値で `applyUserPreferences` を呼んで画面・言語・ユーザーメニューの氏名をそろえ、ブラウザにも保存する（画面を開いた時点で言語やテーマが変わることがある）
B. 読んだ値をフォームに出すだけで、画面には当てない。「元に戻す」はフォームを読んだ値に戻し、画面は当たっている値に戻る（食い違いは次のログイン・トークンの更新・保存まで残る）
C. 読まずに、画面に当たっている利用者の設定と氏名（`useDisplaySettings`）をフォームの初期値にする（C4 の GET を使わない。読み込み中の表示は要らなくなるが、ほかの PC で変えた値は次のトークンの更新まで映らない）
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめ:

- 設計の要点（案）は冒頭の「設計の要点（案）」の 16 件のとおり（置き場 `features/preferences`・URL `/me/preferences`・`/me/password`、読み込みの失敗はもう一度読み込むボタン、選んだ時点の見せ方と「元に戻す」、保存の成功で `applyUserPreferences` の後に新しい言語で Toast、送信の後にフォーカスをボタンへ戻す、パスワードの規則の関数を U6 と共通の置き場に置く、U2 の持ち越し R-01〜R-03 の画面への影響などを含む）
- Q1 A: ユーザーメニューの項目の登録に任意の `path` を足し、`action` と `path` のどちらか一方だけを持つ形にする。登録の検査で登録されていない URL とどちらも無い・両方あるを誤りにし、`ShellLayout` はサイドバーと同じく読み込み直しなしで移る（骨組みへの安全な追加。承認の場で骨組みの変更として確かめる）
- Q2 A: サーバーと同じ決まりを画面でもすべて確かめ、誤りがあれば送らない。サーバーが項目ごとの誤りを返したときも同じ項目の下に出す。確かめの関数は U6 が決めた `frontend/src/shared/validation/` の共用の関数を使う
- Q3 A: 読んだ値が画面に当たっている値と違えば、読んだ値で `applyUserPreferences` を呼んで画面・言語・ユーザーメニューの氏名をそろえ、ブラウザにも保存する（内部DB が正）

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
