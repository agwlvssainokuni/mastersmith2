# NFR Requirements — Questions（U5 利用者の管理の画面 / u5-user-admin-ui）

U5 は、管理者が利用者の一覧で状態を確かめ、検索し、行ごとに管理者の印の付け外し・利用停止と解除・ロックの解除・氏名と言語の変更を行う画面の単位です（S1〜S5。S6 は U4 の共通の扱いを使う）。種類 ui のため、作る成果物は `performance-requirements.md`・`security-requirements.md`・`tech-stack-decisions.md`・`traceability.json` の4つです（拡張性・信頼性・観測性は service の単位だけ）。確かめた資料は、承認済みの機能設計 `aidlc/spaces/default/intents/260930-user-admin/construction/u5-user-admin-ui/functional-design/`（`functional-spec.md` の D1〜D20・W1〜W12・8節のテストの方針・9節の E2E・10節の上流との差・承認の場の決定、`frontend-components.md` の 2節・8節・9節）、要件 `inception/requirements-analysis/requirements.md` の NFR1〜NFR11 と 4節の制約・7節の未解決の点、契約 `inception/contract-design/contract-summary.md` の C3〜C5、Bolt の計画 `inception/delivery-planning/bolt-plan.md` の B5、画面の段の `inception/refined-mockups/`（`accessibility-checklist.md`・`interaction-spec.md`・`mockups.md` の D5（RQ7 C））、Practices Discovery の Q5 B（実際のブラウザの検査は Intent ごとに設計で決める）、前の Intent の画面の単位の NFR 要件 `aidlc/spaces/default/intents/260925-user-management/construction/u5-invitation-ui/nfr-requirements/`（画面の時間の目標と、Playwright＋axe-core の 20 組の検査の前例）、既存のコード `frontend/e2e/`（050〜100 の検査、`support/displayCombos.ts` の 20 組、`support/registeredUser.ts`・`support/invitationSeed.ts`）・`frontend/scripts/check-bundle-size.mjs`・`frontend/.npmrc`（今は `engine-strict=true` だけ）、決まり `aidlc/spaces/default/memory/team.md`・`project.md` です。上流で決まっていない2点（実際のブラウザでのアクセシビリティの検査を足すか・その範囲、画面の時間の目標）だけを質問にします。

## 設計の要点（案）

上流の決定から導ける、この単位の非機能要件の見通しです。質問の答えで決まる点は（Qn）と書きます。ID は成果物での枝番の見込みで、単位の中で .1 から振ります。依存の取り扱いのように当たる上流の NFR の ID が無い要件は NFR9 の枝番に寄せ、そのことを成果物の冒頭に書きます（`project.md` の学び）。

### 性能

1. **一覧の読み方（NFR5）**: 1ページ 20 件をサーバーの順のまま描き、画面で並べ替えない（D1）。読むのは画面を開いたとき・ページ送り・検索・検索を消す・操作の後・「もう一度読み込む」のときだけで、決まった間隔の読み直しはしない。重なった読み直しは最後の答えだけを使い、画面を離れた後の答えは捨てる（D3）。空のページの読み直しは1回の読み込みにつき1回まで（D10）。
2. **API の応答時間（NFR5）**: 一覧（検索を含む）と各操作の API の p95 1 秒は U3 の NFR 要件が持ち、Performance Validation の段の k6 で測る。U5 の成果物では U3 を指すだけにする。
3. **画面の時間の目標（NFR5）**: 画面を開いてから一覧が出るまで・「次へ」で次のページが出るまでの目標と測り方は（Q2）。
4. **待ちの見せ方（NFR5）**: 画面は要求に独自の時間切れを置かない（既存の ApiClient も持たない）。送信から 5 秒を過ぎたら確かめ・入力の表示の中に「時間がかかっています」を出し、要求は打ち切らない（D14）。二重の送信は、表示のボタン・部品の形（`Button` の `loading`・`aria-disabled`・ページ送りの押下を捨てる）・`useUserAdmin` の参照の3つで防ぐ（D13）。
5. **初回の JavaScript の大きさ（NFR5）**: 既存の目安（gzip で 500KB、超えたら警告で統合は止めない、`frontend/scripts/check-bundle-size.mjs`）をそのまま当てる。画面は遅延読み込み（`lazy`）で、新しい実行時の依存は無いため、初回の大きさへの影響は小さい見込み。make-you-chic-ui の固定先を上げた前後の値をコード生成で測って記録する。

### セキュリティ

