package org.example;

import org.example.handler.AuthHandler;
import org.example.handler.ConnectionHandler;
import org.example.handler.DeliverHandler;
import org.jctools.queues.atomic.MpscChunkedAtomicArrayQueue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class SlaveReactor implements Runnable {
    private Selector slaveReactor;
    private String id;
    private Queue<Runnable> taskQueue;
    private volatile int state = ST_NOT_STARTED;
    private static final int ST_NOT_STARTED = 1;
    private static final int ST_STARTED = 2;
    private static final int ST_SHUTDOWN = 4;
    private static final AtomicIntegerFieldUpdater<SlaveReactor> STATE_UPDATER = AtomicIntegerFieldUpdater.newUpdater(SlaveReactor.class, "state");
    private static final int AWAKE = -1;
    private static final int SYNC = 1;
    private AtomicInteger wakeUpdater = new AtomicInteger(AWAKE);

    public SlaveReactor() {
        try {
            id = UUID.randomUUID().toString();
            this.slaveReactor = Selector.open();
            this.taskQueue = new MpscChunkedAtomicArrayQueue<Runnable>(1024, 1024 * 2);
        } catch (IOException e) {
            System.out.println("打开Selector 失败" + e.getMessage());
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
                    System.out.println("启动SlaveReactor失败");
                    e.printStackTrace();
                } finally {
                    if (!success) {
                        STATE_UPDATER.compareAndSet(this, ST_STARTED, ST_NOT_STARTED);
                    }
                }
            }
        } else {
            //设置状态为唤醒，如果上一个select状态为唤醒态则当过这次唤醒。
            if (wakeUpdater.getAndSet(AWAKE) != AWAKE) {
                System.out.println(id + " 唤醒成功");
                slaveReactor.wakeup();
            } else {
                System.out.println(id + " 唤醒失败");
            }
        }
    }

    @Override
    public void run() {
        ByteBuffer writeBuffer = ByteBuffer.allocate(4096 * 5);
        while (true) {
            try {
                //醒来可能是有io就绪任务，也可能是普通任务。优先执行io就绪任务。
                //1:有io就绪，处理io就绪。没有task。
                //1:有io就绪，处理io就绪。有task，处理task。
                //2：没io就绪，没有task。。
                //2：没io就绪，有task，处理task。
                int n = -1;
                if (taskQueue.isEmpty()) {
                    System.out.println(id + " 切换sync");
                    wakeUpdater.set(SYNC);
                    n = slaveReactor.select();
                } else {
                    n = slaveReactor.selectNow();
                }
                //修改为唤醒状态
                System.out.println(id + " 切换AWAKE");
                //在这里不及时的修改为wake，最多造成多一次wakeup。对程序影响不大。
                wakeUpdater.lazySet(AWAKE);
                if (n > 0) {
                    processIO(writeBuffer);
                }
                if (!taskQueue.isEmpty()) {
                    Runnable task = null;
                    while (null != (task = taskQueue.poll())) {
                        task.run();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void processIO(ByteBuffer writeBuffer) throws IOException {
        Set<SelectionKey> selectionKeys = slaveReactor.selectedKeys();
        Iterator<SelectionKey> iterator = selectionKeys.iterator();
        while (iterator.hasNext()) {
            SelectionKey key = iterator.next();
            if (!key.isValid()) {
                Attr attr = (Attr) key.attachment();
                String uuid = attr.getUuid();
                System.out.println(uuid);
                key.cancel();
                continue;
            }
            if (key.isReadable()) {
                SocketChannel childChannel = (SocketChannel) key.channel();
                Attr attr = (Attr) key.attachment();
                String uuid = attr.getUuid();
                Status status = attr.getStatus();
                //处理粘包问题
                switch (status) {
                    case AUTH:
                        new AuthHandler().auth(writeBuffer, key, childChannel, attr, uuid);
                        break;
                    case CONNECTION:
                        new ConnectionHandler().connection(writeBuffer, key, childChannel, attr, uuid);
                        break;
                    case DELIVER:
                        new DeliverHandler().deliver(key, childChannel, uuid);
                }
            }
            iterator.remove();
        }
    }

    public void register(SocketChannel childChannel) throws IOException {
        taskQueue.offer(() -> {
            try {
                childChannel.configureBlocking(false);
                //select和register会造成阻塞
                SelectionKey selectionKey = childChannel.register(slaveReactor, 0);
                String uuid = UUID.randomUUID().toString().replaceAll("-", "");
                selectionKey.attach(new Attr().status(Status.AUTH).uuid(uuid));
                selectionKey.interestOps(SelectionKey.OP_READ);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        //初次进来会启动reactor
        start();
    }
}
