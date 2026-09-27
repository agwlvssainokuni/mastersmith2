# Functional Design — Questions（U8 インスタンスの見た目の設定 / u8-instance-appearance）

U8 の受け持ち（`aidlc/spaces/default/intents/260925-user-management/inception/units-generation/unit-of-work.md` の U8）は、`application.yaml` のブランドカラーとフォントファミリーを読み、ログインなしで読める API（契約 C7）で返す小さなバックエンドです。論点のほとんどは上流で決まっているため、まず設計の要点（案）を示し、上流の答えから決まらない点だけを質問にします。

## 設計の要点（案）

1. **置き場**: 新しいパッケージ `cherry.mastersmith.appearance` に置き、中を `web`（API）・`service`（設定の解決と提供）・`config`（設定の型）に分ける。`domain`・`repository` は作らない（内部DB に触れない）。新しいパッケージのため、パッケージごとのカバレッジの下限（行 80%・分岐 70%）の対象になる。
2. **エンティティは作らない**: ブランドカラーとフォントファミリーは設定値で、アプリが独自に持つデータは無い。`entities.md` には「エンティティ無し」とその理由を書き、求める振る舞いは `rules.md` の決まり（BR）として書く。
3. **設定の項目**: `mastersmith.appearance.brand-color`（環境変数 `MASTERSMITH_APPEARANCE_BRAND_COLOR`）と `mastersmith.appearance.font-family`（`MASTERSMITH_APPEARANCE_FONT_FAMILY`）の2つ。`application.yaml` は既存の書き方どおり `${環境変数:}` で空を既定にし、`.env.example` と README に項目を足す。
4. **設定の型は文字列で受ける**: 列挙で受けると許されない値で Spring の結び付けが起動を止め、FR8.2（起動を止めない）を守れないため、文字列で受けて自前で判定する（前の Intent の対象DB の設定と同じ判断）。
5. **値の解決**: 起動時に1回だけ判定し、結果を保持する。
   - 空・未設定 → 既定（blue・sans）。警告は出さない（「無いとき」に当たる）。
   - 許される値 → その値。
   - 許されない値 → 既定（blue・sans）にし、項目ごとに WARN のログを1回だけ出す。起動は止めない。
   - 2つの項目は別々に判定する（片方だけ不正なら、その項目だけ既定にする）。
6. **警告のログ**: 構造化ログのキーと値で、項目の名前・使った既定の値・許される値の一覧を出す。設定された値そのものは出さない（前の Intent の対象DB の設定の警告と同じく、項目の名前だけで原因が分かる形にそろえる）。スタックトレースは付けない。
7. **API**: `GET /api/appearance` が `200` で `{ brandColor, fontFamily }` を返す（契約 C7。値は列挙の小文字の名前だけ）。起動時に解決した値を返すだけで、要求ごとに設定を読み直さない。設定の変更はアプリの起動し直しで当たる。
8. **公開の決まり**: U8 の `SecurityRuleContributor` で `GET /api/appearance` だけを認証なしにする。order はほかの単位（既存の 110・210、U3 が足すもの）と重ならない値を割り当て、重なりは既存の起動時の検査（`SecurityExtensionValidator`）で止まる。ほかのメソッドは既存の既定の扱い（`/api/**` はログイン必須）のまま。
9. **キャッシュ**: 既存の `CacheControlFilter` により `/api/**` の応答は `no-store` になる。U8 はこれを変えない。前に保存された値で一瞬描かれることの防ぎ方（ストーリーの「後の段に回す点」、契約 C7 の未解決の点）は、ブラウザに最後の値を持つなど画面の側（U4）の機能設計で扱い、U8 はサーバーの側で手を足さない。
10. **エラーと監査**: 想定内の失敗は無く、エラーの `code` の一覧（`ProblemTypeCatalog`）は作らない。読むだけの公開の API のため監査の出来事は出さない（FR9.1 の対象に無い）。応答に秘密・個人に関する値は含まない。
11. **画面での使われ方**: 返すのは名前だけで、画面（U4）が make-you-chic-ui の見た目に対応づける。外部のフォントを読み込まないため、既存の Content-Security-Policy（`font-src 'self'`）は変えない。
12. **受け入れ基準との対応**: CR2（Should）は受け入れ基準の番号を持たないため、`traceability.json` は FR8.1・FR8.2 と FR8.2 の Given/When/Then（red → 起動し、blue、警告）を BR に対応づける。

## 決まっていること

