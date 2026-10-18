# 拡張性の設計 — U1 u1-businessdata-read

## 捨ての試しの結果（要点）

全体は reliability-design.md の 1節にある。拡張性に関わるものは次の4つ。

- **T5 ヒープ**: 100 件 × 100 カラム × 日本語の 100 字の応答（`bizSearchWide` の形）を、3種類の DB で測った。

  | 測ったもの | 値 |
  |---|---|
  | 1要求で JDBC の読みと切り詰めが割り当てる量 | 17.6〜18.2MB |
  | JSON の書き出しが割り当てる量 | ほぼ 0MB（流しながら書く） |
  | JSON の大きさ | 2.86MB |
  | 10 本分の結果を同時に持ったときに残る量 | 23.2MB |

- **T4**: `SUBSTRING` で絞らないと、2,000,000 字の値 10 行で 267MB を割り当てた。絞ると 0.016MB だった。
- **T6（借りる待ち）**: 10 本同時・プール 5 で、繰り返しの間が 0ms だと、借りる待ちの 3 秒の時間切れが起きた（PG で 29〜48 回）。間を 2ms 置くと 0 回だった。
- **T6（表の大きさ）**: MySQL・MariaDB は、表（532MB・372MB）がバッファープール（既定の 128MB）に入りきらず、重い場面の待ちが積み重なった。依頼者の決定は F1: A で、Performance Validation では表が入る設定で測る。

## 対応表

| U1 の NFR 要件の ID | この文書の節 | 扱い |
|---|---|---|
| NFR2.1 規模の前提と種 | 1節 | 種の形は Q5: A（performance-design.md の 4節） |
| NFR2.2 読む行の数の上限 | 2節 | そのまま |
| NFR2.3 1要求の接続の数 | 3節 | そのまま |
| NFR2.4 プールの大きさ | 4節 | そのまま。合否は Performance Validation（F1: A） |
| NFR2.5 長い文字と memory.peak | 5節 | R-03 の手当てを Q6: A で取り込んだ |

## 1. 規模と、横に増やさない前提

- アプリは状態を持たない。ただし、内部DB が組み込みの H2 のため、1台で動く前提（既存の決まり）。台数を増やす作りは置かない。
- 規模の前提は、対象DB 100 テーブル・100 カラム・利用者 50 名（NFR2.1）。
- 負荷の試験の悪い側は、100 カラムの表1つに 100,000 行。種の形は Q5: A（行は約 2KB）。
  - 試しの行の平均は、PG で 1,820 バイトだった。
  - 試しの表の大きさは、PG 198MB・MySQL 532MB・MariaDB 372MB だった。
- 道は3つに固定する。指標の `uri` の系列は、テーブルの数が増えても増えない。

## 2. 読む行の数（NFR2.2）

- 一覧は、`LIMIT ?`（ページの件数 20・50・100）と `setMaxRows(size)` の両方を置く。
- 深いページを主キーを先に読む形にしても、外側の問い合わせが読む行は最大 size 件（performance-design.md の 2節）。
- 詳細は、`setMaxRows(2)`。

## 3. 1要求の接続の数（NFR2.3）

| API | 内部DB（同時に借りている最大） | 対象DB（同時に借りている最大） | 順 |
|---|---|---|---|
| 定義 | 1 | 0 | 写しの短い読み取りだけ |
| 一覧 | 1 | 1 | 写しを取り終えて返してから、対象DB を1回の貸し出しで借りる（件数とページ） |
| 詳細 | 1 | 1 | 一覧と同じ |

- 対象DB を借りている間、内部DB の借りている数は 0 になる。写しは、`EffectivePermissionResolverImpl.snapshotFor` の読み取りだけのトランザクションの中で取り終える。
- 次の2つは、同じ1本の接続の中で行うため、接続の数は増えない。
  - MySQL・MariaDB の `SET time_zone`
  - 貸すときの `setNetworkTimeout`（F2: A）
- 確かめは `BusinessDataConnectionUsageIT` で行う。形は、既存の `NavigationConnectionUsageIT` と同じにする。

## 4. 対象DB の読み取りのプール（NFR2.4）

