# NFR Requirements — Questions（U8 インスタンスの見た目の設定 / u8-instance-appearance）

U8 は、ブランドカラーとフォントファミリーを設定から起動時に1回だけ読み、ログインなしで読める `GET /api/appearance`（契約 C7）で返す小さなバックエンドです（種類 service のため、性能・セキュリティ・拡張性・信頼性・観測性・技術の選択・traceability のすべてを作ります）。非機能の論点のほとんどは要件・契約・承認済みの機能設計と既存のコードで決まっているため、まず NFR の要点（案）を示し、上流から決まらない点（応答時間の目標の値）だけを質問にします。

読んだ上流:

- 承認済みの機能設計 `aidlc/spaces/default/intents/260925-user-management/construction/u8-instance-appearance/functional-design/`（`rules.md` の BR1.1〜BR3.6、`functional-spec.md`、`entities.md`、`traceability.json`）
- 要件 `aidlc/spaces/default/intents/260925-user-management/inception/requirements-analysis/requirements.md`（FR8、NFR1〜NFR11、制約、未解決の点）
- 契約 `aidlc/spaces/default/intents/260925-user-management/inception/contract-design/contract-summary.md`（共通の決まり、C7、未解決の点）
- 設計の決定 `aidlc/spaces/default/intents/260925-user-management/inception/domain-design/decisions.md`（ADR-006・ADR-010）、単位 `inception/units-generation/unit-of-work.md`（U8）、Bolt の計画 `inception/delivery-planning/bolt-plan.md`（B4）
- U4 の承認済みの機能設計 `construction/u4-display-foundation/functional-design/functional-spec.md`（W2 のゲート: 見た目の設定の答えが出るまで最初の画面を描かず、待ちに上限を置かない。D10: トークンを付けずに呼ぶ）
- コード知識ベース `aidlc/spaces/default/codekb/mastersmith2/`（`api-documentation.md` の公開の API、`code-quality-assessment.md` の K-6）
- 既存のコード `backend/src/main/java/cherry/mastersmith/config/SecurityConfig.java`・`common/security/SecurityRuleContributor.java`・`auth/web/AuthSecurityContributor.java`・`common/web/CacheControlFilter.java`・`backend/src/main/resources/application.yaml`・`backend/src/test/java/cherry/mastersmith/ArchitectureTest.java`
- 前の Intent の NFR 要件 `aidlc/spaces/default/intents/260922-auth-audit-base/construction/*/nfr-requirements/`（負荷の想定、接続元ごとの回数の制限を設けない判断）

## NFR の要点（案）

