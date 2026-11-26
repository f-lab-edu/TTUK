-- KEYS[1] meta, KEYS[2] headcount, KEYS[3] price, KEYS[4] status, KEYS[5] participants
-- 반환: meta 없음 {}, 있으면 { status, headcount, price, 참여자 memberId... } (모두 문자열, 없는 값은 '')
-- 정산할 상태(정원이 차서 CLOSED, 또는 마감 시각이 지나 status 없음)이고 인원·단가가 있으면
-- meta에 settled 표시를 남긴다. 표시가 있으면 참여 되돌리기 Lua는 값을 바꾸지 않는다.

if redis.call('EXISTS', KEYS[1]) == 0 then
	return {}
end

local status = redis.call('GET', KEYS[4]) or ''
local headcount = redis.call('GET', KEYS[2]) or ''
local price = redis.call('GET', KEYS[3]) or ''

if status ~= 'OPEN' and status ~= 'CANCELED' and headcount ~= '' and price ~= '' then
	redis.call('HSET', KEYS[1], 'settled', '1')
end

local result = { status, headcount, price }
for _, memberId in ipairs(redis.call('SMEMBERS', KEYS[5])) do
	result[#result + 1] = memberId
end
return result