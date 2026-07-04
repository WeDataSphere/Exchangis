package com.webank.wedatasphere.exchangis.datasource.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.webank.wedatasphere.exchangis.common.UserUtils;
import com.webank.wedatasphere.exchangis.common.config.GlobalConfiguration;
import com.webank.wedatasphere.exchangis.dao.domain.ExchangisJobParamConfig;
import com.webank.wedatasphere.exchangis.dao.mapper.ExchangisJobParamConfigMapper;
import com.webank.wedatasphere.exchangis.datasource.core.ExchangisDataSourceDefinition;
import com.webank.wedatasphere.exchangis.datasource.core.context.ExchangisDataSourceContext;
import com.webank.wedatasphere.exchangis.datasource.core.ui.*;
import com.webank.wedatasphere.exchangis.datasource.core.ui.viewer.DefaultDataSourceUIViewer;
import com.webank.wedatasphere.exchangis.datasource.core.ui.viewer.ExchangisDataSourceUIViewer;
import com.webank.wedatasphere.exchangis.datasource.core.utils.Json;
import com.webank.wedatasphere.exchangis.datasource.core.domain.ExchangisDataSourceDetail;
import com.webank.wedatasphere.exchangis.job.domain.content.ExchangisJobDataSourcesContent;
import com.webank.wedatasphere.exchangis.job.domain.content.ExchangisJobInfoContent;
import com.webank.wedatasphere.exchangis.job.domain.content.ExchangisJobParamsContent;
import com.webank.wedatasphere.exchangis.job.domain.content.ExchangisJobTransformsContent;
import com.webank.wedatasphere.exchangis.job.domain.ExchangisJobEntity;
import org.apache.commons.lang.StringUtils;
import org.apache.linkis.common.exception.ErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

public abstract class AbstractDataSourceService extends AbstractLinkisDataSourceService implements DataSourceService {
    protected final ObjectMapper mapper = new ObjectMapper();
    protected final ExchangisDataSourceContext context;
    protected final ExchangisJobParamConfigMapper exchangisJobParamConfigMapper;

    private final static Logger LOG = LoggerFactory.getLogger(AbstractDataSourceService.class);


    public AbstractDataSourceService(ExchangisDataSourceContext context, ExchangisJobParamConfigMapper exchangisJobParamConfigMapper) {
        this.context = context;
        this.exchangisJobParamConfigMapper = exchangisJobParamConfigMapper;
    }

    private ExchangisDataSourceIdsUI buildDataSourceIdsUI(ExchangisJobInfoContent content) {
        return this.buildDataSourceIdsUI(null, content);
    }

    /**
     * Build data source ui for 'ids'
     * @param request client request
     * @param content content
     * @return ui entity
     */
    private ExchangisDataSourceIdsUI buildDataSourceIdsUI(HttpServletRequest request, ExchangisJobInfoContent content) {
        String requestUser = UserUtils.getLoginUser(request);
        ExchangisJobDataSourcesContent dataSources = content.getDataSources();
        if (Objects.isNull(dataSources)) {
            return null;
        }
        String sourceId = dataSources.getSourceId();
        String sinkId = dataSources.getSinkId();
        ExchangisDataSourceIdUI sourceUi = parseDataSourceIdUi(requestUser, sourceId,
                dataSources.getSource());
        ExchangisDataSourceIdUI sinkUi = parseDataSourceIdUi(requestUser, sinkId,
                dataSources.getSink());
        ExchangisDataSourceIdsUI ids = new ExchangisDataSourceIdsUI();
        ids.setSource(sourceUi);
        ids.setSink(sinkUi);
        return ids;
    }

