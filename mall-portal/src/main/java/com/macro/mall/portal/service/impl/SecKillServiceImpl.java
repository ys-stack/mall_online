package com.macro.mall.portal.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.service.RedisService;
import com.macro.mall.mapper.OmsOrderItemMapper;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.mapper.OmsOrderSettingMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.mapper.SmsFlashPromotionMapper;
import com.macro.mall.mapper.SmsFlashPromotionProductRelationMapper;
import com.macro.mall.mapper.SmsFlashPromotionSessionMapper;
import com.macro.mall.mapper.UmsMemberReceiveAddressMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderExample;
import com.macro.mall.model.OmsOrderItem;
import com.macro.mall.model.OmsOrderSetting;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.SmsFlashPromotion;
import com.macro.mall.model.SmsFlashPromotionProductRelation;
import com.macro.mall.model.SmsFlashPromotionSession;
import com.macro.mall.model.UmsMember;
import com.macro.mall.model.UmsMemberReceiveAddress;
import com.macro.mall.model.UmsMemberReceiveAddressExample;
import com.macro.mall.portal.dao.PortalOrderDao;
import com.macro.mall.portal.dao.SecKillMessageOutboxDao;
import com.macro.mall.portal.domain.QueueEnum;
import com.macro.mall.portal.domain.SecKillMessageOutbox;
import com.macro.mall.portal.domain.SecKillMessageStatus;
import com.macro.mall.portal.domain.SecKillOrderMessage;
import com.macro.mall.portal.domain.SecKillRedisKey;
import com.macro.mall.portal.domain.SecKillResult;
import com.macro.mall.portal.domain.SecKillSubmitParam;
import com.macro.mall.portal.service.SecKillService;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.util.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀业务 Service 实现。
 */
@Slf4j
@Service
public class SecKillServiceImpl implements SecKillService {
    private static final int QUANTITY = 1;
    private static final long LUA_SUCCESS = 0L;
    private static final long LUA_SOLD_OUT = 1L;
    private static final long LUA_REPEAT = 2L;

