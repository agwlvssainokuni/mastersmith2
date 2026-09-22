# Code Generation — Questions（U3 アクセス制御 / u3-access-control）

U3 の作業計画（`code-generation-plan.md`）で決めていただく点（D1〜D3）と、確認していただく点（C1〜C9）です。回答を計画に反映してから、計画の承認（Plan Approval）をお願いします。

### Q1. U1 のテストの設定 8 ファイルを変えてよいですか？（計画 D1）

U3 が `/api/**` を既定でログイン必須にするため、U1 のテストのうち8ファイルが今のままでは通りません。いずれもテストと E2E の実行の設定で、本番の設定と品質の目標は変えません。

- ルートの `build.gradle.kts`: `e2eTest` の対象を U1 の E2E だけから `e2e/` 全体に広げる（代表の流れを1コマンドで動かすため）
- `SecurityExtensionIT`・`TestSecurityExtensions`: 役の `ApiDefaultAccess` を取り除く（U3 の本物と2つになると起動が失敗するため）
- `ErrorResponseIT`・`TracingAndLoggingIT`・`ExternalExportIT`・`ExposureIT`・`SecurityHeadersIT`: テストの中だけで既定の拒否を緩める設定を1行足す（いずれもアクセス制御ではなく、エラー応答・トレース・ヘッダーを確かめるテストのため）

- A. 上の8ファイルを変える。緩める仕組みはテストのソースの中だけに置き、本番の設定では切り替えられないようにする
- B. `ApiDefaultAccess` の既定を設定で切り替えられるようにする（本番でも既定の拒否を無効にできてしまうため勧めません）
- X. Other (please specify)

[Answer]: A. 上の8ファイルを変える。緩める仕組みはテストのソースの中だけに置き、本番の設定では切り替えられないようにする

### Q2. U2 が置いた 401 の入口の処理を、どう置き換えますか？（計画 D2）

- A. U3 は「管理者のみのパスか・拒否の理由・出来事の通知」だけを受け持ち、401 の応答の書き出しは U2 の処理に任せる（応答の形が1か所に保たれ、U2 のソースもテストも変えずに済む）
- B. U3 が自分で 401 を書く（U2 のテストを直す必要があり、同じ処理が2か所に増える）
- X. Other (please specify)

[Answer]: A. U3 は「管理者のみのパスか・拒否の理由・出来事の通知」だけを受け持ち、401 の応答の書き出しは U2 の処理に任せる

### Q3. 承認済みの Functional Design との違い3点を、どう揃えますか？（計画 D3）

1. 問題の種類に `REQUEST_REJECTED`（400）を加える → NFR 設計に合わせる
2. 正規化されていないパスの拒否の応答の形（共通の形と U1 のヘッダー）→ NFR 設計に合わせる
3. アクセス拒否の出来事に要求のパスを載せる → NFR 設計に合わせる（U4 の NFR 設計の承認のときに、あなたが「アクセス拒否のときだけ要求のパスを記録する」と決めた内容にそろえます。計画は当初これを載せない形でしたが、こちらで直しました）

- A. 上のとおり作る。設計の文書は書き換えず、違いを `code-summary.md` と `traceability.json` に記録する
- B. 先に Functional Design をやり直してから進める
- X. Other (please specify)

[Answer]: A. 上のとおり作る。設計の文書は書き換えず、違いを `code-summary.md` と `traceability.json` に記録する

### Q4. 計画の確認事項 C1〜C9 を、このまま進めてよいですか？

- C1 パッケージは `cherry.mastersmith.access`。表を持たないため DB の部分は作らない。差し込み口の order は 210
- C2 確認用の API は `GET /api/admin/check` で 204 を返す
- C3 U3 の出来事は DB の更新を伴わないため、要求と同じ流れの中で応答を書く前に知らせる（U2 は確定の後に知らせる）
- C4 正規化されていないパスの拒否は、Spring Security の仕組みに差し込む形を第一案とし、効くことをテストで確かめる。効かなければ代わりの方法、それでも駄目なら生成を止めて案を示す
- C5 接続元IP・User-Agent・トレースIDは U2 の仕組みをそのまま使う（U2 の出来事と取り方をそろえる）
- C6 画面は `frontend/src/features/admin/`。経路は `/admin`（管理者のみ、AppShell の中、遅延読み込み）、サイドバーに「管理」をホームの次に置く
- C7 テストの中だけで既定の拒否を緩める仕組みを U3 のテストのソースに置く（本番の設定では切り替えられない）
- C8 E2E で代表の流れ「ログイン → 管理画面 → ログアウト」を完成させる
- C9 管理者でない利用者は、結合テストの中で作る

- A. すべてこのまま進めてよい
- B. 直したい点がある（番号と内容を書く）
- X. Other (please specify)

[Answer]: A. すべてこのまま進めてよい

## Plan Approval

U3 の作業計画（`code-generation-plan.md`、埋め込んだ Testing Contract を含む）と、テストの手順書（`unit-test-instructions.md`）を、この内容で承認しますか？

[Approval Fingerprint]: sha256:v3:b1d6994558e42d9d4a309a454db992ae5cb04b771a9767fb33e3e4c6f701eb23
[Planned Source]: 850a55ece8b1484883b87959265f6577c995c715ed04cae30deba262f1302351

- Approve Plan
- Request Changes

[Answer]: Approve Plan
