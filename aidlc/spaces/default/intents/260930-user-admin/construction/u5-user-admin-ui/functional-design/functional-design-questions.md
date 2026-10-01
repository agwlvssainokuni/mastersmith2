# Functional Design の質問 — u5-user-admin-ui

単位 U5（`aidlc/spaces/default/intents/260930-user-admin/inception/units-generation/unit-of-work.md`、種類 ui）の機能設計のための質問です。受け持つのは US1.1・US2.1・US3.1・US4.1・US5.1 の画面の側（AC1.1.8〜AC1.1.12・AC2.1.8・AC2.1.9・AC2.1.13・AC3.1.7・AC3.1.8・AC3.1.11・AC4.1.9・AC4.1.10 の画面の部分・AC5.1.2・AC5.1.3・AC5.1.7）と、US2.2 の共通の扱いを使うこと、E2E の代表の流れ（M9 A）です（`aidlc/spaces/default/intents/260930-user-admin/inception/user-stories/stories.md`）。画面は `aidlc/spaces/default/intents/260930-user-admin/inception/refined-mockups/` の S1〜S6 と操作の仕様、契約は C3（管理の API）・C4（403 の共通の扱いと自分の氏名と言語の反映）・C5（画面のページ送り）（`aidlc/spaces/default/intents/260930-user-admin/inception/contract-design/contract-summary.md`）、Bolt は B5 です。既存のコードは `frontend/src/features/invitation/`（一覧・状態のフック・失敗の文言・登録）・`frontend/src/features/preferences/fieldErrors.ts`・`frontend/src/shared/`（ApiClient・日時・氏名の確かめ）・`frontend/src/app/`（機能の登録）・`frontend/e2e/`（090 の流れと `support/` の手伝い）・`frontend/playwright.config.ts`・`backend/src/main/resources/application.yaml`（ロックのしきい値）と、`vendor/make-you-chic-ui` の `Dropdown`・`Table`・`Modal`・`AppShell` を読んで確かめました。

## 設計の要点（案）

上流の決定とコードの確認から導ける、この単位の機能設計の見通しです。質問の答えで決まる点は（Qn）と書きます。

1. **置き場と登録**: 新しい機能 `frontend/src/features/useradmin/` に置き、`registration.ts` で登録する。画面の URL は `/admin/users`、`layout: 'SHELL'`・`access: 'ADMIN'`、サイドバーは「利用者の管理」（en: Users）を `visibleWhen: 'ADMIN'`・order 230（招待の 220 の次）に置き、画面は遅延読み込み（招待・DSL と同じ `lazy`）にする。骨組み（`src/app/`）のファイルは書き換えない。
2. **部品の構成**: 操作の仕様の部品の名前（UserAdminPage・UserSearchBox・UserTable・UserRowActions・ConfirmActionDialog・EditProfileDialog）を正式の名前にする。状態と操作は1つのフック `useUserAdmin`（招待の `useInvitationAdmin` の形）に集め、API の関数の集まり（テストで差し替える）・応答の型・文言（`messages.ts`、ja・en）・失敗の code から文言の鍵を選ぶ関数を機能の中に置く。権限が無いときの表示（S6）は U4 の共通の表示（C4 の `AdminForbiddenView`・`useAdminForbidden`）を使い、U5 では作らない。
3. **画面の状態**: loading（初め）・reloading（前の行を残し、行の操作を押せない）・populated・empty-search・empty-page・load-error の6つを持つ。読み直しごとに番号を増やし、最後に始めた読み直しの答えだけを使う（招待の前例）。読み直しの間に前の行を残す点は画面イメージ 3.2 の決定で、行を空にする招待の前例とは違う。ページの番号と検索の文字の持ち方は（Q1）。
4. **ロックの表示**: 一覧の応答の `locked`・`lockedUntil` をそのまま表示に使う。開いたまま解除の予定の時刻を過ぎたときの表示は（Q2）。
5. **行の操作のメニュー**: 出す項目と押せない項目は、`AdminUser` の `admin`・`suspended`・`resettable`・`self` だけから決める1つの純粋な関数にする（画面イメージ 4節の表）。性質ベースのテスト（fast-check）の対象にする（例: `self` の行では「印を外す」「止める」が必ず押せない形、停止中の行には印の項目が出ない、`resettable` が偽なら「ロックを解除」が出ない）。メニューは固定先を更新した make-you-chic-ui の `Dropdown` の `disabled`・`description` で作る。
6. **失敗の扱い**: 応答の `detail`・`title` は使わず、`code` から画面の文言の鍵を選ぶ（招待・DSL の前例）。文言は画面イメージ S5 の表のとおりで、氏名は画面が持つ行の値から差し込む。
   - 404 `USER_NOT_FOUND`・409 `USER_ADMIN_SELF_OPERATION`・`USER_ADMIN_TARGET_SUSPENDED`・`USER_ADMIN_NO_CHANGE`・`USER_ADMIN_LAST_ADMIN`・`USER_ADMIN_BUSY` → 一覧の上の残る `Alert`（warning）。S4 の中の 404 は表示の中に出す。
   - 400 `VALIDATION_FAILED` → 一覧の要求なら検索の入力欄の下、氏名・言語の保存なら項目の下（項目ごとの誤りの読み方は Q3）。
   - 403 → `useAdminForbidden`（C4）に渡す。401 → 既存の ApiClient に任せ、画面の文言を出さない。5xx・通信の失敗・知らない code → 一般の文言。
   - 成功・失敗のどちらでも一覧を読み直し（今のページと検索の文字を保つ）、ページが空になったら `correctedPage`（C5）で最後のページへ移る。
