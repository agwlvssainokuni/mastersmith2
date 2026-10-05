# セキュリティの要件 — U7 app-frame-ui

## 出典

- `functional-spec.md`（この単位の承認済みの機能設計。画面の決まり D1〜D30、流れ W1.1〜W9.1、4節の状態、6節のテストの方針、7節の上流との差、11節の承認の場の直し）と `frontend-components.md`。
- `rules.md` は ui の単位には無い（機能設計の段の `produces_kinds` で ui は `rules.md` を作らない）。段の定義が必須とする `rules` の代わりに、`functional-spec.md` の D1〜D30 を業務の決まりの入力として使う。無い `rules.md` の中身は作らない。
- `requirements.md`（NFR1〜NFR6、FR5・FR6・FR9・FR10）。
- `contract-summary.md`（C2・C8・C9 と共通の決まり）と、先の単位の確定の形:
  - role の機能設計 2.11: 自分の権限の道は `/api/me/permissions/schemas`・`tables?schema=…`・`columns?schema=…&table=…`。
  - navigation の機能設計 8節: `{ja, en}`・`id`・`emptyReason`・置き場の問い合わせの引数。
- `technology-stack.md`（コード知識ベース `aidlc/spaces/default/codekb/mastersmith2/`）。
- この段の答え: `nfr-requirements-questions.md` の Q1〜Q3 はすべて A。まとめの確認は Looks correct で、「決まっていること」「この段で決める要点」を含む。
- 決まりの層:
  - `team.md`: N 階層のメニューの画面のテスト、役割・権限の画面の出し分け、画面のテスト、E2E、フロントエンドの依存の脆弱性、make-you-chic-ui の固定先。
  - `project.md`: Forbidden・Mandated、実際のブラウザの axe と Playwright の報告の学び。
- 先に確定した単位の NFR 要件と U7 への引き継ぎ: navigation・role・cross-cutting（NFR4.5）・role-admin-ui（形と、そのレビューの R-01〜R-07）。

## ID の振り方（4つの成果物に共通）

- 上流の枝番（`requirements.md` の NFR1.1〜NFR6.4）と、**同じ番号は同じ意味** でだけ使う。
  - 上流の要件をこの単位に当てはめたものは、上流と同じ ID にする。
  - この単位で新しく足す要件は、上流の最後の枝番の次から振る（NFR1.7〜・NFR2.5〜・NFR4.4〜・NFR6.5〜）。
  - 番号は単位ごとで、ほかの単位の同じ番号とは別の要件である。
- 1つの要件は1つの成果物にだけ書く。下の表は4つの成果物の全体を示す。
- テストも持ち主の段も無いものは要件の行にせず、`tech-stack-decisions.md` の「受け入れた制約」の節に書く。

