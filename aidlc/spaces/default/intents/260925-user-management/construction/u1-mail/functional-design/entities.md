# Entities — U1 メールの描画と送信（u1-mail）

U1 は内部DB に何も保存しない（`aidlc/spaces/default/intents/260925-user-management/inception/domain-design/components.md` の Mail は保存するデータを持たず、契約 C1 は「内部DB に触れない」）。そのため、保存するエンティティは無い。

下の正本には、U1 が独自に持つ **保存しない値の型** だけを書く。どれも送信の依頼ごと、または起動のときに作る変更できない値である。SMTP の接続先・ポート・暗号化・資格情報・差出人・時間切れなどの設定値は、フレームワークの設定の仕組みの値なのでエンティティにせず、`rules.md` の決まり（BR1.x）として書く（`aidlc/spaces/default/memory/project.md` の Code Style）。

```yaml
entities:
  - name: MailTemplateDefinition
    description: >
      テンプレートの一覧（カタログ）の1行。templateId と、そのテンプレートが受け取る差し込みの名前の一覧を持つ。
      U1 が一覧を持ち、テンプレートを足す単位（今回は U3 の invitation）が行を足す（Q4: A）。保存しない値の型
    persisted: false
    attributes:
      - { name: templateId, type: string, required: true, unique: "一覧の中で", constraints: "英小文字で始まり、英小文字・数字・ハイフンだけ（下線は使わない。ファイル名の区切りに使うため、BR2.1）" }
      - { name: variableNames, type: set<string>, required: true, constraints: "差し込みの名前の集合。0件もありうる。名前は英字で始まり英数字だけ（例: registrationUrl・validityHours）" }
    constraints:
      - "templateId ごとに、ja と en の両方の MailTemplate がある（無ければ起動を止める、BR2.3）"
      - "variableNames は、ja・en のどちらのテンプレートの中の差し込みの名前とも一致する（テストで確かめる、BR2.6）"
    relationships:
      - "MailTemplateDefinition 1 → 2 MailTemplate（ja と en を1つずつ持つ）"

  - name: MailTemplate
    description: >
      描く前のテンプレート1つ。templateId と language の組で1つに決まる。置き場のファイル
      mail/templates/<templateId>_<language>.html から起動のときに読み、描ける形に準備して持つ（Q1: C）。保存しない値の型
    persisted: false
    attributes:
      - { name: templateId, type: string, required: true, references: MailTemplateDefinition.templateId }
      - { name: language, type: enum, required: true, allowed: [ja, en] }
      - { name: source, type: text, required: true, constraints: "HTML の本文のテンプレート。エスケープされる差し込み（二重の波かっこ）だけを使う。ライセンスヘッダーは Mustache のコメントで書く（BR2.4・BR2.5）" }
    constraints:
      - "(templateId, language) は重ならない"
      - "動いている間は変わらない（起動のときに読み、差し替えない）"
    relationships:
      - "MailTemplate N → 1 MailTemplateDefinition（属する）"

  - name: MailRequest
    description: 送信の依頼（契約 C1 の MailRequest）。呼び出し元（U3）が作って渡す。保存しない値の型
    persisted: false
    attributes:
      - { name: templateId, type: string, required: true, constraints: "一覧に無ければ TEMPLATE_ERROR（BR3.4）" }
      - { name: language, type: enum, required: true, allowed: [ja, en], constraints: "ほかの値は INVALID_INPUT（BR3.4）" }
      - { name: to, type: string, required: true, constraints: "正規化済みのメールアドレス（前後の空白が無く小文字、254 文字まで、形式に合う）。改行を含まない（BR3.1・BR3.2）" }
      - { name: variables, type: map<string, string>, required: true, constraints: "名前の集合が一覧の variableNames と一致する（欠け・余分とも不可）。値は null でなく、空の文字列でも前後の空白を除くと0文字になる文字列（空白だけ）でもなく、改行（CR・LF）を含まない（BR3.2・BR3.3）。値は変えずにそのまま描く。invitation では registrationUrl と validityHours の2つ（BR2.2）" }
    constraints:
      - "宛先は1人だけ"
    relationships:
      - "MailRequest N → 1 MailTemplateDefinition（templateId で指す）"
      - "MailRequest 1 → 0..1 RenderedMail（確かめを通ったときだけ描く）"
      - "MailRequest 1 → 1 MailSendResult（必ず1つの結果になる）"

  - name: RenderedMail
    description: 描いた結果。送信のために U1 の中だけで作り、呼び出し元には返さない。保存しない値の型
    persisted: false
    attributes:
      - { name: language, type: enum, required: true, allowed: [ja, en], constraints: "依頼の language と同じ。本文の html 要素の lang 属性とも一致する（BR4.3）" }
      - { name: subject, type: string, required: true, constraints: "本文の title 要素の文面から作る。空でなく、改行を含まない（BR4.2）" }
      - { name: htmlBody, type: text, required: true, constraints: "描いた HTML の本文。ライセンスヘッダーの文面と、描き残しの二重の波かっこを含まない（BR2.5・BR2.6）" }

  - name: MailSendResult
    description: 送信の結果（契約 C1 の MailSendResult）。成功か、失敗とその種類。保存しない値の型（記録は呼び出し元の U3 が行う）
    persisted: false
    attributes:
      - { name: outcome, type: enum, required: true, allowed: [SENT, FAILED] }
      - { name: failureKind, type: enum, required: "outcome が FAILED のときだけ", allowed: [NOT_CONFIGURED, CONNECTION_FAILED, TIMEOUT, REJECTED, INVALID_INPUT, TEMPLATE_ERROR] }
    constraints:
      - "宛先のメールアドレス・SMTP の応答の文面・例外のメッセージ・資格情報・差し込んだ値・件名を持たない（BR6.1）"
```

## まとめ

| 値の型 | 役割 | 作る時点 | 保存 |
|---|---|---|---|
| MailTemplateDefinition | テンプレートの一覧の1行（templateId と差し込みの名前の一覧） | 起動のとき（U1 が持つ一覧） | しない |
| MailTemplate | 描く前のテンプレート（templateId と言語の組） | 起動のとき（置き場のファイルから） | しない |
| MailRequest | 送信の依頼 | 送信のたび（U3 が作る） | しない |
| RenderedMail | 描いた件名と本文 | 送信のたび（U1 の中だけ） | しない |
| MailSendResult | 送信の結果（SENT、または FAILED と失敗の種類） | 送信のたび | しない（U3 が招待に記録する） |

どれも変更できない値で、呼び出し元に返す MailSendResult は秘密と個人情報を持たない。設定値（SMTP の接続先・暗号化・資格情報・差出人・時間切れ）は `rules.md` の BR1.x に書く。
