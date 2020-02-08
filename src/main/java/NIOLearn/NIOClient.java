package NIOLearn;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

public class NIOClient {
    // 空闲等待最长时间
    private static final int MAX_IDLE_COUNT = 60;
    // 定义最大缓存区大小
    private static final int BUFFER_SIZE = 1024 * 8;
    // 是否关闭客户端的标志
    private boolean isClosed = false;
    // 通道管理器
    private Selector selector;
    // 与服务端交互的socket通道
    private SocketChannel socketChannel;
    // 分配的读缓存
    private ByteBuffer readBuffer;
    // 读处理器
    private ReadHandler readHandler;
    // 待写队列
    private LinkedList<ByteBuffer> bufferQueue;

    // 当前空闲计数
    private int idleCount;

    private String serverIP;
    private int port;
    private String name;

    public String getName() {
        return this.name;
    }

    public NIOClient() {}

    public NIOClient(String serverIP, int port) {
        this.serverIP = serverIP;
        this.port = port;
    }

    public NIOClient(String name, String serverIP, int port) {
        this.name = name;
        this.serverIP = serverIP;
        this.port = port;
    }

    // 完成初始化工作
    private void init() {

        idleCount = 0;

        // 初始化读缓存
        readBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        // 初始化待写队列
        bufferQueue = new LinkedList<ByteBuffer>();

        SocketChannel socketChannel = null;
        try {
            socketChannel = SocketChannel.open();
            // 需要设置为非阻塞模式才能进行一系列操作
            socketChannel.configureBlocking(false);
            socketChannel.connect(new InetSocketAddress(serverIP, port));
            selector = Selector.open();
            socketChannel.register(selector, SelectionKey.OP_CONNECT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 启动
    public void startup() {
        // 完成初始化
        init();

        new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    while (!isClosed) {
                        int nKey = selector.select(1000);	// 结合MAX_IDLE_COUNT 等价于 MAX_IDLE_COUNT(s)空闲时间检测
                        if (nKey > 0) {
                            idleCount = 0;
                            Set<SelectionKey> keySet = selector.selectedKeys();
                            Iterator<SelectionKey> iterator = keySet.iterator();
                            while (iterator.hasNext()) {
                                SelectionKey key = iterator.next();
                                iterator.remove();
                                if (key.isConnectable()) {
                                    // 连接事件
                                    finishedConnection(key);
                                } else if (key.isReadable()) {
                                    // 读事件
                                    readFromChanel(key);
                                } else if (key.isWritable()) {
                                    // 写事件
                                    writeToChannel(key);
                                }
                            }
                        } else {
                            idleCount++;
                            if(idleCount >= MAX_IDLE_COUNT) {
                                // 空闲超时,断开与客户端的连接
                                close();
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void finishedConnection(SelectionKey key) {
        SocketChannel socketChannel = (SocketChannel) key
                .channel();
        if (socketChannel.isConnectionPending()) {
            try {
                socketChannel.finishConnect();
                socketChannel.configureBlocking(false);
                // 注册读权限
                socketChannel.register(selector,
                        SelectionKey.OP_READ);
                this.socketChannel = socketChannel;
                readHandler = new ClientReadHandler(this, this.socketChannel);
                System.out.println(Report.reportCurrentTime() + this.name + " Connect to Server");

                final String msg = "本地服务器你好!" + "我是" + this.getName();
                final String msg1 = "我是" + this.getName() + " 本地服务器你好!";
                send(msg);
                send(msg1);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void readFromChanel(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        // 清空读缓存
        readBuffer.clear();
        try {
            int count = channel.read(readBuffer);
//			System.out.println("count = " + count);
            if(count > 0) {
                byte[] data = new byte[count];
                System.arraycopy(readBuffer.array(), 0, data, 0, count);
                readHandler.read(data);
                readHandler.handle();
            }
        } catch (IOException e) {
            e.printStackTrace();
            try {
                socketChannel.close();
                key.cancel();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    public boolean isClosed() {
        return isClosed;
    }

    public void close() {
        try {
            // 使线程退出
            isClosed = true;
            selector.close();
            socketChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void send(String msg) {
        MsgPacket msgPacket = new MsgPacket(msg);
        // 添加到写队列
        bufferQueue.add(ByteBuffer.wrap(msgPacket.getBytes()));
        socketChannel.keyFor(this.selector).interestOps(SelectionKey.OP_WRITE);
        // 唤醒
        selector.wakeup();
    }

    // 往通道写
    private void writeToChannel(SelectionKey key) {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        while(!bufferQueue.isEmpty()) {
            ByteBuffer buffer = bufferQueue.get(0);
            try {
                socketChannel.write(buffer);
                if(buffer.remaining() > 0) {
                    break;
                }
                bufferQueue.remove(0);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if(bufferQueue.isEmpty()) {
            // 全部数据写完了 取消写等待事件
            key.interestOps(SelectionKey.OP_READ);
        }
    }

    public static void main(String[] args) {
        NIOClient client = new NIOClient("127.0.0.1", 9000);
        client.startup();
        System.out.println(Report.reportCurrentTime() + " client startup");
    }

}