    protected ExchangisDataSourceParamsUI buildDataSourceParamsUI(ExchangisDataSourceIdsUI dataSourceIdsUi, ExchangisJobInfoContent content) {
        ExchangisJobParamsContent params = content.getParams();
        List<ExchangisJobParamConfig> sourceParamConfigs = Collections.emptyList();
        List<ExchangisJobParamConfig> sinkParamConfigs = Collections.emptyList();
        if (null != dataSourceIdsUi) {
            ExchangisDataSourceIdUI source = dataSourceIdsUi.getSource();
            if (null != source) {
                String type = source.getType();
                ExchangisDataSourceDefinition exchangisSourceDataSource = this.context.getExchangisDsDefinition(type);
                if (null != exchangisSourceDataSource) {
                    sourceParamConfigs = exchangisSourceDataSource.getDataSourceParamConfigs().stream().filter(
                            i -> i.getConfigDirection().equals(content.getEngine() + "-SOURCE") || "SOURCE".equalsIgnoreCase(i.getConfigDirection())).collect(Collectors.toList());
                }
            }

            ExchangisDataSourceIdUI sink = dataSourceIdsUi.getSink();
            if (null != sink) {
                String type = sink.getType();
                ExchangisDataSourceDefinition exchangisSinkDataSource = this.context.getExchangisDsDefinition(type);
                if (null != exchangisSinkDataSource) {
                    sinkParamConfigs = exchangisSinkDataSource.getDataSourceParamConfigs().stream().filter(i ->
                            i.getConfigDirection().equals(content.getEngine() + "-SINK") || "SINK".equalsIgnoreCase(i.getConfigDirection())).collect(Collectors.toList());
                }
            }
        }

        List<ExchangisJobParamsContent.ExchangisJobParamsItem> sourceParamsItems = Collections.emptyList();
        List<ExchangisJobParamsContent.ExchangisJobParamsItem> sinkParamsItems = Collections.emptyList();
        if (null != params && null != params.getSources()) {
            sourceParamsItems = params.getSources();
        }
        if (null != params && null != params.getSinks()) {
            sinkParamsItems = params.getSinks();
        }

        List<ElementUI<?>> jobDataSourceParamsUI1 = buildDataSourceParamsFilledValueUI(sourceParamConfigs, sourceParamsItems);
        List<ElementUI<?>> jobDataSourceParamsUI2 = buildDataSourceParamsFilledValueUI(sinkParamConfigs, sinkParamsItems);
        ExchangisDataSourceParamsUI paramsUI = new ExchangisDataSourceParamsUI();
        paramsUI.setSources(jobDataSourceParamsUI1);
        paramsUI.setSinks(jobDataSourceParamsUI2);
        return paramsUI;
    }

    protected ExchangisDataSourceUIViewer buildAllUI(HttpServletRequest request, ExchangisJobEntity job, ExchangisJobInfoContent content) {
        // ----------- 构建 dataSourceIdsUI
        ExchangisDataSourceIdsUI dataSourceIdsUI = buildDataSourceIdsUI(request, content);

        // ----------- 构建 dataSourceParamsUI
        // 先走标准表查询重建（sink 始终走该逻辑；FILE source 因无 param config 注册会得到空 source list）
        // Standard table-based rebuild first (sink always uses this; FILE source gets an empty
        // source list because no param config is registered for it).
        ExchangisDataSourceParamsUI paramsUI = buildDataSourceParamsUI(dataSourceIdsUI, content);

        // ⭐ REQ-01: 文件 source 无 ExchangisJobParamConfig 注册（M5 决策），表查询重建会返回空 source list，
        //   导致 getDecoratedJob 用空 params 覆盖原始 jobContent，前端编辑态无法回显
        //   encoding/delimiter/nullFormat 及 __file_bml_* BML 引用。此处对 FILE source 直接从原始
        //   content 透传 params.sources（config_key 格式 → InputElementUI），保留全部 7 项及用户改过的值。
        //   非 FILE 类型不进入此分支，零影响。
        //   File source has no param config registration (M5); table-based rebuild returns an empty
        //   source list, which overwrites the original jobContent via getDecoratedJob, so the frontend
        //   cannot restore encoding/delimiter/nullFormat and __file_bml_* BML references. Here we
        //   pass through params.sources directly from the original content for FILE (config_key
        //   format → InputElementUI), preserving all 7 items and user-edited values.
        //   Non-FILE types never enter this branch — zero impact.
        if (Objects.nonNull(dataSourceIdsUI) && Objects.nonNull(dataSourceIdsUI.getSource())) {
            String sourceType = dataSourceIdsUI.getSource().getType();
            if ("FILE".equalsIgnoreCase(sourceType)) {
                paramsUI.setSources(buildFileSourceParamsUIFromContent(content));
            }
        }

        // ----------- 构建 dataSourceTransformsUI
        ExchangisJobTransformsContent transforms = content.getTransforms();

        List<ElementUI<?>> jobDataSourceSettingsUI = this.buildJobSettingsUI(job.getEngineType(), content);

        return new DefaultDataSourceUIViewer(content.getSubJobName(), dataSourceIdsUI, paramsUI, transforms, jobDataSourceSettingsUI);
    }

