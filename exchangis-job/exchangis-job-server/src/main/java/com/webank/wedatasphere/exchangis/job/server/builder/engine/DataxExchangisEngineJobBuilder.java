package com.webank.wedatasphere.exchangis.job.server.builder.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.webank.wedatasphere.exchangis.datasource.core.utils.Json;
import com.webank.wedatasphere.exchangis.engine.domain.EngineBmlResource;
import com.webank.wedatasphere.exchangis.engine.resource.loader.datax.DataxEngineResourceConf;
import com.webank.wedatasphere.exchangis.job.builder.ExchangisJobBuilderContext;
import com.webank.wedatasphere.exchangis.job.domain.ExchangisEngineJob;
import com.webank.wedatasphere.exchangis.job.domain.SubExchangisJob;
import com.webank.wedatasphere.exchangis.job.domain.content.ExchangisJobDataSourcesContent;
import com.webank.wedatasphere.exchangis.job.domain.params.JobParamDefine;
import com.webank.wedatasphere.exchangis.job.domain.params.JobParams;
import com.webank.wedatasphere.exchangis.job.exception.ExchangisJobException;
import com.webank.wedatasphere.exchangis.job.exception.ExchangisJobExceptionCode;
import com.webank.wedatasphere.exchangis.job.server.builder.transform.TransformExchangisJob;
import com.webank.wedatasphere.exchangis.job.server.builder.transform.handlers.FileDataxSubExchangisJobHandler;
import com.webank.wedatasphere.exchangis.job.server.render.transform.TransformTypes;
import com.webank.wedatasphere.exchangis.common.util.json.JsonEntity;
import com.webank.wedatasphere.exchangis.job.utils.MemUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Datax engine job builder
 */