7. **自分の行の氏名・言語**: 保存が成功し、対象が自分の行（`self`）なら、送った値を `useApplyOwnProfile`（C4）に渡して画面の言語と上の帯の氏名を変える（AC5.1.3）。
8. **氏名・言語の入力（S4）**: 画面の確かめは既存の `src/shared/validation/validateDisplayName.ts`（前後の空白を除いて空・254 コードポイント超え・Cc と Cf の文字）を使い、判定はサーバーを正とする。文言は機能の `messages.ts` に置く。
9. **検索（S1、〔Should〕）**: 長さの上限は U3 の機能設計で決める値（契約の未解決の点）と同じ値を画面の定数に置き、入力欄で上限を超えて入れられないようにする（AC1.1.11）。空なら `q` を付けない。検索したら1ページ目に戻る。
10. **日時**: 登録した日時と解除の予定の時刻は既存の `src/shared/format/formatDateTime.ts` の形で、利用者の言語とブラウザの時間帯で表示する。解除の予定の時刻は、今日なら時刻だけ、今日でなければ日付つき（画面イメージ 3.1）。
11. **テスト**: 画面部品ごとに vitest-axe の検査を `accessibility-checklist.md` 5節の状態で入れる（2語の氏名・誤りの状態を含む）。描画の後に反映される値は `waitFor` で待って確かめる（`team.md`）。E2E は `frontend/e2e/` に新しいファイルを1本足す（番号は 110、既存の 010〜100 は変えない）。E2E の利用者とロックの作り方は（Q4）（Q5）。
12. **リポジトリの作業**: make-you-chic-ui の固定先を `3481488` 以降に上げる（承認を得た専用のコミット、更新前後のハッシュを記録、fast-forward で統合）。`frontend/.npmrc` は今 `engine-strict=true` だけなので、`ignore-scripts=true` を足し、`vendor/make-you-chic-ui` のインストールとビルドが通ることを確かめる。
13. **コードで分かった注意**:
    - 手元のサブモジュールは今の固定先 `077f5b4` で、`Dropdown` の `MenuItem` は `label`・`href`・`onClick` だけを持つ。`3481488` は手元にまだ取り込まれていないため、`disabled`・`description` の実際の形は固定先を更新するときに確かめる。
    - `Dropdown` の開き口には `data-testid="dropdown-trigger"`・`aria-haspopup="true"` が固定で付き、表の行ごとに置くと同じ `data-testid` が並ぶ。テストと E2E は開き口を読み上げの名前（「〔氏名〕（〔メールアドレス〕）の操作」）と役割で探す。
    - 既存の E2E の手伝い `support/registeredUser.ts` の `openUserMenuItem` は `app-shell` の中の `dropdown-trigger` を1つと決めて探す。`app-shell` は本文（`app-shell-content`）を含むため、利用者の管理の画面を開いたままこの手伝いを使うと、複数に当たって失敗する。新しい E2E では、この画面でこの手伝いを使わない。
    - `Table` は `caption` と行ごとの `data-testid` を持たない。表の名前は `aria-label`（「利用者の一覧」）で付ける（招待の前例）。

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| 画面は AppShell の中の1画面。確かめ（S3）と氏名・言語の入力（S4）は Modal で重ね、画面は移らない | 画面イメージ 2節（RQ1 A・RQ3 A） |
| 一覧は登録した日時の古い順・1ページ 20 件。管理者・状態・ロック中・「あなた」は `Badge` の文字でも示し、無いときは「—」（読み上げは「なし」） | 画面イメージ 3.1、AC1.1.1、D3・D6 |
| 一覧は make-you-chic-ui の `Table` で作り、ページ送りと空の文言は `labels` で利用者の言語を渡す（招待の一覧と同じ。DSL の画面で素の table にした理由のページ送りの文言の固定は、`labels` で解ける） | `design-system-mapping.md` 1節・3節、`frontend/src/features/invitation/InvitationList.tsx` |
| 行の操作は1つのメニュー。出す条件と、自分の行の「印を外す」「止める」を押せない形で理由とともに出すこと | 画面イメージ 4節（RQ2 A）、M6 B、AC2.1.9・AC3.1.8 |
| 行の操作のメニューは make-you-chic-ui の `Dropdown` を使い、固定先を `3481488` 以降に上げる。frontend の側で自前のメニューは作らない | `unit-of-work.md` の U5（UQ2 A）、`bolt-plan.md` の B5 |
| 確かめの表示は5種類。見出し・効き目の文・ボタンの文言と見た目、はじめのフォーカスは「やめる」、背景のクリックで閉じない、閉じたら同じ利用者の行の「操作」へ戻す | 画面イメージ 5節、M4 B |
| 氏名・言語の入力は今の値で開き、はじめのフォーカスは氏名。誤りは入力欄のすぐ下で、入力は消さない。メールアドレス・パスワード・テーマ・文字の大きさは置かない | 画面イメージ 6節、AC5.1.6・AC5.1.7 |
| 成功は `Toast`、業務の拒否は一覧の上に残る `Alert`（warning）。理由ごとの文言（`USER_ADMIN_BUSY` の「ほかの処理と重なったため、操作できませんでした。少し待ってから、もう一度操作してください。」を含む） | 画面イメージ 7節（S5） |
| 成功の応答は本文なしの 204。画面は成功・失敗のどちらでも一覧を読み直し、今のページと検索の文字を保つ | C3（Q2 A）、画面イメージ 7節 |
| 読み直しで今のページが空になったら `correctedPage` で最後のページへ移る（氏名の変更で検索に当たらなくなったときなど） | C5 |
| 403 は `useAdminForbidden`（C4）に渡し、共通の表示に置き換わる。開いていた確かめ・入力の表示は閉じる。401 は既存の ApiClient のまま | C4、画面イメージ 8節、AC2.2.1 |
| 自分の氏名と言語の変更は `useApplyOwnProfile`（C4）で画面に当てる。テーマと文字の大きさは変えない | C4、AC5.1.3 |
| 失敗の文言は画面が `code` から選び、サーバーの `detail`・`title` を画面に出さない | 招待の `failureMessage.ts` の前例 |
| 応答の日時は ISO 8601 の UTC で受け、利用者の言語の書式とブラウザの時間帯で表示する | C3 の共通の決まり、ストーリーの画面の共通の決まり |
| 狭い画面向けの形は作らない。表がはみ出したら横にずれて読めることだけを確かめる | 画面イメージ 2節（RQ7 C） |
| 送信から 5 秒を過ぎたら確かめ・入力の表示の中に「時間がかかっています」を出す。`prefers-reduced-motion` では開閉の動きを出さない | 操作の仕様 3節 |
| 検索の長さの上限の値は U3 の機能設計で決め、画面は同じ値を使う | C3 の Open questions、AC1.1.4・AC1.1.11 |
| 実際のブラウザでのアクセシビリティの検査（Playwright と axe）を足すかは、NFR 要件の段で決める（前の Intent でも NFR 要件の段で決めた）。足しても E2E の本数には数えない | AC1.1.12、`accessibility-checklist.md`、`project.md` の学び |
| E2E は代表の流れを1本。初期管理者でログインし、流れの中で作った利用者 U を検索で探し、ロック → 失敗回数を戻す → 止める → ログインできない → 停止を解く → ログインできる。印の付け外しは入れない。初期管理者の状態は変えない | ストーリーの E2E の代表の流れ（M9 A）、`team.md`、`bolt-plan.md` の B5 |
| 検索（B3）は B5 より前に作られるため、E2E は U を検索で探せる | `bolt-plan.md`、ストーリーの申し送り |
| 統合の前に E2E を手元で流す。サブモジュールの固定先の更新を含むため fast-forward で統合してよい | `bolt-plan.md`、`team.md` |