6. **画面で隠すことはサーバー側の判定の代わりにしない（NFR1）**: 自分の行の「管理者の印を外す」「利用を止める」を押せない形にしても、サーバー側の拒否（U3）は残す（D6）。管理の API の 401・403・200 と停止中の管理者の拒否は U3・U1 のサーバー側のテストで確かめ、U5 では画面の側の扱いだけを書く。
7. **個人に関する値（NFR3）**: 応答の値（メールアドレス・氏名）と検索の文字を、console・localStorage・sessionStorage・URL・履歴に出さない。今のページと検索の文字は画面の中だけに持つ（D2・D20）。部品のテストで、操作の後に保存の場所に値が入らないこと、URL が変わらないこと、console に値が出ないことを確かめる（招待の画面の前例）。E2E の題・注記・添付に宛先・パスワード・トークンを入れない（9節）。
8. **失敗の文言（NFR3）**: 応答の `code` と状態コードから画面が文言を選び、サーバーの `detail`・`title` を出さない。知らない code・code の無い応答・通信の失敗は一般の文言にする（D8）。応答の値を HTML として差し込まない（既存のリンタの決まり `react/no-danger` などのまま）。
9. **403・401（NFR1）**: 403 は U4 の `useAdminForbidden` に渡し、画面は何も出さない（D11）。401 は既存の ApiClient とログインの状態に任せ、S3・S4 を閉じる（D12）。
10. **CSP と外部の資源**: 外部への通信・外部の資源・埋め込みのスクリプトとスタイルを足さず、CSP は変えない（前の Intent の U4 の NFR9.4）。

### アクセシビリティ（NFR7）

11. **目標と部品ごとの検査**: 目標は WCAG 2.1 AA。画面部品ごとに vitest-axe の検査を1件以上入れ、違反 0 件とする（2語の氏名・長いメールアドレス・ロック中・利用停止・「あなた」・誤りの状態・5種類の確かめ、`functional-spec.md` 8節）。状態は色に加えて `Badge` の文字で示す（D19）。
12. **実際のブラウザの検査**: Playwright＋axe-core の検査を足すか、足すならその組と状態は（Q1）。足しても流れの E2E の本数には数えない（`project.md` の学び）。
13. **狭い幅**: 狭い画面向けの形は作らず、表の外側を横にずらせるだけにする（RQ7 C）。Q1 で 375px の組を入れるときは、文書が横にはみ出さないこと（表は包む要素の中だけで動く）を確かめる形になる。

### 多言語（NFR8）

14. **文言**: 画面の文言（`useradmin.*`）は ja・en の両方を持ち、`Table` の `labels` と `Modal` の閉じるの名前も画面の言語で渡す。鍵の対がそろうことと、en の画面で make-you-chic-ui の日本語の既定の文言が出ないことをテストで確かめる。自分の言語を en に直した直後の Toast が英語であること（D17）もテストで確かめる。日時は既存の `formatDateTime` で画面の言語とブラウザの時間帯で出す（D18）。

### 依存とテスト（NFR9）

15. **新しい依存は足さない**: 既存の React・make-you-chic-ui（`Dropdown`・`Table`・`Modal`・`Button`・`Badge`・`Alert`・`Toast`）・Vitest・Testing Library・user-event・vitest-axe・fast-check・Playwright・axe-core で作る。
16. **make-you-chic-ui の固定先**: `077f5b4` から `3481488` 以降へ上げる。中身は変えず、承認を得た専用のコミットで更新前後のハッシュを記録する（`project.md` の Forbidden・Mandated）。上げた直後に `frontend-components.md` 2.5 の表で実際の形を確かめ、既存の画面のテストと E2E が通ることを確かめる。`vendor/make-you-chic-ui` の lockfile は今までどおり OSV-Scanner の対象。
17. **`frontend/.npmrc` の `ignore-scripts=true`**: 足したうえで、`npm ci` と `vendor/make-you-chic-ui` のインストールとビルド、`./gradlew verify` が通ることを確かめる（`team.md` の Code Style）。
18. **テストとカバレッジ**: フロントエンドのカバレッジの下限（行 80%・分岐 70%）を守る。性質ベースのテスト（fast-check、失敗時の種を記録）を `rowActions.ts`・`searchInput.ts` に当てる。描画の後の値は `waitFor` で待つ。テストの説明文は英語、メールアドレスは `example.com` だけ。
19. **E2E**: 代表の流れを1本（`frontend/e2e/110-user-admin-flow.e2e.ts`、検索 → ロック → 失敗回数を戻す → 止める → 入れない → 停止を解く → 入れる）。状態を変えるのは流れの中で作った利用者だけで、初期管理者は変えない。`./gradlew verify` と CI の外に置き、統合の前に手元で流す（機能設計 9節、`team.md`）。

### traceability の見込み

