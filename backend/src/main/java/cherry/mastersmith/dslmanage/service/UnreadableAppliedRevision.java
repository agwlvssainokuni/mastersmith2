/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cherry.mastersmith.dslmanage.service;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * 起動時に今の書式で読めなかった適用中の版の ID の置き場（メモリ。U2 dsl-v2 の BR3.6 の差、NFR 設計の 4.8・R-06、Q1: A）。
 *
 * <p>使い方と守りの決まり:
 *
 * <ul>
 *   <li>書き換える（{@link #remember(UUID)}・{@link #clear()}）のは {@link DslStartupLoader} だけ。起動時に読めなかったら覚え、
 *       読めた・適用中が無いときは空にする。口はこのパッケージの中だけで使え、ほかの機能からは呼べない。
 *   <li>{@link DslLifecycle} は判定（{@link #isUnreadable(UUID)}）だけを使い、今の状態の {@code appliedUnreadable} にする。
 *   <li>新しい版を適用すると適用中の版の ID が変わるため、置き場を消さなくても判定は偽になる。適用の確定から適用中のモデルの差し替え
 *       までの間に読んでも、誤って真にならない。
 *   <li>内部DB には保存しない（再起動のたびに起動時の読み直しで決め直す）。
 * </ul>
 *
 * <p>あわせて、起動時に読めたが深すぎるメニューの枝を落とした版の ID と落とした項目の数も覚える（Intent 261015-deps-ui-carryover の
 * FR3.2、前の Intent のレビューの dsl-v2 R-08。今の状態の {@code prunedMenuItems} の元）。守りの決まりは読めなかった版と同じで、
 * 書き換えるのは {@link DslStartupLoader} だけ、{@link DslLifecycle} は今の適用中の版の数（{@link #prunedMenuItems(UUID)}）だけを
 * 使う。新しい版は適用の検証で深さを確かめるため、版が変われば数は 0 になる。読めなかった版と落とした版は、同じ起動で両方にはならない
 * （どちらかを覚えると、もう片方を消す）。
 */
@Component
class UnreadableAppliedRevision {

    private final AtomicReference<UUID> revisionId = new AtomicReference<>();

    private final AtomicReference<Pruned> pruned = new AtomicReference<>();

    /**
     * 深すぎる枝を落とした版の ID と落とした項目の数。
     *
     * @param revisionId 版の ID
     * @param count 落とした項目の数（1 以上）
     */
    private record Pruned(UUID revisionId, int count) {}

    /**
     * 起動時に読めなかった版の ID を覚える（{@link DslStartupLoader} だけが呼ぶ）。
     *
     * @param id 読めなかった適用中の版の ID
     */
    void remember(UUID id) {
        revisionId.set(id);
        pruned.set(null);
    }

    /**
     * 起動時に読めたが深すぎる枝を落とした版の ID と、落とした項目の数を覚える（{@link DslStartupLoader} だけが呼ぶ）。
     *
     * @param id 適用中の版の ID
     * @param count 落とした項目の数（1 以上）
     */
    void rememberPruned(UUID id, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("count は 1 以上です");
        }
        pruned.set(new Pruned(id, count));
        revisionId.set(null);
    }

    /** 覚えた ID と数を消す（起動時に枝を落とさずに読めた・適用中が無いとき。{@link DslStartupLoader} だけが呼ぶ）。 */
    void clear() {
        revisionId.set(null);
        pruned.set(null);
    }

    /**
     * 今の適用中の版が、起動時に読めなかった版かを判定する。
     *
     * @param currentRevisionId 今の適用中の版の ID（無ければ null）
     * @return 覚えた ID と同じなら true
     */
    boolean isUnreadable(UUID currentRevisionId) {
        return currentRevisionId != null && currentRevisionId.equals(revisionId.get());
    }

    /**
     * 今の適用中の版について、起動時に落とした深すぎるメニューの項目の数を返す。
     *
     * @param currentRevisionId 今の適用中の版の ID（無ければ null）
     * @return 覚えた版と同じなら落とした項目の数、違えば 0
     */
    int prunedMenuItems(UUID currentRevisionId) {
        Pruned value = pruned.get();
        return currentRevisionId != null && value != null && currentRevisionId.equals(value.revisionId())
                ? value.count()
                : 0;
    }
}
