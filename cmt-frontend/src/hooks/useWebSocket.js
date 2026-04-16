import { useEffect, useState, useRef, useCallback } from 'react';

/**
 * Custom hook to manage WebSocket connection for order streaming.
 * Handles ORDER, TRADE, ORDER_UPDATE, CANCEL_RESPONSE, AUDIT_TRAIL events.
 * Features: cancel requests, retry queue on disconnect, audit trail on-demand.
 * 
 * @param {string} url - WebSocket server URL
 * @returns {Object} - Full state + actions for the dashboard
 */
const useWebSocket = (url) => {
  const [messages, setMessages] = useState([]);
  const [trades, setTrades] = useState([]);
  const [auditTrails, setAuditTrails] = useState({}); // Map<orderId, AuditEvent[]>
  const [notifications, setNotifications] = useState([]); // Transient UI notifications
  const [connectionStatus, setConnectionStatus] = useState('connecting');
  const [error, setError] = useState(null);
  const socketRef = useRef(null);
  const reconnectTimeoutRef = useRef(null);
  const reconnectAttemptsRef = useRef(0);
  const maxReconnectAttempts = 10;
  // Retry queue: cancel requests queued while disconnected
  const cancelRetryQueueRef = useRef([]);
  // Notification ID counter
  const notifIdRef = useRef(0);

  /**
   * Add a transient notification that auto-dismisses after a timeout.
   */
  const addNotification = useCallback((type, message, duration = 5000) => {
    const id = ++notifIdRef.current;
    const notif = { id, type, message, timestamp: Date.now() };
    setNotifications(prev => [notif, ...prev]);
    // Auto-dismiss
    setTimeout(() => {
      setNotifications(prev => prev.filter(n => n.id !== id));
    }, duration);
    return id;
  }, []);

  /**
   * Dismiss a notification manually.
   */
  const dismissNotification = useCallback((id) => {
    setNotifications(prev => prev.filter(n => n.id !== id));
  }, []);

  /**
   * Send a JSON message via WebSocket.
   * Returns true if sent, false if queued.
   */
  const sendMessage = useCallback((msg) => {
    const socket = socketRef.current;
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify(msg));
      return true;
    }
    return false;
  }, []);

  /**
   * Send a cancel request for an order.
   * If disconnected, queues the request and replays on reconnect.
   */
  const cancelOrder = useCallback((orderId) => {
    const msg = { type: 'CANCEL_REQUEST', data: { orderId } };

    // Optimistically set CANCEL_PENDING on the frontend
    setMessages(prev =>
      prev.map(order =>
        order.orderId === orderId
          ? { ...order, status: 'CANCEL_PENDING', _prevStatus: order.status }
          : order
      )
    );

    const sent = sendMessage(msg);
    if (!sent) {
      // Queue for retry
      cancelRetryQueueRef.current.push(msg);
      addNotification('warning', `Cancel queued — will retry when reconnected (Order ${orderId})`);
    }
  }, [sendMessage, addNotification]);

  /**
   * Request audit trail for an order (on-demand).
   */
  const requestAuditTrail = useCallback((orderId) => {
    const msg = { type: 'AUDIT_REQUEST', data: { orderId } };
    sendMessage(msg);
  }, [sendMessage]);

  /**
   * Flush the cancel retry queue after reconnecting.
   */
  const flushRetryQueue = useCallback(() => {
    const queue = cancelRetryQueueRef.current;
    if (queue.length > 0) {
      console.log(`Flushing ${queue.length} queued cancel requests`);
      queue.forEach(msg => sendMessage(msg));
      addNotification('info', `Replayed ${queue.length} queued cancel request(s)`);
      cancelRetryQueueRef.current = [];
    }
  }, [sendMessage, addNotification]);

  const connect = useCallback(() => {
    try {
      const socket = new WebSocket(url);
      socketRef.current = socket;

      socket.onopen = () => {
        console.log('✅ Connected to Order Service');
        setConnectionStatus('connected');
        setError(null);
        reconnectAttemptsRef.current = 0;
        // Replay any queued cancel requests
        flushRetryQueue();
      };

      socket.onmessage = (event) => {
        try {
          const message = JSON.parse(event.data);

          switch (message.type) {
            case 'ORDER': {
              setMessages((prev) => {
                const exists = prev.some(o => o.orderId === message.data.orderId);
                if (exists) return prev;
                return [message.data, ...prev];
              });
              break;
            }

            case 'TRADE': {
              setTrades((prev) => [message.data, ...prev]);
              break;
            }

            case 'ORDER_UPDATE': {
              setMessages((prev) =>
                prev.map((order) =>
                  order.orderId === message.data.orderId
                    ? {
                        ...order,
                        status: message.data.status,
                        quantity: message.data.remainingQty,
                        originalQuantity: message.data.originalQuantity,
                      }
                    : order
                )
              );
              break;
            }

            case 'CANCEL_RESPONSE': {
              const { orderId, status, reason } = message.data;
              if (status === 'CANCEL_REJECTED') {
                // Revert optimistic CANCEL_PENDING to previous status
                setMessages(prev =>
                  prev.map(order =>
                    order.orderId === orderId && order.status === 'CANCEL_PENDING'
                      ? { ...order, status: order._prevStatus || 'NEW', _cancelRejected: true, _cancelReason: reason }
                      : order
                  )
                );
                addNotification('error', `Cancel rejected: ${reason} (Order ${orderId})`);
              } else {
                // CANCELLED or PARTIALLY_CANCELLED — update already comes via ORDER_UPDATE
                addNotification('success', `Order ${orderId} cancelled successfully`);
              }
              break;
            }

            case 'AUDIT_TRAIL': {
              const { orderId, events } = message.data;
              setAuditTrails(prev => ({
                ...prev,
                [orderId]: events
              }));
              break;
            }

            case 'AUDIT_EVENT': {
              const event = message.data;
              setAuditTrails(prev => {
                const existing = prev[event.orderId];
                if (existing) {
                  return { ...prev, [event.orderId]: [...existing, event] };
                }
                // Don't add if we haven't loaded the trail yet (on-demand only)
                return prev;
              });
              break;
            }

            default:
              console.warn('Unknown message type:', message.type);
          }
        } catch (err) {
          console.error('Failed to parse message:', err);
          setError('Failed to parse incoming data');
        }
      };

      socket.onerror = () => {
        setError('Connection error occurred');
        setConnectionStatus('error');
      };

      socket.onclose = (event) => {
        setConnectionStatus('disconnected');

        if (!event.wasClean && reconnectAttemptsRef.current < maxReconnectAttempts) {
          const delay = Math.min(1000 * Math.pow(2, reconnectAttemptsRef.current), 30000);
          reconnectAttemptsRef.current += 1;
          setConnectionStatus('reconnecting');
          reconnectTimeoutRef.current = setTimeout(connect, delay);
        }
      };
    } catch (err) {
      setError('Failed to establish connection');
      setConnectionStatus('error');
    }
  }, [url, flushRetryQueue, addNotification]);

  useEffect(() => {
    connect();

    return () => {
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
      }
      if (socketRef.current?.readyState === WebSocket.OPEN) {
        socketRef.current.close();
      }
    };
  }, [connect]);

  return {
    messages,
    trades,
    auditTrails,
    notifications,
    connectionStatus,
    error,
    cancelOrder,
    requestAuditTrail,
    dismissNotification,
    retryQueueSize: cancelRetryQueueRef.current.length,
  };
};

export default useWebSocket;
