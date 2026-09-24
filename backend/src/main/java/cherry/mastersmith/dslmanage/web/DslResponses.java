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
package cherry.mastersmith.dslmanage.web;

import cherry.mastersmith.dslmanage.domain.DslAppliedRef;
import cherry.mastersmith.dslmanage.domain.DslPreviewRef;
import cherry.mastersmith.dslmanage.domain.DslStatus;
import cherry.mastersmith.dslmanage.domain.DslUserRef;
import cherry.mastersmith.dslmanage.domain.PreviewView;
import java.time.Instant;
import java.util.List;

/** DSL の管理の API の応答（契約 C6 のスキーマ）。エンティティを直接返さないための {@code record} の DTO をまとめる。 */
public final class DslResponses {

    private DslResponses() {}

    /**
     * 操作した管理者（契約 C6 の {@code by}）。
     *
     * @param userId 利用者 ID（文字列）
     * @param email メールアドレス（利用者が見つからなければ null）
     */
    public record UserResponse(String userId, String email) {

        static UserResponse from(DslUserRef user) {
            return new UserResponse(Long.toString(user.userId()), user.email());
        }
    }

    /**
     * 適用中の版の参照（契約 C6 の {@code AppliedRef}）。
     *
     * @param revisionId 版の識別
     * @param dslHash DSL の識別
     * @param source 出どころ
     * @param by 適用した管理者
     * @param at 適用した日時（UTC）
     */
    public record AppliedRefResponse(String revisionId, String dslHash, String source, UserResponse by, Instant at) {

        static AppliedRefResponse from(DslStatus.Applied applied) {
            DslAppliedRef ref = applied.ref();
            return new AppliedRefResponse(
                    ref.revisionId().toString(),
                    ref.dslHash(),
                    ref.source().name(),
                    UserResponse.from(applied.by()),
                    ref.appliedAt());
        }
    }

    /**
     * 今のプレビューの参照（契約 C6 の {@code PreviewRef}）。
     *
     * @param previewId プレビューの識別
     * @param dslHash DSL の識別
     * @param source 出どころ
     * @param by 置いた管理者
     * @param at 置いた日時（UTC）
     */
    public record PreviewRefResponse(String previewId, String dslHash, String source, UserResponse by, Instant at) {

        static PreviewRefResponse from(DslPreviewRef ref, DslUserRef by) {
            return new PreviewRefResponse(
                    ref.previewId().toString(),
                    ref.dslHash(),
                    ref.source().name(),
                    UserResponse.from(by),
                    ref.placedAt());
        }
    }

    /**
     * 今の状態（契約 C6 の {@code DslStatus}）。
     *
     * @param applied 適用中の版（無ければ null）
     * @param preview 今のプレビュー（無ければ null）
     */
    public record DslStatusResponse(AppliedRefResponse applied, PreviewRefResponse preview) {

        static DslStatusResponse from(DslStatus status) {
            return new DslStatusResponse(
                    status.applied() == null ? null : AppliedRefResponse.from(status.applied()),
                    status.preview() == null
                            ? null
                            : PreviewRefResponse.from(
                                    status.preview().ref(), status.preview().by()));
        }
    }

    /**
     * プレビューの中身（契約 C6 の {@code Preview}）。要約・違い・警告は保存しない値（エンティティではない）。
     *
     * @param previewId プレビューの識別
     * @param dslHash DSL の識別
     * @param source 出どころ
     * @param by 置いた管理者
     * @param at 置いた日時（UTC）
     * @param summary 要約
     * @param diff 適用中との違い
     * @param warnings 照合の警告
     */
    public record PreviewResponse(
            String previewId,
            String dslHash,
            String source,
            UserResponse by,
            Instant at,
            PreviewView.Summary summary,
            PreviewView.Diff diff,
            List<PreviewView.Warning> warnings) {

        static PreviewResponse from(PreviewView view) {
            PreviewRefResponse ref = PreviewRefResponse.from(view.preview(), view.placedBy());
            return new PreviewResponse(
                    ref.previewId(),
                    ref.dslHash(),
                    ref.source(),
                    ref.by(),
                    ref.at(),
                    view.summary(),
                    view.diff(),
                    view.warnings());
        }
    }

    /**
     * 履歴の1件（契約 C6 の {@code HistoryEntry}）。
     *
     * @param revisionId 版の識別
     * @param dslHash DSL の識別
     * @param source 出どころ
     * @param by 適用した管理者
     * @param at 適用した日時（UTC）
     * @param current 今適用中の版なら true
     */
    public record HistoryEntryResponse(
            String revisionId, String dslHash, String source, UserResponse by, Instant at, boolean current) {

        static HistoryEntryResponse from(DslStatus.HistoryEntry entry) {
            AppliedRefResponse ref = AppliedRefResponse.from(entry.applied());
            return new HistoryEntryResponse(
                    ref.revisionId(), ref.dslHash(), ref.source(), ref.by(), ref.at(), entry.current());
        }
    }
}