---

## Q1. ページの番号と検索の文字の持ち方

招待の管理の画面は、今のページを画面の中だけに持ち、開くたびに1ページ目から始めます（`useInvitationAdmin.ts`。URL とブラウザの保存は使わない）。今の画面で URL の問い合わせ（`?page=`）に状態を持つものはありません。利用者の管理の画面では検索の文字（メールアドレスや氏名の一部）も持つため、URL に持つとブラウザの履歴やブックマークに個人に関する値が残ります。
理由: 読み直し・戻る・画面の再読み込みのときにどこから表示するかと、検索の文字の置き場が、画面イメージと操作の仕様で決まっていないため。

A. 画面の中だけに持つ（招待と同じ。開く・再読み込みのたびに1ページ目・検索なしから始める。検索の文字が URL と履歴に残らない）（推奨: 前例と同じで、個人に関する値を URL に出さないため）
B. ページの番号と検索の文字の両方を URL の問い合わせ（`?page=&q=`）に持つ（再読み込み・戻るで同じ表示に戻る。検索の文字が履歴に残る）
C. ページの番号だけを URL に持ち、検索の文字は画面の中だけに持つ
X. Other (please specify)

[Answer]: A

## Q2. 開いたまま解除の予定の時刻を過ぎたときのロックの表示

