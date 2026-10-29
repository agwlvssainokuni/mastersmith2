# User Stories — 質問（Intent 261029-reference-lookup-cleanup）

要件（`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`）の FR1〜FR8 を、利用者の言葉のストーリーと受け入れ基準にします。判断が分かれる点だけを尋ねます。

## 決まっていること（質問しません）

- ストーリーは INVEST で書き、受け入れ基準は Given/When/Then。ID は `US{群}.{番号}`・`AC{群}.{番号}.{基準}`。
- 要件の ID（FR・NFR）は、`traceability.json` ですべて「ストーリーで受け持つ」「後の段へ回す」「ストーリーにしない（理由つき）」のどれかにする。
- 優先度は MoSCoW。最小の範囲（MVP）の線は Delivery Planning で決める。
- 前の Intent（261016-business-data-views）の P1（業務データを見る人）・P2（業務データを直す人）・P3（管理者）を引き継ぐ。

## Q1: ペルソナ
今回の機能は、フォームで参照先を選ぶ人（P2 直す人）と、権限を欠く人（P1・P2 の別の立場）が中心です。持ち越しの片付け（JWT の伏せ字・コンテナ・perf の文書）は、利用者ではなく、このアプリを動かして測る人のための仕事です。

A. 前の P1・P2・P3 を引き継ぎ、運用・性能の測定を受け持つ人（P4）を新しく足す
B. 前の P1・P2・P3 だけで、持ち越しの片付けは P3（管理者）のストーリーにする
X. Other (please specify)

[Answer]: A

## Q2: ストーリーの分け方
要件の FR は、参照の選択肢（FR1）・ピッカー（FR2）・保存前の確かめ（FR3）・一覧と詳細（FR4）・持ち越し（FR5〜FR8）です。

A. 機能のまとまりごとに群を分ける（群1 参照の選択肢、群2 参照ピッカー、群3 保存前の確かめ、群4 持ち越しの片付け）
B. 人（ペルソナ）ごとに群を分ける
X. Other (please specify)

[Answer]: A

## Q3: 優先度（MoSCoW）
A. 参照の選択肢・ピッカー・保存前の確かめ・JWT の伏せ字を Must、コンテナの版そろえ・perf の文書の整理・Dependabot を Should にする
B. すべて Must にする
X. Other (please specify)

[Answer]: A

## Q4: 受け入れ基準の細かさ
要件には、境界の値（200 件ちょうどは選択肢を返し、201 件は誤り）、権限を欠く人の扱い、値が変わった参照だけを確かめる更新などがあります。

A. 要件の境界と失敗の場合を、受け入れ基準に1件ずつ書く（機能設計・テストにそのままつながる）
B. 代表の流れだけを書き、境界と失敗の場合は機能設計の段で細かくする
X. Other (please specify)

[Answer]: A

## Q5: 持ち越しの片付け（JWT の伏せ字・コンテナ・perf の文書・Dependabot）の扱い
A. JWT の伏せ字・コンテナの版そろえ・perf の文書の整理は P4 のストーリーにする。Dependabot の取り込みは、決まりが既にあるためストーリーにせず、理由をつけて対象外（N/A）にする
B. 4つともストーリーにする
C. 4つともストーリーにせず、理由をつけて対象外（N/A）にし、後の段（単位の分割・コード生成）で扱う
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- Q1: ペルソナは前の P1（業務データを見る人）・P2（直す人）・P3（管理者）を引き継ぎ、運用・性能の測定を受け持つ P4 を新しく足す
- Q2: ストーリーは機能のまとまりごとに4群に分ける（群1 参照の選択肢、群2 参照ピッカー、群3 保存前の確かめ、群4 持ち越しの片付け）
- Q3: 優先度は、参照の選択肢・ピッカー・保存前の確かめ・JWT の伏せ字を Must、コンテナの版そろえ・perf の文書の整理を Should（Dependabot は対象外）
- Q4: 受け入れ基準に、要件の境界と失敗の場合を1件ずつ書く
- Q5: JWT の伏せ字・コンテナの版そろえ・perf の文書の整理は P4 のストーリーにする。Dependabot の取り込みは、決まりが既にあるため、理由つきで対象外（N/A）にする

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
