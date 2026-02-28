import React from 'react';
import useWebSocket from '../hooks/useWebSocket';
import './OrderGrid.css';

const OrderGrid = () => {
  const { messages: orders, connectionStatus, error } = useWebSocket('ws://localhost:8080');

  const getStatusColor = () => {
    switch (connectionStatus) {
      case 'connected':
        return '#10b981';
      case 'connecting':
        return '#f59e0b';
      case 'disconnected':
      case 'error':
        return '#ef4444';
      default:
        return '#6b7280';
    }
  };

  const formatSide = (side) => {
    return side === '1' || side === 1 ? 'BUY' : 'SELL';
  };

  const getSideColor = (side) => {
    return side === '1' || side === 1 ? '#10b981' : '#ef4444';
  };

  const formatPrice = (price) => {
    return typeof price === 'number' ? price.toFixed(2) : parseFloat(price).toFixed(2);
  };

  const formatQuantity = (qty) => {
    return typeof qty === 'number' ? qty.toFixed(0) : parseFloat(qty).toFixed(0);
  };

  return (
    <div className="order-grid-container">
      <div className="header">
        <div className="header-content">
          <h1 className="title">Live Order Blotter</h1>
          <div className="status-badge">
            <div 
              className="status-indicator" 
              style={{ backgroundColor: getStatusColor() }}
            />
            <span className="status-text">
              {connectionStatus.charAt(0).toUpperCase() + connectionStatus.slice(1)}
            </span>
          </div>
        </div>
        {error && (
          <div className="error-banner">
            <span className="error-icon">⚠️</span>
            {error}
          </div>
        )}
        <div className="stats">
          <div className="stat-item">
            <span className="stat-label">Total Orders</span>
            <span className="stat-value">{orders.length}</span>
          </div>
        </div>
      </div>

      <div className="table-container">
        {orders.length === 0 ? (
          <div className="empty-state">
            <div className="empty-icon">📊</div>
            <h3>No Orders Yet</h3>
            <p>Waiting for incoming orders from the FIX server...</p>
          </div>
        ) : (
          <table className="order-table">
            <thead>
              <tr>
                <th>Order ID</th>
                <th>Symbol</th>
                <th>Side</th>
                <th>Quantity</th>
                <th>Price</th>
              </tr>
            </thead>
            <tbody>
              {orders.map((order, index) => (
                <tr key={`${order.clOrdID}-${index}`} className="order-row">
                  <td className="order-id">{order.clOrdID}</td>
                  <td className="symbol">{order.symbol}</td>
                  <td className="side">
                    <span 
                      className="side-badge"
                      style={{ 
                        backgroundColor: getSideColor(order.side) + '20',
                        color: getSideColor(order.side)
                      }}
                    >
                      {formatSide(order.side)}
                    </span>
                  </td>
                  <td className="quantity">{formatQuantity(order.quantity)}</td>
                  <td className="price">${formatPrice(order.price)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
};

export default OrderGrid;