    /**
     * 文件 source 的 params.sources 透传构建（REQ-01）。
     * <p>
     * FILE 类型在 exchangis_job_param_config 表无注册（M5 决策），无法走标准的
     * buildDataSourceParamsFilledValueUI 重建流程（会得到空 list）。本方法直接从原始
     * content.getParams().getSources() 读取已保存的 config_key 格式参数项，逐个转换为
     * InputElementUI，确保以下 7 项全部保留且值正确：
     *   - __file_bml_resource_id / __file_bml_version / __file_bml_owner / __file_bml_name（BML 引用，hidden）
     *   - encoding / delimiter / nullFormat（用户可编辑）
     * <p>
     * __file_bml_* 为 hidden 参数：InputElementUI 无 show/hidden 字段，原样保留——前端按
     * "__file_bml_" 前缀识别（设计文档 §9），不会渲染但进入保存 payload。
     * <p>
     * File source params.sources passthrough builder (REQ-01).
     * FILE type has no registration in the exchangis_job_param_config table (M5 decision), so it
     * cannot go through the standard buildDataSourceParamsFilledValueUI rebuild (which yields an
     * empty list). This method reads the saved config_key-format params directly from the original
     * content.getParams().getSources() and converts each to an InputElementUI, ensuring all 7
     * items are preserved with correct values:
     *   - __file_bml_resource_id / __file_bml_version / __file_bml_owner / __file_bml_name (BML refs, hidden)
     *   - encoding / delimiter / nullFormat (user-editable)
     * <p>
     * __file_bml_* are hidden params: InputElementUI has no show/hidden field, so they are kept
     * as-is — the frontend recognizes them by the "__file_bml_" prefix (design §9); they are not
     * rendered but remain in the save payload.
     *
     * @param content job info content
     * @return source params UI list (config_key → InputElementUI)
     */
    private List<ElementUI<?>> buildFileSourceParamsUIFromContent(ExchangisJobInfoContent content) {
        List<ElementUI<?>> sourceUIs = new ArrayList<>();
        if (Objects.isNull(content) || Objects.isNull(content.getParams())) {
            return sourceUIs;
        }
        List<ExchangisJobParamsContent.ExchangisJobParamsItem> sources = content.getParams().getSources();
        if (Objects.isNull(sources) || sources.isEmpty()) {
            return sourceUIs;
        }
        for (ExchangisJobParamsContent.ExchangisJobParamsItem item : sources) {
            if (Objects.isNull(item)) {
                continue;
            }
            InputElementUI ui = new InputElementUI();
            // key/field 均设为 configKey，前端可按 key 或 config_key 读取
            // Set both key and field to configKey so the frontend can read by either key or config_key
            ui.setKey(item.getConfigKey());
            ui.setField(item.getConfigKey());
            ui.setLabel(item.getConfigName());
            // configValue 为 Object 类型，转为 String 供 InputElementUI.value 使用；null 转空串
            // configValue is Object; convert to String for InputElementUI.value; null → empty string
            Object configValue = item.getConfigValue();
            String valueStr = Objects.nonNull(configValue) ? String.valueOf(configValue) : "";
            ui.setValue(valueStr);
            ui.setSort(item.getSort());
            sourceUIs.add(ui);
        }
        return sourceUIs;
    }


    protected List<ElementUI<?>> buildJobSettingsUI(String jobEngineType) {
        if (Strings.isNullOrEmpty(jobEngineType)) {
            return Collections.emptyList();
        }
        QueryWrapper<ExchangisJobParamConfig> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("type", jobEngineType);
        queryWrapper.eq("is_hidden", 0);
        queryWrapper.eq("status", 1);
        List<ExchangisJobParamConfig> settingParamConfigs = exchangisJobParamConfigMapper.selectList(queryWrapper);
        return buildDataSourceParamsFilledValueUI(settingParamConfigs, null);
    }

