# Code Generation の質問 — U5 u5-carryover-tests（B1）

計画（`code-generation-plan.md`）の「依頼者に確かめたいこと」の答えを先に決め、計画に反映してから Plan Approval に進みます。

## D1. 入れ替えた順の場合に、後の保存が 409 `ROLE_BUSY` で返ったときの扱い

A. 待ち合わせの結果の確かめを 409 の分岐より前に置き、409 は「入れ替えが成り立たなかった」としてテストを失敗にする。409 で落ちたときは、team.md の不安定なテストの決まりで扱う（推奨）

B. 409 なら、自然の順と同じく code だけを確かめて通す（その回は入れ替えを確かめないまま合格に数える）

X. Other (please specify)

[Answer]: A

## D2. 自然の順の場合の作り方

A. 自然の順も、同じ待ち合わせで確実に作る（止める相手と放す相手を入れ替えるだけ）。2つの場合が毎回同じ順になる（推奨）

B. 自然の順の場合は待ち合わせを準備せず、順を問わない。どちらの順になったかを記録するだけ

X. Other (please specify)

[Answer]: A

## D3. `perf/` の台本の形

A. 別のファイルにする。確かめの関数（`perf/lib/perf-db-url.sh`）・その試しの台本・1回の測り（`perf/dsl-mixed-memory.sh`）。悪い入力の試しをいつでも流し直せる。ファイルが3つ増える（推奨）

B. ファイルを増やさない。確かめの関数は `dsl-timing.sh` の中に置く。試しは一度だけ手で流し、記録のループは README の手順の中で書き出す

X. Other (please specify)

[Answer]: A

## D4. 試しの回の `RETENTION_TIME` の値

A. `0`。効くかどうかを最も大きく見られる（推奨）

B. `5000`（5 秒）。取り込むときに近い値で測れるが、効かなかったときに原因を見分けにくい

X. Other (please specify)

[Answer]: A

## D5. 記録のループの空回し

A. この段で、配備したアプリのコンテナに向けて、記録のループだけを 5 秒流す（cgroup のファイルを読むだけで、止めない・書かない）。列が正しいことを確かめてから Build and Test に渡す（推奨）

B. 流さない。Build and Test の最初の回で確かめる

X. Other (please specify)

[Answer]: A

## Plan Approval

`code-generation-plan.md`（埋め込みの Testing Contract を含む）と `unit-test-instructions.md` の承認です。D1〜D5 の答えは計画に反映済みです。

[Approval Fingerprint]: sha256:v3:baa3a2af27ce4456466eb1e00b50645c96007c439757dddd9a853056f596652e
[Planned Source]: a0bf502e33e918b06115c31c6cc2e14e97b2ec62957ef5d2c6d54ede5bc17cd6

- Approve Plan
- Request Changes

[Answer]: Approve Plan
