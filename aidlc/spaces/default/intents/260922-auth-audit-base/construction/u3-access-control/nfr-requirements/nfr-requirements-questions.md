# NFR Requirements — Questions（U3 管理画面のアクセス制御 / u3-access-control）

U3 の非機能要件で決まっていない点を確認します。U3 の安全の要件の多くは、Functional Design の決まり（/api/admin/ の保護、既定で拒否、パスの照合、401／403 の処理、拒否の出来事）と、U1・U2 の NFR（セキュリティヘッダー、Actuator の公開範囲、トークンの検証）で決まっています。

決定済みの事項（再確認はしません）: 技術は U1 の tech-stack-decisions.md のとおり（Spring Security のアクセス制御の設定、AccessDeniedHandler・AuthenticationEntryPoint）。管理者かどうかは要求ごとに内部DBから読む（U2）。

### Q1. 管理者向け領域の確認用 API の応答時間の目標は、どれにしますか？

認証が必要な API の要求ごとの処理（トークンの検証と利用者の読み取り）は 95% で 50 ミリ秒以内としています（U2 の NFR1.4）。

- A. 95% が 300 ミリ秒以内（同時 10 件の要求で）
- B. 95% が 1 秒以内（ログインと同じ目標）
- X. Other (please specify)

[Answer]: A. 95% が 300 ミリ秒以内（同時 10 件の要求で）

## Consolidated Summary Confirmation

- 確認用 API の応答時間: 同時 10 件の要求で 95% が 300 ミリ秒以内
- そのほかの U3 の非機能要件は、Functional Design の決まりと U1・U2 の NFR から導く（新しい判断は加えない）

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
