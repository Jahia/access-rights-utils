package org.jahia.modules.accessrightsutils;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class ScriptLogger {

    private final Logger logger;
    private final List<String> buffer;

    public ScriptLogger(Logger logger) {
        this.logger = logger;
        buffer = new ArrayList<>();
    }

    public List<String> getBuffer() {
        return buffer;
    }

    public void clear() {
        buffer.clear();
    }

    public void info(String msg) {
        info(msg, null);
    }

    public void info(String msg, Throwable t) {
        if (!logger.isInfoEnabled()) return;
        buffer.add(msg);
        if (t == null) {
            logger.info(msg);
        } else {
            logger.info(msg, t);
        }
    }

    public void error(String msg) {
        error(msg, null);
    }

    public void error(String msg, Throwable t) {
        if (!logger.isErrorEnabled()) return;
        buffer.add(msg);
        if (t == null) {
            logger.error(msg);
        } else {
            logger.error(msg, t);
        }
    }

    public void debug(String msg) {
        debug(msg, null);
    }

    public void debug(String msg, Throwable t) {
        if (!logger.isDebugEnabled()) return;
        buffer.add(msg);
        if (t == null) {
            logger.debug(msg);
        } else {
            logger.debug(msg, t);
        }
    }

    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }
}