| 上流の枝番 | この単位での扱い | この単位の ID と置き場 |
|---|---|---|
| NFR1.1 API ごとのサーバー側の判定 | 当たる（メニュー・画面を隠すことを代わりにしない） | NFR1.1・NFR1.10（security） |
| NFR1.2 401・403・200 のテスト | 部分（画面の権限なしの表示。サーバーの表のテストは U3〜U5） | NFR1.1（security） |
| NFR1.3 API の分類の網羅 | 当たらない（U7 は API を持たない。網羅は U1、印は U4・U5） | — |
| NFR1.4 昇格・一括代入・IDOR | 部分（画面が送る項目を決める。判定は U4・U5） | NFR1.4・NFR1.8（security） |
| NFR1.5 権限の YAML の守り | 部分（YAML は扱わない。DSL から来る値を信頼できない入力として描く点だけ） | NFR1.7（security） |
| NFR1.6 個人に関する値を出さない | 当たる（画面の側: コンソール・ブラウザの保存・道・E2E の報告） | NFR1.6・NFR1.9（security） |
| NFR2.1 規模の前提 | 部分（測りに使う悪い側の木。規模の確定は U4・U5） | NFR2.12（performance） |
| NFR2.2 メニュー・作業ロールの時間 | 部分（API の時間は U4・U5 の目標のまま。画面の側の時間を足す） | NFR2.2・NFR2.5〜NFR2.11（performance） |
| NFR2.3 要求ごとの権限の読み出しの重さ | 当たらない（サーバーの読み出し。U4・U5） | — |
| NFR2.4 import の時間 | 当たらない（U4・U6） | — |
| NFR3.1・NFR3.2 同時性・一括確定 | 当たらない（サーバーの持ち物。画面は `ROLE_NOT_ASSIGNED` の読み直しだけで、機能設計 D8） | — |
| NFR4.1 WCAG 2.1 AA と部品ごとの検査 | 当たる | NFR4.1（tech-stack） |
| NFR4.2 N 階層のメニューと実際のブラウザの axe | 当たる（主の単位） | NFR4.2・NFR4.4・NFR4.5（tech-stack） |
| NFR4.3 日本語と英語 | 当たる | NFR4.3（tech-stack） |
| NFR5.1・NFR5.2 監査・指標 | 当たらない（U3・U4。画面は監査と指標を持たない） | — |
| NFR6.1 必須のテスト | 部分（N 階層のメニューの画面と画面の出し分け。サーバーの必須のテストは U3〜U5） | NFR6.1・NFR6.7（tech-stack） |
| NFR6.2 性質ベースのテスト | 部分（画面の木を作る関数。サーバー側の絞る関数は U5、継承の解決は U4） | NFR6.2（tech-stack） |
| NFR6.3 E2E | 部分（I の流れの1本と、F の流れに B9 で足す部分。F の前半は U6） | NFR6.3（tech-stack） |
| NFR6.4 カバレッジの下限 | 当たる | NFR6.4・NFR6.5・NFR6.6・NFR6.8（tech-stack） |

