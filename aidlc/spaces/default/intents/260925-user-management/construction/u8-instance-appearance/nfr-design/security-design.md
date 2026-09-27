# Security Design — U8 インスタンスの見た目の設定（u8-instance-appearance）

ログインなしで読める `GET /api/appearance`（契約 C7）のセキュリティの設計です。答えは `nfr-design-questions.md`（Q1: A、Q2: A、Consolidated Summary Confirmation: Looks correct）。

出典の略号: NFR はこの単位の NFR 要件 `construction/u8-instance-appearance/nfr-requirements/` の枝番（枝番はこの単位の中で振る）、BR は `construction/u8-instance-appearance/functional-design/rules.md`、「要点 n」は `nfr-design-questions.md` の「NFR 設計の要点（案）」の番号、C7 と共通の決まりは `inception/contract-design/contract-summary.md`。

## 1. 守る範囲の考え方

- U8 が外に出すのは、色とフォントの2つの名前だけ（秘密・個人に関する値を扱わない）。守るべきものは「公開の範囲が広がらないこと」「設定に書かれた文字列が外に出ないこと」「書き込みの口を持たないこと」の3つ。
- 認証・認可の仕組みは既存のもの（`config/SecurityConfig` のフィルターの連鎖と差し込み口）をそのまま使い、U8 は決まりを1つ足すだけにする。トークンの検証・401 の入口の処理・403 の処理・ヘッダー・セッション・CSRF は変えない（既存の `auth`・`access` と `SecurityConfig` のまま）。

## 2. 公開の決まり

### 2.1 差し込み口の決まり

U8 の差し込み口（`SecurityRuleContributor` の Bean、`appearance.web` に置く。前例は `auth/web/AuthSecurityContributor`・`access/web/AdminSecurityContributor`）は、`authorizeHttpRequests` に「メソッド GET・道 `/api/appearance`」の認証なしの決まりを1つだけ足す（NFR4.1、BR3.2）。

説明用の断片（名前はコード生成で決める）:

```java
public static final int ORDER = 410; // appearance は 400 台（Q1: A）

@Override
public void contribute(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(authorize -> authorize
            .requestMatchers(HttpMethod.GET, "/api/appearance")
            .permitAll());
    // トークンの検証・入口の処理・ヘッダーは足さない（既存のまま）
}
```

### 2.2 呼ばれる順番と既定

`SecurityConfig` の順番は、U1 の公開の決まり → 差し込み口を order の小さい順 → `/api/**` の既定（ログインが必要）→ 画面の配信の許可。U8 の決まりは既定より前に必ず当たる。U8 の決まりの道はほかの差し込み口の道（`/api/auth/login`・`/api/auth/session/**`・`/api/admin/**`・U3 の `/api/registration/verify`・`/api/registration/complete`）と重ならないため、order の値で公開の範囲は変わらない。

### 2.3 order の割り当て（Q1: A）

単位の番号ではなく機能の名前で 100 台ずつ割り当てる。本番の決まりは x10、テストの決まりは x00・x50 を使う慣習を説明文に明記する。

| 範囲 | 機能 | 本番の値 | テストの決まりが使う値 |
|---|---|---|---|
| 100〜199 | `auth`（前の Intent の U2） | 110（既存の `AuthSecurityContributor`） | 100（`TestSecurityExtensions` など）・150（`ProtectedTestEndpoint`） |
| 200〜299 | `access`（前の Intent の U3） | 210（既存の `AdminSecurityContributor`） | 200・250（`PublicApiTestRules`） |
| 300〜399 | `invitation`（この Intent の u3-invitation） | 310（U3 の NFR 設計・コード生成に引き継ぐ） | — |
| 400〜499 | `appearance`（この Intent の U8） | 410 | — |

- `common/security/SecurityRuleContributor.java` の説明文の「U2 は 100 台、U3 は 200 台」を、上の表の機能の名前の割り当てに書き直し、「x00・x50 はテストの決まりが使う」を足す。これで NFR 要件の承認の場の R-01（「U3」がどれのことか分からない）を片づける（NFR4.7）。
- 値の重なりは既存の `SecurityExtensionValidator` で起動が止まる。テストの決まりの値（100・150・200・250）を本番に使わないのは、同じテストの文脈に入ったときに起動が止まるため。

## 3. 公開の範囲の確かめ

サーバー側の結合テスト（`appearance.web` の `XxxIT`）で次を確かめる。これらのテストでは `/api/**` を公開にするテスト用の決まり（`access/testsupport/PublicApiTestRules`、`mastersmith.test-fixture.public-api`）を有効にしない。

