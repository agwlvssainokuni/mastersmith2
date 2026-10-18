# セキュリティ・アクセシビリティ・言語・テスト・運用の要件 — U3 u3-businessdata-screens

U3 は種類 `ui` の単位で、段の定義により performance・security・tech-stack・traceability だけを作る。そのため、この文書に NFR4〜NFR8 を節に分けて書く。U3 は SQL も対象DB の接続も持たないが、画面で主キー・行の値・検索の値を扱い、E2E では対象DB の資格情報を使う。

## 上流の ID との対応

| 上流 | 上流の枝番 | この文書の ID | 扱い |
|---|---|---|---|
| NFR4 セキュリティ | 4.1 SQL の組み立て | NFR4.1 | N/A（SQL を作らない） |
| | 4.2 行の値をログ・トレース・エラー応答に出さない | NFR4.2 | 同じ意味で、画面の側を受け持つ |
| | 4.3 書き込みの接続の分離 | NFR4.3 | N/A（接続を持たない） |
| | 4.4 問い合わせの時間の上限 | NFR4.4 | N/A（サーバーの側。画面は `performance-requirements.md` の NFR1.9） |
| | （足す） | NFR4.20〜NFR4.26 | U2 の割り振りのとおり 4.20 から |
| NFR5 アクセシビリティ（枝番なし） | — | NFR5.6〜NFR5.10 | U4 の 5.1〜5.5 の次から |
| NFR6 言語（枝番なし） | — | NFR6.3・NFR6.4 | U4 の 6.1・6.2 の次から |
| NFR7 テスト（枝番なし） | — | NFR7.9〜NFR7.13 | 7.8 は `performance-requirements.md`。7.8 から振る理由は「上流との差」の4 |
| NFR8 運用（枝番なし） | — | NFR8.7 | U2 の割り振りのとおり |

NFR1〜NFR3 は `performance-requirements.md` に書いた。

