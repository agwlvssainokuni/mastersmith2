# 信頼性の設計 — U5 navigation

## 出典

- この単位の承認済みの NFR 要件 `construction/navigation/nfr-requirements/reliability-requirements.md`（NFR1.10・NFR1.11・NFR6.8）・`security-requirements.md`（NFR1.7・NFR1.12）
- この単位の承認済みの機能設計 `construction/navigation/functional-design/functional-spec.md`（2.1・2.2・3節）・`rules.md`（BR1.5・BR4.3・BR7.2）
- この段の答え `nfr-design-questions.md`（Q2 A・Q3 A と、まとめの確認）
- 統合の点: role の NFR 設計 `construction/role/nfr-design/reliability-design.md`（捨ての試し U5、3節の一致）、group の NFR 設計 `construction/group/nfr-design/reliability-design.md`（捨ての試し T6）、group・role の NFR 設計の読み直しの R-01・R-02（時間に頼るテスト・瞬間値）

## 1. 捨ての試しの結果（Q3 A）

### 1.1 行ったこと

- 場所: リポジトリの外のスクラッチの置き場に、作業フォルダを写して行った（`.git`・`build`・`node_modules`・`.env`・`aidlc`・`reference` は写していない）。終わった後に消した。本物の作業フォルダでは git・Gradle・npm を使っていない。
- 版と設定: 本番と同じ `backend/gradle.lockfile` の版（Tomcat `tomcat-embed-core` 11.0.26・Spring Security `spring-security-web` 7.1.1・Spring MVC `spring-webmvc` 7.0.9）。今のアプリの `SecurityFilterChain`・要求の検査（`StrictHttpFirewall` の既定と `AccessRequestRejectedHandler`）・Tomcat の既定の設定と `GlobalExceptionHandler` を、そのまま `@SpringBootTest(RANDOM_PORT)` で起動した。
- 試しのために、テストの側にだけ、引数 `schema`・`table` を受けて UTF-8 の16進で返す口を置いた。置き場は2つで、`/trial/echo`（ログインなしで通る道）と `/api/trial/echo`（ログインだけの道。未認証の 401 になれば要求の検査を通ったと読める）。
- 要求は生のソケットで送り、送るバイト列をそのまま決めた。
- かかった時間は約 10 分（上限 30 分）。

### 1.2 結果

| 試し | 送ったもの | 結果 | 設計への意味 |
|---|---|---|---|
| N1 | 正しくエンコードした引数の値に、`/`・`..`・`../x`・`?`・`#`・`%`・`%25`・空白・`;`・`javascript:alert(1)`・`https://example.com/x`・`\`・`&`と`=`・`<'"&>`・日本語（受注明細）・絵文字・300 文字・タブ・LF・CR・NUL・DEL | `/trial/echo` は 23 種すべて 200 で、受け取った値のバイト列は送った値と同じ（300 文字は長さ 300 で受け取った）。`/api/trial/echo` は 23 種すべて 401 `AUTHENTICATION_REQUIRED`（共通の本文）で、要求の検査の 400 は1件も無い | 引数で受ける形（機能設計 Q4 A）は、印字できる文字も制御文字も拒否しない。`StrictHttpFirewall` は引数の値を拒否しない。NFR1.7 の前提は成り立つ。base64url で包む形への切り替えは要らない（Q3 A の条件に当たらない） |
| N2-a | エンコードしていない空白・`#`・`"`・`<`・`|`・`{` を引数に直接書く | Tomcat が Spring に渡す前に 400 を返した。本文は Tomcat の既定の HTML で、アプリの共通の誤りの本文ではない | 画面（U7）は必ずエンコードして送るため当たらない。細工した要求だけが受ける。受け入れた制約（`security-design.md` 7節） |
| N2-b | 問い合わせが約 7,000 バイト | 200 | 長い名前は要求の頭の大きさの上限（Tomcat の既定 8 KiB）までは通る |
| N2-c | 問い合わせが約 9,000 バイト（`/trial/echo` と `/api/trial/echo`） | どちらも Tomcat の既定の HTML の 400（ログインの判定より前） | 8 KiB を超える名前は受けられない。DSL の名前に長さの上限は無いが、実際の名前（対象DB の識別子）は 64 文字程度で届かない。受け入れた制約 |
| N2-d | 正しくない `%` の並び（`a%zz`・`a%`・`a%2`） | **500 `INTERNAL_ERROR`**。`GlobalExceptionHandler` が ERROR を出し、その例外の文（Tomcat の `InvalidParameterException`）に **引数の名前と値（例 `Parameter [table] with value [a%zz]`）が入った** | 応答は共通の本文で、値は載らない。一方、ログに要求の値が出るため、NFR1.8（問い合わせの値をログに出さない）を、細工した要求では満たさない。これはアプリ全体の今の振る舞いで、navigation の範囲だけでは直せない。依頼者の決定で受け入れた制約とし、アプリ全体の直しは後の Intent に回す（`observability-design.md` 3節）。ログインだけの道には送っていないため、認証の前か後かは未確認 |
| N2-e | 引数 `table` が無い | 400 `VALIDATION_FAILED`（共通の本文、WARN の1行は例外の型だけ） | 承認済みの BR5.1 のとおり、既存の変換で足りる |
| N2-f | 引数 `table` が空（`table=`） | 200（空の文字列が渡った） | Spring の結び付けは空を拒否しない。navigation の画面入出力の層で空を明示して確かめ、400 `VALIDATION_FAILED` にする（`security-design.md` 4節） |

