# 拡張性の要件 — U2 u2-businessdata-write

## 出典

- 要件: `aidlc/spaces/default/intents/261016-business-data-views/inception/requirements-analysis/requirements.md`
  - NFR2
  - FR12（監査）
  - FR16（`memory.peak` の物差し）
- ストーリー: `inception/user-stories/stories.md`
  - AC3.6.6・AC3.6.7
- この単位の機能設計: `construction/u2-businessdata-write/functional-design/`
  - `rules.md` の BR6.2・BR9.3・BR9.4・BR11.3
  - `functional-spec.md` 8節・11.2（R-04）
- ADR-008: `inception/domain-design/decisions.md`（対象DB のプールが2つになる）
- この段の答え: `construction/u2-businessdata-write/nfr-requirements/nfr-requirements-questions.md`
  - Q3・Q4・Q5・Q6 はすべて A
  - まとめの確認は Looks correct
- U1 の NFR 要件: `construction/u1-businessdata-read/nfr-requirements/scalability-requirements.md`
  - NFR2.1〜NFR2.5
  - 「U2・U3 とそろえる点」
- コード:
  - `backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDbProperties.java`（読み取りのプールの既定は 5）
  - `backend/src/main/resources/application.yaml`（内部DB のプールは 30）
  - `audit/domain/AuditEvent.java`（`detail` は 16,384 字まで）
  - `docker/perf/compose.yaml`（アプリの上限は 2g）
- `project.md` の学び:
  - 接続プールの見積もりは、上限に届く形で確かめる
  - 大きな入力のヒープの見積もりは、試しの測定で裏付ける
  - 要求1件で接続を2本使う経路は、負荷の試験で確かめる

## ID の対応表

全体の表は、`security-requirements.md` の「ID の振り方」にある。

| 上流の ID | この単位での扱い | この単位の ID |
|---|---|---|
| NFR2 規模 | 当たる | NFR2.6〜NFR2.9 |

## 要件

### NFR2.6 1要求で使う接続の数

**要件**

1要求で同時に借りる数の最大を、作成・更新・削除のどれも次のとおりとする（BR9.3・BR9.4）。

| 接続 | 本数 |
|---|---|
| 内部DB | 1 |
| 対象DB（書き込み） | 1 |
| 対象DB（読み取り） | 0 |

あわせて次を守る。

- 書き込みの接続を借りている間は、内部DB の接続を 0 本にする。
- 次の3つは、借りた1本の中で行い、書き込みの口を2回呼ばない。
  - 事前の問い合わせ
  - 書き込みの文
  - 違反の後の確かめ
- 監査は口から戻った後に、内部DB の1本で書く。

**確かめ方**

- U1 と1つにまとめた `BusinessDataConnectionUsageIT` に、次の3行を足す。
  - 作成（違反の後の確かめの経路を含む）
  - 更新
  - 削除
- 書き込みの DataSource を数える列を足す。
- 次を確かめる。
  - 要求の始まりに借りている数が 0 であること
  - 借りている数の最大が上の表と一致すること
  - 書き込みの接続を借りている間、内部DB を借りている数が 0 であること

**持ち主の段**: Code Generation（B4）

### NFR2.7 書き込みの接続のプール

**要件**

- 書き込みのプールの既定を 5 とし、環境変数で変えられるようにする（読み取りと同じ形。Q4: A）。
- 対象DB の側の接続は、読み取り 5 + 書き込み 5 で最大 10 本になる。
- 10 VU の場面は、上限の 5 に必ず届く。
- 合格の条件は次の3つとする。
  - 各場面の p95 < 1 秒（`performance-requirements.md` の NFR1.4・NFR1.5）
  - 書き込みのプールの待ちの時間切れの累計が 0
  - 503 の件数が 0
- 借りるまでの待ちの最大は、記録だけにする。

**確かめ方**

- 使い捨てのアプリにだけ `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics` を渡す。
- 書き込みのプールの `hikaricp` の値（待ちの時間切れの累計・待ちの最大）を、場面の前後で読む。
- 上限に届く形の確かめは、`bizWritePoolLimit`（最も長く接続を持つ違反の経路）で行う（Q6: A）。

**持ち主の段**: Performance Validation。満たせないときは、上限を 10 に上げる案を依頼者に諮る。

### NFR2.8 大きな本文のメモリ

**要件**

- 本文の上限（4 MiB。`security-requirements.md` の NFR4.9）に近い作成の要求を、10 VU で流したときの値を確かめる。
  - アプリのコンテナの `memory.peak` が、上限 2g の 90% 未満であること（FR16 と同じ物差し）。
  - `memory.stat` の `anon` の最大も並べて記録する。