## 1. セキュリティ（NFR4）

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR4.2 | 主キー・行の値・検索の値・入力の値を、次のどこにも置かない（D2・D8、CQ2: A）。<br>・道（URL）・履歴の状態（置くのは意味の無い印 `{ rowToken }` だけ）<br>・ブラウザの保存（localStorage・sessionStorage・IndexedDB）<br>・DOM の `id`・`data-testid`（Table の `getRowId` はページの中の番号）<br>・`console` の出力<br>・E2E の注記・添付・標準出力・失敗の文 | 画面の部品のテストで、道と履歴の状態に主キーが無いこと。コードの検索で `console` と保存の呼び出しを洗い出す。E2E の json の報告を見本の主キーの値で検索する | Code Generation（B5・B6） |
| NFR4.20 | 値と表示名は文字として描き、HTML として描かない（`dangerouslySetInnerHTML` を使わない。oxlint の `react/no-danger`）。リンクの先は、道を作る1か所の関数で作った道だけにし、値から `href` を作らない（`javascript:` の道を作らない）（D16） | `<script>`・`<img onerror>`・`"`・`'`・`javascript:` を含む値と表示名で、一覧・詳細・フォームに文字として出ること、リンクの先が決めた道の形であることの画面のテスト | Code Generation（B5・B6） |
| NFR4.21 | 行の操作の `data-testid` の接頭辞は `tables-row-actions-r<ページの中の番号>` とし、主キーを含めない（U4 からの依頼への答え）。U4 の passes の確かめの部品は、この接頭辞で行の操作を見つける | 画面の部品のテストで `data-testid` の形を確かめる | Code Generation（B5） |
| NFR4.22 | 記憶の寿命と上限（Q3: A）。<br>・印から主キーを引く関数は読むだけで消さない（ブラウザの戻る操作で詳細へ戻れる）<br>・印は最大 100 個までとし、古いものから捨てる。捨てた印を引くと一覧へ移る（D2）<br>・一覧の状態はテーブルごとに最大 100 テーブル分を持つ<br>・全部を消す時: ログアウト、トークンの更新に失敗してログインの画面へ戻ったとき、別の利用者でログインしたとき<br>・記憶は画面の外のモジュールの変数だけに持ち、ブラウザの保存に写さない | 単体テストで、101 個目で最も古い印が捨てられること、3つの消す時のそれぞれで印と一覧の状態が無くなること、読んだ後も印が残ること | NFR 設計で作りを決め、Code Generation（B5）で確かめる |
| NFR4.23 | 本文の大きさと値の長さを、画面でも確かめる（Q4: A）。サーバーの判定を正とする。<br>・値の長さ: 検索の値と主キーは1つ 1,000 字、書き込みの値は 10,000 字（DSL・DB の長さが短ければそちら）。コードポイントで数え、入力から離れたときと送るときに確かめる<br>・本文: 送る前に UTF-8 のバイト数を測り、上限（読み取り・削除 64 KB、作成・更新 4 MiB）を超えるなら送らずに誤りを出す<br>・413 `PAYLOAD_TOO_LARGE` を受けたときの文を、機能設計の 6.1 の表に足す（NFR6.3）。入力した値は消さない | 上限ちょうどは送り、上限 + 1 字・+ 1 バイトは送らないことの単体テスト。413 の応答の見本で文が出ることの画面のテスト | Code Generation（B5・B6） |
| NFR4.24 | 応答の本文は `unknown` で受け、型の項目を確かめてから写す（D5）。形の崩れは通信の失敗の表示にし、黙って既定の値で動かさない。<br>・入力にするカラムに `formPart`・`nullable`・`required` のどれかが欠けたら、形の崩れにする（承認の場の決定）<br>・`formPart` の値域に `lookup` を含めない。知らない列挙の値も形の崩れにする | 写しの関数の単体テストで、3つの項目の欠け・`lookup`・知らない値で通信の失敗になること | Code Generation（B5・B6） |
| NFR4.25 | E2E の対象DB の資格情報（読み取り・書き込みのアカウントのパスワード）と接続先は、Playwright の `webServer.env` に置かずプロセスの環境変数で渡す（`frontend/playwright.config.ts` の 47〜55行と同じ形）。`SECRET_ENV_NAMES` に足し、報告の部品（`frontend/playwright-secret-check-reporter.ts`）が json の報告と `test-results/` を検索して、値が無いことを確かめる（team.md）。見本の行の値は架空のものだけにする | E2E の全件の実行で、報告の部品が通ること | Code Generation（B6） |
| NFR4.26 | 画面の出し分けを、サーバーの判定の代わりにしない（project.md の Mandated）。ボタンと行の操作は定義の `operations` に従って出すだけで、判定はサーバーが行う。権限の無いテーブルへ直接移ったときは 403 の表示にする（6.1） | 画面のテストで、`operations` に無い操作のボタンが出ないこと、直接移ったときに 403 の表示になること。サーバーの判定は U1・U2 の結合テスト | Code Generation（B5・B6） |

NFR4.1・NFR4.3・NFR4.4 は U3 に当たらない（SQL・接続・問い合わせの時間の上限を持たない）。

脅威の見立て: U3 が新しく見せる値は、もとからサーバーが権限で絞って返す値だけである。気をつけるのは、(1) 値を HTML として描くこと、(2) 主キーなどの値が道・履歴・保存・記録へ漏れること、(3) E2E の資格情報が報告に残ること、(4) 画面の出し分けを判定とみなすこと、の4つで、NFR4.20〜NFR4.26 で受ける。

