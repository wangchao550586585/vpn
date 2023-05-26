package org.example.entity;

import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class Resource {
    SocketChannel remoteClient;
    Selector selector;

    //
    SocketChannel childChannel;
    SelectionKey childSKey;

    private Resource self() {
        return this;
    }

    public SocketChannel getRemoteClient() {
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

    public Selector getSelector() {
        return selector;
    }

    public Resource selector(Selector selector) {
        this.selector = selector;
        return self();
    }

    public SelectionKey childSKey() {
        return childSKey;
    }

    public Resource childSKey(SelectionKey childSKey) {
        this.childSKey = childSKey;
        return self();
    }
}