20. U5 に当たるのは NFR1（画面の側の扱いだけ）・NFR3・NFR5（Q2）・NFR7（Q1）・NFR8・NFR9。NFR2（停止の拒否の応答は U1）・NFR4（同時の重なりの守りはサーバー側の U3。画面の二重の送信の守りは NFR5 に書く）・NFR6（接続プール）・NFR10（スキーマ）・NFR11（バックエンドの境界）は `N/A` とし、理由を書く。

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| 一覧は 1ページ 20 件、サーバーの順。今のページと検索の文字は画面の中だけに持ち、URL・保存・履歴に出さない | 機能設計 D1・D2（Q1 A） |
| 自動の読み直しはしない。最後の読み直しの答えだけを使う。空のページの読み直しは1回まで | 機能設計 D3・D10（R-04・R-06） |
| 画面の側の時間切れは置かない。5 秒を過ぎたら「時間がかかっています」を出し、打ち切らない | 機能設計 D14、`interaction-spec.md` 3節 |
| 二重の送信は3つの守りで防ぐ | 機能設計 D13（R-01） |
| 一覧と操作の API の p95 1 秒は U3 が持ち、Performance Validation の段の k6 で測る | 要件 NFR5、`aidlc-state.md`（performance-validation は EXECUTE） |
| 失敗の文言は `code` から選び、`detail`・`title` を出さない | 機能設計 D8 |
| 応答の値と検索の文字を console・保存・URL に出さない | 機能設計 D20、要件 NFR3 |
| 403 は U4 の共通の扱い、401 は ApiClient に任せる | 機能設計 D11・D12、契約 C4 |
| 画面で隠すことはサーバー側の判定の代わりにしない | 要件 NFR1、`project.md` の Mandated |
| 目標は WCAG 2.1 AA。画面部品ごとに vitest-axe を1件以上。テーマ（light・dark）と文字の大きさ（sm・md・lg）のどの組でも崩れない | 要件 NFR7、`team.md` の Testing Posture |
| 狭い画面向けの形は作らず、表を横にずらして読めるだけにする | 画面イメージ RQ7 C・D5 |
| 文言は ja・en の両方 | 要件 NFR8、機能設計 W11 |
| 初回の JavaScript は gzip で 500KB を目安（警告だけ） | `frontend/scripts/check-bundle-size.mjs`、前の Intent の NFR |
| CSP を変えず、外部の資源を足さない | 前の Intent の U4 の NFR9.4 |
| 新しい npm の依存は足さない。make-you-chic-ui の固定先を `3481488` 以降へ上げ、中身は変えない（専用のコミットで前後のハッシュを記録） | 機能設計 2節・`frontend-components.md` 2.4、Bolt B5、`project.md` の Forbidden・Mandated |
| `frontend/.npmrc` に `ignore-scripts=true` を足す | 要件 4節の制約、`team.md` の Code Style、Bolt B5 |
| E2E は代表の流れを1本（110）。初期管理者は変えず、`verify` と CI の外に置く | 機能設計 9節、ストーリー M9 A、`team.md` |
| 実際のブラウザの検査と画面の時間の測りは、流れの E2E の本数に数えない | `project.md` の学び |
| フロントエンドのカバレッジの下限は行 80%・分岐 70%。性質ベースのテストは fast-check | `team.md` の Testing Posture、機能設計 8節 |

---

## Q1. 実際のブラウザでのアクセシビリティの検査（Playwright＋axe-core）を、この画面に足しますか？足すなら、どの組と状態で行いますか？

理由: Practices Discovery の Q5 B で、この検査は決まりにせず Intent ごとに設計で決めることになり、機能設計（8節）がこの段に回しました。jsdom の vitest-axe は色のコントラストを確かめられません。前の Intent では、誤りの状態と2語の氏名を実際のブラウザで見て初めて、make-you-chic-ui の Avatar と dark の FormField の文字のコントラスト不足が見つかりました（`project.md` の学び）。この画面は、自分の行のメニューの押せない項目と理由の文（`frontend-components.md` 2.5 で light・dark とも 4.5:1 以上を期待）、ロック中・利用停止・「あなた」の `Badge`、業務の失敗の `Alert` など、コントラストを実際のブラウザでしか確かめられない表示が多くあります。既存の検査（050〜100）は 20 組（(a) テーマ2×文字の大きさ3、(b) ブランドカラー4×テーマ2、(c) (a) の6組を 375px）で、一覧の API の答えを見本に差し替えて行っています。

