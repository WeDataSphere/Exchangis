package com.webank.wedatasphere.exchangis.job.server.render.transform;

/**
 * Lightweight column VO for file source (only used when sourceTypeId == "file").
 * Provided by frontend from the upload parse result, possibly with user-edited inferred types (M4).
 *
 * 文件 source 轻量列 VO（仅 sourceTypeId == "file" 时使用）。
 * 由前端从上传解析结果携带，可能含用户在 UI 上修改过的推断类型（M4）。
 *
 * Note: Do NOT reuse parse-package FileColumnDefine to avoid coupling between
 *       render package and parse package (per supplementary design).
 * 注意：不复用 parse 包的 FileColumnDefine，避免 render 包与 parse 包耦合（补充设计要求）。
 */
public class FileColumnVo {

    /**
     * Normalized column name
     * 规范化列名
     */
    private String name;

    /**
     * Inferred type (int/long/double/decimal/date/timestamp/string)
     * 推断类型（int/long/double/decimal/date/timestamp/string）
     */
    private String type;

    public FileColumnVo() {
    }

    public FileColumnVo(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
