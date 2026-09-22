# NFR Design — Questions（U3 管理画面のアクセス制御 / u3-access-control）

U3 の NFR 要件（`nfr-requirements/` の各文書）を満たす設計の方針のうち、これまでの段階で決まっていない点を確認します。

決定済みの事項（再確認はしません）: 判定の決まりは U3 の Functional Design（BR1〜BR6）のとおり。技術は U3 の `tech-stack-decisions.md` のとおり（Spring Security のアクセス制御の設定、独自の AuthenticationEntryPoint と AccessDeniedHandler、ApplicationEventPublisher）。U1 の NFR Design の決定（U1 が Spring Security のフィルターの連鎖を用意し、U3 は差し込み口で「管理者のみ」「公開」の決まりと、`/api/**` の既定の扱い（ログイン必須）を足す）。U2 の NFR Design の決定（401 の応答は U1 の共通の組み立ての仕組みで返す）。確認用 API の目標は同時 10 件で 95% が 300 ミリ秒以内（NFR1.1）。

### Q1. 正規化されていないパス（`/api/admin/..;/`、エンコードされた区切りなど）を判定の前に拒否したときの 400 の応答は、どの形にしますか？（NFR3.3）

この拒否は Spring Security の要求の検査（フレームワークの既定の防御）が、フィルターの連鎖の入口で行います。何もしないと、フレームワークの既定の空の 400 が返り、U1 の共通のエラー応答の形（type・code・traceId、U1 の決まり 5.1）にならず、U1 のセキュリティ関係のヘッダーも付きません（ヘッダーを書く処理より手前で拒否されるため）。

- A. 拒否の処理を置き換え、U1 の共通の組み立ての仕組みで ErrorResponse の形の 400 を返す。code は新しく `REQUEST_REJECTED` とし、日英の説明つきの問題の種類を U3 が定義する。U1 と同じセキュリティ関係のヘッダーも付ける
- B. A と同じく ErrorResponse の形にするが、code は既存の `VALIDATION_FAILED` を使う（新しい code を増やさない）
- C. フレームワークの既定の空の 400 のままにする（形はそろわないが、細工された要求への応答なので気にしない）
- X. Other (please specify)

[Answer]: A. 拒否の処理を置き換え、U1 の共通の組み立ての仕組みで ErrorResponse の形の 400 を返す。code は新しく `REQUEST_REJECTED` とし、日英の説明つきの問題の種類を U3 が定義する。U1 と同じセキュリティ関係のヘッダーも付ける

## Consolidated Summary Confirmation

- 細工されたパスの拒否の応答（Q1）: フレームワークの要求の検査で拒否したときも、U1 の共通の組み立ての仕組みで ErrorResponse の形の 400 を返す。code は新しく `REQUEST_REJECTED`（日英の説明つきの問題の種類を U3 が定義）。U1 と同じセキュリティ関係のヘッダーも付ける

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
