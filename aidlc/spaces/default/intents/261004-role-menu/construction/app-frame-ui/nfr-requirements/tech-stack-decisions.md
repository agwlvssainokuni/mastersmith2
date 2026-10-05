# 技術の選択とテスト・品質の要件 — U7 app-frame-ui

## 出典

- `functional-spec.md`（この単位の承認済みの機能設計。D1〜D30、1.3 の make-you-chic-ui の口と固定先の更新、6節のテストの方針、7節の上流との差、11節の承認の場の直し）と `frontend-components.md`。
- `rules.md` は ui の単位には無い。段の定義が必須とする `rules` の代わりに、`functional-spec.md` の D1〜D30 を入力として使う（`security-requirements.md` の出典と同じ）。
- `requirements.md`（NFR4・NFR6、C3）、`contract-summary.md`（C2・C8・C9）、`technology-stack.md`（コード知識ベース。React 19・react-router 8・Vitest・Testing Library・vitest-axe・fast-check・Playwright、make-you-chic-ui は `file:` の依存）。
- この段の答え: Q1〜Q3 はすべて A。まとめの確認は Looks correct。
- 先に確定した単位: cross-cutting の NFR4.5（S8 の共有の木の実際のブラウザの axe は U7）、role-admin-ui の NFR 要件（形と、そのレビューの R-01・R-05・R-06）、navigation と role の U7 への引き継ぎ。
- make-you-chic-ui の上流のリポジトリ（読み取りだけ）: `git diff --stat e82b651 5bf1ffe -- '*.json' 'LICENSE*'` に変わったファイルが無いことを確かめた。

上流の枝番との対応は `security-requirements.md` の「ID の振り方」の表のとおり。

## 技術の選択

| 分野 | 選択 | 理由 |
|---|---|---|
| 画面の土台 | React 19・react-router 8.4.0（B8 で data router に替わる前提。替えられなかったときは `BrowserRouter` のまま） | 既存。`useBlocker` と `state` つきの移動（NFR1.8）は同じパッケージにある |
| デザインシステム | make-you-chic-ui を e82b651 から 5bf1ffe へ上げる（追加の依頼が B9 までに入ればその版） | 入れ子のサイドバー（`navSections`）が 5bf1ffe にある（機能設計 1.3） |
| 状態の持ち方 | React の文脈（`WorkRoleProvider`・`BusinessNavigationProvider`）と、開閉の `sessionStorage` | 新しい状態の管理の部品を足さない |
| テスト | Vitest・Testing Library・user-event・vitest-axe・fast-check・Playwright・@axe-core/playwright | 既存 |
| 新しい npm の依存 | 足さない | NFR6.5 |

