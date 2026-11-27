-- KEYS[1] meta, KEYS[2] headcount, KEYS[3] price, KEYS[4] status, KEYS[5] version
-- 반환: meta 없음 {}, 있으면
--   { version, status, headcount, price, basePrice, floorPrice, discountType, discountValue,
--     discountStepSize, maxHeadcount, closeAt } (모두 문자열, 없는 값은 '')
-- version은 부를 때마다 1씩 늘린다. version 키가 없으면(첫 방송이거나 Redis가 비워진 뒤)
-- Redis 서버 시각(밀리초)으로 시작해서, 화면이 이미 받은 version보다 항상 크게 한다.

if redis.call('EXISTS', KEYS[1]) == 0 then
	return {}
end

local status = redis.call('GET', KEYS[4]) or ''
local headcount = redis.call('GET', KEYS[2]) or ''
local price = redis.call('GET', KEYS[3]) or ''
local meta = redis.call('HMGET', KEYS[1],
	'basePrice', 'floorPrice', 'discountType', 'discountValue', 'discountStepSize', 'maxHeadcount', 'closeAt')

local version = redis.call('INCR', KEYS[5])
if version == 1 then
	local now = redis.call('TIME')
	version = tonumber(now[1]) * 1000 + math.floor(tonumber(now[2]) / 1000)
	redis.call('SET', KEYS[5], string.format('%.0f', version))
end
local expireAt = redis.call('EXPIRETIME', KEYS[1])
if expireAt > 0 then
	redis.call('EXPIREAT', KEYS[5], expireAt)
end

return {
	string.format('%.0f', version),
	status, headcount, price,
	meta[1] or '', meta[2] or '', meta[3] or '', meta[4] or '',
	meta[5] or '', meta[6] or '', meta[7] or ''
}