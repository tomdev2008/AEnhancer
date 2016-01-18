package com.baidu.aenhancer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import com.baidu.aenhancer.core.context.ProcessContext;
import com.baidu.aenhancer.core.processor.Processor;
import com.baidu.aenhancer.core.processor.ext.Splitable;
import com.baidu.aenhancer.core.processor.ext.impl.ReturnNull;
import com.baidu.aenhancer.entry.Collapse;
import com.baidu.aenhancer.entry.Enhancer;
import com.baidu.aenhancer.entry.Split;
import com.baidu.aenhancer.exception.CodingError;
import com.baidu.aenhancer.ext.impl.aggr.Aggr;
import com.baidu.aenhancer.ext.impl.aggr.AggrCacher;
import com.baidu.aenhancer.ext.impl.aggr.AggrSpliter;

@Service
public class BeanMock {
    @Enhancer
    public Integer testGet(Integer x) {
        return ++x;
    }

    @Aggr(param = "T(java.lang.Math).PI", result = "#this.getKey()", nameSpace = "test")
    @Enhancer
    public Map<Integer, String> testGetList(ConcurrentHashMap<Integer, String> param) {
        Map<Integer, String> ret = new HashMap<Integer, String>();
        for (Entry<Integer, String> pi : param.entrySet()) {
            ret.put(pi.getKey(), pi.getKey() + ":" + pi.getValue());
        }
        return ret;
    }

    @Enhancer(retry = 2, fallback = ReturnNull.class)
    public Integer get(Integer x) {
        throw new RuntimeException("error");
    }

    @Enhancer(fallback = ReturnNull.class)
    public void testFallback() {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        throw new RuntimeException();
    }

    @Enhancer
    public Integer getInt() {
        return 5;
    }

    @Aggr(ignList = 0)
    @Enhancer()
    public Integer getInt2(Integer f) {
        return 6;
    }

    @Aggr(cache = "NopDriver")
    @Enhancer()
    public String getStr() {
        return "OK";
    }

    @Aggr(sequential = true, aggrSize = 1, cache = "NopDriver")
    @Enhancer( //
    timeout = 100, // 超时时间
    cacher = AggrCacher.class, // 缓存策略：按集合对象中的元素缓存
    spliter = AggrSpliter.class, // 拆分成多次调用的策略：反集合元素个数拆分
    parallel = true, // 可并行
    group = "ServiceGroupA", // 所属的组
    fallback = ReturnNull.class // 降级策略
    )
    public List<String> getStrs(String[] args) {
        try {
            Thread.sleep(0);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return Arrays.asList(args);
    }

    @Aggr(cache = "")
    @Enhancer(timeout = 100)
    public String timeoutTest() {
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            // e.printStackTrace();
        }
        return "test timeout";
    }

    @Aggr(cache = "")
    @Enhancer(timeout = 12000)
    public String notTimeoutTest() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // e.printStackTrace();
        }
        return "test not timeout";
    }

    @Enhancer(spliter = CustomSpliter.class)
    public Integer costomSplit(int start, int end) {
        return end + start;
    }

    public static class CustomSpliter implements Splitable {

        @Override
        public void init(ProceedingJoinPoint jp, ApplicationContext context) throws CodingError {

        }

        @Override
        public void beforeProcess(ProcessContext ctx, Processor currentProcess) {

        }

        @Split
        public List<Object[]> kill(int start, int end) {
            List<Object[]> ret = new ArrayList<Object[]>();
            ret.add(new Object[] { start, start });
            ret.add(new Object[] { end, end });
            return ret;
        }

        @Collapse
        public Integer collapse(List<Integer> result) {
            int x = 0;
            for (Integer i : result) {
                x += i;
            }
            return x;
        }

    }
}
