package NIOLearn;

import java.nio.channels.SocketChannel;

public class ClientReadHandler extends ReadHandler {

    private NIOClient nioClient;

    public ClientReadHandler(NIOClient nioClient, SocketChannel socketChannel) {
        super(socketChannel);
        this.nioClient = nioClient;
    }

    protected void response(MsgPacket msgPacket) {
        String content = new String(msgPacket.getData());
        System.out.println(Report.reportCurrentTime() + nioClient.getName() + " receive content -> "
                + content);
    }

}