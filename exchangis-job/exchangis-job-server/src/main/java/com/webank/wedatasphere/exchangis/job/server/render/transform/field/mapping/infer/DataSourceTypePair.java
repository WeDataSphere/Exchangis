package com.webank.wedatasphere.exchangis.job.server.render.transform.field.mapping.infer;

import java.util.Objects;

/**
 * Value object representing an ordered pair of data source type ids
 * (用于注册字段类型推断器的有序数据源类型对值对象).
 *
 * <p>The pair is ordered: (sourceType, sinkType) is NOT equal to (sinkType, sourceType),
 * because field type inference is directional — inferring the sink type from the source type
 * is the reverse problem of inferring the source type from the sink type.
 * (类型对是有序的：(sourceType, sinkType) 不等于 (sinkType, sourceType)，
 * 因为字段类型推断是单向的——由源侧推断汇侧 与 由汇侧推断源侧 是相反的方向。)</p>
 */
public class DataSourceTypePair {

    /**
     * Source / "from" data source type id (来源/起始数据源类型)
     */
    private final String sourceType;

    /**
     * Sink / "to" data source type id (目的/目标数据源类型)
     */
    private final String sinkType;

    public DataSourceTypePair(String sourceType, String sinkType) {
        this.sourceType = sourceType;
        this.sinkType = sinkType;
    }

    /**
     * Static factory (静态工厂方法)
     * @param sourceType source type (来源类型)
     * @param sinkType sink type (目的类型)
     * @return type pair (类型对)
     */
    public static DataSourceTypePair of(String sourceType, String sinkType) {
        return new DataSourceTypePair(sourceType, sinkType);
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getSinkType() {
        return sinkType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DataSourceTypePair)) {
            return false;
        }
        DataSourceTypePair that = (DataSourceTypePair) o;
        return Objects.equals(sourceType, that.sourceType) && Objects.equals(sinkType, that.sinkType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceType, sinkType);
    }

    @Override
    public String toString() {
        return sourceType + "->" + sinkType;
    }
}
