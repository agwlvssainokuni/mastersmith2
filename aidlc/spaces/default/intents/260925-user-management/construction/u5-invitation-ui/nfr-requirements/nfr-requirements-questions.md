# NFR Requirements — Questions（U5 招待の管理の画面 / u5-invitation-ui）

U5 は、管理者が招待中の人を一覧で確かめ、招待・送り直し・取り消しを行う画面の単位です（S1・S1-M1・S1-M2。種類 ui のため、作る成果物は性能・セキュリティ・技術の選択・traceability の4つです。拡張性・信頼性・観測性は service の単位だけ）。画面の単位の非機能の多くは U4 の NFR 要件（最初の画面の時間、Playwright＋axe-core の実際のブラウザの検査を B5 で U5〜U7 の画面に足すこと、vitest-axe、依存、CSP）と、U3 の NFR 要件（API の応答時間）、承認済みの機能設計で決まっています。そのため、まず NFR の要点（案）を示し、上流から決まらない2点（狭い幅（768px 未満）の確かめ方と、招待の画面の時間の目標）だけを質問にします。

読んだ上流:

- 承認済みの機能設計 `aidlc/spaces/default/intents/260925-user-management/construction/u5-invitation-ui/functional-design/`（`functional-spec.md` の D1〜D14・W1〜W10・6節の応答ごとの動き・7節の文言・9節の上流との差、`frontend-components.md` の 2節・3節・5節・7節、`traceability.json`）
- 依存する単位の NFR 要件（同じ段、READY）`construction/u4-display-foundation/nfr-requirements/`（NFR6.1 最初の画面 2 秒、NFR7.1〜NFR7.5 アクセシビリティと B5 で U5〜U7 の画面を足すこと、NFR9.4 CSP、NFR9.6 axe-core、NFR9.11 検査は流れの本数に数えない、`tech-stack-decisions.md` の上流との差の「狭い幅を検査に足すかは B5 の計画で依頼者に確かめる」）・`construction/u3-invitation/nfr-requirements/`（NFR6.1 招待・送り直しは同時 10 件で p95 5 秒、NFR6.2 受け手が応答しないときの既知の限界、NFR6.3 一覧・取り消しは p95 1 秒）
- 要件 `aidlc/spaces/default/intents/260925-user-management/inception/requirements-analysis/requirements.md`（NFR1〜NFR11、特に NFR1・NFR2・NFR4・NFR6・NFR7・NFR8・NFR9）
- 契約 `inception/contract-design/contract-summary.md`（C5・C9）、画面 `inception/refined-mockups/interaction-spec.md`（2〜4節。幅はパソコンを主とし、狭い幅（768px 未満）でも崩れずに読めて操作できる。一覧の表は狭い幅で横に動かして見る。Modal は狭い幅で画面の幅いっぱい）
- 既存のコード `frontend/src/shared/api-client/apiClient.ts`（要求に時間切れを持たない）・`frontend/playwright.config.ts`（`Desktop Chrome` の1つの project）・`frontend/e2e/`（010〜040）・`frontend/src/features/dsl/`（前の Intent の管理画面の作り）、`vendor/make-you-chic-ui`（固定先 edb1f94。新しい Table・Modal・Button は `origin/main` 735ef04 にあり、固定先の更新は B4。変更しない）
- 前の Intent の画面の単位の NFR 要件 `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/u5-dsl-admin-ui/nfr-requirements/performance-requirements.md`（NFR1.18: 画面の時間の目標を置き、E2E で測って記録し、統合の関門にしない前例）
- 決まり `aidlc/spaces/default/memory/team.md`（Testing Posture の画面のテスト・アクセシビリティ・E2E の本数・性質ベースのテスト、Deployment のフロントエンドの依存の脆弱性、Code Style のフロントエンド）・`project.md`（Forbidden の招待のトークン・メールアドレス、make-you-chic-ui を変えない）

## NFR の要点（案）

