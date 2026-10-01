# Tech Stack Decisions — U5 利用者の管理の画面（u5-user-admin-ui）

この文書は、U5 の技術の選定と、依存・アクセシビリティ・多言語・テストの要件をまとめたものです。技術は前の Intent とこの Intent の U4 から変えません（React・TypeScript・Vite・i18next・make-you-chic-ui・Vitest・Testing Library・user-event・vitest-axe・fast-check・Playwright・axe-core。`aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`）。U5 は新しい npm の依存を足しません。答えは `nfr-requirements-questions.md` にあります（Q1: A、Q2: B、Consolidated Summary Confirmation: Looks correct）。

出典の略号:
- NFR・FR は要件 `aidlc/spaces/default/intents/260930-user-admin/inception/requirements-analysis/requirements.md` の ID です。
- D・W・4節〜10節は、この単位の `construction/u5-user-admin-ui/functional-design/functional-spec.md` の節です。「承認の場」は同じ文書の「承認の場の決定」の節です。
- 「部品 n節」は、同じフォルダの `frontend-components.md` の節です。
- Q と「要点 n」は、この段の `nfr-requirements-questions.md` の番号です。
- U4 の NFRx.y は `construction/u4-admin-forbidden-ui/nfr-requirements/` の ID です。
- PM は `aidlc/spaces/default/memory/project.md`、TM は `aidlc/spaces/default/memory/team.md` です。

枝番はこの単位の中で振ります。ほかの要件の置き場は `performance-requirements.md` の冒頭の表のとおりです。依存・`.npmrc`・テスト・E2E の要件は、上流に当たる ID が無い（または要件の NFR9 のテストの要件に当たる）ため NFR9 の枝番に寄せました。

## 1. 選定

| 対象 | 選定 | 理由 |
|---|---|---|
| 画面の置き場 | `frontend/src/features/useradmin/` を新しく置く。共通のもの（ページ送り `src/shared/paging/`、日時の書式 `src/shared/format/`、氏名の確かめ `src/shared/validation/`、項目ごとの誤り `src/shared/api-client/fieldErrors.ts`）だけを `src/shared/` から読む | 機能設計 2節、TM の Code Style（機能どうしは直接 import し合わない） |
| 表・メニュー・Modal・ボタン・知らせ | make-you-chic-ui の `Table`（`labels` で ja・en）・`Dropdown`（押せない項目と理由の文）・`Modal`（`closeLabel`、背景のクリックで閉じない）・`Button`（`loading`）・`Badge`・`Alert`・`Toast`。固定先は `3481488` 以降の具体のコミットを B5 のコード生成の計画で決める（NFR9.3） | 機能設計 D6・D7・D13・D19、部品 2.4・2.5 |
| 403 と自分の氏名・言語の反映 | U4 の口 `useAdminForbidden`・`useApplyOwnProfile`（契約 C4） | 機能設計 D11・D17 |
| API | 既存の ApiClient（`frontend/src/shared/api-client/`）を通す。独自の時間切れを置かない | 機能設計 2節、`performance-requirements.md` の NFR5.5 |
| 文言 | 既存の i18next・react-i18next（鍵は `useradmin.`） | NFR8.1 |
| 画面部品のテスト | Vitest・Testing Library（jsdom）・user-event・vitest-axe（既存） | TM の Testing Posture、NFR7.2 |
| 性質ベースのテスト | fast-check（既存） | NFR9.6 |
| 実際のブラウザのアクセシビリティの検査と画面の時間の測り | Playwright（既存）＋ axe-core（既存の devDependencies）。組は既存の `frontend/e2e/support/displayCombos.ts` の 20 組 | Q1 A、Q2 B、NFR7.3、`performance-requirements.md` の NFR5.1・NFR5.2 |
| 初回の JavaScript の大きさの確かめ | 既存の `frontend/scripts/check-bundle-size.mjs` | `performance-requirements.md` の NFR5.6 |

## 2. 依存・make-you-chic-ui・`.npmrc`

