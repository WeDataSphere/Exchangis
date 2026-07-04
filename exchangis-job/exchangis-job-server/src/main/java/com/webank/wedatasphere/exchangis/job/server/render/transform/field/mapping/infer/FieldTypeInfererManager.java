package com.webank.wedatasphere.exchangis.job.server.render.transform.field.mapping.infer;

/**
 * Field type inferer manager (字段类型推断器管理器接口).
 *
 * <p>Holds a registry of {@link FieldTypeInferer} instances keyed by the
 * {@code (sourceType, sinkType)} pair and delegates inference to the matching inferer at
 * runtime. See {@link FieldTypeInferer} for the direction contract of {@code infer}.
 * (按 {@code (sourceType, sinkType)} 类型对维护 {@link FieldTypeInferer} 实例注册表，
 * 运行时将推断请求委派给匹配的推断器。{@code infer} 的方向约定见 {@link FieldTypeInferer}。)</p>
 */
public interface FieldTypeInfererManager {

    /**
     * Register an inferer for a specific {@code (sourceType, sinkType)} pair
     * (为指定来源-目的类型对注册推断器).
     *
     * <p>If a different inferer is already registered for the same pair, the existing one is
     * retained and a warning is logged (first-registered-wins).
     * (若同一类型对已注册其他推断器，保留先注册者并打印 warn 日志——先注册者优先。)</p>
     *
     * @param sourceType source / "from" data source type id (来源/起始数据源类型)
     * @param sinkType   sink / "to" data source type id (目的/目标数据源类型)
     * @param inferer    inferer to register (待注册的推断器)
     */
    void register(String sourceType, String sinkType, FieldTypeInferer inferer);

    /**
     * Infer the target field type (推断目标字段类型).
     *
     * <p>Direction contract: {@code infer(A, B, columnType)} = "given an A-side column of type
     * {@code columnType}, infer the B-side field type".
     * (方向约定：{@code infer(A, B, columnType)} 表示「已知 A 侧字段类型为 {@code columnType}，推断 B 侧字段类型」。)</p>
     *
     * @param sourceType source / "from" data source type id (来源/起始数据源类型)
     * @param sinkType   sink / "to" data source type id (目的/目标数据源类型)
     * @param columnType column type from the "from" side (起始侧字段类型)
     * @return inferred target field type, or {@code null} if no inferer is registered for the pair
     *         (推断的目标字段类型，无匹配推断器时返回 {@code null})
     */
    String infer(String sourceType, String sinkType, String columnType);
}
