package com.pllapallpal;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SelectorThread implements Runnable {

    private SelectionKey selectionKey;
    private Selector selector;
    private List<SocketChannel> socketChannelList;
    private final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();

    @Override
    public void run() {
        System.out.println("Server start");
        while (true) {
            try {
                selector.select();
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keys = selectedKeys.iterator();
                while (keys.hasNext()) {
                    SelectionKey selectionKey = keys.next();
                    keys.remove();
                    if (selectionKey.isAcceptable()) {
                        // accept
                        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) selectionKey.channel();
                        SocketChannel socketChannel = serverSocketChannel.accept();

                        if (Objects.isNull(socketChannel)) {
                            return;
                        }

                        socketChannel.configureBlocking(false);
                        socketChannel.register(selector, SelectionKey.OP_READ);
                        socketChannelList.add(socketChannel);
                        UserInfoMap.getInstance().getUserMap().put(socketChannel, new UserInfo());
                    } else if (selectionKey.isReadable()) {
                        SocketChannel clientSocketChannel = (SocketChannel) selectionKey.channel();
                        ByteBuffer receivedByteBuffer = readFrom(clientSocketChannel);
                        int protocol = receivedByteBuffer.getInt();
                        switch (protocol) {
                            // LOGIN
                            case 100: {
                                String username = decoder.decode(receivedByteBuffer).toString();
                                System.out.println(username); // DEBUG
                                UserInfoMap.getInstance().getUserMap().get(clientSocketChannel).setUsername(username);

                                // send LIST protocol
                                ByteBuffer writeByteBuffer = ByteBuffer.allocate(1024);
                                writeByteBuffer.putInt(102);
                                writeByteBuffer.putInt(UserInfoMap.getInstance().getUserMap().size());
                                Iterator<Map.Entry<SocketChannel, UserInfo>> iterator = UserInfoMap.getInstance().getUserMap().entrySet().iterator();
                                while (iterator.hasNext()) {
                                    String listItem = iterator.next().getValue().getUsername() + "\t";
                                    writeByteBuffer.put(listItem.getBytes());
                                }
                                writeByteBuffer.flip();
                                write(clientSocketChannel, writeByteBuffer);
                                break;
                            }
                            case 101: {
                                break;
                            }
                            case 102: {
                                break;
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * @return ByteBuffer which is read from socketChannel
     */
    private ByteBuffer readFrom(SocketChannel socketChannel) {
        System.out.println("readFrom");
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        try {
            System.out.println("read 전");
            int numRead = socketChannel.read(byteBuffer);
            System.out.println("numRead: " + numRead);
        } catch (IOException e) {
            try {
                System.out.println("Client " + UserInfoMap.getInstance().getUserMap().get(socketChannel).getUsername() + " has left the server.");
                socketChannel.close();
                socketChannelList.remove(socketChannel);
                UserInfoMap.getInstance().getUserMap().remove(socketChannel);
                byteBuffer.putInt(101);
                byteBuffer.flip();
                return byteBuffer;
            } catch (IOException ex) {
                e.printStackTrace();
            }
        }

        byteBuffer.flip();
        return byteBuffer;
    }

    /**
     * @param byteBuffer Data that will be broadcast
     */
    private void broadcast(ByteBuffer byteBuffer) {
        for (SocketChannel socketChannelBroadcast : socketChannelList) {
            try {
                write(socketChannelBroadcast, byteBuffer);
                System.out.println("Broadcast " + decoder.decode(byteBuffer));
            } catch (IOException e) {
                try {
                    socketChannelBroadcast.close();
                    socketChannelList.remove(socketChannelBroadcast);
                } catch (IOException ex) {
                    e.printStackTrace();
                }
            }
            byteBuffer.rewind();
        }
    }

    /**
     * @param socketChannel SocketChannel that data will be written
     * @param byteBuffer    Buffer that will be sent to the SocketChannel
     */
    private void write(SocketChannel socketChannel, ByteBuffer byteBuffer) {
        try {
            socketChannel.write(byteBuffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setSelectionKey(SelectionKey selectionKey) {
        this.selectionKey = selectionKey;
    }

    public void setSelector(Selector selector) {
        this.selector = selector;
    }

    public void setSocketChannelList(List<SocketChannel> socketChannelList) {
        this.socketChannelList = socketChannelList;
    }
}