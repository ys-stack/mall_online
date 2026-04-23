package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.domain.SecKillResult;
import com.macro.mall.portal.domain.SecKillSubmitParam;
import com.macro.mall.portal.service.SecKillService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 秒杀业务 Controller。
 */
@Controller
@Api(tags = "SecKillController")
@Tag(name = "SecKillController", description = "秒杀业务")
@RequestMapping("/seckill")
public class SecKillController {
    @Autowired
    private SecKillService secKillService;

    /**
     * 预热秒杀库存到 Redis，真实上线一般由运营发布活动或定时任务触发。
     */
    @ApiOperation("预热秒杀库存")
    @RequestMapping(value = "/preheat/{flashPromotionProductRelationId}", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult<Integer> preheat(@PathVariable Long flashPromotionProductRelationId) {
        Integer stock = secKillService.preheat(flashPromotionProductRelationId);
        return CommonResult.success(stock);
    }

    /**
     * 提交秒杀请求，接口快速返回“已受理”，异步消费者负责最终创建订单。
     */
    @ApiOperation("提交秒杀请求")
    @RequestMapping(value = "/submit", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult<SecKillResult> submit(@RequestBody SecKillSubmitParam param) {
        SecKillResult result = secKillService.submit(param);
        return CommonResult.success(result, result.getMessage());
    }

    /**
     * 查询秒杀订单创建结果，返回空表示仍在排队或建单失败。
     */
    @ApiOperation("查询秒杀订单创建结果")
    @RequestMapping(value = "/result/{flashPromotionProductRelationId}", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<String> queryResult(@PathVariable Long flashPromotionProductRelationId) {
        String orderSn = secKillService.queryOrderSn(flashPromotionProductRelationId);
        return CommonResult.success(orderSn);
    }
}
