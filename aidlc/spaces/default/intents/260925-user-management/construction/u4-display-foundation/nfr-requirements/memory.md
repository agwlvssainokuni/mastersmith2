<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-27T03:54:10Z — 質問は最初の描画の時間の目標の置き方（Q1）と NFR7 の確かめ方（Q2）の2問にし、ほかは NFR の要点（案）14 件として要約で確かめる形にした; ブラウザの保存・受け渡し・公開の API・CSP・フォントの採用と ADR・make-you-chic-ui の固定先の更新は承認済みの機能設計と team.md・project.md で決まっていると読んだ。NFR7 の確かめ方はストーリーの「後の段に回す点」でこの段が持ち主とされており、新しく決める論点とした。
- 2026-09-27T03:54:10Z — 最初の描画の待ちは、並べ読み（W2）のため2つの API の遅いほう（更新 p95 1 秒）にほぼ等しいと読んだ; API の時間は U2 の NFR6.5・U8 の NFR6.1 で押さえ済みで、画面の側の時間を置くかだけを Q1 で問うた。前の Intent の DSL の管理画面の前例（E2E で測って記録し、関門にしない）を推奨の根拠にした。
- 2026-09-27T03:54:10Z — フォントの重さは dist の実測（Noto Sans JP は japanese のサブセットが太さごとに woff2 約 1.0MB、合計 約 9.8MB）と CacheControlFilter の immutable の扱いから、要点として書いた; font-display: swap のため描画は止まらず、2回目以降は読み直さない。dist と WAR の大きさには上限を置かず、コード生成で増えを測って記録する案にした。
- 2026-09-27T04:36:02Z — 画面の側のセキュリティ（公開の API・要求のヘッダー・応答の値・CSP）と依存・テストを NFR9 の枝番（NFR9.1〜NFR9.11）に寄せた; 要件の NFR1〜NFR11 に当たる ID が無く、同じ Intent の U8 が当たる ID の無い要件を NFR9 に寄せた前例に合わせた。NFR4 は要約の確認どおり N/A とし、画面の側の扱いは NFR9.1 に置いた。
- 2026-09-27T04:36:02Z — アクセシビリティ（NFR7）・多言語（NFR8）・テスト（NFR9.8〜NFR9.11）は ui の単位で作る成果物に専用の文書が無いため tech-stack-decisions.md の3〜5節に置き、各文書の冒頭の表で置き場を示した; security-requirements.md はレビューの対象のため、セキュリティの要件だけに絞った。
- 2026-09-27T04:36:02Z — 依存の版は npm の公開の登録簿を読み取りだけで確かめた（@fontsource/noto-serif-jp 5.3.0 OFL-1.1、axe-core 4.13.0 MPL-2.0、どちらも推移依存なし）; axe-core は今の lockfile の vitest-axe の推移依存と同じ版で、明示にしても版は1つにそろう見込み。make-you-chic-ui の固定先の前後のハッシュ（edb1f94…→735ef04…、8 コミット、peerDependencies は同じ）を tech-stack-decisions.md に記録した。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-27T04:36:02Z — NFR7.3 の横のはみ出しの確かめは既定の表示の幅（Desktop Chrome）だけにし、interaction-spec.md の狭い幅（768px 未満）は検査に入れなかった; Q2 B の答えは組の数だけを決めており、幅を足すと組が倍になるため。上流との差に書き、B5 の計画で依頼者に確かめることにした。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-27T04:36:02Z — Playwright 用の包み（@axe-core/playwright）ではなく axe-core だけを明示で足す形にした; 依存を1つに抑え、vitest-axe と版をそろえられる代わりに、テストのページへの読み込みを自分で書く。アプリの CSP（script-src 'self'）は緩めず、読み込みの扱いは検査のブラウザのコンテキストだけで行う（NFR9.4）。具体の方法はコード生成で決める。
- 2026-09-27T04:36:02Z — NFR6.1 の 2 秒は 5 回すべてが収まることを目標にし、統合の関門にはしない形にした; ブラウザと PC の状態で揺れるため、前の Intent の DSL の管理画面と同じく測って記録する。超えたときは目標を緩めず、どこが遅いかを切り分けて依頼者に相談する。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-27T03:54:10Z — Q2 B（Playwright で axe-core を流す検査）は team.md の「E2E は Intent ごとに代表の流れを1本まで」と食い違いうる; 流れではない検査として本数に数えない読み方を選択肢に書いたが、依頼者が B を選んだときは、この読み方でよいかを追加の質問で確かめる必要がある。axe-core（MPL-2.0）は今も vitest-axe の推移依存として入っているが、採用の理由の記録は見当たらない。
- 2026-09-27T04:36:02Z — 上の点は依頼者が Q2 B とともに受け入れた（流れではない検査として本数に数えない）; tech-stack-decisions.md の NFR9.11 と上流との差に記録し、axe-core の採用の理由は同じ文書の 2.1 に ADR 形式で残した。Noto Serif JP のフォントのファイルの大きさは未測定のままで、NFR6.5 でコード生成が測る。