## 要件

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR4.1 | 目標は WCAG 2.1 AA。<br>・状態（作業ロール・今の項目・開閉・業務のメニューに出るか・権限なし・準備中）は、色だけでなく文字と読み上げの名前で示す。<br>・U7 の画面部品ごとに vitest-axe を1件置く（作業ロールの切り替え・サイドバー・ホームの案内・置き場・自分の権限・ログアウトの画面）。誤りの状態と 40 文字の名前を含める。<br>・切り替えの結果は Toast だけで伝える（Toast の入れ物が `role="status"`・`aria-live="polite"` を持つ）。機能設計の `WorkRoleAnnouncer` の区画は置かず、同じ文を2回読ませない（機能設計の再レビューの R-05） | 部品ごとの `*.test.tsx`。<br>`WorkRoleSwitcher.test.tsx` で、切り替えの文が `role="status"` の区画に1回だけ出る | Code Generation（B9） |
| NFR4.2 | N 階層のメニュー（`team.md` の N 階層のメニューの決まり、要件 NFR4.2）:<br>・開閉のボタンに `aria-expanded`、今の項目に `aria-current="page"` が付き、今の項目は多くて1つ。<br>・キーボードだけ（Tab・Enter・Space）で 5 段の末端まで開いて移れる。<br>・深さの上限ちょうど（5 段）の木が欠けずに描かれる（上限を超える DSL の拒否は U2）。<br>・ロールが1つ・無いときの説明は、開いた一覧の押せない項目の `description`（`aria-describedby`）で、矢印のキーでたどれる（機能設計 D6・R-03）。<br>・make-you-chic-ui の部品に頼る振る舞いも、このリポジトリのテストで確かめる | `ShellLayout.test.tsx`・`WorkRoleSwitcher.test.tsx`（user-event のキーボードの操作）。<br>`buildNavSections.property.test.ts` の性質 4（NFR6.2） | Code Generation（B9） |
| NFR4.3 | 画面の文言は日本語と英語（既定は日本語）。<br>・サイドバーの領域の名前と開閉のボタンの名前は `navLabels` で渡し、英語の表示で英語になる（AC5.1.12）。<br>・ロール・テーブル・DSL の表示名は訳さない。<br>・トップバーの畳むボタンとユーザーメニューのトリガーの名前は、5bf1ffe では日本語の固定のまま（受け入れた制約） | 英語の表示での画面のテスト。<br>骨組みの文言の鍵が日本語と英語の両方にあることを、既存の `messages.test.ts` で確かめる | Code Generation（B9） |
| NFR4.4 | 実際のブラウザの検査（Playwright＋axe-core）を `frontend/e2e/` に U7 の画面の検査として1ファイル足す（番号はコード生成で U6 と合わせて決める。既存の 010〜130 は変えない）。E2E の本数に数えない（`project.md` の読み方）。<br>・状態: 5 段まで開き 40 文字の名前の項目を含むサイドバー（今の項目が末端）、開いた作業ロールの Dropdown（ロール3つ・40 文字のロール名）、作業ロールが1つ・無いの開いた一覧、S2 の準備中と権限なし、S8 の木を開いてカラムの表を出した状態（cross-cutting の NFR4.5）、ホームの案内（設定なし・表示なし・読み込みの失敗）、畳んだサイドバー。<br>・組: 表示の設定の 20 組すべて（`support/displayCombos.ts`）。<br>・合格: axe の違反 0 件、`REQUIRED_RULES` が流れた、画面全体の横のはみ出しが無い、開いた Dropdown と 5 段のサイドバーの矩形が表示の中に収まる。<br>・畳んだ状態で 5bf1ffe の `aria-labelledby`（見出しの無い id を指す）が違反になったときは、目標を緩めず除外も足さずに、その時点で依頼者に諮る（機能設計 1.3）。<br>・API は差し替えの口と見本で返し、見本に画面の側の型を付ける（作業ロール・業務のメニュー・置き場・自分の権限）。見本の形が本物と同じことは、流れの E2E（NFR6.3）で確かめる | 検査のファイル | Code Generation（B9）・Build and Test（統合の前と、リリースの前に手元で流す） |
| NFR4.5 | 画面の幅: 360px・768px・1280px を、既定の1組で確かめる（mockups 10a 節）。<br>・作業ロールの見える文字が 768px 未満で縮み、読み上げの名前は変わらない。<br>・S8 が 768px 未満で縦に積まれる。<br>・サイドバー（幅固定の 220px、5bf1ffe）と画面全体に、横のはみ出しが無い。<br>・はみ出しが出たら、目標を緩めずに依頼者に諮る（AppShell に幅による切り替えが無い件。機能設計 7節 (c)） | NFR4.4 の検査のファイルの中 | Code Generation（B9）・Build and Test |
| NFR6.1 | 必須のテスト（`team.md` の N 階層のメニュー・役割と権限の画面の出し分け）のうち、画面の側のもの:<br>・開閉と今の項目（NFR4.2）。<br>・深さの上限ちょうどの描画（NFR4.2）。<br>・表示の木を作る関数の fast-check（NFR6.2）。<br>・信頼できない入力（`security-requirements.md` の NFR1.7）。<br>・make-you-chic-ui に頼る振る舞いの画面のテストと実際のブラウザの axe（NFR4.2・NFR4.4）。<br>・権限の無い画面へ直接移ったときの権限なしの表示（NFR1.1）。<br>サーバーの必須のテストは U3〜U5（部分） | 各テストの通過（機能設計 6.1） | Code Generation（B9） |
| NFR6.2 | 性質ベースのテスト（fast-check、失敗時の乱数の種を記録する）:<br>・`buildNavSections` に機能設計 6.2 の性質 1〜7。生成器は深さ 1〜5 の木、`navigable` と子の組み合わせ、同じ組を指す項目の重なり、任意の `label`、許した・許していないアイコン、section ADMIN の登録、`admin` の真偽、任意の道。<br>・`tablePlaceholderPath` の往復（任意の名前で、作った道を読み戻すと同じ名前と `item` になり、道の先頭が `/tables?`）。<br>サーバー側の絞る関数の jqwik は U5、継承の解決は U4（部分） | `buildNavSections.property.test.ts`・`tablePlaceholderPath.test.ts`。種は `fc.assert` の失敗の報告から再現できる | Code Generation（B9） |
| NFR6.3 | 流れの E2E（Intent で2本のまま）:<br>**I の流れ**（機能設計 6.4、新しいファイル）<br>・前提（版 2 の深い DSL・ロール・割り当て・利用者）は API で自分で作る。<br>・始めに自分の DSL を API で投入・適用し、適用の後の状態に頼らない。後始末（前の DSL に戻す）はしない（Q2 A）。<br>・流れのファイルは `040-dsl-admin.e2e.ts` より後の番号に置き、040 は「まだ適用していない」前提を保つ。<br>・初期管理者の状態とロールは変えない。<br>・時間の上限は、既存の流れの E2E（090・110）と同じ形で `test.setTimeout` で延ばし、値は既存の流れに合わせる。<br>**F の流れに B9 で足す部分**（機能設計 6.5）<br>・2つ目のロールの作成と割り当て、トップバーでの切り替え、サイドバーの変化、`GET /api/me/work-role` の本物の応答と見本の型の一致。<br>・F の流れの DSL の前提も I の流れと同じ形（始めに自分で適用する）にするよう、コード生成の計画で U6 と合わせる（U6 の NFR 要件のレビューの R-01）。<br>**どちらも**<br>・メニューからのログアウトで、ログインの画面へ戻る。I の流れの最後の手順で、`/logout` の直接の表示でログアウトしないこと、メニューからのログアウトの後に戻る・再読み込みしてもログアウトの要求が1回だけであることを確かめる（流れの本数は増やさない。`security-requirements.md` の NFR1.8、承認の場の直し R-01）。<br>・統合の前に手元で E2E 全体を流す | 流れの E2E の通過。報告の値の確かめ（NFR1.9） | Code Generation（B9）・Build and Test |
| NFR6.4 | カバレッジ: フロントエンドの下限（行 80%・分岐 70%、`@vitest/coverage-v8` の `thresholds`）を、骨組みの変更・`features/tables`・`features/mypermissions`・`features/auth` の変更を含めて満たす。除外を足さない。バックエンドには手を入れない（`packagesJudgedByTotal` に触れない） | `./gradlew verify` の中のカバレッジの検証 | Code Generation（B9）・Build and Test |
| NFR6.5 | 新しい npm の依存を足さない。<br>・data router（`createBrowserRouter`・`RouterProvider`・`useBlocker`）と `state` つきの移動は react-router 8.4.0（MIT、lockfile で固定）にある。<br>・fast-check・vitest-axe・Playwright・@axe-core/playwright は既存。<br>・フロントエンドの依存の脆弱性の関門と `ignore-scripts=true` は今のまま（`team.md`） | `frontend/package.json`・`package-lock.json` に足した依存が無いこと（コード生成の成果物で確かめる） | Code Generation（B9） |
| NFR6.6 | make-you-chic-ui の固定先の更新（e82b651 → 5bf1ffe）は、B9 の最初の専用のコミットで行い、前後のハッシュを記録する。統合は `team.md` の fast-forward の例外を使う（機能設計 D28）。そのコミットで次を確かめる。<br>・2つの版の間で `package.json`・`package-lock.json`・`LICENSE` に変更が無いこと（ライセンスは Apache License 2.0 のまま）。<br>・`vendorInstall`（`npm ci`）が lockfile どおりに通る。<br>・`vendor/make-you-chic-ui` の lockfile を含む OSV-Scanner が通る。<br>・アイコンの一覧（18 個）が変わらず、`allowed-icons.txt` と `app/registry` の一覧の一致のテストが通る。<br>・`./gradlew verify` が通る。<br>追加の依頼（`make-you-chic-ui-request-2.md` の3点）が B9 までに上流に入れば、その版を固定先にし、同じ確かめを当てる。間に合わなければ 5bf1ffe で進め、残る点をコード生成の成果物に差として記録し、承認の場で伝える | 固定先の更新のコミットの記録（前後のハッシュ、`git diff --stat` の結果、確かめのコマンドの結果） | Code Generation（B9） |
| NFR6.7 | 既存のテストの書き換え（機能設計 D28・7節 (f)・11節）を、要件として行う（U6 のレビューの R-05）。<br>・`app/layout/ShellLayout.test.tsx`・`app/routing/AppRouter.test.tsx`: サイドバーの `data-testid` が `sidebar-nav-<id>` に変わる。<br>・`app/navigation/navigationItems.test.ts`: 平らな一覧から区画へ。<br>・`features/auth` のログアウトの項目と登録のテスト: action から `/logout` の path と印へ。<br>・E2E の `support/registeredUser.ts`（`openUserMenuItem`）と `080-preferences-accessibility.e2e.ts` の開き口の操作: 作業ロールの入れ物（`work-role-switcher`）の外の `dropdown-trigger` を探す新しい `support/userMenu.ts` の関数へ。<br>・書き換えた後も、テストが確かめていた中身（項目の出し分け・移動・ユーザーメニューの操作）は減らさない | `./gradlew verify` の通過と、統合の前の E2E 全体の通過（strict モードで開き口が1件に絞れる） | Code Generation（B9） |
| NFR6.8 | 自分の権限の応答の型（機能設計の再レビューの R-08）:<br>・道は role の確定の形（`/api/me/permissions/schemas`・`tables?schema=…`・`columns?schema=…&table=…`）で固定する。<br>・項目の型（`displayName` の形、`create`・`delete` の型）は、コード生成の計画の前に U4 の B5 の計画で確かめ、計画に書き留める。<br>・`parseMyPermissions` は、形が崩れた本文を通信の失敗として扱う | `myPermissionsApi.test.ts`（道・引数の形と、崩れた本文で失敗になる） | Code Generation（B9）の計画 |