## 要件

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR1.1 | メニュー・画面を隠すことをサーバー側の判定の代わりにしない。<br>・管理の区画はログイン状態の `admin` で出し分けるが、見せ方だけ。管理の API はサーバーが守る（D12）。<br>・業務のメニューは、サーバーが作業ロールで絞った木（C9）をそのまま描き、画面で権限を足さない（D11）。<br>・置き場（S2）は開くたびにサーバーに問い、200 のときだけ表示名を出す（D21）。403・400・引数なしは同じ権限なしの表示で、テーブルの有無を見分けられない（AC5.1.4）。<br>・メニューに出ていないテーブルの道を直接開いても、サーバーの答えで決まる | 画面のテスト:<br>・`buildNavSections.test.ts`: `admin` が偽なら管理の区画が無い。<br>・`TablePlaceholderPage.test.tsx`: 403・400・引数なしが同じ表示で、表示名とテーブルの名前が DOM に無い。<br>・`TablePlaceholderPage.test.tsx`: メニューに無い組の道でも問い合わせを送る。<br>サーバーの 401・403・200 の表は U3〜U5 のテスト | Code Generation（B9） |
| NFR1.4 | 画面が送る要求に利用者を指す値を入れない。主体はサーバーが要求の文脈から読む（AC4.1.12・AC4.2.3）。<br>・作業ロールの切り替え（`PUT /api/me/work-role`）の本文は `{roleId}` だけ。<br>・業務のメニュー・作業ロールの読み取り・自分の権限は、引数を付けないか、名前の引数だけ。<br>・置き場の問い合わせは `schema`・`table` だけを送り、画面の道の `item` は送らない | `workRoleApi.test.ts`・`navigationApi.test.ts`・`tableAccessApi.test.ts`・`myPermissionsApi.test.ts` で、道・引数・本文が上の形だけであることを、要求の差し替えで確かめる | Code Generation（B9） |
| NFR1.6 | 個人に関する値・応答の値を、画面の外へ出さない。<br>・応答・要求の値（ロールの名前・表示名・作業ロール）をコンソールに出さない。<br>・ブラウザの保存（`sessionStorage`）には、開閉の状態の項目の `id`（位置の道と `biz:`・`adm:`・`home`）だけを置く。名前・氏名・トークンは置かない（D16）。<br>・置き場の道の引数は、DSL の定義のスキーマ名・テーブル名だけ。接続情報（接続先・ユーザー名・パスワード・JDBC の URL）は道に入らない（`project.md` の Forbidden。読み方はストーリーの差 D1 と同じ） | `navExpansion.test.ts`: 保存した値が `id` の配列だけ。<br>画面のテストでコンソールの呼び出しを見張り、値が出ないこと。<br>`tablePlaceholderPath.test.ts`: 道の引数が `schema`・`table`・`item` の3つだけ | Code Generation（B9） |
| NFR1.7 | DSL から来る値（`label`・`displayName`・`icon`・テーブルの名前）を信頼できない入力として扱う（D14、`team.md` の N 階層のメニューの決まり、FR9.6）。<br>・名前は React の文字として描き、HTML として描かない（`dangerouslySetInnerHTML` を使わない）。<br>・アイコンは `app/registry` の許した名前の一覧で照らし、一覧に無い・空・大文字違いは `list`（サーバーが照らした後でも画面でもう一度照らす）。<br>・テーブルの置き場の道は、`URLSearchParams` で作る `/tables?schema=…&table=…&item=…` の形だけ。名前に `/`・`..`・`?`・`#`・`%`・空白・`&`・`=`・`javascript:`・`https://` を含んでも、外部の URL・`javascript:`・`/tables` の外の道にならない（AC5.1.7・AC5.1.13） | `ShellLayout.test.tsx`・`TablePlaceholderPage.test.tsx`: `<script>`・`<img onerror>`・`&`・`"`・`'` を含む表示名が文字のまま出て、タグにならない。<br>`buildNavSections.property.test.ts`（性質 2・3、`tech-stack-decisions.md` の NFR6.2）と `tablePlaceholderPath.test.ts`（上の文字を含む名前の往復と、道の先頭が `/tables?`）。<br>静的検査は NFR1.10 | Code Generation（B9） |
| NFR1.8 | ログアウトは、ユーザーメニューの操作から来たときだけ行う（Q3 A、機能設計の再レビューの R-07）。<br>・**印は履歴に残らない一度きりの値にする**（承認の場の直し R-01）。ルーターの `state` は履歴に保存され、再読み込み・戻る・進むでも残るため使わない。<br>・印は `features/auth` の中のメモリ上の値（モジュールの中の1つの値）に置く。ユーザーメニューの「ログアウト」を選んだとき、`/logout` へ移る直前に印を立てる。<br>・`/logout` の画面は、描いた最初に印を読み、その場で消す（読むと同時に下ろす）。印があったときだけ `logout()` を1回呼ぶ（StrictMode の二度の描画でも1回）。<br>・印が無いとき（外のサイトのリンク・ブックマーク・アドレスの直接の入力・再読み込み・戻る・進む）は、ログアウトせずにホームへ戻す（`replace`）。再読み込みでは画面のメモリが消えるため、印は必ず無い。<br>・印は、S4 の確かめで［留まる］を選んで移動が止まったときも消す（次の別の移動で使われないように）。<br>・`/logout` の道では、作業ロールと業務のメニューを読み直さない（D4 の (d) の例外）。<br>・U6 の S4 の保存していない変更の確かめ（`useBlocker`）は、この移動にもそのまま効く | `LogoutPage.test.tsx`: 印ありで `logout()` が1回呼ばれ、印が消える。同じ画面を描き直しても2回目は呼ばれない。印なしでは呼ばれずホームへ移る。［留まる］の後に印が残らない。<br>`WorkRoleProvider.test.tsx`・`BusinessNavigationProvider.test.tsx`: `/logout` へ移っても要求を送らない。<br>`features/auth` の登録のテスト: メニューの項目が `/logout` へのリンクで、選んだときに印を立てる。<br>**実際のブラウザの確かめ（1件）**: I の流れの E2E（`tech-stack-decisions.md` の NFR6.3）の最後の手順として行い、流れの本数は増やさない。<br>・ログインしたまま `/logout` を直接開くと、ホームに戻りログインしたまま。<br>・メニューからログアウトし、ログインの画面に移る。<br>・ブラウザの戻る・再読み込みをしても、ログアウトの要求（`POST /api/auth/session/logout`）は流れ全体で1回だけで、ログインの画面のまま。要求の数は Playwright の要求の見張りで数える | Code Generation（B9）・Build and Test |
| NFR1.9 | E2E の報告と記録に値を残さない（`project.md` の学び）。<br>・仮の資格情報はプロセスの環境変数で渡し、`webServer.env` に置かない。<br>・報告の値の確かめは、既存の `playwright-secret-check-reporter.ts` を使い回す。この部品はファイルの名前で絞らずに json の報告・`test-results/`・前の報告を探すため、U7 の検査・I の流れ・F の流れへの追加にもそのまま効く（U6 のレビューの R-06）。<br>・I の流れで作る利用者のメールアドレス・パスワード・氏名が、部品の探す値（`e2e/support/secretValues.ts` の値、または既存の形）に入るようにする。<br>・測りのテスト（`performance-requirements.md` の NFR2.8）の添付には、名前と時間だけを書き、氏名・メールアドレスを書かない | I の流れのコード生成で、作った値が `secretValues.ts` に書かれることを確かめる。<br>統合の前の E2E 全体の実行で、報告の部品が失敗しないこと。<br>部品の自己の確かめ（既存）が通ること | Code Generation（B9）・Build and Test |
| NFR1.10 | 画面の書き方の静的検査を今のまま当てる。oxlint の `react/no-danger`・`no-script-url`、ESLint の `no-implied-eval`・`export default` と `enum` の禁止・機能どうしの import の制限（U1）が通る。<br>`features/tables`・`features/mypermissions` は互いに import しない。`app/` のフックだけを読む | `./gradlew verify` の中のリンタの段 | Code Generation（B9） |

