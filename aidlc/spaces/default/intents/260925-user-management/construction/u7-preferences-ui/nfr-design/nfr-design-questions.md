# NFR Design — Questions（U7 プリファレンスとパスワードの変更の画面 / u7-preferences-ui）

U7 は、ログインした利用者が自分の氏名・言語・テーマ・文字の大きさを変えて保存する画面（S4）と、自分のパスワードを変える画面（S5）の単位です。種類は ui のため、作る成果物は performance-design・security-design・logical-components・traceability の4つです（拡張性・信頼性・観測性は service の単位だけ）。

画面の単位の作りの多くは、U4 の NFR 設計（実際のブラウザの検査の置き場・組・組の切り替え方・axe-core の読み込み方・合否の規則）と、U2 の NFR 設計（`fieldErrors` の形・条件つきの更新・401 の返し方）で決まっています。そのため、まず NFR 設計の要点（案）を示します。上流から作りが1つに決まらない3点だけを質問にします。

1. 画面の時間の測りの置き場と、測りに使う利用者（NFR 要件の承認の場の R-01）
2. ログインの後の2つの画面の実際のブラウザの検査で、ログインする利用者と組の切り替え方
3. 実際のブラウザの検査に、項目の誤りを出した状態を入れるか

読んだ上流:

- 承認済みの NFR 要件 `aidlc/spaces/default/intents/260925-user-management/construction/u7-preferences-ui/nfr-requirements/`
  - NFR2.1、NFR6.1〜NFR6.6、NFR7.1〜NFR7.5、NFR8.1・NFR8.2、NFR9.1〜NFR9.10
  - `nfr-requirements-questions.md` の Q1: A と要点
- NFR 要件の承認の場の決定（Minor 2 件は Accepted risk で、後の段で拾う）
  - R-01: 画面の時間の測りの置き場（`./gradlew e2eTest` の中か、Build and Test の手順か）を、B5 のコード生成の計画で決めて記録する
  - R-02: U7 が U6 の共用の確かめの関数に頼ることが、U6 の側でも同じ内容で記録されているかを、B5 のコード生成の計画の承認の前に突き合わせる
- 承認済みの機能設計 `construction/u7-preferences-ui/functional-design/`
  - `functional-spec.md`: D1〜D14、W1〜W13、6節（応答ごとの動き）、9節（骨組みの変更）、10節（上流との差）
  - `frontend-components.md`: 2節（`fieldErrors.ts`・`errorMessages.ts`）、3節（読み込みの番号・送信中に外れたときの答えの捨て方）、7節（テストの一覧）
- 依存する単位の NFR 設計（READY）
  - `construction/u4-display-foundation/nfr-design/`: `logical-components.md` の5節（`frontend/e2e/050-display-accessibility.e2e.ts` の見込み、`frontend/e2e/support/` の手伝い、組 (a)・(b)・(c) の 20 組、組の切り替えは U4 の鍵の初めのスクリプトと `/api/appearance` の差し替え、`<html>` の属性の直接の書き換えはしない、組ごとに新しいコンテキスト）、`performance-design.md` の3節（テストの側の時計で5回、注記と添付で記録、関門にしない）、`security-design.md`（axe-core は Node の側で読み `page.evaluate` で評価、合否は WCAG 2.0・2.1 の A・AA のタグ、`ApiError.problem` は追加の項目ごと持つ）
  - `construction/u2-user-preferences/nfr-design/`: `security-design.md` の2節（本人の行が無ければ 401 `AUTHENTICATION_REQUIRED`）・3節（400 `VALIDATION_FAILED` の追加の項目 `fieldErrors: [{field, reason}]`、reason は `REQUIRED`・`TOO_SHORT`・`TOO_LONG`・`INVALID_CHARACTER`・`INVALID_VALUE`・`MISMATCH`）、`reliability-design.md` の2節（パスワードの書き換えは条件つきの更新で、照合の後に変わっていれば 400 `PASSWORD_CURRENT_MISMATCH`）、`performance-design.md`（パスワードの変更の性能の場面で、1人の利用者を同時に2つの場面で使わない）
