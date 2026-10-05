# 技術の選定 — U6 role-admin-ui

## 出典

- `functional-spec.md`（承認済みの機能設計。7節の data router と `useBlocker`、8節のテストの方針、D1〜D32）と `frontend-components.md`
- `rules.md` は ui の単位には無い。代わりに `functional-spec.md` の D1〜D32 を入力にする（`security-requirements.md` の出典と同じ）。
- `requirements.md`（NFR4・NFR6）
- `contract-summary.md`（C2 の共有の型と木、C6・C7）
- `technology-stack.md`（コード知識ベース `aidlc/spaces/default/codekb/mastersmith2/`）と、確かめたコード（`frontend/package.json`・`frontend/package-lock.json`・`frontend/src/main.tsx`・`frontend/src/shared/api-client/apiClient.ts`・`frontend/e2e/`）
- この段の答え: `nfr-requirements-questions.md` の Q1 A・Q2 A、まとめの確認は Looks correct。
- 決まりの層: `team.md`（Testing Posture の画面のテスト・E2E・役割・権限の機能、Deployment のフロントエンドの依存の脆弱性、Code Style のフロントエンド）、`project.md`（実際のブラウザの axe・開いた部品のはみ出し・E2E の見本と報告・Playwright の axe と時間の測りは本数に数えない）。
- 先に確定した単位: `construction/cross-cutting/nfr-requirements/`（NFR4.5: S4 の実際のブラウザの axe は U6）、`construction/role/nfr-requirements/`（NFR2.9 と U6 への引き継ぎ）。

## ID の振り方

`security-requirements.md` の「ID の振り方」と対応表のとおり（4つの成果物に共通）。この文書は NFR4.1〜NFR4.7・NFR6.1〜NFR6.10 を持つ。

## 1. 選定

| 区分 | 選んだもの | 理由 |
|---|---|---|
| 画面の部品 | React 19・TypeScript（既存） | 既存の画面と同じ |
| ルーター | react-router 8.4.0（既存、MIT）。`BrowserRouter` を data router（`createBrowserRouter`＋`RouterProvider`）に替え、`useBlocker` を使う | 機能設計の Q2 B（AC1.2.15）。同じパッケージにあり、新しい依存にならない |
| デザインシステム | make-you-chic-ui（固定先 e82b651 のまま） | Table・Tabs・Modal・Dropdown・Select・Alert・Badge で足りる。U6 が上流に頼む部品は無い。固定先の更新は B9 の U7 |
| 共有の木 | `frontend/src/shared/tree`（U1 が B2 で作る） | 契約 C2 |
| API の呼び出し | 既存の ApiClient（`apiRequest`・`apiDownload`）。`ApiDownload` に応答のヘッダーを足す | Q2 A |
| 文言 | react-i18next（既存）、機能ごとの `messages` | 既存の形 |
| テスト | Vitest・Testing Library・user-event・vitest-axe・fast-check・`@vitest/coverage-v8`・Playwright・@axe-core/playwright（すべて既存） | `team.md` の Testing Posture |

