# NFR Requirements — Questions（U4 表示の設定の土台 / u4-display-foundation）

U4 は、すべての画面に表示の設定（言語・テーマの選択・文字の大きさ・インスタンスの見た目）を当てる土台、ApiClient の広げ（要求の言語・トークンを付けない公開の API のパス）、ログインの画面の言語の切り替えと登録の完了からの受け渡しを受け持つ画面の単位です（種類 ui のため、作る成果物は性能・セキュリティ・技術の選択・traceability の4つです。拡張性・信頼性・観測性は service の単位だけ）。非機能の論点のほとんどは要件・契約・承認済みの機能設計と既存のコードで決まっているため、まず NFR の要点（案）を示し、上流から決まらない2点（最初の描画までの時間の目標の置き方と、テーマ・文字の大きさのすべての組み合わせで崩れないこと（NFR7）の確かめ方）だけを質問にします。

読んだ上流:

- 承認済みの機能設計 `aidlc/spaces/default/intents/260925-user-management/construction/u4-display-foundation/functional-design/`（`functional-spec.md` の D1〜D14・W1〜W12・7節の失敗の場合・9節の Noto Serif JP と Noto Sans JP の採用の記録、`frontend-components.md` の 6節・7節・8節、`traceability.json`、`functional-design-questions.md`）
- 依存する単位の承認済みの NFR 要件 `construction/u8-instance-appearance/nfr-requirements/`（NFR6.1: `GET /api/appearance` は同時 10 件で p95 300 ミリ秒）・`construction/u2-user-preferences/nfr-requirements/`（NFR6.5: ログイン・トークンの更新は同時 10 件で p95 1 秒のまま）
- 要件 `aidlc/spaces/default/intents/260925-user-management/inception/requirements-analysis/requirements.md`（NFR1〜NFR11、特に NFR6・NFR7・NFR8・NFR9）
- 契約 `inception/contract-design/contract-summary.md`（C3・C7・C9）、画面 `inception/refined-mockups/interaction-spec.md`（WCAG AA のコントラスト・テーマと文字の大きさのどの組み合わせでも）、ストーリー `inception/user-stories/stories.md` の「後の段に回す点」（NFR7 の確かめ方の持ち主はこの段）
- 前の Intent の画面の単位の NFR 要件 `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u1-app-skeleton/nfr-requirements/performance-requirements.md`（NFR1.5: 初回の JavaScript は gzip で 500KB を目安）・`aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/u5-dsl-admin-ui/nfr-requirements/`（画面の時間の目標を E2E で測って記録し、統合の関門にしない前例）
- 既存のコード `frontend/package.json`・`frontend/vite.config.ts`・`frontend/src/main.tsx`・`frontend/scripts/check-bundle-size.mjs`・`frontend/playwright.config.ts`・`frontend/e2e/`、`backend/src/main/resources/application.yaml`（CSP）、`backend/src/main/java/cherry/mastersmith/common/web/CacheControlFilter.java`、`vendor/make-you-chic-ui`（固定先 edb1f94、`origin/main` 735ef04。変更しない）
- 決まり `aidlc/spaces/default/memory/team.md`（Testing Posture の画面のテスト・アクセシビリティ・E2E の本数、Deployment のフロントエンドの依存の脆弱性、Code Style の新しい依存のライセンス）・`project.md`

## NFR の要点（案）

