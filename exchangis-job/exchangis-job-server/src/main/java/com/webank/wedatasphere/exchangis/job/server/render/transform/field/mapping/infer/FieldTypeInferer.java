package com.webank.wedatasphere.exchangis.job.server.render.transform.field.mapping.infer;

import java.util.List;

/**
 * Field type inferer (字段类型推断器接口).
 *
 * <p>Abstraction for inferring the target-side field type when the source or sink table
 * does not exist and the field type cannot be loaded from metadata. Implementations are
 * registered to {@link FieldTypeInfererManager} by the {@code (sourceType, sinkType)} pair
 * and selected at runtime based on the actual data source types of the sync job.
 * (当来源表或目的表不存在、无法从元数据加载字段类型时，通过本接口推断目标侧字段类型。
 * 实现类按 {@code (sourceType, sinkType)} 类型对注册到 {@link FieldTypeInfererManager}，
 * 运行时根据同步作业的实际数据源类型选择对应实现。)</p>
 *
 * <p><b>Direction contract / 方向约定：</b><br>
 * {@code infer(A, B, columnType)} means "given an A-side column of type {@code columnType},
 * infer the B-side field type". The first parameter is always the "from" side (the side whose
 * column type is known), and the second is always the "to" side (the side whose type is to be
 * inferred). To infer the source type from the sink type, swap the arguments and call
 * {@code infer(sinkType, sourceType, sinkColumnType)}.
 * ({@code infer(A, B, columnType)} 表示「已知 A 侧字段类型为 {@code columnType}，推断 B 侧字段类型」。
 * 第一参数恒为"起始侧"（已知列类型的一侧），第二参数恒为"目标侧"（待推断的一侧）。
 * 若需由汇侧类型反推源侧类型，交换参数调用 {@code infer(sinkType, sourceType, sinkColumnType)} 即可。)</p>
 */
public interface FieldTypeInferer {

    /**
     * Infer the target field type (推断目标字段类型).
     *
     * @param sourceType source / "from" data source type id (来源/起始数据源类型)
     * @param sinkType   sink / "to" data source type id (目的/目标数据源类型)
     * @param columnType column type from the "from" side (起始侧字段类型)
     * @return inferred target field type, or {@code null} if this inferer cannot infer
     *         (推断的目标字段类型，无法推断时返回 {@code null})
     */
    String infer(String sourceType, String sinkType, String columnType);

    /**
     * The {@code (sourceType, sinkType)} pairs this inferer supports
     * (本推断器支持的来源-目的类型对列表).
     *
     * <p>Used by {@link FieldTypeInfererManager} for auto-registration when the manager is
     * constructed with a list of Spring-managed inferer beans. Return an empty list if the
     * inferer is only meant to be registered programmatically.
     * (供 {@link FieldTypeInfererManager} 在注入 Spring 管理的推断器 Bean 列表时自动注册使用。
     * 若仅通过编程式注册，返回空列表即可。)</p>
     *
     * @return list of supported type pairs, empty if none (支持的类型对列表，无则返回空列表)
     */
    List<DataSourceTypePair> supportedPairs();
}
