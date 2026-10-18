# 技術の選択 — U2 u2-businessdata-write

## 出典

- コード知識ベース: `aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`
- この単位の機能設計: `construction/u2-businessdata-write/functional-design/`（`rules.md` の BR4.2・BR9.1〜BR9.3、`functional-spec.md` 4節）
- ADR-007・ADR-008: `inception/domain-design/decisions.md`
- U1 の技術の選択: `construction/u1-businessdata-read/nfr-requirements/tech-stack-decisions.md`
- この段の答え: `construction/u2-businessdata-write/nfr-requirements/nfr-requirements-questions.md`
  - Q2: A
  - まとめの確認の「新しい依存は無い」「pattern の打ち切りは自前の包み」に Looks correct
- コード:
  - `dsl/validate/PatternChecker.java`: 組み立てを別のスレッドで待つ今の形。照合の打ち切りではない
  - `targetdb/config/TargetDataSourceConfig.java`
- `team.md` の Code Style: 新しい依存は採用の前にライセンスを確かめる。取得元は Maven Central だけ

## ID の対応表

- この文書は要件の ID を持たない。
- どの要件がどの技術を使うかは、下の表の「使う要件」の列に書く。
- 全体の表は `security-requirements.md` の「ID の振り方」にある。

## 決め

**新しい依存は足さない。** この単位の要件は、採用済みの部品と自前の小さな部品で満たす。

| 用途 | 使う部品 | 状態 | 使う要件 |
|---|---|---|---|
| 書き込みの接続のプール | HikariCP（Spring Boot の管理の版） | 採用済み。既定の候補にしない名前つきの別のプールを足す。読み取り専用にしない | NFR2.7・NFR4.3 |
| 対象DB 用のトランザクションの管理者 | Spring の `DataSourceTransactionManager` | 採用済みの部品。名前つきで、内部DB の管理者の既定の候補にしない | NFR4.3・NFR4.12 |
| 書き込みの文 | JDBC と3つのドライバー（PostgreSQL・MySQL・MariaDB） | 採用済み。書き込みの接続では、件数を一致した行にする設定と通信の待ち（上限 + 5 秒）を当てる | NFR3.5・NFR4.1・NFR4.4 |
| SQL の識別子の引用 | 既存の `SqlIdentifiers.quoteKnown` | 自前の部品を使い回す | NFR4.1 |
| pattern の照合の打ち切り | 締め切りを見る `CharSequence` の包み（自前）と、標準の `java.util.regex` | 新しく書く小さな部品。別のスレッドを使わない | NFR4.7・NFR1.6 |
| 本文の上限 | 既存の `RequestBodyLimitRoute` と `RequestSizeLimitFilter` | 自前の仕組みに道を3つ足す | NFR4.9 |
| 監査 | 既存の `AuditEventListener`（`fallbackExecution`）と `AuditEvent` | 自前の仕組みに種類・理由の値・列を足す | NFR4.13・NFR4.14・NFR8.4 |
| 3種類の対象DB の結合テスト | Testcontainers（版を固定したイメージ） | 採用済み | NFR3.5・NFR3.6・NFR4.12・NFR7.5 |
| 性質ベースのテスト | jqwik 1.10.2 | 採用済み | NFR7.6 |
| 構造の検査 | ArchUnit | 採用済み。`BusinessDataBoundaryArchitectureTest` に書き込みの側を足す | NFR4.1 |
| 静的解析 | SpotBugs ＋ FindSecBugs | 採用済み。`SQL_` の除外を書き込みのメソッドに3つの名前で絞って広げる | NFR4.1 |
| 接続の数を数える | U1 と同じテスト用の包み（`businessdata/testsupport`） | U1 の部品に書き込みの DataSource の列を足す | NFR2.6 |
| 負荷の試験 | k6・`perf/` の台本・`docker/perf/compose.yaml` | 採用済み。書き込みの4場面を足す | NFR1.4・NFR1.5・NFR2.7〜NFR2.9・NFR3.4 |
| 手元の監視 | grafana/otel-lgtm | 採用済み。ダッシュボードのファイルに区画を足す | NFR8.5・NFR8.6 |