1. **性能（並べ読みの作り）**: 画面を開いたときの待ちは、見た目の設定の読み取り（`GET /api/appearance`、U8 の NFR6.1 で p95 300 ミリ秒）とセッションの復元（トークンの更新、前の Intent の目標で p95 1 秒、U2 の NFR6.5）を同時に始め、両方の答えが出たら描く（W2・D11）。最初の描画の待ちは、2つの API の遅いほう（多くはセッションの復元）にほぼ等しく、見た目の設定を足しても待ちは足し算にならない。2つの要求が、どちらかの答えを待たずに始まることを部品のテストで確かめる。画面の数値の目標をどう置くかは Q1 で決める。
2. **性能（初回の JavaScript の大きさ）**: 既存の目安（初回の読み込みの JavaScript は gzip で 500KB 以内。超えたら警告で、統合は止めない。`frontend/scripts/check-bundle-size.mjs`）をそのまま当てる。U4 が足すのは小さな関数と部品だけで、フォントは CSS とフォントのファイルのため、この値は増えない見込み（機能設計の 9.1）。今の値（入口の JavaScript は圧縮前 約 370KB）からの増えは、コード生成で測って記録する。
3. **性能（フォントの読み込み）**: Noto Serif JP の CSS は、Noto Sans JP と同じく太さごとに `@font-face` の宣言だけで、フォントのファイルは明朝体の文字を描くときにだけ読まれる（`sans` のインスタンスでは読まれない）。今の Noto Sans JP は japanese のサブセットが太さごとに1ファイル（woff2 で 約 1.0MB）で、Noto Serif JP も同じ形の見込み。宣言は `font-display: swap` のため、フォントのファイルを読む間も代わりのフォントで文字が出て、描画は止まらない（読み終わると明朝体に切り替わる）。フォントのファイルは名前にハッシュが付く `/assets/` の下に置かれ、既存の `CacheControlFilter` により `public, max-age=31536000, immutable` で保存されるため、2回目以降は読み直さない。
4. **性能（配信物の大きさ）**: Noto Serif JP の4つの太さ（japanese・latin、woff2 と woff）を足すため、`dist` と WAR が大きくなる（今の Noto Sans JP の分は 約 9.8MB、WAR は 約 87MB）。大きさの上限は置かず、増えた量をコード生成で測って記録する（Q4 A で受け入れた結果。機能設計の 9.2 の Consequences）。
5. **セキュリティ（ブラウザの保存）**: localStorage に置くのは U4 の鍵 `mastersmith.display-settings` の3つの値（`language`・`theme`・`fontSize`）と、make-you-chic-ui の鍵（`design-system-*`）の見た目の値だけで、トークン・メールアドレス・氏名・見せ方の値を置かない（機能設計 4.1）。トークンと利用者は、既存の `frontend/src/features/auth/authSession.ts` のとおりメモリだけに持つ。読み込む値は項目ごとに許される値だけを受け入れ、それ以外は無いものとする（D7）。コード生成のテストで、保存の後の U4 の鍵の中身が3つの値だけで、localStorage のどの鍵にもトークン・メールアドレス・氏名が入らないことを確かめる。
6. **セキュリティ（登録の完了からの受け渡し）**: 登録の完了からログインの画面へ渡すのはメールアドレスだけで、画面の中のメモリだけで1回だけ渡す。URL・ブラウザの保存に載せず、パスワード・招待のトークンは渡さない（D13、W9）。テストで、URL と localStorage にメールアドレスが載らないことを確かめる（`frontend-components.md` の 7節の `LoginForm`）。
7. **セキュリティ（トークンを付けない公開の API）**: `/api/appearance`・`/api/registration/verify`・`/api/registration/complete` にはアクセストークンを付けず、401 を受けても更新と送り直しをしない。パスの判定は完全な一致（問い合わせの部分を除く）で行い、似た別のパスを公開として扱わない（D10、`frontend-components.md` の 7節の ApiClient）。サーバー側の認可（公開の範囲、未認証・管理者でない・管理者の 401・403・200）は U3・U8 のサーバー側のテストで確かめ、画面の側の扱いはその代わりにしない（NFR4、`team.md`）。
8. **セキュリティ（要求の言語と見た目の値）**: `Accept-Language` に付けるのは許される値（`ja`・`en`）だけのため、利用者の入力がヘッダーに入ることはない（D9）。見た目の設定の応答は、項目ごとに契約 C7 の許される値だけを make-you-chic-ui に渡し、それ以外は当てない（W3）。応答の値を HTML や属性にそのまま差し込まない。
9. **セキュリティ（CSP とフォントの配信）**: 外部のフォント・外部の通信は足さず、CSP（`font-src 'self'`・`script-src 'self'`・`style-src 'self'`・`connect-src 'self'`）は変えない（機能設計 9.1）。フォントは自前で配信する。埋め込みのスクリプト・スタイルを足さない（既存の `vite.config.ts` の設定のまま）。
10. **セキュリティ（新しい依存と make-you-chic-ui）**: 新しい実行時の依存は `@fontsource/noto-serif-jp`（OFL-1.1）だけ。採用の理由は機能設計の 9.2 の ADR 形式の記録にあり、既存の `@fontsource/noto-sans-jp`（OFL-1.1、版 5.3.0）の記録も 9.3 に足してある（`team.md` のライセンスの決まり）。版は lockfile で固定し、推移依存の有無と版をコード生成で確かめて記録する。実行時の依存のため、依存関係の脆弱性検査で High 以上なら統合を止める対象になる（`team.md` の Deployment）。make-you-chic-ui の固定先の更新（edb1f94 → 735ef04）は B4 で、承認を得た専用のコミットで行い、更新の前後のコミットのハッシュを記録する（`project.md` の Mandated）。更新した版の lockfile も依存関係の脆弱性検査の対象に入る（`team.md` の Code Style）。
11. **アクセシビリティ（目標）**: 目標は WCAG 2.1 AA（文字のコントラスト 4.5:1、部品 3:1、キーボードで操作できる、読み上げで名前が分かる、色だけに頼らない。refined-mockups の決定、`interaction-spec.md`）。U4 の画面部品（`LoginLanguageSwitch`・登録の完了の案内を出す `LoginForm`・氏名を出す `ShellLayout`）ごとに vitest-axe の検査を1件入れ、違反 0 件とする（`team.md`）。言語の選択肢には `lang` 属性を付け、切り替えた後もフォーカスを残す（D12、W7）。画面の言語が変わったら `<html lang>` も変える（D14）。動きは `prefers-reduced-motion` のときに止める（make-you-chic-ui の扱い）。テーマ・文字の大きさのすべての組み合わせで崩れないこと（NFR7）とコントラストの確かめ方は Q2 で決める。U4 で決めた確かめ方を、後の U5〜U7 の画面にも同じく当てる。
12. **多言語**: U4 が足す文言（`auth.login.registered`・`auth.language.label`・`display.theme.*`・`display.fontSize.*`）は ja・en の両方を用意する（NFR8、機能設計 8節）。言語の名前（「日本語」「English」）は訳さない。文言の鍵が ja と en でそろっていることをテストで確かめる。
13. **テストとカバレッジ**: フロントエンドのカバレッジの下限（行 80%・分岐 70%）を守る（NFR9、`team.md`）。性質ベースのテスト（fast-check、失敗時の種を記録）を、保存の値の読み取りと検証・テーマの解き方・言語の解き方・画面の値を決める関数に当てる（機能設計 10節）。既存の画面のテストと、既存の E2E（`frontend/e2e/` の 010〜040。ログインの画面・認証・管理画面）が通り続けることを確かめる。この Intent の代表の流れの E2E（招待から登録の完了まで）は U5・U6 の受け持ちで、U4 では足さない。
14. **traceability の見込み**: U4 に当たる要件の非機能は NFR2（登録の完了からの受け渡しで、メールアドレスを URL・ブラウザの保存に載せない）・NFR6（最初の描画の待ち。Q1）・NFR7（アクセシビリティ。Q2）・NFR8（文言）・NFR9（テスト・カバレッジ・既存の E2E）。NFR1（招待のトークンは受け渡さず、U4 は扱わない）・NFR3・NFR4（サーバー側の認可は U3・U8。画面の側の D10 は要点 7 に書き、NFR4 は U4 では N/A とし理由を書く）・NFR5・NFR10・NFR11 は `N/A` とし、理由を書く。依存の決まり（要点 10）は tech-stack-decisions に書き、NFR9 の枝番で追う。

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| 見た目の設定とセッションの復元を並べて読み、両方の答えが出るまで描かない。待ちに上限を置かない（応答が返らないと全画面が描けない影響も受け入れ済み） | 機能設計 W2・D11・7節、U4 の Q2 A |
| `GET /api/appearance` は同時 10 件で p95 300 ミリ秒、ログイン・トークンの更新は同時 10 件で p95 1 秒 | U8 の NFR6.1、U2 の NFR6.5 |
| 初回の読み込みの JavaScript は gzip で 500KB を目安（超えたら警告、統合は止めない） | 前の Intent の U1 の NFR1.5、`frontend/scripts/check-bundle-size.mjs` |
| ブラウザに保存するのは3つの表示の設定だけで、トークン・メールアドレス・氏名を置かない。読み込む値は項目ごとに検証する | 機能設計 4.1・D6・D7 |
| 受け渡しはメールアドレスだけ、メモリだけで1回だけ。URL とブラウザの保存に載せない | 機能設計 D13・W9 |
| 公開の API の3つのパスにはトークンを付けず、401 での更新と送り直しもしない | 機能設計 D10、契約 C6・C7 |
| Noto Serif JP を 400〜700 の japanese・latin で自前で配信し、CSP を変えない。OFL-1.1 の採用の理由は記録済み（Noto Sans JP も） | 機能設計 9節（Q4 A と承認の場の Request Changes） |
| フロントエンドの実行時の依存は High 以上で統合を止める。版は lockfile で固定する | `team.md` の Deployment、`project.md` の Mandated |
| make-you-chic-ui の中身は変えない。固定先の更新は B4 の専用のコミットで、前後のハッシュを記録する | `project.md` の Forbidden・Mandated、Bolt の計画 |
| 目標は WCAG 2.1 AA。画面部品ごとに vitest-axe の検査を1件入れる | refined-mockups の `interaction-spec.md`、`team.md` の Testing Posture |
| 文言は ja・en の両方 | 要件 NFR8、機能設計 8節 |
| フロントエンドのカバレッジの下限は行 80%・分岐 70% | `team.md` の Testing Posture |
| E2E は `./gradlew e2eTest` で `verify` と CI の外に置き、機能の Intent ごとに代表の流れを1本まで足す。この Intent の1本は招待から登録の完了まで | `team.md` の Testing Posture、要件 NFR9 |
| 負荷の試験（API の時間）は Performance Validation の段で k6 を使って測る | U2・U8 の NFR 要件、`aidlc-state.md` の段の一覧 |

