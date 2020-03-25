// Copyright (c) 2017, Xiaomi, Inc.  All rights reserved.
// This source code is licensed under the Apache License Version 2.0, which
// can be found in the LICENSE file in the root directory of this source tree.
package com.xiaomi.infra.pegasus.tools;

import java.nio.charset.Charset;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.RolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.TriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.action.Action;
import org.apache.logging.log4j.core.appender.rolling.action.DeleteAction;
import org.apache.logging.log4j.core.appender.rolling.action.Duration;
import org.apache.logging.log4j.core.appender.rolling.action.IfFileName;
import org.apache.logging.log4j.core.appender.rolling.action.IfLastModified;
import org.apache.logging.log4j.core.appender.rolling.action.PathCondition;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// The wrapper base log4j2:
// 1. default, `LoggerOptions.enablePegasusCustomLog = true`.user will use pegasus custom log
// config, but user can change the `LoggerOptions.rollingFileSaveName`
// to set log path.
// 2. if `LoggerOptions.enablePegasusCustomLog = false`, user will use the xml config.
// 3. if xml config just exist same appender name, directly use xml config, though you set
// `LoggerOptions.enablePegasusCustomLog = true`.
// 4. `LoggerOptions` expose `enablePegasusCustomLog` and `rollingFileSaveName` to user by
// `pegasus.properties`.
public class LogWrapper {

  private static final Object singletonLock = new Object();
  private static final LoggerOptions defaultLoggerOptions = new LoggerOptions();
  private static PegasusRollingFileLogger singletonPegasusLogger;

  public static Logger getRollingFileLogger(Class clazz) {
    return getRollingFileLogger(defaultLoggerOptions, clazz);
  }

  public static Logger getRollingFileLogger(LoggerOptions loggerOptions, Class clazz) {
    if (!loggerOptions.isEnablePegasusCustomLog()) {
      return LoggerFactory.getLogger(clazz);
    }

    if (singletonPegasusLogger != null) {
      return singletonPegasusLogger.getLogger(clazz.getName());
    }

    synchronized (singletonLock) {
      if (singletonPegasusLogger != null) {
        return singletonPegasusLogger.getLogger(clazz.getName());
      }

      singletonPegasusLogger = createRollingFileAppender(loggerOptions);
      return singletonPegasusLogger.getLogger(clazz.getName());
    }
  }

  private static PegasusRollingFileLogger createRollingFileAppender(LoggerOptions loggerOptions) {

    LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false /*todo*/);
    Configuration configuration = loggerContext.getConfiguration();

    // TODO(jiashuo1) if xml has same name appender name, now choose the xml config.
    if ((configuration.getAppender(loggerOptions.getRollingFileAppenderName())) != null
        && configuration.getAppender(loggerOptions.getRollingFileAppenderName())
            instanceof RollingFileAppender) {
      singletonPegasusLogger = new PegasusRollingFileLogger();
      return singletonPegasusLogger;
    }

    PatternLayout patternLayout =
        PatternLayout.newBuilder()
            .withCharset(Charset.forName("UTF-8"))
            .withConfiguration(configuration)
            .withPattern(loggerOptions.getLayoutPattern())
            .build();

    PathCondition lastModified =
        IfLastModified.createAgeCondition(Duration.parse(loggerOptions.getDeleteAge()), null);
    PathCondition fileNameMatch =
        IfFileName.createNameCondition(
            loggerOptions.getDeleteFileNamePattern(),
            loggerOptions.getDeleteFileNamePattern(),
            null);
    PathCondition[] pathConditions = new PathCondition[] {fileNameMatch, lastModified};
    DeleteAction action =
        DeleteAction.createDeleteAction(
            loggerOptions.getDeleteFilePath(),
            false,
            1,
            false,
            null,
            pathConditions,
            null,
            configuration);
    Action[] actions = new Action[] {action};

    RolloverStrategy strategy =
        DefaultRolloverStrategy.newBuilder()
            .withMax(loggerOptions.getMaxFileNumber())
            .withMin(loggerOptions.getMinFileNumber())
            .withCustomActions(actions)
            .build();

    TriggeringPolicy policy =
        SizeBasedTriggeringPolicy.createPolicy(loggerOptions.getSingleFileSize());

    RollingFileAppender rollingFileAppender =
        RollingFileAppender.newBuilder()
            .setName(loggerOptions.getRollingFileAppenderName())
            .withImmediateFlush(true)
            .withFileName(loggerOptions.getRollingFileSaveName())
            .withFilePattern(loggerOptions.getRollingFileSavePattern())
            .setLayout(patternLayout)
            .withPolicy(policy)
            .withStrategy(strategy)
            .build();

    rollingFileAppender.start();

    configuration.addAppender(rollingFileAppender);

    AppenderRef ref =
        AppenderRef.createAppenderRef(loggerOptions.getRollingFileAppenderName(), null, null);
    AppenderRef[] refs = new AppenderRef[] {ref};

