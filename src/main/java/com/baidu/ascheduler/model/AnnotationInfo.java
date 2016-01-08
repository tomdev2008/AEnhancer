package com.baidu.ascheduler.model;

import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import com.baidu.ascheduler.entry.Sched;
import com.baidu.ascheduler.exception.IllegalParamException;

/**
 * annotation info
 * 
 * @author xushuda
 *
 */
public class AnnotationInfo {

    private final Expression extractFromParam;
    private final Expression extractFromResult;
    private final int[] ignList;
    private final int batchSize;
    private final int expiration;
    private final String driverBeanName;
    private final String nameSpace;
    private final int retryTimes;
    private final boolean isResultSequential;

    public int getRetryTimes() {
        return retryTimes;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public int getExpiration() {
        return expiration;
    }

    public String getDriverName() {
        return driverBeanName;
    }

    /**
     * 创建cachedInfo
     * 
     * @param scheduled
     * @throws IllegalParamException
     */
    public AnnotationInfo(Sched scheduled) throws IllegalParamException {
        SpelExpressionParser parser = new SpelExpressionParser();
        String etParam = scheduled.param();
        String etResult = scheduled.result();
        // annotation spring expression language
        if (null != etParam && !"".equals(etParam)) {
            extractFromParam = parser.parseExpression(etParam);
        } else {
            extractFromParam = null;
        }
        if (null != etResult && !"".equals(etResult)) {
            extractFromResult = parser.parseExpression(etResult);
        } else {
            extractFromResult = null;
        }
        this.expiration = scheduled.expiration();
        this.batchSize = scheduled.batchLimit();
        this.driverBeanName = scheduled.cache();
        this.ignList = scheduled.ignList();
        this.nameSpace = scheduled.nameSpace();
        this.retryTimes = scheduled.retry();
        isResultSequential = scheduled.sequential();
        assertLegal();
    }

    /**
     * 确认合法性
     * 
     * @throws IllegalParamException
     */
    private void assertLegal() throws IllegalParamException {
        // 一定是依赖于顺序或者用SpEl获取key，两个条件只有一个为真
        if (isResultSequential && extractFromResult != null) {
            throw new IllegalParamException("annotation is error can't both use sequential and spel to get key");
        }
        // 如果用聚合调用，那么isResultSequential和extractFromResult必定有一个明确指定
        if (null != extractFromParam && (!isResultSequential && extractFromResult == null)) {
            throw new IllegalParamException(
                    "error annotation, if keyInParam is not null, then must specify isResultSequential or keyInResult");
        }
    }

    public boolean isResultSequential() {
        return isResultSequential;
    }

    /**
     * 是否使用聚合方式缓存，
     * 
     * @return true，使用aggr方式缓存，即对对象和
     * @throws IllegalParamException
     */
    public boolean aggrInvok() {
        return null != extractFromResult || null != extractFromParam || isResultSequential == true;
    }

    public String getNameSpace() {
        return nameSpace;
    }

    /**
     * 从param中获取key
     * 
     * @param paramElement
     * @return
     * @throws IllegalParamException
     */
    public Object extParam(Object paramElement) throws IllegalParamException {
        assertBatch();
        if (null != extractFromParam) {
            return extractFromParam.getValue(new StandardEvaluationContext(paramElement));
        }
        return paramElement;
    }

    /**
     * 从result中获取key
     * 
     * @param resultElement
     * @return key
     * @throws IllegalParamException
     */
    public Object extResult(Object resultElement) throws IllegalParamException {
        assertBatch();
        return extractFromResult.getValue(resultElement);
    }

    /**
     * 
     * @throws IllegalParamException
     */
    public void assertBatch() throws IllegalParamException {
        if (!aggrInvok()) {
            throw new IllegalParamException("only the aggregated param and result can be extracted");
        }
    }

    /**
     * 返回忽略参数列表
     * 
     * @return
     */
    public int[] getIgnList() {
        return ignList;
    }
}
