package com.kosta.darfin.controller;

import com.kosta.darfin.dto.community.*;
import com.kosta.darfin.service.CommunityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    // =========================================================================
    // 질문 (Questions)
    // =========================================================================

    @GetMapping("/questions")
    public ResponseEntity<List<QuestionSummaryResponse>> getQuestions(
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(communityService.getQuestions(search));
    }

    @GetMapping("/questions/{id}")
    public ResponseEntity<QuestionDetailResponse> getQuestion(@PathVariable Long id) {
        return ResponseEntity.ok(communityService.getQuestion(id));
    }

    @PostMapping("/questions")
    public ResponseEntity<QuestionDetailResponse> createQuestion(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody QuestionCreateRequest request) {
        QuestionDetailResponse response = communityService.createQuestion(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/questions/{id}")
    public ResponseEntity<QuestionDetailResponse> updateQuestion(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody QuestionUpdateRequest request) {
        return ResponseEntity.ok(communityService.updateQuestion(userDetails.getUsername(), id, request));
    }

    @DeleteMapping("/questions/{id}")
    public ResponseEntity<Void> deleteQuestion(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        communityService.deleteQuestion(userDetails.getUsername(), id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/questions/{id}/resolve")
    public ResponseEntity<Void> resolveQuestion(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        communityService.resolveQuestion(userDetails.getUsername(), id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // 답변 (Answers)
    // =========================================================================

    @GetMapping("/questions/{id}/answers")
    public ResponseEntity<List<AnswerResponse>> getAnswers(@PathVariable Long id) {
        return ResponseEntity.ok(communityService.getAnswers(id));
    }

    @PostMapping("/questions/{id}/answers")
    public ResponseEntity<AnswerResponse> createAnswer(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody AnswerCreateRequest request) {
        AnswerResponse response = communityService.createAnswer(userDetails.getUsername(), id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/answers/{answerId}/accept")
    public ResponseEntity<Void> acceptAnswer(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long answerId) {
        communityService.acceptAnswer(userDetails.getUsername(), answerId);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // 대댓글 (Replies)
    // =========================================================================

    @GetMapping("/answers/{answerId}/replies")
    public ResponseEntity<List<ReplyResponse>> getReplies(@PathVariable Long answerId) {
        return ResponseEntity.ok(communityService.getReplies(answerId));
    }

    @PostMapping("/answers/{answerId}/replies")
    public ResponseEntity<ReplyResponse> createReply(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long answerId,
            @Valid @RequestBody ReplyCreateRequest request) {
        ReplyResponse response = communityService.createReply(userDetails.getUsername(), answerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/replies/{replyId}")
    public ResponseEntity<Void> deleteReply(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long replyId) {
        communityService.deleteReply(userDetails.getUsername(), replyId);
        return ResponseEntity.noContent().build();
    }
}