- 並行して設計する単位の上流
  - U6 の機能設計 `construction/u6-registration-ui/functional-design/functional-spec.md` の6節（共用の確かめの関数 `validateDisplayName`・`validateNewPassword`・`validatePasswordConfirmation` は、理由を `'required'`・`'tooLong'`・`'invalidCharacter'`・`'tooShort'`・`'mismatch'` の union で返し、文言を持たない）。U6 は `fieldErrors` を読まない（U6 の機能設計の Q3 A）
  - U5・U6 の NFR 要件（招待を置く検査・測りは、E2E の WAR で招待を使える設定と、手元の受け手からリンクを取り出す手段に頼る。どちらも infrastructure-design の持ち主。U6 の E2E-1 と同じ前提）
- 契約 `aidlc/spaces/default/intents/260925-user-management/inception/contract-design/contract-summary.md` の C4（受ける側）・C9（受ける側）、画面 `inception/refined-mockups/interaction-spec.md` の7節・8節（狭い幅（768px 未満）で1列に積み、ラジオの選択肢は折り返す）
- 決まり `aidlc/spaces/default/memory/team.md`・`project.md`
  - E2E の本数と置き場。Playwright の検査と測りは流れではないため本数に数えない（NFR 要件の段の学び）
  - 不安定なテストは統合しない。目標を緩めて「満たした」ことにはしない
- 既存のコード（読み取りだけ）
  - `frontend/playwright.config.ts`: `workers: 1`・`fullyParallel: false`、`Desktop Chrome`、ロケール `ja-JP`、WAR は実行ごとの一時の内部DB。初期管理者（`e2e-admin@example.com`）とパスワードは実行ごとに作って環境変数で渡す。SMTP とベース URL は渡していない
  - `frontend/e2e/020-auth.e2e.ts`・`030-admin-access.e2e.ts`: ユーザーメニューを `getByRole('button', { name: adminEmail })` で開き、`getByRole('menuitem', { name: 'ログアウト' })` を選ぶ
  - `frontend/src/app/layout/ShellLayout.tsx`: サイドバーの項目は `href` と、`onClick` で既定の移動を止めて `navigate` する作り。ユーザーメニューの項目は `buildUserMenuItems` から作る
  - `frontend/src/app/registry/types.ts`: `UserMenuItemRegistration`（今は `action` だけ）
  - `frontend/src/shared/api-client/apiClient.ts`: `apiRequest(path, init)`。独自の時間切れを持たない
  - `vendor/make-you-chic-ui`（`git show origin/main:` の読み取りだけ）: 新しい版（`735ef04`）の Dropdown は、`href` を持つ項目を `<a href role="menuitem">` で描き、Enter と Space の両方で `onClick(event)` を呼んでからメニューを閉じ、フォーカスを開き口に戻す。`href` の無い項目は今までどおり `<button role="menuitem">`

## NFR 設計の要点（案）

1. **部品の構成（logical-components）**
   - 置き場は機能設計の2節のとおり、`frontend/src/features/preferences/`（新しい）に、登録・文言・API の関数・`fieldErrors.ts`・`errorMessages.ts`・2つのフック・画面の部品を置く。骨組みの `frontend/src/app/registry/types.ts`・`validateRegistrations.ts`・`frontend/src/app/layout/ShellLayout.tsx` を広げる。
   - 使うだけの部品: U4 の `frontend/src/app/display-settings/`（C9 の口）、U6 の `frontend/src/shared/validation/`、既存の ApiClient、make-you-chic-ui。`features/preferences` はほかの機能を読み込まない。
   - 実際のブラウザの検査と測りは、U7 の新しい検査のファイル（Q1・Q2）に置き、U4 の手伝い（`frontend/e2e/support/` の組の切り替え・axe-core の読み込み・はみ出しの判定・CSP の違反の集め）を使う。手伝いを U7 のために別に作らない。
   - 失敗の範囲はブラウザの1つのタブの中に閉じる。読み込みの失敗は LoadFailed（フォームを出さない）、保存・変更の失敗は画面の知らせで、入れた値と見せ方を残す（機能設計の6節）。
