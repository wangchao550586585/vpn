package org.example.reactor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.entity.ChannelWrapped;
import org.example.protocol.AbstractHandler;
import org.example.protocol.tcp.entity.TCB;
import org.example.protocol.tcp.entity.TcpStatus;
import org.example.protocol.tcp.server.TcpServer;
import org.example.util.UnsafeHelper;
import org.example.util.Utils;
import org.jctools.queues.atomic.MpscChunkedAtomicArrayQueue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.channels.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class SlaveReactor implements Runnable {
    private final Logger LOGGER = LogManager.getLogger(this.getClass());
    private Selector slaveReactor;
    //未包装的selector，仅仅提供注册服务。
    private Selector unwrappedSelector;
    private SelectedSelectionKeySet selectedKeys;
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
    private final static boolean DISABLE_KEY_SET_OPTIMIZATION = false;

    public SlaveReactor() {
        id = UUID.randomUUID().toString();
        this.slaveReactor = optimizationKey();
        this.taskQueue = new MpscChunkedAtomicArrayQueue<Runnable>(1024, 1024 * 2);
    }

    private Selector optimizationKey() {
        final Selector selector;
        try {
            selector = Selector.open();
        } catch (IOException e) {
            LOGGER.error("slaveReactor open Selector fail", e);
            throw new RuntimeException(e);
        }
        if (DISABLE_KEY_SET_OPTIMIZATION) {
            return selector;
        }
        //对select进行优化

        Class<?> selectorImplClass = AccessController.doPrivileged((PrivilegedAction<Class<?>>) () -> {
            try {
                return Class.forName("sun.nio.ch.SelectorImpl", false, Utils.getSystemClassLoader());
            } catch (ClassNotFoundException e) {
                LOGGER.error(" optimizationKey fail ", e);
                return null;
            }
        });
        //说明抛出了异常或者不是jdk原生的类实现，则无法优化，直接返回
        if (Objects.isNull(selectorImplClass) || !selectorImplClass.isAssignableFrom(selector.getClass())) {
            return selector;
        }

        final SelectedSelectionKeySet selectionKeySet = new SelectedSelectionKeySet();
        Object maybeException = AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
            try {
                //执行select后添加到此，表示就绪io的集合
                Field selectedKeysField = selectorImplClass.getDeclaredField("selectedKeys");
                //selectedKeys后返回，表示selectedKeys的副本
                Field publicSelectedKeysField = selectorImplClass.getDeclaredField("publicSelectedKeys");
                //jdk1.8以上则通过unsafe添加
                if (Utils.javaVersion() >= 8 && UnsafeHelper.hasUnsafe()) {
                    //获取字段偏移量
                    long selectedKeysFieldOffset = UnsafeHelper.objectFieldOffset(selectedKeysField);
                    long publicSelectedKeysFieldOffset = UnsafeHelper.objectFieldOffset(publicSelectedKeysField);
                    if (selectedKeysFieldOffset != -1 && publicSelectedKeysFieldOffset != -1) {
                        UnsafeHelper.putObject(selector, selectedKeysFieldOffset, selectionKeySet);
                        UnsafeHelper.putObject(selector, publicSelectedKeysFieldOffset, selectionKeySet);
                        return null;
                    }
                }
                //1.9以下直接反射赋值。
                selectedKeysField.setAccessible(true);
                selectedKeysField.set(selector, selectionKeySet);
                publicSelectedKeysField.setAccessible(true);
                publicSelectedKeysField.set(selector, selectionKeySet);
                return null;
            } catch (Exception e) {
                return e;
            }
        });
        if (maybeException instanceof Exception) {
            Exception e = (Exception) maybeException;
            LOGGER.warn("update selectedKeys fail", e);
        }
        this.selectedKeys = selectionKeySet;
        this.unwrappedSelector = selector;
        //封装select，支持selectedKeys
        return new SelectedSelectionKeySetSelector(selector, selectionKeySet);
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
                    process();
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

    private void process() {
        if (null == selectedKeys) {
            processIO();
        } else {
            processIOOptimized();
        }
    }

    private void processIOOptimized() {
        for (int i = 0; i < selectedKeys.size; i++) {
            SelectionKey key = selectedKeys.keys[i];
            AbstractHandler handler = (AbstractHandler) key.attachment();
            selectedKeys.keys[i] = null;
            handler(key,handler);
        }
    }

    private void processIO() {
        Set<SelectionKey> selectionKeys = slaveReactor.selectedKeys();
        Iterator<SelectionKey> iterator = selectionKeys.iterator();
        while (iterator.hasNext()) {
            SelectionKey key = iterator.next();
            AbstractHandler handler = (AbstractHandler) key.attachment();
            iterator.remove();
            handler(key,handler);
        }
    }

    private void handler(SelectionKey key,AbstractHandler handler) {
        //主动调用cancel，close chanle，close selector会key失效
        if (!key.isValid()) {
            String uuid = handler.uuid();
            LOGGER.error("key was invalid {}", uuid);
            handler.closeChildChannel();//这里会取消key
            return;
        }
        try {
            int readyOps = key.readyOps();
            if ((readyOps & SelectionKey.OP_READ) != 0) {
                handler.run();
            }
            if ((readyOps & SelectionKey.OP_WRITE) != 0) {
                // TODO: 2023/6/9
            }
        } catch (CancelledKeyException ignored) {
            handler.closeChildChannel();
        }
    }

    public void register(SocketChannel childChannel) {
        taskQueue.offer(() -> {
            try {
                childChannel.configureBlocking(false);
                //select和register会造成阻塞
                SelectionKey selectionKey = childChannel.register(unwrappedSelector, 0);
                ChannelWrapped channelWrapped = ChannelWrapped.builder().key(selectionKey).channel(childChannel);
                //AbstractHandler handler = new AuthHandler(channelWrapped);
                //AbstractHandler handler = new HttpHandler(channelWrapped);
                //WebsocketHandler handler = new WebsocketHandler(channelWrapped);
                TcpServer handler = new TcpServer(channelWrapped,new TCB().SourcePort(8090).IRS(0).TcpStatus(TcpStatus.LISTEN));
                selectionKey.attach(handler);
                selectionKey.interestOps(SelectionKey.OP_READ);
                LOGGER.debug("slaveReactor register childChannel success {}", handler.uuid());
            } catch (IOException e) {
                LOGGER.error("slaveReactor register childChannel fail ", e);
                throw new RuntimeException(e);
            }
        });
        //初次进来会启动reactor
        start();
    }
}
