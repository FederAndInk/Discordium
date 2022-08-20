package ru.aiefu.discordium;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiscordiumLogger {
  private static Logger logger = LoggerFactory.getLogger("discordium");

  private String prefix() {
    return "[" + getName() + "] ";
  }

  public void debug(String arg0) {
    logger.debug(prefix() + arg0);
  }

  public void debug(String arg0, Object... arg1) {
    logger.debug(prefix() + arg0, arg1);
  }

  public void error(String arg0) {
    logger.error(prefix() + arg0);
  }

  public void error(String arg0, Object... arg1) {
    logger.error(prefix() + arg0, arg1);
  }

  public String getName() {
    return logger.getName();
  }

  public void info(String arg0) {
    logger.info(prefix() + arg0);
  }

  public void info(String arg0, Object... arg1) {
    logger.info(prefix() + arg0, arg1);
  }

  public void trace(String arg0) {
    logger.trace(prefix() + arg0);
  }

  public void trace(String arg0, Object... arg1) {
    logger.trace(prefix() + arg0, arg1);
  }

  public void warn(String arg0) {
    logger.warn(prefix() + arg0);
  }

  public void warn(String arg0, Object... arg1) {
    logger.warn(prefix() + arg0, arg1);
  }
}