## 受け入れた制約

- **管理のメニューの遅れ**（機能設計の再レビューの R-06）: 管理者の印を外されたとき、管理の区画は、次のログイン状態の知らせ（トークンの更新、既定 5 分）か、管理の API の 403 の後の読み直しまで残る。サーバーの管理の API は 403 で守られる。AC5.3.7 は、サーバーの判定と次の知らせの後の表示で満たす「部分」として扱う（承認済みの AC は書き換えない）。
- **トップバーの文言**: 5bf1ffe のトップバーの畳むボタンとユーザーメニューのトリガーの名前は、日本語の固定のまま。英語の表示でも日本語で出る（機能設計 7節 (i)）。追加の依頼が入ったら NFR4.3・NFR4.4 の確かめを見直す。
- **畳んだ状態**: 畳んだサイドバーでは、深い段の今の項目の印が1段目に出ない（追加の依頼の2点目）。
- **画面の時間**: 手元の PC の1台で測り、記録だけにする（`performance-requirements.md`）。

## 上流との差（承認済みの文書は書き換えず、ここに記録する）

| # | 上流 | 上流の書き方 | この段の決まり | 理由 |
|---|---|---|---|---|
| (a) | app-frame-ui の機能設計の質問のファイル（「決まっていること」の API の項目） | 置き場の問い合わせは「引数が無い・空・257 文字以上は 400」 | 400 は引数が無い・空のときだけ。長さの上限は navigation の承認の場の直しでやめた（navigation の NFR 要件の U7 への引き継ぎ）。承認済みの `functional-spec.md` の D21・5節（403・400・引数なしを同じ権限なしの表示にする）は、どちらの決まりでも変わらない | navigation の承認の場の直し |
| (b) | 機能設計 D7・D8・`frontend-components.md` 3.2 | 結果を Toast と `aria-live` の区画（`WorkRoleAnnouncer`）の両方で伝える | Toast だけで伝える（NFR4.1） | Toast の入れ物が既に `aria-live` を持ち、2回読まれる（R-05） |
| (c) | `frontend-components.md` 3.2 | 切り替え中はトリガーに `aria-disabled` を付けて押下を捨てる | 待ちの間は Dropdown を押せないボタンに差し替える（`performance-requirements.md` の NFR2.11） | Dropdown がトリガーの `onClick` を上書きする（R-04） |
| (d) | 機能設計 D27・W9.1 | `/logout` を描いたら `logout()` を呼ぶ | メモリ上の一度きりの印（履歴に残さない）があるときだけ呼び、読んだら消す。印が無ければホームへ戻す。`/logout` の道では読み直さない（`security-requirements.md` の NFR1.8） | 意図しないログアウトの強制を防ぐ（Q3 A、R-07） |
| (e) | 機能設計の traceability.json の AC5.3.7 | 管理の区画は印で出し、業務の区画は読み直す（部分） | 管理の区画は次のトークンの更新まで残りうる（受け入れた制約） | 印の読み直しは D4 の対象外（R-06） |

