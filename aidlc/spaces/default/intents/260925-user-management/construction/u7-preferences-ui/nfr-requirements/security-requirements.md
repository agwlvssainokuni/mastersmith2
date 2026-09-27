# Security Requirements — U7 プリファレンスとパスワードの変更の画面（u7-preferences-ui）

U7 のセキュリティの要件です。U7 は画面の単位で、サーバー側の認可・検証・監査を持ちません（どれも U2）。ここでは、画面に出る個人に関する値、パスワードの値の持ち方、応答の値の扱い、今のパスワードの誤りとログイン状態、骨組み（ユーザーメニュー）の変更を扱います。答えは `nfr-requirements-questions.md`（Q1: A、Consolidated Summary Confirmation: Looks correct）です。

出典の略号:
- NFR・FR は `aidlc/spaces/default/intents/260925-user-management/inception/requirements-analysis/requirements.md`
- D・W・9節・10節は、この単位の `construction/u7-preferences-ui/functional-design/functional-spec.md`
- 「部品 7節・8節」は、同じフォルダの `frontend-components.md` の節
- 「要点 n」は、この段の `nfr-requirements-questions.md` の「NFR の要点（案）」の番号

枝番はこの単位の中で振ります。ほかの要件の置き場は `performance-requirements.md` の冒頭の表のとおりです。

要件に当たる ID が無い画面の側のセキュリティの要件（NFR9.1〜NFR9.4）は、NFR9 の枝番に寄せました。どれもテストで確かめ方を決める要件だからです（U4・U8 の前例と同じ扱い）。

## 前提

- 目標は、前の Intent から続く決まりです。
  - トークンと利用者の情報はメモリだけに持ちます（`frontend/src/features/auth/authSession.ts`）。
  - パスワード（平文・ハッシュ値とも）・トークンを、ログ・エラー応答・外部へのエクスポートに含めません（`project.md` の Forbidden）。
- サーバー側の認可（プリファレンスとパスワードの変更の API はログインした利用者だけ、未認証の 401 とログインした利用者の 200）と、サーバー側の入力の検証・今のパスワードの照合・監査は、U2 のサーバー側のテストで確かめます。画面の側の確かめと画面を隠すことは、その代わりにしません（要件 NFR4、`team.md` の Testing Posture、D7・D8）。
- GitHub のリポジトリは公開のため、テストのデータに実在の個人に関する値やパスワードを使いません（`team.md` の Way of Working）。

## 1. 個人に関する値

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR2.1 | プリファレンスの画面に出す氏名（初期値はメールアドレス、AC4.1.10）を、ブラウザのコンソール・localStorage・sessionStorage・URL に出さない。ブラウザに保存するのは U4 の鍵の3つの表示の設定（言語・テーマ・文字の大きさ）だけで、保存は U4 の `applyUserPreferences` が行い、U7 はブラウザの保存に直接触れない | 画面部品のテストで、読み込み・保存の成功・保存の失敗の後に、localStorage と sessionStorage のどの鍵にも氏名（メールアドレスの形の初期値を含む）が入らないこと、`console` の出力が無いことを見る（コード生成） | 要点 5・6、D13、U4 の NFR2.2、要件 NFR2 |

## 2. 画面の側のセキュリティ

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR9.1 | 3つのパスワードの値（今のパスワード・新しいパスワード・確かめ）は React の状態（メモリ）だけに持ち、localStorage・sessionStorage・Cookie・URL に置かない。3つの項目は `type="password"` とし、`autocomplete` を付ける（今のパスワードは `current-password`、新しい2つは `new-password`）。変更の成功（204）で3つを空に戻す。送信中に画面を離れたときは応答を捨てる | 画面部品のテスト（部品 7節の `PasswordChangePage`）。送信の前・成功の後・失敗の後に、localStorage と sessionStorage のどの鍵にもパスワードの値が入らないこと、URL に値が載らないこと、成功で3つの項目が空になることを見る | 要点 5、W9・W10、CR6.9、`project.md` の Forbidden |
| NFR9.2 | 失敗の文言は、サーバーの `detail` を使わず、応答の `code` と項目の名前・理由から画面の文言の鍵を選ぶ。応答の値と入力の値（パスワード・氏名）をブラウザのコンソールに出さない。サーバーの応答の値を HTML として差し込まない（React の文字としてだけ描く） | 画面部品のテストで、`detail` に目印の文字を入れた偽の失敗の応答を返しても、画面にその文字が出ないことと、`console` の出力が無いことを見る。`errorMessages.ts` のテスト（部品 7節）で、知らない理由が項目ごとの一般の文言になることを見る。HTML の直接の差し込みは既存のリンタの `react/no-danger` で止める | 要点 5、D13、W12 |
| NFR9.3 | 今のパスワードの誤り（400 `PASSWORD_CURRENT_MISMATCH`）は、ApiClient の 401 の更新と送り直しの流れに乗らず、ログインしたままでログインの画面へ移らない。今のパスワードの誤りの回数の制限は画面でも置かない（U2 の決定のまま）。画面の確かめ（D7）はサーバーの検証の代わりにせず、サーバーが返した項目ごとの誤り（400 `VALIDATION_FAILED`）を受ける作りを残す（D8） | 画面部品のテスト（部品 7節の「今のパスワードの誤り」）で、トークンの更新が呼ばれず、ログイン状態が変わらず、ログインの画面へ移らないことを見る。サーバー側の照合と 401・200 は U2 のサーバー側のテスト | 要点 7、W10・W12、AC5.1.6、U2 の BR4.2 |
| NFR9.4 | 骨組みの変更（ユーザーメニューの項目に `path` を足す、9節）は、登録された画面の URL（ホームを含む）だけを許し、外の URL へ移る道を作らない。`action` と `path` のどちらも無い・両方ある項目、登録されていない `path` は、登録の検査で起動を止める。既存のログアウトの項目（`action` だけ）は変わらず動く | 既存の `validateRegistrations.test.ts`・`ShellLayout.test.tsx`・`navigationItems.test.ts` に足す（部品 7節）。画面・認証に関わる変更のため、統合の前に既存の E2E（`./gradlew e2eTest`）を手元で流す（NFR9.8） | 要点 8、9節、部品 8節、`team.md` の Testing Posture |

## 3. 上流との差

承認済みの文書は書き換えません（`aidlc/spaces/default/memory/project.md` の Way of Working）。

| 決定 | 文書 | 承認済みの記述 | この段での扱い |
|---|---|---|---|
| U7 が骨組み（部品 AppFrame、持ち主は U4）の型・登録の検査・`ShellLayout` を変える | `inception/units-generation/unit-of-work.md` の U7 の境界、U4 の機能設計（登録の仕組みを変えない） | 表示の設定を当てる仕組みと骨組みは U4 | 機能設計の Q1 A で依頼者が選び、機能設計の 10節の (a) に記録済み。この段では、外の URL へ移る道を作らないことをセキュリティの要件（NFR9.4）として足した。承認済みの文書は書き換えない |
| 画面の側のセキュリティの要件を NFR9 の枝番に寄せた | 要件の NFR1〜NFR11 | 画面の側のパスワードの値の持ち方・応答の値の扱いに当たる ID が無い | 追加（NFR9.1〜NFR9.4）。U4・U8 と同じ扱い |