pattern の打ち切りの包みの形（説明用の断片。名前と間隔は NFR 設計で決める）:

```java
record DeadlineCharSequence(CharSequence value, Clock clock, Instant deadline) implements CharSequence {
    @Override
    public char charAt(int index) {
        if (!clock.instant().isBefore(deadline)) {
            throw new PatternDeadlineExceeded(); // 照合の外で PATTERN_TIMEOUT に変える
        }
        return value.charAt(index);
    }
    // length・subSequence・toString（値を伏せる）は省略
}
```

## ライセンスの確かめと採らなかった案

**ライセンスの確かめ**: 足す依存が無いため、新しい確かめは無い。

**採らなかった案**

| 案 | 採らなかった理由 |
|---|---|
| 外部の正規表現の部品（RE2J など。線形時間で、打ち切りが要らない） | 依存とライセンスの確かめが増える。DSL の pattern（Java の正規表現の文法）と書き方が違いうる。NFR 設計の試しで包みの形が駄目だったときだけ、ライセンスと依存の木を確かめて依頼者に諮る |
| 照合を別のスレッドで動かし、待ちを打ち切る（今の `PatternChecker` の形） | 打ち切った後もスレッドが計算を続け、10 VU の重い式で CPU とスレッドが積み上がる（BR4.2 の「別のスレッドを残さない」） |
| jOOQ などの SQL の組み立ての部品 | SQL を1つの部品に閉じる決まりと、ArchUnit の絞り込みを崩す（U1 と同じ理由） |
| 楽観的な排他の部品 | 要件の範囲の外（FR7.2） |

## 上流との差

- **承認の場の決定**（`nullable` は U2 が足す、`formPart` から `lookup` を外す、`required`・`nullable` はすべてのカラムで返す）
  - 技術の選択に差は生まない。
- **R-08**（DB の種類つきの書き込みの型）
  - 採用済みの部品の使い方の話で、新しい依存は要らない。
- **ADR-007 の pattern の切り替え先**（照合の前に長さの上限を当てる）
  - この段では切り替え先にとどめず、照合の打ち切りと合わせて先に当てる（`security-requirements.md` の NFR4.7）。
- **NFR1.4 の番号**
  - U1 の文書は「U3 は NFR1.4 以降」としている。この差は `performance-requirements.md` に書いた。

## 後の段に回すこと

- **NFR 設計の試し**
  - 締め切りを見る包みの形と、時計を読む間隔（何文字ごと）
  - `(a+)+$` で打ち切れること
  - 名前つきの書き込みの部品が、内部DB の型の注入と衝突しないこと
  - 書き込みの接続のドライバーの設定（件数・通信の待ち）
  - 駄目なときに新しい部品が要るなら、その時点でライセンスを確かめて依頼者に諮る
- **Performance Validation**: k6 と使い捨ての環境を使う。
- **Observability Setup**: ダッシュボードのファイルを直す。
- **基盤の設計**: 書き込みの接続の環境変数と、見本の書き込みのアカウントを用意する（新しい道具は入れない）。

## U3 とそろえる点

- **番号**: U3 は NFR1.7・2.10・3.7・4.20・7.7・8.7 から振る。
- **部品**: U3 の画面の部品は make-you-chic-ui と採用済みの道具を使い、U2 の決めとは独立である。
- **定義の項目**: 平らに置く。`required`・`nullable` は必ず値を持ち、`formPart` に `lookup` は来ない。
- **値の長さ**: 上限 10,000 字を、画面の `maxLength` にも当てる。U3 の D19 は画面だけの守りになる。
- **pattern**: 画面では当てない（式は返らない）。`PATTERN_TIMEOUT` と 413 の文は画面が用意する。
- **503 の出し分け**: `BUSINESS_TARGET_DB_TIMEOUT` は「保存されたか分からない」、`UNAVAILABLE` は「保存されていない」として出す。
- **E2E**: 代表の流れ1本まで。U2 は E2E を足さない。
