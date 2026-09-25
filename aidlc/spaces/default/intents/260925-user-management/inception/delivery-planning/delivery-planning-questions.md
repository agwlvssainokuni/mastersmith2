# Delivery Planning の質問

単位（`aidlc/spaces/default/intents/260925-user-management/inception/units-generation/unit-of-work.md`、8 単位）と依存の図（`unit-of-work-dependency.md`）から、作る順を決めます。ここでの Bolt は、1つ以上の単位をまとめて作り終え、動くものができる1回の作る区切りのことです。

決まっていること（質問にしない）:
- 単位は1つずつ作る（並行して作らない。Units Generation の Q3: A）。
- 最初の Bolt だけを単独で動かして承認を待つ特別な手順（walking skeleton の儀式）は行わない。アプリの骨格はすでにある（`team.md` の Walking Skeleton）。
- Bolt の作業ブランチは `develop` から作り、`develop` へ squash で戻す。サブモジュールの固定先の更新を含む変更だけは fast-forward で戻してよい（`team.md` の Way of Working）。U1 は java-mustache-processor のサブモジュールを足すため fast-forward になる見込み。
- 依存がほぼ一本道で順の選択肢が少ないため、点数付けの方法（価値と急ぎと大きさで点を付ける WSJF など）は使わない（前の Intent と同じ判断）。
- 作るのは依頼者と AI の2人で、チームは1つ（`team.md`）。

---

## Q1. 何から作るか

依存の図の上で最初に作れるのは U1（メール）・U2（利用者のプリファレンス）・U8（見た目の設定）です。

A. 危険の大きいものを先にする: U1（java-mustache-processor のサブモジュールと composite build の取り込みは前例が無く、満たせなければ Maven Central への公開に切り替える必要がある）を最初に作り、次に U2・U3（招待と登録の完了の中心）、その後に U8・U4・画面（U5・U6・U7）
B. 招待から登録までの一本を先に通す: U1 → U2 → U3 → U8 → U4 → U6（登録の完了の画面）→ U5（招待の管理の画面）→ U7 の順にし、招待から登録の完了までの流れを早く動かす
C. 手早く価値が出るものを先にする: U2 → U8 → U4 → U7（プリファレンスの画面）で表示の設定を先に仕上げ、その後に U1 → U3 → U5 → U6
X. Other (please specify)

[Answer]: A

## Q2. 1つの Bolt の大きさ

A. 1つの単位を1つの Bolt にする（8 Bolt。統合のたびに1コマンドの検査を通す）
B. 関係の深い単位をまとめる（例: U1・U3 の招待とメール、U2・U7 の利用者の設定と画面、U8・U4 の見た目と表示の土台、U5・U6 の招待と登録の画面 の 4 Bolt）
X. Other (please specify)

[Answer]: B

## Q3. 設計と作る作業の進め方

設計の段（機能設計・NFR 要件・NFR 設計・基盤の設計）とコード生成を、単位に対してどう回すかです。

A. 設計の段ごとにすべての単位を通し、コード生成を最後にまとめて行う（今までの Intent と同じ。設計どうしの食い違いを先に見つけやすいが、動くコードは最後に出る）
B. 1つの単位の設計からコード生成までを終えてから、次の単位に進む（動くコードが早く出て Bolt の順に作れるが、後の単位の設計で前の単位の直しが要ることがある。各段の承認は最後にまとめて出る）
X. Other (please specify)

[Answer]: A

## Q4. 作る体制

A. この会話の中で1つずつ作り、依頼者が段ごとに承認する（1人のチーム）
B. 単位ごとに別のチームが持ち、それぞれが承認する（複数のチーム。Q3 で B が要る）
X. Other (please specify)

[Answer]: A

---

## 追加の質問

## F1. Bolt のまとめ方と順の食い違い

Q2 B の例のまとめ方（U1・U3、U2・U7、U8・U4、U5・U6）は、依存の図の上で成り立ちません。U3 は U2 に依存し、U7 は U4 に、U4 は U2 と U8 に依存するため、「U2・U7」と「U8・U4」はお互いを先に要し、どちらからも始められません。また Q1 A（U1 を最初）とも合いません。Q1 A の順を保ったまま、関係の深い単位をまとめる形を選んでください。

A. 5 Bolt: B1 U1（メールと取り込み。危険を最初に確かめる）→ B2 U2（利用者の設定）→ B3 U3（招待と登録の完了）→ B4 U8・U4（見た目の設定と表示の土台）→ B5 U5・U6・U7（3つの画面）
B. 4 Bolt: B1 U1・U2（土台のバックエンド2つ）→ B2 U3 → B3 U8・U4 → B4 U5・U6・U7
C. 1つの単位を1つの Bolt にする（8 Bolt。Q2 A に変える）
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめ:

- Q1 A と F1 A: 危険の大きいものを先にし、5 つの Bolt に分ける
  - B1 U1 メールの描画と送信（java-mustache-processor のサブモジュールと composite build の取り込みを最初に確かめる。満たせなければ Maven Central への公開に切り替える）
  - B2 U2 利用者のプリファレンスとパスワードの変更
  - B3 U3 招待と登録の完了
  - B4 U8・U4 見た目の設定と表示の土台
  - B5 U5・U6・U7 招待の管理・登録の完了・プリファレンスとパスワードの変更の画面（招待から登録の完了までの E2E を含む）
- Q2 B: 関係の深い単位をまとめる（F1 A の形）
- Q3 A: 設計の段ごとにすべての単位を通し、コード生成を最後にまとめて行う（今までと同じ）
- Q4 A: この会話の中で1つずつ作り、依頼者が段ごとに承認する（1人のチーム）
- 決まっていること: 並行して作らない、特別な骨格の手順は行わない、点数付けの方法は使わない、統合は squash（B1 はサブモジュールを足すため fast-forward の見込み）

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
