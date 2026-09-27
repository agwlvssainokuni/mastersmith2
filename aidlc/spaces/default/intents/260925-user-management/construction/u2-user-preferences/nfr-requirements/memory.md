<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-27T02:48:25Z — 質問を2問（パスワードの変更の応答時間・今のパスワードの誤りの制限）に絞った; 機能設計の6節がこの段へ渡した論点のうち、判断が分かれるのはこの2つだけだった。カバレッジの範囲・V7 の後方互換・2本目の接続の負荷の試験は team.md・要件 NFR5・NFR10 と project.md の決まりで決まっているため、設計の要点と「決まっていること」に書いた。
- 2026-09-27T02:48:25Z — パスワードの変更の成功は bcrypt を2回計算するため、要件 NFR6 の [assumption]（p95 1 秒）が同時 10 件では届かない見込みと読んだ; 照合1回 約 278 ms、ログインの同時 10 件の p95 が CPU 4 で 940 ms（colima-spec-up の実測）から、成功は約 1.9 秒と見積もった（未測定）。そのため Q1 で目標と測る負荷を尋ねた。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-27T03:08:50Z — NFR6 の [assumption] 1 秒と違い、パスワードの変更の成功を p95 2 秒にした（NFR6.3）; 依頼者の決定 Q1 A。要件の文書は書き換えず、performance-requirements.md の上流との差 P-D1 に記録した。
- 2026-09-27T03:08:50Z — まとめの確認の要約にあった監査の名前 PASSWORD_CHANGE_FAILED を使わず、承認済みの PASSWORD_CHANGED・FAILURE・CURRENT_PASSWORD_MISMATCH で書いた; その値は機能設計と契約 C8 に無いため、承認済みの設計を正とし、security-requirements.md の S-D1 に差を記録した（project.md の決まり）。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-27T03:08:50Z — 今のパスワードの誤りを制限せず、残る危険 R1 として受け入れた（NFR4.5、Q2 A）; 仕組みが増えない代わりに、漏れたトークンでロックを通らずに試せる。アクセストークンは 5 分だが、リフレッシュトークン（24 時間）が漏れていれば試せる時間はその有効期限までになることも R1 の根拠に書き添えた。
- 2026-09-27T03:08:50Z — 上流の NFR の枝番が無い行（パスワードの秘密・入力の上限・監査の失敗）を、NFR2（個人情報の扱いと同じ扱い）と NFR9（必須のテスト）の枝番に寄せた; traceability の検査を通すため、どの行にも上流の ID を付けた。NFR2.2 のパスワードの秘密は出典に project.md の Forbidden を並べた。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-27T02:48:25Z — V7 の displayName は既定の値なしの必須のため、1つ前の版のアプリが初期管理者を作る場面（利用者が1人もいないとき）だけ追記が失敗しうる; Flyway の既定が知らない新しい移行を無視すること、Hibernate の validate が余分な列を許すことは見込みで、まだ確かめていない。確かめは NFR 設計・基盤の設計に回した。
- 2026-09-27T02:48:25Z — Q2 で B か C を選ぶと、承認済みの BR4.5（誤りを数えない）と違う決まりになる; 機能設計の文書は書き換えず、成果物に上流との差として記録する必要がある（project.md の決まり）。B では V7 に回数の列が増える。
