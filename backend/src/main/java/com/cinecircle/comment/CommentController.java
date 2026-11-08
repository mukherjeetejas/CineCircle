package com.cinecircle.comment;

import com.cinecircle.comment.dto.CommentDto;
import com.cinecircle.comment.dto.CreateCommentRequest;
import com.cinecircle.common.PageParams;
import com.cinecircle.common.PageResponse;
import com.cinecircle.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping("/posts/{id}/comments")
    public PageResponse<CommentDto> list(@AuthenticationPrincipal AuthUser viewer, @PathVariable String id,
                                         @RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer size) {
        return commentService.list(id, AuthUser.idOf(viewer), PageParams.of(page, size));
    }

    @PostMapping("/posts/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentDto create(@AuthenticationPrincipal AuthUser me, @PathVariable String id,
                             @Valid @RequestBody CreateCommentRequest req) {
        return commentService.create(id, me.id(), req);
    }

    @DeleteMapping("/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable String id) {
        commentService.delete(id, me.id());
    }
}
