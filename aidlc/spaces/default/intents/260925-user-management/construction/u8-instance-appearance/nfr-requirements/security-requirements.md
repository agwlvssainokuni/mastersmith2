# Security Requirements — U8 インスタンスの見た目の設定（u8-instance-appearance）

U8 のセキュリティの要件です。要件定義の NFR4（認可）を、ログインなしで読める `GET /api/appearance`（契約 C7）に当てます。答えは `nfr-requirements-questions.md`（Q1: A、Consolidated Summary Confirmation: Looks correct）。

出典の略号: NFR・FR は `aidlc/spaces/default/intents/260925-user-management/inception/requirements-analysis/requirements.md`、BR はこの単位の `construction/u8-instance-appearance/functional-design/rules.md`、「要点 n」はこの段の `nfr-requirements-questions.md` の「NFR の要点（案）」の番号、C7 と共通の決まりは `inception/contract-design/contract-summary.md`、U4 D10 は `construction/u4-display-foundation/functional-design/functional-spec.md` の D10。枝番はこの単位の中で振る。

## 前提

- 要件の NFR4 は、招待・一覧・送り直し・取り消し・プリファレンス・パスワードの変更・登録の完了の API の認可を決め、どれもサーバー側のテストで 401・403・200 を確かめるとしている。`GET /api/appearance` は NFR4 の一覧に無いが、契約の共通の決まり（認可）で「差し込み口で公開にする」と決まっている。この単位は、NFR4 と同じ確かめ方（サーバー側のテスト）を公開の範囲に当てる。
- 返すのは色とフォントの名前だけで、秘密・個人に関する値を扱わない（C7、ADR-006）。
- 既存のアクセスの決まり: `/actuator/health`・`/api/problems/**` の公開 → 差し込み口（`SecurityRuleContributor`）を order の小さい順 → `/api/**` の既定（ログインが必要）→ 画面の配信の許可（`backend/src/main/java/cherry/mastersmith/config/SecurityConfig.java`）。公開の道でも `Authorization: Bearer` が付いていればトークンを検証する（`auth/web/AuthSecurityContributor.java`）。

## 1. 認可と公開の範囲

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR4.1 | トークンを付けない `GET /api/appearance` は、認証を求めずに 200 と契約 C7 の応答を返す。公開の決まりは U8 の差し込み口で、メソッドを GET に限って足す | 結合テスト（未認証の GET が 200 で、本文が C7 の形） | NFR4、BR3.2、C7、要点 4 |
| NFR4.2 | GET 以外のメソッド（POST・PUT・PATCH・DELETE）は公開にしない。未認証・使えないトークンなら既存の 401 / `AUTHENTICATION_REQUIRED`、使えるトークン付きなら既存の 405 / `METHOD_NOT_ALLOWED` と `Allow` の見出しになる。U8 は新しい応答・`code`・例外を足さない | 結合テスト（未認証の POST が 401 / `AUTHENTICATION_REQUIRED`、使えるトークン付きの POST が 405 / `METHOD_NOT_ALLOWED` で `Allow` に GET を含む） | NFR4、BR3.2、要点 4 |
| NFR4.3 | `HEAD`・`OPTIONS` も公開に足さない。未認証の `HEAD` は既存の 401 になる。使えるトークン付きの `HEAD` は Spring の既定で GET と同じ処理が本文なしで返ることを受け入れる | 結合テスト（未認証の HEAD が 401）。実際の扱いが違えば（GET の公開の決まりが HEAD にも当たれば）、コード生成で扱いを記録し、依頼者に確かめる | BR3.2（GET だけ）、要点 5 |
| NFR4.4 | 期限切れ・改ざんなどの使えないトークンを付けた GET の 401 は、既存の認証の扱いのまま受け入れる。U8 と `auth` の作りは変えない。画面の側（U4）はこの API にトークンを付けない | 結合テスト（使えないトークンを付けた GET が 401、使えるトークンを付けた GET が 200）。画面の側のテストは U4 | BR3.3、U4 D10 |
| NFR4.5 | 応答の本文は `brandColor`・`fontFamily` の2項目だけで、どちらも契約 C7 の列挙の小文字の名前とする。設定された元の文字列・ほかの設定の値・利用者の情報を載せない | 結合テスト（本文の項目がちょうど2つで、許されない値を設定しても元の文字列が本文に出ない） | BR3.1、BR3.4、C7、要点 7 |
| NFR4.6 | ログインなしの API の回数の制限は設けない。返すのは秘密を含まない2つの名前だけで、処理はメモリの値を読むだけで内部DB を使わない（NFR5.1）。既存の公開の `GET /api/problems/{slug}` と同じ扱いで、前の Intent でログインの API の接続元ごとの回数の制限も設けないと決めている。受け入れる危険は、大量の要求でアプリのスレッドが占められること。配備先が決まったときに前段（逆プロキシなど）の制限で扱う | 確かめるテストは無い（設けないことの記録）。配備先が決まったときに見直す | 要点 6、`aidlc/spaces/default/intents/260922-auth-audit-base/construction/u2-authentication/nfr-requirements/tech-stack-decisions.md` |
| NFR4.7 | U8 の差し込み口の order は、既存の値（`AuthSecurityContributor` の 110、`AdminSecurityContributor` の 210）と U3 が足す値と重ならない値にする。重なりは既存の起動時の検査（`SecurityExtensionValidator`）で起動が止まる。`SecurityRuleContributor` の説明文の order の割り当て（今は U2 の 100 台・U3 の 200 台だけ）に U8 の範囲を足す | 既存の起動時の検査のテスト。説明文の書き足しはコード生成の計画で拾う（機能設計の R-01） | BR3.2、要点 12 |

