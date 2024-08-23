package com.webank.wedatasphere.exchangis.datasource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.google.common.base.Strings;
import com.webank.wedatasphere.exchangis.common.config.GlobalConfiguration;
import com.webank.wedatasphere.exchangis.common.pager.PageResult;
import com.webank.wedatasphere.exchangis.dao.domain.ExchangisJobDsBind;
import com.webank.wedatasphere.exchangis.dao.domain.ExchangisJobParamConfig;
import com.webank.wedatasphere.exchangis.dao.mapper.ExchangisJobDsBindMapper;
import com.webank.wedatasphere.exchangis.dao.mapper.ExchangisJobParamConfigMapper;
import com.webank.wedatasphere.exchangis.datasource.GetDataSourceInfoByIdAndVersionIdAction;
import com.webank.wedatasphere.exchangis.datasource.GetInfoByDataSourceIdAndVersionIdResult;
import com.webank.wedatasphere.exchangis.datasource.core.ExchangisDataSourceDefinition;
import com.webank.wedatasphere.exchangis.datasource.core.domain.DataSourceModel;
import com.webank.wedatasphere.exchangis.datasource.core.domain.DataSourceModelRelation;
import com.webank.wedatasphere.exchangis.datasource.core.serialize.ParamKeySerializer;
import com.webank.wedatasphere.exchangis.datasource.core.utils.Json;
import com.webank.wedatasphere.exchangis.datasource.domain.ExchangisDataSourceDetail;
import com.webank.wedatasphere.exchangis.datasource.domain.ExchangisDataSourceItem;
import com.webank.wedatasphere.exchangis.datasource.domain.ExchangisDataSourceTypeDefinition;
import com.webank.wedatasphere.exchangis.datasource.core.context.ExchangisDataSourceContext;
import com.webank.wedatasphere.exchangis.datasource.core.exception.ExchangisDataSourceException;
import com.webank.wedatasphere.exchangis.datasource.core.exception.ExchangisDataSourceExceptionCode;
import com.webank.wedatasphere.exchangis.datasource.core.ui.ElementUI;
import com.webank.wedatasphere.exchangis.datasource.core.ui.viewer.ExchangisDataSourceUIViewer;
import com.webank.wedatasphere.exchangis.datasource.mapper.DataSourceModelMapper;
import com.webank.wedatasphere.exchangis.datasource.mapper.DataSourceModelRelationMapper;
import com.webank.wedatasphere.exchangis.datasource.mapper.DataSourceModelTypeKeyMapper;
import com.webank.wedatasphere.exchangis.datasource.service.AbstractDataSourceService;
import com.webank.wedatasphere.exchangis.datasource.service.DataSourceUIGetter;
import com.webank.wedatasphere.exchangis.datasource.service.DataSourceService;
import com.webank.wedatasphere.exchangis.job.domain.content.ExchangisJobInfoContent;
import com.webank.wedatasphere.exchangis.datasource.remote.*;
import com.webank.wedatasphere.exchangis.datasource.linkis.ExchangisLinkisRemoteClient;
import com.webank.wedatasphere.exchangis.datasource.linkis.request.ParamsTestConnectAction;
import com.webank.wedatasphere.exchangis.datasource.vo.DataSourceCreateVo;
import com.webank.wedatasphere.exchangis.datasource.vo.DataSourceQueryVo;
import com.webank.wedatasphere.exchangis.engine.dao.EngineSettingsDao;
import com.webank.wedatasphere.exchangis.engine.domain.EngineSettings;
import com.webank.wedatasphere.exchangis.job.api.ExchangisJobOpenService;
import com.webank.wedatasphere.exchangis.job.domain.ExchangisJobEntity;
import com.webank.wedatasphere.exchangis.job.exception.ExchangisJobException;
import com.webank.wedatasphere.exchangis.job.utils.JobUtils;
import com.webank.wedatasphere.exchangis.project.entity.domain.OperationType;
import com.webank.wedatasphere.exchangis.project.entity.entity.ExchangisProject;
import com.webank.wedatasphere.exchangis.project.entity.entity.ExchangisProjectDsRelation;
import com.webank.wedatasphere.exchangis.project.entity.vo.ExchangisProjectInfo;
import com.webank.wedatasphere.exchangis.project.entity.vo.ProjectDsQueryVo;
import com.webank.wedatasphere.exchangis.project.provider.service.ProjectOpenService;
import org.apache.commons.lang3.StringUtils;
import org.apache.linkis.common.exception.ErrorException;
import org.apache.linkis.datasource.client.AbstractRemoteClient;
import org.apache.linkis.datasource.client.impl.LinkisDataSourceRemoteClient;
import org.apache.linkis.datasource.client.impl.LinkisMetaDataRemoteClient;
import org.apache.linkis.datasource.client.request.*;
import org.apache.linkis.datasource.client.response.*;
import org.apache.linkis.datasource.client.response.GetDataSourceVersionsResult;
import org.apache.linkis.datasource.client.response.MetadataGetColumnsResult;
import org.apache.linkis.datasourcemanager.common.domain.DataSource;
import org.apache.linkis.datasourcemanager.common.domain.DataSourceType;
import org.apache.linkis.metadata.query.common.domain.MetaColumnInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

import static com.webank.wedatasphere.exchangis.datasource.core.exception.ExchangisDataSourceExceptionCode.*;

