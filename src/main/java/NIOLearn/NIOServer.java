package NIOLearn;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class NIOServer {

    // 缓存大小 最好设置大一些,但是也不要设得太大(不可少于2)
    private static final int BUFFER_SIZE = 1024 * 8;
    // 通道管理器
    private Selector selector;
    // 读处理线程池
    private ThreadPoolExecutor readPoolExecutor;
    // 分配读缓存
    private ByteBuffer byteBuffer;
    // 存放与客户端连接的SocketChannel和其待写队列
    private Map<SocketChannel, LinkedList<ByteBuffer>> writeBufferMap;
    // 存放与客户端连接的SocketChannel和ReadHandler(一个客户端对应一个ReadHandler)
    private Map<SocketChannel, ReadHandler> SRMap;
    // 现有连接数
    private int connnectCount;

    private String ip;
    private int port;

    public NIOServer() {}

    public NIOServer(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    // 初始化
    private void init() {
        final int processors = Runtime.getRuntime().availableProcessors();
        readPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(processors * 2);
        // 初始化分配读缓存
        byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        // 初始化写队列
        writeBufferMap = new ConcurrentHashMap<SocketChannel, LinkedList<ByteBuffer>>();
        // 初始化SRMap
        SRMap = new ConcurrentHashMap<SocketChannel, ReadHandler>();
        // 初始化连接数
        connnectCount = 0;

        ServerSocketChannel serverSocketChannel = null;
        try {
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.socket().bind(new InetSocketAddress(ip, port));
            serverSocketChannel.configureBlocking(false);
            selector = Selector.open();
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 启动
    public void startup() {
        // 完成初始化
        init();
        // 报告服务器启动
        System.out.println(Report.reportCurrentTime() + "server startup...");
        System.out.println(Report.reportCurrentTime() + "server listen on " + ip + " port " + port);
        new Thread(new Runnable() {

            @Override
            public void run() {
                // 一直跑
                while(!Thread.interrupted()) {
                    try {
                        // 阻塞在这里 如果写成selector.select(1000)则最多阻塞1000ms
                        int nKey = selector.select();
                        if (nKey > 0) {
                            Set<SelectionKey> keySet = selector.selectedKeys();
                            Iterator<SelectionKey> iterator = keySet.iterator();
                            while (iterator.hasNext()) {
                                final SelectionKey key = iterator.next();
                                // 移除,避免重复处理
                                iterator.remove();
                                // 根据key的类型进行判断 OP_ACCEPT|OP_READ|OP_WRITE
                                if (key.isValid() && key.isAcceptable()) {
                                    // 处理接收连接请求
                                    acceptConnection(key);
                                } else if (key.isValid() && key.isReadable()) {
                                    // 处理读操作
                                    readFromChannel(key);
                                } else if(key.isValid() && key.isWritable()) {
                                    // 处理写操作
                                    writeToChannel(key);
                                }
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    // 接收客户端连接请求
    private void acceptConnection(SelectionKey key) {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key
                .channel();
        // 接受客户端连接
        Socket socket = null;

        try {
            socket = serverSocketChannel
                    .accept().socket();
            SocketChannel socketChannel = socket
                    .getChannel();
            // 设置通道非阻塞
            socketChannel.configureBlocking(false);

            // 注册读权限
            socketChannel.register(selector,
                    SelectionKey.OP_READ);

            // 测试写数据
            socketChannel.write(ByteBuffer.wrap(new MsgPacket(Report.reportCurrentTime() + "欢迎来到本地服务器").getBytes()));
            System.out.println(Report.reportCurrentTime() + "accept one Client");
            connnectCount++;
            // 保存SocketChannel与处理该通道的ReadHandler
            SRMap.put(socketChannel, new ServerReadHandler(socketChannel, this));
            // 保存SocketChannel与其待写队列
            writeBufferMap.put(socketChannel, new LinkedList<ByteBuffer>());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 从通道读
    private void readFromChannel(SelectionKey key) {
        final SocketChannel socketChannel = (SocketChannel) key.channel();
        synchronized (byteBuffer) {
            byteBuffer.clear();
            try {
                final int count = socketChannel.read(byteBuffer);
//				System.out.println("count = " + count);
                if(count > 0) { // 接收
                    final byte[] data = new byte[count];
                    System.arraycopy(byteBuffer.array(), 0, data, 0, count);
                    final ReadHandler readHandler = SRMap.get(socketChannel);
                    readHandler.read(data);
                    // 线程池处理
                    readPoolExecutor.execute(new Runnable() {

                        @Override
                        public void run() {
//							readHandler.handle(array, count);
                            readHandler.handle();
                        }
                    });
                } else if (count < 0) { // 客户端主动断开连接
                    connnectCount --;
                    // 释放资源
                    releaseResource(socketChannel);
                    socketChannel.close();
                    key.cancel();
                    System.out.println("客户端主动断开连接," + " 剩余连接数: " + connnectCount);
                }
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    if(socketChannel != null && socketChannel.isOpen()) {
                        // 处理客户端异常断开
                        socketChannel.close();
                    }
                    // 取消感兴趣的事件
                    key.cancel();
                    // 移除与该socketChannel的资源
                    releaseResource(socketChannel);
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }

    // 模拟响应
    public synchronized void respone(SocketChannel socketChannel, String msg) {
        MsgPacket msgPacket = new MsgPacket(msg);
        LinkedList<ByteBuffer> bufferQueue = writeBufferMap.get(socketChannel);
        // 添加到写队列
        bufferQueue.add(ByteBuffer.wrap(msgPacket.getBytes()));
        socketChannel.keyFor(this.selector).interestOps(SelectionKey.OP_WRITE);
        // 唤醒
        selector.wakeup();
    }

    // 往通道写
    private synchronized void writeToChannel(SelectionKey key) {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        LinkedList<ByteBuffer> bufferQueue = writeBufferMap.get(socketChannel);
        while(!bufferQueue.isEmpty()) {
            ByteBuffer buffer = bufferQueue.get(0);
            try {
                socketChannel.write(buffer);
                if(buffer.remaining() > 0) {
                    // 该缓冲区中的字节还没有写完,break,让下一个write key继续写
                    break;
                }
                // 写完一个buffer
                bufferQueue.remove(0);
            } catch (IOException e) {
                e.printStackTrace();
                // 处理客户端异常断开
                try {
                    if(socketChannel != null && socketChannel.isOpen()) {
                        socketChannel.close();
                    }
                } catch (IOException e1) {	// 关闭时可能遇到ClosedChannelException
                    e1.printStackTrace();
                } finally {
                    // 取消感兴趣的事件
                    key.cancel();
                    // 释放资源
                    releaseResource(socketChannel);
                }
            }
        }
        if(bufferQueue.isEmpty()) {
            // 全部数据写完了 取消写等待事件(不取消会造成cpu很快达到100%,因为OP_WRITE没有移除,seletor.select()不会阻塞,一直执行while死循环)
            key.interestOps(SelectionKey.OP_READ);
        }
    }

    // 释放资源
    private synchronized void releaseResource(SocketChannel socketChannel) {
        SRMap.remove(socketChannel);
//		System.out.println("SRMap size = " + SRMap.size());
        writeBufferMap.remove(socketChannel);
//		System.out.println("writeBufferMap size = " + writeBufferMap.size());
    }

    public static void main(String[] args) {
        NIOServer server = new NIOServer("127.0.0.1", 9000);
        server.startup();
    }

}