1. **性能（一覧の読み込みと描画）**: 一覧は 20 件ごとで、サーバーの順のまま描き、画面で並べ替えない（D1）。1ページの行は最大 20 件で、描く量は小さい。読むのは画面を開いたとき・ページを変えたとき・操作の後・「もう一度読み込む」のときだけで、決まった間隔の自動の読み直しはしない（D2）。読み直しが重なったら最後に始めた読み直しの答えだけを使い、画面を離れた後の答えも捨てる（D2）。一覧の API の時間は U3 の NFR6.3（同時 10 件で p95 1 秒）で押さえ、Performance Validation の段の k6 で測る。画面の時間の目標をどう置くかは Q2 で決める。
2. **性能（招待・送り直しの待ちの見せ方）**: 招待・送り直しの API は SMTP の送信を含めて同時 10 件で p95 5 秒（U3 の NFR6.1）で、受け手が応答しないときは U1 の時間切れ（3 秒）の後に `sendResult` FAILED で応答する（U3 の NFR6.2）。画面は送信の間、押したボタンを make-you-chic-ui の Button の `loading` にして「送信しています」と文字で示し、フォーカスを保つ（D9）。招待の送信中は Modal を閉じない（D9）。取り消しの要求中も同じ（D9）。
3. **性能（画面の側の時間切れ）**: 画面は要求に独自の時間切れを置かない（既存の ApiClient も持たない）。待ちの上限はサーバーの時間切れ（U1 の SMTP の時間切れ、U3 の NFR6.2）に任せる。そのため、受け手が応答の手前で遅れ続ける既知の限界（U1 の NFR6.3、U3 の NFR6.2 で引き継いだもの）の間は、招待の Modal が閉じられないまま「送信しています」が続く。これは画面の単位では変えず、この限界を画面の側でも引き継ぐことを記録する（前の Intent の DSL の管理画面も、長い照合を画面の時間切れなしで待つ）。
4. **性能（初回の JavaScript の大きさ）**: 既存の目安（初回の読み込みの JavaScript は gzip で 500KB 以内。超えたら警告で、統合は止めない。`frontend/scripts/check-bundle-size.mjs`、U4 の NFR6.3）をそのまま当てる。U5 が足すのは機能 `invitation` の画面・部品・フック・純粋な関数と文言で、make-you-chic-ui の Table・Modal・Button・Alert・Badge・RadioGroup・Toast を使う（新しい実行時の依存は無い）。変更の前後の値はコード生成で測って記録する。
5. **セキュリティ（画面に出す個人に関する値）**: 画面に出す・知らせる個人に関する値は、招待のメールアドレスと招待した管理者の氏名だけ（D11）。招待のトークン・招待の URL は扱わない（一覧・招待・送り直しの応答に無い、U3 の BR5.4。`project.md` の Forbidden）。応答の値をブラウザのコンソール・localStorage・sessionStorage・URL に出さない。今のページは画面の中の状態だけに持ち、URL に載せない（D1）。コード生成の部品のテストで、一覧の表示にトークン・URL が出ないこと（`frontend-components.md` の 7節の `InvitationList`）に加え、操作の後に localStorage と sessionStorage に招待のメールアドレスが入らないこと、URL（`location`）が変わらないこと、`console` の出力に応答の値が出ないことを確かめる。
6. **セキュリティ（失敗の文言）**: 失敗の文言はサーバーの `detail` を使わず、応答の `code` から画面の文言の鍵を選ぶ。知らない `code`・`code` の無い応答・通信の失敗は、状態コードの種類ごとの一般の文言にする（D4）。応答の値を HTML として差し込まない（`{{ }}` の値は React の文字として描く、7節）。`react/no-danger` などの既存のリンタの決まりのまま。
7. **セキュリティ（認可）**: 画面でメニューやボタンを隠すことは、サーバー側の管理者の判定の代わりにしない（D14、`team.md`）。招待・一覧・送り直し・取り消しの API の 401・403・200 はサーバー側のテスト（U3）で確かめる。401 は既存の ApiClient の更新とログインの画面への移動に任せ、403 は一般の 4xx の文言で示す（6節）。NFR4 は U5 では画面の側の扱いだけを書き、サーバー側の確かめは U3 を指す。
8. **セキュリティ（CSP・依存・make-you-chic-ui）**: 外部への通信・外部の資源を足さず、CSP は変えない（U4 の NFR9.4）。埋め込みのスクリプト・スタイルを足さない。目立たせた行は機能の CSS の `:has()` で示す（素の CSS、埋め込みのスタイルではない、W7）。新しい依存は足さない。make-you-chic-ui の新しい Table・Modal・Button は、B4 の固定先の更新（edb1f94 → 735ef04、承認を得た専用のコミットで前後のハッシュを記録、`project.md` の Mandated）の後に使い、中身は変えない（`project.md` の Forbidden）。
9. **アクセシビリティ（目標と部品ごとの検査）**: 目標は WCAG 2.1 AA（U4 の NFR7.1）。画面部品（`InvitationList`・`InvitationUnavailableAlert`・`InviteDialog`・`CancelConfirmDialog`・`InvitationAdminPage`）ごとに vitest-axe の検査を1件入れ、違反 0 件とする（`team.md`、`frontend-components.md` の 7節）。状態と送信の結果は文字で示し色だけに頼らない（D5）、行のボタンの読み上げの名前にメールアドレスを含める（D13）、言語の名前に `lang` 属性を付ける（W1）、取り消しの確かめは `role="alertdialog"` ではじめのフォーカスを「やめる」に置く（D12）。
10. **アクセシビリティ（実際のブラウザの検査）**: U4 の NFR7.3・NFR7.5 のとおり、B5 で Playwright＋axe-core の検査に U5 の画面を足す。対象は一覧の画面（行ありの状態）・招待の入力の Modal・取り消しの確かめの Modal・招待を使えないときの警告の表示で、テーマ2×文字の大きさ3の6組とブランドカラー4×テーマ2の8組（`md`）を切り替え、コントラストを含む違反 0 件と、文書の横のはみ出しが無いことを確かめる。一覧の表は Table の包む要素の中で横に動くため、文書そのものははみ出さない前提。行を置くため、検査は招待を自分で用意する（前のテストの状態に頼らない、U4 の NFR9.11）。狭い幅（768px 未満）をこの検査に足すかは Q1 で決める。
11. **多言語**: U5 の文言（7節の `invitation.*`）は ja・en の両方を持ち、Table の `labels` の7項目と Modal の `closeLabel` も画面の言語で渡す（NFR8、7節）。文言の鍵が ja と en でそろっていることと、en の画面で make-you-chic-ui の日本語の既定の文言が出ないことをテストで確かめる（`frontend-components.md` の 7節）。
12. **テストとカバレッジ**: フロントエンドのカバレッジの下限（行 80%・分岐 70%）を守る（NFR9、`team.md`）。性質ベースのテスト（fast-check、失敗時の種を記録）を、件数の範囲・ページの数・ページの補正の純粋な関数（`paging.ts`）に当てる（機能設計 8節）。`formatDateTime` を `shared/format` へ移した後も、DSL の画面のテストがそのまま通る（機能設計の Q1 A）。この単位では流れの E2E を足さない（招待から登録の完了までの E2E-1 は U6、`team.md` の Intent ごとに1本まで）。E2E-1 は U5 の画面を通るため、U5 の変更の後に E2E-1 が通ることを B5 で確かめる。
13. **traceability の見込み**: U5 に当たる要件の非機能は NFR1（画面は招待のトークン・URL を扱わない）・NFR2（メールアドレスを画面の外（ログ・保存・URL）に出さない）・NFR6（画面の側の待ちの見せ方と時間。Q2）・NFR7（アクセシビリティ。Q1）・NFR8（文言）・NFR9（テスト・カバレッジ・E2E-1 が通ること）。NFR4 は画面の側の扱い（D14）だけで、サーバー側の確かめは U3 のため N/A とし理由を書く（U4 と同じ扱い）。NFR3・NFR5・NFR10・NFR11 は `N/A` とし、理由を書く。

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| 一覧は 20 件ごと、サーバーの順のまま。今のページは画面の中だけに持ち URL に載せない。自動の読み直しはせず、重なった読み直しは最後の答えだけを使う | 機能設計 D1・D2（Q2 B・Q3 A） |
| 一覧・取り消しの API は同時 10 件で p95 1 秒、招待・送り直しは SMTP を含めて p95 5 秒。受け手が応答しないときは時間切れの後に FAILED で応答し、遅れ続ける受け手では数倍になりうる既知の限界がある | U3 の NFR6.1〜NFR6.3、U1 の NFR6.3 |
| 送信の間はボタンを `loading` にしてフォーカスを保ち、招待の送信中と取り消しの要求中は Modal を閉じない | 機能設計 D9 |
| 画面に出す個人に関する値はメールアドレスと招待した管理者の氏名だけ。トークン・URL は扱わない | 機能設計 D11、U3 の BR5.4、`project.md` の Forbidden |
| 失敗の文言は `code` から選び、サーバーの `detail` を出さない | 機能設計 D4、要件 NFR1・NFR2 |
| 画面で隠すことはサーバー側の判定の代わりにしない。API の 401・403・200 は U3 のサーバー側のテストで確かめる | 機能設計 D14、要件 NFR4、`team.md` |
| 最初の画面（ログインの画面）が出るまで 2 秒以内（E2E で測って記録、関門にしない）。初回の JavaScript は gzip で 500KB を目安 | U4 の NFR6.1・NFR6.3 |
| 目標は WCAG 2.1 AA。画面部品ごとに vitest-axe を1件。Playwright＋axe-core の検査（6組＋8組、既定の幅）に U5〜U7 の画面を B5 で足す。この検査は流れの本数に数えない | U4 の NFR7.1〜NFR7.3・NFR7.5・NFR9.11、`team.md` の Testing Posture |
| CSP を変えず、外部の資源を足さない。新しい依存は足さない。make-you-chic-ui は変えず、固定先の更新は B4 の専用のコミット | U4 の NFR9.4、`project.md` の Forbidden・Mandated、Bolt の計画 |
| 文言は ja・en の両方。Table の `labels` と Modal の `closeLabel` も画面の言語で渡す | 要件 NFR8、機能設計 7節 |
| フロントエンドのカバレッジの下限は行 80%・分岐 70%。性質ベースのテストは `paging.ts` | `team.md` の Testing Posture、機能設計 8節 |
| 招待から登録の完了までの E2E-1 は U6 が持ち、U5 では流れの E2E を足さない | 機能設計 8節、U6 の機能設計 W13、`team.md` |
| API の負荷の試験は Performance Validation の段で k6 を使って測る | U3 の NFR 要件、`aidlc-state.md` の段の一覧 |

