# 観測の要件 — U5 navigation

## 出典

- `functional-spec.md`・`rules.md`（この単位の承認済みの機能設計。BR4.2・BR5.5・BR7.3・BR7.4）
- `requirements.md`（NFR1.6、NFR5.1・NFR5.2）
- `contract-summary.md`（C9）
- `technology-stack.md`（コード知識ベース。`TraceAspect`・`UrlQueryStrippingObservationFilter`・`http.server.requests`）
- この段の答えとまとめの確認（問い合わせの値が出る経路を結合テストで確かめる、p95 の警報が無いことは受け入れた制約）。
- 機能設計の再レビューの R-06、role のレビューの R-09。

## ID の対応表

全体の表は `security-requirements.md` の「ID の振り方」にある。上流の NFR5（監査・指標）はこの単位では当たらない。この単位の観測の要件は、意味の近い当たる枝番（NFR1.6 の記録への漏えい）の続きで振る。

| 上流の枝番 | この単位での扱い | この単位の ID |
|---|---|---|
| NFR5.1 監査 | 当たらない（監査に残す操作が無い。置き場の 403 も残さない、機能設計 Q6 A） | — |
| NFR5.2 指標 | 当たらない（対象は管理の API と作業ロールの切り替え。U5 は既存の `http.server.requests` だけ） | — |
| NFR1.6 記録への漏えい | 当たる | NFR1.8 |

## 今の観測の仕組み（コードで確かめた事実）

- 指標: Spring Boot の `http.server.requests`。タグの `uri` は道の型（`/api/me/navigation`・`/api/me/table-access`）で、問い合わせの部分を含まない。バケットは 100・250・500・1000・2000・5000 ms（`application.yaml`）。
- トレース: `common/observability/UrlQueryStrippingObservationFilter.java` が、トレースの属性と指標のタグから URL の問い合わせの部分を取り除く。
- アクセスログ: Tomcat のアクセスログは設定していない。
- 誤りのログ: 既存の `GlobalExceptionHandler` は 4xx を WARN の1行（code・状態・例外の型）で出す。`ProblemDetail` の `instance` は問い合わせの部分を含まない道。
- TRACE: `TraceAspect` は web・service・domain の層の引数と戻り値を、TRACE のときだけ文字列で出す。

## 要件

| ID | 要件 | 測り方・確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR1.8 | 置き場の問い合わせの引数（スキーマ名・テーブル名）を、トレースの属性・指標のタグ・誤りのログ（403・400 の WARN）・誤りの応答に出さない（BR5.5、機能設計の再レビューの R-06）。メニューの API と置き場の問い合わせで、業務のログとアイコンの置き換えのログを出さない（BR4.2・BR7.3）。新しい指標・監査の種類は足さない | 結合テスト `NavigationQueryExposureIT`。目印の文字列を名前に持つテーブルで置き場の問い合わせ（200・403・400）を送り、テストの中で集めたスパンの属性・`http.server.requests` のタグ・アプリのログ（INFO 以上）・応答の本文に、目印の文字列が無いこと。監査の行の数が増えないこと | Code Generation（B7） |

## 受け入れた制約（要件の行にしない）

- **p95 の警報が無い**: メニューの API と置き場の問い合わせの p95 1 秒の目標を拾う警報は足さない（配備先が決まるまでの手元の監視の方針）。目標の確かめは Performance Validation の1回に限られ、その後の退行は手元の監視のダッシュボードで `http.server.requests` を見ないと気づけない（role のレビューの R-09 と同じ扱い）。
- **TRACE で名前が出る**: `TraceAspect` を TRACE にすると、置き場の問い合わせのスキーマ名・テーブル名が出る。業務の名前で、個人に関する値でも秘密でもないため伏せない（`security-requirements.md` の受け入れた制約と同じ）。
- **監査が無い**: 置き場の 403 は監査に残さない（機能設計 Q6 A）。業務データの拒否の残し方は J・K で決める。
