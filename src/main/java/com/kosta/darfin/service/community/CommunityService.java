package com.kosta.darfin.service.community;

import com.kosta.darfin.dto.community.*;
import com.kosta.darfin.entity.common.Stock;
import com.kosta.darfin.entity.common.Users;
import com.kosta.darfin.entity.community.CommunityComments;
import com.kosta.darfin.entity.community.CommunityPosts;
import com.kosta.darfin.repository.common.StockRepository;
import com.kosta.darfin.repository.common.UsersRepository;
import com.kosta.darfin.repository.community.CommunityCommentsRepository;
import com.kosta.darfin.repository.community.CommunityPostsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityService {

    private final CommunityPostsRepository postsRepository;
    private final CommunityCommentsRepository commentsRepository;
    private final UsersRepository usersRepository;
    private final StockRepository stockRepository;

    // =========================================================================
    // 질문 (Questions)
    // =========================================================================

    public List<QuestionSummaryResponse> getQuestions(String search) {
        List<CommunityPosts> posts = postsRepository.searchPosts(
                search == null || search.isBlank() ? null : search
        );
        return posts.stream()
                .map(p -> QuestionSummaryResponse.from(p, commentsRepository.countAnswersByPostId(p.getId())))
                .collect(Collectors.toList());
    }

    @Transactional
    public QuestionDetailResponse getQuestion(Long id) {
        CommunityPosts post = findActivePost(id);
        post.incrementViews();
        long answerCount = commentsRepository.countAnswersByPostId(id);
        return QuestionDetailResponse.from(post, answerCount);
    }

    @Transactional
    public QuestionDetailResponse createQuestion(String email, QuestionCreateRequest req) {
        Users author = findActiveUser(email);
        Stock stock = resolveStock(req.getDartCorpCode());

        CommunityPosts post = CommunityPosts.builder()
                .author(author)
                .stock(stock)
                .title(req.getTitle())
                .content(req.getContent())
                .build();

        CommunityPosts saved = postsRepository.save(post);
        return QuestionDetailResponse.from(saved, 0);
    }

    @Transactional
    public QuestionDetailResponse updateQuestion(String email, Long id, QuestionUpdateRequest req) {
        CommunityPosts post = findActivePost(id);
        checkAuthor(post.getAuthor().getId(), email);
        Stock stock = resolveStock(req.getDartCorpCode());
        post.update(req.getTitle(), req.getContent(), stock);
        long answerCount = commentsRepository.countAnswersByPostId(id);
        return QuestionDetailResponse.from(post, answerCount);
    }

    @Transactional
    public void deleteQuestion(String email, Long id) {
        CommunityPosts post = findActivePost(id);
        checkAuthor(post.getAuthor().getId(), email);
        post.delete();
    }

    @Transactional
    public void resolveQuestion(String email, Long id) {
        CommunityPosts post = findActivePost(id);
        checkAuthor(post.getAuthor().getId(), email);
        if (post.getIsResolved()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 해결된 질문입니다.");
        }
        post.resolve();
    }

    // =========================================================================
    // 답변 (Answers)
    // =========================================================================

    public List<AnswerResponse> getAnswers(Long questionId) {
        findActivePost(questionId); // 존재 여부 확인
        List<CommunityComments> answers = commentsRepository.findAnswersByPostId(questionId);
        return answers.stream()
                .map(a -> AnswerResponse.from(a, commentsRepository.findRepliesByParentId(a.getId()).size()))
                .collect(Collectors.toList());
    }

    @Transactional
    public AnswerResponse createAnswer(String email, Long questionId, AnswerCreateRequest req) {
        Users author = findActiveUser(email);
        CommunityPosts post = findActivePost(questionId);

        CommunityComments answer = CommunityComments.builder()
                .author(author)
                .post(post)
                .parent(null)
                .content(req.getContent())
                .build();

        CommunityComments saved = commentsRepository.save(answer);
        return AnswerResponse.from(saved, 0);
    }

    @Transactional
    public void acceptAnswer(String email, Long answerId) {
        CommunityComments answer = findComment(answerId);

        if (answer.getParent() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "대댓글은 채택할 수 없습니다.");
        }

        CommunityPosts post = answer.getPost();
        checkAuthor(post.getAuthor().getId(), email);

        if (post.getIsResolved()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 채택된 답변이 있는 질문입니다.");
        }

        answer.adopt();
        post.resolve(); // 채택 시 질문도 해결됨으로 변경
    }

    // =========================================================================
    // 대댓글 (Replies)
    // =========================================================================

    public List<ReplyResponse> getReplies(Long answerId) {
        findComment(answerId); // 존재 여부 확인
        return commentsRepository.findRepliesByParentId(answerId).stream()
                .map(ReplyResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public ReplyResponse createReply(String email, Long answerId, ReplyCreateRequest req) {
        Users author = findActiveUser(email);
        CommunityComments answer = findComment(answerId);

        if (answer.getParent() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "대댓글에는 답변할 수 없습니다.");
        }

        CommunityComments reply = CommunityComments.builder()
                .author(author)
                .post(answer.getPost())
                .parent(answer)
                .content(req.getContent())
                .build();

        CommunityComments saved = commentsRepository.save(reply);
        return ReplyResponse.from(saved);
    }

    @Transactional
    public void deleteReply(String email, Long replyId) {
        CommunityComments reply = findComment(replyId);

        if (reply.getParent() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "답변은 이 API로 삭제할 수 없습니다.");
        }

        checkAuthor(reply.getAuthor().getId(), email);
        commentsRepository.delete(reply);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private Users findActiveUser(String email) {
        Users user = usersRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        if ("DELETED".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.GONE, "탈퇴한 계정입니다.");
        }
        return user;
    }

    private CommunityPosts findActivePost(Long id) {
        return postsRepository.findActiveById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "질문을 찾을 수 없습니다."));
    }

    private CommunityComments findComment(Long id) {
        return commentsRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."));
    }

    private Stock resolveStock(String dartCorpCode) {
        if (dartCorpCode == null || dartCorpCode.isBlank()) return null;
        return stockRepository.findByDartCorpCode(dartCorpCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "종목을 찾을 수 없습니다: " + dartCorpCode));
    }

    private void checkAuthor(Long authorId, String email) {
        Users user = usersRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        if (!authorId.equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "작성자만 수행할 수 있습니다.");
        }
    }
}
