package com.xiaomi.infra.pegasus.tools;

import org.apache.logging.log4j.Level;

public class LoggerOptions {
  private Level rollingLogLevel = Level.ALL;
  private boolean enablePegasusCustomLog = true;
  // PatternLayout
  private String layoutPattern = "%d{yyyy-MM-dd HH:mm:ss} %-5p %c{1}:%L - %m%n";
  // DeleteAction
  private String deleteAge = "P7D";
  private String deleteFilePath = "log/pegasus";
  private String deleteFileNamePattern = "pegasus.client.log*";
  // RolloverStrategy
  private String maxFileNumber = "5";
  private String minFileNumber = "1";
  // SizeBasedTriggeringPolicy
  private String singleFileSize = "10";
  // RollingFileAppender
  private String rollingFileAppenderName = "pegasusRolling-" + System.currentTimeMillis();
  private String rollingFileSaveName = "log/pegasus/pegasus.client.log";
  private String rollingFileSavePattern = "log/pegasus/pegasus.client.log.%d{yyyy-MM-dd.HH:mm:ss}";

  public LoggerOptions setEnablePegasusCustomLog(boolean enablePegasusCustomLog) {
    this.enablePegasusCustomLog = enablePegasusCustomLog;
    return this;
  }

  public void setRollingFileSaveName(String rollingFileSaveName) {
    this.rollingFileSaveName = rollingFileSaveName;
    // TODO rollingFileSavePattern, deleteFilePath, deleteFileNamePattern need be re-set base
    // rollingFileSaveName
  }

  public boolean isEnablePegasusCustomLog() {
    return enablePegasusCustomLog;
  }

  public Level getRollingLogLevel() {
    return rollingLogLevel;
  }

  public String getLayoutPattern() {
    return layoutPattern;
  }

  public String getDeleteAge() {
    return deleteAge;
  }

  public String getDeleteFilePath() {
    return deleteFilePath;
  }

  public String getDeleteFileNamePattern() {
    return deleteFileNamePattern;
  }

  public String getMaxFileNumber() {
    return maxFileNumber;
  }

  public String getMinFileNumber() {
    return minFileNumber;
  }

  public String getSingleFileSize() {
    return singleFileSize;
  }

  public String getRollingFileAppenderName() {
    return rollingFileAppenderName;
  }

  public String getRollingFileSaveName() {
    return rollingFileSaveName;
  }

  public String getRollingFileSavePattern() {
    return rollingFileSavePattern;
  }
}