| 要求 | 期待する応答 | 要件 |
|---|---|---|
| GET・トークンなし | 200、本文は C7 の2項目 | NFR4.1 |
| POST・トークンなし | 401 / `AUTHENTICATION_REQUIRED` | NFR4.2 |
| POST・使えるトークン付き | 405 / `METHOD_NOT_ALLOWED`、`Allow` に GET を含む | NFR4.2 |
| HEAD・トークンなし | 401（メソッドを GET に限って合わせるため公開の決まりに当たらない見込み） | NFR4.3 |
| GET・使えないトークン付き（期限切れ・改ざん） | 401 | NFR4.4 |
| GET・使えるトークン付き | 200 | NFR4.4 |

- HEAD の実際の扱いが見込みと違う（GET の決まりが HEAD にも当たる）ときは、コード生成で扱いを記録し、依頼者に確かめる（NFR4.3）。末尾に `/` の付いた道（`/api/appearance/`）も、既定の 401 になる見込みで、コード生成で確かめる。
- U8 は新しい応答・エラーの `code`・例外を足さない（NFR4.2、BR3.2）。

## 4. 応答の中身とヘッダー

- 応答の DTO は `brandColor`・`fontFamily` の2項目の `record` だけで、値は C7 の列挙の小文字の名前。業務処理が持つ値の型から写すため、設定された元の文字列は応答に届かない（NFR4.5、BR3.4）。結合テストで、許されない値（例: `red`）を設定しても本文にその文字列が出ないことを確かめる。
- ヘッダーは既存の共通の仕組みのまま付く（Content-Security-Policy（`font-src 'self'` を含む）、`X-Content-Type-Options: nosniff`、`X-Frame-Options: DENY`、`Referrer-Policy: same-origin`、`Cache-Control: no-store`）。U8 は変えない。外部のフォントを読み込まない（NFR4.8、BR3.6）。結合テストで、未認証の GET の応答に `Cache-Control: no-store` と CSP が付くことを確かめる。

## 5. ログ

- 起動時の警告のログは、項目の名前・使った既定の値・許される値の一覧だけを出し、設定された値そのものは出さない（BR2.1）。設計と確かめ方は `observability-design.md` の3節（NFR9.4）。
- 要求ごとのログは出さない。

## 6. 回数の制限

ログインなしの API の回数の制限は設けない（NFR4.6）。処理はメモリの値を読むだけで内部DB を使わず（`reliability-design.md`）、返す値に秘密が無い。受け入れる危険は、大量の要求でアプリの要求のスレッドが占められること。配備先が決まったときに前段（逆プロキシなど）の制限で扱う。

## 7. 脅威と設計の対応

| 脅威 | 設計 | 要件 |
|---|---|---|
| 公開の決まりが広すぎて、ほかのメソッドや道がログインなしになる | メソッド GET・道 `/api/appearance` に限った1つの決まり。ほかは既定のまま。3節のテスト | NFR4.1〜NFR4.3 |
| 応答からほかの設定・利用者の情報が漏れる | 2項目の DTO を列挙の値から写す | NFR4.5 |
| 設定に書いた文字列（誤って秘密を書いた場合を含む）がログ・応答に出る | 応答は列挙の値だけ、警告のログは項目の名前・既定・許される値だけ | NFR4.5、NFR9.4 |
| 大量の要求による濫用 | 受け入れる。処理は軽く内部DB を使わない | NFR4.6 |
| 見た目の設定を利用者の操作で書き換える | 書き込みの API を持たない | NFR4.2、BR3.1 |
| order の重なりで決まりの順番が崩れる | 機能ごとの割り当て（2.3）と、既存の起動時の検査 | NFR4.7 |

## 8. 上流との差

承認済みの文書は書き換えない（`aidlc/spaces/default/memory/project.md` の Way of Working）。次の点を、この段の依頼者の決定（Q1: A）として記録する。

| 項目 | 承認済みの記述 | この段での扱い |
|---|---|---|
| order の決め方 | `SecurityRuleContributor` の説明文は単位の番号（U2 は 100 台、U3 は 200 台）で割り当てている。NFR4.7 は「既存と U3 が足す値と重ならない値」とだけ書いている | 機能の名前で 100 台ずつ割り当てる形に変え、U8 は 410 とした（2.3） |
| u3-invitation の値 | U3 の承認済みの機能設計（`construction/u3-invitation/functional-design/rules.md`）と NFR 要件（NFR4.2）は「順番の値はコード生成で決める」としている | U8 の段で 310 を先に決めた。U3 の NFR 設計とコード生成に割り当てとして引き継ぐ |
| `common.security` の説明文の変更 | NFR9.7 は説明文の書き足しを「コードの中身を変えない」扱いとしている | 書き直しの範囲が「U8 の範囲の書き足し」から「割り当て全体の書き直し」に広がった。`common.security` は `backend/build.gradle.kts` の `packagesJudgedByTotal` にあり、`team.md` の「手を入れる Bolt ではテストを足して一覧から外す」が説明文だけの変更にも当たるかは明記されていない。書き直す Bolt（先に作る U3 の B3 か U8 の B4）の計画で、依頼者に扱いを確かめる |