## Q1. 狭い幅（768px 未満）で崩れずに読めて操作できること（`interaction-spec.md`）を、どう確かめますか？

理由: `interaction-spec.md` は「パソコンの幅を主とし、狭い幅（768px 未満）でも崩れずに読めて操作できる」とし、U5 の一覧の表は狭い幅で横に動かして見る（Table の包む要素の中で横に動く）、Modal は画面の幅いっぱい、としています。U4 の NFR7.3 の実際のブラウザの検査は既定の幅（Playwright の `Desktop Chrome`、1280px）だけで、狭い幅を検査に足すかは「B5 の計画で依頼者に確かめる」とされました（U4 の `tech-stack-decisions.md` の上流との差）。U5 は、この Intent の画面で最も横に広い表（8 列）を持つため、狭い幅で文書そのものがはみ出さないこと（表だけが包む要素の中で動くこと）と、横に動く領域にキーボードで届くこと（axe の `scrollable-region-focusable`。今の設計は、包む要素の中の「取り消す」とページ送りのボタンに Tab で届く形、機能設計 W1 の 5）は、実際のブラウザでしか確かめられません。ここで決めた確かめ方は、U6・U7 の画面にも同じく当てる前提です（それぞれの NFR 要件で引き継ぐ）。

- A. 自動の検査は既定の幅のまま（U4 の NFR7.3 のとおり）。狭い幅は Build and Test で、実際のブラウザの幅を 375px にして U5 の画面（一覧・2つの Modal）を開き、点検表（文書の横のはみ出し・表の横の移動・Tab で表の中のボタンに届く・Modal が幅いっぱい）で目で確かめて記録する
- B. U4 の NFR7.3 の検査に狭い幅を1つ足す。幅 375px（高さ 812px）で、テーマ2×文字の大きさ3の6組（ブランドカラーは既定の `blue`）を切り替え、文書の横の大きさが表示の幅を超えないこと（表は Table の包む要素の中だけで横に動く）と、axe-core の違反 0 件（`scrollable-region-focusable` を含む）を確かめる。U5 の一覧の画面（行あり）・招待の入力の Modal・取り消しの確かめの Modal に当て、B5 で U6・U7 の画面にも同じく当てる。ブランドカラーの8組は幅に関わらないため既定の幅だけのまま。新しい依存は足さない（推奨）
- C. 狭い幅は確かめない。画面の段の決まり（`interaction-spec.md`）どおりに作るだけとし、U4 の NFR7.3 の既定の幅の検査だけで NFR7 を満たしたとする
- X. Other (please specify)

