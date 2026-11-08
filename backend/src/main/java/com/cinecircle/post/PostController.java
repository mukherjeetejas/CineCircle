package com.cinecircle.post;

import com.cinecircle.common.ApiException;
import com.cinecircle.common.PageParams;
import com.cinecircle.common.PageResponse;
import com.cinecircle.common.RequestValidator;
import com.cinecircle.post.dto.CreatePostRequest;
import com.cinecircle.post.dto.PostDetail;
import com.cinecircle.post.dto.PostSummary;
import com.cinecircle.security.AuthUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final RequestValidator validator;
    private final ObjectMapper objectMapper;

    /**
     * Multipart: part {@code post} (JSON; accepted either as an application/json part or as a plain form
     * field containing JSON) + optional part {@code image}.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PostDetail create(@AuthenticationPrincipal AuthUser me,
                             @RequestPart("post") String postJson,
                             @RequestPart(name = "image", required = false) MultipartFile image) {
        CreatePostRequest req;
        try {
            req = objectMapper.readValue(postJson, CreatePostRequest.class);
        } catch (JsonProcessingException e) {
            throw ApiException.validation("post", "Part 'post' must be valid JSON");
        }
        return postService.create(me.id(), validator.validate(req), image);
    }

    @GetMapping("/popular")
    public PageResponse<PostSummary> popular(@AuthenticationPrincipal AuthUser viewer,
                                             @RequestParam(required = false, defaultValue = "all") String window,
                                             @RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) Integer size) {
        return postService.popular(window, AuthUser.idOf(viewer), PageParams.of(page, size));
    }

    @GetMapping("/{id}")
    public PostDetail get(@AuthenticationPrincipal AuthUser viewer, @PathVariable String id) {
        return postService.get(id, AuthUser.idOf(viewer));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable String id) {
        postService.delete(id, me.id());
    }
}
