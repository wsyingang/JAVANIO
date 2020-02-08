package NIOLearn;

import java.nio.channels.SocketChannel;

class ServerReadHandler extends ReadHandler {

    private NIOServer nioServer;

    public ServerReadHandler(SocketChannel socketChannel, NIOServer nioServer) {
        super(socketChannel);
        this.nioServer = nioServer;
    }

    protected synchronized void response(MsgPacket msgPacket) {
        final String content = new String(msgPacket.getData());
        System.out.println(Report.reportCurrentTime() + "receive content -> "
                + content);

        // 模拟耗时操作
        try {
            Thread.sleep(100);
            // 模拟一下回复客户端
            nioServer.respone(socketChannel, Report.reportCurrentTime() + "Server reply -> " + content);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
}