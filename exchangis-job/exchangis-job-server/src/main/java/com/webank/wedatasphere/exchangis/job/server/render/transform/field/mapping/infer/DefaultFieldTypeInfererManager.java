package com.webank.wedatasphere.exchangis.job.server.render.transform.field.mapping.infer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default field type inferer manager (字段类型推断器管理器默认实现).
 *
 * <p>Registers {@link FieldTypeInferer} instances keyed by the normalized
 * {@code (sourceType, sinkType)} pair and delegates inference to the matching inferer.
 * Spring-managed inferer beans are auto-registered at construction time via
 * {@link FieldTypeInferer#supportedPairs()}; additional inferers may be registered
 * programmatically through {@link #register(String, String, FieldTypeInferer)}.
 * (按规范化后的 {@code (sourceType, sinkType)} 类型对注册 {@link FieldTypeInferer} 实例，
 * 推断时委派给匹配的推断器。Spring 管理的推断器 Bean 在构造时通过
 * {@link FieldTypeInferer#supportedPairs()} 自动注册；额外推断器可通过
 * {@link #register(String, String, FieldTypeInferer)} 编程式注册。)</p>
 *
 * <p>Type ids are normalized (trim + upper-case) before building the registry key, so that
 * {@code ("MYSQL", "HIVE")} and {@code ("mysql", "hive")} resolve to the same inferer.
 * (类型 id 在构建注册键前会做规范化——trim 并转大写，因此 {@code ("MYSQL","HIVE")}
 * 与 {@code ("mysql","hive")} 命中同一推断器。)</p>
 */
@Component
public class DefaultFieldTypeInfererManager implements FieldTypeInfererManager {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultFieldTypeInfererManager.class);

    /**
     * Separator between source type and sink type in the registry key (注册键中来源类型与目的类型的分隔符)
     */
    private static final String KEY_SEPARATOR = ":";

    /**
     * Inferer registry, key = normalize(sourceType) + ":" + normalize(sinkType)
     * (推断器注册表，键 = 规范化来源类型 + ":" + 规范化目的类型)
     */
    private final ConcurrentHashMap<String, FieldTypeInferer> inferers = new ConcurrentHashMap<>();

    /**
     * Construct with Spring-managed inferer beans and auto-register them
     * (构造时注入 Spring 管理的推断器 Bean 列表并自动注册).
     *
     * @param infererList list of FieldTypeInferer beans, may be empty or null
     *                    (FieldTypeInferer Bean 列表，可为空或 null)
     */
    public DefaultFieldTypeInfererManager(List<FieldTypeInferer> infererList) {
        if (Objects.nonNull(infererList) && !infererList.isEmpty()) {
            for (FieldTypeInferer inferer : infererList) {
                List<DataSourceTypePair> pairs = inferer.supportedPairs();
                if (Objects.isNull(pairs) || pairs.isEmpty()) {
                    LOG.warn("FieldTypeInferer [{}] declares no supported pairs, skip auto-registration " +
                            "(推断器[{}]未声明任何支持的类型对，跳过自动注册)", inferer.getClass().getSimpleName(),
                            inferer.getClass().getSimpleName());
                    continue;
                }
                for (DataSourceTypePair pair : pairs) {
                    register(pair.getSourceType(), pair.getSinkType(), inferer);
                }
            }
        }
        LOG.info("FieldTypeInfererManager initialized with [{}] registered type pair(s) " +
                "(字段类型推断器管理器初始化完成，已注册[{}]个类型对)", inferers.size(), inferers.size());
    }

    @Override
    public void register(String sourceType, String sinkType, FieldTypeInferer inferer) {
        if (Objects.isNull(inferer)) {
            LOG.warn("Cannot register null FieldTypeInferer for pair [{}] (无法为类型对[{}]注册空推断器)",
                    buildKey(sourceType, sinkType), buildKey(sourceType, sinkType));
            return;
        }
        String key = buildKey(sourceType, sinkType);
        FieldTypeInferer existing = inferers.putIfAbsent(key, inferer);
        if (Objects.nonNull(existing) && existing != inferer) {
            LOG.warn("Duplicate field type inferer for pair [{}], keep existing [{}], ignore new [{}] " +
                            "(类型对[{}]已注册推断器，保留先注册的[{}]，忽略新注册的[{}])",
                    key, existing.getClass().getSimpleName(), inferer.getClass().getSimpleName(),
                    key, existing.getClass().getSimpleName(), inferer.getClass().getSimpleName());
        }
    }

    @Override
    public String infer(String sourceType, String sinkType, String columnType) {
        FieldTypeInferer inferer = inferers.get(buildKey(sourceType, sinkType));
        if (Objects.isNull(inferer)) {
            return null;
        }
        return inferer.infer(sourceType, sinkType, columnType);
    }

    /**
     * Build the normalized registry key for a (sourceType, sinkType) pair
     * (为类型对构建规范化后的注册键)
     * @param sourceType source type (来源类型)
     * @param sinkType sink type (目的类型)
     * @return normalized key (规范化后的键)
     */
    private String buildKey(String sourceType, String sinkType) {
        return normalize(sourceType) + KEY_SEPARATOR + normalize(sinkType);
    }

    /**
     * Normalize a type id: trim + upper-case, null-safe
     * (规范化类型 id：trim 并转大写，null 安全)
     * @param type type id (类型 id)
     * @return normalized type id (规范化后的类型 id)
     */
    private String normalize(String type) {
        return Objects.isNull(type) ? "" : type.trim().toUpperCase(Locale.ROOT);
    }
}