一覧の `locked` はサーバーが要求の時点の時刻で判定した値です（FR1.2、AC1.1.2）。画面は決まった間隔では読み直しません（ストーリーの画面の共通の決まり）。そのため、一覧を開いたまま解除の予定の時刻を過ぎると、次の読み直しまで「ロック中 〇〇まで」が残ります。過ぎた後も失敗回数は残るので `resettable` は真のままで、「ロックを解除（失敗回数を戻す）」の操作は成功します（M2 B）。
理由: 画面イメージ 3.1 の「解除の予定の時刻ちょうど以後はロック中と示さない」を、画面の表示の時点でも守るかが決まっていないため。

A. 応答の値をそのまま表示し、次の読み直しまで変えない（サーバーの判定を正とする。操作の後の読み直しで今の状態になる）（推奨: ブラウザの時計とサーバーの時計のずれで誤った表示をしないため。過ぎた後の操作も成功し害が無い）
B. 画面がブラウザの時計で解除の予定の時刻を過ぎたかを見て、過ぎたら「ロック中」の印を消す（時計のずれがあると、まだロック中の利用者を外れたように見せうる）
C. 解除の予定の時刻を過ぎたら、一覧を自動で1回読み直す
X. Other (please specify)

[Answer]: A

## Q3. 氏名・言語の保存の入力の誤り（400）を項目に結び付ける読み方

サーバーの `VALIDATION_FAILED` は項目ごとの誤り `fieldErrors: [{ field, reason }]` を持ちます。これを読む関数は今 `frontend/src/features/preferences/fieldErrors.ts`（プリファレンスの機能の中）にあり、決まり（Code Style）では機能どうしは直接 import し合わず、共有するものは `src/shared/` へ移します。画面イメージ 6節は、サーバーの入力の誤りも氏名・言語の入力欄のすぐ下に出すとしています。
理由: S4 でサーバーの項目ごとの誤りを読む関数の置き場が決まっていないため（プリファレンスの機能に手が入るかが変わる）。

A. `fieldErrors.ts` を `src/shared/`（`api-client` の隣など）へ移し、プリファレンスと利用者の管理の両方が使う（プリファレンスの import とテストの置き場を直す。振る舞いは変えない）（推奨: 決まりどおり1か所にでき、同じ形を2つ持たないため）
B. 利用者の管理の機能の中に、同じ形の小さな読み取りの関数を別に作る（プリファレンスには手を入れない。同じ形が2か所になる）
C. 項目に結び付けず、サーバーの 400 は入力の表示の中の一般の文言で示す（画面の確かめで同じ決まりを先に見るため、サーバーの 400 はまれ。画面イメージ 6節の「同じ場所に示す」とは違う）
X. Other (please specify)

[Answer]: A

## Q4. E2E で使う利用者 U の作り方

