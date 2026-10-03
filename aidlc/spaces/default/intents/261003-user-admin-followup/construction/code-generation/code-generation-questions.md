# Code Generation の質問（261003-user-admin-followup）

計画（`code-generation-plan.md`）の 4節「依頼者に確かめたいこと」D1〜D4 の答えを、計画の承認の前に記録します。

## Question D1
S1 の直し方（FR8.2、Q1: D）。計画の段の確かめで、重なったメールアドレスが既定の INFO のアプリのログ（ロガー `org.hibernate.orm.jdbc.error` の WARN）に出ました。

A. 案 A: ログの設定で `org.hibernate.orm.jdbc.error` を `OFF` にする（アプリのコードは変えない）
B. 案 B: 違反を受ける所でクラスの名前だけを出す（これだけでは確かめた漏えいは止まらない）
C. 案 C: A と B の両方（推奨）
X. Other (please specify)

[Answer]: C

## Question D2
T1 の合否の基準と負荷の形（計画 7節: 上限 4・同時 12・5 分、時間切れの累計 1 以上または待ちの最大 1,000 ms 以上、BUSY の L3・L4 の結び付き、鳴らなかったときの扱い）でよいですか。

A. 計画の値でよい（推奨）
B. 値を変える（Other に書く）
X. Other (please specify)

[Answer]: A

## Question D3
FR1 の再現の確かめ（固定先を 3d9521a に戻して新しい E2E の確かめが落ちることを見る）を行いますか。

A. 行わない。前の Intent のスモークテストと make-you-chic-ui の報告を根拠にする（推奨）
B. 行う
X. Other (please specify)

[Answer]: A

## Question D4
固定先の更新は専用のコミット（C1）、FR1 の回帰のテスト（E2E のフォーカスの確かめ）は画面の側の直しと同じ C2 に入れて C1 の直後に置く扱いでよいですか。

A. この扱いでよい（推奨）
B. 回帰のテストも C1 に入れる（専用のコミットに固定先以外を含める）
X. Other (please specify)

[Answer]: A

## Plan Approval

`code-generation-plan.md`（埋め込んだ Testing Contract を含む）と `unit-test-instructions.md` のとおりにコードを生成してよいですか。

[Approval Fingerprint]: sha256:v3:5d2a68f73920f19007012437ec23036bf28a11bfdb4634d4fd886aff234a20c6
[Planned Source]: eebd29087310657be26686e6cefaf92198725f944fdf78618030dfecc4900e90

- Approve Plan
- Request Changes

[Answer]: Approve Plan