## コード生成（B9）への引き継ぎ

- 作る順は機能設計 9節のとおり。固定先の更新（NFR6.6）を最初の専用のコミットにする。
- 計画の前に確かめること:
  - 自分の権限の応答の型（NFR6.8）。
  - I の流れの利用者の値が、報告の確かめの部品の対象に入るか（`security-requirements.md` の NFR1.9）。
  - E2E の番号を U6（U6 の検査・F の流れ）と合わせる。
  - F の流れの DSL の前提を、I の流れと同じ形（始めに自分で適用）に合わせる（NFR6.3）。
  - 追加の依頼が上流に入ったかと、固定先にする版（NFR6.6）。
- 測りのテスト（`performance-requirements.md` の NFR2.8）は NFR4.4 の検査のファイルの中に置き、見本の木（NFR2.12）を `e2e/support/` の生成の関数で作る。
- 初回の JavaScript の前後の値と chunk の大きさ（NFR2.10）を code-summary に記録する。

## 承認の場の決定と直し

- 決定: 「Major 11 件だけを直す」（Request Changes）。この文書では、R-01 の直しに合わせて NFR6.3 の最後の手順（`/logout` の直接の表示・戻る・再読み込みの確かめを I の流れの中に置き、本数を増やさない）と、上流との差 (d)（印はメモリ上の一度きりの値で、読んだら消す）を書き直した。直しの中身は `security-requirements.md`（R-01）と `performance-requirements.md`（R-02）の同じ名前の節のとおり。