- 上限は 5 のまま（決まっていること）。DSL の生成（上限 20 秒）と照合が同じプールを使う間は、使える数が減る（既知の制約）。
- 10 VU の場面は、上限 5 に必ず届く。
- 合格の条件は、承認済みのとおり次の3つ。
  - 各場面の p95 < 1 秒
  - 待ちの時間切れの累計 0
  - 503 の件数 0
- 試しの T6 の見立て:
  - PG の軽い場面（最初のページ）は、借りる待ちを含めても p95 23ms だった。
  - 部分一致と MySQL の場面は、1回の繰り返しが重く、待ちが積み重なった。MySQL の部分一致は、45 回すべてが借りる待ちの 3 秒で切れた。
  - 依頼者の決定は F1: A で、プールの大きさとアプリの作りは変えない。表が入る設定で測り、それでも超えれば Not Met として、Performance Validation で扱いを諮る。
- 繰り返しの間が 0ms のとき、HikariCP は、待っている側を 3 秒待たせることがある（T6）。Performance Validation で、待ちの時間切れの累計を必ず読む。
- 相手が止まったとき、接続は最大 10〜20 秒借りられたまま（F2: A の形）。その間、ほかの要求は残りの本数と借りる待ち 3 秒で失敗する（reliability-design.md の 5節）。

## 5. 長い文字とメモリ（NFR2.5、R-03 は Q6: A）

### 5.1 受け取る量

- 選ぶ列を `SUBSTRING(列, 1, 上限 + 1)` にする。上限 + 1 は、一覧が 101、詳細が 10,001 コードポイント。T4 で、コードポイントで数えることを確かめた。
- 「上限 + 1」字が返ったら、上限で切って `truncatedColumns` に入れる。
- BINARY と、BINARY と同じ扱いの型（F3: A）は、選ばない。

### 5.2 ヒープの見積もり（T5 で裏付けた）

| 部分 | 1要求 | 10 本同時 |
|---|---|---|
| 一時の割り当て（JDBC の読み・文字の切り詰め） | 約 18MB | 約 180MB（若い世代で回収される） |
| 結果として残る量（行の値の文字） | 約 2.3MB | 約 23MB |
| アプリの DTO と変換の上乗せ（見立て。結果と同じ量を2倍まで） | 約 2.3〜4.6MB | 約 23〜46MB |
| JSON の書き出し | ほぼ 0（流しながら書く） | ほぼ 0 |

- 合わせると、残る量は最大で約 70MB で、最大ヒープ 1g（上限 2g の 50%）に対して小さい。
- 下書きの見積もり（1要求 6〜10MB）と比べると、一時の割り当て（約 18MB）は上回り、残る量（約 2.3〜7MB）は下回った。
- 前の Intent の DSL の投入（10 MiB）は、GC の後でも 573〜746MB あった。それと比べて、業務データの一覧のヒープは小さい（前の Intent の学び「大きな入力のヒープは試しで裏付ける」に当たる比べ）。

### 5.3 memory.peak の読み方（R-03 の手当て）

- 合格の線は、承認済みのとおり、アプリのコンテナの `memory.peak` < 2g の 90%。線は緩めない。
- `bizSearchWide` を流す前に、アプリのコンテナを作り直して前の最大を消す。
- そのうえで、次の3つを並べて記録する。
  - 場面の前の基準の値（作り直した直後のアイドル）の `memory.peak` と、`memory.stat` の `anon`
  - 場面の後の `memory.peak` と、`anon` の最大
  - 増えた分（U1 の寄与）
- 前の Intent の dslMixed のように、ページキャッシュの分で線を超えたときは、内訳を記録して依頼者に諮る（物差しは変えない）。

## 6. 上流との差

- 下書きの「1要求 6〜10MB」を、T5 の実測をもとに 5.2 の値に改めた。承認済みの NFR 要件の目標（memory.peak の線）は変えていない。
- R-03 の「基準の値と並べる」は、Q6: A で取り込んだ。線は緩めていない。
- NFR2.4 の合否を測る対象DB の設定を、表が入る大きさにする（F1: A）。物差し（p95 < 1 秒・待ちの時間切れ 0・503 の件数 0）は変えず、測る環境の設定を決めて記録する差。
- 機能設計の BR7.1 との差（MySQL・MariaDB の `bit` と空間の型を BINARY と同じ扱いにする、F3: A）は、reliability-design.md の 6節に書いた。

