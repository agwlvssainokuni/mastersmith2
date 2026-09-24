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

import cherry.mastersmith.common.i18n.domain.DisplayLanguage;
import cherry.mastersmith.dsl.domain.ActiveDsl;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.service.ActiveDslModelProvider;
import cherry.mastersmith.dslmanage.domain.DslPreviewRef;
import cherry.mastersmith.dslmanage.domain.DslUserRef;
import cherry.mastersmith.dslmanage.domain.PreviewView;
import cherry.mastersmith.dslmanage.domain.PreviewView.Warning;
import cherry.mastersmith.targetdb.domain.ReadPurpose;
import cherry.mastersmith.targetdb.domain.TargetSchemaResult;
import cherry.mastersmith.targetdb.service.TargetSchemaReader;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * プレビューの中身（要約・違い・照合の警告）を求める（BR2.1〜BR2.4）。保存しない。
 *
 * <p>適用中のモデルは U2 が持っているもの（{@link ActiveDslModelProvider}）を使い、読み直さない。照合は U1 の
 * {@code readSchema(COMPARE)}（接続 3 秒・問い合わせ1回 5 秒）だけで、照合の全体の上限は置かない（決定 B）。照合できなくても
 * 失敗にせず、警告にする。
 */
@Component
public class DslPreviewAnalysis {

    private final TargetSchemaReader targetSchemaReader;

    private final ActiveDslModelProvider activeDslModelProvider;

    private final DslOperationMetrics metrics;

    /**
     * 作る。
     *
     * @param targetSchemaReader 対象DB のスキーマの読み取り（U1）
     * @param activeDslModelProvider 適用中のモデルの提供口（U2）
     * @param metrics 指標とログ
     */
    public DslPreviewAnalysis(
            TargetSchemaReader targetSchemaReader,
            ActiveDslModelProvider activeDslModelProvider,
            DslOperationMetrics metrics) {
        this.targetSchemaReader = targetSchemaReader;
        this.activeDslModelProvider = activeDslModelProvider;
        this.metrics = metrics;
    }

    /**
     * プレビューの中身を求める。
     *
     * @param preview プレビューの参照
     * @param placedBy 置いた管理者
     * @param model プレビューのモデル
     * @param language 警告の文言の表示言語
     * @param recordCompare 照合の時間を指標とログ（{@code operation=compare}）に記録するか（プレビューの表示のときだけ true）
     * @return プレビューの中身
     */
    public PreviewView analyze(
            DslPreviewRef preview,
            DslUserRef placedBy,
            DslModel model,
            DisplayLanguage language,
            boolean recordCompare) {
        PreviewView.Summary summary = DslSummaryCalculator.summarize(model);
        DslModel applied =
                activeDslModelProvider.current() instanceof ActiveDsl.Present present ? present.model() : null;
        PreviewView.Diff diff = DslDiffCalculator.diff(model, applied);
        long start = metrics.start();
        TargetSchemaResult target = targetSchemaReader.readSchema(ReadPurpose.COMPARE);
        List<Warning> warnings = DslReconciler.reconcile(model, target, language);
        if (recordCompare) {
            DslOutcome outcome = target instanceof TargetSchemaResult.Success ? DslOutcome.SUCCESS : DslOutcome.FAILED;
            metrics.record(DslOperation.COMPARE, outcome, start, preview.dslHash(), preview.source());
        }
        return new PreviewView(preview, placedBy, summary, diff, warnings);
    }
}