2. **性能（画面を開くときの要求の数、NFR6.4）**
   - プリファレンスの画面は、部品が付いたときに `GET /api/me/preferences` を1回だけ始める。開発時の StrictMode の二重の実行でも、使う答えは最後に始めた読み込みの答えだけにする（読み込みの番号で見分ける、部品 3節）。要求を2本送ることがあっても答えは1つだけ使う。
   - パスワードの変更の画面は、開いても API を呼ばない。
   - 画面の部品は `React.lazy` で遅延読み込みにする（NFR6.6）。初回の JavaScript の大きさは既存の `check-bundle-size.mjs` で U7 の変更の前後を記録する。上限は既存の目安（gzip 500KB、警告だけ）のまま。
3. **性能（送信の間と二重送信の防止、NFR6.5）**
   - 二重送信は、フックの中で送信中の印を描画の外の値（`useRef`）で持ち、要求を始める前に同じ呼び出しの中で立てて防ぐ。状態（`useState`）の更新は次の描画まで見えないため、印を状態だけで持つと、描画の前の2回目のクリック・Enter を止められない。
   - 画面の表示は機能設計の D10 のとおり（Button の `loading`、文字の項目は `readOnly`、ラジオの組は `disabled` にせずフックが選択を受け付けない、「元に戻す」は `disabled`）。
   - 画面の側に独自の時間切れ・再送・中断（AbortController）を足さない。既存の ApiClient も中断を持たない。画面を離れた後・読み直しを始めた後の答えは、部品が付いているかの印と読み込みの番号で捨てる（W2 の6・W10 の5）。保存の送信中に離れたときだけ、200 の答えで `applyUserPreferences` を呼ぶ（W6 の3）。
4. **性能（画面の時間の測り方、NFR6.1〜NFR6.3）**
   - 置き場と利用者は Q1 で決める。どの案でも、測り方は U4 の NFR6.1 の形にそろえる。
     - テストの側の時計で測り、5回の値を注記（`test.info().annotations`）と添付（JSON）で残し、Build and Test の結果に写す。時間で失敗させない（統合の関門にしない）
     - 開く（NFR6.1）: 回ごとに新しいブラウザのコンテキストでログインし（時間に入れない）、ユーザーメニューを開いた後、項目を選んだ直前から、プリファレンスは「保存する」、パスワードの変更は「変更する」のボタンが見えるまで。遅延読み込みの塊を含めるため、回ごとにキャッシュの空の新しいコンテキストを使う
     - 保存（NFR6.2）: 「保存する」を押した直前から、Toast「保存しました」が見えるまで。回ごとにテーマ（`light`・`dark`）を交互に変えて、変更ありの保存にする
     - パスワードの変更（NFR6.3）: 「変更する」を押した直前から、Toast「パスワードを変更しました」が見えるまで。回ごとに変更の前と後のパスワードを交互に使う（U2 の条件つきの更新に当たらないよう、1回ずつ順に送る）
   - 測りは本物の応答で行い、要求を差し替えない（U4 の NFR6.1 と同じ）。
   - 目標を超えたときは緩めず、API・遅延読み込み・描画のどこが遅いかを切り分けて依頼者に相談する（NFR 要件の2節）。長い実行は `caffeinate -i` を付ける。
5. **セキュリティ（個人に関する値とパスワードの値、NFR2.1・NFR9.1）**
   - 氏名とパスワードの値は、フックの状態（メモリ）だけに持つ。U7 はブラウザの保存・Cookie・URL・`console` に触れない。ブラウザの保存は U4 の `applyUserPreferences` だけが行い、書くのは3つの表示の設定だけ（U4 の NFR2.2）。
   - 3つのパスワードの項目は `type="password"` と `autocomplete`（`current-password`・`new-password`）。変更の成功（204）で3つを空に戻す。フォームの `method` と `action` を置かず、送信の出来事で止めて ApiClient で送る（値が URL に載らない）。
   - 確かめは NFR 要件の表のとおり画面部品のテストで行う（読み込み・保存の成功と失敗・変更の前後で、localStorage と sessionStorage のすべての鍵に、テストの氏名・パスワードが無いこと、URL に値が無いこと、`console` の出力が無いこと）。