E2E は1つの WAR・内部DB・初期管理者を全ファイルで共有し、状態を変える操作は流れの中で自分で作った利用者だけを対象にします（`team.md`）。既存の手伝い `support/registeredUser.ts` の `createRegisteredUser` は、招待の API → Mailpit からリンクを取り出す → 登録の完了の API で利用者を作り、宛先 `u7-perf-<印>@example.com`・氏名「計測 花子」（2語）・パスワード（実行ごとの乱数）を返します（080 が使う）。宛先の印は実行ごとに重ならないため、検索に印を入れれば U だけが当たります。
理由: 流れの1の「招待して登録させた利用者 U」をどの道で作るかが、ストーリーと Bolt の計画で決まっていないため。

A. 既存の `createRegisteredUser` をそのまま使い（手伝いは変えない）、画面の検索には宛先の印を入れて U を探す（推奨: 招待と登録の画面の流れは 090 がすでに確かめており、速く、既存の手伝いを変えずに済むため）
B. `createRegisteredUser` に宛先の前置きと氏名を渡せる省略できる引数を足し、この流れの名前（例 `e2e-useradmin-<印>@example.com`）で作る（080 の呼び方は変えない）
C. 招待の画面と登録の完了の画面を画面の操作で通して作る（090 と同じ流れをもう一度通すため時間が延びる）
X. Other (please specify)

[Answer]: A

## Q5. E2E で U をロックする方法

ロックのしきい値は既定 5 回（`application.yaml` の `MASTERSMITH_AUTH_LOCK_THRESHOLD`）で、E2E の起動の設定（`playwright.config.ts` の `webServer`）はこの値を渡していません。しきい値を下げる設定は全ファイルの共有の WAR に効くため、ほかの E2E（020 のパスワードの誤りなど）の前提に触れます。流れの3（失敗回数を戻した後に正しいパスワードで入れる）と4（止めた後のログインの拒否）は、どちらも U のブラウザの画面で確かめる予定です。
理由: 流れの2の「U がパスワードをしきい値の回数だけ誤る」をどの道で行うかが決まっていないため。

A. Playwright の要求の口で、U の誤ったパスワードでログインの API をしきい値の回数（定数 5。設定を渡していないことを手伝いの注に書く）呼び、すべて 401 であることを確かめる。そのあと管理者の画面で U がロック中と表示されることを確かめる（推奨: 速く、確かめたいのは管理者の画面の表示と解除であり、ログインの画面の流れは3・4で通すため）
B. U のブラウザのログインの画面で、しきい値の回数だけ誤ったパスワードを入れる（画面の流れとして通す。時間がやや延び、最後の回のロックの文言も画面で見る）
C. E2E の起動の設定でしきい値を小さな値（例 2）にして、A か B で誤る（全ファイルに効くため、ほかの E2E の前提を確かめ直す）
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめと、U5 の機能設計の計画:

- 設計の要点（案）の 1〜13 のとおりに作る（S1〜S6 の画面の状態、行の操作のメニュー、5つの確かめの表示、code ごとの文言、操作の後の読み直しと `correctedPage`、make-you-chic-ui の Table と Dropdown、アクセシビリティ、固定先の更新と `ignore-scripts`）。
- Q1 A: ページの番号と検索の文字は画面の中だけに持つ。開く・再読み込みのたびに1ページ目・検索なしから始める。
- Q2 A: ロックの表示は応答の値のまま出し、次の読み直しまで変えない。
- Q3 A: `features/preferences/fieldErrors.ts` を `src/shared/` へ移し、プリファレンスと利用者の管理の両方で使う。プリファレンスの振る舞いは変えない。
- Q4 A: E2E の利用者 U は、既存の `createRegisteredUser` をそのまま使って作る。画面の検索には宛先の印を入れて U を探す。
- Q5 A: E2E のロックは、ログインの API を誤ったパスワードで5回呼び、すべて 401 であることを確かめてから、管理者の画面でロック中の表示を確かめる。しきい値の設定は変えない。
- 検索の上限は、U3 の機能設計で決めた 254 コードポイントを画面でも使う（AC1.1.11）。前後の空白を除いてから数える。
- コードで分かった注意点:
  - Dropdown の `disabled`・`description` の実際の形は、固定先を `3481488` 以降に上げるときに確かめる（今の固定先は `077f5b4`）。
  - 行ごとの開き口は同じ `data-testid` が並ぶため、テストと E2E は読み上げの名前と役割で探す。
  - 既存の E2E の手伝い `openUserMenuItem` は、この画面では使わない。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
