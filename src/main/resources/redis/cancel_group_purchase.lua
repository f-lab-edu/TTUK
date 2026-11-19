-- KEYS[1] meta, KEYS[2] headcount, KEYS[3] price, KEYS[4] status, KEYS[5] participants
-- 반환: { 결과 코드, 취소 시점 인원, 취소 시점 단가, 참여자 수 } 또는 { 결과 코드 }
--    1 이번에 CANCELED로 바꿈
--    2 이미 CANCELED (앞의 취소가 DB에서 실패했을 수 있음 → 자바가 DB로 판단)
--   -1 키 없음 / -2 진행 중 아님(정원 마감·마감 시각 지남) / -3 인원·단가 값 이상

if redis.call('EXISTS', KEYS[1]) == 0 then
	return { -1 }
end

local status = redis.call('GET', KEYS[4])
if status ~= 'OPEN' and status ~= 'CANCELED' then
	return { -2 }
end

local headcount = tonumber(redis.call('GET', KEYS[2]))
local price = tonumber(redis.call('GET', KEYS[3]))
if not headcount or not price then
	return { -3 }
end

local code = 2
if status == 'OPEN' then
	redis.call('SET', KEYS[4], 'CANCELED', 'KEEPTTL')
	code = 1
end

return { code, headcount, price, redis.call('SCARD', KEYS[5]) }