## Q1. 画面を開いてから最初の画面が出るまでの時間の目標を、どうしますか？

理由: 要件 NFR6 は API の応答時間だけを決めており、画面を開いてから最初の画面が出るまでの目標はありません。U4 の W2 で、最初の描画は見た目の設定とセッションの復元の両方の答えを待つため、待ちは2つの API の遅いほう（セッションの復元の p95 1 秒）にほぼ等しくなります。API の時間は U2・U8 の目標と Performance Validation の k6 ですでに押さえられています。一方で、画面の側の待ち（JavaScript の読み込み・描画）を含めた利用者の体感の時間は、どの段でも測りません。前の Intent の画面の単位（DSL の管理画面）では、画面の時間の目標を置き、Build and Test の E2E（実際のブラウザ）で測って記録し、統合の関門にはしない扱いにしました（jsdom の時間は不安定で、`team.md` の「不安定なテストは統合しない」に当たるため）。

- A. 画面の数値の目標は置かない。2つの要求が同時に始まる作り（要点 1）を部品のテストで確かめ、時間は API の目標（更新 p95 1 秒・見た目の設定 p95 300 ミリ秒）で押さえる
- B. 目標を置く。手元の PC のブラウザで、ブラウザのキャッシュが空の状態で画面を開いてから最初の画面（ログインの画面）が出るまで 2 秒以内。Build and Test で既存の E2E の中で測って記録し、統合の関門にはしない（前の Intent の DSL の管理画面と同じ扱い）。作りは A と同じく部品のテストで確かめる（推奨）
- C. B の目標を、ログインしたままの状態での読み込み直し（セッションの復元を含み、ログインの後の最初の画面が出るまで）にも当てる。E2E の中で両方を測って記録する
- X. Other (please specify)