## 7. 後の段に回すこと

- **Infrastructure Design・Performance Validation（F1: A）**:
  - 使い捨ての対象DB の設定の値は、`bizPrepare` の後に表の大きさ（データと索引）を実測して決める。MySQL・MariaDB はバッファープール、PG は共有バッファー。
  - `docker/perf/compose.yaml` の対象DB のメモリの上限（今は 512m・768m・512m）とあわせて決める。
  - colima の VM（CPU 4・6GiB）で、アプリ（2g）と対象DB を同時に動かせるかを、配備したアプリを止める前提で確かめる。
  - 決めた値は、Performance Validation の記録に書く。
- **Performance Validation（F1: A）**:
  - 今回の試しの値（performance-design.md の冒頭の2つの表と、5.2 の値）を、基準として並べて記録する。
  - NFR2.4・NFR2.5 の合否の回を行い、5.3 のとおり記録する。表が入る設定でも超える場面は Not Met として、その段で扱いを諮る。目標は緩めない。
- **コード生成の計画（B2）**:
  - `BusinessDataConnectionUsageIT` を、U2 と1つのクラスにする。
  - `bizPrepare` と `bizSearchWide` の台本を書く。

## 8. U2・U3 とそろえる点

- `BusinessDataConnectionUsageIT` は1つのテストのクラスにする。U1 の3行（定義・一覧・詳細）と U2 の3行（作成・更新・削除）を、表にまとめる。U2 は、書き込みの接続の列を足す。
- U2 の書き込みのプールの見積もりも、同じ物差しにする（待ちの時間切れの累計 0・503 の件数 0、待ちの最大は記録だけ）。上限に届く場面 `bizWritePoolLimit` も置く。対象DB の設定は、F1: A の同じ値で測る。
- U3 の長い値の省略の字数は、一覧 100 字・詳細 10,000 字（コードポイント）に合わせる。

## Sources

- 承認済みの NFR 要件: `aidlc/spaces/default/intents/261016-business-data-views/construction/u1-businessdata-read/nfr-requirements/scalability-requirements.md`（NFR2.1〜NFR2.5）
- NFR 要件のレビュー: `.aidlc-reviews/nfr-requirements/units/u1-businessdata-read/00d44a7a2452e6b7/1.json`（R-03）
- この段の答え
  - `construction/u1-businessdata-read/nfr-design/nfr-design-questions.md`（Q5・Q6: A）
  - `construction/u1-businessdata-read/nfr-design/nfr-design-followup-questions.md`（F1: A）
- コード
  - `backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDbProperties.java`（プールの既定 5）
  - `backend/src/main/java/cherry/mastersmith/role/service/EffectivePermissionResolverImpl.java`（写しの読み取り）
  - `Dockerfile`（`MaxRAMPercentage=50`）
  - `docker/perf/compose.yaml`（アプリ 2g、対象DB 512m・768m・512m）
- 決まり: `aidlc/spaces/default/memory/project.md`
  - 大きな入力のヒープの見積もりは、試しで裏付ける
  - memory.peak を比べる前に、コンテナを作り直す
  - dslMixed の memory.peak の持ち越し
- 捨ての試し: リポジトリの外の試しのコードと結果（T4・T5・T6・F2）

## Assumptions & Open Questions

- **依頼者の決定（F1: A）**: プールの大きさとアプリの作りは変えない。Performance Validation の使い捨ての対象DB を、表が入る設定にして記録してから測る。それでも超える場面は Not Met として、その段で扱いを諮る。目標は緩めない。
- 前提: アプリの DTO と変換の上乗せは、結果と同じ量を2倍までとみなした（試しでは、JDBC の読みと JSON の書き出しだけを測った）。Performance Validation の `bizSearchWide` で確かめる。
- 前提: 表が入る設定にすると、対象DB のコンテナのメモリの上限が増える。そのため、colima の VM のメモリに収まるかは Infrastructure Design で確かめる。