6. **セキュリティ（応答の値の扱いと項目ごとの誤り、NFR9.2・NFR8.2）**
   - 失敗の文言は `detail` を使わず、`code` と `fieldErrors` の `field`・`reason` から文言の鍵を選ぶ（D13）。応答の値は React の文字としてだけ描く（`react/no-danger` のリンタのまま）。
   - `fieldErrors.ts` は、`ApiError.problem` の `fieldErrors` だけを読む1か所にする。U2 の NFR 設計の3節の形が決まったため、機能設計の 10節の (c)（形は U2 のコード生成で決まる）は、この段で形を当てて読み取りを設計する。ApiClient は変えない（U4 の NFR 設計のとおり）。
     - 配列でない・要素がオブジェクトでない・`field` か `reason` が文字列でないものは捨てる。例外を出さない（NFR 要件の NFR9.6、性質ベースのテスト）
     - `field` は画面ごとの C4 の項目名の union にあるものだけを残す。知らない項目は捨てる。同じ項目が重なれば最初の1つだけ使う
   - 画面の確かめ（U6 の共用の関数）とサーバーの項目ごとの誤りは、同じ理由の union に寄せてから、`errorMessages.ts` の1つの表で文言の鍵にする。サーバーの理由の対応は `REQUIRED` → `required`、`TOO_SHORT` → `tooShort`、`TOO_LONG` → `tooLong`、`INVALID_CHARACTER` → `invalidCharacter`、`MISMATCH` → `mismatch`。`INVALID_VALUE` は選択のまとまりの `preferences.choice.invalid`。知らない理由と、項目と理由の組が表に無いものは、項目ごとの一般の文言（`preferences.field.invalid`）にする（W12 の3）。これで、画面とサーバーの決まりが同じ理由なら同じ文言で出る。
   - 今のパスワードの空の確かめは U7 が持つ（`required`。規則は当てない、W11 の3）。サーバーが `currentPassword` の `REQUIRED` を返したときも同じ文言になる。
   - 対応づけられる誤りが1つも無いときは、画面の知らせ「入力を確かめてください。」を出す（W12 の4）。
7. **セキュリティ（今のパスワードの誤りとログイン状態、NFR9.3）**
   - 400 `PASSWORD_CURRENT_MISMATCH` は ApiClient の 401 の更新の流れに乗らない。画面はログイン状態を変えず、今のパスワードの項目に誤りを結び付ける（W10 の2）。U2 の条件つきの更新（照合の後にほかの変更が確定した）でも同じ code が返り、画面は同じ文言で出す（今のパスワードが今のものでない、という意味で同じ）。
   - 401 は、アクセストークンの期限切れと、U2 の「本人の行が無い」の2つで返る。どちらも既存の ApiClient が更新と送り直しを1回だけ行い、更新もできなければ未ログインになる。画面の側で 401 を別に扱わない（機能設計の6節）。
   - 画面の確かめはサーバーの検証の代わりにしない。サーバーの側の 401・200・204 と入力の検証は U2 のサーバー側のテストで確かめる。
8. **セキュリティと互換（骨組みの変更、NFR9.4・NFR9.8）**
   - `UserMenuItemRegistration` は `action` と `path` のどちらか一方だけの型にし、`validateRegistrations` で「どちらも無い・両方ある」と「登録されていない `path`（ホームを含む登録済みの URL の外）」を誤りにして起動を止める（機能設計の 9節）。`path` は登録済みの画面の URL と完全に一致するものだけを許すため、外の URL（`http:` などの絶対 URL・`//` で始まる URL）へ移る道はできない。
   - `ShellLayout` は、`path` の項目を Dropdown の `href` と `onClick`（`event.preventDefault()` の後に `navigate(path)`）にする。サイドバーの項目と同じ作り。Dropdown の新しい版は、Enter と Space のどちらでも `onClick` を呼ぶため、キーボードでも読み込み直しなしで移る。`action` の項目（既存のログアウト）は今までどおり `<button role="menuitem">` で、既存の E2E の `getByRole('menuitem', { name: 'ログアウト' })` はそのまま通る。
   - 既存の画面への影響は次の3つで確かめる。
     - 骨組みのテスト（`validateRegistrations.test.ts`・`ShellLayout.test.tsx`・`navigationItems.test.ts`）に、`path` の項目・誤りの2つ・ログアウトの項目が変わらず動くこと・`path` の項目を選ぶと読み込み直しなしで移ること（マウスとキーボード）を足す
     - 既存の画面のテストと既存の E2E（010〜040）を変えずに通す。統合の前に `./gradlew e2eTest` を手元で流す（NFR9.8）
     - U7 の実際のブラウザの検査で、パスワードの変更の画面へはユーザーメニューから移る（Q2 A の場合。本物の骨組みの道を1回通す）
   - `ShellLayout` は U4 が B4 でユーザーメニューの名前を氏名にする変更と同じファイルのため、U7 の B5 はその後の `ShellLayout` に足す。
