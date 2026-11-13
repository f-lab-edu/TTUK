-- KEYS[1] meta, KEYS[2] headcount, KEYS[3] price, KEYS[4] status, KEYS[5] participants
-- ARGV[1] memberId
-- 반환: 성공 { 1, 참여 후 인원, 참여 후 단가 }, 실패 { 결과 코드 }
--   -1 초기화 안 됨 / -2 참여할 수 없는 상태 / -3 이미 참여 / -4 정원 마감

if redis.call('EXISTS', KEYS[1]) == 0 then
	return { -1 }
end

local meta = redis.call('HMGET', KEYS[1],
	'basePrice', 'floorPrice', 'discountType', 'discountValue', 'discountStepSize', 'maxHeadcount')
local basePrice = tonumber(meta[1])
local floorPrice = tonumber(meta[2])
local discountType = meta[3]
local discountValue = tonumber(meta[4])
local discountStepSize = tonumber(meta[5])
local maxHeadcount = tonumber(meta[6])

local headcount = tonumber(redis.call('GET', KEYS[2]) or 0)
local status = redis.call('GET', KEYS[4])

if status ~= 'OPEN' then
	if status == 'CLOSED' and maxHeadcount and headcount >= maxHeadcount then
		return { -4 }
	end
	return { -2 }
end

if redis.call('SISMEMBER', KEYS[5], ARGV[1]) == 1 then
	return { -3 }
end

if maxHeadcount and headcount >= maxHeadcount then
	return { -4 }
end

redis.call('SADD', KEYS[5], ARGV[1])
redis.call('EXPIREAT', KEYS[5], redis.call('EXPIRETIME', KEYS[1]))
local newHeadcount = redis.call('INCR', KEYS[2])

local discountPerStep = discountValue
if discountType == 'RATE' then
	discountPerStep = math.floor(basePrice * discountValue / 100)
end
local step = math.floor(newHeadcount / discountStepSize)
local newPrice = math.max(floorPrice, basePrice - step * discountPerStep)
redis.call('SET', KEYS[3], newPrice, 'KEEPTTL')

if maxHeadcount and newHeadcount >= maxHeadcount then
	redis.call('SET', KEYS[4], 'CLOSED', 'KEEPTTL')
end

return { 1, newHeadcount, newPrice }