### 1.3 先の単位の試しの使い回し

- role の U5: `snapshotFor`（1万行）は p95 3 ms・最大 42 ms、`resolve` は p95 1 ms・最大 8 ms。navigation の時間の予算は、この値で足りる（`performance-design.md` 2節）。
- group の T6: 例外の連なりの文に行の値が入るのは、書き込み・行の排他の経路。navigation は読み取りだけで当たらない（3節）。

## 2. 読み取りと DSL の適用し直しの重なり（NFR1.10）

- 待ち合わせの口 `NavigationBarrier` を `navigation.service` に置く（Q2 A。部品は `logical-components.md` の L7）。本番は何もしない `NoOpNavigationBarrier`、テストは `navigation/testsupport` の `@Primary` の部品で、点で止めて合図で再開する。

| 点 | 呼ぶ所 | 使うテスト |
|---|---|---|
| `afterDslRead()` | メニューの `current()` の後・`snapshotFor` の前 | `NavigationDslSwapIT` のメニューの場合 |
| `afterResolve()` | 置き場の `resolve` の後・表示名のための `current()` の前 | `NavigationDslSwapIT` の置き場の場合 |

```java
// 説明用の断片（navigation.service）
public interface NavigationBarrier {
    void afterDslRead();   // メニュー: current() の後、snapshotFor の前
    void afterResolve();   // 置き場: resolve の後、表示名の current() の前
}
```

- `NavigationDslSwapIT` の場合は4つ。
  - メニュー × テーブルを1つ消した DSL: 再開した応答に、消えたテーブルを指して移れる項目が無い。
  - メニュー × 別のテーブルを足した DSL: 再開した応答に、権限の無い項目が無い。
  - 置き場 × 組を消した DSL: `resolve` が READ を返した後に DSL から組が消え、応答は 403（表示名を返さない）。
  - 置き場 × 組を残した DSL: 200 で、表示名は新しい DSL の値。
- 止め方と放し方: テストは点で止まったことを合図で受けてから DSL を適用し、適用の確定を待って放す。待ちの上限（20 秒）は止まったままになったときの守りで、合否は応答の中身で決める。`sleep`・経過の時間・壁時計の境は使わない（`team.md`、group・role の読み直しの R-02）。

## 3. 想定外の失敗（NFR1.11）

- navigation は書き込み・行の排他を持たない。H2 の読み取りは MVCC で行の排他を待たないため、group の T1〜T4 の違反・上限切れの経路は無い。
- 解決の口の DB の誤りは例外のまま投げ、`GlobalExceptionHandler` が 500 の共通の本文にする（BR7.2）。途中まで絞った木は返さない（絞る関数は写しを得た後に動き、例外は写しを得る前に起きる）。
- `NavigationFailureIT`: 解決の口を例外を投げる替え物（`@Primary`）にし、2本の API の応答が 500 の共通の本文で、例外の文を含まないこと。

## 4. アイコンの一覧のファイルの誤り（NFR6.8）

- `AllowedNavIconList` は起動時に1回だけ読み（`@PostConstruct` ではなく、コンストラクターで読む部品にする）、誤りなら起動を止める（BR4.3）。
- `AllowedNavIconListTest`（単体）: 読む口に文字列を渡す形にし、`#` の行・空行・前後の空白の読み方と、5つの誤り（無い・読めない・0個・`list` が無い・重なり）で例外を投げることを確かめる。ファイルの場所を変えるテストの設定は置かない（本番のファイルは既存の起動のテストが読む）。
- 配備した後に起きうる経路は無いため、可用性の要件にしない（承認済み）。

## 承認の場の決定と直し

- 決定: 依頼者は承認の場で Request Changes を選び、決定の文は「推奨の案のとおり直す」。navigation で直すのは R-01・R-02 の2件。
- R-02（Major）: 1.2 の N2-d の行の「依頼者に諮る」を、依頼者の決定（受け入れた制約、直しは後の Intent）に改め、認証の前か後かは未確認であることを足した（試しではログインなしで通る道にだけ送ったため）。中身は `observability-design.md` の 3節と末尾の節。
