#!/usr/bin/env bash
#
# End-to-end verification through the API gateway.
#
# Everything goes through the gateway on :8080 - that is the only port a client is
# supposed to know. Each check asserts a status code and, where it matters, the body:
# ownership rules, idempotency, counters, fan-out, projections, notifications.
#
# Requires: mysql, kafka, redis, elasticsearch and s3mock running (docker compose up -d),
# all six services up, and a clean slate is NOT required - the script creates its own users.
#
#   ./scripts/e2e.sh                       # defaults below
#   BASE_URL=http://localhost:8080 \
#   NOTIF_LOG=/tmp/v/notification-service.log ./scripts/e2e.sh
#
# The notification checks read the consumer's log, because delivery is a log line for now
# (notification-service has no HTTP API yet). Point NOTIF_LOG at wherever it writes, or
# set SKIP_NOTIFICATIONS=1 to leave those checks out.
#
set -uo pipefail

G=${BASE_URL:-http://localhost:8080}
BODY=/tmp/e2e-body.json
PASS=0; FAIL=0; FAILED=""
ok(){ PASS=$((PASS+1)); printf '  PASS  %s\n' "$1"; }
bad(){ FAIL=$((FAIL+1)); FAILED="$FAILED\n    - $1 (got $2, want $3)"; printf '  FAIL  %s  (got %s, want %s)\n' "$1" "$2" "$3"; }
chk(){ local label="$1" want="$2" got; shift 2; got=$(curl -s -o $BODY -w '%{http_code}' "$@"); if [ "$got" = "$want" ]; then ok "$label [$got]"; else bad "$label" "$got" "$want"; fi; }
sec(){ printf '\n=== %s ===\n' "$1"; }
py(){ python3 -c "import json;d=json.load(open('$BODY'));print($1)" 2>/dev/null; }
reg(){ curl -s -X POST $G/api/v1/auth/register -H 'Content-Type: application/json' -d "{\"firstName\":\"$1\",\"lastName\":\"$2\",\"email\":\"$3\",\"password\":\"secret123\"}"; }

if ! curl -sf -m 5 "$G/actuator/health" > /dev/null; then
    printf 'gateway not reachable at %s - start the stack first (docker compose up -d, then the six services)\n' "$G" >&2
    exit 2
fi

printf 'PNG' > /tmp/e2e.png; python3 -c "open('/tmp/e2e.png','wb').write(bytes.fromhex('89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c4890000000a49444154789c6360000002000100ffff03000006000557bfabd40000000049454e44ae426082'))"

sec "1. AUTH"
chk 'register A (no token)' 201 -X POST $G/api/v1/auth/register -H 'Content-Type: application/json' -d '{"firstName":"Ada","lastName":"Lovelace","email":"ada@e2e.test","password":"secret123"}'
A=$(py "d['userId']"); AT=$(py "d['accessToken']"); RT=$(py "d['refreshToken']")
chk 'duplicate email' 409 -X POST $G/api/v1/auth/register -H 'Content-Type: application/json' -d '{"firstName":"Ada","lastName":"Lovelace","email":"ada@e2e.test","password":"secret123"}'
chk 'register B' 201 -X POST $G/api/v1/auth/register -H 'Content-Type: application/json' -d '{"firstName":"Grace","lastName":"Hopper","email":"grace@e2e.test","password":"secret123"}'; B=$(py "d['userId']")
chk 'register C' 201 -X POST $G/api/v1/auth/register -H 'Content-Type: application/json' -d '{"firstName":"Katherine","lastName":"Johnson","email":"katherine@e2e.test","password":"secret123"}'; C=$(py "d['userId']")
chk 'login A' 200 -X POST $G/api/v1/auth/login -H 'Content-Type: application/json' -d '{"email":"ada@e2e.test","password":"secret123"}'; AT=$(py "d['accessToken']"); RT=$(py "d['refreshToken']")
chk 'login B' 200 -X POST $G/api/v1/auth/login -H 'Content-Type: application/json' -d '{"email":"grace@e2e.test","password":"secret123"}'; BT=$(py "d['accessToken']")
chk 'login C' 200 -X POST $G/api/v1/auth/login -H 'Content-Type: application/json' -d '{"email":"katherine@e2e.test","password":"secret123"}'; CT=$(py "d['accessToken']")
chk 'login wrong password' 401 -X POST $G/api/v1/auth/login -H 'Content-Type: application/json' -d '{"email":"ada@e2e.test","password":"wrong"}'
chk 'register short password' 400 -X POST $G/api/v1/auth/register -H 'Content-Type: application/json' -d '{"firstName":"X","lastName":"Y","email":"x@e2e.test","password":"1"}'
printf 'A=%s\nB=%s\nC=%s\nAT=%s\nRT=%s\nBT=%s\nCT=%s\n' "$A" "$B" "$C" "$AT" "$RT" "$BT" "$CT" > /tmp/e2e-ids.sh

sec "2. GATEWAY AUTH MATRIX"
HA="Authorization: Bearer $AT"
chk 'protected, no token' 401 $G/api/v1/users/$A/connections
chk 'protected, garbage token' 401 $G/api/v1/users/$A/connections -H 'Authorization: Bearer nope.nope.nope'
chk 'refresh token as access' 401 $G/api/v1/users/$A/connections -H "Authorization: Bearer $RT"
chk 'valid token' 200 $G/api/v1/users/$A/connections -H "$HA"
chk 'spoof header on own path' 200 $G/api/v1/users/$A/connections -H "$HA" -H "X-User-Id: $B"
chk 'spoof header on other path' 403 $G/api/v1/users/$B/connections -H "$HA" -H "X-User-Id: $B"
chk 'unknown path, anonymous' 401 $G/api/v1/does-not-exist -H 'Authorization: Bearer nope'
chk 'unknown path, authenticated' 404 $G/api/v1/does-not-exist -H "$HA"

sec "3. PROFILE + PHOTO (real upload to s3mock)"
chk 'get own profile' 200 $G/api/v1/users/$A -H "$HA"
chk 'update profile' 200 -X PUT $G/api/v1/users/$A/profile -H "$HA" -H 'Content-Type: application/json' -d '{"firstName":"Ada","lastName":"Lovelace","headline":"Countess of Computing","about":"First programmer","location":"London, UK","skills":["Mathematics","Analytical Engine"]}'
HEAD=$(py "d['headline']"); SK=$(py "len(d['skills'])"); LOC=$(py "d['location']")
[ "$HEAD" = 'Countess of Computing' ] && ok 'headline persisted' || bad 'headline persisted' "$HEAD" 'Countess of Computing'
[ "$SK" = '2' ] && ok 'skills persisted (2)' || bad 'skills persisted' "$SK" 2
[ "$LOC" = 'London, UK' ] && ok 'location persisted' || bad 'location persisted' "$LOC" 'London, UK'
chk 'update someone else profile' 403 -X PUT $G/api/v1/users/$B/profile -H "$HA" -H 'Content-Type: application/json' -d '{"headline":"hacked"}'
chk 'upload profile photo' 200 -X POST $G/api/v1/users/$A/upload/profile-photo -H "$HA" -F 'file=@/tmp/e2e.png;type=image/png'
PHOTO=$(py "d['profilePhotoUrl']")
case "$PHOTO" in http://localhost:9090/*) ok "photo stored in s3mock ($PHOTO)";; *) bad 'photo url from s3mock' "$PHOTO" 'http://localhost:9090/...';; esac
chk 'upload non-image rejected' 400 -X POST $G/api/v1/users/$A/upload/profile-photo -H "$HA" -F 'file=@/tmp/e2e-ids.sh;type=text/plain'

sec "4. CONNECTIONS"
chk 'A connects B' 201 -X POST $G/api/v1/users/$B/connect -H "$HA"; CID=$(py "d['id']")
chk 'duplicate request' 409 -X POST $G/api/v1/users/$B/connect -H "$HA"
chk 'reverse request' 409 -X POST $G/api/v1/users/$A/connect -H "Authorization: Bearer $BT"
chk 'self connect' 400 -X POST $G/api/v1/users/$A/connect -H "$HA"
chk 'connect unknown user' 404 -X POST $G/api/v1/users/00000000-0000-0000-0000-000000000000/connect -H "$HA"
chk 'B pending list' 200 $G/api/v1/users/$B/connections/pending -H "Authorization: Bearer $BT"
N=$(py "len(d)")
[ "$N" = '1' ] && ok 'B has exactly 1 pending request' || bad 'B pending count' "$N" 1
chk 'requester cannot accept' 403 -X POST $G/api/v1/users/connections/$CID/accept -H "$HA"
chk 'receiver accepts' 200 -X POST $G/api/v1/users/connections/$CID/accept -H "Authorization: Bearer $BT"
ST=$(py "d['status']")
[ "$ST" = 'CONNECTED' ] && ok 'status CONNECTED' || bad 'status after accept' "$ST" CONNECTED
CAT=$(py "d['connectedAt']")
[ "$CAT" != 'None' ] && ok "connectedAt stamped ($CAT)" || bad 'connectedAt' "$CAT" 'a timestamp'
chk 'A sees 1 connection' 200 $G/api/v1/users/$A/connections -H "$HA"; N=$(py "len(d)")
[ "$N" = '1' ] && ok 'A connection list has 1' || bad 'A connection count' "$N" 1
chk 'B sees 1 connection (both sides)' 200 $G/api/v1/users/$B/connections -H "Authorization: Bearer $BT"; N=$(py "len(d)")
[ "$N" = '1' ] && ok 'B connection list has 1' || bad 'B connection count' "$N" 1
chk 'outsider disconnects' 403 -X DELETE $G/api/v1/users/connections/$CID -H "Authorization: Bearer $CT"
chk 'B disconnects' 200 -X DELETE $G/api/v1/users/connections/$CID -H "Authorization: Bearer $BT"; ST=$(py "d['status']")
[ "$ST" = 'REMOVED' ] && ok 'status REMOVED' || bad 'status after disconnect' "$ST" REMOVED
chk 'A connections empty after disconnect' 200 $G/api/v1/users/$A/connections -H "$HA"; N=$(py "len(d)")
[ "$N" = '0' ] && ok 'A connection list emptied' || bad 'A connection count after disconnect' "$N" 0
chk 'reconnect after disconnect' 201 -X POST $G/api/v1/users/$B/connect -H "$HA"; CID2=$(py "d['id']")
[ "$CID2" = "$CID" ] && ok 'row reused on reconnect (same connectionId)' || bad 'connectionId reused' "$CID2" "$CID"
chk 'B accepts again' 200 -X POST $G/api/v1/users/connections/$CID/accept -H "Authorization: Bearer $BT"; ST=$(py "d['status']")
[ "$ST" = 'CONNECTED' ] && ok 'reconnected' || bad 'status after reconnect' "$ST" CONNECTED
chk 'C connects A' 201 -X POST $G/api/v1/users/$A/connect -H "Authorization: Bearer $CT"; CIDC=$(py "d['id']")
chk 'A rejects C' 200 -X POST $G/api/v1/users/connections/$CIDC/reject -H "$HA"; ST=$(py "d['status']")
[ "$ST" = 'REJECTED' ] && ok 'status REJECTED' || bad 'status after reject' "$ST" REJECTED
chk 'C re-requests after rejection' 201 -X POST $G/api/v1/users/$A/connect -H "Authorization: Bearer $CT"
chk 'A accepts C' 200 -X POST $G/api/v1/users/connections/$CIDC/accept -H "$HA"
chk 'A now sees 2 connections' 200 $G/api/v1/users/$A/connections -H "$HA"; N=$(py "len(d)")
[ "$N" = '2' ] && ok 'A connection list has 2' || bad 'A connection count' "$N" 2

sec "5. POSTS"
chk 'create post (text)' 201 -X POST $G/api/v1/posts -H "$HA" -F 'content=Distributed feeds need fan-out on write'; P1=$(py "d['id']")
chk 'create post (text + image)' 201 -X POST $G/api/v1/posts -H "$HA" -F 'content=Analytical engine notes attached' -F 'image=@/tmp/e2e.png;type=image/png'; P2=$(py "d['id']")
IMG=$(py "d['imageUrl']")
case "$IMG" in http://localhost:9090/*) ok "post image stored in s3mock";; *) bad 'post image url' "$IMG" 'http://localhost:9090/...';; esac
chk 'blank content' 400 -X POST $G/api/v1/posts -H "$HA" -F 'content=   '
chk 'missing content part' 400 -X POST $G/api/v1/posts -H "$HA" -F 'x=1'
chk 'json body to multipart endpoint' 415 -X POST $G/api/v1/posts -H "$HA" -H 'Content-Type: application/json' -d '{"content":"nope"}'
chk 'get post' 200 $G/api/v1/posts/$P1 -H "$HA"
chk 'get unknown post' 404 $G/api/v1/posts/nope -H "$HA"
chk 'list A posts' 200 $G/api/v1/posts/user/$A -H "$HA"; N=$(py "len(d)")
[ "$N" = '2' ] && ok 'A has 2 posts' || bad 'A post count' "$N" 2

sec "6. LIKES"
chk 'B likes' 200 -X PUT $G/api/v1/posts/$P1/like -H "Authorization: Bearer $BT"; LC=$(py "d['likeCount']")
[ "$LC" = '1' ] && ok 'likeCount 1' || bad 'likeCount after first like' "$LC" 1
chk 'B likes again (idempotent)' 200 -X PUT $G/api/v1/posts/$P1/like -H "Authorization: Bearer $BT"; LC=$(py "d['likeCount']")
[ "$LC" = '1' ] && ok 'still 1 like' || bad 'likeCount after duplicate like' "$LC" 1
chk 'A likes own post' 200 -X PUT $G/api/v1/posts/$P1/like -H "$HA"; LC=$(py "d['likeCount']")
[ "$LC" = '2' ] && ok 'likeCount 2' || bad 'likeCount after second liker' "$LC" 2
chk 'B unlikes' 200 -X DELETE $G/api/v1/posts/$P1/like -H "Authorization: Bearer $BT"; LC=$(py "d['likeCount']")
[ "$LC" = '1' ] && ok 'likeCount back to 1' || bad 'likeCount after unlike' "$LC" 1
chk 'B unlikes again (idempotent)' 200 -X DELETE $G/api/v1/posts/$P1/like -H "Authorization: Bearer $BT"; LC=$(py "d['likeCount']")
[ "$LC" = '1' ] && ok 'never negative' || bad 'likeCount after duplicate unlike' "$LC" 1
chk 'like unknown post' 404 -X PUT $G/api/v1/posts/nope/like -H "Authorization: Bearer $BT"

sec "7. COMMENTS"
chk 'B comments' 201 -X POST $G/api/v1/posts/$P1/comments -H "Authorization: Bearer $BT" -H 'Content-Type: application/json' -d '{"content":"Great point about fan-out"}'; CM1=$(py "d['id']")
chk 'A comments' 201 -X POST $G/api/v1/posts/$P1/comments -H "$HA" -H 'Content-Type: application/json' -d '{"content":"Thanks - read path stays cheap"}'; CM2=$(py "d['id']")
chk 'blank comment' 400 -X POST $G/api/v1/posts/$P1/comments -H "$HA" -H 'Content-Type: application/json' -d '{"content":"  "}'
chk 'comment on unknown post' 404 -X POST $G/api/v1/posts/nope/comments -H "$HA" -H 'Content-Type: application/json' -d '{"content":"x"}'
chk 'list comments' 200 $G/api/v1/posts/$P1/comments -H "$HA"; N=$(py "len(d)")
[ "$N" = '2' ] && ok '2 comments' || bad 'comment count' "$N" 2
FIRST=$(py "d[0]['content']")
[ "$FIRST" = 'Thanks - read path stays cheap' ] && ok 'comments newest first' || bad 'comment order' "$FIRST" 'newest first'
chk 'outsider deletes comment' 403 -X DELETE $G/api/v1/posts/$P1/comments/$CM1 -H "Authorization: Bearer $CT"
chk 'author deletes own comment' 204 -X DELETE $G/api/v1/posts/$P1/comments/$CM1 -H "Authorization: Bearer $BT"
chk 'commentCount after delete' 200 $G/api/v1/posts/$P1 -H "$HA"; CC=$(py "d['commentCount']")
[ "$CC" = '1' ] && ok 'commentCount decremented to 1' || bad 'commentCount' "$CC" 1
chk 'outsider deletes post' 403 -X DELETE $G/api/v1/posts/$P1 -H "Authorization: Bearer $CT"

sec "8. FEED (fan-out from real events)"
sleep 4
chk 'A feed' 200 $G/api/v1/feed/$A -H "$HA"; N=$(py "len(d['postIds'])")
[ "$N" = '2' ] && ok 'author sees own 2 posts' || bad 'A feed size' "$N" 2
chk 'B feed (connection)' 200 $G/api/v1/feed/$B -H "Authorization: Bearer $BT"
IN=$(py "'$P1' in d['postIds'] and '$P2' in d['postIds']")
[ "$IN" = 'True' ] && ok 'B received both posts via fan-out' || bad 'B feed contents' "$IN" True
chk 'C feed (second connection)' 200 $G/api/v1/feed/$C -H "Authorization: Bearer $CT"
IN=$(py "'$P1' in d['postIds'] and '$P2' in d['postIds']")
[ "$IN" = 'True' ] && ok 'C received both posts via fan-out' || bad 'C feed contents' "$IN" True
chk 'B feed is ids only / paginated' 200 "$G/api/v1/feed/$B?page=0&size=1" -H "Authorization: Bearer $BT"
T=$(py "d['totalElements']"); H=$(py "d['hasNext']"); N=$(py "len(d['postIds'])")
  [ "$T" = '2' ] && [ "$H" = 'True' ] && [ "$N" = '1' ] && ok 'page 0 of size 1: 1 item, total 2, hasNext' || bad 'feed pagination' "total=$T hasNext=$H n=$N" 'total=2 hasNext=True n=1'
chk 'B feed page 1' 200 "$G/api/v1/feed/$B?page=1&size=1" -H "Authorization: Bearer $BT"; H=$(py "d['hasNext']")
[ "$H" = 'False' ] && ok 'last page hasNext false' || bad 'last page hasNext' "$H" False
chk 'outsider reads B feed' 403 $G/api/v1/feed/$B -H "$HA"
chk 'A deletes post P2' 204 -X DELETE $G/api/v1/posts/$P2 -H "$HA"
sleep 4
chk 'B feed after post.deleted' 200 $G/api/v1/feed/$B -H "Authorization: Bearer $BT"
GONE=$(py "'$P2' not in d['postIds']")
[ "$GONE" = 'True' ] && ok 'deleted post un-fanned from B feed' || bad 'un-fanout' "$GONE" True
chk 'B disconnects A' 200 -X DELETE $G/api/v1/users/connections/$CID -H "Authorization: Bearer $BT"
sleep 2
chk 'A posts after disconnect' 201 -X POST $G/api/v1/posts -H "$HA" -F 'content=Posted after the disconnect'; P3=$(py "d['id']")
sleep 4
chk 'B feed unchanged post-disconnect' 200 $G/api/v1/feed/$B -H "Authorization: Bearer $BT"
NB=$(py "len(d['postIds'])")
[ "$NB" = '1' ] && ok 'disconnected user got nothing new' || bad 'B feed after disconnect' "$NB" 1
chk 'C feed grew' 200 $G/api/v1/feed/$C -H "Authorization: Bearer $CT"
IN=$(py "'$P3' in d['postIds']")
[ "$IN" = 'True' ] && ok 'still-connected user received it' || bad 'C feed after new post' "$IN" True
chk 'clear B feed' 204 -X DELETE $G/api/v1/feed/$B -H "Authorization: Bearer $BT"
chk 'B feed empty after clear' 200 $G/api/v1/feed/$B -H "Authorization: Bearer $BT"; N=$(py "len(d['postIds'])")
[ "$N" = '0' ] && ok 'feed cleared' || bad 'feed after clear' "$N" 0

sec "9. SEARCH (projections from events)"
sleep 3
chk 'users q=Ada' 200 "$G/api/v1/search/users?q=Ada" -H "$HA"; IN=$(py "any(r['userId']=='$A' for r in d['results'])")
[ "$IN" = 'True' ] && ok 'A found by first name' || bad 'search by name' "$IN" True
chk 'users q=Countess (headline)' 200 "$G/api/v1/search/users?q=Countess" -H "$HA"; IN=$(py "any(r['userId']=='$A' for r in d['results'])")
[ "$IN" = 'True' ] && ok 'A found by headline' || bad 'search by headline' "$IN" True
chk 'users q=London (location)' 200 "$G/api/v1/search/users?q=London" -H "$HA"; IN=$(py "any(r['userId']=='$A' for r in d['results'])")
[ "$IN" = 'True' ] && ok 'A found by location' || bad 'search by location' "$IN" True
chk 'users q=Analytical (skill)' 200 "$G/api/v1/search/users?q=Analytical" -H "$HA"; IN=$(py "any(r['userId']=='$A' for r in d['results'])")
[ "$IN" = 'True' ] && ok 'A found by skill' || bad 'search by skill' "$IN" True
chk 'posts q=Distributed' 200 "$G/api/v1/search/posts?q=Distributed" -H "$HA"; IN=$(py "any(r['postId']=='$P1' for r in d['results'])")
[ "$IN" = 'True' ] && ok 'post found by content' || bad 'post search' "$IN" True
chk 'deleted post gone from search' 200 "$G/api/v1/search/posts?q=Analytical" -H "$HA"; GONE=$(py "all(r['postId']!='$P2' for r in d['results'])")
[ "$GONE" = 'True' ] && ok 'post.deleted de-indexed' || bad 'post de-index' "$GONE" True
chk 'search size=100 rejected' 400 "$G/api/v1/search/users?q=a&size=100" -H "$HA"
chk 'search missing q' 400 "$G/api/v1/search/users" -H "$HA"
chk 'search without token' 401 "$G/api/v1/search/users?q=a"

if [ "${SKIP_NOTIFICATIONS:-0}" = "1" ]; then
  printf "\n=== 10. NOTIFICATIONS - skipped (SKIP_NOTIFICATIONS=1) ===\n"
else
sec "10. NOTIFICATIONS (consumer log)"
sleep 3
NL=${NOTIF_LOG:-/tmp/notification-service.log}
cnt(){ grep -c "$1" $NL 2>/dev/null || echo 0; }
[ "$(cnt 'NOTIFY \[WELCOME\]')" -ge 3 ] && ok 'welcome notifications >= 3' || bad 'welcome count' "$(cnt 'NOTIFY \[WELCOME\]')" '>=3'
grep -q "NOTIFY \[CONNECTION_REQUEST\] to=$B actor=$A" $NL && ok 'connection request -> receiver, actor=requester' || bad 'connection request routing' 'missing' "to=$B actor=$A"
grep -q "NOTIFY \[CONNECTION_ACCEPTED\] to=$A actor=$B" $NL && ok 'connection accepted -> requester, actor=receiver' || bad 'connection accepted routing' 'missing' "to=$A actor=$B"
grep -q "NOTIFY \[POST_LIKED\] to=$A actor=$B" $NL && ok 'post liked -> post author' || bad 'post liked routing' 'missing' "to=$A actor=$B"
grep -q "NOTIFY \[POST_COMMENTED\] to=$A actor=$B" $NL && ok 'comment -> post author' || bad 'comment routing' 'missing' "to=$A actor=$B"
grep -q "NOTIFY \[POST_LIKED\] to=$A actor=$A" $NL && bad 'self-like produced a notification' 'found' 'none' || ok 'self-like suppressed'

fi

sec "SUMMARY"
printf 'passed: %d\nfailed: %d\n' "$PASS" "$FAIL"
if [ "$FAIL" -gt 0 ]; then
    printf 'failures:%b\n' "$FAILED"
    exit 1
fi

exit 0
