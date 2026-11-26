-- KEYS[1] meta, KEYS[2] headcount, KEYS[3] price, KEYS[4] status, KEYS[5] participants
-- ARGV[1] basePrice, ARGV[2] floorPrice, ARGV[3] discountType, ARGV[4] discountValue,
-- ARGV[5] discountStepSize, ARGV[6] maxHeadcount('' = 무제한),
-- ARGV[7] closeAt(epoch 초), ARGV[8] keepUntil(closeAt + 1일, epoch 초),
-- ARGV[9] 시작 인원, ARGV[10] 시작 단가, ARGV[11] 시작 상태(OPEN / CLOSED),
-- ARGV[12]부터 DB에 JOINED로 남아 있는 참여자 id (등록 직후면 없음)
-- 반환: 1 초기화함 / 0 이미 있어서 아무것도 쓰지 않음

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

redis.call('SET', KEYS[2], ARGV[9], 'EXAT', ARGV[8])
redis.call('SET', KEYS[3], ARGV[10], 'EXAT', ARGV[8])
redis.call('SET', KEYS[4], ARGV[11], 'EXAT', ARGV[7])

redis.call('DEL', KEYS[5])
for i = 12, #ARGV do
	redis.call('SADD', KEYS[5], ARGV[i])
end
if #ARGV >= 12 then
	redis.call('EXPIREAT', KEYS[5], ARGV[8])
end

return 1