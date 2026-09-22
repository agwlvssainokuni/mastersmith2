# Tech Stack Decisions — U2 認証（u2-authentication）

U2 はアプリ全体の技術の選定（`aidlc/spaces/default/intents/260922-auth-audit-base/construction/u1-app-skeleton/nfr-requirements/tech-stack-decisions.md`）に従う。本書は、U2 で追加して決めるものだけを書く。

| 分類 | 選定 | 理由 | 出典 |
|---|---|---|---|
| パスワードのハッシュ | Spring Security の BCryptPasswordEncoder（cost 既定 12、設定で変更可） | Q1。追加の部品が要らない | Q1 |
| アクセストークン | JWT（HS256）。発行と検証は Spring Security の OAuth2 Resource Server（JOSE）の仕組みを使う | Q2。署名方式を固定した検証を備える | Q2、FR4.4 |
| 署名鍵 | 256 ビット以上の乱数を環境変数で渡す | Q2、NFR3 | Q2 |
| リフレッシュトークン | 256 ビット以上の暗号学的な乱数（SecureRandom）。保存は SHA-256 のハッシュ | U2 の決まり 5.1 | U2 FD |
| CSRF 対策 | SameSite=Strict の Cookie と、トークンの更新・ログアウトの API での Origin ヘッダーの確認。Spring Security の CSRF トークンの仕組みは使わない（Cookie で認証するのは2つの API だけで、ほかの API は Authorization ヘッダーのため） | Q4 | Q4 |
| 内部DBへのアクセス | Spring Data JPA。同じ利用者の試みを1つずつ行う処理は行の排他（悲観的な排他）、条件付きの無効化は更新の問い合わせで書く | U1 Q8、U2 の決まり 3.8・5.6 | U1 Q8 |
| 使い終わったトークンの削除 | Spring の定期実行の仕組み（1日1回） | NFR1.7 | — |
| 画面 | React、react-i18next、make-you-chic-ui のフォーム部品 | U1 の選定 | U1 |

## 検討して採らなかったもの

| 候補 | 採らなかった理由 |
|---|---|
| Argon2id | Q1 で bcrypt を選んだ |
| 公開鍵方式の署名（Ed25519 など） | 発行も検証も同じアプリ1台で、共有鍵で足りる（Q2） |
| 接続元ごとのログイン回数の制限 | 利用者 50 名の社内向けで、危険を受け入れた（Q3） |
| Spring Security の CSRF トークン | Cookie で認証する API が2つだけで、Origin の確認と SameSite=Strict で足りる（Q4） |