9. **アクセシビリティ（実際のブラウザの検査、NFR7.3・NFR7.4・NFR9.9）**
   - 検査は U4 の作りをそのまま使う: 組 (a) テーマ2×文字の大きさ3、(b) ブランドカラー4×テーマ2（文字の大きさ `md`）、(c) 幅 375px（高さ 812px）でテーマ2×文字の大きさ3。組ごとに新しいコンテキスト。合否は WCAG 2.0・2.1 の A・AA のタグの規則で違反 0 件と、横のはみ出しが無いこと（`scrollWidth` が表示の幅以下）。ブランドカラーは `/api/appearance` の差し替え。
   - 組の切り替えが効いたことを、検査の前に `<html>` の `data-theme`・`data-font-size`・`data-brand` で確かめ、効いていなければ結果を使わずに失敗させる（U4 の 5.3 と同じ）。
   - ログインの後の画面のため、ログインする利用者と、テーマ・文字の大きさの当て方は Q2 で決める。検査する状態は Q3 で決める。
   - 画面部品の側（NFR7.2・NFR7.5）は機能設計のとおり（vitest-axe を画面ごとに1件、RadioGroup の `legend`・選択肢の `lang`、`aria-describedby`・`aria-invalid`、最初の誤りの項目へのフォーカス、Toast と `role="alert"`）。
10. **拡張性・信頼性・観測性（ui のため成果物は作らない）**
    - 状態はブラウザの1つのタブの中だけにあり、サーバーに状態を足さない。サーバーの負荷は、プリファレンスの画面を開くたびの GET 1件と、利用者の操作ごとの PUT・POST 1件だけ。
    - 再試行・サーキットブレーカー・時間切れは画面に置かない（要点 3）。失敗の扱いは機能設計の6節のとおり。
    - 画面の側に独自の指標・ログの送り先を足さない。`console` に氏名・パスワード・応答の値を出さない。
    - 3つの分類の要点は logical-components の節に書く。
