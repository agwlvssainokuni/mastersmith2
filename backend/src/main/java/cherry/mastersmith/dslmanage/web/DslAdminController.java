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

import cherry.mastersmith.auth.domain.AuthenticatedUser;
import cherry.mastersmith.dslmanage.domain.DslDownload;
import cherry.mastersmith.dslmanage.domain.DslSource;
import cherry.mastersmith.dslmanage.service.DslLifecycle;
import cherry.mastersmith.dslmanage.service.DslOperation;
import cherry.mastersmith.dslmanage.service.DslRequestContext;
import cherry.mastersmith.dslmanage.web.DslResponses.DslStatusResponse;
import cherry.mastersmith.dslmanage.web.DslResponses.HistoryEntryResponse;
import cherry.mastersmith.dslmanage.web.DslResponses.PreviewResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * DSL の管理の API（契約 C6。管理者だけ、BR8.3）。HTTP の受け渡し（入力の形、DTO への変換、状態コード）だけを行う。
 *
 * <p>未認証 401・管理者でない 403 は、既存のアクセス制御（{@code /api/admin/**}）が返す。想定内の失敗は業務処理の層が業務の例外にし、
 * 共通の変換が Problem Details にする。重い道には {@link HeavyDslOperation} の印を付ける（本文を読む前に同時に1つの許可を取る）。
 */
@RestController
public class DslAdminController {

    /** DSL の本文の形（投入とダウンロード）。 */
    public static final String APPLICATION_YAML = "application/yaml";

    private final DslLifecycle lifecycle;

    private final DslRequestContextResolver contextResolver;

    /**
     * 作る。
     *
     * @param lifecycle DSL の管理の業務処理
     * @param contextResolver 要求の文脈の組み立て
     */
    public DslAdminController(DslLifecycle lifecycle, DslRequestContextResolver contextResolver) {
        this.lifecycle = lifecycle;
        this.contextResolver = contextResolver;
    }

    /** 投入の出どころ（画面から送れるもの。履歴からの戻しは別の API）。 */
    public enum SubmitSource {
        /** ファイルのアップロード。 */
        UPLOAD,
        /** テキストの貼り付け。 */
        PASTE
    }

    /**
     * 今の状態（BR6.3）。
     *
     * @return 今の状態
     */
    @GetMapping(DslAdminPaths.STATUS)
    public DslStatusResponse status() {
        return DslStatusResponse.from(lifecycle.status());
    }

    /**
     * プレビューの中身（BR2.1・BR2.5）。重い道。
     *
     * @param request 要求
     * @param user 操作した管理者
     * @return プレビューの中身
     */
    @GetMapping(DslAdminPaths.PREVIEW)
    @HeavyDslOperation(DslOperation.COMPARE)
    public PreviewResponse preview(HttpServletRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        return PreviewResponse.from(lifecycle.showPreview(context(request, user)));
    }

    /**
     * 投入（BR1.2）。本文は {@code application/yaml} だけ（ほかは 415）。重い道。
     *
     * @param source 出どころ
     * @param body 本文（空なら検証で誤りになる）
     * @param request 要求
     * @param user 操作した管理者
     * @return プレビューの中身（201）
     */
    @PostMapping(path = DslAdminPaths.PREVIEW, consumes = APPLICATION_YAML)
    @ResponseStatus(HttpStatus.CREATED)
    @HeavyDslOperation(DslOperation.SUBMIT)
    public PreviewResponse submit(
            @RequestParam SubmitSource source,
            @RequestBody(required = false) byte[] body,
            HttpServletRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return PreviewResponse.from(lifecycle.submit(
                body == null ? new byte[0] : body, DslSource.valueOf(source.name()), context(request, user)));
    }

    /**
     * プレビューの破棄（BR3.1）。
     *
     * @param request 要求
     * @param user 操作した管理者
     */
    @DeleteMapping(DslAdminPaths.PREVIEW)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discard(HttpServletRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        lifecycle.discard(context(request, user));
    }

    /**
     * スキーマの読み込み（BR1.1）。重い道。
     *
     * @param request 要求
     * @param user 操作した管理者
     * @return プレビューの中身（201）
     */
    @PostMapping(DslAdminPaths.GENERATE)
    @ResponseStatus(HttpStatus.CREATED)
    @HeavyDslOperation(DslOperation.GENERATE)
    public PreviewResponse generate(HttpServletRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        return PreviewResponse.from(lifecycle.generate(context(request, user)));
    }

    /**
     * プレビュー中の DSL のダウンロード（BR6.2、NFR3.8）。保存した本文をそのまま、添付として返す。
     *
     * @return 本文
     */
    @GetMapping(DslAdminPaths.PREVIEW_DOWNLOAD)
    public ResponseEntity<byte[]> downloadPreview() {
        return download(lifecycle.downloadPreview());
    }

    /**
     * 適用（BR4.1〜BR4.7）。
     *
     * @param body 見たプレビューの識別
     * @param request 要求
     * @param user 操作した管理者
     * @return 適用した後の今の状態
     */
    @PostMapping(path = DslAdminPaths.APPLY, consumes = MediaType.APPLICATION_JSON_VALUE)
    public DslStatusResponse apply(
            @Valid @RequestBody ApplyRequest body,
            HttpServletRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return DslStatusResponse.from(lifecycle.apply(UUID.fromString(body.previewId()), context(request, user)));
    }

    /**
     * 適用の履歴（新しい順、BR6.1）。
     *
     * @return 履歴
     */
    @GetMapping(DslAdminPaths.HISTORY)
    public List<HistoryEntryResponse> history() {
        return lifecycle.history().stream().map(HistoryEntryResponse::from).toList();
    }

    /**
     * 履歴の版をプレビューに戻す（BR1.3）。重い道。識別が UUID の形でなければ、ほかの API の入力と同じく 400
     * {@code VALIDATION_FAILED}（共通の変換）。
     *
     * @param revisionId 戻す版の識別
     * @param request 要求
     * @param user 操作した管理者
     * @return プレビューの中身（201）
     */
    @PostMapping(DslAdminPaths.HISTORY_RESTORE)
    @ResponseStatus(HttpStatus.CREATED)
    @HeavyDslOperation(DslOperation.RESTORE)
    public PreviewResponse restore(
            @PathVariable UUID revisionId,
            HttpServletRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return PreviewResponse.from(lifecycle.restore(revisionId, context(request, user)));
    }

    /**
     * 適用中の DSL のダウンロード（BR6.2、NFR3.8）。保存した本文をそのまま、添付として返す。
     *
     * @return 本文
     */
    @GetMapping(DslAdminPaths.APPLIED_DOWNLOAD)
    public ResponseEntity<byte[]> downloadApplied() {
        return download(lifecycle.downloadApplied());
    }

    private DslRequestContext context(HttpServletRequest request, AuthenticatedUser user) {
        return contextResolver.resolve(request, user);
    }

    private static ResponseEntity<byte[]> download(DslDownload download) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(APPLICATION_YAML))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(download.fileName())
                                .build()
                                .toString())
                .body(download.content().yamlBytes());
    }
}
