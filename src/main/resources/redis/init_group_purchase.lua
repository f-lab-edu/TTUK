-- KEYS[1] meta, KEYS[2] headcount, KEYS[3] price, KEYS[4] status
-- ARGV[1] basePrice, ARGV[2] floorPrice, ARGV[3] discountType, ARGV[4] discountValue,
-- ARGV[5] discountStepSize, ARGV[6] maxHeadcount('' = 무제한),
-- ARGV[7] closeAt(epoch 초), ARGV[8] keepUntil(closeAt + 1일, epoch 초)

if redis.call('EXISTS', KEYS[1]) == 1 then
	return 0
end

redis.call('HSET', KEYS[1],
	'basePrice', ARGV[1],
	'floorPrice', ARGV[2],
	'discountType', ARGV[3],
	'discountValue', ARGV[4],
	'discountStepSize', ARGV[5],
	'closeAt', ARGV[7])
if ARGV[6] ~= '' then
	redis.call('HSET', KEYS[1], 'maxHeadcount', ARGV[6])
end
redis.call('EXPIREAT', KEYS[1], ARGV[8])

redis.call('SET', KEYS[2], 0, 'EXAT', ARGV[8])
redis.call('SET', KEYS[3], ARGV[1], 'EXAT', ARGV[8])
redis.call('SET', KEYS[4], 'OPEN', 'EXAT', ARGV[7])

return 1