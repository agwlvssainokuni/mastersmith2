# Decisions — user-management

この Intent の Domain Design の決定の記録。部品の一覧は `components.md`。答えは `domain-design-questions.md`（Q1〜Q7）。上流は要件 `aidlc/spaces/default/intents/260925-user-management/inception/requirements-analysis/requirements.md`、ストーリー `aidlc/spaces/default/intents/260925-user-management/inception/user-stories/stories.md`、既存のコード `aidlc/spaces/default/codekb/mastersmith2/architecture.md`・`component-inventory.md`、チームの進め方 `aidlc/spaces/default/intents/260925-user-management/inception/practices-discovery/team-practices.md`。

| ADR | 題 | 状態 |
|---|---|---|
| ADR-001 | 招待を新しい部品 Invitation と別の表に持ち、登録の完了で利用者を作る | Accepted |
| ADR-002 | メールの描画と送信を共通の部品 Mail に置く | Accepted |
| ADR-003 | 氏名とプリファレンスを利用者の表の列として UserAccount が持つ | Accepted |
| ADR-004 | パスワードの変更を UserAccount に置く | Accepted |
| ADR-005 | ログインした利用者のエラーの言語は、画面が Accept-Language に利用者の言語を入れて決める | Accepted |
| ADR-006 | インスタンスの見た目の設定を公開の API で返す部品 InstanceAppearance を置く | Accepted |
| ADR-007 | 画面を3つの機能に分け、表示の設定を当てる仕組みを AppFrame に足す | Accepted |
| ADR-008 | 監査は既存の AuditLog に出来事の種類と対象の列を足す | Accepted |
| ADR-009 | 招待メールは招待の確定の後に、内部DB の接続を持たずに送る | Accepted |
| ADR-010 | 実現できるかの見通しと、後の段で確かめる条件 | Accepted |
| ADR-011 | 登録の完了の API は /api/auth/ の下に置かず、Invitation の差し込み口で公開の決まりを足す | Accepted |

## ADR-001: 招待を新しい部品 Invitation と別の表に持ち、登録の完了で利用者を作る

- **Context**: 招待された人は登録を終えるまでログインできてはならない（FR1.3）。今のログイン・トークンの更新・トークンの認証は利用者の状態を見ておらず、利用者の表の `password_hash` は必須（K-1・K-2）。要件の Q1 で「招待は別の表に持ち、完了で利用者を作る」と決まっている。
- **Decision**: 新しい部品 Invitation（パッケージ `invitation`）が招待の表と、招待・送り直し・取り消し・登録の完了を持つ。登録の完了で UserAccount の利用者の作成を呼ぶ。依存は invitation → user の一方向とし、user は invitation を知らない（Q1: A）。
- **Consequences**:
  - 良い点: ログインの3つの経路に手を入れずに、招待中の人を拒否できる（利用者の表にいないため）。利用者の表の必須の列を変えずに済む。
  - 悪い点: 同じメールアドレスの利用者と招待中の招待の重なりを、2つの表をまたいで確かめる必要がある（同時の操作の扱いは機能設計・NFR 設計）。
  - 中立: 新しいパッケージは自動でパッケージごとのカバレッジの下限の対象になる（`team.md`）。
- **Alternatives Rejected**:
  - UserAccount の中に招待を足し、利用者の表に状態を持つ（Q1: B）: 1つの一覧で見えるが、ログイン・更新・トークンの認証の3経路すべてで拒否の確かめが要り、漏れの危険がある。

## ADR-002: メールの描画と送信を共通の部品 Mail に置く

- **Context**: 招待メールは HTML で、java-mustache-processor のテンプレートを描き、件名は `<title>` から取る（FR2.1）。メール送信の仕組みは今のコードに無い（K-3）。外部の部品と SMTP は、エスケープ・ヘッダーの差し込み・秘密情報の決まり（`project.md` の Forbidden・Mandated）を守る必要がある。
- **Decision**: 共通の部品 Mail（パッケージ `mail`）がテンプレートの描画と SMTP の送信を持ち、Invitation が呼ぶ（Q2: A）。
- **Consequences**:
  - 良い点: java-mustache-processor と SMTP への依存、メールの安全の決まりを1か所で守り、テストできる。今後のメールも同じ仕組みを使える。
  - 悪い点: 今の利用者は Invitation だけで、共通化が早すぎる面がある。
  - 中立: テンプレートの置き場（言語ごとの持ち方）は機能設計で決める。
- **Alternatives Rejected**:
  - Invitation の中に閉じる（Q2: B）: 今は小さく済むが、今後のメールで作り直しになる。

## ADR-003: 氏名とプリファレンスを利用者の表の列として UserAccount が持つ

- **Context**: 利用者ごとに氏名・言語・テーマ・文字の大きさを内部DB に保存する（FR5.1、RD-5.9）。値は利用者と1対1で、利用者の読み取り（ログインの応答・トークンの認証）と一緒に使う。
- **Decision**: 利用者の表に列を足し、UserAccount が読み書きの操作を持つ（Q3: A）。既存の利用者には初期値を入れる（NFR10、要件の前提 A3）。
- **Consequences**:
  - 良い点: 利用者の読み取り1回で設定もそろう。部品が増えない。
  - 悪い点: 既存のパッケージ `user` に手を入れるため、そのパッケージを一覧から外してパッケージごとのカバレッジの下限の対象に戻す作業が要る（`team.md`、K-11）。
  - 中立: スキーマの変更は前進のみで、1つ前の版のアプリが動くよう列は既定の値を持つ（`team.md` の Deployment）。