| ID | 要件 | 確かめ方と持ち主の段 | 出典 |
|---|---|---|---|
| NFR9.2 | U5 は新しい npm の依存（実行時・開発時とも）を足さない。`frontend/package.json` と `frontend/package-lock.json` の差分は、make-you-chic-ui の固定先の更新に伴う分の外に持たない。axe-core・fast-check・Playwright は既存のものを使う | コード生成で、`frontend/package.json` の差分が無いこと、lockfile の差分が make-you-chic-ui の版の分だけであることを確かめて記録する | 要点 15、TM の Code Style（新しい依存のライセンス） |
| NFR9.3 | make-you-chic-ui の固定先を `077f5b4` から `3481488` 以降へ上げる。取り込む具体のコミット（`3481488` 以降のどれか）は、B5 のコード生成の計画で決め、計画に記録する（計画の承認の前に、そのコミットに部品 2.4・2.5 の口があることを確かめる）。中身は変えず、承認を得た専用のコミットで更新前後のコミットのハッシュを記録する。上げた直後（`UserRowActions` を書く前）に部品 2.5 の表を部品のソースとテストで確かめ、「依頼者に戻す差」が出たら依頼者に報告して扱いを決めてもらう。上げた版で、既存の画面（招待の一覧・DSL・ShellLayout のユーザーメニューなど）の部品のテストと E2E（`frontend/e2e/` の 010〜100 と U4 の 130）が通る。`vendor/make-you-chic-ui` の lockfile は今までどおり OSV-Scanner の対象に含める | コード生成（B5）で、計画で決めたコミットへの固定先の更新を専用のコミットにし、前後のハッシュを記録する。固定先の更新と NFR9.4 の `.npmrc` の変更は別のコミットに分け、それぞれの変更の後に `npm ci` と `./gradlew e2eTest` を通す（失敗の原因を切り分けるため）。2.5 の表の確かめの結果を記録する。`./gradlew verify`（OSV-Scanner を含む）と `./gradlew e2eTest` を流して記録する。`vendor/make-you-chic-ui` の中身に差分が無いことを確かめる | 要点 16、機能設計 2節・承認の場（R-03）、部品 2.4・2.5、PM の Forbidden・Mandated、TM の Way of Working（サブモジュールの更新は fast-forward で統合してよい） |
| NFR9.4 | `frontend/.npmrc` に `ignore-scripts=true` を足す（今の `engine-strict=true` は残す）。この設定が効くのは `frontend/` の中で行う npm のインストール（`npm ci` など）の範囲だけで、`vendor/make-you-chic-ui` の中のインストールには効かせない（`vendor/` の中身は変えられない、PM の Forbidden）。`vendor/make-you-chic-ui` のインストールとビルドは今までどおりの手順のまま通ることだけを確かめる。足した後も、`frontend/` の `npm ci`・フロントエンドのビルド・Playwright の E2E（インストール時のスクリプトに頼る部分が無いこと）・`./gradlew verify`・CI が通る。`.npmrc` の変更は、NFR9.3 の固定先の更新と別のコミットにする | コード生成で、`.npmrc` を変えたコミットの後に `npm ci`・`./gradlew verify`・`./gradlew e2eTest` を流して記録する（固定先の更新のコミットの後とは別に流す）。Build and Test で CI の結果を記録する | 要点 17、要件 4節の制約、TM の Code Style（npm のパッケージのスクリプトを動かさない）、Bolt B5、承認の場（R-05） |

## 3. アクセシビリティ

