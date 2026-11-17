-- KEYS[1] meta, KEYS[2] headcount, KEYS[3] price, KEYS[4] status, KEYS[5] participants
-- ARGV[1] memberId, ARGV[2] 지금 시각(epoch 초), ARGV[3] 마감 몇 초 전까지 취소할 수 있는지
-- 반환: 성공 { 1, 취소 후 인원, 취소 후 단가 }, 실패 { 결과 코드 }
--   -1 키 없음 / -2 참여 중 아님 / -3 진행 중 아님 / -4 취소 기한 지남 / -5 인원 값 이상

if redis.call('EXISTS', KEYS[1]) == 0 then
	return { -1 }
end

if redis.call('SISMEMBER', KEYS[5], ARGV[1]) == 0 then
	return { -2 }
end

if redis.call('GET', KEYS[4]) ~= 'OPEN' then
	return { -3 }
end

local meta = redis.call('HMGET', KEYS[1],
	'basePrice', 'floorPrice', 'discountType', 'discountValue', 'discountStepSize', 'closeAt')
local basePrice = tonumber(meta[1])
local floorPrice = tonumber(meta[2])
local discountType = meta[3]
local discountValue = tonumber(meta[4])
local discountStepSize = tonumber(meta[5])
local closeAt = tonumber(meta[6])

if tonumber(ARGV[2]) >= closeAt - tonumber(ARGV[3]) then
	return { -4 }
end

local headcount = tonumber(redis.call('GET', KEYS[2]))
if not headcount or headcount < 1 then
	return { -5 }
end

local newHeadcount = redis.call('DECR', KEYS[2])
redis.call('SREM', KEYS[5], ARGV[1])

local discountPerStep = discountValue
if discountType == 'RATE' then
	discountPerStep = math.floor(basePrice * discountValue / 100)
end
local step = math.floor(newHeadcount / discountStepSize)
local newPrice = math.max(floorPrice, basePrice - step * discountPerStep)
redis.call('SET', KEYS[3], newPrice, 'KEEPTTL')

return { 1, newHeadcount, newPrice }