[Answer]: B

---

## Q2. テーマ・文字の大きさのすべての組み合わせで崩れないこと（NFR7）とコントラスト（WCAG 2.1 AA）を、どう確かめますか？

理由: 要件 NFR7 は「テーマ（light・dark）と文字の大きさ（sm・md・lg）のどの組み合わせでも表示が崩れない」とし、`interaction-spec.md` はコントラストを「WCAG AA（テーマ・文字の大きさのどの組み合わせでも）」としています。ストーリーの段で、その確かめ方の持ち主はこの段とされました（jsdom では色のコントラストや配置を評価できない見込み）。今の vitest-axe は jsdom で動くため、名前・役割・`lang` などの構造は確かめられますが、コントラストと崩れ（はみ出し・重なり）は確かめられません。ブランドカラー（4つ）もテーマと組み合わさってコントラストに関わります。make-you-chic-ui にはすべての組み合わせで AA を満たすという明示の約束は見当たりません（ダークのツールチップのコントラストを後から直した記録はある）。ここで決めた確かめ方は、後の U5〜U7 の画面にも同じく当てます。なお axe-core（MPL-2.0）は、今も vitest-axe の推移依存として `frontend/node_modules` に入っています（版 4.13.0）。

- A. 部品ごとの vitest-axe（構造）に加え、Build and Test で、実際のブラウザで新しい画面ごとにテーマ2×文字の大きさ3の6組（ブランドカラーは既定の blue に加えて4色を light・dark で代表の1画面だけ）を開き、点検表で崩れとコントラストを目で確かめて記録する。新しい依存は足さない
- B. A の目の確かめの代わりに、Playwright の検査を1つ足す（`./gradlew e2eTest` の中、`verify` と CI の外）。新しい画面ごとに、テーマ2×文字の大きさ3の6組と、ブランドカラー4×テーマ2の8組（`md`）を切り替え、実際のブラウザで axe-core を流してコントラストを含む違反 0 件を確かめ、崩れは画面の幅を超えるはみ出しが無いことで確かめる。axe-core を devDependencies に明示で足し（MPL-2.0、今の推移依存と同じ版）、採用の理由を tech-stack-decisions に ADR 形式で残す。この検査は流れではないため、「代表の流れを1本まで」の本数に数えない。U4 の B4 ではログインの画面から始め、U5〜U7 の画面は B5 で足す（推奨）
- C. 部品ごとの vitest-axe（構造）だけにする。コントラストと崩れは make-you-chic-ui の色と部品を使うことで守られるとみなし、この Intent では確かめない（NFR7 の確かめは vitest-axe の違反 0 件で満たしたとする）
- X. Other (please specify)

[Answer]: B

---

## Consolidated Summary Confirmation

答えのまとめ（Q1・Q2 の回答の後に埋めます）:

- NFR の要点（案）は冒頭の「NFR の要点（案）」の 14 件のとおり
- Q1 B: 手元の PC のブラウザで、キャッシュが空の状態で画面を開いてからログインの画面が出るまで 2 秒以内。Build and Test で既存の E2E の中で測って記録し、統合の関門にはしない。作りは部品のテストで確かめる
- Q2 B: Playwright の検査を1つ足す（`./gradlew e2eTest` の中、`verify` と CI の外）。新しい画面ごとに、テーマ2×文字の大きさ3の6組と、ブランドカラー4×テーマ2の8組（`md`）で、実際のブラウザの axe-core のコントラストを含む違反 0 件と、画面の幅を超えるはみ出しが無いことを確かめる。axe-core（MPL-2.0）を devDependencies に明示で足し、採用の理由を tech-stack-decisions に ADR 形式で残す。この検査は流れではないため、`team.md` の「代表の流れを1本まで」の本数に数えない（依頼者が受け入れた読み方）。U4 の B4 ではログインの画面から始め、U5〜U7 の画面は B5 で足す

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
