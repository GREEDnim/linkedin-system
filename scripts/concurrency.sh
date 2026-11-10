#!/usr/bin/env bash
#
# Concurrency checks: the behaviours that are correct by design but have only been proven
# sequentially.
#
#   ./scripts/concurrency.sh                 # 8 parallel users
#   USERS=20 ./scripts/concurrency.sh
#
# What it proves, and why each one is worth doing:
#
#   1. likeCount after N parallel likes  - the counter is incremented in SQL, not read-modify-
#      write, so a lost update would show as a count below N. Duplicate likes are also an
#      opportunity for the unique key to reject a request that the service check let through.
#   2. commentCount after N parallel comments - same arithmetical hazard, different table.
#   3. one refresh token used by N callers at once - rotation consumes the token with GETDEL, so
#      exactly one caller may win. More than one success means a stolen token could be replayed
#      alongside the legitimate client.
#   4. N simultaneous duplicate connection requests - the pair is protected by a service check,
#      a unique key and (for the reverse direction) the same check again; the count of successes
#      must be exactly one.
#
# Requires the stack from scripts/e2e.sh to be running. Each run registers USERS accounts, so it
# consumes that much of the gateway's 20/minute auth budget - run it on its own, not back to
# back with the E2E suite.
#
set -uo pipefail

G=${BASE_URL:-http://localhost:8080}
USERS=${USERS:-8}
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
PASS=0; FAIL=0; FAILED=""
ok(){ PASS=$((PASS+1)); printf '  PASS  %s\n' "$1"; }
bad(){ FAIL=$((FAIL+1)); FAILED="$FAILED\n    - $1 (got $2, want $3)"; printf '  FAIL  %s  (got %s, want %s)\n' "$1" "$2" "$3"; }
field(){ python3 -c "import json,sys;print(json.load(open('$1')).get('$2'))" 2>/dev/null; }

if ! curl -sf -m 5 "$G/actuator/health" > /dev/null; then
    printf 'gateway not reachable at %s - start the stack first\n' "$G" >&2
    exit 2
fi

echo "=== setup: registering $USERS users ==="
TOKENS=()
for i in $(seq 1 "$USERS"); do
    body=$(curl -s -X POST "$G/api/v1/auth/register" -H 'Content-Type: application/json' \
        -d "{\"firstName\":\"Load$i\",\"lastName\":\"Test\",\"email\":\"load$i-$$@e2e.test\",\"password\":\"secret123\"}")
    echo "$body" > "$WORK/user.$i.json"
    TOKENS+=("$(field "$WORK/user.$i.json" accessToken)")
done
UID1=$(field "$WORK/user.1.json" userId)
UID2=$(field "$WORK/user.2.json" userId)
if [ -z "${TOKENS[0]:-}" ] || [ -z "$UID2" ]; then
    printf 'could not register test users - rate limit or stack not ready\n' >&2
    exit 2
fi

POST=$(curl -s -X POST "$G/api/v1/posts" -H "Authorization: Bearer ${TOKENS[0]}" -F 'content=Concurrency probe post')
echo "$POST" > "$WORK/post.json"
PID=$(field "$WORK/post.json" id)
if [ -z "$PID" ]; then
    printf 'could not create the probe post\n' >&2
    exit 2
fi

# ---------------------------------------------------------------- parallel likes
printf "\n=== $USERS parallel likes on one post ==="
for i in $(seq 1 "$USERS"); do
    ( curl -s -o /dev/null -w '%{http_code}\n' -X PUT "$G/api/v1/posts/$PID/like" \
        -H "Authorization: Bearer ${TOKENS[$((i-1))]}" > "$WORK/like.$i" ) &
done
wait
cat "$WORK"/like.* > "$WORK/like.all"
OKL=$(grep -c '^200$' "$WORK/like.all")
COUNT=$(curl -s "$G/api/v1/posts/$PID" -H "Authorization: Bearer ${TOKENS[0]}" | python3 -c 'import json,sys;print(json.load(sys.stdin)["likeCount"])')
[ "$OKL" = "$USERS" ] && ok "all $USERS likes accepted ($OKL)" || bad 'likes accepted' "$OKL" "$USERS"
[ "$COUNT" = "$USERS" ] && ok "likeCount is exactly $USERS (no lost update)" || bad 'likeCount' "$COUNT" "$USERS"

# ---------------------------------------------------------------- parallel comments
printf "\n=== $USERS parallel comments on one post ==="
for i in $(seq 1 "$USERS"); do
    ( curl -s -o /dev/null -w '%{http_code}\n' -X POST "$G/api/v1/posts/$PID/comments" \
        -H "Authorization: Bearer ${TOKENS[$((i-1))]}" -H 'Content-Type: application/json' \
        -d "{\"content\":\"parallel comment $i\"}" > "$WORK/comment.$i" ) &
done
wait
cat "$WORK"/comment.* > "$WORK/comment.all"
OKC=$(grep -c '^201$' "$WORK/comment.all")
CCOUNT=$(curl -s "$G/api/v1/posts/$PID" -H "Authorization: Bearer ${TOKENS[0]}" | python3 -c 'import json,sys;print(json.load(sys.stdin)["commentCount"])')
[ "$OKC" = "$USERS" ] && ok "all $USERS comments created ($OKC)" || bad 'comments created' "$OKC" "$USERS"
[ "$CCOUNT" = "$USERS" ] && ok "commentCount is exactly $USERS" || bad 'commentCount' "$CCOUNT" "$USERS"

# ---------------------------------------------------------------- refresh race
printf "\n=== the same refresh token used by 5 callers at once ==="
RT=$(field "$WORK/user.1.json" refreshToken)
for i in 1 2 3 4 5; do
    ( curl -s -o /dev/null -w '%{http_code}\n' -X POST "$G/api/v1/auth/refresh" \
        -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$RT\"}" > "$WORK/refresh.$i" ) &
done
wait
cat "$WORK"/refresh.* > "$WORK/refresh.all"
OKR=$(grep -c '^200$' "$WORK/refresh.all")
[ "$OKR" = "1" ] && ok 'exactly one refresh succeeded (token is one-shot)' || bad 'refresh winners' "$OKR" 1

# ---------------------------------------------------------------- duplicate requests
printf "\n=== 5 simultaneous identical connection requests ==="
for i in 1 2 3 4 5; do
    ( curl -s -o /dev/null -w '%{http_code}\n' -X POST "$G/api/v1/users/$UID2/connect" \
        -H "Authorization: Bearer ${TOKENS[0]}" > "$WORK/connect.$i" ) &
done
wait
cat "$WORK"/connect.* > "$WORK/connect.all"
OKX=$(grep -c '^201$' "$WORK/connect.all")
CONFLICT=$(grep -c '^409$' "$WORK/connect.all")
[ "$OKX" = "1" ] && ok 'exactly one connection created' || bad 'connections created' "$OKX" 1
[ "$((OKX + CONFLICT))" = "5" ] && ok 'the rest were rejected with 409' || bad 'conflict responses' "$CONFLICT" 4

printf '\n=== SUMMARY ===\npassed: %d\nfailed: %d\n' "$PASS" "$FAIL"
if [ "$FAIL" -gt 0 ]; then
    printf 'failures:%b\n' "$FAILED"
    exit 1
fi
exit 0
