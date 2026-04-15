package com.lab2;

import quickfix.Log;
import quickfix.LogFactory;
import quickfix.SessionID;

/**
 * A QuickFIX/J LogFactory implementation that suppresses all FIX message logging.
 */
public class NoOpLogFactory implements LogFactory {
    @Override
    public Log create(SessionID sessionID) {
        return new NoOpLog();
    }

    private static class NoOpLog implements Log {
        @Override
        public void clear() {
            // no-op
        }

        @Override
        public void onIncoming(String message) {
            // no-op
        }

        @Override
        public void onOutgoing(String message) {
            // no-op
        }

        @Override
        public void onEvent(String message) {
            // no-op
        }

        @Override
        public void onErrorEvent(String message) {
            // no-op
        }
    }
}