11. **テストと依存（NFR9.5〜NFR9.7・NFR9.10）**
    - フロントエンドのカバレッジの下限（行 80%・分岐 70%）を守り、除外を増やさない。`frontend/e2e/` の下の検査と測りは Vitest の計測の対象外（既存の E2E と同じ扱い）で、除外の設定は変えない。
    - 性質ベースのテスト（fast-check、失敗時の既定の報告の `seed`・`path` を出力に残す）を `fieldErrors.ts` に当てる。共用の確かめの関数の境界と性質ベースのテストは U6 が持ち、U7 は差し替えずに画面のテストで境界（11・12 コードポイント、72・73 バイト、絵文字 11・12 文字、確かめの不一致、今のパスワードに規則を当てない）を確かめる。
    - 新しい依存を足さない。make-you-chic-ui の新しい版は B4 の固定先の更新の後に使う。
    - U6 への依存（R-02）: B5 のコード生成の計画で、U6 の `frontend/src/shared/validation/` を U7 より先に作る順と、関数の名前・理由の union（U6 の機能設計の6節）が U7 の `errorMessages.ts` の表と合うことを、計画の承認の前に突き合わせる。この段の成果物には、U6 の6節の名前と理由を前提として書く。

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| 画面の時間の目標は、開く 2 秒・保存 1.5 秒・パスワードの変更 2.5 秒。実際のブラウザで場面ごとに5回測って記録し、統合の関門にしない。パスワードの変更の測りは、測りのテストが自分で作った利用者で、前後のパスワードを交互に使う | NFR6.1〜NFR6.3（Q1: A、共通の決定） |
| 画面の API の時間は U2 の目標で押さえ、U7 では測らない。プリファレンスの画面は開くたびに GET 1回、パスワードの変更の画面は API を呼ばない | NFR6.4 |
| 送信の間は Button の `loading`、要求は1回だけ。画面に独自の時間切れ・再送を足さない | NFR6.5、D10 |
| 2つの画面は遅延読み込み。初回の JavaScript は既存の目安（gzip 500KB、警告だけ） | NFR6.6 |
| 実際のブラウザの検査は U4 の検査に B5 で足し、組は (a) 6組・(b) 8組・(c) 幅 375px の6組。流れの E2E の本数に数えず、前提を自分で作る | NFR7.3・NFR7.4・NFR9.9、U4 の `logical-components.md` 5節 |
| 合否は WCAG 2.0・2.1 の A・AA のタグの規則。axe-core は Node の側で読み `page.evaluate` で評価し、アプリの CSP と `bypassCSP` は変えない。組の切り替えに `<html>` の属性の直接の書き換えを使わない | U4 の NFR 設計の Q2: A と `security-design.md`、`logical-components.md` の 5.3 |
| 検査の要求の差し替え（`page.route`）は、検査のブラウザのコンテキストの中だけで行う | U4 の `logical-components.md` 5.1、U6 の NFR7.5（Q2: B） |
| `fieldErrors` の形は `[{field, reason}]`、reason は6つの値。知らない値は一般の文言で出す | U2 の NFR 設計の `security-design.md` 3節（Q2 A） |
| ApiClient は `fieldErrors` のために変えない。読むのは U7 の `fieldErrors.ts` | U4 の NFR 設計の `security-design.md` |
| 共用の確かめの関数は U6 が作り、U7 は使うだけ。B5 で U6 が先に作る | 機能設計の 10節の (d)・(d2)、NFR9.10 |
| 骨組みに `path` を足し、外の URL へ移る道を作らない。既存のログアウトの項目は変わらず動く | 機能設計の 9節（Q1 A）、NFR9.4 |
| 新しい依存を足さない。make-you-chic-ui は変更しない | NFR9.10、`project.md` の Forbidden |

次の2点は、候補の論点から質問にせず、要点に書いて要約で確かめます。

- `fieldErrors` を項目の下に出す作りと、U6 の共用の関数との関係（要点 6）: U2 の NFR 設計で `fieldErrors` の形と理由の値が決まり、U6 の機能設計で共用の関数の理由の union が決まったため、2つを同じ理由の union に寄せて1つの表で文言にする作りに1つに決まるため。
- 骨組みの変更の既存の画面への影響の確かめ（要点 8）: make-you-chic-ui の新しい版の Dropdown が `href` の項目も `role="menuitem"` で描き、`action` の項目は今までどおり `<button>` のため、既存の E2E の選び方がそのまま通ることをコードで確かめた。確かめ方は既存のテストに足す形で1つに決まるため。

## Q1. 画面の時間の測り（NFR6.1〜NFR6.3）を、どこに置き、どの利用者で測りますか？

理由:

- NFR 要件の承認の場の R-01 は、測りの置き場（`./gradlew e2eTest` の中か、Build and Test の手順か）を B5 のコード生成の計画で決めることを求めています。置き場によって、測りに使う利用者の作り方が変わるため、この段で前倒しで決めます。
- 承認済みの NFR6.3 は「測りのテストが自分で作った利用者」でパスワードを変えるとしています。E2E の WAR には初期管理者しかおらず、利用者を作るには、管理者の招待と、手元の受け手から招待のリンクを取り出して登録を完了する手順が要ります。受け手の手段と、E2E の WAR への SMTP の設定の渡し方は、infrastructure-design の持ち主で、まだ決まっていません（U6 の E2E-1・U5 の検査と同じ前提）。
- 初期管理者のパスワードを変えると、測りの途中で失敗したときにパスワードが元に戻らず、後に動く検査のファイル（B5 で足す U5・U6 の検査など）が初期管理者でログインできなくなります。
- U4 は、最初の画面の時間の測りを、実際のブラウザの検査のファイル（`050-`）の中にテストとして置きました（U4 の `performance-design.md` の3節）。