    LoggerConfig loggerConfig =
        LoggerConfig.createLogger(
            false,
            loggerOptions.getRollingLogLevel(),
            loggerOptions.getRollingFileAppenderName(),
            "true",
            refs,
            null,
            configuration,
            null);
    loggerConfig.addAppender(rollingFileAppender, null, null);
    configuration.addLogger(loggerOptions.getRollingFileAppenderName(), loggerConfig);
    loggerContext.updateLoggers(configuration);
    singletonPegasusLogger =
        new PegasusRollingFileLogger(loggerContext, loggerOptions.getRollingFileAppenderName());
    return singletonPegasusLogger;
  }

  // only test for reviewing to show usage.
  public static void main(String[] args) {
    Logger logger = LogWrapper.getRollingFileLogger(LogWrapper.class);
    logger.warn("this is test!");
  }
}

class LoggerOptions {

  private static final boolean DEFAULT_ENABLE_PEGASUS_CUSTOM_LOG = true;

  private static final Level DEFAULT_LEVEL = Level.ALL;

  private static final String DEFAULT_LAYOUT_PATTERN =
      "%d{yyyy-MM-dd HH:mm:ss} %-5p %c{1}:%L - %m%n";

  private static final String DEFAULT_DELETE_AGE = "P7D";
  private static final String DEFAULT_DELETE_FILE_PATH = "log/pegasus";
  private static final String DEFAULT_DELETE_FILE_NAME_PATTERN = "pegasus.client.log*";

  private static final String DEFAULT_MAX_FILE_NUMBER = "5";
  private static final String DEFAULT_MIN_FILE_NUMBER = "1";

  private static final String DEFAULT_SINGLE_FILE_SIZE = "10";

  private static final String DEFAULT_ROLLING_FILE_APPENDER_NAME = "pegasusRolling";
  private static final String DEFAULT_ROLLING_FILE_SAVE_NAME = "log/pegasus/pegasus.client.log";
  private static final String DEFAULT_ROLLING_FILE_SAVE_PATTERN =
      "log/pegasus/pegasus.client.log.%d{yyyy-MM-dd.HH:mm:ss}";

  private Level rollingLogLevel;
  private boolean enablePegasusCustomLog;
  // PatternLayout
  private String layoutPattern;
  // DeleteAction
  private String deleteAge;
  private String deleteFilePath;
  private String deleteFileNamePattern;
  // RolloverStrategy
  private String maxFileNumber;
  private String minFileNumber;
  // SizeBasedTriggeringPolicy
  private String singleFileSize;
  // RollingFileAppender
  private String rollingFileAppenderName;
  private String rollingFileSaveName;
  private String rollingFileSavePattern;

  public LoggerOptions() {
    this.enablePegasusCustomLog = DEFAULT_ENABLE_PEGASUS_CUSTOM_LOG;
    this.rollingLogLevel = DEFAULT_LEVEL;
    this.layoutPattern = DEFAULT_LAYOUT_PATTERN;
    this.deleteAge = DEFAULT_DELETE_AGE;
    this.deleteFilePath = DEFAULT_DELETE_FILE_PATH;
    this.deleteFileNamePattern = DEFAULT_DELETE_FILE_NAME_PATTERN;
    this.maxFileNumber = DEFAULT_MAX_FILE_NUMBER;
    this.minFileNumber = DEFAULT_MIN_FILE_NUMBER;
    this.singleFileSize = DEFAULT_SINGLE_FILE_SIZE;
    this.rollingFileAppenderName = DEFAULT_ROLLING_FILE_APPENDER_NAME;
    this.rollingFileSaveName = DEFAULT_ROLLING_FILE_SAVE_NAME;
    this.rollingFileSavePattern = DEFAULT_ROLLING_FILE_SAVE_PATTERN;
  }

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

class PegasusRollingFileLogger {

  private boolean useXMLConfig;

  public LoggerConfig loggerConfig;
  public LoggerContext loggerContext;
  public Configuration configuration;

  public PegasusRollingFileLogger() {
    this.useXMLConfig = true;
  }

  public PegasusRollingFileLogger(LoggerContext loggerContext, String appenderName) {
    this.useXMLConfig = false;
    this.loggerContext = loggerContext;
    this.configuration = loggerContext.getConfiguration();
    this.loggerConfig = configuration.getLoggerConfig(appenderName);
  }

  public Logger getLogger(String loggerName) {
    // TODO(jiashuo1) if xml has same name appender name, now choose the xml config
    if (useXMLConfig) {
      return LoggerFactory.getLogger(loggerName);
    }

    if (loggerConfig == null || loggerContext == null || configuration == null) {
      throw new NullPointerException(
          "PegasusRollingFileLogger hasn't been initialized successfully ");
    }

    // addLogger is volatile
    configuration.addLogger(loggerName, loggerConfig);
    loggerContext.updateLoggers(configuration);
    return LoggerFactory.getLogger(loggerName);
  }
}