A. 足す（推奨。コントラストは実際のブラウザでしか確かめられず、押せない項目と理由の文はこの Intent で新しく使う形のため）。`frontend/e2e/120-user-admin-accessibility.e2e.ts` を足し、既存の 20 組（`support/displayCombos.ts`）で、一覧の API と操作の API の答えだけを見本に差し替えて、次の状態を確かめる: 一覧（行あり。2語の氏名・長いメールアドレス・ロック中・利用停止・管理者・「あなた」の行を含む）、自分の行のメニューを開いた状態（押せない項目と理由の文）、確かめの表示（「利用を止める」の1種類を代表にする）、氏名・言語の入力の誤りの状態（画面の確かめの誤り）、業務の失敗の知らせ（409 の見本）、検索の上限の誤り。axe の違反 0 件（既知の違反の一覧は空から始める）と、文書が横にはみ出さないこと（(c) の 375px の組を含む）を確かめる。操作の要求は送らない（差し替えた答えだけ）
B. 足すが、組を減らす。(a) テーマ2×文字の大きさ3 と (b) ブランドカラー4×テーマ2 の 14 組だけにし、375px の (c) は入れない（狭い画面向けの形を作らない RQ7 C のため）。状態は A と同じ
C. 足さない。vitest-axe の部品の検査だけで NFR7 を満たしたとする（色のコントラストは確かめない。押せない項目の文字のコントラストは make-you-chic-ui の側の確かめに任せる）
X. Other (please specify)

[Answer]: A

## Q2. 利用者の管理の画面の時間の目標と、測り方をどうしますか？

理由: 要件 NFR5 は API の応答時間だけを決めており（U3 が持つ）、画面の時間の目標はありません。前の Intent の招待の画面では、開いてから一覧（20 行）が出るまで 2 秒以内、「次へ」で次のページが出るまで 1.5 秒以内を目標にし、実際のブラウザで5回ずつ測って記録し、統合の関門にはしない扱いにしました（jsdom の時間は不安定なため）。ただし、利用者の一覧で「次へ」を本物の API で測るには利用者が 21 人以上要り、利用者は招待と登録の完了（Mailpit を通る）でしか作れません。招待は API で安く作れましたが、利用者は1人ずつ時間がかかります。E2E の内部DB は実行ごとの一時のものです。

A. 画面の数値の目標は置かない。一覧と操作の時間は U3 の API の目標（p95 1 秒）で押さえ、画面の側は描き方（20 件ごと・自動の読み直しなし・古い答えを捨てる）を部品のテストで確かめる
B. 目標を置く（推奨。招待の画面とそろえ、利用者を大量に作らずに済むため）。開いてから一覧の1ページ目が出るまで 2 秒以内（本物の API、何も差し替えない。その時点の利用者の数のまま）、「次へ」で次のページが出るまで 1.5 秒以内（一覧の API の答えを 2 ページ分の見本に差し替え、画面の側の時間だけを測る。API の時間は U3 の k6 が持つ）。実際のブラウザで5回ずつ測って記録し、統合の関門にはしない。目標を超えたときは目標を緩めず、API と描画のどちらが遅いかを確かめて依頼者に相談する。測る場は Q1 の検査のファイルの中の測りのテスト1件とする（Q1 が C のときは 110 の E2E の中で測る）
C. B と同じ目標で、「次へ」も本物の API で測る。測りのテストの中で `createRegisteredUser` を使って利用者を 21 人作ってから測る（招待と登録の完了を 21 回行うため、E2E の時間が延び、Mailpit に 21 通が残る）
X. Other (please specify)

[Answer]: B

---

## Consolidated Summary Confirmation

答えのまとめと、U5 の NFR 要件の計画:

- 設計の要点（案）のとおりに作る。成果物は ui の4つ（performance・security・tech-stack・traceability）。
- Q1 A: `frontend/e2e/120-user-admin-accessibility.e2e.ts` を足し、既存の 20 組（`support/displayCombos.ts`、375px の6組を含む）で、実際のブラウザのアクセシビリティを確かめる。
  - 確かめる状態は6つ: 一覧、自分の行のメニュー（押せない項目と理由の文）、確かめの表示、氏名・言語の入力の誤り、409 の知らせ、検索の上限の誤り。
  - API の答えは見本に差し替え、操作の要求は送らない。
  - axe の違反 0 件と横のはみ出し無しを確かめる。
  - 流れの E2E の本数には数えない。U4 の `130-admin-forbidden-accessibility.e2e.ts` と同じ組にそろえる。
- Q2 B: 画面の時間の目標は招待の画面と同じにする。開いてから一覧が出るまで 2 秒、「次へ」で次のページが出るまで 1.5 秒。
  - Q1 のファイルの中の測りのテスト1件で、5 回ずつ測って記録する。統合の関門にはしない。
  - 一覧と操作の API の p95 1 秒は U3 が受け持つ。
- make-you-chic-ui の固定先を `3481488` 以降へ上げる。`frontend/.npmrc` に `ignore-scripts=true` を足す。新しい npm の依存は足さない。代表の流れの E2E は 110 の1本。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