- **Alternatives Rejected**:
  - 新しい部品と別の表（Q3: B）: 利用者の表を変えずに済むが、読み取りのたびに2つを合わせる手間が増え、1対1のため分ける利点が小さい。
  - 列は UserAccount、画面の API は別のパッケージ（Q3: C）: web の層だけを分けても、業務処理は user にあり、境界の検査（ArchUnit）の決まりが増える。

## ADR-004: パスワードの変更を UserAccount に置く

- **Context**: 今のパスワードの照合（`verifyPassword`）とパスワードの規則（`PasswordPolicy`）は UserAccount にある。パスワードを変えてもリフレッシュトークンは無効にしない（FR6.3）。
- **Decision**: UserAccount に「パスワードを変える」操作を足し、照合・規則・保存を1か所にする（Q4: A）。成功と今のパスワードの誤りの出来事を知らせる。
- **Consequences**:
  - 良い点: パスワードの扱いが1か所にまとまり、Authentication とのやり取りが増えない。
  - 悪い点: 今のパスワードの誤りが続いたときの制限（ロックとの関係）を設ける場合は、Authentication のロックの仕組みとの関係を決め直す必要がある（NFR 要件の未解決の点）。
- **Alternatives Rejected**:
  - Authentication に置く（Q4: B）: 認証の操作として並べられるが、照合と規則のために UserAccount を呼び、保存も UserAccount に頼むため、責務が2つに割れる。

## ADR-005: ログインした利用者のエラーの言語は、画面が Accept-Language に利用者の言語を入れて決める

- **Context**: ログインした利用者の要求へのエラーは利用者の言語で返す（FR7.2）。ログインの前の画面も、画面の言語にエラーをそろえる（ストーリーの CR1.2）。サーバーは今、要求の `Accept-Language` で言語を決める。
- **Decision**: ApiClient が、ログインの前は画面の言語、ログインの後は利用者の言語を `Accept-Language` に入れて送る。サーバーの言語の決め方は変えない（Q5: A）。
- **Consequences**:
  - 良い点: サーバーの変更が無い。ログインの前後で同じ仕組みで画面とエラーの言語がそろう。
  - 悪い点: 画面を通さずに API を呼ぶと利用者の言語にならない。画面の不具合で食い違うおそれがある（画面のテストで確かめる）。
- **Alternatives Rejected**:
  - サーバーが認証の主体の言語で決める（Q5: B）: 画面に頼らないが、ログインの前の要求には使えず、2つの決め方が並ぶ。

## ADR-006: インスタンスの見た目の設定を公開の API で返す部品 InstanceAppearance を置く

- **Context**: ブランドカラーとフォントファミリーは `application.yml` の固定の設定で、ログインの前後を問わず全画面に当てる（FR8）。今は設定の値を画面へ渡す道が無い（K-4）。
- **Decision**: 部品 InstanceAppearance が、ログインなしで読める API で見た目の設定を返し、AppFrame が起動時に読んで当てる（Q6: A）。
- **Consequences**:
  - 良い点: 配信の仕組みに手を入れずに済む。テストしやすい。
  - 悪い点: 起動時に1回の要求が増え、読み終わるまで前に保存された値で一瞬描かれるおそれがある（防ぎ方は機能設計）。公開の API が1つ増える（返すのは色とフォントの名前だけで、秘密は含まない）。
- **Alternatives Rejected**:
  - 配信する HTML に埋め込む（Q6: B）: 一瞬の描き直しは無いが、SPA の配信と見つからない URL のフォールバックに手を入れる必要があり、キャッシュの扱いも難しくなる。

## ADR-007: 画面を3つの機能に分け、表示の設定を当てる仕組みを AppFrame に足す

- **Context**: 画面は管理者（招待の管理）・ログインなし（登録の完了）・ログインした利用者（プリファレンスとパスワードの変更）でアクセスの区分と置き場が違う（FR10）。表示の設定（テーマ system・文字の大きさ・言語・見た目）はすべての画面に当たる。make-you-chic-ui は変えられない（`project.md` の Forbidden）。
- **Decision**: 画面の機能を `features/invitation`・`features/registration`・`features/preferences` に分け、表示の設定を当てる仕組み（テーマ system の解決、ブラウザへの保存、言語の切り替えの口、ユーザーメニューの氏名）は AppFrame に足す（Q7: A）。
- **Consequences**:
  - 良い点: 既存の機能の登録の仕組み（アクセスの区分ごと）をそのまま使える。表示の設定がどの画面でも同じに当たる。
  - 悪い点: AppFrame（骨組み）に手を入れるため、既存の画面すべてに影響しうる（画面のテストで確かめる）。