## 2. アクセシビリティ（NFR5）

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR5.6 | 実際のブラウザの axe（WCAG 2.0・2.1 の A・AA）を、`frontend/e2e/support/displayCombos.ts` の 20 組のすべてで、一覧・詳細・作成・更新について流す。次の状態を含める: 誤りの状態、長い表示名、100 カラムの表、NULL の行、開いた検索の項目、開いた行の操作のメニュー、削除と離れる確かめの Modal、375px の幅、200% の拡大。規則・要素は外さない（project.md の Forbidden）。画面の部品のテストでは、部品ごとに vitest-axe を1件入れる | E2E 185 と画面の部品のテスト。違反の件数を記録する | Code Generation（B5・B6）、Build and Test |
| NFR5.7 | 開いたメニュー・Modal・Tooltip が画面の中に収まり、画面全体が横にスクロールしない（375px を含む）。表の右端の行の操作の Dropdown は `placement="bottom-end"` にする（project.md の学び） | E2E 185 で、開いた部品の位置が画面の中にあることと、横のスクロールが無いことを確かめる（`frontend/e2e/support/overflow.ts`） | Code Generation（B5・B6） |
| NFR5.8 | B5・B6 の終わりの 4.14 の試し（D31）で、違反を「ファイル × 表示の組 × 規則 × 要素（`data-testid`）」の形で記録する。axe の選択子は出さない（U4 の NFR4.5）。あわせて、行の操作のトリガーと行のリンクが規則 `label-content-name-mismatch` の passes・incomplete・violations のどれに入ったかを記録し、U4 の passes の対象の候補にする（U4 のレビューの R-01）。試しの版は統合しない | 試しの記録 | Code Generation（B5・B6） |
| NFR5.9 | 行のリンクの見える文字が空にならないこと（R-11）。<br>・`rowLabelColumns` の最初のカラムの書式の後の文字が空白だけなら、見える文字を「（空）」にし、行のラベルにも含める<br>・最初のカラムが一覧の列（`list.visible`）に無いときは、行のリンクを出さず、入口を行の操作の「詳細」だけにする<br>どちらでも、見える文字は名前に含まれる（WCAG 2.5.3） | 性質ベースのテスト（NFR7.10）と画面の部品のテスト | Code Generation（B5） |
| NFR5.10 | キーボードだけで、メニュー → 一覧 → 検索 → 並べ替え → ページ送り → 詳細 → 更新 → 保存 → 削除の確かめ、を最後まで行える。フォーカスが見え、閉じ込められない。件数と表示の範囲は `aria-live` で伝え、詳細は開いたら見出しへ、誤りはまとめへフォーカスを移す（D12・D18・D22）。動きは `prefers-reduced-motion` を守る | E2E 180 の中のキーボードの操作と、画面の部品のテスト | Code Generation（B5・B6） |

## 3. 言語（NFR6）

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR6.3 | 画面の文言は日本語と英語の両方を用意し、既定を日本語とする。機能設計の 6.1 のすべての行と、足す 413 の文（NFR4.23）を含める。値の書式（D15）は日英で同じ形にする | 文言の鍵が日英でそろうことの単体テスト | Code Generation（B5・B6） |
| NFR6.4 | 保存の 403 の別の文（R-06。鍵 `tables.problem.writeForbidden`）は、コード生成の計画（B6）で足すと決めたときに日英で用意する | NFR6.3 と同じ | コード生成の計画（B6） |

