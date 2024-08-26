package com.webank.wedatasphere.exchangis.datasource.domain;

import com.webank.wedatasphere.exchangis.common.domain.ExchangisDataSource;

import java.util.Date;

/**
 * Data source For list view
 */
public class ExchangisDataSourceItem implements ExchangisDataSource {

    private Long id;
    private String name;
    private String type;
    private Long dataSourceTypeId;
    private Long dataSourceModelId;
    private String createIdentify;
    private String createSystem;
    private String desc;
    private String createUser;
    private String labels;
    private String label;
    private Long versionId;
    private String modifyUser;
    private Date modifyTime;
    private boolean expire;

    public boolean isExpire() {
        return expire;
    }

    public void setExpire(boolean expire) {
        this.expire = expire;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getCreator() {
        return this.createUser;
    }

    @Override
    public void setCreator(String creator) {
        this.createUser = creator;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCreateIdentify() {
        return createIdentify;
    }

    public void setCreateIdentify(String createIdentify) {
        this.createIdentify = createIdentify;
    }

    public Long getDataSourceTypeId() {
        return dataSourceTypeId;
    }

    public void setDataSourceTypeId(Long dataSourceTypeId) {
        this.dataSourceTypeId = dataSourceTypeId;
    }

    public Long getDataSourceModelId() {
        return dataSourceModelId;
    }

    public void setDataSourceModelId(Long dataSourceModelId) {
        this.dataSourceModelId = dataSourceModelId;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String getCreateUser() {
        return createUser;
    }

    public void setCreateUser(String createUser) {
        this.createUser = createUser;
    }

    public String getLabels() {
        return labels;
    }

    public void setLabels(String labels) {
        this.labels = labels;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Long getVersionId() {
        return versionId;
    }

    public void setVersionId(Long versionId) {
        this.versionId = versionId;
    }

    public String getModifyUser() {
        return modifyUser;
    }

    public void setModifyUser(String modifyUser) {
        this.modifyUser = modifyUser;
    }

    public Date getModifyTime() {
        return modifyTime;
    }

    public void setModifyTime(Date modifyTime) {
        this.modifyTime = modifyTime;
    }

    public String getCreateSystem() {
        return createSystem;
    }

    public void setCreateSystem(String createSystem) {
        this.createSystem = createSystem;
    }

}
