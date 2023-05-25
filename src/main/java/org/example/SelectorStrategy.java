package org.example;

import java.io.IOException;
import java.nio.channels.Selector;
import java.util.concurrent.atomic.AtomicInteger;

public class SelectorStrategy {
    private SlaveReactor[] slaveReactor;
    private AtomicInteger index;

    public SelectorStrategy(int size) {
        slaveReactor = new SlaveReactor[1];
        for (int i = 0; i < slaveReactor.length; i++) {
            slaveReactor[i] = new SlaveReactor();
        }
        index = new AtomicInteger();
    }

    public SlaveReactor getSlaveReactor() {
        int andIncrement = index.getAndIncrement();
        int i = andIncrement % (slaveReactor.length);
        return slaveReactor[i];
    }
}
