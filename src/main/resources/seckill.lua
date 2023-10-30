-- 1.参数列表
-- 秒杀券ID
local voucherId = ARGV[1]
-- 用户ID
local userId = ARGV[2]
-- 订单ID
--local orderId = ARGV[3]

-- 2.key列表
-- 库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 订单key
local orderKey = 'seckill:order:' .. userId

-- 3.判断库存是否充足
-- 从redis取出的值为string类型，使用tonumber转化为number类型
if (tonumber(redis.call('get', stockKey)) <= 0) then
    -- 库存不足，返回1
    return 1
end

-- 4.判断用户是否重复下单
if (redis.call('sismember', orderKey, userId) == 1) then
    -- 重复下单,返回2
    return 2
end

-- 5.扣减库存
redis.call('incrby', stockKey, -1)
-- 6.将userId存入当前优惠券的set集合
redis.call('sadd', orderKey, userId)
-- 7.发送消息到Stream消息队列中
--redis.call('sadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)

return 0
