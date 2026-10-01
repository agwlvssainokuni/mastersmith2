# NFR Requirements の質問 — u4-admin-forbidden-ui

単位 U4（`aidlc/spaces/default/intents/260930-user-admin/inception/units-generation/unit-of-work.md`、種類 ui）の非機能要件のための質問です。ui の単位のため、成果物は `performance-requirements.md`・`security-requirements.md`・`tech-stack-decisions.md`・`traceability.json` です（拡張性・信頼性・観測の文書は作りません）。読んだ上流は、この単位の承認済みの機能設計 `construction/u4-admin-forbidden-ui/functional-design/`（`functional-spec.md` の D1〜D14・6節・7節・8節・10節と「承認の場の決定」の節、`frontend-components.md` の 3.4・7節・8節、`traceability.json`。ui の単位のため `rules.md` はありません）、要件 `inception/requirements-analysis/requirements.md` の NFR1・NFR3・NFR5・NFR7・NFR8・NFR9、契約 `inception/contract-design/contract-summary.md` の C4、Bolt の計画 `inception/delivery-planning/bolt-plan.md` の B2、前の Intent の画面の単位の NFR（`aidlc/spaces/default/intents/260925-user-management/construction/u4-display-foundation/nfr-requirements/performance-requirements.md` の NFR6.1、`u5-invitation-ui/nfr-requirements/nfr-requirements-questions.md` の Q1・Q2）、既存の E2E（`frontend/e2e/060-invitation-accessibility.e2e.ts`・`100-app-text-contrast.e2e.ts`・`support/axe.ts`・`support/displayCombos.ts`）、決まり `aidlc/spaces/default/memory/team.md`（Testing Posture）・`project.md`（Mandated・Corrections の NFR 要件の学び）です。

## 設計の要点（案）

上流とコードの確認から導ける、この単位の非機能要件の見通しです。質問の答えで決まる点は（Qn）と書きます。ID は成果物での枝番の見込みで、`project.md` の学びのとおり、当たる上流の NFR が無い画面の時間は NFR5 ではなく画面の側として扱い、寄せた先を成果物の冒頭に書きます。

### 性能

1. **S6 への置き換えは同じ描画の中で終わる**（D3）。403 を受けてから S6 が出るまでに、新しい要求も待ちも挟まない。ログインの状態の読み直しは S6 を出した後に始まり、呼び出し元はその結果を待たない（W3 の 6）。このことは部品のテスト（`AdminForbiddenProvider.test.tsx`）で確かめる。
2. **読み直しの回数**: 同じ URL で 403 を重ねて受けても、読み直し（トークンの更新）は1回だけ（D7）。DSL の管理が並べて読む3つの要求で 403 が重なっても、更新の要求は1件にまとまる。部品のテストで更新の呼ばれた回数を数えて確かめる。
3. **読み直しの時間の目安**: 読み直しは既存のトークンの更新の API で行い、その目標（同時 10 件で p95 1 秒、前の Intent の U2 の NFR6.5）をそのまま当てる。画面の側の数値の目標を新しく置くかは（Q2）。
4. **初回の JavaScript の大きさ**: 足すのは小さな部品と関数だけで、新しい依存は無い。既存の目安（gzip で 500KB、警告だけ、`frontend/scripts/check-bundle-size.mjs`）をそのまま当てる。

### セキュリティ

5. **画面の判定はサーバーの判定の代わりにしない**（要件 NFR1、`project.md` の Mandated）。S6 を出すこと、管理のメニューを隠すことは表示だけで、管理の API の 401・403・200 の判定と監査の「アクセスの拒否」は今のサーバーの動作のまま（AC2.2.6 は U3 のサーバー側のテストが受け持つ）。U4 はサーバーのコードを変えない。
6. **判定の条件を広げない**: 「権限が無い」は `/api/admin/` の下・403・`ACCESS_DENIED` の3つがそろうときだけ（D1）。性質ベースのテスト（fast-check、種を記録）で、接頭辞だけ似たパス・code の無い 403・知らない値でも true にならないことを確かめる。
7. **管理者でない利用者が管理の画面を開いたとき**は、サーバーに問わずに S6 を出し（D6）、画面の部品を作らないため管理の API は呼ばれない。管理の画面の URL があることは分かるが、画面の一覧は配る JavaScript からも読めるため、新しく漏れる情報は無い（`frontend-components.md` 8節、承認の場で受け入れ済み）。
8. **401 の扱いとログアウトは変えない**（D9・D10）。読み直しの失敗は今の更新の失敗と同じくログインの状態を消す（承認の場で受け入れ済み）。印を外してもリフレッシュトークンは無効にならない。
9. **表示する文言に原因や内部の値を出さない**: S6 は「この画面を使う権限がありません」だけで、原因（管理者ではなくなった）・応答の `detail`・利用者の値を出さない（D12）。トークン・メールアドレスを画面のログ（`console`）に出さない。E2E を足すときは、`test.step` の題・注記・添付と json の報告にパスワード・トークン・メールアドレスを入れない（`project.md` の学び）。
10. **自分の氏名と言語の反映**（D13・D14）は、ログインしているときだけ当て、ブラウザの保存は言語だけを書き換える（氏名はブラウザに保存しない）。言語は ja・en 以外を当てない。
11. **静的検査**: 既存の oxlint のセキュリティ系のルール（`react/no-danger` など）・ESLint・型検査をそのまま通す。HTML を直接埋め込まない。

