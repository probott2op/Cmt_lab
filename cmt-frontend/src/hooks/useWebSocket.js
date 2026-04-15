import { useEffect, useState, useRef, useCallback } from 'react';

/**
 * Custom hook to manage WebSocket connection for order streaming.
 * Handles ORDER, TRADE, and ORDER_UPDATE events with auto-reconnect.
 * 
 * @param {string} url - WebSocket server URL
 * @returns {Object} - { messages, trades, connectionStatus, error }
 */
const useWebSocket = (url) => {
  const [messages, setMessages] = useState([]);
  const [trades, setTrades] = useState([]);
  const [connectionStatus, setConnectionStatus] = useState('connecting');
  const [error, setError] = useState(null);
  const socketRef = useRef(null);
  const reconnectTimeoutRef = useRef(null);
  const reconnectAttemptsRef = useRef(0);
  const maxReconnectAttempts = 10;

  const connect = useCallback(() => {
    try {
      const socket = new WebSocket(url);
      socketRef.current = socket;

      socket.onopen = () => {
        console.log('✅ Connected to Order Service');
        setConnectionStatus('connected');
        setError(null);
        reconnectAttemptsRef.current = 0;
      };

      socket.onmessage = (event) => {
        try {
          const message = JSON.parse(event.data);

          if (message.type === 'ORDER') {
            setMessages((prev) => {
              // Deduplicate by orderId (recovered orders may already exist)
              const exists = prev.some(o => o.orderId === message.data.orderId);
              if (exists) return prev;
              return [message.data, ...prev];
            });
          } else if (message.type === 'TRADE') {
            setTrades((prev) => [message.data, ...prev]);
          } else if (message.type === 'ORDER_UPDATE') {
            // Update existing order's fill status and remaining qty in-place
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
  }, [url]);

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

  return { messages, trades, connectionStatus, error };
};

export default useWebSocket;