## 脅威の見方（STRIDE の要点）

| 種類 | この単位での当たり方 | 守り |
|---|---|---|
| なりすまし・権限の昇格 | 画面の値を変えて他人の作業ロールや権限を読む・変える | 要求に利用者を指す値を入れない（NFR1.4）。判定はサーバー（NFR1.1、U4・U5） |
| 改ざん（信頼できない入力） | DSL の `label`・`icon`・テーブルの名前に、タグや外部の道を入れる | 文字として描く、アイコンの照合、道の形の固定（NFR1.7・NFR1.10） |
| 情報の漏えい | 権限の無いテーブルの表示名、ブラウザの保存・コンソール・E2E の報告に残る値 | 置き場の同じ権限なしの表示（NFR1.1）、保存は `id` だけ（NFR1.6）、報告の確かめ（NFR1.9） |
| 意図しない操作の強制 | 外のリンクで `/logout` を開かせる | 操作の印が無ければログアウトしない（NFR1.8） |
| サービスの妨害 | 深い木・多い項目で画面が固まる | `performance-requirements.md` の NFR2.6・NFR2.7 の測り。深さ 5 段と DSL の大きさの上限はサーバー（U2） |

## コンプライアンスの見方

- 新しい個人データの保存は無い。画面は表示だけで、ブラウザの保存は開閉の `id` だけ（NFR1.6）。
- テストの見本のメールアドレスは `example.com` だけ。実在しそうな氏名を置かない（`team.md`）。
- 新しい依存は足さない。make-you-chic-ui の固定先の更新でライセンス（Apache License 2.0）が変わらないことは、`tech-stack-decisions.md` の NFR6.6 で確かめる。

## 承認の場の決定と直し

- 決定: 依頼者は承認の場で Request Changes を選び、直す範囲を「Major 11 件だけを直す」とした。この単位で直すのは R-01・R-02 の2件で、ほかの指摘は直さない。レビューの記録は `aidlc/spaces/default/intents/261004-role-menu/.aidlc-reviews/nfr-requirements/units/app-frame-ui/4e9510ac79c4db11/1.json`。
- **R-01（Major）**: `/logout` の操作の印をルーターの `state` に付けると、ブラウザの履歴に残り、再読み込み・戻るでも印が残る。この文書の NFR1.8 を次のとおり書き直した。
  - 印は、`features/auth` の中のメモリ上の一度きりの値にし、ログアウトの画面が読んだときに消す。履歴の `state` は使わない。［留まる］で移動が止まったときも消す。
  - 実際のブラウザの確かめを1件足した: 直接の表示でログアウトしない、メニューからのログアウトの後に戻る・再読み込みしてもログアウトの要求が1回だけ。I の流れの E2E の最後の手順として行い、流れの本数は増やさない（`tech-stack-decisions.md` の NFR6.3 と上流との差 (d) も合わせた）。
- R-02 の直しは `performance-requirements.md` の同じ名前の節に書いた。
