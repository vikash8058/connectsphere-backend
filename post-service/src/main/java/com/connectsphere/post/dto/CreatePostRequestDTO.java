package com.connectsphere.post.dto;

import com.connectsphere.post.entity.PostType;
import com.connectsphere.post.entity.Visibility;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePostRequestDTO {
    @Size(max = 5000)
    private String content;

    @Builder.Default
    private List<String> mediaUrls = new ArrayList<>();

    @Builder.Default
    private PostType postType = PostType.TEXT;

    @Builder.Default
    private Visibility visibility = Visibility.PUBLIC;

    private Boolean isElite;
}