| ID | 要件 | 確かめ方と持ち主の段 | 出典 |
|---|---|---|---|
| NFR7.1 | 目標は WCAG 2.1 AA とする。U5 の画面では次を守る。管理者の印・状態（有効・利用停止）・ロック中・「あなた」は、色に加えて `Badge` の文字で示し、印が無い・ロックしていないは「—」で示して読み上げを「なし」にする。自分の行の押せない項目は `aria-disabled="true"` と理由の文を `aria-describedby` で結ぶ。確かめの表示は背景のクリックで閉じず、はじめのフォーカスを「やめる」に置き、本文の先頭に対象と効き目の文を置く。行の「操作」の読み上げの名前に対象の氏名とメールアドレスを入れる。読み直しの間も行の「操作」のフォーカスを失わない（`Button` の `loading`）。フォーカスの戻し先は W12 のとおり。狭い画面向けの形は作らず、表は包む要素の中だけで横に動く | 画面部品のテスト（部品 8節）と NFR7.3 の実際のブラウザの検査 | 要件 NFR7、要点 11・13、D6・D7・D13・D19、W12、10節 (e)・(k)、`inception/refined-mockups/accessibility-checklist.md`、画面イメージ RQ7 C |
| NFR7.2 | U5 の画面部品（`UserAdminPage`・`UserSearchBox`・`UserTable`・`UserRowActions`・`ConfirmActionDialog`・`EditProfileDialog`）ごとに、vitest-axe の検査を1件以上入れ、違反 0 件とする。状態は、2語の氏名・長いメールアドレス・ロック中・利用停止・「あなた」・誤りの状態・5種類の確かめを含める。light・dark と文字の大きさ sm・md・lg の組は、既存の表示の設定のテストの手伝いで流す | 画面部品のテスト（部品 8節の各テストの「vitest-axe」の項） | 要点 11、機能設計 8節、TM の Testing Posture、PM の学び（2語の氏名と誤りの状態） |
| NFR7.3 | 実際のブラウザのアクセシビリティの検査（Playwright＋axe-core）を `frontend/e2e/120-user-admin-accessibility.e2e.ts` に足す。組は U4 の `130-admin-forbidden-accessibility.e2e.ts` と同じ既存の 20 組（`support/displayCombos.ts`。(a) テーマ2×文字の大きさ3、(b) ブランドカラー4×テーマ2、(c) (a) の6組を 375px×812px）とし、切り替え方も同じにする。確かめる状態は 11: (1) 一覧（行あり。2語の氏名・長いメールアドレス・ロック中・利用停止・管理者・「あなた」の行を含む）、(2) 自分の行のメニューを開いた状態（押せない項目と理由の文）、(3) 確かめの表示（「利用を止める」を代表にする）、(4) 氏名・言語の入力の誤りの状態（画面の確かめの誤り）、(5) 業務の失敗の知らせ（409 の見本）、(6) 検索の上限の誤り、(7) 氏名・言語の入力の表示の通常の状態（誤りの無い入力の途中）、(8) 成功の `Toast`（操作の成功の後）、(9) 一覧の読み込みの失敗の `Alert`（通信の失敗の見本）、(10) 空の一覧の `Alert`（検索に当たる利用者がいない見本）、(11) 自分の行のメニューの押せない項目にホバーした状態と、キーボードでフォーカスを置いた状態（前の Intent の quality-followup でホバーの検査を足した前例と同じ形）。一覧の API と操作の API の答えだけを見本に差し替え、状態を変える要求はサーバーへ届けない（`security-requirements.md` の NFR3.5）。組と状態ごとに、axe-core の違反 0 件（color-contrast を含む WCAG 2.0・2.1 の A・AA、横に動く領域の `scrollable-region-focusable` を含む）と、`support/axe.ts` の `REQUIRED_RULES`（`color-contrast`・`scrollable-region-focusable`）が走ったこと（`missingRequiredRules` が空。U4 の 130 と同じ）と、文書の横の大きさが表示の幅を超えないこと（375px の組を含む）を確かめる。`Toast` のように時間で消える表示は、消える前に検査する（消えて検査の対象から外れたまま通さない）。既知の違反の一覧は空から始める。押せない項目と理由の文の文字のコントラストが light・dark のどちらかで 4.5:1 を下回ったときは、部品 2.5 の「依頼者に戻す差」として扱う。この検査と画面の時間の測り（`performance-requirements.md` の NFR5.1・NFR5.2）は流れではないため、流れの E2E の本数に数えない | コード生成で 120 を書き、`./gradlew e2eTest` を流して組と状態ごとの結果（違反の件数・成否・横のはみ出しの有無）を記録する。Build and Test でも流して記録する。画面・認証に関わる変更とサブモジュールの固定先の更新を統合する前と、リリースの前に手元で実行する（`./gradlew verify` と CI の外） | Q1 A、要点 12・13、要件 NFR7、U4 の NFR7.3（130 と同じ組）、PM の学び（実際のブラウザの検査は誤りの状態と現実に近いデータで、ブランドカラーとテーマのすべての組。Playwright の検査は E2E の本数に数えない）、承認の場（R-04） |

## 4. 多言語

