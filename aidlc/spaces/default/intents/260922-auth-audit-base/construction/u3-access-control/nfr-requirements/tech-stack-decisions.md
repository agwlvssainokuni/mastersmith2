# Tech Stack Decisions — U3 管理画面のアクセス制御（u3-access-control）

U3 はアプリ全体の技術の選定（`aidlc/spaces/default/intents/260922-auth-audit-base/construction/u1-app-skeleton/nfr-requirements/tech-stack-decisions.md`）に従う。本書は、U3 で使う仕組みだけを書く。

| 分類 | 選定 | 理由 | 出典 |
|---|---|---|---|
| アクセスの決まり | Spring Security のアクセス制御の設定（パスの型で、管理者のみ・公開・ログイン必須の順に書く） | U3 の決まり 1.1〜1.4。個々の API での宣言に頼らない | Functional Design Q1・Q2 |
| パスの検査 | Spring Security の既定の要求の検査（正規化されていないパスの拒否）を外さずに使う | U3 の決まり 1.6 | — |
| 401／403 の処理 | 独自の AuthenticationEntryPoint と AccessDeniedHandler。応答は U1 の共通の組み立ての仕組みで作る | U3 の決まり 3.6 | — |
| 出来事の通知 | Spring のアプリ内の出来事の仕組み（ApplicationEventPublisher） | Domain Design ADR-004 | — |
| 画面 | React、React Router、react-i18next、make-you-chic-ui | U1 の選定 | U1 |
