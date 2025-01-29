package com.hmdp.service.impl;

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
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.RedissonLock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private RedissonClient redissonClient;
    
    @Autowired
    private RedisIdWorker worker;

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
        Lock lock = redissonClient.getLock("order:" + userid);
        //SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + userid, stringRedisTemplate);
        boolean b = lock.tryLock();
        if(!b){
            return Result.fail("不允许重复下单");
        }
        try {
            return voucherService.createVolucjerOrder(voucherId, voucher);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }


    @Transactional
    public Result createVolucjerOrder(Long voucherId, SeckillVoucher voucher) {
        Long userid = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userid).eq("voucher_id", voucherId).count();
        if(count == 1){
            return Result.fail("用户已经买过了");
        }
        UpdateWrapper<SeckillVoucher> wrapper = new UpdateWrapper();
        wrapper.set("stock", voucher.getStock() - 1);
        wrapper.eq("voucher_id", voucherId);
        wrapper.eq("stock", voucher.getStock());
        boolean update = seckillVoucherService.update(wrapper);
        if(!update){
            return Result.fail("库存不足");
        }
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(worker.nextId("order"));
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userid);
        save(voucherOrder);
        return Result.ok(voucherOrder.getId());
    }
}
