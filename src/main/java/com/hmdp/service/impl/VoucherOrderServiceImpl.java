package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.RedissonLock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private VoucherOrderServiceImpl voucherService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    private BlockingQueue<VoucherOrder> blockingQueue = new ArrayBlockingQueue<>(1024 * 1024);
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    public static final String queueName = "streams.order";

    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {
            while (true){
                try {
                    List<MapRecord<String, Object, Object>> read = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    if(read == null | read.isEmpty()) continue;
                    MapRecord<String, Object, Object> entries = read.get(0);
                    Map<Object, Object> value = entries.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", entries.getId());
//                    VoucherOrder take = blockingQueue.take();
                    HandleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    handlePendingList();
                }

            }
        }

        private void handlePendingList() {
            while (true){
                try {
                    List<MapRecord<String, Object, Object>> read = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    if(read == null | read.isEmpty()) break;
                    MapRecord<String, Object, Object> entries = read.get(0);
                    Map<Object, Object> value = entries.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", entries.getId());
//                    VoucherOrder take = blockingQueue.take();
                    HandleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理异常");
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }

            }
        }

    }
    private void HandleVoucherOrder(VoucherOrder voucherOrder) {
        voucherService.createVolucjerOrder(voucherOrder);
    }
    
    @Autowired
    private RedisIdWorker worker;
    @Autowired
    private RedisIdWorker redisIdWorker;

    @Override
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if(voucher.getBeginTime().isBefore(LocalDateTime.now())){
            return Result.fail("活动还没开始");
        }
        if(voucher.getEndTime().isAfter(LocalDateTime.now())){
            return Result.fail("活动已经结束");
        }
        if(voucher.getStock() < 1){
            return Result.fail("优惠卷库存不足");
        }
        Long userid = UserHolder.getUser().getId();
        long order = redisIdWorker.nextId("order");
        String luaScript = "if(tonumber(redis.call('get', KEYS[1])) <= 0) then" +
                "    return 1" +
                "end" +
                "if(redis.call('sismember', KEYS[2], ARGV[1]) == 1) then" +
                "    return 2" +
                "end" +
                "redis.call('incrby', KEYS[1], -1)" +
                "redis.call('sadd', KEYS[2], ARGV[1])" +
                "redis.call('xadd', *, 'userId', ARGV[1], 'voucherId', ARGV[2], 'id', ARGV[3])" +
                "return 0";
        Long result = (Long) redisTemplate.execute(
                new DefaultRedisScript<>(luaScript, Long.class),
                Arrays.asList(RedisConstants.SECKILL_STOCK_KEY + voucherId, RedisConstants.SECKILL_ORDER_KEY + userid),
                Arrays.asList(userid.toString(), voucherId.toString(), String.valueOf(order)));

        int r = result.intValue();
        if(r != 0){
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
//
//        VoucherOrder voucherOrder = new VoucherOrder();
//        voucherOrder.setId(order);
//        voucherOrder.setVoucherId(voucherId);
//        voucherOrder.setUserId(userid);
//
//        blockingQueue.add(voucherOrder);

        //Lock lock = redissonClient.getLock("order:" + userid);
//        //SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + userid, stringRedisTemplate);
//        boolean b = lock.tryLock();
//        if(!b){
//            return Result.fail("不允许重复下单");
//        }
//        try {
//            return voucherService.createVolucjerOrder(voucherId, voucher);
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        } finally {
//            lock.unlock();
//        }
        return Result.ok();
    }


    @Transactional
    public void createVolucjerOrder(VoucherOrder voucherOrder) {
        Long userid = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        Integer count = query().eq("user_id", userid).eq("voucher_id", voucherId).count();
        if(count > 0){
            log.error("用户已经买过一次");
        }
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id", voucherId).gt("stock", 0).update();
        if(!success){
            log.error("库存不足");
        }
        save(voucherOrder);
    }
}