- **Alternatives Rejected**:
  - 1つの機能にまとめる（Q7: B）: アクセスの区分が混ざり、表示の設定が機能の中に閉じて他の画面に当たらない。

## ADR-008: 監査は既存の AuditLog に出来事の種類と対象の列を足す

- **Context**: 招待・送り直し・取り消し・登録の完了と失敗・パスワードの変更を監査に残す（FR9.1）。今の監査の表に対象を表す列が無い（K-7）。監査の共通の仕組みは後続の Intent で検討する（`project.md` の DECIDED）。
- **Decision**: 既存の AuditLog に出来事の種類を足し、対象（利用者・招待）と結果の列を Flyway の移行で足す。既存の決まり（確定の後に記録、記録の失敗で元の操作を失敗させない）に従う。
- **Consequences**:
  - 良い点: 既存の監査の決まりとテスト（`AuditSecretLeakIT` など）をそのまま使える。
  - 悪い点: 出来事の種類ごとに AuditLog の中を変える形が続く。確定の後に2本目の接続を借りる経路が増える（NFR5 の負荷の試験で確かめる）。
- **Alternatives Rejected**:
  - 共通の監査の仕組みを作る: DECIDED により後続の Intent で検討する。

## ADR-009: 招待メールは招待の確定の後に、内部DB の接続を持たずに送る

- **Context**: 送信を待つ間に内部DB の接続を持ち続けない（FR2.3、NFR5、K-10）。送信に失敗しても招待は残し、送信の結果を記録して送り直せるようにする（FR2.4）。
- **Decision**: 招待（送り直し）を短いトランザクションで確定し、その後トランザクションの外で Mail に送信を頼み、結果を別の短いトランザクションで招待に記録する。要求はその結果を待って応答する（同じ要求の中で行う）。
- **Consequences**:
  - 良い点: 送信の待ちの間に接続を持たない。送信の失敗が招待を取り消さない。
  - 悪い点: 送信の途中でアプリが止まると送信の結果が残らない（一覧の表し方は機能設計の論点）。応答時間に SMTP の時間が乗る（NFR6 の時間切れの値で上限を決める）。
- **Alternatives Rejected**:
  - トランザクションの中で送る: 簡単だが、送信の待ちの間に接続を持ち続け、プールが尽きる危険がある（`project.md` の学び）。
  - 別のスレッド・待ち行列で後から送る: 応答は速いが、トレースID・監査の決まり（確定の後に同じスレッドで記録）と合わず、送信の結果を画面にすぐ返せない（要件の AC1.1.5）。

## ADR-010: 実現できるかの見通しと、後の段で確かめる条件

- **Context**: この範囲（classic）には実現可能性の評価（Feasibility）の段が無い。この Intent には、前例の無い取り込みと仕組みがある。
- **Decision**: 次の見通しで進め、確かめる条件と持ち主の段を決める。
  - java-mustache-processor をサブモジュールと composite build で取り込めること。取得元を Maven Central だけにする決まりを崩さず、推移依存も lockfile と脆弱性検査の対象に含められることを、最初に取り込む Bolt で確かめる（`team.md`）。満たせなければ部品を Maven Central に公開して使う形に切り替える。
  - JVM の中で動くテスト用の SMTP の受け手を使えること。採用の前にライセンスを確かめ、Apache License 2.0 と違えば理由を記録する（`team.md`）。持ち主は NFR 要件・コード生成。
  - 同じメールアドレスの招待中を1件に限ること。組み込みの H2 の索引で作れるかは NFR 設計で確かめ、作れなければ業務処理の側で行ロックなどで確かめる。
  - 1インスタンスだけで動く前提（組み込みの H2）は変えない。
- **Consequences**:
  - 良い点: 不確かな点と切り替え先が決まっている。
  - 悪い点: 取り込みの仕組みは最初の Bolt まで確かめられない。
- **Alternatives Rejected**:
  - Feasibility の段を足して先に確かめる: 確かめたいことはコードを書かないと分からない点が多く、段を足しても最初の Bolt の確かめを置き換えられない。

## ADR-011: 登録の完了の API は /api/auth/ の下に置かず、Invitation の差し込み口で公開の決まりを足す

- **Context**: `/api/**` は既定でログイン必須。`/api/auth/` の下ではアクセストークンを読まない（K-6）。登録の完了はログインなしで呼ぶ必要がある。
- **Decision**: 登録の完了（リンクの確かめと完了）の API は Invitation の置き場に置き、Invitation の差し込み口（SecurityRuleContributor）で、その道だけを公開にする決まりを足す。招待の管理の API は `/api/admin/` の下に置く。プリファレンスとパスワードの変更は `/api/` の下の既定のログイン必須に乗せる。
- **Consequences**:
  - 良い点: 公開にする道を最小にでき、既存の決まりを変えない。
  - 悪い点: 公開の道が1つ増えるため、回数の制限などの守りを NFR 要件で考える必要がある。
- **Alternatives Rejected**:
  - `/api/auth/` の下に置く: 公開にはなるが、認証の API と同じ置き場に招待の機能が混ざり、アクセストークンを読まない決まりのためログインした利用者の API をそこに置けない制約が広がる。
