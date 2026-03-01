package com.lab2;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.List;
import java.util.Collections;

public class OrderBook {
    private String symbol;
    // Bids: Sorted High to Low (Descending) - Best Bid is Highest Price
    private ConcurrentSkipListMap<Double, List<Order>> bids = new ConcurrentSkipListMap<>(Collections.reverseOrder());
    // Asks: Sorted Low to High (Ascending) - Best Ask is Lowest Price
    private ConcurrentSkipListMap<Double, List<Order>> asks =new ConcurrentSkipListMap<>();
    // Methods to add/remove orders will be added in Lab 7
}
