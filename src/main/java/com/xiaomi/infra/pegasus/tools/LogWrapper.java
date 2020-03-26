// Copyright (c) 2017, Xiaomi, Inc.  All rights reserved.
// This source code is licensed under the Apache License Version 2.0, which
// can be found in the LICENSE file in the root directory of this source tree.
package com.xiaomi.infra.pegasus.tools;

import java.nio.charset.Charset;
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
// 3. `LoggerOptions` expose `enablePegasusCustomLog` and `rollingFileSaveName` to change for user
// by
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

  static class PegasusRollingFileLogger {

    public LoggerConfig loggerConfig;
    public LoggerContext loggerContext;
    public Configuration configuration;

    public PegasusRollingFileLogger(LoggerContext loggerContext, String appenderName) {
      this.loggerContext = loggerContext;
      this.configuration = loggerContext.getConfiguration();
      this.loggerConfig = configuration.getLoggerConfig(appenderName);
    }

    public Logger getLogger(String loggerName) {
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
}