| ID | 要件 | 確かめ方と持ち主の段 | 出典 |
|---|---|---|---|
| NFR8.1 | U5 の文言（機能設計 7節の `useradmin.*`）は ja・en の両方を持ち、既定は日本語。ja と en の文言の鍵がそろっている。新しいエラーの code の文言（7.5 の業務の失敗の知らせ）も ja・en の両方を持つ | `registration.test.tsx`（または `messages.test.ts`）で、文言の鍵の一覧が ja と en で一致することを見る。en の画面で en の文言が出ることを `UserAdminPage.test.tsx` で見る（コード生成） | 要件 NFR8、要点 14、機能設計 7節・W11 |
| NFR8.2 | make-you-chic-ui の `Table` の `labels` と `Modal` の閉じるの名前（`closeLabel`）を画面の言語で渡し、en の画面に make-you-chic-ui の日本語の既定の文言を出さない | `UserTable.test.tsx`・`ConfirmActionDialog.test.tsx`・`EditProfileDialog.test.tsx` で、en の画面に `Table` と `Modal` の日本語の既定の文言が無いことを見る（コード生成） | 要点 14、W11 |
| NFR8.3 | 自分の行の言語を en に直して保存した直後の成功の Toast は、送った言語（en）で出す（i18next の `lng` を明示する）。日時（登録した日時・解除の予定の時刻）は既存の `formatDateTime` の書式で、画面の言語とブラウザの時間帯で出す。解除の予定の時刻は、今日なら時刻と時差の略号だけ、今日でなければ日付つきにする | `UserAdminPage.test.tsx` で、en に直した直後の Toast が英語であることを `waitFor` で見る。`lockedUntil.test.ts` で、今日・明日・時間帯の違いと ja・en を、時間帯と今の時刻を引数で固定して見る（コード生成） | 要点 14、D17・D18、承認の場（R-02）、10節 (b) |

## 5. テストと E2E

| ID | 要件 | 確かめ方と持ち主の段 | 出典 |
|---|---|---|---|
| NFR9.5 | フロントエンドのカバレッジの下限（行 80%・分岐 70%）を守る。U5 のために計測の除外を増やさない | `./gradlew verify` の中の `@vitest/coverage-v8` の `thresholds`（code-generation・build-and-test） | 要件 NFR9、要点 18、TM の Testing Posture |
| NFR9.6 | 性質ベースのテスト（fast-check、失敗時の乱数の種を記録）を `rowActions.ts` と `searchInput.ts` に当てる。`rowActions.ts` は、任意の `admin`・`suspended`・`resettable`・`self` で、自分の行の「印を外す」「止める」が押せる形にならないこと、項目が4つの値だけで決まること（W4 の 6）を見る。`searchInput.ts` は、前後に空白（半角・全角）を足しても結果が同じこと、送る値の長さが 254 コードポイント以下であることを見る | `rowActions.test.ts`・`searchInput.test.ts`（部品 8節、コード生成） | 要点 18、機能設計 8節、D5・D6・D15、TM の Testing Posture |
| NFR9.7 | テストの書き方を次のとおりにする。描画の後に反映される値（読み直しの後の行・Toast・フォーカス・言語の切り替え）は `waitFor` で待つ。テストの時間の上限は原因を確かめずに延ばさない。テストの説明文は英語、テストデータのメールアドレスは `example.com` だけ、見本の氏名は2語を含め、実在しそうな氏名・宛先を置かない。時刻は偽の時計か引数で固定し、実時刻と `sleep` に頼らない。`fieldErrors.ts` を `src/shared/api-client/` へ移した後も、プリファレンスの既存のテストがそのまま通る | `./gradlew verify`（code-generation・build-and-test）。テストのコードのレビューで書き方を確かめる | 要点 18、機能設計 8節・10節 (f)、TM の Testing Posture、PM の学び（`waitFor`） |
| NFR9.8 | 流れの E2E は代表の流れを1本（`frontend/e2e/110-user-admin-flow.e2e.ts`、検索 → ロック → 失敗回数を戻す → 止める → 入れない → 停止を解く → 入れる）だけ足す。状態を変える操作は流れの中で作った利用者だけに行い、初期管理者の状態は変えない。前のテストが作った状態に頼らない。`./gradlew verify` と CI の外に置き、画面・認証に関わる変更とサブモジュールの固定先の更新を統合する前と、リリースの前に手元で流す。前提（招待・Mailpit）が無くて飛ばした場合は理由の種類だけを注記し、統合の前の実行では飛ばしていないこと（飛ばした注記が出ていないこと）を確かめる。U5 の変更の後に、既存の E2E（010〜100）と U4 の 130 も通る | コード生成で 110 を書いて流し、Build and Test で `./gradlew e2eTest` を流して、飛ばした注記の有無とあわせて記録する（承認の場の R-08 の申し送り） | 要件 NFR9、要点 19、機能設計 9節、承認の場（R-08）、TM の Testing Posture、ストーリー M9 A |
| NFR9.9 | 120 の検査と NFR5.2 の測りで差し替える答えの見本は1つにまとめ、画面の側の型（`api/types.ts` の `AdminUserPage`・`AdminUser`）を付ける。本物の応答と項目の名前・型が一致することを、110 の流れの中で一覧の本物の応答を読んで毎回確かめる。409 と検索の 400 の見本も同じ置き場に置き、110 の中で本物の失敗の応答と照らし合わせる。(1) 409: 110 が自分で作った利用者 U に、失敗回数を戻した後でもう一度「失敗回数を戻す」の API を呼んで本物の 409（`USER_ADMIN_NO_CHANGE`、U の状態は変わらない）を受け、見本と項目の名前・型（`code`・`status` など）が一致すること、見本の `code` が C3 と U3 の決めた5つの code（`USER_ADMIN_SELF_OPERATION`・`USER_ADMIN_TARGET_SUSPENDED`・`USER_ADMIN_NO_CHANGE`・`USER_ADMIN_LAST_ADMIN`・`USER_ADMIN_BUSY`）のどれかであることを確かめる（最後の管理者の 409 は初期管理者の状態を変えないと本物で作れないため、code の一覧との照合にとどめる）。(2) 400: 上限を超える検索の文字で一覧の API を呼んで本物の 400 を受け、見本と `code`（`VALIDATION_FAILED`）と項目の名前・型が一致することを確かめる。見本のメールアドレスは `example.com` の値を使い、110 が作る値の形（`u7-perf-` で始まる宛先）を使わない（`security-requirements.md` の NFR3.4 の形での探し方と重ならないため）。照らし合わせの失敗の知らせには、項目の名前だけを出し、値を出さない。既存の `frontend/e2e/support/` のファイルは変えない（見本と手伝いを足す置き場はコード生成の計画で決める） | コード生成で、見本の型の検査（`tsc`）と、110 の中の一覧・409・400 の照らし合わせを書く。Build and Test で `./gradlew e2eTest` を流して記録する | Q1 A、PM の学び（E2E の検査で API の答えを差し替えるときは見本を1つにして型を付け、流れの E2E で毎回確かめる）、部品 2.4・9節、C3、U3 の機能設計（問題の種類の一覧）、承認の場（R-04） |