1. **性能**: U8 の応答は、起動時に解決してメモリに保持した2つの名前を返すだけで、内部DB・ほかの単位・外部の仕組みに触れない（BR1.6）。負荷の想定は既存と同じ（利用者 50 名、同時 10 件）。目標の値は Q1 で決める。U4 の W2 のとおり、この API の応答はすべての画面の最初の描画を待たせるため、速く・必ず返ることが要る。
2. **信頼性（内部DB に依存しない）**: `GET /api/appearance` の処理は内部DB の接続を借りない。接続プールが尽きたとき、内部DB の詰め直しの一時停止（JMX の操作。内部DB を使う要求は再開まで待たされる）のあいだも、応答が待たされない。トークンを付けない要求は認証の段階でも内部DB に触れない（既存の `AuthSecurityContributor` はトークンが無ければ検証をしない）。`appearance` のパッケージが DB アクセス（`repository`・`DataSource`・JPA）に依存しないことを ArchUnit の構造の検査で確かめる。
3. **信頼性（起動を止めない）**: 設定の不備（無い・許されない値）で起動を止めず、既定（blue・sans）で必ず 200 を返す（BR1.3・BR1.4・BR1.7）。可用性の目標はアプリ全体と同じで、U8 だけの SLO は置かない。配備先が決まるまでは手元の監視を常に動かさないため、SLO の判定は既存の決まりどおり `Unverified` とする（`project.md` の Deployment）。
4. **セキュリティ（公開の範囲）**: ログインなしで読めるのは `GET /api/appearance` だけ（BR3.2）。ほかのメソッドは既存の扱い（未認証・使えないトークンは 401 / `AUTHENTICATION_REQUIRED`、使えるトークン付きは 405 / `METHOD_NOT_ALLOWED`）。サーバー側のテストで、未認証の GET が 200、未認証の POST が 401、使えるトークン付きの POST が 405 であることを確かめる（NFR4 と同じ確かめ方）。
5. **セキュリティ（HEAD と OPTIONS）**: 公開の決まりはメソッドを GET に限って足すため、ログインしていない `HEAD`・`OPTIONS` は公開にならず、既存の扱い（401）になる。使えるトークン付きの `HEAD` は Spring の既定で GET と同じ処理が本文なしで返る。画面は GET しか使わないため、HEAD を公開に足さない（BR3.2 の「GET だけ」を文字どおりに守る）。未認証の HEAD が 401 になることをサーバー側のテストで確かめ、実際の扱いが違えば（GET の決まりが HEAD にも当たれば）コード生成で記録する。
6. **セキュリティ（回数の制限を設けない）**: ログインなしの API の回数の制限は設けない。返すのは秘密を含まない2つの名前だけで（BR3.4）、処理はメモリの値を読むだけで内部DB も使わない。既存の公開の `GET /api/problems/{slug}` にも制限は無く、前の Intent でログインの API の接続元ごとの回数の制限も、利用者 50 名の社内向けとして設けないと決めている（`260922-auth-audit-base` の U2 の `tech-stack-decisions.md`）。濫用への備えは配備先が決まったときの前段（逆プロキシなど）の仕組みで扱う。
7. **セキュリティ（応答とログの中身）**: 応答は2項目だけで、設定された元の文字列・ほかの設定・利用者の情報を載せない（BR3.4）。警告のログは WARN・スタックトレースなしで、項目の名前・使った既定の値・許される値の一覧だけを出し、設定された値そのものは出さない（BR2.1）。応答のヘッダーは既存の共通の仕組み（Content-Security-Policy・`X-Content-Type-Options`・`X-Frame-Options`・`Referrer-Policy`）がそのまま付き、U8 では変えない。外部のフォントを読み込まないため `font-src 'self'` も変えない。
8. **キャッシュ**: 応答は既存の `CacheControlFilter` により `no-store` のまま（BR3.6）。見た目の設定は起動し直しで変わり、`no-store` なら変更の後の最初の読み取りから新しい値になる。前の値で一瞬描かれることは U4 の W2 のゲート（答えが出るまで描かない）で防ぐため、U8 でキャッシュの見出しを変えて速さを稼ぐ必要は無い。
9. **拡張性**: 内部DB が組み込みの H2 のため、アプリは1インスタンスだけで動く（要件の制約）。U8 の値は起動時に決まって変わらない読み取り専用の値のため、同時の要求どうしで待ち合わせる箇所が無く、同時の数が増えても U8 が先に詰まることは無い。拡張の仕組み（複数のインスタンスへの配り直しなど）は持たない。
10. **観測性**: 新しい独自の指標は足さない。要求の数・誤りの数・応答時間は、既存の HTTP の要求の指標（Micrometer の `http.server.requests`、`uri` のラベルに `/api/appearance`）とトレースで見える。健全性は既存の `/actuator/health` のまま（U8 は起動の後に失敗しうる依存を持たない）。起動時の警告のログ（BR2.1）が、設定の不備を運用で見つける手段になる。監査の出来事は出さない（BR3.5）。
11. **技術の選択**: 新しい依存は足さない（ライセンスの確認は不要）。設定は1つの機能の設定の型（`XxxProperties` の `record`、`appearance` のパッケージの中）で、値を列挙に結び付けず文字列で受け取る（BR1.7）。大文字・小文字の比べ方は実行環境の言語の設定に左右されない形（`Locale.ROOT`）とする。値の判定は純粋な関数として、既存の決まりどおり jqwik の性質ベースのテスト（任意の文字列で、結果が常に許される値のどれかになる・許される値の大文字・小文字と前後の空白の揺れは同じ結果になる）を一部に当てる（`team.md` の Testing Posture）。
12. **テストとカバレッジ**: 新しいパッケージ `cherry.mastersmith.appearance` は、パッケージごとのカバレッジの下限（行 80%・分岐 70%）の対象になる（NFR9、`team.md`）。既存の一覧で外しているパッケージには手を入れない見込み（`SecurityRuleContributor` の説明文に U8 の order の範囲を足すのは `common.security` の説明文だけの変更で、コード生成の計画で拾う。機能設計の R-01）。
13. **traceability の見込み**: U8 に当たる要件の非機能は NFR4（認可: 公開の範囲とほかのメソッドの 401・405 のテスト）・NFR5（接続の使い方: 内部DB の接続を借りない）・NFR6（応答時間: Q1 の値）・NFR9（テストとカバレッジ）。NFR1〜NFR3・NFR7・NFR8・NFR10・NFR11 は、招待のトークン・メール・列挙の防止・画面・文言・スキーマ・メールの受け手のどれにも U8 が触れないため `N/A` とし、理由を書く。

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| ブランドカラーは blue・green・purple・orange、フォントファミリーは sans・serif。無い・許されない値は blue・sans にし、許されない値のときは起動時に警告を1回出す。起動は止めない | 要件 FR8.1・FR8.2、機能設計 BR1.1〜BR1.7 |
| API は `GET /api/appearance` で、200 と `brandColor`・`fontFamily` の2項目だけを返す。秘密を含めない | 契約 C7、機能設計 BR3.1・BR3.4 |
| GET だけをログインなしで公開し、差し込み口（`SecurityRuleContributor`）で足す。ほかのメソッドは既存の 401・405 に任せる。order はほかの単位と重ならない値にする | 契約の共通の決まり（認可）、機能設計 BR3.2 |
| 使えないトークンを付けた要求の 401 は受け入れ、画面の側がトークンを付けない | 機能設計 Q1 A・BR3.3、U4 の D10 |
| 応答のキャッシュは既存の `no-store` のまま | 機能設計 BR3.6、既存の `CacheControlFilter` |
| 読み取りは監査ログに残さない | 要件 FR9.1、機能設計 BR3.5 |
| 画面は見た目の設定の答えが出るまで最初の画面を描かず、待ちに上限を置かない | U4 の機能設計 W2（Q2 A） |
| 内部DB は組み込みの H2 で、1インスタンスだけで動く | 要件の制約、コード知識ベース `architecture.md` |
| 負荷の想定は利用者 50 名・同時 10 件。性能は 95 パーセンタイルで測る | 前の Intent の NFR1（`260922-auth-audit-base`） |
| 新しい依存はライセンスを確かめてから足す（今回は足さない） | `team.md` の Code Style |
| 負荷の試験は配備した環境とは別の使い捨ての環境で行う。この Intent には Performance Validation の段がある | `project.md` の Testing Posture、`aidlc-state.md` の段の一覧 |

