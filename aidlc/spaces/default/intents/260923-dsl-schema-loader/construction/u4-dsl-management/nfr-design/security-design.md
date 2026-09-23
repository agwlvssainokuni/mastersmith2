# Security Design — U4 DSL の管理（u4-dsl-management）

U4 のセキュリティの要件（`construction/u4-dsl-management/nfr-requirements/security-requirements.md`）を満たす作りの方針。

## 1. 本文の上限（NFR2.6、Q1: A、2回目の Request Changes）

- 既存の本文の上限の仕組み（`RequestSizeLimitFilter`、Servlet の Filter）の置き場を、`SecurityConfig` で**認証（JWT の検証、`BearerTokenAuthenticationFilter`）と認可（`AuthorizationFilter`）の後**に移す（今はヘッダーを書く処理の後・認証の前）。本文を読むのは、ログインしていて、その道を使える人の要求だけになる。認証・認可で断られる要求は、本文を読む前に 401・403 で返る。
- 道ごとの上限と code の表を足す。`POST /api/admin/dsl/preview` だけ上限 `mastersmith.dsl.max-submit-size`（既定 10MB＝10,485,760 バイト）・code `DSL_TOO_LARGE`、ほかの道は今までどおり `mastersmith.web.max-request-body-size`（既定 1MB）・code `PAYLOAD_TOO_LARGE`。
- `Content-Length` がある送り方: ヘッダーの値だけで判定し、超えていれば本文を読まずに 413。
- `Content-Length` が無い送り方（chunked）: 既存の仕組みのとおり、読んだ量を数え、上限を超えた時点で読むのをやめて 413。上限までの本文は読み込んで後ろへ渡す。
- 監査: 認証の後に動くため、操作した人が分かる。Filter に「道で断ったことを知らせる口」（断った道・上限・送られた大きさを渡す）を持たせ、U4 が投入の道の知らせを受けて、受け付けなかった投入の監査の出来事（理由 SIZE_LIMIT、識別なし、操作した人つき）を出す。共通の部品（`common.web`）は DSL を知らない。
- 既存の働きの変化: ログインしていない大きな要求は、今は 413 だが 401 になる。前の Intent の NFR3.12（本文の上限で 413）は、ログインした要求には今までどおり効く。既存のテスト3件（`ExposureIT` の2件、`SecurityHeadersIT` の1件、ログインなしで送って 413 を確かめている）は、コード生成でログインしてから送る形に直す。

## 2. アクセス制御と検証（NFR3.7）

- `/api/admin/dsl/**` は既存の AccessControl（管理者だけ。未認証 401・管理者でない 403）。DSL の道だけの例外は作らない。
- 本文はサーバー側で必ず U2 の検証を通す。画面の検証に頼らない。
- 適用は表示したプレビューの識別（`previewId`）を必ず受け取り、今のプレビューと違えば 409（機能設計の BR4.1・BR4.3）。

## 3. 応答とダウンロード（NFR3.8・NFR4.10・NFR5.4・NFR5.5）

- エラー応答は既存の共通の変換（`@RestControllerAdvice`）で Problem Details と `code`。`DSL_BUSY`（503）を `ProblemTypeCatalog` に日英の説明つきで1つの状態コードで登録する。
- 誤りの一覧は U2 の文言の鍵から、要求の表示言語の文言にする。部品の例外の文言・`detail` の内部の情報・スタックトレースを載せない。
- ダウンロードは `Content-Type: application/yaml`、`Content-Disposition: attachment; filename="dsl-preview-<識別の先頭12文字>.yaml"`（適用中は `dsl-applied-`）。既存の `X-Content-Type-Options: nosniff` が付く。
- 応答・ダウンロード・ログ・監査に接続先を含めない（DSL の書式に接続先が無く、U1 の結果は種類だけ）。

## 4. 承認済みの文書との差

| 決定 | 文書 | 承認済みの記述 | 実装での扱い |
|---|---|---|---|
| 本文をバイト列で保存（Q3: A） | `construction/u4-dsl-management/functional-design/entities.md`（`yamlText` は text） | 文字列 | `BINARY LARGE OBJECT`。列の名前も本文のバイト列だと分かるものにする（コード生成で決める） |
| 照合の全体の上限を置かない（決定 B） | U4 の NFR 要件の NFR1.8（10 秒）・U1 の NFR 設計の security-design.md 2節（照合の全体 8 秒） | 10 秒・8 秒 | 全体の上限は作らない。応答しない対象DB では最悪 23〜28 秒 |
| `DSL_BUSY`（NFR 要件で決定） | 契約 C6 | 無い | 3節と scalability-design.md のとおり |
| 大きさの確かめを業務の決まりから共通の仕組みへ移す（2回目の Request Changes） | `construction/u4-dsl-management/functional-design/rules.md` の BR1.2・BR1.6（投入の API で 5MB を超えたら 413） | 業務処理で本文を受け取った後に確かめる形に読める | 認証・認可の後に移した既存の `RequestSizeLimitFilter` の道ごとの上限で、本文を読む前（chunked は読みながら）に確かめる。監査（BR7.4）は知らせる口を通して U4 が出す |

## 5. 残る危険

| 危険 | 扱い |
|---|---|
| chunked の送り方では、重なりで断る要求（`DSL_BUSY`）の本文も Filter が先に読む（最大 10MB） | 受け入れる。読むのはログインした管理者の要求だけで、`Content-Length` がある送り方（画面の送り方）では本文を読まずに断れる |
| ログインしていない大きな要求の応答が 413 から 401 に変わる | 受け入れる（依頼者の決定）。既存のテストをコード生成で直す |
