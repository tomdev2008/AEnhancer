package com.baidu.ascheduler.processor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.baidu.ascheduler.cache.driver.CacheDriver;
import com.baidu.ascheduler.exception.IllegalParamException;
import com.baidu.ascheduler.exception.UnexpectedStateException;
import com.baidu.ascheduler.model.Aggregation;
import com.baidu.ascheduler.model.ProcessContext;

public class AggrCacheProcessor extends AbsCacheProcessor {

    private Logger logger = LoggerFactory.getLogger(AggrCacheProcessor.class);

    /**
     * Object p 为参数中的集合对象
     */
    @Override
    public Object process(ProcessContext ctx, Object p) throws Throwable {
        Aggregation param = getParam(p);
        Aggregation result = new Aggregation(ctx.getRetType());
        CacheDriver driver = ctx.getCacheDriver();
        // 结束
        if (param.isEmpty()) {
            return result.toInstance();
        }
        List<String> keys = new ArrayList<String>();
        for (Object obj : param) {
            if (null == obj) {
                logger.error("the object in parameter is null, which will be skipped");
                continue;
            }
            keys.add(getKey(ctx.replaceArgsWithKeys(ctx.getKeyFromParam(obj)), ctx));
        }

        // 获取缓存中的数据
        List<Object> cachedResult = driver.getAll(keys, ctx.getNameSpace());
        // 接口会按照入参的顺序返回结果，对于未命中的参数会返回null.所以结果集合大小必须和keys一样
        assertSize(cachedResult, keys);
        // 遍历对比key和cache中的结果
        Iterator<?> pIter = param.iterator();
        Iterator<?> rIter = cachedResult.iterator();
        // 未缓存的参数
        Aggregation unCachedParam = new Aggregation(ctx.getAggParamType());
        while (pIter.hasNext()) {
            // 同步遍历，同步跳过
            Object resultElement = rIter.next();
            Object paramElement = pIter.next();
            // driver返回的null值被认为是没有缓存的
            if (resultElement == null) {
                unCachedParam.add(paramElement);
            } else {
                result.add(resultElement);
            }
        }
        Object nextParam = ctx.replaceArgsWithKeys(unCachedParam.toInstance());
        Object rawResult = decoratee.process(ctx, nextParam);
        if (null != rawResult) {
            if (!(rawResult instanceof Aggregation)) {
                throw new IllegalParamException("non aggregation param for cache processor");
            }
            Aggregation unCachedResult = Aggregation.class.cast(param);
            // 缓存这部分数据
            cacheUnCached(unCachedResult, unCachedParam, ctx, driver);
            result.add(unCachedResult);
        }

        return result;
    }

    /**
     * 
     * @param unCachedResult
     * @param unCachedParam
     * @param ctx
     * @param driver
     * @throws UnexpectedStateException
     * @throws IllegalParamException
     */
    private void cacheUnCached(Aggregation unCachedResult, Aggregation unCachedParam, ProcessContext ctx,
            CacheDriver driver) throws UnexpectedStateException, IllegalParamException {
        assert !unCachedResult.isEmpty();
        // 生成批量缓存的kv
        List<String> unCachedKeys = new LinkedList<String>();
        List<Object> unCachedDatas = new LinkedList<Object>();

        // 根据result获取key
        if (!ctx.relyOnSeqResult()) {
            // 只遍历结果集
            for (Object resultElement : unCachedResult) {
                // XXX 注意：这里跳过了null的结果，如果原来接口返回null，则不将它缓存下来
                if (null == resultElement) {
                    logger.error("the element got from procedure contains nill, which won't be saved to cache");
                    continue;
                }
                unCachedKeys.add(getKey(ctx.replaceArgsWithKeys(ctx.getKeyFromResult(resultElement)), ctx));
                unCachedDatas.add(resultElement);
            }
            assertSize(unCachedDatas, unCachedKeys);
        } else { // rely on result is sequential、
            // 顺序的话，依赖参数与结果集的顺序。所以，大小必须一样
            assertSize(unCachedResult, unCachedParam);
            Iterator<?> urIter = unCachedResult.iterator(); // uncached result iterator
            Iterator<?> upIter = unCachedParam.iterator(); // uncached param iterator
            while (upIter.hasNext()) {
                // 同步遍历结果集和参数集
                Object uData = urIter.next();
                Object uParam = upIter.next();
                if (null != uData) { // XXX 这里与非顺序的是一样的，如果null就不缓存
                    unCachedKeys.add(getKey(ctx.replaceArgsWithKeys(ctx.getKeyFromParam(uParam)), ctx));
                    unCachedDatas.add(uData);
                }
            }

        }
        // 缓存这部分数据
        logger.info("unCached data (order is disrupted size {}) will be saved (expiration: {}) ", unCachedKeys.size(),
                ctx.getExpiration());
        driver.setAll(unCachedKeys, unCachedDatas, ctx.getExpiration(), ctx.getNameSpace());
        // 加入result的集合
        // result.add(unCachedResult)
    }

    private Aggregation getParam(Object p) throws IllegalParamException {
        if (p == null) {
            throw new IllegalParamException("null param for cache processor");
        }
        if (!(p instanceof Aggregation)) {
            throw new IllegalParamException("non aggregation param for cache processor");
        }
        return Aggregation.class.cast(p);
    }

    private DecoratableProcessor decoratee;

    @Override
    public DecoratableProcessor decorate(DecoratableProcessor decoratee) {
        this.decoratee = decoratee;
        return this;
    }

}