public class DataxExchangisEngineJobBuilder extends AbstractResourceEngineJobBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(DataxExchangisEngineJob.class);

    private static final String BYTE_SPEED_SETTING_PARAM = "setting.speed.byte";

    private static final String PROCESSOR_SWITCH = "setting.useProcessor";

    private static final String PROCESSOR_BASE_PATH = "core.processor.loader.plugin.sourcePath";

    private static final Map<String, String> PLUGIN_NAME_MAPPER = new HashMap<>();

    static{
        //hive use hdfs plugin resource
        PLUGIN_NAME_MAPPER.put("hive", "hdfs");
        PLUGIN_NAME_MAPPER.put("tdsql", "mysql");
        // file source uses the datax txtfilereader plugin (file -> txtfile + "reader" = txtfilereader)
        // 文件 source 走 datax txtfilereader 插件（file -> txtfile + "reader" = txtfilereader）
        PLUGIN_NAME_MAPPER.put("file", "txtfile");
    }

    /**
     * Column mappings define
     */
    private static final JobParamDefine<DataxMappingContext> COLUMN_MAPPINGS = JobParams.define("column.mappings", job -> {
        DataxMappingContext mappingContext = new DataxMappingContext();
        job.getSourceColumns().forEach(columnDefine -> {
            DataxMappingContext.Column column = new DataxMappingContext.Column(columnDefine.getName(), columnDefine.getType(),
                    columnDefine.getRawType(), columnDefine.getIndex() != null ? columnDefine.getIndex() + "": null);
            column.setValue(columnDefine.getValue());
            mappingContext.getSourceColumns().add( columnDefine instanceof SubExchangisJob.DecimalColumnDefine ?
                new DataxMappingContext.DecimalColumn(column,
                        ((SubExchangisJob.DecimalColumnDefine) columnDefine).getPrecision(),
                        ((SubExchangisJob.DecimalColumnDefine) columnDefine).getScale()) :
                    column);
        });
        job.getSinkColumns().forEach(columnDefine -> {
            DataxMappingContext.Column column = new DataxMappingContext.Column(columnDefine.getName(), columnDefine.getType(),
                    columnDefine.getRawType(), columnDefine.getIndex() != null ? columnDefine.getIndex() + "" : null);
            column.setValue(columnDefine.getValue());
            mappingContext.getSinkColumns().add(columnDefine instanceof SubExchangisJob.DecimalColumnDefine ?
                    new DataxMappingContext.DecimalColumn(column,
                            ((SubExchangisJob.DecimalColumnDefine) columnDefine).getPrecision(),
                            ((SubExchangisJob.DecimalColumnDefine) columnDefine).getScale()):
                    column);
        });
        job.getColumnFunctions().forEach(function -> {
            DataxMappingContext.Transformer.Parameter parameter = new DataxMappingContext.Transformer.Parameter();
            parameter.setColumnIndex(function.getIndex() + "");
            parameter.setParas(function.getParams());
            mappingContext.getTransformers()
                    .add(new DataxMappingContext.Transformer(function.getName(), parameter));
        });
        return mappingContext;
    }, SubExchangisJob.class);

    /**
     * Source content
     */
    private static final JobParamDefine<String> PLUGIN_SOURCE_NAME = JobParams.define("content[0].reader.name", job ->
            getPluginName(job.getSourceType(), "reader"), SubExchangisJob.class);

    private static final JobParamDefine<Map<String, Object>> PLUGIN_SOURCE_PARAM = JobParams.define("content[0].reader.parameter", job ->
            job.getParamsToMap(SubExchangisJob.REALM_JOB_CONTENT_SOURCE, false), SubExchangisJob.class);

    /**
     * Sink content
     */
    private static final JobParamDefine<String> PLUGIN_SINK_NAME = JobParams.define("content[0]].writer.name", job ->
            getPluginName(job.getSinkType(), "writer"), SubExchangisJob.class);

    private static final JobParamDefine<Map<String, Object>> PLUGIN_SINK_PARAM = JobParams.define("content[0].writer.parameter", job ->
            job.getParamsToMap(SubExchangisJob.REALM_JOB_CONTENT_SINK, false), SubExchangisJob.class);

    /**
     * Source columns
     */
    private static final JobParamDefine<List<DataxMappingContext.Column>> SOURCE_COLUMNS = JobParams.define("content[0].reader.parameter.column",
            DataxMappingContext::getSourceColumns,DataxMappingContext.class);

    /**
     * Sink columns
     */
    private static final JobParamDefine<List<DataxMappingContext.Column>> SINK_COLUMNS = JobParams.define("content[0].writer.parameter.column",
            DataxMappingContext::getSinkColumns,DataxMappingContext.class);

    /**
     * Transform list
     */
    private static final JobParamDefine<List<DataxMappingContext.Transformer>> TRANSFORM_LIST = JobParams.define("content[0].transformer",
            DataxMappingContext::getTransformers, DataxMappingContext.class);

    @Override
    public int priority() {
        return 1;
    }

    @Override
    public boolean canBuild(SubExchangisJob inputJob) {
        return "datax".equalsIgnoreCase(inputJob.getEngineType());
    }

    @Override
    public DataxExchangisEngineJob buildJob(SubExchangisJob inputJob, ExchangisEngineJob expectOut, ExchangisJobBuilderContext ctx) throws ExchangisJobException {

        try {
            DataxExchangisEngineJob engineJob = new DataxExchangisEngineJob(expectOut);
            engineJob.setId(inputJob.getId());
            Map<String, Object> codeMap = buildDataxCode(inputJob, ctx);
            if (Objects.nonNull(codeMap)){
                try {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Datax-code built complete, output: " + Json.getMapper().writerWithDefaultPrettyPrinter().writeValueAsString(codeMap));
                    }
                    info("Datax-code built complete, output: " + Json.getMapper().writerWithDefaultPrettyPrinter().writeValueAsString(codeMap));
                } catch (JsonProcessingException e) {
                    //Ignore
                }
                engineJob.setCode(codeMap);
            }
            // engine resources
            engineJob.getResources().addAll(
                    getResources(inputJob.getEngineType().toLowerCase(Locale.ROOT), getResourcesPaths(inputJob)));
            if (inputJob instanceof TransformExchangisJob.TransformSubExchangisJob){
                TransformExchangisJob.TransformSubExchangisJob transformJob = ((TransformExchangisJob.TransformSubExchangisJob) inputJob);
                TransformTypes type = transformJob.getTransformType();
                if (type == TransformTypes.PROCESSOR){
                    settingProcessorInfo(transformJob, engineJob);
                }
                ExchangisJobDataSourcesContent dsContent = transformJob.getJobInfoContent().getDataSources();
                engineJob.setSourceId(dsContent.parseSourceId());
                engineJob.setSinkId(dsContent.parseSinkId());
            }
            // M2: file source BML resource injection (文件 source BML 资源注入)
            // The handler stashes the file BML ref into jobParams; materialize it as an
            // EngineBmlResource so the launcher serializes it into wds.linkis.engineconn.datax.bml.resources
            // for DataxEngineConnLaunchBuilder.getBmlResources() to auto-download to the EC workdir.
            if ("file".equalsIgnoreCase(inputJob.getSourceType())) {
                settingFileSourceBmlResource(inputJob, engineJob);
            }
            engineJob.setName(inputJob.getName());
            //Unit MB
            Optional.ofNullable(engineJob.getRuntimeParams().get(BYTE_SPEED_SETTING_PARAM)).ifPresent(byteLimit -> {
                long limit = Long.parseLong(String.valueOf(byteLimit));
                // Convert to bytes
                engineJob.getRuntimeParams().put(BYTE_SPEED_SETTING_PARAM,
                        MemUtils.convertToByte(limit, MemUtils.StoreUnit.MB.name()));
            });

            engineJob.setCreateUser(inputJob.getCreateUser());
            // Lock the memory unit
            engineJob.setMemoryUnitLock(true);
            engineJob.setSourceType(inputJob.getSourceType());
            engineJob.setSinkType(inputJob.getSinkType());
            engineJob.setContent(Json.toJson(engineJob.getJobContent(), null));
            return engineJob;

        } catch (Exception e) {
            throw new ExchangisJobException(ExchangisJobExceptionCode.BUILDER_ENGINE_ERROR.getCode(),
                    "Fail to build datax engine job, message:[" + e.getMessage() + "]", e);
        }
    }

    /**
     * Build datax code content
     * @param inputJob input job
     * @param ctx ctx
     * @return code map
     */
    private Map<String, Object> buildDataxCode(SubExchangisJob inputJob, ExchangisJobBuilderContext ctx){
        JsonEntity dataxJob = JsonEntity.from("{}");
        dataxJob.set(PLUGIN_SOURCE_NAME.getKey(), PLUGIN_SOURCE_NAME.getValue(inputJob));
        Optional.ofNullable(PLUGIN_SOURCE_PARAM.getValue(inputJob)).ifPresent(source -> source.forEach((key, value) ->{
            dataxJob.set(PLUGIN_SOURCE_PARAM.getKey() + "." + key, value);
        }));
        dataxJob.set(PLUGIN_SINK_NAME.getKey(), PLUGIN_SINK_NAME.getValue(inputJob));
        Optional.ofNullable(PLUGIN_SINK_PARAM.getValue(inputJob)).ifPresent(sink -> sink.forEach((key, value) -> {
            dataxJob.set(PLUGIN_SINK_PARAM.getKey() + "." + key, value);
        }));
        DataxMappingContext mappingContext = COLUMN_MAPPINGS.getValue(inputJob);
        if (Objects.isNull(dataxJob.get(SOURCE_COLUMNS.getKey()))) {
            dataxJob.set(SOURCE_COLUMNS.getKey(), SOURCE_COLUMNS.getValue(mappingContext));
        }
        if (Objects.isNull(dataxJob.get(SINK_COLUMNS.getKey()))){
            dataxJob.set(SINK_COLUMNS.getKey(), SINK_COLUMNS.getValue(mappingContext));
        }
        dataxJob.set(TRANSFORM_LIST.getKey(), TRANSFORM_LIST.getValue(mappingContext));
        return dataxJob.toMap();
    }

    /**
     * Setting processor info into engine job
     * @param transformJob transform job
     * @param engineJob engine job
     */
    private void settingProcessorInfo(TransformExchangisJob.TransformSubExchangisJob transformJob, ExchangisEngineJob engineJob){
        Optional.ofNullable(transformJob.getCodeResource()).ifPresent(codeResource ->{
            engineJob.getRuntimeParams().put(PROCESSOR_SWITCH, true);
            Object basePath = engineJob.getRuntimeParams().computeIfAbsent(PROCESSOR_BASE_PATH, key -> "proc/src");
            engineJob.getResources().add(new EngineBmlResource(engineJob.getEngineType(), ".",
                    String.valueOf(basePath) + IOUtils.DIR_SEPARATOR_UNIX + "code_" + transformJob.getId(),
                    codeResource.getResourceId(), codeResource.getVersion(), transformJob.getCreateUser()));
        });
    }

    /**
     * Inject the file source BML reference as an {@link EngineBmlResource} (M2).
     *
     * <p>The {@code FileDataxSubExchangisJobHandler} stashes the file BML ref (resourceId/version/
     * owner/name) into {@code jobParams}. Here we materialize it as an {@code EngineBmlResource};
     * the launcher then serializes {@code engineJob.getResources()} into
     * {@code wds.linkis.engineconn.datax.bml.resources}, which {@code DataxEngineConnLaunchBuilder
     * #getBmlResources()} reads to auto-download the file to the EC workdir. txtfilereader reads
     * the file from the workdir local path — no engine plugin change needed.
     *
     * <p>{@code path="."} means Private visibility (only this job's EC can read the file).
     *
     * <p>handler 将文件 BML 引用暂存到 jobParams；此处物化为 {@link EngineBmlResource}，
     * launcher 序列化为 {@code wds.linkis.engineconn.datax.bml.resources}，由 LaunchBuilder 自动下载到 EC 工作目录。
     */
    @SuppressWarnings("unchecked")
    private void settingFileSourceBmlResource(SubExchangisJob inputJob, ExchangisEngineJob engineJob) {
        Object stashed = inputJob.getJobParams().get(FileDataxSubExchangisJobHandler.FILE_BML_RESOURCE_KEY);
        if (!(stashed instanceof Map)) {
            LOG.warn("File source job missing stashed BML reference (文件 source 作业缺少暂存的 BML 引用): jobId={}", inputJob.getId());
            return;
        }
        Map<String, Object> bmlRef = (Map<String, Object>) stashed;
        String resourceId = bmlRef.get(FileDataxSubExchangisJobHandler.PARAM_BML_RESOURCE_ID) == null ? null
                : String.valueOf(bmlRef.get(FileDataxSubExchangisJobHandler.PARAM_BML_RESOURCE_ID));
        String version = bmlRef.get(FileDataxSubExchangisJobHandler.PARAM_BML_VERSION) == null ? null
                : String.valueOf(bmlRef.get(FileDataxSubExchangisJobHandler.PARAM_BML_VERSION));
        if (Objects.isNull(resourceId) || Objects.isNull(version)) {
            LOG.warn("File source BML reference missing resourceId/version (BML 引用缺失 resourceId/version): jobId={}", inputJob.getId());
            return;
        }
        String owner = bmlRef.get(FileDataxSubExchangisJobHandler.PARAM_BML_OWNER) == null
                ? inputJob.getCreateUser()
                : String.valueOf(bmlRef.get(FileDataxSubExchangisJobHandler.PARAM_BML_OWNER));
        String name = bmlRef.get(FileDataxSubExchangisJobHandler.PARAM_BML_NAME) == null
                ? resourceId : String.valueOf(bmlRef.get(FileDataxSubExchangisJobHandler.PARAM_BML_NAME));
        // path="." -> Private visibility (only this job's EC can read the file)
        // name = <basePath>/<name> -> the EC downloads the file to <workdir>/<basePath>/<name>,
        //   and txtfilereader reads from path=<basePath> (set in FileDataxSubExchangisJobHandler).
        //   Mirrors the PROCESSOR_BASE_PATH pattern in settingProcessorInfo.
        //   name = <basePath>/<name> -> EC 将文件下载到 <workdir>/<basePath>/<name>，
        //   txtfilereader 从 path=<basePath> 读取（在 FileDataxSubExchangisJobHandler 中设置）。仿 settingProcessorInfo 的 PROCESSOR_BASE_PATH 模式。
        String basePath = FileDataxSubExchangisJobHandler.FILE_BASE_PATH.getValue();
        engineJob.getResources().add(new EngineBmlResource(engineJob.getEngineType(), ".",
                basePath + IOUtils.DIR_SEPARATOR_UNIX + name, resourceId, version, owner));
        LOG.info("File source BML resource injected (文件 source BML 资源已注入): jobId={}, basePath={}, name={}, resourceId={}",
                inputJob.getId(), basePath, name, resourceId);
    }

    private String[] getResourcesPaths(SubExchangisJob inputJob){
        return new String[]{
                DataxEngineResourceConf.RESOURCE_PATH_PREFIX.getValue() + IOUtils.DIR_SEPARATOR_UNIX + "reader" + IOUtils.DIR_SEPARATOR_UNIX +
                        toResourcePathName(PLUGIN_SOURCE_NAME.getValue(inputJob)),
                DataxEngineResourceConf.RESOURCE_PATH_PREFIX.getValue() + IOUtils.DIR_SEPARATOR_UNIX + "writer" + IOUtils.DIR_SEPARATOR_UNIX +
                        toResourcePathName(PLUGIN_SINK_NAME.getValue(inputJob))
        };
    }

    /**
     * Map the datax plugin name to its actual resource storage path directory name.
     *
     * <p>The txtfilereader/txtfilewriter plugins are stored under textfilereader/textfilewriter
     * in the resource path (naming inconsistency between the datax plugin name and the resource
     * storage directory). The datax config still uses txtfilereader/txtfilewriter as the
     * reader/writer name (see {@link #PLUGIN_SOURCE_NAME} / {@link #PLUGIN_SINK_NAME}); only the
     * resource path lookup needs the actual directory name.
     *
     * <p>txtfilereader/txtfilewriter 插件在资源存储路径下的实际目录是 textfilereader/textfilewriter
     * （datax 插件名与资源存储目录名不一致）。datax 配置仍用 txtfilereader/txtfilewriter 作为
     * reader/writer name，仅资源路径查找需用实际目录名。
     *
     * @param pluginName datax plugin name (e.g. txtfilereader)
     * @return resource path directory name (e.g. textfilereader)
     */
    private static String toResourcePathName(String pluginName) {
        if ("txtfilereader".equals(pluginName)) {
            return "textfilereader";
        }
        if ("txtfilewriter".equals(pluginName)) {
            return "textfilewriter";
        }
        return pluginName;
    }

    // core.processor.loader.plugin.sourcePath
    /**
     * Plugin name
     * @param typeName type name
     * @param suffix suffix
     * @return plugin name
     */
    private static String getPluginName(String typeName, String suffix){
        return Objects.nonNull(typeName) ? PLUGIN_NAME_MAPPER.getOrDefault(typeName.toLowerCase(Locale.ROOT),
                typeName.toLowerCase(Locale.ROOT))
                + suffix : null;
    }
}