@Service
public class DefaultDataSourceService extends AbstractDataSourceService
        implements DataSourceUIGetter, DataSourceService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultDataSourceService.class);

    /**
     * Engine settings
     */
    private final EngineSettingsDao settingsDao;

    /**
     * Data source model
     */
    @Resource
    private DataSourceModelMapper modelMapper;

    /**
     * Model type key
     */
    @Resource
    private DataSourceModelTypeKeyMapper modelTypeKeyMapper;

    /**
     * Model relation model
     */
    @Resource
    private DataSourceModelRelationMapper modelRelationMapper;
    /**
     * Open service
     */
    @Resource
    private ExchangisJobOpenService jobOpenService;

    /**
     * Project open service
     */
    @Resource
    private ProjectOpenService projectOpenService;

    /**
     * Job and data source
     */
    @Autowired
    private ExchangisJobDsBindMapper exchangisJobDsBindMapper;

    @Resource
    private ParamKeySerializer keySerializer;

    @Autowired
    public DefaultDataSourceService(ExchangisDataSourceContext context,
                                    ExchangisJobParamConfigMapper exchangisJobParamConfigMapper, EngineSettingsDao settingsDao) {
        super(context, exchangisJobParamConfigMapper);
        this.settingsDao = settingsDao;
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Override
    public List<ExchangisDataSourceUIViewer> getJobDataSourceUIs(HttpServletRequest request, Long jobId) {
        if (Objects.isNull(jobId)) {
            return null;
        }
        ExchangisJobEntity job;
        try {
            job = this.jobOpenService.getJobById(jobId, false);
        } catch (ExchangisJobException e) {
            throw new ExchangisDataSourceException
                    .Runtime(CONTEXT_GET_DATASOURCE_NULL.getCode(), "Fail to get job entity (获得任务信息失败)", e);
        }
        if (Objects.isNull(job)) {
            return null;
        }

        List<ExchangisJobInfoContent> jobInfoContents = JobUtils.parseJobContent(job.getJobContent());
        List<ExchangisDataSourceUIViewer> uis = new ArrayList<>();
        for (ExchangisJobInfoContent cnt : jobInfoContents) {
            cnt.setEngine(job.getEngineType());
            ExchangisDataSourceUIViewer viewer = buildAllUI(request, job, cnt);
            uis.add(viewer);
        }

        return uis;
    }

    /**
     * Generate data source ui
     */
    @Override
    public List<ElementUI<?>> getDataSourceParamsUI(String dsType, String engineAndDirection) {

        ExchangisDataSourceDefinition exchangisDataSource = this.context.getExchangisDsDefinition(dsType);
        List<ExchangisJobParamConfig> paramConfigs = exchangisDataSource.getDataSourceParamConfigs();
        List<ExchangisJobParamConfig> filteredConfigs = new ArrayList<>();
        String[] engineDirect = engineAndDirection.split("-");
        String direction = engineDirect[1];
        for (ExchangisJobParamConfig paramConfig : paramConfigs) {
            //skip the
            Optional.ofNullable(paramConfig.getConfigDirection()).ifPresent(configDirection -> {
                if (configDirection.equalsIgnoreCase(engineAndDirection) || configDirection.equalsIgnoreCase(direction)){
                    filteredConfigs.add(paramConfig);
                }
            });
        }
        return this.buildDataSourceParamsUI(filteredConfigs);
    }

    @Override
    public List<ElementUI<?>> getJobEngineSettingsUI(String engineType) {
        return this.buildJobSettingsUI(engineType);
    }

    /**
     * 根据 LocalExchangisDataSourceLoader 加载到的本地的数据源与 Linkis 支持的数据源
     * 做比较，筛选出可以给前端展示的数据源类型
     */
    @Override
    public List<ExchangisDataSourceTypeDefinition> listDataSourceTypes(String operator,
                                                                       String engineType, String direct, String sourceType)
            throws ExchangisDataSourceException{
        List<ExchangisDataSourceTypeDefinition> typeDefinitions = new ArrayList<>();
        // Load engine settings
        List<EngineSettings> settingsList = this.settingsDao.getSettings();
        List<EngineSettings> engineSettings = new ArrayList<>();
        if (StringUtils.isEmpty(engineType)) {
            engineSettings = settingsList;
        } else {
            EngineSettings engineSetting = new EngineSettings();
            for (EngineSettings settings : settingsList) {
                if (StringUtils.equals(settings.getName(), engineType.toLowerCase())) {
                    engineSetting = settings;
                    break;
                }
            }
            engineSettings.add(engineSetting);
        }

        Set<String> directType = new HashSet<>();
        for (EngineSettings engineSetting: engineSettings) {
            for (int i = 0; i < engineSetting.getDirectionRules().size(); i++) {
                engineSetting.getDirectionRules().forEach(item -> {
                    String source = item.getSource();
                    String sink = item.getSink();
                    if (StringUtils.isEmpty(direct)) {
                        directType.add(source);
                        directType.add(sink);
                    } else if (StringUtils.equals(direct, "source")) {
                        directType.add(source);
                    } else {
                        // Sink types filter
                        if ((StringUtils.isBlank(sourceType) ||
                                (StringUtils.isNoneBlank(sourceType) && StringUtils.equals(source, sourceType.toLowerCase())))) {
                            directType.add(sink);
                        }
                    }
                });
            }
        }
        // Send to get data source types
        LinkisDataSourceRemoteClient client = ExchangisLinkisRemoteClient.getLinkisDataSourceRemoteClient();
        GetAllDataSourceTypesResult result = rpcSend(client, () -> GetAllDataSourceTypesAction.builder()
                .setUser(operator)
                .build(),
                LinkisDataSourceRemoteClient::getAllDataSourceTypes,CLIENT_DATASOURCE_GET_TYPES_ERROR.getCode(),
                "datasource get types null or empty");
        List<DataSourceType> dataSourceTypes = result.getAllDataSourceType();
        for (DataSourceType type : dataSourceTypes) {
            String typeName = type.getName();
            if (directType.contains(typeName)) {
                ExchangisDataSourceDefinition definition = this.context.getExchangisDsDefinition(typeName);
                ExchangisDataSourceTypeDefinition typeDef = new ExchangisDataSourceTypeDefinition(
                        type.getId(),
                        type.getClassifier(),
                        definition.name(),
                        definition.structClassifier()
                );
                // use linkis datasource table field to fill the definition bean
                typeDef.setIcon(type.getIcon());
                typeDef.setDescription(type.getDescription());
                typeDef.setOption(type.getOption());
                typeDefinitions.add(typeDef);
            }
        }
        return typeDefinitions;
    }

    /**
     * Create data source
     * @param operator operator operatorr
     * @param vo create vo
     * @return data source id
     * @throws ExchangisDataSourceException e
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(String operator, DataSourceCreateVo vo) throws ExchangisDataSourceException {
        Long modelId = vo.getModelId();
        // Merge parameter from data source model
        mergeModelParams(vo, modelId);
        Map<String, Object> payLoads = Json.fromJson(Json.toJson(vo, null), Map.class);
        Optional.ofNullable(payLoads).ifPresent(pay -> pay.put("labels", pay.get("label")));
        ExchangisDataSourceDefinition dsType = context.getExchangisDsDefinition(vo.getDataSourceTypeId());
        if (Objects.isNull(dsType)) {
            throw new ExchangisDataSourceException(CONTEXT_GET_DATASOURCE_NULL.getCode(), "exchangis context get datasource null");
        }
        LinkisDataSourceRemoteClient client = dsType.getDataSourceRemoteClient();
        // Send to create data source
        CreateDataSourceResult createResult = rpcSend(client, () -> CreateDataSourceAction.builder()
                .setUser(operator)
                .addRequestPayloads(payLoads)
                .build(),
                LinkisDataSourceRemoteClient::createDataSource, CLIENT_DATASOURCE_CREATE_ERROR.getCode(),
                "datasource create response null or empty");
        // Get data source id
        Long dataSourceId = createResult.getInsertId();
        // Send to create version
        UpdateDataSourceParameterResult versionResult = rpcSend(client, () -> UpdateDataSourceParameterAction.builder()
                .setUser(operator)
                .setDataSourceId(Long.parseLong(dataSourceId + ""))
                .addRequestPayloads(payLoads)
                .build(),
                LinkisDataSourceRemoteClient::updateDataSourceParameter, CLIENT_DATASOURCE_UPDATE_PARAMS_VERSION_ERROR.getCode(),
                "datasource update params version null or empty");
        // Build the relation between model and data source version
        DataSourceModelRelation relation = new DataSourceModelRelation();
        relation.setModelId(modelId);
        relation.setDsName(vo.getDataSourceName());
        relation.setDsVersion(versionResult.getVersion());
        this.modelRelationMapper.addDsModelRelation(Collections.singletonList(relation));
        Map<String, Object> versionParams = versionResult.getData();
        versionParams.put("id", dataSourceId);
        return versionParams;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> update(String operator, Long id, DataSourceCreateVo vo) throws ExchangisDataSourceException {
        ExchangisDataSourceDefinition dsType = context.getExchangisDsDefinition(vo.getDataSourceTypeId());
        if (Objects.isNull(dsType)) {
            throw new ExchangisDataSourceException(CONTEXT_GET_DATASOURCE_NULL.getCode(), "exchangis context get datasource null");
        }
        Long modelId = vo.getModelId();
        // Merge parameter from data source model
        mergeModelParams(vo, modelId);
        Map<String, Object> payLoads = Json.fromJson(Json.toJson(vo, null), Map.class);
        Optional.ofNullable(payLoads).ifPresent(pay -> pay.put("labels", pay.get("label")));
        LinkisDataSourceRemoteClient client = dsType.getDataSourceRemoteClient();
        // First send to get data source data before
        GetInfoByDataSourceIdResult result = rpcSend(ExchangisLinkisRemoteClient.getLinkisDataSourceRemoteClient(), () -> GetInfoByDataSourceIdAction.builder()
                        .setSystem("exchangis")
                        .setUser(operator)
                        .setDataSourceId(id).build(),
                LinkisDataSourceRemoteClient::getInfoByDataSourceId, CLIENT_QUERY_DATASOURCE_ERROR.getCode(),
                "");
        DataSource beforeDs = result.getDataSource();
        if (!Objects.equals(vo.getPublishedVersionId(), vo.getVersionId())){
            // TODO delete the relation between data source id, version id and model id
        }
        // Send to update data source
        rpcSend(client, () -> UpdateDataSourceAction.builder()
                .setUser(operator)
                .setDataSourceId(Long.parseLong(id + ""))
                .addRequestPayloads(payLoads)
                .build(),
                AbstractRemoteClient::execute, CLIENT_DATASOURCE_UPDATE_ERROR.getCode(),
                "datasource update null or empty");
        UpdateDataSourceParameterResult versionResult;
        try {
            // Send to create version
            versionResult = rpcSend(client, () -> UpdateDataSourceParameterAction.builder()
                            .setDataSourceId(Long.parseLong(id + ""))
                            .setUser(operator)
                            .addRequestPayloads(payLoads)
                            .build(),
                    LinkisDataSourceRemoteClient::updateDataSourceParameter,
                    CLIENT_DATASOURCE_UPDATE_PARAMS_VERSION_ERROR.getCode(),
                    "datasource update params version null or empty");
        } catch (ExchangisDataSourceException e){
            // Send to update the data source to rollback
            rpcSend(client, () -> UpdateDataSourceAction.builder()
                            .setUser(operator)
                            .setDataSourceId(Long.parseLong(id + ""))
                            .addRequestPayloads(Json.convert(beforeDs, Map.class, String.class, Object.class))
                            .build(),
                    AbstractRemoteClient::execute, CLIENT_DATASOURCE_UPDATE_ERROR.getCode(),
                    "datasource update null or empty");
            throw e;
        }
        // Build the relation between model and data source version
        DataSourceModelRelation relation = new DataSourceModelRelation();
        relation.setModelId(modelId);
        relation.setDsName(vo.getDataSourceName());
        relation.setDsVersion(versionResult.getVersion());
        this.modelRelationMapper.addDsModelRelation(Collections.singletonList(relation));
        return new HashMap<>();
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long delete(String operator,  Long id) throws ExchangisDataSourceException {
        QueryWrapper<ExchangisJobDsBind> condition = new QueryWrapper<>();
        condition.eq("source_ds_id", id).or().eq("sink_ds_id", id);
        Long inUseCount = this.exchangisJobDsBindMapper.selectCount(condition);
        if (inUseCount > 0) {
            throw new ExchangisDataSourceException(ExchangisDataSourceExceptionCode.CLIENT_DATASOURCE_DELETE_ERROR.getCode(), "目前存在引用依赖");
        }
        // TODO delete the relation between model and data source version
        LinkisDataSourceRemoteClient client = ExchangisLinkisRemoteClient.getLinkisDataSourceRemoteClient();
        DeleteDataSourceResult result = rpcSend(client, () -> DeleteDataSourceAction.builder()
                .setUser(operator).setDataSourceId(Long.parseLong(id + "")).builder(),
                LinkisDataSourceRemoteClient::deleteDataSource, CLIENT_DATASOURCE_DELETE_ERROR.getCode(),
                "datasource delete null or empty");
        return result.getRemoveId();
    }

    /**
     * Query database from data source
     * @param operator username
     * @param type type
     * @param id id
     * @return message
     * @throws ExchangisDataSourceException e
     */
    @Override
    public List<String> getDatabases(String operator, String type, Long id) throws ExchangisDataSourceException {
        ExchangisDataSourceDefinition definition = context.getExchangisDsDefinition(type);
        MetadataGetDatabasesResult result = rpcSend(definition.getMetaDataRemoteClient(), () -> MetadataGetDatabasesAction.builder()
                .setSystem("exchangis")
                .setSystem(type)
                .setDataSourceId(id)
                .setUser(operator)
                .build(),
                LinkisMetaDataRemoteClient::getDatabases, CLIENT_METADATA_GET_DATABASES_ERROR.getCode(),
                "metadata get databases null or empty");
        return Optional.ofNullable(result.getDbs()).orElse(new ArrayList<>());
    }

    /**
     * Query table in database from data source
     * @param  operator operator
     * @param type type
     * @param id id
     * @param database database name
     * @return message
     * @throws ExchangisDataSourceException e
     */
    @Override
    public List<String> getTables(String operator, String type, Long id, String database) throws ExchangisDataSourceException {
        ExchangisDataSourceDefinition definition = context.getExchangisDsDefinition(type);
        MetadataGetTablesResult tablesResult = rpcSend(definition.getMetaDataRemoteClient(), () -> MetadataGetTablesAction.builder()
                .setSystem(type)
                .setDataSourceId(id)
                .setDatabase(database)
                .setUser(operator)
                .build(),
                LinkisMetaDataRemoteClient::getTables, CLIENT_METADATA_GET_TABLES_ERROR.getCode(),
                "metadata get tables null or empty");
        return Optional.ofNullable(tablesResult.getTables()).orElse(new ArrayList<>());
    }

    /**
     * Query table fields (columns)
     * @param operator username
     * @param type type
     * @param id id
     * @param database database name
     * @param table table name
     * @return message
     * @throws ExchangisDataSourceException e
     */
    @Override
    public List<DataSourceDbTableColumn> getTableFields(String operator,
                                                        String type, Long id, String database, String table) throws ExchangisDataSourceException {
        ExchangisDataSourceDefinition definition = context.getExchangisDsDefinition(type);
        MetadataGetColumnsResult columnsResult = rpcSend(definition.getMetaDataRemoteClient(), () -> MetadataGetColumnsAction.builder()
                .setSystem(type)
                .setDataSourceId(id)
                .setDatabase(database)
                .setTable(table)
                .setUser(operator)
                .build(),
                LinkisMetaDataRemoteClient::getColumns, CLIENT_METADATA_GET_COLUMNS_ERROR.getCode(),
                "metadata get columns null or empty");
        List<MetaColumnInfo> columns = columnsResult.getAllColumns();
        return Optional.ofNullable(columns).orElse(new ArrayList<>()).stream().map(column -> {
            DataSourceDbTableColumn item = new DataSourceDbTableColumn();
            item.setName(column.getName());
            item.setType(column.getType());
            return item;
        }).collect(Collectors.toList());
    }

    /**
     * Query data sources
     * @param operator operator
     * @param vo vo object
     * @return response message
     * @throws Exception e
     */
    @Override
    public PageResult<ExchangisDataSourceItem> queryDataSources(String operator,
                                                                DataSourceQueryVo vo) throws ExchangisDataSourceException {
        String username = StringUtils.isNoneBlank(vo.getCreateUser()) ?
                vo.getCreateUser() : operator;
        int page = Objects.isNull(vo.getPage()) ? 1 : vo.getPage();
        int pageSize = Objects.isNull(vo.getPageSize()) ? 100 : vo.getPageSize();
        String dataSourceName = Objects.isNull(vo.getName()) ? "" : vo.getName().replace("_", "\\_");
        Long typeId = vo.getTypeId();
        int total = 0;
        // If to fetch from remote server
        boolean toRemote = true;
        Map<String, ExchangisDataSourceItem> dsQueryMap = new LinkedHashMap<>();
        Long refProjectId = vo.getProjectId();
        if (Objects.nonNull(refProjectId)){
            // Try to get data sources from project relation
            ExchangisProjectInfo project = projectOpenService.getProject(refProjectId);
            if (Objects.nonNull(project)){
                toRemote = !ExchangisProject.Domain.DSS.name()
                        .equalsIgnoreCase(project.getDomain());
                if (projectOpenService.hasAuthority(username, project, OperationType.PROJECT_QUERY)){
                    // Build project data source query
                    ProjectDsQueryVo dsQueryVo = new ProjectDsQueryVo();
                    dsQueryVo.setProjectId(refProjectId);
                    dsQueryVo.setName(dataSourceName);
                    if (Objects.nonNull(typeId)){
                        // Try to find the type string
                        ExchangisDataSourceDefinition definition =
                                this.context.getExchangisDsDefinition(typeId);
                        if (Objects.nonNull(definition)){
                            dsQueryVo.setType(definition.name());
                        }
                    }
                    PageResult<ExchangisProjectDsRelation> dsRelations = projectOpenService.queryDsRelation(dsQueryVo);
                    total += dsRelations.getTotal();
                    Optional.ofNullable(toExchangisDataSourceItems(refProjectId, dsRelations.getList()))
                            .ifPresent(list -> {
                                list.forEach(item -> {
                                    if (!dsQueryMap.containsKey(item.getName())) {
                                        dsQueryMap.put(item.getName(), item);
                                    }
                                });
                            });
                }
            }
        }
        if (toRemote) {
            // Recalculate the page number and page size
            if (dsQueryMap.size() >= pageSize){
                page = 1;
            } else if (total > 0){
                int totalPages = (total + pageSize - 1) / pageSize;
                if (page <= totalPages){
                    page = 1;
                } else {
                    // TODO has a problem that produces duplicate page data
                    page = page - totalPages;
                }
            }
            // Send to get data sources from linkis
            int finalPage = page;
            QueryDataSourceResult result = rpcSend(ExchangisLinkisRemoteClient.getLinkisDataSourceRemoteClient(), () -> {
                QueryDataSourceAction.Builder builder = QueryDataSourceAction.builder()
                        .setSystem("exchangis")
                        .setName(dataSourceName)
                        .setIdentifies("")
                        .setCurrentPage(finalPage)
                        .setUser(username)
                        .setPageSize(pageSize);
                if (!Objects.isNull(typeId)) {
                    builder.setTypeId(typeId);
                }
                if (!Strings.isNullOrEmpty(vo.getTypeName())) {
                    builder.setSystem(vo.getTypeName());
                }
                return builder.build();
            }, LinkisDataSourceRemoteClient::queryDataSource, CLIENT_QUERY_DATASOURCE_ERROR.getCode(),
                    "");
            total += result.getTotalPage();
            List<DataSource> dataSources = result.getAllDataSource();
            int addSize = Math.min(pageSize - dsQueryMap.size(), dataSources.size());
            if (addSize > 0){
                Optional.ofNullable(toExchangisDataSourceItems(dataSources.subList(0, addSize)))
                        .ifPresent(list -> {
                            for(int i = 0; i < addSize; i++) {
                                ExchangisDataSourceItem item = list.get(i);
                                if (!dsQueryMap.containsKey(item.getName())) {
                                    dsQueryMap.put(item.getName(), item);
                                }
                            }
                        });
            }
        }
        PageResult<ExchangisDataSourceItem> pageResult = new PageResult<>();
        pageResult.setList(new ArrayList<>(dsQueryMap.values()));
        pageResult.setTotal((long) total);
        return pageResult;
    }

    /**
     * List data sources
     * @param operator operator
     * @param typeName type name
     * @param typeId type id
     * @param page page num
     * @param pageSize page size
     * @return data sources
     * @throws ExchangisDataSourceException
     */
    public List<ExchangisDataSourceItem> listDataSources(String operator,
                                                         String typeName, Long typeId, Integer page, Integer pageSize) throws ExchangisDataSourceException {
        QueryDataSourceResult result = rpcSend(ExchangisLinkisRemoteClient.getLinkisDataSourceRemoteClient(), () -> {
            QueryDataSourceAction.Builder builder = QueryDataSourceAction.builder()
                    .setSystem("exchangis")
                    .setIdentifies("")
                    .setUser(operator);
            if (!Strings.isNullOrEmpty(typeName)) {
                builder.setName(typeName);
            }
            if (!Objects.isNull(typeId)) {
                builder.setTypeId(typeId);
            }
            if (!Objects.isNull(page)) {
                builder.setCurrentPage(page);
            } else {
                builder.setCurrentPage(1);
            }
            if (!Objects.isNull(pageSize)) {
                builder.setPageSize(pageSize);
            } else {
                builder.setPageSize(200);
            }
            return builder.build();
        }, LinkisDataSourceRemoteClient::queryDataSource, CLIENT_QUERY_DATASOURCE_ERROR.getCode(),
                "");
        List<ExchangisDataSourceItem> dataSources = new ArrayList<>();
        if (!Objects.isNull(result.getAllDataSource())) {
            dataSources = toExchangisDataSourceItems(result.getAllDataSource());
        }
        return dataSources;
    }

    /**
     * Get data source details
     * @param operator operator
     * @param id id
     * @param versionId version id
     * @return detail
     */
    @Override
    public ExchangisDataSourceDetail getDataSource(String operator, Long id, String versionId) {
        if (Strings.isNullOrEmpty(versionId)) {
            return getDataSource(operator, id);
        } else {
            return getDataSourceByIdAndVersionId(operator, id, versionId);
        }
    }

    @Override
    public ExchangisDataSourceDetail getDataSource(String operator, Long id) throws ErrorException {
        GetInfoByDataSourceIdResult result = rpcSend(ExchangisLinkisRemoteClient.getLinkisDataSourceRemoteClient(), () -> GetInfoByDataSourceIdAction.builder()
                        .setSystem("exchangis")
                        .setUser(operator)
                        .setDataSourceId(id).build(),
                LinkisDataSourceRemoteClient::getInfoByDataSourceId, CLIENT_QUERY_DATASOURCE_ERROR.getCode(),
                "");
        return toExchangisDataSourceDetail(result.getDataSource(),
                Optional.ofNullable(result.getDataSource().getVersionId()).orElse(0L).toString());
    }

    /**
     * Get data source by name
     * @param userName username
     * @param dsName data source name
     * @return dto
     * @throws ErrorException e
     */
    @Override
    public ExchangisDataSourceDetail getDataSource(String userName, String dsName) throws ErrorException{
        GetInfoByDataSourceNameResult result = rpcSend(ExchangisLinkisRemoteClient.getLinkisDataSourceRemoteClient(), () -> GetInfoByDataSourceNameAction.builder()
                .setSystem("exchangis").setUser(userName).setDataSourceName(dsName)
                .build(),
                LinkisDataSourceRemoteClient::getInfoByDataSourceName, CLIENT_QUERY_DATASOURCE_ERROR.getCode(),
                "");
        return toExchangisDataSourceDetail(result.getDataSource(),
                Optional.ofNullable(result.getDataSource().getVersionId()).orElse(0L).toString());
    }

    /**
     *
     * @param operator operator
     * @param id data source id
     * @return versions
     * @throws ErrorException e
     */
    @Override
    public List<Map<String, Object>> getDataSourceVersionsById(String operator, Long id) throws ErrorException {
        GetInfoByDataSourceIdResult dataSourceInfo = rpcSend(ExchangisLinkisRemoteClient.getLinkisDataSourceRemoteClient(), () -> GetInfoByDataSourceIdAction.builder()
                .setSystem("exchangis")
                .setUser(operator).setDataSourceId(id).build(),
                LinkisDataSourceRemoteClient::getInfoByDataSourceId, CLIENT_GET_DATASOURCE_ERROR.getCode(),
                "");
        DataSource dataSource = dataSourceInfo.getDataSource();
        // Get the published version id
        Long publishedVersionId = dataSource.getPublishedVersionId();
        GetDataSourceVersionsResult versionsResult = rpcSend(ExchangisLinkisRemoteClient.getLinkisDataSourceRemoteClient(), () -> GetDataSourceVersionsAction.builder()
                .setUser(operator).setDataSourceId(Long.parseLong(id + "")).build(),
                LinkisDataSourceRemoteClient::getDataSourceVersions, CLIENT_GET_DATASOURCE_VERSION_ERROR.getCode(),
                "datasource version response body null or empty");
        List<Map<String, Object>> versions = versionsResult.getVersions();
        if (Objects.nonNull(versions) && Objects.nonNull(publishedVersionId)) {
            for (Map<String, Object> version : versions) {
                Object versionId = version.get("versionId");
                if (Objects.isNull(versionId)) {
                    continue;
                }
                long v = Long.parseLong(versionId.toString());
                if (publishedVersionId.equals(v)) {
                    version.put("published", true);
                }
            }
        }
        // Sort the version list
        versions.sort((left, right) -> {
            Object vid1 = left.get("versionId");
            Object vid2 = right.get("versionId");
            long a1 = Long.parseLong(Optional.ofNullable(vid1).orElse(0).toString());
            long a2 = Long.parseLong(Optional.ofNullable(vid2).orElse(0).toString());;
            return (int) (a2 - a1);
        });
        return versions;
    }

    /**
     *
     * @param operator operator
     * @param id data source id
     * @param version version id
     */
    @Override
    public void testConnect(String operator, Long id, Long version) throws ExchangisDataSourceException {
        rpcSend(ExchangisLinkisRemoteClient.getLinkisDataSourceRemoteClient(), () ->
                        DataSourceTestConnectAction.builder().setUser(operator).setDataSourceId(Long.parseLong(id + "")).setVersion(version + "").build(),
                    LinkisDataSourceRemoteClient::getDataSourceTestConnect, CLIENT_DATASOURCE_TEST_CONNECTION_ERROR.getCode(),
                "");
    }

    /**
     *
     * @param operator operator
     * @param vo value object
     */
    @Override
    public void testConnectByVo(String operator, DataSourceCreateVo vo){
        Map<String, Object> payLoads = Json.fromJson(Json.toJson(vo, null), Map.class);
        Optional.ofNullable(payLoads).ifPresent(pay -> pay.put("labels", pay.get("label")));
        rpcSend(ExchangisLinkisRemoteClient.getLinkisDataSourceRemoteClient(), () ->
                new ParamsTestConnectAction(payLoads, operator),
                LinkisDataSourceRemoteClient::execute, CLIENT_DATASOURCE_TEST_CONNECTION_ERROR.getCode(),
                "");
    }

    /**
     * Publish data source
     * @param operator operator
     * @param id data source id
     * @param version version
     * @return message mes
     * @throws ErrorException e
     */
    @Override
    public void publishDataSource(String operator, Long id, Long version){
        rpcSend(ExchangisLinkisRemoteClient.getLinkisDataSourceRemoteClient(), () -> PublishDataSourceVersionAction.builder()
                .setUser(operator).setDataSourceId(Long.parseLong(id + "")).setVersion(Long.parseLong(version + ""))
                .build(),
                LinkisDataSourceRemoteClient::publishDataSourceVersion, CLIENT_DATASOURCE_PUBLISH_VERSION_ERROR.getCode(),
                "datasource publish version response body null or empty");
    }

    /**
     *
     * @param operator operator
     * @param id data source id
     * @return params
     */
    @Override
    public Map<String, Object> getDataSourceConnectParamsById(String operator, Long id) throws ExchangisDataSourceException {
        GetConnectParamsByDataSourceIdResult result = rpcSend(ExchangisLinkisRemoteClient.getLinkisDataSourceRemoteClient(), () -> GetConnectParamsByDataSourceIdAction.builder()
                .setSystem("exchangis")
                .setUser(operator).setDataSourceId(id).build(),
                LinkisDataSourceRemoteClient::getConnectParams, CLIENT_DATASOURCE_PARAMS_GET_ERROR.getCode(),
                "");
        return result.getConnectParams();
    }

    /**
     *
     * @param operator operator
     * @param id id
     * @throws ErrorException e
     */
    @Override
    public void expireDataSource(String operator, Long id) throws ExchangisDataSourceException {
        rpcSend(ExchangisLinkisRemoteClient.getLinkisDataSourceRemoteClient(), () ->
                ExpireDataSourceAction.builder()
                        .setUser(operator)
                        .setDataSourceId(Long.parseLong(id + "")).build(),
                LinkisDataSourceRemoteClient::expireDataSource, CLIENT_DATASOURCE_EXPIRE_ERROR.getCode(),
                "datasource expire response body null or empty");
    }

    /**
     *
     * @param operator operator
     * @param typeId type id
     * @return map
     * @throws ExchangisDataSourceException
     */
    public List<Map<String, Object>> getDataSourceKeyDefine(String operator, Long typeId) throws ExchangisDataSourceException {
        if (Objects.isNull(typeId)) {
            throw new ExchangisDataSourceException(ExchangisDataSourceExceptionCode.PARAMETER_INVALID.getCode(),
                    "dataSourceType id should not be null");
        }
        GetKeyTypeDatasourceResult result = rpcSend(ExchangisLinkisRemoteClient.getLinkisDataSourceRemoteClient(), () ->
                GetKeyTypeDatasourceAction.builder()
                        .setUser(operator).setDataSourceTypeId(typeId).build(),
                LinkisDataSourceRemoteClient::getKeyDefinitionsByType, CLIENT_DATASOURCE_GET_KEY_DEFINES_ERROR.getCode(),
                "");
        return Objects.isNull(result.getKeyDefine()) ? null : result.getKeyDefine();
    }

    /**
     *
     * @param engine engine
     * @param sourceType  source type
     * @param sinkType sink type
     * @throws ExchangisDataSourceException e
     */
    public void supportDataSource(String engine, String sourceType, String sinkType) throws ExchangisDataSourceException {
        switch (engine) {
            case "SQOOP":
                if (!("HIVE".equals(sourceType) || "HIVE".equals(sinkType))) {
                    throw new ExchangisDataSourceException(ExchangisDataSourceExceptionCode.DS_MAPPING_MUST_CONTAIN_HIVE.getCode(), "SQOOP引擎输入/输出数据源必须包含HIVE");
                }
                if (sourceType.equals(sinkType)) {
                    throw new ExchangisDataSourceException(ExchangisDataSourceExceptionCode.DS_TYPE_MUST_DIFFERENT.getCode(), "SQOOP引擎读写类型不可相同");
                }
                break;
            case "DATAX":
                break;
            default:
                throw new ExchangisDataSourceException(ExchangisDataSourceExceptionCode.UNSUPPORTEd_ENGINE.getCode(), "不支持的引擎");
        }
    }

    /**
     *
     * @param operator operate user
     * @param sourceName source name
     * @param newName new name
     * @throws ErrorException e
     */
    @Override
    public void copyDataSource(String operator,
                               String sourceName, String newName) throws ErrorException{
        // Try to get data source with new name
        ExchangisDataSourceDetail dsResult;
        try {
            dsResult = getDataSource(operator, newName);
            if (Objects.nonNull(dsResult)){
                // data source already exists
                return;
            }
        } catch (Exception e){
            // Ignore the exception
        }
        // Use admin user to get model data source
        String admin = GlobalConfiguration.getAdminUser();
        dsResult = getDataSource(StringUtils.isNotBlank(admin) ? admin : operator, sourceName);
        if (Objects.nonNull(dsResult)){
            DataSourceCreateVo createDs = new DataSourceCreateVo();
            createDs.setDataSourceName(newName);
            createDs.setDataSourceTypeId(dsResult.getDataSourceTypeId());
            createDs.setDataSourceDesc(dsResult.getDataSourceDesc());
            createDs.setCreateSystem(dsResult.getCreateSystem());
            createDs.setCreateUser(operator);
            createDs.setLabel(dsResult.getLabels());
            createDs.setConnectParams(dsResult.getConnectParams());
            createDs.setComment("init");
            Map<String, Object> versionParams = create(operator, createDs);
            Object version = versionParams.get("version");
            Object id = versionParams.get("id");
            if (Objects.nonNull(version) && Objects.nonNull(id)){
                publishDataSource(operator,
                        Long.parseLong(String.valueOf(id)), Long.parseLong(String.valueOf(version)));
            }
        }
    }

    @Override
    public void recycleDataSource(String userName, String handover) throws ExchangisDataSourceException {

    }

    /**
     * Merge model parameters
     * @param vo void
     * @param modelId model id
     */
    private void mergeModelParams(DataSourceCreateVo vo, Long modelId){
        if (Objects.nonNull(modelId)){
            return;
        }
        Map<String, Object> connectParams = Optional.ofNullable(vo.getConnectParams())
                .orElse(new HashMap<>());
        DataSourceModel model = this.modelMapper.selectOne(modelId);
        if (Objects.nonNull(model)){
            Map<String, Object> modelParams = model.resolveParams();
            // TODO get the model key definitions
            List<ExchangisDataSourceTypeDefinition> definitions = new ArrayList<>();
            definitions.forEach(definition -> {
                // TODO try to serialize the parameter

            });
            // Add and overwrite to connect params
            connectParams.putAll(modelParams);
            vo.setConnectParams(connectParams);
        }
    }
    /**
     * Get data source by id and version id
     * @param operator operator
     * @param id data source id
     * @param versionId version id
     * @return detail
     * @throws ErrorException
     */
    private ExchangisDataSourceDetail getDataSourceByIdAndVersionId(String operator, Long id, String versionId) throws ErrorException {
        GetInfoByDataSourceIdAndVersionIdResult result = (GetInfoByDataSourceIdAndVersionIdResult) rpcSend(ExchangisLinkisRemoteClient.getLinkisDataSourceRemoteClient(), () -> GetDataSourceInfoByIdAndVersionIdAction.builder()
                .setSystem("exchangis")
                .setUser(operator)
                .setDataSourceId(id)
                .setVersionId(versionId).build(),
                LinkisDataSourceRemoteClient::execute, CLIENT_QUERY_DATASOURCE_ERROR. getCode(),
                "");
        return toExchangisDataSourceDetail(result.getDataSource(), versionId);
    }

    /**
     * To data source detail
     * @param dataSource data source
     * @param version version
     * @return detail
     */
    private ExchangisDataSourceDetail toExchangisDataSourceDetail(DataSource dataSource, String version){
        ExchangisDataSourceDetail detail = new ExchangisDataSourceDetail(dataSource);
        if (StringUtils.isNotBlank(version)) {
            // TODO Get the model id by data source and version id
        }
        return detail;
    }
    /**
     * Convert project data source relations to exchangis data sources
     * @param dsRelations relation
     * @return list
     */
    private List<ExchangisDataSourceItem> toExchangisDataSourceItems(Long projectId, List<ExchangisProjectDsRelation> dsRelations){
        return dsRelations.stream().map(ds -> {
            ExchangisDataSourceItem item = new ExchangisDataSourceItem();
            item.setId(ds.getDsId());
            item.setName(ds.getDsName());
            item.setType(ds.getDsType());
            item.setCreateUser(ds.getDsCreator());
            return item;
        }).collect(Collectors.toList());
    }
    /**
     * Convert linkis data sources to exchangis data sources
     * @param dataSources  linkis data sources
     * @return list
     */
    private List<ExchangisDataSourceItem> toExchangisDataSourceItems(List<DataSource> dataSources){
        return dataSources.stream().map(ds -> {
            ExchangisDataSourceItem item = new ExchangisDataSourceItem();
            item.setId(ds.getId());
            item.setCreateIdentify(ds.getCreateIdentify());
            item.setName(ds.getDataSourceName());
            Optional.ofNullable(ds.getDataSourceType()).ifPresent(type -> {
                item.setType(type.getName());
            });
            item.setCreateSystem(ds.getCreateSystem());
            item.setDataSourceTypeId(ds.getDataSourceTypeId());
            item.setLabels(ds.getLabels());
            item.setLabel(ds.getLabels());
            item.setDesc(ds.getDataSourceDesc());
            item.setCreateUser(ds.getCreateUser());
            item.setModifyUser(ds.getModifyUser());
            item.setModifyTime(ds.getModifyTime());
            item.setVersionId(ds.getVersionId());
            item.setExpire(ds.isExpire());
            return item;
        }).collect(Collectors.toList());
    }


}