package com.linkedin.searchservice.dto;

import java.util.List;

import com.linkedin.searchservice.document.UserDocument;

/**
 * One row of a people search result: enough to render a result card, nothing more.
 *
 * <p>Deliberately not the user aggregate - the client opens a profile through user-service
 * when it needs the rest, the same split feed-service uses for post ids. That is also where
 * display assets come from (avatar, cover photo): only searchable profile text is indexed,
 * so a result stays a pointer to the right person instead of a second copy of the profile.
 * Email is indexed (so exact lookups work) but never returned: search must not become a way
 * to harvest addresses.
 */
public record SearchUserResponse(
        String userId,
        String firstName,
        String lastName,
        String headline,
        String location,
        List<String> skills) {

    public static SearchUserResponse from(UserDocument document) {
        return new SearchUserResponse(
                document.getUserId(),
                document.getFirstName(),
                document.getLastName(),
                document.getHeadline(),
                document.getLocation(),
                document.getSkills() == null ? List.of() : document.getSkills());
    }
}
