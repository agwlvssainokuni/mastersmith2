# Code Generation の質問 — U1 u1-businessdata-read（B2）

計画（`code-generation-plan.md`）の 9節「依頼者に確かめたいこと」の答えを先に決め、計画に反映してから Plan Approval に進みます。

## D1. 結果を読む段の型の変換の失敗（NFR 設計のレビュー R-01）

MySQL・MariaDB のゼロ日付のように、保存済みの値を読むときに型の変換が失敗する行があると、入力が正しくても、そのテーブルを開けなくなります。この場合は 400 にしません。そのうえで、何を返すかを決めます。

A. 409 `BUSINESS_TARGET_DB_MISMATCH` に写す（定義との食い違いの一種として扱う。新しい code を足さず、U3 の文もそのまま使える。ログは WARN で、理由は `READ_CONVERSION`）（推奨）

B. 新しい code（例 409 `BUSINESS_TARGET_DB_VALUE_UNREADABLE`）を足して見分ける。U3 の画面に文を1つ足す

C. 想定外の失敗（500）にする

D. 行は返し、読めない値のカラムを印で返す（契約 C2 に項目を足し、U3 の作業が増える）

X. Other (please specify)

[Answer]: A

## D2. 主キー以外で並べ替えた深いページの場面（R-05・基盤の設計のレビュー R-03）

A. 並べ替えのカラムに索引を作らず、悪い側の条件として測る。MySQL・MariaDB は 1 秒を超える見込みが高く、Performance Validation で Not Met として扱いを諮る（推奨）

B. 並べ替えのカラムに索引を作る（表と索引が大きくなり、PG は 80% の線を超える見込み）

C. この場面を足さず、主キーの順の場面だけで測る

X. Other (please specify)

[Answer]: A

## D3. つないだ後に止まる中継のテストを置く DB の種類

1回で 10〜20 秒かかります。

A. 3種類すべてに置く。verify が約 45 秒延びる（直近は 14 分 1 秒で、42 分の線まで余裕がある）（推奨）

B. 最も長くかかる MySQL だけに置く

C. 置かず、借りる失敗と遅い問い合わせの確かめだけにする

X. Other (please specify)

[Answer]: A

## D4. 台本の試し走り（Step 22）の範囲と、配備したアプリの扱い

A. PostgreSQL の1種類だけを、既定の設定で流す。配備したアプリは止めない（上限の合計 5,376 MiB で VM に収まる）。MySQL・MariaDB の種の台本は SQL を出すところまでを確かめ、流すのは Performance Validation にする（推奨）

B. 3種類すべてで流す。MySQL・MariaDB の回は、配備したアプリを止める

C. 試し走りを Performance Validation に回し、この段は `k6 inspect` と compose の確かめだけにする

X. Other (please specify)

[Answer]: A

## Plan Approval

`code-generation-plan.md`（埋め込みの Testing Contract を含む）と `unit-test-instructions.md` の承認です。D1〜D4 の答えは、計画に反映済みです。

[Approval Fingerprint]: sha256:v3:e7b668d7aab701d37414d9ac004be7e947ead69df3de6afe8c447d249fcd7f58
[Planned Source]: 254604db5f808ae654cf114324e8287e7c022c68fe68c4fed4328535b9293633

- Approve Plan
- Request Changes

[Answer]: Approve Plan
