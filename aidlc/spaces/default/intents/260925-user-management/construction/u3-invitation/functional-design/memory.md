<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

- 2026-09-27T00:02:21Z — ベース URL が無いときは WARN を出さず、不正な値のときだけ WARN を出す; Q3 A は「合わなければ WARN を1件」とだけ述べ、値が無いときの扱いは決めていない。U1 の BR1.2（SMTP の項目が1つも無ければ警告しない）にそろえ、招待を使わない使い方を許した（rules.md の BR1.3）。
- 2026-09-27T00:02:21Z — 招待した管理者の氏名が得られないときのメールアドレスは、U2 の利用者の要約（BR5.5）から得ることにした; 契約 C2 には利用者 ID からメールアドレスを引く操作が無く、U2 では氏名が必須のため、氏名が無いのは利用者の行そのものが無いときだけになる。その場合は空の文字列を返す（今は利用者を消す操作が無く起きない）とした（BR5.3）。
- 2026-09-27T00:02:21Z — 招待のメールアドレスに CR・LF があれば、正規化の前の生の入力で拒否する; 既存の EmailAddress.normalize は trim で前後の改行を除くため、AC1.1.3（改行を含むメールアドレスは拒否）を満たすには正規化より先に確かめる必要がある（BR1.1）。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-27T00:02:21Z — 設計の要点 1 の completedAt と「終わった日時」を、1つの endedAt にまとめた; 完了・取り消し・置き換えの時点を1つの属性にまとめると、保存期間（Q1 C）の起点が1つで済む。完了の時点は state が COMPLETED の行の endedAt とし、completedUserId は残した。差は functional-spec.md の6節に書いた。
- 2026-09-27T00:02:21Z — 状態 REPLACED と、送信の結果の内部の値 PENDING を足したが、契約の文書は書き換えなかった; Q4 A で C8 に足す EMAIL_ALREADY_REGISTERED と、Q2 C の内部の PENDING（API では FAILED として返す）を、依頼のとおり functional-spec.md の6節「上流との差」に記録した。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-27T00:02:21Z — 終わった招待の行では tokenHash を消さずに残す; 使用済み・取り消し済み・置き換え済みのリンクの監査の理由を分けられる代わりに、保存の日数（既定 90 日）のあいだハッシュが内部DB に残る。ハッシュからトークンは戻せず、行は定期の削除で消える（BR7.6・BR11.1）。
- 2026-09-27T00:02:21Z — AC2.2.12・AC3.2.1・AC3.2.18 は、サーバー側が支える部分もあるが Deferred（画面の単位）にした; どの AC も、確かめる中心が Toast・画面の表示・ブラウザへの保存であるため。サーバー側の支えは、対象の BR を traceability.json の理由の文に書いた。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-27T00:02:21Z — 有効期限の長さの設定を 24 時間から変えると、テンプレートの固定の文「24 時間有効です」と食い違う; 今は BR1.6 で「長さを変えたらテンプレートも直す」としたが、長さを差し込む値にするかは U1 のテンプレートの一覧の差し込みの名前（registrationUrl だけ）に関わるため、この段では決めなかった。