- A. `./gradlew e2eTest` の中の U7 の検査のファイル（流れの E2E と別のファイル。番号は B5 のコード生成の計画で U5・U6 の検査と合わせて決める）に、3つの場面の測りのテストを置く。測りのテストの中で、初期管理者で招待し、手元の受け手からリンクを取り出して登録を完了し、作った利用者で3つの場面を測る（NFR6.3 のとおり。受け手の手段は U6 の E2E-1 と同じもので、infrastructure-design の決定に頼る）。初期管理者の設定とパスワードは変えない。招待するメールアドレスは実行ごとに重ならない `example.com` の値（推奨）
- B. A と同じ置き場。利用者は作らず、初期管理者で測る。保存はテーマを交互に変えて最後に元の値へ戻し、パスワードの変更は前後のパスワードを交互に使って偶数回で元のパスワードに戻す（6回目の戻しは時間に入れない）。受け手の手段に頼らない代わりに、NFR6.3 の「自分で作った利用者」と違う（上流との差に記録する）。途中で失敗すると初期管理者のパスワードが戻らず、後のファイルが落ちうる
- C. e2eTest に測りのテストを足さず、Build and Test の手順書に、手元のブラウザで測る手順（作った利用者で、開発者の道具の時間を読む）を書いて、その段で手で5回ずつ測る
- X. Other (please specify)

[Answer]: A

## Q2. ログインの後の2つの画面の実際のブラウザの検査で、ログインする利用者と、テーマ・文字の大きさの組の当て方をどうしますか？

理由:

- U4 の検査は、ログインの前の画面のため、テーマ・文字の大きさを U4 の鍵（`mastersmith.display-settings`）を初めのスクリプトで置いて切り替えます（U4 の 5.3）。ログインの後は、ログインの応答の利用者の設定（内部DB の値）が当たり、ブラウザの保存の値は使われません（U4 の W5・D8）。そのため U4 の切り替え方はログインの後の画面では効きません。
- さらに、プリファレンスの画面は開くと `GET /api/me/preferences` の値で当たっている値をそろえます（D2・W3 の3）。
- 組は画面ごとに 20 組（2つの画面で 40 組）です。組ごとに新しいコンテキストでログインします（ログインの成功は監査に残りますが、E2E の使い捨ての内部DB です）。
- 検査の目的は描画・色・はみ出しの確かめで、サーバーの動きの確かめではありません。本物の保存と応答の形の確かめは、画面部品のテスト・U2 のサーバー側のテスト・Q1 の測りで行います。

- A. 初期管理者で組ごとにログインし、そのコンテキストの中だけで `GET /api/me/preferences` の答えを差し替えて（`page.route`、200 で組のテーマ・文字の大きさ、氏名と言語は固定のテストの値）、プリファレンスの画面を開く。画面の D2 のそろえ（`applyUserPreferences`）という本物の道で組が当たる。パスワードの変更の画面は、組を当てたプリファレンスの画面からユーザーメニューで移って検査する（当てた値のまま移り、骨組みの `path` の道も通る）。ブランドカラーは U4 と同じく `/api/appearance` の差し替え。サーバーの利用者の設定は変えず、受け手の手段にも頼らない（推奨）
- B. Q1 で作る利用者（または検査の中で同じ手順で作る利用者）で、組ごとに本物の保存（`PUT /api/me/preferences`）で利用者の設定を変えてからログインし直して開く。差し替えない代わりに、受け手の手段（infrastructure-design）に頼り、組ごとの保存とログインが増える
- C. 初期管理者で、組ごとに本物の保存で設定を変えてから開き、終わりに元の値へ戻す。受け手の手段に頼らない代わりに、途中で失敗すると初期管理者の設定が戻らず、後のファイルの表示の前提が変わりうる
- X. Other (please specify)

[Answer]: A

## Q3. 実際のブラウザの検査で、どの状態を検査しますか？

理由:

