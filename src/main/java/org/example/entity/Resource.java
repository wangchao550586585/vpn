package org.example.entity;

import java.io.IOException;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class Resource {
    SocketChannel remoteClient;
    Selector remoteSelector;

    SocketChannel childChannel;

    private Resource self() {
        return this;
    }

    public SocketChannel remoteClient() {
        return remoteClient;
    }

    public Resource remoteClient(SocketChannel remoteClient) {
        this.remoteClient = remoteClient;
        return self();
    }

    public SocketChannel getChildChannel() {
        return childChannel;
    }

    public Resource childChannel(SocketChannel childChannel) {
        this.childChannel = childChannel;
        return self();
    }


    public Resource remoteSelector(Selector remoteSelector) {
        this.remoteSelector = remoteSelector;
        return self();
    }

    public void closeRemote() throws IOException {
        remoteClient.close();
        //close调用会调用wakeup
        remoteSelector.close();

    }

}