[Answer]: B

---

## Q2. 招待の画面の時間の目標を、どうしますか？

理由: 要件 NFR6 は API の応答時間だけを決めており、U3 の NFR6.1・NFR6.3 で一覧 p95 1 秒・招待と送り直し p95 5 秒が k6 で測られます。画面の時間の目標は、U4 の NFR6.1 でログインの画面（最初の画面）にだけ置かれ、招待の画面（ログインした後の画面）にはありません。前の Intent の DSL の管理画面では、画面を開いてから表示されるまでの目標（3 秒）を置き、Build and Test の E2E（実際のブラウザ）で測って記録し、統合の関門にはしない扱いにしました（jsdom の時間は不安定で、`team.md` の「不安定なテストは統合しない」に当たるため）。U5 の1ページの描画は最大 20 行と小さく、画面の側の待ちは主に一覧の API の時間です。なお E2E-1（U6）と Q1・要点 10 の検査は、どちらも U5 の画面を実際のブラウザで開くため、測る場はすでにあります。

- A. 画面の数値の目標は置かない。一覧と招待の時間は U3 の API の目標（p95 1 秒・5 秒）で押さえ、画面の側は描き方（20 件ごと・自動の読み直しなし・古い答えを捨てる）を部品のテストで確かめる
- B. 目標を置く。ログインした管理者が手元の PC のブラウザで「利用者の招待」を開いてから、一覧の1ページ目（招待を 21 件以上置いた状態で 20 行）が出るまで 2 秒以内、「次へ」を押してから次のページの行が出るまで 1.5 秒以内。Build and Test で、実際のブラウザの E2E の中（要点 10 の検査か E2E-1 のどちらで測るかはコード生成で決める）で 5 回ずつ測って記録し、統合の関門にはしない（U4 の NFR6.1・前の Intent の DSL の管理画面と同じ扱い）。2 秒を超えたときは目標を緩めず、API・描画のどちらが遅いかを確かめて依頼者に相談する（推奨）
- C. B に加えて、受け手が正常に受けるとき、招待の Modal で「招待する」を押してから Modal が閉じて Toast が出るまで 6 秒以内（U3 の NFR6.1 の 5 秒に画面の 1 秒を足す）も測って記録する（E2E の手元の受け手を使う）
- X. Other (please specify)

[Answer]: B

---

## Consolidated Summary Confirmation

答えのまとめ（Q1・Q2 の回答の後に埋めます）:

- NFR の要点（案）は冒頭の「NFR の要点（案）」の 13 件のとおり
- Q1 B: U4 の Playwright＋axe-core の検査に幅 375px を1つ足し、テーマ2×文字の大きさ3の6組で、文書が横にはみ出さないことと axe の違反 0 件（`scrollable-region-focusable` を含む）を確かめる。対象は一覧と2つの Modal で、B5 で U6・U7 の画面にも同じく当てる（依頼者の決定）。新しい依存は足さない。U4 の NFR7.3（既定の幅だけ）への追加として、U5 の成果物に上流との差を書く
- Q2 B: 画面を開いてから一覧（20 行）が出るまで 2 秒以内、「次へ」で次のページが出るまで 1.5 秒以内。Build and Test の実際のブラウザで5回ずつ測って記録し、統合の関門にはしない。U6・U7 の画面もこの考え方（画面の時間の目標を置き、測って記録し関門にしない）にそろえる（依頼者の決定）

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
