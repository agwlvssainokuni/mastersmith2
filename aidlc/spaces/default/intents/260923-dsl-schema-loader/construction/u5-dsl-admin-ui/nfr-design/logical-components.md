# Logical Components — U5 DSL の管理画面（u5-dsl-admin-ui）

U5 の論理的な部品と、障害の範囲を示す。部品の構成は機能設計の `frontend-components.md` が正本で、ここでは非機能の作りが効く場所だけを示す。

## 1. 非機能の作りが効く部品

| 部品 | 効く作り | 置き場 |
|---|---|---|
| DslAdminPage | 今の状態とプレビューを同時に要求する（NFR1.18）。`DSL_BUSY` の Alert（NFR5.6） | `frontend/src/features/dsl/` |
| DslDiffTable・DslMenuTree | 開いた行・段だけを描く（NFR1.19） | `frontend/src/features/dsl/` |
| DslSubmitForm | 読む前の `File.size` での判定（NFR1.20）、JSON Schema のリンク（NFR3.11） | `frontend/src/features/dsl/` |
| DslErrorList | 先頭 100 件だけを描く（NFR1.21） | `frontend/src/features/dsl/` |
| dslApi・ApiClient（拡張） | トークンの扱いは ApiClient だけ、ダウンロードの一時的な URL（NFR3.10） | `frontend/src/features/dsl/`・`frontend/src/shared/api-client/` |
| 登録の messages | ja・en の文言（NFR9.1） | `frontend/src/features/dsl/registration.ts` |

## 2. 障害の範囲

- 画面の不具合の影響は DSL の管理画面に限られる。機能の登録は既存の仕組みで、ほかの画面の登録を書き換えない（機能設計の BR1.1）。
- サーバーの失敗（401・403・404・409・413・422・503。503 は対象DB の設定が無い・接続できない（生成のとき）と `DSL_BUSY` で、文言と扱いが違う）は、機能設計の決まりと `DSL_BUSY` の扱いで画面の中に示し、画面全体を止めない。
