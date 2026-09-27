# Entities — U8 インスタンスの見た目の設定（u8-instance-appearance）

U8 には、アプリが独自に持つデータが無い。ブランドカラーとフォントファミリーは、アプリの設定（`application.yaml` と環境変数）から受け取る設定値であり、内部DB に保存せず、利用者の操作で変わることもない。設定値はエンティティにせず、求める振る舞い（読み取り・既定への置き換え・警告・公開の API）を `rules.md` の決まり（BR1.x〜BR3.x）として書く（`aidlc/spaces/default/memory/project.md` の Code Style、`aidlc/spaces/default/intents/260925-user-management/inception/units-generation/unit-of-work.md` の U8 の注意、`components.md` の InstanceAppearance の `entities: []`）。

```yaml
entities: []
# 理由:
#   - ブランドカラー（mastersmith.appearance.brand-color）とフォントファミリー（mastersmith.appearance.font-family）は
#     フレームワークの設定の仕組みが提供する設定値で、アプリが独自に持つデータではない（project.md の Code Style）。
#   - 内部DB の表・列を作らない。Flyway の移行も無い。
#   - 起動時に解決した値（ブランドカラーの名前とフォントファミリーの名前の組）はメモリの中に保持するだけで、
#     識別子・生存期間・状態の移り変わりを持たない。値の形は契約 C7 の応答（brandColor・fontFamily）がそのまま正であり、
#     許される値と解決の決まりは rules.md の BR1.1〜BR1.7 に書く。
```

## 要約

- エンティティは 0 件。U8 は内部DB に触れず、`domain`・`repository` の層を作らない。
- 扱う値は2つの設定値だけで、許される値はブランドカラーが blue・green・purple・orange、フォントファミリーが sans・serif（要件 FR8.1）。既定は blue・sans（FR8.2）。
- 起動時に解決した2つの名前の組を保持し、公開の API（契約 C7）で返す。値の形は契約 C7 の応答の形と同じで、別のデータの形は定義しない。