## 2. 依存・共有の部品・ルーター

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR6.5 | U6 は新しい npm の依存（実行時・開発時とも）を足さない。`frontend/package.json` と `frontend/package-lock.json` を変えない。data router と `useBlocker` は今の react-router 8.4.0（MIT）を使う | B8 の差分に `package.json`・`package-lock.json` の変更が無いことを、コード生成の終わりに確かめる | Code Generation（B8） |
| NFR6.6 | 共有の `ApiDownload`（`frontend/src/shared/api-client/apiClient.ts`）に応答のヘッダー（`headers: Headers`）を足す互換の変更をする。既存の使う側（DSL のダウンロード）は変えない。`roletransfer` は `X-Role-Transfer-Exceeds-Import-Limit` が `true` のときだけ、「上限を超えたため、このファイルはそのまま読み込めません。ロールの一部ずつに分けて読み込んでください」を出す。上限を超えたかの判定はサーバーの1か所（role の NFR2.9）に置き、画面は本文の大きさで判定しない | `apiClient.download.test.ts` に、ヘッダーが返る・既存の項目が変わらないことを足す。`roletransfer` のテストで、ヘッダーが `true`・無い・`false`・ほかの値の境界で案内の有無を確かめる。既存の DSL のダウンロードのテストが通る | Code Generation（B8） |
| NFR6.7 | `frontend/src/main.tsx` を `createBrowserRouter`（今の `App` を `path: '*'` の1つの道で包む）と `RouterProvider` に替え、`App` の中の振り分けは変えない。今の `main.tsx` の受け渡し（表示の設定・`StrictMode` など）は保つ。コード生成の最初に、替えた後で既存の画面のテストと E2E（010〜130）が変わらないことと、`useBlocker` がサイドバーの移動で止まることを小さく確かめ、どちらかが成り立たなければ戻して機能設計 7.3 の形に切り替え、差を記録する | 既存のテストと E2E の通過。`PermissionPanel`・`RoleDetailPage` のテストを `createMemoryRouter` で描き、道の移動で確かめが出ることを確かめる | Code Generation（B8）・Build and Test |
| NFR6.8 | フロントエンドの依存の脆弱性の関門は今のまま（実行時の依存の High 以上・`MAL-`・成果物を作る道具の High 以上で統合を止める、`ignore-scripts=true`）。U6 で緩めない | `./gradlew verify` の中の OSV-Scanner | Build and Test |

## 3. アクセシビリティ

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR4.1 | 目標は WCAG 2.1 AA。状態（明示・継承・既定・今の DSL に無い・業務のメニューに出る・利用停止・割り当て済み・メンバー済み・未保存）は色だけでなく文字で示す。操作の結果は Alert と `aria-live` で伝える。確かめの表示は `role="alertdialog"`・背景のクリックで閉じない・はじめのフォーカスは「やめる」（未保存は「留まる」）・閉じたら開いた所へ戻す。U6 の画面部品ごとに vitest-axe を1件置く（誤りの状態・2語の氏名・64 文字の名前を含める） | 部品ごとの `*.test.tsx`（`frontend-components.md` の 2節・4節・5節・6節の部品） | Code Generation（B8） |
| NFR4.2 | S4 の共有の木は、開閉（`aria-expanded`）と選び（`aria-current`）が出て、キーボードだけ（Tab・Enter・Space）で開いて選べる。Select の名前に対象を含める。N 階層のメニューの決まりは U7 が持つ（部分） | `PermissionPanel.test.tsx` と、実際のブラウザの検査（NFR4.4）の S4 | Code Generation（B8） |
| NFR4.4 | 実際のブラウザの検査（Playwright＋axe-core）を `frontend/e2e/` に U6 の画面の検査として1ファイル足す（番号はコード生成で決める。既存の 010〜130 は変えない）。範囲は次のとおりで、E2E の本数に数えない（`project.md` の読み方）。<br>状態: S3 の一覧・開いた Dropdown・名前の誤りの Modal、S4 の保存の誤り（BUSY）・未保存の確かめ・今の DSL に無い印、S5 の候補の Modal（利用停止・割り当て済み）・外すときの確かめ、S6 の詳細とメンバーの外しの Alert、S7 の結果の行を開いた状態と誤りの一覧、S9 の Modal。<br>見本: 2語の氏名、64 文字のロール名、長いメールアドレス、`<`・`&` を含む名前。<br>組: 表示の設定の 20 組すべて（`support/displayCombos.ts`）。<br>合格: axe の違反 0 件、`REQUIRED_RULES` が流れた、画面全体の横のはみ出しが無い、開いた Dropdown・Modal の矩形が表示の中に収まる。<br>共有の木（S4）の実際のブラウザの axe はこのファイルで覆う（cross-cutting の NFR4.5） | 検査のファイル。API は差し替えの口で受け、書き換えをサーバーへ届けない（`security-requirements.md` の NFR1.11） | Code Generation（B8）・Build and Test（統合の前と、リリースの前に手元で流す） |
| NFR4.5 | 画面の幅は 360px・768px・1280px の3つを、既定の1組で確かめる。768px 未満で S4 の木と表が縦に積まれ、木で選ぶと表の見出しへフォーカスが移ること、一覧と結果の表が外枠の中で横に動き1列目が見えること、Modal が幅いっぱい（左右 16px）であること、どの幅でも画面全体の横のスクロールが無いこと（機能設計 D31） | NFR4.4 の検査のファイルの中に、幅ごとのテストを置く | Code Generation（B8）・Build and Test |
| NFR4.7 | S4 と S5 は同じ `RoleDetailPage` を2つの道に登録し、タブの切り替えの後もフォーカスはタブに残る（画面を作り直さない）（機能設計の再レビューの R-05） | `RoleDetailPage.test.tsx` で、タブを切り替えた後にフォーカスがタブにあり、ロールの読み込みが1回だけであることを確かめる | Code Generation（B8） |