- 見積もりは、NFR 設計の段で試しの測定で裏付ける（`project.md` の学び）。

**確かめ方**

- 場面の前に、アプリのコンテナを作り直して、前の最大を消す。
- 場面は `bizWritePoolLimit` の本文を大きくした回か、専用の短い回にする（形は NFR 設計で決める）。

**持ち主の段**: NFR 設計（試し）、Performance Validation

### NFR2.9 監査の行の増え方と、負荷の試験の行

**要件**

監査の行の増え方を見積もり、記録する。見積もりの仮定は、監査の行1つが約 0.5 KB（主キーが短いとき）であること。

- ふつうの使い方: 50 名 × 1日 200 回 で、約 1万行・約 5 MB/日。
- 主キー・ビューの 409 の監査を、補助権限を持つ人だけに限る（Q5: A、`security-requirements.md` の NFR4.14）。そのため、主権限が READ だけの人がスクリプトで繰り返し送っても、監査の行は増えない。
  - 限らなかった場合の見積もり: 50 回/秒で、約 430 万行・約 2 GB/日。
- 補助権限を持つ人の繰り返しの失敗（重なり・参照先に無い値）は残る。これは、操作できる人の記録として受け入れる。

負荷の試験で書き込む行は、次のとおりにする。

- U1 と同じ `bizPrepare` の表と種を使う。
- 書き込みの場面の setup は、自分で行を作る。値は架空の値だけにする。

**確かめ方**

- 見積もりは、仮定とともに記録する（実測ではない）。
- Performance Validation の後に、使い捨ての環境で次を数え、見積もりと並べて記録する。
  - 監査の行の数
  - 内部DB のファイルの大きさ
- 片付ける前に結果を見る（`project.md`）。

**持ち主の段**: Code Generation（B4）（場面の台本）、Performance Validation（実測の記録）

## 補足

- 内部DB のプール（既定 30）は変えない。業務データの書き込みが内部DB を使うのは、写しの短い読み取りと、監査の短い書き込みだけである。どちらも同時に1本で、続けて行う。
- 要求1件で接続を2本同時に使う経路は無い。そのため、`project.md` の「2本使う経路は負荷の試験で確かめる」に当たる場面は要らない。ただし、上限に届く確かめ（NFR2.7）は行う。

## 上流との差

- 承認の場の決定（`construction/functional-design/memory.md` の Open questions）:
  - `nullable` は U2 が足す。
  - `formPart` から `lookup` を外す。
  - `required`・`nullable` はすべてのカラムで返す。
  - これは定義の応答の項目の話で、この文書の要件に差は無い。
- Q1: A で、事前の問い合わせと確かめを1文にまとめる。そのため、NFR2.6 の1本の中の文の数は最大3つになる（BR5.1・BR6.2 の読み替え）。
- Q5: A で、BR11.3 の残す範囲を狭めた。NFR2.9 の見積もりは、狭めた後の形で書いた。
- U1 の文書が「U3 は NFR1.4 以降」としていることとの差は、`performance-requirements.md` に書いた。拡張性の枝番は、U1 の「NFR2.6 以降」に従う。

## 後の段に回すこと

- **NFR 設計の試し**
  - 4 MiB の本文 × 10 VU のヒープの見積もり（NFR2.8）。
  - 1文にまとめた事前の問い合わせの重さ。
- **Performance Validation**
  - NFR2.7・NFR2.8 の合否の回。
  - NFR2.9 の監査の行の数と内部DB の大きさの記録。
- **Observability Setup**: 書き込みのプールの待ちの区画（`observability-requirements.md` の NFR8.5）。
- **基盤の設計**: 書き込みのプールの大きさの環境変数を、`.env.example` と README に書く。

## U3 とそろえる点

- 番号: U3 は NFR1.7・2.10・3.7・4.20・7.7・8.7 から振る。
- 定義の項目: 平らに置く。`required`・`nullable` は必ず値を持つ。`formPart` に `lookup` は来ない。
- 値の長さの上限: 10,000 字を、画面の入力の `maxLength` にも当てる。U3 の D19 は、画面だけの守りになる。
- 本文の上限: 413（4 MiB を超えた本文）の文を画面が用意する。画面で先に大きさを確かめるかは、U3 で決める。
- pattern: 画面では当てない。`PATTERN_TIMEOUT` の文を用意する。
- 503 の出し分け: `BUSINESS_TARGET_DB_TIMEOUT` は「保存されたか分からない」、`UNAVAILABLE` は「保存されていない」とする。
- E2E: 代表の流れ1本まで。U2 は E2E を足さない。
