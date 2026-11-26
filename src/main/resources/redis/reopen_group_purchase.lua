-- KEYS[1] status
-- 반환: 1 CANCELED를 OPEN으로 되돌림 / 0 되돌릴 것 없음

if redis.call('GET', KEYS[1]) ~= 'CANCELED' then
	return 0
end

redis.call('SET', KEYS[1], 'OPEN', 'KEEPTTL')
return 1