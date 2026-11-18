#!lua flags=no-writes
-- KEYS[1] meta, KEYS[2] headcount, KEYS[3] price, KEYS[4] status, KEYS[5] participants
-- 반환: meta 없음 {}, 있으면 { status, headcount, price, 참여자 memberId... } (모두 문자열, 없는 값은 '')

if redis.call('EXISTS', KEYS[1]) == 0 then
	return {}
end

local result = {
	redis.call('GET', KEYS[4]) or '',
	redis.call('GET', KEYS[2]) or '',
	redis.call('GET', KEYS[3]) or ''
}
for _, memberId in ipairs(redis.call('SMEMBERS', KEYS[5])) do
	result[#result + 1] = memberId
end
return result