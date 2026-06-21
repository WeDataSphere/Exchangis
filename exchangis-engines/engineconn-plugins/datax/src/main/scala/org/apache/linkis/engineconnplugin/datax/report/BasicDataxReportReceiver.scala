package org.apache.linkis.engineconnplugin.datax.report
import com.alibaba.datax.core.statistics.communication.{Communication, CommunicationTool}
import org.apache.commons.lang3.StringUtils
import org.apache.linkis.common.conf.CommonVars
import org.apache.linkis.protocol.engine.JobProgressInfo

import java.nio.charset.StandardCharsets
import java.util
import java.util.Objects

/**
 * Basic datax report receiver
 */
class BasicDataxReportReceiver extends DataxReportReceiver {

  private var jobId: String =_

  /**
   * Error report in metric
   */
  private val ERROR_REPORT_NAME: CommonVars[String] = CommonVars[String]("wds.linkis.engineconn.datax.report.error.name", "errorReport")

  /**
   * Max byte length of error report in metric.
   * Limit to a half of MySQL TEXT column (TEXT = 65535 bytes), so the errorReport will never
   * occupy more than half of the metrics text field(限制不超过 text 类型长度的一半).
   */
  private val ERROR_REPORT_MAX_BYTES: CommonVars[Int] = CommonVars[Int]("wds.linkis.engineconn.datax.report.error.max.bytes", 32767)

  /**
   * Suffix to indicate that the error report has been truncated(已截断标记)
   */
  private val ERROR_REPORT_TRUNCATE_SUFFIX: String = "...(truncated)"

  /**
   * Just store the last communication
   */
  private var lastCommunication: Communication = _


  /**
   * Receive communication
   *
   * @param communication communication
   */
  override def receive(jobId: String, communication: Communication): Unit = {
    if (StringUtils.isNotBlank(jobId)){
      this.jobId = jobId
    }
    // Update
    this.lastCommunication = communication
  }

  /**
   * Progress value
   *
   * @return
   */
  override def getProgress: Float = {
      Option(this.lastCommunication) match {
        case Some(communication) =>
          communication.getDoubleCounter(CommunicationTool.PERCENTAGE).floatValue()
        case _ => 0f
      }
  }

  /**
   * Progress info
   *
   * @return
   */
override def getProgressInfo: Array[JobProgressInfo] = {
    // datax does not have failed task
    var totalTask: Long = 0
    var finishTask: Long = 0
    Option(this.lastCommunication) match {
      case Some(communication) =>
        // Just statistics the total job
        finishTask = communication.getLongCounter(CommunicationTool.STAGE)
        // reverse calculate
        val percentage = communication.getDoubleCounter(CommunicationTool.PERCENTAGE)
        totalTask = (finishTask.toDouble / percentage).toInt
      case _ =>
    }
    Array(JobProgressInfo(this.jobId, totalTask.toInt, (totalTask - finishTask).toInt, 0, finishTask.toInt))
}

  /**
   * Metrics info
   *
   * @return
   */
  override def getMetrics: util.Map[String, Any] = {
    // Convert the whole counter in communication
    Option(this.lastCommunication) match {
      case Some(communication) =>
        val metric = new util.HashMap[String, Any]()
        Option(communication.getCounter) match {
          case Some(counter) =>
            metric.putAll(counter)
          case _ =>
        }
        Option(communication.getMessage(ERROR_REPORT_NAME.getValue)) match {
          case Some(messages) =>
            // Flatten the message list to a single string and limit the byte length,
            // so that the errorReport will not exceed half of the metrics TEXT column
            // and will not contain characters that require utf8mb4 to store
            // (将错误信息列表压成单个字符串，并限制字节长度，避免超出 metrics TEXT 字段的一半，
            // 且不包含必须用 utf8mb4 才能保存的字符)
            val errorReport = limitErrorReport(messages)
            if (StringUtils.isNotBlank(errorReport)){
              metric.put(ERROR_REPORT_NAME.getValue, errorReport)
            }
          case _ =>
        }
        metric
      case _ => new util.HashMap[String, Any]()
    }
  }

  /**
   * Limit the error report content.
   * <p>
   * 1. Flatten the message list to a single string, join with line separator.
   * 2. Strip the characters that require utf8mb4 to store (i.e. the supplementary
   *    characters encoded by surrogate pairs), so the content can be saved with
   *    utf8(3-byte) charset (剔除必须用 utf8mb4 才能保存的补充平面字符/代理对).
   * 3. Truncate by utf-8 bytes to the max length, never breaking a multi-byte
   *    character (按 utf-8 字节截断，不切断多字节字符).
   *
   * @param messages message list from communication
   * @return limited error report string
   */
  private def limitErrorReport(messages: util.List[String]): String = {
    if (Objects.isNull(messages) || messages.isEmpty){
      return ""
    }
    // Flatten the message list to a single string
    val builder = new StringBuilder
    val iterator = messages.iterator()
    var first = true
    while (iterator.hasNext) {
      val message = iterator.next()
      if (Objects.nonNull(message)) {
        if (!first) {
          builder.append("\n")
        }
        builder.append(message)
        first = false
      }
    }
    val flattened = builder.toString
    if (StringUtils.isBlank(flattened)){
      return ""
    }
    // Strip the surrogate pairs (supplementary characters that require utf8mb4)
    val stripped = stripSurrogates(flattened)
    // Truncate by utf-8 bytes
    truncateUtf8(stripped, ERROR_REPORT_MAX_BYTES.getValue)
  }

  /**
   * Strip the surrogate characters (high/low surrogate), so that the remaining
   * content can be stored with utf8(3-byte) charset instead of utf8mb4
   * (剔除代理对字符，使内容可用 utf8 三字节编码保存，无需 utf8mb4)
   *
   * @param value origin string
   * @return string without surrogate characters
   */
  private def stripSurrogates(value: String): String = {
    val builder = new StringBuilder(value.length)
    var i = 0
    val length = value.length
    while (i < length) {
      val ch = value.charAt(i)
      if (!Character.isSurrogate(ch)) {
        builder.append(ch)
      }
      i += 1
    }
    builder.toString
  }

  /**
   * Truncate the string by utf-8 bytes to the max length.
   * If truncated, never break a multi-byte character and append a suffix marker
   * (按 utf-8 字节截断到最大长度，截断时不切断多字节字符并追加截断标记)
   *
   * @param value    origin string
   * @param maxBytes max byte length in utf-8
   * @return truncated string
   */
  private def truncateUtf8(value: String, maxBytes: Int): String = {
    if (maxBytes <= 0){
      return ""
    }
    val bytes = value.getBytes(StandardCharsets.UTF_8)
    if (bytes.length <= maxBytes){
      return value
    }
    // Reserve space for the truncate suffix
    val suffixBytes = ERROR_REPORT_TRUNCATE_SUFFIX.getBytes(StandardCharsets.UTF_8)
    var budget = maxBytes - suffixBytes.length
    // If the budget cannot hold the suffix, only return the (truncated) suffix
    if (budget <= 0){
      val fit = math.min(maxBytes, suffixBytes.length)
      return new String(suffixBytes, 0, fit, StandardCharsets.UTF_8)
    }
    // Step back to the utf-8 character boundary (a leading byte satisfies (byte & 0xC0) != 0x80)
    while (budget > 0 && (bytes(budget) & 0xC0) == 0x80){
      budget -= 1
    }
    if (budget <= 0){
      return ERROR_REPORT_TRUNCATE_SUFFIX
    }
    new String(bytes, 0, budget, StandardCharsets.UTF_8) + ERROR_REPORT_TRUNCATE_SUFFIX
  }


}
