package org.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.handler.AbstractHandler;
import org.example.handler.AuthHandler;
import org.jctools.queues.atomic.MpscChunkedAtomicArrayQueue;

import java.io.IOException;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class SlaveReactor implements Runnable {
    private final Logger LOGGER = LogManager.getLogger(this.getClass());
    private Selector slaveReactor;
    private String id;
    private Queue<Runnable> taskQueue;
    private volatile int state = ST_NOT_STARTED;
    private static final int ST_NOT_STARTED = 1;
    private static final int ST_STARTED = 2;
    private static final int ST_SHUTDOWN = 4;
    private static final AtomicIntegerFieldUpdater<SlaveReactor> STATE_UPDATER = AtomicIntegerFieldUpdater.newUpdater(SlaveReactor.class, "state");
    private static final int AWAKE = -1;
    private static final int SELECT = 1;
    private AtomicInteger wakeUpdater = new AtomicInteger(AWAKE);

    public SlaveReactor() {
        try {
            id = UUID.randomUUID().toString();
            this.slaveReactor = Selector.open();
            this.taskQueue = new MpscChunkedAtomicArrayQueue<Runnable>(1024, 1024 * 2);
        } catch (IOException e) {
            LOGGER.error("slaveReactor open Selector fail", e);
            throw new RuntimeException(e);
        }
    }

    public void start() {
        if (state == ST_NOT_STARTED) {
            if (STATE_UPDATER.compareAndSet(this, ST_NOT_STARTED, ST_STARTED)) {
                boolean success = false;
                try {
                    new Thread(this).start();
                    success = true;
                } catch (Exception e) {
                    LOGGER.error("slaveReactor start fail", e);
                    throw new RuntimeException(e);
                } finally {
                    if (!success) {
                        STATE_UPDATER.compareAndSet(this, ST_STARTED, ST_NOT_STARTED);
                    }
                }
            }
        } else {
            //设置状态为唤醒，如果上一个select状态为唤醒态则当过这次唤醒。
            if (wakeUpdater.getAndSet(AWAKE) != AWAKE) {
                LOGGER.debug("AWAKE wakeup success {}", id);
                slaveReactor.wakeup();
            } else {
                LOGGER.debug("AWAKE wakeup fail {}", id);
            }
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                //醒来可能是有io就绪任务，也可能是普通任务。优先执行io就绪任务。
                //1:有io就绪，处理io就绪。没有task。
                //1:有io就绪，处理io就绪。有task，处理task。
                //2：没io就绪，没有task。。
                //2：没io就绪，有task，处理task。
                int n = -1;
                if (taskQueue.isEmpty()) {
                    LOGGER.debug("AWAKE swap SELECT {}", id);
                    wakeUpdater.set(SELECT);
                    n = slaveReactor.select();
                } else {
                    n = slaveReactor.selectNow();
                }
                //修改为唤醒状态
                LOGGER.debug("AWAKE swap AWAKE {}", id);
                //在这里不及时的修改为wake，最多造成多一次wakeup。对程序影响不大。
                wakeUpdater.lazySet(AWAKE);
                if (n > 0) {
                    processIO();
                }
                if (!taskQueue.isEmpty()) {
                    Runnable task;
                    while (null != (task = taskQueue.poll())) {
                        task.run();
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("slaveReactor select fail ", e);
            throw new RuntimeException(e);
        }
    }

    private void processIO() {
        Set<SelectionKey> selectionKeys = slaveReactor.selectedKeys();
        Iterator<SelectionKey> iterator = selectionKeys.iterator();
        while (iterator.hasNext()) {
            SelectionKey key = iterator.next();
            if (!key.isValid()) {
                AbstractHandler handler = (AbstractHandler) key.attachment();
                String uuid = handler.getAttr().getUuid();
                LOGGER.error("key was invalid {}", uuid);
                key.cancel();
                continue;
            }
            if (key.isReadable()) {
                Runnable runnable = (Runnable) key.attachment();
                runnable.run();
            }
            iterator.remove();
        }
    }

    public void register(SocketChannel childChannel) {
        taskQueue.offer(() -> {
            try {
                childChannel.configureBlocking(false);
                //select和register会造成阻塞
                SelectionKey selectionKey = childChannel.register(slaveReactor, 0);
                String uuid = UUID.randomUUID().toString().replaceAll("-", "");
                Attr attr = new Attr().uuid(uuid);
                AuthHandler authHandler = new AuthHandler(attr, selectionKey, childChannel);
                selectionKey.attach(authHandler);
                selectionKey.interestOps(SelectionKey.OP_READ);
                LOGGER.info("slaveReactor register childChannel success {}", uuid);
            } catch (IOException e) {
                LOGGER.error("slaveReactor register childChannel fail ", e);
                throw new RuntimeException(e);
            }
        });
        //初次进来会启动reactor
        start();
    }
}