## 2. 応答のヘッダーとログ

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR4.8 | 応答のヘッダーは既存の共通の仕組みのまま付く（Content-Security-Policy、`X-Content-Type-Options: nosniff`、`X-Frame-Options: DENY`、`Referrer-Policy: same-origin`、`Cache-Control: no-store`）。U8 はヘッダー・CSP（`font-src 'self'` を含む）・キャッシュの指定を変えない。外部のフォントを読み込まない | 結合テスト（未認証の GET の応答に `Cache-Control: no-store` と CSP が付く） | BR3.6、要点 7・8 |

起動時の警告のログに設定された値そのものを出さない決まり（BR2.1）は、観測性の要件 `observability-requirements.md` の NFR9.4 で扱う（ログの形と一緒に確かめるため）。

## 3. 脅威と扱い

| 脅威 | 扱い | 要件 |
|---|---|---|
| 公開の決まりが広すぎて、ほかのメソッドや道がログインなしになる | メソッドを GET・道を `/api/appearance` に限って足し、ほかのメソッドの 401・405 と HEAD の 401 をテストで確かめる | NFR4.1〜NFR4.3 |
| 応答からほかの設定・利用者の情報が漏れる | 応答を2項目の列挙の値に限る | NFR4.5 |
| 設定の値に書いた文字列（誤って秘密を書いた場合を含む）がログ・応答に出る | 応答は列挙の値だけ、警告のログは項目の名前・既定・許される値だけ | NFR4.5、NFR9.4 |
| 大量の要求による濫用 | 受け入れる（回数の制限なし）。処理は内部DB を使わず軽い | NFR4.6、NFR5.1 |
| 見た目の設定を利用者の操作で書き換える | 書き込みの API を持たない（GET だけ） | NFR4.2、BR3.1 |
| 差し込み口の order の重なりで決まりの順番が崩れる | 既存の起動時の検査で起動を止める | NFR4.7 |

## 4. 上流との差

承認済みの文書は書き換えない（`aidlc/spaces/default/memory/project.md` の Way of Working）。承認済みの文書と食い違う記述は無く、次は追加だけ。

| 項目 | 承認済みの記述 | この段での扱い |
|---|---|---|
| NFR4 の対象 | 要件の NFR4 の一覧に `GET /api/appearance` は無い | 契約の共通の決まり（認可）と BR3.2 の公開の範囲を、NFR4 と同じ確かめ方で NFR4.1〜NFR4.4 に書いた |
| HEAD・OPTIONS の扱い | 機能設計の BR3.2 は「GET だけ公開、ほかのメソッドは既定」とし、HEAD を名指ししていない | BR3.2 を文字どおりに読み、HEAD も公開にしないとした（NFR4.3）。実際の扱いはテストで確かめる |
| 回数の制限 | 要件・契約・機能設計に記述が無い（契約の未解決の点は C6 の登録の完了の API だけ） | 設けないと決めた（NFR4.6、要点 6） |