- NFR7.3・NFR7.4 は「画面ごとに組を切り替えて検査する」とだけ決めており、状態を決めていません。U5 は一覧（行あり）と2つの Modal、U6 はフォームと「リンクが使えない」の状態を挙げています。
- 項目の誤り（赤系の文字）・`aria-invalid` の枠・画面の知らせ（Alert の danger）は、テーマとブランドカラーの組でコントラストが変わります。vitest-axe（jsdom）は構造を見ますが、色のコントラストは計算しません（`color-contrast` は実際のブラウザでだけ判定できる）。
- 画面の確かめの誤りは、送信のときに画面の中だけで出て、要求を送りません（D7）。検査の中で出しても、サーバーの状態を変えません。
- 誤りの状態を足すとき、同じコンテキストで最初の状態を検査した後に誤りを出して続けて検査すれば、ログインの回数は増えません（検査の回数は2倍）。
- サーバーが返したときだけの選択のまとまりの誤り（`legend` の中の誤り、W12 の3）は、画面の確かめでは出ません。出すには `PUT /api/me/preferences` の答えを 400 `VALIDATION_FAILED` の `fieldErrors` に差し替えます。

- A. 各画面で2つの状態を検査する。(1) 最初の状態（プリファレンスは値の入ったフォーム、パスワードの変更は空の3つの項目）、(2) 画面の確かめの誤りを出した状態（プリファレンスは氏名を空にして「保存する」、パスワードの変更は3つを空のまま「変更する」。要求を送らない）。(2) は (1) と同じコンテキストで続けて検査する。サーバーの選択の誤りの状態と、Toast・画面の知らせの状態は vitest-axe に任せる（推奨）
- B. A に加えて、プリファレンスの画面で `PUT /api/me/preferences` の答えを 400 `VALIDATION_FAILED`（`fieldErrors` に `theme` の `INVALID_VALUE` と `displayName` の `TOO_LONG`）に差し替え、選択のまとまりの `legend` の中の誤りと画面の知らせを出した状態も検査する
- C. 最初の状態だけを検査する（誤りの状態は vitest-axe に任せる）
- X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめ（Q1〜Q3 の回答の後に埋めます）:

- NFR 設計の要点（案）は冒頭の 11 件のとおり。主な点は次のとおり。
  - 読み込みは部品が付いたときに1回だけ始め、最後に始めた読み込みの答えだけを使う。パスワードの変更の画面は API を呼ばない
  - 二重送信は描画の外の送信中の印（`useRef`）で防ぐ。画面に時間切れ・再送・中断を足さない
  - 画面の時間はテストの側の時計で5回測り、注記と添付で残して関門にしない（U4 の NFR6.1 の形）。本物の応答で測る
  - `fieldErrors.ts` は U2 の形 `[{field, reason}]` だけを読み、壊れた入力を捨てる。画面の確かめとサーバーの理由を同じ union に寄せ、`errorMessages.ts` の1つの表で文言にする。知らない理由は一般の文言
  - 今のパスワードの誤り（条件つきの更新の場合を含む）はログイン状態を変えない。401 は既存の ApiClient に任せる
  - 骨組みの `path` は登録済みの URL の完全な一致だけを許す。`ShellLayout` はサイドバーと同じ `href`・`onClick` の作り。既存のログアウトの項目と既存の E2E はそのまま
  - 実際のブラウザの検査は U4 の組・合否・手伝いをそのまま使う
  - 拡張性・信頼性・観測性の成果物は作らない
  - U6 の共用の関数の名前と理由の union が U7 の表と合うことを、B5 のコード生成の計画の承認の前に突き合わせる（R-02）
- Q1 A: 画面の時間の測りは `./gradlew e2eTest` の中の U7 の検査のファイル（流れの E2E とは別）に置き、測りのテストの中で初期管理者が招待し、受け手からリンクを取り出して登録を完了し、作った利用者で3つの場面を測る（承認済みの NFR6.3 のとおり）。初期管理者の設定とパスワードは変えない。受け手の手段は infrastructure-design の決定に頼る
- Q2 A: ログインの後の2つの画面の検査は、初期管理者でログインし、そのコンテキストの中だけで `GET /api/me/preferences` の答えを組の値に差し替える（開いたときのそろえ D2 の本物の道で当たる）。パスワードの変更の画面へはユーザーメニューから移り、骨組みの `path` の道も通る。サーバーの状態は変えない
- Q3 A: 実際のブラウザの検査は、最初の状態に加えて、画面の確かめの誤りを出した状態も検査する（要求は送らない。同じコンテキストで続けるのでログインの回数は増えない）

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
