package com.linkedin.searchservice.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * A post as stored in the {@code posts} index.
 *
 * <p>Only what a search result needs: who wrote it and the text. Engagement counters and
 * the image url are deliberately absent - the counters live in like/comment events we do
 * not consume yet (indexing a permanent zero would be a lie), and display assets belong to
 * the post endpoint, not to the index.
 */
@Document(indexName = PostDocument.INDEX_NAME, createIndex = true)
@Setting(settingPath = "/elasticsearch/posts-settings.json")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostDocument {

    public static final String INDEX_NAME = "posts";

    @Id
    @Field(type = FieldType.Keyword)
    private String postId;

    /** Keyword so a future "posts by this author" filter is exact, not analysed. */
    @Field(type = FieldType.Keyword)
    private String authorId;

    /** The only analysed field: this is what a full-text post search matches. */
    @Field(type = FieldType.Text)
    private String content;
}