## Q1. `GET /api/appearance` の応答時間の目標を、どうしますか？

理由: 要件 NFR6 は、招待・送り直し（5 秒）と、プリファレンス・パスワードの変更・登録の完了（既存の API と同じく 1 秒）の目標だけを決めており、見た目の設定の API の値は決めていません。この API はメモリの値を返すだけで内部DB に触れないため、1 秒よりずっと速く返せる見込みです。一方で U4 の W2 のとおり、この応答が遅いとすべての画面の最初の描画が遅れます（セッションの復元と並べて読むため、多くの場合はセッションの復元の方が長い）。前の Intent では、業務の処理を持たない軽い API（管理者向け領域の確認用 API）に 300 ミリ秒を置いた前例があります（`260922-auth-audit-base` の U3 の NFR1.1）。測る場は、この Intent の Performance Validation の段の負荷の試験（k6 に1本の場面を足す）とし、Build and Test では結合テストで機能を確かめます。

- A. 同時 10 件の要求が来る状態で、95 パーセンタイル 300 ミリ秒以内（軽い API の前例と同じ）。Performance Validation の負荷の試験で測る（推奨）
- B. 同時 10 件の要求が来る状態で、95 パーセンタイル 1 秒以内（NFR6 のほかの API と同じ）。Performance Validation の負荷の試験で測る
- C. 数値の目標を置かない。内部DB の接続を借りないこと（要点 2）だけを構造の検査と結合テストで確かめ、負荷の試験の場面も足さない
- X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめ（Q1 の回答の後に埋めます）:

- NFR の要点（案）は冒頭の「NFR の要点（案）」の 13 件のとおり
- Q1 A: `GET /api/appearance` の応答時間は同時 10 件で p95 300ms 以内。Performance Validation の k6 に場面を1本足して測る

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