## 6. 上流との差

承認済みの文書は書き換えません（PM の Way of Working）。

| 決定 | 文書 | 承認済みの記述 | この段での扱い |
|---|---|---|---|
| 実際のブラウザの検査を足す（Q1: A） | 要件の NFR7、機能設計 8節 | 「実際のブラウザでの検査を足すかは設計で決める」「NFR 要件の段で決める」 | この段で決めた（NFR7.3）。決めるべき点の決定で、食い違いではない |
| E2E のファイル 120 を足す | 機能設計 2節・部品 2.4・9節 | E2E は 110 を足し、010〜100 と `support/` は変えない | 120 を足す（NFR7.3・NFR9.9）。既存の 010〜100・130 と `support/` の既存のファイルは変えない。追加。承認の場（2026-10-01）で、依頼者がこの差を受け入れた |
| 確かめる状態を 11 に広げ、409・400 の見本を本物と照らし合わせる | この文書の前の版（承認の場の前） | 状態は6つ。本物と照らし合わせるのは一覧の見本だけ | 承認の場（R-04）で足した（NFR7.3・NFR9.9）。決め直しの理由は `security-requirements.md` の「承認の場の決定」の節 |
| 固定先と `.npmrc` | この文書の前の版（承認の場の前） | 固定先は「`3481488` 以降」。`ignore-scripts=true` の効く範囲は書いていない | 承認の場（R-05）で、固定先は B5 のコード生成の計画で決める、効く範囲は `frontend/` のインストール、2つの変更は別のコミット、と書いた（NFR9.3・NFR9.4） |
| 狭い幅（375px）の6組を検査に含める | 画面イメージ RQ7 C・D5 | 狭い画面向けの形は作らず、表を横にずらして読めるだけにする | 狭い画面向けの形は作らないまま、文書が横にはみ出さないこと（表は包む要素の中だけで動く）と axe の違反 0 件だけを確かめる（NFR7.3）。RQ7 C の確かめ方を決めたもので、食い違いではない |
| 押せない項目のコントラスト不足を「依頼者に戻す差」とする | 部品 2.5 | 押せない項目と理由の文の文字のコントラストは light・dark で 4.5:1 以上を期待し、下回れば依頼者に戻す | 確かめの場を 120 の検査に決めた（NFR7.3）。部品 2.5 のとおりで、食い違いではない |
| 依存・`.npmrc`・テスト・E2E の要件を NFR9 の枝番に寄せる | 要件の NFR1〜NFR11 | 依存と `.npmrc` に当たる ID が無い。テストは NFR9 | NFR9.2〜NFR9.9 に置いた（U4 の前例と同じ）。traceability.json の NFR9 の target に書く |