### アクセシビリティ（要件 NFR7）

12. **部品のテスト**: 新しい画面部品 `AdminForbiddenView` に vitest-axe の検査を1件入れる（`team.md`、B2 の完了の条件）。Alert が `role="status"` であること、見出しへフォーカスが移ること（`waitFor`）、ForbiddenByApi から ForbiddenByRoute へ移ってもフォーカスとサイドバーの状態が変わらないことを確かめる（`frontend-components.md` 7節）。
13. **実際のブラウザでの検査**: （Q1）。

### 多言語（要件 NFR8）

14. **S6 の文言は ja・en の両方**（7節の3つの鍵）。部品のテストで ja・en の両方の文言を確かめる。見出しはサイドバーの項目の文言の鍵を使い回し、項目が無い URL は共通の見出し「管理」・"Administration"。

### 技術とテスト（要件 NFR9）

15. **新しい依存は足さない**: React・react-router・make-you-chic-ui（`Alert`）・Vitest・Testing Library・user-event・vitest-axe・fast-check・Playwright（Q1 で足すとき）の既存のものだけで作る。make-you-chic-ui は変更しない。
16. **カバレッジ**: フロントエンドの下限（行 80%・分岐 70%、`@vitest/coverage-v8` の `thresholds`）を保つ。バックエンドのパッケージには手を入れない。
17. **E2E の流れの本数**: U4 は流れの E2E を足さない（`030-admin-access.e2e.ts` は変えない）。Q1 で実際のブラウザの検査を足すときも、操作の流れではないため「Intent ごとに代表の流れを1本まで」の本数に数えない（`project.md` の学び）。

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| 「権限が無い」の判定は `/api/admin/` の下・403・`ACCESS_DENIED` の3つがそろうときだけ | 機能設計 D1、契約 C4 |
| S6 の文言（ja・en）と見出しの決め方 | 機能設計 7節・D11・D12 |
| S6 の Alert は info で `role="status"`、見出しへフォーカスを移す | 機能設計 D12、`frontend-components.md` 3.4（承認の場の R-05） |
| 読み直しは権限が無い URL を新しく覚えたときに1回だけ、失敗は今の更新の失敗と同じくログアウト | 機能設計 D7・D9（承認の場で受け入れ） |
| 管理者でない利用者が管理の画面を開くと S6（「ページが見つかりません」ではない） | 機能設計 D6・G1（承認の場で受け入れ） |
| 画面で隠すことをサーバー側の判定の代わりにしない。サーバーの 403 と監査は今のまま、U3 が確かめる | 要件 NFR1、`project.md` の Mandated、機能設計 8節 |
| トークンの更新の API の目標は同時 10 件で p95 1 秒 | 前の Intent の U2 の NFR6.5 |
| 画面部品ごとに vitest-axe の検査を1件、テストの説明文は英語、描画の後の値は `waitFor` で待つ、性質ベースのテストは fast-check で種を記録 | `team.md` の Testing Posture |
| 画面の時間は統合の関門にせず、測って記録する（測るときの扱い） | 前の Intent の U4 の NFR6.1、`team.md` の「不安定なテストは統合しない」 |
| Playwright のアクセシビリティの検査と画面の時間の測りは、流れの E2E の本数に数えない | `project.md` の学び（user-management の U4 の NFR 要件） |
| 実際のブラウザの axe の検査は、表示の設定の 20 組と誤りの状態で行う。狭い幅は 375px で6組 | `project.md` の学び（user-management の U7）、前の Intent の U5 の Q1 B |
| E2E には初期管理者しかおらず、初期管理者の状態は変えない。403 は差し替えて作る | `team.md` の Testing Posture、Units Generation の R-02、機能設計 8節 |
| make-you-chic-ui は変更しない。新しい依存は足さない | `project.md` の Forbidden、機能設計 |
| 列挙の防止（NFR2）・同時の操作（NFR4）・接続の使い方（NFR6）・スキーマの変更（NFR10）・構造の決まり（NFR11）はこの単位に当てはまらない（`traceability.json` で N/A とする） | 単位の分割 `unit-of-work.md` |

