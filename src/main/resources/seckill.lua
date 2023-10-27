-- 秒杀券ID
local voucherId = ARGV[1]
-- 用户ID
local userId = ARGV[2]

-- 库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 订单key
local orderKey = 'seckill:order:' .. userId

-- 判断库存是否充足
-- 从redis取出的值为string类型，使用tonumber转化为number类型
if (tonumber(redis.call('get', stockKey)) <= 0) then
    -- 库存不足，返回1
    return 1
end

-- 判断用户是否下单
if (redis.call('sismember', orderKey, userId) == 1) then
    -- 用户已经下过单，重复下单,返回2
    return 2
end

-- 扣减库存
redis.call('incrby', stockKey, -1)
-- 将userId存入当前优惠券的set集合
redis.call('sadd', orderKey, userId)

return 0