| 決まっていること | 出典 |
|---|---|
| ブランドカラーは blue・green・purple・orange、フォントファミリーは sans・serif。`application.yml` のインスタンス全体の設定で、利用者ごとには変えられない | 要件 FR8.1、ストーリー CR2 |
| 無いときは blue・sans。許されない値のときも blue・sans にして警告のログを出す。どちらも起動は止めない | 要件 FR8.2、`components.md` の InstanceAppearance |
| 画面へは、ログインなしで読める公開の API で渡す（配信する HTML への埋め込みはしない） | ADR-006（Domain Design Q6: A） |
| API は `GET /api/appearance`、応答は `brandColor`・`fontFamily`（どちらも必須の列挙）、秘密を含まない | 契約 C7 |
| `/api/appearance` は差し込み口（`SecurityRuleContributor`）で公開にする | 契約の共通の決まり（認可） |
| 画面に当てるのは U4。U8 は読み取りと既定への置き換えと公開の API だけ | `unit-of-work.md` の U8・U4 の境界 |
| 設定値はエンティティにせず、振る舞いを決まりとして書く | `project.md` の Code Style の学び、`unit-of-work.md` の U8 の注意 |
| CR2 の優先度は Should | ストーリーの段の Q4: C |
| 1つの機能の設定（`XxxProperties`）はその機能のパッケージに置く。コンストラクター注入のみ、Lombok は使わない | `team.md` の Code Style |
| `/api/**` の応答は `no-store` | 既存の `backend/src/main/java/cherry/mastersmith/common/web/CacheControlFilter.java` |

## Q1. ログインした後の画面が、期限切れなどの使えないアクセストークンを付けて `/api/appearance` を呼んだとき、どう扱いますか？

理由: 今の作りでは、公開の道でも `Authorization: Bearer` が付いていればトークンを検証し、使えなければ `401` になります（トークンを読まないのは `/api/auth/` の下だけ。`backend/src/main/java/cherry/mastersmith/auth/web/AuthSecurityContributor.java`）。画面の共通の呼び出し（`frontend/src/shared/api-client/apiClient.ts`）は、認証の API 以外にはトークンを付け、`401` なら更新を1回試し、更新も失敗すると未ログインの扱いに移ります。契約 C7 の「公開」がトークンの状態に関わらず `200` を返す意味かが決まっていません。

- A. 画面の側で、`/api/appearance` を呼ぶときはトークンを付けない（認証の API と同じ扱いにする。U4 の ApiClient の変更）。サーバーの側（U8・`auth`）は今の作りのままとし、トークンを付けて呼ばれたときの `401` は受け入れる
- B. サーバーの側で、`/api/appearance` ではトークンを読まないようにし、どんなトークンが付いていても `200` を返す（`auth.web` のトークンの取り出しに手を入れる。`auth.web` は全体の合計で判定しているパッケージのため、`team.md` の決まりでテストを足してパッケージごとの下限の対象に戻す作業が加わる）
- C. A と B の両方を行う（画面は付けず、サーバーも読まない）
- D. 今の作りのまま受け入れる（使えないトークンなら `401` → 更新 → 送り直し。更新も失敗すれば未ログインの扱いに移り、見た目は既定または前に保存された値で描く）
- X. Other (please specify)

[Answer]: A

## Q2. 設定の値の大文字・小文字と前後の空白を、どう扱いますか？

理由: 要件は「許されない値のときは既定にして警告」とだけ決めており、`Blue` や ` green ` のような書き方が許される値か決まっていません。前の Intent の対象DB の種類（`mastersmith.target-db.type`）は「大文字・小文字は問わない」としています。

- A. 前後の空白を除き、大文字・小文字を問わずに判定する（`Blue`・` green ` は blue・green として受け付ける）。API は常に小文字の名前で返す
- B. 大文字・小文字は問わないが、前後の空白は除かない（` green ` は許されない値として既定にし、警告する）
- C. 書いたとおりの小文字だけを受け付ける（`Blue` も ` green ` も許されない値として既定にし、警告する）
- X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめ:

- 設計の要点（案）は冒頭の「設計の要点（案）」の 12 件のとおり
- Q1 A: 画面の側で、見た目の設定の API（`/api/appearance`）を呼ぶときはトークンを付けない（U4 の ApiClient の変更）。サーバーの側は今の作りのまま
- Q2 A: 設定の値は前後の空白を除き、大文字・小文字を問わずに判定する。API は常に小文字の名前で返す

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
