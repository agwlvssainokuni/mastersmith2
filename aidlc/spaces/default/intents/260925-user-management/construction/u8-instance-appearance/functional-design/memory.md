<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-25T15:58:57Z — 前後の空白を除くと空になる値は「無い」と同じ扱い（既定・警告なし）とした; Q2 A は空白を除いて判定すると決めたが、空白だけの値の扱いは決めていなかった。空の文字列を既定とする application.yaml の書き方と揃え、BR1.3 に明記した。
- 2026-09-27T00:00:00Z — 承認の場の Request Changes の R-02 で、GET 以外のメソッドの応答を既存の扱い（401・405）に任せると明記した; config/SecurityConfig と access/web/AdminApiDefaultAccess で /api/** がログイン必須、auth/web/TokenAuthenticationEntryPoint が 401 / AUTHENTICATION_REQUIRED、common/error/web/GlobalExceptionHandler が 405 / METHOD_NOT_ALLOWED と Allow を返すことをコードで確かめた。U8 は新しい応答・code を作らず、W3.3・8節・BR3.1・BR3.2 に書き、functional-spec.md の9節に直しの一覧を置いた。
- 2026-09-25T15:58:57Z — traceability.json の upstream_ids を FR8.1・FR8.2 とした; CR2 は受け入れ基準の番号を持たないため、依頼の指示どおり要件の枝番を使った。FR8.2 の Given/When/Then と CR2 の確かめ方（サーバー側）は functional-spec.md の7節で BR に対応づけた。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-25T15:58:57Z — 警告のログに設定された値そのものを出さない形（BR2.1）にした; 秘密ではない値だが、前の Intent の対象DB の設定の警告と揃え、項目の名前・既定・許される値だけで原因が分かる形を選んだ。代わりに、どの値を書いたかは設定を見て確かめる必要がある。
- 2026-09-25T15:58:57Z — Q1 A の決まり（使えないトークンの 401 を受け入れる）は FR8 の受け入れの条件ではないため、BR3.3 として書き traceability の reverse に N/A で理由を付けた; 画面の側（U4）の ApiClient の変更は U4 の機能設計で受け持つ。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
