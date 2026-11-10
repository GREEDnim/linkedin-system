package com.linkedin.searchservice.document;

import java.util.List;

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
 * A user as stored in the {@code users} index - the Spring Data mapping for one document.
 *
 * <p>A projection, not an entity: it holds only what is worth searching or showing in a
 * result row, and it is only ever written from events or read by a query. Anything else
 * the client needs is fetched from user-service by id, the same split feed-service uses
 * for post ids.
 *
 * <p>Every field comes from a user event except {@code fullName}, which is derived from
 * first + last name so a query for "ada lovelace" matches one field instead of needing a
 * cross-field AND. {@code user.created} supplies name and email; {@code user.updated}
 * supplies headline, location, skills and the photo url.
 *
 * <p>A mutable class rather than a record because Spring Data needs a no-arg constructor
 * to instantiate documents when reading them back.
 *
 * <p>{@code createIndex = true} means Spring Data creates the index from this mapping and
 * {@code @Setting} when the repository bean is first built, so there is no index
 * bootstrapping code - and an unreachable cluster fails startup rather than the first
 * query. Nothing updates an existing mapping: a changed {@code @Field} needs the index
 * deleted and the consumer group reset, so the events replay into the fresh index.
 */
@Document(indexName = UserDocument.INDEX_NAME, createIndex = true)
@Setting(settingPath = "/elasticsearch/users-settings.json")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDocument {

    public static final String INDEX_NAME = "users";

    /** Keyword: an id is matched exactly and never tokenised. */
    @Id
    @Field(type = FieldType.Keyword)
    private String userId;

    /**
     * Ranked highest by the query: a name match is almost always the intent.
     * Copied into {@code fullName} by Elasticsearch at index time.
     */
    @Field(type = FieldType.Text, copyTo = "fullName")
    private String firstName;

    @Field(type = FieldType.Text, copyTo = "fullName")
    private String lastName;

    /**
     * "Ada Lovelace", built by Elasticsearch from firstName + lastName ({@code copy_to}),
     * so a multi-word query matches one field instead of needing a cross-field AND.
     *
     * <p>Never written by this service and not part of {@code _source}, so it reads back as
     * null. It exists as a property only so the mapping declares the destination field; the
     * value itself is entirely the engine's business, which is why it cannot drift from the
     * two source fields even when a write never passes through our code.
     */
    @Field(type = FieldType.Text)
    private String fullName;

    @Field(type = FieldType.Text)
    private String headline;

    @Field(type = FieldType.Text)
    private String location;

    @Field(type = FieldType.Text)
    private List<String> skills;

    /**
     * Keyword, not text: email is matched exactly, never tokenised. Tokenising would let
     * "test" match every {@code @test.com} address - useless, and a way to enumerate
     * accounts. Written lower-cased so the exact match is case-insensitive.
     */
    @Field(type = FieldType.Keyword)
    private String email;
}
