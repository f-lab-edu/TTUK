-- KEYS[1] meta, KEYS[2] headcount, KEYS[3] price, KEYS[4] status, KEYS[5] participants
-- ARGV[1] memberId
-- 반환: 1 되돌림 / 0 되돌릴 것 없음

if redis.call('EXISTS', KEYS[1]) == 0 then
	return 0
end

if redis.call('SREM', KEYS[5], ARGV[1]) == 0 then
	return 0
end

local newHeadcount = redis.call('DECR', KEYS[2])

local meta = redis.call('HMGET', KEYS[1],
	'basePrice', 'floorPrice', 'discountType', 'discountValue', 'discountStepSize', 'maxHeadcount')
local basePrice = tonumber(meta[1])
local floorPrice = tonumber(meta[2])
local discountType = meta[3]
local discountValue = tonumber(meta[4])
local discountStepSize = tonumber(meta[5])
local maxHeadcount = tonumber(meta[6])

local discountPerStep = discountValue
if discountType == 'RATE' then
	discountPerStep = math.floor(basePrice * discountValue / 100)
end
local step = math.floor(newHeadcount / discountStepSize)
local newPrice = math.max(floorPrice, basePrice - step * discountPerStep)
redis.call('SET', KEYS[3], newPrice, 'KEEPTTL')

if redis.call('GET', KEYS[4]) == 'CLOSED' and maxHeadcount and newHeadcount < maxHeadcount then
	redis.call('SET', KEYS[4], 'OPEN', 'KEEPTTL')
end

return 1