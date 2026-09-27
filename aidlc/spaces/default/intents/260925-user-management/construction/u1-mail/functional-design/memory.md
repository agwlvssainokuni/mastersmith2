<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-25T15:58:49Z — Q1 C の templateId の文字の決まりは選択肢 A のもの（英小文字・数字・ハイフン）を引き継いだ; C はファイル名を下線で区切るため、templateId に下線を許さないと区切りが一意になる。置き場に名前の合わないファイルや一覧に無い templateId のファイルがあるときも、「壊れている」と同じく起動を止める扱いにした（BR2.1・BR2.3）。
- 2026-09-25T15:58:49Z — 契約 C1 の宛先の型「EmailAddress」は、U1 の中に同じ決まりを持つ形で読んだ; 既存の EmailAddress は user.domain の静的な決まりで、部品 Mail は depends_on が空のため依存させない。正規化は呼び出し元が行い、正規化済みでない宛先は INVALID_INPUT とした（BR3.1、functional-spec.md 10節）。
- 2026-09-25T15:58:49Z — isConfigured の意味を契約の「接続先が設定されているか」から広げた; Q2 B（差出人も必須）と Q3 A（資格情報と暗号化 NONE は設定がないと同じ）の答えの帰結で、真偽を返す形は変わらず契約の持ち主は U1 のため、functional-spec.md 10節に差として書いた。
- 2026-09-25T15:58:49Z — 答えで決まっていない細部を設計で決めた; ポートを省いたときは暗号化の方式の標準の番号、STARTTLS を受け付けない受け手は CONNECTION_FAILED、認証の拒否は REJECTED、差し込む値の null は INVALID_INPUT、SMTP の項目が1つも無いときは WARN を出さない（前の Intent の対象DB の設定と同じ扱い）。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-25T15:58:49Z — テンプレートの中の差し込みの名前と一覧の一致は、起動時ではなくテストで確かめる形にした（Q4 A の答えどおり）; 起動の処理を小さく保てる代わりに、テストを通さずに動かすと食い違いが送信の時まで見つからない（その場合も送信の依頼の名前の確かめで INVALID_INPUT になり、リンクの無いメールは送られない）。
- 2026-09-25T15:58:49Z — 本文を送った後の応答の待ちの時間切れも TIMEOUT とし、自動の再試行はしない; 受け手に届いている場合があり、管理者の送り直しで同じ招待のメールが重なって届くことはありうる。重複を防ぐ仕組みは入れていない（functional-spec.md 8節）。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-25T15:58:49Z — 差し込む値の空の文字列は拒否していない（BR3.3 は null だけを拒否する）; 空の registrationUrl を防ぐのは U3 の組み立ての決まりに任せた。U1 の側でも空を拒否するかは、承認の場で必要なら確かめる。