    protected List<ElementUI<?>> buildJobSettingsUI(String jobEngineType, ExchangisJobInfoContent content) {
        if (Strings.isNullOrEmpty(jobEngineType)) {
            return Collections.emptyList();
        }
        List<ExchangisJobParamsContent.ExchangisJobParamsItem> settings = content.getSettings();
        QueryWrapper<ExchangisJobParamConfig> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("type", jobEngineType);
        queryWrapper.eq("is_hidden", 0);
        queryWrapper.eq("status", 1);
        List<ExchangisJobParamConfig> settingParamConfigs = exchangisJobParamConfigMapper.selectList(queryWrapper);
        return buildDataSourceParamsFilledValueUI(settingParamConfigs, settings);
    }

    protected List<ElementUI<?>> buildDataSourceParamsUI(List<ExchangisJobParamConfig> paramConfigs) {
        List<ElementUI<?>> uis = new ArrayList<>();
        if (!Objects.isNull(paramConfigs) && !paramConfigs.isEmpty()) {
            for (ExchangisJobParamConfig cfg : paramConfigs) {
                ElementUI<?> ui = fillElementUIValue(cfg, "");
                uis.add(ui);
            }
        }
        return uis;
    }

    protected List<ElementUI<?>> buildDataSourceParamsFilledValueUI(List<ExchangisJobParamConfig> paramConfigs, List<ExchangisJobParamsContent.ExchangisJobParamsItem> paramsList) {
        List<ElementUI<?>> uis = new ArrayList<>();
        if (!Objects.isNull(paramConfigs) && !paramConfigs.isEmpty()) {
            for (ExchangisJobParamConfig cfg : paramConfigs) {
                if (Objects.isNull(paramsList) || paramsList.isEmpty()) {
                    uis.add(fillElementUIValue(cfg, ""));
                    continue;
                }
                ExchangisJobParamsContent.ExchangisJobParamsItem selectedParamItem = getJobParamsItem(cfg.getConfigKey(), paramsList);
                if (Objects.isNull(selectedParamItem)) {
                    ElementUI<?> ui = fillElementUIValue(cfg, "");
                    uis.add(ui);
                } else {
                    ElementUI<?> ui = fillElementUIValue(cfg, selectedParamItem.getConfigValue());
                    uis.add(ui);
                }
            }
        }
        return uis;
    }

    private ExchangisJobParamsContent.ExchangisJobParamsItem getJobParamsItem(String configKey, List<ExchangisJobParamsContent.ExchangisJobParamsItem> sources) {
        for (ExchangisJobParamsContent.ExchangisJobParamsItem item : sources) {
            if (item.getConfigKey().equalsIgnoreCase(configKey)) {
                return item;
            }
        }
        return null;
    }

    private ElementUI<?> fillElementUIValue(ExchangisJobParamConfig config, Object value) {
        String uiType = config.getUiType();
        ElementUI.Type uiTypeEnum;
        try {
            uiTypeEnum = StringUtils.isNotBlank(uiType)?
                    ElementUI.Type.valueOf(uiType.toUpperCase(Locale.ROOT)) : ElementUI.Type.NONE;
        }catch (Exception e){
            uiTypeEnum = ElementUI.Type.NONE;
        }
        switch (uiTypeEnum) {
            case OPTION:
                return fillOptionElementUIValue(config, String.valueOf(value));
            case INPUT:
                return fillInputElementUIValue(config, String.valueOf(value));
            case MAP:
                Map<String, Object> mapElement = null;
                try {
                    if (Objects.nonNull(value)) {
                        if (!(value instanceof String) || StringUtils.isNotBlank(String.valueOf(value))) {
                            String json = Json.toJson(value, null);
                            if (StringUtils.isNotBlank(json)) {
                                mapElement = Json.fromJson(json,
                                        Map.class, String.class, Object.class);
                            }
                        }
                    }
                } catch (Exception e) {
                    LOG.info("Exception happened while parse json"+ "Config value: " + value + "message: " + e.getMessage(), e);
                }
                return fillMapElementUIValue(config, mapElement);
            default:
                return null;
        }
    }


