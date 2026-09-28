# Build and Test — Questions

前提（2026-09-28 時点）:

- develop の先頭は 66fe981 で、U1〜U8 のコードはすべて統合済みです（B5 の U7 は d1c1505）。
- 測れる品質の目標は 249 件あります（U1 30・U2 38・U3 49・U4 29・U5 24・U6 29・U7 25・U8 25）。
  - このうち 29 件は、この段では確かめられません。持ち主は後の段です。
    - performance-validation：同時 10 件の p95、接続プール、規模
    - observability-setup：指標・警報・SLO
    - deployment-pipeline・deployment-execution：戻しの練習・バックアップ
  - この Intent の流れには、これらの段がすべてあります。この段では、確かめられない目標を `Unverified` とし、持ち主を明記します。
- 次のことは決まっているため、質問にしません。この段で行います。
  - `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で、テストの件数・カバレッジ・時間を実測する（project.md の決まり）。colima の VM（CPU 4・メモリ 6GiB）は動いていて、配備したアプリ・Mailpit・PostgreSQL の対象DB も動いています。
  - Mailpit を起動した状態で `./gradlew e2eTest` を流し、画面の時間・実際のブラウザの検査（axe）・CSP の違反・E2E-1 の結果を `frontend/test-results/e2e-results.json` から写す。json に秘密や個人に関する値が入っていないことも確かめる。
  - 各単位の計画で「Build and Test に引き継ぐ」とされた k6 の場面を `perf/k6/scenarios.js` と `perf/README.md` に足し、`k6 inspect --include-system-env-vars` で読み込めることを確かめる。足す場面は、U2 のプリファレンスとパスワードの変更、U3 の招待・一覧・送り直し・取り消し・リンクの確かめ・登録の完了の8つです。k6 は PC に無いため、既にある `grafana/k6:2.3.0` のイメージで流します。負荷をかけての測定は performance-validation で行います。
- 以下の Q3〜Q7 は、コード生成の承認の場で確かめるはずだった事項です（依頼者の決定で、この質問にまとめました）。

## Q1. U7 のときに1回だけ落ちたテスト（`DisplaySettingsProvider.test.tsx` の1件）の扱い

U7 のコード生成で、変更の前の verify のときに、既存のこのテストの1件が1回だけ落ちました。原因は確かめていません。team.md には「不安定なテストは原因を直すまで統合しない」とあります。一方で、再現できなければ不安定と確かめられていない扱いとした前例があります（project.md、260925-storage-memory-fixes の F3）。U7 は、この点を確かめないまま develop に統合されています。

A. この段で、そのテストのファイルを繰り返し流して（例: 50 回）再現を試す。再現しなければ「不安定と確かめられていない」として記録し、再現したら原因を直す（直すのはテストの側の足場だけで、アプリのコードの原因ならコード生成に戻す）
B. A に加えて、フロントエンドのテスト全体（`npm run test:coverage`）も繰り返し流す（例: 10 回）
C. 繰り返しは流さず、この段の verify で落ちなければよいとする
X. Other (please specify)

[Answer]: A

## Q2. 負荷の試験で、登録の完了に使う招待のトークンの用意の仕方（U3 の計画でこの段に回された決定）

登録の完了の場面（同時 10 件）には、使われていない招待のトークンが VU ごとに要ります。トークンは内部DB にハッシュ値だけで保存されるため、試験の側で平文のトークンを持つ必要があります。

A. 試験の前に招待の API で招待を出し、Mailpit の API からメールを読んで、リンクのトークンを取り出す（本物の流れに近い。Mailpit の件数が増え、用意に時間がかかる）
B. 既知のトークンを試験の側で作り、そのハッシュ値を持つ招待の行を、使い捨ての環境の内部DB に SQL で直接入れる（速い。本物の招待の流れは通らない）
C. 登録の完了の場面は k6 に入れず、performance-validation で改めて決める
X. Other (please specify)

[Answer]: A

## Q3. CI と Dependabot の確かめ（U1 の引き継ぎ）

U1 の引き継ぎに次の2つがあります。どちらも、依頼者が `origin` にプッシュした後でないと確かめられません。

- CI（`submodules: true`）が `vendor/java-mustache-processor` を固定先で取得し、`verify` が通るか
- Dependabot が compose のイメージ（Mailpit ほか）を知らせの対象にしたか

A. この段の中で依頼者が develop をプッシュし、AI が `gh` で CI の結果と Dependabot の状態を読み取って記録する
B. 次の CI Pipeline の段に回す（この段では `Unverified` とし、持ち主を CI Pipeline と明記する）
C. 依頼者が後で GitHub の画面で確かめる（この段では `Unverified`）
X. Other (please specify)

[Answer]: A

## Q4. 初回の JavaScript の大きさの増加（U5・U6・U7）

初回に読む JavaScript（gzip）が、U4 の後の 114.1 KB から、U5 で 119.2 KB、U6 で 121.9 KB、U7 で 123.1 KB に増えました。U6 と U7 では、Vite が共有の部分の分け方を変えています（例: `I18nProvider-*.js` が分かれた）。上限は置かない決まりで、Vite の 500 KB の目安の警告も出ていません。

A. このままでよい（この段の実測を記録するだけ）
B. このままでよいが、今後の目安として上限（例: 150 KB）を README に書く（関門にはしない）
C. 増えた理由を、この段で塊ごとに調べて記録する
X. Other (please specify)

[Answer]: A

## Q5. U1 のコード生成で、設計と違う形にした点（`construction/u1-mail/code-generation/code-summary.md` の「依頼者に確かめる点」）

1. `MailUnexpectedException.of` は、送信の部品の例外が中に持つ個々の宛先の例外（`getMessageExceptions`）をたどりません。
2. 想定外の失敗を表すタグ `mail.failure.kind=unexpected` と、起動時の INFO の `encryption=UNUSED` を足しました。
3. 設計の BR1.5 の「STARTTLS のポートの既定は 587」を、部品の設定を書き換えずに README の案内で代えています。
4. 確かめのために、サブモジュールのファイルに一時的に1行足し、戻しました（変更は残っていません）。

A. 4点すべてこのままでよい（受け入れたこととして記録する）
B. 一部を直したい（どれかを X で書く）
X. Other (please specify)

[Answer]: A

## Q6. U5・U6 の画面で、計画と違う形にした点

- U5（`construction/u5-invitation-ui/code-generation/code-summary.md` 2節・8節）
  - 状態の Badge の種類の選び方
  - 375px の幅で、フォーカスした行のボタンを見える位置へ動かす直し（`scrollIntoView`）
  - 計画に無かったファイル2つ
  - `useInvitationAdmin.ts` の分岐のカバレッジが 82.4% で、U5 の中で最も低い（下限の 70% は満たす）
- U6（`construction/u6-registration-ui/code-generation/code-summary.md` 2節・10節）
  - ログアウトの後に確かめへ移る仕組みを、設計の「副作用で移す」から「描画の中で導く」に変えた（リンタの決まりのため。動きは同じ）
  - 090 のサイドバーの確かめの文言を、計画の「DSL の管理」ではなく今の画面の「DSL」にした

A. すべてこのままでよい（受け入れたこととして記録する）
B. 一部を直したい（どれかを X で書く）
X. Other (please specify)

[Answer]: A

## Q7. make-you-chic-ui 側で直すかどうか（U7 の既知の制約の2つ）

U7 の実際のブラウザの検査で、make-you-chic-ui の部品に次の2つのコントラストの不足が見つかりました。今は、既知の違反（`AVATAR_KNOWN_COMBOS` など）として扱っています。

- Avatar：頭文字が2文字のとき
- FormField：dark のテーマで、誤りの文字を出すとき

A. make-you-chic-ui のリポジトリ側で直す。直ったら固定先を更新し、既知の違反の扱いを外す。時期は、この Intent が終わった後の別の作業とする
B. A と同じだが、この Intent の中（Operation の段より前）で行う
C. 直さず、既知の制約として残す
X. Other (please specify)

[Answer]: A（依頼者が make-you-chic-ui 側の対応完了の報告を添えた。報告の要点: コミット `7865c28`（Button の primary・Avatar・FormField の誤りの文字ほか、文字色のトークンを背景用と分けて WCAG AA に対応）と `310e1ec`（Badge の success）を make-you-chic-ui の main にプッシュ済み。ライト・ダーク × 4 ブランドのすべての組で 4.5:1 以上を確かめ済み。破壊的な変更なし）

## Q8. README の差し込み口の表の「使う単位」に U8 を足すか（U8 の引き継ぎ）

README の安全の決まりの差し込み口（`SecurityRuleContributor`）の表では、「使う単位」の列が「U2、U3」のままで、U8 がありません。U8 のコード生成の計画の範囲外だったため、承認の場で確かめるとしていました。

A. この段で README の表に U8 を足す（文書だけの変更）
B. 足さない
X. Other (please specify)

[Answer]: A

## F1. make-you-chic-ui の固定先の更新の時期（Q7 の追加の質問）

Q7 の答えは A（make-you-chic-ui 側で直し、固定先の更新と既知の違反の扱いの取り外しは、この Intent の後の別の作業とする）でした。添えていただいた報告では、直しはすでに make-you-chic-ui の main にあります（`7865c28`・`310e1ec`。今の固定先 `735ef04` の後の2コミット）。この直しには、U7 の2件に加えて、U4 の既知の違反（green・orange のブランドの Button の primary）と Badge の success も含まれます。

- 固定先を更新すると、次の3つを合わせて見直す必要があります。
  - 既知の違反の一覧（U4 の `KNOWN_VIOLATIONS`、U7 の `AVATAR_KNOWN_COMBOS` と FormField の誤りの文字の扱い）
  - README
  - 実際のブラウザの検査（050〜080）
- これはアプリの変更です。Build and Test の段の中では行わず、決まりに従い、専用のコミットと fast-forward で統合します。

A. Q7 の答えのとおり、この Intent の後の別の作業とする。この段の検査は今の固定先（`735ef04`）で行い、既知の違反は既知のまま記録する
B. この Intent の中で行う。Build and Test の前にコード生成へ戻し（U4・U7 の手直しとして）、固定先を更新してから、この段の検査を新しい固定先で行う
C. この Intent の中で行うが、Build and Test と CI Pipeline を終えた後、Operation の段の前に別のコミットとして行う
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

回答をまとめると、この段では次のとおり進めます。

- `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` と、Mailpit を起動した状態の `./gradlew e2eTest` を流します。テストの件数・カバレッジ・時間・画面の時間・実際のブラウザの検査・CSP の違反を実測して、249 件の目標の表に写します。後の段が持ち主の 29 件は `Unverified` とし、持ち主を明記します。
- U7 のときに1回だけ落ちた `DisplaySettingsProvider.test.tsx` は、そのファイルを 50 回ほど繰り返し流して再現を試します。再現しなければ「不安定と確かめられていない」と記録します。再現したら原因を直します。アプリのコードが原因なら、コード生成に戻します（Q1: A）。
- k6 に U2 の場面（プリファレンスとパスワードの変更）と U3 の8つの場面を足し、`perf/README.md` に手順を書きます。登録の完了のトークンは、試験の前に招待の API で招待を出し、Mailpit の API でメールからリンクのトークンを取り出して用意します（Q2: A）。読み込みは `grafana/k6:2.3.0` のイメージの `k6 inspect --include-system-env-vars` で確かめます。負荷をかけての測定は performance-validation で行います。
- CI と Dependabot は、この段の中で依頼者が develop をプッシュし、AI が `gh` で CI の結果と Dependabot の状態を読み取って記録します（Q3: A）。AI はプッシュしません。
- 初回の JavaScript の増加（114.1 → 123.1 KB）は、このままとし、実測を記録するだけにします（Q4: A）。
- U1 の設計との差の4点（Q5: A）と、U5・U6 の計画との差（Q6: A）は、すべて受け入れたこととして記録します。
- make-you-chic-ui のコントラストの不足は、make-you-chic-ui 側で直っています（`7865c28`・`310e1ec`）。固定先の更新と既知の違反の扱いの取り外しは、この Intent の後の別の作業とします（Q7: A、F1: A）。この段の検査は今の固定先（`735ef04`）で行い、既知の違反は既知のまま記録します。
- README の安全の決まりの差し込み口の表の「使う単位」に、この段で U8 を足します（Q8: A。文書だけの変更）。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