---

## Q1. S6 のアクセシビリティを、実際のブラウザでも確かめますか？

理由: 要件 NFR7 は「実際のブラウザでの検査を足すかは設計で決める」としています。部品のテスト（jsdom の vitest-axe）は色のコントラストを測れません。S6 は Alert（info）の中に「ホームへ戻る」のリンクを置く新しい組み合わせで、前の Intent では、ブランドカラーとテーマの組によって make-you-chic-ui の部品のコントラスト不足が実際のブラウザでだけ見つかりました（`project.md` の学び）。E2E には初期管理者しかいませんが、管理の入口の確かめの API（`GET /api/admin/check`）の答えだけを 403・`ACCESS_DENIED` の見本に差し替えれば、サーバーの状態を変えずに S6 を出せます（`060`・`100` と同じ差し替えの形）。

A. 実際のブラウザの検査は足さない。部品のテストの vitest-axe と、Alert の `role`・フォーカスの確かめだけで NFR7 を満たしたとする
B. 足す。新しい E2E のファイル（`130-admin-forbidden-accessibility.e2e.ts`。答えの後に、U5 の 110・120 と重ならない番号にそろえた）で、初期管理者でログインし、`GET /api/admin/check` の答えだけを 403・`ACCESS_DENIED` に差し替えて管理の入口を開き、S6 を出す。表示の設定の 20 組（既定の幅）と、幅 375px のテーマ2×文字の大きさ3の6組で、axe-core の違反 0 件（color-contrast を含む WCAG 2.0・2.1 の A・AA）と文書の横のはみ出しが無いことを確かめる。あわせて、Alert が `role="status"` であること、見出しにフォーカスがあること、「ホームへ戻る」でホームへ移れることを確かめる。管理の POST は送らない。既知の違反の一覧は空から始める。流れの E2E の本数に数えない（推奨。コントラストは実際のブラウザでしか測れず、既存の形をそのまま使えるため）
C. B のうち、表示の設定の 20 組（既定の幅）だけにし、狭い幅の6組は足さない（S6 は見出しと短い文言だけで、幅で崩れる部分が少ないため）
X. Other (please specify)

[Answer]: B

## Q2. 403 を受けてからの画面の時間の目標を置きますか？

理由: 要件 NFR5 は API の応答時間だけを決めています。U4 では、S6 への置き換えは同じ描画の中で終わり（要点 1）、待ちが入るのはその後のログインの状態の読み直し（トークンの更新の API、p95 1 秒）と、それによる管理のメニューの消え方だけです。前の Intent では、画面を開いてから表示までの時間の目標を置き、E2E で測って記録しました（統合の関門にはしない）。

A. 画面の数値の目標は置かない。S6 が同じ描画の中で出ることと読み直しが1回だけであることを部品のテストで確かめ、読み直しの時間はトークンの更新の API の既存の目標（p95 1 秒）で押さえる（推奨。S6 は待ちなしで出て、測る値がほぼ API の時間と同じになるため）
B. 目標を置く。管理の画面が 403 を受けてから S6 の見出しが見えるまで 0.5 秒以内、管理のメニューが消えるまで 1.5 秒以内とし、Q1 の E2E の中で5回ずつ測って記録する。統合の関門にはしない（ただし E2E では初期管理者の印は外れないため、メニューが消えるまでの時間は、ログインの応答の印を差し替えないと測れない）
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめと、U4 の NFR 要件の計画:

- 設計の要点（案）のとおりに作る。成果物は ui の4つ（performance・security・tech-stack・traceability）。
- Q1 B: 実際のブラウザでの S6 のアクセシビリティの検査を足す。
  - 新しい E2E は `frontend/e2e/130-admin-forbidden-accessibility.e2e.ts`。U5 の 110（代表の流れ）・120（検査）と重ならない番号にした。
  - 管理の入口の API（`GET /api/admin/check`）の答えだけを 403 `ACCESS_DENIED` に差し替えて、S6 を出す。
  - 既存の 20 組（`support/displayCombos.ts`。375px の6組を含む）で、axe の違反 0 件と横のはみ出しが無いことを確かめる。U5 と同じ組にそろえた。
  - あわせて、`role="status"`・見出しへのフォーカス・「ホームへ戻る」を確かめる。管理の POST は送らない。
  - 流れの E2E の本数には数えない。
- Q2 A: 画面の時間の数値の目標は置かない。S6 が同じ描画の中で出ることと、読み直しが1回だけであることは、部品のテストで確かめる。
- S6 の文言の ja・en（NFR8）と、画面で隠すことをサーバーの判定の代わりにしないこと（NFR1）は、機能設計で決まっている。
- 新しい依存は無い。バックエンドには手を入れない。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