    private OptionElementUI fillOptionElementUIValue(ExchangisJobParamConfig config, String value) {
        String valueRange = config.getValueRange();
        List values = Collections.emptyList();
        try {
            values = mapper.readValue(valueRange, List.class);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        OptionElementUI ui = new OptionElementUI();
        ui.setId(config.getId());
        ui.setKey(config.getConfigKey());
        ui.setField(config.getUiField());
        ui.setLabel(config.getUiLabel());
        ui.setValues(values);
        ui.setValue(value);
        ui.setDefaultValue(config.getDefaultValue());
        ui.setSort(config.getSort());
        ui.setRequired(config.getRequired());
        ui.setUnit(config.getUnit());
        ui.setRefId(config.getRefId());
        return ui;
    }

    private InputElementUI fillInputElementUIValue(ExchangisJobParamConfig config, String value) {
        InputElementUI ui = new InputElementUI();
        ui.setId(config.getId());
        ui.setKey(config.getConfigKey());
        ui.setField(config.getUiField());
        ui.setLabel(config.getUiLabel());
        ui.setValue(value);
        ui.setDefaultValue(config.getDefaultValue());
        ui.setSort(config.getSort());
        ui.setRequired(config.getRequired());
        ui.setUnit(config.getUnit());
        ui.setSource(config.getSource());
        ui.setValidateType(config.getValidateType());
        ui.setValidateRange(config.getValidateRange());
        ui.setValidateMsg(config.getValidateMsg());
        ui.setRefId(config.getRefId());
        return ui;
    }

    private MapElementUI fillMapElementUIValue(ExchangisJobParamConfig config, Map<String, Object> value) {
        MapElementUI ui = new MapElementUI();
        ui.setId(config.getId());
        ui.setKey(config.getConfigKey());
        ui.setField(config.getUiField());
        ui.setLabel(config.getUiLabel());
        ui.setValue(value);
        //ui.setDefaultValue(config.getDefaultValue());
        ui.setSort(config.getSort());
        ui.setRequired(config.getRequired());
        ui.setUnit(config.getUnit());
        ui.setSource(config.getSource());
        ui.setValidateType(config.getValidateType());
        ui.setValidateRange(config.getValidateRange());
        ui.setValidateMsg(config.getValidateMsg());
        ui.setRefId(config.getRefId());
        return ui;
    }

    /**
     * Parse data source id ui
     * @param jobDataSource data source
     * @return ui
     */
    private ExchangisDataSourceIdUI parseDataSourceIdUi(String requestUser, String idValue,
                                                        ExchangisJobDataSourcesContent.ExchangisJobDataSource jobDataSource){
        ExchangisDataSourceIdUI ui = new ExchangisDataSourceIdUI(jobDataSource);
        if (StringUtils.isNotBlank(idValue)) {
            String[] split = idValue.trim().split("\\.");
            ui.setType(split[0]);
            ui.setId(split[1]);
            ui.setDb(split[2]);
            ui.setTable(split[3]);
        }
        if (StringUtils.isBlank(ui.getDs())){
            String operator = StringUtils.isNotBlank(ui.getCreator())? ui.getCreator() :
                    GlobalConfiguration.getAdminUser();
            if (StringUtils.isBlank(operator)){
                operator = requestUser;
            }
            String finalOperator = operator;
            Optional.ofNullable(this.context.getExchangisDsDefinition(ui.getType())).ifPresent(o -> {
                try {
                    ExchangisDataSourceDetail dataSourceInfo = getDataSource(finalOperator, Long.parseLong(ui.getId()));
                    if (Objects.nonNull(dataSourceInfo)) {
                        String name = dataSourceInfo.getDataSourceName();
                        ui.setDs(name);
                        ui.setName(name);
                        ui.setCreator(dataSourceInfo.getCreateUser());
                    }
                } catch (ErrorException e) {
                    // Ignore
                }
            });
        }
        return ui;
    }
}