## 4. テスト（NFR7）

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR7.9 | team.md の「業務データの一覧・詳細」の必須のテストのうち、画面の分を書く。<br>・画面ごとのアクセシビリティ検査（NFR5.6）<br>・権限の無いテーブルへ直接移ったときの 403 の表示、操作の可否に合わせてボタンが出ないこと（NFR4.26）<br>・画面に出す値が文字として出ること（NFR4.20）<br>・道に乗る主キーの値: 主キーは道に置かないため、`/`・`%`・`?`・`#`・空白・引用符を含む主キー（複合の主キーを含む）が道に現れず、記憶を通して詳細へ往復できること（NFR4.2）<br>・描画の後に反映される値は `waitFor` で待つ。テストの時間の上限は原因を確かめずに延ばさない | 画面の部品のテスト（Vitest・Testing Library・user-event・vitest-axe） | Code Generation（B5・B6） |
| NFR7.10 | 純粋な関数（書式・行のラベル・変更の比べ・本文の写し）に fast-check の性質ベースのテストを当て、失敗のときの乱数の種を記録する（D29）。性質には、BIGINT・DECIMAL の桁を失わないこと、変えないカラムが本文に入らないこと、`<`・`"`・`'` が文字のまま返ること、行のリンクの見える文字が必ずリンクの名前と行の操作の名前に含まれることを入れ、先頭のカラムが空の文字列の入力を含める（R-11） | fast-check のテスト（`verify` の中） | Code Generation（B5・B6） |
| NFR7.11 | 「1回だけ飛ばす」の旗のテスト（R-12）。`src/shared/unsaved-guard/` の単体テストに次を入れる: `markClean()` の後に道の変わらない移動をしても、次に変更を作れば次の移動が止まること。あわせて機能設計の D25 の3つ（`markClean()` の後の移動が止まらない、その後に変更を作ると止まる、呼ばなければ今どおり止まる）と、B6 の画面のテストで保存の成功から詳細への移動で確かめの Modal が出ないこと | 単体テストと画面の部品のテスト | NFR 設計で旗の作りを決め、Code Generation（B6）で確かめる |
| NFR7.12 | frontend のカバレッジの下限（全体の合計で行 80%・分岐 70%）を保ち、除外を増やさない。`src/features/tables/` のカバレッジの値を Build and Test で記録する（team.md） | `npm run test:coverage`（`verify` の中） | Code Generation（B5・B6）、Build and Test |
| NFR7.13 | E2E（Q2: A）。<br>・業務データの代表の流れ 180 を1本足す（一覧 → 作成 → 詳細 → 更新 → 削除）。見本の PostgreSQL の、流れの専用のスキーマで行い、行・利用者・ロールは流れの中で自分で作る。初期管理者の状態は変えない<br>・`./gradlew e2eTest` は始める前に対象DB に届くかを確かめ、届かなければ起動の手順を示して失敗する（Mailpit と同じ扱い）<br>・170 の書き換えと 185 は、型の付いた1つの見本で応答を差し替える。見本の形が本物の応答と合うことを、180 の中で毎回確かめる（project.md の学び）<br>・170 の書き換え・185・測りのテストは本数に数えない | E2E の全件の実行 | Code Generation（B5・B6）、Build and Test |

## 5. 運用（NFR8）

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR8.7 | 403 を受けても、画面は自動で要求し直さない（決まった間隔の読み直しもしない。`performance-requirements.md` の NFR1.9）。警報 `ms-forbidden` の数が、利用者の操作の数を表すようにするため。しきい値（20）の見直しは Observability Setup が受け持つ | フックのテストで、403 の後に要求が増えないこと | Code Generation（B5・B6）。しきい値は Observability Setup |

## 上流との差

1. **承認の場の決定**（`aidlc/spaces/default/intents/261016-business-data-views/construction/functional-design/memory.md` の Open questions）: `nullable` は U2 が足し、`formPart` の値域から `lookup` を外し、U3 は `formPart`・`nullable`・`required` が欠けたら形の崩れとする。機能設計の 6.2 の `text`・`lookup` の行は `text` だけの行と読み替え、D19 の形の崩れの条件に `nullable`・`required` の欠けを足して読む（NFR4.24。文書は書き換えない。レビューの R-09・R-10 はこれで解けた）。
2. **413 の行が増える**: 機能設計の 6.1 の表示の表に 413 `PAYLOAD_TOO_LARGE` の行を足す（NFR4.23・NFR6.3）。
3. **team.md の E2E の読み方**: 「業務データを表示する E2E は見本の対象DB（PostgreSQL）がコンテナで動いていることを前提にする」は、代表の流れ 180 に当てると読む。170 の書き換えと 185 は流れではなく、型の付いた見本で応答を差し替える（NFR7.13、Q2: A）。
4. **ID の振り方**: U2 の割り振りでは U3 の NFR7 は 7.7 からだが、U4 がすでに NFR7.4〜7.7 を使っているため 7.8 から振る（7.8 は `performance-requirements.md`）。あわせて、U4 の NFR4.5〜4.9・NFR7.1〜7.7 は U1・U2 の番号と重なっている。U4 を直すかは承認の場で諮る。

## 後の段に回すこと