    private static final RedisScript<Long> SECKILL_SCRIPT = RedisScript.of(
            "if redis.call('exists', KEYS[2]) == 1 then return 2 end " +
                    "local stock = tonumber(redis.call('get', KEYS[1]) or '-1') " +
                    "if stock < 0 then return 3 end " +
                    "if stock <= 0 then return 1 end " +
                    "redis.call('decrby', KEYS[1], ARGV[1]) " +
                    "redis.call('set', KEYS[2], ARGV[2], 'EX', ARGV[3]) " +
                    "return 0",
            Long.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedisService redisService;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private SmsFlashPromotionProductRelationMapper flashPromotionProductRelationMapper;
    @Autowired
    private SmsFlashPromotionMapper flashPromotionMapper;
    @Autowired
    private SmsFlashPromotionSessionMapper flashPromotionSessionMapper;
    @Autowired
    private PmsProductMapper productMapper;
    @Autowired
    private OmsOrderMapper orderMapper;
    @Autowired
    private OmsOrderItemMapper orderItemMapper;
    @Autowired
    private OmsOrderSettingMapper orderSettingMapper;
    @Autowired
    private UmsMemberReceiveAddressMapper receiveAddressMapper;
    @Autowired
    private PortalOrderDao portalOrderDao;
    @Autowired
    private SecKillMessageOutboxDao secKillMessageOutboxDao;

    @Value("${redis.key.orderId}")
    private String redisKeyOrderId;
    @Value("${redis.database}")
    private String redisDatabase;
    @Value("${mall.seckill.stock-ttl-seconds:7200}")
    private long stockTtlSeconds;
    @Value("${mall.seckill.user-hold-ttl-seconds:1800}")
    private long userHoldTtlSeconds;
    @Value("${mall.seckill.processing-ttl-seconds:600}")
    private long processingTtlSeconds;
    @Value("${mall.seckill.order-result-ttl-seconds:86400}")
    private long orderResultTtlSeconds;
    @Value("${mall.seckill.outbox.processing-timeout-seconds:60}")
    private int outboxProcessingTimeoutSeconds;

    /**
     * 将秒杀活动库存预热到 Redis，避免活动开始瞬间所有请求直接打到数据库。
     */
    @Override
    public Integer preheat(Long flashPromotionProductRelationId) {
        SmsFlashPromotionProductRelation relation = getRelationOrFail(flashPromotionProductRelationId);
        Integer stock = relation.getFlashPromotionCount() == null ? 0 : relation.getFlashPromotionCount();
        stringRedisTemplate.opsForValue().set(SecKillRedisKey.stockKey(flashPromotionProductRelationId),
                String.valueOf(stock), stockTtlSeconds, TimeUnit.SECONDS);
        return stock;
    }

    /**
     * 秒杀提交入口：Redis Lua 原子完成库存预扣和一人一单校验，成功后投递 MQ 削峰建单。
     */
    @Override
    public SecKillResult submit(SecKillSubmitParam param) {
        if (param == null || param.getFlashPromotionProductRelationId() == null || param.getMemberReceiveAddressId() == null) {
            return SecKillResult.failed(4, "秒杀参数不完整", null);
        }
        UmsMember currentMember = memberService.getCurrentMember();
        SmsFlashPromotionProductRelation relation = getRelationOrFail(param.getFlashPromotionProductRelationId());
        validateFlashPromotionWindow(relation);
        validateAddress(param.getMemberReceiveAddressId(), currentMember.getId());
        String requestId = UUID.randomUUID().toString().replace("-", "");
        SecKillOrderMessage message = buildMessage(relation, currentMember, param.getMemberReceiveAddressId(), requestId);
        insertOutboxMessage(message);
        Long result = executeSecKillScript(relation.getId(), currentMember.getId(), requestId);
        if (Objects.equals(result, LUA_SOLD_OUT)) {
            secKillMessageOutboxDao.markCanceled(message.getMessageId(), "秒杀商品已售罄");
            return SecKillResult.failed(1, "秒杀商品已售罄", relation.getId());
        }
        if (Objects.equals(result, LUA_REPEAT)) {
            secKillMessageOutboxDao.markCanceled(message.getMessageId(), "重复提交秒杀请求");
            return SecKillResult.failed(2, "请勿重复提交秒杀请求", relation.getId());
        }
        if (!Objects.equals(result, LUA_SUCCESS)) {
            secKillMessageOutboxDao.markCanceled(message.getMessageId(), "活动未预热或已结束");
            return SecKillResult.failed(3, "秒杀活动未预热或已结束", relation.getId());
        }
        secKillMessageOutboxDao.markReserved(message.getMessageId());
        try {
            sendCreateOrderMessage(message);
            secKillMessageOutboxDao.markSent(message.getMessageId());
        } catch (Exception e) {
            secKillMessageOutboxDao.markFailed(message.getMessageId(), limitError(e), nextRetryTime(1));
            log.error("Send seckill order message failed, requestId:{}", requestId, e);
        }
        return SecKillResult.accepted(requestId, relation.getId());
    }

    /**
     * 消费秒杀建单消息：先做 Redis/DB 幂等，再在事务内扣减 DB 秒杀库存并写入订单。
     */
    @Override
    @Transactional
    public void createOrder(SecKillOrderMessage message) {
        if (message == null || message.getRequestId() == null) {
            Asserts.fail("秒杀消息为空");
        }
        if (hasCreatedOrder(message)) {
            return;
        }
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                SecKillRedisKey.processingKey(message.getRequestId()),
                message.getMessageId(),
                processingTtlSeconds,
                TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        try {
            if (hasCreatedOrder(message)) {
                return;
            }
            SmsFlashPromotionProductRelation relation = getRelationOrFail(message.getFlashPromotionProductRelationId());
            PmsProduct product = getProductOrFail(message.getProductId());
            UmsMemberReceiveAddress address = validateAddress(message.getMemberReceiveAddressId(), message.getMemberId());
            int reduceCount = portalOrderDao.decreaseFlashPromotionStock(relation.getId(), message.getQuantity());
            if (reduceCount == 0) {
                Asserts.fail("秒杀库存不足");
            }
            int productStockReduceCount = portalOrderDao.decreaseProductStock(product.getId(), message.getQuantity());
            if (productStockReduceCount == 0) {
                Asserts.fail("商品库存不足");
            }
            OmsOrder order = buildOrder(message, relation, address);
            orderMapper.insert(order);
            OmsOrderItem orderItem = buildOrderItem(order, message, relation, product);
            orderItemMapper.insert(orderItem);
            redisService.set(SecKillRedisKey.orderKey(message.getFlashPromotionProductRelationId(), message.getMemberId()),
                    order.getOrderSn(), orderResultTtlSeconds);
        } finally {
            redisService.del(SecKillRedisKey.processingKey(message.getRequestId()));
        }
    }

    /**
     * 查询当前用户秒杀订单号，返回 null 表示还在排队或建单失败。
     */
    @Override
    public String queryOrderSn(Long flashPromotionProductRelationId) {
        UmsMember currentMember = memberService.getCurrentMember();
        Object orderSn = redisService.get(SecKillRedisKey.orderKey(flashPromotionProductRelationId, currentMember.getId()));
        return orderSn == null ? null : String.valueOf(orderSn);
    }

    /**
     * 扫描秒杀 outbox 并补偿投递，解决应用在 Redis 预扣后、MQ 投递前宕机造成的消息丢失问题。
     */
    @Override
    public void dispatchPendingOutboxMessages(Integer limit) {
        List<SecKillMessageOutbox> outboxList = secKillMessageOutboxDao.listDispatchable(limit, outboxProcessingTimeoutSeconds);
        if (CollectionUtils.isEmpty(outboxList)) {
            return;
        }
        for (SecKillMessageOutbox outbox : outboxList) {
            dispatchOutboxMessage(outbox);
        }
    }

    /**
     * 消费失败时回补 Redis 库存并删除用户资格 Key，让用户可以重新参与，避免资格被永久占用。
     */
    @Override
    public void compensate(SecKillOrderMessage message) {
        if (message == null) {
            return;
        }
        String stockKey = SecKillRedisKey.stockKey(message.getFlashPromotionProductRelationId());
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(stockKey))) {
            redisService.incr(stockKey, message.getQuantity());
        }
        redisService.del(SecKillRedisKey.userKey(message.getFlashPromotionProductRelationId(), message.getMemberId()));
        if (message.getRequestId() != null) {
            redisService.del(SecKillRedisKey.processingKey(message.getRequestId()));
        }
    }

    /**
     * 新增秒杀 outbox 消息，先把“要发的消息”落到 DB，避免 Redis 预扣后应用宕机造成消息丢失。
     */
    private void insertOutboxMessage(SecKillOrderMessage message) {
        SecKillMessageOutbox outbox = new SecKillMessageOutbox();
        outbox.setMessageId(message.getMessageId());
        outbox.setRequestId(message.getRequestId());
        outbox.setMemberId(message.getMemberId());
        outbox.setMemberUsername(message.getMemberUsername());
        outbox.setMemberReceiveAddressId(message.getMemberReceiveAddressId());
        outbox.setFlashPromotionProductRelationId(message.getFlashPromotionProductRelationId());
        outbox.setProductId(message.getProductId());
        outbox.setQuantity(message.getQuantity());
        outbox.setStatus(SecKillMessageStatus.INIT);
        outbox.setRetryCount(0);
        secKillMessageOutboxDao.insert(outbox);
    }

    /**
     * 补偿投递单条 outbox 消息，只有确认 Redis 资格 Key 仍存在时才投递。
     */
    private void dispatchOutboxMessage(SecKillMessageOutbox outbox) {
        SecKillOrderMessage message = buildMessage(outbox);
        String reservedRequestId = stringRedisTemplate.opsForValue()
                .get(SecKillRedisKey.userKey(message.getFlashPromotionProductRelationId(), message.getMemberId()));
        if (!Objects.equals(reservedRequestId, message.getRequestId())) {
            if (hasCreatedOrder(message)) {
                secKillMessageOutboxDao.markSent(message.getMessageId());
            } else {
                if (!SecKillMessageStatus.INIT.equals(outbox.getStatus())) {
                    compensate(message);
                }
                secKillMessageOutboxDao.markCanceled(message.getMessageId(), "Redis 秒杀资格不存在，取消补发");
            }
            return;
        }
        secKillMessageOutboxDao.markProcessing(message.getMessageId());
        try {
            sendCreateOrderMessage(message);
            secKillMessageOutboxDao.markSent(message.getMessageId());
        } catch (Exception e) {
            int retryCount = outbox.getRetryCount() == null ? 1 : outbox.getRetryCount() + 1;
            secKillMessageOutboxDao.markFailed(message.getMessageId(), limitError(e), nextRetryTime(retryCount));
            log.warn("Dispatch seckill outbox failed, messageId:{}, retryCount:{}", message.getMessageId(), retryCount, e);
        }
    }

    /**
     * 将 outbox 记录转换成秒杀建单消息。
     */
    private SecKillOrderMessage buildMessage(SecKillMessageOutbox outbox) {
        SecKillOrderMessage message = new SecKillOrderMessage();
        message.setMessageId(outbox.getMessageId());
        message.setRequestId(outbox.getRequestId());
        message.setMemberId(outbox.getMemberId());
        message.setMemberUsername(outbox.getMemberUsername());
        message.setMemberReceiveAddressId(outbox.getMemberReceiveAddressId());
        message.setFlashPromotionProductRelationId(outbox.getFlashPromotionProductRelationId());
        message.setProductId(outbox.getProductId());
        message.setQuantity(outbox.getQuantity());
        message.setCreateTime(outbox.getCreateTime());
        return message;
    }

    /**
     * 执行 Redis Lua 脚本，保证库存预扣和一人一单校验是一个原子操作。
     */
    private Long executeSecKillScript(Long relationId, Long memberId, String requestId) {
        List<String> keys = Arrays.asList(SecKillRedisKey.stockKey(relationId), SecKillRedisKey.userKey(relationId, memberId));
        return stringRedisTemplate.execute(SECKILL_SCRIPT, keys, String.valueOf(QUANTITY), requestId, String.valueOf(userHoldTtlSeconds));
    }

    /**
     * 发送持久化秒杀建单消息，RabbitMQ 宕机恢复后消息不会因为内存队列丢失。
     */
    private void sendCreateOrderMessage(SecKillOrderMessage message) {
        rabbitTemplate.invoke(operations -> {
            operations.convertAndSend(
                    QueueEnum.QUEUE_SECKILL_ORDER_CREATE.getExchange(),
                    QueueEnum.QUEUE_SECKILL_ORDER_CREATE.getRouteKey(),
                    message,
                    new MessagePostProcessor() {
                        @Override
                        public Message postProcessMessage(Message rabbitMessage) {
                            rabbitMessage.getMessageProperties().setMessageId(message.getMessageId());
                            rabbitMessage.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                            return rabbitMessage;
                        }
                    });
            operations.waitForConfirmsOrDie(5000);
            return true;
        });
    }

    /**
     * 构建秒杀建单消息，消息里只放建单必须字段，商品价格等关键数据仍以 DB 为准。
     */
    private SecKillOrderMessage buildMessage(SmsFlashPromotionProductRelation relation,
                                             UmsMember member,
                                             Long memberReceiveAddressId,
                                             String requestId) {
        SecKillOrderMessage message = new SecKillOrderMessage();
        message.setMessageId(UUID.randomUUID().toString().replace("-", ""));
        message.setRequestId(requestId);
        message.setMemberId(member.getId());
        message.setMemberUsername(member.getUsername());
        message.setMemberReceiveAddressId(memberReceiveAddressId);
        message.setFlashPromotionProductRelationId(relation.getId());
        message.setProductId(relation.getProductId());
        message.setQuantity(QUANTITY);
        message.setCreateTime(new Date());
        return message;
    }

    /**
     * 构建秒杀订单主表数据。
     */
    private OmsOrder buildOrder(SecKillOrderMessage message,
                                SmsFlashPromotionProductRelation relation,
                                UmsMemberReceiveAddress address) {
        BigDecimal payAmount = relation.getFlashPromotionPrice().multiply(BigDecimal.valueOf(message.getQuantity()));
        OmsOrder order = new OmsOrder();
        order.setMemberId(message.getMemberId());
        order.setMemberUsername(message.getMemberUsername());
        order.setCreateTime(new Date());
        order.setTotalAmount(payAmount);
        order.setPayAmount(payAmount);
        order.setFreightAmount(BigDecimal.ZERO);
        order.setPromotionAmount(BigDecimal.ZERO);
        order.setIntegrationAmount(BigDecimal.ZERO);
        order.setCouponAmount(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setPayType(0);
        order.setSourceType(1);
        order.setStatus(0);
        order.setOrderType(1);
        order.setPromotionInfo("秒杀活动 relationId=" + relation.getId());
        order.setBillType(0);
        order.setConfirmStatus(0);
        order.setDeleteStatus(0);
        order.setUseIntegration(0);
        order.setIntegration(0);
        order.setGrowth(0);
        order.setNote(buildRequestNote(message.getRequestId()));
        order.setReceiverName(address.getName());
        order.setReceiverPhone(address.getPhoneNumber());
        order.setReceiverPostCode(address.getPostCode());
        order.setReceiverProvince(address.getProvince());
        order.setReceiverCity(address.getCity());
        order.setReceiverRegion(address.getRegion());
        order.setReceiverDetailAddress(address.getDetailAddress());
        OmsOrderSetting setting = orderSettingMapper.selectByPrimaryKey(1L);
        if (setting != null) {
            order.setAutoConfirmDay(setting.getConfirmOvertime());
        }
        order.setOrderSn(generateSecKillOrderSn());
        return order;
    }

    /**
     * 构建秒杀订单明细。
     */
    private OmsOrderItem buildOrderItem(OmsOrder order,
                                        SecKillOrderMessage message,
                                        SmsFlashPromotionProductRelation relation,
                                        PmsProduct product) {
        OmsOrderItem orderItem = new OmsOrderItem();
        orderItem.setOrderId(order.getId());
        orderItem.setOrderSn(order.getOrderSn());
        orderItem.setProductId(product.getId());
        orderItem.setProductPic(product.getPic());
        orderItem.setProductName(product.getName());
        orderItem.setProductBrand(product.getBrandName());
        orderItem.setProductSn(product.getProductSn());
        orderItem.setProductPrice(relation.getFlashPromotionPrice());
        orderItem.setProductQuantity(message.getQuantity());
        orderItem.setProductCategoryId(product.getProductCategoryId());
        orderItem.setPromotionName("秒杀活动");
        orderItem.setPromotionAmount(BigDecimal.ZERO);
        orderItem.setCouponAmount(BigDecimal.ZERO);
        orderItem.setIntegrationAmount(BigDecimal.ZERO);
        orderItem.setRealAmount(relation.getFlashPromotionPrice());
        orderItem.setGiftIntegration(0);
        orderItem.setGiftGrowth(0);
        return orderItem;
    }

    /**
     * 判断订单是否已经创建，Redis 是快速路径，DB 备注中的 requestId 是最终幂等兜底。
     */
    private boolean hasCreatedOrder(SecKillOrderMessage message) {
        if (Boolean.TRUE.equals(redisService.hasKey(SecKillRedisKey.orderKey(message.getFlashPromotionProductRelationId(), message.getMemberId())))) {
            return true;
        }
        OmsOrderExample example = new OmsOrderExample();
        example.createCriteria()
                .andMemberIdEqualTo(message.getMemberId())
                .andOrderTypeEqualTo(1)
                .andNoteEqualTo(buildRequestNote(message.getRequestId()));
        return orderMapper.countByExample(example) > 0;
    }

    /**
     * 校验秒杀活动商品关系是否存在。
     */
    private SmsFlashPromotionProductRelation getRelationOrFail(Long relationId) {
        SmsFlashPromotionProductRelation relation = flashPromotionProductRelationMapper.selectByPrimaryKey(relationId);
        if (relation == null) {
            Asserts.fail("秒杀活动商品不存在");
        }
        return relation;
    }

    /**
     * 校验秒杀活动和场次是否正在进行，防止绕过首页直接调用秒杀接口。
     */
    private void validateFlashPromotionWindow(SmsFlashPromotionProductRelation relation) {
        SmsFlashPromotion flashPromotion = flashPromotionMapper.selectByPrimaryKey(relation.getFlashPromotionId());
        if (flashPromotion == null || !Integer.valueOf(1).equals(flashPromotion.getStatus())) {
            Asserts.fail("秒杀活动未启用");
        }
        Date now = new Date();
        Date currentDate = DateUtil.getDate(now);
        if (flashPromotion.getStartDate() == null || flashPromotion.getEndDate() == null
                || currentDate.before(flashPromotion.getStartDate())
                || currentDate.after(flashPromotion.getEndDate())) {
            Asserts.fail("秒杀活动不在有效日期内");
        }
        SmsFlashPromotionSession session = flashPromotionSessionMapper.selectByPrimaryKey(relation.getFlashPromotionSessionId());
        if (session == null || !Integer.valueOf(1).equals(session.getStatus())) {
            Asserts.fail("秒杀场次未启用");
        }
        Date currentTime = DateUtil.getTime(now);
        if (session.getStartTime() == null || session.getEndTime() == null
                || currentTime.before(session.getStartTime())
                || currentTime.after(session.getEndTime())) {
            Asserts.fail("秒杀场次未开始或已结束");
        }
    }

    /**
     * 校验商品是否存在。
     */
    private PmsProduct getProductOrFail(Long productId) {
        PmsProduct product = productMapper.selectByPrimaryKey(productId);
        if (product == null) {
            Asserts.fail("商品不存在");
        }
        return product;
    }

    /**
     * 校验收货地址属于当前会员，消费者线程没有登录态，所以这里直接按 memberId 查询。
     */
    private UmsMemberReceiveAddress validateAddress(Long addressId, Long memberId) {
        UmsMemberReceiveAddressExample example = new UmsMemberReceiveAddressExample();
        example.createCriteria().andIdEqualTo(addressId).andMemberIdEqualTo(memberId);
        List<UmsMemberReceiveAddress> addressList = receiveAddressMapper.selectByExample(example);
        if (CollectionUtils.isEmpty(addressList)) {
            Asserts.fail("收货地址不存在");
        }
        return addressList.get(0);
    }

    /**
     * 生成秒杀订单号，和普通订单共用 Redis 自增序列但使用 seckill 后缀隔离。
     */
    private String generateSecKillOrderSn() {
        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String key = redisDatabase + ":" + redisKeyOrderId + ":seckill:" + date;
        Long increment = redisService.incr(key, 1);
        StringBuilder sb = new StringBuilder();
        sb.append(date);
        sb.append("10");
        sb.append("00");
        String incrementStr = increment.toString();
        if (incrementStr.length() <= 6) {
            sb.append(String.format("%06d", increment));
        } else {
            sb.append(incrementStr);
        }
        return sb.toString();
    }

    /**
     * 构建订单备注中的请求幂等标识。
     */
    private String buildRequestNote(String requestId) {
        return "秒杀请求:" + requestId;
    }

    /**
     * 计算 outbox 下一次补偿投递时间，使用指数退避避免 MQ 故障时打爆 Broker。
     */
    private Date nextRetryTime(int retryCount) {
        int safeRetryCount = Math.min(Math.max(retryCount, 1), 6);
        long delaySeconds = Math.min(60L, 1L << safeRetryCount);
        return new Date(System.currentTimeMillis() + delaySeconds * 1000);
    }

    /**
     * 限制异常信息长度，避免错误堆栈撑爆 outbox 表字段。
     */
    private String limitError(Exception e) {
        if (e == null || e.getMessage() == null) {
            return null;
        }
        String message = e.getMessage();
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
