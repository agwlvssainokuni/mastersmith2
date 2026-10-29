<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-30T00:00:00Z — 保存するエンティティが無いため、entities.md は persisted_entities を空にし、道具の中だけの一時の形（入力の木・置換の結果・残存の1件・キーの衝突）を参考として置いた（project.md のエンティティの決まり）。
- 2026-10-30T00:00:00Z — JWT の形の 3 区間目は 1 文字以上とし、署名の空の形（alg: none の「a.b.」）は対象にしない。アプリはその形を出さず、「2 区間は置換しない」と食い違わない読み方を選んだ。4 区間以上は先頭の 3 区間を替える（伏せすぎの側）。
- 2026-10-30T00:00:00Z — 残存の検査の「独立」は、置換の関数を呼ばず、木ではなく書き出す直前の文字列を端から調べる別の関数、と読んだ。形の定義（BR1.1）は共有する。
- 2026-10-30T00:00:00Z — AC4.1.9: 台本と既存の記録の鍵の名前（値は開かない）を読み、summary json に台本の値が入るのは setup_data だけ、リフレッシュトークンは vuState（setup の外）、Authorization ヘッダーは要求のたびに組み立てると確かめた。入らないため置換の対象は JWT だけのまま（M2: B）。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-30T00:00:00Z — 契約 C7 に、verify の evidence の検査の口（Q1: A で同じ残存の検査の関数を使うため）を足し、終わりのコードを「非 0」から 1〜5 に細分した。どちらも足す変更で、C7 の既存の呼び方は変えない（functional-spec.md 10節）。
- 2026-10-30T01:00:00Z — 追加の質問 F1: A を受け、上の階層の setup_data の下の文字列をすべて [REDACTED-SETUP] に替える BR1.7 と独立の確かめ BR2.4 を足し、性質 (3) を setup_data の外に読み替え (5) を足した。ストーリーの M2: B（キーによる伏せ字は行わない）との差として functional-spec.md 10節に記録し、7節の「2 場面を記録に置かない前提」をこの決定に置き換えた。
- 2026-10-29T23:25:02Z — 承認の場の Request Changes（依頼者の決定）で、R-01: 同じ道具にテキストの置換の口（--text。BR1.2 の置換の関数を共有）を足し BR1.8・BR3.4・BR5.4 を新設、W1 を W1-J・W1-T に分けて置いた後に evidence の検査と gitleaks dir を流す手順を置いた。R-02: verify に組み込む前に既存の evidence/ へ検査を一度流して 0 件を確かめ、見つかれば依頼者に諮る手順をコード生成に回した。R-03: 手で置いた json の setup_data の下の JWT の形でない秘密は verify で拾えない穴を 10節に記録し、evidence の検査は setup_data に広げない（過去の記録の [REDACTED-JWT] で落ちるため）。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-30T00:00:00Z — evidence の検査は拡張子で絞らず、先頭に NUL を含むものだけをバイナリとして飛ばす形にした。手で貼った値を広く拾える代わりに、バイナリに入った値は拾わない（飛ばした件数は表示する）。設計の時点で既存の evidence/ の2か所には JWT の形のファイルが 0 件と確かめた（件数だけ）。
- 2026-10-30T00:00:00Z — 出力の同じさは JSON として読んだ値で比べ、書式（字下げ・空白）は保たない。k6 の指標は浮動小数で、読み直しで値が変わらない代わりに、元のファイルと文字の並びは一致しない。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-30T00:00:00Z — AC4.1.9 の対象外だが、registrationVerify・registrationComplete の場面は setup() が招待のトークン（JWT の形でない）を setup_data.registrationTokens に入れ、その summary json を記録に置くと置換されずに残る。置換の対象を広げる・その場面の結果を記録に置かない・台本で setup_data から外す、のどれにするかを依頼者に諮る。決まるまでその 2 場面を記録に置かない前提で設計した（functional-spec.md 7節）。
