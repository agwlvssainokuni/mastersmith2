# Code Generation — Questions（U2 認証 / u2-authentication）

U2 の作業計画（`code-generation-plan.md`）で決めていただく点（D1〜D3）と、確認していただく点（C1〜C9）です。回答を計画に反映してから、計画の承認（Plan Approval）をお願いします。

### Q1. U1 のファイルを3か所だけ変えてよいですか？（計画 D1）

- `backend/build.gradle.kts`: 依存関係を1行足す（`spring-boot-starter-oauth2-resource-server`）
- `frontend/playwright.config.ts`: テストのたびに作る署名鍵と初期管理者の値を環境変数で渡す（署名鍵が無いとアプリが起動しないため）
- `frontend/e2e/u1-skeleton.e2e.ts`: 集めるエラーから、起動時のトークンの更新の 401 だけを除く（未ログインで画面を開くと必ず起き、今のままでは U1 の E2E が失敗するため）

- A. 3か所とも変えてよい
- B. 変えない。別の方法を考える
- X. Other (please specify)

[Answer]: A. 3か所とも変えてよい

### Q2. U3 ができるまで、トークンが無い・無効な要求への 401 の応答をどうしますか？（計画 D2）

- A. U2 が 401 / `AUTHENTICATION_REQUIRED` を共通の形で返す入口の処理を置き、U3 が後で置き換える
- B. U3 ができるまで、U1 の既定の応答（本文なしの 401）のままにする
- X. Other (please specify)

[Answer]: A. U2 が 401 / `AUTHENTICATION_REQUIRED` を共通の形で返す入口の処理を置き、U3 が後で置き換える

### Q3. NFR 設計（`security-design.md` 8章）と承認済みの Functional Design の違い2点を、どう揃えますか？（計画 D3）

違いは、ログインの手順の順番（利用者の検索 → 照合 → 短いトランザクションでロックの状態を排他つきで読み書き）と、4つ目の問題の種類 `ORIGIN_NOT_ALLOWED`（403）です。

- A. NFR 設計のとおりに作り、違いを code-summary に記録する
- B. Functional Design をやり直してから進める
- X. Other (please specify)

[Answer]: A. NFR 設計のとおりに作り、違いを code-summary に記録する

### Q4. 計画の確認事項 C1〜C9 を、このまま進めてよいですか？

- C1 環境変数の名前: `MASTERSMITH_AUTH_SIGNING_KEY`・`MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL`・`MASTERSMITH_AUTH_INITIAL_ADMIN_PASSWORD` と、任意の `MASTERSMITH_AUTH_*` 7つ。既定値は Java 側に置き、U1 の `application.yaml` には書き足さない
- C2 API の形: ログインと更新の応答は `{accessToken, expiresAt, user:{email, admin}}`、ログアウトは 204
- C3 U3 との約束の例外 `TokenAuthenticationException` は `OAuth2AuthenticationException` を継承する（`AuthenticationException` の子）。差し込み口の order は 110（100 は U1 のテストが使っているため）
- C4 注入できる時計（`Clock`）の Bean を U2 が置き、U4 もこれを使う
- C5 画面: API 呼び出しの共通部分は U3 も使うため `frontend/src/shared/api-client/`、認証の画面は `frontend/src/features/auth/`
- C6 U2 を入れると署名鍵が無いと起動しなくなるため、U1 の結合テストが通るよう、テストのソースの中だけに鍵を作って入れる仕組みを置く（U1 のテストのファイルは変えない）
- C7 ロックの状態の表は、ダミーの行を負の ID で同じ表に置くため `users` への外部キーを置かない。H2 で「ほかの試みが持つ行を飛ばす」指定と「待ちの上限 3 秒」が効くかを Step 6 で確かめ、効かなければ計画の代わりの方法、それでも駄目なら生成を止めて案を示す
- C8 ロックの状態の行は、利用者の作成時の出来事（`UserCreatedEvent`）で作る（`user` が `auth` に依存しない形）
- C9 U2 の E2E は「ログイン → ホーム → 再読み込み → ログアウト」まで。管理画面に入れるかは U3 で足す

- A. すべてこのまま進めてよい
- B. 直したい点がある（番号と内容を書く）
- X. Other (please specify)

[Answer]: A. すべてこのまま進めてよい

## Plan Approval

最初の確認で、C1 について「全単位で共用するものなので `application.yaml` を変えてよい」との修正依頼を受けた。計画の C1 と Step 1 を、`application.yaml` の `mastersmith.auth` のまとまりに `${MASTERSMITH_AUTH_XXX:既定値}` の形で書く内容に直した。

2回目の確認で、C2 について「refreshToken も返却するよね？」との質問を受けた。承認済みの設計のとおり、リフレッシュトークンは JSON の本文ではなく同じ応答の `HttpOnly` の Cookie（`mastersmith_refresh`）で返す。計画の C2 にこれをはっきり書いた。

U2 の作業計画（`code-generation-plan.md`、埋め込んだ Testing Contract を含む）と、テストの手順書（`unit-test-instructions.md`）を、この内容で承認しますか？

[Approval Fingerprint]: sha256:v3:9dd4cf4763f279cb25619042903eb42cc0c0a4c8f2b470c997171091739e83ab
[Planned Source]: 331a7226f7571307892bfc331c30964afaaa1c2928deec5bdbbb8369403cd7d7

- Approve Plan
- Request Changes

[Answer]: Approve Plan