## 4. 多言語

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR4.3 | U6 の文言（`roleadmin.*`・`groupadmin.*`・`roletransfer.*`・`useradmin` に足す鍵）は ja・en の両方を持ち、既定は日本語。鍵の組が ja と en でそろう。`reason` の 13 個の文言も ja・en。ロール・グループ・スキーマ・テーブル・カラムの名前と氏名は訳さない | 各機能の `messages` のテスト（ja と en の鍵の一致）と、en の表示で画面の文言がすべて英語であることの画面のテスト | Code Generation（B8） |
| NFR4.6 | make-you-chic-ui の `Table` の `labels`・`Modal` の `closeLabel`・`Alert` の `dismissLabel` と、共有の木の `labels` を画面の言語で渡し、en の画面に部品の日本語の既定の文言を出さない。上限の値は文言に直接書かず `MAX_TRANSFER_BYTES` から差し込む（機能設計の再レビューの R-07） | en の表示の画面のテストで、部品の既定の日本語が出ないことを確かめる | Code Generation（B8） |

## 5. テストと E2E

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR6.1 | 画面の側の必須のテスト（`team.md` の役割・権限の機能の「画面の出し分け」、認証・認可・監査の画面の部分）を書く: 管理の API の 403 が骨組みの表示になる・道の直接入力で権限なしの表示になる（`security-requirements.md` の NFR1.1）。サーバーの必須のテストは U3・U4（部分） | 機能設計 8.1 の確かめの一覧と NFR1.1 の表のテスト | Code Generation（B8） |
| NFR6.2 | 性質ベースのテスト（fast-check、失敗時の乱数の種を記録）を画面の純粋な関数に当てる: `permissionDraft`（変えていなければ `entries` が空、戻すと消える、`scope` の外を含まない、同じ対象は1つ）、`removalImpact`（直接以外の出どころがあれば無くならない、残りの最初は ID の最小）、`removedRoles`、`changePaging`（範囲が重ならず全件を覆う）、`reason` を文言に写す関数（任意の文字列で例外にならない）。継承の解決は U4、メニューを絞る関数は U5・U7（部分） | 各関数の `*.test.ts` | Code Generation（B8） |
| NFR6.3 | 流れの E2E は F の流れの1本を B8 で書く（機能設計 8.3）。自分で作った利用者（招待から登録まで）と、走らせるごとに一意の名前のロールだけを対象にし、初期管理者の状態とロールは変えない。B8 では管理の画面でロールを作り、テーブルの主権限を READ にして保存し、作った利用者に割り当て、その利用者で `GET /api/me/work-role` と `GET /api/me/navigation` の応答を確かめる。B9（U7）が同じファイルに画面での切り替えとサイドバーの変化を足す。I の流れは U7（部分）。<br>前提の DSL（承認の場の直し R-01、app-frame-ui の NFR 要件の Q2 の決定にそろえる）: F の流れのファイルは、始めに自分で版 2 の DSL（スキーマ1つとテーブル・カラムを持つ流れ用の見本）を管理の API で投入して適用し、テーブルの主権限を READ にする対象をその DSL から取る。前のテストが適用した DSL や状態に頼らない。後始末は無し（適用した DSL は残す）。流れのファイルの番号は 040（DSL の管理）より後に置き、040 の「まだ適用していません」の前提を壊さない。B9 で足す後半も同じファイルの中で、この始めの適用の後に続ける（I の流れのファイルも同じ形で自分で適用する、app-frame-ui の持ち物） | 流れの E2E の通過（統合の前に手元で E2E 全体を流す）。040 より後の番号であることと、ファイルの始めで DSL を適用していることを、コード生成のレビューで確かめる | Code Generation（B8）・B9 |
| NFR6.4 | フロントエンドのカバレッジの下限（行 80%・分岐 70%、`@vitest/coverage-v8` の `thresholds`）を、新しい3機能と `useradmin` の変更を含めて満たす。計測の除外を増やさない。バックエンドのパッケージの一覧（`packagesJudgedByTotal`）には触れない | `./gradlew verify` の中のカバレッジの検証 | Code Generation（B8）・Build and Test |
| NFR6.9 | テストの書き方: 描画の後に反映される値は `waitFor`・`findBy*` で待つ。テストの時間の上限は原因を確かめずに延ばさない。説明文は英語。見本のメールアドレスは `example.com` だけで、実在しそうな氏名を置かない。`useBlocker` を使う部品は `createMemoryRouter` で描く | コード生成のレビュー | Code Generation（B8） |
| NFR6.10 | E2E と検査で差し替える答えの見本は1つにまとめ、画面の側の型（各機能の `api/types.ts`）を付ける。本物の応答と項目の名前・型が一致することを、流れの E2E（NFR6.3）の中で毎回確かめる（`project.md` の学び） | 流れの E2E の中の比べ | Code Generation（B8） |

