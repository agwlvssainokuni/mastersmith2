<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

- 2026-09-27T06:22:08Z — library の単位のため、性能・信頼性・観測に当たる設計（NFR5・NFR6・送信の観測）を logical-components.md の節に置く案にした; NFR 要件の段が tech-stack-decisions.md の2節に置いた形にならう。成果物は security-design.md・logical-components.md・traceability.json の3つ。
- 2026-09-27T06:22:08Z — 質問は、承認済みの NFR 要件から作りが1つに決まらない2点（描画と送信の内部をメソッドの追跡から外す作り、送信の観測の単位）に絞った; 起動時の点検の場所・テンプレートの準備・失敗の分類・想定外の例外の包み方は、targetdb の前例と project.md の Forbidden で一通りに決まるため要点にした。
- 2026-09-27T09:49:19Z — 失敗の分類は Jakarta Mail の API の型（SendFailedException・AuthenticationFailedException）と Spring の例外の型だけで行う設計にした; Angus Mail は実行時だけの依存で、SMTP の例外の型をコンパイルの時に使えないため。受け手の拒否の SMTP の例外は SendFailedException を継ぐので REJECTED に入る。質問ファイルの要点 8 の書き方との差として security-design.md の10節に書いた。
- 2026-09-27T09:49:19Z — templateId と language は、一覧にある値と ja・en だけをログと Observation のタグにそのまま出し、それ以外は unknown と出す設計にした; 呼び出し元が渡す未知の文字列（改行を含みうる）をログ・タグに写さず、指標のタグの種類が増え続けるのも防ぐため。BR6.2・BR6.3 との差として記録した。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-27T06:22:08Z — BR2.4 の検査（三重の波かっこ・{{& を書かない）に、部分テンプレート {{> と区切りの変更 {{= を書かないことを足す案にした; 区切りを変えると三重の波かっこの検査をすり抜けうり、Mustache.compile(String) は解決できない部分テンプレートを黙って空にするため。承認済みの BR2.4 の趣旨を強める追加として、成果物の上流との差に書く。
- 2026-09-27T09:49:19Z — NFR11.1 の確かめ方（招待のメールが受け手の画面に出ることを B1 の完了の条件にする）を、B1 では profile の起動と isConfigured が真になることまでとし、画面での確かめを B3 に引き継ぐ設計にした; B1 の時点では本番の一覧が空で招待のテンプレートが無いため。Delivery Planning の B1 の完了の条件（profile で起動できる）とは食い違わず、NFR11.1 との差として logical-components.md の11節に書き、B1 の計画で依頼者に確かめる。
- 2026-09-27T09:49:19Z — Observation の属性の確かめに micrometer-observation-test（TestObservationRegistry）を足さず、ObservationRegistry.create() に文脈を集める小さな受け手を付ける設計にした; 今の lockfile に無い依存を増やさずに同じことを確かめられるため。質問ファイルの Q2 B の書き方との差として記録した。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

- 2026-09-27T06:22:08Z — 想定外の例外は、固定の文言と原因の連なりの型の名前だけを持つ U1 の例外に包み、原因の例外そのものを付けない案にした; 既存の @RestControllerAdvice が 5xx を ERROR とスタックトレースで出すため、部品の例外のメッセージ（宛先や SMTP の応答を含みうる）がログに残るのを防ぐ。代わりに調べるときは型の名前・時刻・トレースIDで絞ることになる。
- 2026-09-27T06:22:08Z — 差し込みの名前と一覧の一致は起動時ではなく BR2.6 のとおりテストで確かめる案にした; java-mustache-processor の公開の API は Mustache.compile と Template.render だけで構文木を出さず、起動時に名前を数えるには部品の内部に頼ることになるため。
- 2026-09-27T09:49:19Z — Observation は send の全体（NOT_CONFIGURED と確かめの失敗を含む）を覆い、FAILED は error にせず outcome と failure.kind のタグで表し、error には包んだ MailUnexpectedException だけを渡す設計にした; トレースの例外のイベントに部品の文言（宛先・SMTP の応答）を載せないため。代わりに、FAILED の送信はトレースの上ではエラーの印が付かず、タグで絞る必要がある。
- 2026-09-27T09:49:19Z — 本番の MailTemplateRegistry は一覧と置き場を受け取って作り、テストは別の置き場 mail/test-templates とテスト用の一覧で作る設計にした; B1 の時点で本番の一覧が空でも送信の結合テストを書け、テスト用のテンプレートが本番の数え上げに混ざらないため。Spring を起動する結合テストはテストの設定で Bean を置き換える必要がある。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-27T06:22:08Z — TraceAspect は web・service・domain・repository の Bean の引数と戻り値を toString で TRACE に出すため、描画・組み立てを別の Bean に分けると差し込む値の Map や描いた本文（招待の URL）が出うる; 守り方を Q1 にした（推奨は用途名の下位パッケージ mail.template・mail.transport に置く A。前例は dsl/parse・dslmanage/generate）。
- 2026-09-27T06:22:08Z — 今のアプリには自前の span・指標が無く、BR6.3 は属性を付けるかを決めていない; 送信の時間を分けて見るかを Q2 にした（推奨は Observation を1つ作りタグを templateId・language・outcome・failureKind に絞る B）。
- 2026-09-27T06:22:08Z — 承認の場の R-01（starttls.enable だけの設定の平文の危険を README に書くことを B1 の完了の条件に）と R-02（環境変数から点を含む鍵への結び付きと composite build の5条件を B1 で確かめ、崩れたら ADR-010 の切り替え）を、要点 12 と logical-components.md の「B1 で確かめること」に引き継ぐ案にした; 環境変数の結び付きが成り立たないときは既定の 3 秒で動くことを確かめ、変え方を B1 で依頼者に諮る。
- 2026-09-27T09:49:19Z — 実行可能 WAR の中で classpath の mail/templates/*.html を数え上げられるかは確かめていない; B1 で手元の WAR の起動で確かめ、できなければ一覧からファイル名を組み立てて直接読む形に替える（logical-components.md の10節）。