- **NFR 設計**: 記憶の作り（印・一覧の状態・定義の持ち方、上限での捨て方、消す時の知らせの受け方。NFR4.22）。`markClean()` の旗の作りと下ろす条件（NFR7.11、R-12）。
- **Infrastructure Design**: E2E のアプリ（PC の上の `java -jar`）から見本の PostgreSQL に届く形。今の `compose.yaml` の `targetdb-postgres` は PC にポートを開けていない（137行）。あわせて、E2E の専用のスキーマと書き込みのアカウントの用意、資格情報の渡し方。
- **コード生成の計画**: R-04（作業ロールの確かめの後の画面）、R-06（保存の 403 の文。NFR6.4）、主キーのカラムを更新で変えるとき（U2 で書けるか、保存の後の主キーの作り直し）。E2E が使う DSL の適用の順（040 との関係）。

## U1・U2・U4 とそろえる点

- 値の長さ（検索・主キー 1,000 字、書き込み 10,000 字）と本文の上限（64 KB・4 MiB）は、U1 の NFR4.5・U2 の Q3 の値をそのまま使う。値を変えるときは3単位で一緒に変える。
- 503 の2つの code と 409 の `BUSINESS_TARGET_DB_MISMATCH` の文の出し分けは、U1 の NFR4.8 と U2 の失敗の分け方（書き込みの TIMEOUT は「保存されたか分かりません」）に合わせる。
- 定義の応答の項目の持ち主は、読み取りの項目が U1（B2）、検証の材料（`formPart`・`required`・`nullable` など）が U2（B4）。
- axe の記録の形と、選択子を出さないことは U4 の NFR4.5・NFR5.4 と同じにする。行の操作の名前は U4 の `rowActionsName` を使い、passes の確かめの部品は NFR4.21 の接頭辞で U4 と共有する。
- 漏えいの確かめは、U1・U2 の `*SecretLeakIT`（サーバーの側）と、U3 の E2E の報告の確かめ（画面の側）で分けて受け持つ。

## Sources

- 質問と答え: `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/nfr-requirements/nfr-requirements-questions.md`（Q1〜Q5: A、まとめの確認: Looks correct）
- 要件: `aidlc/spaces/default/intents/261016-business-data-views/inception/requirements-analysis/requirements.md`（NFR4〜NFR8）
- 機能設計: `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/functional-design/functional-spec.md`（D2・D5・D8・D9・D12・D16・D18・D19・D22・D25・D29〜D31、6.1・6.2、11節・12節）、`frontend-components.md`（3節・4節・7節）
- レビュー: `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/functional-design/units/u3-businessdata-screens/7dacbaa3e842d0af/1.json`（R-09〜R-12）、`aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/nfr-requirements/units/u4-a11y-carryover/00d44a7a2452e6b7/1.json`（R-01）
- 画面イメージ: `aidlc/spaces/default/intents/261016-business-data-views/inception/refined-mockups/accessibility-checklist.md`
- U1・U2・U4 の NFR 要件: `aidlc/spaces/default/intents/261016-business-data-views/construction/u1-businessdata-read/nfr-requirements/security-requirements.md`・`reliability-requirements.md`、`aidlc/spaces/default/intents/261016-business-data-views/construction/u2-businessdata-write/nfr-requirements/nfr-requirements-questions.md`、`aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/nfr-requirements/security-requirements.md`
- コード: `frontend/playwright.config.ts`（47〜55行）、`frontend/playwright-secret-check-reporter.ts`、`frontend/e2e/support/secretValues.ts`・`displayCombos.ts`・`overflow.ts`、`compose.yaml`（134〜166行）、`build.gradle.kts`（418〜450行）
- 決まり: `aidlc/spaces/default/memory/team.md`（Testing Posture・Code Style）、`aidlc/spaces/default/memory/project.md`（Forbidden・Mandated・学び）

## Assumptions & Open Questions

- E2E のアプリから見本の PostgreSQL に届く形は未決で、Infrastructure Design で決める（NFR7.13）。
- 4.14 の試しで、行のリンクと行の操作が passes に入るかは未確認で、B5・B6 で確かめる（NFR5.8）。