## 6. 受け入れた制約

- 骨組みのログアウトは道を変える前にトークンを捨てうるため、未保存の確かめが出ないことがある（機能設計 7.2）。
- 実際のブラウザの検査と画面の時間の測りは `./gradlew verify` と CI の外に置き、統合の前とリリースの前に手元で流す（`team.md` の E2E と同じ扱い）。

## 7. 上流との差

- この段の Q2 A で、共有の `ApiDownload` に応答のヘッダーを足す（NFR6.6）。shared の部品の変更は項目を足すだけの互換の変更。
- 機能設計の再レビューの R-03（traceability の Deferred を OK の部分に）・R-04（7.1 の断片を今の `main.tsx` の形に）・R-09（権限の木の道の条件つきの言い回し）は、承認済みの機能設計を書き換えず、コード生成の計画に引き継ぐ。NFR6.7 は R-04 の手当てを要件に含める（今の受け渡しを保つ）。

## 8. コード生成（B8）への引き継ぎ

- 計画の最初の手順に NFR6.7 の小さな確かめを置き、結果で 7.3 の切り替えを決める。
- NFR4.4 の検査のファイルと NFR6.3 の流れの E2E の番号を決める（既存の 130 の後）。
- 測りのテスト（performance の NFR2.9）は NFR4.4 の検査のファイルの中に置く。
- B8 の前の `check-bundle-size.mjs` の値を記録する（performance の NFR2.13）。
- `useradmin` の本体に手が入るため、メニューの項目の数を確かめる既存のテストを1つ増える形に直す。
- F の流れの E2E の始めに投入する版 2 の DSL の見本（流れ用）を用意し、ファイルの番号を 040 より後に決める（NFR6.3）。

## 9. 承認の場の決定と直し

- **決定**: 依頼者は NFR 要件の承認の場で Request Changes を選び、直す範囲を「Major 11 件だけを直す」とした。この単位で直すのは R-01 の1件で、ほかの指摘（R-02 以降）は直さない。
- **R-01（Major）**: NFR6.3 の F の流れの E2E で、テーブルの主権限を READ にして保存する前提の適用済みの DSL をどう用意するかが書かれていなかった。app-frame-ui の NFR 要件の Q2 で依頼者が決めた形にそろえ、NFR6.3 に次を足した: 流れのファイルは始めに自分で版 2 の DSL を投入して適用する、前のテストの状態に頼らない、後始末は無し、流れのファイルは 040 より後の番号に置く、B8 の前半と B9 の後半は同じファイルでこの適用の後に続ける。8節の引き継ぎに見本の用意と番号を足した。`traceability.json` は ID と行が変わらないため直していない。
