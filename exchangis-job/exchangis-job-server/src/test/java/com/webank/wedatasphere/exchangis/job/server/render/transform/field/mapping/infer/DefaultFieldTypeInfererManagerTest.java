package com.webank.wedatasphere.exchangis.job.server.render.transform.field.mapping.infer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link DefaultFieldTypeInfererManager}
 * ({@link DefaultFieldTypeInfererManager} 单元测试).
 */
class DefaultFieldTypeInfererManagerTest {

    /**
     * Build a simple inferer that returns a fixed target type for any non-null column type
     * (构建一个简单推断器：对非空列类型返回固定目标类型)
     */
    private FieldTypeInferer fixedResultInferer(String result, List<DataSourceTypePair> pairs) {
        return new FieldTypeInferer() {
            @Override
            public String infer(String sourceType, String sinkType, String columnType) {
                return result;
            }

            @Override
            public List<DataSourceTypePair> supportedPairs() {
                return pairs;
            }
        };
    }

    /**
     * Build a passthrough inferer that echoes the column type
     * (构建透传推断器：原样返回列类型)
     */
    private FieldTypeInferer passThroughInferer(List<DataSourceTypePair> pairs) {
        return new FieldTypeInferer() {
            @Override
            public String infer(String sourceType, String sinkType, String columnType) {
                return columnType;
            }

            @Override
            public List<DataSourceTypePair> supportedPairs() {
                return pairs;
            }
        };
    }

    @Test
    @DisplayName("register + infer: registered inferer is delegated to (注册后推断能委派到对应推断器)")
    void testRegisterAndInfer() {
        DefaultFieldTypeInfererManager manager = new DefaultFieldTypeInfererManager(Collections.emptyList());
        manager.register("MYSQL", "HIVE", fixedResultInferer("STRING", Collections.emptyList()));

        assertEquals("STRING", manager.infer("MYSQL", "HIVE", "VARCHAR(255)"),
                "Inferer for (MYSQL, HIVE) should be invoked (MYSQL->HIVE 推断器应被调用)");
    }

    @Test
    @DisplayName("supportedPairs: inferer beans are auto-registered at construction "
            + "(构造时通过 supportedPairs 自动注册)")
    void testAutoRegistrationViaSupportedPairs() {
        FieldTypeInferer inferer = fixedResultInferer("STRING",
                Arrays.asList(DataSourceTypePair.of("MYSQL", "HIVE")));
        DefaultFieldTypeInfererManager manager = new DefaultFieldTypeInfererManager(Collections.singletonList(inferer));

        assertEquals("STRING", manager.infer("MYSQL", "HIVE", "VARCHAR(100)"),
                "Auto-registered inferer should be reachable (自动注册的推断器应可查找到)");
    }

    @Test
    @DisplayName("Case-insensitive: ('MYSQL','HIVE') and ('mysql','hive') resolve to the same inferer "
            + "(大小写无关：MYSQL/HIVE 与 mysql/hive 命中同一推断器)")
    void testCaseInsensitiveLookup() {
        DefaultFieldTypeInfererManager manager = new DefaultFieldTypeInfererManager(Collections.emptyList());
        manager.register("MYSQL", "HIVE", passThroughInferer(Collections.emptyList()));

        assertEquals("VARCHAR(255)", manager.infer("mysql", "hive", "VARCHAR(255)"),
                "Lower-case lookup should hit the upper-case-registered inferer (小写查找应命中大写注册的推断器)");
        assertEquals("VARCHAR(255)", manager.infer(" MySql ", " Hive ", "VARCHAR(255)"),
                "Trimmed lookup should hit the registered inferer (带空格的查找应命中已注册推断器)");
    }

    @Test
    @DisplayName("Unregistered pair returns null (未注册的类型对返回 null)")
    void testUnregisteredPairReturnsNull() {
        DefaultFieldTypeInfererManager manager = new DefaultFieldTypeInfererManager(Collections.emptyList());
        manager.register("MYSQL", "HIVE", fixedResultInferer("STRING", Collections.emptyList()));

        assertNull(manager.infer("MYSQL", "MONGODB", "VARCHAR(255)"),
                "Unregistered (MYSQL, MONGODB) should return null (未注册的 MYSQL->MONGODB 应返回 null)");
        assertNull(manager.infer("HIVE", "MYSQL", "STRING"),
                "Reverse direction (HIVE, MYSQL) is a different key and should return null "
                        + "(反方向 HIVE->MYSQL 是不同的键，应返回 null)");
    }

    @Test
    @DisplayName("Delegate return value: null from inferer is propagated as null "
            + "(推断器返回 null 时原样透传)")
    void testNullResultPropagated() {
        DefaultFieldTypeInfererManager manager = new DefaultFieldTypeInfererManager(Collections.emptyList());
        manager.register("MYSQL", "HIVE", fixedResultInferer(null, Collections.emptyList()));

        assertNull(manager.infer("MYSQL", "HIVE", "VARCHAR(255)"),
                "Null result from inferer should be propagated (推断器返回 null 应原样透传)");
    }

    @Test
    @DisplayName("Duplicate registration: first-registered wins, second is ignored "
            + "(重复注册：先注册者保留，后者忽略)")
    void testDuplicateRegistrationFirstWins() {
        DefaultFieldTypeInfererManager manager = new DefaultFieldTypeInfererManager(Collections.emptyList());
        manager.register("MYSQL", "HIVE", fixedResultInferer("FIRST", Collections.emptyList()));
        // Register a second inferer for the same pair — should be ignored
        manager.register("MYSQL", "HIVE", fixedResultInferer("SECOND", Collections.emptyList()));

        assertEquals("FIRST", manager.infer("MYSQL", "HIVE", "VARCHAR(255)"),
                "First-registered inferer should win (先注册的推断器应保留)");
    }

    @Test
    @DisplayName("Registering a null inferer is a no-op (注册 null 推断器为空操作)")
    void testRegisterNullInfererIsNoOp() {
        DefaultFieldTypeInfererManager manager = new DefaultFieldTypeInfererManager(Collections.emptyList());
        manager.register("MYSQL", "HIVE", null);

        assertNull(manager.infer("MYSQL", "HIVE", "VARCHAR(255)"),
                "Null inferer registration should not create an entry (注册 null 推断器不应产生注册项)");
    }

    @Test
    @DisplayName("Inferer with empty supportedPairs is not auto-registered "
            + "(supportedPairs 为空的推断器不会被自动注册)")
    void testEmptySupportedPairsNotAutoRegistered() {
        FieldTypeInferer inferer = fixedResultInferer("STRING", Collections.emptyList());
        DefaultFieldTypeInfererManager manager = new DefaultFieldTypeInfererManager(Collections.singletonList(inferer));

        assertNull(manager.infer("MYSQL", "HIVE", "VARCHAR(255)"),
                "Inferer with no supported pairs should not be auto-registered "
                        + "(未声明类型对的推断器不应被自动注册)");
    }
}
